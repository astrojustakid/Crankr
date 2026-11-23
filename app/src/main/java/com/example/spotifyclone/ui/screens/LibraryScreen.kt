package com.example.spotifyclone.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifyclone.R
import com.example.spotifyclone.data.model.SpotifyPlaylist
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.ui.components.BackupRestoreDialog
import com.example.spotifyclone.ui.components.springPressable
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(
    spotifyPlaylists: List<SpotifyPlaylist>,
    likedSongsPlaylist: SpotifyPlaylist?,
    recentlyPlayedTracks: List<Track>,
    onPlaylistClick: (SpotifyPlaylist) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onTrackClick: (Track) -> Unit,
    onGetBackupJson: suspend () -> String,
    onRestoreJson: (String) -> Unit,
    onRecapClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }

    val filteredPlaylists = remember(spotifyPlaylists, searchQuery) {
        val nonLiked = spotifyPlaylists.filter { it.id != "liked_songs" }
        if (searchQuery.isBlank()) {
            nonLiked
        } else {
            val q = searchQuery.trim().lowercase()
            nonLiked.filter {
                it.name.lowercase().contains(q) ||
                (it.description?.lowercase()?.contains(q) == true) ||
                it.ownerName.lowercase().contains(q)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Neutral)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // 1. Screen TopAppBar: crankrlogo + CRANKR Title + Action Buttons
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.crankrlogo),
                        contentDescription = "CRANKR Logo",
                        modifier = Modifier.height(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CRANKR",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        letterSpacing = 1.2.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Create playlist button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Primary,
                        modifier = Modifier
                            .springPressable(0.95f)
                            .clickable { showCreateDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Buat Playlist",
                                tint = Neutral,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "PLAYLIST",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Neutral,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 2. Panel Status Atas untuk Metrik: Lagu Disukai, Playlist, Mode Offline
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Metrik 1: Lagu Disukai
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (likedSongsPlaylist != null) onPlaylistClick(likedSongsPlaylist)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${likedSongsPlaylist?.trackCount ?: 0}",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Text(
                            text = "Lagu Disukai",
                            fontFamily = CrankrBodyFont,
                            fontSize = 10.5.sp,
                            color = Tertiary
                        )
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(Border)
                    )

                    // Metrik 2: Playlist
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${spotifyPlaylists.size}",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Text(
                            text = "Playlist",
                            fontFamily = CrankrBodyFont,
                            fontSize = 10.5.sp,
                            color = Tertiary
                        )
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(Border)
                    )

                    // Metrik 3: Mode Offline
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OfflinePin,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AKTIF",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Mode Offline",
                            fontFamily = CrankrBodyFont,
                            fontSize = 10.5.sp,
                            color = Tertiary
                        )
                    }
                }
            }
        }

        // 3. Kartu Cadangan Database dengan 2 Tombol Aksi: Ekspor JSON & Pulihkan Cadangan
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CADANGAN BASIS DATA",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                letterSpacing = 0.8.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${spotifyPlaylists.size} Item",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Amankan playlist, riwayat putar, dan lagu favorit Anda ke file cadangan JSON lokal.",
                        fontFamily = CrankrBodyFont,
                        fontSize = 11.5.sp,
                        color = Tertiary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tombol 1: Ekspor JSON (Outlined)
                        OutlinedButton(
                            onClick = { showBackupRestoreDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = TextWhite
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = Tertiary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ekspor JSON",
                                fontFamily = CrankrBodyFont,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextWhite
                            )
                        }

                        // Tombol 2: Pulihkan Cadangan (Solid Primary #D9822B)
                        Button(
                            onClick = { showBackupRestoreDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor = Neutral
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = Neutral,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pulihkan Cadangan",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Neutral
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Search Input in Library
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Cari di library / koleksi...",
                        fontFamily = CrankrBodyFont,
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (searchQuery.isNotEmpty()) Primary else Tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardSurface,
                    unfocusedContainerColor = CardSurface,
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    cursorColor = Primary
                )
            )
        }

        // Featured: Liked Songs Card
        if (likedSongsPlaylist != null && searchQuery.isBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .springPressable(0.982f)
                        .clickable { onPlaylistClick(likedSongsPlaylist) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Lagu yang Disukai",
                                tint = Primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LAGU YANG DISUKAI",
                                fontFamily = CrankrDisplayFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextWhite,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${likedSongsPlaylist.trackCount} lagu favorit tersimpan",
                                fontFamily = CrankrBodyFont,
                                fontSize = 12.sp,
                                color = Tertiary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Putar",
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // Playlists Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PLAYLIST SAYA",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${filteredPlaylists.size} playlist",
                    fontFamily = CrankrBodyFont,
                    fontSize = 12.sp,
                    color = Tertiary
                )
            }
        }

        // Playlists List
        if (filteredPlaylists.isEmpty()) {
            item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "Tidak ada playlist sesuai pencarian" else "Belum ada playlist",
                                fontFamily = CrankrBodyFont,
                                color = Tertiary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(filteredPlaylists, key = { index, playlist -> "${playlist.id}_$index" }) { index, playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardSurface)
                            .springPressable(0.982f)
                            .clickable { onPlaylistClick(playlist) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElevatedBackground)
                        ) {
                            if (!playlist.imageUrl.isNullOrEmpty()) {
                                Crossfade(
                                    targetState = playlist.imageUrl,
                                    animationSpec = tween(300),
                                    label = "LibraryCoverCrossfade"
                                ) { targetCover ->
                                    AsyncImage(
                                        model = targetCover,
                                        contentDescription = playlist.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.Center)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                fontFamily = CrankrDisplayFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${playlist.ownerName} • ${playlist.trackCount} lagu",
                                fontFamily = CrankrBodyFont,
                                fontSize = 12.sp,
                                color = Tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

    // Create Playlist Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
                newPlaylistDesc = ""
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = CardSurface,
            title = {
                Text(
                    text = "BUAT PLAYLIST BARU",
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextWhite,
                    letterSpacing = 0.5.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Nama Playlist", fontFamily = CrankrBodyFont, fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Neutral,
                            unfocusedContainerColor = Neutral,
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Border,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )
                    OutlinedTextField(
                        value = newPlaylistDesc,
                        onValueChange = { newPlaylistDesc = it },
                        label = { Text("Deskripsi (Opsional)", fontFamily = CrankrBodyFont, fontSize = 12.sp) },
                        maxLines = 2,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Neutral,
                            unfocusedContainerColor = Neutral,
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Border,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim(), newPlaylistDesc.trim())
                            showCreateDialog = false
                            newPlaylistName = ""
                            newPlaylistDesc = ""
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Neutral)
                ) {
                    Text("SIMPAN", fontFamily = CrankrDisplayFont, fontWeight = FontWeight.Bold, color = Neutral)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showCreateDialog = false
                        newPlaylistName = ""
                        newPlaylistDesc = ""
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary)
                ) {
                    Text("BATAL", fontFamily = CrankrDisplayFont, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Backup & Restore Dialog
    if (showBackupRestoreDialog) {
        BackupRestoreDialog(
            playlistsCount = spotifyPlaylists.size,
            onGetBackupJson = onGetBackupJson,
            onRestoreJson = onRestoreJson,
            onDismiss = { showBackupRestoreDialog = false }
        )
    }
}
