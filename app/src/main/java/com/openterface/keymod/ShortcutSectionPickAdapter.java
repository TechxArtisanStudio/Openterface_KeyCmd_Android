package com.openterface.keymod;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.openterface.keymod.prefs.ShortcutHubDetailUiPrefs;
import com.openterface.keymod.util.ShortcutFavoriteRowViews;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only list of profile shortcuts in browse tabs; bookmark control adds or removes from staged My.
 */
public class ShortcutSectionPickAdapter extends RecyclerView.Adapter<ShortcutSectionPickAdapter.VH> {

    /** RecyclerView must not recycle card rows as list rows (and vice versa) when toggling layout. */
    private static final int VIEW_TYPE_COMPACT_LIST = 0;
    private static final int VIEW_TYPE_HUB_LIST = 1;
    private static final int VIEW_TYPE_HUB_CARD = 2;

    public interface FavoriteMembershipChecker {
        boolean isInMyFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut);
    }

    public interface OnBookmarkActionListener {
        void onAddToFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut);

        void onRemoveFromFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut);
    }

    /** Optional: tap / long-press on the row body (not the bookmark or edit control). */
    public interface RowInteraction {
        void onRowClick(@NonNull ShortcutProfileManager.Shortcut shortcut, @NonNull View rowContent);

        void onRowLongClick(@NonNull ShortcutProfileManager.Shortcut shortcut);
    }

    public interface OnEditShortcutClickListener {
        void onEditClick(@NonNull ShortcutProfileManager.Shortcut shortcut);
    }

    /** Card layout: overflow menu (edit / favorite) instead of inline bookmark and edit. */
    public interface HubCardOverflowMenuRequestListener {
        void onHubCardOverflowRequested(
                @NonNull ShortcutProfileManager.Shortcut shortcut,
                @NonNull View anchor);
    }

    @NonNull
    private String targetOs;
    private final List<ShortcutProfileManager.Shortcut> items;
    @Nullable
    private FavoriteMembershipChecker favoriteChecker;
    @Nullable
    private OnBookmarkActionListener bookmarkListener;
    @Nullable
    private RowInteraction rowInteraction;
    @Nullable
    private OnEditShortcutClickListener editShortcutClickListener;
    @Nullable
    private ItemTouchHelper dragHelper;
    @Nullable
    private HubCardOverflowMenuRequestListener hubCardOverflowMenuRequestListener;

    /** When false, compact list + strip binding (e.g. reorder bottom sheet). */
    private boolean shortcutHubDetailEnabled;
    private boolean shortcutHubCardLayout;
    private int shortcutHubDisplayMode = ShortcutHubDetailUiPrefs.DISPLAY_NAME;

    public ShortcutSectionPickAdapter(Context context, String targetOs,
            List<ShortcutProfileManager.Shortcut> items) {
        this.targetOs = targetOs != null ? targetOs : "macos";
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    /**
     * Updates host OS used for modifier/chord labels in row binding. Call when {@code AppPrefs}
     * {@code target_os} changes without recreating the adapter.
     */
    public void setTargetOs(@Nullable String os) {
        String next = os != null ? os : "macos";
        if (next.equals(targetOs)) {
            return;
        }
        this.targetOs = next;
        notifyDataSetChanged();
    }

    public void setHubCardOverflowMenuRequestListener(
            @Nullable HubCardOverflowMenuRequestListener listener) {
        this.hubCardOverflowMenuRequestListener = listener;
    }

    /**
     * Shortcut Hub profile detail: list vs card and row display. When {@code enabled} is false,
     * uses compact list + {@link ShortcutFavoriteRowViews#bindFavoriteStripRow}.
     */
    public void setShortcutHubDetailPresentation(boolean enabled, boolean cardLayout, int displayMode) {
        shortcutHubDetailEnabled = enabled;
        shortcutHubCardLayout = cardLayout;
        shortcutHubDisplayMode = ShortcutHubDetailUiPrefs.clampDisplay(displayMode);
        notifyDataSetChanged();
    }

    public void setDragHelper(@Nullable ItemTouchHelper helper) {
        this.dragHelper = helper;
    }

    public void setItems(List<ShortcutProfileManager.Shortcut> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    @NonNull
    public List<ShortcutProfileManager.Shortcut> getItems() {
        return items;
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }
        ShortcutProfileManager.Shortcut s = items.remove(fromPosition);
        items.add(toPosition, s);
        notifyItemMoved(fromPosition, toPosition);
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

    public void setOnEditShortcutClickListener(@Nullable OnEditShortcutClickListener listener) {
        this.editShortcutClickListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        if (!shortcutHubDetailEnabled) {
            return VIEW_TYPE_COMPACT_LIST;
        }
        return shortcutHubCardLayout ? VIEW_TYPE_HUB_CARD : VIEW_TYPE_HUB_LIST;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout;
        if (viewType == VIEW_TYPE_HUB_CARD) {
            layout = R.layout.item_shortcut_section_pick_row_card;
        } else {
            layout = R.layout.item_shortcut_section_pick_row;
        }
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ShortcutProfileManager.Shortcut shortcut = items.get(position);
        if (shortcutHubDetailEnabled) {
            ShortcutFavoriteRowViews.bindShortcutHubDetailRow(
                    holder.itemView.getContext(),
                    holder.contentRow,
                    shortcut,
                    targetOs,
                    shortcutHubDisplayMode,
                    shortcutHubCardLayout);
        } else {
            ShortcutFavoriteRowViews.bindFavoriteStripRow(
                    holder.itemView.getContext(), holder.contentRow, shortcut, targetOs);
        }

        boolean hubCardChrome = shortcutHubDetailEnabled && shortcutHubCardLayout;
        if (hubCardChrome) {
            if (holder.bookmark != null) {
                holder.bookmark.setVisibility(View.GONE);
                holder.bookmark.setOnClickListener(null);
            }
            if (holder.editShortcut != null) {
                holder.editShortcut.setVisibility(View.GONE);
                holder.editShortcut.setOnClickListener(null);
            }
            if (holder.hubOverflow != null) {
                holder.hubOverflow.setVisibility(
                        hubCardOverflowMenuRequestListener != null ? View.VISIBLE : View.GONE);
                holder.hubOverflow.setOnClickListener(v -> {
                    if (hubCardOverflowMenuRequestListener != null) {
                        hubCardOverflowMenuRequestListener.onHubCardOverflowRequested(shortcut, v);
                    }
                });
            }
        } else {
            if (holder.hubOverflow != null) {
                holder.hubOverflow.setVisibility(View.GONE);
                holder.hubOverflow.setOnClickListener(null);
            }
            if (holder.bookmark != null) {
                holder.bookmark.setVisibility(View.VISIBLE);
                boolean inFavorites = favoriteChecker != null && favoriteChecker.isInMyFavorites(shortcut);
                holder.bookmark.setImageResource(
                        inFavorites ? R.drawable.ic_bookmark_star_24 : R.drawable.ic_bookmark_add_24);
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
            }
            if (editShortcutClickListener != null && holder.editShortcut != null) {
                holder.editShortcut.setVisibility(View.VISIBLE);
                holder.editShortcut.setOnClickListener(v -> editShortcutClickListener.onEditClick(shortcut));
            } else if (holder.editShortcut != null) {
                holder.editShortcut.setVisibility(View.GONE);
                holder.editShortcut.setOnClickListener(null);
            }
        }

        if (dragHelper != null) {
            holder.dragHandle.setVisibility(View.VISIBLE);
            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragHelper.startDrag(holder);
                }
                return false;
            });
        } else {
            holder.dragHandle.setVisibility(View.GONE);
            holder.dragHandle.setOnTouchListener(null);
        }

        holder.contentRow.setOnClickListener(v -> {
            if (rowInteraction != null) {
                rowInteraction.onRowClick(shortcut, holder.contentRow);
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
        final ImageView dragHandle;
        final View contentRow;
        @Nullable
        final ImageView editShortcut;
        @Nullable
        final AppCompatImageButton bookmark;
        @Nullable
        final AppCompatImageButton hubOverflow;

        VH(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.pick_row_drag_handle);
            contentRow = itemView.findViewById(R.id.pick_row_favorite_content);
            editShortcut = itemView.findViewById(R.id.pick_row_edit);
            bookmark = itemView.findViewById(R.id.pick_row_bookmark);
            hubOverflow = itemView.findViewById(R.id.hub_card_overflow);
        }
    }
}
