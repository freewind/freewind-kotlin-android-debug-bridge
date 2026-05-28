package com.freewind.android.debugbridge.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.freewind.android.debugbridge.DebugBridge
import com.freewind.android.debugbridge.domain.models.DebugActionSpec
import com.freewind.android.debugbridge.domain.models.DebugActionRequest
import com.freewind.android.debugbridge.domain.models.DebugActionResult

// 在 Compose 里注册动作。
@Composable
fun DebugBridge.registerComposeAction(
    targetId: String,
    registerKeys: Array<out Any?> = emptyArray(),
    targetType: String? = null,
    screenName: String? = null,
    actions: List<DebugActionSpec> = emptyList(),
    action: suspend (DebugActionRequest) -> DebugActionResult,
) {
    DisposableEffect(targetId, targetType, screenName, actions, *registerKeys) {
        registerAction(
            targetId = targetId,
            targetType = targetType,
            screenName = screenName,
            actions = actions,
            action = action,
        )
        onDispose {
            unregisterAction(targetId)
        }
    }
}

@Deprecated("Prefer registerComposeAction(...).")
@Composable
fun DebugBridge.RegisterDebugAction(
    targetId: String,
    registerKeys: Array<out Any?> = emptyArray(),
    targetType: String? = null,
    screenName: String? = null,
    actions: List<DebugActionSpec> = emptyList(),
    action: suspend (DebugActionRequest) -> DebugActionResult,
) {
    registerComposeAction(
        targetId = targetId,
        registerKeys = registerKeys,
        targetType = targetType,
        screenName = screenName,
        actions = actions,
        action = action,
    )
}
