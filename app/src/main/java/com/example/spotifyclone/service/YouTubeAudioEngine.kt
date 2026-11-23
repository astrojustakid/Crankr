package com.example.spotifyclone.service

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class YouTubeAudioEngine(
    private val context: Context? = null,
    private val onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit,
    private val onPlaybackStateChanged: (isPlaying: Boolean) -> Unit,
    private val onTrackEnded: () -> Unit,
    private val onPlaybackError: ((videoId: String, errorCode: Int) -> Unit)? = null
) {
    var webView: WebView? = null
        private set

    private var currentVideoId: String? = null
    var isCurrentlyPlaying = false
        private set
    private var currentPlaybackSpeed = 1.0f

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pollerRunnable: Runnable? = null

    init {
        context?.let { ctx ->
            initPersistentWebView(ctx)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun initPersistentWebView(ctx: Context) {
        if (webView != null) return
        mainHandler.post {
            if (webView != null) return@post
            try {
                val wv = object : WebView(ctx.applicationContext) {
                    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false
                    override fun onTouchEvent(event: MotionEvent?): Boolean = false
                    override fun onGenericMotionEvent(event: MotionEvent?): Boolean = false
                    override fun onTrackballEvent(event: MotionEvent?): Boolean = false
                    override fun onCheckIsTextEditor(): Boolean = false
                    override fun onWindowVisibilityChanged(visibility: Int) {
                        // Always report VISIBLE so that screen lock / screen-off does not pause Chromium WebMediaPlayer
                        super.onWindowVisibilityChanged(View.VISIBLE)
                    }
                }
                attachWebView(wv)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attachWebView(wv: WebView) {
        if (this.webView === wv) return
        this.webView = wv
        wv.apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectAutoPlayScript()
                }
            }
            addJavascriptInterface(Bridge(), "AndroidBridge")
        }
    }

    private var playbackStartWatchdogRunnable: Runnable? = null

    private fun startPlaybackWatchdog(videoId: String) {
        cancelPlaybackWatchdog()
        playbackStartWatchdogRunnable = Runnable {
            if (currentVideoId == videoId && !isCurrentlyPlaying) {
                Log.w("CRANKR_YOUTUBE", "YouTube playback timed out after 5s for $videoId. Triggering error fallback.")
                onPlaybackError?.invoke(videoId, -1)
            }
        }
        mainHandler.postDelayed(playbackStartWatchdogRunnable!!, 5000L)
    }

    private fun cancelPlaybackWatchdog() {
        playbackStartWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        playbackStartWatchdogRunnable = null
    }

    fun play(videoId: String) {
        currentVideoId = videoId
        isCurrentlyPlaying = false
        startPlaybackWatchdog(videoId)

        mainHandler.post {
            if (webView == null && context != null) {
                initPersistentWebView(context)
            }
            webView?.evaluateJavascript(
                "typeof window.player !== 'undefined' && window.player !== null && typeof window.player.loadVideoById === 'function'"
            ) { result ->
                val canReuse = result?.trim()?.trim('"') == "true"
                if (canReuse) {
                    webView?.evaluateJavascript(
                        """
                        try {
                            window.player.loadVideoById('$videoId');
                            window.player.playVideo();
                        } catch(e) {}
                        """.trimIndent(),
                        null
                    )
                    startPeriodicPoller()
                } else {
                    val html = buildEmbedHtml(videoId)
                    webView?.loadDataWithBaseURL("https://com.example.spotifyclone", html, "text/html", "UTF-8", null)
                    startPeriodicPoller()
                }
            }
        }
    }

    fun togglePlayPause() {
        if (isCurrentlyPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        isCurrentlyPlaying = false
        cancelPlaybackWatchdog()
        stopPeriodicPoller()
        mainHandler.post {
            webView?.evaluateJavascript(
                """
                try {
                    if (window.player && window.player.pauseVideo) window.player.pauseVideo();
                    var v = document.querySelector('video');
                    if (v) v.pause();
                } catch(e) {}
                """.trimIndent(),
                null
            )
        }
        onPlaybackStateChanged(false)
    }

    fun resume() {
        isCurrentlyPlaying = true
        mainHandler.post {
            webView?.evaluateJavascript(
                """
                try {
                    if (window.player && window.player.playVideo) window.player.playVideo();
                    var v = document.querySelector('video');
                    if (v) v.play();
                } catch(e) {}
                """.trimIndent(),
                null
            )
            startPeriodicPoller()
        }
        onPlaybackStateChanged(true)
    }

    fun seekTo(positionMs: Long) {
        val seconds = positionMs / 1000f
        mainHandler.post {
            webView?.evaluateJavascript(
                """
                try {
                    if (window.player && window.player.seekTo) window.player.seekTo($seconds, true);
                    var v = document.querySelector('video');
                    if (v) v.currentTime = $seconds;
                } catch(e) {}
                """.trimIndent(),
                null
            )
        }
    }

    fun setVolume(volumeFraction: Float) {
        val vol100 = (volumeFraction.coerceIn(0f, 1f) * 100).toInt()
        val vol1 = volumeFraction.coerceIn(0f, 1f)
        mainHandler.post {
            webView?.evaluateJavascript(
                """
                try {
                    if (window.player && typeof window.player.setVolume === 'function') {
                        window.player.setVolume($vol100);
                    }
                    var v = document.querySelector('video');
                    if (v) {
                        v.volume = $vol1;
                    }
                } catch(e) {}
                """.trimIndent(),
                null
            )
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        currentPlaybackSpeed = speed
        mainHandler.post {
            webView?.evaluateJavascript(
                """
                try {
                    if (window.player && typeof window.player.setPlaybackRate === 'function') {
                        window.player.setPlaybackRate($speed);
                    }
                    var v = document.querySelector('video');
                    if (v) {
                        v.playbackRate = $speed;
                    }
                } catch(e) {}
                """.trimIndent(),
                null
            )
        }
    }

    fun stop() {
        isCurrentlyPlaying = false
        cancelPlaybackWatchdog()
        stopPeriodicPoller()
        mainHandler.post {
            webView?.evaluateJavascript(
                """
                try {
                    if (window.player && window.player.stopVideo) window.player.stopVideo();
                    var v = document.querySelector('video');
                    if (v) { v.pause(); v.currentTime = 0; }
                } catch(e) {}
                """.trimIndent(),
                null
            )
        }
        onPlaybackStateChanged(false)
    }

    fun release() {
        stop()
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                stopPeriodicPoller()
                webView?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    removeJavascriptInterface("AndroidBridge")
                    destroy()
                }
                webView = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun injectAutoPlayScript() {
        webView?.evaluateJavascript(
            """
            (function() {
                try {
                    var v = document.querySelector('video');
                    if (v) {
                        v.playbackRate = $currentPlaybackSpeed;
                        v.play().catch(function() {});
                    }
                    if (window.player && typeof window.player.setPlaybackRate === 'function') {
                        window.player.setPlaybackRate($currentPlaybackSpeed);
                    }
                    var playBtn = document.querySelector('.ytp-large-play-button') || document.querySelector('button[aria-label="Play"]');
                    if (playBtn) playBtn.click();
                } catch(e) {}
            })();
            """.trimIndent(),
            null
        )
    }

    private fun startPeriodicPoller() {
        stopPeriodicPoller()
        pollerRunnable = object : Runnable {
            override fun run() {
                if (!isCurrentlyPlaying) return

                webView?.evaluateJavascript(
                    """
                    (function() {
                        try {
                            if (window.player && window.player.getCurrentTime && window.player.getDuration) {
                                var cur = window.player.getCurrentTime();
                                var dur = window.player.getDuration();
                                if (dur > 0) {
                                    return JSON.stringify({ cur: Math.floor(cur * 1000), dur: Math.floor(dur * 1000), ok: 1 });
                                }
                            }
                            var v = document.querySelector('video');
                            if (v && v.duration > 0) {
                                return JSON.stringify({ cur: Math.floor(v.currentTime * 1000), dur: Math.floor(v.duration * 1000), ok: 1 });
                            }
                        } catch(e) {}
                        return null;
                    })();
                    """.trimIndent()
                ) { result ->
                    if (result != null && result != "null" && result.contains("ok")) {
                        try {
                            val clean = if (result.startsWith("\"") && result.endsWith("\"")) {
                                org.json.JSONTokener(result).nextValue().toString()
                            } else result
                            val json = JSONObject(clean)
                            val cur = json.getLong("cur")
                            val dur = json.getLong("dur")
                            if (dur > 0) {
                                onProgressUpdate(cur, dur)
                            }
                        } catch (_: Exception) {}
                    }
                }

                mainHandler.postDelayed(this, 500)
            }
        }
        mainHandler.postDelayed(pollerRunnable!!, 500)
    }

    private fun stopPeriodicPoller() {
        pollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            pollerRunnable = null
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            mainHandler.post {
                cancelPlaybackWatchdog()
                isCurrentlyPlaying = true
                onPlaybackStateChanged(true)
                startPeriodicPoller()
            }
        }

        @JavascriptInterface
        fun onProgress(positionMs: Long, durationMs: Long) {
            mainHandler.post {
                if (positionMs > 0) {
                    cancelPlaybackWatchdog()
                }
                onProgressUpdate(positionMs, durationMs)
            }
        }

        @JavascriptInterface
        fun onStateChange(state: Int) {
            // 1: PLAYING, 2: PAUSED, 0: ENDED, 3: BUFFERING
            mainHandler.post {
                when (state) {
                    1 -> {
                        cancelPlaybackWatchdog()
                        isCurrentlyPlaying = true
                        onPlaybackStateChanged(true)
                        startPeriodicPoller()
                    }
                    2 -> {
                        isCurrentlyPlaying = false
                        onPlaybackStateChanged(false)
                        stopPeriodicPoller()
                    }
                    3 -> {
                        // Buffering state: jangan picu playing di notifikasi Android
                        onPlaybackStateChanged(false)
                    }
                    0 -> {
                        cancelPlaybackWatchdog()
                        isCurrentlyPlaying = false
                        onPlaybackStateChanged(false)
                        stopPeriodicPoller()
                        onTrackEnded()
                    }
                }
            }
        }

        @JavascriptInterface
        fun onError(errorCode: Int) {
            val vid = currentVideoId ?: ""
            mainHandler.post {
                cancelPlaybackWatchdog()
                onPlaybackError?.invoke(vid, errorCode)
            }
        }
    }

    private fun buildEmbedHtml(videoId: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <meta name="referrer" content="strict-origin-when-cross-origin">
              <style>
                * { margin:0; padding:0; background:#000; overflow:hidden; }
                html, body { width:100%; height:100%; }
                #player { width:100%; height:100%; }
              </style>
              <script defer src="https://www.youtube.com/iframe_api"></script>
            </head>
            <body>
              <div id="player"></div>
              <script>
                var player = null;
                var progressInterval = null;

                function onYouTubeIframeAPIReady() {
                  player = new YT.Player('player', {
                    height: '100%',
                    width: '100%',
                    videoId: '$videoId',
                    playerVars: {
                      'autoplay': 1,
                      'playsinline': 1,
                      'controls': 0,
                      'enablejsapi': 1,
                      'fs': 0,
                      'rel': 0,
                      'iv_load_policy': 3,
                      'origin': 'https://com.example.spotifyclone'
                    },
                    events: {
                      'onReady': function(e) {
                        try { e.target.playVideo(); } catch(err) {}
                        if (window.AndroidBridge) {
                          window.AndroidBridge.onReady();
                          window.AndroidBridge.onStateChange(1);
                        }
                        startTracking();
                      },
                      'onStateChange': function(e) {
                        if (window.AndroidBridge) {
                          window.AndroidBridge.onStateChange(e.data);
                        }
                        if (e.data === 1) {
                          startTracking();
                        } else if (e.data === 2 || e.data === 0) {
                          stopTracking();
                        }
                      },
                      'onError': function(e) {
                        if (window.AndroidBridge) {
                          window.AndroidBridge.onError(e.data);
                        }
                      }
                    }
                  });
                }

                function startTracking() {
                  if (progressInterval) clearInterval(progressInterval);
                  progressInterval = setInterval(function() {
                    try {
                      if (player && typeof player.getCurrentTime === 'function' && typeof player.getDuration === 'function') {
                        var cur = player.getCurrentTime();
                        var dur = player.getDuration();
                        if (dur > 0 && window.AndroidBridge) {
                          window.AndroidBridge.onProgress(Math.floor(cur * 1000), Math.floor(dur * 1000));
                        }
                      }
                    } catch(e) {}
                  }, 400);
                }

                function stopTracking() {
                  if (progressInterval) {
                    clearInterval(progressInterval);
                    progressInterval = null;
                  }
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}