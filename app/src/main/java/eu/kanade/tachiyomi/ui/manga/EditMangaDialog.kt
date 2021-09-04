package eu.kanade.tachiyomi.ui.manga

import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.children
import coil.loadAny
import coil.transform.RoundedCornersTransformation
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.EditMangaDialogBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.widget.materialdialogs.setTextInput
import exh.util.dropBlank
import exh.util.trimOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EditMangaDialog : DialogController {

    private lateinit var binding: EditMangaDialogBinding

    private val manga: Manga

    private var customCoverUri: Uri? = null

    private var willResetCover = false

    private val infoController
        get() = targetController as MangaController

    private val context: Context get() = binding.root.context

    constructor(target: MangaController, manga: Manga) : super(
        bundleOf(KEY_MANGA to manga.id!!)
    ) {
        targetController = target
        this.manga = manga
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        manga = Injekt.get<DatabaseHelper>().getManga(bundle.getLong(KEY_MANGA))
            .executeAsBlocking()!!
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = EditMangaDialogBinding.inflate(activity!!.layoutInflater)
        val view = ScrollView(activity!!).apply {
            addView(binding.root)
        }
        onViewCreated()
        return MaterialAlertDialogBuilder(activity!!)
            .setView(view)
            .setPositiveButton(R.string.action_save) { _, _ -> onPositiveButtonClick() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    fun onViewCreated() {
        loadCover()

        val isLocal = manga.source == LocalSource.ID

        val statusAdapter: ArrayAdapter<String> = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            listOf(
                R.string.manga,
                R.string.ongoing,
                R.string.completed,
                R.string.licensed,
                R.string.publication_complete,
                R.string.hiatus,
                R.string.cancelled
            ).map { context.getString(it) }
        )

        binding.status.adapter = statusAdapter
        if (manga.status != manga.originalStatus) {
            binding.status.setSelection(
                when (manga.status) {
                    SManga.UNKNOWN -> 0
                    SManga.ONGOING -> 1
                    SManga.COMPLETED -> 2
                    SManga.LICENSED -> 3
                    SManga.PUBLICATION_COMPLETE -> 4
                    SManga.HIATUS -> 5
                    SManga.CANCELLED -> 6
                    else -> 0
                }
            )
        }

        if (isLocal) {
            if (manga.title != manga.url) {
                binding.title.setText(manga.title)
            }
            binding.title.hint = "${resources?.getString(R.string.title)}: ${manga.url}"
            binding.mangaAuthor.setText(manga.author.orEmpty())
            binding.mangaArtist.setText(manga.artist.orEmpty())
            binding.mangaDescription.setText(manga.description.orEmpty())
            binding.mangaGenresTags.setChips(manga.getGenres().orEmpty().dropBlank())
        } else {
            if (manga.title != manga.originalTitle) {
                binding.title.append(manga.title)
            }
            if (manga.author != manga.originalAuthor) {
                binding.mangaAuthor.append(manga.author.orEmpty())
            }
            if (manga.artist != manga.originalArtist) {
                binding.mangaArtist.append(manga.artist.orEmpty())
            }
            if (manga.description != manga.originalDescription) {
                binding.mangaDescription.append(manga.description.orEmpty())
            }
            binding.mangaGenresTags.setChips(manga.getGenres().orEmpty())

            binding.title.hint = "${resources?.getString(R.string.title)}: ${manga.originalTitle}"
            if (manga.originalAuthor != null) {
                binding.mangaAuthor.hint = "Author: ${manga.originalAuthor}"
            }
            if (manga.originalArtist != null) {
                binding.mangaArtist.hint = "Artist: ${manga.originalArtist}"
            }
            if (manga.originalDescription != null) {
                binding.mangaDescription.hint =
                    "${resources?.getString(R.string.description)}: ${manga.originalDescription?.replace(
                        "\n",
                        " "
                    )?.chop(20)}"
            }
        }
        binding.mangaGenresTags.clearFocus()
        binding.coverLayout.clicks()
            .onEach { infoController.changeCover() }
            .launchIn(infoController.viewScope)
        binding.resetTags.clicks()
            .onEach { resetTags() }
            .launchIn(infoController.viewScope)
    }

    private fun resetTags() {
        if (manga.genre.isNullOrBlank() || manga.source == LocalSource.ID) {
            binding.mangaGenresTags.setChips(emptyList())
        } else {
            binding.mangaGenresTags.setChips(manga.getOriginalGenres().orEmpty())
        }
    }

    fun loadCover() {
        val radius = context.resources.getDimension(R.dimen.card_radius)
        binding.mangaCover.loadAny(manga) {
            transformations(RoundedCornersTransformation(radius))
        }
    }

    fun updateCover(uri: Uri) {
        willResetCover = false
        val radius = context.resources.getDimension(R.dimen.card_radius)
        binding.mangaCover.loadAny(uri) {
            transformations(RoundedCornersTransformation(radius))
        }
        customCoverUri = uri
    }

    private fun onPositiveButtonClick() {
        infoController.presenter.updateMangaInfo(
            context,
            binding.title.text.toString(),
            binding.mangaAuthor.text.toString(),
            binding.mangaArtist.text.toString(),
            binding.mangaDescription.text.toString(),
            binding.mangaGenresTags.getTextStrings(),
            binding.status.selectedItemPosition.let {
                when (it) {
                    1 -> SManga.ONGOING
                    2 -> SManga.COMPLETED
                    3 -> SManga.LICENSED
                    4 -> SManga.PUBLICATION_COMPLETE
                    5 -> SManga.HIATUS
                    6 -> SManga.CANCELLED
                    else -> null
                }
            },
            customCoverUri,
            willResetCover
        )
    }

    private fun ChipGroup.setChips(items: List<String>) {
        removeAllViews()

        items.asSequence().map { item ->
            Chip(context).apply {
                text = item

                isCloseIconVisible = true
                closeIcon?.setTint(context.getResourceColor(R.attr.colorAccent))
                setOnCloseIconClickListener {
                    removeView(this)
                }
            }
        }.forEach {
            addView(it)
        }

        val addTagChip = Chip(context).apply {
            setText(R.string.add_tag)

            chipIcon = ContextCompat.getDrawable(context, R.drawable.ic_add_24dp)?.apply {
                setTint(context.getResourceColor(R.attr.colorAccent))
            }
            textStartPadding = 0F

            clicks().onEach {
                var newTag: String? = null
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.add_tag)
                    .setTextInput {
                        newTag = it.trimOrNull()
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        if (newTag != null) setChips(items + listOfNotNull(newTag))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }.launchIn(infoController.viewScope)
        }
        addView(addTagChip)
    }

    private fun ChipGroup.getTextStrings(): List<String> = children.mapNotNull {
        if (it is Chip && !it.text.toString().contains(context.getString(R.string.add_tag), ignoreCase = true)) {
            it.text.toString()
        } else null
    }.toList()

    private companion object {
        const val KEY_MANGA = "manga_id"
    }
}
