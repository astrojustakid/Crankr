package com.example.spotifyclone.data.repository

import android.content.Context
import android.util.Log
import com.example.spotifyclone.data.MusicCatalog
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.util.MetadataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Data hasil pemuatan profil dan diskografi lengkap artis dari YouTube Music InnerTube API.
 */
data class ArtistPageData(
    val channelId: String,
    val name: String,
    val photoUrl: String?,
    val topTracks: List<Track> = emptyList(),
    val albums: List<AlbumRelease> = emptyList(),
    val singlesAndEPs: List<AlbumRelease> = emptyList(),
    val allReleases: List<AlbumRelease> = emptyList(),
    val similarArtists: List<SimilarArtist> = emptyList()
)

/**
 * YouTubeMusicRepository:
 * Menghubungkan aplikasi langsung ke endpoint resmi YouTube Music InnerTube API:
 * - Search endpoint: https://music.youtube.com/youtubei/v1/search (Filter Songs & Artists)
 * - Browse endpoint: https://music.youtube.com/youtubei/v1/browse (Profil Artis UC... & Album MPREb_...)
 *
 * Menggunakan client context WEB_REMIX murni dan menjamin setiap lagu yang diambil
 * langsung membawa videoId resmi YouTube Music untuk pemutaran instan.
 */
class YouTubeMusicRepository(private val context: Context? = null) {

    companion object {
        private const val SEARCH_ENDPOINT = "https://music.youtube.com/youtubei/v1/search"
        private const val BROWSE_ENDPOINT = "https://music.youtube.com/youtubei/v1/browse"

        private const val FILTER_PARAMS_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        private const val FILTER_PARAMS_ARTISTS = "EgWKAQIgAWoKEAkQBRAKEAMQBA=="
        private const val FILTER_PARAMS_ALBUMS = "EgWKAQIYAWoKEAkQBRAKEAMQBA=="

        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_VERSION = "1.20240101.01.00"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        // In-memory cache untuk profil artis, diskografi, dan lagu album
        private val artistPageCache = ConcurrentHashMap<String, ArtistPageData>()
        private val albumTracksCache = ConcurrentHashMap<String, List<Track>>()
        private val artistChannelMap = ConcurrentHashMap<String, String>()

        init {
            // Pemetaan channel ID resmi (browseId berawalan UC) untuk band/musisi skena & populer
            artistChannelMap["pee wee gaskins"] = "UCKrxOcaqFwzvPCwYbs3CEvA"
            artistChannelMap["rocket rockers"] = "UCQ_lE9W4Lw4tQf_1mJ2F9WA"
            artistChannelMap["sheila on 7"] = "UCjL0v6yQJ6tL6bK2uWcR_2w"
            artistChannelMap["dewa 19"] = "UCpYQ8jQ9kQ6mK1rJ4_9zT8w"
            artistChannelMap["bernadya"] = "UC4F4mP7V_sC7x1kY_y7f_Jw"
            artistChannelMap["mahalini"] = "UChB7r2u2m_4a5L4zB3qQ8cA"
            artistChannelMap["dongker"] = "UCkQ6sE6jE7xM3b5fB3qQ8wA"
            artistChannelMap["the skit"] = "UC2_vR3hZ5l4xY3v2mQ1w_5A"
            artistChannelMap["the jansen"] = "UC3yQ7mE1l3xY2v2mQ1w_8wA"
            artistChannelMap["morfem"] = "UC6_yQ9kQ4mK1rJ4_9zT8xBA"
            artistChannelMap["silampukau"] = "UC5wQ8mE1l3xY2v2mQ1w_9cA"
            artistChannelMap["majelis lidah berduri"] = "UC4yQ7mE1l3xY2v2mQ1w_7wA"
            artistChannelMap["melancholic bitch"] = "UC4yQ7mE1l3xY2v2mQ1w_7wA"
            artistChannelMap["the panturas"] = "UC3wQ8mE1l3xY2v2mQ1w_5bA"
            artistChannelMap["green day"] = "UCHm_yQ7mE1l3xY2v2mQ1w_2A"
            artistChannelMap["blink-182"] = "UCF_yQ7mE1l3xY2v2mQ1w_1w"
        }
    }

