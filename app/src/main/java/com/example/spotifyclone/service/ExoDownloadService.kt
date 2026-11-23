package com.example.spotifyclone.service

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.example.spotifyclone.R

/**
 * ExoDownloadService:
 * Foreground Service resmi bertipe dataSync berbasis androidx.media3.exoplayer.offline.DownloadService.
 * Berjalan stabil di latar belakang untuk mengunduh lagu dan segmen audio ke SimpleCache
 * tanpa terputus ketika aplikasi diminimalkan atau layar dimatikan.
 */
@OptIn(UnstableApi::class)
open class ExoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.app_name,
    0
) {
    override fun getDownloadManager(): DownloadManager = DownloadUtil.getDownloadManager(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification {
        return DownloadUtil.getDownloadNotificationHelper(this)
            .buildProgressNotification(
                this,
                R.drawable.ic_launcher_foreground,
                null,
                null,
                downloads,
                notMetRequirements
            )
    }

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 2001
    }
}
