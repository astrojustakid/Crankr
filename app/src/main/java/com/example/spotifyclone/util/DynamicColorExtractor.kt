package com.example.spotifyclone.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import com.example.spotifyclone.theme.CrimsonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class ExtractedPalette(
    val dominantColor: Color,
    val secondaryColor: Color,
    val accentColor: Color,
    val dominantColorInt: Int = dominantColor.toArgb()
)

object DynamicColorExtractor {

    val defaultPalette = ExtractedPalette(
        dominantColor = CrimsonRed,
        secondaryColor = Color(0xFF240A10),
        accentColor = CrimsonRed,
        dominantColorInt = android.graphics.Color.parseColor("#E50914")
    )

    private val cache = ConcurrentHashMap<String, ExtractedPalette>()

    suspend fun extractColors(context: Context, imageUrl: String?): ExtractedPalette = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) return@withContext defaultPalette

        cache[imageUrl]?.let { return@withContext it }

        try {
            val imageLoader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // Software bitmap needed for pixel inspection
                .size(64, 64) // Fast thumbnail for Palette
                .build()

            val result = imageLoader.execute(request)
            val drawable = result.drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap

            if (bitmap != null) {
                val palette = analyzeBitmapWithPalette(bitmap)
                cache[imageUrl] = palette
                return@withContext palette
            }
        } catch (_: Exception) {}

        defaultPalette
    }

    private fun analyzeBitmapWithPalette(bitmap: Bitmap): ExtractedPalette {
        try {
            // Android Palette API extraction
            val palette = Palette.from(bitmap).generate()
            val dominantSwatch = palette.dominantSwatch
                ?: palette.vibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch

            if (dominantSwatch != null) {
                val primaryInt = dominantSwatch.rgb
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(primaryInt, hsv)
                val primaryColor = Color(primaryInt)

                hsv[1] = (hsv[1] * 0.85f).coerceIn(0.2f, 0.95f)
                hsv[2] = (hsv[2] * 0.35f).coerceIn(0.08f, 0.28f)
                val secondary = Color(android.graphics.Color.HSVToColor(hsv))

                return ExtractedPalette(
                    dominantColor = primaryColor,
                    secondaryColor = secondary,
                    accentColor = primaryColor,
                    dominantColorInt = primaryInt
                )
            }
        } catch (_: Exception) {}

        return fallbackAnalyze(bitmap)
    }

    private fun fallbackAnalyze(bitmap: Bitmap): ExtractedPalette {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var highestScore = -1f
        var bestColor = -1

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        val hsv = FloatArray(3)

        for (pixel in pixels) {
            val alpha = android.graphics.Color.alpha(pixel)
            if (alpha < 128) continue

            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)

            android.graphics.Color.colorToHSV(pixel, hsv)
            val sat = hsv[1]
            val value = hsv[2]

            if (value in 0.14f..0.94f && sat > 0.12f) {
                rSum += r
                gSum += g
                bSum += b
                count++

                val score = (sat * 2.0f) + (value * 0.6f)
                if (score > highestScore) {
                    highestScore = score
                    bestColor = pixel
                }
            }
        }

        val primaryInt = when {
            bestColor != -1 -> bestColor
            count > 0 -> android.graphics.Color.rgb(
                (rSum / count).toInt(),
                (gSum / count).toInt(),
                (bSum / count).toInt()
            )
            else -> android.graphics.Color.rgb(229, 9, 20)
        }

        android.graphics.Color.colorToHSV(primaryInt, hsv)
        val accentHue = Color(primaryInt)

        hsv[1] = (hsv[1] * 0.85f).coerceIn(0.2f, 0.95f)
        hsv[2] = (hsv[2] * 0.35f).coerceIn(0.08f, 0.28f)
        val secondary = Color(android.graphics.Color.HSVToColor(hsv))

        return ExtractedPalette(
            dominantColor = accentHue,
            secondaryColor = secondary,
            accentColor = accentHue,
            dominantColorInt = primaryInt
        )
    }
}
