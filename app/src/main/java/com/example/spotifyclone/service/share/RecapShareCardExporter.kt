package com.example.spotifyclone.service.share

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.spotifyclone.data.db.CrankrRecapData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Utilitas untuk mengonversi data Crankr Recap menjadi poster Bitmap 9:16 (1080x1920)
 * beresolusi tinggi dengan estetika modern industrial minimalist.
 * 
 * Bebas dari elemen AI slop (tanpa fake barcode, tanpa fake BPM 160, tanpa noise kotor).
 */
object RecapShareCardExporter {

    private const val POSTER_WIDTH = 1080
    private const val POSTER_HEIGHT = 1920

    suspend fun generateRecapStoryBitmap(context: Context, recap: CrankrRecapData): Bitmap = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(POSTER_WIDTH, POSTER_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Deep solid black background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#0D0D0D")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, POSTER_WIDTH.toFloat(), POSTER_HEIGHT.toFloat(), bgPaint)

        // 2. Modern Industrial Frame
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E50914") // Crimson Red
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        val innerBorderPaint = Paint().apply {
            color = Color.parseColor("#222222")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        canvas.drawRect(36f, 36f, POSTER_WIDTH - 36f, POSTER_HEIGHT - 36f, borderPaint)
        canvas.drawRect(48f, 48f, POSTER_WIDTH - 48f, POSTER_HEIGHT - 48f, innerBorderPaint)

        var currentY = 120f

        // 3. Header: Kategori & Judul Utama
        val tagPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.12f
        }
        canvas.drawText("// CRANKR RECAP //", 80f, currentY, tagPaint)

        currentY += 56f
        val mainTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 68f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.04f
        }
        canvas.drawText("NOISE POSTER", 80f, currentY, mainTitlePaint)

