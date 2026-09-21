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
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import com.vpsbrowser.app.security.SecurityManager

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
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(url) }

    fun loadWithHeaders(targetUrl: String) {
        currentUrl = targetUrl
        val token = SecurityManager(context).getGitHubToken()
        if (profile.isCodespace() && !token.isNullOrBlank() && targetUrl.contains(".app.github.dev")) {
            val headers = mapOf("X-Github-Token" to token.trim())
            webViewRef?.loadUrl(targetUrl, headers)
        } else {
            webViewRef?.loadUrl(targetUrl)
        }
    }

    val controller = remember(webViewRef) {
        object : VpsEngineController {
            override fun loadUrl(url: String) {
                loadWithHeaders(url)
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

    fun injectDevTunnelsBypass(view: WebView?) {
        val bypassJs = """
            (function() {
                function bypassDevTunnels() {
                    try {
                        var btn = document.getElementById('continue-button');
                        if (btn) { btn.click(); return true; }
                        var buttons = document.querySelectorAll('button, a, input[type="button"], input[type="submit"]');
                        for (var i = 0; i < buttons.length; i++) {
                            var el = buttons[i];
                            var text = (el.innerText || el.textContent || el.value || '').trim().toLowerCase();
                            if (text === 'continue' || text === 'continuar') {
                                el.click();
                                return true;
                            }
                        }
                    } catch(e) {}
                    return false;
                }
                if (!bypassDevTunnels()) {
                    var count = 0;
                    var timer = setInterval(function() {
                        count++;
                        if (bypassDevTunnels() || count > 20) { clearInterval(timer); }
                    }, 250);
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(bypassJs, null)
    }

    fun showRetryPage(view: WebView?, targetUrl: String) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <style>
                    body {
                        margin: 0; padding: 24px;
                        background: #0d1117; color: #c9d1d9;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        display: flex; flex-direction: column; align-items: center; justify-content: center;
                        min-height: 85vh; text-align: center; box-sizing: border-box;
                    }
                    .spinner {
                        width: 48px; height: 48px;
                        border: 4px solid #21262d;
                        border-top: 4px solid #58a6ff;
                        border-radius: 50%;
                        animation: spin 1s linear infinite;
                        margin-bottom: 20px;
                    }
                    @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
                    h2 { color: #58a6ff; margin: 0 0 10px 0; font-size: 20px; font-weight: 600; }
                    p { color: #8b949e; font-size: 14px; margin: 0 0 24px 0; max-width: 320px; line-height: 1.5; }
                    .btn {
                        background: #238636; color: white; border: none;
                        padding: 12px 28px; border-radius: 8px; font-weight: 600; font-size: 15px;
                        cursor: pointer; text-decoration: none; box-shadow: 0 4px 12px rgba(35,134,54,0.3);
                    }
                    .btn:active { background: #2ea043; }
                    .badge {
                        margin-top: 20px; font-size: 12px; color: #6e7681;
                        background: #161b22; padding: 6px 12px; border-radius: 12px;
                    }
                </style>
            </head>
            <body>
                <div class="spinner"></div>
                <h2>Iniciando Servidor Cloud...</h2>
                <p>GitHub Codespaces está preparando Firefox en la nube. Conectando automáticamente en unos segundos...</p>
                <button class="btn" onclick="retryNow()">Reintentar Ahora</button>
                <div class="badge">Reintento automático activo</div>
                <script>
                    function retryNow() { window.location.href = '$targetUrl'; }
                    setTimeout(retryNow, 4000);
                </script>
            </body>
            </html>
        """.trimIndent()
        view?.loadDataWithBaseURL("https://github.com", html, "text/html", "utf-8", null)
    }

    LaunchedEffect(controller) {
        onEngineReady(controller)
    }

    LaunchedEffect(url) {
        if (url.isNotBlank() && url != currentUrl) {
            loadWithHeaders(url)
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
                        if (newProgress in 30..90) {
                            injectDevTunnelsBypass(view)
                        }
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
                        injectDevTunnelsBypass(view)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        controller.injectTurboOptimizations()
                        injectDevTunnelsBypass(view)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true && profile.isCodespace()) {
                            showRetryPage(view, currentUrl)
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        if (request?.isForMainFrame == true && profile.isCodespace() && (errorResponse?.statusCode ?: 0) in 400..599) {
                            showRetryPage(view, currentUrl)
                        }
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
                    loadWithHeaders(currentUrl)
                }
            }
        },
        update = { wv ->
            webViewRef = wv
        },
        modifier = modifier.fillMaxSize()
    )
}