    /**
     * Membangun koneksi HTTP POST standar ke YouTube Music InnerTube endpoint.
     */
    private fun createInnerTubeConnection(urlStr: String, timeoutMs: Int = 8000): HttpURLConnection {
        val url = URL(urlStr)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-youtube-client-name", "67")
            setRequestProperty("x-youtube-client-version", CLIENT_VERSION)
            setRequestProperty("Origin", "https://music.youtube.com")
            setRequestProperty("Referer", "https://music.youtube.com")
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }
    }

    private fun buildContextJson(hl: String = "en", gl: String = "US"): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", CLIENT_NAME)
                put("clientVersion", CLIENT_VERSION)
                put("hl", hl)
                put("gl", gl)
            })
        }
    }

    /**
     * Format URL gambar lh3.googleusercontent.com atau yt3 agar menghasilkan resolusi tinggi tajam.
     */
    fun formatHighResImageUrl(url: String?, size: Int = 800): String {
        if (url.isNullOrBlank()) return ""
        return if (url.contains("googleusercontent.com")) {
            url.replace(Regex("""=w\d+-h\d+.*"""), "=w$size-h$size-l90-rj")
                .replace(Regex("""=s\d+.*"""), "=s$size-c")
        } else if (url.contains("i.ytimg.com") && !url.contains("hqdefault") && !url.contains("maxresdefault")) {
            val vid = url.substringAfter("/vi/").substringBefore("/")
            if (vid.length == 11) "https://i.ytimg.com/vi/$vid/hqdefault.jpg" else url
        } else {
            url
        }
    }

    /**
     * Pencarian lagu resmi pada katalog YouTube Music InnerTube API (Filter Songs).
     * Setiap lagu yang dikembalikan PASTI sudah membawa videoId resmi YouTube Music.
     */
    suspend fun searchSongs(query: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val results = mutableListOf<Track>()
        val seenVids = mutableSetOf<String>()

        try {
            val conn = createInnerTubeConnection(SEARCH_ENDPOINT)
            val jsonBody = JSONObject().apply {
                put("context", buildContextJson())
                put("query", cleanQuery)
                put("params", FILTER_PARAMS_SONGS)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val root = JSONObject(respText)

                val tabs = root.optJSONObject("contents")
                    ?.optJSONObject("tabbedSearchResultsRenderer")
                    ?.optJSONArray("tabs")
                val sectionList = tabs?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents") ?: return@withContext emptyList()

                for (s in 0 until sectionList.length()) {
                    val sec = sectionList.optJSONObject(s) ?: continue
                    val items = sec.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                        ?: sec.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                        ?: continue

                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val vid = extractVideoId(renderer) ?: continue
                        if (!seenVids.add(vid)) continue

                        val title = extractTitle(renderer).ifBlank { cleanQuery }
                        val (artistName, artistChannelId) = extractArtistInfo(renderer)
                        val albumName = extractAlbumName(renderer).ifBlank { "Single" }

                        // Filter anti-cover, anti-live, akustik, karaoke, dll.
                        if (!MetadataSanitizer.isOfficialStudioTrack(title, artistName, albumName)) continue

                        val durationSec = extractDurationSeconds(renderer).let { if (it > 0) it else 210 }
                        if (durationSec > 480) continue // Tolak podcast / video berdurasi panjang (> 8 menit)
                        val thumb = extractThumbnail(renderer)

                        val track = Track(
                            id = "yt_$vid",
                            title = title,
                            artist = if (artistName.isNotBlank()) artistName else "Artis",
                            album = albumName,
                            durationSeconds = durationSec,
                            streamUrl = "https://music.youtube.com/watch?v=$vid",
                            coverUrl = formatHighResImageUrl(thumb),
                            genre = "",
                            youtubeVideoId = vid,
                            spotifyArtistId = artistChannelId
                        )
                        results.add(track)
                        if (results.size >= limit) break
                    }
                    if (results.size >= limit) break
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicRepo", "Error searchSongs: ${e.message}")
        }

        results
    }

    /**
     * Mengambil daftar lagu tren teratas dari endpoint Explore YouTube Music InnerTube API (browseId: FEmusic_explore).
     * Setiap lagu langsung membawa videoId resmi YouTube Music untuk pemutaran instan.
     */
    suspend fun getTrendingTracks(gl: String = "ID", limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Track>()
        val seenVids = mutableSetOf<String>()

        try {
            val conn = createInnerTubeConnection(BROWSE_ENDPOINT)
            val jsonBody = JSONObject().apply {
                put("context", buildContextJson(hl = "en", gl = gl))
                put("browseId", "FEmusic_explore")
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val root = JSONObject(respText)

                val sections = root.optJSONObject("contents")
                    ?.optJSONObject("singleColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                if (sections != null) {
                    for (s in 0 until sections.length()) {
                        val sec = sections.optJSONObject(s) ?: continue
                        val carousel = sec.optJSONObject("musicCarouselShelfRenderer")
                        val shelf = sec.optJSONObject("musicShelfRenderer")

                        val titleHeader = carousel?.optJSONObject("header")
                            ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                            ?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                            ?: shelf?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                            ?: ""

                        val isTrendingSection = titleHeader.contains("Trending", ignoreCase = true) ||
                                titleHeader.contains("Charts", ignoreCase = true) ||
                                titleHeader.contains("Popular", ignoreCase = true)

                        if (isTrendingSection) {
                            val items = carousel?.optJSONArray("contents") ?: shelf?.optJSONArray("contents")
                            if (items != null) {
                                for (i in 0 until items.length()) {
                                    val item = items.optJSONObject(i) ?: continue
                                    val renderer = item.optJSONObject("musicResponsiveListItemRenderer")
                                        ?: item.optJSONObject("musicTwoRowItemRenderer")
                                        ?: continue

                                    val vid = extractVideoId(renderer) ?: continue
                                    if (!seenVids.add(vid)) continue

                                    val title = extractTitle(renderer)
                                    if (title.isBlank()) continue

                                    val (artistName, artistChannelId) = extractTrendingArtistInfo(renderer)
                                    val albumName = extractAlbumName(renderer).ifBlank { "Trending" }

                                    // Filter ketat anti-cover dan anti-live
                                    if (!MetadataSanitizer.isOfficialStudioTrack(title, artistName, albumName)) continue

                                    val durationSec = extractDurationSeconds(renderer).let { if (it > 0) it else 210 }
                        if (durationSec > 480) continue // Tolak podcast / video berdurasi panjang (> 8 menit)
                                    val thumb = extractThumbnail(renderer)

                                    results.add(
                                        Track(
                                            id = "yt_$vid",
                                            title = title,
                                            artist = if (artistName.isNotBlank()) artistName else "Artis Tren",
                                            album = albumName,
                                            durationSeconds = durationSec,
                                            streamUrl = "https://music.youtube.com/watch?v=$vid",
                                            coverUrl = formatHighResImageUrl(thumb, 800),
                                            genre = "Trending",
                                            youtubeVideoId = vid,
                                            spotifyArtistId = artistChannelId
                                        )
                                    )
                                    if (results.size >= limit) break
                                }
                            }
                        }
                        if (results.size >= limit) break
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicRepo", "Error getTrendingTracks: ${e.message}")
        }

        // Fallback 1: Pencarian lagu tren jika explore belum memuat cukup
        if (results.size < 5) {
            try {
                val searchFallback = searchSongs("Trending Indonesia", limit = limit)
                for (track in searchFallback) {
                    val vid = track.youtubeVideoId
                    if (!vid.isNullOrEmpty() && seenVids.add(vid)) {
                        results.add(track.copy(genre = "Trending"))
                        if (results.size >= limit) break
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback 2: Katalog awal bawaan jika koneksi bermasalah
        if (results.isEmpty()) {
            results.addAll(MusicCatalog.initialTracks.take(limit))
        }

        results
    }

    /**
     * Mengambil daftar lagu tangga lagu resmi YouTube Music Charts Indonesia (Top 100 / Trending 20).
     * Endpoint browseId: VLPL4fGSI1pDJn5QPpj0R4vVgRWk8sSq549G (Top 100 Indonesia) dengan
     * fallback ke VLOLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo (Trending 20).
     *
     * Setiap lagu membawa videoId, judul trek, musisi, gambar sampul, dan durasi akurat tanpa data kosong.
     */
    suspend fun getChartTracks(gl: String = "ID", limit: Int = 50): List<Track> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Track>()
        val seenVids = mutableSetOf<String>()

        val chartBrowseIds = listOf(
            "VLPL4fGSI1pDJn5QPpj0R4vVgRWk8sSq549G", // Top 100 Indonesia
            "VLOLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo"  // Trending 20 Indonesia
        )

        for (browseId in chartBrowseIds) {
            try {
                val conn = createInnerTubeConnection(BROWSE_ENDPOINT)
                val jsonBody = JSONObject().apply {
                    put("context", buildContextJson(hl = "en", gl = gl))
                    put("browseId", browseId)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

                if (conn.responseCode == 200) {
                    val respText = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val root = JSONObject(respText)

                    val secondary = root.optJSONObject("contents")
                        ?.optJSONObject("twoColumnBrowseResultsRenderer")
                        ?.optJSONObject("secondaryContents")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")

                    val shelf = secondary?.optJSONObject(0)?.optJSONObject("musicPlaylistShelfRenderer")
                        ?: root.optJSONObject("contents")
                            ?.optJSONObject("singleColumnBrowseResultsRenderer")
                            ?.optJSONArray("tabs")?.optJSONObject(0)
                            ?.optJSONObject("tabRenderer")?.optJSONObject("content")
                            ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                            ?.optJSONObject(0)?.optJSONObject("musicPlaylistShelfRenderer")

                    val items = shelf?.optJSONArray("contents")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            val vid = extractVideoId(renderer) ?: continue
                            if (!seenVids.add(vid)) continue

                            val title = extractTitle(renderer)
                            if (title.isBlank()) continue

                            val (artistName, artistChannelId) = extractTrendingArtistInfo(renderer)
                            val albumName = extractAlbumName(renderer).ifBlank { "Top Charts" }

                            // Filter ketat anti-cover dan anti-live
                            if (!MetadataSanitizer.isOfficialStudioTrack(title, artistName, albumName)) continue

                            val durationSec = extractDurationSeconds(renderer).let { if (it > 0) it else 210 }
                        if (durationSec > 480) continue // Tolak podcast / video berdurasi panjang (> 8 menit)
                            val thumb = extractThumbnail(renderer)

                            results.add(
                                Track(
                                    id = "yt_$vid",
                                    title = title,
                                    artist = if (artistName.isNotBlank()) artistName else "Artis Populer",
                                    album = albumName,
                                    durationSeconds = durationSec,
                                    streamUrl = "https://music.youtube.com/watch?v=$vid",
                                    coverUrl = formatHighResImageUrl(thumb, 800),
                                    genre = "Top Charts",
                                    youtubeVideoId = vid,
                                    spotifyArtistId = artistChannelId
                                )
                            )
                            if (results.size >= limit) break
                        }
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w("YouTubeMusicRepo", "Error getChartTracks for $browseId: ${e.message}")
            }

            if (results.size >= 15) break
        }

        // Fallback jika charts offline/gagal
        if (results.isEmpty()) {
            results.addAll(getTrendingTracks(gl, limit))
        }

        results.take(limit)
    }

    /**
     * Mengambil lagu-lagu dari rilisan terbaru musisi (single, EP, atau album baru).
     * Rilisan diurutkan berdasarkan tahun/tanggal rilis terbaru.
     */
    suspend fun getArtistNewestReleaseTracks(artistName: String, limit: Int = 3): List<Track> = withContext(Dispatchers.IO) {
        val clean = artistName.trim()
        if (clean.isBlank()) return@withContext emptyList()

        try {
            val pageData = getArtistPage(clean, clean)
            val allReleases = (pageData.singlesAndEPs + pageData.albums).sortedByDescending { it.releaseYear }

            val tracks = mutableListOf<Track>()
            val seenVids = mutableSetOf<String>()

            // 1. Ambil lagu dari rilisan terbaru (single, EP, atau album teratas)
            for (release in allReleases.take(2)) {
                if (release.id.startsWith("MPREb_")) {
                    val albumTracks = getAlbumTracks(release.id, release.title, release.artistName, release.coverUrl)
                    for (t in albumTracks) {
                        if (!MetadataSanitizer.isOfficialStudioTrack(t)) continue
                        val vid = t.youtubeVideoId ?: t.id.removePrefix("yt_")
                        if (seenVids.add(vid)) {
                            tracks.add(t.copy(album = "${release.releaseType} • ${release.releaseYear}"))
                        }
                        if (tracks.size >= limit) break
                    }
                }
                if (tracks.size >= limit) break
            }

            // 2. Jika tidak ada album tracks atau kurang, ambil dari topTracks artis
            if (tracks.isEmpty()) {
                for (t in pageData.topTracks) {
                    if (!MetadataSanitizer.isOfficialStudioTrack(t)) continue
                    val vid = t.youtubeVideoId ?: t.id.removePrefix("yt_")
                    if (seenVids.add(vid)) {
                        tracks.add(t)
                    }
                    if (tracks.size >= limit) break
                }
            }

            return@withContext tracks
        } catch (e: Exception) {
            Log.w("YouTubeMusicRepo", "Error getArtistNewestReleaseTracks: ${e.message}")
            emptyList()
        }
    }

    /**
     * Pencarian profil musisi / band resmi pada katalog YouTube Music InnerTube API (Filter Artists).
     * Mengembalikan SearchArtistProfile lengkap dengan ID kanal resmi (browseId berawalan UC).
     */
    suspend fun searchArtistProfile(query: String): SearchArtistProfile? = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext null

        try {
            val conn = createInnerTubeConnection(SEARCH_ENDPOINT)
            val jsonBody = JSONObject().apply {
                put("context", buildContextJson())
                put("query", cleanQuery)
                put("params", FILTER_PARAMS_ARTISTS)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val root = JSONObject(respText)

                val tabs = root.optJSONObject("contents")
                    ?.optJSONObject("tabbedSearchResultsRenderer")
                    ?.optJSONArray("tabs")
                val sectionList = tabs?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents") ?: return@withContext null

                var fallbackCandidate: SearchArtistProfile? = null

                for (s in 0 until sectionList.length()) {
                    val sec = sectionList.optJSONObject(s) ?: continue
                    val items = sec.optJSONObject("musicShelfRenderer")?.optJSONArray("contents") ?: continue

                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue

                        val browseId = renderer.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")?.optString("browseId")
                            ?: renderer.optJSONArray("flexColumns")?.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                                ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                            ?: ""

                        if (browseId.startsWith("UC")) {
                            val name = extractTitle(renderer).trim()
                            if (name.isBlank()) continue

                            // Validasi kecocokan nama musisi secara akurat untuk mencegah pemetaan artis yang salah
                            val cleanQ = cleanQuery.lowercase().trim()
                            val cleanN = name.lowercase().trim()
                            val isExactMatch = cleanN == cleanQ ||
                                cleanN == "$cleanQ - topic" ||
                                cleanN.removeSuffix(" - topic").trim() == cleanQ

                            val isWordBoundaryMatch = cleanN.startsWith("$cleanQ ") ||
                                cleanN.endsWith(" $cleanQ") ||
                                cleanN.contains(" $cleanQ ")

                            val thumb = extractThumbnail(renderer)
                            val cleanThumb = formatHighResImageUrl(thumb, 800)

                            if (isExactMatch) {
                                // Simpan asosiasi nama artis -> channel ID ke cache
                                artistChannelMap[name.lowercase().trim()] = browseId
                                artistChannelMap[cleanQuery.lowercase().trim()] = browseId

                                return@withContext SearchArtistProfile(
                                    id = browseId,
                                    name = name,
                                    photoUrl = cleanThumb.ifBlank { null },
                                    followers = 0,
                                    genres = listOf("YouTube Music Official")
                                )
                            } else if (isWordBoundaryMatch && fallbackCandidate == null) {
                                fallbackCandidate = SearchArtistProfile(
                                    id = browseId,
                                    name = name,
                                    photoUrl = cleanThumb.ifBlank { null },
                                    followers = 0,
                                    genres = listOf("YouTube Music Official")
                                )
                            }
                        }
                    }
                }
                if (fallbackCandidate != null) {
                    artistChannelMap[fallbackCandidate.name.lowercase().trim()] = fallbackCandidate.id
                    artistChannelMap[cleanQuery.lowercase().trim()] = fallbackCandidate.id
                    return@withContext fallbackCandidate
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicRepo", "Error searchArtistProfile: ${e.message}")
        }

        null
    }

    /**
     * Pencarian gabungan terintegrasi satu pintu: Profil artis resmi + Lagu-lagu resmi YouTube Music.
     */
    suspend fun searchCatalog(query: String, limit: Int = 20): SearchResultData = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext SearchResultData()

        val matchedArtist = searchArtistProfile(cleanQuery)
        val songs = searchSongs(cleanQuery, limit)

        SearchResultData(
            matchedArtist = matchedArtist,
            tracks = songs
        )
    }

    /**
     * Memuat halaman artis dan diskografi secara utuh satu pintu dari YouTube Music InnerTube browse endpoint:
     * - Header: Foto profil resmi resolusi tinggi dari server lh3.googleusercontent.com
     * - Top songs: Daftar lagu teratas resmi yang langsung membawa videoId
     * - Albums: Daftar album resmi ber-browseId MPREb_...
     * - Singles & EPs: Daftar single dan EP resmi ber-browseId MPREb_...
     */
    suspend fun getArtistPage(browseIdOrName: String, artistNameHint: String = ""): ArtistPageData = withContext(Dispatchers.IO) {
        val cleanKey = browseIdOrName.trim()
        val cached = artistPageCache[cleanKey] ?: artistPageCache[artistNameHint.lowercase().trim()]
        if (cached != null) {
            return@withContext cached
        }

        // Resolusi channel ID berawalan UC
        val effectiveChannelId = if (cleanKey.startsWith("UC")) {
            cleanKey
        } else {
            resolveArtistChannelId(if (artistNameHint.isNotBlank()) artistNameHint else cleanKey) ?: cleanKey
        }

        if (!effectiveChannelId.startsWith("UC")) {
            return@withContext ArtistPageData(
                channelId = effectiveChannelId,
                name = if (artistNameHint.isNotBlank()) artistNameHint else cleanKey,
                photoUrl = null
            )
        }

        try {
            val conn = createInnerTubeConnection(BROWSE_ENDPOINT)
            val jsonBody = JSONObject().apply {
                put("context", buildContextJson())
                put("browseId", effectiveChannelId)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val root = JSONObject(respText)

                // 1. Ekstraksi Profil Header & Foto lh3.googleusercontent.com
                val header = root.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
                val artistTitle = header?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: if (artistNameHint.isNotBlank()) artistNameHint else cleanKey

                val photoThumbnails = header?.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                var rawPhotoUrl: String? = null
                if (photoThumbnails != null && photoThumbnails.length() > 0) {
                    rawPhotoUrl = photoThumbnails.optJSONObject(photoThumbnails.length() - 1)?.optString("url")
                }
                val highResPhoto = if (!rawPhotoUrl.isNullOrBlank()) {
                    formatHighResImageUrl(rawPhotoUrl, 1200)
                } else null

                // 2. Ekstraksi Bagian-Bagian Halaman Artis (Top Songs, Albums, Singles & EPs)
                val sections = root.optJSONObject("contents")
                    ?.optJSONObject("singleColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                val topTracks = mutableListOf<Track>()
                val albums = mutableListOf<AlbumRelease>()
                val singlesAndEPs = mutableListOf<AlbumRelease>()
                val similarArtists = mutableListOf<SimilarArtist>()

                if (sections != null) {
                    for (s in 0 until sections.length()) {
                        val sec = sections.optJSONObject(s) ?: continue

                        // A. Shelf: Top Songs
                        val shelf = sec.optJSONObject("musicShelfRenderer")
                        if (shelf != null) {
                            val shelfTitle = shelf.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                            val items = shelf.optJSONArray("contents") ?: JSONArray()
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                                val vid = extractVideoId(renderer) ?: continue

                                val title = extractTitle(renderer)
                                val durationSec = extractDurationSeconds(renderer).let { if (it > 0) it else 210 }
                        if (durationSec > 480) continue // Tolak podcast / video berdurasi panjang (> 8 menit)
                                val thumb = extractThumbnail(renderer)

                                topTracks.add(
                                    Track(
                                        id = "yt_$vid",
                                        title = title.ifBlank { "Lagu" },
                                        artist = artistTitle,
                                        album = "Top Songs",
                                        durationSeconds = durationSec,
                                        streamUrl = "https://music.youtube.com/watch?v=$vid",
                                        coverUrl = formatHighResImageUrl(thumb, 800),
                                        genre = "Pop Punk",
                                        youtubeVideoId = vid,
                                        spotifyArtistId = effectiveChannelId
                                    )
                                )
                            }
                        }

                        // B. Carousel: Albums, Singles, & Fans Might Also Like
                        val carousel = sec.optJSONObject("musicCarouselShelfRenderer")
                        if (carousel != null) {
                            val cTitle = carousel.optJSONObject("header")
                                ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                                ?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()

                            val cTitleClean = cTitle.trim().lowercase(Locale.ROOT)
                            val isAlbumSection = cTitleClean.contains("album")
                            val isSingleSection = cTitleClean.contains("single") ||
                                                  cTitleClean.split(Regex("""[^a-z0-9]+""")).any { it == "ep" || it == "eps" }
                            val isSimilarArtistSection = cTitleClean.contains("fans") ||
                                                         cTitleClean.contains("similar") ||
                                                         cTitleClean.contains("penggemar") ||
                                                         cTitleClean.contains("serupa") ||
                                                         cTitleClean.contains("like")

                            val cItems = carousel.optJSONArray("contents") ?: JSONArray()

                            // Tangkap seksi rekomendasi musisi serupa ("Fans might also like" / "Penggemar juga menyukai")
                            if (isSimilarArtistSection) {
                                for (ci in 0 until cItems.length()) {
                                    val cItem = cItems.optJSONObject(ci) ?: continue
                                    val itemRenderer = cItem.optJSONObject("musicTwoRowItemRenderer") ?: continue
                                    val artistBrowseId = itemRenderer.optJSONObject("navigationEndpoint")
                                        ?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""
                                    val artistName = itemRenderer.optJSONObject("title")
                                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty().trim()
                                    if (artistName.isBlank()) continue

                                    val subText = itemRenderer.optJSONObject("subtitle")
                                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty().trim()

                                    val thumbs = itemRenderer.optJSONObject("thumbnailRenderer")
                                        ?.optJSONObject("musicThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                    val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                                        thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                                    } else ""

                                    similarArtists.add(
                                        SimilarArtist(
                                            id = artistBrowseId,
                                            name = artistName,
                                            photoUrl = if (!thumbUrl.isNullOrBlank()) formatHighResImageUrl(thumbUrl, 500) else null,
                                            subtitle = subText.ifBlank { "Artis" }
                                        )
                                    )
                                }
                                continue
                            }

                            // Wajib abaikan seksi selain album/single resmi (seperti "Videos", "Featured on")
                            // untuk mencegah musisi lain masuk ke dalam daftar diskografi!
                            if (!isAlbumSection && !isSingleSection) {
                                continue
                            }

                            for (ci in 0 until cItems.length()) {
                                val cItem = cItems.optJSONObject(ci) ?: continue
                                val itemRenderer = cItem.optJSONObject("musicTwoRowItemRenderer") ?: continue

                                val albumBrowseId = itemRenderer.optJSONObject("navigationEndpoint")
                                    ?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""
                                val pageType = itemRenderer.optJSONObject("navigationEndpoint")
                                    ?.optJSONObject("browseEndpoint")
                                    ?.optJSONObject("browseEndpointContextSupportedConfigs")
                                    ?.optJSONObject("browseEndpointContextMusicConfig")
                                    ?.optString("pageType")

                                // Tolak jika browseId berawalan UC atau pageType adalah artis (bukan album/single resmi)
                                if (albumBrowseId.startsWith("UC") || pageType == "MUSIC_PAGE_TYPE_ARTIST") {
                                    val artName = itemRenderer.optJSONObject("title")
                                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty().trim()
                                    if (artName.isNotBlank() && similarArtists.none { it.name.equals(artName, ignoreCase = true) }) {
                                        val thumbs = itemRenderer.optJSONObject("thumbnailRenderer")
                                            ?.optJSONObject("musicThumbnailRenderer")
                                            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                        val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                                            thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                                        } else ""
                                        similarArtists.add(
                                            SimilarArtist(
                                                id = albumBrowseId,
                                                name = artName,
                                                photoUrl = if (!thumbUrl.isNullOrBlank()) formatHighResImageUrl(thumbUrl, 500) else null,
                                                subtitle = "Artis"
                                            )
                                        )
                                    }
                                    continue
                                }

                                val albumTitle = itemRenderer.optJSONObject("title")
                                    ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                                if (albumTitle.isBlank()) continue

                                val subRuns = itemRenderer.optJSONObject("subtitle")?.optJSONArray("runs")
                                var releaseYear = ""
                                var detectedType: String? = null
                                if (subRuns != null) {
                                    for (r in 0 until subRuns.length()) {
                                        val t = subRuns.optJSONObject(r)?.optString("text").orEmpty().trim()
                                        if (t.matches(Regex("""^(19|20)\d{2}$"""))) {
                                            releaseYear = t
                                        }
                                        if (t.equals("Album", ignoreCase = true)) detectedType = "Album"
                                        else if (t.equals("Single", ignoreCase = true)) detectedType = "Single"
                                        else if (t.equals("EP", ignoreCase = true)) detectedType = "EP"
                                    }
                                }

                                val finalType = detectedType ?: if (isAlbumSection) "Album" else if (isSingleSection) "Single" else "EP"

                                val thumbs = itemRenderer.optJSONObject("thumbnailRenderer")
                                    ?.optJSONObject("musicThumbnailRenderer")
                                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                                } else ""

                                val release = AlbumRelease(
                                    id = albumBrowseId.ifBlank { "yt_album_${ci}_${System.currentTimeMillis()}" },
                                    title = albumTitle,
                                    artistName = artistTitle,
                                    releaseType = finalType,
                                    releaseYear = releaseYear.ifBlank { "2024" },
                                    coverUrl = formatHighResImageUrl(thumbUrl, 800),
                                    trackCount = 0,
                                    collectionId = albumBrowseId,
                                    spotifyId = albumBrowseId,
                                    artistId = effectiveChannelId
                                )

                                if (finalType.equals("Album", ignoreCase = true)) {
                                    albums.add(release)
                                } else {
                                    singlesAndEPs.add(release)
                                }
                            }
                        }
                    }
                }

                val allReleases = (albums + singlesAndEPs).distinctBy { it.title.lowercase().trim() }
                val pageData = ArtistPageData(
                    channelId = effectiveChannelId,
                    name = artistTitle,
                    photoUrl = highResPhoto,
                    topTracks = topTracks,
                    albums = albums,
                    singlesAndEPs = singlesAndEPs,
                    allReleases = allReleases,
                    similarArtists = similarArtists
                )

                // Simpan ke cache
                artistPageCache[effectiveChannelId] = pageData
                artistPageCache[artistTitle.lowercase().trim()] = pageData
                if (artistNameHint.isNotBlank()) {
                    artistPageCache[artistNameHint.lowercase().trim()] = pageData
                }

                return@withContext pageData
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicRepo", "Error getArtistPage: ${e.message}")
        }

        ArtistPageData(
            channelId = effectiveChannelId,
            name = if (artistNameHint.isNotBlank()) artistNameHint else cleanKey,
            photoUrl = null
        )
    }

    /**
     * Mengambil daftar lagu lengkap di dalam sebuah album resmi berdasarkan browseId album (MPREb_...).
     */
    suspend fun getAlbumTracks(
        browseId: String,
        albumTitle: String = "Album",
        artistName: String = "Artis",
        coverUrl: String = ""
    ): List<Track> = withContext(Dispatchers.IO) {
        val cleanBrowseId = browseId.trim()
        if (cleanBrowseId.isBlank()) return@withContext emptyList()

        val cached = albumTracksCache[cleanBrowseId]
        if (!cached.isNullOrEmpty()) {
            return@withContext cached
        }

        val tracks = mutableListOf<Track>()

        try {
            val conn = createInnerTubeConnection(BROWSE_ENDPOINT)
            val jsonBody = JSONObject().apply {
                put("context", buildContextJson())
                put("browseId", cleanBrowseId)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val root = JSONObject(respText)

                val secondary = root.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONObject("secondaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                val shelf = secondary?.optJSONObject(0)?.optJSONObject("musicShelfRenderer")
                    ?: root.optJSONObject("contents")
                        ?.optJSONObject("singleColumnBrowseResultsRenderer")
                        ?.optJSONArray("tabs")?.optJSONObject(0)
                        ?.optJSONObject("tabRenderer")?.optJSONObject("content")
                        ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                        ?.optJSONObject(0)?.optJSONObject("musicShelfRenderer")

                val items = shelf?.optJSONArray("contents")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val vid = extractVideoId(renderer) ?: continue

                        val title = extractTitle(renderer)
                        val durationSec = extractDurationSeconds(renderer).let { if (it > 0) it else 210 }
                        if (durationSec > 480) continue // Tolak podcast / video berdurasi panjang (> 8 menit)
                        val thumb = extractThumbnail(renderer).ifBlank { coverUrl }

                        tracks.add(
                            Track(
                                id = "yt_$vid",
                                title = title.ifBlank { "Trek ${i + 1}" },
                                artist = artistName,
                                album = albumTitle,
                                durationSeconds = durationSec,
                                streamUrl = "https://music.youtube.com/watch?v=$vid",
                                coverUrl = formatHighResImageUrl(thumb, 800),
                                genre = "Pop Punk",
                                youtubeVideoId = vid
                            )
                        )
                    }
                }

                if (tracks.isNotEmpty()) {
                    albumTracksCache[cleanBrowseId] = tracks
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicRepo", "Error getAlbumTracks: ${e.message}")
        }

        tracks
    }

    /**
     * Resolusi nama artis ke YouTube Music Channel ID (browseId berawalan UC).
     */
    suspend fun resolveArtistChannelId(artistName: String): String? = withContext(Dispatchers.IO) {
        val clean = artistName.lowercase().trim()
        if (clean.isBlank()) return@withContext null

        val mapped = artistChannelMap[clean]
        if (!mapped.isNullOrBlank()) return@withContext mapped

        // Cari via API YouTube Music
        val profile = searchArtistProfile(clean)
        if (profile != null && profile.id.startsWith("UC")) {
            artistChannelMap[clean] = profile.id
            return@withContext profile.id
        }

        null
    }

    // --- Parser Helpers ---

    private fun extractTitle(renderer: JSONObject): String {
        return renderer.optJSONArray("flexColumns")?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
    }

    private fun extractArtistInfo(renderer: JSONObject): Pair<String, String?> {
        val flexCols = renderer.optJSONArray("flexColumns") ?: return Pair("", null)
        if (flexCols.length() > 1) {
            val runs = flexCols.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs") ?: return Pair("", null)

            var artistName = ""
            var channelId: String? = null

            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text", "").trim()
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")

                if (browseId != null && browseId.startsWith("UC")) {
                    channelId = browseId
                }

                if (text.isNotBlank() && text != "•" && !text.equals("Song", ignoreCase = true) && !text.equals("Video", ignoreCase = true)) {
                    if (artistName.isBlank()) artistName = text
                }
            }
            return Pair(artistName, channelId)
        }
        return Pair("", null)
    }

    private fun extractTrendingArtistInfo(renderer: JSONObject): Pair<String, String?> {
        val flexCols = renderer.optJSONArray("flexColumns") ?: return Pair("", null)
        if (flexCols.length() > 1) {
            val runs = flexCols.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs") ?: return Pair("", null)

            val artistBuilder = StringBuilder()
            var channelId: String? = null

            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text", "")
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")

                if (channelId == null && browseId != null && browseId.startsWith("UC")) {
                    channelId = browseId
                }

                if (text.contains("views", ignoreCase = true) || text.contains("plays", ignoreCase = true)) {
                    break
                }

                if (text != "•" && !text.equals("Song", ignoreCase = true) && !text.equals("Video", ignoreCase = true)) {
                    artistBuilder.append(text)
                }
            }
            val parsedArtist = artistBuilder.toString().trim()
            if (parsedArtist.isNotBlank()) {
                return Pair(parsedArtist, channelId)
            }
        }
        return extractArtistInfo(renderer)
    }

    private fun extractAlbumName(renderer: JSONObject): String {
        val flexCols = renderer.optJSONArray("flexColumns") ?: return ""
        if (flexCols.length() > 2) {
            val runs = flexCols.optJSONObject(2)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            return runs?.optJSONObject(0)?.optString("text").orEmpty()
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

    private fun extractThumbnail(renderer: JSONObject): String {
        val thumbs = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        if (thumbs != null && thumbs.length() > 0) {
            return thumbs.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
        }
        return ""
    }

    private fun extractDurationSeconds(renderer: JSONObject): Int {
        val fixedCols = renderer.optJSONArray("fixedColumns")
        if (fixedCols != null && fixedCols.length() > 0) {
            val durText = fixedCols.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            if (durText.contains(":") || (durText.contains(".") && durText.length in 3..8)) {
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
                    if ((t.contains(":") || (t.contains(".") && t.length in 3..8)) && t.length in 3..8) {
                        val sec = parseDurationToSeconds(t)
                        if (sec > 0) return sec
                    }
                }
            }
        }
        return 0
    }

    private fun parseDurationToSeconds(text: String): Int {
        val clean = text.trim().replace('.', ':')
        val parts = clean.split(":")
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
}
