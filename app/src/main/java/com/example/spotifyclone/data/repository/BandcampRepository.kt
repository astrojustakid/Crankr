package com.example.spotifyclone.data.repository

import android.content.Context
import android.util.Log
import com.example.spotifyclone.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Model data artis resmi dari Bandcamp.
 */
data class BandcampArtist(
    val id: Long,
    val name: String,
    val location: String?,
    val photoUrl: String?,
    val bandcampUrl: String,
    val genre: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Model data detail profil artis/band lengkap dari Bandcamp.
 */
data class BandcampBandDetail(
    val id: Long,
    val name: String,
    val bio: String?,
    val location: String?,
    val photoUrl: String?,
    val bandcampUrl: String
)

/**
 * Model data album / EP rilisan resmi dari Bandcamp.
 */
data class BandcampAlbum(
    val id: Long,
    val bandId: Long,
    val title: String,
    val artistName: String,
    val coverUrl: String,
    val bandcampUrl: String,
    val releaseDate: String? = null,
    val price: String? = null
)

/**
 * Data pembungkus hasil pencarian Bandcamp (Trek, Album, Artis).
 */
data class BandcampSearchResult(
    val tracks: List<Track> = emptyList(),
    val albums: List<BandcampAlbum> = emptyList(),
    val artists: List<BandcampArtist> = emptyList()
)

/**
 * BandcampRepository:
 * Menghubungkan Crankr langsung ke katalog musik independen & underground Bandcamp:
 * - Search Autocomplete: https://bandcamp.com/api/fuzzysearch/1/app_autocomplete
 * - Tralbum Details: https://bandcamp.com/api/mobile/25/tralbum_details (Audio stream MP3 128kbps, durasi, harga, link direct support)
 * - Band Details: https://bandcamp.com/api/mobile/25/band_details (Profil artis & diskografi lengkap)
 *
 * Menerapkan prinsip zero-bloat, direct audio streaming untuk ExoPlayer, serta tautan
 * etis "Support The Artist" agar pendengar dapat membeli rilisan resmi langsung dari musisi.
 */
class BandcampRepository(private val context: Context? = null) {

    companion object {
        private const val TAG = "BANDCAMP_REPO"
        private const val USER_AGENT = "Bandcamp/2.3.0 (Android)"
        private const val NETWORK_TIMEOUT_MS = 8000

        private const val SEARCH_ENDPOINT = "https://bandcamp.com/api/fuzzysearch/1/app_autocomplete"
        private const val TRALBUM_ENDPOINT = "https://bandcamp.com/api/mobile/25/tralbum_details"
        private const val BAND_ENDPOINT = "https://bandcamp.com/api/mobile/25/band_details"

        // Cache in-memory
        private val searchCache = ConcurrentHashMap<String, BandcampSearchResult>()
        private val streamUrlCache = ConcurrentHashMap<String, String>()
        private val albumTracksCache = ConcurrentHashMap<String, List<Track>>()
        private val bandDetailsCache = ConcurrentHashMap<Long, BandcampBandDetail>()
        private val discographyCache = ConcurrentHashMap<Long, List<AlbumRelease>>()
        private val artistTracksCache = ConcurrentHashMap<Long, List<Track>>()

        /**
         * Mengubah suffix gambar resolusi rendah Bandcamp (_3.jpg, _23.jpg) menjadi _10.jpg (1200x1200 high-res).
         */
        fun formatHighResCover(rawUrl: String?): String {
            if (rawUrl.isNullOrBlank()) return ""
            return rawUrl.replace(Regex("""_\d+\.jpg$"""), "_10.jpg")
        }

        /**
         * Membersihkan format duplikasi URL yang terkadang dihasilkan oleh respons API Bandcamp.
         * Menjamin URL valid dengan protokol https:// dan domain lengkap.
         */
        fun sanitizeBandcampUrl(rawUrl: String?): String {
            if (rawUrl.isNullOrBlank()) return ""
            var u = rawUrl.trim()
            val lastHttps = u.lastIndexOf("https://")
            if (lastHttps > 0) u = u.substring(lastHttps)
            val lastHttp = u.lastIndexOf("http://")
            if (lastHttp > 0) u = u.substring(lastHttp)

            if (u.startsWith("//")) {
                u = "https:$u"
            } else if (!u.startsWith("http://") && !u.startsWith("https://")) {
                if (u.contains(".bandcamp.com")) {
                    u = "https://$u"
                } else if (u.startsWith("/")) {
                    u = "https://bandcamp.com$u"
                } else {
                    u = "https://$u"
                }
            }
            return u
        }
    }

    /**
     * Melakukan pencarian menyeluruh ke Bandcamp (Tracks, Albums, Bands) dengan query teks.
     */
    suspend fun search(query: String): BandcampSearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext BandcampSearchResult()

        val cacheKey = cleanQuery.lowercase()
        searchCache[cacheKey]?.let { return@withContext it }

        try {
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            val urlString = "$SEARCH_ENDPOINT?q=$encodedQuery"
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Search request failed with HTTP ${conn.responseCode}")
                conn.disconnect()
                return@withContext BandcampSearchResult()
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseBody)
            val resultsArray = json.optJSONArray("results") ?: JSONArray()

            val tracks = mutableListOf<Track>()
            val albums = mutableListOf<BandcampAlbum>()
            val artists = mutableListOf<BandcampArtist>()

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(i) ?: continue
                val type = item.optString("type", "")
                val id = item.optLong("id", 0L)
                val name = item.optString("name", "").trim()
                if (id == 0L || name.isBlank()) continue

                val rawImg = item.optString("img", "")
                val highResImg = formatHighResCover(rawImg)
                val rawUrl = item.optString("url", "")
                val cleanUrl = sanitizeBandcampUrl(rawUrl)

                when (type) {
                    "t" -> {
                        // Track
                        val bandId = item.optLong("band_id", 0L)
                        val bandName = item.optString("band_name", "Unknown Artist").trim()
                        val albumName = item.optString("album_name", "").trim().ifEmpty { "Single" }
                        val genre = item.optString("genre_name", "").trim().ifEmpty { "Indie / Punk" }

                        val streamUrlPlaceholder = "https://bandcamp.com/stream_redirect?enc=mp3-128&track_id=$id"

                        tracks.add(
                            Track(
                                id = "bc_${bandId}_$id",
                                title = name,
                                artist = bandName,
                                album = albumName,
                                durationSeconds = 180,
                                streamUrl = streamUrlPlaceholder,
                                coverUrl = highResImg,
                                genre = genre,
                                spotifyArtistId = if (bandId > 0) "bc_$bandId" else null,
                                bandcampUrl = cleanUrl,
                                source = "bandcamp"
                            )
                        )
                    }
                    "a" -> {
                        // Album
                        val bandId = item.optLong("band_id", 0L)
                        val bandName = item.optString("band_name", "Unknown Artist").trim()

                        albums.add(
                            BandcampAlbum(
                                id = id,
                                bandId = bandId,
                                title = name,
                                artistName = bandName,
                                coverUrl = highResImg,
                                bandcampUrl = cleanUrl
                            )
                        )
                    }
                    "b" -> {
                        // Band / Artist
                        val location = item.optString("location", "").trim().ifEmpty { null }
                        val genre = item.optString("genre_name", "").trim().ifEmpty { null }
                        val tagsArray = item.optJSONArray("tag_names")
                        val tags = mutableListOf<String>()
                        if (tagsArray != null) {
                            for (t in 0 until tagsArray.length()) {
                                val tag = tagsArray.optString(t, "").trim()
                                if (tag.isNotBlank()) tags.add(tag)
                            }
                        }

                        artists.add(
                            BandcampArtist(
                                id = id,
                                name = name,
                                location = location,
                                photoUrl = highResImg,
                                bandcampUrl = cleanUrl,
                                genre = genre,
                                tags = tags
                            )
                        )
                    }
                }
            }

            val searchResult = BandcampSearchResult(
                tracks = tracks,
                albums = albums,
                artists = artists
            )
            searchCache[cacheKey] = searchResult
            searchResult
        } catch (e: Exception) {
            Log.e(TAG, "Error saat mencari di Bandcamp: ${e.message}", e)
            BandcampSearchResult()
        }
    }

    /**
     * Mengambil daftar lagu dari hasil pencarian Bandcamp secara praktis.
     * Jika trek mandiri sedikit (< 6) dan terdapat album, secara otomatis memuat trek dari album teratas
     * secara paralel agar hasil pencarian kaya lagu yang siap diputar instan.
     */
    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        val searchResult = search(query)
        val directTracks = searchResult.tracks.toMutableList()

        if (directTracks.size < 6 && searchResult.albums.isNotEmpty()) {
            val topAlbums = searchResult.albums.take(3)
            val deferredAlbums = topAlbums.map { album ->
                async {
                    try {
                        getAlbumTracks(album.id, album.bandId)
                    } catch (_: Exception) {
                        emptyList<Track>()
                    }
                }
            }
            val albumTracksList = deferredAlbums.awaitAll().flatten()
            val existingIds = directTracks.map { it.id }.toSet()
            albumTracksList.forEach { track ->
                if (track.id !in existingIds) {
                    directTracks.add(track)
                }
            }
        }
        directTracks
    }

    /**
     * Mengambil detail lengkap sebuah album Bandcamp beserta seluruh daftar lagunya yang siap diputar.
     */
    suspend fun getAlbumTracks(albumId: Long, bandId: Long): List<Track> = withContext(Dispatchers.IO) {
        val cacheKey = "album_${bandId}_$albumId"
        albumTracksCache[cacheKey]?.let { return@withContext it }

        try {
            val urlString = "$TRALBUM_ENDPOINT?band_id=$bandId&tralbum_type=a&tralbum_id=$albumId"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseBody)
            val albumTitle = json.optString("album_title", "").ifEmpty { json.optString("title", "Album") }
            val rawAlbumUrl = json.optString("bandcamp_url", "")
            val cleanAlbumUrl = sanitizeBandcampUrl(rawAlbumUrl)

            val bandObj = json.optJSONObject("band")
            val defaultArtist = json.optString("tralbum_artist", "").ifEmpty {
                bandObj?.optString("name", "Unknown Artist") ?: "Unknown Artist"
            }

            val artId = json.optLong("art_id", 0L)
            val albumCover = if (artId > 0) "https://f4.bcbits.com/img/a${artId}_10.jpg" else ""

            val currency = json.optString("currency", "USD")
            val albumPrice = if (json.optBoolean("is_purchasable", false)) {
                val priceVal = json.optDouble("price", 0.0)
                if (priceVal > 0) "$currency $priceVal" else null
            } else null

            val tracksArray = json.optJSONArray("tracks") ?: JSONArray()
            val resultTracks = mutableListOf<Track>()

            for (i in 0 until tracksArray.length()) {
                val tObj = tracksArray.optJSONObject(i) ?: continue
                val trackId = tObj.optLong("track_id", 0L)
                val title = tObj.optString("title", "").trim()
                if (trackId == 0L || title.isBlank()) continue

                val durationDouble = tObj.optDouble("duration", 180.0)
                val durationSec = Math.round(durationDouble).toInt().coerceAtLeast(1)

                val isStreamable = tObj.optBoolean("is_streamable", true)
                val streamingUrlObj = tObj.optJSONObject("streaming_url")
                val streamUrl = streamingUrlObj?.optString("mp3-128", "") ?: ""

                val rawTrackUrl = tObj.optString("track_url", "").ifEmpty { cleanAlbumUrl }
                val cleanTrackUrl = sanitizeBandcampUrl(rawTrackUrl)

                val trackArtist = tObj.optString("band_name", "").ifEmpty { defaultArtist }
                val trackArtId = tObj.optLong("art_id", 0L)
                val coverUrl = if (trackArtId > 0) "https://f4.bcbits.com/img/a${trackArtId}_10.jpg" else albumCover

                val trackPrice = if (tObj.optBoolean("is_purchasable", false)) {
                    val pVal = tObj.optDouble("price", 0.0)
                    if (pVal > 0) "$currency $pVal" else albumPrice
                } else albumPrice

                val finalStreamUrl = if (isStreamable && streamUrl.isNotBlank()) {
                    streamUrl
                } else if (isStreamable && streamingUrlObj != null) {
                    "https://bandcamp.com/stream_redirect?enc=mp3-128&track_id=$trackId"
                } else {
                    "" // Purchase-only / locked
                }

                // Cache stream URL langsung jika didapatkan
                if (streamUrl.isNotBlank()) {
                    streamUrlCache["bc_${bandId}_$trackId"] = streamUrl
                }

                resultTracks.add(
                    Track(
                        id = "bc_${bandId}_$trackId",
                        title = title,
                        artist = trackArtist,
                        album = albumTitle,
                        durationSeconds = durationSec,
                        streamUrl = finalStreamUrl,
                        coverUrl = coverUrl,
                        genre = "Indie / Punk",
                        spotifyArtistId = "bc_$bandId",
                        bandcampUrl = cleanTrackUrl,
                        source = "bandcamp",
                        purchasePrice = trackPrice
                    )
                )
            }

            albumTracksCache[cacheKey] = resultTracks
            resultTracks
        } catch (e: Exception) {
            Log.e(TAG, "Error saat mengambil album tracks: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Mengambil URL streaming MP3 128kbps langsung untuk sebuah trek Bandcamp.
     */
    suspend fun resolveStreamUrl(bandId: Long, trackId: Long): String? = withContext(Dispatchers.IO) {
        val cacheKey = "bc_${bandId}_$trackId"
        streamUrlCache[cacheKey]?.let { return@withContext it }

        try {
            val urlString = "$TRALBUM_ENDPOINT?band_id=$bandId&tralbum_type=t&tralbum_id=$trackId"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext null
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseBody)
            val tracksArray = json.optJSONArray("tracks")
            val firstTrack = tracksArray?.optJSONObject(0)
            val isStreamable = firstTrack?.optBoolean("is_streamable", true) ?: true
            val streamUrlObj = firstTrack?.optJSONObject("streaming_url")
            val streamUrl = streamUrlObj?.optString("mp3-128", "")?.ifEmpty { null }

            if (!isStreamable || streamUrlObj == null || streamUrl.isNullOrBlank()) {
                return@withContext null
            }

            streamUrlCache[cacheKey] = streamUrl
            streamUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving Bandcamp stream URL: ${e.message}")
            null
        }
    }

    /**
     * Mengambil detail satu trek mandiri (single) dari Bandcamp via tralbum_details.
     */
    suspend fun resolveTrackDetails(bandId: Long, trackId: Long): Track? = withContext(Dispatchers.IO) {
        val cacheKey = "bc_${bandId}_$trackId"
        try {
            val urlString = "$TRALBUM_ENDPOINT?band_id=$bandId&tralbum_type=t&tralbum_id=$trackId"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext null
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseBody)
            val bandObj = json.optJSONObject("band")
            val defaultArtist = json.optString("tralbum_artist", "").ifEmpty {
                bandObj?.optString("name", "Unknown Artist") ?: "Unknown Artist"
            }
            val artId = json.optLong("art_id", 0L)
            val defaultCover = if (artId > 0) "https://f4.bcbits.com/img/a${artId}_10.jpg" else ""

            val tracksArray = json.optJSONArray("tracks") ?: JSONArray()
            val tObj = tracksArray.optJSONObject(0) ?: return@withContext null

            val title = tObj.optString("title", "").trim().ifEmpty { json.optString("title", "") }
            if (title.isBlank()) return@withContext null

            val durationDouble = tObj.optDouble("duration", 180.0)
            val durationSec = Math.round(durationDouble).toInt().coerceAtLeast(1)

            val isStreamable = tObj.optBoolean("is_streamable", true)
            val streamingUrlObj = tObj.optJSONObject("streaming_url")
            val streamUrl = streamingUrlObj?.optString("mp3-128", "") ?: ""

            val trackArtId = tObj.optLong("art_id", 0L)
            val coverUrl = if (trackArtId > 0) "https://f4.bcbits.com/img/a${trackArtId}_10.jpg" else defaultCover

            val trackArtist = tObj.optString("band_name", "").ifEmpty { defaultArtist }
            val rawTrackUrl = tObj.optString("track_url", "").ifEmpty { json.optString("bandcamp_url", "") }
            val cleanTrackUrl = sanitizeBandcampUrl(rawTrackUrl)

            val finalStreamUrl = if (isStreamable && streamUrl.isNotBlank()) {
                streamUrl
            } else if (isStreamable) {
                "https://bandcamp.com/stream_redirect?enc=mp3-128&track_id=$trackId"
            } else ""

            if (streamUrl.isNotBlank()) {
                streamUrlCache[cacheKey] = streamUrl
            }

            Track(
                id = cacheKey,
                title = title,
                artist = trackArtist,
                album = "Single",
                durationSeconds = durationSec,
                streamUrl = finalStreamUrl,
                coverUrl = coverUrl,
                genre = "Indie / Punk",
                spotifyArtistId = "bc_$bandId",
                bandcampUrl = cleanTrackUrl,
                source = "bandcamp"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving single track $trackId: ${e.message}")
            null
        }
    }

    /**
     * Mengambil seluruh lagu resmi yang benar-benar dirilis oleh artis/band (berdasarkan bandId)
     * dari semua rilisannya di Bandcamp secara paralel.
     * Menjamin kepemilikan mutlak 100% lagu milik musisi tersebut tanpa pencampuran artis luar.
     */
    suspend fun getArtistTracks(bandId: Long, limit: Int = 25): List<Track> = withContext(Dispatchers.IO) {
        if (bandId <= 0L) return@withContext emptyList()
        artistTracksCache[bandId]?.let { return@withContext it }

        try {
            val urlString = "$BAND_ENDPOINT?band_id=$bandId"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseBody)
            val discographyArray = json.optJSONArray("discography") ?: JSONArray()
            val totalReleases = discographyArray.length()
            if (totalReleases == 0) return@withContext emptyList()

            // Ambil maksimal 8 rilisan teratas untuk mendapatkan kumpulan trek resmi musisi
            val takeCount = totalReleases.coerceAtMost(8)
            val deferredTracks = (0 until takeCount).map { i ->
                val item = discographyArray.optJSONObject(i)
                val itemId = item?.optLong("item_id", 0L) ?: 0L
                val itemType = item?.optString("item_type", "album")?.lowercase() ?: "album"
                async {
                    if (itemId > 0L) {
                        try {
                            if (itemType == "track") {
                                val single = resolveTrackDetails(bandId, itemId)
                                if (single != null) listOf(single) else emptyList()
                            } else {
                                getAlbumTracks(itemId, bandId)
                            }
                        } catch (_: Exception) {
                            emptyList<Track>()
                        }
                    } else emptyList()
                }
            }

            val allTracksLists = deferredTracks.awaitAll().flatten()
            val uniqueTracks = mutableListOf<Track>()
            val seenIds = mutableSetOf<String>()

            for (track in allTracksLists) {
                // Pastikan lagu benar-benar memiliki ID atau band_id yang sesuai
                if (track.id.startsWith("bc_${bandId}_") || track.spotifyArtistId == "bc_$bandId") {
                    if (seenIds.add(track.id)) {
                        uniqueTracks.add(track)
                    }
                }
            }

            val result = if (uniqueTracks.isNotEmpty()) {
                uniqueTracks.take(limit)
            } else {
                emptyList()
            }

            if (result.isNotEmpty()) {
                artistTracksCache[bandId] = result
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error saat mengambil trek resmi artis Bandcamp: ${e.message}")
            emptyList()
        }
    }

    /**
     * Mengambil detail profil artis/band lengkap dari Bandcamp (nama, bio, foto high-res, tautan).
     */
    suspend fun getBandDetails(bandId: Long): BandcampBandDetail? = withContext(Dispatchers.IO) {
        val cacheKey = bandId
        bandDetailsCache[cacheKey]?.let { return@withContext it }

        try {
            val urlString = "$BAND_ENDPOINT?band_id=$bandId"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext null
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseBody)
            val name = json.optString("name", "Unknown Artist")
            val bio = json.optString("bio", "").ifEmpty { null }
            val location = json.optString("location", "").ifEmpty { null }
            val bioImageId = json.optLong("bio_image_id", 0L)
            val photoUrl = if (bioImageId > 0) "https://f4.bcbits.com/img/00${bioImageId}_10.jpg" else null
            val rawUrl = json.optString("bandcamp_url", "")
            val bandcampUrl = sanitizeBandcampUrl(rawUrl)

            val detail = BandcampBandDetail(
                id = bandId,
                name = name,
                bio = bio,
                location = location,
                photoUrl = photoUrl,
                bandcampUrl = bandcampUrl
            )
            bandDetailsCache[cacheKey] = detail
            detail
        } catch (e: Exception) {
            Log.e(TAG, "Error saat mengambil detail band Bandcamp: ${e.message}")
            null
        }
    }

    /**
     * Mengambil diskografi album resmi band dari Bandcamp yang langsung kompatibel dengan format AlbumRelease Crankr.
     */
    suspend fun getArtistDiscography(bandId: Long): List<AlbumRelease> = withContext(Dispatchers.IO) {
        discographyCache[bandId]?.let { return@withContext it }

        try {
            val urlString = "$BAND_ENDPOINT?band_id=$bandId"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseBody)
            val bandName = json.optString("name", "Unknown Artist")
            val discographyArray = json.optJSONArray("discography") ?: JSONArray()
            val releases = mutableListOf<AlbumRelease>()

            for (i in 0 until discographyArray.length()) {
                val item = discographyArray.optJSONObject(i) ?: continue
                val itemId = item.optLong("item_id", 0L)
                val itemType = item.optString("item_type", "album").lowercase()
                val title = item.optString("title", "").trim()
                if (itemId == 0L || title.isBlank()) continue

                val artId = item.optLong("art_id", 0L)
                val coverUrl = if (artId > 0) "https://f4.bcbits.com/img/a${artId}_10.jpg" else ""
                val releaseDate = item.optString("release_date", "").trim()
                val releaseYear = Regex("""\b(19\d\d|20\d\d)\b""").find(releaseDate)?.value ?: "2024"

                releases.add(
                    AlbumRelease(
                        id = "bc_album_${bandId}_$itemId",
                        title = title,
                        artistName = bandName,
                        releaseType = if (itemType == "album") "Album" else "Single",
                        releaseYear = releaseYear,
                        releaseDate = releaseDate,
                        coverUrl = coverUrl,
                        trackCount = 1,
                        collectionId = "bc_${bandId}_$itemId",
                        spotifyId = "bc_${bandId}_$itemId",
                        artistId = "bc_$bandId"
                    )
                )
            }
            discographyCache[bandId] = releases
            releases
        } catch (e: Exception) {
            Log.e(TAG, "Error saat mengambil diskografi Bandcamp: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Mengambil diskografi album artis/band dari Bandcamp.
     */
    suspend fun getArtistAlbums(bandId: Long): List<BandcampAlbum> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$BAND_ENDPOINT?band_id=$bandId"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseBody)
            val bandName = json.optString("name", "Unknown Artist")
            val discographyArray = json.optJSONArray("discography") ?: JSONArray()
            val albums = mutableListOf<BandcampAlbum>()

            for (i in 0 until discographyArray.length()) {
                val item = discographyArray.optJSONObject(i) ?: continue
                val itemId = item.optLong("item_id", 0L)
                val title = item.optString("title", "").trim()
                if (itemId == 0L || title.isBlank()) continue

                val artId = item.optLong("art_id", 0L)
                val coverUrl = if (artId > 0) "https://f4.bcbits.com/img/a${artId}_10.jpg" else ""
                val releaseDate = item.optString("release_date", "").trim().ifEmpty { null }

                albums.add(
                    BandcampAlbum(
                        id = itemId,
                        bandId = bandId,
                        title = title,
                        artistName = bandName,
                        coverUrl = coverUrl,
                        bandcampUrl = json.optString("bandcamp_url", ""),
                        releaseDate = releaseDate
                    )
                )
            }
            albums
        } catch (e: Exception) {
            Log.e(TAG, "Error saat mengambil diskografi Bandcamp artis: ${e.message}", e)
            emptyList()
        }
    }
}

