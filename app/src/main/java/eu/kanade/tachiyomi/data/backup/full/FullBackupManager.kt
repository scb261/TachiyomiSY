package eu.kanade.tachiyomi.data.backup.full

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.AbstractBackupManager
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CATEGORY
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CATEGORY_MASK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CHAPTER
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CHAPTER_MASK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CUSTOM_INFO
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CUSTOM_INFO_MASK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_HISTORY
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_HISTORY_MASK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_READ_MANGA
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_READ_MANGA_MASK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_TRACK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_TRACK_MASK
import eu.kanade.tachiyomi.data.backup.full.models.Backup
import eu.kanade.tachiyomi.data.backup.full.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.full.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.full.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.full.models.BackupFull
import eu.kanade.tachiyomi.data.backup.full.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.full.models.BackupManga
import eu.kanade.tachiyomi.data.backup.full.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.full.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.full.models.BackupSerializer
import eu.kanade.tachiyomi.data.backup.full.models.BackupSource
import eu.kanade.tachiyomi.data.backup.full.models.BackupTracking
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.util.lang.launchIO
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.metadata.metadata.base.insertFlatMetadataAsync
import exh.savedsearches.JsonSavedSearch
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.util.executeOnIO
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.sink
import timber.log.Timber
import kotlin.math.max

class FullBackupManager(context: Context) : AbstractBackupManager(context) {

    val parser = ProtoBuf

    /**
     * Create backup Json file from database
     *
     * @param uri path of Uri
     * @param isJob backup called from job
     */
    override fun createBackup(uri: Uri, flags: Int, isJob: Boolean): String? {
        // Create root object
        var backup: Backup? = null

        databaseHelper.inTransaction {
            val databaseManga = getFavoriteManga() /* SY --> */ + if (flags and BACKUP_READ_MANGA_MASK == BACKUP_READ_MANGA) {
                getReadManga()
            } else {
                emptyList()
            } + getMergedManga() /* SY <-- */

            backup = Backup(
                backupManga(databaseManga, flags),
                backupCategories(),
                backupExtensionInfo(databaseManga),
                backupSavedSearches()
            )
        }

        try {
            val file: UniFile = (
                if (isJob) {
                    // Get dir of file and create
                    var dir = UniFile.fromUri(context, uri)
                    dir = dir.createDirectory("automatic")

                    // Delete older backups
                    val numberOfBackups = numberOfBackups()
                    val backupRegex = Regex("""tachiyomi_\d+-\d+-\d+_\d+-\d+.proto.gz""")
                    dir.listFiles { _, filename -> backupRegex.matches(filename) }
                        .orEmpty()
                        .sortedByDescending { it.name }
                        .drop(numberOfBackups - 1)
                        .forEach { it.delete() }

                    // Create new file to place backup
                    dir.createFile(BackupFull.getDefaultFilename())
                } else {
                    UniFile.fromUri(context, uri)
                }
                )
                ?: throw Exception("Couldn't create backup file")

            val byteArray = parser.encodeToByteArray(BackupSerializer, backup!!)
            file.openOutputStream().sink().gzip().buffer().use { it.write(byteArray) }
            return file.uri.toString()
        } catch (e: Exception) {
            Timber.e(e)
            throw e
        }
    }

    private fun backupManga(mangas: List<Manga>, flags: Int): List<BackupManga> {
        return mangas.map {
            backupMangaObject(it, flags)
        }
    }

    private fun backupExtensionInfo(mangas: List<Manga>): List<BackupSource> {
        return mangas
            .asSequence()
            .map { it.source }
            .distinct()
            .map { sourceManager.getOrStub(it) }
            .map { BackupSource.copyFrom(it) }
            .toList()
    }

    /**
     * Backup the categories of library
     *
     * @return list of [BackupCategory] to be backed up
     */
    private fun backupCategories(): List<BackupCategory> {
        return databaseHelper.getCategories()
            .executeAsBlocking()
            .map { BackupCategory.copyFrom(it) }
    }

