package com.freewind.android.debugserver.demo.features.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.freewind.android.debugserver.DebugBridge
import com.freewind.android.debugserver.compose.DebugNodeRegistry
import com.freewind.android.debugserver.compose.RecordLazyListScroll
import com.freewind.android.debugserver.compose.debugNode
import com.freewind.android.debugserver.compose.publishComposeSnapshot
import com.freewind.android.debugserver.compose.registerComposeAction
import com.freewind.android.debugserver.demo.domain.handler.DemoHandler
import com.freewind.android.debugserver.demo.domain.store.DemoStore
import com.freewind.android.debugserver.domain.models.DebugActionResult
import com.freewind.android.debugserver.domain.models.DebugActionSpec

@Composable
fun MainScreen(
    debugBridge: DebugBridge,
    store: DemoStore,
    handler: DemoHandler,
) {
    val uiState by store.state().collectAsState()
    val registry = remember { DebugNodeRegistry() }
    val listState = rememberLazyListState()

    debugBridge.publishComposeSnapshot(
        registry = registry,
        screenName = "DemoScreen",
        appState = mapOf(
            "enabled" to uiState.enabled.toString(),
            "keyword" to uiState.keyword,
            "keywordLength" to uiState.keyword.length.toString(),
            "saveCount" to uiState.saveCount.toString(),
            "status" to uiState.status,
        ),
    )

    debugBridge.registerComposeAction(
        targetId = "save_button",
        registerKeys = arrayOf(uiState.keyword, uiState.enabled, uiState.saveCount),
        targetType = "Button",
        screenName = "DemoScreen",
        actions = listOf(
            DebugActionSpec(
                name = "click",
                summary = "trigger demo save flow",
            ),
        ),
    ) { request ->
        when (request.action) {
            "click" -> {
                debugBridge.log(
                    event = "action_handler_entered",
                    targetId = "save_button",
                    summary = "ai action entered save handler",
                )
                handler.onSaveClick()
                DebugActionResult(true, "accepted")
            }
            else -> DebugActionResult(false, "unsupported")
        }
    }

    debugBridge.registerComposeAction(
        targetId = "reset_button",
        registerKeys = arrayOf(uiState.saveCount, uiState.keyword),
        targetType = "Button",
        screenName = "DemoScreen",
        actions = listOf(
            DebugActionSpec(
                name = "click",
                summary = "reset demo state",
            ),
        ),
    ) { request ->
        when (request.action) {
            "click" -> {
                debugBridge.log(
                    event = "action_handler_entered",
                    targetId = "reset_button",
                    summary = "ai action entered reset handler",
                )
                handler.onResetClick()
                DebugActionResult(true, "accepted")
            }
            else -> DebugActionResult(false, "unsupported")
        }
    }

    SideEffect {
        debugBridge.publishTargetState(
            targetId = "enabled_switch",
            state = mapOf(
                "checked" to uiState.enabled.toString(),
                "label" to "Enable feature",
            ),
        )
        debugBridge.publishTargetState(
            targetId = "keyword_input",
            state = mapOf(
                "value" to uiState.keyword,
                "length" to uiState.keyword.length.toString(),
            ),
        )
        debugBridge.publishTargetState(
            targetId = "save_button",
            state = mapOf(
                "label" to "Save",
                "saveCount" to uiState.saveCount.toString(),
            ),
        )
        debugBridge.publishTargetState(
            targetId = "reset_button",
            state = mapOf(
                "label" to "Reset",
                "status" to uiState.status,
            ),
        )
    }

    debugBridge.RecordLazyListScroll(
        targetId = "event_list",
        state = listState,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .debugNode(
                registry = registry,
                id = "screen_root",
                type = "Column",
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Debug Server Demo",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.debugNode(
                registry = registry,
                id = "title_text",
                parentId = "screen_root",
                type = "Text",
                text = "Debug Server Demo",
            ),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .debugNode(
                    registry = registry,
                    id = "control_card",
                    parentId = "screen_root",
                    type = "Card",
                ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .debugNode(
                            registry = registry,
                            id = "switch_row",
                            parentId = "control_card",
                            type = "Row",
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Enable feature")
                    Switch(
                        checked = uiState.enabled,
                        onCheckedChange = { checked ->
                            debugBridge.log(
                                event = "toggle",
                                targetId = "enabled_switch",
                                summary = "human toggled enabled",
                                data = mapOf(
                                    "checked" to checked.toString(),
                                ),
                            )
                            handler.onEnabledChange(checked)
                        },
                        modifier = Modifier.debugNode(
                            registry = registry,
                            id = "enabled_switch",
                            parentId = "switch_row",
                            type = "Switch",
                            role = "switch",
                            enabled = true,
                            clickable = true,
                            value = uiState.enabled.toString(),
                        ),
                    )
                }

                OutlinedTextField(
                    value = uiState.keyword,
                    onValueChange = { value ->
                        debugBridge.log(
                            event = "input",
                            targetId = "keyword_input",
                            summary = "human changed keyword",
                            data = mapOf(
                                "value" to value,
                                "length" to value.length.toString(),
                            ),
                        )
                        handler.onKeywordChange(value)
                    },
                    label = { Text("Keyword") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .debugNode(
                            registry = registry,
                            id = "keyword_input",
                            parentId = "control_card",
                            type = "TextField",
                            text = uiState.keyword,
                            role = "input",
                            enabled = true,
                            clickable = true,
                            value = uiState.keyword,
                        ),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .debugNode(
                            registry = registry,
                            id = "button_row",
                            parentId = "control_card",
                            type = "Row",
                        ),
                ) {
                    Button(
                        onClick = {
                            debugBridge.log(
                                event = "click",
                                targetId = "save_button",
                                summary = "human tapped save",
                                data = mapOf(
                                    "keyword" to uiState.keyword,
                                    "enabled" to uiState.enabled.toString(),
                                ),
                            )
                            handler.onSaveClick()
                        },
                        modifier = Modifier.debugNode(
                            registry = registry,
                            id = "save_button",
                            parentId = "button_row",
                            type = "Button",
                            text = "Save",
                            role = "button",
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            clickable = true,
                        ),
                    ) {
                        Text("Save")
                    }

                    Button(
                        onClick = {
                            debugBridge.log(
                                event = "click",
                                targetId = "reset_button",
                                summary = "human tapped reset",
                            )
                            handler.onResetClick()
                        },
                        modifier = Modifier.debugNode(
                            registry = registry,
                            id = "reset_button",
                            parentId = "button_row",
                            type = "Button",
                            text = "Reset",
                            role = "button",
                            clickable = true,
                        ),
                    ) {
                        Text("Reset")
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .debugNode(
                    registry = registry,
                    id = "status_card",
                    parentId = "screen_root",
                    type = "Card",
                ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Status: ${uiState.status}",
                    modifier = Modifier.debugNode(
                        registry = registry,
                        id = "status_text",
                        parentId = "status_card",
                        type = "Text",
                        text = uiState.status,
                    ),
                )
                Text(
                    text = "Save count: ${uiState.saveCount}",
                    modifier = Modifier.debugNode(
                        registry = registry,
                        id = "save_count_text",
                        parentId = "status_card",
                        type = "Text",
                        text = uiState.saveCount.toString(),
                    ),
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .debugNode(
                    registry = registry,
                    id = "event_card",
                    parentId = "screen_root",
                    type = "Card",
                ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .debugNode(
                        registry = registry,
                        id = "event_list",
                        parentId = "event_card",
                        type = "LazyColumn",
                        role = "list",
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = uiState.events,
                    key = { it.id },
                ) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .debugNode(
                                registry = registry,
                                id = "event_item_${item.id}",
                                parentId = "event_list",
                                type = "Card",
                                text = item.label,
                                extra = mapOf(
                                    "eventId" to item.id.toString(),
                                ),
                            ),
                    ) {
                        Text(
                            text = item.label,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}
