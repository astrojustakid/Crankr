package com.example.spotifyclone.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.data.repository.GenreMoodRepository
import com.example.spotifyclone.data.repository.SearchArtistProfile
import com.example.spotifyclone.data.repository.SearchRepository
import com.example.spotifyclone.data.repository.SearchResultData
import com.example.spotifyclone.service.DownloadManagerHelper
import com.example.spotifyclone.service.spotify.SpotifyApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI State untuk penjelajahan kategori Genre & Mood.
 */
sealed interface GenreUiState {
    object Idle : GenreUiState
    object Loading : GenreUiState
    data class Success(val genre: String, val tracks: List<Track>) : GenreUiState
    data class Error(val genre: String, val message: String, val fallbackTracks: List<Track> = emptyList()) : GenreUiState
}

/**
 * ViewModel khusus pencarian musik dan penjelajahan Genre/Mood:
 * - Menampilkan profil artis resmi di posisi paling atas saat mencari nama band seperti "The Skit".
 * - Memprioritaskan katalog resmi Spotify Web API (type=track,artist) dibanding kueri mentah YouTube.
 * - Membuang istilah sistem dan artefak web (pintasan keyboard, video bundar, watch full, subscribe, teaser).
 * - Mengoptimalkan loading kategori Genre/Mood dengan cache 12 jam, timeout 8 detik, dan shimmer feedback.
 */
