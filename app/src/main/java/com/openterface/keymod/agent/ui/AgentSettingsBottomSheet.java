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

    private static final String PREF_AI_PROVIDER      = "ai_provider";
    private static final String PREF_AI_ENDPOINT      = "ai_endpoint";
    private static final String PREF_AI_MODEL         = "ai_model";
    private static final String PREF_MAX_STEPS        = "agent_max_steps";
    private static final String PREF_MAX_RETRIES      = "agent_max_retries";
    private static final String PREF_PROMPT_TERMINAL  = "agent_prompt_terminal";
    private static final String PREF_PROMPT_HID       = "agent_prompt_hid";

    // ── Provider definitions ─────────────────────────────────────────────
    //
    // MUST match ProviderAdapterFactory.ADAPTER_NAMES order and
    // R.array.settings_ai_provider_names in strings.xml:
    //   0: OpenAI  1: Anthropic  2: Google  3: Mistral
    //   4: Groq    5: DashScope  6: DeepSeek  7: Custom

    private static final String[] PROVIDER_NAMES = {
            "OpenAI", "Anthropic", "Google", "Mistral",
            "Groq", "DashScope", "DeepSeek", "Custom"
    };

    private static final String[] PROVIDER_MODELS = {
            "gpt-4o", "claude-3-5-sonnet-20241022", "gemini-2.0-flash", "mistral-large-latest",
            "llama-3.3-70b-versatile", "qwen-plus", "deepseek-chat", ""
    };

    private static final String[] PROVIDER_ENDPOINTS = {
            "https://api.openai.com/v1",
            "https://api.anthropic.com/v1",
            "https://generativelanguage.googleapis.com/v1beta",
            "https://api.mistral.ai/v1",
            "https://api.groq.com/openai/v1",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "https://api.deepseek.com/v1",
            ""
    };

    private static final int PROVIDER_CUSTOM_INDEX = PROVIDER_NAMES.length - 1;

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
     * Only the Custom provider (local services like Ollama) does not need one.
     */
    private static boolean providerNeedsApiKey(int index) {
        return index != PROVIDER_CUSTOM_INDEX;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        // Full window, no ratio calculation — consistent portrait/landscape
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setDimAmount(0.5f);
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
        selectedProviderIndex = prefs.getInt(PREF_AI_PROVIDER, 0);
        if (selectedProviderIndex < 0 || selectedProviderIndex >= PROVIDER_NAMES.length) {
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
        prefs.edit()
                .putInt(PREF_AI_PROVIDER, index)
                .putString(PREF_AI_MODEL, PROVIDER_MODELS[index])
                .putString(PREF_AI_ENDPOINT, PROVIDER_ENDPOINTS[index])
                .apply();
        // Clear API key for providers that don't need one (Custom / local)
        if (!providerNeedsApiKey(index)) {
            prefs.edit().putString("ai_api_key_" + index, "").apply();
        }
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

        for (int i = 0; i < PROVIDER_NAMES.length; i++) {
            final int idx = i;

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
            nameView.setText(PROVIDER_NAMES[i]);
            nameView.setTextSize(16);
            nameView.setTextColor(textPrimary);
            nameView.setTypeface(nameView.getTypeface(), android.graphics.Typeface.BOLD);

            // Model Name → .secondary (always secondary text color)
            TextView modelView = new TextView(requireContext());
            modelView.setText(PROVIDER_MODELS[i]);
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
            if (i < PROVIDER_NAMES.length - 1) {
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
                + "| kind      | payload       | Purpose                    |\n"
                + "|-----------|---------------|----------------------------|\n"
                + "| terminal  | shell command | Execute via SSH, output is captured |\n\n"
                + "Use only `terminal` kind for SSH tasks. Return ONLY valid JSON.";
    }

    @NonNull
    private String getDefaultHidPrompt() {
        return "You are a keyboard control assistant. The user controls a target computer through a wireless keyboard.\n\n"
                + "## Output Format\n"
                + "Return JSON:\n"
                + "{\n"
                + "  \"summary\": \"one-line summary\",\n"
                + "  \"steps\": [\n"
                + "    {\"title\": \"step title\", \"keys\": \"<CMD>s</CMD>\", \"kind\": \"hid\"}\n"
                + "  ]\n"
                + "}\n\n"
                + "## Key Syntax\n"
                + "- Modifier keys: <CMD>, <CTRL>, <ALT>, <SHIFT>\n"
                + "- Special keys: <ENTER>, <TAB>, <ESC>, <BACKSPACE>, <DELETE>\n"
                + "- Arrow keys: <UP>, <DOWN>, <LEFT>, <RIGHT>\n\n"
                + "Return ONLY valid JSON, no additional text.";
    }
}
