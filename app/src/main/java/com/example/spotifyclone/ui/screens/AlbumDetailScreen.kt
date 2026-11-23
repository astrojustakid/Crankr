package com.example.spotifyclone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.data.repository.AlbumRelease
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.ui.components.TrackItem
import com.example.spotifyclone.ui.components.springPressable

/**
 * Screen displaying the full tracklist and metadata of an Album, EP, or Single.
 */
@Composable
fun AlbumDetailScreen(
    album: AlbumRelease,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    downloadProgressMap: Map<String, Float>,
    isLoading: Boolean = false,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAllClick: (List<Track>) -> Unit,
    onShufflePlayClick: (List<Track>) -> Unit,
    onDownloadClick: (Track) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit = {},
    onPlayNextInQueue: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onShareTrack: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PitchBlack)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. Top Navigation & Album Hero Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    DarkCharcoal,
                                    PitchBlack
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Back Navigation Button
                    Surface(
                        onClick = onBackClick,
                        shape = CircleShape,
                        color = CardBackground,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .size(40.dp)
                            .springPressable(scaleDown = 0.92f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali ke Artis",
                                tint = TextWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Center: Album Artwork
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .aspectRatio(1f)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(3.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(3.dp))
                            .background(CardBackground)
                    ) {
                        if (album.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = album.coverUrl,
                                contentDescription = album.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Title
                    Text(
                        text = album.title,
                        color = TextWhite,
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Artist & Metadata Label: "[Tahun Rilis] • [Tipe Rilis] • [Jumlah Trek] Lagu"
                    Text(
                        text = "${album.artistName} • ${album.releaseYear} • ${album.releaseType}" +
                                if (tracks.isNotEmpty()) " • ${tracks.size} Lagu" else "",
                        color = TextSecondary,
                        fontFamily = CrankrBodyFont,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Controls: Play All & Shuffle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Shuffle
                        Surface(
                            onClick = { onShufflePlayClick(tracks) },
                            enabled = tracks.isNotEmpty(),
                            shape = RoundedCornerShape(2.dp),
                            color = CardBackground,
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.springPressable(scaleDown = 0.94f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Putar Acak",
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ACAK",
                                    color = TextWhite,
                                    fontFamily = CrankrDisplayFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Play
                        Surface(
                            onClick = { onPlayAllClick(tracks) },
                            enabled = tracks.isNotEmpty(),
                            shape = RoundedCornerShape(2.dp),
                            color = CrimsonRed,
                            modifier = Modifier.springPressable(scaleDown = 0.94f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Semua",
                                    tint = TextWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PLAY",
                                    color = TextWhite,
                                    fontFamily = CrankrDisplayFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // 2. Section Header: "DAFTAR LAGU"
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .background(CrimsonRed, RoundedCornerShape(1.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DAFTAR LAGU",
                        color = TextWhite,
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // 3. Loading state
            if (isLoading && tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CrimsonRed,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else if (tracks.isEmpty() && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Trek tidak tersedia",
                            color = TextMuted,
                            fontFamily = CrankrBodyFont,
                            fontSize = 13.5.sp
                        )
                    }
                }
            } else {
                // 4. Album Tracks List
                itemsIndexed(
                    items = tracks,
                    key = { index, track -> "${track.id}_$index" }
                ) { index, track ->
                    TrackItem(
                        track = track,
                        isPlaying = isPlaying && currentTrack?.id == track.id,
                        isCurrent = currentTrack?.id == track.id,
                        isFavorite = favoriteIds.contains(track.id),
                        downloadProgress = downloadProgressMap[track.id],
                        trackNumber = index + 1,
                        subtitleOverride = album.artistName,
                        showDurationOnRight = true,
                        onClick = { onTrackClick(track) },
                        onDownloadClick = { onDownloadClick(track) },
                        onFavoriteClick = { onFavoriteClick(track.id) },
                        onAddToPlaylistClick = { onAddToPlaylistClick(track) },
                        onPlayNextInQueue = { onPlayNextInQueue(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onShareTrackClick = { onShareTrack(track) }
                    )
                }
            }
        }
    }
}
