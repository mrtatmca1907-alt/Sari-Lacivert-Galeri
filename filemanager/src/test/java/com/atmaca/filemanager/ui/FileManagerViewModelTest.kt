package com.atmaca.filemanager.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileManagerViewModelTest {
    @Test
    fun operationGate_rejectsSecondOperationForSameSource_untilFirstFinishes() {
        val gate = OperationGate()
        val paths = setOf("/storage/emulated/0/DCIM/a.jpg")

        assertTrue(gate.tryStart(paths))
        assertFalse(gate.tryStart(paths))
        gate.finish(paths)
        assertTrue(gate.tryStart(paths))
    }

    @Test
    fun operationGate_allowsDifferentFilesInParallelQueue() {
        val gate = OperationGate()

        assertTrue(gate.tryStart(setOf("/a.jpg")))
        assertTrue(gate.tryStart(setOf("/b.jpg")))
        assertFalse(gate.tryStart(setOf("/a.jpg", "/c.jpg")))
    }

    @Test
    fun targetedRefreshAccumulator_deduplicatesWithoutAddingStorageRoot() {
        val accumulator = TargetedRefreshAccumulator()
        accumulator.add(setOf("/storage/emulated/0/DCIM", "/storage/emulated/0/Pictures/1907"))
        accumulator.add(setOf("/storage/emulated/0/DCIM"))

        val result = accumulator.drain()

        assertTrue(result == setOf("/storage/emulated/0/DCIM", "/storage/emulated/0/Pictures/1907"))
        assertFalse(result.contains("/storage/emulated/0"))
        assertTrue(accumulator.drain().isEmpty())
    }
}
