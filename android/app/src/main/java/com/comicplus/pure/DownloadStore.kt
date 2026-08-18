package com.comicplus.pure

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class DownloadedChapter(
    val comicId: String,
    val comicTitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val pageCount: Int,
    val downloadedPages: Int,
    val bytes: Long,
    val complete: Boolean,
)

class DownloadStore(context: Context) {
    private val root by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        File(context.filesDir, "jm-downloads").apply { mkdirs() }
    }
    private val _items = MutableStateFlow<List<DownloadedChapter>>(emptyList())
    val items: StateFlow<List<DownloadedChapter>> = _items.asStateFlow()
    private val running = ConcurrentHashMap<String, Boolean>()
    private val fileMutex = Mutex()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        fileMutex.withLock { refreshLocked() }
    }

    private fun refreshLocked() {
        val comicDirectories = root.listFiles { file -> file.isDirectory && isSafeId(file.name) }.orEmpty()
        comicDirectories.forEach { comicDirectory ->
            recoverBackupDirectories(comicDirectory)
            deleteStaleWorkDirectories(comicDirectory)
        }
        val refreshedDirectories = root.listFiles { file -> file.isDirectory && isSafeId(file.name) }.orEmpty()
        _items.value = refreshedDirectories
            .asSequence()
            .flatMap { comicDir ->
                comicDir.listFiles { file -> file.isDirectory && isSafeId(file.name) }
                    .orEmpty()
                    .asSequence()
            }
            .mapNotNull(::readDownload)
            .sortedByDescending { chapterDir(it.comicId, it.chapterId).lastModified() }
            .toList()
    }

    fun chapterDir(comicId: String, chapterId: String): File {
        require(isSafeId(comicId) && isSafeId(chapterId)) { "Invalid JM download id" }
        return File(File(root, comicId), chapterId)
    }

    suspend fun prepareDownload(comicId: String, chapterId: String, expectedPages: Int): File = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            require(expectedPages in 1..MAX_DOWNLOAD_PAGES) { "Invalid page count" }
            partialChapterDir(comicId, chapterId).apply {
                if (!exists() && !mkdirs()) throw JmSourceException()
                cleanupPartialPageFiles(this, expectedPages)
            }
        }
    }

    fun partialPageFile(directory: File, index: Int): File {
        require(index in 1..MAX_DOWNLOAD_PAGES) { "Invalid page index" }
        return File(directory, String.format(Locale.US, "%06d.webp", index))
    }

    suspend fun localPages(item: DownloadedChapter): List<File> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!item.complete) return@withLock emptyList()
            val pages = listPageFiles(chapterDir(item.comicId, item.chapterId))
            pages.takeIf { hasCompletePageSet(it, item.pageCount) }.orEmpty()
        }
    }

    suspend fun isDownloaded(comicId: String, chapterId: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock { readDownload(chapterDir(comicId, chapterId))?.complete == true }
    }

    suspend fun completeDownload(item: DownloadedChapter) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            require(item.pageCount in 1..MAX_DOWNLOAD_PAGES) { "Invalid page count" }
            val partialDir = partialChapterDir(item.comicId, item.chapterId)
            val pages = listPageFiles(partialDir)
            if (!hasCompletePageSet(pages, item.pageCount)) throw JmSourceException()
            val completed = item.copy(
                downloadedPages = pages.size,
                bytes = pages.sumOf(File::length),
                complete = true,
            )
            writeMetaAtomically(partialDir, completed)

            installCompletedDirectory(
                partialDir = partialDir,
                finalDir = chapterDir(item.comicId, item.chapterId),
                commitDir = commitChapterDir(item.comicId, item.chapterId),
                backupDir = backupChapterDir(item.comicId, item.chapterId),
                expectedPages = item.pageCount,
            )
            refreshLocked()
        }
    }

    suspend fun delete(item: DownloadedChapter) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (isRunning("${item.comicId}:${item.chapterId}")) throw JmSourceException()
            listOf(
                chapterDir(item.comicId, item.chapterId),
                partialChapterDir(item.comicId, item.chapterId),
                commitChapterDir(item.comicId, item.chapterId),
                backupChapterDir(item.comicId, item.chapterId),
            ).forEach { directory ->
                if (directory.exists() && !directory.deleteRecursively()) throw JmSourceException()
            }
            val comicDir = File(root, item.comicId)
            if (comicDir.listFiles().isNullOrEmpty()) comicDir.delete()
            refreshLocked()
        }
    }

    fun start(id: String): Boolean = running.putIfAbsent(id, true) == null
    fun finish(id: String) { running.remove(id) }
    fun isRunning(id: String) = running[id] == true

    private fun partialChapterDir(comicId: String, chapterId: String) =
        workChapterDir(comicId, chapterId, "partial")

    private fun commitChapterDir(comicId: String, chapterId: String) =
        workChapterDir(comicId, chapterId, "commit")

    private fun backupChapterDir(comicId: String, chapterId: String) =
        workChapterDir(comicId, chapterId, "backup")

    private fun workChapterDir(comicId: String, chapterId: String, kind: String): File {
        require(isSafeId(comicId) && isSafeId(chapterId)) { "Invalid JM download id" }
        return File(File(root, comicId), ".$chapterId.$CURRENT_DECODE_VERSION.$kind")
    }

    private fun installCompletedDirectory(
        partialDir: File,
        finalDir: File,
        commitDir: File,
        backupDir: File,
        expectedPages: Int,
    ) {
        recoverBackupIfNeeded(finalDir, backupDir)
        installDirectoryWithRollback(partialDir, finalDir, commitDir, backupDir, expectedPages)
    }

    /**
     * A process can die after moving the old directory to .backup but before
     * the new directory is committed. An incomplete final directory must never
     * hide that valid backup on the next refresh or retry.
     */
    private fun recoverBackupIfNeeded(finalDir: File, backupDir: File) {
        if (!backupDir.exists()) return
        val backupItem = readDownload(
            backupDir,
            finalDir.parentFile?.name,
            finalDir.name,
        )
        val finalItem = readDownload(finalDir)
        when {
            backupItem?.complete == true && finalItem?.complete != true -> {
                // Deleting an incomplete final is safe; the valid backup stays
                // in place if the rename itself fails and can be retried later.
                if (finalDir.exists() && !finalDir.deleteRecursively()) return
                backupDir.renameTo(finalDir)
            }
            finalItem?.complete == true -> backupDir.deleteRecursively()
            backupItem == null -> backupDir.deleteRecursively()
        }
    }

    private fun recoverBackupDirectories(comicDirectory: File) {
        comicDirectory.listFiles { file ->
            file.isDirectory && file.name.endsWith(".$CURRENT_DECODE_VERSION.backup")
        }.orEmpty().forEach { backup ->
            val chapterId = BACKUP_DIRECTORY_NAME.matchEntire(backup.name)?.groupValues?.getOrNull(1) ?: return@forEach
            val key = "${comicDirectory.name}:$chapterId"
            if (isRunning(key)) return@forEach
            val finalDir = File(comicDirectory, chapterId)
            val backupItem = readDownload(backup, comicDirectory.name, chapterId)
            val finalItem = readDownload(finalDir)
            when {
                backupItem?.complete == true && finalItem?.complete != true -> {
                    if (finalDir.exists() && !finalDir.deleteRecursively()) return@forEach
                    backup.renameTo(finalDir)
                }
                finalItem?.complete == true || backupItem == null -> backup.deleteRecursively()
                backup.lastModified() in 1 until (System.currentTimeMillis() - STALE_WORK_DIRECTORY_MILLIS) ->
                    backup.deleteRecursively()
            }
        }
    }

    private fun deleteStaleWorkDirectories(comicDirectory: File) {
        val cutoff = System.currentTimeMillis() - STALE_WORK_DIRECTORY_MILLIS
        comicDirectory.listFiles { file ->
            file.isDirectory && file.name.startsWith('.') &&
                (file.name.endsWith(".partial") || file.name.endsWith(".commit") || file.name.endsWith(".backup"))
        }.orEmpty().forEach { directory ->
            val chapterId = (WORK_DIRECTORY_NAME.matchEntire(directory.name)
                ?: BACKUP_DIRECTORY_NAME.matchEntire(directory.name))?.groupValues?.getOrNull(1)
            val isActive = chapterId != null && isRunning("${comicDirectory.name}:$chapterId")
            if (!isActive && directory.lastModified() in 1 until cutoff) directory.deleteRecursively()
        }
        if (comicDirectory.listFiles().isNullOrEmpty()) comicDirectory.delete()
    }

    private fun readDownload(
        directory: File,
        expectedComicId: String? = directory.parentFile?.name,
        expectedChapterId: String? = directory.name,
    ): DownloadedChapter? {
        val fields = directory.resolve(META_FILE_NAME).readTextOrNull()?.lineSequence()?.toList() ?: return null
        if (fields.size < 7) return null
        val comicId = fields[0].takeIf(::isSafeId) ?: return null
        val chapterId = fields[2].takeIf(::isSafeId) ?: return null
        if (expectedChapterId != chapterId || expectedComicId != comicId) return null
        val pageCount = fields[4].toIntOrNull()?.takeIf { it in 1..MAX_DOWNLOAD_PAGES } ?: return null
        val downloadedPages = fields[5].toIntOrNull()?.coerceIn(0, pageCount) ?: 0
        val recordedBytes = fields[6].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val pages = listPageFiles(directory)
        val complete = fields.getOrNull(7) == "complete" &&
            isCompatibleDownload(chapterId, fields.getOrNull(8)) &&
            downloadedPages == pageCount &&
            hasCompletePageSet(pages, pageCount)
        val actualBytes = pages.sumOf(File::length)
        val bytes = if (complete) actualBytes else recordedBytes.coerceAtMost(actualBytes)
        return DownloadedChapter(
            comicId = comicId,
            comicTitle = sanitizeMetadataText(fields[1]),
            chapterId = chapterId,
            chapterTitle = sanitizeMetadataText(fields[3]),
            pageCount = pageCount,
            downloadedPages = downloadedPages,
            bytes = bytes,
            complete = complete,
        )
    }

    private fun writeMetaAtomically(directory: File, item: DownloadedChapter) {
        if (!directory.exists() && !directory.mkdirs()) throw JmSourceException()
        val temporary = File(directory, "$META_FILE_NAME.tmp")
        val target = File(directory, META_FILE_NAME)
        temporary.writeText(
            listOf(
                item.comicId,
                sanitizeMetadataText(item.comicTitle),
                item.chapterId,
                sanitizeMetadataText(item.chapterTitle),
                item.pageCount.toString(),
                item.downloadedPages.toString(),
                item.bytes.toString(),
                if (item.complete) "complete" else "partial",
                CURRENT_DECODE_VERSION,
            ).joinToString("\n"),
        )
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }
}

