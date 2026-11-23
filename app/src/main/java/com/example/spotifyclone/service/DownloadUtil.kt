package com.example.spotifyclone.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.example.spotifyclone.data.db.ListeningHistoryDatabaseHelper
import com.example.spotifyclone.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * DownloadUtil:
 * Mengelola ExoPlayer DownloadManager resmi yang terikat secara atomik dengan
 * StandaloneDatabaseProvider dan SimpleCache lokal MediaCacheManager.
 * Menjamin pembaruan URL stream aktif bebas HTTP 403, delegasi ke Foreground Service,
 * serta pemantauan progres unduhan akurat secara real-time.
 */
@OptIn(UnstableApi::class)
object DownloadUtil {

    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "crankr_downloads_channel"
    private const val TAG = "CrankrDownloadUtil"

    private var downloadManager: DownloadManager? = null
    private var downloadNotificationHelper: DownloadNotificationHelper? = null

    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var progressTrackerJob: Job? = null

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManager == null) {
            val appContext = context.applicationContext
            val databaseProvider = MediaCacheManager.getDatabaseProvider(appContext)
            val downloadCache = MediaCacheManager.getCache(appContext)

            val upstreamFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

            val executor = Executors.newFixedThreadPool(4)
            val dm = DownloadManager(
                appContext,
                databaseProvider,
                downloadCache,
                upstreamFactory,
                executor
            ).apply {
                maxParallelDownloads = 3
                minRetryCount = 5
                requirements = Requirements(Requirements.NETWORK)
            }

            dm.addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    val trackId = download.request.id
                    when (download.state) {
                        Download.STATE_DOWNLOADING -> {
                            val percent = if (download.percentDownloaded >= 0) {
                                (download.percentDownloaded / 100f).coerceIn(0.01f, 0.99f)
                            } else {
                                0.05f
                            }
                            _downloadProgressMap.update { it + (trackId to percent) }
                        }
                        Download.STATE_COMPLETED -> {
                            _downloadProgressMap.update { it - trackId }
                            Log.d(TAG, "Download selesai untuk track: $trackId")
                        }
                        Download.STATE_FAILED, Download.STATE_STOPPED, Download.STATE_REMOVING -> {
                            _downloadProgressMap.update { it - trackId }
                            finalException?.let {
                                Log.w(TAG, "Download error/stop track $trackId: ${it.message}")
                            }
                        }
                    }
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    _downloadProgressMap.update { it - download.request.id }
                }
            })

            // Pulihkan status unduhan lokal dari database SQLite ExoPlayerDownloads agar tidak hilang saat aplikasi ditutup
            scope.launch {
                try {
                    val cursor = dm.downloadIndex.getDownloads()
                    val prefs = appContext.getSharedPreferences("spotify_downloads_prefs", Context.MODE_PRIVATE)
                    val downloadsDir = getDownloadsDirectory(appContext)
                    val restoredProgress = mutableMapOf<String, Float>()
                    while (cursor.moveToNext()) {
                        val download = cursor.download
                        val id = download.request.id
                        if (download.state == Download.STATE_COMPLETED) {
                            val targetFile = File(downloadsDir, "${id}.mp3")
                            if (targetFile.exists() && targetFile.length() > 0) {
                                prefs.edit().putString(id, targetFile.absolutePath).apply()
                            }
                        } else if (download.state == Download.STATE_DOWNLOADING) {
                            val percent = if (download.percentDownloaded >= 0) {
                                (download.percentDownloaded / 100f).coerceIn(0.01f, 0.99f)
                            } else {
                                0.05f
                            }
                            restoredProgress[id] = percent
                        }
                    }
                    cursor.close()
                    if (restoredProgress.isNotEmpty()) {
                        _downloadProgressMap.update { it + restoredProgress }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error restoring downloads from ExoPlayerDownloads: ${e.message}")
                }
            }

            downloadManager = dm
            startProgressTracker(dm)
        }
        return downloadManager!!
    }

    @Synchronized
    fun getDownloadNotificationHelper(context: Context): DownloadNotificationHelper {
        if (downloadNotificationHelper == null) {
            val appContext = context.applicationContext
            ensureNotificationChannel(appContext)
            downloadNotificationHelper = DownloadNotificationHelper(appContext, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        }
        return downloadNotificationHelper!!
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                "Crankr Unduhan Musik",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Proses pengunduhan lagu di latar belakang"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startProgressTracker(dm: DownloadManager) {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch {
            while (isActive) {
                try {
                    val currentProgress = _downloadProgressMap.value
                    if (currentProgress.isNotEmpty()) {
                        val cursor = dm.downloadIndex.getDownloads()
                        val newProgress = currentProgress.toMutableMap()
                        while (cursor.moveToNext()) {
                            val download = cursor.download
                            val id = download.request.id
                            if (download.state == Download.STATE_DOWNLOADING) {
                                val p = if (download.percentDownloaded >= 0) {
                                    (download.percentDownloaded / 100f).coerceIn(0.01f, 0.99f)
                                } else {
                                    0.05f
                                }
                                newProgress[id] = p
                            } else if (download.state == Download.STATE_COMPLETED || download.state == Download.STATE_FAILED) {
                                newProgress.remove(id)
                            }
                        }
                        cursor.close()
                        _downloadProgressMap.value = newProgress
                    }
                } catch (_: Exception) {}
                delay(400)
            }
        }
    }

    /**
     * Mengambil direktori unduhan offline di getExternalFilesDir (bebas blokir izin penyimpanan Android).
     */
    fun getDownloadsDirectory(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Memulai proses pengunduhan lagu dengan pengambilan ulang URL stream aktif secara otomatis
     * untuk mencegah HTTP 403 Forbidden. Menggunakan Foreground Service untuk kestabilan di latar belakang,
     * serta mencatat progres dan status unduhan ke database Room / SQLite secara berkala.
     */
    suspend fun downloadTrackWithRenewal(
        context: Context,
        track: Track,
        onProgress: ((Float) -> Unit)? = null
    ): Result<String> {
        val appContext = context.applicationContext
        val trackId = track.id
        val dbHelper = ListeningHistoryDatabaseHelper.getInstance(appContext)

        _downloadProgressMap.update { it + (trackId to 0.05f) }
        onProgress?.invoke(0.05f)
        dbHelper.recordDownloadStatus(track, "DOWNLOADING", 0.05f)

        // 1. Ekstraksi / Refresh URL streaming audio murni dari YouTube Music InnerTube
        var freshAudioUrl: String? = null
        val vid = track.youtubeVideoId?.takeIf { it.isNotBlank() }
            ?: AudioStreamResolver.resolveYouTubeVideoId(track)

        if (!vid.isNullOrBlank()) {
            // Bersihkan cache memori terlebih dahulu agar URL streaming yang didapat memiliki token kedaluwarsa baru
            AudioStreamResolver.clearStreamCache()
            val candidates = AudioStreamResolver.resolveCandidateStreamUrls(vid)
            if (candidates.isNotEmpty()) {
                freshAudioUrl = candidates.first()
            }
        }

        if (freshAudioUrl.isNullOrBlank() && track.streamUrl.startsWith("http") && !track.streamUrl.contains("youtube.com")) {
            freshAudioUrl = track.streamUrl
        }

        if (freshAudioUrl.isNullOrBlank()) {
            dbHelper.markDownloadFailed(trackId)
            _downloadProgressMap.update { it - trackId }
            return Result.failure(Exception("Aliran audio streaming resmi tidak tersedia untuk diunduh"))
        }

        // 2. Kirim DownloadRequest ke ExoPlayer DownloadManager & CrankrDownloadService (Foreground Service)
        try {
            val uri = Uri.parse(freshAudioUrl)
            val downloadRequest = DownloadRequest.Builder(trackId, uri)
                .setCustomCacheKey(trackId)
                .setData(track.title.toByteArray(Charsets.UTF_8))
                .build()

            DownloadService.sendAddDownload(
                appContext,
                ExoDownloadService::class.java,
                downloadRequest,
                /* foreground = */ true
            )
        } catch (e: Exception) {
            Log.w(TAG, "ExoPlayer DownloadService sendAddDownload fallback: ${e.message}")
        }

        // 3. Simpan salinan file offline permanen ke folder lokal aplikasi getExternalFilesDir
        val downloadsDir = getDownloadsDirectory(appContext)
        val targetFile = File(downloadsDir, "${trackId}.mp3")
        val tempFile = File(downloadsDir, "${trackId}.tmp")

        // 4. Mekanisme pengunduhan tangguh dengan coba ulang otomatis (Auto-Retry) saat koneksi terputus
        val maxAttempts = 3
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                // Jika retry setelah kegagalan atau URL kedaluwarsa, ambil ulang stream URL baru
                if (freshAudioUrl.isNullOrBlank() || attempt > 1) {
                    if (!vid.isNullOrBlank()) {
                        AudioStreamResolver.clearStreamCache()
                        val retryCandidates = AudioStreamResolver.resolveCandidateStreamUrls(vid)
                        if (retryCandidates.isNotEmpty()) {
                            freshAudioUrl = retryCandidates.first()
                        }
                    }
                }

                if (freshAudioUrl.isNullOrBlank()) {
                    throw Exception("URL audio baru tidak dapat diperbarui")
                }

                val connection = (URL(freshAudioUrl).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                    connectTimeout = 15000
                    readTimeout = 30000
                    connect()
                }

                val responseCode = connection.responseCode
                if (responseCode in 400..403 || responseCode == 410) {
                    connection.disconnect()
                    freshAudioUrl = null // picu refresh URL pada percobaan selanjutnya
                    Log.w(TAG, "Download attempt $attempt received HTTP $responseCode, retrying with fresh URL...")
                    if (attempt < maxAttempts) {
                        delay(1200L * attempt)
                        continue
                    }
                }

                val fileLength = connection.contentLengthLong
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(16384)
                var totalBytesRead: Long = 0
                var bytesRead: Int
                var lastDbUpdateMs = System.currentTimeMillis()

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (fileLength > 0) {
                        val progress = (totalBytesRead.toFloat() / fileLength.toFloat()).coerceIn(0.05f, 0.99f)
                        _downloadProgressMap.update { it + (trackId to progress) }
                        onProgress?.invoke(progress)

                        // Catat persentase unduh ke Room / SQLite secara berkala (setiap 500ms)
                        val now = System.currentTimeMillis()
                        if (now - lastDbUpdateMs >= 500) {
                            dbHelper.updateDownloadProgress(trackId, progress)
                            lastDbUpdateMs = now
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                connection.disconnect()

                if (tempFile.renameTo(targetFile)) {
                    val prefs = appContext.getSharedPreferences("spotify_downloads_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString(trackId, targetFile.absolutePath).apply()

                    // Catat status selesai dan lokasi berkas lokal ke database Room / SQLite
                    dbHelper.markDownloadCompleted(trackId, targetFile.absolutePath)

                    _downloadProgressMap.update { it - trackId }
                    onProgress?.invoke(1.0f)
                    return Result.success(targetFile.absolutePath)
                } else {
                    tempFile.delete()
                    throw Exception("Gagal memindahkan file audio sementara ke nama permanen")
                }
            } catch (e: Exception) {
                lastException = e
                tempFile.delete()
                Log.w(TAG, "Download attempt $attempt failed for $trackId: ${e.message}")
                if (attempt < maxAttempts) {
                    delay(1500L * attempt)
                }
            }
        }

        // Seluruh percobaan gagal
        dbHelper.markDownloadFailed(trackId)
        _downloadProgressMap.update { it - trackId }
        return Result.failure(lastException ?: Exception("Gagal mengunduh lagu setelah $maxAttempts percobaan"))
    }

    fun removeDownload(context: Context, trackId: String) {
        val appContext = context.applicationContext
        val dbHelper = ListeningHistoryDatabaseHelper.getInstance(appContext)
        dbHelper.deleteDownload(trackId)

        try {
            DownloadService.sendRemoveDownload(
                appContext,
                ExoDownloadService::class.java,
                trackId,
                false
            )
        } catch (_: Exception) {}

        try {
            MediaCacheManager.getCache(appContext).removeResource(trackId)
        } catch (_: Exception) {}

        _downloadProgressMap.update { it - trackId }

        val prefs = appContext.getSharedPreferences("spotify_downloads_prefs", Context.MODE_PRIVATE)
        val path = prefs.getString(trackId, null)
        prefs.edit().remove(trackId).apply()
        if (path != null) {
            val f = File(path)
            if (f.exists()) f.delete()
        }

        val targetFile = File(getDownloadsDirectory(appContext), "${trackId}.mp3")
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }
}
