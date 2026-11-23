package com.example.spotifyclone.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.data.repository.HomeRepository
import com.example.spotifyclone.data.repository.MusicRepository
import com.example.spotifyclone.service.AudioStreamResolver
import com.example.spotifyclone.service.DownloadManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI State terpisah untuk bagian Popular Tracks (Spotify Indonesia Trending Feed).
 */
sealed interface PopularTracksUiState {
    object Loading : PopularTracksUiState
    data class Success(val tracks: List<Track>) : PopularTracksUiState
    data class Error(val message: String, val tracks: List<Track> = emptyList()) : PopularTracksUiState
}

/**
 * UI State terpisah untuk bagian Recently Played (Riwayat Putar Lokal Room).
 */
sealed interface RecentlyPlayedUiState {
    object Loading : RecentlyPlayedUiState
    data class Success(val tracks: List<Track>) : RecentlyPlayedUiState
    object Empty : RecentlyPlayedUiState
}

/**
 * UI State terpisah untuk bagian Trending Songs For You (Lagu Tren Untuk Anda).
 */
sealed interface TrendingSongsUiState {
    object Loading : TrendingSongsUiState
    data class Success(val tracks: List<Track>) : TrendingSongsUiState
    data class Error(val message: String, val tracks: List<Track> = emptyList()) : TrendingSongsUiState
    object Empty : TrendingSongsUiState
}

/**
 * ViewModel untuk layar Beranda (HomeScreen) yang memisahkan aliran data secara tegas:
 * 1. [popularTracksState]: Murni bersumber dari endpoint publik Spotify Indonesia (Top 50 / artis skena)
 *    dengan cache memori & Room khusus ber-TTL 6 jam. Tidak pernah mengambil dari riwayat putar.
 * 2. [recentlyPlayedState]: Khusus mengambil riwayat putar lokal dari Room database / file manager.
 * 3. [trendingSongsState]: Rekomendasi lagu tren personal dari YouTube Music InnerTube Charts/Explore & riwayat dengar lokal.
 */
