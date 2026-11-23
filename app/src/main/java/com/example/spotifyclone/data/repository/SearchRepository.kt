package com.example.spotifyclone.data.repository

import android.content.Context
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.MusicStreamResolver
import com.example.spotifyclone.service.spotify.SpotifyApiService
import com.example.spotifyclone.util.CrankrCatalogFilter
import com.example.spotifyclone.util.MetadataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Model data profil artis resmi yang ditemukan dari Spotify Catalog Search.
 */
data class SearchArtistProfile(
    val id: String,
    val name: String,
    val photoUrl: String?,
    val followers: Int = 0,
    val genres: List<String> = emptyList(),
    val source: String = "spotify"
) {
    val isBandcamp: Boolean get() = source == "bandcamp" || id.startsWith("bc_")
}

/**
 * Data pembungkus hasil pencarian: profil artis resmi (jika ada) dan daftar lagu resmi.
 */
data class SearchResultData(
    val matchedArtist: SearchArtistProfile? = null,
    val tracks: List<Track> = emptyList()
)

/**
 * Repository yang mengelola pencarian musik terverifikasi:
 * - Memprioritaskan katalog resmi Spotify Web API (type=track,artist) daripada kueri YouTube mentah.
 * - Menampilkan profil artis resmi dan lagu resmi artis di posisi paling atas (misal: "The Skit" -> "Bab III (Revisi)", "Laut Tinggal Kenangan").
 * - Membuang artefak web/sistem (pintasan keyboard, video bundar, watch full, subscribe, teaser).
 * - Membuang lagu palsu di mana judul lagu sama persis dengan nama band (kecuali terdaftar resmi di Spotify).
 */
