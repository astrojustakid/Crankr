package com.example.spotifyclone.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifyclone.R
import com.example.spotifyclone.theme.*

/**
 * AboutScreen (Profil Developer):
 * Layar Tentang Developer bergaya visual Dark Zine / Brutalist khas Crankr:
 * - Header Aplikasi: CRANKR, versi aplikasi v1.0, and label deck.
 * - Hero Card: Foto biarawati sebagai latar belakang + gradient scrim gelap kontras tinggi.
 * - Identitas Developer: AstroKido dengan tipografi tegas & badge DEVELOPER.
 * - Avatar Bulat: Ilustrasi tengkorak punk di sudut kanan bawah kartu dengan border tipis.
 * - Sosial Media Cards: Kartu Instagram & TikTok yang membuka Intent.ACTION_VIEW.
 */
@Composable
fun AboutScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val punchyRed = CrimsonRed

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to HomeTopGlow,
                        0.32f to Surface,
                        1.0f to PitchBlack
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.example.spotifyclone.ui.components.GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        onClick = onDismiss,
                        size = 38.dp,
                        iconSize = 19.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Tentang Crankr",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Text(
                            text = "Developer Profile",
                            fontFamily = CrankrBodyFont,
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = DarkCharcoal,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onDismiss() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Hero Card Developer (Foto Biarawati Latar Belakang + Avatar Bulat Tengkorak Selfie)
                Card(
                    shape = RoundedCornerShape(3.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
                    border = BorderStroke(1.5.dp, punchyRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Background: Foto Biarawati Membaca Buku Sambil Merokok
                        Image(
                            painter = painterResource(id = R.drawable.dev_hero_nun),
                            contentDescription = "Latar Belakang Developer",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Gradient Scrim Gelap agar teks terbaca tajam dan kontras
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            PitchBlack.copy(alpha = 0.20f),
                                            PitchBlack.copy(alpha = 0.60f),
                                            PitchBlack.copy(alpha = 0.95f)
                                        )
                                    )
                                )
                        )

                        // Bottom Section: Identitas Developer & Circular Skull Avatar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Info Developer
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = punchyRed,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Text(
                                        text = "DEVELOPER",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = "AstroKido",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite,
                                    letterSpacing = 1.5.sp
                                )

                                Text(
                                    text = "Creator of Crankr Music Player",
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Foto Profil Bulat: Ilustrasi Tengkorak Selfie di sudut kanan bawah
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(PitchBlack)
                                    .border(2.dp, punchyRed, CircleShape)
                                    .padding(3.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.dev_avatar_skull),
                                    contentDescription = "Avatar Developer AstroKido",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                // 2. Seksi Media Sosial dan Tautan
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "MEDIA SOSIAL & TAUTAN",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Kartu Instagram
                    SocialMediaCard(
                        platformName = "Instagram",
                        username = "@astronotdarat___",
                        url = "https://instagram.com/astronotdarat___",
                        accentColor = punchyRed,
                        onOpenUrl = { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Kartu TikTok
                    SocialMediaCard(
                        platformName = "TikTok",
                        username = "@sagijustakid",
                        url = "https://tiktok.com/@sagijustakid",
                        accentColor = Color(0xFF00F2FE),
                        onOpenUrl = { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialMediaCard(
    platformName: String,
    username: String,
    url: String,
    accentColor: Color,
    onOpenUrl: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(url) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Platform Badge Icon / Indicator
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = PitchBlack,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (platformName == "Instagram") "IG" else "TT",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = platformName.uppercase(),
                        fontFamily = CrankrDisplayFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = username,
                        fontFamily = CrankrDisplayFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "BUKA",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonRed
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Buka Link",
                    tint = CrimsonRed,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
