package com.example.spotifyclone.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.spotifyclone.theme.CrimsonRed

/**
 * Animated Bouncing Equalizer Bars for displaying live playback status.
 * Replaces static icons with a vibrant, dynamic micro-equalizer.
 */
@Composable
fun BouncingEqualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    color: Color = CrimsonRed,
    barWidth: Dp = 2.5.dp,
    barSpacing: Dp = 2.dp,
    maxHeight: Dp = 16.dp,
    minHeight: Dp = 3.dp
) {
    val transition = rememberInfiniteTransition(label = "bouncing_eq")

    // Staggered durations & easing for realistic organic audio meter bouncing
    val h1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_3"
    )
    val h4 by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 410, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_4"
    )

    val fractions = listOf(h1, h2, h3, h4)
    val staticFractions = listOf(0.45f, 0.75f, 0.35f, 0.60f)

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until barCount.coerceIn(3, 4)) {
            val fraction = if (isPlaying) fractions[i % fractions.size] else staticFractions[i % staticFractions.size]
            val currentBarHeight = minHeight + (maxHeight - minHeight) * fraction

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(currentBarHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}
