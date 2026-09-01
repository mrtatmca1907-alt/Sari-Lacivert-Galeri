package com.atmaca.filemanager.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileOperationEngineTest {
    @Test
    fun move_sameStorage_removesSource_andRefreshesOnlySourceAndTarget() = runTest {
        val root = Files.createTempDirectory("atmaca-move").toFile()
        val sourceDir = File(root, "source").apply { mkdirs() }
        val targetDir = File(root, "target").apply { mkdirs() }
        val source = File(sourceDir, "a.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        val engine = FileOperationEngine(LocalFileBackend())
        val result = engine.move(listOf(source), targetDir)

        val target = File(targetDir, source.name)
        assertTrue(target.exists())
        assertFalse(source.exists())
        assertEquals(setOf(sourceDir.absolutePath, targetDir.absolutePath), result.refresh.directories)
        assertEquals(listOf(target.absolutePath), result.succeeded)
    }

    @Test
    fun copy_keepsSource_andRefreshesOnlyTarget() = runTest {
        val root = Files.createTempDirectory("atmaca-copy").toFile()
        val sourceDir = File(root, "source").apply { mkdirs() }
        val targetDir = File(root, "target").apply { mkdirs() }
        val source = File(sourceDir, "b.png").apply { writeBytes(ByteArray(8192) { (it % 255).toByte() }) }

        val engine = FileOperationEngine(LocalFileBackend())
        val result = engine.copy(listOf(source), targetDir)

        val target = File(targetDir, source.name)
        assertTrue(source.exists())
        assertTrue(target.exists())
        assertEquals(source.length(), target.length())
        assertEquals(setOf(targetDir.absolutePath), result.refresh.directories)
    }

    @Test
    fun delete_removesItem_andRefreshesOnlyParent() = runTest {
        val root = Files.createTempDirectory("atmaca-delete").toFile()
        val source = File(root, "delete-me.txt").apply { writeText("x") }

        val engine = FileOperationEngine(LocalFileBackend())
        val result = engine.delete(listOf(source))

        assertFalse(source.exists())
        assertEquals(setOf(root.absolutePath), result.refresh.directories)
        assertEquals(OperationType.DELETE, result.type)
    }

    @Test
    fun failedMove_doesNotReportSuccess_orDeleteSource() = runTest {
        val root = Files.createTempDirectory("atmaca-fail").toFile()
        val sourceDir = File(root, "source").apply { mkdirs() }
        val source = File(sourceDir, "keep.dat").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val invalidTarget = File(root, "not-a-dir").apply { writeText("occupied") }

        val engine = FileOperationEngine(LocalFileBackend())
        val result = engine.move(listOf(source), invalidTarget)

        assertTrue(source.exists())
        assertTrue(result.succeeded.isEmpty())
        assertTrue(result.failed.containsKey(source.absolutePath))
    }
}
