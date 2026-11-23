package com.example.spotifyclone.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.spotifyclone.data.ListeningStats
import com.example.spotifyclone.theme.*

@Composable
fun CrankrStatsDialog(
    stats: ListeningStats,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(3.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(1.5.dp, CrimsonRed, RoundedCornerShape(3.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with punk zine title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(CrimsonRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Statistik Crankr",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "Laporan Mendengarkan Musik Punk Tech",
                            fontFamily = CrankrBodyFont,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Persona Banner (Punk Gig Ticket Style)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(CardBackground)
                        .border(1.dp, CrimsonRed.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LISTENER PERSONA",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonRed,
                                letterSpacing = 1.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = CrimsonRed.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonRed)
                            ) {
                                Text(
                                    text = stats.personaBadge,
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CrimsonRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stats.personaTitle,
                            fontFamily = CrankrDisplayFont,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "\"${stats.personaSubtitle}\"",
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2-Column Key Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total Minutes Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(CardBackground)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(2.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "WAKTU MENDENGAR",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 10.sp,
                                color = TextMuted,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val formattedTime = when {
                                stats.totalMinutes >= 60 -> {
                                    val hours = stats.totalMinutes / 60
                                    val mins = stats.totalMinutes % 60
                                    if (mins > 0) "$hours JAM $mins MENIT" else "$hours JAM"
                                }
                                stats.totalMinutes > 0 -> "${stats.totalMinutes} MENIT"
                                else -> "< 1 MENIT"
                            }
                            Text(
                                text = formattedTime,
                                fontFamily = CrankrDisplayFont,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonRed
                            )
                        }
                    }

                    // Total Tracks Played Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(CardBackground)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(2.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "LAGU DIPUTAR",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 10.sp,
                                color = TextMuted,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${stats.totalTracksPlayed} TRACK",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Top Tracks Shelf
                Text(
                    text = "TOP 5 LAGU PALING SERING DIPUTAR",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonRed,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (stats.topTracks.isEmpty()) {
                    Text(
                        text = "Belum ada data lagu. Putar musik untuk mulai merekam statistik!",
                        fontFamily = CrankrBodyFont,
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        stats.topTracks.take(4).forEachIndexed { idx, (trackName, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(CardBackground)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${idx + 1}",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CrimsonRed
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = trackName,
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 12.sp,
                                        color = TextWhite,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = "${count}x",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Share Zine Button
                    Button(
                        onClick = {
                            val durationShareText = if (stats.totalMinutes >= 60) {
                                "${stats.totalMinutes / 60} Jam ${stats.totalMinutes % 60} Menit"
                            } else {
                                "${stats.totalMinutes} Menit"
                            }
                            val shareText = """
                                CRANKR MUSIC STATS
                                Persona: ${stats.personaTitle} (${stats.personaBadge})
                                "${stats.personaSubtitle}"
                                
                                Total Mendengarkan: $durationShareText
                                Total Lagu Diputar: ${stats.totalTracksPlayed} Lagu
                                
                                Top Lagu:
                                ${stats.topTracks.take(3).mapIndexed { i, t -> "${i + 1}. ${t.first} (${t.second}x)" }.joinToString("\n")}
                                
                                Diputar via Crankr - Punk Rock & Tech Music Player
                            """.trimIndent()

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Bagikan Statistik Crankr"))
                        },
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BAGIKAN",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Close Button
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "TUTUP",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
