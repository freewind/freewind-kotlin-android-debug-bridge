package com.freewind.android.debugserver.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.freewind.android.debugserver.DebugBridge
import com.freewind.android.debugserver.demo.domain.handler.DemoHandler
import com.freewind.android.debugserver.demo.domain.store.DemoStore
import com.freewind.android.debugserver.demo.features.main.MainScreen
import com.freewind.android.debugserver.demo.ui.DemoTheme

class MainActivity : ComponentActivity() {
    private val debugBridge = DebugBridge(appName = "Debug Server Demo")
    private val store = DemoStore()
    private val handler = DemoHandler(store = store)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        debugBridge.start()
        setContent {
            DemoTheme {
                val currentStore = remember { store }
                val currentHandler = remember { handler }
                MainScreen(
                    debugBridge = debugBridge,
                    store = currentStore,
                    handler = currentHandler,
                )
            }
        }
    }

    override fun onDestroy() {
        debugBridge.stop()
        super.onDestroy()
    }
}
