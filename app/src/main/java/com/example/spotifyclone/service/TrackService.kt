package com.example.spotifyclone.service

import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.spotify.SpotifyApiService
import com.example.spotifyclone.util.CrankrCatalogFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Service untuk mengambil katalog lagu populer / trending langsung dari endpoint resmi Spotify API.
 * Menghindari pencarian teks bebas acak dan mengutamakan playlist Top 50 / Viral 50 Indonesia (market=ID)
 * serta top tracks resmi dari artis unggulan skena.
 */
class TrackService(
    private val spotifyApiService: SpotifyApiService = SpotifyApiService()
) {
    companion object {
        // Playlist Resmi Spotify Indonesia
        const val TOP_50_INDONESIA_PLAYLIST_ID_PRIMARY = "37i9dQZEVXbObFQZqJLv1I"
        const val TOP_50_INDONESIA_PLAYLIST_ID_ALT = "37i9dQZEVXbObFQZqJLvLc"
        const val VIRAL_50_INDONESIA_PLAYLIST_ID = "37i9dQZEVXbIZK8aUquXt8"
        const val HOT_HITS_INDONESIA_PLAYLIST_ID = "37i9dQZF1DX142bzyzL5qA"

        // ID Artis Unggulan Skena & Alternatif Lokal untuk On-Repeat
        val FEATURED_SCENE_ARTIST_IDS = listOf(
            "5q023fUe2hR79nN1nQ8GzU", // Rocket Rockers
            "3Z9rY6aV0GzQ6XbV7o9X8m", // Pee Wee Gaskins
            "6c8jZfE5e69o7Kz7e3s6wQ", // Sheila On 7
            "0kL8a4pX8X9q8b7k8Y9m7Q", // Dewa 19
            "0Q8q6k8X7Y6b8m7k8X9q8b"  // Bernadya
        )
    }

    /**
     * Mengambil daftar lagu populer resmi dari Spotify Indonesia (market=ID).
     * Endpoint utama: GET /v1/playlists/{playlist_id}/tracks?limit=20&market=ID
     */
    suspend fun fetchPopularTracks(
        playlistId: String = TOP_50_INDONESIA_PLAYLIST_ID_PRIMARY,
        market: String = "ID",
        limit: Int = 20
    ): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val playlistIdsToTry = listOf(
            playlistId,
            TOP_50_INDONESIA_PLAYLIST_ID_ALT,
            HOT_HITS_INDONESIA_PLAYLIST_ID,
            VIRAL_50_INDONESIA_PLAYLIST_ID
        ).distinct()

        for (pid in playlistIdsToTry) {
            val list = fetchTracksFromPlaylist(pid, market, limit)
            for (t in list) {
                if (tracks.none { it.id == t.id }) {
                    tracks.add(t)
                }
            }
            if (tracks.size >= limit) break
        }

        // 3. Jika playlist masih kosong, coba ambil top tracks artis unggulan skena: GET /v1/artists/{id}/top-tracks?market=ID
        if (tracks.isEmpty()) {
            for (artistId in FEATURED_SCENE_ARTIST_IDS) {
                try {
                    val artistTracks = fetchArtistTopTracks(artistId, market = market)
                    val clean = artistTracks.filter { isCleanTrack(it) }
                    tracks.addAll(clean.take(4))
                    if (tracks.size >= limit) break
                } catch (_: Exception) {}
            }
        }

        tracks.distinctBy { it.id }.take(limit)
    }

    /**
     * Mengambil lagu dari playlist Spotify menggunakan API resmi atau embed fallback.
     */
    private suspend fun fetchTracksFromPlaylist(
        playlistId: String,
        market: String,
        limit: Int
    ): List<Track> {
        val tracks = mutableListOf<Track>()

        // 1. Coba panggil endpoint resmi Spotify API via access token
        try {
            val token = spotifyApiService.getSpotifyToken()
            if (!token.isNullOrBlank()) {
                val url = URL("https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=$limit&market=$market")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connectTimeout = 10000
                    readTimeout = 15000
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val items = json.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val trackObj = item.optJSONObject("track") ?: continue
                            val parsed = parseOfficialSpotifyTrack(trackObj)
                            if (parsed != null && isCleanTrack(parsed)) {
                                tracks.add(parsed)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallthrough ke scraper resmi jika token API terblokir atau rate-limited
        }

        // 2. Jika token API resmi belum siap / terbatas, gunakan playlist embed resmi Spotify untuk playlist yang sama
        if (tracks.isEmpty()) {
            try {
                val publicResult = spotifyApiService.fetchPublicPlaylistFromUrl(
                    "https://open.spotify.com/playlist/$playlistId",
                    bypassCache = true
                )
                if (publicResult.isSuccess) {
                    val scrapedTracks = publicResult.getOrNull()?.second.orEmpty()
                    val cleanScraped = scrapedTracks.filter { isCleanTrack(it) }.take(limit)
                    tracks.addAll(cleanScraped)
                }
            } catch (_: Exception) {}
        }

        return tracks
    }

    /**
     * Mengambil lagu teratas (Top Tracks) dari artis skena unggulan langsung dari Spotify:
     * GET /v1/artists/{id}/top-tracks?market=ID
     */
    suspend fun fetchArtistTopTracks(
        artistId: String,
        market: String = "ID"
    ): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        try {
            val token = spotifyApiService.getSpotifyToken()
            if (!token.isNullOrBlank()) {
                val url = URL("https://api.spotify.com/v1/artists/$artistId/top-tracks?market=$market")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connectTimeout = 8000
                    readTimeout = 10000
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val items = json.optJSONArray("tracks")
                    if (items != null && items.length() > 0) {
                        for (i in 0 until items.length()) {
                            val trackObj = items.optJSONObject(i) ?: continue
                            val parsed = parseOfficialSpotifyTrack(trackObj)
                            if (parsed != null && isCleanTrack(parsed)) {
                                tracks.add(parsed)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (tracks.isEmpty()) {
            val fallback = spotifyApiService.fetchArtistTopTracks(artistId, market)
            tracks.addAll(fallback.filter { isCleanTrack(it) })
        }

        tracks
    }

    /**
     * Pemetaan metadata lagu resmi Spotify ke model Track Crankr:
     * - Judul lagu (track.name)
     * - Nama musisi/band asli (track.artists.map { it.name })
     * - Cover art resolusi tinggi (track.album.images[0].url)
     * - Durasi lagu resmi (track.duration_ms)
     * - ID Spotify lagu (track.id)
     */
    private fun parseOfficialSpotifyTrack(t: JSONObject): Track? {
        val spotifyId = t.optString("id", "").trim()
        val name = t.optString("name", "").trim()
        if (spotifyId.isBlank() || name.isBlank()) return null

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
        val artistString = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Various Artists"

        val albumObj = t.optJSONObject("album")
        val albumName = albumObj?.optString("name", "Single") ?: "Single"
        val images = albumObj?.optJSONArray("images")
        val coverUrl = if (images != null && images.length() > 0) {
            images.getJSONObject(0).optString("url", "")
        } else ""

        val durationMs = t.optLong("duration_ms", 210000L)
        val durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(30)
        val previewUrl = t.optString("preview_url", "")

        return Track(
            id = "sp_$spotifyId",
            title = name,
            artist = artistString,
            album = albumName,
            durationSeconds = durationSeconds,
            streamUrl = if (previewUrl.isNotBlank() && previewUrl != "null") previewUrl else "https://music.youtube.com/search?q=${URLEncoder.encode("$name $artistString", "UTF-8")}",
            coverUrl = coverUrl,
            genre = "Trending",
            spotifyArtistId = primaryArtistId
        )
    }

    /**
     * Memeriksa kebersihan trek sesuai aturan CrankrCatalogFilter:
     * - Tolak judul buatan akun kompilasi, cover pihak ketiga, atau remix yang bukan rilisan berlisensi resmi.
     * - Tolak video bootleg atau durasi tidak wajar.
     */
    fun isCleanTrack(track: Track): Boolean {
        if (CrankrCatalogFilter.isBlacklisted(track.title, track.artist)) return false
        if (MusicStreamResolver.isCompilationOrBootleg(track.title, track.artist)) return false
        if (track.durationSeconds !in 30..600) return false
        return true
    }
}
