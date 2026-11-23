package com.example.spotifyclone.util

import com.example.spotifyclone.data.model.Track

/**
 * Pembersih metadata terpusat (Centralized MetadataSanitizer) untuk aplikasi Crankr.
 * 
 * Bertanggung jawab untuk:
 * 1. Memblokir artefak antarmuka web, judul video mentah, atau tombol sistem:
 *    - Pintasan keyboard, Video Bundar, Round Video, Official Lyric Video, Subscribe, Audio Only, Watch Full, Teaser.
 * 2. Memblokir duplikasi nama di mana judul lagu sama dengan nama artis
 *    jika trek tersebut tidak memiliki ID rilis album resmi (bukan rilisan resmi Spotify).
 * 3. Normalisasi teks: membersihkan tag kurung seperti (Audio), [HQ], [Official Video],
 *    (Official Music Video), (Lyric Video), [HD], dll. sebelum data dipetakan ke UI.
 * 4. Memverifikasi integritas playback (durasi minimal 30 detik untuk menghindari klip cuplikan rusak).
 */
object MetadataSanitizer {

    private val SYSTEM_WEB_ARTIFACTS = listOf(
        "pintasan keyboard",
        "keyboard shortcuts",
        "keyboard shortcut",
        "video bundar",
        "round video",
        "watch full",
        "watch video",
        "audio only",
        "audio resmi",
        "subscribe",
        "teaser",
        "trailer",
        "full episode",
        "podcast",
        "full podcast",
        "podcast episode",
        "interview",
        "exclusive interview",
        "talkshow",
        "talk show",
        "wawancara",
        "shorts",
        "short",
        "official lyric video",
        "official music video",
        "official visualizer",
        "official video",
        "video musik",
        "video klip",
        "video clip",
        "music video"
    )

    private val ARTIFACT_REGEX = Regex(
        """(?i)(?:pintasan\s+keyboard|keyboard\s+shortcuts?|video\s+bundar|round\s+video|watch\s+full|watch\s+video|audio\s+only|audio\s+resmi|subscribe|\bteaser\b|\btrailer\b|full\s+episode|podcast\s+episode|\bpodcasts?\b|\binterviews?\b|\btalkshows?\b|talk\s+show|wawancara|\bshorts?\b)"""
    )

    private val BRACKET_NOISE_REGEX = Regex(
        """(?i)\s*[\(\[\{][^\)\]\}]*(?:official|audio|video|hq|hd|4k|lyric|clip|remaster|version|explicit|lirik|visualizer|album\s+version|audio\s+only)[^\)\]\}]*[\)\]\}]"""
    )

    private val ARTIST_CHANNEL_CLEANER = Regex(
        """(?i)\s*[-–—]\s*(?:topic|official\s+channel|vevo|official)$"""
    )

    /**
     * Memeriksa apakah teks judul atau artis mengandung artefak antarmuka web atau sistem.
     */
    fun isBlockedArtifact(title: String, artist: String = ""): Boolean {
        val cleanTitle = title.trim().lowercase()
        val cleanArtist = artist.trim().lowercase()
        if (cleanTitle.isBlank()) return true

        if (ARTIFACT_REGEX.containsMatchIn(cleanTitle) || (cleanArtist.isNotBlank() && ARTIFACT_REGEX.containsMatchIn(cleanArtist))) {
            return true
        }

        return SYSTEM_WEB_ARTIFACTS.any { cleanTitle.contains(it) || (cleanArtist.isNotBlank() && cleanArtist.contains(it)) }
    }

