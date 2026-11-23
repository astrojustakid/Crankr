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
 * Utilitas untuk mengonversi data Crankr Recap / Noise Tape menjadi
 * poster Bitmap 9:16 (1080x1920) beresolusi tinggi bertema zine punk rock monokrom brutalist,
 * lengkap dengan tekstur fotokopi kotor, kolase artwork miring, dan efek cetak misregistrasi.
 */
object RecapShareCardExporter {

    private const val POSTER_WIDTH = 1080
    private const val POSTER_HEIGHT = 1920

    suspend fun generateRecapStoryBitmap(context: Context, recap: CrankrRecapData): Bitmap = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(POSTER_WIDTH, POSTER_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Deep photocopy carbon black background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#0A0A0A")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, POSTER_WIDTH.toFloat(), POSTER_HEIGHT.toFloat(), bgPaint)

        // 2. Analog Halftone Pattern Overlay
        val halftonePaint = Paint().apply {
            color = Color.WHITE
            alpha = 12 // 5% subtle dot grid
            isAntiAlias = true
        }
        val step = 20f
        var hx = 0f
        while (hx < POSTER_WIDTH) {
            var hy = 0f
            while (hy < POSTER_HEIGHT) {
                canvas.drawCircle(hx, hy, 1.3f, halftonePaint)
                hy += step
            }
            hx += step
        }

        // 3. Xerox drum scanlines & analog noise grit
        val drumLinePaint = Paint().apply {
            color = Color.parseColor("#1C1C1C")
            strokeWidth = 1.5f
        }
        var scanY = 30f
        while (scanY < POSTER_HEIGHT) {
            canvas.drawLine(0f, scanY, POSTER_WIDTH.toFloat(), scanY, drumLinePaint)
            scanY += 60f
        }

        val noisePaint = Paint().apply {
            color = Color.parseColor("#2E2E2E")
            strokeWidth = 2f
        }
        for (i in 0..200) {
            val rx = (Math.random() * POSTER_WIDTH).toFloat()
            val ry = (Math.random() * POSTER_HEIGHT).toFloat()
            val len = (Math.random() * 16 + 4).toFloat()
            canvas.drawLine(rx, ry, rx + len, ry, noisePaint)
        }

        // 4. Industrial Brutalist Outer & Inner Frame
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E50914") // Crimson Red
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        val innerBorderPaint = Paint().apply {
            color = Color.parseColor("#333333")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(36f, 36f, POSTER_WIDTH - 36f, POSTER_HEIGHT - 36f, borderPaint)
        canvas.drawRect(48f, 48f, POSTER_WIDTH - 48f, POSTER_HEIGHT - 48f, innerBorderPaint)

        // Corner rivets
        val rivetPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(54f, 54f, 6f, rivetPaint)
        canvas.drawCircle(POSTER_WIDTH - 54f, 54f, 6f, rivetPaint)
        canvas.drawCircle(54f, POSTER_HEIGHT - 54f, 6f, rivetPaint)
        canvas.drawCircle(POSTER_WIDTH - 54f, POSTER_HEIGHT - 54f, 6f, rivetPaint)

        var currentY = 110f

        // 5. HEADER: ZINE TAPE & MISREGISTRATION HEADLINE
        // Masking tape strip behind top tag
        val tapePaint = Paint().apply {
            color = Color.parseColor("#E50914")
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.rotate(-1.5f, 80f, currentY)
        canvas.drawRect(70f, currentY - 26f, 480f, currentY + 16f, tapePaint)
        val headerTagPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.15f
        }
        canvas.drawText("NOISE TAPE AUDIT 2026", 85f, currentY, headerTagPaint)
        canvas.restore()

        currentY += 65f

        // Misregistration / Print Misalignment Ghosting on main title
        val mainTitleGhostPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            textSize = 66f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.04f
        }
        val mainTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 66f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.04f
        }
        canvas.drawText("CRANKR RECAP", 84f, currentY + 4f, mainTitleGhostPaint)
        canvas.drawText("CRANKR RECAP", 80f, currentY, mainTitlePaint)

        currentY += 40f
        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#9E9E9E")
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("LAPORAN DISTORSI PERSONAL", 80f, currentY, subTitlePaint)

