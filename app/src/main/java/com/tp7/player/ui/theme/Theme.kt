package com.tp7.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TP7Colors = darkColorScheme(
    primary    = Color(0xFFEEEEEE),
    background = Color(0xFF0A0A0A),
    surface    = Color(0xFF111111),
    onPrimary  = Color(0xFF0A0A0A),
    onBackground = Color(0xFFEEEEEE),
    onSurface  = Color(0xFFAAAAAA)
)

@Composable
fun TP7Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TP7Colors,
        content     = content
    )
}
