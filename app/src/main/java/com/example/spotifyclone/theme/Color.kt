package com.example.spotifyclone.theme

import androidx.compose.ui.graphics.Color

// Crankr Design Tokens (Pure OLED Obsidian & Electric Crimson)
val Primary = Color(0xFFFF2A4D) // Electric Neon Crimson Red
val Secondary = Color(0xFFFFFFFF) // Crisp Pure White
val Tertiary = Color(0xFFA1A1AA) // Cool Slate Gray
val Neutral = Color(0xFF000000) // True OLED Pitch Black
val Background = Neutral // #000000
val Surface = Color(0xFF101014) // Deep Obsidian Surface
val CardSurface = Color(0xFF16161C) // Elevated Card Surface without harsh borders
val Border = Color(0xFF222228) // Ultra-subtle divider
val BorderSubtle = Color(0xFF1A1A20)
val BorderActive = Primary // #FF2A4D

// Surface & Background Mapping
val PitchBlack = Neutral // #000000
val DarkCharcoal = Surface // #101014
val CardBackground = CardSurface // #16161C
val ElevatedBackground = Color(0xFF1E1E26)

// Primary Accents (Electric Crimson Neon)
val CrimsonRed = Primary // #FF2A4D
val PrimaryBright = Color(0xFFFF5271)
val PrimaryDark = Color(0xFFD61839)
val CrimsonRedDark = PrimaryDark
val CrimsonRedSoft = Color(0x33FF2A4D)
val CrimsonRedBadge = Primary
val CrankrAmber = Color(0xFFFFB300)

// Typography Colors
val TextWhite = Color(0xFFFFFFFF) // 100% Crisp White
val TextPrimary = TextWhite
val TextSecondary = Tertiary // Cool Slate Gray #A1A1AA
val TextMuted = Color(0xFF71717A) // Subdued Gray

// Palette mappings for clean compatibility
val MidnightDark = PitchBlack
val MidnightSlate = DarkCharcoal
val MidnightCard = CardBackground
val MidnightElevated = ElevatedBackground
val MidnightBorder = BorderSubtle

val AuroraViolet = Primary
val AuroraVioletLight = Primary
val AuroraCyan = Primary
val AuroraCyanDark = CrimsonRedDark
val AuroraRose = Primary
val AuroraPink = Primary
val AuroraEmerald = Primary

val SpotifyGreen = Primary
val SpotifyGreenPressed = CrimsonRedDark
val SpotifyBlack = PitchBlack
val SpotifyDarkBackground = DarkCharcoal
val SpotifyCardBackground = CardBackground
val SpotifyElevated = ElevatedBackground
val SpotifyLightGrey = BorderSubtle
val SpotifyTextPrimary = TextPrimary
val SpotifyTextSecondary = TextSecondary
val SpotifyTextMuted = TextMuted
val SpotifyRed = Primary
