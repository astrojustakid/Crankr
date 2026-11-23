package com.example.spotifyclone.service

import android.util.Log
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.data.repository.YouTubeMusicRepository
import com.example.spotifyclone.util.MetadataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RawSpotifyTrackInfo(
    val title: String,
    val artist: String,
    val album: String = ""
)

data class MatchedTrackResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int,
    val coverUrl: String
)

/**
 * YouTubeTrackMatcher:
 * Mencocokkan lagu (judul dan artis) dari ekstraksi teks Spotify ke trek resmi YouTube Music
 * menggunakan arsitektur multi-tier:
 * 1. Piped API (Filter music_songs)
 * 2. Invidious API (Type video)
 * 3. YouTube Music InnerTube Search (Ironclad fallback)
 */
object YouTubeTrackMatcher {

    private const val TAG = "YouTubeTrackMatcher"

    private val PIPED_INSTANCES = listOf(
        "https://api.piped.privacydev.net",
        "https://pipedapi.tokhmi.xyz",
        "https://piped-api.lunar.icu"
    )

    private val INVIDIOUS_INSTANCES = listOf(
        "https://inv.nadeko.net",
        "https://yewtu.be",
        "https://invidious.privacydev.net"
    )

    private val youTubeMusicRepository by lazy { YouTubeMusicRepository() }

