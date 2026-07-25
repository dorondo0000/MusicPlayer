package kr.dorondo.cinematomusic.manager

import kr.dorondo.cinematomusic.MusicPlayer
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.security.SecureRandom
import java.util.Base64

class ConfigManager(private val plugin: MusicPlayer) {
    
    private lateinit var config: YamlConfiguration
    private val configFile = File(plugin.dataFolder, "config.yml")
    
    private var webServerEnabled = true
    private var webServerPort = 9090
    private var defaultBossbarEnabled = true
    private var adminPassword = ""
    
    fun load() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false)
        }
        
        config = YamlConfiguration.loadConfiguration(configFile)
        
        webServerEnabled = config.getBoolean("web-server.enabled", true)
        webServerPort = config.getInt("web-server.port", 9090)
        defaultBossbarEnabled = config.getBoolean("music.default-bossbar-enabled", true)

        adminPassword = config.getString("security.admin-password", "")?.trim().orEmpty()

        var configChanged = false
        if (adminPassword.isBlank()) {
            adminPassword = generateSecret()
            config.set("security.admin-password", adminPassword)
            configChanged = true
        }
        if (config.contains("web-server.client-base-url")) {
            config.set("web-server.client-base-url", null)
            configChanged = true
        }
        if (config.contains("security.playback-token")) {
            config.set("security.playback-token", null)
            configChanged = true
        }
        if (configChanged) {
            config.save(configFile)
            plugin.logger.info("Updated web security settings in plugins/MusicPlayer/config.yml")
        }
    }
    
    fun reload() {
        load()
    }
    
    fun isWebServerEnabled() = webServerEnabled
    fun getWebServerPort() = webServerPort
    fun isDefaultBossbarEnabled() = defaultBossbarEnabled
    fun getAdminPassword() = adminPassword

    fun setAdminPassword(password: String) {
        adminPassword = password
        config.set("security.admin-password", password)
        config.save(configFile)
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}


