package com.example.spotifyclone.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.ui.components.springPressable

@Composable
fun OfflineScreen(
    downloadedTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    isOfflineOnlyMode: Boolean,
    storageUsage: String,
    onOfflineModeToggle: (Boolean) -> Unit,
    onTrackClick: (Track) -> Unit,
    onDeleteDownloadClick: (Track) -> Unit,
    onPlayAllOfflineClick: () -> Unit,
    onDeleteAllDownloadsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    val filteredTracks = remember(downloadedTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            downloadedTracks
        } else {
            downloadedTracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PitchBlack)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Screen Header
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DOWNLOADS",
                    fontSize = 28.sp,
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(CrimsonRed)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Storage & Offline Mode Switch Card
        item {
            Card(
                shape = RoundedCornerShape(3.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(3.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Offline Mode",
                                    tint = if (isOfflineOnlyMode) CrimsonRed else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Offline Mode Only",
                                    color = TextWhite,
                                    fontFamily = CrankrDisplayFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Only play downloaded songs to save mobile data",
                                color = TextSecondary,
                                fontFamily = CrankrBodyFont,
                                fontSize = 12.sp
                            )
                        }

                        Switch(
                            checked = isOfflineOnlyMode,
                            onCheckedChange = onOfflineModeToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextWhite,
                                checkedTrackColor = CrimsonRed,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = ElevatedBackground
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = BorderSubtle
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Storage",
                                tint = CrimsonRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Storage: $storageUsage (${downloadedTracks.size} tracks)",
                                color = TextSecondary,
                                fontFamily = CrankrBodyFont,
                                fontSize = 12.5.sp
                            )
                        }

                        if (downloadedTracks.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onDeleteAllDownloadsClick != null) {
                                    OutlinedButton(
                                        onClick = { showDeleteAllDialog = true },
                                        shape = RoundedCornerShape(2.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.springPressable(0.95f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = "Hapus Semua Unduhan",
                                            tint = CrimsonRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "KOSONGKAN",
                                            fontFamily = CrankrDisplayFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Button(
                                    onClick = onPlayAllOfflineClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                    shape = RoundedCornerShape(2.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.springPressable(0.95f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play All",
                                        tint = TextWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "PUTAR SEMUA",
                                        color = TextWhite,
                                        fontFamily = CrankrDisplayFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Real-time Search Box for Downloads
        if (downloadedTracks.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    placeholder = {
                        Text(
                            text = "Cari di lagu unduhan...",
                            fontSize = 13.sp,
                            fontFamily = CrankrBodyFont,
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = if (searchQuery.isNotEmpty()) CrimsonRed else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(3.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        cursorColor = CrimsonRed
                    )
                )
            }
        }

        // Section Title & Search Count
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DOWNLOADED SONGS",
                    fontSize = 18.sp,
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = TextWhite
                )

                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "${filteredTracks.size} dari ${downloadedTracks.size}",
                        fontSize = 12.sp,
                        fontFamily = CrankrBodyFont,
                        color = CrimsonRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Downloaded tracks list or Empty State
        if (downloadedTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = "No downloads",
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No offline tracks yet",
                            color = TextWhite,
                            fontFamily = CrankrDisplayFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the download icon on any track to listen offline without an internet connection.",
                            color = TextSecondary,
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else if (filteredTracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lagu \"$searchQuery\" tidak ditemukan di unduhan",
                        color = TextWhite,
                        fontFamily = CrankrBodyFont,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            itemsIndexed(filteredTracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                val delayMs = (index * 30).coerceAtMost(300)
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(track.id) {
                    delay(delayMs.toLong())
                    isVisible = true
                }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                            slideInVertically(
                                initialOffsetY = { it / 6 },
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            ),
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(220),
                        fadeOutSpec = tween(200),
                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f)
                    )
                ) {
                    DownloadedTrackItem(
                        track = track,
                        isCurrent = currentTrack?.id == track.id,
                        isPlaying = isPlaying,
                        onClick = { onTrackClick(track) },
                        onDeleteClick = { onDeleteDownloadClick(track) }
                    )
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = {
                Text(
                    text = "HAPUS SEMUA UNDUHAN?",
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Semua file audio (${downloadedTracks.size} lagu, $storageUsage) yang tersimpan di memori HP Anda akan dihapus permanen untuk mengosongkan ruang.",
                    fontFamily = CrankrBodyFont,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        onDeleteAllDownloadsClick?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text(
                        text = "HAPUS SEMUA",
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllDialog = false },
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text(
                        text = "BATAL",
                        fontFamily = CrankrBodyFont,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            },
            containerColor = DarkCharcoal,
            shape = RoundedCornerShape(3.dp)
        )
    }
}

@Composable
fun DownloadedTrackItem(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(if (isCurrent) CardBackground else Color.Transparent)
            .then(
                if (isCurrent) Modifier.border(1.dp, CrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                else Modifier
            )
            .springPressable(0.96f)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(2.dp))
                .background(ElevatedBackground)
        ) {
            Crossfade(
                targetState = track.coverUrl,
                animationSpec = tween(300),
                label = "DownloadedCoverCrossfade"
            ) { targetCover ->
                AsyncImage(
                    model = targetCover,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(13.dp)
                            .background(CrimsonRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = track.title,
                    color = if (isCurrent) CrimsonRed else TextWhite,
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = CrimsonRed,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${track.artist} • ${track.formattedDuration}",
                    color = TextSecondary,
                    fontFamily = CrankrBodyFont,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
                .size(32.dp)
                .springPressable(0.90f)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete Download",
                tint = TextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
