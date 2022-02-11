package eu.kanade.tachiyomi.ui.browse.extension

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import coil.clear
import coil.load
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ExtensionItemBinding
import eu.kanade.tachiyomi.extension.api.REPO_URL_PREFIX
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.util.system.LocaleHelper

class ExtensionHolder(view: View, val adapter: ExtensionAdapter) :
    FlexibleViewHolder(view, adapter) {

    private val binding = ExtensionItemBinding.bind(view)

    init {
        binding.extButton.setOnClickListener {
            adapter.buttonClickListener.onButtonClick(bindingAdapterPosition)
        }
        binding.cancelButton.setOnClickListener {
            adapter.buttonClickListener.onCancelButtonClick(bindingAdapterPosition)
        }
    }

    fun bind(item: ExtensionItem) {
        val extension = item.extension

        binding.name.text = extension.name
        binding.version.text = extension.versionName
        binding.lang.text = LocaleHelper.getSourceDisplayName(extension.lang, itemView.context)
        binding.warning.text = when {
            extension is Extension.Untrusted -> itemView.context.getString(R.string.ext_untrusted)
            extension is Extension.Installed && extension.isUnofficial -> itemView.context.getString(R.string.ext_unofficial)
            extension is Extension.Installed && extension.isObsolete -> itemView.context.getString(R.string.ext_obsolete)
            // SY -->
            extension is Extension.Installed && extension.isRedundant -> itemView.context.getString(R.string.ext_redundant)
            extension.isNsfw -> itemView.context.getString(R.string.ext_nsfw_short).plusRepo(extension)
            else -> "".plusRepo(extension)
            // SY <--
        }.uppercase()

        binding.icon.clear()
        if (extension is Extension.Available) {
            binding.icon.load(extension.iconUrl)
        } else if (extension is Extension.Installed) {
            binding.icon.load(extension.icon)
        }
        bindButtons(item)
    }

    // SY -->
    private fun String.plusRepo(extension: Extension): String {
        return if (extension is Extension.Available) {
            when (extension.repoUrl) {
                REPO_URL_PREFIX -> this
                else -> {
                    if (isEmpty()) {
                        this
                    } else {
                        this + " • "
                    } + itemView.context.getString(R.string.repo_source)
                }
            }
        } else this
    }

    // SY <--
    @Suppress("ResourceType")
    fun bindButtons(item: ExtensionItem) = with(binding.extButton) {
        val extension = item.extension

        val installStep = item.installStep
        setText(
            when (installStep) {
                InstallStep.Pending -> R.string.ext_pending
                InstallStep.Downloading -> R.string.ext_downloading
                InstallStep.Installing -> R.string.ext_installing
                InstallStep.Installed -> R.string.ext_installed
                InstallStep.Error -> R.string.action_retry
                InstallStep.Idle -> {
                    when (extension) {
                        is Extension.Installed -> {
                            if (extension.hasUpdate) {
                                R.string.ext_update
                            } else {
                                R.string.action_settings
                            }
                        }
                        is Extension.Untrusted -> R.string.ext_trust
                        is Extension.Available -> R.string.ext_install
                    }
                }
            }
        )
        // SY -->
        if (extension is Extension.Installed &&
            installStep == InstallStep.Idle &&
            extension.sources.any { it is ConfigurableSource }
        ) {
            @SuppressLint("SetTextI18n")
            text = "$text+"
        }
        // SY <--

        val isIdle = installStep == InstallStep.Idle || installStep == InstallStep.Error
        binding.cancelButton.isVisible = !isIdle
        isEnabled = isIdle
        isClickable = isIdle
    }
}
