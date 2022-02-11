package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import exh.md.utils.MangaDexRelation
import exh.md.utils.MdUtil
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable
import tachiyomi.source.model.MangaInfo

@Serializable
class MangaDexSearchMetadata : RaisedSearchMetadata() {
    var mdUuid: String? = null

    // var mdUrl: String? = null

    var cover: String? = null

    var title: String? by titleDelegate(TITLE_TYPE_MAIN)
    var altTitles: List<String>? = null

    var description: String? = null

    var authors: List<String>? = null
    var artists: List<String>? = null

    var langFlag: String? = null

    var lastChapterNumber: Int? = null
    var rating: Float? = null
    // var users: String? = null

    var anilistId: String? = null
    var kitsuId: String? = null
    var myAnimeListId: String? = null
    var mangaUpdatesId: String? = null
    var animePlanetId: String? = null

    var status: Int? = null

    // var missing_chapters: String? = null

    var followStatus: Int? = null
    var relation: MangaDexRelation? = null

    // var maxChapterNumber: Int? = null

    override fun createMangaInfo(manga: MangaInfo): MangaInfo {
        val key = mdUuid?.let { MdUtil.buildMangaUrl(it) }

        val title = title

        val cover = cover

        val author = authors?.joinToString()?.let { MdUtil.cleanString(it) }

        val artist = artists?.joinToString()?.let { MdUtil.cleanString(it) }

        val status = status

        val genres = tagsToGenreList()

        val description = description

        return manga.copy(
            key = key ?: manga.key,
            title = title ?: manga.title,
            cover = cover ?: manga.cover,
            author = author ?: manga.author,
            artist = artist ?: manga.artist,
            status = status ?: manga.status,
            genres = genres,
            description = description ?: manga.description
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                mdUuid?.let { getString(R.string.id) to it },
                // mdUrl?.let { getString(R.string.url) to it },
                cover?.let { getString(R.string.thumbnail_url) to it },
                title?.let { getString(R.string.title) to it },
                authors?.let { getString(R.string.author) to it.joinToString() },
                artists?.let { getString(R.string.artist) to it.joinToString() },
                langFlag?.let { getString(R.string.language) to it },
                lastChapterNumber?.let { getString(R.string.last_chapter_number) to it.toString() },
                // rating?.let { getString(R.string.average_rating) to it },
                // users?.let { getString(R.string.total_ratings) to it },
                status?.let { getString(R.string.status) to it.toString() },
                // missing_chapters?.let { getString(R.string.missing_chapters) to it },
                followStatus?.let { getString(R.string.follow_status) to it.toString() },
                anilistId?.let { getString(R.string.anilist_id) to it },
                kitsuId?.let { getString(R.string.kitsu_id) to it },
                myAnimeListId?.let { getString(R.string.mal_id) to it },
                mangaUpdatesId?.let { getString(R.string.manga_updates_id) to it },
                animePlanetId?.let { getString(R.string.anime_planet_id) to it },
            )
        }
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0
    }
}
