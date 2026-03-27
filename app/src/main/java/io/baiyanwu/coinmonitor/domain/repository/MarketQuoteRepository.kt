package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.WatchItem

interface MarketQuoteRepository {
    suspend fun fetchQuotes(items: List<WatchItem>): List<MarketQuote>
}

