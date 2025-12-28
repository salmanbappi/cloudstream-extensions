package com.cloudstream.extensions.dflix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element

class DflixProvider : MainAPI() {
    override var mainUrl = "https://dflix.discoveryftp.net"
    override var name = "Dflix"
    override val hasMainPage = true
    override var lang = "bn"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "category/Bangla" to "Bangla",
        "category/English" to "English",
        "category/Hindi" to "Hindi",
        "category/Tamil" to "Tamil",
        "category/Animation" to "Animation",
        "category/Others" to "Others"
    )

    private var loginCookie: Map<String, String>? = null

    private suspend fun login() {
        if (loginCookie == null || loginCookie!!.isEmpty()) {
            val client = app.get("$mainUrl/login/demo", allowRedirects = false)
            loginCookie = client.cookies
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        login()
        val doc = app.get("$mainUrl/m/${request.data}/$page", cookies = loginCookie ?: emptyMap()).document
        val home = doc.select("div.card").mapNotNull { element -> toResult(element) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse? {
        val url = post.selectFirst("a")?.attr("abs:href") ?: return null
        val title = post.select("div.card > div:nth-child(2) > h3:nth-child(1)").text().trim()
        val poster = post.selectFirst("div.poster > img:nth-child(1)")?.attr("abs:src")
        
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        login()
        val doc = app.get("$mainUrl/m/find/$query", cookies = loginCookie ?: emptyMap()).document
        return doc.select("div.card").mapNotNull { element -> toResult(element) }
    }

    override suspend fun load(url: String): LoadResponse? {
        login()
        val doc = app.get(url, cookies = loginCookie ?: emptyMap()).document
        val title = doc.select(".movie-detail-content h3").first()?.text()?.trim() ?: doc.title()
        val poster = doc.selectFirst(".movie-detail-banner img")?.attr("abs:src")
        val plot = doc.selectFirst(".storyline")?.text()?.trim()
        
        // Movie link extraction
        val dataUrl = doc.select("div.col-md-12 a.btn-download, a.btn-download, .download-link a").lastOrNull()?.attr("abs:href")
        
        if (dataUrl != null && !dataUrl.contains("/m/view/")) {
            return newMovieLoadResponse(title, url, TvType.Movie, dataUrl) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
        
        // If it's a series
        val episodes = doc.select("div.card.episode-item, .download-link").mapNotNull { element ->
            val epName = element.select("h5").text().trim()
            val epUrl = element.select("a").attr("abs:href")
            if (epUrl.isNotEmpty()) {
                newEpisode(epUrl) {
                    this.name = epName
                }
            } else null
        }
        
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                name,
                name,
                url = data,
                referer = mainUrl,
                type = ExtractorLinkType.VIDEO
            )
        )
        return true
    }
}
