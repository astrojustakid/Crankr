package com.example.spotifyclone.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.spotifyclone.data.ListeningStats
import com.example.spotifyclone.data.ListeningStatsManager
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.spotifyclone.service.MediaCacheManager
import com.example.spotifyclone.service.PcmEqualizerAudioProcessor
import com.example.spotifyclone.data.MusicCatalog
import com.example.spotifyclone.data.LikedSongsManager
import com.example.spotifyclone.data.PlaylistStorageManager
import com.example.spotifyclone.data.RecentlyPlayedManager
import com.example.spotifyclone.data.SearchHistoryManager
import com.example.spotifyclone.data.db.ListeningHistoryDatabaseHelper
import com.example.spotifyclone.data.db.ListeningHistoryEntity
import com.example.spotifyclone.data.model.LyricsData
import com.example.spotifyclone.data.model.SpotifyPlaylist
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.ui.components.PlaylistImportProgress
import com.example.spotifyclone.service.AudioStreamResolver
import com.example.spotifyclone.service.NetworkQualityMonitor
import com.example.spotifyclone.service.DownloadManagerHelper
import com.example.spotifyclone.service.EqualizerHandler
import com.example.spotifyclone.service.MusicNotificationReceiver
import com.example.spotifyclone.service.MusicPlaybackService
import com.example.spotifyclone.service.MusicStreamResolver
import com.example.spotifyclone.service.TrackCoverResolver
import com.example.spotifyclone.service.YouTubeAudioEngine
import com.example.spotifyclone.service.lyrics.LyricsService
import com.example.spotifyclone.data.repository.LyricsRepository
import com.example.spotifyclone.service.share.StoryBackgroundStyle
import com.example.spotifyclone.service.spotify.SpotifyApiService
import com.example.spotifyclone.service.youtube.YouTubeMusicService
import com.example.spotifyclone.service.ListenTogetherManager
import com.example.spotifyclone.data.model.ListenTogetherRoomState
import com.example.spotifyclone.data.model.RoomRole
import com.example.spotifyclone.data.repository.AlbumRelease
import com.example.spotifyclone.data.repository.ArtistRepository
import com.example.spotifyclone.data.repository.BandcampRepository
import com.example.spotifyclone.data.repository.GenreMoodRepository
import com.example.spotifyclone.data.repository.HomeRepository
import com.example.spotifyclone.data.repository.RecommendationRepository
import com.example.spotifyclone.data.repository.SearchArtistProfile
import com.example.spotifyclone.util.MetadataSanitizer
import com.example.spotifyclone.data.repository.SearchRepository
import com.example.spotifyclone.data.repository.SearchResultData
import com.example.spotifyclone.data.repository.YouTubeMusicRepository
import com.example.spotifyclone.util.ArtistParser
import com.example.spotifyclone.util.CrankrCatalogFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class ImportSummary(
    val playlist: SpotifyPlaylist,
    val matchedTracks: List<Track>,
    val unmatchedTracks: List<Track>
)

enum class RepeatMode {
    OFF, ALL, ONE
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private fun DefaultRenderersFactory.setEnableAudioOffload(enable: Boolean): DefaultRenderersFactory = this

    val pcmEqualizerAudioProcessor = PcmEqualizerAudioProcessor()

    private val downloadManager = DownloadManagerHelper(application)

