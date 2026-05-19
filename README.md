# freewind-android-debug-server

把 Android app 里那层给 AI 用的本地 debug bridge 抽成独立库。

目标：

- app 运行时起本地 HTTP server
- 导出当前页面已注册节点的结构化快照
- 接收外部动作命令
- 侵入收敛到少量 `DebugBridge`、`DebugNodeRegistry`、`Modifier.debugNode(...)`、动作注册

## 提供什么

库模块：`debug-server`

主要 API：

- `DebugBridge`
- `DebugNodeRegistry`
- `Modifier.debugNode(...)`
- `DebugBridge.PublishComposeSnapshot(...)`
- `DebugBridge.RegisterDebugAction(...)`
- `DebugBridge.recordClick(...)`
- `DebugBridge.recordToggle(...)`
- `DebugBridge.recordTextInput(...)`
- `DebugBridge.recordSelection(...)`
- `DebugBridge.RecordScrollState(...)`
- `DebugBridge.RecordLazyListScroll(...)`

HTTP 接口：

- `GET /`
- `GET /snapshot`
- `POST /snapshot/query`
- `GET /operations`
- `GET /logs`
- `POST /action`

当前快照字段：

- app 名
- screen 名
- 组件数
- appState
- 每节点 `id/parentId/type/text/role/backgroundColor/contentColor/visible/enabled/clickable/value/extra/bounds`

## 快速接入

### 1. 依赖本地模块

```kotlin
include(":debug-server")
```

```kotlin
dependencies {
    implementation(project(":debug-server"))
}
```

### 2. Activity 持有 bridge

```kotlin
private val debugBridge = DebugBridge(
    appName = "Your App",
    host = "127.0.0.1",
    port = 8765,
)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    debugBridge.start()
}

override fun onDestroy() {
    debugBridge.stop()
    super.onDestroy()
}
```

### 3. 页面注册节点并发布快照

```kotlin
@Composable
fun DemoScreen(
    debugBridge: DebugBridge,
    handler: DemoHandler,
    store: DemoStore,
) {
    val registry = remember { DebugNodeRegistry() }
    val uiState by store.state.collectAsState()

    debugBridge.PublishComposeSnapshot(
        registry = registry,
        screenName = "DemoScreen",
        appState = mapOf(
            "route" to uiState.route,
            "count" to uiState.count.toString(),
        ),
    )

    debugBridge.RegisterDebugAction(
        targetId = "save_button",
        registerKeys = arrayOf(uiState.count),
    ) { request ->
        when (request.action) {
            "click" -> {
                handler.onSaveClick()
                DebugActionResult(true, "saved")
            }
            else -> DebugActionResult(false, "unsupported")
        }
    }

    Button(
        onClick = debugBridge.recordClick(
            targetId = "save_button",
            onClick = handler::onSaveClick,
        ),
        modifier = Modifier.debugNode(
            registry = registry,
            id = "save_button",
            parentId = "form_root",
            type = "Button",
            text = "Save",
            role = "button",
            clickable = true,
        ),
    ) {
        Text("Save")
    }
}
```

常见低侵入写法：

```kotlin
Switch(
    checked = uiState.enabled,
    onCheckedChange = debugBridge.recordToggle(
        targetId = "enabled_switch",
        onToggle = handler::onEnabledChange,
    ),
)

TextField(
    value = uiState.keyword,
    onValueChange = debugBridge.recordTextInput(
        targetId = "keyword_input",
        onValueChange = handler::onKeywordChange,
    ),
)

val scrollState = rememberScrollState()
debugBridge.RecordScrollState(
    targetId = "detail_scroll",
    state = scrollState,
)
```

### 4. 用 adb 转发访问

```bash
adb forward tcp:8765 tcp:8765
curl http://127.0.0.1:8765/snapshot
```

更省 token 的例子：

```bash
curl "http://127.0.0.1:8765/operations?afterSeq=0&limit=10&groupBySource=true"
curl "http://127.0.0.1:8765/operations?afterSeq=0&limit=10&sources=human"
curl "http://127.0.0.1:8765/snapshot?nodeIds=save_button"
curl "http://127.0.0.1:8765/snapshot?nodeIds=save_button&includeAncestors=true&ancestorDepth=1"
curl "http://127.0.0.1:8765/snapshot?nodeIds=save_button&includeAncestors=true&snapshotFields=screenName,updatedAtEpochMs,nodes&nodeFields=id,parentId,type,text,clickable,bounds"
curl "http://127.0.0.1:8765/snapshot?compact=false"
```

## 设计约束

