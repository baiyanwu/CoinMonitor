package io.baiyanwu.coinmonitor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.baiyanwu.coinmonitor.data.local.DappSearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DappSearchHistoryDao {
    @Query("SELECT * FROM dapp_search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DappSearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DappSearchHistoryEntity)

    @Query("DELETE FROM dapp_search_history")
    suspend fun clear()

    @Query(
        """
        DELETE FROM dapp_search_history
        WHERE normalizedQuery NOT IN (
            SELECT normalizedQuery
            FROM dapp_search_history
            ORDER BY searchedAt DESC
            LIMIT :limit
        )
        """
    )
    suspend fun trimTo(limit: Int)
}
