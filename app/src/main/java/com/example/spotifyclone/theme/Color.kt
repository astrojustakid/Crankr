package com.example.spotifyclone.theme

import androidx.compose.ui.graphics.Color

// Crankr Design Tokens — AURA LIQUID (Spatial Glass & Midnight Depth)
val Primary = Color(0xFF38BDF8)           // Electric Ice Blue (Primary Accent & CTA)
val OnPrimary = Color(0xFF041624)         // Deep Abyss Ink (Crisp Contrast on Ice Blue)
val Secondary = Color(0xFFF8FAFC)         // Pure Pearl White
val Tertiary = Color(0xFF94A3B8)          // Cool Mist Slate
val Neutral = Color(0xFF060B17)           // Deep Midnight Navy Canvas
val Background = Neutral                  // #060B17
val Surface = Color(0xFF0D1629)           // Deep Ocean Glass Surface
val CardSurface = Color(0xFF13203A)       // Frosted Midnight Card Surface
val Border = Color(0xFF24385C)            // Luminous Translucent Glass Edge
val BorderSubtle = Color(0xFF192946)      // Subtle Midnight Divider
val BorderActive = Primary                // #38BDF8

// Surface & Background Mapping
val PitchBlack = Neutral                  // #060B17
val DarkCharcoal = Surface                // #0D1629
val CardBackground = CardSurface          // #13203A
val ElevatedBackground = Color(0xFF192A4A)

// Primary Accents (Aura Liquid Ice & Cerulean)
val CrimsonRed = Primary                  // #38BDF8 (Mapped to Electric Ice Blue)
val PrimaryBright = Color(0xFF7DD3FC)     // Luminous Sky Highlight
val PrimaryDark = Color(0xFF0284C7)       // Deep Cerulean
val CrimsonRedDark = Color(0xFF0E2947)    // Deep Marine Container
val CrimsonRedSoft = Color(0x2438BDF8)    // 14% Translucent Ice Glass Tint
val CrimsonRedBadge = Primary
val CrankrAmber = Color(0xFF7DD3FC)       // Sky Metallic Secondary

// Typography Colors
val TextWhite = Color(0xFFF8FAFC)         // Pure Pearl White
val TextPrimary = TextWhite
val TextSecondary = Tertiary              // Cool Mist Slate #94A3B8
val TextMuted = Color(0xFF64748B)         // Muted Ocean Slate

// Palette mappings for clean compatibility
val MidnightDark = PitchBlack
val MidnightSlate = DarkCharcoal
val MidnightCard = CardBackground
val MidnightElevated = ElevatedBackground
val MidnightBorder = BorderSubtle

val AuroraViolet = Primary
val AuroraVioletLight = PrimaryBright
val AuroraCyan = Primary
val AuroraCyanDark = PrimaryDark
val AuroraRose = Primary
val AuroraPink = Primary
val AuroraEmerald = Primary

val SpotifyGreen = Primary
val SpotifyGreenPressed = PrimaryDark
val SpotifyBlack = PitchBlack
val SpotifyDarkBackground = DarkCharcoal
val SpotifyCardBackground = CardBackground
val SpotifyElevated = ElevatedBackground
val SpotifyLightGrey = BorderSubtle
val SpotifyTextPrimary = TextPrimary
val SpotifyTextSecondary = TextSecondary
val SpotifyTextMuted = TextMuted
val SpotifyRed = Primary
