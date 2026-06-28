package com.tp7.player.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2

@Composable
fun VinylDisc(
    onScrub: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOrigin by remember { mutableStateOf(0f) }
    var manualAngle by remember { mutableStateOf(0f) }
    val framesPerFullRotation = 1000f

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        dragOrigin = atan2(offset.y - cy, offset.x - cx)
                    },
                    onDrag = { change, _ ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val pos = change.position
                        val currentAngle = atan2(pos.y - cy, pos.x - cx)
                        
                        var delta = currentAngle - dragOrigin
                        val piFloat = Math.PI.toFloat()

                        while (delta > piFloat) delta -= 2f * piFloat
                        while (delta < -piFloat) delta += 2f * piFloat

                        val deltaFrames = (delta / (2f * piFloat)) * framesPerFullRotation
                        manualAngle += (delta * 180f / piFloat)
                        dragOrigin = currentAngle
                        onScrub(deltaFrames)
                        change.consume()
                    }
                )
            }
    ) {
        // Disc UI components go here
    }
}
