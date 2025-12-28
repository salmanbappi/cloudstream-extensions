package com.cloudstream.extensions.ftpbd

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FtpBdPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FtpBdProvider())
    }
}
