package com.example.spotifyclone.data.db

/**
 * Entitas riwayat pemutaran audio Crankr untuk Room/SQLite.
 * Mencatat trek yang diputar minimal 30 detik.
 */
data class ListeningHistoryEntity(
    val id: Long = 0L,
    val trackId: String,
    val trackTitle: String,
    val artistId: String,
    val artistName: String,
    val albumArtUrl: String,
    val durationPlayedMs: Long,
    val timestamp: Long
)

data class TopArtistStat(
    val artistName: String,
    val artistId: String,
    val albumArtUrl: String,
    val playCount: Int,
    val totalDurationMs: Long
)

data class TopTrackStat(
    val trackId: String,
    val trackTitle: String,
    val artistName: String,
    val albumArtUrl: String,
    val playCount: Int,
    val totalDurationMs: Long
)

data class CrankrRecapData(
    val totalMinutes: Long,
    val totalHours: Int,
    val uniqueArtistsCount: Int,
    val topTrack: TopTrackStat?,
    val topArtists: List<TopArtistStat>,
    val topTracks: List<TopTrackStat>,
    val personaTitle: String,
    val personaBadge: String,
    val personaQuote: String
)
