package com.example.spotifyclone.service

import com.example.spotifyclone.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class YouTubeTrackInfo(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String
)

object MusicStreamResolver {

    private val cache = ConcurrentHashMap<String, String>()
    private val thumbnailCache = ConcurrentHashMap<String, String>()
    private val blacklistedVideoIds = ConcurrentHashMap.newKeySet<String>()

    // Blacklist filter for Official Music Videos, Live performances, compilations, remixes, covers, bootlegs, and mashups
    val studioBlacklistRegex = Regex(
        """(?i)(?:best\s+album\s+of|album\s+populer|kumpulan\s+lagu|full\s+album|greatest\s+hits|\bbest\s+of\b|top\s+tracks|kompilasi|compilation|pilihan\s+terbaik|tembang\s+kenangan|mixtape|\bmashup\b|official\s+music\s+video|\bmv\b|\bm/v\b|live\s+at|live\s+performance|\bconcert\b|\bremix\b|\bcover\b|acoustic\s+cover|\btribute\b|\bbootleg\b|best\s+songs|nonstop|slowed|reverb|karaoke|\blofi\b|\blo-fi\b|rekaman\s+penonton|audience|fancam|reaction|tutorial|1\s+hour|hour\s+loop|how\s+to\s+play|pintasan\s+keyboard|keyboard\s+shortcuts?|video\s+bundar|round\s+video|watch\s+full|subscribe|\bteaser\b|\btrailer\b|official\s+video|music\s+video|video\s+musik)"""
    )

    fun isCompilationOrBootleg(title: String, artist: String = ""): Boolean {
        val safeTitle = title.replace(Regex("""(?i)saturday\s+night\s+karaoke"""), "saturday night band")
        val safeArtist = artist.replace(Regex("""(?i)saturday\s+night\s+karaoke"""), "saturday night band")
        return com.example.spotifyclone.util.CrankrCatalogFilter.isBlacklisted(title, artist) ||
            com.example.spotifyclone.util.MetadataSanitizer.isBlockedArtifact(title, artist) ||
            studioBlacklistRegex.containsMatchIn(safeTitle) ||
            (safeArtist.isNotBlank() && studioBlacklistRegex.containsMatchIn(safeArtist))
    }

    fun markVideoRestricted(videoId: String) {
        blacklistedVideoIds.add(videoId)
        val toRemove = cache.filter { it.value == videoId }.keys
        toRemove.forEach { cache.remove(it) }
    }

