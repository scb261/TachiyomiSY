package eu.kanade.tachiyomi.ui.category.genre

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dev.chrisbanes.insetter.applyInsetter
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.davidea.flexibleadapter.helpers.UndoHelper
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.CategoriesControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.shrinkOnScroll

/**
 * Controller to manage the categories for the users' library.
 */
class SortTagController :
    NucleusController<CategoriesControllerBinding, SortTagPresenter>(),
    FabController,
    ActionMode.Callback,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    SortTagAdapter.OnItemReleaseListener,
    SortTagCreateDialog.Listener,
    UndoHelper.OnActionListener {

    /**
     * Object used to show ActionMode toolbar.
     */
    private var actionMode: ActionMode? = null

    private var shownHelpDialog = false

    /**
     * Adapter containing category items.
     */
    private var adapter: SortTagAdapter? = null

    private var actionFab: ExtendedFloatingActionButton? = null
    private var actionFabScrollListener: RecyclerView.OnScrollListener? = null

    /**
     * Undo helper used for restoring a deleted category.
     */
    private var undoHelper: UndoHelper? = null

    /**
     * Creates the presenter for this controller. Not to be manually called.
     */
    override fun createPresenter() = SortTagPresenter()

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle(): String? {
        return resources?.getString(R.string.action_edit_tags)
    }

    init {
        setHasOptionsMenu(true)
    }

    override fun createBinding(inflater: LayoutInflater) = CategoriesControllerBinding.inflate(inflater)

    /**
     * Called after view inflation. Used to initialize the view.
     *
     * @param view The view of this controller.
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        adapter = SortTagAdapter(this@SortTagController)
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.adapter = adapter
        adapter?.isHandleDragEnabled = true
        adapter?.isPermanentDelete = false

        actionFabScrollListener = actionFab?.shrinkOnScroll(binding.recycler)
    }

    override fun configureFab(fab: ExtendedFloatingActionButton) {
        actionFab = fab
        fab.setText(R.string.action_add)
        fab.setIconResource(R.drawable.ic_add_24dp)
        fab.setOnClickListener {
            if (!shownHelpDialog) {
                shownHelpDialog = true
                helpDialog(true)
            } else {
                SortTagCreateDialog(this@SortTagController).showDialog(router, null)
            }
        }
    }

    override fun cleanupFab(fab: ExtendedFloatingActionButton) {
        fab.setOnClickListener(null)
        actionFabScrollListener?.let { binding.recycler.removeOnScrollListener(it) }
        actionFab = null
    }

    /**
     * Called when the view is being destroyed. Used to release references and remove callbacks.
     *
     * @param view The view of this controller.
     */
    override fun onDestroyView(view: View) {
        // Manually call callback to delete categories if required
        undoHelper?.onDeleteConfirmed(Snackbar.Callback.DISMISS_EVENT_MANUAL)
        undoHelper = null
        actionMode = null
        adapter = null
        super.onDestroyView(view)
    }

    /**
     * Called from the presenter when the categories are updated.
     *
     * @param categories The new list of categories to display.
     */
    fun setCategories(categories: List<SortTagItem>) {
        actionMode?.finish()
        adapter?.updateDataSet(categories)
        if (categories.isNotEmpty()) {
            binding.emptyView.hide()
            val selected = categories.filter { it.isSelected }
            if (selected.isNotEmpty()) {
                selected.forEach { onItemLongClick(categories.indexOf(it)) }
            }
        } else {
            binding.emptyView.show(R.string.information_empty_tags)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.sort_tags, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_help -> {
                shownHelpDialog = true
                helpDialog()
            }
            else -> return false
        }
        return true
    }

    /**
     * Called when action mode is first created. The menu supplied will be used to generate action
     * buttons for the action mode.
     *
     * @param mode ActionMode being created.
     * @param menu Menu used to populate action buttons.
     * @return true if the action mode should be created, false if entering this mode should be
     *              aborted.
     */
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        // Inflate menu.
        mode.menuInflater.inflate(R.menu.category_selection, menu)
        // Enable adapter multi selection.
        adapter?.mode = SelectableAdapter.Mode.MULTI
        return true
    }

    /**
     * Called to refresh an action mode's action menu whenever it is invalidated.
     *
     * @param mode ActionMode being prepared.
     * @param menu Menu used to populate action buttons.
     * @return true if the menu or action mode was updated, false otherwise.
     */
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val adapter = adapter ?: return false
        val count = adapter.selectedItemCount
        mode.title = count.toString()

        // Show edit button only when one item is selected
        mode.menu.findItem(R.id.action_edit).isVisible = false
        return true
    }

    /**
     * Called to report a user click on an action button.
     *
     * @param mode The current ActionMode.
     * @param item The item that was clicked.
     * @return true if this callback handled the event, false if the standard MenuItem invocation
     *              should continue.
     */
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val adapter = adapter ?: return false

        when (item.itemId) {
            R.id.action_delete -> {
                undoHelper = UndoHelper(adapter, this)
                undoHelper?.start(
                    adapter.selectedPositions,
                    (activity as? MainActivity)?.binding?.rootCoordinator!!,
                    R.string.snack_tags_deleted,
                    R.string.action_undo,
                    3000
                )
                mode.finish()
            }
            else -> return false
        }
        return true
    }

    private fun helpDialog(hasPositive: Boolean = false) {
        MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.add_tag)
            .setMessage(R.string.action_add_tags_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (hasPositive) {
                    SortTagCreateDialog(this@SortTagController).showDialog(router, null)
                }
            }
            .show()
    }

    /**
     * Called when an action mode is about to be exited and destroyed.
     *
     * @param mode The current ActionMode being destroyed.
     */
    override fun onDestroyActionMode(mode: ActionMode) {
        // Reset adapter to single selection
        adapter?.mode = SelectableAdapter.Mode.IDLE
        adapter?.clearSelection()
        actionMode = null
    }

    /**
     * Called when an item in the list is clicked.
     *
     * @param position The position of the clicked item.
     * @return true if this click should enable selection mode.
     */
    override fun onItemClick(view: View, position: Int): Boolean {
        // Check if action mode is initialized and selected item exist.
        return if (actionMode != null && position != RecyclerView.NO_POSITION) {
            toggleSelection(position)
            true
        } else {
            false
        }
    }

    /**
     * Called when an item in the list is long clicked.
     *
     * @param position The position of the clicked item.
     */
    override fun onItemLongClick(position: Int) {
        val activity = activity as? AppCompatActivity ?: return

        // Check if action mode is initialized.
        if (actionMode == null) {
            // Initialize action mode
            actionMode = activity.startSupportActionMode(this)
        }

        // Set item as selected
        toggleSelection(position)
    }

    /**
     * Toggle the selection state of an item.
     * If the item was the last one in the selection and is unselected, the ActionMode is finished.
     *
     * @param position The position of the item to toggle.
     */
    private fun toggleSelection(position: Int) {
        val adapter = adapter ?: return

        // Mark the position selected
        adapter.toggleSelection(position)

        if (adapter.selectedItemCount == 0) {
            actionMode?.finish()
        } else {
            actionMode?.invalidate()
        }
    }

    /**
     * Called when an item is released from a drag.
     *
     * @param position The position of the released item.
     */
    override fun onItemReleased(position: Int) {
        val adapter = adapter ?: return
        val tags = (0 until adapter.itemCount).mapNotNull { adapter.getItem(it)?.tag }
        presenter.reorderTags(tags)
    }

    /**
     * Called when the undo action is clicked in the snackbar.
     *
     * @param action The action performed.
     */
    override fun onActionCanceled(action: Int, positions: MutableList<Int>?) {
        adapter?.restoreDeletedItems()
        undoHelper = null
    }

    /**
     * Called when the time to restore the items expires.
     *
     * @param action The action performed.
     * @param event The event that triggered the action
     */
    override fun onActionConfirmed(action: Int, event: Int) {
        val adapter = adapter ?: return
        presenter.deleteTags(adapter.deletedItems.map { it.tag })
        undoHelper = null
    }

    /**
     * Creates a new category with the given name.
     *
     * @param name The name of the new category.
     */
    override fun createCategory(name: String) {
        presenter.createTag(name)
    }

    /**
     * Called from the presenter when a category with the given name already exists.
     */
    fun onTagExistsError() {
        activity?.toast(R.string.error_tag_exists)
    }
}
