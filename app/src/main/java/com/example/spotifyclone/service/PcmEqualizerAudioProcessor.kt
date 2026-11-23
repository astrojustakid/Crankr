package com.example.spotifyclone.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * CustomEqualizerAudioProcessor / PcmEqualizerAudioProcessor
 * AudioProcessor berbasis PCM 16-bit untuk ExoPlayer DefaultRenderersFactory.
 * Memproses filter Low-Shelf (Bass), High-Shelf (Treble), dan kompensasi gain otomatis
 * berbasis metadata loudnessDb dari YouTube Music InnerTube secara real-time langsung
 * pada aliran data PCM, menjamin karakter suara dan volume optimal di semua perangkat.
 */
typealias CustomEqualizerAudioProcessor = PcmEqualizerAudioProcessor

@OptIn(UnstableApi::class)
class PcmEqualizerAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var isPcmProcessingEnabled: Boolean = true

    @Volatile
    var isVolumeNormalizationEnabled: Boolean = true

    @Volatile
    private var bassGainNormalized: Float = 0.5f // 0.0 - 1.0 (0.5 = 0dB)

    @Volatile
    private var trebleGainNormalized: Float = 0.5f // 0.0 - 1.0 (0.5 = 0dB)

    @Volatile
    private var gainCompensationMultiplier: Double = 1.0 // Kompensasi gain otomatis dari loudnessDb InnerTube

    @Volatile
    private var preampMultiplier: Double = 1.0

    private var sampleRate: Int = 44100
    private var channelCount: Int = 2

    // Biquad filter state for up to 8 channels (Left, Right, etc.)
    // Low-shelf (Bass, ~140 Hz)
    private var b0_bass = 1.0; private var b1_bass = 0.0; private var b2_bass = 0.0
    private var a1_bass = 0.0; private var a2_bass = 0.0
    private val x1_bass = DoubleArray(8)
    private val x2_bass = DoubleArray(8)
    private val y1_bass = DoubleArray(8)
    private val y2_bass = DoubleArray(8)

    // High-shelf (Treble, ~6000 Hz)
    private var b0_treble = 1.0; private var b1_treble = 0.0; private var b2_treble = 0.0
    private var a1_treble = 0.0; private var a2_treble = 0.0
    private val x1_treble = DoubleArray(8)
    private val x2_treble = DoubleArray(8)
    private val y1_treble = DoubleArray(8)
    private val y2_treble = DoubleArray(8)

    init {
        updateFilterCoefficients()
    }

    fun setBassGain(gain: Float) {
        val clamped = gain.coerceIn(0f, 1f)
        if (abs(bassGainNormalized - clamped) > 0.001f) {
            bassGainNormalized = clamped
            updateFilterCoefficients()
        }
    }

    fun setTrebleGain(gain: Float) {
        val clamped = gain.coerceIn(0f, 1f)
        if (abs(trebleGainNormalized - clamped) > 0.001f) {
            trebleGainNormalized = clamped
            updateFilterCoefficients()
        }
    }

    fun setVolumeNormalization(enabled: Boolean) {
        isVolumeNormalizationEnabled = enabled
    }

    fun setPreset(preset: String) {
        when (preset.lowercase()) {
            "bass boost" -> {
                setBassGain(1.0f)
                setTrebleGain(0.55f)
            }
            "rock" -> {
                setBassGain(0.85f)
                setTrebleGain(0.90f)
            }
            "pop" -> {
                setBassGain(0.45f)
                setTrebleGain(0.45f)
            }
            "vocal" -> {
                setBassGain(0.35f)
                setTrebleGain(0.65f)
            }
            "acoustic" -> {
                setBassGain(0.70f)
                setTrebleGain(0.75f)
            }
            else /* Flat */ -> {
                setBassGain(0.50f)
                setTrebleGain(0.50f)
            }
        }
    }

    /**
     * Menerapkan kompensasi gain otomatis berbasis nilai loudnessDb dari InnerTube YouTube Music.
     * Mengangkat track yang di-master pelan dan menyeimbangkan track keras agar volume lantang setara Spotify/Apple Music.
     */
    fun setLoudnessDb(loudnessDb: Double) {
        gainCompensationMultiplier = if (loudnessDb.isFinite()) {
            val adjustmentDb = (-loudnessDb).coerceIn(-6.0, 8.0)
            10.0.pow(adjustmentDb / 20.0)
        } else {
            1.0
        }
    }

    /**
     * Mengatur penguatan preamp volume audio (0 dB s/d +12 dB) secara langsung pada pipeline PCM
     * dengan soft-clipping saturation limiter untuk mencegah distorsi harmonik keras.
     */
    fun setPreampGainDb(gainDb: Float) {
        val clamped = gainDb.coerceIn(0f, 12f)
        preampMultiplier = 10.0.pow(clamped / 20.0)
    }

    private fun updateFilterCoefficients() {
        val sRate = if (sampleRate > 0) sampleRate.toDouble() else 44100.0

        // 1. Bass Low-Shelf Filter (f0 = 140 Hz, Q = 0.707)
        // dB range: -15dB to +15dB (punchy & bertenaga khas Crankr audio deck)
        val bassDb = (bassGainNormalized - 0.5f) * 30.0
        val aBass = 10.0.pow(bassDb / 40.0)
        val w0Bass = 2.0 * PI * 140.0 / sRate
        val cosW0Bass = cos(w0Bass)
        val sinW0Bass = sin(w0Bass)
        val alphaBass = sinW0Bass / (2.0 * 0.707)
        val twoSqrtABassAlpha = 2.0 * sqrt(aBass) * alphaBass

        val a0Bass = (aBass + 1.0) + (aBass - 1.0) * cosW0Bass + twoSqrtABassAlpha
        b0_bass = (aBass * ((aBass + 1.0) - (aBass - 1.0) * cosW0Bass + twoSqrtABassAlpha)) / a0Bass
        b1_bass = (2.0 * aBass * ((aBass - 1.0) - (aBass + 1.0) * cosW0Bass)) / a0Bass
        b2_bass = (aBass * ((aBass + 1.0) - (aBass - 1.0) * cosW0Bass - twoSqrtABassAlpha)) / a0Bass
        a1_bass = (-2.0 * ((aBass - 1.0) + (aBass + 1.0) * cosW0Bass)) / a0Bass
        a2_bass = ((aBass + 1.0) + (aBass - 1.0) * cosW0Bass - twoSqrtABassAlpha) / a0Bass

        // 2. Treble High-Shelf Filter (f0 = 6000 Hz, Q = 0.707)
        // dB range: -15dB to +15dB (clarity jernih dan renyah)
        val trebleDb = (trebleGainNormalized - 0.5f) * 30.0
        val aTreble = 10.0.pow(trebleDb / 40.0)
        val w0Treble = 2.0 * PI * 6000.0 / sRate
        val cosW0Treble = cos(w0Treble)
        val sinW0Treble = sin(w0Treble)
        val alphaTreble = sinW0Treble / (2.0 * 0.707)
        val twoSqrtATrebleAlpha = 2.0 * sqrt(aTreble) * alphaTreble

        val a0Treble = (aTreble + 1.0) - (aTreble - 1.0) * cosW0Treble + twoSqrtATrebleAlpha
        b0_treble = (aTreble * ((aTreble + 1.0) + (aTreble - 1.0) * cosW0Treble + twoSqrtATrebleAlpha)) / a0Treble
        b1_treble = (-2.0 * aTreble * ((aTreble - 1.0) + (aTreble + 1.0) * cosW0Treble)) / a0Treble
        b2_treble = (aTreble * ((aTreble + 1.0) + (aTreble - 1.0) * cosW0Treble - twoSqrtATrebleAlpha)) / a0Treble
        a1_treble = (2.0 * ((aTreble - 1.0) - (aTreble + 1.0) * cosW0Treble)) / a0Treble
        a2_treble = ((aTreble + 1.0) - (aTreble - 1.0) * cosW0Treble - twoSqrtATrebleAlpha) / a0Treble
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount.coerceIn(1, 8)
        updateFilterCoefficients()
        resetFilterState()
        return inputAudioFormat
    }

    private fun resetFilterState() {
        x1_bass.fill(0.0); x2_bass.fill(0.0); y1_bass.fill(0.0); y2_bass.fill(0.0)
        x1_treble.fill(0.0); x2_treble.fill(0.0); y1_treble.fill(0.0); y2_treble.fill(0.0)
    }

    override fun onReset() {
        resetFilterState()
    }

    override fun onFlush() {
        resetFilterState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        // If processing is disabled or both gains are flat, gainCompensation is neutral, and preamp is 0dB, pass through raw bytes
        val normMultiplier = if (isVolumeNormalizationEnabled) gainCompensationMultiplier else 1.0
        val isFlat = abs(bassGainNormalized - 0.5f) < 0.01f && abs(trebleGainNormalized - 0.5f) < 0.01f
        val isGainNeutral = abs(normMultiplier - 1.0) < 0.01
        val isPreampNeutral = abs(preampMultiplier - 1.0) < 0.01
        if (!isPcmProcessingEnabled || (isFlat && isGainNeutral && isPreampNeutral)) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val ch = channelCount
        val totalGain = normMultiplier * preampMultiplier
        while (inputBuffer.remaining() >= 2) {
            // Process channel by channel
            for (c in 0 until ch) {
                if (inputBuffer.remaining() < 2) break
                val sample = inputBuffer.short.toDouble()

                // 1. Apply Bass Low-Shelf
                val yBass = b0_bass * sample + b1_bass * x1_bass[c] + b2_bass * x2_bass[c] - a1_bass * y1_bass[c] - a2_bass * y2_bass[c]
                x2_bass[c] = x1_bass[c]
                x1_bass[c] = sample
                y2_bass[c] = y1_bass[c]
                y1_bass[c] = yBass

                // 2. Apply Treble High-Shelf on output of bass
                val yTreble = b0_treble * yBass + b1_treble * x1_treble[c] + b2_treble * x2_treble[c] - a1_treble * y1_treble[c] - a2_treble * y2_treble[c]
                x2_treble[c] = x1_treble[c]
                x1_treble[c] = yBass
                y2_treble[c] = y1_treble[c]
                y1_treble[c] = yTreble

                // 3. Apply automatic loudness gain & preamp boost with soft-saturation limiter to eliminate digital clipping
                val scaled = yTreble * totalGain
                val finalSample = if (scaled > 30000.0 || scaled < -30000.0) {
                    val norm = scaled / 32768.0
                    (tanh(norm) * 32767.0).coerceIn(-32768.0, 32767.0)
                } else {
                    scaled
                }
                val clamped = finalSample.toInt().toShort()
                outputBuffer.putShort(clamped)
            }
        }
        outputBuffer.flip()
    }
}
