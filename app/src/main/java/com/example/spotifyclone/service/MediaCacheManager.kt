package com.example.spotifyclone.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Singleton MediaCacheManager:
 * Menyediakan disk caching ExoPlayer berkapasitas 1 GB menggunakan SimpleCache
 * dan CacheDataSource.Factory dengan LeastRecentlyUsedCacheEvictor.
 * Setiap audio yang sedang dialirkan otomatis tersimpan ke cache lokal ponsel
 * dan langsung diputar dari disk saat lagu yang sama diputar kembali.
 */
@OptIn(UnstableApi::class)
object MediaCacheManager {
    private var simpleCache: SimpleCache? = null
    private var databaseProvider: DatabaseProvider? = null
    const val CACHE_SIZE_BYTES = 1024 * 1024 * 1024L // 1 GB Disk Cache
    const val INITIAL_CHUNK_SIZE_BYTES = 128 * 1024L // 128 KB potongan awal

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    @Synchronized
    fun getDatabaseProvider(context: Context): DatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider = ExoPlayerDownloadsDatabaseProvider.getInstance(context.applicationContext)
        }
        return databaseProvider!!
    }

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.applicationContext.cacheDir, "crankr_media_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES)
            val dbProvider = getDatabaseProvider(context)
            simpleCache = SimpleCache(cacheDir, evictor, dbProvider)
        }
        return simpleCache!!
    }

    fun createCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val cache = getCache(context)
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(25000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun createCacheDataSource(context: Context): CacheDataSource {
        return createCacheDataSourceFactory(context).createDataSource()
    }

    /**
     * Menulis potongan awal data audio (misal 128 KB pertama) langsung ke SimpleCache
     * sehingga ExoPlayer langsung memutar detik awal audio dari disk lokal seketika.
     */
    fun writeInitialChunkToCache(context: Context, key: String, chunkBytes: ByteArray): Boolean {
        if (key.isBlank() || chunkBytes.isEmpty()) return false
        val cache = getCache(context)
        return synchronized(cache) {
            try {
                if (cache.isCached(key, 0L, chunkBytes.size.toLong())) {
                    return@synchronized true
                }
                val cacheFile = cache.startFile(key, 0L, chunkBytes.size.toLong())
                FileOutputStream(cacheFile).use { fos ->
                    fos.write(chunkBytes)
                    fos.flush()
                }
                cache.commitFile(cacheFile, chunkBytes.size.toLong())
                Log.d("CRANKR_CACHE", "Berhasil commit potongan awal 128 KB untuk key $key ke SimpleCache")
                true
            } catch (e: Exception) {
                Log.w("CRANKR_CACHE", "Gagal menulis potongan awal ke SimpleCache: ${e.message}")
                false
            }
        }
    }

    /**
     * Mengunduh 128 KB pertama dari berkas audio lagu yang tampil di layar menggunakan OkHttpClient
     * dan menulisnya langsung ke SimpleCache milik ExoPlayer (Zero Latency playback).
     */
    fun downloadInitialChunk(context: Context, streamUrl: String, key: String): Boolean {
        if (streamUrl.isBlank() || key.isBlank()) return false
        val cache = getCache(context)
        if (cache.isCached(key, 0L, INITIAL_CHUNK_SIZE_BYTES)) {
            return true
        }

        return try {
            val request = Request.Builder()
                .url(streamUrl)
                .header("Range", "bytes=0-${INITIAL_CHUNK_SIZE_BYTES - 1}")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful || response.code == 206) {
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    writeInitialChunkToCache(context, key, bytes)
                } else false
            } else {
                Log.w("CRANKR_CACHE", "Gagal download 128 KB potongan awal: HTTP ${response.code}")
                false
            }
        } catch (e: Exception) {
            Log.w("CRANKR_CACHE", "Error download initial chunk OkHttp: ${e.message}")
            false
        }
    }

    /**
     * Mengunduh seluruh berkas lagu berikutnya menggunakan CacheWriter ke SimpleCache
     * agar lagu baru berputar lancar tanpa buffering walau ponsel masuk area tanpa sinyal.
     */
    fun prefetchFullTrack(
        context: Context,
        streamUrl: String,
        key: String,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        if (streamUrl.isBlank() || key.isBlank()) return false
        val cache = getCache(context)
        if (isTrackCached(context, key)) return true

        return try {
            val dataSpec = DataSpec.Builder()
                .setUri(Uri.parse(streamUrl))
                .setKey(key)
                .build()
            val cacheDataSource = createCacheDataSource(context)
            val buffer = ByteArray(64 * 1024)
            val writer = CacheWriter(cacheDataSource, dataSpec, buffer) { requestLength, bytesCached, _ ->
                if (requestLength > 0) {
                    val progress = (bytesCached.toFloat() / requestLength.toFloat()).coerceIn(0f, 1f)
                    onProgress?.invoke(progress)
                }
            }
            writer.cache()
            Log.d("CRANKR_CACHE", "Prefetch utuh trek berikutnya berhasil ke SimpleCache: key $key")
            true
        } catch (e: Exception) {
            Log.w("CRANKR_CACHE", "Prefetch full track via CacheWriter berhenti/gagal: ${e.message}")
            false
        }
    }

    /**
     * Memeriksa apakah audio dengan key/videoId tertentu sudah tersimpan di SimpleCache disk.
     */
    fun isTrackCached(context: Context, key: String): Boolean {
        if (key.isBlank()) return false
        return try {
            val cache = getCache(context)
            val spans = cache.getCachedSpans(key)
            spans.isNotEmpty() && spans.any { it.isCached && it.length > 50_000L }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Mengambil berkas lokal audio langsung dari SimpleCache disk jika sudah tersimpan di disk.
     */
    fun getCachedDiskFile(context: Context, key: String): File? {
        if (key.isBlank()) return null
        return try {
            val cache = getCache(context)
            val spans = cache.getCachedSpans(key)
            val spanWithFile = spans.firstOrNull { span ->
                val f = span.file
                span.isCached && f != null && f.exists() && f.length() > 50_000L
            }
            spanWithFile?.file
        } catch (_: Exception) {
            null
        }
    }
}
