package com.tp7.player.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tp7.player.audio.AudioEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    val engine = AudioEngine(app)

    val isPlaying:  StateFlow<Boolean> = engine.isPlaying.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isLoading:  StateFlow<Boolean> = engine.isLoading.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val positionMs: StateFlow<Long>    = engine.positionMs.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val durationMs: StateFlow<Long>    = engine.durationMs.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val trackName:  StateFlow<String>  = engine.trackName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val error:      StateFlow<String?> = engine.error.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun loadUri(uri: Uri) {
        val name = resolveDisplayName(uri)
        engine.load(uri, name)
    }

    fun togglePlayPause() = engine.togglePlayPause()

    fun setSpeed(speed: Float) { engine.speed = speed }

    fun setDiscHeld(held: Boolean) { engine.discHeld = held }

    fun scrubByFrames(deltaFrames: Double) = engine.scrubByFrames(deltaFrames)

    private fun resolveDisplayName(uri: Uri): String {
        return try {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment ?: "Unknown"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "Unknown"
        }
    }

    override fun onCleared() {
        engine.release()
    }
}
