package com.example.spotifyclone.util

import com.example.spotifyclone.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Test untuk verifikasi logika ArtistParser:
 * - Parsing musisi kolaborasi (feat, ft, with, x, koma, slash)
 * - Pencocokan ketat trek terhadap musisi utama/kolaborator
 */
class ArtistParserTest {

    @Test
    fun parseArtists_singleArtist_returnsSingleItem() {
        val result = ArtistParser.parseArtists("The Panturas")
        assertEquals(listOf("The Panturas"), result)
    }

    @Test
    fun parseArtists_multipleSeparators_returnsAllArtists() {
        val input = "The Panturas feat. Adipati & Dongker"
        val result = ArtistParser.parseArtists(input)
        assertEquals(listOf("The Panturas", "Adipati", "Dongker"), result)
    }

    @Test
    fun parseArtists_commaAndAmpersand_splitsCorrectly() {
        val input = "The Kick, The Jansen & Teenage Death Star"
        val result = ArtistParser.parseArtists(input)
        assertEquals(listOf("The Kick", "The Jansen", "Teenage Death Star"), result)
    }

    @Test
    fun parseArtists_slashSeparated_splitsCorrectly() {
        val input = "Grrrl Gang / Swellow"
        val result = ArtistParser.parseArtists(input)
        assertEquals(listOf("Grrrl Gang", "Swellow"), result)
    }

    @Test
    fun parseArtists_withQuotationMarks_cleansQuotes() {
        val input = "The Panturas feat. 'The Kuda'"
        val result = ArtistParser.parseArtists(input)
        assertEquals(listOf("The Panturas", "The Kuda"), result)
    }

    @Test
    fun parseArtists_emptyOrBlank_returnsEmptyList() {
        assertTrue(ArtistParser.parseArtists("").isEmpty())
        assertTrue(ArtistParser.parseArtists("   ").isEmpty())
    }

    @Test
    fun isTrackStrictlyByArtist_exactArtistMatch_returnsTrue() {
        val track = Track(
            id = "t1",
            title = "Tafsir Mistik",
            artist = "The Panturas",
            album = "Ombak Banyu Asmara",
            durationSeconds = 210,
            streamUrl = "",
            coverUrl = ""
        )
        assertTrue(ArtistParser.isTrackStrictlyByArtist(track, "The Panturas"))
        assertTrue(ArtistParser.isTrackStrictlyByArtist(track, "the panturas")) // Case insensitive
    }

    @Test
    fun isTrackStrictlyByArtist_collaboratorInArtist_returnsTrue() {
        val track = Track(
            id = "t2",
            title = "Balada Semburan Naga",
            artist = "The Panturas feat. Adipati",
            album = "Single",
            durationSeconds = 180,
            streamUrl = "",
            coverUrl = ""
        )
        assertTrue(ArtistParser.isTrackStrictlyByArtist(track, "The Panturas"))
        assertTrue(ArtistParser.isTrackStrictlyByArtist(track, "Adipati"))
    }

    @Test
    fun isTrackStrictlyByArtist_featuredInTitle_returnsTrue() {
        val track = Track(
            id = "t3",
            title = "Jimat (feat. Morfem)",
            artist = "The Panturas",
            album = "Single",
            durationSeconds = 200,
            streamUrl = "",
            coverUrl = ""
        )
        assertTrue(ArtistParser.isTrackStrictlyByArtist(track, "The Panturas"))
        assertTrue(ArtistParser.isTrackStrictlyByArtist(track, "Morfem"))
    }

    @Test
    fun isTrackStrictlyByArtist_differentArtist_returnsFalse() {
        val track = Track(
            id = "t4",
            title = "UFO",
            artist = "The Kick",
            album = "Single",
            durationSeconds = 190,
            streamUrl = "",
            coverUrl = ""
        )
        assertFalse(ArtistParser.isTrackStrictlyByArtist(track, "The Jansen"))
        assertFalse(ArtistParser.isTrackStrictlyByArtist(track, "The Panturas"))
    }
}
