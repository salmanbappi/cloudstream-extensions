package com.cloudstream.extensions.dhakaflix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.util.regex.Pattern
import kotlin.text.RegexOption

class DhakaFlixProvider : MainAPI() {
    override var mainUrl = "http://172.16.50.9"
    override var name = "DhakaFlix"
    override val hasMainPage = true
    override var lang = "bn"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "http://172.16.50.14/DHAKA-FLIX-14/Hindi%20Movies/%282025%29/" to "Hindi Movies (2025)",
        "http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/%282025%29/" to "English Movies (2025)",
        "http://172.16.50.14/DHAKA-FLIX-14/English%20Movies%20%281080p%29/%282025%29%201080p/" to "English Movies 1080p (2025)",
        "http://172.16.50.14/DHAKA-FLIX-14/SOUTH%20INDIAN%20MOVIES/South%20Movies/2025/" to "South Indian Movies (2025)",
        "http://172.16.50.12/DHAKA-FLIX-12/TV-WEB-Series/" to "TV & Web Series",
        "http://172.16.50.9/DHAKA-FLIX-9/Anime%20%26%20Cartoon%20TV%20Series/" to "Anime & Cartoon"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data).document
        val animeList = mutableListOf<SearchResponse>()
        
        val cards = doc.select("div.card")
        if (cards.isNotEmpty()) {
            cards.forEach { card ->
                val link = card.selectFirst("h5 a")
                val title = link?.text() ?: ""
                val url = link?.attr("abs:href") ?: ""
                if (title.isNotEmpty() && url.isNotEmpty()) {
                    val img = card.selectFirst("img[src~=(?i)a11|a_al|poster|banner|thumb], img:not([src~=(?i)back|folder|parent|icon|/icons/])")
                    val posterUrl = (img?.attr("abs:data-src")?.takeIf { it.isNotEmpty() } 
                        ?: img?.attr("abs:data-lazy-src")?.takeIf { it.isNotEmpty() }
                        ?: img?.attr("abs:src") ?: "").replace(" ", "%20")
                        
                    animeList.add(newMovieSearchResponse(title, url, TvType.Movie) {
                        this.posterUrl = posterUrl
                    })
                }
            }
        } else {
            doc.select("a").forEach { element ->
                val title = element.text()
                val url = element.attr("abs:href")
                if (isValidDirectoryItem(title, url)) {
                    val cleanTitle = if (title.endsWith("/")) title.dropLast(1) else title
                    val finalUrl = if (url.endsWith("/")) url else "$url/"
                    val posterUrl = (finalUrl + "a_AL_.jpg").replace(" ", "%20")
                    
                    animeList.add(newMovieSearchResponse(cleanTitle, url, TvType.Movie) {
                        this.posterUrl = posterUrl
                    })
                }
            }
        }
        
        return newHomePageResponse(request.name, animeList)
    }

    private fun isValidDirectoryItem(title: String, url: String): Boolean {
        val lowerTitle = title.lowercase()
        if (isIgnored(lowerTitle)) return false
        if (url.contains("../") || url.contains("?")) return false
        return true
    }

    private val servers = listOf(
        "http://172.16.50.14" to "DHAKA-FLIX-14",
        "http://172.16.50.12" to "DHAKA-FLIX-12",
        "http://172.16.50.9" to "DHAKA-FLIX-9",
        "http://172.16.50.7" to "DHAKA-FLIX-7"
    )

    private val sizeRegex = Regex("(\\d+\\.\\d+ [GM]B|\\d+ [GM]B).*", RegexOption.IGNORE_CASE)
    private val ipHttpRegex = Regex("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\s*http", RegexOption.IGNORE_CASE)
    private val doubleProtocolRegex = Regex("https?://https?://", RegexOption.IGNORE_CASE)
    private val multiSlashRegex = Regex("(?<!:)/{2,}")

    private fun fixUrl(url: String): String {
        if (url.isBlank()) return url
        var u = url.trim()
        val lastHttp = u.lastIndexOf("http://", ignoreCase = true)
        val lastHttps = u.lastIndexOf("https://", ignoreCase = true)
        val lastProtocol = if (lastHttp > lastHttps) lastHttp else lastHttps
        
        if (lastProtocol > 0) {
            u = u.substring(lastProtocol)
        }
        
        u = ipHttpRegex.replace(u, "$1/http")
        u = doubleProtocolRegex.replace(u, "http://") // Simplified as lastProtocol handles most cases anyway
        u = u.replace(":://://", ":://")
        u = multiSlashRegex.replace(u, "/")
        
        return u.replace(" ", "%20")
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResults = mutableListOf<SearchResponse>()
        
        servers.forEach { (serverUrl, serverName) ->
            try {
                val searchUrl = "$serverUrl/$serverName/"
                val response = app.post(
                    searchUrl,
                    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                    json = mapOf("action" to "get", "search" to mapOf("href" to "/$serverName/", "pattern" to query, "ignorecase" to true))
                )
                
                val bodyString = response.text
                val hostUrl = serverUrl
                
                val pattern = Pattern.compile("\"href\":\"([^\"]+)\"[^}]*\"size\":null", Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(bodyString)
                
                while (matcher.find()) {
                    var href = matcher.group(1).replace('\\', '/').trim()
                    href = href.replace(Regex("/+"), "/")
                    
                    var cleanHrefForTitle = href
                    while (cleanHrefForTitle.endsWith("/")) {
                        cleanHrefForTitle = cleanHrefForTitle.dropLast(1)
                    }
                    
                    val rawTitle = cleanHrefForTitle.substringAfterLast("/")
                    val title = try {
                        URLDecoder.decode(rawTitle, "UTF-8").trim()
                    } catch (e: Exception) {
                        rawTitle.trim()
                    }
                    
                    if (title.isNotEmpty() && !isIgnored(title)) {
                        val finalHref = if (href.endsWith("/")) href else "$href/"
                        val url = "$hostUrl$finalHref"
                        val thumbSuffix = if (serverName.contains("9")) "a11.jpg" else "a_AL_.jpg"
                        val posterUrl = (url + thumbSuffix).replace(" ", "%20")
                        
                        searchResults.add(newMovieSearchResponse(title, url, TvType.Movie) {
                            this.posterUrl = posterUrl
                        })
                    }
                }
            } catch (e: Exception) {
                // Ignore errors from individual servers
            }
        }
        return searchResults.distinctBy { it.url }
    }

    private fun isIgnored(text: String): Boolean {
        val ignored = listOf("Parent Directory", "modern browsers", "Name", "Last modified", "Size", "Description", "Index of", "JavaScript", "powered by", "_h5ai")
        return ignored.any { text.contains(it, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fixedUrl = fixUrl(url)
        val document = app.get(fixedUrl).document
        val mediaType = getMediaType(document)
        val title = document.title().replace("Index of", "").trim()
        
        val poster = document.selectFirst("figure.movie-detail-banner img, .movie-detail-banner img, .col-md-3 img, .poster img")
            ?.attr("abs:src")?.replace(" ", "%20")
            ?: (fixedUrl + "a_AL_.jpg")

        val desc = document.selectFirst("p.storyline")?.text()?.trim()

        if (mediaType == "m") {
            // Movie
            val dataUrl = document.select("div.col-md-12 a.btn, .movie-buttons a, a[href*=/m/lazyload/], a[href*=/s/lazyload/], .download-link a").lastOrNull()?.attr("abs:href") ?: fixedUrl
            return newMovieLoadResponse(title, fixedUrl, TvType.Movie, dataUrl) {
                this.posterUrl = poster
                this.plot = desc
            }
        } else {
            // Series or Directory
            val episodes = mutableListOf<Episode>()
            
            if (mediaType == "s") {
                val extracted = extractEpisodes(document)
                if (extracted.isNotEmpty()) {
                    extracted.forEach {
                        episodes.add(newEpisode(it.videoUrl) {
                            this.name = it.seasonEpisode + " - " + it.episodeName
                        })
                    }
                } else {
                    val recEpisodes = parseDirectoryParallel(document, fixedUrl)
                    episodes.addAll(recEpisodes)
                }
            } else {
                val recEpisodes = parseDirectoryParallel(document, fixedUrl)
                episodes.addAll(recEpisodes)
            }

            return newTvSeriesLoadResponse(title, fixedUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
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
        
        if (isVideoFile(url)) {
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
        return false
    }

    private fun getMediaType(document: Document): String? {
        val html = document.select("script").html()
        return when {
            html.contains("/m/lazyload/") -> "m"
            html.contains("/s/lazyload/") -> "s"
            html.contains("/s/view/") -> "s"
            else -> null
        }
    }

    data class EpisodeData(
        val seasonEpisode: String,
        val videoUrl: String,
        val quality: String,
        val episodeName: String,
        val size: String
    )

    private fun extractEpisodes(document: Document): List<EpisodeData> {
        return document.select("div.card, div.episode-item, div.download-link").mapNotNull { element ->
            val titleElement = element.selectFirst("h5") ?: return@mapNotNull null
            val rawTitle = titleElement.ownText().trim()
            val name = rawTitle.split("&nbsp;").first().trim()
            val url = element.selectFirst("h5 a")?.attr("abs:href")?.trim() ?: ""
            val qualityText = element.selectFirst("h5 .badge-fill")?.text() ?: ""
            val quality = sizeRegex.replace(qualityText, "$1").trim()
            val epName = element.selectFirst("h4")?.ownText()?.trim() ?: ""
            val size = element.selectFirst("h4 .badge-outline")?.text()?.trim() ?: ""
            
            if (name.isNotEmpty() && url.isNotEmpty()) {
                EpisodeData(name, url, quality, epName, size)
            } else null
        }
    }

    private suspend fun parseDirectoryParallel(document: Document, currentUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val visited = mutableSetOf<String>()
        parseDirectoryRecursive(document, 3, episodes, visited, currentUrl)
        return episodes.sortedBy { it.name }.reversed()
    }

    private suspend fun parseDirectoryRecursive(document: Document, depth: Int, episodes: MutableList<Episode>, visited: MutableSet<String>, currentUrl: String) {
        if (!visited.add(currentUrl)) return

        val links = document.select("a[href]")
        val files = mutableListOf<Pair<String, String>>()
        val dirs = mutableListOf<String>()

        links.forEach {
            val href = it.attr("abs:href")
            if (href.isNotEmpty() && href !in visited) {
                if (isVideoFile(href)) {
                    files.add(it.text().trim() to href)
                } else {
                    val attr = it.attr("href")
                    if (attr != "../" && !attr.startsWith("?") && attr.endsWith("/") && !attr.contains("_h5ai")) {
                        dirs.add(href)
                    }
                }
            }
        }

        files.forEach { (name, url) ->
            episodes.add(newEpisode(url) {
                this.name = name
            })
            visited.add(url)
        }

        if (depth > 0 && files.isEmpty()) {
            dirs.forEach { dirUrl ->
                try {
                    val doc = app.get(dirUrl).document
                    parseDirectoryRecursive(doc, depth - 1, episodes, visited, dirUrl)
                } catch (e: Exception) {}
            }
        }
    }

    private fun isVideoFile(href: String): Boolean {
        val h = href.lowercase()
        return listOf(".mkv", ".mp4", ".avi", ".ts", ".m4v", ".webm", ".mov").any { h.endsWith(it) || h.contains("$it?") }
    }
}
