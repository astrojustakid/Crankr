package com.example.spotifyclone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.spotifyclone.data.db.CrankrRecapData
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.ui.components.springPressable
import com.example.spotifyclone.ui.viewmodel.RecapViewModel

/**
 * Shape tiket konser berlubang (notched edges) di sisi kiri dan kanan.
 */
class ConcertTicketShape(
    private val cornerRadius: Dp = 4.dp,
    private val notchRadius: Dp = 10.dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val nr = with(density) { notchRadius.toPx() }
        val w = size.width
        val h = size.height
        val cy = h / 2f

        val path = Path().apply {
            reset()
            moveTo(r, 0f)
            lineTo(w - r, 0f)
            quadraticTo(w, 0f, w, r)
            lineTo(w, cy - nr)
            arcTo(
                rect = Rect(w - nr, cy - nr, w + nr, cy + nr),
                startAngleDegrees = 270f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(w, h - r)
            quadraticTo(w, h, w - r, h)
            lineTo(r, h)
            quadraticTo(0f, h, 0f, h - r)
            lineTo(0f, cy + nr)
            arcTo(
                rect = Rect(-nr, cy - nr, nr, cy + nr),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(0f, r)
            quadraticTo(0f, 0f, r, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Efek bintik noise fotokopi kotor (photocopy grit & halftone pattern).
 */
fun Modifier.photocopyGrit(): Modifier = this.drawBehind {
    val step = 14f
    val dotRadius = 1.1f
    val dotColor = Color.White.copy(alpha = 0.045f)
    var x = 0f
    while (x < size.width) {
        var y = 0f
        while (y < size.height) {
            drawCircle(dotColor, radius = dotRadius, center = Offset(x, y))
            y += step
        }
        x += step
    }

    // Faint horizontal xerox drum scanlines
    val linePaint = Color(0xFF1F1F1F).copy(alpha = 0.35f)
    var ly = 20f
    while (ly < size.height) {
        drawLine(
            color = linePaint,
            start = Offset(0f, ly),
            end = Offset(size.width, ly),
            strokeWidth = 1f
        )
        ly += 48f
    }
}

/**
 * Pita solasi / masking tape kertas zine miring buatan tangan.
 */
@Composable
fun ZineTapeLabel(
    text: String,
    modifier: Modifier = Modifier,
    rotation: Float = -2f,
    bgColor: Color = CrimsonRed,
    textColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .rotate(rotation)
            .background(bgColor, RoundedCornerShape(1.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Stempel tinta brutalist miring (distressed ink stamp).
 */
@Composable
fun DistressedStampBadge(
    text: String,
    modifier: Modifier = Modifier,
    rotation: Float = 3f,
    color: Color = CrimsonRed
) {
    Box(
        modifier = modifier
            .rotate(rotation)
            .border(2.dp, color, RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(2.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = color,
            letterSpacing = 0.8.sp,
            softWrap = true
        )
    }
}

/**
 * Gelombang suara audio merah kasar di belakang angka audit waktu.
 */
@Composable
fun AudioWaveformGraphic(
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    color: Color = CrimsonRed.copy(alpha = 0.35f)
) {
    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val barWidth = (totalWidth / barCount) * 0.65f
        val gap = (totalWidth / barCount) * 0.35f
        val seedHeights = floatArrayOf(
            0.35f, 0.65f, 0.95f, 0.45f, 0.8f, 0.3f, 0.75f, 1.0f,
            0.55f, 0.85f, 0.4f, 0.9f, 0.6f, 0.75f, 0.95f, 0.5f,
            0.7f, 1.0f, 0.4f, 0.85f, 0.6f, 0.35f, 0.75f, 0.9f
        )
        for (i in 0 until barCount) {
            val hRatio = seedHeights[i % seedHeights.size]
            val h = size.height * hRatio
            val left = i * (barWidth + gap)
            val top = (size.height - h) / 2f
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, h)
            )
        }
    }
}

@Composable
fun CrankrRecapScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecapViewModel = viewModel()
) {
    val context = LocalContext.current
    val recapData by viewModel.recapData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentIndex by viewModel.currentCardIndex.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    BackHandler {
        if (currentIndex > 0) {
            viewModel.prevCard()
        } else {
            onClose()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)) // Deep photocopy carbon black
            .photocopyGrit()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CrimsonRed, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Memindai Data Musik...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                }
            }
        } else {
            val recap = recapData ?: CrankrRecapData(
                totalMinutes = 0,
                totalHours = 0,
                uniqueArtistsCount = 0,
                topTrack = null,
                topArtists = emptyList(),
                topTracks = emptyList(),
                personaTitle = "GARAGE ROOKIE",
                personaBadge = "ROOKIE",
                personaQuote = "Baru mulai menyetel distorsi amplifier."
            )

            Column(modifier = Modifier.fillMaxSize()) {
                // Top Story Progress Indicators (4 segmented tape bars)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (i in 0..3) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    when {
                                        i < currentIndex -> CrimsonRed
                                        i == currentIndex -> Color.White
                                        else -> Color(0xFF222222)
                                    }
                                )
                                .clickable { viewModel.setCardIndex(i) }
                        )
                    }
                }

                // Top Bar with Zine title and Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(CrimsonRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CRANKR RECAP",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            letterSpacing = 1.2.sp
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFF1A1A1A), CircleShape)
                            .border(1.dp, Color(0xFF333333), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Main Interactive Card Content Area (Card 0 to 3)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    AnimatedContent(
                        targetState = currentIndex,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> -width } + fadeOut())
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> width } + fadeOut())
                            }
                        },
                        label = "RecapCardTransition"
                    ) { index ->
                        when (index) {
                            0 -> TotalTimeCard(recap = recap)
                            1 -> TopTrackCard(recap = recap)
                            2 -> TopArtistsCard(recap = recap)
                            3 -> SummaryPosterCard(
                                recap = recap,
                                isExporting = isExporting,
                                onShareInstagram = { viewModel.shareToInstagramStory(context) },
                                onShareGeneral = { viewModel.shareGeneral(context) }
                            )
                        }
                    }
                }

                // Bottom Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentIndex > 0) {
                        TextButton(
                            onClick = { viewModel.prevCard() },
                            colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SEBELUMNYA",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(80.dp))
                    }

                    Text(
                        text = "${currentIndex + 1} / 4",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = CrimsonRed,
                        fontWeight = FontWeight.Bold
                    )

                    if (currentIndex < 3) {
                        Button(
                            onClick = { viewModel.nextCard() },
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                        ) {
                            Text(
                                text = "LANJUT",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(80.dp))
                    }
                }
            }
        }
    }
}

