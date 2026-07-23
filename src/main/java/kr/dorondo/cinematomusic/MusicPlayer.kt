package kr.dorondo.cinematomusic

import kr.dorondo.cinematomusic.api.MusicAPI
import kr.dorondo.cinematomusic.manager.ConfigManager
import kr.dorondo.cinematomusic.manager.PlaylistManager
import kr.dorondo.cinematomusic.manager.MusicPlayerManager
import kr.dorondo.cinematomusic.manager.WebServerManager
import kr.dorondo.cinematomusic.manager.PluginMessageManager
import org.bukkit.plugin.java.JavaPlugin

class MusicPlayer : JavaPlugin() {
    
    companion object {
        @JvmStatic
        lateinit var instance: MusicPlayer
            private set
        
        @JvmStatic
        lateinit var api: MusicAPI
            private set
    }
    
    private lateinit var configManager: ConfigManager
    private lateinit var playlistManager: PlaylistManager
    private lateinit var musicPlayerManager: MusicPlayerManager
    private lateinit var webServerManager: WebServerManager
    private lateinit var pluginMessageManager: PluginMessageManager
    
    override fun onEnable() {
        instance = this
        
        logger.info("MusicPlayer loading...")
        
        // Configuration must be loaded before managers read their defaults.
        configManager = ConfigManager(this)
        configManager.load()

        // Initialize managers
        playlistManager = PlaylistManager(this)
        musicPlayerManager = MusicPlayerManager(this)
        webServerManager = WebServerManager(this)
        pluginMessageManager = PluginMessageManager(this)
        
        // Initialize API
        api = MusicAPI(musicPlayerManager, playlistManager)
        
        playlistManager.load()
        
        // Register plugin messaging
        pluginMessageManager.register()
        
        // Start web server
        if (configManager.isWebServerEnabled()) {
            webServerManager.start()
        }
        
        logger.info("MusicPlayer enabled successfully!")
        logger.info("Web dashboard: http://localhost:${configManager.getWebServerPort()}")
    }
    
    override fun onDisable() {
        logger.info("MusicPlayer disabling...")
        
        // Stop music for all players
        musicPlayerManager.stopAll()
        
        // Stop web server
        if (::webServerManager.isInitialized) {
            webServerManager.stop()
        }
        
        // Unregister plugin messaging
        if (::pluginMessageManager.isInitialized) {
            pluginMessageManager.unregister()
        }
        
        logger.info("MusicPlayer disabled!")
    }
    
    fun getConfigManager() = configManager
    fun getPlaylistManager() = playlistManager
    fun getMusicPlayerManager() = musicPlayerManager
    fun getWebServerManager() = webServerManager
    fun getPluginMessageManager() = pluginMessageManager
}

