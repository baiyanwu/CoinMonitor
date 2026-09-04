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
import io.baiyanwu.coinmonitor.domain.model.ArrangedEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.ArrangedOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.MarqueeOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.MarqueeSpeed
import io.baiyanwu.coinmonitor.domain.model.OverlayDisplayType
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

    override suspend fun setDisplayType(displayType: OverlayDisplayType) {
        updateSettings { it.copy(displayType = displayType) }
    }

    override suspend fun setArrangedOpacity(opacity: Float) {
        updateSettings {
            it.copy(
                arranged = it.arranged.copy(
                    opacity = opacity.coerceIn(
                        minimumValue = ArrangedOverlaySettings.MIN_OPACITY,
                        maximumValue = ArrangedOverlaySettings.MAX_OPACITY
                    )
                )
            )
        }
    }

    override suspend fun setArrangedMaxCount(maxCount: Int) {
        updateSettings {
            it.copy(
                arranged = it.arranged.copy(
                    maxItems = maxCount.coerceIn(1, OverlaySettings.MAX_SELECTABLE_ITEMS)
                )
            )
        }
    }

    override suspend fun setArrangedLeadingDisplayMode(mode: OverlayLeadingDisplayMode) {
        updateSettings { it.copy(arranged = it.arranged.copy(leadingDisplayMode = mode)) }
    }

    override suspend fun setArrangedFontScale(fontScale: Float) {
        updateSettings {
            it.copy(
                arranged = it.arranged.copy(
                    fontScale = fontScale.coerceIn(
                        minimumValue = ArrangedOverlaySettings.MIN_FONT_SCALE,
                        maximumValue = ArrangedOverlaySettings.MAX_FONT_SCALE
                    )
                )
            )
        }
    }

    override suspend fun setArrangedSnapToEdge(enabled: Boolean) {
        updateSettings { it.copy(arranged = it.arranged.copy(snapToEdge = enabled)) }
    }

    override suspend fun setArrangedEdgeDisplayMode(mode: ArrangedEdgeDisplayMode) {
        updateSettings { it.copy(arranged = it.arranged.copy(edgeDisplayMode = mode)) }
    }

    override suspend fun setArrangedEdgeTabOpacity(opacity: Float) {
        updateSettings {
            it.copy(
                arranged = it.arranged.copy(
                    edgeTabOpacity = opacity.coerceIn(
                        minimumValue = ArrangedOverlaySettings.MIN_EDGE_TAB_OPACITY,
                        maximumValue = ArrangedOverlaySettings.MAX_EDGE_TAB_OPACITY
                    )
                )
            )
        }
    }

    override suspend fun setArrangedEdgeAutoCollapseSeconds(seconds: Int) {
        updateSettings {
            it.copy(
                arranged = it.arranged.copy(
                    edgeAutoCollapseSeconds = seconds.coerceIn(
                        minimumValue = ArrangedOverlaySettings.MIN_EDGE_AUTO_COLLAPSE_SECONDS,
                        maximumValue = ArrangedOverlaySettings.MAX_EDGE_AUTO_COLLAPSE_SECONDS
                    )
                )
            )
        }
    }

    override suspend fun setArrangedWindowPosition(x: Int, y: Int) {
        updateSettings { it.copy(arranged = it.arranged.copy(windowX = x, windowY = y)) }
    }

    override suspend fun setMarqueeOpacity(opacity: Float) {
        updateSettings {
            it.copy(
                marquee = it.marquee.copy(
                    opacity = opacity.coerceIn(
                        minimumValue = MarqueeOverlaySettings.MIN_OPACITY,
                        maximumValue = MarqueeOverlaySettings.MAX_OPACITY
                    )
                )
            )
        }
    }

    override suspend fun setMarqueeMaxCount(maxCount: Int) {
        updateSettings {
            it.copy(
                marquee = it.marquee.copy(
                    maxItems = maxCount.coerceIn(1, OverlaySettings.MAX_SELECTABLE_ITEMS)
                )
            )
        }
    }

    override suspend fun setMarqueeFontScale(fontScale: Float) {
        updateSettings {
            it.copy(
                marquee = it.marquee.copy(
                    fontScale = fontScale.coerceIn(
                        minimumValue = MarqueeOverlaySettings.MIN_FONT_SCALE,
                        maximumValue = MarqueeOverlaySettings.MAX_FONT_SCALE
                    )
                )
            )
        }
    }

    override suspend fun setMarqueeSpeed(speed: MarqueeSpeed) {
        updateSettings { it.copy(marquee = it.marquee.copy(speed = speed)) }
    }

    override suspend fun setMarqueeWindowY(y: Int) {
        updateSettings { it.copy(marquee = it.marquee.copy(windowY = y)) }
    }

    private suspend fun updateSettings(transform: (OverlaySettings) -> OverlaySettings) {
        overlayPreferences.edit { preferences ->
            preferences.writeSettings(transform(preferences.toOverlaySettings()))
        }
    }
}

