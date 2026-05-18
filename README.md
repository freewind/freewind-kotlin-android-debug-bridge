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

HTTP 接口：

- `GET /`
- `GET /snapshot`
- `GET /logs`
- `POST /action`

当前快照字段：

- app 名
- screen 名
- 组件数
- appState
- 每节点 `id/type/text/role/backgroundColor/contentColor/visible/enabled/clickable/value/extra/bounds`

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
        onClick = handler::onSaveClick,
        modifier = Modifier.debugNode(
            registry = registry,
            id = "save_button",
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

### 4. 用 adb 转发访问

```bash
adb forward tcp:8765 tcp:8765
curl http://127.0.0.1:8765/snapshot
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
