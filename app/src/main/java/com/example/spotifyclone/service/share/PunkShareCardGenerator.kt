package com.example.spotifyclone.service.share

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.util.DynamicColorExtractor
import com.example.spotifyclone.util.ExtractedPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PunkShareCardGenerator {

    suspend fun getCoverBitmap(context: Context, coverUrl: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (coverUrl.isNullOrBlank()) return@withContext null
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .allowHardware(false)
                .build()
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            if (result is android.graphics.drawable.BitmapDrawable) {
                result.bitmap
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveStoryPalette(coverBitmap: Bitmap?, palette: ExtractedPalette?): StoryColorPalette {
        if (palette != null) {
            return StoryColorPalette(
                dominantColor = palette.dominantColor,
                secondaryColor = palette.secondaryColor,
                dominantColorInt = palette.dominantColorInt
            )
        }
        if (coverBitmap != null) {
            try {
                val p = Palette.from(coverBitmap).generate()
                val swatch = p.dominantSwatch ?: p.vibrantSwatch ?: p.darkVibrantSwatch ?: p.mutedSwatch
                if (swatch != null) {
                    return StoryColorPalette(
                        dominantColor = Color(swatch.rgb),
                        secondaryColor = Color(0xFF240A10),
                        dominantColorInt = swatch.rgb
                    )
                }
            } catch (_: Exception) {}
        }
        val def = DynamicColorExtractor.defaultPalette
        return StoryColorPalette(
            dominantColor = def.dominantColor,
            secondaryColor = def.secondaryColor,
            dominantColorInt = def.dominantColorInt
        )
    }

    suspend fun generateSongCard(
        context: Context,
        track: Track,
        coverBitmap: Bitmap?,
        backgroundStyle: StoryBackgroundStyle = StoryBackgroundStyle.SOLID_BLACK,
        palette: ExtractedPalette? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val width = 1080
            val height = 1920
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val storyPalette = resolveStoryPalette(coverBitmap, palette)

            // 1. Draw 9:16 Story Canvas Background
            val bgPaint = storyPalette.createAndroidPaint(backgroundStyle, width.toFloat(), height.toFloat())
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // 2. Measure & Layout Centered Floating Song Card
            val cardWidth = 840f
            val cardPadding = 48f
            val coverSize = cardWidth - (cardPadding * 2f) // 744x744

            val titlePaint = TextPaint().apply {
                color = android.graphics.Color.WHITE
                textSize = 50f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val titleLayout = StaticLayout.Builder.obtain(
                track.title, 0, track.title.length, titlePaint, coverSize.toInt()
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .build()

            val artistTextSize = 34f
            val logoRowHeight = 44f
            val spacingCoverToTitle = 44f
            val spacingTitleToArtist = 16f
            val spacingArtistToLogo = 52f

            val cardContentHeight = cardPadding + coverSize + spacingCoverToTitle +
                    titleLayout.height + spacingTitleToArtist + artistTextSize +
                    spacingArtistToLogo + logoRowHeight + cardPadding

            val cardLeft = (width - cardWidth) / 2f
            val cardTop = (height - cardContentHeight) / 2f
            val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardContentHeight)

            // Card Drop Shadow
            val shadowPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#44000000")
                setShadowLayer(40f, 0f, 20f, android.graphics.Color.parseColor("#80000000"))
            }
            canvas.drawRoundRect(cardRect, 32f, 32f, shadowPaint)

            // Card Background (Deep Charcoal #141416)
            val cardBgPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#141416")
                isAntiAlias = true
            }
            canvas.drawRoundRect(cardRect, 32f, 32f, cardBgPaint)

            // Card Subtle Border
            val cardBorderPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#28282D")
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                isAntiAlias = true
            }
            canvas.drawRoundRect(cardRect, 32f, 32f, cardBorderPaint)

            // 3. Album Cover Art inside Card
            val coverLeft = cardLeft + cardPadding
            val coverTop = cardTop + cardPadding
            val coverRect = RectF(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize)

            if (coverBitmap != null) {
                val roundCover = getRoundedCornerBitmap(coverBitmap, 20f, coverSize.toInt(), coverSize.toInt())
                canvas.drawBitmap(roundCover, coverLeft, coverTop, null)
            } else {
                val fallbackPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#26262B")
                    isAntiAlias = true
                }
                canvas.drawRoundRect(coverRect, 20f, 20f, fallbackPaint)
                val notePaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 120f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.drawText("♪", coverLeft + coverSize / 2f, coverTop + coverSize / 2f + 40f, notePaint)
            }

            // Subtle border on album cover
            val coverBorderPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#22FFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 2f
                isAntiAlias = true
            }
            canvas.drawRoundRect(coverRect, 20f, 20f, coverBorderPaint)

            // 4. Track Title & Artist
            val textLeft = coverLeft
            val titleY = coverTop + coverSize + spacingCoverToTitle

            canvas.save()
            canvas.translate(textLeft, titleY)
            titleLayout.draw(canvas)
            canvas.restore()

            val artistY = titleY + titleLayout.height + spacingTitleToArtist + artistTextSize * 0.85f
            val artistPaint = TextPaint().apply {
                color = android.graphics.Color.parseColor("#A0A0A5")
                textSize = artistTextSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val ellipsizedArtist = android.text.TextUtils.ellipsize(
                track.artist, artistPaint, coverSize, android.text.TextUtils.TruncateAt.END
            ).toString()
            canvas.drawText(ellipsizedArtist, textLeft, artistY, artistPaint)

            // 5. Crankr Text Logo at the Very Bottom of Card
            val logoRowY = cardTop + cardContentHeight - cardPadding - 10f
            val logoSize = 36f

            val logoBitmap = try {
                BitmapFactory.decodeResource(context.resources, com.example.spotifyclone.R.drawable.crankr_logo)
            } catch (_: Exception) {
                null
            }

            val logoTextPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#8E8E93")
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.18f
                isAntiAlias = true
            }
            val logoText = "CRANKR"
            val textBounds = Rect()
            logoTextPaint.getTextBounds(logoText, 0, logoText.length, textBounds)

            val totalLogoW = if (logoBitmap != null) (logoSize + 12f + textBounds.width()) else textBounds.width().toFloat()
            val logoStartX = cardLeft + (cardWidth - totalLogoW) / 2f

            if (logoBitmap != null) {
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoSize.toInt(), logoSize.toInt(), true)
                canvas.drawBitmap(scaledLogo, logoStartX, logoRowY - logoSize + 4f, null)
                canvas.drawText(logoText, logoStartX + logoSize + 12f, logoRowY - 4f, logoTextPaint)
            } else {
                canvas.drawText(logoText, logoStartX, logoRowY, logoTextPaint)
            }

            saveBitmapToCache(context, bitmap, "crankr_song_${track.id}.png")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun generateLyricCard(
        context: Context,
        track: Track,
        coverBitmap: Bitmap?,
        lyrics: List<String>,
        backgroundStyle: StoryBackgroundStyle = StoryBackgroundStyle.SOLID_BLACK,
        palette: ExtractedPalette? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val width = 1080
            val height = 1920
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val storyPalette = resolveStoryPalette(coverBitmap, palette)

            // 1. Draw 9:16 Story Canvas Background
            val bgPaint = storyPalette.createAndroidPaint(backgroundStyle, width.toFloat(), height.toFloat())
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // 2. Measure & Layout Centered Floating Lyric Card
            val cardWidth = 840f
            val cardHeight = 1120f
            val cardLeft = (width - cardWidth) / 2f
            val cardTop = (height - cardHeight) / 2f
            val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)

            // Card Drop Shadow
            val shadowPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#44000000")
                setShadowLayer(40f, 0f, 20f, android.graphics.Color.parseColor("#80000000"))
            }
            canvas.drawRoundRect(cardRect, 32f, 32f, shadowPaint)

            // Card Background (#141416)
            val cardBgPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#141416")
                isAntiAlias = true
            }
            canvas.drawRoundRect(cardRect, 32f, 32f, cardBgPaint)

            // Card Subtle Border
            val cardBorderPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#28282D")
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                isAntiAlias = true
            }
            canvas.drawRoundRect(cardRect, 32f, 32f, cardBorderPaint)

            // 3. Middle Area: Centered Multi-line Lyric Quote (1 to 5 lines)
            val safeLyrics = if (lyrics.isEmpty()) listOf("♪ Crankr Punk Rock Audio ♪") else lyrics.take(5)
            val lineCount = safeLyrics.size

            val maxChars = safeLyrics.maxOfOrNull { it.length } ?: 0
            val baseSize = when (lineCount) {
                1 -> 58f
                2 -> 50f
                3 -> 44f
                4 -> 38f
                else -> 33f
            }
            val lengthScale = when {
                maxChars > 45 -> 0.82f
                maxChars > 35 -> 0.90f
                else -> 1.0f
            }
            val lyricTextSize = (baseSize * lengthScale).coerceAtLeast(26f)
            val lineSpacingMult = 1.32f

            val lyricPaint = TextPaint().apply {
                color = android.graphics.Color.WHITE
                textSize = lyricTextSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val lyricContentWidth = 660
            val layouts = safeLyrics.map { line ->
                StaticLayout.Builder.obtain(line, 0, line.length, lyricPaint, lyricContentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(8f, lineSpacingMult)
                    .setMaxLines(2)
                    .build()
            }

            val lineGap = lyricTextSize * 0.40f
            val totalLinesHeight = layouts.sumOf { it.height } + ((layouts.size - 1) * lineGap)
            val quoteMarkSize = (lyricTextSize * 1.8f).coerceIn(60f, 110f)
            val totalLyricBlockH = quoteMarkSize * 0.6f + totalLinesHeight + quoteMarkSize * 0.6f

            // The middle lyric area is between cardTop + 40f and footerDividerY - 20f
            val footerDividerY = cardTop + cardHeight - 170f
            val lyricAreaCenterY = (cardTop + 40f + footerDividerY - 20f) / 2f
            var curY = lyricAreaCenterY - (totalLyricBlockH / 2f)

            // Red vertical accent bar on left of quote
            val redBarPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#E50914")
                isAntiAlias = true
            }
            val barLeft = cardLeft + 54f
            canvas.drawRoundRect(
                RectF(barLeft, curY + quoteMarkSize * 0.25f, barLeft + 6f, curY + totalLyricBlockH - quoteMarkSize * 0.25f),
                3f, 3f, redBarPaint
            )

            // Opening Quote “
            val quotePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#E50914")
                textSize = quoteMarkSize
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("“", barLeft + 24f, curY + quoteMarkSize * 0.65f, quotePaint)
            curY += quoteMarkSize * 0.70f

            // Draw each lyric line
            layouts.forEach { layout ->
                canvas.save()
                canvas.translate(barLeft + 28f, curY)
                layout.draw(canvas)
                canvas.restore()
                curY += layout.height + lineGap
            }

            // Closing Quote ”
            canvas.drawText("”", cardLeft + cardWidth - 90f, curY + quoteMarkSize * 0.35f, quotePaint)

            // 4. Bottom Footer Section inside Card:
            // Divider
            val footerDivPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#25252C")
                strokeWidth = 2f
            }
            canvas.drawLine(cardLeft + 48f, footerDividerY, cardLeft + cardWidth - 48f, footerDividerY, footerDivPaint)

            // Thumbnail Album Cover on Left
            val thumbSize = 96f
            val thumbLeft = cardLeft + 48f
            val thumbTop = footerDividerY + 28f
            val thumbRect = RectF(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize)

            if (coverBitmap != null) {
                val roundThumb = getRoundedCornerBitmap(coverBitmap, 12f, thumbSize.toInt(), thumbSize.toInt())
                canvas.drawBitmap(roundThumb, thumbLeft, thumbTop, null)
            } else {
                val fbPaint = Paint().apply { color = android.graphics.Color.parseColor("#26262B") }
                canvas.drawRoundRect(thumbRect, 12f, 12f, fbPaint)
                val notePaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 40f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.drawText("♪", thumbLeft + thumbSize / 2f, thumbTop + thumbSize / 2f + 14f, notePaint)
            }

            val thumbBorderPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#22FFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                isAntiAlias = true
            }
            canvas.drawRoundRect(thumbRect, 12f, 12f, thumbBorderPaint)

            // Middle: Title & Artist beside thumbnail
            val textLeft = thumbLeft + thumbSize + 22f
            val availableMetaW = 440f

            val titlePaint = TextPaint().apply {
                color = android.graphics.Color.WHITE
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val ellipsizedTitle = android.text.TextUtils.ellipsize(
                track.title, titlePaint, availableMetaW, android.text.TextUtils.TruncateAt.END
            ).toString()
            canvas.drawText(ellipsizedTitle, textLeft, thumbTop + 40f, titlePaint)

            val artistPaint = TextPaint().apply {
                color = android.graphics.Color.parseColor("#A0A0A5")
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val ellipsizedArtist = android.text.TextUtils.ellipsize(
                track.artist, artistPaint, availableMetaW, android.text.TextUtils.TruncateAt.END
            ).toString()
            canvas.drawText(ellipsizedArtist, textLeft, thumbTop + 78f, artistPaint)

            // Right side of footer: Crankr logo & text
            val logoSize = 34f
            val logoBitmap = try {
                BitmapFactory.decodeResource(context.resources, com.example.spotifyclone.R.drawable.crankr_logo)
            } catch (_: Exception) {
                null
            }
            val logoRight = cardLeft + cardWidth - 48f
            val logoTop = thumbTop + (thumbSize - logoSize) / 2f

            val wmTextPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#8E8E93")
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                letterSpacing = 0.15f
                isAntiAlias = true
            }

            if (logoBitmap != null) {
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoSize.toInt(), logoSize.toInt(), true)
                canvas.drawBitmap(scaledLogo, logoRight - logoSize, logoTop - 6f, null)
                canvas.drawText("CRANKR", logoRight, logoTop + logoSize + 14f, wmTextPaint)
            } else {
                canvas.drawText("CRANKR ⚡", logoRight, thumbTop + 54f, wmTextPaint)
            }

            saveBitmapToCache(context, bitmap, "crankr_lyric_${track.id}.png")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun generateLyricCard(
        context: Context,
        track: Track,
        coverBitmap: Bitmap?,
        lyricQuote: String,
        backgroundStyle: StoryBackgroundStyle = StoryBackgroundStyle.SOLID_BLACK,
        palette: ExtractedPalette? = null
    ): Uri? = generateLyricCard(
        context = context,
        track = track,
        coverBitmap = coverBitmap,
        lyrics = lyricQuote.lines().map { it.trim() }.filter { it.isNotBlank() },
        backgroundStyle = backgroundStyle,
        palette = palette
    )

    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float, targetW: Int, targetH: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }
        val rect = RectF(0f, 0f, targetW.toFloat(), targetH.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return output
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return try {
            val cacheFolder = File(context.cacheDir, "shared")
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs()
            }
            val file = File(cacheFolder, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareToInstagramStory(context: Context, imageUri: Uri, track: Track) {
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(imageUri, "image/png")
            setPackage("com.instagram.android")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra("content_url", "https://crankr.app/track/${track.id}")
        }

        try {
            val packageManager = context.packageManager
            // Berikan izin baca URI secara eksplisit ke Instagram
            context.grantUriPermission("com.instagram.android", imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (intent.resolveActivity(packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Instagram tidak terpasang, membuka menu bagikan...", Toast.LENGTH_SHORT).show()
                shareToGeneral(context, imageUri, track)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            shareToGeneral(context, imageUri, track)
        }
    }

    fun shareToGeneral(context: Context, imageUri: Uri, track: Track, customText: String? = null) {
        val text = customText ?: "Mendengarkan \"${track.title}\" oleh ${track.artist} di Crankr ⚡ Punk Rock Music"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_SUBJECT, "${track.title} - ${track.artist}")
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val chooser = Intent.createChooser(sendIntent, "Bagikan via Crankr")
        chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        context.startActivity(chooser)
    }
}
