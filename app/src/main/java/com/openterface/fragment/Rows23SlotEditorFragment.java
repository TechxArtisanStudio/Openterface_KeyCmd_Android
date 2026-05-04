package com.openterface.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.textfield.TextInputEditText;
import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.preset.FixedStripLayoutCatalog;
import com.openterface.keymod.preset.HidKeyCatalog;
import com.openterface.keymod.preset.IconCatalog;
import com.openterface.keymod.preset.Rows23StripProfile;
import com.openterface.keymod.preset.Rows23StripProfileManager;
import com.openterface.keymod.util.KeyParser;
import com.openterface.keymod.util.ShortcutFavoriteRowViews;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen editor for a single Rows 2–3 strip slot (name, icon, one HID key, persistence key display).
 * Visual language aligns with {@link com.openterface.keymod.CreateShortcutBottomSheet} / Edit shortcut.
 */
public class Rows23SlotEditorFragment extends Fragment {

    private static final String ARG_PROFILE_ID = "profileId";
    private static final String ARG_SLOT_KEY = "slotKey";
    private static final String ARG_SHORTCUT_ID = "shortcutId";
    private static final String ARG_FACTORY_CAP = "factoryCap";
    private static final String ARG_FACTORY_EVENT = "factoryEvent";
    private static final String ARG_TARGET_OS = "targetOs";

    @NonNull
    public static Rows23SlotEditorFragment newInstance(
            @NonNull String profileId,
            @NonNull String slotKey,
            @Nullable String shortcutId,
            @NonNull String factoryCap,
            @NonNull String factoryEvent,
            @NonNull String targetOs
    ) {
        Rows23SlotEditorFragment f = new Rows23SlotEditorFragment();
        Bundle b = new Bundle();
        b.putString(ARG_PROFILE_ID, profileId);
        b.putString(ARG_SLOT_KEY, slotKey);
        b.putString(ARG_SHORTCUT_ID, shortcutId != null ? shortcutId : "");
        b.putString(ARG_FACTORY_CAP, factoryCap);
        b.putString(ARG_FACTORY_EVENT, factoryEvent);
        b.putString(ARG_TARGET_OS, targetOs);
        f.setArguments(b);
        return f;
    }

    private ShortcutProfileManager profileManager;
    private Rows23StripProfileManager stripProfileManager;

    private String profileId;
    private String slotKey;
    @Nullable
    private String shortcutId;
    private String factoryCap;
    private String factoryEvent;
    private String targetOs;

    private FixedStripLayoutCatalog.ParsedSlotKey parsedSlot;
    @Nullable
    private FixedStripLayoutCatalog.FactoryHid factoryHid;

    private int selectedKeyCode = -1;
    private int selectedModifiers = 0;

    private final List<ChipGroup> keyChipGroups = new ArrayList<>();

    private MaterialToolbar toolbar;
    private TextView persistenceValue;
    private TextView slotSummary;
    private TextView factoryCapView;
    private TextView factoryEventView;
    private TextInputEditText nameInput;
    private ChipGroup iconChips;
    private RecyclerView iconGrid;
    private TextView keyPreview;
    private LinearLayout keySectionsLayout;
    private MaterialButton resetButton;
    private MaterialButton saveButton;
    private MaterialButton cancelButton;

    @NonNull
    private String iconValue = "";

    private IconGridAdapter iconAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle a = getArguments();
        if (a == null) {
            return;
        }
        profileId = a.getString(ARG_PROFILE_ID, "");
        slotKey = a.getString(ARG_SLOT_KEY, "");
        shortcutId = a.getString(ARG_SHORTCUT_ID, "");
        if (shortcutId != null && shortcutId.isEmpty()) {
            shortcutId = null;
        }
        factoryCap = a.getString(ARG_FACTORY_CAP, "");
        factoryEvent = a.getString(ARG_FACTORY_EVENT, "");
        targetOs = a.getString(ARG_TARGET_OS, "macos");
        parsedSlot = FixedStripLayoutCatalog.parseSlotKey(slotKey);
        factoryHid = parsedSlot != null ? FixedStripLayoutCatalog.resolveFactoryHidForSlot(parsedSlot) : null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rows23_slot_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        profileManager = new ShortcutProfileManager(requireContext());
        stripProfileManager = new Rows23StripProfileManager(requireContext(), profileManager);