    fun getThumbnailForVideo(videoId: String): String {
        return thumbnailCache[videoId] ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    fun cacheThumbnail(videoId: String, url: String) {
        if (videoId.isNotBlank() && url.isNotBlank()) {
            thumbnailCache[videoId] = url
        }
    }

    fun clearStreamCache() {
        cache.clear()
        thumbnailCache.clear()
        blacklistedVideoIds.clear()
    }

    suspend fun resolveYouTubeTrackInfo(
        title: String,
        artist: String,
        expectedDurationSeconds: Int? = null
    ): YouTubeTrackInfo? {
        val vid = resolveYouTubeVideoId(title, artist, expectedDurationSeconds) ?: return null
        return YouTubeTrackInfo(
            videoId = vid,
            title = title,
            artist = artist,
            thumbnailUrl = getThumbnailForVideo(vid)
        )
    }

    /**
     * Membersihkan string judul sebelum dicocokkan (menghapus tanda kurung, tanda baca,
     * serta tag seperti official, video, lirik, remaster, feat).
     */
    fun cleanTitleForMatching(title: String): String {
        return title.lowercase()
            .replace(Regex("""\([^)]*(official|video|audio|lirik|lyric|remaster|mv|music video|hd|4k|feat|ft)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[[^\]]*(official|video|audio|lirik|lyric|remaster|mv|music video|hd|4k|feat|ft)[^\]]*\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(official|video|audio|lirik|lyric|lyrics|mv|m/v|remastered|visualizer|feat|ft)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Mengekstrak kata kunci utama dari judul lagu asli Spotify untuk validasi pencocokan teks.
     */
    fun extractPrimaryKeywords(title: String): List<String> {
        val cleaned = cleanTitleForMatching(title)
        val stopWords = setOf("the", "a", "an", "and", "or", "in", "on", "at", "to", "for", "of", "with", "di", "ke", "dan", "yang", "dari", "untuk", "la", "le", "de")
        val words = cleaned.split(" ").map { it.trim() }.filter { it.length >= 2 }
        val filtered = words.filter { !stopWords.contains(it) }
        return if (filtered.isNotEmpty()) filtered else words
    }

    /**
     * Memvalidasi kesesuaian teks judul:
     * Menolak hasil pencarian jika judul tidak memuat kata kunci utama dari judul lagu asli Spotify,
     * meskipun nama band / artisnya sama.
     */
    fun isTitleMatching(expectedSpotifyTitle: String, candidateTitle: String): Boolean {
        val keywords = extractPrimaryKeywords(expectedSpotifyTitle)
        if (keywords.isEmpty()) return true
        val candidateClean = cleanTitleForMatching(candidateTitle)

        val matched = keywords.count { kw -> candidateClean.contains(kw) }
        return if (keywords.size <= 2) {
            matched == keywords.size
        } else {
            matched >= (keywords.size * 0.6).toInt().coerceAtLeast(2)
        }
    }

    suspend fun resolveAlternativeVideoId(title: String, artist: String, failedVideoId: String, expectedDurationSeconds: Int? = null): String? = withContext(Dispatchers.IO) {
        markVideoRestricted(failedVideoId)
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null

        val cacheKey = "$cleanTitle - $cleanArtist".lowercase()
        cache.remove(cacheKey)

        val altQuery = "$cleanArtist - $cleanTitle (Official Audio)"
        val altVid = searchVideoIdWithQuery(altQuery, expectedDurationSeconds, cleanTitle)
        if (altVid != null && !blacklistedVideoIds.contains(altVid)) {
            cache[cacheKey] = altVid
            return@withContext altVid
        }

        resolveYouTubeVideoId(title, artist, expectedDurationSeconds)
    }

    suspend fun resolveYouTubeVideoId(
        title: String,
        artist: String,
        expectedDurationSeconds: Int? = null
    ): String? = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null

        val cacheKey = "$cleanTitle - $cleanArtist".lowercase()
        val cached = cache[cacheKey]
        if (!cached.isNullOrEmpty() && !blacklistedVideoIds.contains(cached)) {
            return@withContext cached
        }

        // 1. Kueri pencarian spesifik resmi: "$artistTitle - $trackTitle (Official Audio)"
        val primaryQuery = "$cleanArtist - $cleanTitle (Official Audio)"
        val vid1 = searchVideoIdWithQuery(primaryQuery, expectedDurationSeconds, cleanTitle)
        if (vid1 != null && !blacklistedVideoIds.contains(vid1)) {
            cache[cacheKey] = vid1
            return@withContext vid1
        }

        // 2. Kueri cadangan resmi YouTube Music Topic: "$cleanArtist - $cleanTitle Topic"
        val topicQuery = "$cleanArtist - $cleanTitle Topic"
        val vid2 = searchVideoIdWithQuery(topicQuery, expectedDurationSeconds, cleanTitle)
        if (vid2 != null && !blacklistedVideoIds.contains(vid2)) {
            cache[cacheKey] = vid2
            return@withContext vid2
        }

        null
    }

    private fun parseDurationToSeconds(text: String): Int {
        val parts = text.trim().split(":")
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

    private fun extractDurationSeconds(renderer: JSONObject): Int {
        val fixedCols = renderer.optJSONArray("fixedColumns")
        if (fixedCols != null && fixedCols.length() > 0) {
            val durText = fixedCols.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            if (durText.contains(":")) {
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
                    if (t.contains(":") && t.length in 3..8) {
                        val sec = parseDurationToSeconds(t)
                        if (sec > 0) return sec
                    }
                }
            }
        }
        return 0
    }

    private fun searchVideoIdWithQuery(query: String, expectedDurationSeconds: Int? = null, expectedOriginalTitle: String? = null): String? {
        // 1. YouTube Music InnerTube Search (WEB_REMIX)
        try {
            val url = URL("https://music.youtube.com/youtubei/v1/search")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-youtube-client-name", "67")
                setRequestProperty("x-youtube-client-version", "1.20231204.01.00")
                setRequestProperty("Origin", "https://music.youtube.com")
                setRequestProperty("Referer", "https://music.youtube.com")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                connectTimeout = 7000
                readTimeout = 7000
            }

            val jsonBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20231204.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", query)
            }

            OutputStreamWriter(connection.outputStream).use { it.write(jsonBody.toString()) }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val rootObj = JSONObject(responseText)
                val tabs = rootObj.optJSONObject("contents")
                    ?.optJSONObject("tabbedSearchResultsRenderer")
                    ?.optJSONArray("tabs")
                val sectionList = tabs?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                if (sectionList != null) {
                    // Check Top Card Result first jika lolos kesesuaian judul dan durasi
                    for (s in 0 until sectionList.length()) {
                        val sec = sectionList.optJSONObject(s) ?: continue
                        val card = sec.optJSONObject("musicCardShelfRenderer")
                        if (card != null) {
                            val cardTitle = card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                            val subRuns = card.optJSONObject("subtitle")?.optJSONArray("runs")
                            val cardType = subRuns?.optJSONObject(0)?.optString("text").orEmpty()
                            val cardSubtitle = if (subRuns != null && subRuns.length() > 2) {
                                subRuns.optJSONObject(2)?.optString("text").orEmpty()
                            } else subRuns?.optJSONObject(0)?.optString("text").orEmpty()

                            val isMvOrLive = studioBlacklistRegex.containsMatchIn(cardTitle) ||
                                studioBlacklistRegex.containsMatchIn(cardSubtitle) ||
                                cardTitle.contains("video", ignoreCase = true) ||
                                cardType.equals("Video", ignoreCase = true)

                            val vid = card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
                                ?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                                ?: card.optJSONArray("buttons")?.optJSONObject(0)?.optJSONObject("buttonRenderer")
                                    ?.optJSONObject("command")?.optJSONObject("watchEndpoint")?.optString("videoId")
                                ?: ""

                            // Validasi kesesuaian judul untuk Top Card: tolak jika tidak memuat kata kunci judul Spotify
                            val matchesTitle = expectedOriginalTitle == null || isTitleMatching(expectedOriginalTitle, cardTitle)

                            if (vid.length == 11 && !blacklistedVideoIds.contains(vid) && !isMvOrLive && matchesTitle) {
                                var cardDuration = 0
                                if (subRuns != null) {
                                    for (r in 0 until subRuns.length()) {
                                        val runText = subRuns.optJSONObject(r)?.optString("text").orEmpty()
                                        if (runText.contains(":")) {
                                            cardDuration = parseDurationToSeconds(runText)
                                            break
                                        }
                                    }
                                }

                                // Bandingkan durasi stream hasil pencarian dengan durasi asli metadata lagu (toleransi 8 detik, 5-10 detik)
                                // Buang hasil jika selisih durasi terlalu jauh
                                if (expectedDurationSeconds != null && expectedDurationSeconds > 0) {
                                    if (cardDuration <= 0) continue
                                    val delta = kotlin.math.abs(cardDuration - expectedDurationSeconds)
                                    if (delta > 8) continue
                                }

                                extractHighResThumb(card.optJSONObject("thumbnail"), vid)
                                return vid
                            }
                        }
                    }

                    // Check items in musicShelfRenderer / itemSectionRenderer
                    // Jangan langsung mengambil item pertama: periksa kecocokan teks judul dan toleransi durasi <= 8 detik.
                    // Ambil trek PERTAMA yang lolos validasi tersebut.
                    for (s in 0 until sectionList.length()) {
                        val sec = sectionList.optJSONObject(s) ?: continue
                        val items = sec.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                            ?: sec.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                            ?: continue

                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            val vid = extractVideoId(renderer) ?: continue
                            if (blacklistedVideoIds.contains(vid)) continue

                            val title = extractTitle(renderer)

                            // 1. Periksa kesesuaian teks: pastikan kata kunci utama dari judul lagu asli Spotify terkandung di dalam judul video hasil pencarian
                            if (expectedOriginalTitle != null && !isTitleMatching(expectedOriginalTitle, title)) {
                                continue
                            }

                            val flexCols = renderer.optJSONArray("flexColumns")
                            val uploaderRuns = if (flexCols != null && flexCols.length() > 1) {
                                flexCols.optJSONObject(1)
                                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.optJSONObject("text")?.optJSONArray("runs")
                            } else null

                            var uploaderOrArtist = ""
                            if (uploaderRuns != null && uploaderRuns.length() > 0) {
                                val t0 = uploaderRuns.optJSONObject(0)?.optString("text").orEmpty()
                                uploaderOrArtist = if (uploaderRuns.length() >= 3) {
                                    uploaderRuns.optJSONObject(2)?.optString("text").orEmpty()
                                } else {
                                    t0
                                }
                            }

                            // Tolak video klip, rekaman live, remix, cover, atau bootleg
                            if (studioBlacklistRegex.containsMatchIn(title) || (uploaderOrArtist.isNotBlank() && studioBlacklistRegex.containsMatchIn(uploaderOrArtist))) {
                                continue
                            }

                            val candDuration = extractDurationSeconds(renderer)
                            // Tolak kompilasi penuh melebihi 10 menit
                            if (candDuration > 600) {
                                continue
                            }

                            // 2. Periksa durasi: bandingkan durasi lagu Spotify dengan durasi video yang ditemukan.
                            // Tolak kandidat audio jika tidak memiliki info durasi atau memiliki selisih lebih dari 8 detik (toleransi 5-10 detik).
                            if (expectedDurationSeconds != null && expectedDurationSeconds > 0) {
                                if (candDuration <= 0) {
                                    continue
                                }
                                val delta = kotlin.math.abs(candDuration - expectedDurationSeconds)
                                if (delta > 8) {
                                    continue
                                }
                            }

                            // 3. Ambil trek pertama yang lolos validasi kata kunci judul dan toleransi durasi tersebut!
                            extractHighResThumb(renderer.optJSONObject("thumbnail"), vid)
                            return vid
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Standard YouTube Web Search Fallback (dengan validasi judul & durasi ketat)
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://www.youtube.com/results?search_query=$encodedQuery")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                connectTimeout = 7000
                readTimeout = 7000
            }

            if (connection.responseCode == 200) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val videoBlockRegex = Regex(""""videoRenderer":\s*\{.*?"videoId":\s*"([a-zA-Z0-9_-]{11})".*?"title":\s*\{"runs":\s*\[\{"text":\s*"([^"]+)".*?"lengthText":\s*\{"simpleText":\s*"([^"]+)"""")
                val matches = videoBlockRegex.findAll(text)
                for (m in matches) {
                    val vid = m.groupValues[1]
                    val candTitle = m.groupValues[2]
                    val candLengthStr = m.groupValues[3]

                    if (blacklistedVideoIds.contains(vid)) continue
                    if (studioBlacklistRegex.containsMatchIn(candTitle)) continue
                    if (expectedOriginalTitle != null && !isTitleMatching(expectedOriginalTitle, candTitle)) continue

                    val candDuration = parseDurationToSeconds(candLengthStr)
                    if (candDuration > 600) continue
                    if (expectedDurationSeconds != null && expectedDurationSeconds > 0) {
                        if (candDuration <= 0) continue
                        val delta = kotlin.math.abs(candDuration - expectedDurationSeconds)
                        if (delta > 8) continue // Buang jika selisih durasi > 8 detik
                    }

                    return vid
                }
            }
        } catch (_: Exception) {}

        return null
    }

    fun sanitizeSearchQuery(rawQuery: String): String {
        return rawQuery
            .replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "")
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        val cleanQuery = sanitizeSearchQuery(query)
        if (cleanQuery.isBlank()) return@withContext emptyList()
        val results = mutableListOf<Track>()
        val seenVideoIds = mutableSetOf<String>()
        val userWantsCoverOrLive = studioBlacklistRegex.containsMatchIn(cleanQuery)

        // 1. YouTube Music InnerTube Search (WEB_REMIX)
        try {
            val url = URL("https://music.youtube.com/youtubei/v1/search")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-youtube-client-name", "67")
                setRequestProperty("x-youtube-client-version", "1.20231204.01.00")
                setRequestProperty("Origin", "https://music.youtube.com")
                setRequestProperty("Referer", "https://music.youtube.com")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                connectTimeout = 8000
                readTimeout = 8000
            }

            val jsonBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20231204.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", cleanQuery)
            }

            OutputStreamWriter(connection.outputStream).use { it.write(jsonBody.toString()) }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val rootObj = JSONObject(responseText)
                val tabs = rootObj.optJSONObject("contents")
                    ?.optJSONObject("tabbedSearchResultsRenderer")
                    ?.optJSONArray("tabs")
                val sectionList = tabs?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                if (sectionList != null) {
                    // Phase A: Top Card Result (Top official music video or release)
                    for (s in 0 until sectionList.length()) {
                        val sec = sectionList.optJSONObject(s) ?: continue
                        val card = sec.optJSONObject("musicCardShelfRenderer") ?: continue
                        val vid = card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
                            ?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                            ?: card.optJSONArray("buttons")?.optJSONObject(0)?.optJSONObject("buttonRenderer")
                                ?.optJSONObject("command")?.optJSONObject("watchEndpoint")?.optString("videoId")
                            ?: ""

                        if (vid.length == 11 && !blacklistedVideoIds.contains(vid) && seenVideoIds.add(vid)) {
                            val rawTitle = card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                            val subRuns = card.optJSONObject("subtitle")?.optJSONArray("runs")
                            val type = subRuns?.optJSONObject(0)?.optString("text").orEmpty()

                            // Skip non-music entities
                            if (!type.equals("Artist", ignoreCase = true) && !type.equals("Profile", ignoreCase = true) && !type.equals("Podcast", ignoreCase = true)) {
                                var artistName = "Artis"
                                if (subRuns != null) {
                                    if ((type.equals("Song", ignoreCase = true) || type.equals("Video", ignoreCase = true)) && subRuns.length() >= 3) {
                                        artistName = subRuns.optJSONObject(2)?.optString("text").orEmpty().ifBlank { "Artis" }
                                    } else if (subRuns.length() > 0) {
                                        val firstText = subRuns.optJSONObject(0)?.optString("text").orEmpty()
                                        if (firstText.isNotBlank()) artistName = firstText
                                    }
                                }

                                val rawThumb = extractHighResThumb(card.optJSONObject("thumbnail"), vid)
                                val cleanTitle = cleanDisplayTitle(rawTitle, artistName)

                                val isBlacklisted = studioBlacklistRegex.containsMatchIn(rawTitle) || studioBlacklistRegex.containsMatchIn(artistName)
                                if (!isBlacklisted || userWantsCoverOrLive) {
                                    results.add(
                                        Track(
                                            id = "yt_search_$vid",
                                            title = cleanTitle.ifEmpty { rawTitle.ifEmpty { cleanQuery } },
                                            artist = artistName,
                                            album = "Single",
                                            durationSeconds = 210,
                                            streamUrl = "https://music.youtube.com/watch?v=$vid",
                                            coverUrl = rawThumb,
                                            genre = "Pop Punk",
                                            youtubeVideoId = vid
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Phase B: Section items (Prioritizing Songs and Official Releases)
                    for (s in 0 until sectionList.length()) {
                        val sec = sectionList.optJSONObject(s) ?: continue
                        val items = sec.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                            ?: sec.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                            ?: continue

                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            val vid = extractVideoId(renderer) ?: continue
                            if (blacklistedVideoIds.contains(vid) || !seenVideoIds.add(vid)) continue

                            val flexCols = renderer.optJSONArray("flexColumns") ?: continue
                            if (flexCols.length() < 2) continue

                            val rawTitle = extractTitle(renderer)
                            val col1Runs = flexCols.getJSONObject(1)
                                .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")?.optJSONArray("runs") ?: JSONArray()

                            if (col1Runs.length() == 0) continue

                            val type = col1Runs.optJSONObject(0)?.optString("text").orEmpty()
                            // Skip podcasts, channels, profiles, episodes, playlists
                            if (type.matches(Regex("""(?i)^(Podcast|Episode|Profile|Artist|Playlist)$"""))) {
                                continue
                            }

                            var artistName = "Artis"
                            if (type.matches(Regex("""(?i)^(Song|Video)$"""))) {
                                // Pattern: ["Song", " • ", "Artist", " • ", "Album"]
                                if (col1Runs.length() >= 3) {
                                    artistName = col1Runs.optJSONObject(2)?.optString("text").orEmpty().trim()
                                }
                            } else {
                                artistName = col1Runs.optJSONObject(0)?.optString("text").orEmpty().trim()
                            }
                            if (artistName.isBlank()) artistName = "Artis"

                            // Reject video compilations and blacklisted keywords
                            val candDuration = extractDurationSeconds(renderer)
                            if (candDuration > 600) {
                                continue // Discard compilation over 10 minutes
                            }

                            if (!userWantsCoverOrLive && (studioBlacklistRegex.containsMatchIn(rawTitle) || studioBlacklistRegex.containsMatchIn(artistName))) {
                                continue
                            }

                            val cleanTitle = cleanDisplayTitle(rawTitle, artistName)
                            val thumb = extractHighResThumb(renderer.optJSONObject("thumbnail"), vid)

                            val candidateTrack = Track(
                                id = "yt_search_$vid",
                                title = cleanTitle.ifEmpty { rawTitle.ifEmpty { cleanQuery } },
                                artist = artistName,
                                album = "Single",
                                durationSeconds = if (candDuration > 0) candDuration else 210,
                                streamUrl = "https://music.youtube.com/watch?v=$vid",
                                coverUrl = thumb,
                                genre = "Pop Punk",
                                youtubeVideoId = vid
                            )
                            val sanitized = com.example.spotifyclone.util.MetadataSanitizer.sanitizeTrack(candidateTrack)
                            if (sanitized != null) {
                                results.add(sanitized)
                            }

                            if (results.size >= 12) break
                        }
                        if (results.size >= 12) break
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback: YouTube Search Web Scraper if needed
        if (results.size < 3) {
            try {
                val encodedQuery = URLEncoder.encode("$cleanQuery official audio", "UTF-8")
                val url = URL("https://www.youtube.com/results?search_query=$encodedQuery")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    connectTimeout = 7000
                    readTimeout = 7000
                }

                if (connection.responseCode == 200) {
                    val html = connection.inputStream.bufferedReader().use { it.readText() }
                    val vidRegex = Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
                    val titleRegex = Regex(""""title"\s*:\s*\{"runs":\s*\[\{"text":\s*"([^"]+)"""")
                    val vidMatches = vidRegex.findAll(html).toList()
                    val titleMatches = titleRegex.findAll(html).toList()

                    for (idx in vidMatches.indices) {
                        val vid = vidMatches[idx].groupValues[1]
                        if (!blacklistedVideoIds.contains(vid) && seenVideoIds.add(vid)) {
                            val rawTitle = if (idx < titleMatches.size) titleMatches[idx].groupValues[1] else cleanQuery

                            // Filter covers, podcasts, reactions, compilations
                            if (!userWantsCoverOrLive && studioBlacklistRegex.containsMatchIn(rawTitle)) {
                                continue
                            }

                            // If title is "Artist - Title", split accurately!
                            var parsedArtist = cleanQuery
                            var parsedTitle = rawTitle
                            if (rawTitle.contains(" - ")) {
                                val parts = rawTitle.split(" - ", limit = 2)
                                parsedArtist = parts[0].trim()
                                parsedTitle = parts[1].trim()
                            }

                            if (!userWantsCoverOrLive && (studioBlacklistRegex.containsMatchIn(parsedTitle) || studioBlacklistRegex.containsMatchIn(parsedArtist))) {
                                continue
                            }

                            val cleanTitle = cleanDisplayTitle(parsedTitle, parsedArtist)
                            val cover = getThumbnailForVideo(vid)

                            val candidateTrack = Track(
                                id = "yt_search_$vid",
                                title = cleanTitle.ifEmpty { parsedTitle.ifEmpty { cleanQuery } },
                                artist = parsedArtist,
                                album = "Single",
                                durationSeconds = 210,
                                streamUrl = "https://music.youtube.com/watch?v=$vid",
                                coverUrl = cover,
                                genre = "Pop Punk",
                                youtubeVideoId = vid
                            )
                            val sanitized = com.example.spotifyclone.util.MetadataSanitizer.sanitizeTrack(candidateTrack)
                            if (sanitized != null) {
                                results.add(sanitized)
                            }
                            if (results.size >= 10) break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        results
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

    private fun extractTitle(renderer: JSONObject): String {
        return renderer.optJSONArray("flexColumns")?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
    }

    private fun extractHighResThumb(thumbnailObj: JSONObject?, videoId: String): String {
        if (thumbnailObj == null) return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val thumbs = thumbnailObj.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        if (thumbs != null && thumbs.length() > 0) {
            val lastUrl = thumbs.getJSONObject(thumbs.length() - 1).optString("url")
            if (lastUrl.isNotBlank()) {
                if (lastUrl.contains("googleusercontent.com")) {
                    return lastUrl.replace(Regex("""=w\d+-h\d+.*"""), "=w800-h800-l90-rj")
                }
                if (lastUrl.contains("i.ytimg.com") && !lastUrl.contains("hqdefault") && !lastUrl.contains("maxresdefault")) {
                    return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                }
                return lastUrl
            }
        }
        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    private fun cleanDisplayTitle(title: String, artist: String): String {
        return com.example.spotifyclone.util.MetadataSanitizer.normalizeTitle(title, artist)
    }

    suspend fun resolveDirectAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.length != 11) return@withContext null

        // Try 1: Android Client InnerTube Player API
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip")
                setRequestProperty("X-YouTube-Client-Name", "3")
                setRequestProperty("X-YouTube-Client-Version", "19.09.37")
                connectTimeout = 7000
                readTimeout = 7000
            }

            val jsonBody = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "19.09.37")
                        put("androidSdkVersion", 34)
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }

            OutputStreamWriter(connection.outputStream).use { it.write(jsonBody.toString()) }

            if (connection.responseCode == 200) {
                val resp = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(resp)
                val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
                if (formats != null) {
                    var bestUrl: String? = null
                    var maxBitrate = -1
                    for (i in 0 until formats.length()) {
                        val fmt = formats.optJSONObject(i) ?: continue
                        val mime = fmt.optString("mimeType", "")
                        val streamUrl = fmt.optString("url", "")
                        if (streamUrl.isNotBlank() && mime.startsWith("audio/") && (mime.contains("mp4") || mime.contains("mp4a") || mime.contains("webm"))) {
                            val bitrate = fmt.optInt("bitrate", fmt.optInt("averageBitrate", 0))
                            if (bitrate > maxBitrate) {
                                maxBitrate = bitrate
                                bestUrl = streamUrl
                            }
                        }
                    }
                    if (bestUrl != null) return@withContext bestUrl
                }
            }
        } catch (_: Exception) {}

        // Try 2: IOS Client InnerTube Player API
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "com.google.ios.youtube/19.09.3 (iPhone16,2; U; CPU iOS 17_4 like Mac OS X)")
                setRequestProperty("X-YouTube-Client-Name", "5")
                setRequestProperty("X-YouTube-Client-Version", "19.09.3")
                connectTimeout = 5000
                readTimeout = 5000
            }

            val jsonBody = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "IOS")
                        put("clientVersion", "19.09.3")
                        put("deviceModel", "iPhone16,2")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }

            OutputStreamWriter(connection.outputStream).use { it.write(jsonBody.toString()) }

            if (connection.responseCode == 200) {
                val resp = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(resp)
                val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
                if (formats != null) {
                    var bestUrl: String? = null
                    var maxBitrate = -1
                    for (i in 0 until formats.length()) {
                        val fmt = formats.optJSONObject(i) ?: continue
                        val mime = fmt.optString("mimeType", "")
                        val streamUrl = fmt.optString("url", "")
                        if (streamUrl.isNotBlank() && mime.startsWith("audio/") && (mime.contains("mp4") || mime.contains("mp4a") || mime.contains("webm"))) {
                            val bitrate = fmt.optInt("bitrate", fmt.optInt("averageBitrate", 0))
                            if (bitrate > maxBitrate) {
                                maxBitrate = bitrate
                                bestUrl = streamUrl
                            }
                        }
                    }
                    if (bestUrl != null) return@withContext bestUrl
                }
            }
        } catch (_: Exception) {}

        null
    }
}