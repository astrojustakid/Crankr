package com.example.spotifyclone.data.model

import org.json.JSONObject

/**
 * Peran pengguna dalam ruangan Listen Together (Putar Bersama).
 */
enum class RoomRole {
    HOST,
    GUEST,
    NONE
}

/**
 * Model status ruangan real-time Listen Together.
 */
data class ListenTogetherRoomState(
    val roomId: String = "",
    val role: RoomRole = RoomRole.NONE,
    val isConnected: Boolean = false,
    val hostName: String = "",
    val memberCount: Int = 1,
    val currentTrackId: String? = null,
    val currentTrackTitle: String? = null,
    val currentTrackArtist: String? = null,
    val currentTrackCover: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val lastSyncTimestamp: Long = 0L,
    val autoApproveSuggestions: Boolean = true
)

/**
 * Tipe sinyal sinkronisasi antar perangkat melalui WebSocket.
 */
enum class ListenTogetherSignalType {
    JOIN_ROOM,
    LEAVE_ROOM,
    PLAY,
    PAUSE,
    SEEK,
    TRACK_CHANGE,
    STATE_SYNC,
    SUGGEST_TRACK,
    APPROVE_SUGGESTION,
    ROOM_UPDATED,
    HEARTBEAT_PING,
    HEARTBEAT_PONG
}

/**
 * Payload sinyal waktu nyata yang dikirim dan diterima via WebSocket.
 */
data class ListenTogetherSignal(
    val type: ListenTogetherSignalType,
    val roomId: String,
    val senderName: String,
    val senderRole: RoomRole,
    val trackId: String? = null,
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val trackCover: String? = null,
    val trackDurationSec: Int = 0,
    val youtubeVideoId: String? = null,
    val streamUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val memberCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("type", type.name)
            put("roomId", roomId)
            put("senderName", senderName)
            put("senderRole", senderRole.name)
            put("trackId", trackId ?: "")
            put("trackTitle", trackTitle ?: "")
            put("trackArtist", trackArtist ?: "")
            put("trackCover", trackCover ?: "")
            put("trackDurationSec", trackDurationSec)
            put("youtubeVideoId", youtubeVideoId ?: "")
            put("streamUrl", streamUrl ?: "")
            put("isPlaying", isPlaying)
            put("positionMs", positionMs)
            put("memberCount", memberCount)
            put("timestamp", timestamp)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): ListenTogetherSignal? {
            return try {
                val obj = JSONObject(jsonStr)
                val typeName = obj.optString("type", "")
                val type = ListenTogetherSignalType.valueOf(typeName)
                val roleName = obj.optString("senderRole", "NONE")
                val role = try { RoomRole.valueOf(roleName) } catch (_: Exception) { RoomRole.NONE }

                ListenTogetherSignal(
                    type = type,
                    roomId = obj.optString("roomId", ""),
                    senderName = obj.optString("senderName", "Guest"),
                    senderRole = role,
                    trackId = obj.optString("trackId").takeIf { it.isNotBlank() },
                    trackTitle = obj.optString("trackTitle").takeIf { it.isNotBlank() },
                    trackArtist = obj.optString("trackArtist").takeIf { it.isNotBlank() },
                    trackCover = obj.optString("trackCover").takeIf { it.isNotBlank() },
                    trackDurationSec = obj.optInt("trackDurationSec", 0),
                    youtubeVideoId = obj.optString("youtubeVideoId").takeIf { it.isNotBlank() },
                    streamUrl = obj.optString("streamUrl").takeIf { it.isNotBlank() },
                    isPlaying = obj.optBoolean("isPlaying", false),
                    positionMs = obj.optLong("positionMs", 0L),
                    memberCount = obj.optInt("memberCount", 1),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
