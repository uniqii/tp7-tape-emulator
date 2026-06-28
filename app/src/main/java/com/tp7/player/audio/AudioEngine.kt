package com.tp7.player.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Low-level PCM AudioEngine.
 *
 * Architecture:
 *  1. MediaExtractor + MediaCodec decode the entire file into a flat ShortArray (16-bit stereo PCM).
 *  2. A lightweight index pointer [pcmIndex] is the single source of truth for position.
 *  3. An AudioTrack in WRITE_NON_BLOCKING mode streams chunks from [pcmIndex].
 *  4. Touch-drag directly mutates [pcmIndex] → zero-latency scrubbing / reverse.
 *  5. Speed is implemented as fractional index advancement per output sample (no pitch shift,
 *     matching TP-7 behavior where speed and pitch are coupled).
 */
class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioEngine"
        // Number of output samples per write chunk — small enough for low latency.
        private const val CHUNK_SAMPLES = 1024
        // Max PCM buffer: ~200 MB for stereo 44.1 kHz / 16-bit ≈ ~23 min
        private const val MAX_PCM_SHORTS = 50_000_000
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private val _isPlaying   = MutableStateFlow(false)
    private val _isLoading   = MutableStateFlow(false)
    private val _positionMs  = MutableStateFlow(0L)
    private val _durationMs  = MutableStateFlow(0L)
    private val _trackName   = MutableStateFlow("")
    private val _error       = MutableStateFlow<String?>(null)

    val isPlaying:  StateFlow<Boolean>  = _isPlaying
    val isLoading:  StateFlow<Boolean>  = _isLoading
    val positionMs: StateFlow<Long>     = _positionMs
    val durationMs: StateFlow<Long>     = _durationMs
    val trackName:  StateFlow<String>   = _trackName
    val error:      StateFlow<String?>  = _error

    // ── PCM store ──────────────────────────────────────────────────────────────

    @Volatile private var pcmData:      ShortArray = ShortArray(0)
    @Volatile private var sampleRate:   Int = 44100
    @Volatile private var channelCount: Int = 2

    // Fractional index into pcmData (interleaved stereo shorts)
    @Volatile private var pcmIndexF:    Double = 0.0

    // Playback speed multiplier (1.0 = normal, negative = reverse)
    @Volatile var speed: Float = 1f
        set(value) { field = value.coerceIn(-4f, 4f) }

    // Whether the disc is being held (touch suppresses audio write, not play state)
    @Volatile var discHeld: Boolean = false

    // ── AudioTrack ─────────────────────────────────────────────────────────────

    private var audioTrack: AudioTrack? = null

    // ── Coroutines ─────────────────────────────────────────────────────────────

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playbackJob: Job? = null
    private var positionJob: Job? = null
    private var loadJob:     Job? = null

    // ── Public API ─────────────────────────────────────────────────────────────

    fun load(uri: Uri, displayName: String) {
        loadJob?.cancel()
        stopPlayback()
        _isLoading.value = true
        _error.value = null
        _trackName.value = displayName

        loadJob = engineScope.launch(Dispatchers.IO) {
            try {
                decodeToPcm(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Decode error", e)
                _error.value = "Failed to load: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun play() {
        if (pcmData.isEmpty()) return
        _isPlaying.value = true
        ensureAudioTrackCreated()
        startPlaybackLoop()
    }

    fun pause() {
        _isPlaying.value = false
        audioTrack?.pause()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    /**
     * Called by the disc drag gesture.
     * [deltaFrames] is signed: positive = forward scrub, negative = reverse.
     * Uses frame units (stereo pair = 1 frame = 2 shorts).
     */
    fun scrubByFrames(deltaFrames: Double) {
        val maxFrame = (pcmData.size / channelCount) - 1
        val newFrame = ((pcmIndexF / channelCount) + deltaFrames).coerceIn(0.0, maxFrame.toDouble())
        pcmIndexF = newFrame * channelCount
        updatePositionFlow()
    }

    fun seekToFraction(fraction: Float) {
        pcmIndexF = (fraction.toDouble() * pcmData.size).coerceIn(0.0, (pcmData.size - 1).toDouble())
        updatePositionFlow()
    }

    fun release() {
        stopPlayback()
        audioTrack?.release()
        audioTrack = null
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun stopPlayback() {
        playbackJob?.cancel()
        positionJob?.cancel()
        playbackJob = null
        positionJob = null
        _isPlaying.value = false
        audioTrack?.pause()
        audioTrack?.flush()
    }

    private fun ensureAudioTrackCreated() {
        val existingTrack = audioTrack
        if (existingTrack != null && existingTrack.state == AudioTrack.STATE_INITIALIZED &&
            existingTrack.sampleRate == sampleRate) return

        existingTrack?.release()

        val channelMask = if (channelCount == 2)
            AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // Use 4× min buffer for headroom while still being low-latency
        val bufSize = max(minBuf * 4, CHUNK_SAMPLES * channelCount * 2)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        positionJob?.cancel()

        // Position update loop (UI thread friendly ~60 Hz)
        positionJob = engineScope.launch {
            while (isActive) {
                updatePositionFlow()
                kotlinx.coroutines.delay(16)
            }
        }

        playbackJob = engineScope.launch(Dispatchers.Default) {
            val track = audioTrack ?: return@launch
            val buf = ShortArray(CHUNK_SAMPLES * channelCount)

            while (isActive && _isPlaying.value) {
                val currentSpeed = speed

                if (discHeld || abs(currentSpeed) < 0.01f) {
                    // Disc held or effectively stopped — write silence to keep AudioTrack alive
                    buf.fill(0)
                    track.write(buf, 0, buf.size, AudioTrack.WRITE_NON_BLOCKING)
                    continue
                }

                // Build output chunk by walking the pcm buffer at fractional speed
                val data = pcmData
                val dataLen = data.size
                if (dataLen == 0) break

                var idx = pcmIndexF
                val step = currentSpeed.toDouble() * channelCount  // shorts per output frame

                for (i in 0 until CHUNK_SAMPLES) {
                    val iFloor = idx.toLong().coerceIn(0, (dataLen - channelCount).toLong()).toInt()
                    // Make iFloor aligned to channel boundary
                    val iAligned = (iFloor / channelCount) * channelCount

                    for (ch in 0 until channelCount) {
                        // Linear interpolation between adjacent frames for smooth pitch
                        val a = data[iAligned + ch]
                        val nextFrame = (iAligned + channelCount + ch).coerceIn(0, dataLen - 1)
                        val b = data[nextFrame]
                        val frac = idx - iFloor.toDouble()
                        buf[i * channelCount + ch] = lerp(a, b, frac)
                    }

                    idx += step

                    // Clamp at boundaries — stop playback when reaching end in forward mode
                    if (step > 0 && idx >= dataLen) {
                        idx = dataLen.toDouble()
                        pcmIndexF = idx
                        _isPlaying.value = false
                        track.pause()
                        return@launch
                    } else if (step < 0 && idx < 0) {
                        idx = 0.0
                        pcmIndexF = idx
                        _isPlaying.value = false
                        track.pause()
                        return@launch
                    }
                }

                pcmIndexF = idx

                var written = 0
                val total = CHUNK_SAMPLES * channelCount
                // Drain write loop for NON_BLOCKING mode
                while (written < total && isActive) {
                    val w = track.write(buf, written, total - written, AudioTrack.WRITE_NON_BLOCKING)
                    if (w > 0) written += w else kotlinx.coroutines.delay(1)
                }
            }
        }
    }

    private fun lerp(a: Short, b: Short, t: Double): Short {
        return (a + (b - a) * t).toInt().toShort()
    }

    private fun updatePositionFlow() {
        val frames   = (pcmIndexF / channelCount).toLong()
        val totalFrm = (pcmData.size / channelCount).toLong()
        val sr       = sampleRate.toLong()
        _positionMs.value = if (sr > 0) frames * 1000L / sr else 0L
        _durationMs.value = if (sr > 0) totalFrm * 1000L / sr else 0L
    }

    // ── MediaCodec decoder ─────────────────────────────────────────────────────

    private fun decodeToPcm(uri: Uri) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        var audioTrackIdx = -1
        var format: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIdx = i
                format = f
                break
            }
        }

        if (audioTrackIdx < 0 || format == null) {
            throw IllegalArgumentException("No audio track found")
        }

        extractor.selectTrack(audioTrackIdx)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        sampleRate   = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()

        // Pre-allocate a growable list of short chunks
        val chunks = ArrayList<ShortArray>(512)
        var totalShorts = 0L
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10_000L)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)!!
                    val sampleSize = extractor.readSampleData(inBuf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (outIdx >= 0) {
                val outBuf = codec.getOutputBuffer(outIdx)
                if (outBuf != null && bufferInfo.size > 0) {
                    val shorts = bufferInfo.size / 2
                    val chunk  = ShortArray(shorts)
                    outBuf.asShortBuffer().get(chunk)
                    chunks.add(chunk)
                    totalShorts += shorts

                    if (totalShorts > MAX_PCM_SHORTS) {
                        // Truncate gracefully — avoid OOM
                        Log.w(TAG, "PCM buffer limit reached, truncating")
                        codec.releaseOutputBuffer(outIdx, false)
                        outputDone = true
                        break
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        // Flatten all chunks into one contiguous ShortArray
        val pcm = ShortArray(totalShorts.coerceAtMost(MAX_PCM_SHORTS.toLong()).toInt())
        var pos = 0
        for (chunk in chunks) {
            val toCopy = min(chunk.size, pcm.size - pos)
            if (toCopy <= 0) break
            System.arraycopy(chunk, 0, pcm, pos, toCopy)
            pos += toCopy
        }

        pcmData    = pcm
        pcmIndexF  = 0.0

        updatePositionFlow()
        _isLoading.value = false

        Log.d(TAG, "Decoded ${pcm.size} shorts, sampleRate=$sampleRate ch=$channelCount dur=${_durationMs.value}ms")
    }
}
