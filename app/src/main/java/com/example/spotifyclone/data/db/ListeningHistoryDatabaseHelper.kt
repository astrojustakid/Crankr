package com.example.spotifyclone.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.spotifyclone.data.ListeningStatsManager
import com.example.spotifyclone.data.RecentlyPlayedManager
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.util.MetadataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementasi Database Helper berbasis Android SQLite native untuk riwayat mendengar Crankr
 * dan cache katalog lagu populer resmi Spotify (TTL 6-12 jam).
 * 100% thread-safe di Dispatchers.IO, bebas konflik plugin/compiler.
 */
class ListeningHistoryDatabaseHelper private constructor(
    private val context: Context
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION),
    ListeningHistoryDao,
    PopularTracksDao {

    companion object {
        private const val DATABASE_NAME = "crankr_listening_history.db"
        private const val DATABASE_VERSION = 4

        const val TABLE_HISTORY = "listening_history"
        const val COLUMN_ID = "id"
        const val COLUMN_TRACK_ID = "trackId"
        const val COLUMN_TRACK_TITLE = "trackTitle"
        const val COLUMN_ARTIST_ID = "artistId"
        const val COLUMN_ARTIST_NAME = "artistName"
        const val COLUMN_ALBUM_ART_URL = "albumArtUrl"
        const val COLUMN_DURATION_PLAYED_MS = "durationPlayedMs"
        const val COLUMN_TIMESTAMP = "timestamp"

        // Tabel Cache Lagu Populer / Trending
        const val TABLE_POPULAR_CACHE = "popular_tracks_cache"
        const val COLUMN_CACHE_TRACK_ID = "track_id"
        const val COLUMN_CACHE_TITLE = "title"
        const val COLUMN_CACHE_ARTIST = "artist"
        const val COLUMN_CACHE_ALBUM = "album"
        const val COLUMN_CACHE_DURATION = "duration_seconds"
        const val COLUMN_CACHE_STREAM_URL = "stream_url"
        const val COLUMN_CACHE_COVER_URL = "cover_url"
        const val COLUMN_CACHE_GENRE = "genre"
        const val COLUMN_CACHE_CACHED_AT = "cached_at"
        const val COLUMN_CACHE_POSITION = "position"

        // Tabel Cache Playlist Kategori Genre & Mood
        const val TABLE_CATEGORY_CACHE = "category_tracks_cache"
        const val COLUMN_CAT_ID = "category_id"
        const val COLUMN_CAT_TRACK_ID = "track_id"
        const val COLUMN_CAT_TITLE = "title"
        const val COLUMN_CAT_ARTIST = "artist"
        const val COLUMN_CAT_ALBUM = "album"
        const val COLUMN_CAT_DURATION = "duration_seconds"
        const val COLUMN_CAT_STREAM_URL = "stream_url"
        const val COLUMN_CAT_COVER_URL = "cover_url"
        const val COLUMN_CAT_GENRE = "genre"
        const val COLUMN_CAT_CACHED_AT = "cached_at"
        const val COLUMN_CAT_POSITION = "position"

        // Tabel Unduhan Offline Lokal Room / SQLite
        const val TABLE_DOWNLOADS = "offline_downloads"
        const val COLUMN_DL_TRACK_ID = "track_id"
        const val COLUMN_DL_TITLE = "title"
        const val COLUMN_DL_ARTIST = "artist"
        const val COLUMN_DL_ALBUM = "album"
        const val COLUMN_DL_DURATION = "duration_seconds"
        const val COLUMN_DL_STREAM_URL = "stream_url"
        const val COLUMN_DL_COVER_URL = "cover_url"
        const val COLUMN_DL_GENRE = "genre"
        const val COLUMN_DL_LOCAL_PATH = "local_file_path"
        const val COLUMN_DL_YOUTUBE_ID = "youtube_video_id"
        const val COLUMN_DL_STATUS = "status" // DOWNLOADING, COMPLETED, FAILED
        const val COLUMN_DL_PROGRESS = "progress"
        const val COLUMN_DL_UPDATED_AT = "updated_at"

        @Volatile
        private var instance: ListeningHistoryDatabaseHelper? = null

        fun getInstance(context: Context): ListeningHistoryDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: ListeningHistoryDatabaseHelper(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val createPopularCacheSql = """
        CREATE TABLE IF NOT EXISTS $TABLE_POPULAR_CACHE (
            $COLUMN_CACHE_TRACK_ID TEXT PRIMARY KEY,
            $COLUMN_CACHE_TITLE TEXT NOT NULL,
            $COLUMN_CACHE_ARTIST TEXT NOT NULL,
            $COLUMN_CACHE_ALBUM TEXT NOT NULL,
            $COLUMN_CACHE_DURATION INTEGER NOT NULL,
            $COLUMN_CACHE_STREAM_URL TEXT NOT NULL,
            $COLUMN_CACHE_COVER_URL TEXT NOT NULL,
            $COLUMN_CACHE_GENRE TEXT NOT NULL,
            $COLUMN_CACHE_CACHED_AT INTEGER NOT NULL,
            $COLUMN_CACHE_POSITION INTEGER NOT NULL
        );
    """.trimIndent()

    private val createCategoryCacheSql = """
        CREATE TABLE IF NOT EXISTS $TABLE_CATEGORY_CACHE (
            $COLUMN_CAT_ID TEXT NOT NULL,
            $COLUMN_CAT_TRACK_ID TEXT NOT NULL,
            $COLUMN_CAT_TITLE TEXT NOT NULL,
            $COLUMN_CAT_ARTIST TEXT NOT NULL,
            $COLUMN_CAT_ALBUM TEXT NOT NULL,
            $COLUMN_CAT_DURATION INTEGER NOT NULL,
            $COLUMN_CAT_STREAM_URL TEXT NOT NULL,
            $COLUMN_CAT_COVER_URL TEXT NOT NULL,
            $COLUMN_CAT_GENRE TEXT NOT NULL,
            $COLUMN_CAT_CACHED_AT INTEGER NOT NULL,
            $COLUMN_CAT_POSITION INTEGER NOT NULL,
            PRIMARY KEY ($COLUMN_CAT_ID, $COLUMN_CAT_TRACK_ID)
        );
    """.trimIndent()

    private val createDownloadsSql = """
        CREATE TABLE IF NOT EXISTS $TABLE_DOWNLOADS (
            $COLUMN_DL_TRACK_ID TEXT PRIMARY KEY,
            $COLUMN_DL_TITLE TEXT NOT NULL,
            $COLUMN_DL_ARTIST TEXT NOT NULL,
            $COLUMN_DL_ALBUM TEXT NOT NULL,
            $COLUMN_DL_DURATION INTEGER NOT NULL,
            $COLUMN_DL_STREAM_URL TEXT NOT NULL,
            $COLUMN_DL_COVER_URL TEXT NOT NULL,
            $COLUMN_DL_GENRE TEXT NOT NULL,
            $COLUMN_DL_LOCAL_PATH TEXT,
            $COLUMN_DL_YOUTUBE_ID TEXT,
            $COLUMN_DL_STATUS TEXT NOT NULL,
            $COLUMN_DL_PROGRESS REAL NOT NULL,
            $COLUMN_DL_UPDATED_AT INTEGER NOT NULL
        );
    """.trimIndent()

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_HISTORY (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TRACK_ID TEXT NOT NULL,
                $COLUMN_TRACK_TITLE TEXT NOT NULL,
                $COLUMN_ARTIST_ID TEXT NOT NULL,
                $COLUMN_ARTIST_NAME TEXT NOT NULL,
                $COLUMN_ALBUM_ART_URL TEXT NOT NULL,
                $COLUMN_DURATION_PLAYED_MS INTEGER NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL
            );
        """.trimIndent()
        db.execSQL(createTableSql)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_track ON $TABLE_HISTORY($COLUMN_TRACK_ID);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_artist ON $TABLE_HISTORY($COLUMN_ARTIST_NAME);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_time ON $TABLE_HISTORY($COLUMN_TIMESTAMP);")

        db.execSQL(createPopularCacheSql)
        db.execSQL(createCategoryCacheSql)
        db.execSQL(createDownloadsSql)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            ensurePopularCacheTable(db)
            ensureCategoryCacheTable(db)
            ensureDownloadsTable(db)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_track ON $TABLE_HISTORY($COLUMN_TRACK_ID);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_artist ON $TABLE_HISTORY($COLUMN_ARTIST_NAME);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_time ON $TABLE_HISTORY($COLUMN_TIMESTAMP);")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Fallback to destructive migration: mencegah crash jika versi database diturunkan atau tidak kompatibel
        try {
            dropAllTables(db)
            onCreate(db)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try {
            if (oldVersion < 2) {
                ensurePopularCacheTable(db)
            }
            if (oldVersion < 3) {
                ensureCategoryCacheTable(db)
            }
            if (oldVersion < 4) {
                ensurePopularCacheTable(db)
                ensureCategoryCacheTable(db)
                ensureDownloadsTable(db)
            }
            ensurePopularCacheTable(db)
            ensureCategoryCacheTable(db)
            ensureDownloadsTable(db)
        } catch (e: Exception) {
            try {
                dropAllTables(db)
                onCreate(db)
            } catch (_: Exception) {}
        }
    }

    fun fallbackToDestructiveMigration(): ListeningHistoryDatabaseHelper {
        try {
            context.applicationContext.deleteDatabase(DATABASE_NAME)
        } catch (_: Exception) {}
        return this
    }

    private fun dropAllTables(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_POPULAR_CACHE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CATEGORY_CACHE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DOWNLOADS")
    }

    fun getSafeWritableDatabase(): SQLiteDatabase {
        return try {
            writableDatabase
        } catch (e: Exception) {
            try {
                context.applicationContext.deleteDatabase(DATABASE_NAME)
            } catch (_: Exception) {}
            writableDatabase
        }
    }

    fun getSafeReadableDatabase(): SQLiteDatabase {
        return try {
            readableDatabase
        } catch (e: Exception) {
            try {
                context.applicationContext.deleteDatabase(DATABASE_NAME)
            } catch (_: Exception) {}
            readableDatabase
        }
    }

    private fun Cursor.getStringSafe(columnName: String, default: String = ""): String {
        val idx = getColumnIndex(columnName)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else default
    }

    private fun Cursor.getIntSafe(columnName: String, default: Int = 0): Int {
        val idx = getColumnIndex(columnName)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else default
    }

    private fun Cursor.getLongSafe(columnName: String, default: Long = 0L): Long {
        val idx = getColumnIndex(columnName)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else default
    }

    private fun ensurePopularCacheTable(db: SQLiteDatabase) {
        db.execSQL(createPopularCacheSql)
    }

    fun ensureCategoryCacheTable(db: SQLiteDatabase) {
        db.execSQL(createCategoryCacheSql)
    }

    fun ensureDownloadsTable(db: SQLiteDatabase) {
        db.execSQL(createDownloadsSql)
    }

    override suspend fun insert(entity: ListeningHistoryEntity): Long = withContext(Dispatchers.IO) {
        val db = writableDatabase
        // Prevent duplicate insertion if the same track was already recorded in the last 45 seconds
        val recentQuery = "SELECT $COLUMN_ID FROM $TABLE_HISTORY WHERE $COLUMN_TRACK_ID = ? AND $COLUMN_TIMESTAMP >= ?"
        db.rawQuery(recentQuery, arrayOf(entity.trackId, (entity.timestamp - 45_000L).toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext cursor.getLong(0)
            }
        }

        val values = ContentValues().apply {
            put(COLUMN_TRACK_ID, entity.trackId)
            put(COLUMN_TRACK_TITLE, entity.trackTitle)
            put(COLUMN_ARTIST_ID, entity.artistId)
            put(COLUMN_ARTIST_NAME, entity.artistName.ifBlank { "Unknown Artist" })
            put(COLUMN_ALBUM_ART_URL, entity.albumArtUrl)
            put(COLUMN_DURATION_PLAYED_MS, entity.durationPlayedMs)
            put(COLUMN_TIMESTAMP, entity.timestamp)
        }
        db.insert(TABLE_HISTORY, null, values)
    }

    override suspend fun getTotalMinutesPlayed(fromTimestamp: Long): Long = withContext(Dispatchers.IO) {
        seedFromExistingStatsIfEmpty()
        val db = readableDatabase
        val query = "SELECT COALESCE(SUM($COLUMN_DURATION_PLAYED_MS), 0) FROM $TABLE_HISTORY WHERE $COLUMN_TIMESTAMP >= ?"
        var totalMs = 0L
        db.rawQuery(query, arrayOf(fromTimestamp.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                totalMs = cursor.getLong(0)
            }
        }
        totalMs / 60000L
    }

    override suspend fun getTopArtists(limit: Int, fromTimestamp: Long): List<TopArtistStat> = withContext(Dispatchers.IO) {
        seedFromExistingStatsIfEmpty()
        val db = readableDatabase
        val query = """
            SELECT 
                $COLUMN_ARTIST_NAME, 
                $COLUMN_ARTIST_ID, 
                $COLUMN_ALBUM_ART_URL, 
                COUNT(*) as play_count, 
                SUM($COLUMN_DURATION_PLAYED_MS) as total_duration
            FROM $TABLE_HISTORY
            WHERE $COLUMN_TIMESTAMP >= ? AND $COLUMN_ARTIST_NAME != ''
            GROUP BY $COLUMN_ARTIST_NAME
            ORDER BY play_count DESC, total_duration DESC
            LIMIT ?
        """.trimIndent()

        val list = mutableListOf<TopArtistStat>()
        db.rawQuery(query, arrayOf(fromTimestamp.toString(), limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    TopArtistStat(
                        artistName = cursor.getString(0) ?: "Unknown Artist",
                        artistId = cursor.getString(1) ?: "",
                        albumArtUrl = cursor.getString(2) ?: "",
                        playCount = cursor.getInt(3),
                        totalDurationMs = cursor.getLong(4)
                    )
                )
            }
        }
        list
    }

    override suspend fun getTopTracks(limit: Int, fromTimestamp: Long): List<TopTrackStat> = withContext(Dispatchers.IO) {
        seedFromExistingStatsIfEmpty()
        val db = readableDatabase
        val query = """
            SELECT 
                $COLUMN_TRACK_ID, 
                $COLUMN_TRACK_TITLE, 
                $COLUMN_ARTIST_NAME, 
                $COLUMN_ALBUM_ART_URL, 
                COUNT(*) as play_count, 
                SUM($COLUMN_DURATION_PLAYED_MS) as total_duration
            FROM $TABLE_HISTORY
            WHERE $COLUMN_TIMESTAMP >= ? AND $COLUMN_TRACK_TITLE != ''
            GROUP BY $COLUMN_TRACK_ID
            ORDER BY play_count DESC, total_duration DESC
            LIMIT ?
        """.trimIndent()

        val list = mutableListOf<TopTrackStat>()
        db.rawQuery(query, arrayOf(fromTimestamp.toString(), limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    TopTrackStat(
                        trackId = cursor.getString(0) ?: "",
                        trackTitle = cursor.getString(1) ?: "Unknown Track",
                        artistName = cursor.getString(2) ?: "Unknown Artist",
                        albumArtUrl = cursor.getString(3) ?: "",
                        playCount = cursor.getInt(4),
                        totalDurationMs = cursor.getLong(5)
                    )
                )
            }
        }
        list
    }

    override suspend fun getUniqueArtistCount(fromTimestamp: Long): Int = withContext(Dispatchers.IO) {
        seedFromExistingStatsIfEmpty()
        val db = readableDatabase
        val query = "SELECT COUNT(DISTINCT $COLUMN_ARTIST_NAME) FROM $TABLE_HISTORY WHERE $COLUMN_TIMESTAMP >= ? AND $COLUMN_ARTIST_NAME != ''"
        var count = 0
        db.rawQuery(query, arrayOf(fromTimestamp.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0)
            }
        }
        count
    }

    override suspend fun getTopTrack(fromTimestamp: Long): TopTrackStat? = withContext(Dispatchers.IO) {
        getTopTracks(limit = 1, fromTimestamp = fromTimestamp).firstOrNull()
    }

    override suspend fun getRecapData(fromTimestamp: Long): CrankrRecapData = withContext(Dispatchers.IO) {
        val totalMinutes = getTotalMinutesPlayed(fromTimestamp)
        val totalHours = (totalMinutes / 60).toInt()
        val uniqueArtists = getUniqueArtistCount(fromTimestamp)
        val topTrack = getTopTrack(fromTimestamp)
        val topArtists = getTopArtists(5, fromTimestamp)
        val topTracks = getTopTracks(5, fromTimestamp)

        // Persona zine punk rock
        val (title, badge, quote) = when {
            totalMinutes < 30 && uniqueArtists < 3 -> Triple(
                "GARAGE ROOKIE",
                "⚡ ROOKIE",
                "Baru mulai menyetel distorsi amplifier di garasi bawah tanah."
            )
            totalMinutes < 120 -> Triple(
                "CLUB STAGE DIVER",
                "🔥 STAGE DIVER",
                "Lompat bebas ke kerumunan gig underground tanpa rasa takut."
            )
            totalMinutes < 300 -> Triple(
                "MOSHPIT VETERAN",
                "💀 MOSHPIT VETERAN",
                "Tahan banting di tengah pusaran circle pit paling beringas."
            )
            totalMinutes < 600 -> Triple(
                "PUNK TECH REBEL",
                "⚡ PUNK TECH REBEL",
                "Meretas frekuensi arus utama dengan distorsi bass murni."
            )
            else -> Triple(
                "IMMORTAL ANARCHIST",
                "👑 IMMORTAL ANARCHIST",
                "Legenda punk bawah tanah sejati yang tak pernah mematikan amplifier."
            )
        }

        CrankrRecapData(
            totalMinutes = totalMinutes,
            totalHours = totalHours,
            uniqueArtistsCount = uniqueArtists.coerceAtLeast(topArtists.size),
            topTrack = topTrack,
            topArtists = topArtists,
            topTracks = topTracks,
            personaTitle = title,
            personaBadge = badge,
            personaQuote = quote
        )
    }

    override suspend fun getAllHistory(limit: Int): List<ListeningHistoryEntity> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val list = mutableListOf<ListeningHistoryEntity>()
        val query = "SELECT * FROM $TABLE_HISTORY ORDER BY $COLUMN_TIMESTAMP DESC LIMIT ?"
        db.rawQuery(query, arrayOf(limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToEntity(cursor))
            }
        }
        list
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_HISTORY, null, null)
        Unit
    }

    private fun cursorToEntity(cursor: Cursor): ListeningHistoryEntity {
        return ListeningHistoryEntity(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
            trackId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TRACK_ID)),
            trackTitle = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TRACK_TITLE)),
            artistId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ARTIST_ID)),
            artistName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ARTIST_NAME)),
            albumArtUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ALBUM_ART_URL)),
            durationPlayedMs = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DURATION_PLAYED_MS)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
        )
    }

    /**
     * Memindahkan data riwayat dari RecentlyPlayedManager jika database masih kosong,
     * sehingga user tidak mendapati statistik kosong saat pertama kali membuka Crankr Recap.
     */
    private suspend fun seedFromExistingStatsIfEmpty() {
        try {
            val db = readableDatabase
            val countQuery = "SELECT COUNT(*) FROM $TABLE_HISTORY"
            var count = 0
            db.rawQuery(countQuery, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0)
                }
            }
            if (count > 0) return

            val recentTracks = RecentlyPlayedManager(context).getRecentlyPlayed()
            if (recentTracks.isNotEmpty()) {
                val writeDb = writableDatabase
                val now = System.currentTimeMillis()
                writeDb.beginTransaction()
                try {
                    recentTracks.forEachIndexed { index, track ->
                        val values = ContentValues().apply {
                            put(COLUMN_TRACK_ID, track.id)
                            put(COLUMN_TRACK_TITLE, track.title)
                            put(COLUMN_ARTIST_ID, track.spotifyArtistId ?: "")
                            put(COLUMN_ARTIST_NAME, track.artist.ifBlank { "Unknown Artist" })
                            put(COLUMN_ALBUM_ART_URL, track.coverUrl)
                            put(COLUMN_DURATION_PLAYED_MS, 45_000L) // Minimal playtime credit
                            put(COLUMN_TIMESTAMP, now - (index * 60_000L))
                        }
                        writeDb.insert(TABLE_HISTORY, null, values)
                    }
                    writeDb.setTransactionSuccessful()
                } finally {
                    writeDb.endTransaction()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =========================================================================
    // PopularTracksDao Implementation (Room / SQLite Local Cache dengan TTL)
    // =========================================================================

    override suspend fun getCachedPopularTracks(): List<PopularTrackEntity> = withContext(Dispatchers.IO) {
        val db = getSafeReadableDatabase()
        ensurePopularCacheTable(db)
        val list = mutableListOf<PopularTrackEntity>()
        val query = "SELECT * FROM $TABLE_POPULAR_CACHE ORDER BY $COLUMN_CACHE_POSITION ASC"
        try {
            db.rawQuery(query, null).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        PopularTrackEntity(
                            trackId = cursor.getStringSafe(COLUMN_CACHE_TRACK_ID),
                            title = cursor.getStringSafe(COLUMN_CACHE_TITLE),
                            artist = cursor.getStringSafe(COLUMN_CACHE_ARTIST),
                            album = cursor.getStringSafe(COLUMN_CACHE_ALBUM),
                            durationSeconds = cursor.getIntSafe(COLUMN_CACHE_DURATION, 180),
                            streamUrl = cursor.getStringSafe(COLUMN_CACHE_STREAM_URL),
                            coverUrl = cursor.getStringSafe(COLUMN_CACHE_COVER_URL),
                            genre = cursor.getStringSafe(COLUMN_CACHE_GENRE, "Unknown"),
                            cachedAt = cursor.getLongSafe(COLUMN_CACHE_CACHED_AT),
                            position = cursor.getIntSafe(COLUMN_CACHE_POSITION)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list.filter { entity ->
            !MetadataSanitizer.isBlockedArtifact(entity.title, entity.artist) && entity.durationSeconds >= 30
        }
    }

    override suspend fun savePopularTracks(tracks: List<Track>, timestamp: Long): Unit = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext
        val db = getSafeWritableDatabase()
        ensurePopularCacheTable(db)
        db.beginTransaction()
        try {
            db.delete(TABLE_POPULAR_CACHE, null, null)
            tracks.forEachIndexed { index, track ->
                val values = ContentValues().apply {
                    put(COLUMN_CACHE_TRACK_ID, track.id)
                    put(COLUMN_CACHE_TITLE, track.title)
                    put(COLUMN_CACHE_ARTIST, track.artist)
                    put(COLUMN_CACHE_ALBUM, track.album)
                    put(COLUMN_CACHE_DURATION, track.durationSeconds)
                    put(COLUMN_CACHE_STREAM_URL, track.streamUrl)
                    put(COLUMN_CACHE_COVER_URL, track.coverUrl)
                    put(COLUMN_CACHE_GENRE, track.genre)
                    put(COLUMN_CACHE_CACHED_AT, timestamp)
                    put(COLUMN_CACHE_POSITION, index)
                }
                db.insertWithOnConflict(TABLE_POPULAR_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {}
        }
    }

    override suspend fun isCacheValid(ttlMillis: Long): Boolean = withContext(Dispatchers.IO) {
        val db = getSafeReadableDatabase()
        ensurePopularCacheTable(db)
        var latestTimestamp = 0L
        var count = 0
        try {
            val query = "SELECT MAX($COLUMN_CACHE_CACHED_AT), COUNT(*) FROM $TABLE_POPULAR_CACHE"
            db.rawQuery(query, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    latestTimestamp = cursor.getLong(0)
                    count = cursor.getInt(1)
                }
            }
        } catch (e: Exception) {
            return@withContext false
        }

        if (count == 0 || latestTimestamp <= 0L) {
            return@withContext false
        }
        val age = System.currentTimeMillis() - latestTimestamp
        age in 0..ttlMillis
    }

    override suspend fun clearCache(): Unit = withContext(Dispatchers.IO) {
        val db = getSafeWritableDatabase()
        ensurePopularCacheTable(db)
        try {
            db.delete(TABLE_POPULAR_CACHE, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Bersihkan tabel cache URL audio lokal (tabel cache populer & kategori)
     * agar trek memuat ulang tautan audio yang benar.
     */
    suspend fun clearStreamUrlCache(): Unit = withContext(Dispatchers.IO) {
        clearCache()
        val db = getSafeWritableDatabase()
        ensureCategoryCacheTable(db)
        try {
            db.delete(TABLE_CATEGORY_CACHE, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =========================================================================
    // Category & Genre Playlist Cache (TTL 12 Jam)
    // =========================================================================

    suspend fun getCachedCategoryTracks(categoryId: String): List<Track> = withContext(Dispatchers.IO) {
        val db = getSafeReadableDatabase()
        ensureCategoryCacheTable(db)
        val list = mutableListOf<Track>()
        val query = "SELECT * FROM $TABLE_CATEGORY_CACHE WHERE $COLUMN_CAT_ID = ? ORDER BY $COLUMN_CAT_POSITION ASC"
        try {
            db.rawQuery(query, arrayOf(categoryId)).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        Track(
                            id = cursor.getStringSafe(COLUMN_CAT_TRACK_ID),
                            title = cursor.getStringSafe(COLUMN_CAT_TITLE),
                            artist = cursor.getStringSafe(COLUMN_CAT_ARTIST),
                            album = cursor.getStringSafe(COLUMN_CAT_ALBUM),
                            durationSeconds = cursor.getIntSafe(COLUMN_CAT_DURATION, 180),
                            streamUrl = cursor.getStringSafe(COLUMN_CAT_STREAM_URL),
                            coverUrl = cursor.getStringSafe(COLUMN_CAT_COVER_URL),
                            genre = cursor.getStringSafe(COLUMN_CAT_GENRE, "Rock")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        MetadataSanitizer.sanitizeTracks(list)
    }

    suspend fun saveCategoryTracks(categoryId: String, tracks: List<Track>, timestamp: Long = System.currentTimeMillis()): Unit = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext
        val db = getSafeWritableDatabase()
        ensureCategoryCacheTable(db)
        db.beginTransaction()
        try {
            db.delete(TABLE_CATEGORY_CACHE, "$COLUMN_CAT_ID = ?", arrayOf(categoryId))
            tracks.forEachIndexed { index, track ->
                val values = ContentValues().apply {
                    put(COLUMN_CAT_ID, categoryId)
                    put(COLUMN_CAT_TRACK_ID, track.id)
                    put(COLUMN_CAT_TITLE, track.title)
                    put(COLUMN_CAT_ARTIST, track.artist)
                    put(COLUMN_CAT_ALBUM, track.album)
                    put(COLUMN_CAT_DURATION, track.durationSeconds)
                    put(COLUMN_CAT_STREAM_URL, track.streamUrl)
                    put(COLUMN_CAT_COVER_URL, track.coverUrl)
                    put(COLUMN_CAT_GENRE, track.genre)
                    put(COLUMN_CAT_CACHED_AT, timestamp)
                    put(COLUMN_CAT_POSITION, index)
                }
                db.insertWithOnConflict(TABLE_CATEGORY_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {}
        }
    }

    suspend fun isCategoryCacheValid(categoryId: String, ttlMillis: Long): Boolean = withContext(Dispatchers.IO) {
        val db = getSafeReadableDatabase()
        ensureCategoryCacheTable(db)
        var latestTimestamp = 0L
        var count = 0
        try {
            val query = "SELECT MAX($COLUMN_CAT_CACHED_AT), COUNT(*) FROM $TABLE_CATEGORY_CACHE WHERE $COLUMN_CAT_ID = ?"
            db.rawQuery(query, arrayOf(categoryId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    latestTimestamp = cursor.getLong(0)
                    count = cursor.getInt(1)
                }
            }
        } catch (_: Exception) {
            return@withContext false
        }

        if (count == 0 || latestTimestamp <= 0L) return@withContext false
        val age = System.currentTimeMillis() - latestTimestamp
        age in 0..ttlMillis
    }

    // ==========================================
    // Modul Operasi Unduhan Offline Room / SQLite
    // ==========================================

    /**
     * Menyimpan atau memperbarui metadata dan status unduhan lagu.
     */
    fun recordDownloadStatus(
        track: Track,
        status: String,
        progress: Float,
        localFilePath: String? = null
    ) {
        try {
            val db = getSafeWritableDatabase()
            ensureDownloadsTable(db)
            val values = ContentValues().apply {
                put(COLUMN_DL_TRACK_ID, track.id)
                put(COLUMN_DL_TITLE, track.title)
                put(COLUMN_DL_ARTIST, track.artist)
                put(COLUMN_DL_ALBUM, track.album)
                put(COLUMN_DL_DURATION, track.durationSeconds)
                put(COLUMN_DL_STREAM_URL, track.streamUrl)
                put(COLUMN_DL_COVER_URL, track.coverUrl)
                put(COLUMN_DL_GENRE, track.genre)
                put(COLUMN_DL_LOCAL_PATH, localFilePath ?: track.localFilePath)
                put(COLUMN_DL_YOUTUBE_ID, track.youtubeVideoId)
                put(COLUMN_DL_STATUS, status)
                put(COLUMN_DL_PROGRESS, progress.toDouble())
                put(COLUMN_DL_UPDATED_AT, System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_DOWNLOADS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            android.util.Log.w("ListeningHistoryDB", "Error recordDownloadStatus: ${e.message}")
        }
    }

    /**
     * Memperbarui persentase progress unduhan secara periodik.
     */
    fun updateDownloadProgress(trackId: String, progress: Float, status: String = "DOWNLOADING") {
        try {
            val db = getSafeWritableDatabase()
            ensureDownloadsTable(db)
            val values = ContentValues().apply {
                put(COLUMN_DL_PROGRESS, progress.toDouble())
                put(COLUMN_DL_STATUS, status)
                put(COLUMN_DL_UPDATED_AT, System.currentTimeMillis())
            }
            db.update(TABLE_DOWNLOADS, values, "$COLUMN_DL_TRACK_ID = ?", arrayOf(trackId))
        } catch (e: Exception) {
            android.util.Log.w("ListeningHistoryDB", "Error updateDownloadProgress: ${e.message}")
        }
    }

    /**
     * Menandai unduhan lagu telah selesai dan menyimpan path lokal permanen.
     */
    fun markDownloadCompleted(trackId: String, localFilePath: String) {
        try {
            val db = getSafeWritableDatabase()
            ensureDownloadsTable(db)
            val values = ContentValues().apply {
                put(COLUMN_DL_LOCAL_PATH, localFilePath)
                put(COLUMN_DL_STATUS, "COMPLETED")
                put(COLUMN_DL_PROGRESS, 1.0)
                put(COLUMN_DL_UPDATED_AT, System.currentTimeMillis())
            }
            db.update(TABLE_DOWNLOADS, values, "$COLUMN_DL_TRACK_ID = ?", arrayOf(trackId))
        } catch (e: Exception) {
            android.util.Log.w("ListeningHistoryDB", "Error markDownloadCompleted: ${e.message}")
        }
    }

    /**
     * Menandai unduhan lagu gagal.
     */
    fun markDownloadFailed(trackId: String) {
        try {
            val db = getSafeWritableDatabase()
            ensureDownloadsTable(db)
            val values = ContentValues().apply {
                put(COLUMN_DL_STATUS, "FAILED")
                put(COLUMN_DL_UPDATED_AT, System.currentTimeMillis())
            }
            db.update(TABLE_DOWNLOADS, values, "$COLUMN_DL_TRACK_ID = ?", arrayOf(trackId))
        } catch (e: Exception) {
            android.util.Log.w("ListeningHistoryDB", "Error markDownloadFailed: ${e.message}")
        }
    }

    /**
     * Menghapus record unduhan lagu dari tabel offline.
     */
    fun deleteDownload(trackId: String) {
        try {
            val db = getSafeWritableDatabase()
            ensureDownloadsTable(db)
            db.delete(TABLE_DOWNLOADS, "$COLUMN_DL_TRACK_ID = ?", arrayOf(trackId))
        } catch (e: Exception) {
            android.util.Log.w("ListeningHistoryDB", "Error deleteDownload: ${e.message}")
        }
    }

    /**
     * Membersihkan seluruh data unduhan dari database.
     */
    fun deleteAllDownloads() {
        try {
            val db = getSafeWritableDatabase()
            ensureDownloadsTable(db)
            db.delete(TABLE_DOWNLOADS, null, null)
        } catch (e: Exception) {
            android.util.Log.w("ListeningHistoryDB", "Error deleteAllDownloads: ${e.message}")
        }
    }

    /**
     * Mengambil seluruh lagu yang berstatus selesai terunduh dan file fisiknya valid di storage.
     */
    fun getAllDownloadedTracks(): List<Track> {
        val list = mutableListOf<Track>()
        try {
            val db = getSafeReadableDatabase()
            ensureDownloadsTable(db)
            val cursor = db.query(
                TABLE_DOWNLOADS,
                null,
                "$COLUMN_DL_STATUS = ?",
                arrayOf("COMPLETED"),
                null,
                null,
                "$COLUMN_DL_UPDATED_AT DESC"
            )
            cursor.use { c ->
                val idIdx = c.getColumnIndex(COLUMN_DL_TRACK_ID)
                val titleIdx = c.getColumnIndex(COLUMN_DL_TITLE)
                val artistIdx = c.getColumnIndex(COLUMN_DL_ARTIST)
                val albumIdx = c.getColumnIndex(COLUMN_DL_ALBUM)
                val durationIdx = c.getColumnIndex(COLUMN_DL_DURATION)
                val streamIdx = c.getColumnIndex(COLUMN_DL_STREAM_URL)
                val coverIdx = c.getColumnIndex(COLUMN_DL_COVER_URL)
                val genreIdx = c.getColumnIndex(COLUMN_DL_GENRE)
                val pathIdx = c.getColumnIndex(COLUMN_DL_LOCAL_PATH)
                val ytIdIdx = c.getColumnIndex(COLUMN_DL_YOUTUBE_ID)

                while (c.moveToNext()) {
                    val path = if (pathIdx >= 0) c.getString(pathIdx) else null
                    if (!path.isNullOrBlank() && java.io.File(path).exists()) {
                        val track = Track(
                            id = if (idIdx >= 0) c.getString(idIdx) else "",
                            title = if (titleIdx >= 0) c.getString(titleIdx) else "",
                            artist = if (artistIdx >= 0) c.getString(artistIdx) else "",
                            album = if (albumIdx >= 0) c.getString(albumIdx) else "Single",
                            durationSeconds = if (durationIdx >= 0) c.getInt(durationIdx) else 210,
                            streamUrl = if (streamIdx >= 0) c.getString(streamIdx) else "",
                            coverUrl = if (coverIdx >= 0) c.getString(coverIdx) else "",
                            genre = if (genreIdx >= 0) c.getString(genreIdx) else "Punk",
                            localFilePath = path,
                            youtubeVideoId = if (ytIdIdx >= 0) c.getString(ytIdIdx)?.takeIf { it.isNotBlank() } else null
                        )
                        list.add(track)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ListeningHistoryDB", "Error getAllDownloadedTracks: ${e.message}")
        }
        return list
    }

    /**
     * Mengambil file path lokal untuk trackId tertentu jika unduhan berstatus selesai.
     */
    fun getDownloadedFilePath(trackId: String): String? {
        try {
            val db = getSafeReadableDatabase()
            ensureDownloadsTable(db)
            val cursor = db.query(
                TABLE_DOWNLOADS,
                arrayOf(COLUMN_DL_LOCAL_PATH),
                "$COLUMN_DL_TRACK_ID = ? AND $COLUMN_DL_STATUS = ?",
                arrayOf(trackId, "COMPLETED"),
                null,
                null,
                null
            )
            cursor.use { c ->
                if (c.moveToFirst()) {
                    val path = c.getString(0)
                    if (!path.isNullOrBlank() && java.io.File(path).exists()) {
                        return path
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
