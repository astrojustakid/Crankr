package com.example.spotifyclone.data.model

data class GoogleUser(
    val id: String,
    val displayName: String,
    val email: String? = null,
    val avatarUrl: String? = null,
    val isYouTubeMusicConnected: Boolean = true
)