- 这是“已注册关键节点”模型，不是自动穷举 Compose 全树
- 业务状态修改仍应走你原本 `handler/store`
- `appState` 只放少量高价值字段
- 推荐仅在 debug build 启用

## 动作协议

请求：

```json
{
  "action": "click",
  "targetId": "save_button"
}
```

常见字段：

- `action`
- `targetId`
- `text`
- `dx`
- `dy`

动作语义由业务 app 自己定义。库只负责转发。

## API 速查

基址：

```text
http://127.0.0.1:8765
```

### `GET /`

用途：浏览器里看当前 snapshot + 最近 20 条 operation。

返回：`text/html`

示例：

```bash
curl http://127.0.0.1:8765/
```

### `GET /snapshot`

用途：按 query string 拉 snapshot。

常见 query：

- `compact=true|false`
- `nodeIds=save_button,keyword_input`
- `includeAncestors=true`
- `ancestorDepth=1`
- `descendantDepth=1`
- `snapshotFields=screenName,updatedAtEpochMs,nodes`
- `nodeFields=id,parentId,type,text,clickable,bounds`
- `appStateKeys=route,count`
- `visibleOnly=true`
- `clickableOnly=true`
- `types=Button,Text`
- `textQuery=save`
- `limit=20`

示例：

```bash
curl "http://127.0.0.1:8765/snapshot?nodeIds=save_button&includeAncestors=true&ancestorDepth=1"
```

返回示例：

```json
{
  "screenName": "DemoScreen",
  "updatedAtEpochMs": 1747654321000,
  "nodes": [
    {
      "id": "form_root",
      "parentId": null,
      "type": "Column",
      "text": null,
      "role": null,
      "visible": true,
      "enabled": true,
      "clickable": false,
      "value": null,
      "bounds": {
        "left": 0,
        "top": 0,
        "width": 1080,
        "height": 2160
      }
    },
    {
      "id": "save_button",
      "parentId": "form_root",
      "type": "Button",
      "text": "Save",
      "role": "button",
      "visible": true,
      "enabled": true,
      "clickable": true,
      "value": null,
      "bounds": {
        "left": 48,
        "top": 320,
        "width": 220,
        "height": 96
      }
    }
  ]
}
```

全量示例：

```bash
curl "http://127.0.0.1:8765/snapshot?compact=false&limit=1"
```

```json
{
  "appName": "Your App",
  "screenName": "DemoScreen",
  "componentCount": 1,
  "serverHost": "127.0.0.1",
  "serverPort": 8765,
  "updatedAtEpochMs": 1747654321000,
  "appState": {
    "count": "3",
    "route": "demo"
  },
  "nodes": [
    {
      "id": "save_button",
      "parentId": "form_root",
      "type": "Button",
      "text": "Save",
      "role": "button",
      "backgroundColor": "#FF6200EE",
      "contentColor": "#FFFFFFFF",
      "visible": true,
      "enabled": true,
      "clickable": true,
      "value": null,
      "extra": {
        "variant": "primary"
      },
      "bounds": {
        "left": 48,
        "top": 320,
        "width": 220,
        "height": 96
      }
    }
  ]
}
```

### `POST /snapshot/query`

用途：body 传复杂 query，避免长 URL。

示例：

```bash
curl -X POST "http://127.0.0.1:8765/snapshot/query" \
  -H "Content-Type: application/json" \
  -d '{
    "nodeIds": ["save_button"],
    "includeAncestors": true,
    "ancestorDepth": 1,
    "snapshotFields": ["screenName", "updatedAtEpochMs", "nodes"],
    "nodeFields": ["id", "parentId", "type", "text", "clickable", "bounds"]
  }'
```

body 字段：

- `compact`
- `nodeIds`
- `includeAncestors`
- `ancestorDepth`
- `descendantDepth`
- `snapshotFields`
- `nodeFields`
- `appStateKeys`
- `visibleOnly`
- `clickableOnly`
- `types`
- `textQuery`
- `limit`

返回：同 `GET /snapshot`

## 操作历史协议

`GET /operations` 支持：

- `afterSeq`：游标，只拿更晚记录
- `limit`：最多返回多少条
- `consume=true`：取完即删，按队列用
- `sources=human,ai`：只拿指定来源
- `groupBySource=true`：返回 `humanItems` / `aiItems`

每条 operation 会带：

- `seq/source/action`
- `targetId/targetParentId/targetType/targetText`
- `screenName`
- `text/dx/dy`
- `success/message`
- `extra`
- `createdAtEpochMs`

说明：

- `POST /action` 触发的动作，自动记为 `source=ai`
- App 内用户点击/切换/输入/选择，优先用 `recordClick/recordToggle/recordTextInput/recordSelection`
- `ScrollState` / `LazyListState` 直接挂 `RecordScrollState/RecordLazyListScroll`
- 特殊场景再回退 `debugBridge.recordHumanOperation(...)`

