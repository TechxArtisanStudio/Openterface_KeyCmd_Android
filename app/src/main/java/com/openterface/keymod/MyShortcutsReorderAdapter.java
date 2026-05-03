package com.openterface.keymod;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.openterface.keymod.util.ShortcutFavoriteRowViews;

import java.util.List;

/**
 * Vertical list of My shortcuts with drag-handle reordering (keyboard sheet + Shortcut Hub).
 */
public class MyShortcutsReorderAdapter extends RecyclerView.Adapter<MyShortcutsReorderAdapter.VH> {

    public interface RowInteraction {
        void onRowClick(ShortcutProfileManager.Shortcut shortcut, @NonNull View rowContent);

        void onRowLongClick(ShortcutProfileManager.Shortcut shortcut);
    }

    public interface OnEditShortcutClickListener {
        void onEditClick(@NonNull ShortcutProfileManager.Shortcut shortcut);
    }

    private final String targetOs;
    private final List<ShortcutProfileManager.Shortcut> items;
    private ItemTouchHelper dragHelper;
    private RowInteraction rowInteraction;
    private OnRemoveFavoriteClickListener removeFavoriteClickListener;
    @Nullable
    private OnEditShortcutClickListener editShortcutClickListener;

    public interface OnRemoveFavoriteClickListener {
        void onRemoveFavoriteClick(@NonNull ShortcutProfileManager.Shortcut shortcut, int position);
    }

    public MyShortcutsReorderAdapter(Context context, String targetOs,
            List<ShortcutProfileManager.Shortcut> items) {
        this.targetOs = targetOs != null ? targetOs : "macos";
        this.items = items;
    }

    public void setDragHelper(ItemTouchHelper helper) {
        this.dragHelper = helper;
    }

    public void setRowInteraction(RowInteraction rowInteraction) {
        this.rowInteraction = rowInteraction;
    }

    public void setRemoveFavoriteClickListener(@Nullable OnRemoveFavoriteClickListener listener) {
        this.removeFavoriteClickListener = listener;
    }

    public void setOnEditShortcutClickListener(@Nullable OnEditShortcutClickListener listener) {
        this.editShortcutClickListener = listener;
    }

    public List<ShortcutProfileManager.Shortcut> getItems() {
        return items;
    }

    public void replaceItems(List<ShortcutProfileManager.Shortcut> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }
        ShortcutProfileManager.Shortcut s = items.remove(fromPosition);
        items.add(toPosition, s);
        notifyItemMoved(fromPosition, toPosition);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_shortcuts_reorder_row_compact, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ShortcutProfileManager.Shortcut shortcut = items.get(position);
        ShortcutFavoriteRowViews.bindFavoriteStripRow(
                holder.itemView.getContext(), holder.content, shortcut, targetOs);
        if (dragHelper != null) {
            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragHelper.startDrag(holder);
                }
                return false;
            });
        } else {
            holder.dragHandle.setOnTouchListener(null);
        }
        holder.content.setOnClickListener(v -> {
            if (rowInteraction != null) {
                rowInteraction.onRowClick(shortcut, holder.content);
            }
        });
        holder.content.setOnLongClickListener(v -> {
            if (rowInteraction != null) {
                rowInteraction.onRowLongClick(shortcut);
                return true;
            }
            return false;
        });
        if (editShortcutClickListener != null) {
            holder.editShortcut.setVisibility(View.VISIBLE);
            holder.editShortcut.setOnClickListener(v -> editShortcutClickListener.onEditClick(shortcut));
        } else {
            holder.editShortcut.setVisibility(View.GONE);
            holder.editShortcut.setOnClickListener(null);
        }
        if (removeFavoriteClickListener != null) {
            holder.removeFavorite.setVisibility(View.VISIBLE);
            holder.removeFavorite.setOnClickListener(v ->
                    removeFavoriteClickListener.onRemoveFavoriteClick(shortcut, holder.getBindingAdapterPosition()));
        } else {
            holder.removeFavorite.setVisibility(View.GONE);
            holder.removeFavorite.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView dragHandle;
        final View content;
        final ImageView editShortcut;
        final ImageView removeFavorite;

        VH(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.reorder_drag_handle);
            content = itemView.findViewById(R.id.reorder_row_content);
            editShortcut = itemView.findViewById(R.id.reorder_edit_shortcut);
            removeFavorite = itemView.findViewById(R.id.reorder_remove_favorite);
        }
    }
}
