package com.openterface.keymod.agent.ui;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.openterface.keymod.R;
import com.openterface.keymod.agent.settings.AIConfigProvider;
import com.openterface.keymod.agent.settings.AIProvider;
import com.openterface.keymod.agent.settings.AIProviderManager;
import com.openterface.keymod.util.BottomSheetBlurHelper;

/**
 * BottomSheet dialog for Agent Settings.
 *
 * <p>Shows settings as a modal sheet on top of AgentView with three sections:
 * <ul>
 *   <li><b>AI Provider</b> — radio list</li>
 *   <li><b>Execution Limits</b> — Max steps (3–30) / Max retries (0–5)</li>
 *   <li><b>System Prompts</b> — tabbed text editor (Terminal / HID)</li>
 * </ul>
 */
public class AgentSettingsBottomSheet extends BottomSheetDialogFragment {

    // ── SharedPreferences keys ───────────────────────────────────────────

    private static final String PREF_MAX_STEPS        = "agent_max_steps";
    private static final String PREF_MAX_RETRIES      = "agent_max_retries";
    private static final String PREF_PROMPT_TERMINAL  = "agent_prompt_terminal";
    private static final String PREF_PROMPT_HID       = "agent_prompt_hid";

    // ── Provider definitions ─────────────────────────────────────────────
    // Provider list is now managed by AIProviderManager
    // This class uses the unified AIConfigProvider for all AI settings

    private static final int DEFAULT_MAX_STEPS   = 10;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int MIN_STEPS = 3,  MAX_STEPS = 30;
    private static final int MIN_RETRIES = 0, MAX_RETRIES = 5;

    // ── Views ───────────────────────────────────────────────────────────

    private LinearLayout providerList;
    private TextView maxStepsValue;
    private TextView maxRetriesValue;
    private TabLayout promptTabs;
    private EditText promptEditor;
    private MaterialButton saveBtn;

    /** Checkmark indicators per provider row — indexed by provider position. */
    private final java.util.List<ImageView> providerChecks = new java.util.ArrayList<>();

    private SharedPreferences prefs;
    private int selectedProviderIndex = 0;
    private int maxSteps = DEFAULT_MAX_STEPS;
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private int currentPromptTab = 0;
    private String draftTerminalPrompt = "";
    private String draftHidPrompt = "";
    /** Original prompts loaded from SharedPreferences — used to detect unsaved changes. */
    private String originalTerminalPrompt = "";
    private String originalHidPrompt = "";
    private boolean hasUnsavedChanges = false;
    private boolean isUpdatingEditor = false;

    /**
     * Check if a provider at the given index requires an API key.
     * Uses AIProviderManager to check the provider's apiKeyOptional field.
     */
    private static boolean providerNeedsApiKey(int index) {
        // This method is no longer needed since we use AIProvider.apiKeyOptional
        // Keep for backward compatibility, but it's not used anymore
        return true;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        // Full window + enhanced dim behind sheet (blur not supported by BottomSheetDialog)
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setDimAmount(0.55f);
        }

        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            BottomSheetBehavior<?> behavior = bsd.getBehavior();

            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setHideable(true);

