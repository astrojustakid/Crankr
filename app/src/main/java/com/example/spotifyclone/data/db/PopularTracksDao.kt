package com.example.spotifyclone.data.db

import com.example.spotifyclone.data.model.Track

/**
 * Entitas cache lokal Room/SQLite untuk lagu-lagu populer / tren dari Spotify.
 */
data class PopularTrackEntity(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val streamUrl: String,
    val coverUrl: String,
    val genre: String,
    val cachedAt: Long,
    val position: Int
) {
    fun toTrack(): Track {
        return Track(
            id = trackId,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            streamUrl = streamUrl,
            coverUrl = coverUrl,
            genre = genre
        )
    }

    companion object {
        fun fromTrack(track: Track, position: Int, timestamp: Long = System.currentTimeMillis()): PopularTrackEntity {
            return PopularTrackEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSeconds = track.durationSeconds,
                streamUrl = track.streamUrl,
                coverUrl = track.coverUrl,
                genre = track.genre,
                cachedAt = timestamp,
                position = position
            )
        }
    }
}

/**
 * DAO interface untuk caching lagu trending / populer Spotify di database lokal.
 * Mendukung verifikasi TTL (Time to Live) antara 6 hingga 12 jam.
 */
interface PopularTracksDao {
    /**
     * Mengambil seluruh lagu populer dari cache lokal terurut berdasarkan posisi.
     */
    suspend fun getCachedPopularTracks(): List<PopularTrackEntity>

    /**
     * Menyimpan daftar lagu populer baru ke dalam cache database lokal.
     */
    suspend fun savePopularTracks(tracks: List<Track>, timestamp: Long = System.currentTimeMillis())

    /**
     * Memeriksa apakah cache masih dalam masa berlaku (TTL).
     */
    suspend fun isCacheValid(ttlMillis: Long = 8 * 3600 * 1000L): Boolean

    /**
     * Menghapus seluruh cache lagu populer.
     */
    suspend fun clearCache()
}