    // SY -->
    /**
     * Backup the saved searches from sources
     *
     * @return list of [BackupSavedSearch] to be backed up
     */
    private fun backupSavedSearches(): List<BackupSavedSearch> {
        return preferences.savedSearches().get().mapNotNull {
            val sourceId = it.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
            val content = Json.decodeFromString<JsonSavedSearch>(it.substringAfter(':'))
            BackupSavedSearch(
                content.name,
                content.query,
                content.filters.toString(),
                sourceId
            )
        }
    }
    // SY <--

    /**
     * Convert a manga to Json
     *
     * @param manga manga that gets converted
     * @param options options for the backup
     * @return [BackupManga] containing manga in a serializable form
     */
    private fun backupMangaObject(manga: Manga, options: Int): BackupManga {
        // Entry for this manga
        val mangaObject = BackupManga.copyFrom(manga /* SY --> */, if (options and BACKUP_CUSTOM_INFO_MASK == BACKUP_CUSTOM_INFO) customMangaManager else null /* SY <-- */)

        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            manga.id?.let { mangaId ->
                mangaObject.mergedMangaReferences = databaseHelper.getMergedMangaReferences(mangaId)
                    .executeAsBlocking()
                    .map { BackupMergedMangaReference.copyFrom(it) }
            }
        }

        val source = sourceManager.get(manga.source)?.getMainSource()
        if (source is MetadataSource<*, *>) {
            manga.id?.let { mangaId ->
                databaseHelper.getFlatMetadataForManga(mangaId).executeAsBlocking()?.let { flatMetadata ->
                    mangaObject.flatMetadata = BackupFlatMetadata.copyFrom(flatMetadata)
                }
            }
        }
        // SY <--

        // Check if user wants chapter information in backup
        if (options and BACKUP_CHAPTER_MASK == BACKUP_CHAPTER) {
            // Backup all the chapters
            val chapters = databaseHelper.getChapters(manga).executeAsBlocking()
            if (chapters.isNotEmpty()) {
                mangaObject.chapters = chapters.map { BackupChapter.copyFrom(it) }
            }
        }

        // Check if user wants category information in backup
        if (options and BACKUP_CATEGORY_MASK == BACKUP_CATEGORY) {
            // Backup categories for this manga
            val categoriesForManga = databaseHelper.getCategoriesForManga(manga).executeAsBlocking()
            if (categoriesForManga.isNotEmpty()) {
                mangaObject.categories = categoriesForManga.mapNotNull { it.order }
            }
        }

        // Check if user wants track information in backup
        if (options and BACKUP_TRACK_MASK == BACKUP_TRACK) {
            val tracks = databaseHelper.getTracks(manga).executeAsBlocking()
            if (tracks.isNotEmpty()) {
                mangaObject.tracking = tracks.map { BackupTracking.copyFrom(it) }
            }
        }

        // Check if user wants history information in backup
        if (options and BACKUP_HISTORY_MASK == BACKUP_HISTORY) {
            val historyForManga = databaseHelper.getHistoryByMangaId(manga.id!!).executeAsBlocking()
            if (historyForManga.isNotEmpty()) {
                val history = historyForManga.mapNotNull { history ->
                    val url = databaseHelper.getChapter(history.chapter_id).executeAsBlocking()?.url
                    url?.let { BackupHistory(url, history.last_read) }
                }
                if (history.isNotEmpty()) {
                    mangaObject.history = history
                }
            }
        }

