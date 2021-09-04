package eu.kanade.tachiyomi.ui.browse.source.browse

import android.os.Bundle
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.browse.source.filter.AutoComplete
import eu.kanade.tachiyomi.ui.browse.source.filter.AutoCompleteSectionItem
import eu.kanade.tachiyomi.ui.browse.source.filter.CheckboxItem
import eu.kanade.tachiyomi.ui.browse.source.filter.CheckboxSectionItem
import eu.kanade.tachiyomi.ui.browse.source.filter.GroupItem
import eu.kanade.tachiyomi.ui.browse.source.filter.HeaderItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SelectItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SelectSectionItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SeparatorItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SortGroup
import eu.kanade.tachiyomi.ui.browse.source.filter.SortItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TextItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TextSectionItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TriStateItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TriStateSectionItem
import eu.kanade.tachiyomi.util.chapter.ChapterSettingsHelper
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithTrackServiceTwoWay
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.removeCovers
import exh.log.xLogE
import exh.savedsearches.EXHSavedSearch
import exh.savedsearches.JsonSavedSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.lang.RuntimeException
import java.util.Date

/**
 * Presenter of [BrowseSourceController].
 */
open class BrowseSourcePresenter(
    private val sourceId: Long,
    searchQuery: String? = null,
    // SY -->
    private val filters: String? = null,
    // SY <--
    private val sourceManager: SourceManager = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get(),
    private val prefs: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get()
) : BasePresenter<BrowseSourceController>() {

    /**
     * Selected source.
     */
    lateinit var source: CatalogueSource

    /**
     * Modifiable list of filters.
     */
    var sourceFilters = FilterList()
        set(value) {
            field = value
            filterItems = value.toItems()
        }

    var filterItems: List<IFlexible<*>> = emptyList()

    /**
     * List of filters used by the [Pager]. If empty alongside [query], the popular query is used.
     */
    var appliedFilters = FilterList()

    /**
     * Pager containing a list of manga results.
     */
    private lateinit var pager: Pager

    /**
     * Flow of manga list to initialize.
     */
    private val mangaDetailsFlow = MutableStateFlow<List<Manga>>(emptyList())

    /**
     * Subscription for the pager.
     */
    private var pagerSubscription: Subscription? = null

    /**
     * Subscription for one request from the pager.
     */
    private var nextPageJob: Job? = null

    private val loggedServices by lazy { Injekt.get<TrackManager>().services.filter { it.isLogged } }

    // SY -->
    private val filterSerializer = FilterSerializer()
    // SY <--

    init {
        query = searchQuery ?: ""
    }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        source = sourceManager.get(sourceId) as? CatalogueSource ?: return

        sourceFilters = source.getFilterList()

        // SY -->
        if (filters != null) {
            val filters = Json.decodeFromString<JsonSavedSearch>(filters)
            filterSerializer.deserialize(sourceFilters, filters.filters)
        }
        val allDefault = sourceFilters == source.getFilterList()
        // SY <--

        if (savedState != null) {
            query = savedState.getString(::query.name, "")
        }

        restartPager(/* SY -->*/ filters = if (allDefault) this.appliedFilters else sourceFilters /* SY <--*/)
    }

    override fun onSave(state: Bundle) {
        state.putString(::query.name, query)
        super.onSave(state)
    }

    /**
     * Restarts the pager for the active source with the provided query and filters.
     *
     * @param query the query.
     * @param filters the current state of the filters (for search mode).
     */
    fun restartPager(query: String = this.query, filters: FilterList = this.appliedFilters) {
        this.query = query
        this.appliedFilters = filters

        // Create a new pager.
        pager = createPager(query, filters)

        val sourceId = source.id

        val sourceDisplayMode = prefs.sourceDisplayMode()

        // Prepare the pager.
        pagerSubscription?.let { remove(it) }
        pagerSubscription = pager.results()
            .observeOn(Schedulers.io())
            // SY -->
            .map { (page, mangas, metadata) ->
                Triple(page, mangas.map { networkToLocalManga(it, sourceId) }, metadata)
            }
            // SY <--
            .doOnNext { initializeMangas(it.second) }
            // SY -->
            .map { (page, mangas, metadata) ->
                page to mangas.mapIndexed { index, manga ->
                    SourceItem(manga, sourceDisplayMode, metadata?.getOrNull(index))
                }
            }
            // SY <--
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeReplay(
                { view, (page, mangas) ->
                    view.onAddPage(page, mangas)
                },
                { _, error ->
                    Timber.e(error)
                }
            )

        // Request first page.
        requestNext()
    }

    /**
     * Requests the next page for the active pager.
     */
    fun requestNext() {
        if (!hasNextPage()) return

        nextPageJob?.cancel()
        nextPageJob = launchIO {
            try {
                pager.requestNextPage()
            } catch (e: Throwable) {
                withUIContext { view?.onAddPageError(e) }
            }
        }
    }

    /**
     * Returns true if the last fetched page has a next page.
     */
    fun hasNextPage(): Boolean {
        return pager.hasNextPage
    }

    /**
     * Returns a manga from the database for the given manga from network. It creates a new entry
     * if the manga is not yet in the database.
     *
     * @param sManga the manga from the source.
     * @return a manga from the database.
     */
    private fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga {
        var localManga = db.getManga(sManga.url, sourceId).executeAsBlocking()
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            val result = db.insertManga(newManga).executeAsBlocking()
            newManga.id = result.insertedId()
            localManga = newManga
        }
        return localManga
    }

    /**
     * Initialize a list of manga.
     *
     * @param mangas the list of manga to initialize.
     */
    fun initializeMangas(mangas: List<Manga>) {
        presenterScope.launchIO {
            mangas.asFlow()
                .filter { it.thumbnail_url == null && !it.initialized }
                .map { getMangaDetails(it) }
                .onEach {
                    withUIContext {
                        @Suppress("DEPRECATION")
                        view?.onMangaInitialized(it)
                    }
                }
                .catch { e -> Timber.e(e) }
                .collect()
        }
    }

    /**
     * Returns the initialized manga.
     *
     * @param manga the manga to initialize.
     * @return the initialized manga
     */
    private suspend fun getMangaDetails(manga: Manga): Manga {
        try {
            val networkManga = source.getMangaDetails(manga.toMangaInfo())
            manga.copyFrom(networkManga.toSManga())
            manga.initialized = true
            db.insertManga(manga).executeAsBlocking()
        } catch (e: Exception) {
            Timber.e(e)
        }
        return manga
    }

    /**
     * Adds or removes a manga from the library.
     *
     * @param manga the manga to update.
     */
    fun changeMangaFavorite(manga: Manga) {
        manga.favorite = !manga.favorite
        manga.date_added = when (manga.favorite) {
            true -> Date().time
            false -> 0
        }

        if (!manga.favorite) {
            manga.removeCovers(coverCache)
        } else {
            ChapterSettingsHelper.applySettingDefaults(manga)

            autoAddTrack(manga)
        }

        db.insertManga(manga).executeAsBlocking()
    }

    private fun autoAddTrack(manga: Manga) {
        loggedServices
            .filterIsInstance<EnhancedTrackService>()
            .filter { it.accept(source) }
            .forEach { service ->
                launchIO {
                    try {
                        service.match(manga)?.let { track ->
                            track.manga_id = manga.id!!
                            (service as TrackService).bind(track)
                            db.insertTrack(track).executeAsBlocking()

                            syncChaptersWithTrackServiceTwoWay(db, db.getChapters(manga).executeAsBlocking(), track, service as TrackService)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Could not match manga: ${manga.title} with service $service")
                    }
                }
            }
    }

    /**
     * Set the filter states for the current source.
     *
     * @param filters a list of active filters.
     */
    fun setSourceFilter(filters: FilterList) {
        restartPager(filters = filters)
    }

    open fun createPager(query: String, filters: FilterList): Pager {
        return SourcePager(source, query, filters)
    }

    // SY -->
    companion object {
        // SY <--
        fun FilterList.toItems(): List<IFlexible<*>> {
            return mapNotNull { filter ->
                when (filter) {
                    is Filter.Header -> HeaderItem(filter)
                    // --> EXH
                    is Filter.AutoComplete -> AutoComplete(filter)
                    // <-- EXH
                    is Filter.Separator -> SeparatorItem(filter)
                    is Filter.CheckBox -> CheckboxItem(filter)
                    is Filter.TriState -> TriStateItem(filter)
                    is Filter.Text -> TextItem(filter)
                    is Filter.Select<*> -> SelectItem(filter)
                    is Filter.Group<*> -> {
                        val group = GroupItem(filter)
                        val subItems = filter.state.mapNotNull {
                            when (it) {
                                is Filter.CheckBox -> CheckboxSectionItem(it)
                                is Filter.TriState -> TriStateSectionItem(it)
                                is Filter.Text -> TextSectionItem(it)
                                is Filter.Select<*> -> SelectSectionItem(it)
                                // SY -->
                                is Filter.AutoComplete -> AutoCompleteSectionItem(it)
                                // SY <--
                                else -> null
                            }
                        }
                        subItems.forEach { it.header = group }
                        group.subItems = subItems
                        group
                    }
                    is Filter.Sort -> {
                        val group = SortGroup(filter)
                        val subItems = filter.values.map {
                            SortItem(it, group)
                        }
                        group.subItems = subItems
                        group
                    }
                }
            }
        }
        // SY -->
    }
    // SY <--

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    fun getCategories(): List<Category> {
        return db.getCategories().executeAsBlocking()
    }

    /**
     * Gets the category id's the manga is in, if the manga is not in a category, returns the default id.
     *
     * @param manga the manga to get categories from.
     * @return Array of category ids the manga is in, if none returns default id
     */
    fun getMangaCategoryIds(manga: Manga): Array<Int?> {
        val categories = db.getCategoriesForManga(manga).executeAsBlocking()
        return categories.mapNotNull { it.id }.toTypedArray()
    }

    /**
     * Move the given manga to categories.
     *
     * @param categories the selected categories.
     * @param manga the manga to move.
     */
    private fun moveMangaToCategories(manga: Manga, categories: List<Category>) {
        val mc = categories.filter { it.id != 0 }.map { MangaCategory.create(manga, it) }
        db.setMangaCategories(mc, listOf(manga))
    }

    /**
     * Move the given manga to the category.
     *
     * @param category the selected category.
     * @param manga the manga to move.
     */
    fun moveMangaToCategory(manga: Manga, category: Category?) {
        moveMangaToCategories(manga, listOfNotNull(category))
    }

    /**
     * Update manga to use selected categories.
     *
     * @param manga needed to change
     * @param selectedCategories selected categories
     */
    fun updateMangaCategories(manga: Manga, selectedCategories: List<Category>) {
        if (!manga.favorite) {
            changeMangaFavorite(manga)
        }

        moveMangaToCategories(manga, selectedCategories)
    }

    // EXH -->
    fun saveSearches(searches: List<EXHSavedSearch>) {
        val otherSerialized = prefs.savedSearches().get().filterNot {
            it.startsWith("${source.id}:")
        }.toSet()
        val newSerialized = searches.map {
            "${source.id}:" + Json.encodeToString(
                JsonSavedSearch(
                    it.name,
                    it.query,
                    if (it.filterList != null) {
                        filterSerializer.serialize(it.filterList)
                    } else JsonArray(emptyList())
                )
            )
        }
        prefs.savedSearches().set(otherSerialized + newSerialized)
    }

    fun loadSearches(): List<EXHSavedSearch> {
        return prefs.savedSearches().get().mapNotNull {
            val id = it.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
            if (id != source.id) return@mapNotNull null
            val content = try {
                Json.decodeFromString<JsonSavedSearch>(it.substringAfter(':'))
            } catch (e: Exception) {
                return@mapNotNull null
            }
            try {
                val originalFilters = source.getFilterList()
                filterSerializer.deserialize(originalFilters, content.filters)
                EXHSavedSearch(
                    content.name,
                    content.query,
                    originalFilters
                )
            } catch (t: RuntimeException) {
                // Load failed
                xLogE("Failed to load saved search!", t)
                EXHSavedSearch(
                    content.name,
                    content.query,
                    null
                )
            }
        }
    }
    // EXH <--
}
