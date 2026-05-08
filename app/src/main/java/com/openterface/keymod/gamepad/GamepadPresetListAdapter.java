package com.openterface.keymod.gamepad;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import com.openterface.keymod.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rows for the gamepad preset picker bottom sheet (activate + overflow per preset).
 * Uses {@link DiffUtil} so rename / activate updates animate minimally instead of full refresh.
 */
public final class GamepadPresetListAdapter extends RecyclerView.Adapter<GamepadPresetListAdapter.VH> {

    public interface Listener {
        void onActivatePreset(@NonNull String id);

        /** Save preset JSON to a user-chosen path (Storage Access Framework). */
        void onSavePreset(@NonNull String id);

        void onSharePreset(@NonNull String id);

        /** User tapped row delete; only invoked for presets that are not deletion-protected. */
        void onDeletePreset(@NonNull String id);

        void onOverflow(@NonNull String id, @NonNull View anchor);
    }

    private static final class Row {
        @NonNull final String id;
        @NonNull final String title;
        final boolean selected;
        final boolean deletable;

        Row(@NonNull String id, @NonNull String title, boolean selected, boolean deletable) {
            this.id = id;
            this.title = title;
            this.selected = selected;
            this.deletable = deletable;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Row)) {
                return false;
            }
            Row row = (Row) o;
            return selected == row.selected
                    && deletable == row.deletable
                    && id.equals(row.id)
                    && title.equals(row.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, title, selected, deletable);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    @Nullable
    private final Listener listener;
    @Nullable
    private ItemTouchHelper dragHelper;
    private boolean reorderPending;

    public GamepadPresetListAdapter(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setDragHelper(@Nullable ItemTouchHelper helper) {
        this.dragHelper = helper;
    }

    /**
     * Reorders the in-memory row list during a drag. Call {@link #consumePendingReorderIds()} after the
     * gesture ends to persist via {@link GamepadLayoutPresetRepository#reorderPresets}.
     */
    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= rows.size() || toPosition >= rows.size()) {
            return;
        }
        if (fromPosition == toPosition) {
            return;
        }
        Row item = rows.remove(fromPosition);
        rows.add(toPosition, item);
        notifyItemMoved(fromPosition, toPosition);
        reorderPending = true;
    }

    /**
     * @return ordered preset ids if the list changed during the last drag; otherwise {@code null}.
     */
    @Nullable
    public List<String> consumePendingReorderIds() {
        if (!reorderPending) {
            return null;
        }
        reorderPending = false;
        List<String> ids = new ArrayList<>(rows.size());
        for (Row r : rows) {
            ids.add(r.id);
        }
        return ids;
    }

    public void setData(@NonNull List<GamepadLayoutPresetRepository.PresetRef> presets,
                        @Nullable String activeId) {
        reorderPending = false;
        List<Row> newRows = new ArrayList<>(presets.size());
        for (GamepadLayoutPresetRepository.PresetRef r : presets) {
            if (r == null || r.id == null) {
                continue;
            }
            String title = r.displayName != null && !r.displayName.isEmpty() ? r.displayName : r.id;
            boolean selected = activeId != null && activeId.equals(r.id);
            boolean deletable = !GamepadLayoutPresetConstants.isPresetDeletionProtected(r.id);
            newRows.add(new Row(r.id, title, selected, deletable));
        }
        if (rows.isEmpty()) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
            return;
        }
        List<Row> oldRows = new ArrayList<>(rows);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldRows.size();
            }

            @Override
            public int getNewListSize() {
                return newRows.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldRows.get(oldItemPosition).id.equals(newRows.get(newItemPosition).id);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return oldRows.get(oldItemPosition).equals(newRows.get(newItemPosition));
            }
        });
        rows.clear();
        rows.addAll(newRows);
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gamepad_preset_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row row = rows.get(position);
        h.title.setText(row.title);

        int primary = MaterialColors.getColor(h.title, com.google.android.material.R.attr.colorPrimary);
        int onSurface = MaterialColors.getColor(h.title, com.google.android.material.R.attr.colorOnSurface);
        if (row.selected) {
            h.title.setTypeface(null, Typeface.BOLD);
            h.title.setTextColor(primary);
        } else {
            h.title.setTypeface(null, Typeface.NORMAL);
            h.title.setTextColor(onSurface);
        }

        h.check.setVisibility(row.selected ? View.VISIBLE : View.INVISIBLE);
        h.overflow.setIconTint(ColorStateList.valueOf(onSurface));

        h.row.setOnClickListener(v -> {
            if (listener != null) {
                listener.onActivatePreset(row.id);
            }
        });
        h.overflow.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOverflow(row.id, h.overflow);
            }
        });

        boolean showReorder = dragHelper != null && rows.size() > 1;
        h.dragHandle.setVisibility(showReorder ? View.VISIBLE : View.GONE);
        if (showReorder) {
            h.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragHelper.startDrag(h);
                }
                return false;
            });
        } else {
            h.dragHandle.setOnTouchListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final TextView title;
        final View check;
        final AppCompatImageView dragHandle;
        final MaterialButton overflow;

        VH(@NonNull View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.preset_row_root);
            check = itemView.findViewById(R.id.preset_row_check);
            title = itemView.findViewById(R.id.preset_row_title);
            dragHandle = itemView.findViewById(R.id.preset_row_drag_handle);
            overflow = itemView.findViewById(R.id.preset_row_overflow);
        }
    }
}
