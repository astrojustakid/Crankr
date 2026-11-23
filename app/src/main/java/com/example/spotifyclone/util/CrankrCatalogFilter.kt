package com.example.spotifyclone.util

/**
 * Central strict blacklist and sanitization filter for Crankr:
 * - Rejects compilation albums, mixtapes, tribute albums, bootlegs, and greatest hits.
 * - Rejects cover versions, karaoke, mashups, lofi/slowed/reverb edits.
 * - Rejects video metadata, platform system terms, and web artifacts:
 *   pintasan keyboard, keyboard shortcuts, video bundar, round video, watch full, official video, subscribe, teaser.
 * - Rejects fake identical title=artist tracks (e.g. title "the skit" by "the skit") unless verified official release.
 * - Enforces studio release integrity across Search, Recommendations, and Discography.
 */
object CrankrCatalogFilter {

    private val webAndSystemArtifacts = listOf(
        "pintasan keyboard",
        "keyboard shortcuts",
        "keyboard shortcut",
        "video bundar",
        "round video",
        "watch full",
        "watch video",
        "official video",
        "official music video",
        "official visualizer",
        "video musik",
        "video klip",
        "video clip",
        "music video",
        "subscribe",
        "teaser",
        "trailer",
        "full episode",
        "short",
        "shorts"
    )

    private val blacklistKeywords = listOf(
        "best album of",
        "album populer",
        "kumpulan lagu",
        "full album",
        "greatest hits",
        "best of",
        "top tracks",
        "kompilasi",
        "compilation",
        "pilihan terbaik",
        "tembang kenangan",
        "mixtape",
        "mashup",
        "cover",
        "acoustic cover",
        "tribute",
        "remix",
        "bootleg",
        "karaoke",
        "lofi",
        "lo-fi",
        "slowed",
        "reverb",
        "rekaman penonton",
        "audience",
        "fancam",
        "reaction",
        "tutorial",
        "1 hour",
        "hour loop",
        "how to play"
    ) + webAndSystemArtifacts

    private val blacklistRegex = Regex(
        """(?i)(?:best\s+album\s+of|album\s+populer|kumpulan\s+lagu|full\s+album|greatest\s+hits|\bbest\s+of\b|top\s+tracks|kompilasi|compilation|pilihan\s+terbaik|tembang\s+kenangan|mixtape|\bmashup\b|\bcover\b|acoustic\s+cover|\btribute\b|\bremix\b|\bbootleg\b|karaoke|\blofi\b|\blo-fi\b|slowed|reverb|pintasan\s+keyboard|keyboard\s+shortcuts?|video\s+bundar|round\s+video|watch\s+full|subscribe|\bteaser\b|\btrailer\b|official\s+video|music\s+video|video\s+musik)"""
    )

    /**
     * Checks if a title or artist name matches compilation, bootleg, or unofficial release patterns.
     */
    fun isBlacklisted(title: String, artist: String = ""): Boolean {
        val cleanTitle = title.trim().lowercase()
        val cleanArtist = artist.trim().lowercase()

        if (cleanTitle.isBlank()) return false

        // Pengecualian khusus band resmi yang namanya memuat kata kunci (misal: "Saturday Night Karaoke")
        val safeTitle = cleanTitle.replace(Regex("""(?i)saturday\s+night\s+karaoke"""), "saturday night band")
        val safeArtist = cleanArtist.replace(Regex("""(?i)saturday\s+night\s+karaoke"""), "saturday night band")

        if (blacklistRegex.containsMatchIn(safeTitle) || (safeArtist.isNotBlank() && blacklistRegex.containsMatchIn(safeArtist))) {
            return true
        }

        return blacklistKeywords.any { safeTitle.contains(it) || (safeArtist.isNotBlank() && safeArtist.contains(it)) }
    }

    /**
     * Memeriksa apakah judul lagu merupakan nama pintasan antarmuka, istilah sistem, atau artefak web.
     */
    fun isWebOrSystemArtifact(title: String): Boolean {
        val clean = title.trim().lowercase()
        return webAndSystemArtifacts.any { clean.contains(it) }
    }

    /**
     * Memeriksa apakah judul lagu sama persis dengan nama band (misal judul lagu "The Skit" oleh artis "The Skit").
     * Dibuang kecuali jika lagu tersebut memang terdaftar di album/single resmi dengan ID Spotify yang valid.
     */
    fun isIdenticalTitleAndArtist(title: String, artist: String, isOfficialSpotify: Boolean): Boolean {
        val cleanTitle = title.trim().lowercase()
        val cleanArtist = artist.trim().lowercase()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return false

        val isSame = cleanTitle == cleanArtist ||
                cleanTitle == "the $cleanArtist" ||
                "the $cleanTitle" == cleanArtist

        if (isSame) {
            // Tolak jika bukan lagu berlisensi resmi Spotify
            return !isOfficialSpotify
        }
        return false
    }

    /**
     * Filter ketat terpusat untuk sanitasi hasil pencarian trek Crankr:
     * - Tolak durasi tidak wajar (<30s atau >600s)
     * - Tolak artefak web/sistem (pintasan keyboard, video bundar, watch full, subscribe, teaser)
     * - Tolak kompilasi/bootleg/cover
     * - Tolak judul sama persis dengan band jika tanpa verifikasi Spotify
     */
    fun isValidSearchTrack(
        title: String,
        artist: String,
        isOfficialSpotify: Boolean = false,
        durationSeconds: Int = 180
    ): Boolean {
        if (title.isBlank()) return false
        if (durationSeconds !in 30..600) return false
        if (isWebOrSystemArtifact(title)) return false
        if (isBlacklisted(title, artist)) return false
        if (isIdenticalTitleAndArtist(title, artist, isOfficialSpotify)) return false
        return true
    }
}
