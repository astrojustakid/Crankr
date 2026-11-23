package com.example.spotifyclone.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

object CrankrMotion {
    val SmoothSpring = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow
    )

    val BouncySpring = spring<Float>(
        dampingRatio = 0.68f,
        stiffness = Spring.StiffnessMedium
    )

    val FastOutSlow = tween<Float>(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )
}

/**
 * Adds an interactive touch bounce micro-interaction (0.95x -> 1.0x with snappy spring physics).
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    scaleDownTo: Float = 0.95f,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    onClick: () -> Unit
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDownTo else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncyClickScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = indication,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Passive touch bounce that reacts to an external interactionSource (e.g. IconButton, Button).
 */
fun Modifier.bounceOnPressed(
    interactionSource: InteractionSource,
    scaleDownTo: Float = 0.95f,
    enabled: Boolean = true
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDownTo else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
