package it.niedermann.owncloud.notes.android;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.view.ActionMode;
import androidx.appcompat.view.ActionMode.Callback;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.android.activity.NotesListViewActivity;
import it.niedermann.owncloud.notes.android.fragment.AccountChooserDialogFragment;
import it.niedermann.owncloud.notes.model.DBNote;
import it.niedermann.owncloud.notes.model.ItemAdapter;
import it.niedermann.owncloud.notes.persistence.NoteSQLiteOpenHelper;
import it.niedermann.owncloud.notes.persistence.NoteServerSyncHelper.ViewProvider;
import it.niedermann.owncloud.notes.util.ICallback;

public class MultiSelectedActionModeCallback implements Callback {

    private Context context;
    private ViewProvider viewProvider;
    private NoteSQLiteOpenHelper db;
    private ActionMode actionMode;
    private ItemAdapter adapter;
    private RecyclerView recyclerView;
    private Runnable refreshLists;
    private FragmentManager fragmentManager;
    private SearchView searchView;

    public MultiSelectedActionModeCallback(
            Context context, ViewProvider viewProvider, NoteSQLiteOpenHelper db, ActionMode actionMode, ItemAdapter adapter, RecyclerView recyclerView, Runnable refreshLists, FragmentManager fragmentManager, SearchView searchView) {
        this.context = context;
        this.viewProvider = viewProvider;
        this.db = db;
        this.actionMode = actionMode;
        this.adapter = adapter;
        this.recyclerView = recyclerView;
        this.refreshLists = refreshLists;
        this.fragmentManager = fragmentManager;
        this.searchView = searchView;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        // inflate contextual menu
        mode.getMenuInflater().inflate(R.menu.menu_list_context_multiple, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    /**
     * @param mode ActionMode - used to close the Action Bar after all work is done.
     * @param item MenuItem - the item in the List that contains the Node
     * @return boolean
     */
    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_delete: {
                List<DBNote> deletedNotes = new ArrayList<>();
                List<Integer> selection = adapter.getSelected();
                for (Integer i : selection) {
                    DBNote note = (DBNote) adapter.getItem(i);
                    deletedNotes.add(db.getNote(note.getAccountId(), note.getId()));
                    db.deleteNoteAndSync(note.getId());
                }
                mode.finish(); // Action picked, so close the CAB
                //after delete selection has to be cleared
                searchView.setIconified(true);
                refreshLists.run();
                String deletedSnackbarTitle = deletedNotes.size() == 1
                        ? context.getString(R.string.action_note_deleted, deletedNotes.get(0).getTitle())
                        : context.getString(R.string.bulk_notes_deleted, deletedNotes.size());
                Snackbar.make(viewProvider.getView(), deletedSnackbarTitle, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_undo, (View v) -> {
                            db.getNoteServerSyncHelper().addCallbackPush(new ICallback() {
                                @Override
                                public void onFinish() {
                                    refreshLists.run();
                                }

                                @Override
                                public void onScheduled() {

                                }
                            });
                            for (DBNote deletedNote : deletedNotes) {
                                db.addNoteAndSync(deletedNote.getAccountId(), deletedNote);
                            }
                            refreshLists.run();
                            String restoreSnackbarTitle = deletedNotes.size() == 1
                                    ? context.getString(R.string.action_note_restored, deletedNotes.get(0).getTitle())
                                    : context.getString(R.string.bulk_notes_restored, deletedNotes.size());
                            Snackbar.make(viewProvider.getView(), restoreSnackbarTitle, Snackbar.LENGTH_SHORT)
                                    .show();
                        })
                        .show();
                return true;
            }
            case R.id.menu_move: {
                AccountChooserDialogFragment.newInstance().show(fragmentManager, NotesListViewActivity.class.getCanonicalName());
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        adapter.clearSelection(recyclerView);
        actionMode = null;
        adapter.notifyDataSetChanged();
    }
}
