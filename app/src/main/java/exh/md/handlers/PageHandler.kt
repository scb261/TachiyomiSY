package exh.md.handlers

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.md.dto.AtHomeDto
import exh.md.dto.ChapterDto
import exh.md.service.MangaDexService
import exh.md.utils.MdApi
import exh.md.utils.MdUtil
import okhttp3.Headers
import tachiyomi.source.Source
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.isAccessible

class PageHandler(
    private val headers: Headers,
    private val service: MangaDexService,
    private val mangaPlusHandler: MangaPlusHandler,
    private val preferences: PreferencesHelper,
    private val mdList: MdList,
) {

    suspend fun fetchPageList(chapter: SChapter, isLogged: Boolean, usePort443Only: Boolean, dataSaver: Boolean, mangadex: Source): List<Page> {
        return withIOContext {
            val chapterResponse = service.viewChapter(MdUtil.getChapterId(chapter.url))

            if (chapter.scanlator.equals("mangaplus", true)) {
                mangaPlusHandler.fetchPageList(
                    chapterResponse.data.attributes.data
                        .first()
                        .substringAfterLast("/")
                )
            } else {
                val headers = if (isLogged) {
                    MdUtil.getAuthHeaders(headers, preferences, mdList)
                } else {
                    headers
                }

                val atHomeRequestUrl = if (usePort443Only) {
                    "${MdApi.atHomeServer}/${MdUtil.getChapterId(chapter.url)}?forcePort443=true"
                } else {
                    "${MdApi.atHomeServer}/${MdUtil.getChapterId(chapter.url)}"
                }

                updateExtensionVariable(mangadex, atHomeRequestUrl)

                val atHomeResponse = service.getAtHomeServer(atHomeRequestUrl, headers)

                pageListParse(chapterResponse, atHomeRequestUrl, atHomeResponse, dataSaver)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateExtensionVariable(mangadex: Source, atHomeRequestUrl: String) {
        val mangadexSuperclass = mangadex::class.superclasses.first()

        val helperCallable = mangadexSuperclass.members.find { it.name == "helper" } ?: return
        helperCallable.isAccessible = true
        val helper = helperCallable.call(mangadex) ?: return

        val tokenTrackerCallable = helper::class.members.find { it.name == "tokenTracker" } ?: return
        tokenTrackerCallable.isAccessible = true
        val tokenTracker = tokenTrackerCallable.call(helper) as? HashMap<String, Long> ?: return
        tokenTracker[atHomeRequestUrl] = System.currentTimeMillis()
    }

    private fun pageListParse(
        chapterDto: ChapterDto,
        atHomeRequestUrl: String,
        atHomeDto: AtHomeDto,
        dataSaver: Boolean,
    ): List<Page> {
        val hash = chapterDto.data.attributes.hash
        val pageArray = if (dataSaver) {
            chapterDto.data.attributes.dataSaver.map { "/data-saver/$hash/$it" }
        } else {
            chapterDto.data.attributes.data.map { "/data/$hash/$it" }
        }
        val now = System.currentTimeMillis()

        return pageArray.mapIndexed { pos, imgUrl ->
            Page(pos, "${atHomeDto.baseUrl},$atHomeRequestUrl,$now", imgUrl)
        }
    }
}