/**
 * Slide 1 (Audit Waktu):
 * Menampilkan tipografi brutalist stencil besar dengan waveform bar kasar merah di latar belakang,
 * metrik tempo ~160 BPM // POGO COMPATIBLE, serta jarak vertikal 12.dp yang rapi dan konsisten.
 */
@Composable
private fun TotalTimeCard(recap: CrankrRecapData) {
    Card(
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        modifier = Modifier
            .fillMaxSize()
            .border(2.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
            .photocopyGrit()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZineTapeLabel(
                        text = "TAPE 01: AUDIT WAKTU",
                        rotation = -2f,
                        bgColor = CrimsonRed,
                        textColor = Color.White
                    )
                    Text(
                        text = "DECIBEL METER",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF888888),
                        fontWeight = FontWeight.Bold,
                        softWrap = true
                    )
                }

                // Batas aman sebelum judul utama
                Spacer(modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))

                Text(
                    text = "SEBERAPA BISING TAHUN INI?",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    lineHeight = 36.sp,
                    letterSpacing = 0.5.sp,
                    softWrap = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Total rekaman frekuensi keras yang lu putar tanpa kompromi.",
                    fontFamily = CrankrBodyFont,
                    fontSize = 13.sp,
                    color = Color(0xFFAAAAAA),
                    softWrap = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)
            }

            // Big Stencil Numbers with Audio Waveform Bars behind
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background Rough Red Audio Waveform Graphic
                AudioWaveformGraphic(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .padding(horizontal = 8.dp),
                    barCount = 22,
                    color = CrimsonRed.copy(alpha = 0.32f)
                )

                // Brutalist Stencil Main Numbers
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val mainNumberText = if (recap.totalHours > 0) "${recap.totalHours} JAM" else "${recap.totalMinutes} MENIT"

                    // Print Misregistration Ghosting Effect
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = mainNumberText,
                            fontFamily = CrankrDisplayFont,
                            fontSize = 62.sp,
                            fontWeight = FontWeight.Black,
                            color = CrimsonRed.copy(alpha = 0.5f),
                            softWrap = true,
                            modifier = Modifier
                                .offset(x = 2.dp, y = 2.dp)
                                .padding(horizontal = 16.dp)
                        )
                        Text(
                            text = mainNumberText,
                            fontFamily = CrankrDisplayFont,
                            fontSize = 62.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            softWrap = true,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (recap.totalHours > 0) {
                        ZineTapeLabel(
                            text = "+ ${recap.totalMinutes % 60} MENIT DISTORSI PENUH",
                            rotation = 1.5f,
                            bgColor = Color.White,
                            textColor = Color.Black
                        )
                    } else {
                        ZineTapeLabel(
                            text = "PUTARAN DI GARASI BAWAH TANAH",
                            rotation = -1.5f,
                            bgColor = CrimsonRed,
                            textColor = Color.White
                        )
                    }

                    // Metrik Tempo Skena
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "RATA-RATA TEMPO: ~160 BPM",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed,
                        letterSpacing = 0.5.sp,
                        softWrap = true,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Bottom Section: Scene Exploration & Ticket Stamp
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Cutout Band Count Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF191919), RoundedCornerShape(2.dp))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(CrimsonRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${recap.uniqueArtistsCount} BAND INDEPENDEN BERHASIL DIEKSPLORASI",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Persona Ticket Stub
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ConcertTicketShape(cornerRadius = 2.dp, notchRadius = 8.dp))
                        .background(Color(0xFF141414))
                        .border(1.5.dp, CrimsonRed, ConcertTicketShape(cornerRadius = 2.dp, notchRadius = 8.dp))
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "KLASIFIKASI PUNK RESMI:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF888888),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "OFFICIAL SCENE TICKET",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = CrimsonRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = recap.personaBadge,
                            fontFamily = CrankrDisplayFont,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "\"${recap.personaQuote}\"",
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Slide 2 (Trek Paling Rusak):
 * Susunan vertikal murni (Column) dari atas ke bawah:
 * - Header dengan pita teks /// TAPE 02: ROTASI MAKSIMAL /// dan judul TREK PALING RUSAK dengan margin jelas.
 * - Wadah Cover persegi mandiri berukuran tetap (220.dp x 220.dp) dengan ambient glow dan border merah tipis, tanpa stiker mengambang di atas gambar.
 * - Metadata Lagu (title besar tebal di bawah cover, artist berwarna CrimsonRed, info durasi resmi).
 * - Badge rotasi DIPUTAR X KALI DALAM ROTASI PENUH di bagian bawah dengan pembatas Spacer(16.dp).
 */
