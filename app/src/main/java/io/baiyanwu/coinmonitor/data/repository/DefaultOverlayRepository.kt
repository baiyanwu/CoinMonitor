package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.local.dao.WatchItemDao
import io.baiyanwu.coinmonitor.data.local.toDomain
import io.baiyanwu.coinmonitor.domain.model.OverlayEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DefaultOverlayRepository(
    private val context: Context,
    private val overlayPreferences: DataStore<Preferences>,
    private val watchItemDao: WatchItemDao
) : OverlayRepository {
    override fun observeSettings(): Flow<OverlaySettings> {
        return overlayPreferences.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map(Preferences::toOverlaySettings)
            .distinctUntilChanged()
    }

    override fun observeOverlayItems(): Flow<List<WatchItem>> {
        return watchItemDao.observeOverlayItems().map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getSettings(): OverlaySettings = observeSettings().first()

    override suspend fun setEnabled(enabled: Boolean) {
        updateSettings { it.copy(enabled = enabled) }
    }

    override suspend fun toggleItem(id: String) {
        val item = watchItemDao.findById(id) ?: return
        if (!item.overlaySelected) {
            val selectedCount = watchItemDao.getWatchItems().count { it.overlaySelected }
            if (selectedCount >= OverlaySettings.MAX_SELECTABLE_ITEMS) {
                throw IllegalStateException(
                    context.getString(
                        R.string.overlay_select_limit_reached,
                        OverlaySettings.MAX_SELECTABLE_ITEMS
                    )
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
        updateSettings {
            it.copy(maxItems = maxCount.coerceIn(1, OverlaySettings.MAX_SELECTABLE_ITEMS))
        }
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

    override suspend fun setEdgeDisplayMode(mode: OverlayEdgeDisplayMode) {
        updateSettings { it.copy(edgeDisplayMode = mode) }
    }

    override suspend fun setEdgeTabOpacity(opacity: Float) {
        updateSettings {
            it.copy(
                edgeTabOpacity = opacity.coerceIn(
                    minimumValue = OverlaySettings.MIN_EDGE_TAB_OPACITY,
                    maximumValue = OverlaySettings.MAX_EDGE_TAB_OPACITY
                )
            )
        }
    }

    override suspend fun setEdgeAutoCollapseSeconds(seconds: Int) {
        updateSettings {
            it.copy(
                edgeAutoCollapseSeconds = seconds.coerceIn(
                    minimumValue = OverlaySettings.MIN_EDGE_AUTO_COLLAPSE_SECONDS,
                    maximumValue = OverlaySettings.MAX_EDGE_AUTO_COLLAPSE_SECONDS
                )
            )
        }
    }

    override suspend fun setWindowPosition(x: Int, y: Int) {
        updateSettings { it.copy(windowX = x, windowY = y) }
    }

    private suspend fun updateSettings(transform: (OverlaySettings) -> OverlaySettings) {
        overlayPreferences.edit { preferences ->
            preferences.writeSettings(transform(preferences.toOverlaySettings()))
        }
    }
}

internal fun Preferences.toOverlaySettings(): OverlaySettings {
    return OverlaySettings(
        enabled = this[OverlayPreferenceKeys.enabled] ?: false,
        locked = this[OverlayPreferenceKeys.locked] ?: false,
        opacity = (this[OverlayPreferenceKeys.opacity] ?: OverlaySettings.DEFAULT_OPACITY).coerceIn(
            minimumValue = OverlaySettings.MIN_OPACITY,
            maximumValue = OverlaySettings.MAX_OPACITY
        ),
        maxItems = (
            this[OverlayPreferenceKeys.maxItems] ?: OverlaySettings.DEFAULT_MAX_ITEMS
            ).coerceIn(
            minimumValue = 1,
            maximumValue = OverlaySettings.MAX_SELECTABLE_ITEMS
        ),
        leadingDisplayMode = enumValueOrDefault(
            value = this[OverlayPreferenceKeys.leadingDisplayMode],
            default = OverlayLeadingDisplayMode.ICON
        ),
        fontScale = (
            this[OverlayPreferenceKeys.fontScale] ?: OverlaySettings.DEFAULT_FONT_SCALE
            ).coerceIn(
            minimumValue = OverlaySettings.MIN_FONT_SCALE,
            maximumValue = OverlaySettings.MAX_FONT_SCALE
        ),
        snapToEdge = this[OverlayPreferenceKeys.snapToEdge] ?: false,
        edgeDisplayMode = enumValueOrDefault(
            value = this[OverlayPreferenceKeys.edgeDisplayMode],
            default = OverlayEdgeDisplayMode.TICKER
        ),
        edgeTabOpacity = (
            this[OverlayPreferenceKeys.edgeTabOpacity]
                ?: OverlaySettings.DEFAULT_EDGE_TAB_OPACITY
            ).coerceIn(
            minimumValue = OverlaySettings.MIN_EDGE_TAB_OPACITY,
            maximumValue = OverlaySettings.MAX_EDGE_TAB_OPACITY
        ),
        edgeAutoCollapseSeconds = (
            this[OverlayPreferenceKeys.edgeAutoCollapseSeconds]
                ?: OverlaySettings.DEFAULT_EDGE_AUTO_COLLAPSE_SECONDS
            ).coerceIn(
            minimumValue = OverlaySettings.MIN_EDGE_AUTO_COLLAPSE_SECONDS,
            maximumValue = OverlaySettings.MAX_EDGE_AUTO_COLLAPSE_SECONDS
        ),
        windowX = this[OverlayPreferenceKeys.windowX],
        windowY = this[OverlayPreferenceKeys.windowY]
    )
}

private fun MutablePreferences.writeSettings(settings: OverlaySettings) {
    this[OverlayPreferenceKeys.enabled] = settings.enabled
    this[OverlayPreferenceKeys.locked] = settings.locked
    this[OverlayPreferenceKeys.opacity] = settings.opacity
    this[OverlayPreferenceKeys.maxItems] = settings.maxItems
    this[OverlayPreferenceKeys.leadingDisplayMode] = settings.leadingDisplayMode.name
    this[OverlayPreferenceKeys.fontScale] = settings.fontScale
    this[OverlayPreferenceKeys.snapToEdge] = settings.snapToEdge
    this[OverlayPreferenceKeys.edgeDisplayMode] = settings.edgeDisplayMode.name
    this[OverlayPreferenceKeys.edgeTabOpacity] = settings.edgeTabOpacity
    this[OverlayPreferenceKeys.edgeAutoCollapseSeconds] = settings.edgeAutoCollapseSeconds
    settings.windowX?.let { this[OverlayPreferenceKeys.windowX] = it }
        ?: remove(OverlayPreferenceKeys.windowX)
    settings.windowY?.let { this[OverlayPreferenceKeys.windowY] = it }
        ?: remove(OverlayPreferenceKeys.windowY)
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
    return value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
