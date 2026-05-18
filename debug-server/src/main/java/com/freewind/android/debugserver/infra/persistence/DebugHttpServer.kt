package com.freewind.android.debugserver.infra.persistence

import android.util.Log
import com.freewind.android.debugserver.domain.handler.DebugServerHandler
import com.freewind.android.debugserver.domain.models.DebugActionRequest
import com.freewind.android.debugserver.domain.models.DebugActionResult
import com.freewind.android.debugserver.domain.models.DebugNode
import com.freewind.android.debugserver.domain.models.DebugSnapshot
import com.freewind.android.debugserver.domain.store.DebugServerStore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
        val path = parts.getOrNull(1).orEmpty()
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            CharArray(contentLength).also { reader.read(it) }.concatToString()
        } else {
            ""
        }

        when {
            method == "GET" && path == "/" -> {
                writeHtml(socket.getOutputStream(), buildIndexHtml(store.snapshot(), store.actionLog()))
            }
            method == "GET" && path == "/snapshot" -> {
                writeJson(socket.getOutputStream(), store.snapshot().value.toJson())
            }
            method == "GET" && path == "/logs" -> {
                writeJson(socket.getOutputStream(), actionLogToJson(store.actionLog().value))
            }
            method == "POST" && path == "/action" -> {
                val request = parseActionRequest(body)
                val result = runBlocking {
                    handler.performAction(request)
                }
                writeJson(socket.getOutputStream(), result.toJson())
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
                append("\r\n")
            }.toByteArray(Charsets.UTF_8),
        )
        outputStream.write(bytes)
        outputStream.flush()
    }

    private fun buildIndexHtml(
        snapshotFlow: StateFlow<DebugSnapshot>,
        actionLogFlow: StateFlow<List<String>>,
    ): String {
        val snapshot = snapshotFlow.value
        val logs = actionLogFlow.value
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>${snapshot.appName}</title>
              <style>
                body { font-family: sans-serif; padding: 16px; background: #f7f7f7; }
                pre { white-space: pre-wrap; word-break: break-word; background: #fff; padding: 12px; border-radius: 8px; }
              </style>
            </head>
            <body>
              <h1>${escapeHtml(snapshot.appName)}</h1>
              <p>GET /snapshot 读快照，POST /action 发动作。</p>
              <pre id="snapshot">${escapeHtml(snapshot.toJson())}</pre>
              <pre id="logs">${escapeHtml(actionLogToJson(logs))}</pre>
            </body>
            </html>
        """.trimIndent()
    }

    private fun parseActionRequest(body: String): DebugActionRequest {
        fun readString(key: String): String? {
            val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            return pattern.find(body)?.groupValues?.get(1)
        }

        fun readFloat(key: String): Float? {
            val pattern = Regex("\"$key\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
            return pattern.find(body)?.groupValues?.get(1)?.toFloatOrNull()
        }

        return DebugActionRequest(
            action = readString("action").orEmpty(),
            targetId = readString("targetId"),
            text = readString("text"),
            dx = readFloat("dx"),
            dy = readFloat("dy"),
        )
    }

    private fun actionLogToJson(lines: List<String>): String {
        return buildString {
            append("{\"items\":[")
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    append(",")
                }
                append("\"")
                append(line.escapeJson())
                append("\"")
            }
            append("]}")
        }
    }
}

private fun DebugSnapshot.toJson(): String {
    return buildString {
        append("{")
        append("\"appName\":\"")
        append(appName.escapeJson())
        append("\",")
        append("\"screenName\":\"")
        append(screenName.escapeJson())
        append("\",")
        append("\"componentCount\":")
        append(componentCount)
        append(",")
        append("\"serverHost\":\"")
        append(serverHost.escapeJson())
        append("\",")
        append("\"serverPort\":")
        append(serverPort)
        append(",")
        append("\"updatedAtEpochMs\":")
        append(updatedAtEpochMs)
        append(",")
        append("\"appState\":{")
        appState.entries.forEachIndexed { index, entry ->
            if (index > 0) {
                append(",")
            }
            append("\"")
            append(entry.key.escapeJson())
            append("\":\"")
            append(entry.value.escapeJson())
            append("\"")
        }
        append("},")
        append("\"nodes\":[")
        nodes.forEachIndexed { index, node ->
            if (index > 0) {
                append(",")
            }
            append(node.toJson())
        }
        append("]}")
    }
}

private fun DebugNode.toJson(): String {
    return buildString {
        append("{")
        append("\"id\":\"")
        append(id.escapeJson())
        append("\",")
        append("\"type\":\"")
        append(type.escapeJson())
        append("\",")
        append("\"text\":")
        appendNullableString(text)
        append(",")
        append("\"role\":")
        appendNullableString(role)
        append(",")
        append("\"backgroundColor\":")
        appendNullableString(backgroundColor)
        append(",")
        append("\"contentColor\":")
        appendNullableString(contentColor)
        append(",")
        append("\"visible\":")
        append(visible)
        append(",")
        append("\"enabled\":")
        append(enabled)
        append(",")
        append("\"clickable\":")
        append(clickable)
        append(",")
        append("\"value\":")
        appendNullableString(value)
        append(",")
        append("\"extra\":{")
        extra.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
            if (index > 0) {
                append(",")
            }
            append("\"")
            append(entry.key.escapeJson())
            append("\":\"")
            append(entry.value.escapeJson())
            append("\"")
        }
        append("},")
        append("\"bounds\":")
        if (bounds == null) {
            append("null")
        } else {
            append("{")
            append("\"left\":")
            append(bounds.left)
            append(",")
            append("\"top\":")
            append(bounds.top)
            append(",")
            append("\"width\":")
            append(bounds.width)
            append(",")
            append("\"height\":")
            append(bounds.height)
            append("}")
        }
        append("}")
    }
}

private fun DebugActionResult.toJson(): String {
    return """{"ok":$ok,"message":"${message.escapeJson()}"}"""
}

private fun String.escapeJson(): String {
    return replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
}

private fun escapeHtml(value: String): String {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

private fun StringBuilder.appendNullableString(value: String?) {
    if (value == null) {
        append("null")
    } else {
        append("\"")
        append(value.escapeJson())
        append("\"")
    }
}
