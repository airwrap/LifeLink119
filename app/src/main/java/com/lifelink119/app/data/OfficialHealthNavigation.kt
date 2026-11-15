package com.lifelink119.app.data

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.lifelink119.app.core.OfficialHealthSite

object OfficialHealthNavigation {
    /** Only an allowlisted public HTTPS URL is passed. No extras/query/identity are attached. */
    fun open(host: Activity, site: OfficialHealthSite): Boolean =
        runCatching {
                host.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(site.url))
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }
            .getOrDefault(false)
}
