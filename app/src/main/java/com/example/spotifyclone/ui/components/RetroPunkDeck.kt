package com.example.spotifyclone.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.spotifyclone.R
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.theme.*
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput

enum class DeckVisualMode {
    COVER,
    CASSETTE,
    VINYL
}

@Composable
fun RetroPunkDeck(
    track: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    mode: DeckVisualMode,
    onToggleMode: () -> Unit,
    onSwipeRight: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Preserve continuous rotation angle across play/pause states
    var deckRotation by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val animatedDragX = remember { Animatable(0f) }
    val hapticFeedback = LocalHapticFeedback.current
    var hasTriggeredHaptic by remember { mutableStateOf(false) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(track.id) {
        animatedDragX.snapTo(0f)
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                withFrameMillis {
                    deckRotation = (deckRotation + 1.25f) % 360f
                }
            }
        }
    }

    val progress = (currentPositionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(265.dp)
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(track.id) {
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                        hasTriggeredHaptic = false
                    },
                    onDragEnd = {
                        val absX = kotlin.math.abs(totalDragX)
                        val absY = kotlin.math.abs(totalDragY)
                        if (absX > absY && absX > 60f) {
                            if (totalDragX > 0f) {
                                onSwipeRight?.invoke()
                            } else {
                                onSwipeLeft?.invoke()
                            }
                        } else if (absY > absX && totalDragY > 70f) {
                            onSwipeDown?.invoke()
                        }
                        coroutineScope.launch {
                            animatedDragX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            hasTriggeredHaptic = false
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            animatedDragX.animateTo(0f)
                            hasTriggeredHaptic = false
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                        if (kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY)) {
                            coroutineScope.launch {
                                val newX = (animatedDragX.value + dragAmount.x).coerceIn(-140f, 140f)
                                animatedDragX.snapTo(newX)
                                if (kotlin.math.abs(newX) >= 60f && !hasTriggeredHaptic) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    hasTriggeredHaptic = true
                                } else if (kotlin.math.abs(newX) < 60f && hasTriggeredHaptic) {
                                    hasTriggeredHaptic = false
                                }
                            }
                        }
                    }
                )
            }
            .clickable { onToggleMode() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = animatedDragX.value
            },
            contentAlignment = Alignment.Center
        ) {
            when (mode) {
            DeckVisualMode.COVER -> {
                val coverScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1.0f else 0.94f,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "CoverPlaybackScale"
                )
                val coverAlpha by animateFloatAsState(
                    targetValue = if (isPlaying) 1.0f else 0.88f,
                    animationSpec = tween(280),
                    label = "CoverPlaybackAlpha"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                            alpha = coverAlpha
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Border, RoundedCornerShape(16.dp))
                        .background(Surface)
                ) {
                    AnimatedContent(
                        targetState = track.coverUrl,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400, easing = LinearEasing)) togetherWith
                                fadeOut(animationSpec = tween(350, easing = LinearEasing))
                        },
                        label = "CoverArtCrossfade"
                    ) { targetCoverUrl ->
                        if (!targetCoverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(targetCoverUrl)
                                    .crossfade(true)
                                    .crossfade(400)
                                    .build(),
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.crankrlogo),
                                    contentDescription = track.title,
                                    modifier = Modifier.size(100.dp)
                                )
                            }
                        }
                    }
                }
            }
            DeckVisualMode.CASSETTE -> {
                RetroCassetteTape(
                    track = track,
                    isPlaying = isPlaying,
                    progress = progress,
                    rotationAngle = deckRotation
                )
            }
            DeckVisualMode.VINYL -> {
                RetroVinylTurntable(
                    track = track,
                    isPlaying = isPlaying,
                    progress = progress,
                    rotationAngle = deckRotation
                )
            }
        }
    }
}
}

