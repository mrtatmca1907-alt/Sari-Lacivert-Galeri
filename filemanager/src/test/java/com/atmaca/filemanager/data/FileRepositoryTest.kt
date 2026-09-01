package com.atmaca.filemanager.data

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileRepositoryTest {
    @Test
    fun listDirectory_putsFoldersFirst_andSortsNames_withoutRecursiveScan() = runTest {
        val root = Files.createTempDirectory("atmaca-list").toFile()
        File(root, "z-folder").mkdirs()
        File(root, "A-folder").mkdirs()
        File(root, "m.jpg").writeText("1")
        File(root, "b.txt").writeText("2")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = FileRepository(ioDispatcher = dispatcher)

        val page = repo.listDirectory(root, offset = 0, limit = 10)

        assertEquals(listOf("A-folder", "z-folder", "b.txt", "m.jpg"), page.items.map { it.file.name })
        assertFalse(page.hasMore)
    }

    @Test
    fun listDirectory_chunksLargeFolder() = runTest {
        val root = Files.createTempDirectory("atmaca-page").toFile()
        repeat(25) { File(root, "file-%02d.jpg".format(it)).writeText("x") }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = FileRepository(ioDispatcher = dispatcher)

        val page = repo.listDirectory(root, offset = 10, limit = 5)

        assertEquals(5, page.items.size)
        assertEquals(10, page.offset)
        assertEquals(15, page.nextOffset)
        assertEquals(true, page.hasMore)
    }

    @Test
    fun targetedRefresh_neverExpandsToStorageRoot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = FileRepository(ioDispatcher = dispatcher)
        val requested = setOf("/storage/emulated/0/DCIM/Camera", "/storage/emulated/0/Pictures/1907")

        val refreshed = repo.refreshDirectories(requested)

        assertEquals(requested, refreshed)
        assertFalse(refreshed.contains("/storage/emulated/0"))
    }
}
