package com.example.spotifyclone.util

import com.example.spotifyclone.data.model.Track

/**
 * Utility for parsing multi-artist strings and performing strict artist filtering.
 */
object ArtistParser {

    // Regex matching separators: comma, ampersand, feat., ft., with, x, slash
    private val separatorRegex = Regex("""(?i)\s*(?:,\s*|\s+&\s+|\s+feat\.?\s+|\s+ft\.?\s+|\s+with\s+|\s+x\s+|\s*/\s*)\s*""")

    /**
     * Splits a multi-artist string into individual distinct artist names.
     * E.g.:
     * - "Jack Ü, Justin Bieber" -> ["Jack Ü", "Justin Bieber"]
     * - "Skrillex & Diplo feat. Justin Bieber" -> ["Skrillex", "Diplo", "Justin Bieber"]
     * - "Coldplay with Beyoncé" -> ["Coldplay", "Beyoncé"]
     * - "Silampukau" -> ["Silampukau"]
     */
    fun parseArtists(rawArtist: String): List<String> {
        val trimmed = rawArtist.trim()
        if (trimmed.isBlank()) return emptyList()

        return trimmed
            .split(separatorRegex)
            .map { it.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'") }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Strict validation to verify if a track belongs to [targetArtist].
     * Returns true ONLY if targetArtist is the primary artist or an official collaborator.
     * Rejects unrelated tracks from search results.
     */
    fun isTrackStrictlyByArtist(track: Track, targetArtist: String): Boolean {
        val cleanTarget = targetArtist.trim().lowercase()
        if (cleanTarget.isBlank()) return false

        // 1. Check in parsed artists from track.artist (strict exact match per artist token)
        val parsedList = parseArtists(track.artist).map { it.trim().lowercase() }
        if (parsedList.any { it == cleanTarget }) {
            return true
        }

        // 2. Exact word boundary / phrase match in track.artist
        val artistPattern = Regex("""(?i)(?:^|[\s,&/()\[\]])\Q$cleanTarget\E(?:$|[\s,&/()\[\]])""")
        if (artistPattern.containsMatchIn(track.artist.lowercase())) {
            return true
        }

        // 3. Official featured credit in track title, e.g. "Song (feat. TargetArtist)"
        val titleLower = track.title.lowercase()
        if ((titleLower.contains("feat") || titleLower.contains("ft.") || titleLower.contains("with")) &&
            artistPattern.containsMatchIn(titleLower)) {
            return true
        }

        return false
    }
}
