package io.baiyanwu.coinmonitor.ui.wallet

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletBackupVerificationTest {
    @Test
    fun `backup verification selects three distinct in-range positions`() {
        repeat(100) { seed ->
            val positions = randomBackupVerificationIndices(
                wordCount = 12,
                random = Random(seed)
            )

            assertEquals(3, positions.size)
            assertEquals(3, positions.distinct().size)
            assertEquals(positions.sorted(), positions)
            assertTrue(positions.all { it in 0 until 12 })
        }
    }

    @Test
    fun `backup verification positions are not fixed`() {
        val selections = (0 until 20)
            .map { seed -> randomBackupVerificationIndices(12, Random(seed)) }
            .toSet()

        assertTrue(selections.size > 1)
    }
}
