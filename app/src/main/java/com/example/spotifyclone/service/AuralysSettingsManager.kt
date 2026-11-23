package com.example.spotifyclone.service

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Pilihan kualitas streaming audio Crankr:
 * - Hemat: 64 kbps (Opus 50k-70k, super hemat kuota)
 * - Standar: 128 kbps (M4A/Opus ~128kbps, keseimbangan optimal)
 * - Maksimal: Opus 256 kbps (Kualitas rekaman murni resolusi tertinggi)
 */
enum class AudioQuality(val key: String, val displayName: String, val bitrateLabel: String, val targetBitrate: Int) {
    SAVER("saver", "Hemat", "64 kbps", 64_000),
    STANDARD("standard", "Standar", "128 kbps", 128_000),
    MAXIMUM("maximum", "Maksimal", "Opus 256 kbps", 256_000);

    companion object {
        fun fromKey(key: String?): AudioQuality = values().firstOrNull { it.key == key } ?: STANDARD
    }
}

/**
 * AuralysSettingsManager:
 * Mengelola preferensi pengaturan audio & sistem musik Crankr (Auralys Engine):
 * 1. Volume Normalization (Normalisasi Volume Audio)
 * 2. Pemilih Kualitas Audio Streaming (Hemat, Standar, Maksimal Opus)
 * 3. Penguat Volume Preamp Audio (0 dB s/d +12 dB)
 * 4. Integrasi Discord Rich Presence (RPC)
 * 5. Integrasi Last.fm Scrobbler
 * 6. Manajemen Cache Streaming & Penyimpanan
 * 7. Penguncian Tema Kustom (Material You / Dynamic Palette dinonaktifkan total & dikunci permanen)
 */
class AuralysSettingsManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("auralys_settings_prefs", Context.MODE_PRIVATE)

    private val _isVolumeNormalizationEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_VOLUME_NORMALIZATION, true))
    val isVolumeNormalizationEnabled: StateFlow<Boolean> = _isVolumeNormalizationEnabled.asStateFlow()

    private val _audioQuality =
        MutableStateFlow(AudioQuality.fromKey(prefs.getString(KEY_AUDIO_QUALITY, AudioQuality.STANDARD.key)))
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()

    private val _preampGainDb =
        MutableStateFlow(prefs.getFloat(KEY_PREAMP_GAIN_DB, 0f))
    val preampGainDb: StateFlow<Float> = _preampGainDb.asStateFlow()

    private val _isDiscordRpcEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_DISCORD_RPC, false))
    val isDiscordRpcEnabled: StateFlow<Boolean> = _isDiscordRpcEnabled.asStateFlow()

    private val _isLastFmScrobbleEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_LASTFM_SCROBBLE, false))
    val isLastFmScrobbleEnabled: StateFlow<Boolean> = _isLastFmScrobbleEnabled.asStateFlow()

    private val _lastFmUsername =
        MutableStateFlow(prefs.getString(KEY_LASTFM_USERNAME, "") ?: "")
    val lastFmUsername: StateFlow<String> = _lastFmUsername.asStateFlow()

    init {
        AudioStreamResolver.currentAudioQuality = _audioQuality.value
    }

    fun setAudioQuality(quality: AudioQuality) {
        prefs.edit().putString(KEY_AUDIO_QUALITY, quality.key).apply()
        _audioQuality.value = quality
        AudioStreamResolver.currentAudioQuality = quality
        AudioStreamResolver.clearPlaybackCache()
    }

    fun setPreampGainDb(gainDb: Float) {
        val clamped = gainDb.coerceIn(0f, 12f)
        prefs.edit().putFloat(KEY_PREAMP_GAIN_DB, clamped).apply()
        _preampGainDb.value = clamped
    }

    fun setVolumeNormalization(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_NORMALIZATION, enabled).apply()
        _isVolumeNormalizationEnabled.value = enabled
    }

    fun setDiscordRpc(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISCORD_RPC, enabled).apply()
        _isDiscordRpcEnabled.value = enabled
    }

    fun setLastFmScrobble(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LASTFM_SCROBBLE, enabled).apply()
        _isLastFmScrobbleEnabled.value = enabled
    }

    fun setLastFmUsername(username: String) {
        val clean = username.trim()
        prefs.edit().putString(KEY_LASTFM_USERNAME, clean).apply()
        _lastFmUsername.value = clean
    }

    @OptIn(UnstableApi::class)
    fun clearStreamingCache(): Long {
        var freedBytes = 0L
        try {
            val cache = MediaCacheManager.getCache(context)
            val keys = cache.keys
            for (key in keys) {
                try {
                    cache.removeResource(key)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        try {
            val cacheDir = File(context.applicationContext.cacheDir, "crankr_media_cache")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    freedBytes += file.length()
                    file.delete()
                }
            }
        } catch (_: Exception) {}

        return freedBytes
    }

    fun getCacheSizeFormatted(): String {
        var totalBytes = 0L
        try {
            val cacheDir = File(context.applicationContext.cacheDir, "crankr_media_cache")
            if (cacheDir.exists()) {
                cacheDir.walkTopDown().forEach { file ->
                    if (file.isFile) totalBytes += file.length()
                }
            }
        } catch (_: Exception) {}

        val mb = totalBytes.toDouble() / (1024 * 1024)
        return "%.1f MB".format(mb)
    }

    companion object {
        private const val KEY_VOLUME_NORMALIZATION = "volume_normalization"
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val KEY_PREAMP_GAIN_DB = "preamp_gain_db"
        private const val KEY_DISCORD_RPC = "discord_rpc"
        private const val KEY_LASTFM_SCROBBLE = "lastfm_scrobble"
        private const val KEY_LASTFM_USERNAME = "lastfm_username"

        @Volatile
        private var instance: AuralysSettingsManager? = null

        fun getInstance(context: Context): AuralysSettingsManager {
            return instance ?: synchronized(this) {
                instance ?: AuralysSettingsManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
