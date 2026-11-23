package com.example.spotifyclone.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifyclone.theme.*

@Composable
fun BackupRestoreDialog(
    playlistsCount: Int,
    onGetBackupJson: suspend () -> String,
    onRestoreJson: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Backup, 1 = Restore
    var backupJsonString by remember { mutableStateOf("") }
    var restoreInput by remember { mutableStateOf("") }
    var isLoadingBackup by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 && backupJsonString.isEmpty()) {
            isLoadingBackup = true
            backupJsonString = onGetBackupJson()
            isLoadingBackup = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(4.dp),
        containerColor = DarkCharcoal,
        titleContentColor = TextWhite,
        textContentColor = TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CrimsonRedSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "BACKUP & RESTORE",
                        fontSize = 18.sp,
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = TextWhite
                    )
                    Text(
                        text = "Simpan atau pulihkan cadangan playlist Anda",
                        fontSize = 12.sp,
                        fontFamily = CrankrBodyFont,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                // Segmented Tabs: Backup vs Restore
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(PitchBlack)
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selectedTab == 0) CardBackground else PitchBlack)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cadangkan (Backup)",
                            fontSize = 12.5.sp,
                            fontFamily = CrankrBodyFont,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) CrimsonRed else TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selectedTab == 1) CardBackground else PitchBlack)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pulihkan (Restore)",
                            fontSize = 12.5.sp,
                            fontFamily = CrankrBodyFont,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) CrimsonRed else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // Backup Tab Content
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Total $playlistsCount playlist siap dicadangkan. Salin atau bagikan data ini agar koleksi lagu Anda aman saat berganti perangkat.",
                            color = TextSecondary,
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.5.sp
                        )

                        if (isLoadingBackup) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = CrimsonRed, modifier = Modifier.size(24.dp))
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(backupJsonString))
                                },
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = TextWhite
                                ),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = TextWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SALIN TEKS KE CLIPBOARD", color = TextWhite, fontFamily = CrankrDisplayFont, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, backupJsonString)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Simpan Cadangan Playlist")
                                    context.startActivity(shareIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = TextWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("BAGIKAN CADANGAN", color = TextWhite, fontFamily = CrankrDisplayFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // Restore Tab Content
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Tempel teks cadangan JSON yang pernah Anda salin sebelumnya untuk memulihkan seluruh playlist Anda:",
                            color = TextSecondary,
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.5.sp
                        )

                        OutlinedTextField(
                            value = restoreInput,
                            onValueChange = { restoreInput = it },
                            placeholder = { Text("Tempel data JSON di sini...", color = TextMuted, fontSize = 12.sp, fontFamily = CrankrBodyFont) },
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrBlank()) {
                                            restoreInput = clipText
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = CrimsonRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = PitchBlack,
                                unfocusedContainerColor = PitchBlack,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = CrimsonRed,
                                unfocusedBorderColor = BorderSubtle
                            )
                        )

                        Button(
                            onClick = {
                                if (restoreInput.isNotBlank()) {
                                    onRestoreJson(restoreInput.trim())
                                    onDismiss()
                                }
                            },
                            enabled = restoreInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonRed,
                                disabledContainerColor = CrimsonRed.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("PULIHKAN PLAYLIST SEKARANG", color = TextWhite, fontFamily = CrankrDisplayFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(2.dp)
            ) {
                Text("TUTUP", color = TextSecondary, fontFamily = CrankrDisplayFont)
            }
        },
        dismissButton = {}
    )
}
