package com.example.spotifyclone.data.repository

import android.content.Context
import android.util.Log
import com.example.spotifyclone.data.MusicCatalog
import com.example.spotifyclone.data.PlaylistStorageManager
import com.example.spotifyclone.data.RecentlyPlayedManager
import com.example.spotifyclone.data.db.ListeningHistoryDao
import com.example.spotifyclone.data.db.ListeningHistoryDatabaseHelper
import com.example.spotifyclone.data.db.PopularTracksDao
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.util.MetadataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * MusicRepository:
 * Menangani rekomendasi musik cerdas (Trending Songs For You) dan tangga lagu resmi (Popular Tracks)
 * dengan mengintegrasikan YouTube Music InnerTube API (Charts / Explore / Artist Channels)
 * serta preferensi musisi dari playlist lokal Room pengguna.
 */
class MusicRepository(
    private val context: Context? = null,
    private val youTubeMusicRepository: YouTubeMusicRepository = YouTubeMusicRepository(context),
    private val listeningHistoryDao: ListeningHistoryDao? = context?.let { ListeningHistoryDatabaseHelper.getInstance(it) },
    private val popularTracksDao: PopularTracksDao? = context?.let { ListeningHistoryDatabaseHelper.getInstance(it) },
    private val recentlyPlayedManager: RecentlyPlayedManager? = context?.let { RecentlyPlayedManager(it) },
    private val playlistStorageManager: PlaylistStorageManager? = context?.let { PlaylistStorageManager(it) }
) {
    companion object {
        private const val TAG = "MusicRepository"
        private const val TRENDING_CACHE_TTL_MS = 15 * 60 * 1000L // 15 menit
        private const val POPULAR_CACHE_TTL_MS = 15 * 60 * 1000L  // 15 menit agar tangga lagu selalu segar

        @Volatile
        private var memoryTrendingForYouCache: List<Track>? = null
        @Volatile
        private var memoryTrendingForYouTimestamp: Long = 0L

        @Volatile
        private var memoryPopularTracksCache: List<Track>? = null
        @Volatile
        private var memoryPopularTracksTimestamp: Long = 0L

        // Musisi alternatif & indie populer sebagai fallback jika playlist pengguna masih kosong
        val FALLBACK_INDIE_MUSICIANS = listOf(
            "Bernadya",
            "Pee Wee Gaskins",
            "The Panturas",
            "The Jansen",
            "Dongker",
            "Morfem",
            "Silampukau",
            "Rocket Rockers",
            "Hindia",
            "Sheila On 7",
            "Blink-182",
            "Green Day"
        )
    }

    /**
     * Mengambil daftar musisi unik dari seluruh lagu di playlist lokal pengguna
     * serta riwayat dengar lokal pada database Room.
     */
    suspend fun getUserPlaylistMusicians(): List<String> = withContext(Dispatchers.IO) {
        val musicians = linkedSetOf<String>()

        try {
            // 1. Ambil musisi dari seluruh playlist lokal
            val playlists = playlistStorageManager?.loadPlaylists().orEmpty()
            for ((_, tracks) in playlists) {
                for (t in tracks) {
                    val rawArtist = t.artist.trim()
                    if (rawArtist.isNotBlank() && !rawArtist.equals("Unknown Artist", ignoreCase = true) && !rawArtist.equals("Artis", ignoreCase = true)) {
                        splitArtistNames(rawArtist).forEach { musicians.add(it) }
                    }
                }
            }

            // 2. Tambahkan artis dari riwayat putar lokal Room
            val topArtists = listeningHistoryDao?.getTopArtists(limit = 10).orEmpty()
            for (stat in topArtists) {
                val a = stat.artistName.trim()
                if (a.isNotBlank()) {
                    splitArtistNames(a).forEach { musicians.add(it) }
                }
            }

            val recentPlayed = recentlyPlayedManager?.getRecentlyPlayed().orEmpty()
            for (tr in recentPlayed) {
                val a = tr.artist.trim()
                if (a.isNotBlank()) {
                    splitArtistNames(a).forEach { musicians.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching user musicians: ${e.message}")
        }

        musicians.toList()
    }

    /**
     * Memecah string artis majemuk (misal: "Denny Caknan & Hendra Kumbara feat. Happy Asmara")
     * menjadi daftar nama musisi individual yang bersih.
     */
    private fun splitArtistNames(raw: String): List<String> {
        val parts = raw.split(Regex("(?i)\\s*(?:&|,|feat\\.|ft\\.|/|x|vs\\.)\\s*"))
        return parts.map { it.trim() }.filter { it.length >= 2 && !it.equals("Unknown", ignoreCase = true) }
    }

    private fun normalizeKey(text: String): String {
        return text
            .replace(Regex("""\s*\([^)]*\)"""), "")
            .replace(Regex("""\s*\[[^]]*]"""), "")
            .lowercase()
            .replace(Regex("""[^a-z0-9]"""), "")
            .trim()
    }

    /**
     * Mengambil lagu-lagu dari band/musisi yang ADA di playlist pengguna,
     * namun lagunya BELUM masuk ke dalam playlist mana pun (Tren Underground / Deep Cuts).
     */
    suspend fun getTrendingForYou(limit: Int = 20, forceRefresh: Boolean = false): List<Track> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        if (!forceRefresh) {
            val memCache = memoryTrendingForYouCache
            if (memCache != null && (now - memoryTrendingForYouTimestamp) < TRENDING_CACHE_TTL_MS && memCache.isNotEmpty()) {
                return@withContext memCache
            }
        }

        try {
            // 1. Kumpulkan seluruh lagu & band yang sudah ada di semua playlist pengguna
            val playlists = playlistStorageManager?.loadPlaylists().orEmpty()
            val existingIds = mutableSetOf<String>()
            val existingVids = mutableSetOf<String>()
            val existingTitleArtistKeys = mutableSetOf<String>()
            val playlistBands = linkedSetOf<String>()

            for ((_, tracks) in playlists) {
                for (t in tracks) {
                    existingIds.add(t.id)
                    val vid = t.youtubeVideoId ?: t.id.removePrefix("yt_")
                    if (vid.isNotBlank()) existingVids.add(vid)
                    val titleKey = normalizeKey(t.title)
                    val artistKey = normalizeKey(t.artist)
                    if (titleKey.isNotBlank() && artistKey.isNotBlank()) {
                        existingTitleArtistKeys.add("$titleKey|$artistKey")
                    }
                    val rawArtist = t.artist.trim()
                    if (rawArtist.isNotBlank() &&
                        !rawArtist.equals("Unknown Artist", ignoreCase = true) &&
                        !rawArtist.equals("Artis", ignoreCase = true)
                    ) {
                        splitArtistNames(rawArtist).forEach { playlistBands.add(it) }
                    }
                }
            }

            // Jika playlist masih kosong, pakai musisi riwayat dengar atau fallback indie
            val candidateBands = when {
                playlistBands.isNotEmpty() -> playlistBands.toList().shuffled()
                else -> {
                    val historyBands = getUserPlaylistMusicians()
                    if (historyBands.isNotEmpty()) historyBands.shuffled() else FALLBACK_INDIE_MUSICIANS.shuffled()
                }
            }

            val targetBands = candidateBands.take(8)

            // 2. Cari lagu lain dari masing-masing band playlist secara paralel via YouTube Music
            val perBandTracks = coroutineScope {
                targetBands.map { band ->
                    async {
                        val bandKey = normalizeKey(band)
                        try {
                            val searched = youTubeMusicRepository.searchSongs(band, limit = 10)
                            searched.filter { track ->
                                // Pastikan benar-benar lagu milik band tersebut
                                val trArtistKey = normalizeKey(track.artist)
                                val artistMatches = bandKey.isNotBlank() &&
                                    (trArtistKey.contains(bandKey) || bandKey.contains(trArtistKey))
                                if (!artistMatches) return@filter false
                                if (!MetadataSanitizer.isOfficialStudioTrack(track)) return@filter false

                                // Saring: WAJIB belum ada di playlist pengguna mana pun
                                val vid = track.youtubeVideoId ?: track.id.removePrefix("yt_")
                                val titleArtistKey = "${normalizeKey(track.title)}|$trArtistKey"
                                val alreadyInPlaylist = existingIds.contains(track.id) ||
                                    (vid.isNotBlank() && existingVids.contains(vid)) ||
                                    existingTitleArtistKeys.contains(titleArtistKey)

                                !alreadyInPlaylist
                            }.take(4)
                        } catch (e: Exception) {
                            Log.w(TAG, "Gagal mencari lagu belum masuk playlist untuk $band: ${e.message}")
                            emptyList()
                        }
                    }
                }.awaitAll()
            }

            // 3. Gabungkan secara merata (round-robin antar band) agar tidak didominasi 1 band saja
            val resultList = mutableListOf<Track>()
            val seenVids = mutableSetOf<String>()
            val seenTitleKeys = mutableSetOf<String>()

            val maxPerBand = perBandTracks.maxOfOrNull { it.size } ?: 0
            for (round in 0 until maxPerBand) {
                for (batch in perBandTracks) {
                    if (round < batch.size) {
                        val t = batch[round]
                        val vid = t.youtubeVideoId ?: t.id.removePrefix("yt_")
                        val tKey = "${normalizeKey(t.title)}|${normalizeKey(t.artist)}"
                        if (vid.length == 11 && seenVids.add(vid) && seenTitleKeys.add(tKey)) {
                            resultList.add(t)
                        }
                        if (resultList.size >= limit) break
                    }
                }
                if (resultList.size >= limit) break
            }

            val finalResult = resultList.take(limit)
            if (finalResult.isNotEmpty()) {
                memoryTrendingForYouCache = finalResult
                memoryTrendingForYouTimestamp = now
            }

            finalResult
        } catch (e: Exception) {
            Log.e(TAG, "Error getTrendingForYou: ${e.message}")
            memoryTrendingForYouCache.orEmpty().take(limit)
        }
    }

    /**
     * Mengambil daftar lagu populer resmi dari endpoint tangga lagu resmi YouTube Music Charts
     * (Top 100 Indonesia / Trending Charts).
     *
     * Setiap lagu membawa metadata lengkap dan valid:
     * - videoId (youtubeVideoId)
     * - judul trek (title)
     * - musisi (artist)
     * - gambar sampul (coverUrl)
     * - durasi detik (durationSeconds)
     *
     * Tanpa data kosong, langsung siap diputar di ExoPlayer tanpa jeda pencarian berulang.
     * Menerapkan filter ketat anti cover dan anti live serta kuota genap via trek cadangan studio resmi.
     */
    suspend fun getPopularTracks(forceRefresh: Boolean = false, limit: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Cek in-memory cache jika tidak dipaksa refresh
        if (!forceRefresh) {
            val memCache = memoryPopularTracksCache
            if (memCache != null && (now - memoryPopularTracksTimestamp) < POPULAR_CACHE_TTL_MS && memCache.isNotEmpty()) {
                return@withContext memCache
            }

            // Cek cache lokal Room (tabel popular_tracks_cache)
            val isCacheValid = popularTracksDao?.isCacheValid(POPULAR_CACHE_TTL_MS) ?: false
            if (isCacheValid && popularTracksDao != null) {
                val cached = popularTracksDao.getCachedPopularTracks()
                val filteredCached = cached.map { it.toTrack() }.filter { MetadataSanitizer.isOfficialStudioTrack(it) }
                if (filteredCached.isNotEmpty()) {
                    memoryPopularTracksCache = filteredCached
                    memoryPopularTracksTimestamp = now
                    return@withContext filteredCached
                }
            }
        }

        try {
            // 2. Ambil data tangga lagu resmi YouTube Music Charts (Top 100 Indonesia)
            val chartTracks = youTubeMusicRepository.getChartTracks(gl = "ID", limit = limit * 2)
                .filter { MetadataSanitizer.isOfficialStudioTrack(it) }

            val validChartTracks = mutableListOf<Track>()
            val seenVids = mutableSetOf<String>()
            for (tr in chartTracks) {
                val vid = tr.youtubeVideoId ?: tr.id.removePrefix("yt_")
                if (seenVids.add(vid)) {
                    validChartTracks.add(tr)
                }
                if (validChartTracks.size >= limit) break
            }

            // Jika daftar lagu berkurang setelah filter anti cover / live, ambil trek cadangan resmi studio agar kuota tetap penuh
            if (validChartTracks.size < limit) {
                val extraBackup = MusicCatalog.initialTracks.filter { MetadataSanitizer.isOfficialStudioTrack(it) }
                for (bk in extraBackup) {
                    val vid = bk.youtubeVideoId ?: bk.id.removePrefix("yt_")
                    if (seenVids.add(vid)) {
                        validChartTracks.add(bk)
                    }
                    if (validChartTracks.size >= limit) break
                }
            }

            if (validChartTracks.isNotEmpty()) {
                // Simpan ke Room cache dan in-memory cache
                popularTracksDao?.savePopularTracks(validChartTracks, now)
                memoryPopularTracksCache = validChartTracks
                memoryPopularTracksTimestamp = now
                return@withContext validChartTracks
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mengambil data tangga lagu YouTube Music Charts: ${e.message}")
        }

        // 3. Fallback jika koneksi gagal: gunakan cache Room yang tersimpan sebelumnya
        val staleCache = popularTracksDao?.getCachedPopularTracks().orEmpty()
            .map { it.toTrack() }
            .filter { MetadataSanitizer.isOfficialStudioTrack(it) }
        if (staleCache.isNotEmpty()) {
            memoryPopularTracksCache = staleCache
            return@withContext staleCache
        }

        // 4. Fallback final: katalog awal bawaan studio resmi
        val fallback = MusicCatalog.initialTracks.filter { MetadataSanitizer.isOfficialStudioTrack(it) }.take(limit)
        memoryPopularTracksCache = fallback
        fallback
    }
}
