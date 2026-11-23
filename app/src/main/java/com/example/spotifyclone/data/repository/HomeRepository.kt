package com.example.spotifyclone.data.repository

import android.content.Context
import com.example.spotifyclone.data.MusicCatalog
import com.example.spotifyclone.data.RecentlyPlayedManager
import com.example.spotifyclone.data.db.ListeningHistoryDao
import com.example.spotifyclone.data.db.ListeningHistoryDatabaseHelper
import com.example.spotifyclone.data.db.PopularTracksDao
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.TrackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository yang mengelola pemuatan lagu populer / trending resmi dari Spotify API
 * dan riwayat putar lokal pengguna secara terpisah.
 * 
 * - Popular Tracks: Murni mengambil trek trending dari Spotify Indonesia (market=ID),
 *   dengan sistem cache memori dan Room SQLite ber-TTL 6 jam.
 *   TIDAK PERNAH mengambil dari listening_history atau riwayat putar lokal.
 * - Recently Played Tracks: Khusus mengambil riwayat putar lokal dari Room database.
 */
class HomeRepository(
    private val trackService: TrackService = TrackService(),
    private val popularTracksDao: PopularTracksDao,
    private val listeningHistoryDao: ListeningHistoryDao? = null,
    private val recentlyPlayedManager: RecentlyPlayedManager? = null,
    private val musicRepository: MusicRepository? = null
) {
    constructor(context: Context) : this(
        trackService = TrackService(),
        popularTracksDao = ListeningHistoryDatabaseHelper.getInstance(context),
        listeningHistoryDao = ListeningHistoryDatabaseHelper.getInstance(context),
        recentlyPlayedManager = RecentlyPlayedManager(context),
        musicRepository = MusicRepository(context)
    )

    companion object {
        // Masa berlaku cache trending feed: 6 jam (TTL) sesuai spesifikasi
        const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

        // In-memory cache khusus feed trending untuk akses instan tanpa I/O berulang
        @Volatile
        private var memoryPopularCache: List<Track>? = null

        @Volatile
        private var memoryPopularTimestamp: Long = 0L
    }

    /**
     * Mengambil daftar lagu populer resmi dari tangga lagu resmi YouTube Music Charts.
     * Murni bersumber dari endpoint Charts dengan cache Room popular_tracks_cache.
     * Setiap lagu membawa videoId resmi untuk pemutaran instan tanpa jeda loading.
     */
    suspend fun getPopularTracks(forceRefresh: Boolean = false): List<Track> = withContext(Dispatchers.IO) {
        val repo = musicRepository ?: MusicRepository(
            popularTracksDao = popularTracksDao,
            listeningHistoryDao = listeningHistoryDao,
            recentlyPlayedManager = recentlyPlayedManager
        )
        repo.getPopularTracks(forceRefresh = forceRefresh)
    }

    /**
     * Memperbarui cache lagu populer secara eksplisit dari katalog resmi Spotify.
     */
    suspend fun refreshPopularTracks(): List<Track> {
        return getPopularTracks(forceRefresh = true)
    }

    /**
     * Khusus mengambil riwayat putar lokal pengguna dari Room database / file manager
     * untuk bagian "Baru Saja Diputar".
     */
    suspend fun getRecentlyPlayedTracks(limit: Int = 15): List<Track> = withContext(Dispatchers.IO) {
        // Ambil riwayat dari RecentlyPlayedManager untuk metadata lengkap
        val fromManager = recentlyPlayedManager?.getRecentlyPlayed().orEmpty()
        if (fromManager.isNotEmpty()) {
            return@withContext fromManager.take(limit)
        }

        // Fallback ke Room listening_history jika RecentlyPlayedManager kosong
        val historyEntities = listeningHistoryDao?.getAllHistory(limit).orEmpty()
        historyEntities.map { entity ->
            Track(
                id = entity.trackId,
                title = entity.trackTitle,
                artist = entity.artistName,
                album = "",
                durationSeconds = (entity.durationPlayedMs / 1000).toInt().coerceAtLeast(30),
                streamUrl = "",
                coverUrl = entity.albumArtUrl,
                genre = "History",
                spotifyArtistId = entity.artistId.ifBlank { null }
            )
        }
    }
}
