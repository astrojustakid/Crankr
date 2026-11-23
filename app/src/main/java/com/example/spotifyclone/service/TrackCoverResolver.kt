package com.example.spotifyclone.service

import com.example.spotifyclone.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ResolvedCoverResult(
    val coverUrl: String,
    val albumName: String? = null,
    val youtubeVideoId: String? = null
)

object TrackCoverResolver {

    /**
     * Resolves the real, individual album artwork for a track using:
     * 1. Spotify oEmbed (official, instant album art for Spotify track IDs)
     * 2. Apple / iTunes Search API (600x600 high-res album cover & accurate album name)
     * 3. YouTube Music Thumbnail resolver (fallback)
     */
    suspend fun resolveTrackCover(
        track: Track,
        playlistCoverUrl: String? = null
    ): ResolvedCoverResult? = withContext(Dispatchers.IO) {
        val cleanTitle = track.title.trim()
        val cleanArtist = track.artist.trim()

        // 1. Try Spotify oEmbed if track has a Spotify ID
        val spotifyCleanId = extractSpotifyTrackId(track.id)
        if (!spotifyCleanId.isNullOrBlank()) {
            val spotifyCover = fetchSpotifyOEmbedCover(spotifyCleanId)
            if (!spotifyCover.isNullOrBlank() && spotifyCover != playlistCoverUrl) {
                return@withContext ResolvedCoverResult(coverUrl = spotifyCover)
            }
        }

        // 2. Try iTunes Search API (Ultra-fast, returns 600x600 official album cover & album title)
        val itunesResult = fetchItunesCover(cleanTitle, cleanArtist)
        if (itunesResult != null && itunesResult.coverUrl.isNotBlank() && itunesResult.coverUrl != playlistCoverUrl) {
            return@withContext itunesResult
        }

        // 3. Try YouTube Music Thumbnail
        val vid = track.youtubeVideoId
        if (!vid.isNullOrBlank()) {
            val ytThumb = MusicStreamResolver.getThumbnailForVideo(vid)
            if (ytThumb.isNotBlank() && ytThumb != playlistCoverUrl) {
                return@withContext ResolvedCoverResult(coverUrl = ytThumb, youtubeVideoId = vid)
            }
        } else {
            val ytInfo = try {
                MusicStreamResolver.resolveYouTubeTrackInfo(cleanTitle, cleanArtist)
            } catch (_: Exception) { null }
            if (ytInfo != null && ytInfo.thumbnailUrl.isNotBlank() && ytInfo.thumbnailUrl != playlistCoverUrl) {
                return@withContext ResolvedCoverResult(coverUrl = ytInfo.thumbnailUrl, youtubeVideoId = ytInfo.videoId)
            }
        }

        null
    }

