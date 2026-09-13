package com.vpsbrowser.app.engine

enum class VpsEngineType(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String
) {
    GECKO(
        id = "gecko",
        title = "🦊 Mozilla GeckoView",
        subtitle = "Motor Firefox real de Mozilla integrado. Aceleración GPU WebRender, control total de DOM y privacidad nativa.",
        badge = "🦊 GECKO"
    ),
    TURBO(
        id = "turbo",
        title = "⚡ Firefox Turbo Client",
        subtitle = "Motor ultraligero de baja latencia con manipulación directa de canvas a 60 FPS e intercepción táctil sin retraso.",
        badge = "⚡ TURBO"
    );

    companion object {
        fun fromId(id: String): VpsEngineType {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: GECKO
        }
    }
}
