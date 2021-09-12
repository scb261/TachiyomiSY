package eu.kanade.tachiyomi.ui.library

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.jakewharton.rxrelay.BehaviorRelay
import com.jakewharton.rxrelay.PublishRelay
import com.tfcporciuncula.flow.Preference
import dev.chrisbanes.insetter.applyInsetter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.databinding.LibraryControllerBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.SearchableNucleusController
import eu.kanade.tachiyomi.ui.base.controller.TabbedController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.EmptyView
import eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
import exh.favorites.FavoritesIntroDialog
import exh.favorites.FavoritesSyncStatus
import exh.source.MERGED_SOURCE_ID
import exh.source.PERV_EDEN_EN_SOURCE_ID
import exh.source.PERV_EDEN_IT_SOURCE_ID
import exh.source.isEhBasedManga
import exh.source.mangaDexSourceIds
import exh.source.nHentaiSourceIds
import exh.ui.LoaderManager
import exh.util.milliseconds
import exh.util.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.viewpager.pageSelections
import rx.Subscription
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryController(
    bundle: Bundle? = null,
    private val preferences: PreferencesHelper = Injekt.get()
) : SearchableNucleusController<LibraryControllerBinding, LibraryPresenter>(bundle),
    RootController,
    TabbedController,
    ActionMode.Callback,
    ChangeMangaCategoriesDialog.Listener,
    DeleteLibraryMangasDialog.Listener {

    /**
     * Position of the active category.
     */
    private var activeCategory: Int = preferences.lastUsedCategory().get()

    /**
     * Action mode for selections.
     */
    private var actionMode: ActionMode? = null

    /**
     * Currently selected mangas.
     */
    val selectedMangas = mutableSetOf<Manga>()

    /**
     * Relay to notify the UI of selection updates.
     */
    val selectionRelay: PublishRelay<LibrarySelectionEvent> = PublishRelay.create()

    /**
     * Relay to notify search query changes.
     */
    val searchRelay: BehaviorRelay<String> = BehaviorRelay.create()

    /**
     * Relay to notify the library's viewpager for updates.
     */
    val libraryMangaRelay: BehaviorRelay<LibraryMangaEvent> = BehaviorRelay.create()

    /**
     * Relay to notify the library's viewpager to select all manga
     */
    val selectAllRelay: PublishRelay<Int> = PublishRelay.create()

    /**
     * Relay to notify the library's viewpager to select the inverse
     */
    val selectInverseRelay: PublishRelay<Int> = PublishRelay.create()

    /**
     * Number of manga per row in grid mode.
     */
    var mangaPerRow = 0
        private set

    /**
     * Adapter of the view pager.
     */
    private var adapter: LibraryAdapter? = null

    /**
     * Sheet containing filter/sort/display items.
     */
    private var settingsSheet: LibrarySettingsSheet? = null

    private var tabsVisibilityRelay: BehaviorRelay<Boolean> = BehaviorRelay.create(false)

    private var mangaCountVisibilityRelay: BehaviorRelay<Boolean> = BehaviorRelay.create(false)

    private var tabsVisibilitySubscription: Subscription? = null

    private var mangaCountVisibilitySubscription: Subscription? = null

    // --> EH
    // Sync dialog
    private var favSyncDialog: AlertDialog? = null

    // Old sync status
    private var oldSyncStatus: FavoritesSyncStatus? = null

    // Favorites
    private var favoritesSyncJob: Job? = null
    val loaderManager = LoaderManager()
    // <-- EH

    init {
        setHasOptionsMenu(true)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    private var currentTitle: String? = null
        set(value) {
            if (field != value) {
                field = value
                setTitle()
            }
        }

    override fun getTitle(): String? {
        return currentTitle ?: resources?.getString(R.string.label_library)
    }

    private fun updateTitle() {
        val showCategoryTabs = preferences.categoryTabs().get()
        val currentCategory = adapter?.categories?.getOrNull(binding.libraryPager.currentItem)

        var title = if (showCategoryTabs) {
            resources?.getString(R.string.label_library)
        } else {
            currentCategory?.name
        }

        if (preferences.categoryNumberOfItems().get() && libraryMangaRelay.hasValue()) {
            libraryMangaRelay.value.mangas.let { mangaMap ->
                if (!showCategoryTabs || adapter?.categories?.size == 1) {
                    title += " (${mangaMap[currentCategory?.id]?.size ?: 0})"
                }
            }
        }

        currentTitle = title
    }

    override fun createPresenter(): LibraryPresenter {
        return LibraryPresenter()
    }

    override fun createBinding(inflater: LayoutInflater) = LibraryControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.actionToolbar.applyInsetter {
            type(navigationBars = true) {
                margin(bottom = true)
            }
        }

        adapter = LibraryAdapter(this)
        binding.libraryPager.adapter = adapter
        binding.libraryPager.pageSelections()
            .drop(1)
            .onEach {
                preferences.lastUsedCategory().set(it)
                activeCategory = it
                updateTitle()
            }
            .launchIn(viewScope)

        getColumnsPreferenceForCurrentOrientation().asImmediateFlow { mangaPerRow = it }
            .drop(1)
            // Set again the adapter to recalculate the covers height
            .onEach { reattachAdapter() }
            .launchIn(viewScope)

        if (selectedMangas.isNotEmpty()) {
            createActionModeIfNeeded()
        }

        settingsSheet = LibrarySettingsSheet(router) { group ->
            when (group) {
                is LibrarySettingsSheet.Filter.FilterGroup -> onFilterChanged()
                is LibrarySettingsSheet.Sort.SortGroup -> onSortChanged()
                is LibrarySettingsSheet.Display.DisplayGroup -> {
                    if (!preferences.categorisedDisplaySettings().get() || activeCategory == 0) {
                        // Reattach adapter when flow preference change
                        reattachAdapter()
                    }
                }
                is LibrarySettingsSheet.Display.BadgeGroup -> onBadgeSettingChanged()
                // SY -->
                is LibrarySettingsSheet.Display.ButtonsGroup -> onButtonSettingChanged()
                // SY <--
                is LibrarySettingsSheet.Display.TabsGroup -> onTabsSettingsChanged()
                // SY -->
                is LibrarySettingsSheet.Grouping.InternalGroup -> onGroupSettingChanged()
                // SY <--
            }
        }

        binding.btnGlobalSearch.clicks()
            .onEach {
                router.pushController(
                    GlobalSearchController(presenter.query).withFadeTransaction()
                )
            }
            .launchIn(viewScope)

        (activity as? MainActivity)?.fixViewToBottom(binding.actionToolbar)
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type.isEnter) {
            (activity as? MainActivity)?.binding?.tabs?.setupWithViewPager(binding.libraryPager)
            presenter.subscribeLibrary()
        }
    }

    override fun onDestroyView(view: View) {
        destroyActionModeIfNeeded()
        (activity as? MainActivity)?.clearFixViewToBottom(binding.actionToolbar)
        binding.actionToolbar.destroy()
        adapter?.onDestroy()
        adapter = null
        settingsSheet = null
        tabsVisibilitySubscription?.unsubscribe()
        tabsVisibilitySubscription = null
        super.onDestroyView(view)
    }

    override fun configureTabs(tabs: TabLayout) {
        with(tabs) {
            tabGravity = TabLayout.GRAVITY_START
            tabMode = TabLayout.MODE_SCROLLABLE
        }
        tabsVisibilitySubscription?.unsubscribe()
        tabsVisibilitySubscription = tabsVisibilityRelay.subscribe { visible ->
            tabs.isVisible = visible
        }
        mangaCountVisibilitySubscription?.unsubscribe()
        mangaCountVisibilitySubscription = mangaCountVisibilityRelay.subscribe {
            adapter?.notifyDataSetChanged()
        }
    }

    override fun cleanupTabs(tabs: TabLayout) {
        tabsVisibilitySubscription?.unsubscribe()
        tabsVisibilitySubscription = null
    }

    fun showSettingsSheet() {
        if (adapter?.categories?.isNotEmpty() == true) {
            adapter?.categories?.getOrNull(binding.libraryPager.currentItem)?.let { category ->
                settingsSheet?.show(category)
            }
        } else {
            settingsSheet?.show()
        }
    }

    fun onNextLibraryUpdate(categories: List<Category>, mangaMap: Map<Int, List<LibraryItem>>) {
        val view = view ?: return
        val adapter = adapter ?: return

        // Show empty view if needed
        if (mangaMap.isNotEmpty()) {
            binding.emptyView.hide()
        } else {
            binding.emptyView.show(
                R.string.information_empty_library,
                listOf(
                    EmptyView.Action(R.string.getting_started_guide, R.drawable.ic_help_24dp) {
                        activity?.openInBrowser("https://tachiyomi.org/help/guides/getting-started")
                    }
                ),
            )
        }

        // Get the current active category.
        val activeCat = if (adapter.categories.isNotEmpty()) {
            binding.libraryPager.currentItem
        } else {
            activeCategory
        }

        // Set the categories
        adapter.categories = categories
        adapter.itemsPerCategory = adapter.categories
            .map { (it.id ?: -1) to (mangaMap[it.id]?.size ?: 0) }
            .toMap()

        /*if (preferences.categorisedDisplaySettings().get()) {
            // Reattach adapter so it doesn't get de-synced
            reattachAdapter()
        }*/

        // Restore active category.
        binding.libraryPager.setCurrentItem(activeCat, false)

        // Trigger display of tabs
        onTabsSettingsChanged()

        // Delay the scroll position to allow the view to be properly measured.
        view.post {
            if (isAttached) {
                (activity as? MainActivity)?.binding?.tabs?.setScrollPosition(binding.libraryPager.currentItem, 0f, true)
            }
        }

        // Send the manga map to child fragments after the adapter is updated.
        libraryMangaRelay.call(LibraryMangaEvent(mangaMap))

        // Finally update the title
        updateTitle()
    }

    /**
     * Returns a preference for the number of manga per row based on the current orientation.
     *
     * @return the preference.
     */
    private fun getColumnsPreferenceForCurrentOrientation(): Preference<Int> {
        return if (resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT) {
            preferences.portraitColumns()
        } else {
            preferences.landscapeColumns()
        }
    }

    private fun onFilterChanged() {
        presenter.requestFilterUpdate()
        activity?.invalidateOptionsMenu()
    }

    private fun onBadgeSettingChanged() {
        presenter.requestBadgesUpdate()
    }

    // SY -->
    private fun onButtonSettingChanged() {
        presenter.requestButtonsUpdate()
    }

    private fun onGroupSettingChanged() {
        presenter.requestGroupsUpdate()
    }
    // SY <--

    private fun onTabsSettingsChanged() {
        tabsVisibilityRelay.call(preferences.categoryTabs().get() && adapter?.categories?.size ?: 0 > 1)
        mangaCountVisibilityRelay.call(preferences.categoryNumberOfItems().get())
        updateTitle()
    }

    /**
     * Called when the sorting mode is changed.
     */
    private fun onSortChanged() {
        // SY -->
        activity?.invalidateOptionsMenu()
        // SY <--
        presenter.requestSortUpdate()
    }

    /**
     * Reattaches the adapter to the view pager to recreate fragments
     */
    private fun reattachAdapter() {
        val adapter = adapter ?: return

        val position = binding.libraryPager.currentItem

        adapter.recycle = false
        binding.libraryPager.adapter = adapter
        binding.libraryPager.currentItem = position
        adapter.recycle = true
    }

    /**
     * Creates the action mode if it's not created already.
     */
    fun createActionModeIfNeeded() {
        if (actionMode == null) {
            actionMode = (activity as AppCompatActivity).startSupportActionMode(this)
            binding.actionToolbar.show(
                actionMode!!,
                R.menu.library_selection
            ) { onActionItemClicked(it!!) }
            (activity as? MainActivity)?.showBottomNav(visible = false, expand = true)
        }
    }

    /**
     * Destroys the action mode.
     */
    private fun destroyActionModeIfNeeded() {
        actionMode?.finish()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        createOptionsMenu(menu, inflater, R.menu.library, R.id.action_search)
        // Mutate the filter icon because it needs to be tinted and the resource is shared.
        menu.findItem(R.id.action_filter).icon.mutate()

        // SY -->
        menu.findItem(R.id.action_sync_favorites).isVisible =
            preferences.isHentaiEnabled().get() && preferences.isSyncEHEnabled().get()
        // SY <--
    }

    fun search(query: String) {
        presenter.query = query
    }

    private fun performSearch() {
        searchRelay.call(presenter.query)
        if (presenter.query.isNotEmpty()) {
            binding.btnGlobalSearch.isVisible = true
            binding.btnGlobalSearch.text =
                resources?.getString(R.string.action_global_search_query, presenter.query)
        } else {
            binding.btnGlobalSearch.isVisible = false
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val settingsSheet = settingsSheet ?: return

        val filterItem = menu.findItem(R.id.action_filter)

        // Tint icon if there's a filter active
        if (settingsSheet.filters.hasActiveFilters()) {
            val filterColor = activity!!.getResourceColor(R.attr.colorFilterActive)
            filterItem.icon.setTint(filterColor)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> expandActionViewFromInteraction = true
            R.id.action_filter -> showSettingsSheet()
            R.id.action_update_library -> {
                activity?.let {
                    if (LibraryUpdateService.start(it)) {
                        it.toast(R.string.updating_library)
                    }
                }
            }
            // SY -->
            R.id.action_sync_favorites -> {
                if (preferences.exhShowSyncIntro().get()) {
                    activity?.let { FavoritesIntroDialog().show(it) }
                } else {
                    presenter.favoritesSync.runSync()
                }
            }
            // SY <--
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Invalidates the action mode, forcing it to refresh its content.
     */
    fun invalidateActionMode() {
        actionMode?.invalidate()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.generic_selection, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = selectedMangas.size
        if (count == 0) {
            // Destroy action mode if there are no items selected.
            destroyActionModeIfNeeded()
        } else {
            mode.title = count.toString()

            binding.actionToolbar.findItem(R.id.action_download_unread)?.isVisible = selectedMangas.any { it.source != LocalSource.ID }

            // SY -->
            binding.actionToolbar.findItem(R.id.action_clean)?.isVisible = selectedMangas.any {
                it.isEhBasedManga() ||
                    it.source in nHentaiSourceIds ||
                    it.source == PERV_EDEN_EN_SOURCE_ID ||
                    it.source == PERV_EDEN_IT_SOURCE_ID
            }
            binding.actionToolbar.findItem(R.id.action_push_to_mdlist)?.isVisible = selectedMangas.any {
                it.source in mangaDexSourceIds
            }
            // SY <--
        }
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return onActionItemClicked(item)
    }

    private fun onActionItemClicked(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_move_to_category -> showChangeMangaCategoriesDialog()
            R.id.action_download_unread -> downloadUnreadChapters()
            R.id.action_mark_as_read -> markReadStatus(true)
            R.id.action_mark_as_unread -> markReadStatus(false)
            R.id.action_delete -> showDeleteMangaDialog()
            R.id.action_select_all -> selectAllCategoryManga()
            R.id.action_select_inverse -> selectInverseCategoryManga()
            // SY -->
            R.id.action_migrate -> {
                val skipPre = preferences.skipPreMigration().get()
                val selectedMangaIds = selectedMangas.filterNot { it.source == MERGED_SOURCE_ID }.mapNotNull { it.id }
                clearSelection()
                if (selectedMangaIds.isNotEmpty()) {
                    PreMigrationController.navigateToMigration(skipPre, router, selectedMangaIds)
                } else {
                    activity?.toast(R.string.no_valid_manga)
                }
            }
            R.id.action_clean -> cleanTitles()
            R.id.action_push_to_mdlist -> pushToMdList()
            // SY <--
            else -> return false
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        // Clear all the manga selections and notify child views.
        selectedMangas.clear()
        selectionRelay.call(LibrarySelectionEvent.Cleared())

        binding.actionToolbar.hide()
        (activity as? MainActivity)?.showBottomNav(visible = true, expand = true)

        actionMode = null
    }

    fun openManga(manga: Manga) {
        // Notify the presenter a manga is being opened.
        presenter.onOpenManga()

        router.pushController(MangaController(manga).withFadeTransaction())
    }

    /**
     * Sets the selection for a given manga.
     *
     * @param manga the manga whose selection has changed.
     * @param selected whether it's now selected or not.
     */
    fun setSelection(manga: Manga, selected: Boolean) {
        if (selected) {
            if (selectedMangas.add(manga)) {
                selectionRelay.call(LibrarySelectionEvent.Selected(manga))
            }
        } else {
            if (selectedMangas.remove(manga)) {
                selectionRelay.call(LibrarySelectionEvent.Unselected(manga))
            }
        }
    }

    /**
     * Toggles the current selection state for a given manga.
     *
     * @param manga the manga whose selection to change.
     */
    fun toggleSelection(manga: Manga) {
        if (selectedMangas.add(manga)) {
            selectionRelay.call(LibrarySelectionEvent.Selected(manga))
        } else if (selectedMangas.remove(manga)) {
            selectionRelay.call(LibrarySelectionEvent.Unselected(manga))
        }
    }

    /**
     * Clear all of the manga currently selected, and
     * invalidate the action mode to revert the top toolbar
     */
    fun clearSelection() {
        selectedMangas.clear()
        selectionRelay.call(LibrarySelectionEvent.Cleared())
        invalidateActionMode()
    }

    /**
     * Move the selected manga to a list of categories.
     */
    private fun showChangeMangaCategoriesDialog() {
        // Create a copy of selected manga
        val mangas = selectedMangas.toList()

        // Hide the default category because it has a different behavior than the ones from db.
        val categories = presenter.categories.filter { it.id != 0 }

        // Get indexes of the common categories to preselect.
        val common = presenter.getCommonCategories(mangas)
        // Get indexes of the mix categories to preselect.
        val mix = presenter.getMixCategories(mangas)
        var preselected = categories.map {
            when (it) {
                in common -> QuadStateTextView.State.CHECKED.ordinal
                in mix -> QuadStateTextView.State.INDETERMINATE.ordinal
                else -> QuadStateTextView.State.UNCHECKED.ordinal
            }
        }.toTypedArray()
        ChangeMangaCategoriesDialog(this, mangas, categories, preselected)
            .showDialog(router)
    }

    private fun downloadUnreadChapters() {
        val mangas = selectedMangas.toList()
        presenter.downloadUnreadChapters(mangas)
        destroyActionModeIfNeeded()
    }

    private fun markReadStatus(read: Boolean) {
        val mangas = selectedMangas.toList()
        presenter.markReadStatus(mangas, read)
        destroyActionModeIfNeeded()
    }

    private fun showDeleteMangaDialog() {
        DeleteLibraryMangasDialog(this, selectedMangas.toList()).showDialog(router)
    }

    // SY -->
    private fun cleanTitles() {
        val mangas = selectedMangas.filter {
            it.isEhBasedManga() ||
                it.source in nHentaiSourceIds ||
                it.source == PERV_EDEN_EN_SOURCE_ID ||
                it.source == PERV_EDEN_IT_SOURCE_ID
        }
        presenter.cleanTitles(mangas)
        destroyActionModeIfNeeded()
    }

    private fun pushToMdList() {
        val mangas = selectedMangas.filter {
            it.source in mangaDexSourceIds
        }
        presenter.syncMangaToDex(mangas)
    }
    // SY <--

    override fun updateCategoriesForMangas(mangas: List<Manga>, addCategories: List<Category>, removeCategories: List<Category>) {
        presenter.updateMangasToCategories(mangas, addCategories, removeCategories)
        destroyActionModeIfNeeded()
    }

    override fun deleteMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        presenter.removeMangas(mangas, deleteFromLibrary, deleteChapters)
        destroyActionModeIfNeeded()
    }

    // SY -->
    override fun onAttach(view: View) {
        super.onAttach(view)

        // --> EXH
        cleanupSyncState()
        favoritesSyncJob =
            presenter.favoritesSync.status
                .sample(100.milliseconds)
                .mapLatest {
                    updateSyncStatus(it)
                }
                .launchIn(viewScope)
        // <-- EXH
    }

    override fun onDetach(view: View) {
        super.onDetach(view)

        // EXH
        cleanupSyncState()
    }
    // SY <--

    private fun selectAllCategoryManga() {
        adapter?.categories?.getOrNull(binding.libraryPager.currentItem)?.id?.let {
            selectAllRelay.call(it)
        }
    }

    private fun selectInverseCategoryManga() {
        adapter?.categories?.getOrNull(binding.libraryPager.currentItem)?.id?.let {
            selectInverseRelay.call(it)
        }
    }

    override fun onSearchViewQueryTextChange(newText: String?) {
        // Ignore events if this controller isn't at the top to avoid query being reset
        if (router.backstack.lastOrNull()?.controller == this) {
            presenter.query = newText ?: ""
            performSearch()
        }
    }

    // --> EXH
    private fun cleanupSyncState() {
        favoritesSyncJob?.cancel()
        favoritesSyncJob = null
        // Close sync status
        favSyncDialog?.dismiss()
        favSyncDialog = null
        oldSyncStatus = null
        // Clear flags
        releaseSyncLocks()
    }

    private fun buildDialog() = activity?.let {
        MaterialAlertDialogBuilder(it)
    }

    private fun showSyncProgressDialog() {
        favSyncDialog?.dismiss()
        favSyncDialog = buildDialog()
            ?.setTitle(R.string.favorites_syncing)
            ?.setMessage("")
            ?.setCancelable(false)
            ?.create()
        favSyncDialog?.show()
    }

    private fun takeSyncLocks() {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun releaseSyncLocks() {
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private suspend fun updateSyncStatus(status: FavoritesSyncStatus) {
        when (status) {
            is FavoritesSyncStatus.Idle -> {
                releaseSyncLocks()

                favSyncDialog?.dismiss()
                favSyncDialog = null
            }
            is FavoritesSyncStatus.BadLibraryState.MangaInMultipleCategories -> {
                releaseSyncLocks()

                favSyncDialog?.dismiss()
                favSyncDialog = buildDialog()
                    ?.setTitle(R.string.favorites_sync_error)
                    ?.setMessage(activity!!.getString(R.string.favorites_sync_bad_library_state, status.message))
                    ?.setCancelable(false)
                    ?.setPositiveButton(R.string.show_gallery) { _, _ ->
                        openManga(status.manga)
                        presenter.favoritesSync.status.value = FavoritesSyncStatus.Idle(activity!!)
                    }
                    ?.setNegativeButton(android.R.string.ok) { _, _ ->
                        presenter.favoritesSync.status.value = FavoritesSyncStatus.Idle(activity!!)
                    }
                    ?.create()
                favSyncDialog?.show()
            }
            is FavoritesSyncStatus.Error -> {
                releaseSyncLocks()

                favSyncDialog?.dismiss()
                favSyncDialog = buildDialog()
                    ?.setTitle(R.string.favorites_sync_error)
                    ?.setMessage(activity!!.getString(R.string.favorites_sync_error_string, status.message))
                    ?.setCancelable(false)
                    ?.setPositiveButton(android.R.string.ok) { _, _ ->
                        presenter.favoritesSync.status.value = FavoritesSyncStatus.Idle(activity!!)
                    }
                    ?.create()
                favSyncDialog?.show()
            }
            is FavoritesSyncStatus.CompleteWithErrors -> {
                releaseSyncLocks()

                favSyncDialog?.dismiss()
                favSyncDialog = buildDialog()
                    ?.setTitle(R.string.favorites_sync_done_errors)
                    ?.setMessage(activity!!.getString(R.string.favorites_sync_done_errors_message, status.message))
                    ?.setCancelable(false)
                    ?.setPositiveButton(android.R.string.ok) { _, _ ->
                        presenter.favoritesSync.status.value = FavoritesSyncStatus.Idle(activity!!)
                    }
                    ?.create()
                favSyncDialog?.show()
            }
            is FavoritesSyncStatus.Processing,
            is FavoritesSyncStatus.Initializing -> {
                takeSyncLocks()

                if (favSyncDialog == null || (
                    oldSyncStatus != null &&
                        oldSyncStatus !is FavoritesSyncStatus.Initializing &&
                        oldSyncStatus !is FavoritesSyncStatus.Processing
                    )
                ) {
                    showSyncProgressDialog()
                }

                favSyncDialog?.setMessage(status.message)
            }
        }
        oldSyncStatus = status
        if (status is FavoritesSyncStatus.Processing && status.delayedMessage != null) {
            delay(5.seconds)
            favSyncDialog?.setMessage(status.delayedMessage)
        }
    }

    fun startReading(manga: Manga, adapter: LibraryCategoryAdapter) {
        if (adapter.mode == SelectableAdapter.Mode.MULTI) {
            toggleSelection(manga)
            return
        }
        val activity = activity ?: return
        val chapter = presenter.getFirstUnread(manga) ?: return
        val intent = ReaderActivity.newIntent(activity, manga, chapter)
        destroyActionModeIfNeeded()
        startActivity(intent)
    }
    // <-- EXH
}
