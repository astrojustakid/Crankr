package com.example.spotifyclone.service.lyrics

import com.example.spotifyclone.data.model.LyricLine
import com.example.spotifyclone.data.model.LyricsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Penyedia lirik sekunder cadangan jika LRCLIB tidak menemukan lirik
 * untuk band independen lokal tertentu.
 */
class SecondaryLyricsService {

    companion object {
        private const val USER_AGENT = "CrankrMusicApp/1.0"
        private const val TIMEOUT_MS = 5000
    }

    /**
     * Mencoba mengambil lirik statis dari sumber sekunder terbuka (misal: lyrics.ovh).
     */
    suspend fun fetchSecondaryLyrics(title: String, artist: String): LyricsData? = withContext(Dispatchers.IO) {
        val cleanArtist = LrcLibService.cleanArtistName(artist)
        val cleanTitle = LrcLibService.cleanTrackTitle(title, cleanArtist)

        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null

        try {
            val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val urlString = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"

            val response = executeHttpGet(urlString) ?: return@withContext null
            val json = JSONObject(response)
            val rawLyrics = json.optString("lyrics").takeIf { it.isNotBlank() } ?: return@withContext null

            // Bersihkan baris pembuka generator lirik jika ada
            var cleanedLyrics = rawLyrics
                .replace(Regex("""(?i)^Paroles de la chanson.*?\n"""), "")
                .trim()

            if (cleanedLyrics.isNotBlank()) {
                val lines = cleanedLyrics.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { LyricLine(-1L, it) }

                if (lines.isNotEmpty()) {
                    return@withContext LyricsData(
                        lines = lines,
                        isSynced = false,
                        rawPlain = cleanedLyrics
                    )
                }
            }
        } catch (_: Exception) {}

        null
    }

    private fun executeHttpGet(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
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
