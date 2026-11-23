package com.example.spotifyclone.data.model

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class LyricsData(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val rawPlain: String? = null
)