internal fun Preferences.toOverlaySettings(): OverlaySettings {
    val arrangedWindowY = this[OverlayPreferenceKeys.windowY]
    return OverlaySettings(
        enabled = this[OverlayPreferenceKeys.enabled] ?: false,
        locked = this[OverlayPreferenceKeys.locked] ?: false,
        displayType = enumValueOrDefault(
            value = this[OverlayPreferenceKeys.displayType],
            default = OverlayDisplayType.ARRANGED
        ),
        arranged = ArrangedOverlaySettings(
            opacity = (
                this[OverlayPreferenceKeys.opacity] ?: ArrangedOverlaySettings.DEFAULT_OPACITY
                ).coerceIn(
                minimumValue = ArrangedOverlaySettings.MIN_OPACITY,
                maximumValue = ArrangedOverlaySettings.MAX_OPACITY
            ),
            maxItems = (
                this[OverlayPreferenceKeys.maxItems] ?: ArrangedOverlaySettings.DEFAULT_MAX_ITEMS
                ).coerceIn(1, OverlaySettings.MAX_SELECTABLE_ITEMS),
            leadingDisplayMode = enumValueOrDefault(
                value = this[OverlayPreferenceKeys.leadingDisplayMode],
                default = OverlayLeadingDisplayMode.ICON
            ),
            fontScale = (
                this[OverlayPreferenceKeys.fontScale]
                    ?: ArrangedOverlaySettings.DEFAULT_FONT_SCALE
                ).coerceIn(
                minimumValue = ArrangedOverlaySettings.MIN_FONT_SCALE,
                maximumValue = ArrangedOverlaySettings.MAX_FONT_SCALE
            ),
            snapToEdge = this[OverlayPreferenceKeys.snapToEdge] ?: false,
            edgeDisplayMode = arrangedEdgeDisplayMode(
                this[OverlayPreferenceKeys.edgeDisplayMode]
            ),
            edgeTabOpacity = (
                this[OverlayPreferenceKeys.edgeTabOpacity]
                    ?: ArrangedOverlaySettings.DEFAULT_EDGE_TAB_OPACITY
                ).coerceIn(
                minimumValue = ArrangedOverlaySettings.MIN_EDGE_TAB_OPACITY,
                maximumValue = ArrangedOverlaySettings.MAX_EDGE_TAB_OPACITY
            ),
            edgeAutoCollapseSeconds = (
                this[OverlayPreferenceKeys.edgeAutoCollapseSeconds]
                    ?: ArrangedOverlaySettings.DEFAULT_EDGE_AUTO_COLLAPSE_SECONDS
                ).coerceIn(
                minimumValue = ArrangedOverlaySettings.MIN_EDGE_AUTO_COLLAPSE_SECONDS,
                maximumValue = ArrangedOverlaySettings.MAX_EDGE_AUTO_COLLAPSE_SECONDS
            ),
            windowX = this[OverlayPreferenceKeys.windowX],
            windowY = arrangedWindowY
        ),
        marquee = MarqueeOverlaySettings(
            opacity = (
                this[OverlayPreferenceKeys.marqueeOpacity]
                    ?: MarqueeOverlaySettings.DEFAULT_OPACITY
                ).coerceIn(
                minimumValue = MarqueeOverlaySettings.MIN_OPACITY,
                maximumValue = MarqueeOverlaySettings.MAX_OPACITY
            ),
            maxItems = (
                this[OverlayPreferenceKeys.marqueeMaxItems]
                    ?: MarqueeOverlaySettings.DEFAULT_MAX_ITEMS
                ).coerceIn(1, OverlaySettings.MAX_SELECTABLE_ITEMS),
            fontScale = (
                this[OverlayPreferenceKeys.marqueeFontScale]
                    ?: MarqueeOverlaySettings.DEFAULT_FONT_SCALE
                ).coerceIn(
                minimumValue = MarqueeOverlaySettings.MIN_FONT_SCALE,
                maximumValue = MarqueeOverlaySettings.MAX_FONT_SCALE
            ),
            speed = enumValueOrDefault(
                value = this[OverlayPreferenceKeys.marqueeSpeed],
                default = MarqueeSpeed.NORMAL
            ),
            windowY = this[OverlayPreferenceKeys.marqueeWindowY] ?: arrangedWindowY
        )
    )
}

