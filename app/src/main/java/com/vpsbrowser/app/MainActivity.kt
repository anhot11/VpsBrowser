package com.vpsbrowser.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vpsbrowser.app.data.ProfileManager
import com.vpsbrowser.app.databinding.ActivityMainBinding
import com.vpsbrowser.app.model.VpsProfile
import com.vpsbrowser.app.util.NetworkHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var profileManager: ProfileManager

    private var activeProfile: VpsProfile? = null
    private var pingJob: Job? = null

    private var isMouseMode: Boolean = false
    private var isFullscreen: Boolean = false

    // Virtual cursor position
    private var cursorX = 300f
    private var cursorY = 400f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val profilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        loadActiveProfile()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profileManager = ProfileManager(this)

        setupWebView()
        setupOmnibar()
        setupNavigationControls()
        setupVirtualMouse()
        setupDesktopKeyBar()

        loadActiveProfile()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = binding.remoteWebView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = false
        }

        binding.remoteWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = newProgress
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                // Auto-grant audio/video streaming permissions for VPS WebRTC
                request?.grant(request.resources)
            }
        }

        binding.remoteWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                updateStatus(true, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    if (!binding.etOmnibar.hasFocus()) {
                        binding.etOmnibar.setText(it)
                    }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    showConnectionError("No se pudo cargar el navegador VPS: ${error?.description}")
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // Allow self-signed certificates typically used for personal VPS
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Certificado SSL VPS")
                    .setMessage("El certificado SSL del VPS es autofirmado o no verificado. ¿Deseas continuar?")
                    .setPositiveButton("Continuar") { _, _ -> handler?.proceed() }
                    .setNegativeButton("Cancelar") { _, _ -> handler?.cancel() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun setupOmnibar() {
        binding.etOmnibar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val input = binding.etOmnibar.text.toString().trim()
                if (input.isNotEmpty()) {
                    navigateTo(input)
                    hideKeyboard()
                }
                true
            } else {
                false
            }
        }

        binding.btnRefresh.setOnClickListener {
            binding.remoteWebView.reload()
        }

        binding.btnServers.setOnClickListener {
            val intent = Intent(this, ProfilesActivity::class.java)
            profilesLauncher.launch(intent)
        }
    }

    private fun navigateTo(input: String) {
        val targetUrl = if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else if (input.contains(".") && !input.contains(" ")) {
            "https://$input"
        } else {
            val searchEngine = profileManager.getSearchEngine()
            searchEngine + java.net.URLEncoder.encode(input, "UTF-8")
        }

        // If active profile has an endpoint or we navigate inside the remote view
        binding.remoteWebView.loadUrl(targetUrl)
    }

    private fun setupNavigationControls() {
        binding.btnBack.setOnClickListener {
            if (binding.remoteWebView.canGoBack()) {
                binding.remoteWebView.goBack()
            } else {
                simulateKeyPress(KeyEvent.KEYCODE_BACK)
            }
        }

        binding.btnForward.setOnClickListener {
            if (binding.remoteWebView.canGoForward()) {
                binding.remoteWebView.goForward()
            }
        }

        binding.btnToggleInputMode.setOnClickListener {
            toggleInputMode()
        }

        binding.btnToggleKeyboard.setOnClickListener {
            toggleSoftKeyboard()
        }

        binding.btnFullscreen.setOnClickListener {
            toggleFullscreen()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnAutoDeployAction.setOnClickListener {
            val intent = Intent(this, AutoDeployActivity::class.java)
            profilesLauncher.launch(intent)
        }

        binding.btnConnectAction.setOnClickListener {
            val intent = Intent(this, ProfilesActivity::class.java)
            profilesLauncher.launch(intent)
        }
    }


    private fun setupVirtualMouse() {
        binding.virtualMouseOverlay.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY
                    cursorX = (cursorX + deltaX).coerceIn(0f, binding.browserContainer.width.toFloat())
                    cursorY = (cursorY + deltaY).coerceIn(0f, binding.browserContainer.height.toFloat())

                    binding.ivMouseCursor.x = cursorX
                    binding.ivMouseCursor.y = cursorY

                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    true
                }
                else -> false
            }
        }

        binding.btnLeftClick.setOnClickListener {
            dispatchClickToWebView(cursorX, cursorY, false)
        }

        binding.btnRightClick.setOnClickListener {
            dispatchClickToWebView(cursorX, cursorY, true)
        }
    }

    private fun dispatchClickToWebView(x: Float, y: Float, isRightClick: Boolean) {
        val downTime = System.currentTimeMillis()
        val eventTime = System.currentTimeMillis()

        if (!isRightClick) {
            val downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0)
            val upEvent = MotionEvent.obtain(downTime, eventTime + 50, MotionEvent.ACTION_UP, x, y, 0)
            binding.remoteWebView.dispatchTouchEvent(downEvent)
            binding.remoteWebView.dispatchTouchEvent(upEvent)
            downEvent.recycle()
            upEvent.recycle()
        } else {
            // Trigger context menu via JS
            val js = "var el = document.elementFromPoint($x, $y); if(el) { el.dispatchEvent(new MouseEvent('contextmenu', {bubbles: true, clientX: $x, clientY: $y})); }"
            binding.remoteWebView.evaluateJavascript(js, null)
        }
    }

    private fun setupDesktopKeyBar() {
        binding.keyEsc.setOnClickListener { simulateKeyPress(KeyEvent.KEYCODE_ESCAPE) }
        binding.keyTab.setOnClickListener { simulateKeyPress(KeyEvent.KEYCODE_TAB) }
        binding.keyCtrl.setOnClickListener { simulateKeyCombo("ctrl") }
        binding.keyAlt.setOnClickListener { simulateKeyCombo("alt") }
        binding.keyEnter.setOnClickListener { simulateKeyPress(KeyEvent.KEYCODE_ENTER) }
        binding.keyF12.setOnClickListener {
            // Open DevTools in remote browser if supported
            binding.remoteWebView.evaluateJavascript("window.dispatchEvent(new KeyboardEvent('keydown', {'key': 'F12', 'code': 'F12', 'keyCode': 123}));", null)
        }
    }

    private fun toggleInputMode() {
        isMouseMode = !isMouseMode
        if (isMouseMode) {
            binding.virtualMouseOverlay.visibility = View.VISIBLE
            binding.ivMouseCursor.visibility = View.VISIBLE
            binding.btnToggleInputMode.setImageResource(R.drawable.ic_mouse)
            binding.btnToggleInputMode.setColorFilter(ContextCompat.getColor(this, R.color.accent_blue))
            Toast.makeText(this, "Modo Ratón activado (desliza para mover cursor)", Toast.LENGTH_SHORT).show()
        } else {
            binding.virtualMouseOverlay.visibility = View.GONE
            binding.ivMouseCursor.visibility = View.GONE
            binding.btnToggleInputMode.setImageResource(R.drawable.ic_touch)
            binding.btnToggleInputMode.setColorFilter(ContextCompat.getColor(this, R.color.primary))
            Toast.makeText(this, "Modo Táctil directo activado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val isVisible = binding.desktopKeyBar.visibility == View.VISIBLE

        if (isVisible) {
            binding.desktopKeyBar.visibility = View.GONE
            imm?.hideSoftInputFromWindow(binding.remoteWebView.windowToken, 0)
        } else {
            binding.desktopKeyBar.visibility = View.VISIBLE
            binding.remoteWebView.requestFocus()
            imm?.showSoftInput(binding.remoteWebView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            binding.topBar.visibility = View.GONE
            binding.bottomBar.visibility = View.GONE
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
            Toast.makeText(this, "Pantalla completa (Toca borde para salir)", Toast.LENGTH_SHORT).show()
        } else {
            binding.topBar.visibility = View.VISIBLE
            binding.bottomBar.visibility = View.VISIBLE
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun simulateKeyPress(keyCode: Int) {
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        binding.remoteWebView.dispatchKeyEvent(eventDown)
        binding.remoteWebView.dispatchKeyEvent(eventUp)
    }

    private fun simulateKeyCombo(modifier: String) {
        Toast.makeText(this, "Tecla $modifier presionada", Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etOmnibar.windowToken, 0)
        binding.etOmnibar.clearFocus()
    }

    private fun loadActiveProfile() {
        activeProfile = profileManager.getActiveProfile()
        if (activeProfile == null) {
            showNoProfileState()
        } else {
            connectToProfile(activeProfile!!)
        }
    }

    private fun connectToProfile(profile: VpsProfile) {
        binding.connectionOverlay.visibility = View.GONE
        binding.statusIndicatorDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_connecting))
        binding.tvPing.text = "Conectando..."

        val vpsUrl = profile.getFullUrl()
        binding.etOmnibar.setText(vpsUrl)
        binding.remoteWebView.loadUrl(vpsUrl)

        startPingMonitor(profile)
    }

    private fun showNoProfileState() {
        binding.connectionOverlay.visibility = View.VISIBLE
        binding.tvStatusMessage.text = getString(R.string.status_no_profile)
        binding.btnAutoDeployAction.visibility = View.VISIBLE
        binding.btnConnectAction.text = "Añadir Servidor Manualmente"
        binding.btnConnectAction.setOnClickListener {
            profilesLauncher.launch(Intent(this, ProfilesActivity::class.java))
        }
        binding.statusIndicatorDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_disconnected))
        binding.tvPing.text = "Sin VPS"
    }

    private fun showConnectionError(message: String) {
        binding.connectionOverlay.visibility = View.VISIBLE
        binding.tvStatusMessage.text = message
        binding.btnAutoDeployAction.visibility = View.GONE
        binding.btnConnectAction.text = getString(R.string.action_reconnect)
        binding.btnConnectAction.setOnClickListener {
            activeProfile?.let { connectToProfile(it) } ?: run {
                profilesLauncher.launch(Intent(this, ProfilesActivity::class.java))
            }
        }
        binding.statusIndicatorDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_disconnected))
        binding.tvPing.text = "Error"
    }


    private fun updateStatus(isConnected: Boolean, latency: Long?) {
        if (isConnected) {
            binding.connectionOverlay.visibility = View.GONE
            binding.statusIndicatorDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_connected))
            binding.tvPing.text = if (latency != null && latency >= 0) "${latency}ms" else "Online"
        } else {
            binding.statusIndicatorDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_disconnected))
            binding.tvPing.text = "Offline"
        }
    }

    private fun startPingMonitor(profile: VpsProfile) {
        pingJob?.cancel()
        pingJob = lifecycleScope.launch {
            while (isActive) {
                val latency = NetworkHelper.pingVps(profile.getCleanHost(), profile.port)
                if (latency >= 0) {
                    updateStatus(true, latency)
                } else {
                    updateStatus(false, null)
                }
                delay(6000)
            }
        }
    }

    override fun onDestroy() {
        pingJob?.cancel()
        binding.remoteWebView.destroy()
        super.onDestroy()
    }
}
