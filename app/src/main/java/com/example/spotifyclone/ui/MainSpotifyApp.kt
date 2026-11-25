package com.example.spotifyclone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.ui.components.AddToPlaylistDialog
import com.example.spotifyclone.ui.components.BandcampPurchaseDialog
import com.example.spotifyclone.ui.components.CrankrMotion
import com.example.spotifyclone.ui.components.CrankrStatsDialog
import com.example.spotifyclone.ui.components.CrossfadeDialog
import com.example.spotifyclone.ui.components.EqualizerDialog
import com.example.spotifyclone.ui.components.ListenTogetherDialog
import com.example.spotifyclone.ui.components.MiniPlayer
import com.example.spotifyclone.ui.components.NowPlayingScreen
import com.example.spotifyclone.ui.components.PlaylistImportDialog
import com.example.spotifyclone.ui.components.PlaylistImportSummaryDialog
import com.example.spotifyclone.ui.components.PunkShareDialog
import com.example.spotifyclone.service.update.CrankrUpdateManager
import com.example.spotifyclone.service.update.UpdateState
import com.example.spotifyclone.ui.components.CrankrUpdateDialog
import com.example.spotifyclone.ui.components.QueueBottomSheet
import com.example.spotifyclone.ui.components.SettingsDialog
import com.example.spotifyclone.ui.components.ShareCardMode
import com.example.spotifyclone.ui.components.SleepTimerDialog
import com.example.spotifyclone.ui.screens.AboutScreen
import com.example.spotifyclone.ui.screens.AlbumDetailScreen
import com.example.spotifyclone.ui.screens.ArtistProfileScreen
import com.example.spotifyclone.ui.screens.CrankrRecapScreen
import com.example.spotifyclone.ui.screens.HomeScreen
import com.example.spotifyclone.ui.screens.LibraryScreen
import com.example.spotifyclone.ui.screens.OfflineScreen
import com.example.spotifyclone.ui.screens.PlaylistDetailScreen
import com.example.spotifyclone.ui.screens.SearchScreen
import com.example.spotifyclone.ui.viewmodel.HomeViewModel
import com.example.spotifyclone.ui.viewmodel.PlayerViewModel
import com.example.spotifyclone.ui.viewmodel.PopularTracksUiState
import com.example.spotifyclone.ui.viewmodel.RecentlyPlayedUiState
import com.example.spotifyclone.ui.viewmodel.TrendingSongsUiState

