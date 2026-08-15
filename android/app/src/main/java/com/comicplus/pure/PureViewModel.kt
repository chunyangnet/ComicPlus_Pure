package com.comicplus.pure

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comicplus.app.data.source.DirectJmCategory
import com.comicplus.app.data.source.DirectReaderPage
import com.comicplus.app.data.source.SourceChapterDto
import com.comicplus.app.data.source.SourceIds
import com.comicplus.app.ui.AppSettings
import com.comicplus.app.ui.CategoryUiState
import com.comicplus.app.ui.ComicResolveUiState
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.JmSearchUiState
import com.comicplus.app.ui.JmSourceUiState
import com.comicplus.app.ui.JmSourceUiItem
import com.comicplus.app.ui.PureUiState
import com.comicplus.app.ui.RankingsUiState
import com.comicplus.app.ui.ReaderUiState
import com.comicplus.app.ui.ReaderChapterSegment
import com.comicplus.app.ui.key
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.LinkedHashMap

class PureViewModel(
    private val gateway: JmGateway,
    private val downloadStore: DownloadStore,
    private val settingsStore: LocalSettingsStore,
) : ViewModel() {
    private val initialSettings = settingsStore.load()
    private val cachedSourceSnapshot = gateway.apply {
        setSourcePreferences(
            autoSelect = initialSettings.autoSelectSource,
            preferredHost = initialSettings.preferredSourceHost,
            preferredImageHost = initialSettings.preferredImageHost,
        )
    }.cachedSourceSnapshot()
    private val _state = MutableStateFlow(
        PureUiState(
            downloads = downloadStore.items.value,
            settings = initialSettings,
            sourceStatus = cachedSourceSnapshot.toUiState(),
        ),
    )
    val state: StateFlow<PureUiState> = _state.asStateFlow()

    private var homeJob: Job? = null
    private var categoryCatalogJob: Job? = null
    private var categoryJob: Job? = null
    private var rankingJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var readerJob: Job? = null
    private var readerWarmupJob: Job? = null
    private var lastProgressSignature: String? = null
    private val progressWriteMutex = Mutex()
    private var settingsSaveJob: Job? = null
    private var sourceRefreshJob: Job? = null
    private var sourceScheduleJob: Job? = null
    private val comicCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, JmComic>(COMIC_CACHE_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JmComic>?): Boolean =
                size > COMIC_CACHE_LIMIT
        },
    )
    private val downloadLimiter = Semaphore(permits = 2)

    init {
        viewModelScope.launch {
            downloadStore.items.collect { downloads -> _state.update { it.copy(downloads = downloads) } }
        }
        viewModelScope.launch { downloadStore.refresh() }
        refreshHome()
        loadCategories()
        loadRankings()
        sourceScheduleJob = viewModelScope.launch {
            val settings = _state.value.settings
            if (settings.autoSelectSource || settings.autoUpdateSourceList) {
                refreshSources(force = false, updateOfficialList = settings.autoUpdateSourceList)
            }
            while (isActive) {
                delay(SOURCE_REFRESH_INTERVAL_MS)
                val currentSettings = _state.value.settings
                if (currentSettings.autoSelectSource || currentSettings.autoUpdateSourceList) {
                    refreshSources(
                        force = currentSettings.autoUpdateSourceList,
                        updateOfficialList = currentSettings.autoUpdateSourceList,
                    )
                }
            }
        }
    }

    fun refreshHome() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching { gateway.home() }
                .onSuccess { items ->
                    val uiItems = items.map(JmRanking::toUiItem)
                    _state.update {
                        it.copy(
                            home = uiItems,
                            discoveryItems = uiItems.drop(8),
                            loading = false,
                            discoveryExhausted = true,
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { it.copy(loading = false, message = error.readable()) }
                }
        }
    }

    fun loadMoreDiscovery() {
        if (_state.value.discoveryLoading || _state.value.discoveryExhausted) return
        _state.update { it.copy(discoveryExhausted = true) }
    }

    fun loadRankings(order: String = _state.value.rankings.jmOrder) {
        val current = _state.value.rankings
        if (current.jmOrder == order && (current.jmLoading || current.jmLoaded && current.jmItems.isNotEmpty())) return
        rankingJob?.cancel()
        rankingJob = viewModelScope.launch {
            _state.update {
                val changingOrder = it.rankings.jmOrder != order
                it.copy(
                    rankings = it.rankings.copy(
                        jmOrder = order,
                        jmItems = if (changingOrder) emptyList() else it.rankings.jmItems,
                        jmLoading = true,
                        jmLoaded = if (changingOrder) false else it.rankings.jmLoaded,
                        jmError = null,
                    ),
                )
            }
            runCatching { gateway.category("0", order) }
                .onSuccess { items ->
                    _state.update { state ->
                        if (state.rankings.jmOrder != order) return@update state
                        state.copy(
                            rankings = state.rankings.copy(
                                jmOrder = order,
                                jmItems = items.map(JmRanking::toUiItem),
                                jmLoading = false,
                                jmLoaded = true,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        if (state.rankings.jmOrder != order) return@update state
                        state.copy(
                            rankings = state.rankings.copy(jmOrder = order, jmLoading = false, jmError = error.readable()),
                            message = error.readable(),
                        )
                    }
                }
        }
    }

    fun loadCategories() {
        if (_state.value.categories.isNotEmpty() || categoryCatalogJob?.isActive == true) return
        categoryCatalogJob = viewModelScope.launch {
            runCatching { gateway.categories() }
                .onSuccess { categories ->
                    _state.update { it.copy(categories = categories.map(JmCategory::toDirectCategory)) }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { it.copy(message = error.readable()) }
                }
        }
    }

    fun selectCategory(category: DirectJmCategory, order: String = _state.value.category.order) {
        selectCategory(category.slug, order)
    }

    fun selectCategory(slug: String, order: String = _state.value.category.order) {
        val normalizedSlug = slug.ifBlank { "0" }
        val current = _state.value.category
        if (
            current.selectedSlug == normalizedSlug &&
            current.order == order &&
            current.page > 0 &&
            current.items.isNotEmpty()
        ) return
        categoryJob?.cancel()
        categoryJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    category = CategoryUiState(
                        selectedSlug = normalizedSlug,
                        order = order,
                        loading = true,
                    ),
                )
            }
            runCatching { gateway.category(normalizedSlug, order, page = 1) }
                .onSuccess { items ->
                    _state.update { state ->
                        val category = state.category
                        if (category.selectedSlug != normalizedSlug || category.order != order) return@update state
                        val mapped = items.map(JmRanking::toUiItem).distinctBy(ComicUiItem::key)
                        state.copy(
                            category = category.copy(
                                items = mapped,
                                page = 1,
                                loading = false,
                                hasMore = mapped.isNotEmpty(),
                                error = null,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        val category = state.category
                        if (category.selectedSlug != normalizedSlug || category.order != order) return@update state
                        state.copy(
                            category = category.copy(loading = false, error = error.readable()),
                            message = error.readable(),
                        )
                    }
                }
        }
    }

    fun selectCategoryOrder(order: String) = selectCategory(_state.value.category.selectedSlug, order)

    fun loadMoreCategory() {
        val current = _state.value.category
        if (current.loading || current.loadingMore || !current.hasMore || current.page <= 0) return
        val slug = current.selectedSlug
        val order = current.order
        val nextPage = current.page + 1
        _state.update { state ->
            if (state.category.selectedSlug != slug || state.category.order != order) state
            else state.copy(category = state.category.copy(loadingMore = true, error = null))
        }
        categoryJob = viewModelScope.launch {
            runCatching { gateway.category(slug, order, nextPage) }
                .onSuccess { items ->
                    _state.update { state ->
                        val category = state.category
                        if (category.selectedSlug != slug || category.order != order) return@update state
                        val merged = mergeComicPage(category.items, items.map(JmRanking::toUiItem))
                        state.copy(
                            category = category.copy(
                                items = merged,
                                page = nextPage,
                                loadingMore = false,
                                hasMore = items.isNotEmpty() && merged.size > category.items.size,
                                error = null,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        val category = state.category
                        if (category.selectedSlug != slug || category.order != order) return@update state
                        state.copy(
                            category = category.copy(loadingMore = false, error = error.readable()),
                            message = error.readable(),
                        )
                    }
                }
        }
    }

    fun search(query: String, mainTag: Int = 0, order: String = "mr", page: Int = 1) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (page == 1) {
                parseJmId(query)?.let { id ->
                    _state.update {
                        it.copy(search = JmSearchUiState(query = query, mainTag = mainTag, order = order, submitted = true))
                    }
                    openComic(id)
                    return@launch
                }
            }
            _state.update {
                val previous = it.search
                it.copy(
                    search = previous.copy(
                        query = query,
                        mainTag = mainTag,
                        order = order,
                        submitted = true,
                        loading = page == 1,
                        loadingMore = page > 1,
                        items = if (page == 1) emptyList() else previous.items,
                        page = if (page == 1) 0 else previous.page,
                        total = if (page == 1) 0 else previous.total,
                        hasMore = if (page == 1) false else previous.hasMore,
                        redirectAid = null,
                        error = null,
                    ),
                )
            }
            runCatching { gateway.search(query, page, mainTag, order) }
                .onSuccess { result ->
                    _state.update {
                        val previous = it.search
                        it.copy(
                            search = previous.copy(
                                query = result.query,
                                mainTag = mainTag,
                                order = order,
                                items = if (page == 1) result.items.map(JmRanking::toUiItem).distinctBy(ComicUiItem::key)
                                else mergeComicPage(previous.items, result.items.map(JmRanking::toUiItem)),
                                page = result.page,
                                total = result.total,
                                redirectAid = result.redirectAid,
                                hasMore = result.hasMore,
                                loading = false,
                                loadingMore = false,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update {
                        it.copy(
                            search = it.search.copy(loading = false, loadingMore = false, error = error.readable()),
                            message = error.readable(),
                        )
                    }
                }
        }
    }

    fun loadMoreSearch() {
        val current = _state.value.search
        if (current.loading || current.loadingMore || !current.hasMore || current.query.isBlank()) return
        search(current.query, current.mainTag, current.order, current.page + 1)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = JmSearchUiState()) }
    }

    fun consumeSearchRedirect() {
        _state.update { state -> state.copy(search = state.search.copy(redirectAid = null)) }
    }

    fun openComic(item: ComicUiItem) = openComic(item.jmId)

    fun openComic(id: String) {
        detailJob?.cancel()
        readerWarmupJob?.cancel()
        detailJob = viewModelScope.launch {
            _state.update { it.copy(detail = ComicResolveUiState.Loading(SourceIds.Jm, id)) }
            runCatching { comicCache[id] ?: gateway.comic(id).also { comicCache[id] = it } }
                .onSuccess { comic ->
                    val progress = settingsStore.loadProgress(comic.id)
                    _state.update { it.copy(detail = comic.toResolveState(progress)) }
                    warmReaderEntry(comic, progress)
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { it.copy(detail = ComicResolveUiState.Error(SourceIds.Jm, id, error.readable())) }
                }
        }
    }

    fun dismissDetail() {
        detailJob?.cancel()
        readerWarmupJob?.cancel()
        _state.update { it.copy(detail = ComicResolveUiState.Idle, reader = ReaderUiState.Idle) }
    }

    private fun warmReaderEntry(comic: JmComic, progress: LocalReadingProgress?) {
        readerWarmupJob?.cancel()
        val settings = _state.value.settings
        val resumeProgress = progress.takeIf { settings.autoResumeReading }
        val chapter = comic.chapters.firstOrNull { it.id == resumeProgress?.chapterId }
            ?: comic.chapters.firstOrNull()
            ?: return
        readerWarmupJob = viewModelScope.launch {
            if (!settings.dataSaver) {
                gateway.warmImageConnections(comic.id, chapter.id)
            }
            runCatching { gateway.chapter(chapter.id) }
                .onSuccess { chapterPages ->
                    if (settings.dataSaver) return@onSuccess
                    val pageIndex = resumeProgress?.pageIndex
                        ?.coerceIn(0, chapterPages.pages.lastIndex.coerceAtLeast(0))
                        ?: 0
                    chapterPages.pages.getOrNull(pageIndex)?.let { page ->
                        runCatching {
                            gateway.prefetchPage(page, settings.readerImageQuality, settings.readerTurboMode)
                        }.onFailure { error -> error.rethrowCancellation() }
                    }
                }
                .onFailure { error -> error.rethrowCancellation() }
        }
    }

    fun openReader(state: ComicResolveUiState.Ready, chapter: SourceChapterDto, initialPageIndex: Int = 0) {
        openReader(
            comicId = state.jmId,
            comicTitle = state.title,
            chapter = chapter,
            chapters = state.chapters,
            initialPageIndex = initialPageIndex,
            preserveCurrent = false,
        )
    }

    private fun openReader(
        comicId: String,
        comicTitle: String,
        chapter: SourceChapterDto,
        chapters: List<SourceChapterDto>,
        initialPageIndex: Int,
        preserveCurrent: Boolean,
    ) {
        val previousReader = _state.value.reader as? ReaderUiState.Ready
        if (!_state.value.settings.dataSaver) {
            gateway.warmImageConnections(comicId, chapter.sourceChapterId)
        }
        readerJob?.cancel()
        readerJob = viewModelScope.launch {
            _state.update { state ->
                state.copy(
                    reader = if (preserveCurrent && previousReader != null) {
                        previousReader.copy(changingChapterTitle = chapter.title)
                    } else {
                        ReaderUiState.Loading(
                            SourceIds.Jm,
                            comicId,
                            comicTitle,
                            chapter.sourceChapterId,
                            chapter.title,
                            initialPageIndex,
                        )
                    },
                )
            }
            runCatching {
                gateway.chapter(chapter.sourceChapterId)
            }
                .onSuccess { chapterPages ->
                    _state.update {
                        it.copy(
                            reader = ReaderUiState.Ready(
                                source = SourceIds.Jm,
                                sourceId = comicId,
                                title = comicTitle,
                                chapterId = chapter.sourceChapterId,
                                chapterTitle = chapter.title.ifBlank { chapterPages.title },
                                pages = chapterPages.pages.map(JmPage::toDirectReaderPage),
                                chapters = chapters,
                                currentChapterIndex = chapters.indexOfFirst { item -> item.sourceChapterId == chapter.sourceChapterId }
                                    .coerceAtLeast(0),
                                initialPageIndex = initialPageIndex,
                                changingChapterTitle = null,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        state.copy(
                            reader = if (preserveCurrent && previousReader != null) {
                                previousReader.copy(changingChapterTitle = null)
                            } else {
                                ReaderUiState.Error(
                                    SourceIds.Jm,
                                    comicId,
                                    comicTitle,
                                    chapter.sourceChapterId,
                                    chapter.title,
                                    chapters,
                                    initialPageIndex,
                                    error.readable(),
                                )
                            },
                            message = if (preserveCurrent) "${chapter.title} 加载失败：${error.readable()}" else state.message,
                        )
                    }
                }
        }
    }

    fun selectReaderChapter(chapter: SourceChapterDto) {
        val current = _state.value.reader
        when (current) {
            is ReaderUiState.Ready -> {
                val resume = settingsStore.loadChapterProgress(current.sourceId, chapter.sourceChapterId)?.pageIndex ?: 0
                openReader(current.sourceId, current.title, chapter, current.chapters, resume, preserveCurrent = true)
            }
            is ReaderUiState.Error -> openReader(current.sourceId, current.title, chapter, current.chapters, 0, preserveCurrent = false)
            else -> Unit
        }
    }

    fun retryReaderChapter() {
        val current = _state.value.reader
        if (current is ReaderUiState.Error) {
            val chapter = current.chapters.firstOrNull { it.sourceChapterId == current.chapterId }
                ?: SourceChapterDto(current.chapterId, 1, current.chapterTitle)
            openReader(current.sourceId, current.title, chapter, current.chapters, current.initialPageIndex, preserveCurrent = false)
        }
    }

    suspend fun loadReaderChapterSegment(chapter: SourceChapterDto): ReaderChapterSegment {
        val current = _state.value.reader as? ReaderUiState.Ready ?: throw JmSourceException()
        if (!current.sourceId.matches(Regex("\\d{1,12}"))) throw JmSourceException()
        if (!_state.value.settings.dataSaver) {
            gateway.warmImageConnections(current.sourceId, chapter.sourceChapterId)
        }
        val chapterPages = gateway.chapter(chapter.sourceChapterId)
        return ReaderChapterSegment(
            chapterId = chapter.sourceChapterId,
            chapterTitle = chapter.title.ifBlank { chapterPages.title },
            chapterIndex = current.chapters.indexOfFirst { it.sourceChapterId == chapter.sourceChapterId }
                .coerceAtLeast(0),
            pages = chapterPages.pages.map(JmPage::toDirectReaderPage),
        )
    }

    fun closeReader() {
        readerJob?.cancel()
        _state.update { it.copy(reader = ReaderUiState.Idle) }
    }

    suspend fun loadReaderPage(page: DirectReaderPage, onAspectRatio: (Float) -> Unit): Bitmap = gateway.loadPage(
        page.toJmPage(),
        quality = _state.value.settings.readerImageQuality,
        turboMode = _state.value.settings.readerTurboMode,
        hedgeImageHosts = _state.value.settings.let { !it.dataSaver && it.autoSelectSource },
        onAspectRatio = onAspectRatio,
    )
    suspend fun prefetchReaderPage(page: DirectReaderPage) {
        val settings = _state.value.settings
        gateway.prefetchPage(page.toJmPage(), settings.readerImageQuality, settings.readerTurboMode)
    }
    fun cachedReaderPage(page: DirectReaderPage): Bitmap? {
        val settings = _state.value.settings
        return gateway.cachedPage(page.toJmPage(), settings.readerImageQuality, settings.readerTurboMode)
    }
    fun recordReaderProgress(comicId: String, chapterId: String, pageIndex: Int, pageCount: Int) {
        if (comicId.isBlank() || chapterId.isBlank() || pageCount <= 0) return
        val safePageIndex = pageIndex.coerceIn(0, pageCount - 1)
        persistReaderProgress(comicId, chapterId, safePageIndex, pageCount)
        _state.update { state ->
            val detail = state.detail as? ComicResolveUiState.Ready ?: return@update state
            if (detail.jmId != comicId) return@update state
            state.copy(detail = detail.copy(resumeChapterId = chapterId, resumePageIndex = safePageIndex))
        }
    }

    private fun persistReaderProgress(comicId: String, chapterId: String, pageIndex: Int, pageCount: Int) {
        val safePageIndex = pageIndex.coerceIn(0, pageCount - 1)
        val signature = "$comicId|$chapterId|$safePageIndex|$pageCount"
        if (signature == lastProgressSignature) return
        lastProgressSignature = signature
        viewModelScope.launch {
            progressWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    settingsStore.saveProgress(
                        comicId = comicId,
                        chapterId = chapterId,
                        pageIndex = safePageIndex,
                        pageCount = pageCount,
                    )
                }
            }
        }
    }

    fun updateSettings(settings: AppSettings) {
        val previous = _state.value.settings
        val normalized = when {
            settings.readerTurboMode && !previous.readerTurboMode -> settings.copy(dataSaver = false)
            settings.dataSaver && !previous.dataSaver -> settings.copy(readerTurboMode = false)
            settings.readerTurboMode && settings.dataSaver -> settings.copy(dataSaver = false)
            else -> settings
        }
        val sourceSnapshot = gateway.setSourcePreferences(
            autoSelect = normalized.autoSelectSource,
            preferredHost = normalized.preferredSourceHost,
            preferredImageHost = normalized.preferredImageHost,
        )
        _state.update {
            it.copy(
                settings = normalized,
                sourceStatus = sourceSnapshot.toUiState(
                    checking = it.sourceStatus.checking,
                    error = it.sourceStatus.error,
                ),
            )
        }
        settingsSaveJob?.cancel()
        settingsSaveJob = viewModelScope.launch {
            delay(250)
            settingsStore.save(_state.value.settings)
        }
        if (normalized.autoSelectSource != previous.autoSelectSource ||
            normalized.preferredSourceHost != previous.preferredSourceHost ||
            (normalized.autoUpdateSourceList && !previous.autoUpdateSourceList)
        ) {
            refreshSources(
                force = normalized.autoUpdateSourceList && !previous.autoUpdateSourceList,
                updateOfficialList = normalized.autoUpdateSourceList && !previous.autoUpdateSourceList,
            )
        }
    }

    fun refreshSources(force: Boolean = true, updateOfficialList: Boolean = true) {
        sourceRefreshJob?.cancel()
        sourceRefreshJob = viewModelScope.launch {
            val current = _state.value.sourceStatus
            _state.update { it.copy(sourceStatus = current.copy(checking = true, error = null)) }
            runCatching { gateway.refreshSourceList(force, updateOfficialList) }
                .onSuccess { snapshot ->
                    _state.update {
                        it.copy(sourceStatus = snapshot.toUiState())
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        state.copy(sourceStatus = state.sourceStatus.copy(checking = false, error = error.readable()))
                    }
                }
        }
    }

    fun clearReaderCache() {
        viewModelScope.launch {
            runCatching { gateway.clearPageCache() }
                .onSuccess { bytes ->
                    _state.update { it.copy(message = if (bytes > 0) "已清理阅读缓存" else "阅读缓存已经是空的") }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { it.copy(message = "清理缓存失败：${error.readable()}") }
                }
        }
    }

    fun openDownloaded(item: DownloadedChapter) {
        viewModelScope.launch {
            val files = downloadStore.localPages(item)
            if (!item.complete || files.size != item.pageCount || files.isEmpty()) {
                _state.update { it.copy(message = "本地章节文件不完整，请重新下载") }
                return@launch
            }
            val chapter = SourceChapterDto(item.chapterId, 1, item.chapterTitle)
            val resumePageIndex = settingsStore.loadChapterProgress(item.comicId, item.chapterId)?.pageIndex ?: 0
            val pages = files.mapIndexed { index, file ->
                DirectReaderPage(
                    index = index + 1,
                    photoId = item.chapterId,
                    fileName = file.name,
                    scrambleId = "0",
                    url = file.toURI().toString(),
                    referer = "",
                    localPath = file.absolutePath,
                )
            }
            _state.update {
                it.copy(
                    reader = ReaderUiState.Ready(
                        source = SourceIds.Jm,
                        sourceId = item.comicId,
                        title = item.comicTitle,
                        chapterId = item.chapterId,
                        chapterTitle = item.chapterTitle,
                        pages = pages,
                        chapters = listOf(chapter),
                        currentChapterIndex = 0,
                        initialPageIndex = resumePageIndex.coerceIn(0, pages.lastIndex),
                    ),
                )
            }
        }
    }

    fun downloadChapter(state: ComicResolveUiState.Ready, chapter: SourceChapterDto) {
        val comic = comicCache[state.jmId] ?: JmComic(
            state.jmId,
            state.title,
            state.description,
            state.coverUrl,
            emptyList(),
            emptyList(),
            state.chapters.map { JmChapter(it.sourceChapterId, it.index, it.title) },
        )
        downloadChapter(comic, JmChapter(chapter.sourceChapterId, chapter.index, chapter.title))
    }

    private fun downloadChapter(comic: JmComic, chapter: JmChapter) {
        val key = "${comic.id}:${chapter.id}"
        if (!downloadStore.start(key)) return
        _state.update { it.copy(downloadProgress = it.downloadProgress + (key to 0f)) }
        viewModelScope.launch {
            try {
                downloadLimiter.withPermit {
                    if (downloadStore.isDownloaded(comic.id, chapter.id)) {
                        _state.update { it.copy(message = "${chapter.title} 已下载") }
                        return@withPermit
                    }
                    val pages = gateway.chapter(chapter.id)
                    val partialDir = downloadStore.prepareDownload(comic.id, chapter.id, pages.pages.size)
                    var downloaded = 0
                    var bytes = 0L
                    var lastPublishedProgress = 0f
                    var lastPublishedAt = 0L
                    pages.pages.forEach { page ->
                        val target = downloadStore.partialPageFile(partialDir, page.index)
                        gateway.downloadPage(page, target) { done, total ->
                            val fraction = if (total > 0) done.toFloat() / total else 0f
                            val progress = (downloaded + fraction) / pages.pages.size
                            val now = System.nanoTime()
                            if (shouldPublishProgress(lastPublishedProgress, progress, now - lastPublishedAt, done == total)) {
                                lastPublishedProgress = progress
                                lastPublishedAt = now
                                _state.update {
                                    it.copy(downloadProgress = it.downloadProgress + (key to progress))
                                }
                            }
                        }
                        downloaded++
                        bytes += target.length()
                        val progress = downloaded.toFloat() / pages.pages.size
                        lastPublishedProgress = progress
                        lastPublishedAt = System.nanoTime()
                        _state.update {
                            it.copy(downloadProgress = it.downloadProgress + (key to progress))
                        }
                    }
                    downloadStore.completeDownload(
                        DownloadedChapter(
                            comic.id,
                            comic.title,
                            chapter.id,
                            chapter.title,
                            pages.pages.size,
                            downloaded,
                            bytes,
                            true,
                        ),
                    )
                    _state.update { it.copy(message = "${chapter.title} 下载完成") }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.copy(message = "下载失败，重试可继续：${error.readable()}") }
            } finally {
                downloadStore.finish(key)
                _state.update { it.copy(downloadProgress = it.downloadProgress - key) }
            }
        }
    }

    fun deleteDownload(item: DownloadedChapter) {
        val key = "${item.comicId}:${item.chapterId}"
        if (downloadStore.isRunning(key)) {
            _state.update { it.copy(message = "章节正在下载，暂时无法删除") }
            return
        }
        viewModelScope.launch {
            runCatching { downloadStore.delete(item) }
                .onSuccess { _state.update { it.copy(message = "已删除 ${item.chapterTitle}") } }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { it.copy(message = "删除失败：${error.readable()}") }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        sourceRefreshJob?.cancel()
        sourceScheduleJob?.cancel()
        settingsStore.save(_state.value.settings)
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PureViewModel(
            gateway = JmGateway(context),
            downloadStore = DownloadStore(context),
            settingsStore = LocalSettingsStore(context),
        ) as T
    }
}

private fun JmRanking.toUiItem() = ComicUiItem(
    jmId = id,
    title = title,
    subtitle = listOf(category, badge).filter(String::isNotBlank).joinToString(" · "),
    metric = when {
        likes != null -> "JM 收藏 ${likes.compact()}"
        views != null -> "JM 浏览 ${views.compact()}"
        else -> "JM 官方源"
    },
    accentIndex = id.takeLast(4).toIntOrNull() ?: title.hashCode(),
    coverUrl = coverUrl,
)

internal fun mergeComicPage(
    existing: List<ComicUiItem>,
    incoming: List<ComicUiItem>,
): List<ComicUiItem> = buildList(existing.size + incoming.size) {
    val seen = HashSet<String>(existing.size + incoming.size)
    (existing + incoming).forEach { item ->
        if (seen.add(item.key)) add(item)
    }
}

internal fun shouldPublishProgress(previous: Float, current: Float, elapsedNanos: Long, completed: Boolean): Boolean =
    completed || current - previous >= 0.01f || elapsedNanos >= 150_000_000L

private fun JmCategory.toDirectCategory() = DirectJmCategory(id, name, slug, "slug", totalAlbums)

private fun JmComic.toResolveState(progress: LocalReadingProgress?) = ComicResolveUiState.Ready(
    source = SourceIds.Jm,
    jmId = id,
    title = title,
    description = description,
    coverUrl = coverUrl,
    cacheState = "direct",
    refreshing = false,
    chapters = chapters.map { SourceChapterDto(it.id, it.index, it.title) },
    resumeChapterId = progress?.chapterId,
    resumePageIndex = progress?.pageIndex ?: 0,
)

private fun JmPage.toDirectReaderPage() = DirectReaderPage(
    index = index,
    photoId = photoId,
    fileName = fileName,
    scrambleId = scrambleId,
    url = url,
    alternativeUrls = alternativeUrls,
    referer = referer,
    localPath = localPath,
)

private fun DirectReaderPage.toJmPage() = JmPage(
    index = index,
    photoId = photoId,
    fileName = fileName,
    scrambleId = scrambleId,
    url = url,
    alternativeUrls = alternativeUrls,
    referer = referer,
    localPath = localPath ?: if (url.startsWith("file:")) runCatching { java.io.File(java.net.URI(url)).absolutePath }.getOrNull() else null,
)

private fun JmSourceSnapshot.toUiState(
    checking: Boolean = false,
    error: String? = null,
) = JmSourceUiState(
    items = endpoints.map { endpoint -> JmSourceUiItem(endpoint.host, endpoint.latencyMs) },
    selectedHost = selectedHost,
    updatedAt = updatedAt,
    imageItems = imageEndpoints.map { endpoint -> JmSourceUiItem(endpoint.host, endpoint.latencyMs) },
    selectedImageHost = selectedImageHost,
    imageUpdatedAt = imageUpdatedAt,
    checking = checking,
    error = error,
)

private fun Long.compact(): String = when {
    this >= 1_000_000 -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000 -> "%.1fK".format(this / 1_000.0)
    else -> toString()
}

private fun Throwable.readable(): String = message?.take(120).orEmpty().ifBlank { "JM 官方源连接失败" }

private fun Throwable.rethrowCancellation() {
    if (this is CancellationException) throw this
}

private fun parseJmId(raw: String): String? {
    val value = raw.trim()
    return when {
        value.matches(Regex("\\d{1,12}")) -> value
        else -> Regex("(?i)^jm\\s*[:#-]?\\s*(\\d{1,12})$").matchEntire(value)?.groupValues?.getOrNull(1)
    }
}

private const val COMIC_CACHE_LIMIT = 48
private const val SOURCE_REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1_000L
