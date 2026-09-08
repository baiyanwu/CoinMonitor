package io.baiyanwu.coinmonitor.overlay

import io.baiyanwu.coinmonitor.domain.model.ArrangedOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.MarqueeOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.OverlayDisplayType
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayWindowControllerTest {
    @Test
    fun `switching display type hides previous window before showing next`() {
        val events = mutableListOf<String>()
        val arranged = FakeArrangedHost(events)
        val marquee = FakeMarqueeHost(events)
        val controller = OverlayWindowController(arranged, marquee)

        controller.showOrUpdate(emptyList(), OverlaySettings())
        controller.showOrUpdate(
            emptyList(),
            OverlaySettings(displayType = OverlayDisplayType.MARQUEE)
        )

        assertEquals(
            listOf("marquee:hide", "arranged:show", "arranged:hide", "marquee:show"),
            events
        )
    }

    @Test
    fun `updates within one type do not hide and recreate its window`() {
        val events = mutableListOf<String>()
        val controller = OverlayWindowController(
            FakeArrangedHost(events),
            FakeMarqueeHost(events)
        )
        val settings = OverlaySettings(displayType = OverlayDisplayType.MARQUEE)

        controller.showOrUpdate(emptyList(), settings)
        controller.showOrUpdate(emptyList(), settings.copy(locked = true))

        assertEquals(listOf("arranged:hide", "marquee:show", "marquee:show"), events)
    }

    private class FakeArrangedHost(
        private val events: MutableList<String>
    ) : ArrangedOverlayWindowHost {
        override fun showOrUpdate(
            items: List<WatchItem>,
            locked: Boolean,
            settings: ArrangedOverlaySettings,
            showOnchainMarketCap: Boolean
        ) {
            events += "arranged:show"
        }

        override fun hide() {
            events += "arranged:hide"
        }
    }

    private class FakeMarqueeHost(
        private val events: MutableList<String>
    ) : MarqueeOverlayWindowHost {
        override fun showOrUpdate(
            items: List<WatchItem>,
            locked: Boolean,
            settings: MarqueeOverlaySettings,
            showOnchainMarketCap: Boolean
        ) {
            events += "marquee:show"
        }

        override fun hide() {
            events += "marquee:hide"
        }
    }
}
