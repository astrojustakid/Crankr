package com.example.spotifyclone.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifyclone.R
import com.example.spotifyclone.theme.*

@Composable
fun CrankrSplashScreen(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )
    val barPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "barPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Neutral),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.crankrlogo),
                contentDescription = "CRANKR Logo",
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "CRANKR",
                fontFamily = CrankrDisplayFont,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = TextWhite,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "PURE ANALOG DISTORTION",
                fontFamily = CrankrDisplayFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Minimal audio loading bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0.4f, 0.8f, 1f, 0.6f, 0.3f).forEachIndexed { i, factor ->
                    val dynamicHeight = (16.dp * (factor * barPulse).coerceIn(0.25f, 1f))
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(dynamicHeight)
                            .background(Primary)
                    )
                }
            }
        }
    }
}

