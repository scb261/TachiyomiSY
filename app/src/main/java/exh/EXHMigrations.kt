package exh

import android.content.Context
import android.os.Build
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.updater.UpdaterJob
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.Hitomi
import eu.kanade.tachiyomi.source.online.all.NHentai
import exh.log.xLogE
import exh.log.xLogW
import exh.source.BlacklistedSources
import exh.source.EH_SOURCE_ID
import exh.source.HBROWSE_SOURCE_ID
import exh.source.PERV_EDEN_EN_SOURCE_ID
import exh.source.PERV_EDEN_IT_SOURCE_ID
import exh.source.TSUMINO_SOURCE_ID
import exh.util.over
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.net.URI
import java.net.URISyntaxException

object EXHMigrations {
    private val db: DatabaseHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    /**
     * Performs a migration when the application is updated.
     *
     * @param preferences Preferences of the application.
     * @return true if a migration is performed, false otherwise.
     */
    fun upgrade(preferences: PreferencesHelper): Boolean {
        val context = preferences.context
        val oldVersion = preferences.ehLastVersionCode().get()
        try {
            if (oldVersion < BuildConfig.VERSION_CODE) {
                preferences.ehLastVersionCode().set(BuildConfig.VERSION_CODE)

                // Fresh install
                if (oldVersion == 0) {
                    // Set up default background tasks
                    if (BuildConfig.INCLUDE_UPDATER && Build.VERSION.SDK_INT over Build.VERSION_CODES.LOLLIPOP_MR1) {
                        UpdaterJob.setupTask(context)
                    }
                    ExtensionUpdateJob.setupTask(context)
                    LibraryUpdateJob.setupTask(context)
                    return false
                }

                // Add migrations here in future

                // if (oldVersion under 1) { } (1 is current release version)
                // do stuff here when releasing changed crap

                // TODO BE CAREFUL TO NOT FUCK UP MergedSources IF CHANGING URLs

                return true
            }
        } catch (e: Exception) {
            xLogE("Failed to migrate app from $oldVersion -> ${BuildConfig.VERSION_CODE}!", e)
        }
        return false
    }

    fun migrateBackupEntry(manga: Manga) {
        if (manga.source == 6905L) {
            manga.source = PERV_EDEN_EN_SOURCE_ID
        }

        if (manga.source == 6906L) {
            manga.source = PERV_EDEN_IT_SOURCE_ID
        }

        if (manga.source == 6907L) {
            // Migrate the old source to the delegated one
            manga.source = NHentai.otherId
            // Migrate nhentai URLs
            manga.url = getUrlWithoutDomain(manga.url)
        }

        // Migrate Tsumino source IDs
        if (manga.source == 6909L) {
            manga.source = TSUMINO_SOURCE_ID
        }

        if (manga.source == 6910L) {
            manga.source = Hitomi.otherId
        }

        if (manga.source == 6912L) {
            manga.source = HBROWSE_SOURCE_ID
            manga.url = manga.url + "/c00001/"
        }

        // Allow importing of EHentai extension backups
        if (manga.source in BlacklistedSources.EHENTAI_EXT_SOURCES) {
            manga.source = EH_SOURCE_ID
        }
    }

    private fun backupDatabase(context: Context, oldMigrationVersion: Int) {
        val backupLocation = File(File(context.filesDir, "exh_db_bck"), "$oldMigrationVersion.bck.db")
        if (backupLocation.exists()) return // Do not backup same version twice

        val dbLocation = context.getDatabasePath(db.lowLevel().sqliteOpenHelper().databaseName)
        try {
            dbLocation.copyTo(backupLocation, overwrite = true)
        } catch (t: Throwable) {
            xLogW("Failed to backup database!")
        }
    }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (e: URISyntaxException) {
            orig
        }
    }

    @Serializable
    private data class UrlConfig(
        @SerialName("s")
        val source: Long,
        @SerialName("u")
        val url: String,
        @SerialName("m")
        val mangaUrl: String
    )

    @Serializable
    private data class MangaConfig(
        @SerialName("c")
        val children: List<MangaSource>
    ) {
        companion object {
            fun readFromUrl(url: String): MangaConfig? {
                return try {
                    Json.decodeFromString(url)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun readMangaConfig(manga: SManga): MangaConfig? {
        return MangaConfig.readFromUrl(manga.url)
    }

    @Serializable
    private data class MangaSource(
        @SerialName("s")
        val source: Long,
        @SerialName("u")
        val url: String
    ) {
        fun load(db: DatabaseHelper, sourceManager: SourceManager): LoadedMangaSource? {
            val manga = db.getManga(url, source).executeAsBlocking() ?: return null
            val source = sourceManager.getOrStub(source)
            return LoadedMangaSource(source, manga)
        }
    }

    private fun readUrlConfig(url: String): UrlConfig? {
        return try {
            Json.decodeFromString(url)
        } catch (e: Exception) {
            null
        }
    }

    private data class LoadedMangaSource(val source: Source, val manga: Manga)

    private fun updateSourceId(newId: Long, oldId: Long) {
        db.lowLevel().executeSQL(
            RawQuery.builder()
                .query(
                    """
                    UPDATE ${MangaTable.TABLE}
                        SET ${MangaTable.COL_SOURCE} = $newId
                        WHERE ${MangaTable.COL_SOURCE} = $oldId
                    """.trimIndent()
                )
                .affectsTables(MangaTable.TABLE)
                .build()
        )
    }
}
