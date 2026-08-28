package com.atmaca.gallery

object ImageSample {
    fun compute(sourceW: Int, sourceH: Int, targetW: Int, targetH: Int): Int {
        if (sourceW <= 0 || sourceH <= 0 || targetW <= 0 || targetH <= 0) return 1
        var sample = 1
        while (sourceW / (sample * 2) >= targetW && sourceH / (sample * 2) >= targetH) {
            sample *= 2
        }
        return sample
    }
}
