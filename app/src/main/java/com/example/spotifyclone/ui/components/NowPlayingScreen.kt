package com.example.spotifyclone.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.example.spotifyclone.util.ArtistParser
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.example.spotifyclone.service.CrankrAppearanceManager
import com.example.spotifyclone.service.LyricsAlignment
import com.example.spotifyclone.service.PlayerBackgroundStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import coil.compose.AsyncImage
import com.example.spotifyclone.data.model.LyricsData
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.share.StoryBackgroundStyle
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.ui.viewmodel.RepeatMode
import com.example.spotifyclone.util.DynamicColorExtractor
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    track: Track,
    nextTrack: Track? = null,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isShuffle: Boolean,
    repeatMode: RepeatMode = RepeatMode.OFF,
    isFavorite: Boolean,
    downloadProgress: Float?,
    // Extended Features
    lyrics: String? = null,
    lyricsData: LyricsData? = null,
    isLoadingLyrics: Boolean = false,
    sleepTimerRemainingMinutes: Int? = null,
    sleepTimerRemainingSeconds: Long? = null,
    sleepTimerMode: String? = null,
    currentEqPreset: String = "Flat",
    crossfadeDurationSeconds: Int = 3,
    playbackSpeed: Float = 1.0f,
    storyBackgroundStyle: StoryBackgroundStyle = StoryBackgroundStyle.SOLID_BLACK,
    onStoryBackgroundStyleChange: ((StoryBackgroundStyle) -> Unit)? = null,
    onOpenSleepTimer: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenCrossfade: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenListenTogether: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onCyclePlaybackSpeed: () -> Unit = {},
    // Core Actions
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDownloadClick: () -> Unit,
    onArtistClick: ((artistName: String, artistId: String?) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showLyricsPanel by remember { mutableStateOf(false) }
    var deckVisualMode by remember { mutableStateOf(DeckVisualMode.COVER) }
    var showShareDialog by remember { mutableStateOf(false) }
    var shareDialogMode by remember { mutableStateOf(ShareCardMode.SONG) }
    var shareDialogLyricQuote by remember { mutableStateOf<String?>(null) }

    BackHandler {
        when {
            showShareDialog -> showShareDialog = false
            showOptionsMenu -> showOptionsMenu = false
            showLyricsPanel -> showLyricsPanel = false
            else -> onDismiss()
        }
    }

    val rawProgress = (currentPositionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)

    val formattedSleepRemaining = sleepTimerRemainingSeconds?.let { sec ->
        val m = sec / 60
        val s = sec % 60
        "%d:%02d".format(m, s)
    } ?: sleepTimerRemainingMinutes?.let { "${it}M" }

    fun cycleDeckMode() {
        deckVisualMode = when (deckVisualMode) {
            DeckVisualMode.COVER -> DeckVisualMode.CASSETTE
            DeckVisualMode.CASSETTE -> DeckVisualMode.VINYL
            DeckVisualMode.VINYL -> DeckVisualMode.COVER
        }
    }

    val context = LocalContext.current
    val appearanceManager = remember(context) { CrankrAppearanceManager.getInstance(context) }
    val playerBgStyle by appearanceManager.playerBackgroundStyle.collectAsState()
    val lyricsAlign by appearanceManager.lyricsAlignment.collectAsState()
    val lyricsScale by appearanceManager.lyricsTextScale.collectAsState()

    var palette by remember(track.id, track.coverUrl) {
        mutableStateOf(DynamicColorExtractor.defaultPalette)
    }

    LaunchedEffect(track.id, track.coverUrl) {
        palette = DynamicColorExtractor.extractColors(context, track.coverUrl)
    }

    val coroutineScope = rememberCoroutineScope()
    val dragOffsetAnimatable = remember { Animatable(0f) }

    // Micro-interactions: Heart Pop & Play/Pause Pulse Halo
    val heartScale = remember { Animatable(1f) }
    var previousFavoriteState by remember { mutableStateOf(isFavorite) }
    LaunchedEffect(isFavorite) {
        if (isFavorite && !previousFavoriteState) {
            heartScale.snapTo(0.65f)
            heartScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        previousFavoriteState = isFavorite
    }
    val heartColor by animateColorAsState(
        targetValue = if (isFavorite) CrimsonRed else TextSecondary,
        animationSpec = tween(220),
        label = "heartColor"
    )

    val playPausePulseTransition = rememberInfiniteTransition(label = "PlayPausePulse")
    val pulseHaloScale by playPausePulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseHaloScale"
    )
    val pulseHaloAlpha by playPausePulseTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseHaloAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { androidx.compose.ui.unit.IntOffset(0, dragOffsetAnimatable.value.toInt()) }
            .graphicsLayer {
                val dragFraction = (dragOffsetAnimatable.value / 800f).coerceIn(0f, 0.45f)
                alpha = 1f - dragFraction
                val scaleFraction = 1f - (dragFraction * 0.12f)
                scaleX = scaleFraction
                scaleY = scaleFraction
            }
            .background(PitchBlack)
    ) {
        // Auralys-Grade Player Backdrop: Blurred Cover, Dynamic Gradient, or Solid Minimal
        when (playerBgStyle) {
            PlayerBackgroundStyle.BLUR -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.25f
                                scaleY = 1.25f
                            }
                            .blur(56.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.45f),
                                        0.50f to PitchBlack.copy(alpha = 0.78f),
                                        1.0f to PitchBlack.copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )
                }
            }
            PlayerBackgroundStyle.GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to palette.dominantColor.copy(alpha = 0.42f),
                                    0.42f to Surface,
                                    1.0f to PitchBlack
                                )
                            )
                        )
                )
            }
            PlayerBackgroundStyle.SOLID -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PitchBlack)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Minimalist Modern Pill Drag Handle
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 6.dp)
                    .width(38.dp)
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(TextMuted.copy(alpha = 0.5f))
            )

            // Header: Close button, Album info & Menu with interactive swipe down to collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (dragOffsetAnimatable.value > 150f) {
                                        onDismiss()
                                    } else {
                                        dragOffsetAnimatable.animateTo(
                                            0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    dragOffsetAnimatable.animateTo(0f)
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    dragOffsetAnimatable.snapTo(
                                        (dragOffsetAnimatable.value + dragAmount).coerceAtLeast(0f)
                                    )
                                }
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(CardBackground)
                        .border(1.dp, BorderSubtle, androidx.compose.foundation.shape.CircleShape)
                        .springPressable(scaleDown = 0.92f)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = TextWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW PLAYING",
                        fontFamily = CrankrDisplayFont,
                        color = CrimsonRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = track.album,
                        fontFamily = CrankrBodyFont,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Active Sleep Timer Indicator Badge
                    if (sleepTimerRemainingSeconds != null || sleepTimerRemainingMinutes != null || sleepTimerMode == "END_OF_TRACK") {
                        Spacer(modifier = Modifier.height(2.dp))
                        val isWindDownPhase = sleepTimerRemainingSeconds != null && sleepTimerRemainingSeconds <= 15
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = if (isWindDownPhase) Color(0x33FFE600) else CrimsonRedSoft,
                            modifier = Modifier.clickable { onOpenSleepTimer() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = if (isWindDownPhase) Color(0xFFFFE600) else CrimsonRed,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (sleepTimerMode == "END_OF_TRACK") "TIMER: AKHIR LAGU" else if (isWindDownPhase) "WIND-DOWN: $formattedSleepRemaining" else "TIMER: $formattedSleepRemaining",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWindDownPhase) Color(0xFFFFE600) else CrimsonRed
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            shareDialogMode = ShareCardMode.SONG
                            shareDialogLyricQuote = null
                            showShareDialog = true
                        },
                        modifier = Modifier.springPressable(scaleDown = 0.92f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Bagikan Lagu",
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenQueue,
                        modifier = Modifier.springPressable(scaleDown = 0.92f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Antrean Musik",
                            tint = TextWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true },
                            modifier = Modifier.springPressable(scaleDown = 0.92f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = TextWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier
                                .background(DarkCharcoal)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(3.dp))
                        ) {
                            if (track.isBandcamp) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "Dukung Artis di Bandcamp ↗",
                                                fontFamily = CrankrBodyFont,
                                                color = CrimsonRed,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp
                                            )
                                            if (!track.purchasePrice.isNullOrBlank()) {
                                                Text(
                                                    text = "Harga Rilis: ${track.purchasePrice}",
                                                    fontFamily = CrankrBodyFont,
                                                    color = TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = CrimsonRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showOptionsMenu = false
                                        val rawTarget = if (!track.bandcampUrl.isNullOrBlank()) {
                                            track.bandcampUrl
                                        } else {
                                            "https://bandcamp.com/search?q=" + java.net.URLEncoder.encode("${track.artist} ${track.title}", "UTF-8")
                                        }
                                        val targetUrl = com.example.spotifyclone.data.repository.BandcampRepository.sanitizeBandcampUrl(rawTarget)
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl))
                                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            try {
                                                val fallbackSearchUrl = "https://bandcamp.com/search?q=" + java.net.URLEncoder.encode("${track.artist} ${track.title}", "UTF-8")
                                                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fallbackSearchUrl))
                                                fallbackIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(fallbackIntent)
                                            } catch (e2: Exception) {
                                                android.widget.Toast.makeText(context, "Tidak dapat membuka browser", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Bagikan Story / Medsos",
                                        fontFamily = CrankrBodyFont,
                                        color = TextWhite,
                                        fontSize = 13.5.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    shareDialogMode = ShareCardMode.SONG
                                    shareDialogLyricQuote = null
                                    showShareDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Listen Together (Putar Bersama)",
                                        fontFamily = CrankrBodyFont,
                                        color = TextWhite,
                                        fontSize = 13.5.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    onOpenListenTogether()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Antrean Putar",
                                        fontFamily = CrankrBodyFont,
                                        color = TextWhite,
                                        fontSize = 13.5.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    onOpenQueue()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (formattedSleepRemaining != null) "Sleep Timer ($formattedSleepRemaining)" else "Sleep Timer",
                                        fontFamily = CrankrBodyFont,
                                        color = TextWhite,
                                        fontSize = 13.5.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = null,
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    onOpenSleepTimer()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Equalizer ($currentEqPreset)",
                                        fontFamily = CrankrBodyFont,
                                        color = TextWhite,
                                        fontSize = 13.5.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    onOpenEqualizer()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Tambahkan ke Playlist",
                                        fontFamily = CrankrBodyFont,
                                        color = TextWhite,
                                        fontSize = 13.5.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistAdd,
                                        contentDescription = null,
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    onAddToPlaylist()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (showLyricsPanel) "Sembunyikan Lirik" else "Lihat Lirik Lagu",
                                        fontFamily = CrankrBodyFont,
                                        color = TextWhite,
                                        fontSize = 13.5.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lyrics,
                                        contentDescription = null,
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showLyricsPanel = !showLyricsPanel
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = when (deckVisualMode) {
                                            DeckVisualMode.COVER -> "Tampilan: Cover Art"
                                            DeckVisualMode.CASSETTE -> "Tampilan: Kaset Pita"
                                            DeckVisualMode.VINYL -> "Tampilan: Vinyl Turntable"
                                        },
                                        fontFamily = CrankrBodyFont,
                                        color = TextWhite,
                                        fontSize = 13.5.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    cycleDeckMode()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Crossfade (${if (crossfadeDurationSeconds == 0) "Off" else "${crossfadeDurationSeconds}s"})",
                                        fontFamily = CrankrBodyFont,
                                        color = TextWhite,
                                        fontSize = 13.5.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    onOpenCrossfade()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Kecepatan Putar (${playbackSpeed}x)",
                                        fontFamily = CrankrBodyFont,
                                        color = TextWhite,
                                        fontSize = 13.5.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    onCyclePlaybackSpeed()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Ref 1 Signature Stage: Circular Radial Progress Ring (default COVER) or Cassette/Vinyl Deck
            if (deckVisualMode == DeckVisualMode.COVER) {
                CircularRadialPlayerDeck(
                    coverUrl = track.coverUrl,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    onSeekTo = onSeekTo,
                    onSwipeLeft = { onNextClick() },
                    onSwipeRight = { onPreviousClick() },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                RetroPunkDeck(
                    track = track,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    mode = deckVisualMode,
                    onToggleMode = { cycleDeckMode() },
                    onSwipeRight = { onPreviousClick() },
                    onSwipeLeft = { onNextClick() },
                    onSwipeDown = { onDismiss() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Track Title & Artist right beneath album art (eliminating excessive vertical gaps)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = {
                            (slideInVertically(animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { height -> height / 2 } + fadeIn(tween(220)))
                                .togetherWith(slideOutVertically(animationSpec = tween(150)) { height -> -height / 2 } + fadeOut(tween(150)))
                        },
                        label = "TrackInfoRoll"
                    ) { curTrack ->
                        Column {
                            MarqueeText(
                                text = curTrack.title,
                                fontFamily = CrankrBodyFont,
                                color = TextWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.sp,
                                active = isPlaying
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            val parsedArtists = remember(curTrack.artist) { ArtistParser.parseArtists(curTrack.artist) }
                            if (parsedArtists.size <= 1) {
                                val singleArtist = parsedArtists.firstOrNull() ?: curTrack.artist
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .springPressable(scaleDown = 0.95f)
                                        .clickable(enabled = onArtistClick != null) {
                                            onArtistClick?.invoke(singleArtist, curTrack.spotifyArtistId)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    MarqueeText(
                                        text = singleArtist,
                                        fontFamily = CrankrBodyFont,
                                        color = if (onArtistClick != null) TextWhite.copy(alpha = 0.88f) else TextSecondary,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        active = isPlaying
                                    )
                                }
                            } else {
                                // Multi-artist collaboration: each artist is individually clickable
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    parsedArtists.forEachIndexed { index, artistName ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .springPressable(scaleDown = 0.95f)
                                                .clickable(enabled = onArtistClick != null) {
                                                    val matchedId = if (artistName.equals(curTrack.artist, ignoreCase = true)) curTrack.spotifyArtistId else null
                                                    onArtistClick?.invoke(artistName, matchedId)
                                                }
                                                .padding(horizontal = 3.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = artistName,
                                                fontFamily = CrankrBodyFont,
                                                color = if (onArtistClick != null) TextWhite.copy(alpha = 0.92f) else TextSecondary,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                        if (index < parsedArtists.size - 1) {
                                            Text(
                                                text = if (index == parsedArtists.size - 2) " & " else ", ",
                                                fontFamily = CrankrBodyFont,
                                                color = TextMuted,
                                                fontSize = 13.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Download Button / Status
                    if (downloadProgress != null) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(24.dp),
                            color = CrimsonRed,
                            strokeWidth = 2.dp
                        )
                    } else if (track.isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = CrimsonRed,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        IconButton(
                            onClick = onDownloadClick,
                            modifier = Modifier.springPressable(scaleDown = 0.92f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download for Offline",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Favorite Button with Heart Pop Micro-Interaction
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.springPressable(scaleDown = 0.92f)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = heartColor,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = heartScale.value
                                    scaleY = heartScale.value
                                }
                        )
                    }
                }
            }

            if (track.isBandcamp) {
                Spacer(modifier = Modifier.height(8.dp))
                BandcampSupportBanner(
                    track = track,
                    onSupportClick = {
                        val rawTarget = if (!track.bandcampUrl.isNullOrBlank()) {
                            track.bandcampUrl
                        } else {
                            "https://bandcamp.com/search?q=" + java.net.URLEncoder.encode("${track.artist} ${track.title}", "UTF-8")
                        }
                        val targetUrl = com.example.spotifyclone.data.repository.BandcampRepository.sanitizeBandcampUrl(rawTarget)
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val fallbackSearchUrl = "https://bandcamp.com/search?q=" + java.net.URLEncoder.encode("${track.artist} ${track.title}", "UTF-8")
                                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fallbackSearchUrl))
                                fallbackIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(fallbackIntent)
                            } catch (e2: Exception) {
                                android.widget.Toast.makeText(context, "Tidak dapat membuka browser", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Punk Overdrive Scrubber: Jagged distortion spikes with electric guitar pick slider
            PunkOverdriveScrubber(
                trackId = track.id,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onSeekTo = onSeekTo,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Playback Controls Row with Tactile Spring Physics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Shuffle button
                IconButton(
                    onClick = onShuffleToggle,
                    modifier = Modifier.springPressable(scaleDown = 0.90f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) CrimsonRed else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Previous button with spring press
                IconButton(
                    onClick = onPreviousClick,
                    modifier = Modifier.springPressable(scaleDown = 0.90f)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextWhite,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Ergonomic Circular Bold Red Play / Pause Button with Pulsing Halo & Icon Morph
                Box(contentAlignment = Alignment.Center) {
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .graphicsLayer {
                                    scaleX = pulseHaloScale
                                    scaleY = pulseHaloScale
                                    alpha = pulseHaloAlpha
                                }
                                .background(CrimsonRed, CircleShape)
                        )
                    }
                    Surface(
                        onClick = onPlayPauseClick,
                        shape = CircleShape,
                        color = CrimsonRed,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(64.dp)
                            .springPressable(scaleDown = 0.92f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.55f, animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(140)))
                                        .togetherWith(scaleOut(targetScale = 0.55f, animationSpec = tween(100)) + fadeOut(tween(100)))
                                },
                                label = "PlayPauseIconMorph"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = OnPrimary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }

                // Next button with spring press
                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.springPressable(scaleDown = 0.90f)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = TextWhite,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Repeat button with responsive 3-state cycle indicator
                IconButton(
                    onClick = onRepeatToggle,
                    modifier = Modifier.springPressable(scaleDown = 0.90f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val (repIcon, repTint, repLabel) = when (repeatMode) {
                            RepeatMode.OFF -> Triple(Icons.Default.Repeat, TextMuted, "Matikan Loop / Off (Mode Normal)")
                            RepeatMode.ALL -> Triple(Icons.Default.Repeat, CrimsonRed, "Putar 1 Kali Lalu Berpindah (Play Once & Next)")
                            RepeatMode.ONE -> Triple(Icons.Default.RepeatOne, CrimsonRed, "Loop Terus Lagu Ini (Repeat One)")
                        }
                        Icon(
                            imageVector = repIcon,
                            contentDescription = repLabel,
                            tint = repTint,
                            modifier = Modifier.size(20.dp)
                        )
                        if (repeatMode != RepeatMode.OFF) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(CrimsonRed)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Synced Lyrics Preview Card
            val activeLyricLine = remember(lyricsData, currentPositionMs, lyrics) {
                if (lyricsData != null && lyricsData.lines.isNotEmpty()) {
                    val idx = lyricsData.lines.indexOfLast { it.timestampMs <= currentPositionMs }
                    if (idx >= 0 && idx in lyricsData.lines.indices) {
                        lyricsData.lines[idx].text
                    } else {
                        lyricsData.lines.first().text
                    }
                } else if (!lyrics.isNullOrBlank()) {
                    lyrics.lines().firstOrNull { it.isNotBlank() } ?: "Lirik tersedia"
                } else {
                    null
                }
            }

            // Ref 1 "Next Songs" + Frosted Glass Lyrics Peek
            if (nextTrack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Next Songs",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite.copy(alpha = 0.9f)
                    )
                    GlassFilterPill(
                        label = "Lirik",
                        selected = showLyricsPanel,
                        leadingIcon = Icons.Default.Lyrics,
                        onClick = { showLyricsPanel = !showLyricsPanel }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                GlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .springPressable(0.97f)
                        .clickable { onOpenQueue() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = nextTrack.coverUrl,
                            contentDescription = nextTrack.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardSurface)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nextTrack.title,
                                fontFamily = CrankrBodyFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = nextTrack.artist,
                                fontFamily = CrankrBodyFont,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Antrean",
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                GlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .springPressable(0.97f)
                        .clickable { showLyricsPanel = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Primary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = "Lirik",
                                tint = Primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LIRIK SINKRON",
                                fontFamily = CrankrDisplayFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeLyricLine ?: (if (isLoadingLyrics) "Memuat lirik..." else "Ketuk untuk melihat lirik penuh"),
                                fontFamily = CrankrBodyFont,
                                color = Secondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.NorthEast,
                            contentDescription = "Buka",
                            tint = Tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Swipe-up / Slide-up Lyrics Panel from Bottom
        AnimatedVisibility(
            visible = showLyricsPanel,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.76f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(DarkCharcoal.copy(alpha = 0.96f))
                    .border(1.dp, Border, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = null,
                                tint = CrimsonRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LIRIK LAGU",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                letterSpacing = 1.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    shareDialogMode = ShareCardMode.LYRIC
                                    val activeIndex = if (lyricsData != null && lyricsData.isSynced && lyricsData.lines.isNotEmpty()) {
                                        lyricsData.lines.indexOfLast { it.timestampMs <= currentPositionMs }
                                    } else -1
                                    shareDialogLyricQuote = if (activeIndex >= 0 && lyricsData != null && activeIndex in lyricsData.lines.indices) {
                                        lyricsData.lines[activeIndex].text
                                    } else null
                                    showShareDialog = true
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Bagikan Lirik",
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { showLyricsPanel = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup Lirik",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoadingLyrics) {
                        LyricsShimmerLoading()
                    } else if (lyricsData != null && lyricsData.lines.isNotEmpty()) {
                        if (lyricsData.isSynced) {
                            val activeIndex = lyricsData.lines.indexOfLast { it.timestampMs <= currentPositionMs }
                            val listState = rememberLazyListState()

                            LaunchedEffect(activeIndex) {
                                if (activeIndex >= 0) {
                                    listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
                            ) {
                                itemsIndexed(
                                    items = lyricsData.lines,
                                    key = { index, line -> "lyric_${index}_${line.timestampMs}" }
                                ) { index, line ->
                                    val isCurrent = index == activeIndex
                                    val isPast = index < activeIndex
                                    val targetAlpha by animateFloatAsState(
                                        targetValue = when {
                                            isCurrent -> 1f
                                            isPast -> 0.34f
                                            else -> 0.48f
                                        },
                                        animationSpec = tween(280),
                                        label = "LyricAlpha_$index"
                                    )
                                    val targetScale by animateFloatAsState(
                                        targetValue = if (isCurrent) 1.03f else 0.97f,
                                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                                        label = "LyricScale_$index"
                                    )
                                    val resolvedAlign = if (lyricsAlign == LyricsAlignment.CENTER) TextAlign.Center else TextAlign.Start

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (line.timestampMs >= 0) {
                                                    onSeekTo(line.timestampMs)
                                                }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .graphicsLayer {
                                                alpha = targetAlpha
                                                scaleX = targetScale
                                                scaleY = targetScale
                                            }
                                    ) {
                                        Text(
                                            text = line.text.ifEmpty { "♪" },
                                            fontFamily = if (isCurrent) CrankrDisplayFont else CrankrBodyFont,
                                            color = TextWhite,
                                            fontSize = if (isCurrent) lyricsScale.activeSp.sp else lyricsScale.inactiveSp.sp,
                                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            lineHeight = if (isCurrent) (lyricsScale.activeSp * 1.32f).sp else (lyricsScale.inactiveSp * 1.35f).sp,
                                            textAlign = resolvedAlign,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = lyricsData.rawPlain ?: lyricsData.lines.joinToString("\n") { it.text },
                                    fontFamily = CrankrBodyFont,
                                    color = TextWhite,
                                    fontSize = 15.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    } else if (!lyrics.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = lyrics,
                                fontFamily = CrankrBodyFont,
                                color = TextWhite,
                                fontSize = 15.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Start
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PitchBlack)
                                    .border(1.dp, CrimsonRed.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MicOff,
                                    contentDescription = null,
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "LIRIK BELUM TERSEDIA",
                                fontFamily = CrankrDisplayFont,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 16.sp,
                                letterSpacing = 0.5.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Trek ini belum memiliki lirik atau merupakan lagu instrumental.",
                                fontFamily = CrankrBodyFont,
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    shareDialogLyricQuote = null
                                    shareDialogMode = ShareCardMode.SONG
                                    showShareDialog = true
                                },
                                shape = RoundedCornerShape(2.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRedSoft),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .border(1.dp, CrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                                    .springPressable(0.92f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "BAGIKAN KARTU LAGU",
                                    fontFamily = CrankrDisplayFont,
                                    fontWeight = FontWeight.Bold,
                                    color = CrimsonRed,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showShareDialog) {
            PunkShareDialog(
                track = track,
                lyricsData = lyricsData,
                currentPositionMs = currentPositionMs,
                initialMode = shareDialogMode,
                initialLyricQuote = shareDialogLyricQuote,
                initialBackgroundStyle = storyBackgroundStyle,
                onBackgroundStyleChange = onStoryBackgroundStyleChange,
                onDismiss = { showShareDialog = false }
            )
        }
    }
}

/**
 * Shimmer placeholder loading state saat lirik sedang diambil dari jaringan (LRCLIB & fallback).
 */
@Composable
private fun LyricsShimmerLoading(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "LyricsShimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "LyricsShimmerTranslate"
    )

    val shimmerColors = listOf(
        CardBackground,
        ElevatedBackground,
        CrimsonRed.copy(alpha = 0.28f),
        ElevatedBackground,
        CardBackground
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(CrimsonRed, androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Memuat Lirik Lagu...",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CrimsonRed,
                letterSpacing = 0.5.sp
            )
        }

        val lineFractions = listOf(0.75f, 0.92f, 0.60f, 0.85f, 0.70f, 0.95f, 0.65f, 0.88f, 0.55f, 0.80f)
        lineFractions.forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(brush)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun BandcampSupportBanner(
    track: Track,
    onSupportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onSupportClick,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF181818),
        border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .springPressable(scaleDown = 0.97f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CrimsonRedSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Support",
                        tint = CrimsonRed,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SUPPORT THE ARTIST",
                            fontFamily = CrankrDisplayFont,
                            color = TextWhite,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        if (!track.purchasePrice.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = CrimsonRedSoft,
                                border = BorderStroke(0.5.dp, CrimsonRed.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = track.purchasePrice,
                                    fontFamily = CrankrDisplayFont,
                                    color = CrimsonRed,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(1.5.dp))
                    Text(
                        text = "Beli rilis digital & merch langsung dari musisi",
                        fontFamily = CrankrBodyFont,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CrimsonRed,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "DUKUNG",
                        fontFamily = CrankrDisplayFont,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}