    @OptIn(UnstableApi::class)
    private val renderersFactory = object : DefaultRenderersFactory(application) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink? {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false) // Memaksa PCM 16-bit agar selalu diproses oleh PcmEqualizerAudioProcessor
                .setAudioProcessors(arrayOf(pcmEqualizerAudioProcessor))
                .build()
        }
    }.setEnableAudioOffload(false)

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    // 1. Penyetelan DefaultLoadControl untuk Latensi Rendah dan Penimbunan Buffer
    // bufferForPlaybackMs = 300 ms, bufferForPlaybackAfterRebufferMs = 500 ms agar lagu langsung berbunyi seketika
    // maxBufferMs = 120000 ms (2 menit) agar pemutar terus menimbun buffer data selama koneksi tersedia
    private val instantLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 15000,
            /* maxBufferMs = */ 120000,
            /* bufferForPlaybackMs = */ 300,
            /* bufferForPlaybackAfterRebufferMs = */ 500
        )
        .setBackBuffer(30000, true)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    // 2. Disk Caching ExoPlayer (100 MB via SimpleCache & CacheDataSource.Factory)
    private val cachedMediaSourceFactory = DefaultMediaSourceFactory(application)
        .setDataSourceFactory(MediaCacheManager.createCacheDataSourceFactory(application))

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(application, renderersFactory)
        .setMediaSourceFactory(cachedMediaSourceFactory)
        .setLoadControl(instantLoadControl)
        .setAudioAttributes(audioAttributes, true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setHandleAudioBecomingNoisy(true)
        .build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                        .build()
                )
                .build()
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 1.0f
        }

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())
    val allTracks: StateFlow<List<Track>> = _allTracks.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(1L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat: StateFlow<Boolean> = _isRepeat.asStateFlow()

    // Playback Queue (Independent of screen navigation)
    private val _playbackQueue = MutableStateFlow<List<Track>>(emptyList())
    val playbackQueue: StateFlow<List<Track>> = _playbackQueue.asStateFlow()

    private val _playbackQueueTitle = MutableStateFlow<String>("Antrean Musik")
    val playbackQueueTitle: StateFlow<String> = _playbackQueueTitle.asStateFlow()

    private var currentQueueIndex: Int = 0
    private var originalPlaybackQueue: List<Track>? = null

    // Playback History Stack (untuk tombol previous di mode shuffle & normal)
    private val playbackHistoryStack = java.util.ArrayDeque<Track>()
    private var isNavigatingHistory: Boolean = false

    // Quick Search on Home
    private val _searchQuickResults = MutableStateFlow<List<Track>>(emptyList())
    val searchQuickResults: StateFlow<List<Track>> = _searchQuickResults.asStateFlow()

    private val _isSearchingQuick = MutableStateFlow(false)
    val isSearchingQuick: StateFlow<Boolean> = _isSearchingQuick.asStateFlow()

    private val _quickSearchQuery = MutableStateFlow("")
    val quickSearchQuery: StateFlow<String> = _quickSearchQuery.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow("All")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    private val _isOfflineOnlyMode = MutableStateFlow(false)
    val isOfflineOnlyMode: StateFlow<Boolean> = _isOfflineOnlyMode.asStateFlow()

    private val _storageUsage = MutableStateFlow("0.0 MB")
    val storageUsage: StateFlow<String> = _storageUsage.asStateFlow()

    private val _favoriteTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteTrackIds: StateFlow<Set<String>> = _favoriteTrackIds.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Listen Together (Putar Bersama) WebSocket Real-time Manager
    val listenTogetherManager = ListenTogetherManager(application)
    val isListenTogetherGuest: StateFlow<Boolean> = listenTogetherManager.isListenTogetherGuest
    val isListenTogetherHost: StateFlow<Boolean> = listenTogetherManager.isListenTogetherHost
    val listenTogetherRoomState: StateFlow<ListenTogetherRoomState> = listenTogetherManager.roomState
    val listenTogetherAutoApproveSuggestions: StateFlow<Boolean> = listenTogetherManager.listenTogetherAutoApproveSuggestions
    val listenTogetherPendingSuggestions: StateFlow<List<Pair<Track, String>>> = listenTogetherManager.pendingSuggestions

    private var isSyncingFromRemote = false

    // Spotify & YouTube Services
    private val spotifyApiService = SpotifyApiService()
    val youTubeMusicService = YouTubeMusicService()
    private val playlistStorageManager = PlaylistStorageManager(application)
    private val playlistRepository = com.example.spotifyclone.data.repository.PlaylistRepository(application, playlistStorageManager)
    private val likedSongsManager = LikedSongsManager(application)
    private val playlistTracksMap = mutableMapOf<String, MutableList<Track>>()
    private val lyricsRepository = LyricsRepository()
    private val lyricsService = LyricsService(lyricsRepository)
    private val recentlyPlayedManager = RecentlyPlayedManager(application)
    private val searchHistoryManager = SearchHistoryManager(application)

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _exploreSearchQuery = MutableStateFlow("")
    val exploreSearchQuery: StateFlow<String> = _exploreSearchQuery.asStateFlow()

    private val _exploreSearchResults = MutableStateFlow<List<Track>>(emptyList())
    val exploreSearchResults: StateFlow<List<Track>> = _exploreSearchResults.asStateFlow()

    private val _exploreMatchedArtist = MutableStateFlow<SearchArtistProfile?>(null)
    val exploreMatchedArtist: StateFlow<SearchArtistProfile?> = _exploreMatchedArtist.asStateFlow()

    private val _exploreSearchError = MutableStateFlow<String?>(null)
    val exploreSearchError: StateFlow<String?> = _exploreSearchError.asStateFlow()

    private val _isSearchingExplore = MutableStateFlow(false)
    val isSearchingExplore: StateFlow<Boolean> = _isSearchingExplore.asStateFlow()

    private val _exploreSourceFilter = MutableStateFlow("Semua")
    val exploreSourceFilter: StateFlow<String> = _exploreSourceFilter.asStateFlow()

    private val _bandcampPurchaseTrack = MutableStateFlow<Track?>(null)
    val bandcampPurchaseTrack: StateFlow<Track?> = _bandcampPurchaseTrack.asStateFlow()

    fun dismissBandcampPurchaseDialog() {
        _bandcampPurchaseTrack.value = null
    }

    private val _isAutoplayEnabled = MutableStateFlow(true)
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()

    private val _likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val likedTracks: StateFlow<List<Track>> = _likedTracks.asStateFlow()

    private val _spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val spotifyPlaylists: StateFlow<List<SpotifyPlaylist>> = _spotifyPlaylists.asStateFlow()

    private val _isSyncingSpotify = MutableStateFlow(false)
    val isSyncingSpotify: StateFlow<Boolean> = _isSyncingSpotify.asStateFlow()

    private val _playlistImportProgress = MutableStateFlow<PlaylistImportProgress?>(null)
    val playlistImportProgress: StateFlow<PlaylistImportProgress?> = _playlistImportProgress.asStateFlow()

    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary: StateFlow<ImportSummary?> = _importSummary.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<SpotifyPlaylist?>(null)
    val selectedPlaylist: StateFlow<SpotifyPlaylist?> = _selectedPlaylist.asStateFlow()

    private val _selectedPlaylistTracks = MutableStateFlow<List<Track>>(emptyList())
    val selectedPlaylistTracks: StateFlow<List<Track>> = _selectedPlaylistTracks.asStateFlow()

    private val _syncingPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val syncingPlaylistIds: StateFlow<Set<String>> = _syncingPlaylistIds.asStateFlow()

    // Artist Profile & Official Catalog States
    private val artistRepository = ArtistRepository(application)
    private val recommendationRepository = RecommendationRepository(spotifyApiService, artistRepository)
    private val homeRepository = HomeRepository(application)
    private val genreMoodRepository = GenreMoodRepository(application, spotifyApiService)
    private val searchRepository = SearchRepository(spotifyApiService, genreMoodRepository)
    private val youTubeMusicRepository by lazy { YouTubeMusicRepository(application) }

    private val _isLoadingDiscography = MutableStateFlow(false)
    val isLoadingDiscography: StateFlow<Boolean> = _isLoadingDiscography.asStateFlow()

    private val _selectedArtistName = MutableStateFlow<String?>(null)
    val selectedArtistName: StateFlow<String?> = _selectedArtistName.asStateFlow()

    private val _selectedArtistId = MutableStateFlow<String?>(null)
    val selectedArtistId: StateFlow<String?> = _selectedArtistId.asStateFlow()

    private val _artistOfficialPhoto = MutableStateFlow<String?>(null)
    val artistOfficialPhoto: StateFlow<String?> = _artistOfficialPhoto.asStateFlow()

    private val _artistDiscography = MutableStateFlow<List<AlbumRelease>>(emptyList())
    val artistDiscography: StateFlow<List<AlbumRelease>> = _artistDiscography.asStateFlow()

    private val _artistAlbums = MutableStateFlow<List<AlbumRelease>>(emptyList())
    val artistAlbums: StateFlow<List<AlbumRelease>> = _artistAlbums.asStateFlow()

    private val _artistSinglesAndEPs = MutableStateFlow<List<AlbumRelease>>(emptyList())
    val artistSinglesAndEPs: StateFlow<List<AlbumRelease>> = _artistSinglesAndEPs.asStateFlow()

    private fun updateDiscographyState(releases: List<AlbumRelease>) {
        val cleanReleases = releases.filter { !it.id.startsWith("UC") && it.collectionId?.startsWith("UC") != true }
        _artistDiscography.value = cleanReleases
        _artistAlbums.value = cleanReleases.filter { it.releaseType.equals("Album", ignoreCase = true) }
        _artistSinglesAndEPs.value = cleanReleases.filter { !it.releaseType.equals("Album", ignoreCase = true) }
    }

    private val _selectedAlbumRelease = MutableStateFlow<AlbumRelease?>(null)
    val selectedAlbumRelease: StateFlow<AlbumRelease?> = _selectedAlbumRelease.asStateFlow()

    private val _albumDetailTracks = MutableStateFlow<List<Track>>(emptyList())
    val albumDetailTracks: StateFlow<List<Track>> = _albumDetailTracks.asStateFlow()

    private val _isLoadingAlbumDetail = MutableStateFlow(false)
    val isLoadingAlbumDetail: StateFlow<Boolean> = _isLoadingAlbumDetail.asStateFlow()

    private val _artistTracks = MutableStateFlow<List<Track>>(emptyList())
    val artistTracks: StateFlow<List<Track>> = _artistTracks.asStateFlow()

    private val _similarArtists = MutableStateFlow<List<com.example.spotifyclone.data.repository.SimilarArtist>>(emptyList())
    val similarArtists: StateFlow<List<com.example.spotifyclone.data.repository.SimilarArtist>> = _similarArtists.asStateFlow()

    private val _isLoadingArtist = MutableStateFlow(false)
    val isLoadingArtist: StateFlow<Boolean> = _isLoadingArtist.asStateFlow()

    private var artistJob: Job? = null
    private var albumDetailJob: Job? = null

    // Playlist Genre-Based Recommendations
    private val _playlistRecommendations = MutableStateFlow<List<Track>>(emptyList())
    val playlistRecommendations: StateFlow<List<Track>> = _playlistRecommendations.asStateFlow()

    private val _isLoadingRecommendations = MutableStateFlow(false)
    val isLoadingRecommendations: StateFlow<Boolean> = _isLoadingRecommendations.asStateFlow()

    private val _dominantGenre = MutableStateFlow<String>("")
    val dominantGenre: StateFlow<String> = _dominantGenre.asStateFlow()

    private var recommendationJob: Job? = null

    // Extended Features States
    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()

    private val _currentLyricsData = MutableStateFlow<LyricsData?>(null)
    val currentLyricsData: StateFlow<LyricsData?> = _currentLyricsData.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private val _sleepTimerRemainingMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingMinutes: StateFlow<Int?> = _sleepTimerRemainingMinutes.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()

    private val _sleepTimerMode = MutableStateFlow<String?>(null) // "MINUTES", "END_OF_TRACK", or null
    val sleepTimerMode: StateFlow<String?> = _sleepTimerMode.asStateFlow()

    // Crankr Preferences & Audio Settings
    private val crankrPrefs = application.getSharedPreferences("crankr_prefs", Context.MODE_PRIVATE)

    private val _currentEqPreset = MutableStateFlow(crankrPrefs.getString("eq_preset", "Flat") ?: "Flat")
    val currentEqPreset: StateFlow<String> = _currentEqPreset.asStateFlow()

    private val _bassGain = MutableStateFlow(crankrPrefs.getFloat("eq_bass_gain", 0.5f))
    val bassGain: StateFlow<Float> = _bassGain.asStateFlow()

    private val _trebleGain = MutableStateFlow(crankrPrefs.getFloat("eq_treble_gain", 0.5f))
    val trebleGain: StateFlow<Float> = _trebleGain.asStateFlow()

    // Hardware Audio Effects (Real Equalizer, BassBoost, LoudnessEnhancer via AudioSessionId)
    val equalizerHandler = EqualizerHandler(application, exoPlayer)
    var equalizer: Equalizer? = null
        private set
    var bassBoost: BassBoost? = null
        private set
    var loudnessEnhancer: LoudnessEnhancer? = null
        private set
    private var currentAttachedSessionId: Int = 0

    fun initAudioEffects(sessionId: Int) {
        if (sessionId <= 0 || sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == android.media.AudioTrack.ERROR_BAD_VALUE) return
        currentAttachedSessionId = sessionId

        val savedPreamp = com.example.spotifyclone.service.AuralysSettingsManager.getInstance(getApplication()).preampGainDb.value
        val targetGainMb = if (savedPreamp > 0f) (savedPreamp * 100).toInt() else 800

        // 1. LoudnessEnhancer: targetGain dan enabled = true setiap kali sesi aktif
        try {
            try {
                loudnessEnhancer?.release()
            } catch (_: Exception) {}
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                setTargetGain(targetGainMb)
                enabled = true
            }
            Log.d("CRANKR_AUDIO", "LoudnessEnhancer aktif: sessionId=$sessionId, targetGain=${targetGainMb}mB")
        } catch (e: Exception) {
            Log.w("CRANKR_AUDIO", "LoudnessEnhancer gagal: ${e.message}")
        }

        // 2. Hardware Equalizer
        try {
            try {
                equalizer?.release()
            } catch (_: Exception) {}
            equalizer = Equalizer(Int.MAX_VALUE, sessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            Log.w("CRANKR_AUDIO", "Hardware Equalizer tidak didukung: ${e.message}")
            equalizer = null
        }

        // 3. Hardware BassBoost
        try {
            try {
                bassBoost?.release()
            } catch (_: Exception) {}
            bassBoost = BassBoost(Int.MAX_VALUE, sessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            Log.w("CRANKR_AUDIO", "Hardware BassBoost tidak didukung: ${e.message}")
            bassBoost = null
        }

        equalizerHandler.setupEqualizer(sessionId)
        equalizerHandler.ensureSessionAttached()
        equalizerHandler.setPreampGain(savedPreamp)
        pcmEqualizerAudioProcessor.setPreampGainDb(savedPreamp)
        applyCurrentEqualizerSettings()
    }

    fun initEqualizer(sessionId: Int) = initAudioEffects(sessionId)

    private val _trackToAddToPlaylist = MutableStateFlow<Track?>(null)
    val trackToAddToPlaylist: StateFlow<Track?> = _trackToAddToPlaylist.asStateFlow()

    private val _recentlyPlayedTracks = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayedTracks: StateFlow<List<Track>> = _recentlyPlayedTracks.asStateFlow()

    // Listening Analytics & Persona Manager
    private val listeningStatsManager = ListeningStatsManager(application)
    private val listeningHistoryDbHelper = ListeningHistoryDatabaseHelper.getInstance(application)
    private val _listeningStats = MutableStateFlow(listeningStatsManager.getStats())
    val listeningStats: StateFlow<ListeningStats> = _listeningStats.asStateFlow()
    private var activeTrackPlayDurationMs: Long = 0L
    private var hasRecordedHistoryForCurrentTrack: Boolean = false

    // Crossfade & Gapless Settings
    private val _crossfadeDurationSeconds = MutableStateFlow(crankrPrefs.getInt("crossfade_sec", 3))
    val crossfadeDurationSeconds: StateFlow<Int> = _crossfadeDurationSeconds.asStateFlow()

    // Share Story Canvas Background Style
    private val _storyBackgroundStyle = MutableStateFlow(
        try {
            val saved = crankrPrefs.getString("story_bg_style", StoryBackgroundStyle.SOLID_BLACK.name)
            StoryBackgroundStyle.valueOf(saved ?: StoryBackgroundStyle.SOLID_BLACK.name)
        } catch (_: Exception) {
            StoryBackgroundStyle.SOLID_BLACK
        }
    )
    val storyBackgroundStyle: StateFlow<StoryBackgroundStyle> = _storyBackgroundStyle.asStateFlow()

    fun setStoryBackgroundStyle(style: StoryBackgroundStyle) {
        _storyBackgroundStyle.value = style
        crankrPrefs.edit().putString("story_bg_style", style.name).apply()
    }

    private var fadeInJob: Job? = null
    private var isFadingIn = false
    private var sleepTimerJob: Job? = null
    private var lyricsJob: Job? = null
    private var coverLoadingJob: Job? = null

    private var isYouTubePlayback = false
    private var isPlaybackPrepared = false
    private var preparedTrackId: String? = null
    private var pendingSeekPositionMs: Long = 0L

    private var candidateStreamUrls: List<String> = emptyList()
    private var currentCandidateIndex: Int = 0
    private var streamWatchdogJob: Job? = null

    fun setCrossfadeDuration(seconds: Int) {
        _crossfadeDurationSeconds.value = seconds
        crankrPrefs.edit().putInt("crossfade_sec", seconds).apply()
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        crankrPrefs.edit().putFloat("playback_speed", speed).apply()
        exoPlayer.setPlaybackSpeed(speed)
        youTubeAudioEngine.setPlaybackSpeed(speed)
        Toast.makeText(getApplication(), "Kecepatan Putar: ${speed}x", Toast.LENGTH_SHORT).show()
    }

    fun cyclePlaybackSpeed() {
        val next = when (_playbackSpeed.value) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 0.75f
            else -> 1.0f
        }
        setPlaybackSpeed(next)
    }

    private fun persistPlaybackQueue(queue: List<Track>, title: String? = null) {
        if (queue.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(getApplication<Application>().filesDir, "saved_playback_queue.json")
                val tempFile = File(getApplication<Application>().filesDir, "saved_playback_queue.json.tmp")
                val jsonArray = JSONArray()
                for (track in queue) {
                    val obj = JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("album", track.album)
                        put("durationSeconds", track.durationSeconds)
                        put("streamUrl", track.streamUrl)
                        put("coverUrl", track.coverUrl)
                        put("genre", track.genre)
                        put("localFilePath", track.localFilePath ?: "")
                        put("youtubeVideoId", track.youtubeVideoId ?: "")
                        put("addedAt", track.addedAt)
                    }
                    jsonArray.put(obj)
                }
                tempFile.writeText(jsonArray.toString(), Charsets.UTF_8)
                tempFile.renameTo(file)
                if (title != null) {
                    crankrPrefs.edit().putString("last_queue_title", title).apply()
                }
            } catch (e: Exception) {
                Log.e("CRANKR_PLAYER", "Gagal menyimpan antrean: ${e.message}")
            }
        }
    }

    private fun loadSavedPlaybackQueue(): List<Track> {
        return try {
            val file = File(getApplication<Application>().filesDir, "saved_playback_queue.json")
            if (!file.exists()) return emptyList()
            val jsonStr = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<Track>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val trackId = obj.optString("id", "").trim()
                if (trackId.isBlank()) continue
                val localPath = downloadManager.getLocalFilePath(trackId)
                    ?: obj.optString("localFilePath").takeIf { it.isNotEmpty() && it != "null" }
                val track = Track(
                    id = trackId,
                    title = obj.optString("title", "Unknown Track"),
                    artist = obj.optString("artist", "Unknown Artist"),
                    album = obj.optString("album", ""),
                    durationSeconds = obj.optInt("durationSeconds", 180),
                    streamUrl = obj.optString("streamUrl", ""),
                    coverUrl = obj.optString("coverUrl", ""),
                    genre = obj.optString("genre", "All"),
                    localFilePath = localPath,
                    youtubeVideoId = obj.optString("youtubeVideoId").takeIf { it.isNotEmpty() && it != "null" },
                    addedAt = obj.optLong("addedAt", 0L)
                )
                list.add(track)
            }
            list
        } catch (e: Exception) {
            Log.e("CRANKR_PLAYER", "Gagal memuat antrean tersimpan: ${e.message}")
            emptyList()
        }
    }

    private fun saveLastPlayed(track: Track, positionMs: Long) {
        val queue = _playbackQueue.value
        val queueIdx = if (queue.isNotEmpty()) {
            val found = queue.indexOfFirst { it.id == track.id }
            if (found >= 0) found else currentQueueIndex
        } else 0

        crankrPrefs.edit()
            .putString("last_track_id", track.id)
            .putString("last_track_title", track.title)
            .putString("last_track_artist", track.artist)
            .putString("last_track_album", track.album)
            .putString("last_track_cover", track.coverUrl)
            .putString("last_track_stream_url", track.streamUrl)
            .putInt("last_track_duration", track.durationSeconds)
            .putString("last_track_video_id", track.youtubeVideoId)
            .putString("last_track_local_path", track.localFilePath)
            .putLong("last_position_ms", positionMs)
            .putInt("last_queue_index", queueIdx)
            .apply()
        if (queue.isNotEmpty()) {
            persistPlaybackQueue(queue, _playbackQueueTitle.value)
        }
    }

    private fun loadLastPlayedTrack() {
        val id = crankrPrefs.getString("last_track_id", null) ?: return
        val title = crankrPrefs.getString("last_track_title", "") ?: return
        val artist = crankrPrefs.getString("last_track_artist", "") ?: ""
        val album = crankrPrefs.getString("last_track_album", "") ?: ""
        val cover = crankrPrefs.getString("last_track_cover", "") ?: ""
        val streamUrl = crankrPrefs.getString("last_track_stream_url", "") ?: ""
        val durationSec = crankrPrefs.getInt("last_track_duration", 180)
        val videoId = crankrPrefs.getString("last_track_video_id", null)
        val localPath = crankrPrefs.getString("last_track_local_path", null)
        val lastPos = crankrPrefs.getLong("last_position_ms", 0L)
        val savedIndex = crankrPrefs.getInt("last_queue_index", 0)

        val restoredTrack = Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSec,
            coverUrl = cover,
            streamUrl = streamUrl,
            genre = "All",
            localFilePath = localPath,
            youtubeVideoId = videoId
        )

        _currentTrack.value = restoredTrack
        _currentPosition.value = lastPos
        pendingSeekPositionMs = lastPos
        _duration.value = (durationSec * 1000L).coerceAtLeast(1L)
        _isPlaying.value = false

        // Pemulihan Antrean Playlist (Anti Auto-Queue Artis Saat Cold Start)
        val savedQueue = loadSavedPlaybackQueue()
        if (savedQueue.isNotEmpty()) {
            _playbackQueue.value = savedQueue
            val savedTitle = crankrPrefs.getString("last_queue_title", null)
            if (savedTitle != null) {
                _playbackQueueTitle.value = savedTitle
            }
            val idx = savedQueue.indexOfFirst { it.id == restoredTrack.id }
            currentQueueIndex = if (idx >= 0) idx else savedIndex.coerceIn(0, (savedQueue.size - 1).coerceAtLeast(0))
        } else {
            // Jika tidak ada antrean tersimpan, putar lagu tersebut tanpa menambahkan antrean artis
            _playbackQueue.value = listOf(restoredTrack)
            currentQueueIndex = 0
        }

        isPlaybackPrepared = false
        preparedTrackId = null
    }

    fun setAudioVolume(fraction: Float) {
        val v = fraction.coerceIn(0f, 1f)
        if (isYouTubePlayback) {
            youTubeAudioEngine.setVolume(v)
        } else {
            exoPlayer.volume = v
        }
    }

    fun refreshListeningStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = listeningStatsManager.getStats()
            _listeningStats.value = stats
        }
    }

    fun resetListeningStats() {
        viewModelScope.launch(Dispatchers.IO) {
            listeningStatsManager.resetStats()
            val stats = listeningStatsManager.getStats()
            _listeningStats.value = stats
        }
    }

    private fun startFadeIn() {
        fadeInJob?.cancel()
        isFadingIn = false
        // Kunci volume dasar ExoPlayer ke angka maksimal 1.0f (matikan normalisasi volume yang mengurangi suara)
        if (!isYouTubePlayback) {
            exoPlayer.volume = 1.0f
        }
    }

    private fun checkCrossfade(positionMs: Long, durationMs: Long) {
        if (isYouTubePlayback) return
        val crossfadeSec = _crossfadeDurationSeconds.value
        if (crossfadeSec <= 0 || durationMs <= 5000L) {
            if (exoPlayer.volume < 1.0f) {
                exoPlayer.volume = 1.0f
            }
            return
        }

        val crossfadeMs = crossfadeSec * 1000L
        val remainingMs = durationMs - positionMs

        // 1. Transisi fade-in di awal lagu (0 s/d 1.5 detik)
        if (positionMs in 0..1500L) {
            val fadeInRatio = (positionMs.toFloat() / 1500f).coerceIn(0.2f, 1.0f)
            exoPlayer.volume = fadeInRatio
            return
        }

        // 2. Transisi fade-out halus saat mendekati akhir lagu
        if (remainingMs in 0..crossfadeMs) {
            val fadeOutRatio = (remainingMs.toFloat() / crossfadeMs.toFloat()).coerceIn(0.08f, 1.0f)
            exoPlayer.volume = fadeOutRatio
            return
        }

        // 3. Normal playback volume penuh
        if (exoPlayer.volume < 1.0f) {
            exoPlayer.volume = 1.0f
        }
    }

    // Smart Next-Track Pre-caching & Preloading Cache
    private var precachedTrackId: String? = null
    private var precacheJob: Job? = null
    private val preloadedLyricsCache = mutableMapOf<String, LyricsData?>()
    private var stagePrefetchJob: Job? = null

    /**
     * Mengambil YouTube Video ID eksak (11 karakter) dari metadata track tanpa melakukan pencarian teks.
     * Mengutamakan youtubeVideoId yang tersimpan, prefix 'yt_', ID 11 karakter, atau parameter URL streaming.
     */
    fun getExactVideoId(track: Track): String? {
        val direct = track.youtubeVideoId?.trim()
        if (!direct.isNullOrEmpty() && direct.length == 11 && !AudioStreamResolver.isRestricted(direct)) {
            return direct
        }
        if (track.id.startsWith("yt_")) {
            val vid = track.id.removePrefix("yt_").trim()
            if (vid.length == 11 && !AudioStreamResolver.isRestricted(vid)) {
                return vid
            }
        }
        if (track.id.length == 11 && !track.id.startsWith("sp_") && !track.id.startsWith("local_") && track.id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            if (!AudioStreamResolver.isRestricted(track.id)) {
                return track.id
            }
        }
        val stream = track.streamUrl
        if (stream.isNotBlank()) {
            val ytMatch = Regex("""(?:v=|youtu\.be/)([a-zA-Z0-9_-]{11})""").find(stream)
            if (ytMatch != null) {
                val vid = ytMatch.groupValues[1]
                if (!AudioStreamResolver.isRestricted(vid)) {
                    return vid
                }
            }
        }
        return null
    }

    /**
     * Mekanisme prefetch stream bertahap:
     * Mengambil 1 atau 2 lagu di depan secara asinkron di latar belakang saat sebuah lagu sedang diputar.
     * Mengunci videoId eksak (tanpa pencarian teks jika videoId sudah ada), menyelesaikan stream audio,
     * dan memasukkan MediaItem berikutnya ke antrean ExoPlayer dengan customCacheKey agar perpindahan
     * trek berlangsung instan dengan 0ms delay dan ter-cache otomatis ke SimpleCache disk (1 GB).
     */
    fun stagePrefetchUpcomingTracks() {
        val current = _currentTrack.value ?: return
        val queue = _playbackQueue.value.ifEmpty { getActiveTrackList() }
        if (queue.isEmpty()) return

        val currentIndex = queue.indexOfFirst { it.id == current.id }.let {
            if (it >= 0) it else currentQueueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        }

        // Tentukan maksimal 1 hingga 2 lagu berikutnya sesuai mode antrean dan repeat
        val upcomingTracks = mutableListOf<Track>()
        if (currentIndex + 1 < queue.size) {
            upcomingTracks.add(queue[currentIndex + 1])
        } else if (_repeatMode.value == RepeatMode.ALL && queue.isNotEmpty()) {
            upcomingTracks.add(queue.first())
        }

        if (currentIndex + 2 < queue.size) {
            upcomingTracks.add(queue[currentIndex + 2])
        } else if (_repeatMode.value == RepeatMode.ALL && queue.size > 1) {
            val wrapIndex = (currentIndex + 2) % queue.size
            if (wrapIndex != currentIndex && upcomingTracks.none { it.id == queue[wrapIndex].id }) {
                upcomingTracks.add(queue[wrapIndex])
            }
        }

        if (upcomingTracks.isEmpty()) return

        stagePrefetchJob?.cancel()
        stagePrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                for (nextTrack in upcomingTracks) {
                    if (!isActive) break

                    // 1. Preload cover image into Coil cache
                    if (nextTrack.coverUrl.isNotBlank()) {
                        try {
                            val loader = ImageLoader(getApplication())
                            val req = ImageRequest.Builder(getApplication())
                                .data(nextTrack.coverUrl)
                                .build()
                            loader.enqueue(req)
                        } catch (_: Exception) {}
                    }

                    // 2. Pre-fetch LRCLIB lyrics into RAM cache
                    try {
                        val lyricsData = lyricsRepository.fetchLyricsData(nextTrack)
                        if (lyricsData != null) {
                            preloadedLyricsCache[nextTrack.id] = lyricsData
                        }
                    } catch (_: Exception) {}

                    // Cek apakah item sudah terpasang di antrean ExoPlayer
                    val alreadyInPlayer = withContext(Dispatchers.Main) {
                        (0 until exoPlayer.mediaItemCount).any { exoPlayer.getMediaItemAt(it).mediaId == nextTrack.id }
                    }
                    if (alreadyInPlayer) continue

                    // 3. Kasus file lokal
                    if (nextTrack.localFilePath != null && File(nextTrack.localFilePath).exists()) {
                        val mediaUri = Uri.fromFile(File(nextTrack.localFilePath))
                        val artworkUri = if (nextTrack.coverUrl.isNotBlank()) Uri.parse(nextTrack.coverUrl) else null
                        val mediaItem = MediaItem.Builder()
                            .setMediaId(nextTrack.id)
                            .setUri(mediaUri)
                            .setCustomCacheKey(nextTrack.id)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(nextTrack.title)
                                    .setArtist(nextTrack.artist)
                                    .apply { if (artworkUri != null) setArtworkUri(artworkUri) }
                                    .build()
                            )
                            .build()

                        withContext(Dispatchers.Main) {
                            if (!isYouTubePlayback && (exoPlayer.mediaItemCount - exoPlayer.currentMediaItemIndex) <= 2) {
                                exoPlayer.addMediaItem(mediaItem)
                            }
                        }
                        continue
                    }

                    // 3b. Prioritaskan Pembacaan SimpleCache Disk untuk trek berikutnya
                    val exactVid = getExactVideoId(nextTrack)
                    val targetVid = if (!exactVid.isNullOrEmpty()) {
                        exactVid
                    } else {
                        AudioStreamResolver.resolveYouTubeVideoId(nextTrack)
                    }

                    val diskKey = exactVid ?: targetVid ?: nextTrack.id
                    val cachedDiskFile = MediaCacheManager.getCachedDiskFile(getApplication(), diskKey)
                    if (cachedDiskFile != null && cachedDiskFile.exists()) {
                        val artworkUri = if (nextTrack.coverUrl.isNotBlank()) Uri.parse(nextTrack.coverUrl) else null
                        val mediaItem = MediaItem.Builder()
                            .setMediaId(nextTrack.id)
                            .setUri(Uri.fromFile(cachedDiskFile))
                            .setCustomCacheKey(diskKey)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(nextTrack.title)
                                    .setArtist(nextTrack.artist)
                                    .apply { if (artworkUri != null) setArtworkUri(artworkUri) }
                                    .build()
                            )
                            .build()

                        withContext(Dispatchers.Main) {
                            if (!isYouTubePlayback && (exoPlayer.mediaItemCount - exoPlayer.currentMediaItemIndex) <= 2) {
                                exoPlayer.addMediaItem(mediaItem)
                            }
                        }
                        continue
                    }

                    // 4. Kasus online: Kunci videoId eksak (tanpa pencarian teks jika videoId valid)

                    if (!targetVid.isNullOrEmpty() && !AudioStreamResolver.isRestricted(targetVid)) {
                        val updatedTrack = if (nextTrack.youtubeVideoId != targetVid) nextTrack.copy(youtubeVideoId = targetVid) else nextTrack
                        _playbackQueue.update { list -> list.map { if (it.id == nextTrack.id) updatedTrack else it } }

                        val cachedUrls = AudioStreamResolver.getCachedStreamUrls(targetVid)
                        val candidates = if (cachedUrls.isNotEmpty()) cachedUrls else AudioStreamResolver.resolveCandidateStreamUrls(targetVid)

                        if (candidates.isNotEmpty() && isActive) {
                            val streamUrl = candidates.first()
                            val artworkUri = if (updatedTrack.coverUrl.isNotBlank()) Uri.parse(updatedTrack.coverUrl) else null
                            val customKey = targetVid

                            val mediaItem = MediaItem.Builder()
                                .setMediaId(updatedTrack.id)
                                .setUri(Uri.parse(streamUrl))
                                .setCustomCacheKey(customKey)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(updatedTrack.title)
                                        .setArtist(updatedTrack.artist)
                                        .apply { if (artworkUri != null) setArtworkUri(artworkUri) }
                                        .build()
                                )
                                .build()

                            withContext(Dispatchers.Main) {
                                if (!isYouTubePlayback && (exoPlayer.mediaItemCount - exoPlayer.currentMediaItemIndex) <= 2) {
                                    exoPlayer.addMediaItem(mediaItem)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Non-blocking staged prefetch
            }
        }
    }

    fun checkAndPrecacheNextTrack() {
        stagePrefetchUpcomingTracks()
    }

    private var preInitializedTrackId: String? = null
    private var fullCachedUpcomingTrackId: String? = null
    private var nextTrackCacheJob: Job? = null

    /**
     * Inisialisasi Decoder Lebih Awal:
     * Siapkan objek MediaItem ke ExoPlayer lebih awal dengan nilai playWhenReady = false lalu panggil method prepare.
     * Sistem menyiapkan decoder audio sebelum pengguna mengetuk tombol putar sehingga audio berbunyi seketika.
     */
    fun preInitializeDecoderForTrack(track: Track, streamUrl: String, customKey: String) {
        if (_isPlaying.value || _currentTrack.value != null) return
        try {
            val artworkUri = if (track.coverUrl.isNotBlank()) Uri.parse(track.coverUrl) else null
            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.parse(streamUrl))
                .setCustomCacheKey(customKey)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .apply { if (artworkUri != null) setArtworkUri(artworkUri) }
                        .build()
                )
                .build()

            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.playWhenReady = false
            exoPlayer.prepare()
            isPlaybackPrepared = true
            preparedTrackId = track.id
            preInitializedTrackId = track.id
            Log.d("CRANKR_DECODER", "Pre-initialized ExoPlayer decoder for track ${track.title} with playWhenReady = false")
        } catch (e: Exception) {
            Log.w("CRANKR_DECODER", "Gagal inisialisasi decoder awal: ${e.message}")
        }
    }

    /**
     * Memantau persentase buffer lagu aktif dan memicu pengunduhan berkas audio penuh
     * untuk lagu berikutnya di antrean menggunakan CacheWriter saat buffer mencapai 80%.
     * Seluruh data lagu berikutnya tersimpan di SimpleCache agar lagu berputar lancar
     * meski ponsel masuk ke area tanpa sinyal (offline resilience).
     */
    fun triggerNextTrackFullCacheWriter() {
        val current = _currentTrack.value ?: return
        val queue = _playbackQueue.value.ifEmpty { getActiveTrackList() }
        if (queue.isEmpty()) return

        val currentIndex = queue.indexOfFirst { it.id == current.id }.let {
            if (it >= 0) it else currentQueueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        }

        val nextTrack = if (currentIndex + 1 < queue.size) {
            queue[currentIndex + 1]
        } else if (_repeatMode.value == RepeatMode.ALL && queue.isNotEmpty()) {
            queue.first()
        } else null ?: return

        // Hindari pengunduhan ganda jika trek berikutnya sudah diproses
        if (nextTrack.id == fullCachedUpcomingTrackId) return
        if (nextTrack.localFilePath != null && File(nextTrack.localFilePath).exists()) return

        val exactVid = getExactVideoId(nextTrack) ?: nextTrack.youtubeVideoId
        val customKey = exactVid ?: nextTrack.id
        if (MediaCacheManager.isTrackCached(getApplication(), customKey)) {
            fullCachedUpcomingTrackId = nextTrack.id
            return
        }

        fullCachedUpcomingTrackId = nextTrack.id
        nextTrackCacheJob?.cancel()
        nextTrackCacheJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetVid = exactVid ?: AudioStreamResolver.resolveYouTubeVideoId(nextTrack)
                if (!targetVid.isNullOrEmpty() && !AudioStreamResolver.isRestricted(targetVid)) {
                    val cachedUrls = AudioStreamResolver.getCachedStreamUrls(targetVid)
                    val streamUrls = if (cachedUrls.isNotEmpty()) cachedUrls else AudioStreamResolver.resolveCandidateStreamUrls(targetVid)
                    val bestUrl = streamUrls.firstOrNull()
                    if (!bestUrl.isNullOrBlank()) {
                        Log.i("CRANKR_CACHE", "Buffer lagu aktif >= 80%. Memulai CacheWriter prefetch utuh untuk trek: ${nextTrack.title}")
                        MediaCacheManager.prefetchFullTrack(getApplication(), bestUrl, targetVid)
                    }
                }
            } catch (e: Exception) {
                Log.w("CRANKR_CACHE", "Gagal prefetch utuh trek berikutnya: ${e.message}")
            }
        }
    }

    /**
     * Ekstraksi tautan audio di latar belakang untuk lagu-lagu yang sedang tampil di layar atau urutan playlist
     * secara paralel agar langsung tersimpan di cache memori sementara (0ms latency saat pengguna mengetuk lagu),
     * serta mengunduh potongan awal 128 KB pertama ke SimpleCache milik ExoPlayer.
     */
    fun preFetchVisibleTracks(tracks: List<Track>, limit: Int = 6) {
        if (tracks.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            tracks.take(limit).forEach { track ->
                launch {
                    try {
                        val exactVid = getExactVideoId(track)
                        val targetVid = if (!exactVid.isNullOrEmpty()) {
                            exactVid
                        } else {
                            AudioStreamResolver.resolveYouTubeVideoId(track)
                        }
                        if (!targetVid.isNullOrEmpty() && !AudioStreamResolver.isRestricted(targetVid)) {
                            val cached = AudioStreamResolver.getCachedStreamUrls(targetVid)
                            val urls = if (cached.isNotEmpty()) cached else AudioStreamResolver.resolveCandidateStreamUrls(targetVid)
                            val primaryUrl = urls.firstOrNull()
                            if (!primaryUrl.isNullOrBlank()) {
                                val customKey = targetVid
                                // Unduh 128 KB potongan awal ke SimpleCache milik ExoPlayer
                                MediaCacheManager.downloadInitialChunk(getApplication(), primaryUrl, customKey)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Pre-fetch dan cache candidate audio stream URLs untuk 3 lagu teratas secara paralel
     * saat pengguna membuka halaman detail playlist, album, atau profil artis:
     * - Menyimpan URL stream ke dalam RAM LruCache
     * - Mengunduh potongan awal 128 KB ke SimpleCache
     * - Menginisialisasi decoder ExoPlayer lebih awal (playWhenReady = false) untuk lagu pertama jika pemutar idle
     */
    fun preFetchTopTracks(tracks: List<Track>, limit: Int = 3) {
        if (tracks.isEmpty()) return
        val firstTrackId = tracks.firstOrNull()?.id
        viewModelScope.launch(Dispatchers.IO) {
            tracks.take(limit).forEach { track ->
                launch {
                    try {
                        val exactVid = getExactVideoId(track)
                        val targetVid = if (!exactVid.isNullOrEmpty()) {
                            exactVid
                        } else {
                            AudioStreamResolver.resolveYouTubeVideoId(track)
                        }
                        if (!targetVid.isNullOrEmpty() && !AudioStreamResolver.isRestricted(targetVid)) {
                            val cached = AudioStreamResolver.getCachedStreamUrls(targetVid)
                            val urls = if (cached.isNotEmpty()) cached else AudioStreamResolver.resolveCandidateStreamUrls(targetVid)
                            val primaryUrl = urls.firstOrNull()
                            if (!primaryUrl.isNullOrBlank()) {
                                val customKey = targetVid
                                // Unduh 128 KB potongan awal ke SimpleCache milik ExoPlayer
                                MediaCacheManager.downloadInitialChunk(getApplication(), primaryUrl, customKey)

                                // Inisialisasi decoder lebih awal jika ini lagu pertama di antrean dan pemutar sedang idle
                                if (track.id == firstTrackId && !_isPlaying.value && _currentTrack.value == null) {
                                    withContext(Dispatchers.Main) {
                                        preInitializeDecoderForTrack(track, primaryUrl, customKey)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private var lastNotificationSyncMs = 0L

    val youTubeAudioEngine: YouTubeAudioEngine = YouTubeAudioEngine(
        context = application,
        onProgressUpdate = { positionMs, durationMs ->
            if (isYouTubePlayback) {
                _currentPosition.value = positionMs
                var shouldUpdateNotif = false
                if (durationMs > 0 && Math.abs(durationMs - _duration.value) > 1000L) {
                    _duration.value = durationMs
                    shouldUpdateNotif = true
                }
                val now = SystemClock.elapsedRealtime()
                if (shouldUpdateNotif || (now - lastNotificationSyncMs >= 8000L && _isPlaying.value)) {
                    lastNotificationSyncMs = now
                    updateMediaNotification()
                }
                checkCrossfade(positionMs, durationMs)
                checkEndOfTrackSleepTimer(positionMs, durationMs)

                // Smart Pre-caching: As soon as song plays past 4s, preload next track
                if (positionMs > 4000L) {
                    checkAndPrecacheNextTrack()
                }
            }
        },
        onPlaybackStateChanged = { isPlaying ->
            if (isYouTubePlayback) {
                _isPlaying.value = isPlaying
                updateMediaNotification(isBuffering = !isPlaying)
            }
        },
        onTrackEnded = {
            if (isYouTubePlayback) {
                handleYouTubeTrackEnded()
            }
        },
        onPlaybackError = { failedVid, errorCode ->
            handlePlaybackError(failedVid, errorCode)
        }
    )

    private fun updateMediaNotification(isBuffering: Boolean = false) {
        val track = _currentTrack.value ?: return
        val isPlaying = _isPlaying.value
        val pos = _currentPosition.value
        val dur = _duration.value
        val isFav = _favoriteTrackIds.value.contains(track.id)
        MusicPlaybackService.update(
            context = getApplication(),
            track = track,
            isPlaying = isPlaying,
            positionMs = pos,
            durationMs = dur,
            isBuffering = isBuffering,
            isFavorite = isFav
        )
    }

    private var playbackRetryCount = 0
    private val maxPlaybackRetries = 3
    private var quickSearchJob: Job? = null
    private var streamPlaybackJob: Job? = null

    private fun sanitizeQuery(query: String): String {
        return query
            .replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun handlePlaybackError(failedVid: String, errorCode: Int) {
        val current = _currentTrack.value ?: return
        if (!isYouTubePlayback) return

        viewModelScope.launch {
            try {
                if (playbackRetryCount < maxPlaybackRetries) {
                    playbackRetryCount++
                    val delayMs = 1200L * playbackRetryCount
                    delay(delayMs)
                    val altVid = AudioStreamResolver.resolveAlternativeVideoId(current.title, current.artist, failedVid, current.durationSeconds)
                    if (!altVid.isNullOrEmpty() && altVid != failedVid) {
                        val updatedTrack = current.copy(
                            youtubeVideoId = altVid,
                            coverUrl = current.coverUrl
                        )
                        if (_currentTrack.value?.id == current.id) {
                            _currentTrack.value = updatedTrack
                        }
                        _allTracks.update { list -> list.map { if (it.id == current.id) updatedTrack else it } }
                        _selectedPlaylistTracks.update { list -> list.map { if (it.id == current.id) updatedTrack else it } }
                        youTubeAudioEngine.play(altVid)
                        return@launch
                    } else {
                        youTubeAudioEngine.play(failedVid)
                        return@launch
                    }
                } else {
                    playbackRetryCount = 0
                    _isPlaying.value = false
                    updateMediaNotification(isBuffering = false)
                    Toast.makeText(
                        getApplication(),
                        "Lagu tidak dapat diputar. Memutar lagu berikutnya...",
                        Toast.LENGTH_SHORT
                    ).show()
                    playNext()
                }
            } catch (e: Exception) {
                Log.e("CRANKR_PLAYER", "Gagal memulihkan stream error: ${e.message}")
                _isPlaying.value = false
                updateMediaNotification(isBuffering = false)
                playNext()
            }
        }
    }

    private fun handleYouTubeTrackEnded() {
        if (_sleepTimerMode.value == "END_OF_TRACK") {
            _sleepTimerMode.value = null
            _isPlaying.value = false
            setAudioVolume(1f)
            youTubeAudioEngine.pause()
            updateMediaNotification(isBuffering = false)
            Toast.makeText(getApplication(), "Tape wind-down selesai: lagu berakhir", Toast.LENGTH_SHORT).show()
            return
        }
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _isPlaying.value = true
                seekTo(0)
                youTubeAudioEngine.resume()
                updateMediaNotification(isBuffering = false)
            }
            RepeatMode.ALL -> {
                val tracks = if (_playbackQueue.value.isNotEmpty()) _playbackQueue.value else getActiveTrackList()
                val current = _currentTrack.value
                if (tracks.size <= 1) {
                    if (_isAutoplayEnabled.value && current != null) {
                        triggerAutoplay(current)
                    } else {
                        _isPlaying.value = false
                        youTubeAudioEngine.pause()
                        updateMediaNotification(isBuffering = false)
                    }
                } else {
                    playNext()
                }
            }
            RepeatMode.OFF -> {
                val tracks = if (_playbackQueue.value.isNotEmpty()) _playbackQueue.value else getActiveTrackList()
                val current = _currentTrack.value
                val currentIndex = tracks.indexOfFirst { it.id == current?.id }.let {
                    if (it >= 0) it else currentQueueIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
                }
                if (currentIndex >= 0 && currentIndex == tracks.size - 1) {
                    if (_isAutoplayEnabled.value && current != null) {
                        triggerAutoplay(current)
                    } else {
                        _isPlaying.value = false
                        youTubeAudioEngine.pause()
                        updateMediaNotification(isBuffering = false)
                    }
                } else {
                    playNext()
                }
            }
        }
    }

    private var progressJob: Job? = null

    init {
        NetworkQualityMonitor.startMonitoring(application)
        refreshTracks()
        loadPopularTracks()
        setupPlayerListener()
        loadPersistedPlaylists()
        loadRecentlyPlayed()
        loadLikedSongs()
        loadSearchHistory()
        loadLastPlayedTrack()
        setupListenTogetherHooks()
        observeDownloadProgress()
        startProgressTracker()

        // Sinkronisasi dinamis pengaturan Auralys (Normalisasi Volume & Preamp Gain)
        viewModelScope.launch {
            val auralys = com.example.spotifyclone.service.AuralysSettingsManager.getInstance(application)
            auralys.isVolumeNormalizationEnabled.collect { isNorm ->
                pcmEqualizerAudioProcessor.setVolumeNormalization(isNorm)
            }
        }
        viewModelScope.launch {
            val auralys = com.example.spotifyclone.service.AuralysSettingsManager.getInstance(application)
            auralys.preampGainDb.collect { gain ->
                pcmEqualizerAudioProcessor.setPreampGainDb(gain)
                equalizerHandler.setPreampGain(gain)
            }
        }
    }

    fun loadPopularTracks(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                val popularTracks = homeRepository.getPopularTracks(forceRefresh = forceRefresh)
                if (popularTracks.isNotEmpty()) {
                    val enriched = popularTracks.map { track ->
                        val localPath = downloadManager.getLocalFilePath(track.id)
                        track.copy(localFilePath = localPath)
                    }
                    _allTracks.update { current ->
                        val currentTrackId = _currentTrack.value?.id
                        val currentPlaying = current.find { it.id == currentTrackId }
                        val downloaded = current.filter { it.isDownloaded }
                        val merged = (downloaded + enriched).distinctBy { it.id }.toMutableList()
                        if (currentPlaying != null && merged.none { it.id == currentPlaying.id }) {
                            merged.add(0, currentPlaying)
                        }
                        merged
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun refreshPopularTracks() {
        loadPopularTracks(forceRefresh = true)
    }

    private fun loadSearchHistory() {
        viewModelScope.launch {
            _searchHistory.value = searchHistoryManager.getSearchHistory()
        }
    }

    private fun loadLikedSongs() {
        viewModelScope.launch {
            val loaded = likedSongsManager.getLikedTracks()
            _likedTracks.value = loaded
            _favoriteTrackIds.value = loaded.map { it.id }.toSet()
        }
    }

    private fun loadRecentlyPlayed() {
        viewModelScope.launch {
            _recentlyPlayedTracks.value = recentlyPlayedManager.getRecentlyPlayed()
        }
    }

    private fun loadPersistedPlaylists() {
        viewModelScope.launch {
            try {
                listeningHistoryDbHelper.clearStreamUrlCache()
            } catch (_: Exception) {}
            val loaded = playlistStorageManager.loadPlaylists()
            _spotifyPlaylists.value = loaded.map { it.first }
            loaded.forEach { (pl, tracks) ->
                val enriched = tracks.map { t ->
                    val local = downloadManager.getLocalFilePath(t.id)
                    t.copy(localFilePath = local)
                }
                playlistTracksMap[pl.id] = enriched.toMutableList()
            }
            autoRepairStaleCovers()
        }
    }

    private fun persistPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = _spotifyPlaylists.value.map { pl ->
                val tracks = playlistTracksMap[pl.id] ?: emptyList()
                Pair(pl.copy(trackCount = tracks.size), tracks)
            }
            playlistStorageManager.savePlaylists(items)
        }
    }

    private fun refreshTracks() {
        val baseList = _allTracks.value.ifEmpty { MusicCatalog.initialTracks }
        val offlineTracks = downloadManager.getAllDownloadedTracks()

        val updatedBase = baseList.map { track ->
            val localPath = downloadManager.getLocalFilePath(track.id)
            track.copy(localFilePath = localPath)
        }

        val offlineMap = offlineTracks.associateBy { it.id }
        val merged = updatedBase.map { offlineMap[it.id] ?: it }.toMutableList()
        offlineTracks.forEach { offTrack ->
            if (merged.none { it.id == offTrack.id }) {
                merged.add(offTrack)
            }
        }

        _allTracks.value = merged
        _storageUsage.value = downloadManager.getOfflineStorageSizeFormatted()
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (!isYouTubePlayback) {
                    exoPlayer.volume = 1.0f
                }
                if (audioSessionId > 0 && audioSessionId != android.media.AudioTrack.ERROR_BAD_VALUE) {
                    initAudioEffects(audioSessionId)
                    loudnessEnhancer?.let {
                        try {
                            val savedPreamp = com.example.spotifyclone.service.AuralysSettingsManager.getInstance(getApplication()).preampGainDb.value
                            val targetGainMb = if (savedPreamp > 0f) (savedPreamp * 100).toInt() else 800
                            it.setTargetGain(targetGainMb)
                            it.enabled = true
                        } catch (_: Exception) {}
                    }
                    equalizerHandler.ensureSessionAttached()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isYouTubePlayback) {
                    _isPlaying.value = isPlaying
                    updateMediaNotification()
                    if (isPlaying) {
                        streamWatchdogJob?.cancel()
                        streamWatchdogJob = null
                        exoPlayer.volume = 1.0f
                        val sid = exoPlayer.audioSessionId
                        if (sid > 0 && sid != android.media.AudioTrack.ERROR_BAD_VALUE) {
                            initAudioEffects(sid)
                        }
                        loudnessEnhancer?.let {
                            try {
                                val savedPreamp = com.example.spotifyclone.service.AuralysSettingsManager.getInstance(getApplication()).preampGainDb.value
                                val targetGainMb = if (savedPreamp > 0f) (savedPreamp * 100).toInt() else 800
                                it.setTargetGain(targetGainMb)
                                it.enabled = true
                            } catch (_: Exception) {}
                        }
                        equalizerHandler.ensureSessionAttached()
                        startProgressTracker()
                    } else {
                        stopProgressTracker()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                streamWatchdogJob?.cancel()
                streamWatchdogJob = null
                val current = _currentTrack.value ?: return

                // Jika masih ada kandidat stream cadangan, langsung beralih ke stream berikutnya
                if (candidateStreamUrls.isNotEmpty() && currentCandidateIndex + 1 < candidateStreamUrls.size) {
                    Log.w("CRANKR_STREAM", "ExoPlayer error kandidat $currentCandidateIndex: ${error.message}. Beralih ke kandidat ${currentCandidateIndex + 1}")
                    playDirectStreamWithFallback(current, candidateStreamUrls, currentCandidateIndex + 1)
                    return
                }

                handleStreamFailure(current)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                coverLoadingJob?.cancel()
                // Kunci volume dasar ExoPlayer ke angka maksimal 1.0f setiap kali lagu berganti
                if (!isYouTubePlayback) {
                    exoPlayer.volume = 1.0f
                }
                val sid = exoPlayer.audioSessionId
                if (sid > 0 && sid != android.media.AudioTrack.ERROR_BAD_VALUE) {
                    initAudioEffects(sid)
                }
                loudnessEnhancer?.let {
                    try {
                        val savedPreamp = com.example.spotifyclone.service.AuralysSettingsManager.getInstance(getApplication()).preampGainDb.value
                        val targetGainMb = if (savedPreamp > 0f) (savedPreamp * 100).toInt() else 800
                        it.setTargetGain(targetGainMb)
                        it.enabled = true
                    } catch (_: Exception) {}
                }
                equalizerHandler.ensureSessionAttached()
                val savedPreamp = com.example.spotifyclone.service.AuralysSettingsManager.getInstance(getApplication()).preampGainDb.value
                equalizerHandler.setPreampGain(savedPreamp)
                pcmEqualizerAudioProcessor.setPreampGainDb(savedPreamp)
                applyCurrentEqualizerSettings()

                // Biarkan ExoPlayer memutar ulang secara otomatis jika mode adalah REPEAT_MODE_ONE tanpa intervensi antrean
                if (exoPlayer.repeatMode == Player.REPEAT_MODE_ONE || _repeatMode.value == RepeatMode.ONE) {
                    return
                }
                val mediaId = mediaItem?.mediaId ?: return
                val queue = _playbackQueue.value
                val index = queue.indexOfFirst { it.id == mediaId }
                if (index >= 0) {
                    val oldTrack = _currentTrack.value
                    if (!isNavigatingHistory && oldTrack != null && oldTrack.id != mediaId) {
                        playbackHistoryStack.addLast(oldTrack)
                        if (playbackHistoryStack.size > 100) {
                            playbackHistoryStack.removeFirst()
                        }
                    }
                    currentQueueIndex = index
                    val newTrack = queue[index]
                    _currentTrack.value = newTrack
                    _duration.value = (newTrack.durationSeconds * 1000L).coerceAtLeast(1L)
                    _currentPosition.value = 0L
                    preparedTrackId = newTrack.id
                    isPlaybackPrepared = true
                    updateMediaNotification(isBuffering = false)
                    saveLastPlayed(newTrack, 0L)
                    viewModelScope.launch {
                        _recentlyPlayedTracks.value = recentlyPlayedManager.addTrack(newTrack)
                    }

                    // Reset kandidat stream untuk lagu baru agar watchdog tidak menggunakan kandidat lagu sebelumnya
                    currentCandidateIndex = 0
                    val targetVid = getExactVideoId(newTrack) ?: newTrack.youtubeVideoId
                    if (!targetVid.isNullOrBlank()) {
                        val cached = AudioStreamResolver.getCachedStreamUrls(targetVid)
                        if (cached.isNotEmpty()) {
                            candidateStreamUrls = cached
                        } else {
                            viewModelScope.launch(Dispatchers.IO) {
                                val fresh = AudioStreamResolver.resolveCandidateStreamUrls(targetVid)
                                if (fresh.isNotEmpty() && _currentTrack.value?.id == newTrack.id) {
                                    candidateStreamUrls = fresh
                                }
                            }
                        }
                    } else if (newTrack.streamUrl.isNotBlank()) {
                        candidateStreamUrls = listOf(newTrack.streamUrl)
                    } else {
                        candidateStreamUrls = emptyList()
                    }

                    // Hapus item terdahulu dari antrean ExoPlayer untuk membatasi antrean hanya 1-2 lagu ke depan
                    val curExoIdx = exoPlayer.currentMediaItemIndex
                    if (curExoIdx > 0) {
                        try {
                            exoPlayer.removeMediaItems(0, curExoIdx)
                        } catch (_: Exception) {}
                    }

                    stagePrefetchUpcomingTracks()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isYouTubePlayback) {
                    // Kunci volume dasar ExoPlayer ke angka maksimal 1.0f setiap kali state berubah
                    exoPlayer.volume = 1.0f

                    if (playbackState == Player.STATE_BUFFERING) {
                        updateMediaNotification(isBuffering = true)
                        // Watchdog timer cerdas saat status ExoPlayer berada di STATE_BUFFERING
                        // Bedakan buffering awal lagu (4.5 detik) vs buffering jaringan di tengah lagu (8 detik)
                        if (streamWatchdogJob == null || streamWatchdogJob?.isActive == false) {
                            val current = _currentTrack.value
                            if (current != null) {
                                streamWatchdogJob = viewModelScope.launch {
                                    val isMidSong = (exoPlayer.currentPosition > 2000L) || (_currentPosition.value > 2000L)
                                    val timeoutMs = if (isMidSong) 8000L else 4500L
                                    delay(timeoutMs)
                                    if (isActive && !exoPlayer.isPlaying && _currentTrack.value?.id == current.id) {
                                        val lastPos = if (exoPlayer.currentPosition > 1000L) exoPlayer.currentPosition else _currentPosition.value
                                        if (lastPos > 1000L && pendingSeekPositionMs == 0L) {
                                            pendingSeekPositionMs = lastPos
                                        }
                                        Log.w("CRANKR_STREAM", "Watchdog: STATE_BUFFERING melebihi ${timeoutMs}ms pada posisi ${lastPos}ms. Mengalihkan kandidat.")
                                        if (candidateStreamUrls.isNotEmpty() && currentCandidateIndex + 1 < candidateStreamUrls.size) {
                                            playDirectStreamWithFallback(current, candidateStreamUrls, currentCandidateIndex + 1)
                                        } else {
                                            handleStreamFailure(current)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (playbackState == Player.STATE_READY) {
                        streamWatchdogJob?.cancel()
                        streamWatchdogJob = null
                        exoPlayer.volume = 1.0f
                        _duration.value = exoPlayer.duration.coerceAtLeast(1L)
                        val sid = exoPlayer.audioSessionId
                        if (sid > 0 && sid != android.media.AudioTrack.ERROR_BAD_VALUE) {
                            initAudioEffects(sid)
                        }
                        loudnessEnhancer?.let {
                            try {
                                val savedPreamp = com.example.spotifyclone.service.AuralysSettingsManager.getInstance(getApplication()).preampGainDb.value
                                val targetGainMb = if (savedPreamp > 0f) (savedPreamp * 100).toInt() else 800
                                it.setTargetGain(targetGainMb)
                                it.enabled = true
                            } catch (_: Exception) {}
                        }
                        equalizerHandler.ensureSessionAttached()
                        val savedPreamp = com.example.spotifyclone.service.AuralysSettingsManager.getInstance(getApplication()).preampGainDb.value
                        equalizerHandler.setPreampGain(savedPreamp)
                        pcmEqualizerAudioProcessor.setPreampGainDb(savedPreamp)
                        applyCurrentEqualizerSettings()
                        if (exoPlayer.playWhenReady) {
                            updateMediaNotification(isBuffering = false)
                        }
                    } else if (playbackState == Player.STATE_ENDED) {
                        if (_sleepTimerMode.value == "END_OF_TRACK") {
                            _sleepTimerMode.value = null
                            _isPlaying.value = false
                            setAudioVolume(1f)
                            exoPlayer.pause()
                            updateMediaNotification(isBuffering = false)
                            Toast.makeText(getApplication(), "Tape wind-down selesai: lagu berakhir", Toast.LENGTH_SHORT).show()
                        } else {
                            // Saat lagu selesai, jika mode saat ini adalah REPEAT_MODE_ONE (Loop Terus 1 Lagu):
                            // Pastikan lagu yang sama langsung terulang dari awal tanpa intervensi antrean.
                            if (exoPlayer.repeatMode == Player.REPEAT_MODE_ONE || _repeatMode.value == RepeatMode.ONE) {
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                                _isPlaying.value = true
                                updateMediaNotification(isBuffering = false)
                                return
                            }
                            when (_repeatMode.value) {
                                RepeatMode.ALL -> {
                                    val tracks = if (_playbackQueue.value.isNotEmpty()) _playbackQueue.value else getActiveTrackList()
                                    val current = _currentTrack.value
                                    if (tracks.size <= 1) {
                                        // Penanganan antrean tunggal: putar 1 kali lalu selesai/lanjut ke autoplay tanpa mengulang lagu
                                        if (_isAutoplayEnabled.value && current != null) {
                                            triggerAutoplay(current)
                                        } else {
                                            _isPlaying.value = false
                                            exoPlayer.pause()
                                            updateMediaNotification(isBuffering = false)
                                        }
                                    } else {
                                        playNext()
                                    }
                                }
                                RepeatMode.OFF -> {
                                    val tracks = if (_playbackQueue.value.isNotEmpty()) _playbackQueue.value else getActiveTrackList()
                                    val current = _currentTrack.value
                                    val currentIndex = tracks.indexOfFirst { it.id == current?.id }.let {
                                        if (it >= 0) it else currentQueueIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
                                    }
                                    if (currentIndex >= 0 && currentIndex == tracks.size - 1) {
                                        if (_isAutoplayEnabled.value && current != null) {
                                            triggerAutoplay(current)
                                        } else {
                                            _isPlaying.value = false
                                            exoPlayer.pause()
                                            updateMediaNotification(isBuffering = false)
                                        }
                                    } else {
                                        playNext()
                                    }
                                }
                                RepeatMode.ONE -> {
                                    exoPlayer.seekTo(0)
                                    exoPlayer.play()
                                    _isPlaying.value = true
                                    updateMediaNotification(isBuffering = false)
                                }
                            }
                        }
                    }
                }
            }
        })

        // Wire Media Notification Controls from status bar / lockscreen
        MusicNotificationReceiver.onPlay = {
            resume()
        }
        MusicNotificationReceiver.onPause = {
            pause()
        }
        MusicNotificationReceiver.onPlayPause = {
            togglePlayPause()
        }
        MusicNotificationReceiver.onNext = {
            if (exoPlayer.repeatMode == Player.REPEAT_MODE_ONE || _repeatMode.value == RepeatMode.ONE) {
                // Hapus pemanggilan playNext jika mode saat ini adalah REPEAT_MODE_ONE
                exoPlayer.seekTo(0)
                exoPlayer.play()
            } else {
                playNext()
            }
        }
        MusicNotificationReceiver.onPrevious = {
            playPrevious()
        }
        MusicNotificationReceiver.onSeekTo = { posMs ->
            seekTo(posMs)
        }
        MusicNotificationReceiver.onBecomingNoisy = {
            if (_isPlaying.value) {
                togglePlayPause()
            }
        }
        MusicNotificationReceiver.onToggleFavorite = {
            val cur = _currentTrack.value
            if (cur != null) {
                toggleFavorite(cur.id, cur)
            }
        }

        val initialSessionId = exoPlayer.audioSessionId
        if (initialSessionId > 0 && initialSessionId != android.media.AudioTrack.ERROR_BAD_VALUE) {
            initAudioEffects(initialSessionId)
        }
    }

    fun playTrack(track: Track, newQueue: List<Track>? = null, queueTitle: String? = null, initialPositionMs: Long = 0L) {
        if (track.title.isBlank()) return
        val sanitized = MetadataSanitizer.sanitizeTrack(track) ?: track

        // Delegasi ke pemutaran antrean playlist berkecepatan tinggi jika daftar lagu baru disertakan
        if (newQueue != null && newQueue.isNotEmpty()) {
            val foundIdx = newQueue.indexOfFirst { it.id == sanitized.id }
            val targetIdx = if (foundIdx >= 0) foundIdx else newQueue.indexOf(track).coerceAtLeast(0)
            playPlaylist(newQueue, targetIdx, queueTitle, initialPositionMs = initialPositionMs)
            return
        }

        // Kirim perintah putar langsung ke MusicService tanpa menunggu proses render ulang UI / state ViewModel
        MusicPlaybackService.playDirect(getApplication(), sanitized)
        fullCachedUpcomingTrackId = null

        // Jika decoder audio sudah diinisialisasi lebih awal (playWhenReady = false), aktifkan seketika
        if (preInitializedTrackId == sanitized.id && isPlaybackPrepared && !isYouTubePlayback) {
            preInitializedTrackId = null
            _currentPosition.value = 0L
            _currentTrack.value = sanitized
            _isPlaying.value = true
            _duration.value = (sanitized.durationSeconds * 1000L).coerceAtLeast(1L)
            exoPlayer.volume = 1.0f
            exoPlayer.playWhenReady = true
            updateMediaNotification(isBuffering = false)
            stagePrefetchUpcomingTracks()
            return
        }

        // Simpan setiap lagu yang selesai atau sedang diputar ke dalam tumpukan riwayat
        val oldTrack = _currentTrack.value
        if (!isNavigatingHistory && oldTrack != null && oldTrack.id != sanitized.id) {
            playbackHistoryStack.addLast(oldTrack)
            if (playbackHistoryStack.size > 100) {
                playbackHistoryStack.removeFirst()
            }
        }

        // Strict Metadata Binding: Pertahankan cover asli (jangan timpa dengan thumbnail video YouTube)
        val boundCover = when {
            sanitized.coverUrl.isNotBlank() && !sanitized.coverUrl.contains("unsplash.com") && !sanitized.coverUrl.contains("ytimg.com") -> sanitized.coverUrl
            else -> sanitized.coverUrl
        }
        val boundTrack = sanitized.copy(coverUrl = boundCover)

        // 1. Batalkan pekerjaan resolusi audio sebelumnya jika sedang berjalan
        streamPlaybackJob?.cancel()
        streamPlaybackJob = null
        streamWatchdogJob?.cancel()
        streamWatchdogJob = null

        // 2. Hentikan pemutaran lagu lama segera pada semua playback engine
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        youTubeAudioEngine.stop()

        // 3. Reset status progres bar dan perbarui state lagu aktif di UI secara instan
        if (initialPositionMs > 0L) {
            _currentPosition.value = initialPositionMs
            pendingSeekPositionMs = initialPositionMs
            saveLastPlayed(boundTrack, initialPositionMs)
        } else {
            _currentPosition.value = 0L
            pendingSeekPositionMs = 0L
            saveLastPlayed(boundTrack, 0L)
        }
        _currentTrack.value = boundTrack
        _isPlaying.value = true
        _duration.value = (boundTrack.durationSeconds * 1000L).coerceAtLeast(1L)
        isPlaybackPrepared = false
        preparedTrackId = boundTrack.id
        playbackRetryCount = 0
        flushCurrentTrackPlayDuration()
        activeTrackPlayDurationMs = 0L
        hasRecordedHistoryForCurrentTrack = false

        // Record Listening Stats & initiate smooth volume fade-in on IO dispatcher
        viewModelScope.launch(Dispatchers.IO) {
            listeningStatsManager.recordTrackPlay(boundTrack)
            val stats = listeningStatsManager.getStats()
            _listeningStats.value = stats
        }
        startFadeIn()

        // Queue updates: Pertahankan daftar lagu utuh apa adanya & atur indeks pemutaran
        if (newQueue != null && newQueue.isNotEmpty()) {
            _playbackQueue.value = newQueue
            val foundIdx = newQueue.indexOfFirst { it.id == boundTrack.id }
            currentQueueIndex = if (foundIdx >= 0) foundIdx else newQueue.indexOf(track).coerceAtLeast(0)
            if (queueTitle != null) {
                _playbackQueueTitle.value = queueTitle
            }
            persistPlaybackQueue(newQueue, queueTitle)
        } else {
            val existingQueue = _playbackQueue.value
            val foundIdx = existingQueue.indexOfFirst { it.id == boundTrack.id }
            if (foundIdx >= 0) {
                currentQueueIndex = foundIdx
            } else {
                _playbackQueue.value = listOf(boundTrack)
                currentQueueIndex = 0
                if (queueTitle != null) {
                    _playbackQueueTitle.value = queueTitle
                }
                persistPlaybackQueue(_playbackQueue.value, queueTitle)
            }
        }

        // Ensure boundTrack is part of _allTracks
        if (_allTracks.value.none { it.id == boundTrack.id }) {
            _allTracks.update { listOf(boundTrack) + it }
        }

        // Listen Together Host Broadcast: Pancarkan perubahan trek ke semua Guest
        if (!isSyncingFromRemote && isListenTogetherHost.value) {
            listenTogetherManager.emitTrackChange(boundTrack, 0L)
        }

        // Update Foreground Media Notification: Hindari spinner loading jika stream audio sudah ada di memori/disk
        val initialExactVid = getExactVideoId(boundTrack) ?: boundTrack.youtubeVideoId
        val isInitialCached = (boundTrack.localFilePath != null && File(boundTrack.localFilePath).exists()) ||
            (!initialExactVid.isNullOrEmpty() && MediaCacheManager.getCachedDiskFile(getApplication(), initialExactVid) != null) ||
            (!initialExactVid.isNullOrEmpty() && AudioStreamResolver.getCachedStreamUrls(initialExactVid).isNotEmpty())
        updateMediaNotification(isBuffering = !isInitialCached)

        // Record exact metadata & cover to Recently Played
        viewModelScope.launch {
            _recentlyPlayedTracks.value = recentlyPlayedManager.addTrack(boundTrack)
        }

        // Preload next track shortly after current starts
        precachedTrackId = null
        viewModelScope.launch {
            delay(500)
            checkAndPrecacheNextTrack()
        }

        // Fetch Lyrics asynchronously via LyricsRepository (LRCLIB + fallback) with pre-cached fast path
        fetchLyricsAsync(boundTrack)
        enrichCoverAsync(boundTrack)

        // Case 1: Local downloaded file exists -> Play locally via ExoPlayer
        val effectiveLocalPath = boundTrack.localFilePath?.takeIf { File(it).exists() }
            ?: downloadManager.getLocalFilePath(boundTrack.id)?.takeIf { File(it).exists() }
        if (effectiveLocalPath != null) {
            val localTrack = if (boundTrack.localFilePath == effectiveLocalPath) boundTrack else boundTrack.copy(localFilePath = effectiveLocalPath)
            isYouTubePlayback = false
            youTubeAudioEngine.stop()

            try {
                val mediaUri = Uri.fromFile(File(effectiveLocalPath))
                val artworkUri = if (boundTrack.coverUrl.isNotBlank()) Uri.parse(boundTrack.coverUrl) else null
                val mediaItem = MediaItem.Builder()
                    .setMediaId(boundTrack.id)
                    .setUri(mediaUri)
                    .setCustomCacheKey(boundTrack.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(boundTrack.title)
                            .setArtist(boundTrack.artist)
                            .apply {
                                if (artworkUri != null) setArtworkUri(artworkUri)
                            }
                            .build()
                    )
                    .build()

                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (pendingSeekPositionMs > 0L) {
                    exoPlayer.seekTo(pendingSeekPositionMs)
                    _currentPosition.value = pendingSeekPositionMs
                    pendingSeekPositionMs = 0L
                }
                exoPlayer.volume = 1.0f
                exoPlayer.playWhenReady = true
                isPlaybackPrepared = true
                preparedTrackId = boundTrack.id
                updateMediaNotification(isBuffering = false)

                stagePrefetchUpcomingTracks()
            } catch (e: Exception) {
                Log.e("CRANKR_PLAYER", "Gagal memutar audio lokal: ${e.message}")
            }
            return
        }

        // Case 1b: Prioritaskan Pembacaan SimpleCache Disk (Jika audio sudah ada di disk, putar langsung tanpa jaringan)
        val exactVid = getExactVideoId(boundTrack) ?: boundTrack.youtubeVideoId
        val diskCacheKey = exactVid ?: boundTrack.id
        val cachedDiskFile = MediaCacheManager.getCachedDiskFile(getApplication(), diskCacheKey)
        if (cachedDiskFile != null && cachedDiskFile.exists()) {
            isYouTubePlayback = false
            youTubeAudioEngine.stop()

            try {
                val artworkUri = if (boundTrack.coverUrl.isNotBlank()) Uri.parse(boundTrack.coverUrl) else null
                val mediaItem = MediaItem.Builder()
                    .setMediaId(boundTrack.id)
                    .setUri(Uri.fromFile(cachedDiskFile))
                    .setCustomCacheKey(diskCacheKey)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(boundTrack.title)
                            .setArtist(boundTrack.artist)
                            .apply {
                                if (artworkUri != null) setArtworkUri(artworkUri)
                            }
                            .build()
                    )
                    .build()

                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (pendingSeekPositionMs > 0L) {
                    exoPlayer.seekTo(pendingSeekPositionMs)
                    _currentPosition.value = pendingSeekPositionMs
                    pendingSeekPositionMs = 0L
                }
                exoPlayer.volume = 1.0f
                exoPlayer.playWhenReady = true
                isPlaybackPrepared = true
                preparedTrackId = boundTrack.id
                updateMediaNotification(isBuffering = false)

                stagePrefetchUpcomingTracks()
                return
            } catch (e: Exception) {
                Log.w("CRANKR_PLAYER", "Gagal memutar langsung dari SimpleCache disk: ${e.message}")
            }
        }

        // Case 1c: Bandcamp Track -> Stream direct MP3 audio via ExoPlayer
        if (boundTrack.isBandcamp) {
            // Jika trek adalah Purchase-Only (audio dikunci musisi khusus untuk pembeli resmi)
            if (boundTrack.isLockedPurchaseOnly) {
                _bandcampPurchaseTrack.value = boundTrack
                _isPlaying.value = false
                updateMediaNotification(isBuffering = false)
                return
            }

            isYouTubePlayback = false
            youTubeAudioEngine.stop()
            streamPlaybackJob = viewModelScope.launch {
                try {
                    val streamUrl = if (boundTrack.streamUrl.isNotBlank() && (boundTrack.streamUrl.startsWith("http://") || boundTrack.streamUrl.startsWith("https://"))) {
                        boundTrack.streamUrl
                    } else {
                        val parts = boundTrack.id.removePrefix("bc_").split("_")
                        val bandId = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                        val trackId = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                        if (bandId > 0 && trackId > 0) {
                            BandcampRepository(getApplication()).resolveStreamUrl(bandId, trackId)
                        } else {
                            null
                        }
                    }

                    if (!streamUrl.isNullOrBlank() && (streamUrl.startsWith("http://") || streamUrl.startsWith("https://"))) {
                        val readyTrack = boundTrack.copy(streamUrl = streamUrl)
                        if (_currentTrack.value?.id == boundTrack.id) {
                            _currentTrack.value = readyTrack
                        }
                        _recentlyPlayedTracks.value = recentlyPlayedManager.addTrack(readyTrack)
                        playbackRetryCount = 0
                        playDirectStreamWithFallback(readyTrack, listOf(streamUrl), 0)
                    } else {
                        // Jika stream kosong atau tidak streamable, buka dialog edukatif pembelian Bandcamp
                        _bandcampPurchaseTrack.value = boundTrack
                        _isPlaying.value = false
                        updateMediaNotification(isBuffering = false)
                    }
                } catch (e: Exception) {
                    Log.e("CRANKR_PLAYER", "Gagal memutar audio Bandcamp: ${e.message}")
                    _isPlaying.value = false
                    updateMediaNotification(isBuffering = false)
                }
            }
            return
        }

        // Case 2: Online Track -> Stream pure audio via ExoPlayer with candidate URLs & 5s fallback
        streamPlaybackJob = viewModelScope.launch {
            try {
                // Kunci videoId eksak (tanpa pencarian teks jika videoId valid)
                val exactVid = getExactVideoId(boundTrack)
                val targetVid = if (!exactVid.isNullOrEmpty()) {
                    exactVid
                } else {
                    AudioStreamResolver.resolveYouTubeVideoId(boundTrack)
                }

                if (!isActive) return@launch
                if (!targetVid.isNullOrEmpty()) {
                    val readyTrack = boundTrack.copy(youtubeVideoId = targetVid)
                    if (_currentTrack.value?.id == boundTrack.id) {
                        _currentTrack.value = readyTrack
                    }
                    _allTracks.update { list -> list.map { if (it.id == boundTrack.id) readyTrack else it } }
                    _selectedPlaylistTracks.update { list -> list.map { if (it.id == boundTrack.id) readyTrack else it } }
                    val plId = _selectedPlaylist.value?.id
                    if (plId != null) {
                        val plList = playlistTracksMap[plId]
                        if (plList != null) {
                            val idx = plList.indexOfFirst { it.id == readyTrack.id }
                            if (idx >= 0) {
                                plList[idx] = readyTrack
                                persistPlaylists()
                            }
                        }
                    }
                    _recentlyPlayedTracks.value = recentlyPlayedManager.addTrack(readyTrack)

                    // Prefetch & Cek cache memory stream URLs terlebih dahulu (0ms instant return)
                    val cachedUrls = AudioStreamResolver.getCachedStreamUrls(targetVid)
                    val candidateUrls = if (cachedUrls.isNotEmpty()) cachedUrls else AudioStreamResolver.resolveCandidateStreamUrls(targetVid)
                    if (!isActive) return@launch

                    if (candidateUrls.isNotEmpty()) {
                        playbackRetryCount = 0
                        playDirectStreamWithFallback(readyTrack, candidateUrls, 0)
                    } else {
                        // Fallback ke YouTubeAudioEngine jika direct stream server tidak merespons
                        isYouTubePlayback = true
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                        isPlaybackPrepared = true
                        preparedTrackId = readyTrack.id
                        youTubeAudioEngine.play(targetVid)
                        if (pendingSeekPositionMs > 0L) {
                            youTubeAudioEngine.seekTo(pendingSeekPositionMs)
                            _currentPosition.value = pendingSeekPositionMs
                            pendingSeekPositionMs = 0L
                        }
                        updateMediaNotification(isBuffering = true)
                    }
                } else {
                    Toast.makeText(getApplication(), "Lagu tidak ditemukan", Toast.LENGTH_SHORT).show()
                    _isPlaying.value = false
                    updateMediaNotification(isBuffering = false)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e("CRANKR_PLAYER", "Gagal inisialisasi streaming audio: ${e.message}")
                    _isPlaying.value = false
                    updateMediaNotification(isBuffering = false)
                }
            }
        }
    }

    private fun playDirectStreamWithFallback(track: Track, candidates: List<String>, candidateIndex: Int) {
        if (candidateIndex >= candidates.size) {
            handleStreamFailure(track)
            return
        }

        currentCandidateIndex = candidateIndex
        candidateStreamUrls = candidates
        val streamUrl = candidates[candidateIndex]

        isYouTubePlayback = false
        youTubeAudioEngine.stop()

        try {
            val artworkUri = if (track.coverUrl.isNotBlank()) Uri.parse(track.coverUrl) else null
            val exactVid = getExactVideoId(track) ?: track.youtubeVideoId
            val customKey = exactVid ?: track.id

            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.parse(streamUrl))
                .setCustomCacheKey(customKey)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .apply {
                            if (artworkUri != null) setArtworkUri(artworkUri)
                        }
                        .build()
                )
                .build()

            val seekTarget = if (pendingSeekPositionMs > 0L) {
                pendingSeekPositionMs
            } else {
                val cur = if (exoPlayer.currentPosition > 1000L) exoPlayer.currentPosition else _currentPosition.value
                if (cur > 1000L) cur else 0L
            }

            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            if (seekTarget > 0L) {
                exoPlayer.seekTo(seekTarget)
                _currentPosition.value = seekTarget
                pendingSeekPositionMs = 0L
            }
            exoPlayer.volume = 1.0f

            // Kompensasi gain otomatis berbasis metadata loudnessDb InnerTube
            val targetVid = exactVid ?: track.youtubeVideoId
            if (!targetVid.isNullOrBlank()) {
                val loudness = AudioStreamResolver.getLoudnessDb(targetVid)
                if (loudness != null) {
                    pcmEqualizerAudioProcessor.setLoudnessDb(loudness)
                }
            }

            exoPlayer.playWhenReady = true
            isPlaybackPrepared = true
            preparedTrackId = track.id
            _isPlaying.value = true
            updateMediaNotification(isBuffering = true)

            // Stage prefetch stream bertahap untuk 1-2 lagu berikutnya di antrean
            stagePrefetchUpcomingTracks()

            // Batas waktu maksimal 3.5 detik per server stream (Watchdog timer 3.5 detik)
            // Jika dalam 3.5 detik stream belum berputar atau muncul error di ExoPlayer,
            // langsung pindah ke URL stream cadangan berikutnya secara otomatis.
            streamWatchdogJob?.cancel()
            streamWatchdogJob = viewModelScope.launch {
                delay(3500L)
                if (isActive && !exoPlayer.isPlaying && _currentTrack.value?.id == track.id) {
                    Log.w("CRANKR_STREAM", "Stream candidate $candidateIndex timed out after 3.5s. Beralih ke kandidat berikutnya.")
                    playDirectStreamWithFallback(track, candidates, candidateIndex + 1)
                }
            }
        } catch (e: Exception) {
            Log.e("CRANKR_STREAM", "Gagal memutar kandidat stream $candidateIndex: ${e.message}")
            playDirectStreamWithFallback(track, candidates, candidateIndex + 1)
        }
    }

    private fun handleStreamFailure(track: Track) {
        val savedPos = if (exoPlayer.currentPosition > 1000L) exoPlayer.currentPosition else _currentPosition.value
        if (savedPos > 1000L && pendingSeekPositionMs == 0L) {
            pendingSeekPositionMs = savedPos
        }

        streamWatchdogJob?.cancel()
        streamWatchdogJob = null

        if (track.isBandcamp) {
            Log.w("CRANKR_PLAYER", "Kegagalan pemutaran Bandcamp track, meresolusi ulang atau membuka opsi pembelian")
            viewModelScope.launch {
                val parts = track.id.removePrefix("bc_").split("_")
                val bandId = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                val trackId = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                val freshUrl = if (bandId > 0 && trackId > 0) {
                    BandcampRepository(getApplication()).resolveStreamUrl(bandId, trackId)
                } else null

                if (!freshUrl.isNullOrBlank() && freshUrl != track.streamUrl) {
                    val freshTrack = track.copy(streamUrl = freshUrl)
                    if (_currentTrack.value?.id == track.id) {
                        _currentTrack.value = freshTrack
                    }
                    playDirectStreamWithFallback(freshTrack, listOf(freshUrl), 0)
                } else {
                    _bandcampPurchaseTrack.value = track
                    _isPlaying.value = false
                    updateMediaNotification(isBuffering = false)
                }
            }
            return
        }

        if (playbackRetryCount < maxPlaybackRetries) {
            playbackRetryCount++
            val failedVid = track.youtubeVideoId.orEmpty()
            if (failedVid.isNotBlank()) {
                AudioStreamResolver.markVideoRestricted(failedVid)
            }

            viewModelScope.launch {
                delay(800L * playbackRetryCount)
                val altVid = AudioStreamResolver.resolveAlternativeVideoId(
                    title = track.title,
                    artist = track.artist,
                    failedVideoId = failedVid,
                    expectedDurationSeconds = track.durationSeconds
                )
                if (!altVid.isNullOrBlank() && altVid != failedVid) {
                    val updatedTrack = track.copy(youtubeVideoId = altVid)
                    if (_currentTrack.value?.id == track.id) {
                        _currentTrack.value = updatedTrack
                    }
                    _allTracks.update { list -> list.map { if (it.id == track.id) updatedTrack else it } }
                    _selectedPlaylistTracks.update { list -> list.map { if (it.id == track.id) updatedTrack else it } }

                    val candidates = AudioStreamResolver.resolveCandidateStreamUrls(altVid)
                    if (candidates.isNotEmpty()) {
                        playDirectStreamWithFallback(updatedTrack, candidates, 0)
                        return@launch
                    }
                }

                // Fallback ke YouTubeAudioEngine jika candidate stream URLs tidak tersedia
                if (!track.youtubeVideoId.isNullOrBlank()) {
                    isYouTubePlayback = true
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    isPlaybackPrepared = true
                    preparedTrackId = track.id
                    youTubeAudioEngine.play(track.youtubeVideoId)
                    if (pendingSeekPositionMs > 0L) {
                        youTubeAudioEngine.seekTo(pendingSeekPositionMs)
                        _currentPosition.value = pendingSeekPositionMs
                        pendingSeekPositionMs = 0L
                    }
                    _isPlaying.value = true
                    updateMediaNotification(isBuffering = true)
                } else {
                    _isPlaying.value = false
                    updateMediaNotification(isBuffering = false)
                    Toast.makeText(getApplication(), "Lagu tidak dapat diputar. Memutar lagu berikutnya...", Toast.LENGTH_SHORT).show()
                    playNext()
                }
            }
        } else {
            playbackRetryCount = 0
            _isPlaying.value = false
            updateMediaNotification(isBuffering = false)
            Toast.makeText(getApplication(), "Lagu tidak dapat diputar. Memutar lagu berikutnya...", Toast.LENGTH_SHORT).show()
            playNext()
        }
    }

    private fun extractSpotifyTrackId(id: String): String? {
        val stripped = id.removePrefix("sp_").substringAfter("track:")
        return if (stripped.length in 15..35 && stripped.matches(Regex("^[a-zA-Z0-9]+$"))) {
            stripped
        } else null
    }

    private fun fallbackToExoPlayer(track: Track?) {
        if (track == null) return
        try {
            if (track.streamUrl.startsWith("http") && !track.streamUrl.contains("youtube.com") && !track.streamUrl.contains("soundhelix.com")) {
                isYouTubePlayback = false
                youTubeAudioEngine.stop()
                val artworkUri = if (track.coverUrl.isNotBlank()) Uri.parse(track.coverUrl) else null
                val mediaItem = MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(Uri.parse(track.streamUrl))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .apply {
                                if (artworkUri != null) setArtworkUri(artworkUri)
                            }
                            .build()
                    )
                    .build()
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (pendingSeekPositionMs > 0L) {
                    exoPlayer.seekTo(pendingSeekPositionMs)
                    _currentPosition.value = pendingSeekPositionMs
                    pendingSeekPositionMs = 0L
                }
                exoPlayer.volume = 1.0f
                exoPlayer.playWhenReady = true
                isPlaybackPrepared = true
                preparedTrackId = track.id
            }
        } catch (e: Exception) {
            Log.e("CRANKR_PLAYER", "Gagal fallback ke ExoPlayer: ${e.message}")
        }
    }

    fun pause() {
        val current = _currentTrack.value ?: return
        val currentPos = if (!isYouTubePlayback) {
            exoPlayer.currentPosition
        } else {
            _currentPosition.value
        }
        if (currentPos > 0L) {
            _currentPosition.value = currentPos
            crankrPrefs.edit().putLong("last_position_ms", currentPos).apply()
        }

        if (isYouTubePlayback) {
            youTubeAudioEngine.pause()
        } else {
            exoPlayer.pause()
        }
        _isPlaying.value = false
        updateMediaNotification()

        if (!isSyncingFromRemote && isListenTogetherHost.value) {
            val pos = if (isYouTubePlayback) _currentPosition.value else exoPlayer.currentPosition
            listenTogetherManager.emitPause(pos)
        }
    }

    fun resume() {
        val current = _currentTrack.value ?: run {
            if (_allTracks.value.isNotEmpty()) {
                playTrack(_allTracks.value.first())
            }
            return
        }

        // Case 1: Cold start restored track where player media item has never been prepared yet
        if (!isPlaybackPrepared || preparedTrackId != current.id) {
            val resumePos = if (pendingSeekPositionMs > 0L) pendingSeekPositionMs else _currentPosition.value
            val activeQueue = _playbackQueue.value.ifEmpty { listOf(current) }
            playTrack(current, activeQueue, _playbackQueueTitle.value, initialPositionMs = resumePos)
            return
        }

        // Case 2: Pure resume from current position without re-preparing or seeking to 0
        if (isYouTubePlayback) {
            youTubeAudioEngine.resume()
        } else {
            exoPlayer.play()
        }
        _isPlaying.value = true
        updateMediaNotification()

        if (!isSyncingFromRemote && isListenTogetherHost.value) {
            val trackId = _currentTrack.value?.id ?: ""
            val pos = if (isYouTubePlayback) _currentPosition.value else exoPlayer.currentPosition
            listenTogetherManager.emitPlay(trackId, pos)
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value || exoPlayer.isPlaying || youTubeAudioEngine.isCurrentlyPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun seekTo(positionMs: Long) {
        _currentPosition.value = positionMs
        if (isYouTubePlayback) {
            youTubeAudioEngine.seekTo(positionMs)
        } else {
            exoPlayer.seekTo(positionMs)
        }
        updateMediaNotification()

        if (!isSyncingFromRemote && isListenTogetherHost.value) {
            listenTogetherManager.emitSeek(positionMs)
        }
    }

    fun playNext() {
        val tracks = if (_playbackQueue.value.isNotEmpty()) _playbackQueue.value else getActiveTrackList()
        if (tracks.isEmpty()) return

        val current = _currentTrack.value
        val currentIndex = tracks.indexOfFirst { it.id == current?.id }.let {
            if (it >= 0) it else currentQueueIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        }

        val nextIndex = currentIndex + 1

        if (nextIndex in tracks.indices) {
            val nextTrack = tracks[nextIndex]
            currentQueueIndex = nextIndex

            // Fast path 0ms: jika lagu berikutnya sudah di-stage di ExoPlayer
            if (!isYouTubePlayback && exoPlayer.hasNextMediaItem()) {
                val nextExoIndex = exoPlayer.currentMediaItemIndex + 1
                if (nextExoIndex < exoPlayer.mediaItemCount && exoPlayer.getMediaItemAt(nextExoIndex).mediaId == nextTrack.id) {
                    exoPlayer.seekToNextMediaItem()
                    return
                }
            }
            playTrack(nextTrack)
        } else {
            if (_repeatMode.value == RepeatMode.ALL) {
                if (_isShuffle.value && tracks.size > 1) {
                    val reshuffled = tracks.shuffled()
                    _playbackQueue.value = reshuffled
                    currentQueueIndex = 0
                    persistPlaybackQueue(reshuffled, _playbackQueueTitle.value)
                    playTrack(reshuffled.first())
                } else {
                    currentQueueIndex = 0
                    val firstTrack = tracks.first()
                    if (!isYouTubePlayback && exoPlayer.hasNextMediaItem()) {
                        val nextExoIndex = exoPlayer.currentMediaItemIndex + 1
                        if (nextExoIndex < exoPlayer.mediaItemCount && exoPlayer.getMediaItemAt(nextExoIndex).mediaId == firstTrack.id) {
                            exoPlayer.seekToNextMediaItem()
                            return
                        }
                    }
                    playTrack(firstTrack)
                }
            } else if (_isAutoplayEnabled.value && current != null) {
                triggerAutoplay(current)
            } else {
                _isPlaying.value = false
                if (isYouTubePlayback) youTubeAudioEngine.pause() else exoPlayer.pause()
                updateMediaNotification(isBuffering = false)
            }
        }
    }

    fun playPrevious() {
        val tracks = if (_playbackQueue.value.isNotEmpty()) _playbackQueue.value else getActiveTrackList()
        if (tracks.isEmpty() && playbackHistoryStack.isEmpty()) return

        // 1. Jika durasi lagu saat ini di atas 3 detik, ulangi lagu dari awal
        if (_currentPosition.value > 3000L) {
            seekTo(0)
            return
        }

        // 2. Jika durasi di bawah 3 detik, ambil lagu sebelumnya dari tumpukan riwayat.
        // DILARANG memicu fungsi acak lagu baru (shuffle locked to next/auto only)!
        if (playbackHistoryStack.isNotEmpty()) {
            val previousTrack = playbackHistoryStack.removeLast()
            isNavigatingHistory = true
            try {
                val targetIndex = tracks.indexOfFirst { it.id == previousTrack.id }
                if (targetIndex >= 0) {
                    currentQueueIndex = targetIndex
                }
                playTrack(previousTrack)
            } finally {
                isNavigatingHistory = false
            }
            return
        }

        // 3. Fallback jika tumpukan riwayat kosong: navigasi mundur berurutan di antrean saat ini
        val current = _currentTrack.value
        val currentIndex = tracks.indexOfFirst { it.id == current?.id }.let {
            if (it >= 0) it else currentQueueIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        }
        val prevIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else {
            tracks.size - 1
        }
        currentQueueIndex = prevIndex
        playTrack(tracks[prevIndex])
    }

    fun toggleShuffle() {
        val newShuffle = !_isShuffle.value
        _isShuffle.value = newShuffle
        val currentQueue = _playbackQueue.value
        val current = _currentTrack.value
        if (newShuffle) {
            if (currentQueue.size > 2 && current != null) {
                if (originalPlaybackQueue == null) {
                    originalPlaybackQueue = currentQueue
                }
                val curIdx = currentQueue.indexOfFirst { it.id == current.id }
                if (curIdx >= 0 && curIdx < currentQueue.size - 1) {
                    val pastAndCurrent = currentQueue.take(curIdx + 1)
                    val upcoming = currentQueue.drop(curIdx + 1).shuffled()
                    val reordered = pastAndCurrent + upcoming
                    _playbackQueue.value = reordered
                    currentQueueIndex = curIdx
                    persistPlaybackQueue(reordered, _playbackQueueTitle.value)
                    syncExoPlayerUpcomingQueue()
                }
            }
        } else {
            val orig = originalPlaybackQueue
            if (orig != null && current != null) {
                _playbackQueue.value = orig
                val curIdx = orig.indexOfFirst { it.id == current.id }
                currentQueueIndex = if (curIdx >= 0) curIdx else 0
                persistPlaybackQueue(orig, _playbackQueueTitle.value)
                syncExoPlayerUpcomingQueue()
            }
            originalPlaybackQueue = null
        }
    }

    fun shufflePlayPlaylist(tracks: List<Track>, playlistTitle: String? = null) {
        if (tracks.isEmpty()) return
        _isShuffle.value = true
        val shuffled = tracks.shuffled()
        playPlaylist(shuffled, 0, playlistTitle ?: "Antrean Acak")
    }

    fun setRepeatMode(mode: RepeatMode, showFeedbackToast: Boolean = false) {
        _repeatMode.value = mode
        _isRepeat.value = mode != RepeatMode.OFF
        exoPlayer.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_OFF // Single-play override pada ExoPlayer: lagu saat ini diputar sekali saja tanpa looping internal, transisi dikendalikan oleh antrean aplikasi
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        MusicNotificationReceiver.isRepeatOne = (mode == RepeatMode.ONE)
        // Pastikan logika penggantian mode repeat tidak menyentuh player.volume.
        // Volume tetap pada nilai default 1.0f.
        exoPlayer.volume = 1.0f

        if (showFeedbackToast) {
            val toastMessage = when (mode) {
                RepeatMode.ALL -> "Putar 1 Kali Lalu Berpindah (Play Once & Next)"
                RepeatMode.ONE -> "Loop Terus Lagu Ini (Repeat One)"
                RepeatMode.OFF -> "Matikan Loop / Off (Mode Normal)"
            }
            Toast.makeText(getApplication(), toastMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(nextMode, showFeedbackToast = true)
    }

    fun toggleFavorite(trackId: String, track: Track? = null) {
        val targetTrack = track
            ?: _likedTracks.value.firstOrNull { it.id == trackId }
            ?: _allTracks.value.firstOrNull { it.id == trackId }
            ?: _playbackQueue.value.firstOrNull { it.id == trackId }
            ?: _selectedPlaylistTracks.value.firstOrNull { it.id == trackId }
            ?: _searchQuickResults.value.firstOrNull { it.id == trackId }
            ?: _currentTrack.value?.takeIf { it.id == trackId }

        if (targetTrack == null) {
            _favoriteTrackIds.update { set ->
                if (set.contains(trackId)) set - trackId else set + trackId
            }
            return
        }

        viewModelScope.launch {
            val updated = likedSongsManager.toggleLiked(targetTrack)
            _likedTracks.value = updated
            _favoriteTrackIds.value = updated.map { it.id }.toSet()
            if (_selectedPlaylist.value?.id == "liked_songs") {
                _selectedPlaylistTracks.value = updated
                _selectedPlaylist.value = _selectedPlaylist.value?.copy(trackCount = updated.size)
            }
            if (_currentTrack.value?.id == trackId) {
                updateMediaNotification()
            }
        }
    }

    fun getLikedSongsPlaylist(): SpotifyPlaylist {
        return SpotifyPlaylist(
            id = "liked_songs",
            name = "Lagu yang Disukai",
            description = "Koleksi semua lagu favorit Anda",
            imageUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=400&auto=format&fit=crop&q=80",
            trackCount = _likedTracks.value.size,
            ownerName = "StaticRebel",
            isSynced = false
        )
    }

    // Queue Management Controls & Fast Playlist Playback
    fun setQueue(sortedList: List<Track>, clickedIndex: Int, queueTitle: String? = null) {
        if (sortedList.isEmpty()) return
        val safeIndex = clickedIndex.coerceIn(0, sortedList.size - 1)
        playPlaylist(sortedList, safeIndex, queueTitle)
    }

    /**
     * Memutar playlist secara instan dengan memasukkan seluruh daftar lagu ke antrean ExoPlayer
     * menggunakan setMediaItems(mediaItems, startIndex, 0L).
     * Memanfaatkan youtubeVideoId yang sudah tersedia tanpa pencarian teks ulang,
     * serta melakukan prefetch URL stream dan buffering cepat.
     */
    fun playPlaylist(tracks: List<Track>, startIndex: Int = 0, playlistTitle: String? = null, initialPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.size - 1)
        val targetTrack = MetadataSanitizer.sanitizeTrack(tracks[safeIndex]) ?: tracks[safeIndex]

        // Kirim perintah putar langsung ke MusicService tanpa menunggu proses render ulang UI / state ViewModel
        MusicPlaybackService.playDirect(getApplication(), targetTrack)
        fullCachedUpcomingTrackId = null

        // Jika decoder audio sudah diinisialisasi lebih awal (playWhenReady = false), aktifkan seketika
        if (preInitializedTrackId == targetTrack.id && isPlaybackPrepared && !isYouTubePlayback) {
            preInitializedTrackId = null
            if (initialPositionMs > 0L) {
                _currentPosition.value = initialPositionMs
                exoPlayer.seekTo(initialPositionMs)
            } else {
                _currentPosition.value = 0L
            }
            _currentTrack.value = targetTrack
            _isPlaying.value = true
            _duration.value = (targetTrack.durationSeconds * 1000L).coerceAtLeast(1L)
            exoPlayer.volume = 1.0f
            exoPlayer.playWhenReady = true
            updateMediaNotification(isBuffering = false)

            _playbackQueue.value = tracks
            val title = playlistTitle ?: _selectedPlaylist.value?.name ?: "Playlist"
            _playbackQueueTitle.value = title
            currentQueueIndex = safeIndex
            persistPlaybackQueue(tracks, title)

            stagePrefetchUpcomingTracks()
            preFetchVisibleTracks(tracks.drop(safeIndex + 1), limit = 6)
            return
        }

        // Simpan setiap lagu yang selesai atau sedang diputar ke dalam tumpukan riwayat
        val oldTrack = _currentTrack.value
        if (!isNavigatingHistory && oldTrack != null && oldTrack.id != targetTrack.id) {
            playbackHistoryStack.addLast(oldTrack)
            if (playbackHistoryStack.size > 100) {
                playbackHistoryStack.removeFirst()
            }
        }

        streamPlaybackJob?.cancel()
        streamPlaybackJob = null
        streamWatchdogJob?.cancel()
        streamWatchdogJob = null

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        youTubeAudioEngine.stop()

        if (initialPositionMs > 0L) {
            _currentPosition.value = initialPositionMs
            pendingSeekPositionMs = initialPositionMs
            saveLastPlayed(targetTrack, initialPositionMs)
        } else {
            _currentPosition.value = 0L
            pendingSeekPositionMs = 0L
            saveLastPlayed(targetTrack, 0L)
        }
        _currentTrack.value = targetTrack
        _isPlaying.value = true
        _duration.value = (targetTrack.durationSeconds * 1000L).coerceAtLeast(1L)
        isPlaybackPrepared = false
        preparedTrackId = targetTrack.id
        playbackRetryCount = 0
        flushCurrentTrackPlayDuration()
        activeTrackPlayDurationMs = 0L
        hasRecordedHistoryForCurrentTrack = false

        _playbackQueue.value = tracks
        val title = playlistTitle ?: _selectedPlaylist.value?.name ?: "Playlist"
        _playbackQueueTitle.value = title
        currentQueueIndex = safeIndex
        persistPlaybackQueue(tracks, title)

        viewModelScope.launch(Dispatchers.IO) {
            listeningStatsManager.recordTrackPlay(targetTrack)
            val stats = listeningStatsManager.getStats()
            _listeningStats.value = stats
        }
        startFadeIn()

        if (!isSyncingFromRemote && isListenTogetherHost.value) {
            listenTogetherManager.emitTrackChange(targetTrack, 0L)
        }

        // Hindari spinner loading jika stream audio sudah ada di memori/disk
        val initialExactVid = getExactVideoId(targetTrack) ?: targetTrack.youtubeVideoId
        val isInitialCached = (targetTrack.localFilePath != null && File(targetTrack.localFilePath).exists()) ||
            (!initialExactVid.isNullOrEmpty() && MediaCacheManager.getCachedDiskFile(getApplication(), initialExactVid) != null) ||
            (!initialExactVid.isNullOrEmpty() && AudioStreamResolver.getCachedStreamUrls(initialExactVid).isNotEmpty())
        updateMediaNotification(isBuffering = !isInitialCached)

        viewModelScope.launch {
            _recentlyPlayedTracks.value = recentlyPlayedManager.addTrack(targetTrack)
        }

        // Asynchronous enrichment (tidak menunda audio)
        enrichCoverAsync(targetTrack)
        fetchLyricsAsync(targetTrack)

        // Kasus 1: File unduhan lokal tersedia
        if (targetTrack.localFilePath != null && File(targetTrack.localFilePath).exists()) {
            isYouTubePlayback = false
            youTubeAudioEngine.stop()

            try {
                val mediaUri = Uri.fromFile(File(targetTrack.localFilePath))
                val artworkUri = if (targetTrack.coverUrl.isNotBlank()) Uri.parse(targetTrack.coverUrl) else null
                val mediaItem = MediaItem.Builder()
                    .setMediaId(targetTrack.id)
                    .setUri(mediaUri)
                    .setCustomCacheKey(targetTrack.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(targetTrack.title)
                            .setArtist(targetTrack.artist)
                            .apply { if (artworkUri != null) setArtworkUri(artworkUri) }
                            .build()
                    )
                    .build()

                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (pendingSeekPositionMs > 0L) {
                    exoPlayer.seekTo(pendingSeekPositionMs)
                    _currentPosition.value = pendingSeekPositionMs
                    pendingSeekPositionMs = 0L
                }
                exoPlayer.volume = 1.0f
                exoPlayer.playWhenReady = true
                isPlaybackPrepared = true
                preparedTrackId = targetTrack.id
                updateMediaNotification(isBuffering = false)

                stagePrefetchUpcomingTracks()
            } catch (e: Exception) {
                Log.e("CRANKR_PLAYER", "Gagal memutar audio lokal playlist: ${e.message}")
            }
            return
        }

        // Kasus 1b: Prioritaskan Pembacaan SimpleCache Disk (Jika audio sudah ada di disk, putar langsung tanpa jaringan)
        val exactVid = getExactVideoId(targetTrack) ?: targetTrack.youtubeVideoId
        val diskCacheKey = exactVid ?: targetTrack.id
        val cachedDiskFile = MediaCacheManager.getCachedDiskFile(getApplication(), diskCacheKey)
        if (cachedDiskFile != null && cachedDiskFile.exists()) {
            isYouTubePlayback = false
            youTubeAudioEngine.stop()

            try {
                val artworkUri = if (targetTrack.coverUrl.isNotBlank()) Uri.parse(targetTrack.coverUrl) else null
                val mediaItem = MediaItem.Builder()
                    .setMediaId(targetTrack.id)
                    .setUri(Uri.fromFile(cachedDiskFile))
                    .setCustomCacheKey(diskCacheKey)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(targetTrack.title)
                            .setArtist(targetTrack.artist)
                            .apply { if (artworkUri != null) setArtworkUri(artworkUri) }
                            .build()
                    )
                    .build()

                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (pendingSeekPositionMs > 0L) {
                    exoPlayer.seekTo(pendingSeekPositionMs)
                    _currentPosition.value = pendingSeekPositionMs
                    pendingSeekPositionMs = 0L
                }
                exoPlayer.volume = 1.0f
                exoPlayer.playWhenReady = true
                isPlaybackPrepared = true
                preparedTrackId = targetTrack.id
                updateMediaNotification(isBuffering = false)

                stagePrefetchUpcomingTracks()
                preFetchVisibleTracks(tracks.drop(safeIndex + 1), limit = 6)
                return
            } catch (e: Exception) {
                Log.w("CRANKR_PLAYER", "Gagal memutar playlist dari SimpleCache disk: ${e.message}")
            }
        }

        // Kasus 1c: Bandcamp Track -> Stream direct MP3 audio via ExoPlayer
        if (targetTrack.isBandcamp) {
            if (targetTrack.isLockedPurchaseOnly) {
                _bandcampPurchaseTrack.value = targetTrack
                _isPlaying.value = false
                updateMediaNotification(isBuffering = false)
                return
            }

            isYouTubePlayback = false
            youTubeAudioEngine.stop()
            streamPlaybackJob = viewModelScope.launch {
                try {
                    val streamUrl = if (targetTrack.streamUrl.isNotBlank() && targetTrack.streamUrl.contains("ts=")) {
                        targetTrack.streamUrl
                    } else {
                        val parts = targetTrack.id.removePrefix("bc_").split("_")
                        val bandId = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                        val trackId = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                        if (bandId > 0 && trackId > 0) {
                            BandcampRepository(getApplication()).resolveStreamUrl(bandId, trackId)
                        } else {
                            null
                        }
                    }

                    if (!streamUrl.isNullOrBlank() && streamUrl.contains("ts=")) {
                        val readyTrack = targetTrack.copy(streamUrl = streamUrl)
                        if (_currentTrack.value?.id == targetTrack.id) {
                            _currentTrack.value = readyTrack
                        }
                        _recentlyPlayedTracks.value = recentlyPlayedManager.addTrack(readyTrack)
                        playbackRetryCount = 0
                        playDirectStreamWithFallback(readyTrack, listOf(streamUrl), 0)
                    } else {
                        // Jika stream kosong / terkunci, buka dialog pembelian Bandcamp
                        _bandcampPurchaseTrack.value = targetTrack
                        _isPlaying.value = false
                        updateMediaNotification(isBuffering = false)
                    }
                } catch (e: Exception) {
                    Log.e("CRANKR_PLAYER", "Gagal memutar audio Bandcamp di playlist: ${e.message}")
                    _isPlaying.value = false
                    updateMediaNotification(isBuffering = false)
                }
            }
            return
        }

        // Kasus 2: Pemutaran Streaming Online
        streamPlaybackJob = viewModelScope.launch {
            try {
                // 1. Kunci videoId eksak (tanpa pencarian teks jika videoId valid)
                val exactVid = getExactVideoId(targetTrack)
                val targetVid = if (!exactVid.isNullOrEmpty()) {
                    exactVid
                } else {
                    AudioStreamResolver.resolveYouTubeVideoId(targetTrack)
                }

                if (!isActive) return@launch
                if (!targetVid.isNullOrEmpty()) {
                    val readyTrack = if (targetTrack.youtubeVideoId == targetVid) targetTrack else targetTrack.copy(youtubeVideoId = targetVid)
                    if (_currentTrack.value?.id == targetTrack.id) {
                        _currentTrack.value = readyTrack
                    }

                    // 2. Prefetch URL stream & buffering cepat: periksa memory cache dahulu (0ms instant return)
                    val cachedUrls = AudioStreamResolver.getCachedStreamUrls(targetVid)
                    val candidateUrls = if (cachedUrls.isNotEmpty()) {
                        cachedUrls
                    } else {
                        AudioStreamResolver.resolveCandidateStreamUrls(targetVid)
                    }

                    if (!isActive) return@launch

                    if (candidateUrls.isNotEmpty()) {
                        playbackRetryCount = 0
                        playDirectStreamWithFallback(readyTrack, candidateUrls, 0)
                        preFetchVisibleTracks(tracks.drop(safeIndex + 1), limit = 6)
                    } else {
                        isYouTubePlayback = true
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                        isPlaybackPrepared = true
                        preparedTrackId = readyTrack.id
                        youTubeAudioEngine.play(targetVid)
                        if (pendingSeekPositionMs > 0L) {
                            youTubeAudioEngine.seekTo(pendingSeekPositionMs)
                            _currentPosition.value = pendingSeekPositionMs
                            pendingSeekPositionMs = 0L
                        }
                        updateMediaNotification(isBuffering = true)
                    }
                } else {
                    Toast.makeText(getApplication(), "Lagu tidak ditemukan", Toast.LENGTH_SHORT).show()
                    _isPlaying.value = false
                    updateMediaNotification(isBuffering = false)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e("CRANKR_PLAYER", "Gagal inisialisasi streaming audio playlist: ${e.message}")
                    _isPlaying.value = false
                    updateMediaNotification(isBuffering = false)
                }
            }
        }
    }

    private fun preFetchAdjacentTracks(tracks: List<Track>, currentIndex: Int) {
        stagePrefetchUpcomingTracks()
    }

    private fun enrichCoverAsync(track: Track) {
        val cover = track.coverUrl
        if (cover.isBlank() || cover.contains("unsplash.com") || cover.contains("ytimg.com")) {
            coverLoadingJob?.cancel()
            val targetTrackId = track.id
            coverLoadingJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val spotifyId = extractSpotifyTrackId(track.id)
                    val spotifyCover = if (!spotifyId.isNullOrBlank()) {
                        TrackCoverResolver.fetchSpotifyOEmbedCover(spotifyId)
                    } else null
                    val itunesCover = if (spotifyCover.isNullOrBlank()) {
                        TrackCoverResolver.fetchItunesCover(track.title, track.artist)?.coverUrl
                    } else null
                    val resolvedArt = spotifyCover ?: itunesCover
                    if (isActive && !resolvedArt.isNullOrBlank() && _currentTrack.value?.id == targetTrackId) {
                        val trackWithCover = track.copy(coverUrl = resolvedArt)
                        _currentTrack.value = trackWithCover
                        _allTracks.update { list -> list.map { if (it.id == track.id) trackWithCover else it } }
                        _selectedPlaylistTracks.update { list -> list.map { if (it.id == track.id) trackWithCover else it } }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun fetchLyricsAsync(track: Track) {
        lyricsJob?.cancel()
        val cachedLyrics = lyricsRepository.getCachedLyrics(track.id) ?: preloadedLyricsCache[track.id]
        if (cachedLyrics != null) {
            _currentLyricsData.value = cachedLyrics
            _currentLyrics.value = cachedLyrics.rawPlain ?: cachedLyrics.lines.joinToString("\n") { it.text }
            _isLoadingLyrics.value = false
        } else {
            _currentLyrics.value = null
            _currentLyricsData.value = null
            _isLoadingLyrics.value = true
            lyricsJob = viewModelScope.launch {
                try {
                    val fetchedData = lyricsRepository.fetchLyricsData(track)
                    if (isActive && _currentTrack.value?.id == track.id) {
                        _currentLyricsData.value = fetchedData
                        _currentLyrics.value = fetchedData?.rawPlain ?: fetchedData?.lines?.joinToString("\n") { it.text }
                        if (fetchedData != null) {
                            preloadedLyricsCache[track.id] = fetchedData
                        }
                    }
                } catch (_: Exception) {
                    if (isActive && _currentTrack.value?.id == track.id) {
                        _currentLyricsData.value = null
                        _currentLyrics.value = null
                    }
                } finally {
                    if (isActive && _currentTrack.value?.id == track.id) {
                        _isLoadingLyrics.value = false
                    }
                }
            }
        }
    }

    fun playTrackAtIndex(newTrackIndex: Int) {
        val tracks = if (_playbackQueue.value.isNotEmpty()) _playbackQueue.value else getActiveTrackList()
        if (tracks.isEmpty()) return
        val safeIndex = newTrackIndex.coerceIn(0, tracks.size - 1)
        currentQueueIndex = safeIndex
        val targetTrack = tracks[safeIndex]

        // Jika ExoPlayer menggunakan antrean playlist (playlist queue)
        if (!isYouTubePlayback && exoPlayer.mediaItemCount > safeIndex) {
            _currentPosition.value = 0L
            _currentTrack.value = targetTrack
            _isPlaying.value = true
            _duration.value = (targetTrack.durationSeconds * 1000L).coerceAtLeast(1L)
            // Hindari spinner loading jika stream audio sudah ada di memori/disk
        val initialExactVid = getExactVideoId(targetTrack) ?: targetTrack.youtubeVideoId
        val isInitialCached = (targetTrack.localFilePath != null && File(targetTrack.localFilePath).exists()) ||
            (!initialExactVid.isNullOrEmpty() && MediaCacheManager.getCachedDiskFile(getApplication(), initialExactVid) != null) ||
            (!initialExactVid.isNullOrEmpty() && AudioStreamResolver.getCachedStreamUrls(initialExactVid).isNotEmpty())
        updateMediaNotification(isBuffering = !isInitialCached)
            exoPlayer.seekTo(safeIndex, 0L)
            exoPlayer.playWhenReady = true
            return
        }

        playTrack(targetTrack, tracks, _playbackQueueTitle.value)
    }

    fun setPlaybackQueue(tracks: List<Track>, title: String = "Antrean Musik") {
        _playbackQueue.value = tracks
        _playbackQueueTitle.value = title
        val current = _currentTrack.value
        currentQueueIndex = if (current != null) tracks.indexOfFirst { it.id == current.id }.coerceAtLeast(0) else 0
        persistPlaybackQueue(tracks, title)
        preFetchVisibleTracks(tracks, limit = 6)
    }

    fun playTrackInQueue(track: Track) {
        playTrack(track)
    }

    fun playNextInQueue(track: Track) {
        if (track.title.isBlank()) return
        val sanitized = MetadataSanitizer.sanitizeTrack(track) ?: track
        val current = _currentTrack.value
        if (current == null) {
            playTrack(sanitized)
            return
        }
        val list = _playbackQueue.value.toMutableList()
        list.removeAll { it.id == sanitized.id }
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex >= 0) {
            list.add(currentIndex + 1, sanitized)
        } else {
            list.add(0, current)
            list.add(1, sanitized)
        }
        _playbackQueue.value = list
        Toast.makeText(getApplication(), "Diputar berikutnya: ${sanitized.title}", Toast.LENGTH_SHORT).show()
    }

    fun addToEndOfQueue(track: Track) {
        if (track.title.isBlank()) return
        val sanitized = MetadataSanitizer.sanitizeTrack(track) ?: track
        val current = _currentTrack.value
        if (current == null) {
            playTrack(sanitized)
            return
        }
        val list = _playbackQueue.value.toMutableList()
        if (list.none { it.id == sanitized.id }) {
            list.add(sanitized)
            _playbackQueue.value = list
        }
        Toast.makeText(getApplication(), "Ditambahkan ke antrean: ${sanitized.title}", Toast.LENGTH_SHORT).show()
    }

    fun moveQueueTrackToTop(trackId: String) {
        val current = _currentTrack.value
        val list = _playbackQueue.value.toMutableList()
        val index = list.indexOfFirst { it.id == trackId }
        if (index > 0) {
            val item = list.removeAt(index)
            val insertIndex = if (current != null) {
                val currentIndex = list.indexOfFirst { it.id == current.id }
                if (currentIndex >= 0) currentIndex + 1 else 0
            } else {
                0
            }
            list.add(insertIndex.coerceAtMost(list.size), item)
            _playbackQueue.value = list
            currentQueueIndex = list.indexOfFirst { it.id == _currentTrack.value?.id }
            persistPlaybackQueue(list, _playbackQueueTitle.value)
            syncExoPlayerUpcomingQueue()
        }
    }

    fun moveQueueTrackUp(trackId: String) {
        val current = _currentTrack.value
        val list = _playbackQueue.value.toMutableList()
        val index = list.indexOfFirst { it.id == trackId }
        val currentIndex = if (current != null) list.indexOfFirst { it.id == current.id } else -1

        // Only allow moving up if it doesn't displace the current track
        if (index > 0 && index - 1 != currentIndex) {
            val item = list.removeAt(index)
            list.add(index - 1, item)
            _playbackQueue.value = list
            currentQueueIndex = list.indexOfFirst { it.id == _currentTrack.value?.id }
            persistPlaybackQueue(list, _playbackQueueTitle.value)
            syncExoPlayerUpcomingQueue()
        }
    }

    fun moveQueueTrackDown(trackId: String) {
        val list = _playbackQueue.value.toMutableList()
        val index = list.indexOfFirst { it.id == trackId }
        if (index >= 0 && index < list.size - 1) {
            val item = list.removeAt(index)
            list.add(index + 1, item)
            _playbackQueue.value = list
            currentQueueIndex = list.indexOfFirst { it.id == _currentTrack.value?.id }
            persistPlaybackQueue(list, _playbackQueueTitle.value)
            syncExoPlayerUpcomingQueue()
        }
    }

    /**
     * Memindahkan urutan antrean berdasarkan indeks posisi asal dan tujuan (Seret & Lepas / Drag & Drop).
     * Langsung menyinkronkan posisi media item pada antrean ExoPlayer menggunakan moveMediaItem.
     */
    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val currentQueue = _playbackQueue.value.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices) return

        val moved = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, moved)
        _playbackQueue.value = currentQueue
        currentQueueIndex = currentQueue.indexOfFirst { it.id == _currentTrack.value?.id }
        persistPlaybackQueue(currentQueue, _playbackQueueTitle.value)
        syncExoPlayerUpcomingQueue()
    }

    /**
     * Memindahkan posisi lagu di antrean berdasarkan ID trek asal dan tujuan.
     */
    fun reorderQueueTracks(fromTrackId: String, toTrackId: String) {
        val currentQueue = _playbackQueue.value.toMutableList()
        val fromIndex = currentQueue.indexOfFirst { it.id == fromTrackId }
        val toIndex = currentQueue.indexOfFirst { it.id == toTrackId }

        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            val moved = currentQueue.removeAt(fromIndex)
            currentQueue.add(toIndex, moved)
            _playbackQueue.value = currentQueue
            currentQueueIndex = currentQueue.indexOfFirst { it.id == _currentTrack.value?.id }
            persistPlaybackQueue(currentQueue, _playbackQueueTitle.value)
            syncExoPlayerUpcomingQueue()
        }
    }

    /**
     * Menghapus trek dari antrean putar (melalui swipe gesture atau tombol hapus).
     * Menyinkronkan penghapusan ke antrean ExoPlayer via removeMediaItem.
     * Jika lagu yang dihapus sedang aktif berputar, otomatis melanjutkan pemutaran ke lagu berikutnya.
     */
    fun removeTrackFromQueue(trackId: String) {
        val currentQueue = _playbackQueue.value.toMutableList()
        val removeIndex = currentQueue.indexOfFirst { it.id == trackId }
        if (removeIndex < 0) return

        val isCurrentPlaying = _currentTrack.value?.id == trackId
        currentQueue.removeAt(removeIndex)
        _playbackQueue.value = currentQueue
        currentQueueIndex = currentQueue.indexOfFirst { it.id == _currentTrack.value?.id }
        persistPlaybackQueue(currentQueue, _playbackQueueTitle.value)

        // Jika pengguna menghapus lagu yang sedang aktif diputar, jalankan pemutaran lagu berikutnya secara otomatis
        if (isCurrentPlaying) {
            if (currentQueue.isNotEmpty()) {
                val nextTrack = if (removeIndex < currentQueue.size) {
                    currentQueue[removeIndex]
                } else {
                    currentQueue.first()
                }
                playTrack(nextTrack, currentQueue, _playbackQueueTitle.value)
            } else {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                youTubeAudioEngine.stop()
                _isPlaying.value = false
                _currentTrack.value = null
                updateMediaNotification()
            }
        } else {
            syncExoPlayerUpcomingQueue()
        }
    }

    private fun syncExoPlayerUpcomingQueue() {
        try {
            if (!isYouTubePlayback) {
                val curExoIdx = exoPlayer.currentMediaItemIndex
                if (exoPlayer.mediaItemCount > curExoIdx + 1) {
                    exoPlayer.removeMediaItems(curExoIdx + 1, exoPlayer.mediaItemCount)
                }
                stagePrefetchUpcomingTracks()
            }
        } catch (e: Exception) {
            Log.w("CRANKR_PLAYER", "Gagal syncExoPlayerUpcomingQueue: ${e.message}")
        }
    }

    fun clearQueue() {
        val current = _currentTrack.value
        _playbackQueue.value = if (current != null) listOf(current) else emptyList()
        currentQueueIndex = 0
        persistPlaybackQueue(_playbackQueue.value, _playbackQueueTitle.value)
    }

    // Quick Search on Home with 400ms Debounce and Sanitization
    fun searchQuickSongs(query: String) {
        _quickSearchQuery.value = query
        val sanitized = sanitizeQuery(query)
        quickSearchJob?.cancel()
        if (sanitized.isBlank()) {
            _searchQuickResults.value = emptyList()
            _isSearchingQuick.value = false
            return
        }
        quickSearchJob = viewModelScope.launch {
            _isSearchingQuick.value = true
            delay(400)
            val results = MusicStreamResolver.searchTracks(sanitized)
            val filtered = results.filter {
                !CrankrCatalogFilter.isBlacklisted(it.title, it.artist) &&
                !MusicStreamResolver.isCompilationOrBootleg(it.title, it.artist) && it.durationSeconds in 30..600
            }
            val enriched = filtered.map { t ->
                val local = downloadManager.getLocalFilePath(t.id)
                t.copy(localFilePath = local)
            }
            _searchQuickResults.value = enriched
            _isSearchingQuick.value = false
        }
    }

    fun clearQuickSearch() {
        _quickSearchQuery.value = ""
        _searchQuickResults.value = emptyList()
        _isSearchingQuick.value = false
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedGenre(genre: String) {
        _selectedGenre.value = genre
    }

    fun setOfflineOnlyMode(enabled: Boolean) {
        _isOfflineOnlyMode.value = enabled
    }

    fun downloadTrack(track: Track) {
        if (track.isDownloaded) return

        viewModelScope.launch {
            Toast.makeText(getApplication(), "Mulai mengunduh: ${track.title}", Toast.LENGTH_SHORT).show()
            _downloadProgressMap.update { it + (track.id to 0.05f) }
            val result = downloadManager.downloadTrack(track) { progress ->
                _downloadProgressMap.update { it + (track.id to progress) }
            }

            if (result.isSuccess) {
                val path = result.getOrNull()
                val downloadedTrack = track.copy(localFilePath = path)
                _allTracks.update { list ->
                    if (list.any { it.id == track.id }) {
                        list.map { if (it.id == track.id) downloadedTrack else it }
                    } else {
                        list + downloadedTrack
                    }
                }
                refreshTracks()
                // Update currentTrack if it was the one being played
                if (_currentTrack.value?.id == track.id) {
                    _currentTrack.value = downloadedTrack
                }
                Toast.makeText(getApplication(), "Berhasil mengunduh \"${track.title}\" untuk offline!", Toast.LENGTH_SHORT).show()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Gagal mengunduh lagu"
                Toast.makeText(getApplication(), "Gagal mengunduh: $err", Toast.LENGTH_SHORT).show()
            }
            _downloadProgressMap.update { it - track.id }
        }
    }

    fun deleteDownloadedTrack(track: Track) {
        val success = downloadManager.deleteDownloadedTrack(track.id)
        refreshTracks()
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = _currentTrack.value?.copy(localFilePath = null)
        }
        if (success) {
            Toast.makeText(getApplication(), "Unduhan \"${track.title}\" dihapus", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteAllDownloads() {
        val success = downloadManager.deleteAllDownloads()
        refreshTracks()
        _storageUsage.value = downloadManager.getOfflineStorageSizeFormatted()
        if (success) {
            Toast.makeText(getApplication(), "Semua lagu unduhan berhasil dibersihkan", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplication(), "Gagal membersihkan unduhan", Toast.LENGTH_SHORT).show()
        }
    }

    private var searchJob: Job? = null

    fun setExploreSourceFilter(filter: String) {
        val clean = filter.trim()
        if (_exploreSourceFilter.value == clean) return
        _exploreSourceFilter.value = clean
        val q = _exploreSearchQuery.value.trim()
        if (q.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                _isSearchingExplore.value = true
                _exploreSearchError.value = null
                val sanitized = sanitizeQuery(q)
                if (clean == "⚡ Bandcamp") {
                    try {
                        val result = searchRepository.searchBandcampCatalog(sanitized, limit = 25)
                        val enriched = result.tracks.map { t ->
                            val local = downloadManager.getLocalFilePath(t.id)
                            t.copy(localFilePath = local)
                        }
                        _exploreMatchedArtist.value = result.matchedArtist
                        _exploreSearchResults.value = enriched
                        _isSearchingExplore.value = false
                        _exploreSearchError.value = if (enriched.isEmpty() && result.matchedArtist == null) {
                            "Tidak ada rilisan Bandcamp yang cocok dengan '$sanitized'"
                        } else null
                    } catch (e: Exception) {
                        _isSearchingExplore.value = false
                        _exploreSearchError.value = e.localizedMessage ?: "Gagal mencari katalog Bandcamp"
                    }
                } else {
                    searchExploreSongs(sanitized)
                }
            }
        }
    }

    // Search & Explore Screen Methods with 350ms Debounce and Sanitization
    fun searchExploreSongs(query: String) {
        _exploreSearchQuery.value = query
        _exploreSearchError.value = null
        val sanitized = sanitizeQuery(query)
        if (sanitized.isBlank()) {
            searchJob?.cancel()
            _exploreSearchResults.value = emptyList()
            _exploreMatchedArtist.value = null
            _isSearchingExplore.value = false
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearchingExplore.value = true
            delay(350)

            if (_exploreSourceFilter.value == "⚡ Bandcamp") {
                try {
                    val result = searchRepository.searchBandcampCatalog(sanitized, limit = 25)
                    val enriched = result.tracks.map { t ->
                        val local = downloadManager.getLocalFilePath(t.id)
                        t.copy(localFilePath = local)
                    }
                    _exploreMatchedArtist.value = result.matchedArtist
                    _exploreSearchResults.value = enriched
                    _isSearchingExplore.value = false
                    _exploreSearchError.value = if (enriched.isEmpty() && result.matchedArtist == null) {
                        "Tidak ada rilisan Bandcamp yang cocok dengan '$sanitized'"
                    } else null
                } catch (e: Exception) {
                    _isSearchingExplore.value = false
                    _exploreSearchError.value = e.localizedMessage ?: "Gagal mencari katalog Bandcamp"
                }
                return@launch
            }

            val cleanLower = sanitized.lowercase()
            val isGenreQuery = listOf(
                "pop punk", "metalcore", "indie rock", "lo-fi", "anime", "j-rock",
                "pop indonesia", "hip-hop", "skate punk", "grunge", "rock", "metal", "indie", "pop"
            ).any { cleanLower.contains(it) || it.contains(cleanLower) }

            try {
                if (isGenreQuery) {
                    var genreTracks = try {
                        genreMoodRepository.getCategoryTracks(sanitized, limit = 20)
                    } catch (_: Exception) {
                        emptyList()
                    }

                    // Fallback 1: Jika Spotify Category kosong, cari track via search catalog Spotify
                    if (genreTracks.isEmpty()) {
                        try {
                            val catRes = searchRepository.searchCatalog(sanitized, limit = 20)
                            if (catRes.tracks.isNotEmpty()) {
                                genreTracks = catRes.tracks
                            }
                        } catch (_: Exception) {}
                    }

                    // Fallback 2: Jika masih kosong, ambil dari katalog lagu lokal/aktif di aplikasi
                    if (genreTracks.isEmpty()) {
                        val localPool = _allTracks.value
                        val matched = localPool.filter { t ->
                            val g = t.genre.lowercase()
                            val title = t.title.lowercase()
                            val artist = t.artist.lowercase()
                            cleanLower.split(" ", "&", "-").any { kw ->
                                kw.isNotBlank() && (g.contains(kw) || title.contains(kw) || artist.contains(kw))
                            }
                        }
                        if (matched.isNotEmpty()) {
                            genreTracks = matched.take(20)
                        }
                    }

                    // Fallback 3: Cari via YouTube Music Repository
                    if (genreTracks.isEmpty()) {
                        try {
                            val ytTracks = youTubeMusicRepository.searchSongs(sanitized, limit = 20)
                            if (ytTracks.isNotEmpty()) {
                                genreTracks = ytTracks
                            }
                        } catch (_: Exception) {}
                    }

                    val enriched = genreTracks.map { t ->
                        val local = downloadManager.getLocalFilePath(t.id)
                        t.copy(localFilePath = local)
                    }
                    _exploreMatchedArtist.value = null
                    _exploreSearchResults.value = enriched
                    _isSearchingExplore.value = false
                    _exploreSearchError.value = if (enriched.isEmpty()) "Lagu untuk genre ini sedang tidak tersedia" else null
                    return@launch
                }

                // Query Spotify Official Catalog (type=track,artist) with strict sanitization
                var result = searchRepository.searchCatalog(sanitized, limit = 20)
                if (result.tracks.isEmpty() && result.matchedArtist == null) {
                    val lyricsFallback = searchRepository.searchByLyrics(sanitized, limit = 20)
                    if (lyricsFallback.tracks.isNotEmpty()) {
                        result = lyricsFallback
                    }
                }
                val enriched = result.tracks.map { t ->
                    val local = downloadManager.getLocalFilePath(t.id)
                    t.copy(localFilePath = local)
                }

                _exploreMatchedArtist.value = result.matchedArtist
                _exploreSearchResults.value = enriched
                _isSearchingExplore.value = false
                _exploreSearchError.value = if (enriched.isEmpty() && result.matchedArtist == null) "Lagu tidak ditemukan" else null
            } catch (e: Exception) {
                // Fallback pencarian katalog lokal jika terjadi kegagalan jaringan eksternal
                val fallbackMatches = _allTracks.value.filter { t ->
                    val g = t.genre.lowercase()
                    val title = t.title.lowercase()
                    val artist = t.artist.lowercase()
                    cleanLower.split(" ").any { kw -> kw.isNotBlank() && (g.contains(kw) || title.contains(kw) || artist.contains(kw)) }
                }
                if (fallbackMatches.isNotEmpty()) {
                    _exploreSearchResults.value = fallbackMatches.take(20)
                    _exploreMatchedArtist.value = null
                    _exploreSearchError.value = null
                } else {
                    _exploreSearchError.value = "Tidak ada hasil yang ditemukan. Coba kata kunci lain."
                }
                _isSearchingExplore.value = false
            }
        }
    }

    /**
     * Menjalankan pencarian lagu langsung berdasarkan penggalan atau potongan lirik lagu.
     */
    fun searchByLyrics(lyricsSnippet: String) {
        val sanitized = sanitizeQuery(lyricsSnippet)
        if (sanitized.isBlank()) return
        saveSearchQuery(sanitized)
        _exploreSearchQuery.value = sanitized
        _exploreSearchError.value = null
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearchingExplore.value = true
            try {
                val result = searchRepository.searchByLyrics(sanitized, limit = 20)
                val enriched = result.tracks.map { t ->
                    val local = downloadManager.getLocalFilePath(t.id)
                    t.copy(localFilePath = local)
                }
                _exploreMatchedArtist.value = null
                _exploreSearchResults.value = enriched
                _isSearchingExplore.value = false
                _exploreSearchError.value = if (enriched.isEmpty()) "Tidak ada lagu yang cocok dengan potongan lirik ini" else null
            } catch (e: Exception) {
                _isSearchingExplore.value = false
                _exploreSearchError.value = e.localizedMessage ?: "Gagal mencari berdasarkan lirik"
            }
        }
    }

    fun submitSearch(query: String) {
        val sanitized = sanitizeQuery(query)
        if (sanitized.isBlank()) return
        saveSearchQuery(sanitized)
        searchExploreSongs(sanitized)
    }

    fun retryExploreSearch() {
        val q = _exploreSearchQuery.value.trim()
        if (q.isNotBlank()) {
            submitSearch(q)
        }
    }

    fun saveSearchQuery(query: String) {
        val sanitized = sanitizeQuery(query)
        if (sanitized.isBlank()) return
        viewModelScope.launch {
            _searchHistory.value = searchHistoryManager.addSearchQuery(sanitized)
        }
    }

    fun onSearchTrackClicked(track: Track, searchList: List<Track>) {
        val currentQuery = _exploreSearchQuery.value.trim()
        val queryToSave = if (currentQuery.isNotBlank()) currentQuery else track.title.trim()
        if (queryToSave.isNotBlank()) {
            saveSearchQuery(queryToSave)
        }
        playTrack(track, searchList, "Pencarian: ${queryToSave.ifBlank { "Jelajah" }}")
    }

    fun clearExploreSearch() {
        searchJob?.cancel()
        _exploreSearchQuery.value = ""
        _exploreSearchResults.value = emptyList()
        _exploreMatchedArtist.value = null
        _exploreSearchError.value = null
        _isSearchingExplore.value = false
    }

    fun removeSearchHistoryItem(query: String) {
        viewModelScope.launch {
            _searchHistory.value = searchHistoryManager.removeSearchQuery(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistoryManager.clearSearchHistory()
            _searchHistory.value = emptyList()
        }
    }

    fun toggleAutoplay() {
        _isAutoplayEnabled.update { !it }
        val msg = if (_isAutoplayEnabled.value) "Radio Otomatis (Autoplay) aktif" else "Radio Otomatis dimatikan"
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    private var isTriggeringAutoplay = false

    fun triggerAutoplay(finishedTrack: Track?) {
        if (isTriggeringAutoplay || finishedTrack == null) return
        isTriggeringAutoplay = true
        viewModelScope.launch {
            try {
                Toast.makeText(getApplication(), "Radio Otomatis: Mencari lagu serupa...", Toast.LENGTH_SHORT).show()
                val query = "${finishedTrack.artist} ${finishedTrack.title} radio"
                val recommended = MusicStreamResolver.searchTracks(query)
                    .filter { it.id != finishedTrack.id && _playbackQueue.value.none { q -> q.id == it.id } }
                    .take(8)

                if (recommended.isNotEmpty()) {
                    val enriched = recommended.map { t ->
                        val local = downloadManager.getLocalFilePath(t.id)
                        t.copy(localFilePath = local)
                    }
                    val updatedQueue = _playbackQueue.value + enriched
                    _playbackQueue.value = updatedQueue
                    _playbackQueueTitle.value = "Radio Otomatis (${finishedTrack.artist})"
                    playTrack(enriched.first(), updatedQueue, "Radio Otomatis (${finishedTrack.artist})")
                    Toast.makeText(getApplication(), "Memutar: ${enriched.first().title}", Toast.LENGTH_SHORT).show()
                } else {
                    val pool = _allTracks.value.filter { it.id != finishedTrack.id }
                    if (pool.isNotEmpty()) {
                        val randomTrack = pool.random()
                        playTrack(randomTrack, pool, "Radio Otomatis (Katalog)")
                    } else {
                        _isPlaying.value = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val pool = _allTracks.value.filter { it.id != finishedTrack.id }
                if (pool.isNotEmpty()) {
                    val randomTrack = pool.random()
                    playTrack(randomTrack, pool, "Radio Otomatis")
                }
            } finally {
                isTriggeringAutoplay = false
            }
        }
    }

    fun getActiveTrackList(): List<Track> {
        val rawList = when {
            _playbackQueue.value.isNotEmpty() -> _playbackQueue.value
            _selectedPlaylist.value != null && _selectedPlaylistTracks.value.isNotEmpty() -> _selectedPlaylistTracks.value
            _isOfflineOnlyMode.value -> _allTracks.value.filter { it.isDownloaded }
            else -> _allTracks.value
        }
        return MetadataSanitizer.sanitizeTracks(rawList)
    }

    fun createPlaylist(name: String, description: String = ""): SpotifyPlaylist {
        val id = "custom_${System.currentTimeMillis()}"
        val newPl = SpotifyPlaylist(
            id = id,
            name = name.trim().ifEmpty { "Playlist Baru" },
            description = description.trim(),
            imageUrl = null,
            trackCount = 0,
            ownerName = "Saya",
            isSynced = false
        )
        _spotifyPlaylists.update { listOf(newPl) + it }
        playlistTracksMap[id] = mutableListOf()
        persistPlaylists()
        Toast.makeText(getApplication(), "Playlist \"${newPl.name}\" dibuat", Toast.LENGTH_SHORT).show()
        return newPl
    }

    fun editPlaylist(playlistId: String, newName: String, newDescription: String = "", newImageUrl: String? = null, newSourceUrl: String? = null) {
        val cleanName = newName.trim().ifEmpty { "Playlist" }
        _spotifyPlaylists.update { list ->
            list.map { pl ->
                if (pl.id == playlistId) {
                    pl.copy(
                        name = cleanName,
                        description = newDescription.trim(),
                        imageUrl = if (newImageUrl != null) newImageUrl.ifEmpty { null } else pl.imageUrl,
                        sourceUrl = if (newSourceUrl != null) newSourceUrl.ifEmpty { null } else pl.sourceUrl
                    )
                } else pl
            }
        }
        if (_selectedPlaylist.value?.id == playlistId) {
            _selectedPlaylist.update { it?.copy(
                name = cleanName,
                description = newDescription.trim(),
                imageUrl = if (newImageUrl != null) newImageUrl.ifEmpty { null } else it.imageUrl,
                sourceUrl = if (newSourceUrl != null) newSourceUrl.ifEmpty { null } else it.sourceUrl
            ) }
        }
        persistPlaylists()
        Toast.makeText(getApplication(), "Playlist diperbarui", Toast.LENGTH_SHORT).show()
    }

    fun setPlaylistSourceUrl(playlistId: String, sourceUrl: String) {
        val cleanUrl = sourceUrl.trim()
        _spotifyPlaylists.update { list ->
            list.map { pl ->
                if (pl.id == playlistId) pl.copy(sourceUrl = cleanUrl) else pl
            }
        }
        if (_selectedPlaylist.value?.id == playlistId) {
            _selectedPlaylist.update { it?.copy(sourceUrl = cleanUrl) }
        }
        persistPlaylists()
        Toast.makeText(getApplication(), "Tautan sumber disimpan", Toast.LENGTH_SHORT).show()
    }

    fun syncPlaylist(playlistId: String) {
        val playlist = _spotifyPlaylists.value.firstOrNull { it.id == playlistId } ?: return
        val url = playlist.sourceUrl ?: if (playlist.id.length in 15..35 && !playlist.id.startsWith("custom_") && !playlist.id.startsWith("ytm_")) {
            "https://open.spotify.com/playlist/${playlist.id}"
        } else null

        viewModelScope.launch {
            _syncingPlaylistIds.update { it + playlistId }
            AudioStreamResolver.clearStreamCache()
            val currentTracks = playlistTracksMap.getOrPut(playlistId) { mutableListOf() }
            val oldCovers = currentTracks.associate { it.id to it.coverUrl }

            if (!url.isNullOrBlank()) {
                val isYouTube = url.contains("youtube.com") || url.contains("youtu.be") || url.contains("music.youtube.com")
                val result = if (isYouTube) {
                    youTubeMusicService.fetchPlaylistFromYouTubeUrl(url)
                } else {
                    spotifyApiService.fetchPublicPlaylistFromUrl(url, bypassCache = true)
                }

                if (result.isSuccess) {
                    val pair = result.getOrNull()
                    if (pair != null) {
                        val (freshPlaylist, freshTracks) = pair

                        // Perbarui trek yang sudah ada dengan data pencocokan akurat dari server jika videoId atau cover-nya berbeda
                        for (idx in currentTracks.indices) {
                            val curr = currentTracks[idx]
                            val matchingFresh = freshTracks.firstOrNull { fresh ->
                                curr.id == fresh.id ||
                                (curr.title.trim().equals(fresh.title.trim(), ignoreCase = true) &&
                                 curr.artist.trim().equals(fresh.artist.trim(), ignoreCase = true))
                            }
                            if (matchingFresh != null) {
                                currentTracks[idx] = curr.copy(
                                    coverUrl = if (matchingFresh.coverUrl.isNotBlank() && !matchingFresh.coverUrl.contains("unsplash")) matchingFresh.coverUrl else curr.coverUrl,
                                    youtubeVideoId = matchingFresh.youtubeVideoId ?: curr.youtubeVideoId,
                                    durationSeconds = if (matchingFresh.durationSeconds > 0) matchingFresh.durationSeconds else curr.durationSeconds
                                )
                            }
                        }

                        // Tambah lagu baru yang belum ada di database lokal
                        val newTracks = freshTracks.filter { fresh ->
                            currentTracks.none { curr ->
                                curr.id == fresh.id ||
                                (curr.title.trim().equals(fresh.title.trim(), ignoreCase = true) &&
                                 curr.artist.trim().equals(fresh.artist.trim(), ignoreCase = true))
                            }
                        }.mapIndexed { idx, t ->
                            val local = downloadManager.getLocalFilePath(t.id)
                            t.copy(
                                localFilePath = local,
                                addedAt = System.currentTimeMillis() - (freshTracks.size - idx) * 1000L
                            )
                        }

                        if (newTracks.isNotEmpty()) {
                            currentTracks.addAll(newTracks)
                        }

                        val updatedPlaylist = playlist.copy(
                            trackCount = currentTracks.size,
                            sourceUrl = url,
                            imageUrl = if (!freshPlaylist.imageUrl.isNullOrBlank() && (playlist.imageUrl.isNullOrBlank() || playlist.imageUrl.orEmpty().contains("unsplash"))) freshPlaylist.imageUrl else playlist.imageUrl
                        )

                        _spotifyPlaylists.update { list ->
                            list.map { if (it.id == playlistId) updatedPlaylist else it }
                        }
                        if (_selectedPlaylist.value?.id == playlistId) {
                            _selectedPlaylist.value = updatedPlaylist
                        }
                        if (newTracks.isNotEmpty()) {
                            _allTracks.value = (_allTracks.value.filter { existing -> newTracks.none { it.id == existing.id } } + newTracks)
                        }
                    }
                }
            }

            // Jalankan perbaikan cover massal secara paralel menggunakan TrackCoverResolver (Spotify oEmbed + iTunes + YouTube)
            val resolvedTracks = TrackCoverResolver.bulkResolvePlaylistCovers(
                tracks = currentTracks.toList(),
                playlistCoverUrl = playlist.imageUrl,
                playlistName = playlist.name
            )

            var updatedCoversCount = 0
            for (i in resolvedTracks.indices) {
                val t = resolvedTracks[i]
                val prev = oldCovers[t.id]
                if (prev != null && prev != t.coverUrl) {
                    updatedCoversCount++
                }
            }

            playlistTracksMap[playlistId] = resolvedTracks.toMutableList()

            withContext(Dispatchers.Main) {
                try {
                    Coil.imageLoader(getApplication()).memoryCache?.clear()
                    Coil.imageLoader(getApplication()).diskCache?.clear()
                } catch (_: Exception) {}

                if (_selectedPlaylist.value?.id == playlistId) {
                    _selectedPlaylistTracks.value = resolvedTracks
                }

                _currentTrack.value?.let { current ->
                    val matching = resolvedTracks.firstOrNull { it.id == current.id }
                    if (matching != null && matching.coverUrl != current.coverUrl) {
                        _currentTrack.value = current.copy(
                            coverUrl = matching.coverUrl,
                            album = matching.album
                        )
                    }
                }
            }

            // Simpan relasi trek secara atomik dan terikat agar audioSourceId tidak tertukar
            val relations = resolvedTracks.map { com.example.spotifyclone.data.repository.PlaylistTrackRelation.fromTrack(it) }
            playlistRepository.batchInsertPlaylistTracks(playlistId, relations)
            persistPlaylists()

            val msg = if (updatedCoversCount > 0) {
                "Sinkronisasi berhasil! $updatedCoversCount cover lagu diperbarui ke cover album asli."
            } else {
                "Sinkronisasi selesai! Seluruh cover lagu mutakhir."
            }
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()

            _syncingPlaylistIds.update { it - playlistId }
        }
    }

    /**
     * Memperbaiki cover lagu secara otomatis di background saat playlist dibuka.
     * Menggunakan Spotify oEmbed, Apple iTunes 600x600, dan YouTube thumbnail fallback.
     */
    fun autoRepairPlaylistCovers(
        playlistId: String,
        playlistName: String?,
        currentTracks: List<Track>,
        playlistCoverUrl: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = TrackCoverResolver.bulkResolvePlaylistCovers(
                tracks = currentTracks,
                playlistCoverUrl = playlistCoverUrl,
                playlistName = playlistName
            )
            val anyChanged = resolved.zip(currentTracks).any { (newT, oldT) ->
                newT.coverUrl != oldT.coverUrl || newT.album != oldT.album
            }
            if (anyChanged) {
                playlistTracksMap[playlistId] = resolved.toMutableList()
                withContext(Dispatchers.Main) {
                    try {
                        Coil.imageLoader(getApplication()).memoryCache?.clear()
                        Coil.imageLoader(getApplication()).diskCache?.clear()
                    } catch (_: Exception) {}

                    if (_selectedPlaylist.value?.id == playlistId) {
                        _selectedPlaylistTracks.value = resolved
                    }

                    _currentTrack.value?.let { current ->
                        val matching = resolved.firstOrNull { it.id == current.id }
                        if (matching != null && matching.coverUrl != current.coverUrl) {
                            _currentTrack.value = current.copy(
                                coverUrl = matching.coverUrl,
                                album = matching.album
                            )
                        }
                    }
                }
                persistPlaylists()
            }
        }
    }

    fun autoRepairUnplayableTracks(playlistId: String, tracks: List<Track>) {
        val unplayable = tracks.filterNot { it.isPlayable }
        if (unplayable.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val repairedMap = mutableMapOf<String, Track>()
            for (t in unplayable) {
                val vid = AudioStreamResolver.resolveYouTubeVideoId(t.title, t.artist)
                    ?: AudioStreamResolver.resolveYouTubeVideoId(t)
                if (!vid.isNullOrBlank()) {
                    repairedMap[t.id] = t.copy(
                        id = "yt_$vid",
                        youtubeVideoId = vid,
                        streamUrl = "https://music.youtube.com/watch?v=$vid"
                    )
                }
            }
            if (repairedMap.isNotEmpty()) {
                val updated = tracks.map { repairedMap[it.id] ?: it }
                playlistTracksMap[playlistId] = updated.toMutableList()
                withContext(Dispatchers.Main) {
                    if (_selectedPlaylist.value?.id == playlistId) {
                        _selectedPlaylistTracks.value = updated
                    }
                }
                persistPlaylists()
            }
        }
    }

    fun autoRepairStaleCovers() {
        viewModelScope.launch(Dispatchers.IO) {
            var anyChanged = false
            for (pl in _spotifyPlaylists.value) {
                val tracks = playlistTracksMap[pl.id] ?: continue
                val resolved = TrackCoverResolver.bulkResolvePlaylistCovers(
                    tracks = tracks,
                    playlistCoverUrl = pl.imageUrl,
                    playlistName = pl.name
                )
                val plChanged = resolved.zip(tracks).any { (newT, oldT) ->
                    newT.coverUrl != oldT.coverUrl || newT.album != oldT.album
                }
                if (plChanged) {
                    playlistTracksMap[pl.id] = resolved.toMutableList()
                    anyChanged = true
                }
            }
            if (anyChanged) {
                withContext(Dispatchers.Main) {
                    try {
                        Coil.imageLoader(getApplication()).memoryCache?.clear()
                        Coil.imageLoader(getApplication()).diskCache?.clear()
                    } catch (_: Exception) {}
                    _selectedPlaylist.value?.let { currentPl ->
                        playlistTracksMap[currentPl.id]?.let {
                            _selectedPlaylistTracks.value = it.toList()
                        }
                    }
                }
                persistPlaylists()
            }
        }
    }

    fun updatePlaylistCover(playlistId: String, newImageUrl: String?) {
        _spotifyPlaylists.update { list ->
            list.map { pl ->
                if (pl.id == playlistId) {
                    pl.copy(imageUrl = newImageUrl)
                } else pl
            }
        }
        if (_selectedPlaylist.value?.id == playlistId) {
            _selectedPlaylist.update { it?.copy(imageUrl = newImageUrl) }
        }
        persistPlaylists()
        Toast.makeText(getApplication(), "Cover playlist diperbarui", Toast.LENGTH_SHORT).show()
    }

    fun savePlaylistCoverFromUri(playlistId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val coversDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
                val targetFile = File(coversDir, "cover_${playlistId}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val localPath = targetFile.absolutePath
                withContext(Dispatchers.Main) {
                    updatePlaylistCover(playlistId, localPath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Gagal menyimpan gambar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        val plName = _spotifyPlaylists.value.firstOrNull { it.id == playlistId }?.name ?: "Playlist"
        _spotifyPlaylists.update { list -> list.filter { it.id != playlistId } }
        playlistTracksMap.remove(playlistId)
        if (_selectedPlaylist.value?.id == playlistId) {
            closePlaylistDetail()
        }
        persistPlaylists()
        Toast.makeText(getApplication(), "\"$plName\" berhasil dihapus", Toast.LENGTH_SHORT).show()
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        val list = playlistTracksMap.getOrPut(playlistId) { mutableListOf() }
        if (list.none { it.id == track.id }) {
            val stampedTrack = if (track.addedAt > 0L) track else track.copy(addedAt = System.currentTimeMillis())
            list.add(stampedTrack)
            _spotifyPlaylists.update { playlists ->
                playlists.map { pl ->
                    if (pl.id == playlistId) {
                        pl.copy(
                            trackCount = list.size,
                            imageUrl = pl.imageUrl ?: track.coverUrl
                        )
                    } else pl
                }
            }
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylistTracks.value = list.toList()
            }
            persistPlaylists()
            Toast.makeText(getApplication(), "\"${track.title}\" ditambahkan ke playlist", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplication(), "Lagu sudah ada di playlist ini", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val list = playlistTracksMap[playlistId] ?: return
        list.removeAll { it.id == trackId }
        _spotifyPlaylists.update { playlists ->
            playlists.map { pl ->
                if (pl.id == playlistId) {
                    val newCover = list.firstOrNull()?.coverUrl ?: pl.imageUrl
                    pl.copy(trackCount = list.size, imageUrl = newCover)
                } else pl
            }
        }
        if (_selectedPlaylist.value?.id == playlistId) {
            _selectedPlaylistTracks.value = list.toList()
        }
        persistPlaylists()
        Toast.makeText(getApplication(), "Lagu dihapus dari playlist", Toast.LENGTH_SHORT).show()
    }

    private var importPlaylistJob: Job? = null

    fun importPlaylistFromUrl(url: String) {
        importPlaylistJob?.cancel()
        importPlaylistJob = viewModelScope.launch {
            _isSyncingSpotify.value = true
            _playlistImportProgress.value = PlaylistImportProgress("Memuat...", 0, 0)
            val isYouTube = url.contains("youtube.com") || url.contains("youtu.be") || url.contains("music.youtube.com")
            val result = if (isYouTube) {
                youTubeMusicService.fetchPlaylistFromYouTubeUrl(url)
            } else {
                spotifyApiService.fetchPublicPlaylistFromUrl(url) { name, current, total ->
                    _playlistImportProgress.value = PlaylistImportProgress(name, current, total)
                }
            }

            if (result.isSuccess) {
                val pair = result.getOrNull()
                if (pair != null) {
                    val (rawPlaylist, tracks) = pair
                    val playlist = rawPlaylist.copy(sourceUrl = url)
                    val enrichedTracks = tracks.map { t ->
                        val local = downloadManager.getLocalFilePath(t.id)
                        t.copy(localFilePath = local)
                    }
                    playlistTracksMap[playlist.id] = enrichedTracks.toMutableList()
                    _spotifyPlaylists.value = listOf(playlist) + _spotifyPlaylists.value.filter { it.id != playlist.id }
                    _selectedPlaylist.value = playlist
                    _selectedPlaylistTracks.value = enrichedTracks
                    _allTracks.value = (_allTracks.value.filter { existing -> enrichedTracks.none { it.id == existing.id } } + enrichedTracks)
                    persistPlaylists()

                    var finalTracks = enrichedTracks
                    var matched = finalTracks.filter { it.isPlayable }
                    var unmatched = finalTracks.filterNot { it.isPlayable }

                    // Resolusi putaran kedua dengan AudioStreamResolver untuk trek yang belum teridentifikasi
                    if (unmatched.isNotEmpty()) {
                        val repairedMap = mutableMapOf<String, Track>()
                        for (t in unmatched) {
                            val vid = AudioStreamResolver.resolveYouTubeVideoId(t.title, t.artist)
                                ?: AudioStreamResolver.resolveYouTubeVideoId(t)
                            if (!vid.isNullOrBlank()) {
                                repairedMap[t.id] = t.copy(
                                    id = "yt_$vid",
                                    youtubeVideoId = vid,
                                    streamUrl = "https://music.youtube.com/watch?v=$vid"
                                )
                            }
                        }

                        if (repairedMap.isNotEmpty()) {
                            finalTracks = finalTracks.map { oldTrack -> repairedMap[oldTrack.id] ?: oldTrack }
                            playlistTracksMap[playlist.id] = finalTracks.toMutableList()
                            _selectedPlaylistTracks.value = finalTracks
                            _allTracks.value = (_allTracks.value.filter { existing -> finalTracks.none { it.id == existing.id } } + finalTracks)
                            persistPlaylists()

                            matched = finalTracks.filter { it.isPlayable }
                            unmatched = finalTracks.filterNot { it.isPlayable }
                        }
                    }

                    if (unmatched.isNotEmpty()) {
                        _importSummary.value = ImportSummary(
                            playlist = playlist,
                            matchedTracks = matched,
                            unmatchedTracks = unmatched
                        )
                    } else {
                        Toast.makeText(
                            getApplication(),
                            "Berhasil mengimpor ${finalTracks.size} lagu: \"${playlist.name}\"",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                Toast.makeText(
                    getApplication(),
                    "Gagal impor: Pastikan tautan playlist valid",
                    Toast.LENGTH_LONG
                ).show()
            }
            _playlistImportProgress.value = null
            _isSyncingSpotify.value = false
        }
    }

    fun confirmImportFilterUnmatched(playlistId: String) {
        val current = playlistTracksMap[playlistId] ?: return
        val filtered = current.filter { it.isPlayable }.toMutableList()
        playlistTracksMap[playlistId] = filtered
        _spotifyPlaylists.update { list ->
            list.map { if (it.id == playlistId) it.copy(trackCount = filtered.size) else it }
        }
        if (_selectedPlaylist.value?.id == playlistId) {
            _selectedPlaylist.value = _selectedPlaylist.value?.copy(trackCount = filtered.size)
            _selectedPlaylistTracks.value = filtered
        }
        persistPlaylists()
        _importSummary.value = null
        Toast.makeText(getApplication(), "Lagu yang tidak ditemukan berhasil dihapus", Toast.LENGTH_SHORT).show()
    }

    fun dismissImportSummary() {
        _importSummary.value = null
    }

    fun purgeUnplayableTracksFromPlaylist(playlistId: String) {
        val current = playlistTracksMap[playlistId] ?: return
        val beforeSize = current.size
        val filtered = current.filter { it.isPlayable }.toMutableList()
        val removedCount = beforeSize - filtered.size
        if (removedCount <= 0) {
            Toast.makeText(getApplication(), "Semua lagu dalam playlist ini tersedia", Toast.LENGTH_SHORT).show()
            return
        }
        playlistTracksMap[playlistId] = filtered
        _spotifyPlaylists.update { list ->
            list.map { if (it.id == playlistId) it.copy(trackCount = filtered.size) else it }
        }
        if (_selectedPlaylist.value?.id == playlistId) {
            _selectedPlaylist.value = _selectedPlaylist.value?.copy(trackCount = filtered.size)
            _selectedPlaylistTracks.value = filtered
        }
        persistPlaylists()
        Toast.makeText(getApplication(), "$removedCount lagu tidak tersedia berhasil dihapus", Toast.LENGTH_LONG).show()
    }

    fun selectPlaylist(playlist: SpotifyPlaylist) {
        // Kosongkan state rekomendasi setiap kali pengguna berpindah membuka playlist lain
        _playlistRecommendations.value = emptyList()
        _dominantGenre.value = ""
        recommendationJob?.cancel()

        if (playlist.id == "liked_songs") {
            _selectedPlaylist.value = playlist.copy(trackCount = _likedTracks.value.size)
            _selectedPlaylistTracks.value = _likedTracks.value
            preFetchTopTracks(_likedTracks.value)
            loadRecommendationsForPlaylist(playlist, _likedTracks.value)
            return
        }

        _selectedPlaylist.value = playlist

        val cached = playlistTracksMap[playlist.id]
        if (!cached.isNullOrEmpty()) {
            _selectedPlaylistTracks.value = cached.toList()
            preFetchTopTracks(cached)
            loadRecommendationsForPlaylist(playlist, cached)
            autoRepairPlaylistCovers(playlist.id, playlist.name, cached, playlist.imageUrl)
            autoRepairUnplayableTracks(playlist.id, cached)
            return
        }

        if (playlist.id.startsWith("ytm_")) {
            viewModelScope.launch {
                val result = youTubeMusicService.fetchPlaylistTracks(playlist.id)
                if (result.isSuccess) {
                    val tracks = result.getOrDefault(emptyList()).map { t ->
                        val local = downloadManager.getLocalFilePath(t.id)
                        t.copy(localFilePath = local)
                    }
                    playlistTracksMap[playlist.id] = tracks.toMutableList()
                    _selectedPlaylistTracks.value = tracks
                    preFetchTopTracks(tracks)
                    persistPlaylists()
                    loadRecommendationsForPlaylist(playlist, tracks)
                    autoRepairPlaylistCovers(playlist.id, playlist.name, tracks, playlist.imageUrl)
                    autoRepairUnplayableTracks(playlist.id, tracks)
                }
            }
            return
        }

        // Muat murni dari database lokal (PlaylistStorageManager) tanpa pernah menghubungi server Spotify
        viewModelScope.launch {
            val localPlaylists = playlistStorageManager.loadPlaylists()
            val found = localPlaylists.firstOrNull { it.first.id == playlist.id }
            if (found != null && found.second.isNotEmpty()) {
                val tracks = found.second.map { t ->
                    val local = downloadManager.getLocalFilePath(t.id)
                    t.copy(localFilePath = local)
                }
                playlistTracksMap[playlist.id] = tracks.toMutableList()
                _selectedPlaylistTracks.value = tracks
                preFetchTopTracks(tracks)
                loadRecommendationsForPlaylist(playlist, tracks)
            }
        }
    }

    fun closePlaylistDetail() {
        _selectedPlaylist.value = null
        _selectedPlaylistTracks.value = emptyList()
        _playlistRecommendations.value = emptyList()
        _dominantGenre.value = ""
        recommendationJob?.cancel()
    }

    fun loadArtist(artistId: String, artistName: String? = null) {
        val name = artistName.orEmpty().ifBlank { "Artis" }
        selectArtist(name, explicitArtistId = artistId)
    }

    fun selectArtist(artistName: String, explicitArtistId: String? = null) {
        val cleanName = artistRepository.canonicalizeArtistName(artistName)
        if (cleanName.isBlank()) return
        val validExplicitId = if (explicitArtistId?.startsWith("bc_") == true || artistRepository.isValidSpotifyArtistId(explicitArtistId)) explicitArtistId?.trim() else null

        // Reset state jika musisi berbeda agar data artis lama tidak tertinggal di layar
        val isDifferentArtist = _selectedArtistId.value != validExplicitId || !_selectedArtistName.value.equals(cleanName, ignoreCase = true)
        if (!isDifferentArtist && _artistDiscography.value.isNotEmpty()) {
            return
        }

        // Batalkan pemanggilan coroutine sebelumnya
        artistJob?.cancel()

        if (isDifferentArtist) {
            _selectedArtistName.value = cleanName
            _selectedArtistId.value = validExplicitId
            _artistOfficialPhoto.value = null

            val seedTracks = if (validExplicitId?.startsWith("bc_") == true) {
                artistRepository.getCachedTopTracks(validExplicitId)
            } else {
                validExplicitId?.let { artistRepository.getCachedTopTracks(it) } ?: artistRepository.getCachedTopTracks(cleanName)
            }
            val seedAlbums = if (validExplicitId?.startsWith("bc_") == true) {
                artistRepository.getCachedAlbums(validExplicitId)
            } else {
                validExplicitId?.let { artistRepository.getCachedAlbums(it) } ?: artistRepository.getCachedAlbums(cleanName)
            }

            _artistTracks.value = seedTracks ?: emptyList()
            if (!seedTracks.isNullOrEmpty()) {
                preFetchTopTracks(seedTracks)
            }
            if (!seedAlbums.isNullOrEmpty()) {
                updateDiscographyState(seedAlbums)
            } else {
                updateDiscographyState(emptyList())
        _similarArtists.value = emptyList()
            }
            val seedSimilar = validExplicitId?.let { artistRepository.getCachedSimilarArtists(it) } ?: artistRepository.getCachedSimilarArtists(cleanName)
            _similarArtists.value = seedSimilar ?: emptyList()
            _isLoadingArtist.value = seedTracks.isNullOrEmpty()
            _isLoadingDiscography.value = seedAlbums.isNullOrEmpty()
        } else {
            _isLoadingArtist.value = _artistTracks.value.isEmpty()
            _isLoadingDiscography.value = _artistDiscography.value.isEmpty()
        }

        artistJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Resolusi otomatis: Wajib Exact / Full Name Match, kembalikan null jika artis tidak ada di Spotify
                val targetArtistId = validExplicitId ?: if (validExplicitId?.startsWith("bc_") == true) null else artistRepository.searchArtistByName(cleanName)

                withContext(Dispatchers.Main) {
                    _selectedArtistId.value = targetArtistId
                }

                var cachedReleases: List<AlbumRelease>? = null

                // 1. Tampilkan diskografi dari in-memory cache atau cache lokal
                if (!targetArtistId.isNullOrBlank()) {
                    val memCached = artistRepository.getCachedAlbums(targetArtistId)
                    if (!memCached.isNullOrEmpty()) {
                        cachedReleases = memCached
                        withContext(Dispatchers.Main) {
                            updateDiscographyState(memCached)
                            _isLoadingDiscography.value = false
                        }
                    } else {
                        val cached = artistRepository.getCachedDiscography(targetArtistId, expectedArtistName = cleanName)
                        if (!cached.isNullOrEmpty()) {
                            cachedReleases = cached
                            withContext(Dispatchers.Main) {
                                updateDiscographyState(cached)
                                _isLoadingDiscography.value = false
                            }
                        }
                    }
                }

                // 2. Fetch Official High-Res Photo
                val photoUrl = artistRepository.getOfficialArtistPhoto(cleanName, targetArtistId)
                withContext(Dispatchers.Main) {
                    _artistOfficialPhoto.value = photoUrl
                }

                // 3. Fetch trek resmi artis langsung dari katalog resmi Spotify (top-tracks)
                var validOfficial: List<Track> = emptyList()
                try {
                    val officialTracks = if (!targetArtistId.isNullOrBlank()) {
                        artistRepository.getOfficialArtistTopTracks(targetArtistId, cleanName)
                    } else {
                        artistRepository.getOfficialArtistTopTracks(cleanName)
                    }

                    validOfficial = officialTracks.map { t ->
                        val local = downloadManager.getLocalFilePath(t.id)
                        t.copy(localFilePath = local, spotifyArtistId = targetArtistId)
                    }

                    // Ambil trek lokal dari koleksi perangkat dengan verifikasi nama artis yang ketat
                    val localMatches = mutableListOf<Track>()
                    val isArtistMatch = { trackArtist: String ->
                        trackArtist.equals(cleanName, ignoreCase = true) ||
                                com.example.spotifyclone.util.ArtistParser.parseArtists(trackArtist)
                                    .any { it.equals(cleanName, ignoreCase = true) }
                    }
                    if (targetArtistId?.startsWith("bc_") != true) {
                        localMatches.addAll(_allTracks.value.filter { isArtistMatch(it.artist) })
                        localMatches.addAll(_likedTracks.value.filter { isArtistMatch(it.artist) })
                        playlistTracksMap.values.forEach { list ->
                            localMatches.addAll(list.filter { isArtistMatch(it.artist) })
                        }
                    } else {
                        localMatches.addAll(_allTracks.value.filter { it.source == "bandcamp" && (isArtistMatch(it.artist) || it.spotifyArtistId == targetArtistId) })
                        localMatches.addAll(_likedTracks.value.filter { it.source == "bandcamp" && (isArtistMatch(it.artist) || it.spotifyArtistId == targetArtistId) })
                    }
                    if (cleanName.contains("Silampukau", ignoreCase = true)) {
                        localMatches.addAll(getSilampukauCuratedTracks())
                    } else if (cleanName.contains("Majelis Lidah Berduri", ignoreCase = true) || cleanName.contains("Melancholic Bitch", ignoreCase = true)) {
                        localMatches.addAll(getMajelisLidahBerduriCuratedTracks())
                    }

                    val combined = (validOfficial + localMatches)
                        .distinctBy { it.title.trim().lowercase() }
                    withContext(Dispatchers.Main) {
                        if (combined.isNotEmpty()) {
                            _artistTracks.value = combined
                            preFetchTopTracks(combined)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        _isLoadingArtist.value = false
                    }
                }

                // 4. Fresh network call ke Spotify API langsung apa adanya tanpa filter
                try {
                    val diskografiTargetId = targetArtistId ?: cleanName
                    val freshDiscography = artistRepository.getArtistAlbums(diskografiTargetId)

                    if (freshDiscography.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            updateDiscographyState(freshDiscography)
                        }
                    } else if (cleanName.contains("Majelis Lidah Berduri", ignoreCase = true) || cleanName.contains("Melancholic Bitch", ignoreCase = true)) {
                        val curated = artistRepository.getMajelisLidahBerduriCuratedDiscography()
                        withContext(Dispatchers.Main) {
                            updateDiscographyState(curated)
                        }
                    } else if (cleanName.contains("Silampukau", ignoreCase = true)) {
                        val curated = artistRepository.getSilampukauCuratedDiscography()
                        withContext(Dispatchers.Main) {
                            updateDiscographyState(curated)
                        }
                    } else if (validOfficial.isNotEmpty() && targetArtistId?.startsWith("bc_") != true) {
                        val fromTop = artistRepository.extractAlbumsFromTopTracks(validOfficial, cleanName, targetArtistId)
                        withContext(Dispatchers.Main) {
                            updateDiscographyState(fromTop)
                        }
                    }

                    // Ambil rekomendasi musisi serupa ("Penggemar Juga Menyukai")
                    try {
                        val similar = artistRepository.getSimilarArtists(cleanName)
                        withContext(Dispatchers.Main) {
                            if (similar.isNotEmpty()) {
                                _similarArtists.value = similar
                            }
                        }
                    } catch (_: Exception) {}
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Pastikan isLoadingArtist dan isLoadingDiscography selalu false di blok finally
                withContext(Dispatchers.Main) {
                    _isLoadingArtist.value = false
                    _isLoadingDiscography.value = false
                }
            }
        }
    }

    private fun getSilampukauCuratedTracks(): List<Track> {
        val coverDosa = "https://i.scdn.co/image/ab67616d0000b273b50c05872a9e33433e36e788"
        val coverSingle = "https://i.scdn.co/image/ab67616d0000b273ef5e054baeb305c48bbf135e"
        return listOf(
            Track(
                id = "silampukau_1",
                title = "Bola Raya",
                artist = "Silampukau",
                album = "Dosa, Kota & Kenangan",
                durationSeconds = 387,
                streamUrl = "https://music.youtube.com/watch?v=gT8v8Y9C_qE",
                coverUrl = coverDosa,
                genre = "Folk",
                youtubeVideoId = "gT8v8Y9C_qE"
            ),
            Track(
                id = "silampukau_2",
                title = "Puan Kelana",
                artist = "Silampukau",
                album = "Dosa, Kota & Kenangan",
                durationSeconds = 277,
                streamUrl = "https://music.youtube.com/watch?v=Fj-E_w4eLq4",
                coverUrl = coverDosa,
                genre = "Folk",
                youtubeVideoId = "Fj-E_w4eLq4"
            ),
            Track(
                id = "silampukau_3",
                title = "Doa 1",
                artist = "Silampukau",
                album = "Dosa, Kota & Kenangan",
                durationSeconds = 295,
                streamUrl = "https://music.youtube.com/watch?v=vV77m6_Z1w0",
                coverUrl = coverDosa,
                genre = "Folk",
                youtubeVideoId = "vV77m6_Z1w0"
            ),
            Track(
                id = "silampukau_4",
                title = "Malam Jatuh di Surabaya",
                artist = "Silampukau",
                album = "Dosa, Kota & Kenangan",
                durationSeconds = 301,
                streamUrl = "https://music.youtube.com/watch?v=6K5iT0xK_3s",
                coverUrl = coverDosa,
                genre = "Folk",
                youtubeVideoId = "6K5iT0xK_3s"
            ),
            Track(
                id = "silampukau_5",
                title = "Si Pelanggan",
                artist = "Silampukau",
                album = "Dosa, Kota & Kenangan",
                durationSeconds = 272,
                streamUrl = "https://music.youtube.com/watch?v=4G2EwQJ7P6w",
                coverUrl = coverDosa,
                genre = "Folk",
                youtubeVideoId = "4G2EwQJ7P6w"
            ),
            Track(
                id = "silampukau_6",
                title = "Bianglala",
                artist = "Silampukau",
                album = "Dosa, Kota & Kenangan",
                durationSeconds = 303,
                streamUrl = "https://music.youtube.com/watch?v=d_OaK4E5q9s",
                coverUrl = coverDosa,
                genre = "Folk",
                youtubeVideoId = "d_OaK4E5q9s"
            ),
            Track(
                id = "silampukau_7",
                title = "Dendang Sangsi",
                artist = "Silampukau",
                album = "Single",
                durationSeconds = 265,
                streamUrl = "https://music.youtube.com/watch?v=Kz6M8T_71yU",
                coverUrl = coverSingle,
                genre = "Folk",
                youtubeVideoId = "Kz6M8T_71yU"
            )
        )
    }

    private fun getMajelisLidahBerduriCuratedTracks(): List<Track> {
        val coverNkkbs = "https://is1-ssl.mzstatic.com/image/thumb/Music123/v4/96/1a/bb/961abb88-4b86-dcab-c419-dfe7704a54ee/artwork.jpg/600x600bb.jpg"
        val coverJoniSusi = "https://is1-ssl.mzstatic.com/image/thumb/Music128/v4/b5/84/6e/b5846e08-97a3-f1f5-a312-39367308339e/artwork.jpg/600x600bb.jpg"
        val coverReanamnesis = "https://is1-ssl.mzstatic.com/image/thumb/Music118/v4/23/e3/13/23e3137e-7931-e15f-9b9b-377dd578705c/artwork.jpg/600x600bb.jpg"
        val coverMama = "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/a0/d7/c0/a0d7c098-080a-06c7-a4a9-879c954abd16/8721416476037.png/600x600bb.jpg"

        return listOf(
            Track(
                id = "mlb_1",
                title = "Mama - Mama Perlawanan",
                artist = "Majelis Lidah Berduri",
                album = "Mama - Mama Perlawanan",
                durationSeconds = 275,
                streamUrl = "https://music.youtube.com/search?q=${java.net.URLEncoder.encode("Mama Mama Perlawanan Majelis Lidah Berduri", "UTF-8")}",
                coverUrl = coverMama,
                genre = "Indie Rock"
            ),
            Track(
                id = "mlb_2",
                title = "7 Hari Menuju Silir",
                artist = "Majelis Lidah Berduri",
                album = "Balada Joni Dan Susi",
                durationSeconds = 290,
                streamUrl = "https://music.youtube.com/search?q=${java.net.URLEncoder.encode("7 Hari Menuju Silir Majelis Lidah Berduri", "UTF-8")}",
                coverUrl = coverJoniSusi,
                genre = "Indie Rock"
            ),
            Track(
                id = "mlb_3",
                title = "Tentang Cinta",
                artist = "Majelis Lidah Berduri",
                album = "Balada Joni Dan Susi",
                durationSeconds = 312,
                streamUrl = "https://music.youtube.com/search?q=${java.net.URLEncoder.encode("Tentang Cinta Melancholic Bitch", "UTF-8")}",
                coverUrl = coverJoniSusi,
                genre = "Indie Rock"
            ),
            Track(
                id = "mlb_4",
                title = "Kartu Pos Bergambar Jembatan Golden Gate",
                artist = "Majelis Lidah Berduri",
                album = "NKKBS Bagian Pertama",
                durationSeconds = 284,
                streamUrl = "https://music.youtube.com/search?q=${java.net.URLEncoder.encode("Kartu Pos Bergambar Jembatan Golden Gate Melancholic Bitch", "UTF-8")}",
                coverUrl = coverNkkbs,
                genre = "Indie Rock"
            ),
            Track(
                id = "mlb_5",
                title = "Bianglala",
                artist = "Majelis Lidah Berduri",
                album = "NKKBS Bagian Pertama",
                durationSeconds = 270,
                streamUrl = "https://music.youtube.com/search?q=${java.net.URLEncoder.encode("Bianglala Melancholic Bitch", "UTF-8")}",
                coverUrl = coverNkkbs,
                genre = "Indie Rock"
            ),
            Track(
                id = "mlb_6",
                title = "Distopia",
                artist = "Majelis Lidah Berduri",
                album = "NKKBS Bagian Pertama",
                durationSeconds = 295,
                streamUrl = "https://music.youtube.com/search?q=${java.net.URLEncoder.encode("Distopia Melancholic Bitch", "UTF-8")}",
                coverUrl = coverNkkbs,
                genre = "Indie Rock"
            ),
            Track(
                id = "mlb_7",
                title = "Dinding Pajangan",
                artist = "Majelis Lidah Berduri",
                album = "Re-Anamnesis",
                durationSeconds = 280,
                streamUrl = "https://music.youtube.com/search?q=${java.net.URLEncoder.encode("Dinding Pajangan Melancholic Bitch", "UTF-8")}",
                coverUrl = coverReanamnesis,
                genre = "Indie Rock"
            )
        )
    }

    fun closeArtistProfile() {
        _selectedArtistName.value = null
        _selectedArtistId.value = null
        _artistTracks.value = emptyList()
        _artistOfficialPhoto.value = null
        updateDiscographyState(emptyList())
        _similarArtists.value = emptyList()
        _selectedAlbumRelease.value = null
        _albumDetailTracks.value = emptyList()
        _isLoadingArtist.value = false
        _isLoadingDiscography.value = false
        artistJob?.cancel()
        albumDetailJob?.cancel()
    }

    fun openAlbumDetail(album: AlbumRelease) {
        _selectedAlbumRelease.value = album
        _isLoadingAlbumDetail.value = true
        _albumDetailTracks.value = emptyList()

        albumDetailJob?.cancel()
        albumDetailJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val tracks = artistRepository.getAlbumTracks(
                    collectionId = album.collectionId,
                    spotifyId = album.spotifyId,
                    albumTitle = album.title,
                    artistName = album.artistName,
                    coverUrl = album.coverUrl
                )

                val finalTracks = if (tracks.isNotEmpty()) {
                    tracks
                } else {
                    val matchingArtistTracks = _artistTracks.value.filter {
                        it.album.contains(album.title, ignoreCase = true) ||
                        album.title.contains(it.album, ignoreCase = true) ||
                        it.title.contains(album.title, ignoreCase = true)
                    }
                    val matchingLocal = _allTracks.value.filter {
                        it.album.contains(album.title, ignoreCase = true) ||
                        it.title.contains(album.title, ignoreCase = true)
                    }
                    if (matchingArtistTracks.isNotEmpty()) {
                        matchingArtistTracks
                    } else if (matchingLocal.isNotEmpty()) {
                        matchingLocal
                    } else if (album.artistName.contains("Silampukau", ignoreCase = true)) {
                        getSilampukauCuratedTracks().filter {
                            it.album.contains(album.title, ignoreCase = true) ||
                            album.title.contains(it.album, ignoreCase = true)
                        }.ifEmpty { getSilampukauCuratedTracks() }
                    } else if (album.artistName.contains("Majelis Lidah Berduri", ignoreCase = true) || album.artistName.contains("Melancholic Bitch", ignoreCase = true)) {
                        getMajelisLidahBerduriCuratedTracks().filter {
                            it.album.contains(album.title, ignoreCase = true) ||
                            album.title.contains(it.album, ignoreCase = true)
                        }.ifEmpty { getMajelisLidahBerduriCuratedTracks() }
                    } else {
                        listOf(
                            Track(
                                id = "album_track_${album.id}",
                                title = album.title,
                                artist = album.artistName,
                                album = album.title,
                                durationSeconds = 240,
                                streamUrl = "https://music.youtube.com/search?q=${java.net.URLEncoder.encode("${album.title} ${album.artistName}", "UTF-8")}",
                                coverUrl = album.coverUrl,
                                genre = "Rock"
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    _albumDetailTracks.value = finalTracks
                    preFetchTopTracks(finalTracks)
                    _isLoadingAlbumDetail.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _isLoadingAlbumDetail.value = false
                }
            }
        }
    }

    fun closeAlbumDetail() {
        _selectedAlbumRelease.value = null
        _albumDetailTracks.value = emptyList()
        _isLoadingAlbumDetail.value = false
        albumDetailJob?.cancel()
    }

    // Dynamic Playlist Recommendation Engine berbasis lagu playlist & YouTube Music API
    fun loadRecommendationsForPlaylist(playlist: SpotifyPlaylist, tracks: List<Track>, forceRefresh: Boolean = false) {
        if (!forceRefresh && _playlistRecommendations.value.isNotEmpty() && _selectedPlaylist.value?.id == playlist.id) {
            return
        }
        recommendationJob?.cancel()
        _playlistRecommendations.value = emptyList()

        if (tracks.isEmpty()) {
            _dominantGenre.value = "Belum ada lagu untuk umpan rekomendasi"
            _isLoadingRecommendations.value = false
            return
        }

        recommendationJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoadingRecommendations.value = true
            try {
                // 1. Baca daftar lagu dan artis yang tersimpan di dalam playlist yang dibuka
                val existingIds = tracks.map { it.id }.toSet()
                val existingVideoIds = tracks.mapNotNull { it.youtubeVideoId }.toSet()
                val existingTitles = tracks.map { "${it.title.lowercase().trim()} - ${it.artist.lowercase().trim()}" }.toSet()

                // 2. Ambil 3 sampai 5 lagu secara acak dari dalam playlist tersebut sebagai umpan pencarian
                val seedSampleCount = if (tracks.size >= 5) (3..5).random() else tracks.size.coerceAtLeast(1)
                val seedTracks = tracks.shuffled().take(seedSampleCount)
                val seedArtistNames = seedTracks.map { it.artist.trim() }.filter { it.isNotBlank() }.distinct()

                val subtitle = if (seedArtistNames.isNotEmpty()) {
                    "Berdasarkan lagu & musisi (${seedArtistNames.take(3).joinToString(", ")})"
                } else {
                    "Rekomendasi musik untuk playlist ${playlist.name}"
                }
                withContext(Dispatchers.Main) {
                    _dominantGenre.value = subtitle
                }

                val candidateList = mutableListOf<Track>()

                // 3. Pencarian lagu terkait / radio mix via API YouTube Music
                coroutineScope {
                    val deferredJobs = seedTracks.map { seed ->
                        async {
                            val results = mutableListOf<Track>()
                            try {
                                val seedQuery = "${seed.artist} ${seed.title}"
                                val searchRes = youTubeMusicRepository.searchSongs(seedQuery, limit = 6)
                                results.addAll(searchRes)
                            } catch (_: Exception) {}

                            try {
                                val artistRes = youTubeMusicRepository.searchSongs(seed.artist, limit = 5)
                                results.addAll(artistRes)
                            } catch (_: Exception) {}

                            results
                        }
                    }

                    val allBatch = deferredJobs.awaitAll().flatten()
                    for (track in allBatch) {
                        val vid = track.youtubeVideoId
                        val titleKey = "${track.title.lowercase().trim()} - ${track.artist.lowercase().trim()}"
                        // Saring daftar hasil rekomendasi agar sistem tidak menampilkan lagu yang sudah ada di dalam playlist
                        val alreadyInPlaylist = existingIds.contains(track.id) ||
                                (vid != null && existingVideoIds.contains(vid)) ||
                                existingTitles.contains(titleKey)

                        val alreadyInCandidates = candidateList.any { c ->
                            c.id == track.id ||
                            (vid != null && c.youtubeVideoId == vid) ||
                            (c.title.equals(track.title, ignoreCase = true) && c.artist.equals(track.artist, ignoreCase = true))
                        }

                        if (!alreadyInPlaylist && !alreadyInCandidates) {
                            val local = downloadManager.getLocalFilePath(track.id)
                            candidateList.add(track.copy(localFilePath = local))
                        }
                    }
                }

                val finalResult = candidateList.shuffled().distinctBy { it.id }.take(15)

                withContext(Dispatchers.Main) {
                    _playlistRecommendations.value = finalResult
                    _isLoadingRecommendations.value = false
                }
            } catch (e: Exception) {
                Log.e("CRANKR_PLAYER", "Gagal memuat rekomendasi playlist: ${e.message}")
                withContext(Dispatchers.Main) {
                    _isLoadingRecommendations.value = false
                }
            }
        }
    }

    fun refreshPlaylistRecommendations() {
        val pl = _selectedPlaylist.value ?: return
        val tr = _selectedPlaylistTracks.value
        loadRecommendationsForPlaylist(pl, tr, forceRefresh = true)
    }

    fun addRecommendedTrackToPlaylist(playlistId: String, track: Track) {
        addTrackToPlaylist(playlistId, track)
        _playlistRecommendations.update { list -> list.filter { it.id != track.id } }
    }

    fun downloadAllPlaylistTracks(tracks: List<Track>) {
        tracks.forEach { track ->
            if (!track.isDownloaded) {
                downloadTrack(track)
            }
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMode.value = "MINUTES"
        val totalSec = minutes * 60L
        _sleepTimerRemainingSeconds.value = totalSec
        _sleepTimerRemainingMinutes.value = minutes
        Toast.makeText(getApplication(), "Sleep timer disetel: $minutes menit (Tape Wind-Down aktif)", Toast.LENGTH_SHORT).show()

        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSec
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _sleepTimerRemainingSeconds.value = remaining
                _sleepTimerRemainingMinutes.value = ((remaining + 59) / 60).toInt()

                // Tape Wind-Down effect: last 15 seconds gradually reduces audio volume
                if (remaining <= 15) {
                    val fadeFraction = (remaining.toFloat() / 15f).coerceIn(0.05f, 1f)
                    setAudioVolume(fadeFraction)
                }
            }
            if (isActive) {
                _isPlaying.value = false
                youTubeAudioEngine.pause()
                exoPlayer.pause()
                setAudioVolume(1f) // restore volume for future playback
                _sleepTimerRemainingMinutes.value = null
                _sleepTimerRemainingSeconds.value = null
                _sleepTimerMode.value = null
                updateMediaNotification()
                Toast.makeText(getApplication(), "Tape wind-down selesai: musik dihentikan", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun setSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMinutes.value = null
        _sleepTimerRemainingSeconds.value = null
        _sleepTimerMode.value = "END_OF_TRACK"
        Toast.makeText(getApplication(), "Musik berhenti di akhir lagu (Tape Wind-Down aktif)", Toast.LENGTH_SHORT).show()
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMinutes.value = null
        _sleepTimerRemainingSeconds.value = null
        _sleepTimerMode.value = null
        setAudioVolume(1f)
        Toast.makeText(getApplication(), "Sleep timer dimatikan", Toast.LENGTH_SHORT).show()
    }

    private fun checkEndOfTrackSleepTimer(positionMs: Long, durationMs: Long) {
        if (_sleepTimerMode.value == "END_OF_TRACK" && durationMs > 0) {
            val remainingMs = durationMs - positionMs
            if (remainingMs in 0..12000L) {
                // Tape Wind-down fade in last 12 seconds of track
                val fraction = (remainingMs.toFloat() / 12000f).coerceIn(0.05f, 1f)
                setAudioVolume(fraction)
            }
        }
    }

    fun setEqualizerPreset(preset: String) {
        _currentEqPreset.value = preset
        crankrPrefs.edit().putString("eq_preset", preset).apply()
        when (preset.lowercase()) {
            "bass boost" -> {
                _bassGain.value = 1.0f
                _trebleGain.value = 0.55f
            }
            "rock" -> {
                _bassGain.value = 0.85f
                _trebleGain.value = 0.90f
            }
            "pop" -> {
                _bassGain.value = 0.45f
                _trebleGain.value = 0.45f
            }
            "vocal" -> {
                _bassGain.value = 0.35f
                _trebleGain.value = 0.65f
            }
            "acoustic" -> {
                _bassGain.value = 0.70f
                _trebleGain.value = 0.75f
            }
            else /* Flat */ -> {
                _bassGain.value = 0.50f
                _trebleGain.value = 0.50f
            }
        }
        crankrPrefs.edit()
            .putFloat("eq_bass_gain", _bassGain.value)
            .putFloat("eq_treble_gain", _trebleGain.value)
            .apply()
        equalizerHandler.setEqualizerPreset(preset)
        pcmEqualizerAudioProcessor.setPreset(preset)
        pcmEqualizerAudioProcessor.setBassGain(_bassGain.value)
        pcmEqualizerAudioProcessor.setTrebleGain(_trebleGain.value)

        equalizer?.let { eq ->
            try {
                if (!eq.enabled) eq.enabled = true
                val idx = findPresetIndex(eq, preset)
                if (idx != null) {
                    eq.usePreset(idx)
                } else {
                    applyPresetLevelsFallback(eq, preset)
                }
            } catch (e: Exception) {
                applyPresetLevelsFallback(eq, preset)
            }
        }
        Toast.makeText(getApplication(), "Preset EQ: $preset aktif", Toast.LENGTH_SHORT).show()
    }

    fun applyPreset(presetIndex: Short) {
        val eq = equalizer ?: equalizerHandler.equalizer ?: return
        if (presetIndex in 0 until eq.numberOfPresets) {
            eq.usePreset(presetIndex)
            equalizerHandler.usePreset(presetIndex)
            val name = try { eq.getPresetName(presetIndex) } catch (_: Exception) { "Preset $presetIndex" }
            _currentEqPreset.value = name
            crankrPrefs.edit().putString("eq_preset", name).apply()
            pcmEqualizerAudioProcessor.setPreset(name)
        }
    }

    fun applyPreset(presetIndex: Int) {
        applyPreset(presetIndex.toShort())
    }

    fun usePreset(presetIndex: Short) {
        applyPreset(presetIndex)
    }

    fun usePreset(presetIndex: Int) {
        applyPreset(presetIndex.toShort())
    }

    fun setEqualizerPreset(presetIndex: Short) {
        applyPreset(presetIndex)
    }

    fun setEqualizerPreset(presetIndex: Int) {
        applyPreset(presetIndex.toShort())
    }

    fun setBassGain(progressPercent: Float) {
        val clamped = progressPercent.coerceIn(0f, 1f)
        _bassGain.value = clamped
        crankrPrefs.edit().putFloat("eq_bass_gain", clamped).apply()
        setBandGain(0.toShort(), clamped)
        val bassBoostStrength = (clamped * 1000).toInt().coerceIn(0, 1000).toShort()
        setBassBoostStrength(bassBoostStrength)
        pcmEqualizerAudioProcessor.setBassGain(clamped)
    }

    fun setTrebleGain(progressPercent: Float) {
        val clamped = progressPercent.coerceIn(0f, 1f)
        _trebleGain.value = clamped
        crankrPrefs.edit().putFloat("eq_treble_gain", clamped).apply()
        val numBands = getNumberOfBands()
        val trebleBand = if (numBands > 0) (numBands - 1).toShort() else 4.toShort()
        setBandGain(trebleBand, clamped)
        pcmEqualizerAudioProcessor.setTrebleGain(clamped)
    }

    fun setBandGain(band: Short, progressPercent: Float) {
        val eq = equalizer ?: equalizerHandler.equalizer
        if (eq != null) {
            try {
                if (!eq.enabled) eq.enabled = true
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]
                val calculatedLevel = (minLevel + (maxLevel - minLevel) * progressPercent.coerceIn(0f, 1f)).toInt().toShort()
                eq.setBandLevel(band, calculatedLevel)
            } catch (e: Exception) {
                Log.w("CRANKR_AUDIO", "Error setBandLevel on hardware EQ: ${e.message}")
            }
        }
        equalizerHandler.setBandGain(band, progressPercent)
    }

    fun setBandGain(band: Int, progressPercent: Float) {
        setBandGain(band.toShort(), progressPercent)
    }

    fun applyCurrentEqualizerSettings() {
        equalizerHandler.applySavedSettings()
        pcmEqualizerAudioProcessor.setPreset(_currentEqPreset.value)
        pcmEqualizerAudioProcessor.setBassGain(_bassGain.value)
        pcmEqualizerAudioProcessor.setTrebleGain(_trebleGain.value)

        val eq = equalizer ?: return
        try {
            if (!eq.enabled) eq.enabled = true
            val preset = _currentEqPreset.value
            val presetIndex = findPresetIndex(eq, preset)
            if (presetIndex != null) {
                eq.usePreset(presetIndex)
            } else {
                applyPresetLevelsFallback(eq, preset)
            }
            // Re-apply custom bass and treble gains
            setBandGain(0.toShort(), _bassGain.value)
            val numBands = eq.numberOfBands.toInt()
            val trebleBand = if (numBands > 0) (numBands - 1).toShort() else 4.toShort()
            setBandGain(trebleBand, _trebleGain.value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findPresetIndex(eq: Equalizer, presetName: String): Short? {
        try {
            val num = eq.numberOfPresets.toInt()
            for (i in 0 until num) {
                if (eq.getPresetName(i.toShort()).equals(presetName, ignoreCase = true)) {
                    return i.toShort()
                }
            }
        } catch (_: Exception) {}
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
            }
        } catch (_: Exception) {}
    }

    fun setBandLevel(band: Short, level: Short) {
        equalizer?.let { eq ->
            try {
                if (!eq.enabled) eq.enabled = true
                eq.setBandLevel(band, level)
            } catch (e: Exception) {
                Log.w("CRANKR_AUDIO", "Error setBandLevel: ${e.message}")
            }
        }
        equalizerHandler.setBandLevel(band, level)
    }

    fun setBandLevel(band: Int, level: Int) {
        setBandLevel(band.toShort(), level.toShort())
    }

    fun getBandLevel(band: Short): Short {
        return equalizer?.getBandLevel(band) ?: equalizerHandler.getBandLevel(band)
    }

    fun getBandLevel(band: Int): Short = getBandLevel(band.toShort())

    fun getNumberOfBands(): Short = equalizer?.numberOfBands ?: equalizerHandler.getNumberOfBands()

    fun getBandLevelRange(): ShortArray = equalizer?.bandLevelRange ?: equalizerHandler.getBandLevelRange()

    fun getCenterFreq(band: Short): Int {
        return equalizer?.getCenterFreq(band) ?: equalizerHandler.getCenterFreq(band)
    }

    fun setBassBoostStrength(strength: Short) {
        bassBoost?.let { bb ->
            try {
                if (bb.strengthSupported) {
                    bb.enabled = strength > 0
                    bb.setStrength(strength)
                }
            } catch (e: Exception) {
                Log.w("CRANKR_AUDIO", "Error setBassBoostStrength: ${e.message}")
            }
        }
        equalizerHandler.setBassBoostStrength(strength)
    }

    fun setVirtualizerStrength(strength: Short) {
        equalizerHandler.setVirtualizerStrength(strength)
    }

    fun setPreampGain(gainDb: Float) {
        val clamped = gainDb.coerceIn(0f, 12f)
        com.example.spotifyclone.service.AuralysSettingsManager.getInstance(getApplication()).setPreampGainDb(clamped)
        equalizerHandler.setPreampGain(clamped)
        pcmEqualizerAudioProcessor.setPreampGainDb(clamped)
    }

    fun getPreampGain(): Float =
        com.example.spotifyclone.service.AuralysSettingsManager.getInstance(getApplication()).preampGainDb.value

    private fun releaseAudioEffects() {
        equalizerHandler.releaseAudioEffects()
    }

    fun openAddToPlaylistDialog(track: Track) {
        _trackToAddToPlaylist.value = track
    }

    fun closeAddToPlaylistDialog() {
        _trackToAddToPlaylist.value = null
    }

    suspend fun exportPlaylistsJson(): String {
        return playlistStorageManager.exportPlaylistsJson()
    }

    fun restorePlaylistsFromJson(jsonStr: String) {
        viewModelScope.launch {
            val result = playlistStorageManager.importPlaylistsFromJson(jsonStr)
            if (result.isSuccess) {
                val restored = result.getOrDefault(emptyList())
                val currentList = _spotifyPlaylists.value.toMutableList()
                restored.forEach { (pl, tracks) ->
                    val existingIdx = currentList.indexOfFirst { it.id == pl.id }
                    if (existingIdx >= 0) {
                        currentList[existingIdx] = pl
                    } else {
                        currentList.add(pl)
                    }
                    playlistTracksMap[pl.id] = tracks.toMutableList()
                }
                _spotifyPlaylists.value = currentList
                persistPlaylists()
                Toast.makeText(getApplication(), "Berhasil memulihkan ${restored.size} playlist!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(getApplication(), "Gagal memulihkan: Format JSON tidak sesuai", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun flushCurrentTrackPlayDuration() {
        val trk = _currentTrack.value
        val durationMs = activeTrackPlayDurationMs
        if (trk != null && trk.id.isNotBlank() && durationMs >= 3000L) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    listeningHistoryDbHelper.insert(
                        ListeningHistoryEntity(
                            trackId = trk.id,
                            trackTitle = trk.title,
                            artistId = trk.spotifyArtistId ?: "",
                            artistName = trk.artist,
                            albumArtUrl = trk.coverUrl,
                            durationPlayedMs = durationMs,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            var statsUpdateTick = 0
            while (isActive) {
                val isExoPlaying = !isYouTubePlayback && exoPlayer.isPlaying
                val isPlayingNow = isExoPlaying || _isPlaying.value

                if (isExoPlaying) {
                    // Pastikan volume kembali ke 1.0f penuh secara otomatis setelah gangguan notifikasi / ducking selesai
                    if (exoPlayer.volume < 1.0f) {
                        exoPlayer.volume = 1.0f
                    }
                    _currentPosition.value = exoPlayer.currentPosition
                    if (exoPlayer.duration > 0) {
                        _duration.value = exoPlayer.duration
                    }
                    checkCrossfade(exoPlayer.currentPosition, exoPlayer.duration)
                    checkEndOfTrackSleepTimer(exoPlayer.currentPosition, exoPlayer.duration)

                    // Pantau persentase buffer lagu yang sedang berputar.
                    // Picu pengunduhan berkas audio untuk satu lagu berikutnya di antrean
                    // menggunakan CacheWriter saat buffer lagu aktif menyentuh 80 persen.
                    if (exoPlayer.bufferedPercentage >= 80) {
                        triggerNextTrackFullCacheWriter()
                    }
                }

                if (isPlayingNow) {
                    activeTrackPlayDurationMs += 500L
                    listeningStatsManager.addPlayTimeMs(500L)

                    statsUpdateTick++
                    if (statsUpdateTick >= 4) { // Update stats state setiap 2 detik
                        statsUpdateTick = 0
                        _listeningStats.value = listeningStatsManager.getStats()
                    }

                    // Record track history if played for at least 30 seconds
                    if (!hasRecordedHistoryForCurrentTrack && activeTrackPlayDurationMs >= 30_000L) {
                        hasRecordedHistoryForCurrentTrack = true
                        val trk = _currentTrack.value
                        if (trk != null && trk.id.isNotBlank()) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    listeningHistoryDbHelper.insert(
                                        ListeningHistoryEntity(
                                            trackId = trk.id,
                                            trackTitle = trk.title,
                                            artistId = trk.spotifyArtistId ?: "",
                                            artistName = trk.artist,
                                            albumArtUrl = trk.coverUrl,
                                            durationPlayedMs = activeTrackPlayDurationMs,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun observeDownloadProgress() {
        viewModelScope.launch {
            downloadManager.downloadProgressMap.collect { map ->
                if (map.isNotEmpty()) {
                    _downloadProgressMap.update { current -> current + map }
                }
            }
        }
    }

    private fun setupListenTogetherHooks() {
        listenTogetherManager.onRemoteTrackChange = { track, positionMs ->
            viewModelScope.launch(Dispatchers.Main) {
                isSyncingFromRemote = true
                playTrack(track)
                if (positionMs > 500) {
                    seekTo(positionMs)
                }
                isSyncingFromRemote = false
            }
        }

        listenTogetherManager.onRemotePlay = { trackId, positionMs, _ ->
            viewModelScope.launch(Dispatchers.Main) {
                if (_currentTrack.value?.id == trackId) {
                    val currentPos = if (isYouTubePlayback) _currentPosition.value else exoPlayer.currentPosition
                    if (Math.abs(currentPos - positionMs) > 1200L) {
                        seekTo(positionMs)
                    }
                    if (!_isPlaying.value) {
                        isSyncingFromRemote = true
                        resume()
                        isSyncingFromRemote = false
                    }
                }
            }
        }

        listenTogetherManager.onRemotePause = { positionMs ->
            viewModelScope.launch(Dispatchers.Main) {
                isSyncingFromRemote = true
                seekTo(positionMs)
                pause()
                isSyncingFromRemote = false
            }
        }

        listenTogetherManager.onRemoteSeek = { positionMs ->
            viewModelScope.launch(Dispatchers.Main) {
                isSyncingFromRemote = true
                seekTo(positionMs)
                isSyncingFromRemote = false
            }
        }

        listenTogetherManager.onTrackSuggested = { track, sender ->
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(getApplication(), "$sender menyarankan lagu: ${track.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =========================================================================
    // LISTEN TOGETHER PUBLIC CONTROLS
    // =========================================================================

    fun createListenTogetherRoom(hostName: String = "Host"): String {
        val code = listenTogetherManager.createRoom(hostName, _currentTrack.value)
        Toast.makeText(getApplication(), "Ruangan Putar Bersama dibuat: $code", Toast.LENGTH_SHORT).show()
        return code
    }

    fun joinListenTogetherRoom(roomId: String, guestName: String = "Guest") {
        listenTogetherManager.joinRoom(roomId, guestName)
        Toast.makeText(getApplication(), "Bergabung ke sesi $roomId...", Toast.LENGTH_SHORT).show()
    }

    fun leaveListenTogetherRoom() {
        listenTogetherManager.leaveRoom()
        Toast.makeText(getApplication(), "Keluar dari sesi Putar Bersama", Toast.LENGTH_SHORT).show()
    }

    fun setListenTogetherAutoApproveSuggestions(enable: Boolean) {
        listenTogetherManager.setAutoApproveSuggestions(enable)
    }

    fun suggestListenTogetherTrack(track: Track) {
        listenTogetherManager.suggestTrack(track)
        Toast.makeText(getApplication(), "Menyarankan \"${track.title}\" ke Host", Toast.LENGTH_SHORT).show()
    }

    fun approveListenTogetherSuggestion(track: Track) {
        listenTogetherManager.approveSuggestion(track)
        playTrack(track)
    }

    fun rejectListenTogetherSuggestion(trackId: String) {
        listenTogetherManager.rejectSuggestion(trackId)
    }

    private fun stopProgressTracker() {
        flushCurrentTrackPlayDuration()
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        val curTrack = _currentTrack.value
        if (curTrack != null) {
            val pos = if (isYouTubePlayback) _currentPosition.value else exoPlayer.currentPosition
            if (pos > 0L) {
                crankrPrefs.edit().putLong("last_position_ms", pos).apply()
            }
        }
        listenTogetherManager.leaveRoom()
        stopProgressTracker()
        releaseAudioEffects()
        MusicPlaybackService.stop(getApplication())
        MusicNotificationReceiver.onPlayPause = null
        MusicNotificationReceiver.onPlay = null
        MusicNotificationReceiver.onPause = null
        MusicNotificationReceiver.onNext = null
        MusicNotificationReceiver.onPrevious = null
        MusicNotificationReceiver.onSeekTo = null
        MusicNotificationReceiver.onBecomingNoisy = null
        MusicNotificationReceiver.onToggleFavorite = null
        MusicNotificationReceiver.isRepeatOne = false
        try {
            exoPlayer.stop()
            exoPlayer.release()
        } catch (_: Exception) {}
        try {
            youTubeAudioEngine.release()
        } catch (_: Exception) {}
        super.onCleared()
    }
}
