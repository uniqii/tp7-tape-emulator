package com.tp7.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Vinyl disc UI component.
 *
 * Spinning: when [isPlaying] && ![isHeld], the disc rotates continuously at [speedMultiplier]×.
 *
 * Touch interaction:
 *  - Pointer down → calls [onHoldChange](true).
 *  - Drag → calls [onScrub] with rotational delta in "virtual frames"
 *    (mapped via [framesPerFullRotation]).
 *  - Pointer up → calls [onHoldChange](false).
 */
@Composable
fun VinylDisc(
    isPlaying: Boolean,
    isHeld: Boolean,
    speedMultiplier: Float = 1f,
    durationMs: Long = 0L,
    modifier: Modifier = Modifier,
    discSize: Dp = 300.dp,
    framesPerFullRotation: Double = 88200.0,  // 2 sec at 44.1 kHz
    onHoldChange: (Boolean) -> Unit,
    onScrub: (deltaFrames: Double) -> Unit
) {
    // Continuous rotation angle accumulator driven by infinite transition
    val infiniteTransition = rememberInfiniteTransition(label = "disc")
    // One full rotation every (1000 / speedMultiplier) ms … but we drive our own manual angle.
    // The InfiniteTransition drives a 0→360 float we use as base.
    val autoAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (speedMultiplier != 0f) (2000f / speedMultiplier).toInt().coerceAtLeast(200) else 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "autoAngle"
    )

    // Manual scrub angle offset accumulated from drag
    var manualAngle by remember { mutableFloatStateOf(0f) }
    var lastAngle   by remember { mutableFloatStateOf(0f) }
    var dragOrigin  by remember { mutableFloatStateOf(0f) }

    val effectiveAngle = if (isHeld) manualAngle
    else if (isPlaying) autoAngle + manualAngle
    else manualAngle

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(discSize)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onHoldChange(true)
                        // Compute initial angle from center
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        dragOrigin = atan2(offset.y - cy, offset.x - cx)
                        lastAngle = effectiveAngle
                    },
                    onDragEnd = {
                        onHoldChange(false)
                    },
                    onDragCancel = {
                        onHoldChange(false)
                    },
                    onDrag = { change, _ ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val pos = change.position
                        val currentAngle = atan2(pos.y - cy, pos.x - cx)
                        var delta = currentAngle - dragOrigin
                        // Wrap delta to [-PI, PI]
                        while (delta > PI) delta -= 2 * PI
                        while (delta < -PI) delta += 2 * PI

                        val deltaFrames = (delta / (2 * PI)) * framesPerFullRotation
                        manualAngle += (delta * 180f / PI.toFloat())
                        dragOrigin = currentAngle
                        onScrub(deltaFrames)
                        change.consume()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.size(discSize)) {
            drawVinyl(effectiveAngle, isHeld)
        }
    }
}

private fun DrawScope.drawVinyl(rotationDeg: Float, isHeld: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r  = size.width / 2f

    rotate(rotationDeg, pivot = Offset(cx, cy)) {

        // ── Outer disc body ────────────────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to Color(0xFF1A1A1A),
                0.6f to Color(0xFF111111),
                0.85f to Color(0xFF0D0D0D),
                1.0f to Color(0xFF222222),
                center = Offset(cx, cy),
                radius = r
            ),
            radius = r,
            center = Offset(cx, cy)
        )

        // ── Vinyl grooves ──────────────────────────────────────────────────────
        val grooveColor = Color(0xFF2A2A2A)
        val grooveCount = 40
        for (i in 1..grooveCount) {
            val gr = r * (0.28f + 0.65f * (i.toFloat() / grooveCount))
            drawCircle(
                color = grooveColor,
                radius = gr,
                center = Offset(cx, cy),
                style = Stroke(width = 0.8f)
            )
        }

        // ── Sheen highlight (simulates light reflection) ───────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to Color.White.copy(alpha = 0.04f),
                0.4f to Color.Transparent,
                center = Offset(cx * 0.7f, cy * 0.7f),
                radius = r * 0.9f
            ),
            radius = r,
            center = Offset(cx, cy)
        )

        // ── Center label ───────────────────────────────────────────────────────
        val labelR = r * 0.22f
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to Color(0xFF2C2C2C),
                1.0f to Color(0xFF181818),
                center = Offset(cx, cy),
                radius = labelR
            ),
            radius = labelR,
            center = Offset(cx, cy)
        )

        // Label ring
        drawCircle(
            color = Color(0xFF3A3A3A),
            radius = labelR,
            center = Offset(cx, cy),
            style = Stroke(width = 1f)
        )

        // ── Spindle hole ───────────────────────────────────────────────────────
        drawCircle(
            color = Color(0xFF090909),
            radius = r * 0.02f,
            center = Offset(cx, cy)
        )
    }

    // ── Tonearm / stylus (static, outside rotation) ────────────────────────────
    drawToneArm(cx, cy, r, isHeld)
}

private fun DrawScope.drawToneArm(cx: Float, cy: Float, r: Float, isHeld: Boolean) {
    val armColor   = if (isHeld) Color(0xFFCCCCCC) else Color(0xFF888888)
    val pivotX     = cx + r * 0.85f
    val pivotY     = cy - r * 0.72f
    val tipX       = cx + r * 0.28f
    val tipY       = cy - r * 0.08f

    // Arm line
    drawLine(
        color = armColor,
        start = Offset(pivotX, pivotY),
        end   = Offset(tipX, tipY),
        strokeWidth = 2.5f,
        cap = StrokeCap.Round
    )

    // Pivot circle
    drawCircle(
        color = armColor,
        radius = 5f,
        center = Offset(pivotX, pivotY)
    )

    // Stylus head
    drawCircle(
        color = Color(0xFFFFFFFF).copy(alpha = 0.7f),
        radius = 3f,
        center = Offset(tipX, tipY)
    )
}
