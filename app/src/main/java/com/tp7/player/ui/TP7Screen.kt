package com.tp7.player.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tp7.player.viewmodel.PlayerViewModel

@Composable
fun TP7Screen(vm: PlayerViewModel = viewModel()) {

    val isPlaying  by vm.isPlaying.collectAsState()
    val isLoading  by vm.isLoading.collectAsState()
    val positionMs by vm.positionMs.collectAsState()
    val durationMs by vm.durationMs.collectAsState()
    val trackName  by vm.trackName.collectAsState()
    val error      by vm.error.collectAsState()

    var discHeld  by remember { mutableStateOf(false) }
    var speed     by remember { mutableFloatStateOf(1f) }

    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { vm.loadUri(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TP-7 wordmark
                Text(
                    text = "TP-7",
                    color = Color(0xFF555555),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp
                )

                // Add track button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A))
                        .clickable { pickAudio.launch("audio/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        color = Color(0xFF888888),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Track name ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isLoading -> "loading…"
                        error != null -> error!!
                        trackName.isNotEmpty() -> trackName
                        else -> "no track loaded"
                    },
                    color = Color(0xFF444444),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Vinyl disc ─────────────────────────────────────────────────────
            VinylDisc(
                isPlaying  = isPlaying && !isLoading,
                isHeld     = discHeld,
                speedMultiplier = speed,
                durationMs = durationMs,
                discSize   = 290.dp,
                onHoldChange = { held ->
                    discHeld = held
                    vm.setDiscHeld(held)
                },
                onScrub = { deltaFrames ->
                    vm.scrubByFrames(deltaFrames)
                }
            )

            Spacer(Modifier.height(32.dp))

            // ── Odometer ───────────────────────────────────────────────────────
            OdometerDisplay(
                positionMs = positionMs,
                color      = Color(0xFFDDDDDD),
                digitWidth = 20.dp,
                digitHeight = 36.dp,
                fontSize   = 28.sp,
                separatorColor = Color(0xFF444444)
            )

            Spacer(Modifier.height(4.dp))

            // Duration
            if (durationMs > 0L) {
                val durSec = durationMs / 1000L
                val dH = durSec / 3600; val dM = (durSec % 3600) / 60; val dS = durSec % 60
                Text(
                    text = "/ %02d:%02d:%02d".format(dH, dM, dS),
                    color = Color(0xFF333333),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light
                )
            }

            Spacer(Modifier.height(36.dp))

            // ── Speed slider ───────────────────────────────────────────────────
            SpeedSlider(
                speed = speed,
                onSpeedChange = { s ->
                    speed = s
                    vm.setSpeed(s)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(36.dp))

            // ── Play / Pause button ────────────────────────────────────────────
            PlayPauseButton(
                isPlaying = isPlaying,
                isEnabled = !isLoading && trackName.isNotEmpty(),
                onClick   = { vm.togglePlayPause() }
            )
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val activeColor = Color(0xFFEEEEEE)
    val dimColor    = Color(0xFF333333)
    val color       = if (isEnabled) activeColor else dimColor
    val ringColor   = if (isEnabled) Color(0xFF2A2A2A) else Color(0xFF161616)

    Box(
        modifier = Modifier
            .size(64.dp)
            .drawBehind {
                drawCircle(color = ringColor, radius = size.minDimension / 2f)
                drawCircle(
                    color = if (isEnabled) Color(0xFF2A2A2A) else Color(0xFF141414),
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 1.5f)
                )
            }
            .clip(CircleShape)
            .clickable(enabled = isEnabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            // Pause icon — two vertical bars
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PauseBar(color)
                PauseBar(color)
            }
        } else {
            // Play triangle
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .drawBehind {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.2f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(size.width * 0.2f, size.height)
                            close()
                        }
                        drawPath(path, color = color)
                    }
            )
        }
    }
}

@Composable
private fun PauseBar(color: Color) {
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(18.dp)
            .background(color, shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
    )
}
