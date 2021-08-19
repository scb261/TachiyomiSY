package eu.kanade.tachiyomi.ui.main

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceDialogController
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.Router
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.navigation.NavigationBarView
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.databinding.MainActivityBinding
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.ui.base.activity.BaseViewBindingActivity
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.NoAppBarElevationController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.TabbedController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.BrowseController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.download.DownloadController
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.more.MoreController
import eu.kanade.tachiyomi.ui.more.NewUpdateDialogController
import eu.kanade.tachiyomi.ui.recent.history.HistoryController
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesController
import eu.kanade.tachiyomi.ui.setting.SettingsMainController
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setNavigationBarTransparentCompat
import eu.kanade.tachiyomi.widget.HideBottomNavigationOnScrollBehavior
import exh.EXHMigrations
import exh.eh.EHentaiUpdateWorker
import exh.source.BlacklistedSources
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.uconfig.WarnConfigureDialogController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.util.Date
import java.util.LinkedList
import java.util.concurrent.TimeUnit

class MainActivity : BaseViewBindingActivity<MainActivityBinding>() {

    private lateinit var router: Router

    private val startScreenId by lazy {
        when (preferences.startScreen()) {
            2 -> R.id.nav_history
            3 -> R.id.nav_updates
            4 -> R.id.nav_browse
            else -> R.id.nav_library
        }
    }

    private var bottomNavAnimator: ViewHeightAnimator? = null

    private var isConfirmingExit: Boolean = false
    private var isHandlingShortcut: Boolean = false

    private var fixedViewsToBottom = mutableMapOf<View, AppBarLayout.OnOffsetChangedListener>()

    /**
     * App bar lift state for backstack
     */
    private val backstackLiftState = mutableMapOf<String, Boolean>()

    // SY -->
    // Idle-until-urgent
    private var firstPaint = false
    private val iuuQueue = LinkedList<() -> Unit>()

    private fun initWhenIdle(task: () -> Unit) {
        // Avoid sync issues by enforcing main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalStateException("Can only be called on main thread!")
        }

