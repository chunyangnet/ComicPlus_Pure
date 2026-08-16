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
import com.comicplus.app.ui.AppUpdateUiState
import com.comicplus.app.ui.CategoryUiState
import com.comicplus.app.ui.ComicResolveUiState
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.JmSearchUiState
import com.comicplus.app.ui.JmBrowseOptionUi
import com.comicplus.app.ui.JmCommentUiItem
import com.comicplus.app.ui.JmCommentsUiState
import com.comicplus.app.ui.JmAccountStatus
import com.comicplus.app.ui.JmAccountUiState
import com.comicplus.app.ui.JmTagGroupUi
import com.comicplus.app.ui.JmSourceUiState
import com.comicplus.app.ui.JmSourceUiItem
import com.comicplus.app.ui.PureUiState
import com.comicplus.app.ui.RankingsUiState
import com.comicplus.app.ui.ReaderUiState
import com.comicplus.app.ui.ReaderChapterSegment
import com.comicplus.app.ui.ReadingHistoryItem
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
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PureViewModel(
    private val gateway: JmGateway,
    private val downloadStore: DownloadStore,
    private val settingsStore: LocalSettingsStore,
    private val releaseClient: GitHubReleaseClient,
    currentVersion: String,
    private val libraryStore: LibraryStore = LibraryStore(),
    private val sessionStore: JmSessionStore? = null,
) : ViewModel() {
    private data class CachedCommentPage(
        val page: JmCommentPage,
        val cachedAt: Long,
    )

    private data class ReaderProgressSnapshot(
        val comicId: String,
        val chapterId: String,
        val pageIndex: Int,
        val pageCount: Int,
    )

    private val initialSettings = settingsStore.load()
    private val initialFavorites = libraryStore.loadFavorites()
    private val initialHistory = libraryStore.loadHistory()
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
            favorites = initialFavorites,
            history = initialHistory,
            sourceStatus = cachedSourceSnapshot.toUiState(),
            appUpdate = AppUpdateUiState(currentVersion = currentVersion),
        ),
    )
    val state: StateFlow<PureUiState> = _state.asStateFlow()

    private var homeJob: Job? = null
    private var categoryCatalogJob: Job? = null
    private var categoryJob: Job? = null
    private var rankingJob: Job? = null
    private var weeklyCatalogJob: Job? = null
    private var weeklyJob: Job? = null
    private var typeRankingJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var commentsJob: Job? = null
    private var accountJob: Job? = null
    private var readerJob: Job? = null
    private var readerWarmupJob: Job? = null
    private var lastProgressSignature: String? = null
    private val progressWriteLock = ReentrantLock()
    private val progressPersistGeneration = AtomicLong()
    @Volatile private var latestProgressSnapshot: ReaderProgressSnapshot? = null
    // onCleared() is synchronous, so a blocking lock is required to serialize
    // its final snapshot with already-running IO writes. The JSON snapshots are
    // bounded and normally small, keeping the critical section short.
    private val libraryWriteLock = ReentrantLock()
    private val favoritesPersistGeneration = AtomicLong()
    private val historyPersistGeneration = AtomicLong()
    private var settingsSaveJob: Job? = null
    private var sourceRefreshJob: Job? = null
    private var sourceScheduleJob: Job? = null
    private var updateCheckJob: Job? = null
    private val searchRequestGeneration = AtomicLong()
    private val detailRequestGeneration = AtomicLong()
    private val commentsRequestGeneration = AtomicLong()
    private val readerRequestGeneration = AtomicLong()
    private val homeRequestGeneration = AtomicLong()
    private val categoryCatalogRequestGeneration = AtomicLong()
    private val categoryRequestGeneration = AtomicLong()
    private val rankingRequestGeneration = AtomicLong()
    private val weeklyCatalogRequestGeneration = AtomicLong()
    private val weeklyRequestGeneration = AtomicLong()
    private val typeRankingRequestGeneration = AtomicLong()
    private val sourceRefreshRequestGeneration = AtomicLong()
    private val updateCheckRequestGeneration = AtomicLong()
    private val comicCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, JmComic>(COMIC_CACHE_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JmComic>?): Boolean =
                size > COMIC_CACHE_LIMIT
        },
    )
    private val commentCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedCommentPage>(COMMENT_CACHE_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedCommentPage>?): Boolean =
                size > COMMENT_CACHE_LIMIT
        },
    )
    private val downloadLimiter = Semaphore(permits = 2)
    private val favoriteOperations = ConcurrentHashMap.newKeySet<String>()

    init {
        viewModelScope.launch {
            downloadStore.items.collect { downloads -> _state.update { it.copy(downloads = downloads) } }
        }
        viewModelScope.launch { downloadStore.refresh() }
        sessionStore?.load()?.let { session ->
            accountJob = viewModelScope.launch { restoreAccount(session) }
        }
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
        val requestGeneration = homeRequestGeneration.incrementAndGet()
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _state.update { state ->
                if (requestGeneration != homeRequestGeneration.get()) return@update state
                state.copy(loading = true, message = null)
            }
            runCatching { gateway.home() }
                .onSuccess { items ->
                    if (requestGeneration != homeRequestGeneration.get()) return@onSuccess
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
                    _state.update { state ->
                        if (requestGeneration != homeRequestGeneration.get()) return@update state
                        state.copy(loading = false, message = error.readable())
                    }
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
        val requestGeneration = rankingRequestGeneration.incrementAndGet()
        rankingJob?.cancel()
        rankingJob = viewModelScope.launch {
            _state.update {
                if (requestGeneration != rankingRequestGeneration.get()) return@update it
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
                        if (requestGeneration != rankingRequestGeneration.get() || state.rankings.jmOrder != order) return@update state
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
                        if (requestGeneration != rankingRequestGeneration.get() || state.rankings.jmOrder != order) return@update state
                        state.copy(
                            rankings = state.rankings.copy(jmOrder = order, jmLoading = false, jmError = error.readable()),
                            message = error.readable(),
                        )
                    }
                }
        }
    }

    fun ensureOfficialBrowse() {
        loadCategories()
        loadWeeklyCatalog()
        val ranking = _state.value.officialBrowse.typeRanking
        if (!ranking.loading && !ranking.loaded) {
            loadTypeRanking(ranking.selectedSlug, ranking.order)
        }
    }

    fun loadWeeklyCatalog(force: Boolean = false) {
        val current = _state.value.officialBrowse.weekly
        if (!force && (current.catalogLoading || current.categories.isNotEmpty() && current.types.isNotEmpty())) return
        val requestGeneration = weeklyCatalogRequestGeneration.incrementAndGet()
        weeklyCatalogJob?.cancel()
        weeklyCatalogJob = viewModelScope.launch {
            _state.update { state ->
                if (requestGeneration != weeklyCatalogRequestGeneration.get()) return@update state
                state.copy(
                    officialBrowse = state.officialBrowse.copy(
                        weekly = state.officialBrowse.weekly.copy(catalogLoading = true, error = null),
                    ),
                )
            }
            runCatching { gateway.weekCatalog() }
                .onSuccess { catalog ->
                    if (requestGeneration != weeklyCatalogRequestGeneration.get()) return@onSuccess
                    val categoryOptions = catalog.categories
                        .map { JmBrowseOptionUi(it.id, it.title) }
                        .distinctBy(JmBrowseOptionUi::id)
                    val typeOptions = catalog.types
                        .map { JmBrowseOptionUi(it.id, it.title) }
                        .distinctBy(JmBrowseOptionUi::id)
                    if (categoryOptions.isEmpty() || typeOptions.isEmpty()) {
                        _state.update { state ->
                            if (requestGeneration != weeklyCatalogRequestGeneration.get()) return@update state
                            state.copy(
                                officialBrowse = state.officialBrowse.copy(
                                    weekly = state.officialBrowse.weekly.copy(
                                        categories = categoryOptions,
                                        types = typeOptions,
                                        selectedCategoryId = "",
                                        selectedTypeId = "",
                                        catalogLoading = false,
                                        loading = false,
                                        loaded = false,
                                        error = "JM 每周目录暂时为空，请稍后重试",
                                    ),
                                ),
                            )
                        }
                        return@onSuccess
                    }
                    val previous = _state.value.officialBrowse.weekly
                    val categoryId = previous.selectedCategoryId.takeIf { id -> categoryOptions.any { it.id == id } }
                        ?: categoryOptions.first().id
                    val typeId = previous.selectedTypeId.takeIf { id -> typeOptions.any { it.id == id } }
                        ?: typeOptions.first().id
                    _state.update { state ->
                        if (requestGeneration != weeklyCatalogRequestGeneration.get()) return@update state
                        state.copy(
                            officialBrowse = state.officialBrowse.copy(
                                weekly = state.officialBrowse.weekly.copy(
                                    categories = categoryOptions,
                                    types = typeOptions,
                                    selectedCategoryId = categoryId,
                                    selectedTypeId = typeId,
                                    catalogLoading = false,
                                    error = null,
                                ),
                            ),
                        )
                    }
                    if (requestGeneration == weeklyCatalogRequestGeneration.get()) loadWeekly(categoryId, typeId)
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        if (requestGeneration != weeklyCatalogRequestGeneration.get()) return@update state
                        state.copy(
                            officialBrowse = state.officialBrowse.copy(
                                weekly = state.officialBrowse.weekly.copy(
                                    catalogLoading = false,
                                    loading = false,
                                    error = error.readable(),
                                ),
                            ),
                        )
                    }
                }
        }
    }

    fun selectWeeklyCategory(categoryId: String) = loadWeekly(
        categoryId = categoryId,
        typeId = _state.value.officialBrowse.weekly.selectedTypeId,
    )

    fun selectWeeklyType(typeId: String) = loadWeekly(
        categoryId = _state.value.officialBrowse.weekly.selectedCategoryId,
        typeId = typeId,
    )

    fun loadWeekly(
        categoryId: String = _state.value.officialBrowse.weekly.selectedCategoryId,
        typeId: String = _state.value.officialBrowse.weekly.selectedTypeId,
        force: Boolean = false,
    ) {
        if (categoryId.isBlank() || typeId.isBlank()) {
            loadWeeklyCatalog(force)
            return
        }
        val current = _state.value.officialBrowse.weekly
        val sameFilter = current.selectedCategoryId == categoryId && current.selectedTypeId == typeId
        if (!force && sameFilter && (current.loading || current.loaded)) return
        val requestGeneration = weeklyRequestGeneration.incrementAndGet()
        weeklyJob?.cancel()
        weeklyJob = viewModelScope.launch {
            _state.update { state ->
                if (requestGeneration != weeklyRequestGeneration.get()) return@update state
                val weekly = state.officialBrowse.weekly
                val changingFilter = weekly.selectedCategoryId != categoryId || weekly.selectedTypeId != typeId
                state.copy(
                    officialBrowse = state.officialBrowse.copy(
                        weekly = weekly.copy(
                            selectedCategoryId = categoryId,
                            selectedTypeId = typeId,
                            items = if (changingFilter) emptyList() else weekly.items,
                            total = if (changingFilter) 0 else weekly.total,
                            loading = true,
                            loaded = if (changingFilter) false else weekly.loaded,
                            error = null,
                        ),
                    ),
                )
            }
            runCatching { gateway.week(categoryId, typeId) }
                .onSuccess { page ->
                    _state.update { state ->
                        if (requestGeneration != weeklyRequestGeneration.get()) return@update state
                        val weekly = state.officialBrowse.weekly
                        if (weekly.selectedCategoryId != categoryId || weekly.selectedTypeId != typeId) return@update state
                        state.copy(
                            officialBrowse = state.officialBrowse.copy(
                                weekly = weekly.copy(
                                    items = page.items.map(JmRanking::toUiItem),
                                    total = page.total,
                                    loading = false,
                                    loaded = true,
                                ),
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        if (requestGeneration != weeklyRequestGeneration.get()) return@update state
                        val weekly = state.officialBrowse.weekly
                        if (weekly.selectedCategoryId != categoryId || weekly.selectedTypeId != typeId) return@update state
                        state.copy(
                            officialBrowse = state.officialBrowse.copy(
                                weekly = weekly.copy(loading = false, error = error.readable()),
                            ),
                        )
                    }
                }
        }
    }

    fun loadTypeRanking(
        slug: String = _state.value.officialBrowse.typeRanking.selectedSlug,
        order: String = _state.value.officialBrowse.typeRanking.order,
        force: Boolean = false,
    ) {
        val safeSlug = slug.trim().take(MAX_BROWSE_OPTION_LENGTH).ifBlank { "doujin" }
        val current = _state.value.officialBrowse.typeRanking
        val sameFilter = current.selectedSlug == safeSlug && current.order == order
        if (!force && sameFilter && (current.loading || current.loaded)) return
        val requestGeneration = typeRankingRequestGeneration.incrementAndGet()
        typeRankingJob?.cancel()
        typeRankingJob = viewModelScope.launch {
            _state.update { state ->
                if (requestGeneration != typeRankingRequestGeneration.get()) return@update state
                val ranking = state.officialBrowse.typeRanking
                val changingFilter = ranking.selectedSlug != safeSlug || ranking.order != order
                state.copy(
                    officialBrowse = state.officialBrowse.copy(
                        typeRanking = ranking.copy(
                            selectedSlug = safeSlug,
                            order = order,
                            items = if (changingFilter) emptyList() else ranking.items,
                            total = if (changingFilter) 0 else ranking.total,
                            loading = true,
                            loaded = if (changingFilter) false else ranking.loaded,
                            error = null,
                        ),
                    ),
                )
            }
            runCatching { gateway.categoryPage(safeSlug, order) }
                .onSuccess { page ->
                    _state.update { state ->
                        if (requestGeneration != typeRankingRequestGeneration.get()) return@update state
                        val ranking = state.officialBrowse.typeRanking
                        if (ranking.selectedSlug != safeSlug || ranking.order != order) return@update state
                        state.copy(
                            officialBrowse = state.officialBrowse.copy(
                                typeRanking = ranking.copy(
                                    items = page.items.map(JmRanking::toUiItem),
                                    total = page.total,
                                    loading = false,
                                    loaded = true,
                                ),
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        if (requestGeneration != typeRankingRequestGeneration.get()) return@update state
                        val ranking = state.officialBrowse.typeRanking
                        if (ranking.selectedSlug != safeSlug || ranking.order != order) return@update state
                        state.copy(
                            officialBrowse = state.officialBrowse.copy(
                                typeRanking = ranking.copy(loading = false, error = error.readable()),
                            ),
                        )
                    }
                }
        }
    }

    fun loadCategories() {
        if (_state.value.officialBrowse.catalogLoaded || categoryCatalogJob?.isActive == true) return
        val requestGeneration = categoryCatalogRequestGeneration.incrementAndGet()
        categoryCatalogJob = viewModelScope.launch {
            _state.update {
                if (requestGeneration != categoryCatalogRequestGeneration.get()) return@update it
                it.copy(officialBrowse = it.officialBrowse.copy(catalogLoading = true))
            }
            runCatching { gateway.categoryCatalog() }
                .onSuccess { catalog ->
                    _state.update {
                        if (requestGeneration != categoryCatalogRequestGeneration.get()) return@update it
                        it.copy(
                            categories = catalog.categories.map(JmCategory::toDirectCategory),
                            officialBrowse = it.officialBrowse.copy(
                                tagGroups = catalog.tagGroups.map { group -> JmTagGroupUi(group.title, group.tags) },
                                catalogLoading = false,
                                catalogLoaded = true,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update {
                        if (requestGeneration != categoryCatalogRequestGeneration.get()) return@update it
                        it.copy(
                            officialBrowse = it.officialBrowse.copy(catalogLoading = false),
                            message = error.readable(),
                        )
                    }
                }
        }
    }

    fun selectCategory(category: DirectJmCategory, order: String = _state.value.category.order) {
        selectCategory(category.slug, order)
    }

    fun selectCategory(slug: String, order: String = _state.value.category.order) {
        val normalizedSlug = slug.trim().take(MAX_BROWSE_OPTION_LENGTH).ifBlank { "0" }
        val current = _state.value.category
        if (
            current.selectedSlug == normalizedSlug &&
            current.order == order &&
            current.page > 0 &&
            current.items.isNotEmpty()
        ) return
        val requestGeneration = categoryRequestGeneration.incrementAndGet()
        categoryJob?.cancel()
        categoryJob = viewModelScope.launch {
            _state.update {
                if (requestGeneration != categoryRequestGeneration.get()) return@update it
                it.copy(
                    category = CategoryUiState(
                        selectedSlug = normalizedSlug,
                        order = order,
                        loading = true,
                    ),
                )
            }
            runCatching { gateway.categoryPage(normalizedSlug, order, page = 1) }
                .onSuccess { result ->
                    _state.update { state ->
                        val category = state.category
                        if (requestGeneration != categoryRequestGeneration.get() ||
                            category.selectedSlug != normalizedSlug || category.order != order
                        ) return@update state
                        if (!isForwardPageResponse(category.page, requestedPage = 1, result.page)) {
                            return@update state.copy(
                                category = category.copy(
                                    loading = false,
                                    hasMore = false,
                                    error = INVALID_PAGINATION_MESSAGE,
                                ),
                            )
                        }
                        val mapped = result.items.map(JmRanking::toUiItem).distinctBy(ComicUiItem::key)
                        state.copy(
                            category = category.copy(
                                items = mapped,
                                page = 1,
                                loading = false,
                                hasMore = result.hasMore && mapped.isNotEmpty(),
                                error = null,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        val category = state.category
                        if (requestGeneration != categoryRequestGeneration.get() ||
                            category.selectedSlug != normalizedSlug || category.order != order
                        ) return@update state
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
        if (current.loading || current.loadingMore || !current.hasMore ||
            current.page <= 0 || current.page >= MAX_PAGINATION_PAGE
        ) return
        val slug = current.selectedSlug
        val order = current.order
        val nextPage = current.page + 1
        val requestGeneration = categoryRequestGeneration.incrementAndGet()
        categoryJob?.cancel()
        _state.update { state ->
            if (requestGeneration != categoryRequestGeneration.get() ||
                state.category.selectedSlug != slug || state.category.order != order
            ) state
            else state.copy(category = state.category.copy(loadingMore = true, error = null))
        }
        categoryJob = viewModelScope.launch {
            runCatching { gateway.categoryPage(slug, order, nextPage) }
                .onSuccess { result ->
                    _state.update { state ->
                        val category = state.category
                        if (requestGeneration != categoryRequestGeneration.get() ||
                            category.selectedSlug != slug || category.order != order
                        ) return@update state
                        if (!isForwardPageResponse(category.page, nextPage, result.page)) {
                            return@update state.copy(
                                category = category.copy(
                                    loadingMore = false,
                                    hasMore = false,
                                    error = INVALID_PAGINATION_MESSAGE,
                                ),
                            )
                        }
                        val incoming = result.items.map(JmRanking::toUiItem)
                        val merged = mergeComicPage(category.items, incoming)
                        state.copy(
                            category = category.copy(
                                items = merged,
                                page = result.page,
                                loadingMore = false,
                                hasMore = result.hasMore && incoming.isNotEmpty() && merged.size > category.items.size,
                                error = null,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        val category = state.category
                        if (requestGeneration != categoryRequestGeneration.get() ||
                            category.selectedSlug != slug || category.order != order
                        ) return@update state
                        state.copy(
                            category = category.copy(loadingMore = false, error = error.readable()),
                            message = error.readable(),
                        )
                    }
                }
        }
    }

    fun search(query: String, mainTag: Int = 0, order: String = "mr", page: Int = 1) {
        val normalizedQuery = query.trim().take(MAX_SEARCH_QUERY_LENGTH)
        val safeMainTag = mainTag.coerceIn(0, 4)
        val safePage = page.coerceIn(1, MAX_PAGINATION_PAGE)
        val requestGeneration = searchRequestGeneration.incrementAndGet()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (safePage == 1) {
                parseJmId(normalizedQuery)?.let { id ->
                    _state.update { state ->
                        if (requestGeneration != searchRequestGeneration.get()) return@update state
                        state.copy(search = JmSearchUiState(query = normalizedQuery, mainTag = safeMainTag, order = order, submitted = true))
                    }
                    if (requestGeneration != searchRequestGeneration.get()) return@launch
                    openComic(id)
                    return@launch
                }
            }
            _state.update { state ->
                if (requestGeneration != searchRequestGeneration.get()) return@update state
                val previous = state.search
                state.copy(
                    search = previous.copy(
                        query = normalizedQuery,
                        mainTag = safeMainTag,
                        order = order,
                        submitted = true,
                        loading = safePage == 1,
                        loadingMore = safePage > 1,
                        items = if (safePage == 1) emptyList() else previous.items,
                        page = if (safePage == 1) 0 else previous.page,
                        total = if (safePage == 1) 0 else previous.total,
                        hasMore = if (safePage == 1) false else previous.hasMore,
                        redirectAid = null,
                        error = null,
                    ),
                )
            }
            runCatching { gateway.search(normalizedQuery, safePage, safeMainTag, order) }
                .onSuccess { result ->
                    _state.update { state ->
                        if (requestGeneration != searchRequestGeneration.get()) return@update state
                        val previous = state.search
                        if (!isForwardPageResponse(previous.page, safePage, result.page)) {
                            return@update state.copy(
                                search = previous.copy(
                                    loading = false,
                                    loadingMore = false,
                                    hasMore = false,
                                    error = INVALID_PAGINATION_MESSAGE,
                                ),
                            )
                        }
                        state.copy(
                            search = previous.copy(
                                query = result.query,
                                mainTag = safeMainTag,
                                order = order,
                                items = if (safePage == 1) result.items.map(JmRanking::toUiItem).distinctBy(ComicUiItem::key)
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
                    _state.update { state ->
                        if (requestGeneration != searchRequestGeneration.get()) return@update state
                        state.copy(
                            search = state.search.copy(loading = false, loadingMore = false, error = error.readable()),
                            message = error.readable(),
                        )
                    }
                }
        }
    }

    fun loadMoreSearch() {
        val current = _state.value.search
        if (current.loading || current.loadingMore || !current.hasMore ||
            current.query.isBlank() || current.page >= MAX_PAGINATION_PAGE
        ) return
        search(current.query, current.mainTag, current.order, current.page + 1)
    }

    fun clearSearch() {
        searchRequestGeneration.incrementAndGet()
        searchJob?.cancel()
        _state.update { it.copy(search = JmSearchUiState()) }
    }

    fun consumeSearchRedirect() {
        _state.update { state -> state.copy(search = state.search.copy(redirectAid = null)) }
    }

    fun openComic(item: ComicUiItem) = openComic(item.jmId, item)

    fun openComic(id: String) = openComic(id, null)

    private fun openComic(id: String, sourceItem: ComicUiItem?) {
        val requestGeneration = detailRequestGeneration.incrementAndGet()
        detailJob?.cancel()
        commentsJob?.cancel()
        readerWarmupJob?.cancel()
        detailJob = viewModelScope.launch {
            _state.update { state ->
                if (requestGeneration != detailRequestGeneration.get()) return@update state
                state.copy(
                    detail = ComicResolveUiState.Loading(SourceIds.Jm, id),
                    comments = JmCommentsUiState(comicId = id),
                )
            }
            // The album and forum endpoints are independent. Start the forum request
            // immediately so the comments tab is ready by the time the detail card settles.
            loadComments(id)
            runCatching { comicCache[id] ?: gateway.comic(id).also { comicCache[id] = it } }
                .onSuccess { comic ->
                    if (requestGeneration != detailRequestGeneration.get()) return@onSuccess
                    val progress = settingsStore.loadProgress(comic.id)
                    _state.update { state ->
                        if (requestGeneration != detailRequestGeneration.get()) return@update state
                        state.copy(detail = comic.toResolveState(progress))
                    }
                    if (requestGeneration != detailRequestGeneration.get()) return@onSuccess
                    val historyItem = comic.toUiItem().let { actual ->
                        sourceItem?.let { seed ->
                            actual.copy(
                                subtitle = seed.subtitle.takeIf(String::isNotBlank) ?: actual.subtitle,
                                metric = seed.metric.takeIf(String::isNotBlank) ?: actual.metric,
                            )
                        } ?: actual
                    }
                    recordHistorySnapshot(historyItem)
                    warmReaderEntry(comic, progress, requestGeneration)
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        if (requestGeneration != detailRequestGeneration.get()) return@update state
                        state.copy(detail = ComicResolveUiState.Error(SourceIds.Jm, id, error.readable()))
                    }
                }
        }
    }

    private fun cachedCommentPage(comicId: String): JmCommentPage? {
        val cached = commentCache[comicId] ?: return null
        if (System.currentTimeMillis() - cached.cachedAt <= COMMENT_CACHE_TTL_MS) return cached.page
        commentCache.remove(comicId, cached)
        return null
    }

    private fun loadComments(comicId: String, page: Int = 1, force: Boolean = false) {
        if (!comicId.matches(Regex("\\d{1,12}"))) return
        val safePage = page.coerceIn(1, MAX_PAGINATION_PAGE)
        val current = _state.value.comments
        if (
            !force &&
            safePage == 1 &&
            current.comicId == comicId &&
            (current.loading || current.loaded)
        ) return
        val requestGeneration = commentsRequestGeneration.incrementAndGet()
        commentsJob?.cancel()
        _state.update { state ->
            if (requestGeneration != commentsRequestGeneration.get()) return@update state
            val previous = state.comments.takeIf { it.comicId == comicId } ?: JmCommentsUiState(comicId = comicId)
            state.copy(
                comments = previous.copy(
                    items = if (safePage == 1) emptyList() else previous.items,
                    page = if (safePage == 1) 0 else previous.page,
                    total = if (safePage == 1) 0L else previous.total,
                    loading = safePage == 1,
                    loadingMore = safePage > 1,
                    loaded = if (safePage == 1) false else previous.loaded,
                    hasMore = if (safePage == 1) false else previous.hasMore,
                    error = null,
                ),
            )
        }
        commentsJob = viewModelScope.launch {
            runCatching {
                val cached = if (safePage == 1 && !force) cachedCommentPage(comicId) else null
                cached ?: gateway.comments(comicId, safePage).also { result ->
                    if (safePage == 1 && requestGeneration == commentsRequestGeneration.get()) {
                        commentCache[comicId] = CachedCommentPage(result, System.currentTimeMillis())
                    }
                }
            }
                .onSuccess { result ->
                    _state.update { state ->
                        val previous = state.comments
                        if (
                            requestGeneration != commentsRequestGeneration.get() ||
                            previous.comicId != comicId
                        ) return@update state
                        if (!isForwardPageResponse(previous.page, safePage, result.page)) {
                            return@update state.copy(
                                comments = previous.copy(
                                    loading = false,
                                    loadingMore = false,
                                    hasMore = false,
                                    error = INVALID_PAGINATION_MESSAGE,
                                ),
                            )
                        }
                        state.copy(
                            comments = previous.copy(
                                items = if (safePage == 1) {
                                    result.comments.map(JmComment::toUiItem).distinctBy(JmCommentUiItem::id)
                                } else {
                                    mergeCommentPage(previous.items, result.comments.map(JmComment::toUiItem))
                                },
                                page = result.page,
                                total = result.total,
                                loading = false,
                                loadingMore = false,
                                loaded = true,
                                hasMore = result.hasMore,
                                error = null,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        val previous = state.comments
                        if (
                            requestGeneration != commentsRequestGeneration.get() ||
                            previous.comicId != comicId
                        ) return@update state
                        state.copy(
                            comments = previous.copy(
                                loading = false,
                                loadingMore = false,
                                error = error.readable(),
                            ),
                        )
                    }
                }
        }
    }

    fun retryComments() {
        val current = _state.value.comments
        if (current.comicId.isBlank()) return
        val page = if (current.items.isNotEmpty() && current.page > 0) current.page + 1 else 1
        loadComments(current.comicId, page, force = true)
    }

    fun loadMoreComments() {
        val current = _state.value.comments
        if (
            current.comicId.isBlank() ||
            current.loading ||
            current.loadingMore ||
            !current.loaded ||
            !current.hasMore
        ) return
        loadComments(current.comicId, current.page + 1)
    }

    private suspend fun restoreAccount(session: JmSession) {
        _state.update {
            it.copy(
                account = JmAccountUiState(
                    status = JmAccountStatus.Restoring,
                    uid = session.uid,
                    username = session.username,
                ),
            )
        }
        gateway.restoreSession(session)
        try {
            val favorites = gateway.allFavorites()
            publishOfficialAccount(
                JmAccount(
                    uid = session.uid,
                    username = session.username,
                    favoriteCount = favorites.size.toLong(),
                ),
                favorites,
            )
        } catch (error: Throwable) {
            error.rethrowCancellation()
            if (error is JmAuthException) {
                gateway.clearSession()
                sessionStore?.clear()
                _state.update {
                    it.copy(
                        account = JmAccountUiState(status = JmAccountStatus.Error, error = error.readable()),
                        message = "JM 登录已失效，请重新登录",
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        account = JmAccountUiState(
                            status = JmAccountStatus.SignedIn,
                            uid = session.uid,
                            username = session.username,
                            error = error.readable(),
                        ),
                    )
                }
            }
        }
    }

    fun login(username: String, password: String) {
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            _state.update { it.copy(account = JmAccountUiState(status = JmAccountStatus.SigningIn)) }
            try {
                val account = gateway.login(username, password)
                val session = gateway.session() ?: throw JmAuthException("JM 登录未返回有效会话")
                sessionStore?.save(session)
                val syncError = try {
                    val favorites = gateway.allFavorites()
                    publishOfficialAccount(account, favorites)
                    null
                } catch (error: Throwable) {
                    error.rethrowCancellation()
                    if (error is JmAuthException) throw error
                    _state.update { state ->
                        state.copy(
                            account = JmAccountUiState(
                                status = JmAccountStatus.SignedIn,
                                uid = account.uid,
                                username = account.username,
                                favoriteCount = account.favoriteCount,
                                error = "收藏同步失败：${error.readable()}",
                            ),
                        )
                    }
                    error.readable()
                }
                _state.update {
                    it.copy(
                        message = if (syncError == null) {
                            "已登录 JM 官方账号 ${account.username}"
                        } else {
                            "已登录 JM 官方账号，收藏稍后重试"
                        },
                    )
                }
            } catch (error: Throwable) {
                error.rethrowCancellation()
                gateway.clearSession()
                sessionStore?.clear()
                _state.update {
                    it.copy(
                        account = JmAccountUiState(status = JmAccountStatus.Error, error = error.readable()),
                        message = error.readable(),
                    )
                }
            }
        }
    }

    fun logout() {
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            runCatching { gateway.logout() }
                .onFailure { error -> error.rethrowCancellation() }
            sessionStore?.clear()
            val cachedFavorites = withContext(Dispatchers.IO) { libraryStore.loadFavorites() }
            _state.update {
                it.copy(
                    account = JmAccountUiState(),
                    favorites = cachedFavorites,
                    message = "已退出 JM 官方账号",
                )
            }
        }
    }

    fun syncOfficialFavorites() {
        val current = _state.value.account
        if (!current.signedIn || current.syncing) {
            if (!current.signedIn) _state.update { it.copy(message = "请先登录 JM 官方账号") }
            return
        }
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            _state.update { state -> state.copy(account = state.account.copy(syncing = true, error = null)) }
            try {
                val favorites = gateway.allFavorites()
                publishOfficialAccount(
                    JmAccount(uid = current.uid, username = current.username),
                    favorites,
                )
                _state.update { it.copy(message = "JM 官方收藏已同步") }
            } catch (error: Throwable) {
                error.rethrowCancellation()
                if (error is JmAuthException) {
                    gateway.clearSession()
                    sessionStore?.clear()
                    _state.update {
                        it.copy(
                            account = JmAccountUiState(status = JmAccountStatus.Error, error = error.readable()),
                            message = "JM 登录已失效，请重新登录",
                        )
                    }
                } else {
                    _state.update { state ->
                        state.copy(
                            account = state.account.copy(syncing = false, error = "收藏同步失败：${error.readable()}"),
                            message = "JM 收藏同步失败",
                        )
                    }
                }
            }
        }
    }

    private fun publishOfficialAccount(account: JmAccount, favorites: List<JmFavoriteItem>) {
        val items = favorites.map(JmFavoriteItem::toUiItem)
            .distinctBy(ComicUiItem::key)
            .take(MAX_FAVORITE_ENTRIES)
        _state.update {
            it.copy(
                account = JmAccountUiState(
                    status = JmAccountStatus.SignedIn,
                    uid = account.uid,
                    username = account.username,
                    favoriteCount = account.favoriteCount ?: items.size.toLong(),
                    error = null,
                ),
                favorites = items,
            )
        }
        persistFavoritesSnapshot()
    }

    /** Toggle a comic through JM's authenticated /favorite endpoint. */
    fun toggleFavorite(item: ComicUiItem) {
        if (!_state.value.account.signedIn) {
            _state.update { it.copy(message = "请先在设置中登录 JM 官方账号") }
            return
        }
        val id = item.jmId.trim()
        if (!id.matches(Regex("\\d{1,12}")) || !favoriteOperations.add(id)) return
        val wasFavorite = _state.value.favorites.any { it.key == item.key }
        viewModelScope.launch {
            try {
                gateway.toggleFavorite(id)
                val official = try {
                    gateway.allFavorites()
                } catch (error: Throwable) {
                    error.rethrowCancellation()
                    if (error is JmAuthException) throw error
                    null
                }
                _state.update { state ->
                    val next = official?.map(JmFavoriteItem::toUiItem)
                        ?.distinctBy(ComicUiItem::key)
                        ?.take(MAX_FAVORITE_ENTRIES)
                        ?: if (wasFavorite) {
                            state.favorites.filterNot { it.key == item.key }
                        } else {
                            (listOf(item) + state.favorites.filterNot { it.key == item.key })
                                .take(MAX_FAVORITE_ENTRIES)
                        }
                    state.copy(
                        favorites = next,
                        account = state.account.copy(
                            favoriteCount = official?.size?.toLong() ?: next.size.toLong(),
                            error = null,
                        ),
                        message = if (official?.any { it.id == id } ?: !wasFavorite) {
                            "已加入 JM 收藏"
                        } else {
                            "已取消 JM 收藏"
                        },
                    )
                }
                persistFavoritesSnapshot()
            } catch (error: Throwable) {
                error.rethrowCancellation()
                if (error is JmAuthException) {
                    gateway.clearSession()
                    sessionStore?.clear()
                    _state.update {
                        it.copy(
                            account = JmAccountUiState(status = JmAccountStatus.Error, error = error.readable()),
                            message = "JM 登录已失效，请重新登录",
                        )
                    }
                } else {
                    _state.update { it.copy(message = "JM 收藏操作失败：${error.readable()}") }
                }
            } finally {
                favoriteOperations.remove(id)
            }
        }
    }

    fun toggleFavorite(state: ComicResolveUiState.Ready) {
        toggleFavorite(state.toUiItem())
    }

    private fun persistFavoritesSnapshot() {
        val generation = favoritesPersistGeneration.incrementAndGet()
        val snapshot = _state.value.favorites
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                libraryWriteLock.withLock {
                    if (generation != favoritesPersistGeneration.get()) return@withLock
                    libraryStore.replaceFavorites(snapshot)
                }
            }
        }
    }

    fun recordHistory(
        item: ComicUiItem,
        chapterId: String? = null,
        chapterTitle: String? = null,
        pageIndex: Int = 0,
        pageCount: Int = 0,
    ) {
        recordHistorySnapshot(item, chapterId, chapterTitle, pageIndex, pageCount)
    }

    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
        persistHistorySnapshot()
    }

    private fun recordHistorySnapshot(
        item: ComicUiItem,
        chapterId: String? = null,
        chapterTitle: String? = null,
        pageIndex: Int = 0,
        pageCount: Int = 0,
    ) {
        val now = System.currentTimeMillis()
        val safePageCount = pageCount.coerceIn(0, MAX_READER_PAGE_COUNT)
        _state.update { state ->
            val previous = state.history.firstOrNull { it.comic.key == item.key }
            val sameChapter = chapterId != null && chapterId == previous?.chapterId
            val entry = ReadingHistoryItem(
                comic = item,
                chapterId = chapterId ?: previous?.chapterId,
                chapterTitle = chapterTitle ?: previous?.chapterTitle?.takeIf { chapterId == null || sameChapter },
                pageIndex = when {
                    safePageCount > 0 -> pageIndex.coerceIn(0, safePageCount - 1)
                    chapterId != null -> pageIndex.coerceIn(0, MAX_READER_PAGE_COUNT - 1)
                    else -> previous?.pageIndex ?: 0
                },
                pageCount = when {
                    safePageCount > 0 -> safePageCount
                    sameChapter -> previous.pageCount
                    chapterId == null -> previous?.pageCount ?: 0
                    else -> 0
                },
                updatedAt = now,
            )
            state.copy(history = (listOf(entry) + state.history.filterNot { it.comic.key == item.key }).take(MAX_HISTORY_ENTRIES))
        }
        persistHistorySnapshot()
    }

    private fun persistHistorySnapshot() {
        val generation = historyPersistGeneration.incrementAndGet()
        val snapshot = _state.value.history
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                libraryWriteLock.withLock {
                    if (generation != historyPersistGeneration.get()) return@withLock
                    libraryStore.replaceHistory(snapshot)
                }
            }
        }
    }

    fun dismissDetail() {
        detailRequestGeneration.incrementAndGet()
        commentsRequestGeneration.incrementAndGet()
        readerRequestGeneration.incrementAndGet()
        detailJob?.cancel()
        commentsJob?.cancel()
        readerJob?.cancel()
        readerWarmupJob?.cancel()
        _state.update {
            it.copy(
                detail = ComicResolveUiState.Idle,
                comments = JmCommentsUiState(),
                reader = ReaderUiState.Idle,
            )
        }
    }

    private fun warmReaderEntry(comic: JmComic, progress: LocalReadingProgress?, detailGeneration: Long) {
        if (detailGeneration != detailRequestGeneration.get()) return
        readerWarmupJob?.cancel()
        val settings = _state.value.settings
        val resumeProgress = progress.takeIf { settings.autoResumeReading }
        val chapter = comic.chapters.firstOrNull { it.id == resumeProgress?.chapterId }
            ?: comic.chapters.firstOrNull()
            ?: return
        readerWarmupJob = viewModelScope.launch {
            if (detailGeneration != detailRequestGeneration.get()) return@launch
            if (!settings.dataSaver) {
                gateway.warmImageConnections(comic.id, chapter.id)
            }
            runCatching { gateway.chapter(chapter.id) }
                .onSuccess { chapterPages ->
                    if (detailGeneration != detailRequestGeneration.get()) return@onSuccess
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
        val requestGeneration = readerRequestGeneration.incrementAndGet()
        val previousReader = _state.value.reader as? ReaderUiState.Ready
        val historyItem = comicCache[comicId]?.toUiItem()
            ?: (_state.value.detail as? ComicResolveUiState.Ready)?.takeIf { it.jmId == comicId }?.toUiItem()
            ?: ComicUiItem(
                jmId = comicId,
                title = comicTitle,
                subtitle = "JM 官方源",
                metric = "",
                accentIndex = comicId.takeLast(4).toIntOrNull() ?: comicTitle.hashCode(),
            )
        recordHistorySnapshot(
            item = historyItem,
            chapterId = chapter.sourceChapterId,
            chapterTitle = chapter.title,
            pageIndex = initialPageIndex,
        )
        if (!_state.value.settings.dataSaver) {
            gateway.warmImageConnections(comicId, chapter.sourceChapterId)
        }
        readerJob?.cancel()
        readerJob = viewModelScope.launch {
            _state.update { state ->
                if (requestGeneration != readerRequestGeneration.get()) return@update state
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
                    _state.update { state ->
                        if (requestGeneration != readerRequestGeneration.get()) return@update state
                        state.copy(
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
                        if (requestGeneration != readerRequestGeneration.get()) return@update state
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
        readerRequestGeneration.incrementAndGet()
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
        if (!comicId.matches(SAFE_JM_ID) || !chapterId.matches(SAFE_JM_ID) ||
            pageCount !in 1..MAX_READER_PAGE_COUNT
        ) return
        val safePageIndex = pageIndex.coerceIn(0, pageCount - 1)
        persistReaderProgress(comicId, chapterId, safePageIndex, pageCount)
        _state.update { state ->
            val detail = state.detail as? ComicResolveUiState.Ready ?: return@update state
            if (detail.jmId != comicId) return@update state
            state.copy(detail = detail.copy(resumeChapterId = chapterId, resumePageIndex = safePageIndex))
        }
        val current = _state.value
        val reader = current.reader as? ReaderUiState.Ready
        val detail = current.detail as? ComicResolveUiState.Ready
        val historyItem = detail?.takeIf { it.jmId == comicId }?.toUiItem()
            ?: comicCache[comicId]?.toUiItem()
            ?: ComicUiItem(
                jmId = comicId,
                title = reader?.takeIf { it.sourceId == comicId }?.title ?: "JM$comicId",
                subtitle = "JM 官方源",
                metric = "",
                accentIndex = comicId.takeLast(4).toIntOrNull() ?: 0,
            )
        recordHistorySnapshot(
            item = historyItem,
            chapterId = chapterId,
            chapterTitle = reader?.takeIf { it.chapterId == chapterId }?.chapterTitle,
            pageIndex = safePageIndex,
            pageCount = pageCount,
        )
    }

    private fun persistReaderProgress(comicId: String, chapterId: String, pageIndex: Int, pageCount: Int) {
        val safePageIndex = pageIndex.coerceIn(0, pageCount - 1)
        val signature = "$comicId|$chapterId|$safePageIndex|$pageCount"
        if (signature == lastProgressSignature) return
        lastProgressSignature = signature
        val generation = progressPersistGeneration.incrementAndGet()
        val snapshot = ReaderProgressSnapshot(comicId, chapterId, safePageIndex, pageCount)
        latestProgressSnapshot = snapshot
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                progressWriteLock.withLock {
                    if (generation != progressPersistGeneration.get()) return@withLock
                    settingsStore.saveProgress(
                        comicId = snapshot.comicId,
                        chapterId = snapshot.chapterId,
                        pageIndex = snapshot.pageIndex,
                        pageCount = snapshot.pageCount,
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

    fun updateChapterSort(descending: Boolean) {
        val current = _state.value.settings
        if (current.chapterDescending == descending) return
        updateSettings(current.copy(chapterDescending = descending))
    }

    fun refreshSources(force: Boolean = true, updateOfficialList: Boolean = true) {
        val requestGeneration = sourceRefreshRequestGeneration.incrementAndGet()
        sourceRefreshJob?.cancel()
        sourceRefreshJob = viewModelScope.launch {
            val current = _state.value.sourceStatus
            _state.update { state ->
                if (requestGeneration != sourceRefreshRequestGeneration.get()) return@update state
                state.copy(sourceStatus = current.copy(checking = true, error = null))
            }
            runCatching { gateway.refreshSourceList(force, updateOfficialList) }
                .onSuccess { snapshot ->
                    _state.update { state ->
                        if (requestGeneration != sourceRefreshRequestGeneration.get()) return@update state
                        state.copy(sourceStatus = snapshot.toUiState())
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        if (requestGeneration != sourceRefreshRequestGeneration.get()) return@update state
                        state.copy(sourceStatus = state.sourceStatus.copy(checking = false, error = error.readable()))
                    }
                }
        }
    }

    fun checkForUpdates(force: Boolean = false) {
        val current = _state.value.appUpdate
        if (current.checking || !force && current.checked) return
        val requestGeneration = updateCheckRequestGeneration.incrementAndGet()
        updateCheckJob?.cancel()
        updateCheckJob = viewModelScope.launch {
            _state.update { state ->
                if (requestGeneration != updateCheckRequestGeneration.get()) return@update state
                state.copy(appUpdate = state.appUpdate.copy(checking = true, error = null))
            }
            runCatching { releaseClient.latest() }
                .onSuccess { release ->
                    _state.update { state ->
                        if (requestGeneration != updateCheckRequestGeneration.get()) return@update state
                        state.copy(
                            appUpdate = state.appUpdate.copy(
                                checking = false,
                                checked = true,
                                latestVersion = release.version,
                                releaseName = release.name,
                                notes = release.notes,
                                publishedAt = release.publishedAt,
                                releaseUrl = release.releaseUrl,
                                downloadUrl = release.downloadUrl,
                                assetSize = release.assetSize,
                                updateAvailable = isRemoteVersionNewer(state.appUpdate.currentVersion, release.version),
                                error = null,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    _state.update { state ->
                        if (requestGeneration != updateCheckRequestGeneration.get()) return@update state
                        state.copy(
                            appUpdate = state.appUpdate.copy(
                                checking = false,
                                checked = true,
                                error = error.message?.take(160).orEmpty().ifBlank { "无法连接 GitHub" },
                            ),
                        )
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
        val requestGeneration = readerRequestGeneration.incrementAndGet()
        readerJob?.cancel()
        readerJob = viewModelScope.launch {
            val files = downloadStore.localPages(item)
            if (requestGeneration != readerRequestGeneration.get()) return@launch
            if (!item.complete || files.size != item.pageCount || files.isEmpty()) {
                _state.update { state ->
                    if (requestGeneration != readerRequestGeneration.get()) return@update state
                    state.copy(message = "本地章节文件不完整，请重新下载")
                }
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
            _state.update { state ->
                if (requestGeneration != readerRequestGeneration.get()) return@update state
                state.copy(
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
            if (requestGeneration != readerRequestGeneration.get()) return@launch
            recordHistorySnapshot(
                item = ComicUiItem(
                    jmId = item.comicId,
                    title = item.comicTitle,
                    subtitle = "本地下载",
                    metric = "",
                    accentIndex = item.comicId.takeLast(4).toIntOrNull() ?: item.comicTitle.hashCode(),
                ),
                chapterId = item.chapterId,
                chapterTitle = item.chapterTitle,
                pageIndex = resumePageIndex,
                pageCount = item.pageCount,
            )
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
        homeRequestGeneration.incrementAndGet()
        categoryCatalogRequestGeneration.incrementAndGet()
        categoryRequestGeneration.incrementAndGet()
        rankingRequestGeneration.incrementAndGet()
        searchRequestGeneration.incrementAndGet()
        detailRequestGeneration.incrementAndGet()
        commentsRequestGeneration.incrementAndGet()
        readerRequestGeneration.incrementAndGet()
        weeklyCatalogRequestGeneration.incrementAndGet()
        weeklyRequestGeneration.incrementAndGet()
        typeRankingRequestGeneration.incrementAndGet()
        sourceRefreshRequestGeneration.incrementAndGet()
        updateCheckRequestGeneration.incrementAndGet()
        homeJob?.cancel()
        categoryCatalogJob?.cancel()
        categoryJob?.cancel()
        rankingJob?.cancel()
        searchJob?.cancel()
        detailJob?.cancel()
        commentsJob?.cancel()
        accountJob?.cancel()
        readerJob?.cancel()
        readerWarmupJob?.cancel()
        settingsSaveJob?.cancel()
        weeklyCatalogJob?.cancel()
        weeklyJob?.cancel()
        typeRankingJob?.cancel()
        sourceRefreshJob?.cancel()
        sourceScheduleJob?.cancel()
        updateCheckJob?.cancel()
        // ViewModel scope jobs may be cancelled as part of clear(). Persist the
        // final in-memory snapshots synchronously so a last tap made just before
        // the activity is destroyed cannot disappear with the cancelled job.
        favoritesPersistGeneration.incrementAndGet()
        historyPersistGeneration.incrementAndGet()
        progressPersistGeneration.incrementAndGet()
        libraryWriteLock.withLock {
            libraryStore.replaceFavorites(_state.value.favorites)
            libraryStore.replaceHistory(_state.value.history)
        }
        latestProgressSnapshot?.let { snapshot ->
            progressWriteLock.withLock {
                settingsStore.saveProgress(
                    comicId = snapshot.comicId,
                    chapterId = snapshot.chapterId,
                    pageIndex = snapshot.pageIndex,
                    pageCount = snapshot.pageCount,
                )
            }
        }
        settingsStore.save(_state.value.settings)
        releaseClient.close()
        gateway.close()
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PureViewModel(
            gateway = JmGateway(context),
            downloadStore = DownloadStore(context),
            settingsStore = LocalSettingsStore(context),
            releaseClient = GitHubReleaseClient(),
            currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
            libraryStore = LibraryStore(context),
            sessionStore = JmSessionStore(context),
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

private fun JmFavoriteItem.toUiItem() = ComicUiItem(
    jmId = id,
    title = title,
    subtitle = authors.take(2).joinToString(" · ").ifBlank { "JM 官方收藏" },
    metric = "JM 官方收藏",
    accentIndex = id.takeLast(4).toIntOrNull() ?: title.hashCode(),
    coverUrl = coverUrl,
)

private fun JmComic.toUiItem() = ComicUiItem(
    jmId = id,
    title = title,
    subtitle = authors.take(2).joinToString(" · ").ifBlank { "JM 官方源" },
    metric = likes?.let { "JM 收藏 ${it.compact()}" }
        ?: views?.let { "JM 浏览 ${it.compact()}" }
        ?: "",
    accentIndex = id.takeLast(4).toIntOrNull() ?: title.hashCode(),
    coverUrl = coverUrl,
)

private fun ComicResolveUiState.Ready.toUiItem() = ComicUiItem(
    jmId = jmId,
    title = title,
    subtitle = "JM 官方源",
    metric = "",
    accentIndex = jmId.takeLast(4).toIntOrNull() ?: title.hashCode(),
    coverUrl = coverUrl,
    source = source,
)

private fun JmComment.toUiItem(): JmCommentUiItem {
    val normalizedUsername = username.trim()
    val normalizedNickname = nickname.trim()
    return JmCommentUiItem(
        id = id,
        userId = userId,
        displayName = normalizedNickname
            .ifBlank { normalizedUsername }
            .ifBlank { userId?.let { "JM$it" }.orEmpty() }
            .ifBlank { "JM 用户" },
        username = normalizedUsername,
        content = content.trim(),
        avatarUrl = avatarUrl?.trim()?.takeIf { it.startsWith("https://") },
        createdAt = createdAt.trim(),
        likes = likes.coerceAtLeast(0L),
        spoiler = spoiler,
        replies = replies.map(JmComment::toUiItem),
    )
}

internal fun mergeComicPage(
    existing: List<ComicUiItem>,
    incoming: List<ComicUiItem>,
): List<ComicUiItem> = buildList(existing.size + incoming.size) {
    val seen = HashSet<String>(existing.size + incoming.size)
    (existing + incoming).forEach { item ->
        if (seen.add(item.key)) add(item)
    }
}

internal fun mergeCommentPage(
    existing: List<JmCommentUiItem>,
    incoming: List<JmCommentUiItem>,
): List<JmCommentUiItem> = buildList(existing.size + incoming.size) {
    val seen = HashSet<String>(existing.size + incoming.size)
    (existing + incoming).forEach { item ->
        if (seen.add(item.id)) add(item)
    }
}

internal fun shouldPublishProgress(previous: Float, current: Float, elapsedNanos: Long, completed: Boolean): Boolean =
    completed || current - previous >= 0.01f || elapsedNanos >= 150_000_000L

internal fun isForwardPageResponse(previousPage: Int, requestedPage: Int, responsePage: Int): Boolean =
    requestedPage > previousPage && responsePage == requestedPage

private fun JmCategory.toDirectCategory() = DirectJmCategory(id, name, slug, type, totalAlbums)

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
    private const val COMMENT_CACHE_LIMIT = 24
    private const val COMMENT_CACHE_TTL_MS = 5L * 60L * 1_000L
    private const val MAX_FAVORITE_ENTRIES = 200
private const val MAX_HISTORY_ENTRIES = 100
private const val MAX_PAGINATION_PAGE = 200
private const val MAX_SEARCH_QUERY_LENGTH = 160
private const val MAX_BROWSE_OPTION_LENGTH = 128
private const val MAX_READER_PAGE_COUNT = 20_000
private const val INVALID_PAGINATION_MESSAGE = "上游分页响应异常，请稍后重试"
private val SAFE_JM_ID = Regex("^\\d{1,12}$")
private const val SOURCE_REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1_000L
