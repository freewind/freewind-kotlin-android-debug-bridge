package com.freewind.android.debugbridge.domain.handler

import com.freewind.android.debugbridge.domain.models.DebugActionRequest
import com.freewind.android.debugbridge.domain.models.DebugActionResult
import com.freewind.android.debugbridge.domain.models.DebugActionTarget
import com.freewind.android.debugbridge.domain.models.DebugNode
import com.freewind.android.debugbridge.domain.models.DebugOperationSource
import com.freewind.android.debugbridge.domain.models.DebugOperationsQuery
import com.freewind.android.debugbridge.domain.models.DebugOperationsResult
import com.freewind.android.debugbridge.domain.models.DebugSnapshot
import com.freewind.android.debugbridge.domain.models.DebugSnapshotQuery
import com.freewind.android.debugbridge.domain.store.DebugBridgeStore
import com.freewind.android.debugbridge.infra.system.DebugActionBus
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

// 编排层。
class DebugBridgeHandler(
    private val store: DebugBridgeStore,
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
        val startedAt = System.currentTimeMillis()
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
                errorType = "TimeoutCancellationException",
                timedOut = true,
            )
        } catch (error: Throwable) {
            DebugActionResult(
                ok = false,
                message = buildActionErrorMessage(error),
                errorType = error::class.java.simpleName.ifBlank { error::class.java.name },
            )
        }
        val completedResult = result.copy(
            durationMs = result.durationMs ?: (System.currentTimeMillis() - startedAt),
        )
        store.recordOperation(
            source = DebugOperationSource.fromWireValue(request.source) ?: DebugOperationSource.AI,
            action = request.action,
            targetId = request.targetId,
            text = request.text,
            dx = request.dx,
            dy = request.dy,
            success = completedResult.ok,
            message = completedResult.message,
            extra = request.args + completedResult.toOperationDetails(),
        )
        return completedResult
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

    private fun DebugActionResult.toOperationDetails(): Map<String, String> {
        return linkedMapOf(
            "durationMs" to durationMs?.toString().orEmpty(),
            "errorType" to errorType.orEmpty(),
            "timedOut" to timedOut.toString(),
        ).filterValues { it.isNotBlank() }
    }
}
