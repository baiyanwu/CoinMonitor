package io.coinbar.tokenmonitor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.coinbar.tokenmonitor.data.local.OverlaySettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OverlaySettingsDao {
    @Query("SELECT * FROM overlay_settings WHERE id = :id LIMIT 1")
    fun observeById(id: Int = OverlaySettingsEntity.DEFAULT_ID): Flow<OverlaySettingsEntity?>

    @Query("SELECT * FROM overlay_settings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int = OverlaySettingsEntity.DEFAULT_ID): OverlaySettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OverlaySettingsEntity)
}

