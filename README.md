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
