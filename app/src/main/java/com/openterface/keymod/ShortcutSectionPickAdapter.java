package com.openterface.keymod;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.RecyclerView;

import com.openterface.keymod.util.ShortcutFavoriteRowViews;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only list of profile shortcuts in browse tabs; bookmark control adds or removes from staged My.
 */
public class ShortcutSectionPickAdapter extends RecyclerView.Adapter<ShortcutSectionPickAdapter.VH> {

    public interface FavoriteMembershipChecker {
        boolean isInMyFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut);
    }

    public interface OnBookmarkActionListener {
        void onAddToFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut);

        void onRemoveFromFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut);
    }

    /** Optional: tap / long-press on the row body (not the bookmark). */
    public interface RowInteraction {
        void onRowClick(@NonNull ShortcutProfileManager.Shortcut shortcut);

        void onRowLongClick(@NonNull ShortcutProfileManager.Shortcut shortcut);
    }

    private final String targetOs;
    private final List<ShortcutProfileManager.Shortcut> items;
    @Nullable
    private FavoriteMembershipChecker favoriteChecker;
    @Nullable
    private OnBookmarkActionListener bookmarkListener;
    @Nullable
    private RowInteraction rowInteraction;

    public ShortcutSectionPickAdapter(Context context, String targetOs,
            List<ShortcutProfileManager.Shortcut> items) {
        this.targetOs = targetOs != null ? targetOs : "macos";
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    public void setItems(List<ShortcutProfileManager.Shortcut> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    public void setFavoriteMembershipChecker(@Nullable FavoriteMembershipChecker checker) {
        this.favoriteChecker = checker;
    }

    public void setBookmarkListener(@Nullable OnBookmarkActionListener listener) {
        this.bookmarkListener = listener;
    }

    public void setRowInteraction(@Nullable RowInteraction rowInteraction) {
        this.rowInteraction = rowInteraction;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shortcut_section_pick_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ShortcutProfileManager.Shortcut shortcut = items.get(position);
        ShortcutFavoriteRowViews.bindFavoriteStripRow(holder.itemView.getContext(), holder.contentRow, shortcut, targetOs);

        boolean inFavorites = favoriteChecker != null && favoriteChecker.isInMyFavorites(shortcut);
        holder.bookmark.setImageResource(inFavorites ? R.drawable.ic_bookmark_star_24 : R.drawable.ic_bookmark_add_24);
        holder.bookmark.setContentDescription(holder.bookmark.getContext().getString(
                inFavorites ? R.string.cd_remove_from_favorites : R.string.cd_add_to_favorites));

        holder.bookmark.setOnClickListener(v -> {
            if (bookmarkListener == null) {
                return;
            }
            boolean nowFavorite = favoriteChecker != null && favoriteChecker.isInMyFavorites(shortcut);
            if (nowFavorite) {
                bookmarkListener.onRemoveFromFavorites(shortcut);
            } else {
                bookmarkListener.onAddToFavorites(shortcut);
            }
        });

        holder.contentRow.setOnClickListener(v -> {
            if (rowInteraction != null) {
                rowInteraction.onRowClick(shortcut);
            }
        });
        holder.contentRow.setOnLongClickListener(v -> {
            if (rowInteraction != null) {
                rowInteraction.onRowLongClick(shortcut);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final View contentRow;
        final AppCompatImageButton bookmark;

        VH(@NonNull View itemView) {
            super(itemView);
            contentRow = itemView.findViewById(R.id.pick_row_favorite_content);
            bookmark = itemView.findViewById(R.id.pick_row_bookmark);
        }
    }
}
