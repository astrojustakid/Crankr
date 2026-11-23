package com.example.spotifyclone.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.data.repository.AlbumRelease
import com.example.spotifyclone.data.repository.ArtistRepository
import com.example.spotifyclone.data.repository.SimilarArtist
import com.example.spotifyclone.data.repository.RecommendationRepository
import com.example.spotifyclone.util.ArtistParser
import com.example.spotifyclone.util.CrankrCatalogFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Representasi state antarmuka untuk profil dan diskografi artis di Crankr.
 */
data class ArtistUiState(
    val artistId: String = "",
    val artistName: String = "",
    val officialPhotoUrl: String? = null,
    val discography: List<AlbumRelease> = emptyList(),
    val albums: List<AlbumRelease> = emptyList(),
    val singlesAndEPs: List<AlbumRelease> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingDiscography: Boolean = false,
    val error: String? = null
) {
    companion object {
        val Loading = ArtistUiState(isLoading = true, isLoadingDiscography = true)
        val Idle = ArtistUiState()
    }
}

/**
 * Dedicated ViewModel for artist profile exploration and verified discography loading.
 * Ensures automatic Spotify Artist ID resolution, strict ID and exact-name matching,
 * cancellation of previous jobs, instant state reset, and prevention of stale data or infinite loading.
 */
