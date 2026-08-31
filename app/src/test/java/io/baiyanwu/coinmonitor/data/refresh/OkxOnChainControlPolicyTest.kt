package io.baiyanwu.coinmonitor.data.refresh

import io.baiyanwu.coinmonitor.domain.model.OkxApiCredentials
import org.junit.Assert.assertEquals
import org.junit.Test

class OkxOnChainControlPolicyTest {
    @Test
    fun `whitelist rejection falls back to polling`() {
        assertEquals(
            OkxOnChainControlAction.FALLBACK_TO_POLLING,
            resolveOkxOnChainControlAction(event = "error", code = "60029")
        )
    }

    @Test
    fun `login rejection falls back and successful login subscribes`() {
        assertEquals(
            OkxOnChainControlAction.FALLBACK_TO_POLLING,
            resolveOkxOnChainControlAction(event = "login", code = "60009")
        )
        assertEquals(
            OkxOnChainControlAction.LOGIN_SUCCEEDED,
            resolveOkxOnChainControlAction(event = "login", code = "0")
        )
    }

    @Test
    fun `subscription acknowledgement remains on websocket`() {
        assertEquals(
            OkxOnChainControlAction.IGNORE,
            resolveOkxOnChainControlAction(event = "subscribe", code = "0")
        )
    }

    @Test
    fun `dex polling uses configured interval and defaults to forty five seconds`() {
        assertEquals(45_000L, resolveOkxOnChainPollingIntervalMillis(credentials = null))
        assertEquals(
            30_000L,
            resolveOkxOnChainPollingIntervalMillis(
                OkxApiCredentials(dexPollingIntervalSeconds = 30)
            )
        )
    }
}
