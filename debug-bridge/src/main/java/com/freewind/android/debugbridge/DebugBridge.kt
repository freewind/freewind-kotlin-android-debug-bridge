package com.freewind.android.debugbridge

import com.freewind.android.debugbridge.domain.handler.DebugBridgeHandler
import com.freewind.android.debugbridge.domain.models.DebugActionSpec
import com.freewind.android.debugbridge.domain.models.DebugActionRequest
import com.freewind.android.debugbridge.domain.models.DebugActionResult
import com.freewind.android.debugbridge.domain.models.DebugActionTarget
import com.freewind.android.debugbridge.domain.models.DebugNode
import com.freewind.android.debugbridge.domain.store.DebugBridgeStore
import com.freewind.android.debugbridge.infra.persistence.DebugHttpServer
import com.freewind.android.debugbridge.infra.system.DebugActionBus

// 对外主入口。
class DebugBridge(
    appName: String,
    consoleTitle: String? = null,
    host: String = "127.0.0.1",
    port: Int = 8765,
) {
    private val store = DebugBridgeStore(
        appName = appName,
        consoleTitle = consoleTitle,
        host = host,
        port = port,
    )
    private val actionBus = DebugActionBus()
    private val handler = DebugBridgeHandler(
        store = store,
        actionBus = actionBus,
    )
    private val httpServer = DebugHttpServer(
        host = host,
        port = port,
        store = store,
        handler = handler,
    )

    fun start() {
        httpServer.start()
    }

    fun stop() {
        httpServer.stop()
    }

    fun setBuildVersion(version: Int) {
        store.setBuildVersion(version)
    }

    fun publishSnapshot(
        screenName: String,
        appState: Map<String, String>,
        nodes: List<DebugNode>,
    ) {
        handler.publishSnapshot(
            screenName = screenName,
            appState = appState,
            nodes = nodes,
        )
    }

    fun publishTargetState(
        targetId: String,
        state: Map<String, String>,
    ) {
        store.updateTargetState(
            targetId = targetId,
            state = state,
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
        handler.recordHumanOperation(
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

    fun log(
        event: String,
        targetId: String? = null,
        level: String = "info",
        summary: String? = null,
        data: Map<String, String> = emptyMap(),
    ) {
        recordHumanOperation(
            action = event,
            targetId = targetId,
            message = summary,
            extra = linkedMapOf(
                "level" to level.ifBlank { "info" },
            ) + data,
        )
    }

    @Deprecated("Prefer explicit log(...) plus the original handler call.")
    fun recordClick(
        targetId: String,
        extra: Map<String, String> = emptyMap(),
        onClick: () -> Unit,
    ): () -> Unit {
        return {
            recordHumanOperation(
                action = "click",
                targetId = targetId,
                extra = extra,
            )
            onClick()
        }
    }

    @Deprecated("Prefer explicit log(...) plus the original handler call.")
    fun recordPress(
        targetId: String,
        extra: Map<String, String> = emptyMap(),
        onPress: () -> Unit,
    ): () -> Unit {
        return {
            recordHumanOperation(
                action = "press",
                targetId = targetId,
                extra = extra,
            )
            onPress()
        }
    }

    @Deprecated("Prefer explicit log(...) plus the original handler call.")
    fun recordToggle(
        targetId: String,
        extra: Map<String, String> = emptyMap(),
        onToggle: (Boolean) -> Unit,
    ): (Boolean) -> Unit {
        return { checked ->
            recordHumanOperation(
                action = "toggle",
                targetId = targetId,
                text = checked.toString(),
                extra = extra + ("checked" to checked.toString()),
            )
            onToggle(checked)
        }
    }

    @Deprecated("Prefer explicit log(...) plus the original handler call.")
    fun recordTextInput(
        targetId: String,
        extra: Map<String, String> = emptyMap(),
        onValueChange: (String) -> Unit,
    ): (String) -> Unit {
        return { value ->
            recordHumanOperation(
                action = "input",
                targetId = targetId,
                text = value,
                extra = extra + ("length" to value.length.toString()),
            )
            onValueChange(value)
        }
    }

    @Deprecated("Prefer explicit log(...) plus the original handler call.")
    fun <T> recordSelection(
        targetId: String,
        action: String = "select",
        extra: Map<String, String> = emptyMap(),
        valueToText: (T) -> String = { it.toString() },
        onSelect: (T) -> Unit,
    ): (T) -> Unit {
        return { value ->
            recordHumanOperation(
                action = action,
                targetId = targetId,
                text = valueToText(value),
                extra = extra,
            )
            onSelect(value)
        }
    }

    suspend fun performAction(request: DebugActionRequest): DebugActionResult {
        return handler.performAction(request)
    }

    fun registerAction(
        targetId: String,
        targetType: String? = null,
        screenName: String? = null,
        actions: List<DebugActionSpec> = emptyList(),
        action: suspend (DebugActionRequest) -> DebugActionResult,
    ) {
        actionBus.registerAction(
            target = DebugActionTarget(
                targetId = targetId,
                targetType = targetType,
                screenName = screenName,
                actions = actions,
            ),
            action = action,
        )
    }

    fun unregisterAction(targetId: String) {
        actionBus.unregisterAction(targetId)
    }

    internal fun actionBusInternal(): DebugActionBus = actionBus
}
