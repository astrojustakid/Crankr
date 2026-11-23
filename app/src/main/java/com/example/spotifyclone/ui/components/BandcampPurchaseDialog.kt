package com.example.spotifyclone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.theme.*

/**
 * Dialog edukatif & apresiatif saat pengguna memilih lagu Bandcamp yang berstatus Purchase-Only (Terkunci).
 * Mengarahkan pendengar untuk membeli rilisan secara langsung dari musisi independen.
 */
@Composable
fun BandcampPurchaseDialog(
    track: Track,
    onDismiss: () -> Unit,
    onBuyClick: () -> Unit,
    onSearchAlternative: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF141414),
            border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f)),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar with Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CrimsonRedSoft,
                            border = BorderStroke(0.5.dp, CrimsonRed.copy(alpha = 0.6f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "PURCHASE-ONLY",
                                    color = CrimsonRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = CrankrDisplayFont,
                                    letterSpacing = 0.6.sp
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Artwork Preview
                if (track.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Track Info
                Text(
                    text = track.title,
                    fontFamily = CrankrBodyFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextWhite,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${track.artist}${if (track.album.isNotBlank() && track.album != "Single") " • ${track.album}" else ""}",
                    fontFamily = CrankrBodyFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!track.purchasePrice.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF221A0F),
                        border = BorderStroke(0.5.dp, Color(0xFFFF8A00).copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "HARGA RILIS: ${track.purchasePrice}",
                            color = Color(0xFFFF8A00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CrankrDisplayFont,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Message
                Text(
                    text = "Musisi mengunci audio lagu ini khusus untuk pembeli resmi di Bandcamp. Dukung mereka secara langsung agar musik independen terus hidup.",
                    fontFamily = CrankrBodyFont,
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Button 1: Buy on Bandcamp (Primary Neon Button)
                Button(
                    onClick = onBuyClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .springPressable(scaleDown = 0.97f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BELI DI BANDCAMP ↗",
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Button 2: Search Alternative Streaming
                OutlinedButton(
                    onClick = onSearchAlternative,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .springPressable(scaleDown = 0.97f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cari Versi Streaming Lainnya",
                        fontFamily = CrankrBodyFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = TextWhite.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

