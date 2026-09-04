package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope

internal object OverlayPreferencesContract {
    const val DATA_STORE_FILE_NAME = "overlay_preferences"
    const val LEGACY_SHARED_PREFERENCES_NAME = "coin_monitor_overlay_preferences"

    const val ENABLED = "enabled"
    const val LOCKED = "locked"
    const val DISPLAY_TYPE = "display_type"
    const val OPACITY_RANGE_VERSION = "opacity_range_version"
    // These legacy names remain the arranged-window keys for upgrade compatibility.
    const val OPACITY = "opacity"
    const val MAX_ITEMS = "max_items"
    const val LEADING_DISPLAY_MODE = "leading_display_mode"
    const val FONT_SCALE = "font_scale"
    const val SNAP_TO_EDGE = "snap_to_edge"
    const val EDGE_DISPLAY_MODE = "edge_display_mode"
    const val EDGE_TAB_OPACITY = "edge_tab_opacity"
    const val EDGE_AUTO_COLLAPSE_SECONDS = "edge_auto_collapse_seconds"
    const val WINDOW_X = "window_x"
    const val WINDOW_Y = "window_y"
    const val MARQUEE_OPACITY = "marquee_opacity"
    const val MARQUEE_MAX_ITEMS = "marquee_max_items"
    const val MARQUEE_FONT_SCALE = "marquee_font_scale"
    const val MARQUEE_SPEED = "marquee_speed"
    const val MARQUEE_WINDOW_Y = "marquee_window_y"
}

internal object OverlayPreferenceKeys {
    val enabled = booleanPreferencesKey(OverlayPreferencesContract.ENABLED)
    val locked = booleanPreferencesKey(OverlayPreferencesContract.LOCKED)
    val displayType = stringPreferencesKey(OverlayPreferencesContract.DISPLAY_TYPE)
    val opacityRangeVersion = intPreferencesKey(OverlayPreferencesContract.OPACITY_RANGE_VERSION)
    val opacity = floatPreferencesKey(OverlayPreferencesContract.OPACITY)
    val maxItems = intPreferencesKey(OverlayPreferencesContract.MAX_ITEMS)
    val leadingDisplayMode = stringPreferencesKey(OverlayPreferencesContract.LEADING_DISPLAY_MODE)
    val fontScale = floatPreferencesKey(OverlayPreferencesContract.FONT_SCALE)
    val snapToEdge = booleanPreferencesKey(OverlayPreferencesContract.SNAP_TO_EDGE)
    val edgeDisplayMode = stringPreferencesKey(OverlayPreferencesContract.EDGE_DISPLAY_MODE)
    val edgeTabOpacity = floatPreferencesKey(OverlayPreferencesContract.EDGE_TAB_OPACITY)
    val edgeAutoCollapseSeconds = intPreferencesKey(
        OverlayPreferencesContract.EDGE_AUTO_COLLAPSE_SECONDS
    )
    val windowX = intPreferencesKey(OverlayPreferencesContract.WINDOW_X)
    val windowY = intPreferencesKey(OverlayPreferencesContract.WINDOW_Y)
    val marqueeOpacity = floatPreferencesKey(OverlayPreferencesContract.MARQUEE_OPACITY)
    val marqueeMaxItems = intPreferencesKey(OverlayPreferencesContract.MARQUEE_MAX_ITEMS)
    val marqueeFontScale = floatPreferencesKey(OverlayPreferencesContract.MARQUEE_FONT_SCALE)
    val marqueeSpeed = stringPreferencesKey(OverlayPreferencesContract.MARQUEE_SPEED)
    val marqueeWindowY = intPreferencesKey(OverlayPreferencesContract.MARQUEE_WINDOW_Y)
}

internal fun createOverlayPreferencesDataStore(
    context: Context,
    scope: CoroutineScope
): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        migrations = listOf(
            SharedPreferencesMigration(
                context = context,
                sharedPreferencesName = OverlayPreferencesContract.LEGACY_SHARED_PREFERENCES_NAME
            )
        ),
        scope = scope,
        produceFile = {
            context.preferencesDataStoreFile(OverlayPreferencesContract.DATA_STORE_FILE_NAME)
        }
    )
}
