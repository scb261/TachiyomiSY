package eu.kanade.tachiyomi.ui.category.sources

import android.app.Dialog
import android.os.Bundle
import com.bluelinelabs.conductor.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.widget.materialdialogs.setTextInput

/**
 * Dialog to rename an existing category of the library.
 */
class SourceCategoryRenameDialog<T>(bundle: Bundle? = null) : DialogController(bundle)
        where T : Controller, T : SourceCategoryRenameDialog.Listener {

    private var category: String? = null

    /**
     * Name of the new category. Value updated with each input from the user.
     */
    private var currentName = ""

    constructor(target: T, category: String) : this() {
        targetController = target
        this.category = category
        currentName = category
    }

    /**
     * Called when creating the dialog for this controller.
     *
     * @param savedViewState The saved state of this dialog.
     * @return a new dialog instance.
     */
    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.action_rename_category)
            .setTextInput(
                hint = resources?.getString(R.string.name),
                prefill = currentName
            ) { input ->
                currentName = input
            }
            .setPositiveButton(android.R.string.ok) { _, _ -> onPositive() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    /**
     * Called to save this Controller's state in the event that its host Activity is destroyed.
     *
     * @param outState The Bundle into which data should be saved
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(CATEGORY_KEY, category)
        super.onSaveInstanceState(outState)
    }

    /**
     * Restores data that was saved in the [onSaveInstanceState] method.
     *
     * @param savedInstanceState The bundle that has data to be restored
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        category = savedInstanceState.getSerializable(CATEGORY_KEY) as? String
    }

    /**
     * Called when the positive button of the dialog is clicked.
     */
    private fun onPositive() {
        val target = targetController as? Listener ?: return
        val category = category ?: return

        target.renameCategory(category, currentName)
    }

    interface Listener {
        fun renameCategory(category: String, name: String)
    }

    private companion object {
        const val CATEGORY_KEY = "CategoryRenameDialog.category"
    }
}
