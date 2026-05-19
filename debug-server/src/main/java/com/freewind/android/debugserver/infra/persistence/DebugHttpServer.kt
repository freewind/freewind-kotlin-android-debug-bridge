package com.freewind.android.debugserver.infra.persistence

import com.freewind.android.debugserver.domain.handler.DebugServerHandler
import com.freewind.android.debugserver.domain.models.DebugActionRequest
import com.freewind.android.debugserver.domain.models.DebugActionResult
import com.freewind.android.debugserver.domain.models.DebugActionSpec
import com.freewind.android.debugserver.domain.models.DebugActionTarget
import com.freewind.android.debugserver.domain.models.DebugBounds
import com.freewind.android.debugserver.domain.models.DebugNode
import com.freewind.android.debugserver.domain.models.DebugOperation
import com.freewind.android.debugserver.domain.models.DebugOperationSource
import com.freewind.android.debugserver.domain.models.DebugSnapshot
import com.freewind.android.debugserver.domain.models.DebugSnapshotQuery
import com.freewind.android.debugserver.domain.store.DebugServerStore
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

// 本地 HTTP server。
class DebugHttpServer(
    private val host: String,
    private val port: Int,
    private val store: DebugServerStore,
    private val handler: DebugServerHandler,
) {
    private var server: ApplicationEngine? = null

    fun start() {
        if (server != null) {
            return
        }
        server = embeddedServer(CIO, host = host, port = port) {
            installRoutes()
        }.also {
            it.start(wait = false)
        }
    }

    fun stop() {
        server?.stop(100, 1_000)
        server = null
    }

    private fun Application.installRoutes() {
        routing {
            get("/") {
                call.respondText(
                    DebugWebAssets.indexHtml(),
                    ContentType.Text.Html,
                )
            }
            get("/app.js") {
                call.respondText(
                    DebugWebAssets.appJs(),
                    ContentType.Application.JavaScript,
                )
            }
            get("/app.css") {
                call.respondText(
                    DebugWebAssets.appCss(),
                    ContentType.Text.CSS,
                )
            }
            get("/help") {
                call.respondText(
                    buildHelpJson(),
                    ContentType.Application.Json,
                )
            }
            get("/action") {
                call.respondText(
                    buildActionJson(call.request.queryParameters),
                    ContentType.Application.Json,
                )
            }
            post("/action") {
                val request = parseActionRequest(call.receiveText())
                val result = handler.performAction(request)
                call.respondText(
                    result.toHttpJsonString(request),
                    ContentType.Application.Json,
                )
            }
            get("/logs") {
                val queryParams = call.request.queryParameters
                call.respondText(
                    if (queryParams.hasEntries()) buildLogsQueryJson(queryParams) else buildLogsSummaryJson(),
                    ContentType.Application.Json,
                )
            }
            delete("/logs") {
                val deletedCount = store.operations().value.size
                store.clearOperations()
                call.respondText(
                    JSONObject()
                        .put("ok", true)
                        .put("deletedCount", deletedCount)
                        .toString(),
                    ContentType.Application.Json,
                )
            }
            get("/state") {
                val queryParams = call.request.queryParameters
                call.respondText(
                    if (queryParams.hasEntries()) buildStateQueryJson(queryParams) else buildStateSummaryJson(),
                    ContentType.Application.Json,
                )
            }
            get("/snapshot") {
                val queryParams = call.request.queryParameters
                call.respondText(
                    if (queryParams.hasEntries()) buildSnapshotQueryJson(queryParams) else buildSnapshotSummaryJson(),
                    ContentType.Application.Json,
                )
            }
        }
    }

    private fun buildHelpJson(): String {
        val snapshot = store.snapshot().value
        val actionTargets = buildActionTargets()
        val operations = store.operations().value
        return JSONObject().apply {
            put("appName", snapshot.appName)
            put("screenName", snapshot.screenName)
            put("serverTime", nowAsWireTime())
            put(
                "capabilities",
                JSONArray().apply {
                    listOf("action", "logs", "state", "snapshot").forEach(::put)
                },
            )
            put(
                "counts",
                JSONObject().apply {
                    put("actionTargetCount", actionTargets.size)
                    put("logCount", operations.size)
                    put("stateKeyCount", snapshot.appState.size)
                    put("snapshotNodeCount", snapshot.nodes.size)
                },
            )
            put(
                "endpoints",
                JSONArray().apply {
                    put(
                        endpointJson(
                            method = "GET",
                            path = "/",
                            summary = "open human-readable debug console",
                        ),
                    )
                    put(
                        endpointJson(
                            method = "GET",
                            path = "/help",
                            summary = "return dynamic full help for AI",
                        ),
                    )
                    put(
                        endpointJson(
                            method = "GET",
                            path = "/action",
                            summary = "show executable targets and actions",
                            queryFields = listOf("targetId", "action", "screen"),
                        ),
                    )
                    put(
                        endpointJson(
                            method = "POST",
                            path = "/action",
                            summary = "trigger one concrete action",
                            bodyFields = listOf("action", "targetId", "text", "dx", "dy"),
                        ),
                    )
                    put(
                        endpointJson(
                            method = "GET",
                            path = "/logs",
                            summary = "show log summary or query matching logs",
                            queryFields = listOf("event", "level", "source", "targetId", "screen", "from", "to", "limit", "keyword"),
                        ),
                    )
                    put(
                        endpointJson(
                            method = "DELETE",
                            path = "/logs",
                            summary = "delete all existing logs",
                        ),
                    )
                    put(
                        endpointJson(
                            method = "GET",
                            path = "/state",
                            summary = "show state summary or query state values",
                            queryFields = listOf("keys", "targetId", "scope"),
                        ),
                    )
                    put(
                        endpointJson(
                            method = "GET",
                            path = "/snapshot",
                            summary = "show tree summary or query node snapshot",
                            queryFields = listOf("targetId", "scope", "depth", "types", "textKeyword", "fields", "limit"),
                        ),
                    )
                },
            )
            put(
                "examples",
                JSONArray().apply {
                    listOf(
                        "GET /help",
                        "GET /logs",
                        "GET /snapshot?targetId=save_button&scope=branchToRoot&fields=id,type,text,bounds",
                        "POST /action {\"action\":\"click\",\"targetId\":\"save_button\"}",
                    ).forEach(::put)
                },
            )
        }.toString()
    }

    private fun endpointJson(
        method: String,
        path: String,
        summary: String,
        queryFields: List<String> = emptyList(),
        bodyFields: List<String> = emptyList(),
    ): JSONObject {
        return JSONObject().apply {
            put("method", method)
            put("path", path)
            put("summary", summary)
            if (queryFields.isNotEmpty()) {
                put("queryFields", JSONArray().apply { queryFields.forEach(::put) })
            }
            if (bodyFields.isNotEmpty()) {
                put("bodyFields", JSONArray().apply { bodyFields.forEach(::put) })
            }
        }
    }

    private fun buildActionJson(queryParams: Parameters): String {
        val targetId = queryParams.singleValue("targetId")
        val actionName = queryParams.singleValue("action")
        val screen = queryParams.singleValue("screen")
        val items = buildActionTargets()
            .mapNotNull { target ->
                val filteredActions = target.actions.filter { action ->
                    actionName == null || action.name == actionName
                }
                val normalizedTarget = target.copy(actions = filteredActions)
                normalizedTarget.takeIf {
                    (targetId == null || normalizedTarget.targetId == targetId) &&
                        (screen == null || normalizedTarget.screenName == screen) &&
                        normalizedTarget.actions.isNotEmpty()
                }
            }
        return JSONObject().apply {
            put(
                "summary",
                JSONObject().apply {
                    put("targetCount", items.size)
                    put("actionCount", items.sumOf { it.actions.size })
                },
            )
            put(
                "items",
                JSONArray().apply {
                    items.forEach { target ->
                        put(target.toJsonObject())
                    }
                },
            )
        }.toString()
    }

    private fun buildLogsSummaryJson(): String {
        val operations = store.operations().value
        val levelCounts = operations.groupingBy(::operationLevel).eachCount()
        val sourceCounts = operations.groupingBy { it.source.wireValue }.eachCount()
        val eventCountsTop = operations.groupingBy { it.action }.eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
        return JSONObject().apply {
            put(
                "summary",
                JSONObject().apply {
                    put("total", operations.size)
                    put(
                        "timeRange",
                        JSONObject().apply {
                            putOrNull("from", operations.minOfOrNull { formatEpochMs(it.createdAtEpochMs) })
                            putOrNull("to", operations.maxOfOrNull { formatEpochMs(it.createdAtEpochMs) })
                        },
                    )
                    put(
                        "levelCounts",
                        levelCounts.toIntMapJsonObject(),
                    )
                    put(
                        "sourceCounts",
                        sourceCounts.toIntMapJsonObject(),
                    )
                    put(
                        "eventCountsTop",
                        JSONObject().apply {
                            eventCountsTop.forEach { (key, value) ->
                                put(key, value)
                            }
                        },
                    )
                },
            )
        }.toString()
    }

    private fun buildLogsQueryJson(queryParams: Parameters): String {
        val event = queryParams.singleValue("event")
        val level = queryParams.singleValue("level")
        val source = queryParams.singleValue("source")
        val targetId = queryParams.singleValue("targetId")
        val screen = queryParams.singleValue("screen")
        val keyword = queryParams.singleValue("keyword")
        val afterSeq = queryParams.singleValue("afterSeq")?.toLongOrNull()
        val fromEpochMs = queryParams.singleValue("from")?.let(::parseWireTime)
        val toEpochMs = queryParams.singleValue("to")?.let(::parseWireTime)
        val limit = queryParams.singleValue("limit")?.toIntOrNull() ?: 20
        val filtered = store.operations().value
            .asSequence()
            .filter { afterSeq == null || it.seq > afterSeq }
            .filter { event == null || it.action == event }
            .filter { level == null || operationLevel(it) == level }
            .filter { source == null || it.source.wireValue == source }
            .filter { targetId == null || it.targetId == targetId }
            .filter { screen == null || it.screenName == screen }
            .filter { fromEpochMs == null || it.createdAtEpochMs >= fromEpochMs }
            .filter { toEpochMs == null || it.createdAtEpochMs <= toEpochMs }
            .filter { keyword == null || it.matchesKeyword(keyword) }
            .take(limit.coerceAtLeast(0))
            .toList()
        return JSONObject().apply {
            put(
                "items",
                JSONArray().apply {
                    filtered.forEach { operation ->
                        put(operation.toLogQueryJson())
                    }
                },
            )
            put("nextAfterSeq", filtered.lastOrNull()?.seq ?: (afterSeq ?: 0L))
        }.toString()
    }

    private fun buildStateSummaryJson(): String {
        val snapshot = store.snapshot().value
        val targetStates = store.targetStates().value
        return JSONObject().apply {
            put(
                "summary",
                JSONObject().apply {
                    put(
                        "appStateKeys",
                        JSONArray().apply {
                            snapshot.appState.entries.sortedBy { it.key }.forEach { (key, value) ->
                                put(
                                    JSONObject().apply {
                                        put("key", key)
                                        put("sample", value)
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "targetStateTargets",
                        JSONArray().apply {
                            targetStates.keys.sorted().forEach(::put)
                        },
                    )
                },
            )
        }.toString()
    }

    private fun buildStateQueryJson(queryParams: Parameters): String {
        val keys = queryParams.csvValues("keys").toSet()
        val targetId = queryParams.singleValue("targetId")
        val scope = queryParams.singleValue("scope") ?: "app"
        val appState = store.snapshot().value.appState
            .let { state -> if (keys.isEmpty()) state else state.filterKeys { it in keys } }
        val targetState = targetId?.let { store.targetStates().value[it].orEmpty() }.orEmpty()
        return JSONObject().apply {
            if (scope == "app" || scope == "branch") {
                put("appState", appState.toStringMapJsonObject())
            }
            if (scope == "target" || scope == "branch") {
                put("targetState", targetState.toStringMapJsonObject())
            }
        }.toString()
    }

    private fun buildSnapshotSummaryJson(): String {
        val snapshot = store.snapshot().value
        val typeCounts = snapshot.nodes.groupingBy { it.type }.eachCount()
        return JSONObject().apply {
            put(
                "summary",
                JSONObject().apply {
                    put("screen", snapshot.screenName)
                    put("nodeCount", snapshot.nodes.size)
                    put(
                        "rootIds",
                        JSONArray().apply {
                            snapshot.nodes.filter { it.parentId == null }.map { it.id }.sorted().forEach(::put)
                        },
                    )
                    put(
                        "typeCounts",
                        typeCounts.toIntMapJsonObject(),
                    )
                    put("clickableCount", snapshot.nodes.count { it.clickable })
                },
            )
            put(
                "fieldCatalog",
                JSONArray().apply {
                    allNodeFields.forEach(::put)
                },
            )
            put(
                "examples",
                JSONArray().apply {
                    listOf(
                        "/snapshot?targetId=save_button&scope=self",
                        "/snapshot?targetId=save_button&scope=branchToRoot&fields=id,type,text,bounds",
                        "/snapshot?types=Button&clickable=true&limit=20",
                    ).forEach(::put)
                },
            )
        }.toString()
    }

    private fun buildSnapshotQueryJson(queryParams: Parameters): String {
        val query = parseSnapshotQuery(queryParams)
        val snapshot = handler.querySnapshot(query)
        val nodeFields = queryParams.csvValues("fields").toSet().ifEmpty { compactNodeFields }
        val enabledFilter = queryParams.singleValue("enabled")?.toBooleanStrictOrNull()
        val nodes = snapshot.nodes
            .asSequence()
            .filter { enabledFilter == null || it.enabled == enabledFilter }
            .toList()
        return JSONObject().apply {
            put("screen", snapshot.screenName)
            put(
                "nodes",
                JSONArray().apply {
                    nodes.forEach { node ->
                        put(node.toJsonObject(nodeFields))
                    }
                },
            )
        }.toString()
    }

    private fun buildActionTargets(): List<DebugActionTarget> {
        val snapshot = store.snapshot().value
        val nodeById = snapshot.nodes.associateBy { it.id }
        return handler.actionTargets()
            .map { target ->
                val node = nodeById[target.targetId]
                target.copy(
                    targetType = target.targetType ?: node?.type,
                    screenName = target.screenName ?: snapshot.screenName,
                )
            }
            .sortedBy { it.targetId }
    }

    private fun parseActionRequest(body: String): DebugActionRequest {
        val json = readJsonObject(body)
        return DebugActionRequest(
            action = json?.optString("action").orEmpty(),
            targetId = json.readNullableString("targetId"),
            text = json.readNullableString("text"),
            dx = json.readNullableDouble("dx")?.toFloat(),
            dy = json.readNullableDouble("dy")?.toFloat(),
        )
    }

    private fun parseSnapshotQuery(queryParams: Parameters): DebugSnapshotQuery {
        val targetId = queryParams.singleValue("targetId")
        val scope = queryParams.singleValue("scope")
        val depth = queryParams.singleValue("depth")?.toIntOrNull()
        val descendantDepth = when (scope) {
            "children" -> depth ?: 1
            "subtree" -> depth ?: 32
            else -> 0
        }
        val includeAncestors = scope == "parent" || scope == "ancestors" || scope == "branchToRoot"
        val ancestorDepth = when (scope) {
            "parent" -> 1
            "ancestors", "branchToRoot" -> depth
            else -> null
        }
        val hasNodeScope = scope != null && scope != "all"
        return DebugSnapshotQuery(
            compact = true,
            nodeFields = queryParams.csvValues("fields").toSet(),
            nodeIds = targetId?.takeIf { hasNodeScope }?.let(::setOf).orEmpty(),
            includeAncestors = includeAncestors,
            ancestorDepth = ancestorDepth,
            descendantDepth = descendantDepth,
            visibleOnly = queryParams.singleValue("visible")?.toBooleanStrictOrNull() ?: false,
            clickableOnly = queryParams.singleValue("clickable")?.toBooleanStrictOrNull() ?: false,
            types = queryParams.csvValues("types").toSet(),
            textQuery = queryParams.singleValue("textKeyword"),
            limit = queryParams.singleValue("limit")?.toIntOrNull(),
        )
    }

    private fun Parameters.hasEntries(): Boolean {
        return names().isNotEmpty()
    }

    private fun Parameters.singleValue(key: String): String? {
        return get(key)?.takeIf { it.isNotBlank() }
    }

    private fun Parameters.csvValues(key: String): List<String> {
        return getAll(key).orEmpty()
            .flatMap(::splitCsv)
    }

    private fun splitCsv(value: String): List<String> {
        return value.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun operationLevel(operation: DebugOperation): String {
        return operation.extra["level"]
            ?: when {
                operation.success == false -> "warn"
                else -> "info"
            }
    }

    private fun DebugOperation.matchesKeyword(keyword: String): Boolean {
        return buildString {
            append(action)
            append(' ')
            append(targetId.orEmpty())
            append(' ')
            append(targetText.orEmpty())
            append(' ')
            append(message.orEmpty())
            append(' ')
            append(text.orEmpty())
            append(' ')
            append(extra.entries.joinToString(" ") { "${it.key}=${it.value}" })
        }.contains(keyword, ignoreCase = true)
    }

    private fun DebugActionTarget.toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("targetId", targetId)
            putOrNull("targetType", targetType)
            putOrNull("screen", screenName)
            put(
                "actions",
                JSONArray().apply {
                    actions.forEach { action ->
                        put(action.toJsonObject(targetId))
                    }
                },
            )
        }
    }

    private fun DebugActionSpec.toJsonObject(targetId: String): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put(
                "args",
                JSONArray().apply {
                    args.forEach(::put)
                },
            )
            putOrNull("summary", summary)
            put(
                "example",
                JSONObject().apply {
                    put("action", name)
                    put("targetId", targetId)
                },
            )
        }
    }

    private fun DebugOperation.toLogQueryJson(): JSONObject {
        return JSONObject().apply {
            put("seq", seq)
            put("time", formatEpochMs(createdAtEpochMs))
            put("source", source.wireValue)
            put("level", operationLevel(this@toLogQueryJson))
            put("event", action)
            putOrNull("targetId", targetId)
            putOrNull("summary", message)
            put(
                "data",
                JSONObject().apply {
                    extra.entries.sortedBy { it.key }.forEach { (key, value) ->
                        put(key, value)
                    }
                    putOrNull("targetType", targetType)
                    putOrNull("targetText", targetText)
                    putOrNull("text", text)
                    putOrNull("accepted", success?.toString())
                },
            )
        }
    }

    private fun DebugActionResult.toHttpJsonString(request: DebugActionRequest): String {
        return JSONObject().apply {
            put("accepted", ok)
            put("message", message)
            put("action", request.action)
            putOrNull("targetId", request.targetId)
        }.toString()
    }

    private fun DebugNode.toJsonObject(nodeFields: Set<String>): JSONObject {
        return JSONObject().apply {
            if ("id" in nodeFields) put("id", id)
            if ("parentId" in nodeFields) putOrNull("parentId", parentId)
            if ("type" in nodeFields) put("type", type)
            if ("text" in nodeFields) putOrNull("text", text)
            if ("role" in nodeFields) putOrNull("role", role)
            if ("backgroundColor" in nodeFields) putOrNull("backgroundColor", backgroundColor)
            if ("contentColor" in nodeFields) putOrNull("contentColor", contentColor)
            if ("visible" in nodeFields) put("visible", visible)
            if ("enabled" in nodeFields) put("enabled", enabled)
            if ("clickable" in nodeFields) put("clickable", clickable)
            if ("value" in nodeFields) putOrNull("value", value)
            if ("extra" in nodeFields) put("extra", extra.toStringMapJsonObject())
            if ("bounds" in nodeFields) {
                put("bounds", bounds?.toJsonObject() ?: JSONObject.NULL)
            }
        }
    }

    private fun DebugBounds.toJsonObject(): JSONObject {
        return JSONObject()
            .put("left", left)
            .put("top", top)
            .put("width", width)
            .put("height", height)
    }

    private fun Map<String, Int>.toIntMapJsonObject(): JSONObject {
        return JSONObject().apply {
            entries.sortedBy { it.key }.forEach { (key, value) ->
                put(key, value)
            }
        }
    }

    private fun Map<String, String>.toStringMapJsonObject(): JSONObject {
        return JSONObject().apply {
            entries.sortedBy { it.key }.forEach { (key, value) ->
                put(key, value)
            }
        }
    }

    private fun JSONObject?.readNullableString(key: String): String? {
        if (this == null || !has(key) || isNull(key)) {
            return null
        }
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject?.readNullableDouble(key: String): Double? {
        if (this == null || !has(key) || isNull(key)) {
            return null
        }
        return optDouble(key)
    }

    private fun readJsonObject(body: String): JSONObject? {
        if (body.isBlank()) {
            return null
        }
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun JSONObject.putOrNull(
        key: String,
        value: Any?,
    ): JSONObject {
        return put(key, value ?: JSONObject.NULL)
    }

    private fun nowAsWireTime(): String {
        return formatEpochMs(System.currentTimeMillis())
    }

    private fun formatEpochMs(epochMs: Long): String {
        return wireTimeFormatter.format(
            Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()),
        )
    }

    private fun parseWireTime(value: String): Long? {
        return runCatching {
            LocalDateTime.parse(value, wireTimeFormatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

}

private val compactNodeFields = linkedSetOf(
    "id",
    "parentId",
    "type",
    "text",
    "role",
    "visible",
    "enabled",
    "clickable",
    "value",
    "bounds",
)

private val allNodeFields = linkedSetOf(
    "id",
    "parentId",
    "type",
    "text",
    "role",
    "backgroundColor",
    "contentColor",
    "visible",
    "enabled",
    "clickable",
    "value",
    "extra",
    "bounds",
)

private val wireTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
