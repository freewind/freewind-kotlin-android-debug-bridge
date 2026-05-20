# freewind-android-debug-server

把 Android app 里那层给 AI 用的本地 debug bridge 抽成独立库。

目标：

- app 运行时起本地 HTTP server
- 导出当前页面已注册节点的结构化快照
- 接收外部动作命令
- 侵入收敛到少量 `DebugBridge`、`DebugNodeRegistry`、`Modifier.debugNode(...)`、动作注册

## 提供什么

库模块：`debug-server`

仓库内同时维护：

- `web/`：给人看的 React + Antd + TypeScript 调试台源码
- `demo-app/`：接好这套 bridge 的 Android Compose demo app

主要 API：

- `DebugBridge`
- `DebugNodeRegistry`
- `Modifier.debugNode(...)`
- `Modifier.debugTextNode(...)`
- `Modifier.debugButtonNode(...)`
- `Modifier.debugSwitchNode(...)`
- `Modifier.debugTextFieldNode(...)`
- `Modifier.debugCardNode(...)`
- `Modifier.debugColumnNode(...)`
- `Modifier.debugRowNode(...)`
- `Modifier.debugLazyColumnNode(...)`
- `DebugBridge.publishComposeSnapshot(...)`
- `DebugBridge.registerComposeAction(...)`
- `DebugBridge.log(...)`
- `DebugBridge.RecordScrollState(...)`
- `DebugBridge.RecordLazyListScroll(...)`

HTTP 接口：

