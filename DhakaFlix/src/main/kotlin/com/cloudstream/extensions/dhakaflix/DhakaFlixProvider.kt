package com.cloudstream.extensions.dhakaflix

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

class DhakaFlixProvider(
    private val providerName: String,
    private val serverRoot: String,
    private val serverPath: String,
    private val tvSeriesKeyword: List<String>,
    private val types: Set<TvType>,
    private val pages: List<MainPageData>
) : MainAPI() {
    override var mainUrl = serverRoot
    override var name = providerName
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val instantLinkLoading = true
    override var lang = "bn"
    override val supportedTypes = types

    override val mainPage = pages

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val url = if (request.data.startsWith("/")) {
            "$mainUrl${request.data}"
        } else {
            "$mainUrl/$serverPath/${request.data}"
        }
        
        val doc = try {
            app.get(url).document
        } catch (_: Exception) {
            return newHomePageResponse(request.name, emptyList())
        }
        
        val homeResponse = doc.select("tbody > tr:gt(1):lt(40)")
        val home = homeResponse.mapNotNull { post ->
            getPostResult(post)
        }
        return newHomePageResponse(request.name, home, false)
    }

    private fun getPostResult(post: Element): SearchResponse {
        val folderHtml = post.selectFirst("td.fb-n > a") ?: return newMovieSearchResponse("", "", TvType.Movie)
        val title = folderHtml.text().removeSuffix("/")
        val url = mainUrl + folderHtml.attr("href")
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            addDubStatus(
                dubExist = "Dual" in title,
                subExist = "ESub" in title
            )
        }
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
        return try {
            val body = "{\"action\":\"get\",\"search\":{\"href\":\"/$serverPath/\",\"pattern\":\"$query\",\"ignorecase\":true}}".toRequestBody("application/json".toMediaType())
            val response = app.post("$mainUrl/$serverPath/", requestBody = body).text
            val searchJson = AppUtils.parseJson<SearchResult>(response)
            
            val searchResponse = mutableListOf<SearchResponse>()
            searchJson.search.take(40).forEach { post ->
                if (post.size == null) {
                    val href = post.href
                    val title = nameFromUrl(href).removeSuffix("/")
                    
                    val url = if (href.startsWith("http")) href else "$mainUrl$href"
                    
                    searchResponse.add(
                        newAnimeSearchResponse(title, url, TvType.Movie) {
                            addDubStatus(
                                dubExist = "Dual" in title,
                                subExist = "ESub" in title
                            )
                        }
                    )
                }
            }
            searchResponse
        } catch (_: Exception) {
            emptyList()
        }
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

    private fun containsAnyLoop(text: String, keyword: List<String>?): Boolean {
        if (!keyword.isNullOrEmpty()) {
            for (kw in keyword) {
                if (text.contains(kw, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val imageLink = mainUrl + (doc.selectFirst("td.fb-n > a[href~=(?i)\\.(png|jpe?g)]")?.attr("href") ?: "")
        val tableHtml = doc.select("tbody > tr:gt(1)")

        if (containsAnyLoop(url, tvSeriesKeyword) || url.contains("TV Series", ignoreCase = true)) {
            val episodesData = mutableListOf<Episode>()
            var seasonNum = 0
            val name = nameFromUrl(url).removeSuffix("/")
            
            tableHtml.forEach {
                val aHtml = it.selectFirst("td.fb-n > a") ?: return@forEach
                val link = mainUrl + aHtml.attr("href")
                if (it.selectFirst("td.fb-i > img")?.attr("alt") == "folder") {
                    seasonNum++
                    seasonExtractor(link, episodesData, seasonNum)
                } else if (aHtml.selectFirst("a[href~=(?i)\\.(mkv|mp4)]") != null || aHtml.attr("href").matches(Regex(".*\\.(mkv|mp4)$", RegexOption.IGNORE_CASE))) {
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
                this.posterUrl = imageLink.takeIf { it != mainUrl }
            }
        } else {
            val folderHtml = tableHtml.selectFirst("td.fb-n > a[href~=(?i)\\.(mkv|mp4)]")
                ?: doc.selectFirst("a[href~=(?i)\\.(mkv|mp4)]")
            
            val name = folderHtml?.text()?.toString() ?: nameFromUrl(url).removeSuffix("/")
            val link = if (folderHtml != null) mainUrl + folderHtml.attr("href") else url
            
            return newMovieLoadResponse(name, url, TvType.Movie, link) {
                this.posterUrl = imageLink.takeIf { it != mainUrl }
            }
        }
    }

    private suspend fun seasonExtractor(
        url: String, episodesData: MutableList<Episode>, seasonNum: Int
    ) {
        try {
            val doc = app.get(url).document
            var episodeNum = 0
            doc.select("tbody > tr:gt(1) > td.fb-n > a[href~=(?i)\\.(mkv|mp4)]").forEach {
                episodeNum++
                val folderHtml = it
                val name = folderHtml.text()
                val link = mainUrl + folderHtml.attr("href")
                episodesData.add(
                    newEpisode(link) {
                        this.name = name
                        this.season = seasonNum
                        this.episode = episodeNum
                    }
                )
            }
            if (episodeNum == 0) {
                doc.select("a[href~=(?i)\\.(mkv|mp4)]").forEach {
                    episodeNum++
                    val folderHtml = it
                    val name = folderHtml.text()
                    val link = mainUrl + folderHtml.attr("href")
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
        callback.invoke(
            newExtractorLink(
                data, this.name, url = data, type = ExtractorLinkType.VIDEO
            )
        )
        return true
    }
}