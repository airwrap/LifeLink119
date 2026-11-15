package com.lifelink119.app

import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifelink119.app.ui.OfficialHealthBrowserActivity
import com.lifelink119.app.ui.OfficialWebSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialBrowserSettingsTest {
    @Suppress("DEPRECATION")
    @Test
    fun webEngineWorksWithoutAnExternalBrowserAndFileAccessIsDisabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            assertNotNull(WebView.getCurrentWebViewPackage())
            val web = WebView(instrumentation.targetContext)
            try {
                OfficialWebSettings.configure(web)
                assertTrue(web.settings.javaScriptEnabled)
                assertTrue(web.settings.domStorageEnabled)
                assertFalse(web.settings.allowFileAccess)
                assertFalse(web.settings.allowContentAccess)
                assertFalse(web.settings.allowFileAccessFromFileURLs)
                assertFalse(web.settings.allowUniversalAccessFromFileURLs)
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.settings.mixedContentMode)
                assertEquals(WebSettings.LOAD_NO_CACHE, web.settings.cacheMode)
                assertFalse(CookieManager.getInstance().acceptThirdPartyCookies(web))
                assertFalse(web.isSaveEnabled)
                assertEquals(
                    View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                    web.importantForAutofill,
                )
            } finally {
                web.destroy()
            }
        }
    }

    @Test
    fun browserIsInternalAndItsProcessIsSeparate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info =
            context.packageManager.getActivityInfo(
                ComponentName(context, OfficialHealthBrowserActivity::class.java),
                0,
            )
        assertFalse(info.exported)
        assertEquals(context.packageName + ":official_web", info.processName)
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(android.Manifest.permission.INTERNET),
        )
    }
}
