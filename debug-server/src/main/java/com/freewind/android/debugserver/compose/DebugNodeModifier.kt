package com.freewind.android.debugserver.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.freewind.android.debugserver.domain.models.DebugNodeDraft

// 给节点打点。
@Composable
fun Modifier.debugNode(
    registry: DebugNodeRegistry,
    id: String,
    parentId: String? = null,
    type: String,
    text: String? = null,
    role: String? = null,
    backgroundColor: Color? = null,
    contentColor: Color? = null,
    visible: Boolean = true,
    enabled: Boolean = true,
    clickable: Boolean = false,
    value: String? = null,
    extra: Map<String, String> = emptyMap(),
): Modifier {
    var bounds by remember(id) { mutableStateOf<Rect?>(null) }

    SideEffect {
        registry.upsert(
            DebugNodeDraft(
                id = id,
                parentId = parentId,
                type = type,
                text = text,
                role = role,
                backgroundColor = backgroundColor,
                contentColor = contentColor,
                visible = visible,
                enabled = enabled,
                clickable = clickable,
                value = value,
                extra = extra,
                bounds = bounds,
            ),
        )
    }

    DisposableEffect(id) {
        onDispose {
            registry.remove(id)
        }
    }

    return this.then(
        Modifier.onGloballyPositioned { coordinates ->
            bounds = coordinates.boundsInWindow()
        },
    )
}
