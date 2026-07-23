package kr.dorondo.cinematomusic.manager

import kr.dorondo.cinematomusic.MusicPlayer
import kr.dorondo.cinematomusic.model.Playlist
import kr.dorondo.cinematomusic.model.Track
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import java.util.*

class MusicPlayerManager(private val plugin: MusicPlayer) : Listener {
    
    // Global state - everyone listens to the same music
    private val playQueue = mutableListOf<Track>()
    private var currentTrackIndex = 0
    private var isPlaying = false
    private var isPaused = false
    private var loop = false
    private var shuffle = false
    private var bossbarEnabled = plugin.getConfigManager().isDefaultBossbarEnabled()
    private var startedAt = 0L
    private var pausedPositionMillis = 0L
    private var trackEndTask: BukkitTask? = null
    
    private val bossBar: BossBar = Bukkit.createBossBar(
        "Music Player",
        BarColor.WHITE,
        BarStyle.SOLID
    )
    
    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        bossBar.isVisible = false
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (isPlaying && bossbarEnabled) updateBossBar()
        }, 1L, 5L)
    }
    
    fun getQueue(): List<Track> = playQueue.toList()
    fun getCurrentIndex(): Int = currentTrackIndex
    fun isPlaying(): Boolean = isPlaying
    fun isPaused(): Boolean = isPaused
    fun isLoopEnabled(): Boolean = loop
    fun isBossbarEnabled(): Boolean = bossbarEnabled
    fun getCurrentTrack(): Track? = playQueue.getOrNull(currentTrackIndex)
    fun getPlaybackPositionSeconds(): Long {
        if (!isPlaying) return 0L
        val elapsedMillis = if (isPaused) {
            pausedPositionMillis
        } else {
            (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        }
        val seconds = elapsedMillis / 1000L
        val duration = getCurrentTrack()?.duration ?: 0L
        return if (duration > 0L) seconds.coerceAtMost(duration) else seconds
    }

    fun getPlaybackDurationSeconds(): Long = getCurrentTrack()?.duration ?: 0L

    fun seekTo(positionSeconds: Long): Boolean {
        val track = playQueue.getOrNull(currentTrackIndex) ?: return false
        if (!isPlaying) return false

        val targetSeconds = if (track.duration > 0L) {
            positionSeconds.coerceIn(0L, track.duration)
        } else {
            positionSeconds.coerceAtLeast(0L)
        }
        val targetMillis = targetSeconds.coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L
        pausedPositionMillis = targetMillis
        startedAt = System.currentTimeMillis() - targetMillis

        plugin.logger.info("[MusicPlayer] Seeking to ${targetSeconds}s: ${track.title}")
        if (!isPaused) {
            sendVideoToAllPlayers(recordHistory = false)
        }
        updateBossBar()
        return true
    }
    
    fun playTrack(youtubeUrl: String, title: String = "Playing...", author: String = ""): Boolean {
        return playTrack(Track(title, youtubeUrl, 0, author))
    }

    fun playTrack(track: Track): Boolean {
        playQueue.clear()
        playQueue.add(track)
        currentTrackIndex = 0
        isPlaying = true
        isPaused = false
        startedAt = System.currentTimeMillis()
        pausedPositionMillis = 0L
        
        plugin.logger.info("[MusicPlayer] Playing track: ${track.title} (${track.youtubeUrl})")
        sendVideoToAllPlayers()
        updateBossBar()
        
        return true
    }
    
    fun playPlaylist(
        playlist: Playlist,
        shuffleOverride: Boolean = false,
        startIndex: Int = 0
    ): Boolean {
        if (playlist.tracks.isEmpty()) return false
        
        playQueue.clear()
        loop = playlist.loop
        shuffle = shuffleOverride || playlist.shuffle
        
        val tracks = if (shuffle) {
            playlist.tracks.shuffled()
        } else {
            playlist.tracks.toList()
        }
        
        playQueue.addAll(tracks)
        currentTrackIndex = if (shuffle) 0 else startIndex.coerceIn(tracks.indices)
        isPlaying = true
        isPaused = false
        startedAt = System.currentTimeMillis()
        pausedPositionMillis = 0L
        
        val firstTrack = playQueue[0]
        plugin.logger.info("[MusicPlayer] Playing playlist: ${playlist.displayName} (${tracks.size} tracks)")
        sendVideoToAllPlayers()
        updateBossBar()
        
        return true
    }
    
    fun addToQueue(track: Track) {
        playQueue.add(track)
        plugin.logger.info("[MusicPlayer] Added to queue: ${track.title}")
        
        // If nothing is playing, start playing
        if (!isPlaying && playQueue.isNotEmpty()) {
            currentTrackIndex = playQueue.size - 1
            isPlaying = true
            isPaused = false
            startedAt = System.currentTimeMillis()
            pausedPositionMillis = 0L
            plugin.logger.info("[MusicPlayer] Starting playback: ${track.title}")
            sendVideoToAllPlayers()
            updateBossBar()
        }
    }

    fun insertIntoQueue(index: Int, track: Track) {
        if (playQueue.isEmpty() || !isPlaying) {
            addToQueue(track)
            return
        }
        val targetIndex = index.coerceIn(0, playQueue.size)
        playQueue.add(targetIndex, track)
        if (targetIndex <= currentTrackIndex) currentTrackIndex++
        plugin.logger.info("[MusicPlayer] Inserted into queue at $targetIndex: ${track.title}")
        updateBossBar()
    }
    
    fun addPlaylistToQueue(playlist: Playlist, shuffleOverride: Boolean = false) {
        val tracks = if (shuffleOverride) playlist.tracks.shuffled() else playlist.tracks
        
        val wasEmpty = playQueue.isEmpty()
        playQueue.addAll(tracks)
        
        if (wasEmpty && playQueue.isNotEmpty()) {
            currentTrackIndex = 0
            isPlaying = true
            isPaused = false
            startedAt = System.currentTimeMillis()
            pausedPositionMillis = 0L
            sendVideoToAllPlayers()
            updateBossBar()
        }
    }
    
    fun removeFromQueue(index: Int) {
        if (index !in playQueue.indices) return
        
        // If removing current track, skip to next
        if (index == currentTrackIndex && isPlaying) {
            playQueue.removeAt(index)
            if (playQueue.isEmpty()) {
                stopMusic()
            } else {
                if (currentTrackIndex >= playQueue.size) {
                    currentTrackIndex = 0
                }
                val track = playQueue[currentTrackIndex]
                startedAt = System.currentTimeMillis()
                pausedPositionMillis = 0L
                sendVideoToAllPlayers()
                updateBossBar()
            }
        } else {
            playQueue.removeAt(index)
            if (index < currentTrackIndex) {
                currentTrackIndex--
            }
            updateBossBar()
        }
    }
    
    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in playQueue.indices || toIndex !in playQueue.indices) return
        
        val track = playQueue.removeAt(fromIndex)
        playQueue.add(toIndex, track)
        
        // Update current index
        when {
            fromIndex == currentTrackIndex -> currentTrackIndex = toIndex
            fromIndex < currentTrackIndex && toIndex >= currentTrackIndex -> currentTrackIndex--
            fromIndex > currentTrackIndex && toIndex <= currentTrackIndex -> currentTrackIndex++
        }
        
        updateBossBar()
    }
    
    fun jumpToTrack(index: Int) {
        if (index !in playQueue.indices) return
        
        currentTrackIndex = index
        isPaused = false
        isPlaying = true
        startedAt = System.currentTimeMillis()
        pausedPositionMillis = 0L
        
        val track = playQueue[currentTrackIndex]
        plugin.logger.info("[MusicPlayer] Jumping to track: ${track.title}")
        sendVideoToAllPlayers()
        updateBossBar()
    }
    
    fun stopMusic() {
        plugin.logger.info("[MusicPlayer] Stopping music")
        isPlaying = false
        isPaused = false
        pausedPositionMillis = 0L
        playQueue.clear()
        currentTrackIndex = 0
        trackEndTask?.cancel()
        trackEndTask = null
        
        sendStopToAllPlayers()
        bossBar.isVisible = false
    }
    
    fun pauseMusic() {
        if (!isPlaying) return
        
        plugin.logger.info("[MusicPlayer] Pausing music")
        pausedPositionMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        isPaused = true
        trackEndTask?.cancel()
        trackEndTask = null
        sendStopToAllPlayers()
        updateBossBar()
    }
    
    fun resumeMusic() {
        if (!isPaused) return
        
        plugin.logger.info("[MusicPlayer] Resuming music")
        isPaused = false
        startedAt = System.currentTimeMillis() - pausedPositionMillis
        val track = playQueue.getOrNull(currentTrackIndex) ?: return
        sendVideoToAllPlayers(recordHistory = false)
        updateBossBar()
    }
    
    fun nextTrack() {
        if (!hasNext()) {
            stopMusic()
            return
        }
        
        if (currentTrackIndex < playQueue.size - 1) {
            currentTrackIndex++
        } else if (loop) {
            currentTrackIndex = 0
        } else {
            return
        }
        
        isPaused = false
        startedAt = System.currentTimeMillis()
        pausedPositionMillis = 0L
        val track = playQueue[currentTrackIndex]
        plugin.logger.info("[MusicPlayer] Next track: ${track.title}")
        sendVideoToAllPlayers()
        updateBossBar()
    }
    
    fun previousTrack() {
        // Music players conventionally restart the current track first. A
        // second press from its beginning moves to the previous queue item.
        if (isPlaying && getPlaybackPositionSeconds() > 1L) {
            seekTo(0L)
            return
        }
        if (!hasPrevious()) return
        
        if (currentTrackIndex > 0) {
            currentTrackIndex--
        } else if (loop) {
            currentTrackIndex = playQueue.size - 1
        } else {
            return
        }
        
        isPaused = false
        startedAt = System.currentTimeMillis()
        pausedPositionMillis = 0L
        val track = playQueue[currentTrackIndex]
        plugin.logger.info("[MusicPlayer] Previous track: ${track.title}")
        sendVideoToAllPlayers()
        updateBossBar()
    }
    
    fun hasNext(): Boolean {
        return currentTrackIndex < playQueue.size - 1 || loop
    }
    
    fun hasPrevious(): Boolean {
        return currentTrackIndex > 0 || loop
    }
    
    fun stopAll() {
        stopMusic()
    }

    fun toggleBossbar(enabled: Boolean) {
        bossbarEnabled = enabled
        updateBossBar()
    }

    fun setLoop(enabled: Boolean) {
        loop = enabled
        plugin.logger.info("[MusicPlayer] Queue repeat ${if (enabled) "enabled" else "disabled"}")
    }
    
    private fun sendVideoToAllPlayers(recordHistory: Boolean = true) {
        val players = Bukkit.getOnlinePlayers()
        val track = playQueue.getOrNull(currentTrackIndex)
        if (track == null) {
            plugin.logger.warning("[MusicPlayer] No current track!")
            return
        }
        plugin.logger.info("[MusicPlayer] Sending to ${players.size} players: ${track.youtubeUrl}")
        if (recordHistory) {
            plugin.getPlaylistManager().recordRecentlyPlayed(track)
        }
        players.forEach { player ->
            try {
                if (plugin.getPluginMessageManager().play(player, track, startedAt)) {
                    plugin.logger.info("[MusicPlayer] Sent to ${player.name}")
                }
            } catch (e: Exception) {
                plugin.logger.warning("[MusicPlayer] Failed to send to ${player.name}: ${e.message}")
            }
        }
        scheduleTrackEnd(track)
    }

    private fun scheduleTrackEnd(track: Track) {
        trackEndTask?.cancel()
        trackEndTask = null
        if (track.duration <= 0L || !isPlaying || isPaused) return

        val expectedStartedAt = startedAt
        val durationMillis = track.duration.coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L
        val elapsedMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        val remainingMillis = (durationMillis - elapsedMillis).coerceAtLeast(1L)
        val delayTicks = ((remainingMillis + 49L) / 50L).coerceAtLeast(1L)
        trackEndTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (isPlaying && !isPaused && startedAt == expectedStartedAt) {
                nextTrack()
            }
        }, delayTicks)
    }
    
    private fun sendStopToAllPlayers() {
        val players = Bukkit.getOnlinePlayers()
        plugin.logger.info("[MusicPlayer] Sending stop to ${players.size} players")
        
        players.forEach { player ->
            try {
                plugin.getPluginMessageManager().stop(player)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private fun updateBossBar() {
        val track = playQueue.getOrNull(currentTrackIndex)
        
        if (track == null || !isPlaying || !bossbarEnabled) {
            bossBar.isVisible = false
            return
        }
        
        val title = if (isPaused) {
            "${ChatColor.GRAY}⏸ ${track.title}"
        } else {
            "${ChatColor.WHITE}${track.title}"
        }
        
        bossBar.setTitle(title)
        bossBar.progress = if (track.duration > 0L) {
            val durationMillis = track.duration.coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L
            val elapsedMillis = if (isPaused) {
                pausedPositionMillis
            } else {
                (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            }
            (elapsedMillis.toDouble() / durationMillis.toDouble()).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        bossBar.isVisible = true
    }
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        bossBar.addPlayer(player)

        // Cinema's own server integration waits three seconds after login.
        // Sending screen/service packets earlier can race the client's world
        // teardown and Fabric channel initialization during a reconnect.
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            plugin.getPluginMessageManager().bootstrap(player)

            // Give the client one more tick to register the hidden screen before
            // loading its browser and seeking to the global playback position.
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (!player.isOnline || !isPlaying || isPaused) return@Runnable
                val track = playQueue.getOrNull(currentTrackIndex) ?: return@Runnable
                plugin.logger.info(
                    "[MusicPlayer] Syncing ${player.name} at ${getPlaybackPositionSeconds()}s"
                )
                plugin.getPluginMessageManager().play(player, track, startedAt)
            }, 2L)
        }, 60L)
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        bossBar.removePlayer(event.player)
    }
}



