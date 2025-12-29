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

    private var loginCookie: Map<String, String> = emptyMap()

    private suspend fun login(force: Boolean = false) {
        if (loginCookie.isEmpty() || force) {
            loginCookie = emptyMap() // Clear existing cookies
            try {
                val client = app.get("$mainUrl/login/demo", allowRedirects = false)
                if (client.cookies.isNotEmpty()) {
                    loginCookie = client.cookies
                }
            } catch (e: Exception) {
                // Log or handle error
            }
        }
    }

    private suspend fun checkLogin(document: org.jsoup.nodes.Document): Boolean {
        if (document.title().contains("Login", ignoreCase = true) || document.select("input[name=username]").isNotEmpty()) {
            return false
        }
        return true
    }

    private fun fixUrl(url: String): String {
        if (url.isBlank()) return url
        var u = url.trim()
        if (!u.startsWith("http")) {
            u = if (u.startsWith("//")) "https:$u"
            else if (u.startsWith("/")) "$mainUrl$u"
            else "$mainUrl/$u"
        }
        return u.replace(" ", "%20")
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        login()
        val url = fixUrl("/m/${request.data}/$page")
        var doc = app.get(url, cookies = loginCookie).document
        if (!checkLogin(doc)) {
            login(true)
            doc = app.get(url, cookies = loginCookie).document
        }
        if (!checkLogin(doc)) {
             throw Exception("Login failed. Please try again later.")
        }

        val home = doc.select("div.card").mapNotNull { element -> toResult(element) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse? {
        val url = fixUrl(post.selectFirst("a")?.attr("href") ?: return null)
        val title = post.select("div.card > div:nth-child(2) > h3:nth-child(1)").text().trim()
        val poster = fixUrl(post.selectFirst("div.poster > img:nth-child(1)")?.attr("src") ?: "")
        val qualityText = post.select("span.movie_details_span_end, div.card > a:nth-child(1) > span:nth-child(1)").text()

        return newAnimeSearchResponse(title, url, TvType.Movie) {
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
        var doc = app.get(url, cookies = loginCookie).document
        if (!checkLogin(doc)) {
            login(true)
            doc = app.get(url, cookies = loginCookie).document
        }
        return doc.select("div.card").mapNotNull { element -> toResult(element) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fixedUrl = fixUrl(url)
        login()
        var doc = app.get(fixedUrl, cookies = loginCookie).document
        if (!checkLogin(doc)) {
            login(true)
            doc = app.get(fixedUrl, cookies = loginCookie).document
        }

        val title = doc.select(".movie-detail-content h3").first()?.text()?.trim() ?: doc.title()
        val poster = fixUrl(doc.selectFirst(".movie-detail-banner img")?.attr("src") ?: "")
        val plot = doc.selectFirst(".storyline")?.text()?.trim()
        val size = doc.select(".badge.badge-fill").text()
        val tags = doc.select(".ganre-wrapper > a").map { it.text().replace(",", "") }
        val actors = doc.select("div.col-lg-2").map { actor(it) }
        val recommendations = doc.select("div.badge-outline > a").mapNotNull { 
             val recUrl = fixUrl(it.attr("href"))
             val recName = it.text()
             if(recUrl.isNotEmpty()) {
                 newMovieSearchResponse(recName, recUrl, TvType.Movie) {
                     this.posterUrl = poster 
                 }
             } else null
        }
        
        val dataUrl = doc.select("a.btn").find { 
            val href = it.attr("href").lowercase()
            val text = it.text().lowercase()
            (href.endsWith(".mkv") || href.endsWith(".mp4") || text.contains("download"))
        }?.attr("href")?.let { fixUrl(it) }
        
        if (dataUrl != null && !dataUrl.contains("/m/view/")) {
            return newMovieLoadResponse(title, fixedUrl, TvType.Movie, dataUrl) {
                this.posterUrl = poster
                this.plot = "<b>$size</b><br><br>$plot"
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        }
        
        val episodes = doc.select("div.card").mapNotNull { element ->
            val h5 = element.selectFirst("h5") ?: return@mapNotNull null
            val epUrl = fixUrl(h5.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val epNameRaw = element.select("h2 a h4").text()
            val epName = epNameRaw.replace(Regex("(1080P|STREAM|720P|WEB-DL|4K).*"), "").trim()
            val seasonEpStr = h5.text()
            val seasonMatch = Regex("S(\\d+)").find(seasonEpStr)
            val epMatch = Regex("EP\\s*(\\d+)").find(seasonEpStr)
            
            val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull()
            val episode = epMatch?.groupValues?.get(1)?.toIntOrNull()

            if (epUrl.isNotEmpty()) {
                newEpisode(epUrl) {
                    this.name = if (epName.isNotEmpty()) epName else seasonEpStr
                    this.season = season
                    this.episode = episode
                }
            } else null
        }
        
        if (episodes.isNotEmpty()) {
             return newTvSeriesLoadResponse(title, fixedUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        }
        
        val streamUrl = doc.select("a.btn").find { it.text().contains("Stream", true) || it.text().contains("Play", true) }?.attr("href")?.let { fixUrl(it) }
        if (streamUrl != null) {
             return newMovieLoadResponse(title, fixedUrl, TvType.Movie, streamUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
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
                this.referer = mainUrl
            }
        )
        return true
    }

    private fun getSearchQuality(check: String?): SearchQuality? {
        val lowercaseCheck = check?.lowercase()
        if (lowercaseCheck != null) {
            return when {
                lowercaseCheck.contains("4k") -> SearchQuality.FourK
                lowercaseCheck.contains("web-r") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("br") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains("hdtc") -> SearchQuality.HdCam
                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("hd") || lowercaseCheck.contains("1080p") -> SearchQuality.HD
                else -> null
            }
        }
        return null
    }
}
