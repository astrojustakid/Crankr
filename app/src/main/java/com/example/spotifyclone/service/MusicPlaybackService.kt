package com.example.spotifyclone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.session.MediaButtonReceiver
import coil.Coil
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.spotifyclone.MainActivity
import com.example.spotifyclone.R
import com.example.spotifyclone.data.db.ListeningHistoryDatabaseHelper
import com.example.spotifyclone.data.db.ListeningHistoryEntity
import com.example.spotifyclone.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MusicNotificationReceiver {
    var onPlayPause: (() -> Unit)? = null
    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onSeekTo: ((Long) -> Unit)? = null
    var onBecomingNoisy: (() -> Unit)? = null
    var onToggleFavorite: (() -> Unit)? = null
    var isRepeatOne: Boolean = false
}

class MusicPlaybackService : Service() {

    private var mediaSession: MediaSessionCompat? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var coverLoadingJob: Job? = null
    private var lastCoverUrl: String? = null
    private var cachedBitmap: Bitmap? = null
    private var isNoisyReceiverRegistered = false
    private var isCurrentTrackFavorite: Boolean = false
    private var currentTrackObj: Track? = null

    private var currentTitle: String = "Crankr Music"
    private var currentArtist: String = "Playing"
    private var currentPositionMs: Long = 0L
    private var currentDurationMs: Long = 0L
    private var isCurrentlyPlaying: Boolean = false
    private var isCurrentlyBuffering: Boolean = false
    private var currentTrackId: String? = null
    private var currentArtistId: String? = null
    private var hasRecordedCurrentTrackPlay: Boolean = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private fun acquireWakeAndWifiLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Crankr:PlaybackWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(24 * 60 * 60 * 1000L)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL
                }
                wifiLock = wifiManager?.createWifiLock(mode, "Crankr:PlaybackWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            wifiLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeAndWifiLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                MusicNotificationReceiver.onBecomingNoisy?.invoke()
                    ?: MusicNotificationReceiver.onPlayPause?.invoke()
            }
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            MusicNotificationReceiver.onPlay?.invoke() ?: MusicNotificationReceiver.onPlayPause?.invoke()
        }

        override fun onPause() {
            releaseWakeAndWifiLocks()
            MusicNotificationReceiver.onPause?.invoke() ?: MusicNotificationReceiver.onPlayPause?.invoke()
        }

        override fun onSkipToNext() {
            if (MusicNotificationReceiver.isRepeatOne) {
                // Jangan panggil playNext jika mode saat ini adalah REPEAT_MODE_ONE
                MusicNotificationReceiver.onSeekTo?.invoke(0L)
            } else {
                MusicNotificationReceiver.onNext?.invoke()
            }
        }

        override fun onSkipToPrevious() {
            MusicNotificationReceiver.onPrevious?.invoke()
        }

        override fun onSeekTo(pos: Long) {
            currentPositionMs = pos
            updatePlaybackState(isCurrentlyPlaying, pos, currentDurationMs, isCurrentlyBuffering)
            MusicNotificationReceiver.onSeekTo?.invoke(pos)
        }

        override fun onStop() {
            releaseWakeAndWifiLocks()
            updatePlaybackState(isPlaying = false, positionMs = 0L, durationMs = 0L, isStopped = true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            val keyEvent = MediaButtonReceiver.handleIntent(mediaSession, mediaButtonEvent)
            return keyEvent != null || super.onMediaButtonEvent(mediaButtonEvent)
        }

        override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
            if (action == ACTION_TOGGLE_LIKE) {
                handleToggleLike()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "crankr_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_PLAY = "com.example.spotifyclone.ACTION_PLAY"
        const val ACTION_PLAY_DIRECT = "com.example.spotifyclone.ACTION_PLAY_DIRECT"
        const val ACTION_PAUSE = "com.example.spotifyclone.ACTION_PAUSE"
        const val ACTION_PLAY_PAUSE = "com.example.spotifyclone.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.example.spotifyclone.ACTION_PREV"
        const val ACTION_NEXT = "com.example.spotifyclone.ACTION_NEXT"
        const val ACTION_TOGGLE_LIKE = "com.example.spotifyclone.ACTION_TOGGLE_LIKE"
        const val ACTION_UPDATE = "com.example.spotifyclone.ACTION_UPDATE"
        const val ACTION_STOP = "com.example.spotifyclone.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_COVER_URL = "extra_cover_url"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_IS_BUFFERING = "extra_is_buffering"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_ARTIST_ID = "extra_artist_id"
        const val EXTRA_IS_FAVORITE = "extra_is_favorite"

        fun playDirect(context: Context, track: Track) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_PLAY_DIRECT
                putExtra(EXTRA_TITLE, track.title)
                putExtra(EXTRA_ARTIST, track.artist)
                putExtra(EXTRA_COVER_URL, track.coverUrl)
                putExtra(EXTRA_TRACK_ID, track.id)
                putExtra(EXTRA_ARTIST_ID, track.spotifyArtistId ?: "")
                putExtra(EXTRA_DURATION_MS, (track.durationSeconds * 1000L).coerceAtLeast(0L))
                putExtra(EXTRA_IS_PLAYING, true)
                putExtra(EXTRA_IS_BUFFERING, false)
                putExtra(EXTRA_POSITION_MS, 0L)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun update(
            context: Context,
            track: Track?,
            isPlaying: Boolean,
            positionMs: Long = 0L,
            durationMs: Long = 0L,
            isBuffering: Boolean = false,
            isFavorite: Boolean = false
        ) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_UPDATE
                if (track != null) {
                    putExtra(EXTRA_TITLE, track.title)
                    putExtra(EXTRA_ARTIST, track.artist)
                    putExtra(EXTRA_COVER_URL, track.coverUrl)
                    putExtra(EXTRA_TRACK_ID, track.id)
                    putExtra(EXTRA_ARTIST_ID, track.spotifyArtistId ?: "")
                    val dur = if (durationMs > 0) durationMs else (track.durationSeconds * 1000L).coerceAtLeast(0L)
                    putExtra(EXTRA_DURATION_MS, dur)
                }
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_IS_BUFFERING, isBuffering)
                putExtra(EXTRA_POSITION_MS, positionMs)
                putExtra(EXTRA_IS_FAVORITE, isFavorite)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val initialNotif = buildNotification(currentTitle, currentArtist, cachedBitmap, isCurrentlyPlaying)
        startServiceForeground(initialNotif)

        mediaSession = MediaSessionCompat(this, "CrankrMediaSession").apply {
            isActive = true
            setCallback(mediaSessionCallback)
        }

        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(noisyReceiver, filter)
            isNoisyReceiverRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY -> {
                MusicNotificationReceiver.onPlay?.invoke() ?: MusicNotificationReceiver.onPlayPause?.invoke()
            }
            ACTION_PAUSE -> {
                releaseWakeAndWifiLocks()
                MusicNotificationReceiver.onPause?.invoke() ?: MusicNotificationReceiver.onPlayPause?.invoke()
            }
            ACTION_PLAY_PAUSE -> {
                MusicNotificationReceiver.onPlayPause?.invoke()
            }
            ACTION_PREV -> {
                MusicNotificationReceiver.onPrevious?.invoke()
            }
            ACTION_NEXT -> {
                MusicNotificationReceiver.onNext?.invoke()
            }
            ACTION_TOGGLE_LIKE -> {
                handleToggleLike()
            }
            ACTION_STOP -> {
                releaseWakeAndWifiLocks()
                MusicNotificationReceiver.onPause?.invoke() ?: MusicNotificationReceiver.onPlayPause?.invoke()
                updatePlaybackState(isPlaying = false, positionMs = 0L, durationMs = 0L, isStopped = true)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE, ACTION_PLAY_DIRECT -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                val coverUrl = intent.getStringExtra(EXTRA_COVER_URL)
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isCurrentlyPlaying)
                val isBuffering = intent.getBooleanExtra(EXTRA_IS_BUFFERING, false)
                val posMs = intent.getLongExtra(EXTRA_POSITION_MS, currentPositionMs)
                val durMs = intent.getLongExtra(EXTRA_DURATION_MS, currentDurationMs)
                val trackId = intent.getStringExtra(EXTRA_TRACK_ID)
                val artistId = intent.getStringExtra(EXTRA_ARTIST_ID)
                val isFav = intent.getBooleanExtra(EXTRA_IS_FAVORITE, isCurrentTrackFavorite)
                isCurrentTrackFavorite = isFav
                if (!trackId.isNullOrBlank()) {
                    currentTrackObj = Track(
                        id = trackId,
                        title = title,
                        artist = artist,
                        album = "Crankr",
                        durationSeconds = (durMs / 1000L).toInt(),
                        streamUrl = "",
                        coverUrl = coverUrl ?: lastCoverUrl ?: ""
                    )
                }

                val isNewTrack = trackId != null && trackId != currentTrackId
                if (isNewTrack) {
                    coverLoadingJob?.cancel()
                    cachedBitmap = null
                    lastCoverUrl = null
                    currentTrackId = trackId
                    currentArtistId = artistId
                    hasRecordedCurrentTrackPlay = false
                    currentPositionMs = 0L
                }

                val effectivePosMs = if (isNewTrack) 0L else posMs

                // Catat riwayat mendengar jika lagu diputar minimal 30 detik (30.000 ms)
                if (isPlaying && !hasRecordedCurrentTrackPlay && !currentTrackId.isNullOrBlank()) {
                    if (effectivePosMs >= 30_000L) {
                        hasRecordedCurrentTrackPlay = true
                        val tId = currentTrackId ?: ""
                        val tTitle = title
                        val aId = currentArtistId ?: ""
                        val aName = artist
                        val cUrl = coverUrl ?: lastCoverUrl ?: ""
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                ListeningHistoryDatabaseHelper.getInstance(applicationContext).insert(
                                    ListeningHistoryEntity(
                                        trackId = tId,
                                        trackTitle = tTitle,
                                        artistId = aId,
                                        artistName = aName,
                                        albumArtUrl = cUrl,
                                        durationPlayedMs = effectivePosMs.coerceAtLeast(30_000L),
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                currentTitle = title
                currentArtist = artist
                currentPositionMs = effectivePosMs
                currentDurationMs = durMs
                isCurrentlyPlaying = isPlaying
                isCurrentlyBuffering = isBuffering

                if (isPlaying) {
                    acquireWakeAndWifiLocks()
                } else {
                    releaseWakeAndWifiLocks()
                }

                updatePlaybackState(isPlaying, effectivePosMs, durMs, isBuffering)
                updateMetadata(title, artist, durMs, cachedBitmap)
                buildAndNotify(title, artist, coverUrl, isPlaying)
            }
        }
        return START_STICKY
    }

    private fun handleToggleLike() {
        isCurrentTrackFavorite = !isCurrentTrackFavorite
        val currentNotification = buildNotification(
            title = currentTitle,
            artist = currentArtist,
            largeIconBitmap = cachedBitmap,
            isPlaying = isCurrentlyPlaying
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, currentNotification)
        updatePlaybackState(isCurrentlyPlaying, currentPositionMs, currentDurationMs, isCurrentlyBuffering)

        // Beritahu UI / PlayerViewModel jika aplikasi sedang aktif
        MusicNotificationReceiver.onToggleFavorite?.invoke()

        // Perbarui status favorit langsung ke database / LikedSongsManager di Room
        val tId = currentTrackId
        if (!tId.isNullOrBlank()) {
            val trackToToggle = currentTrackObj ?: Track(
                id = tId,
                title = currentTitle,
                artist = currentArtist,
                album = "Crankr",
                durationSeconds = (currentDurationMs / 1000L).toInt(),
                streamUrl = "",
                coverUrl = lastCoverUrl ?: ""
            )
            serviceScope.launch(Dispatchers.IO) {
                try {
                    com.example.spotifyclone.data.LikedSongsManager(applicationContext).toggleLiked(trackToToggle)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updatePlaybackState(
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        isBuffering: Boolean = false,
        isStopped: Boolean = false
    ) {
        val state = when {
            isStopped -> PlaybackStateCompat.STATE_STOPPED
            isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            isPlaying -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        val playbackSpeed = if (isPlaying && !isBuffering && !isStopped) 1.0f else 0.0f
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, positionMs.coerceAtLeast(0L), playbackSpeed, SystemClock.elapsedRealtime())

        val likeIconRes = if (isCurrentTrackFavorite) R.drawable.ic_notification_favorite else R.drawable.ic_notification_favorite_border
        val customAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_TOGGLE_LIKE,
            if (isCurrentTrackFavorite) "Batal Suka" else "Suka",
            likeIconRes
        ).build()
        stateBuilder.addCustomAction(customAction)

        mediaSession?.setPlaybackState(stateBuilder.build())
    }

    private fun updateMetadata(title: String, artist: String, durationMs: Long, bitmap: Bitmap?, coverUrl: String? = null) {
        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Crankr")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs.coerceAtLeast(0L))

        if (bitmap != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
        }
        val artUri = coverUrl ?: lastCoverUrl
        if (!artUri.isNullOrBlank()) {
            metaBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri)
            metaBuilder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri)
        }
        mediaSession?.setMetadata(metaBuilder.build())
    }

    private fun buildAndNotify(title: String, artist: String, coverUrl: String?, isPlaying: Boolean) {
        val initialNotif = buildNotification(title, artist, cachedBitmap, isPlaying)
        startServiceForeground(initialNotif)

        if (!coverUrl.isNullOrBlank() && (coverUrl != lastCoverUrl || cachedBitmap == null)) {
            lastCoverUrl = coverUrl
            coverLoadingJob?.cancel()
            val expectedTrackId = currentTrackId
            coverLoadingJob = serviceScope.launch {
                val bitmap = loadCoverBitmap(coverUrl)
                // Pastikan hanya memasang Bitmap jika ID lagu masih sama persis
                if (isActive && bitmap != null && (expectedTrackId == null || currentTrackId == expectedTrackId)) {
                    cachedBitmap = bitmap
                    updateMetadata(title, artist, currentDurationMs, bitmap, coverUrl)
                    val updatedNotif = buildNotification(title, artist, bitmap, isPlaying)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, updatedNotif)
                }
            }
        }
    }

    private fun startServiceForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(
        title: String,
        artist: String,
        largeIconBitmap: Bitmap?,
        isPlaying: Boolean
    ): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(
            this,
            1,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            2,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val likeIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_TOGGLE_LIKE }
        val likePendingIntent = PendingIntent.getService(
            this,
            4,
            likeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val likeIcon = if (isCurrentTrackFavorite) R.drawable.ic_notification_favorite else R.drawable.ic_notification_favorite_border
        val likeTitle = if (isCurrentTrackFavorite) "Batal Suka" else "Suka"

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)

        mediaSession?.let { session ->
            mediaStyle.setMediaSession(session.sessionToken)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("Crankr")
            .setSmallIcon(R.drawable.app_logo)
            .setContentIntent(mainPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setStyle(mediaStyle)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, playPauseTitle, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(likeIcon, likeTitle, likePendingIntent)

        if (largeIconBitmap != null) {
            builder.setLargeIcon(largeIconBitmap)
        }

        return builder.build()
    }

    private suspend fun loadCoverBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val loader = Coil.imageLoader(this@MusicPlaybackService)
            val request = ImageRequest.Builder(this@MusicPlaybackService)
                .data(url)
                .allowHardware(false)
                .build()
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            (result as? BitmapDrawable)?.bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Playback"
            val descriptionText = "Notifikasi pemutar audio Crankr"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try {
            serviceScope.cancel()
        } catch (_: Exception) {}
        releaseWakeAndWifiLocks()
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
                isNoisyReceiverRegistered = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
        MusicNotificationReceiver.onPlayPause = null
        MusicNotificationReceiver.onPlay = null
        MusicNotificationReceiver.onPause = null
        MusicNotificationReceiver.onNext = null
        MusicNotificationReceiver.onPrevious = null
        MusicNotificationReceiver.onSeekTo = null
        MusicNotificationReceiver.onBecomingNoisy = null
        MusicNotificationReceiver.onToggleFavorite = null
        super.onDestroy()
    }
}
