package com.freewind.android.debugserver.domain.handler

import com.freewind.android.debugserver.domain.models.DebugActionRequest
import com.freewind.android.debugserver.domain.models.DebugActionResult
import com.freewind.android.debugserver.domain.models.DebugNode
import com.freewind.android.debugserver.domain.store.DebugServerStore
import com.freewind.android.debugserver.infra.system.DebugActionBus

// 编排层。
class DebugServerHandler(
    private val store: DebugServerStore,
    private val actionBus: DebugActionBus,
) {
    fun publishSnapshot(
        screenName: String,
        appState: Map<String, String>,
        nodes: List<DebugNode>,
    ) {
        store.updateSnapshot(
            screenName = screenName,
            appState = appState,
            nodes = nodes,
        )
    }

    suspend fun performAction(request: DebugActionRequest): DebugActionResult {
        val result = actionBus.dispatch(request)
        store.appendActionLog(
            requestSummary = buildString {
                append(request.action)
                append(" target=")
                append(request.targetId)
                request.text?.let {
                    append(" text=")
                    append(it)
                }
                if (request.dx != null || request.dy != null) {
                    append(" delta=")
                    append(request.dx ?: 0f)
                    append(",")
                    append(request.dy ?: 0f)
                }
            },
            result = result,
        )
        return result
    }
}