        return mangaObject
    }

    fun restoreMangaNoFetch(manga: Manga, dbManga: Manga) {
        manga.id = dbManga.id
        manga.copyFrom(dbManga)
        insertManga(manga)
    }

    /**
     * Fetches manga information
     *
     * @param manga manga that needs updating
     * @return Updated manga info.
     */
    fun restoreManga(manga: Manga): Manga {
        return manga.also {
            it.initialized = it.description != null
            it.id = insertManga(it)
        }
    }

    /**
     * Restore the categories from Json
     *
     * @param backupCategories list containing categories
     */
    internal fun restoreCategories(backupCategories: List<BackupCategory>) {
        // Get categories from file and from db
        val dbCategories = databaseHelper.getCategories().executeAsBlocking()

        // Iterate over them
        backupCategories.map { it.getCategoryImpl() }.forEach { category ->
            // Used to know if the category is already in the db
            var found = false
            for (dbCategory in dbCategories) {
                // If the category is already in the db, assign the id to the file's category
                // and do nothing
                if (category.name == dbCategory.name) {
                    category.id = dbCategory.id
                    found = true
                    break
                }
            }
            // If the category isn't in the db, remove the id and insert a new category
            // Store the inserted id in the category
            if (!found) {
                // Let the db assign the id
                category.id = null
                val result = databaseHelper.insertCategory(category).executeAsBlocking()
                category.id = result.insertedId()?.toInt()
            }
        }
    }

    /**
     * Restores the categories a manga is in.
     *
     * @param manga the manga whose categories have to be restored.
     * @param categories the categories to restore.
     */
    internal fun restoreCategoriesForManga(manga: Manga, categories: List<Int>, backupCategories: List<BackupCategory>) {
        val dbCategories = databaseHelper.getCategories().executeAsBlocking()
        val mangaCategoriesToUpdate = ArrayList<MangaCategory>(categories.size)
        categories.forEach { backupCategoryOrder ->
            backupCategories.firstOrNull {
                it.order == backupCategoryOrder
            }?.let { backupCategory ->
                dbCategories.firstOrNull { dbCategory ->
                    dbCategory.name == backupCategory.name
                }?.let { dbCategory ->
                    mangaCategoriesToUpdate += MangaCategory.create(manga, dbCategory)
                }
            }
        }

        // Update database
        if (mangaCategoriesToUpdate.isNotEmpty()) {
            databaseHelper.deleteOldMangasCategories(listOf(manga)).executeAsBlocking()
            databaseHelper.insertMangasCategories(mangaCategoriesToUpdate).executeAsBlocking()
        }
    }

    /**
     * Restore history from Json
     *
     * @param history list containing history to be restored
     */
    internal fun restoreHistoryForManga(history: List<BackupHistory>) {
        // List containing history to be updated
        val historyToBeUpdated = ArrayList<History>(history.size)
        for ((url, lastRead) in history) {
            val dbHistory = databaseHelper.getHistoryByChapterUrl(url).executeAsBlocking()
            // Check if history already in database and update
            if (dbHistory != null) {
                dbHistory.apply {
                    last_read = max(lastRead, dbHistory.last_read)
                }
                historyToBeUpdated.add(dbHistory)
            } else {
                // If not in database create
                databaseHelper.getChapter(url).executeAsBlocking()?.let {
                    val historyToAdd = History.create(it).apply {
                        last_read = lastRead
                    }
                    historyToBeUpdated.add(historyToAdd)
                }
            }
        }
        databaseHelper.updateHistoryLastRead(historyToBeUpdated).executeAsBlocking()
    }

    /**
     * Restores the sync of a manga.
     *
     * @param manga the manga whose sync have to be restored.
     * @param tracks the track list to restore.
     */
    internal fun restoreTrackForManga(manga: Manga, tracks: List<Track>) {
        // Fix foreign keys with the current manga id
        tracks.map { it.manga_id = manga.id!! }

        // Get tracks from database
        val dbTracks = databaseHelper.getTracks(manga).executeAsBlocking()
        val trackToUpdate = mutableListOf<Track>()

        tracks.forEach { track ->
            var isInDatabase = false
            for (dbTrack in dbTracks) {
                if (track.sync_id == dbTrack.sync_id) {
                    // The sync is already in the db, only update its fields
                    if (track.media_id != dbTrack.media_id) {
                        dbTrack.media_id = track.media_id
                    }
                    if (track.library_id != dbTrack.library_id) {
                        dbTrack.library_id = track.library_id
                    }
                    dbTrack.last_chapter_read = max(dbTrack.last_chapter_read, track.last_chapter_read)
                    isInDatabase = true
                    trackToUpdate.add(dbTrack)
                    break
                }
            }
            if (!isInDatabase) {
                // Insert new sync. Let the db assign the id
                track.id = null
                trackToUpdate.add(track)
            }
        }
        // Update database
        if (trackToUpdate.isNotEmpty()) {
            databaseHelper.insertTracks(trackToUpdate).executeAsBlocking()
        }
    }

    internal fun restoreChaptersForManga(manga: Manga, chapters: List<Chapter>) {
        val dbChapters = databaseHelper.getChapters(manga).executeAsBlocking()

        chapters.forEach { chapter ->
            val dbChapter = dbChapters.find { it.url == chapter.url }
            if (dbChapter != null) {
                chapter.id = dbChapter.id
                chapter.copyFrom(dbChapter)
                if (dbChapter.read && !chapter.read) {
                    chapter.read = dbChapter.read
                    chapter.last_page_read = dbChapter.last_page_read
                } else if (chapter.last_page_read == 0 && dbChapter.last_page_read != 0) {
                    chapter.last_page_read = dbChapter.last_page_read
                }
                if (!chapter.bookmark && dbChapter.bookmark) {
                    chapter.bookmark = dbChapter.bookmark
                }
            }

            chapter.manga_id = manga.id
        }

        val newChapters = chapters.groupBy { it.id != null }
        newChapters[true]?.let { updateKnownChapters(it) }
        newChapters[false]?.let { insertChapters(it) }
    }

    // SY -->
    internal fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        val currentSavedSearches = preferences.savedSearches().get().mapNotNull {
            val sourceId = it.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
            val content = try {
                Json.decodeFromString<JsonSavedSearch>(it.substringAfter(':'))
            } catch (e: Exception) {
                return@mapNotNull null
            }
            BackupSavedSearch(
                content.name,
                content.query,
                content.filters.toString(),
                sourceId
            )
        }

        val newSavedSearches = backupSavedSearches.filter { backupSavedSearch ->
            currentSavedSearches.none { it.name == backupSavedSearch.name && it.source == backupSavedSearch.source }
        }.map {
            "${it.source}:" + Json.encodeToString(
                JsonSavedSearch(
                    it.name,
                    it.query,
                    Json.decodeFromString(it.filterList)
                )
            )
        }.toSet()

        preferences.savedSearches().set(newSavedSearches + preferences.savedSearches().get())
    }

    /**
     * Restore the categories from Json
     *
     * @param manga the merge manga for the references
     * @param backupMergedMangaReferences the list of backup manga references for the merged manga
     */
    internal fun restoreMergedMangaReferencesForManga(manga: Manga, backupMergedMangaReferences: List<BackupMergedMangaReference>) {
        // Get merged manga references from file and from db
        val dbMergedMangaReferences = databaseHelper.getMergedMangaReferences().executeAsBlocking()

        // Iterate over them
        backupMergedMangaReferences.forEach { backupMergedMangaReference ->
            // Used to know if the merged manga reference is already in the db
            var found = false
            for (dbMergedMangaReference in dbMergedMangaReferences) {
                // If the backupMergedMangaReference is already in the db, assign the id to the file's backupMergedMangaReference
                // and do nothing
                if (backupMergedMangaReference.mergeUrl == dbMergedMangaReference.mergeUrl && backupMergedMangaReference.mangaUrl == dbMergedMangaReference.mangaUrl) {
                    found = true
                    break
                }
            }
            // If the backupMergedMangaReference isn't in the db, remove the id and insert a new backupMergedMangaReference
            // Store the inserted id in the backupMergedMangaReference
            if (!found) {
                // Let the db assign the id
                val mergedManga = databaseHelper.getManga(backupMergedMangaReference.mangaUrl, backupMergedMangaReference.mangaSourceId).executeAsBlocking() ?: return@forEach
                val mergedMangaReference = backupMergedMangaReference.getMergedMangaReference()
                mergedMangaReference.mergeId = manga.id
                mergedMangaReference.mangaId = mergedManga.id
                databaseHelper.insertMergedManga(mergedMangaReference).executeAsBlocking()
            }
        }
    }

    internal fun restoreFlatMetadata(manga: Manga, backupFlatMetadata: BackupFlatMetadata) {
        val mangaId = manga.id ?: return
        launchIO {
            databaseHelper.getFlatMetadataForManga(mangaId).executeOnIO().let {
                if (it == null) {
                    val flatMetadata = backupFlatMetadata.getFlatMetadata(mangaId)
                    databaseHelper.insertFlatMetadataAsync(flatMetadata).await()
                }
            }
        }
    }
    // SY <--
}
