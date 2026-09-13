package com.vpsbrowser.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.vpsbrowser.app.model.VpsProfile

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FirefoxTurboBrowserView(
    url: String,
    profile: VpsProfile,
    onProgress: (Int) -> Unit,
    onUrlChanged: (String) -> Unit,
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
                if (!isRightClick) {
                    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
                    val up = MotionEvent.obtain(downTime, downTime + 30, MotionEvent.ACTION_UP, x, y, 0)
                    wv.dispatchTouchEvent(down)
                    wv.dispatchTouchEvent(up)
                    down.recycle()
                    up.recycle()
                } else {
                    val js = "var el = document.elementFromPoint($x, $y); if(el) { el.dispatchEvent(new MouseEvent('contextmenu', {bubbles: true, clientX: $x, clientY: $y})); }"
                    wv.evaluateJavascript(js, null)
                }
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
                    "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0"
                }
            }

            override fun injectTurboOptimizations() {
                val turboJs = """
                    (function() {
                        var canvases = document.querySelectorAll('canvas');
                        canvases.forEach(function(c) {
                            c.style.touchAction = 'none';
                            c.style.userSelect = 'none';
                            c.style.imageRendering = 'pixelated';
                        });
                        console.log('⚡ Firefox Turbo Low-Latency optimizations active.');
                    })();
                """.trimIndent()
                webViewRef?.evaluateJavascript(turboJs, null)
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

                // High performance hardware layer
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowFileAccess = true
                    allowContentAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress)
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        url?.let {
                            currentUrl = it
                            onUrlChanged(it)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        controller.injectTurboOptimizations()
                    }

                    override fun onReceivedHttpAuthRequest(
                        view: WebView?,
                        handler: HttpAuthHandler?,
                        host: String?,
                        realm: String?
                    ) {
                        val user = profile.browserUser.ifBlank { "admin" }
                        val pass = profile.browserPassword
                        if (pass.isNotBlank()) {
                            handler?.proceed(user, pass)
                        } else {
                            handler?.cancel()
                        }
                    }
                }

                webViewRef = this
                if (currentUrl.isNotBlank()) {
                    loadUrl(currentUrl)
                }
            }
        },
        update = { wv ->
            webViewRef = wv
        },
        modifier = modifier.fillMaxSize()
    )
}
