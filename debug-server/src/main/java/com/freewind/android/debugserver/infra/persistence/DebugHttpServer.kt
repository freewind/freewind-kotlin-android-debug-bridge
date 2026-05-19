package com.freewind.android.debugserver.infra.persistence

import android.util.Log
import com.freewind.android.debugserver.domain.handler.DebugServerHandler
import com.freewind.android.debugserver.domain.models.DebugActionRequest
import com.freewind.android.debugserver.domain.models.DebugActionResult
import com.freewind.android.debugserver.domain.models.DebugBounds
import com.freewind.android.debugserver.domain.models.DebugNode
import com.freewind.android.debugserver.domain.models.DebugOperation
import com.freewind.android.debugserver.domain.models.DebugOperationSource
import com.freewind.android.debugserver.domain.models.DebugOperationsQuery
import com.freewind.android.debugserver.domain.models.DebugOperationsResult
import com.freewind.android.debugserver.domain.models.DebugSnapshot
import com.freewind.android.debugserver.domain.models.DebugSnapshotQuery
import com.freewind.android.debugserver.domain.store.DebugServerStore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

// 本地 HTTP server。
class DebugHttpServer(
    private val host: String,
    private val port: Int,
    private val store: DebugServerStore,
    private val handler: DebugServerHandler,
) {
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    fun start() {
        if (serverJob != null) {
            return
        }
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName(host))
                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    launch {
                        socket.use(::handleSocket)
                    }
                }
            } catch (throwable: Throwable) {
                Log.e("DebugHttpServer", "server stopped", throwable)
            }
        }
    }

    fun stop() {
        serverSocket?.close()
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        scope.cancel()
    }

    private fun handleSocket(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val firstLine = reader.readLine() ?: return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) {
                break
            }
            val separatorIndex = line.indexOf(':')
            if (separatorIndex > 0) {
                val key = line.substring(0, separatorIndex).trim().lowercase()
                val value = line.substring(separatorIndex + 1).trim()
                headers[key] = value
            }
        }

        val parts = firstLine.split(" ")
        val method = parts.getOrNull(0).orEmpty()
        val requestTarget = parts.getOrNull(1).orEmpty()
        val route = requestTarget.substringBefore("?")
        val queryParams = parseQueryParams(requestTarget.substringAfter("?", ""))
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = readBody(reader, contentLength)

        when {
            method == "OPTIONS" -> {
                writeText(socket.getOutputStream(), 200, "ok")
            }
            method == "GET" && route == "/" -> {
                writeHtml(
                    socket.getOutputStream(),
                    buildIndexHtml(
                        snapshot = store.snapshot().value,
                        operations = store.operations().value.takeLast(20).reversed(),
                    ),
                )
            }
            method == "GET" && route == "/snapshot" -> {
                val query = parseSnapshotQuery(queryParams, null)
                val snapshot = handler.querySnapshot(query)
                writeJson(socket.getOutputStream(), snapshot.toJsonString(query))
            }
            method == "POST" && route == "/snapshot/query" -> {
                val query = parseSnapshotQuery(queryParams, readJsonObject(body))
                val snapshot = handler.querySnapshot(query)
                writeJson(socket.getOutputStream(), snapshot.toJsonString(query))
            }
            method == "GET" && route == "/operations" -> {
                val query = parseOperationsQuery(queryParams)
                val result = handler.queryOperations(query)
                writeJson(socket.getOutputStream(), result.toJsonString(query))
            }
            method == "GET" && route == "/logs" -> {
                writeJson(socket.getOutputStream(), operationsLogToJsonString(store.operations().value))
            }
            method == "POST" && route == "/action" -> {
                val request = parseActionRequest(body)
                val result = runBlocking {
                    handler.performAction(request)
                }
                writeJson(socket.getOutputStream(), result.toJsonString())
            }
            else -> {
                writeText(socket.getOutputStream(), 404, "not found")
            }
        }
    }

    private fun writeHtml(outputStream: OutputStream, body: String) {
        writeResponse(outputStream, "text/html; charset=utf-8", 200, body)
    }

    private fun writeJson(outputStream: OutputStream, body: String) {
        writeResponse(outputStream, "application/json; charset=utf-8", 200, body)
    }

    private fun writeText(outputStream: OutputStream, statusCode: Int, body: String) {
        writeResponse(outputStream, "text/plain; charset=utf-8", statusCode, body)
    }

    private fun writeResponse(
        outputStream: OutputStream,
        contentType: String,
        statusCode: Int,
        body: String,
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        outputStream.write(
            buildString {
                append("HTTP/1.1 ")
                append(statusCode)
                append(
                    when (statusCode) {
                        200 -> " OK"
                        404 -> " Not Found"
                        else -> ""
                    },
                )
                append("\r\n")
                append("Content-Type: ")
                append(contentType)
                append("\r\n")
                append("Content-Length: ")
                append(bytes.size)
                append("\r\n")
                append("Connection: close\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Access-Control-Allow-Methods: GET,POST,OPTIONS\r\n")
                append("Access-Control-Allow-Headers: Content-Type\r\n")
                append("\r\n")
            }.toByteArray(Charsets.UTF_8),
        )
        outputStream.write(bytes)
        outputStream.flush()
    }

    private fun readBody(
        reader: BufferedReader,
        contentLength: Int,
    ): String {
        if (contentLength <= 0) {
            return ""
        }
        val buffer = CharArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val readCount = reader.read(buffer, offset, contentLength - offset)
            if (readCount <= 0) {
                break
            }
            offset += readCount
        }
        return String(buffer, 0, offset)
    }

    private fun parseQueryParams(rawQuery: String): Map<String, List<String>> {
        if (rawQuery.isBlank()) {
            return emptyMap()
        }
        return rawQuery.split("&")
            .filter { it.isNotBlank() }
            .groupBy(
                keySelector = { part ->
                    decodeUrl(part.substringBefore("="))
                },
                valueTransform = { part ->
                    decodeUrl(part.substringAfter("=", ""))
                },
            )
    }

    private fun parseSnapshotQuery(
        queryParams: Map<String, List<String>>,
        bodyJson: JSONObject?,
    ): DebugSnapshotQuery {
        val snapshotFields = readStringSet(queryParams, bodyJson, "snapshotFields")
        val nodeFields = readStringSet(queryParams, bodyJson, "nodeFields")
        val appStateKeys = readStringSet(queryParams, bodyJson, "appStateKeys")
        val nodeIds = readStringSet(queryParams, bodyJson, "nodeIds")
        return DebugSnapshotQuery(
            compact = readBoolean(queryParams, bodyJson, "compact") ?: true,
            snapshotFields = snapshotFields,
            nodeFields = nodeFields,
            appStateKeys = appStateKeys,
            nodeIds = nodeIds,
            includeAncestors = readBoolean(queryParams, bodyJson, "includeAncestors") ?: false,
            ancestorDepth = readInt(queryParams, bodyJson, "ancestorDepth"),
            descendantDepth = readInt(queryParams, bodyJson, "descendantDepth") ?: 0,
            visibleOnly = readBoolean(queryParams, bodyJson, "visibleOnly") ?: false,
            clickableOnly = readBoolean(queryParams, bodyJson, "clickableOnly") ?: false,
            types = readStringSet(queryParams, bodyJson, "types"),
            textQuery = readString(queryParams, bodyJson, "textQuery"),
            limit = readInt(queryParams, bodyJson, "limit"),
        )
    }

    private fun parseOperationsQuery(queryParams: Map<String, List<String>>): DebugOperationsQuery {
        val sources = queryParams["sources"]
            .orEmpty()
            .flatMap { value -> splitCsv(value) }
            .mapNotNull(DebugOperationSource::fromWireValue)
            .toSet()
        return DebugOperationsQuery(
            afterSeq = queryParams.singleValue("afterSeq")?.toLongOrNull(),
            limit = queryParams.singleValue("limit")?.toIntOrNull() ?: 20,
            consume = queryParams.singleValue("consume").toBooleanStrictOrFalse(),
            sources = sources,
            groupBySource = queryParams.singleValue("groupBySource").toBooleanStrictOrFalse(),
        )
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

    private fun readJsonObject(body: String): JSONObject? {
        if (body.isBlank()) {
            return null
        }
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun buildIndexHtml(
        snapshot: DebugSnapshot,
        operations: List<DebugOperation>,
    ): String {
        val prettySnapshot = JSONObject(snapshot.toJsonString(DebugSnapshotQuery())).toString(2)
        val prettyOperations = JSONObject(operationsLogToJsonString(operations.reversed())).toString(2)
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>${escapeHtml(snapshot.appName)}</title>
              <style>
                body { font-family: sans-serif; padding: 16px; background: #f7f7f7; }
                pre { white-space: pre-wrap; word-break: break-word; background: #fff; padding: 12px; border-radius: 8px; }
              </style>
            </head>
            <body>
              <h1>${escapeHtml(snapshot.appName)}</h1>
              <p>GET /snapshot、POST /snapshot/query、GET /operations、POST /action。</p>
              <pre id="snapshot">${escapeHtml(prettySnapshot)}</pre>
              <pre id="logs">${escapeHtml(prettyOperations)}</pre>
            </body>
            </html>
        """.trimIndent()
    }

    private fun readString(
        queryParams: Map<String, List<String>>,
        bodyJson: JSONObject?,
        key: String,
    ): String? {
        return queryParams.singleValue(key) ?: bodyJson.readNullableString(key)
    }

    private fun readBoolean(
        queryParams: Map<String, List<String>>,
        bodyJson: JSONObject?,
        key: String,
    ): Boolean? {
        return queryParams.singleValue(key)?.toBooleanStrictOrNull()
            ?: bodyJson?.takeIf { it.has(key) }?.optBoolean(key)
    }

    private fun readInt(
        queryParams: Map<String, List<String>>,
        bodyJson: JSONObject?,
        key: String,
    ): Int? {
        return queryParams.singleValue(key)?.toIntOrNull()
            ?: bodyJson?.takeIf { it.has(key) }?.optInt(key)
    }

    private fun readStringSet(
        queryParams: Map<String, List<String>>,
        bodyJson: JSONObject?,
        key: String,
    ): Set<String> {
        val queryValues = queryParams[key].orEmpty().flatMap(::splitCsv)
        if (queryValues.isNotEmpty()) {
            return queryValues.toSet()
        }
        val rawArray = bodyJson?.optJSONArray(key) ?: return emptySet()
        return buildSet {
            repeat(rawArray.length()) { index ->
                rawArray.optString(index)?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun Map<String, List<String>>.singleValue(key: String): String? {
        return get(key)?.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun splitCsv(value: String): List<String> {
        return value.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun decodeUrl(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }

    private fun String?.toBooleanStrictOrFalse(): Boolean {
        return this?.toBooleanStrictOrNull() ?: false
    }
}

private val allSnapshotFields = linkedSetOf(
    "appName",
    "screenName",
    "componentCount",
    "serverHost",
    "serverPort",
    "updatedAtEpochMs",
    "appState",
    "nodes",
)

private val compactSnapshotFields = linkedSetOf(
    "screenName",
    "updatedAtEpochMs",
    "nodes",
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

private fun DebugSnapshot.toJsonString(query: DebugSnapshotQuery): String {
    return toJsonObject(query).toString()
}

private fun DebugSnapshot.toJsonObject(query: DebugSnapshotQuery): JSONObject {
    val snapshotFields = query.resolveSnapshotFields()
    val nodeFields = query.resolveNodeFields()
    return JSONObject().apply {
        if ("appName" in snapshotFields) put("appName", appName)
        if ("screenName" in snapshotFields) put("screenName", screenName)
        if ("componentCount" in snapshotFields) put("componentCount", componentCount)
        if ("serverHost" in snapshotFields) put("serverHost", serverHost)
        if ("serverPort" in snapshotFields) put("serverPort", serverPort)
        if ("updatedAtEpochMs" in snapshotFields) put("updatedAtEpochMs", updatedAtEpochMs)
        if ("appState" in snapshotFields) put("appState", appState.toJsonObject())
        if ("nodes" in snapshotFields) {
            put(
                "nodes",
                JSONArray().apply {
                    nodes.forEach { node ->
                        put(node.toJsonObject(nodeFields))
                    }
                },
            )
        }
    }
}

private fun DebugSnapshotQuery.resolveSnapshotFields(): Set<String> {
    if (snapshotFields.isNotEmpty()) {
        return snapshotFields
    }
    if (!compact) {
        return allSnapshotFields
    }
    return linkedSetOf<String>().apply {
        add("screenName")
        add("updatedAtEpochMs")
        if (shouldIncludeAppStateByDefault()) {
            add("appState")
        }
        if (shouldIncludeNodesByDefault()) {
            add("nodes")
        }
    }
}

private fun DebugSnapshotQuery.resolveNodeFields(): Set<String> {
    if (nodeFields.isNotEmpty()) {
        return nodeFields
    }
    return if (compact) compactNodeFields else allNodeFields
}

private fun DebugSnapshotQuery.shouldIncludeAppStateByDefault(): Boolean {
    return appStateKeys.isNotEmpty()
}

private fun DebugSnapshotQuery.shouldIncludeNodesByDefault(): Boolean {
    return !shouldIncludeAppStateByDefault() || hasNodeFilter()
}

private fun DebugSnapshotQuery.hasNodeFilter(): Boolean {
    return nodeIds.isNotEmpty() ||
        includeAncestors ||
        ancestorDepth != null ||
        descendantDepth > 0 ||
        visibleOnly ||
        clickableOnly ||
        types.isNotEmpty() ||
        !textQuery.isNullOrBlank() ||
        limit != null
}

private fun Map<String, String>.toJsonObject(): JSONObject {
    return JSONObject().apply {
        entries.sortedBy { it.key }.forEach { (key, value) ->
            put(key, value)
        }
    }
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
        if ("extra" in nodeFields) put("extra", extra.toJsonObject())
        if ("bounds" in nodeFields) {
            put(
                "bounds",
                bounds?.toJsonObject() ?: JSONObject.NULL,
            )
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

private fun DebugActionResult.toJsonString(): String {
    return JSONObject()
        .put("ok", ok)
        .put("message", message)
        .toString()
}

private fun DebugOperationsResult.toJsonString(query: DebugOperationsQuery): String {
    return JSONObject().apply {
        if (query.groupBySource) {
            put("humanItems", items.filter { it.source == DebugOperationSource.HUMAN }.toJsonArray())
            put("aiItems", items.filter { it.source == DebugOperationSource.AI }.toJsonArray())
        } else {
            put("items", items.toJsonArray())
        }
        put("nextAfterSeq", nextAfterSeq)
        put("remainingCount", remainingCount)
    }.toString()
}

private fun List<DebugOperation>.toJsonArray(): JSONArray {
    return JSONArray().apply {
        forEach { operation ->
            put(operation.toJsonObject())
        }
    }
}

private fun DebugOperation.toJsonObject(): JSONObject {
    return JSONObject().apply {
        put("seq", seq)
        put("source", source.wireValue)
        put("action", action)
        putOrNull("targetId", targetId)
        putOrNull("targetParentId", targetParentId)
        putOrNull("targetType", targetType)
        putOrNull("targetText", targetText)
        put("screenName", screenName)
        putOrNull("text", text)
        putOrNull("dx", dx)
        putOrNull("dy", dy)
        putOrNull("success", success)
        putOrNull("message", message)
        put("extra", extra.toJsonObject())
        put("createdAtEpochMs", createdAtEpochMs)
    }
}

private fun operationsLogToJsonString(operations: List<DebugOperation>): String {
    return JSONObject().apply {
        put(
            "items",
            JSONArray().apply {
                operations.forEach { operation ->
                    put(operation.toLogLine())
                }
            },
        )
    }.toString()
}

private fun DebugOperation.toLogLine(): String {
    return buildString {
        append(seq)
        append(" | ")
        append(source.wireValue)
        append(" | ")
        append(action)
        targetId?.let {
            append(" target=")
            append(it)
        }
        success?.let {
            append(" ok=")
            append(it)
        }
        message?.takeIf { it.isNotBlank() }?.let {
            append(" msg=")
            append(it)
        }
    }
}

private fun JSONObject.putOrNull(
    key: String,
    value: Any?,
): JSONObject {
    return put(key, value ?: JSONObject.NULL)
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

private fun escapeHtml(value: String): String {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
