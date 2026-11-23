package com.example.spotifyclone.data.model

data class SpotifyUser(
    val id: String,
    val displayName: String,
    val email: String? = null,
    val avatarUrl: String? = null,
    val product: String? = "premium"
)

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val trackCount: Int = 0,
    val ownerName: String = "You",
    val isSynced: Boolean = true,
    val sourceUrl: String? = null
)
