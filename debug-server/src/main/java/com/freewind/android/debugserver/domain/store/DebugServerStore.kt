package com.freewind.android.debugserver.domain.store

import com.freewind.android.debugserver.domain.models.DebugActionResult
import com.freewind.android.debugserver.domain.models.DebugNode
import com.freewind.android.debugserver.domain.models.DebugSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 只放内存态。
class DebugServerStore(
    private val appName: String,
    private val host: String,
    private val port: Int,
) {
    private val snapshotState = MutableStateFlow(
        DebugSnapshot(
            appName = appName,
            screenName = "Booting",
            componentCount = 0,
            serverHost = host,
            serverPort = port,
            updatedAtEpochMs = System.currentTimeMillis(),
            appState = emptyMap(),
            nodes = emptyList(),
        ),
    )

    private val actionLogState = MutableStateFlow<List<String>>(emptyList())

    fun snapshot(): StateFlow<DebugSnapshot> = snapshotState.asStateFlow()

    fun actionLog(): StateFlow<List<String>> = actionLogState.asStateFlow()

    fun updateSnapshot(
        screenName: String,
        appState: Map<String, String>,
        nodes: List<DebugNode>,
    ) {
        snapshotState.value = DebugSnapshot(
            appName = appName,
            screenName = screenName,
            componentCount = nodes.size,
            serverHost = host,
            serverPort = port,
            updatedAtEpochMs = System.currentTimeMillis(),
            appState = appState.toSortedMap(),
            nodes = nodes.sortedBy { it.id },
        )
    }

    fun appendActionLog(
        requestSummary: String,
        result: DebugActionResult,
    ) {
        val line = "${System.currentTimeMillis()} | $requestSummary | ${result.message}"
        actionLogState.value = (listOf(line) + actionLogState.value).take(30)
    }
}
