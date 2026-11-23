package com.example.spotifyclone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.spotifyclone.R
import coil.compose.AsyncImage
import com.example.spotifyclone.data.model.SpotifyPlaylist
import android.net.Uri
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.theme.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import com.example.spotifyclone.ui.components.ChangeCoverDialog
import com.example.spotifyclone.ui.components.MarqueeText
import com.example.spotifyclone.ui.components.TrackItem
import com.example.spotifyclone.ui.components.springPressable

enum class PlaylistSortOption(val displayName: String) {
    CUSTOM("Custom order"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    RECENTLY_ADDED("Recently added")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: SpotifyPlaylist,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    downloadProgressMap: Map<String, Float>,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackClickWithQueue: ((List<Track>, Int) -> Unit)? = null,
    onDownloadClick: (Track) -> Unit,
    onDownloadAllClick: (List<Track>) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onPlayAllClick: (List<Track>) -> Unit,
    onShufflePlayClick: ((List<Track>) -> Unit)? = null,
    onEditPlaylist: (String, String, String?) -> Unit,
    onDeletePlaylist: () -> Unit,
    onRemoveTrack: ((Track) -> Unit)? = null,
    onUpdateCoverUrl: ((String?) -> Unit)? = null,
    onPickCoverUri: ((Uri) -> Unit)? = null,
    isSyncing: Boolean = false,
    onSyncPlaylist: (() -> Unit)? = null,
    recommendedTracks: List<Track> = emptyList(),
    isLoadingRecommendations: Boolean = false,
    dominantGenre: String = "",
    onRefreshRecommendations: () -> Unit = {},
    onAddRecommendedTrack: (Track) -> Unit = {},
    onPlayNextInQueue: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    onShareTrack: ((Track) -> Unit)? = null,
    onPreloadTopTracks: ((List<Track>) -> Unit)? = null,
    onPurgeUnplayableTracks: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isLikedSongsPlaylist = playlist.id == "liked_songs"
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var selectedSortOption by remember { mutableStateOf(PlaylistSortOption.RECENTLY_ADDED) }
    var isSortAscending by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showChangeCoverDialog by remember { mutableStateOf(false) }
    var trackToRemove by remember { mutableStateOf<Track?>(null) }
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }
    val unplayableTracks = remember(tracks) { tracks.filterNot { it.isPlayable } }

    BackHandler(enabled = true) {
        when {
            showEditDialog -> showEditDialog = false
            showDeleteDialog -> showDeleteDialog = false
            showPurgeConfirmDialog -> showPurgeConfirmDialog = false
            showSortBottomSheet -> showSortBottomSheet = false
            showChangeCoverDialog -> showChangeCoverDialog = false
            trackToRemove != null -> trackToRemove = null
            isSearchExpanded -> {
                isSearchExpanded = false
                searchQuery = ""
            }
            else -> onBackClick()
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    val allSortedTracks = remember(tracks, selectedSortOption, isSortAscending) {
        when (selectedSortOption) {
            PlaylistSortOption.CUSTOM -> {
                if (isSortAscending) tracks else tracks.reversed()
            }
            PlaylistSortOption.TITLE -> {
                if (isSortAscending) {
                    tracks.sortedBy { it.title.lowercase() }
                } else {
                    tracks.sortedByDescending { it.title.lowercase() }
                }
            }
            PlaylistSortOption.ARTIST -> {
                if (isSortAscending) {
                    tracks.sortedBy { it.artist.lowercase() }
                } else {
                    tracks.sortedByDescending { it.artist.lowercase() }
                }
            }
            PlaylistSortOption.ALBUM -> {
                if (isSortAscending) {
                    tracks.sortedBy { it.album.lowercase() }
                } else {
                    tracks.sortedByDescending { it.album.lowercase() }
                }
            }
            PlaylistSortOption.RECENTLY_ADDED -> {
                val timestamps = tracks.map { it.addedAt }
                val hasDistinctAddedAt = timestamps.distinct().size > 1
                if (hasDistinctAddedAt) {
                    if (isSortAscending) {
                        tracks.sortedBy { it.addedAt }
                    } else {
                        tracks.sortedByDescending { it.addedAt }
                    }
                } else {
                    // Fallback jika semua timestamp sama:
                    // isSortAscending == true (Terlama) -> membalik urutan list
                    // isSortAscending == false (Terbaru) -> urutan normal list
                    if (isSortAscending) tracks.reversed() else tracks
                }
            }
        }
    }

    val sortedTracks = remember(allSortedTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            allSortedTracks
        } else {
            val q = searchQuery.trim().lowercase()
            allSortedTracks.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
    }

    // Preload stream audio untuk 3 lagu teratas di playlist secara asinkron di latar belakang (0ms delay saat ditekan)
    LaunchedEffect(sortedTracks) {
        if (sortedTracks.isNotEmpty()) {
            onPreloadTopTracks?.invoke(sortedTracks.take(3))
        }
    }

    val listState = rememberLazyListState()
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 420f).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PitchBlack)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Spacing for top status bar and pinned glassmorphic app bar
            item {
                Spacer(
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(58.dp)
                )
            }

