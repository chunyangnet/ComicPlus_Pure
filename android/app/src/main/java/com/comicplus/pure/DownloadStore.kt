package com.comicplus.pure

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val root = File(context.filesDir, "jm-downloads").apply { mkdirs() }
    private val _items = MutableStateFlow<List<DownloadedChapter>>(emptyList())
    val items: StateFlow<List<DownloadedChapter>> = _items.asStateFlow()
    private val running = ConcurrentHashMap<String, Boolean>()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val comicDirectories = root.listFiles { file -> file.isDirectory && isSafeId(file.name) }.orEmpty()
        comicDirectories.forEach(::deleteStaleWorkDirectories)
        _items.value = comicDirectories
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
        require(expectedPages in 1..MAX_DOWNLOAD_PAGES) { "Invalid page count" }
        val finalDir = chapterDir(comicId, chapterId)
        if (finalDir.exists() && !finalDir.deleteRecursively()) throw JmSourceException()
        partialChapterDir(comicId, chapterId).apply {
            if (!exists() && !mkdirs()) throw JmSourceException()
            listFiles().orEmpty().forEach { file ->
                val index = file.nameWithoutExtension.toIntOrNull()
                if (file.isFile && (file.extension.equals("tmp", ignoreCase = true) || index !in 1..expectedPages)) {
                    file.delete()
                }
            }
        }
    }

    fun partialPageFile(directory: File, index: Int): File {
        require(index > 0) { "Invalid page index" }
        return File(directory, String.format(Locale.US, "%06d.webp", index))
    }

    suspend fun localPages(item: DownloadedChapter): List<File> = withContext(Dispatchers.IO) {
        listPageFiles(chapterDir(item.comicId, item.chapterId))
    }

    suspend fun isDownloaded(comicId: String, chapterId: String): Boolean = withContext(Dispatchers.IO) {
        readDownload(chapterDir(comicId, chapterId))?.complete == true
    }

    suspend fun completeDownload(item: DownloadedChapter) = withContext(Dispatchers.IO) {
        require(item.pageCount > 0) { "Invalid page count" }
        val partialDir = partialChapterDir(item.comicId, item.chapterId)
        val pages = listPageFiles(partialDir)
        if (!hasCompletePageSet(pages, item.pageCount)) throw JmSourceException()
        val completed = item.copy(
            downloadedPages = pages.size,
            bytes = pages.sumOf(File::length),
            complete = true,
        )
        writeMetaAtomically(partialDir, completed)

        val finalDir = chapterDir(item.comicId, item.chapterId)
        if (finalDir.exists() && !finalDir.deleteRecursively()) throw JmSourceException()
        if (!partialDir.renameTo(finalDir)) {
            val commitDir = commitChapterDir(item.comicId, item.chapterId)
            commitDir.deleteRecursively()
            if (!partialDir.copyRecursively(commitDir, overwrite = true)) throw JmSourceException()
            if (!hasCompletePageSet(listPageFiles(commitDir), item.pageCount) || !commitDir.renameTo(finalDir)) {
                commitDir.deleteRecursively()
                throw JmSourceException()
            }
            partialDir.deleteRecursively()
        }
        refresh()
    }

    suspend fun delete(item: DownloadedChapter) = withContext(Dispatchers.IO) {
        listOf(
            chapterDir(item.comicId, item.chapterId),
            partialChapterDir(item.comicId, item.chapterId),
            commitChapterDir(item.comicId, item.chapterId),
        ).forEach { directory ->
            if (directory.exists() && !directory.deleteRecursively()) throw JmSourceException()
        }
        val comicDir = File(root, item.comicId)
        if (comicDir.listFiles().isNullOrEmpty()) comicDir.delete()
        refresh()
    }

    fun start(id: String): Boolean = running.putIfAbsent(id, true) == null
    fun finish(id: String) { running.remove(id) }
    fun isRunning(id: String) = running[id] == true

    private fun partialChapterDir(comicId: String, chapterId: String) =
        File(File(root, comicId), ".$chapterId.$CURRENT_DECODE_VERSION.partial")

    private fun commitChapterDir(comicId: String, chapterId: String) =
        File(File(root, comicId), ".$chapterId.$CURRENT_DECODE_VERSION.commit")

    private fun deleteStaleWorkDirectories(comicDirectory: File) {
        val cutoff = System.currentTimeMillis() - STALE_WORK_DIRECTORY_MILLIS
        comicDirectory.listFiles { file ->
            file.isDirectory && file.name.startsWith('.') &&
                (file.name.endsWith(".partial") || file.name.endsWith(".commit"))
        }.orEmpty().forEach { directory ->
            val chapterId = WORK_DIRECTORY_NAME.matchEntire(directory.name)?.groupValues?.getOrNull(1)
            val isActive = chapterId != null && isRunning("${comicDirectory.name}:$chapterId")
            if (!isActive && directory.lastModified() in 1 until cutoff) directory.deleteRecursively()
        }
        if (comicDirectory.listFiles().isNullOrEmpty()) comicDirectory.delete()
    }

    private fun readDownload(directory: File): DownloadedChapter? {
        val fields = directory.resolve(META_FILE_NAME).readTextOrNull()?.lineSequence()?.toList() ?: return null
        if (fields.size < 7) return null
        val comicId = fields[0].takeIf(::isSafeId) ?: return null
        val chapterId = fields[2].takeIf(::isSafeId) ?: return null
        if (directory.name != chapterId || directory.parentFile?.name != comicId) return null
        val pageCount = fields[4].toIntOrNull()?.takeIf { it in 1..MAX_DOWNLOAD_PAGES } ?: return null
        val downloadedPages = fields[5].toIntOrNull()?.coerceIn(0, pageCount) ?: 0
        val bytes = fields[6].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val pages = listPageFiles(directory)
        val complete = fields.getOrNull(7) == "complete" &&
            isCompatibleDownload(chapterId, fields.getOrNull(8)) &&
            downloadedPages == pageCount &&
            hasCompletePageSet(pages, pageCount)
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

private fun listPageFiles(directory: File): List<File> = directory
    .listFiles { file -> file.isFile && file.extension.equals("webp", ignoreCase = true) && file.length() > 0L }
    .orEmpty()
    .sortedBy(File::getName)

private fun hasCompletePageSet(pages: List<File>, expected: Int): Boolean =
    pages.size == expected && pages.withIndex().all { (offset, page) ->
        page.nameWithoutExtension.toIntOrNull() == offset + 1 && page.length() in 1..MAX_PAGE_FILE_BYTES
    }

private fun File.readTextOrNull(): String? = runCatching {
    if (isFile && length() in 1..MAX_META_BYTES) readText() else null
}.getOrNull()

private fun isSafeId(value: String): Boolean = value.matches(Regex("\\d{1,12}"))

internal fun sanitizeMetadataText(value: String): String =
    value.replace(Regex("[\\r\\n\\u0000]+"), " ").trim().take(MAX_META_TEXT_LENGTH)

private const val META_FILE_NAME = "meta.txt"
private const val MAX_META_BYTES = 16 * 1024L
private const val MAX_META_TEXT_LENGTH = 500
private const val MAX_DOWNLOAD_PAGES = 20_000
private const val MAX_PAGE_FILE_BYTES = 40L * 1024L * 1024L
private const val STALE_WORK_DIRECTORY_MILLIS = 14L * 24L * 60L * 60L * 1_000L
private const val CURRENT_DECODE_VERSION = "decode-v3"
private val WORK_DIRECTORY_NAME = Regex("^\\.(\\d{1,12})\\.[A-Za-z0-9_-]+\\.(?:partial|commit)$")

internal fun isCompatibleDownload(@Suppress("UNUSED_PARAMETER") chapterId: String, decodeVersion: String?): Boolean =
    decodeVersion == CURRENT_DECODE_VERSION