            // ─ Rounded top corners (16dp) via ViewOutlineProvider ──
            float density = getResources().getDisplayMetrics().density;
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                final float cornerRadius = 16 * density;
                bottomSheet.setClipToOutline(true);
                bottomSheet.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(android.view.View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(),
                                (int) (view.getHeight() + cornerRadius), cornerRadius);
                    }
                });

                android.view.View parent = (android.view.View) bottomSheet.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).setClipChildren(false);
                    parent.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }
            }
        });

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Apply background blur effect (API 31+) — paired with removeBlur() in onStop()
        BottomSheetBlurHelper.applyBlur(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        // Remove background blur effect
        BottomSheetBlurHelper.removeBlur(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_agent_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        loadSettings();
        bindViews(view);
        setupProviderList();
        updateLimitDisplay();
        setupPromptTabs();
    }

    // ── Settings I/O ────────────────────────────────────────────────────

    private void loadSettings() {
        // Use AIProviderManager for provider selection
        AIProviderManager providerManager = AIProviderManager.getInstance(requireContext());
        selectedProviderIndex = providerManager.getSelectedProviderIndex();
        if (selectedProviderIndex < 0 || selectedProviderIndex >= providerManager.getProviderCount()) {
            selectedProviderIndex = 0;
        }
        maxSteps = prefs.getInt(PREF_MAX_STEPS, DEFAULT_MAX_STEPS);
        maxRetries = prefs.getInt(PREF_MAX_RETRIES, DEFAULT_MAX_RETRIES);
        // Draft copies for prompt editing — discarded on Cancel, committed on Save
        originalTerminalPrompt = prefs.getString(PREF_PROMPT_TERMINAL, getDefaultTerminalPrompt());
        originalHidPrompt = prefs.getString(PREF_PROMPT_HID, getDefaultHidPrompt());
        draftTerminalPrompt = originalTerminalPrompt;
        draftHidPrompt = originalHidPrompt;
        hasUnsavedChanges = false;
    }

    /**
     * Save only the prompt drafts. Provider and Stepper changes are saved
     * instantly on interaction (iOS spec conformance).
     */
    private void saveSettings() {
        prefs.edit()
                .putString(PREF_PROMPT_TERMINAL, draftTerminalPrompt)
                .putString(PREF_PROMPT_HID, draftHidPrompt)
                .apply();
        hasUnsavedChanges = false;
        dismiss();
    }

    /** Save provider selection immediately (instant-effect per iOS spec). */
    private void saveProviderInstant(int index) {
        // Use AIProviderManager for provider selection
        AIProviderManager providerManager = AIProviderManager.getInstance(requireContext());
        providerManager.setSelectedProviderIndex(index);
    }

    /** Save execution limits immediately (instant-effect per iOS spec). */
    private void saveLimitsInstant() {
        prefs.edit()
                .putInt(PREF_MAX_STEPS, maxSteps)
                .putInt(PREF_MAX_RETRIES, maxRetries)
                .apply();
    }

    // ── View binding ─────────────────────────────────────────────────────

    private void bindViews(@NonNull View view) {
        // Toolbar: Cancel (left) — Title (center) — Save (right)
        MaterialButton cancelBtn = view.findViewById(R.id.agent_settings_cancel_btn);
        cancelBtn.setOnClickListener(v -> dismiss());

        saveBtn = view.findViewById(R.id.agent_settings_save_btn);
        saveBtn.setEnabled(false);
        saveBtn.setOnClickListener(v -> saveSettings());

        providerList = view.findViewById(R.id.agent_provider_list);
        maxStepsValue = view.findViewById(R.id.agent_max_steps_value);
        maxRetriesValue = view.findViewById(R.id.agent_max_retries_value);
        view.findViewById(R.id.agent_max_steps_minus)
                .setOnClickListener(v -> { if (maxSteps > MIN_STEPS) { maxSteps--; updateLimitDisplay(); saveLimitsInstant(); } });
        view.findViewById(R.id.agent_max_steps_plus)
                .setOnClickListener(v -> { if (maxSteps < MAX_STEPS) { maxSteps++; updateLimitDisplay(); saveLimitsInstant(); } });
        view.findViewById(R.id.agent_max_retries_minus)
                .setOnClickListener(v -> { if (maxRetries > MIN_RETRIES) { maxRetries--; updateLimitDisplay(); saveLimitsInstant(); } });
        view.findViewById(R.id.agent_max_retries_plus)
                .setOnClickListener(v -> { if (maxRetries < MAX_RETRIES) { maxRetries++; updateLimitDisplay(); saveLimitsInstant(); } });
        promptTabs = view.findViewById(R.id.agent_prompt_tabs);
        promptEditor = view.findViewById(R.id.agent_prompt_editor);

        // Detect prompt edits to enable/disable Save button
        promptEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingEditor) return;
                if (currentPromptTab == 0) draftTerminalPrompt = s.toString();
                else draftHidPrompt = s.toString();
                checkUnsavedChanges();
            }
        });
    }

    /** Compare drafts against originals and update Save button enabled state. */
    private void checkUnsavedChanges() {
        boolean changed = !draftTerminalPrompt.equals(originalTerminalPrompt)
                       || !draftHidPrompt.equals(originalHidPrompt);
        if (changed != hasUnsavedChanges) {
            hasUnsavedChanges = changed;
            if (saveBtn != null) saveBtn.setEnabled(hasUnsavedChanges);
        }
    }

    // ── AI Provider list ─────────────────────────────────────────────────

    private void setupProviderList() {
        providerList.removeAllViews();
        providerChecks.clear();
        float density = getResources().getDisplayMetrics().density;

        int textPrimary = getResources().getColor(R.color.text_primary, null);
        int textSecondary = getResources().getColor(R.color.text_secondary, null);

        // Resolve accent color for checkmark
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true);
        int accentColor = tv.data;

        // Use AIProviderManager to get provider list
        AIProviderManager providerManager = AIProviderManager.getInstance(requireContext());
        java.util.List<AIProvider> providers = providerManager.getProviders();

        for (int i = 0; i < providers.size(); i++) {
            final int idx = i;
            AIProvider provider = providers.get(i);

            // ── Row: HStack { name + model, Spacer, checkmark } ──
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int rowPadV = (int) (14 * density);
            int rowPadH = (int) (16 * density);
            row.setPadding(rowPadH, rowPadV, rowPadH, rowPadV);
            row.setClickable(true);
            row.setFocusable(true);
            // Ripple feedback on tap
            int[] attrs = new int[]{android.R.attr.selectableItemBackground};
            android.content.res.TypedArray ta = requireContext().obtainStyledAttributes(attrs);
            row.setBackground(ta.getDrawable(0));
            ta.recycle();

            // Left: name + model column
            LinearLayout textCol = new LinearLayout(requireContext());
            textCol.setOrientation(LinearLayout.VERTICAL);

            // Provider Name → .primary (always primary text color)
            TextView nameView = new TextView(requireContext());
            nameView.setText(provider.name != null ? provider.name : "Unknown");
            nameView.setTextSize(16);
            nameView.setTextColor(textPrimary);
            nameView.setTypeface(nameView.getTypeface(), android.graphics.Typeface.BOLD);

            // Model Name → .secondary (always secondary text color)
            TextView modelView = new TextView(requireContext());
            modelView.setText(provider.modelName != null ? provider.modelName : "");
            modelView.setTextSize(13);
            modelView.setTextColor(textSecondary);

            textCol.addView(nameView);
            textCol.addView(modelView);

            // Checkmark → accent color (theme color)
            ImageView check = new ImageView(requireContext());
            check.setImageResource(R.drawable.ic_check);
            check.setColorFilter(accentColor);
            int checkSize = (int) (22 * density);
            LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(checkSize, checkSize);
            checkLp.setMarginStart((int) (8 * density));
            check.setVisibility(i == selectedProviderIndex ? View.VISIBLE : View.GONE);
            providerChecks.add(check);

            row.addView(textCol, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(check, checkLp);

            // Click → select this provider
            row.setOnClickListener(v -> {
                selectedProviderIndex = idx;
                refreshProviderSelection();
                saveProviderInstant(idx);
            });

            providerList.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            // Divider between rows (not after last)
            if (i < providers.size() - 1) {
                View divider = new View(requireContext());
                divider.setBackgroundColor(getResources().getColor(R.color.agent_settings_divider, null));
                int marginH = (int) (12 * density);
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int) (density));
                divLp.setMargins(marginH, 0, marginH, 0);
                providerList.addView(divider, divLp);
            }
        }
    }

    /** Update only checkmark visibility — text colors stay fixed (.primary / .secondary). */
    private void refreshProviderSelection() {
        for (int i = 0; i < providerChecks.size(); i++) {
            providerChecks.get(i).setVisibility(i == selectedProviderIndex
                    ? View.VISIBLE : View.GONE);
        }
    }

    // ── Execution Limits ────────────────────────────────────────────────

    private void updateLimitDisplay() {
        maxStepsValue.setText(String.valueOf(maxSteps));
        maxRetriesValue.setText(String.valueOf(maxRetries));
    }

    // ── System Prompts tabs ──────────────────────────────────────────────

    private void setupPromptTabs() {
        promptTabs.addTab(promptTabs.newTab().setText(R.string.agent_settings_prompt_terminal));
        promptTabs.addTab(promptTabs.newTab().setText(R.string.agent_settings_prompt_hid));
        promptTabs.selectTab(promptTabs.getTabAt(0));
        promptEditor.setText(draftTerminalPrompt);

        promptTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // Save current editor content into the active draft before switching
                if (currentPromptTab == 0) draftTerminalPrompt = promptEditor.getText().toString();
                else draftHidPrompt = promptEditor.getText().toString();
                currentPromptTab = tab.getPosition();
                // Load the other draft into the editor (suppress TextWatcher change detection)
                isUpdatingEditor = true;
                promptEditor.setText(currentPromptTab == 0 ? draftTerminalPrompt : draftHidPrompt);
                isUpdatingEditor = false;
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    // ── Default prompts ──────────────────────────────────────────────────

    @NonNull
    private String getDefaultTerminalPrompt() {
        return "You are an autonomous agent that executes commands on a remote device via SSH terminal.\n\n"
                + "{{TERMINAL_MODE_CONTEXT}}\n\n"
                + "## Task\n\n"
                + "Break the user's request into concrete shell commands. Output a JSON plan.\n\n"
                + "## Step types\n\n"
                + "| kind | payload | Purpose |\n"
                + "|---|---|---|\n"
                + "| `terminal` | shell command | Execute via SSH, output is captured |\n\n"
                + "Use only `terminal` steps. Do NOT use `hid` or `macro` steps in terminal mode.\n\n"
                + "## OS-specific commands\n\n"
                + "**CRITICAL: Use commands that work on the target OS specified above.**\n\n"
                + "### macOS\n\n"
                + "| Task | Command |\n"
                + "|---|---|\n"
                + "| IP address | `ipconfig getifaddr en0` |\n"
                + "| All interfaces | `ifconfig` |\n"
                + "| Disk space | `df -h` |\n"
                + "| Memory | `vm_stat` |\n"
                + "| CPU load | `top -l 1 -n 0` |\n"
                + "| OS version | `sw_vers` |\n"
                + "| Uptime | `uptime` |\n"
                + "| Processes | `ps aux` |\n"
                + "| Kill process | `kill <pid>` |\n\n"
                + "**DO NOT use Linux commands on macOS:** `hostname -I`, `ip addr`, `free`, `lscpu`, `lsb_release`, `apt`, `yum`, `systemctl`.\n\n"
                + "### Linux\n\n"
                + "| Task | Command |\n"
                + "|---|---|\n"
                + "| IP address | `hostname -I` or `ip addr show` |\n"
                + "| All interfaces | `ip a` |\n"
                + "| Disk space | `df -h` |\n"
                + "| Memory | `free -h` |\n"
                + "| CPU load | `top -bn1 \\| head -20` |\n"
                + "| OS version | `lsb_release -a` or `cat /etc/os-release` |\n"
                + "| Uptime | `uptime` |\n"
                + "| Processes | `ps aux` |\n"
                + "| Kill process | `kill <pid>` |\n"
                + "| Packages | `apt` (Debian/Ubuntu) or `yum`/`dnf` (RHEL) |\n\n"
                + "**DO NOT use macOS commands on Linux:** `ipconfig`, `vm_stat`, `sw_vers`, `brew`.\n\n"
                + "### Windows (CMD)\n\n"
                + "| Task | Command |\n"
                + "|---|---|\n"
                + "| IP address | `ipconfig` |\n"
                + "| Disk space | `wmic logicaldisk get size,freespace,caption` |\n"
                + "| Memory | `systeminfo \\| findstr /C:\"Total Physical Memory\"` |\n"
                + "| OS version | `ver` |\n"
                + "| Processes | `tasklist` |\n"
                + "| Kill process | `taskkill /PID <pid> /F` |\n\n"
                + "**DO NOT use Unix commands on Windows:** `ls`, `cat`, `grep`, `ps`, `kill`, `top`, `df`, `free`.\n\n"
                + "## Response format\n\n"
                + "Respond with a single JSON object inside a ` ```json ` code fence. No prose before or after.\n\n"
                + "```json\n"
                + "{\n"
                + "  \"intro\": \"One-sentence description.\",\n"
                + "  \"steps\": [\n"
                + "    {\n"
                + "      \"kind\": \"terminal\",\n"
                + "      \"title\": \"Run version command\",\n"
                + "      \"payload\": \"uname -a\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n"
                + "```\n\n"
                + "## Constraints\n\n"
                + "- Keep steps minimal — one command per step.\n"
                + "- Do not include destructive commands unless explicitly requested.\n"
                + "- If you cannot fulfill the request, say so in the intro and return empty steps array.";
    }

    @NonNull
    private String getDefaultHidPrompt() {
        return "You are an autonomous agent that controls a computer via BLE keyboard (HID). No SSH terminal is available — commands are typed into the active window.\n\n"
                + "{{TERMINAL_MODE_CONTEXT}}\n\n"
                + "## Task\n\n"
                + "Break the user's request into steps. Output a JSON plan.\n\n"
                + "## Step types\n\n"
                + "| kind | payload | Purpose |\n"
                + "|---|---|---|\n"
                + "| `hid` | keyboard tokens | Send keystrokes, shortcuts, typed text |\n"
                + "| `terminal` | shell command | Type command + Enter (output NOT captured) |\n\n"
                + "Your plan MUST have an `hid` step FIRST to open a terminal app, then `terminal` steps for commands.\n\n"
                + "## Keyboard tokens\n\n"
                + "| Action | Syntax |\n"
                + "|---|---|\n"
                + "| Type text | literal characters |\n"
                + "| Modifier held | `<CMD>c</CMD>`, `<CTRL>s</CTRL>`, `<SHIFT>A</SHIFT>` |\n"
                + "| Chords | `<CTRL><SHIFT>t</SHIFT></CTRL>` |\n"
                + "| Special keys | `<ESC>`, `<ENTER>`, `<BACK>`, `<SPACE>`, `<TAB>`, `<F1>`–`<F12>` |\n"
                + "| Arrows | `<LEFT>`, `<RIGHT>`, `<UP>`, `<DOWN>` |\n"
                + "| Delays | `<DELAY1S>` through `<DELAY10S>` |\n\n"
                + "**IMPORTANT:** Always close modifier tags (`</CMD>`) before typing plain text.\n\n"
                + "## Open terminal (HID step)\n\n"
                + "| OS | HID payload |\n"
                + "|---|---|\n"
                + "| macOS | `<CMD><SPACE></CMD><DELAY1S><CMD>a</CMD><BACK><DELAY1S>terminal<ENTER><DELAY3S>` |\n"
                + "| Linux | `<CTRL><ALT>t<DELAY3S>` |\n"
                + "| Windows | `<WIN>r<DELAY1S>cmd<ENTER><DELAY4S>` |\n\n"
                + "After terminal opens, use `terminal` steps for commands (each gets `<ENTER>` automatically).\n\n"
                + "## Response format\n\n"
                + "Respond with a single JSON object inside a ` ```json ` code fence. No prose.\n\n"
                + "```json\n"
                + "{\n"
                + "  \"intro\": \"One-sentence description.\",\n"
                + "  \"steps\": [\n"
                + "    {\n"
                + "      \"kind\": \"hid\",\n"
                + "      \"title\": \"Open Terminal\",\n"
                + "      \"payload\": \"<CMD><SPACE></CMD><DELAY1S><CMD>a</CMD><BACK><DELAY1S>terminal<ENTER><DELAY3S>\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"kind\": \"terminal\",\n"
                + "      \"title\": \"Check version\",\n"
                + "      \"payload\": \"uname -a\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n"
                + "```\n\n"
                + "## Constraints\n\n"
                + "- First step MUST be hid to open terminal.\n"
                + "- Keep steps minimal.\n"
                + "- Since output is NOT captured, avoid commands that depend on reading previous output.";
    }
}
