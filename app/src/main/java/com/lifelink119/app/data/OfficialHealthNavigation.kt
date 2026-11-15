package com.lifelink119.app.data

import android.app.Activity
import android.content.Intent
import com.lifelink119.app.core.OfficialHealthSite
import com.lifelink119.app.ui.OfficialHealthBrowserActivity

object OfficialHealthNavigation {
    /**
     * Fixed site enum and optional one-use nonce only. No patient data, credentials or arbitrary
     * URLs.
     */
    @Suppress("DEPRECATION")
    fun open(host: Activity, site: OfficialHealthSite, importNonce: String? = null): Boolean =
        runCatching {
                val intent =
                    Intent(host, OfficialHealthBrowserActivity::class.java)
                        .putExtra(OfficialHealthBrowserActivity.EXTRA_SITE, site.name)
                if (site == OfficialHealthSite.SAFE_CALL && importNonce != null) {
                    MedicalCrypto.prepareStorageKey()
                    intent.putExtra(SafeCallImportSession.EXTRA_NONCE, importNonce)
                    host.startActivityForResult(intent, SafeCallImportSession.REQUEST)
                } else host.startActivity(intent)
                true
            }
            .getOrDefault(false)
}
