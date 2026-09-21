package com.vpsbrowser.app.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import com.vpsbrowser.app.engine.FirefoxTurboBrowserView
import com.vpsbrowser.app.engine.GeckoBrowserView
import com.vpsbrowser.app.engine.NativeMobileBrowserView
import com.vpsbrowser.app.engine.VpsEngineController
import com.vpsbrowser.app.engine.VpsEngineType
import com.vpsbrowser.app.engine.VpsProxyController
import com.vpsbrowser.app.model.VpsProfile
import com.vpsbrowser.app.ssh.SshRemoteLifecycle
import com.vpsbrowser.app.ssh.SshTunnelManager
import com.vpsbrowser.app.ui.components.DesktopAccessoryBar
import com.vpsbrowser.app.ui.components.EngineControlDialog
import com.vpsbrowser.app.ui.components.FloatingPillToolbar
import com.vpsbrowser.app.ui.components.MobileBrowserOmnibar
import com.vpsbrowser.app.ui.theme.DarkBorder
import com.vpsbrowser.app.ui.theme.StatusGreen
import com.vpsbrowser.app.ui.theme.StatusRed
import com.vpsbrowser.app.ui.theme.StatusYellow
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
    onSaveProfile: (VpsProfile) -> Unit = {},
    onOpenServerManager: () -> Unit,
    onOpenProfiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var engineType by remember { mutableStateOf(VpsEngineType.fromId(profile.appEngine)) }
    var engineController by remember { mutableStateOf<VpsEngineController?>(null) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    val securityManager = remember { com.vpsbrowser.app.security.SecurityManager(context) }
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

    // Multi-Path & Dynamic IP Shield Routing State
    var activeRouteName by remember { mutableStateOf("⚡ Conectando...") }
    var showRouteDialog by remember { mutableStateOf(false) }
    var isBenchmarkingRoutes by remember { mutableStateOf(false) }
    var routeBenchmarkResults by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var clientPublicIp by remember { mutableStateOf<String?>(null) }

    // HTTP Basic Auth Handling
    var showAuthDialog by remember { mutableStateOf(false) }
    var authUsername by remember { mutableStateOf(profile.browserUser.ifBlank { "admin" }) }
    var authPassword by remember { mutableStateOf(profile.browserPassword) }
    var authAttempts by remember { mutableIntStateOf(0) }
    var pendingAuthHandler by remember { mutableStateOf<HttpAuthHandler?>(null) }
    var pendingAuthHost by remember { mutableStateOf("") }
    var pendingAuthRealm by remember { mutableStateOf("") }

    // Virtual cursor position
    var cursorX by remember { mutableFloatStateOf(400f) }
    var cursorY by remember { mutableFloatStateOf(600f) }

    // Native Mobile vs Remote Desktop Mode
    var browserMode by remember { mutableStateOf(profile.browserMode.ifBlank { "native_mobile" }) }
    var isSocksActive by remember { mutableStateOf(false) }
    var isIncognito by remember { mutableStateOf(false) }
    var mobileCanGoBack by remember { mutableStateOf(false) }
    var mobileCanGoForward by remember { mutableStateOf(false) }

    // Adaptive Multi-Path Router: Direct (Escudo IP) -> SSH Tunnel -> Cloudflare Tunnel -> SOCKS5
    fun connect() {
        connectionError = null
        scope.launch {
            if (browserMode == "native_mobile") {
                if (profile.isCodespace()) {
                    activeRouteName = "☁️ Móvil Cloud"
                    isTunnelActive = true
                    val target = if (currentUrl.isBlank() || currentUrl.startsWith("http://127.0.0.1") || currentUrl.contains(":3000")) {
                        if (searchEngineUrl.isNotBlank()) searchEngineUrl else "https://www.google.com"
                    } else {
                        currentUrl
                    }
                    currentUrl = target
                    omnibarText = target
                    engineController?.loadUrl(target)
                    return@launch
                }
                if (SshTunnelManager.isSocksProxyActive()) {
                    isSocksActive = true
                    isTunnelActive = true
                    activeRouteName = "🛡️ Móvil VPS"
                    val target = if (currentUrl.isBlank() || currentUrl.startsWith("http://127.0.0.1") || currentUrl.contains(":3000")) {
                        if (searchEngineUrl.isNotBlank()) searchEngineUrl else "https://www.google.com"
                    } else {
                        currentUrl
                    }
                    currentUrl = target
                    omnibarText = target
                    return@launch
                }
                isConnectingTunnel = true
                activeRouteName = "🛡️ Túnel Seguro..."
                val socksRes = SshTunnelManager.startSocksProxy(profile)
                isConnectingTunnel = false
                socksRes.onSuccess { socksPort ->
                    VpsProxyController.applySocksProxy(socksPort) { success ->
                        isSocksActive = success
                        isTunnelActive = true
                        activeRouteName = "🛡️ Móvil VPS"
                        val target = if (currentUrl.isBlank() || currentUrl.startsWith("http://127.0.0.1") || currentUrl.contains(":3000")) {
                            if (searchEngineUrl.isNotBlank()) searchEngineUrl else "https://www.google.com"
                        } else {
                            currentUrl
                        }
                        currentUrl = target
                        omnibarText = target
                        engineController?.loadUrl(target)
                    }
                }.onFailure { err ->
                    isSocksActive = false
                    isTunnelActive = false
                    connectionError = "Fallo al iniciar túnel seguro VPS: ${err.message}"
                }
            } else {
                VpsProxyController.clearProxy()
                when (profile.connectionMode) {
                    "cloudflare", "codespace" -> {
                        if (profile.cloudflareUrl.isNotBlank()) {
                            SshTunnelManager.stopTunnel()
                            isTunnelActive = true
                            activeRouteName = if (profile.isCodespace()) "☁️ Codespaces" else "☁️ Cloudflare"
                            val cfUrl = if (profile.isCodespace()) profile.cloudflareUrl.trimEnd('/') + "/" else profile.cloudflareUrl.trimEnd('/')
                            currentUrl = cfUrl
                            engineController?.loadUrl(cfUrl)
                        } else {
                            connectionError = "No hay URL de conexión configurada en este perfil."
                        }
                    }
                    "ssh_tunnel" -> {
                        isConnectingTunnel = true
                        activeRouteName = "🔒 Túnel SSH..."
                        val tunnelRes = SshTunnelManager.startTunnel(profile)
                        isConnectingTunnel = false
                        tunnelRes.onSuccess { localUrl ->
                            isTunnelActive = true
                            activeRouteName = "🔒 Túnel SSH"
                            currentUrl = localUrl
                            engineController?.loadUrl(localUrl)
                        }.onFailure { err ->
                            isTunnelActive = false
                            connectionError = "Fallo al crear túnel SSH: ${err.message}."
                        }
                    }
                    "direct" -> {
                        SshTunnelManager.stopTunnel()
                        if (profile.enableIpShield) {
                            activeRouteName = "🛡️ Escudo IP..."
                            val ip = NetworkHelper.getDevicePublicIp()
                            clientPublicIp = ip
                            if (ip != null) {
                                SshRemoteLifecycle.whitelistClientIp(profile, ip)
                            }
                        }
                        isTunnelActive = false
                        activeRouteName = "⚡ Directo"
                        val directUrl = profile.getDirectUrl()
                        currentUrl = directUrl
                        engineController?.loadUrl(directUrl)
                    }
                    else -> { // "auto": Fast-Path Inteligente
                        activeRouteName = "⚡ Fast-Path..."
                        var directConnected = false
                        if (profile.enableIpShield) {
                            val ip = NetworkHelper.getDevicePublicIp()
                            clientPublicIp = ip
                            if (ip != null) {
                                SshRemoteLifecycle.whitelistClientIp(profile, ip)
                            }
                        }

                        // Comprobar si el puerto directo 3000 responde
                        val canReachDirect = NetworkHelper.testPortReachability(profile.getCleanHost(), profile.browserPort, 2000)
                        if (canReachDirect) {
                            SshTunnelManager.stopTunnel()
                            isTunnelActive = false
                            activeRouteName = "⚡ Directo"
                            val directUrl = profile.getDirectUrl()
                            currentUrl = directUrl
                            engineController?.loadUrl(directUrl)
                            directConnected = true
                        }

                        if (!directConnected) {
                            // Respaldo automático 1: Túnel SSH local
                            activeRouteName = "🔒 SSH..."
                            isConnectingTunnel = true
                            val tunnelRes = SshTunnelManager.startTunnel(profile)
                            isConnectingTunnel = false
                            tunnelRes.onSuccess { localUrl ->
                                isTunnelActive = true
                                activeRouteName = "🔒 Túnel SSH"
                                currentUrl = localUrl
                                engineController?.loadUrl(localUrl)
                            }.onFailure {
                                // Respaldo automático 2: Túnel Cloudflare
                                if (profile.cloudflareUrl.isNotBlank()) {
                                    isTunnelActive = true
                                    activeRouteName = "☁️ Cloudflare"
                                    val cfUrl = profile.cloudflareUrl.trimEnd('/')
                                    currentUrl = cfUrl
                                    engineController?.loadUrl(cfUrl)
                                } else {
                                    isTunnelActive = false
                                    connectionError = "No se pudo conectar de forma directa ni por túnel SSH. Verifica que tu VPS esté activa."
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(profile, browserMode) {
        connect()
    }

    // Ping Latency Loop Adaptativo
    LaunchedEffect(profile, activeRouteName) {
        while (isActive) {
            val ping = if (activeRouteName.contains("Cloudflare") && profile.cloudflareUrl.isNotBlank()) {
                NetworkHelper.testHttpHealth(profile.cloudflareUrl).second
            } else if (activeRouteName.contains("Directo")) {
                val p = NetworkHelper.pingVps(profile.getCleanHost(), profile.browserPort)
                if (p > 0) p else NetworkHelper.pingVps(profile.getCleanHost(), profile.sshPort)
            } else {
                NetworkHelper.pingVps(profile.getCleanHost(), profile.sshPort)
            }
            latencyMs = ping
            delay(4000)
        }
    }

    fun toggleBrowserMode() {
        val newMode = if (browserMode == "native_mobile") "remote_desktop" else "native_mobile"
        browserMode = newMode
        profile.browserMode = newMode
        onSaveProfile(profile)
        if (newMode == "native_mobile") {
            if (currentUrl.contains(":3000") || currentUrl.startsWith("http://127.0.0.1")) {
                val target = if (searchEngineUrl.isNotBlank()) searchEngineUrl else "https://www.google.com"
                currentUrl = target
                omnibarText = target
            }
        } else {
            VpsProxyController.clearProxy()
            currentUrl = ""
            omnibarText = ""
        }
        connect()
    }

    fun verifyIp() {
        val target = "https://browserleaks.com/ip"
        currentUrl = target
        omnibarText = target
        engineController?.loadUrl(target)
    }

    fun toggleIncognito() {
        val newState = !isIncognito
        isIncognito = newState
        if (newState) {
            Toast.makeText(context, "🕵️ Modo Incógnito Activado: Memoria RAM pura (Cero disco)", Toast.LENGTH_SHORT).show()
        } else {
            com.vpsbrowser.app.engine.IncognitoManager.purgeIncognitoData(null)
            Toast.makeText(context, "Modo Estándar: Datos de memoria RAM purgados", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = browserMode == "native_mobile" && (mobileCanGoBack || engineController?.canGoBack() == true)) {
        engineController?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            // Preserve tunnel when switching between screens in MainActivity.
            // MainActivity.onDestroy() handles full cleanup on app exit.
        }
    }

    fun dispatchClick(x: Float, y: Float, isRightClick: Boolean) {
        engineController?.dispatchClick(x, y, isRightClick)
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

        currentUrl = target
        engineController?.loadUrl(target)
        isOmnibarVisible = false
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (browserMode == "native_mobile") {
            // 100% Native Mobile Android UI (0ms Lag, Touch 120Hz & Soft Keyboard)
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    NativeMobileBrowserView(
                        url = currentUrl,
                        isIncognito = isIncognito,
                        onProgress = {
                            loadProgress = it
                            mobileCanGoBack = engineController?.canGoBack() == true
                            mobileCanGoForward = engineController?.canGoForward() == true
                        },
                        onUrlChanged = { newUrl ->
                            currentUrl = newUrl
                            omnibarText = newUrl
                            if (!isIncognito && newUrl.isNotBlank() && !newUrl.startsWith("about:") && !newUrl.contains(":3000")) {
                                securityManager.addHistory(pageTitle.ifBlank { newUrl }, newUrl)
                            }
                            mobileCanGoBack = engineController?.canGoBack() == true
                            mobileCanGoForward = engineController?.canGoForward() == true
                        },
                        onTitleChanged = { title ->
                            pageTitle = title
                            if (!isIncognito && currentUrl.isNotBlank() && !currentUrl.startsWith("about:") && !currentUrl.contains(":3000")) {
                                securityManager.addHistory(title, currentUrl)
                            }
                        },
                        onEngineReady = { ctrl ->
                            engineController = ctrl
                            if (currentUrl.isNotBlank()) {
                                ctrl.loadUrl(currentUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                MobileBrowserOmnibar(
                    currentUrl = currentUrl,
                    pageTitle = pageTitle,
                    vpsHost = profile.getCleanHost(),
                    latencyMs = latencyMs,
                    isSocksActive = isSocksActive,
                    browserMode = browserMode,
                    canGoBack = mobileCanGoBack,
                    canGoForward = mobileCanGoForward,
                    isIncognito = isIncognito,
                    onToggleIncognito = { toggleIncognito() },
                    onNavigate = { navigateTo(it) },
                    onBack = {
                        if (engineController?.canGoBack() == true) {
                            engineController?.goBack()
                        }
                    },
                    onForward = {
                        if (engineController?.canGoForward() == true) {
                            engineController?.goForward()
                        }
                    },
                    onRefresh = { engineController?.reload() },
                    onToggleBrowserMode = { toggleBrowserMode() },
                    onVerifyIp = { verifyIp() },
                    onOpenServerManager = onOpenServerManager,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Desktop Streaming Browser Engine (🦊 Mozilla GeckoView or ⚡ Firefox Turbo)
            Box(modifier = Modifier.fillMaxSize()) {
                if (engineType == VpsEngineType.GECKO) {
                    GeckoBrowserView(
                        url = currentUrl,
                        profile = profile,
                        onProgress = { loadProgress = it },
                        onUrlChanged = { newUrl ->
                            currentUrl = newUrl
                            omnibarText = newUrl
                        },
                        onEngineReady = { ctrl ->
                            engineController = ctrl
                            if (currentUrl.isNotBlank()) {
                                ctrl.loadUrl(currentUrl)
                            }
                        },
                        onWebCodecsUnsupported = {
                            Toast.makeText(context, "⚡ El servidor VPS requiere WebCodecs. Conmutando a Motor Turbo...", Toast.LENGTH_LONG).show()
                            engineType = VpsEngineType.TURBO
                            profile.appEngine = "turbo"
                            onSaveProfile(profile)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    FirefoxTurboBrowserView(
                        url = currentUrl,
                        profile = profile,
                        onProgress = { loadProgress = it },
                        onUrlChanged = { newUrl ->
                            currentUrl = newUrl
                            omnibarText = newUrl
                        },
                        onEngineReady = { ctrl ->
                            engineController = ctrl
                            if (currentUrl.isNotBlank()) {
                                ctrl.loadUrl(currentUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

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

        if (browserMode != "native_mobile") {
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
                        engineController?.dispatchKeyEvent(code)
                    } else if (key.contains("F12")) {
                        engineController?.evaluateJavascript("window.dispatchEvent(new KeyboardEvent('keydown', {'key': 'F12', 'code': 'F12', 'keyCode': 123}));", null)
                    }
                },
                onSendClipboardToVps = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val text = cm?.primaryClip?.getItemAt(0)?.text?.toString()
                    if (!text.isNullOrBlank()) {
                        val safeText = text.replace("'", "\\'")
                        engineController?.evaluateJavascript("navigator.clipboard.writeText('$safeText');", null)
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
                activeRouteName = activeRouteName,
                currentEngineBadge = engineType.badge,
                onToggleEnginePicker = { showEngineDialog = true },
                onToggleRoutePicker = { showRouteDialog = true },
                onToggleBrowserMode = { toggleBrowserMode() },
                onBack = {
                    if (engineController?.canGoBack() == true) engineController?.goBack()
                },
                onForward = {
                    if (engineController?.canGoForward() == true) engineController?.goForward()
                },
                onRefresh = { engineController?.reload() },
                onToggleInputMode = { isMouseMode = !isMouseMode },
                onToggleKeyboardBar = { isKeyboardBarVisible = !isKeyboardBarVisible },
                onToggleFullscreen = { isFullscreen = !isFullscreen },
                onOpenServerManager = onOpenServerManager,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }

        // Route Switcher & Latency Benchmark Dialog
        if (showRouteDialog) {
            AlertDialog(
                onDismissRequest = { showRouteDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enrutador & Aceleración VPS", fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Selecciona la ruta de conexión a tu navegador VPS según tu red y preferencias:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!clientPublicIp.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("🛡️ IP de tu móvil:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(clientPublicIp!!, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Option 1: Auto (Fast-Path)
                        RouteSelectionCard(
                            title = "🤖 Enrutador Automático (Fast-Path)",
                            subtitle = "Prueba directo con Escudo IP y conmuta a túnel si tu operadora bloquea el puerto.",
                            isSelected = profile.connectionMode == "auto",
                            pingMs = null,
                            onClick = {
                                profile.connectionMode = "auto"
                                onSaveProfile(profile)
                                showRouteDialog = false
                                connect()
                            }
                        )

                        // Option 2: Direct + Dynamic IP Shield
                        val directPing = routeBenchmarkResults["direct"]
                        RouteSelectionCard(
                            title = "⚡ Directo + Escudo IP Dinámico",
                            subtitle = "60 FPS nativos, ping mínimo (<20ms). Puerto cerrado al mundo, abierto solo para tu IP.",
                            isSelected = profile.connectionMode == "direct",
                            pingMs = directPing,
                            onClick = {
                                profile.connectionMode = "direct"
                                onSaveProfile(profile)
                                showRouteDialog = false
                                connect()
                            }
                        )

                        // Option 3: SSH Tunnel
                        val sshPing = routeBenchmarkResults["ssh"]
                        RouteSelectionCard(
                            title = "🔒 Túnel SSH Cifrado",
                            subtitle = "Cero puertos abiertos. Cifrado ChaCha20-Poly1305 en bucle local.",
                            isSelected = profile.connectionMode == "ssh_tunnel",
                            pingMs = sshPing,
                            onClick = {
                                profile.connectionMode = "ssh_tunnel"
                                onSaveProfile(profile)
                                showRouteDialog = false
                                connect()
                            }
                        )

                        // Option 4: Cloudflare Tunnel
                        if (profile.cloudflareUrl.isNotBlank()) {
                            val cfPing = routeBenchmarkResults["cloudflare"]
                            RouteSelectionCard(
                                title = "☁️ Túnel Cloudflare HTTPS",
                                subtitle = "Enrutado mediante red Anycast mundial de Cloudflare.",
                                isSelected = profile.connectionMode == "cloudflare",
                                pingMs = cfPing,
                                onClick = {
                                    profile.connectionMode = "cloudflare"
                                    onSaveProfile(profile)
                                    showRouteDialog = false
                                    connect()
                                }
                            )
                        }

                        // Benchmark button
                        OutlinedButton(
                            onClick = {
                                isBenchmarkingRoutes = true
                                scope.launch {
                                    val results = mutableMapOf<String, Long>()
                                    val dp = NetworkHelper.pingVps(profile.getCleanHost(), profile.browserPort)
                                    results["direct"] = if (dp > 0) dp else NetworkHelper.pingVps(profile.getCleanHost(), profile.sshPort)
                                    val sp = NetworkHelper.pingVps(profile.getCleanHost(), profile.sshPort)
                                    results["ssh"] = if (sp > 0) sp + 12 else -1L
                                    if (profile.cloudflareUrl.isNotBlank()) {
                                        val cfp = NetworkHelper.testHttpHealth(profile.cloudflareUrl).second
                                        results["cloudflare"] = cfp
                                    }
                                    routeBenchmarkResults = results
                                    isBenchmarkingRoutes = false
                                }
                            },
                            enabled = !isBenchmarkingRoutes,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isBenchmarkingRoutes) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Midiendo latencias...", fontSize = 12.sp)
                            } else {
                                Text("📊 Medir Latencia en Tiempo Real de Cada Ruta", fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showRouteDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }

        // HTTP Basic Auth Dialog
        if (showAuthDialog) {
            var isPassVisible by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = {
                    pendingAuthHandler?.cancel()
                    showAuthDialog = false
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Autenticación Requerida")
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Tu servidor VPS requiere credenciales de acceso (HTTP Basic Auth) para cargar Firefox.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = authUsername,
                            onValueChange = { authUsername = it },
                            label = { Text("Usuario") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = authPassword,
                            onValueChange = { authPassword = it },
                            label = { Text("Contraseña / Token") },
                            singleLine = true,
                            visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPassVisible = !isPassVisible }) {
                                    Icon(
                                        if (isPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("VPS Command", "cat /opt/vps-browser/docker-compose.yml | grep PASSWORD")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Comando copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Copiar comando para ver clave en VPS", fontSize = 11.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val user = authUsername.trim().ifBlank { "admin" }
                            val pass = authPassword.trim()
                            profile.browserUser = user
                            profile.browserPassword = pass
                            onSaveProfile(profile)
                            pendingAuthHandler?.proceed(user, pass)
                            engineController?.reload()
                            showAuthDialog = false
                        }
                    ) {
                        Text("Acceder")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingAuthHandler?.cancel()
                            showAuthDialog = false
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Engine Control & Manipulation Dialog
        if (showEngineDialog) {
            EngineControlDialog(
                currentEngine = engineType,
                onSelectEngine = { newEngine ->
                    engineType = newEngine
                    profile.appEngine = newEngine.id
                    onSaveProfile(profile)
                    showEngineDialog = false
                },
                controller = engineController,
                latencyMs = latencyMs,
                onDismiss = { showEngineDialog = false }
            )
        }
    }
}

@Composable
private fun RouteSelectionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    pingMs: Long?,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else DarkBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (pingMs != null && pingMs >= 0) {
                    val color = if (pingMs < 70) StatusGreen else if (pingMs < 160) StatusYellow else StatusRed
                    Text(
                        text = "${pingMs}ms",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

