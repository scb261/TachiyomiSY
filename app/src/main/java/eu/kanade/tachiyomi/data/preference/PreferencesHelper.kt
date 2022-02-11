package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.tfcporciuncula.flow.FlowSharedPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrationSourcesController
import eu.kanade.tachiyomi.ui.library.LibraryGroup
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

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

    fun hideBottomBarOnScroll() = flowPrefs.getBoolean("pref_hide_bottom_bar_on_scroll", true)

    fun sideNavIconAlignment() = flowPrefs.getInt("pref_side_nav_icon_alignment", 0)

    fun useAuthenticator() = flowPrefs.getBoolean("use_biometric_lock", false)

    fun lockAppAfter() = flowPrefs.getInt("lock_app_after", 0)

    fun lastAppUnlock() = flowPrefs.getLong("last_app_unlock", 0)

    fun secureScreen() = flowPrefs.getBoolean("secure_screen", false)

    fun hideNotificationContent() = prefs.getBoolean(Keys.hideNotificationContent, false)

    fun autoUpdateMetadata() = prefs.getBoolean(Keys.autoUpdateMetadata, false)

    fun autoUpdateTrackers() = prefs.getBoolean(Keys.autoUpdateTrackers, false)

    fun themeMode() = flowPrefs.getEnum(
        "pref_theme_mode_key",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { Values.ThemeMode.system } else { Values.ThemeMode.dark }
    )

    fun appTheme() = flowPrefs.getEnum(
        "pref_app_theme",
        if (DynamicColors.isDynamicColorAvailable()) { Values.AppTheme.MONET } else { Values.AppTheme.DEFAULT }
    )

    fun themeDarkAmoled() = flowPrefs.getBoolean("pref_theme_dark_amoled_key", false)

    // SY -->
    fun pageTransitionsPager() = flowPrefs.getBoolean("pref_enable_transitions_pager_key", true)

    fun pageTransitionsWebtoon() = flowPrefs.getBoolean("pref_enable_transitions_webtoon_key", true)
    // SY <--

    fun doubleTapAnimSpeed() = flowPrefs.getInt("pref_double_tap_anim_speed", 500)

    fun showPageNumber() = flowPrefs.getBoolean("pref_show_page_number_key", true)

    fun dualPageSplitPaged() = flowPrefs.getBoolean("pref_dual_page_split", false)

    fun dualPageSplitWebtoon() = flowPrefs.getBoolean("pref_dual_page_split_webtoon", false)

    fun dualPageInvertPaged() = flowPrefs.getBoolean("pref_dual_page_invert", false)

    fun dualPageInvertWebtoon() = flowPrefs.getBoolean("pref_dual_page_invert_webtoon", false)

    fun showReadingMode() = prefs.getBoolean(Keys.showReadingMode, true)

    fun trueColor() = flowPrefs.getBoolean("pref_true_color_key", false)

    fun fullscreen() = flowPrefs.getBoolean("fullscreen", true)

    fun cutoutShort() = flowPrefs.getBoolean("cutout_short", true)

    fun keepScreenOn() = flowPrefs.getBoolean("pref_keep_screen_on_key", true)

    fun customBrightness() = flowPrefs.getBoolean("pref_custom_brightness_key", false)

    fun customBrightnessValue() = flowPrefs.getInt("custom_brightness_value", 0)

    fun colorFilter() = flowPrefs.getBoolean("pref_color_filter_key", false)

    fun colorFilterValue() = flowPrefs.getInt("color_filter_value", 0)

    fun colorFilterMode() = flowPrefs.getInt("color_filter_mode", 0)

    fun grayscale() = flowPrefs.getBoolean("pref_grayscale", false)

    fun invertedColors() = flowPrefs.getBoolean("pref_inverted_colors", false)

    fun defaultReadingMode() = prefs.getInt(Keys.defaultReadingMode, ReadingModeType.RIGHT_TO_LEFT.flagValue)

    fun defaultOrientationType() = prefs.getInt(Keys.defaultOrientationType, OrientationType.FREE.flagValue)

    fun imageScaleType() = flowPrefs.getInt("pref_image_scale_type_key", 1)

    fun zoomStart() = flowPrefs.getInt("pref_zoom_start_key", 1)

    fun readerTheme() = flowPrefs.getInt("pref_reader_theme_key", 3)

    fun alwaysShowChapterTransition() = flowPrefs.getBoolean("always_show_chapter_transition", true)

    fun cropBorders() = flowPrefs.getBoolean("crop_borders", false)

    fun cropBordersWebtoon() = flowPrefs.getBoolean("crop_borders_webtoon", false)

    fun webtoonSidePadding() = flowPrefs.getInt("webtoon_side_padding", 0)

    fun readWithTapping() = flowPrefs.getBoolean("reader_tap", true)

    fun pagerNavInverted() = flowPrefs.getEnum("reader_tapping_inverted", Values.TappingInvertMode.NONE)

    fun webtoonNavInverted() = flowPrefs.getEnum("reader_tapping_inverted_webtoon", Values.TappingInvertMode.NONE)

    fun readWithLongTap() = flowPrefs.getBoolean("reader_long_tap", true)

    fun readWithVolumeKeys() = flowPrefs.getBoolean("reader_volume_keys", false)

    fun readWithVolumeKeysInverted() = flowPrefs.getBoolean("reader_volume_keys_inverted", false)

    fun navigationModePager() = flowPrefs.getInt("reader_navigation_mode_pager", 0)

    fun navigationModeWebtoon() = flowPrefs.getInt("reader_navigation_mode_webtoon", 0)

    fun showNavigationOverlayNewUser() = flowPrefs.getBoolean("reader_navigation_overlay_new_user", true)

    fun showNavigationOverlayOnStart() = flowPrefs.getBoolean("reader_navigation_overlay_on_start", false)

    fun readerHideThreshold() = flowPrefs.getEnum("reader_hide_threshold", Values.ReaderHideThreshold.LOW)

    fun portraitColumns() = flowPrefs.getInt("pref_library_columns_portrait_key", 0)

    fun landscapeColumns() = flowPrefs.getInt("pref_library_columns_landscape_key", 0)

    fun jumpToChapters() = prefs.getBoolean(Keys.jumpToChapters, false)

    fun autoUpdateTrack() = prefs.getBoolean(Keys.autoUpdateTrack, true)

    fun lastUsedSource() = flowPrefs.getLong("last_catalogue_source", -1)

    fun lastUsedCategory() = flowPrefs.getInt("last_used_category", 0)

    fun lastVersionCode() = flowPrefs.getInt("last_version_code", 0)

    fun sourceDisplayMode() = flowPrefs.getEnum("pref_display_mode_catalogue", DisplayModeSetting.COMPACT_GRID)

    fun enabledLanguages() = flowPrefs.getStringSet("source_languages", setOf("all", "en", Locale.getDefault().language))

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

    fun backupsDirectory() = flowPrefs.getString("backup_directory", defaultBackupDir.toString())

    fun relativeTime() = flowPrefs.getInt("relative_time", 7)

    fun dateFormat(format: String = flowPrefs.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadsDirectory() = flowPrefs.getString("download_directory", defaultDownloadsDir.toString())

    fun downloadOnlyOverWifi() = prefs.getBoolean(Keys.downloadOnlyOverWifi, true)

    fun saveChaptersAsCBZ() = flowPrefs.getBoolean("save_chapter_as_cbz", false)

    fun folderPerManga() = prefs.getBoolean(Keys.folderPerManga, false)

    fun numberOfBackups() = flowPrefs.getInt("backup_slots", 1)

    fun backupInterval() = flowPrefs.getInt("backup_interval", 0)

    fun removeAfterReadSlots() = prefs.getInt(Keys.removeAfterReadSlots, -1)

    fun removeAfterMarkedAsRead() = prefs.getBoolean(Keys.removeAfterMarkedAsRead, false)

    fun removeBookmarkedChapters() = prefs.getBoolean(Keys.removeBookmarkedChapters, false)

    fun removeExcludeCategories() = flowPrefs.getStringSet("remove_exclude_categories", emptySet())

    fun libraryUpdateInterval() = flowPrefs.getInt("pref_library_update_interval_key", 24)

    fun libraryUpdateDeviceRestriction() = flowPrefs.getStringSet("library_update_restriction", setOf(DEVICE_UNMETERED_NETWORK))
    fun libraryUpdateMangaRestriction() = flowPrefs.getStringSet("library_update_manga_restriction", setOf(MANGA_FULLY_READ, MANGA_ONGOING))

    fun showUpdatesNavBadge() = flowPrefs.getBoolean("library_update_show_tab_badge", false)
    fun unreadUpdatesCount() = flowPrefs.getInt("library_unread_updates_count", 0)

    fun libraryUpdateCategories() = flowPrefs.getStringSet("library_update_categories", emptySet())
    fun libraryUpdateCategoriesExclude() = flowPrefs.getStringSet("library_update_categories_exclude", emptySet())

    fun libraryDisplayMode() = flowPrefs.getEnum("pref_display_mode_library", DisplayModeSetting.COMPACT_GRID)

    fun downloadBadge() = flowPrefs.getBoolean("display_download_badge", false)

    fun localBadge() = flowPrefs.getBoolean("display_local_badge", true)

    fun downloadedOnly() = flowPrefs.getBoolean("pref_downloaded_only", false)

    fun unreadBadge() = flowPrefs.getBoolean("display_unread_badge", true)

    fun languageBadge() = flowPrefs.getBoolean("display_language_badge", false)

    fun categoryTabs() = flowPrefs.getBoolean("display_category_tabs", true)

    fun categoryNumberOfItems() = flowPrefs.getBoolean("display_number_of_items", false)

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

    fun automaticExtUpdates() = flowPrefs.getBoolean("automatic_ext_updates", true)

    fun showNsfwSource() = flowPrefs.getBoolean("show_nsfw_source", true)

    fun extensionUpdatesCount() = flowPrefs.getInt("ext_updates_count", 0)

    fun lastAppCheck() = flowPrefs.getLong("last_app_check", 0)
    fun lastExtCheck() = flowPrefs.getLong("last_ext_check", 0)

    fun searchPinnedSourcesOnly() = prefs.getBoolean(Keys.searchPinnedSourcesOnly, false)

    fun disabledSources() = flowPrefs.getStringSet("hidden_catalogues", emptySet())

    fun pinnedSources() = flowPrefs.getStringSet("pinned_catalogues", emptySet())

    fun downloadNew() = flowPrefs.getBoolean("download_new", false)

    fun downloadNewCategories() = flowPrefs.getStringSet("download_new_categories", emptySet())
    fun downloadNewCategoriesExclude() = flowPrefs.getStringSet("download_new_categories_exclude", emptySet())

    fun defaultCategory() = prefs.getInt(Keys.defaultCategory, -1)

    fun categorizedDisplaySettings() = flowPrefs.getBoolean("categorized_display", false)

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

    fun incognitoMode() = flowPrefs.getBoolean("incognito_mode", false)

    fun tabletUiMode() = flowPrefs.getEnum("tablet_ui_mode", Values.TabletUiMode.AUTOMATIC)

    fun extensionInstaller() = flowPrefs.getEnum(
        "extension_installer",
        if (DeviceUtil.isMiui) Values.ExtensionInstaller.LEGACY else Values.ExtensionInstaller.PACKAGEINSTALLER
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

    fun skipPreMigration() = flowPrefs.getBoolean("skip_pre_migration", false)

    fun hideNotFoundMigration() = flowPrefs.getBoolean("hide_not_found_migration", false)

    fun isHentaiEnabled() = flowPrefs.getBoolean("eh_is_hentai_enabled", true)

    fun enableExhentai() = flowPrefs.getBoolean("enable_exhentai", false)

    fun imageQuality() = flowPrefs.getString("ehentai_quality", "auto")

    fun useHentaiAtHome() = flowPrefs.getInt("eh_enable_hah", 0)

    fun useJapaneseTitle() = flowPrefs.getBoolean("use_jp_title", false)

    fun exhUseOriginalImages() = flowPrefs.getBoolean("eh_useOrigImages", false)

    fun ehTagFilterValue() = flowPrefs.getInt("eh_tag_filtering_value", 0)

    fun ehTagWatchingValue() = flowPrefs.getInt("eh_tag_watching_value", 0)

    // EH Cookies
    fun memberIdVal() = flowPrefs.getString("eh_ipb_member_id", "")

    fun passHashVal() = flowPrefs.getString("eh_ipb_pass_hash", "")
    fun igneousVal() = flowPrefs.getString("eh_igneous", "")
    fun ehSettingsProfile() = flowPrefs.getInt("eh_ehSettingsProfile", -1)
    fun exhSettingsProfile() = flowPrefs.getInt("eh_exhSettingsProfile", -1)
    fun exhSettingsKey() = flowPrefs.getString("eh_settingsKey", "")
    fun exhSessionCookie() = flowPrefs.getString("eh_sessionCookie", "")
    fun exhHathPerksCookies() = flowPrefs.getString("eh_hathPerksCookie", "")

    fun exhShowSyncIntro() = flowPrefs.getBoolean("eh_show_sync_intro", true)

    fun exhReadOnlySync() = flowPrefs.getBoolean("eh_sync_read_only", false)

    fun exhLenientSync() = flowPrefs.getBoolean("eh_lenient_sync", false)

    fun exhShowSettingsUploadWarning() = flowPrefs.getBoolean("eh_showSettingsUploadWarning2", true)

    fun expandFilters() = flowPrefs.getBoolean("eh_expand_filters", false)

    fun readerThreads() = flowPrefs.getInt("eh_reader_threads", 2)

    fun readerInstantRetry() = flowPrefs.getBoolean("eh_reader_instant_retry", true)

    fun autoscrollInterval() = flowPrefs.getFloat("eh_util_autoscroll_interval", 3f)

    fun cacheSize() = flowPrefs.getString("eh_cache_size", "75")

    fun preserveReadingPosition() = flowPrefs.getBoolean("eh_preserve_reading_position", false)

    fun autoSolveCaptcha() = flowPrefs.getBoolean("eh_autosolve_captchas", false)

    fun delegateSources() = flowPrefs.getBoolean("eh_delegate_sources", true)

    fun ehLastVersionCode() = flowPrefs.getInt("eh_last_version_code", 0)

    fun savedSearches() = flowPrefs.getStringSet("eh_saved_searches", emptySet())

    fun logLevel() = flowPrefs.getInt(Keys.eh_logLevel, 0)

    fun enableSourceBlacklist() = flowPrefs.getBoolean("eh_enable_source_blacklist", true)

    fun exhAutoUpdateFrequency() = flowPrefs.getInt("eh_auto_update_frequency", 1)

    fun exhAutoUpdateRequirements() = flowPrefs.getStringSet("eh_auto_update_restrictions", emptySet())

    fun exhAutoUpdateStats() = flowPrefs.getString("eh_auto_update_stats", "")

    fun aggressivePageLoading() = flowPrefs.getBoolean("eh_aggressive_page_loading", false)

    fun preloadSize() = flowPrefs.getInt("eh_preload_size", 10)

    fun useAutoWebtoon() = flowPrefs.getBoolean("eh_use_auto_webtoon", true)

    fun exhWatchedListDefaultState() = flowPrefs.getBoolean("eh_watched_list_default_state", false)

    fun exhSettingsLanguages() = flowPrefs.getString(
        "eh_settings_languages",
        "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false"
    )

    fun exhEnabledCategories() = flowPrefs.getString(
        "eh_enabled_categories",
        "false,false,false,false,false,false,false,false,false,false"
    )

    fun latestTabSources() = flowPrefs.getStringSet("latest_tab_sources", mutableSetOf())

    fun latestTabInFront() = flowPrefs.getBoolean("latest_tab_position", false)

    fun sourcesTabCategories() = flowPrefs.getStringSet("sources_tab_categories", mutableSetOf())

    fun sourcesTabCategoriesFilter() = flowPrefs.getBoolean("sources_tab_categories_filter", false)

    fun sourcesTabSourcesInCategories() = flowPrefs.getStringSet("sources_tab_source_categories", mutableSetOf())

    fun sourceSorting() = flowPrefs.getInt("sources_sort", 0)

    fun recommendsInOverflow() = flowPrefs.getBoolean("recommends_in_overflow", false)

    fun enhancedEHentaiView() = flowPrefs.getBoolean("enhanced_e_hentai_view", true)

    fun webtoonEnableZoomOut() = flowPrefs.getBoolean("webtoon_enable_zoom_out", false)

    fun startReadingButton() = flowPrefs.getBoolean("start_reading_button", true)

    fun groupLibraryBy() = flowPrefs.getInt("group_library_by", LibraryGroup.BY_DEFAULT)

    fun continuousVerticalTappingByPage() = flowPrefs.getBoolean("continuous_vertical_tapping_by_page", false)

    fun groupLibraryUpdateType() = flowPrefs.getEnum("group_library_update_type", Values.GroupLibraryMode.GLOBAL)

    fun useNewSourceNavigation() = flowPrefs.getBoolean("use_new_source_navigation", false)

    fun preferredMangaDexId() = flowPrefs.getString("preferred_mangaDex_id", "0")

    fun mangadexSyncToLibraryIndexes() = flowPrefs.getStringSet("pref_mangadex_sync_to_library_indexes", emptySet())

    fun dataSaver() = flowPrefs.getBoolean("data_saver", false)

    fun dataSaverIgnoreJpeg() = flowPrefs.getBoolean("ignore_jpeg", false)

    fun dataSaverIgnoreGif() = flowPrefs.getBoolean("ignore_gif", true)

    fun dataSaverImageQuality() = flowPrefs.getInt("data_saver_image_quality", 80)

    fun dataSaverImageFormatJpeg() = flowPrefs.getBoolean("data_saver_image_format_jpeg", false)

    fun dataSaverServer() = flowPrefs.getString("data_saver_server", "")

    fun dataSaverColorBW() = flowPrefs.getBoolean("data_saver_color_bw", false)

    fun dataSaverExcludedSources() = flowPrefs.getStringSet("data_saver_excluded", emptySet())

    fun dataSaverDownloader() = flowPrefs.getBoolean("data_saver_downloader", true)

    fun allowLocalSourceHiddenFolders() = flowPrefs.getBoolean("allow_local_source_hidden_folders", false)

    fun authenticatorTimeRanges() = flowPrefs.getStringSet("biometric_time_ranges", mutableSetOf())

    fun authenticatorDays() = flowPrefs.getInt("biometric_days", 0x7F)

    fun sortTagsForLibrary() = flowPrefs.getStringSet("sort_tags_for_library", mutableSetOf())

    fun extensionRepos() = flowPrefs.getStringSet("extension_repos", emptySet())

    fun cropBordersContinuousVertical() = flowPrefs.getBoolean("crop_borders_continues_vertical", false)

    fun forceHorizontalSeekbar() = flowPrefs.getBoolean("pref_force_horz_seekbar", false)

    fun landscapeVerticalSeekbar() = flowPrefs.getBoolean("pref_show_vert_seekbar_landscape", false)

    fun leftVerticalSeekbar() = flowPrefs.getBoolean("pref_left_handed_vertical_seekbar", false)

    fun readerBottomButtons() = flowPrefs.getStringSet("reader_bottom_buttons", ReaderBottomButton.BUTTONS_DEFAULTS)

    fun bottomBarLabels() = flowPrefs.getBoolean("pref_show_bottom_bar_labels", true)

    fun showNavUpdates() = flowPrefs.getBoolean("pref_show_updates_button", true)

    fun showNavHistory() = flowPrefs.getBoolean("pref_show_history_button", true)

    fun pageLayout() = flowPrefs.getInt("page_layout", PagerConfig.PageLayout.AUTOMATIC)

    fun invertDoublePages() = flowPrefs.getBoolean("invert_double_pages", false)

    // XZM -->
    fun ehIsSyncEhEnabled() = flowPrefs.getBoolean("eh_is_sync_eh_enabled", true)
    // XZM <--
}
