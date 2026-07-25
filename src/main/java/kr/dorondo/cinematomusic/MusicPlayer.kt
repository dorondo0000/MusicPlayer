package kr.dorondo.cinematomusic

import kr.dorondo.cinematomusic.api.MusicAPI
import kr.dorondo.cinematomusic.manager.ConfigManager
import kr.dorondo.cinematomusic.manager.PlaylistManager
import kr.dorondo.cinematomusic.manager.MusicPlayerManager
import kr.dorondo.cinematomusic.manager.WebServerManager
import kr.dorondo.cinematomusic.manager.PluginMessageManager
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
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

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!command.name.equals("musicplayer", ignoreCase = true)) return false
        if (!sender.hasPermission("musicplayer.admin")) {
            sender.sendMessage("§c권한이 없습니다.")
            return true
        }
        if (args.size < 2 || !args[0].equals("password", ignoreCase = true)) {
            sender.sendMessage("§e사용법: /$label password <새 비밀번호>")
            return true
        }

        val password = args.drop(1).joinToString(" ")
        if (password.length < 8) {
            sender.sendMessage("§c비밀번호는 8자 이상이어야 합니다.")
            return true
        }

        configManager.setAdminPassword(password)
        if (::webServerManager.isInitialized) {
            webServerManager.invalidateSessions()
        }
        sender.sendMessage("§aMusicPlayer 웹 관리자 비밀번호를 변경했습니다. 기존 로그인은 모두 해제됩니다.")
        return true
    }
    
    fun getConfigManager() = configManager
    fun getPlaylistManager() = playlistManager
    fun getMusicPlayerManager() = musicPlayerManager
    fun getWebServerManager() = webServerManager
    fun getPluginMessageManager() = pluginMessageManager
}

