package com.lifelink119.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import com.lifelink119.app.R
import com.lifelink119.app.core.DrivingRestrictions
import com.lifelink119.app.core.OfficialBrowserPolicy
import com.lifelink119.app.core.OfficialHealthSite
import com.lifelink119.app.data.MedicalCrypto
import com.lifelink119.app.data.SafeCallImportSession
import com.lifelink119.app.data.SafeCallWebImport
import com.lifelink119.app.vehicle.DrivingRestrictionsGateway
import java.io.ByteArrayInputStream
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Isolated temporary web session. Only explicit, whitelisted medical form extraction is allowed.
 */
class OfficialHealthBrowserActivity : Activity() {
    companion object {
        const val EXTRA_SITE = "officialSite"
        private var directoryReady = false
    }

    private lateinit var site: OfficialHealthSite
    private lateinit var content: FrameLayout
    private lateinit var status: TextView
    private lateinit var origin: TextView
    private lateinit var progress: ProgressBar
    private lateinit var back: Button
    private val pages = mutableListOf<WebView>()
    private var gateway: DrivingRestrictionsGateway? = null
    @Volatile private var allowed = false
    @Volatile private var resumed = false
    private var session = 0
    private var preparing = false
    private var webReady = false
    private var dialog: AlertDialog? = null
    private var extracting = false
    private var navigationEpoch = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        site =
            OfficialHealthSite.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_SITE) }
                ?: run {
                    finish()
                    return
                }
        makeLayout()
        try {
            // This Activity runs in :official_web; its cookies/storage do not share the app
            // process.
            if (!directoryReady) {
                WebView.setDataDirectorySuffix("official_health")
                directoryReady = true
            }
            if (WebView.getCurrentWebViewPackage() == null) error("No WebView provider")
            WebView.setWebContentsDebuggingEnabled(false)
            webReady = true
        } catch (_: Exception) {
            showMessage("이 기기의 웹 표시 기능을 사용할 수 없습니다. 휴대전화·PC에서 공식 사이트를 열어주세요.")
            return
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            runCatching {
                    gateway =
                        DrivingRestrictionsGateway(this) { restrictions ->
                            runOnUiThread {
                                if (!isDestroyed && !isFinishing) restrictionsChanged(restrictions)
                            }
                        }
                }
                .onFailure { restrictionsChanged(DrivingRestrictions()) }
        } else restrictionsChanged(DrivingRestrictions.nonAutomotiveDemo())
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float = 22f) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(LifeLinkUiStyle.ink)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }

    private fun action(value: String, key: Int, clicked: () -> Unit) =
        Button(this).apply {
            id = key
            text = value
            textSize = 24f
            isAllCaps = false
            minHeight = dp(80)
            setTextColor(LifeLinkUiStyle.actionInk)
            background =
                LifeLinkUiStyle.actionBackground(
                    this@OfficialHealthBrowserActivity,
                    LifeLinkUiStyle.control,
                )
            setOnClickListener { clicked() }
        }

    private fun makeLayout() {
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(LifeLinkUiStyle.background)
            }
        root.addView(label("LifeLink 119 시연 모드 · 아래는 실제 공식 웹사이트입니다", 17f))
        val controls = LinearLayout(this).apply { setPadding(dp(12), 0, dp(12), 0) }
        val canImport =
            site == OfficialHealthSite.SAFE_CALL &&
                intent.hasExtra(SafeCallImportSession.EXTRA_NONCE)
        fun copyAddress() {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(site.label, site.url))
            Toast.makeText(this, "공식 사이트 주소를 복사했습니다", Toast.LENGTH_SHORT).show()
        }
        back = action("뒤로", R.id.official_web_back, ::goBack)
        controls.addView(
            action("앱으로 돌아가기", R.id.official_web_close) { finish() },
            LinearLayout.LayoutParams(0, dp(80), 1f),
        )
        controls.addView(
            back,
            LinearLayout.LayoutParams(0, dp(80), 1f).apply { marginStart = dp(8) },
        )
        controls.addView(
            if (canImport) action("내 정보 가져오기", R.id.official_web_import, ::importForm)
            else action("공식 주소 복사", R.id.official_web_copy, ::copyAddress),
            LinearLayout.LayoutParams(0, dp(80), 1f).apply { marginStart = dp(8) },
        )
        root.addView(controls)
        origin =
            label(
                    getString(
                        R.string.official_web_origin,
                        OfficialBrowserPolicy.allowedHost(site.url),
                    ),
                    22f,
                )
                .apply { id = R.id.official_web_origin }
        root.addView(origin)
        root.addView(label("정차 중 이용 · 로그인 정보는 닫을 때 정리 · 가져온 정보는 확인 후 암호화 저장", 17f))
        progress =
            ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(4)))
        content = FrameLayout(this).apply { id = R.id.official_web_content }
        status =
            label("차량의 주행 제한 상태를 확인하고 있습니다", 26f).apply {
                id = R.id.official_web_status
                gravity = Gravity.CENTER
                setBackgroundColor(LifeLinkUiStyle.background)
            }
        content.addView(status, FrameLayout.LayoutParams(-1, -1))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this)
        bottom.addView(
            action("사이트 처음부터 다시 열기", R.id.official_web_retry) {
                if (allowed && resumed && webReady) {
                    stopSession()
                    beginSession()
                } else showMessage("정차 후 차량 제한이 해제되면 이용할 수 있습니다. 웹 기능이 없으면 휴대전화·PC를 이용해 주세요.")
            },
            LinearLayout.LayoutParams(0, dp(72), 1f),
        )
        if (canImport)
            bottom.addView(
                action("공식 주소 복사", R.id.official_web_copy, ::copyAddress),
                LinearLayout.LayoutParams(0, dp(72), 1f),
            )
        root.addView(bottom)
        setContentView(root)
        updateBack()
    }

    private fun restrictionsChanged(value: DrivingRestrictions) {
        val next = OfficialBrowserPolicy.canBrowse(value)
        allowed = next
        if (!next) {
            stopSession()
            showMessage("주행 중이거나 차량 제한 상태를 확인할 수 없습니다. 정차 후 공식 사이트를 이용해 주세요.")
        } else if (resumed) beginSession()
    }

    private fun beginSession() {
        if (!allowed || !resumed || !webReady || preparing || pages.isNotEmpty() || isFinishing)
            return
        preparing = true
        val token = ++session
        showMessage("공식 사이트를 열고 있습니다")
        // Clear a session left by process death before allowing any new network navigation.
        clearWebStorage()
        CookieManager.getInstance().removeAllCookies {
            if (token != session || !allowed || !resumed || isDestroyed || isFinishing)
                return@removeAllCookies
            preparing = false
            CookieManager.getInstance().setAcceptCookie(true)
            runCatching {
                    newPage().apply {
                        clearCache(true)
                        loadUrl(site.url)
                    }
                }
                .onFailure {
                    stopSession()
                    showMessage("공식 웹 화면을 시작하지 못했습니다. 다시 열거나 휴대전화·PC를 이용해 주세요.")
                }
        }
    }

    private fun newPage(): WebView {
        val view = WebView(this)
        OfficialWebSettings.configure(view)
        view.id = if (pages.isEmpty()) R.id.official_web_page else R.id.official_web_popup
        view.webViewClient = client()
        view.webChromeClient = chrome()
        view.setDownloadListener { _, _, _, _, _ ->
            showNotice("파일 내려받기는 휴대전화·PC에서 진행해 주세요. 차량에는 파일을 저장하지 않습니다.")
        }
        pages.lastOrNull()?.visibility = View.GONE
        pages += view
        content.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
        status.visibility = View.GONE
        updateBack()
        return view
    }

    private fun blocked() =
        WebResourceResponse(
            "text/plain",
            "UTF-8",
            403,
            "Blocked",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )

    private fun client() =
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                if (!allowed || !OfficialBrowserPolicy.allows(request.url.toString())) {
                    showNotice("이 연결은 내장 웹에서 지원하지 않습니다. 외부 인증 앱이나 다른 사이트가 필요하면 휴대전화·PC에서 진행해 주세요.")
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? =
                if (allowed && resumed && OfficialBrowserPolicy.allows(request.url.toString())) null
                else blocked()

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                navigationEpoch++
                extracting = false
                if (view !in pages) return
                if (url == "about:blank") return
                if (!allowed || url == null || !OfficialBrowserPolicy.allows(url)) {
                    view.stopLoading()
                    showMessage("허용된 공식 HTTPS 사이트만 열 수 있습니다.")
                    return
                }
                if (view == pages.lastOrNull()) {
                    origin.text =
                        getString(
                            R.string.official_web_origin,
                            OfficialBrowserPolicy.allowedHost(url),
                        )
                    progress.visibility = View.VISIBLE
                    status.visibility = View.GONE
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (view == pages.lastOrNull()) {
                    progress.visibility = View.GONE
                    updateBack()
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame && view == pages.lastOrNull()) {
                    navigationEpoch++
                    showMessage("사이트에 연결하지 못했습니다. 인터넷 연결을 확인한 뒤 다시 열어주세요.")
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                response: WebResourceResponse,
            ) {
                if (request.isForMainFrame && view == pages.lastOrNull())
                    showMessage("공식 사이트가 요청을 처리하지 못했습니다. 잠시 후 다시 열어주세요.")
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError,
            ) {
                handler.cancel()
                navigationEpoch++
                if (view !in pages) return
                view.stopLoading()
                showMessage("사이트의 보안 인증서를 확인할 수 없어 연결을 중단했습니다. 기기 시각과 인터넷 연결을 확인해 주세요.")
            }

            override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
                dontResend.sendToTarget()
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                pages.remove(view)
                content.removeView(view)
                runCatching { view.destroy() }
                showMessage("웹 화면이 종료되었습니다. ‘사이트 처음부터 다시 열기’를 눌러주세요.")
                return true
            }
        }

    private fun chrome() =
        object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (view == pages.lastOrNull()) progress.progress = newProgress
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                if (!allowed || !resumed || !isUserGesture || pages.size >= 2) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = newPage()
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                if (window == pages.lastOrNull() && pages.size > 1) closePopup()
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback,
            ) {
                callback.invoke(origin, false, false)
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                callback.onReceiveValue(null)
                showNotice("파일 첨부는 휴대전화·PC에서 진행해 주세요.")
                return true
            }

            // Do not copy website console messages (which may contain auth/health data) into
            // Logcat.
            override fun onConsoleMessage(message: ConsoleMessage) = true
        }

    private fun showMessage(message: String) {
        status.text = message
        status.visibility = View.VISIBLE
        status.bringToFront()
        progress.visibility = View.GONE
    }

    private fun importForm() {
        if (
            extracting ||
                !allowed ||
                !resumed ||
                status.visibility == View.VISIBLE ||
                site != OfficialHealthSite.SAFE_CALL
        )
            return
        val view = pages.lastOrNull() ?: return
        if (!SafeCallWebImport.allowsOrigin(view.url.orEmpty())) {
            showNotice("안심콜 사이트에서 본인 인증을 마치고 내 병력정보 화면을 열어주세요.")
            return
        }
        val nonce = intent.getStringExtra(SafeCallImportSession.EXTRA_NONCE) ?: return
        val generation = session
        val epoch = navigationEpoch
        extracting = true
        val timeout = Runnable {
            if (session == generation && navigationEpoch == epoch && extracting) {
                extracting = false
                navigationEpoch++
                showNotice("정보를 읽는 시간이 초과되었습니다. 웹 화면이 완전히 열린 후 다시 시도해 주세요.")
            }
        }
        view.postDelayed(timeout, 8000)
        runCatching {
                val script =
                    assets.open("safe-call-extract.js").bufferedReader(Charsets.UTF_8).use {
                        it.readText()
                    }
                view.evaluateJavascript(script) { encoded ->
                    view.removeCallbacks(timeout)
                    if (
                        session != generation ||
                            navigationEpoch != epoch ||
                            !allowed ||
                            !resumed ||
                            view != pages.lastOrNull() ||
                            !SafeCallWebImport.allowsOrigin(view.url.orEmpty()) ||
                            isFinishing
                    )
                        return@evaluateJavascript
                    extracting = false
                    runCatching {
                            require(encoded.length <= SafeCallImportSession.MAX_CIPHER_CHARS)
                            val raw =
                                JSONTokener(encoded).nextValue() as? String ?: error("No form")
                            val result = JSONObject(raw)
                            require(!result.has("error"))
                            val retrievedAt = System.currentTimeMillis()
                            // Validate before crossing processes. This does not save or authorize
                            // sharing.
                            SafeCallWebImport.prepare(raw, retrievedAt, null)
                            val cipher =
                                MedicalCrypto.encrypt(
                                    SafeCallImportSession.wrap(raw, nonce, retrievedAt)
                                )
                            require(cipher.length <= SafeCallImportSession.MAX_CIPHER_CHARS)
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(SafeCallImportSession.EXTRA_CIPHER, cipher),
                            )
                            finish()
                        }
                        .onFailure {
                            showNotice(
                                "정보를 가져오지 못했습니다. 본인 인증을 마치고 병력정보 화면을 열어주세요. 가려진 정보·지원하지 않는 항목 형식은 가져오지 않으며 기존 저장 정보는 유지됩니다."
                            )
                        }
                }
            }
            .onFailure {
                view.removeCallbacks(timeout)
                extracting = false
                showNotice("현재 웹 화면에서 정보를 읽을 수 없습니다. 잠시 후 다시 시도해 주세요.")
            }
    }

    private fun showNotice(message: String) {
        if (dialog?.isShowing == true || isFinishing || isDestroyed || !resumed) return
        dialog =
            AlertDialog.Builder(this)
                .setTitle("공식 사이트 이용 안내")
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show()
        dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun updateBack() {
        back.isEnabled = pages.size > 1 || pages.lastOrNull()?.canGoBack() == true
    }

    private fun closePopup() {
        navigationEpoch++
        extracting = false
        val child = pages.removeAt(pages.lastIndex)
        content.removeView(child)
        child.stopLoading()
        child.destroy()
        pages.lastOrNull()?.let {
            it.visibility = View.VISIBLE
            origin.text =
                getString(
                    R.string.official_web_origin,
                    OfficialBrowserPolicy.allowedHost(it.url.orEmpty())
                        ?: OfficialBrowserPolicy.allowedHost(site.url),
                )
        }
        status.visibility = View.GONE
        updateBack()
    }

    private fun goBack() {
        val current = pages.lastOrNull()
        if (current?.canGoBack() == true && allowed) {
            status.visibility = View.GONE
            current.goBack()
        } else if (pages.size > 1) closePopup() else finish()
    }

    @Deprecated("Framework back compatibility") override fun onBackPressed() = goBack()

    @Suppress("DEPRECATION")
    private fun clearWebStorage() {
        runCatching { WebStorage.getInstance().deleteAllData() }
        runCatching { WebViewDatabase.getInstance(this).clearFormData() }
        runCatching { WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword() }
        runCatching { WebView.clearClientCertPreferences(null) }
    }

    private fun stopSession() {
        ++session
        extracting = false
        navigationEpoch++
        preparing = false
        dialog?.dismiss()
        dialog = null
        val old = pages.toList()
        pages.clear()
        old.forEach { view ->
            content.removeView(view)
            runCatching {
                view.stopLoading()
                view.clearHistory()
                view.clearFormData()
                view.clearCache(true)
                view.destroy()
            }
        }
        if (webReady) {
            clearWebStorage()
            runCatching { CookieManager.getInstance().setAcceptCookie(false) }
            runCatching {
                CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
            }
        }
        updateBack()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::site.isInitialized && allowed) beginSession()
    }

    override fun onPause() {
        resumed = false
        if (::content.isInitialized) {
            stopSession()
            showMessage("웹 세션을 닫았습니다. 정차 상태를 확인한 뒤 다시 엽니다.")
        }
        super.onPause()
    }

    override fun onDestroy() {
        gateway?.release()
        gateway = null
        if (::content.isInitialized) stopSession()
        super.onDestroy()
    }
}
