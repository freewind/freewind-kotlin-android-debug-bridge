package com.freewind.android.debugbridge.demo.domain.handler

import com.freewind.android.debugbridge.demo.domain.store.DemoStore

class DemoHandler(
    private val store: DemoStore,
) {
    fun onEnabledChange(enabled: Boolean) {
        store.setEnabled(enabled)
    }

    fun onKeywordChange(keyword: String) {
        store.setKeyword(keyword)
    }

    fun onSaveClick() {
        store.save()
    }

    fun onResetClick() {
        store.reset()
    }
}