    /**
     * Bulk resolve stale or playlist-inherited covers in parallel for an entire tracklist.
     */
    suspend fun bulkResolvePlaylistCovers(
        tracks: List<Track>,
        playlistCoverUrl: String? = null,
        playlistName: String? = null,
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<Track> = withContext(Dispatchers.IO) {
        val updatedList = tracks.toMutableList()

        // Find cover URLs that are duplicated across multiple songs or match playlist cover
        val coverCounts = tracks.groupingBy { it.coverUrl }.eachCount()
        val staleCovers = tracks.filter { t ->
            val isBlank = t.coverUrl.isBlank()
            val isPlaylistCover = !playlistCoverUrl.isNullOrBlank() && t.coverUrl == playlistCoverUrl
            val isUnsplash = t.coverUrl.contains("unsplash.com")
            val isSpotifyPlaylistImage = t.coverUrl.contains("ab67706c") // Spotify playlist mosaic/cover hash
            val isDuplicated = (coverCounts[t.coverUrl] ?: 0) >= 3 // If 3+ tracks share exact same cover
            isBlank || isPlaylistCover || isUnsplash || isSpotifyPlaylistImage || isDuplicated
        }

        if (staleCovers.isEmpty()) {
            return@withContext tracks
        }

        // Process in concurrent batches of 8 to avoid overwhelming the network
        val chunkSize = 8
        var resolvedCount = 0

        staleCovers.chunked(chunkSize).forEach { chunk ->
            val deferreds = chunk.map { track ->
                async {
                    val result = resolveTrackCover(track, playlistCoverUrl)
                    track to result
                }
            }

            val results = deferreds.awaitAll()
            results.forEach { (track, res) ->
                if (res != null && res.coverUrl.isNotBlank() && res.coverUrl != track.coverUrl) {
                    val idx = updatedList.indexOfFirst { it.id == track.id }
                    if (idx != -1) {
                        val curr = updatedList[idx]
                        val shouldUpdateAlbum = !res.albumName.isNullOrBlank() && (
                            curr.album.isBlank() ||
                            curr.album.equals("Single", ignoreCase = true) ||
                            curr.album.equals("Spotify Synced", ignoreCase = true) ||
                            (!playlistName.isNullOrBlank() && curr.album.equals(playlistName, ignoreCase = true)) ||
                            (curr.coverUrl == playlistCoverUrl)
                        )
                        updatedList[idx] = curr.copy(
                            coverUrl = res.coverUrl,
                            album = if (shouldUpdateAlbum) res.albumName else curr.album,
                            youtubeVideoId = curr.youtubeVideoId?.takeIf { it.isNotBlank() } ?: res.youtubeVideoId
                        )
                        resolvedCount++
                    }
                }
            }
            onProgress?.invoke(resolvedCount, staleCovers.size)
        }

        updatedList
    }

    private fun extractSpotifyTrackId(id: String): String? {
        val stripped = id.removePrefix("sp_").substringAfter("track:")
        return if (stripped.length in 15..35 && stripped.matches(Regex("^[a-zA-Z0-9]+$"))) {
            stripped
        } else null
    }

    fun fetchSpotifyOEmbedCover(spotifyTrackId: String): String? {
        return try {
            val url = URL("https://open.spotify.com/oembed?url=https://open.spotify.com/track/$spotifyTrackId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val response = reader.readText()
                reader.close()
                val json = JSONObject(response)
                val thumb = json.optString("thumbnail_url")
                if (thumb.isNotBlank()) thumb else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun fetchItunesCover(title: String, artist: String): ResolvedCoverResult? {
        return try {
            val cleanTitle = title.replace(Regex("""\(.*?\)|\[.*?\]"""), "").trim()
            val cleanArtist = artist.split(",", "&", "feat.", "ft.").firstOrNull()?.trim() ?: artist
            val query = URLEncoder.encode("$cleanTitle $cleanArtist", "UTF-8")
            val url = URL("https://itunes.apple.com/search?term=$query&media=music&entity=song&limit=5")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val response = reader.readText()
                reader.close()
                val json = JSONObject(response)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val itemTrack = item.optString("trackName", "")
                        val itemArtist = item.optString("artistName", "")

                        // 1. Validasi kecocokan judul secara akurat
                        if (!MusicStreamResolver.isTitleMatching(cleanTitle, itemTrack)) continue

                        // 2. Validasi kecocokan nama musisi / artis
                        val candArtistLower = itemArtist.lowercase().trim()
                        val expArtistLower = cleanArtist.lowercase().trim()
                        val artistMatches = candArtistLower.contains(expArtistLower) ||
                            expArtistLower.contains(candArtistLower) ||
                            expArtistLower.split(",", "&", "feat", "ft", "/").any { part ->
                                val p = part.trim()
                                p.length >= 3 && candArtistLower.contains(p)
                            }
                        if (!artistMatches) continue

                        val rawArt = item.optString("artworkUrl100")
                        val collectionName = item.optString("collectionName").takeIf { it.isNotBlank() }
                        val highResArt = rawArt.replace("100x100bb.jpg", "600x600bb.jpg")
                            .replace("100x100bb.png", "600x600bb.png")
                            .replace("100x100bb", "600x600bb")
                        if (highResArt.isNotBlank()) {
                            return ResolvedCoverResult(
                                coverUrl = highResArt,
                                albumName = collectionName
                            )
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
