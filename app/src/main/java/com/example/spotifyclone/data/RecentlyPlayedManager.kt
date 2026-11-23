package com.example.spotifyclone.data

import android.content.Context
import com.example.spotifyclone.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RecentlyPlayedManager(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "recent_tracks.json")

    private val maxItems = 15

    suspend fun getRecentlyPlayed(): List<Track> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            val content = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<Track>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val vid = obj.optString("youtubeVideoId").takeIf { it.isNotEmpty() && it != "null" }
                val rawCover = obj.optString("coverUrl", "")
                val resolvedCover = when {
                    rawCover.isNotBlank() && !rawCover.contains("unsplash.com") -> rawCover
                    !vid.isNullOrEmpty() -> "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                    else -> rawCover
                }
                val tId = obj.optString("id", "").trim()
                if (tId.isBlank()) continue
                val track = Track(
                    id = tId,
                    title = obj.optString("title", "Unknown Track"),
                    artist = obj.optString("artist", "Unknown Artist"),
                    album = obj.optString("album", ""),
                    durationSeconds = obj.optInt("durationSeconds", 210),
                    streamUrl = obj.optString("streamUrl", ""),
                    coverUrl = resolvedCover,
                    genre = obj.optString("genre", "Pop"),
                    localFilePath = obj.optString("localFilePath").takeIf { it.isNotEmpty() && it != "null" },
                    youtubeVideoId = vid,
                    spotifyArtistId = obj.optString("spotifyArtistId").takeIf { it.isNotEmpty() && it != "null" },
                    isrc = obj.optString("isrc").takeIf { it.isNotEmpty() && it != "null" },
                    bandcampUrl = obj.optString("bandcampUrl").takeIf { it.isNotEmpty() && it != "null" },
                    source = obj.optString("source", "crankr"),
                    purchasePrice = obj.optString("purchasePrice").takeIf { it.isNotEmpty() && it != "null" }
                )
                list.add(track)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addTrack(track: Track): List<Track> = withContext(Dispatchers.IO) {
        val vid = track.youtubeVideoId
        val boundCover = when {
            track.coverUrl.isNotBlank() && !track.coverUrl.contains("unsplash.com") -> track.coverUrl
            !vid.isNullOrEmpty() -> "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
            else -> track.coverUrl
        }
        val safeTrack = track.copy(coverUrl = boundCover)
        val current = getRecentlyPlayed().toMutableList()
        current.removeAll { it.id == safeTrack.id }
        current.add(0, safeTrack)
        val trimmed = current.take(maxItems)
        saveList(trimmed)
        trimmed
    }

    private suspend fun saveList(tracks: List<Track>) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            for (t in tracks) {
                val obj = JSONObject()
                obj.put("id", t.id)
                obj.put("title", t.title)
                obj.put("artist", t.artist)
                obj.put("album", t.album)
                obj.put("durationSeconds", t.durationSeconds)
                obj.put("streamUrl", t.streamUrl)
                obj.put("coverUrl", t.coverUrl)
                obj.put("genre", t.genre)
                obj.put("localFilePath", t.localFilePath ?: "")
                obj.put("youtubeVideoId", t.youtubeVideoId ?: "")
                obj.put("spotifyArtistId", t.spotifyArtistId ?: "")
                obj.put("isrc", t.isrc ?: "")
                obj.put("bandcampUrl", t.bandcampUrl ?: "")
                obj.put("source", t.source)
                obj.put("purchasePrice", t.purchasePrice ?: "")
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
