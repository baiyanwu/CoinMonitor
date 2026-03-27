package io.baiyanwu.coinmonitor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.baiyanwu.coinmonitor.data.local.OverlaySettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OverlaySettingsDao {
    @Query("SELECT * FROM overlay_settings WHERE id = :id LIMIT 1")
    fun observeById(id: Int = OverlaySettingsEntity.Companion.DEFAULT_ID): Flow<OverlaySettingsEntity?>

    @Query("SELECT * FROM overlay_settings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int = OverlaySettingsEntity.Companion.DEFAULT_ID): OverlaySettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OverlaySettingsEntity)
}

