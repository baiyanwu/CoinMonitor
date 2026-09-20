package io.baiyanwu.coinmonitor.data.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletMnemonicDictionaryTest {
    @Test
    fun `parses native space separated suggestions without duplicates`() {
        assertEquals(
            listOf("abandon", "ability", "able"),
            parseMnemonicSuggestions("abandon ability  able ability ")
        )
    }
}
