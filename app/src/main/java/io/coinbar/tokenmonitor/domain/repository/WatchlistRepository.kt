package io.coinbar.tokenmonitor.domain.repository

import io.coinbar.tokenmonitor.domain.model.MarketQuote
import io.coinbar.tokenmonitor.domain.model.WatchItem
import kotlinx.coroutines.flow.Flow

interface WatchlistRepository {
    fun observeWatchlist(): Flow<List<WatchItem>>
    suspend fun getWatchlist(): List<WatchItem>
    suspend fun add(item: WatchItem)
    suspend fun remove(id: String)
    suspend fun updateQuotes(quotes: List<MarketQuote>)
}

