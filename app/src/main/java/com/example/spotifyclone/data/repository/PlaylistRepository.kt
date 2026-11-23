package com.example.spotifyclone.data.repository

import android.content.Context
import com.example.spotifyclone.data.PlaylistStorageManager
import com.example.spotifyclone.data.model.SpotifyPlaylist
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.AudioStreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Objek relasi terikat untuk trek playlist:
 * Memastikan spotifyTrackId, title, artistName, dan audioSourceId disimpan secara terikat
 * dalam satu objek relasi tanpa saling menimpa saat operasi batch insert.
 */
data class PlaylistTrackRelation(
    val spotifyTrackId: String,
    val title: String,
    val artistName: String,
    val audioSourceId: String?,
    val durationSeconds: Int = 0,
    val coverUrl: String = "",
    val album: String = "",
    val genre: String = "Pop",
    val localFilePath: String? = null,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toTrack(): Track {
        return Track(
            id = spotifyTrackId,
            title = title,
            artist = artistName,
            album = album,
            durationSeconds = durationSeconds,
            streamUrl = "",
            coverUrl = coverUrl,
            genre = genre,
            localFilePath = localFilePath,
            youtubeVideoId = audioSourceId,
            addedAt = addedAt
        )
    }

    companion object {
        fun fromTrack(track: Track): PlaylistTrackRelation {
            return PlaylistTrackRelation(
                spotifyTrackId = track.id,
                title = track.title,
                artistName = track.artist,
                audioSourceId = track.youtubeVideoId,
                durationSeconds = track.durationSeconds,
                coverUrl = track.coverUrl,
                album = track.album,
                genre = track.genre,
                localFilePath = track.localFilePath,
                addedAt = track.addedAt
            )
        }
    }
}

/**
 * Repository terpusat untuk operasi penyimpanan dan sinkronisasi playlist Crankr.
 * Menjamin integritas relasi trek saat operasi batch insert dan resolusi stream audio.
 */
class PlaylistRepository(
    private val context: Context,
    private val storageManager: PlaylistStorageManager = PlaylistStorageManager(context)
) {
    private val batchMutex = Mutex()

    /**
     * Menyimpan batch relasi trek ke playlist secara aman dan terikat.
     * Mencegah bug audio tertukar dengan memastikan spotifyTrackId, title, artistName,
     * dan audioSourceId selalu disimpan dalam satu unit atomik.
     */
    suspend fun batchInsertPlaylistTracks(
        playlistId: String,
        relations: List<PlaylistTrackRelation>
    ): Unit = withContext(Dispatchers.IO) {
        batchMutex.withLock {
            val allPlaylists = storageManager.loadPlaylists().toMutableList()
            val index = allPlaylists.indexOfFirst { it.first.id == playlistId }
            if (index >= 0) {
                val currentPair = allPlaylists[index]
                val currentTracks = currentPair.second.toMutableList()

                // Tambahkan atau perbarui relasi terikat berdasarkan spotifyTrackId unik
                val relationMap = relations.associateBy { it.spotifyTrackId }
                val updatedTracks = currentTracks.map { existing ->
                    val rel = relationMap[existing.id]
                    if (rel != null) rel.toTrack() else existing
                }.toMutableList()

                // Tambahkan trek baru yang belum ada
                relations.forEach { rel ->
                    if (updatedTracks.none { it.id == rel.spotifyTrackId }) {
                        updatedTracks.add(rel.toTrack())
                    }
                }

                allPlaylists[index] = Pair(
                    currentPair.first.copy(trackCount = updatedTracks.size),
                    updatedTracks
                )
                storageManager.savePlaylists(allPlaylists)
            }
        }
    }

    /**
     * Mengambil daftar relasi trek yang terikat untuk playlist tertentu.
     */
    suspend fun getPlaylistRelations(playlistId: String): List<PlaylistTrackRelation> = withContext(Dispatchers.IO) {
        val allPlaylists = storageManager.loadPlaylists()
        val pair = allPlaylists.firstOrNull { it.first.id == playlistId } ?: return@withContext emptyList()
        pair.second.map { PlaylistTrackRelation.fromTrack(it) }
    }

    /**
     * Memperbarui atau mengisi audioSourceId pada trek playlist yang belum memiliki sumber stream.
     * Menggunakan validasi judul presisi dan toleransi durasi ketat 5-7 detik.
     */
    suspend fun resolveAndBindTrackAudio(
        track: Track
    ): PlaylistTrackRelation = withContext(Dispatchers.IO) {
        if (!track.youtubeVideoId.isNullOrBlank()) {
            return@withContext PlaylistTrackRelation.fromTrack(track)
        }

        val resolvedVid = AudioStreamResolver.resolveYouTubeVideoId(
            title = track.title,
            artist = track.artist,
            expectedDurationSeconds = track.durationSeconds
        )

        val updated = if (!resolvedVid.isNullOrBlank()) {
            track.copy(youtubeVideoId = resolvedVid)
        } else {
            track
        }

        PlaylistTrackRelation.fromTrack(updated)
    }

    /**
     * Memuat seluruh playlist dan trek yang tersimpan.
     */
    suspend fun loadPlaylists(): List<Pair<SpotifyPlaylist, List<Track>>> = withContext(Dispatchers.IO) {
        storageManager.loadPlaylists()
    }

    /**
     * Menyimpan seluruh playlist dan trek.
     */
    suspend fun savePlaylists(items: List<Pair<SpotifyPlaylist, List<Track>>>) = withContext(Dispatchers.IO) {
        batchMutex.withLock {
            storageManager.savePlaylists(items)
        }
    }

    /**
     * Membersihkan cache URL audio lokal dan reset relasi trek playlist
     * agar trek pada playlist sinkronisasi yang sempat tertukar memuat ulang tautan audio yang benar.
     */
    suspend fun clearStreamUrlCache(): Unit = withContext(Dispatchers.IO) {
        batchMutex.withLock {
            AudioStreamResolver.clearStreamCache()
        }
    }
}