@Composable
fun RetroCassetteTape(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    rotationAngle: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.96f)
            .height(235.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF141417))
            .border(1.dp, BorderSubtle, RoundedCornerShape(3.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Cassette Top Header Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFE60023))
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CRANKR STEREO 90",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "HIGH-BIAS TYPE II",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            // 2. Vintage Paper Label (Beige / Kraft texture with scribbled track title)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFECE4D0))
                    .border(1.dp, Color(0xFFB5A992), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Handwritten track title & artist
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "A  ${track.title}",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                fontFamily = CrankrBodyFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF7A1C1C),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = if (isPlaying) "PLAY" else "PAUSE",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPlaying) Color(0xFFE60023) else Color(0xFF555555)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Transparent Window with Left & Right Rotating Tape Spools
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0F0F12))
                            .border(1.dp, Color(0xFF333338), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Canvas drawing magnetic tape thickness and rotating gear sprockets
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val leftCenter = Offset(w * 0.28f, h * 0.5f)
                            val rightCenter = Offset(w * 0.72f, h * 0.5f)

                            // Magnetic tape spool packs (Left spool empties, Right spool fills)
                            val minTapeR = h * 0.26f
                            val maxTapeR = h * 0.44f

                            val leftTapeR = minTapeR + (maxTapeR - minTapeR) * (1f - progress)
                            val rightTapeR = minTapeR + (maxTapeR - minTapeR) * progress

                            // Left tape pack (brown magnetic tape)
                            drawCircle(
                                color = Color(0xFF4A2B18),
                                radius = leftTapeR,
                                center = leftCenter
                            )
                            // Right tape pack (brown magnetic tape)
                            drawCircle(
                                color = Color(0xFF4A2B18),
                                radius = rightTapeR,
                                center = rightCenter
                            )

                            // Connecting magnetic tape line
                            drawLine(
                                color = Color(0xFF382012),
                                start = Offset(leftCenter.x, leftCenter.y + leftTapeR * 0.9f),
                                end = Offset(rightCenter.x, rightCenter.y + rightTapeR * 0.9f),
                                strokeWidth = 5f
                            )

                            // Sprocket hubs (White plastic wheels with 6 teeth)
                            fun drawCassetteSprocket(center: Offset, angle: Float) {
                                val hubRadius = h * 0.20f
                                drawCircle(
                                    color = Color(0xFFEAEAEA),
                                    radius = hubRadius,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFF0D0D10),
                                    radius = hubRadius * 0.55f,
                                    center = center
                                )

                                // 6 Teeth
                                for (i in 0 until 6) {
                                    val toothAngle = Math.toRadians((angle + i * 60).toDouble())
                                    val tx = center.x + (hubRadius * 0.75f * cos(toothAngle)).toFloat()
                                    val ty = center.y + (hubRadius * 0.75f * sin(toothAngle)).toFloat()
                                    drawCircle(
                                        color = Color(0xFFE60023),
                                        radius = hubRadius * 0.16f,
                                        center = Offset(tx, ty)
                                    )
                                }
                            }

                            drawCassetteSprocket(leftCenter, rotationAngle)
                            drawCassetteSprocket(rightCenter, rotationAngle)

                            // Center ruler measurement lines
                            val rulerY = h * 0.5f
                            drawLine(
                                color = Color(0x66FFFFFF),
                                start = Offset(w * 0.42f, rulerY),
                                end = Offset(w * 0.58f, rulerY),
                                strokeWidth = 1f
                            )
                        }

                        // Center progress readout
                        Text(
                            text = "%02d:%02d".format((progress * 100).toInt(), (progress * 60).toInt() % 60),
                            fontFamily = CrankrDisplayFont,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0x99FFFFFF)
                        )
                    }
                }
            }

            // 3. Bottom Trapezoid Head Area with Screws
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(Color(0xFF1D1D22)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brass screw dots
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF707075))
                )
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0xFF0F0F12))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF707075))
                )
            }
        }
    }
}

