package com.example.spotifyclone.data.repository

import android.content.Context
import com.example.spotifyclone.data.db.ListeningHistoryDatabaseHelper
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.spotify.SpotifyApiService
import com.example.spotifyclone.util.CrankrCatalogFilter
import com.example.spotifyclone.util.MetadataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository khusus untuk memuat dan mengelola data kategori Genre & Mood
 * langsung dari endpoint resmi Spotify (GET /v1/browse/categories/{id}/playlists?country=ID&limit=15)
 * dengan sistem caching memori & database ber-TTL 12 jam serta batas timeout tegas 8 detik.
 */
class GenreMoodRepository(
    private val context: Context? = null,
    private val spotifyApiService: SpotifyApiService = SpotifyApiService()
) {
    private val dbHelper: ListeningHistoryDatabaseHelper? = context?.let {
        ListeningHistoryDatabaseHelper.getInstance(it)
    }

    companion object {
        // Masa berlaku cache genre & mood: 12 jam (TTL)
        const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L

        // Timeout jaringan tegas maksimal 8 detik
        const val NETWORK_TIMEOUT_MS = 8000

        // In-memory cache untuk transisi antar genre seketika tanpa I/O berulang
        private val memoryGenreCache = ConcurrentHashMap<String, Pair<Long, List<Track>>>()

        // Mapping kategori aplikasi ke Spotify Category ID resmi
        fun mapToSpotifyCategoryId(genreOrMood: String): String {
            val lower = genreOrMood.trim().lowercase()
            return when {
                lower.contains("pop punk") -> "punk"
                lower.contains("metalcore") || lower.contains("metal") -> "metal"
                lower.contains("indie rock") || lower.contains("indie") -> "indie_alt"
                lower.contains("lo-fi") || lower.contains("chill") -> "chill"
                lower.contains("anime") || lower.contains("j-rock") -> "anime"
                lower.contains("pop indonesia") || lower.contains("indo") -> "0JQ5DAqbMKFx0uLQR2okcc" // Indonesian Pop / Local
                lower.contains("hip-hop") || lower.contains("hip hop") -> "hiphop"
                lower.contains("skate punk") || lower.contains("grunge") -> "punk"
                lower.contains("rock") -> "rock"
                lower.contains("pop") -> "pop"
                lower.contains("mood") -> "mood"
                lower.contains("workout") -> "workout"
                else -> lower.replace(Regex("[^a-z0-9_-]"), "")
            }
        }
    }

    /**
     * Mengambil trek playlist kategori resmi dari Spotify API.
     * Mengutamakan cache memori & Room jika masih berlaku (< 12 jam).
     * Jika memuat dari internet, menggunakan panggilan non-rekursif langsung dengan timeout 8 detik.
     */
    suspend fun getCategoryTracks(
        genreOrMood: String,
        limit: Int = 15,
        forceRefresh: Boolean = false
    ): List<Track> = withContext(Dispatchers.IO) {
        val cleanGenre = genreOrMood.trim()
        if (cleanGenre.isBlank()) return@withContext emptyList()

        val categoryId = mapToSpotifyCategoryId(cleanGenre)
        val now = System.currentTimeMillis()

        // 1. Cek cache memori instan jika tidak forceRefresh
        if (!forceRefresh) {
            val memCached = memoryGenreCache[categoryId]
            if (memCached != null && (now - memCached.first) < CACHE_TTL_MS && memCached.second.isNotEmpty()) {
                return@withContext memCached.second.take(limit)
            }

            // 2. Cek database Room / SQLite lokal (tabel category_tracks_cache)
            if (dbHelper != null && dbHelper.isCategoryCacheValid(categoryId, CACHE_TTL_MS)) {
                val dbTracks = dbHelper.getCachedCategoryTracks(categoryId)
                if (dbTracks.isNotEmpty()) {
                    memoryGenreCache[categoryId] = Pair(now, dbTracks)
                    return@withContext dbTracks.take(limit)
                }
            }
        }

        // 3. Panggil endpoint resmi Spotify Browse Categories:
        // GET /v1/browse/categories/{category_id}/playlists?country=ID&limit=15
        val fetchedTracks = fetchTracksDirectFromSpotifyCategory(categoryId, cleanGenre, limit)

        if (fetchedTracks.isNotEmpty()) {
            // Simpan ke cache memori dan database Room dengan TTL 12 jam
            memoryGenreCache[categoryId] = Pair(now, fetchedTracks)
            dbHelper?.saveCategoryTracks(categoryId, fetchedTracks, now)
            return@withContext fetchedTracks
        }

        // 4. Fallback jika internet bermasalah: gunakan cache lama yang ada di database
        if (dbHelper != null) {
            val staleTracks = dbHelper.getCachedCategoryTracks(categoryId)
            if (staleTracks.isNotEmpty()) {
                memoryGenreCache[categoryId] = Pair(now, staleTracks)
                return@withContext staleTracks.take(limit)
            }
        }

        emptyList()
    }

    /**
     * Memanggil langsung endpoint playlist kategori Spotify tanpa rekursi berulang.
     * 1. GET /v1/browse/categories/{category_id}/playlists?country=ID&limit=15 (Timeout: 8s)
     * 2. GET /v1/playlists/{playlist_id}/tracks?limit=20&market=ID (Timeout: 8s)
     */
    private suspend fun fetchTracksDirectFromSpotifyCategory(
        categoryId: String,
        genreLabel: String,
        limit: Int
    ): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val token = spotifyApiService.getSpotifyToken() ?: return emptyList()

            // Step 1: Ambil daftar playlist kategori resmi
            val browseUrl = URL("https://api.spotify.com/v1/browse/categories/$categoryId/playlists?country=ID&limit=15")
            val browseConn = (browseUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
            }

            var primaryPlaylistId: String? = null
            if (browseConn.responseCode == 200) {
                val responseStr = browseConn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)
                val playlistsArr = json.optJSONObject("playlists")?.optJSONArray("items")
                if (playlistsArr != null && playlistsArr.length() > 0) {
                    for (i in 0 until playlistsArr.length()) {
                        val pl = playlistsArr.optJSONObject(i) ?: continue
                        val id = pl.optString("id", "")
                        if (id.isNotBlank()) {
                            primaryPlaylistId = id
                            break
                        }
                    }
                }
            }
            browseConn.disconnect()

            // Step 2: Ambil trek dari playlist teratas dalam SATU panggilan langsung
            if (!primaryPlaylistId.isNullOrBlank()) {
                val tracksUrl = URL("https://api.spotify.com/v1/playlists/$primaryPlaylistId/tracks?limit=25&market=ID")
                val tracksConn = (tracksUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connectTimeout = NETWORK_TIMEOUT_MS
                    readTimeout = NETWORK_TIMEOUT_MS
                }

                if (tracksConn.responseCode == 200) {
                    val responseStr = tracksConn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)
                    val itemsArr = json.optJSONArray("items")
                    if (itemsArr != null && itemsArr.length() > 0) {
                        for (i in 0 until itemsArr.length()) {
                            val item = itemsArr.optJSONObject(i) ?: continue
                            val tObj = item.optJSONObject("track") ?: continue
                            val parsed = parseSpotifyTrack(tObj, genreLabel)
                            if (parsed != null && CrankrCatalogFilter.isValidSearchTrack(parsed.title, parsed.artist, isOfficialSpotify = true, parsed.durationSeconds)) {
                                tracks.add(parsed)
                            }
                        }
                    }
                }
                tracksConn.disconnect()
            }

            // Fallback non-rekursif jika kategori browse tidak mengembalikan playlist:
            // Panggil pencarian track resmi berbasis genre Spotify dalam 1 panggilan langsung
            if (tracks.isEmpty()) {
                val searchUrl = URL("https://api.spotify.com/v1/search?q=${URLEncoder.encode(genreLabel, "UTF-8")}&type=track&limit=20&market=ID")
                val searchConn = (searchUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connectTimeout = NETWORK_TIMEOUT_MS
                    readTimeout = NETWORK_TIMEOUT_MS
                }

                if (searchConn.responseCode == 200) {
                    val responseStr = searchConn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)
                    val itemsArr = json.optJSONObject("tracks")?.optJSONArray("items")
                    if (itemsArr != null && itemsArr.length() > 0) {
                        for (i in 0 until itemsArr.length()) {
                            val tObj = itemsArr.optJSONObject(i) ?: continue
                            val parsed = parseSpotifyTrack(tObj, genreLabel)
                            if (parsed != null && CrankrCatalogFilter.isValidSearchTrack(parsed.title, parsed.artist, isOfficialSpotify = true, parsed.durationSeconds)) {
                                tracks.add(parsed)
                            }
                        }
                    }
                }
                searchConn.disconnect()
            }
        } catch (_: Exception) {
            // Abaikan kegagalan jaringan setelah 8 detik timeout
        }

        return MetadataSanitizer.sanitizeTracks(tracks).distinctBy { it.id }.take(limit)
    }

    private fun parseSpotifyTrack(t: JSONObject, genre: String): Track? {
        val id = t.optString("id", "").trim()
        val title = t.optString("name", "").trim()
        if (id.isBlank() || title.isBlank()) return null

        val artistsArr = t.optJSONArray("artists")
        val artistNames = mutableListOf<String>()
        var primaryArtistId: String? = null
        if (artistsArr != null && artistsArr.length() > 0) {
            for (i in 0 until artistsArr.length()) {
                val a = artistsArr.optJSONObject(i) ?: continue
                val aName = a.optString("name", "").trim()
                if (aName.isNotBlank()) artistNames.add(aName)
                if (i == 0) primaryArtistId = a.optString("id").ifBlank { null }
            }
        }
        val artist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Various Artists"

        val albumObj = t.optJSONObject("album")
        val album = albumObj?.optString("name", "Single") ?: "Single"
        val images = albumObj?.optJSONArray("images")
        val coverUrl = if (images != null && images.length() > 0) {
            images.getJSONObject(0).optString("url", "")
        } else ""

        val durationMs = t.optLong("duration_ms", 210000L)
        val durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(30)
        val previewUrl = t.optString("preview_url", "")
        val isrc = t.optJSONObject("external_ids")?.optString("isrc")?.takeIf { it.isNotBlank() }
            ?: t.optString("isrc").takeIf { it.isNotBlank() }

        return Track(
            id = "sp_$id",
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            streamUrl = if (previewUrl.isNotBlank() && previewUrl != "null") previewUrl else "https://music.youtube.com/search?q=${URLEncoder.encode("$title $artist", "UTF-8")}",
            coverUrl = coverUrl,
            genre = genre,
            spotifyArtistId = primaryArtistId,
            isrc = isrc
        )
    }
}
