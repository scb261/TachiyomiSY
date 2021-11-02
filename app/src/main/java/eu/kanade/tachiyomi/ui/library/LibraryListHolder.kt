package eu.kanade.tachiyomi.ui.library

import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.clear
import coil.loadAny
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.SourceListItemBinding

/**
 * Class used to hold the displayed data of a manga in the library, like the cover or the title.
 * All the elements from the layout file "item_library_list" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @param listener a listener to react to single tap and long tap events.
 * @constructor creates a new library holder.
 */
class LibraryListHolder(
    private val view: View,
    // SY -->
    adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
    // SY <--
) : LibraryHolder<SourceListItemBinding>(view, adapter) {

    override val binding = SourceListItemBinding.bind(view)

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    override fun onSetValues(item: LibraryItem) {
        // Update the title of the manga.
        binding.title.text = item.manga.title

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
            text = "${item.downloadCount}"
        }
        // Update the source language and its visibility
        with(binding.languageText) {
            isVisible = item.sourceLanguage.isNotEmpty()
            text = item.sourceLanguage
        }
        // show local text badge if local manga
        binding.localText.isVisible = item.isLocal

        // Create thumbnail onclick to simulate long click
        binding.thumbnail.setOnClickListener {
            // Simulate long click on this view to enter selection mode
            onLongClick(itemView)
        }

        // Update the cover
        binding.thumbnail.clear()
        binding.thumbnail.loadAny(item.manga)
    }
}
