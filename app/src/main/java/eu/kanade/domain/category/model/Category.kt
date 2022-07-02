package eu.kanade.domain.category.model

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import java.io.Serializable
import eu.kanade.tachiyomi.data.database.models.Category as DbCategory

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    // SY -->
    val mangaOrder: List<Long>,
    // SY <--
) : Serializable {

    val displayMode: Long
        get() = flags and DisplayModeSetting.MASK

    val sortMode: Long
        get() = flags and SortModeSetting.MASK

    val sortDirection: Long
        get() = flags and SortDirectionSetting.MASK

    companion object {
        val default = { context: Context ->
            Category(
                id = 0,
                name = context.getString(R.string.default_category),
                order = 0,
                flags = 0,
                mangaOrder = emptyList(),
            )
        }
    }
}

fun Category.toDbCategory(): DbCategory = DbCategory.create(name).also {
    it.id = id.toInt()
    it.order = order.toInt()
    it.flags = flags.toInt()
}
