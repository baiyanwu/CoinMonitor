package io.baiyanwu.coinmonitor.overlay

import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 悬浮窗这一层只负责消费已经落库的观察列表数据并渲染，不再自己发行情请求。
 * 这样首页和悬浮窗都能统一依赖全局刷新器，后续切 WSS 时改造面会更小。
 */
class OverlayPriceRefreshCoordinator(
    private val scope: CoroutineScope,
    private val overlayRepository: OverlayRepository,
    private val onRender: (List<WatchItem>, OverlaySettings) -> Unit
) {
    private var stateJob: Job? = null
    private var currentItems: List<WatchItem> = emptyList()
    private var currentSettings: OverlaySettings = OverlaySettings()

    fun start() {
        if (stateJob != null) return

        stateJob = scope.launch {
            combine(
                overlayRepository.observeSettings(),
                overlayRepository.observeOverlayItems()
            ) { settings, items ->
                currentSettings = settings
                currentItems = items
                onRender(items, settings)
            }.collect {}
        }
    }

    fun snapshot(): Pair<List<WatchItem>, OverlaySettings> = currentItems to currentSettings

    fun stop() {
        stateJob?.cancel()
        stateJob = null
    }
}
