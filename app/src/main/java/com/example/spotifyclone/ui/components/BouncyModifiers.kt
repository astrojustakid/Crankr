package com.example.spotifyclone.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Flagship Micro-Touch Physics modifier:
 * Subtle, crisp compression (0.982x -> 1.0x) with critical damping (no rubbery wobble)
 * and rich Material 3 ripple for a high-end, responsive feel.
 */
fun Modifier.bouncyClick(
    scaleDown: Float = 0.982f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val effectiveScaleDown = scaleDown.coerceAtLeast(0.98f)

    val scale by animateFloatAsState(
        targetValue = if (isPressed) effectiveScaleDown else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "micro_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(color = Color.White.copy(alpha = 0.12f)),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
}

/**
 * Spring pressable modifier for buttons and custom surfaces:
 * Applies snappy, low-profile scale compression without overriding surface clicks.
 */
fun Modifier.springPressable(
    scaleDown: Float = 0.982f
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val effectiveScaleDown = scaleDown.coerceAtLeast(0.98f)

    val scale by animateFloatAsState(
        targetValue = if (isPressed) effectiveScaleDown else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "spring_press_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                waitForUpOrCancellation()
                isPressed = false
            }
        }
}
