package com.example.spotifyclone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.data.repository.AlbumRelease
import com.example.spotifyclone.theme.BorderSubtle
import com.example.spotifyclone.theme.CardBackground
import com.example.spotifyclone.theme.CrankrBodyFont
import com.example.spotifyclone.theme.CrankrDisplayFont
import com.example.spotifyclone.theme.CrimsonRed
import com.example.spotifyclone.theme.DarkCharcoal
import com.example.spotifyclone.theme.ElevatedBackground
import com.example.spotifyclone.theme.PitchBlack
import com.example.spotifyclone.theme.TextMuted
import com.example.spotifyclone.theme.TextSecondary
import com.example.spotifyclone.theme.TextWhite
import com.example.spotifyclone.ui.components.TrackItem
import com.example.spotifyclone.ui.viewmodel.ArtistViewModel

/**
 * ArtistDetailScreen Composable:
 * Komponen minimalis dan polos untuk menampilkan profil dan diskografi artis.
 * Langsung membaca StateFlow diskografi tanpa pemeriksaan kondisi yang rumit.
 * Mematikan spinner merah seketika jika isDiskografiLoading bernilai false.
 */
@Composable
fun ArtistDetailScreen(
    artistId: String,
    artistName: String? = null,
    viewModel: ArtistViewModel,
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    favoriteIds: Set<String> = emptySet(),
    downloadProgressMap: Map<String, Float> = emptyMap(),
    onBackClick: () -> Unit = {},
    onTrackClick: (Track) -> Unit = {},
    onAlbumClick: (AlbumRelease) -> Unit = {},
    onPlayAllClick: (List<Track>) -> Unit = {},
    onShufflePlayClick: (List<Track>) -> Unit = {},
    onDownloadClick: (Track) -> Unit = {},
    onFavoriteClick: (String) -> Unit = {},
    onAddToPlaylistClick: (Track) -> Unit = {},
    onPlayNextInQueue: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onShareTrack: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)

    // Panggil pemuatan konten artis (Top Tracks & Diskografi dalam 2 alur mandiri)
    LaunchedEffect(artistId) {
        viewModel.loadArtistContent(artistId)
        viewModel.loadArtist(artistId, artistName)
    }

    val uiState by viewModel.uiState.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()
    val isTopTracksLoading by viewModel.isTopTracksLoading.collectAsState()
    val diskografiList by viewModel.diskografiList.collectAsState()
    val isDiskografiLoading by viewModel.isDiskografiLoading.collectAsState()

    val displayName = uiState.artistName.ifBlank { artistName.orEmpty().ifBlank { "Artis" } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PitchBlack)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Header Foto Profil Artis dengan Gradasi Punk Minimalis
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    if (!uiState.officialPhotoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = uiState.officialPhotoUrl,
                            contentDescription = displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(CardBackground, DarkCharcoal)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Default Artist Avatar",
                                tint = TextMuted.copy(alpha = 0.25f),
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }

                    // Scrim atas untuk tombol kembali
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(PitchBlack.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                    )

                    // Fade bawah ke hitam pekat
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.25f to Color.Transparent,
                                    0.70f to PitchBlack.copy(alpha = 0.85f),
                                    1.0f to PitchBlack
                                )
                            )
                    )

                    // Tombol Kembali (Kiri Atas)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 14.dp, top = 12.dp)
                    ) {
                        Surface(
                            onClick = onBackClick,
                            shape = CircleShape,
                            color = PitchBlack.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kembali",
                                    tint = TextWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Nama & Info Artis di Bagian Bawah Header
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PROFIL ARTIS",
                                color = CrimsonRed,
                                fontFamily = CrankrDisplayFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = displayName,
                            color = TextWhite,
                            fontFamily = CrankrDisplayFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. Tombol Putar & Acak dan Lagu Populer (Pengecekan Data Riil)
            if (topTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onPlayAllClick(topTracks) },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Putar",
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PUTAR",
                                color = TextWhite,
                                fontFamily = CrankrDisplayFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = { onShufflePlayClick(topTracks) },
                            colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                            border = BorderStroke(1.dp, BorderSubtle),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Acak",
                                tint = TextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ACAK",
                                color = TextWhite,
                                fontFamily = CrankrDisplayFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Header Lagu Populer
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                            text = "POPULER",
                            color = TextWhite,
                            fontFamily = CrankrDisplayFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Menampilkan daftar lagu riil dan menyembunyikan skeleton
                items(topTracks.take(5), key = { it.id }) { track ->
                    TrackItem(
                        track = track,
                        isPlaying = isPlaying && currentTrack?.id == track.id,
                        isCurrent = currentTrack?.id == track.id,
                        isFavorite = favoriteIds.contains(track.id),
                        downloadProgress = downloadProgressMap[track.id],
                        trackNumber = topTracks.indexOf(track) + 1,
                        subtitleOverride = if (track.album.isNotBlank() && track.album != "Single") track.album else "Single",
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
            } else if (isTopTracksLoading) {
                // Tampilkan skeleton hanya saat loading dan data riil masih kosong
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                            text = "POPULER",
                            color = TextWhite,
                            fontFamily = CrankrDisplayFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                items(3) {
                    TrackSkeletonItem()
                }
            }

            // 3. Seksi Diskografi (Pemeriksaan Data Riil)
            item {
                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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
                        text = "DISKOGRAFI",
                        color = TextWhite,
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${diskografiList.size})",
                        color = TextMuted,
                        fontFamily = CrankrDisplayFont,
                        fontSize = 13.sp
                    )

                    // Spinner merah mati seketika jika diskografiList sudah memiliki data ATAU isDiskografiLoading == false
                    val showSpinner = isDiskografiLoading && diskografiList.isEmpty()
                    if (showSpinner) {
                        Spacer(modifier = Modifier.width(10.dp))
                        CircularProgressIndicator(
                            color = CrimsonRed,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Render LazyRow seketika jika data riil diskografiList.isNotEmpty()
            item {
                if (diskografiList.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 180.dp)
                            .padding(top = 8.dp, bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(diskografiList, key = { it.id }) { album ->
                            MinimalistAlbumCard(
                                item = album,
                                onClick = { onAlbumClick(album) }
                            )
                        }
                    }
                } else if (!isDiskografiLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .background(CardBackground, RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada album studio atau single resmi yang ditemukan.",
                            color = TextMuted,
                            fontFamily = CrankrBodyFont,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder halus untuk daftar lagu saat proses loading awal.
 */
@Composable
private fun TrackSkeletonItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CardBackground)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardBackground)
                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardBackground)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardBackground)
            )
        }
    }
}

/**
 * Kartu Rilis Album / Single Minimalis untuk Diskografi.
 * Menggunakan ukuran eksplisit Modifier.width(140.dp).wrapContentHeight().
 */
@Composable
fun MinimalistAlbumCard(
    item: AlbumRelease,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .wrapContentHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .background(CardBackground)
        ) {
            if (item.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ElevatedBackground)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            color = TextWhite,
            fontFamily = CrankrBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "${item.releaseYear} • ${item.releaseType}",
            color = TextSecondary,
            fontFamily = CrankrBodyFont,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Alias Composable AlbumCard dan DiscographyItem untuk fleksibilitas pemanggilan.
 */
@Composable
fun AlbumCard(
    item: AlbumRelease,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = MinimalistAlbumCard(item = item, onClick = onClick, modifier = modifier)

@Composable
fun DiscographyItem(
    item: AlbumRelease,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = MinimalistAlbumCard(item = item, onClick = onClick, modifier = modifier)
