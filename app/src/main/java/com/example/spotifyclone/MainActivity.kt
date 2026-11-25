package com.example.spotifyclone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import com.example.spotifyclone.ui.components.CrankrMotion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.spotifyclone.service.spotify.SpotifyExtractor
import com.example.spotifyclone.theme.SpotifyCloneTheme
import com.example.spotifyclone.ui.MainSpotifyApp
import com.example.spotifyclone.ui.screens.CrankrSplashScreen
import com.example.spotifyclone.ui.viewmodel.HomeViewModel
import com.example.spotifyclone.ui.viewmodel.PlayerViewModel
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpotifyExtractor.init(this)

        // Initialize high-performance Coil ImageLoader with hardware memory cache & 200ms crossfade
        val imageLoader = ImageLoader.Builder(this)
            .crossfade(true)
            .crossfade(200)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)

        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            SpotifyCloneTheme {
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(1350)
                    showSplash = false
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainSpotifyApp(
                            viewModel = playerViewModel,
                            homeViewModel = homeViewModel
                        )

                        AnimatedVisibility(
                            visible = showSplash,
                            enter = fadeIn(tween(120)),
                            exit = fadeOut(
                                tween(durationMillis = 340, easing = CrankrMotion.EaseOutStrong)
                            ) + scaleOut(
                                targetScale = 1.04f,
                                animationSpec = tween(durationMillis = 340, easing = CrankrMotion.EaseOutStrong)
                            )
                        ) {
                            CrankrSplashScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null) {
            val urlStr = uri.toString()
            if (urlStr.contains("spotify.com") || urlStr.contains("youtube.com") || urlStr.contains("youtu.be")) {
                playerViewModel.importPlaylistFromUrl(urlStr)
            }
        }
        val extraText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!extraText.isNullOrBlank() && (extraText.contains("spotify.com") || extraText.contains("youtube.com") || extraText.contains("youtu.be"))) {
            playerViewModel.importPlaylistFromUrl(extraText)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !playerViewModel.isPlaying.value) {
            com.example.spotifyclone.service.MusicPlaybackService.stop(this)
        }
    }
}
