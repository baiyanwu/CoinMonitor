package io.baiyanwu.coinmonitor.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class DappProviderProtocolTest {
    @Test
    fun `routes Trust Provider signing callbacks independently`() {
        assertEquals(
            DappProviderAction.Sign(DappSignatureMethod.PERSONAL_MESSAGE),
            route("signPersonalMessage")
        )
        assertEquals(
            DappProviderAction.Sign(DappSignatureMethod.RAW_MESSAGE),
            route("signMessage")
        )
        assertEquals(
            DappProviderAction.Sign(DappSignatureMethod.TYPED_DATA),
            route("signTypedMessage")
        )
        assertEquals(DappProviderAction.SendTransaction, route("signTransaction"))
    }

    private fun route(method: String): DappProviderAction = DappProviderProtocol.route(
        DappBridgeRequest(id = 1, method = method, params = null, origin = "https://app.uniswap.org")
    )
}
