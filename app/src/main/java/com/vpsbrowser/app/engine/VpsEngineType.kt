package com.vpsbrowser.app.engine

enum class VpsEngineType(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String
) {
    TURBO(
        id = "turbo",
        title = "⚡ Firefox Turbo Client (Recomendado)",
        subtitle = "Aceleración de hardware total y soporte nativo WebCodecs a 60 FPS (compatible con Selkies, KasmVNC y streaming H.264/VP9 sin retardo).",
        badge = "⚡ TURBO"
    ),
    GECKO(
        id = "gecko",
        title = "🦊 Mozilla GeckoView",
        subtitle = "Motor Firefox oficial de Mozilla integrado (WebRender GPU). Para navegación estándar. No compatible con streaming WebCodecs.",
        badge = "🦊 GECKO"
    );

    companion object {
        fun fromId(id: String): VpsEngineType {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: TURBO
        }
    }
}
