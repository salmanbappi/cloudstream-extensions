package com.cloudstream.extensions.dhakaflix

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import java.util.Calendar

@CloudstreamPlugin
class DhakaFlixPlugin : Plugin() {
    override fun load(context: Context) {
        val year = Calendar.getInstance().get(Calendar.YEAR)

        registerMainAPI(
            DhakaFlixProvider(
                providerName = "DhakaFlix-7",
                serverRoot = "http://172.16.50.7",
                serverPath = "DHAKA-FLIX-7",
                tvSeriesKeyword = emptyList(),
                types = setOf(TvType.Movie),
                pages = mainPageOf(
                    "English Movies/($year)/" to "English Movies",
                    "English Movies (1080p)/($year) 1080p/" to "English Movies (1080p)",
                    "3D Movies/" to "3D Movies",
                    "Foreign Language Movies/Japanese Language/" to "Japanese Movies",
                    "Foreign Language Movies/Korean Language/" to "Korean Movies",
                    "Foreign Language Movies/Bangla Dubbing Movies/" to "Bangla Dubbing Movies",
                    "Foreign Language Movies/Pakistani Movie/" to "Pakistani Movies",
                    "Kolkata Bangla Movies/($year)/" to "Kolkata Bangla Movies",
                    "Foreign Language Movies/Chinese Language/" to "Chinese Movies"
                )
            )
        )
        registerMainAPI(
            DhakaFlixProvider(
                providerName = "DhakaFlix-14",
                serverRoot = "http://172.16.50.14",
                serverPath = "DHAKA-FLIX-14",
                tvSeriesKeyword = listOf("KOREAN%20TV%20%26%20WEB%20Series"),
                types = setOf(TvType.Movie, TvType.AnimeMovie, TvType.TvSeries),
                pages = mainPageOf(
                    "Animation Movies (1080p)/" to "Animation Movies",
                    "English Movies (1080p)/($year) 1080p/" to "English Movies",
                    "Hindi Movies/($year)/" to "Hindi Movies",
                    "IMDb Top-250 Movies/" to "IMDb Top-250 Movies",
                    "SOUTH INDIAN MOVIES/Hindi Dubbed/($year)/" to "Hindi Dubbed",
                    "SOUTH INDIAN MOVIES/South Movies/$year/" to "South Movies",
                    "/KOREAN TV %26 WEB Series/" to "Korean TV & WEB Series"
                )
            )
        )
        registerMainAPI(
            DhakaFlixProvider(
                providerName = "DhakaFlix-9",
                serverRoot = "http://172.16.50.9",
                serverPath = "DHAKA-FLIX-9",
                tvSeriesKeyword = listOf("Awards", "WWE", "KOREAN", "Documentary", "Anime"),
                types = setOf(TvType.Movie, TvType.AnimeMovie, TvType.TvSeries),
                pages = mainPageOf(
                    "Anime %26 Cartoon TV Series/Anime-TV Series ♥%20 A%20 —%20 F/" to "Anime TV Series",
                    "KOREAN TV %26 WEB Series/" to "KOREAN TV & WEB Series",
                    "Documentary/" to "Documentary",
                    "Awards %26 TV Shows/%23 TV SPECIAL %26 SHOWS/" to "TV SPECIAL & SHOWS",
                    "Awards %26 TV Shows/%23 AWARDS/" to "Awards",
                    "WWE %26 AEW Wrestling/WWE Wrestling/" to "WWE Wrestling",
                    "WWE %26 AEW Wrestling/AEW Wrestling/" to "AEW Wrestling"
                )
            )
        )
        registerMainAPI(
            DhakaFlixProvider(
                providerName = "DhakaFlix-12",
                serverRoot = "http://172.16.50.12",
                serverPath = "DHAKA-FLIX-12",
                tvSeriesKeyword = listOf("TV-WEB-Series"),
                types = setOf(TvType.TvSeries),
                pages = mainPageOf(
                    "TV-WEB-Series/TV Series ★%20 0%20 —%20 9/" to "TV Series ★ 0 — 9",
                    "TV-WEB-Series/TV Series ♥%20 A%20 —%20 L/" to "TV Series ♥ A — L",
                    "TV-WEB-Series/TV Series ♦%20 M%20 —%20 R/" to "TV Series ♦ M — R",
                    "TV-WEB-Series/TV Series ♦%20 S%20 —%20 Z/" to "TV Series ♦ S — Z"
                )
            )
        )
    }
}
