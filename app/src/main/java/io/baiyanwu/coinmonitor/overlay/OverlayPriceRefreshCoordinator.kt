package io.baiyanwu.coinmonitor.overlay

import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
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
    private val appPreferencesRepository: AppPreferencesRepository,
    private val marketQuoteRepository: MarketQuoteRepository,
    private val onRender: (List<WatchItem>, OverlaySettings) -> Unit
) {
    private var stateJob: Job? = null
    private var refreshJob: Job? = null

    private var currentItems: List<WatchItem> = emptyList()
    private var currentSettings: OverlaySettings = OverlaySettings()
    private var refreshIntervalMillis: Long =
        AppPreferences.DEFAULT_REFRESH_INTERVAL_SECONDS * 1_000L

    fun start() {
        if (stateJob != null) return

        stateJob = scope.launch {
            combine(
                overlayRepository.observeSettings(),
                overlayRepository.observeOverlayItems(),
                appPreferencesRepository.observePreferences()
            ) { settings, items, preferences ->
                OverlayRefreshSnapshot(
                    settings = settings,
                    items = items,
                    refreshIntervalMillis = preferences.refreshIntervalSeconds * 1_000L
                )
            }.collect { snapshot ->
                currentSettings = snapshot.settings
                currentItems = snapshot.items
                refreshIntervalMillis = snapshot.refreshIntervalMillis
                onRender(snapshot.items, snapshot.settings)
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
                delay(refreshIntervalMillis)
                refreshNow()
            }
        }
    }
}

private data class OverlayRefreshSnapshot(
    val settings: OverlaySettings,
    val items: List<WatchItem>,
    val refreshIntervalMillis: Long
)
