package com.example.spotifyclone.data.repository

import com.example.spotifyclone.data.model.LyricLine
import com.example.spotifyclone.data.model.LyricsData
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.lyrics.LrcLibService
import com.example.spotifyclone.service.lyrics.SecondaryLyricsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository utama untuk lirik lagu aplikasi Crankr.
 * Mengorkestrasi pengambilan lirik dari LRCLIB (synced/plain),
 * fallback sekunder jika tidak ditemukan, dan caching di memori.
 */
class LyricsRepository(
    private val lrcLibService: LrcLibService = LrcLibService(),
    private val secondaryLyricsService: SecondaryLyricsService = SecondaryLyricsService()
) {

    private val lyricsCache = ConcurrentHashMap<String, LyricsData>()

    companion object {
        private val EMPTY_LYRICS_DATA = LyricsData(
            lines = emptyList(),
            isSynced = false,
            rawPlain = null
        )
    }

    /**
     * Memeriksa apakah lirik untuk trackId tertentu sudah ada di cache RAM.
     */
    fun getCachedLyrics(trackId: String): LyricsData? {
        val cached = lyricsCache[trackId]
        return if (cached != null && cached !== EMPTY_LYRICS_DATA) cached else null
    }

    /**
     * Mengambil data lirik berdasarkan entitas model Track.
     */
    suspend fun fetchLyricsData(track: Track): LyricsData? = withContext(Dispatchers.IO) {
        fetchLyricsData(
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSeconds = track.durationSeconds,
            trackId = track.id
        )
    }

    /**
     * Mengambil data lirik lengkap dengan alur fallback multi-level.
     */
    suspend fun fetchLyricsData(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = 0,
        trackId: String? = null
    ): LyricsData? = withContext(Dispatchers.IO) {
        val cleanArtist = LrcLibService.cleanArtistName(artist)
        val cleanTitle = LrcLibService.cleanTrackTitle(title, cleanArtist)

        if (cleanTitle.isBlank()) return@withContext null

        val cacheKey = "${cleanTitle.lowercase()}_${cleanArtist.lowercase()}"

        // Cek cache by trackId jika ada
        if (!trackId.isNullOrBlank()) {
            lyricsCache[trackId]?.let {
                return@withContext if (it === EMPTY_LYRICS_DATA) null else it
            }
        }

        // Cek cache by cleaned name
        lyricsCache[cacheKey]?.let {
            if (it === EMPTY_LYRICS_DATA) return@withContext null
            if (!trackId.isNullOrBlank()) lyricsCache[trackId] = it
            return@withContext it
        }

        // 1. Sumber Utama: LRCLIB API (get spesifik -> fallback search structured -> fallback general)
        try {
            val lrcLibResult = lrcLibService.fetchLyrics(
                title = title,
                artist = artist,
                album = album,
                durationSeconds = durationSeconds
            )

            if (lrcLibResult != null && lrcLibResult.lines.isNotEmpty()) {
                lyricsCache[cacheKey] = lrcLibResult
                if (!trackId.isNullOrBlank()) lyricsCache[trackId] = lrcLibResult
                return@withContext lrcLibResult
            }
        } catch (_: Exception) {}

        // 2. Sumber Sekunder Cadangan: SecondaryLyricsService
        try {
            val secondaryResult = secondaryLyricsService.fetchSecondaryLyrics(title, artist)
            if (secondaryResult != null && secondaryResult.lines.isNotEmpty()) {
                lyricsCache[cacheKey] = secondaryResult
                if (!trackId.isNullOrBlank()) lyricsCache[trackId] = secondaryResult
                return@withContext secondaryResult
            }
        } catch (_: Exception) {}

        // Tandai sebagai tidak ditemukan di cache untuk mencegah redundant network spam
        lyricsCache[cacheKey] = EMPTY_LYRICS_DATA
        if (!trackId.isNullOrBlank()) lyricsCache[trackId] = EMPTY_LYRICS_DATA

        null
    }

    /**
     * Helper untuk mendapatkan teks lirik statis (raw plain atau digabung dari lines).
     */
    suspend fun fetchLyricsText(track: Track): String? {
        val data = fetchLyricsData(track) ?: return null
        return data.rawPlain ?: data.lines.joinToString("\n") { it.text }
    }
}
