package com.vpsbrowser.app.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vpsbrowser.app.model.VpsProfile
import com.vpsbrowser.app.ssh.SshTunnelManager
import com.vpsbrowser.app.ui.components.DesktopAccessoryBar
import com.vpsbrowser.app.ui.components.FloatingPillToolbar
import com.vpsbrowser.app.ui.theme.DarkBorder
import com.vpsbrowser.app.util.NetworkHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    profile: VpsProfile,
    searchEngineUrl: String,
    onOpenServerManager: () -> Unit,
    onOpenProfiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("") }
    var omnibarText by remember { mutableStateOf("") }
    var isOmnibarVisible by remember { mutableStateOf(false) }

    var isMouseMode by remember { mutableStateOf(false) }
    var isKeyboardBarVisible by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isToolbarVisible by remember { mutableStateOf(true) }

    var loadProgress by remember { mutableIntStateOf(0) }
    var latencyMs by remember { mutableStateOf<Long?>(null) }
    var isTunnelActive by remember { mutableStateOf(false) }
    var isConnectingTunnel by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    // Virtual cursor position
    var cursorX by remember { mutableFloatStateOf(400f) }
    var cursorY by remember { mutableFloatStateOf(600f) }

    // Start Tunnel, Cloudflare, or Direct connection
    fun connect() {
        connectionError = null
        if (profile.useCloudflareTunnel && profile.cloudflareUrl.isNotBlank()) {
            isTunnelActive = true
            val cfUrl = profile.cloudflareUrl.trimEnd('/')
            currentUrl = cfUrl
            webViewRef?.loadUrl(cfUrl)
        } else if (profile.useSshTunnel) {
            isConnectingTunnel = true
            scope.launch {
                val tunnelRes = SshTunnelManager.startTunnel(profile)
                isConnectingTunnel = false
                tunnelRes.onSuccess { localUrl ->
                    isTunnelActive = true
                    currentUrl = localUrl
                    webViewRef?.loadUrl(localUrl)
                }.onFailure { err ->
                    isTunnelActive = false
                    if (profile.cloudflareUrl.isNotBlank()) {
                        Toast.makeText(context, "Túnel SSH no disponible. Conectando vía Cloudflare Tunnel...", Toast.LENGTH_LONG).show()
                        isTunnelActive = true
                        val cfUrl = profile.cloudflareUrl.trimEnd('/')
                        currentUrl = cfUrl
                        webViewRef?.loadUrl(cfUrl)
                    } else {
                        connectionError = "Fallo al crear túnel SSH: ${err.message}. Si tu VPS no permite abrir puertos, usa Cloudflare Tunnel."
                    }
                }
            }
        } else if (profile.cloudflareUrl.isNotBlank()) {
            isTunnelActive = true
            val cfUrl = profile.cloudflareUrl.trimEnd('/')
            currentUrl = cfUrl
            webViewRef?.loadUrl(cfUrl)
        } else {
            val directUrl = profile.getDirectUrl()
            currentUrl = directUrl
            webViewRef?.loadUrl(directUrl)
        }
    }

    LaunchedEffect(profile) {
        connect()
    }

    // Ping Latency Loop
    LaunchedEffect(profile) {
        while (isActive) {
            val ping = NetworkHelper.pingVps(profile.getCleanHost(), profile.sshPort)
            latencyMs = ping
            delay(5000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            SshTunnelManager.stopTunnel()
            webViewRef?.destroy()
        }
    }

    fun dispatchClick(x: Float, y: Float, isRightClick: Boolean) {
        val wv = webViewRef ?: return
        val downTime = System.currentTimeMillis()
        if (!isRightClick) {
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, x, y, 0)
            wv.dispatchTouchEvent(down)
            wv.dispatchTouchEvent(up)
            down.recycle()
            up.recycle()
        } else {
            val js = "var el = document.elementFromPoint($x, $y); if(el) { el.dispatchEvent(new MouseEvent('contextmenu', {bubbles: true, clientX: $x, clientY: $y})); }"
            wv.evaluateJavascript(js, null)
        }
    }

    fun navigateTo(input: String) {
        val clean = input.trim()
        val target = if (clean.startsWith("http://") || clean.startsWith("https://")) {
            clean
        } else if (clean.contains(".") && !clean.contains(" ")) {
            "https://$clean"
        } else {
            searchEngineUrl + URLEncoder.encode(clean, "UTF-8")
        }

        webViewRef?.loadUrl(target)
        isOmnibarVisible = false
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

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
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                        }
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            url?.let { omnibarText = it }
                        }
                    }

                    webViewRef = this
                    if (currentUrl.isNotBlank()) {
                        loadUrl(currentUrl)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Progress Line
        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }

        // Connecting / Error Overlay
        if (isConnectingTunnel || connectionError != null) {
            Surface(
                color = Color(0xDD0D1117),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        if (isConnectingTunnel) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.size(16.dp))
                            Text("Estableciendo túnel SSH cifrado...", color = Color.White)
                        } else {
                            SelectionContainer {
                                Text(
                                    text = connectionError ?: "Error de conexión",
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.size(16.dp))
                            Row(
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(onClick = { connect() }) {
                                    Text("Reintentar Conexión")
                                }
                                if (connectionError != null) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Connection Error", connectionError)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Error copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar error", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.size(4.dp))
                                        Text("Copiar Error")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Virtual Mouse Trackpad Overlay
        if (isMouseMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            cursorX = (cursorX + dragAmount.x).coerceIn(0f, 1500f)
                            cursorY = (cursorY + dragAmount.y).coerceIn(0f, 2500f)
                        }
                    }
            ) {
                // Floating pointer
                Icon(
                    imageVector = Icons.Default.Mouse,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .offset { IntOffset(cursorX.roundToInt(), cursorY.roundToInt()) }
                        .size(24.dp)
                )

                // Mouse click action buttons floating at bottom right
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 80.dp, end = 16.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                        .padding(6.dp)
                ) {
                    Button(
                        onClick = { dispatchClick(cursorX, cursorY, false) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(width = 56.dp, height = 40.dp)
                    ) {
                        Text("L", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Button(
                        onClick = { dispatchClick(cursorX, cursorY, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.size(width = 56.dp, height = 40.dp)
                    ) {
                        Text("R", fontSize = 12.sp)
                    }
                }
            }
        }

        // Desktop Accessory Bar
        DesktopAccessoryBar(
            visible = isKeyboardBarVisible,
            onSendKey = { key ->
                val code = when (key) {
                    "Esc" -> KeyEvent.KEYCODE_ESCAPE
                    "Tab" -> KeyEvent.KEYCODE_TAB
                    "Enter" -> KeyEvent.KEYCODE_ENTER
                    else -> null
                }
                if (code != null) {
                    webViewRef?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                    webViewRef?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                } else if (key.contains("F12")) {
                    webViewRef?.evaluateJavascript("window.dispatchEvent(new KeyboardEvent('keydown', {'key': 'F12', 'code': 'F12', 'keyCode': 123}));", null)
                }
            },
            onSendClipboardToVps = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = cm?.primaryClip?.getItemAt(0)?.text?.toString()
                if (!text.isNullOrBlank()) {
                    val safeText = text.replace("'", "\\'")
                    webViewRef?.evaluateJavascript("navigator.clipboard.writeText('$safeText');", null)
                    Toast.makeText(context, "Portapapeles enviado al navegador VPS", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (!isFullscreen) 64.dp else 0.dp)
        )

        // Omnibar Input Drawer / Popup
        AnimatedVisibility(
            visible = isOmnibarVisible,
            enter = slideInVertically(),
            exit = slideOutVertically(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = omnibarText,
                        onValueChange = { omnibarText = it },
                        placeholder = { Text("Buscar con privacidad o escribir URL...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigateTo(omnibarText) }),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { isOmnibarVisible = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
            }
        }

        // Floating Pill Toolbar
        FloatingPillToolbar(
            visible = isToolbarVisible && !isFullscreen,
            isMouseMode = isMouseMode,
            isKeyboardBarVisible = isKeyboardBarVisible,
            latencyMs = latencyMs,
            isTunnelActive = isTunnelActive,
            onBack = {
                if (webViewRef?.canGoBack() == true) webViewRef?.goBack()
            },
            onForward = {
                if (webViewRef?.canGoForward() == true) webViewRef?.goForward()
            },
            onRefresh = { webViewRef?.reload() },
            onToggleInputMode = { isMouseMode = !isMouseMode },
            onToggleKeyboardBar = { isKeyboardBarVisible = !isKeyboardBarVisible },
            onToggleFullscreen = { isFullscreen = !isFullscreen },
            onOpenServerManager = onOpenServerManager,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}
