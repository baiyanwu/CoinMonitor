package io.coinbar.tokenmonitor.overlay

import io.coinbar.tokenmonitor.domain.model.OverlaySettings
import io.coinbar.tokenmonitor.domain.model.WatchItem
import io.coinbar.tokenmonitor.domain.repository.MarketQuoteRepository
import io.coinbar.tokenmonitor.domain.repository.OverlayRepository
import io.coinbar.tokenmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OverlayPriceRefreshCoordinator(
    private val scope: CoroutineScope,
    private val watchlistRepository: WatchlistRepository,
    private val overlayRepository: OverlayRepository,
    private val marketQuoteRepository: MarketQuoteRepository,
    private val onRender: (List<WatchItem>, OverlaySettings) -> Unit
) {
    private var stateJob: Job? = null
    private var refreshJob: Job? = null

    private var currentItems: List<WatchItem> = emptyList()
    private var currentSettings: OverlaySettings = OverlaySettings()

    fun start() {
        if (stateJob != null) return

        stateJob = scope.launch {
            combine(
                overlayRepository.observeSettings(),
                overlayRepository.observeOverlayItems()
            ) { settings, items ->
                settings to items
            }.collect { (settings, items) ->
                currentSettings = settings
                currentItems = items
                onRender(items, settings)
                restartRefreshLoop()
            }
        }
    }

    suspend fun refreshNow() {
        val items = currentItems.take(currentSettings.maxItems)
        if (items.isEmpty()) return

        val quotes = marketQuoteRepository.fetchQuotes(items)
        if (quotes.isNotEmpty()) {
            watchlistRepository.updateQuotes(quotes)
        }
    }

    fun snapshot(): Pair<List<WatchItem>, OverlaySettings> = currentItems to currentSettings

    fun stop() {
        refreshJob?.cancel()
        refreshJob = null
        stateJob?.cancel()
        stateJob = null
    }

    private fun restartRefreshLoop() {
        refreshJob?.cancel()
        if (!currentSettings.enabled || currentItems.isEmpty()) return

        refreshJob = scope.launch {
            refreshNow()
            while (isActive) {
                delay(3_000)
                refreshNow()
            }
        }
    }
}

