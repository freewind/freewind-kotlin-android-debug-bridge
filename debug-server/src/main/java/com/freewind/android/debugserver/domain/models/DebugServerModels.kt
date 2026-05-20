package com.freewind.android.debugserver.domain.models

// 组件边界。
data class DebugBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

// 组件快照。
data class DebugNode(
    val id: String,
    val parentId: String?,
    val type: String,
    val text: String?,
    val role: String?,
    val backgroundColor: String?,
    val contentColor: String?,
    val visible: Boolean,
    val enabled: Boolean,
    val clickable: Boolean,
    val value: String?,
    val extra: Map<String, String>,
    val bounds: DebugBounds?,
)

// 页面快照。
data class DebugSnapshot(
    val appName: String,
    val screenName: String,
    val componentCount: Int,
    val serverHost: String,
    val serverPort: Int,
    val updatedAtEpochMs: Long,
    val appState: Map<String, String>,
    val nodes: List<DebugNode>,
)

data class DebugActionSpec(
    val name: String,
    val args: List<String> = emptyList(),
    val summary: String? = null,
)

data class DebugActionTarget(
    val targetId: String,
    val targetType: String? = null,
    val screenName: String? = null,
    val actions: List<DebugActionSpec> = emptyList(),
)

// 动作请求。
data class DebugActionRequest(
    val action: String,
    val targetId: String?,
    val text: String?,
    val dx: Float?,
    val dy: Float?,
    val args: Map<String, String> = emptyMap(),
    val source: String? = null,
)

// 动作结果。
data class DebugActionResult(
    val ok: Boolean,
    val message: String,
    val errorType: String? = null,
    val durationMs: Long? = null,
    val timedOut: Boolean = false,
)

enum class DebugOperationSource(
    val wireValue: String,
) {
    SYSTEM("system"),
    HUMAN("human"),
    AI("ai");

    companion object {
        fun fromWireValue(value: String?): DebugOperationSource? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

data class DebugOperation(
    val seq: Long,
    val source: DebugOperationSource,
    val action: String,
    val targetId: String?,
    val targetParentId: String?,
    val targetType: String?,
    val targetText: String?,
    val screenName: String,
    val text: String?,
    val dx: Float?,
    val dy: Float?,
    val success: Boolean?,
    val message: String?,
    val extra: Map<String, String>,
    val createdAtEpochMs: Long,
)

data class DebugSnapshotQuery(
    val compact: Boolean = true,
    val snapshotFields: Set<String> = emptySet(),
    val nodeFields: Set<String> = emptySet(),
    val appStateKeys: Set<String> = emptySet(),
    val nodeIds: Set<String> = emptySet(),
    val includeAncestors: Boolean = false,
    val ancestorDepth: Int? = null,
    val descendantDepth: Int = 0,
    val visibleOnly: Boolean = false,
    val clickableOnly: Boolean = false,
    val types: Set<String> = emptySet(),
    val textQuery: String? = null,
    val limit: Int? = null,
)

data class DebugOperationsQuery(
    val afterSeq: Long? = null,
    val limit: Int = 20,
    val consume: Boolean = false,
    val sources: Set<DebugOperationSource> = emptySet(),
    val groupBySource: Boolean = false,
)

data class DebugOperationsResult(
    val items: List<DebugOperation>,
    val nextAfterSeq: Long,
    val remainingCount: Int,
)