@Composable
private fun TopTrackCard(recap: CrankrRecapData) {
    val topTrack = recap.topTrack

    Card(
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        modifier = Modifier
            .fillMaxSize()
            .border(2.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
            .photocopyGrit()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Pita teks penanda kategori dan judul TREK PALING RUSAK dengan margin bawah yang jelas
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZineTapeLabel(
                        text = "TAPE 02: ROTASI MAKSIMAL",
                        rotation = 0f,
                        bgColor = CrimsonRed,
                        textColor = Color.White
                    )
                    Text(
                        text = "SIDE A TRACK 01",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF888888),
                        fontWeight = FontWeight.Bold,
                        softWrap = true
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "TREK PALING RUSAK",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    lineHeight = 32.sp,
                    softWrap = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lagu yang lu putar terus tanpa ampun sampai headphone lu jebol:",
                    fontFamily = CrankrBodyFont,
                    fontSize = 12.sp,
                    color = Color(0xFFAAAAAA),
                    softWrap = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)
            }

            // Konten Tengah (Cover + Metadata) dengan weight(1f) dan susunan vertikal teratur
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Wadah Cover: Tempatkan cover album dalam container persegi mandiri berukuran tetap (220.dp x 220.dp)
                // Tambahkan border merah tipis atau bayangan ambient di belakang wadah, tanpa stiker apa pun di atas gambar.
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(CrimsonRed.copy(alpha = 0.35f), Color.Transparent),
                                radius = 240f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .border(1.5.dp, CrimsonRed, RoundedCornerShape(2.dp))
                            .background(Color(0xFF141414), RoundedCornerShape(2.dp))
                            .clip(RoundedCornerShape(2.dp))
                    ) {
                        if (!topTrack?.albumArtUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = topTrack.albumArtUrl,
                                contentDescription = topTrack.trackTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "NOISE\nTAPE",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF666666),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metadata Lagu: Letakkan nama lagu (title) dengan font besar tebal tepat di bawah cover,
                // diikuti nama musisi/band (artist) berwarna merah Crankr.
                Text(
                    text = topTrack?.trackTitle ?: "Belum ada trek tercatat",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = topTrack?.artistName ?: "Underground Artist",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Info durasi & rilisan
                val avgSec = if ((topTrack?.playCount ?: 0) > 0) {
                    (((topTrack?.totalDurationMs ?: 0L) / topTrack!!.playCount) / 1000).toInt().coerceIn(60, 600)
                } else {
                    195
                }
                val min = avgSec / 60
                val sec = avgSec % 60
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DURASI: %d:%02d".format(min, sec),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center
                )
            }

            // Badge Rotasi: Tempatkan kotak badge DIPUTAR X KALI DALAM ROTASI PENUH di posisi paling bawah wadah kartu
            // dengan jarak pemisah (Spacer(16.dp)), bukan mengambang bebas.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CrimsonRed, RoundedCornerShape(2.dp))
                        .background(CrimsonRed.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(CrimsonRed, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DIPUTAR ${topTrack?.playCount ?: 1} KALI DALAM ROTASI PENUH",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.8.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Slide 3 (Penguasa Headphone):
 * Memisahkan teks deskripsi dengan stempel VERIFIED BY SCENE agar tidak bertabrakan,
 * serta memberi jarak aman antara teks terverifikasi kiri dan label CRANKR.ZINE kanan.
 */
@Composable
private fun TopArtistsCard(recap: CrankrRecapData) {
    Card(
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        modifier = Modifier
            .fillMaxSize()
            .border(2.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
            .photocopyGrit()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZineTapeLabel(
                        text = "TAPE 03: HEADPHONE LORDS",
                        rotation = -1.5f,
                        bgColor = CrimsonRed,
                        textColor = Color.White
                    )
                    Text(
                        text = "LINEUP RESMI",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF888888),
                        fontWeight = FontWeight.Bold,
                        softWrap = true
                    )
                }

                Spacer(modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))

                Text(
                    text = "PENGUASA HEADPHONE",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    lineHeight = 36.sp,
                    softWrap = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "5 Band yang paling sering menguasai gendang telinga lu:",
                    fontFamily = CrankrBodyFont,
                    fontSize = 13.sp,
                    color = Color(0xFFAAAAAA),
                    softWrap = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)
            }

            // Brutalist Artist List with Mini Polaroid Collages
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val artists = recap.topArtists.take(5)
                if (artists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada riwayat band.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    artists.forEachIndexed { index, artistStat ->
                        val isFirst = index == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isFirst) Color(0xFF1E1717) else Color(0xFF141414), RoundedCornerShape(2.dp))
                                .border(
                                    width = if (isFirst) 2.dp else 1.dp,
                                    color = if (isFirst) CrimsonRed else Color(0xFF262626),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Kolom 1 (Kiri): Nomor urut "01" dan foto profil artis
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "0${index + 1}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = if (isFirst) 20.sp else 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isFirst) CrimsonRed else Color.White
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                // Spotify Photo Mini Polaroid Frame
                                Box(
                                    modifier = Modifier
                                        .size(if (isFirst) 42.dp else 36.dp)
                                        .rotate(if (isFirst) -2.5f else 1.5f)
                                        .border(1.5.dp, Color.White, RoundedCornerShape(1.dp))
                                        .background(Color.Black)
                                ) {
                                    if (artistStat.albumArtUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = artistStat.albumArtUrl,
                                            contentDescription = artistStat.artistName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "BAND",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            // Kolom 2 (Tengah): Gunakan Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) untuk menampung nama artis dan jumlah putaran. Pasang maxLines = 1 dengan textOverflow = TextOverflow.Ellipsis agar teks tidak pernah memanjang ke kanan.
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = artistStat.artistName,
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = if (isFirst) 17.sp else 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = true
                                )
                                Text(
                                    text = "${artistStat.playCount}x putaran di rotasi",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (isFirst) CrimsonRed else Color(0xFF888888),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = true
                                )
                            }

                            // Kolom 3 (Kanan): Tempatkan komponen stempel ★ VERIFIED BY SCENE secara mandiri tanpa absoluteOffset atau penumpukan layer, sehingga stempel memiliki ruangnya sendiri di sisi paling kanan.
                            if (isFirst) {
                                DistressedStampBadge(
                                    text = "VERIFIED BY SCENE",
                                    rotation = 3f,
                                    color = CrimsonRed
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Zine Verification & Label
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "TERVERIFIKASI BERDASARKAN TOTAL PEMUTARAN AUDIO",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color(0xFF888888),
                    softWrap = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "CRANKR.ZINE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonRed,
                    softWrap = true
                )
            }
        }
    }
}

