package com.cloudstream.extensions.ftpbd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Document
import java.util.regex.Pattern
import kotlin.text.RegexOption

class FtpBdProvider : MainAPI() {
    override var mainUrl = "http://server3.ftpbd.net"
    override var name = "FtpBd"
    override val hasMainPage = true
    override var lang = "bn"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "http://server3.ftpbd.net/FTP-3/Hindi%20Movies/2025/" to "Hindi Movies (2025)",
        "http://server3.ftpbd.net/FTP-3/English%20Movies/2025/" to "English Movies (2025)",
        "http://server3.ftpbd.net/FTP-3/Animation%20Movies/" to "Animation Movies",
        "http://server3.ftpbd.net/FTP-1/TV%20Series/" to "TV Series",
        "http://server3.ftpbd.net/FTP-3/South%20Indian%20Movies/" to "South Indian Movies"
    )

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = try {
            app.get(request.data, headers = commonHeaders, timeout = 10).document
        } catch (e: Exception) {
            return newHomePageResponse(request.name, emptyList())
        }
        val items = doc.select("td.fb-n a, div.entry-content a, table tr a")
        val animeList = items.mapNotNull { link ->
            val title = link.text().trim()
            val url = fixUrl(link.attr("href"), request.data)
            if (!url.contains("?") && !url.endsWith("..") && title.isNotEmpty()) {
                newMovieSearchResponse(title, url, TvType.Movie)
            } else null
        }
        return newHomePageResponse(request.name, animeList)
    }

    private val baseDomain = "ftpbd.net"

    private fun fixUrl(url: String, baseUrl: String = mainUrl): String {
        if (url.isBlank()) return url
        var u = url.trim()
        if (!u.startsWith("http")) {
            u = if (u.startsWith("//")) "http:$u"
            else if (u.startsWith("/")) {
                val host = if (baseUrl.contains("//")) {
                    baseUrl.substringBefore("/", "http://")
                } else baseUrl
                "$host$u"
            }
            else "$baseUrl/$u"
        }
        return u.replace(" ", "%20").replace(Regex("(?<!:)/{2,}"), "/")
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()
        val searchResults = mutableListOf<SearchResponse>()
        
        val searchPaths = listOf(
            "http://server3.$baseDomain/FTP-3/Hindi%20Movies/2025/",
            "http://server3.$baseDomain/FTP-3/Hindi%20Movies/2024/",
            "http://server3.$baseDomain/FTP-3/Hindi%20Movies/"
        )

        searchPaths.forEach { path ->
            try {
                val doc = app.get(path, headers = commonHeaders, timeout = 10).document
                val items = doc.select("td.fb-n a, div.entry-content a, table tr a")
                items.forEach { link ->
                    val title = link.text().trim()
                    if (title.contains(query, true)) {
                        val url = fixUrl(link.attr("href"), path)
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
        val document = try {
            app.get(fixedUrl, headers = commonHeaders, timeout = 15).document
        } catch (e: Exception) {
            return null
        }
        val title = document.title().replace("Index of", "").trim()
        
        val poster = document.selectFirst("img[src~=(?i)a11|poster|banner|thumb]")?.attr("src")?.let { fixUrl(it, fixedUrl) }
            ?: fixUrl("poster.jpg", fixedUrl)

        val episodes = mutableListOf<Episode>()
        val visited = mutableSetOf<String>()
        
        parseDirectoryRecursive(document, 3, episodes, visited, fixedUrl)
        
        if (episodes.isNotEmpty()) {
             return newTvSeriesLoadResponse(title, fixedUrl, TvType.TvSeries, episodes.sortedBy { it.name }) {
                 this.posterUrl = poster
             }
        }
        return null
    }

    private suspend fun parseDirectoryRecursive(document: Document, depth: Int, episodes: MutableList<Episode>, visited: MutableSet<String>, currentUrl: String) {
        if (!visited.add(currentUrl)) return

        val links = document.select("a[href]")
        val files = mutableListOf<Pair<String, String>>()
        val dirs = mutableListOf<String>()

        links.forEach { element ->
            val href = element.attr("href")?.let { fixUrl(it, currentUrl) } ?: ""
            val text = element.text().trim()
            if (href.contains("?") || text.equals("parent directory", true) || href.endsWith("../")) return@forEach
            
            if (isVideoFile(href)) {
                files.add(text to href)
            } else if (href.endsWith("/")) {
                dirs.add(href)
            }
        }

        files.forEach { (name, url) ->
            episodes.add(newEpisode(url) {
                this.name = name
            })
        }

        if (depth > 0 && files.isEmpty()) {
            dirs.forEach { dirUrl ->
                try {
                    val doc = app.get(dirUrl, headers = commonHeaders, timeout = 5).document
                    parseDirectoryRecursive(doc, depth - 1, episodes, visited, dirUrl)
                } catch (e: Exception) {}
            }
        }
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
                this.referer = "$mainUrl/"
                this.headers = commonHeaders
            }
        )
        return true
    }

    private fun isVideoFile(href: String): Boolean {
        val h = href.lowercase()
        return listOf(".mkv", ".mp4", ".avi", ".ts", ".m4v", ".webm", ".mov").any { h.endsWith(it) }
    }
}