@Composable
fun RetroVinylTurntable(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    rotationAngle: Float,
    modifier: Modifier = Modifier
) {
    // Realistic tonearm angle:
    // Rest position: -18f (resting on tonearm clip)
    // Active playback: 14f (lead-in groove) progressing smoothly to 30f (run-out groove near label)
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 14f + (progress * 16f) else -18f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tonearm_angle"
    )

    // Slight needle contact vibration when playing
    val needleInfiniteTransition = rememberInfiniteTransition(label = "needle_vibe")
    val needleJitter by needleInfiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(durationMillis = 180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "needle_jitter"
    )

    Box(
        modifier = modifier
            .fillMaxWidth(0.96f)
            .height(245.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF141418))
            .border(1.dp, BorderSubtle, RoundedCornerShape(3.dp))
            .padding(8.dp)
    ) {
        // Turntable Plinth Background Accents: Screws in 4 corners
        Canvas(modifier = Modifier.fillMaxSize()) {
            val screwColor = Color(0xFF44444C)
            val screwR = 4f
            drawCircle(screwColor, radius = screwR, center = Offset(12f, 12f))
            drawCircle(screwColor, radius = screwR, center = Offset(size.width - 12f, 12f))
            drawCircle(screwColor, radius = screwR, center = Offset(12f, size.height - 12f))
            drawCircle(screwColor, radius = screwR, center = Offset(size.width - 12f, size.height - 12f))

            // Brushed metal line separator between platter area and tonearm deck
            drawLine(
                color = Color(0x22FFFFFF),
                start = Offset(size.width * 0.73f, 16f),
                end = Offset(size.width * 0.73f, size.height - 16f),
                strokeWidth = 1f
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spinning Vinyl Platter (Left side)
            Box(
                modifier = Modifier
                    .size(225.dp)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                // Platter Rim & Strobe Dots
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotationAngle)
                ) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Platter Rim (Heavy Aluminum)
                    drawCircle(
                        color = Color(0xFF09090B),
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF282832),
                        radius = radius - 1f,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )

                    // Strobe Calibrator Dots around rim
                    val dotCount = 36
                    for (i in 0 until dotCount) {
                        val angle = Math.toRadians((i * (360f / dotCount)).toDouble())
                        val dx = center.x + ((radius - 4f) * cos(angle)).toFloat()
                        val dy = center.y + ((radius - 4f) * sin(angle)).toFloat()
                        drawCircle(
                            color = if (i % 2 == 0) Color(0x44FFFFFF) else Color(0x22FFFFFF),
                            radius = 1.2f,
                            center = Offset(dx, dy)
                        )
                    }

                    // Concentric Micro-Grooves with realistic light reflections
                    val grooveCount = 20
                    for (i in 1..grooveCount) {
                        val gRadius = radius * (0.38f + 0.56f * (i.toFloat() / grooveCount))
                        drawCircle(
                            color = Color(0x18FFFFFF),
                            radius = gRadius,
                            center = center,
                            style = Stroke(width = 1f)
                        )
                    }

                    // Rotating Specular Sheen (Soft Light Wedges)
                    val sheenBrush1 = Brush.radialGradient(
                        colors = listOf(Color(0x2EFFFFFF), Color.Transparent),
                        center = Offset(center.x + radius * 0.45f, center.y - radius * 0.45f),
                        radius = radius * 0.85f
                    )
                    drawCircle(brush = sheenBrush1, radius = radius * 0.95f, center = center)

                    val sheenBrush2 = Brush.radialGradient(
                        colors = listOf(Color(0x2EFFFFFF), Color.Transparent),
                        center = Offset(center.x - radius * 0.45f, center.y + radius * 0.45f),
                        radius = radius * 0.85f
                    )
                    drawCircle(brush = sheenBrush2, radius = radius * 0.95f, center = center)
                }

                // Center Label with Album Cover Artwork Thumbnail
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .border(2.dp, CrimsonRed, CircleShape)
                        .background(Color(0xFF1F1F24)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationAngle)
                    )

                    // Spindle Silver Ring & Hole
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0E0E0))
                            .border(1.2.dp, Color(0xFF555555), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                        )
                    }
                }
            }

            // Right side: Controls & Deck Label
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Brand Text
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "CRANKR-1200",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB0B0B8),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "DIRECT DRIVE",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 7.5.sp,
                        color = TextMuted
                    )
                }

                // Speed & Strobe Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) Color(0xFF00E676) else TextMuted)
                    )
                    Text(
                        text = if (isPlaying) "33⅓ RPM" else "STOPPED",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) CrimsonRed else TextMuted
                    )
                }

                // Pitch slider graphic indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF0D0D10))
                            .border(0.5.dp, Color(0xFF33333A), RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (isPlaying) CrimsonRed else Color(0xFF66666E))
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "PITCH",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 7.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Full Realistic Tonearm Overlay (Pivoting from top-right over vinyl record)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val pivotX = size.width * 0.81f
            val pivotY = size.height * 0.22f

            // 1. Pivot Base (Tonearm Gimbal & Height Ring)
            drawCircle(
                color = Color(0xFF0A0A0C),
                radius = 20f,
                center = Offset(pivotX, pivotY)
            )
            drawCircle(
                color = Color(0xFF484854),
                radius = 20f,
                center = Offset(pivotX, pivotY),
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = Color(0xFF282832),
                radius = 14f,
                center = Offset(pivotX, pivotY)
            )
            drawCircle(
                color = CrimsonRed,
                radius = 5f,
                center = Offset(pivotX, pivotY)
            )

            // 2. Tonearm Rest Clip (beside the platter)
            val restX = size.width * 0.77f
            val restY = size.height * 0.65f
            drawLine(
                color = Color(0xFF3A3A44),
                start = Offset(restX - 6f, restY),
                end = Offset(restX + 6f, restY),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )

            // 3. Pivoting Tonearm Assembly
            val activeAngle = tonearmAngle + (if (isPlaying) needleJitter else 0f)

            rotate(degrees = activeAngle, pivot = Offset(pivotX, pivotY)) {
                // A. Rear Counterweight (extends upward-right behind pivot)
                drawLine(
                    color = Color(0xFF35353E),
                    start = Offset(pivotX, pivotY),
                    end = Offset(pivotX + 8f, pivotY - 26f),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
                // Counterweight Ring (textured metal cylinder)
                drawCircle(
                    color = Color(0xFF6B6B78),
                    radius = 9f,
                    center = Offset(pivotX + 6f, pivotY - 20f)
                )
                drawCircle(
                    color = Color(0xFF1B1B22),
                    radius = 4f,
                    center = Offset(pivotX + 6f, pivotY - 20f)
                )

                // B. S-Curved Chrome Tonearm Tube
                val armLength = size.height * 0.70f
                val armPath = Path().apply {
                    moveTo(pivotX, pivotY)
                    // First curve away towards center
                    cubicTo(
                        pivotX - 18f, pivotY + armLength * 0.32f,
                        pivotX - 42f, pivotY + armLength * 0.62f,
                        pivotX - 58f, pivotY + armLength
                    )
                }
                // Tonearm drop shadow
                drawPath(
                    path = armPath,
                    color = Color(0x66000000),
                    style = Stroke(width = 5.5f, cap = StrokeCap.Round)
                )
                // Chrome arm body
                drawPath(
                    path = armPath,
                    color = Color(0xFFE8E8EE),
                    style = Stroke(width = 4.2f, cap = StrokeCap.Round)
                )
                // Chrome highlight line
                drawPath(
                    path = armPath,
                    color = Color.White,
                    style = Stroke(width = 1.2f, cap = StrokeCap.Round)
                )

                // C. Headshell & Cartridge (Red & Black angled cartridge at end of arm)
                val headX = pivotX - 58f
                val headY = pivotY + armLength
                val headAngle = -22f

                rotate(degrees = headAngle, pivot = Offset(headX, headY)) {
                    // Cartridge connector collar
                    drawCircle(
                        color = Color(0xFF404048),
                        radius = 4.5f,
                        center = Offset(headX, headY)
                    )

                    // Headshell body
                    val cartridgeEnd = Offset(headX - 16f, headY + 22f)
                    drawLine(
                        color = Color(0xFF1E1E24),
                        start = Offset(headX, headY),
                        end = cartridgeEnd,
                        strokeWidth = 9f,
                        cap = StrokeCap.Square
                    )
                    drawLine(
                        color = CrimsonRed,
                        start = Offset(headX - 6f, headY + 8f),
                        end = cartridgeEnd,
                        strokeWidth = 7f,
                        cap = StrokeCap.Square
                    )

                    // Stylus Tip & Contact Needle (Glowing white dot on vinyl)
                    drawCircle(
                        color = if (isPlaying) Color.White else Color(0xFFCCCCCC),
                        radius = if (isPlaying) 2.5f else 2f,
                        center = cartridgeEnd
                    )
                    if (isPlaying) {
                        drawCircle(
                            color = CrimsonRed.copy(alpha = 0.5f),
                            radius = 6f,
                            center = cartridgeEnd
                        )
                    }
                }
            }
        }
    }
}
