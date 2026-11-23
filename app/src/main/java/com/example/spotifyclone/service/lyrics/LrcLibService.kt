package com.example.spotifyclone.service.lyrics

import com.example.spotifyclone.data.model.LyricLine
import com.example.spotifyclone.data.model.LyricsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Service untuk mengambil lirik lagu dari LRCLIB API resmi (https://lrclib.net).
 * Mengutamakan lirik tersinkronisasi (syncedLyrics), dengan fallback ke lirik statis (plainLyrics),
 * serta mekanisme pencarian fallback jika endpoint get spesifik menghasilkan 404.
 */
class LrcLibService {

    companion object {
        private const val USER_AGENT = "CrankrMusicApp/1.0 (https://github.com/crankr/crankr; contact@crankr.id)"
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val READ_TIMEOUT_MS = 7000

        /**
         * Membersihkan judul trek dari teks metadata tambahan seperti (feat. ...),
         * (Remastered), [Official Video], tanda baca khusus, dsb.
         */
        fun cleanTrackTitle(title: String, artist: String = ""): String {
            var cleaned = title

            // Hapus tanda kurung berisi video/audio/remaster/live dsb
            cleaned = cleaned.replace(
                Regex("""(?i)\s*[\(\[\{][^\)\]\}]*(?:official|video|audio|remaster|hd|4k|lyric|clip|version|explicit|lirik|bonus\s*track|live|deluxe|edit|acoustic)[^\)\]\}]*[\)\]\}]"""),
                ""
            )
            // Hapus feat/ft di dalam atau di luar kurung
            cleaned = cleaned.replace(Regex("""(?i)\b(feat\.?|ft\.?)\s+.*"""), "")

            // Hapus nama artis di awal judul jika ada pattern "Artis - Judul"
            if (artist.isNotBlank()) {
                val escapedArtist = Regex.escape(artist.trim())
                cleaned = cleaned.replace(Regex("""(?i)^\s*$escapedArtist\s*[-–—:]\s*"""), "")
            }

            // Hapus tanda strip di akhir judul seperti "- Remastered 2011"
            cleaned = cleaned.replace(
                Regex("""(?i)\s*[-–—:]\s*(?:remastered|remaster|official|audio|mono|stereo|deluxe|single|album\s*version)?\s*$"""),
                ""
            )

            // Bersihkan kurung kosong yang tersisa
            cleaned = cleaned.replace(Regex("""[\(\[\{]\s*[\)\]\}]"""), "")

            return cleaned.trim()
        }

        /**
         * Membersihkan nama artis untuk pencarian (mengambil artis utama pertama).
         */
        fun cleanArtistName(artist: String): String {
            var cleaned = artist
                .replace(Regex("""(?i)\b(feat\.?|ft\.?).*"""), "")
                .replace(Regex("""(?i)\s*-\s*Topic$"""), "")
                .replace(Regex("""(?i)\s*VEVO$"""), "")
            val first = cleaned.split(",", "&", "x", "/", ";").firstOrNull()?.trim()
            return if (!first.isNullOrBlank()) first else cleaned.trim()
        }
    }

    /**
     * Mengambil data lirik dengan alur:
     * 1. GET /api/get dengan parameter spesifik (track_name, artist_name, album_name, duration)
     * 2. Fallback GET /api/search?track_name={cleanTitle}&artist_name={cleanArtist}
     * 3. Fallback GET /api/search?q={cleanTitle} {cleanArtist}
     */
    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = 0
    ): LyricsData? = withContext(Dispatchers.IO) {
        val cleanArtist = cleanArtistName(artist)
        val cleanTitle = cleanTrackTitle(title, cleanArtist)

        if (cleanTitle.isBlank()) return@withContext null

        // 1. Pemanggilan Pertama: Parameter spesifik via GET /api/get
        try {
            val getResult = fetchExactGet(cleanTitle, cleanArtist, album, durationSeconds)
            if (getResult != null && getResult.lines.isNotEmpty()) {
                return@withContext getResult
            }
        } catch (_: Exception) {}

        // Jika judul asli berbeda dengan judul bersih, coba juga dengan judul asli sebelum fallback ke search
        if (title.trim() != cleanTitle) {
            try {
                val originalGetResult = fetchExactGet(title.trim(), cleanArtist, album, durationSeconds)
                if (originalGetResult != null && originalGetResult.lines.isNotEmpty()) {
                    return@withContext originalGetResult
                }
            } catch (_: Exception) {}
        }

        // 2. Mekanisme Fallback: GET /api/search?track_name={cleanTitle}&artist_name={cleanArtist}
        try {
            val searchStructuredResult = fetchSearchStructured(cleanTitle, cleanArtist)
            if (searchStructuredResult != null && searchStructuredResult.lines.isNotEmpty()) {
                return@withContext searchStructuredResult
            }
        } catch (_: Exception) {}

        // 3. Fallback Kueri Pencarian Bebas: GET /api/search?q={cleanTitle} {cleanArtist}
        try {
            val searchQuery = "$cleanTitle $cleanArtist".trim()
            val searchGeneralResult = fetchSearchGeneral(searchQuery, cleanTitle, cleanArtist)
            if (searchGeneralResult != null && searchGeneralResult.lines.isNotEmpty()) {
                return@withContext searchGeneralResult
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Memanggil endpoint GET https://lrclib.net/api/get
     */
    private fun fetchExactGet(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int
    ): LyricsData? {
        val params = StringBuilder()
        params.append("track_name=").append(URLEncoder.encode(title, "UTF-8"))
        params.append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))

        val cleanAlbum = album?.trim()
        if (!cleanAlbum.isNullOrBlank() && !cleanAlbum.equals("Unknown", ignoreCase = true) && !cleanAlbum.equals("Single", ignoreCase = true)) {
            params.append("&album_name=").append(URLEncoder.encode(cleanAlbum, "UTF-8"))
        }

        if (durationSeconds > 0) {
            params.append("&duration=").append(durationSeconds)
        }

        val urlString = "https://lrclib.net/api/get?$params"
        val response = executeHttpGet(urlString) ?: return null

        return try {
            val json = JSONObject(response)
            parseLyricsJson(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Memanggil endpoint GET https://lrclib.net/api/search?track_name=...&artist_name=...
     */
    private fun fetchSearchStructured(title: String, artist: String): LyricsData? {
        val params = "track_name=${URLEncoder.encode(title, "UTF-8")}&artist_name=${URLEncoder.encode(artist, "UTF-8")}"
        val urlString = "https://lrclib.net/api/search?$params"
        val response = executeHttpGet(urlString) ?: return null

        return parseSearchResults(response, title, artist)
    }

    /**
     * Memanggil endpoint GET https://lrclib.net/api/search?q=...
     */
    private fun fetchSearchGeneral(query: String, expectedTitle: String, expectedArtist: String): LyricsData? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val urlString = "https://lrclib.net/api/search?q=$encodedQuery"
        val response = executeHttpGet(urlString) ?: return null

        return parseSearchResults(response, expectedTitle, expectedArtist)
    }

    /**
     * Mem-parsing array hasil pencarian dari LRCLIB dan mengambil kecocokan terbaik (mengutamakan syncedLyrics).
     */
    private fun parseSearchResults(jsonResponse: String, expectedTitle: String, expectedArtist: String): LyricsData? {
        val array = try {
            JSONArray(jsonResponse)
        } catch (_: Exception) {
            return null
        }

        var bestSynced: LyricsData? = null
        var bestPlain: LyricsData? = null

        val normExpectedTitle = expectedTitle.lowercase()
        val normExpectedArtist = expectedArtist.lowercase()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val trackName = item.optString("trackName").takeIf { it.isNotBlank() } ?: item.optString("name")
            val artistName = item.optString("artistName")

            val normItemTitle = trackName.lowercase()
            val normItemArtist = artistName.lowercase()

            val isTitleMatch = normItemTitle.contains(normExpectedTitle) || normExpectedTitle.contains(normItemTitle)
            val isArtistMatch = normItemArtist.contains(normExpectedArtist) || normExpectedArtist.contains(normItemArtist)

            if (isTitleMatch || isArtistMatch || array.length() == 1) {
                val parsed = parseLyricsJson(item)
                if (parsed != null && parsed.lines.isNotEmpty()) {
                    if (parsed.isSynced) {
                        bestSynced = parsed
                        break // Prioritaskan lirik tersinkronisasi pertama yang cocok
                    } else if (bestPlain == null) {
                        bestPlain = parsed
                    }
                }
            }
        }

        return bestSynced ?: bestPlain
    }

    /**
     * Parsing JSONObject LRCLIB ke model LyricsData.
     * Mengutamakan syncedLyrics, lalu plainLyrics.
     */
    fun parseLyricsJson(json: JSONObject): LyricsData? {
        val isInstrumental = json.optBoolean("instrumental", false)
        if (isInstrumental) {
            return LyricsData(
                lines = listOf(LyricLine(0L, "♪ Lagu Instrumental ♪")),
                isSynced = false,
                rawPlain = "♪ Lagu Instrumental ♪"
            )
        }

        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }

        // Prioritas 1: Lirik Tersinkronisasi
        if (!synced.isNullOrBlank()) {
            val parsedLines = parseLrc(synced)
            if (parsedLines.isNotEmpty()) {
                return LyricsData(
                    lines = parsedLines,
                    isSynced = true,
                    rawPlain = plain ?: parsedLines.joinToString("\n") { it.text }
                )
            }
        }

        // Prioritas 2: Lirik Statis Biasa
        if (!plain.isNullOrBlank()) {
            val lines = plain.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LyricLine(-1L, it) }

            if (lines.isNotEmpty()) {
                return LyricsData(
                    lines = lines,
                    isSynced = false,
                    rawPlain = plain
                )
            }
        }

        return null
    }

    /**
     * Parser teks format LRC [mm:ss.xx] atau [mm:ss.xxx] ke List<LyricLine>.
     */
    fun parseLrc(lrcText: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        // Format: [00:12.34] atau [01:23.456] atau [00:12]
        val lrcRegex = Regex("""^\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]\s*(.*)$""")

        for (rawLine in lrcText.lines()) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val match = lrcRegex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = match.groupValues[3]
                val fractionMs = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    3 -> (fracStr.toLongOrNull() ?: 0L)
                    else -> 0L
                }
                val timestampMs = (min * 60 + sec) * 1000 + fractionMs
                val text = match.groupValues[4].trim()
                result.add(LyricLine(timestampMs, text))
            }
        }
        return result.sortedBy { it.timestampMs }
    }

    private fun executeHttpGet(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
                reader.close()
                sb.toString()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