class ArtistViewModel @JvmOverloads constructor(
    application: Application,
    private val artistRepository: ArtistRepository = ArtistRepository(application),
    private val recommendationRepository: RecommendationRepository = RecommendationRepository()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ArtistUiState.Idle)
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    private val _selectedArtistName = MutableStateFlow<String?>(null)
    val selectedArtistName: StateFlow<String?> = _selectedArtistName.asStateFlow()

    private val _selectedArtistId = MutableStateFlow<String?>(null)
    val selectedArtistId: StateFlow<String?> = _selectedArtistId.asStateFlow()

    private val _artistOfficialPhoto = MutableStateFlow<String?>(null)
    val artistOfficialPhoto: StateFlow<String?> = _artistOfficialPhoto.asStateFlow()

    // Alur 1: Top Tracks StateFlow
    private val _topTracks = MutableStateFlow<List<Track>>(emptyList())
    val topTracks: StateFlow<List<Track>> = _topTracks.asStateFlow()
    val artistTracks: StateFlow<List<Track>> = _topTracks.asStateFlow()

    private val _isTopTracksLoading = MutableStateFlow(false)
    val isTopTracksLoading: StateFlow<Boolean> = _isTopTracksLoading.asStateFlow()
    val isLoadingArtist: StateFlow<Boolean> = _isTopTracksLoading.asStateFlow()

    // Alur 2: Diskografi StateFlow
    private val _diskografiList = MutableStateFlow<List<AlbumRelease>>(emptyList())
    val diskografiList: StateFlow<List<AlbumRelease>> = _diskografiList.asStateFlow()
    val artistDiscography: StateFlow<List<AlbumRelease>> = _diskografiList.asStateFlow()

    private val _artistAlbums = MutableStateFlow<List<AlbumRelease>>(emptyList())
    val artistAlbums: StateFlow<List<AlbumRelease>> = _artistAlbums.asStateFlow()

    private val _artistSinglesAndEPs = MutableStateFlow<List<AlbumRelease>>(emptyList())
    val artistSinglesAndEPs: StateFlow<List<AlbumRelease>> = _artistSinglesAndEPs.asStateFlow()

    private val _similarArtists = MutableStateFlow<List<SimilarArtist>>(emptyList())
    val similarArtists: StateFlow<List<SimilarArtist>> = _similarArtists.asStateFlow()

    private val _isDiskografiLoading = MutableStateFlow(false)
    val isDiskografiLoading: StateFlow<Boolean> = _isDiskografiLoading.asStateFlow()
    val isLoadingDiscography: StateFlow<Boolean> = _isDiskografiLoading.asStateFlow()

    // Variabel referensi kerja untuk coroutine pemanggilan artis
    private var topTracksJob: Job? = null
    private var discographyJob: Job? = null
    private var profileJob: Job? = null
    private var fetchJob: Job? = null
    var fetchArtistJob: Job?
        get() = fetchJob
        set(value) { fetchJob = value }
    private var currentLoadedArtistId: String? = null

    /**
     * Memisahkan eksekusi coroutine menjadi dua alur terpisah untuk Top Tracks dan Diskografi.
     * Menggunakan cache memori untuk pemuatan instan tanpa menampilkan skeleton.
     */
    fun loadArtistContent(artistId: String) {
        val cleanId = artistId.trim().removePrefix("sp_").removePrefix("spotify:artist:")

        topTracksJob?.cancel()
        discographyJob?.cancel()

        // Alur 1: Top Tracks (Instan dari in-memory cache jika tersedia)
        topTracksJob = viewModelScope.launch(Dispatchers.IO) {
            val cachedTracks = artistRepository.getCachedTopTracks(cleanId)
            if (!cachedTracks.isNullOrEmpty()) {
                _topTracks.value = cachedTracks
                _uiState.update { it.copy(tracks = cachedTracks, isLoading = false) }
                _isTopTracksLoading.value = false
            } else {
                _isTopTracksLoading.value = true
            }

            try {
                val tracks = artistRepository.getArtistTopTracks(cleanId)
                if (tracks.isNotEmpty()) {
                    _topTracks.value = tracks
                    _uiState.update { it.copy(tracks = tracks, isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("CRANKR_DEBUG", "Top tracks error: ${e.message}")
            } finally {
                _isTopTracksLoading.value = false
            }
        }

        // Alur 2: Diskografi (Semua rilisan album, single, dan EP dialirkan apa adanya tanpa filter)
        discographyJob = viewModelScope.launch(Dispatchers.IO) {
            val cachedAlbums = artistRepository.getCachedAlbums(cleanId)
            if (!cachedAlbums.isNullOrEmpty()) {
                _diskografiList.value = cachedAlbums
                _artistAlbums.value = cachedAlbums
                _artistSinglesAndEPs.value = cachedAlbums
                _uiState.update { current ->
                    current.copy(
                        discography = cachedAlbums,
                        albums = cachedAlbums,
                        singlesAndEPs = cachedAlbums,
                        isLoadingDiscography = false
                    )
                }
                _isDiskografiLoading.value = false
            } else {
                _isDiskografiLoading.value = true
            }

            try {
                val albums = artistRepository.getArtistAlbums(cleanId)
                    .filter { !it.id.startsWith("UC") && it.collectionId?.startsWith("UC") != true }
                if (albums.isNotEmpty()) {
                    _diskografiList.value = albums
                    _artistAlbums.value = albums
                    _artistSinglesAndEPs.value = albums
                    _uiState.update { current ->
                        current.copy(
                            discography = albums,
                            albums = albums,
                            singlesAndEPs = albums,
                            isLoadingDiscography = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("CRANKR_DEBUG", "Diskografi error: ${e.message}")
            } finally {
                _isDiskografiLoading.value = false
                _uiState.update { it.copy(isLoadingDiscography = false) }
            }
        }
    }

    /**
     * Menjalankan pemanggilan diskografi secara independen menggunakan coroutine scope.
     * Langsung memancarkan hasil tanpa filter apa pun dan segera mematikan loading spinner.
     */
    fun loadDiskografi(artistId: String) {
        val cleanId = artistId.trim().removePrefix("sp_").removePrefix("spotify:artist:")
        discographyJob?.cancel()
        discographyJob = viewModelScope.launch(Dispatchers.IO) {
            val cachedAlbums = artistRepository.getCachedAlbums(cleanId)
            if (!cachedAlbums.isNullOrEmpty()) {
                _diskografiList.value = cachedAlbums
                _artistAlbums.value = cachedAlbums
                _artistSinglesAndEPs.value = cachedAlbums
                _uiState.update { current ->
                    current.copy(
                        discography = cachedAlbums,
                        albums = cachedAlbums,
                        singlesAndEPs = cachedAlbums,
                        isLoadingDiscography = false
                    )
                }
                _isDiskografiLoading.value = false
            } else {
                _isDiskografiLoading.value = true
            }

            try {
                val albums = artistRepository.getArtistAlbums(cleanId)
                    .filter { !it.id.startsWith("UC") && it.collectionId?.startsWith("UC") != true }
                if (albums.isNotEmpty()) {
                    _diskografiList.value = albums
                    _artistAlbums.value = albums
                    _artistSinglesAndEPs.value = albums
                    _uiState.update { current ->
                        current.copy(
                            discography = albums,
                            albums = albums,
                            singlesAndEPs = albums,
                            isLoadingDiscography = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("CRANKR_DEBUG", "Diskografi error: ${e.message}")
            } finally {
                _isDiskografiLoading.value = false
                _uiState.update { it.copy(isLoadingDiscography = false) }
            }
        }
    }

    /**
     * Memuat profil dan diskografi artis menggunakan artistId secara unik dan instan tanpa filter.
     */
    fun loadArtist(artistId: String, artistName: String? = null) {
        val cleanId = artistId.trim().removePrefix("sp_").removePrefix("spotify:artist:")
        if (cleanId.isBlank()) return

        topTracksJob?.cancel()
        discographyJob?.cancel()
        profileJob?.cancel()
        fetchJob?.cancel()

        val cachedTracks = artistRepository.getCachedTopTracks(cleanId)
        val cachedAlbums = artistRepository.getCachedAlbums(cleanId)

        currentLoadedArtistId = cleanId
        _selectedArtistId.value = cleanId
        _selectedArtistName.value = artistName
        _artistOfficialPhoto.value = null
        _topTracks.value = cachedTracks ?: emptyList()
        _diskografiList.value = cachedAlbums ?: emptyList()
        _artistAlbums.value = cachedAlbums ?: emptyList()
        _artistSinglesAndEPs.value = cachedAlbums ?: emptyList()
        _isTopTracksLoading.value = cachedTracks.isNullOrEmpty()
        _isDiskografiLoading.value = cachedAlbums.isNullOrEmpty()
        _uiState.update {
            it.copy(
                artistId = cleanId,
                artistName = artistName.orEmpty(),
                tracks = cachedTracks ?: emptyList(),
                discography = cachedAlbums ?: emptyList(),
                albums = cachedAlbums ?: emptyList(),
                singlesAndEPs = cachedAlbums ?: emptyList(),
                isLoading = cachedTracks.isNullOrEmpty(),
                isLoadingDiscography = cachedAlbums.isNullOrEmpty()
            )
        }

        // Panggil Top Tracks dan Diskografi di 2 alur mandiri paralel
        loadArtistContent(cleanId)

        // Alur terpisah secara paralel: Resolusi nama & foto resmi artis
        profileJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetName = if (!artistName.isNullOrBlank()) {
                    artistRepository.canonicalizeArtistName(artistName)
                } else {
                    artistRepository.getArtistNameById(cleanId) ?: cleanId
                }

                _selectedArtistName.value = targetName
                _uiState.update { it.copy(artistName = targetName) }

                val photoUrl = artistRepository.getOfficialArtistPhoto(targetName, cleanId)
                _artistOfficialPhoto.value = photoUrl
                _uiState.update { it.copy(officialPhotoUrl = photoUrl) }
            } catch (e: Exception) {
                Log.e("CRANKR_DEBUG", "Error resolusi profil: ${e.message}")
            }
        }
    }

    /**
     * Backward-compatible overload for selectArtist.
     */
    fun selectArtist(artistName: String, explicitArtistId: String? = null) {
        val cleanName = artistRepository.canonicalizeArtistName(artistName)
        if (cleanName.isBlank()) return

        val validExplicitId = if (explicitArtistId?.startsWith("bc_") == true || artistRepository.isValidSpotifyArtistId(explicitArtistId)) explicitArtistId?.trim() else null

        if (validExplicitId != null) {
            loadArtist(validExplicitId, cleanName)
            return
        }

        topTracksJob?.cancel()
        discographyJob?.cancel()
        profileJob?.cancel()
        fetchJob?.cancel()

        currentLoadedArtistId = cleanName
        _selectedArtistName.value = cleanName
        _selectedArtistId.value = null
        _artistOfficialPhoto.value = null
        _diskografiList.value = emptyList()
        _topTracks.value = emptyList()
        _artistAlbums.value = emptyList()
        _artistSinglesAndEPs.value = emptyList()
        _similarArtists.value = emptyList()
        _isTopTracksLoading.value = true
        _isDiskografiLoading.value = true
        _uiState.update { ArtistUiState.Loading.copy(artistName = cleanName, isLoading = true, isLoadingDiscography = true) }

        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetArtistId = artistRepository.searchArtistByName(cleanName) ?: cleanName
                _selectedArtistId.value = targetArtistId
                _uiState.update { it.copy(artistId = targetArtistId) }

                loadArtistContent(targetArtistId)

                val photoUrl = artistRepository.getOfficialArtistPhoto(cleanName, targetArtistId)
                _artistOfficialPhoto.value = photoUrl
                _uiState.update { it.copy(officialPhotoUrl = photoUrl) }

                val similar = artistRepository.getSimilarArtists(cleanName)
                _similarArtists.value = similar
            } catch (e: Exception) {
                Log.e("CRANKR_DEBUG", "Gagal search artist: ${e.message}")
                _isTopTracksLoading.value = false
                _isDiskografiLoading.value = false
                _uiState.update { it.copy(isLoading = false, isLoadingDiscography = false) }
            }
        }
    }

    fun closeArtistProfile() {
        topTracksJob?.cancel()
        discographyJob?.cancel()
        profileJob?.cancel()
        fetchJob?.cancel()
        currentLoadedArtistId = null
        _selectedArtistName.value = null
        _selectedArtistId.value = null
        _topTracks.value = emptyList()
        _artistOfficialPhoto.value = null
        _diskografiList.value = emptyList()
        _artistAlbums.value = emptyList()
        _artistSinglesAndEPs.value = emptyList()
        _similarArtists.value = emptyList()
        _uiState.value = ArtistUiState.Idle
        _isTopTracksLoading.value = false
        _isDiskografiLoading.value = false
    }
}
