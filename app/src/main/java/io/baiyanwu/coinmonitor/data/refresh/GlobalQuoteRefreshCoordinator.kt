package io.baiyanwu.coinmonitor.data.refresh

import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
        marketQuoteRepository = marketQuoteRepository,
        okxCredentialsProvider = okxCredentialsProvider,
        networkLogRepository = networkLogRepository
    )

    private var observeJob: Job? = null

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
        refreshEngine.stop()
        observeJob?.cancel()
        observeJob = null
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
                refreshEngine.updateConfig(
                    QuoteRefreshConfig(
                        enabled = snapshot.shouldRun,
                        items = snapshot.items,
                        refreshIntervalMillis = snapshot.refreshIntervalMillis
                    )
                )
            }
        }
    }

    private data class RefreshSnapshot(
        val items: List<WatchItem>,
        val refreshIntervalMillis: Long,
        val shouldRun: Boolean
    )
}
