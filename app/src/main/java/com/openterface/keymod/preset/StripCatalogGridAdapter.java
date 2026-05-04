package com.openterface.keymod.preset;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.ShortcutProfileManager.Shortcut;
import com.openterface.keymod.util.KeyParser;
import com.openterface.keymod.util.ShortcutFavoriteRowViews;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Grid strip catalog: page / row band headers span two columns; slot cells use
 * {@link StripCatalogGridItem#VIEW_TYPE_SLOT_CELL}.
 */
public class StripCatalogGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnStripCatalogCellClickListener {
        void onStripCatalogCellClick(@NonNull StripCatalogGridItem slotCell, @Nullable String shortcutId);
    }

    @Nullable
    private OnStripCatalogCellClickListener cellClickListener;

    private final Context appContext;
    private final ShortcutProfileManager profileManager;
    @Nullable
    private final Rows23StripProfileManager rows23StripProfileManager;
    @Nullable
    private final String stripProfileIdForOverrides;
    private final StripSlotMapStore legacySlotMapStore;
    private final String targetOs;
    private final List<StripCatalogGridItem> items = new ArrayList<>();
    @Nullable
    private ItemTouchHelper stripSlotItemTouchHelper;

    public StripCatalogGridAdapter(
            @NonNull Context context,
            @NonNull ShortcutProfileManager profileManager,
            @Nullable Rows23StripProfileManager rows23StripProfileManager,
            @Nullable String stripProfileIdForOverrides,
            @NonNull String targetOs
    ) {
        this.appContext = context.getApplicationContext();
        this.profileManager = profileManager;
        this.rows23StripProfileManager = rows23StripProfileManager;
        this.stripProfileIdForOverrides = stripProfileIdForOverrides;
        this.legacySlotMapStore = new StripSlotMapStore(context);
        this.targetOs = (targetOs != null && !targetOs.isEmpty()) ? targetOs : "macos";
    }

    public void setOnStripCatalogCellClickListener(@Nullable OnStripCatalogCellClickListener listener) {
        this.cellClickListener = listener;
    }

    public void setStripSlotItemTouchHelper(@Nullable ItemTouchHelper helper) {
        this.stripSlotItemTouchHelper = helper;
    }

    public void setItems(@NonNull List<StripCatalogGridItem> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @Nullable
    public StripCatalogGridItem getItem(int adapterPosition) {
        if (adapterPosition < 0 || adapterPosition >= items.size()) {
            return null;
        }
        return items.get(adapterPosition);
    }

    @Nullable
    public StripCatalogGridItem getSlotCellAt(int adapterPosition) {
        StripCatalogGridItem it = getItem(adapterPosition);
        if (it == null || it.viewType != StripCatalogGridItem.VIEW_TYPE_SLOT_CELL) {
            return null;
        }
        return it;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == StripCatalogGridItem.VIEW_TYPE_PAGE_HEADER) {
            View v = inflater.inflate(R.layout.item_strip_catalog_grid_page_header, parent, false);
            return new PageHeaderVH(v);
        }
        if (viewType == StripCatalogGridItem.VIEW_TYPE_ROW_BAND_HEADER) {
            View v = inflater.inflate(R.layout.item_strip_catalog_grid_row_header, parent, false);
            return new RowBandVH(v);
        }
        View v = inflater.inflate(R.layout.item_strip_catalog_grid_cell, parent, false);
        return new CellVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        StripCatalogGridItem item = items.get(position);
        if (holder instanceof PageHeaderVH) {
            PageHeaderVH h = (PageHeaderVH) holder;
            h.title.setText(item.pageTitle != null ? item.pageTitle : "");
            if (TextUtils.isEmpty(item.pageSubtitle)) {
                h.subtitle.setVisibility(View.GONE);
            } else {
                h.subtitle.setVisibility(View.VISIBLE);
                h.subtitle.setText(item.pageSubtitle);
            }
        } else if (holder instanceof RowBandVH) {
            ((RowBandVH) holder).title.setText(item.rowBandTitle != null ? item.rowBandTitle : "");
        } else if (holder instanceof CellVH) {
            bindCell((CellVH) holder, item, holder);
        }
    }

    private void bindCell(@NonNull CellVH h, @NonNull StripCatalogGridItem item, @NonNull RecyclerView.ViewHolder holder) {
        Context rowCtx = holder.itemView.getContext();
        h.layer.setText(item.layerLabel != null ? item.layerLabel : "");
        if (item.pageIndex >= 0 && item.stripRow >= 0 && item.col >= 0 && !TextUtils.isEmpty(item.slotKey)) {
            h.slotId.setVisibility(View.VISIBLE);
            h.slotId.setText(item.slotKey);
        } else {
            h.slotId.setVisibility(View.GONE);
            h.slotId.setText("");
        }
        h.physical.setVisibility(View.GONE);

        if (TextUtils.isEmpty(item.latchHintLine)) {
            h.latchHint.setVisibility(View.GONE);
        } else {
            h.latchHint.setVisibility(View.VISIBLE);
            h.latchHint.setText(item.latchHintLine);
        }
        String slotKey = item.slotKey != null ? item.slotKey : "";
        h.meta.setVisibility(View.GONE);

        Map<String, String> map = resolveSlotMapForSummary();
        boolean hasCustom = slotMapHasShortcut(map, slotKey);
        if (hasCustom) {
            h.status.setVisibility(View.VISIBLE);
            h.status.setText(R.string.shortcut_hub_strip_slot_status_custom);
            int onPrimary = MaterialColors.getColor(h.status, com.google.android.material.R.attr.colorOnPrimaryContainer,
                    androidx.core.content.ContextCompat.getColor(appContext, R.color.text_primary));
            int container = MaterialColors.getColor(h.status, com.google.android.material.R.attr.colorPrimaryContainer,
                    androidx.core.content.ContextCompat.getColor(appContext, R.color.text_secondary));
            h.status.setBackgroundColor(container);
            h.status.setTextColor(onPrimary);
        } else {
            h.status.setVisibility(View.GONE);
        }

        Shortcut sc = resolveShortcutForSlotKey(slotKey);
        if (sc != null) {
            ShortcutFavoriteRowViews.bindFavoriteStripRow(rowCtx, h.favoriteRow, sc, targetOs);
            TextView nameTv = h.favoriteRow.findViewById(R.id.favorite_row_name);
            TextView chordTv = h.favoriteRow.findViewById(R.id.favorite_row_chord);
            String chord = KeyParser.toLabelForTargetOs(sc.keyCode, sc.modifiers, targetOs);
            if (sc.label != null && !sc.label.trim().isEmpty()) {
                String fromLabel = KeyParser.displayLabel(sc.label.trim(), targetOs);
                if (!KeyParser.isUnparsedKeyTokenLabel(fromLabel)) {
                    chord = fromLabel;
                }
            }
            String factoryCap = item.physicalLabel != null ? item.physicalLabel.trim() : "";
            String userName = sc.name != null ? sc.name.trim() : "";
            boolean userNameMeaningful = !userName.isEmpty()
                    && !userName.equalsIgnoreCase(factoryCap)
                    && !userName.equalsIgnoreCase(chord);
            nameTv.setVisibility(View.VISIBLE);
            nameTv.setText(userNameMeaningful ? userName : chord);
            // Do not show factory cap (e.g. F7–F9) as secondary: it is the unlatched catalog hint, not the
            // customized Fn-layer assignment, and reads as wrong after an override. Secondary only when
            // the user set a custom title (then show chord on the right).
            String secondary = userNameMeaningful ? chord : "";
            chordTv.setText(secondary);
            chordTv.setVisibility(secondary.isEmpty() ? View.GONE : View.VISIBLE);
            chordTv.setTypeface(userNameMeaningful ? Typeface.MONOSPACE : Typeface.DEFAULT);
            tuneFavoriteRowForGridCell(h.favoriteRow);
        } else {
            bindPhysicalKeyFavoriteRow(rowCtx, h.favoriteRow, item, targetOs);
            tuneFavoriteRowForGridCell(h.favoriteRow);
        }

        String sid = map.get(slotKey);
        if (sid != null) {
            sid = sid.trim();
            if (sid.isEmpty()) {
                sid = null;
            }
        }
        String sidFinal = sid;
        View.OnClickListener open = v -> {
            if (cellClickListener != null && item.slotKey != null) {
                cellClickListener.onStripCatalogCellClick(item, sidFinal);
            }
        };
        h.favoriteRow.setOnClickListener(open);
        h.layer.setOnClickListener(open);
        h.slotId.setOnClickListener(open);
        h.latchHint.setOnClickListener(open);
        h.status.setOnClickListener(open);
        holder.itemView.setOnClickListener(open);

        boolean canReorder = stripSlotItemTouchHelper != null
                && rows23StripProfileManager != null
                && stripProfileIdForOverrides != null;
        if (canReorder) {
            h.dragHandle.setVisibility(View.VISIBLE);
            // Return false so MOVE/UP reach RecyclerView; ItemTouchHelper needs the stream to update drop target.
            h.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    stripSlotItemTouchHelper.startDrag(holder);
                }
                return false;
            });
        } else {
            h.dragHandle.setVisibility(View.GONE);
            h.dragHandle.setOnTouchListener(null);
        }
    }

    private static void bindPhysicalKeyFavoriteRow(
            @NonNull Context ctx,
            @NonNull View favoriteRow,
            @NonNull StripCatalogGridItem item,
            @NonNull String targetOs
    ) {
        android.widget.ImageView iconDrawable = favoriteRow.findViewById(R.id.favorite_row_icon_drawable);
        TextView iconEmoji = favoriteRow.findViewById(R.id.favorite_row_icon_emoji);
        TextView nameTv = favoriteRow.findViewById(R.id.favorite_row_name);
        TextView chordTv = favoriteRow.findViewById(R.id.favorite_row_chord);
        nameTv.setVisibility(View.VISIBLE);
        String label = item.physicalLabel != null ? item.physicalLabel : "";
        int iconRes = StripCatalogPhysicalKeyIcons.iconForSlot(targetOs, item.pageIndex, item.stripRow, item.col);
        int tint = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFF212121);
        if (iconRes != 0) {
            iconDrawable.setImageResource(iconRes);
            iconDrawable.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
            iconDrawable.setVisibility(View.VISIBLE);
            iconEmoji.setVisibility(View.GONE);
        } else {
            iconDrawable.setImageDrawable(null);
            iconDrawable.clearColorFilter();
            iconDrawable.setVisibility(View.GONE);
            if (label.isEmpty()) {
                iconEmoji.setText("\u00b7");
            } else {
                iconEmoji.setText(label);
            }
            iconEmoji.setMaxLines(1);
            iconEmoji.setEllipsize(TextUtils.TruncateAt.END);
            iconEmoji.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            iconEmoji.setVisibility(View.VISIBLE);
        }
        nameTv.setText(label);
        nameTv.setMaxLines(3);
        String keyEv = item.keyEventLabel != null ? item.keyEventLabel.trim() : "";
        chordTv.setTypeface(Typeface.DEFAULT);
        chordTv.setText(keyEv);
        chordTv.setVisibility(View.VISIBLE);
    }

    private static void tuneFavoriteRowForGridCell(@NonNull View favoriteRow) {
        TextView nameTv = favoriteRow.findViewById(R.id.favorite_row_name);
        TextView chordTv = favoriteRow.findViewById(R.id.favorite_row_chord);
        nameTv.setMaxLines(3);
        if (chordTv.getText() == null || chordTv.getText().toString().trim().isEmpty()) {
            chordTv.setVisibility(View.GONE);
        } else {
            chordTv.setVisibility(View.VISIBLE);
            chordTv.setMaxLines(3);
            int halfCellPx = favoriteRow.getContext().getResources().getDisplayMetrics().widthPixels / 2;
            chordTv.setMaxWidth(Math.max(160, halfCellPx - 72));
        }
    }

    private static boolean slotMapHasShortcut(@NonNull Map<String, String> map, @NonNull String key) {
        String v = map.get(key);
        if (v != null && !v.trim().isEmpty()) {
            return true;
        }
        String canon = StripSlotMapStore.canonicalSlotKeyOrSelf(key);
        if (!canon.equals(key)) {
            v = map.get(canon);
            return v != null && !v.trim().isEmpty();
        }
        return false;
    }

    @Nullable
    private Shortcut resolveShortcutForSlotKey(@NonNull String slotKey) {
        Map<String, String> map = resolveSlotMapForSummary();
        String sid = map.get(slotKey);
        if (sid == null || sid.trim().isEmpty()) {
            String canon = StripSlotMapStore.canonicalSlotKeyOrSelf(slotKey);
            sid = map.get(canon);
        }
        if (sid == null || sid.trim().isEmpty()) {
            return null;
        }
        return resolveShortcutForId(sid.trim());
    }

    @NonNull
    private Map<String, String> resolveSlotMapForSummary() {
        if (rows23StripProfileManager != null && stripProfileIdForOverrides != null) {
            Rows23StripProfile p = rows23StripProfileManager.getProfileById(stripProfileIdForOverrides);
            if (p != null && p.slotMap != null) {
                return p.slotMap;
            }
        }
        return legacySlotMapStore.getAll();
    }

    @Nullable
    private Shortcut resolveShortcutForId(@NonNull String sid) {
        if (rows23StripProfileManager != null && stripProfileIdForOverrides != null) {
            Shortcut s = rows23StripProfileManager.findShortcut(stripProfileIdForOverrides, sid);
            if (s != null) {
                return s;
            }
        }
        profileManager.ensureKeyboardStripLayoutProfile();
        ShortcutProfileManager.ShortcutProfile strip = profileManager.getProfileById(
                KeyboardStripPresetConstants.STRIP_PROFILE_ID);
        if (strip == null) {
            return null;
        }
        return profileManager.findShortcutInProfile(strip, sid);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class PageHeaderVH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        PageHeaderVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.strip_catalog_grid_page_title);
            subtitle = itemView.findViewById(R.id.strip_catalog_grid_page_subtitle);
        }
    }

    static final class RowBandVH extends RecyclerView.ViewHolder {
        final TextView title;

        RowBandVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.strip_catalog_grid_row_title);
        }
    }

    static final class CellVH extends RecyclerView.ViewHolder {
        final ImageView dragHandle;
        final TextView layer;
        final TextView slotId;
        final TextView physical;
        final TextView latchHint;
        final View favoriteRow;
        final TextView status;
        final TextView meta;
        CellVH(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.strip_catalog_cell_drag_handle);
            layer = itemView.findViewById(R.id.strip_catalog_cell_layer);
            slotId = itemView.findViewById(R.id.strip_catalog_cell_slot_id);
            physical = itemView.findViewById(R.id.strip_catalog_cell_physical);
            latchHint = itemView.findViewById(R.id.strip_catalog_cell_latch_hint);
            favoriteRow = itemView.findViewById(R.id.strip_catalog_cell_favorite_row);
            status = itemView.findViewById(R.id.strip_catalog_cell_status);
            meta = itemView.findViewById(R.id.strip_catalog_cell_meta);
        }
    }
}
