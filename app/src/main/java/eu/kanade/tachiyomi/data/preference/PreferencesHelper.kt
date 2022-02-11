package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.tfcporciuncula.flow.FlowSharedPreferences
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferenceValues.ThemeMode.dark
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrationSourcesController
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

fun <T> Preference<T>.asImmediateFlow(block: (T) -> Unit): Flow<T> {
    block(get())
    return asFlow()
        .onEach { block(it) }
}

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

fun Preference<Boolean>.toggle(): Boolean {
    set(!get())
    return get()
}

class PreferencesHelper(val context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val flowPrefs = FlowSharedPreferences(prefs)

    private val defaultDownloadsDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "downloads"
    ).toUri()

    private val defaultBackupDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "backup"
    ).toUri()

    fun startScreen() = prefs.getInt(Keys.startScreen, 1)

    fun confirmExit() = prefs.getBoolean(Keys.confirmExit, false)

    fun hideBottomBarOnScroll() = flowPrefs.getBoolean(Keys.hideBottomBarOnScroll, true)

    fun sideNavIconAlignment() = flowPrefs.getInt(Keys.sideNavIconAlignment, 0)

    fun useAuthenticator() = flowPrefs.getBoolean(Keys.useAuthenticator, false)

    fun lockAppAfter() = flowPrefs.getInt(Keys.lockAppAfter, 0)

    fun lastAppUnlock() = flowPrefs.getLong(Keys.lastAppUnlock, 0)

    fun secureScreen() = flowPrefs.getBoolean(Keys.secureScreen, false)

    fun hideNotificationContent() = prefs.getBoolean(Keys.hideNotificationContent, false)

    fun autoUpdateMetadata() = prefs.getBoolean(Keys.autoUpdateMetadata, false)

    fun autoUpdateTrackers() = prefs.getBoolean(Keys.autoUpdateTrackers, false)

    fun themeMode() = flowPrefs.getEnum(Keys.themeMode, dark)

    fun appTheme() = flowPrefs.getEnum(Keys.appTheme, Values.AppTheme.DEFAULT)

    fun themeDarkAmoled() = flowPrefs.getBoolean(Keys.themeDarkAmoled, false)

    fun pageTransitionsPager() = flowPrefs.getBoolean(Keys.enableTransitionsPager, true)

    fun pageTransitionsWebtoon() = flowPrefs.getBoolean(Keys.enableTransitionsWebtoon, true)

    fun doubleTapAnimSpeed() = flowPrefs.getInt(Keys.doubleTapAnimationSpeed, 500)

    fun showPageNumber() = flowPrefs.getBoolean(Keys.showPageNumber, true)

    fun dualPageSplitPaged() = flowPrefs.getBoolean(Keys.dualPageSplitPaged, false)

    fun dualPageSplitWebtoon() = flowPrefs.getBoolean(Keys.dualPageSplitWebtoon, false)

    fun dualPageInvertPaged() = flowPrefs.getBoolean(Keys.dualPageInvertPaged, false)

    fun dualPageInvertWebtoon() = flowPrefs.getBoolean(Keys.dualPageInvertWebtoon, false)

    fun showReadingMode() = prefs.getBoolean(Keys.showReadingMode, true)

    fun trueColor() = flowPrefs.getBoolean(Keys.trueColor, false)

    fun fullscreen() = flowPrefs.getBoolean(Keys.fullscreen, true)

    fun cutoutShort() = flowPrefs.getBoolean(Keys.cutoutShort, true)

    fun keepScreenOn() = flowPrefs.getBoolean(Keys.keepScreenOn, true)

    fun customBrightness() = flowPrefs.getBoolean(Keys.customBrightness, false)

    fun customBrightnessValue() = flowPrefs.getInt(Keys.customBrightnessValue, 0)

    fun colorFilter() = flowPrefs.getBoolean(Keys.colorFilter, false)

    fun colorFilterValue() = flowPrefs.getInt(Keys.colorFilterValue, 0)

    fun colorFilterMode() = flowPrefs.getInt(Keys.colorFilterMode, 0)

    fun grayscale() = flowPrefs.getBoolean(Keys.grayscale, false)

    fun invertedColors() = flowPrefs.getBoolean(Keys.invertedColors, false)

    fun defaultReadingMode() = prefs.getInt(Keys.defaultReadingMode, ReadingModeType.RIGHT_TO_LEFT.flagValue)

    fun defaultOrientationType() = prefs.getInt(Keys.defaultOrientationType, OrientationType.FREE.flagValue)

    fun imageScaleType() = flowPrefs.getInt(Keys.imageScaleType, 1)

    fun zoomStart() = flowPrefs.getInt(Keys.zoomStart, 1)

    fun readerTheme() = flowPrefs.getInt(Keys.readerTheme, 3)

    fun alwaysShowChapterTransition() = flowPrefs.getBoolean(Keys.alwaysShowChapterTransition, true)

    fun cropBorders() = flowPrefs.getBoolean(Keys.cropBorders, false)

    fun cropBordersWebtoon() = flowPrefs.getBoolean(Keys.cropBordersWebtoon, false)

    fun webtoonSidePadding() = flowPrefs.getInt(Keys.webtoonSidePadding, 0)

    fun readWithTapping() = flowPrefs.getBoolean(Keys.readWithTapping, true)

    fun pagerNavInverted() = flowPrefs.getEnum(Keys.pagerNavInverted, Values.TappingInvertMode.NONE)

    fun webtoonNavInverted() = flowPrefs.getEnum(Keys.webtoonNavInverted, Values.TappingInvertMode.NONE)

    fun readWithLongTap() = flowPrefs.getBoolean(Keys.readWithLongTap, true)

    fun readWithVolumeKeys() = flowPrefs.getBoolean(Keys.readWithVolumeKeys, false)

    fun readWithVolumeKeysInverted() = flowPrefs.getBoolean(Keys.readWithVolumeKeysInverted, false)

    fun navigationModePager() = flowPrefs.getInt(Keys.navigationModePager, 0)

    fun navigationModeWebtoon() = flowPrefs.getInt(Keys.navigationModeWebtoon, 0)

    fun showNavigationOverlayNewUser() = flowPrefs.getBoolean(Keys.showNavigationOverlayNewUser, true)

    fun showNavigationOverlayOnStart() = flowPrefs.getBoolean(Keys.showNavigationOverlayOnStart, false)

    fun readerHideTreshold() = flowPrefs.getEnum(Keys.readerHideThreshold, Values.ReaderHideThreshold.LOW)

    fun portraitColumns() = flowPrefs.getInt(Keys.portraitColumns, 0)

    fun landscapeColumns() = flowPrefs.getInt(Keys.landscapeColumns, 0)

    fun jumpToChapters() = prefs.getBoolean(Keys.jumpToChapters, false)

    fun autoUpdateTrack() = prefs.getBoolean(Keys.autoUpdateTrack, true)

    fun lastUsedSource() = flowPrefs.getLong(Keys.lastUsedSource, -1)

    fun lastUsedCategory() = flowPrefs.getInt(Keys.lastUsedCategory, 0)

    fun lastVersionCode() = flowPrefs.getInt("last_version_code", 0)

    fun sourceDisplayMode() = flowPrefs.getEnum(Keys.sourceDisplayMode, DisplayModeSetting.COMPACT_GRID)

    fun enabledLanguages() = flowPrefs.getStringSet(Keys.enabledLanguages, setOf("all", "en", Locale.getDefault().language))

    fun trackUsername(sync: TrackService) = prefs.getString(Keys.trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = prefs.getString(Keys.trackPassword(sync.id), "")

    fun setTrackCredentials(sync: TrackService, username: String, password: String) {
        prefs.edit {
            putString(Keys.trackUsername(sync.id), username)
            putString(Keys.trackPassword(sync.id), password)
        }
    }

    fun trackToken(sync: TrackService) = flowPrefs.getString(Keys.trackToken(sync.id), "")

    fun anilistScoreType() = flowPrefs.getString("anilist_score_type", Anilist.POINT_10)

    fun backupsDirectory() = flowPrefs.getString(Keys.backupDirectory, defaultBackupDir.toString())

    fun relativeTime() = flowPrefs.getInt(Keys.relativeTime, 7)

    fun dateFormat(format: String = flowPrefs.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadsDirectory() = flowPrefs.getString(Keys.downloadsDirectory, defaultDownloadsDir.toString())

    fun downloadOnlyOverWifi() = prefs.getBoolean(Keys.downloadOnlyOverWifi, true)

    fun folderPerManga() = prefs.getBoolean(Keys.folderPerManga, false)

    fun numberOfBackups() = flowPrefs.getInt(Keys.numberOfBackups, 1)

    fun backupInterval() = flowPrefs.getInt(Keys.backupInterval, 0)

    fun removeAfterReadSlots() = prefs.getInt(Keys.removeAfterReadSlots, -1)

    fun removeAfterMarkedAsRead() = prefs.getBoolean(Keys.removeAfterMarkedAsRead, false)

    fun removeBookmarkedChapters() = prefs.getBoolean(Keys.removeBookmarkedChapters, false)

    fun removeExcludeCategories() = flowPrefs.getStringSet(Keys.removeExcludeCategories, emptySet())

    fun libraryUpdateInterval() = flowPrefs.getInt(Keys.libraryUpdateInterval, 24)

    fun libraryUpdateDeviceRestriction() = flowPrefs.getStringSet(Keys.libraryUpdateDeviceRestriction, setOf(DEVICE_UNMETERED_NETWORK))
    fun libraryUpdateMangaRestriction() = flowPrefs.getStringSet(Keys.libraryUpdateMangaRestriction, setOf(MANGA_FULLY_READ, MANGA_ONGOING))

    fun showUpdatesNavBadge() = flowPrefs.getBoolean(Keys.showUpdatesNavBadge, false)
    fun unreadUpdatesCount() = flowPrefs.getInt("library_unread_updates_count", 0)

    fun libraryUpdateCategories() = flowPrefs.getStringSet(Keys.libraryUpdateCategories, emptySet())
    fun libraryUpdateCategoriesExclude() = flowPrefs.getStringSet(Keys.libraryUpdateCategoriesExclude, emptySet())

    fun libraryDisplayMode() = flowPrefs.getEnum(Keys.libraryDisplayMode, DisplayModeSetting.COMPACT_GRID)

    fun downloadBadge() = flowPrefs.getBoolean(Keys.downloadBadge, false)

    fun localBadge() = flowPrefs.getBoolean(Keys.localBadge, true)

    fun downloadedOnly() = flowPrefs.getBoolean(Keys.downloadedOnly, false)

    fun unreadBadge() = flowPrefs.getBoolean(Keys.unreadBadge, true)

    fun languageBadge() = flowPrefs.getBoolean(Keys.languageBadge, false)

    fun categoryTabs() = flowPrefs.getBoolean(Keys.categoryTabs, true)

    fun categoryNumberOfItems() = flowPrefs.getBoolean(Keys.categoryNumberOfItems, false)

    fun filterDownloaded() = flowPrefs.getInt(Keys.filterDownloaded, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterUnread() = flowPrefs.getInt(Keys.filterUnread, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterCompleted() = flowPrefs.getInt(Keys.filterCompleted, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterTracking(name: Int) = flowPrefs.getInt("${Keys.filterTracked}_$name", ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterStarted() = flowPrefs.getInt(Keys.filterStarted, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterLewd() = flowPrefs.getInt(Keys.filterLewd, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun librarySortingMode() = flowPrefs.getEnum(Keys.librarySortingMode, SortModeSetting.ALPHABETICAL)
    fun librarySortingAscending() = flowPrefs.getEnum(Keys.librarySortingDirection, SortDirectionSetting.ASCENDING)

    fun migrationSortingMode() = flowPrefs.getEnum(Keys.migrationSortingMode, MigrationSourcesController.SortSetting.ALPHABETICAL)
    fun migrationSortingDirection() = flowPrefs.getEnum(Keys.migrationSortingDirection, MigrationSourcesController.DirectionSetting.ASCENDING)

    fun automaticExtUpdates() = flowPrefs.getBoolean(Keys.automaticExtUpdates, true)

    fun showNsfwSource() = flowPrefs.getBoolean(Keys.showNsfwSource, true)

    fun extensionUpdatesCount() = flowPrefs.getInt("ext_updates_count", 0)

    fun lastAppCheck() = flowPrefs.getLong("last_app_check", 0)
    fun lastExtCheck() = flowPrefs.getLong("last_ext_check", 0)

    fun searchPinnedSourcesOnly() = prefs.getBoolean(Keys.searchPinnedSourcesOnly, false)

    fun disabledSources() = flowPrefs.getStringSet("hidden_catalogues", emptySet())

    fun pinnedSources() = flowPrefs.getStringSet("pinned_catalogues", emptySet())

    fun downloadNew() = flowPrefs.getBoolean(Keys.downloadNew, false)

    fun downloadNewCategories() = flowPrefs.getStringSet(Keys.downloadNewCategories, emptySet())
    fun downloadNewCategoriesExclude() = flowPrefs.getStringSet(Keys.downloadNewCategoriesExclude, emptySet())

    fun defaultCategory() = prefs.getInt(Keys.defaultCategory, -1)

    fun categorisedDisplaySettings() = flowPrefs.getBoolean(Keys.categorizedDisplay, false)

    fun skipRead() = prefs.getBoolean(Keys.skipRead, false)

    fun skipFiltered() = prefs.getBoolean(Keys.skipFiltered, true)

    fun migrateFlags() = flowPrefs.getInt("migrate_flags", Int.MAX_VALUE)

    fun trustedSignatures() = flowPrefs.getStringSet("trusted_signatures", emptySet())

    fun dohProvider() = prefs.getInt(Keys.dohProvider, -1)

    fun lastSearchQuerySearchSettings() = flowPrefs.getString("last_search_query", "")

    fun filterChapterByRead() = prefs.getInt(Keys.defaultChapterFilterByRead, Manga.SHOW_ALL)

    fun filterChapterByDownloaded() = prefs.getInt(Keys.defaultChapterFilterByDownloaded, Manga.SHOW_ALL)

    fun filterChapterByBookmarked() = prefs.getInt(Keys.defaultChapterFilterByBookmarked, Manga.SHOW_ALL)

    fun sortChapterBySourceOrNumber() = prefs.getInt(Keys.defaultChapterSortBySourceOrNumber, Manga.CHAPTER_SORTING_SOURCE)

    fun displayChapterByNameOrNumber() = prefs.getInt(Keys.defaultChapterDisplayByNameOrNumber, Manga.CHAPTER_DISPLAY_NAME)

    fun sortChapterByAscendingOrDescending() = prefs.getInt(Keys.defaultChapterSortByAscendingOrDescending, Manga.CHAPTER_SORT_DESC)

    fun incognitoMode() = flowPrefs.getBoolean(Keys.incognitoMode, false)

    fun tabletUiMode() = flowPrefs.getEnum(Keys.tabletUiMode, Values.TabletUiMode.AUTOMATIC)

    fun extensionInstaller() = flowPrefs.getEnum(
        Keys.extensionInstaller,
        if (DeviceUtil.isMiui()) Values.ExtensionInstaller.LEGACY else Values.ExtensionInstaller.PACKAGEINSTALLER
    )

    fun verboseLogging() = prefs.getBoolean(Keys.verboseLogging, false)

    fun autoClearChapterCache() = prefs.getBoolean(Keys.autoClearChapterCache, false)

    fun setChapterSettingsDefault(manga: Manga) {
        prefs.edit {
            putInt(Keys.defaultChapterFilterByRead, manga.readFilter)
            putInt(Keys.defaultChapterFilterByDownloaded, manga.downloadedFilter)
            putInt(Keys.defaultChapterFilterByBookmarked, manga.bookmarkedFilter)
            putInt(Keys.defaultChapterSortBySourceOrNumber, manga.sorting)
            putInt(Keys.defaultChapterDisplayByNameOrNumber, manga.displayMode)
            putInt(Keys.defaultChapterSortByAscendingOrDescending, if (manga.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC)
        }
    }
    // SY -->

    fun defaultMangaOrder() = flowPrefs.getString("default_manga_order", "")

    fun migrationSources() = flowPrefs.getString("migrate_sources", "")

    fun smartMigration() = flowPrefs.getBoolean("smart_migrate", false)

    fun useSourceWithMost() = flowPrefs.getBoolean("use_source_with_most", false)

    fun skipPreMigration() = flowPrefs.getBoolean(Keys.skipPreMigration, false)

    fun hideNotFoundMigration() = flowPrefs.getBoolean(Keys.hideNotFoundMigration, false)

    fun isHentaiEnabled() = flowPrefs.getBoolean(Keys.eh_is_hentai_enabled, true)

    fun isSyncEHEnabled() = flowPrefs.getBoolean(Keys.eh_is_sync_eh_enabled, true)

    fun enableExhentai() = flowPrefs.getBoolean(Keys.eh_enableExHentai, false)

    fun imageQuality() = flowPrefs.getString(Keys.eh_ehentai_quality, "auto")

    fun useHentaiAtHome() = flowPrefs.getInt(Keys.eh_enable_hah, 0)

    fun useJapaneseTitle() = flowPrefs.getBoolean("use_jp_title", false)

    fun exhUseOriginalImages() = flowPrefs.getBoolean(Keys.eh_useOrigImages, false)

    fun ehTagFilterValue() = flowPrefs.getInt(Keys.eh_tag_filtering_value, 0)

    fun ehTagWatchingValue() = flowPrefs.getInt(Keys.eh_tag_watching_value, 0)

    // EH Cookies
    fun memberIdVal() = flowPrefs.getString("eh_ipb_member_id", "")

    fun passHashVal() = flowPrefs.getString("eh_ipb_pass_hash", "")
    fun igneousVal() = flowPrefs.getString("eh_igneous", "")
    fun ehSettingsProfile() = flowPrefs.getInt(Keys.eh_ehSettingsProfile, -1)
    fun exhSettingsProfile() = flowPrefs.getInt(Keys.eh_exhSettingsProfile, -1)
    fun exhSettingsKey() = flowPrefs.getString(Keys.eh_settingsKey, "")
    fun exhSessionCookie() = flowPrefs.getString(Keys.eh_sessionCookie, "")
    fun exhHathPerksCookies() = flowPrefs.getString(Keys.eh_hathPerksCookie, "")

    fun exhShowSyncIntro() = flowPrefs.getBoolean(Keys.eh_showSyncIntro, true)

    fun exhReadOnlySync() = flowPrefs.getBoolean(Keys.eh_readOnlySync, false)

    fun exhLenientSync() = flowPrefs.getBoolean(Keys.eh_lenientSync, false)

    fun exhShowSettingsUploadWarning() = flowPrefs.getBoolean(Keys.eh_showSettingsUploadWarning, true)

    fun expandFilters() = flowPrefs.getBoolean(Keys.eh_expandFilters, false)

    fun readerThreads() = flowPrefs.getInt(Keys.eh_readerThreads, 2)

    fun readerInstantRetry() = flowPrefs.getBoolean(Keys.eh_readerInstantRetry, true)

    fun autoscrollInterval() = flowPrefs.getFloat(Keys.eh_utilAutoscrollInterval, 3f)

    fun cacheSize() = flowPrefs.getString(Keys.eh_cacheSize, "75")

    fun preserveReadingPosition() = flowPrefs.getBoolean(Keys.eh_preserveReadingPosition, false)

    fun autoSolveCaptcha() = flowPrefs.getBoolean(Keys.eh_autoSolveCaptchas, false)

    fun delegateSources() = flowPrefs.getBoolean(Keys.eh_delegateSources, true)

    fun ehLastVersionCode() = flowPrefs.getInt("eh_last_version_code", 0)

    fun savedSearches() = flowPrefs.getStringSet("eh_saved_searches", emptySet())

    fun logLevel() = flowPrefs.getInt(Keys.eh_logLevel, 0)

    fun enableSourceBlacklist() = flowPrefs.getBoolean(Keys.eh_enableSourceBlacklist, true)

    fun exhAutoUpdateFrequency() = flowPrefs.getInt(Keys.eh_autoUpdateFrequency, 1)

    fun exhAutoUpdateRequirements() = flowPrefs.getStringSet(Keys.eh_autoUpdateRestrictions, emptySet())

    fun exhAutoUpdateStats() = flowPrefs.getString(Keys.eh_autoUpdateStats, "")

    fun aggressivePageLoading() = flowPrefs.getBoolean(Keys.eh_aggressivePageLoading, false)

    fun preloadSize() = flowPrefs.getInt(Keys.eh_preload_size, 10)

    fun useAutoWebtoon() = flowPrefs.getBoolean(Keys.eh_use_auto_webtoon, true)

    fun exhWatchedListDefaultState() = flowPrefs.getBoolean(Keys.eh_watched_list_default_state, false)

    fun exhSettingsLanguages() = flowPrefs.getString(Keys.eh_settings_languages, "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false")

    fun exhEnabledCategories() = flowPrefs.getString(Keys.eh_enabled_categories, "false,false,false,false,false,false,false,false,false,false")

    fun latestTabSources() = flowPrefs.getStringSet(Keys.latest_tab_sources, mutableSetOf())

    fun latestTabInFront() = flowPrefs.getBoolean(Keys.latest_tab_position, false)

    fun sourcesTabCategories() = flowPrefs.getStringSet(Keys.sources_tab_categories, mutableSetOf())

    fun sourcesTabCategoriesFilter() = flowPrefs.getBoolean(Keys.sources_tab_categories_filter, false)

    fun sourcesTabSourcesInCategories() = flowPrefs.getStringSet(Keys.sources_tab_source_categories, mutableSetOf())

    fun sourceSorting() = flowPrefs.getInt(Keys.sourcesSort, 0)

    fun recommendsInOverflow() = flowPrefs.getBoolean(Keys.recommendsInOverflow, false)

    fun enhancedEHentaiView() = flowPrefs.getBoolean(Keys.enhancedEHentaiView, true)

    fun webtoonEnableZoomOut() = flowPrefs.getBoolean(Keys.webtoonEnableZoomOut, false)

    fun startReadingButton() = flowPrefs.getBoolean(Keys.startReadingButton, true)

    fun groupLibraryBy() = flowPrefs.getInt(Keys.groupLibraryBy, 0)

    fun continuousVerticalTappingByPage() = flowPrefs.getBoolean(Keys.continuousVerticalTappingByPage, false)

    fun groupLibraryUpdateType() = flowPrefs.getEnum(Keys.groupLibraryUpdateType, Values.GroupLibraryMode.GLOBAL)

    fun useNewSourceNavigation() = flowPrefs.getBoolean(Keys.useNewSourceNavigation, false)

    fun preferredMangaDexId() = flowPrefs.getString(Keys.preferredMangaDexId, "0")

    fun mangadexSyncToLibraryIndexes() = flowPrefs.getStringSet(Keys.mangadexSyncToLibraryIndexes, emptySet())

    fun dataSaver() = flowPrefs.getBoolean(Keys.dataSaver, false)

    fun ignoreJpeg() = flowPrefs.getBoolean(Keys.ignoreJpeg, false)

    fun ignoreGif() = flowPrefs.getBoolean(Keys.ignoreGif, true)

    fun dataSaverImageQuality() = flowPrefs.getInt(Keys.dataSaverImageQuality, 80)

    fun dataSaverImageFormatJpeg() = flowPrefs.getBoolean(Keys.dataSaverImageFormatJpeg, false)

    fun dataSaverServer() = flowPrefs.getString(Keys.dataSaverServer, "")

    fun dataSaverColorBW() = flowPrefs.getBoolean(Keys.dataSaverColorBW, false)

    fun dataSaverExcludedSources() = flowPrefs.getStringSet(Keys.dataSaverExcludedSources, emptySet())

    fun dataSaverDownloader() = flowPrefs.getBoolean(Keys.dataSaverDownloaer, true)

    fun saveChaptersAsCBZ() = flowPrefs.getBoolean(Keys.saveChaptersAsCBZ, false)

    fun saveChaptersAsCBZLevel() = flowPrefs.getInt(Keys.saveChaptersAsCBZLevel, 0)

    fun allowLocalSourceHiddenFolders() = flowPrefs.getBoolean(Keys.allowLocalSourceHiddenFolders, false)

    fun authenticatorTimeRanges() = flowPrefs.getStringSet(Keys.authenticatorTimeRanges, mutableSetOf())

    fun authenticatorDays() = flowPrefs.getInt(Keys.authenticatorDays, 0x7F)

    fun sortTagsForLibrary() = flowPrefs.getStringSet(Keys.sortTagsForLibrary, mutableSetOf())

    fun extensionRepos() = flowPrefs.getStringSet(Keys.extensionRepos, emptySet())

    fun cropBordersContinuousVertical() = flowPrefs.getBoolean(Keys.cropBordersContinuousVertical, false)

    fun forceHorizontalSeekbar() = flowPrefs.getBoolean(Keys.forceHorizontalSeekbar, false)

    fun landscapeVerticalSeekbar() = flowPrefs.getBoolean(Keys.landscapeVerticalSeekbar, false)

    fun leftVerticalSeekbar() = flowPrefs.getBoolean(Keys.leftVerticalSeekbar, false)

    fun readerBottomButtons() = flowPrefs.getStringSet(Keys.readerBottomButtons, ReaderBottomButton.BUTTONS_DEFAULTS)

    fun bottomBarLabels() = flowPrefs.getBoolean(Keys.bottomBarLabels, true)

    fun showNavUpdates() = flowPrefs.getBoolean(Keys.showNavUpdates, true)

    fun showNavHistory() = flowPrefs.getBoolean(Keys.showNavHistory, true)

    fun pageLayout() = flowPrefs.getInt(Keys.pageLayout, PagerConfig.PageLayout.AUTOMATIC)

    fun invertDoublePages() = flowPrefs.getBoolean(Keys.invertDoublePages, false)
}
