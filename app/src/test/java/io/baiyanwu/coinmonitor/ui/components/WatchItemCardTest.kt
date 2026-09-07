package io.baiyanwu.coinmonitor.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchItemCardTest {
    @Test
    fun `compact contract address keeps the head and tail`() {
        assertEquals(
            "0x1234…cdef",
            compactContractAddress("0x1234567890abcdef")
        )
    }

    @Test
    fun `compact contract address leaves short values unchanged`() {
        assertEquals("short-ca", compactContractAddress("short-ca"))
    }

    @Test
    fun `compact contract address trims surrounding whitespace`() {
        assertEquals("0x1234…cdef", compactContractAddress("  0x1234567890abcdef  "))
    }

}
