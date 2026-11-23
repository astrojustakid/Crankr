package com.example.spotifyclone.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.theme.*

/**
 * QueueBottomSheet:
 * Komponen pengelolaan antrean putar interaktif dengan gaya zine punk rock minimalis.
 * - Mendukung gestur seret (Drag & Drop) untuk mengatur ulang urutan lagu secara instan.
 * - Mendukung gestur usap (Swipe-to-Dismiss) ke kiri/kanan untuk menghapus lagu dari antrean.
 * - Terintegrasi atomik dengan PlayerViewModel dan ExoPlayer moveMediaItem / removeMediaItem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    currentTrack: Track?,
    playbackQueue: List<Track>,
    queueTitle: String,
    isPlaying: Boolean,
    onTrackClick: (Track) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onClearQueue: () -> Unit,
    onReorderTracks: ((String, String) -> Unit)? = null,
    onReorderQueue: ((Int, Int) -> Unit)? = null,
    onMoveTrackToTop: ((String) -> Unit)? = null,
    onMoveTrackUp: ((String) -> Unit)? = null,
    onMoveTrackDown: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val upcomingTracks = remember(playbackQueue, currentTrack?.id) {
        val curIdx = playbackQueue.indexOfFirst { it.id == currentTrack?.id }
        if (curIdx >= 0) {
            playbackQueue.drop(curIdx + 1)
        } else {
            playbackQueue.filter { it.id != currentTrack?.id }
        }
    }
    val currentUpcomingTracks by rememberUpdatedState(upcomingTracks)
    val currentOnReorderTracks by rememberUpdatedState(onReorderTracks)

    var draggedTrackId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val itemHeightPx = with(density) { 66.dp.toPx() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetGesturesEnabled = draggedTrackId == null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = DarkCharcoal,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = TextMuted)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // Header: Title & Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "ANTREAN PUTAR",
                            fontSize = 18.sp,
                            fontFamily = CrankrDisplayFont,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = TextWhite
                        )
                        Text(
                            text = queueTitle,
                            fontSize = 12.sp,
                            fontFamily = CrankrBodyFont,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playbackQueue.size > 1) {
                        TextButton(
                            onClick = onClearQueue,
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.springPressable(0.92f)
                        ) {
                            Text(
                                text = "BERSIHKAN",
                                color = CrimsonRed,
                                fontFamily = CrankrDisplayFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ElevatedBackground)
                            .springPressable(0.90f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Section: Now Playing dengan Swipe-to-Dismiss
                if (currentTrack != null) {
                    item(key = "header_now_playing") {
                        Text(
                            text = "SEDANG DIPUTAR",
                            fontSize = 13.sp,
                            fontFamily = CrankrDisplayFont,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    item(key = "current_${currentTrack.id}") {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CrimsonRed.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ElevatedBackground)
                                ) {
                                    AsyncImage(
                                        model = currentTrack.coverUrl,
                                        contentDescription = currentTrack.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentTrack.title,
                                        color = TextWhite,
                                        fontFamily = CrankrDisplayFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentTrack.artist,
                                        color = Primary,
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .padding(end = 6.dp)
                                )
                                IconButton(
                                    onClick = { onRemoveTrack(currentTrack.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus lagu yang sedang diputar",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Section: Next in Queue
                item(key = "header_upcoming") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BERIKUTNYA DALAM ANTREAN",
                            fontSize = 13.sp,
                            fontFamily = CrankrDisplayFont,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${upcomingTracks.size} lagu",
                            fontSize = 11.5.sp,
                            fontFamily = CrankrBodyFont,
                            color = TextMuted
                        )
                    }
                }

                if (upcomingTracks.isEmpty()) {
                    item(key = "empty_upcoming") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tidak ada lagu berikutnya di antrean",
                                color = TextMuted,
                                fontFamily = CrankrBodyFont,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(upcomingTracks, key = { it.id }) { track ->
                        val index = upcomingTracks.indexOfFirst { it.id == track.id }
                        val isDragging = draggedTrackId == track.id

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.StartToEnd || dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    onRemoveTrack(track.id)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = !isDragging,
                            enableDismissFromEndToStart = !isDragging,
                            backgroundContent = {
                                val direction = dismissState.dismissDirection
                                val alignment = when (direction) {
                                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                    else -> Alignment.Center
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(CrimsonRed.copy(alpha = 0.9f))
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = alignment
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus",
                                            tint = TextWhite,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "HAPUS",
                                            fontFamily = CrankrDisplayFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = TextWhite,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .zIndex(if (isDragging) 10f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else 0f
                                    scaleX = if (isDragging) 1.02f else 1f
                                    scaleY = if (isDragging) 1.02f else 1f
                                }
                                .animateItem(
                                    fadeInSpec = tween(220),
                                    fadeOutSpec = tween(200),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                        ) {
                            Card(
                                shape = RoundedCornerShape(3.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDragging) CardBackground.copy(alpha = 0.95f) else CardBackground
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (isDragging) 8.dp else 0.dp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = if (isDragging) CrimsonRed else BorderSubtle,
                                        shape = RoundedCornerShape(3.dp)
                                    )
                                    .clickable { onTrackClick(track) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(ElevatedBackground)
                                    ) {
                                        AsyncImage(
                                            model = track.coverUrl,
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            color = TextWhite,
                                            fontFamily = CrankrDisplayFont,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist,
                                            color = TextSecondary,
                                            fontFamily = CrankrBodyFont,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Tombol Aksi Cepat: Paling Atas
                                    if (onMoveTrackToTop != null && index > 0) {
                                        IconButton(
                                            onClick = { onMoveTrackToTop(track.id) },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .springPressable(0.88f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VerticalAlignTop,
                                                contentDescription = "Paling Atas / Putar Berikutnya",
                                                tint = CrimsonRed,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }

                                    // Tombol Geser Satu Langkah ke Atas
                                    if (onMoveTrackUp != null) {
                                        IconButton(
                                            onClick = { onMoveTrackUp(track.id) },
                                            enabled = index > 0,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .springPressable(0.88f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Pindah ke Atas",
                                                tint = if (index > 0) TextSecondary else TextMuted.copy(alpha = 0.25f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Tombol Geser Satu Langkah ke Bawah
                                    if (onMoveTrackDown != null) {
                                        IconButton(
                                            onClick = { onMoveTrackDown(track.id) },
                                            enabled = index < upcomingTracks.size - 1,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .springPressable(0.88f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Pindah ke Bawah",
                                                tint = if (index < upcomingTracks.size - 1) TextSecondary else TextMuted.copy(alpha = 0.25f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Tombol Drag Handle Interaktif (Seret & Lepas / Drag to Reorder)
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .pointerInput(track.id) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                                                    down.consume()
                                                    draggedTrackId = track.id
                                                    dragOffsetY = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                                    while (true) {
                                                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                        if (!change.pressed) {
                                                            break
                                                        }
                                                        val dragDeltaY = change.position.y - change.previousPosition.y
                                                        change.consume()
                                                        dragOffsetY += dragDeltaY

                                                        val tracks = currentUpcomingTracks
                                                        val currentIdx = tracks.indexOfFirst { it.id == (draggedTrackId ?: track.id) }
                                                        val threshold = itemHeightPx * 0.55f

                                                        if (dragOffsetY > threshold && currentIdx in 0 until tracks.size - 1) {
                                                            val targetTrack = tracks[currentIdx + 1]
                                                            currentOnReorderTracks?.invoke(track.id, targetTrack.id)
                                                            dragOffsetY -= itemHeightPx
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        } else if (dragOffsetY < -threshold && currentIdx > 0 && currentIdx < tracks.size) {
                                                            val targetTrack = tracks[currentIdx - 1]
                                                            currentOnReorderTracks?.invoke(track.id, targetTrack.id)
                                                            dragOffsetY += itemHeightPx
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }
                                                    draggedTrackId = null
                                                    dragOffsetY = 0f
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Seret untuk atur urutan",
                                            tint = if (isDragging) CrimsonRed else TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Tombol Hapus Eksplisit
                                    IconButton(
                                        onClick = { onRemoveTrack(track.id) },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .springPressable(0.88f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hapus dari antrean",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
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
}
