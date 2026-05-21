package com.freewind.android.debugbridge.view

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.IdRes
import com.freewind.android.debugbridge.DebugBridge
import com.freewind.android.debugbridge.R
import com.freewind.android.debugbridge.domain.models.DebugActionRequest
import com.freewind.android.debugbridge.domain.models.DebugActionResult
import com.freewind.android.debugbridge.domain.models.DebugActionSpec
import com.freewind.android.debugbridge.domain.models.DebugActionTarget
import com.freewind.android.debugbridge.domain.models.DebugBounds
import com.freewind.android.debugbridge.domain.models.DebugNode
import java.lang.ref.WeakReference
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Coordination note for other AI: this package must stay app-agnostic.
// Put stable ids / business meaning in the app repo via debugNode(...), not here.
data class DebugViewNodeSpec(
    val id: String? = null,
    val parentId: String? = null,
    val type: String? = null,
    val text: String? = null,
    val role: String? = null,
    val clickable: Boolean? = null,
    val value: String? = null,
    val extra: Map<String, String> = emptyMap(),
)

private data class IndexedViewNode(
    val target: DebugActionTarget,
    val viewRef: WeakReference<View>,
)

data class DebugViewSnapshot(
    val nodes: List<DebugNode>,
    val actionTargets: List<DebugActionTarget>,
)