enum class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("HOME", Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH("SEARCH", Icons.Filled.Search, Icons.Outlined.Search),
    LIBRARY("LIBRARY", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    DOWNLOADS("DOWNLOADS", Icons.Filled.DownloadDone, Icons.Outlined.DownloadDone)
}

@Composable
fun MainSpotifyApp(
    viewModel: PlayerViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    val tabs = remember { NavigationTab.values().toList() }
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    // Sinkronisasi tab aktif hanya saat pager telah benar-benar menetap (mencegah macet di tengah jalan saat melompati halaman)
    LaunchedEffect(pagerState.settledPage) {
        currentTab = tabs[pagerState.settledPage]
    }

    // State dari HomeViewModel (Aliran Data Terpisah: Trending Spotify API vs Riwayat Putar Lokal vs Trending For You)
    val popularTracksState by homeViewModel.popularTracksState.collectAsState()
    val popularTracks by homeViewModel.popularTracks.collectAsState()
    val homeRecentlyPlayedState by homeViewModel.recentlyPlayedState.collectAsState()
    val homeRecentlyPlayedTracks by homeViewModel.recentlyPlayedTracks.collectAsState()
    val trendingSongsState by homeViewModel.trendingSongsState.collectAsState()
    val trendingSongs by homeViewModel.trendingSongs.collectAsState()

    val allTracks by viewModel.allTracks.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPosition.collectAsState()
    val durationMs by viewModel.duration.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val isRepeat by viewModel.isRepeat.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()
    val playbackQueueTitle by viewModel.playbackQueueTitle.collectAsState()
    val searchQuickResults by viewModel.searchQuickResults.collectAsState()
    val isSearchingQuick by viewModel.isSearchingQuick.collectAsState()
    val quickSearchQuery by viewModel.quickSearchQuery.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val downloadProgressMap by viewModel.downloadProgressMap.collectAsState()
    val isOfflineOnlyMode by viewModel.isOfflineOnlyMode.collectAsState()
    val storageUsage by viewModel.storageUsage.collectAsState()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsState()

    // Spotify / Custom / YouTube Music Playlists
    val spotifyPlaylists by viewModel.spotifyPlaylists.collectAsState()
    val isSyncingSpotify by viewModel.isSyncingSpotify.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val selectedPlaylistTracks by viewModel.selectedPlaylistTracks.collectAsState()
    val syncingPlaylistIds by viewModel.syncingPlaylistIds.collectAsState()

    // Extended Features States
    val currentLyrics by viewModel.currentLyrics.collectAsState()
    val currentLyricsData by viewModel.currentLyricsData.collectAsState()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()
    val sleepTimerRemainingMinutes by viewModel.sleepTimerRemainingMinutes.collectAsState()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsState()
    val sleepTimerMode by viewModel.sleepTimerMode.collectAsState()
    val currentEqPreset by viewModel.currentEqPreset.collectAsState()
    val listeningStats by viewModel.listeningStats.collectAsState()
    val crossfadeDurationSeconds by viewModel.crossfadeDurationSeconds.collectAsState()
    val trackToAddToPlaylist by viewModel.trackToAddToPlaylist.collectAsState()
    val recentlyPlayedTracks by viewModel.recentlyPlayedTracks.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val exploreSearchQuery by viewModel.exploreSearchQuery.collectAsState()
    val exploreSearchResults by viewModel.exploreSearchResults.collectAsState()
    val exploreMatchedArtist by viewModel.exploreMatchedArtist.collectAsState()
    val exploreSearchError by viewModel.exploreSearchError.collectAsState()
    val isSearchingExplore by viewModel.isSearchingExplore.collectAsState()
    val bandcampPurchaseTrack by viewModel.bandcampPurchaseTrack.collectAsState()
    val playlistRecommendations by viewModel.playlistRecommendations.collectAsState()
    val isLoadingRecommendations by viewModel.isLoadingRecommendations.collectAsState()
    val dominantGenre by viewModel.dominantGenre.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val storyBackgroundStyle by viewModel.storyBackgroundStyle.collectAsState()
    val selectedArtistName by viewModel.selectedArtistName.collectAsState()
    val selectedArtistId by viewModel.selectedArtistId.collectAsState()
    val artistOfficialPhoto by viewModel.artistOfficialPhoto.collectAsState()
    val artistDiscography by viewModel.artistDiscography.collectAsState()
    val artistAlbums by viewModel.artistAlbums.collectAsState()
    val artistSinglesAndEPs by viewModel.artistSinglesAndEPs.collectAsState()
    val selectedAlbumRelease by viewModel.selectedAlbumRelease.collectAsState()
    val albumDetailTracks by viewModel.albumDetailTracks.collectAsState()
    val isLoadingAlbumDetail by viewModel.isLoadingAlbumDetail.collectAsState()
    val artistTracks by viewModel.artistTracks.collectAsState()
    val similarArtists by viewModel.similarArtists.collectAsState()
    val isLoadingArtist by viewModel.isLoadingArtist.collectAsState()
    val isLoadingDiscography by viewModel.isLoadingDiscography.collectAsState()
    val bassGain by viewModel.bassGain.collectAsState()
    val trebleGain by viewModel.trebleGain.collectAsState()

    // Dialogs state
    var isSleepTimerDialogOpen by remember { mutableStateOf(false) }
    var isEqualizerDialogOpen by remember { mutableStateOf(false) }
    var isQueueSheetOpen by remember { mutableStateOf(false) }
    var isStatsDialogOpen by remember { mutableStateOf(false) }
    var isRecapOpen by remember { mutableStateOf(false) }
    var isCrossfadeDialogOpen by remember { mutableStateOf(false) }
    var trackToShare by remember { mutableStateOf<Track?>(null) }
    var isListenTogetherDialogOpen by remember { mutableStateOf(false) }
    var isSettingsDialogOpen by remember { mutableStateOf(false) }
    var isAboutDeveloperOpen by remember { mutableStateOf(false) }

    // OTA Auto-Update Manager
    val updateManager = remember { CrankrUpdateManager.getInstance(context) }
    val updateState by updateManager.updateState.collectAsState()

    LaunchedEffect(Unit) {
        updateManager.checkForUpdates(isManualCheck = false)
    }

    // Listen Together State
    val listenTogetherRoomState by viewModel.listenTogetherRoomState.collectAsState()
    val listenTogetherPendingSuggestions by viewModel.listenTogetherPendingSuggestions.collectAsState()
    val listenTogetherAutoApproveSuggestions by viewModel.listenTogetherAutoApproveSuggestions.collectAsState()
    val playlistImportProgress by viewModel.playlistImportProgress.collectAsState()
    val importSummary by viewModel.importSummary.collectAsState()

    val downloadedTracks = remember(allTracks) {
        allTracks.filter { it.isDownloaded }
    }

    val progress = (currentPositionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)

    val sleepTimerBadge = remember(sleepTimerRemainingSeconds, sleepTimerRemainingMinutes, sleepTimerMode) {
        when {
            sleepTimerMode == "END_OF_TRACK" -> "AKHIR LAGU"
            sleepTimerRemainingSeconds != null -> {
                val m = sleepTimerRemainingSeconds!! / 60
                val s = sleepTimerRemainingSeconds!! % 60
                "%d:%02d".format(m, s)
            }
            sleepTimerRemainingMinutes != null -> "${sleepTimerRemainingMinutes}m"
            else -> null
        }
    }

    // Android System Back Navigation Handler
    val isAnyOverlayOpen = playlistImportProgress != null ||
            importSummary != null ||
            (updateState !is UpdateState.Idle && updateState !is UpdateState.Checking) ||
            isSleepTimerDialogOpen ||
            isEqualizerDialogOpen ||
            isQueueSheetOpen ||
            isStatsDialogOpen ||
            isRecapOpen ||
            isCrossfadeDialogOpen ||
            isSettingsDialogOpen ||
            isAboutDeveloperOpen ||
            isListenTogetherDialogOpen ||
            trackToAddToPlaylist != null ||
            trackToShare != null ||
            isNowPlayingExpanded

    val isAlbumDetailOpen = selectedAlbumRelease != null
    val isArtistProfileOpen = selectedArtistName != null
    val isSearchActive = quickSearchQuery.isNotEmpty() || searchQuickResults.isNotEmpty() || exploreSearchQuery.isNotEmpty() || exploreSearchResults.isNotEmpty()
    val isPlaylistDetailOpen = selectedPlaylist != null
    val isNotOnHomeTab = pagerState.currentPage != 0

    // Only intercept back button if the user is in a sub-page, dialog, search shelf, or other tab.
    // If the user is on the main Home screen with nothing open, back button exits/minimizes the app.
    val shouldHandleBack = isAnyOverlayOpen || isAlbumDetailOpen || isArtistProfileOpen || isPlaylistDetailOpen || isSearchActive || isNotOnHomeTab

    BackHandler(enabled = shouldHandleBack) {
        when {
            playlistImportProgress != null -> { /* Dialog impor sedang aktif, tidak dapat ditutup */ }
            updateState !is UpdateState.Idle && updateState !is UpdateState.Checking -> {
                if (updateState !is UpdateState.Downloading) {
                    updateManager.dismissUpdate()
                }
            }
            isRecapOpen -> isRecapOpen = false
            trackToShare != null -> trackToShare = null
            isListenTogetherDialogOpen -> isListenTogetherDialogOpen = false
            isAboutDeveloperOpen -> isAboutDeveloperOpen = false
            isSettingsDialogOpen -> isSettingsDialogOpen = false
            isSleepTimerDialogOpen -> isSleepTimerDialogOpen = false
            isEqualizerDialogOpen -> isEqualizerDialogOpen = false
            isStatsDialogOpen -> isStatsDialogOpen = false
            isCrossfadeDialogOpen -> isCrossfadeDialogOpen = false
            trackToAddToPlaylist != null -> viewModel.closeAddToPlaylistDialog()
            isQueueSheetOpen -> isQueueSheetOpen = false
            isNowPlayingExpanded -> viewModel.setNowPlayingExpanded(false)
            selectedAlbumRelease != null -> viewModel.closeAlbumDetail()
            selectedArtistName != null -> viewModel.closeArtistProfile()
            selectedPlaylist != null -> viewModel.closePlaylistDetail()
            quickSearchQuery.isNotEmpty() || searchQuickResults.isNotEmpty() -> viewModel.clearQuickSearch()
            exploreSearchQuery.isNotEmpty() || exploreSearchResults.isNotEmpty() -> viewModel.clearExploreSearch()
            pagerState.currentPage != 0 -> {
                coroutineScope.launch {
                    val distance = pagerState.currentPage
                    if (distance <= 1) {
                        pagerState.animateScrollToPage(
                            0,
                            animationSpec = spring(dampingRatio = 0.92f, stiffness = 300f)
                        )
                    } else {
                        pagerState.scrollToPage(0)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Audio Engine WebView: Completely touch-transparent, placed in background behind Scaffold
        AndroidView(
            factory = { ctx ->
                object : WebView(ctx) {
                    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false
                    override fun onTouchEvent(event: MotionEvent?): Boolean = false
                    override fun onGenericMotionEvent(event: MotionEvent?): Boolean = false
                    override fun onTrackballEvent(event: MotionEvent?): Boolean = false
                    override fun onCheckIsTextEditor(): Boolean = false
                    override fun onWindowVisibilityChanged(visibility: Int) {
                        // CRITICAL: Always report VISIBLE so Chromium doesn't pause audio when screen is locked or turned off
                        super.onWindowVisibilityChanged(android.view.View.VISIBLE)
                    }
                }.apply {
                    layoutParams = ViewGroup.LayoutParams(2, 2)
                    isClickable = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setOnTouchListener { _, _ -> false }
                    viewModel.youTubeAudioEngine.attachWebView(this)
                }
            },
            modifier = Modifier
                .size(2.dp)
                .alpha(0.01f)
        )

        Scaffold(
            containerColor = PitchBlack,
            bottomBar = {
                Column(
                    modifier = Modifier
                        .background(PitchBlack)
                        .navigationBarsPadding()
                ) {
                    // Floating Mini Player with sharp 3.dp corners
                    if (currentTrack != null) {
                        MiniPlayer(
                            track = currentTrack!!,
                            isPlaying = isPlaying,
                            progress = progress,
                            isFavorite = favoriteTrackIds.contains(currentTrack!!.id),
                            onPlayPauseClick = { viewModel.togglePlayPause() },
                            onFavoriteClick = { viewModel.toggleFavorite(currentTrack!!.id, currentTrack) },
                            onClick = { viewModel.setNowPlayingExpanded(true) },
                            onNextClick = { viewModel.playNext() },
                            onPreviousClick = { viewModel.playPrevious() },
                            sleepTimerBadge = sleepTimerBadge
                        )
                    }

                    // Ref 2 + Ref 3: Compact Centered Frosted Glass Floating Pill Navigation Dock
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ElevatedBackground.copy(alpha = 0.88f),
                        border = BorderStroke(0.8.dp, com.example.spotifyclone.ui.components.glassHairlineBrush()),
                        shadowElevation = 14.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 42.dp, vertical = 8.dp)
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            val bottomTabs = remember {
                                listOf(NavigationTab.HOME, NavigationTab.SEARCH, NavigationTab.LIBRARY)
                            }
                            bottomTabs.forEach { tab ->
                                val isSelected = pagerState.targetPage == tab.ordinal
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.08f else 1.0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "NavIconScale_${tab.name}"
                                )

                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        if (selectedAlbumRelease != null) {
                                            viewModel.closeAlbumDetail()
                                        }
                                        if (selectedArtistName != null) {
                                            viewModel.closeArtistProfile()
                                        }
                                        if (selectedPlaylist != null) {
                                            viewModel.closePlaylistDetail()
                                        }
                                        if (tab.ordinal != pagerState.currentPage) {
                                            coroutineScope.launch {
                                                val distance = kotlin.math.abs(tab.ordinal - pagerState.currentPage)
                                                if (distance <= 1) {
                                                    pagerState.animateScrollToPage(
                                                        page = tab.ordinal,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.92f,
                                                            stiffness = 300f
                                                        )
                                                    )
                                                } else {
                                                    pagerState.scrollToPage(tab.ordinal)
                                                }
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .scale(iconScale)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            fontFamily = CrankrDisplayFont,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            letterSpacing = 0.8.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Primary,
                                        selectedTextColor = Primary,
                                        unselectedIconColor = Tertiary,
                                        unselectedTextColor = Tertiary,
                                        indicatorColor = Primary.copy(alpha = 0.16f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = selectedPlaylist,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Up,
                                animationSpec = spring(
                                    dampingRatio = 0.85f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + scaleIn(
                                initialScale = 0.90f,
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(260))) togetherWith
                                (scaleOut(
                                    targetScale = 0.95f,
                                    animationSpec = tween(200)
                                ) + fadeOut(animationSpec = tween(200)))
                        } else {
                            (scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(220)
                            ) + fadeIn(animationSpec = tween(220))) togetherWith
                                (slideOutOfContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                                ) + scaleOut(
                                    targetScale = 0.90f,
                                    animationSpec = tween(240)
                                ) + fadeOut(animationSpec = tween(200)))
                        }
                    },
                    label = "PlaylistDetailTransition"
                ) { currentSelectedPlaylist ->
                    if (currentSelectedPlaylist != null) {
                        // Show Playlist Detail View
                        PlaylistDetailScreen(
                            playlist = currentSelectedPlaylist,
                            tracks = selectedPlaylistTracks,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            favoriteIds = favoriteTrackIds,
                            downloadProgressMap = downloadProgressMap,
                            onBackClick = { viewModel.closePlaylistDetail() },
                            onTrackClick = { track ->
                                val idx = selectedPlaylistTracks.indexOfFirst { it.id == track.id }
                                if (idx >= 0) {
                                    viewModel.playPlaylist(selectedPlaylistTracks, idx, currentSelectedPlaylist.name)
                                } else {
                                    viewModel.playTrack(track, selectedPlaylistTracks, currentSelectedPlaylist.name)
                                }
                            },
                            onTrackClickWithQueue = { sortedList, clickedIndex ->
                                viewModel.playPlaylist(sortedList, clickedIndex, currentSelectedPlaylist.name)
                            },
                            onPreloadTopTracks = { topTracks ->
                                viewModel.preFetchTopTracks(topTracks, limit = 3)
                            },
                            onDownloadClick = { viewModel.downloadTrack(it) },
                            onDownloadAllClick = { viewModel.downloadAllPlaylistTracks(it) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                            onPlayAllClick = { tracks ->
                                if (tracks.isNotEmpty()) {
                                    viewModel.playPlaylist(tracks, 0, currentSelectedPlaylist.name)
                                }
                            },
                            onShufflePlayClick = { tracks ->
                                if (tracks.isNotEmpty()) {
                                    viewModel.shufflePlayPlaylist(tracks, currentSelectedPlaylist.name)
                                }
                            },
                            onEditPlaylist = { name, desc, sourceUrl ->
                                viewModel.editPlaylist(currentSelectedPlaylist.id, name, desc, newSourceUrl = sourceUrl)
                            },
                            onDeletePlaylist = {
                                viewModel.deletePlaylist(currentSelectedPlaylist.id)
                            },
                            onRemoveTrack = { track ->
                                if (currentSelectedPlaylist.id == "liked_songs") {
                                    viewModel.toggleFavorite(track.id, track)
                                } else {
                                    viewModel.removeTrackFromPlaylist(currentSelectedPlaylist.id, track.id)
                                }
                            },
                            onUpdateCoverUrl = { url ->
                                viewModel.updatePlaylistCover(currentSelectedPlaylist.id, url)
                            },
                            onPickCoverUri = { uri ->
                                viewModel.savePlaylistCoverFromUri(currentSelectedPlaylist.id, uri)
                            },
                            isSyncing = syncingPlaylistIds.contains(currentSelectedPlaylist.id),
                            onSyncPlaylist = {
                                viewModel.syncPlaylist(currentSelectedPlaylist.id)
                            },
                            recommendedTracks = playlistRecommendations,
                            isLoadingRecommendations = isLoadingRecommendations,
                            dominantGenre = dominantGenre,
                            onRefreshRecommendations = { viewModel.refreshPlaylistRecommendations() },
                            onAddRecommendedTrack = { track ->
                                viewModel.addRecommendedTrackToPlaylist(currentSelectedPlaylist.id, track)
                            },
                            onPlayNextInQueue = { viewModel.playNextInQueue(it) },
                            onAddToQueue = { viewModel.addToEndOfQueue(it) },
                            onShareTrack = { trackToShare = it },
                            onPurgeUnplayableTracks = { viewModel.purgeUnplayableTracksFromPlaylist(currentSelectedPlaylist.id) }
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 2,
                            userScrollEnabled = selectedPlaylist == null && selectedAlbumRelease == null && selectedArtistName == null
                        ) { page ->
                            // Subtle parallax & depth micro-animation between tab screens during swipe
                            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            val absOffset = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                            val tabScale = 1f - (absOffset * 0.04f)
                            val tabAlpha = 1f - (absOffset * 0.22f)

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = tabScale
                                        scaleY = tabScale
                                        alpha = tabAlpha
                                    }
                            ) {
                                when (tabs[page]) {
                                NavigationTab.HOME -> {
                                    HomeScreen(
                                        tracks = popularTracks.ifEmpty { allTracks },
                                        recentlyPlayedTracks = homeRecentlyPlayedTracks.ifEmpty { recentlyPlayedTracks },
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        favoriteIds = favoriteTrackIds,
                                        downloadProgressMap = downloadProgressMap,
                                        selectedGenre = selectedGenre,
                                        spotifyPlaylists = spotifyPlaylists,
                                        isSyncingSpotify = isSyncingSpotify,
                                        onImportUrl = { viewModel.importPlaylistFromUrl(it) },
                                        onPlaylistClick = { viewModel.selectPlaylist(it) },
                                        onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                                        onAddToPlaylistClick = { viewModel.openAddToPlaylistDialog(it) },
                                        onGetBackupJson = { viewModel.exportPlaylistsJson() },
                                        onRestoreJson = { viewModel.restorePlaylistsFromJson(it) },
                                        onGenreSelect = { viewModel.setSelectedGenre(it) },
                                        onTrackClick = { clickedTrack ->
                                            val basePool = if (selectedGenre == "All") (popularTracks.ifEmpty { allTracks }) else allTracks
                                            val safePool = if (basePool.any { it.id == clickedTrack.id }) {
                                                basePool
                                            } else {
                                                listOf(clickedTrack) + basePool
                                            }
                                            viewModel.playTrack(clickedTrack, safePool, "Lagu Populer")
                                            homeViewModel.refreshRecentlyPlayed()
                                        },
                                        onRecentTrackClick = { clickedTrack, recentList ->
                                            val safeRecent = if (recentList.any { it.id == clickedTrack.id }) {
                                                recentList
                                            } else {
                                                listOf(clickedTrack) + recentList
                                            }
                                            viewModel.playTrack(clickedTrack, safeRecent, "Baru Saja Diputar")
                                            homeViewModel.refreshRecentlyPlayed()
                                        },
                                        onGenreTrackClick = { clickedTrack, genreList, genreName ->
                                            val safeGenre = if (genreList.any { it.id == clickedTrack.id }) {
                                                genreList
                                            } else {
                                                listOf(clickedTrack) + genreList
                                            }
                                            viewModel.playTrack(clickedTrack, safeGenre, genreName)
                                            homeViewModel.refreshRecentlyPlayed()
                                        },
                                        onSearchTrackClick = { viewModel.playTrack(it, searchQuickResults, "Hasil Pencarian") },
                                        onDownloadClick = { viewModel.downloadTrack(it) },
                                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                                        onPlayNextInQueue = { viewModel.playNextInQueue(it) },
                                        onAddToQueue = { viewModel.addToEndOfQueue(it) },
                                        quickSearchQuery = quickSearchQuery,
                                        searchQuickResults = searchQuickResults,
                                        isSearchingQuick = isSearchingQuick,
                                        onQuickSearch = { viewModel.searchQuickSongs(it) },
                                        onClearQuickSearch = { viewModel.clearQuickSearch() },
                                        likedSongsPlaylist = viewModel.getLikedSongsPlaylist(),
                                        onOpenStats = {
                                            viewModel.refreshListeningStats()
                                            isStatsDialogOpen = true
                                        },
                                        onRecapClick = { isRecapOpen = true },
                                        onOpenSettings = { isSettingsDialogOpen = true },
                                        onShareTrack = { trackToShare = it },
                                        popularTracksState = popularTracksState,
                                        recentlyPlayedState = homeRecentlyPlayedState,
                                        onRefreshPopularTracks = { homeViewModel.refreshPopularTracks() },
                                        trendingSongs = trendingSongs,
                                        trendingSongsState = trendingSongsState,
                                        onTrendingTrackClick = { track, trendingList ->
                                            viewModel.playTrack(track, trendingList, "Lagu Tren Untuk Anda")
                                            homeViewModel.refreshRecentlyPlayed()
                                        },
                                        onSearchBarClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(
                                                    page = NavigationTab.SEARCH.ordinal,
                                                    animationSpec = spring(
                                                        dampingRatio = 0.92f,
                                                        stiffness = 300f
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }

                                NavigationTab.SEARCH -> {
                                    SearchScreen(
                                        searchQuery = exploreSearchQuery,
                                        searchResults = exploreSearchResults,
                                        isSearching = isSearchingExplore,
                                        searchHistory = searchHistory,
                                        trendingTracks = allTracks,
                                        recentlyPlayedTracks = recentlyPlayedTracks,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        favoriteIds = favoriteTrackIds,
                                        downloadProgressMap = downloadProgressMap,
                                        onQueryChange = { viewModel.searchExploreSongs(it) },
                                        onSearch = { viewModel.submitSearch(it) },
                                        onClearQuery = { viewModel.clearExploreSearch() },
                                        onRemoveHistoryItem = { viewModel.removeSearchHistoryItem(it) },
                                        onClearHistory = { viewModel.clearSearchHistory() },
                                        onTrackClick = { track, list -> viewModel.onSearchTrackClicked(track, list) },
                                        onDownloadClick = { viewModel.downloadTrack(it) },
                                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                                        onAddToPlaylistClick = { viewModel.openAddToPlaylistDialog(it) },
                                        onPlayNextInQueue = { viewModel.playNextInQueue(it) },
                                        onAddToQueue = { viewModel.addToEndOfQueue(it) },
                                        onShareTrack = { trackToShare = it },
                                        matchedArtist = exploreMatchedArtist,
                                        searchError = exploreSearchError,
                                        onRetrySearch = { viewModel.retryExploreSearch() },
                                        onArtistClick = { name, explicitId ->
                                            viewModel.selectArtist(name, explicitId)
                                        },
                                        onOpenStats = {
                                            viewModel.refreshListeningStats()
                                            isStatsDialogOpen = true
                                        },
                                        onRecapClick = { isRecapOpen = true },
                                        onOpenSettings = { isSettingsDialogOpen = true },
                                        onSourceFilterChange = { viewModel.setExploreSourceFilter(it) }
                                    )
                                }

                                NavigationTab.LIBRARY -> {
                                    LibraryScreen(
                                        spotifyPlaylists = spotifyPlaylists,
                                        likedSongsPlaylist = viewModel.getLikedSongsPlaylist(),
                                        recentlyPlayedTracks = recentlyPlayedTracks,
                                        onPlaylistClick = { viewModel.selectPlaylist(it) },
                                        onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                                        onTrackClick = { viewModel.playTrack(it, allTracks, "Library") },
                                        onGetBackupJson = { viewModel.exportPlaylistsJson() },
                                        onRestoreJson = { viewModel.restorePlaylistsFromJson(it) },
                                        onRecapClick = { isRecapOpen = true }
                                    )
                                }

                                NavigationTab.DOWNLOADS -> {
                                    OfflineScreen(
                                        downloadedTracks = downloadedTracks,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        isOfflineOnlyMode = isOfflineOnlyMode,
                                        storageUsage = storageUsage,
                                        onOfflineModeToggle = { viewModel.setOfflineOnlyMode(it) },
                                        onTrackClick = { viewModel.playTrack(it, downloadedTracks, "Lagu Unduhan") },
                                        onDeleteDownloadClick = { viewModel.deleteDownloadedTrack(it) },
                                        onPlayAllOfflineClick = {
                                            if (downloadedTracks.isNotEmpty()) {
                                                viewModel.playTrack(downloadedTracks.first(), downloadedTracks, "Lagu Unduhan")
                                            }
                                        },
                                        onDeleteAllDownloadsClick = { viewModel.deleteAllDownloads() }
                                    )
                                }
                            }
                            }
                        }
                    }
                }

                // Artist Profile Screen Overlay
                AnimatedVisibility(
                    visible = selectedArtistName != null,
                    enter = slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(260)),
                    exit = slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (selectedArtistName != null) {
                        ArtistProfileScreen(
                            artistName = selectedArtistName!!,
                            artistId = selectedArtistId,
                            officialPhotoUrl = artistOfficialPhoto,
                            discography = artistDiscography,
                            albums = artistAlbums,
                            singlesAndEPs = artistSinglesAndEPs,
                            tracks = artistTracks,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            favoriteIds = favoriteTrackIds,
                            downloadProgressMap = downloadProgressMap,
                            isLoading = isLoadingArtist,
                            isLoadingDiscography = isLoadingDiscography,
                            onBackClick = { viewModel.closeArtistProfile() },
                            onTrackClick = { track ->
                                viewModel.playTrack(track, artistTracks, "Profil Artis: $selectedArtistName")
                            },
                            onAlbumClick = { album ->
                                viewModel.openAlbumDetail(album)
                            },
                            onPlayAllClick = { tracks ->
                                if (tracks.isNotEmpty()) {
                                    viewModel.playTrack(tracks.first(), tracks, "Profil Artis: $selectedArtistName")
                                }
                            },
                            onShufflePlayClick = { tracks ->
                                if (tracks.isNotEmpty()) {
                                    viewModel.shufflePlayPlaylist(tracks, "Profil Artis: $selectedArtistName")
                                }
                            },
                            onDownloadClick = { viewModel.downloadTrack(it) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                            onAddToPlaylistClick = { viewModel.openAddToPlaylistDialog(it) },
                            onPlayNextInQueue = { viewModel.playNextInQueue(it) },
                            onAddToQueue = { viewModel.addToEndOfQueue(it) },
                            onShareTrack = { trackToShare = it },
                            similarArtists = similarArtists,
                            onSimilarArtistClick = { artist ->
                                viewModel.selectArtist(artist.name, artist.id.takeIf { it.startsWith("UC") })
                            },
                            onRetryClick = { selectedArtistName?.let { viewModel.selectArtist(it, selectedArtistId) } }
                        )
                    }
                }

                // Album Details Screen Overlay
                AnimatedVisibility(
                    visible = selectedAlbumRelease != null,
                    enter = slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(240)),
                    exit = slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (selectedAlbumRelease != null) {
                        AlbumDetailScreen(
                            album = selectedAlbumRelease!!,
                            tracks = albumDetailTracks,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            favoriteIds = favoriteTrackIds,
                            downloadProgressMap = downloadProgressMap,
                            isLoading = isLoadingAlbumDetail,
                            onBackClick = { viewModel.closeAlbumDetail() },
                            onTrackClick = { track ->
                                viewModel.playTrack(track, albumDetailTracks, "Album: ${selectedAlbumRelease?.title}")
                            },
                            onPlayAllClick = { tracks ->
                                if (tracks.isNotEmpty()) {
                                    viewModel.playTrack(tracks.first(), tracks, "Album: ${selectedAlbumRelease?.title}")
                                }
                            },
                            onShufflePlayClick = { tracks ->
                                if (tracks.isNotEmpty()) {
                                    viewModel.shufflePlayPlaylist(tracks, "Album: ${selectedAlbumRelease?.title}")
                                }
                            },
                            onDownloadClick = { viewModel.downloadTrack(it) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                            onAddToPlaylistClick = { viewModel.openAddToPlaylistDialog(it) },
                            onPlayNextInQueue = { viewModel.playNextInQueue(it) },
                            onAddToQueue = { viewModel.addToEndOfQueue(it) },
                            onShareTrack = { trackToShare = it }
                        )
                    }
                }
            }
        }

        // Fullscreen Now Playing with iOS/Apple Music Drawer Curve (emil-design-eng + micro-interactions)
        AnimatedVisibility(
            visible = isNowPlayingExpanded && currentTrack != null,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(
                    durationMillis = 320,
                    easing = CrankrMotion.EaseDrawer
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 220,
                    easing = CrankrMotion.EaseOutStrong
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(
                    durationMillis = 210,
                    easing = CrankrMotion.EaseDrawer
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 150,
                    easing = CrankrMotion.EaseOutStrong
                )
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            if (currentTrack != null) {
                val nextTrackInQueue = remember(playbackQueue, currentTrack?.id) {
                    val idx = playbackQueue.indexOfFirst { it.id == currentTrack?.id }
                    if (idx >= 0 && idx + 1 < playbackQueue.size) playbackQueue[idx + 1]
                    else playbackQueue.firstOrNull { it.id != currentTrack?.id }
                }
                NowPlayingScreen(
                    track = currentTrack!!,
                    nextTrack = nextTrackInQueue,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    isFavorite = favoriteTrackIds.contains(currentTrack!!.id),
                    downloadProgress = downloadProgressMap[currentTrack!!.id],
                    lyrics = currentLyrics,
                    lyricsData = currentLyricsData,
                    isLoadingLyrics = isLoadingLyrics,
                    sleepTimerRemainingMinutes = sleepTimerRemainingMinutes,
                    sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                    sleepTimerMode = sleepTimerMode,
                    currentEqPreset = currentEqPreset,
                    crossfadeDurationSeconds = crossfadeDurationSeconds,
                    playbackSpeed = playbackSpeed,
                    storyBackgroundStyle = storyBackgroundStyle,
                    onStoryBackgroundStyleChange = { viewModel.setStoryBackgroundStyle(it) },
                    onCyclePlaybackSpeed = { viewModel.cyclePlaybackSpeed() },
                    onOpenSleepTimer = { isSleepTimerDialogOpen = true },
                    onOpenEqualizer = { isEqualizerDialogOpen = true },
                    onOpenCrossfade = { isCrossfadeDialogOpen = true },
                    onOpenQueue = { isQueueSheetOpen = true },
                    onOpenListenTogether = { isListenTogetherDialogOpen = true },
                    onAddToPlaylist = { currentTrack?.let { viewModel.openAddToPlaylistDialog(it) } },
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onNextClick = { viewModel.playNext() },
                    onPreviousClick = { viewModel.playPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onShuffleToggle = { viewModel.toggleShuffle() },
                    onRepeatToggle = { viewModel.toggleRepeat() },
                    onFavoriteToggle = { viewModel.toggleFavorite(currentTrack!!.id, currentTrack) },
                    onDownloadClick = { viewModel.downloadTrack(currentTrack!!) },
                    onArtistClick = { artistName, artistId ->
                        viewModel.selectArtist(artistName, artistId)
                        viewModel.setNowPlayingExpanded(false)
                    },
                    onDismiss = { viewModel.setNowPlayingExpanded(false) }
                )
            }
        }

        // Sleep Timer Dialog
        if (isSleepTimerDialogOpen) {
            SleepTimerDialog(
                currentMinutesRemaining = sleepTimerRemainingMinutes,
                currentSecondsRemaining = sleepTimerRemainingSeconds,
                currentMode = sleepTimerMode,
                onSetTimer = { minutes -> minutes?.let { viewModel.setSleepTimer(it) } },
                onSetEndOfTrack = { viewModel.setSleepTimerEndOfTrack() },
                onCancelTimer = { viewModel.cancelSleepTimer() },
                onDismiss = { isSleepTimerDialogOpen = false }
            )
        }

        // Audio Equalizer Dialog
        if (isEqualizerDialogOpen) {
            EqualizerDialog(
                selectedPreset = currentEqPreset,
                onSelectPreset = { preset -> viewModel.setEqualizerPreset(preset) },
                bassGain = bassGain,
                onBassGainChanged = { viewModel.setBassGain(it) },
                trebleGain = trebleGain,
                onTrebleGainChanged = { viewModel.setTrebleGain(it) },
                onDismiss = { isEqualizerDialogOpen = false }
            )
        }

        // Add to Playlist Dialog
        if (trackToAddToPlaylist != null) {
            AddToPlaylistDialog(
                track = trackToAddToPlaylist!!,
                playlists = spotifyPlaylists,
                onPlaylistSelect = { pl ->
                    viewModel.addTrackToPlaylist(pl.id, trackToAddToPlaylist!!)
                    viewModel.closeAddToPlaylistDialog()
                },
                onCreateNewPlaylist = {
                    val newPl = viewModel.createPlaylist("Playlist Baru")
                    viewModel.addTrackToPlaylist(newPl.id, trackToAddToPlaylist!!)
                    viewModel.closeAddToPlaylistDialog()
                },
                onDismiss = { viewModel.closeAddToPlaylistDialog() }
            )
        }

        // Bandcamp Purchase Dialog for Purchase-Only / Locked Tracks
        bandcampPurchaseTrack?.let { lockedTrack ->
            BandcampPurchaseDialog(
                track = lockedTrack,
                onDismiss = { viewModel.dismissBandcampPurchaseDialog() },
                onBuyClick = {
                    val rawUrl = if (!lockedTrack.bandcampUrl.isNullOrBlank()) {
                        lockedTrack.bandcampUrl
                    } else {
                        "https://bandcamp.com/search?q=" + java.net.URLEncoder.encode("${lockedTrack.artist} ${lockedTrack.title}", "UTF-8")
                    }
                    val url = com.example.spotifyclone.data.repository.BandcampRepository.sanitizeBandcampUrl(rawUrl)
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val fallbackSearchUrl = "https://bandcamp.com/search?q=" + java.net.URLEncoder.encode("${lockedTrack.artist} ${lockedTrack.title}", "UTF-8")
                            val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fallbackSearchUrl))
                            fallbackIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(fallbackIntent)
                        } catch (e2: Exception) {
                            android.widget.Toast.makeText(context, "Tidak dapat membuka browser", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    viewModel.dismissBandcampPurchaseDialog()
                },
                onSearchAlternative = {
                    viewModel.dismissBandcampPurchaseDialog()
                    viewModel.searchExploreSongs("${lockedTrack.title} ${lockedTrack.artist}")
                }
            )
        }

        // Now Playing Queue Bottom Sheet
        if (isQueueSheetOpen) {
            QueueBottomSheet(
                currentTrack = currentTrack,
                playbackQueue = playbackQueue,
                queueTitle = playbackQueueTitle,
                isPlaying = isPlaying,
                onTrackClick = { viewModel.playTrackInQueue(it) },
                onRemoveTrack = { viewModel.removeTrackFromQueue(it) },
                onClearQueue = { viewModel.clearQueue() },
                onReorderTracks = { fromId, toId -> viewModel.reorderQueueTracks(fromId, toId) },
                onReorderQueue = { from, to -> viewModel.reorderQueue(from, to) },
                onMoveTrackToTop = { viewModel.moveQueueTrackToTop(it) },
                onMoveTrackUp = { viewModel.moveQueueTrackUp(it) },
                onMoveTrackDown = { viewModel.moveQueueTrackDown(it) },
                onDismiss = { isQueueSheetOpen = false }
            )
        }

        // Crankr Stats & Persona Dialog (Wrapped)
        if (isStatsDialogOpen) {
            CrankrStatsDialog(
                stats = listeningStats,
                onDismiss = { isStatsDialogOpen = false }
            )
        }

        // Crossfade & Gapless Settings Dialog
        if (isCrossfadeDialogOpen) {
            CrossfadeDialog(
                currentDurationSeconds = crossfadeDurationSeconds,
                onSelectDuration = { viewModel.setCrossfadeDuration(it) },
                onDismiss = { isCrossfadeDialogOpen = false }
            )
        }

        // Unified Settings Dialog (Auralys & Crankr)
        if (isSettingsDialogOpen) {
            SettingsDialog(
                crossfadeDurationSeconds = crossfadeDurationSeconds,
                onSelectCrossfade = { viewModel.setCrossfadeDuration(it) },
                onOpenEqualizer = { isEqualizerDialogOpen = true },
                onOpenListenTogether = { isListenTogetherDialogOpen = true },
                onOpenDownloads = {
                    coroutineScope.launch {
                        pagerState.scrollToPage(NavigationTab.DOWNLOADS.ordinal)
                    }
                },
                onOpenAboutDeveloper = { isAboutDeveloperOpen = true },
                onCheckUpdate = {
                    updateManager.checkForUpdates(isManualCheck = true)
                },
                onPreampChange = { viewModel.setPreampGain(it) },
                onDismiss = { isSettingsDialogOpen = false }
            )
        }

        // Dialog Pembaruan Aplikasi (OTA Auto-Update)
        CrankrUpdateDialog(
            updateState = updateState,
            updateManager = updateManager,
            onDismiss = { updateManager.dismissUpdate() }
        )

        // About Developer Screen dengan transisi horizontal slide & fade halus
        AnimatedVisibility(
            visible = isAboutDeveloperOpen,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(240)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            AboutScreen(
                onDismiss = { isAboutDeveloperOpen = false }
            )
        }

        // Crankr Noise Tape Recap Full Screen Story dengan slide halus ke atas
        AnimatedVisibility(
            visible = isRecapOpen,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(260)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            CrankrRecapScreen(
                onClose = { isRecapOpen = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Quick Share Story Dialog for any track
        if (trackToShare != null) {
            PunkShareDialog(
                track = trackToShare!!,
                lyricsData = null,
                currentPositionMs = 0L,
                initialMode = ShareCardMode.SONG,
                initialBackgroundStyle = storyBackgroundStyle,
                onBackgroundStyleChange = { viewModel.setStoryBackgroundStyle(it) },
                onDismiss = { trackToShare = null }
            )
        }

        // Listen Together Dialog (Real-time Room Sync)
        if (isListenTogetherDialogOpen) {
            ListenTogetherDialog(
                roomState = listenTogetherRoomState,
                currentTrack = currentTrack,
                pendingSuggestions = listenTogetherPendingSuggestions,
                autoApproveSuggestions = listenTogetherAutoApproveSuggestions,
                onCreateRoom = { hostName -> viewModel.createListenTogetherRoom(hostName) },
                onJoinRoom = { roomId, guestName -> viewModel.joinListenTogetherRoom(roomId, guestName) },
                onLeaveRoom = { viewModel.leaveListenTogetherRoom() },
                onToggleAutoApprove = { viewModel.setListenTogetherAutoApproveSuggestions(it) },
                onSuggestCurrentTrack = { currentTrack?.let { viewModel.suggestListenTogetherTrack(it) } },
                onApproveSuggestion = { viewModel.approveListenTogetherSuggestion(it) },
                onRejectSuggestion = { viewModel.rejectListenTogetherSuggestion(it) },
                onDismiss = { isListenTogetherDialogOpen = false }
            )
        }

        // Dialog Loading Impor Playlist Bertema Hitam dan Merah
        playlistImportProgress?.let { progress ->
            PlaylistImportDialog(progress = progress)
        }

        // Dialog Ringkasan Hasil Impor: Menampilkan lagu tidak ditemukan & opsi pembersihan
        importSummary?.let { summary ->
            PlaylistImportSummaryDialog(
                summary = summary,
                onFilterUnmatched = {
                    viewModel.confirmImportFilterUnmatched(summary.playlist.id)
                },
                onKeepAll = {
                    viewModel.dismissImportSummary()
                },
                onDismiss = {
                    viewModel.dismissImportSummary()
                }
            )
        }
    }
}