class SearchViewModel @JvmOverloads constructor(
    application: Application,
    private val spotifyApiService: SpotifyApiService = SpotifyApiService(),
    private val genreMoodRepository: GenreMoodRepository = GenreMoodRepository(application, spotifyApiService),
    private val searchRepository: SearchRepository = SearchRepository(spotifyApiService, genreMoodRepository)
) : AndroidViewModel(application) {

    private val downloadManager = DownloadManagerHelper(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    private val _matchedArtist = MutableStateFlow<SearchArtistProfile?>(null)
    val matchedArtist: StateFlow<SearchArtistProfile?> = _matchedArtist.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _selectedGenre = MutableStateFlow("Semua")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    private val _selectedSourceFilter = MutableStateFlow("Semua")
    val selectedSourceFilter: StateFlow<String> = _selectedSourceFilter.asStateFlow()

    fun setSourceFilter(filter: String) {
        val clean = filter.trim()
        if (_selectedSourceFilter.value == clean) return
        _selectedSourceFilter.value = clean
        val query = _searchQuery.value.trim()
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                _isSearching.value = true
                _searchError.value = null
                executeSearchInternal(query)
            }
        }
    }

    private val _genreState = MutableStateFlow<GenreUiState>(GenreUiState.Idle)
    val genreState: StateFlow<GenreUiState> = _genreState.asStateFlow()

    private var searchJob: Job? = null
    private var genreJob: Job? = null

    // Daftar genre terkurasi untuk deteksi kueri eksplorasi
    private val knownGenres = setOf(
        "pop punk", "metalcore", "indie rock", "lo-fi chill", "lo-fi", "anime & j-rock",
        "j-rock", "pop indonesia", "hip-hop underground", "hip hop", "skate punk & grunge",
        "punk", "rock", "metal", "indie", "pop", "grunge"
    )

    fun onQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        _searchError.value = null

        if (newQuery.isBlank()) {
            searchJob?.cancel()
            _searchResults.value = emptyList()
            _matchedArtist.value = null
            _isSearching.value = false
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(350) // Debounce pengetikan
            executeSearchInternal(newQuery.trim())
        }
    }

    fun submitSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            executeSearchInternal(clean)
        }
    }

    /**
     * Menjalankan pencarian langsung berdasarkan penggalan lirik lagu.
     */
    fun searchByLyrics(lyricsSnippet: String) {
        val clean = lyricsSnippet.trim()
        if (clean.isBlank()) return
        _searchQuery.value = clean
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            executeLyricsSearchInternal(clean)
        }
    }

    private suspend fun executeLyricsSearchInternal(lyricsSnippet: String) = withContext(Dispatchers.IO) {
        try {
            val result = searchRepository.searchByLyrics(lyricsSnippet, limit = 20)
            val enrichedTracks = result.tracks.map { track ->
                val local = downloadManager.getLocalFilePath(track.id)
                track.copy(localFilePath = local)
            }
            withContext(Dispatchers.Main) {
                _matchedArtist.value = null
                _searchResults.value = enrichedTracks
                _isSearching.value = false
                if (enrichedTracks.isEmpty()) {
                    _searchError.value = "Tidak ada lagu yang cocok dengan potongan lirik ini"
                } else {
                    _searchError.value = null
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _isSearching.value = false
                _searchError.value = e.localizedMessage ?: "Gagal mencari berdasarkan lirik"
            }
        }
    }

    private suspend fun executeSearchInternal(query: String) = withContext(Dispatchers.IO) {
        if (_selectedSourceFilter.value == "⚡ Bandcamp") {
            try {
                val result = searchRepository.searchBandcampCatalog(query, limit = 25)
                val enrichedTracks = result.tracks.map { track ->
                    val local = downloadManager.getLocalFilePath(track.id)
                    track.copy(localFilePath = local)
                }
                withContext(Dispatchers.Main) {
                    _matchedArtist.value = result.matchedArtist
                    _searchResults.value = enrichedTracks
                    _isSearching.value = false
                    if (enrichedTracks.isEmpty() && result.matchedArtist == null) {
                        _searchError.value = "Tidak ada rilisan Bandcamp yang cocok dengan '$query'"
                    } else {
                        _searchError.value = null
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isSearching.value = false
                    _searchError.value = e.localizedMessage ?: "Gagal mencari katalog Bandcamp"
                }
            }
            return@withContext
        }

        val normalized = query.lowercase()
        val isGenreSearch = knownGenres.any { normalized.contains(it) || it.contains(normalized) }

        try {
            if (isGenreSearch) {
                // Kueri genre: gunakan pemanggilan kategori ber-cache 12 jam
                val genreTracks = genreMoodRepository.getCategoryTracks(query, limit = 20)
                val enriched = genreTracks.map { track ->
                    val local = downloadManager.getLocalFilePath(track.id)
                    track.copy(localFilePath = local)
                }

                withContext(Dispatchers.Main) {
                    _matchedArtist.value = null
                    _searchResults.value = enriched
                    _isSearching.value = false
                    _searchError.value = null
                }
                return@withContext
            }

            // Kueri pencarian artis atau lagu: utamakan Spotify Web API (type=track,artist)
            val result: SearchResultData = searchRepository.searchCatalog(query, limit = 20)
            val enrichedTracks = result.tracks.map { track ->
                val local = downloadManager.getLocalFilePath(track.id)
                track.copy(localFilePath = local)
            }

            withContext(Dispatchers.Main) {
                _matchedArtist.value = result.matchedArtist
                _searchResults.value = enrichedTracks
                _isSearching.value = false
                if (enrichedTracks.isEmpty() && result.matchedArtist == null) {
                    _searchError.value = "Tidak ada lagu yang cocok dengan kueri"
                } else {
                    _searchError.value = null
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _isSearching.value = false
                _searchError.value = e.localizedMessage ?: "Gagal memuat hasil pencarian"
            }
        }
    }

    /**
     * Memuat kategori Genre atau Mood dengan cache 12 jam dan penanganan timeout 8 detik.
     */
    fun loadGenreCategory(genre: String, forceRefresh: Boolean = false) {
        val clean = genre.trim()
        if (clean.isBlank()) return
        _selectedGenre.value = clean

        genreJob?.cancel()
        genreJob = viewModelScope.launch {
            _genreState.value = GenreUiState.Loading
            try {
                val tracks = genreMoodRepository.getCategoryTracks(clean, limit = 20, forceRefresh = forceRefresh)
                val enriched = tracks.map { track ->
                    val local = downloadManager.getLocalFilePath(track.id)
                    track.copy(localFilePath = local)
                }

                if (enriched.isNotEmpty()) {
                    _genreState.value = GenreUiState.Success(clean, enriched)
                    _searchResults.value = enriched
                } else {
                    _genreState.value = GenreUiState.Error(clean, "Kategori tidak dapat dimuat")
                }
            } catch (e: Exception) {
                _genreState.value = GenreUiState.Error(
                    clean,
                    e.localizedMessage ?: "Koneksi terputus saat memuat kategori"
                )
            }
        }
    }

    /**
     * Mengulang pencarian yang gagal.
     */
    fun retrySearch() {
        val query = _searchQuery.value.trim()
        if (query.isNotBlank()) {
            submitSearch(query)
        }
    }

    /**
     * Mengulang pemuatan kategori genre yang gagal ("Muat Ulang").
     */
    fun retryGenre() {
        val currentGenre = _selectedGenre.value
        if (currentGenre.isNotBlank() && currentGenre != "Semua") {
            loadGenreCategory(currentGenre, forceRefresh = true)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        genreJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _matchedArtist.value = null
        _isSearching.value = false
        _searchError.value = null
        _genreState.value = GenreUiState.Idle
    }
}
