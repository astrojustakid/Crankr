package com.example.spotifyclone.service

import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.spotify.SpotifyApiService
import com.example.spotifyclone.util.MetadataSanitizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import android.net.Uri

/**
 * AudioStreamResolver Crankr:
 * Pengurai sumber stream audio bertahap presisi tinggi:
 * - Menggunakan kode ISRC Spotify dan katalog resmi YouTube Music (Filter Songs).
 * - Menjamin file audio master rekaman resmi dan membuang video klip, lirik, cover, atau live.
 * - Resolusi Direct Audio Stream URL murni (m4a/opus) dengan parallel race resolution.
 */
object AudioStreamResolver {

    private const val YTM_SONGS_FILTER_PARAMS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="

    private val videoCache = ConcurrentHashMap<String, String>()
    private val isrcCache = ConcurrentHashMap<String, String>()
    private val streamUrlCache = ConcurrentHashMap<String, String>()
    private val thumbnailCache = ConcurrentHashMap<String, String>()
    private val blacklistedVideoIds = ConcurrentHashMap.newKeySet<String>()
    private val loudnessDbCache = ConcurrentHashMap<String, Double>()

    fun getLoudnessDb(videoId: String): Double? = loudnessDbCache[videoId]

    fun cacheLoudnessDb(videoId: String, loudness: Double) {
        if (videoId.isNotBlank() && loudness.isFinite()) {
            loudnessDbCache[videoId] = loudness
        }
    }

    fun putIsrc(title: String, artist: String, isrc: String) {
        if (isrc.isNotBlank()) {
            val key = "$title - $artist".lowercase().trim()
            isrcCache[key] = isrc.trim()
        }
    }

    fun getIsrc(title: String, artist: String): String? {
        val key = "$title - $artist".lowercase().trim()
        return isrcCache[key]
    }

    data class CachedStream(
        val urls: List<String>,
        val expiresAtMs: Long
    )
    private val playbackUrlCache = android.util.LruCache<String, CachedStream>(100)
    private val streamCacheWithTtl = ConcurrentHashMap<String, CachedStream>()
    private const val DEFAULT_STREAM_TTL_MS = 4 * 60 * 60 * 1000L // 4 jam

    @Volatile
    var currentAudioQuality: AudioQuality = AudioQuality.STANDARD

    @Volatile
    var adaptiveQualityOverride: AudioQuality? = null

    val effectiveAudioQuality: AudioQuality
        get() = adaptiveQualityOverride ?: currentAudioQuality

    fun clearPlaybackCache() {
        streamUrlCache.clear()
        streamCacheWithTtl.clear()
        playbackUrlCache.evictAll()
    }

    fun clearStreamCache() {
        videoCache.clear()
        isrcCache.clear()
        streamUrlCache.clear()
        thumbnailCache.clear()
        streamCacheWithTtl.clear()
        playbackUrlCache.evictAll()
        loudnessDbCache.clear()
        blacklistedVideoIds.clear()
        MusicStreamResolver.clearStreamCache()
    }

    fun getCachedStreamUrls(videoId: String): List<String> {
        val cached = playbackUrlCache.get(videoId) ?: streamCacheWithTtl[videoId] ?: return emptyList()
        if (System.currentTimeMillis() > cached.expiresAtMs) {
            playbackUrlCache.remove(videoId)
            streamCacheWithTtl.remove(videoId)
            streamUrlCache.remove(videoId)
            return emptyList()
        }
        return cached.urls
    }

    fun putCachedStreamUrls(videoId: String, urls: List<String>) {
        if (videoId.isNotBlank() && urls.isNotEmpty()) {
            val ttlMs = extractTtlFromUrl(urls.first()) ?: DEFAULT_STREAM_TTL_MS
            val expiresAt = System.currentTimeMillis() + ttlMs
            val entry = CachedStream(urls, expiresAt)
            playbackUrlCache.put(videoId, entry)
            streamCacheWithTtl[videoId] = entry
            streamUrlCache[videoId] = urls.first()
        }
    }