    /**
     * Cocokkan satu lagu ke YouTube Music menggunakan InnerTube, AudioStreamResolver, Piped, atau Invidious.
     */
    suspend fun matchTrack(title: String, artist: String): MatchedTrackResult? = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() && cleanArtist.isBlank()) return@withContext null

        val query = "$cleanTitle $cleanArtist".trim()

        // 1. Prioritas Utama: YouTube Music InnerTube Search (Direct, Official, dan Paling Cepat)
        val innerTubeResult = searchInnerTube(query, cleanTitle, cleanArtist)
        if (innerTubeResult != null) {
            Log.d(TAG, "InnerTube matched: '$query' -> ${innerTubeResult.videoId}")
            return@withContext innerTubeResult
        }

        // 1b. Coba kueri alternatif "$artist $title" jika judul-artis belum menemukan hasil
        val altQuery = "$cleanArtist $cleanTitle".trim()
        val altInnerTubeResult = searchInnerTube(altQuery, cleanTitle, cleanArtist)
        if (altInnerTubeResult != null) {
            Log.d(TAG, "InnerTube alt matched: '$altQuery' -> ${altInnerTubeResult.videoId}")
            return@withContext altInnerTubeResult
        }

        // 1c. Coba dengan judul ternormalisasi (buang noise seperti Remastered, Bonus Track, dll)
        val normalizedTitle = MetadataSanitizer.normalizeTitle(cleanTitle, cleanArtist)
        if (normalizedTitle.isNotBlank() && !normalizedTitle.equals(cleanTitle, ignoreCase = true)) {
            val normQuery = "$normalizedTitle $cleanArtist".trim()
            val normResult = searchInnerTube(normQuery, normalizedTitle, cleanArtist)
            if (normResult != null) {
                Log.d(TAG, "InnerTube normalized matched: '$normQuery' -> ${normResult.videoId}")
                return@withContext normResult
            }
        }

        // 2. AudioStreamResolver (Mesin Pencari & Pemutar Resmi Crankr)
        // Menjamin jika lagu dapat diputar oleh Player, maka lagu tersebut PASTI cocok saat impor
        val audioStreamVid = AudioStreamResolver.resolveYouTubeVideoId(cleanTitle, cleanArtist)
            ?: (if (normalizedTitle.isNotBlank() && !normalizedTitle.equals(cleanTitle, ignoreCase = true)) {
                AudioStreamResolver.resolveYouTubeVideoId(normalizedTitle, cleanArtist)
            } else null)
            ?: MusicStreamResolver.resolveYouTubeVideoId(cleanTitle, cleanArtist)

        if (!audioStreamVid.isNullOrBlank()) {
            Log.d(TAG, "AudioStreamResolver matched: '$query' -> $audioStreamVid")
            return@withContext MatchedTrackResult(
                videoId = audioStreamVid,
                title = cleanTitle,
                artist = cleanArtist,
                durationSeconds = 210,
                coverUrl = MusicStreamResolver.getThumbnailForVideo(audioStreamVid)
            )
        }

        // 3. Cadangan Piped API
        val pipedResult = searchPiped(query, cleanTitle, cleanArtist)
        if (pipedResult != null) {
            Log.d(TAG, "Piped matched: '$query' -> ${pipedResult.videoId}")
            return@withContext pipedResult
        }

        // 4. Cadangan Invidious API
        val invidiousResult = searchInvidious(query, cleanTitle, cleanArtist)
        if (invidiousResult != null) {
            Log.d(TAG, "Invidious matched: '$query' -> ${invidiousResult.videoId}")
            return@withContext invidiousResult
        }

        Log.w(TAG, "No match found for: '$query'")
        null
    }

    private fun searchPiped(query: String, expectedTitle: String, expectedArtist: String): MatchedTrackResult? {
        val encoded = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (_: Exception) {
            return null
        }

        for (instance in PIPED_INSTANCES) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$instance/search?q=$encoded&filter=music_songs")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 3500
                    readTimeout = 3500
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val items = if (body.trim().startsWith("[")) {
                        JSONArray(body)
                    } else {
                        val obj = JSONObject(body)
                        obj.optJSONArray("items") ?: obj.optJSONArray("results")
                    } ?: continue

                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val rawUrl = item.optString("url", "")
                        val vid = when {
                            rawUrl.contains("v=") -> rawUrl.substringAfter("v=").take(11)
                            rawUrl.startsWith("/watch?v=") -> rawUrl.removePrefix("/watch?v=").take(11)
                            else -> item.optString("id", "").take(11)
                        }
                        if (vid.length == 11 && vid.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                            val itemTitle = item.optString("title", query)
                            val itemArtist = item.optString("uploaderName", "")
                            if (!MetadataSanitizer.isOfficialStudioTrack(itemTitle, itemArtist)) continue
                            if (!MusicStreamResolver.isTitleMatching(expectedTitle, itemTitle)) continue

                            val duration = item.optInt("duration", 210)
                            if (duration > 480) continue // Tolak podcast / video berdurasi panjang
                            val thumb = item.optString("thumbnail", "")
                            return MatchedTrackResult(
                                videoId = vid,
                                title = itemTitle,
                                artist = itemArtist,
                                durationSeconds = if (duration > 0) duration else 210,
                                coverUrl = formatThumbnailUrl(thumb)
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Lanjut ke instance berikutnya jika ada kegagalan jaringan
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun searchInvidious(query: String, expectedTitle: String, expectedArtist: String): MatchedTrackResult? {
        val encoded = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (_: Exception) {
            return null
        }

        for (instance in INVIDIOUS_INSTANCES) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$instance/api/v1/search?q=$encoded&type=video")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 3500
                    readTimeout = 3500
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val items = JSONArray(body)
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val vid = item.optString("videoId", "")
                        if (vid.length == 11 && vid.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                            val itemTitle = item.optString("title", query)
                            val itemArtist = item.optString("author", "")
                            if (!MetadataSanitizer.isOfficialStudioTrack(itemTitle, itemArtist)) continue
                            if (!MusicStreamResolver.isTitleMatching(expectedTitle, itemTitle)) continue

                            val duration = item.optInt("lengthSeconds", 210)
                            if (duration > 480) continue // Tolak podcast / video berdurasi panjang
                            val thumbs = item.optJSONArray("videoThumbnails")
                            val thumb = thumbs?.optJSONObject(0)?.optString("url") ?: ""
                            return MatchedTrackResult(
                                videoId = vid,
                                title = itemTitle,
                                artist = itemArtist,
                                durationSeconds = if (duration > 0) duration else 210,
                                coverUrl = formatThumbnailUrl(thumb)
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Lanjut ke instance berikutnya jika ada kegagalan jaringan
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private suspend fun searchInnerTube(query: String, expectedTitle: String, expectedArtist: String): MatchedTrackResult? {
        return try {
            val songs = youTubeMusicRepository.searchSongs(query, limit = 10)
            val normalizedExpected = MetadataSanitizer.normalizeTitle(expectedTitle, expectedArtist)
            val candidate = songs.firstOrNull {
                val vid = it.youtubeVideoId
                vid != null &&
                vid.length == 11 &&
                (MusicStreamResolver.isTitleMatching(expectedTitle, it.title) ||
                 (normalizedExpected.isNotBlank() && MusicStreamResolver.isTitleMatching(normalizedExpected, it.title)) ||
                 it.title.contains(expectedTitle, ignoreCase = true) ||
                 (normalizedExpected.isNotBlank() && it.title.contains(normalizedExpected, ignoreCase = true)))
            }
            if (candidate != null && candidate.youtubeVideoId != null) {
                MatchedTrackResult(
                    videoId = candidate.youtubeVideoId,
                    title = candidate.title,
                    artist = candidate.artist,
                    album = candidate.album,
                    durationSeconds = candidate.durationSeconds,
                    coverUrl = candidate.coverUrl
                )
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube search fallback error: ${e.message}")
            null
        }
    }

    private fun formatThumbnailUrl(url: String): String {
        if (url.isBlank()) return ""
        return if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            url.replace(Regex("=w\\d+-h\\d+.*"), "=w800-h800-l90-rj")
                .replace(Regex("=s\\d+.*"), "=s800")
        } else {
            url
        }
    }

    /**
     * Mencocokkan daftar teks trek Spotify ke trek resmi YouTube secara konkuren.
     */
    suspend fun matchTracksConcurrently(
        rawTracks: List<RawSpotifyTrackInfo>,
        playlistName: String,
        fallbackCoverUrl: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<Track> = withContext(Dispatchers.IO) {
        if (rawTracks.isEmpty()) return@withContext emptyList()

        val total = rawTracks.size
        val completedCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val semaphore = Semaphore(6)
        val deferred = rawTracks.mapIndexed { index, raw ->
            async {
                semaphore.withPermit {
                    val matched = matchTrack(raw.title, raw.artist)
                    val current = completedCounter.incrementAndGet()
                    onProgress?.invoke(current, total)

                    val baseCover = fallbackCoverUrl.ifBlank {
                        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=60"
                    }

                    val resolvedAlbum = raw.album.ifBlank {
                        matched?.album?.ifBlank { "Single" } ?: "Single"
                    }

                    if (matched != null) {
                        Track(
                            id = "yt_${matched.videoId}",
                            title = raw.title.ifBlank { matched.title },
                            artist = raw.artist.ifBlank { matched.artist },
                            album = resolvedAlbum,
                            durationSeconds = if (matched.durationSeconds > 0) matched.durationSeconds else 210,
                            streamUrl = "https://music.youtube.com/watch?v=${matched.videoId}",
                            coverUrl = if (matched.coverUrl.isNotBlank()) matched.coverUrl else baseCover,
                            genre = "Pop Punk",
                            youtubeVideoId = matched.videoId,
                            addedAt = System.currentTimeMillis() - (rawTracks.size - index) * 1000L
                        )
                    } else {
                        // Fallback jika tidak ada kecocokan
                        val placeholderVid = "c_${System.currentTimeMillis()}_$index"
                        Track(
                            id = "yt_$placeholderVid",
                            title = raw.title,
                            artist = raw.artist,
                            album = resolvedAlbum,
                            durationSeconds = 210,
                            streamUrl = "",
                            coverUrl = baseCover,
                            genre = "Pop Punk",
                            youtubeVideoId = null,
                            addedAt = System.currentTimeMillis() - (rawTracks.size - index) * 1000L
                        )
                    }
                }
            }
        }
        deferred.awaitAll()
    }
}

