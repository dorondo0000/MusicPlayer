package kr.dorondo.cinematomusic.packet

data class VideoInfo(
    val serviceType: String, // "YOUTUBE", "TWITCH", etc
    val id: String,
    val title: String,
    val poster: String,
    val thumbnailUrl: String,
    val durationSeconds: Long
) {
    fun toBytes(buf: CinemaPacketBuf) {
        buf.writeString(serviceType)
        buf.writeString(id)
        buf.writeString(title)
        buf.writeString(poster)
        buf.writeString(thumbnailUrl)
        buf.writeLong(durationSeconds)
    }
}

data class CinemaVideo(
    val videoInfo: VideoInfo,
    val startedAt: Long
) {
    fun toBytes(buf: CinemaPacketBuf) {
        videoInfo.toBytes(buf)
        buf.writeLong(startedAt)
    }
}