    private fun extractTtlFromUrl(url: String): Long? {
        return try {
            val uri = Uri.parse(url)
            val expireSecStr = uri.getQueryParameter("expire")
            if (expireSecStr != null) {
                val expireEpochSec = expireSecStr.toLong()
                val nowSec = System.currentTimeMillis() / 1000L
                val remainingSec = expireEpochSec - nowSec
                if (remainingSec > 60) {
                    (remainingSec - 60) * 1000L
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun markVideoRestricted(videoId: String) {
        if (videoId.isNotBlank()) {
            blacklistedVideoIds.add(videoId)
            val keysToRemove = videoCache.filter { it.value == videoId }.keys
            keysToRemove.forEach { videoCache.remove(it) }
            streamUrlCache.remove(videoId)
            MusicStreamResolver.markVideoRestricted(videoId)
        }
    }

    fun isRestricted(videoId: String): Boolean {
        return blacklistedVideoIds.contains(videoId)
    }

    fun getThumbnailForVideo(videoId: String): String {
        return thumbnailCache[videoId] ?: MusicStreamResolver.getThumbnailForVideo(videoId)
    }

    fun cacheThumbnail(videoId: String, url: String) {
        if (videoId.isNotBlank() && url.isNotBlank()) {
            thumbnailCache[videoId] = url
            MusicStreamResolver.cacheThumbnail(videoId, url)
        }
    }

    private val forbiddenVideoRegex = Regex(
        """(?i)(?:\b(mv|m/v|live|cover|karaoke|lyric|lyrics|lirik)\b|music\s+video|official\s+video|video\s+clip|videoclip)"""
    )

    fun isForbiddenVideo(title: String, subtitle: String = "", originalTrackTitle: String = ""): Boolean {
        // Pengecualian khusus band legitim yang namanya memuat kata karaoke (misal: "Saturday Night Karaoke")
        val safeTitle = title.replace(Regex("""(?i)saturday\s+night\s+karaoke"""), "saturday night band")
        val safeSubtitle = subtitle.replace(Regex("""(?i)saturday\s+night\s+karaoke"""), "saturday night band")
        val origLower = originalTrackTitle.lowercase()
        val matchesTitle = forbiddenVideoRegex.containsMatchIn(safeTitle)
        val matchesSub = safeSubtitle.isNotBlank() && forbiddenVideoRegex.containsMatchIn(safeSubtitle)
        if (matchesTitle || matchesSub) {
            if (origLower.isNotBlank() && forbiddenVideoRegex.containsMatchIn(origLower)) {
                return false
            }
            return true
        }
        if (!MetadataSanitizer.isOfficialStudioTrack(title, description = subtitle)) {
            return true
        }
        if (MusicStreamResolver.isCompilationOrBootleg(title)) {
            return true
        }
        return false
    }

    suspend fun resolveYouTubeVideoId(track: Track): String? = withContext(Dispatchers.IO) {
        val resolvedIsrc = if (!track.isrc.isNullOrBlank()) {
            track.isrc
        } else if (track.id.startsWith("sp_")) {
            SpotifyApiService.fetchIsrcForTrack(track.id)
        } else null

        resolveYouTubeVideoId(
            title = track.title,
            artist = track.artistName,
            expectedDurationSeconds = track.durationSeconds,
            isrc = resolvedIsrc,
            trackId = track.id
        )
    }

    /**
     * Pencari sumber audio bertahap berbasis kode ISRC dan katalog lagu resmi YouTube Music:
     * - Tahap 1: Jika lagu memiliki kode ISRC (dari Track atau diambil via endpoint detail Spotify),
     *   cari langsung di YouTube Music InnerTube API menggunakan kode ISRC sebagai kata kunci
     *   dengan filter Songs (params: EgWKAQIIAWoKEAkQBRAKEAMQBA==).
     * - Tahap 2: Jika kode ISRC tidak tersedia atau hasil kosong, cari di YouTube Music dengan filter Songs
     *   menggunakan kata kunci nama artis dan judul lagu.
     *   Validasi hasilnya dengan mencocokkan nama artis serta memilih lagu yang memiliki selisih durasi maksimal 5 detik dari durasi asli Spotify.
     */
    suspend fun resolveYouTubeVideoId(
        title: String,
        artist: String,
        expectedDurationSeconds: Int? = null,
        isrc: String? = null,
        trackId: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null

        val cacheKey = "$cleanTitle - $cleanArtist".lowercase()
        val cached = videoCache[cacheKey]
        if (!cached.isNullOrEmpty() && !blacklistedVideoIds.contains(cached)) {
            return@withContext cached
        }

        // TAHAP 1: Resolusi via Kode ISRC (Katalog Rekaman Master Resmi Label)
        var effectiveIsrc = isrc ?: isrcCache[cacheKey]
        if (effectiveIsrc.isNullOrBlank() && !trackId.isNullOrBlank() && trackId.startsWith("sp_")) {
            effectiveIsrc = SpotifyApiService.fetchIsrcForTrack(trackId)
            if (!effectiveIsrc.isNullOrBlank()) {
                isrcCache[cacheKey] = effectiveIsrc
            }
        }

        if (!effectiveIsrc.isNullOrBlank()) {
            val isrcVid = searchYouTubeMusicSongs(
                query = effectiveIsrc,
                isIsrcSearch = true
            )
            if (!isrcVid.isNullOrBlank() && !blacklistedVideoIds.contains(isrcVid)) {
                videoCache[cacheKey] = isrcVid
                return@withContext isrcVid
            }
        }

        // TAHAP 2: Pencarian YouTube Music InnerTube API dengan Filter Songs (params: EgWKAQIIAWoKEAkQBRAKEAMQBA==)
        // Kata kunci: nama artis dan judul lagu dengan pencocokan nama artis serta selisih durasi maksimal 5 detik
        val primaryQueries = listOf(
            "$cleanArtist - $cleanTitle",
            "$cleanTitle $cleanArtist"
        )

        for (query in primaryQueries) {
            val vid = searchYouTubeMusicSongs(
                query = query,
                expectedArtist = cleanArtist,
                expectedTitle = cleanTitle,
                expectedDurationSeconds = expectedDurationSeconds,
                maxDurationDiffSeconds = 5,
                isIsrcSearch = false
            )
            if (!vid.isNullOrBlank() && !blacklistedVideoIds.contains(vid)) {
                videoCache[cacheKey] = vid
                return@withContext vid
            }
        }

        // Cadangan bertahap jika selisih 5 detik belum menemukan lagu: toleransi 15 detik pada filter Songs
        for (query in primaryQueries) {
            val vid = searchYouTubeMusicSongs(
                query = query,
                expectedArtist = cleanArtist,
                expectedTitle = cleanTitle,
                expectedDurationSeconds = expectedDurationSeconds,
                maxDurationDiffSeconds = 15,
                isIsrcSearch = false
            )
            if (!vid.isNullOrBlank() && !blacklistedVideoIds.contains(vid)) {
                videoCache[cacheKey] = vid
                return@withContext vid
            }
        }

        // Fallback terakhir: MusicStreamResolver
        val fallback = MusicStreamResolver.resolveYouTubeVideoId(cleanTitle, cleanArtist, null)
        if (!fallback.isNullOrBlank() && !blacklistedVideoIds.contains(fallback)) {
            videoCache[cacheKey] = fallback
            return@withContext fallback
        }

        null
    }

    suspend fun resolveYouTubeTrackInfo(
        title: String,
        artist: String,
        expectedDurationSeconds: Int? = null,
        isrc: String? = null,
        trackId: String? = null
    ): YouTubeTrackInfo? = withContext(Dispatchers.IO) {
        val vid = resolveYouTubeVideoId(title, artist, expectedDurationSeconds, isrc, trackId) ?: return@withContext null
        YouTubeTrackInfo(
            videoId = vid,
            title = title,
            artist = artist,
            thumbnailUrl = getThumbnailForVideo(vid)
        )
    }

    suspend fun resolveAlternativeVideoId(
        title: String,
        artist: String,
        failedVideoId: String,
        expectedDurationSeconds: Int? = null,
        isrc: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (failedVideoId.isNotBlank()) {
            markVideoRestricted(failedVideoId)
        }
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null

        val cacheKey = "$cleanTitle - $cleanArtist".lowercase()
        videoCache.remove(cacheKey)
        streamUrlCache.remove(failedVideoId)

        // Tahap 1: Coba pencarian YouTube Music Songs filter dengan kueri alternatif
        val altQueries = listOf(
            "$cleanTitle $cleanArtist",
            "$cleanArtist - $cleanTitle"
        )
        for (altQuery in altQueries) {
            val vid = searchYouTubeMusicSongs(
                query = altQuery,
                expectedArtist = cleanArtist,
                expectedTitle = cleanTitle,
                expectedDurationSeconds = expectedDurationSeconds,
                maxDurationDiffSeconds = 15,
                isIsrcSearch = false,
                excludeVid = failedVideoId
            )
            if (!vid.isNullOrBlank() && vid != failedVideoId && !blacklistedVideoIds.contains(vid)) {
                videoCache[cacheKey] = vid
                return@withContext vid
            }
        }

        val fallback = MusicStreamResolver.resolveAlternativeVideoId(cleanTitle, cleanArtist, failedVideoId, expectedDurationSeconds)
        if (!fallback.isNullOrBlank() && fallback != failedVideoId && !blacklistedVideoIds.contains(fallback)) {
            videoCache[cacheKey] = fallback
            return@withContext fallback
        }

        null
    }

    /**
     * Resolusi kandidat URL Stream Audio Murni (m4a/opus 128kbps efisien).
     * Menerapkan Pencarian Stream Paralel (Parallel Race Resolution) ke seluruh server
     * secara bersamaan dengan coroutine async/launch dan timeout 3 detik per request.
     * Mengambil hasil pertama yang valid dan langsung membatalkan proses pencarian sisanya.
     */
    suspend fun resolveCandidateStreamUrls(videoId: String): List<String> = withContext(Dispatchers.IO) {
        if (videoId.length != 11) return@withContext emptyList()

        // 1. Cek cache memori dengan TTL terlebih dahulu (0ms instant return)
        val cached = getCachedStreamUrls(videoId)
        if (cached.isNotEmpty()) {
            return@withContext cached
        }

        // 2. Parallel Race Resolution: jalankan pencarian ke Android VR, Web Remix, Android, dan iOS secara bersamaan
        val candidateDeferred = CompletableDeferred<List<String>>()
        val completedCount = AtomicInteger(0)
        val clients = listOf<suspend () -> List<String>>(
            { fetchStreamUrlsFromAndroidVrClient(videoId, timeoutMs = 3000) },
            { fetchStreamUrlsFromWebClient(videoId, timeoutMs = 3000) },
            { fetchStreamUrlsFromAndroidClient(videoId, timeoutMs = 3000) },
            { fetchStreamUrlsFromIosClient(videoId, timeoutMs = 3000) }
        )

        val scope = this
        val jobs = clients.map { clientTask ->
            scope.launch {
                try {
                    val urls = clientTask()
                    if (urls.isNotEmpty() && !candidateDeferred.isCompleted) {
                        candidateDeferred.complete(urls)
                    }
                } catch (_: Exception) {
                } finally {
                    if (completedCount.incrementAndGet() == clients.size && !candidateDeferred.isCompleted) {
                        candidateDeferred.complete(emptyList())
                    }
                }
            }
        }

        val winningUrls = withTimeoutOrNull(3000L) {
            candidateDeferred.await()
        } ?: emptyList()

        // Batalkan proses pencarian sisanya segera begitu pemenang ditemukan
        jobs.forEach { it.cancel() }

        if (winningUrls.isNotEmpty()) {
            putCachedStreamUrls(videoId, winningUrls)
        }

        winningUrls
    }

    /**
     * Pre-fetch dan simpan URL stream audio untuk sebuah track ke dalam cache memori.
     */
    suspend fun preFetchTrackStream(track: Track): Boolean = withContext(Dispatchers.IO) {
        if (track.title.isBlank()) return@withContext false
        val vid = if (!track.youtubeVideoId.isNullOrEmpty() && !isRestricted(track.youtubeVideoId)) {
            track.youtubeVideoId
        } else if (track.id.startsWith("yt_")) {
            val clean = track.id.removePrefix("yt_").trim()
            if (clean.length == 11 && !isRestricted(clean)) clean else resolveYouTubeVideoId(track.title, track.artist, track.durationSeconds)
        } else if (track.id.length == 11 && !track.id.startsWith("sp_") && !track.id.startsWith("local_") && !isRestricted(track.id)) {
            track.id
        } else {
            resolveYouTubeVideoId(track.title, track.artist, track.durationSeconds)
        }
        if (!vid.isNullOrEmpty() && !isRestricted(vid)) {
            val cached = getCachedStreamUrls(vid)
            if (cached.isNotEmpty()) return@withContext true
            val candidates = resolveCandidateStreamUrls(vid)
            return@withContext candidates.isNotEmpty()
        }
        return@withContext false
    }

    /**
     * Resolusi URL Stream Audio Langsung:
     * Mengembalikan stream audio murni dengan bitrate efisien tertinggi (audio-only m4a/opus).
     */
    suspend fun resolveDirectAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.length != 11) return@withContext null

        val cached = getCachedStreamUrls(videoId)
        if (cached.isNotEmpty()) {
            return@withContext cached.first()
        }

        resolveCandidateStreamUrls(videoId).firstOrNull()
    }

    private fun fetchStreamUrlsFromAndroidVrClient(videoId: String, timeoutMs: Int): List<String> {
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 12; Mobile VR; rv:105.0) Gecko/105.0 Firefox/105.0")
                setRequestProperty("X-YouTube-Client-Name", "56")
                setRequestProperty("X-YouTube-Client-Version", "1.60.19")
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }

            val jsonBody = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.60.19")
                        put("deviceMake", "Oculus")
                        put("deviceModel", "Quest 3")
                        put("osName", "Android")
                        put("osVersion", "12")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                extractAndCacheLoudness(resp, videoId)
                return parseAllAudioUrlsFromPlayerResponse(resp)
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    private fun fetchStreamUrlsFromAndroidClient(videoId: String, timeoutMs: Int): List<String> {
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip")
                setRequestProperty("X-YouTube-Client-Name", "3")
                setRequestProperty("X-YouTube-Client-Version", "19.09.37")
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }

            val jsonBody = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "19.09.37")
                        put("androidSdkVersion", 34)
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                extractAndCacheLoudness(resp, videoId)
                return parseAllAudioUrlsFromPlayerResponse(resp)
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    private fun fetchStreamUrlsFromIosClient(videoId: String, timeoutMs: Int): List<String> {
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "com.google.ios.youtube/19.09.3 (iPhone16,2; U; CPU iOS 17_4 like Mac OS X)")
                setRequestProperty("X-YouTube-Client-Name", "5")
                setRequestProperty("X-YouTube-Client-Version", "19.09.3")
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }

            val jsonBody = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "IOS")
                        put("clientVersion", "19.09.3")
                        put("deviceModel", "iPhone16,2")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                extractAndCacheLoudness(resp, videoId)
                return parseAllAudioUrlsFromPlayerResponse(resp)
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    private fun fetchStreamUrlsFromWebClient(videoId: String, timeoutMs: Int): List<String> {
        try {
            val url = URL("https://music.youtube.com/youtubei/v1/player")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Origin", "https://music.youtube.com")
                setRequestProperty("Referer", "https://music.youtube.com")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                setRequestProperty("x-youtube-client-name", "67")
                setRequestProperty("x-youtube-client-version", "1.20240101.01.00")
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }

            val jsonBody = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                extractAndCacheLoudness(resp, videoId)
                return parseAllAudioUrlsFromPlayerResponse(resp)
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    private fun extractAndCacheLoudness(responseText: String, videoId: String) {
        try {
            val root = JSONObject(responseText)
            val audioConfig = root.optJSONObject("playerConfig")?.optJSONObject("audioConfig")
            if (audioConfig != null && audioConfig.has("loudnessDb")) {
                val lDb = audioConfig.optDouble("loudnessDb", Double.NaN)
                if (lDb.isFinite()) {
                    cacheLoudnessDb(videoId, lDb)
                }
            }
        } catch (_: Exception) {}
    }

    private fun parseAudioUrlFromPlayerResponse(responseText: String): String? {
        val urls = parseAllAudioUrlsFromPlayerResponse(responseText)
        return urls.firstOrNull()
    }

    private fun parseAllAudioUrlsFromPlayerResponse(responseText: String): List<String> {
        val result = mutableListOf<Pair<Int, String>>()
        try {
            val root = JSONObject(responseText)
            val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return emptyList()

            for (i in 0 until formats.length()) {
                val fmt = formats.optJSONObject(i) ?: continue
                val mime = fmt.optString("mimeType", "")
                val streamUrl = fmt.optString("url", "")
                if (streamUrl.isBlank()) continue

                // HANYA ambil stream audio murni. Jangan pernah sertakan video!
                if (!mime.startsWith("audio/")) continue

                val isM4a = mime.contains("audio/mp4") || mime.contains("mp4a")
                val isOpus = mime.contains("audio/webm") || mime.contains("opus")
                if (!isM4a && !isOpus) continue

                val bitrate = fmt.optInt("bitrate", fmt.optInt("averageBitrate", 0))

                // Prioritaskan format berdasarkan opsi kualitas pilihan pengguna:
                // - SAVER: Hemat (64 kbps)
                // - STANDARD: Standar (128 kbps)
                // - MAXIMUM: Maksimal (Opus 256 kbps)
                val score = when (effectiveAudioQuality) {
                    AudioQuality.SAVER -> {
                        // Prioritaskan stream efisien ~50k-80kbps (Opus itag 249/250)
                        val delta = kotlin.math.abs(bitrate - 64_000)
                        val base = 50_000 - delta
                        if (bitrate in 40_000..85_000) base + 20_000 else base
                    }
                    AudioQuality.STANDARD -> {
                        // Keseimbangan optimal ~128 kbps (M4A itag 140 atau Opus ~130-160kbps)
                        when {
                            isM4a && bitrate in 115_000..150_000 -> 30_000 + bitrate
                            isOpus && bitrate in 110_000..170_000 -> 25_000 + bitrate
                            else -> 10_000 - kotlin.math.abs(bitrate - 128_000)
                        }
                    }
                    AudioQuality.MAXIMUM -> {
                        // Kualitas tertinggi: utamakan codec Opus dan bitrate tertinggi
                        val opusBonus = if (isOpus) 50_000 else 0
                        opusBonus + bitrate
                    }
                }

                result.add(Pair(score, streamUrl))
            }
            // Urutkan berdasarkan skor prioritas efisiensi tertinggi
            result.sortByDescending { it.first }
            return result.map { it.second }
        } catch (_: Exception) {
            return emptyList()
        }
    }

    /**
     * Pencarian lagu resmi pada katalog YouTube Music InnerTube API (https://music.youtube.com/youtubei/v1/search)
     * Menggunakan client context WEB_REMIX dan parameter filter khusus Songs (params: EgWKAQIIAWoKEAkQBRAKEAMQBA==).
     * Filter ini mengunci hasil pencarian HANYA pada file audio master rekaman resmi dari label rekaman,
     * dan otomatis membuang video klip (MV), video lirik, rekaman konser/live, serta lagu cover.
     */
    private fun searchYouTubeMusicSongs(
        query: String,
        expectedArtist: String? = null,
        expectedTitle: String? = null,
        expectedDurationSeconds: Int? = null,
        maxDurationDiffSeconds: Int = 5,
        isIsrcSearch: Boolean = false,
        excludeVid: String? = null
    ): String? {
        try {
            val url = URL("https://music.youtube.com/youtubei/v1/search")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-youtube-client-name", "67")
                setRequestProperty("x-youtube-client-version", "1.20240101.01.00")
                setRequestProperty("Origin", "https://music.youtube.com")
                setRequestProperty("Referer", "https://music.youtube.com")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                connectTimeout = 3000
                readTimeout = 3000
            }

            val jsonBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", query)
                put("params", YTM_SONGS_FILTER_PARAMS)
            }

            OutputStreamWriter(connection.outputStream).use { it.write(jsonBody.toString()) }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val rootObj = JSONObject(responseText)
                val tabs = rootObj.optJSONObject("contents")
                    ?.optJSONObject("tabbedSearchResultsRenderer")
                    ?.optJSONArray("tabs")
                val sectionList = tabs?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                if (sectionList != null) {
                    var fallbackCandidateWithoutAlbum: Pair<String, JSONObject?>? = null

                    for (s in 0 until sectionList.length()) {
                        val sec = sectionList.optJSONObject(s) ?: continue
                        val items = sec.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                            ?: sec.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                            ?: continue

                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            val vid = extractVideoId(renderer) ?: continue
                            if (vid == excludeVid || blacklistedVideoIds.contains(vid)) continue

                            val candTitle = extractTitle(renderer)
                            val candArtist = extractArtist(renderer)
                            val candAlbum = extractAlbum(renderer)

                            // 1. Filter ketat anti-cover dan anti-live:
                            // Membuang cover, acoustic cover, live, concert, session, karaoke, tribute, parodi, bootleg
                            if (!MetadataSanitizer.isOfficialStudioTrack(candTitle, candArtist, candAlbum)) {
                                continue
                            }

                            if (isForbiddenVideo(candTitle, candAlbum)) {
                                continue
                            }

                            // 2. Jika pencarian dengan ISRC: hasil filter Songs yang lolos filter studio adalah master audio resmi
                            if (isIsrcSearch) {
                                cacheCardThumbnail(renderer.optJSONObject("thumbnail"), vid)
                                return vid
                            }

                            // 2b. Validasi Judul: pastikan judul lagu cocok (mencegah tertukar dengan lagu lain dari artis yang sama)
                            if (!expectedTitle.isNullOrBlank()) {
                                if (!MusicStreamResolver.isTitleMatching(expectedTitle, candTitle)) {
                                    continue
                                }
                            }

                            val candDuration = extractDurationSeconds(renderer)

                            // 3. Validasi Artis: pastikan nama musisi/artis cocok
                            if (!expectedArtist.isNullOrBlank() && candArtist.isNotBlank()) {
                                val cleanExpected = expectedArtist.lowercase().trim()
                                val cleanCand = candArtist.lowercase().trim()
                                val artistMatches = cleanCand.contains(cleanExpected) ||
                                        cleanExpected.contains(cleanCand) ||
                                        cleanExpected.split(",", "&", "feat", "ft", "/").any { part ->
                                            val p = part.trim()
                                            p.length >= 3 && cleanCand.contains(p)
                                        }
                                if (!artistMatches) continue
                            }

                            // 4. Validasi Durasi: selisih durasi maksimal dari durasi asli Spotify
                            if (expectedDurationSeconds != null && expectedDurationSeconds > 0 && candDuration > 0) {
                                val delta = kotlin.math.abs(candDuration - expectedDurationSeconds)
                                if (delta > maxDurationDiffSeconds) continue
                            }

                            // 5. Prioritaskan trek yang memiliki metadata album studio resmi
                            if (candAlbum.isNotBlank()) {
                                cacheCardThumbnail(renderer.optJSONObject("thumbnail"), vid)
                                return vid
                            } else if (fallbackCandidateWithoutAlbum == null) {
                                fallbackCandidateWithoutAlbum = Pair(vid, renderer.optJSONObject("thumbnail"))
                            }
                        }
                    }

                    // Jika tidak ada trek dengan metadata album resmi, gunakan kandidat cadangan pertama yang lolos verifikasi
                    if (fallbackCandidateWithoutAlbum != null) {
                        val (fVid, fThumb) = fallbackCandidateWithoutAlbum
                        cacheCardThumbnail(fThumb, fVid)
                        return fVid
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun extractAlbum(renderer: JSONObject): String {
        val flexCols = renderer.optJSONArray("flexColumns") ?: return ""
        if (flexCols.length() > 2) {
            val runs = flexCols.optJSONObject(2)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            return runs?.optJSONObject(0)?.optString("text").orEmpty()
        }
        return ""
    }

    private fun extractArtist(renderer: JSONObject): String {
        val flexCols = renderer.optJSONArray("flexColumns") ?: return ""
        if (flexCols.length() > 1) {
            val runs = flexCols.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs") ?: return ""
            val artists = mutableListOf<String>()
            for (i in 0 until runs.length()) {
                val runObj = runs.optJSONObject(i) ?: continue
                val text = runObj.optString("text", "").trim()
                if (text.isNotBlank() && text != "•" && !text.contains(":") && !text.equals("Song", ignoreCase = true)) {
                    artists.add(text)
                }
            }
            return artists.joinToString(", ")
        }
        return ""
    }

    private fun extractVideoId(renderer: JSONObject): String? {
        val vid = renderer.optJSONObject("playlistItemData")?.optString("videoId")
            ?: renderer.optJSONArray("menu")?.optJSONObject(0)?.optString("videoId")
            ?: renderer.optJSONArray("flexColumns")?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
            ?: renderer.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
        return vid?.takeIf { it.length == 11 }
    }

    private fun extractTitle(renderer: JSONObject): String {
        return renderer.optJSONArray("flexColumns")?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
    }

    private fun cacheCardThumbnail(thumbnailObj: JSONObject?, videoId: String) {
        if (thumbnailObj == null || videoId.isBlank()) return
        val thumbs = thumbnailObj.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        if (thumbs != null && thumbs.length() > 0) {
            val lastUrl = thumbs.getJSONObject(thumbs.length() - 1).optString("url")
            if (lastUrl.isNotBlank()) {
                val formatted = if (lastUrl.contains("googleusercontent.com")) {
                    lastUrl.replace(Regex("""=w\d+-h\d+.*"""), "=w800-h800-l90-rj")
                } else if (lastUrl.contains("i.ytimg.com") && !lastUrl.contains("hqdefault") && !lastUrl.contains("maxresdefault")) {
                    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                } else {
                    lastUrl
                }
                cacheThumbnail(videoId, formatted)
            }
        }
    }

    private fun parseDurationToSeconds(text: String): Int {
        val parts = text.trim().split(":")
        return when (parts.size) {
            2 -> {
                val min = parts[0].toIntOrNull() ?: 0
                val sec = parts[1].toIntOrNull() ?: 0
                min * 60 + sec
            }
            3 -> {
                val hr = parts[0].toIntOrNull() ?: 0
                val min = parts[1].toIntOrNull() ?: 0
                val sec = parts[2].toIntOrNull() ?: 0
                hr * 3600 + min * 60 + sec
            }
            else -> 0
        }
    }

    private fun extractDurationSeconds(renderer: JSONObject): Int {
        val fixedCols = renderer.optJSONArray("fixedColumns")
        if (fixedCols != null && fixedCols.length() > 0) {
            val durText = fixedCols.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            if (durText.contains(":")) {
                val sec = parseDurationToSeconds(durText)
                if (sec > 0) return sec
            }
        }
        val flexCols = renderer.optJSONArray("flexColumns")
        if (flexCols != null && flexCols.length() > 1) {
            val runs = flexCols.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            if (runs != null) {
                for (r in runs.length() - 1 downTo 0) {
                    val t = runs.optJSONObject(r)?.optString("text").orEmpty()
                    if (t.contains(":") && t.length in 3..8) {
                        val sec = parseDurationToSeconds(t)
                        if (sec > 0) return sec
                    }
                }
            }
        }
        return 0
    }

    fun isTitleMatching(expectedSpotifyTitle: String, candidateTitle: String): Boolean {
        return MusicStreamResolver.isTitleMatching(expectedSpotifyTitle, candidateTitle)
    }

    fun cleanTitleForMatching(title: String): String {
        return MusicStreamResolver.cleanTitleForMatching(title)
    }

    fun extractPrimaryKeywords(title: String): List<String> {
        return MusicStreamResolver.extractPrimaryKeywords(title)
    }
}
