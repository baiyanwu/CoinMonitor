package io.baiyanwu.coinmonitor.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletWatchModelsTest {
    @Test fun `classifies EVM and Solana addresses`() {
        assertEquals(WalletAddressKind.EVM, WalletAddressParser.classify("0x000000000000000000000000000000000000dEaD"))
        assertEquals(WalletAddressKind.SOLANA, WalletAddressParser.classify("11111111111111111111111111111111"))
    }

    @Test fun `rejects malformed addresses`() {
        assertNull(WalletAddressParser.classify("0x1234"))
        assertNull(WalletAddressParser.classify("O0Il-not-base58"))
    }

    @Test fun `routes address kinds to separate chain registries`() {
        assertEquals(listOf("501"), OkxWalletChainRegistry.indexesFor(WalletAddressKind.SOLANA))
        assertEquals(29, OkxWalletChainRegistry.indexesFor(WalletAddressKind.EVM).size)
        assertEquals("Ethereum", OkxWalletChainRegistry.displayName("1"))
        assertEquals("Robinhood", OkxWalletChainRegistry.displayName("4663"))
    }
}
