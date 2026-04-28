package com.cloudstream.extensions.ftpbd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Calendar
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class FtpBdProvider : MainAPI() {
    override var mainUrl = "https://server3.ftpbd.net"
    override var name = "FtpBd"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val instantLinkLoading = true
    override var lang = "bn"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AnimeMovie)

    private val year = Calendar.getInstance().get(Calendar.YEAR)

    override val mainPage = mainPageOf(
        "https://server3.ftpbd.net/FTP-3/Hindi%20Movies/$year/" to "Hindi Movies ($year)",
        "https://server2.ftpbd.net/FTP-2/English%20Movies/$year/" to "English Movies ($year)",
        "https://server3.ftpbd.net/FTP-3/South%20Indian%20Movies/$year/" to "South Indian Movies ($year)",
        "https://server3.ftpbd.net/FTP-3/South%20Indian%20Movies/HINDI-DUBBED/$year/" to "South Indian (Hindi Dubbed)",
        "https://server5.ftpbd.net/FTP-5/Animation%20Movies/($year)/" to "Animation Movies",
        "https://server5.ftpbd.net/FTP-5/Anime--Cartoon-TV-Series/" to "Anime & Cartoon",
        "https://server4.ftpbd.net/FTP-4/English-Foreign-TV-Series/" to "TV Series"
    )

    private fun fixUrl(url: String, baseUrl: String): String {
        if (url.isBlank()) return url
        var u = url.trim()
        if (!u.startsWith("http")) {
            u = if (u.startsWith("//")) "https:$u"
            else if (u.startsWith("/")) {
                val host = if (baseUrl.contains("//")) {
                    baseUrl.substringBefore("//") + "//" + baseUrl.substringAfter("//").substringBefore("/")
                } else baseUrl
                "$host$u"
            } else {
                val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                "$base$u"
            }
        }
        return u.replace(" ", "%20").replace(Regex("(?<!:)/{2,}"), "/")
    }

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val url = request.data
        
        val doc = try {
            app.get(url).document
        } catch (_: Exception) {
            return newHomePageResponse(request.name, emptyList())
        }
        
        val homeResponse = doc.select("tbody > tr:gt(1)")
        val home = homeResponse.mapNotNull { post ->
            val folderHtml = post.selectFirst("td.fb-n > a") ?: return@mapNotNull null
            val title = folderHtml.text().removeSuffix("/").trim()
            if (title.equals("Parent Directory", ignoreCase = true) || title.isBlank()) return@mapNotNull null

            val itemUrl = fixUrl(folderHtml.attr("href"), url)
            
            // Avoid showing year folders for Animation Movies and just append year to the base url directly.
            // In mainPageOf we already append $year. So this logic filters out year folders inside the $year folder if they exist
            if (title.matches(Regex("""^\d{4}$"""))) {
                return@mapNotNull null
            }

            newMovieSearchResponse(title, itemUrl, TvType.Movie) {
                this.posterUrl = fixUrl("a_AL_.jpg", itemUrl)
            }
        }
        return newHomePageResponse(request.name, home, false)
    }

    data class SearchResult(
        val search: List<Search>
    )

    data class Search(
        val fetched: Boolean,
        val href: String,
        val managed: Boolean,
        val size: Long?,
        val time: Long
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val searchTargets = listOf(
            Pair("https://server3.ftpbd.net", "/FTP-3/"),
            Pair("https://server2.ftpbd.net", "/FTP-2/"),
            Pair("https://server4.ftpbd.net", "/FTP-4/"),
            Pair("https://server5.ftpbd.net", "/FTP-5/")
        )

        val searchResponse = mutableListOf<SearchResponse>()
        
        coroutineScope {
            searchTargets.map { (host, path) ->
                async {
                    try {
                        val body = "{\"action\":\"get\",\"search\":{\"href\":\"$path\",\"pattern\":\"$query\",\"ignorecase\":true}}".toRequestBody("application/json".toMediaType())
                        val response = app.post(host + path, requestBody = body, timeout = 15).text
                        val searchJson = AppUtils.parseJson<SearchResult>(response)
                        
                        searchJson.search.take(40).forEach { post ->
                            if (post.size == null) {
                                val href = post.href
                                val title = nameFromUrl(href).removeSuffix("/")
                                val url = fixUrl(href, host)
                                
                                // Ignore intermediate year folders in search
                                if (!title.matches(Regex("""^\d{4}$""")) && !title.equals("Parent Directory", ignoreCase = true)) {
                                    searchResponse.add(
                                        newMovieSearchResponse(title, url, TvType.Movie) {
                                            this.posterUrl = fixUrl("a_AL_.jpg", url)
                                        }
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }.awaitAll()
        }
        
        return searchResponse.distinctBy { it.url }
    }

    private val nameRegex = Regex(""".*/([^/]+)(?:/[^/]*)*$""")
    private fun nameFromUrl(href: String): String {
        val hrefDecoded = try {
            URLDecoder.decode(href, StandardCharsets.UTF_8.toString())
        } catch (_: Exception) {
            href
        }
        val match = nameRegex.find(hrefDecoded)?.groups?.get(1)?.value
        return match ?: hrefDecoded.substringAfterLast("/")
    }

    private fun isTvSeries(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("tv series") || lowerUrl.contains("tv serias") || lowerUrl.contains("anime--cartoon-tv-series")
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        
        val relativeThumb = doc.selectFirst("td.fb-n > a[href~=(?i)\\.(png|jpe?g)]")?.attr("href") ?: "a_AL_.jpg"
        val imageLink = fixUrl(relativeThumb, url)
        
        val tableHtml = doc.select("tbody > tr:gt(1)")

        if (isTvSeries(url)) {
            val episodesData = mutableListOf<Episode>()
            var seasonNum = 0
            val name = nameFromUrl(url).removeSuffix("/")
            
            tableHtml.forEach {
                val aHtml = it.selectFirst("td.fb-n > a") ?: return@forEach
                val link = fixUrl(aHtml.attr("href"), url)
                if (it.selectFirst("td.fb-i > img")?.attr("alt") == "folder") {
                    seasonNum++
                    seasonExtractor(link, episodesData, seasonNum)
                } else if (aHtml.selectFirst("a[href~=(?i)\\.(mkv|mp4|avi|ts|m4v|webm|mov)]") != null || aHtml.attr("href").matches(Regex(".*\\.(mkv|mp4|avi|ts|m4v|webm|mov)$", RegexOption.IGNORE_CASE))) {
                    val tittle = aHtml.text()
                    episodesData.add(
                        newEpisode(link) {
                            this.name = tittle
                            this.season = 1
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(name, url, TvType.TvSeries, episodesData) {
                this.posterUrl = imageLink
            }
        } else {
            val folderHtml = tableHtml.selectFirst("td.fb-n > a[href~=(?i)\\.(mkv|mp4|avi|ts|m4v|webm|mov)]")
                ?: doc.selectFirst("a[href~=(?i)\\.(mkv|mp4|avi|ts|m4v|webm|mov)]")
            
            val name = folderHtml?.text()?.toString() ?: nameFromUrl(url).removeSuffix("/")
            val link = if (folderHtml != null) fixUrl(folderHtml.attr("href"), url) else url
            
            return newMovieLoadResponse(name, url, TvType.Movie, link) {
                this.posterUrl = imageLink
            }
        }
    }

    private suspend fun seasonExtractor(
        url: String, episodesData: MutableList<Episode>, seasonNum: Int
    ) {
        try {
            val doc = app.get(url).document
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
        val url = fixUrl(data, mainUrl)
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = url
            ) {
                this.referer = url.substringBeforeLast("/") + "/"
            }
        )
        return true
    }
}