package com.vpsbrowser.app.model

import java.util.UUID

data class VpsProfile(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var host: String,
    var port: Int = 3000,
    var password: String = "",
    var useSsl: Boolean = false,
    var touchEmulation: Boolean = true,
    var resolution: String = "1920x1080"
) {
    fun getFullUrl(): String {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://")
        val scheme = if (useSsl) "https" else "http"
        return if (cleanHost.contains(":")) {
            "$scheme://$cleanHost"
        } else {
            "$scheme://$cleanHost:$port"
        }
    }

    fun getCleanHost(): String {
        return host.trim().removePrefix("http://").removePrefix("https://").split(":")[0]
    }
}
