package com.cloudstream.extensions.dhakaflix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DhakaFlixPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DhakaFlixProvider())
    }
}
