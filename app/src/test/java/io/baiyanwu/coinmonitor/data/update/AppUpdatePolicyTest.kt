package io.baiyanwu.coinmonitor.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test
    fun `newer semantic version is detected numerically`() {
        assertTrue(AppUpdatePolicy.isRemoteVersionNewer("v1.0.10", "1.0.9"))
        assertTrue(AppUpdatePolicy.isRemoteVersionNewer("2.0", "1.9.9"))
    }

    @Test
    fun `equal and older versions do not trigger an update`() {
        assertFalse(AppUpdatePolicy.isRemoteVersionNewer("v1.0.7", "1.0.7"))
        assertFalse(AppUpdatePolicy.isRemoteVersionNewer("1.0.7.0", "1.0.7"))
        assertFalse(AppUpdatePolicy.isRemoteVersionNewer("1.0.6", "1.0.7"))
    }

    @Test
    fun `invalid remote tag does not trigger an update`() {
        assertFalse(AppUpdatePolicy.isRemoteVersionNewer("latest", "1.0.7"))
        assertFalse(AppUpdatePolicy.isRemoteVersionNewer("v1..8", "1.0.7"))
    }

}