示例：

```bash
curl "http://127.0.0.1:8765/operations?afterSeq=12&limit=5&sources=human,ai"
```

返回示例：

```json
{
  "items": [
    {
      "seq": 13,
      "source": "human",
      "action": "click",
      "targetId": "save_button",
      "targetParentId": "form_root",
      "targetType": "Button",
      "targetText": "Save",
      "screenName": "DemoScreen",
      "text": null,
      "dx": null,
      "dy": null,
      "success": null,
      "message": null,
      "extra": {},
      "createdAtEpochMs": 1747654321000
    },
    {
      "seq": 14,
      "source": "ai",
      "action": "click",
      "targetId": "save_button",
      "targetParentId": "form_root",
      "targetType": "Button",
      "targetText": "Save",
      "screenName": "DemoScreen",
      "text": null,
      "dx": null,
      "dy": null,
      "success": true,
      "message": "saved",
      "extra": {},
      "createdAtEpochMs": 1747654325000
    }
  ],
  "nextAfterSeq": 14,
  "remainingCount": 0
}
```

分组示例：

```bash
curl "http://127.0.0.1:8765/operations?afterSeq=0&limit=10&groupBySource=true"
```

```json
{
  "humanItems": [
    {
      "seq": 13,
      "source": "human",
      "action": "click",
      "targetId": "save_button",
      "targetParentId": "form_root",
      "targetType": "Button",
      "targetText": "Save",
      "screenName": "DemoScreen",
      "text": null,
      "dx": null,
      "dy": null,
      "success": null,
      "message": null,
      "extra": {},
      "createdAtEpochMs": 1747654321000
    }
  ],
  "aiItems": [
    {
      "seq": 14,
      "source": "ai",
      "action": "click",
      "targetId": "save_button",
      "targetParentId": "form_root",
      "targetType": "Button",
      "targetText": "Save",
      "screenName": "DemoScreen",
      "text": null,
      "dx": null,
      "dy": null,
      "success": true,
      "message": "saved",
      "extra": {},
      "createdAtEpochMs": 1747654325000
    }
  ],
  "nextAfterSeq": 14,
  "remainingCount": 0
}
```

### `GET /logs`

用途：拿纯文本风格 operation log，给人快速扫。

示例：

```bash
curl http://127.0.0.1:8765/logs
```

返回示例：

```json
{
  "items": [
    "13 | human | click target=save_button",
    "14 | ai | click target=save_button ok=true msg=saved"
  ]
}
```

### `POST /action`

用途：外部 AI 发动作给 app；库会转发给已注册 handler，并自动记一条 `source=ai` operation。

示例：

```bash
curl -X POST "http://127.0.0.1:8765/action" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "click",
    "targetId": "save_button"
  }'
```

输入字段：

- `action`：动作名，必传
- `targetId`：目标节点 id，常用
- `text`：输入/选择类动作的值
- `dx`：横向位移
- `dy`：纵向位移

返回示例：

```json
{
  "ok": true,
  "message": "saved"
}
```

## 快照查询协议

`GET /snapshot` 与 `POST /snapshot/query` 都支持：

- `compact=true`：默认值。返回精简字段，防 token 爆
- `compact=false`：返回全量字段
- `nodeIds`：指定一个或多个节点
- `nodeIds=save_button`：只拿它自己
- `includeAncestors=true`：把 parent 链一起带回
- `includeAncestors=true&ancestorDepth=1`：拿自己 + parent
- `ancestorDepth=2`：只往上拿 2 层；不传则到顶
- `includeAncestors=true` 且不传 `ancestorDepth`：拿自己到 root 的整条分支
- `descendantDepth=1`：往下拿子树
- `snapshotFields`：顶层字段白名单
- `nodeFields`：节点字段白名单
- `appStateKeys`：只拿指定状态字段
- `visibleOnly=true`
- `clickableOnly=true`
- `types=Button,Text`
- `textQuery=save`
- `limit=20`

默认 compact 顶层字段：

- `screenName`
- `updatedAtEpochMs`
- `nodes`
- 命中 `appStateKeys` 时，再附带 `appState`

默认 compact 节点字段：

- `id`
- `parentId`
- `type`
- `text`
- `role`
- `visible`
- `enabled`
- `clickable`
- `value`
- `bounds`

建议 AI 流程：

1. 先轮询 `/operations`
2. 拿到 `targetId`
3. 再按 `nodeIds + includeAncestors + 精简 fields` 拉局部快照
