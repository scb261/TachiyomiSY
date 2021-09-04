package eu.kanade.tachiyomi.ui.library

import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.clear
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.SourceComfortableGridItemBinding
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.view.loadAnyAutoPause
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks

/**
 * Class used to hold the displayed data of a manga in the library, like the cover or the title.
 * All the elements from the layout file "item_source_grid" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @param listener a listener to react to single tap and long tap events.
 * @constructor creates a new library holder.
 */
class LibraryComfortableGridHolder(
    private val view: View,
    adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    // SY -->
    private val hasTitle: Boolean
// SY <--
) : LibraryHolder<SourceComfortableGridItemBinding>(view, adapter) {

    override val binding = SourceComfortableGridItemBinding.bind(view)

    // SY -->
    var manga: Manga? = null

    init {
        binding.playLayout.clicks()
            .onEach {
                playButtonClicked()
            }
            .launchIn((adapter as LibraryCategoryAdapter).controller.viewScope)
    }
    // SY <--

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    override fun onSetValues(item: LibraryItem) {
        // SY -->
        manga = item.manga
        // SY <--
        // Update the title of the manga.
        binding.title.text = item.manga.title
        // SY -->
        binding.title.isVisible = hasTitle
        // SY <--

        // For rounded corners
        binding.badges.clipToOutline = true

        // Update the unread count and its visibility.
        with(binding.unreadText) {
            isVisible = item.unreadCount > 0
            text = item.unreadCount.toString()
        }
        // Update the download count and its visibility.
        with(binding.downloadText) {
            isVisible = item.downloadCount > 0
            text = item.downloadCount.toString()
        }
        // set local visibility if its local manga
        binding.localText.isVisible = item.isLocal

        // SY -->
        binding.playLayout.isVisible = (item.manga.unread > 0 && item.startReadingButton)
        // SY <--

        // For rounded corners
        binding.card.clipToOutline = true

        // Update the cover.
        binding.thumbnail.clear()
        binding.thumbnail.loadAnyAutoPause(item.manga)
    }

    // SY -->
    private fun playButtonClicked() {
        manga?.let { (adapter as LibraryCategoryAdapter).controller.startReading(it, (adapter as LibraryCategoryAdapter)) }
    }
    // SY <--
}
