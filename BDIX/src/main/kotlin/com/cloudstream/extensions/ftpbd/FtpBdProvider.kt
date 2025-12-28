package com.cloudstream.extensions.ftpbd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import java.util.regex.Pattern
import kotlin.text.RegexOption

class FtpBdProvider : MainAPI() {
    override var mainUrl = "https://server3.ftpbd.net"
    override var name = "FtpBd"
    override val hasMainPage = true
    override var lang = "bn"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val baseDomain = "ftpbd.net"

    private fun fixUrl(url: String): String {
        if (url.isBlank()) return url
        var u = url.trim()
        val lastHttp = u.lastIndexOf("http://", ignoreCase = true)
        val lastHttps = u.lastIndexOf("https://", ignoreCase = true)
        val lastProtocol = if (lastHttp > lastHttps) lastHttp else lastHttps
        if (lastProtocol > 0) u = u.substring(lastProtocol)
        u = u.replace(Regex("http(s)?://http(s)?://", RegexOption.IGNORE_CASE), "http$1://")
        return u.replace(" ", "%20")
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()
        val searchResults = mutableListOf<SearchResponse>()
        
        val domain = baseDomain
        val searchPaths = listOf(
            "https://server3.$domain/FTP-3/Hindi%20Movies/2025/",
            "https://server3.$domain/FTP-3/Hindi%20Movies/2024/",
            "https://server3.$domain/FTP-3/Hindi%20Movies/"
        )

        searchPaths.forEach { path ->
            try {
                val doc = app.get(path).document
                val items = doc.select("td.fb-n a, div.entry-content a, table tr a")
                items.forEach { link ->
                    val title = link.text().trim()
                    if (title.contains(query, true)) {
                        val url = fixUrl(link.attr("abs:href"))
                        if (!url.contains("?") && !url.endsWith("..")) {
                            searchResults.add(newMovieSearchResponse(title, url, TvType.Movie))
                        }
                    }
                }
            } catch (e: Exception) { }
        }
        return searchResults.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fixedUrl = fixUrl(url)
        val document = app.get(fixedUrl).document
        val title = document.title().replace("Index of", "").trim()
        
        val episodes = mutableListOf<Episode>()
        document.select("a[href]").forEach { link ->
            val href = link.attr("abs:href")
            val text = link.text().trim()
            if (href.contains("?") || text.equals("parent directory", true)) return@forEach
            
            if (isVideoFile(href)) {
                episodes.add(newEpisode(href) {
                    this.name = text
                })
            }
        }

        if (episodes.isNotEmpty()) {
             return newTvSeriesLoadResponse(title, fixedUrl, TvType.TvSeries, episodes)
        }
        return null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url = fixUrl(data)
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = url
            ) {
                this.referer = ""
            }
        )
        return true
    }

    private fun isVideoFile(href: String): Boolean {
        val h = href.lowercase()
        return listOf(".mkv", ".mp4", ".avi", ".ts", ".m4v", ".webm", ".mov").any { h.endsWith(it) }
    }
}