package com.example.spotifyclone.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class CrankrPaletteTokens(
    val primary: Color,
    val onPrimary: Color,
    val primaryBright: Color,
    val primaryDark: Color,
    val primaryContainer: Color,
    val accentSoft: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val cardSurface: Color,
    val elevated: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val homeTopGlow: Color
)

val AuraLiquidPalette = CrankrPaletteTokens(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF041624),
    primaryBright = Color(0xFF7DD3FC),
    primaryDark = Color(0xFF0284C7),
    primaryContainer = Color(0xFF0E2947),
    accentSoft = Color(0x2438BDF8),
    secondary = Color(0xFFF8FAFC),
    tertiary = Color(0xFF94A3B8),
    background = Color(0xFF060B17),
    surface = Color(0xFF0D1629),
    cardSurface = Color(0xFF13203A),
    elevated = Color(0xFF192A4A),
    border = Color(0xFF24385C),
    borderSubtle = Color(0xFF192946),
    textPrimary = Color(0xFFF8FAFC),
    textMuted = Color(0xFF64748B),
    homeTopGlow = Color(0xFF102246)
)

val KineticMonoPalette = CrankrPaletteTokens(
    primary = Color(0xFFFAFAFA),
    onPrimary = Color(0xFF09090B),
    primaryBright = Color(0xFFFFFFFF),
    primaryDark = Color(0xFFD4D4D8),
    primaryContainer = Color(0xFF27272A),
    accentSoft = Color(0x1FFAFAFA),
    secondary = Color(0xFFF4F4F5),
    tertiary = Color(0xFF8B8B94),
    background = Color(0xFF0D0D0F),
    surface = Color(0xFF141417),
    cardSurface = Color(0xFF18181C),
    elevated = Color(0xFF202026),
    border = Color(0xFF27272A),
    borderSubtle = Color(0xFF1F1F24),
    textPrimary = Color(0xFFFAFAFA),
    textMuted = Color(0xFF63636B),
    homeTopGlow = Color(0xFF18181D)
)

val PureOledPalette = CrankrPaletteTokens(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryBright = Color(0xFFFFFFFF),
    primaryDark = Color(0xFFCCCCCC),
    primaryContainer = Color(0xFF1C1C1E),
    accentSoft = Color(0x1FFFFFFF),
    secondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF98989F),
    background = Color(0xFF000000),
    surface = Color(0xFF0B0B0D),
    cardSurface = Color(0xFF121215),
    elevated = Color(0xFF1A1A1E),
    border = Color(0xFF242429),
    borderSubtle = Color(0xFF18181C),
    textPrimary = Color(0xFFFFFFFF),
    textMuted = Color(0xFF63636A),
    homeTopGlow = Color(0xFF000000)
)

val VelvetCrimsonPalette = CrankrPaletteTokens(
    primary = Color(0xFFE50914),
    onPrimary = Color(0xFFFFFFFF),
    primaryBright = Color(0xFFFF4D5A),
    primaryDark = Color(0xFFB81D24),
    primaryContainer = Color(0xFF330C10),
    accentSoft = Color(0x26E50914),
    secondary = Color(0xFFF5F4F0),
    tertiary = Color(0xFF9A9AA6),
    background = Color(0xFF0A0A0C),
    surface = Color(0xFF131317),
    cardSurface = Color(0xFF1A1A20),
    elevated = Color(0xFF22222A),
    border = Color(0xFF2B2B33),
    borderSubtle = Color(0xFF1F1F26),
    textPrimary = Color(0xFFF5F4F0),
    textMuted = Color(0xFF6E6E78),
    homeTopGlow = Color(0xFF240B0E)
)

object CrankrThemeState {
    var activePalette by mutableStateOf(AuraLiquidPalette)

    fun applyPreset(presetId: String) {
        activePalette = when (presetId) {
            "kinetic_mono" -> KineticMonoPalette
            "pure_oled" -> PureOledPalette
            "velvet_crimson" -> VelvetCrimsonPalette
            else -> AuraLiquidPalette
        }
    }
}

// Live-Reactive Crankr Design Tokens (otomatis recompose saat tema diganti di Pengaturan)
val Primary: Color get() = CrankrThemeState.activePalette.primary
val OnPrimary: Color get() = CrankrThemeState.activePalette.onPrimary
val Secondary: Color get() = CrankrThemeState.activePalette.secondary
val Tertiary: Color get() = CrankrThemeState.activePalette.tertiary
val Neutral: Color get() = CrankrThemeState.activePalette.background
val Background: Color get() = CrankrThemeState.activePalette.background
val Surface: Color get() = CrankrThemeState.activePalette.surface
val CardSurface: Color get() = CrankrThemeState.activePalette.cardSurface
val Border: Color get() = CrankrThemeState.activePalette.border
val BorderSubtle: Color get() = CrankrThemeState.activePalette.borderSubtle
val BorderActive: Color get() = Primary
val HomeTopGlow: Color get() = CrankrThemeState.activePalette.homeTopGlow

// Surface & Background Mapping
val PitchBlack: Color get() = Neutral
val DarkCharcoal: Color get() = Surface
val CardBackground: Color get() = CardSurface
val ElevatedBackground: Color get() = CrankrThemeState.activePalette.elevated

// Primary Accents
val CrimsonRed: Color get() = Primary
val PrimaryBright: Color get() = CrankrThemeState.activePalette.primaryBright
val PrimaryDark: Color get() = CrankrThemeState.activePalette.primaryDark
val CrimsonRedDark: Color get() = CrankrThemeState.activePalette.primaryContainer
val CrimsonRedSoft: Color get() = CrankrThemeState.activePalette.accentSoft
val CrimsonRedBadge: Color get() = Primary
val CrankrAmber: Color get() = PrimaryBright

// Typography Colors
val TextWhite: Color get() = CrankrThemeState.activePalette.textPrimary
val TextPrimary: Color get() = TextWhite
val TextSecondary: Color get() = Tertiary
val TextMuted: Color get() = CrankrThemeState.activePalette.textMuted

// Palette mappings for clean compatibility
val MidnightDark: Color get() = PitchBlack
val MidnightSlate: Color get() = DarkCharcoal
val MidnightCard: Color get() = CardBackground
val MidnightElevated: Color get() = ElevatedBackground
val MidnightBorder: Color get() = BorderSubtle

val AuroraViolet: Color get() = Primary
val AuroraVioletLight: Color get() = PrimaryBright
val AuroraCyan: Color get() = Primary
val AuroraCyanDark: Color get() = PrimaryDark
val AuroraRose: Color get() = Primary
val AuroraPink: Color get() = Primary
val AuroraEmerald: Color get() = Primary

val SpotifyGreen: Color get() = Primary
val SpotifyGreenPressed: Color get() = PrimaryDark
val SpotifyBlack: Color get() = PitchBlack
val SpotifyDarkBackground: Color get() = DarkCharcoal
val SpotifyCardBackground: Color get() = CardBackground
val SpotifyElevated: Color get() = ElevatedBackground
val SpotifyLightGrey: Color get() = BorderSubtle
val SpotifyTextPrimary: Color get() = TextPrimary
val SpotifyTextSecondary: Color get() = TextSecondary
val SpotifyTextMuted: Color get() = TextMuted
val SpotifyRed: Color get() = Primary
