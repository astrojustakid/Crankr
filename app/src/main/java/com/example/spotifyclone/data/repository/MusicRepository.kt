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
        private const val TRENDING_CACHE_TTL_MS = 30 * 60 * 1000L // 30 menit
        private const val POPULAR_CACHE_TTL_MS = 60 * 60 * 1000L  // 1 jam

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

    /**
     * Mengambil rekomendasi lagu tren personal (Trending Songs For You).
     *
     * Alur:
     * 1. Ambil daftar musisi unik dari playlist lokal pengguna di Room.
     * 2. Jika kosong, gunakan data rilis musik alternatif/indie populer sebagai cadangan.
     * 3. Kueri rilisan terbaru (single, EP, atau album baru) dari musisi tersebut via YouTube Music channel / browse endpoint.
     * 4. Urutkan daftar rilis berdasarkan tanggal/tahun rilis terbaru agar karya anyar langsung muncul di layar Home.
     * 5. Lengkapi dengan lagu tren viral teratas untuk memastikan feed kaya dan bervariasi.
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
            // 1. Dapatkan daftar musisi unik dari playlist lokal pengguna
            val userMusicians = getUserPlaylistMusicians()
            val targetMusicians = if (userMusicians.isNotEmpty()) {
                userMusicians.take(6)
            } else {
                FALLBACK_INDIE_MUSICIANS.shuffled().take(6)
            }

            // 2. Kueri rilisan terbaru musisi tersebut secara paralel
            val releaseTracksBatches = coroutineScope {
                targetMusicians.map { musician ->
                    async {
                        try {
                            youTubeMusicRepository.getArtistNewestReleaseTracks(musician, limit = 2)
                        } catch (e: Exception) {
                            Log.w(TAG, "Gagal mengambil rilis baru $musician: ${e.message}")
                            emptyList()
                        }
                    }
                }.awaitAll()
            }

            val newestReleaseTracks = releaseTracksBatches.flatten()
                .filter { MetadataSanitizer.isOfficialStudioTrack(it) }
            val resultList = mutableListOf<Track>()
            val seenVids = mutableSetOf<String>()

            // 3. Masukkan karya anyar musisi playlist terlebih dahulu (karya rilis terbaru teratas)
            for (t in newestReleaseTracks) {
                val vid = t.youtubeVideoId ?: t.id.removePrefix("yt_")
                if (vid.length == 11 && seenVids.add(vid)) {
                    resultList.add(t)
                }
            }

            // 4. Ambil feed lagu tren global/regional resmi untuk melengkapi daftar hingga limit
            if (resultList.size < limit) {
                val generalTrending = try {
                    youTubeMusicRepository.getTrendingTracks(gl = "ID", limit = limit * 2)
                        .filter { MetadataSanitizer.isOfficialStudioTrack(it) }
                } catch (_: Exception) {
                    emptyList()
                }

                for (tr in generalTrending) {
                    val vid = tr.youtubeVideoId ?: tr.id.removePrefix("yt_")
                    if (vid.length == 11 && seenVids.add(vid)) {
                        resultList.add(tr)
                    }
                    if (resultList.size >= limit) break
                }
            }

            // 5. Jika kuota lagu berkurang setelah filter studio, ambil trek cadangan resmi studio agar tetap genap dan penuh
            if (resultList.size < limit) {
                val studioBackups = MusicCatalog.initialTracks.filter { MetadataSanitizer.isOfficialStudioTrack(it) }
                for (bk in studioBackups) {
                    val vid = bk.youtubeVideoId ?: bk.id.removePrefix("yt_")
                    if (seenVids.add(vid)) {
                        resultList.add(bk)
                    }
                    if (resultList.size >= limit) break
                }
            }

            val finalResult = resultList.take(limit)
            memoryTrendingForYouCache = finalResult
            memoryTrendingForYouTimestamp = now

            finalResult
        } catch (e: Exception) {
            Log.e(TAG, "Error getTrendingForYou: ${e.message}")
            val fallback = (memoryTrendingForYouCache ?: MusicCatalog.initialTracks)
                .filter { MetadataSanitizer.isOfficialStudioTrack(it) }
                .take(limit)
            fallback
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
