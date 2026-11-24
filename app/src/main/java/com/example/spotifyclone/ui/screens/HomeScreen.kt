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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifyclone.R
import com.example.spotifyclone.data.MusicCatalog
import com.example.spotifyclone.data.model.SpotifyPlaylist
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.ui.components.BackupRestoreDialog
import com.example.spotifyclone.ui.components.PlaylistCard
import com.example.spotifyclone.ui.components.TrackItem
import com.example.spotifyclone.ui.components.springPressable
import com.example.spotifyclone.ui.viewmodel.PopularTracksUiState
import com.example.spotifyclone.ui.viewmodel.RecentlyPlayedUiState
import com.example.spotifyclone.ui.viewmodel.TrendingSongsUiState

@Composable
fun HomeScreen(
    tracks: List<Track>,
    recentlyPlayedTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    downloadProgressMap: Map<String, Float>,
    selectedGenre: String,
    spotifyPlaylists: List<SpotifyPlaylist>,
    isSyncingSpotify: Boolean,
    onImportUrl: (String) -> Unit,
    onPlaylistClick: (SpotifyPlaylist) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit,
    onGetBackupJson: suspend () -> String,
    onRestoreJson: (String) -> Unit,
    // Track actions
    onGenreSelect: (String) -> Unit,
    onTrackClick: (Track) -> Unit,
    onSearchTrackClick: ((Track) -> Unit)? = null,
    onDownloadClick: (Track) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onPlayNextInQueue: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    onShareTrack: ((Track) -> Unit)? = null,
    // Quick search on home
    quickSearchQuery: String = "",
    searchQuickResults: List<Track> = emptyList(),
    isSearchingQuick: Boolean = false,
    onQuickSearch: (String) -> Unit = {},
    onClearQuickSearch: () -> Unit = {},
    likedSongsPlaylist: SpotifyPlaylist? = null,
    onOpenStats: () -> Unit = {},
    onRecapClick: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    // Dedicated separate streams for Popular Tracks, Recently Played, and Trending Songs
    popularTracksState: PopularTracksUiState? = null,
    recentlyPlayedState: RecentlyPlayedUiState? = null,
    onRefreshPopularTracks: (() -> Unit)? = null,
    // Trending Songs For You (Lagu Tren Untuk Anda)
    trendingSongs: List<Track> = emptyList(),
    trendingSongsState: TrendingSongsUiState? = null,
    onTrendingTrackClick: ((Track, List<Track>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var isImportExpanded by remember { mutableStateOf(false) }
    var importUrlInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val punchyRed = Primary

    // Pisahkan Aliran Data:
    // 1. Recently Played murni dari Room DB / RecentlyPlayedState
    val displayRecentlyPlayed = when (recentlyPlayedState) {
        is RecentlyPlayedUiState.Success -> recentlyPlayedState.tracks
        is RecentlyPlayedUiState.Empty -> emptyList()
        else -> recentlyPlayedTracks
    }

    // 2. Popular Tracks murni dari Spotify API / PopularTracksState
    val displayPopularTracks = when (popularTracksState) {
        is PopularTracksUiState.Success -> popularTracksState.tracks
        is PopularTracksUiState.Error -> popularTracksState.tracks.ifEmpty { tracks }
        else -> tracks
    }

    // 3. Trending Songs For You dari YouTube Music Explore/Charts + Preferensi Lokal
    val displayTrendingSongs = when (trendingSongsState) {
        is TrendingSongsUiState.Success -> trendingSongsState.tracks
        is TrendingSongsUiState.Error -> trendingSongsState.tracks.ifEmpty { trendingSongs }
        else -> trendingSongs
    }

    val filteredTracks = if (selectedGenre == "All") {
        displayPopularTracks
    } else {
        displayPopularTracks.filter { it.genre.equals(selectedGenre, ignoreCase = true) }
    }

    LazyColumn(
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
            ),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Kinetic Mono Editorial Masthead
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Editorial Brand & Subtitle
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.crankrlogo),
                            contentDescription = "CRANKR Logo",
                            modifier = Modifier.height(30.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CRANKR",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                letterSpacing = 1.6.sp
                            )
                            Text(
                                text = "Dengar Sekarang",
                                fontFamily = CrankrBodyFont,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Minimalist Monochrome Action Pills
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Surface,
                            border = BorderStroke(1.dp, Border),
                            modifier = Modifier
                                .springPressable(0.95f)
                                .clickable { onRecapClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "RECAP",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextWhite,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Surface,
                            border = BorderStroke(1.dp, Border),
                            modifier = Modifier
                                .springPressable(0.95f)
                                .clickable { onOpenStats() }
                        ) {
                            Text(
                                text = "STATS",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = Surface,
                            border = BorderStroke(1.dp, Border),
                            modifier = Modifier
                                .size(32.dp)
                                .springPressable(0.95f)
                                .clickable { onOpenSettings() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Kinetic Mono Import Playlist Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isImportExpanded = !isImportExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Impor Playlist Spotify / Link",
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextWhite
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isImportExpanded) Primary else ElevatedBackground,
                                border = BorderStroke(1.dp, if (isImportExpanded) Primary else Border),
                                modifier = Modifier.springPressable(0.96f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isImportExpanded) Icons.Default.Close else Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (isImportExpanded) OnPrimary else TextWhite,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isImportExpanded) "Tutup" else "Buka",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isImportExpanded) OnPrimary else TextWhite
                                    )
                                }
                            }
                        }

                        // Expandable Clean Import Field
                        AnimatedVisibility(
                            visible = isImportExpanded || isSyncingSpotify,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(BorderSubtle)
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = importUrlInput,
                                    onValueChange = { importUrlInput = it },
                                    placeholder = {
                                        Text(
                                            text = "Tempel link playlist anda...",
                                            fontFamily = CrankrBodyFont,
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = null,
                                            tint = if (importUrlInput.isNotEmpty()) punchyRed else TextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (importUrlInput.isNotEmpty()) {
                                            IconButton(
                                                onClick = { importUrlInput = "" },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Hapus",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(3.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = PitchBlack,
                                        unfocusedContainerColor = PitchBlack,
                                        focusedBorderColor = punchyRed,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        cursorColor = punchyRed
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipText = clipboardManager.getText()?.text
                                            if (!clipText.isNullOrBlank()) {
                                                importUrlInput = clipText.trim()
                                            }
                                        },
                                        shape = RoundedCornerShape(3.dp),
                                        border = BorderStroke(1.dp, BorderSubtle),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = PitchBlack,
                                            contentColor = TextWhite
                                        ),
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Tempel",
                                            fontFamily = CrankrBodyFont,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextWhite
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (importUrlInput.isNotBlank()) {
                                                onImportUrl(importUrlInput.trim())
                                                importUrlInput = ""
                                                isImportExpanded = false
                                            }
                                        },
                                        enabled = importUrlInput.isNotBlank() && !isSyncingSpotify,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Primary,
                                            disabledContainerColor = Primary.copy(alpha = 0.3f),
                                            contentColor = Neutral,
                                            disabledContentColor = Neutral.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        if (isSyncingSpotify) {
                                            CircularProgressIndicator(
                                                color = Neutral,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "MEMPROSES...",
                                                fontFamily = CrankrDisplayFont,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Neutral
                                            )
                                        } else {
                                            Text(
                                                text = "IMPOR PLAYLIST",
                                                fontFamily = CrankrDisplayFont,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = OnPrimary,
                                                letterSpacing = 0.8.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Playlists Row Section with Sharp Corners
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PLAYLIST SAYA",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 0.5.sp
                )

                // Quick Shuffle ("Putar Acak" ala Auralys) + Create Playlist Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val randomPool = remember(displayTrendingSongs, displayPopularTracks, displayRecentlyPlayed) {
                        (displayTrendingSongs + displayPopularTracks + displayRecentlyPlayed).distinctBy { it.id }
                    }
                    if (randomPool.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Primary,
                            modifier = Modifier
                                .springPressable(0.95f)
                                .clickable {
                                    randomPool.randomOrNull()?.let { picked ->
                                        onTrackClick(picked)
                                    }
                                }
                        ) {
                            Text(
                                text = "ACAK",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimary,
                                letterSpacing = 0.6.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .springPressable(0.95f)
                            .clickable { showCreateDialog = true }
                    ) {
                        Text(
                            text = "+ PLAYLIST",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (likedSongsPlaylist != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            modifier = Modifier
                                .width(148.dp)
                                .springPressable(0.982f)
                                .clickable { onPlaylistClick(likedSongsPlaylist) }
                        ) {
                            Column(modifier = Modifier.padding(9.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(130.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ElevatedBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Lagu yang Disukai",
                                        tint = Primary,
                                        modifier = Modifier.size(48.dp)
                                    )

                                    // Badge Total Lagu
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(PitchBlack.copy(alpha = 0.85f))
                                            .border(0.5.dp, BorderSubtle, RoundedCornerShape(2.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${likedSongsPlaylist.trackCount} LAGU",
                                            fontFamily = CrankrBodyFont,
                                            color = Primary,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "LAGU DISUKAI",
                                    fontFamily = CrankrBodyFont,
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${likedSongsPlaylist.trackCount} lagu",
                                    fontFamily = CrankrBodyFont,
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                items(spotifyPlaylists.filter { it.id != "liked_songs" }, key = { it.id }) { playlist ->
                    PlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        if (displayRecentlyPlayed.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "BARU SAJA DIPUTAR",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(CrimsonRed)
                        )
                    }

                    Text(
                        text = "${displayRecentlyPlayed.size} lagu",
                        fontFamily = CrankrBodyFont,
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(displayRecentlyPlayed, key = { index, track -> "${track.id}_$index" }) { _, track ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            modifier = Modifier
                                .width(122.dp)
                                .springPressable(0.982f)
                                .clickable { onTrackClick(track) }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(106.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ElevatedBackground)
                                ) {
                                    AsyncImage(
                                        model = track.coverUrl,
                                        contentDescription = track.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = track.title,
                                    fontFamily = CrankrBodyFont,
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    fontFamily = CrankrBodyFont,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Seksi TREN UNDERGROUND (14dp Rounded Card)
        if (displayTrendingSongs.isNotEmpty() || trendingSongsState is TrendingSongsUiState.Loading) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "TREN UNDERGROUND",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(CrimsonRed)
                        )
                    }

                    if (displayTrendingSongs.isNotEmpty()) {
                        Text(
                            text = "${displayTrendingSongs.size} lagu",
                            fontFamily = CrankrBodyFont,
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                if (trendingSongsState is TrendingSongsUiState.Loading && displayTrendingSongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CrimsonRed,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(displayTrendingSongs, key = { index, track -> "${track.id}_$index" }) { _, track ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                modifier = Modifier
                                    .width(122.dp)
                                    .springPressable(0.982f)
                                    .clickable {
                                        if (onTrendingTrackClick != null) {
                                            onTrendingTrackClick(track, displayTrendingSongs)
                                        } else {
                                            onTrackClick(track)
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(106.dp)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ElevatedBackground)
                                    ) {
                                        AsyncImage(
                                            model = track.coverUrl,
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = track.title,
                                        fontFamily = CrankrBodyFont,
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        fontFamily = CrankrBodyFont,
                                        color = Color(0xFFCCCCCC),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Genre Filter Chips with Modern Rounded Pill Radius (SIGNAL TYPE)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "SIGNAL TYPE",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MusicCatalog.genres.forEach { genre ->
                        val isSelected = selectedGenre == genre
                        Surface(
                            onClick = { onGenreSelect(genre) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) Primary else CardSurface,
                            border = BorderStroke(1.dp, if (isSelected) Primary else Border),
                            modifier = Modifier.springPressable(0.96f)
                        ) {
                            Text(
                                text = genre,
                                fontFamily = CrankrBodyFont,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = 0.2.sp,
                                color = if (isSelected) Neutral else TextWhite,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Tracks Section Header (REKOMENDASI TERATAS)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedGenre == "All") "REKOMENDASI TERATAS" else "${selectedGenre.uppercase()} TRACKS",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 0.5.sp
                )

                if (selectedGenre == "All") {
                    Surface(
                        color = CrimsonRedSoft,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(CrimsonRed, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CHARTS • YOUTUBE MUSIC",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        // State Feedback: Loading state for official YouTube Music Charts Tracks
        if (selectedGenre == "All" && popularTracksState is PopularTracksUiState.Loading && filteredTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = CrimsonRed,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // State Feedback: Error state with retry action
        if (selectedGenre == "All" && popularTracksState is PopularTracksUiState.Error && filteredTracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TIDAK DAPAT MEMUAT TANGGA LAGU POPULER",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = popularTracksState.message,
                        fontFamily = CrankrBodyFont,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    if (onRefreshPopularTracks != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onRefreshPopularTracks,
                            border = BorderStroke(1.dp, CrimsonRed),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "COBA LAGI",
                                fontFamily = CrankrDisplayFont,
                                color = TextWhite,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Track items list optimized for smooth 60/120 FPS scrolling
        itemsIndexed(filteredTracks, key = { index, track -> "${track.id}_$index" }) { _, track ->
            TrackItem(
                track = track,
                isPlaying = isPlaying,
                isCurrent = currentTrack?.id == track.id,
                isFavorite = favoriteIds.contains(track.id),
                downloadProgress = downloadProgressMap[track.id],
                onClick = { onTrackClick(track) },
                onDownloadClick = { onDownloadClick(track) },
                onFavoriteClick = { onFavoriteClick(track.id) },
                onAddToPlaylistClick = { onAddToPlaylistClick(track) },
                onPlayNextInQueue = { onPlayNextInQueue?.invoke(track) },
                onAddToQueue = { onAddToQueue?.invoke(track) },
                onShareTrackClick = if (onShareTrack != null) { { onShareTrack(track) } } else null,
                modifier = Modifier.animateItem()
            )
        }
    }

    // Dialog: Create New Playlist
    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        var newDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            shape = RoundedCornerShape(4.dp),
            containerColor = DarkCharcoal,
            titleContentColor = TextWhite,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "BUAT PLAYLIST BARU",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 0.5.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nama Playlist", fontFamily = CrankrBodyFont, fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBackground,
                            unfocusedContainerColor = CardBackground,
                            focusedBorderColor = CrimsonRed,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )

                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Deskripsi (Opsional)", fontFamily = CrankrBodyFont, fontSize = 12.sp) },
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBackground,
                            unfocusedContainerColor = CardBackground,
                            focusedBorderColor = CrimsonRed,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreatePlaylist(newName.trim(), newDesc.trim())
                            showCreateDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                ) {
                    Text(text = "SIMPAN", fontFamily = CrankrDisplayFont, fontWeight = FontWeight.Bold, color = OnPrimary)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCreateDialog = false },
                    shape = RoundedCornerShape(3.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BorderSubtle)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text(text = "BATAL", fontFamily = CrankrDisplayFont, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Dialog: Backup & Restore
    if (showBackupRestoreDialog) {
        BackupRestoreDialog(
            playlistsCount = spotifyPlaylists.size,
            onGetBackupJson = onGetBackupJson,
            onRestoreJson = onRestoreJson,
            onDismiss = { showBackupRestoreDialog = false }
        )
    }
}
