package com.freewind.android.debugserver.demo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun DemoTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content,
    )
}
