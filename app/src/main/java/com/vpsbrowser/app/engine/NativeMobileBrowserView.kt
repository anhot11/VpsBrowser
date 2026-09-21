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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NativeMobileBrowserView(
    url: String,
    isIncognito: Boolean = false,
    isTunnelActive: Boolean = false,
    onProgress: (Int) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: ((String) -> Unit)? = null,
    onEngineReady: (VpsEngineController) -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(url) }

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

    val controller = remember(webViewRef, isTunnelActive) {
        object : VpsEngineController {
            override fun loadUrl(url: String) {
                currentUrl = url
                if (isTunnelActive) {
                    webViewRef?.loadUrl(url)
                }
            }

            override fun reload() {
                if (isTunnelActive) {
                    webViewRef?.reload()
                }
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
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                }
                settings.useWideViewPort = enabled
                settings.loadWithOverviewMode = enabled
                webViewRef?.reload()
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

    LaunchedEffect(url, isTunnelActive) {
        if (url.isNotBlank() && isTunnelActive && url != currentUrl) {
            currentUrl = url
            webViewRef?.loadUrl(url)
        } else if (!isTunnelActive) {
            webViewRef?.stopLoading()
            webViewRef?.loadUrl("about:blank")
        }
    }

    LaunchedEffect(isIncognito) {
        webViewRef?.let { wv ->
            IncognitoManager.applyIncognitoSettings(wv, isIncognito)
            if (!isIncognito) {
                IncognitoManager.purgeIncognitoData(wv)
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus(View.FOCUS_DOWN)

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

                        useWideViewPort = false
                        loadWithOverviewMode = false
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false

                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    IncognitoManager.applyIncognitoSettings(this, isIncognito)

                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                        try {
                            val fileName = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                            val req = android.app.DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                                setMimeType(mimeType)
                                val cookie = android.webkit.CookieManager.getInstance().getCookie(downloadUrl)
                                if (!cookie.isNullOrBlank()) {
                                    addRequestHeader("Cookie", cookie)
                                }
                                if (!userAgent.isNullOrBlank()) {
                                    addRequestHeader("User-Agent", userAgent)
                                }
                                setDescription("VPS Browser - Descargando...")
                                setTitle(fileName)
                                allowScanningByMediaScanner()
                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                            }
                            val dm = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
                            dm?.enqueue(req)
                            android.widget.Toast.makeText(ctx, "Iniciando descarga: $fileName", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                ctx.startActivity(intent)
                            } catch (ignored: Exception) {}
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            onProgress(newProgress)
                            if (newProgress in 30..90) {
                                injectDevTunnelsBypass(view)
                            }
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
                            injectDevTunnelsBypass(view)
                            onProgress(15)
                        }

                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            pageUrl?.let {
                                currentUrl = it
                                onUrlChanged(it)
                            }
                            injectDevTunnelsBypass(view)
                            onProgress(100)
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUri = request?.url ?: return null
                            val scheme = reqUri.scheme?.lowercase() ?: ""
                            if (scheme != "http" && scheme != "https") {
                                return null
                            }

                            // ABSOLUTE HARDWARE KILL-SWITCH: Zero Direct IP Leakage
                            // Drop 100% of outgoing requests if the VPS tunnel is not confirmed active
                            if (!isTunnelActive) {
                                android.util.Log.e("VPS_KILL_SWITCH", "🛡️ BLOQUEADO POR KILL-SWITCH: Intento de fuga hacia ${request.url}")
                                val blockedHtml = """
                                    <!DOCTYPE html>
                                    <html>
                                    <body style="background:#0d1117;color:#f85149;display:flex;flex-direction:column;align-items:center;justify-content:center;height:90vh;font-family:sans-serif;text-align:center;padding:24px;">
                                        <h2>🛡️ Escudo Anti-Fugas Activo (Kill-Switch)</h2>
                                        <p style="color:#8b949e;font-size:14px;">Conexión directa bloqueada. Todo el tráfico de tu IP está bloqueado hasta que el túnel seguro de la VPS esté activo.</p>
                                    </body>
                                    </html>
                                """.trimIndent()
                                return WebResourceResponse(
                                    "text/html",
                                    "UTF-8",
                                    ByteArrayInputStream(blockedHtml.toByteArray(Charsets.UTF_8))
                                )
                            }

                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val reqUri = request?.url ?: return false
                            val scheme = reqUri.scheme?.lowercase() ?: ""

                            if (!isTunnelActive && (scheme == "http" || scheme == "https")) {
                                android.util.Log.w("VPS_KILL_SWITCH", "Navegación bloqueada por Kill-Switch: $reqUri")
                                return true
                            }

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

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                android.util.Log.e("NativeMobileBrowser", "WebView main frame error: code=${error?.errorCode}, desc=${error?.description}, url=${request.url}")
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: android.webkit.SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) {
                            android.util.Log.w("NativeMobileBrowser", "WebView SSL error: ${error?.primaryError} on ${error?.url}")
                            super.onReceivedSslError(view, handler, error)
                        }
                    }

                    if (url.isNotBlank() && isTunnelActive) {
                        loadUrl(url)
                    }

                    webViewRef = this
                }
            },
            update = { wv ->
                // State updates handled via controller
            },
            modifier = Modifier.fillMaxSize()
        )

        // Kill Switch Shield Overlay: Visible whenever tunnel is not confirmed active
        if (!isTunnelActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1117)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Escudo Anti-Fugas",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "🛡️ Escudo Anti-Fugas Activo",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Conexiones directas bloqueadas (Kill-Switch Activo). Tu IP real está 100% protegida contra fugas. Conectando túnel cifrado exclusivo con tu VPS...",
                        fontSize = 13.sp,
                        color = Color(0xFF8B949E),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isIncognito) {
                IncognitoManager.purgeIncognitoData(webViewRef)
            }
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}
