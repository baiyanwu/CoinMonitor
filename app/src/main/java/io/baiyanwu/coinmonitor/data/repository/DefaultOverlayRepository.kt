package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.local.OverlaySettingsEntity
import io.baiyanwu.coinmonitor.data.local.dao.OverlaySettingsDao
import io.baiyanwu.coinmonitor.data.local.dao.WatchItemDao
import io.baiyanwu.coinmonitor.data.local.toDomain
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultOverlayRepository(
    private val context: Context,
    private val overlaySettingsDao: OverlaySettingsDao,
    private val watchItemDao: WatchItemDao
) : OverlayRepository {
    override fun observeSettings(): Flow<OverlaySettings> {
        return overlaySettingsDao.observeById().map { it?.toDomain() ?: OverlaySettings() }
    }

    override fun observeOverlayItems(): Flow<List<WatchItem>> {
        return watchItemDao.observeOverlayItems().map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getSettings(): OverlaySettings {
        return overlaySettingsDao.getById()?.toDomain() ?: OverlaySettings()
    }

    override suspend fun setEnabled(enabled: Boolean) {
        updateSettings { it.copy(enabled = enabled) }
    }

    override suspend fun toggleItem(id: String) {
        val item = watchItemDao.findById(id) ?: return
        if (!item.overlaySelected) {
            val selectedCount = watchItemDao.getWatchItems().count { it.overlaySelected }
            if (selectedCount >= OverlaySettings.MAX_SELECTABLE_ITEMS) {
                throw IllegalStateException(
                    context.getString(R.string.overlay_select_limit_reached, OverlaySettings.MAX_SELECTABLE_ITEMS)
                )
            }
        }
        watchItemDao.updateOverlaySelected(id, !item.overlaySelected)
    }

    override suspend fun setLocked(locked: Boolean) {
        updateSettings { it.copy(locked = locked) }
    }

    override suspend fun setOpacity(opacity: Float) {
        updateSettings {
            it.copy(
                opacity = opacity.coerceIn(
                    minimumValue = OverlaySettings.MIN_OPACITY,
                    maximumValue = OverlaySettings.MAX_OPACITY
                )
            )
        }
    }

    override suspend fun setMaxCount(maxCount: Int) {
        updateSettings { it.copy(maxItems = maxCount.coerceIn(1, OverlaySettings.MAX_SELECTABLE_ITEMS)) }
    }

    override suspend fun setLeadingDisplayMode(mode: OverlayLeadingDisplayMode) {
        updateSettings { it.copy(leadingDisplayMode = mode) }
    }

    override suspend fun setFontScale(fontScale: Float) {
        updateSettings {
            it.copy(
                fontScale = fontScale.coerceIn(
                    minimumValue = OverlaySettings.MIN_FONT_SCALE,
                    maximumValue = OverlaySettings.MAX_FONT_SCALE
                )
            )
        }
    }

    override suspend fun setSnapToEdge(enabled: Boolean) {
        updateSettings { it.copy(snapToEdge = enabled) }
    }

    override suspend fun setWindowPosition(x: Int, y: Int) {
        updateSettings { it.copy(windowX = x, windowY = y) }
    }

    private suspend fun updateSettings(transform: (OverlaySettings) -> OverlaySettings) {
        val current = overlaySettingsDao.getById()?.toDomain() ?: OverlaySettings()
        val next = transform(current)
        overlaySettingsDao.upsert(
            OverlaySettingsEntity(
                enabled = next.enabled,
                locked = next.locked,
                opacity = next.opacity,
                maxItems = next.maxItems,
                leadingDisplayMode = next.leadingDisplayMode.name,
                fontScale = next.fontScale,
                snapToEdge = next.snapToEdge,
                windowX = next.windowX,
                windowY = next.windowY
            )
        )
    }
}
