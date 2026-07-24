package com.openterface.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.util.Log;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.openterface.keymod.R;
import com.openterface.keymod.SettingsActivity;
import com.openterface.keymod.agent.AgentDemoPlayer;
import com.openterface.keymod.agent.AgentDemoScript;
import com.openterface.keymod.agent.AgentDemoScriptRegistry;
import com.openterface.keymod.agent.AgentMessage;
import com.openterface.keymod.agent.AgentMessageAdapter;
import com.openterface.keymod.agent.AgentPlanStep;
import com.openterface.keymod.agent.core.AgentController;
import com.openterface.keymod.agent.core.AgentPlan;
import com.openterface.keymod.agent.core.AgentState;
import com.openterface.keymod.agent.llm.LlmHttpClient;
import com.openterface.keymod.agent.llm.ProviderAdapterFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Agent mode marketing MVP — curated demo scripts with Plan/Act UI. */
public class AgentFragment extends Fragment {

    private static final String TAG = "AgentFragment";
    private static final String PREF_AI_API_KEY = "ai_api_key";
    private static final String PREF_AI_ENDPOINT = "ai_endpoint";
    private static final String PREF_AI_MODEL = "ai_model";
    private static final String PREF_AI_PROVIDER = "ai_provider";

    public static final String ARG_DEMO_SCRIPT = "agent_demo_script";
    public static final String ARG_AUTO_PLAY = "agent_demo_auto_play";
    public static final String ARG_AUTO_APPROVE = "agent_demo_auto_approve";
    public static final String ARG_SKIP_GATE = "agent_demo_skip_gate";
    public static final String ARG_PAUSE_AT = "agent_demo_pause_at";

    private static final String PREF_DEMO_TOKEN_CONNECTED = "agent_demo_token_connected";

    private ScrollView gateOverlay;
    private View demoPickerScroll;
    private LinearLayout demoPickerContainer;
    private RecyclerView messagesList;
    private TextView emptyHint;
    private EditText inputField;
    private ImageButton sendButton;
    private View sessionBar;
    private TextView sessionHint;
    private TextView connectionPill;

    private AgentMessageAdapter adapter;
    private AgentDemoPlayer demoPlayer;
    @Nullable private AgentDemoScript pendingAutoScript;

