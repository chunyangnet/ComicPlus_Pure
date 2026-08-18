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
import com.comicplus.app.ui.JmCommentsUiState
import com.comicplus.app.ui.JmAccountStatus
import com.comicplus.app.ui.JmAccountUiState
import com.comicplus.app.ui.JmFavoriteFolderUiItem
import com.comicplus.app.ui.JmFavoriteFoldersUiState
import com.comicplus.app.ui.JmTagGroupUi
import com.comicplus.app.ui.JmSourceUiState
import com.comicplus.app.ui.PureUiState
import com.comicplus.app.ui.RankingsUiState
import com.comicplus.app.ui.ReaderUiState
import com.comicplus.app.ui.ReaderChapterSegment
import com.comicplus.app.ui.ReaderPrefetchMode
import com.comicplus.app.ui.ReadingHistoryItem
import com.comicplus.app.ui.key
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

class PureViewModel(
    private val gateway: JmGateway,
    private val downloadStore: DownloadStore,
    private val settingsStore: LocalSettingsStore,
    private val releaseClient: GitHubReleaseClient,
    currentVersion: String,
    private val libraryStore: LibraryStore = LibraryStore(),
    private val sessionStore: JmSessionStore? = null,
) : ViewModel() {
    private data class ReaderProgressSnapshot(
        val comicId: String,
        val chapterId: String,
        val pageIndex: Int,
        val pageCount: Int,
    )

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
            appUpdate = AppUpdateUiState(currentVersion = currentVersion),
        ),
    )
    val state: StateFlow<PureUiState> = _state.asStateFlow()
    private val chapterPreloader = JmChapterPreloader(viewModelScope, gateway) { _state.value.settings }

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
    private var favoriteFolderJob: Job? = null
    private var readerJob: Job? = null
    private var readerWarmupJob: Job? = null
    private var readerBufferRefillJob: Job? = null
    private var readerWarmupChapterId: String? = null
    private var lastProgressSignature: String? = null
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val localLibraryLoaded = CompletableDeferred<Unit>()
    private val progressPersistGeneration = AtomicLong()
    @Volatile private var latestProgressSnapshot: ReaderProgressSnapshot? = null
    @Volatile private var persistedProgressGeneration = 0L
    private val favoritesPersistGeneration = AtomicLong()
    private val historyPersistGeneration = AtomicLong()
    private val settingsPersistGeneration = AtomicLong()
    @Volatile private var persistedFavoritesGeneration = 0L
    @Volatile private var persistedHistoryGeneration = 0L
    @Volatile private var persistedSettingsGeneration = 0L
    private var settingsSaveJob: Job? = null
    private var readerStateSaveJob: Job? = null
    private var sourceRefreshJob: Job? = null
    private var sourceScheduleJob: Job? = null
    private var updateCheckJob: Job? = null
    private val searchRequestGeneration = AtomicLong()
    private val detailRequestGeneration = AtomicLong()
    private val commentsRequestGeneration = AtomicLong()
    private val favoriteFolderRequestGeneration = AtomicLong()
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
    private val appInForeground = AtomicBoolean(true)
    private val comicCache = SynchronizedLruCache<String, JmComic>(maxEntries = COMIC_CACHE_LIMIT)
    private val commentCache = CommentPageCache()
    private val downloadLimiter = Semaphore(permits = 2)
    // Official favorite writes are stateful and must stay ordered. Reads remain concurrent and
    // use favoriteMutationRevision to reject snapshots captured across a write.
    private val favoriteOperationLimiter = Semaphore(permits = 1)
    private val favoriteOperations = ConcurrentHashMap.newKeySet<String>()
    private val favoriteOperationGeneration = AtomicLong()
    private val favoriteMutationRevision = AtomicLong()

    init {
        viewModelScope.launch {
            val favoritesGeneration = favoritesPersistGeneration.get()
            val historyGeneration = historyPersistGeneration.get()
            try {
                val (favorites, history) = withContext(Dispatchers.IO) {
                    libraryStore.loadFavorites() to libraryStore.loadHistory()
                }
                _state.update { state ->
                    val loadedFavorites = if (favoritesGeneration == favoritesPersistGeneration.get()) {
                        favorites
                    } else {
                        (state.favorites + favorites)
                            .distinctBy(ComicUiItem::key)
                            .take(MAX_FAVORITE_ENTRIES)
                    }
                    val loadedHistory = if (historyGeneration == historyPersistGeneration.get()) {
                        history
                    } else {
                        (state.history + history)
                            .distinctBy { it.comic.key }
                            .sortedByDescending(ReadingHistoryItem::updatedAt)
                            .take(MAX_HISTORY_ENTRIES)
                    }
                    state.copy(
                        favorites = loadedFavorites,
                        history = loadedHistory,
                        favoriteFolders = if (!state.account.signedIn) {
                            state.favoriteFolders.copy(
                                items = loadedFavorites,
                                total = loadedFavorites.size.toLong(),
                            )
                        } else {
                            state.favoriteFolders
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the in-memory defaults when a corrupt or unavailable local snapshot fails.
            } finally {
                localLibraryLoaded.complete(Unit)
            }
        }
        viewModelScope.launch {
            downloadStore.items.collect { downloads -> _state.update { it.copy(downloads = downloads) } }
        }
        sessionStore?.let { store ->
            accountJob = viewModelScope.launch {
                localLibraryLoaded.await()
                withContext(Dispatchers.IO) { store.load() }?.let { session -> restoreAccount(session) }
            }
        }
        refreshHome()
        viewModelScope.launch {
            awaitInitialHomePriorityWindow()
            try {
                downloadStore.refresh()
            } catch (error: Throwable) {
                error.rethrowCancellation()
                // Downloads can be refreshed again from the library screen.
            }
        }
        viewModelScope.launch {
            awaitInitialHomePriorityWindow()
            loadCategories()
        }
        sourceScheduleJob = viewModelScope.launch {
            awaitInitialHomePriorityWindow()
            // Source probing opens several connections per host. Give the
            // first screen and an immediately opened reader a quiet window,
            // then refresh only when the persisted measurements are stale.
            delay(SOURCE_REFRESH_START_DELAY_MS)
            while (isActive) {
                val currentSettings = _state.value.settings
                val currentState = _state.value
                val appIsIdle = currentState.detail is ComicResolveUiState.Idle &&
                    currentState.reader is ReaderUiState.Idle &&
                    !currentState.account.syncing &&
                    currentState.downloadProgress.isEmpty() &&
                    !currentState.favoriteFolders.loading &&
                    !currentState.favoriteFolders.creating &&
                    currentState.favoriteFolders.movingKey == null &&
                    currentState.account.status != JmAccountStatus.Restoring &&
                    currentState.account.status != JmAccountStatus.SigningIn
                if (
                    appInForeground.get() &&
                    appIsIdle &&
                    !currentState.sourceStatus.checking &&
                    (currentSettings.autoSelectSource || currentSettings.autoUpdateSourceList) &&
                    sourceSnapshotNeedsRefresh(
                        gateway.cachedSourceSnapshot(),
                        System.currentTimeMillis(),
                        SOURCE_REFRESH_INTERVAL_MS,
                    )
                ) {
                    refreshSources(
                        force = currentSettings.autoUpdateSourceList,
                        updateOfficialList = currentSettings.autoUpdateSourceList,
                    )
                }
                delay(SOURCE_REFRESH_CHECK_INTERVAL_MS)
            }
        }
    }

    fun refreshHome() {
        cancelSourceRefreshForForeground()
        val requestGeneration = homeRequestGeneration.incrementAndGet()
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _state.update { state ->
                if (requestGeneration != homeRequestGeneration.get()) return@update state
                state.copy(loading = true, message = null)
            }
            runCatching {
                gateway.home { partial ->
                    if (requestGeneration != homeRequestGeneration.get()) return@home
                    val uiItems = mapRankingItems(partial)
                    if (requestGeneration != homeRequestGeneration.get()) return@home
                    _state.update { state ->
                        if (requestGeneration != homeRequestGeneration.get()) return@update state
                        state.copy(
                            home = uiItems,
                            discoveryItems = uiItems.drop(8),
                            loading = false,
                            discoveryExhausted = false,
                        )
                    }
                }
            }
                .onSuccess { items ->
                    if (requestGeneration != homeRequestGeneration.get()) return@onSuccess
                    val uiItems = mapRankingItems(items)
                    if (requestGeneration != homeRequestGeneration.get()) return@onSuccess
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
        cancelSourceRefreshForForeground()
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
                    val uiItems = mapRankingItems(items)
                    _state.update { state ->
                        if (requestGeneration != rankingRequestGeneration.get() || state.rankings.jmOrder != order) return@update state
                        state.copy(
                            rankings = state.rankings.copy(
                                jmOrder = order,
                                jmItems = uiItems,
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
        cancelSourceRefreshForForeground()
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
                    val (categoryOptions, typeOptions) = withContext(Dispatchers.Default) {
                        catalog.categories
                            .map { JmBrowseOptionUi(it.id, it.title) }
                            .distinctBy(JmBrowseOptionUi::id) to catalog.types
                            .map { JmBrowseOptionUi(it.id, it.title) }
                            .distinctBy(JmBrowseOptionUi::id)
                    }
                    if (requestGeneration != weeklyCatalogRequestGeneration.get()) return@onSuccess
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
        cancelSourceRefreshForForeground()
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
                    val uiItems = mapRankingItems(page.items)
                    _state.update { state ->
                        if (requestGeneration != weeklyRequestGeneration.get()) return@update state
                        val weekly = state.officialBrowse.weekly
                        if (weekly.selectedCategoryId != categoryId || weekly.selectedTypeId != typeId) return@update state
                        state.copy(
                            officialBrowse = state.officialBrowse.copy(
                                weekly = weekly.copy(
                                    items = uiItems,
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
        cancelSourceRefreshForForeground()
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
                    val uiItems = mapRankingItems(page.items)
                    _state.update { state ->
                        if (requestGeneration != typeRankingRequestGeneration.get()) return@update state
                        val ranking = state.officialBrowse.typeRanking
                        if (ranking.selectedSlug != safeSlug || ranking.order != order) return@update state
                        state.copy(
                            officialBrowse = state.officialBrowse.copy(
                                typeRanking = ranking.copy(
                                    items = uiItems,
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
        cancelSourceRefreshForForeground()
        val requestGeneration = categoryCatalogRequestGeneration.incrementAndGet()
        categoryCatalogJob = viewModelScope.launch {
            _state.update {
                if (requestGeneration != categoryCatalogRequestGeneration.get()) return@update it
                it.copy(officialBrowse = it.officialBrowse.copy(catalogLoading = true))
            }
            runCatching { gateway.categoryCatalog() }
                .onSuccess { catalog ->
                    val (categories, tagGroups) = withContext(Dispatchers.Default) {
                        catalog.categories.map(JmCategory::toDirectCategory) to
                            catalog.tagGroups.map { group -> JmTagGroupUi(group.title, group.tags) }
                    }
                    _state.update {
                        if (requestGeneration != categoryCatalogRequestGeneration.get()) return@update it
                        it.copy(
                            categories = categories,
                            officialBrowse = it.officialBrowse.copy(
                                tagGroups = tagGroups,
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
        cancelSourceRefreshForForeground()
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
                    val mapped = mapRankingItems(result.items, distinct = true)
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
        cancelSourceRefreshForForeground()
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
                    val incoming = mapRankingItems(result.items)
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
                        val merged = mergeComicPage(category.items, incoming)
                        state.copy(
                            category = category.copy(
                                items = merged,
                                page = result.page,
                                loadingMore = false,
                                // `merged` was already materialised above. Reuse its
                                // size instead of allocating a second merged list.
                                hasMore = result.hasMore && incoming.isNotEmpty() &&
                                    merged.size > category.items.size,
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
        cancelSourceRefreshForForeground()
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
                    val incoming = mapRankingItems(result.items, distinct = safePage == 1)
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
                        val merged = if (safePage == 1) {
                            incoming
                        } else {
                            mergeComicPage(previous.items, incoming)
                        }
                        state.copy(
                            search = previous.copy(
                                query = result.query,
                                mainTag = safeMainTag,
                                order = order,
                                items = merged,
                                page = result.page,
                                total = result.total,
                                redirectAid = result.redirectAid,
                                hasMore = if (safePage == 1) {
                                    result.hasMore && merged.isNotEmpty()
                                } else {
                                    result.hasMore && incoming.isNotEmpty() &&
                                        merged.size > previous.items.size
                                },
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
        cancelSourceRefreshForForeground()
        val requestGeneration = detailRequestGeneration.incrementAndGet()
        detailJob?.cancel()
        commentsJob?.cancel()
        readerWarmupJob?.cancel()
        readerWarmupChapterId = null
        detailJob = viewModelScope.launch {
            _state.update { state ->
                if (requestGeneration != detailRequestGeneration.get()) return@update state
                state.copy(
                    detail = ComicResolveUiState.Loading(SourceIds.Jm, id),
                    comments = JmCommentsUiState(comicId = id),
                )
            }
            runCatching { comicCache[id] ?: gateway.comic(id).also { comicCache[id] = it } }
                .onSuccess { comic ->
                    if (requestGeneration != detailRequestGeneration.get()) return@onSuccess
                    val progress = withContext(Dispatchers.IO) {
                        settingsStore.loadProgress(comic.id)
                    }
                    val resolvedDetail = withContext(Dispatchers.Default) {
                        comic.toResolveState(progress)
                    }
                    _state.update { state ->
                        if (requestGeneration != detailRequestGeneration.get()) return@update state
                        state.copy(detail = resolvedDetail)
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

    private fun loadComments(
        comicId: String,
        chapterId: String,
        page: Int = 1,
        force: Boolean = false,
    ) {
        if (!comicId.matches(SAFE_JM_ID) || !chapterId.matches(SAFE_JM_ID)) return
        val safePage = page.coerceIn(1, MAX_PAGINATION_PAGE)
        val current = _state.value.comments
        if (
            !force &&
            safePage == 1 &&
            current.comicId == comicId &&
            current.chapterId == chapterId &&
            (current.loading || current.loaded)
        ) return
        val requestGeneration = commentsRequestGeneration.incrementAndGet()
        commentsJob?.cancel()
        val cached = if (safePage == 1 && !force) commentCache.get(comicId, chapterId) else null
        if (cached != null) {
            _state.update { state ->
                if (requestGeneration != commentsRequestGeneration.get()) return@update state
                state.copy(
                    comments = JmCommentsUiState(
                        comicId = comicId,
                        chapterId = chapterId,
                        items = cached.items,
                        page = cached.page,
                        total = cached.total,
                        loaded = true,
                        hasMore = cached.hasMore,
                    ),
                )
            }
            return
        }
        _state.update { state ->
            if (requestGeneration != commentsRequestGeneration.get()) return@update state
            val previous = state.comments.takeIf {
                it.comicId == comicId && it.chapterId == chapterId
            } ?: JmCommentsUiState(comicId = comicId, chapterId = chapterId)
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
                val result = gateway.comments(chapterId, safePage)
                withContext(Dispatchers.Default) { result.toUiSnapshot() }
            }
                .onSuccess { result ->
                    _state.update { state ->
                        val previous = state.comments
                        if (
                            requestGeneration != commentsRequestGeneration.get() ||
                            previous.comicId != comicId ||
                            previous.chapterId != chapterId
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
                        val mergedItems = if (safePage == 1) {
                            result.items
                        } else {
                            mergeCommentPage(previous.items, result.items)
                        }
                        state.copy(
                            comments = previous.copy(
                                items = mergedItems,
                                page = result.page,
                                total = result.total,
                                loading = false,
                                loadingMore = false,
                                loaded = true,
                                hasMore = if (safePage == 1) {
                                    result.hasMore && result.items.isNotEmpty()
                                } else {
                                    result.hasMore && result.items.isNotEmpty() &&
                                        mergedItems.size > previous.items.size
                                },
                                error = null,
                            ),
                        )
                    }
                    val published = _state.value.comments
                    if (
                        requestGeneration == commentsRequestGeneration.get() &&
                        published.comicId == comicId &&
                        published.chapterId == chapterId &&
                        published.loaded &&
                        published.page == result.page
                    ) {
                        commentCache.put(
                            comicId = comicId,
                            chapterId = chapterId,
                            page = CommentPageSnapshot(
                                page = published.page,
                                total = published.total,
                                items = published.items,
                                hasMore = published.hasMore,
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
                            previous.comicId != comicId ||
                            previous.chapterId != chapterId
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

    /** Load comments for an explicitly selected chapter from the reader. */
    fun openComments(comicId: String, chapterId: String) {
        loadComments(comicId.trim(), chapterId.trim())
    }

    /** Load the official comic-level forum used by the detail page. */
    fun openComicComments(comicId: String) {
        val normalizedId = comicId.trim()
        loadComments(normalizedId, normalizedId)
    }

    fun retryComments() {
        val current = _state.value.comments
        if (current.comicId.isBlank() || current.chapterId.isBlank()) return
        val page = if (current.items.isNotEmpty() && current.page > 0) current.page + 1 else 1
        loadComments(current.comicId, current.chapterId, page, force = true)
    }

    fun loadMoreComments() {
        val current = _state.value.comments
        if (
            current.comicId.isBlank() ||
            current.chapterId.isBlank() ||
            current.loading ||
            current.loadingMore ||
            !current.loaded ||
            !current.hasMore
        ) return
        loadComments(current.comicId, current.chapterId, current.page + 1)
    }

    private suspend fun mapRankingItems(
        items: List<JmRanking>,
        distinct: Boolean = false,
    ): List<ComicUiItem> = withContext(Dispatchers.Default) {
        items.map(JmRanking::toUiItem).let { mapped ->
            if (distinct) mapped.distinctBy(ComicUiItem::key) else mapped
        }
    }

    private suspend fun awaitInitialHomePriorityWindow() {
        val initialHomeJob = homeJob ?: return
        withTimeoutOrNull(INITIAL_HOME_PRIORITY_TIMEOUT_MS) { initialHomeJob.join() }
    }

    private suspend fun mapFavoriteItems(items: List<JmFavoriteItem>): List<ComicUiItem> =
        withContext(Dispatchers.Default) {
            items.map(JmFavoriteItem::toUiItem)
                .distinctBy(ComicUiItem::key)
                .take(MAX_FAVORITE_ENTRIES)
        }

    private fun List<JmFavoriteFolder>.toFavoriteFolderUiItems(): List<JmFavoriteFolderUiItem> =
        (listOf(JmFavoriteFolder(id = "0", name = "全部")) + this)
            .map { folder ->
                JmFavoriteFolderUiItem(
                    id = folder.id,
                    name = folder.name.trim().ifBlank { if (folder.id == "0") "全部" else "收藏夹 ${folder.id}" },
                )
            }
            .distinctBy(JmFavoriteFolderUiItem::id)

    private suspend fun saveStoredSession(session: JmSession): Boolean {
        val store = sessionStore ?: return true
        return withContext(Dispatchers.IO) { store.save(session) }
    }

    private suspend fun saveLatestGatewaySession(fallback: JmSession? = null): Boolean {
        val session = gateway.session() ?: fallback ?: return false
        return saveStoredSession(session)
    }

    private suspend fun clearStoredSession() {
        val store = sessionStore ?: return
        withContext(Dispatchers.IO) { store.clear() }
    }

    private suspend fun restoreAccount(session: JmSession) {
        _state.update {
            it.copy(
                account = JmAccountUiState(
                    status = JmAccountStatus.Restoring,
                    uid = session.uid,
                    username = session.username,
                ),
                favoriteFolders = JmFavoriteFoldersUiState(
                    items = it.favorites,
                    total = it.favorites.size.toLong(),
                ),
            )
        }
        gateway.restoreSession(session)
        // The encrypted session is already trusted enough to expose the local
        // cache immediately. The network reconciliation runs in the background
        // so a cold start never blocks the library behind a full-page sync.
        _state.update { state ->
            state.copy(
                account = JmAccountUiState(
                    status = JmAccountStatus.SignedIn,
                    uid = session.uid,
                    username = session.username,
                    syncing = true,
                ),
                favoriteFolders = state.favoriteFolders.copy(
                    items = state.favorites,
                    total = state.favorites.size.toLong(),
                ),
            )
        }
        awaitInitialHomePriorityWindow()
        delay(ACCOUNT_SYNC_START_DELAY_MS)
        val mutationRevision = favoriteMutationRevision.get()
        try {
            val account = JmAccount(uid = session.uid, username = session.username)
            val favorites = gateway.favoriteCollection { partial ->
                publishOfficialAccount(
                    account = account.copy(favoriteCount = partial.total),
                    favorites = partial,
                    expectedMutationRevision = mutationRevision,
                    mergeWithCached = true,
                    syncing = true,
                )
            }
            val sessionPersisted = saveLatestGatewaySession(session)
            val published = publishOfficialAccount(
                account = account.copy(favoriteCount = favorites.total),
                favorites = favorites,
                expectedMutationRevision = mutationRevision,
            )
            if (!published || !sessionPersisted) {
                _state.update { state ->
                    state.copy(
                        account = state.account.copy(
                            syncing = false,
                            error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                        ),
                    )
                }
            }
        } catch (error: Throwable) {
            error.rethrowCancellation()
            if (error is JmAuthException) {
                invalidateFavoriteFolderSession(error)
            } else {
                val sessionPersisted = saveLatestGatewaySession(session)
                _state.update { state ->
                    if (mutationRevision != favoriteMutationRevision.get()) {
                        return@update state.copy(
                            account = state.account.copy(
                                syncing = false,
                                error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                            ),
                        )
                    }
                    state.copy(
                        account = JmAccountUiState(
                            status = JmAccountStatus.SignedIn,
                            uid = session.uid,
                            username = session.username,
                            syncing = false,
                            error = listOfNotNull(
                                error.readable(),
                                SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                            ).joinToString("；"),
                        ),
                    )
                }
            }
        }
    }

    fun login(username: String, password: String) {
        cancelSourceRefreshForForeground()
        favoriteFolderRequestGeneration.incrementAndGet()
        favoriteFolderJob?.cancel()
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    account = JmAccountUiState(status = JmAccountStatus.SigningIn),
                    favoriteFolders = JmFavoriteFoldersUiState(
                        items = it.favorites,
                        total = it.favorites.size.toLong(),
                    ),
                )
            }
            try {
                val account = gateway.login(username, password)
                val session = gateway.session() ?: throw JmAuthException("JM 登录未返回有效会话")
                saveStoredSession(session)
                val mutationRevision = favoriteMutationRevision.get()
                val syncError = try {
                    val favorites = gateway.favoriteCollection { partial ->
                        publishOfficialAccount(
                            account = account,
                            favorites = partial,
                            expectedMutationRevision = mutationRevision,
                            mergeWithCached = true,
                        )
                    }
                    publishOfficialAccount(
                        account = account,
                        favorites = favorites,
                        expectedMutationRevision = mutationRevision,
                    )
                    null
                } catch (error: Throwable) {
                    error.rethrowCancellation()
                    if (error is JmAuthException) throw error
                    if (mutationRevision == favoriteMutationRevision.get()) {
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
                    }
                    error.readable()
                }
                val sessionPersisted = saveLatestGatewaySession(session)
                if (!sessionPersisted) {
                    _state.update { state ->
                        state.copy(
                            account = state.account.copy(
                                error = listOfNotNull(
                                    state.account.error,
                                    SESSION_PERSISTENCE_ERROR,
                                ).distinct().joinToString("；"),
                            ),
                        )
                    }
                }
                _state.update { state ->
                    state.copy(
                        message = if (mutationRevision != favoriteMutationRevision.get()) {
                            state.message
                        } else if (!sessionPersisted) {
                            "JM 登录成功，但登录状态无法写入本机"
                        } else if (syncError == null) {
                            "已登录 JM 官方账号 ${account.username}"
                        } else {
                            "已登录 JM 官方账号，收藏稍后重试"
                        },
                    )
                }
            } catch (error: Throwable) {
                error.rethrowCancellation()
                gateway.clearSession()
                clearStoredSession()
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
        favoriteOperationGeneration.incrementAndGet()
        favoriteFolderRequestGeneration.incrementAndGet()
        favoriteFolderJob?.cancel()
        _state.update { it.copy(favoritePendingKeys = emptySet()) }
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            runCatching { favoriteOperationLimiter.withPermit { gateway.logout() } }
                .onFailure { error -> error.rethrowCancellation() }
            clearStoredSession()
            val cachedFavorites = withContext(Dispatchers.IO) { libraryStore.loadFavorites() }
            _state.update {
                it.copy(
                    account = JmAccountUiState(),
                    favorites = cachedFavorites,
                    favoriteFolders = JmFavoriteFoldersUiState(
                        items = cachedFavorites,
                        total = cachedFavorites.size.toLong(),
                    ),
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
        cancelSourceRefreshForForeground()
        val operationGeneration = favoriteOperationGeneration.get()
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            _state.update { state -> state.copy(account = state.account.copy(syncing = true, error = null)) }
            val mutationRevision = favoriteMutationRevision.get()
            try {
                if (
                    operationGeneration != favoriteOperationGeneration.get() ||
                    !_state.value.account.signedIn
                ) return@launch
                val account = JmAccount(uid = current.uid, username = current.username)
                val favorites = gateway.favoriteCollection { partial ->
                    if (operationGeneration != favoriteOperationGeneration.get()) return@favoriteCollection
                    publishOfficialAccount(
                        account = account,
                        favorites = partial,
                        expectedMutationRevision = mutationRevision,
                        mergeWithCached = true,
                        syncing = true,
                    )
                }
                if (operationGeneration != favoriteOperationGeneration.get()) return@launch
                val sessionPersisted = saveLatestGatewaySession()
                val published = publishOfficialAccount(
                    account = account,
                    favorites = favorites,
                    expectedMutationRevision = mutationRevision,
                )
                if (published && !sessionPersisted) {
                    _state.update { state ->
                        state.copy(account = state.account.copy(error = SESSION_PERSISTENCE_ERROR))
                    }
                }
                _state.update { state ->
                    state.copy(
                        account = state.account.copy(syncing = false),
                        message = if (published) "JM 官方收藏已同步" else state.message,
                    )
                }
            } catch (error: Throwable) {
                error.rethrowCancellation()
                if (error is JmAuthException) {
                    invalidateFavoriteFolderSession(error)
                } else if (mutationRevision != favoriteMutationRevision.get()) {
                    _state.update { state -> state.copy(account = state.account.copy(syncing = false)) }
                } else {
                    val sessionPersisted = saveLatestGatewaySession()
                    _state.update { state ->
                        state.copy(
                            account = state.account.copy(
                                syncing = false,
                                error = listOfNotNull(
                                    "收藏同步失败：${error.readable()}",
                                    SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                                ).joinToString("；"),
                            ),
                            message = "JM 收藏同步失败",
                        )
                    }
                }
            }
        }
    }

    fun selectFavoriteFolder(folderId: String, force: Boolean = false) {
        val safeFolderId = folderId.trim()
        val current = _state.value
        if (!current.account.signedIn) {
            _state.update { it.copy(message = "请先登录 JM 官方账号") }
            return
        }
        if (!safeFolderId.matches(SAFE_JM_ID) || current.favoriteFolders.folders.none { it.id == safeFolderId }) return
        if (safeFolderId == "0") {
            favoriteFolderRequestGeneration.incrementAndGet()
            favoriteFolderJob?.cancel()
            _state.update { state ->
                state.copy(
                    favoriteFolders = state.favoriteFolders.copy(
                        selectedFolderId = "0",
                        items = state.favorites,
                        total = state.account.favoriteCount ?: state.favorites.size.toLong(),
                        loading = false,
                        error = null,
                    ),
                )
            }
            return
        }
        cancelSourceRefreshForForeground()
        if (
            !force && current.favoriteFolders.selectedFolderId == safeFolderId &&
            !current.favoriteFolders.loading && current.favoriteFolders.error == null
        ) return

        val requestGeneration = favoriteFolderRequestGeneration.incrementAndGet()
        favoriteFolderJob?.cancel()
        _state.update { state ->
            state.copy(
                favoriteFolders = state.favoriteFolders.copy(
                    selectedFolderId = safeFolderId,
                    items = if (state.favoriteFolders.selectedFolderId == safeFolderId) state.favoriteFolders.items else emptyList(),
                    total = if (state.favoriteFolders.selectedFolderId == safeFolderId) state.favoriteFolders.total else 0L,
                    loading = true,
                    error = null,
                ),
            )
        }
        favoriteFolderJob = viewModelScope.launch {
            val mutationRevision = favoriteMutationRevision.get()
            try {
                suspend fun publishFolder(collection: JmFavoriteCollection): Boolean {
                    val items = mapFavoriteItems(collection.items)
                    val fetchedFolders = collection.folders.toFavoriteFolderUiItems()
                    var published = false
                    if (requestGeneration != favoriteFolderRequestGeneration.get()) return false
                    _state.update { state ->
                        if (
                            requestGeneration != favoriteFolderRequestGeneration.get() ||
                            mutationRevision != favoriteMutationRevision.get() ||
                            state.favoriteFolders.selectedFolderId != safeFolderId
                        ) return@update state
                        published = true
                        state.copy(
                            favoriteFolders = state.favoriteFolders.copy(
                                folders = fetchedFolders.takeIf { folders -> folders.any { it.id == safeFolderId } }
                                    ?: state.favoriteFolders.folders,
                                items = items,
                                total = collection.total,
                                loading = false,
                                error = null,
                            ),
                        )
                    }
                    return published
                }

                val collection = gateway.favoriteCollection(folderId = safeFolderId) { partial ->
                    publishFolder(partial)
                }
                val sessionPersisted = saveLatestGatewaySession()
                if (requestGeneration != favoriteFolderRequestGeneration.get()) return@launch
                val published = publishFolder(collection)
                _state.update { state ->
                    if (state.favoriteFolders.selectedFolderId != safeFolderId) return@update state
                    state.copy(
                        account = state.account.copy(
                            error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted || !published },
                        ),
                        favoriteFolders = state.favoriteFolders.copy(loading = false),
                    )
                }
            } catch (error: Throwable) {
                error.rethrowCancellation()
                if (error is JmAuthException) {
                    invalidateFavoriteFolderSession(error)
                } else if (mutationRevision != favoriteMutationRevision.get()) {
                    _state.update { state ->
                        if (state.favoriteFolders.selectedFolderId != safeFolderId) return@update state
                        state.copy(favoriteFolders = state.favoriteFolders.copy(loading = false))
                    }
                } else if (requestGeneration == favoriteFolderRequestGeneration.get()) {
                    val sessionPersisted = saveLatestGatewaySession()
                    _state.update { state ->
                        if (state.favoriteFolders.selectedFolderId != safeFolderId) return@update state
                        state.copy(
                            account = state.account.copy(
                                error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                            ),
                            favoriteFolders = state.favoriteFolders.copy(
                                loading = false,
                                error = "收藏夹读取失败：${error.readable()}",
                            ),
                        )
                    }
                }
            }
        }
    }

    fun retryFavoriteFolder() {
        selectFavoriteFolder(_state.value.favoriteFolders.selectedFolderId, force = true)
    }

    fun createFavoriteFolder(name: String) {
        val safeName = name.trim()
        val current = _state.value
        if (!current.account.signedIn) {
            _state.update { it.copy(message = "请先登录 JM 官方账号") }
            return
        }
        if (current.favoriteFolders.creating) return
        if (safeName.isBlank()) {
            _state.update { it.copy(message = "收藏夹名称不能为空") }
            return
        }
        if (safeName.length > MAX_FAVORITE_FOLDER_NAME_LENGTH) {
            _state.update { it.copy(message = "收藏夹名称不能超过 $MAX_FAVORITE_FOLDER_NAME_LENGTH 个字符") }
            return
        }

        val operationGeneration = favoriteOperationGeneration.get()
        favoriteMutationRevision.incrementAndGet()
        val knownFolderIds = current.favoriteFolders.folders.mapTo(hashSetOf(), JmFavoriteFolderUiItem::id)
        _state.update { state ->
            state.copy(favoriteFolders = state.favoriteFolders.copy(creating = true, error = null))
        }
        viewModelScope.launch {
            try {
                favoriteOperationLimiter.withPermit {
                    if (
                        operationGeneration != favoriteOperationGeneration.get() ||
                        !_state.value.account.signedIn
                    ) return@withPermit
                    gateway.createFavoriteFolder(safeName)
                    val refreshed = gateway.favoritePage(page = 1, folderId = "0")
                    if (operationGeneration != favoriteOperationGeneration.get()) return@withPermit
                    val parsedFolders = refreshed.folders.toFavoriteFolderUiItems()
                    val folders = parsedFolders.takeIf { it.size > 1 || _state.value.favoriteFolders.folders.size <= 1 }
                        ?: _state.value.favoriteFolders.folders
                    val createdFolder = folders.firstOrNull { it.id !in knownFolderIds }
                    val sessionPersisted = saveLatestGatewaySession()
                    _state.update { state ->
                        state.copy(
                            account = state.account.copy(
                                error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                            ),
                            favoriteFolders = state.favoriteFolders.copy(
                                folders = folders,
                                creating = false,
                                error = null,
                            ),
                            message = createdFolder?.let { "已创建收藏夹「${it.name}」" }
                                ?: "收藏夹已创建",
                        )
                    }
                }
            } catch (error: Throwable) {
                error.rethrowCancellation()
                if (error is JmAuthException) {
                    invalidateFavoriteFolderSession(error)
                } else {
                    val sessionPersisted = saveLatestGatewaySession()
                    _state.update { state ->
                        state.copy(
                            account = state.account.copy(
                                error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                            ),
                            favoriteFolders = state.favoriteFolders.copy(creating = false),
                            message = "创建收藏夹失败：${error.readable()}",
                        )
                    }
                }
            } finally {
                favoriteMutationRevision.incrementAndGet()
                _state.update { state ->
                    state.copy(favoriteFolders = state.favoriteFolders.copy(creating = false))
                }
            }
        }
    }

    fun moveFavoriteToFolder(item: ComicUiItem, folderId: String) {
        val targetFolderId = folderId.trim()
        val current = _state.value
        val targetFolder = current.favoriteFolders.folders.firstOrNull { it.id == targetFolderId }
        if (!current.account.signedIn) {
            _state.update { it.copy(message = "请先登录 JM 官方账号") }
            return
        }
        if (
            !item.jmId.matches(SAFE_JM_ID) || targetFolderId == "0" || targetFolder == null ||
            targetFolderId == current.favoriteFolders.selectedFolderId || current.favoriteFolders.movingKey != null
        ) return

        val operationGeneration = favoriteOperationGeneration.get()
        favoriteMutationRevision.incrementAndGet()
        val sourceFolderId = current.favoriteFolders.selectedFolderId
        _state.update { state ->
            state.copy(favoriteFolders = state.favoriteFolders.copy(movingKey = item.key, error = null))
        }
        viewModelScope.launch {
            try {
                favoriteOperationLimiter.withPermit {
                    if (
                        operationGeneration != favoriteOperationGeneration.get() ||
                        !_state.value.account.signedIn
                    ) return@withPermit
                    gateway.moveFavoriteToFolder(item.jmId, targetFolderId)
                    if (operationGeneration != favoriteOperationGeneration.get()) return@withPermit
                    val sessionPersisted = saveLatestGatewaySession()
                    _state.update { state ->
                        val stillShowingSource = state.favoriteFolders.selectedFolderId == sourceFolderId
                        val sourceItems = if (stillShowingSource && sourceFolderId != "0") {
                            state.favoriteFolders.items.filterNot { it.key == item.key }
                        } else {
                            state.favoriteFolders.items
                        }
                        val sourceTotal = if (
                            stillShowingSource && sourceFolderId != "0" &&
                            state.favoriteFolders.items.any { it.key == item.key }
                        ) {
                            (state.favoriteFolders.total - 1L).coerceAtLeast(0L)
                        } else {
                            state.favoriteFolders.total
                        }
                        state.copy(
                            account = state.account.copy(
                                error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                            ),
                            favoriteFolders = state.favoriteFolders.copy(
                                items = sourceItems,
                                total = sourceTotal,
                                movingKey = null,
                                error = null,
                            ),
                            message = "已移动到「${targetFolder.name}」",
                        )
                    }
                }
            } catch (error: Throwable) {
                error.rethrowCancellation()
                if (error is JmAuthException) {
                    invalidateFavoriteFolderSession(error)
                } else {
                    val sessionPersisted = saveLatestGatewaySession()
                    _state.update { state ->
                        state.copy(
                            account = state.account.copy(
                                error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                            ),
                            favoriteFolders = state.favoriteFolders.copy(movingKey = null),
                            message = "移动收藏失败：${error.readable()}",
                        )
                    }
                }
            } finally {
                favoriteMutationRevision.incrementAndGet()
                _state.update { state ->
                    state.copy(favoriteFolders = state.favoriteFolders.copy(movingKey = null))
                }
            }
        }
    }

    private suspend fun invalidateFavoriteFolderSession(error: JmAuthException) {
        favoriteOperationGeneration.incrementAndGet()
        favoriteFolderRequestGeneration.incrementAndGet()
        gateway.clearSession()
        clearStoredSession()
        _state.update { state ->
            state.copy(
                account = JmAccountUiState(status = JmAccountStatus.Error, error = error.readable()),
                favoriteFolders = JmFavoriteFoldersUiState(
                    items = state.favorites,
                    total = state.favorites.size.toLong(),
                ),
                favoritePendingKeys = emptySet(),
                message = "JM 登录已失效，请重新登录",
            )
        }
    }

    private suspend fun publishOfficialAccount(
        account: JmAccount,
        favorites: JmFavoriteCollection,
        expectedMutationRevision: Long? = null,
        mergeWithCached: Boolean = false,
        syncing: Boolean = false,
    ): Boolean {
        val fetchedItems = mapFavoriteItems(favorites.items)
        val fetchedFolders = favorites.folders.toFavoriteFolderUiItems()
        var published = false
        _state.update { state ->
            if (
                expectedMutationRevision != null &&
                expectedMutationRevision != favoriteMutationRevision.get()
            ) return@update state
            val items = if (mergeWithCached) {
                (fetchedItems + state.favorites)
                    .distinctBy(ComicUiItem::key)
                    .take(MAX_FAVORITE_ENTRIES)
            } else {
                fetchedItems
            }
            val folders = fetchedFolders.takeIf { it.size > 1 || state.favoriteFolders.folders.size <= 1 }
                ?: state.favoriteFolders.folders
            val selectedFolderId = state.favoriteFolders.selectedFolderId
                .takeIf { selected -> folders.any { it.id == selected } }
                ?: "0"
            val favoriteKeys = items.mapTo(hashSetOf(), ComicUiItem::key)
            published = true
            state.copy(
                account = JmAccountUiState(
                    status = JmAccountStatus.SignedIn,
                    uid = account.uid,
                    username = account.username,
                    favoriteCount = account.favoriteCount ?: favorites.total,
                    error = null,
                    syncing = syncing,
                ),
                favorites = items,
                favoriteFolders = state.favoriteFolders.copy(
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    items = if (selectedFolderId == "0") {
                        items
                    } else if (mergeWithCached) {
                        state.favoriteFolders.items
                    } else {
                        state.favoriteFolders.items.filter { it.key in favoriteKeys }
                    },
                    total = if (selectedFolderId == "0") favorites.total else state.favoriteFolders.total,
                    loading = false,
                    error = null,
                ),
            )
        }
        if (published && !mergeWithCached) persistFavoritesSnapshot()
        return published
    }

    /** Toggle a comic through JM's authenticated /favorite endpoint. */
    fun toggleFavorite(item: ComicUiItem) {
        if (!_state.value.account.signedIn) {
            _state.update { it.copy(message = "请先在设置中登录 JM 官方账号") }
            return
        }
        val id = item.jmId.trim()
        if (!id.matches(SAFE_JM_ID) || !favoriteOperations.add(id)) return
        val operationGeneration = favoriteOperationGeneration.get()
        val mutation = favoriteMutationSnapshot(_state.value.favorites, item)
        favoriteMutationRevision.incrementAndGet()
        _state.update { state ->
            state.copy(favoritePendingKeys = state.favoritePendingKeys + item.key)
        }
        applyOptimisticFavoriteMutation(item, mutation, operationGeneration)
        viewModelScope.launch {
            try {
                favoriteOperationLimiter.withPermit {
                    if (
                        operationGeneration != favoriteOperationGeneration.get() ||
                        !_state.value.account.signedIn
                    ) return@withPermit
                    try {
                        gateway.toggleFavorite(id)
                        if (operationGeneration != favoriteOperationGeneration.get()) return@withPermit
                        val sessionPersisted = saveLatestGatewaySession()
                        _state.update { state ->
                            if (operationGeneration != favoriteOperationGeneration.get()) return@update state
                            val removeFromSelectedFolder = mutation.wasFavorite &&
                                state.favoriteFolders.selectedFolderId != "0" &&
                                state.favoriteFolders.items.any { it.key == item.key }
                            state.copy(
                                account = state.account.copy(
                                    error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                                ),
                                favoriteFolders = if (removeFromSelectedFolder) {
                                    state.favoriteFolders.copy(
                                        items = state.favoriteFolders.items.filterNot { it.key == item.key },
                                        total = (state.favoriteFolders.total - 1L).coerceAtLeast(0L),
                                    )
                                } else {
                                    state.favoriteFolders
                                },
                                message = if (mutation.wasFavorite) {
                                    "已取消 JM 收藏"
                                } else {
                                    "已加入 JM 收藏"
                                },
                            )
                        }
                        persistFavoritesSnapshot()
                    } catch (error: Throwable) {
                        error.rethrowCancellation()
                        if (error is JmAuthException) throw error
                        val official = try {
                            gateway.favoriteCollection()
                        } catch (reconcileError: Throwable) {
                            reconcileError.rethrowCancellation()
                            if (reconcileError is JmAuthException) throw reconcileError
                            null
                        }
                        if (official != null && operationGeneration == favoriteOperationGeneration.get()) {
                            val account = _state.value.account
                            publishOfficialAccount(
                                JmAccount(uid = account.uid, username = account.username),
                                official,
                            )
                            val sessionPersisted = saveLatestGatewaySession()
                            val isOfficiallyFavorite = official.items.any { it.id == id }
                            _state.update { state ->
                                if (operationGeneration != favoriteOperationGeneration.get()) return@update state
                                state.copy(
                                    account = state.account.copy(
                                        error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                                    ),
                                    message = if (isOfficiallyFavorite) {
                                        "网络响应较慢，已按官方收藏夹确认为已收藏"
                                    } else {
                                        "网络响应较慢，已按官方收藏夹确认为未收藏"
                                    },
                                )
                            }
                        } else {
                            rollbackFavoriteMutation(item, mutation, operationGeneration)
                            val sessionPersisted = saveLatestGatewaySession()
                            _state.update { state ->
                                if (operationGeneration != favoriteOperationGeneration.get()) return@update state
                                state.copy(
                                    account = state.account.copy(
                                        error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                                    ),
                                    message = "JM 收藏操作失败，已恢复原状态：${error.readable()}",
                                )
                            }
                            persistFavoritesSnapshot()
                        }
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    rollbackFavoriteMutation(item, mutation, operationGeneration)
                    throw error
                }
                error.rethrowCancellation()
                if (error is JmAuthException) {
                    rollbackFavoriteMutation(item, mutation, operationGeneration)
                    invalidateFavoriteFolderSession(error)
                } else {
                    rollbackFavoriteMutation(item, mutation, operationGeneration)
                    val sessionPersisted = saveLatestGatewaySession()
                    _state.update { state ->
                        if (operationGeneration != favoriteOperationGeneration.get()) return@update state
                        state.copy(
                            account = state.account.copy(
                                error = SESSION_PERSISTENCE_ERROR.takeUnless { sessionPersisted },
                            ),
                            message = "JM 收藏操作失败，已恢复原状态：${error.readable()}",
                        )
                    }
                    persistFavoritesSnapshot()
                }
            } finally {
                favoriteMutationRevision.incrementAndGet()
                favoriteOperations.remove(id)
                _state.update { state ->
                    state.copy(favoritePendingKeys = state.favoritePendingKeys - item.key)
                }
            }
        }
    }

    private fun applyOptimisticFavoriteMutation(
        item: ComicUiItem,
        mutation: FavoriteMutationSnapshot,
        operationGeneration: Long,
    ) {
        _state.update { state ->
            if (operationGeneration != favoriteOperationGeneration.get()) return@update state
            val shouldBeFavorite = !mutation.wasFavorite
            val currentlyFavorite = state.favorites.any { it.key == item.key }
            val next = favoriteItemsWithMembership(
                items = state.favorites,
                item = item,
                shouldBeFavorite = shouldBeFavorite,
                maxEntries = MAX_FAVORITE_ENTRIES,
            )
            val nextFavoriteCount = if (currentlyFavorite == shouldBeFavorite) {
                state.account.favoriteCount
            } else {
                adjustedFavoriteCount(
                    currentCount = state.account.favoriteCount,
                    loadedCount = state.favorites.size,
                    adding = shouldBeFavorite,
                )
            }
            state.copy(
                favorites = next,
                account = state.account.copy(
                    favoriteCount = nextFavoriteCount,
                    error = null,
                ),
                favoriteFolders = if (state.favoriteFolders.selectedFolderId == "0") {
                    state.favoriteFolders.copy(
                        items = next,
                        total = nextFavoriteCount ?: next.size.toLong(),
                    )
                } else {
                    state.favoriteFolders
                },
            )
        }
    }

    private fun rollbackFavoriteMutation(
        item: ComicUiItem,
        mutation: FavoriteMutationSnapshot?,
        operationGeneration: Long,
    ) {
        mutation ?: return
        _state.update { state ->
            if (operationGeneration != favoriteOperationGeneration.get()) return@update state
            val currentlyFavorite = state.favorites.any { it.key == item.key }
            val next = favoriteItemsWithMembership(
                items = state.favorites,
                item = item,
                shouldBeFavorite = mutation.wasFavorite,
                insertionIndex = mutation.originalIndex,
                maxEntries = MAX_FAVORITE_ENTRIES,
            )
            val nextFavoriteCount = if (currentlyFavorite == mutation.wasFavorite) {
                state.account.favoriteCount
            } else {
                adjustedFavoriteCount(
                    currentCount = state.account.favoriteCount,
                    loadedCount = state.favorites.size,
                    adding = mutation.wasFavorite,
                )
            }
            state.copy(
                favorites = next,
                account = state.account.copy(
                    favoriteCount = nextFavoriteCount,
                ),
                favoriteFolders = if (state.favoriteFolders.selectedFolderId == "0") {
                    state.favoriteFolders.copy(
                        items = next,
                        total = nextFavoriteCount ?: next.size.toLong(),
                    )
                } else {
                    state.favoriteFolders
                },
            )
        }
    }

    fun toggleFavorite(state: ComicResolveUiState.Ready) {
        toggleFavorite(state.toUiItem())
    }

    private fun persistFavoritesSnapshot() {
        val generation = favoritesPersistGeneration.incrementAndGet()
        val snapshot = _state.value.favorites
        persistenceScope.launch {
            persistBestEffort {
                if (generation != favoritesPersistGeneration.get()) return@persistBestEffort
                libraryStore.replaceFavorites(snapshot)
                persistedFavoritesGeneration = generation
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
        val historyGeneration = historyPersistGeneration.incrementAndGet()
        val historySnapshot = _state.value.history
        val progressGeneration = progressPersistGeneration.get()
        val progressSnapshot = latestProgressSnapshot
        readerStateSaveJob?.cancel()
        readerStateSaveJob = persistenceScope.launch {
            persistBestEffort {
                delay(LOCAL_STATE_SAVE_DEBOUNCE_MS)
                if (historyGeneration == historyPersistGeneration.get()) {
                    libraryStore.replaceHistory(historySnapshot)
                    persistedHistoryGeneration = historyGeneration
                }
                if (
                    progressSnapshot != null &&
                    progressGeneration > persistedProgressGeneration &&
                    progressGeneration == progressPersistGeneration.get()
                ) {
                    settingsStore.saveProgress(
                        comicId = progressSnapshot.comicId,
                        chapterId = progressSnapshot.chapterId,
                        pageIndex = progressSnapshot.pageIndex,
                        pageCount = progressSnapshot.pageCount,
                    )
                    persistedProgressGeneration = progressGeneration
                }
            }
        }
    }

    fun dismissDetail() {
        detailRequestGeneration.incrementAndGet()
        commentsRequestGeneration.incrementAndGet()
        favoriteFolderRequestGeneration.incrementAndGet()
        readerRequestGeneration.incrementAndGet()
        detailJob?.cancel()
        commentsJob?.cancel()
        readerJob?.cancel()
        readerWarmupJob?.cancel()
        readerBufferRefillJob?.cancel()
        chapterPreloader.cancelAll()
        readerWarmupChapterId = null
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
        readerWarmupChapterId = chapter.id
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
                    chapterPreloader.schedule(chapter.id, chapterPages.pages, pageIndex)
                    val warmupCount = when {
                        settings.readerPrefetchMode == ReaderPrefetchMode.UltraAggressive -> 4
                        settings.readerTurboMode -> 3
                        else -> 2
                    }
                    warmReaderPages(chapterPages.pages, pageIndex, warmupCount, settings)
                }
                .onFailure { error -> error.rethrowCancellation() }
        }
    }

    private suspend fun warmReaderPages(
        pages: List<JmPage>,
        startPageIndex: Int,
        pageBudget: Int,
        settings: AppSettings,
    ) {
        readerEntryWarmupIndices(startPageIndex, pages.size, pageBudget)
            .chunked(READER_WARMUP_CONCURRENCY)
            .forEach { batch ->
                coroutineScope {
                    batch.map { index ->
                        async(Dispatchers.IO) {
                            val page = pages.getOrNull(index) ?: return@async
                            runCatchingNonFatal {
                                gateway.prefetchPage(
                                    page,
                                    settings.readerImageQuality,
                                    settings.readerTurboMode,
                                )
                            }.onFailure { error -> error.rethrowCancellation() }
                        }
                    }.awaitAll()
                }
            }
    }

    private fun refillUltraReaderBuffer() {
        readerBufferRefillJob?.cancel()
        val snapshot = _state.value
        val reader = snapshot.reader as? ReaderUiState.Ready ?: return
        val settings = snapshot.settings
        if (
            !appInForeground.get() ||
            settings.dataSaver ||
            settings.readerPrefetchMode != ReaderPrefetchMode.UltraAggressive
        ) return
        readerBufferRefillJob = viewModelScope.launch {
            val pages = withContext(Dispatchers.Default) {
                reader.pages.map(DirectReaderPage::toJmPage)
            }
            val latestReader = _state.value.reader as? ReaderUiState.Ready ?: return@launch
            if (latestReader.chapterId != reader.chapterId) return@launch
            val startPageIndex = latestProgressSnapshot
                ?.takeIf { it.comicId == reader.sourceId && it.chapterId == reader.chapterId }
                ?.pageIndex
                ?: reader.initialPageIndex
            chapterPreloader.schedule(reader.chapterId, pages, startPageIndex)
            warmReaderPages(
                pages = pages,
                startPageIndex = startPageIndex,
                pageBudget = ULTRA_READER_ENTRY_READY_PAGES,
                settings = settings,
            )
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
        cancelSourceRefreshForForeground()
        val requestGeneration = readerRequestGeneration.incrementAndGet()
        readerBufferRefillJob?.cancel()
        chapterPreloader.cancelExcept(chapter.sourceChapterId)
        // Keep a same-chapter metadata request alive so the reader can reuse its
        // result. Only unrelated speculative work is cancelled; same-page image
        // work is already foreground-preemptible inside the gateway.
        if (readerWarmupChapterId != chapter.sourceChapterId) {
            readerWarmupJob?.cancel()
            readerWarmupJob = null
            readerWarmupChapterId = null
        }
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
                val chapterPages = gateway.chapter(chapter.sourceChapterId)
                val (readerPages, currentChapterIndex) = withContext(Dispatchers.Default) {
                    chapterPages.pages.map(JmPage::toDirectReaderPage) to
                        chapters.indexOfFirst { item -> item.sourceChapterId == chapter.sourceChapterId }.coerceAtLeast(0)
                }
                Triple(chapterPages, readerPages, currentChapterIndex)
            }
                .onSuccess { (chapterPages, readerPages, currentChapterIndex) ->
                    chapterPreloader.schedule(
                        chapterId = chapter.sourceChapterId,
                        pages = chapterPages.pages,
                        startPageIndex = initialPageIndex,
                    )
                    val warmupSettings = _state.value.settings
                    if (
                        !warmupSettings.dataSaver &&
                        warmupSettings.readerPrefetchMode == ReaderPrefetchMode.UltraAggressive
                    ) {
                        // Ultra mode deliberately keeps the preparation screen until a small
                        // decoded runway exists. Once content is shown, continuous scrolling can
                        // refill the larger forward window without exposing per-page skeletons.
                        warmReaderPages(
                            pages = chapterPages.pages,
                            startPageIndex = initialPageIndex,
                            pageBudget = ULTRA_READER_ENTRY_READY_PAGES,
                            settings = warmupSettings,
                        )
                    } else {
                        val warmupPage = chapterPages.pages.getOrNull(
                            initialPageIndex.coerceIn(0, chapterPages.pages.lastIndex.coerceAtLeast(0)),
                        )
                        if (!warmupSettings.dataSaver && warmupPage != null) {
                            // Start the first image as soon as chapter metadata is ready. The
                            // reader joins this request instead of waiting for its first frame.
                            launch(Dispatchers.IO) {
                                if (requestGeneration != readerRequestGeneration.get()) return@launch
                                runCatchingNonFatal {
                                    gateway.prefetchPage(
                                        warmupPage,
                                        warmupSettings.readerImageQuality,
                                        warmupSettings.readerTurboMode,
                                    )
                                }.onFailure { error -> error.rethrowCancellation() }
                            }
                        }
                    }
                    _state.update { state ->
                        if (requestGeneration != readerRequestGeneration.get()) return@update state
                        state.copy(
                            reader = ReaderUiState.Ready(
                                source = SourceIds.Jm,
                                sourceId = comicId,
                                title = comicTitle,
                                chapterId = chapter.sourceChapterId,
                                chapterTitle = chapter.title.ifBlank { chapterPages.title },
                                pages = readerPages,
                                chapters = chapters,
                                currentChapterIndex = currentChapterIndex,
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
                val selectionGeneration = readerRequestGeneration.incrementAndGet()
                viewModelScope.launch {
                    val resume = try {
                        withContext(Dispatchers.IO) {
                            settingsStore.loadChapterProgress(current.sourceId, chapter.sourceChapterId)?.pageIndex ?: 0
                        }
                    } catch (error: Throwable) {
                        error.rethrowCancellation()
                        0
                    }
                    if (selectionGeneration != readerRequestGeneration.get()) return@launch
                    val latest = _state.value.reader as? ReaderUiState.Ready ?: return@launch
                    if (latest.sourceId != current.sourceId || latest.title != current.title) return@launch
                    openReader(current.sourceId, current.title, chapter, current.chapters, resume, preserveCurrent = true)
                }
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
        if (!current.sourceId.matches(SAFE_JM_ID)) throw JmSourceException()
        if (!_state.value.settings.dataSaver) {
            gateway.warmImageConnections(current.sourceId, chapter.sourceChapterId)
        }
        val chapterPages = gateway.chapter(chapter.sourceChapterId)
        chapterPreloader.schedule(chapter.sourceChapterId, chapterPages.pages)
        val (pages, chapterIndex) = withContext(Dispatchers.Default) {
            chapterPages.pages.map(JmPage::toDirectReaderPage) to
                current.chapters.indexOfFirst { it.sourceChapterId == chapter.sourceChapterId }.coerceAtLeast(0)
        }
        return ReaderChapterSegment(
            chapterId = chapter.sourceChapterId,
            chapterTitle = chapter.title.ifBlank { chapterPages.title },
            chapterIndex = chapterIndex,
            pages = pages,
        )
    }

    fun closeReader() {
        readerRequestGeneration.incrementAndGet()
        readerJob?.cancel()
        readerBufferRefillJob?.cancel()
        chapterPreloader.cancelAll()
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
        if (!appInForeground.get()) return
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
        progressPersistGeneration.incrementAndGet()
        val snapshot = ReaderProgressSnapshot(comicId, chapterId, safePageIndex, pageCount)
        latestProgressSnapshot = snapshot
    }

    fun updateSettings(settings: AppSettings) {
        val previous = _state.value.settings
        val normalized = when {
            settings.readerPrefetchMode == ReaderPrefetchMode.UltraAggressive &&
                previous.readerPrefetchMode != ReaderPrefetchMode.UltraAggressive -> settings.copy(dataSaver = false)
            settings.dataSaver && !previous.dataSaver &&
                settings.readerPrefetchMode == ReaderPrefetchMode.UltraAggressive -> settings.copy(
                    readerTurboMode = false,
                    readerPrefetchMode = ReaderPrefetchMode.Conservative,
                    readerPrefetchPages = 1,
                )
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
        val settingsGeneration = settingsPersistGeneration.incrementAndGet()
        settingsSaveJob = persistenceScope.launch {
            persistBestEffort {
                delay(SETTINGS_SAVE_DEBOUNCE_MS)
                if (settingsGeneration == settingsPersistGeneration.get()) {
                    settingsStore.save(_state.value.settings)
                    persistedSettingsGeneration = settingsGeneration
                }
            }
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
        if (normalized.readerPrefetchMode != ReaderPrefetchMode.UltraAggressive || normalized.dataSaver) {
            readerBufferRefillJob?.cancel()
            chapterPreloader.cancelAll()
        } else if (
            previous.readerPrefetchMode != ReaderPrefetchMode.UltraAggressive ||
            previous.dataSaver ||
            previous.readerImageQuality != normalized.readerImageQuality ||
            previous.readerTurboMode != normalized.readerTurboMode
        ) {
            refillUltraReaderBuffer()
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

    private fun cancelSourceRefreshForForeground() {
        if (sourceRefreshJob?.isActive != true && !_state.value.sourceStatus.checking) return
        sourceRefreshRequestGeneration.incrementAndGet()
        sourceRefreshJob?.cancel()
        sourceRefreshJob = null
        _state.update { state ->
            if (!state.sourceStatus.checking) state
            else state.copy(sourceStatus = state.sourceStatus.copy(checking = false))
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
        cancelSourceRefreshForForeground()
        val requestGeneration = readerRequestGeneration.incrementAndGet()
        readerWarmupJob?.cancel()
        readerBufferRefillJob?.cancel()
        readerWarmupJob = null
        readerWarmupChapterId = null
        readerJob?.cancel()
        readerJob = viewModelScope.launch {
            val files = try {
                downloadStore.localPages(item)
            } catch (error: Throwable) {
                error.rethrowCancellation()
                _state.update { it.copy(message = "无法读取本地章节：${error.readable()}") }
                return@launch
            }
            if (requestGeneration != readerRequestGeneration.get()) return@launch
            if (!item.complete || files.size != item.pageCount || files.isEmpty()) {
                _state.update { state ->
                    if (requestGeneration != readerRequestGeneration.get()) return@update state
                    state.copy(message = "本地章节文件不完整，请重新下载")
                }
                return@launch
            }
            val chapter = SourceChapterDto(item.chapterId, 1, item.chapterTitle)
            val resumePageIndex = try {
                withContext(Dispatchers.IO) {
                    settingsStore.loadChapterProgress(item.comicId, item.chapterId)?.pageIndex ?: 0
                }
            } catch (error: Throwable) {
                error.rethrowCancellation()
                0
            }
            val pages = withContext(Dispatchers.Default) {
                files.mapIndexed { index, file ->
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
            }
            if (requestGeneration != readerRequestGeneration.get()) return@launch
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
        cancelSourceRefreshForForeground()
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

    /** Persist the latest bounded snapshots without blocking the lifecycle/main thread. */
    fun flushLocalState() {
        enqueueLocalStateFlush(closeAfter = false)
    }

    fun setAppForeground(foreground: Boolean) {
        appInForeground.set(foreground)
        if (!foreground) {
            cancelSourceRefreshForForeground()
            chapterPreloader.cancelAll()
            readerWarmupJob?.cancel()
            readerBufferRefillJob?.cancel()
            readerWarmupJob = null
            readerWarmupChapterId = null
        } else {
            refillUltraReaderBuffer()
        }
    }

    private fun enqueueLocalStateFlush(closeAfter: Boolean) {
        // A lifecycle stop is common during rotation/backgrounding. Only flush snapshots that
        // have actually changed since process start; otherwise every stop serializes the full
        // library and performs a synchronous preference write on the persistence worker.
        val favoritesGeneration = favoritesPersistGeneration.get()
        val historyGeneration = historyPersistGeneration.get()
        val settingsGeneration = settingsPersistGeneration.get()
        val writeFavorites = favoritesGeneration > persistedFavoritesGeneration
        val writeHistory = historyGeneration > persistedHistoryGeneration
        val writeSettings = settingsGeneration > persistedSettingsGeneration
        val progressGeneration = progressPersistGeneration.get()
        val stateSnapshot = _state.value
        val progressSnapshot = latestProgressSnapshot
        settingsSaveJob?.cancel()
        readerStateSaveJob?.cancel()
        persistenceScope.launch {
            try {
                persistBestEffort {
                    if (writeFavorites && favoritesGeneration == favoritesPersistGeneration.get()) {
                        libraryStore.replaceFavorites(stateSnapshot.favorites)
                        persistedFavoritesGeneration = favoritesGeneration
                    }
                    if (writeHistory && historyGeneration == historyPersistGeneration.get()) {
                        libraryStore.replaceHistory(stateSnapshot.history)
                        persistedHistoryGeneration = historyGeneration
                    }
                    if (
                        progressSnapshot != null &&
                        progressGeneration > persistedProgressGeneration &&
                        progressGeneration == progressPersistGeneration.get()
                    ) {
                        settingsStore.saveProgress(
                            comicId = progressSnapshot.comicId,
                            chapterId = progressSnapshot.chapterId,
                            pageIndex = progressSnapshot.pageIndex,
                            pageCount = progressSnapshot.pageCount,
                        )
                        persistedProgressGeneration = progressGeneration
                    }
                    if (writeSettings && settingsGeneration == settingsPersistGeneration.get()) {
                        settingsStore.save(stateSnapshot.settings)
                        persistedSettingsGeneration = settingsGeneration
                    }
                }
            } finally {
                if (closeAfter) persistenceScope.cancel()
            }
        }
    }

    private suspend fun persistBestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            error.rethrowCancellation()
            // Local persistence failures must not crash an otherwise usable session.
        }
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
        favoriteFolderJob?.cancel()
        readerJob?.cancel()
        readerWarmupJob?.cancel()
        readerBufferRefillJob?.cancel()
        chapterPreloader.cancelAll()
        settingsSaveJob?.cancel()
        readerStateSaveJob?.cancel()
        weeklyCatalogJob?.cancel()
        weeklyJob?.cancel()
        typeRankingJob?.cancel()
        sourceRefreshJob?.cancel()
        sourceScheduleJob?.cancel()
        updateCheckJob?.cancel()
        enqueueLocalStateFlush(closeAfter = true)
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

    private const val COMIC_CACHE_LIMIT = 48
private const val MAX_FAVORITE_ENTRIES = 200
private const val MAX_HISTORY_ENTRIES = 100
private const val LOCAL_STATE_SAVE_DEBOUNCE_MS = 5_000L
private const val SETTINGS_SAVE_DEBOUNCE_MS = 250L
private const val INITIAL_HOME_PRIORITY_TIMEOUT_MS = 2_500L
private const val SOURCE_REFRESH_START_DELAY_MS = 10_000L
private const val SOURCE_REFRESH_CHECK_INTERVAL_MS = 15L * 60L * 1_000L
private const val ACCOUNT_SYNC_START_DELAY_MS = 4_000L
private const val READER_WARMUP_CONCURRENCY = 2
private const val ULTRA_READER_ENTRY_READY_PAGES = 3
private const val MAX_PAGINATION_PAGE = 200
private const val MAX_SEARCH_QUERY_LENGTH = 160
private const val MAX_BROWSE_OPTION_LENGTH = 128
private const val MAX_READER_PAGE_COUNT = 20_000
private const val INVALID_PAGINATION_MESSAGE = "上游分页响应异常，请稍后重试"
private const val SESSION_PERSISTENCE_ERROR = "当前会话有效，但无法保存登录状态；重启后可能需要重新登录"
private const val SOURCE_REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1_000L
