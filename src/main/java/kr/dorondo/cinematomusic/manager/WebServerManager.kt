package kr.dorondo.cinematomusic.manager

import com.google.gson.Gson
import io.javalin.Javalin
import io.javalin.http.Context
import kr.dorondo.cinematomusic.MusicPlayer
import kr.dorondo.cinematomusic.model.Track
import kr.dorondo.cinematomusic.util.YouTubeUtil
import org.bukkit.Bukkit
import java.util.concurrent.Callable

class WebServerManager(private val plugin: MusicPlayer) {
    
    private var app: Javalin? = null
    private val gson = Gson()

    private inline fun <reified T> parseBody(ctx: Context): T =
        gson.fromJson(ctx.body(), T::class.java)

    private fun <T> onServerThread(action: () -> T): T {
        if (Bukkit.isPrimaryThread()) return action()
        return Bukkit.getScheduler().callSyncMethod(plugin, Callable { action() }).get()
    }
    
    fun start() {
        val port = plugin.getConfigManager().getWebServerPort()
        
        try {
            app = Javalin.create { config ->
                config.showJavalinBanner = false
                config.staticFiles.add("/web")
            }.start(port)
            
            setupRoutes()
            
            plugin.logger.info("Web server started on port $port")
            plugin.logger.info("Access at: http://localhost:$port")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to start web server: ${e.message}")
        }
    }
    
    fun stop() {
        app?.stop()
        plugin.logger.info("Web server stopped")
    }
    
