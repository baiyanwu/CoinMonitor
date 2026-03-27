package io.coinbar.tokenmonitor.domain.repository

import io.coinbar.tokenmonitor.domain.model.MarketQuote
import io.coinbar.tokenmonitor.domain.model.WatchItem

interface MarketQuoteRepository {
    suspend fun fetchQuotes(items: List<WatchItem>): List<MarketQuote>
}

