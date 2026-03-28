package io.baiyanwu.coinmonitor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.baiyanwu.coinmonitor.data.local.WatchItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchItemDao {
    @Query("SELECT * FROM watch_items ORDER BY addedAt ASC")
    fun observeWatchItems(): Flow<List<WatchItemEntity>>

    @Query("SELECT * FROM watch_items ORDER BY addedAt ASC")
    suspend fun getWatchItems(): List<WatchItemEntity>

    @Query("SELECT * FROM watch_items WHERE overlaySelected = 1 ORDER BY addedAt ASC")
    fun observeOverlayItems(): Flow<List<WatchItemEntity>>

    @Query("SELECT * FROM watch_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): WatchItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchItemEntity)

    @Query("DELETE FROM watch_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE watch_items SET overlaySelected = :selected WHERE id = :id")
    suspend fun updateOverlaySelected(id: String, selected: Boolean)

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
}
