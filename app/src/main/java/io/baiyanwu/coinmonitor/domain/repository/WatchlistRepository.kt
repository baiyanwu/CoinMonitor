package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.QuoteState
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import kotlinx.coroutines.flow.Flow

interface WatchlistRepository {
    fun observeWatchlist(): Flow<List<WatchItem>>
    fun observeHomeWatchlist(): Flow<List<WatchItem>>
    suspend fun getWatchlist(): List<WatchItem>
    suspend fun add(item: WatchItem)
    suspend fun remove(id: String)
    suspend fun setHomePinned(id: String, pinned: Boolean)
    suspend fun moveHomeItem(id: String, targetBeforeId: String?)
    suspend fun movePinnedHomeItem(id: String, targetBeforeId: String?)
    suspend fun updateQuotes(quotes: List<MarketQuote>)
    suspend fun persistQuoteSnapshot(quotes: Map<String, QuoteState>)
}
