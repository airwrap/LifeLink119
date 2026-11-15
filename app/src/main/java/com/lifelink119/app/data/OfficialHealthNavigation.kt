package com.lifelink119.app.data

import android.app.Activity
import android.content.Intent
import com.lifelink119.app.core.OfficialHealthSite
import com.lifelink119.app.ui.OfficialHealthBrowserActivity

object OfficialHealthNavigation {
    /** Only the public site enum is passed. No patient data, credentials or arbitrary URLs. */
    fun open(host: Activity, site: OfficialHealthSite): Boolean =
        runCatching {
                host.startActivity(
                    Intent(host, OfficialHealthBrowserActivity::class.java)
                        .putExtra(OfficialHealthBrowserActivity.EXTRA_SITE, site.name)
                )
                true
            }
            .getOrDefault(false)
}
