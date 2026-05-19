import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const webRoot = path.resolve(__dirname, '..')
const distRoot = path.join(webRoot, 'dist')
const outputPath = path.resolve(
  webRoot,
  '../debug-server/src/main/java/com/freewind/android/debugserver/infra/persistence/DebugWebAssets.kt',
)

const chunkSize = 16000

const readText = async (name) => readFile(path.join(distRoot, name), 'utf8')

const toChunks = (value) => {
  const chunks = []
  for (let index = 0; index < value.length; index += chunkSize) {
    chunks.push(value.slice(index, index + chunkSize))
  }
  return chunks
}

const toJoinedBase64 = (value) =>
  toChunks(Buffer.from(value, 'utf8').toString('base64'))
    .map((chunk) => `        "${chunk}"`)
    .join(',\n')

const main = async () => {
  const [indexHtml, appJs, appCss] = await Promise.all([
    readText('index.html'),
    readText('app.js'),
    readText('app.css'),
  ])

  const code = `package com.freewind.android.debugserver.infra.persistence

import java.nio.charset.StandardCharsets
import java.util.Base64

internal object DebugWebAssets {
    fun indexHtml(): String = decode(indexHtmlBase64)

    fun appJs(): String = decode(appJsBase64)

    fun appCss(): String = decode(appCssBase64)

    private fun decode(base64: String): String {
        return String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8)
    }

    private val indexHtmlBase64 = joinBase64(
${toJoinedBase64(indexHtml)}
    )

    private val appJsBase64 = joinBase64(
${toJoinedBase64(appJs)}
    )

    private val appCssBase64 = joinBase64(
${toJoinedBase64(appCss)}
    )

    private fun joinBase64(vararg parts: String): String {
        return parts.joinToString(separator = "")
    }
}
`

  await mkdir(path.dirname(outputPath), { recursive: true })
  await writeFile(outputPath, code, 'utf8')
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