            // Top Header Hero Card with Parallax scaling and smooth fade
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = 1f - (scrollOffset * 0.72f)
                            scaleX = 1f - (scrollOffset * 0.12f)
                            scaleY = 1f - (scrollOffset * 0.12f)
                        }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    CrimsonRed.copy(alpha = 0.22f),
                                    DarkCharcoal.copy(alpha = 0.5f),
                                    PitchBlack
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Center Info: Artwork with 14dp rounded corners
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Border, RoundedCornerShape(14.dp))
                            .background(ElevatedBackground)
                            .clickable { showChangeCoverDialog = true }
                    ) {
                        if (!playlist.imageUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = playlist.imageUrl,
                                contentDescription = playlist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .aspectRatio(1f)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = CrimsonRed,
                                modifier = Modifier
                                    .size(54.dp)
                                    .align(Alignment.Center)
                            )
                        }

                        // Edit Cover Camera Overlay Badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(DarkCharcoal.copy(alpha = 0.85f))
                                .border(1.dp, BorderSubtle, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Ubah Cover",
                                tint = TextWhite,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = playlist.name,
                        color = TextWhite,
                        fontFamily = CrankrDisplayFont,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!playlist.description.isNullOrBlank()) {
                        Text(
                            text = playlist.description,
                            color = TextSecondary,
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "${playlist.ownerName} • ${tracks.size} lagu",
                        color = TextMuted,
                        fontFamily = CrankrBodyFont,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

        // Action Buttons Row (Play All, Shuffle, Download All & Sort)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Play All Button (Solid Bold Orange Pill) with tactile spring
                Button(
                    onClick = {
                        val toPlay = if (sortedTracks.isNotEmpty()) sortedTracks else tracks
                        onPlayAllClick(toPlay)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = Neutral
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .springPressable(scaleDown = 0.93f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play All",
                        tint = Neutral,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PLAY ALL",
                        color = Neutral,
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        letterSpacing = 0.8.sp,
                        maxLines = 1
                    )
                }

                // 2. Tombol Acak Lagu (Shuffle Play) - Persegi Tumpul Proporsional
                IconButton(
                    onClick = {
                        val toShuffle = (if (sortedTracks.isNotEmpty()) sortedTracks else tracks).shuffled()
                        if (toShuffle.isNotEmpty()) {
                            if (onShufflePlayClick != null) {
                                onShufflePlayClick(toShuffle)
                            } else {
                                onPlayAllClick(toShuffle)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CardBackground.copy(alpha = 0.85f))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(2.dp))
                        .springPressable(scaleDown = 0.92f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Putar Acak",
                        tint = CrimsonRed,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 3. Download All Button - Tombol Sekunder dengan Teks Ringkas "UNDUH"
                OutlinedButton(
                    onClick = { onDownloadAllClick(sortedTracks) },
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(BorderSubtle)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = TextWhite
                    ),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .springPressable(scaleDown = 0.93f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download All",
                        tint = TextWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "UNDUH",
                        color = TextWhite,
                        fontFamily = CrankrBodyFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.3.sp,
                        maxLines = 1
                    )
                }

                // 3b. Tombol Bersihkan Lagu Tidak Tersedia (Muncul jika ada trek yang tidak ditemukan)
                if (unplayableTracks.isNotEmpty() && onPurgeUnplayableTracks != null) {
                    OutlinedButton(
                        onClick = { showPurgeConfirmDialog = true },
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = SolidColor(CrimsonRed.copy(alpha = 0.8f))
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CrimsonRedSoft,
                            contentColor = CrimsonRed
                        ),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .springPressable(scaleDown = 0.93f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Bersihkan Lagu Tidak Tersedia",
                            tint = CrimsonRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "BERSIHKAN (${unplayableTracks.size})",
                            color = CrimsonRed,
                            fontFamily = CrankrBodyFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.3.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))

                // 4. Tombol Filter/Urutkan (Sort) - Proporsional dengan Ikon Urutkan & Arah Panah
                Surface(
                    onClick = { showSortBottomSheet = true },
                    shape = RoundedCornerShape(10.dp),
                    color = CardBackground.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selectedSortOption != PlaylistSortOption.RECENTLY_ADDED || isSortAscending) CrimsonRed.copy(alpha = 0.8f) else BorderSubtle
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .springPressable(scaleDown = 0.92f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Urutkan Playlist",
                            tint = CrimsonRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (isSortAscending) "Urutan Naik" else "Urutan Turun",
                            tint = TextWhite,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Active Search Filter Indicator (Minimalis & Elegan hanya muncul saat ada kata kunci pencarian)
        if (searchQuery.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = CrimsonRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Filter: \"$searchQuery\" (${sortedTracks.size} lagu)",
                            fontSize = 12.sp,
                            fontFamily = CrankrBodyFont,
                            color = CrimsonRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Hapus filter",
                        fontSize = 11.5.sp,
                        fontFamily = CrankrBodyFont,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { searchQuery = "" }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        item {
            HorizontalDivider(
                color = BorderSubtle.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Track items or Empty search state
        if (tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Playlist masih kosong",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (sortedTracks.isEmpty()) {
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
                        text = "Lagu \"$searchQuery\" tidak ditemukan",
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Coba kata kunci judul lagu atau nama penyanyi lain",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            itemsIndexed(sortedTracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                TrackItem(
                    track = track,
                    isPlaying = isPlaying,
                    isCurrent = currentTrack?.id == track.id,
                    isFavorite = favoriteIds.contains(track.id),
                    downloadProgress = downloadProgressMap[track.id],
                    onClick = {
                        if (onTrackClickWithQueue != null) {
                            val fullList = if (allSortedTracks.isNotEmpty()) allSortedTracks else tracks
                            val fullIndex = fullList.indexOfFirst { it.id == track.id }
                            if (fullIndex >= 0) {
                                onTrackClickWithQueue(fullList, fullIndex)
                            } else {
                                onTrackClickWithQueue(sortedTracks, index)
                            }
                        } else {
                            onTrackClick(track)
                        }
                    },
                    onDownloadClick = { onDownloadClick(track) },
                    onFavoriteClick = { onFavoriteClick(track.id) },
                    onPlayNextInQueue = { onPlayNextInQueue?.invoke(track) },
                    onAddToQueue = { onAddToQueue?.invoke(track) },
                    onRemoveFromPlaylist = if (onRemoveTrack != null) { { trackToRemove = track } } else null,
                    onShareTrackClick = if (onShareTrack != null) { { onShareTrack(track) } } else null,
                    modifier = Modifier.animateItem()
                )
            }
        }

        // --- SEKSI REKOMENDASI BERDASARKAN GENRE PLAYLIST ---
        item {
            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider(
                color = BorderSubtle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CrimsonRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REKOMENDASI LAGU",
                                fontFamily = CrankrDisplayFont,
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        if (dominantGenre.isNotBlank()) {
                            Text(
                                text = if (dominantGenre.startsWith("Berdasarkan") || dominantGenre.startsWith("Rekomendasi")) {
                                    dominantGenre
                                } else {
                                    "Berdasarkan genre \"$dominantGenre\" di playlist ini"
                                },
                                fontFamily = CrankrBodyFont,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Refresh Button
                    TextButton(
                        onClick = onRefreshRecommendations,
                        enabled = !isLoadingRecommendations
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isLoadingRecommendations) TextMuted else CrimsonRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Segarkan",
                            fontFamily = CrankrDisplayFont,
                            color = if (isLoadingRecommendations) TextMuted else CrimsonRed,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (isLoadingRecommendations) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = CrimsonRed,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Mencari rekomendasi serupa...",
                            fontFamily = CrankrBodyFont,
                            color = TextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        } else if (recommendedTracks.isNotEmpty()) {
            itemsIndexed(recommendedTracks, key = { index, recTrack -> "rec_${recTrack.id}_$index" }) { _, recTrack ->
                RecommendedTrackItem(
                    track = recTrack,
                    isCurrent = currentTrack?.id == recTrack.id,
                    onClick = { onTrackClick(recTrack) },
                    onAddClick = { onAddRecommendedTrack(recTrack) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }

    // Pinned Glassmorphic Parallax Top App Bar
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSearchExpanded) {
                    SolidColor(DarkCharcoal.copy(alpha = 0.98f))
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkCharcoal.copy(alpha = (0.94f * scrollOffset).coerceIn(0f, 1f)),
                            PitchBlack.copy(alpha = (0.90f * scrollOffset).coerceIn(0f, 1f))
                        )
                    )
                }
            )
            .then(
                if (isSearchExpanded || scrollOffset > 0.25f) {
                    Modifier.border(
                        0.5.dp,
                        BorderSubtle.copy(alpha = if (isSearchExpanded) 0.8f else (scrollOffset * 0.8f).coerceIn(0f, 1f)),
                        RoundedCornerShape(0.dp)
                    )
                } else Modifier
            )
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSearchExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tombol Tutup / Kembali dari Mode Cari
                IconButton(
                    onClick = {
                        isSearchExpanded = false
                        searchQuery = ""
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CardBackground.copy(alpha = 0.85f))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                        .springPressable(scaleDown = 0.88f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Tutup Pencarian",
                        tint = TextWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Kolom Pencarian Ekspansif di Bilah Atas
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBackground)
                        .border(
                            1.dp,
                            if (searchQuery.isNotEmpty()) CrimsonRed else BorderSubtle,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Cari di playlist ini...",
                                color = TextMuted,
                                fontSize = 13.sp,
                                fontFamily = CrankrBodyFont
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontFamily = CrankrBodyFont
                            ),
                            cursorBrush = SolidColor(CrimsonRed)
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Hapus teks",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back Button (Proporsional & Ergonomis)
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                        .springPressable(scaleDown = 0.92f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Center Title & Count (Smoothly reveals as header scrolls, mengambil ruang tengah proporsional)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .graphicsLayer {
                            alpha = scrollOffset
                            translationY = 16f * (1f - scrollOffset)
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column {
                        MarqueeText(
                            text = playlist.name,
                            color = TextWhite,
                            fontFamily = CrankrDisplayFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            active = scrollOffset > 0.6f
                        )
                        Text(
                            text = "${tracks.size} lagu",
                            color = Tertiary,
                            fontFamily = CrankrBodyFont,
                            fontSize = 11.sp
                        )
                    }
                }

                // Right Actions Row: Search, Sync, Edit, Delete (Rapi & Proporsional tanpa logo yang mendempet)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol Cari di Playlist
                    IconButton(
                        onClick = { isSearchExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface)
                            .border(
                                1.dp,
                                if (searchQuery.isNotEmpty()) Primary else Border,
                                RoundedCornerShape(10.dp)
                            )
                            .springPressable(scaleDown = 0.92f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari di Playlist",
                            tint = if (searchQuery.isNotEmpty()) Primary else TextWhite,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    if (isLikedSongsPlaylist) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Primary.copy(alpha = 0.15f))
                                .border(1.dp, Primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Lagu yang Disukai",
                                tint = Primary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    } else {
                        if (onSyncPlaylist != null) {
                            IconButton(
                                onClick = { onSyncPlaylist() },
                                enabled = !isSyncing,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface)
                                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                                    .springPressable(scaleDown = 0.92f)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Primary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync Playlist",
                                        tint = if (!playlist.sourceUrl.isNullOrEmpty() || (playlist.id.length in 15..35 && !playlist.id.startsWith("custom_"))) Primary else TextWhite,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .border(1.dp, Border, RoundedCornerShape(10.dp))
                                .springPressable(scaleDown = 0.92f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Playlist",
                                tint = TextWhite,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .border(1.dp, Border, RoundedCornerShape(10.dp))
                                .springPressable(scaleDown = 0.92f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Playlist",
                                tint = Primary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
} // closes outer Box

    // Dialog: Edit Playlist
    if (showEditDialog) {
        var editName by remember { mutableStateOf(playlist.name) }
        var editDesc by remember { mutableStateOf(playlist.description ?: "") }
        var editSourceUrl by remember { mutableStateOf(playlist.sourceUrl ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            shape = RoundedCornerShape(4.dp),
            containerColor = DarkCharcoal,
            titleContentColor = TextWhite,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "EDIT PLAYLIST",
                    fontSize = 18.sp,
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cover Preview & Change Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(ElevatedBackground)
                            .clickable { showChangeCoverDialog = true }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(DarkCharcoal),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!playlist.imageUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = playlist.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Foto Sampul",
                                color = TextWhite,
                                fontFamily = CrankrBodyFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ketuk untuk ganti logo/foto",
                                color = TextSecondary,
                                fontFamily = CrankrBodyFont,
                                fontSize = 11.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = CrimsonRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(text = "Nama Playlist", fontSize = 12.sp, fontFamily = CrankrBodyFont, color = TextSecondary)
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
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

                    Text(text = "Deskripsi (Opsional)", fontSize = 12.sp, fontFamily = CrankrBodyFont, color = TextSecondary)
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = CrimsonRed,
                            unfocusedBorderColor = BorderSubtle
                        )
                    )

                    Text(text = "Tautan Sumber Playlist untuk Sinkronisasi", fontSize = 12.sp, fontFamily = CrankrBodyFont, color = TextSecondary)
                    OutlinedTextField(
                        value = editSourceUrl,
                        onValueChange = { editSourceUrl = it },
                        placeholder = { Text("https://open.spotify.com/playlist/...", color = TextMuted, fontSize = 12.sp, fontFamily = CrankrBodyFont) },
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            onEditPlaylist(editName.trim(), editDesc.trim(), editSourceUrl.trim().ifEmpty { null })
                            showEditDialog = false
                        }
                    },
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text(text = "SIMPAN", fontFamily = CrankrDisplayFont, fontWeight = FontWeight.Bold, color = TextWhite)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showEditDialog = false },
                    shape = RoundedCornerShape(3.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(BorderSubtle)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                ) {
                    Text(text = "BATAL", fontFamily = CrankrDisplayFont, color = TextSecondary)
                }
            }
        )
    }

    // Dialog: Delete Playlist Confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(4.dp),
            containerColor = DarkCharcoal,
            titleContentColor = TextWhite,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "HAPUS PLAYLIST?",
                    fontSize = 18.sp,
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            },
            text = {
                Text(
                    text = "Yakin ingin menghapus playlist \"${playlist.name}\"? Seluruh daftar lagu di playlist ini akan dihapus.",
                    color = TextSecondary,
                    fontFamily = CrankrBodyFont,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeletePlaylist()
                    },
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text(text = "HAPUS", fontFamily = CrankrDisplayFont, fontWeight = FontWeight.Bold, color = TextWhite)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false },
                    shape = RoundedCornerShape(3.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(BorderSubtle)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                ) {
                    Text(text = "BATAL", fontFamily = CrankrDisplayFont, color = TextSecondary)
                }
            }
        )
    }

    // Dialog: Remove Track Confirmation
    if (trackToRemove != null) {
        AlertDialog(
            onDismissRequest = { trackToRemove = null },
            shape = RoundedCornerShape(4.dp),
            containerColor = DarkCharcoal,
            titleContentColor = TextWhite,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "HAPUS LAGU?",
                    fontSize = 17.sp,
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            },
            text = {
                Text(
                    text = "Hapus \"${trackToRemove?.title}\" dari playlist \"${playlist.name}\"?",
                    color = TextSecondary,
                    fontFamily = CrankrBodyFont,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        trackToRemove?.let { onRemoveTrack?.invoke(it) }
                        trackToRemove = null
                    },
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text(text = "HAPUS", fontFamily = CrankrDisplayFont, fontWeight = FontWeight.Bold, color = TextWhite)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { trackToRemove = null },
                    shape = RoundedCornerShape(3.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(BorderSubtle)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                ) {
                    Text(text = "BATAL", fontFamily = CrankrDisplayFont, color = TextSecondary)
                }
            }
        )
    }

    // Dialog: Purge Unplayable Tracks Confirmation
    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            containerColor = Surface,
            title = {
                Text(
                    text = "BERSIHKAN LAGU TIDAK TERSEDIA?",
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextWhite
                )
            },
            text = {
                Text(
                    text = "Hapus ${unplayableTracks.size} lagu yang tidak ditemukan di YouTube Music maupun Bandcamp dari playlist \"${playlist.name}\"?",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPurgeConfirmDialog = false
                        onPurgeUnplayableTracks?.invoke()
                    },
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text(text = "HAPUS SEMUA", fontFamily = CrankrDisplayFont, fontWeight = FontWeight.Bold, color = TextWhite)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showPurgeConfirmDialog = false },
                    shape = RoundedCornerShape(3.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(BorderSubtle)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                ) {
                    Text(text = "BATAL", fontFamily = CrankrDisplayFont, color = TextSecondary)
                }
            }
        )
    }

    // Dialog: Change Playlist Cover
    if (showChangeCoverDialog) {
        ChangeCoverDialog(
            currentCoverUrl = playlist.imageUrl,
            firstTrackCoverUrl = tracks.firstOrNull()?.coverUrl,
            onDismiss = { showChangeCoverDialog = false },
            onUriSelected = { uri ->
                onPickCoverUri?.invoke(uri)
                showChangeCoverDialog = false
            },
            onUrlSelected = { url ->
                onUpdateCoverUrl?.invoke(url)
                showChangeCoverDialog = false
            }
        )
    }

    // Modal Bottom Sheet: Sort by
    if (showSortBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortBottomSheet = false },
            containerColor = DarkCharcoal,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted.copy(alpha = 0.6f))
                )
            },
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "Sort by",
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextWhite,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )

                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(bottom = 4.dp))

                PlaylistSortOption.values().forEach { option ->
                    val isSelected = selectedSortOption == option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .springPressable(scaleDown = 0.96f)
                            .clickable {
                                if (isSelected) {
                                    // Jika item yang aktif diklik lagi, ubah arah panah dan balikkan urutan daftarnya secara langsung
                                    isSortAscending = !isSortAscending
                                } else {
                                    selectedSortOption = option
                                    // Default arah pengurutan saat memilih opsi baru:
                                    // Recently added -> descending (terbaru ke terlama)
                                    // Opsi lainnya -> ascending (A-Z / urutan awal)
                                    isSortAscending = (option != PlaylistSortOption.RECENTLY_ADDED)
                                }
                                showSortBottomSheet = false
                            }
                            .padding(horizontal = 24.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = option.displayName,
                            color = if (isSelected) CrimsonRed else TextWhite,
                            fontFamily = CrankrBodyFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.5.sp
                        )

                        // Tampilkan ikon panah arah sejajar di ujung kanan HANYA pada item yang sedang aktif
                        if (isSelected) {
                            Icon(
                                imageVector = if (isSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = if (isSortAscending) "Urutan Naik" else "Urutan Turun",
                                tint = CrimsonRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendedTrackItem(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isAdded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .springPressable(0.96f)
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (isCurrent) CardBackground else Color.Transparent)
            .border(
                1.dp,
                if (isCurrent) CrimsonRed.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(3.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover Art with smooth Crossfade
        Box(
            modifier = Modifier
                .size(46.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp))
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(6.dp))
        ) {
            Crossfade(
                targetState = track.coverUrl,
                animationSpec = tween(300),
                label = "RecTrackCoverCrossfade"
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

        // Title, Artist, & Genre Chip
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontFamily = CrankrDisplayFont,
                color = if (isCurrent) CrimsonRed else TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist,
                    fontFamily = CrankrBodyFont,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.genre.isNotBlank() && !track.genre.equals("Chill", ignoreCase = true)) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = CrimsonRedSoft
                    ) {
                        Text(
                            text = track.genre,
                            fontFamily = CrankrDisplayFont,
                            color = CrimsonRed,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Add to Playlist Button
        if (isAdded) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = DarkCharcoal,
                border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Ditambahkan",
                        tint = CrimsonRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Ditambahkan",
                        fontFamily = CrankrDisplayFont,
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Button(
                onClick = {
                    isAdded = true
                    onAddClick()
                },
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonRed,
                    contentColor = TextWhite
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tambah ke playlist",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Tambah",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