        currentY += 36f
        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#888888")
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
            letterSpacing = 0.05f
        }
        canvas.drawText("OFFICIAL ANNUAL LISTENING AUDIT", 80f, currentY, subTitlePaint)

        currentY += 36f
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#262626")
            strokeWidth = 2f
        }
        canvas.drawLine(80f, currentY, POSTER_WIDTH - 80f, currentY, dividerPaint)

        currentY += 50f

        // 4. Horizontal Album Covers Showcase (Left #02, Center #01, Right #03)
        val topTrack = recap.topTrack
        val topArtists = recap.topArtists
        val collageCenterY = currentY + 120f
        val collageCenterX = POSTER_WIDTH / 2f

        val cover1Bitmap = getCoverBitmap(context, topTrack?.albumArtUrl)
        val cover2Bitmap = if (topArtists.size > 1) getCoverBitmap(context, topArtists[1].albumArtUrl) else null
        val cover3Bitmap = if (topArtists.size > 2) getCoverBitmap(context, topArtists[2].albumArtUrl) else null

        // Album Kiri (#02)
        canvas.save()
        canvas.translate(collageCenterX - 280f, collageCenterY - 90f)
        drawFramedAlbum(canvas, cover2Bitmap, 180f, "#444444", "#181818", borderW = 2f)
        canvas.restore()

        // Album Kanan (#03)
        canvas.save()
        canvas.translate(collageCenterX + 100f, collageCenterY - 90f)
        drawFramedAlbum(canvas, cover3Bitmap, 180f, "#444444", "#181818", borderW = 2f)
        canvas.restore()

        // Album Tengah (#01 Utama)
        canvas.save()
        canvas.translate(collageCenterX - 110f, collageCenterY - 110f)
        drawFramedAlbum(canvas, cover1Bitmap, 220f, "#E50914", "#101010", borderW = 4f)
        canvas.restore()

        currentY += 270f

        // 5. Section 1: Total Listening Time
        val sectionLabelPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.08f
        }
        canvas.drawText("01. TOTAL WAKTU ROTASI", 80f, currentY, sectionLabelPaint)
        currentY += 60f

        val bigStatPaint = Paint().apply {
            color = Color.WHITE
            textSize = 76f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val totalTimeText = if (recap.totalHours > 0) {
            "${recap.totalHours} JAM ${recap.totalMinutes % 60}M"
        } else {
            "${recap.totalMinutes} MENIT"
        }
        canvas.drawText(totalTimeText, 80f, currentY, bigStatPaint)
        currentY += 40f

        val statSubTextPaint = Paint().apply {
            color = Color.parseColor("#A0A0A0")
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("${recap.uniqueArtistsCount} Band Independen Berhasil Dieksplorasi", 80f, currentY, statSubTextPaint)
        currentY += 40f

        canvas.drawLine(80f, currentY, POSTER_WIDTH - 80f, currentY, dividerPaint)
        currentY += 50f

        // 6. Section 2: Top Track
        canvas.drawText("02. TREK PALING RUSAK", 80f, currentY, sectionLabelPaint)
        currentY += 48f

        val trackTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val trackArtistPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val trackMetaPaint = Paint().apply {
            color = Color.parseColor("#888888")
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        val trackTitle = (topTrack?.trackTitle ?: "Belum ada trek tercatat").take(32)
        val trackArtist = (topTrack?.artistName ?: "Underground Band").take(28)
        val plays = "Diputar ${topTrack?.playCount ?: 1} kali dalam rotasi penuh"

        canvas.drawText(trackTitle, 80f, currentY, trackTitlePaint)
        currentY += 42f
        canvas.drawText(trackArtist, 80f, currentY, trackArtistPaint)
        currentY += 36f
        canvas.drawText(plays, 80f, currentY, trackMetaPaint)
        currentY += 45f

        canvas.drawLine(80f, currentY, POSTER_WIDTH - 80f, currentY, dividerPaint)
        currentY += 50f

        // 7. Section 3: Top 5 Artists
        canvas.drawText("03. PENGUASA HEADPHONE (TOP 5)", 80f, currentY, sectionLabelPaint)
        currentY += 46f

        val rankPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        val bandNamePaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val bandPlaysPaint = Paint().apply {
            color = Color.parseColor("#888888")
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val top5 = if (recap.topArtists.isNotEmpty()) recap.topArtists.take(5) else listOf()
        if (top5.isEmpty()) {
            canvas.drawText("01  CRANKR UNDERGROUND SCENE", 80f, currentY, bandNamePaint)
            currentY += 46f
        } else {
            top5.forEachIndexed { index, artistStat ->
                val numStr = "0${index + 1}"
                canvas.drawText(numStr, 80f, currentY, rankPaint)
                canvas.drawText(artistStat.artistName.take(28), 145f, currentY, bandNamePaint)
                canvas.drawText("${artistStat.playCount}x", POSTER_WIDTH - 80f, currentY, bandPlaysPaint)
                currentY += 46f
            }
        }

        currentY += 15f
        canvas.drawLine(80f, currentY, POSTER_WIDTH - 80f, currentY, dividerPaint)
        currentY += 45f

        // 8. Section 4: Persona Badge
        val personaBoxRect = RectF(80f, currentY, POSTER_WIDTH - 80f, currentY + 120f)
        val personaBoxBg = Paint().apply {
            color = Color.parseColor("#181818")
            style = Paint.Style.FILL
        }
        val personaBoxBorder = Paint().apply {
            color = Color.parseColor("#2A2A2A")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(personaBoxRect, 4f, 4f, personaBoxBg)
        canvas.drawRoundRect(personaBoxRect, 4f, 4f, personaBoxBorder)

        val personaBadgePaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val personaQuotePaint = Paint().apply {
            color = Color.parseColor("#999999")
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("KLASIFIKASI: ${recap.personaBadge}", 110f, currentY + 48f, personaBadgePaint)
        canvas.drawText("\"${recap.personaQuote}\"", 110f, currentY + 92f, personaQuotePaint)

        // 9. Footer
        val footerY = POSTER_HEIGHT - 90f
        val footerPaint = Paint().apply {
            color = Color.parseColor("#666666")
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.08f
        }
        canvas.drawText("CRANKR AUDIO // INDEPENDENT SCENE STREAMING • CRANKR.APP", 80f, footerY, footerPaint)

        bitmap
    }

    private fun drawFramedAlbum(canvas: Canvas, bitmap: Bitmap?, size: Float, borderColor: String, bgColor: String, borderW: Float = 2f) {
        val rect = RectF(0f, 0f, size, size)
        val fillPaint = Paint().apply {
            color = Color.parseColor(bgColor)
            style = Paint.Style.FILL
        }
        canvas.drawRect(rect, fillPaint)

        if (bitmap != null) {
            val scaled = Bitmap.createScaledBitmap(bitmap, size.toInt(), size.toInt(), true)
            canvas.drawBitmap(scaled, 0f, 0f, null)
        } else {
            val txtPaint = Paint().apply {
                color = Color.parseColor("#444444")
                textSize = size * 0.16f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("CRANKR", rect.centerX(), rect.centerY() + 8f, txtPaint)
        }

        val strokePaint = Paint().apply {
            color = Color.parseColor(borderColor)
            style = Paint.Style.STROKE
            strokeWidth = borderW
            isAntiAlias = true
        }
        canvas.drawRect(rect, strokePaint)
    }

    private suspend fun getCoverBitmap(context: Context, coverUrl: String?): Bitmap? = withContext(Dispatchers.IO) {
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

    fun saveRecapToCache(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val cacheFolder = File(context.cacheDir, "shared")
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs()
            }
            val file = File(cacheFolder, "crankr_recap_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    fun shareToInstagramStory(context: Context, imageUri: Uri) {
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(imageUri, "image/png")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra("top_background_color", "#0D0D0D")
            putExtra("bottom_background_color", "#0D0D0D")
        }

        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                shareGeneral(context, imageUri)
            }
        } catch (_: Exception) {
            shareGeneral(context, imageUri)
        }
    }

    fun shareToGeneral(context: Context, imageUri: Uri) {
        shareGeneral(context, imageUri)
    }

    fun shareGeneral(context: Context, imageUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, "Crankr Recap — Laporan pemutaran audio independen saya di Crankr.")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val chooser = Intent.createChooser(shareIntent, "Bagikan Crankr Recap").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    }
}
