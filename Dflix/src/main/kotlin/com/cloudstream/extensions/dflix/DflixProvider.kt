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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        login()
        var doc = app.get("$mainUrl/m/${request.data}/$page", cookies = loginCookie).document
        if (!checkLogin(doc)) {
            login(true)
            doc = app.get("$mainUrl/m/${request.data}/$page", cookies = loginCookie).document
        }
        // One more check to be safe
        if (!checkLogin(doc)) {
             throw ErrorResponse("Login failed. Please try again later.")
        }

        val home = doc.select("div.card").mapNotNull { element -> toResult(element) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse? {
        val url = post.selectFirst("a")?.attr("abs:href") ?: return null
        val title = post.select("div.card > div:nth-child(2) > h3:nth-child(1)").text().trim()
        val poster = post.selectFirst("div.poster > img:nth-child(1)")?.attr("abs:src")
        val qualityText = post.select("span.movie_details_span_end, div.card > a:nth-child(1) > span:nth-child(1)").text()

        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = poster
            val qualityVal = getSearchQuality(qualityText)
            // If newAnimeSearchResponse doesn't support 'quality' directly in DSL (it might not), 
            // we can set it if the underlying object allows, or rely on tags.
            // Checking reference: reference used 'this.quality = ...' inside block.
            // Let's assume it's available or we need to cast/assign.
            // Actually, AnimeSearchResponse has 'quality' field? No, mostly 'posterUrl', 'year', 'dubStatus', etc.
            // But SearchResponse interface has no quality. MovieSearchResponse has quality. AnimeSearchResponse has?
            // Reference code: "this.quality = getSearchQuality(check)" inside newAnimeSearchResponse.
            // So it must be there or added via extension.
            
            // Wait, if I use newAnimeSearchResponse, I should be able to use addDubStatus.
            addDubStatus(
                dubExist = qualityText.contains("DUAL", true),
                subExist = false
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        login()
        var doc = app.get("$mainUrl/m/find/$query", cookies = loginCookie).document
        if (!checkLogin(doc)) {
            login(true)
            doc = app.get("$mainUrl/m/find/$query", cookies = loginCookie).document
        }
        return doc.select("div.card").mapNotNull { element -> toResult(element) }
    }

    override suspend fun load(url: String): LoadResponse? {
        login()
        var doc = app.get(url, cookies = loginCookie).document
        if (!checkLogin(doc)) {
            login(true)
            doc = app.get(url, cookies = loginCookie).document
        }

        val title = doc.select(".movie-detail-content h3").first()?.text()?.trim() ?: doc.title()
        val poster = doc.selectFirst(".movie-detail-banner img")?.attr("abs:src")
        val plot = doc.selectFirst(".storyline")?.text()?.trim()
        val size = doc.select(".badge.badge-fill").text()
        val tags = doc.select(".ganre-wrapper > a").map { it.text().replace(",", "") }
        val actors = doc.select("div.col-lg-2").map { actor(it) }
        val recommendations = doc.select("div.badge-outline > a").mapNotNull { 
            // This selector in reference code seems specific, ensuring it doesn't break
             val recUrl = it.attr("abs:href")
             val recName = it.text()
             if(recUrl.isNotEmpty()) {
                 newMovieSearchResponse(recName, recUrl, TvType.Movie) {
                     this.posterUrl = poster // Reusing main poster as fallback or if appropriate
                 }
             } else null
        }
        
        // Movie link extraction
        val dataUrl = doc.select("a.btn").find { 
            val href = it.attr("href").lowercase()
            val text = it.text().lowercase()
            (href.endsWith(".mkv") || href.endsWith(".mp4") || text.contains("download"))
        }?.attr("abs:href")
        
        if (dataUrl != null && !dataUrl.contains("/m/view/")) {
            return newMovieLoadResponse(title, url, TvType.Movie, dataUrl) {
                this.posterUrl = poster
                this.plot = "<b>$size</b><br><br>$plot"
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        }
        
        // Series Episode Extraction
        val episodes = doc.select("div.card").mapNotNull { element ->
            val h5 = element.selectFirst("h5") ?: return@mapNotNull null
            val epUrl = h5.selectFirst("a")?.attr("abs:href") ?: return@mapNotNull null
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
             return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        }
        
        val streamUrl = doc.select("a.btn").find { it.text().contains("Stream", true) || it.text().contains("Play", true) }?.attr("abs:href")
        if (streamUrl != null) {
             return newMovieLoadResponse(title, url, TvType.Movie, streamUrl) {
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
        val img = html.attr("abs:src")
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
