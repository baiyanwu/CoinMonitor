package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.ArrangedEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.MarqueeSpeed
import io.baiyanwu.coinmonitor.domain.model.OverlayDisplayType
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
    suspend fun moveOverlayItem(id: String, targetBeforeId: String?)
    suspend fun setLocked(locked: Boolean)
    suspend fun setDisplayType(displayType: OverlayDisplayType)
    suspend fun setArrangedOpacity(opacity: Float)
    suspend fun setArrangedMaxCount(maxCount: Int)
    suspend fun setArrangedLeadingDisplayMode(mode: OverlayLeadingDisplayMode)
    suspend fun setArrangedFontScale(fontScale: Float)
    suspend fun setArrangedSnapToEdge(enabled: Boolean)
    suspend fun setArrangedEdgeDisplayMode(mode: ArrangedEdgeDisplayMode)
    suspend fun setArrangedEdgeTabOpacity(opacity: Float)
    suspend fun setArrangedEdgeAutoCollapseSeconds(seconds: Int)
    suspend fun setArrangedWindowPosition(x: Int, y: Int)
    suspend fun setMarqueeOpacity(opacity: Float)
    suspend fun setMarqueeMaxCount(maxCount: Int)
    suspend fun setMarqueeFontScale(fontScale: Float)
    suspend fun setMarqueeSpeed(speed: MarqueeSpeed)
    suspend fun setMarqueeWindowY(y: Int)
}
