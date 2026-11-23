package com.example.spotifyclone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spotifyclone.theme.*

data class PlaylistImportProgress(
    val playlistName: String,
    val current: Int,
    val total: Int
)

@Composable
fun PlaylistImportDialog(
    progress: PlaylistImportProgress
) {
    Dialog(
        onDismissRequest = { /* Modal melayang tidak dapat ditutup secara manual saat impor berlangsung */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .border(1.dp, Primary, RoundedCornerShape(16.dp))
                .padding(vertical = 28.dp, horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Animasi loading melingkar berwarna oranye di bagian tengah atas kotak
                CircularProgressIndicator(
                    modifier = Modifier.size(46.dp),
                    color = Primary,
                    strokeWidth = 3.5.dp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Teks nama playlist menggunakan warna putih tepat di bawah animasi loading
                Text(
                    text = progress.playlistName.ifBlank { "Playlist" },
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextWhite,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Teks hitungan progres lagu: IMPORTING $currentIndex OF $totalTracks
                val currentIndex = progress.current
                val totalTracks = progress.total
                val progressText = if (totalTracks > 0) {
                    "IMPORTING $currentIndex OF $totalTracks"
                } else {
                    "MEMPERSIAPKAN DAFTAR LAGU..."
                }

                Text(
                    text = progressText,
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    letterSpacing = 1.2.sp,
                    color = Primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

