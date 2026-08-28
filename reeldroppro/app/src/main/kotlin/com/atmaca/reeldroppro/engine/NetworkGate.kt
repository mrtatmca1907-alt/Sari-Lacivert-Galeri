package com.atmaca.reeldroppro.engine

enum class NetworkDecision {
    RUN,
    WAIT_FOR_NETWORK,
    WAIT_FOR_UNMETERED
}

object NetworkGate {
    fun decide(isConnected: Boolean, isMetered: Boolean, allowMobile: Boolean): NetworkDecision {
        if (!isConnected) return NetworkDecision.WAIT_FOR_NETWORK
        if (isMetered && !allowMobile) return NetworkDecision.WAIT_FOR_UNMETERED
        return NetworkDecision.RUN
    }
}
