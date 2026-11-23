package com.example.spotifyclone.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spotifyclone.data.model.ListenTogetherRoomState
import com.example.spotifyclone.data.model.RoomRole
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.theme.*

@Composable
fun ListenTogetherDialog(
    roomState: ListenTogetherRoomState,
    currentTrack: Track?,
    pendingSuggestions: List<Pair<Track, String>>,
    autoApproveSuggestions: Boolean,
    onCreateRoom: (hostName: String) -> Unit,
    onJoinRoom: (roomId: String, guestName: String) -> Unit,
    onLeaveRoom: () -> Unit,
    onToggleAutoApprove: (Boolean) -> Unit,
    onSuggestCurrentTrack: () -> Unit,
    onApproveSuggestion: (Track) -> Unit,
    onRejectSuggestion: (trackId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0: Host, 1: Guest
    var hostNameInput by remember { mutableStateOf("Rebel Host") }
    var guestNameInput by remember { mutableStateOf("Punk Listener") }
    var roomCodeInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(DarkCharcoal)
                .border(1.dp, BorderSubtle, RoundedCornerShape(3.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(CrimsonRedSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = CrimsonRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "LISTEN TOGETHER",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Sinkronisasi Musik Real-time",
                                fontFamily = CrankrBodyFont,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Ruangan Aktif
                if (roomState.role != RoomRole.NONE) {
                    ActiveRoomContent(
                        roomState = roomState,
                        currentTrack = currentTrack,
                        pendingSuggestions = pendingSuggestions,
                        autoApproveSuggestions = autoApproveSuggestions,
                        onToggleAutoApprove = onToggleAutoApprove,
                        onSuggestCurrentTrack = onSuggestCurrentTrack,
                        onApproveSuggestion = onApproveSuggestion,
                        onRejectSuggestion = onRejectSuggestion,
                        onLeaveRoom = onLeaveRoom,
                        onCopyCode = { code ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Crankr Room Code", code))
                            Toast.makeText(context, "Kode ruangan $code disalin!", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    // Tab Selector: Buat vs Gabung
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(CardBackground)
                            .padding(3.dp)
                    ) {
                        TabButton(
                            title = "BUAT RUANGAN",
                            selected = activeTab == 0,
                            modifier = Modifier.weight(1f),
                            onClick = { activeTab = 0 }
                        )
                        TabButton(
                            title = "GABUNG SESI",
                            selected = activeTab == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { activeTab = 1 }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (activeTab == 0) {
                        // Create Host Room Form
                        Text(
                            text = "NAMA HOST",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = hostNameInput,
                            onValueChange = { hostNameInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonRed,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = CardBackground,
                                unfocusedContainerColor = CardBackground
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (currentTrack != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(CardBackground)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(3.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentTrack.title,
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentTrack.artist,
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Button(
                            onClick = {
                                onCreateRoom(hostNameInput.ifBlank { "Host" })
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Podcasts,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BUAT RUANGAN SEKARANG",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                letterSpacing = 1.sp
                            )
                        }
                    } else {
                        // Join Guest Room Form
                        Text(
                            text = "KODE RUANGAN (6 KARAKTER)",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = roomCodeInput,
                            onValueChange = { roomCodeInput = it.uppercase() },
                            placeholder = { Text("Contoh: CRK-492", color = TextMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonRed,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = CardBackground,
                                unfocusedContainerColor = CardBackground
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "NAMA PENDENGAR (GUEST)",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = guestNameInput,
                            onValueChange = { guestNameInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonRed,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = CardBackground,
                                unfocusedContainerColor = CardBackground
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (roomCodeInput.isNotBlank()) {
                                    onJoinRoom(roomCodeInput.trim(), guestNameInput.ifBlank { "Guest" })
                                } else {
                                    Toast.makeText(context, "Masukkan kode ruangan!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = roomCodeInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonRed,
                                disabledContainerColor = CardBackground
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GABUNG RUANGAN",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveRoomContent(
    roomState: ListenTogetherRoomState,
    currentTrack: Track?,
    pendingSuggestions: List<Pair<Track, String>>,
    autoApproveSuggestions: Boolean,
    onToggleAutoApprove: (Boolean) -> Unit,
    onSuggestCurrentTrack: () -> Unit,
    onApproveSuggestion: (Track) -> Unit,
    onRejectSuggestion: (String) -> Unit,
    onLeaveRoom: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    val isHost = roomState.role == RoomRole.HOST

    Column(modifier = Modifier.fillMaxWidth()) {
        // Status Badge Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = if (isHost) CrimsonRedSoft else Color(0x3300C853)
            ) {
                Text(
                    text = if (isHost) "HOST RUANGAN" else "PENDENGAR (GUEST)",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHost) CrimsonRed else Color(0xFF00C853),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (roomState.isConnected) Color(0xFF00C853) else CrimsonRed)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${roomState.memberCount} PENDENGAR",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Kode Ruangan Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(CardBackground)
                .border(1.dp, BorderSubtle, RoundedCornerShape(3.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "KODE RUANGAN",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = roomState.roomId,
                        fontFamily = CrankrDisplayFont,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        letterSpacing = 2.sp
                    )
                }

                OutlinedButton(
                    onClick = { onCopyCode(roomState.roomId) },
                    shape = RoundedCornerShape(3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SALIN",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Lagu yang Sedang Diputar Bersama
        val trackTitle = roomState.currentTrackTitle ?: currentTrack?.title ?: "Tidak ada lagu"
        val trackArtist = roomState.currentTrackArtist ?: currentTrack?.artist ?: "-"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(CardBackground)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = CrimsonRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackTitle,
                    fontFamily = CrankrBodyFont,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = trackArtist,
                    fontFamily = CrankrBodyFont,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Opsi Host: Auto-approve saran lagu
        if (isHost) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(CardBackground)
                    .clickable { onToggleAutoApprove(!autoApproveSuggestions) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Otomatis Putar Saran Lagu",
                        fontFamily = CrankrBodyFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Text(
                        text = "Langsung putar lagu yang disarankan pendengar",
                        fontFamily = CrankrBodyFont,
                        fontSize = 10.5.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = autoApproveSuggestions,
                    onCheckedChange = onToggleAutoApprove,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = CrimsonRed,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkCharcoal
                    )
                )
            }

            // Antrean Saran Lagu dari Pendengar
            AnimatedVisibility(visible = pendingSuggestions.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "SARAN PENDENGAR (${pendingSuggestions.size})",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    pendingSuggestions.forEach { (track, sender) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(CardBackground)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(3.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Dari: $sender",
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = { onApproveSuggestion(track) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Putar",
                                        tint = Color(0xFF00C853),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRejectSuggestion(track.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tolak",
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        } else {
            // Guest Action: Sarankan lagu ke host
            OutlinedButton(
                onClick = onSuggestCurrentTrack,
                shape = RoundedCornerShape(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SARANKAN LAGU KE HOST",
                    fontFamily = CrankrDisplayFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tombol Keluar / Tutup Ruangan
        Button(
            onClick = onLeaveRoom,
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = null,
                tint = CrimsonRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isHost) "TUTUP RUANGAN" else "KELUAR RUANGAN",
                fontFamily = CrankrDisplayFont,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = CrimsonRed,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun TabButton(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) CrimsonRed else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontFamily = CrankrDisplayFont,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) TextWhite else TextMuted,
            letterSpacing = 0.5.sp
        )
    }
}
