package io.baiyanwu.coinmonitor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.baiyanwu.coinmonitor.data.local.WatchItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchItemDao {
    @Query("SELECT * FROM watch_items ORDER BY homeOrder ASC, addedAt ASC, id ASC")
    fun observeWatchItems(): Flow<List<WatchItemEntity>>

    @Query("SELECT * FROM watch_items ORDER BY homeOrder ASC, addedAt ASC, id ASC")
    suspend fun getWatchItems(): List<WatchItemEntity>

    @Query(
        """
        SELECT * FROM watch_items
        ORDER BY
            homePinned DESC,
            CASE WHEN homePinned = 1 THEN homePinnedOrder END ASC,
            CASE WHEN homePinned = 0 THEN homeOrder END ASC,
            addedAt ASC,
            id ASC
        """
    )
    fun observeHomeOrderedWatchItems(): Flow<List<WatchItemEntity>>

    @Query(
        """
        SELECT * FROM watch_items
        WHERE overlaySelected = 1
        ORDER BY
            CASE WHEN overlayOrder IS NULL THEN 1 ELSE 0 END ASC,
            overlayOrder ASC,
            addedAt ASC,
            id ASC
        """
    )
    fun observeOverlayItems(): Flow<List<WatchItemEntity>>

    @Query(
        """
        SELECT * FROM watch_items
        WHERE overlaySelected = 1
        ORDER BY
            CASE WHEN overlayOrder IS NULL THEN 1 ELSE 0 END ASC,
            overlayOrder ASC,
            addedAt ASC,
            id ASC
        """
    )
    suspend fun getOverlayItems(): List<WatchItemEntity>

    @Query("SELECT * FROM watch_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): WatchItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchItemEntity)

    @Query("DELETE FROM watch_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        UPDATE watch_items
        SET overlaySelected = :selected,
            overlayOrder = :overlayOrder
        WHERE id = :id
        """
    )
    suspend fun updateOverlaySelection(id: String, selected: Boolean, overlayOrder: Long?)

    @Query("UPDATE watch_items SET overlayOrder = :overlayOrder WHERE id = :id")
    suspend fun updateOverlayOrder(id: String, overlayOrder: Long)

    @Query(
        """
        UPDATE watch_items
        SET homePinned = :pinned,
            homePinnedOrder = :homePinnedOrder
        WHERE id = :id
        """
    )
    suspend fun updateHomePinnedState(
        id: String,
        pinned: Boolean,
        homePinnedOrder: Long?
    )

    @Query("UPDATE watch_items SET homeOrder = :homeOrder WHERE id = :id")
    suspend fun updateHomeOrder(id: String, homeOrder: Long)

    @Query("UPDATE watch_items SET homePinnedOrder = :homePinnedOrder WHERE id = :id")
    suspend fun updateHomePinnedOrder(id: String, homePinnedOrder: Long)

    @Query(
        """
        UPDATE watch_items
        SET lastPrice = :lastPrice,
            previousPrice = :previousPrice,
            liveTrend = :liveTrend,
            change24hPercent = :change24hPercent,
            lastUpdatedAt = :lastUpdatedAt
        WHERE id = :id
        """
    )
    suspend fun updateQuote(
        id: String,
        lastPrice: Double,
        previousPrice: Double?,
        liveTrend: String,
        change24hPercent: Double?,
        lastUpdatedAt: Long
    )

    @Query("UPDATE watch_items SET symbol = :symbol WHERE id = :id AND symbol != :symbol")
    suspend fun updateSymbol(id: String, symbol: String)

    @Query(
        """
        UPDATE watch_items
        SET poolAddress = :poolAddress,
            poolTokenSide = :poolTokenSide,
            lastPrice = NULL,
            previousPrice = NULL,
            liveTrend = 'NEUTRAL',
            change24hPercent = NULL,
            lastUpdatedAt = NULL
        WHERE id = :id
          AND (poolAddress IS NOT :poolAddress OR poolTokenSide IS NOT :poolTokenSide)
        """
    )
    suspend fun updateOnchainPoolBinding(
        id: String,
        poolAddress: String,
        poolTokenSide: String
    ): Int
}
