package io.baiyanwu.coinmonitor.data.refresh

import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 默认的轮询式刷新引擎。
 *
 * 这一层只关心“按配置刷新并落库”，不关心首页、悬浮窗、前后台等场景判断；
 * 这些状态都由上面的全局协调器统一计算后再灌进来。
 */
class PollingQuoteRefreshEngine(
    private val scope: CoroutineScope,
    private val watchlistRepository: WatchlistRepository,
    private val marketQuoteRepository: MarketQuoteRepository
) : QuoteRefreshEngine {
    private val refreshMutex = Mutex()

    private var refreshJob: Job? = null
    private var currentConfig: QuoteRefreshConfig = QuoteRefreshConfig(
        enabled = false,
        items = emptyList(),
        refreshIntervalMillis = 15_000L
    )

    override fun updateConfig(config: QuoteRefreshConfig) {
        currentConfig = config
        restartLoop(config)
    }

    override suspend fun refreshNow() {
        refreshQuotes(currentConfig.items)
    }

    override fun reconnect() {
        restartLoop(currentConfig)
    }

    override fun stop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun restartLoop(config: QuoteRefreshConfig) {
        refreshJob?.cancel()
        if (!config.enabled || config.items.isEmpty()) return

        refreshJob = scope.launch {
            refreshQuotes(config.items)
            while (isActive) {
                delay(config.refreshIntervalMillis)
                refreshQuotes(currentConfig.items)
            }
        }
    }

    private suspend fun refreshQuotes(items: List<WatchItem>) {
        if (items.isEmpty()) return
        refreshMutex.withLock {
            val quotes: List<MarketQuote> = marketQuoteRepository.fetchQuotes(items)
            if (quotes.isNotEmpty()) {
                watchlistRepository.updateQuotes(quotes)
            }
        }
    }
}
