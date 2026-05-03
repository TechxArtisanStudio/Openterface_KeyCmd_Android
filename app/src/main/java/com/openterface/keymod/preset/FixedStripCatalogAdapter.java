package com.openterface.keymod.preset;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutProfileManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders {@link FixedStripLayoutCatalog.Row} items for Shortcut Hub strip tab.
 */
public class FixedStripCatalogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Context appContext;
    private final ShortcutProfileManager profileManager;
    private final StripSlotMapStore slotMapStore;
    private final List<FixedStripLayoutCatalog.Row> rows = new ArrayList<>();

    public FixedStripCatalogAdapter(@NonNull Context context, @NonNull ShortcutProfileManager profileManager) {
        this.appContext = context.getApplicationContext();
        this.profileManager = profileManager;
        this.slotMapStore = new StripSlotMapStore(context);
    }

    public void setRows(@NonNull List<FixedStripLayoutCatalog.Row> next) {
        rows.clear();
        rows.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == FixedStripLayoutCatalog.VIEW_TYPE_SECTION) {
            View v = inflater.inflate(R.layout.item_fixed_strip_catalog_section, parent, false);
            return new SectionVH(v);
        }
        View v = inflater.inflate(R.layout.item_fixed_strip_catalog_slot, parent, false);
        return new SlotVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        FixedStripLayoutCatalog.Row row = rows.get(position);
        if (holder instanceof SectionVH) {
            SectionVH h = (SectionVH) holder;
            h.title.setText(row.primary);
            if (TextUtils.isEmpty(row.secondary)) {
                h.subtitle.setVisibility(View.GONE);
            } else {
                h.subtitle.setVisibility(View.VISIBLE);
                h.subtitle.setText(row.secondary);
            }
        } else if (holder instanceof SlotVH) {
            SlotVH h = (SlotVH) holder;
            h.title.setText(row.primary);
            if (TextUtils.isEmpty(row.secondary)) {
                h.fnLine.setVisibility(View.GONE);
            } else {
                h.fnLine.setVisibility(View.VISIBLE);
                h.fnLine.setText(row.secondary);
            }
            String override = resolveOverrideSummary(row);
            if (TextUtils.isEmpty(override)) {
                h.overrideLine.setVisibility(View.GONE);
            } else {
                h.overrideLine.setVisibility(View.VISIBLE);
                h.overrideLine.setText(override);
            }
            String slotKey = StripSlotMapStore.slotKey(row.pageIndex, row.stripRow, row.col, false);
            h.meta.setText(slotKey);
        }
    }

    @Nullable
    private String resolveOverrideSummary(@NonNull FixedStripLayoutCatalog.Row row) {
        if (row.viewType != FixedStripLayoutCatalog.VIEW_TYPE_SLOT) {
            return null;
        }
        Map<String, String> map = slotMapStore.getAll();
        String baseKey = StripSlotMapStore.slotKey(row.pageIndex, row.stripRow, row.col, false);
        String fnKey = StripSlotMapStore.slotKey(row.pageIndex, row.stripRow, row.col, true);
        String sid = map.get(baseKey);
        if (sid == null) {
            sid = map.get(fnKey);
        }
        if (sid == null || sid.isEmpty()) {
            return null;
        }
        profileManager.ensureKeyboardStripLayoutProfile();
        ShortcutProfileManager.ShortcutProfile strip = profileManager.getProfileById(
                KeyboardStripPresetConstants.STRIP_PROFILE_ID);
        if (strip == null) {
            return appContext.getString(R.string.shortcut_hub_strip_catalog_override_id, sid);
        }
        ShortcutProfileManager.Shortcut sc = profileManager.findShortcutInProfile(strip, sid);
        if (sc != null && !TextUtils.isEmpty(sc.name)) {
            return appContext.getString(R.string.shortcut_hub_strip_catalog_override_named, sc.name);
        }
        return appContext.getString(R.string.shortcut_hub_strip_catalog_override_id, sid);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class SectionVH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        SectionVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.fixed_strip_section_title);
            subtitle = itemView.findViewById(R.id.fixed_strip_section_subtitle);
        }
    }

    static final class SlotVH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView fnLine;
        final TextView overrideLine;
        final TextView meta;

        SlotVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.fixed_strip_slot_title);
            fnLine = itemView.findViewById(R.id.fixed_strip_slot_fn);
            overrideLine = itemView.findViewById(R.id.fixed_strip_slot_override);
            meta = itemView.findViewById(R.id.fixed_strip_slot_meta);
        }
    }
}
