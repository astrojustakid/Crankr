package com.example.spotifyclone.data

import android.content.Context
import com.example.spotifyclone.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LikedSongsManager(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "liked_songs.json")

    suspend fun getLikedTracks(): List<Track> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            val content = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<Track>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val addedAt = obj.optLong("addedAt", 0L).let {
                    if (it > 0L) it else System.currentTimeMillis() - (jsonArray.length() - i) * 60000L
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
                    coverUrl = obj.optString("coverUrl", ""),
                    genre = obj.optString("genre", "Pop"),
                    localFilePath = obj.optString("localFilePath").takeIf { it.isNotEmpty() && it != "null" },
                    youtubeVideoId = obj.optString("youtubeVideoId").takeIf { it.isNotEmpty() && it != "null" },
                    addedAt = addedAt,
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

    suspend fun toggleLiked(track: Track): List<Track> = withContext(Dispatchers.IO) {
        val current = getLikedTracks().toMutableList()
        val index = current.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(0, track.copy(addedAt = System.currentTimeMillis()))
        }
        saveList(current)
        current
    }

    suspend fun removeLiked(trackId: String): List<Track> = withContext(Dispatchers.IO) {
        val current = getLikedTracks().toMutableList()
        current.removeAll { it.id == trackId }
        saveList(current)
        current
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
                obj.put("addedAt", t.addedAt)
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
