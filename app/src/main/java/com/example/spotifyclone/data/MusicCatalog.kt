package com.example.spotifyclone.data

import com.example.spotifyclone.data.model.Track

object MusicCatalog {
    val initialTracks = listOf(
        Track(
            id = "track_1",
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
            id = "track_2",
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
            id = "track_3",
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
            id = "track_4",
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
            id = "track_5",
            title = "Dan...",
            artist = "Sheila On 7",
            album = "Sheila On 7",
            durationSeconds = 285,
            streamUrl = "https://music.youtube.com/watch?v=dGcGbF4ex5o",
            coverUrl = "https://i.ytimg.com/vi/dGcGbF4ex5o/hqdefault.jpg",
            genre = "Pop Rock",
            youtubeVideoId = "dGcGbF4ex5o"
        ),
        Track(
            id = "track_6",
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
            id = "track_7",
            title = "Akad",
            artist = "Payung Teduh",
            album = "Ruang Tunggu",
            durationSeconds = 250,
            streamUrl = "https://music.youtube.com/watch?v=viW0M5R2BLo",
            coverUrl = "https://i.ytimg.com/vi/viW0M5R2BLo/hqdefault.jpg",
            genre = "Indie Pop",
            youtubeVideoId = "viW0M5R2BLo"
        ),
        Track(
            id = "track_8",
            title = "Dari Mata Sang Garuda",
            artist = "Pee Wee Gaskins",
            album = "The Sophomore",
            durationSeconds = 252,
            streamUrl = "https://music.youtube.com/watch?v=ixHGYhLo_Po",
            coverUrl = "https://i.ytimg.com/vi/ixHGYhLo_Po/hqdefault.jpg",
            genre = "Pop Punk",
            youtubeVideoId = "ixHGYhLo_Po"
        )
    )

    val genres = listOf("All", "Pop Punk", "Pop", "Indie", "Pop Rock", "Rock", "Acoustic")
}
