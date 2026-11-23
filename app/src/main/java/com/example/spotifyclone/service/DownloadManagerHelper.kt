package com.example.spotifyclone.service

import android.content.Context
import android.content.SharedPreferences
import com.example.spotifyclone.data.db.ListeningHistoryDatabaseHelper
import com.example.spotifyclone.data.model.Track
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * DownloadManagerHelper:
 * Terintegrasi penuh dengan DownloadUtil, CrankrDownloadService (Media3 ExoPlayer DownloadManager),
 * dan database Room / SQLite lokal (ListeningHistoryDatabaseHelper).
 * Mengelola riwayat file unduhan lokal di getExternalFilesDir, kalkulasi pemakaian penyimpanan,
 * dan penyediaan live progress state untuk UI Crankr.
 */
class DownloadManagerHelper(private val context: Context) {

    private val dbHelper = ListeningHistoryDatabaseHelper.getInstance(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("spotify_downloads_prefs", Context.MODE_PRIVATE)

    val downloadDir: File
        get() {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            val dir = File(base, "downloads")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    val downloadProgressMap: StateFlow<Map<String, Float>>
        get() = DownloadUtil.downloadProgressMap

    fun getLocalFilePath(trackId: String): String? {
        // 1. Cek dari database Room / SQLite
        val dbPath = dbHelper.getDownloadedFilePath(trackId)
        if (dbPath != null && File(dbPath).exists()) return dbPath

        // 2. Cek file langsung di externalFilesDir downloads
        val targetFile = File(downloadDir, "${trackId}.mp3")
        if (targetFile.exists() && targetFile.length() > 0) return targetFile.absolutePath

        // 3. Fallback SharedPreferences lama / filesDir
        val path = prefs.getString(trackId, null)
        if (path != null) {
            val file = File(path)
            if (file.exists() && file.length() > 0) return path
        }

        val oldInternalFile = File(File(context.filesDir, "downloads"), "${trackId}.mp3")
        if (oldInternalFile.exists() && oldInternalFile.length() > 0) {
            return oldInternalFile.absolutePath
        }

        return null
    }

    fun isTrackDownloaded(trackId: String): Boolean {
        return getLocalFilePath(trackId) != null
    }

    suspend fun downloadTrack(
        track: Track,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return DownloadUtil.downloadTrackWithRenewal(context, track, onProgress)
    }

    /**
     * Mengambil daftar lagu offline dari database Room / SQLite.
     */
    fun getAllDownloadedTracks(): List<Track> {
        return dbHelper.getAllDownloadedTracks()
    }

    fun deleteDownloadedTrack(trackId: String): Boolean {
        DownloadUtil.removeDownload(context, trackId)
        dbHelper.deleteDownload(trackId)

        val path = prefs.getString(trackId, null)
        prefs.edit().remove(trackId).apply()
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        val extFile = File(downloadDir, "${trackId}.mp3")
        if (extFile.exists()) {
            extFile.delete()
        }
        val oldFile = File(File(context.filesDir, "downloads"), "${trackId}.mp3")
        if (oldFile.exists()) {
            oldFile.delete()
        }
        return true
    }

    fun getOfflineStorageSizeFormatted(): String {
        var totalBytes: Long = 0
        downloadDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                totalBytes += file.length()
            }
        }
        File(context.filesDir, "downloads").listFiles()?.forEach { file ->
            if (file.isFile) {
                totalBytes += file.length()
            }
        }
        val mb = totalBytes.toDouble() / (1024 * 1024)
        return "%.1f MB".format(mb)
    }

    fun getDownloadedTrackCount(): Int {
        var count = 0
        downloadDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.extension == "mp3" || file.extension == "m4a" || file.extension == "webm")) {
                count++
            }
        }
        return count
    }

    fun deleteAllDownloads(): Boolean {
        prefs.edit().clear().apply()
        dbHelper.deleteAllDownloads()
        downloadDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
        File(context.filesDir, "downloads").listFiles()?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
        return true
    }
}
