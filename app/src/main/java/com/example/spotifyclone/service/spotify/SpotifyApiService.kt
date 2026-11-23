package com.example.spotifyclone.service.spotify

import android.util.Log
import com.example.spotifyclone.data.model.SpotifyPlaylist
import com.example.spotifyclone.data.model.SpotifyUser
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.AudioStreamResolver
import com.example.spotifyclone.service.MusicStreamResolver
import com.example.spotifyclone.service.RawSpotifyTrackInfo
import com.example.spotifyclone.service.TrackCoverResolver
import com.example.spotifyclone.service.YouTubeTrackMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ExtractedPlaylistTextData(
    val id: String,
    val name: String,
    val description: String,
    val coverUrl: String,
    val tracks: List<RawSpotifyTrackInfo>
)

class SpotifyApiService {

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        private val spotifyCookieStore = mutableMapOf<String, MutableList<Cookie>>()

        private val spotifyCookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(spotifyCookieStore) {
                    val list = spotifyCookieStore.getOrPut(url.host) { mutableListOf() }
                    for (c in cookies) {
                        list.removeAll { it.name == c.name }
                        list.add(c)
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                synchronized(spotifyCookieStore) {
                    val list = spotifyCookieStore[url.host] ?: return emptyList()
                    val now = System.currentTimeMillis()
                    return list.filter { it.expiresAt > now }
                }
            }
        }

