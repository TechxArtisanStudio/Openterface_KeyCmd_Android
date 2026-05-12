package com.openterface.fragment;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.compose.SavedTextItem;
import com.openterface.keymod.compose.SavedTextRepository;
import com.openterface.keymod.util.ComposeSendPreviewDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen overlay for browsing and managing saved IME compose texts (Keyboard &amp; Mouse Pro).
 */
public class ImeSavedTextFragment extends Fragment {

    public interface Host {
        @NonNull
        String readCurrentEditorText();

        void onLoadIntoEditor(@NonNull String content);

        void onSendSavedText(@NonNull String content);
    }

    @Nullable
    private OnBackPressedCallback rootBackCallback;
    @Nullable
    private ImageButton closeButton;
    @Nullable
    private Host host;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ime_saved_text, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Activity act = getActivity();
        if (!(act instanceof MainActivity)) {
            return;
        }
        MainActivity mainActivity = (MainActivity) act;
        host = mainActivity.getImeSavedTextHost();
        if (host == null) {
            mainActivity.hideImeSavedTextOverlay();
            return;
        }

        Context ctx = requireContext();
        closeButton = view.findViewById(R.id.ime_saved_text_close);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dismissImeSavedText());
        }

        rootBackCallback =
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        dismissImeSavedText();
                    }
                };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), rootBackCallback);

        SavedTextRepository repo = new SavedTextRepository(ctx);
        RecyclerView rv = view.findViewById(R.id.ime_saved_text_list);
        TextView empty = view.findViewById(R.id.ime_saved_text_empty);
        MaterialButton saveBtn = view.findViewById(R.id.ime_saved_text_save_current);
        MaterialButton previewBtn = view.findViewById(R.id.ime_saved_text_action_preview);
        MaterialButton loadBtn = view.findViewById(R.id.ime_saved_text_action_load);
        MaterialButton sendBtn = view.findViewById(R.id.ime_saved_text_action_send);

        List<SavedTextItem> items = new ArrayList<>(repo.loadSorted());
        Runnable dismissOverlay = this::dismissImeSavedText;
        Adapter adapter =
                new Adapter(
                        ctx,
                        items,
                        repo,
                        host,
                        view,
                        dismissOverlay,
                        hasSelection -> {
                            int visibility = hasSelection ? View.VISIBLE : View.GONE;
                            if (previewBtn != null) {
                                previewBtn.setVisibility(visibility);
                            }
                            if (loadBtn != null) {
                                loadBtn.setVisibility(visibility);
                            }
                            if (sendBtn != null) {
                                sendBtn.setVisibility(visibility);
                            }
                        });
        rv.setLayoutManager(new LinearLayoutManager(ctx));
        rv.setAdapter(adapter);
        refreshEmpty(empty, items);
        if (previewBtn != null) {
            previewBtn.setOnClickListener(v -> adapter.showPreviewForSelected());
        }
        if (loadBtn != null) {
            loadBtn.setOnClickListener(v -> adapter.loadSelectedIntoEditor());
        }
        if (sendBtn != null) {
            sendBtn.setOnClickListener(v -> adapter.sendSelected());
        }

        if (saveBtn != null) {
            saveBtn.setOnClickListener(
                    v -> {
                        SavedTextItem added = repo.addFromPlainText(host.readCurrentEditorText());
                        if (added == null) {
                            Toast.makeText(ctx, R.string.ime_saved_text_save_empty, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(ctx, R.string.ime_saved_text_saved, Toast.LENGTH_SHORT).show();
                        items.clear();
                        items.addAll(repo.loadSorted());
                        adapter.notifyDataSetChanged();
                        adapter.setSelectedItemId(added.id);
                        refreshEmpty(empty, items);
                        rv.scrollToPosition(0);
                    });
        }
    }

    private void dismissImeSavedText() {
        Activity a = getActivity();
        if (a instanceof MainActivity) {
            ((MainActivity) a).hideImeSavedTextOverlay();
        }
    }

    private static void refreshEmpty(@Nullable TextView emptyView, @NonNull List<SavedTextItem> items) {
        if (emptyView == null) {
            return;
        }
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        private final Context ctx;
        private final List<SavedTextItem> items;
        private final SavedTextRepository repo;
        private final Host host;
        private final View contentRoot;
        private final Runnable dismissOverlay;
        private final SelectionListener selectionListener;
        private long selectedItemId = -1L;

        interface SelectionListener {
            void onSelectionChanged(boolean hasSelection);
        }

        Adapter(
                @NonNull Context ctx,
                @NonNull List<SavedTextItem> items,
                @NonNull SavedTextRepository repo,
                @NonNull Host host,
                @NonNull View contentRoot,
                @NonNull Runnable dismissOverlay,
                @NonNull SelectionListener selectionListener) {
            this.ctx = ctx;
            this.items = items;
            this.repo = repo;
            this.host = host;
            this.contentRoot = contentRoot;
            this.dismissOverlay = dismissOverlay;
            this.selectionListener = selectionListener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row =
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_ime_saved_text_row, parent, false);
            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            SavedTextItem it = items.get(position);
            h.title.setText(it.title != null ? it.title : "");
            CharSequence rel =
                    DateUtils.getRelativeTimeSpanString(
                            it.updatedAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE);
            h.meta.setText(rel);
            h.preview.setText(previewOf(it.content));
            h.pinnedIcon.setVisibility(it.pinned ? View.VISIBLE : View.GONE);

            boolean selected = it.id == selectedItemId;
            int titleSelected =
                    MaterialColors.getColor(h.title, com.google.android.material.R.attr.colorPrimary, 0);
            int titleNormal =
                    MaterialColors.getColor(
                            h.title, com.google.android.material.R.attr.colorOnSurface, 0);
            h.title.setTextColor(selected ? titleSelected : titleNormal);
            int selectedBg =
                    MaterialColors.getColor(
                            h.itemView,
                            com.google.android.material.R.attr.colorSurfaceContainerHighest,
                            0);
            h.itemView.setBackgroundColor(selected ? selectedBg : Color.TRANSPARENT);
            h.itemView.setOnClickListener(
                    v -> {
                        if (selectedItemId == it.id) {
                            selectedItemId = -1L;
                        } else {
                            selectedItemId = it.id;
                        }
                        notifyDataSetChanged();
                        notifySelectionChanged();
                    });

            h.more.setOnClickListener(
                    v -> {
                        PopupMenu pm = new PopupMenu(ctx, h.more);
                        MenuInflater inflater = pm.getMenuInflater();
                        inflater.inflate(R.menu.menu_ime_saved_text_row, pm.getMenu());
                        if (it.pinned) {
                            pm.getMenu().findItem(R.id.ime_saved_row_menu_pin).setTitle(R.string.ime_saved_text_unpin);
                        } else {
                            pm.getMenu().findItem(R.id.ime_saved_row_menu_pin).setTitle(R.string.ime_saved_text_pin);
                        }
                        pm.setOnMenuItemClickListener(
                                item -> {
                                    int id = item.getItemId();
                                    if (id == R.id.ime_saved_row_menu_pin) {
                                        repo.setPinned(it.id, !it.pinned);
                                        reloadFromRepo();
                                        return true;
                                    }
                                    if (id == R.id.ime_saved_row_menu_rename) {
                                        showRenameDialog(it);
                                        return true;
                                    }
                                    if (id == R.id.ime_saved_row_menu_delete) {
                                        showDeleteConfirm(it);
                                        return true;
                                    }
                                    return false;
                                });
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            pm.setForceShowIcon(true);
                        }
                        pm.show();
                    });
        }

        private void showRenameDialog(@NonNull SavedTextItem it) {
            int pad = dp(20);
            EditText et = new EditText(ctx);
            et.setPadding(pad, dp(8), pad, dp(8));
            et.setText(it.title != null ? it.title : "");
            et.setSingleLine(true);
            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.ime_saved_text_rename_title)
                    .setView(et)
                    .setPositiveButton(
                            android.R.string.ok,
                            (d, w) -> {
                                CharSequence cs = et.getText();
                                repo.updateTitle(it.id, cs != null ? cs.toString() : "");
                                reloadFromRepo();
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void showDeleteConfirm(@NonNull SavedTextItem it) {
            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.ime_saved_text_delete_confirm_title)
                    .setMessage(R.string.ime_saved_text_delete_confirm_message)
                    .setPositiveButton(
                            android.R.string.ok,
                            (d, w) -> {
                                repo.delete(it.id);
                                reloadFromRepo();
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private int dp(int v) {
            return Math.round(v * ctx.getResources().getDisplayMetrics().density);
        }

        private void reloadFromRepo() {
            items.clear();
            items.addAll(repo.loadSorted());
            if (findById(selectedItemId) == null) {
                selectedItemId = -1L;
            }
            notifyDataSetChanged();
            notifySelectionChanged();
            TextView empty = contentRoot.findViewById(R.id.ime_saved_text_empty);
            ImeSavedTextFragment.refreshEmpty(empty, items);
        }

        void setSelectedItemId(long itemId) {
            selectedItemId = itemId;
            notifyDataSetChanged();
            notifySelectionChanged();
        }

        void showPreviewForSelected() {
            SavedTextItem it = requireSelectedOrToast();
            if (it == null) {
                return;
            }
            ComposeSendPreviewDialog.show(ctx, it.content != null ? it.content : "");
        }

        void loadSelectedIntoEditor() {
            SavedTextItem it = requireSelectedOrToast();
            if (it == null) {
                return;
            }
            host.onLoadIntoEditor(it.content != null ? it.content : "");
            dismissOverlay.run();
        }

        void sendSelected() {
            SavedTextItem it = requireSelectedOrToast();
            if (it == null) {
                return;
            }
            host.onSendSavedText(it.content != null ? it.content : "");
        }

        @Nullable
        private SavedTextItem requireSelectedOrToast() {
            SavedTextItem selected = findById(selectedItemId);
            if (selected == null) {
                Toast.makeText(ctx, R.string.ime_saved_text_select_first, Toast.LENGTH_SHORT).show();
            }
            return selected;
        }

        @Nullable
        private SavedTextItem findById(long id) {
            if (id < 0) {
                return null;
            }
            for (int i = 0; i < items.size(); i++) {
                SavedTextItem it = items.get(i);
                if (it.id == id) {
                    return it;
                }
            }
            return null;
        }

        private void notifySelectionChanged() {
            selectionListener.onSelectionChanged(findById(selectedItemId) != null);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        private static String previewOf(@Nullable String content) {
            if (content == null || content.isEmpty()) {
                return "";
            }
            String oneLine = content.replace('\n', ' ').replace('\r', ' ');
            if (oneLine.length() > 160) {
                return oneLine.substring(0, 160).trim() + "…";
            }
            return oneLine.trim();
        }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView meta;
            final TextView preview;
            final View pinnedIcon;
            final MaterialButton more;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.ime_saved_row_title);
                meta = itemView.findViewById(R.id.ime_saved_row_meta);
                preview = itemView.findViewById(R.id.ime_saved_row_preview);
                pinnedIcon = itemView.findViewById(R.id.ime_saved_row_pinned_icon);
                more = itemView.findViewById(R.id.ime_saved_row_more);
            }
        }
    }
}
