package com.example.spotifyclone.service.spotify

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.example.spotifyclone.service.RawSpotifyTrackInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class PathfinderQueryInfo(
    val endpointUrl: String,
    val operationName: String,
    val sha256Hash: String,
    val variablesTemplate: JSONObject? = null,
    val authToken: String = "",
    val clientToken: String? = null
)

object SpotifyExtractor {

    private const val TAG = "SpotifyExtractor"
    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    // SHA-256 Hash query GraphQL Pathfinder resmi Spotify Web Player
    private const val PLAYLIST_SHA256 =
        "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"
    private const val ALBUM_SHA256 =
        "0b6ce3511e1b02e4a40fa95440f992acbe85080eb308af9194322d708fc1914f"

    private var appContext: Context? = null
    private var activityRef: WeakReference<Activity>? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (context is Activity) {
            activityRef = WeakReference(context)
        }
    }

    private fun getAvailableContext(): Context? {
        val act = activityRef?.get()
        if (act != null && !act.isFinishing && !act.isDestroyed) {
            return act
        }
        if (appContext != null) {
            return appContext
        }
        return try {
            val appThreadClass = Class.forName("android.app.ActivityThread")
            val currentAppMethod = appThreadClass.getMethod("currentApplication")
            val app = currentAppMethod.invoke(null) as? Application
            if (app != null) {
                appContext = app
            }
            app
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Mengambil accessToken tamu resmi dan metadata awal langsung dari halaman embed publik Spotify.
     * Sangat cepat (~200ms) tanpa dependensi antarmuka.
     */
    private suspend fun fetchEmbedSession(entityType: String, entityId: String): Triple<String?, String?, String?> = withContext(Dispatchers.IO) {
        try {
            val embedUrl = "https://open.spotify.com/embed/$entityType/$entityId"
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val html = res.body?.string() ?: ""
                    var token: String? = null
                    var title: String? = null
                    var cover: String? = null

                    // 1. Ekstrak accessToken dari session JSON
                    val tokenMatch = Regex(""""accessToken":\s*"([^"]+)"""").find(html)
                    if (tokenMatch != null) {
                        token = tokenMatch.groupValues[1]
                    }

                    // 2. Ekstrak judul dan cover dari __NEXT_DATA__
                    val nextDataMatch = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)
                    if (nextDataMatch != null) {
                        try {
                            val json = JSONObject(nextDataMatch.groupValues[1])
                            val entity = json.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
                            if (entity != null) {
                                title = entity.optString("title", entity.optString("name", "")).trim()
                                val visualIdentity = entity.optJSONObject("visualIdentity")
                                val imgArr = visualIdentity?.optJSONArray("image")
                                if (imgArr != null && imgArr.length() > 0) {
                                    cover = imgArr.optJSONObject(imgArr.length() - 1)?.optString("url", "")
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    return@withContext Triple(token, title, cover)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mengambil sesi embed: ${e.message}")
        }
        Triple(null, null, null)
    }

    /**
     * Alur ekstraksi utama playlist / album:
     * 1. Mengambil sesi resmi Spotify Web.
     * 2. Menjalankan pagination penuh via Pathfinder GraphQL API (Bebas batas 100 lagu, mampu menarik seluruh lagu hingga ribuan).
     * 3. Jika gagal, otomatis fallback ke Headless WebView / REST API.
     */
    suspend fun extractPlaylist(
        entityType: String,
        entityId: String,
        onProgress: ((playlistName: String, current: Int, total: Int) -> Unit)? = null
    ): ExtractedPlaylistTextData? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Memulai ekstraksi Spotify untuk $entityType/$entityId")

        // 1. Ambil token sesi resmi dari embed Spotify
        val (embedToken, embedTitle, embedCover) = fetchEmbedSession(entityType, entityId)

        if (!embedToken.isNullOrBlank()) {
            Log.d(TAG, "Token sesi Spotify berhasil didapat: ${embedToken.take(15)}...")

            // Jalankan looping pagination penuh menggunakan Pathfinder GraphQL
            val pathfinderData = queryPathfinderPagination(
                entityType = entityType,
                entityId = entityId,
                token = embedToken,
                fallbackTitle = embedTitle,
                fallbackCover = embedCover,
                onProgress = onProgress
            )

            if (pathfinderData != null && pathfinderData.tracks.isNotEmpty()) {
                Log.d(TAG, "SUKSES: Berhasil mengekstrak ${pathfinderData.tracks.size} lagu penuh tanpa batas 100 lagu!")
                return@withContext pathfinderData
            }
        }

        // 2. Jalur Cadangan (Fallback): Headless WebView intersep jika token embed gagal
        Log.w(TAG, "Menjalankan jalur cadangan Headless WebView Desktop Mode...")
        val webViewData = extractViaHeadlessWebView(entityType, entityId, onProgress)
        if (webViewData != null && webViewData.tracks.isNotEmpty()) {
            return@withContext webViewData
        }

        null
    }

    /**
     * Mesin Pagination Tanpa Batas menggunakan Pathfinder GraphQL resmi Spotify.
     * Mendukung offset 0, 100, 200, dst. Mengambil seluruh lagu tanpa terkunci di angka 100.
     */
    private suspend fun queryPathfinderPagination(
        entityType: String,
        entityId: String,
        token: String,
        fallbackTitle: String?,
        fallbackCover: String?,
        onProgress: ((playlistName: String, current: Int, total: Int) -> Unit)?
    ): ExtractedPlaylistTextData? = withContext(Dispatchers.IO) {
        var playlistTitle = fallbackTitle?.ifBlank { null } ?: if (entityType == "album") "Spotify Album" else "Spotify Playlist"
        var playlistSubtitle = ""
        var coverUrl = fallbackCover?.ifBlank { null } ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=60"
        val textTracks = mutableListOf<RawSpotifyTrackInfo>()

        var offset = 0
        val limit = 100
        var totalTracks = -1
        val isAlbum = entityType == "album"
        val operationName = if (isAlbum) "getAlbum" else "fetchPlaylist"
        val sha256 = if (isAlbum) ALBUM_SHA256 else PLAYLIST_SHA256

        while (totalTracks == -1 || textTracks.size < totalTracks) {
            val varsJson = JSONObject().apply {
                put("uri", "spotify:$entityType:$entityId")
                put("offset", offset)
                put("limit", limit)
                if (!isAlbum) {
                    put("enableWatchFeedEntrypoint", false)
                }
            }

            val extJson = JSONObject().apply {
                put("persistedQuery", JSONObject().apply {
                    put("version", 1)
                    put("sha256Hash", sha256)
                })
            }

            val encodedVars = URLEncoder.encode(varsJson.toString(), "UTF-8")
            val encodedExt = URLEncoder.encode(extJson.toString(), "UTF-8")
            val queryUrl = "https://api-partner.spotify.com/pathfinder/v1/query?operationName=$operationName&variables=$encodedVars&extensions=$encodedExt"

            val req = Request.Builder()
                .url(queryUrl)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Origin", "https://open.spotify.com")
                .header("Referer", "https://open.spotify.com/")
                .header("Accept", "application/json")
                .build()

            var shouldBreak = false
            try {
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        val json = JSONObject(body)

                        val parsed = parsePathfinderResponse(json, entityType)
                        if (!parsed.third.isNullOrBlank()) {
                            playlistTitle = parsed.third!!
                        }

                        if (totalTracks == -1 && parsed.second > 0) {
                            totalTracks = parsed.second
                            // Kirim angka total asli seketika agar UI dialog loading langsung terbarui (misal IMPORTING 0 OF 104)
                            Log.d(TAG, "Total lagu sebenarnya terdeteksi: $totalTracks lagu")
                            onProgress?.invoke(playlistTitle, 0, totalTracks)
                        }

                        val newTracks = parsed.first
                        if (newTracks.isEmpty()) {
                            shouldBreak = true
                            return@use
                        }

                        textTracks.addAll(newTracks)
                        Log.d("CrankrImport", "Pathfinder: Berhasil menarik ${textTracks.size} dari $totalTracks lagu")
                        onProgress?.invoke(
                            playlistTitle,
                            textTracks.size,
                            if (totalTracks > 0) totalTracks else textTracks.size
                        )

                        if (totalTracks > 0 && textTracks.size >= totalTracks) {
                            shouldBreak = true
                            return@use
                        }

                        offset += limit
                    } else {
                        Log.w(TAG, "Pathfinder HTTP ${res.code} pada offset $offset")
                        shouldBreak = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error saat memanggil Pathfinder pada offset $offset: ${e.message}")
                shouldBreak = true
            }

            if (shouldBreak) break
            delay(100)
        }

        if (textTracks.isEmpty()) return@withContext null

        ExtractedPlaylistTextData(
            id = entityId,
            name = playlistTitle,
            description = playlistSubtitle,
            coverUrl = coverUrl,
            tracks = textTracks
        )
    }

    /**
     * Parser universal untuk struktur respons Pathfinder GraphQL maupun REST API Spotify.
     * Mengambil nama lagu, artis gabungan, nama album asli (tanpa ditimpa nama playlist), dan total hitungan lagu.
     */
    private fun parsePathfinderResponse(
        json: JSONObject,
        entityType: String
    ): Triple<List<RawSpotifyTrackInfo>, Int, String?> {
        val tracks = mutableListOf<RawSpotifyTrackInfo>()
        var totalCount = -1
        var playlistTitle: String? = null

        val dataObj = json.optJSONObject("data") ?: json

        val entityObj = dataObj.optJSONObject("playlistV2")
            ?: dataObj.optJSONObject("playlist")
            ?: dataObj.optJSONObject("albumUnion")
            ?: dataObj.optJSONObject("album")

        if (entityObj != null) {
            val name = entityObj.optString("name", "").trim()
            if (name.isNotBlank()) playlistTitle = name

            val contentObj = entityObj.optJSONObject("content")
                ?: entityObj.optJSONObject("tracks")
                ?: entityObj

            totalCount = contentObj.optInt("totalCount", -1)
            if (totalCount == -1) {
                totalCount = contentObj.optInt("total", -1)
            }

            val itemsArr = contentObj.optJSONArray("items")
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val item = itemsArr.optJSONObject(i) ?: continue

                    val itemV2 = item.optJSONObject("itemV2")
                    val trackData = itemV2?.optJSONObject("data")
                        ?: item.optJSONObject("track")
                        ?: item.optJSONObject("data")
                        ?: item

                    val trackTitle = trackData.optString("name", "").trim()
                    if (trackTitle.isBlank()) continue

                    val artistNames = mutableListOf<String>()
                    val artistsObj = trackData.optJSONObject("artists")
                    val artistsItems = artistsObj?.optJSONArray("items")
                    if (artistsItems != null) {
                        for (a in 0 until artistsItems.length()) {
                            val aItem = artistsItems.optJSONObject(a)
                            val aName = aItem?.optJSONObject("profile")?.optString("name", "")
                                ?: aItem?.optString("name", "")
                            if (!aName.isNullOrBlank()) artistNames.add(aName.trim())
                        }
                    } else {
                        val artistsArr = trackData.optJSONArray("artists")
                        if (artistsArr != null) {
                            for (a in 0 until artistsArr.length()) {
                                val aName = artistsArr.optJSONObject(a)?.optString("name", "")
                                if (!aName.isNullOrBlank()) artistNames.add(aName.trim())
                            }
                        }
                    }
                    val trackArtist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unknown Artist"

                    // Ambil nama album asli secara ketat (JANGAN menimpa nama album asli dengan nama playlist)
                    val albumObj = trackData.optJSONObject("albumOfTrack") ?: trackData.optJSONObject("album")
                    val albumName = if (entityType == "album") {
                        playlistTitle ?: ""
                    } else {
                        albumObj?.optString("name", "")?.trim() ?: ""
                    }

                    Log.d("CrankrImport", "Lagu: $trackTitle | Album: $albumName")
                    tracks.add(RawSpotifyTrackInfo(trackTitle, trackArtist, albumName))
                }
            }
        }

        // Fallback untuk array items level utama jika ada
        if (tracks.isEmpty()) {
            val itemsArr = json.optJSONArray("items") ?: dataObj.optJSONArray("items")
            if (itemsArr != null) {
                totalCount = json.optInt("total", -1)
                for (i in 0 until itemsArr.length()) {
                    val item = itemsArr.optJSONObject(i) ?: continue
                    val trackObj = if (entityType == "playlist") {
                        if (item.isNull("track") || !item.has("track")) continue
                        item.optJSONObject("track") ?: continue
                    } else item

                    val trackTitle = trackObj.optString("name", "").trim()
                    if (trackTitle.isBlank()) continue

                    val artistsArr = trackObj.optJSONArray("artists")
                    val artistNames = mutableListOf<String>()
                    if (artistsArr != null) {
                        for (a in 0 until artistsArr.length()) {
                            val aName = artistsArr.optJSONObject(a)?.optString("name", "")?.trim() ?: ""
                            if (aName.isNotBlank()) artistNames.add(aName)
                        }
                    }
                    val trackArtist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unknown Artist"

                    val albumObj = trackObj.optJSONObject("album")
                    val albumName = if (entityType == "album") (playlistTitle ?: "") else albumObj?.optString("name", "")?.trim() ?: ""

                    Log.d("CrankrImport", "Lagu: $trackTitle | Album: $albumName")
                    tracks.add(RawSpotifyTrackInfo(trackTitle, trackArtist, albumName))
                }
            }
        }

        return Triple(tracks, totalCount, playlistTitle)
    }

    /**
     * Jalur cadangan menggunakan Headless WebView jika dibutuhkan.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractViaHeadlessWebView(
        entityType: String,
        entityId: String,
        onProgress: ((playlistName: String, current: Int, total: Int) -> Unit)?
    ): ExtractedPlaylistTextData? {
        val targetUrl = "https://open.spotify.com/$entityType/$entityId"
        var capturedToken: String? = null
        val sessionReadyDeferred = CompletableDeferred<Boolean>()
        var webView: WebView? = null

        try {
            withContext(Dispatchers.Main) {
                val context = getAvailableContext() ?: return@withContext
                try {
                    webView = WebView(context).apply {
                        visibility = View.GONE
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = DESKTOP_USER_AGENT
                            loadsImagesAutomatically = false
                            blockNetworkImage = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val auth = request?.requestHeaders?.entries?.firstOrNull {
                                    it.key.equals("Authorization", ignoreCase = true)
                                }?.value

                                if (!auth.isNullOrBlank() && auth.startsWith("Bearer ", ignoreCase = true)) {
                                    val token = auth.substring(7).trim()
                                    if (token.isNotBlank() && token != "null") {
                                        capturedToken = token
                                        if (!sessionReadyDeferred.isCompleted) {
                                            sessionReadyDeferred.complete(true)
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        loadUrl(targetUrl)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal membuat fallback WebView: ${e.message}")
                }
            }

            withTimeoutOrNull(10000L) {
                sessionReadyDeferred.await()
            }
        } finally {
            withContext(Dispatchers.Main) {
                try {
                    webView?.stopLoading()
                    webView?.destroy()
                } catch (_: Exception) {
                } finally {
                    webView = null
                }
            }
        }

        val token = capturedToken
        if (!token.isNullOrBlank()) {
            return queryPathfinderPagination(entityType, entityId, token, null, null, onProgress)
        }

        return null
    }
}
