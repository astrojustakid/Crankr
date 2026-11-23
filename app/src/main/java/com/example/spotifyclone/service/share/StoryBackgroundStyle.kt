package com.example.spotifyclone.service.share

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.spotifyclone.theme.PitchBlack

enum class StoryBackgroundStyle(val displayName: String) {
    SOLID_BLACK("Solid Black"),
    SOLID_DOMINANT("Warna Dominan"),
    GRADIENT_DOMINANT("Gradien Cover"),
    GRADIENT_CRANKR("Gradien Crankr")
}

data class StoryColorPalette(
    val dominantColor: Color = Color(0xFFE50914),
    val secondaryColor: Color = Color(0xFF240A10),
    val dominantColorInt: Int = android.graphics.Color.parseColor("#E50914")
) {
    val pitchBlackColor = PitchBlack
    val pitchBlackInt: Int = android.graphics.Color.parseColor("#0D0D0D")
    val crankrBloodRedInt: Int = android.graphics.Color.parseColor("#5A050A")
    val crankrBloodRedColor = Color(0xFF5A050A)

    fun getBrush(style: StoryBackgroundStyle): Brush {
        return when (style) {
            StoryBackgroundStyle.SOLID_BLACK -> Brush.linearGradient(
                listOf(pitchBlackColor, pitchBlackColor)
            )
            StoryBackgroundStyle.SOLID_DOMINANT -> Brush.linearGradient(
                listOf(dominantColor, dominantColor)
            )
            StoryBackgroundStyle.GRADIENT_DOMINANT -> Brush.verticalGradient(
                listOf(dominantColor, pitchBlackColor)
            )
            StoryBackgroundStyle.GRADIENT_CRANKR -> Brush.verticalGradient(
                listOf(pitchBlackColor, crankrBloodRedColor)
            )
        }
    }

    fun createAndroidPaint(style: StoryBackgroundStyle, width: Float, height: Float): Paint {
        return Paint().apply {
            isAntiAlias = true
            when (style) {
                StoryBackgroundStyle.SOLID_BLACK -> {
                    color = pitchBlackInt
                    shader = null
                }
                StoryBackgroundStyle.SOLID_DOMINANT -> {
                    color = dominantColorInt
                    shader = null
                }
                StoryBackgroundStyle.GRADIENT_DOMINANT -> {
                    shader = LinearGradient(
                        0f, 0f, 0f, height,
                        intArrayOf(dominantColorInt, pitchBlackInt),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                StoryBackgroundStyle.GRADIENT_CRANKR -> {
                    shader = LinearGradient(
                        0f, 0f, 0f, height,
                        intArrayOf(pitchBlackInt, crankrBloodRedInt),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
            }
        }
    }
}
