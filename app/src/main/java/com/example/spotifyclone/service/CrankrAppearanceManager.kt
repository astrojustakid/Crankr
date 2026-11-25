package com.example.spotifyclone.service

import android.content.Context
import com.example.spotifyclone.theme.CrankrThemeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemePreset(val id: String, val label: String) {
    AURA_LIQUID("aura_liquid", "Aura Liquid"),
    KINETIC_MONO("kinetic_mono", "Kinetic Mono"),
    VELVET_CRIMSON("velvet_crimson", "Velvet Crimson"),
    MIDNIGHT_JADE("midnight_jade", "Midnight Jade"),
    SUNSET_AMBER("sunset_amber", "Sunset Amber"),
    SOLARIS_GOLD("solaris_gold", "Solaris Gold");

    companion object {
        fun fromId(id: String?): AppThemePreset {
            if (id == "pure_oled") return KINETIC_MONO
            return entries.firstOrNull { it.id == id } ?: AURA_LIQUID
        }
    }
}

enum class PlayerBackgroundStyle(val id: String, val label: String) {
    BLUR("blur", "Blur Cover"),
    GRADIENT("gradient", "Gradient"),
    SOLID("solid", "Solid");

    companion object {
        fun fromId(id: String?): PlayerBackgroundStyle =
            entries.firstOrNull { it.id == id } ?: BLUR
    }
}

enum class LyricsAlignment(val id: String, val label: String) {
    START("start", "Kiri"),
    CENTER("center", "Tengah");

    companion object {
        fun fromId(id: String?): LyricsAlignment =
            entries.firstOrNull { it.id == id } ?: START
    }
}

enum class LyricsTextScale(
    val id: String,
    val label: String,
    val activeSp: Float,
    val inactiveSp: Float
) {
    COMPACT("compact", "Kecil", 19f, 14.5f),
    NORMAL("normal", "Normal", 22.5f, 16.5f),
    LARGE("large", "Besar", 26f, 18.5f);

    companion object {
        fun fromId(id: String?): LyricsTextScale =
            entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/**
 * CrankrAppearanceManager:
 * Mengelola seluruh preferensi tampilan & tema secara persisten dan reaktif (live switch tanpa restart).
 */
class CrankrAppearanceManager private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themePreset = MutableStateFlow(
        AppThemePreset.fromId(prefs.getString(KEY_THEME_PRESET, AppThemePreset.AURA_LIQUID.id))
    )
    val themePreset: StateFlow<AppThemePreset> = _themePreset.asStateFlow()

    private val _playerBackgroundStyle = MutableStateFlow(
        PlayerBackgroundStyle.fromId(prefs.getString(KEY_PLAYER_BG_STYLE, PlayerBackgroundStyle.BLUR.id))
    )
    val playerBackgroundStyle: StateFlow<PlayerBackgroundStyle> = _playerBackgroundStyle.asStateFlow()

    private val _miniPlayerCoverTint = MutableStateFlow(
        prefs.getBoolean(KEY_MINI_PLAYER_TINT, true)
    )
    val miniPlayerCoverTint: StateFlow<Boolean> = _miniPlayerCoverTint.asStateFlow()

    private val _lyricsAlignment = MutableStateFlow(
        LyricsAlignment.fromId(prefs.getString(KEY_LYRICS_ALIGN, LyricsAlignment.START.id))
    )
    val lyricsAlignment: StateFlow<LyricsAlignment> = _lyricsAlignment.asStateFlow()

    private val _lyricsTextScale = MutableStateFlow(
        LyricsTextScale.fromId(prefs.getString(KEY_LYRICS_SCALE, LyricsTextScale.NORMAL.id))
    )
    val lyricsTextScale: StateFlow<LyricsTextScale> = _lyricsTextScale.asStateFlow()

    init {
        CrankrThemeState.applyPreset(_themePreset.value.id)
    }

    fun setThemePreset(preset: AppThemePreset) {
        _themePreset.value = preset
        prefs.edit().putString(KEY_THEME_PRESET, preset.id).apply()
        CrankrThemeState.applyPreset(preset.id)
    }

    fun setPlayerBackgroundStyle(style: PlayerBackgroundStyle) {
        _playerBackgroundStyle.value = style
        prefs.edit().putString(KEY_PLAYER_BG_STYLE, style.id).apply()
    }

    fun setMiniPlayerCoverTint(enabled: Boolean) {
        _miniPlayerCoverTint.value = enabled
        prefs.edit().putBoolean(KEY_MINI_PLAYER_TINT, enabled).apply()
    }

    fun setLyricsAlignment(alignment: LyricsAlignment) {
        _lyricsAlignment.value = alignment
        prefs.edit().putString(KEY_LYRICS_ALIGN, alignment.id).apply()
    }

    fun setLyricsTextScale(scale: LyricsTextScale) {
        _lyricsTextScale.value = scale
        prefs.edit().putString(KEY_LYRICS_SCALE, scale.id).apply()
    }

    companion object {
        private const val PREFS_NAME = "crankr_appearance_prefs"
        private const val KEY_THEME_PRESET = "theme_preset"
        private const val KEY_PLAYER_BG_STYLE = "player_bg_style"
        private const val KEY_MINI_PLAYER_TINT = "mini_player_tint"
        private const val KEY_LYRICS_ALIGN = "lyrics_align"
        private const val KEY_LYRICS_SCALE = "lyrics_scale"

        @Volatile
        private var instance: CrankrAppearanceManager? = null

        fun getInstance(context: Context): CrankrAppearanceManager {
            return instance ?: synchronized(this) {
                instance ?: CrankrAppearanceManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
