package com.example.spotifyclone.service

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Hardware Audio FX Engine for Crankr
 * Mengelola android.media.audiofx.Equalizer, BassBoost, dan Virtualizer
 * yang terhubung langsung ke audioSessionId ExoPlayer secara dinamis melalui AnalyticsListener.
 */
@OptIn(UnstableApi::class)
class EqualizerHandler(
    private val context: Context,
    private val exoPlayer: ExoPlayer
) {
    var equalizer: Equalizer? = null
    var bassBoost: BassBoost? = null
    var virtualizer: Virtualizer? = null
    var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentAttachedSessionId: Int = 0

    private val customBandLevels = mutableMapOf<Short, Short>()
    private var currentPresetIndex: Short? = null
    private var currentPresetName: String = "Flat"
    private var currentBassBoostStrength: Short = 0
    private var currentVirtualizerStrength: Short = 0
    private var currentPreampGainDb: Float = 0f

    init {
        val prefs = context.getSharedPreferences("crankr_prefs", Context.MODE_PRIVATE)
        currentPresetName = prefs.getString("eq_preset", "Flat") ?: "Flat"
        val auralysPrefs = context.getSharedPreferences("auralys_settings_prefs", Context.MODE_PRIVATE)
        currentPreampGainDb = auralysPrefs.getFloat("preamp_gain_db", 0f)

        // Dengarkan perubahan session audio lewat AnalyticsListener & Player.Listener
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId > 0 && audioSessionId != android.media.AudioTrack.ERROR_BAD_VALUE) {
                    setupEqualizer(audioSessionId)
                }
            }
        })

        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                if (audioSessionId > 0 && audioSessionId != android.media.AudioTrack.ERROR_BAD_VALUE) {
                    setupEqualizer(audioSessionId)
                }
            }
        })

        // Ambil audioSessionId aktif hanya jika sudah valid (bukan 0 dan bukan ERROR_BAD_VALUE)
        val initialSessionId = exoPlayer.audioSessionId
        if (initialSessionId > 0 && initialSessionId != android.media.AudioTrack.ERROR_BAD_VALUE) {
            setupEqualizer(initialSessionId)
        }
    }

    /**
     * Inisialisasi android.media.audiofx.Equalizer, BassBoost, dan Virtualizer
     * dengan prioritas Int.MAX_VALUE saat audioSessionId valid dan lebih besar dari 0.
     */
    @Synchronized
    fun setupEqualizer(sessionId: Int) {
        if (sessionId <= 0 || sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == android.media.AudioTrack.ERROR_BAD_VALUE) return
        if (sessionId == currentAttachedSessionId && equalizer != null) {
            try {
                equalizer?.enabled = true
            } catch (_: Exception) {}
            applySavedSettings()
            return
        }

        try {
            try {
                equalizer?.release()
            } catch (_: Exception) {}
            equalizer = Equalizer(Int.MAX_VALUE, sessionId).apply {
                enabled = true
            }

            // Terapkan efek hardware BassBoost jika didukung perangkat
            try {
                bassBoost?.release()
                bassBoost = BassBoost(Int.MAX_VALUE, sessionId).apply {
                    enabled = true
                }
            } catch (e: Exception) {
                Log.w("EqualizerHandler", "BassBoost tidak didukung: ${e.message}")
            }

            // Terapkan efek hardware Virtualizer jika didukung perangkat
            try {
                virtualizer?.release()
                virtualizer = Virtualizer(Int.MAX_VALUE, sessionId).apply {
                    enabled = true
                }
            } catch (e: Exception) {
                Log.w("EqualizerHandler", "Virtualizer tidak didukung: ${e.message}")
            }

            // Terapkan efek hardware LoudnessEnhancer (Preamp Volume Booster)
            try {
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    val gainMb = (currentPreampGainDb * 100).toInt()
                    setTargetGain(gainMb)
                    enabled = currentPreampGainDb > 0f
                }
            } catch (e: Exception) {
                Log.w("EqualizerHandler", "LoudnessEnhancer tidak didukung: ${e.message}")
            }

            currentAttachedSessionId = sessionId
            applySavedSettings()
            Log.d("EqualizerHandler", "Equalizer, BassBoost, Virtualizer & LoudnessEnhancer aktif pada sessionId=$sessionId (priority MAX_VALUE)")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("EqualizerHandler", "Gagal inisialisasi audio effects pada session $sessionId", e)
        }
    }

    // Alias untuk kompatibilitas fungsi pemanggilan
    fun initAudioEffects(audioSessionId: Int) = setupEqualizer(audioSessionId)

    fun ensureSessionAttached() {
        val sessionId = exoPlayer.audioSessionId
        if (sessionId > 0 && sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != android.media.AudioTrack.ERROR_BAD_VALUE && (equalizer == null || currentAttachedSessionId != sessionId)) {
            setupEqualizer(sessionId)
        }
    }

    /**
     * Konversi nilai slider frekuensi (0.0f - 1.0f) ke satuan milibel perangkat keras
     */
    fun setBandGain(band: Short, progressPercent: Float) {
        ensureSessionAttached()
        val eq = equalizer ?: return
        val minLevel = eq.bandLevelRange[0]
        val maxLevel = eq.bandLevelRange[1]
        val calculatedLevel = (minLevel + (maxLevel - minLevel) * progressPercent.coerceIn(0f, 1f)).toInt().toShort()
        eq.setBandLevel(band, calculatedLevel)
        customBandLevels[band] = calculatedLevel
    }

    fun setBandGain(band: Int, progressPercent: Float) {
        setBandGain(band.toShort(), progressPercent)
    }

    /**
     * Terapkan preset berdasarkan index hardware
     */
    fun applyPreset(presetIndex: Short) {
        ensureSessionAttached()
        val eq = equalizer ?: return
        if (presetIndex in 0 until eq.numberOfPresets) {
            eq.usePreset(presetIndex)
            currentPresetIndex = presetIndex
            currentPresetName = try { eq.getPresetName(presetIndex) } catch (_: Exception) { "Preset $presetIndex" }
            applyComplementaryEffects(currentPresetName)
        }
    }

    fun applyPreset(presetIndex: Int) {
        applyPreset(presetIndex.toShort())
    }

    /**
     * Sambungkan perubahan slider frekuensi UI ke hardware equalizer:
     * equalizer.setBandLevel(bandIndex, levelInMilliBels)
     */
    fun setBandLevel(bandIndex: Short, levelInMilliBels: Short) {
        customBandLevels[bandIndex] = levelInMilliBels
        ensureSessionAttached()
        equalizer?.let { eq ->
            try {
                if (!eq.enabled) eq.enabled = true
                eq.setBandLevel(bandIndex, levelInMilliBels)
            } catch (e: Exception) {
                Log.e("EqualizerHandler", "Error setBandLevel($bandIndex, $levelInMilliBels)", e)
            }
        }
    }

    fun setBandLevel(bandIndex: Int, levelInMilliBels: Int) {
        setBandLevel(bandIndex.toShort(), levelInMilliBels.toShort())
    }

    /**
     * Sambungkan pilihan preset UI ke hardware equalizer:
     * equalizer.usePreset(presetIndex)
     */
    fun usePreset(presetIndex: Short) {
        currentPresetIndex = presetIndex
        customBandLevels.clear()
        ensureSessionAttached()
        equalizer?.let { eq ->
            try {
                if (!eq.enabled) eq.enabled = true
                eq.usePreset(presetIndex)
                currentPresetName = try { eq.getPresetName(presetIndex) } catch (_: Exception) { "Preset $presetIndex" }
                applyComplementaryEffects(currentPresetName)
            } catch (e: Exception) {
                Log.e("EqualizerHandler", "Error usePreset($presetIndex)", e)
            }
        }
    }

    fun usePreset(presetIndex: Int) {
        usePreset(presetIndex.toShort())
    }

    /**
     * Menerapkan preset nama (Rock, Pop, Flat, Bass Boost, dsb.) ke hardware equalizer.
     */
    fun setEqualizerPreset(presetName: String) {
        currentPresetName = presetName
        customBandLevels.clear()
        ensureSessionAttached()
        equalizer?.let { eq ->
            try {
                if (!eq.enabled) eq.enabled = true
                val presetIndex = findPresetIndex(eq, presetName)
                if (presetIndex != null) {
                    currentPresetIndex = presetIndex
                    eq.usePreset(presetIndex)
                } else {
                    currentPresetIndex = null
                    applyPresetLevelsFallback(eq, presetName)
                }
                applyComplementaryEffects(presetName)
            } catch (e: Exception) {
                Log.e("EqualizerHandler", "Error setting preset: $presetName", e)
                applyPresetLevelsFallback(eq, presetName)
            }
        }
    }

    /**
     * Mengatur kekuatan BassBoost (0 - 1000)
     */
    fun setBassBoostStrength(strength: Short) {
        currentBassBoostStrength = strength
        ensureSessionAttached()
        bassBoost?.let { bb ->
            try {
                if (bb.strengthSupported) {
                    bb.enabled = strength > 0
                    bb.setStrength(strength)
                }
            } catch (e: Exception) {
                Log.e("EqualizerHandler", "Error setBassBoostStrength($strength)", e)
            }
        }
    }

    /**
     * Mengatur kekuatan Virtualizer (0 - 1000)
     */
    fun setVirtualizerStrength(strength: Short) {
        currentVirtualizerStrength = strength
        ensureSessionAttached()
        virtualizer?.let { virt ->
            try {
                if (virt.strengthSupported) {
                    virt.enabled = strength > 0
                    virt.setStrength(strength)
                }
            } catch (e: Exception) {
                Log.e("EqualizerHandler", "Error setVirtualizerStrength($strength)", e)
            }
        }
    }

    fun getBandLevel(bandIndex: Short): Short {
        return equalizer?.let { eq ->
            try {
                eq.getBandLevel(bandIndex)
            } catch (_: Exception) {
                customBandLevels[bandIndex] ?: 0.toShort()
            }
        } ?: customBandLevels[bandIndex] ?: 0.toShort()
    }

    fun getNumberOfBands(): Short = equalizer?.numberOfBands ?: 5.toShort()

    fun getBandLevelRange(): ShortArray = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)

    fun getCenterFreq(bandIndex: Short): Int {
        return try {
            equalizer?.getCenterFreq(bandIndex) ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun getPresetNames(): List<String> {
        val eq = equalizer ?: return emptyList()
        val num = eq.numberOfPresets.toInt()
        val list = mutableListOf<String>()
        for (i in 0 until num) {
            try {
                list.add(eq.getPresetName(i.toShort()))
            } catch (_: Exception) {}
        }
        return list
    }

    fun applySavedSettings() {
        val eq = equalizer ?: return
        try {
            if (!eq.enabled) eq.enabled = true
            if (currentPresetIndex != null) {
                try {
                    eq.usePreset(currentPresetIndex!!)
                } catch (e: Exception) {
                    applyPresetLevelsFallback(eq, currentPresetName)
                }
            } else {
                val idx = findPresetIndex(eq, currentPresetName)
                if (idx != null) {
                    currentPresetIndex = idx
                    eq.usePreset(idx)
                } else {
                    applyPresetLevelsFallback(eq, currentPresetName)
                }
            }

            // Terapkan custom band level jika ada
            if (customBandLevels.isNotEmpty()) {
                customBandLevels.forEach { (band, level) ->
                    try {
                        eq.setBandLevel(band, level)
                    } catch (_: Exception) {}
                }
            }

            applyComplementaryEffects(currentPresetName)
        } catch (e: Exception) {
            Log.e("EqualizerHandler", "Error applying saved settings", e)
        }
    }

    // Alias untuk kompatibilitas
    fun applyCurrentSettings() = applySavedSettings()

    private fun applyComplementaryEffects(presetName: String) {
        val (bbStrength, virtStrength) = when (presetName.lowercase()) {
            "bass boost" -> 1000.toShort() to 400.toShort()
            "rock" -> 500.toShort() to 600.toShort()
            "pop" -> 250.toShort() to 350.toShort()
            "vocal" -> 0.toShort() to 200.toShort()
            "acoustic" -> 200.toShort() to 450.toShort()
            else /* Flat */ -> 0.toShort() to 0.toShort()
        }
        setBassBoostStrength(bbStrength)
        setVirtualizerStrength(virtStrength)
    }

    private fun findPresetIndex(eq: Equalizer, presetName: String): Short? {
        try {
            val numPresets = eq.numberOfPresets.toInt()
            if (numPresets <= 0) return null

            // 1. Exact match (case insensitive)
            for (i in 0 until numPresets) {
                val name = eq.getPresetName(i.toShort())
                if (name.equals(presetName, ignoreCase = true)) {
                    return i.toShort()
                }
            }

            // 2. Specific matching untuk Rock, Pop, Flat, Bass Boost
            val target = presetName.trim().lowercase()
            for (i in 0 until numPresets) {
                val name = eq.getPresetName(i.toShort()).lowercase()
                when {
                    target == "flat" && (name.contains("flat") || name.contains("normal")) -> return i.toShort()
                    target == "rock" && (name.contains("rock") || name.contains("metal")) -> return i.toShort()
                    target == "pop" && name.contains("pop") -> return i.toShort()
                    target.contains("bass") && (name.contains("bass") || name.contains("deep")) -> return i.toShort()
                }
            }
        } catch (e: Exception) {
            Log.e("EqualizerHandler", "Error finding preset index for $presetName", e)
        }
        return null
    }

    private fun applyPresetLevelsFallback(eq: Equalizer, preset: String) {
        try {
            val numBands = eq.numberOfBands.toInt()
            val bandRange = eq.bandLevelRange
            val minL = bandRange[0].toFloat()
            val maxL = bandRange[1].toFloat()

            fun calculateLevel(ratio: Float): Short {
                val clampedRatio = ratio.coerceIn(-1.0f, 1.0f)
                return if (clampedRatio >= 0f) {
                    (maxL * clampedRatio).toInt().coerceIn(minL.toInt(), maxL.toInt()).toShort()
                } else {
                    (-minL * clampedRatio).toInt().coerceIn(minL.toInt(), maxL.toInt()).toShort()
                }
            }

            val ratios = when (preset.lowercase()) {
                "bass boost" -> floatArrayOf(1.0f, 0.75f, 0.10f, 0.0f, 0.10f)
                "rock" -> floatArrayOf(0.70f, 0.35f, -0.10f, 0.50f, 0.90f)
                "pop" -> floatArrayOf(-0.10f, 0.25f, 0.60f, 0.35f, -0.10f)
                "vocal" -> floatArrayOf(-0.35f, 0.0f, 0.70f, 0.40f, 0.15f)
                "acoustic" -> floatArrayOf(0.40f, 0.25f, 0.10f, 0.35f, 0.60f)
                else /* Flat */ -> floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
            }

            for (i in 0 until numBands) {
                val ratio = if (i < ratios.size) ratios[i] else 0.0f
                val lvl = calculateLevel(ratio)
                eq.setBandLevel(i.toShort(), lvl)
                customBandLevels[i.toShort()] = lvl
            }
        } catch (e: Exception) {
            Log.e("EqualizerHandler", "Error applying preset fallback: $preset", e)
        }
    }

    /**
     * Mengatur nilai penguatan preamp (Preamp Volume Booster) dalam satuan dB (0 dB s/d +12 dB).
     * Diterapkan langsung ke sesi audio hardware melalui LoudnessEnhancer API.
     */
    fun setPreampGain(gainDb: Float) {
        val clamped = gainDb.coerceIn(0f, 12f)
        currentPreampGainDb = clamped
        ensureSessionAttached()
        try {
            loudnessEnhancer?.let {
                val gainMb = (clamped * 100).toInt()
                it.setTargetGain(gainMb)
                it.enabled = clamped > 0f
            }
        } catch (e: Exception) {
            Log.w("EqualizerHandler", "Gagal menerapkan preamp gain: ${e.message}")
        }
    }

    fun getPreampGain(): Float = currentPreampGainDb

    fun releaseAudioEffects() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null

        try {
            bassBoost?.enabled = false
            bassBoost?.release()
        } catch (_: Exception) {}
        bassBoost = null

        try {
            virtualizer?.enabled = false
            virtualizer?.release()
        } catch (_: Exception) {}
        virtualizer = null

        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        loudnessEnhancer = null

        currentAttachedSessionId = 0
    }
}
