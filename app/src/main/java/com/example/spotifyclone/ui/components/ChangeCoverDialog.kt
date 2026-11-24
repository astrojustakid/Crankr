package com.example.spotifyclone.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.spotifyclone.theme.*

data class CoverPreset(
    val name: String,
    val imageUrl: String
)

val PRESET_COVERS = listOf(
    CoverPreset("Neon Beats", "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80"),
    CoverPreset("Vintage Vinyl", "https://images.unsplash.com/photo-1539375665275-f9de415ef9ac?w=600&q=80"),
    CoverPreset("Lo-Fi Chill", "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&q=80"),
    CoverPreset("Crimson Flame", "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&q=80"),
    CoverPreset("Acoustic Sunset", "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&q=80"),
    CoverPreset("Cyber Groove", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80")
)

@Composable
fun ChangeCoverDialog(
    currentCoverUrl: String?,
    firstTrackCoverUrl: String?,
    onDismiss: () -> Unit,
    onUriSelected: (Uri) -> Unit,
    onUrlSelected: (String?) -> Unit
) {
    var showUrlInput by remember { mutableStateOf(false) }
    var inputUrlText by remember { mutableStateOf("") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onUriSelected(uri)
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(3.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UBAH SAMPUL PLAYLIST",
                        fontSize = 18.sp,
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = TextWhite
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current Cover Preview
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, CrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                        .background(ElevatedBackground),
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentCoverUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = currentCoverUrl,
                            contentDescription = "Sampul Saat Ini",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = CrimsonRed,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Option 1: Pilih dari Galeri HP (Utama)
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "PILIH DARI GALERI HP",
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp,
                        color = TextWhite
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Gunakan Cover Lagu Pertama (Jika ada)
                if (!firstTrackCoverUrl.isNullOrEmpty() && firstTrackCoverUrl != currentCoverUrl) {
                    OutlinedButton(
                        onClick = {
                            onUrlSelected(firstTrackCoverUrl)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Gunakan Cover Lagu Pertama",
                            fontFamily = CrankrBodyFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = TextWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Option 3: Presets Cover Estetik
                Text(
                    text = "Atau Pilih Preset Estetik:",
                    fontSize = 12.sp,
                    fontFamily = CrankrBodyFont,
                    color = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(vertical = 6.dp)
                )

                // Grid 3x2 of Presets
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PRESET_COVERS.chunked(3).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(3.dp))
                                        .clickable {
                                            onUrlSelected(preset.imageUrl)
                                            onDismiss()
                                        }
                                        .background(ElevatedBackground)
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(2.dp))
                                    ) {
                                        AsyncImage(
                                            model = preset.imageUrl,
                                            contentDescription = preset.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = preset.name,
                                        color = TextSecondary,
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Option 4: Input URL Manual
                if (!showUrlInput) {
                    TextButton(
                        onClick = { showUrlInput = true },
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tempel Link Gambar (URL)",
                            color = TextSecondary,
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.5.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(ElevatedBackground)
                            .padding(10.dp)
                    ) {
                        OutlinedTextField(
                            value = inputUrlText,
                            onValueChange = { inputUrlText = it },
                            placeholder = { Text("https://example.com/cover.jpg", color = TextMuted, fontSize = 12.sp, fontFamily = CrankrBodyFont) },
                            singleLine = true,
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = CrimsonRed,
                                unfocusedBorderColor = BorderSubtle
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showUrlInput = false },
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Text("Batal", color = TextSecondary, fontFamily = CrankrDisplayFont, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    if (inputUrlText.isNotBlank()) {
                                        onUrlSelected(inputUrlText.trim())
                                        onDismiss()
                                    }
                                },
                                shape = RoundedCornerShape(2.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                            ) {
                                Text("TERAPKAN", color = OnPrimary, fontFamily = CrankrDisplayFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Option 5: Reset Cover
                if (!currentCoverUrl.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            onUrlSelected(null)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = CrimsonRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Hapus Cover (Gunakan Ikon Bawaan)",
                            color = CrimsonRed,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }
    }
}