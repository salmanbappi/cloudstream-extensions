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
                serverPath = "DHAKA-FLIX-7"
            )
        )
        registerMainAPI(
            DhakaFlixProvider(
                providerName = "DhakaFlix-14",
                serverRoot = "http://172.16.50.14",
                serverPath = "DHAKA-FLIX-14"
            )
        )
        registerMainAPI(
            DhakaFlixProvider(
                providerName = "DhakaFlix-9",
                serverRoot = "http://172.16.50.9",
                serverPath = "DHAKA-FLIX-9"
            )
        )
        registerMainAPI(
            DhakaFlixProvider(
                providerName = "DhakaFlix-12",
                serverRoot = "http://172.16.50.12",
                serverPath = "DHAKA-FLIX-12"
            )
        )
    }
}
