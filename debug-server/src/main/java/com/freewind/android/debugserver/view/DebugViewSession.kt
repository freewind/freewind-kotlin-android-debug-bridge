package com.freewind.android.debugserver.view

import android.view.View
import com.freewind.android.debugserver.DebugBridge

class DebugViewSession internal constructor(
    private val bridge: DebugBridge,
    val registry: DebugViewRegistry = DebugViewRegistry(),
) {
    @Volatile
    private var disposed = false

    fun publish(
        rootView: View,
        screenName: String,
        appState: Map<String, String> = emptyMap(),
    ) {
        if (disposed) {
            return
        }
        bridge.publishViewSnapshot(
            registry = registry,
            rootView = rootView,
            screenName = screenName,
            appState = appState,
        )
    }

    fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        registry.release(bridge)
    }
}

fun DebugBridge.createViewSession(): DebugViewSession {
    return DebugViewSession(bridge = this)
}
