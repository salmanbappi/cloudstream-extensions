package com.cloudstream.extensions.dhakaflix

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DhakaFlixPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(
            DhakaFlixProvider(
                providerName = "DhakaFlix-7",
                serverRoot = "http://172.16.50.7",
                serverPath = "DHAKA-FLIX-7",
                categories = mapOf(
                    "English Movies" to "English Movies",
                    "Foreign Language Movies" to "Foreign Language Movies",
                    "Kolkata Bangla Movies" to "Kolkata Bangla Movies",
                    "3D Movies" to "3D Movies"
                )
            )
        )
        registerMainAPI(
            DhakaFlixProvider(
                providerName = "DhakaFlix-14",
                serverRoot = "http://172.16.50.14",
                serverPath = "DHAKA-FLIX-14",
                categories = mapOf(
                    "English Movies (1080p)" to "English Movies (1080p)",
                    "Hindi Movies" to "Hindi Movies",
                    "Animation Movies" to "Animation Movies",
                    "Animation Movies (1080p)" to "Animation Movies (1080p)",
                    "KOREAN TV & WEB Series" to "KOREAN TV & WEB Series",
                    "SOUTH INDIAN MOVIES" to "SOUTH INDIAN MOVIES",
                    "IMDb Top-250 Movies" to "IMDb Top-250 Movies"
                )
            )
        )
        registerMainAPI(
            DhakaFlixProvider(
                providerName = "DhakaFlix-9",
                serverRoot = "http://172.16.50.9",
                serverPath = "DHAKA-FLIX-9",
                categories = mapOf(
                    "Anime & Cartoon TV Series" to "Anime & Cartoon TV Series",
                    "Awards & TV Shows" to "Awards & TV Shows",
                    "Documentary" to "Documentary",
                    "WWE & AEW Wrestling" to "WWE & AEW Wrestling"
                )
            )
        )
        registerMainAPI(
            DhakaFlixProvider(
                providerName = "DhakaFlix-12",
                serverRoot = "http://172.16.50.12",
                serverPath = "DHAKA-FLIX-12",
                categories = mapOf(
                    "TV & WEB Series" to "TV-WEB-Series"
                )
            )
        )
    }
}
