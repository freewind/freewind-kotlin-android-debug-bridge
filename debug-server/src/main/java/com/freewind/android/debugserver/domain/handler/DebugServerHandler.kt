package com.freewind.android.debugserver.domain.handler

import com.freewind.android.debugserver.domain.models.DebugActionRequest
import com.freewind.android.debugserver.domain.models.DebugActionResult
import com.freewind.android.debugserver.domain.models.DebugActionTarget
import com.freewind.android.debugserver.domain.models.DebugNode
import com.freewind.android.debugserver.domain.models.DebugOperationSource
import com.freewind.android.debugserver.domain.models.DebugOperationsQuery
import com.freewind.android.debugserver.domain.models.DebugOperationsResult
import com.freewind.android.debugserver.domain.models.DebugSnapshot
import com.freewind.android.debugserver.domain.models.DebugSnapshotQuery
import com.freewind.android.debugserver.domain.store.DebugServerStore
import com.freewind.android.debugserver.infra.system.DebugActionBus
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

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

    fun recordHumanOperation(
        action: String,
        targetId: String? = null,
        text: String? = null,
        dx: Float? = null,
        dy: Float? = null,
        success: Boolean? = null,
        message: String? = null,
        extra: Map<String, String> = emptyMap(),
    ) {
        store.recordOperation(
            source = DebugOperationSource.HUMAN,
            action = action,
            targetId = targetId,
            text = text,
            dx = dx,
            dy = dy,
            success = success,
            message = message,
            extra = extra,
        )
    }

    suspend fun performAction(
        request: DebugActionRequest,
        timeoutMs: Long? = null,
    ): DebugActionResult {
        val result = try {
            if (timeoutMs == null) {
                actionBus.dispatch(request)
            } else {
                withTimeout(timeoutMs) {
                    actionBus.dispatch(request)
                }
            }
        } catch (_: TimeoutCancellationException) {
            DebugActionResult(
                ok = false,
                message = "action timeout after ${timeoutMs ?: 0L}ms",
            )
        } catch (error: Throwable) {
            DebugActionResult(
                ok = false,
                message = buildActionErrorMessage(error),
            )
        }
        store.recordOperation(
            source = DebugOperationSource.fromWireValue(request.source) ?: DebugOperationSource.AI,
            action = request.action,
            targetId = request.targetId,
            text = request.text,
            dx = request.dx,
            dy = request.dy,
            success = result.ok,
            message = result.message,
            extra = request.args,
        )
        return result
    }

    private fun buildActionErrorMessage(error: Throwable): String {
        val simpleName = error::class.java.simpleName.ifBlank { "Throwable" }
        val message = error.message.orEmpty().trim()
        return if (message.isBlank()) {
            "action error: $simpleName"
        } else {
            "action error: $simpleName: $message"
        }
    }

    fun querySnapshot(query: DebugSnapshotQuery): DebugSnapshot {
        return store.querySnapshot(query)
    }

    fun queryOperations(query: DebugOperationsQuery): DebugOperationsResult {
        return store.queryOperations(query)
    }

    fun actionTargets(): List<DebugActionTarget> {
        return actionBus.targets()
    }
}
