package com.example.spotifyclone.data.repository

import com.example.spotifyclone.data.model.SpotifyPlaylist
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.MusicStreamResolver
import com.example.spotifyclone.service.spotify.SpotifyApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.example.spotifyclone.util.MetadataSanitizer

/**
 * Repository responsible for generating clean, verified track recommendations.
 * Mandate:
 * - Only official Spotify catalog tracks.
 * - Absolutely zero unverified loose text queries.
 * - Strict rejection of covers, compilations, bootlegs, tributes, slowed/reverb, and karaoke.
 */
class RecommendationRepository(
    private val spotifyApiService: SpotifyApiService = SpotifyApiService(),
    private val artistRepository: ArtistRepository = ArtistRepository()
) {

    /**
     * Validates whether [track] is an authentic studio track and does not match any blacklist criteria.
     */
    fun isOfficialTrackValid(track: Track): Boolean {
        return MetadataSanitizer.isValidTrack(track)
    }

    /**
     * Generates verified song recommendations for a playlist using official Spotify Recommendation endpoint (/v1/recommendations)
     * and artist seeds, strictly avoiding free-text YouTube search queries.
     */
    suspend fun getRecommendationsForPlaylist(
        playlist: SpotifyPlaylist,
        tracks: List<Track>,
        dominantGenre: String,
        limit: Int = 10
    ): List<Track> = withContext(Dispatchers.IO) {
        val existingIds = tracks.map { it.id }.toSet()
        val existingTitles = tracks.map { "${it.title.lowercase().trim()} - ${it.artist.lowercase().trim()}" }.toSet()
        val candidateList = mutableListOf<Track>()

        // 1. Resolve seed artists from playlist (prioritizing valid direct Spotify Artist IDs)
        val directSeedIds = tracks.mapNotNull { it.spotifyArtistId?.trim()?.ifBlank { null } }
            .filter { artistRepository.isValidSpotifyArtistId(it) }
            .distinct().shuffled().take(2)
        val seedArtistIds = mutableListOf<String>()
        seedArtistIds.addAll(directSeedIds)

        if (seedArtistIds.size < 2) {
            val sampleArtistNames = tracks.map { it.artist.trim() }.filter { it.isNotBlank() }.distinct().shuffled().take(2)
            for (artistName in sampleArtistNames) {
                val id = artistRepository.resolveSpotifyArtistId(artistName)
                if (!id.isNullOrBlank() && !seedArtistIds.contains(id)) {
                    seedArtistIds.add(id)
                }
                if (seedArtistIds.size >= 2) break
            }
        }

        // Map dominant genre to Spotify seed genre
        val seedGenre = mapGenreToSeed(dominantGenre)
        val seedGenres = if (seedGenre != null) listOf(seedGenre) else emptyList()

        // 2. Fetch using official /v1/recommendations endpoint
        if (seedArtistIds.isNotEmpty() || seedGenres.isNotEmpty()) {
            val spotifyRecs = spotifyApiService.fetchSpotifyRecommendations(
                seedArtists = seedArtistIds,
                seedGenres = seedGenres,
                limit = limit * 2
            )
            for (rec in spotifyRecs) {
                if (isOfficialTrackValid(rec) &&
                    !existingIds.contains(rec.id) &&
                    candidateList.none { it.id == rec.id || (it.title.equals(rec.title, true) && it.artist.equals(rec.artist, true)) } &&
                    !existingTitles.contains("${rec.title.lowercase().trim()} - ${rec.artist.lowercase().trim()}")
                ) {
                    candidateList.add(rec.copy(genre = dominantGenre))
                }
                if (candidateList.size >= limit) break
            }
        }

        // 3. Fallback: Fetch from official top-tracks in parallel (eliminating waterfall requests)
        if (candidateList.size < limit && seedArtistIds.isNotEmpty()) {
            val topTracksBatches = coroutineScope {
                seedArtistIds.map { artistId ->
                    async { spotifyApiService.fetchArtistTopTracks(artistId) }
                }.awaitAll()
            }
            for (topTracks in topTracksBatches) {
                for (t in topTracks) {
                    if (isOfficialTrackValid(t) &&
                        !existingIds.contains(t.id) &&
                        candidateList.none { it.id == t.id || (it.title.equals(t.title, true) && it.artist.equals(t.artist, true)) } &&
                        !existingTitles.contains("${t.title.lowercase().trim()} - ${t.artist.lowercase().trim()}")
                    ) {
                        candidateList.add(t.copy(genre = dominantGenre))
                    }
                    if (candidateList.size >= limit) break
                }
                if (candidateList.size >= limit) break
            }
        }

        // 4. Fallback: Use official Spotify Genre recommendations
        if (candidateList.size < limit) {
            val genreRecs = spotifyApiService.fetchGenreRecommendations(dominantGenre, limit = limit * 2)
            for (gRec in genreRecs) {
                if (isOfficialTrackValid(gRec) &&
                    !existingIds.contains(gRec.id) &&
                    candidateList.none { it.id == gRec.id || (it.title.equals(gRec.title, true) && it.artist.equals(gRec.artist, true)) } &&
                    !existingTitles.contains("${gRec.title.lowercase().trim()} - ${gRec.artist.lowercase().trim()}")
                ) {
                    candidateList.add(gRec.copy(genre = dominantGenre))
                }
                if (candidateList.size >= limit) break
            }
        }

        MetadataSanitizer.sanitizeTracks(candidateList).take(limit)
    }

    /**
     * Fetches official genre recommendations validated against Spotify catalog.
     */
    suspend fun getRecommendationsByGenre(genre: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val tracks = spotifyApiService.fetchGenreRecommendations(genre, limit)
        tracks.filter { isOfficialTrackValid(it) }
    }

    /**
     * Fetches official artist recommendations or top tracks from Spotify catalog.
     */
    suspend fun getOfficialArtistTracks(artistName: String, limit: Int = 10): List<Track> = withContext(Dispatchers.IO) {
        val clean = artistName.trim()
        val artistId = artistRepository.resolveSpotifyArtistId(clean)
        val tracks = if (!artistId.isNullOrBlank()) {
            spotifyApiService.fetchArtistTopTracks(artistId)
        } else {
            spotifyApiService.searchOfficialSpotifyTracks(clean, limit = limit)
        }
        tracks.filter { isOfficialTrackValid(it) && it.artist.contains(clean, ignoreCase = true) }
    }

    private fun mapGenreToSeed(genre: String): String? {
        val lower = genre.lowercase().trim()
        return when {
            lower.contains("pop punk") -> "punk"
            lower.contains("metalcore") || lower.contains("metal") -> "metal"
            lower.contains("indie rock") || lower.contains("indie") -> "indie"
            lower.contains("lo-fi") || lower.contains("chill") -> "chill"
            lower.contains("anime") || lower.contains("j-rock") -> "j-rock"
            lower.contains("hip-hop") || lower.contains("hip hop") -> "hip-hop"
            lower.contains("skate punk") || lower.contains("grunge") -> "grunge"
            lower.contains("punk") -> "punk"
            lower.contains("rock") -> "rock"
            lower.contains("pop") -> "pop"
            else -> null
        }
    }
}
