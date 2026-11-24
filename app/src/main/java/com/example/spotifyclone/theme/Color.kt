package com.example.spotifyclone.theme

import androidx.compose.ui.graphics.Color

// Crankr Design Tokens — KINETIC MONO (Ultra-Minimalist Art Gallery)
val Primary = Color(0xFFFAFAFA)           // Chalk White (Primary Accent & CTA)
val OnPrimary = Color(0xFF09090B)         // Carbon Black (Crisp Ink on Chalk White)
val Secondary = Color(0xFFF4F4F5)         // Soft Chalk White
val Tertiary = Color(0xFF8B8B94)          // Ash Grey
val Neutral = Color(0xFF0D0D0F)           // Graphite Matte Canvas
val Background = Neutral                  // #0D0D0F
val Surface = Color(0xFF141417)           // Matte Slate Surface
val CardSurface = Color(0xFF18181C)       // Elevated Gallery Card Surface
val Border = Color(0xFF27272A)            // Hairline Architectural Divider
val BorderSubtle = Color(0xFF1F1F24)      // Ultra-subtle Divider
val BorderActive = Primary                // #FAFAFA

// Surface & Background Mapping
val PitchBlack = Neutral                  // #0D0D0F
val DarkCharcoal = Surface                // #141417
val CardBackground = CardSurface          // #18181C
val ElevatedBackground = Color(0xFF202026)

// Primary Accents (Kinetic Mono Chalk & Zinc)
val CrimsonRed = Primary                  // #FAFAFA (Kinetic Mono Chalk Accent)
val PrimaryBright = Color(0xFFFFFFFF)     // Pure Highlight
val PrimaryDark = Color(0xFFD4D4D8)       // Muted Zinc Accent
val CrimsonRedDark = Color(0xFF27272A)    // Dark Container Accent
val CrimsonRedSoft = Color(0x1FFAFAFA)    // Subtle 12% Chalk Tint
val CrimsonRedBadge = Primary
val CrankrAmber = Color(0xFFA1A1AA)       // Silver Metallic Secondary

// Typography Colors
val TextWhite = Color(0xFFFAFAFA)         // Crisp Chalk White
val TextPrimary = TextWhite
val TextSecondary = Tertiary              // Ash Grey #8B8B94
val TextMuted = Color(0xFF63636B)         // Subdued Graphite Grey

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
