package com.cloudstream.extensions.ftpbd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Document
import java.util.regex.Pattern
import kotlin.text.RegexOption
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Calendar

class FtpBdProvider : MainAPI() {
    override var mainUrl = "http://server3.ftpbd.net"
    override var name = "FtpBd"
    override val hasMainPage = true
    override var lang = "bn"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val year = Calendar.getInstance().get(Calendar.YEAR)

    override val mainPage = mainPageOf(
        "http://server3.ftpbd.net/FTP-3/Hindi%20Movies/$year/" to "Hindi Movies ($year)",
        "http://server3.ftpbd.net/FTP-3/English%20Movies/$year/" to "English Movies ($year)",
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
        val items = doc.select("tbody > tr:gt(1)")
        val animeList = items.mapNotNull { post ->
            val folderHtml = post.selectFirst("td.fb-n > a") ?: return@mapNotNull null
            val title = folderHtml.text().trim().removeSuffix("/")
            if (title.equals("Parent Directory", ignoreCase = true) || title.isBlank()) return@mapNotNull null
            
            val url = fixUrl(folderHtml.attr("href"), request.data)
            val thumbUrl = fixUrl("${url}a_AL_.jpg", request.data)
            
            newMovieSearchResponse(title, url, TvType.Movie) {
                this.posterUrl = thumbUrl
            }
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
            "http://server3.$baseDomain/FTP-3/Hindi%20Movies/",
            "http://server3.$baseDomain/FTP-3/English%20Movies/2025/",
            "http://server3.$baseDomain/FTP-3/English%20Movies/2024/",
            "http://server3.$baseDomain/FTP-3/English%20Movies/",
            "http://server3.$baseDomain/FTP-3/Animation%20Movies/",
            "http://server3.$baseDomain/FTP-1/TV%20Series/",
            "http://server3.$baseDomain/FTP-3/South%20Indian%20Movies/"
        )

        val responses = coroutineScope {
            searchPaths.map { path ->
                async {
                    try {
                        path to app.get(path, headers = commonHeaders, timeout = 10).document
                    } catch (_: Exception) {
                        path to null
                    }
                }
            }.awaitAll()
        }
        
        responses.forEach { (path, doc) ->
            if (doc != null) {
                try {
                    val items = doc.select("td.fb-n a, div.entry-content a, table tr a")
                    items.forEach { link ->
                        val title = link.text().trim()
                        if (title.contains(query, true)) {
                            val url = fixUrl(link.attr("href"), path)
                            if (!url.contains("?") && !url.endsWith("..")) {
                                searchResults.add(newMovieSearchResponse(title.removeSuffix("/"), url, TvType.Movie))
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
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

        val tableHtml = document.select("tbody > tr:gt(1)")
        val isTvSeries = url.contains("TV Series", ignoreCase = true) || url.contains("TV Serias", ignoreCase = true)

        if (isTvSeries) {
            val episodesData = mutableListOf<Episode>()
            var seasonNum = 0
            val name = title.ifBlank { fixedUrl.substringAfterLast("/").removeSuffix("/") }
            
            tableHtml.forEach {
                val aHtml = it.selectFirst("td.fb-n > a") ?: return@forEach
                val link = fixUrl(aHtml.attr("href"), fixedUrl)
                if (it.selectFirst("td.fb-i > img")?.attr("alt") == "folder") {
                    seasonNum++
                    seasonExtractor(link, episodesData, seasonNum)
                } else if (aHtml.selectFirst("a[href~=(?i)\\.(mkv|mp4|avi|ts|m4v|webm|mov)]") != null || aHtml.attr("href").matches(Regex(".*\\.(mkv|mp4|avi|ts|m4v|webm|mov)$", RegexOption.IGNORE_CASE))) {
                    val epTitle = aHtml.text()
                    episodesData.add(
                        newEpisode(link) {
                            this.name = epTitle
                            this.season = 1
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(name, fixedUrl, TvType.TvSeries, episodesData) {
                this.posterUrl = poster
            }
        } else {
            val folderHtml = tableHtml.selectFirst("td.fb-n > a[href~=(?i)\\.(mkv|mp4|avi|ts|m4v|webm|mov)]")
                ?: document.selectFirst("a[href~=(?i)\\.(mkv|mp4|avi|ts|m4v|webm|mov)]")
            
            val name = folderHtml?.text()?.toString() ?: title.ifBlank { fixedUrl.substringAfterLast("/").removeSuffix("/") }
            val link = if (folderHtml != null) fixUrl(folderHtml.attr("href"), fixedUrl) else fixedUrl
            
            return newMovieLoadResponse(name, fixedUrl, TvType.Movie, link) {
                this.posterUrl = poster
            }
        }
    }

    private suspend fun seasonExtractor(
        url: String, episodesData: MutableList<Episode>, seasonNum: Int
    ) {
        try {
            val doc = app.get(url, headers = commonHeaders, timeout = 10).document
            var episodeNum = 0
            doc.select("tbody > tr:gt(1) > td.fb-n > a[href~=(?i)\\.(mkv|mp4|avi|ts|m4v|webm|mov)]").forEach {
                episodeNum++
                val folderHtml = it
                val name = folderHtml.text()
                val link = fixUrl(folderHtml.attr("href"), url)
                episodesData.add(
                    newEpisode(link) {
                        this.name = name
                        this.season = seasonNum
                        this.episode = episodeNum
                    }
                )
            }
            if (episodeNum == 0) {
                doc.select("a[href~=(?i)\\.(mkv|mp4|avi|ts|m4v|webm|mov)]").forEach {
                    episodeNum++
                    val folderHtml = it
                    val name = folderHtml.text()
                    val link = fixUrl(folderHtml.attr("href"), url)
                    episodesData.add(
                        newEpisode(link) {
                            this.name = name
                            this.season = seasonNum
                            this.episode = episodeNum
                        }
                    )
                }
            }
        } catch (_: Exception) {}
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
            }
        )
        return true
    }

    private fun isVideoFile(href: String): Boolean {
        val h = href.lowercase()
        return listOf(".mkv", ".mp4", ".avi", ".ts", ".m4v", ".webm", ".mov").any { h.endsWith(it) }
    }
}