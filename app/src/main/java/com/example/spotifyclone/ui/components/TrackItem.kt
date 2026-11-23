package com.example.spotifyclone.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import coil.compose.AsyncImage
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackItem(
    track: Track,
    isPlaying: Boolean,
    isCurrent: Boolean,
    isFavorite: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddToPlaylistClick: (() -> Unit)? = null,
    onPlayNextInQueue: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onShareTrackClick: (() -> Unit)? = null,
    trackNumber: Int? = null,
    subtitleOverride: String? = null,
    showDurationOnRight: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .springPressable(0.96f)
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isCurrent) ElevatedBackground.copy(alpha = 0.7f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track index rank number if provided
        if (trackNumber != null) {
            Text(
                text = "$trackNumber",
                color = if (isCurrent) CrimsonRed else TextMuted,
                fontFamily = CrankrDisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier
                    .width(26.dp)
                    .padding(end = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        // Track Artwork with 8dp rounded corners (Clean, no harsh border)
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ElevatedBackground)
        ) {
            Crossfade(
                targetState = track.coverUrl,
                animationSpec = tween(300),
                label = "TrackItemCoverCrossfade"
            ) { targetCover ->
                AsyncImage(
                    model = targetCover,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Playing Equalizer Indicator (Animated Bouncing Equalizer)
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.68f)),
                    contentAlignment = Alignment.Center
                ) {
                    BouncingEqualizer(
                        isPlaying = isPlaying,
                        color = CrimsonRed,
                        barCount = 4,
                        barWidth = 3.dp,
                        barSpacing = 2.dp,
                        maxHeight = 16.dp,
                        minHeight = 3.5.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist Column with wide breathing room
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(13.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(CrimsonRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    MarqueeText(
                        text = track.title,
                        color = CrimsonRed,
                        fontFamily = CrankrBodyFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        active = isPlaying,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    Text(
                        text = track.title,
                        color = TextWhite,
                        fontFamily = CrankrBodyFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (track.isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = CrimsonRed,
                        modifier = Modifier
                            .size(12.dp)
                            .padding(end = 4.dp)
                    )
                }

                if (track.isLockedPurchaseOnly) {
                    Surface(
                        color = Color(0xFF261E14),
                        shape = RoundedCornerShape(3.dp),
                        border = BorderStroke(0.5.dp, Color(0xFFFF8A00).copy(alpha = 0.7f)),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFFFF8A00),
                                modifier = Modifier.size(9.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (!track.purchasePrice.isNullOrBlank()) "BELI ${track.purchasePrice}" else "BUY ONLY",
                                color = Color(0xFFFF8A00),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CrankrDisplayFont,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                } else if (!track.isPlayable) {
                    Surface(
                        color = Color(0xFF2A1515),
                        shape = RoundedCornerShape(3.dp),
                        border = BorderStroke(0.5.dp, CrimsonRed.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "TIDAK TERSEDIA",
                            color = CrimsonRed,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CrankrDisplayFont,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                        )
                    }
                } else if (track.isBandcamp) {
                    Surface(
                        color = CrimsonRedSoft,
                        shape = RoundedCornerShape(3.dp),
                        border = BorderStroke(0.5.dp, CrimsonRed.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "BANDCAMP",
                            color = CrimsonRed,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CrankrDisplayFont,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                        )
                    }
                }

                Text(
                    text = subtitleOverride ?: if (track.album.isNotBlank() && track.album != "Single") "${track.artist} • ${track.album}" else track.artist,
                    color = TextSecondary,
                    fontFamily = CrankrBodyFont,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Clean & uncluttered action buttons: Download indicator / Favorite + MoreVert
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Download progress indicator if actively downloading
            if (downloadProgress != null) {
                CircularProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.size(18.dp),
                    color = CrimsonRed,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Favorite Button
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier
                    .size(32.dp)
                    .springPressable(scaleDown = 0.92f)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) CrimsonRed else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Duration text on the right side
            if (showDurationOnRight) {
                Text(
                    text = track.formattedDuration,
                    color = TextMuted,
                    fontFamily = CrankrBodyFont,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // More Options Button
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(32.dp)
                    .springPressable(scaleDown = 0.92f)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opsi Lainnya",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // Modal Bottom Sheet: Track Options (Spring Easing & Background Dimming)
    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            containerColor = DarkCharcoal,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted.copy(alpha = 0.5f))
                )
            },
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Header with Track Artwork, Title, and Artist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .border(0.5.dp, BorderSubtle, RoundedCornerShape(2.dp))
                    ) {
                        AsyncImage(
                            model = track.coverUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontFamily = CrankrBodyFont,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            fontFamily = CrankrBodyFont,
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

                if (onPlayNextInQueue != null) {
                    TrackOptionRow(
                        title = "Putar Berikutnya",
                        icon = Icons.Default.PlaylistPlay,
                        onClick = {
                            showMenu = false
                            onPlayNextInQueue()
                        }
                    )
                }

                if (onAddToQueue != null) {
                    TrackOptionRow(
                        title = "Tambah ke Antrean",
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        onClick = {
                            showMenu = false
                            onAddToQueue()
                        }
                    )
                }

                if (onAddToPlaylistClick != null) {
                    TrackOptionRow(
                        title = "Tambah ke Playlist",
                        icon = Icons.Default.PlaylistAdd,
                        onClick = {
                            showMenu = false
                            onAddToPlaylistClick()
                        }
                    )
                }

                TrackOptionRow(
                    title = if (track.isDownloaded) "Lagu Sudah Diunduh" else "Download Lagu",
                    icon = if (track.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                    tint = if (track.isDownloaded) TextMuted else CrimsonRed,
                    textColor = if (track.isDownloaded) TextMuted else TextWhite,
                    onClick = {
                        showMenu = false
                        if (!track.isDownloaded) {
                            onDownloadClick()
                        }
                    }
                )

                if (onShareTrackClick != null) {
                    TrackOptionRow(
                        title = "Bagikan Kartu Lagu",
                        icon = Icons.Default.Share,
                        tint = CrimsonRed,
                        textColor = TextWhite,
                        onClick = {
                            showMenu = false
                            onShareTrackClick()
                        }
                    )
                }

                if (onRemoveFromPlaylist != null) {
                    HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))
                    TrackOptionRow(
                        title = "Hapus dari Playlist",
                        icon = Icons.Default.DeleteOutline,
                        tint = CrimsonRed,
                        textColor = CrimsonRed,
                        onClick = {
                            showMenu = false
                            onRemoveFromPlaylist()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackOptionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = CrimsonRed,
    textColor: Color = TextWhite,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springPressable(scaleDown = 0.96f)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontFamily = CrankrBodyFont,
            fontWeight = FontWeight.Medium,
            color = textColor,
            fontSize = 14.5.sp
        )
    }
}

