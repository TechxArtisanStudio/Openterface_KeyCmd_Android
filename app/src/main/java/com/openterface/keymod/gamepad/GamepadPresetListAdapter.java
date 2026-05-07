package com.openterface.keymod.gamepad;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

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

        void onOverflow(@NonNull String id, @NonNull View anchor);
    }

    private static final class Row {
        @NonNull final String id;
        @NonNull final String title;
        final boolean selected;

        Row(@NonNull String id, @NonNull String title, boolean selected) {
            this.id = id;
            this.title = title;
            this.selected = selected;
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
                    && id.equals(row.id)
                    && title.equals(row.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, title, selected);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    @Nullable
    private final Listener listener;

    public GamepadPresetListAdapter(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setData(@NonNull List<GamepadLayoutPresetRepository.PresetRef> presets,
                        @Nullable String activeId) {
        List<Row> newRows = new ArrayList<>(presets.size());
        for (GamepadLayoutPresetRepository.PresetRef r : presets) {
            if (r == null || r.id == null) {
                continue;
            }
            String title = r.displayName != null && !r.displayName.isEmpty() ? r.displayName : r.id;
            boolean selected = activeId != null && activeId.equals(r.id);
            newRows.add(new Row(r.id, title, selected));
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
        h.check.setVisibility(row.selected ? View.VISIBLE : View.INVISIBLE);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onActivatePreset(row.id);
            }
        });
        h.save.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSavePreset(row.id);
            }
        });
        h.share.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSharePreset(row.id);
            }
        });
        h.overflow.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOverflow(row.id, h.overflow);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final AppCompatImageView check;
        final TextView title;
        final AppCompatImageButton save;
        final AppCompatImageButton share;
        final AppCompatImageButton overflow;

        VH(@NonNull View itemView) {
            super(itemView);
            check = itemView.findViewById(R.id.preset_row_check);
            title = itemView.findViewById(R.id.preset_row_title);
            save = itemView.findViewById(R.id.preset_row_save);
            share = itemView.findViewById(R.id.preset_row_share);
            overflow = itemView.findViewById(R.id.preset_row_overflow);
        }
    }
}