    // Day 3: Real Agent engine
    @Nullable private AgentController agentController;
    private boolean realEngineEnabled = false;
    private final List<AgentMessage> chatMessages = new ArrayList<>();
    @Nullable private ExecutorService verifyExecutor;
    private volatile boolean verifyRunning = false;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @NonNull
    public static AgentFragment newInstance(
            @Nullable String scriptId,
            boolean autoPlay,
            boolean autoApprove,
            boolean skipGate,
            @Nullable String pauseAt) {
        AgentFragment fragment = new AgentFragment();
        Bundle args = new Bundle();
        if (scriptId != null) {
            args.putString(ARG_DEMO_SCRIPT, scriptId);
        }
        args.putBoolean(ARG_AUTO_PLAY, autoPlay);
        args.putBoolean(ARG_AUTO_APPROVE, autoApprove);
        args.putBoolean(ARG_SKIP_GATE, skipGate);
        if (pauseAt != null) {
            args.putString(ARG_PAUSE_AT, pauseAt);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_agent, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupMessagesList();
        applySessionBarLayout();
        setupGate(view);
        setupDemoPicker();
        applyLaunchArgs();
        refreshEngineState();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-check API config when returning from settings
        refreshEngineState();
    }

    /**
     * Re-evaluate engine state and gate visibility.
     * Called on resume to pick up settings changes.
     */
    private void refreshEngineState() {
        if (inputField == null) return; // views not bound yet
        if (verifyRunning) return;      // verification already in progress

        if (!isApiKeyConfigured()) {
            disableEngine();
            return;
        }

        // API field is non-empty — verify it actually works
        verifyRunning = true;
        if (verifyExecutor == null) {
            verifyExecutor = Executors.newSingleThreadExecutor();
        }
        inputField.setHint(R.string.agent_state_thinking);
        inputField.setEnabled(false);
        sendButton.setEnabled(false);

        verifyExecutor.submit(() -> {
            boolean valid = testApiConnection();
            verifyRunning = false;

            if (!isAdded()) return;
            mainHandler.post(() -> {
                if (valid) {
                    enableEngine();
                } else {
                    disableEngine();
                    Toast.makeText(requireContext(),
                            "API connection failed. Please check your settings.",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /** Test API connection using LlmHttpClient.testConnection() */
    private boolean testApiConnection() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            String endpoint = prefs.getString(PREF_AI_ENDPOINT, "https://api.openai.com/v1");
            String model = prefs.getString(PREF_AI_MODEL, "gpt-4o-mini");
            int providerIdx = prefs.getInt(PREF_AI_PROVIDER, 0);
            String apiKey = prefs.getString(PREF_AI_API_KEY + "_" + providerIdx, "");

            String providerName = getProviderNameFromIndex(providerIdx);
            LlmHttpClient client = new LlmHttpClient(apiKey, endpoint);
            client.testConnection(model, providerName);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "API verification failed", e);
            return false;
        }
    }

    @NonNull
    private String getProviderNameFromIndex(int index) {
        // Use canonical adapter names (English, locale-independent)
        // instead of localized display names from R.array
        String[] names = ProviderAdapterFactory.ADAPTER_NAMES;
        if (index >= 0 && index < names.length) return names[index];
        return "OpenAI";
    }

    private void enableEngine() {
        if (!realEngineEnabled) {
            realEngineEnabled = true;
            agentController = new AgentController(requireContext());
            setupAgentController();
        }
        inputField.setEnabled(true);
        sendButton.setEnabled(true);
        inputField.setHint(R.string.agent_input_hint_real);
        sendButton.setOnClickListener(v -> submitToAgent());
        showGate(false);
    }

    private void disableEngine() {
        if (realEngineEnabled) {
            if (agentController != null) {
                agentController.shutdown();
                agentController = null;
            }
            realEngineEnabled = false;
            chatMessages.clear();
        }
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        inputField.setHint(R.string.agent_input_hint);
        sendButton.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.agent_input_demo_only, Toast.LENGTH_SHORT).show());
        showGate(true);
    }

    private void bindViews(@NonNull View view) {
        gateOverlay = view.findViewById(R.id.agent_gate_overlay);
        demoPickerScroll = view.findViewById(R.id.agent_demo_picker_scroll);
        demoPickerContainer = view.findViewById(R.id.agent_demo_picker_container);
        messagesList = view.findViewById(R.id.agent_messages_list);
        emptyHint = view.findViewById(R.id.agent_empty_hint);
        inputField = view.findViewById(R.id.agent_input);
        sendButton = view.findViewById(R.id.agent_send_button);
        sessionBar = view.findViewById(R.id.agent_session_bar);
        sessionHint = view.findViewById(R.id.agent_session_hint);
        connectionPill = view.findViewById(R.id.agent_connection_pill);
    }

    private void applySessionBarLayout() {
        if (sessionBar == null) {
            return;
        }
        int paddingH = getResources().getDimensionPixelSize(R.dimen.agent_session_bar_padding_h);
        int paddingV = getResources().getDimensionPixelSize(R.dimen.agent_session_bar_padding_v);
        sessionBar.setPadding(paddingH, paddingV, paddingH, paddingV);
        if (sessionHint != null) {
            boolean isLandscape =
                    getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            sessionHint.setText(isLandscape ? "" : getString(R.string.agent_subtitle));
        }
    }

    private void setupMessagesList() {
        adapter = new AgentMessageAdapter();
        adapter.setActBarListener(new AgentMessageAdapter.ActBarListener() {
            @Override
            public void onApprove() {
                if (realEngineEnabled && agentController != null) {
                    agentController.approveAndRun();
                } else if (demoPlayer != null) {
                    demoPlayer.approveAndRun();
                }
            }

            @Override
            public void onEditPlan() {
                Toast.makeText(requireContext(), R.string.agent_edit_plan_coming_soon, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancel() {
                if (realEngineEnabled && agentController != null) {
                    agentController.cancel();
                } else if (demoPlayer != null) {
                    demoPlayer.reset();
                }
                showEmptyState(true);
            }
        });
        messagesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        messagesList.setAdapter(adapter);

        demoPlayer = new AgentDemoPlayer(new AgentDemoPlayer.Listener() {
            @Override
            public void onMessagesUpdated(@NonNull List<AgentMessage> messages) {
                adapter.submitList(messages);
                showEmptyState(messages.isEmpty());
                messagesList.post(() -> {
                    if (adapter.getItemCount() > 0) {
                        messagesList.scrollToPosition(adapter.getItemCount() - 1);
                    }
                });
            }

            @Override
            public void onWaitingForApprove(boolean waiting) {
                // Act bar is inline in the transcript.
            }

            @Override
            public void onPlaybackComplete() {
                // Demo finished — keep transcript for screenshots.
            }

            @Override
            public void onPlaybackReset() {
                showEmptyState(true);
            }
        });
    }

    private void setupGate(@NonNull View view) {
        LinearLayout buttonRow = view.findViewById(R.id.agent_gate_button_row);
        MaterialButton byok = view.findViewById(R.id.agent_gate_byok_button);
        MaterialButton github = view.findViewById(R.id.agent_gate_github_button);
        MaterialButton skip = view.findViewById(R.id.agent_gate_demo_skip_button);
        applyGateButtonLayout(buttonRow, byok, github, skip);

        byok.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SettingsActivity.class);
            intent.putExtra("settings_tab_index", 2);
            startActivity(intent);
        });
        github.setOnClickListener(v -> mockConnect(getString(R.string.agent_gate_github_connected_toast)));
        skip.setOnClickListener(v -> mockConnect(null));

        if (isDemoTokenConnected() || shouldSkipGate() || isApiKeyConfigured()) {
            showConnectedUi();
            maybeStartAutoDemo();
        } else {
            showGate(true);
        }
    }

    private void applyGateButtonLayout(
            @NonNull LinearLayout buttonRow,
            @NonNull MaterialButton byok,
            @NonNull MaterialButton github,
            @NonNull MaterialButton skip) {
        boolean isLandscape =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int gap = getResources().getDimensionPixelSize(R.dimen.agent_gate_button_gap);
        int height = getResources().getDimensionPixelSize(R.dimen.agent_gate_button_height);
        buttonRow.setOrientation(isLandscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        MaterialButton[] buttons = new MaterialButton[] {byok, github, skip};
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams params;
            if (isLandscape) {
                params = new LinearLayout.LayoutParams(0, height, 1f);
                params.setMarginStart(i == 0 ? 0 : gap);
            } else {
                params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
                params.topMargin = i == 0 ? 0 : gap;
            }
            buttons[i].setLayoutParams(params);
        }
    }

    private void setupDemoPicker() {
        demoPickerContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (AgentDemoScript script : AgentDemoScriptRegistry.all()) {
            View card = inflater.inflate(R.layout.item_agent_demo_picker_card, demoPickerContainer, false);
            TextView title = card.findViewById(R.id.agent_demo_card_title);
            TextView tagline = card.findViewById(R.id.agent_demo_card_tagline);
            title.setText(script.pickerTitle);
            tagline.setText(script.pickerTagline);
            card.setOnClickListener(v -> startDemo(script));
            demoPickerContainer.addView(card);
        }
    }

    private void applyLaunchArgs() {
        Bundle args = getArguments();
        if (args == null) {
            return;
        }
        AgentDemoPlayer.PauseAt pauseAt = parsePauseAt(args.getString(ARG_PAUSE_AT));
        boolean autoApprove = args.getBoolean(ARG_AUTO_APPROVE, false);
        demoPlayer.configure(pauseAt, autoApprove);

        String scriptId = args.getString(ARG_DEMO_SCRIPT);
        AgentDemoScript script = AgentDemoScriptRegistry.get(scriptId);
        if (script == null && args.getBoolean(ARG_AUTO_PLAY, false)) {
            script = AgentDemoScriptRegistry.defaultScript();
        }
        if (script != null && args.getBoolean(ARG_AUTO_PLAY, false)) {
            pendingAutoScript = script;
            if (isDemoTokenConnected() || shouldSkipGate()) {
                maybeStartAutoDemo();
            }
        }
    }

    private void maybeStartAutoDemo() {
        if (pendingAutoScript == null || demoPlayer == null) {
            return;
        }
        AgentDemoScript script = pendingAutoScript;
        pendingAutoScript = null;
        startDemo(script);
    }

    private void mockConnect(@Nullable String toastMessage) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        prefs.edit().putBoolean(PREF_DEMO_TOKEN_CONNECTED, true).apply();
        if (toastMessage != null) {
            Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show();
        }
        showConnectedUi();
        maybeStartAutoDemo();
    }

    private void showConnectedUi() {
        showGate(false);
        connectionPill.setText(R.string.agent_connection_ready);
        showEmptyState(true);
    }

    private void showGate(boolean visible) {
        gateOverlay.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showEmptyState(boolean empty) {
        if (demoPickerScroll != null) {
            demoPickerScroll.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        emptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        messagesList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void startDemo(@NonNull AgentDemoScript script) {
        if (demoPlayer == null) {
            return;
        }
        highlightSelectedDemoCard(script.id);
        demoPlayer.start(script);
    }

    private void highlightSelectedDemoCard(@NonNull String scriptId) {
        for (int i = 0; i < demoPickerContainer.getChildCount(); i++) {
            View child = demoPickerContainer.getChildAt(i);
            if (child instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) child;
                AgentDemoScript script = AgentDemoScriptRegistry.all().get(i);
                boolean selected = script.id.equals(scriptId);
                card.setStrokeWidth(selected ? 4 : 0);
                card.setStrokeColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorPrimary));
            }
        }
    }

    private boolean isDemoTokenConnected() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        return prefs.getBoolean(PREF_DEMO_TOKEN_CONNECTED, false);
    }

