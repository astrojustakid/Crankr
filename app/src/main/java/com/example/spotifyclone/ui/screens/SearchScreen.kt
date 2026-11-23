package com.example.spotifyclone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifyclone.R
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.data.repository.SearchArtistProfile
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.ui.components.TrackItem
import com.example.spotifyclone.ui.components.springPressable
import kotlinx.coroutines.delay

// Pure OLED Obsidian & Electric Crimson Palette constants
private val CrankrAmber = Primary
private val CrankrAmberBright = PrimaryBright
private val CrankrAmberDark = PrimaryDark
private val CrankrCardDark = CardSurface
private val CrankrCardBorder = BorderSubtle.copy(alpha = 0.4f)

private data class CuratedHubItem(
    val title: String,
    val subtitle: String,
    val searchKeyword: String,
    val matchKeywords: List<String>,
    val isAmber: Boolean,
    val icon: ImageVector,
    val iconBgColor: Color,
    val iconTintColor: Color,
    val gradientColors: List<Color>,
    val borderColor: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    searchResults: List<Track>,
    isSearching: Boolean,
    searchHistory: List<String>,
    trendingTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    downloadProgressMap: Map<String, Float>,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClearQuery: () -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onDownloadClick: (Track) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit,
    onPlayNextInQueue: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    onShareTrack: ((Track) -> Unit)? = null,
    matchedArtist: SearchArtistProfile? = null,
    searchError: String? = null,
    onRetrySearch: (() -> Unit)? = null,
    onArtistClick: ((String, String?) -> Unit)? = null,
    onOpenStats: (() -> Unit)? = null,
    onRecapClick: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onSourceFilterChange: ((String) -> Unit)? = null,
    recentlyPlayedTracks: List<Track> = emptyList(),
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = searchQuery.isNotBlank() || searchResults.isNotEmpty()) {
        focusManager.clearFocus()
        onClearQuery()
    }

    var selectedCategory by remember { mutableStateOf("Semua") }
    val filterChips = remember { listOf("Semua", "Lagu", "Artis", "⚡ Bandcamp", "Pop Punk", "Rock", "Indie", "Unduhan") }

    val displaySearchResults = remember(searchResults, selectedCategory, searchQuery) {
        when (selectedCategory) {
            "Lagu" -> searchResults.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }.ifEmpty { searchResults }
            "Artis" -> searchResults.filter { it.artist.contains(searchQuery.trim(), ignoreCase = true) }.ifEmpty { searchResults }
            "⚡ Bandcamp" -> searchResults.filter { it.isBandcamp }
            "Unduhan" -> searchResults.filter { it.isDownloaded }
            else -> searchResults
        }
    }

    val curatedHubs = remember {
        listOf(
            CuratedHubItem(
                title = "Distortion & Fuzz",
                subtitle = "HEAVY TONE HUB",
                searchKeyword = "Rock",
                matchKeywords = listOf("rock", "fuzz", "distortion", "guitar", "overdrive", "alternative"),
                isAmber = true,
                icon = Icons.Default.GraphicEq,
                iconBgColor = CrankrAmber,
                iconTintColor = Color.Black,
                gradientColors = listOf(Color(0xFF381C08), Color(0xFF22130A), Color(0xFF141414)),
                borderColor = Color(0xFF5A3010)
            ),
            CuratedHubItem(
                title = "Mid-90s Punk",
                subtitle = "ZINE DIY UNDERGROUND",
                searchKeyword = "Punk",
                matchKeywords = listOf("punk", "skate", "green day", "nofx", "bad religion", "ramones"),
                isAmber = false,
                icon = Icons.Default.Bolt,
                iconBgColor = Color(0xFF2E1A1A),
                iconTintColor = Color(0xFFFF5252),
                gradientColors = listOf(Color(0xFF331618), Color(0xFF201214), Color(0xFF121212)),
                borderColor = Color(0xFF4D2427)
            ),
            CuratedHubItem(
                title = "Melodic Hardcore",
                subtitle = "PURE RAW DRIVE",
                searchKeyword = "Metalcore",
                matchKeywords = listOf("hardcore", "metalcore", "metal", "breakdown", "screamo"),
                isAmber = false,
                icon = Icons.Default.Group,
                iconBgColor = Color(0xFF261F2C),
                iconTintColor = Color(0xFFBB86FC),
                gradientColors = listOf(Color(0xFF281C30), Color(0xFF1B1422), Color(0xFF121212)),
                borderColor = Color(0xFF422E50)
            ),
            CuratedHubItem(
                title = "Cassette Chill",
                subtitle = "LO-FI TAPE WARBLE",
                searchKeyword = "Lo-Fi",
                matchKeywords = listOf("chill", "lo-fi", "lofi", "tape", "acoustic", "slow", "ambient"),
                isAmber = false,
                icon = Icons.Default.Waves,
                iconBgColor = Color(0xFF1B2824),
                iconTintColor = Color(0xFF64D2B4),
                gradientColors = listOf(Color(0xFF152A24), Color(0xFF101E1A), Color(0xFF121212)),
                borderColor = Color(0xFF23443A)
            ),
            CuratedHubItem(
                title = "Post-Rock Dynamics",
                subtitle = "CRESCENDO WAVES",
                searchKeyword = "Indie Rock",
                matchKeywords = listOf("post-rock", "indie", "ambient", "shoegaze", "crescendo", "sigur"),
                isAmber = false,
                icon = Icons.Default.GridView,
                iconBgColor = Color(0xFF1A2333),
                iconTintColor = Color(0xFF64B5F6),
                gradientColors = listOf(Color(0xFF16253B), Color(0xFF111B29), Color(0xFF0F1114)),
                borderColor = Color(0xFF243B5E)
            ),
            CuratedHubItem(
                title = "Noise & Industrial",
                subtitle = "MODULAR DISTORTION",
                searchKeyword = "Metal",
                matchKeywords = listOf("industrial", "noise", "synth", "dark", "electronic", "modular"),
                isAmber = true,
                icon = Icons.Default.Tune,
                iconBgColor = Color(0xFF2E2619),
                iconTintColor = CrankrAmber,
                gradientColors = listOf(Color(0xFF332314), Color(0xFF211710), Color(0xFF121212)),
                borderColor = Color(0xFF4E3722)
            ),
            CuratedHubItem(
                title = "Pop Punk Revival",
                subtitle = "ENERGY & ANTHEMS",
                searchKeyword = "Pop Punk",
                matchKeywords = listOf("pop punk", "poppunk", "rocket", "pee wee", "blink", "sum 41"),
                isAmber = true,
                icon = Icons.Default.Bolt,
                iconBgColor = Color(0xFF332010),
                iconTintColor = CrankrAmberBright,
                gradientColors = listOf(Color(0xFF3B200E), Color(0xFF24150B), Color(0xFF121212)),
                borderColor = Color(0xFF5A3519)
            ),
            CuratedHubItem(
                title = "Alternative & Grunge",
                subtitle = "90S DISTORTION TONE",
                searchKeyword = "Grunge",
                matchKeywords = listOf("grunge", "alt", "nirvana", "soundgarden", "pearl jam", "stone"),
                isAmber = false,
                icon = Icons.Default.MusicNote,
                iconBgColor = Color(0xFF262626),
                iconTintColor = Color(0xFFDDDDDD),
                gradientColors = listOf(Color(0xFF2A2826), Color(0xFF1C1B1A), Color(0xFF121212)),
                borderColor = Color(0xFF3E3B38)
            )
        )
    }

    val favoriteTracks = remember(favoriteIds, trendingTracks) {
        trendingTracks.filter { favoriteIds.contains(it.id) }
    }
    val userPreferenceTracks = remember(favoriteTracks, recentlyPlayedTracks) {
        (favoriteTracks + recentlyPlayedTracks).distinctBy { it.id }
    }

    val (forYouHubs, exploreHubs) = remember(userPreferenceTracks, curatedHubs) {
        if (userPreferenceTracks.isEmpty()) {
            val forYou = curatedHubs.take(4)
            val explore = curatedHubs.drop(4).take(4)
            Pair(forYou, explore)
        } else {
            // Hitung skor afinitas tiap hub berdasarkan preferensi lagu yang disukai & diputar pengguna
            val scoredHubs = curatedHubs.map { hub ->
                val score = userPreferenceTracks.count { track ->
                    val g = track.genre.lowercase()
                    val artist = track.artist.lowercase()
                    val title = track.title.lowercase()
                    val album = track.album.lowercase()
                    hub.matchKeywords.any { kw ->
                        g.contains(kw) || artist.contains(kw) || title.contains(kw) || album.contains(kw)
                    }
                }
                Pair(hub, score)
            }

            val sortedByAffinity = scoredHubs.sortedByDescending { it.second }.map { it.first }
            val forYou = sortedByAffinity.take(4)
            val remaining = sortedByAffinity.drop(4)
            val explore = if (remaining.size >= 4) remaining.take(4) else curatedHubs.filterNot { forYou.contains(it) }.take(4)

            Pair(forYou, explore)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PitchBlack),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // 1. Top Header: Logo + CRANKR Brand + RECAP, STATS, SETTINGS
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Logo & Typemark
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.crankrlogo),
                        contentDescription = "CRANKR Logo",
                        modifier = Modifier.height(32.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "CRANKR",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        letterSpacing = 1.2.sp
                    )
                }

                // Action Buttons: RECAP, STATS, SETTINGS
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // RECAP Pill Button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CardSurface,
                        border = null,
                        modifier = Modifier
                            .springPressable(0.982f)
                            .clickable { onRecapClick?.invoke() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = CrankrAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RECAP",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrankrAmber,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    // STATS Pill Button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CardSurface,
                        border = null,
                        modifier = Modifier
                            .springPressable(0.982f)
                            .clickable { onOpenStats?.invoke() }
                    ) {
                        Text(
                            text = "STATS",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    // SETTINGS Icon Button
                    Surface(
                        shape = CircleShape,
                        color = CardSurface,
                        border = null,
                        modifier = Modifier
                            .size(32.dp)
                            .springPressable(0.982f)
                            .clickable { onOpenSettings?.invoke() }
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
        }

        // 2. Search Bar Capsule Input Field
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(CardSurface)
                    .border(0.5.dp, BorderSubtle.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (searchQuery.isNotEmpty()) CrankrAmber else Color(0xFF888888),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Cari judul, artis, album, atau lirik...",
                                fontFamily = CrankrBodyFont,
                                color = Color(0xFF777777),
                                fontSize = 13.5.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextWhite,
                                fontFamily = CrankrBodyFont,
                                fontSize = 13.5.sp
                            ),
                            cursorBrush = SolidColor(CrankrAmber),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    if (searchQuery.isNotBlank()) {
                                        onSearch(searchQuery)
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onClearQuery()
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    // Microphone icon indicator
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF222222),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        // Active Search Results Mode
        if (searchQuery.isNotBlank()) {
            // Category Filter Chips during active search
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterChips.forEach { chip ->
                        val isSelected = selectedCategory == chip
                        val isBandcampChip = chip == "⚡ Bandcamp"
                        Surface(
                            onClick = {
                                selectedCategory = chip
                                onSourceFilterChange?.invoke(chip)
                                if (chip in listOf("Pop Punk", "Rock", "Indie")) {
                                    onQueryChange(chip)
                                    onSearch(chip)
                                } else if (isBandcampChip) {
                                    if (searchQuery.isNotBlank()) {
                                        onSearch(searchQuery)
                                    } else {
                                        onQueryChange("punk")
                                        onSearch("punk")
                                    }
                                } else if (chip == "Semua" && searchQuery in listOf("Pop Punk", "Rock", "Indie", "punk")) {
                                    onClearQuery()
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                if (isBandcampChip) CrimsonRed else CrankrAmber
                            } else Color(0xFF1A1A1A),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) {
                                    if (isBandcampChip) CrimsonRed else CrankrAmber
                                } else Color(0xFF2A2A2A)
                            ),
                            modifier = Modifier.springPressable(scaleDown = 0.96f)
                        ) {
                            Text(
                                text = chip,
                                fontFamily = CrankrBodyFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                letterSpacing = 0.2.sp,
                                color = if (isSelected) {
                                    if (isBandcampChip) Color.White else Color.Black
                                } else TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (isSearching) {
                item {
                    SearchSkeletonLoadingView()
                }
            } else if (displaySearchResults.isNotEmpty() || matchedArtist != null) {
                val isBandcampOnly = selectedCategory == "⚡ Bandcamp"
                val shouldShowArtist = if (isBandcampOnly) {
                    matchedArtist?.isBandcamp == true
                } else {
                    matchedArtist != null
                }

                if (shouldShowArtist && matchedArtist != null) {
                    item {
                        OfficialArtistProfileCard(
                            artist = matchedArtist,
                            onClick = {
                                onArtistClick?.invoke(matchedArtist.name, matchedArtist.id)
                            }
                        )
                    }
                }

                item {
                    val headerTitle = if (shouldShowArtist && matchedArtist != null) {
                        "TREK RESMI OLEH ${matchedArtist.name.uppercase()}"
                    } else {
                        "HASIL PENCARIAN (${displaySearchResults.size})${if (selectedCategory != "Semua") " • $selectedCategory" else ""}"
                    }
                    Text(
                        text = headerTitle,
                        fontFamily = CrankrDisplayFont,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                itemsIndexed(displaySearchResults, key = { index, track -> "search_${track.id}_$index" }) { _, track ->
                    TrackItem(
                        track = track,
                        isPlaying = isPlaying,
                        isCurrent = currentTrack?.id == track.id,
                        isFavorite = favoriteIds.contains(track.id),
                        downloadProgress = downloadProgressMap[track.id],
                        onClick = { onTrackClick(track, displaySearchResults) },
                        onDownloadClick = { onDownloadClick(track) },
                        onFavoriteClick = { onFavoriteClick(track.id) },
                        onAddToPlaylistClick = { onAddToPlaylistClick(track) },
                        onPlayNextInQueue = { onPlayNextInQueue?.invoke(track) },
                        onAddToQueue = { onAddToQueue?.invoke(track) },
                        onShareTrackClick = if (onShareTrack != null) { { onShareTrack(track) } } else null,
                        modifier = Modifier.animateItem()
                    )
                }
            } else if (searchError != null && onRetrySearch != null) {
                item {
                    SearchErrorRetryView(
                        message = searchError,
                        onRetry = onRetrySearch
                    )
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (selectedCategory == "⚡ Bandcamp") "Katalog Bandcamp Tidak Ditemukan" else "Lagu Tidak Ditemukan",
                                fontFamily = CrankrDisplayFont,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedCategory == "⚡ Bandcamp") "Coba cari nama band indie, rilisan lokal, atau label underground di Bandcamp" else "Coba gunakan kata kunci artis atau judul yang lebih spesifik",
                                fontFamily = CrankrBodyFont,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            // Default Showcase View matching the reference design exactly

            // "RIWAYAT PENCARIAN TERKINI" Section (Hanya tampil jika ada riwayat pencarian pengguna)
            if (searchHistory.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color(0xFFAAAAAA),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "RIWAYAT PENCARIAN TERKINI",
                                    fontFamily = CrankrDisplayFont,
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "HAPUS SEMUA",
                                fontFamily = CrankrDisplayFont,
                                color = CrankrAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier
                                    .clickable { onClearHistory() }
                                    .padding(4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            searchHistory.forEach { itemQuery ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = CardSurface,
                                    border = null,
                                    modifier = Modifier
                                        .springPressable(0.982f)
                                        .clickable {
                                            onQueryChange(itemQuery)
                                            onSearch(itemQuery)
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = itemQuery,
                                            fontFamily = CrankrBodyFont,
                                            color = TextWhite,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hapus",
                                            tint = Color(0xFF777777),
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clickable {
                                                    onRemoveHistoryItem(itemQuery)
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. "SESUAI SELERA ANDA" (50% Rekomendasi Berdasarkan Preferensi & Lagu Disukai)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(CrankrAmber)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SESUAI SELERA ANDA",
                                fontFamily = CrankrDisplayFont,
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "UNTUK KAMU",
                            fontFamily = CrankrDisplayFont,
                            color = CrankrAmber,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2-Column Grid untuk forYouHubs
                    for (i in forYouHubs.indices step 2) {
                        val item1 = forYouHubs[i]
                        val item2 = forYouHubs.getOrNull(i + 1)
                        val covers1 = remember(item1, userPreferenceTracks, trendingTracks) {
                            getHubCovers(item1, userPreferenceTracks, trendingTracks)
                        }
                        val covers2 = if (item2 != null) {
                            remember(item2, userPreferenceTracks, trendingTracks) {
                                getHubCovers(item2, userPreferenceTracks, trendingTracks)
                            }
                        } else emptyList()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CuratedHubCard(
                                    hub = item1,
                                    covers = covers1,
                                    onClick = {
                                        onQueryChange(item1.searchKeyword)
                                        onSearch(item1.searchKeyword)
                                    }
                                )
                            }
                            if (item2 != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CuratedHubCard(
                                        hub = item2,
                                        covers = covers2,
                                        onClick = {
                                            onQueryChange(item2.searchKeyword)
                                            onSearch(item2.searchKeyword)
                                        }
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 7. "JELAJAHI HAL BARU" (50% Eksplorasi Genre & Mood Baru / Discovery)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF64B5F6))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "JELAJAHI HAL BARU",
                                fontFamily = CrankrDisplayFont,
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "DISCOVERY RADAR",
                            fontFamily = CrankrDisplayFont,
                            color = Color(0xFF64B5F6),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2-Column Grid untuk exploreHubs
                    for (i in exploreHubs.indices step 2) {
                        val item1 = exploreHubs[i]
                        val item2 = exploreHubs.getOrNull(i + 1)
                        val covers1 = remember(item1, trendingTracks) {
                            getHubCovers(item1, emptyList(), trendingTracks)
                        }
                        val covers2 = if (item2 != null) {
                            remember(item2, trendingTracks) {
                                getHubCovers(item2, emptyList(), trendingTracks)
                            }
                        } else emptyList()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CuratedHubCard(
                                    hub = item1,
                                    covers = covers1,
                                    onClick = {
                                        onQueryChange(item1.searchKeyword)
                                        onSearch(item1.searchKeyword)
                                    }
                                )
                            }
                            if (item2 != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CuratedHubCard(
                                        hub = item2,
                                        covers = covers2,
                                        onClick = {
                                            onQueryChange(item2.searchKeyword)
                                            onSearch(item2.searchKeyword)
                                        }
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniWaveformBars(
    color: Color = CrankrAmber,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val h1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(340, easing = LinearEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(510, easing = LinearEasing), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        modifier = modifier.height(13.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val bars = listOf(h1, h2, h3)
        bars.forEach { h ->
            val actualHeight = if (isActive) h else 0.4f
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(actualHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

private fun getHubCovers(
    hub: CuratedHubItem,
    preferredTracks: List<Track>,
    allTracks: List<Track>
): List<String> {
    // 1. Cek di lagu-lagu preferensi pengguna terlebih dahulu (Liked Songs & Recently Played)
    val userMatched = preferredTracks.filter { track ->
        val g = track.genre.lowercase()
        val title = track.title.lowercase()
        val artist = track.artist.lowercase()
        val album = track.album.lowercase()
        hub.matchKeywords.any { kw ->
            g.contains(kw) || title.contains(kw) || artist.contains(kw) || album.contains(kw)
        }
    }.map { it.coverUrl }.filter { it.isNotBlank() }.distinct()

    if (userMatched.isNotEmpty()) {
        if (userMatched.size >= 2) return userMatched.take(2)
        // Jika baru 1 cover, lengkapi dengan cover dari allTracks
        val fallback = allTracks.filter { track ->
            val g = track.genre.lowercase()
            val title = track.title.lowercase()
            val artist = track.artist.lowercase()
            val album = track.album.lowercase()
            hub.matchKeywords.any { kw ->
                g.contains(kw) || title.contains(kw) || artist.contains(kw) || album.contains(kw)
            }
        }.map { it.coverUrl }.filter { it.isNotBlank() && !userMatched.contains(it) }.distinct()
        return (userMatched + fallback).take(2)
    }

    // 2. Jika tidak ada di preferensi pengguna, ambil dari seluruh trek katalog
    val allMatched = allTracks.filter { track ->
        val g = track.genre.lowercase()
        val title = track.title.lowercase()
        val artist = track.artist.lowercase()
        val album = track.album.lowercase()
        hub.matchKeywords.any { kw ->
            g.contains(kw) || title.contains(kw) || artist.contains(kw) || album.contains(kw)
        }
    }.map { it.coverUrl }.filter { it.isNotBlank() }.distinct()

    if (allMatched.isNotEmpty()) {
        return allMatched.take(2)
    }

    // 3. Fallback konsisten menggunakan hash title agar setiap hub tetap memiliki cover unik
    val pool = allTracks.map { it.coverUrl }.filter { it.isNotBlank() }.distinct()
    if (pool.isNotEmpty()) {
        val hash = kotlin.math.abs(hub.title.hashCode())
        return listOf(
            pool[hash % pool.size],
            pool[(hash + 1) % pool.size]
        )
    }
    return emptyList()
}

@Composable
private fun CuratedHubCard(
    hub: CuratedHubItem,
    covers: List<String>,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF141414),
        border = BorderStroke(1.dp, hub.borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .springPressable(0.96f)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(hub.gradientColors))
        ) {
            // Sisi Kanan: Artwork Cover Miring Bergaya Spotify Browse Cards
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.55f)
                    .align(Alignment.CenterEnd)
            ) {
                // Secondary / Back cover (terlihat sebagian di belakang cover utama)
                val backCover = covers.getOrNull(1)
                if (backCover != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF222222),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(52.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 6.dp, y = (-26).dp)
                            .graphicsLayer {
                                rotationZ = -12f
                                alpha = 0.65f
                            }
                    ) {
                        AsyncImage(
                            model = backCover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Primary / Front cover (terlihat dominan dan miring di pojok kanan bawah)
                val frontCover = covers.firstOrNull()
                if (frontCover != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF262626),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(68.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 10.dp, y = 10.dp)
                            .graphicsLayer {
                                rotationZ = 18f
                            }
                    ) {
                        AsyncImage(
                            model = frontCover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // Placeholder album art jika cover belum tersedia
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF202020),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(66.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 10.dp, y = 10.dp)
                            .graphicsLayer {
                                rotationZ = 18f
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1E1E1E))
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color(0xFF555555),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // Left Scrim: Gradient hitam halus dari kiri ke kanan agar teks selalu sangat kontras dan mudah dibaca
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.8f),
                                0.55f to Color.Black.copy(alpha = 0.45f),
                                0.85f to Color.Transparent
                            )
                        )
                    )
            )

            // Konten Teks & Badge di Sisi Kiri Kartu
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Mini Genre Icon Badge
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(hub.iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = hub.icon,
                        contentDescription = null,
                        tint = hub.iconTintColor,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Bottom: Title & Subtitle
                Column(
                    modifier = Modifier.fillMaxWidth(0.68f)
                ) {
                    Text(
                        text = hub.title,
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        color = if (hub.isAmber) CrankrAmberBright else TextWhite,
                        fontSize = 13.sp,
                        maxLines = 2,
                        lineHeight = 16.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = hub.subtitle,
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF999999),
                        fontSize = 8.5.sp,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSkeletonLoadingView() {
    val transition = rememberInfiniteTransition(label = "SearchShimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SearchShimmerAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            CircularProgressIndicator(
                color = CrankrAmber,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "MEMUAT DARI KATALOG RESMI...",
                fontFamily = CrankrDisplayFont,
                color = CrankrAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBackground)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = alpha * 0.15f))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = alpha * 0.2f))
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = alpha * 0.12f))
                    )
                }
            }
        }
    }
}

@Composable
private fun OfficialArtistProfileCard(
    artist: SearchArtistProfile,
    onClick: () -> Unit
) {
    val isBc = artist.isBandcamp
    val accentColor = if (isBc) CrimsonRed else CrankrAmber

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (isBc) 0.85f else 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .springPressable(scaleDown = 0.97f)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(DarkCharcoal)
                    .border(2.dp, accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!artist.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artist.photoUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = accentColor,
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = if (isBc) "⚡ BANDCAMP" else "ARTIS RESMI",
                            fontFamily = CrankrDisplayFont,
                            color = if (isBc) Color.White else Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isBc && artist.genres.isNotEmpty()) {
                        Text(
                            text = "• ${artist.genres.first()}",
                            fontFamily = CrankrBodyFont,
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (artist.followers > 0) {
                        Text(
                            text = "• ${formatFollowersCount(artist.followers)} pendengar",
                            fontFamily = CrankrBodyFont,
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = artist.name,
                    fontFamily = CrankrDisplayFont,
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (artist.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = artist.genres.take(3).joinToString(", ") { genre ->
                            genre.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
                        },
                        fontFamily = CrankrBodyFont,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = CrankrAmber.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, CrankrAmber.copy(alpha = 0.4f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "PROFIL",
                        fontFamily = CrankrDisplayFont,
                        color = CrankrAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Lihat Profil",
                        tint = CrankrAmber,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchErrorRetryView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = CrankrAmber.copy(alpha = 0.15f),
                shape = CircleShape,
                border = BorderStroke(1.dp, CrankrAmber.copy(alpha = 0.4f)),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = CrankrAmber,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "KONEKSI BERMASALAH",
                fontFamily = CrankrDisplayFont,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message.ifBlank { "Gagal memuat katalog lagu resmi. Coba periksa koneksi internet Anda." },
                fontFamily = CrankrBodyFont,
                color = TextSecondary,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = CrankrAmber),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MUAT ULANG",
                    fontFamily = CrankrDisplayFont,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

private fun formatFollowersCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
