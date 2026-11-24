package com.example.spotifyclone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifyclone.theme.*
import kotlin.math.atan2
import kotlin.math.PI

/**
 * Luminous hairline glass rim brush (Ref 3 glassmorphism without purple slop).
 */
fun glassHairlineBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.20f),
        Color.White.copy(alpha = 0.05f)
    )
)

/**
 * Reusable Frosted Glass Surface with 0.8.dp luminous hairline border.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = Surface.copy(alpha = 0.72f),
    borderWidth: Dp = 0.8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(borderWidth, glassHairlineBrush()), shape),
        content = content
    )
}

/**
 * Circular Frosted Glass Icon Button (Ref 2 & Ref 3).
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 18.dp,
    iconTint: Color = TextWhite,
    containerColor: Color = Color.White.copy(alpha = 0.09f)
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(0.8.dp, glassHairlineBrush()),
        modifier = modifier
            .size(size)
            .springPressable(0.92f)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Frosted Glass Filter Pill (Ref 3 Library / Home Quick Chips).
 */
@Composable
fun GlassFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Primary else Color.White.copy(alpha = 0.08f),
        border = if (selected) null else BorderStroke(0.8.dp, glassHairlineBrush()),
        modifier = modifier
            .springPressable(0.95f)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) OnPrimary else TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                fontFamily = CrankrBodyFont,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) OnPrimary else TextWhite
            )
        }
    }
}

/**
 * CircularRadialPlayerDeck (Signature Reference 1 Component):
 * Cover album bulat di tengah dengan bezel gelap taktil & busur progress melingkar (Arc Scrubber)
 * serta durasi lagu rapi di bagian bawah cincin. Mendukung tap-to-seek di cincin & swipe kiri/kanan.
 */
@Composable
fun CircularRadialPlayerDeck(
    coverUrl: String,
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val accentColor = Primary
    val trackBgColor = Color.White.copy(alpha = 0.12f)
    val bezelFill = Color.Black.copy(alpha = 0.36f)

    // Arc geometry: starts at 125° (bottom-left) and sweeps 290° clockwise to 55° (bottom-right),
    // leaving a clean 70° opening at the bottom center for the timestamp text.
    val startAngle = 125f
    val totalSweep = 290f

    var dragAccumulatedX by remember { mutableFloatStateOf(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(286.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulatedX = 0f },
                    onDragEnd = {
                        if (dragAccumulatedX < -70f) onSwipeLeft()
                        else if (dragAccumulatedX > 70f) onSwipeRight()
                        dragAccumulatedX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulatedX += dragAmount
                    }
                )
            }
            .pointerInput(safeDuration) {
                detectTapGestures { tapOffset ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = tapOffset.x - cx
                    val dy = tapOffset.y - cy
                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    val innerRadius = size.width * 0.36f
                    // Tap on the outer bezel ring seeks directly to that angle
                    if (dist >= innerRadius) {
                        var deg = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat()
                        if (deg < 0f) deg += 360f
                        var rel = deg - startAngle
                        if (rel < 0f) rel += 360f
                        if (rel <= totalSweep) {
                            val newFraction = (rel / totalSweep).coerceIn(0f, 1f)
                            onSeekTo((newFraction * safeDuration).toLong())
                        }
                    }
                }
            }
    ) {
        // Outer Tactile Dark Glass Bezel + Radial Arc Progress
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 3.2.dp.toPx()
            val bezelThickness = 22.dp.toPx()
            val ringInset = strokePx + 6.dp.toPx()
            val arcSize = Size(size.width - ringInset * 2, size.height - ringInset * 2)
            val topLeft = Offset(ringInset, ringInset)

            // Soft dark bezel ring behind the arc
            drawCircle(
                color = bezelFill,
                radius = (size.minDimension / 2f) - 2.dp.toPx()
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = (size.minDimension / 2f) - 2.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = (size.minDimension / 2f) - bezelThickness,
                style = Stroke(width = 1.dp.toPx())
            )

            // Inactive background arc
            drawArc(
                color = trackBgColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active progress arc
            if (progress > 0.002f) {
                drawArc(
                    color = accentColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweep * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        // Centered Circular Album Artwork
        Box(
            modifier = Modifier
                .size(214.dp)
                .shadow(18.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(CardBackground)
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Album Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Integrated Bottom Timestamp inside the ring gap (just like Ref 1)
        Text(
            text = formatRadialTime(currentPositionMs) + " / " + formatRadialTime(durationMs),
            fontFamily = CrankrBodyFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextWhite.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
        )
    }
}

private fun formatRadialTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}
