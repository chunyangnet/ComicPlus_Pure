package com.comicplus.pure

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStoreTest {
    @Test
    fun completedReplacementKeepsTheOldCopyUntilTheNewDirectoryIsValid() {
        withTemporaryDirectory { root ->
            val partial = File(root, "partial").apply { mkdirs() }
            val final = File(root, "final").apply { mkdirs() }
            val commit = File(root, "commit")
            val backup = File(root, "backup")
            File(final, "old.marker").writeText("old")
            File(partial, "000001.webp").writeBytes(byteArrayOf(1, 2, 3))

            installDirectoryWithRollback(partial, final, commit, backup, expectedPages = 1)

            assertTrue(File(final, "000001.webp").isFile)
            assertFalse(File(final, "old.marker").exists())
            assertFalse(backup.exists())
        }
    }

    @Test
    fun invalidReplacementNeverDeletesTheExistingDownload() {
        withTemporaryDirectory { root ->
            val partial = File(root, "partial").apply { mkdirs() }
            val final = File(root, "final").apply { mkdirs() }
            val commit = File(root, "commit")
            val backup = File(root, "backup")
            val marker = File(final, "old.marker").apply { writeText("old") }

            val result = runCatching {
                installDirectoryWithRollback(partial, final, commit, backup, expectedPages = 1)
            }

            assertTrue(result.isFailure)
            assertTrue(marker.isFile)
            assertFalse(backup.exists())
        }
    }

    @Test
    fun completePageSetRequiresCanonicalContinuousBoundedFiles() {
        withTemporaryDirectory { root ->
            File(root, "000001.webp").writeBytes(byteArrayOf(1))
            File(root, "000003.webp").writeBytes(byteArrayOf(3))

            assertFalse(hasCompletePageSet(listPageFiles(root), expected = 2))

            File(root, "000003.webp").delete()
            File(root, "2.webp").writeBytes(byteArrayOf(2))
            assertFalse(hasCompletePageSet(listPageFiles(root), expected = 2))

            File(root, "2.webp").delete()
            File(root, "000002.webp").writeBytes(byteArrayOf(2))
            assertTrue(hasCompletePageSet(listPageFiles(root), expected = 2))
            assertEquals(listOf("000001.webp", "000002.webp"), listPageFiles(root).map(File::getName))
        }
    }

    @Test
    fun resumeCleanupRemovesNonCanonicalAndStalePageFiles() {
        withTemporaryDirectory { root ->
            File(root, "000001.webp").writeBytes(byteArrayOf(1))
            File(root, "000002.webp").writeBytes(byteArrayOf(2))
            File(root, "1.webp").writeBytes(byteArrayOf(1))
            File(root, "000003.webp").writeBytes(byteArrayOf(3))
            File(root, "000001.webp.tmp").writeBytes(byteArrayOf(1))
            File(root, "meta.txt").writeText("stale")
            File(root, "junk").mkdirs()

            cleanupPartialPageFiles(root, expectedPages = 2)

            assertTrue(File(root, "000001.webp").isFile)
            assertTrue(File(root, "000002.webp").isFile)
            assertFalse(File(root, "1.webp").exists())
            assertFalse(File(root, "000003.webp").exists())
            assertFalse(File(root, "000001.webp.tmp").exists())
            assertFalse(File(root, "meta.txt").exists())
            assertFalse(File(root, "junk").exists())
        }
    }

    @Test
    fun failedInstallRestoresBackupEvenWhenIncompleteFinalExists() {
        withTemporaryDirectory { root ->
            val final = File(root, "final").apply { mkdirs() }
            val backup = File(root, "backup").apply { mkdirs() }
            File(final, "000001.webp").writeBytes(byteArrayOf(1))
            val oldMarker = File(backup, "old.marker").apply { writeText("old") }

            restoreBackupAfterFailedInstall(final, backup, expectedPages = 2)

            assertFalse(backup.exists())
            assertFalse(File(final, "000001.webp").exists())
            assertTrue(File(final, oldMarker.name).isFile)
        }
    }

    private fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("comicplus-download-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
