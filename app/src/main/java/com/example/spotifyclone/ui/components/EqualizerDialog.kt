package com.example.spotifyclone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifyclone.theme.*

data class EqPresetItem(
    val name: String,
    val description: String,
    val iconDesc: String
)

@Composable
fun EqualizerDialog(
    selectedPreset: String,
    onSelectPreset: (String) -> Unit,
    bassGain: Float = 0.5f,
    onBassGainChanged: ((Float) -> Unit)? = null,
    trebleGain: Float = 0.5f,
    onTrebleGainChanged: ((Float) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val presets = listOf(
        EqPresetItem("Flat", "Suara standar asli tanpa efek tambahan", "ORIGINAL"),
        EqPresetItem("Bass Boost", "Dentingan bass bertenaga & low-end tebal", "BASS++"),
        EqPresetItem("Rock", "Distorsi gitar tajam & drum bertenaga", "ROCK"),
        EqPresetItem("Pop", "Vokal jernih dengan ritme cerah & seimbang", "POP"),
        EqPresetItem("Vocal", "Fokus kejernihan suara penyanyi & lirik", "VOCAL"),
        EqPresetItem("Acoustic", "Petikan senar gitar hangat & natural", "ACOUSTIC")
    )

    val accentColor = Primary

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Surface,
        titleContentColor = TextWhite,
        textContentColor = TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AUDIO EQUALIZER",
                        fontSize = 17.sp,
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TextWhite
                    )
                    Text(
                        text = "DSP Hardware Audio & Filter Frekuensi",
                        fontSize = 11.sp,
                        fontFamily = CrankrBodyFont,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                // Section: Hardware Sliders (Bass & Treble)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Neutral)
                            .border(1.dp, Border, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "KONTROL FREKUENSI REAL-TIME",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            letterSpacing = 0.5.sp
                        )

                        // Bass Slider (Band 0 / Low-End)
                        Column {
                            val bassDb = (bassGain - 0.5f) * 30f
                            val bassText = String.format(java.util.Locale.US, "%+.1f dB", bassDb)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BASS BOOST (LOW-END)",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Text(
                                    text = bassText,
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (bassGain > 0.52f) accentColor else TextSecondary
                                )
                            }
                            Slider(
                                value = bassGain,
                                onValueChange = { onBassGainChanged?.invoke(it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = Surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                            )
                        }

                        // Treble Slider (Band 4 / High-End)
                        Column {
                            val trebleDb = (trebleGain - 0.5f) * 30f
                            val trebleText = String.format(java.util.Locale.US, "%+.1f dB", trebleDb)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TREBLE / CLARITY (HIGH-END)",
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Text(
                                    text = trebleText,
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (trebleGain > 0.52f) accentColor else TextSecondary
                                )
                            }
                            Slider(
                                value = trebleGain,
                                onValueChange = { onTrebleGainChanged?.invoke(it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = Surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                            )
                        }
                    }
                }

                // Section: Presets Header
                item {
                    Text(
                        text = "PRESET KARAKTER SUARA",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Section: Preset List Items
                items(presets, key = { it.name }) { preset ->
                    val isSelected = selectedPreset.equals(preset.name, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Neutral else Surface)
                            .border(
                                1.dp,
                                if (isSelected) accentColor else Border,
                                RoundedCornerShape(10.dp)
                            )
                            .springPressable(0.96f)
                            .clickable { onSelectPreset(preset.name) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = preset.name,
                                    color = if (isSelected) accentColor else TextWhite,
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) accentColor.copy(alpha = 0.2f) else Neutral
                                ) {
                                    Text(
                                        text = preset.iconDesc,
                                        color = if (isSelected) accentColor else TextMuted,
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = preset.description,
                                color = TextSecondary,
                                fontFamily = CrankrBodyFont,
                                fontSize = 11.sp
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(accentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.Black,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black
                ),
                modifier = Modifier.springPressable(0.92f)
            ) {
                Text(
                    text = "SELESAI",
                    fontFamily = CrankrDisplayFont,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    letterSpacing = 0.5.sp
                )
            }
        },
        dismissButton = {}
    )
}
