package com.cloudstream.extensions.dflix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DflixPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DflixProvider())
    }
}
