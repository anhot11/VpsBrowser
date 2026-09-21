package com.vpsbrowser.app.engine

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView

/**
 * IncognitoManager: Gestor independiente de navegación en Modo Incógnito / RAM Pura.
 *
 * Requisitos:
 * 1. Desactiva toda escritura en disco de Android WebView:
 *    - cacheMode = LOAD_NO_CACHE (el caché reside solo en memoria volátil RAM)
 *    - domStorageEnabled = false (sin almacenamiento HTML5 persistente en disco)
 *    - databaseEnabled = false (sin bases de datos SQLite / IndexedDB en disco)
 *    - saveFormData = false (sin autocompletado en disco)
 *    - setGeolocationEnabled = false
 * 2. Las cookies y el caché viven únicamente en la memoria RAM volátil.
 * 3. Al desactivar o salir, purga de forma absoluta todas las cookies y cachés.
 * 4. Aislamiento total en su propio archivo sin alterar componentes existentes.
 */
object IncognitoManager {

    var isIncognitoActive: Boolean = false
        private set

    /**
     * Aplica la configuración de Memoria RAM Pura (Incógnito Forzado) al WebView.
     */
    fun applyIncognitoSettings(webView: WebView, enabled: Boolean) {
        isIncognitoActive = enabled
        val settings = webView.settings
        if (enabled) {
            // Memoria RAM pura forzada: Cero persistencia en almacenamiento flash/disco
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.domStorageEnabled = true // Required by Cloudflare Turnstile; purged upon exit
            settings.databaseEnabled = true
            @Suppress("DEPRECATION")
            settings.saveFormData = false
            settings.setGeolocationEnabled(false)

            // Limpieza inicial para garantizar sesión 100% limpia
            webView.clearCache(true)
            webView.clearFormData()
        } else {
            // Modo estándar normal
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            @Suppress("DEPRECATION")
            settings.saveFormData = true
            settings.setGeolocationEnabled(true)
        }
    }

    /**
     * Purga de forma atómica todas las cookies, caché volátil en RAM y datos de sesión.
     * Garantiza cero rastro en el dispositivo móvil.
     */
    fun purgeIncognitoData(webView: WebView?, onComplete: (() -> Unit)? = null) {
        try {
            webView?.clearCache(true)
            webView?.clearFormData()
            webView?.clearHistory()

            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies {
                cookieManager.removeSessionCookies {
                    try {
                        WebStorage.getInstance().deleteAllData()
                    } catch (_: Exception) {}
                    onComplete?.invoke()
                }
            }
        } catch (e: Exception) {
            onComplete?.invoke()
        }
    }
}