class SearchRepository(
    private val spotifyApiService: SpotifyApiService = SpotifyApiService(),
    private val genreMoodRepository: GenreMoodRepository = GenreMoodRepository(),
    private val youTubeMusicRepository: YouTubeMusicRepository = YouTubeMusicRepository(),
    private val bandcampRepository: BandcampRepository = BandcampRepository()
) {
    constructor(context: Context) : this(
        spotifyApiService = SpotifyApiService(),
        genreMoodRepository = GenreMoodRepository(context),
        youTubeMusicRepository = YouTubeMusicRepository(context),
        bandcampRepository = BandcampRepository(context)
    )

    companion object {
        const val NETWORK_TIMEOUT_MS = 8000

        // Database ID Spotify resmi artis skena untuk pencocokan instan tanpa latensi
        val KNOWN_SCENE_ARTIST_IDS = mapOf(
            "the skit" to "3SSbowOdeBgnukoCm6dGqT",
            "dongker" to "6sf1OnuDZM48bgFQvPkbYm",
            "the kick" to "5acyN68XSrCN4Jl9miQE1O",
            "the jansen" to "0q3MCOdd2qGlN2TxJdr0SQ",
            "morfem" to "3JoyBiTkzGqZCWwq07no59",
            "grrrl gang" to "4GgA61hzcYno3GYTrjhZ6A",
            "silampukau" to "40ucFk6nwFWYZ4gvL9DNm8",
            "pee wee gaskins" to "62USA8ccl2yj1sSVyqL2LR",
            "rocket rockers" to "3k7qB8d47YeYcHlrqaLM4U",
            "sheila on 7" to "6q87vizIEdEN4NvlR6mjfT",
            "bernadya" to "47z98pKd71yIbgXwe9LPVC",
            "mahalini" to "3wOsYKZM0zcKNasi3I7fP4"
        )
    }

    /**
     * Menjalankan pencarian resmi YouTube Music InnerTube API (Filter Songs & Artists)
     * di mana setiap lagu sudah langsung membawa videoId resmi YouTube Music untuk pemutaran instan.
     */
    suspend fun searchCatalog(query: String, limit: Int = 20): SearchResultData = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext SearchResultData()

        val mappedQuery = if (cleanQuery.contains("melancholic bitch", ignoreCase = true)) {
            cleanQuery.replace(Regex("(?i)melancholic\\s+bitch"), "Majelis Lidah Berduri")
        } else {
            cleanQuery
        }

        val wordCount = cleanQuery.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val isLikelyLyrics = wordCount >= 3 && !KNOWN_SCENE_ARTIST_IDS.containsKey(cleanQuery.lowercase())

        // Jika kueri adalah kalimat panjang (indikasi penggalan lirik), prioritaskan pencarian lirik
        if (isLikelyLyrics) {
            try {
                val lyricsResult = searchByLyrics(mappedQuery, limit)
                if (lyricsResult.tracks.isNotEmpty()) {
                    return@withContext lyricsResult
                }
            } catch (_: Exception) {}
        }

        // 1. Prioritas Utama: YouTube Music InnerTube API murni (WEB_REMIX) & Bandcamp Underground
        val bcTracks = try {
            bandcampRepository.searchTracks(mappedQuery).take(8)
        } catch (_: Exception) {
            emptyList()
        }

        try {
            val ytmResult = youTubeMusicRepository.searchCatalog(mappedQuery, limit)
            if (ytmResult.tracks.isNotEmpty() || ytmResult.matchedArtist != null) {
                val combined = mutableListOf<Track>()
                combined.addAll(ytmResult.tracks)
                val existingKeys = ytmResult.tracks.map { "${it.title.lowercase()} ${it.artist.lowercase()}" }.toSet()
                bcTracks.forEach { bc ->
                    val key = "${bc.title.lowercase()} ${bc.artist.lowercase()}"
                    if (key !in existingKeys) {
                        combined.add(bc)
                    }
                }
                return@withContext ytmResult.copy(tracks = combined)
            }
        } catch (e: Exception) {
            // Lanjut ke fallback jika ada kendala
        }

        // Jika YouTube Music kosong tetapi Bandcamp memiliki lagu rilis
        if (bcTracks.isNotEmpty()) {
            return@withContext SearchResultData(tracks = bcTracks)
        }

        // 2. Fallback Spotify Web API (type=track,artist)
        try {
            val spotifyResult = searchOfficialSpotifyCatalogWithArtist(mappedQuery, limit)
            if (spotifyResult.tracks.isNotEmpty() || spotifyResult.matchedArtist != null) {
                return@withContext spotifyResult
            }
        } catch (_: Exception) {
        }

        // 3. Fallback pencarian penggalan lirik sebelum resolver mentah
        try {
            val lyricsFallback = searchByLyrics(mappedQuery, limit)
            if (lyricsFallback.tracks.isNotEmpty()) {
                return@withContext lyricsFallback
            }
        } catch (_: Exception) {}

        // 4. Fallback stream resolver jika Spotify gagal, terapkan sanitasi ketat
        val rawResults = MusicStreamResolver.searchTracks(mappedQuery)
        val sanitized = MetadataSanitizer.sanitizeTracks(rawResults).take(limit)

        SearchResultData(matchedArtist = null, tracks = sanitized)
    }

    /**
     * Pencarian khusus katalog rilisan musisi dan label underground Bandcamp.
     * Mengembalikan profil artis resmi Bandcamp dan daftar trek Bandcamp eksklusif
     * tanpa campuran hasil YouTube Music atau Spotify.
     * Jika ditemukan kecocokan artis (matchedArtist), hasil trek disaring secara ketat
     * agar HANYA menampilkan lagu resmi milik band tersebut.
     */
    suspend fun searchBandcampCatalog(query: String, limit: Int = 20): SearchResultData = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext SearchResultData()

        try {
            val searchResult = bandcampRepository.search(cleanQuery)
            val matchedArtistEntity = searchResult.artists.firstOrNull {
                it.name.equals(cleanQuery, ignoreCase = true)
            } ?: searchResult.artists.firstOrNull {
                val aName = it.name.lowercase().trim()
                val qLower = cleanQuery.lowercase().trim()
                aName.startsWith("$qLower ") || aName.endsWith(" $qLower") ||
                qLower.startsWith("$aName ") || qLower.endsWith(" $aName") ||
                (qLower.length >= 4 && aName.contains(qLower)) ||
                (aName.length >= 4 && qLower.contains(aName))
            }

            val matchedArtist = matchedArtistEntity?.let { artist ->
                SearchArtistProfile(
                    id = "bc_${artist.id}",
                    name = artist.name,
                    photoUrl = artist.photoUrl,
                    followers = 0,
                    genres = artist.tags.ifEmpty { listOfNotNull(artist.genre) },
                    source = "bandcamp"
                )
            }

            val finalTracks = mutableListOf<Track>()

            if (matchedArtistEntity != null && matchedArtistEntity.id > 0L) {
                // 1. Prioritas utama: Ambil lagu resmi langsung dari diskografi musisi tersebut
                val officialBandTracks = bandcampRepository.getArtistTracks(matchedArtistEntity.id, limit = limit)
                finalTracks.addAll(officialBandTracks)

                // 2. Trek dari hasil pencarian umum disaring secara KETAT:
                // HANYA meloloskan lagu yang memiliki bandId yang sama atau nama musisi yang persis sama
                val searchTracks = bandcampRepository.searchTracks(cleanQuery)
                val filteredSearchTracks = searchTracks.filter { track ->
                    val isSameBandId = track.id.startsWith("bc_${matchedArtistEntity.id}_") ||
                        track.spotifyArtistId == "bc_${matchedArtistEntity.id}"
                    val isExactArtist = track.artist.equals(matchedArtistEntity.name, ignoreCase = true)
                    isSameBandId || isExactArtist
                }
                for (st in filteredSearchTracks) {
                    if (finalTracks.none { it.id == st.id }) {
                        finalTracks.add(st)
                    }
                }
            } else {
                // Jika tidak ada profil artis spesifik, saring lagu agar relevan secara ketat dengan kueri pengguna
                val searchTracks = bandcampRepository.searchTracks(cleanQuery)
                val qLower = cleanQuery.lowercase().trim()
                val relevantTracks = searchTracks.filter { track ->
                    val aLower = track.artist.lowercase().trim()
                    val tLower = track.title.lowercase().trim()
                    aLower.contains(qLower) || qLower.contains(aLower) || tLower.contains(qLower)
                }
                finalTracks.addAll(relevantTracks)
            }

            SearchResultData(
                matchedArtist = matchedArtist,
                tracks = finalTracks.take(limit)
            )
        } catch (e: Exception) {
            SearchResultData()
        }
    }

    /**
     * Pencarian lagu berdasarkan potongan lirik (Lyrics Snippet Search):
     * 1. Mengarahkan kueri ke endpoint YouTube Music InnerTube (Filter Songs) yang mengindeks teks lirik secara native.
     * 2. Menjalankan kueri pelengkap ke penyedia lirik LRCLIB API resmi (https://lrclib.net/api/search?q=...)
     *    untuk mendeteksi lagu yang memuat baris lirik tersebut.
     * 3. Menyaring hasil dengan filter anti-cover & anti-live (CrankrCatalogFilter) dan memastikan setiap lagu
     *    membawa videoId YouTube Music resmi agar dapat langsung diputar secara instan tanpa jeda.
     */
    suspend fun searchByLyrics(lyricsSnippet: String, limit: Int = 20): SearchResultData = withContext(Dispatchers.IO) {
        val cleanSnippet = lyricsSnippet.trim()
        if (cleanSnippet.isBlank()) return@withContext SearchResultData()

        val results = mutableListOf<Track>()
        val seenVids = mutableSetOf<String>()

        // 1. YouTube Music InnerTube API (Filter Songs) dengan kueri teks lirik
        try {
            val ytmSongs = youTubeMusicRepository.searchSongs(cleanSnippet, limit)
            for (track in ytmSongs) {
                val vid = track.youtubeVideoId ?: track.id.removePrefix("yt_")
                if (vid.isNotBlank() && seenVids.add(vid)) {
                    results.add(track)
                }
            }
        } catch (_: Exception) {}

        // Tambahan percobaan dengan varian kueri lyrics jika hasil masih sedikit
        if (results.size < 5) {
            try {
                val ytmLyricsVariant = youTubeMusicRepository.searchSongs("$cleanSnippet lyrics", limit)
                for (track in ytmLyricsVariant) {
                    val vid = track.youtubeVideoId ?: track.id.removePrefix("yt_")
                    if (vid.isNotBlank() && seenVids.add(vid)) {
                        results.add(track)
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Kueri penyedia lirik LRCLIB API resmi jika hasil masih kurang dari batas limit
        if (results.size < limit) {
            try {
                val lrclibCandidates = searchLrcLibByLyricsSnippet(cleanSnippet, limit = 5)
                for (cand in lrclibCandidates) {
                    val resolved = youTubeMusicRepository.searchSongs("${cand.title} ${cand.artist}", limit = 1)
                    for (song in resolved) {
                        val vid = song.youtubeVideoId ?: song.id.removePrefix("yt_")
                        if (vid.isNotBlank() && seenVids.add(vid)) {
                            results.add(song)
                        }
                    }
                    if (results.size >= limit) break
                }
            } catch (_: Exception) {}
        }

        val filtered = filterStrictly(results).take(limit)
        SearchResultData(matchedArtist = null, tracks = filtered)
    }

    /**
     * Kueri pencarian teks bebas ke LRCLIB API: GET https://lrclib.net/api/search?q={snippet}
     */
    private suspend fun searchLrcLibByLyricsSnippet(snippet: String, limit: Int = 5): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        try {
            val encoded = URLEncoder.encode(snippet, "UTF-8")
            val url = URL("https://lrclib.net/api/search?q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CrankrMusicApp/1.0 (contact@crankr.id)")
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val array = JSONArray(resp)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val title = item.optString("trackName").takeIf { it.isNotBlank() } ?: item.optString("name")
                    val artist = item.optString("artistName")
                    val album = item.optString("albumName", "Single")
                    val duration = item.optInt("duration", 210)
                    if (title.isNotBlank() && artist.isNotBlank()) {
                        tracks.add(
                            Track(
                                id = "lrclib_${item.optLong("id", i.toLong())}",
                                title = title,
                                artist = artist,
                                album = album,
                                durationSeconds = duration,
                                streamUrl = "",
                                coverUrl = ""
                            )
                        )
                        if (tracks.size >= limit) break
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (_: Exception) {}
        tracks
    }

    /**
     * Versi kompatibel lama yang mengembalikan List<Track>.
     */
    suspend fun searchTracks(query: String, limit: Int = 20): List<Track> {
        return searchCatalog(query, limit).tracks
    }

    /**
     * Pencarian khusus katalog independen & underground Bandcamp.
     */
    suspend fun searchBandcamp(query: String, limit: Int = 20): SearchResultData = withContext(Dispatchers.IO) {
        try {
            val tracks = bandcampRepository.searchTracks(query).take(limit)
            SearchResultData(tracks = tracks)
        } catch (_: Exception) {
            SearchResultData()
        }
    }

    /**
     * Mengambil rekomendasi genre resmi melalui GenreMoodRepository (dengan cache 12 jam).
     */
    suspend fun getGenreRecommendations(genre: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        genreMoodRepository.getCategoryTracks(genre, limit)
    }

    /**
     * Memanggil langsung Spotify Web API: GET /v1/search?q={query}&type=track,artist&limit={limit}&market=ID
     * dengan batas waktu jaringan 8 detik.
     */
    private suspend fun searchOfficialSpotifyCatalogWithArtist(query: String, limit: Int): SearchResultData {
        val token = spotifyApiService.getSpotifyToken() ?: return SearchResultData()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://api.spotify.com/v1/search?q=$encodedQuery&type=track,artist&limit=$limit&market=ID")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
        }

        if (conn.responseCode != 200) {
            conn.disconnect()
            return SearchResultData()
        }

        val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(responseStr)

        val resultTracks = mutableListOf<Track>()
        var matchedArtist: SearchArtistProfile? = null

        val queryLower = query.lowercase().trim()

        // 1. Periksa apakah kueri cocok dengan nama musisi / band di Spotify
        val artistsArr = json.optJSONObject("artists")?.optJSONArray("items")
        if (artistsArr != null && artistsArr.length() > 0) {
            for (i in 0 until artistsArr.length()) {
                val aObj = artistsArr.optJSONObject(i) ?: continue
                val aName = aObj.optString("name", "").trim()
                val aId = aObj.optString("id", "").trim()
                if (aName.isBlank() || aId.isBlank()) continue

                val isExactOrKnown = aName.equals(queryLower, ignoreCase = true) ||
                        queryLower.equals(aName, ignoreCase = true) ||
                        KNOWN_SCENE_ARTIST_IDS[queryLower] == aId ||
                        KNOWN_SCENE_ARTIST_IDS.containsKey(queryLower)

                if (isExactOrKnown) {
                    val imagesArr = aObj.optJSONArray("images")
                    val photoUrl = if (imagesArr != null && imagesArr.length() > 0) {
                        imagesArr.getJSONObject(0).optString("url")
                    } else null

                    val followers = aObj.optJSONObject("followers")?.optInt("total", 0) ?: 0
                    val genresList = mutableListOf<String>()
                    val gArr = aObj.optJSONArray("genres")
                    if (gArr != null) {
                        for (g in 0 until gArr.length()) {
                            genresList.add(gArr.optString(g))
                        }
                    }

                    matchedArtist = SearchArtistProfile(
                        id = aId,
                        name = aName,
                        photoUrl = photoUrl,
                        followers = followers,
                        genres = genresList
                    )

                    // Ambil lagu resmi rilisan band tersebut dari top-tracks Spotify
                    val artistTopTracks = fetchArtistOfficialTopTracks(aId)
                    resultTracks.addAll(artistTopTracks)
                    break
                }
            }
        }

        // Jika artis dikenal ada di mapping tetapi belum terambil dari search
        if (matchedArtist == null && KNOWN_SCENE_ARTIST_IDS.containsKey(queryLower)) {
            val knownId = KNOWN_SCENE_ARTIST_IDS[queryLower]!!
            val artistTopTracks = fetchArtistOfficialTopTracks(knownId)
            if (artistTopTracks.isNotEmpty()) {
                val first = artistTopTracks.first()
                matchedArtist = SearchArtistProfile(
                    id = knownId,
                    name = first.artist,
                    photoUrl = first.coverUrl,
                    followers = 10000,
                    genres = listOf("Indie", "Rock")
                )
                resultTracks.addAll(artistTopTracks)
            }
        }

        // 2. Ambil trek dari Spotify Search (type=track)
        val tracksArr = json.optJSONObject("tracks")?.optJSONArray("items")
        if (tracksArr != null && tracksArr.length() > 0) {
            for (i in 0 until tracksArr.length()) {
                val tObj = tracksArr.optJSONObject(i) ?: continue
                val parsed = parseSpotifyTrack(tObj) ?: continue

                // Terapkan sanitasi ketat:
                val isClean = CrankrCatalogFilter.isValidSearchTrack(
                    title = parsed.title,
                    artist = parsed.artist,
                    isOfficialSpotify = true,
                    durationSeconds = parsed.durationSeconds
                )

                if (isClean && resultTracks.none { it.id == parsed.id || (it.title.equals(parsed.title, true) && it.artist.equals(parsed.artist, true)) }) {
                    resultTracks.add(parsed)
                }
            }
        }

        val sanitizedTracks = MetadataSanitizer.sanitizeTracks(resultTracks)

        return SearchResultData(
            matchedArtist = matchedArtist,
            tracks = sanitizedTracks.take(limit)
        )
    }

    /**
     * Mengambil top tracks resmi dari musisi bersangkutan melalui GET /v1/artists/{id}/top-tracks?market=ID
     */
    private suspend fun fetchArtistOfficialTopTracks(artistId: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val token = spotifyApiService.getSpotifyToken() ?: return emptyList()
            val url = URL("https://api.spotify.com/v1/artists/$artistId/top-tracks?market=ID")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
            }

            if (conn.responseCode == 200) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)
                val itemsArr = json.optJSONArray("tracks")
                if (itemsArr != null && itemsArr.length() > 0) {
                    for (i in 0 until itemsArr.length()) {
                        val tObj = itemsArr.optJSONObject(i) ?: continue
                        val parsed = parseSpotifyTrack(tObj) ?: continue
                        if (CrankrCatalogFilter.isValidSearchTrack(parsed.title, parsed.artist, isOfficialSpotify = true, parsed.durationSeconds)) {
                            tracks.add(parsed)
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}

        return tracks
    }

    private fun parseSpotifyTrack(t: JSONObject): Track? {
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
            genre = "Search",
            spotifyArtistId = primaryArtistId,
            isrc = isrc
        )
    }

    /**
     * Strictly filters out compilations, bootlegs, and tracks matching CrankrCatalogFilter.
     */
    fun filterStrictly(tracks: List<Track>): List<Track> {
        return tracks.filter { track ->
            CrankrCatalogFilter.isValidSearchTrack(
                title = track.title,
                artist = track.artist,
                isOfficialSpotify = track.id.startsWith("sp_"),
                durationSeconds = track.durationSeconds
            )
        }
    }
}
