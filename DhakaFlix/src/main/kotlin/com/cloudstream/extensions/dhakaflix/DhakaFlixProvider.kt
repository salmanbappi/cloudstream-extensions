package com.cloudstream.extensions.dhakaflix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.util.regex.Pattern
import kotlin.text.RegexOption

class DhakaFlixProvider(
    private val providerName: String,
    private val serverRoot: String,
    private val serverPath: String,
    private val categories: Map<String, String> = emptyMap()
) : MainAPI() {
    override var mainUrl = serverRoot
    override var name = providerName
    override val hasMainPage = true
    override var lang = "bn"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = categories.map { (name, path) ->
        mainPageOf("$serverRoot/$serverPath/$path/" to name)
    }.flatten()

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    )

    private val doubleProtocolRegex = Regex("""https?://https?://""", RegexOption.IGNORE_CASE)
    private val multiSlashRegex = Regex("""(?<!:)/{2,}""")
    private val sizeRegex = Regex("""(\d+\.\d+ [GM]B|\d+ [GM]B).*""", RegexOption.IGNORE_CASE)
    private val yearRegex = Regex("""\((\d{4})\)""")

    private fun fixUrl(url: String, baseUrl: String = mainUrl): String {
        if (url.isBlank()) return url
        var u = url.trim()
        if (!u.startsWith("http", ignoreCase = true)) {
            u = when {
                u.startsWith("//") -> "http:$u"
                u.startsWith("/") -> {
                    val root = baseUrl.substringBefore("//") + "//" + baseUrl.substringAfter("//").substringBefore("/")
                    "$root$u"
                }
                else -> {
                    val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                    "$base$u"
                }
            }
        }
        u = doubleProtocolRegex.replace(u, "http://")
        u = multiSlashRegex.replace(u, "/")
        return u.replace(" ", "%20")
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = try {
            app.get(request.data, headers = commonHeaders, timeout = 10).document
        } catch (_: Exception) {
            return newHomePageResponse(request.name, emptyList())
        }

        val results = mutableListOf<SearchResponse>()

        // h5ai fallback table is usually what we see in raw HTML
        doc.select("td.fb-n a").forEach { element ->
            val title = element.text().trim().removeSuffix("/")
            val url = element.attr("href").let { fixUrl(it, request.data) }
            
            if (isValidDirectoryItem(title, url)) {
                // If it's a year folder, we might want to peek inside or just show it
                // For now, let's just show it. 
                // We can improve this by checking if it contains only directories or files.
                
                val finalUrl = if (url.endsWith("/")) url else "$url/"
                val thumbSuffix = if (serverPath.contains("-9")) "a11.jpg" else "a_AL_.jpg"
                val posterUrl = fixUrl("${finalUrl}$thumbSuffix", request.data)

                results.add(newMovieSearchResponse(title, finalUrl, TvType.Movie) {
                    this.posterUrl = posterUrl
                })
            }
        }

        // If no fallback table, try generic a tags
        if (results.isEmpty()) {
            doc.select("a").forEach { element ->
                val title = element.text().trim().removeSuffix("/")
                val url = element.attr("href").let { fixUrl(it, request.data) }
                if (isValidDirectoryItem(title, url)) {
                    val finalUrl = if (url.endsWith("/")) url else "$url/"
                    val thumbSuffix = if (serverPath.contains("-9")) "a11.jpg" else "a_AL_.jpg"
                    val posterUrl = fixUrl("${finalUrl}$thumbSuffix", request.data)

                    results.add(newMovieSearchResponse(title, finalUrl, TvType.Movie) {
                        this.posterUrl = posterUrl
                    })
                }
            }
        }

        return newHomePageResponse(request.name, results)
    }

    private fun isValidDirectoryItem(title: String, url: String): Boolean {
        if (title.isBlank()) return false
        val lowerTitle = title.lowercase()
        if (isIgnored(lowerTitle)) return false
        if (url.contains("../") || url.contains("?")) return false
        // Ignore h5ai public folders
        if (url.contains("/_h5ai/")) return false
        return true
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val basePath = "/$serverPath/"
            val searchUrl = "$serverRoot/$serverPath/"

            val response = app.post(
                searchUrl,
                headers = mapOf("Content-Type" to "application/json; charset=utf-8") + commonHeaders,
                json = mapOf(
                    "action" to "get",
                    "search" to mapOf(
                        "href" to basePath,
                        "pattern" to query,
                        "ignorecase" to true
                    )
                ),
                timeout = 8
            )

            val bodyString = response.text
            val pattern = Pattern.compile("\\\"href\\\":\\\"([^\\\"]+)\\\"[^}]*\\\"size\\\":null", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(bodyString)
            val searchResults = mutableListOf<SearchResponse>()

            while (matcher.find()) {
                val hrefMatch = matcher.group(1) ?: continue
                var href = hrefMatch.replace('\\', '/').trim().replace(Regex("//+"), "/")

                while (href.endsWith('/')) href = href.dropLast(1)
                val rawTitle = href.substringAfterLast("/")
                val title = try {
                    URLDecoder.decode(rawTitle, "UTF-8").trim()
                } catch (_: Exception) {
                    rawTitle.trim()
                }

                if (title.isNotBlank() && !isIgnored(title)) {
                    val dirUrl = fixUrl("$href/", serverRoot)
                    val thumbSuffix = if (serverPath.contains("-9")) "a11.jpg" else "a_AL_.jpg"
                    val posterUrl = fixUrl("$dirUrl$thumbSuffix", serverRoot)

                    searchResults.add(newMovieSearchResponse(title, dirUrl, TvType.Movie) {
                        this.posterUrl = posterUrl
                    })
                }
            }

            searchResults.distinctBy { it.url }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isIgnored(text: String): Boolean {
        val ignored = listOf(
            "Parent Directory",
            "modern browsers",
            "Name",
            "Last modified",
            "Size",
            "Description",
            "Index of",
            "JavaScript",
            "powered by",
            "_h5ai"
        )
        return ignored.any { text.contains(it, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fixedUrl = fixUrl(url, serverRoot)
        val document = try {
            app.get(fixedUrl, headers = commonHeaders, timeout = 15).document
        } catch (_: Exception) {
            return null
        }

        val mediaType = getMediaType(document)
        val title = document.title().replace("Index of", "").trim().ifBlank { fixedUrl.substringAfterLast('/').ifBlank { name } }

        val poster = document.selectFirst("figure.movie-detail-banner img, .movie-detail-banner img, .col-md-3 img, .poster img")
            ?.attr("src")?.let { fixUrl(it, fixedUrl) }
            ?: fixUrl(fixedUrl + (if (fixedUrl.endsWith("/")) "" else "/") + "a_AL_.jpg", fixedUrl)

        val desc = document.selectFirst("p.storyline")?.text()?.trim()

        if (mediaType == "m") {
            val dataUrl = document.select("div.col-md-12 a.btn, .movie-buttons a, a[href*=/m/lazyload/], a[href*=/s/lazyload/], .download-link a")
                .lastOrNull()?.attr("href")?.let { fixUrl(it, fixedUrl) } ?: fixedUrl

            return newMovieLoadResponse(title, fixedUrl, TvType.Movie, dataUrl) {
                this.posterUrl = poster
                this.plot = desc
            }
        }

        val episodes = mutableListOf<Episode>()
        if (mediaType == "s") {
            val extracted = extractEpisodes(document, fixedUrl)
            if (extracted.isNotEmpty()) {
                extracted.forEach { epData ->
                    episodes.add(newEpisode(epData.videoUrl) {
                        this.name = "${epData.seasonEpisode} - ${epData.episodeName}"
                    })
                }
            } else {
                episodes.addAll(parseDirectoryParallel(document, fixedUrl))
            }
        } else {
            episodes.addAll(parseDirectoryParallel(document, fixedUrl))
        }

        return newTvSeriesLoadResponse(title, fixedUrl, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = desc
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url = fixUrl(data, serverRoot)

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
        val size: String,
    )

    private fun extractEpisodes(document: Document, baseUrl: String): List<EpisodeData> {
        return document.select("div.card, div.episode-item, div.download-link").mapNotNull { element ->
            val titleElement = element.selectFirst("h5") ?: return@mapNotNull null
            val rawTitle = titleElement.ownText().trim()
            val name = rawTitle.split("&nbsp;").first().trim()
            val url = element.selectFirst("h5 a")?.attr("href")?.let { fixUrl(it, baseUrl) } ?: ""
            val qualityText = element.selectFirst("h5 .badge-fill")?.text() ?: ""
            val quality = sizeRegex.replace(qualityText, "$1").trim()
            val epName = element.selectFirst("h4")?.ownText()?.trim() ?: ""
            val size = element.selectFirst("h4 .badge-outline")?.text()?.trim() ?: ""

            if (name.isNotEmpty() && url.isNotEmpty()) EpisodeData(name, url, quality, epName, size) else null
        }
    }

    private suspend fun parseDirectoryParallel(document: Document, currentUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val visited = mutableSetOf<String>()
        parseDirectoryRecursive(document, 3, episodes, visited, currentUrl)
        return episodes.sortedBy { it.name }.reversed()
    }

    private suspend fun parseDirectoryRecursive(
        document: Document,
        depth: Int,
        episodes: MutableList<Episode>,
        visited: MutableSet<String>,
        currentUrl: String,
    ) {
        if (!visited.add(currentUrl)) return

        val links = document.select("a[href]")
        val files = mutableListOf<Pair<String, String>>()
        val dirs = mutableListOf<String>()

        links.forEach { element ->
            val href = element.attr("href").let { fixUrl(it, currentUrl) }
            if (href.isNotEmpty() && href !in visited) {
                if (isVideoFile(href)) {
                    files.add(element.text().trim() to href)
                } else {
                    val attr = element.attr("href")
                    if (attr != "../" && !attr.startsWith("?") && attr.endsWith("/") && !attr.contains("_h5ai")) {
                        dirs.add(href)
                    }
                }
            }
        }

        files.forEach { (epName, epUrl) ->
            episodes.add(newEpisode(epUrl) {
                this.name = epName
            })
            visited.add(epUrl)
        }

        if (depth > 0 && files.isEmpty()) {
            dirs.forEach { dirUrl ->
                try {
                    val doc = app.get(dirUrl, headers = commonHeaders, timeout = 5).document
                    parseDirectoryRecursive(doc, depth - 1, episodes, visited, dirUrl)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun isVideoFile(href: String): Boolean {
        val h = href.lowercase()
        return listOf(".mkv", ".mp4", ".avi", ".ts", ".m4v", ".webm", ".mov").any { h.endsWith(it) || h.contains("$it?") }
    }
}