class DebugViewRegistry(
    @param:IdRes private val tagKey: Int = R.id.debug_server_view_spec_tag,
) {
    private val lock = Any()
    private val indexedNodes = linkedMapOf<String, IndexedViewNode>()
    private val fallbackActionIds = linkedSetOf<String>()

    fun snapshot(rootView: View): DebugViewSnapshot {
        val nextIndexedNodes = linkedMapOf<String, IndexedViewNode>()
        val nodes = buildNodes(
            view = rootView,
            parentId = null,
            resolvedSegment = null,
            nextIndexedNodes = nextIndexedNodes,
        )
        synchronized(lock) {
            indexedNodes.clear()
            indexedNodes.putAll(nextIndexedNodes)
        }
        return DebugViewSnapshot(
            nodes = nodes,
            actionTargets = nextIndexedNodes.values.map { it.target },
        )
    }

    suspend fun performAction(request: DebugActionRequest): DebugActionResult {
        val targetId = request.targetId ?: return DebugActionResult(
            ok = false,
            message = "missing targetId",
        )
        val indexedNode = synchronized(lock) {
            indexedNodes[targetId]
        } ?: return DebugActionResult(
            ok = false,
            message = "target not found: $targetId",
        )
        val view = indexedNode.viewRef.get() ?: return DebugActionResult(
            ok = false,
            message = "target view released: $targetId",
        )
        return withContext(Dispatchers.Main.immediate) {
            when (request.action) {
                "click" -> performClick(view, targetId)
                "longClick" -> performLongClick(view, targetId)
                "input" -> performInput(view, targetId, request.text)
                "setChecked" -> performSetChecked(view, targetId, request)
                else -> DebugActionResult(false, "unsupported action: ${request.action}")
            }
        }
    }

    internal fun syncFallbackActions(
        bridge: DebugBridge,
        actionTargets: List<DebugActionTarget>,
    ) {
        val bus = bridge.actionBusInternal()
        val nextIds = actionTargets.map { it.targetId }.toSet()
        synchronized(lock) {
            fallbackActionIds
                .filterNot { it in nextIds }
                .forEach(bus::unregisterFallbackAction)
            actionTargets.forEach { target ->
                bus.registerFallbackAction(target) { request ->
                    performAction(request)
                }
            }
            fallbackActionIds.clear()
            fallbackActionIds.addAll(nextIds)
        }
    }

    // App-side cleanup hook.
    // Call this when a screen leaves the foreground so stale targets stop leaking.
    fun release(bridge: DebugBridge) {
        val bus = bridge.actionBusInternal()
        synchronized(lock) {
            fallbackActionIds.forEach(bus::unregisterFallbackAction)
            fallbackActionIds.clear()
            indexedNodes.clear()
        }
    }

    private fun buildNodes(
        view: View,
        parentId: String?,
        resolvedSegment: String?,
        nextIndexedNodes: MutableMap<String, IndexedViewNode>,
    ): List<DebugNode> {
        val spec = view.debugNodeSpecOrNull(tagKey)
        val nodeId = resolveNodeId(
            view = view,
            parentId = parentId,
            resolvedSegment = resolvedSegment,
            explicitId = spec?.id,
        )
        val role = resolveRole(view, spec)
        val clickable = spec?.clickable ?: canClick(view)
        val debugNode = DebugNode(
            id = nodeId,
            parentId = spec?.parentId ?: parentId,
            type = spec?.type ?: normalizeTypeName(view),
            text = spec?.text ?: resolveText(view),
            role = role,
            backgroundColor = resolveBackgroundColor(view),
            contentColor = resolveContentColor(view),
            visible = isVisible(view),
            enabled = view.isEnabled,
            clickable = clickable,
            value = spec?.value ?: resolveValue(view),
            extra = resolveExtra(view, spec),
            bounds = resolveBounds(view),
        )
        buildActionTarget(debugNode, view)?.let { target ->
            nextIndexedNodes[nodeId] = IndexedViewNode(
                target = target,
                viewRef = WeakReference(view),
            )
        }
        if (view !is ViewGroup) {
            return listOf(debugNode)
        }
        val children = view.childrenWithResolvedIds()
        return buildList {
            add(debugNode)
            children.forEach { child ->
                addAll(
                    buildNodes(
                        view = child.view,
                        parentId = nodeId,
                        resolvedSegment = child.segment,
                        nextIndexedNodes = nextIndexedNodes,
                    ),
                )
            }
        }
    }

    private fun ViewGroup.childrenWithResolvedIds(): List<ResolvedChild> {
        val counts = mutableMapOf<String, Int>()
        return (0 until childCount).map { index ->
            val child = getChildAt(index)
            val spec = child.debugNodeSpecOrNull(tagKey)
            val baseName = spec?.id ?: child.defaultNodeSegment()
            val seen = counts.getOrDefault(baseName, 0) + 1
            counts[baseName] = seen
            ResolvedChild(
                view = child,
                segment = if (spec?.id != null || seen == 1) baseName else "$baseName#$seen",
            )
        }
    }

    private fun resolveNodeId(
        view: View,
        parentId: String?,
        resolvedSegment: String?,
        explicitId: String?,
    ): String {
        if (!explicitId.isNullOrBlank()) {
            return explicitId
        }
        val segment = resolvedSegment ?: view.defaultNodeSegment()
        return when {
            parentId.isNullOrBlank() -> segment
            else -> "$parentId/$segment"
        }
    }

    private fun buildActionTarget(
        node: DebugNode,
        view: View,
    ): DebugActionTarget? {
        val actions = mutableListOf<DebugActionSpec>()
        if (canClick(view)) {
            actions += DebugActionSpec(
                name = "click",
                summary = "trigger performClick or adapter item click",
            )
        }
        if (view.isLongClickable) {
            actions += DebugActionSpec(
                name = "longClick",
                summary = "trigger performLongClick",
            )
        }
        if (view is EditText) {
            actions += DebugActionSpec(
                name = "input",
                args = listOf("text"),
                summary = "replace input text",
            )
        }
        if (view is CompoundButton) {
            actions += DebugActionSpec(
                name = "setChecked",
                args = listOf("checked"),
                summary = "set checked true/false",
            )
        }
        if (actions.isEmpty()) {
            return null
        }
        return DebugActionTarget(
            targetId = node.id,
            targetType = node.type,
            actions = actions,
        )
    }

    private fun performClick(
        view: View,
        targetId: String,
    ): DebugActionResult {
        if (view.isClickable && view.performClick()) {
            return DebugActionResult(true, "clicked")
        }
        val adapterContext = view.findAdapterItemContext()
        if (adapterContext != null) {
            val accepted = adapterContext.adapterView.performItemClick(
                adapterContext.itemView,
                adapterContext.position,
                adapterContext.adapterView.adapter.getItemId(adapterContext.position),
            )
            return if (accepted) {
                DebugActionResult(true, "item clicked")
            } else {
                DebugActionResult(false, "item click rejected: $targetId")
            }
        }
        return DebugActionResult(false, "target not clickable: $targetId")
    }

    private fun performLongClick(
        view: View,
        targetId: String,
    ): DebugActionResult {
        if (!view.isLongClickable) {
            return DebugActionResult(false, "target not longClickable: $targetId")
        }
        return if (view.performLongClick()) {
            DebugActionResult(true, "long clicked")
        } else {
            DebugActionResult(false, "long click rejected: $targetId")
        }
    }

    private fun performInput(
        view: View,
        targetId: String,
        text: String?,
    ): DebugActionResult {
        if (view !is EditText) {
            return DebugActionResult(false, "target not input: $targetId")
        }
        view.setText(text.orEmpty())
        view.setSelection(view.text.length)
        return DebugActionResult(true, "input updated")
    }

    private fun performSetChecked(
        view: View,
        targetId: String,
        request: DebugActionRequest,
    ): DebugActionResult {
        if (view !is CompoundButton) {
            return DebugActionResult(false, "target not checkable: $targetId")
        }
        val raw = request.args["checked"] ?: request.text
        val checked = raw?.toBooleanStrictOrNull() ?: return DebugActionResult(
            ok = false,
            message = "missing checked=true|false",
        )
        view.isChecked = checked
        return DebugActionResult(true, "checked=$checked")
    }

    private fun resolveRole(
        view: View,
        spec: DebugViewNodeSpec?,
    ): String? {
        if (!spec?.role.isNullOrBlank()) {
            return spec?.role
        }
        return when (view) {
            is EditText -> "input"
            is CheckBox -> "switch"
            is CompoundButton -> "toggle"
            is Button, is ImageButton -> "button"
            is TextView -> "text"
            is AdapterView<*> -> "list"
            else -> if (canClick(view)) "button" else null
        }
    }

    private fun resolveText(view: View): String? {
        return when (view) {
            is TextView -> view.text?.toString()?.takeIf { it.isNotBlank() }
                ?: view.hint?.toString()?.takeIf { it.isNotBlank() }
            else -> view.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        }
    }

    private fun resolveValue(view: View): String? {
        return when (view) {
            is EditText -> view.text?.toString()
            is CompoundButton -> view.isChecked.toString()
            else -> null
        }
    }

    private fun resolveExtra(
        view: View,
        spec: DebugViewNodeSpec?,
    ): Map<String, String> {
        val extra = linkedMapOf<String, String>()
        view.safeResourceEntryName()?.let { extra["resourceId"] = it }
        extra["className"] = view.javaClass.name
        if (view is TextView) {
            view.hint?.toString()?.takeIf { it.isNotBlank() }?.let { extra["hint"] = it }
        }
        view.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { extra["contentDescription"] = it }
        if (view is ViewGroup) {
            extra["childCount"] = view.childCount.toString()
        }
        spec?.extra?.forEach { (key, value) ->
            extra[key] = value
        }
        return extra.toSortedMap()
    }

    private fun resolveBounds(view: View): DebugBounds? {
        if (view.width <= 0 || view.height <= 0) {
            return null
        }
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return DebugBounds(
            left = location[0].toFloat(),
            top = location[1].toFloat(),
            width = view.width.toFloat(),
            height = view.height.toFloat(),
        )
    }

    private fun resolveBackgroundColor(view: View): String? {
        return (view.background as? ColorDrawable)?.color?.toColorHex()
    }

    private fun resolveContentColor(view: View): String? {
        return when (view) {
            is TextView -> view.currentTextColor.toColorHex()
            else -> null
        }
    }

    private fun isVisible(view: View): Boolean {
        return view.visibility == View.VISIBLE && view.alpha > 0f && view.isShown
    }

    private fun canClick(view: View): Boolean {
        return view.isClickable || view is Button || view is ImageButton || view.findAdapterItemContext() != null
    }

    private fun normalizeTypeName(view: View): String {
        val simpleName = view.javaClass.simpleName
        return simpleName
            .removePrefix("AppCompat")
            .removePrefix("Material")
            .ifBlank { "View" }
    }

    private fun View.defaultNodeSegment(): String {
        return safeResourceEntryName() ?: normalizeSegment(normalizeTypeName(this))
    }

    private fun View.safeResourceEntryName(): String? {
        val viewId = id
        if (viewId == View.NO_ID) {
            return null
        }
        return runCatching { resources.getResourceEntryName(viewId) }.getOrNull()
    }

    private fun normalizeSegment(value: String): String {
        return value
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace(Regex("[^A-Za-z0-9_]+"), "_")
            .trim('_')
            .lowercase(Locale.US)
            .ifBlank { "view" }
    }

    private fun Int.toColorHex(): String {
        return String.format(Locale.US, "#%08X", this)
    }

    private fun View.debugNodeSpecOrNull(@IdRes key: Int): DebugViewNodeSpec? {
        return getTag(key) as? DebugViewNodeSpec
    }

    private fun String.toBooleanStrictOrNull(): Boolean? {
        return when (lowercase(Locale.US)) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private data class ResolvedChild(
        val view: View,
        val segment: String,
    )

    private data class AdapterItemContext(
        val adapterView: AdapterView<*>,
        val itemView: View,
        val position: Int,
    )

    private fun View.findAdapterItemContext(): AdapterItemContext? {
        var current: View = this
        var parent: ViewParent? = current.parent
        while (parent is ViewGroup) {
            if (parent is AdapterView<*>) {
                val position = parent.getPositionForView(this)
                if (position == AdapterView.INVALID_POSITION) {
                    return null
                }
                return AdapterItemContext(
                    adapterView = parent,
                    itemView = current,
                    position = position,
                )
            }
            current = parent
            parent = current.parent
        }
        return null
    }
}

fun View.debugNode(
    id: String? = null,
    parentId: String? = null,
    type: String? = null,
    text: String? = null,
    role: String? = null,
    clickable: Boolean? = null,
    value: String? = null,
    extra: Map<String, String> = emptyMap(),
): View {
    setTag(
        R.id.debug_server_view_spec_tag,
        DebugViewNodeSpec(
            id = id,
            parentId = parentId,
            type = type,
            text = text,
            role = role,
            clickable = clickable,
            value = value,
            extra = extra,
        ),
    )
    return this
}
