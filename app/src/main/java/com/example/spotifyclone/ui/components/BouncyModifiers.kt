package com.example.spotifyclone.ui.components

import androidx.compose.animation.core.CubicBezierEasing
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
 * Shared Motion Tokens (Emil Kowalski Design Engineering + Micro-Interactions):
 * - Strong Ease-Out: immediate response, decisive settle
 * - Drawer Curve: iOS / Apple Music sheet feel
 * - Snappy Spring: ~140-180ms settle, interruptible, subtle critical damping
 */
object CrankrMotion {
    val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val EaseInOutStrong = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    fun <T> snappySpring() = spring<T>(dampingRatio = 0.82f, stiffness = 620f)
    fun <T> gentleSpring() = spring<T>(dampingRatio = 0.86f, stiffness = 320f)
}

/**
 * Flagship Micro-Touch Physics modifier (scale 0.965x -> 1.0x in ~140ms)
 * using GPU graphicsLayer to avoid recomposition overhead.
 */
fun Modifier.bouncyClick(
    scaleDown: Float = 0.965f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val effectiveScaleDown = scaleDown.coerceIn(0.93f, 0.985f)

    val scale by animateFloatAsState(
        targetValue = if (isPressed) effectiveScaleDown else 1.0f,
        animationSpec = CrankrMotion.snappySpring(),
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
 * Snappy spring pressable modifier for buttons, pills, and glass cards:
 * Responds instantly on finger down (0.95-0.97x) and recovers with zero wobble.
 */
fun Modifier.springPressable(
    scaleDown: Float = 0.965f
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val effectiveScaleDown = scaleDown.coerceIn(0.92f, 0.985f)

    val scale by animateFloatAsState(
        targetValue = if (isPressed) effectiveScaleDown else 1.0f,
        animationSpec = CrankrMotion.snappySpring(),
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
