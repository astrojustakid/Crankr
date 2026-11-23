package com.example.spotifyclone.service

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/**
 * CrankrDownloadService:
 * Subclass dari ExoDownloadService untuk menjaga kompatibilitas intent service yang sudah terdaftar.
 */
@OptIn(UnstableApi::class)
class CrankrDownloadService : ExoDownloadService()
