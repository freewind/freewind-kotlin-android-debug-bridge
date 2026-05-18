package com.freewind.android.debugserver.domain.models

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import java.util.Locale

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

// 动作请求。
data class DebugActionRequest(
    val action: String,
    val targetId: String?,
    val text: String?,
    val dx: Float?,
    val dy: Float?,
)

// 动作结果。
data class DebugActionResult(
    val ok: Boolean,
    val message: String,
)

// 组件注册草稿。
data class DebugNodeDraft(
    val id: String,
    val type: String,
    val text: String? = null,
    val role: String? = null,
    val backgroundColor: Color? = null,
    val contentColor: Color? = null,
    val visible: Boolean = true,
    val enabled: Boolean = true,
    val clickable: Boolean = false,
    val value: String? = null,
    val extra: Map<String, String> = emptyMap(),
    val bounds: Rect? = null,
)

fun Rect.toDebugBounds(): DebugBounds {
    return DebugBounds(
        left = left,
        top = top,
        width = width,
        height = height,
    )
}

fun Color.toHexString(): String {
    return String.format(Locale.US, "#%08X", toArgbCompat())
}

private fun Color.toArgbCompat(): Int {
    val alphaInt = (alpha * 255f).toInt().coerceIn(0, 255)
    val redInt = (red * 255f).toInt().coerceIn(0, 255)
    val greenInt = (green * 255f).toInt().coerceIn(0, 255)
    val blueInt = (blue * 255f).toInt().coerceIn(0, 255)
    return (alphaInt shl 24) or (redInt shl 16) or (greenInt shl 8) or blueInt
}
