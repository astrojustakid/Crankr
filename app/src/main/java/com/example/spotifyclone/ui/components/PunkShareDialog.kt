package com.example.spotifyclone.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.spotifyclone.R
import com.example.spotifyclone.data.model.LyricsData
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.share.PunkShareCardGenerator
import com.example.spotifyclone.service.share.StoryBackgroundStyle
import com.example.spotifyclone.service.share.StoryColorPalette
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.util.DynamicColorExtractor
import com.example.spotifyclone.util.ExtractedPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ShareCardMode {
    SONG,
    LYRIC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PunkShareDialog(
    track: Track,
    lyricsData: LyricsData?,
    currentPositionMs: Long,
    initialMode: ShareCardMode = ShareCardMode.SONG,
    initialLyricQuote: String? = null,
    initialBackgroundStyle: StoryBackgroundStyle = StoryBackgroundStyle.SOLID_BLACK,
    onBackgroundStyleChange: ((StoryBackgroundStyle) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(initialMode) }
    var selectedBackgroundStyle by remember { mutableStateOf(initialBackgroundStyle) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedPalette by remember { mutableStateOf<ExtractedPalette?>(null) }
    var storyPalette by remember { mutableStateOf(StoryColorPalette()) }
    var isGeneratingImage by remember { mutableStateOf(false) }

    // Lyric selection: parse both synced lines and raw plain text
    val availableLyrics = remember(lyricsData) {
        if (lyricsData?.lines?.isNotEmpty() == true) {
            lyricsData.lines.map { it.text.trim() }.filter { it.isNotBlank() }
        } else if (!lyricsData?.rawPlain.isNullOrBlank()) {
            lyricsData.rawPlain.lines().map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    }

    // Determine initial line index based on initialLyricQuote or currently playing synced position
    val initialIndex = remember(availableLyrics, currentPositionMs, initialLyricQuote) {
        if (!initialLyricQuote.isNullOrBlank()) {
            val found = availableLyrics.indexOfFirst { it.equals(initialLyricQuote.trim(), ignoreCase = true) }
            if (found >= 0) found else 0
        } else if (lyricsData != null && lyricsData.isSynced && lyricsData.lines.isNotEmpty()) {
            val active = lyricsData.lines.indexOfLast { it.timestampMs <= currentPositionMs }
            if (active in lyricsData.lines.indices) {
                val activeText = lyricsData.lines[active].text.trim()
                val found = availableLyrics.indexOfFirst { it == activeText }
                if (found >= 0) found else 0
            } else {
                0
            }
        } else {
            0
        }
    }

    var startLineIndex by remember(availableLyrics) {
        mutableIntStateOf(if (availableLyrics.isNotEmpty()) initialIndex.coerceIn(0, availableLyrics.lastIndex) else 0)
    }
    var endLineIndex by remember(availableLyrics) {
        mutableIntStateOf(if (availableLyrics.isNotEmpty()) initialIndex.coerceIn(0, availableLyrics.lastIndex) else 0)
    }
    var feedbackNotice by remember { mutableStateOf<String?>(null) }

    // Auto-dismiss warning feedback after 3 seconds
    LaunchedEffect(feedbackNotice) {
        if (feedbackNotice != null) {
            delay(3000)
            feedbackNotice = null
        }
    }

    // Auto-scroll lyric picker list to selected initial index
    val pickerScrollState = rememberScrollState()
    LaunchedEffect(initialIndex, availableLyrics.isNotEmpty()) {
        if (availableLyrics.isNotEmpty() && initialIndex > 0) {
            pickerScrollState.animateScrollTo((initialIndex * 75).coerceAtLeast(0))
        }
    }

    // Slice currently selected lines (guaranteed 1 to 5 lines)
    val selectedLyrics = remember(availableLyrics, startLineIndex, endLineIndex) {
        if (availableLyrics.isEmpty()) emptyList()
        else {
            val s = startLineIndex.coerceIn(0, availableLyrics.lastIndex)
            val e = endLineIndex.coerceIn(s, (s + 4).coerceAtMost(availableLyrics.lastIndex))
            availableLyrics.subList(s, e + 1)
        }
    }

    // Multi-line selection logic handler
    fun onLyricLineTapped(tappedIdx: Int) {
        if (availableLyrics.isEmpty()) return
        val currentCount = endLineIndex - startLineIndex + 1

        if (currentCount == 1) {
            if (tappedIdx == startLineIndex) {
                return
            }
            val minIdx = minOf(startLineIndex, tappedIdx)
            val maxIdx = maxOf(startLineIndex, tappedIdx)
            val count = maxIdx - minIdx + 1

            if (count <= 5) {
                startLineIndex = minIdx
                endLineIndex = maxIdx
                feedbackNotice = null
            } else {
                if (tappedIdx > startLineIndex) {
                    startLineIndex = (tappedIdx - 4).coerceAtLeast(0)
                    endLineIndex = tappedIdx
                } else {
                    startLineIndex = tappedIdx
                    endLineIndex = (tappedIdx + 4).coerceAtMost(availableLyrics.lastIndex)
                }
                feedbackNotice = "Maksimal 5 baris lirik (pilihan disesuaikan)"
            }
        } else {
            if (tappedIdx in startLineIndex..endLineIndex) {
                if (tappedIdx == startLineIndex) {
                    startLineIndex = (startLineIndex + 1).coerceAtMost(endLineIndex)
                    feedbackNotice = null
                } else if (tappedIdx == endLineIndex) {
                    endLineIndex = (endLineIndex - 1).coerceAtLeast(startLineIndex)
                    feedbackNotice = null
                } else {
                    startLineIndex = tappedIdx
                    endLineIndex = tappedIdx
                    feedbackNotice = null
                }
            } else if (tappedIdx == endLineIndex + 1) {
                if (currentCount < 5) {
                    endLineIndex = tappedIdx
                    feedbackNotice = null
                } else {
                    startLineIndex = startLineIndex + 1
                    endLineIndex = tappedIdx
                    feedbackNotice = "Maksimal 5 baris (baris pertama digeser)"
                }
            } else if (tappedIdx == startLineIndex - 1) {
                if (currentCount < 5) {
                    startLineIndex = tappedIdx
                    feedbackNotice = null
                } else {
                    startLineIndex = tappedIdx
                    endLineIndex = endLineIndex - 1
                    feedbackNotice = "Maksimal 5 baris (baris terakhir digeser)"
                }
            } else {
                startLineIndex = tappedIdx
                endLineIndex = tappedIdx
                feedbackNotice = null
            }
        }
    }

    // Fetch cover bitmap and extract Android Palette colors on launch
    LaunchedEffect(track.coverUrl) {
        coverBitmap = PunkShareCardGenerator.getCoverBitmap(context, track.coverUrl)
        val pal = DynamicColorExtractor.extractColors(context, track.coverUrl)
        extractedPalette = pal
        storyPalette = StoryColorPalette(
            dominantColor = pal.dominantColor,
            secondaryColor = pal.secondaryColor,
            dominantColorInt = pal.dominantColorInt
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkCharcoal,
        scrimColor = Color.Black.copy(alpha = 0.70f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextMuted.copy(alpha = 0.5f))
            )
        },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SHARE TO STORY & MEDSOS",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonRed,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Format 9:16 Minimalis Instagram Stories & Medsos",
                            fontFamily = CrankrBodyFont,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Tabs: Song Card vs Lyric Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(CardBackground)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Song Tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedMode = ShareCardMode.SONG },
                        shape = RoundedCornerShape(3.dp),
                        color = if (selectedMode == ShareCardMode.SONG) CrimsonRed else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (selectedMode == ShareCardMode.SONG) TextWhite else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "KARTU LAGU",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedMode == ShareCardMode.SONG) TextWhite else TextSecondary
                            )
                        }
                    }

                    // Lyric Tab
                    val hasLyrics = availableLyrics.isNotEmpty()
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = hasLyrics) {
                                if (hasLyrics) selectedMode = ShareCardMode.LYRIC
                            },
                        shape = RoundedCornerShape(3.dp),
                        color = if (selectedMode == ShareCardMode.LYRIC) CrimsonRed else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = null,
                                tint = if (!hasLyrics) TextMuted else if (selectedMode == ShareCardMode.LYRIC) TextWhite else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasLyrics) "KUTIPAN LIRIK" else "LIRIK TIDAK ADA",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!hasLyrics) TextMuted else if (selectedMode == ShareCardMode.LYRIC) TextWhite else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Content: Live 9:16 Canvas Preview + Swatch Bar + Lyric Selector
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Live 9:16 Story Canvas Preview with Smooth Rounded Corners & Dynamic Canvas Background
                    Box(
                        modifier = Modifier
                            .width(202.dp)
                            .height(360.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(storyPalette.getBrush(selectedBackgroundStyle))
                            .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedMode == ShareCardMode.SONG) {
                            // Centered Minimalist Song Card
                            Column(
                                modifier = Modifier
                                    .width(166.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF141416))
                                    .border(1.dp, Color(0xFF28282D), RoundedCornerShape(3.dp))
                                    .padding(9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Square Album Cover at top
                                AsyncImage(
                                    model = track.coverUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(148.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(2.dp))
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Song Title (Bold)
                                Text(
                                    text = track.title,
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Artist Name Below
                                Text(
                                    text = track.artist,
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 9.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Crankr Logo Text at the very bottom of the card
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.crankr_logo),
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "CRANKR",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        } else {
                            // Centered Minimalist Lyric Card
                            val lineCount = selectedLyrics.size.coerceIn(1, 5)
                            val maxChars = selectedLyrics.maxOfOrNull { it.length } ?: 0
                            val baseSp = when (lineCount) {
                                1 -> 13.5f
                                2 -> 12f
                                3 -> 10.5f
                                4 -> 9.5f
                                else -> 8.5f
                            }
                            val charScale = when {
                                maxChars > 45 -> 0.85f
                                maxChars > 35 -> 0.92f
                                else -> 1.0f
                            }
                            val dynamicFontSize = (baseSp * charScale).sp
                            val dynamicLineHeight = (baseSp * charScale * 1.35f).sp
                            val quoteFontSize = (baseSp * 1.5f).coerceIn(13f, 19f).sp

                            Column(
                                modifier = Modifier
                                    .width(166.dp)
                                    .height(236.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF141416))
                                    .border(1.dp, Color(0xFF28282D), RoundedCornerShape(3.dp))
                                    .padding(9.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.Start
                            ) {
                                // Middle: Multi-line Lyric Quote (replaces square cover)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "“",
                                            fontFamily = CrankrDisplayFont,
                                            fontSize = quoteFontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = CrimsonRed,
                                            lineHeight = quoteFontSize
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height((lineCount * 14).coerceIn(14, 56).dp)
                                                    .background(CrimsonRed, RoundedCornerShape(1.dp))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                selectedLyrics.forEach { line ->
                                                    Text(
                                                        text = line,
                                                        fontFamily = CrankrDisplayFont,
                                                        fontSize = dynamicFontSize,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextWhite,
                                                        lineHeight = dynamicLineHeight,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "”",
                                            fontFamily = CrankrDisplayFont,
                                            fontSize = quoteFontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = CrimsonRed,
                                            lineHeight = quoteFontSize,
                                            modifier = Modifier.align(Alignment.End)
                                        )
                                    }
                                }

                                // Bottom Footer: Small Thumbnail Cover + Title & Artist + Crankr Logo
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color(0xFF28282D))
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = track.coverUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(3.dp))
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.title,
                                                    fontFamily = CrankrDisplayFont,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = track.artist,
                                                    fontFamily = CrankrBodyFont,
                                                    fontSize = 7.sp,
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        Image(
                                            painter = painterResource(id = R.drawable.crankr_logo),
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Color Palette Selector (Baris Lingkaran Pemilih Warna)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StoryBackgroundStyle.values().forEach { style ->
                            val isSelected = (selectedBackgroundStyle == style)
                            val swatchBrush = remember(style, storyPalette) {
                                storyPalette.getBrush(style)
                            }

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.5.dp, Color.White, CircleShape)
                                        } else {
                                            Modifier.border(1.dp, Color(0x33FFFFFF), CircleShape)
                                        }
                                    )
                                    .padding(if (isSelected) 3.5.dp else 2.dp)
                                    .clip(CircleShape)
                                    .background(swatchBrush)
                                    .clickable {
                                        selectedBackgroundStyle = style
                                        onBackgroundStyleChange?.invoke(style)
                                    }
                            )
                        }
                    }

                    // 3. If Lyric Mode: Interactive Multi-line Lyric Picker list
                    if (selectedMode == ShareCardMode.LYRIC && availableLyrics.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))

                        // Header & Counter Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PILIH BARIS LIRIK:",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CrimsonRed
                                )
                                Text(
                                    text = "Ketuk baris awal lalu baris akhir (min. 1, maks. 5 baris)",
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 9.5.sp,
                                    color = TextSecondary
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CrimsonRedSoft,
                                border = BorderStroke(1.dp, CrimsonRed)
                            ) {
                                Text(
                                    text = "${selectedLyrics.size} / 5 BARIS",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CrimsonRed,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Visual feedback banner
                        if (feedbackNotice != null) {
                            Spacer(modifier = Modifier.height(5.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(CrimsonRedSoft)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = feedbackNotice!!,
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextWhite
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Scrollable Lyric Line List
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CardBackground)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                                .verticalScroll(pickerScrollState)
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            availableLyrics.forEachIndexed { index, lineText ->
                                val isSelected = index in startLineIndex..endLineIndex
                                val isStart = isSelected && index == startLineIndex && startLineIndex != endLineIndex
                                val isEnd = isSelected && index == endLineIndex && startLineIndex != endLineIndex
                                val orderNum = if (isSelected) (index - startLineIndex + 1) else null

                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = if (isSelected) CrimsonRedSoft else Color.Transparent,
                                    border = if (isSelected) BorderStroke(1.dp, CrimsonRed) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onLyricLineTapped(index) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(17.dp)
                                                    .background(CrimsonRed, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$orderNum",
                                                    fontFamily = CrankrDisplayFont,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextWhite
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "${index + 1}",
                                                fontFamily = CrankrBodyFont,
                                                fontSize = 9.5.sp,
                                                color = TextMuted,
                                                modifier = Modifier.width(17.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = lineText,
                                            fontFamily = CrankrBodyFont,
                                            fontSize = 11.5.sp,
                                            color = if (isSelected) TextWhite else TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (isStart) {
                                            Surface(
                                                color = CrimsonRedDark,
                                                shape = RoundedCornerShape(2.dp)
                                            ) {
                                                Text(
                                                    text = "AWAL",
                                                    fontFamily = CrankrDisplayFont,
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextWhite,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        } else if (isEnd) {
                                            Surface(
                                                color = CrimsonRedDark,
                                                shape = RoundedCornerShape(2.dp)
                                            ) {
                                                Text(
                                                    text = "AKHIR",
                                                    fontFamily = CrankrDisplayFont,
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextWhite,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons: Share to Instagram Stories vs General Share
                if (isGeneratingImage) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = CrimsonRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Merender kartu resolusi tinggi (1080x1920)...",
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.sp,
                            color = TextWhite
                        )
                    }
                } else {
                    // Instagram Stories Primary Button
                    Surface(
                        onClick = {
                            isGeneratingImage = true
                            coroutineScope.launch {
                                val uri: Uri? = if (selectedMode == ShareCardMode.SONG) {
                                    PunkShareCardGenerator.generateSongCard(
                                        context = context,
                                        track = track,
                                        coverBitmap = coverBitmap,
                                        backgroundStyle = selectedBackgroundStyle,
                                        palette = extractedPalette
                                    )
                                } else {
                                    PunkShareCardGenerator.generateLyricCard(
                                        context = context,
                                        track = track,
                                        coverBitmap = coverBitmap,
                                        lyrics = selectedLyrics,
                                        backgroundStyle = selectedBackgroundStyle,
                                        palette = extractedPalette
                                    )
                                }
                                isGeneratingImage = false
                                if (uri != null) {
                                    PunkShareCardGenerator.shareToInstagramStory(context, uri, track)
                                } else {
                                    Toast.makeText(context, "Gagal membuat gambar share", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = CrimsonRed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .springPressable(0.95f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SHARE KE INSTAGRAM STORIES",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary: General Share (WhatsApp, Telegram, etc.)
                    Surface(
                        onClick = {
                            isGeneratingImage = true
                            coroutineScope.launch {
                                val uri: Uri? = if (selectedMode == ShareCardMode.SONG) {
                                    PunkShareCardGenerator.generateSongCard(
                                        context = context,
                                        track = track,
                                        coverBitmap = coverBitmap,
                                        backgroundStyle = selectedBackgroundStyle,
                                        palette = extractedPalette
                                    )
                                } else {
                                    PunkShareCardGenerator.generateLyricCard(
                                        context = context,
                                        track = track,
                                        coverBitmap = coverBitmap,
                                        lyrics = selectedLyrics,
                                        backgroundStyle = selectedBackgroundStyle,
                                        palette = extractedPalette
                                    )
                                }
                                isGeneratingImage = false
                                if (uri != null) {
                                    val customText = if (selectedMode == ShareCardMode.SONG) {
                                        "Mendengarkan \"${track.title}\" - ${track.artist} di Crankr"
                                    } else {
                                        val quoteLines = selectedLyrics.joinToString("\n")
                                        "“$quoteLines”\n\n— \"${track.title}\" oleh ${track.artist} di Crankr"
                                    }
                                    PunkShareCardGenerator.shareToGeneral(context, uri, track, customText)
                                } else {
                                    Toast.makeText(context, "Gagal membuat gambar share", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = CardBackground,
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .springPressable(0.95f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BAGIKAN KE MEDSOS LAINNYA",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
