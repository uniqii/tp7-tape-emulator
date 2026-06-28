package com.tp7.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * TP-7 style speed control.
 * Range: 0.25× … 2.0× (centre = 1.0×).
 * Double-tap resets to 1.0×.
 */
@Composable
fun SpeedSlider(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val minSpeed = 0.25f
    val maxSpeed = 2.0f
    val normalizedCenter = (1f - minSpeed) / (maxSpeed - minSpeed)

    // Convert speed to 0..1 fraction
    val fraction = ((speed - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0f, 1f)

    val trackColor     = Color(0xFF333333)
    val activeColor    = Color(0xFFEEEEEE)
    val thumbColor     = Color.White
    val centerMarkColor = Color(0xFF666666)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, delta ->
                        val newFrac = (fraction + delta / size.width).coerceIn(0f, 1f)
                        val newSpeed = minSpeed + newFrac * (maxSpeed - minSpeed)
                        onSpeedChange(newSpeed)
                        change.consume()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onSpeedChange(1f) },
                        onTap = { offset ->
                            val newFrac = (offset.x / size.width).coerceIn(0f, 1f)
                            val newSpeed = minSpeed + newFrac * (maxSpeed - minSpeed)
                            onSpeedChange(newSpeed)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val trackY   = size.height / 2f
                val trackH   = 2f
                val thumbR   = 6f
                val padding  = thumbR + 4f

                val trackLeft  = padding
                val trackRight = size.width - padding
                val trackWidth = trackRight - trackLeft

                // Background track
                drawLine(
                    color = trackColor,
                    start = Offset(trackLeft, trackY),
                    end   = Offset(trackRight, trackY),
                    strokeWidth = trackH,
                    cap = StrokeCap.Round
                )

                // Active segment (from center to thumb)
                val thumbX     = trackLeft + fraction * trackWidth
                val centerX    = trackLeft + normalizedCenter * trackWidth
                val activeLeft  = minOf(thumbX, centerX)
                val activeRight = maxOf(thumbX, centerX)
                if (activeRight > activeLeft) {
                    drawLine(
                        color = activeColor,
                        start = Offset(activeLeft, trackY),
                        end   = Offset(activeRight, trackY),
                        strokeWidth = trackH,
                        cap = StrokeCap.Round
                    )
                }

                // Center tick (1×)
                drawLine(
                    color = centerMarkColor,
                    start = Offset(centerX, trackY - 8f),
                    end   = Offset(centerX, trackY + 8f),
                    strokeWidth = 1.5f,
                    cap = StrokeCap.Round
                )

                // Thumb
                drawCircle(
                    color = thumbColor,
                    radius = thumbR,
                    center = Offset(thumbX, trackY)
                )
            }
        }

        // Label
        val displaySpeed = (speed * 100).roundToInt() / 100f
        Text(
            text = "${displaySpeed}×",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace
        )
    }
}