- `GET /`
- `GET /help`
- `GET /action`
- `GET /logs`
- `DELETE /logs`
- `GET /state`
- `GET /snapshot`
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

    debugBridge.publishComposeSnapshot(
        registry = registry,
        screenName = "DemoScreen",
        appState = mapOf(
            "route" to uiState.route,
            "count" to uiState.count.toString(),
        ),
    )

    debugBridge.registerComposeAction(
        targetId = "save_button",
        registerKeys = arrayOf(uiState.count),
    ) { request ->
        when (request.action) {
            "click" -> {
                handler.onSaveClick()
                DebugActionResult(true, "accepted")
            }
            else -> DebugActionResult(false, "unsupported")
        }
    }

    Button(
        onClick = {
            debugBridge.log(
                event = "click",
                targetId = "save_button",
                data = mapOf(
                    "screen" to "DemoScreen",
                    "targetType" to "Button",
                    "targetText" to "Save",
                ),
            )
            handler.onSaveClick()
        },
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

推荐显式写法：

```kotlin
Switch(
    checked = uiState.enabled,
    onCheckedChange = { checked ->
        debugBridge.log(
            event = "toggle",
            targetId = "enabled_switch",
            data = mapOf(
                "screen" to "DemoScreen",
                "checked" to checked.toString(),
            ),
        )
        handler.onEnabledChange(checked)
    },
)

TextField(
    value = uiState.keyword,
    onValueChange = { value ->
        debugBridge.log(
            event = "input",
            targetId = "keyword_input",
            data = mapOf(
                "screen" to "DemoScreen",
                "value" to value,
                "length" to value.length.toString(),
            ),
        )
        handler.onKeywordChange(value)
    },
)

val scrollState = rememberScrollState()
debugBridge.RecordScrollState(
    targetId = "detail_scroll",
    state = scrollState,
)
```

推荐优先用类型化 node 方法：

```kotlin
Text(
    text = "Title",
    modifier = Modifier.debugTextNode(
        registry = registry,
        id = "title_text",
        parentId = "screen_root",
        text = "Title",
    ),
)

Button(
    onClick = handler::onSaveClick,
    modifier = Modifier.debugButtonNode(
        registry = registry,
        id = "save_button",
        parentId = "button_row",
        text = "Save",
    ),
) {
    Text("Save")
}

OutlinedTextField(
    value = uiState.keyword,
    onValueChange = handler::onKeywordChange,
    label = { Text("Keyword") },
    modifier = Modifier.debugTextFieldNode(
        registry = registry,
        id = "keyword_input",
        parentId = "form_root",
        value = uiState.keyword,
        labelText = "Keyword",
    ),
)
```

这些方法会在对应类型上把关键字段写死：

- `debugButtonNode(...)`：固定 `type=Button`、`role=button`、`clickable=true`
- `debugSwitchNode(...)`：固定 `type=Switch`、`role=switch`、`value=checked`
- `debugTextFieldNode(...)`：固定 `type=TextField`、`role=input`、`value=value`
- `debugLazyColumnNode(...)`：固定 `type=LazyColumn`、`role=list`、`extra.itemCount`
- `debugTextNode(...)`：要求显式给 `text`

说明：

- `log(...)` 只负责记结构化事件
- 原业务逻辑仍直接调用原 `handler`
- `registerComposeAction(...)` 负责把 `targetId -> action handler` 暴露给 `POST /action`
- 旧的 `recordClick/recordToggle/recordTextInput/...` 仍保留，但已不推荐

### 4. 用 adb 转发访问

```bash
adb forward tcp:8765 tcp:8765
curl http://127.0.0.1:8765/snapshot
```

更省 token 的例子：

```bash
curl "http://127.0.0.1:8765/"
curl "http://127.0.0.1:8765/help"
curl "http://127.0.0.1:8765/action"
curl "http://127.0.0.1:8765/logs"
curl "http://127.0.0.1:8765/state"
curl "http://127.0.0.1:8765/snapshot"
curl "http://127.0.0.1:8765/snapshot?targetId=save_button&scope=branchToRoot&fields=id,type,text,bounds"
```

### 5. web 控制台与 demo app

构建 web 并同步到 server 内置资产：

```bash
cd web
pnpm install
pnpm build
```

编译 demo app：

```bash
./gradlew :demo-app:assembleDebug
```

看效果：

1. 安装运行 `demo-app`
2. `adb forward tcp:8765 tcp:8765`
3. 打开 `http://127.0.0.1:8765/`
4. 在 web 控制台里：
   - 看 `logs` 表格与查询
   - 查 `state`
   - 查 `snapshot`
   - 直接点动态生成的 action 按钮

## 设计约束

- 这是“已注册关键节点”模型，不是自动穷举 Compose 全树
- 业务状态修改仍应走你原本 `handler/store`
- `appState` 只放少量高价值字段
- 推荐仅在 debug build 启用

## 面向 AI 的对外协议

这一节是目标协议规范。

- 先把语义写死，便于别的 AI/别的技术栈照着实现
- 当前代码未必已全部实现到位
- 但后续实现应尽量向这里收敛

基址：

```text
http://127.0.0.1:8765
```

总原则：

- `GET /`：返回给人看的可视化页
- 当前实现是 React + Antd 控制台，不再是静态占位 HTML
- `GET /help`：返回动态全量 help，给 AI 一次性拿结构、字段、示例
- `POST /action` body 支持 `action/targetId/text/dx/dy/args/source`
- `DELETE /logs` 返回 `accepted/message/clearedCount`，同时保留 `ok/deletedCount`
- `GET /help`：返回动态全量 help，只给结构、能力、字段、示例，不给大数据
- `GET /action`、`GET /logs`、`GET /state`、`GET /snapshot`：默认返回该资源当前时刻的 summary
- `DELETE /logs`：清空当前内存里的全部日志
- 子路径带 query：才返回具体数据
- `POST /action`：唯一执行入口

### `GET /`

用途：

- 给人直接打开看
- 返回可视化调试页
- 页面里展示概览与入口，不直接承载 AI 协议
- 当前页支持：
  - 日志表格
  - logs 查询
  - state 查询
  - snapshot 查询
  - action 动态按钮
  - 手动 action 表单

返回应包含：

- `text/html`
- 前端静态资源入口页

返回示例：

```html
<!doctype html>
<html>
  <head>...</head>
  <body>debug console</body>
</html>
```

### `GET /help`

用途：

- AI 第一次接入时先看这里
- 返回当前时刻动态组装的全量 help
- 只返回结构性信息，不返回大量业务数据
- 告诉 AI：当前有哪些能力、有哪些 endpoint、每个 endpoint 支持哪些 query、建议先怎么查

返回应包含：

- `appName`
- `screenName`
- `serverTime`
- `capabilities`
- `counts`
- `endpoints`
- `examples`

返回示例：

```json
{
  "appName": "Your App",
  "screenName": "DemoScreen",
  "serverTime": "20260519-223355",
  "capabilities": [
    "action",
    "logs",
    "state",
    "snapshot"
  ],
  "counts": {
    "actionTargetCount": 3,
    "logCount": 128,
    "stateKeyCount": 5,
    "snapshotNodeCount": 84
  },
  "endpoints": [
    {
      "method": "GET",
      "path": "/",
      "summary": "open human-readable debug console"
    },
    {
      "method": "GET",
      "path": "/help",
      "summary": "return dynamic full help for AI"
    },
    {
      "method": "GET",
      "path": "/action",
      "summary": "show executable targets and actions",
      "queryFields": ["targetId", "action", "screen"]
    },
    {
      "method": "POST",
      "path": "/action",
      "summary": "trigger one concrete action",
      "bodyFields": ["action", "targetId", "text", "dx", "dy"]
    },
    {
      "method": "GET",
      "path": "/logs",
      "summary": "show log summary or query matching logs",
      "queryFields": ["event", "level", "source", "targetId", "screen", "from", "to", "limit", "keyword"]
    },
    {
      "method": "DELETE",
      "path": "/logs",
      "summary": "delete all existing logs"
    },
    {
      "method": "GET",
      "path": "/state",
      "summary": "show state summary or query state values",
      "queryFields": ["keys", "targetId", "scope"]
    },
    {
      "method": "GET",
      "path": "/snapshot",
      "summary": "show tree summary or query node snapshot",
      "queryFields": ["targetId", "scope", "depth", "types", "textKeyword", "fields", "limit"]
    }
  ],
  "examples": [
    "GET /help",
    "GET /logs",
    "GET /snapshot?targetId=save_button&scope=branchToRoot&fields=id,type,text,bounds",
    "POST /action {\"action\":\"click\",\"targetId\":\"save_button\"}"
  ]
}
```

### `GET /action`

默认返回：

- 当前可执行目标总数
- 每个 `targetId`
- 每个 target 支持的 `action`
- 每个 action 需要哪些 args
- 最小调用示例

常见 query：

- `targetId=save_button`
- `action=click`
- `screen=DemoScreen`

默认返回示例：

```json
{
  "summary": {
    "targetCount": 3,
    "actionCount": 5
  },
  "items": [
    {
      "targetId": "save_button",
      "targetType": "Button",
      "screen": "DemoScreen",
      "actions": [
        {
          "name": "click",
          "args": [],
          "summary": "trigger save flow",
          "example": {
            "action": "click",
            "targetId": "save_button"
          }
        }
      ]
    }
  ]
}
```

本地代码里通常这样注册：

```kotlin
debugBridge.registerComposeAction(
    targetId = "save_button",
) { request ->
    when (request.action) {
        "click" -> {
            handler.onSaveClick()
            DebugActionResult(true, "accepted")
        }
        else -> DebugActionResult(false, "unsupported")
    }
}
```

这里建议：

- `DebugActionResult` 只表达“这个 action req 是否被接受/拒绝”
- 真正业务结果、失败原因、补充日志，放到 `handler` 内部自己记录
- `message` 保持短小即可，比如 `accepted` / `unsupported`

### `POST /action`

用途：

- 外部 AI 发动作给 app
- server 按 `targetId` 找已注册 handler
- 自动记一条 action log
- `source` 取 req 里的 `source`，未传时默认 `ai`

body 字段：

- `action`
- `targetId`
- `text`
- `dx`
- `dy`
- `args`
- `source`

示例：

```bash
curl -X POST "http://127.0.0.1:8765/action" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "click",
    "targetId": "save_button",
    "source": "human",
    "args": {
      "reason": "retry"
    }
  }'
```

返回示例：

```json
{
  "accepted": true,
  "message": "accepted",
  "action": "click",
  "targetId": "save_button"
}
```

### `GET /logs`

默认返回：

- 当前 log 总数
- 时间范围
- 各 `level` 数量
- 各 `source` 数量
- 高频 `event`
- 最近少量高价值例子

常见 query：

- `event=click`
- `level=warn`
- `source=ai`
- `targetId=save_button`
- `screen=DemoScreen`
- `from=20260101-112233`
- `to=20260101-122233`
- `limit=20`
- `keyword=timeout`

默认返回示例：

```json
{
  "summary": {
    "total": 128,
    "timeRange": {
      "from": "20260519-220001",
      "to": "20260519-223355"
    },
    "levelCounts": {
      "debug": 80,
      "info": 30,
      "warn": 12,
      "error": 6
    },
    "sourceCounts": {
      "human": 40,
      "ai": 28
    },
    "eventCountsTop": {
      "click": 22,
      "input": 18,
      "request_fail": 4
    }
  }
}
```

### `DELETE /logs`

用途：

- 清空当前进程内内存日志
- 方便 AI 从新一轮操作开始录制

返回示例：

```json
{
  "accepted": true,
  "message": "cleared",
  "clearedCount": 128,
  "ok": true,
  "deletedCount": 128
}
```

带 query 返回示例：

```json
{
  "items": [
    {
      "seq": 15,
      "time": "20260519-221530",
      "source": "ai",
      "level": "info",
      "event": "click",
      "targetId": "save_button",
      "summary": "ai triggered save button",
      "data": {
        "screen": "DemoScreen",
        "targetType": "Button",
        "targetText": "Save",
        "accepted": "true"
      }
    }
  ],
  "nextAfterSeq": 15
}
```

记录字段建议：

- 固定字段：
  - `time`
  - `source`
  - `event`
  - `level`
  - `targetId`
  - `summary`
  - `data`
- `event` 常见例子：
  - `click`
  - `toggle`
  - `input`
  - `scroll`
  - `request_fail`
  - `state_change`
- `data` 常见 key：
  - `screen`
  - `targetType`
  - `targetText`
  - `checked`
  - `value`
  - `length`
  - `accepted`
  - `reason`
  - `actual`
  - `expected`

### `GET /state`

默认返回：

- 当前有哪些 appState key
- 每个 key 的 sample value
- 哪些 target 挂了局部 state
- 支持哪些 query

常见 query：

- `keys=route,count,loading`
- `targetId=keyword_input`
- `scope=app`
- `scope=target`
- `scope=branch`

默认返回示例：

```json
{
  "summary": {
    "appStateKeys": [
      { "key": "route", "sample": "demo" },
      { "key": "count", "sample": "3" },
      { "key": "loading", "sample": "false" }
    ],
    "targetStateTargets": [
      "form_root",
      "keyword_input"
    ]
  }
}
```

带 query 返回示例：

```json
{
  "appState": {
    "route": "demo",
    "count": "3"
  },
  "targetState": {
    "keyword": "hello"
  }
}
```

接入点：

- 页面层：主动 publish `appState`
- 组件层：若要让 AI 查局部 state，需主动 publish `targetState`
- 不 publish，就查不到

### `GET /snapshot`

默认返回：

- 当前 screen
- node 总数
- root ids
- type 分布
- clickable 数量
- 可查询字段列表
- 推荐 query 示例

常见 query：

- `targetId=save_button`
- `scope=self`
- `scope=branchToRoot`
- `scope=subtree`
- `depth=2`
- `types=Button,Text`
- `textKeyword=save`
- `visible=true`
- `enabled=true`
- `clickable=true`
- `fields=id,parentId,type,text,role,bounds,clickable`
- `limit=20`

默认返回示例：

```json
{
  "summary": {
    "screen": "DemoScreen",
    "nodeCount": 84,
    "rootIds": ["form_root"],
    "typeCounts": {
      "Button": 6,
      "Text": 18,
      "TextField": 2
    },
    "clickableCount": 9
  },
  "fieldCatalog": [
    "id",
    "parentId",
    "type",
    "text",
    "role",
    "visible",
    "enabled",
    "clickable",
    "value",
    "bounds"
  ],
  "examples": [
    "/snapshot?targetId=save_button&scope=self",
    "/snapshot?targetId=save_button&scope=branchToRoot&fields=id,type,text,bounds",
    "/snapshot?types=Button&clickable=true&limit=20"
  ]
}
```

带 query 返回示例：

```json
{
  "screen": "DemoScreen",
  "nodes": [
    {
      "id": "save_button",
      "parentId": "form_root",
      "type": "Button",
      "text": "Save",
      "role": "button",
      "clickable": true,
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

接入点：

- 组件必须先 `Modifier.debugNode(...)` 注册
- 页面必须先 `publishComposeSnapshot(...)`
- 不注册节点，就不会出现在 `/snapshot`

## 接入要点

AI 能否查到/操作到，取决于你有没有先把信息接进来：

- 想操作一个 target：
  - 先 `registerComposeAction(targetId = ...)`
- 想看到 app state：
  - 先在页面层 publish `appState`
- 想看到局部 state：
  - 先主动挂到 target 对应 state
- 想看到组件树：
  - 先 `Modifier.debugNode(...)` 注册
  - 再 `publishComposeSnapshot(...)`
- 想留下可查的交互记录：
  - 用 `debugBridge.log(...)`
  - `ScrollState` / `LazyListState` 可直接挂 `RecordScrollState/RecordLazyListScroll`

## 实现任务清单

下面这组任务，是别的技术栈也应尽量对齐的最小实现顺序。

### P0. 文档对齐

- 先按本 README 固定协议语义
- 不要先自由发挥接口名
- 不要先做 UI，再反推协议

### P1. 人类页

- `GET /` 返回 HTML
- 给人看，不是给 AI 协议消费
- 至少放：
  - 当前 app/screen 概览
  - `/help` 入口
  - `action/logs/state/snapshot` 入口

### P2. 动态全量 help

- `GET /help` 返回 JSON
- 内容动态组装
- 只放结构/能力/字段/示例，不放大数据
- 至少含：
  - `appName`
  - `screenName`
  - `serverTime`
  - `capabilities`
  - `counts`
  - `endpoints`
  - `examples`

### P3. 动作协议

- `GET /action`
  - 默认返回当前可执行 target/action summary
- `POST /action`
  - 真执行
- 必须打通：
  - `targetId -> handler`
  - handler 接收 `action/text/dx/dy`
  - action req 自动留一条 `source=ai` log

### P4. 日志协议

- `GET /logs`
  - 无 query：返回 summary
  - 有 query：返回匹配 items
- 至少支持：
  - `event`
  - `level`
  - `source`
  - `targetId`
  - `screen`
  - `from`
  - `to`
  - `limit`
  - `keyword`

### P5. 状态协议

- `GET /state`
  - 无 query：返回 state summary
  - 有 query：返回具体 state
- 至少支持：
  - `keys`
  - `targetId`
  - `scope`
- 业务代码必须显式 publish：
  - `appState`
  - 可选 `targetState`

### P6. 快照协议

- `GET /snapshot`
  - 无 query：返回 snapshot summary
  - 有 query：返回具体 node data
- 至少支持：
  - `targetId`
  - `scope`
  - `depth`
  - `types`
  - `textKeyword`
  - `fields`
  - `limit`
- UI 代码必须显式 publish：
  - node register
  - snapshot publish

### P7. 完成标准

- 人类先开 `GET /`
- AI 先读 `GET /help`
- AI 不读源码，也能从 `/help` 知道：
  - 有哪些能力
  - 哪些 query 能用
  - 哪些字段会出现
  - 下一步该查哪里
- `GET /action|logs|state|snapshot` 无 query 都能返回 summary
- 加 query 后，都能稳定返回具体数据
- `POST /action` 可驱动真实 handler
- 所有关键行为都能通过 `logs/state/snapshot` 交叉验证