    /**
     * Filter global untuk memastikan hanya lagu rekaman studio orisinal yang lolos.
     * Memeriksa metadata judul lagu, nama artis, dan deskripsi/album trek dalam format huruf kecil.
     * Membuang lagu jika mengandung kata kunci:
     * cover, acoustic cover, akustik, live, live concert, live session, live acoustic,
     * karaoke, tribute, parodi, atau bootleg.
     */
    fun isOfficialStudioTrack(title: String, artist: String = "", description: String = ""): Boolean {
        val t = title.lowercase().trim()
        val a = artist.lowercase().trim()
        val d = description.lowercase().trim()

        // Pengecualian khusus musisi/band resmi yang namanya memuat kata kunci (misal: "Saturday Night Karaoke")
        val safeT = t.replace(Regex("""(?i)saturday\s+night\s+karaoke"""), "saturday night band")
        val safeA = a.replace(Regex("""(?i)saturday\s+night\s+karaoke"""), "saturday night band")
        val safeD = d.replace(Regex("""(?i)saturday\s+night\s+karaoke"""), "saturday night band")
        val safeCombined = "$safeT $safeA $safeD"

        // 1. Pengecekan frasa spesifik terlarang
        val forbiddenPhrases = listOf(
            "acoustic cover",
            "akustik cover",
            "live concert",
            "live session",
            "live acoustic",
            "akustik live",
            "konser live",
            "live at",
            "live in",
            "full episode",
            "podcast episode",
            "the zach sang show",
            "talk show",
            "exclusive interview"
        )
        for (phrase in forbiddenPhrases) {
            if (safeCombined.contains(phrase)) return false
        }

        // 2. Kata kunci terlarang dengan batas kata (word boundary)
        val generalForbiddenRegex = Regex("""(?i)\b(?:cover|akustik|acoustic|karaoke|tribute|parodi|parody|bootleg|podcast|interview|talkshow|wawancara)\b""")
        if (generalForbiddenRegex.containsMatchIn(safeT) || generalForbiddenRegex.containsMatchIn(safeD)) {
            return false
        }

        // Pengecekan episode podcast terlarang (menghindari 'Episode 12', 'Ep. 5', 'Podcast', namun menjaga 'The Next Episode')
        val episodePatternRegex = Regex("""(?i)(?:full\s+episode|podcast\s+episode|\bepisode\s*#?\d+\b|\bep\.\s*\d+\b)""")
        if (episodePatternRegex.containsMatchIn(safeT) || episodePatternRegex.containsMatchIn(safeD)) {
            return false
        }

        // Pengecekan rekaman langsung / konser (Live Recording):
        // Hanya memblokir jika "live" muncul dalam konteks rekaman panggung/konser,
        // TIDAK memblokir lagu studio sah seperti "Live Forever", "Live to Win", "Live and Let Die", "I Want to Live".
        val liveRecordingRegex = Regex("""(?i)(?:[\(\[\{]\s*live[\s\w\d\-–—]*[\)\]\}]|\b-\s*live\b|\blive\s+(?:version|recording|audio|performance|session|concert|tour|bootleg|edition)\b|\blive\s+at\b|\blive\s+in\b)""")
        if (liveRecordingRegex.containsMatchIn(safeT) || liveRecordingRegex.containsMatchIn(safeD)) {
            return false
        }

        // Pengecekan pada nama artis (hanya kata kunci yang relevan untuk musisi/channel)
        val artistForbiddenRegex = Regex("""(?i)\b(?:cover|karaoke|tribute|parodi|parody|bootleg|podcast)\b""")
        if (artistForbiddenRegex.containsMatchIn(safeA)) {
            return false
        }

        return true
    }

    fun isOfficialStudioTrack(track: Track): Boolean {
        return isOfficialStudioTrack(track.title, track.artist, track.album)
    }

    /**
     * Memeriksa apakah judul lagu sama persis dengan nama band (misal judul "The Skit" oleh artis "The Skit").
     * Ditolak jika berupa artefak/spam durasi sangat pendek, namun diizinkan jika merupakan trek resmi,
     * trek Bandcamp, atau lagu dengan durasi studio wajar (>= 30 detik).
     */
    fun isDuplicateTitleAndArtist(title: String, artist: String, isOfficial: Boolean, durationSeconds: Int = 0): Boolean {
        val cleanTitle = title.trim().lowercase()
        val cleanArtist = artist.trim().lowercase()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return false

        val isIdentical = cleanTitle == cleanArtist ||
                cleanTitle == "the $cleanArtist" ||
                "the $cleanTitle" == cleanArtist

        if (isIdentical) {
            // Izinkan lagu self-titled sah jika resmi atau durasi lagu normal (>= 30 detik)
            if (isOfficial || durationSeconds >= 30) {
                return false
            }
            return true
        }
        return false
    }

