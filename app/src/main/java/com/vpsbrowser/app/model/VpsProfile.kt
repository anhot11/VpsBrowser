package com.vpsbrowser.app.model

import java.util.UUID

data class VpsProfile(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var host: String,
    var sshPort: Int = 22,
    var sshUser: String = "root",
    var sshPassword: String = "",
    var sshPrivateKey: String = "",
    var useSshTunnel: Boolean = true, // Zero open ports: traffic encrypted through SSH tunnel
    var cloudflareUrl: String = "", // Free HTTPS Cloudflare Tunnel (trycloudflare.com)
    var useCloudflareTunnel: Boolean = false, // Connect directly via Cloudflare Tunnel
    var browserPort: Int = 3000,
    var browserPassword: String = "",
    var browserEngine: String = "firefox",
    var useSsl: Boolean = false,
    var touchEmulation: Boolean = true,
    var resolution: String = "1920x1080"
) {
    var port: Int
        get() = browserPort
        set(value) { browserPort = value }

    var password: String
        get() = browserPassword
        set(value) { browserPassword = value }

    fun getCleanHost(): String {
        return host.trim().removePrefix("http://").removePrefix("https://").split(":")[0]
    }

    fun getDirectUrl(): String {
        val cleanHost = getCleanHost()
        val scheme = if (useSsl) "https" else "http"
        return "$scheme://$cleanHost:$browserPort"
    }

    fun getFullUrl(): String = getDirectUrl()

    fun isKeyAuth(): Boolean = sshPrivateKey.isNotBlank()
}

