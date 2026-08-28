package com.atmaca.gallery

class RefreshGate {
    private var running = false
    private var pending = false

    @Synchronized
    fun request(): Boolean {
        if (running) {
            pending = true
            return false
        }
        running = true
        return true
    }

    @Synchronized
    fun finishAndCheckPending(): Boolean {
        if (!running) return false
        running = false
        if (!pending) return false
        pending = false
        return true
    }
}
