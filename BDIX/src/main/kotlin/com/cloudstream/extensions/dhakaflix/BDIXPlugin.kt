package com.cloudstream.extensions.dhakaflix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.cloudstream.extensions.ftpbd.FtpBdProvider

@CloudstreamPlugin
class BDIXPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DhakaFlixProvider())
        registerMainAPI(FtpBdProvider())
        registerMainAPI(DflixProvider())
    }
}