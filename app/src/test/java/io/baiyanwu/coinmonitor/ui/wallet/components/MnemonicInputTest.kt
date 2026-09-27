package io.baiyanwu.coinmonitor.ui.wallet.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MnemonicInputTest {
    @Test
    fun `splits ordinary line breaks and ideographic spaces`() {
        assertEquals(
            listOf("alpha", "beta", "gamma", "delta"),
            splitMnemonicWords(" alpha  beta\ngamma\u3000delta ")
        )
    }

    @Test
    fun `pasted phrase appends as ordered word blocks`() {
        assertEquals(
            "alpha beta gamma delta",
            updateMnemonicWords("alpha", " beta\ngamma  delta ", mode = MnemonicWordEditMode.APPEND)
        )
    }

    @Test
    fun `editing a word preserves its position and can expand pasted words`() {
        assertEquals(
            "alpha beta gamma delta",
            updateMnemonicWords(
                "alpha wrong delta",
                "beta gamma",
                editingIndex = 1,
                mode = MnemonicWordEditMode.REPLACE
            )
        )
    }

    @Test
    fun `accepts only standard bip39 word counts for import action`() {
        val twelveWords = (1..12).joinToString(" ") { "word$it" }
        val twentyFourWords = (1..24).joinToString(" ") { "word$it" }

        assertTrue(hasSupportedMnemonicWordCount(twelveWords))
        assertTrue(hasSupportedMnemonicWordCount(twentyFourWords))
        assertFalse(hasSupportedMnemonicWordCount("alpha beta gamma"))
    }

    @Test
    fun `suggested word replaces an edited block without changing its position`() {
        assertEquals(
            "alpha ability gamma",
            updateMnemonicWords(
                "alpha abilty gamma",
                "ability",
                editingIndex = 1,
                mode = MnemonicWordEditMode.REPLACE
            )
        )
    }

    @Test
    fun `inserts one or more words immediately after the selected word`() {
        assertEquals(
            "alpha beta gamma delta",
            updateMnemonicWords(
                "alpha delta",
                "beta gamma",
                editingIndex = 0,
                mode = MnemonicWordEditMode.INSERT_AFTER
            )
        )
    }
}
