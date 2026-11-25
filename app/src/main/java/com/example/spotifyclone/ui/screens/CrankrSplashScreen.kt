package com.example.spotifyclone.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifyclone.theme.CrankrDisplayFont
import com.example.spotifyclone.theme.PitchBlack
import com.example.spotifyclone.theme.Primary
import com.example.spotifyclone.theme.TextWhite
import com.example.spotifyclone.ui.components.CrankrMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Crankr 2.0 Animated Splash Screen (Option B — Clean Pulse "C"):
 * - Menggambar monogram geometris "C" satin-white dengan belahan tengah presisi.
 * - Garis gelombang suara (acoustic pulse wave) merah/aksen hidup & berdenyut lembut
 *   melintasi pusat huruf C, terinspirasi animasi logo vektor minimalis modern.
 * - Satu wordmark "CRANKR" bersih tanpa dobel teks atau elemen loading yang ramai.
 */
@Composable
fun CrankrSplashScreen(
    modifier: Modifier = Modifier
) {
    val arcProgress = remember { Animatable(0f) }
    val waveReveal = remember { Animatable(0f) }
    val wordmarkProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            arcProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 520,
                    easing = CrankrMotion.EaseOutStrong
                )
            )
        }
        launch {
            delay(110)
            waveReveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 540,
                    easing = CrankrMotion.EaseOutStrong
                )
            )
        }
        launch {
            delay(260)
            wordmarkProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = CrankrMotion.EaseOutStrong
                )
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "SplashWaveLoop")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    val ambientGlowColor = Primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ambientGlowColor.copy(alpha = 0.13f * arcProgress.value),
                        Color(0xFF0B0B0E),
                        PitchBlack
                    ),
                    radius = 640f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Vector Emblem: Option B (Clean Pulse "C")
            Canvas(
                modifier = Modifier
                    .size(136.dp)
                    .graphicsLayer {
                        val s = 0.90f + (0.10f * arcProgress.value)
                        scaleX = s
                        scaleY = s
                        alpha = arcProgress.value.coerceIn(0f, 1f)
                    }
            ) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                // 1. Subtle radial back-glow inside the C cavity
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientGlowColor.copy(alpha = 0.20f * waveReveal.value),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = w * 0.42f
                    ),
                    radius = w * 0.42f,
                    center = Offset(cx, cy)
                )

                // 2. Geometric satin-white "C" split horizontally on the left (Option B signature)
                val strokeWidth = w * 0.125f
                val arcDiameter = w * 0.64f
                val topLeft = Offset((w - arcDiameter) / 2f, (h - arcDiameter) / 2f)
                val arcSize = Size(arcDiameter, arcDiameter)
                val cColor = Color(0xFFF3F4F6)

                val sweepHalf = 132f * arcProgress.value
                // Top half of C (sweeps clockwise from left-center gap up to top-right opening)
                drawArc(
                    color = cColor,
                    startAngle = 186f,
                    sweepAngle = sweepHalf,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                // Bottom half of C (sweeps counter-clockwise from left-center gap down to bottom-right opening)
                drawArc(
                    color = cColor,
                    startAngle = 174f,
                    sweepAngle = -sweepHalf,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                // 3. Living horizontal audio waveform pulse slicing across the center of the C
                val reveal = waveReveal.value
                if (reveal > 0.01f) {
                    val startX = w * 0.06f
                    val endX = w * 0.94f
                    val currentEndX = startX + (endX - startX) * reveal
                    val maxAmp = h * 0.165f
                    val steps = 96

                    val wavePath = Path()
                    for (i in 0..steps) {
                        val frac = i / steps.toFloat()
                        val x = startX + (currentEndX - startX) * frac
                        // Normalize x across full width so envelope stays centered in the C
                        val normX = (x - startX) / (endX - startX)
                        val distFromCenter = (normX - 0.5f) / 0.135f
                        val envelope = exp(-(distFromCenter * distFromCenter).toDouble()).toFloat()
                        val harmonic = sin((normX * 7.5f * PI).toDouble() - wavePhase.toDouble()).toFloat()
                        val y = cy + harmonic * maxAmp * envelope * reveal

                        if (i == 0) {
                            wavePath.moveTo(x, y)
                        } else {
                            wavePath.lineTo(x, y)
                        }
                    }

                    // Soft outer glow pass
                    drawPath(
                        path = wavePath,
                        color = ambientGlowColor.copy(alpha = 0.30f * reveal),
                        style = Stroke(
                            width = 7.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    // Crisp core pulse line
                    drawPath(
                        path = wavePath,
                        color = ambientGlowColor,
                        style = Stroke(
                            width = 2.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Single clean wordmark (no duplicate text, no cluttered tagline)
            Text(
                text = "CRANKR",
                fontFamily = CrankrDisplayFont,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                letterSpacing = 4.5.sp,
                modifier = Modifier.graphicsLayer {
                    val p = wordmarkProgress.value
                    alpha = p
                    translationY = (1f - p) * 14.dp.toPx()
                }
            )
        }
    }
}
