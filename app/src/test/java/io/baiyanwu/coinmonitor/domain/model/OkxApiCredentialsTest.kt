package io.baiyanwu.coinmonitor.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OkxApiCredentialsTest {
    @Test
    fun `dex polling defaults to forty five seconds`() {
        assertEquals(45, OkxApiCredentials().effectiveDexPollingIntervalSeconds)
    }

    @Test
    fun `dex polling interval is clamped and snapped to five second steps`() {
        assertEquals(10, OkxApiCredentials.normalizeDexPollingIntervalSeconds(1))
        assertEquals(15, OkxApiCredentials.normalizeDexPollingIntervalSeconds(13))
        assertEquals(120, OkxApiCredentials.normalizeDexPollingIntervalSeconds(999))
    }
}
