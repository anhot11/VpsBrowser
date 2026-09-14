package com.vpsbrowser.app.engine

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NativeMobileBrowserView(
    url: String,
    onProgress: (Int) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: ((String) -> Unit)? = null,
    onEngineReady: (VpsEngineController) -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(url) }

    val controller = remember(webViewRef) {
        object : VpsEngineController {
            override fun loadUrl(url: String) {
                currentUrl = url
                webViewRef?.loadUrl(url)
            }

            override fun reload() {
                webViewRef?.reload()
            }

            override fun goBack() {
                if (webViewRef?.canGoBack() == true) {
                    webViewRef?.goBack()
                }
            }

            override fun goForward() {
                if (webViewRef?.canGoForward() == true) {
                    webViewRef?.goForward()
                }
            }

            override fun canGoBack(): Boolean = webViewRef?.canGoBack() == true

            override fun canGoForward(): Boolean = webViewRef?.canGoForward() == true

            override fun evaluateJavascript(script: String, onResult: ((String) -> Unit)?) {
                webViewRef?.evaluateJavascript(script) { res ->
                    onResult?.invoke(res ?: "")
                }
            }

            override fun dispatchClick(x: Float, y: Float, isRightClick: Boolean) {
                val wv = webViewRef ?: return
                val downTime = System.currentTimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(downTime, downTime + 30, MotionEvent.ACTION_UP, x, y, 0)
                wv.dispatchTouchEvent(down)
                wv.dispatchTouchEvent(up)
                down.recycle()
                up.recycle()
            }

            override fun dispatchKeyEvent(keyCode: Int) {
                val wv = webViewRef ?: return
                wv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                wv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }

            override fun setDesktopMode(enabled: Boolean) {
                val settings = webViewRef?.settings ?: return
                settings.userAgentString = if (enabled) {
                    "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"
                } else {
                    "Mozilla/5.0 (Linux; Android 14; Mobile; rv:130.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                }
                settings.useWideViewPort = enabled
                settings.loadWithOverviewMode = enabled
            }

            override fun injectTurboOptimizations() {
                // Native mobile engine runs on hardware layer without desktop canvas optimizations
            }

            override fun getCurrentUrl(): String = currentUrl
        }
    }

    LaunchedEffect(controller) {
        onEngineReady(controller)
    }

    LaunchedEffect(url) {
        if (url.isNotBlank() && url != currentUrl) {
            currentUrl = url
            webViewRef?.loadUrl(url)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Input & Keyboard focus readiness
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus(View.FOCUS_DOWN)

                // High-performance hardware acceleration (smooth 60/120Hz scrolling)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                setOnTouchListener { v, _ ->
                    if (!v.hasFocus()) {
                        v.requestFocus()
                    }
                    false
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                    // Responsive mobile viewport
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false

                    // Mobile user agent
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile; rv:130.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        if (!title.isNullOrBlank()) {
                            onTitleChanged?.invoke(title)
                        }
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        try {
                            request?.grant(request.resources)
                        } catch (e: Exception) {
                            super.onPermissionRequest(request)
                        }
                    }

                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?,
                        callback: GeolocationPermissions.Callback?
                    ) {
                        callback?.invoke(origin, true, false)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, pageUrl, favicon)
                        pageUrl?.let {
                            currentUrl = it
                            onUrlChanged(it)
                        }
                        onProgress(15)
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        pageUrl?.let {
                            currentUrl = it
                            onUrlChanged(it)
                        }
                        onProgress(100)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val reqUri = request?.url ?: return false
                        val scheme = reqUri.scheme?.lowercase() ?: ""
                        if (scheme != "http" && scheme != "https" && scheme != "about" && scheme != "data" && scheme != "javascript") {
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, reqUri)
                                ctx.startActivity(intent)
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }
                        return false
                    }
                }

                if (url.isNotBlank()) {
                    loadUrl(url)
                }

                webViewRef = this
            }
        },
        update = { wv ->
            // State updates handled via controller
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}
