package com.freewind.android.debugbridge.view

import android.view.View
import com.freewind.android.debugbridge.DebugBridge

fun DebugBridge.publishViewSnapshot(
    registry: DebugViewRegistry,
    rootView: View,
    screenName: String,
    appState: Map<String, String> = emptyMap(),
) {
    val snapshot = registry.snapshot(rootView)
    publishSnapshot(
        screenName = screenName,
        appState = appState,
        nodes = snapshot.nodes,
    )
    registry.syncFallbackActions(
        bridge = this,
        actionTargets = snapshot.actionTargets.map { it.copy(screenName = screenName) },
    )
}
