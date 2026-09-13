package com.vpsbrowser.app.engine

interface VpsEngineController {
    fun loadUrl(url: String)
    fun reload()
    fun goBack()
    fun goForward()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun evaluateJavascript(script: String, onResult: ((String) -> Unit)? = null)
    fun dispatchClick(x: Float, y: Float, isRightClick: Boolean)
    fun dispatchKeyEvent(keyCode: Int)
    fun setDesktopMode(enabled: Boolean)
    fun injectTurboOptimizations()
    fun getCurrentUrl(): String
}
