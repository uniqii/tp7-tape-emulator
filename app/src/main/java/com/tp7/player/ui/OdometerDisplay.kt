package com.tp7.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * A single odometer digit that scrolls vertically.
 *
 * The digit column contains 0-9 arranged top-to-bottom.
 * Scrolling upward (negative offsetY) reveals higher digits — matching
 * physical odometer behavior.  Fractional offsets between integers give
 * the 1:1 linear drag-between-digits feel.
 *
 * [digitValue] – current digit 0-9
 * [fractional] – 0..1, how far "into" the next digit the odometer has rolled
 */
@Composable
fun OdometerDigit(
    digitValue: Int,
    fractional: Float = 0f,
    charWidth: Dp = 24.dp,
    charHeight: Dp = 40.dp,
    fontSize: TextUnit = 32.sp,
    color: Color = Color.White,
    fontFamily: FontFamily = FontFamily.Monospace
) {
    val density = LocalDensity.current
    val charHeightPx = with(density) { charHeight.toPx() }

    // Smooth animation only when NOT scrubbing (fractional drives it directly during scrub)
    val animatedFractional by animateFloatAsState(
        targetValue = fractional,
        animationSpec = tween(durationMillis = 80),
        label = "odometerFrac"
    )

    // Total offset: roll back by (digit + fractional) * charHeight
    // so digit=0 frac=0 → offset 0, digit=1 frac=0 → offset -charHeightPx, etc.
    val offsetPx = -((digitValue + animatedFractional) * charHeightPx)

    Box(
        modifier = Modifier
            .width(charWidth)
            .height(charHeight)
            .clipToBounds()
    ) {
        // Render digits 0-9 stacked, then 0 again at bottom for wraparound
        Box(
            modifier = Modifier.offset { IntOffset(0, offsetPx.roundToInt()) }
        ) {
            Column11Digits(
                charWidth = charWidth,
                charHeight = charHeight,
                fontSize = fontSize,
                color = color,
                fontFamily = fontFamily
            )
        }
    }
}

@Composable
private fun Column11Digits(
    charWidth: Dp,
    charHeight: Dp,
    fontSize: TextUnit,
    color: Color,
    fontFamily: FontFamily
) {
    // 0-9 plus a trailing 0 for smooth wraparound from 9→0
    androidx.compose.foundation.layout.Column {
        for (d in 0..10) {
            Box(
                modifier = Modifier
                    .width(charWidth)
                    .height(charHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${d % 10}",
                    fontSize = fontSize,
                    fontWeight = FontWeight.Light,
                    fontFamily = fontFamily,
                    color = color
                )
            }
        }
    }
}

/**
 * Full HH:MM:SS odometer display.
 * Each digit rolls independently with the correct carry fractional.
 */
@Composable
fun OdometerDisplay(
    positionMs: Long,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    digitWidth: Dp = 22.dp,
    digitHeight: Dp = 38.dp,
    fontSize: TextUnit = 30.sp,
    separatorColor: Color = Color.White.copy(alpha = 0.4f)
) {
    val totalSec = positionMs / 1000L
    val hours    = (totalSec / 3600L).toInt()
    val minutes  = ((totalSec % 3600L) / 60L).toInt()
    val seconds  = (totalSec % 60L).toInt()
    val millis   = positionMs % 1000L

    // Fractional: how far into the NEXT digit this digit has rolled.
    // milliseconds drive seconds fractional; seconds drive minutes; etc.
    val secFrac = (millis / 1000f)
    val minFrac = ((totalSec % 60L) + secFrac) / 60f
    val hrFrac  = ((totalSec % 3600L) / 60f + minFrac) / 60f

    val h0 = hours / 10;  val h1 = hours % 10
    val m0 = minutes / 10; val m1 = minutes % 10
    val s0 = seconds / 10; val s1 = seconds % 10

    // Carry fractions: s1 drives s0, s0 drives m1, m1 drives m0, etc.
    val s1Frac  = secFrac
    val s0Frac  = if (s1 == 9) secFrac else 0f   // s0 rolls only when s1 is at 9
    val m1Frac  = if (seconds == 59) secFrac else 0f
    val m0Frac  = if (minutes % 10 == 9 && seconds == 59) secFrac else 0f
    val h1Frac  = if (minutes == 59 && seconds == 59) secFrac else 0f
    val h0Frac  = if (hours % 10 == 9 && minutes == 59 && seconds == 59) secFrac else 0f

    val fontFamily = FontFamily.Monospace

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OdometerDigit(h0, h0Frac, digitWidth, digitHeight, fontSize, color, fontFamily)
        OdometerDigit(h1, h1Frac, digitWidth, digitHeight, fontSize, color, fontFamily)

        Separator(digitHeight, separatorColor, fontFamily, fontSize)

        OdometerDigit(m0, m0Frac, digitWidth, digitHeight, fontSize, color, fontFamily)
        OdometerDigit(m1, m1Frac, digitWidth, digitHeight, fontSize, color, fontFamily)

        Separator(digitHeight, separatorColor, fontFamily, fontSize)

        OdometerDigit(s0, s0Frac, digitWidth, digitHeight, fontSize, color, fontFamily)
        OdometerDigit(s1, s1Frac, digitWidth, digitHeight, fontSize, color, fontFamily)
    }
}

@Composable
private fun Separator(
    height: Dp,
    color: Color,
    fontFamily: FontFamily,
    fontSize: TextUnit
) {
    Box(
        modifier = Modifier.height(height),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ":",
            fontSize = fontSize,
            fontWeight = FontWeight.Light,
            fontFamily = fontFamily,
            color = color,
            modifier = Modifier.offset(y = (-3).dp)
        )
    }
}
