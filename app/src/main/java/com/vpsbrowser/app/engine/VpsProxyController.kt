package com.vpsbrowser.app.engine

import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor

object VpsProxyController {
    private const val TAG = "VpsProxyController"
    private val directExecutor = Executor { it.run() }
    @Volatile
    private var proxyActive: Boolean = false

    fun isSupported(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    }

    fun isProxyActive(): Boolean = proxyActive

    fun applySocksProxy(socksPort: Int, onComplete: ((Boolean) -> Unit)? = null) {
        if (!isSupported()) {
            Log.w(TAG, "Proxy override is not supported on this Android WebView version")
            proxyActive = false
            onComplete?.invoke(false)
            return
        }

        try {
            // AndroidX WebKit ProxyConfig supports "socks://" and "http://" schemes.
            // SshSocksServer handles both SOCKS5 and HTTP CONNECT transparently on the same port.
            val proxyConfig = ProxyConfig.Builder()
                .addProxyRule("socks://127.0.0.1:$socksPort")
                .addProxyRule("http://127.0.0.1:$socksPort")
                .build()

            ProxyController.getInstance().setProxyOverride(
                proxyConfig,
                directExecutor
            ) {
                proxyActive = true
                Log.d(TAG, "VPS Tunnel Proxy applied successfully: 127.0.0.1:$socksPort (socks + http)")
                onComplete?.invoke(true)
            }
        } catch (e: Exception) {
            proxyActive = false
            Log.e(TAG, "Failed to set proxy override", e)
            onComplete?.invoke(false)
        }
    }

    fun clearProxy(onComplete: (() -> Unit)? = null) {
        proxyActive = false
        if (!isSupported()) {
            onComplete?.invoke()
            return
        }

        try {
            ProxyController.getInstance().clearProxyOverride(
                directExecutor
            ) {
                proxyActive = false
                Log.d(TAG, "Proxy override cleared")
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            proxyActive = false
            Log.e(TAG, "Failed to clear proxy override", e)
            onComplete?.invoke()
        }
    }
}
