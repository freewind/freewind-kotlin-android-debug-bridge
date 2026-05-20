package com.freewind.android.debugserver.infra.system

import com.freewind.android.debugserver.domain.models.DebugActionSpec
import com.freewind.android.debugserver.domain.models.DebugActionRequest
import com.freewind.android.debugserver.domain.models.DebugActionResult
import com.freewind.android.debugserver.domain.models.DebugActionTarget
import java.util.concurrent.ConcurrentHashMap

// 动作总线。
class DebugActionBus {
    private val actions = ConcurrentHashMap<String, suspend (DebugActionRequest) -> DebugActionResult>()
    private val targets = ConcurrentHashMap<String, DebugActionTarget>()
    private val fallbackActions = ConcurrentHashMap<String, suspend (DebugActionRequest) -> DebugActionResult>()
    private val fallbackTargets = ConcurrentHashMap<String, DebugActionTarget>()

    fun registerAction(
        target: DebugActionTarget,
        action: suspend (DebugActionRequest) -> DebugActionResult,
    ) {
        actions[target.targetId] = action
        targets[target.targetId] = target.normalize()
    }

    fun unregisterAction(targetId: String) {
        actions.remove(targetId)
        targets.remove(targetId)
    }

    fun registerFallbackAction(
        target: DebugActionTarget,
        action: suspend (DebugActionRequest) -> DebugActionResult,
    ) {
        fallbackActions[target.targetId] = action
        fallbackTargets[target.targetId] = target.normalize()
    }

    fun unregisterFallbackAction(targetId: String) {
        fallbackActions.remove(targetId)
        fallbackTargets.remove(targetId)
    }

    fun targets(): List<DebugActionTarget> {
        val merged = linkedMapOf<String, DebugActionTarget>()
        fallbackTargets.values.sortedBy { it.targetId }.forEach { target ->
            merged[target.targetId] = target
        }
        targets.values.sortedBy { it.targetId }.forEach { target ->
            merged[target.targetId] = target
        }
        return merged.values.toList()
    }

    suspend fun dispatch(request: DebugActionRequest): DebugActionResult {
        val targetId = request.targetId ?: return DebugActionResult(
            ok = false,
            message = "missing targetId",
        )
        val action = actions[targetId] ?: fallbackActions[targetId] ?: return DebugActionResult(
            ok = false,
            message = "target not found: $targetId",
        )
        return action(request)
    }
}

private fun DebugActionTarget.normalize(): DebugActionTarget {
    val normalizedActions = if (actions.isEmpty()) {
        listOf(
            DebugActionSpec(
                name = "unknown",
                summary = "action metadata missing; inspect handler or docs",
            ),
        )
    } else {
        actions
    }
    return copy(actions = normalizedActions)
}
