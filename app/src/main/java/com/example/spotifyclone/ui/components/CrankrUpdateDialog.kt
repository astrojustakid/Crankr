package com.example.spotifyclone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spotifyclone.service.update.CrankrUpdateInfo
import com.example.spotifyclone.service.update.CrankrUpdateManager
import com.example.spotifyclone.service.update.UpdateState
import com.example.spotifyclone.theme.*

/**
 * CrankrUpdateDialog:
 * Dialog pembaruan aplikasi OTA terpadu bergaya industrial punk rock.
 */
@Composable
fun CrankrUpdateDialog(
    updateState: UpdateState,
    updateManager: CrankrUpdateManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Jangan tampilkan jika Idle atau Checking tanpa update
    if (updateState is UpdateState.Idle || updateState is UpdateState.Checking) {
        return
    }

    val isMandatory = (updateState as? UpdateState.UpdateAvailable)?.info?.mandatory == true

    Dialog(
        onDismissRequest = {
            if (!isMandatory && updateState !is UpdateState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isMandatory && updateState !is UpdateState.Downloading,
            dismissOnClickOutside = !isMandatory && updateState !is UpdateState.Downloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.2.dp, CrimsonRed.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header Bar dengan Neon Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(18.dp)
                                .background(CrimsonRed, RoundedCornerShape(1.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (updateState) {
                                is UpdateState.ReadyToInstall -> "SIAP DIPASANG"
                                is UpdateState.Downloading -> "MENGUNDUH PEMBARUAN"
                                is UpdateState.Error -> "KENDALA UPDATE"
                                is UpdateState.UpToDate -> "VERSI TERBARU"
                                else -> "PEMBARUAN TERSEDIA"
                            },
                            fontFamily = CrankrDisplayFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        color = CrimsonRed.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, CrimsonRed.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "OTA",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Isi Dialog berdasarkan status
                when (updateState) {
                    is UpdateState.UpdateAvailable -> {
                        val info = updateState.info
                        Text(
                            text = "Versi Baru: ${info.versionName}",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonRed
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "CATATAN RILIS:",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Box Changelog
                        Surface(
                            color = Surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = info.changelog.ifBlank { "Peningkatan performa, pembaruan audio engine, dan perbaikan bug." },
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 12.5.sp,
                                    color = TextWhite,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Tombol Aksi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!info.mandatory) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "NANTI",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = { updateManager.startDownload(info) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CrimsonRed,
                                    contentColor = TextWhite
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "UPDATE SEKARANG",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        val progressPct = (updateState.progress * 100).toInt()
                        val downloadedMb = updateState.downloadedBytes / (1024f * 1024f)
                        val totalMb = updateState.totalBytes / (1024f * 1024f)

                        Text(
                            text = "Mengunduh file paket crankr.apk...",
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.5.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { updateState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CrimsonRed,
                            trackColor = Surface
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$progressPct%",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonRed
                            )
                            if (totalMb > 0f) {
                                Text(
                                    text = String.format("%.1f MB / %.1f MB", downloadedMb, totalMb),
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 11.5.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    is UpdateState.ReadyToInstall -> {
                        val apk = updateState.apkFile
                        Text(
                            text = "File crankr.apk berhasil diunduh dan siap dipasang di perangkat Anda.",
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.5.sp,
                            color = TextWhite,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { updateManager.installApk(context, apk) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonRed,
                                contentColor = TextWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "PASANG SEKARANG",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    is UpdateState.UpToDate -> {
                        Text(
                            text = "Crankr v${updateState.currentVersion} Anda sudah versi terbaru.",
                            fontFamily = CrankrBodyFont,
                            fontSize = 13.sp,
                            color = TextWhite
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Surface),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "TUTUP",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                    }

                    is UpdateState.Error -> {
                        Text(
                            text = updateState.message,
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.5.sp,
                            color = CrimsonRed
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, BorderSubtle),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "TUTUP",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { updateManager.checkForUpdates(isManualCheck = true) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "COBA LAGI",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}
