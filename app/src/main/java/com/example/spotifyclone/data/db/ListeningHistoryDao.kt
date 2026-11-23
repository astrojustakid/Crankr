package com.example.spotifyclone.data.db

/**
 * Interface DAO untuk agregasi data riwayat mendengar Crankr.
 */
interface ListeningHistoryDao {
    /**
     * Memasukkan rekaman lagu yang telah didengar minimal 30 detik.
     */
    suspend fun insert(entity: ListeningHistoryEntity): Long

    /**
     * Total menit mendengar dalam rentang waktu tertentu (default all-time).
     */
    suspend fun getTotalMinutesPlayed(fromTimestamp: Long = 0L): Long

    /**
     * Top N artis paling sering diputar berdasarkan jumlah putaran dan total waktu.
     */
    suspend fun getTopArtists(limit: Int = 5, fromTimestamp: Long = 0L): List<TopArtistStat>

    /**
     * Top N lagu paling sering diputar berdasarkan jumlah putaran.
     */
    suspend fun getTopTracks(limit: Int = 5, fromTimestamp: Long = 0L): List<TopTrackStat>

    /**
     * Jumlah total artis independen unik yang pernah didengarkan.
     */
    suspend fun getUniqueArtistCount(fromTimestamp: Long = 0L): Int

    /**
     * Lagu #1 yang paling sering diulang (Trek Paling Rusak).
     */
    suspend fun getTopTrack(fromTimestamp: Long = 0L): TopTrackStat?

    /**
     * Mengambil paket lengkap data ringkasan Crankr Recap.
     */
    suspend fun getRecapData(fromTimestamp: Long = 0L): CrankrRecapData

    /**
     * Mengambil seluruh riwayat pemutaran (dibatasi limit terbaru).
     */
    suspend fun getAllHistory(limit: Int = 100): List<ListeningHistoryEntity>

    /**
     * Membersihkan seluruh data riwayat pemutaran.
     */
    suspend fun clearHistory()
}
