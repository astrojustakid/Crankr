package com.example.spotifyclone.data.model

import java.io.File

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val streamUrl: String,
    val coverUrl: String,
    val genre: String = "Chill",
    val localFilePath: String? = null,
    val youtubeVideoId: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val spotifyArtistId: String? = null,
    val isrc: String? = null,
    val bandcampUrl: String? = null,
    val source: String = "crankr",
    val purchasePrice: String? = null
) {
    val isDownloaded: Boolean
        get() = localFilePath != null && File(localFilePath).exists()

    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val artistName: String
        get() = artist

    val durationMs: Long
        get() = durationSeconds * 1000L

    val isBandcamp: Boolean
        get() = source.equals("bandcamp", ignoreCase = true) || !bandcampUrl.isNullOrBlank() || id.startsWith("bc_")

    val isLockedPurchaseOnly: Boolean
        get() = isBandcamp && (streamUrl.isBlank() || streamUrl.contains("locked"))

    val isPlayable: Boolean
        get() = isDownloaded || isBandcamp || (!youtubeVideoId.isNullOrBlank() && !youtubeVideoId.startsWith("c_")) || (streamUrl.isNotBlank() && !streamUrl.contains("placeholder"))
}
