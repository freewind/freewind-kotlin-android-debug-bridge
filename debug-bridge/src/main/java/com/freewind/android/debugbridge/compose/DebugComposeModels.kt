package com.freewind.android.debugbridge.compose

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.freewind.android.debugbridge.domain.models.DebugBounds
import java.util.Locale

// Compose 注册草稿只留在 Compose 包，core model 保持 UI 技术无关。
data class DebugNodeDraft(
    val id: String,
    val parentId: String? = null,
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