        currentY += 45f
        val redDividerPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            strokeWidth = 3f
        }
        canvas.drawLine(80f, currentY, POSTER_WIDTH - 80f, currentY, redDividerPaint)

        currentY += 40f

        // 6. TILTED ALBUM ART COLLAGE STACK (Top 1, Top 2, Top 3)
        val topTrack = recap.topTrack
        val topArtists = recap.topArtists
        val collageCenterY = currentY + 140f
        val collageCenterX = POSTER_WIDTH / 2f

        // Fetch Cover Bitmaps
        val cover1Bitmap = getCoverBitmap(context, topTrack?.albumArtUrl)
        val cover2Bitmap = if (topArtists.size > 1) getCoverBitmap(context, topArtists[1].albumArtUrl) else null
        val cover3Bitmap = if (topArtists.size > 2) getCoverBitmap(context, topArtists[2].albumArtUrl) else null

        // Stack #2 (Left, rotated -8 deg)
        canvas.save()
        canvas.translate(collageCenterX - 180f, collageCenterY - 20f)
        canvas.rotate(-8f)
        drawFramedAlbum(canvas, cover2Bitmap, 180f, "#888888", "#1A1A1A")
        canvas.restore()

        // Stack #3 (Right, rotated 9 deg)
        canvas.save()
        canvas.translate(collageCenterX + 60f, collageCenterY - 20f)
        canvas.rotate(9f)
        drawFramedAlbum(canvas, cover3Bitmap, 180f, "#888888", "#1A1A1A")
        canvas.restore()

        // Stack #1 (Center Front, rotated -2 deg, elevated with red glow)
        canvas.save()
        canvas.translate(collageCenterX - 120f, collageCenterY - 60f)
        canvas.rotate(-2.5f)
        // Red Ambient Glow behind #1
        val glowPaint = Paint().apply {
            color = Color.parseColor("#33E50914")
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRect(-15f, -15f, 255f, 255f, glowPaint)
        drawFramedAlbum(canvas, cover1Bitmap, 240f, "#FFFFFF", "#000000", borderW = 5f)

        // Masking tape on #1 corner
        val tapeRotPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(20f, 195f, 220f, 225f, tapeRotPaint)
        val tapeTextPaint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("HEAVY ROTATION #1", 30f, 217f, tapeTextPaint)
        canvas.restore()

        currentY += 280f

        // 7. SECTION 1: AUDIO WAVEFORM & TOTAL AUDIT WAKTU
        val sectionLabelPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("01. TOTAL DISTORSI TEREKAM", 80f, currentY, sectionLabelPaint)
        currentY += 35f

        // Draw rough audio waveform bars behind big numbers
        val waveformPaint = Paint().apply {
            color = Color.parseColor("#33E50914")
            style = Paint.Style.FILL
        }
        val seedWave = floatArrayOf(25f, 55f, 85f, 40f, 70f, 95f, 50f, 80f, 35f, 65f, 90f, 45f, 75f, 95f, 55f, 30f, 60f, 85f, 40f)
        var waveX = 80f
        for (h in seedWave) {
            canvas.drawRect(waveX, currentY + (100f - h), waveX + 32f, currentY + 100f, waveformPaint)
            waveX += 48f
        }

        val bigStatGhostPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            textSize = 72f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val bigStatPaint = Paint().apply {
            color = Color.WHITE
            textSize = 72f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val totalTimeText = if (recap.totalHours > 0) {
            "${recap.totalHours} JAM ${recap.totalMinutes % 60}M"
        } else {
            "${recap.totalMinutes} MENIT"
        }
        canvas.drawText(totalTimeText, 84f, currentY + 74f, bigStatGhostPaint)
        canvas.drawText(totalTimeText, 80f, currentY + 70f, bigStatPaint)
        currentY += 105f

        val bandExploredPaint = Paint().apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("★ ${recap.uniqueArtistsCount} BAND INDEPENDEN BERHASIL DIEKSPLORASI", 80f, currentY, bandExploredPaint)
        currentY += 32f

        val tempoPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("RATA-RATA TEMPO: ~160 BPM", 80f, currentY, tempoPaint)
        currentY += 35f

        val greyLinePaint = Paint().apply {
            color = Color.parseColor("#262626")
            strokeWidth = 2f
        }
        canvas.drawLine(80f, currentY, POSTER_WIDTH - 80f, currentY, greyLinePaint)
        currentY += 40f

        // 8. SECTION 2: TREK PALING RUSAK (#1 TRACK)
        canvas.drawText("02. TREK PALING RUSAK (MOST REPEATED)", 80f, currentY, sectionLabelPaint)
        currentY += 40f

        val trackTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val trackArtistPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val playBadgePaint = Paint().apply {
            color = Color.parseColor("#B3B3B3")
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        val trackTitle = (topTrack?.trackTitle ?: "Belum ada trek tercatat").take(34)
        val trackArtist = (topTrack?.artistName ?: "Underground Band").take(30)
        val avgSec = if ((topTrack?.playCount ?: 0) > 0) {
            (((topTrack?.totalDurationMs ?: 0L) / topTrack!!.playCount) / 1000).toInt().coerceIn(60, 600)
        } else {
            195
        }
        val trackDurationInfo = "DURASI: %d:%02d".format(avgSec / 60, avgSec % 60)
        val plays = "DIPUTAR ${topTrack?.playCount ?: 1}x DALAM ROTASI PENUH"

        canvas.drawText(trackTitle, 80f, currentY, trackTitlePaint)
        currentY += 38f
        canvas.drawText(trackArtist, 80f, currentY, trackArtistPaint)
        currentY += 32f
        canvas.drawText(trackDurationInfo, 80f, currentY, playBadgePaint)
        currentY += 32f
        canvas.drawText(plays, 80f, currentY, playBadgePaint)
        currentY += 40f

        canvas.drawLine(80f, currentY, POSTER_WIDTH - 80f, currentY, greyLinePaint)
        currentY += 40f

        // 9. SECTION 3: TOP 5 BAND PENGUASA HEADPHONE
        canvas.drawText("03. TOP BAND PENGUASA HEADPHONE", 80f, currentY, sectionLabelPaint)
        currentY += 40f

        val rankNumberPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            textSize = 30f
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
            color = Color.parseColor("#808080")
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val top5 = if (recap.topArtists.isNotEmpty()) recap.topArtists.take(5) else listOf()
        if (top5.isEmpty()) {
            canvas.drawText("01  CRANKR UNDERGROUND SCENE", 80f, currentY, bandNamePaint)
            currentY += 45f
        } else {
            top5.forEachIndexed { index, artistStat ->
                val numStr = "0${index + 1}"
                canvas.drawText(numStr, 80f, currentY, rankNumberPaint)
                canvas.drawText(artistStat.artistName.take(28), 145f, currentY, bandNamePaint)
                canvas.drawText("${artistStat.playCount}x", POSTER_WIDTH - 80f, currentY, bandPlaysPaint)
                currentY += 44f
            }
        }

        currentY += 10f
        canvas.drawLine(80f, currentY, POSTER_WIDTH - 80f, currentY, greyLinePaint)
        currentY += 40f

        // 10. SECTION 4: CONCERT TICKET STAMP FOR PERSONA
        val ticketRect = RectF(80f, currentY, POSTER_WIDTH - 80f, currentY + 140f)
        val ticketBgPaint = Paint().apply {
            color = Color.parseColor("#141414")
            style = Paint.Style.FILL
        }
        val ticketBorderPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
        }
        canvas.drawRoundRect(ticketRect, 4f, 4f, ticketBgPaint)
        canvas.drawRoundRect(ticketRect, 4f, 4f, ticketBorderPaint)

        // Ticket circular cutout notches on left & right
        val notchPaint = Paint().apply {
            color = Color.parseColor("#0A0A0A")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(80f, ticketRect.centerY(), 14f, notchPaint)
        canvas.drawCircle(POSTER_WIDTH - 80f, ticketRect.centerY(), 14f, notchPaint)

        val personaBadgePaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val personaQuotePaint = Paint().apply {
            color = Color.parseColor("#B3B3B3")
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("KLASIFIKASI: ${recap.personaBadge}", 115f, currentY + 55f, personaBadgePaint)
        canvas.drawText("\"${recap.personaQuote}\"", 115f, currentY + 105f, personaQuotePaint)

        // 11. FOOTER: BARCODE & ZIGZAG LIGHTNING GRAPHICS
        val footerY = POSTER_HEIGHT - 120f
        val barcodePaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 3f
        }
        val barcodeWidths = floatArrayOf(3f, 6f, 2f, 8f, 2f, 4f, 7f, 2f, 5f, 2f, 6f, 3f, 8f, 2f, 4f, 7f, 2f)
        var bx = 80f
        for (w in barcodeWidths) {
            barcodePaint.strokeWidth = w
            canvas.drawLine(bx, footerY, bx, footerY + 45f, barcodePaint)
            bx += w + 6f
        }

        val footerTextPaint = Paint().apply {
            color = Color.parseColor("#777777")
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        val editionPaint = Paint().apply {
            color = Color.parseColor("#E50914")
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("⚡ ⚡ ⚡ CRANKR NOISE TAPE 2026", 320f, footerY + 16f, footerTextPaint)
        canvas.drawText("EDISI: #CKR-2026-NZT BATCH 01", 320f, footerY + 38f, editionPaint)
        canvas.drawText("VERIFIED BY UNDERGROUND SCENE • CRANKR.APP", 320f, footerY + 58f, footerTextPaint)

        bitmap
    }

    private fun drawFramedAlbum(canvas: Canvas, bitmap: Bitmap?, size: Float, borderColor: String, bgColor: String, borderW: Float = 3f) {
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
                color = Color.parseColor("#888888")
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
            val file = File(cacheFolder, "crankr_noise_tape_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, "com.example.spotifyclone.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareToInstagramStory(context: Context, imageUri: Uri) {
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(imageUri, "image/png")
            setPackage("com.instagram.android")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra("content_url", "https://crankr.app/recap")
        }

        try {
            context.grantUriPermission("com.instagram.android", imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Instagram tidak terpasang, membuka menu bagikan...", Toast.LENGTH_SHORT).show()
                shareToGeneral(context, imageUri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            shareToGeneral(context, imageUri)
        }
    }

    fun shareToGeneral(context: Context, imageUri: Uri, customText: String? = null) {
        val text = customText ?: "Catatan distorsi personal Crankr Recap gw! ⚡💀 Musik keras tanpa kompromi. #CrankrRecap #NoiseTape #UndergroundPunk"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_SUBJECT, "Crankr Noise Tape Recap")
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val chooser = Intent.createChooser(sendIntent, "Bagikan Crankr Recap Poster")
        chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        context.startActivity(chooser)
    }
}
