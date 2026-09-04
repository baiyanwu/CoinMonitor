package io.baiyanwu.coinmonitor.overlay

import android.content.Context
import io.baiyanwu.coinmonitor.domain.model.ArrangedOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.MarqueeOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.OverlayDisplayType
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.overlay.arranged.ArrangedOverlayWindowController
import io.baiyanwu.coinmonitor.overlay.marquee.MarqueeOverlayWindowController
import kotlinx.coroutines.CoroutineScope

internal interface ArrangedOverlayWindowHost {
    fun showOrUpdate(
        items: List<WatchItem>,
        locked: Boolean,
        settings: ArrangedOverlaySettings
    )

    fun hide()
}

internal interface MarqueeOverlayWindowHost {
    fun showOrUpdate(
        items: List<WatchItem>,
        locked: Boolean,
        settings: MarqueeOverlaySettings
    )

    fun hide()
}

/**
 * Chooses the active overlay implementation. Rendering, touch handling and animation remain inside
 * the arranged and marquee packages so the two window types never share mutable view state.
 */
class OverlayWindowController internal constructor(
    private val arrangedWindow: ArrangedOverlayWindowHost,
    private val marqueeWindow: MarqueeOverlayWindowHost
) {
    constructor(
        context: Context,
        overlayRepository: OverlayRepository,
        appPreferencesRepository: AppPreferencesRepository,
        scope: CoroutineScope
    ) : this(
        arrangedWindow = ArrangedOverlayWindowController(
            context = context,
            overlayRepository = overlayRepository,
            appPreferencesRepository = appPreferencesRepository,
            scope = scope
        ),
        marqueeWindow = MarqueeOverlayWindowController(
            context = context,
            overlayRepository = overlayRepository,
            appPreferencesRepository = appPreferencesRepository,
            scope = scope
        )
    )

    private var activeDisplayType: OverlayDisplayType? = null

    fun showOrUpdate(items: List<WatchItem>, settings: OverlaySettings) {
        switchTo(settings.displayType)
        when (settings.displayType) {
            OverlayDisplayType.ARRANGED -> arrangedWindow.showOrUpdate(
                items = items,
                locked = settings.locked,
                settings = settings.arranged
            )

            OverlayDisplayType.MARQUEE -> marqueeWindow.showOrUpdate(
                items = items,
                locked = settings.locked,
                settings = settings.marquee
            )
        }
    }

    fun hide() {
        arrangedWindow.hide()
        marqueeWindow.hide()
        activeDisplayType = null
    }

    private fun switchTo(displayType: OverlayDisplayType) {
        if (activeDisplayType == displayType) return
        when (activeDisplayType) {
            OverlayDisplayType.ARRANGED -> arrangedWindow.hide()
            OverlayDisplayType.MARQUEE -> marqueeWindow.hide()
            null -> when (displayType) {
                OverlayDisplayType.ARRANGED -> marqueeWindow.hide()
                OverlayDisplayType.MARQUEE -> arrangedWindow.hide()
            }
        }
        activeDisplayType = displayType
    }
}
