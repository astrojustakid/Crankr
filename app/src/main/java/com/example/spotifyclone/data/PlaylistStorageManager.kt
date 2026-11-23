package com.example.spotifyclone.data

import android.content.Context
import com.example.spotifyclone.data.model.SpotifyPlaylist
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.youtube.YouTubeMusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PlaylistStorageManager(private val context: Context) {

    private val playlistsFile: File
        get() = File(context.filesDir, "saved_playlists.json")

    private fun getBackupFiles(): List<File> {
        val list = mutableListOf<File>()
        try {
            val docs = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            list.add(File(docs, "Crankr/crankr_playlists_backup.json"))
        } catch (_: Exception) {}
        try {
            val dls = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            list.add(File(dls, "Crankr/crankr_playlists_backup.json"))
        } catch (_: Exception) {}
        try {
            val ext = context.getExternalFilesDir(null)
            if (ext != null) {
                list.add(File(ext, "crankr_playlists_backup.json"))
            }
        } catch (_: Exception) {}
        return list
    }

    suspend fun loadPlaylists(): List<Pair<SpotifyPlaylist, List<Track>>> = withContext(Dispatchers.IO) {
        val file = playlistsFile
        if (!file.exists()) {
            // Coba pulihkan dari backup eksternal publik jika ada (misal setelah instal ulang atau update)
            var restoredFromBackup = false
            for (backup in getBackupFiles()) {
                if (backup.exists() && backup.length() > 10L) {
                    try {
                        val backupContent = backup.readText(Charsets.UTF_8).trim()
                        if (backupContent.startsWith("[") && backupContent.length > 20) {
                            file.writeText(backupContent, Charsets.UTF_8)
                            restoredFromBackup = true
                            break
                        }
                    } catch (_: Exception) {}
                }
            }
            if (!restoredFromBackup) {
                val defaults = seedDefaultPlaylists()
                savePlaylists(defaults)
                return@withContext defaults
            }
        }

        try {
            val jsonStr = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonStr)
            val result = mutableListOf<Pair<SpotifyPlaylist, List<Track>>>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val rawSourceUrl = obj.optString("sourceUrl").takeIf { it.isNotEmpty() && it != "null" }
                val pId = obj.optString("id", "").trim()
                if (pId.isBlank()) continue
                val deducedSourceUrl = rawSourceUrl ?: if (pId.length in 15..35 && !pId.startsWith("custom_") && !pId.startsWith("ytm_")) {
                    "https://open.spotify.com/playlist/$pId"
                } else null

                val playlist = SpotifyPlaylist(
                    id = pId,
                    name = obj.optString("name", "Playlist"),
                    description = obj.optString("description", ""),
                    imageUrl = obj.optString("imageUrl").takeIf { it.isNotEmpty() && it != "null" },
                    trackCount = obj.optInt("trackCount", 0),
                    ownerName = obj.optString("ownerName", "Saya"),
                    isSynced = obj.optBoolean("isSynced", true),
                    sourceUrl = deducedSourceUrl
                )

                val tracksArray = obj.optJSONArray("tracks") ?: JSONArray()
                val tracks = mutableListOf<Track>()
                for (j in 0 until tracksArray.length()) {
                    val tObj = tracksArray.getJSONObject(j)
                    val addedAt = tObj.optLong("addedAt", 0L).let {
                        if (it > 0L) it else System.currentTimeMillis() - (tracksArray.length() - j) * 60000L
                    }
                    val trackId = tObj.optString("id", "").trim()
                    if (trackId.isBlank()) continue
                    val track = Track(
                        id = trackId,
                        title = tObj.optString("title", "Unknown Track"),
                        artist = tObj.optString("artist", "Unknown Artist"),
                        album = tObj.optString("album", ""),
                        durationSeconds = tObj.optInt("durationSeconds", 210),
                        streamUrl = tObj.optString("streamUrl", ""),
                        coverUrl = tObj.optString("coverUrl", ""),
                        genre = tObj.optString("genre", "Pop"),
                        localFilePath = tObj.optString("localFilePath").takeIf { it.isNotEmpty() && it != "null" },
                        youtubeVideoId = tObj.optString("youtubeVideoId").takeIf { it.isNotEmpty() && it != "null" },
                        addedAt = addedAt,
                        spotifyArtistId = tObj.optString("spotifyArtistId").takeIf { it.isNotEmpty() && it != "null" },
                        isrc = tObj.optString("isrc").takeIf { it.isNotEmpty() && it != "null" },
                        bandcampUrl = tObj.optString("bandcampUrl").takeIf { it.isNotEmpty() && it != "null" },
                        source = tObj.optString("source", "crankr"),
                        purchasePrice = tObj.optString("purchasePrice").takeIf { it.isNotEmpty() && it != "null" }
                    )
                    tracks.add(track)
                }

                result.add(Pair(playlist.copy(trackCount = tracks.size), tracks))
            }

            if (result.isEmpty()) {
                val defaults = seedDefaultPlaylists()
                savePlaylists(defaults)
                return@withContext defaults
            }

            result
        } catch (e: Exception) {
            e.printStackTrace()
            val defaults = seedDefaultPlaylists()
            savePlaylists(defaults)
            defaults
        }
    }

    suspend fun savePlaylists(items: List<Pair<SpotifyPlaylist, List<Track>>>) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            for ((playlist, tracks) in items) {
                val obj = JSONObject()
                obj.put("id", playlist.id)
                obj.put("name", playlist.name)
                obj.put("description", playlist.description ?: "")
                obj.put("imageUrl", playlist.imageUrl ?: "")
                obj.put("trackCount", tracks.size)
                obj.put("ownerName", playlist.ownerName)
                obj.put("isSynced", playlist.isSynced)
                obj.put("sourceUrl", playlist.sourceUrl ?: "")

                val tracksArray = JSONArray()
                for (track in tracks) {
                    val tObj = JSONObject()
                    tObj.put("id", track.id)
                    tObj.put("title", track.title)
                    tObj.put("artist", track.artist)
                    tObj.put("album", track.album)
                    tObj.put("durationSeconds", track.durationSeconds)
                    tObj.put("streamUrl", track.streamUrl)
                    tObj.put("coverUrl", track.coverUrl)
                    tObj.put("genre", track.genre)
                    tObj.put("localFilePath", track.localFilePath ?: "")
                    tObj.put("youtubeVideoId", track.youtubeVideoId ?: "")
                    tObj.put("addedAt", track.addedAt)
                    tObj.put("spotifyArtistId", track.spotifyArtistId ?: "")
                    tObj.put("isrc", track.isrc ?: "")
                    tObj.put("bandcampUrl", track.bandcampUrl ?: "")
                    tObj.put("source", track.source)
                    tObj.put("purchasePrice", track.purchasePrice ?: "")
                    tracksArray.put(tObj)
                }
                obj.put("tracks", tracksArray)
                jsonArray.put(obj)
            }

            val jsonFormatted = jsonArray.toString(2)
            val file = playlistsFile
            val tempFile = File(context.filesDir, "saved_playlists.json.tmp")
            tempFile.writeText(jsonFormatted, Charsets.UTF_8)
            tempFile.renameTo(file)

            // Auto-backup ke public storage (Documents/Crankr & Download/Crankr) agar playlist tidak hilang saat uninstall/update
            for (backup in getBackupFiles()) {
                try {
                    backup.parentFile?.mkdirs()
                    backup.writeText(jsonFormatted, Charsets.UTF_8)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Membersihkan cache URL audio yang rusak / kedaluwarsa pada playlist yang tersinkronisasi
     * tanpa menghapus identitas permanen youtubeVideoId.
     */
    suspend fun clearCorruptedStreamUrls(): Unit = withContext(Dispatchers.IO) {
        try {
            val file = playlistsFile
            if (!file.exists()) return@withContext
            val playlists = loadPlaylists()
            var modified = false
            val updated = playlists.map { (playlist, tracks) ->
                if (playlist.isSynced) {
                    val cleanedTracks = tracks.map { track ->
                        if (track.localFilePath == null && track.streamUrl.isNotBlank()) {
                            modified = true
                            track.copy(
                                streamUrl = ""
                            )
                        } else {
                            track
                        }
                    }
                    Pair(playlist, cleanedTracks)
                } else {
                    Pair(playlist, tracks)
                }
            }
            if (modified) {
                savePlaylists(updated)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun exportPlaylistsJson(): String = withContext(Dispatchers.IO) {
        val file = playlistsFile
        if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            "[]"
        }
    }

    suspend fun importPlaylistsFromJson(jsonStr: String): Result<List<Pair<SpotifyPlaylist, List<Track>>>> = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray(jsonStr.trim())
            val result = mutableListOf<Pair<SpotifyPlaylist, List<Track>>>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val playlist = SpotifyPlaylist(
                    id = obj.optString("id", "imported_${System.currentTimeMillis()}_$i"),
                    name = obj.optString("name", "Playlist"),
                    description = obj.optString("description", ""),
                    imageUrl = obj.optString("imageUrl").takeIf { it.isNotEmpty() && it != "null" },
                    trackCount = obj.optInt("trackCount", 0),
                    ownerName = obj.optString("ownerName", "Saya"),
                    isSynced = obj.optBoolean("isSynced", false),
                    sourceUrl = obj.optString("sourceUrl").takeIf { it.isNotEmpty() && it != "null" }
                )

                val tracksArray = obj.optJSONArray("tracks") ?: JSONArray()
                val tracks = mutableListOf<Track>()
                for (j in 0 until tracksArray.length()) {
                    val tObj = tracksArray.getJSONObject(j)
                    val addedAt = tObj.optLong("addedAt", 0L).let {
                        if (it > 0L) it else System.currentTimeMillis() - (tracksArray.length() - j) * 60000L
                    }
                    val trackId = tObj.optString("id", "").trim()
                    if (trackId.isBlank()) continue
                    val track = Track(
                        id = trackId,
                        title = tObj.optString("title", "Unknown Track"),
                        artist = tObj.optString("artist", "Unknown Artist"),
                        album = tObj.optString("album", ""),
                        durationSeconds = tObj.optInt("durationSeconds", 210),
                        streamUrl = tObj.optString("streamUrl", ""),
                        coverUrl = tObj.optString("coverUrl", ""),
                        genre = tObj.optString("genre", "Pop"),
                        localFilePath = tObj.optString("localFilePath").takeIf { it.isNotEmpty() && it != "null" },
                        youtubeVideoId = tObj.optString("youtubeVideoId").takeIf { it.isNotEmpty() && it != "null" },
                        addedAt = addedAt,
                        spotifyArtistId = tObj.optString("spotifyArtistId").takeIf { it.isNotEmpty() && it != "null" },
                        isrc = tObj.optString("isrc").takeIf { it.isNotEmpty() && it != "null" },
                        bandcampUrl = tObj.optString("bandcampUrl").takeIf { it.isNotEmpty() && it != "null" },
                        source = tObj.optString("source", "crankr"),
                        purchasePrice = tObj.optString("purchasePrice").takeIf { it.isNotEmpty() && it != "null" }
                    )
                    tracks.add(track)
                }
                result.add(Pair(playlist.copy(trackCount = tracks.size), tracks))
            }

            if (result.isNotEmpty()) {
                savePlaylists(result)
                Result.success(result)
            } else {
                Result.failure(Exception("Data JSON kosong atau tidak valid"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun seedDefaultPlaylists(): List<Pair<SpotifyPlaylist, List<Track>>> {
        val ytService = YouTubeMusicService()
        val defaultPlaylists = ytService.getDefaultYouTubePlaylists()
        return defaultPlaylists.map { playlist ->
            val tracks = ytService.fetchPlaylistTracks(playlist.id).getOrDefault(emptyList())
            val cover = tracks.firstOrNull()?.coverUrl ?: playlist.imageUrl
            Pair(playlist.copy(trackCount = tracks.size, imageUrl = cover), tracks)
        }
    }
}
