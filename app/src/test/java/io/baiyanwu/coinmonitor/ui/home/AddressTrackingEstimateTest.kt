package io.baiyanwu.coinmonitor.ui.home

import io.baiyanwu.coinmonitor.domain.model.TrackedAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class AddressTrackingEstimateTest {
    @Test fun enteringTrackingOnlyRequestsMissingValuesIncludingOldRecords() {
        val cached = TrackedAddress("cached", listOf("1"), 1, "123", updatedAtMillis = 2)
        val zero = cached.copy(address = "zero", totalValueUsd = "0", updatedAtMillis = null)
        val missing = cached.copy(address = "missing", totalValueUsd = null)
        val blank = cached.copy(address = "blank", totalValueUsd = "")
        assertEquals(listOf("missing", "blank"), addressesMissingEstimate(listOf(cached, zero, missing, blank)))
        assertEquals(emptyList<String>(), addressesMissingEstimate(listOf(cached, zero)))
    }
}
