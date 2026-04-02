package io.baiyanwu.coinmonitor.data.ai.market

import io.baiyanwu.coinmonitor.lib.agents.MarketEventType
import io.baiyanwu.coinmonitor.lib.agents.MarketImpactDirection
import io.baiyanwu.coinmonitor.lib.agents.SourceTimestampConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementParsingTest {
    @Test
    fun `parse Binance announcement records from public cms payload`() {
        val payload = """
            {
              "code":"000000",
              "data":{
                "articles":[
                  {
                    "id":1,
                    "code":"abc123",
                    "title":"Binance Will List Espresso (ESP) with Seed Tag Applied"
                  },
                  {
                    "id":2,
                    "code":"def456",
                    "title":"Binance Futures Will Launch USDⓈ-Margined OPNUSDT Perpetual Contract Pre-Market Trading (2026-02-21)"
                  }
                ]
              },
              "success":true
            }
        """.trimIndent()

        val records = parseBinanceAnnouncementRecords(payload, nowMillis = 123L)

        assertEquals(2, records.size)
        assertEquals("binance:abc123", records.first().id)
        assertEquals(
            "https://www.binance.com/en/support/announcement/detail/abc123",
            records.first().url
        )
        assertEquals(SourceTimestampConfidence.UNKNOWN, records.first().sourceTimestampConfidence)
        assertEquals(SourceTimestampConfidence.ESTIMATED, records.last().sourceTimestampConfidence)
        assertTrue(records.last().publishedAtMillis > 123L)
    }

    @Test
    fun `parse OKX announcement records from help center html`() {
        val html = """
            <html>
              <body>
                <section>
                  <article>
                    <a href="/help/okx-to-list-xyz">OKX to List XYZ</a>
                    <p>Published on 26 Jan 2026</p>
                  </article>
                  <article>
                    <a href="/help/delist-abc">OKX will delist ABC</a>
                    <p>Published on 20 Jan 2026</p>
                  </article>
                </section>
              </body>
            </html>
        """.trimIndent()

        val records = parseOkxAnnouncementRecords(html, nowMillis = 123L)

        assertEquals(2, records.size)
        assertEquals("OKX to List XYZ", records.first().title)
        assertEquals("https://www.okx.com/help/okx-to-list-xyz", records.first().url)
        assertEquals(SourceTimestampConfidence.ESTIMATED, records.first().sourceTimestampConfidence)
    }

    @Test
    fun `classify listing and delisting titles`() {
        assertEquals(
            MarketEventType.LISTING,
            classifyEventType("Binance Will List Espresso (ESP) with Seed Tag Applied")
        )
        assertEquals(
            MarketImpactDirection.POSITIVE,
            classifyImpactDirection("Binance Will List Espresso (ESP) with Seed Tag Applied")
        )
        assertEquals(
            MarketEventType.DELISTING,
            classifyEventType("OKX will delist ABC")
        )
        assertEquals(
            MarketImpactDirection.NEGATIVE,
            classifyImpactDirection("OKX will delist ABC")
        )
    }
}