internal fun installDirectoryWithRollback(
    partialDir: File,
    finalDir: File,
    commitDir: File,
    backupDir: File,
    expectedPages: Int,
) {
    require(expectedPages in 1..MAX_DOWNLOAD_PAGES) { "Invalid page count" }
    if (!hasCompletePageSet(listPageFiles(partialDir), expectedPages)) throw JmSourceException()
    var previousMoved = false
    try {
        if (finalDir.exists()) {
            if (!finalDir.renameTo(backupDir)) throw JmSourceException()
            previousMoved = true
        }
        if (!partialDir.renameTo(finalDir)) {
            commitDir.deleteRecursively()
            if (!partialDir.copyRecursively(commitDir, overwrite = true)) throw JmSourceException()
            if (!hasCompletePageSet(listPageFiles(commitDir), expectedPages) || !commitDir.renameTo(finalDir)) {
                throw JmSourceException()
            }
            // The copy path leaves the original hidden partial directory in
            // place. It is no longer resumable after a valid commit and would
            // otherwise consume space until the stale-directory sweep.
            partialDir.deleteRecursively()
        }
        backupDir.deleteRecursively()
    } catch (error: Throwable) {
        commitDir.deleteRecursively()
        if (previousMoved) restoreBackupAfterFailedInstall(finalDir, backupDir, expectedPages)
        throw error
    }
}

