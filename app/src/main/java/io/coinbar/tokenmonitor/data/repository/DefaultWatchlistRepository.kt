package io.coinbar.tokenmonitor.data.repository

import io.coinbar.tokenmonitor.data.local.dao.WatchItemDao
import io.coinbar.tokenmonitor.data.local.toDomain
import io.coinbar.tokenmonitor.data.local.toEntity
import io.coinbar.tokenmonitor.domain.model.LivePriceTrend
import io.coinbar.tokenmonitor.domain.model.MarketQuote
import io.coinbar.tokenmonitor.domain.model.WatchItem
import io.coinbar.tokenmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultWatchlistRepository(
    private val watchItemDao: WatchItemDao
) : WatchlistRepository {
    override fun observeWatchlist(): Flow<List<WatchItem>> {
        return watchItemDao.observeWatchItems().map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getWatchlist(): List<WatchItem> {
        return watchItemDao.getWatchItems().map { it.toDomain() }
    }

    override suspend fun add(item: WatchItem) {
        val existing = watchItemDao.findById(item.id)
        watchItemDao.upsert(
            item.copy(
                overlaySelected = existing?.overlaySelected ?: item.overlaySelected,
                lastPrice = existing?.lastPrice ?: item.lastPrice,
                previousPrice = existing?.previousPrice ?: item.previousPrice,
                liveTrend = existing?.liveTrend?.let(LivePriceTrend::valueOf) ?: item.liveTrend,
                change24hPercent = existing?.change24hPercent ?: item.change24hPercent,
                lastUpdatedAt = existing?.lastUpdatedAt ?: item.lastUpdatedAt
            ).toEntity()
        )
    }

    override suspend fun remove(id: String) {
        watchItemDao.deleteById(id)
    }

    override suspend fun updateQuotes(quotes: List<MarketQuote>) {
        val now = System.currentTimeMillis()
        quotes.forEach { quote ->
            val existing = watchItemDao.findById(quote.id)
            val existingTrend = existing?.liveTrend?.let(LivePriceTrend::valueOf) ?: LivePriceTrend.NEUTRAL
            val liveTrend = when {
                existing?.lastPrice == null -> existingTrend
                quote.priceUsd > existing.lastPrice -> LivePriceTrend.UP
                quote.priceUsd < existing.lastPrice -> LivePriceTrend.DOWN
                else -> existingTrend
            }
            watchItemDao.updateQuote(
                id = quote.id,
                lastPrice = quote.priceUsd,
                previousPrice = existing?.lastPrice,
                liveTrend = liveTrend.name,
                change24hPercent = quote.change24hPercent,
                lastUpdatedAt = now
            )
        }
    }
}
