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

    private val jsonHeaders = mapOf(
        "Content-Type" to "application/json; charset=utf-8",
        "Accept" to "application/json"
    ) + commonHeaders

    private suspend fun getH5aiItems(url: String): List<H5aiItem> {
        val root = if (url.endsWith("/")) url else "$url/"
        val path = "/" + root.substringAfter("//").substringAfter("/")
        
        return try {
            val response = app.post(
                root,
                headers = jsonHeaders,
                json = mapOf(
                    "action" to "get",
                    "items" to mapOf(
                        "href" to path,
                        "what" to 1
                    )
                ),
                timeout = 10
            )
            val json = response.text
            // Simple parsing to avoid heavy libraries
            val items = mutableListOf<H5aiItem>()
            val itemPattern = Pattern.compile("\\\"href\\\":\\\"([^\\\"]+)\\\",\\\"time\\\":(\\d+)")
            val matcher = itemPattern.matcher(json)
            while (matcher.find()) {
                val href = matcher.group(1).replace("\\/", "/")
                if (href != path && href != "/" && href != root) {
                    items.add(H5aiItem(href, matcher.group(2).toLong()))
                }
            }
            items
        } catch (_: Exception) {
            emptyList()
        }
    }

    data class H5aiItem(val href: String, val time: Long)

    private fun isIntermediateFolder(href: String): Boolean {
        val decoded = try { URLDecoder.decode(href.removeSuffix("/").substringAfterLast("/"), "UTF-8") } catch(_: Exception) { href.removeSuffix("/").substringAfterLast("/") }
        val name = decoded.trim()
        
        if (name.matches(Regex("""^\(\d{4}\)$"""))) return true
        if (name.matches(Regex("""^\d{4}$"""))) return true
        if (name.matches(Regex("""^\d{4}\s*&\s*Before$""", RegexOption.IGNORE_CASE))) return true
        if (name.matches(Regex("""^\(\d{4}\)\s+\w+$"""))) return true
        if (name.contains("—")) return true
        if (name.contains("★") || name.contains("♥") || name.contains("♦") || name.contains("♣")) return true
        if (name.startsWith("Season ", ignoreCase = true)) return true
        if (name.equals("South Movies", ignoreCase = true) || name.equals("Hindi Dubbed", ignoreCase = true)) return true
        return false
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = getH5aiItems(request.data)
        val results = mutableListOf<SearchResponse>()

        val sortedItems = items.sortedByDescending { it.time }
        val intermediateFolders = sortedItems.filter { it.href.endsWith("/") && isIntermediateFolder(it.href) }

        if (intermediateFolders.isNotEmpty()) {
            val foldersToPeek = intermediateFolders.take(4)
            val nestedItems = foldersToPeek.map { folder ->
                coroutineScope { async { getH5aiItems(fixUrl(folder.href, serverRoot)) } }
            }.awaitAll().flatten()

            nestedItems.filter { !isIntermediateFolder(it.href) }.sortedByDescending { it.time }.take(40).forEach { item ->
                results.add(itemToSearchResult(item, request.data))
            }
        } else {
            sortedItems.filter { !isIntermediateFolder(it.href) }.take(40).forEach { item ->
                results.add(itemToSearchResult(item, request.data))
            }
        }

        return newHomePageResponse(request.name, results.distinctBy { it.url })
    }

    private fun itemToSearchResult(item: H5aiItem, baseUrl: String): SearchResponse {
        var href = item.href
        while (href.endsWith("/")) href = href.dropLast(1)
        val title = try {
            URLDecoder.decode(href.substringAfterLast("/"), "UTF-8")
        } catch (_: Exception) {
            href.substringAfterLast("/")
        }

        val dirUrl = fixUrl("${item.href}/", serverRoot)
        val thumbSuffix = if (serverPath.contains("-9")) "a11.jpg" else "a_AL_.jpg"
        val posterUrl = fixUrl("$dirUrl$thumbSuffix", serverRoot)

        return newMovieSearchResponse(title, dirUrl, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val basePath = "/$serverPath/"
            val searchUrl = "$serverRoot/$serverPath/"

            val response = app.post(
                searchUrl,
                headers = jsonHeaders,
                json = mapOf(
                    "action" to "get",
                    "search" to mapOf(
                        "href" to basePath,
                        "pattern" to query,
                        "ignorecase" to true
                    )
                ),
                timeout = 10
            )

            val json = response.text
            val itemPattern = Pattern.compile("\\\"href\\\":\\\"([^\\\"]+)\\\",\\\"time\\\":(\\d+)")
            val matcher = itemPattern.matcher(json)
            val searchResults = mutableListOf<SearchResponse>()
            val addedUrls = mutableSetOf<String>()

            while (matcher.find()) {
                val href = matcher.group(1).replace("\\/", "/")
                
                if (isIgnored(href)) continue

                val folderHref = if (isVideoFile(href)) {
                    href.substringBeforeLast("/") + "/"
                } else if (!href.endsWith("/")) {
                    "$href/"
                } else href

                if (isIntermediateFolder(folderHref)) continue
                if (addedUrls.contains(folderHref)) continue
                addedUrls.add(folderHref)

                val title = try {
                    URLDecoder.decode(folderHref.removeSuffix("/").substringAfterLast("/"), "UTF-8")
                } catch (_: Exception) {
                    folderHref.removeSuffix("/").substringAfterLast("/")
                }
                
                val dirUrl = fixUrl(folderHref, serverRoot)
                val thumbSuffix = if (serverPath.contains("-9")) "a11.jpg" else "a_AL_.jpg"
                val posterUrl = fixUrl("$dirUrl$thumbSuffix", serverRoot)

                searchResults.add(newMovieSearchResponse(title, dirUrl, TvType.Movie) {
                    this.posterUrl = posterUrl
                })
            }

            searchResults
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fixedUrl = fixUrl(url, serverRoot)
        
        if (isVideoFile(fixedUrl)) {
            val parentUrl = fixedUrl.substringBeforeLast("/") + "/"
            val title = try { URLDecoder.decode(fixedUrl.substringAfterLast("/"), "UTF-8") } catch (_: Exception) { fixedUrl.substringAfterLast("/") }
            return newMovieLoadResponse(title, parentUrl, TvType.Movie, fixedUrl)
        }

        val items = getH5aiItems(fixedUrl)
        
        val title = try {
            URLDecoder.decode(fixedUrl.removeSuffix("/").substringAfterLast("/"), "UTF-8")
        } catch (_: Exception) {
            fixedUrl.removeSuffix("/").substringAfterLast("/")
        }.replace("Index of", "").trim()

        val thumbSuffix = if (serverPath.contains("-9")) "a11.jpg" else "a_AL_.jpg"
        val poster = fixUrl("${fixedUrl}$thumbSuffix", fixedUrl)

        val videoFiles = items.filter { isVideoFile(it.href) }
        val subDirs = items.filter { it.href.endsWith("/") && !isIgnored(it.href) }

        if (videoFiles.size == 1 && subDirs.isEmpty()) {
            return newMovieLoadResponse(title, fixedUrl, TvType.Movie, fixUrl(videoFiles[0].href, serverRoot)) {
                this.posterUrl = poster
            }
        }

        val episodes = mutableListOf<Episode>()
        
        // If there are video files directly in the folder, treat as episodes (or multi-part movie)
        videoFiles.forEach { file ->
            episodes.add(newEpisode(fixUrl(file.href, serverRoot)) {
                this.name = try { URLDecoder.decode(file.href.substringAfterLast("/"), "UTF-8") } catch (_: Exception) { file.href.substringAfterLast("/") }
            })
        }

        // If there are subdirectories (like Season 1), peek into them
        if (subDirs.isNotEmpty()) {
            val nested = subDirs.map { dir ->
                coroutineScope {
                    async {
                        val subItems = getH5aiItems(fixUrl(dir.href, serverRoot))
                        subItems.filter { isVideoFile(it.href) }.map { file ->
                            newEpisode(fixUrl(file.href, serverRoot)) {
                                val folderName = try { URLDecoder.decode(dir.href.removeSuffix("/").substringAfterLast("/"), "UTF-8") } catch (_: Exception) { dir.href.removeSuffix("/").substringAfterLast("/") }
                                val fileName = try { URLDecoder.decode(file.href.substringAfterLast("/"), "UTF-8") } catch (_: Exception) { file.href.substringAfterLast("/") }
                                this.name = "$folderName - $fileName"
                            }
                        }
                    }
                }
            }.awaitAll().flatten()
            episodes.addAll(nested)
        }

        if (episodes.isEmpty() && videoFiles.isEmpty()) {
             return newMovieLoadResponse(title, fixedUrl, TvType.Movie, fixedUrl) {
                this.posterUrl = poster
            }
        }

        return newTvSeriesLoadResponse(title, fixedUrl, TvType.TvSeries, episodes.sortedBy { it.name }) {
            this.posterUrl = poster
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
