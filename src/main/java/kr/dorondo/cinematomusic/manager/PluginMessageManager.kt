package kr.dorondo.cinematomusic.manager

import kr.dorondo.cinematomusic.MusicPlayer
import kr.dorondo.cinematomusic.model.Track
import kr.dorondo.cinematomusic.packet.CinemaPacketBuf
import kr.dorondo.cinematomusic.packet.CinemaVideo
import kr.dorondo.cinematomusic.packet.VideoInfo
import kr.dorondo.cinematomusic.util.YouTubeUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PluginMessageManager(private val plugin: MusicPlayer) : PluginMessageListener {
    private val initializedPlayers = ConcurrentHashMap.newKeySet<UUID>()
    
    companion object {
        // Cinema Mod channels
        const val CHANNEL_LOAD_SCREEN = "cinemamod:load_screen"
        const val CHANNEL_UNLOAD_SCREEN = "cinemamod:unload_screen"
        const val CHANNEL_SERVICES = "cinemamod:services"
        const val CHANNEL_SCREENS = "cinemamod:screens"

        private const val SCREEN_X = 0
        private const val SCREEN_Y = -1000
        private const val SCREEN_Z = 0
        private const val YOUTUBE_BRIDGE_URL =
            "https://dorondo0000.github.io/MusicPlayer/bridge/v1.1.3/youtube.html"
        // Cinema Mod treats duration 0 as a livestream and deliberately skips
        // seeking. Use a long VOD duration for legacy/API tracks whose duration
        // is unknown so late joiners can still synchronize to startedAt.
        private const val UNKNOWN_VOD_DURATION_SECONDS = 7L * 24L * 60L * 60L
    }
    
    fun register() {
        val messenger = Bukkit.getMessenger()
        
        // Register outgoing channels
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL_LOAD_SCREEN)
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL_UNLOAD_SCREEN)
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL_SERVICES)
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL_SCREENS)
        
        plugin.logger.info("[PluginMessage] Registered Cinema Mod channels")
    }

    fun bootstrap(player: Player) {
        if (!initializedPlayers.add(player.uniqueId)) return

        sendServices(player)
        sendHiddenScreen(player)
        plugin.logger.info("[PluginMessage] Cinema client initialized for ${player.name}")
    }

    fun play(player: Player, track: Track, startedAt: Long): Boolean {
        val videoId = YouTubeUtil.extractVideoId(track.youtubeUrl) ?: run {
            plugin.logger.warning("[PluginMessage] Invalid YouTube URL: ${track.youtubeUrl}")
            return false
        }

        // CinemaMod's original server integration registers services/screens
        // once after login. Re-registering the same screen closes the client's
        // existing browser, so only initialize here as a fallback for hot
        // reloads or playback during the first seconds of login.
        if (player.uniqueId !in initializedPlayers) {
            bootstrap(player)
        }

        val buf = CinemaPacketBuf()
        writeScreenPosition(buf)
        CinemaVideo(
            VideoInfo(
                serviceType = "YOUTUBE",
                id = videoId,
                title = track.title,
                poster = track.author,
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                durationSeconds = track.duration.takeIf { it > 0L }
                    ?: UNKNOWN_VOD_DURATION_SECONDS
            ),
            startedAt
        ).toBytes(buf)
        player.sendPluginMessage(plugin, CHANNEL_LOAD_SCREEN, buf.array())
        return true
    }

    fun stop(player: Player) {
        val buf = CinemaPacketBuf()
        writeScreenPosition(buf)
        player.sendPluginMessage(plugin, CHANNEL_UNLOAD_SCREEN, buf.array())
    }

    fun forget(player: Player) {
        initializedPlayers.remove(player.uniqueId)
    }

    private fun sendServices(player: Player) {
        val buf = CinemaPacketBuf()
        buf.writeInt(1)
        buf.writeString("YOUTUBE")
        // Playback uses a versioned public static bridge, just like the
        // original CinemaMod service. It never depends on the admin web port.
        buf.writeString(YOUTUBE_BRIDGE_URL)
        buf.writeString("th_volume(%d);")
        buf.writeString("th_video('%s', %b);")
        buf.writeString("th_seek(%d);")
        player.sendPluginMessage(plugin, CHANNEL_SERVICES, buf.array())
    }

    private fun sendHiddenScreen(player: Player) {
        val buf = CinemaPacketBuf()
        buf.writeInt(1)
        writeScreenPosition(buf)
        buf.writeString("NORTH")
        buf.writeFloat(1.0f)
        buf.writeFloat(1.0f)
        buf.writeBoolean(false)
        buf.writeBoolean(false)
        player.sendPluginMessage(plugin, CHANNEL_SCREENS, buf.array())
    }

    private fun writeScreenPosition(buf: CinemaPacketBuf) {
        buf.writeInt(SCREEN_X)
        buf.writeInt(SCREEN_Y)
        buf.writeInt(SCREEN_Z)
    }
    
    fun unregister() {
        initializedPlayers.clear()
        val messenger = Bukkit.getMessenger()
        
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL_LOAD_SCREEN)
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL_UNLOAD_SCREEN)
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL_SERVICES)
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL_SCREENS)
    }
    
    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        // Not used for now
    }
}
