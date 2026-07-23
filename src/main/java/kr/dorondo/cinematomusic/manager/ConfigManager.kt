package kr.dorondo.cinematomusic.manager

import kr.dorondo.cinematomusic.MusicPlayer
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ConfigManager(private val plugin: MusicPlayer) {
    
    private lateinit var config: YamlConfiguration
    private val configFile = File(plugin.dataFolder, "config.yml")
    
    private var webServerEnabled = true
    private var webServerPort = 9090
    private var clientBaseUrl = "http://localhost:9090"
    private var defaultBossbarEnabled = true
    
    fun load() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false)
        }
        
        config = YamlConfiguration.loadConfiguration(configFile)
        
        webServerEnabled = config.getBoolean("web-server.enabled", true)
        webServerPort = config.getInt("web-server.port", 9090)
        clientBaseUrl = config.getString("web-server.client-base-url", "http://localhost:$webServerPort")
            ?.trimEnd('/')
            ?: "http://localhost:$webServerPort"
        defaultBossbarEnabled = config.getBoolean("music.default-bossbar-enabled", true)
    }
    
    fun reload() {
        load()
    }
    
    fun isWebServerEnabled() = webServerEnabled
    fun getWebServerPort() = webServerPort
    fun getClientBaseUrl() = clientBaseUrl
    fun isDefaultBossbarEnabled() = defaultBossbarEnabled
}


