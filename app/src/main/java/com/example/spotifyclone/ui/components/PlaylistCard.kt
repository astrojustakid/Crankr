package com.example.spotifyclone.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifyclone.data.model.SpotifyPlaylist
import com.example.spotifyclone.theme.*

@Composable
fun PlaylistCard(
    playlist: SpotifyPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = modifier
            .width(148.dp)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .springPressable(0.96f)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(9.dp)) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElevatedBackground)
                    .border(0.5.dp, BorderSubtle, RoundedCornerShape(10.dp))
            ) {
                if (!playlist.imageUrl.isNullOrEmpty()) {
                    Crossfade(
                        targetState = playlist.imageUrl,
                        animationSpec = tween(300),
                        label = "PlaylistCardCoverCrossfade"
                    ) { targetImage ->
                        AsyncImage(
                            model = targetImage,
                            contentDescription = playlist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                    )
                }

                // Synced Badge with clean sharp shape
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
                        text = "${playlist.trackCount} LAGU",
                        fontFamily = CrankrBodyFont,
                        color = CrimsonRed,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = playlist.name,
                fontFamily = CrankrBodyFont,
                color = TextWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = if (playlist.ownerName.isNotBlank() && playlist.ownerName != "Crankr") playlist.ownerName else "${playlist.trackCount} lagu",
                fontFamily = CrankrBodyFont,
                color = TextSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
