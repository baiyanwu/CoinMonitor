package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import kotlinx.coroutines.flow.Flow

interface OverlayRepository {
    fun observeSettings(): Flow<OverlaySettings>
    fun observeOverlayItems(): Flow<List<WatchItem>>
    suspend fun getSettings(): OverlaySettings
    suspend fun setEnabled(enabled: Boolean)
    suspend fun toggleItem(id: String)
    suspend fun setLocked(locked: Boolean)
    suspend fun setOpacity(opacity: Float)
    suspend fun setMaxCount(maxCount: Int)
    suspend fun setLeadingDisplayMode(mode: OverlayLeadingDisplayMode)
    suspend fun setFontScale(fontScale: Float)
    suspend fun setSnapToEdge(enabled: Boolean)
    suspend fun setWindowPosition(x: Int, y: Int)
}