    private boolean shouldSkipGate() {
        Bundle args = getArguments();
        return args != null && args.getBoolean(ARG_SKIP_GATE, false);
    }

    @NonNull
    private AgentDemoPlayer.PauseAt parsePauseAt(@Nullable String value) {
        if ("plan".equals(value)) {
            return AgentDemoPlayer.PauseAt.PLAN;
        }
        if ("none".equals(value)) {
            return AgentDemoPlayer.PauseAt.NONE;
        }
        return AgentDemoPlayer.PauseAt.ACT;
    }

    // ── Day 3: Real Agent Engine ─────────────────────────────────────────

    /**
     * Check if the active provider is configured.
     * Uses the same provider list as AgentSettingsBottomSheet.
     * For providers that don't need an API key (Ollama, Local Qwen),
     * only checks that an endpoint is configured.
     */
    private boolean isApiKeyConfigured() {
        if (!isAdded()) return false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        // Use the same provider list as AISettingsFragment
        String[] providerNames = getResources().getStringArray(R.array.settings_ai_provider_names);
        int providerIdx = prefs.getInt(PREF_AI_PROVIDER, 0);

        if (providerIdx < 0 || providerIdx >= providerNames.length) {
            return false;
        }

        // Custom provider (last index) — only endpoint is required
        if (providerIdx == providerNames.length - 1) {
            String endpoint = prefs.getString(PREF_AI_ENDPOINT, "");
            return !TextUtils.isEmpty(endpoint);
        }

        String apiKey = prefs.getString(PREF_AI_API_KEY + "_" + providerIdx, "");
        return !TextUtils.isEmpty(apiKey);
    }

