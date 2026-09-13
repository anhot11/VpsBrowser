package com.vpsbrowser.app.engine

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoEngineManager {
    @Volatile
    private var runtime: GeckoRuntime? = null

    @Synchronized
    fun getOrCreateRuntime(context: Context): GeckoRuntime {
        if (runtime == null) {
            val settings = GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(true)
                .extensionsWebAPIEnabled(true)
                .consoleOutput(true)
                .build()
            runtime = GeckoRuntime.create(context.applicationContext, settings)
        }
        return runtime!!
    }

    fun isInitialized(): Boolean = runtime != null
}