        toolbar = root.findViewById(R.id.rows23_slot_editor_toolbar);
        persistenceValue = root.findViewById(R.id.rows23_slot_editor_persistence_value);
        slotSummary = root.findViewById(R.id.rows23_slot_editor_slot_summary);
        factoryCapView = root.findViewById(R.id.rows23_slot_editor_factory_cap);
        factoryEventView = root.findViewById(R.id.rows23_slot_editor_factory_event);
        nameInput = root.findViewById(R.id.rows23_slot_editor_name);
        iconChips = root.findViewById(R.id.rows23_slot_editor_icon_chips);
        iconGrid = root.findViewById(R.id.rows23_slot_editor_icon_grid);
        keyPreview = root.findViewById(R.id.rows23_slot_editor_key_preview);
        keySectionsLayout = root.findViewById(R.id.rows23_slot_editor_key_sections);
        resetButton = root.findViewById(R.id.rows23_slot_editor_reset);
        saveButton = root.findViewById(R.id.rows23_slot_editor_save);
        cancelButton = root.findViewById(R.id.rows23_slot_editor_cancel);

        boolean editing = shortcutId != null;
        toolbar.setTitle(editing ? getString(R.string.rows23_slot_editor_title_edit)
                : getString(R.string.rows23_slot_editor_title_new));
        toolbar.setNavigationOnClickListener(v -> dismissSelf());

        persistenceValue.setText(slotKey);
        factoryCapView.setText(factoryCap);
        factoryEventView.setText(factoryEvent);

        if (parsedSlot != null) {
            String layer = parsedSlot.fnLayer
                    ? getString(R.string.shortcut_hub_strip_grid_layer_fn)
                    : getString(R.string.shortcut_hub_strip_grid_layer_base);
            slotSummary.setText(getString(R.string.rows23_slot_editor_slot_summary,
                    parsedSlot.pageIndex, parsedSlot.stripRow, parsedSlot.col, layer));
        } else {
            slotSummary.setText("");
        }

        ShortcutProfileManager.Shortcut existing = shortcutId != null
                ? stripProfileManager.findShortcut(profileId, shortcutId)
                : null;
        if (existing != null) {
            nameInput.setText(existing.name != null ? existing.name : "");
            iconValue = existing.icon != null ? existing.icon : "";
            selectedKeyCode = existing.keyCode;
            selectedModifiers = HidKeyCatalog.normalizeStripModifiers(existing.modifiers);
        } else if (factoryHid != null) {
            selectedKeyCode = factoryHid.keyCode;
            selectedModifiers = HidKeyCatalog.normalizeStripModifiers(factoryHid.modifiers);
            String defName = factoryCap != null && !factoryCap.isEmpty() ? factoryCap
                    : HidKeyCatalog.formatChoiceLabel(requireContext(), selectedKeyCode, selectedModifiers,
                    targetOs, null);
            nameInput.setText(defName);
        }

        iconGrid.setLayoutManager(new GridLayoutManager(requireContext(), 6));
        iconAdapter = new IconGridAdapter();
        iconGrid.setAdapter(iconAdapter);

        iconChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.rows23_icon_chip_none) {
                iconValue = "";
                iconAdapter.setSelectedValue("");
            }
            updateIconGridVisibility();
            bindIconAdapterList();
        });

        if (iconValue.isEmpty()) {
            iconChips.check(R.id.rows23_icon_chip_none);
        } else if (ShortcutFavoriteRowViews.isEmojiIcon(iconValue)) {
            iconChips.check(R.id.rows23_icon_chip_emoji);
        } else {
            iconChips.check(R.id.rows23_icon_chip_vector);
        }

        nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveEnabled();
            }
        });

        updateIconGridVisibility();
        bindIconAdapterList();

        buildKeyPickers();

        updateKeyPreview();
        updateResetEnabled();
        updateSaveEnabled();

        resetButton.setOnClickListener(v -> onReset());
        cancelButton.setOnClickListener(v -> dismissSelf());
        saveButton.setOnClickListener(v -> onSave());
    }

    private void updateIconGridVisibility() {
        int checked = iconChips.getCheckedChipId();
        iconGrid.setVisibility(checked == R.id.rows23_icon_chip_none ? View.GONE : View.VISIBLE);
    }

    private void bindIconAdapterList() {
        int checked = iconChips.getCheckedChipId();
        if (checked == R.id.rows23_icon_chip_none) {
            iconAdapter.setEntries(new ArrayList<>());
            iconAdapter.setSelectedValue("");
            iconAdapter.notifyDataSetChanged();
            return;
        }
        if (checked == R.id.rows23_icon_chip_emoji) {
            iconAdapter.setEntries(IconCatalog.emojiEntries());
            iconAdapter.setKind(IconCatalog.Kind.EMOJI);
        } else {
            iconAdapter.setEntries(IconCatalog.vectorEntries());
            iconAdapter.setKind(IconCatalog.Kind.VECTOR_DRAWABLE);
        }
        iconAdapter.setSelectedValue(iconValue);
        iconAdapter.notifyDataSetChanged();
    }

    private void buildKeyPickers() {
        keySectionsLayout.removeAllViews();
        keyChipGroups.clear();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<HidKeyCatalog.Section> sections = HidKeyCatalog.buildSections();
        for (int i = 0; i < sections.size(); i++) {
            HidKeyCatalog.Section section = sections.get(i);
            if (i > 0) {
                MaterialDivider divider = new MaterialDivider(requireContext());
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dlp.topMargin = dp(18);
                divider.setLayoutParams(dlp);
                keySectionsLayout.addView(divider);
            }

            TextView title = new TextView(requireContext());
            TextViewCompat.setTextAppearance(title, R.style.TextAppearance_KeyMod_SectionCaption);
            title.setText(section.titleRes);
            title.setPadding(0, dp(i == 0 ? 4 : 0), 0, dp(10));
            keySectionsLayout.addView(title);

            ChipGroup group = new ChipGroup(requireContext());
            group.setSingleSelection(false);
            group.setChipSpacingHorizontal(dp(6));
            group.setChipSpacingVertical(dp(8));
            keyChipGroups.add(group);
            keySectionsLayout.addView(group);

            for (HidKeyCatalog.Choice choice : section.choices) {
                String label = HidKeyCatalog.formatChoiceLabel(requireContext(), choice.keyCode,
                        choice.modifiers, targetOs, choice.labelOverride);
                int chipLayout = label.length() == 1
                        ? R.layout.item_create_shortcut_key_chip
                        : R.layout.item_create_shortcut_key_chip_small;
                Chip chip = (Chip) inflater.inflate(chipLayout, group, false);
                chip.setText(label);
                chip.setTag(choice);
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (!isChecked) {
                        return;
                    }
                    HidKeyCatalog.Choice c = (HidKeyCatalog.Choice) buttonView.getTag();
                    for (ChipGroup og : keyChipGroups) {
                        if (og != group) {
                            og.clearCheck();
                        }
                    }
                    selectedKeyCode = c.keyCode;
                    selectedModifiers = HidKeyCatalog.normalizeStripModifiers(c.modifiers);
                    updateKeyPreview();
                    updateSaveEnabled();
                });
                group.addView(chip);
                if (HidKeyCatalog.isSameChoice(choice.keyCode, choice.modifiers, selectedKeyCode, selectedModifiers)) {
                    chip.setChecked(true);
                }
            }
        }
    }

    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }

    private void updateKeyPreview() {
        if (selectedKeyCode < 0) {
            keyPreview.setText(getString(R.string.rows23_slot_editor_error_key));
            return;
        }
        HidKeyCatalog.Choice match = HidKeyCatalog.findMatchingChoice(selectedKeyCode, selectedModifiers);
        String label = HidKeyCatalog.formatChoiceLabel(requireContext(), selectedKeyCode, selectedModifiers, targetOs,
                match != null ? match.labelOverride : null);
        keyPreview.setText(getString(R.string.rows23_slot_editor_key_preview, label));
    }

    private void updateResetEnabled() {
        Rows23StripProfile p = stripProfileManager.getProfileById(profileId);
        boolean overridden = p != null && p.slotMap != null && p.slotMap.containsKey(slotKey);
        resetButton.setEnabled(overridden);
    }

    private void updateSaveEnabled() {
        String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        saveButton.setEnabled(!name.isEmpty() && selectedKeyCode >= 0);
    }

    private void onReset() {
        String sid = null;
        Rows23StripProfile p = stripProfileManager.getProfileById(profileId);
        if (p != null && p.slotMap != null) {
            sid = p.slotMap.get(slotKey);
        }
        stripProfileManager.removeSlot(profileId, slotKey);
        if (sid != null) {
            stripProfileManager.removeShortcutIfUnreferenced(profileId, sid);
        }
        Toast.makeText(requireContext(), R.string.rows23_slot_editor_reset_done, Toast.LENGTH_SHORT).show();
        notifyHostChanged();
        dismissSelf();
    }

    private void onSave() {
        String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), R.string.rows23_slot_editor_error_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedKeyCode < 0) {
            Toast.makeText(requireContext(), R.string.rows23_slot_editor_error_key, Toast.LENGTH_SHORT).show();
            return;
        }
        int mods = HidKeyCatalog.normalizeStripModifiers(selectedModifiers);
        String label = KeyParser.toLabelForTargetOs(selectedKeyCode, mods, targetOs);
        label = KeyParser.displayLabel(label, targetOs);

        if (shortcutId != null) {
            ShortcutProfileManager.Shortcut sc = stripProfileManager.findShortcut(profileId, shortcutId);
            if (sc == null) {
                Toast.makeText(requireContext(), R.string.create_shortcut_save_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            sc.name = name;
            sc.label = label;
            sc.keyCode = selectedKeyCode;
            sc.modifiers = mods;
            sc.icon = iconValue != null ? iconValue : "";
            stripProfileManager.upsertShortcut(profileId, sc);
        } else {
            ShortcutProfileManager.Shortcut created = new ShortcutProfileManager.Shortcut();
            created.id = "strip_ov_" + System.currentTimeMillis();
            created.name = name;
            created.label = label;
            created.keyCode = selectedKeyCode;
            created.modifiers = mods;
            created.icon = iconValue != null ? iconValue : "";
            created.displayOrder = 0;
            stripProfileManager.upsertShortcut(profileId, created);
            stripProfileManager.putSlot(profileId, slotKey, created.id);
        }
        Toast.makeText(requireContext(), getString(R.string.rows23_slot_editor_saved, name), Toast.LENGTH_SHORT).show();
        notifyHostChanged();
        dismissSelf();
    }

    private void notifyHostChanged() {
        Fragment p = getParentFragment();
        if (p instanceof ShortcutHubFragment) {
            ((ShortcutHubFragment) p).onRows23SlotEditorFinished();
        }
    }

    private void dismissSelf() {
        Fragment p = getParentFragment();
        if (p != null) {
            p.getChildFragmentManager().popBackStack();
        }
    }

    private final class IconGridAdapter extends RecyclerView.Adapter<IconGridAdapter.VH> {

        @NonNull
        private List<IconCatalog.Entry> entries = new ArrayList<>();
        @NonNull
        private IconCatalog.Kind kind = IconCatalog.Kind.EMOJI;
        @NonNull
        private String selected = "";

        void setEntries(@NonNull List<IconCatalog.Entry> e) {
            entries = e;
        }

        void setKind(@NonNull IconCatalog.Kind k) {
            kind = k;
        }

        void setSelectedValue(@NonNull String s) {
            selected = s != null ? s : "";
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rows23_icon_cell, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            IconCatalog.Entry e = entries.get(position);
            boolean sel = e.value.equals(selected);
            if (e.kind == IconCatalog.Kind.EMOJI) {
                holder.emoji.setVisibility(View.VISIBLE);
                holder.drawable.setVisibility(View.GONE);
                holder.emoji.setTextSize(24);
                holder.emoji.setText(e.value);
            } else {
                holder.emoji.setTextSize(24);
                holder.emoji.setText("");
                int res = requireContext().getResources().getIdentifier(e.value, "drawable",
                        requireContext().getPackageName());
                if (res != 0) {
                    holder.emoji.setVisibility(View.GONE);
                    holder.drawable.setVisibility(View.VISIBLE);
                    holder.drawable.setImageResource(res);
                } else {
                    holder.drawable.setVisibility(View.GONE);
                    holder.emoji.setVisibility(View.VISIBLE);
                    holder.emoji.setText(e.value);
                    holder.emoji.setTextSize(10);
                }
            }
            holder.itemView.setAlpha(sel ? 1f : 0.75f);
            holder.itemView.setOnClickListener(v -> {
                iconValue = e.value;
                selected = e.value;
                if (e.kind == IconCatalog.Kind.EMOJI) {
                    iconChips.check(R.id.rows23_icon_chip_emoji);
                } else {
                    iconChips.check(R.id.rows23_icon_chip_vector);
                }
                updateIconGridVisibility();
                iconAdapter.setSelectedValue(iconValue);
                iconAdapter.notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView drawable;
            final TextView emoji;

            VH(@NonNull View itemView) {
                super(itemView);
                drawable = itemView.findViewById(R.id.rows23_icon_cell_drawable);
                emoji = itemView.findViewById(R.id.rows23_icon_cell_emoji);
            }
        }
    }
}
