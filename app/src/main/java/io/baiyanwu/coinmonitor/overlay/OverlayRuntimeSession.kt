package io.baiyanwu.coinmonitor.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 管理仅在服务运行周期内生效的悬浮窗临时状态。
 * 这类状态不落库，避免“临时隐藏”误伤用户原有配置。
 */
object OverlayRuntimeSession {
    private val _temporarilyHidden = MutableStateFlow(false)
    val temporarilyHidden: StateFlow<Boolean> = _temporarilyHidden.asStateFlow()

    fun setTemporarilyHidden(hidden: Boolean) {
        _temporarilyHidden.value = hidden
    }

    fun reset() {
        _temporarilyHidden.value = false
    }
}