    /**
     * Initialize the AgentController and wire up listener callbacks.
     */
    private void setupAgentController() {
        if (agentController == null) return;

        agentController.setListener(new AgentController.AgentListener() {
            @Override
            public void onStateChanged(@NonNull AgentState state) {
                if (!isAdded()) return;
                updateUiForState(state);
            }

            @Override
            public void onPlanReady(@NonNull AgentPlan plan) {
                if (!isAdded()) return;
                showAgentPlan(plan);
            }

            @Override
            public void onExecutionProgress(int currentStep, int totalSteps) {
                // Day 5: update execution UI
                Log.d(TAG, "Execution progress: " + currentStep + "/" + totalSteps);
            }

            @Override
            public void onRetry(int attempt, int maxAttempts) {
                if (!isAdded()) return;
                Log.d(TAG, "Retrying step: " + attempt + "/" + maxAttempts);
            }

            @Override
            public void onExecutionComplete() {
                if (!isAdded()) return;
                inputField.setEnabled(true);
                sendButton.setEnabled(true);
                showEmptyState(false);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                inputField.setEnabled(true);
                sendButton.setEnabled(true);
            }

            @Override
            public void onPlanTruncated(int maxSteps) {
                if (!isAdded()) return;
                chatMessages.add(AgentMessage.assistant(
                        "⚠️ Plan truncated to " + maxSteps + " steps (exceeds limit)."));
                adapter.submitList(new ArrayList<>(chatMessages));
                messagesList.post(() -> {
                    if (adapter.getItemCount() > 0) {
                        messagesList.scrollToPosition(adapter.getItemCount() - 1);
                    }
                });
            }
        });
    }

