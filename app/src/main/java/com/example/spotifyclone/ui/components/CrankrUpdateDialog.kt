package com.example.spotifyclone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
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
 * Dialog pembaruan aplikasi OTA terpadu bergaya industrial punk rock modern.
 * Dirancang dengan kontras tinggi, kartu inspeksi paket terverifikasi, dan tipografi tegas.
 */
@Composable
fun CrankrUpdateDialog(
    updateState: UpdateState,
    updateManager: CrankrUpdateManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Jangan tampilkan jika Idle atau Checking tanpa hasil
    if (updateState is UpdateState.Idle || updateState is UpdateState.Checking) {
        return
    }

    val isMandatory = (updateState as? UpdateState.UpdateAvailable)?.info?.mandatory == true
    val isDownloading = updateState is UpdateState.Downloading

    Dialog(
        onDismissRequest = {
            if (!isMandatory && !isDownloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isMandatory && !isDownloading,
            dismissOnClickOutside = !isMandatory && !isDownloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.5.dp, CrimsonRed.copy(alpha = 0.65f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header Dialog Bergaya Industrial
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(22.dp)
                                .background(CrimsonRed, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = when (updateState) {
                                    is UpdateState.ReadyToInstall -> "SIAP DIPASANG"
                                    is UpdateState.Downloading -> "MENGUNDUH PAKET"
                                    is UpdateState.Error -> "KENDALA UPDATE"
                                    is UpdateState.UpToDate -> "CRANKR MUTAKHIR"
                                    else -> "PEMBARUAN TERSEDIA"
                                },
                                fontFamily = CrankrDisplayFont,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "CRANKR INDUSTRIAL OTA",
                                fontFamily = CrankrBodyFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    // Tag Badge & Tombol Close
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = CrimsonRedSoft,
                            border = BorderStroke(0.8.dp, CrimsonRed.copy(alpha = 0.5f)),
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

                        if (!isMandatory && !isDownloading) {
                            Surface(
                                shape = CircleShape,
                                color = Surface,
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier
                                    .size(28.dp)
                                    .springPressable(0.95f)
                                    .clickable { onDismiss() }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Tutup",
                                    tint = TextSecondary,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Isi Dialog berdasarkan status state
                when (updateState) {
                    is UpdateState.ReadyToInstall -> {
                        val apk = updateState.apkFile
                        val info = updateState.info
                        val apkSizeMb = if (apk.exists()) apk.length() / (1024f * 1024f) else 20.2f

                        // Kartu Inspeksi Paket APK
                        Surface(
                            color = Surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.DownloadDone,
                                            contentDescription = null,
                                            tint = CrimsonRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "PAKET APK TERVERIFIKASI",
                                            fontFamily = CrankrDisplayFont,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextWhite,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Surface(
                                        color = Primary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "v${info.versionName}",
                                            fontFamily = CrankrDisplayFont,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OnPrimary,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)
                                Spacer(modifier = Modifier.height(10.dp))

                                // Detail Baris File
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Berkas:",
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = apk.name,
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextWhite
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Ukuran Paket:",
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = String.format("%.1f MB", apkSizeMb),
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CrimsonRed
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Integritas:",
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "Lolos Validasi Android",
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 12.sp,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Sentuh tombol di bawah untuk memasang pembaruan. Seluruh playlist, data offline, dan riwayat dengar Anda tetap aman.",
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Tombol Pasang Utama
                        Button(
                            onClick = { updateManager.installApk(context, apk) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor = OnPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .springPressable(0.95f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PASANG SEKARANG",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .springPressable(0.95f)
                        ) {
                            Text(
                                text = "PASANG NANTI",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    is UpdateState.UpdateAvailable -> {
                        val info = updateState.info

                        Surface(
                            color = Surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "VERSI BARU TERSEDIA",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = info.versionName,
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CrimsonRed
                                    )
                                }
                                Surface(
                                    color = CrimsonRedSoft,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.8.dp, CrimsonRed.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "Build ${info.versionCode}",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "CATATAN RILIS:",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Box Changelog
                        Surface(
                            color = PitchBlack,
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
                                    text = info.changelog.ifBlank { "Peningkatan stabilitas sistem dan pembaruan audio streaming." },
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 12.sp,
                                    color = TextWhite,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

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
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .springPressable(0.95f)
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
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Primary,
                                    contentColor = OnPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .springPressable(0.95f)
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
                            text = "Mengunduh paket instalasi crankr.apk dari server rilis...",
                            fontFamily = CrankrBodyFont,
                            fontSize = 12.5.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress Bar Industri
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$progressPct%",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 15.sp,
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
                            } else {
                                Text(
                                    text = String.format("%.1f MB", downloadedMb),
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 11.5.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    is UpdateState.UpToDate -> {
                        Surface(
                            color = Surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "CRANKR v${updateState.currentVersion}",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = "Aplikasi Anda sudah menggunakan rilis versi paling mutakhir.",
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Surface),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .springPressable(0.95f)
                        ) {
                            Text(
                                text = "MENGERTI",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                    }

                    is UpdateState.Error -> {
                        Surface(
                            color = CrimsonRedSoft,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = updateState.message,
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 12.sp,
                                    color = TextWhite,
                                    lineHeight = 17.sp
                                )
                            }
                        }

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
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .springPressable(0.95f)
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
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Primary,
                                    contentColor = OnPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .springPressable(0.95f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
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