/**
 * Slide 4 (Poster Ringkasan Resmi):
 * Layout poster gigs bergaris kisi (grid/segmented layout) yang rapi dan terisolasi:
 * - Baris Atas: Header judul recap tahunan berdampingan dengan badge status pengguna dalam satu Row fleksibel.
 * - Baris Tengah (Showcase Visual): 3 cover album teratas secara horizontal berjajar (bukan saling tumpuk)
 *   dengan ukuran proporsional (album utama di tengah sedikit lebih besar, dua album pendamping di kiri dan kanan
 *   dengan sudut miring rapi dan padding pembatas yang tegas).
 * - Baris Bawah (Blok Data): Tabel data sederhana bergaris batas brutalist untuk memuat TOTAL DISTORSI,
 *   BAND DIEKSPLORASI, dan TREK PALING RUSAK dengan pembatas vertikal & horizontal tegas.
 * - Area Tombol: Posisi terkunci di bagian bawah dengan padding aman dari tepi layar perangkat.
 * - Penyesuaian Ruang Fleksibel: weight(1f) dan layout terukur agar otomatis pas di semua dimensi layar.
 */
@Composable
private fun SummaryPosterCard(
    recap: CrankrRecapData,
    isExporting: Boolean,
    onShareInstagram: () -> Unit,
    onShareGeneral: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        modifier = Modifier
            .fillMaxSize()
            .border(2.dp, CrimsonRed, RoundedCornerShape(2.dp))
            .photocopyGrit()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Baris Atas: Header judul recap tahunan berdampingan dengan badge status pengguna dalam satu Row fleksibel
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        ZineTapeLabel(
                            text = "TAPE 04: NOISE POSTER",
                            rotation = 0f,
                            bgColor = CrimsonRed,
                            textColor = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "CRANKR RECAP 2026",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = TextWhite,
                            letterSpacing = 0.5.sp,
                            softWrap = true
                        )
                        Text(
                            text = "POSTER RINGKASAN RESMI",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = CrimsonRed,
                            fontWeight = FontWeight.Bold,
                            softWrap = true
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    DistressedStampBadge(
                        text = recap.personaBadge,
                        rotation = 0f,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)
            }

            // Konten Tengah (Showcase Visual + Blok Data Grid) dengan weight(1f) dan penyusunan teratur
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Baris Tengah (Showcase Visual): 3 cover album teratas secara horizontal berjajar (bukan saling tumpuk)
                // ukuran proporsional (album utama di tengah sedikit lebih besar, dua album pendamping di kiri dan kanan dengan sudut miring rapi dan padding pembatas yang tegas)
                val topTrack = recap.topTrack
                val topArtists = recap.topArtists
                val leftCover = if (topArtists.size > 1) topArtists[1].albumArtUrl else topTrack?.albumArtUrl
                val centerCover = topTrack?.albumArtUrl ?: topArtists.firstOrNull()?.albumArtUrl
                val rightCover = if (topArtists.size > 2) topArtists[2].albumArtUrl else (topArtists.firstOrNull()?.albumArtUrl ?: topTrack?.albumArtUrl)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album Pendamping Kiri (sudut miring rapi -4 deg, padding pembatas tegas)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .rotate(-4f)
                                .border(1.5.dp, Color(0xFF666666), RoundedCornerShape(2.dp))
                                .background(Color(0xFF1A1A1A), RoundedCornerShape(2.dp))
                                .clip(RoundedCornerShape(2.dp))
                        ) {
                            if (!leftCover.isNullOrBlank()) {
                                AsyncImage(
                                    model = leftCover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "#02",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF888888)
                        )
                    }

                    // Album Utama di Tengah (Sedikit lebih besar, aksen merah neon)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(94.dp)
                                .border(2.dp, CrimsonRed, RoundedCornerShape(2.dp))
                                .background(Color(0xFF141414), RoundedCornerShape(2.dp))
                                .clip(RoundedCornerShape(2.dp))
                        ) {
                            if (!centerCover.isNullOrBlank()) {
                                AsyncImage(
                                    model = centerCover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "#01 HEAVY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black,
                            color = CrimsonRed
                        )
                    }

                    // Album Pendamping Kanan (sudut miring rapi 4 deg, padding pembatas tegas)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .rotate(4f)
                                .border(1.5.dp, Color(0xFF666666), RoundedCornerShape(2.dp))
                                .background(Color(0xFF1A1A1A), RoundedCornerShape(2.dp))
                                .clip(RoundedCornerShape(2.dp))
                        ) {
                            if (!rightCover.isNullOrBlank()) {
                                AsyncImage(
                                    model = rightCover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "#03",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF888888)
                        )
                    }
                }

                // Baris Bawah (Blok Data): Tabel data sederhana bergaris batas brutalist
                // untuk memuat TOTAL DISTORSI, BAND DIEKSPLORASI, dan TREK PALING RUSAK.
                // Berikan tinggi baris yang cukup dan pembatas vertikal sehingga angka dan teks keterangan tidak pernah berdekatan.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
                        .background(Color(0xFF141414), RoundedCornerShape(2.dp))
                ) {
                    // Baris 1: TOTAL DISTORSI | BAND DIEKSPLORASI (dengan pembatas vertikal di tengah)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        // Kolom Kiri: TOTAL DISTORSI
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "TOTAL DISTORSI",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF888888),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (recap.totalHours > 0) "${recap.totalHours} JAM ${recap.totalMinutes % 60}M" else "${recap.totalMinutes}M",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        // Pembatas Vertikal
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.5.dp)
                                .background(Color(0xFF333333))
                        )

                        // Kolom Kanan: BAND DIEKSPLORASI
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "BAND DIEKSPLORASI",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF888888),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${recap.uniqueArtistsCount} BAND",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = CrimsonRed
                            )
                        }
                    }

                    // Pembatas Horizontal
                    HorizontalDivider(color = Color(0xFF333333), thickness = 1.5.dp)

                    // Baris 2: TREK PALING RUSAK
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "TREK PALING RUSAK",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF888888),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = recap.topTrack?.trackTitle ?: "-",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = recap.topTrack?.artistName ?: "-",
                            fontFamily = CrankrBodyFont,
                            fontSize = 11.sp,
                            color = CrimsonRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Pembatas Horizontal
                    HorizontalDivider(color = Color(0xFF333333), thickness = 1.5.dp)

                    // Baris 3: Edisi / Barcode strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "|||| ||| ||||| ||||||| ||",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF666666)
                        )
                        Text(
                            text = "EDISI: #CKR-2026-NZT OFFICIAL",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonRed
                        )
                    }
                }
            }

            // Area Tombol: Kunci posisi tombol aksi di bagian bawah dengan padding aman dari tepi layar perangkat
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Concert Ticket Button (Torn Perforated Edge)
                Surface(
                    onClick = onShareInstagram,
                    enabled = !isExporting,
                    shape = ConcertTicketShape(cornerRadius = 2.dp, notchRadius = 9.dp),
                    color = CrimsonRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .springPressable(0.96f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isExporting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "MEMPROSES TIKET POSTER...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "BAGIKAN KE INSTAGRAM STORY",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                // Secondary Button (Gritty Bracket Outline)
                Surface(
                    onClick = onShareGeneral,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(2.dp),
                    color = Color(0xFF141414),
                    border = CardDefaults.outlinedCardBorder(enabled = !isExporting).copy(
                        brush = SolidColor(Color.White)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .springPressable(0.96f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ BAGIKAN GAMBAR LAINNYA / SIMPAN ]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
