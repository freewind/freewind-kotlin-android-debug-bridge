package com.freewind.android.debugbridge.domain.store

import com.freewind.android.debugbridge.domain.models.DebugNode
import com.freewind.android.debugbridge.domain.models.DebugOperation
import com.freewind.android.debugbridge.domain.models.DebugOperationSource
import com.freewind.android.debugbridge.domain.models.DebugOperationsQuery
import com.freewind.android.debugbridge.domain.models.DebugOperationsResult
import com.freewind.android.debugbridge.domain.models.DebugSnapshot
import com.freewind.android.debugbridge.domain.models.DebugSnapshotQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 只放内存态。
class DebugBridgeStore(
    private val appName: String,
    private val consoleTitle: String?,
    private val host: String,
    private val port: Int,
) {
    private val lock = Any()
    private var nextOperationSeq = 1L
    private val buildVersionState = MutableStateFlow(0)

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

    private val operationState = MutableStateFlow<List<DebugOperation>>(emptyList())
    private val targetStateState = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    fun snapshot(): StateFlow<DebugSnapshot> = snapshotState.asStateFlow()

    fun operations(): StateFlow<List<DebugOperation>> = operationState.asStateFlow()

    fun targetStates(): StateFlow<Map<String, Map<String, String>>> = targetStateState.asStateFlow()

    fun buildVersion(): StateFlow<Int> = buildVersionState.asStateFlow()

    fun appName(): String = appName

    fun consoleTitle(): String? = consoleTitle?.trim()?.takeIf { it.isNotEmpty() }

    fun setBuildVersion(version: Int) {
        buildVersionState.value = version.coerceAtLeast(0)
    }

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

    fun recordOperation(
        source: DebugOperationSource,
        action: String,
        targetId: String? = null,
        text: String? = null,
        dx: Float? = null,
        dy: Float? = null,
        success: Boolean? = null,
        message: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): DebugOperation {
        return synchronized(lock) {
            val snapshot = snapshotState.value
            val targetNode = snapshot.nodes.firstOrNull { it.id == targetId }
            val operation = DebugOperation(
                seq = nextOperationSeq,
                source = source,
                action = action,
                targetId = targetId,
                targetParentId = targetNode?.parentId,
                targetType = targetNode?.type,
                targetText = targetNode?.text,
                screenName = snapshot.screenName,
                text = text,
                dx = dx,
                dy = dy,
                success = success,
                message = message,
                extra = extra.toSortedMap(),
                createdAtEpochMs = System.currentTimeMillis(),
            )
            nextOperationSeq += 1
            operationState.value = (operationState.value + operation).takeLast(200)
            operation
        }
    }

    fun clearOperations() {
        synchronized(lock) {
            operationState.value = emptyList()
        }
    }

    fun updateTargetState(
        targetId: String,
        state: Map<String, String>,
    ) {
        synchronized(lock) {
            targetStateState.value = targetStateState.value + (targetId to state.toSortedMap())
        }
    }

    fun querySnapshot(query: DebugSnapshotQuery): DebugSnapshot {
        val snapshot = snapshotState.value
        val nodes = filterNodes(snapshot.nodes, query)
        val appState = when {
            query.appStateKeys.isEmpty() -> snapshot.appState
            else -> snapshot.appState.filterKeys { it in query.appStateKeys }.toSortedMap()
        }
        return snapshot.copy(
            componentCount = nodes.size,
            appState = appState,
            nodes = nodes,
        )
    }

    fun queryOperations(query: DebugOperationsQuery): DebugOperationsResult {
        return synchronized(lock) {
            val filtered = operationState.value
                .asSequence()
                .filter { operation ->
                    query.afterSeq == null || operation.seq > query.afterSeq
                }
                .filter { operation ->
                    query.sources.isEmpty() || operation.source in query.sources
                }
                .toList()
            val items = filtered.take(query.limit.coerceAtLeast(0))
            if (query.consume && items.isNotEmpty()) {
                val consumedSeqs = items.map { it.seq }.toSet()
                operationState.value = operationState.value.filterNot { it.seq in consumedSeqs }
            }
            DebugOperationsResult(
                items = items,
                nextAfterSeq = items.lastOrNull()?.seq ?: (query.afterSeq ?: 0L),
                remainingCount = (filtered.size - items.size).coerceAtLeast(0),
            )
        }
    }

    private fun filterNodes(
        nodes: List<DebugNode>,
        query: DebugSnapshotQuery,
    ): List<DebugNode> {
        if (nodes.isEmpty()) {
            return emptyList()
        }
        val nodeById = nodes.associateBy { it.id }
        val childrenByParentId = nodes.groupBy { it.parentId }
        val filteredBaseNodes = nodes.filter { node ->
            (query.nodeIds.isEmpty() || node.id in query.nodeIds) &&
                (!query.visibleOnly || node.visible) &&
                (!query.clickableOnly || node.clickable) &&
                (query.types.isEmpty() || node.type in query.types) &&
                matchesTextQuery(node, query.textQuery)
        }
        val expandedIds = linkedSetOf<String>()
        filteredBaseNodes.forEach { node ->
            expandedIds += node.id
            if (query.includeAncestors) {
                expandedIds += collectAncestorIds(node, nodeById, query.ancestorDepth)
            }
            if (query.descendantDepth > 0) {
                expandedIds += collectDescendantIds(node.id, childrenByParentId, query.descendantDepth)
            }
        }
        val resolved = when {
            hasNodeFilter(query) -> nodes.filter { it.id in expandedIds }
            else -> nodes
        }
        val limited = query.limit?.let { limit ->
            resolved.take(limit.coerceAtLeast(0))
        } ?: resolved
        return limited
    }

    private fun collectAncestorIds(
        node: DebugNode,
        nodeById: Map<String, DebugNode>,
        ancestorDepth: Int?,
    ): Set<String> {
        val result = linkedSetOf<String>()
        var currentParentId = node.parentId
        var depth = 0
        while (currentParentId != null) {
            val parentNode = nodeById[currentParentId] ?: break
            result += parentNode.id
            currentParentId = parentNode.parentId
            depth += 1
            if (ancestorDepth != null && depth >= ancestorDepth) {
                break
            }
        }
        return result
    }

    private fun collectDescendantIds(
        nodeId: String,
        childrenByParentId: Map<String?, List<DebugNode>>,
        descendantDepth: Int,
    ): Set<String> {
        val result = linkedSetOf<String>()
        var frontier = listOf(nodeId)
        repeat(descendantDepth) {
            val nextFrontier = mutableListOf<String>()
            frontier.forEach { parentId ->
                childrenByParentId[parentId].orEmpty().forEach { child ->
                    if (result.add(child.id)) {
                        nextFrontier += child.id
                    }
                }
            }
            frontier = nextFrontier
            if (frontier.isEmpty()) {
                return result
            }
        }
        return result
    }

    private fun hasNodeFilter(query: DebugSnapshotQuery): Boolean {
        return query.nodeIds.isNotEmpty() ||
            query.includeAncestors ||
            query.descendantDepth > 0 ||
            query.visibleOnly ||
            query.clickableOnly ||
            query.types.isNotEmpty() ||
            !query.textQuery.isNullOrBlank() ||
            query.limit != null
    }

    private fun matchesTextQuery(
        node: DebugNode,
        textQuery: String?,
    ): Boolean {
        if (textQuery.isNullOrBlank()) {
            return true
        }
        val query = textQuery.lowercase()
        return node.text?.lowercase()?.contains(query) == true ||
            node.id.lowercase().contains(query) ||
            node.type.lowercase().contains(query)
    }
}