internal fun restoreBackupAfterFailedInstall(finalDir: File, backupDir: File, expectedPages: Int) {
    // A failed copy/rename may leave a partially-created final directory. It
    // must not hide the previously valid backup from the caller or recovery.
    val finalIsValid = finalDir.exists() && hasCompletePageSet(listPageFiles(finalDir), expectedPages)
    if (finalIsValid) return
    if (finalDir.exists() && !finalDir.deleteRecursively()) return
    backupDir.renameTo(finalDir)
}

internal fun listPageFiles(directory: File): List<File> = directory
    .listFiles { file -> file.isFile && file.extension.equals("webp", ignoreCase = true) && file.length() > 0L }
    .orEmpty()
    .sortedBy(File::getName)

/** Remove stale/corrupt entries before a chapter download is resumed. */
internal fun cleanupPartialPageFiles(directory: File, expectedPages: Int) {
    require(expectedPages in 1..MAX_DOWNLOAD_PAGES) { "Invalid page count" }
    directory.listFiles().orEmpty().forEach { file ->
        if (!isCanonicalPageFile(file, expectedPages)) file.deleteRecursively()
    }
}

private fun isCanonicalPageFile(file: File, expectedPages: Int): Boolean {
    if (!file.isFile || !file.extension.equals("webp", ignoreCase = true)) return false
    val index = file.nameWithoutExtension.toIntOrNull() ?: return false
    return index in 1..expectedPages &&
        file.name.equals(canonicalPageFileName(index), ignoreCase = true) &&
        file.length() in 1..MAX_PAGE_FILE_BYTES
}

