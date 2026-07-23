package kr.dorondo.cinematomusic.model

data class Track(
    val title: String,
    val youtubeUrl: String,
    val duration: Long = 0, // in seconds
    val author: String = ""
)


