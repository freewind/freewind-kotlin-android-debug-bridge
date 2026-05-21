package com.freewind.android.debugbridge.demo.domain.model

data class DemoEvent(
    val id: Long,
    val label: String,
)

data class DemoUiState(
    val enabled: Boolean = true,
    val keyword: String = "",
    val status: String = "ready",
    val saveCount: Int = 0,
    val events: List<DemoEvent> = listOf(
        DemoEvent(1, "app booted"),
        DemoEvent(2, "debug bridge started"),
    ),
)
