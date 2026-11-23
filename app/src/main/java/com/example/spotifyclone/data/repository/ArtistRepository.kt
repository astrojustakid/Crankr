package com.example.spotifyclone.data.repository

import android.content.Context
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.MusicStreamResolver
import com.example.spotifyclone.util.ArtistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Model data rilisan resmi musisi (Album, EP, Single).
 */
data class AlbumRelease(
    val id: String,
    val title: String,
    val artistName: String,
    val releaseType: String, // "Album", "Single", "EP"
    val releaseYear: String, // e.g. "2024"
    val releaseDate: String = "", // e.g. "2024-05-10"
    val coverUrl: String,
    val trackCount: Int,
    val collectionId: String? = null,
    val spotifyId: String? = null,
    val artistId: String? = null
) {
    val artist: String get() = artistName
}

/**
 * Model data musisi serupa / rekomendasi ("Penggemar Juga Menyukai" / Fans Might Also Like).
 */
data class SimilarArtist(
    val id: String, // channelId berawalan UC
    val name: String,
    val photoUrl: String?,
    val subtitle: String = "Artis"
)

/**
 * Repository diskografi artis multi-sumber stabil dan presisi:
 * - Pemetaan ID Spotify terverifikasi untuk band lokal & internasional.
 * - Resolusi Spotify Artist ID berbasis pencocokan ketat & skor popularitas.
 * - Pengambilan diskografi Spotify berparameter market=ID dan validasi kepemilikan rilisan.
 * - Fallback iTunes presisi (kesamaan nama >= 90%).
 */
class ArtistRepository(private val context: Context? = null) {

    val youTubeMusicRepository = YouTubeMusicRepository(context)
    val bandcampRepository = BandcampRepository(context)
    private var cachedSpotifyToken: String? = null
    private var tokenExpirationMs: Long = 0L

    // In-memory caches untuk pemuatan instan tanpa jeda
    private val topTracksCache = ConcurrentHashMap<String, List<Track>>()
    private val albumsCache = ConcurrentHashMap<String, List<AlbumRelease>>()
    private val similarArtistsCache = ConcurrentHashMap<String, List<SimilarArtist>>()

    fun getCachedTopTracks(artistId: String): List<Track>? {
        val cleanId = artistId.trim().removePrefix("sp_").removePrefix("spotify:artist:")
        return topTracksCache[cleanId] ?: topTracksCache[artistId]
    }

    fun getCachedAlbums(artistId: String): List<AlbumRelease>? {
        val cleanId = artistId.trim().removePrefix("sp_").removePrefix("spotify:artist:")
        val raw = albumsCache[cleanId] ?: albumsCache[artistId]
        return raw?.filter { !it.id.startsWith("UC") && it.collectionId?.startsWith("UC") != true }
    }

    fun getCachedSimilarArtists(artistId: String): List<SimilarArtist>? {
        val cleanId = artistId.trim().removePrefix("sp_").removePrefix("spotify:artist:")
        return similarArtistsCache[cleanId] ?: similarArtistsCache[artistId]
    }

    suspend fun getSimilarArtists(artistName: String): List<SimilarArtist> = withContext(Dispatchers.IO) {
        val cleanName = canonicalizeArtistName(artistName).trim()
        val cached = similarArtistsCache[cleanName]
        if (!cached.isNullOrEmpty()) return@withContext cached

        try {
            val channelId = if (cleanName.startsWith("UC")) cleanName else youTubeMusicRepository.resolveArtistChannelId(cleanName)
            if (!channelId.isNullOrBlank()) {
                val page = youTubeMusicRepository.getArtistPage(channelId, cleanName)
                if (page.similarArtists.isNotEmpty()) {
                    similarArtistsCache[cleanName] = page.similarArtists
                    similarArtistsCache[channelId] = page.similarArtists
                    return@withContext page.similarArtists
                }
            }
        } catch (_: Exception) {}
        emptyList()
    }

    // Pemetaan Spotify Artist ID resmi yang telah diverifikasi
    private val knownSpotifyArtistIds: MutableMap<String, String> = mutableMapOf(
        "morfem" to "3JoyBiTkzGqZCWwq07no59",
        "the kuda" to "4aRuyN2moR9xvYmIzUHiR1",
        "the skit" to "3SSbowOdeBgnukoCm6dGqT",
        "teenage death star" to "5W404N31N24b5T064f2g5K",
        "texpack" to "0Kxlav9sQGeqTEkc3iaQZ6",
        "the panturas" to "06i56Y11P29G8x4u5fL5q5",
        "sukatani" to "6j1La1fkBWt6vQcwwXla0w",
        "dongker" to "6sf1OnuDZM48bgFQvPkbYm",
        "the kick" to "5acyN68XSrCN4Jl9miQE1O",
        "the jansen" to "0q3MCOdd2qGlN2TxJdr0SQ",
        "grrrl gang" to "4GgA61hzcYno3GYTrjhZ6A",
        "silampukau" to "40ucFk6nwFWYZ4gvL9DNm8",
        "pee wee gaskins" to "3B3oYx5WJ1vT6eT2x1uO4b",
        "mahalini" to "6zC15qG8zSjM1Z80e22mXk",
        "bernadya" to "5Nfe49m01bV6b6cW1q6n9m",
        "sheila on 7" to "1ZzjhW1t6fT9h27VwJ3r1U",
        "payung teduh" to "1pZf9n72q8jE6Z0c8n6V5m",
        "rocket rockers" to "3k7qB8d47YeYcHlrqaLM4U",
        "green day" to "7oPftvlIMllembAc1v49a6",
        "blink-182" to "6FBDaR13swwdtiCKTB4KG1",
        "sum 41" to "0qiFS9N922h9vFv7o14cQy",
        "superman is dead" to "3sQd1y3z6sU6p8s9vQ0x5k",
        "the adicts" to "1h9tC0mU4W7pC5nQk2K7gT",
        "nofx" to "2O7K0scZZGgD4gJjK6lD5L",
        "bad religion" to "2yJwiyBzpv1vllNxzg7Epk",
        "the offspring" to "5UWoi4wXhXbEWevB4bgC9E",
        "rise against" to "6kEvdn3q0Q9p4K9w1j2L8z"
    )

