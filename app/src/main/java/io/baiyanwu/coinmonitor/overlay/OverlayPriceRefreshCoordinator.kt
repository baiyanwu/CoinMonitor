package io.baiyanwu.coinmonitor.overlay

import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.QuoteRepository
import io.baiyanwu.coinmonitor.domain.model.withQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 悬浮窗这一层只负责组合“静态观察项 + 内存实时报价”并渲染，不自己发行情请求。
 */
class OverlayPriceRefreshCoordinator(
    private val scope: CoroutineScope,
    private val overlayRepository: OverlayRepository,
    private val quoteRepository: QuoteRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val onRender: (List<WatchItem>, OverlaySettings, Boolean) -> Unit
) {
    private var stateJob: Job? = null
    private var currentItems: List<WatchItem> = emptyList()
    private var currentSettings: OverlaySettings = OverlaySettings()

    fun start() {
        if (stateJob != null) return

        stateJob = scope.launch {
            combine(
                overlayRepository.observeSettings(),
                overlayRepository.observeOverlayItems(),
                quoteRepository.quotes,
                appPreferencesRepository.observePreferences()
            ) { settings, items, quotes, preferences ->
                OverlayRenderSnapshot(
                    items = items.map { item -> item.withQuote(quotes[item.id]) },
                    settings = settings,
                    showOnchainMarketCap = preferences.showOnchainMarketCap
                )
            }
                .distinctUntilChanged()
                .conflate()
                .collect { snapshot ->
                    currentSettings = snapshot.settings
                    currentItems = snapshot.items
                    onRender(
                        snapshot.items,
                        snapshot.settings,
                        snapshot.showOnchainMarketCap
                    )
                }
        }
    }

    fun snapshot(): Pair<List<WatchItem>, OverlaySettings> = currentItems to currentSettings

    fun stop() {
        stateJob?.cancel()
        stateJob = null
    }
}

private data class OverlayRenderSnapshot(
    val items: List<WatchItem>,
    val settings: OverlaySettings,
    val showOnchainMarketCap: Boolean
)
