package com.example.spotifyclone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.spotifyclone.theme.*

@Composable
fun CrossfadeDialog(
    currentDurationSeconds: Int,
    onSelectDuration: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        0 to "Nonaktif (0 Detik)",
        3 to "Cepat (3 Detik)",
        5 to "Rekomendasi (5 Detik)",
        8 to "Mulus (8 Detik)",
        12 to "Konser / Ambient (12 Detik)"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.96f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(androidx.compose.foundation.BorderStroke(0.8.dp, glassHairlineBrush()), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Crossfade & Gapless",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Text(
                            text = "Transisi mulus tanpa jeda hening antar lagu",
                            fontFamily = CrankrBodyFont,
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }

                    GlassIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Tutup",
                        onClick = onDismiss,
                        size = 32.dp,
                        iconSize = 16.dp,
                        iconTint = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Options list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (seconds, label) ->
                        val isSelected = currentDurationSeconds == seconds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSelected) CrimsonRed.copy(alpha = 0.15f) else CardBackground)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) CrimsonRed else BorderSubtle,
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .clickable {
                                    onSelectDuration(seconds)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontFamily = CrankrDisplayFont,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextWhite else TextSecondary
                            )

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(CrimsonRed)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "TUTUP",
                        fontFamily = CrankrDisplayFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
