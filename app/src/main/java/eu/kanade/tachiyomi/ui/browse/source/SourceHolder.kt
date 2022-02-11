package eu.kanade.tachiyomi.ui.browse.source

import android.view.View
import androidx.core.view.isVisible
import coil.load
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SourceMainControllerItemBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.view.setVectorCompat

class SourceHolder(view: View, val adapter: SourceAdapter /* SY --> */, private val showLatest: Boolean, private val showPins: Boolean /* SY <-- */) :
    FlexibleViewHolder(view, adapter) {

    private val binding = SourceMainControllerItemBinding.bind(view)

    init {
        binding.sourceLatest.setOnClickListener {
            adapter.clickListener.onLatestClick(bindingAdapterPosition)
        }

        binding.pin.setOnClickListener {
            adapter.clickListener.onPinClick(bindingAdapterPosition)
        }

        // SY -->
        if (!showLatest) {
            binding.sourceLatest.isVisible = false
        }
        // SY <--
    }

    fun bind(item: SourceItem) {
        val source = item.source

        binding.title.text = source.name
        binding.subtitle.isVisible = source !is LocalSource
        binding.subtitle.text = LocaleHelper.getDisplayName(source.lang)

        // Set source icon
        val icon = source.icon()
        when {
            icon != null -> binding.image.load(icon)
            item.source.id == LocalSource.ID -> binding.image.load(R.mipmap.ic_local_source)
        }

        binding.sourceLatest.isVisible = source.supportsLatest/* SY --> */ && showLatest /* SY <-- */

        binding.pin.isVisible = showPins
        if (item.isPinned) {
            binding.pin.setVectorCompat(R.drawable.ic_push_pin_24dp, R.attr.colorAccent)
        } else {
            binding.pin.setVectorCompat(R.drawable.ic_push_pin_outline_24dp, android.R.attr.textColorHint)
        }
    }
}
