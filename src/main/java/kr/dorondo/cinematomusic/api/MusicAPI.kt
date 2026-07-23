package kr.dorondo.cinematomusic.api

import kr.dorondo.cinematomusic.manager.MusicPlayerManager
import kr.dorondo.cinematomusic.manager.PlaylistManager
import kr.dorondo.cinematomusic.model.Playlist
import kr.dorondo.cinematomusic.model.Track

/**
 * Public API for MusicPlayer
 * Use this API from other plugins or Skript
 * 
 * Note: This is a global player - all players hear the same music
 */
class MusicAPI(
    private val musicPlayerManager: MusicPlayerManager,
    private val playlistManager: PlaylistManager
) {
    
    /**
     * Play a track (server-wide)
     */
    fun playTrack(youtubeUrl: String, title: String = "Playing...", author: String = ""): Boolean {
        return musicPlayerManager.playTrack(youtubeUrl, title, author)
    }

    fun playTrack(track: Track): Boolean {
        return musicPlayerManager.playTrack(track)
    }
    
    /**
     * Play a playlist (server-wide)
     */
    fun playPlaylist(
        playlistName: String,
        shuffle: Boolean = false,
        startIndex: Int = 0
    ): Boolean {
        val playlist = playlistManager.getPlaylist(playlistName) ?: return false
        return musicPlayerManager.playPlaylist(playlist, shuffle, startIndex)
    }
    
    /**
     * Stop music (server-wide)
     */
    fun stopMusic() {
        musicPlayerManager.stopMusic()
    }
    
    /**
     * Pause music (server-wide)
     */
    fun pauseMusic() {
        musicPlayerManager.pauseMusic()
    }
    
    /**
     * Resume music (server-wide)
     */
    fun resumeMusic() {
        musicPlayerManager.resumeMusic()
    }
    
    /**
     * Skip to next track (server-wide)
     */
    fun nextTrack() {
        musicPlayerManager.nextTrack()
    }
    
    /**
     * Go to previous track (server-wide)
     */
    fun previousTrack() {
        musicPlayerManager.previousTrack()
    }
    
    /**
     * Add track to queue
     */
    fun addToQueue(track: Track) {
        musicPlayerManager.addToQueue(track)
    }

    fun addToQueue(
        youtubeUrl: String,
        title: String = "Playing...",
        author: String = "",
        duration: Long = 0L
    ) {
        musicPlayerManager.addToQueue(Track(title, youtubeUrl, duration, author))
    }

    fun insertIntoQueue(index: Int, track: Track) {
        musicPlayerManager.insertIntoQueue(index, track)
    }

    fun removeFromQueue(index: Int) {
        musicPlayerManager.removeFromQueue(index)
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        musicPlayerManager.moveInQueue(fromIndex, toIndex)
    }
    
    /**
     * Jump to specific track in queue
     */
    fun jumpToTrack(index: Int) {
        musicPlayerManager.jumpToTrack(index)
    }

    /**
     * Seek the current track for every listener.
     */
    fun seekTo(positionSeconds: Long): Boolean {
        return musicPlayerManager.seekTo(positionSeconds)
    }

    fun getPlaybackPositionSeconds(): Long {
        return musicPlayerManager.getPlaybackPositionSeconds()
    }

    fun getPlaybackDurationSeconds(): Long {
        return musicPlayerManager.getPlaybackDurationSeconds()
    }
    
    /**
     * Get all playlists
     */
    fun getPlaylists(): List<Playlist> {
        return playlistManager.getAllPlaylists()
    }
    
    /**
     * Get a specific playlist
     */
    fun getPlaylist(name: String): Playlist? {
        return playlistManager.getPlaylist(name)
    }

    fun getRecentlyPlayed(): List<Track> {
        return playlistManager.getRecentlyPlayed()
    }
    
    /**
     * Check if music is playing
     */
    fun isPlaying(): Boolean {
        return musicPlayerManager.isPlaying()
    }
    
    /**
     * Check if music is paused
     */
    fun isPaused(): Boolean {
        return musicPlayerManager.isPaused()
    }
    
    /**
     * Get current track
     */
    fun getCurrentTrack(): Track? {
        return musicPlayerManager.getCurrentTrack()
    }
    
    /**
     * Get current queue
     */
    fun getQueue(): List<Track> {
        return musicPlayerManager.getQueue()
    }
    
    /**
     * Get current track index
     */
    fun getCurrentIndex(): Int {
        return musicPlayerManager.getCurrentIndex()
    }

    fun toggleBossbar(enabled: Boolean) {
        musicPlayerManager.toggleBossbar(enabled)
    }

    fun setLoop(enabled: Boolean) {
        musicPlayerManager.setLoop(enabled)
    }

    fun isLoopEnabled(): Boolean {
        return musicPlayerManager.isLoopEnabled()
    }
}