    private fun setupRoutes() {
        val app = this.app ?: return
        
        // Get YouTube video info
        app.post("/api/youtube/info") { ctx ->
            try {
                val data = parseBody<YouTubeUrlRequest>(ctx)
                plugin.logger.info("[WebServer] Fetching info for: ${data.url}")
                
                val info = YouTubeUtil.getVideoInfo(data.url)
                
                if (info != null) {
                    plugin.logger.info("[WebServer] Success: ${info.title} by ${info.author}")
                    ctx.json(mapOf(
                        "title" to info.title,
                        "author" to info.author,
                        "videoId" to info.videoId,
                        "duration" to info.duration
                    ))
                } else {
                    plugin.logger.warning("[WebServer] Failed to fetch video info, using fallback")
                    // Fallback: just return basic info
                    val videoId = YouTubeUtil.extractVideoId(data.url)
                    if (videoId != null) {
                        ctx.json(mapOf(
                            "title" to "YouTube Video",
                            "author" to "Unknown",
                            "videoId" to videoId,
                            "duration" to 0
                        ))
                    } else {
                        ctx.status(400).json(mapOf("error" to "Invalid YouTube URL"))
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("[WebServer] Error: ${e.message}")
                e.printStackTrace()
                ctx.status(500).json(mapOf("error" to e.message))
            }
        }
        
        // Get server state (global player)
        app.get("/api/state") { ctx ->
            ctx.json(onServerThread {
                val manager = plugin.getMusicPlayerManager()
                mapOf(
                    "queue" to manager.getQueue(),
                    "currentIndex" to manager.getCurrentIndex(),
                    "isPlaying" to manager.isPlaying(),
                    "isPaused" to manager.isPaused(),
                    "loopEnabled" to manager.isLoopEnabled(),
                    "bossbarEnabled" to manager.isBossbarEnabled(),
                    "currentTrack" to manager.getCurrentTrack(),
                    "positionSeconds" to manager.getPlaybackPositionSeconds(),
                    "durationSeconds" to manager.getPlaybackDurationSeconds(),
                    "recentTracks" to plugin.getPlaylistManager().getRecentlyPlayed(),
                    "onlinePlayers" to Bukkit.getOnlinePlayers().map { it.name }
                )
            })
        }
        
        // Get queue
        app.get("/api/queue") { ctx ->
            ctx.json(onServerThread {
                val manager = plugin.getMusicPlayerManager()
                mapOf(
                    "queue" to manager.getQueue(),
                    "currentIndex" to manager.getCurrentIndex(),
                    "isPlaying" to manager.isPlaying(),
                    "isPaused" to manager.isPaused(),
                    "loopEnabled" to manager.isLoopEnabled(),
                    "bossbarEnabled" to manager.isBossbarEnabled(),
                    "positionSeconds" to manager.getPlaybackPositionSeconds(),
                    "durationSeconds" to manager.getPlaybackDurationSeconds()
                )
            })
        }
        
        // Add track to queue
        app.post("/api/queue") { ctx ->
            val track = parseBody<Track>(ctx)
            onServerThread { plugin.getMusicPlayerManager().addToQueue(track) }
            ctx.json(mapOf("success" to true))
        }

        app.post("/api/queue/insert") { ctx ->
            val data = parseBody<InsertTrackRequest>(ctx)
            onServerThread {
                plugin.getMusicPlayerManager().insertIntoQueue(data.index, data.track)
            }
            ctx.json(mapOf("success" to true))
        }
        
        // Remove track from queue
        app.delete("/api/queue/{index}") { ctx ->
            val index = ctx.pathParam("index").toIntOrNull()
            if (index == null) {
                ctx.status(400).json(mapOf("error" to "Invalid index"))
                return@delete
            }
            
            onServerThread { plugin.getMusicPlayerManager().removeFromQueue(index) }
            ctx.json(mapOf("success" to true))
        }
        
        // Move track in queue
        app.post("/api/queue/move") { ctx ->
            val data = parseBody<MoveTrackRequest>(ctx)
            onServerThread { plugin.getMusicPlayerManager().moveInQueue(data.fromIndex, data.toIndex) }
            ctx.json(mapOf("success" to true))
        }
        
        // Play/pause
        app.post("/api/player/play-pause") { ctx ->
            onServerThread {
                val manager = plugin.getMusicPlayerManager()
                if (manager.isPaused()) {
                    manager.resumeMusic()
                } else if (manager.isPlaying()) {
                    manager.pauseMusic()
                }
            }
            ctx.json(mapOf("success" to true))
        }
        
        // Next track
        app.post("/api/player/next") { ctx ->
            onServerThread { plugin.getMusicPlayerManager().nextTrack() }
            ctx.json(mapOf("success" to true))
        }
        
        // Previous track
        app.post("/api/player/previous") { ctx ->
            onServerThread { plugin.getMusicPlayerManager().previousTrack() }
            ctx.json(mapOf("success" to true))
        }
        
        // Jump to track
        app.post("/api/player/jump") { ctx ->
            val data = parseBody<JumpToTrackRequest>(ctx)
            onServerThread { plugin.getMusicPlayerManager().jumpToTrack(data.index) }
            ctx.json(mapOf("success" to true))
        }

        app.post("/api/player/play-track") { ctx ->
            val track = parseBody<Track>(ctx)
            val success = onServerThread { plugin.getMusicPlayerManager().playTrack(track) }
            ctx.json(mapOf("success" to success))
        }

        // Seek the global player. Cinema has no dedicated seek packet, so the
        // manager rebases startedAt and reloads every listener at that position.
        app.post("/api/player/seek") { ctx ->
            val data = parseBody<SeekRequest>(ctx)
            val success = onServerThread {
                plugin.getMusicPlayerManager().seekTo(data.positionSeconds)
            }
            if (success) {
                ctx.json(mapOf("success" to true, "positionSeconds" to data.positionSeconds))
            } else {
                ctx.status(409).json(mapOf("error" to "재생 길이를 확인할 수 없는 곡입니다."))
            }
        }
        
        // Stop music
        app.post("/api/player/stop") { ctx ->
            onServerThread { plugin.getMusicPlayerManager().stopMusic() }
            ctx.json(mapOf("success" to true))
        }

        // Show or hide the global music boss bar
        app.post("/api/player/bossbar") { ctx ->
            val data = parseBody<BossbarRequest>(ctx)
            onServerThread { plugin.getMusicPlayerManager().toggleBossbar(data.enabled) }
            ctx.json(mapOf("success" to true, "enabled" to data.enabled))
        }

        app.post("/api/player/loop") { ctx ->
            val data = parseBody<LoopRequest>(ctx)
            onServerThread { plugin.getMusicPlayerManager().setLoop(data.enabled) }
            ctx.json(mapOf("success" to true, "enabled" to data.enabled))
        }
        
        // Load playlist to queue
        app.post("/api/player/load-playlist") { ctx ->
            val data = parseBody<LoadPlaylistRequest>(ctx)
            
            val playlist = plugin.getPlaylistManager().getPlaylist(data.playlistName)
            if (playlist != null) {
                if (data.replace) {
                    val success = onServerThread {
                        plugin.getMusicPlayerManager().playPlaylist(
                            playlist,
                            data.shuffle,
                            data.startIndex
                        )
                    }
                    ctx.json(mapOf("success" to success))
                } else {
                    onServerThread {
                        plugin.getMusicPlayerManager().addPlaylistToQueue(playlist, data.shuffle)
                    }
                    ctx.json(mapOf("success" to true))
                }
            } else {
                ctx.status(404).json(mapOf("error" to "Playlist not found"))
            }
        }
        
        // Get all playlists
        app.get("/api/playlists") { ctx ->
            val playlists = plugin.getPlaylistManager().getAllPlaylists()
            ctx.json(playlists)
        }

        app.get("/api/recent") { ctx ->
            ctx.json(plugin.getPlaylistManager().getRecentlyPlayed())
        }
        
        // Get specific playlist
        app.get("/api/playlists/{name}") { ctx ->
            val name = ctx.pathParam("name")
            val playlist = plugin.getPlaylistManager().getPlaylist(name)
            if (playlist != null) {
                ctx.json(playlist)
            } else {
                ctx.status(404).json(mapOf("error" to "Playlist not found"))
            }
        }

        // Rename the library-facing playlist title without changing its stable
        // filename/API key.
        app.patch("/api/playlists/{name}") { ctx ->
            val name = ctx.pathParam("name")
            val data = parseBody<RenamePlaylistRequest>(ctx)
            val playlist = plugin.getPlaylistManager().updateDisplayName(name, data.displayName)
            if (playlist != null) {
                ctx.json(playlist)
            } else {
                ctx.status(400).json(mapOf("error" to "플레이리스트 이름을 확인해 주세요."))
            }
        }
        
        // Create playlist
        app.post("/api/playlists") { ctx ->
            val data = parseBody<CreatePlaylistRequest>(ctx)
            val playlist = plugin.getPlaylistManager().createPlaylist(data.name, data.displayName)
            ctx.json(playlist)
        }
        
        // Delete playlist
        app.delete("/api/playlists/{name}") { ctx ->
            val name = ctx.pathParam("name")
            val success = plugin.getPlaylistManager().deletePlaylist(name)
            if (success) {
                ctx.json(mapOf("success" to true))
            } else {
                ctx.status(404).json(mapOf("error" to "Playlist not found"))
            }
        }
        
        // Add track to playlist
        app.post("/api/playlists/{name}/tracks") { ctx ->
            val name = ctx.pathParam("name")
            val playlist = plugin.getPlaylistManager().getPlaylist(name)
            if (playlist != null) {
                val track = parseBody<Track>(ctx)
                playlist.addTrack(track)
                plugin.getPlaylistManager().savePlaylist(playlist)
                ctx.json(playlist)
            } else {
                ctx.status(404).json(mapOf("error" to "Playlist not found"))
            }
        }
        
        // Remove track from playlist
        app.delete("/api/playlists/{name}/tracks/{index}") { ctx ->
            val name = ctx.pathParam("name")
            val index = ctx.pathParam("index").toIntOrNull()
            if (index == null) {
                ctx.status(400).json(mapOf("error" to "Invalid index"))
                return@delete
            }
            
            val playlist = plugin.getPlaylistManager().getPlaylist(name)
            if (playlist != null) {
                playlist.removeTrack(index)
                plugin.getPlaylistManager().savePlaylist(playlist)
                ctx.json(playlist)
            } else {
                ctx.status(404).json(mapOf("error" to "Playlist not found"))
            }
        }
        
        // Move track in playlist
        app.post("/api/playlists/{name}/tracks/move") { ctx ->
            val name = ctx.pathParam("name")
            val data = parseBody<MoveTrackRequest>(ctx)
            
            val playlist = plugin.getPlaylistManager().getPlaylist(name)
            if (playlist != null) {
                playlist.moveTrack(data.fromIndex, data.toIndex)
                plugin.getPlaylistManager().savePlaylist(playlist)
                ctx.json(playlist)
            } else {
                ctx.status(404).json(mapOf("error" to "Playlist not found"))
            }
        }
        
        // Import from YouTube playlist
        app.post("/api/playlists/import") { ctx ->
            val data = parseBody<ImportPlaylistRequest>(ctx)
            val playlist = plugin.getPlaylistManager().importFromYouTube(data.url, data.name)
            if (playlist != null) {
                ctx.json(playlist)
            } else {
                ctx.status(500).json(mapOf("error" to "Failed to import playlist"))
            }
        }
    }
    
    data class YouTubeUrlRequest(val url: String) {
        constructor() : this("")
    }
    data class CreatePlaylistRequest(val name: String, val displayName: String)
    data class MoveTrackRequest(val fromIndex: Int, val toIndex: Int)
    data class InsertTrackRequest(val index: Int, val track: Track)
    data class LoadPlaylistRequest(
        val playlistName: String,
        val shuffle: Boolean = false,
        val replace: Boolean = true,
        val startIndex: Int = 0
    )
    data class ImportPlaylistRequest(val url: String, val name: String)
    data class RenamePlaylistRequest(val displayName: String)
    data class JumpToTrackRequest(val index: Int)
    data class SeekRequest(val positionSeconds: Long)
    data class BossbarRequest(val enabled: Boolean)
    data class LoopRequest(val enabled: Boolean)
}

