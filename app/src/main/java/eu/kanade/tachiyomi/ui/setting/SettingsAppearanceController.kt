package eu.kanade.tachiyomi.ui.setting

import android.os.Build
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.initThenAdd
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.widget.preference.ThemesPreference
import kotlinx.coroutines.flow.launchIn
import java.util.Date
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

class SettingsAppearanceController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_appearance

        preferenceCategory {
            titleRes = R.string.pref_category_theme

            listPreference {
                key = Keys.themeMode
                titleRes = R.string.pref_theme_mode

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    entriesRes = arrayOf(
                        R.string.theme_system,
                        R.string.theme_light,
                        R.string.theme_dark
                    )
                    entryValues = arrayOf(
                        Values.ThemeMode.system.name,
                        Values.ThemeMode.light.name,
                        Values.ThemeMode.dark.name
                    )
                    defaultValue = Values.ThemeMode.system.name
                } else {
                    entriesRes = arrayOf(
                        R.string.theme_light,
                        R.string.theme_dark
                    )
                    entryValues = arrayOf(
                        Values.ThemeMode.light.name,
                        Values.ThemeMode.dark.name
                    )
                    defaultValue = Values.ThemeMode.light.name
                }

                summary = "%s"
            }
            initThenAdd(ThemesPreference(context)) {
                key = Keys.appTheme
                titleRes = R.string.pref_app_theme

                val appThemes = Values.AppTheme.values().filter {
                    val monetFilter = if (it == Values.AppTheme.MONET) {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    } else {
                        true
                    }
                    it.titleResId != null && monetFilter
                }
                entries = appThemes
                defaultValue = appThemes[0].name

                onChange {
                    activity?.recreate()
                    true
                }
            }
            switchPreference {
                key = Keys.themeDarkAmoled
                titleRes = R.string.pref_dark_theme_pure_black
                defaultValue = false

                preferences.themeMode().asImmediateFlow { isVisible = it != Values.ThemeMode.light }
                    .launchIn(viewScope)

                onChange {
                    activity?.recreate()
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_layout

            if (context.isTablet()) {
                intListPreference {
                    key = Keys.sideNavIconAlignment
                    titleRes = R.string.pref_side_nav_icon_alignment
                    entriesRes = arrayOf(
                        R.string.alignment_top,
                        R.string.alignment_center,
                        R.string.alignment_bottom,
                    )
                    entryValues = arrayOf("0", "1", "2")
                    defaultValue = "0"
                    summary = "%s"
                }
            } else {
                switchPreference {
                    key = Keys.hideBottomBarOnScroll
                    titleRes = R.string.pref_hide_bottom_bar_on_scroll
                    defaultValue = true
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_timestamps

            intListPreference {
                key = Keys.relativeTime
                titleRes = R.string.pref_relative_format
                val values = arrayOf("0", "2", "7")
                entryValues = values
                entries = values.map {
                    when (it) {
                        "0" -> context.getString(R.string.off)
                        "2" -> context.getString(R.string.pref_relative_time_short)
                        else -> context.getString(R.string.pref_relative_time_long)
                    }
                }.toTypedArray()
                defaultValue = "7"
                summary = "%s"
            }

            listPreference {
                key = Keys.dateFormat
                titleRes = R.string.pref_date_format
                entryValues = arrayOf("", "MM/dd/yy", "dd/MM/yy", "yyyy-MM-dd", "dd MMM yyyy", "MMM dd, yyyy")

                val now = Date().time
                entries = entryValues.map { value ->
                    val formattedDate = preferences.dateFormat(value.toString()).format(now)
                    if (value == "") {
                        "${context.getString(R.string.label_default)} ($formattedDate)"
                    } else {
                        "$value ($formattedDate)"
                    }
                }.toTypedArray()

                defaultValue = ""
                summary = "%s"
            }
        }
        switchPreference {
            key = Keys.showNavUpdates
            titleRes = R.string.pref_hide_updates_button
            defaultValue = true
        }
        switchPreference {
            key = Keys.showNavHistory
            titleRes = R.string.pref_hide_history_button
            defaultValue = true
        }
        switchPreference {
            key = Keys.bottomBarLabels
            titleRes = R.string.pref_show_bottom_bar_labels
            defaultValue = true
        }
    }
}
