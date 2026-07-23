package kr.dorondo.cinematomusic.util

import com.google.gson.JsonParser
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object YouTubeUtil {
    
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    
    private val VIDEO_ID_PATTERNS = listOf(
        Pattern.compile("(?:youtube\\.com/watch\\?v=)([a-zA-Z0-9_-]{11})"),
        Pattern.compile("(?:youtu\\.be/)([a-zA-Z0-9_-]{11})"),
        Pattern.compile("(?:youtube\\.com/embed/)([a-zA-Z0-9_-]{11})"),
        Pattern.compile("(?:youtube\\.com/v/)([a-zA-Z0-9_-]{11})")
    )
    
    /**
     * Extract video ID from YouTube URL
     */
    fun extractVideoId(url: String): String? {
        println("[YouTubeUtil] Extracting video ID from: $url")
        for (pattern in VIDEO_ID_PATTERNS) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                val videoId = matcher.group(1)
                println("[YouTubeUtil] Found video ID: $videoId")
                return videoId
            }
        }
        println("[YouTubeUtil] No video ID found")
        return null
    }
    
    /**
     * Get video metadata from YouTube
     * Uses multiple methods for maximum compatibility
     */
    fun getVideoInfo(url: String): VideoInfo? {
        println("[YouTubeUtil] Getting video info for: $url")
        
        val videoId = extractVideoId(url)
        if (videoId == null) {
            println("[YouTubeUtil] Failed to extract video ID")
            return null
        }
        
        // Method 1: Try oEmbed API
        try {
            println("[YouTubeUtil] Trying oEmbed API...")
            val info = getInfoFromOEmbed(videoId)
            if (info != null) {
                println("[YouTubeUtil] oEmbed success: ${info.title}")
                return info
            }
        } catch (e: Exception) {
            println("[YouTubeUtil] oEmbed failed: ${e.message}")
            e.printStackTrace()
        }
        
        // Method 2: Try scraping from YouTube page
        try {
            println("[YouTubeUtil] Trying page scraping...")
            val info = getInfoFromPage(videoId)
            if (info != null) {
                println("[YouTubeUtil] Page scraping success: ${info.title}")
                return info
            }
        } catch (e: Exception) {
            println("[YouTubeUtil] Page scraping failed: ${e.message}")
            e.printStackTrace()
        }
        
        // Method 3: Fallback - use video ID as title
        println("[YouTubeUtil] Using fallback method")
        return VideoInfo("YouTube Video", "Unknown", videoId)
    }
    
    private fun getInfoFromOEmbed(videoId: String): VideoInfo? {
        val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
        println("[YouTubeUtil] oEmbed URL: $oembedUrl")
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create(oembedUrl))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .GET()
            .build()
        
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        println("[YouTubeUtil] oEmbed response code: ${response.statusCode()}")
        
        if (response.statusCode() == 200) {
            val body = response.body()
            println("[YouTubeUtil] oEmbed response: ${body.take(200)}...")
            
            val json = JsonParser.parseString(body).asJsonObject
            
            val title = json.get("title")?.asString
            val author = json.get("author_name")?.asString ?: "Unknown"
            
            if (title != null) {
                return VideoInfo(title, author, videoId, getDurationSeconds(videoId))
            }
        }
        
        return null
    }
    
    private fun getInfoFromPage(videoId: String): VideoInfo? {
        val pageUrl = "https://www.youtube.com/watch?v=$videoId"
        println("[YouTubeUtil] Page URL: $pageUrl")
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create(pageUrl))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET()
            .build()
        
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        println("[YouTubeUtil] Page response code: ${response.statusCode()}")
        
        if (response.statusCode() == 200) {
            val html = response.body()
            
            // Try to extract title from meta tags
            var title: String? = null
            var author: String? = null
            
            // Look for og:title
            val titlePattern = Pattern.compile("<meta property=\"og:title\" content=\"([^\"]+)\"")
            val titleMatcher = titlePattern.matcher(html)
            if (titleMatcher.find()) {
                title = URLDecoder.decode(titleMatcher.group(1), StandardCharsets.UTF_8)
                println("[YouTubeUtil] Found title from og:title: $title")
            }
            
            // Look for channel name
            val authorPattern = Pattern.compile("<link itemprop=\"name\" content=\"([^\"]+)\"")
            val authorMatcher = authorPattern.matcher(html)
            if (authorMatcher.find()) {
                author = URLDecoder.decode(authorMatcher.group(1), StandardCharsets.UTF_8)
                println("[YouTubeUtil] Found author: $author")
            }
            
            // Alternative: look for JSON data
            if (title == null) {
                val jsonPattern = Pattern.compile("\"title\":\"([^\"]+)\"")
                val jsonMatcher = jsonPattern.matcher(html)
                if (jsonMatcher.find()) {
                    title = jsonMatcher.group(1)
                        .replace("\\\\u0026", "&")
                        .replace("\\\\", "")
                    println("[YouTubeUtil] Found title from JSON: $title")
                }
            }
            
            if (title != null) {
                return VideoInfo(
                    title = cleanText(title),
                    author = author?.let { cleanText(it) } ?: "Unknown",
                    videoId = videoId,
                    duration = extractDuration(html)
                )
            }
        }
        
        return null
    }
    
    private fun cleanText(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    private fun getDurationSeconds(videoId: String): Long {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.youtube.com/watch?v=$videoId"))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) extractDuration(response.body()) else 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun extractDuration(html: String): Long {
        return Pattern.compile("\"lengthSeconds\":\"(\\d+)\"")
            .matcher(html)
            .let { matcher -> if (matcher.find()) matcher.group(1).toLongOrNull() ?: 0L else 0L }
    }
    
    data class VideoInfo(
        val title: String,
        val author: String,
        val videoId: String,
        val duration: Long = 0L
    )
}