        private val spotifyOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .cookieJar(spotifyCookieJar)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
        }

        private val defaultInstance = SpotifyApiService()
        fun getInstance(): SpotifyApiService = defaultInstance

        suspend fun fetchIsrcForTrack(trackId: String): String? {
            return defaultInstance.fetchIsrcForTrack(trackId)
        }
    }

    /**
     * Menyelesaikan URL Spotify jika berupa tautan pendek (spotify.link atau spoti.fi).
     */
    private fun resolveSpotifyUrl(inputUrl: String): String {
        val trimmed = inputUrl.trim()
        if (!trimmed.contains("spotify.link") && !trimmed.contains("spoti.fi")) {
            return trimmed
        }
        return try {
            val req = Request.Builder()
                .url(trimmed)
                .header("User-Agent", DESKTOP_USER_AGENT)
                .build()
            spotifyOkHttpClient.newCall(req).execute().use { res ->
                val finalUrl = res.request.url.toString()
                if (finalUrl.contains("open.spotify.com")) {
                    return finalUrl
                }
                val html = res.body?.string() ?: ""
                val canon = Regex("""<link rel="canonical" href="([^"]+)"""").find(html)?.groupValues?.get(1)
                if (!canon.isNullOrBlank()) return canon
                val href = Regex("""https://open\.spotify\.com/[^\s"']+""").find(html)?.value
                if (!href.isNullOrBlank()) return href
                finalUrl
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    /**
     * Pembersih URL Spotify: Mengambil ID murni deretan alfanumerik tanpa query parameter (?si=xxx).
     */
    private fun extractCleanEntityInfo(rawInput: String): Pair<String, String> {
        val cleanUrl = resolveSpotifyUrl(rawInput)

        // 1. Tautan standar atau intl: open.spotify.com/(intl-xx/)?(playlist|album|track)/ID
        // atau URI: spotify:(playlist|album|track):ID
        val standardRegex = Regex("""(?:open\.spotify\.com/(?:(?:intl-[a-z]{2,5}|embed)/)?|spotify:)(playlist|album|track)[/:]([a-zA-Z0-9]{15,35})""", RegexOption.IGNORE_CASE)
        val standardMatch = standardRegex.find(cleanUrl)
        if (standardMatch != null) {
            val type = standardMatch.groupValues[1].lowercase()
            val id = standardMatch.groupValues[2].substringBefore("?").substringBefore("&").substringBefore("#").trim()
            return Pair(type, id)
        }

        // 2. Format spotify:user:...:playlist:ID
        val userPlRegex = Regex("""spotify:user:[^:]+:playlist:([a-zA-Z0-9]{15,35})""", RegexOption.IGNORE_CASE)
        val userPlMatch = userPlRegex.find(cleanUrl)
        if (userPlMatch != null) {
            val id = userPlMatch.groupValues[1].substringBefore("?").substringBefore("&").substringBefore("#").trim()
            return Pair("playlist", id)
        }

        // 3. Pengguna memasukkan ID langsung atau potongan terakhir
        val lastSegment = cleanUrl.substringAfterLast("/").substringBefore("?").substringBefore("&").substringBefore("#").trim()
        if (lastSegment.matches(Regex("^[a-zA-Z0-9]{15,35}$"))) {
            val type = if (cleanUrl.contains("album", ignoreCase = true)) "album" else "playlist"
            return Pair(type, lastSegment)
        }

        return Pair("playlist", "")
    }

    /**
     * Mengambil sesi dan accessToken resmi Spotify Web Player:
     * Langkah A: Kirim permintaan GET ke https://open.spotify.com/ dengan header User-Agent browser desktop Chrome terbaru agar server menanamkan kuki sesi awal.
     * Langkah B: Kirim permintaan GET ke https://open.spotify.com/get_access_token?reason=transport&productType=web_player dengan header yang sama beserta header Referer https://open.spotify.com/.
     */
    private suspend fun fetchSpotifyWebSessionToken(): String? = withContext(Dispatchers.IO) {
        try {
            // Langkah A: Kunjungi open.spotify.com dengan OkHttpClient dan CookieJar aktif
            val sessionReq = Request.Builder()
                .url("https://open.spotify.com/")
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Ch-Ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Upgrade-Insecure-Requests", "1")
                .build()
            spotifyOkHttpClient.newCall(sessionReq).execute().use { res ->
                Log.d("CrankrImport", "Langkah A: Sesi penjelajahan awal Spotify diinisialisasi, HTTP code: ${res.code}")
            }

            // Langkah B: Kirim permintaan GET ke get_access_token dengan User-Agent yang sama dan Referer https://open.spotify.com/
            val tokenReq = Request.Builder()
                .url("https://open.spotify.com/get_access_token?reason=transport&productType=web_player")
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Referer", "https://open.spotify.com/")
                .header("Accept", "application/json")
                .header("Sec-Ch-Ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .build()
            spotifyOkHttpClient.newCall(tokenReq).execute().use { res ->
                if (res.isSuccessful) {
                    val bodyStr = res.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val token = json.optString("accessToken").takeIf { it.isNotBlank() }
                        ?: json.optString("access_token").takeIf { it.isNotBlank() }
                    if (!token.isNullOrBlank()) {
                        Log.d("CrankrImport", "Langkah B: Berhasil mengambil accessToken dari web player")
                        return@withContext token
                    }
                } else {
                    Log.w("CrankrImport", "Langkah B: get_access_token mengembalikan HTTP ${res.code}")
                }
            }
        } catch (e: Exception) {
            Log.w("CrankrImport", "Gagal mengambil sesi token web Spotify: ${e.message}")
        }

        // Jalur token cadangan jika get_access_token terhalang jaringan:
        // https://open.spotify.com/embed/api/token
        try {
            val embedTokenReq = Request.Builder()
                .url("https://open.spotify.com/embed/api/token")
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Referer", "https://open.spotify.com/")
                .header("Accept", "application/json")
                .build()
            spotifyOkHttpClient.newCall(embedTokenReq).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: ""
                    val json = JSONObject(body)
                    val token = json.optString("accessToken").takeIf { it.isNotBlank() }
                    if (!token.isNullOrBlank()) {
                        Log.d("CrankrImport", "Berhasil mengambil accessToken dari embed/api/token")
                        return@withContext token
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Ekstraksi satu kali dari tautan playlist publik Spotify:
     * Mengambil nama playlist, deskripsi, cover, serta daftar teks judul lagu dan nama artisnya saja.
     */
    suspend fun extractPlaylistTextTracks(
        url: String,
        onProgress: ((playlistName: String, current: Int, total: Int) -> Unit)? = null
    ): Result<ExtractedPlaylistTextData> = withContext(Dispatchers.IO) {
        try {
            val (entityType, entityId) = extractCleanEntityInfo(url)

            // Hanya tampilkan pesan tautan tidak valid jika sistem benar-benar gagal mengekstrak ID playlist dari teks input pengguna
            if (entityId.isEmpty()) {
                return@withContext Result.failure(Exception("Tautan playlist Spotify tidak valid: ID playlist tidak ditemukan"))
            }

            // 1. Ekstraksi Menggunakan Invisible Headless WebView (Menyadap Token Sesi Resmi & Pagination Penuh)
            Log.d("CrankrImport", "Memulai ekstraksi Spotify via SpotifyExtractor (Headless WebView) untuk ID: $entityId...")
            try {
                val extractedData = SpotifyExtractor.extractPlaylist(entityType, entityId, onProgress)
                if (extractedData != null && extractedData.tracks.isNotEmpty()) {
                    Log.d("CrankrImport", "Ekstraksi via Headless WebView berhasil: ${extractedData.tracks.size} lagu")
                    return@withContext Result.success(extractedData)
                }
            } catch (extEx: Exception) {
                Log.w("CrankrImport", "SpotifyExtractor mengalami error: ${extEx.message}")
            }

            Log.w("CrankrImport", "SpotifyExtractor tidak mengembalikan data, melanjutkan ke metode alternatif...")

            var playlistTitle = "Spotify Playlist"
            var playlistSubtitle = ""
            var coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=60"
            val textTracks = mutableListOf<RawSpotifyTrackInfo>()

            // 1. Ambil Sesi dan Token Web Spotify dengan OkHttpClient + CookieJar
            var accessToken: String? = null
            try {
                accessToken = fetchSpotifyWebSessionToken()
            } catch (tokenEx: Exception) {
                Log.w("CrankrImport", "Gagal mendapatkan token: ${tokenEx.message}")
            }

            // 2. Sistem Perulangan Halaman Lagu (Pagination) jika accessToken berhasil didapat
            if (!accessToken.isNullOrBlank() && (entityType == "playlist" || entityType == "album")) {
                try {
                    // Ambil metadata judul, deskripsi, dan cover
                    try {
                        val metaEndpoint = if (entityType == "playlist") {
                            "https://api.spotify.com/v1/playlists/$entityId?fields=name,description,images"
                        } else {
                            "https://api.spotify.com/v1/albums/$entityId?fields=name,images"
                        }
                        val metaReq = Request.Builder()
                            .url(metaEndpoint)
                            .header("Authorization", "Bearer $accessToken")
                            .header("User-Agent", DESKTOP_USER_AGENT)
                            .header("Accept", "application/json")
                            .build()
                        spotifyOkHttpClient.newCall(metaReq).execute().use { res ->
                            if (res.isSuccessful) {
                                val metaJson = JSONObject(res.body?.string() ?: "")
                                val name = metaJson.optString("name", "").trim()
                                if (name.isNotBlank()) playlistTitle = name
                                playlistSubtitle = metaJson.optString("description", "").trim()
                                val images = metaJson.optJSONArray("images")
                                if (images != null && images.length() > 0) {
                                    val img = images.optJSONObject(0)?.optString("url", "") ?: ""
                                    if (img.isNotBlank()) coverUrl = img
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    var offset = 0
                    var totalTracks = -1

                    while (totalTracks == -1 || textTracks.size < totalTracks) {
                        val tracksEndpoint = if (entityType == "playlist") {
                            "https://api.spotify.com/v1/playlists/$entityId/tracks?limit=100&offset=$offset"
                        } else {
                            "https://api.spotify.com/v1/albums/$entityId/tracks?limit=50&offset=$offset"
                        }
                        val tracksReq = Request.Builder()
                            .url(tracksEndpoint)
                            .header("Authorization", "Bearer $accessToken")
                            .header("User-Agent", DESKTOP_USER_AGENT)
                            .header("Accept", "application/json")
                            .build()

                        var shouldBreak = false
                        try {
                            spotifyOkHttpClient.newCall(tracksReq).execute().use { res ->
                                if (res.isSuccessful) {
                                    val body = res.body?.string() ?: ""
                                    val response = JSONObject(body)

                                    if (totalTracks == -1) {
                                        totalTracks = response.optInt("total", -1)
                                        if (totalTracks > 0) {
                                            onProgress?.invoke(playlistTitle, 0, totalTracks)
                                        }
                                    }

                                    val items = response.optJSONArray("items")
                                    if (items == null || items.length() == 0) {
                                        shouldBreak = true
                                        return@use
                                    }

                                    for (i in 0 until items.length()) {
                                        val item = items.optJSONObject(i) ?: continue

                                        val trackObj = if (entityType == "playlist") {
                                            // Abaikan item jika properti track bernilai null
                                            if (item.isNull("track") || !item.has("track")) continue
                                            item.optJSONObject("track") ?: continue
                                        } else {
                                            item
                                        }

                                        val trackTitle = trackObj.optString("name", "").trim()
                                        if (trackTitle.isBlank()) continue

                                        // Ambil nama artis dari penggabungan seluruh nama di track.artists
                                        val artistsArr = trackObj.optJSONArray("artists")
                                        val artistNames = mutableListOf<String>()
                                        if (artistsArr != null && artistsArr.length() > 0) {
                                            for (a in 0 until artistsArr.length()) {
                                                val aName = artistsArr.optJSONObject(a)?.optString("name", "")?.trim() ?: ""
                                                if (aName.isNotBlank()) artistNames.add(aName)
                                            }
                                        }
                                        val trackArtist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unknown Artist"

                                        // Ambil nama album asli secara ketat dari track.getJSONObject("album").getString("name")
                                        // Jangan pernah mengisi kolom atau properti album menggunakan variabel playlistName
                                        val albumObj = trackObj.optJSONObject("album")
                                        val albumName = albumObj?.optString("name", "")?.trim() ?: ""

                                        // Log pencatatan verifikasi nama album
                                        Log.d("CrankrImport", "Lagu: $trackTitle | Album: $albumName")

                                        textTracks.add(RawSpotifyTrackInfo(trackTitle, trackArtist, albumName))
                                    }

                                    Log.d("CrankrImport", "Berhasil mengambil: ${textTracks.size} dari $totalTracks lagu")
                                    onProgress?.invoke(playlistTitle, textTracks.size, if (totalTracks > 0) totalTracks else textTracks.size)

                                    val nextProp = response.optString("next")
                                    val isNextNull = response.isNull("next") || nextProp.isBlank() || nextProp == "null"
                                    if (isNextNull) {
                                        shouldBreak = true
                                        return@use
                                    }

                                    offset += if (entityType == "playlist") 100 else 50
                                } else {
                                    Log.w("CrankrImport", "Spotify tracks API HTTP ${res.code} pada offset $offset")
                                    shouldBreak = true
                                }
                            }
                        } catch (connEx: Exception) {
                            Log.w("CrankrImport", "Koneksi Spotify API gagal pada offset $offset: ${connEx.message}")
                            shouldBreak = true
                        }

                        if (shouldBreak) break

                        // Tambahkan jeda delay(100) di setiap putaran perulangan agar terhindar dari pemutusan koneksi server
                        delay(100)
                    }
                } catch (e: Exception) {
                    Log.w("CrankrImport", "Error pagination Spotify tracks: ${e.message}")
                }
            }

            // 3. Sistem Cadangan (Fallback) Jika Token / API Tracks Gagal atau Kosong:
            // Alihkan sistem secara otomatis untuk membaca data daftar lagu dari halaman embed publik
            if (textTracks.isEmpty()) {
                Log.d("CrankrImport", "Menjalankan jalur cadangan embed publik Spotify untuk ID: $entityId...")
                try {
                    val embedUrl = "https://open.spotify.com/embed/$entityType/$entityId"
                    val embedReq = Request.Builder()
                        .url(embedUrl)
                        .header("User-Agent", DESKTOP_USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .build()
                    spotifyOkHttpClient.newCall(embedReq).execute().use { res ->
                        if (res.isSuccessful) {
                            val html = res.body?.string() ?: ""
                            val nextDataRegex = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                            val scriptMatch = nextDataRegex.find(html)
                            if (scriptMatch != null) {
                                val json = JSONObject(scriptMatch.groupValues[1])
                                val pageProps = json.optJSONObject("props")?.optJSONObject("pageProps")
                                val pageState = pageProps?.optJSONObject("state")
                                val entity = pageState?.optJSONObject("data")?.optJSONObject("entity")

                                if (entity != null) {
                                    val entityTitle = entity.optString("title", entity.optString("name", "")).trim()
                                    if (entityTitle.isNotBlank()) playlistTitle = entityTitle
                                    val entitySub = entity.optString("subtitle", "").trim()
                                    if (entitySub.isNotBlank()) playlistSubtitle = entitySub

                                    // Cover art
                                    val visualIdentity = entity.optJSONObject("visualIdentity")
                                    val imageArray = visualIdentity?.optJSONArray("image")
                                    if (imageArray != null && imageArray.length() > 0) {
                                        val img = imageArray.optJSONObject(imageArray.length() - 1)?.optString("url", "") ?: ""
                                        if (img.isNotBlank()) coverUrl = img
                                    } else {
                                        val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
                                        if (coverSources != null && coverSources.length() > 0) {
                                            val img = coverSources.optJSONObject(0)?.optString("url", "") ?: ""
                                            if (img.isNotBlank()) coverUrl = img
                                        }
                                    }

                                    val trackListArray = entity.optJSONArray("trackList")
                                    if (trackListArray != null && trackListArray.length() > 0) {
                                        val fallbackTotal = trackListArray.length()
                                        onProgress?.invoke(playlistTitle, 0, fallbackTotal)

                                        for (i in 0 until trackListArray.length()) {
                                            val item = trackListArray.optJSONObject(i) ?: continue
                                            val trackTitle = item.optString("title", item.optString("name", "")).trim()
                                            if (trackTitle.isBlank()) continue
                                            val trackArtist = item.optString("subtitle", item.optString("artist", "Unknown Artist")).trim()

                                            // Ambil nama album asli secara ketat tanpa pernah menggunakan playlistTitle / playlistName
                                            val albumObj = item.optJSONObject("album")
                                            val albumName = albumObj?.optString("name", "")?.trim() ?: ""

                                            Log.d("CrankrImport", "Lagu: $trackTitle | Album: $albumName")

                                            textTracks.add(RawSpotifyTrackInfo(trackTitle, trackArtist, albumName))
                                        }

                                        onProgress?.invoke(playlistTitle, textTracks.size, fallbackTotal)
                                        Log.d("CrankrImport", "Berhasil mengekstrak ${textTracks.size} lagu dari jalur cadangan embed")
                                    }
                                }
                            }
                        }
                    }
                } catch (fallbackEx: Exception) {
                    Log.w("CrankrImport", "Jalur cadangan embed gagal: ${fallbackEx.message}")
                }
            }

            // Fallback metadata oEmbed jika judul masih default
            if (playlistTitle == "Spotify Playlist") {
                try {
                    val oembedUrl = "https://open.spotify.com/oembed?url=https://open.spotify.com/$entityType/$entityId"
                    val oembedReq = Request.Builder()
                        .url(oembedUrl)
                        .header("User-Agent", DESKTOP_USER_AGENT)
                        .build()
                    spotifyOkHttpClient.newCall(oembedReq).execute().use { res ->
                        if (res.isSuccessful) {
                            val oembedJson = JSONObject(res.body?.string() ?: "")
                            val title = oembedJson.optString("title", "").trim()
                            if (title.isNotBlank()) playlistTitle = title
                            val thumb = oembedJson.optString("thumbnail_url", "").trim()
                            if (thumb.isNotBlank()) coverUrl = thumb
                        }
                    }
                } catch (_: Exception) {}
            }

            if (textTracks.isEmpty()) {
                return@withContext Result.failure(Exception("Gagal memuat lagu: Daftar lagu kosong atau periksa koneksi internet Anda"))
            }

            Result.success(
                ExtractedPlaylistTextData(
                    id = entityId,
                    name = playlistTitle,
                    description = playlistSubtitle,
                    coverUrl = coverUrl,
                    tracks = textTracks
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Memproses impor playlist publik Spotify:
     * 1. Menjalankan proses ekstraksi satu kali (nama playlist + teks judul dan artis).
     * 2. Mencocokkan seluruh lagu ke YouTube Music (via Piped / Invidious) untuk videoId, judul, artis, durasi, dan coverUrl.
     * Bebas dari token loop Spotify atau query berulang ke server Spotify.
     */
    suspend fun fetchPublicPlaylistFromUrl(
        url: String,
        bypassCache: Boolean = false,
        onProgress: ((playlistName: String, current: Int, total: Int) -> Unit)? = null
    ): Result<Pair<SpotifyPlaylist, List<Track>>> = withContext(Dispatchers.IO) {
        try {
            val extractedRes = extractPlaylistTextTracks(url, onProgress)
            if (extractedRes.isFailure) {
                return@withContext Result.failure(extractedRes.exceptionOrNull() ?: Exception("Gagal mengekstrak playlist Spotify"))
            }
            val extracted = extractedRes.getOrNull()!!

            onProgress?.invoke(extracted.name, 0, extracted.tracks.size)

            // Pencocokan otomatis ke API YouTube Music (via Piped atau Invidious)
            val matchedTracks = YouTubeTrackMatcher.matchTracksConcurrently(
                rawTracks = extracted.tracks,
                playlistName = extracted.name,
                fallbackCoverUrl = extracted.coverUrl,
                onProgress = { cur, tot ->
                    onProgress?.invoke(extracted.name, cur, tot)
                }
            )

            val playlist = SpotifyPlaylist(
                id = extracted.id,
                name = extracted.name,
                description = extracted.description,
                imageUrl = extracted.coverUrl,
                trackCount = matchedTracks.size,
                ownerName = "Spotify",
                isSynced = true,
                sourceUrl = url
            )

            Result.success(Pair(playlist, matchedTracks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractTrackCover(item: JSONObject, fallbackCover: String): String {
        // 1. Cek album.images (format resmi track.album.images)
        val albumObj = item.optJSONObject("album")
        if (albumObj != null) {
            val images = albumObj.optJSONArray("images")
            if (images != null && images.length() > 0) {
                val url = images.getJSONObject(0).optString("url")
                if (url.isNotBlank()) return url
            }
            val sources = albumObj.optJSONObject("coverArt")?.optJSONArray("sources")
            if (sources != null && sources.length() > 0) {
                val url = sources.getJSONObject(0).optString("url")
                if (url.isNotBlank()) return url
            }
        }

        // 2. Cek coverArt.sources langsung pada track item
        val coverSources = item.optJSONObject("coverArt")?.optJSONArray("sources")
        if (coverSources != null && coverSources.length() > 0) {
            val url = coverSources.getJSONObject(0).optString("url")
            if (url.isNotBlank()) return url
        }

        // 3. Cek albumOfTrack (format Pathfinder / GraphQL Spotify)
        val aot = item.optJSONObject("albumOfTrack")
        val aotSources = aot?.optJSONObject("data")?.optJSONObject("coverArt")?.optJSONArray("sources")
            ?: aot?.optJSONObject("coverArt")?.optJSONArray("sources")
        if (aotSources != null && aotSources.length() > 0) {
            val url = aotSources.getJSONObject(0).optString("url")
            if (url.isNotBlank()) return url
        }

        // 4. Cek images array langsung
        val directImages = item.optJSONArray("images")
        if (directImages != null && directImages.length() > 0) {
            val url = directImages.getJSONObject(0).optString("url")
            if (url.isNotBlank()) return url
        }

        // 5. Cek field imageUrl / coverUrl
        val directUrl = item.optString("imageUrl", item.optString("coverUrl", ""))
        if (directUrl.isNotBlank()) return directUrl

        return fallbackCover
    }

    fun fetchTrackCoverViaOEmbed(trackId: String): String? {
        return try {
            val cleanId = trackId.removePrefix("sp_").substringAfter("track:")
            val url = URL("https://open.spotify.com/oembed?url=https://open.spotify.com/track/$cleanId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val json = JSONObject(readStream(conn))
                json.optString("thumbnail_url").takeIf { it.isNotBlank() }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private var cachedToken: String? = null
    private var tokenExpirationMs: Long = 0L

    fun invalidateToken() {
        cachedToken = null
        tokenExpirationMs = 0L
    }

    fun getSpotifyToken(): String? {
        if (!cachedToken.isNullOrBlank() && System.currentTimeMillis() < tokenExpirationMs) {
            return cachedToken
        }
        return null
    }

    private fun fetchAnonymousToken(): String? {
        return fetchAnonymousWebToken()
    }

    private fun fetchAnonymousWebToken(): String? {
        return try {
            val url = URL("https://open.spotify.com/get_access_token?reason=transport&productType=web_player")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 6000
                readTimeout = 6000
            }
            try {
                if (conn.responseCode == 200) {
                    val body = readStream(conn)
                    val json = JSONObject(body)
                    json.optString("accessToken").takeIf { it.isNotBlank() }
                } else null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun executeSpotifyGet(
        urlStr: String,
        maxRetries: Int = 2
    ): JSONObject? = withContext(Dispatchers.IO) {
        var attempt = 0
        while (attempt <= maxRetries) {
            val token = getSpotifyToken()
            if (token.isNullOrBlank()) {
                Log.e("CRANKR_DEBUG", "executeSpotifyGet: Token kosong/tidak tersedia untuk $urlStr")
                return@withContext null
            }
            var conn: HttpURLConnection? = null
            try {
                Log.d("CRANKR_DEBUG", "executeSpotifyGet (percobaan $attempt): $urlStr")
                val url = URL(urlStr)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val code = conn.responseCode
                Log.d("CRANKR_DEBUG", "executeSpotifyGet HTTP $code untuk $urlStr")
                if (code == 200) {
                    val body = readStream(conn)
                    Log.d("CRANKR_DEBUG", "executeSpotifyGet HTTP 200 OK (${body.length} karakter) untuk $urlStr")
                    return@withContext JSONObject(body)
                } else if (code == 401 || code == 403) {
                    Log.w("CRANKR_DEBUG", "executeSpotifyGet HTTP $code (Token Kedaluwarsa/Unauthorized). Reset token.")
                    invalidateToken()
                } else if (code == 429) {
                    val retrySec = conn.getHeaderField("Retry-After")?.toLongOrNull() ?: (1L shl attempt)
                    Log.w("CRANKR_DEBUG", "executeSpotifyGet HTTP 429 (Rate Limit). Backoff $retrySec s.")
                    val backoffMs = (retrySec * 1000L).coerceIn(1000L, 4000L)
                    delay(backoffMs)
                    attempt++
                    continue
                } else {
                    Log.w("CRANKR_DEBUG", "executeSpotifyGet HTTP $code untuk $urlStr")
                }
            } catch (e: Exception) {
                Log.e("CRANKR_DEBUG", "executeSpotifyGet error pada percobaan $attempt ($urlStr): ${e.message}")
            } finally {
                conn?.disconnect()
            }
            attempt++
            if (attempt <= maxRetries) {
                delay(500L)
            }
        }
        Log.w("CRANKR_DEBUG", "executeSpotifyGet: Gagal setelah $maxRetries percobaan: $urlStr")
        null
    }

    private fun fetchRemainingTracksViaPathfinder(
        playlistId: String,
        token: String,
        offsetStart: Int,
        coverUrl: String,
        albumTitle: String,
        bypassCache: Boolean
    ): List<Track> {
        return emptyList()
    }

    suspend fun fetchUserProfile(accessToken: String): Result<SpotifyUser> = withContext(Dispatchers.IO) {
        if (accessToken.startsWith("demo_")) {
            return@withContext Result.success(
                SpotifyUser(
                    id = "spotify_user_demo",
                    displayName = "Alex Rivers",
                    email = "alex.music@spotify.com",
                    avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80",
                    product = "Spotify Premium"
                )
            )
        }

        try {
            val url = URL("https://api.spotify.com/v1/me")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == 200) {
                val response = readStream(connection)
                val json = JSONObject(response)
                val id = json.getString("id")
                val name = json.optString("display_name", "Spotify User")
                val email = if (json.has("email")) json.getString("email") else null
                val product = json.optString("product", "Free")
                val images = json.optJSONArray("images")
                val avatarUrl = if (images != null && images.length() > 0) {
                    images.getJSONObject(0).optString("url")
                } else null

                Result.success(SpotifyUser(id, name, email, avatarUrl, product))
            } else {
                Result.failure(Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserPlaylists(accessToken: String): Result<List<SpotifyPlaylist>> = withContext(Dispatchers.IO) {
        if (accessToken.startsWith("demo_")) {
            return@withContext Result.success(getDemoPlaylists())
        }

        try {
            val url = URL("https://api.spotify.com/v1/me/playlists?limit=20")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == 200) {
                val response = readStream(connection)
                val json = JSONObject(response)
                val items = json.getJSONArray("items")
                val list = mutableListOf<SpotifyPlaylist>()

                for (i in 0 until items.length()) {
                    val p = items.getJSONObject(i)
                    val id = p.getString("id")
                    val name = p.getString("name")
                    val desc = p.optString("description", "")
                    val images = p.optJSONArray("images")
                    val imageUrl = if (images != null && images.length() > 0) {
                        images.getJSONObject(0).optString("url")
                    } else null
                    val tracksObj = p.optJSONObject("tracks")
                    val count = tracksObj?.optInt("total", 0) ?: 0
                    val owner = p.optJSONObject("owner")?.optString("display_name", "You") ?: "You"

                    list.add(SpotifyPlaylist(id, name, desc, imageUrl, count, owner))
                }
                Result.success(list)
            } else {
                Result.success(getDemoPlaylists())
            }
        } catch (e: Exception) {
            Result.success(getDemoPlaylists())
        }
    }

    suspend fun fetchPlaylistTracks(accessToken: String, playlistId: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        if (accessToken.startsWith("demo_") || playlistId.startsWith("demo_")) {
            Result.success(getDemoPlaylistTracks(playlistId))
        } else {
            Result.success(emptyList())
        }
    }

    /**
     * Fetches official licensed tracks from Spotify for a specific genre.
     * Uses Spotify Recommendations API (GET /v1/recommendations?seed_genres=...)
     * and fallback to Spotify Catalog Search (GET /v1/search?q=genre:...&type=track).
     * Strictly rejects compilation videos, bootlegs, or tracks exceeding 10 minutes.
     */
    suspend fun fetchGenreRecommendations(genre: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val cleanGenre = genre.trim()
        if (cleanGenre.isBlank()) return@withContext emptyList()

        val token = fetchAnonymousToken()
        val results = mutableListOf<Track>()

        val (seedGenre, spotifyQuery) = mapGenreToSpotify(cleanGenre)

        // 1. Try Spotify Recommendations by Seed Genre endpoint
        if (!seedGenre.isNullOrBlank()) {
            val json = executeSpotifyGet("https://api.spotify.com/v1/recommendations?seed_genres=$seedGenre&limit=$limit")
            val tracks = json?.optJSONArray("tracks")
            if (tracks != null && tracks.length() > 0) {
                for (i in 0 until tracks.length()) {
                    val t = tracks.optJSONObject(i) ?: continue
                    val track = parseSpotifyTrackJson(t, cleanGenre)
                    if (track != null && !MusicStreamResolver.isCompilationOrBootleg(track.title, track.artist) && track.durationSeconds <= 600) {
                        results.add(track)
                    }
                }
            }
        }

        // 2. Complement / Fallback via Spotify Catalog Track Search
        if (results.size < 6) {
            val encodedQuery = URLEncoder.encode(spotifyQuery, "UTF-8")
            val json = executeSpotifyGet("https://api.spotify.com/v1/search?q=$encodedQuery&type=track&limit=$limit")
            val tracks = json?.optJSONObject("tracks")?.optJSONArray("items")
            if (tracks != null && tracks.length() > 0) {
                for (i in 0 until tracks.length()) {
                    val t = tracks.optJSONObject(i) ?: continue
                    val track = parseSpotifyTrackJson(t, cleanGenre)
                    if (track != null && !MusicStreamResolver.isCompilationOrBootleg(track.title, track.artist) && track.durationSeconds <= 600) {
                        if (results.none { it.id == track.id || (it.title.equals(track.title, true) && it.artist.equals(track.artist, true)) }) {
                            results.add(track)
                        }
                    }
                }
            }
        }

        results
    }

    /**
     * Fetches official recommendations from Spotify API using seed artists and/or seed genres.
     * Endpoint: GET /v1/recommendations?seed_artists=...&seed_genres=...&limit=...
     */
    suspend fun fetchSpotifyRecommendations(
        seedArtists: List<String> = emptyList(),
        seedGenres: List<String> = emptyList(),
        limit: Int = 20
    ): List<Track> = withContext(Dispatchers.IO) {
        val token = fetchAnonymousToken() ?: return@withContext emptyList()
        val results = mutableListOf<Track>()

        val params = mutableListOf<String>()
        if (seedArtists.isNotEmpty()) {
            params.add("seed_artists=" + seedArtists.take(2).joinToString(","))
        }
        if (seedGenres.isNotEmpty()) {
            params.add("seed_genres=" + seedGenres.take(2).joinToString(","))
        }
        params.add("limit=$limit")

        try {
            val queryStr = params.joinToString("&")
            val url = URL("https://api.spotify.com/v1/recommendations?$queryStr")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            if (conn.responseCode == 200) {
                val body = readStream(conn)
                val json = JSONObject(body)
                val tracks = json.optJSONArray("tracks")
                if (tracks != null && tracks.length() > 0) {
                    for (i in 0 until tracks.length()) {
                        val t = tracks.getJSONObject(i)
                        val track = parseSpotifyTrackJson(t, seedGenres.firstOrNull() ?: "Rock")
                        if (track != null) {
                            results.add(track)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        results
    }

    /**
     * Fetches official top tracks of an artist directly from Spotify API.
     * Endpoint: GET /v1/artists/{id}/top-tracks?market=ID
     */
    suspend fun fetchArtistTopTracks(spotifyArtistId: String, market: String = "ID"): List<Track> = withContext(Dispatchers.IO) {
        val cleanId = spotifyArtistId.trim()
        if (cleanId.isBlank()) return@withContext emptyList()

        val results = mutableListOf<Track>()
        val json = executeSpotifyGet("https://api.spotify.com/v1/artists/$cleanId/top-tracks?market=$market")
        val tracks = json?.optJSONArray("tracks")
        if (tracks != null && tracks.length() > 0) {
            for (i in 0 until tracks.length()) {
                val t = tracks.optJSONObject(i) ?: continue
                val track = parseSpotifyTrackJson(t, "Pop Punk")
                if (track != null) {
                    results.add(track)
                }
            }
        }

        results
    }

    /**
     * Mengambil daftar album dan single resmi artis langsung dari Spotify API (maksimal 50 item).
     * Endpoint: GET /v1/artists/{id}/albums?include_groups=album,single&limit=50
     * Parameter market dihapus sepenuhnya agar katalog band independen lokal tidak hilang.
     */
    suspend fun getArtistAlbums(
        id: String = "",
        limit: Int = 50,
        includeGroups: String = "album,single",
        artistId: String = id
    ): JSONObject? = withContext(Dispatchers.IO) {
        val cleanId = (if (id.isNotBlank()) id else artistId).trim()
        if (cleanId.isBlank()) return@withContext null
        val url = "https://api.spotify.com/v1/artists/$cleanId/albums?include_groups=$includeGroups&limit=$limit"
        executeSpotifyGet(url)
    }

    /**
     * Searches official licensed tracks directly from Spotify Catalog.
     */
    suspend fun searchOfficialSpotifyTracks(query: String, limit: Int = 15): List<Track> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val results = mutableListOf<Track>()
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val json = executeSpotifyGet("https://api.spotify.com/v1/search?q=$encodedQuery&type=track&limit=$limit")
        val tracks = json?.optJSONObject("tracks")?.optJSONArray("items")
        if (tracks != null && tracks.length() > 0) {
            for (i in 0 until tracks.length()) {
                val t = tracks.optJSONObject(i) ?: continue
                val track = parseSpotifyTrackJson(t, "Pop")
                if (track != null && !MusicStreamResolver.isCompilationOrBootleg(track.title, track.artist) && track.durationSeconds <= 600) {
                    results.add(track)
                }
            }
        }

        results
    }

    private fun parseSpotifyTrackJson(t: JSONObject, genre: String): Track? {
        val id = t.optString("id", "")
        val title = t.optString("name", "").trim()
        if (id.isBlank() || title.isBlank()) return null

        val artistsArr = t.optJSONArray("artists")
        val artistName = if (artistsArr != null && artistsArr.length() > 0) {
            val names = mutableListOf<String>()
            for (a in 0 until artistsArr.length()) {
                val aName = artistsArr.getJSONObject(a).optString("name", "")
                if (aName.isNotBlank()) names.add(aName)
            }
            names.joinToString(", ")
        } else "Artis"

        val albumObj = t.optJSONObject("album")
        val albumName = albumObj?.optString("name", "Single") ?: "Single"
        val images = albumObj?.optJSONArray("images")
        val coverUrl = if (images != null && images.length() > 0) {
            images.getJSONObject(0).optString("url", "")
        } else ""

        val primaryArtistId = if (artistsArr != null && artistsArr.length() > 0) {
            artistsArr.optJSONObject(0)?.optString("id").orEmpty().ifBlank { null }
        } else null

        val durationMs = t.optLong("duration_ms", 210000L)
        val durationSec = (durationMs / 1000).toInt().coerceAtLeast(30)
        val previewUrl = t.optString("preview_url", "")
        val isrc = t.optJSONObject("external_ids")?.optString("isrc")?.takeIf { it.isNotBlank() }
            ?: t.optString("isrc").takeIf { it.isNotBlank() }
        if (!isrc.isNullOrBlank()) {
            AudioStreamResolver.putIsrc(title, artistName, isrc)
        }

        return Track(
            id = "sp_$id",
            title = title,
            artist = artistName,
            album = albumName,
            durationSeconds = durationSec,
            streamUrl = if (previewUrl.isNotBlank() && previewUrl != "null") previewUrl else "https://music.youtube.com/search?q=${URLEncoder.encode("$title $artistName", "UTF-8")}",
            coverUrl = coverUrl,
            genre = genre,
            spotifyArtistId = primaryArtistId,
            isrc = isrc
        )
    }

    private fun mapGenreToSpotify(genre: String): Pair<String?, String> {
        val lower = genre.lowercase().trim()
        return when {
            lower.contains("pop punk") -> Pair("punk", "genre:\"pop punk\"")
            lower.contains("metalcore") || lower.contains("metal") -> Pair("metal", "genre:\"metalcore\"")
            lower.contains("indie rock") || lower.contains("indie") -> Pair("indie", "genre:\"indie rock\"")
            lower.contains("lo-fi") || lower.contains("chill") -> Pair("chill", "genre:\"lo-fi\"")
            lower.contains("anime") || lower.contains("j-rock") -> Pair("j-rock", "genre:\"j-rock\"")
            lower.contains("pop indonesia") || lower.contains("indonesia") || lower.contains("indo") -> Pair(null, "genre:\"indonesian pop\"")
            lower.contains("hip-hop") || lower.contains("hip hop") -> Pair("hip-hop", "genre:\"hip-hop\"")
            lower.contains("skate punk") || lower.contains("grunge") -> Pair("grunge", "genre:\"skate punk\"")
            lower.contains("punk") -> Pair("punk", "genre:\"punk\"")
            lower.contains("rock") -> Pair("rock", "genre:\"rock\"")
            lower.contains("pop") -> Pair("pop", "genre:\"pop\"")
            else -> Pair(null, "genre:\"$genre\"")
        }
    }

    private fun readStream(connection: HttpURLConnection): String {
        val stream = try {
            if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        } catch (_: Exception) {
            connection.errorStream
        } ?: return ""
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }

    private fun getDemoPlaylists(): List<SpotifyPlaylist> {
        return listOf(
            SpotifyPlaylist(
                id = "demo_playlist_1",
                name = "My Chill Lo-Fi Mix",
                description = "Relaxing beats synced from your Spotify library",
                imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&auto=format&fit=crop&q=60",
                trackCount = 4,
                ownerName = "Alex Rivers"
            ),
            SpotifyPlaylist(
                id = "demo_playlist_2",
                name = "Cyberpunk & Synthwave",
                description = "High energy retro future sounds",
                imageUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&auto=format&fit=crop&q=60",
                trackCount = 3,
                ownerName = "Alex Rivers"
            ),
            SpotifyPlaylist(
                id = "demo_playlist_3",
                name = "Midnight Coffee Drive",
                description = "Late night acoustic and deep ambient melodies",
                imageUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=500&auto=format&fit=crop&q=60",
                trackCount = 3,
                ownerName = "Alex Rivers"
            )
        )
    }

    private fun getDemoPlaylistTracks(playlistId: String): List<Track> {
        return when (playlistId) {
            "demo_playlist_2" -> listOf(
                Track(
                    id = "sp_synth_1",
                    title = "Neon Highway",
                    artist = "Retro Lumina",
                    album = "Cyberpunk 2026",
                    durationSeconds = 423,
                    streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                    coverUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&auto=format&fit=crop&q=60",
                    genre = "Electronic"
                ),
                Track(
                    id = "sp_synth_2",
                    title = "Laser Grid Runner",
                    artist = "Outrun Drive",
                    album = "Synthwave Night",
                    durationSeconds = 310,
                    streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
                    coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&auto=format&fit=crop&q=60",
                    genre = "Electronic"
                )
            )
            "demo_playlist_3" -> listOf(
                Track(
                    id = "sp_coffee_1",
                    title = "Rainy Day Acoustic",
                    artist = "Sunday Morning Collective",
                    album = "Acoustic Sanctuary",
                    durationSeconds = 345,
                    streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                    coverUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=500&auto=format&fit=crop&q=60",
                    genre = "Acoustic"
                ),
                Track(
                    id = "sp_coffee_2",
                    title = "Tokyo Midnight Lights",
                    artist = "Komorebi",
                    album = "Midnight in Shibuya",
                    durationSeconds = 298,
                    streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                    coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&auto=format&fit=crop&q=60",
                    genre = "Lo-Fi"
                )
            )
            else -> listOf(
                Track(
                    id = "sp_lofi_1",
                    title = "Study Session Reverie",
                    artist = "Aether Waves",
                    album = "Lo-Fi Dreams",
                    durationSeconds = 372,
                    streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&auto=format&fit=crop&q=60",
                    genre = "Lo-Fi"
                ),
                Track(
                    id = "sp_lofi_2",
                    title = "Cosmic Floating Pulse",
                    artist = "Nova Stellar",
                    album = "Cosmic Reverie",
                    durationSeconds = 480,
                    streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
                    coverUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=500&auto=format&fit=crop&q=60",
                    genre = "Ambient"
                )
            )
        }
    }

    /**
     * Mengambil kode ISRC melalui endpoint detail track Spotify (GET /v1/tracks/{id})
     * jika lagu berasal dari endpoint album Spotify atau sumber lain yang belum memuat external_ids.
     */
    suspend fun fetchIsrcForTrack(trackId: String): String? = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix("sp_").substringAfter("track:")
        if (cleanId.isBlank()) return@withContext null
        try {
            val json = executeSpotifyGet("https://api.spotify.com/v1/tracks/$cleanId")
            val isrc = json?.optJSONObject("external_ids")?.optString("isrc")?.takeIf { it.isNotBlank() }
            if (!isrc.isNullOrBlank()) {
                val title = json.optString("name", "")
                val artists = json.optJSONArray("artists")
                val artist = if (artists != null && artists.length() > 0) {
                    artists.getJSONObject(0).optString("name", "")
                } else ""
                if (title.isNotBlank() && artist.isNotBlank()) {
                    AudioStreamResolver.putIsrc(title, artist, isrc)
                }
                return@withContext isrc
            }
        } catch (_: Exception) {}
        null
    }
}
