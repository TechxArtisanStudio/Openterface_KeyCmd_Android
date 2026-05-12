package com.openterface.keymod.compose;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openterface.keymod.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom sheet UI for browsing / managing saved compose texts (Pro IME phase 1).
 */
public final class ImeSavedTextBottomSheet {

    public interface Host {
        @NonNull
        String readCurrentEditorText();

        void onLoadIntoEditor(@NonNull String content);

        void onSendSavedText(@NonNull String content);
    }

    private ImeSavedTextBottomSheet() {}

    public static void show(@NonNull FragmentActivity activity, @NonNull Host host) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View root = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_ime_saved_text, null);
        dialog.setContentView(root);

        SavedTextRepository repo = new SavedTextRepository(activity);
        RecyclerView rv = root.findViewById(R.id.ime_saved_text_list);
        TextView empty = root.findViewById(R.id.ime_saved_text_empty);
        MaterialButton saveBtn = root.findViewById(R.id.ime_saved_text_save_current);

        List<SavedTextItem> items = new ArrayList<>(repo.loadSorted());
        Adapter adapter = new Adapter(activity, items, repo, host, dialog);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setAdapter(adapter);
        refreshEmpty(empty, items);

        saveBtn.setOnClickListener(
                v -> {
                    SavedTextItem added = repo.addFromPlainText(host.readCurrentEditorText());
                    if (added == null) {
                        Toast.makeText(activity, R.string.ime_saved_text_save_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(activity, R.string.ime_saved_text_saved, Toast.LENGTH_SHORT).show();
                    items.clear();
                    items.addAll(repo.loadSorted());
                    adapter.notifyDataSetChanged();
                    refreshEmpty(empty, items);
                    rv.scrollToPosition(0);
                });

        dialog.show();
    }

    private static void refreshEmpty(@NonNull TextView emptyView, @NonNull List<SavedTextItem> items) {
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        private final Context ctx;
        private final List<SavedTextItem> items;
        private final SavedTextRepository repo;
        private final Host host;
        private final BottomSheetDialog dialog;

        Adapter(
                @NonNull Context ctx,
                @NonNull List<SavedTextItem> items,
                @NonNull SavedTextRepository repo,
                @NonNull Host host,
                @NonNull BottomSheetDialog dialog) {
            this.ctx = ctx;
            this.items = items;
            this.repo = repo;
            this.host = host;
            this.dialog = dialog;
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

            int primary =
                    MaterialColors.getColor(h.pin, com.google.android.material.R.attr.colorPrimary, 0);
            int onSurface =
                    MaterialColors.getColor(
                            h.pin, com.google.android.material.R.attr.colorOnSurfaceVariant, 0);
            h.pin.setIconResource(R.drawable.ic_bookmark_star_24);
            if (it.pinned) {
                h.pin.setIconTint(ColorStateList.valueOf(primary));
            } else {
                h.pin.setIconTint(ColorStateList.valueOf(onSurface));
            }
            h.pin.setOnClickListener(
                    v -> {
                        repo.setPinned(it.id, !it.pinned);
                        reloadFromRepo();
                    });

            h.load.setOnClickListener(
                    v -> {
                        String c = it.content != null ? it.content : "";
                        host.onLoadIntoEditor(c);
                        dialog.dismiss();
                    });

            h.send.setOnClickListener(v -> host.onSendSavedText(it.content != null ? it.content : ""));

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
            notifyDataSetChanged();
            TextView empty = dialog.findViewById(R.id.ime_saved_text_empty);
            if (empty != null) {
                ImeSavedTextBottomSheet.refreshEmpty(empty, items);
            }
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
            final MaterialButton pin;
            final MaterialButton load;
            final MaterialButton send;
            final MaterialButton more;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.ime_saved_row_title);
                meta = itemView.findViewById(R.id.ime_saved_row_meta);
                preview = itemView.findViewById(R.id.ime_saved_row_preview);
                pin = itemView.findViewById(R.id.ime_saved_row_pin);
                load = itemView.findViewById(R.id.ime_saved_row_load);
                send = itemView.findViewById(R.id.ime_saved_row_send);
                more = itemView.findViewById(R.id.ime_saved_row_more);
            }
        }
    }
}
