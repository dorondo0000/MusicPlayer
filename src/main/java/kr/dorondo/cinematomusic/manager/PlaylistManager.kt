package kr.dorondo.cinematomusic.manager

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kr.dorondo.cinematomusic.MusicPlayer
import kr.dorondo.cinematomusic.model.Playlist
import kr.dorondo.cinematomusic.model.Track
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap

class PlaylistManager(private val plugin: MusicPlayer) {
    
    private val playlists = ConcurrentHashMap<String, Playlist>()
    private val playlistsFolder = File(plugin.dataFolder, "playlists")
    private val recentlyPlayedFile = File(plugin.dataFolder, "recently-played.yml")
    private val recentlyPlayed = mutableListOf<Track>()
    private val gson = Gson()
    private val httpClient = HttpClient.newHttpClient()
    
    fun load() {
        if (!playlistsFolder.exists()) {
            playlistsFolder.mkdirs()
        }
        
        playlists.clear()
        
        playlistsFolder.listFiles()?.forEach { file ->
            if (file.extension == "yml") {
                loadPlaylistFromFile(file)
            }
        }
        loadRecentlyPlayed()
        
        plugin.logger.info("Loaded ${playlists.size} playlist(s)")
    }

    private fun loadRecentlyPlayed() {
        recentlyPlayed.clear()
        if (!recentlyPlayedFile.exists()) return
        val yaml = YamlConfiguration.loadConfiguration(recentlyPlayedFile)
        yaml.getMapList("tracks").take(30).forEach { trackMap ->
            val url = trackMap["url"]?.toString() ?: return@forEach
            recentlyPlayed.add(
                Track(
                    title = trackMap["title"]?.toString() ?: "Unknown",
                    youtubeUrl = url,
                    duration = (trackMap["duration"] as? Number)?.toLong() ?: 0L,
                    author = trackMap["author"]?.toString() ?: ""
                )
            )
        }
    }

    @Synchronized
    fun recordRecentlyPlayed(track: Track) {
        recentlyPlayed.removeAll { it.youtubeUrl == track.youtubeUrl }
        recentlyPlayed.add(0, track)
        while (recentlyPlayed.size > 30) recentlyPlayed.removeAt(recentlyPlayed.lastIndex)

        val yaml = YamlConfiguration()
        yaml.set("tracks", recentlyPlayed.map {
            mapOf(
                "title" to it.title,
                "url" to it.youtubeUrl,
                "author" to it.author,
                "duration" to it.duration
            )
        })
        yaml.save(recentlyPlayedFile)
    }

    @Synchronized
    fun getRecentlyPlayed(): List<Track> = recentlyPlayed.toList()
    
    private fun loadPlaylistFromFile(file: File) {
        try {
            val yaml = YamlConfiguration.loadConfiguration(file)
            val name = file.nameWithoutExtension
            val displayName = yaml.getString("display-name") ?: name
            val loop = yaml.getBoolean("loop", false)
            val shuffle = yaml.getBoolean("shuffle", false)
            
            val playlist = Playlist(name, displayName, mutableListOf(), loop, shuffle)
            
            val tracksList = yaml.getMapList("tracks")
            tracksList.forEach { trackMap ->
                val title = trackMap["title"]?.toString() ?: "Unknown"
                val url = trackMap["url"]?.toString() ?: return@forEach
                val author = trackMap["author"]?.toString() ?: ""
                val duration = (trackMap["duration"] as? Number)?.toLong() ?: 0L
                
                playlist.addTrack(Track(title, url, duration, author))
            }
            
            playlists[name] = playlist
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load playlist from ${file.name}: ${e.message}")
        }
    }
    
    fun savePlaylist(playlist: Playlist) {
        val file = File(playlistsFolder, "${playlist.name}.yml")
        val yaml = YamlConfiguration()
        
        yaml.set("display-name", playlist.displayName)
        yaml.set("loop", playlist.loop)
        yaml.set("shuffle", playlist.shuffle)
        
        val tracksList = playlist.tracks.map { track ->
            mapOf(
                "title" to track.title,
                "url" to track.youtubeUrl,
                "author" to track.author,
                "duration" to track.duration
            )
        }
        yaml.set("tracks", tracksList)
        
        yaml.save(file)
        playlists[playlist.name] = playlist
    }
    
