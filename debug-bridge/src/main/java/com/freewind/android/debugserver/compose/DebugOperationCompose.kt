package com.freewind.android.debugbridge.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.freewind.android.debugbridge.DebugBridge
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

// 自动记录普通滚动。
@Composable
fun DebugBridge.RecordScrollState(
    targetId: String,
    state: ScrollState,
    axis: String = "vertical",
) {
    LaunchedEffect(this, targetId, state, axis) {
        var lastValue = state.value
        snapshotFlow { state.value }
            .drop(1)
            .distinctUntilChanged()
            .collect { value ->
                recordHumanOperation(
                    action = "scroll",
                    targetId = targetId,
                    dx = if (axis == "horizontal") (value - lastValue).toFloat() else null,
                    dy = if (axis == "horizontal") null else (value - lastValue).toFloat(),
                    extra = mapOf(
                        "axis" to axis,
                        "position" to value.toString(),
                    ),
                )
                lastValue = value
            }
    }
}

// 自动记录 Lazy 列表滚动。
@Composable
fun DebugBridge.RecordLazyListScroll(
    targetId: String,
    state: LazyListState,
    axis: String = "vertical",
) {
    LaunchedEffect(this, targetId, state, axis) {
        var lastIndex = state.firstVisibleItemIndex
        var lastOffset = state.firstVisibleItemScrollOffset
        snapshotFlow {
            state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        }
            .drop(1)
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val delta = ((index - lastIndex) * 100000) + (offset - lastOffset)
                recordHumanOperation(
                    action = "scroll",
                    targetId = targetId,
                    dx = if (axis == "horizontal") delta.toFloat() else null,
                    dy = if (axis == "horizontal") null else delta.toFloat(),
                    extra = mapOf(
                        "axis" to axis,
                        "firstVisibleItemIndex" to index.toString(),
                        "firstVisibleItemScrollOffset" to offset.toString(),
                    ),
                )
                lastIndex = index
                lastOffset = offset
            }
    }
}