    /**
     * Submit user input to the real AgentController.
     */
    private void submitToAgent() {
        if (agentController == null) return;
        String prompt = inputField.getText().toString().trim();
        if (prompt.isEmpty()) return;

        // Track user message in local list
        chatMessages.add(AgentMessage.user(prompt));

        inputField.setText("");
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        showEmptyState(false);

        adapter.submitList(new ArrayList<>(chatMessages));
        messagesList.post(() -> {
            if (adapter.getItemCount() > 0) {
                messagesList.scrollToPosition(adapter.getItemCount() - 1);
            }
        });

        agentController.submit(prompt);
    }

    /**
     * Update UI elements based on Agent state.
     */
    private void updateUiForState(@NonNull AgentState state) {
        switch (state) {
            case IDLE:
                inputField.setEnabled(true);
                sendButton.setEnabled(true);
                inputField.setHint(R.string.agent_input_hint_real);
                break;
            case THINKING:
                inputField.setEnabled(false);
                sendButton.setEnabled(false);
                inputField.setHint(R.string.agent_state_thinking);
                break;
            case WAITING_APPROVE:
                inputField.setEnabled(false);
                sendButton.setEnabled(false);
                break;
            case EXECUTING:
                inputField.setEnabled(false);
                sendButton.setEnabled(false);
                break;
            case RETRYING:
                // Keep disabled during retry
                break;
            case ERROR:
                inputField.setEnabled(true);
                sendButton.setEnabled(true);
                break;
        }
    }

    /**
     * Display a generated plan in the message list.
     * Converts AgentPlan to AgentPlanStep for display.
     */
    private void showAgentPlan(@NonNull AgentPlan plan) {
        List<AgentPlanStep> displaySteps = new ArrayList<>();
        for (AgentPlan.Step step : plan.steps) {
            AgentPlanStep.Kind kind;
            String subtitle;
            switch (step.kind) {
                case "hid":
                    kind = AgentPlanStep.Kind.HID;
                    subtitle = step.keys;
                    break;
                case "macro":
                    kind = AgentPlanStep.Kind.MACRO;
                    subtitle = step.macroId;
                    break;
                case "terminal":
                default:
                    kind = AgentPlanStep.Kind.TERMINAL;
                    subtitle = step.command;
                    break;
            }
            displaySteps.add(new AgentPlanStep(step.index, step.title, subtitle, kind));
        }

        chatMessages.add(AgentMessage.plan(displaySteps));
        chatMessages.add(AgentMessage.actBar());
        adapter.submitList(new ArrayList<>(chatMessages));

        messagesList.post(() -> {
            if (adapter.getItemCount() > 0) {
                messagesList.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (agentController != null) {
            agentController.shutdown();
        }
        if (verifyExecutor != null) {
            verifyExecutor.shutdownNow();
        }
    }
}
