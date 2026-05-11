package com.openterface.keymod.gamepad;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import com.openterface.keymod.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Card grid for the gamepad Layouts picker: preview image, metadata, overflow, optional reorder handles.
 */
public final class GamepadPresetCardGridAdapter extends RecyclerView.Adapter<GamepadPresetCardGridAdapter.VH> {

    public interface Listener {
        void onActivatePreset(@NonNull String id);

        void onSavePreset(@NonNull String id);

        void onSharePreset(@NonNull String id);

        void onDeletePreset(@NonNull String id);

        void onOverflow(@NonNull String id, @NonNull View anchor);

        void onPresetReorderFinished();
    }

    private static final class Row {
        @NonNull final String id;
        @NonNull final String title;
        final boolean selected;
        final boolean deletable;
        final boolean builtin;

        Row(@NonNull String id, @NonNull String title, boolean selected, boolean deletable, boolean builtin) {
            this.id = id;
            this.title = title;
            this.selected = selected;
            this.deletable = deletable;
            this.builtin = builtin;
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
                    && builtin == row.builtin
                    && id.equals(row.id)
                    && title.equals(row.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, title, selected, deletable, builtin);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    @Nullable
    private final Listener listener;
    @NonNull
    private final GamepadLayoutPresetRepository repository;
    @NonNull
    private final GamepadLayoutPreviewCache previewCache;
    private boolean reorderMode;
    private boolean reorderPending;
    private int manualDragAnchorPos = RecyclerView.NO_POSITION;

    public GamepadPresetCardGridAdapter(
            @Nullable Listener listener,
            @NonNull GamepadLayoutPresetRepository repository,
            @NonNull GamepadLayoutPreviewCache previewCache) {
        this.listener = listener;
        this.repository = repository;
        this.previewCache = previewCache;
        setHasStableIds(true);
    }

    public void setReorderMode(boolean enabled) {
        if (reorderMode == enabled) {
            return;
        }
        reorderMode = enabled;
        notifyDataSetChanged();
    }

    public boolean isReorderMode() {
        return reorderMode;
    }

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
        manualDragAnchorPos = RecyclerView.NO_POSITION;
        List<Row> newRows = new ArrayList<>(presets.size());
        for (GamepadLayoutPresetRepository.PresetRef r : presets) {
            if (r == null || r.id == null) {
                continue;
            }
            String title = r.displayName != null && !r.displayName.isEmpty() ? r.displayName : r.id;
            boolean selected = activeId != null && activeId.equals(r.id);
            boolean deletable = !GamepadLayoutPresetConstants.isPresetDeletionProtected(r.id);
            boolean builtin = GamepadLayoutPresetConstants.isPresetDeletionProtected(r.id)
                    || GamepadLayoutPresetConstants.isBundledPackPresetId(r.id);
            newRows.add(new Row(r.id, title, selected, deletable, builtin));
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

    @Override
    public long getItemId(int position) {
        return rows.get(position).id.hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gamepad_preset_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row row = rows.get(position);
        Context ctx = h.itemView.getContext();
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

        h.activeBadge.setVisibility(row.selected ? View.VISIBLE : View.GONE);
        h.builtinBadge.setVisibility(row.builtin ? View.VISIBLE : View.GONE);

        float density = ctx.getResources().getDisplayMetrics().density;
        int strokePx = row.selected ? Math.round(2f * density) : 0;
        h.card.setStrokeWidth(strokePx);
        if (row.selected) {
            h.card.setStrokeColor(ColorStateList.valueOf(primary));
        } else {
            h.card.setStrokeColor(ColorStateList.valueOf(Color.TRANSPARENT));
        }

        h.more.setTextColor(onSurface);

        h.card.setOnClickListener(v -> {
            if (reorderMode) {
                return;
            }
            if (listener != null) {
                listener.onActivatePreset(row.id);
            }
        });
        h.more.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOverflow(row.id, h.more);
            }
        });

