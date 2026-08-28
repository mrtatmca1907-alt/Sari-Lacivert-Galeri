package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkGateTest {
    @Test
    fun `offline jobs wait and online jobs may run`() {
        assertEquals(NetworkDecision.WAIT_FOR_NETWORK, NetworkGate.decide(isConnected = false, isMetered = false, allowMobile = true))
        assertEquals(NetworkDecision.RUN, NetworkGate.decide(isConnected = true, isMetered = false, allowMobile = false))
        assertEquals(NetworkDecision.RUN, NetworkGate.decide(isConnected = true, isMetered = true, allowMobile = true))
    }

    @Test
    fun `metered network waits when mobile data is disabled`() {
        assertEquals(NetworkDecision.WAIT_FOR_UNMETERED, NetworkGate.decide(isConnected = true, isMetered = true, allowMobile = false))
    }
}
