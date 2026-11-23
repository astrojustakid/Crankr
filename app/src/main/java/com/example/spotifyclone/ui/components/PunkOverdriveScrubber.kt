package com.example.spotifyclone.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifyclone.theme.*

/**
 * Modern, Sederhana & User-Friendly Music Progress Slider:
 * - Track horizontal bersih dengan indikator warna Primary (#D9822B).
 * - Thumb bulat ergonomis yang mudah digeser (scrubbing) dengan feedback haptic.
 * - Area sentuh yang luas (32dp) sehingga nyaman dan responsif dioperasikan pengguna.
 * - Label durasi yang rapi dan jelas di sisi kiri dan kanan.
 */
@Composable
fun PunkOverdriveScrubber(
    trackId: String,
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isUserDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var componentWidthPx by remember { mutableIntStateOf(1) }

    val rawProgress = (currentPositionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
    val displayFraction = if (isUserDragging) dragFraction else rawProgress

    val currentDurationMs by rememberUpdatedState(durationMs)
    val currentOnSeekTo by rememberUpdatedState(onSeekTo)

    // Animasi dinamis ukuran thumb dan glow saat scrubbing
    val thumbRadius by animateDpAsState(
        targetValue = if (isUserDragging) 8.5.dp else 5.5.dp,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "thumbRadius"
    )
    val activeTrackHeightDp by animateDpAsState(
        targetValue = if (isUserDragging) 5.5.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "activeTrackHeight"
    )
    val glowRadius by animateDpAsState(
        targetValue = if (isUserDragging) 18.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.70f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glowRadius"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isUserDragging) 0.35f else 0f,
        animationSpec = tween(180),
        label = "glowAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { componentWidthPx = it.width.coerceAtLeast(1) }
    ) {
        // Touch Target Bar (Luas & Mudah Disentuh)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val initialFraction = (down.position.x / componentWidthPx.toFloat()).coerceIn(0f, 1f)
                        dragFraction = initialFraction
                        isUserDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                val targetMs = (dragFraction * currentDurationMs).toLong()
                                currentOnSeekTo(targetMs)
                                isUserDragging = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                break
                            } else {
                                change.consume()
                                dragFraction = (change.position.x / componentWidthPx.toFloat()).coerceIn(0f, 1f)
                            }
                        }
                        isUserDragging = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                val totalWidth = size.width
                val centerY = size.height / 2f
                val trackHeight = 4.dp.toPx()
                val activeTrackHeight = activeTrackHeightDp.toPx()
                val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                // 1. Inactive Track (Jalur durasi belum diputar)
                drawRoundRect(
                    color = Border,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(totalWidth, trackHeight),
                    cornerRadius = cornerRadius
                )

                // 2. Active Track (Jalur durasi yang sedang diputar)
                val thumbXPx = (displayFraction * totalWidth).coerceIn(0f, totalWidth)
                drawRoundRect(
                    color = Primary,
                    topLeft = Offset(0f, centerY - activeTrackHeight / 2f),
                    size = Size(thumbXPx, activeTrackHeight),
                    cornerRadius = CornerRadius(activeTrackHeight / 2f, activeTrackHeight / 2f)
                )

                // 3. Sentuhan Glow Tactile saat scrubbing
                if (glowRadius > 0.dp && glowAlpha > 0f) {
                    drawCircle(
                        color = Primary.copy(alpha = glowAlpha),
                        radius = glowRadius.toPx(),
                        center = Offset(thumbXPx, centerY)
                    )
                }

                // 4. Thumb Knob Bulat Ergonomis
                drawCircle(
                    color = Secondary,
                    radius = thumbRadius.toPx(),
                    center = Offset(thumbXPx, centerY)
                )
                drawCircle(
                    color = Primary,
                    radius = (thumbRadius.toPx() * 0.55f),
                    center = Offset(thumbXPx, centerY)
                )
            }
        }

        // Label waktu: Waktu sekarang di kiri, Total durasi di kanan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentDisplayMs = if (isUserDragging) (dragFraction * durationMs).toLong() else currentPositionMs
            Text(
                text = formatScrubberTime(currentDisplayMs),
                fontFamily = CrankrBodyFont,
                color = if (isUserDragging) Primary else Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            )

            Text(
                text = formatScrubberTime(durationMs),
                fontFamily = CrankrBodyFont,
                color = Tertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            )
        }
    }
}

private fun formatScrubberTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
