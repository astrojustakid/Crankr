package com.example.spotifyclone.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spotifyclone.service.AppThemePreset
import com.example.spotifyclone.service.AudioQuality
import com.example.spotifyclone.service.AuralysSettingsManager
import com.example.spotifyclone.service.CrankrAppearanceManager
import com.example.spotifyclone.service.LyricsAlignment
import com.example.spotifyclone.service.LyricsTextScale
import com.example.spotifyclone.service.PlayerBackgroundStyle
import com.example.spotifyclone.theme.*

/**
 * SettingsDialog:
 * Halaman Pengaturan terpadu Crankr & Auralys Engine bergaya Industrial Minimalist Zine:
 * - Pengaturan Audio Engine (Bitrate Streaming, Preamp Gain, Crossfade, Normalisasi Volume, Equalizer).
 * - Manajemen Cache Streaming & Penyimpanan Offline.
 * - Pintasan modul Kolaborasi Listen Together.
 */
@Composable
fun SettingsDialog(
    crossfadeDurationSeconds: Int,
    onSelectCrossfade: (Int) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenListenTogether: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAboutDeveloper: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onPreampChange: ((Float) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { AuralysSettingsManager.getInstance(context) }
    val appearanceManager = remember { CrankrAppearanceManager.getInstance(context) }
    val updateManager = remember { com.example.spotifyclone.service.update.CrankrUpdateManager.getInstance(context) }
    val currentVersionName = remember { updateManager.getCurrentVersionName() }
    val currentVersionCode = remember { updateManager.getCurrentVersionCode() }

    val selectedThemePreset by appearanceManager.themePreset.collectAsState()
    val selectedPlayerBg by appearanceManager.playerBackgroundStyle.collectAsState()
    val miniPlayerTint by appearanceManager.miniPlayerCoverTint.collectAsState()
    val selectedLyricsAlign by appearanceManager.lyricsAlignment.collectAsState()
    val selectedLyricsScale by appearanceManager.lyricsTextScale.collectAsState()

    val isVolumeNormalization by settingsManager.isVolumeNormalizationEnabled.collectAsState()
    val currentAudioQuality by settingsManager.audioQuality.collectAsState()
    val preampGainDb by settingsManager.preampGainDb.collectAsState()
    var cacheSizeFormatted by remember { mutableStateOf(settingsManager.getCacheSizeFormatted()) }

    val accentColor = Primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.96f)),
            border = BorderStroke(0.8.dp, glassHairlineBrush()),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.84f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Dialog Crankr 2.0 Glass
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pengaturan",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Text(
                            text = "Tampilan, audio & penyimpanan",
                            fontFamily = CrankrBodyFont,
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }

                    GlassIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Tutup",
                        onClick = onDismiss,
                        size = 34.dp,
                        iconSize = 16.dp,
                        iconTint = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // SEKSI 0: TAMPILAN & TEMA (AURALYS APPEARANCE)
                    SettingsSection(title = "TAMPILAN & TEMA") {
                        // Theme Preset Switcher (Live)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Neutral, RoundedCornerShape(12.dp))
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "TEMA APLIKASI",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Ganti karakter warna secara langsung tanpa restart",
                                fontFamily = CrankrBodyFont,
                                fontSize = 10.5.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val presetRows = AppThemePreset.entries.chunked(2)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                presetRows.forEach { rowPresets ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowPresets.forEach { preset ->
                                            val isSelected = selectedThemePreset == preset
                                            val dotColor = when (preset) {
                                                AppThemePreset.AURA_LIQUID -> Color(0xFF38BDF8)
                                                AppThemePreset.KINETIC_MONO -> Color(0xFFFAFAFA)
                                                AppThemePreset.VELVET_CRIMSON -> Color(0xFFE50914)
                                                AppThemePreset.MIDNIGHT_JADE -> Color(0xFF10E58B)
                                                AppThemePreset.SUNSET_AMBER -> Color(0xFFF59E0B)
                                                AppThemePreset.SOLARIS_GOLD -> Color(0xFFEAB308)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) accentColor else Surface,
                                                border = BorderStroke(1.dp, if (isSelected) accentColor else Border),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .springPressable(0.96f)
                                                    .clickable { appearanceManager.setThemePreset(preset) }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .background(
                                                                if (isSelected) OnPrimary else dotColor,
                                                                CircleShape
                                                            )
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = preset.label,
                                                        fontFamily = CrankrDisplayFont,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) OnPrimary else TextWhite,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Player Background Style
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Neutral, RoundedCornerShape(12.dp))
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "LATAR NOW PLAYING",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Blur cover album, gradien warna dinamis, atau solid",
                                fontFamily = CrankrBodyFont,
                                fontSize = 10.5.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PlayerBackgroundStyle.entries.forEach { bgStyle ->
                                    val isSelected = selectedPlayerBg == bgStyle
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) accentColor else Surface,
                                        border = BorderStroke(1.dp, if (isSelected) accentColor else Border),
                                        modifier = Modifier
                                            .weight(1f)
                                            .springPressable(0.96f)
                                            .clickable { appearanceManager.setPlayerBackgroundStyle(bgStyle) }
                                    ) {
                                        Text(
                                            text = bgStyle.label,
                                            fontFamily = CrankrDisplayFont,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) OnPrimary else TextSecondary,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Dynamic Cover-Tinted MiniPlayer Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Neutral, RoundedCornerShape(12.dp))
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MINI-PLAYER WARNA COVER",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Text(
                                    text = "Serap warna dominan album ke kapsul mini-player",
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 10.5.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = miniPlayerTint,
                                onCheckedChange = { appearanceManager.setMiniPlayerCoverTint(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OnPrimary,
                                    checkedTrackColor = accentColor,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = Surface
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Lyrics Style (Alignment & Size)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Neutral, RoundedCornerShape(12.dp))
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "GAYA LIRIK SINKRON",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LyricsAlignment.entries.forEach { align ->
                                    val isSelected = selectedLyricsAlign == align
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) accentColor else Surface,
                                        border = BorderStroke(1.dp, if (isSelected) accentColor else Border),
                                        modifier = Modifier
                                            .weight(1f)
                                            .springPressable(0.96f)
                                            .clickable { appearanceManager.setLyricsAlignment(align) }
                                    ) {
                                        Text(
                                            text = "Rata ${align.label}",
                                            fontFamily = CrankrDisplayFont,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) OnPrimary else TextSecondary,
                                            modifier = Modifier.padding(vertical = 7.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LyricsTextScale.entries.forEach { scale ->
                                    val isSelected = selectedLyricsScale == scale
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) accentColor else Surface,
                                        border = BorderStroke(1.dp, if (isSelected) accentColor else Border),
                                        modifier = Modifier
                                            .weight(1f)
                                            .springPressable(0.96f)
                                            .clickable { appearanceManager.setLyricsTextScale(scale) }
                                    ) {
                                        Text(
                                            text = scale.label,
                                            fontFamily = CrankrDisplayFont,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) OnPrimary else TextSecondary,
                                            modifier = Modifier.padding(vertical = 7.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SEKSI 1: AUDIO ENGINE & PEMUTARAN
                    SettingsSection(title = "AUDIO ENGINE") {
                        // Crossfade Duration
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Neutral, RoundedCornerShape(12.dp))
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "CROSSFADE & TRANSISI",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Transisi mulus antar lagu tanpa jeda sunyi",
                                fontFamily = CrankrBodyFont,
                                fontSize = 10.5.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(0, 3, 5, 8, 12).forEach { sec ->
                                    val isSelected = crossfadeDurationSeconds == sec
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) accentColor else Surface,
                                        border = BorderStroke(1.dp, if (isSelected) accentColor else Border),
                                        modifier = Modifier
                                            .weight(1f)
                                            .springPressable(0.96f)
                                            .clickable { onSelectCrossfade(sec) }
                                    ) {
                                        Text(
                                            text = if (sec == 0) "OFF" else "${sec}s",
                                            fontFamily = CrankrDisplayFont,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else TextSecondary,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Normalisasi Volume Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Neutral, RoundedCornerShape(12.dp))
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "NORMALISASI VOLUME",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Text(
                                    text = "Seimbangkan level audio antar track streaming",
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 10.5.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = isVolumeNormalization,
                                onCheckedChange = { settingsManager.setVolumeNormalization(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = accentColor,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = Surface
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Pemilih Kualitas Audio Streaming (Bitrate)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Neutral, RoundedCornerShape(12.dp))
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "KUALITAS AUDIO STREAMING",
                                fontFamily = CrankrDisplayFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Bitrate aliran audio server InnerTube",
                                fontFamily = CrankrBodyFont,
                                fontSize = 10.5.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AudioQuality.values().forEach { quality ->
                                    val isSelected = currentAudioQuality == quality
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) accentColor else Surface,
                                        border = BorderStroke(1.dp, if (isSelected) accentColor else Border),
                                        modifier = Modifier
                                            .weight(1f)
                                            .springPressable(0.96f)
                                            .clickable { settingsManager.setAudioQuality(quality) }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = quality.displayName.uppercase(),
                                                fontFamily = CrankrDisplayFont,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.Black else TextWhite,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Text(
                                                text = quality.bitrateLabel,
                                                fontFamily = CrankrBodyFont,
                                                fontSize = 9.sp,
                                                color = if (isSelected) Color.Black.copy(alpha = 0.85f) else TextSecondary,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Penguat Volume Preamp Audio (0 dB s/d +12 dB)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Neutral, RoundedCornerShape(12.dp))
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "PENGUAT VOLUME PREAMP",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Penguat gain hardware & software tanpa distorsi",
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 10.5.sp,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = if (preampGainDb <= 0.05f) "0 dB" else "+%.1f dB".format(preampGainDb),
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (preampGainDb > 0.05f) accentColor else TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = preampGainDb,
                                onValueChange = { newGain ->
                                    val rounded = (kotlin.math.round(newGain * 2f) / 2f)
                                    settingsManager.setPreampGainDb(rounded)
                                    onPreampChange?.invoke(rounded)
                                },
                                valueRange = 0f..12f,
                                steps = 23,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = Surface,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "0 dB (FLAT)",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 9.5.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "+6 dB",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 9.5.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "+12 dB (MAX BOOST)",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 9.5.sp,
                                    color = if (preampGainDb >= 11.5f) accentColor else TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Equalizer Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Neutral,
                            border = BorderStroke(1.dp, Border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .springPressable(0.97f)
                                .clickable {
                                    onDismiss()
                                    onOpenEqualizer()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "EQUALIZER & TONE CONTROLLER",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                }
                                Text(
                                    text = "ATUR >",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 11.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // SEKSI 2: CACHE & PENYIMPANAN
                    SettingsSection(title = "CACHE & PENYIMPANAN") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Neutral, RoundedCornerShape(12.dp))
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CACHE STREAMING (SIMPLECACHE)",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Text(
                                    text = cacheSizeFormatted,
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val freed = settingsManager.clearStreamingCache()
                                        cacheSizeFormatted = settingsManager.getCacheSizeFormatted()
                                        val mb = freed.toDouble() / (1024 * 1024)
                                        Toast.makeText(context, "Cache dibersihkan (%.1f MB)".format(mb), Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Surface,
                                        contentColor = TextWhite
                                    ),
                                    border = BorderStroke(1.dp, Border),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "BERSIHKAN CACHE",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenDownloads()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Surface,
                                        contentColor = TextWhite
                                    ),
                                    border = BorderStroke(1.dp, Border),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "KELOLA UNDUHAN",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // SEKSI 3: KOLABORASI
                    SettingsSection(title = "Kolaborasi") {
                        GlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .springPressable(0.97f)
                                .clickable {
                                    onDismiss()
                                    onOpenListenTogether()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Listen Together",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = "Dengarkan musik bersama teman secara real-time",
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = "Masuk ›",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // SEKSI 4: TENTANG
                    SettingsSection(title = "Tentang") {
                        GlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .springPressable(0.97f)
                                .clickable {
                                    onDismiss()
                                    onOpenAboutDeveloper()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Tentang Developer",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = "Profil AstroKido & media sosial",
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = "Lihat ›",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // SEKSI 5: PEMBARUAN APLIKASI (OTA)
                    SettingsSection(title = "Pembaruan Sistem") {
                        GlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .springPressable(0.97f)
                                .clickable { onCheckUpdate() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cek Pembaruan Aplikasi",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = "Versi $currentVersionName (Build $currentVersionCode)",
                                        fontFamily = CrankrBodyFont,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = "Periksa ›",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontFamily = CrankrDisplayFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}