private fun MutablePreferences.writeSettings(settings: OverlaySettings) {
    this[OverlayPreferenceKeys.enabled] = settings.enabled
    this[OverlayPreferenceKeys.locked] = settings.locked
    this[OverlayPreferenceKeys.displayType] = settings.displayType.name
    this[OverlayPreferenceKeys.opacity] = settings.arranged.opacity
    this[OverlayPreferenceKeys.maxItems] = settings.arranged.maxItems
    this[OverlayPreferenceKeys.leadingDisplayMode] = settings.arranged.leadingDisplayMode.name
    this[OverlayPreferenceKeys.fontScale] = settings.arranged.fontScale
    this[OverlayPreferenceKeys.snapToEdge] = settings.arranged.snapToEdge
    this[OverlayPreferenceKeys.edgeDisplayMode] = settings.arranged.edgeDisplayMode.name
    this[OverlayPreferenceKeys.edgeTabOpacity] = settings.arranged.edgeTabOpacity
    this[OverlayPreferenceKeys.edgeAutoCollapseSeconds] =
        settings.arranged.edgeAutoCollapseSeconds
    settings.arranged.windowX?.let { this[OverlayPreferenceKeys.windowX] = it }
        ?: remove(OverlayPreferenceKeys.windowX)
    settings.arranged.windowY?.let { this[OverlayPreferenceKeys.windowY] = it }
        ?: remove(OverlayPreferenceKeys.windowY)
    this[OverlayPreferenceKeys.marqueeOpacity] = settings.marquee.opacity
    this[OverlayPreferenceKeys.marqueeMaxItems] = settings.marquee.maxItems
    this[OverlayPreferenceKeys.marqueeFontScale] = settings.marquee.fontScale
    this[OverlayPreferenceKeys.marqueeSpeed] = settings.marquee.speed.name
    settings.marquee.windowY?.let { this[OverlayPreferenceKeys.marqueeWindowY] = it }
        ?: remove(OverlayPreferenceKeys.marqueeWindowY)
}

private fun arrangedEdgeDisplayMode(value: String?): ArrangedEdgeDisplayMode {
    return when (value) {
        ArrangedEdgeDisplayMode.HIDDEN_TAB.name -> ArrangedEdgeDisplayMode.HIDDEN_TAB
        ArrangedEdgeDisplayMode.DOCKED.name, LEGACY_EDGE_TICKER_VALUE ->
            ArrangedEdgeDisplayMode.DOCKED
        else -> ArrangedEdgeDisplayMode.DOCKED
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
    return value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}

private const val LEGACY_EDGE_TICKER_VALUE = "TICKER"