private fun canonicalPageFileName(index: Int): String = String.format(Locale.US, "%06d.webp", index)

internal fun hasCompletePageSet(pages: List<File>, expected: Int): Boolean =
    pages.size == expected && pages.withIndex().all { (offset, page) ->
        page.name.equals(canonicalPageFileName(offset + 1), ignoreCase = true) &&
            page.length() in 1..MAX_PAGE_FILE_BYTES
    }

private fun File.readTextOrNull(): String? = runCatchingNonFatal {
    if (isFile && length() in 1..MAX_META_BYTES) readText() else null
}.getOrNull()

private fun isSafeId(value: String): Boolean = value.matches(SAFE_DOWNLOAD_ID)

internal fun sanitizeMetadataText(value: String): String =
    value.replace(META_CONTROL_CHARACTERS, " ").trim().take(MAX_META_TEXT_LENGTH)

private const val META_FILE_NAME = "meta.txt"
private const val MAX_META_BYTES = 16 * 1024L
private const val MAX_META_TEXT_LENGTH = 500
private const val MAX_DOWNLOAD_PAGES = 20_000
private const val MAX_PAGE_FILE_BYTES = 40L * 1024L * 1024L
private const val STALE_WORK_DIRECTORY_MILLIS = 14L * 24L * 60L * 60L * 1_000L
private const val CURRENT_DECODE_VERSION = "decode-v4"
private val SAFE_DOWNLOAD_ID = Regex("^\\d{1,12}$")
private val META_CONTROL_CHARACTERS = Regex("[\\r\\n\\u0000]+")
private val WORK_DIRECTORY_NAME = Regex("^\\.(\\d{1,12})\\.[A-Za-z0-9_-]+\\.(?:partial|commit)$")
private val BACKUP_DIRECTORY_NAME = Regex("^\\.(\\d{1,12})\\.[A-Za-z0-9_-]+\\.backup$")

internal fun isCompatibleDownload(@Suppress("UNUSED_PARAMETER") chapterId: String, decodeVersion: String?): Boolean =
    decodeVersion == CURRENT_DECODE_VERSION
