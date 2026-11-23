package com.example.spotifyclone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.data.repository.AlbumRelease
import com.example.spotifyclone.data.repository.SimilarArtist
import com.example.spotifyclone.theme.*
import com.example.spotifyclone.ui.components.springPressable
import java.util.Locale

/**
 * Tampilan profil artis Compose murni minimalis punk rock:
 * - Header status bars dengan foto resolusi tinggi dan fade gradien hitam pekat.
 * - Tombol Play & Acak bergaya Crankr neon red.
 * - Seksi Lagu Teratas (Top 5 tracks) dengan toggle "LIHAT SEMUA".
 * - Seksi Diskografi (LazyRow & Grid 2 kolom) dengan chip filter ("Semua", "Album", "Single & EP").
 * - Semua pemanggilan gambar menggunakan cache Coil aktif.
 */
@Composable
fun ArtistProfileScreen(
    artistName: String,
    artistId: String? = null,
    officialPhotoUrl: String? = null,
    discography: List<AlbumRelease> = emptyList(),
    albums: List<AlbumRelease> = emptyList(),
    singlesAndEPs: List<AlbumRelease> = emptyList(),
    tracks: List<Track> = emptyList(),
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    favoriteIds: Set<String> = emptySet(),
    downloadProgressMap: Map<String, Float> = emptyMap(),
    isLoading: Boolean = false,
    isLoadingDiscography: Boolean = false,
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
    similarArtists: List<SimilarArtist> = emptyList(),
    onSimilarArtistClick: (SimilarArtist) -> Unit = {},
    errorMessage: String? = null,
    onRetryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current

    var showAllPopularTracks by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Semua") }
    var isGridView by remember { mutableStateOf(false) }

    val allReleases = remember(discography, albums, singlesAndEPs) {
        val list = mutableListOf<AlbumRelease>()
        list.addAll(discography)
        list.addAll(albums)
        list.addAll(singlesAndEPs)
        val validList = list.filter { !it.id.startsWith("UC") && it.collectionId?.startsWith("UC") != true }
        if (validList.isEmpty()) discography.filter { !it.id.startsWith("UC") } else validList.distinctBy { it.title.lowercase().trim() }
    }

    val latestRelease = remember(allReleases) { allReleases.firstOrNull() }

    val filteredDiscography = remember(allReleases, selectedCategory) {
        when (selectedCategory) {
            "Album" -> allReleases.filter { it.releaseType.equals("Album", ignoreCase = true) }
            "Single & EP" -> allReleases.filter {
                it.releaseType.equals("Single", ignoreCase = true) || it.releaseType.equals("EP", ignoreCase = true)
            }
            else -> allReleases
        }
    }

    val displayTracks = remember(tracks, showAllPopularTracks) {
        if (showAllPopularTracks) tracks else tracks.take(5)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PitchBlack),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Header Banner & Profile Photo
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                if (!officialPhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(officialPhotoUrl)
                            .crossfade(true)
                            .crossfade(300)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = artistName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(CardBackground, DarkCharcoal)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Default Avatar",
                            modifier = Modifier.size(80.dp),
                            tint = TextMuted.copy(alpha = 0.25f)
                        )
                    }
                }

                // Top Vignette Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(PitchBlack.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                )

                // Bottom Fade Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.2f to Color.Transparent,
                                0.65f to PitchBlack.copy(alpha = 0.75f),
                                1.0f to PitchBlack
                            )
                        )
                )

                // Back Button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 14.dp, top = 12.dp)
                ) {
                    Surface(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .springPressable(scaleDown = 0.92f),
                        shape = CircleShape,
                        color = PitchBlack.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = TextWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Artist Details Overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
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
                            text = "ARTIS RESMI",
                            color = CrimsonRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CrankrDisplayFont,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Text(
                        text = artistName.uppercase(Locale.ROOT),
                        color = TextWhite,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = CrankrDisplayFont,
                        lineHeight = 36.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (tracks.isNotEmpty()) "${tracks.size} Lagu Terdaftar" else "Katalog Musik",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontFamily = CrankrBodyFont
                    )
                }
            }
        }

        // 2. Play & Shuffle Buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { onShufflePlayClick(tracks) },
                    modifier = Modifier.springPressable(scaleDown = 0.94f),
                    enabled = tracks.isNotEmpty(),
                    shape = RoundedCornerShape(22.dp),
                    color = CardBackground,
                    border = BorderStroke(1.5.dp, CrimsonRed)
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

                Surface(
                    onClick = { onPlayAllClick(tracks) },
                    modifier = Modifier.springPressable(scaleDown = 0.94f),
                    enabled = tracks.isNotEmpty(),
                    shape = RoundedCornerShape(22.dp),
                    color = CrimsonRed
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Putar",
                            tint = TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PUTAR",
                            color = TextWhite,
                            fontFamily = CrankrDisplayFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }

        // 2b. Seksi Rilisan Terbaru (Latest Release)
        if (latestRelease != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
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
                            text = "RILISAN TERBARU",
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CrankrDisplayFont,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardBackground)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { onAlbumClick(latestRelease) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(ElevatedBackground)
                        ) {
                            if (latestRelease.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(latestRelease.coverUrl)
                                        .crossfade(true)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .build(),
                                    contentDescription = latestRelease.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                color = CrimsonRedSoft,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (latestRelease.releaseType.isNotBlank()) latestRelease.releaseType.uppercase() else "RILISAN BARU",
                                    color = CrimsonRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CrankrDisplayFont,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = latestRelease.title,
                                color = TextWhite,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CrankrDisplayFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val releaseMeta = buildString {
                                if (latestRelease.releaseYear.isNotBlank()) append(latestRelease.releaseYear)
                                if (latestRelease.trackCount > 0) {
                                    if (isNotEmpty()) append(" • ")
                                    append("${latestRelease.trackCount} Lagu")
                                }
                            }
                            if (releaseMeta.isNotBlank()) {
                                Text(
                                    text = releaseMeta,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = CrankrBodyFont,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CrimsonRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Buka Rilisan",
                                tint = TextWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Lagu Teratas Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .background(CrimsonRed, RoundedCornerShape(1.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LAGU TERATAS",
                        color = TextWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CrankrDisplayFont,
                        letterSpacing = 0.5.sp
                    )
                }

                if (tracks.size > 5) {
                    Text(
                        text = if (showAllPopularTracks) "TAMPILKAN LEBIH SEDIKIT" else "LIHAT SEMUA",
                        color = CrimsonRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CrankrDisplayFont,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showAllPopularTracks = !showAllPopularTracks }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 4. Popular Tracks Rows (5 Lagu Teratas atau Semua jika di-toggle)
        itemsIndexed(
            items = displayTracks,
            key = { index, track -> "${track.id}_$index" }
        ) { index, track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTrackClick(track) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}",
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(24.dp)
                )

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.coverUrl)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = if (currentTrack?.id == track.id) CrimsonRed else TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CrankrBodyFont,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.album.ifBlank { track.artist },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = CrankrBodyFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (track.durationSeconds > 0) {
                    Text(
                        text = track.formattedDuration,
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = CrankrBodyFont
                    )
                }
            }
        }

        // 5. Diskografi Header & Category Chips
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CrankrDisplayFont,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${filteredDiscography.size})",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontFamily = CrankrDisplayFont
                    )
                    if (isLoadingDiscography) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            color = CrimsonRed,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Text(
                    text = if (isGridView) "TAMPILAN BARIS" else "LIHAT SEMUA",
                    color = CrimsonRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CrankrDisplayFont,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { isGridView = !isGridView }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            // Chips ("Semua", "Album", "Single & EP")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Semua", "Album", "Single & EP").forEach { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        onClick = { selectedCategory = category },
                        modifier = Modifier.springPressable(scaleDown = 0.94f),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) CrimsonRed else CardBackground,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) CrimsonRed else BorderSubtle
                        )
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) TextWhite else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = CrankrDisplayFont,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 6. Diskografi Display (Row atau Grid 2 kolom)
        if (!isGridView) {
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredDiscography, key = { it.id }) { release ->
                        OfficialDiscographyCard(
                            item = release,
                            onClick = { onAlbumClick(release) }
                        )
                    }
                }
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    filteredDiscography.chunked(2).forEach { rowPair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (release in rowPair) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OfficialDiscographyGridCard(
                                        item = release,
                                        onClick = { onAlbumClick(release) }
                                    )
                                }
                            }
                            if (rowPair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 7. Penggemar Juga Menyukai (Fans Might Also Like)
        if (similarArtists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
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
                        text = "PENGGEMAR JUGA MENYUKAI",
                        color = TextWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CrankrDisplayFont,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(similarArtists, key = { it.id.ifBlank { it.name } }) { artist ->
                        SimilarArtistCard(
                            artist = artist,
                            onClick = { onSimilarArtistClick(artist) }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
private fun OfficialDiscographyCard(
    item: AlbumRelease,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .background(CardBackground)
        ) {
            if (item.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.coverUrl)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
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
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CrankrBodyFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${item.releaseYear} • ${item.releaseType}",
            color = TextSecondary,
            fontSize = 12.sp,
            fontFamily = CrankrBodyFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OfficialDiscographyGridCard(
    item: AlbumRelease,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .background(CardBackground)
        ) {
            if (item.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.coverUrl)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
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
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CrankrBodyFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${item.releaseYear} • ${item.releaseType}",
            color = TextSecondary,
            fontSize = 12.sp,
            fontFamily = CrankrBodyFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SimilarArtistCard(
    artist: SimilarArtist,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(CardBackground)
                .border(1.5.dp, BorderSubtle, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!artist.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artist.photoUrl)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            color = TextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CrankrBodyFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = artist.subtitle.ifBlank { "Artis" },
            color = TextSecondary,
            fontSize = 11.5.sp,
            fontFamily = CrankrBodyFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}