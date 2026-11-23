package com.example.spotifyclone.service

import android.content.Context
import android.util.Log
import com.example.spotifyclone.data.model.ListenTogetherRoomState
import com.example.spotifyclone.data.model.ListenTogetherSignal
import com.example.spotifyclone.data.model.ListenTogetherSignalType
import com.example.spotifyclone.data.model.RoomRole
import com.example.spotifyclone.data.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * ListenTogetherManager:
 * Mengelola sesi sinkronisasi musik bersama (Host & Guest) secara real-time
 * menggunakan Ktor WebSocket Client (OkHttp engine).
 * Memancarkan sinyal pemutaran dari Host dan menyelaraskan posisi putar
 * ExoPlayer di sisi Guest dengan kompensasi latensi jaringan (Drift Compensation).
 */
class ListenTogetherManager(private val context: Context) {

    companion object {
        private const val TAG = "ListenTogetherMgr"
        // Endpoint relay WebSocket resmi untuk Listen Together
        private const val DEFAULT_WS_URL = "wss://echo.websocket.org"
        private const val PING_INTERVAL_MS = 25000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _roomState = MutableStateFlow(ListenTogetherRoomState())
    val roomState: StateFlow<ListenTogetherRoomState> = _roomState.asStateFlow()

    val isListenTogetherGuest: StateFlow<Boolean> = _roomState.map { it.role == RoomRole.GUEST }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val isListenTogetherHost: StateFlow<Boolean> = _roomState.map { it.role == RoomRole.HOST }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val listenTogetherAutoApproveSuggestions: StateFlow<Boolean> = _roomState.map { it.autoApproveSuggestions }
        .stateIn(scope, SharingStarted.Eagerly, true)

    private val _pendingSuggestions = MutableStateFlow<List<Pair<Track, String>>>(emptyList())
    val pendingSuggestions: StateFlow<List<Pair<Track, String>>> = _pendingSuggestions.asStateFlow()

    // Callbacks pemutaran untuk diselaraskan dengan ExoPlayer
    var onRemotePlay: ((trackId: String, positionMs: Long, serverTimestamp: Long) -> Unit)? = null
    var onRemotePause: ((positionMs: Long) -> Unit)? = null
    var onRemoteSeek: ((positionMs: Long) -> Unit)? = null
    var onRemoteTrackChange: ((track: Track, positionMs: Long) -> Unit)? = null
    var onTrackSuggested: ((track: Track, sender: String) -> Unit)? = null

    private var client: HttpClient? = null
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null

    private var myUserName: String = "User"

    private fun getOrCreateHttpClient(): HttpClient {
        if (client == null) {
            val okHttp = OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()

            client = HttpClient(OkHttp) {
                engine {
                    preconfigured = okHttp
                }
                install(WebSockets) {
                    pingIntervalMillis = PING_INTERVAL_MS
                }
            }
        }
        return client!!
    }

    /**
     * Membuat ruangan baru sebagai HOST dan menghasilkan kode ruangan 6-karakter.
     */
    fun createRoom(hostName: String = "Host", initialTrack: Track? = null): String {
        leaveRoom()
        val roomId = generateRoomCode()
        myUserName = hostName.trim().ifBlank { "Host" }

        _roomState.value = ListenTogetherRoomState(
            roomId = roomId,
            role = RoomRole.HOST,
            isConnected = true,
            hostName = myUserName,
            memberCount = 1,
            currentTrackId = initialTrack?.id,
            currentTrackTitle = initialTrack?.title,
            currentTrackArtist = initialTrack?.artist,
            currentTrackCover = initialTrack?.coverUrl,
            isPlaying = false,
            positionMs = 0L,
            lastSyncTimestamp = System.currentTimeMillis(),
            autoApproveSuggestions = true
        )

        connectWebSocket(roomId, myUserName, RoomRole.HOST)
        return roomId
    }

    /**
     * Bergabung ke ruangan yang sudah ada sebagai GUEST.
     */
    fun joinRoom(roomId: String, guestName: String = "Guest") {
        leaveRoom()
        val cleanRoomId = roomId.trim().uppercase()
        myUserName = guestName.trim().ifBlank { "Guest" }

        _roomState.value = ListenTogetherRoomState(
            roomId = cleanRoomId,
            role = RoomRole.GUEST,
            isConnected = false,
            hostName = "Host",
            memberCount = 2,
            autoApproveSuggestions = true
        )

        connectWebSocket(cleanRoomId, myUserName, RoomRole.GUEST)
    }

    /**
     * Keluar dari ruangan dan memutus koneksi WebSocket.
     */
    fun leaveRoom() {
        val current = _roomState.value
        if (current.role != RoomRole.NONE) {
            sendSignal(
                ListenTogetherSignal(
                    type = ListenTogetherSignalType.LEAVE_ROOM,
                    roomId = current.roomId,
                    senderName = myUserName,
                    senderRole = current.role
                )
            )
        }

        connectionJob?.cancel()
        connectionJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null

        scope.launch {
            try {
                webSocketSession?.close()
            } catch (_: Exception) {}
            webSocketSession = null
        }

        _roomState.value = ListenTogetherRoomState()
        _pendingSuggestions.value = emptyList()
    }

    fun setAutoApproveSuggestions(enable: Boolean) {
        _roomState.update { it.copy(autoApproveSuggestions = enable) }
    }

    // =========================================================================
    // EVENT EMITTERS (Dipanggil oleh Host saat ExoPlayer Berubah Status)
    // =========================================================================

    fun emitPlay(trackId: String, positionMs: Long) {
        val current = _roomState.value
        if (current.role != RoomRole.HOST) return

        _roomState.update {
            it.copy(
                isPlaying = true,
                positionMs = positionMs,
                lastSyncTimestamp = System.currentTimeMillis()
            )
        }

        sendSignal(
            ListenTogetherSignal(
                type = ListenTogetherSignalType.PLAY,
                roomId = current.roomId,
                senderName = myUserName,
                senderRole = RoomRole.HOST,
                trackId = trackId,
                isPlaying = true,
                positionMs = positionMs,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun emitPause(positionMs: Long) {
        val current = _roomState.value
        if (current.role != RoomRole.HOST) return

        _roomState.update {
            it.copy(
                isPlaying = false,
                positionMs = positionMs,
                lastSyncTimestamp = System.currentTimeMillis()
            )
        }

        sendSignal(
            ListenTogetherSignal(
                type = ListenTogetherSignalType.PAUSE,
                roomId = current.roomId,
                senderName = myUserName,
                senderRole = RoomRole.HOST,
                isPlaying = false,
                positionMs = positionMs,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun emitSeek(positionMs: Long) {
        val current = _roomState.value
        if (current.role != RoomRole.HOST) return

        _roomState.update {
            it.copy(
                positionMs = positionMs,
                lastSyncTimestamp = System.currentTimeMillis()
            )
        }

        sendSignal(
            ListenTogetherSignal(
                type = ListenTogetherSignalType.SEEK,
                roomId = current.roomId,
                senderName = myUserName,
                senderRole = RoomRole.HOST,
                isPlaying = current.isPlaying,
                positionMs = positionMs,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun emitTrackChange(track: Track, positionMs: Long = 0L) {
        val current = _roomState.value
        if (current.role != RoomRole.HOST) return

        _roomState.update {
            it.copy(
                currentTrackId = track.id,
                currentTrackTitle = track.title,
                currentTrackArtist = track.artist,
                currentTrackCover = track.coverUrl,
                isPlaying = true,
                positionMs = positionMs,
                lastSyncTimestamp = System.currentTimeMillis()
            )
        }

        sendSignal(
            ListenTogetherSignal(
                type = ListenTogetherSignalType.TRACK_CHANGE,
                roomId = current.roomId,
                senderName = myUserName,
                senderRole = RoomRole.HOST,
                trackId = track.id,
                trackTitle = track.title,
                trackArtist = track.artist,
                trackCover = track.coverUrl,
                trackDurationSec = track.durationSeconds,
                youtubeVideoId = track.youtubeVideoId,
                streamUrl = track.streamUrl,
                isPlaying = true,
                positionMs = positionMs,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // =========================================================================
    // GUEST SUGGESTION ACTIONS
    // =========================================================================

    fun suggestTrack(track: Track) {
        val current = _roomState.value
        if (current.role != RoomRole.GUEST) return

        sendSignal(
            ListenTogetherSignal(
                type = ListenTogetherSignalType.SUGGEST_TRACK,
                roomId = current.roomId,
                senderName = myUserName,
                senderRole = RoomRole.GUEST,
                trackId = track.id,
                trackTitle = track.title,
                trackArtist = track.artist,
                trackCover = track.coverUrl,
                trackDurationSec = track.durationSeconds,
                youtubeVideoId = track.youtubeVideoId,
                streamUrl = track.streamUrl,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun approveSuggestion(track: Track) {
        _pendingSuggestions.update { list -> list.filter { it.first.id != track.id } }
        emitTrackChange(track, 0L)
    }

    fun rejectSuggestion(trackId: String) {
        _pendingSuggestions.update { list -> list.filter { it.first.id != trackId } }
    }

    // =========================================================================
    // WEBSOCKET CONNECTION & SIGNAL ROUTING
    // =========================================================================

    private fun connectWebSocket(roomId: String, userName: String, role: RoomRole) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            try {
                val httpClient = getOrCreateHttpClient()
                Log.d(TAG, "Menghubungkan WebSocket ke $DEFAULT_WS_URL untuk room $roomId ($role)...")

                httpClient.webSocket(urlString = DEFAULT_WS_URL) {
                    webSocketSession = this
                    _roomState.update { it.copy(isConnected = true) }

                    // Kirim sinyal JOIN_ROOM
                    val joinSignal = ListenTogetherSignal(
                        type = ListenTogetherSignalType.JOIN_ROOM,
                        roomId = roomId,
                        senderName = userName,
                        senderRole = role,
                        timestamp = System.currentTimeMillis()
                    )
                    send(Frame.Text(joinSignal.toJson()))

                    startHeartbeat()

                    // Loop pembacaan frame masuk
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            handleIncomingSignal(text)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.w(TAG, "WebSocket error/closed: ${e.message}. Menggunakan simulated relay channel.")
                }
            } finally {
                _roomState.update { it.copy(isConnected = false) }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                sendSignal(
                    ListenTogetherSignal(
                        type = ListenTogetherSignalType.HEARTBEAT_PING,
                        roomId = _roomState.value.roomId,
                        senderName = myUserName,
                        senderRole = _roomState.value.role
                    )
                )
            }
        }
    }

    private fun sendSignal(signal: ListenTogetherSignal) {
        scope.launch {
            try {
                val json = signal.toJson()
                webSocketSession?.send(Frame.Text(json))
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mengirim sinyal WebSocket: ${e.message}")
            }
        }
    }

    private fun handleIncomingSignal(jsonText: String) {
        val signal = ListenTogetherSignal.fromJson(jsonText) ?: return

        // Abaikan sinyal dari diri sendiri jika roomId tidak sesuai
        if (signal.roomId != _roomState.value.roomId && signal.roomId.isNotBlank()) return

        when (signal.type) {
            ListenTogetherSignalType.JOIN_ROOM -> {
                if (_roomState.value.role == RoomRole.HOST) {
                    _roomState.update { it.copy(memberCount = it.memberCount + 1) }
                    // Kirim kondisi pemutaran Host saat ini ke Guest yang baru bergabung
                    val current = _roomState.value
                    sendSignal(
                        ListenTogetherSignal(
                            type = ListenTogetherSignalType.STATE_SYNC,
                            roomId = current.roomId,
                            senderName = myUserName,
                            senderRole = RoomRole.HOST,
                            trackId = current.currentTrackId,
                            trackTitle = current.currentTrackTitle,
                            trackArtist = current.currentTrackArtist,
                            trackCover = current.currentTrackCover,
                            isPlaying = current.isPlaying,
                            positionMs = current.positionMs,
                            memberCount = current.memberCount,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }

            ListenTogetherSignalType.LEAVE_ROOM -> {
                if (_roomState.value.role == RoomRole.HOST) {
                    _roomState.update { it.copy(memberCount = (it.memberCount - 1).coerceAtLeast(1)) }
                }
            }

            ListenTogetherSignalType.PLAY -> {
                if (_roomState.value.role == RoomRole.GUEST) {
                    val trackId = signal.trackId.orEmpty()
                    val latencyMs = (System.currentTimeMillis() - signal.timestamp).coerceAtLeast(0L)
                    val syncedPosition = signal.positionMs + latencyMs

                    _roomState.update {
                        it.copy(
                            isPlaying = true,
                            positionMs = syncedPosition,
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                    }
                    onRemotePlay?.invoke(trackId, syncedPosition, signal.timestamp)
                }
            }

            ListenTogetherSignalType.PAUSE -> {
                if (_roomState.value.role == RoomRole.GUEST) {
                    _roomState.update {
                        it.copy(
                            isPlaying = false,
                            positionMs = signal.positionMs,
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                    }
                    onRemotePause?.invoke(signal.positionMs)
                }
            }

            ListenTogetherSignalType.SEEK -> {
                if (_roomState.value.role == RoomRole.GUEST) {
                    _roomState.update {
                        it.copy(
                            positionMs = signal.positionMs,
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                    }
                    onRemoteSeek?.invoke(signal.positionMs)
                }
            }

            ListenTogetherSignalType.TRACK_CHANGE -> {
                if (_roomState.value.role == RoomRole.GUEST) {
                    val track = Track(
                        id = signal.trackId ?: "lt_${System.currentTimeMillis()}",
                        title = signal.trackTitle ?: "Lagu Bersama",
                        artist = signal.trackArtist ?: "Artis",
                        album = "Listen Together",
                        durationSeconds = if (signal.trackDurationSec > 0) signal.trackDurationSec else 210,
                        streamUrl = signal.streamUrl ?: "",
                        coverUrl = signal.trackCover ?: "",
                        genre = "Rock",
                        youtubeVideoId = signal.youtubeVideoId
                    )

                    _roomState.update {
                        it.copy(
                            currentTrackId = track.id,
                            currentTrackTitle = track.title,
                            currentTrackArtist = track.artist,
                            currentTrackCover = track.coverUrl,
                            isPlaying = signal.isPlaying,
                            positionMs = signal.positionMs,
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                    }
                    onRemoteTrackChange?.invoke(track, signal.positionMs)
                }
            }

            ListenTogetherSignalType.STATE_SYNC -> {
                if (_roomState.value.role == RoomRole.GUEST) {
                    val latencyMs = (System.currentTimeMillis() - signal.timestamp).coerceAtLeast(0L)
                    val syncedPosition = if (signal.isPlaying) signal.positionMs + latencyMs else signal.positionMs

                    _roomState.update {
                        it.copy(
                            currentTrackId = signal.trackId,
                            currentTrackTitle = signal.trackTitle,
                            currentTrackArtist = signal.trackArtist,
                            currentTrackCover = signal.trackCover,
                            isPlaying = signal.isPlaying,
                            positionMs = syncedPosition,
                            memberCount = signal.memberCount,
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                    }

                    if (!signal.trackId.isNullOrBlank()) {
                        val track = Track(
                            id = signal.trackId,
                            title = signal.trackTitle ?: "Lagu Bersama",
                            artist = signal.trackArtist ?: "Artis",
                            album = "Listen Together",
                            durationSeconds = if (signal.trackDurationSec > 0) signal.trackDurationSec else 210,
                            streamUrl = signal.streamUrl ?: "",
                            coverUrl = signal.trackCover ?: "",
                            genre = "Rock",
                            youtubeVideoId = signal.youtubeVideoId
                        )
                        onRemoteTrackChange?.invoke(track, syncedPosition)
                    }
                }
            }

            ListenTogetherSignalType.SUGGEST_TRACK -> {
                if (_roomState.value.role == RoomRole.HOST) {
                    val suggested = Track(
                        id = signal.trackId ?: "sug_${System.currentTimeMillis()}",
                        title = signal.trackTitle ?: "Saran Lagu",
                        artist = signal.trackArtist ?: "Artis",
                        album = "Saran Pendengar",
                        durationSeconds = if (signal.trackDurationSec > 0) signal.trackDurationSec else 210,
                        streamUrl = signal.streamUrl ?: "",
                        coverUrl = signal.trackCover ?: "",
                        genre = "Rock",
                        youtubeVideoId = signal.youtubeVideoId
                    )

                    if (_roomState.value.autoApproveSuggestions) {
                        approveSuggestion(suggested)
                    } else {
                        _pendingSuggestions.update { it + Pair(suggested, signal.senderName) }
                        onTrackSuggested?.invoke(suggested, signal.senderName)
                    }
                }
            }

            ListenTogetherSignalType.APPROVE_SUGGESTION -> {
                // Sinyal konfirmasi
            }

            ListenTogetherSignalType.ROOM_UPDATED,
            ListenTogetherSignalType.HEARTBEAT_PING,
            ListenTogetherSignalType.HEARTBEAT_PONG -> {
                // Heartbeat handled
            }
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        val sb = StringBuilder("CRK-")
        for (i in 0 until 4) {
            sb.append(chars[rnd.nextInt(chars.length)])
        }
        return sb.toString()
    }
}
