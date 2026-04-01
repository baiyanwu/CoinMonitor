package io.baiyanwu.coinmonitor.data.refresh

import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import io.baiyanwu.coinmonitor.domain.repository.QuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页和悬浮窗都只读同一份观察列表快照，因此“是否需要持续刷新”也必须统一在这里收口。
 *
 * 这一层只负责聚合运行态并下发配置，不直接关心底层是轮询还是 WSS；
 * 后续接入长连接时，只要替换 QuoteRefreshEngine 的实现即可。
 */
class GlobalQuoteRefreshCoordinator(
    private val scope: CoroutineScope,
    private val watchlistRepository: WatchlistRepository,
    private val quoteRepository: QuoteRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    marketQuoteRepository: MarketQuoteRepository,
    okxCredentialsProvider: suspend () -> io.baiyanwu.coinmonitor.domain.model.OkxApiCredentials? = { null },
    networkLogRepository: NetworkLogRepository
) {
    private val homeActive = MutableStateFlow(false)
    private val overlayActive = MutableStateFlow(false)
    private val refreshEngine: QuoteRefreshEngine = StreamingQuoteRefreshEngine(
        scope = scope,
        watchlistRepository = watchlistRepository,
        quoteRepository = quoteRepository,
        marketQuoteRepository = marketQuoteRepository,
        okxCredentialsProvider = okxCredentialsProvider,
        networkLogRepository = networkLogRepository
    )

    private var observeJob: Job? = null
    private var persistJob: Job? = null
    private var currentItems: List<WatchItem> = emptyList()
    private var wasRunning: Boolean = false

    init {
        start()
    }

    fun setHomeActive(active: Boolean) {
        homeActive.value = active
    }

    fun setOverlayActive(active: Boolean) {
        overlayActive.value = active
    }

    suspend fun refreshNow() {
        refreshEngine.refreshNow()
    }

    fun reconnect() {
        refreshEngine.reconnect()
    }

    fun stop() {
        scope.launch {
            persistLatestSnapshot()
        }
        refreshEngine.stop()
        observeJob?.cancel()
        observeJob = null
        persistJob?.cancel()
        persistJob = null
    }

    private fun start() {
        if (observeJob != null) return

        observeJob = scope.launch {
            combine(
                watchlistRepository.observeWatchlist(),
                appPreferencesRepository.observePreferences(),
                homeActive,
                overlayActive
            ) { items, preferences, isHomeActive, isOverlayActive ->
                RefreshSnapshot(
                    items = items,
                    refreshIntervalMillis = preferences.refreshIntervalSeconds * 1_000L,
                    shouldRun = isHomeActive || isOverlayActive
                )
            }.collect { snapshot ->
                currentItems = snapshot.items
                quoteRepository.seedFromWatchItems(snapshot.items)
                quoteRepository.retainOnly(snapshot.items.map { it.id }.toSet())
                if (wasRunning && !snapshot.shouldRun) {
                    persistLatestSnapshot()
                }
                wasRunning = snapshot.shouldRun
                refreshEngine.updateConfig(
                    QuoteRefreshConfig(
                        enabled = snapshot.shouldRun,
                        items = snapshot.items,
                        refreshIntervalMillis = snapshot.refreshIntervalMillis
                    )
                )
            }
        }
        persistJob = scope.launch {
            while (isActive) {
                delay(SNAPSHOT_PERSIST_INTERVAL_MILLIS)
                if (homeActive.value || overlayActive.value) {
                    persistLatestSnapshot()
                }
            }
        }
    }

    private suspend fun persistLatestSnapshot() {
        val items = currentItems
        if (items.isEmpty()) return
        val quotes = quoteRepository.getQuotes(items.map { it.id })
        if (quotes.isEmpty()) return
        watchlistRepository.persistQuoteSnapshot(quotes)
    }

    private data class RefreshSnapshot(
        val items: List<WatchItem>,
        val refreshIntervalMillis: Long,
        val shouldRun: Boolean
    )

    private companion object {
        private const val SNAPSHOT_PERSIST_INTERVAL_MILLIS = 15 * 60 * 1000L
    }
}
