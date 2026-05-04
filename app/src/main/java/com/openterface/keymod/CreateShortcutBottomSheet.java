package com.openterface.keymod;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextUtils;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.openterface.keymod.preset.Rows23StripProfileManager;
import com.openterface.keymod.util.KeyParser;
import com.openterface.keymod.util.MyShortcutsReorderHelpReadModeDialog;

/**
 * Quick-create shortcut from the keyboard strip: modifiers + key chips (or advanced token string),
 * then save to General and Favorites.
 */
public final class CreateShortcutBottomSheet {

    public enum CreateMode {
        GENERAL_AND_FAVORITES,
        CATEGORY_ONLY
    }

    private CreateShortcutBottomSheet() {
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }

    @NonNull
    private static String targetOsDisplayName(@NonNull Context ctx, @Nullable String targetOs) {
        if ("windows".equals(targetOs)) {
            return ctx.getString(R.string.target_os_windows);
        }
        if ("linux".equals(targetOs)) {
            return ctx.getString(R.string.target_os_linux);
        }
        return ctx.getString(R.string.target_os_macos);
    }

    /**
     * Same read-mode help UX as {@link MyShortcutsReorderHelpReadModeDialog} for reorder Favorites.
     */
    @NonNull
    private static CharSequence formatCreateShortcutHelpReadText(@NonNull Context ctx, @NonNull String targetOs) {
        final int htmlMode = HtmlCompat.FROM_HTML_MODE_COMPACT;
        String osName = targetOsDisplayName(ctx, targetOs);
        String title = ctx.getString(R.string.create_shortcut_help_read_mode_title, osName);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(title);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(1.12f), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(
                ctx.getString(R.string.create_shortcut_help_read_mode_block1), htmlMode));
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(
                ctx.getString(R.string.create_shortcut_help_read_mode_block2), htmlMode));
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(
                ctx.getString(R.string.create_shortcut_help_read_mode_block3), htmlMode));
        return sb;
    }

    /**
     * Labels match {@link com.openterface.keymod.CustomKeyboardView#buildTopPanelModifierKey} / app Target OS.
     */
    private static void applyModifierButtonLabelsForTargetOs(
            @NonNull Context ctx,
            @Nullable String targetOs,
            @NonNull MaterialButton modCmd,
            @NonNull MaterialButton modCtrl,
            @NonNull MaterialButton modShift,
            @NonNull MaterialButton modAlt
    ) {
        String os = targetOs != null ? targetOs : "macos";
        String shift = ctx.getString(R.string.Shift);
        if ("macos".equals(os)) {
            modCmd.setText("⌘");
            modCmd.setContentDescription(ctx.getString(R.string.modifier_command));
            modCtrl.setText(ctx.getString(R.string.modifier_control));
            modCtrl.setContentDescription(ctx.getString(R.string.modifier_control));
            modShift.setText(shift);
            modShift.setContentDescription(shift);
            modAlt.setText(ctx.getString(R.string.modifier_option));
            modAlt.setContentDescription(ctx.getString(R.string.modifier_option));
        } else if ("linux".equals(os)) {
            modCmd.setText(ctx.getString(R.string.modifier_sup));
            modCmd.setContentDescription(ctx.getString(R.string.modifier_sup));
            modCtrl.setText(ctx.getString(R.string.modifier_ctrl));
            modCtrl.setContentDescription(ctx.getString(R.string.modifier_ctrl));
            modShift.setText(shift);
            modShift.setContentDescription(shift);
            modAlt.setText(ctx.getString(R.string.modifier_alt));
            modAlt.setContentDescription(ctx.getString(R.string.modifier_alt));
        } else {
            modCmd.setText(ctx.getString(R.string.modifier_win));
            modCmd.setContentDescription(ctx.getString(R.string.modifier_win));
            modCtrl.setText(ctx.getString(R.string.modifier_ctrl));
            modCtrl.setContentDescription(ctx.getString(R.string.modifier_ctrl));
            modShift.setText(shift);
            modShift.setContentDescription(shift);
            modAlt.setText(ctx.getString(R.string.modifier_alt));
            modAlt.setContentDescription(ctx.getString(R.string.modifier_alt));
        }
    }

    private static String buildMacroData(int modifierMask, String innerKeyToken) {
        StringBuilder sb = new StringBuilder();
        if ((modifierMask & 0x08) != 0) {
            sb.append("<CMD>");
        }
        if ((modifierMask & 0x01) != 0) {
            sb.append("<CTRL>");
        }
        if ((modifierMask & 0x02) != 0) {
            sb.append("<SHIFT>");
        }
        if ((modifierMask & 0x04) != 0) {
            sb.append("<ALT>");
        }
        sb.append(innerKeyToken);
        if ((modifierMask & 0x04) != 0) {
            sb.append("</ALT>");
        }
        if ((modifierMask & 0x02) != 0) {
            sb.append("</SHIFT>");
        }
        if ((modifierMask & 0x01) != 0) {
            sb.append("</CTRL>");
        }
        if ((modifierMask & 0x08) != 0) {
            sb.append("</CMD>");
        }
        return sb.toString();
    }

    private static int readModifierMask(
            MaterialButton cmd,
            MaterialButton ctrl,
            MaterialButton shift,
            MaterialButton alt
    ) {
        int m = 0;
        if (cmd.isChecked()) {
            m |= 0x08;
        }
        if (ctrl.isChecked()) {
            m |= 0x01;
        }
        if (shift.isChecked()) {
            m |= 0x02;
        }
        if (alt.isChecked()) {
            m |= 0x04;
        }
        return m;
    }

    @Nullable
    private static String selectedKeyToken(ChipGroup keyChips) {
        for (int i = 0; i < keyChips.getChildCount(); i++) {
            View v = keyChips.getChildAt(i);
            if (v instanceof Chip && ((Chip) v).isChecked()) {
                Object tag = v.getTag();
                return tag instanceof String ? (String) tag : null;
            }
        }
        return null;
    }

    private static void addKeyChip(
            AppCompatActivity activity,
            ChipGroup group,
            int layoutRes,
            String label,
            String token
    ) {
        LayoutInflater inflater = activity.getLayoutInflater();
        Chip chip = (Chip) inflater.inflate(layoutRes, group, false);
        chip.setText(label);
        chip.setTag(token);
        chip.setContentDescription(label);
        group.addView(chip);
    }

    private static void setModifierButtonsFromMask(
            MaterialButton modCmd,
            MaterialButton modCtrl,
            MaterialButton modShift,
            MaterialButton modAlt,
            int modifiersMask
    ) {
        modCmd.setChecked((modifiersMask & 0x08) != 0);
        modCtrl.setChecked((modifiersMask & 0x01) != 0);
        modShift.setChecked((modifiersMask & 0x02) != 0);
        modAlt.setChecked((modifiersMask & 0x04) != 0);
    }

    private static boolean chordMatchesShortcut(
            KeyParser.ParsedKey parsed,
            int keyCode,
            int modifiers,
            String targetOs
    ) {
        if (parsed.keyCode < 0 || parsed.keyCode != keyCode) {
            return false;
        }
        int pa = ShortcutProfileManager.normalizeModifiersForTargetOs(parsed.modifiers, targetOs);
        int pb = ShortcutProfileManager.normalizeModifiersForTargetOs(modifiers, targetOs);
        return pa == pb;
    }

    private static void tryBindChordUiFromShortcut(
            ShortcutProfileManager.Shortcut shortcut,
            String targetOs,
            MaterialButton modCmd,
            MaterialButton modCtrl,
            MaterialButton modShift,
            MaterialButton modAlt,
            ChipGroup keyChips,
            TextInputEditText advanced
    ) {
        setModifierButtonsFromMask(modCmd, modCtrl, modShift, modAlt, shortcut.modifiers);
        for (int i = 0; i < keyChips.getChildCount(); i++) {
            View v = keyChips.getChildAt(i);
            if (!(v instanceof Chip)) {
                continue;
            }
            Chip chip = (Chip) v;
            Object tag = chip.getTag();
            if (!(tag instanceof String)) {
                continue;
            }
            String token = (String) tag;
            String data = buildMacroData(readModifierMask(modCmd, modCtrl, modShift, modAlt), token);
            KeyParser.ParsedKey p = KeyParser.parse(data);
            if (chordMatchesShortcut(p, shortcut.keyCode, shortcut.modifiers, targetOs)) {
                chip.setChecked(true);
                advanced.setText("");
                return;
            }
        }
        keyChips.clearCheck();
        advanced.setText(KeyParser.toToken(shortcut.keyCode, shortcut.modifiers));
    }

    private static int parseDisplayOrderInternal(String raw, int fallback) {
        if (TextUtils.isEmpty(raw)) {
            return fallback > 0 ? fallback : 0;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(value, 0);
        } catch (NumberFormatException ignored) {
            return fallback > 0 ? fallback : 0;
        }
    }

    private static void populateKeyChips(AppCompatActivity activity, ChipGroup keyChips) {
        int letterLayout = R.layout.item_create_shortcut_key_chip;
        int specialLayout = R.layout.item_create_shortcut_key_chip_small;
        for (char c = 'A'; c <= 'Z'; c++) {
            String display = String.valueOf(c);
            String token = String.valueOf((char) ('a' + (c - 'A')));
            addKeyChip(activity, keyChips, letterLayout, display, token);
        }
        String[][] tokens = {
                {"Esc", "<ESC>"}, {"Back", "<BACK>"}, {"Enter", "<ENTER>"}, {"Space", "<SPACE>"},
                {"←", "<LEFT>"}, {"→", "<RIGHT>"}, {"↑", "<UP>"}, {"↓", "<DOWN>"},
                {"Home", "<HOME>"}, {"End", "<END>"}, {"Tab", "<TAB>"}, {"Del", "<DEL>"},
                {"F1", "<F1>"}, {"F2", "<F2>"}, {"F3", "<F3>"}, {"F4", "<F4>"},
                {"F5", "<F5>"}, {"F6", "<F6>"}, {"F7", "<F7>"}, {"F8", "<F8>"},
                {"F9", "<F9>"}, {"F10", "<F10>"}, {"F11", "<F11>"}, {"F12", "<F12>"}
        };
        for (String[] entry : tokens) {
            addKeyChip(activity, keyChips, specialLayout, entry[0], entry[1]);
        }
    }

    private static void installShortcutSheet(
            @NonNull AppCompatActivity activity,
            @NonNull ShortcutProfileManager profileManager,
            @NonNull String profileId,
            @NonNull String targetOs,
            @Nullable CreateMode createMode,
            @Nullable String categoryId,
            @Nullable ShortcutProfileManager.Shortcut editShortcut,
            @Nullable Rows23StripProfileManager rows23StripMgr,
            @Nullable String rows23StripProfileId,
            @Nullable String rows23AssignSlotKey,
            @Nullable Runnable onSaved
    ) {
        final boolean isEdit = editShortcut != null;
        if (!isEdit && createMode == null) {
            return;
        }

        final boolean rows23Mode = rows23StripMgr != null && rows23StripProfileId != null;
        ShortcutProfileManager.ShortcutProfile profile = rows23Mode
                ? null
                : profileManager.getProfileById(profileId);
        if (!rows23Mode && profile == null) {
            Toast.makeText(activity, R.string.create_shortcut_no_profile, Toast.LENGTH_SHORT).show();
            return;
        }
        if (rows23Mode && rows23StripMgr.getProfileById(rows23StripProfileId) == null) {
            Toast.makeText(activity, R.string.create_shortcut_no_profile, Toast.LENGTH_SHORT).show();
            return;
        }

        View root = activity.getLayoutInflater().inflate(R.layout.bottomsheet_create_shortcut, null, false);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(root);

        TextView sheetTitle = root.findViewById(R.id.create_shortcut_sheet_title);
        View metaContainer = root.findViewById(R.id.create_shortcut_edit_meta_container);
        TextInputEditText nameInput = root.findViewById(R.id.create_shortcut_edit_name_input);
        TextInputEditText iconInput = root.findViewById(R.id.create_shortcut_edit_icon_input);
        TextInputEditText orderInput = root.findViewById(R.id.create_shortcut_edit_order_input);

        ImageButton info = root.findViewById(R.id.create_shortcut_info);
        if (info != null) {
            info.setOnClickListener(v -> MyShortcutsReorderHelpReadModeDialog.show(
                    activity, formatCreateShortcutHelpReadText(activity, targetOs)));
        }

        TextInputEditText advanced = root.findViewById(R.id.create_shortcut_advanced_input);
        TextView preview = root.findViewById(R.id.create_shortcut_preview);
        MaterialButton modCmd = root.findViewById(R.id.create_shortcut_mod_cmd);
        MaterialButton modCtrl = root.findViewById(R.id.create_shortcut_mod_ctrl);
        MaterialButton modShift = root.findViewById(R.id.create_shortcut_mod_shift);
        MaterialButton modAlt = root.findViewById(R.id.create_shortcut_mod_alt);
        applyModifierButtonLabelsForTargetOs(activity, targetOs, modCmd, modCtrl, modShift, modAlt);
        ChipGroup keyChips = root.findViewById(R.id.create_shortcut_key_chips);
        MaterialButton cancel = root.findViewById(R.id.create_shortcut_cancel);
        MaterialButton save = root.findViewById(R.id.create_shortcut_save);

        populateKeyChips(activity, keyChips);

        if (isEdit) {
            if (sheetTitle != null) {
                sheetTitle.setText(R.string.create_shortcut_sheet_title_edit);
            }
            if (metaContainer != null) {
                metaContainer.setVisibility(View.VISIBLE);
            }
            if (nameInput != null) {
                nameInput.setText(editShortcut.name != null ? editShortcut.name : "");
            }
            if (iconInput != null) {
                iconInput.setText(editShortcut.icon != null ? editShortcut.icon : "");
            }
            if (orderInput != null && editShortcut.displayOrder > 0) {
                orderInput.setText(String.valueOf(editShortcut.displayOrder));
            }
            tryBindChordUiFromShortcut(editShortcut, targetOs, modCmd, modCtrl, modShift, modAlt, keyChips, advanced);
        } else {
            if (metaContainer != null) {
                metaContainer.setVisibility(View.GONE);
            }
        }

        Runnable updatePreview = () -> {
            String adv = advanced.getText() != null ? advanced.getText().toString().trim() : "";
            String data;
            if (!adv.isEmpty()) {
                data = adv;
            } else {
                String token = selectedKeyToken(keyChips);
                if (token == null) {
                    preview.setText(activity.getString(R.string.create_shortcut_preview_empty));
                    save.setEnabled(false);
                    return;
                }
                data = buildMacroData(
                        readModifierMask(modCmd, modCtrl, modShift, modAlt),
                        token);
            }
            KeyParser.ParsedKey parsed = KeyParser.parse(data);
            if (parsed.keyCode < 0) {
                preview.setText(activity.getString(R.string.create_shortcut_preview_invalid));
                save.setEnabled(false);
            } else {
                String label = KeyParser.toLabelForTargetOs(parsed.keyCode, parsed.modifiers, targetOs);
                preview.setText(KeyParser.displayLabel(label, targetOs));
                if (isEdit && nameInput != null) {
                    String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                    save.setEnabled(!name.isEmpty());
                } else {
                    save.setEnabled(true);
                }
            }
        };

        keyChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                String adv = advanced.getText() != null ? advanced.getText().toString().trim() : "";
                if (!adv.isEmpty()) {
                    advanced.setText("");
                }
            }
            updatePreview.run();
        });

        View.OnClickListener modClick = v -> updatePreview.run();
        modCmd.setOnClickListener(modClick);
        modCtrl.setOnClickListener(modClick);
        modShift.setOnClickListener(modClick);
        modAlt.setOnClickListener(modClick);

        advanced.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.toString().trim().length() > 0) {
                    keyChips.clearCheck();
                }
                updatePreview.run();
            }
        });

        if (isEdit && nameInput != null) {
            nameInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    updatePreview.run();
                }
            });
        }

        cancel.setOnClickListener(v -> dialog.dismiss());

        save.setOnClickListener(v -> {
            String adv = advanced.getText() != null ? advanced.getText().toString().trim() : "";
            String data;
            if (!adv.isEmpty()) {
                data = adv;
            } else {
                String token = selectedKeyToken(keyChips);
                if (token == null) {
                    Toast.makeText(activity, R.string.create_shortcut_need_key, Toast.LENGTH_SHORT).show();
                    return;
                }
                data = buildMacroData(
                        readModifierMask(modCmd, modCtrl, modShift, modAlt),
                        token);
            }
            KeyParser.ParsedKey parsed = KeyParser.parse(data);
            if (parsed.keyCode < 0) {
                Toast.makeText(activity, R.string.create_shortcut_invalid, Toast.LENGTH_LONG).show();
                return;
            }
            if (isEdit) {
                String name = nameInput != null && nameInput.getText() != null
                        ? nameInput.getText().toString().trim() : "";
                if (name.isEmpty()) {
                    Toast.makeText(activity, R.string.shortcut_hub_toast_all_fields_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                String excludeId = editShortcut.id != null && !editShortcut.id.isEmpty() ? editShortcut.id : null;
                boolean dupChord = rows23Mode
                        ? rows23StripMgr.profileHasChordExcluding(
                                rows23StripProfileId, parsed.keyCode, parsed.modifiers, targetOs, excludeId)
                        : profileManager.profileHasChordExcluding(
                                profileId, parsed.keyCode, parsed.modifiers, targetOs, excludeId);
                if (dupChord) {
                    Toast.makeText(activity, R.string.create_shortcut_duplicate, Toast.LENGTH_LONG).show();
                    return;
                }
                String label = KeyParser.toLabelForTargetOs(parsed.keyCode, parsed.modifiers, targetOs);
                editShortcut.name = name;
                editShortcut.label = label;
                editShortcut.modifiers = parsed.modifiers;
                editShortcut.keyCode = parsed.keyCode;
                if (iconInput != null) {
                    editShortcut.icon = iconInput.getText() != null ? iconInput.getText().toString().trim() : "";
                }
                if (orderInput != null) {
                    String orderRaw = orderInput.getText() != null ? orderInput.getText().toString().trim() : "";
                    editShortcut.displayOrder = parseDisplayOrderInternal(orderRaw, editShortcut.displayOrder);
                }
                if (rows23Mode) {
                    rows23StripMgr.upsertShortcut(rows23StripProfileId, editShortcut);
                } else {
                    profileManager.updateProfile(profile);
                    if (editShortcut.id != null) {
                        profileManager.refreshMyShortcutClonesFromProfile(profileId, editShortcut.id);
                    }
                }
                Toast.makeText(
                        activity,
                        activity.getString(R.string.create_shortcut_saved, name),
                        Toast.LENGTH_SHORT).show();
            } else {
                boolean dupNew = rows23Mode
                        ? rows23StripMgr.profileHasChordExcluding(
                                rows23StripProfileId, parsed.keyCode, parsed.modifiers, targetOs, null)
                        : profileManager.profileHasChord(profileId, parsed.keyCode, parsed.modifiers, targetOs);
                if (dupNew) {
                    Toast.makeText(activity, R.string.create_shortcut_duplicate, Toast.LENGTH_LONG).show();
                    return;
                }
                if (rows23Mode) {
                    ShortcutProfileManager.Shortcut created = new ShortcutProfileManager.Shortcut();
                    created.id = "strip_ov_" + System.currentTimeMillis();
                    String label = KeyParser.toLabelForTargetOs(parsed.keyCode, parsed.modifiers, targetOs);
                    created.label = label;
                    created.name = label;
                    created.modifiers = parsed.modifiers;
                    created.keyCode = parsed.keyCode;
                    created.icon = "";
                    created.displayOrder = 0;
                    rows23StripMgr.upsertShortcut(rows23StripProfileId, created);
                    if (rows23AssignSlotKey != null && !rows23AssignSlotKey.isEmpty()) {
                        rows23StripMgr.putSlot(rows23StripProfileId, rows23AssignSlotKey, created.id);
                    }
                    Toast.makeText(
                            activity,
                            activity.getString(R.string.create_shortcut_saved, created.label),
                            Toast.LENGTH_SHORT).show();
                } else {
                    ShortcutProfileManager.Shortcut created;
                    if (createMode == CreateMode.CATEGORY_ONLY) {
                        created = profileManager.addQuickShortcutToCategoryOnly(
                                profileId, categoryId, parsed.keyCode, parsed.modifiers, targetOs);
                    } else {
                        created = profileManager.addQuickShortcutToGeneralAndFavorites(
                                profileId, parsed.keyCode, parsed.modifiers, targetOs);
                    }
                    if (created == null) {
                        Toast.makeText(activity, R.string.create_shortcut_save_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(
                            activity,
                            activity.getString(R.string.create_shortcut_saved, created.label),
                            Toast.LENGTH_SHORT).show();
                }
            }
            dialog.dismiss();
            if (onSaved != null) {
                onSaved.run();
            }
        });

        updatePreview.run();
        dialog.show();
    }

    public static void show(
            @NonNull AppCompatActivity activity,
            @NonNull ShortcutProfileManager profileManager,
            @NonNull String profileId,
            @NonNull String targetOs,
            @NonNull CreateMode mode,
            @Nullable String categoryId,
            @Nullable Runnable onSaved
    ) {
        installShortcutSheet(activity, profileManager, profileId, targetOs, mode, categoryId, null,
                null, null, null, onSaved);
    }

    /**
     * Edit an existing profile shortcut using the same bottom sheet as {@link #show} (New shortcut).
     */
    public static void showEdit(
            @NonNull AppCompatActivity activity,
            @NonNull ShortcutProfileManager profileManager,
            @NonNull String profileId,
            @NonNull String targetOs,
            @NonNull ShortcutProfileManager.Shortcut editShortcut,
            @Nullable Runnable onSaved
    ) {
        installShortcutSheet(activity, profileManager, profileId, targetOs, null, null, editShortcut,
                null, null, null, onSaved);
    }

    /**
     * Edit a shortcut stored in a Rows 2–3 strip profile (slot definitions).
     */
    public static void showEditRows23Strip(
            @NonNull AppCompatActivity activity,
            @NonNull ShortcutProfileManager profileManager,
            @NonNull Rows23StripProfileManager stripMgr,
            @NonNull String stripProfileId,
            @NonNull String targetOs,
            @NonNull ShortcutProfileManager.Shortcut editShortcut,
            @Nullable Runnable onSaved
    ) {
        installShortcutSheet(activity, profileManager, stripProfileId, targetOs, null, null, editShortcut,
                stripMgr, stripProfileId, null, onSaved);
    }

    /**
     * Create a new shortcut and assign it to a strip slot ({@code p0_r2_c0_base} style key).
     */
    public static void showNewRows23StripSlot(
            @NonNull AppCompatActivity activity,
            @NonNull ShortcutProfileManager profileManager,
            @NonNull Rows23StripProfileManager stripMgr,
            @NonNull String stripProfileId,
            @NonNull String slotKey,
            @NonNull String targetOs,
            @Nullable Runnable onSaved
    ) {
        installShortcutSheet(activity, profileManager, stripProfileId, targetOs,
                CreateMode.GENERAL_AND_FAVORITES, null, null,
                stripMgr, stripProfileId, slotKey, onSaved);
    }

    public static void show(
            @NonNull AppCompatActivity activity,
            @NonNull ShortcutProfileManager profileManager,
            @NonNull String profileId,
            @NonNull String targetOs,
            @Nullable Runnable onSaved
    ) {
        show(activity, profileManager, profileId, targetOs,
                CreateMode.GENERAL_AND_FAVORITES, null, onSaved);
    }
}
