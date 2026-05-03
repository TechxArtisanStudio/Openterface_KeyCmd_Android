package com.openterface.keymod;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.text.HtmlCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.openterface.keymod.MacrosManager.KeyEvent;
import com.openterface.keymod.MacrosManager.Macro;
import com.openterface.keymod.util.MyShortcutsReorderHelpReadModeDialog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Add / edit macro UI aligned with {@link CreateShortcutBottomSheet}: bottom sheet, preview strip,
 * outlined inputs, Material dividers, token chips, and Cancel / Save row.
 */
public final class CreateMacroBottomSheet {

    private static final String TAG = "CreateMacroBottomSheet";

    private CreateMacroBottomSheet() {
    }

    @NonNull
    private static CharSequence formatMacroHelpReadText(@NonNull Context ctx) {
        final int htmlMode = HtmlCompat.FROM_HTML_MODE_COMPACT;
        String title = ctx.getString(R.string.macros_help_read_mode_title);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(title);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(1.12f), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(ctx.getString(R.string.macros_help_read_mode_block1), htmlMode));
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(ctx.getString(R.string.macros_help_read_mode_block2), htmlMode));
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(ctx.getString(R.string.macros_help_read_mode_block3), htmlMode));
        sb.append("\n\n");
        sb.append(ctx.getString(R.string.my_shortcuts_reorder_help_read_mode_footer));
        return sb;
    }

    private static void insertToken(@NonNull TextInputEditText editText, @NonNull String token) {
        int start = Math.max(editText.getSelectionStart(), 0);
        int end = Math.max(editText.getSelectionEnd(), 0);
        Editable editable = editText.getText();
        if (editable != null) {
            editable.replace(Math.min(start, end), Math.max(start, end), token, 0, token.length());
        }
    }

    private static void addTokenChip(
            @NonNull AppCompatActivity activity,
            @NonNull ChipGroup group,
            @NonNull TextInputEditText dataInput,
            @NonNull String label,
            @NonNull String token
    ) {
        Chip chip = (Chip) activity.getLayoutInflater().inflate(R.layout.item_macro_token_chip, group, false);
        chip.setText(label);
        chip.setContentDescription(label);
        chip.setOnClickListener(v -> insertToken(dataInput, token));
        group.addView(chip);
    }

    public static void show(
            @NonNull AppCompatActivity activity,
            @NonNull MacrosManager macrosManager,
            @Nullable Macro macro,
            @Nullable Runnable onSaved
    ) {
        final boolean isEditing = macro != null;
        final long[] scheduledAtMillis = {isEditing ? macro.scheduledAtMillis : 0L};

        View root = activity.getLayoutInflater().inflate(R.layout.bottomsheet_create_macro, null, false);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(root);

        TextView sheetTitle = root.findViewById(R.id.create_macro_sheet_title);
        sheetTitle.setText(isEditing ? activity.getString(R.string.macros_sheet_title_edit)
                : activity.getString(R.string.macros_sheet_title_new));

        ImageButton info = root.findViewById(R.id.create_macro_info);
        if (info != null) {
            info.setOnClickListener(v -> MyShortcutsReorderHelpReadModeDialog.show(
                    activity, formatMacroHelpReadText(activity)));
        }

        TextView preview = root.findViewById(R.id.create_macro_preview);
        TextInputEditText nameInput = root.findViewById(R.id.create_macro_name_input);
        TextInputEditText dataInput = root.findViewById(R.id.create_macro_data_input);
        ChipGroup tokenChips = root.findViewById(R.id.create_macro_token_chips);
        TextView intervalLabel = root.findViewById(R.id.create_macro_interval_label);
        SeekBar intervalSeek = root.findViewById(R.id.create_macro_interval_seek);
        SwitchCompat schedulerSwitch = root.findViewById(R.id.create_macro_scheduler_switch);
        TextView scheduleAtText = root.findViewById(R.id.create_macro_schedule_at);
        MaterialButton pickTimeButton = root.findViewById(R.id.create_macro_pick_time);
        TextInputEditText repeatCountInput = root.findViewById(R.id.create_macro_repeat_count);
        TextInputEditText repeatIntervalInput = root.findViewById(R.id.create_macro_repeat_interval);
        View repeatCountLayout = root.findViewById(R.id.create_macro_repeat_count_layout);
        View repeatIntervalLayout = root.findViewById(R.id.create_macro_repeat_interval_layout);
        MaterialButton cancel = root.findViewById(R.id.create_macro_cancel);
        MaterialButton save = root.findViewById(R.id.create_macro_save);

        String[][] keyEntries = {
                {"⎇ Alt", "<ALT>"}, {"^ Ctrl", "<CTRL>"}, {"⇧ Shift", "<SHIFT>"}, {"⌘ Cmd", "<CMD>"},
                {"</ALT>", "</ALT>"}, {"</CTRL>", "</CTRL>"}, {"</SHIFT>", "</SHIFT>"}, {"</CMD>", "</CMD>"},
                {"⎋ Esc", "<ESC>"}, {"⌫ Back", "<BACK>"}, {"⏎ Enter", "<ENTER>"}, {"␣ Space", "<SPACE>"},
                {"←", "<LEFT>"}, {"→", "<RIGHT>"}, {"↑", "<UP>"}, {"↓", "<DOWN>"},
                {"⇱ Home", "<HOME>"}, {"⇲ End", "<END>"},
                {"⏱ 1s", "<DELAY1S>"}, {"⏱ 2s", "<DELAY2S>"}, {"⏱ 5s", "<DELAY5S>"}, {"⏱ 10s", "<DELAY10S>"}
        };
        for (String[] keyEntry : keyEntries) {
            addTokenChip(activity, tokenChips, dataInput, keyEntry[0], keyEntry[1]);
        }

        int initialIntervalMs = isEditing && macro.intervalMs > 0 ? macro.intervalMs : 100;
        intervalSeek.setProgress(Math.max(0, Math.min(99, (initialIntervalMs / 10) - 1)));
        intervalLabel.setText(activity.getString(R.string.macros_editor_interval_ms,
                (intervalSeek.getProgress() + 1) * 10));

        pickTimeButton.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            if (scheduledAtMillis[0] > 0L) {
                c.setTimeInMillis(scheduledAtMillis[0]);
            }

            DatePickerDialog datePicker = new DatePickerDialog(activity,
                    (view, year, month, dayOfMonth) -> {
                        TimePickerDialog timePicker = new TimePickerDialog(activity,
                                (tpView, hour, minute) -> {
                                    Calendar selected = Calendar.getInstance();
                                    selected.set(year, month, dayOfMonth, hour, minute, 0);
                                    scheduledAtMillis[0] = selected.getTimeInMillis();
                                    scheduleAtText.setText(activity.getString(R.string.macros_editor_run_at_line,
                                            formatScheduleTime(scheduledAtMillis[0])));
                                },
                                c.get(Calendar.HOUR_OF_DAY),
                                c.get(Calendar.MINUTE),
                                true);
                        timePicker.show();
                    },
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            datePicker.show();
        });

        if (isEditing) {
            nameInput.setText(macro.name);
            Editable ne = nameInput.getText();
            if (ne != null) {
                nameInput.setSelection(ne.length());
            }
            dataInput.setText(macro.data != null ? macro.data : "");
            schedulerSwitch.setChecked(macro.isScheduled);
            if (macro.scheduledAtMillis > 0L) {
                scheduleAtText.setText(activity.getString(R.string.macros_editor_run_at_line,
                        formatScheduleTime(macro.scheduledAtMillis)));
            } else {
                scheduleAtText.setText(R.string.macros_editor_run_at_not_set);
            }
            if (macro.repeatCount > 0) {
                repeatCountInput.setText(String.valueOf(macro.repeatCount));
            }
            if (macro.repeatIntervalSeconds > 0) {
                repeatIntervalInput.setText(String.valueOf(macro.repeatIntervalSeconds));
            }
        } else {
            schedulerSwitch.setChecked(false);
            scheduleAtText.setText(R.string.macros_editor_run_at_not_set);
            dataInput.setText("");
        }

        Runnable syncSchedulerVisibility = () -> {
            int v = schedulerSwitch.isChecked() ? View.VISIBLE : View.GONE;
            scheduleAtText.setVisibility(v);
            pickTimeButton.setVisibility(v);
            repeatCountLayout.setVisibility(v);
            repeatIntervalLayout.setVisibility(v);
        };
        schedulerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> syncSchedulerVisibility.run());
        syncSchedulerVisibility.run();

        Runnable updatePreview = () -> {
            String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
            String data = dataInput.getText() != null ? dataInput.getText().toString().trim() : "";
            if (name.isEmpty() && data.isEmpty()) {
                preview.setText(activity.getString(R.string.macros_preview_sheet_empty));
            } else if (name.isEmpty()) {
                preview.setText(ellipsize(data, 160));
            } else if (data.isEmpty()) {
                preview.setText(name);
            } else {
                preview.setText(name + "\n" + ellipsize(data, 140));
            }
            int intervalMs = (intervalSeek.getProgress() + 1) * 10;
            boolean canSave = !name.isEmpty() && !data.isEmpty()
                    && !buildKeyEventsFromMacroData(data, intervalMs).isEmpty();
            save.setEnabled(canSave);
        };

        TextWatcher tw = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updatePreview.run();
            }
        };
        nameInput.addTextChangedListener(tw);
        dataInput.addTextChangedListener(tw);
        intervalSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                intervalLabel.setText(activity.getString(R.string.macros_editor_interval_ms,
                        (progress + 1) * 10));
                updatePreview.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        cancel.setOnClickListener(v -> dialog.dismiss());

        save.setOnClickListener(v -> {
            String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
            String data = dataInput.getText() != null ? dataInput.getText().toString() : "";
            if (name.isEmpty()) {
                Toast.makeText(activity, R.string.macros_toast_name_required, Toast.LENGTH_SHORT).show();
                return;
            }
            if (data.trim().isEmpty()) {
                Toast.makeText(activity, R.string.macros_toast_data_required, Toast.LENGTH_SHORT).show();
                return;
            }

            int intervalMs = (intervalSeek.getProgress() + 1) * 10;
            boolean isScheduled = schedulerSwitch.isChecked();
            int repeatCount = parseIntOrZero(
                    repeatCountInput.getText() != null ? repeatCountInput.getText().toString() : "");
            long repeatIntervalSeconds = parseLongOrZero(
                    repeatIntervalInput.getText() != null ? repeatIntervalInput.getText().toString() : "");

            List<KeyEvent> events = buildKeyEventsFromMacroData(data, intervalMs);
            if (events.isEmpty()) {
                Toast.makeText(activity, R.string.macros_toast_no_events, Toast.LENGTH_LONG).show();
                return;
            }

            boolean saved;
            if (isEditing) {
                saved = macrosManager.updateMacro(
                        macro.id,
                        name,
                        data,
                        intervalMs,
                        isScheduled,
                        scheduledAtMillis[0],
                        repeatCount,
                        repeatIntervalSeconds,
                        events
                );
            } else {
                macrosManager.createMacro(
                        name,
                        data,
                        intervalMs,
                        isScheduled,
                        scheduledAtMillis[0],
                        repeatCount,
                        repeatIntervalSeconds,
                        events
                );
                saved = true;
            }

            if (saved) {
                Toast.makeText(activity,
                        isEditing ? R.string.macros_toast_updated : R.string.macros_toast_added,
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                if (onSaved != null) {
                    onSaved.run();
                }
            } else {
                Toast.makeText(activity, R.string.macros_toast_save_failed, Toast.LENGTH_SHORT).show();
            }
        });

        updatePreview.run();
        dialog.show();
    }

    @NonNull
    private static String ellipsize(@NonNull String s, int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, Math.max(0, maxLen - 1)) + "…";
    }

    private static String formatScheduleTime(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return String.format(
                Locale.getDefault(),
                "%04d-%02d-%02d %02d:%02d",
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE)
        );
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @NonNull
    private static List<KeyEvent> buildKeyEventsFromMacroData(String data, int intervalMs) {
        List<KeyEvent> events = new ArrayList<>();
        Pattern p = Pattern.compile("<[^>]+>|.", Pattern.DOTALL);
        Matcher m = p.matcher(data);

        int activeModifiers = 0;
        long timestamp = 0;

        while (m.find()) {
            String token = m.group();
            if (token == null || token.isEmpty()) {
                continue;
            }

            switch (token) {
                case "<CTRL>": activeModifiers |= 0x01; continue;
                case "<SHIFT>": activeModifiers |= 0x02; continue;
                case "<ALT>": activeModifiers |= 0x04; continue;
                case "<CMD>": activeModifiers |= 0x08; continue;
                case "</CTRL>": activeModifiers &= ~0x01; continue;
                case "</SHIFT>": activeModifiers &= ~0x02; continue;
                case "</ALT>": activeModifiers &= ~0x04; continue;
                case "</CMD>": activeModifiers &= ~0x08; continue;
                case "<DELAY1S>": timestamp += 1000; continue;
                case "<DELAY2S>": timestamp += 2000; continue;
                case "<DELAY5S>": timestamp += 5000; continue;
                case "<DELAY10S>": timestamp += 10000; continue;
                default: break;
            }

            int keyCode = mapTokenToHidCode(token);
            int modifiers = activeModifiers;

            if (keyCode < 0 && token.length() == 1) {
                char c = token.charAt(0);
                keyCode = mapCharToHidCode(c);
                if (needsShift(c)) {
                    modifiers |= 0x02;
                }
            }

            if (keyCode >= 0) {
                events.add(new KeyEvent(keyCode, modifiers, timestamp));
                timestamp += intervalMs;
            } else {
                Log.w(TAG, "Unsupported macro token: " + token);
            }
        }

        return events;
    }

    private static int mapTokenToHidCode(String token) {
        switch (token) {
            case "<ESC>": return 41;
            case "<BACK>": return 42;
            case "<ENTER>": return 40;
            case "<SPACE>": return 44;
            case "<LEFT>": return 80;
            case "<RIGHT>": return 79;
            case "<UP>": return 82;
            case "<DOWN>": return 81;
            case "<HOME>": return 74;
            case "<END>": return 77;
            case "\n": return 40;
            case "\t": return 43;
            default: return -1;
        }
    }

    private static int mapCharToHidCode(char c) {
        if (c >= 'a' && c <= 'z') {
            return 4 + (c - 'a');
        }
        if (c >= 'A' && c <= 'Z') {
            return 4 + (c - 'A');
        }
        if (c >= '1' && c <= '9') {
            return 30 + (c - '1');
        }
        if (c == '0') {
            return 39;
        }

        switch (c) {
            case ' ': return 44;
            case '-': case '_': return 45;
            case '=': case '+': return 46;
            case '[': case '{': return 47;
            case ']': case '}': return 48;
            case '\\': case '|': return 49;
            case ';': case ':': return 51;
            case '\'': case '"': return 52;
            case ',': case '<': return 54;
            case '.': case '>': return 55;
            case '/': case '?': return 56;
            case '`': case '~': return 53;
            case '!': return 30;
            case '@': return 31;
            case '#': return 32;
            case '$': return 33;
            case '%': return 34;
            case '^': return 35;
            case '&': return 36;
            case '*': return 37;
            case '(': return 38;
            case ')': return 39;
            default: return -1;
        }
    }

    private static boolean needsShift(char c) {
        if (Character.isUpperCase(c)) {
            return true;
        }
        return "~!@#$%^&*()_+{}|:\"<>?".indexOf(c) >= 0;
    }
}
