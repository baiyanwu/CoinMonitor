package io.baiyanwu.coinmonitor.overlay

/**
 * 统一约束悬浮窗运行条件，避免设置页、Activity、Receiver 和 Service 各自维护一套规则。
 */
object OverlayRuntimePolicy {
    fun shouldPersistEnabled(requestedEnabled: Boolean, canDrawOverlays: Boolean): Boolean {
        return requestedEnabled && canDrawOverlays
    }

    fun shouldRunOverlay(settingsEnabled: Boolean, canDrawOverlays: Boolean): Boolean {
        return settingsEnabled && canDrawOverlays
    }
}
