# freewind-android-debug-bridge

把 Android app 里那层给 AI 用的本地 debug bridge 抽成独立库。

目标：

- app 运行时起本地 HTTP server
- 导出当前页面已注册节点的结构化快照
- 接收外部动作命令
- 侵入收敛到少量 `DebugBridge`、`DebugNodeRegistry`、`Modifier.debugNode(...)`、动作注册

## 提供什么

库模块：`debug-bridge`

仓库内同时维护：

- `demo-app/`：接好这套 bridge 的 Android Compose demo app

统一协议与独立调试台已迁到：`/Users/peng.li/workspace/freewind-debug-bridge-web`

主要 API：

- `DebugBridge`
- `DebugNodeRegistry`
- `DebugViewRegistry`
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
- `DebugBridge.publishViewSnapshot(...)`
- `DebugBridge.registerComposeAction(...)`
- `View.debugNode(...)`
- `DebugBridge.log(...)`
- `DebugBridge.RecordScrollState(...)`
- `DebugBridge.RecordLazyListScroll(...)`

HTTP 接口：

- `GET /help`
- `GET /action`
- `POST /action`
- `GET /logs`
- `DELETE /logs`
- `GET /state`
- `GET /snapshot`

当前快照字段：

- app 名
- screen 名
- 组件数
- appState
- 每节点 `id/parentId/type/text/role/backgroundColor/contentColor/visible/enabled/clickable/value/extra/bounds`

## 快速接入

### 1. 依赖本地模块

```kotlin
include(":debug-bridge")
```

```kotlin
dependencies {
    implementation(project(":debug-bridge"))
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
- `publishViewSnapshot(...)` 会给当前 view tree 同步默认 fallback action：`click / longClick / input / setChecked`
- 若同一 `targetId` 既有 `registerComposeAction(...)` 又有 view fallback action，显式注册优先
- 旧的 `recordClick/recordToggle/recordTextInput/...` 仍保留，但已不推荐

### 3.1 非 Compose / View 系 app

适合：

- 传统 `Activity + XML`
- `ListView/RecyclerView/AdapterView`
- 想先拿到全树 snapshot，再按需给关键节点补稳定 id

最小接法：

```kotlin
private val debugBridge = DebugBridge(appName = "Your App")
private val debugRegistry = DebugViewRegistry()

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    debugBridge.start()

    findViewById<View>(R.id.saveButton).debugNode(
        id = "save_button",
        type = "Button",
        text = "Save",
        role = "button",
        clickable = true,
    )
}

private fun publishDebugSnapshot() {
    debugBridge.publishViewSnapshot(
        registry = debugRegistry,
        rootView = findViewById(R.id.root),
        screenName = "MainActivity",
        appState = mapOf(
            "route" to "main",
        ),
    )
}
```

说明：

- 不打 `debugNode(...)` 也会抓 view tree；但 id 可能退化成路径型，适合探索，不适合长期脚本
- 给关键节点补 `id/text/role` 后，AI 更稳
- `AdapterView` item root 默认支持 `click`
- `EditText` 默认支持 `input`
- `CompoundButton` 默认支持 `setChecked`

### 4. 用 adb 转发访问

```bash
adb forward tcp:8765 tcp:8765
curl http://127.0.0.1:8765/meta
curl http://127.0.0.1:8765/snapshot
```

更省 token 的例子：

```bash
curl "http://127.0.0.1:8765/meta"
curl "http://127.0.0.1:8765/help"
curl "http://127.0.0.1:8765/action"
curl "http://127.0.0.1:8765/logs"
curl "http://127.0.0.1:8765/state"
curl "http://127.0.0.1:8765/snapshot"
curl "http://127.0.0.1:8765/snapshot?targetId=save_button&scope=branchToRoot&fields=id,type,text,bounds"
```

### 5. 独立调试台与 demo app

独立调试台在：

`/Users/peng.li/workspace/freewind-debug-bridge-web`

编译 demo app：

```bash
./gradlew :demo-app:assembleDebug
```

看效果：

1. 安装运行 `demo-app`
2. `adb forward tcp:8765 tcp:8765`
3. 在独立调试台里把 base URL 指到 `http://127.0.0.1:8765`
4. 然后：
   - 看 `logs` 表格与查询
   - 查 `state`
   - 查 `snapshot`
   - 直接点动态生成的 action 按钮

## 设计约束

- 这是“已注册关键节点”模型，不是自动穷举 Compose 全树
- 业务状态修改仍应走你原本 `handler/store`
- `appState` 只放少量高价值字段
- 推荐仅在 debug build 启用

## 协议来源

本仓不再维护独立 API 文档。

唯一准绳：

- `/Users/peng.li/workspace/freewind-debug-bridge-web/src/api-spec.ts`

修改协议时：

1. 先改那边类型
2. 再回这里对齐实现
3. 最后跑 `./gradlew :debug-bridge:assemble :demo-app:assemble`

当前 Android 侧已对齐的 route：

- `GET /meta`
- `GET /help`
- `GET /action`
- `POST /action`
- `GET /logs`
- `DELETE /logs`
- `GET /state`
- `GET /snapshot`