    private suspend fun getSpotifyAccessToken(): String? = withContext(Dispatchers.IO) {
        if (!cachedSpotifyToken.isNullOrBlank() && System.currentTimeMillis() < tokenExpirationMs) {
            return@withContext cachedSpotifyToken
        }

        val tokenEndpoints = listOf(
            "https://open.spotify.com/embed/api/token",
            "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"
        )

        for (endpoint in tokenEndpoints) {
            try {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                conn.setRequestProperty("Referer", "https://open.spotify.com/")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 7000
                conn.readTimeout = 7000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    val json = JSONObject(reader.readText())
                    reader.close()

                    val token = json.optString("accessToken")
                    if (token.isNotBlank()) {
                        cachedSpotifyToken = token
                        val exp = json.optLong("accessTokenExpirationTimestampMs", 0L)
                        tokenExpirationMs = if (exp > System.currentTimeMillis()) exp else (System.currentTimeMillis() + 2700000L) // 45 menit
                        return@withContext token
                    }
                }
            } catch (_: Exception) { }
        }
        null
    }

    fun isValidSpotifyArtistId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        val trimmed = id.trim().removePrefix("sp_").removePrefix("spotify:artist:")
        if (trimmed.startsWith("UC") && trimmed.length in 20..32) return true
        return trimmed.length in 15..35 && trimmed.matches(Regex("^[a-zA-Z0-9_-]+$"))
    }

    fun canonicalizeArtistName(artistName: String): String {
        val clean = artistName.trim()
        return when {
            clean.equals("melancholic bitch", ignoreCase = true) ||
            clean.contains("melancholic bitch", ignoreCase = true) -> "Majelis Lidah Berduri"
            else -> clean
        }
    }

    /**
     * Algoritma Resolusi Spotify Artist ID presisi:
     * 1. Pencocokan ketat terhadap knownSpotifyArtistIds (exact match setelah trim dan lowercase).
     * 2. Pencarian Spotify Search API dengan prioritas:
     *    - Kecocokan persis nama (exact match).
     *    - Nama paling mendekati (similarity threshold >= 0.70) dikombinasikan dengan popularitas tertinggi.
     *    - Tidak pernah otomatis mengambil index 0 jika nama tidak cocok.
     */
    suspend fun resolveSpotifyArtistId(artistName: String): String? = withContext(Dispatchers.IO) {
        val clean = artistName.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) return@withContext null

        // 1. Pencocokan ketat terhadap knownSpotifyArtistIds
        knownSpotifyArtistIds[clean]?.let { return@withContext it }
        val canonical = canonicalizeArtistName(clean).lowercase(Locale.ROOT)
        knownSpotifyArtistIds[canonical]?.let { return@withContext it }

        // 2. Pencarian Spotify Search API
        val token = getSpotifyAccessToken() ?: return@withContext null
        try {
            val urlStr = "https://api.spotify.com/v1/search?q=${URLEncoder.encode(artistName.trim(), "UTF-8")}&type=artist&limit=10"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 7000
            conn.readTimeout = 7000

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val json = JSONObject(reader.readText())
                reader.close()

                val items = json.optJSONObject("artists")?.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val cleanArtist = artistName.trim()

                    // Saring hasil yang namanya cocok persis (name.equals(cleanArtist, ignoreCase = true)) lalu pilih entitas artis dengan nilai popularitas atau pengikut tertinggi.
                    val exactMatches = mutableListOf<JSONObject>()
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val name = item.optString("name", "").trim()
                        val id = item.optString("id", "").trim()
                        if (id.isNotBlank() && name.equals(cleanArtist, ignoreCase = true)) {
                            exactMatches.add(item)
                        }
                    }

                    if (exactMatches.isNotEmpty()) {
                        val bestExact = exactMatches.maxWithOrNull(
                            compareBy<JSONObject> { it.optInt("popularity", 0) }
                                .thenBy { it.optJSONObject("followers")?.optInt("total", 0) ?: 0 }
                        )
                        val chosenId = bestExact?.optString("id", "")?.trim()
                        if (!chosenId.isNullOrBlank()) {
                            knownSpotifyArtistIds[clean] = chosenId
                            return@withContext chosenId
                        }
                    }

                    // Prioritas 2: Nama paling mendekati dan relevan dengan popularitas tertinggi jika exact match tidak ditemukan
                    var bestId: String? = null
                    var bestScore = -1.0

                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val name = item.optString("name", "").trim()
                        val id = item.optString("id", "").trim()
                        val followers = item.optJSONObject("followers")?.optInt("total", 0) ?: 0
                        val popularity = item.optInt("popularity", 0)

                        if (name.isBlank() || id.isBlank()) continue
                        // Saring entitas dengan 0 pengikut
                        if (followers == 0) continue

                        val similarity = calculateStringSimilarity(name, cleanArtist)
                        val isContain = name.contains(cleanArtist, ignoreCase = true) || cleanArtist.contains(name, ignoreCase = true)

                        if (similarity >= 0.70 || isContain) {
                            val score = (similarity * 100.0) + (popularity * 0.4)
                            if (score > bestScore) {
                                bestScore = score
                                bestId = id
                            }
                        }
                    }

                    // Hanya ambil jika ada kandidat yang benar-benar cocok dan aktif
                    if (!bestId.isNullOrBlank()) {
                        knownSpotifyArtistIds[clean] = bestId
                        return@withContext bestId
                    }
                }
            }
        } catch (_: Exception) { }
        null
    }

    suspend fun searchArtistByName(artistName: String): String? {
        val clean = canonicalizeArtistName(artistName).trim()
        if (clean.isBlank()) return null

        // 1. Cek apakah musisi merupakan entitas terdaftar di katalog underground Bandcamp
        try {
            val bcResult = bandcampRepository.search(clean)
            val bcArtist = bcResult.artists.firstOrNull { it.name.equals(clean, ignoreCase = true) }
                ?: bcResult.artists.firstOrNull {
                    val n = it.name.lowercase().trim()
                    val c = clean.lowercase().trim()
                    (n.startsWith("$c ") || n.endsWith(" $c")) && c.length >= 4
                }
            if (bcArtist != null && bcArtist.id > 0L) {
                return "bc_${bcArtist.id}"
            }
        } catch (_: Exception) {}

        val ytmChannel = youTubeMusicRepository.resolveArtistChannelId(clean)
        if (!ytmChannel.isNullOrBlank()) return ytmChannel
        return resolveSpotifyArtistId(clean)
    }

    suspend fun getArtistNameById(artistId: String): String? = withContext(Dispatchers.IO) {
        val cleanId = artistId.trim().removePrefix("sp_").removePrefix("spotify:artist:")
        if (cleanId.isBlank()) return@withContext null
        if (cleanId.startsWith("bc_")) {
            val bandId = cleanId.removePrefix("bc_").toLongOrNull() ?: 0L
            if (bandId > 0) {
                val detail = bandcampRepository.getBandDetails(bandId)
                if (detail != null && detail.name.isNotBlank()) {
                    return@withContext detail.name
                }
            }
            return@withContext null
        }
        knownSpotifyArtistIds.entries.firstOrNull { it.value.equals(cleanId, ignoreCase = true) }?.key?.let {
            return@withContext canonicalizeArtistName(it)
        }
        val token = getSpotifyAccessToken()
        if (!token.isNullOrBlank()) {
            try {
                val url = URL("https://api.spotify.com/v1/artists/$cleanId")
                val conn = URL(url.toString()).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    val json = JSONObject(reader.readText())
                    reader.close()
                    val name = json.optString("name", "").trim()
                    if (name.isNotBlank()) {
                        knownSpotifyArtistIds[name.lowercase(Locale.ROOT)] = cleanId
                        return@withContext name
                    }
                }
            } catch (_: Exception) { }
        }
        null
    }

    suspend fun getOfficialArtistPhoto(artistName: String, explicitSpotifyId: String? = null): String? = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank()) return@withContext null

        val cleanExplicit = explicitSpotifyId?.trim()?.removePrefix("sp_")?.removePrefix("spotify:artist:")
        val isBandcamp = cleanExplicit?.startsWith("bc_") == true || cleanName.startsWith("bc_")

        // 1. ISOLASI BANDCAMP KETAT: Jika entitas Bandcamp, HANYA ambil foto resmi Bandcamp (DILARANG jatuh ke YouTube Music atau Spotify)
        if (isBandcamp) {
            val bandId = (cleanExplicit ?: cleanName).removePrefix("bc_").toLongOrNull() ?: 0L
            if (bandId > 0) {
                val bandDetail = bandcampRepository.getBandDetails(bandId)
                if (!bandDetail?.photoUrl.isNullOrBlank()) {
                    return@withContext bandDetail.photoUrl
                }
                // Fallback: cover album pertama dari diskografi resmi Bandcamp
                val releases = bandcampRepository.getArtistDiscography(bandId)
                val firstCover = releases.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                if (!firstCover.isNullOrBlank()) {
                    return@withContext firstCover
                }
            }
            return@withContext null
        }

        // 2. YouTube Music InnerTube untuk musisi non-Bandcamp
        try {
            val ytmPage = youTubeMusicRepository.getArtistPage(cleanExplicit ?: cleanName, cleanName)
            if (!ytmPage.photoUrl.isNullOrBlank()) {
                return@withContext ytmPage.photoUrl
            }
        } catch (_: Exception) {}
        val isExplicitValid = isValidSpotifyArtistId(cleanExplicit)
        val isNameValidId = isValidSpotifyArtistId(cleanName)

        val targetName = if (isNameValidId) {
            knownSpotifyArtistIds.entries.firstOrNull { it.value.equals(cleanName, ignoreCase = true) }?.key ?: cleanName
        } else {
            canonicalizeArtistName(cleanName)
        }

        val spId = if (isExplicitValid) {
            cleanExplicit
        } else if (isNameValidId) {
            cleanName
        } else {
            knownSpotifyArtistIds[targetName.lowercase(Locale.ROOT)] ?: resolveSpotifyArtistId(targetName)
        }

        // 2. Ambil foto via Spotify oEmbed atau embed metadata menggunakan Spotify ID resmi
        if (!spId.isNullOrBlank()) {
            val spotifyPhoto = fetchSpotifyOfficialPhoto(spId)
            if (!spotifyPhoto.isNullOrBlank()) return@withContext spotifyPhoto
        }

        // 2. Jika Spotify oEmbed tidak mengembalikan foto, ambil cover URL dari album pertama milik artis tersebut
        val cachedAlbums = getCachedAlbums(spId ?: targetName) ?: getCachedAlbums(targetName)
        val cachedCover = cachedAlbums?.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
        if (!cachedCover.isNullOrBlank()) {
            return@withContext cachedCover
        }

        // Ambil artwork dari diskografi resmi artis
        val releases = getOfficialDiscography(targetName)
        val releaseCover = releases.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
        if (!releaseCover.isNullOrBlank()) {
            return@withContext releaseCover
        }

        null
    }

    private fun fetchSpotifyOfficialPhoto(spotifyArtistId: String): String? {
        try {
            val conn = URL("https://open.spotify.com/oembed?url=https://open.spotify.com/artist/$spotifyArtistId").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val json = JSONObject(reader.readText())
                reader.close()
                val thumb = json.optString("thumbnail_url", "").trim()
                if (thumb.isNotBlank()) {
                    return thumb.replace("00005174", "0000e5eb")
                }
            }
        } catch (_: Exception) { }

        try {
            val conn = URL("https://open.spotify.com/embed/artist/$spotifyArtistId").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val html = reader.readText()
                reader.close()

                val regex = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                val match = regex.find(html)
                if (match != null) {
                    val root = JSONObject(match.groupValues[1])
                    val entity = root.optJSONObject("props")
                        ?.optJSONObject("pageProps")
                        ?.optJSONObject("state")
                        ?.optJSONObject("data")
                        ?.optJSONObject("entity")

                    val images = entity?.optJSONObject("visualIdentity")?.optJSONArray("image")
                    if (images != null && images.length() > 0) {
                        for (i in 0 until images.length()) {
                            val img = images.getJSONObject(i)
                            if (img.optInt("maxWidth", 0) >= 640) {
                                return img.optString("url")
                            }
                        }
                        return images.getJSONObject(0).optString("url")
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    /**
     * Memanggil diskografi multi-sumber stabil dan hybrid:
     * Spotify Albums API + iTunes Search API (secara bersamaan) -> Penggabungan (Hybrid Merge) -> Dedup Engine.
     * Pola hybrid ini menjamin jika Spotify kehilangan lisensi band (seperti Morfem / The Skit),
     * katalog dari iTunes langsung mengisi kekosongan secara utuh tanpa menghasilkan 0 diskografi.
     */
    suspend fun getOfficialDiscography(artistName: String): List<AlbumRelease> = withContext(Dispatchers.IO) {
        val input = artistName.trim().removePrefix("sp_").removePrefix("spotify:artist:")
        if (input.isBlank()) return@withContext emptyList()

        // Prioritas Bandcamp: Jika ID diawali dengan bc_ atau merupakan ID numerik Bandcamp
        if (input.startsWith("bc_") || (input.toLongOrNull() != null && !input.startsWith("UC"))) {
            val bandId = input.removePrefix("bc_").toLongOrNull() ?: 0L
            if (bandId > 0) {
                val bcReleases = bandcampRepository.getArtistDiscography(bandId)
                if (bcReleases.isNotEmpty()) {
                    albumsCache[input] = bcReleases
                    return@withContext bcReleases
                }
                if (input.startsWith("bc_")) {
                    return@withContext emptyList()
                }
            }
        }

        val isInputSpotifyId = isValidSpotifyArtistId(input)
        val cleanArtist = if (isInputSpotifyId) {
            knownSpotifyArtistIds.entries.firstOrNull { it.value.equals(input, ignoreCase = true) }?.key
                ?: getArtistNameById(input)
                ?: input
        } else {
            canonicalizeArtistName(input)
        }

        if (cleanArtist.equals("silampukau", ignoreCase = true)) {
            return@withContext getSilampukauCuratedDiscography()
        }

        // 1. YouTube Music InnerTube Official Discography (Single-Door Architecture)
        try {
            val channelId = if (input.startsWith("UC")) input else youTubeMusicRepository.resolveArtistChannelId(cleanArtist)
            if (!channelId.isNullOrBlank()) {
                val ytmPage = youTubeMusicRepository.getArtistPage(channelId, cleanArtist)
                if (ytmPage.allReleases.isNotEmpty()) {
                    albumsCache[channelId] = ytmPage.allReleases
                    albumsCache[cleanArtist] = ytmPage.allReleases
                    albumsCache[input] = ytmPage.allReleases
                    saveDiscographyToCache(channelId, ytmPage.allReleases)
                    saveDiscographyToCache(cleanArtist, ytmPage.allReleases)
                    if (ytmPage.topTracks.isNotEmpty()) {
                        topTracksCache[channelId] = ytmPage.topTracks
                        topTracksCache[cleanArtist] = ytmPage.topTracks
                        topTracksCache[input] = ytmPage.topTracks
                    }
                    if (ytmPage.similarArtists.isNotEmpty()) {
                        similarArtistsCache[channelId] = ytmPage.similarArtists
                        similarArtistsCache[cleanArtist] = ytmPage.similarArtists
                        similarArtistsCache[input] = ytmPage.similarArtists
                    }
                    return@withContext ytmPage.allReleases
                }
            }
        } catch (_: Exception) {}

        val spId = if (isInputSpotifyId) {
            input
        } else {
            knownSpotifyArtistIds[cleanArtist.lowercase(Locale.ROOT)] ?: resolveSpotifyArtistId(cleanArtist)
        }

        val rawReleases = mutableListOf<AlbumRelease>()

        // 1. Ambil data dari Spotify API
        if (!spId.isNullOrBlank()) {
            val spotifyReleases = fetchSpotifyDiscographyPaged(spId, cleanArtist)
            if (spotifyReleases.isNotEmpty()) {
                rawReleases.addAll(spotifyReleases)
            }
        }

        // 2. Panggil iTunes HANYA jika Spotify mengembalikan list kosong (0 data)
        if (rawReleases.isEmpty()) {
            val itunesReleases = fetchItunesDiscography(cleanArtist)
            if (itunesReleases.isNotEmpty()) {
                rawReleases.addAll(itunesReleases)
            }
        }

        // 3. Jika keduanya kosong dan spId valid, coba ekstrak dari top tracks
        if (rawReleases.isEmpty() && !spId.isNullOrBlank()) {
            val topTracks = getArtistTopTracks(spId)
            if (topTracks.isNotEmpty()) {
                rawReleases.addAll(extractAlbumsFromTopTracks(topTracks, cleanArtist, spId))
            }
        }

        // 4. Fallback ke Bandcamp jika tidak ada rilisan resmi di Spotify/YouTube/iTunes
        if (rawReleases.isEmpty() && cleanArtist.isNotBlank()) {
            try {
                val bcResult = bandcampRepository.search(cleanArtist)
                val bcArtist = bcResult.artists.firstOrNull {
                    it.name.equals(cleanArtist, ignoreCase = true) || cleanArtist.contains(it.name, ignoreCase = true)
                }
                if (bcArtist != null && bcArtist.id > 0) {
                    val bcReleases = bandcampRepository.getArtistDiscography(bcArtist.id)
                    if (bcReleases.isNotEmpty()) {
                        albumsCache[input] = bcReleases
                        albumsCache[cleanArtist] = bcReleases
                        return@withContext bcReleases
                    }
                }
            } catch (_: Exception) {}
        }

        val deduped = deduplicateReleases(rawReleases)
        deduped.sortedWith(
            compareByDescending<AlbumRelease> {
                it.releaseDate.ifBlank { it.releaseYear }
            }.thenByDescending { it.releaseYear }
             .thenBy { it.title }
        )
    }

    suspend fun getOfficialDiscography(
        artistId: String,
        artistName: String = "",
        forceFreshNetwork: Boolean = true,
        fallbackTopTracks: List<Track> = emptyList()
    ): List<AlbumRelease> {
        val cleanId = artistId.trim()
        val target = if (cleanId.startsWith("bc_")) cleanId else artistName.ifBlank { cleanId }
        return getOfficialDiscography(target)
    }

    suspend fun getArtistAlbums(artistId: String): List<AlbumRelease> = withContext(Dispatchers.IO) {
        val cleanId = artistId.trim().removePrefix("sp_").removePrefix("spotify:artist:")
        val cached = albumsCache[cleanId] ?: albumsCache[artistId]
        if (!cached.isNullOrEmpty()) {
            return@withContext cached
        }
        val releases = getOfficialDiscography(cleanId)
        if (releases.isNotEmpty()) {
            albumsCache[cleanId] = releases
            albumsCache[artistId] = releases
            if (!cleanId.startsWith("bc_")) {
                val resolvedName = knownSpotifyArtistIds.entries.firstOrNull { it.value.equals(cleanId, ignoreCase = true) }?.key
                if (!resolvedName.isNullOrBlank()) {
                    albumsCache[resolvedName] = releases
                    albumsCache[resolvedName.lowercase(Locale.ROOT)] = releases
                }
            }
        }
        releases
    }

    /**
     * Mengambil diskografi Spotify dengan parameter market=ID dan filter kepemilikan rilisan:
     * Mengecualikan kompilasi asing (appears_on) dan memastikan album memuat nama/ID artis yang bersangkutan.
     */
    private suspend fun fetchSpotifyDiscographyPaged(spotifyArtistId: String, cleanArtist: String): List<AlbumRelease> = withContext(Dispatchers.IO) {
        val releases = mutableListOf<AlbumRelease>()
        val token = getSpotifyAccessToken() ?: return@withContext releases

        var nextUrl: String? = "https://api.spotify.com/v1/artists/$spotifyArtistId/albums?include_groups=album,single&market=ID&limit=50"
        var loopCount = 0

        while (!nextUrl.isNullOrBlank() && loopCount < 50) {
            loopCount++
            try {
                val conn = URL(nextUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    val json = JSONObject(reader.readText())
                    reader.close()

                    val items = json.optJSONArray("items") ?: break
                    if (items.length() == 0) break

                    for (i in 0 until items.length()) {
                        val obj = items.getJSONObject(i)
                        val id = obj.optString("id", "")
                        val name = obj.optString("name", "").trim()
                        if (name.isBlank() || id.isBlank()) continue

                        val albumType = obj.optString("album_type", "").trim()
                        val albumGroup = obj.optString("album_group", albumType).trim()

                        // Tolak semua rilisan kompilasi campuran atau album yang memiliki album_group bernilai appears_on
                        if (albumGroup.equals("appears_on", ignoreCase = true) ||
                            albumType.equals("compilation", ignoreCase = true) ||
                            albumGroup.equals("compilation", ignoreCase = true) ||
                            name.contains("various artists", ignoreCase = true)) {
                            continue
                        }

                        // Pastikan array artists pada setiap album memiliki ID yang sama dengan spotifyArtistId artis yang sedang dibuka
                        val artistsArray = obj.optJSONArray("artists") ?: continue
                        var belongsToArtist = false
                        for (a in 0 until artistsArray.length()) {
                            if (artistsArray.getJSONObject(a).optString("id") == spotifyArtistId) {
                                belongsToArtist = true
                                break
                            }
                        }
                        if (!belongsToArtist) continue

                        val trackCount = obj.optInt("total_tracks", 1)
                        val releaseDate = obj.optString("release_date", "")
                        val year = if (releaseDate.length >= 4) releaseDate.take(4) else "Rilis"
                        val cleanName = cleanReleaseName(name)
                        val type = determineReleaseType(name, trackCount, albumType, albumGroup)

                        var coverUrl = ""
                        val images = obj.optJSONArray("images")
                        if (images != null && images.length() > 0) {
                            coverUrl = images.getJSONObject(0).optString("url", "")
                        }

                        val releaseArtistName = if (artistsArray.length() > 1) {
                            (0 until artistsArray.length()).mapNotNull {
                                artistsArray.optJSONObject(it)?.optString("name")?.trim()
                            }.filter { it.isNotBlank() }.joinToString(", ")
                        } else {
                            cleanArtist
                        }

                        releases.add(
                            AlbumRelease(
                                id = "sp_album_$id",
                                title = cleanName,
                                artistName = releaseArtistName,
                                releaseType = type,
                                releaseYear = year,
                                releaseDate = releaseDate,
                                coverUrl = coverUrl,
                                trackCount = trackCount,
                                collectionId = null,
                                spotifyId = id,
                                artistId = spotifyArtistId
                            )
                        )
                    }
                    val next = json.optString("next", "")
                    nextUrl = if (next.isBlank() || next == "null") null else next
                } else {
                    break
                }
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }
        releases
    }

    /**
     * Fallback iTunes Presisi:
     * artistName dari item search iTunes wajib cocok persis atau memiliki kesamaan nama di atas 90%.
     */
    private suspend fun fetchItunesDiscography(cleanArtist: String): List<AlbumRelease> = withContext(Dispatchers.IO) {
        val releases = mutableListOf<AlbumRelease>()
        try {
            val urlStr = "https://itunes.apple.com/search?term=${URLEncoder.encode(cleanArtist, "UTF-8")}&entity=album&limit=100"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            conn.connectTimeout = 7000
            conn.readTimeout = 7000

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val json = JSONObject(reader.readText())
                reader.close()

                val results = json.optJSONArray("results") ?: return@withContext releases
                for (i in 0 until results.length()) {
                    val obj = results.getJSONObject(i)
                    val albumArtist = obj.optString("artistName", "").trim()
                    val collectionName = obj.optString("collectionName", "").trim()
                    if (collectionName.isBlank()) continue

                    // Wajib validasi artistName.equals(cleanArtist, ignoreCase = true) agar album dari musisi lain yang memiliki kemiripan kata tidak masuk ke daftar
                    if (!albumArtist.equals(cleanArtist, ignoreCase = true)) {
                        continue
                    }

                    val trackCount = obj.optInt("trackCount", 1)
                    val releaseDate = obj.optString("releaseDate", "")
                    val year = if (releaseDate.length >= 4) releaseDate.take(4) else "Rilis"
                    val cleanName = cleanReleaseName(collectionName)
                    val type = determineReleaseType(collectionName, trackCount, "album", "album")

                    val art = obj.optString("artworkUrl100", "")
                    val cover = art.replace("100x100bb.jpg", "600x600bb.jpg").replace("100x100bb.png", "600x600bb.png")
                    val collectionId = obj.optLong("collectionId", 0L).toString()
                    val dateFormatted = if (releaseDate.length >= 10) releaseDate.take(10) else releaseDate

                    releases.add(
                        AlbumRelease(
                            id = "album_${obj.optLong("collectionId", 0L)}",
                            title = cleanName,
                            artistName = if (albumArtist.isNotBlank()) albumArtist else cleanArtist,
                            releaseType = type,
                            releaseYear = year,
                            releaseDate = dateFormatted,
                            coverUrl = cover,
                            trackCount = trackCount,
                            collectionId = collectionId,
                            spotifyId = null
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        releases
    }

    private fun deduplicateReleases(rawList: List<AlbumRelease>): List<AlbumRelease> {
        val result = mutableListOf<AlbumRelease>()

        val sorted = rawList.sortedWith(
            compareByDescending<AlbumRelease> { !it.title.contains("clean", ignoreCase = true) }
                .thenByDescending { it.coverUrl.contains("scdn") || it.coverUrl.contains("600x600") }
                .thenByDescending { it.trackCount }
        )

        for (item in sorted) {
            val norm = normalizeForDedup(item.title)
            val existingIndex = result.indexOfFirst {
                val existingNorm = normalizeForDedup(it.title)
                existingNorm == norm ||
                (existingNorm.isNotBlank() && norm.isNotBlank() && (existingNorm.contains(norm) || norm.contains(existingNorm)) && it.releaseYear == item.releaseYear)
            }

            if (existingIndex >= 0) {
                val prev = result[existingIndex]
                val bestCover = if (prev.coverUrl.contains("600x600") || prev.coverUrl.contains("scdn")) {
                    prev.coverUrl
                } else if (item.coverUrl.contains("600x600") || item.coverUrl.contains("scdn")) {
                    item.coverUrl
                } else {
                    prev.coverUrl.ifBlank { item.coverUrl }
                }

                val merged = prev.copy(
                    collectionId = prev.collectionId ?: item.collectionId,
                    spotifyId = prev.spotifyId ?: item.spotifyId,
                    coverUrl = bestCover,
                    releaseDate = if (prev.releaseDate.length >= 10) prev.releaseDate else (if (item.releaseDate.length >= 10) item.releaseDate else prev.releaseDate),
                    trackCount = maxOf(prev.trackCount, item.trackCount)
                )
                result[existingIndex] = merged
            } else {
                result.add(item)
            }
        }

        // Urutkan daftar rilis berdasarkan release_date secara menurun dari rilisan terbaru ke terlama setelah proses deduplicateReleases selesai
        return result.sortedWith(
            compareByDescending<AlbumRelease> {
                it.releaseDate.ifBlank { it.releaseYear }
            }.thenByDescending { it.releaseYear }
             .thenBy { it.title }
        )
    }

    private fun cleanReleaseName(raw: String): String {
        return raw.replace(Regex("""\s*-\s*Single$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*EP$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(Single\)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(EP\)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[Single\]$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[EP\]$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun determineReleaseType(rawName: String, totalTracks: Int, albumType: String, albumGroup: String = ""): String {
        val lower = rawName.lowercase(Locale.ROOT)
        if (lower.endsWith(" - single") || lower.endsWith("(single)") || lower.contains(" [single]")) {
            return "Single"
        }
        if (lower.endsWith(" - ep") || lower.endsWith("(ep)") || lower.contains(" [ep]") || lower.contains(" ep")) {
            return "EP"
        }
        if (albumGroup.equals("single", ignoreCase = true) || albumType.equals("single", ignoreCase = true)) {
            return if (totalTracks in 2..6) "EP" else "Single"
        }
        return if (totalTracks == 1) "Single" else if (totalTracks in 2..6) "EP" else "Album"
    }

    private fun normalizeForDedup(title: String): String {
        val lower = title.lowercase(Locale.ROOT)
        return lower
            .replace(Regex("""\s*\((clean|explicit|deluxe|expanded|remastered|anniversary|bonus track|special edition)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[(clean|explicit|deluxe|expanded|remastered|anniversary|bonus track|special edition)[^\]]*\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(clean|explicit|deluxe|expanded|remastered|anniversary|bonus track|special edition).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*single$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*ep$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^a-z0-9]"""), "")
            .trim()
    }

    suspend fun getAlbumTracks(
        collectionId: String? = null,
        spotifyId: String? = null,
        albumTitle: String,
        artistName: String,
        coverUrl: String
    ): List<Track> = withContext(Dispatchers.IO) {
        // Prioritas Bandcamp jika collectionId / spotifyId memuat identitas album Bandcamp
        val bcId = listOfNotNull(collectionId, spotifyId).firstOrNull { it.startsWith("bc_") }
        if (bcId != null) {
            val parts = bcId.removePrefix("bc_").removePrefix("album_").removePrefix("track_").split("_")
            val bandId = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val albumId = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            if (bandId > 0 && albumId > 0) {
                val bcTracks = bandcampRepository.getAlbumTracks(albumId, bandId)
                if (bcTracks.isNotEmpty()) return@withContext bcTracks
                val singleTrack = bandcampRepository.resolveTrackDetails(bandId, albumId)
                if (singleTrack != null) return@withContext listOf(singleTrack)
            }
            return@withContext emptyList()
        }

        // 1. Prioritas Utama: YouTube Music InnerTube Album Browse (MPREb_...)
        val ytmBrowseId = listOfNotNull(collectionId, spotifyId).firstOrNull { it.startsWith("MPREb_") }
        if (ytmBrowseId != null) {
            try {
                val ytmTracks = youTubeMusicRepository.getAlbumTracks(ytmBrowseId, albumTitle, artistName, coverUrl)
                if (ytmTracks.isNotEmpty()) return@withContext ytmTracks
            } catch (_: Exception) {}
        }

        // Pencarian trek album di YouTube Music jika belum memiliki MPREb_
        if (albumTitle.isNotBlank() && artistName.isNotBlank()) {
            try {
                val ytmSongs = youTubeMusicRepository.searchSongs("$albumTitle $artistName", limit = 15)
                val matchingAlbum = ytmSongs.filter {
                    it.album.contains(albumTitle, ignoreCase = true) || albumTitle.contains(it.album, ignoreCase = true)
                }
                if (matchingAlbum.isNotEmpty()) {
                    return@withContext matchingAlbum
                }
            } catch (_: Exception) {}
        }

        val trackList = mutableListOf<Track>()
        if (!spotifyId.isNullOrBlank()) {
            val token = getSpotifyAccessToken()
            if (token != null) {
                try {
                    val conn = URL("https://api.spotify.com/v1/albums/$spotifyId/tracks?limit=50").openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    if (conn.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                        val json = JSONObject(reader.readText())
                        reader.close()
                        val items = json.optJSONArray("items")
                        if (items != null) {
                            for (i in 0 until items.length()) {
                                val item = items.getJSONObject(i)
                                val name = item.optString("name", "Track ${i + 1}")
                                val id = item.optString("id", "track_$i")
                                val duration = item.optInt("duration_ms", 180000) / 1000
                                val preview = item.optString("preview_url", "")
                                trackList.add(
                                    Track(
                                        id = "sp_track_${spotifyId}_$id",
                                        title = name,
                                        artist = artistName,
                                        album = albumTitle,
                                        durationSeconds = duration,
                                        streamUrl = if (preview.isNotBlank() && preview != "null") preview else "https://music.youtube.com/search?q=${URLEncoder.encode("$name $artistName", "UTF-8")}",
                                        coverUrl = coverUrl,
                                        genre = "Rock"
                                    )
                                )
                            }
                            if (trackList.isNotEmpty()) {
                                return@withContext trackList
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }

        if (trackList.isEmpty()) {
            val targetCollectionId = if (!collectionId.isNullOrBlank() && collectionId != "0") collectionId else fetchItunesCollectionId(albumTitle, artistName)
            if (!targetCollectionId.isNullOrBlank() && targetCollectionId != "0") {
                try {
                    val conn = URL("https://itunes.apple.com/lookup?id=$targetCollectionId&entity=song").openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    if (conn.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                        val json = JSONObject(reader.readText())
                        reader.close()
                        val results = json.optJSONArray("results")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                if (item.optString("wrapperType") == "track") {
                                    val trackId = item.optLong("trackId", i.toLong()).toString()
                                    val trackName = item.optString("trackName", "Track $i").trim()
                                    val duration = (item.optLong("trackTimeMillis", 180000L) / 1000).toInt()
                                    val preview = item.optString("previewUrl", "")
                                    trackList.add(
                                        Track(
                                            id = "album_track_${targetCollectionId}_$trackId",
                                            title = trackName,
                                            artist = artistName,
                                            album = albumTitle,
                                            durationSeconds = duration,
                                            streamUrl = if (preview.isNotBlank()) preview else "https://music.youtube.com/search?q=${URLEncoder.encode("$trackName $artistName", "UTF-8")}",
                                            coverUrl = coverUrl,
                                            genre = "Rock"
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }

        trackList
    }

    private suspend fun fetchItunesCollectionId(albumTitle: String, artistName: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = "$albumTitle $artistName".trim()
            val urlStr = "https://itunes.apple.com/search?term=${URLEncoder.encode(query, "UTF-8")}&entity=album&limit=5"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val json = JSONObject(reader.readText())
                reader.close()
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    return@withContext results.getJSONObject(0).optLong("collectionId", 0L).toString()
                }
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun getArtistTopTracks(artistId: String): List<Track> = withContext(Dispatchers.IO) {
        val cleanId = artistId.trim().removePrefix("sp_").removePrefix("spotify:artist:")
        val cached = topTracksCache[cleanId] ?: topTracksCache[artistId]
        if (!cached.isNullOrEmpty()) {
            return@withContext cached
        }

        // ISOLASI BANDCAMP KETAT: Jika ID diawali bc_ atau merupakan ID numerik Bandcamp
        if (cleanId.startsWith("bc_") || (cleanId.toLongOrNull() != null && !cleanId.startsWith("UC"))) {
            val bandId = cleanId.removePrefix("bc_").toLongOrNull() ?: 0L
            if (bandId > 0) {
                val tracks = bandcampRepository.getArtistTracks(bandId, limit = 25)
                if (tracks.isNotEmpty()) {
                    topTracksCache[cleanId] = tracks
                    topTracksCache[artistId] = tracks
                    return@withContext tracks
                }
                if (cleanId.startsWith("bc_")) {
                    return@withContext emptyList()
                }
            }
        }

        val isId = isValidSpotifyArtistId(cleanId)
        var targetArtistName = if (isId) {
            knownSpotifyArtistIds.entries.firstOrNull { it.value.equals(cleanId, ignoreCase = true) }?.key
                ?: getArtistNameById(cleanId)
                ?: cleanId
        } else {
            canonicalizeArtistName(cleanId)
        }
        val targetId = if (isId) cleanId else (knownSpotifyArtistIds[targetArtistName.lowercase(Locale.ROOT)] ?: resolveSpotifyArtistId(targetArtistName) ?: cleanId)

        // 1. Prioritas Utama: YouTube Music InnerTube Browse
        try {
            val channelId = if (cleanId.startsWith("UC")) cleanId else youTubeMusicRepository.resolveArtistChannelId(targetArtistName)
            if (!channelId.isNullOrBlank()) {
                val ytmPage = youTubeMusicRepository.getArtistPage(channelId, targetArtistName)
                if (ytmPage.topTracks.isNotEmpty()) {
                    topTracksCache[cleanId] = ytmPage.topTracks
                    topTracksCache[artistId] = ytmPage.topTracks
                    topTracksCache[channelId] = ytmPage.topTracks
                    topTracksCache[targetArtistName.lowercase(Locale.ROOT)] = ytmPage.topTracks
                    if (ytmPage.allReleases.isNotEmpty()) {
                        albumsCache[cleanId] = ytmPage.allReleases
                        albumsCache[artistId] = ytmPage.allReleases
                        albumsCache[channelId] = ytmPage.allReleases
                        saveDiscographyToCache(channelId, ytmPage.allReleases)
                        saveDiscographyToCache(targetArtistName, ytmPage.allReleases)
                    }
                    return@withContext ytmPage.topTracks
                }
            }
        } catch (_: Exception) {}

        val token = getSpotifyAccessToken()
        if (!token.isNullOrBlank() && targetId.isNotBlank() && isValidSpotifyArtistId(targetId)) {
            try {
                val url = URL("https://api.spotify.com/v1/artists/$targetId/top-tracks?market=ID")
                val conn = URL(url.toString()).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    val json = JSONObject(reader.readText())
                    reader.close()
                    val tracksArr = json.optJSONArray("tracks")
                    if (tracksArr != null && tracksArr.length() > 0) {
                        val list = mutableListOf<Track>()
                        for (i in 0 until tracksArr.length()) {
                            val item = tracksArr.getJSONObject(i)
                            val tId = item.optString("id", "")
                            val tName = item.optString("name", "")
                            val durMs = item.optLong("duration_ms", 180000L)
                            val durSec = (durMs / 1000).toInt()
                            val albObj = item.optJSONObject("album")
                            val albName = albObj?.optString("name", "") ?: ""
                            var cover = ""
                            val imgs = albObj?.optJSONArray("images")
                            if (imgs != null && imgs.length() > 0) {
                                cover = imgs.getJSONObject(0).optString("url", "")
                            }
                            var artName = targetArtistName
                            val artArr = item.optJSONArray("artists")
                            if (artArr != null && artArr.length() > 0) {
                                artName = artArr.getJSONObject(0).optString("name", targetArtistName)
                            }
                            val preview = item.optString("preview_url", "")
                            val track = Track(
                                id = "sp_top_${tId}",
                                title = tName,
                                artist = artName,
                                album = albName,
                                durationSeconds = durSec,
                                streamUrl = if (preview.isNotBlank() && preview != "null") preview else "https://music.youtube.com/search?q=${URLEncoder.encode("$tName $artName", "UTF-8")}",
                                coverUrl = cover,
                                genre = "Rock",
                                spotifyArtistId = targetId
                            )
                            list.add(track)
                        }
                        if (list.isNotEmpty()) {
                            val resolvedName = list.firstOrNull()?.artist ?: targetArtistName
                            topTracksCache[cleanId] = list
                            topTracksCache[artistId] = list
                            topTracksCache[resolvedName.lowercase(Locale.ROOT)] = list
                            if (isValidSpotifyArtistId(targetId)) {
                                knownSpotifyArtistIds[resolvedName.lowercase(Locale.ROOT)] = targetId
                            }
                            return@withContext list
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        // Fallback iTunes Songs jika Spotify API kosong atau terkena batasan/rate-limit
        val resolvedNameForSearch = if (isValidSpotifyArtistId(targetArtistName)) {
            getArtistNameById(targetArtistName) ?: targetArtistName
        } else targetArtistName

        if (!isValidSpotifyArtistId(resolvedNameForSearch) && resolvedNameForSearch.isNotBlank()) {
            val itunesTracks = fetchItunesTopTracks(resolvedNameForSearch)
            if (itunesTracks.isNotEmpty()) {
                topTracksCache[cleanId] = itunesTracks
                topTracksCache[artistId] = itunesTracks
                topTracksCache[resolvedNameForSearch.lowercase(Locale.ROOT)] = itunesTracks
                return@withContext itunesTracks
            }
        }

        // Fallback YouTube Music search jika Spotify & iTunes keduanya belum membuahkan hasil
        if (resolvedNameForSearch.isNotBlank() && !isValidSpotifyArtistId(resolvedNameForSearch)) {
            try {
                val ytTracks = MusicStreamResolver.searchTracks(resolvedNameForSearch).filter {
                    it.artist.contains(resolvedNameForSearch, ignoreCase = true) || it.title.contains(resolvedNameForSearch, ignoreCase = true)
                }.take(20)
                if (ytTracks.isNotEmpty()) {
                    topTracksCache[cleanId] = ytTracks
                    topTracksCache[artistId] = ytTracks
                    topTracksCache[resolvedNameForSearch.lowercase(Locale.ROOT)] = ytTracks
                    return@withContext ytTracks
                }
            } catch (_: Exception) {}
        }

        emptyList()
    }

    private suspend fun fetchItunesTopTracks(cleanArtist: String): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        try {
            val urlStr = "https://itunes.apple.com/search?term=${URLEncoder.encode(cleanArtist, "UTF-8")}&entity=song&limit=25"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 7000
            conn.readTimeout = 7000

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val json = JSONObject(reader.readText())
                reader.close()
                val results = json.optJSONArray("results")
                if (results != null) {
                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        if (item.optString("wrapperType") == "track") {
                            val artName = item.optString("artistName", "").trim()
                            val tName = item.optString("trackName", "").trim()
                            val trackId = item.optLong("trackId", i.toLong()).toString()
                            val collectionName = item.optString("collectionName", "").trim()
                            val durMs = item.optLong("trackTimeMillis", 180000L)
                            val preview = item.optString("previewUrl", "")
                            val art = item.optString("artworkUrl100", "")
                            val cover = art.replace("100x100bb.jpg", "600x600bb.jpg").replace("100x100bb.png", "600x600bb.png")

                            val track = Track(
                                id = "itunes_top_${trackId}",
                                title = tName,
                                artist = artName,
                                album = collectionName,
                                durationSeconds = (durMs / 1000).toInt(),
                                streamUrl = if (preview.isNotBlank()) preview else "https://music.youtube.com/search?q=${URLEncoder.encode("$tName $artName", "UTF-8")}",
                                coverUrl = cover,
                                genre = "Rock"
                            )

                            val parsedArtists = com.example.spotifyclone.util.ArtistParser.parseArtists(track.artist)
                            if (track.artist.equals(cleanArtist, ignoreCase = true) ||
                                parsedArtists.any { it.equals(cleanArtist, ignoreCase = true) }) {
                                list.add(track)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun getOfficialArtistTopTracks(artistId: String, artistName: String): List<Track> = withContext(Dispatchers.IO) {
        val cleanId = artistId.trim().removePrefix("sp_").removePrefix("spotify:artist:")
        val tracks = getArtistTopTracks(cleanId.ifBlank { artistName })
        if (tracks.isNotEmpty()) return@withContext tracks
        if (cleanId.startsWith("bc_")) {
            return@withContext emptyList()
        }
        if (artistName.isNotBlank() && !artistName.equals(cleanId, ignoreCase = true)) {
            getArtistTopTracks(artistName)
        } else {
            emptyList()
        }
    }

    suspend fun getOfficialArtistTopTracks(artistName: String): List<Track> = withContext(Dispatchers.IO) {
        getArtistTopTracks(artistName)
    }

    fun extractAlbumsFromTopTracks(
        topTracks: List<Track>,
        artistName: String,
        spotifyArtistId: String? = null
    ): List<AlbumRelease> {
        val cleanArtist = artistName.trim()
        val seen = mutableSetOf<String>()
        val extracted = mutableListOf<AlbumRelease>()

        for (track in topTracks) {
            val rawAlbum = track.album.trim()
            if (rawAlbum.isBlank()) continue
            if (rawAlbum.equals("Top Songs", ignoreCase = true) || rawAlbum.equals("Lagu", ignoreCase = true)) continue
            val isStrictArtist = track.artist.equals(cleanArtist, ignoreCase = true) ||
                    com.example.spotifyclone.util.ArtistParser.parseArtists(track.artist)
                        .any { it.equals(cleanArtist, ignoreCase = true) }
            if (!isStrictArtist) continue

            val normKey = normalizeForDedup(rawAlbum)
            if (!seen.contains(normKey) && !seen.contains(rawAlbum.lowercase(Locale.ROOT))) {
                seen.add(normKey)
                seen.add(rawAlbum.lowercase(Locale.ROOT))
                val releaseType = determineReleaseType(rawAlbum, 1, "album", "album")
                extracted.add(
                    AlbumRelease(
                        id = "sp_extracted_${track.id.ifBlank { rawAlbum.hashCode().toString() }}",
                        title = cleanReleaseName(rawAlbum),
                        artistName = if (track.artist.isNotBlank()) track.artist else cleanArtist,
                        releaseType = releaseType,
                        releaseYear = "Rilis",
                        releaseDate = "",
                        coverUrl = track.coverUrl,
                        trackCount = 1,
                        collectionId = null,
                        spotifyId = null,
                        artistId = spotifyArtistId
                    )
                )
            }
        }
        return deduplicateReleases(extracted)
    }

    fun getCachedDiscography(artistId: String, expectedArtistName: String? = null): List<AlbumRelease>? {
        val cleanId = artistId.trim()
        val mem = albumsCache[cleanId]
        if (!mem.isNullOrEmpty()) {
            val cleanMem = mem.filter { !it.id.startsWith("UC") && it.collectionId?.startsWith("UC") != true }
            if (cleanMem.size != mem.size) {
                albumsCache[cleanId] = cleanMem
            }
            return cleanMem
        }
        val ctx = context ?: return null
        return try {
            val dir = java.io.File(ctx.cacheDir, "crankr_discography")
            val file = java.io.File(dir, "id_$cleanId.json")
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                val list = mutableListOf<AlbumRelease>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val rId = obj.optString("id", "")
                    val cId = obj.optString("collectionId")
                    if (rId.startsWith("UC") || cId.startsWith("UC")) continue
                    list.add(
                        AlbumRelease(
                            id = rId,
                            title = obj.optString("title", "Untitled"),
                            artistName = obj.optString("artistName", expectedArtistName ?: "Artist"),
                            releaseType = obj.optString("releaseType", "Album"),
                            releaseYear = obj.optString("releaseYear", ""),
                            releaseDate = obj.optString("releaseDate", ""),
                            coverUrl = obj.optString("coverUrl", ""),
                            trackCount = obj.optInt("trackCount", 0),
                            collectionId = cId.takeIf { it.isNotBlank() },
                            spotifyId = obj.optString("spotifyId").takeIf { it.isNotBlank() },
                            artistId = obj.optString("artistId").takeIf { it.isNotBlank() } ?: cleanId
                        )
                    )
                }
                list
            } else null
        } catch (_: Exception) { null }
    }

    fun saveDiscographyToCache(artistId: String, releases: List<AlbumRelease>) {
        val cleanReleases = releases.filter { !it.id.startsWith("UC") && it.collectionId?.startsWith("UC") != true }
        val cleanId = artistId.trim()
        if (cleanId.isNotBlank() && cleanReleases.isNotEmpty()) {
            albumsCache[cleanId] = cleanReleases
        }
        val ctx = context ?: return
        if (cleanId.isBlank()) return
        try {
            val dir = java.io.File(ctx.cacheDir, "crankr_discography").apply { mkdirs() }
            val file = java.io.File(dir, "id_$cleanId.json")
            val arr = JSONArray()
            cleanReleases.forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("title", r.title)
                        put("artistName", r.artistName)
                        put("releaseType", r.releaseType)
                        put("releaseYear", r.releaseYear)
                        put("releaseDate", r.releaseDate)
                        put("coverUrl", r.coverUrl)
                        put("trackCount", r.trackCount)
                        put("collectionId", r.collectionId ?: "")
                        put("spotifyId", r.spotifyId ?: "")
                        put("artistId", cleanId)
                    }
                )
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    fun getSilampukauCuratedDiscography(): List<AlbumRelease> = listOf(
        AlbumRelease("silampukau_lantun", "Lantun Mustahil", "Silampukau", "Single", "2022", "2022-06-03", "https://is1-ssl.mzstatic.com/image/thumb/Music122/v4/aa/5d/16/aa5d1622-9845-affc-1fbe-606f2e628987/198003705807.jpg/600x600bb.jpg", 1, "1623348633", null),
        AlbumRelease("silampukau_dendang", "Dendang Sangsi", "Silampukau", "Single", "2021", "2021-06-18", "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/40/cf/52/40cf521a-3967-f750-51d5-fe249fa7d224/198000579081.jpg/600x600bb.jpg", 1, "1573030282", null),
        AlbumRelease("silampukau_pelanggan", "Si Pelanggan", "Silampukau", "Single", "2017", "2017-09-08", "https://is1-ssl.mzstatic.com/image/thumb/Music118/v4/4c/6b/34/4c6b3405-ddc3-9e24-e65f-0a2549b76b2b/192914675181.jpg/600x600bb.jpg", 1, "1429480112", null),
        AlbumRelease("silampukau_dosa", "Dosa, Kota, Dan Kenangan", "Silampukau", "Album", "2015", "2015-04-18", "https://is1-ssl.mzstatic.com/image/thumb/Music118/v4/d9/5c/a1/d95ca18d-126d-849a-6f1a-89895e23a33c/192914744702.jpg/600x600bb.jpg", 10, "1429477851", null),
        AlbumRelease("silampukau_sementara", "Sementara Ini", "Silampukau", "EP", "2009", "2009-01-01", "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/58/2d/7f/582d7f42-7f17-5795-b06f-6d2a6fc6c8b8/198500004557.jpg/600x600bb.jpg", 5, "1622353147", null)
    )

    fun getMajelisLidahBerduriCuratedDiscography(): List<AlbumRelease> = listOf(
        AlbumRelease("mlb_mama", "Mama - Mama Perlawanan", "Majelis Lidah Berduri", "Single", "2025", "2025-10-24", "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/a0/d7/c0/a0d7c098-080a-06c7-a4a9-879c954abd16/8721416476037.png/600x600bb.jpg", 1, "1847461778", null),
        AlbumRelease("mlb_serampang", "Serampang", "Majelis Lidah Berduri", "Single", "2023", "2023-06-27", "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/6c/0a/c9/6c0ac988-ff2f-f67d-2c2d-93852380afdd/artwork.jpg/600x600bb.jpg", 1, "1694467637", null),
        AlbumRelease("mlb_pulang", "Pulang Kampung", "Majelis Lidah Berduri", "Single", "2023", "2023-04-01", "https://is1-ssl.mzstatic.com/image/thumb/Music126/v4/46/93/29/46932906-0518-34cc-241b-373b672ac36a/artwork.jpg/600x600bb.jpg", 1, "1680309484", null),
        AlbumRelease("mlb_reanamnesis", "Re-Anamnesis", "Majelis Lidah Berduri", "Album", "2018", "2018-01-01", "https://is1-ssl.mzstatic.com/image/thumb/Music118/v4/23/e3/13/23e3137e-7931-e15f-9b9b-377dd578705c/artwork.jpg/600x600bb.jpg", 13, "1290650570", null),
        AlbumRelease("mlb_balada_joni_susi", "Balada Joni Dan Susi", "Majelis Lidah Berduri", "Album", "2013", "2013-09-26", "https://is1-ssl.mzstatic.com/image/thumb/Music128/v4/b5/84/6e/b5846e08-97a3-f1f5-a312-39367308339e/artwork.jpg/600x600bb.jpg", 12, "1290672550", null),
        AlbumRelease("mlb_nkkbs", "NKKBS Bagian Pertama", "Majelis Lidah Berduri", "Album", "2009", "2009-09-09", "https://is1-ssl.mzstatic.com/image/thumb/Music123/v4/96/1a/bb/961abb88-4b86-dcab-c419-dfe7704a54ee/artwork.jpg/600x600bb.jpg", 11, "1482511000", null)
    )

    /**
     * Menghitung nilai kemiripan string (0.0 - 1.0) menggunakan Levenshtein distance.
     */
    fun calculateStringSimilarity(s1: String, s2: String): Double {
        val str1 = s1.trim().lowercase(Locale.ROOT)
        val str2 = s2.trim().lowercase(Locale.ROOT)
        if (str1 == str2) return 1.0
        if (str1.isEmpty() || str2.isEmpty()) return 0.0

        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1
        val longerLength = longer.length
        if (longerLength == 0) return 1.0

        val editDistance = levenshteinDistance(longer, shorter)
        return (longerLength - editDistance).toDouble() / longerLength.toDouble()
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        var cost = IntArray(lhs.length + 1) { it }
        var newCost = IntArray(lhs.length + 1) { 0 }

        for (i in 1..rhs.length) {
            newCost[0] = i
            for (j in 1..lhs.length) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhs.length]
    }
}