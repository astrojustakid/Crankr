package com.example.spotifyclone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifyclone.theme.*

@Composable
fun SpotifyImportCard(
    isImporting: Boolean,
    onImportUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(3.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(BorderSubtle)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header Row: Tap to toggle expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(DarkCharcoal)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = CrimsonRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Import Playlist Spotify",
                            fontFamily = CrankrDisplayFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Spotify atau tautan playlist publik",
                            fontFamily = CrankrBodyFont,
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = if (isExpanded) CrimsonRedSoft else CardBackground,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(if (isExpanded) CrimsonRed else BorderSubtle)
                    ),
                    modifier = Modifier.clickable { isExpanded = !isExpanded }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.Add,
                            contentDescription = null,
                            tint = if (isExpanded) CrimsonRed else TextWhite,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isExpanded) "Tutup" else "Impor",
                            fontFamily = CrankrBodyFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isExpanded) CrimsonRed else TextWhite
                        )
                    }
                }
            }

            // Expandable Content: URL Input & Actions
            AnimatedVisibility(
                visible = isExpanded || isImporting,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // URL Input Field
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = {
                            Text(
                                text = "Tempel link Spotify playlist...",
                                fontFamily = CrankrBodyFont,
                                color = TextMuted,
                                fontSize = 12.5.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = if (urlInput.isNotEmpty()) CrimsonRed else TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        trailingIcon = {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { urlInput = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Hapus",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = PitchBlack,
                            unfocusedContainerColor = PitchBlack,
                            focusedBorderColor = CrimsonRed,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = CrimsonRed
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons: Paste & Import
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrBlank()) {
                                    urlInput = clipText.trim()
                                }
                            },
                            shape = RoundedCornerShape(2.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BorderSubtle)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardBackground,
                                contentColor = TextWhite
                            ),
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tempel",
                                fontFamily = CrankrBodyFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextWhite
                            )
                        }

                        Button(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    onImportUrl(urlInput.trim())
                                    urlInput = ""
                                    isExpanded = false
                                }
                            },
                            enabled = urlInput.isNotBlank() && !isImporting,
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonRed,
                                disabledContainerColor = CrimsonRed.copy(alpha = 0.3f),
                                contentColor = TextWhite,
                                disabledContentColor = TextWhite.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    color = TextWhite,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Memproses...",
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                            } else {
                                Text(
                                    text = "Impor & Putar",
                                    fontFamily = CrankrBodyFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
