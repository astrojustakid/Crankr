package com.example.spotifyclone.data

import android.content.Context
import android.content.SharedPreferences
import com.example.spotifyclone.data.model.Track
import org.json.JSONObject

data class ListeningStats(
    val totalMinutes: Long,
    val totalTracksPlayed: Int,
    val topTracks: List<Pair<String, Int>>,
    val topArtists: List<Pair<String, Int>>,
    val personaTitle: String,
    val personaSubtitle: String,
    val personaBadge: String
)

class ListeningStatsManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("crankr_listening_stats", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOTAL_SECONDS = "total_seconds"
        private const val KEY_TOTAL_TRACKS = "total_tracks"
        private const val KEY_TRACK_COUNTS = "track_counts_json"
        private const val KEY_ARTIST_COUNTS = "artist_counts_json"
    }

    @Synchronized
    fun recordTrackPlay(track: Track) {
        val totalTracks = prefs.getInt(KEY_TOTAL_TRACKS, 0) + 1
        
        // Track play counts
        val trackMap = getJsonMap(KEY_TRACK_COUNTS)
        val trackKey = "${track.title} - ${track.artist}"
        val currentTrackCount = trackMap.optInt(trackKey, 0) + 1
        trackMap.put(trackKey, currentTrackCount)

        // Artist play counts
        val artistMap = getJsonMap(KEY_ARTIST_COUNTS)
        val artistKey = track.artist.ifBlank { "Unknown Artist" }
        val currentArtistCount = artistMap.optInt(artistKey, 0) + 1
        artistMap.put(artistKey, currentArtistCount)

        prefs.edit()
            .putInt(KEY_TOTAL_TRACKS, totalTracks)
            .putString(KEY_TRACK_COUNTS, trackMap.toString())
            .putString(KEY_ARTIST_COUNTS, artistMap.toString())
            .apply()
    }

    @Synchronized
    fun addPlayTimeMs(ms: Long) {
        if (ms <= 0) return
        val currentMs = prefs.getLong("total_play_ms", 0L) + ms
        val totalSec = currentMs / 1000L
        prefs.edit()
            .putLong("total_play_ms", currentMs)
            .putLong(KEY_TOTAL_SECONDS, totalSec)
            .apply()
    }

    @Synchronized
    fun addListeningTime(seconds: Long) {
        if (seconds <= 0) return
        val currentSeconds = prefs.getLong(KEY_TOTAL_SECONDS, 0L) + seconds
        val currentMs = prefs.getLong("total_play_ms", 0L) + (seconds * 1000L)
        prefs.edit()
            .putLong(KEY_TOTAL_SECONDS, currentSeconds)
            .putLong("total_play_ms", currentMs)
            .apply()
    }

    fun getStats(): ListeningStats {
        var totalSec = prefs.getLong(KEY_TOTAL_SECONDS, 0L)
        val totalTracks = prefs.getInt(KEY_TOTAL_TRACKS, 0)

        // Fallback jika detik masih 0 tetapi trek sudah tercatat banyak
        if (totalSec <= 0L && totalTracks > 0) {
            try {
                val dbHelper = com.example.spotifyclone.data.db.ListeningHistoryDatabaseHelper.getInstance(context)
                val dbMinutes = kotlinx.coroutines.runBlocking { dbHelper.getTotalMinutesPlayed(0L) }
                if (dbMinutes > 0) {
                    totalSec = dbMinutes * 60L
                } else {
                    // Estimasi realistis dari riwayat track yang pernah tercatat (rata-rata 2 menit per lagu)
                    val estimatedMinutes = (totalTracks * 2L).coerceAtLeast(1L)
                    totalSec = estimatedMinutes * 60L
                }
                prefs.edit().putLong(KEY_TOTAL_SECONDS, totalSec).putLong("total_play_ms", totalSec * 1000L).apply()
            } catch (_: Exception) {}
        }

        val totalMinutes = totalSec / 60

        // Top Tracks
        val trackMap = getJsonMap(KEY_TRACK_COUNTS)
        val topTracks = mutableListOf<Pair<String, Int>>()
        val trackKeys = trackMap.keys()
        while (trackKeys.hasNext()) {
            val key = trackKeys.next()
            topTracks.add(Pair(key, trackMap.optInt(key, 0)))
        }
        val sortedTopTracks = topTracks.sortedByDescending { it.second }.take(5)

        // Top Artists
        val artistMap = getJsonMap(KEY_ARTIST_COUNTS)
        val topArtists = mutableListOf<Pair<String, Int>>()
        val artistKeys = artistMap.keys()
        while (artistKeys.hasNext()) {
            val key = artistKeys.next()
            topArtists.add(Pair(key, artistMap.optInt(key, 0)))
        }
        val sortedTopArtists = topArtists.sortedByDescending { it.second }.take(5)

        // Persona determination
        val (title, subtitle, badge) = when {
            totalMinutes < 15 && totalTracks < 5 -> Triple(
                "GARAGE ROOKIE",
                "Baru mulai menyetel distorsi amplifier di garasi",
                "ROOKIE"
            )
            totalMinutes < 60 -> Triple(
                "CLUB STAGE DIVER",
                "Lompat bebas ke kerumunan panggung underground",
                "STAGE DIVER"
            )
            totalMinutes < 240 -> Triple(
                "MOSHPIT VETERAN",
                "Tahan banting di tengah pusaran circle pit paling liar",
                "MOSHPIT VETERAN"
            )
            totalMinutes < 600 -> Triple(
                "PUNK TECH REBEL",
                "Meretas sistem dengan bass bertenaga dan distorsi penuh",
                "PUNK TECH REBEL"
            )
            else -> Triple(
                "IMMORTAL ANARCHIST",
                "Legenda punk sejati yang tak pernah mematikan amplifier",
                "IMMORTAL ANARCHIST"
            )
        }

        return ListeningStats(
            totalMinutes = totalMinutes,
            totalTracksPlayed = totalTracks,
            topTracks = sortedTopTracks,
            topArtists = sortedTopArtists,
            personaTitle = title,
            personaSubtitle = subtitle,
            personaBadge = badge
        )
    }

    fun resetStats() {
        prefs.edit().clear().apply()
    }

    private fun getJsonMap(key: String): JSONObject {
        val str = prefs.getString(key, null) ?: return JSONObject()
        return try {
            JSONObject(str)
        } catch (_: Exception) {
            JSONObject()
        }
    }
}
