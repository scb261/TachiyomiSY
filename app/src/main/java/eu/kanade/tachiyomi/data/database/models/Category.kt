package eu.kanade.tachiyomi.data.database.models

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import java.io.Serializable

interface Category : Serializable {

    var id: Int?

    var name: String

    var order: Int

    var flags: Int

    // SY -->
    var mangaOrder: List<Long>
    // SY <--

    private fun setFlags(flag: Int, mask: Int) {
        flags = flags and mask.inv() or (flag and mask)
    }

    var displayMode: Int
        get() = flags and DisplayModeSetting.MASK
        set(mode) = setFlags(mode, DisplayModeSetting.MASK)

    var sortMode: Int
        get() = flags and SortModeSetting.MASK
        set(mode) = setFlags(mode, SortModeSetting.MASK)

    var sortDirection: Int
        get() = flags and SortDirectionSetting.MASK
        set(mode) = setFlags(mode, SortDirectionSetting.MASK)

    companion object {

        fun create(name: String): Category = CategoryImpl().apply {
            this.name = name
        }

        fun createDefault(context: Context): Category = create(context.getString(R.string.label_default)).apply { id = 0 }
    }
}
