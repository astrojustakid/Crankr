package com.example.spotifyclone.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Test untuk verifikasi keandalan resolver streaming dan penolakan podcast:
 * - Penolakan podcast, episode, interview, dan talkshow dari aliran streaming lagu
 * - Pencocokan judul lagu fleksibel (feat, kolaborasi, tanda baca)
 * - Filtrasi video klip, rekaman langsung, dan bootleg
 */
class MusicStreamResolverTest {

    @Test
    fun studioBlacklistRegex_rejectsPodcastAndInterview() {
        val titles = listOf(
            "Rich Brian - C'est La Vie | The Zach Sang Show Podcast",
            "Rich Brian Interview on C'est La Vie",
            "Rich Brian Talks C'est La Vie Full Episode",
            "Podcast Episode #15: Rich Brian",
            "Wawancara Eksklusif Rich Brian",
            "The Joe Rogan Experience Podcast",
            "Discussion on C'est La Vie"
        )
        for (title in titles) {
            assertTrue("Seharusnya ditolak oleh studioBlacklistRegex: $title",
                MusicStreamResolver.studioBlacklistRegex.containsMatchIn(title))
        }
    }

    @Test
    fun isCompilationOrBootleg_rejectsPodcastMetadata() {
        assertTrue(MusicStreamResolver.isCompilationOrBootleg("C'est La Vie Podcast Episode 4"))
        assertTrue(MusicStreamResolver.isCompilationOrBootleg("C'est La Vie", "Zach Sang Podcast"))
        assertTrue(MusicStreamResolver.isCompilationOrBootleg("C'est La Vie Interview"))
        assertFalse(MusicStreamResolver.isCompilationOrBootleg("C'est La Vie", "Rich Brian"))
    }

    @Test
    fun isTitleMatching_matchesSongTitleWithCollaborations() {
        val expected = "C'est La Vie"
        assertTrue(MusicStreamResolver.isTitleMatching(expected, "C'est La Vie (feat. Rich Brian)"))
        assertTrue(MusicStreamResolver.isTitleMatching(expected, "C'est La Vie (Official Audio)"))
        assertTrue(MusicStreamResolver.isTitleMatching(expected, "C’est La Vie (feat. bbno$ & Rich Brian)"))
        assertTrue(MusicStreamResolver.isTitleMatching(expected, "C'est La Vie"))
        assertFalse(MusicStreamResolver.isTitleMatching(expected, "Edamame (feat. Rich Brian)"))
        assertFalse(MusicStreamResolver.isTitleMatching(expected, "Glow Like Dat"))
    }

    @Test
    fun audioStreamResolver_isForbiddenVideo_rejectsPodcast() {
        assertTrue(AudioStreamResolver.isForbiddenVideo("Rich Brian - C'est La Vie Podcast"))
        assertTrue(AudioStreamResolver.isForbiddenVideo("Rich Brian Interview on Music"))
        assertTrue(AudioStreamResolver.isForbiddenVideo("C'est La Vie (Official Music Video)"))
        assertTrue(AudioStreamResolver.isForbiddenVideo("C'est La Vie Live at Jakarta"))
        assertFalse(AudioStreamResolver.isForbiddenVideo("C'est La Vie", "Rich Brian"))
    }
}
