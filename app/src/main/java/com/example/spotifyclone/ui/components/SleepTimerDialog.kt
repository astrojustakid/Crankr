package com.example.spotifyclone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
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

data class SleepOption(
    val label: String,
    val minutes: Int?, // null = turn off, -1 = end of song, > 0 = minutes
    val desc: String
)

@Composable
fun SleepTimerDialog(
    currentMinutesRemaining: Int?,
    currentSecondsRemaining: Long? = null,
    currentMode: String?,
    onSetTimer: (Int?) -> Unit,
    onSetEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        SleepOption("15 Menit", 15, "Cocok untuk istirahat sejenak"),
        SleepOption("30 Menit", 30, "Waktu ideal sebelum terlelap tidur"),
        SleepOption("45 Menit", 45, "Putar satu album penuh"),
        SleepOption("60 Menit", 60, "1 jam playlist tanpa jeda"),
        SleepOption("Akhir Lagu Ini", -1, "Berhenti begitu lagu ini selesai diputar"),
        SleepOption("Matikan Timer", null, "Musik terus berputar tanpa batas waktu")
    )

    val formattedRemaining = currentSecondsRemaining?.let { sec ->
        val m = sec / 60
        val s = sec % 60
        "%d:%02d".format(m, s)
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
                        .size(36.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(CrimsonRedSoft)
                        .border(1.dp, CrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "RETRO SLEEP TIMER",
                        fontSize = 17.sp,
                        fontFamily = CrankrDisplayFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TextWhite
                    )
                    Text(
                        text = if (formattedRemaining != null && currentSecondsRemaining > 0) {
                            "Hitung Mundur: $formattedRemaining"
                        } else if (currentMode == "END_OF_TRACK") {
                            "Aktif: Berhenti di akhir lagu"
                        } else {
                            "Otomatis matikan musik sebelum tidur"
                        },
                        fontSize = 12.sp,
                        fontFamily = CrankrBodyFont,
                        color = if (currentSecondsRemaining != null || currentMode != null) Color(0xFFFFE600) else TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Retro Tape Wind-Down Feature Callout
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(PitchBlack)
                        .border(1.dp, CrimsonRed.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFFFFE600),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TAPE WIND-DOWN: Volume akan mengecil perlahan (fade-out) sebelum pemutar berhenti.",
                            fontFamily = CrankrBodyFont,
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                options.forEach { opt ->
                    val isSelected = when (opt.minutes) {
                        null -> currentMinutesRemaining == null && currentMode == null
                        -1 -> currentMode == "END_OF_TRACK"
                        else -> currentMinutesRemaining == opt.minutes && currentMode != "END_OF_TRACK"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) CardBackground else PitchBlack)
                            .border(
                                1.dp,
                                if (isSelected) CrimsonRed else BorderSubtle,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable {
                                when (opt.minutes) {
                                    null -> onCancelTimer()
                                    -1 -> onSetEndOfTrack()
                                    else -> onSetTimer(opt.minutes)
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = opt.label,
                                    color = if (isSelected) CrimsonRed else TextWhite,
                                    fontFamily = CrankrDisplayFont,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (isSelected && formattedRemaining != null && opt.minutes != null && opt.minutes > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "[$formattedRemaining]",
                                        fontFamily = CrankrDisplayFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFE600)
                                    )
                                }
                            }
                            Text(
                                text = opt.desc,
                                color = TextSecondary,
                                fontFamily = CrankrBodyFont,
                                fontSize = 11.sp
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(CrimsonRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = TextWhite,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    text = "TUTUP",
                    fontFamily = CrankrDisplayFont,
                    color = TextSecondary
                )
            }
        },
        dismissButton = {}
    )
}
