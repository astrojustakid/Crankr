package com.example.spotifyclone.service.lyrics

import com.example.spotifyclone.data.model.LyricsData
import com.example.spotifyclone.data.repository.LyricsRepository

/**
 * Service adaptor yang mengarahkan panggilan ke LyricsRepository dan LrcLibService.
 */
class LyricsService(
    private val repository: LyricsRepository = LyricsRepository()
) {

    suspend fun fetchLyricsData(title: String, artist: String): LyricsData? {
        return repository.fetchLyricsData(title = title, artist = artist)
    }

    suspend fun fetchLyricsData(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = 0,
        trackId: String? = null
    ): LyricsData? {
        return repository.fetchLyricsData(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            trackId = trackId
        )
    }

    suspend fun fetchLyrics(title: String, artist: String): String? {
        val data = fetchLyricsData(title, artist) ?: return null
        return data.rawPlain ?: data.lines.joinToString("\n") { it.text }
    }

    companion object {
        fun cleanTrackTitle(title: String, artist: String = ""): String =
            LrcLibService.cleanTrackTitle(title, artist)

        fun cleanArtistName(artist: String): String =
            LrcLibService.cleanArtistName(artist)
    }
}