class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val homeRepository: HomeRepository = HomeRepository(application),
    private val musicRepository: MusicRepository = MusicRepository(application)
) : AndroidViewModel(application) {

    private val downloadManager = DownloadManagerHelper(application)

    // Aliran data 1: Popular Tracks (Trending Spotify Indonesia)
    private val _popularTracksState = MutableStateFlow<PopularTracksUiState>(PopularTracksUiState.Loading)
    val popularTracksState: StateFlow<PopularTracksUiState> = _popularTracksState.asStateFlow()

    private val _popularTracks = MutableStateFlow<List<Track>>(emptyList())
    val popularTracks: StateFlow<List<Track>> = _popularTracks.asStateFlow()

    // Aliran data 2: Recently Played Tracks (Riwayat Putar Lokal Room)
    private val _recentlyPlayedState = MutableStateFlow<RecentlyPlayedUiState>(RecentlyPlayedUiState.Loading)
    val recentlyPlayedState: StateFlow<RecentlyPlayedUiState> = _recentlyPlayedState.asStateFlow()

    private val _recentlyPlayedTracks = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayedTracks: StateFlow<List<Track>> = _recentlyPlayedTracks.asStateFlow()

    // Aliran data 3: Trending Songs For You (Rekomendasi Tren Personal Auralys / YouTube Music)
    private val _trendingSongsState = MutableStateFlow<TrendingSongsUiState>(TrendingSongsUiState.Loading)
    val trendingSongsState: StateFlow<TrendingSongsUiState> = _trendingSongsState.asStateFlow()

    private val _trendingSongs = MutableStateFlow<List<Track>>(emptyList())
    val trendingSongs: StateFlow<List<Track>> = _trendingSongs.asStateFlow()

    init {
        loadPopularTracks()
        loadRecentlyPlayed()
        loadTrendingSongs()
    }

    /**
     * Memuat daftar trek populer resmi dari YouTube Music Charts Indonesia (Top 100).
     * Setiap trek membawa videoId resmi untuk pemutaran instan tanpa jeda pencarian berulang.
     */
    fun loadPopularTracks(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (_popularTracks.value.isEmpty() || forceRefresh) {
                _popularTracksState.value = PopularTracksUiState.Loading
            }

            try {
                val enriched = withContext(Dispatchers.IO) {
                    val fetched = musicRepository.getPopularTracks(forceRefresh = forceRefresh)
                    fetched.map { track ->
                        val localPath = downloadManager.getLocalFilePath(track.id)
                        track.copy(localFilePath = localPath)
                    }
                }

                _popularTracks.value = enriched
                _popularTracksState.value = PopularTracksUiState.Success(enriched)

                // Ekstraksi tautan audio di latar belakang untuk lagu populer teratas ke cache memori (0ms saat diketuk)
                viewModelScope.launch(Dispatchers.IO) {
                    enriched.take(8).forEach { track ->
                        try {
                            AudioStreamResolver.preFetchTrackStream(track)
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                val current = _popularTracks.value
                _popularTracksState.value = PopularTracksUiState.Error(
                    message = e.localizedMessage ?: "Gagal memuat tangga lagu populer",
                    tracks = current
                )
            }
        }
    }

    /**
     * Memperbarui cache tangga lagu populer secara eksplisit.
     */
    fun refreshPopularTracks() {
        loadPopularTracks(forceRefresh = true)
    }

    /**
     * Khusus memuat riwayat putar lokal dari Room database / RecentlyPlayedManager.
     */
    fun loadRecentlyPlayed() {
        viewModelScope.launch {
            try {
                val enriched = withContext(Dispatchers.IO) {
                    val history = homeRepository.getRecentlyPlayedTracks()
                    history.map { track ->
                        val localPath = downloadManager.getLocalFilePath(track.id)
                        track.copy(localFilePath = localPath)
                    }
                }

                _recentlyPlayedTracks.value = enriched
                if (enriched.isNotEmpty()) {
                    _recentlyPlayedState.value = RecentlyPlayedUiState.Success(enriched)
                } else {
                    _recentlyPlayedState.value = RecentlyPlayedUiState.Empty
                }
            } catch (_: Exception) {
                _recentlyPlayedState.value = RecentlyPlayedUiState.Empty
            }
        }
    }

    /**
     * Memperbarui riwayat putar lokal ketika ada trek baru yang selesai atau sedang diputar.
     */
    fun refreshRecentlyPlayed() {
        loadRecentlyPlayed()
    }

    /**
     * Memuat daftar lagu tren personal (Trending Songs For You) secara asinkron
     * saat halaman Home pertama kali dibuka tanpa menghambat pemuatan data utama.
     * Hasil respons disimpan ke memori cache sementara agar tidak membebani kuota data saat pengguna berpindah tab.
     */
    fun loadTrendingSongs(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (_trendingSongs.value.isEmpty() || forceRefresh) {
                _trendingSongsState.value = TrendingSongsUiState.Loading
            }

            try {
                val enriched = withContext(Dispatchers.IO) {
                    val fetched = musicRepository.getTrendingForYou(limit = 20, forceRefresh = forceRefresh)
                    fetched.map { track ->
                        val localPath = downloadManager.getLocalFilePath(track.id)
                        track.copy(localFilePath = localPath)
                    }
                }

                _trendingSongs.value = enriched
                if (enriched.isNotEmpty()) {
                    _trendingSongsState.value = TrendingSongsUiState.Success(enriched)

                    // Ekstraksi tautan audio di latar belakang untuk lagu tren teratas ke cache memori (0ms saat diketuk)
                    viewModelScope.launch(Dispatchers.IO) {
                        enriched.take(8).forEach { track ->
                            try {
                                AudioStreamResolver.preFetchTrackStream(track)
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    _trendingSongsState.value = TrendingSongsUiState.Empty
                }
            } catch (e: Exception) {
                val current = _trendingSongs.value
                if (current.isNotEmpty()) {
                    _trendingSongsState.value = TrendingSongsUiState.Success(current)
                } else {
                    _trendingSongsState.value = TrendingSongsUiState.Error(
                        message = e.localizedMessage ?: "Gagal memuat rekomendasi lagu tren",
                        tracks = current
                    )
                }
            }
        }
    }

    /**
     * Memperbarui daftar lagu tren personal secara eksplisit.
     */
    fun refreshTrendingSongs() {
        loadTrendingSongs(forceRefresh = true)
    }
}
