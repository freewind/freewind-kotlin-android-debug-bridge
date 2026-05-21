package com.freewind.android.debugbridge.compose

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

object DebugNodeTypes {
    const val BUTTON = "Button"
    const val CARD = "Card"
    const val COLUMN = "Column"
    const val LAZY_COLUMN = "LazyColumn"
    const val ROW = "Row"
    const val SWITCH = "Switch"
    const val TEXT = "Text"
    const val TEXT_FIELD = "TextField"
}

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

@Composable
fun Modifier.debugTextNode(
    registry: DebugNodeRegistry,
    id: String,
    text: String,
    parentId: String? = null,
    visible: Boolean = true,
    enabled: Boolean = true,
    extra: Map<String, String> = emptyMap(),
): Modifier {
    return debugNode(
        registry = registry,
        id = id,
        parentId = parentId,
        type = DebugNodeTypes.TEXT,
        text = text,
        role = "text",
        visible = visible,
        enabled = enabled,
        clickable = false,
        extra = extra,
    )
}

@Composable
fun Modifier.debugButtonNode(
    registry: DebugNodeRegistry,
    id: String,
    text: String,
    parentId: String? = null,
    enabled: Boolean = true,
    backgroundColor: Color? = null,
    contentColor: Color? = null,
    extra: Map<String, String> = emptyMap(),
): Modifier {
    return debugNode(
        registry = registry,
        id = id,
        parentId = parentId,
        type = DebugNodeTypes.BUTTON,
        text = text,
        role = "button",
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        enabled = enabled,
        clickable = true,
        extra = extra + mapOf(
            "label" to text,
        ),
    )
}

@Composable
fun Modifier.debugSwitchNode(
    registry: DebugNodeRegistry,
    id: String,
    checked: Boolean,
    parentId: String? = null,
    labelText: String? = null,
    enabled: Boolean = true,
    extra: Map<String, String> = emptyMap(),
): Modifier {
    val fixedExtra = linkedMapOf(
        "checked" to checked.toString(),
    ).apply {
        labelText?.let { put("label", it) }
        putAll(extra)
    }
    return debugNode(
        registry = registry,
        id = id,
        parentId = parentId,
        type = DebugNodeTypes.SWITCH,
        text = labelText,
        role = "switch",
        enabled = enabled,
        clickable = true,
        value = checked.toString(),
        extra = fixedExtra,
    )
}

@Composable
fun Modifier.debugTextFieldNode(
    registry: DebugNodeRegistry,
    id: String,
    value: String,
    parentId: String? = null,
    labelText: String? = null,
    placeholderText: String? = null,
    enabled: Boolean = true,
    extra: Map<String, String> = emptyMap(),
): Modifier {
    val fixedExtra = linkedMapOf(
        "length" to value.length.toString(),
    ).apply {
        labelText?.let { put("label", it) }
        placeholderText?.let { put("placeholder", it) }
        putAll(extra)
    }
    return debugNode(
        registry = registry,
        id = id,
        parentId = parentId,
        type = DebugNodeTypes.TEXT_FIELD,
        text = labelText ?: value,
        role = "input",
        enabled = enabled,
        clickable = true,
        value = value,
        extra = fixedExtra,
    )
}

@Composable
fun Modifier.debugCardNode(
    registry: DebugNodeRegistry,
    id: String,
    parentId: String? = null,
    text: String? = null,
    backgroundColor: Color? = null,
    contentColor: Color? = null,
    visible: Boolean = true,
    enabled: Boolean = true,
    extra: Map<String, String> = emptyMap(),
): Modifier {
    return debugNode(
        registry = registry,
        id = id,
        parentId = parentId,
        type = DebugNodeTypes.CARD,
        text = text,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        visible = visible,
        enabled = enabled,
        extra = extra,
    )
}

@Composable
fun Modifier.debugColumnNode(
    registry: DebugNodeRegistry,
    id: String,
    parentId: String? = null,
    visible: Boolean = true,
    enabled: Boolean = true,
    extra: Map<String, String> = emptyMap(),
): Modifier {
    return debugNode(
        registry = registry,
        id = id,
        parentId = parentId,
        type = DebugNodeTypes.COLUMN,
        visible = visible,
        enabled = enabled,
        extra = extra,
    )
}

@Composable
fun Modifier.debugRowNode(
    registry: DebugNodeRegistry,
    id: String,
    parentId: String? = null,
    visible: Boolean = true,
    enabled: Boolean = true,
    extra: Map<String, String> = emptyMap(),
): Modifier {
    return debugNode(
        registry = registry,
        id = id,
        parentId = parentId,
        type = DebugNodeTypes.ROW,
        visible = visible,
        enabled = enabled,
        extra = extra,
    )
}

@Composable
fun Modifier.debugLazyColumnNode(
    registry: DebugNodeRegistry,
    id: String,
    itemCount: Int,
    parentId: String? = null,
    visible: Boolean = true,
    enabled: Boolean = true,
    extra: Map<String, String> = emptyMap(),
): Modifier {
    return debugNode(
        registry = registry,
        id = id,
        parentId = parentId,
        type = DebugNodeTypes.LAZY_COLUMN,
        role = "list",
        visible = visible,
        enabled = enabled,
        extra = extra + mapOf(
            "itemCount" to itemCount.toString(),
        ),
    )
}
