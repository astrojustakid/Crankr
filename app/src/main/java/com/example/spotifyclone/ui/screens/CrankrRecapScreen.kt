package com.example.spotifyclone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.spotifyclone.data.db.CrankrRecapData
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.ui.components.springPressable
import com.example.spotifyclone.ui.viewmodel.RecapViewModel

/**
 * Crankr Recap Screen — Modern Industrial Punk Rock Minimalist
 * 
 * Mengusung estetika editorial industrial:
 * - Latar belakang hitam pekat solid (PitchBlack) tanpa bintik kotor/slop artifisial.
 * - Tipografi berbobot masif (Oswald / Anton) sebagai pahlawan visual.
 * - Garis pembatas hairline tajam dan aksen Crimson Red khas Crankr.
 * - 100% data riil dan transparan dari riwayat pemutaran lokal.
 */
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
            .background(Color(0xFF0D0D0D))
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
                        text = "MEMUAT AUDIT AUDIO...",
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
                personaQuote = "Baru mulai menyetel distorsi amplifier di garasi bawah tanah."
            )

            Column(modifier = Modifier.fillMaxSize()) {
                // Top Story Progress Indicators (4 segmented bars)
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
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    when {
                                        i < currentIndex -> CrimsonRed
                                        i == currentIndex -> Color.White
                                        else -> Color(0xFF242424)
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
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(CrimsonRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CRANKR RECAP",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = TextWhite,
                            letterSpacing = 1.5.sp
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
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
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentIndex > 0) {
                        TextButton(
                            onClick = { viewModel.prevCard() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFAAAAAA))
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SEBELUMNYA",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
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
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (currentIndex < 3) {
                        Button(
                            onClick = { viewModel.nextCard() },
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "LANJUT",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
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
 * Slide 1 (Audit Waktu Pemutaran):
 * Mengutamakan tipografi industrial berbobot masif, kontras tinggi,
 * dan penyajian data riil yang transparan tanpa visualizer/fake BPM artifisial.
 */
@Composable
private fun TotalTimeCard(recap: CrankrRecapData) {
    Card(
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Color(0xFF262626), RoundedCornerShape(2.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Kategori
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "// 01. LISTENING AUDIT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "ANNUAL STATS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF777777),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "TOTAL WAKTU ROTASI",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    lineHeight = 36.sp,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Total rekaman audio yang berhasil kamu putar tanpa kompromi.",
                    fontFamily = CrankrBodyFont,
                    fontSize = 13.sp,
                    color = Color(0xFF999999),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
            }

            // Stat Angka Utama Masif
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val totalHours = recap.totalHours
                val totalMinutes = recap.totalMinutes

                if (totalHours > 0) {
                    Text(
                        text = "$totalHours JAM",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 68.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${totalMinutes % 60} MENIT",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = CrimsonRed,
                        lineHeight = 40.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "$totalMinutes",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 76.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "MENIT DISTORSI",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = CrimsonRed,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .background(Color(0xFF1C1C1C), RoundedCornerShape(2.dp))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${recap.uniqueArtistsCount} BAND INDEPENDEN DIEKSPLORASI",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Footer Kartu: Persona Klasifikasi
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181818), RoundedCornerShape(2.dp))
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(2.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STATUS KLASIFIKASI",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF777777),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = recap.personaBadge,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = CrimsonRed
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = recap.personaTitle,
                    fontFamily = CrankrDisplayFont,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "\"${recap.personaQuote}\"",
                    fontFamily = CrankrBodyFont,
                    fontSize = 12.sp,
                    color = Color(0xFFAAAAAA),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * Slide 2 (Trek Paling Sering Diputar):
 * Fokus pada artwork album beresolusi tinggi dalam bingkai persegi murni,
 * bebas dari tempelan stiker atau distorsi yang mengganggu karya visual musisi.
 */
@Composable
private fun TopTrackCard(recap: CrankrRecapData) {
    val topTrack = recap.topTrack

    Card(
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Color(0xFF262626), RoundedCornerShape(2.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Kategori
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "// 02. HEAVY ROTATION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "NO. 1 TRACK",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF777777),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "TREK PALING RUSAK",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    lineHeight = 36.sp,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Lagu yang paling sering menguasai rotasi pemutar audio kamu:",
                    fontFamily = CrankrBodyFont,
                    fontSize = 13.sp,
                    color = Color(0xFF999999),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
            }

            // Konten Tengah: Cover Album & Metadata
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Wadah Cover Persegi Bersih
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .border(1.5.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
                        .background(Color(0xFF101010), RoundedCornerShape(2.dp))
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
                                text = "CRANKR\nTRACK",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF444444),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

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

                Spacer(modifier = Modifier.height(4.dp))

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

                // Info Durasi Resmi
                val avgSec = if ((topTrack?.playCount ?: 0) > 0) {
                    (((topTrack?.totalDurationMs ?: 0L) / topTrack!!.playCount) / 1000).toInt().coerceIn(30, 600)
                } else {
                    180
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DURASI: %d:%02d".format(avgSec / 60, avgSec % 60),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF777777),
                    textAlign = TextAlign.Center
                )
            }

            // Footer Badge Rotasi
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CrimsonRed.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                    .border(1.dp, CrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DIPUTAR ${topTrack?.playCount ?: 1} KALI DALAM ROTASI RESMI",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Slide 3 (5 Musisi Teratas):
 * Menampilkan daftar baris musisi independen dengan indeks nomor tajam (01 - 05),
 * foto artis bersih, dan informasi putaran yang presisi.
 */
@Composable
private fun TopArtistsCard(recap: CrankrRecapData) {
    Card(
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Color(0xFF262626), RoundedCornerShape(2.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Kategori
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "// 03. HEADPHONE LORDS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "TOP 5 ARTISTS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF777777),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "PENGUASA HEADPHONE",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    lineHeight = 36.sp,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Musisi yang paling mendominasi riwayat pemutaran audasimu:",
                    fontFamily = CrankrBodyFont,
                    fontSize = 13.sp,
                    color = Color(0xFF999999),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
            }

            // Daftar 5 Artis
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val artists = recap.topArtists.take(5)
                if (artists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada riwayat artis.",
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
                                .background(
                                    if (isFirst) Color(0xFF1E1717) else Color(0xFF181818),
                                    RoundedCornerShape(2.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isFirst) CrimsonRed else Color(0xFF262626),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Indeks nomor urut
                            Text(
                                text = "0${index + 1}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (isFirst) 18.sp else 15.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isFirst) CrimsonRed else Color(0xFF888888)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            // Foto Profil Artis / Album
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .border(1.dp, if (isFirst) CrimsonRed else Color(0xFF444444), RoundedCornerShape(2.dp))
                                    .background(Color(0xFF101010), RoundedCornerShape(2.dp))
                                    .clip(RoundedCornerShape(2.dp))
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
                                            text = "ART",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Nama & Total Putaran
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artistStat.artistName,
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = if (isFirst) 17.sp else 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${artistStat.playCount}x putaran",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (isFirst) CrimsonRed else Color(0xFF777777),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Tag Nomor 1
                            if (isFirst) {
                                Text(
                                    text = "NO. 1",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CrimsonRed,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // Footer Label
            Text(
                text = "DATA DIANALISIS BERDASARKAN TOTAL PEMUTARAN AUDIO LOKAL",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                color = Color(0xFF666666),
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Slide 4 (Poster Ringkasan Resmi):
 * Layout poster editorial industrial yang terisolasi dan proporsional:
 * - Showcase 3 cover album teratas berjejer rapi secara horizontal.
 * - Blok data modular untuk total waktu, artis dieksplorasi, dan lagu teratas.
 * - Tombol aksi berbagi Instagram Story & Simpan Gambar yang ergonomis dan kokoh.
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Color(0xFF262626), RoundedCornerShape(2.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Poster
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "// 04. NOISE POSTER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonRed,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "CRANKR RECAP",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = TextWhite,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1F1F1F), RoundedCornerShape(2.dp))
                            .border(1.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = recap.personaBadge,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
            }

            // Konten Tengah: 3 Cover Album Berjajar & Blok Data Grid
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val topTrack = recap.topTrack
                val topArtists = recap.topArtists
                val leftCover = if (topArtists.size > 1) topArtists[1].albumArtUrl else topTrack?.albumArtUrl
                val centerCover = topTrack?.albumArtUrl ?: topArtists.firstOrNull()?.albumArtUrl
                val rightCover = if (topArtists.size > 2) topArtists[2].albumArtUrl else (topArtists.firstOrNull()?.albumArtUrl ?: topTrack?.albumArtUrl)

                // 3 Cover Album Horizontal (Proporsional & Seimbang)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album Kiri (#02)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .border(1.dp, Color(0xFF444444), RoundedCornerShape(2.dp))
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "#02",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF777777)
                        )
                    }

                    // Album Tengah (#01 Utama)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "#01 UTAMA",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = CrimsonRed
                        )
                    }

                    // Album Kanan (#03)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .border(1.dp, Color(0xFF444444), RoundedCornerShape(2.dp))
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "#03",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF777777)
                        )
                    }
                }

                // Tabel Ringkasan Data Industrial
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF262626), RoundedCornerShape(2.dp))
                        .background(Color(0xFF181818), RoundedCornerShape(2.dp))
                ) {
                    // Baris 1: Total Distorsi | Band Dieksplorasi
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "TOTAL DISTORSI",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF888888),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (recap.totalHours > 0) "${recap.totalHours} JAM ${recap.totalMinutes % 60}M" else "${recap.totalMinutes}M",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Color(0xFF262626))
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "BAND DIEKSPLORASI",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF888888),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${recap.uniqueArtistsCount} BAND",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = CrimsonRed
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)

                    // Baris 2: Trek Paling Sering Diputar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "TREK PALING RUSAK",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF888888),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = recap.topTrack?.trackTitle ?: "-",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = recap.topTrack?.artistName ?: "-",
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.sp,
                            color = CrimsonRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Tombol Aksi Berbagi
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tombol Utama: Instagram Story
                Button(
                    onClick = onShareInstagram,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonRed,
                        disabledContainerColor = CrimsonRed.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .springPressable(0.96f)
                ) {
                    if (isExporting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "MEMPROSES POSTER...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
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
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "BAGIKAN KE INSTAGRAM STORY",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Tombol Sekunder: Simpan / Bagikan Lainnya
                OutlinedButton(
                    onClick = onShareGeneral,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .springPressable(0.96f)
                ) {
                    Text(
                        text = "SIMPAN GAMBAR / BAGIKAN LAINNYA",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