        if (firstPaint) {
            task()
        } else {
            iuuQueue += task
        }
    }
    // SY <--

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val didMigration = if (savedInstanceState == null) EXHMigrations.upgrade(preferences) else false

        binding = MainActivityBinding.inflate(layoutInflater)

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Draw edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.fabLayout.rootFab.applyInsetter {
            type(navigationBars = true) {
                margin()
            }
        }
        binding.bottomNav?.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        // Make sure navigation bar is on bottom before we modify it
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            if (insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom > 0) {
                window.setNavigationBarTransparentCompat(this)
            }
            insets
        }

        if (binding.bottomNav != null) {
            bottomNavAnimator = ViewHeightAnimator(binding.bottomNav!!)

            // Set behavior of bottom nav
            preferences.hideBottomBarOnScroll()
                .asImmediateFlow { setBottomNavBehaviorOnScroll() }
                .launchIn(lifecycleScope)
        }

        if (binding.sideNav != null) {
            preferences.sideNavIconAlignment()
                .asImmediateFlow {
                    binding.sideNav?.menuGravity = when (it) {
                        1 -> Gravity.CENTER
                        2 -> Gravity.BOTTOM
                        else -> Gravity.TOP
                    }
                }
                .launchIn(lifecycleScope)
        }

        nav.setOnItemSelectedListener { item ->
            val id = item.itemId

            val currentRoot = router.backstack.firstOrNull()
            if (currentRoot?.tag()?.toIntOrNull() != id) {
                when (id) {
                    R.id.nav_library -> setRoot(LibraryController(), id)
                    R.id.nav_updates -> setRoot(UpdatesController(), id)
                    R.id.nav_history -> setRoot(HistoryController(), id)
                    R.id.nav_browse -> setRoot(BrowseController(), id)
                    R.id.nav_more -> setRoot(MoreController(), id)
                }
            } else if (!isHandlingShortcut) {
                when (id) {
                    R.id.nav_library -> {
                        val controller = router.getControllerWithTag(id.toString()) as? LibraryController
                        controller?.showSettingsSheet()
                    }
                    R.id.nav_updates -> {
                        if (router.backstackSize == 1) {
                            router.pushController(DownloadController().withFadeTransaction())
                        }
                    }
                    R.id.nav_more -> {
                        if (router.backstackSize == 1) {
                            router.pushController(SettingsMainController().withFadeTransaction())
                        }
                    }
                }
            }
            true
        }

        val container: ViewGroup = binding.controllerContainer
        router = Conductor.attachRouter(this, container, savedInstanceState)
        if (!router.hasRootController()) {
            // Set start screen
            if (!handleIntentAction(intent)) {
                setSelectedNavItem(startScreenId)
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        router.addChangeListener(
            object : ControllerChangeHandler.ControllerChangeListener {
                override fun onChangeStarted(
                    to: Controller?,
                    from: Controller?,
                    isPush: Boolean,
                    container: ViewGroup,
                    handler: ControllerChangeHandler
                ) {
                    syncActivityViewWithController(to, from, isPush)
                }

                override fun onChangeCompleted(
                    to: Controller?,
                    from: Controller?,
                    isPush: Boolean,
                    container: ViewGroup,
                    handler: ControllerChangeHandler
                ) {
                }
            }
        )

        syncActivityViewWithController(router.backstack.lastOrNull()?.controller)

        if (savedInstanceState == null) {
            // Reset Incognito Mode on relaunch
            preferences.incognitoMode().set(false)

            // Show changelog prompt on update
            if (didMigration) {
                WhatsNewDialogController().showDialog(router)
            }
            // EXH <--

            // SY -->
            initWhenIdle {
                // Upload settings
                if (preferences.enableExhentai().get() &&
                    preferences.exhShowSettingsUploadWarning().get()
                ) {
                    WarnConfigureDialogController.uploadSettings(router)
                }
                // Scheduler uploader job if required

                EHentaiUpdateWorker.scheduleBackground(this)
            }
            // SY <--
        }
        // SY -->
        if (!preferences.isHentaiEnabled().get()) {
            BlacklistedSources.HIDDEN_SOURCES += EH_SOURCE_ID
            BlacklistedSources.HIDDEN_SOURCES += EXH_SOURCE_ID
        }
        // SY -->

        preferences.extensionUpdatesCount()
            .asImmediateFlow { setExtensionsBadge() }
            .launchIn(lifecycleScope)

        preferences.downloadedOnly()
            .asImmediateFlow { binding.downloadedOnly.isVisible = it }
            .launchIn(lifecycleScope)

        binding.incognitoMode.isVisible = preferences.incognitoMode().get()
        preferences.incognitoMode().asFlow()
            .drop(1)
            .onEach {
                binding.incognitoMode.isVisible = it

                // Close BrowseSourceController and its MangaController child when incognito mode is disabled
                if (!it) {
                    val fg = router.backstack.last().controller
                    if (fg is BrowseSourceController || fg is MangaController && fg.fromSource) {
                        router.popToRoot()
                    }
                }
            }
            .launchIn(lifecycleScope)

        // SY -->
        preferences.bottomBarLabels()
            .asImmediateFlow { setNavLabelVisibility() }
            .launchIn(lifecycleScope)
        // SY <--
    }

    override fun onNewIntent(intent: Intent) {
        if (!handleIntentAction(intent)) {
            super.onNewIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        checkForExtensionUpdates()
        if (BuildConfig.INCLUDE_UPDATER) {
            checkForAppUpdates()
        }
    }

    private fun checkForAppUpdates() {
        // Limit checks to once a day at most
        if (Date().time < preferences.lastAppCheck().get() + TimeUnit.DAYS.toMillis(1)) {
            return
        }

        lifecycleScope.launchIO {
            try {
                val result = AppUpdateChecker().checkForUpdate()
                if (result is AppUpdateResult.NewUpdate) {
                    NewUpdateDialogController(result).showDialog(router)
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private fun checkForExtensionUpdates() {
        // Limit checks to once a day at most
        if (Date().time < preferences.lastExtCheck().get() + TimeUnit.DAYS.toMillis(1)) {
            return
        }

        lifecycleScope.launchIO {
            try {
                val pendingUpdates = ExtensionGithubApi().checkForUpdates(this@MainActivity)
                preferences.extensionUpdatesCount().set(pendingUpdates.size)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private fun setExtensionsBadge() {
        val updates = preferences.extensionUpdatesCount().get()
        if (updates > 0) {
            nav.getOrCreateBadge(R.id.nav_browse).number = updates
        } else {
            nav.removeBadge(R.id.nav_browse)
        }
    }

    private fun handleIntentAction(intent: Intent): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(applicationContext, notificationId, intent.getIntExtra("groupId", 0))
        }

        isHandlingShortcut = true

        when (intent.action) {
            SHORTCUT_LIBRARY -> setSelectedNavItem(R.id.nav_library)
            SHORTCUT_RECENTLY_UPDATED -> setSelectedNavItem(R.id.nav_updates)
            SHORTCUT_RECENTLY_READ -> setSelectedNavItem(R.id.nav_history)
            SHORTCUT_CATALOGUES -> setSelectedNavItem(R.id.nav_browse)
            SHORTCUT_EXTENSIONS -> {
                if (router.backstackSize > 1) {
                    router.popToRoot()
                }
                setSelectedNavItem(R.id.nav_browse)
                router.pushController(BrowseController(toExtensions = true).withFadeTransaction())
            }
            SHORTCUT_MANGA -> {
                val extras = intent.extras ?: return false
                if (router.backstackSize > 1) {
                    router.popToRoot()
                }
                setSelectedNavItem(R.id.nav_library)
                router.pushController(MangaController(extras).withFadeTransaction())
            }
            SHORTCUT_DOWNLOADS -> {
                if (router.backstackSize > 1) {
                    router.popToRoot()
                }
                setSelectedNavItem(R.id.nav_more)
                router.pushController(DownloadController().withFadeTransaction())
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                // Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (query != null && query.isNotEmpty()) {
                    if (router.backstackSize > 1) {
                        router.popToRoot()
                    }
                    router.pushController(GlobalSearchController(query).withFadeTransaction())
                }
            }
            INTENT_SEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (query != null && query.isNotEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    if (router.backstackSize > 1) {
                        router.popToRoot()
                    }
                    router.pushController(GlobalSearchController(query, filter).withFadeTransaction())
                }
            }
            else -> {
                isHandlingShortcut = false
                return false
            }
        }

        isHandlingShortcut = false
        return true
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun onDestroy() {
        super.onDestroy()

        // Binding sometimes isn't actually instantiated yet somehow
        nav?.setOnItemSelectedListener(null)
        binding?.toolbar.setNavigationOnClickListener(null)
    }

    override fun onBackPressed() {
        val backstackSize = router.backstackSize
        if (backstackSize == 1 && router.getControllerWithTag("$startScreenId") == null) {
            // Return to start screen
            setSelectedNavItem(startScreenId)
        } else if (shouldHandleExitConfirmation()) {
            // Exit confirmation (resets after 2 seconds)
            lifecycleScope.launchUI { resetExitConfirmation() }
        } else if (backstackSize == 1 || !router.handleBack()) {
            // Regular back
            super.onBackPressed()
        }
    }

    private suspend fun resetExitConfirmation() {
        isConfirmingExit = true
        val toast = toast(R.string.confirm_exit, Toast.LENGTH_LONG)
        delay(2000)
        toast.cancel()
        isConfirmingExit = false
    }

    private fun shouldHandleExitConfirmation(): Boolean {
        return router.backstackSize == 1 &&
            router.getControllerWithTag("$startScreenId") != null &&
            preferences.confirmExit() &&
            !isConfirmingExit
    }

    fun setSelectedNavItem(itemId: Int) {
        if (!isFinishing) {
            nav.selectedItemId = itemId
        }
    }

    private fun setRoot(controller: Controller, id: Int) {
        router.setRoot(controller.withFadeTransaction().tag(id.toString()))
    }

    private fun syncActivityViewWithController(to: Controller?, from: Controller? = null, isPush: Boolean = true) {
        if (from is DialogController || to is DialogController) {
            return
        }
        if (from is PreferenceDialogController || to is PreferenceDialogController) {
            return
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(router.backstackSize != 1)

        // Always show appbar again when changing controllers
        binding.appbar.setExpanded(true)

        if ((from == null || from is RootController) && to !is RootController) {
            showNav(visible = false, expand = true)
        }
        if (to is RootController) {
            // Always show bottom nav again when returning to a RootController
            showNav(visible = true, expand = from !is RootController)
        }

        if (from is TabbedController) {
            from.cleanupTabs(binding.tabs)
        }
        if (to is TabbedController) {
            to.configureTabs(binding.tabs)
        } else {
            binding.tabs.setupWithViewPager(null)
        }
        binding.tabs.isVisible = to is TabbedController

        if (from is FabController) {
            binding.fabLayout.rootFab.isVisible = false
            from.cleanupFab(binding.fabLayout.rootFab)
        }
        if (to is FabController) {
            binding.fabLayout.rootFab.isVisible = true
            to.configureFab(binding.fabLayout.rootFab)
        }

        if (!isTablet()) {
            // Save lift state
            if (isPush) {
                if (router.backstackSize > 1) {
                    // Save lift state
                    from?.let {
                        backstackLiftState[it.instanceId] = binding.appbar.isLifted
                    }
                } else {
                    backstackLiftState.clear()
                }
                binding.appbar.isLifted = false
            } else {
                to?.let {
                    binding.appbar.isLifted = backstackLiftState.getOrElse(it.instanceId) { false }
                }
                from?.let {
                    backstackLiftState.remove(it.instanceId)
                }
            }

            binding.root.isLiftAppBarOnScroll = to !is NoAppBarElevationController

            binding.appbar.isTransparentWhenNotLifted = to is MangaController &&
                preferences.appTheme().get() != PreferenceValues.AppTheme.BLUE
            binding.controllerContainer.overlapHeader = to is MangaController
        }
    }

    private fun showNav(visible: Boolean, expand: Boolean = false) {
        showBottomNav(visible, expand)
        showSideNav(visible)
    }

    // Also used from some controllers to swap bottom nav with action toolbar
    fun showBottomNav(visible: Boolean, expand: Boolean = false) {
        if (visible) {
            binding.bottomNav?.translationY = 0F
            if (expand) {
                bottomNavAnimator?.expand()
                // SY -->
                binding.bottomNav?.menu?.let { updateNavMenu(it) }
                // SY <--
            }
        } else {
            bottomNavAnimator?.collapse()
        }
    }

    private fun showSideNav(visible: Boolean) {
        binding.sideNav?.let {
            it.isVisible = visible
            // SY -->
            updateNavMenu(it.menu)
            // SY <--
        }
    }

    // SY -->
    private fun updateNavMenu(menu: Menu) {
        menu.findItem(R.id.nav_updates).isVisible = preferences.showNavUpdates().get()
        menu.findItem(R.id.nav_history).isVisible = preferences.showNavHistory().get()
    }
    // SY <--

    /**
     * Used to manually offset a view within the activity's child views that might be cut off due to
     * the collapsing AppBarLayout.
     */
    fun fixViewToBottom(view: View) {
        val listener = AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val maxAbsOffset = appBarLayout.measuredHeight - binding.tabs.measuredHeight
            view.translationY = -maxAbsOffset - verticalOffset.toFloat() + appBarLayout.paddingTop
        }
        binding.appbar.addOnOffsetChangedListener(listener)
        fixedViewsToBottom[view] = listener
    }

    fun clearFixViewToBottom(view: View) {
        val listener = fixedViewsToBottom.remove(view)
        binding.appbar.removeOnOffsetChangedListener(listener)
    }

    private fun setBottomNavBehaviorOnScroll() {
        showNav(visible = true)

        binding.bottomNav?.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = when {
                preferences.hideBottomBarOnScroll().get() -> HideBottomNavigationOnScrollBehavior()
                else -> null
            }
        }
        binding.bottomNav?.translationY = 0F
    }

    private val nav: NavigationBarView
        get() = binding.bottomNav ?: binding.sideNav!!

    private fun setNavLabelVisibility() {
        if (preferences.bottomBarLabels().get()) {
            nav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
        } else {
            nav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_SELECTED
        }
    }

    companion object {
        // Shortcut actions
        const val SHORTCUT_LIBRARY = "eu.kanade.tachiyomi.SHOW_LIBRARY"
        const val SHORTCUT_RECENTLY_UPDATED = "eu.kanade.tachiyomi.SHOW_RECENTLY_UPDATED"
        const val SHORTCUT_RECENTLY_READ = "eu.kanade.tachiyomi.SHOW_RECENTLY_READ"
        const val SHORTCUT_CATALOGUES = "eu.kanade.tachiyomi.SHOW_CATALOGUES"
        const val SHORTCUT_DOWNLOADS = "eu.kanade.tachiyomi.SHOW_DOWNLOADS"
        const val SHORTCUT_MANGA = "eu.kanade.tachiyomi.SHOW_MANGA"
        const val SHORTCUT_EXTENSIONS = "eu.kanade.tachiyomi.EXTENSIONS"

        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"
    }
}
