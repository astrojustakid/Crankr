package com.example.spotifyclone.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

enum class NetworkType {
    WIFI,
    CELLULAR_STRONG,
    CELLULAR_WEAK,
    NONE
}

/**
 * NetworkQualityMonitor:
 * Memantau status, jenis, dan kualitas koneksi internet perangkat secara berkala menggunakan ConnectivityManager.
 * - Menggunakan bitrate standar / tinggi pada jaringan Wi-Fi.
 * - Mengalihkan pemanggilan stream ke format hemat 64 kbps secara adaptif saat sinyal seluler lemah atau latensi tinggi.
 */
object NetworkQualityMonitor {
    private var isMonitoring = false
    private var connectivityManager: ConnectivityManager? = null

    @Volatile
    var currentNetworkType: NetworkType = NetworkType.WIFI
        private set

    fun startMonitoring(context: Context) {
        if (isMonitoring) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = cm
        isMonitoring = true

        updateNetworkCapabilities(cm.getNetworkCapabilities(cm.activeNetwork))

        try {
            val request = NetworkRequest.Builder().build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    updateNetworkCapabilities(networkCapabilities)
                }

                override fun onLost(network: Network) {
                    currentNetworkType = NetworkType.NONE
                    Log.d("NetworkQualityMonitor", "Koneksi jaringan terputus.")
                }
            })
            Log.d("NetworkQualityMonitor", "Monitoring kualitas jaringan aktif.")
        } catch (e: Exception) {
            Log.w("NetworkQualityMonitor", "Gagal mendaftarkan network callback: ${e.message}")
        }
    }

    private fun updateNetworkCapabilities(caps: NetworkCapabilities?) {
        if (caps == null) {
            currentNetworkType = NetworkType.NONE
            return
        }

        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                currentNetworkType = NetworkType.WIFI
                // Pada jaringan Wi-Fi: Gunakan bitrate standar atau tinggi sesuai preferensi
                AudioStreamResolver.adaptiveQualityOverride = null
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val downstreamKbps = caps.linkDownstreamBandwidthKbps
                val isCongested = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
                val isSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
                } else false

                // Jika sinyal seluler lemah (< 1200 kbps), padat, atau suspended: alihkan ke SAVER (64 kbps)
                if (downstreamKbps in 1..1200 || isCongested || isSuspended) {
                    currentNetworkType = NetworkType.CELLULAR_WEAK
                    AudioStreamResolver.adaptiveQualityOverride = AudioQuality.SAVER
                    Log.i("NetworkQualityMonitor", "Sinyal seluler lemah ($downstreamKbps kbps). Mengalihkan bitrate audio ke format Hemat 64 kbps.")
                } else {
                    currentNetworkType = NetworkType.CELLULAR_STRONG
                    AudioStreamResolver.adaptiveQualityOverride = null
                }
            }
            else -> {
                currentNetworkType = NetworkType.NONE
            }
        }
    }
}