    fun deletePlaylist(name: String): Boolean {
        val file = File(playlistsFolder, "$name.yml")
        val deleted = file.delete()
        if (deleted) {
            playlists.remove(name)
        }
        return deleted
    }
    
    fun getPlaylist(name: String): Playlist? {
        return playlists[name]
    }
    
    fun getAllPlaylists(): List<Playlist> {
        return playlists.values.sortedBy { it.displayName.lowercase() }
    }
    
    fun createPlaylist(name: String, displayName: String): Playlist {
        val playlist = Playlist(name, displayName)
        savePlaylist(playlist)
        return playlist
    }

    fun updateDisplayName(name: String, displayName: String): Playlist? {
        val current = playlists[name] ?: return null
        val trimmed = displayName.trim()
        if (trimmed.isBlank() || trimmed.length > 100) return null
        val updated = current.copy(displayName = trimmed)
        savePlaylist(updated)
        return updated
    }
    
    fun importFromYouTube(playlistUrl: String, name: String): Playlist? {
        val displayName = name.trim()
        if (displayName.isBlank() || displayName.length > 100) {
            plugin.logger.warning("Invalid playlist name: $name")
            return null
        }
        val safeName = displayName
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^A-Za-z0-9가-힣_-]"), "")
            .trim('-', '_')
            .take(64)
            .ifBlank { "playlist-${System.currentTimeMillis()}" }

        val playlistId = extractPlaylistId(playlistUrl) ?: run {
            plugin.logger.warning("Invalid YouTube playlist URL: $playlistUrl")
            return null
        }

        return try {
            val url = "https://www.youtube.com/playlist?list=$playlistId&hl=en"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                plugin.logger.warning("YouTube playlist request failed with HTTP ${response.statusCode()}")
                return null
            }

            val jsonText = extractInitialData(response.body()) ?: run {
                plugin.logger.warning("Could not find ytInitialData in YouTube playlist page")
                return null
            }
            val root = gson.fromJson(jsonText, JsonElement::class.java)
            val tracks = mutableListOf<Track>()
            val seenIds = LinkedHashSet<String>()
            collectPlaylistTracks(root, tracks, seenIds)

            if (tracks.isEmpty()) {
                plugin.logger.warning("No playable videos found in YouTube playlist")
                return null
            }

