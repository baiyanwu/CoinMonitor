package io.coinbar.tokenmonitor.data.repository

import io.coinbar.tokenmonitor.data.local.OverlaySettingsEntity
import io.coinbar.tokenmonitor.data.local.dao.OverlaySettingsDao
import io.coinbar.tokenmonitor.data.local.dao.WatchItemDao
import io.coinbar.tokenmonitor.data.local.toDomain
import io.coinbar.tokenmonitor.domain.model.OverlayLeadingDisplayMode
import io.coinbar.tokenmonitor.domain.model.OverlaySettings
import io.coinbar.tokenmonitor.domain.model.WatchItem
import io.coinbar.tokenmonitor.domain.repository.OverlayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultOverlayRepository(
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
        watchItemDao.updateOverlaySelected(id, !item.overlaySelected)
    }

    override suspend fun setLocked(locked: Boolean) {
        updateSettings { it.copy(locked = locked) }
    }

    override suspend fun setOpacity(opacity: Float) {
        updateSettings { it.copy(opacity = opacity.coerceIn(0.16f, 0.72f)) }
    }

    override suspend fun setMaxCount(maxCount: Int) {
        updateSettings { it.copy(maxItems = maxCount.coerceIn(1, 10)) }
    }

    override suspend fun setLeadingDisplayMode(mode: OverlayLeadingDisplayMode) {
        updateSettings { it.copy(leadingDisplayMode = mode) }
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
                windowX = next.windowX,
                windowY = next.windowY
            )
        )
    }
}
