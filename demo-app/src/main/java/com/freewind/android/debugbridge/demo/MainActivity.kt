package com.freewind.android.debugbridge.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.core.content.pm.PackageInfoCompat
import com.freewind.android.debugbridge.DebugBridge
import com.freewind.android.debugbridge.demo.domain.handler.DemoHandler
import com.freewind.android.debugbridge.demo.domain.store.DemoStore
import com.freewind.android.debugbridge.demo.features.main.MainScreen
import com.freewind.android.debugbridge.demo.ui.DemoTheme

class MainActivity : ComponentActivity() {
    private val debugBridge = DebugBridge(appName = "Debug Bridge Demo")
    private val store = DemoStore()
    private val handler = DemoHandler(store = store)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        debugBridge.setBuildVersion(resolveBuildVersion())
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

    private fun resolveBuildVersion(): Int {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
