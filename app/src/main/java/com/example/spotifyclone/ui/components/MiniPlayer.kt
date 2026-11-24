package com.example.spotifyclone.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.util.DynamicColorExtractor
import kotlinx.coroutines.launch
import kotlin.math.abs

private val MiniPlayerAccent: Color get() = Primary
private val MiniPlayerCardBg: Color get() = ElevatedBackground
private val MiniPlayerBorder: Color get() = Border

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    isFavorite: Boolean,
    onPlayPauseClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    onNextClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    sleepTimerBadge: String? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val animatedDragX = remember { Animatable(0f) }
    val hapticFeedback = LocalHapticFeedback.current
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        animatedDragX.snapTo(0f)
    }

    val context = LocalContext.current
    val appearanceManager = remember(context) { com.example.spotifyclone.service.CrankrAppearanceManager.getInstance(context) }
    val coverTintEnabled by appearanceManager.miniPlayerCoverTint.collectAsState()

    var palette by remember(track.id, track.coverUrl) {
        mutableStateOf(DynamicColorExtractor.defaultPalette)
    }

    LaunchedEffect(track.id, track.coverUrl) {
        palette = DynamicColorExtractor.extractColors(context, track.coverUrl)
    }

    val dynamicCapsuleBg by animateColorAsState(
        targetValue = if (coverTintEnabled) {
            androidx.compose.ui.graphics.lerp(ElevatedBackground, palette.dominantColor, 0.30f)
        } else {
            ElevatedBackground
        },
        animationSpec = tween(420),
        label = "MiniPlayerDynamicBg"
    )
    val dynamicCapsuleBorder by animateColorAsState(
        targetValue = if (coverTintEnabled) {
            androidx.compose.ui.graphics.lerp(Border, palette.dominantColor, 0.42f)
        } else {
            Primary.copy(alpha = 0.22f)
        },
        animationSpec = tween(420),
        label = "MiniPlayerDynamicBorder"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        // Layer 1: Background Action Cues revealed when card is dragged
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .background(PitchBlack)
                .border(1.dp, BorderSubtle.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            val leftAlpha = (animatedDragX.value / 60f).coerceIn(0f, 1f)
            if (leftAlpha > 0.02f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .graphicsLayer { alpha = leftAlpha },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = null,
                        tint = if (animatedDragX.value >= 60f) MiniPlayerAccent else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SEBELUMNYA",
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = if (animatedDragX.value >= 60f) MiniPlayerAccent else TextSecondary
                    )
                }
            }

            val rightAlpha = (-animatedDragX.value / 60f).coerceIn(0f, 1f)
            if (rightAlpha > 0.02f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .graphicsLayer { alpha = rightAlpha },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BERIKUTNYA",
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = if (-animatedDragX.value >= 60f) MiniPlayerAccent else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        tint = if (-animatedDragX.value >= 60f) MiniPlayerAccent else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Layer 2: Auralys-Grade Dynamic Cover-Tinted Glass Capsule MiniPlayer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = animatedDragX.value
                }
                .clip(RoundedCornerShape(22.dp))
                .background(dynamicCapsuleBg.copy(alpha = 0.95f))
                .border(1.dp, dynamicCapsuleBorder, RoundedCornerShape(22.dp))
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            hasTriggeredHaptic = false
                        },
                        onDragEnd = {
                            val finalOffset = animatedDragX.value
                            coroutineScope.launch {
                                if (finalOffset >= 60f) {
                                    onPreviousClick()
                                } else if (finalOffset <= -60f) {
                                    onNextClick()
                                }
                                animatedDragX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                hasTriggeredHaptic = false
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                animatedDragX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                hasTriggeredHaptic = false
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (animatedDragX.value + dragAmount).coerceIn(-180f, 180f)
                                animatedDragX.snapTo(newOffset)
                                if (abs(newOffset) >= 60f && !hasTriggeredHaptic) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    hasTriggeredHaptic = true
                                } else if (abs(newOffset) < 60f && hasTriggeredHaptic) {
                                    hasTriggeredHaptic = false
                                }
                            }
                        }
                    )
                }
                .springPressable(scaleDown = 0.982f)
                .clickable(onClick = onClick)
        ) {
            Column {
                // Top Slim Amber Progress Bar Indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFF222222))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Primary,
                                        PrimaryBright
                                    )
                                )
                            )
                    )
                }

                // Main Content Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Spinning Vinyl Record Disc Graphic on the Left
                    SpinningVinylDisc(
                        coverUrl = track.coverUrl,
                        isPlaying = isPlaying,
                        modifier = Modifier.size(42.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Artist with Horizontal Slide Crossfade
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = {
                            (slideInHorizontally(animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { width -> width / 3 } + fadeIn(tween(180)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(140)) { width -> -width / 3 } + fadeOut(tween(140)))
                        },
                        label = "MiniPlayerTrackTransition",
                        modifier = Modifier.weight(1f)
                    ) { curTrack ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = curTrack.title,
                                    fontFamily = CrankrDisplayFont,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (sleepTimerBadge != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MiniPlayerAccent.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = sleepTimerBadge,
                                            fontFamily = CrankrBodyFont,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiniPlayerAccent,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = curTrack.artist,
                                fontFamily = CrankrBodyFont,
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Media Controls: Previous, Play/Pause, Next (Tanpa tombol love & visualizer amp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Tombol Previous
                        IconButton(
                            onClick = onPreviousClick,
                            modifier = Modifier
                                .size(34.dp)
                                .springPressable(scaleDown = 0.92f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Sebelumnya",
                                tint = TextWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Tombol Play / Pause Utama (Kinetic Mono Chalk White Circle)
                        Surface(
                            onClick = onPlayPauseClick,
                            shape = CircleShape,
                            color = MiniPlayerAccent,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .springPressable(scaleDown = 0.95f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = OnPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Tombol Next
                        IconButton(
                            onClick = onNextClick,
                            modifier = Modifier
                                .size(34.dp)
                                .springPressable(scaleDown = 0.92f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Berikutnya",
                                tint = TextWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpinningVinylDisc(
    coverUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylRotation"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                if (isPlaying) {
                    rotationZ = rotation
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Vinyl Body & Grooves Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val radius = size.minDimension / 2f

            // Outer Vinyl Disc
            drawCircle(
                color = Color(0xFF141414),
                radius = radius
            )
            // Outer Rim
            drawCircle(
                color = Color(0xFF282828),
                radius = radius,
                style = Stroke(width = 1.2f)
            )
            // Concentric Grooves
            drawCircle(
                color = Color(0xFF222222),
                radius = radius * 0.85f,
                style = Stroke(width = 0.8f)
            )
            drawCircle(
                color = Color(0xFF1E1E1E),
                radius = radius * 0.70f,
                style = Stroke(width = 0.8f)
            )
            drawCircle(
                color = Color(0xFF242424),
                radius = radius * 0.55f,
                style = Stroke(width = 0.8f)
            )
        }

        // Center Label (Cover Art or Amber Circle)
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A1C12))
                .border(0.8.dp, MiniPlayerAccent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiniPlayerAccent.copy(alpha = 0.35f))
                )
            }

            // Center Spindle Hole
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
            )
        }
    }
}

@Composable
fun MiniWaveformEqualizer(
    color: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val b1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val b2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(290, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val b3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(460, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )

    val h1 by animateFloatAsState(
        targetValue = if (isActive) b1 else 0.2f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "h1"
    )
    val h2 by animateFloatAsState(
        targetValue = if (isActive) b2 else 0.2f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "h2"
    )
    val h3 by animateFloatAsState(
        targetValue = if (isActive) b3 else 0.2f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "h3"
    )

    Row(
        modifier = modifier.height(13.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val bars = listOf(h1, h2, h3)
        bars.forEach { heightFraction ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(heightFraction.coerceIn(0.15f, 1f))
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}
