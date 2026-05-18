package com.freewind.android.debugserver.infra.system

import com.freewind.android.debugserver.domain.models.DebugActionRequest
import com.freewind.android.debugserver.domain.models.DebugActionResult
import java.util.concurrent.ConcurrentHashMap

// 动作总线。
class DebugActionBus {
    private val actions = ConcurrentHashMap<String, suspend (DebugActionRequest) -> DebugActionResult>()

    fun registerAction(
        targetId: String,
        action: suspend (DebugActionRequest) -> DebugActionResult,
    ) {
        actions[targetId] = action
    }

    fun unregisterAction(targetId: String) {
        actions.remove(targetId)
    }

    suspend fun dispatch(request: DebugActionRequest): DebugActionResult {
        val targetId = request.targetId ?: return DebugActionResult(
            ok = false,
            message = "missing targetId",
        )
        val action = actions[targetId] ?: return DebugActionResult(
            ok = false,
            message = "target not found: $targetId",
        )
        return action(request)
    }
}
