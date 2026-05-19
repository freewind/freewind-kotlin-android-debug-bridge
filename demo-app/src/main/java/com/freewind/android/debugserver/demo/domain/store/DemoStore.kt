package com.freewind.android.debugserver.demo.domain.store

import com.freewind.android.debugserver.demo.domain.model.DemoEvent
import com.freewind.android.debugserver.demo.domain.model.DemoUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DemoStore {
    private val state = MutableStateFlow(DemoUiState())
    private var nextEventId = 3L

    fun state(): StateFlow<DemoUiState> = state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        val current = state.value
        state.value = current.copy(
            enabled = enabled,
            status = if (enabled) "feature enabled" else "feature disabled",
        )
        appendEvent("enabled -> $enabled")
    }

    fun setKeyword(keyword: String) {
        val current = state.value
        state.value = current.copy(
            keyword = keyword,
            status = "keyword length ${keyword.length}",
        )
        appendEvent("keyword -> $keyword")
    }

    fun save() {
        val current = state.value
        val nextSaveCount = current.saveCount + 1
        state.value = current.copy(
            saveCount = nextSaveCount,
            status = "saved #$nextSaveCount",
        )
        appendEvent("save clicked #$nextSaveCount")
    }

    fun reset() {
        state.value = DemoUiState(
            enabled = true,
            keyword = "",
            status = "reset done",
            saveCount = 0,
            events = listOf(
                DemoEvent(nextEventId++, "reset done"),
            ),
        )
    }

    private fun appendEvent(label: String) {
        val event = DemoEvent(
            id = nextEventId,
            label = label,
        )
        nextEventId += 1
        state.value = state.value.copy(
            events = (listOf(event) + state.value.events).take(30),
        )
    }
}
