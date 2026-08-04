package com.openterface.keymod.agent.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
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
    private static final String PREF_AI_API_KEY       = "ai_api_key";
    private static final String PREF_MAX_STEPS        = "agent_max_steps";
    private static final String PREF_MAX_RETRIES      = "agent_max_retries";
    private static final String PREF_PROMPT_TERMINAL  = "agent_prompt_terminal";
    private static final String PREF_PROMPT_HID       = "agent_prompt_hid";

    // ── Provider definitions ─────────────────────────────────────────────
    //
    // TODO: Unify this list with R.array.settings_ai_provider_names in strings.xml.
    //  Currently this list uses different indices than AgentController.PROVIDER_NAMES
    //  and the canonical resource array, causing provider index mismatch when the same
    //  SharedPreferences keys (ai_provider, ai_model, ai_endpoint) are read by different
    //  components. See code review F9/F10.

    private static final String[] PROVIDER_NAMES = {
            "OpenAI", "Ollama (Local)", "DeepSeek", "Qwen (Alibaba)",
            "GLM (Zhipu AI)", "Local Qwen 0.6B", "Local Qwen 1.7B"
    };

    private static final String[] PROVIDER_MODELS = {
            "gpt-4o", "llama3", "deepseek-chat", "qwen-plus",
            "glm-4-flash", "Qwen3-0.6B-4bit", "Qwen3-1.7B-4bit"
    };

    private static final String[] PROVIDER_ENDPOINTS = {
            "https://api.openai.com/v1",
            "http://127.0.0.1:11434/v1",
            "https://api.deepseek.com/v1",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "https://open.bigmodel.cn/api/paas/v4",
            "", ""
    };

    private static final int DEFAULT_MAX_STEPS   = 10;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int MIN_STEPS = 3,  MAX_STEPS = 30;
    private static final int MIN_RETRIES = 0, MAX_RETRIES = 5;

    // ── Views ───────────────────────────────────────────────────────────

    private RadioGroup providerRadioGroup;
    private TextView maxStepsValue;
    private TextView maxRetriesValue;
    private TabLayout promptTabs;
    private EditText promptEditor;

    private SharedPreferences prefs;
    private int selectedProviderIndex = 0;
    private int maxSteps = DEFAULT_MAX_STEPS;
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private int currentPromptTab = 0;
    private String terminalPrompt = "";
    private String hidPrompt = "";

    // ── Lifecycle ────────────────────────────────────────────────────────

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            BottomSheetBehavior<?> behavior = bsd.getBehavior();

            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            boolean isPortrait = dm.heightPixels > dm.widthPixels;
            // Portrait: show ~10% of screen  →  sheet = 90%
            // Landscape: show ~4% of screen  →  sheet = 96%
            int maxHeight = (int) (dm.heightPixels * (isPortrait ? 0.90 : 0.96));
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
            }

            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setHideable(true);

            // ─ Rounded top corners (16dp) via ViewOutlineProvider ──
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                final float cornerRadius = 16 * dm.density;
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
        setupExecutionLimits();
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
        terminalPrompt = prefs.getString(PREF_PROMPT_TERMINAL, getDefaultTerminalPrompt());
        hidPrompt = prefs.getString(PREF_PROMPT_HID, getDefaultHidPrompt());
    }

    private void saveSettings() {
        prefs.edit()
                .putInt(PREF_AI_PROVIDER, selectedProviderIndex)
                .putString(PREF_AI_MODEL, PROVIDER_MODELS[selectedProviderIndex])
                .putString(PREF_AI_ENDPOINT, PROVIDER_ENDPOINTS[selectedProviderIndex])
                .putInt(PREF_MAX_STEPS, maxSteps)
                .putInt(PREF_MAX_RETRIES, maxRetries)
                .putString(PREF_PROMPT_TERMINAL, terminalPrompt)
                .putString(PREF_PROMPT_HID, hidPrompt)
                .apply();

        boolean needsKey = selectedProviderIndex != 1
                        && selectedProviderIndex != 5
                        && selectedProviderIndex != 6;
        if (!needsKey) {
            prefs.edit().putString(PREF_AI_API_KEY + "_" + selectedProviderIndex, "").apply();
        }

        dismiss();
    }

    // ── View binding ─────────────────────────────────────────────────────

    private void bindViews(@NonNull View view) {
        // Replace toolbar with inline header
        MaterialToolbar toolbar = view.findViewById(R.id.agent_settings_toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> dismiss());
            toolbar.setTitle(R.string.agent_settings_title);

            // Cancel button (left)
            View navView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.agent_settings_toolbar_nav, toolbar, false);
            navView.findViewById(com.google.android.material.R.id.design_menu_item_text);
            MaterialButton cancelBtn = navView.findViewById(R.id.agent_settings_cancel_btn);
            cancelBtn.setOnClickListener(v -> dismiss());
            toolbar.addView(navView, 0);

            // Save button (right)
            View actionView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.agent_settings_toolbar_action, toolbar, false);
            MaterialButton saveBtn = actionView.findViewById(R.id.agent_settings_save_btn);
            saveBtn.setOnClickListener(v -> saveSettings());
            toolbar.addView(actionView);
        }

        providerRadioGroup = view.findViewById(R.id.agent_provider_radio_group);
        maxStepsValue = view.findViewById(R.id.agent_max_steps_value);
        maxRetriesValue = view.findViewById(R.id.agent_max_retries_value);
        view.findViewById(R.id.agent_max_steps_minus)
                .setOnClickListener(v -> { if (maxSteps > MIN_STEPS) { maxSteps--; updateLimitDisplay(); } });
        view.findViewById(R.id.agent_max_steps_plus)
                .setOnClickListener(v -> { if (maxSteps < MAX_STEPS) { maxSteps++; updateLimitDisplay(); } });
        view.findViewById(R.id.agent_max_retries_minus)
                .setOnClickListener(v -> { if (maxRetries > MIN_RETRIES) { maxRetries--; updateLimitDisplay(); } });
        view.findViewById(R.id.agent_max_retries_plus)
                .setOnClickListener(v -> { if (maxRetries < MAX_RETRIES) { maxRetries++; updateLimitDisplay(); } });
        promptTabs = view.findViewById(R.id.agent_prompt_tabs);
        promptEditor = view.findViewById(R.id.agent_prompt_editor);
    }

    // ── AI Provider list ─────────────────────────────────────────────────

    private void setupProviderList() {
        providerRadioGroup.clearCheck();
        for (int i = 0; i < PROVIDER_NAMES.length; i++) {
            RadioButton rb = new RadioButton(requireContext());
            rb.setId(View.generateViewId());
            rb.setText(PROVIDER_NAMES[i]);
            rb.setTextSize(16);

            TextView modelLabel = new TextView(requireContext());
            modelLabel.setText(PROVIDER_MODELS[i]);
            modelLabel.setTextSize(13);
            modelLabel.setTextColor(getResources().getColor(R.color.text_secondary, null));

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(16, 8, 16, 8);
            row.addView(rb);
            row.addView(modelLabel);

            rb.setChecked(i == selectedProviderIndex);
            final int idx = i;
            rb.setOnClickListener(v -> {
                selectedProviderIndex = idx;
                providerRadioGroup.clearCheck();
                rb.setChecked(true);
            });

            providerRadioGroup.addView(row, new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    // ── Execution Limits ────────────────────────────────────────────────

    private void setupExecutionLimits() {
        updateLimitDisplay();
    }

    private void updateLimitDisplay() {
        maxStepsValue.setText(String.valueOf(maxSteps));
        maxRetriesValue.setText(String.valueOf(maxRetries));
    }

    // ── System Prompts tabs ──────────────────────────────────────────────

    private void setupPromptTabs() {
        promptTabs.addTab(promptTabs.newTab().setText(R.string.agent_settings_prompt_terminal));
        promptTabs.addTab(promptTabs.newTab().setText(R.string.agent_settings_prompt_hid));
        promptTabs.selectTab(promptTabs.getTabAt(0));
        promptEditor.setText(terminalPrompt);

        promptTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (currentPromptTab == 0) terminalPrompt = promptEditor.getText().toString();
                else hidPrompt = promptEditor.getText().toString();
                currentPromptTab = tab.getPosition();
                promptEditor.setText(currentPromptTab == 0 ? terminalPrompt : hidPrompt);
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