        boolean showReorderHandle = reorderMode && rows.size() > 1;
        h.dragHandle.setVisibility(showReorderHandle ? View.VISIBLE : View.GONE);
        if (showReorderHandle) {
            h.dragHandle.setOnTouchListener((v, event) -> {
                ViewParentRv parentRv = ViewParentRv.from(h.itemView);
                if (parentRv == null) {
                    return false;
                }
                RecyclerView rv = parentRv.recyclerView;
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    int pos = h.getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) {
                        return false;
                    }
                    manualDragAnchorPos = pos;
                    rv.requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    if (manualDragAnchorPos == RecyclerView.NO_POSITION) {
                        return true;
                    }
                    int[] loc = new int[2];
                    rv.getLocationOnScreen(loc);
                    float x = event.getRawX() - loc[0];
                    float y = event.getRawY() - loc[1];
                    View under = rv.findChildViewUnder(x, y);
                    if (under == null) {
                        return true;
                    }
                    int targetPos = rv.getChildAdapterPosition(under);
                    if (targetPos == RecyclerView.NO_POSITION || targetPos == manualDragAnchorPos) {
                        return true;
                    }
                    moveItem(manualDragAnchorPos, targetPos);
                    manualDragAnchorPos = targetPos;
                    return true;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    rv.requestDisallowInterceptTouchEvent(false);
                    if (manualDragAnchorPos != RecyclerView.NO_POSITION) {
                        manualDragAnchorPos = RecyclerView.NO_POSITION;
                        if (reorderPending && listener != null) {
                            listener.onPresetReorderFinished();
                        }
                    }
                    return true;
                }
                return true;
            });
        } else {
            h.dragHandle.setOnTouchListener(null);
        }

        h.preview.setImageDrawable(null);
        long sig = repository.presetFileContentSignature(row.id);
        final int boundPosition = position;
        previewCache.loadPreview(row.id, sig, bmp -> {
            if (h.getBindingAdapterPosition() != boundPosition) {
                return;
            }
            if (bmp != null) {
                h.preview.setImageBitmap(bmp);
            } else {
                h.preview.setImageBitmap(null);
            }
        });

        StringBuilder a11y = new StringBuilder(row.title);
        if (row.builtin) {
            a11y.append(". ").append(ctx.getString(R.string.gamepad_presets_builtin_badge));
        }
        if (row.selected) {
            a11y.append(". ").append(ctx.getString(R.string.gamepad_presets_active_badge));
        }
        if (reorderMode) {
            a11y.append(". ").append(ctx.getString(R.string.gamepad_presets_card_a11y_reorder_suffix));
        } else {
            a11y.append(". ")
                    .append(ctx.getString(R.string.gamepad_presets_more))
                    .append(": ")
                    .append(ctx.getString(R.string.gamepad_preset_row_menu_cd))
                    .append(". ")
                    .append(ctx.getString(R.string.gamepad_presets_card_a11y_activate));
        }
        h.card.setContentDescription(a11y.toString());
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        holder.preview.setImageBitmap(null);
        holder.dragHandle.setOnTouchListener(null);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private static final class ViewParentRv {
        final RecyclerView recyclerView;

        ViewParentRv(RecyclerView recyclerView) {
            this.recyclerView = recyclerView;
        }

        @Nullable
        static ViewParentRv from(@NonNull View itemView) {
            ViewParent p = itemView.getParent();
            if (p instanceof RecyclerView) {
                return new ViewParentRv((RecyclerView) p);
            }
            return null;
        }
    }

    static final class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView preview;
        final TextView title;
        final TextView activeBadge;
        final TextView builtinBadge;
        final AppCompatImageView dragHandle;
        final MaterialButton more;

        VH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.preset_card_root);
            preview = itemView.findViewById(R.id.preset_card_preview);
            title = itemView.findViewById(R.id.preset_card_title);
            activeBadge = itemView.findViewById(R.id.preset_card_active_badge);
            builtinBadge = itemView.findViewById(R.id.preset_card_builtin_chip);
            dragHandle = itemView.findViewById(R.id.preset_card_drag_handle);
            more = itemView.findViewById(R.id.preset_card_more);
        }
    }
}
