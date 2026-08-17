package com.comicplus.pure

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemVpnMonitorTest {
    private val direct = SystemRouteSnapshot(1L, vpnActive = false, linkFingerprint = "wlan0")

    @Test
    fun firstSnapshotDoesNotInvalidateClients() {
        assertFalse(systemRouteChanged(null, direct))
    }

    @Test
    fun unchangedRouteDoesNotInvalidateClients() {
        assertFalse(systemRouteChanged(direct, direct.copy()))
    }

    @Test
    fun vpnTransitionInvalidatesClients() {
        assertTrue(systemRouteChanged(direct, SystemRouteSnapshot(2L, vpnActive = true, linkFingerprint = "tun0")))
    }
}
