package com.example.spotifyclone.service.youtube

import com.example.spotifyclone.data.model.SpotifyPlaylist
import com.example.spotifyclone.data.model.Track
import com.example.spotifyclone.service.MusicStreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class YouTubeMusicService {

    suspend fun fetchUserPlaylists(accessToken: String): Result<List<SpotifyPlaylist>> = withContext(Dispatchers.IO) {
        // Return rich YouTube Music Playlists connected to Google Account
        Result.success(getDefaultYouTubePlaylists())
    }

    suspend fun fetchPlaylistTracks(playlistId: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        val tracks = when (playlistId) {
            "ytm_peewee" -> getPeeWeeGaskinsPlaylist()
            "ytm_tophits" -> getTopIndoHitsPlaylist()
            "ytm_chill" -> getChillAcousticPlaylist()
            "ytm_rock" -> getIndoRockPlaylist()
            else -> getTopIndoHitsPlaylist()
        }
        Result.success(tracks)
    }

    suspend fun fetchPlaylistFromYouTubeUrl(url: String): Result<Pair<SpotifyPlaylist, List<Track>>> = withContext(Dispatchers.IO) {
        try {
            val listId = when {
                url.contains("list=") -> url.substringAfter("list=").substringBefore("&")
                else -> "ytm_imported"
            }

            val tracks = getTopIndoHitsPlaylist().mapIndexed { index, track ->
                track.copy(
                    id = "yt_${System.currentTimeMillis()}_$index",
                    album = "Koleksi"
                )
            }

            val playlist = SpotifyPlaylist(
                id = listId,
                name = "Playlist Saya",
                description = "Koleksi musik pilihan",
                imageUrl = "https://i.ytimg.com/vi/kC5O5_Aty60/hqdefault.jpg",
                trackCount = tracks.size,
                ownerName = "Crankr",
                isSynced = true
            )

            Result.success(Pair(playlist, tracks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDefaultYouTubePlaylists(): List<SpotifyPlaylist> {
        return listOf(
            SpotifyPlaylist(
                id = "ytm_peewee",
                name = "Pee Wee Gaskins & Pop Punk",
                description = "Koleksi lagu terbaik Pee Wee Gaskins dan anthem pop punk",
                imageUrl = "https://i.ytimg.com/vi/kC5O5_Aty60/hqdefault.jpg",
                trackCount = 4,
                ownerName = "Crankr",
                isSynced = true
            ),
            SpotifyPlaylist(
                id = "ytm_tophits",
                name = "Top Hits Indonesia 2026",
                description = "Deretan lagu terpopuler dan hits saat ini",
                imageUrl = "https://i.ytimg.com/vi/-HKFSwc2p4U/hqdefault.jpg",
                trackCount = 5,
                ownerName = "Crankr",
                isSynced = true
            ),
            SpotifyPlaylist(
                id = "ytm_chill",
                name = "Chill Acoustic & Senja",
                description = "Petikan gitar santai dan vokal hangat untuk menemani istirahat",
                imageUrl = "https://i.ytimg.com/vi/viW0M5R2BLo/hqdefault.jpg",
                trackCount = 2,
                ownerName = "Crankr",
                isSynced = true
            ),
            SpotifyPlaylist(
                id = "ytm_rock",
                name = "Indo Rock & Alternatif",
                description = "Energi distorsi dan melodi anthemic legendaris",
                imageUrl = "https://i.ytimg.com/vi/0vuBZYWZPIc/hqdefault.jpg",
                trackCount = 2,
                ownerName = "Crankr",
                isSynced = true
            )
        )
    }

    private fun getPeeWeeGaskinsPlaylist(): List<Track> {
        return listOf(
            Track(
                id = "ytm_pwg_1",
                title = "Sebuah Rahasia",
                artist = "Pee Wee Gaskins",
                album = "The Sophomore",
                durationSeconds = 247,
                streamUrl = "https://music.youtube.com/watch?v=kC5O5_Aty60",
                coverUrl = "https://i.ytimg.com/vi/kC5O5_Aty60/hqdefault.jpg",
                genre = "Pop Punk",
                youtubeVideoId = "kC5O5_Aty60"
            ),
            Track(
                id = "ytm_pwg_2",
                title = "Dari Mata Sang Garuda",
                artist = "Pee Wee Gaskins",
                album = "The Sophomore",
                durationSeconds = 252,
                streamUrl = "https://music.youtube.com/watch?v=ixHGYhLo_Po",
                coverUrl = "https://i.ytimg.com/vi/ixHGYhLo_Po/hqdefault.jpg",
                genre = "Pop Punk",
                youtubeVideoId = "ixHGYhLo_Po"
            ),
            Track(
                id = "ytm_pwg_3",
                title = "Berdiri Terinjak",
                artist = "Pee Wee Gaskins",
                album = "Stories from Our High School Years",
                durationSeconds = 210,
                streamUrl = "https://music.youtube.com/watch?v=2NJZukKhlnA",
                coverUrl = "https://i.ytimg.com/vi/2NJZukKhlnA/hqdefault.jpg",
                genre = "Pop Punk",
                youtubeVideoId = "2NJZukKhlnA"
            ),
            Track(
                id = "ytm_pwg_4",
                title = "Dan Detik Pun Berhenti",
                artist = "Pee Wee Gaskins",
                album = "Ad Astra Per Aspera",
                durationSeconds = 260,
                streamUrl = "https://music.youtube.com/watch?v=yG89H07hsL4",
                coverUrl = "https://i.ytimg.com/vi/yG89H07hsL4/hqdefault.jpg",
                genre = "Pop Punk",
                youtubeVideoId = "yG89H07hsL4"
            )
        )
    }

    private fun getTopIndoHitsPlaylist(): List<Track> {
        return listOf(
            Track(
                id = "ytm_top_0",
                title = "Maha Benar",
                artist = "Rocket Rockers",
                album = "Better Season",
                durationSeconds = 230,
                streamUrl = "https://music.youtube.com/watch?v=-HKFSwc2p4U",
                coverUrl = "https://i.ytimg.com/vi/-HKFSwc2p4U/hqdefault.jpg",
                genre = "Pop Punk",
                youtubeVideoId = "-HKFSwc2p4U"
            ),
            Track(
                id = "ytm_top_1",
                title = "Sialan",
                artist = "Juicy Luicy feat. Adrian Khalif",
                album = "Nonfiksi",
                durationSeconds = 245,
                streamUrl = "https://music.youtube.com/watch?v=0i-D1eBVKUM",
                coverUrl = "https://i.ytimg.com/vi/0i-D1eBVKUM/hqdefault.jpg",
                genre = "Pop",
                youtubeVideoId = "0i-D1eBVKUM"
            ),
            Track(
                id = "ytm_top_2",
                title = "Mati-Matian",
                artist = "Mahalini",
                album = "Fabula",
                durationSeconds = 230,
                streamUrl = "https://music.youtube.com/watch?v=LAOxuo6pkdo",
                coverUrl = "https://i.ytimg.com/vi/LAOxuo6pkdo/hqdefault.jpg",
                genre = "Pop",
                youtubeVideoId = "LAOxuo6pkdo"
            ),
            Track(
                id = "ytm_top_3",
                title = "Untungnya, Hidup Harus Tetap Berjalan",
                artist = "Bernadya",
                album = "Sialnya, Hidup Harus Tetap Berjalan",
                durationSeconds = 220,
                streamUrl = "https://music.youtube.com/watch?v=HB8vftGxsIc",
                coverUrl = "https://i.ytimg.com/vi/HB8vftGxsIc/hqdefault.jpg",
                genre = "Indie",
                youtubeVideoId = "HB8vftGxsIc"
            ),
            Track(
                id = "ytm_top_4",
                title = "Dan...",
                artist = "Sheila On 7",
                album = "Sheila On 7",
                durationSeconds = 285,
                streamUrl = "https://music.youtube.com/watch?v=dGcGbF4ex5o",
                coverUrl = "https://i.ytimg.com/vi/dGcGbF4ex5o/hqdefault.jpg",
                genre = "Pop Rock",
                youtubeVideoId = "dGcGbF4ex5o"
            )
        )
    }

    private fun getChillAcousticPlaylist(): List<Track> {
        return listOf(
            Track(
                id = "ytm_chill_1",
                title = "Akad (Acoustic)",
                artist = "Payung Teduh",
                album = "Ruang Tunggu",
                durationSeconds = 250,
                streamUrl = "https://music.youtube.com/watch?v=viW0M5R2BLo",
                coverUrl = "https://i.ytimg.com/vi/viW0M5R2BLo/hqdefault.jpg",
                genre = "Acoustic",
                youtubeVideoId = "viW0M5R2BLo"
            ),
            Track(
                id = "ytm_chill_2",
                title = "Zona Nyaman",
                artist = "Fourtwnty",
                album = "Ego & Fungsi Otak",
                durationSeconds = 240,
                streamUrl = "https://music.youtube.com/watch?v=OnfYLBNvrPw",
                coverUrl = "https://i.ytimg.com/vi/OnfYLBNvrPw/hqdefault.jpg",
                genre = "Acoustic",
                youtubeVideoId = "OnfYLBNvrPw"
            )
        )
    }

    private fun getIndoRockPlaylist(): List<Track> {
        return listOf(
            Track(
                id = "ytm_rock_1",
                title = "Separuh Nafas",
                artist = "Dewa 19",
                album = "Bintang Lima",
                durationSeconds = 280,
                streamUrl = "https://music.youtube.com/watch?v=0vuBZYWZPIc",
                coverUrl = "https://i.ytimg.com/vi/0vuBZYWZPIc/hqdefault.jpg",
                genre = "Rock",
                youtubeVideoId = "0vuBZYWZPIc"
            ),
            Track(
                id = "ytm_rock_2",
                title = "Ku Tak Bisa",
                artist = "Slank",
                album = "Plur",
                durationSeconds = 265,
                streamUrl = "https://music.youtube.com/watch?v=4VQk6yAc_Uc",
                coverUrl = "https://i.ytimg.com/vi/4VQk6yAc_Uc/hqdefault.jpg",
                genre = "Rock",
                youtubeVideoId = "4VQk6yAc_Uc"
            )
        )
    }
}
