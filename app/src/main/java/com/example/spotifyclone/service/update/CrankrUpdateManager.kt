package com.example.spotifyclone.service.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Model metadata pembaruan aplikasi Crankr.
 */
data class CrankrUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val mandatory: Boolean = false
)

/**
 * Representasi status pembaruan aplikasi (OTA State).
 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val info: CrankrUpdateInfo) : UpdateState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateState()
    data class ReadyToInstall(val apkFile: File, val info: CrankrUpdateInfo) : UpdateState()
    data class UpToDate(val currentVersion: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/**
 * CrankrUpdateManager:
 * Modul pembaruan mandiri (In-App OTA Auto-Updater) bergaya industrial punk rock.
 * Memeriksa versi rilis terbaru via GitHub/CDN, mengunduh APK di latar belakang,
 * dan memicu proses instalasi resmi Android via FileProvider.
 */
class CrankrUpdateManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var activeUpdateInfo: CrankrUpdateInfo? = null

    fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (_: Exception) {
            64
        }
    }

    fun getCurrentVersionName(): String {
        return try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.64"
        } catch (_: Exception) {
            "1.64"
        }
    }

    companion object {
        @Volatile
        private var instance: CrankrUpdateManager? = null

        fun getInstance(context: Context): CrankrUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: CrankrUpdateManager(context.applicationContext).also { instance = it }
            }
        }

        // Endpoint metadata rilis resmi Crankr
        private const val DEFAULT_VERSION_URL = "https://raw.githubusercontent.com/astrojustakid/Crankr/main/version.json"
        private const val GITHUB_RELEASES_API = "https://api.github.com/repos/astrojustakid/Crankr/releases/latest"
    }

    /**
     * Memeriksa ketersediaan pembaruan ke server secara asinkron.
     * @param isManualCheck true jika dipicu manual oleh pengguna dari menu Pengaturan.
     */
    fun checkForUpdates(isManualCheck: Boolean = false) {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) {
            return
        }

        _updateState.value = UpdateState.Checking

        scope.launch {
            try {
                val info = fetchLatestVersionInfo()
                if (info != null && info.versionCode > getCurrentVersionCode()) {
                    activeUpdateInfo = info
                    _updateState.value = UpdateState.UpdateAvailable(info)
                } else {
                    if (isManualCheck) {
                        _updateState.value = UpdateState.UpToDate(getCurrentVersionName())
                    } else {
                        _updateState.value = UpdateState.Idle
                    }
                }
            } catch (e: Exception) {
                if (isManualCheck) {
                    _updateState.value = UpdateState.Error("Gagal memeriksa versi: ${e.localizedMessage ?: "Koneksi bermasalah"}")
                } else {
                    _updateState.value = UpdateState.Idle
                }
            }
        }
    }

    /**
     * Mengambil informasi versi dari remote endpoint dengan fallback cerdas.
     */
    private suspend fun fetchLatestVersionInfo(): CrankrUpdateInfo? = withContext(Dispatchers.IO) {
        // Coba 1: Remote version.json langsung
        val directInfo = fetchFromRawVersionJson(DEFAULT_VERSION_URL)
        if (directInfo != null) return@withContext directInfo

        // Coba 2: GitHub Releases API
        return@withContext fetchFromGitHubReleases(GITHUB_RELEASES_API)
    }

    private fun fetchFromRawVersionJson(urlStr: String): CrankrUpdateInfo? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "Crankr-Music-App/${getCurrentVersionName()}")
                requestMethod = "GET"
            }

            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonText)
                val code = obj.optInt("versionCode", 0)
                val name = obj.optString("versionName", "")
                val downloadUrl = obj.optString("downloadUrl", "")
                val changelog = obj.optString("changelog", "Peningkatan performa dan stabilitas aplikasi.")
                val mandatory = obj.optBoolean("mandatory", false)

                if (code > 0 && downloadUrl.isNotBlank()) {
                    CrankrUpdateInfo(
                        versionCode = code,
                        versionName = name.ifBlank { "v$code" },
                        downloadUrl = downloadUrl,
                        changelog = changelog,
                        mandatory = mandatory
                    )
                } else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchFromGitHubReleases(apiUrl: String): CrankrUpdateInfo? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(apiUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "Crankr-Music-App/${getCurrentVersionName()}")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                requestMethod = "GET"
            }

            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonText)
                val tagName = obj.optString("tag_name", "").removePrefix("v").trim()
                val body = obj.optString("body", "Pembaruan rilis Crankr terbaru.")

                // Cari asset APK di dalam release
                val assets = obj.optJSONArray("assets")
                var apkDownloadUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                // Ekstrak perkiraan version code dari tagName atau angka versi
                val parts = tagName.split(".")
                val parsedCode = if (parts.size >= 2) {
                    val major = parts[0].toIntOrNull() ?: 1
                    val minor = parts[1].toIntOrNull() ?: 0
                    (major * 100) + minor
                } else 0

                if (apkDownloadUrl.isNotBlank()) {
                    CrankrUpdateInfo(
                        versionCode = parsedCode,
                        versionName = if (tagName.isNotBlank()) "v$tagName" else "Versi Baru",
                        downloadUrl = apkDownloadUrl,
                        changelog = body
                    )
                } else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Memulai unduhan APK pembaruan di latar belakang dan memperbarui progress secara riil.
     */
    fun startDownload(info: CrankrUpdateInfo) {
        if (_updateState.value is UpdateState.Downloading) return
        activeUpdateInfo = info

        downloadJob?.cancel()
        downloadJob = scope.launch {
            _updateState.value = UpdateState.Downloading(0.01f, 0L, 0L)
            var finalConn: HttpURLConnection? = null
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
                val targetApk = File(updatesDir, "crankr_${info.versionCode}.apk")
                val tempApk = File(updatesDir, "crankr_${info.versionCode}.tmp")

                // Jika file APK versi ini sudah selesai diunduh sebelumnya dan utuh & valid, langsung siap instal
                if (targetApk.exists() && targetApk.length() > 5_000_000L) {
                    val existingPkg = appContext.packageManager.getPackageArchiveInfo(targetApk.absolutePath, 0)
                    if (existingPkg != null && existingPkg.packageName == appContext.packageName) {
                        _updateState.value = UpdateState.ReadyToInstall(targetApk, info)
                        return@launch
                    } else {
                        targetApk.delete()
                    }
                }

                var currentUrl = info.downloadUrl
                var redirectCount = 0
                val maxRedirects = 8

                while (redirectCount < maxRedirects) {
                    val u = URL(currentUrl)
                    val conn = (u.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        setRequestProperty("User-Agent", "Crankr-Music-App/${getCurrentVersionName()}")
                        setRequestProperty("Accept", "*/*")
                        instanceFollowRedirects = false
                        requestMethod = "GET"
                    }
                    val code = conn.responseCode
                    if (code in 301..308) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (location.isNullOrBlank()) {
                            throw java.io.IOException("Redirect received without Location header")
                        }
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(u, location).toString()
                        }
                        redirectCount++
                    } else if (code == 200) {
                        finalConn = conn
                        break
                    } else {
                        conn.disconnect()
                        throw java.io.IOException("Gagal mengunduh (HTTP $code: ${conn.responseMessage})")
                    }
                }

                if (finalConn == null) {
                    throw java.io.IOException("Gagal terhubung ke server unduhan (terlalu banyak redirect).")
                }

                val totalBytes = finalConn.contentLengthLong
                inputStream = finalConn.inputStream
                outputStream = FileOutputStream(tempApk)

                val buffer = ByteArray(16 * 1024)
                var downloadedBytes = 0L
                var bytesRead: Int
                var lastProgressEmit = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressEmit > 100 || downloadedBytes == totalBytes) {
                        lastProgressEmit = now
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0.5f
                        _updateState.value = UpdateState.Downloading(
                            progress = progress.coerceIn(0f, 1f),
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes
                        )
                    }
                }

                outputStream.flush()
                outputStream.close()
                outputStream = null
                inputStream.close()
                inputStream = null
                finalConn.disconnect()
                finalConn = null

                // Validasi integritas berkas unduhan
                if (!tempApk.exists() || tempApk.length() < 5_000_000L) {
                    tempApk.delete()
                    throw java.io.IOException("Ukuran file APK tidak valid (${tempApk.length()} bytes). Unduhan belum lengkap.")
                }

                // Validasi keabsahan paket APK Android
                val packageInfo = appContext.packageManager.getPackageArchiveInfo(tempApk.absolutePath, 0)
                if (packageInfo == null) {
                    tempApk.delete()
                    throw java.io.IOException("File APK yang diunduh rusak atau gagal diverifikasi oleh Android.")
                }

                if (packageInfo.packageName != appContext.packageName) {
                    tempApk.delete()
                    throw java.io.IOException("Identitas paket APK tidak cocok (${packageInfo.packageName} != ${appContext.packageName}).")
                }

                val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }

                if (apkVersionCode <= getCurrentVersionCode()) {
                    tempApk.delete()
                    throw java.io.IOException("Versi APK yang diunduh (v${packageInfo.versionName ?: apkVersionCode}) tidak lebih baru dari aplikasi yang terpasang.")
                }

                // Ganti nama dari file tmp ke apk target
                if (targetApk.exists()) targetApk.delete()
                if (!tempApk.renameTo(targetApk)) {
                    tempApk.copyTo(targetApk, overwrite = true)
                    tempApk.delete()
                }

                _updateState.value = UpdateState.ReadyToInstall(targetApk, info)

            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Gagal mengunduh berkas pembaruan.")
            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                try { inputStream?.close() } catch (_: Exception) {}
                try { finalConn?.disconnect() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Membuka layar instalasi paket OS Android menggunakan FileProvider resmi.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() < 5_000_000L) {
                _updateState.value = UpdateState.Error("File APK tidak ditemukan atau tidak lengkap. Silakan unduh ulang.")
                return
            }

            // Validasi ulang integritas berkas sebelum memanggil PackageInstaller sistem
            val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (packageInfo == null) {
                apkFile.delete()
                _updateState.value = UpdateState.Error("File APK rusak. Silakan unduh ulang.")
                return
            }

            // Android 8.0+ izin instalasi aplikasi tidak dikenal
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // Berikan izin akses URI secara eksplisit ke seluruh resolver PackageInstaller di perangkat
            val resolvedActivities = context.packageManager.queryIntentActivities(installIntent, 0)
            for (resolveInfo in resolvedActivities) {
                val installerPkg = resolveInfo.activityInfo.packageName
                context.grantUriPermission(installerPkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)

        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Gagal memicu instalasi: ${e.localizedMessage}")
        }
    }

    /**
     * Menutup atau mengabaikan dialog update saat ini (kembali ke Idle).
     */
    fun dismissUpdate() {
        downloadJob?.cancel()
        _updateState.value = UpdateState.Idle
    }
}
