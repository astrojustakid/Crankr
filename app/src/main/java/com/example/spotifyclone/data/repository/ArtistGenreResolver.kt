package com.example.spotifyclone.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Menyelesaikan genre asli band/artis secara dinamis menggunakan iTunes Search API
 * dengan in-memory cache agar rekomendasi lagu di playlist menampilkan genre sesuai band-nya,
 * bukan hardcode satu genre saja.
 */
object ArtistGenreResolver {

    private const val TAG = "ArtistGenreResolver"
    private const val TIMEOUT_MS = 3500

    // Key: normalized primary artist name -> Value: resolved genre ("" jika tidak ditemukan)
    private val artistGenreCache = ConcurrentHashMap<String, String>()

    fun normalizePrimaryArtist(rawArtist: String): String {
        val cleaned = rawArtist
            .replace(Regex("""\s*\((feat\.?|ft\.?|featuring)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .split(Regex("""\s*(?:,|&|;|/|×|\bx\b)\s*""", RegexOption.IGNORE_CASE))
            .firstOrNull()
            .orEmpty()
            .trim()
        return cleaned.ifBlank { rawArtist.trim() }
    }

    private fun cacheKey(artist: String): String =
        normalizePrimaryArtist(artist).lowercase(Locale.ROOT)

    fun getCachedGenre(artist: String): String? {
        val key = cacheKey(artist)
        if (key.isBlank()) return null
        return artistGenreCache[key]
    }

    suspend fun resolveGenre(artist: String, trackTitle: String = ""): String = withContext(Dispatchers.IO) {
        val primaryArtist = normalizePrimaryArtist(artist)
        val key = primaryArtist.lowercase(Locale.ROOT)
        if (key.isBlank() || key == "artis" || key == "unknown artist") {
            return@withContext ""
        }

        artistGenreCache[key]?.let { return@withContext it }

        val fromArtist = fetchGenreFromItunesArtist(primaryArtist)
        val resolved = if (fromArtist.isNotBlank()) {
            fromArtist
        } else if (trackTitle.isNotBlank()) {
            fetchGenreFromItunesSong(primaryArtist, trackTitle)
        } else {
            ""
        }

        artistGenreCache[key] = resolved
        resolved
    }

    private fun fetchGenreFromItunesArtist(artist: String): String {
        return try {
            val encoded = URLEncoder.encode(artist, "UTF-8")
            val url = URL("https://itunes.apple.com/search?term=$encoded&entity=musicArtist&limit=6")
            val json = fetchJson(url) ?: return ""
            val results = json.optJSONArray("results") ?: return ""
            if (results.length() == 0) return ""

            val targetLoose = stripPunctuation(artist)
            var fallbackGenre = ""

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val name = item.optString("artistName", "").trim()
                val genre = sanitizeGenre(item.optString("primaryGenreName", ""))
                if (genre.isBlank()) continue

                if (name.equals(artist, ignoreCase = true) || stripPunctuation(name) == targetLoose) {
                    return genre
                }
                if (fallbackGenre.isBlank() &&
                    (stripPunctuation(name).contains(targetLoose) || targetLoose.contains(stripPunctuation(name)))
                ) {
                    fallbackGenre = genre
                }
            }
            fallbackGenre
        } catch (e: Exception) {
            Log.w(TAG, "Gagal resolve artist genre ($artist): ${e.message}")
            ""
        }
    }

    private fun fetchGenreFromItunesSong(artist: String, title: String): String {
        return try {
            val cleanTitle = title
                .replace(Regex("""\s*\([^)]*\)"""), "")
                .replace(Regex("""\s*\[[^]]*]"""), "")
                .trim()
            val encoded = URLEncoder.encode("$artist $cleanTitle", "UTF-8")
            val url = URL("https://itunes.apple.com/search?term=$encoded&entity=song&limit=6")
            val json = fetchJson(url) ?: return ""
            val results = json.optJSONArray("results") ?: return ""
            if (results.length() == 0) return ""

            val targetLoose = stripPunctuation(artist)
            var fallbackGenre = ""

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val name = item.optString("artistName", "").trim()
                val genre = sanitizeGenre(item.optString("primaryGenreName", ""))
                if (genre.isBlank()) continue

                if (name.equals(artist, ignoreCase = true) || stripPunctuation(name) == targetLoose) {
                    return genre
                }
                if (fallbackGenre.isBlank()) {
                    fallbackGenre = genre
                }
            }
            fallbackGenre
        } catch (e: Exception) {
            Log.w(TAG, "Gagal resolve song genre ($artist - $title): ${e.message}")
            ""
        }
    }

    private fun fetchJson(url: URL): JSONObject? {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CrankrMusic/2.0")
        }
        return try {
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body)
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun stripPunctuation(text: String): String =
        text.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")

    private fun sanitizeGenre(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        // Hindari label terlalu generik yang tidak informatif
        if (trimmed.equals("Music", ignoreCase = true) || trimmed.equals("Unknown", ignoreCase = true)) {
            return ""
        }
        return trimmed
    }
}
