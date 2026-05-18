package com.freewind.android.debugserver.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.freewind.android.debugserver.DebugBridge
import com.freewind.android.debugserver.infra.system.DebugActionBus

// 把 registry 推到 bridge。
@Composable
fun DebugBridge.PublishComposeSnapshot(
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

// 暴露 action bus，便于 UI 注册动作。
fun DebugBridge.actionBus(): DebugActionBus = actionBusInternal()
