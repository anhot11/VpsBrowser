package com.vpsbrowser.app.engine

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.vpsbrowser.app.model.VpsProfile
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

@Composable
fun GeckoBrowserView(
    url: String,
    profile: VpsProfile,
    onProgress: (Int) -> Unit,
    onUrlChanged: (String) -> Unit,
    onEngineReady: (VpsEngineController) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val runtime = remember { GeckoEngineManager.getOrCreateRuntime(context) }
    var currentUrl by remember { mutableStateOf(url) }
    var canGoBackState by remember { mutableStateOf(false) }
    var canGoForwardState by remember { mutableStateOf(false) }

    val session = remember {
        GeckoSession().apply {
            settings.apply {
                useTrackingProtection = true
                fullScreenMode = true
                viewportMode = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                allowJavascript = true
            }
        }
    }

    var geckoViewRef by remember { mutableStateOf<GeckoView?>(null) }

    val controller = remember(session) {
        object : VpsEngineController {
            override fun loadUrl(url: String) {
                currentUrl = url
                session.loadUri(url)
            }

            override fun reload() {
                session.reload()
            }

            override fun goBack() {
                session.goBack()
            }

            override fun goForward() {
                session.goForward()
            }

            override fun canGoBack(): Boolean = canGoBackState

            override fun canGoForward(): Boolean = canGoForwardState

            override fun evaluateJavascript(script: String, onResult: ((String) -> Unit)?) {
                val safe = script.trim().removePrefix("javascript:")
                session.loadUri("javascript:(function(){try{return $safe;}catch(e){return e.message;}})()")
            }

            override fun dispatchClick(x: Float, y: Float, isRightClick: Boolean) {
                val gv = geckoViewRef ?: return
                val downTime = System.currentTimeMillis()
                if (!isRightClick) {
                    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
                    val up = MotionEvent.obtain(downTime, downTime + 30, MotionEvent.ACTION_UP, x, y, 0)
                    gv.dispatchTouchEvent(down)
                    gv.dispatchTouchEvent(up)
                    down.recycle()
                    up.recycle()
                } else {
                    val js = "var el = document.elementFromPoint($x, $y); if(el) { el.dispatchEvent(new MouseEvent('contextmenu', {bubbles: true, clientX: $x, clientY: $y})); }"
                    evaluateJavascript(js)
                }
            }

            override fun dispatchKeyEvent(keyCode: Int) {
                val gv = geckoViewRef ?: return
                val eventDown = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
                val eventUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
                gv.dispatchKeyEvent(eventDown)
                gv.dispatchKeyEvent(eventUp)
            }

            override fun setDesktopMode(enabled: Boolean) {
                session.settings.userAgentMode = if (enabled) {
                    GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                } else {
                    GeckoSessionSettings.USER_AGENT_MODE_MOBILE
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
                        console.log('⚡ GeckoView Turbo optimizations applied to canvases.');
                    })();
                """.trimIndent()
                evaluateJavascript(turboJs)
            }

            override fun getCurrentUrl(): String = currentUrl
        }
    }

    LaunchedEffect(session) {
        onEngineReady(controller)
    }

    // Configure GeckoSession delegates
    DisposableEffect(session) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, newUrl: String?) {
                newUrl?.let {
                    currentUrl = it
                    onUrlChanged(it)
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                canGoBackState = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                canGoForwardState = canGoForward
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                onProgress(20)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                onProgress(100)
                controller.injectTurboOptimizations()
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                onProgress(progress)
            }
        }

        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAuthPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AuthPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.AuthResponse>? {
                val user = profile.browserUser.ifBlank { "admin" }
                val pass = profile.browserPassword
                if (pass.isNotBlank()) {
                    return GeckoResult.fromValue(prompt.confirm(user, pass))
                }
                return null
            }
        }

        if (!session.isOpen) {
            session.open(runtime)
        }

        onDispose {
            if (session.isOpen) {
                session.close()
            }
        }
    }

    // Load URL when changed
    LaunchedEffect(url) {
        if (url.isNotBlank() && url != currentUrl) {
            currentUrl = url
            session.loadUri(url)
        }
    }

    AndroidView(
        factory = { ctx ->
            GeckoView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setSession(session)
                geckoViewRef = this
            }
        },
        update = { gv ->
            geckoViewRef = gv
        },
        modifier = modifier.fillMaxSize()
    )
}