            val youtubeTitle = findPlaylistTitle(root)
            // The administrator's chosen name is the library name. YouTube's
            // title is source metadata and must not silently overwrite it.
            val playlist = Playlist(safeName, displayName, tracks)
            savePlaylist(playlist)
            plugin.logger.info(
                "Imported ${tracks.size} track(s) as '$displayName'" +
                    (youtubeTitle?.let { " (YouTube: '$it')" } ?: "")
            )
            playlist
        } catch (e: Exception) {
            plugin.logger.warning("Failed to import YouTube playlist: ${e.message}")
            null
        }
    }

    private fun extractPlaylistId(url: String): String? {
        return Regex("[?&]list=([A-Za-z0-9_-]+)")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?: url.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,}")) }
    }

    private fun extractInitialData(html: String): String? {
        val markers = listOf("var ytInitialData = ", "ytInitialData = ")
        for (marker in markers) {
            val markerIndex = html.indexOf(marker)
            if (markerIndex < 0) continue
            val start = html.indexOf('{', markerIndex + marker.length)
            if (start < 0) continue
            extractBalancedJson(html, start)?.let { return it }
        }
        return null
    }

    private fun extractBalancedJson(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun collectPlaylistTracks(
        element: JsonElement,
        tracks: MutableList<Track>,
        seenIds: MutableSet<String>
    ) {
        when {
            element.isJsonArray -> element.asJsonArray.forEach {
                collectPlaylistTracks(it, tracks, seenIds)
            }
            element.isJsonObject -> {
                val objectValue = element.asJsonObject
                objectValue.getAsJsonObject("playlistVideoRenderer")?.let { renderer ->
                    parsePlaylistTrack(renderer)?.let { pair ->
                        if (seenIds.add(pair.first)) tracks.add(pair.second)
                    }
                }
                objectValue.getAsJsonObject("lockupViewModel")?.let { lockup ->
                    parseLockupTrack(lockup)?.let { pair ->
                        if (seenIds.add(pair.first)) tracks.add(pair.second)
                    }
                }
                objectValue.entrySet().forEach { (_, child) ->
                    collectPlaylistTracks(child, tracks, seenIds)
                }
            }
        }
    }

    private fun parsePlaylistTrack(renderer: JsonObject): Pair<String, Track>? {
        val videoId = renderer.get("videoId")?.asString ?: return null
        val title = textFrom(renderer.get("title")) ?: return null
        val author = textFrom(renderer.get("shortBylineText")) ?: ""
        val duration = renderer.get("lengthSeconds")?.asString?.toLongOrNull() ?: 0L
        return videoId to Track(
            title = title,
            youtubeUrl = "https://www.youtube.com/watch?v=$videoId",
            duration = duration,
            author = author
        )
    }

    private fun parseLockupTrack(lockup: JsonObject): Pair<String, Track>? {
        if (lockup.get("contentType")?.asString != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val videoId = lockup.get("contentId")?.asString ?: return null
        val metadata = lockup.getAsJsonObject("metadata")
            ?.getAsJsonObject("lockupMetadataViewModel")
            ?: return null
        val title = metadata.getAsJsonObject("title")
            ?.get("content")
            ?.asString
            ?: return null
        val author = metadata.getAsJsonObject("metadata")
            ?.getAsJsonObject("contentMetadataViewModel")
            ?.getAsJsonArray("metadataRows")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonArray("metadataParts")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("text")
            ?.get("content")
            ?.asString
            ?: ""
        val durationText = findBadgeDuration(lockup)
        return videoId to Track(
            title = title,
            youtubeUrl = "https://www.youtube.com/watch?v=$videoId",
            duration = durationText?.let(::parseDuration) ?: 0L,
            author = author
        )
    }

    private fun findBadgeDuration(element: JsonElement): String? {
        if (element.isJsonObject) {
            val objectValue = element.asJsonObject
            objectValue.getAsJsonObject("thumbnailBadgeViewModel")
                ?.get("text")
                ?.asString
                ?.takeIf { it.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?")) }
                ?.let { return it }
            for ((_, child) in objectValue.entrySet()) {
                findBadgeDuration(child)?.let { return it }
            }
        } else if (element.isJsonArray) {
            for (child in element.asJsonArray) {
                findBadgeDuration(child)?.let { return it }
            }
        }
        return null
    }

    private fun parseDuration(text: String): Long {
        return text.split(':').fold(0L) { total, part -> total * 60 + part.toLong() }
    }

    private fun textFrom(element: JsonElement?): String? {
        if (element == null || !element.isJsonObject) return null
        val objectValue = element.asJsonObject
        objectValue.get("simpleText")?.let { return it.asString }
        val runs = objectValue.getAsJsonArray("runs") ?: return null
        return runs.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
            .takeIf { it.isNotBlank() }
    }

    private fun findPlaylistTitle(element: JsonElement): String? {
        if (element.isJsonObject) {
            val objectValue = element.asJsonObject
            objectValue.getAsJsonObject("playlistMetadataRenderer")
                ?.get("title")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            for ((_, child) in objectValue.entrySet()) {
                findPlaylistTitle(child)?.let { return it }
            }
        } else if (element.isJsonArray) {
            for (child in element.asJsonArray) {
                findPlaylistTitle(child)?.let { return it }
            }
        }
        return null
    }
}


