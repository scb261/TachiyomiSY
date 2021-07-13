package eu.kanade.tachiyomi.ui.browse.source

import android.graphics.drawable.Drawable
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.minusAssign
import eu.kanade.tachiyomi.data.preference.plusAssign
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.switchPreferenceCategory
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.widget.preference.SwitchPreferenceCategory
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.appcompat.queryTextChanges
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class SourceFilterController : SettingsController() {

    init {
        setHasOptionsMenu(true)
    }

    private val onlineSources by lazy {
        Injekt.get<SourceManager>().getVisibleOnlineSources()
    }

    private var query = ""

    private var orderedLangs = listOf<String>()
    private var langPrefs = mutableListOf<Pair<String, SwitchPreferenceCategory>>()
    private var sourcesByLang: TreeMap<String, MutableList<HttpSource>> = TreeMap()
    private var sorting = SourcesSort.Alpha

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.label_sources

        sorting = SourcesSort.from(preferences.sourceSorting().get()) ?: SourcesSort.Alpha
        activity?.invalidateOptionsMenu()

        // Get the list of active language codes.
        val activeLangsCodes = preferences.enabledLanguages().get()

        // Get a map of sources grouped by language.
        sourcesByLang = onlineSources.groupByTo(TreeMap(), { it.lang })

        // Order first by active languages, then inactive ones
        orderedLangs = sourcesByLang.keys.filter { it in activeLangsCodes } +
            sourcesByLang.keys.filterNot { it in activeLangsCodes }

        orderedLangs.forEach { lang ->
            // Create a preference group and set initial state and change listener
            langPrefs.add(
                Pair(
                    lang,
                    switchPreferenceCategory {
                        preferenceScreen.addPreference(this)
                        title = LocaleHelper.getSourceDisplayName(lang, context)
                        isPersistent = false
                        if (lang in activeLangsCodes) {
                            setChecked(true)
                            addLanguageSources(this, sortedSources(sourcesByLang[lang]))
                        }

                        onChange { newValue ->
                            val checked = newValue as Boolean
                            if (!checked) {
                                preferences.enabledLanguages() -= lang
                                removeAll()
                            } else {
                                preferences.enabledLanguages() += lang
                                addLanguageSources(this, sortedSources(sourcesByLang[lang]))
                            }
                            true
                        }
                    }
                )
            )
        }
    }

    override fun setDivider(divider: Drawable?) {
        super.setDivider(null)
    }

    /**
     * Adds the source list for the given group (language).
     *
     * @param group the language category.
     */
    private fun addLanguageSources(group: PreferenceGroup, sources: List<HttpSource>) {
        val disabledSourceIds = preferences.disabledSources().get()

        val selectAllPreference = CheckBoxPreference(group.context).apply {
            title = "\t\t${context.getString(R.string.pref_category_all_sources)}"
            key = "all_${sources.first().lang}"
            isPersistent = false
            isChecked = sources.all { it.id.toString() !in disabledSourceIds }
            isVisible = query.isEmpty()

            onChange { newValue ->
                val checked = newValue as Boolean
                val current = preferences.disabledSources().get().toMutableSet()
                if (checked) {
                    current.removeAll(sources.map { it.id.toString() })
                } else {
                    current.addAll(sources.map { it.id.toString() })
                }
                preferences.disabledSources().set(current)
                group.removeAll()
                addLanguageSources(group, sortedSources(sources))
                true
            }
        }
        group.addPreference(selectAllPreference)

        sources.forEach { source ->
            val sourcePreference = CheckBoxPreference(group.context).apply {
                val id = source.id.toString()
                title = source.name
                key = getSourceKey(source.id)
                isPersistent = false
                isChecked = id !in disabledSourceIds
                isVisible = query.isEmpty() || source.name.contains(query, ignoreCase = true)

                val sourceIcon = source.icon()
                if (sourceIcon != null) {
                    icon = sourceIcon
                }

                onChange { newValue ->
                    val checked = newValue as Boolean

                    if (checked) {
                        preferences.disabledSources() -= id
                    } else {
                        preferences.disabledSources() += id
                    }

                    group.removeAll()
                    addLanguageSources(group, sortedSources(sources))
                    true
                }
            }

            group.addPreference(sourcePreference)
        }
    }

    private fun getSourceKey(sourceId: Long): String {
        return "source_$sourceId"
    }

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.settings_sources, menu)
        if (sorting == SourcesSort.Alpha) menu.findItem(R.id.action_sort_alpha).isChecked = true
        else menu.findItem(R.id.action_sort_enabled).isChecked = true

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.maxWidth = Int.MAX_VALUE

        if (this.query.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(query, true)
            searchView.clearFocus()
        }

        searchView.queryTextChanges()
            .filter { router.backstack.lastOrNull()?.controller == this }
            .onEach {
                this.query = it.toString()
                drawSources()
            }
            .launchIn(viewScope)

        // Fixes problem with the overflow icon showing up in lieu of search
        searchItem.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    activity?.invalidateOptionsMenu()
                    return true
                }
            }
        )
    }

    private fun drawSources() {
        val activeLangsCodes = preferences.enabledLanguages().get()
        langPrefs.forEach { group ->
            if (group.first in activeLangsCodes) {
                group.second.removeAll()
                addLanguageSources(group.second, sortedSources(sourcesByLang[group.first]))
            }
        }
    }

    private fun sortedSources(sources: List<HttpSource>?): List<HttpSource> {
        val sourceAlpha = sources.orEmpty().sortedBy { it.name.lowercase() }
        return if (sorting == SourcesSort.Enabled) {
            val disabledSourceIds = preferences.disabledSources().get()
            sourceAlpha.filter { it.id.toString() !in disabledSourceIds } +
                sourceAlpha.filterNot { it.id.toString() !in disabledSourceIds }
        } else {
            sourceAlpha
        }
    }

    /**
     * Called when an option menu item has been selected by the user.
     *
     * @param item The selected item.
     * @return True if this event has been consumed, false if it has not.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        sorting = when (item.itemId) {
            R.id.action_sort_alpha -> SourcesSort.Alpha
            R.id.action_sort_enabled -> SourcesSort.Enabled
            else -> return super.onOptionsItemSelected(item)
        }
        item.isChecked = true
        preferences.sourceSorting().set(sorting.value)
        drawSources()
        return true
    }

    enum class SourcesSort(val value: Int) {
        Alpha(0), Enabled(1);

        companion object {
            fun from(i: Int): SourcesSort? = values().find { it.value == i }
        }
    }
}
