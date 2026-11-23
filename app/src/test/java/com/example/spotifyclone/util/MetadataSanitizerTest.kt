package com.example.spotifyclone.util

import com.example.spotifyclone.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Test untuk verifikasi logika MetadataSanitizer:
 * - Pembersihan noise tanda kurung (Official Music Video, Lyric Video, dll.)
 * - Pembersihan prefix artis dari judul
 * - Deteksi artefak UI/sistem
 * - Filter anti-cover, anti-karaoke, anti-live
 */
class MetadataSanitizerTest {

    @Test
    fun normalizeTitle_removesBracketNoise() {
        val raw = "Tafsir Mistik (Official Music Video)"
        val cleaned = MetadataSanitizer.normalizeTitle(raw)
        assertEquals("Tafsir Mistik", cleaned)
    }

    @Test
    fun normalizeTitle_removesLyricVideoAndHD() {
        val raw = "UFO [Lyric Video] [HD]"
        val cleaned = MetadataSanitizer.normalizeTitle(raw)
        assertEquals("UFO", cleaned)
    }

    @Test
    fun normalizeTitle_removesArtistPrefix() {
        val raw = "The Jansen - Langit Tak Seharusnya Biru"
        val cleaned = MetadataSanitizer.normalizeTitle(raw, artistName = "The Jansen")
        assertEquals("Langit Tak Seharusnya Biru", cleaned)
    }

    @Test
    fun normalizeArtist_removesTopicSuffix() {
        val raw = "The Panturas - Topic"
        val cleaned = MetadataSanitizer.normalizeArtist(raw)
        assertEquals("The Panturas", cleaned)
    }

    @Test
    fun isBlockedArtifact_detectsSystemArtifacts() {
        assertTrue(MetadataSanitizer.isBlockedArtifact("Pintasan Keyboard"))
        assertTrue(MetadataSanitizer.isBlockedArtifact("Video Bundar"))
        assertTrue(MetadataSanitizer.isBlockedArtifact("Keyboard shortcuts"))
        assertFalse(MetadataSanitizer.isBlockedArtifact("Ombak Banyu Asmara"))
    }

    @Test
    fun isOfficialStudioTrack_rejectsCoverAndLive() {
        assertFalse(MetadataSanitizer.isOfficialStudioTrack("Sunshine (Acoustic Cover)"))
        assertFalse(MetadataSanitizer.isOfficialStudioTrack("Live Concert di Bandung"))
        assertFalse(MetadataSanitizer.isOfficialStudioTrack("Tafsir Mistik (Karaoke Version)"))
        assertTrue(MetadataSanitizer.isOfficialStudioTrack("Tafsir Mistik"))
    }

    @Test
    fun isOfficialStudioTrack_allowsOfficialBandWithRestrictedWord() {
        // Band bernama "Saturday Night Karaoke" adalah band pop punk resmi dan tidak boleh diblokir
        assertTrue(MetadataSanitizer.isOfficialStudioTrack("Bam!", "Saturday Night Karaoke"))
    }

    @Test
    fun isValidTrack_acceptsValidStudioTrack() {
        val track = Track(
            id = "sp_track_123",
            title = "Tafsir Mistik",
            artist = "The Panturas",
            album = "Ombak Banyu Asmara",
            durationSeconds = 210,
            streamUrl = "",
            coverUrl = ""
        )
        assertTrue(MetadataSanitizer.isValidTrack(track))
    }

    @Test
    fun isValidTrack_rejectsCorruptedShortTrack() {
        val track = Track(
            id = "yt_corrupted",
            title = "Intro Cuplikan",
            artist = "The Panturas",
            album = "Single",
            durationSeconds = 12, // Kurang dari 30 detik untuk trek non-resmi
            streamUrl = "",
            coverUrl = ""
        )
        assertFalse(MetadataSanitizer.isValidTrack(track))
    }

    @Test
    fun sanitizeTrack_cleansAndReturnsValidTrack() {
        val rawTrack = Track(
            id = "sp_track_456",
            title = "Balada Semburan Naga (Official Audio)",
            artist = "The Panturas - Topic",
            album = "Single",
            durationSeconds = 180,
            streamUrl = "",
            coverUrl = ""
        )
        val sanitized = MetadataSanitizer.sanitizeTrack(rawTrack)
        assertNotNull(sanitized)
        assertEquals("Balada Semburan Naga", sanitized!!.title)
        assertEquals("The Panturas", sanitized.artist)
    }

    @Test
    fun sanitizeTrack_returnsNullForInvalidTrack() {
        val badTrack = Track(
            id = "yt_bad",
            title = "Pintasan Keyboard",
            artist = "Unknown",
            album = "Unknown",
            durationSeconds = 10,
            streamUrl = "",
            coverUrl = ""
        )
        val sanitized = MetadataSanitizer.sanitizeTrack(badTrack)
        assertNull(sanitized)
    }
}
