package com.freewind.android.debugserver.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.freewind.android.debugserver.DebugBridge
import com.freewind.android.debugserver.domain.models.DebugActionRequest
import com.freewind.android.debugserver.domain.models.DebugActionResult

// 在 Compose 里注册动作。
@Composable
fun DebugBridge.RegisterDebugAction(
    targetId: String,
    registerKeys: Array<out Any?> = emptyArray(),
    action: suspend (DebugActionRequest) -> DebugActionResult,
) {
    DisposableEffect(targetId, *registerKeys) {
        registerAction(
            targetId = targetId,
            action = action,
        )
        onDispose {
            unregisterAction(targetId)
        }
    }
}