    /**
     * Membersihkan tag kurung dan teks pengganggu dari judul lagu:
     * - Menghapus (Audio), [HQ], [Official Video], (Lyric Video), [HD], dll.
     * - Menghapus prefix "Artist - " jika terdapat pada awal judul.
     * - Merapikan spasi dan tanda baca berlebih.
     */
    fun normalizeTitle(rawTitle: String, artistName: String = ""): String {
        if (rawTitle.isBlank()) return ""
        var cleaned = rawTitle

        // 1. Bersihkan noise dalam kurung: (Official Video), [HQ], dll.
        cleaned = BRACKET_NOISE_REGEX.replace(cleaned, "")

        // 2. Jika judul diawali dengan nama artis ("Artist - Title"), hilangkan prefix artis
        if (artistName.isNotBlank()) {
            val esc = Regex.escape(artistName.trim())
            cleaned = cleaned.replace(Regex("""(?i)^\s*$esc\s*[-–—:]\s*"""), "")
        }

        // 3. Bersihkan kurung kosong yang tersisa: (), [], {}
        cleaned = cleaned.replace(Regex("""[\(\[\{]\s*[\)\]\}]"""), "")

        // 4. Bersihkan tanda hubung atau titik dua di akhir judul
        cleaned = cleaned.replace(Regex("""(?i)\s*[-–—:]\s*(?:official|audio|video)?\s*$"""), "")

        // 5. Bersihkan tanda petik pembuka/penutup
        cleaned = cleaned.trim().trim('"', '\'', '`')

        return cleaned.trim()
    }

    /**
     * Membersihkan nama artis dari akhiran channel (misal: "Artist - Topic" -> "Artist").
     */
    fun normalizeArtist(rawArtist: String): String {
        if (rawArtist.isBlank()) return ""
        var cleaned = rawArtist.trim()
        cleaned = ARTIST_CHANNEL_CLEANER.replace(cleaned, "")
        return cleaned.trim()
    }

    /**
     * Memvalidasi apakah sebuah trek memenuhi kriteria keabsahan Crankr:
     * - Durasi minimal 30 detik (menolak cuplikan/klip rusak)
     * - Bebas dari artefak web atau tombol antarmuka
     * - Bebas dari blacklist studio (kompilasi/bootleg/cover/karaoke)
     * - Bukan duplikasi judul==artis tanpa lisensi resmi
     */
    fun isValidTrack(track: Track): Boolean {
        if (track.title.isBlank()) return false
        val isOfficial = track.id.startsWith("sp_") || !track.spotifyArtistId.isNullOrBlank()
        val isOfficialAlbumTrack = isOfficial && (track.id.startsWith("sp_track_") || (track.album.isNotBlank() && track.album != "Single"))

        // Jangan memblokir trek hanya karena durasinya pendek jika trek tersebut merupakan bagian sah dari tracklist album atau EP resmi
        if (!isOfficialAlbumTrack && track.durationSeconds < 30) return false
        if (track.durationSeconds <= 0) return false

        // Untuk trek non-resmi (YouTube), durasi maksimal 10 menit (menghindari full album/kompilasi)
        if (!isOfficial && track.durationSeconds > 600) return false

        // Tolak artefak web dan sistem
        if (isBlockedArtifact(track.title, track.artist)) return false

        // Tolak lagu non-studio (cover, live, akustik, karaoke, tribute, parodi, bootleg)
        if (!isOfficialStudioTrack(track)) return false

        // Tolak kompilasi / bootleg / cover
        if (CrankrCatalogFilter.isBlacklisted(track.title, track.artist)) return false

        // Tolak duplikasi judul == artis
        if (isDuplicateTitleAndArtist(track.title, track.artist, isOfficial, track.durationSeconds)) return false

        return true
    }

    /**
     * Membersihkan dan menormalisasi objek Track.
     * Mengembalikan null jika trek melanggar filter keabsahan.
     */
    fun sanitizeTrack(track: Track): Track? {
        if (!isValidTrack(track)) return null

        val cleanTitle = normalizeTitle(track.title, track.artist).ifBlank { track.title.trim() }
        val cleanArtist = normalizeArtist(track.artist).ifBlank { track.artist.trim() }

        return track.copy(
            title = cleanTitle,
            artist = cleanArtist
        )
    }

    /**
     * Membersihkan daftar trek sekaligus: memfilter trek yang tidak valid dan menormalisasi judul/artis.
     */
    fun sanitizeTracks(tracks: List<Track>): List<Track> {
        return tracks.mapNotNull { sanitizeTrack(it) }
    }
}

/**
 * Fungsi filter global isOfficialStudioTrack pada lapisan data/utilitas pemutar
 * untuk membuang versi cover, live concert/session, akustik, karaoke, tribute, parodi, atau bootleg.
 */
fun isOfficialStudioTrack(title: String, artist: String = "", description: String = ""): Boolean =
    MetadataSanitizer.isOfficialStudioTrack(title, artist, description)

fun isOfficialStudioTrack(track: Track): Boolean =
    MetadataSanitizer.isOfficialStudioTrack(track)
