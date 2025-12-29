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
        "m/recent" to "Recent Movies",
        "s/recent" to "Recent Series",
        "m/category/Hindi" to "Hindi Movies",
        "s/category/Hindi" to "Hindi Series",
        "m/category/English" to "English Movies",
        "s/category/Foreign" to "Foreign Series",
        "m/category/Animation" to "Animation Movies",
        "s/category/Animation" to "Animation Series",
        "m/category/Bangla" to "Bangla Movies",
        "s/category/Bangla" to "Bangla Series"
    )

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    private var loginCookie: Map<String, String> = emptyMap()
    private var lastLoginTime: Long = 0

    private suspend fun login(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (loginCookie.isEmpty() || force || (now - lastLoginTime > 3600000)) { // Re-login every hour
            try {
                val client = app.get("$mainUrl/login/demo", headers = commonHeaders, allowRedirects = false)
                if (client.cookies.isNotEmpty()) {
                    loginCookie = client.cookies
                    lastLoginTime = now
                }
            } catch (e: Exception) {}
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        login()
        val url = if (request.data.contains("recent")) {
            fixUrl("${request.data}/$page")
        } else {
            fixUrl("${request.data}/$page")
        }
        
        val response = app.get(url, cookies = loginCookie, headers = commonHeaders)
        var doc = response.document
        
        if (doc.select("input[name=username]").isNotEmpty()) {
            login(true)
            doc = app.get(url, cookies = loginCookie, headers = commonHeaders).document
        }

        val home = doc.select("div.card, div.fgrid").mapNotNull { element -> toResult(element) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse? {
        val aTag = post.selectFirst("a") ?: return null
        val url = fixUrl(aTag.attr("href"))
        val title = (post.selectFirst("h3") ?: post.selectFirst(".ftitle") ?: post.selectFirst(".fdetails"))?.text()?.trim() ?: ""
        val poster = fixUrl(post.selectFirst("img")?.attr("src") ?: "")
        val qualityText = post.text()

        val type = if (url.contains("/s/view/")) TvType.TvSeries else TvType.Movie

        return newAnimeSearchResponse(title, url, type) {
            this.posterUrl = poster
            addDubStatus(
                dubExist = qualityText.contains("DUAL", true),
                subExist = false
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        login()
        val url = fixUrl("/m/find/$query")
        val response = app.get(url, cookies = loginCookie, headers = commonHeaders)
        var doc = response.document
        if (doc.select("input[name=username]").isNotEmpty()) {
            login(true)
            doc = app.get(url, cookies = loginCookie, headers = commonHeaders).document
        }
        return doc.select("div.card").mapNotNull { element -> toResult(element) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fixedUrl = fixUrl(url)
        login()
        val response = app.get(fixedUrl, cookies = loginCookie, headers = commonHeaders)
        var doc = response.document
        
        if (doc.select("input[name=username]").isNotEmpty()) {
            login(true)
            doc = app.get(fixedUrl, cookies = loginCookie, headers = commonHeaders).document
        }

        val title = doc.select(".movie-detail-content h3, .movie-detail-content-test h3").first()?.text()?.trim() ?: doc.title()
        val poster = fixUrl(doc.selectFirst(".movie-detail-banner img")?.attr("src") ?: "")
        val plot = doc.selectFirst(".storyline")?.text()?.trim()
        val tags = doc.select(".ganre-wrapper > a").map { it.text().replace(",", "").trim() }
        val actors = doc.select("div.col-lg-2").map { actor(it) }
        
        // Try parsing as Series first if URL contains /s/
        if (fixedUrl.contains("/s/view/")) {
            val episodes = doc.select("div.card").mapNotNull { element ->
                val h5 = element.selectFirst("h5") ?: return@mapNotNull null
                val epUrl = h5.selectFirst("a")?.attr("href")?.let { fixUrl(it) } ?: return@mapNotNull null
                val epNameRaw = element.select("h2 a h4, h4").text()
                val epName = epNameRaw.replace(Regex("(?i)(1080P|STREAM|720P|WEB-DL|4K|DUAL|ESUB).*"), "").trim()
                
                val seasonEpStr = h5.text()
                val seasonMatch = Regex("(?i)S(\\d+)").find(seasonEpStr)
                val epMatch = Regex("(?i)EP\\s*(\\d+)").find(seasonEpStr)
                
                val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull()
                val episode = epMatch?.groupValues?.get(1)?.toIntOrNull()

                newEpisode(epUrl) {
                    this.name = epName.ifEmpty { seasonEpStr }
                    this.season = season
                    this.episode = episode
                }
            }
            
            if (episodes.isNotEmpty()) {
                return newTvSeriesLoadResponse(title, fixedUrl, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = plot
                    this.tags = tags
                    this.actors = actors
                }
            }
        }

        // Movie link extraction
        val dataUrl = doc.select("a.btn").find { 
            val href = it.attr("href").lowercase()
            val text = it.text().lowercase()
            (href.endsWith(".mkv") || href.endsWith(".mp4") || text.contains("download"))
        }?.attr("href")?.let { fixUrl(it) }
        
        if (dataUrl != null && !dataUrl.contains("/view/")) {
            return newMovieLoadResponse(title, fixedUrl, TvType.Movie, dataUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.actors = actors
            }
        }
        
        // Last resort Stream/Play buttons
        val streamUrl = doc.select("a.btn").find { 
            val text = it.text().lowercase()
            text.contains("stream") || text.contains("play") 
        }?.attr("href")?.let { fixUrl(it) }
        
        if (streamUrl != null) {
             return newMovieLoadResponse(title, fixedUrl, TvType.Movie, streamUrl) {
                this.posterUrl = poster
                this.plot = plot
            }
        }

        return null
    }

    private fun actor(post: Element): ActorData {
        val html = post.select("div.col-lg-2 > a:nth-child(1) > img:nth-child(1)")
        val img = fixUrl(html.attr("src"))
        val name = html.attr("alt")
        return ActorData(
            actor = Actor(name, img), 
            roleString = post.select("div.col-lg-2 > p.text-center.text-white").text()
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = data,
            ) {
                this.referer = "$mainUrl/"
                this.headers = commonHeaders
            }
        )
        return true
    }
}
