package io.baiyanwu.coinmonitor.data.repository

import androidx.room.withTransaction
import io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase
import io.baiyanwu.coinmonitor.data.local.dao.WatchItemDao
import io.baiyanwu.coinmonitor.data.local.toDomain
import io.baiyanwu.coinmonitor.data.local.toEntity
import io.baiyanwu.coinmonitor.domain.model.LivePriceTrend
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.QuoteState
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultWatchlistRepository(
    private val database: CoinMonitorDatabase
) : WatchlistRepository {
    private val watchItemDao: WatchItemDao = database.watchItemDao()

    override fun observeWatchlist(): Flow<List<WatchItem>> {
        return watchItemDao.observeWatchItems().map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeHomeWatchlist(): Flow<List<WatchItem>> {
        return watchItemDao.observeHomeOrderedWatchItems().map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getWatchlist(): List<WatchItem> {
        return watchItemDao.getWatchItems().map { it.toDomain() }
    }

    override suspend fun add(item: WatchItem) {
        database.withTransaction {
            val existing = watchItemDao.findById(item.id)
            val allItems = watchItemDao.getWatchItems()
            watchItemDao.upsert(
                item.copy(
                    overlaySelected = existing?.overlaySelected ?: item.overlaySelected,
                    homePinned = existing?.homePinned ?: false,
                    homeOrder = existing?.homeOrder ?: WatchlistHomeOrderManager.nextNormalOrder(allItems),
                    homePinnedOrder = existing?.homePinnedOrder,
                    lastPrice = existing?.lastPrice ?: item.lastPrice,
                    previousPrice = existing?.previousPrice ?: item.previousPrice,
                    liveTrend = existing?.liveTrend?.let(LivePriceTrend::valueOf) ?: item.liveTrend,
                    change24hPercent = existing?.change24hPercent ?: item.change24hPercent,
                    lastUpdatedAt = existing?.lastUpdatedAt ?: item.lastUpdatedAt
                ).toEntity()
            )
        }
    }

    override suspend fun remove(id: String) {
        watchItemDao.deleteById(id)
    }

    override suspend fun setHomePinned(id: String, pinned: Boolean) {
        database.withTransaction {
            val allItems = watchItemDao.getWatchItems()
            val target = allItems.firstOrNull { it.id == id } ?: return@withTransaction
            if (target.homePinned == pinned) return@withTransaction
            val pinnedOrder = if (pinned) {
                WatchlistHomeOrderManager.nextPinnedOrder(allItems)
            } else {
                null
            }
            watchItemDao.updateHomePinnedState(
                id = id,
                pinned = pinned,
                homePinnedOrder = pinnedOrder
            )
        }
    }

    override suspend fun moveHomeItem(id: String, targetBeforeId: String?) {
        database.withTransaction {
            val allItems = watchItemDao.getWatchItems()
            val target = allItems.firstOrNull { it.id == id } ?: return@withTransaction
            if (target.homePinned) return@withTransaction
            WatchlistHomeOrderManager.reorderNormalGroup(
                items = allItems,
                itemId = id,
                targetBeforeId = targetBeforeId
            ).forEach { update ->
                watchItemDao.updateHomeOrder(id = update.id, homeOrder = update.order)
            }
        }
    }

    override suspend fun movePinnedHomeItem(id: String, targetBeforeId: String?) {
        database.withTransaction {
            val allItems = watchItemDao.getWatchItems()
            val target = allItems.firstOrNull { it.id == id } ?: return@withTransaction
            if (!target.homePinned) return@withTransaction
            WatchlistHomeOrderManager.reorderPinnedGroup(
                items = allItems,
                itemId = id,
                targetBeforeId = targetBeforeId
            ).forEach { update ->
                watchItemDao.updateHomePinnedOrder(id = update.id, homePinnedOrder = update.order)
            }
        }
    }

    override suspend fun updateQuotes(quotes: List<MarketQuote>) {
        if (quotes.isEmpty()) return
        val snapshot = quotes.associate { quote ->
            quote.id to QuoteState(
                lastPrice = quote.priceUsd,
                change24hPercent = quote.change24hPercent,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
        persistQuoteSnapshot(snapshot)
    }

    override suspend fun persistQuoteSnapshot(quotes: Map<String, QuoteState>) {
        if (quotes.isEmpty()) return
        val now = System.currentTimeMillis()
        val existingById = watchItemDao.getWatchItems().associateBy { it.id }
        quotes.forEach { (id, quote) ->
            val existing = existingById[id] ?: return@forEach
            val existingTrend = LivePriceTrend.valueOf(existing.liveTrend)
            val liveTrend = when {
                existing.lastPrice == null -> quote.liveTrend.takeIf { it != LivePriceTrend.NEUTRAL } ?: existingTrend
                quote.lastPrice > existing.lastPrice -> LivePriceTrend.UP
                quote.lastPrice < existing.lastPrice -> LivePriceTrend.DOWN
                else -> existingTrend
            }
            watchItemDao.updateQuote(
                id = id,
                lastPrice = quote.lastPrice,
                previousPrice = existing.lastPrice,
                liveTrend = liveTrend.name,
                change24hPercent = quote.change24hPercent,
                lastUpdatedAt = quote.lastUpdatedAt ?: now
            )
        }
    }
}
