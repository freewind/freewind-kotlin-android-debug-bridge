package com.freewind.android.debugbridge.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.freewind.android.debugbridge.DebugBridge
import com.freewind.android.debugbridge.infra.system.DebugActionBus

// 把 registry 推到 bridge。
@Composable
fun DebugBridge.publishComposeSnapshot(
    registry: DebugNodeRegistry,
    screenName: String,
    appState: Map<String, String> = emptyMap(),
) {
    LaunchedEffect(registry.version, screenName, appState) {
        publishSnapshot(
            screenName = screenName,
            appState = appState,
            nodes = registry.snapshotNodes(),
        )
    }
}

@Deprecated("Prefer publishComposeSnapshot(...).")
@Composable
fun DebugBridge.PublishComposeSnapshot(
    registry: DebugNodeRegistry,
    screenName: String,
    appState: Map<String, String> = emptyMap(),
) {
    publishComposeSnapshot(
        registry = registry,
        screenName = screenName,
        appState = appState,
    )
}

// 暴露 action bus，便于 UI 注册动作。
fun DebugBridge.actionBus(): DebugActionBus = actionBusInternal()
