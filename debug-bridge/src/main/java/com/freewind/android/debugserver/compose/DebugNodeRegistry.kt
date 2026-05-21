package com.freewind.android.debugbridge.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.freewind.android.debugbridge.domain.models.DebugNode

// 聚合当前页面已注册节点。
class DebugNodeRegistry {
    private val nodes = linkedMapOf<String, DebugNodeDraft>()
    var version by mutableIntStateOf(0)
        private set

    fun upsert(draft: DebugNodeDraft) {
        val previous = nodes[draft.id]
        if (previous != draft) {
            nodes[draft.id] = draft
            version += 1
        }
    }

    fun remove(id: String) {
        if (nodes.remove(id) != null) {
            version += 1
        }
    }

    fun snapshotNodes(): List<DebugNode> {
        return nodes.values.map { draft ->
            DebugNode(
                id = draft.id,
                parentId = draft.parentId,
                type = draft.type,
                text = draft.text,
                role = draft.role,
                backgroundColor = draft.backgroundColor?.toHexString(),
                contentColor = draft.contentColor?.toHexString(),
                visible = draft.visible,
                enabled = draft.enabled,
                clickable = draft.clickable,
                value = draft.value,
                extra = draft.extra,
                bounds = draft.bounds?.toDebugBounds(),
            )
        }
    }
}
