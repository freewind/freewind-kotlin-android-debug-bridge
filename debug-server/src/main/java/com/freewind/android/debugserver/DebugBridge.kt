package com.freewind.android.debugserver

import com.freewind.android.debugserver.domain.handler.DebugServerHandler
import com.freewind.android.debugserver.domain.models.DebugActionRequest
import com.freewind.android.debugserver.domain.models.DebugActionResult
import com.freewind.android.debugserver.domain.models.DebugNode
import com.freewind.android.debugserver.domain.store.DebugServerStore
import com.freewind.android.debugserver.infra.persistence.DebugHttpServer
import com.freewind.android.debugserver.infra.system.DebugActionBus

// 对外主入口。
class DebugBridge(
    appName: String,
    host: String = "127.0.0.1",
    port: Int = 8765,
) {
    private val store = DebugServerStore(
        appName = appName,
        host = host,
        port = port,
    )
    private val actionBus = DebugActionBus()
    private val handler = DebugServerHandler(
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
        action: suspend (DebugActionRequest) -> DebugActionResult,
    ) {
        actionBus.registerAction(targetId, action)
    }

    fun unregisterAction(targetId: String) {
        actionBus.unregisterAction(targetId)
    }

    internal fun actionBusInternal(): DebugActionBus = actionBus
}
