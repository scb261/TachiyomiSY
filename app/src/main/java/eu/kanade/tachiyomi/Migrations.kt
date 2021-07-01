package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.updater.UpdaterJob
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob

object Migrations {

    /**
     * Performs a migration when the application is updated.
     *
     * @param preferences Preferences of the application.
     * @return true if a migration is performed, false otherwise.
     */
    fun upgrade(preferences: PreferencesHelper): Boolean {
        val context = preferences.context

        // Cancel app updater job for debug builds that don't include it
        if (BuildConfig.DEBUG && !BuildConfig.INCLUDE_UPDATER) {
            UpdaterJob.cancelTask(context)
        }

        val oldVersion = preferences.lastVersionCode().get()
        if (oldVersion < BuildConfig.VERSION_CODE) {
            preferences.lastVersionCode().set(BuildConfig.VERSION_CODE)

            // Fresh install
            if (oldVersion == 0) {
                // Set up default background tasks
                if (BuildConfig.INCLUDE_UPDATER) {
                    UpdaterJob.setupTask(context)
                }
                ExtensionUpdateJob.setupTask(context)
                LibraryUpdateJob.setupTask(context)
                return false
            }

            // Add migrations here in future

            return true
        }

        return false
    }
}
