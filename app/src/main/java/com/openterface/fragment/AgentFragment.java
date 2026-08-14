package com.openterface.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
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

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.openterface.keymod.R;
import com.openterface.keymod.SettingsActivity;
import com.openterface.keymod.agent.demo.AgentDemoPlayer;
import com.openterface.keymod.agent.demo.AgentDemoScript;
import com.openterface.keymod.agent.demo.AgentDemoScriptRegistry;
import com.openterface.keymod.agent.ui.AgentMessage;
import com.openterface.keymod.agent.ui.AgentMessageAdapter;
import com.openterface.keymod.agent.ui.AgentPlanStep;
import com.openterface.keymod.agent.ui.EditPlanSheet;
import com.openterface.keymod.agent.core.AgentController;
import com.openterface.keymod.agent.core.AgentEnvironment;
import com.openterface.keymod.agent.core.AgentPlan;
import com.openterface.keymod.agent.core.AgentState;
import com.openterface.keymod.agent.executor.CompositeToolExecutor;
import com.openterface.keymod.agent.executor.HidToolExecutor;
import com.openterface.keymod.agent.executor.MacroToolExecutor;
import com.openterface.keymod.agent.executor.TerminalToolExecutor;
import com.openterface.keymod.agent.llm.LlmHttpClient;
import com.openterface.keymod.agent.settings.AIConfigProvider;
import com.openterface.keymod.agent.settings.AIProvider;
import com.openterface.terminal.CredentialManager;
import com.openterface.terminal.CredentialProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Agent mode marketing MVP — curated demo scripts with Plan/Act UI. */
public class AgentFragment extends Fragment {

    private static final String TAG = "AgentFragment";

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
    private View emptyState;
    private EditText inputField;
    private ImageButton sendButton;
    private View sessionBar;
    private TextView sessionHint;
    private TextView connectionPill;
    private GridLayout suggestedPromptsContainer;

    private AgentMessageAdapter adapter;
    private AgentDemoPlayer demoPlayer;
    @Nullable private AgentDemoScript pendingAutoScript;

    // Real Agent engine
    @Nullable private AgentController agentController;
    @Nullable private TerminalToolExecutor terminalExecutor;
    @Nullable private HidToolExecutor hidExecutor;
    @Nullable private MacroToolExecutor macroExecutor;
    @Nullable private CompositeToolExecutor compositeExecutor;
    private boolean realEngineEnabled = false;
    private final List<AgentMessage> chatMessages = new ArrayList<>();
    @Nullable private ExecutorService verifyExecutor;
    private final AtomicBoolean verifyRunning = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** Hash of last verified config to avoid redundant API tests */
    @Nullable private String lastVerifiedConfigHash;

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
        setupSuggestedPrompts();
        applyLaunchArgs();
        refreshEngineState();
        setupKeyboardHandler();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Restore persisted Agent SSH profile so it survives app restart
        restoreAgentSshProfile();
        // Re-check API config when returning from settings
        refreshEngineState();
        // Refresh connection pill to reflect current AI Provider selection
        updateConnectionPill();
        // Update session bar to reflect restored target
        updateSessionBar();
        // Update executor connections (user may have connected/disconnected)
        updateTerminalExecutorSshClient();
        updateHidExecutorConnection();
        updateMacroExecutorConnection();
    }

    /**
     * Update the TerminalToolExecutor with the current SshClient from the environment.
     * SshClient is stored at the Activity level so it survives tab switches.
     * Also sets HID ConnectionManager for fallback when SSH is unavailable.
     */
    private void updateTerminalExecutorSshClient() {
        if (terminalExecutor == null || !isAdded()) return;
        AgentEnvironment env = (AgentEnvironment) requireActivity();
        com.openterface.terminal.SshClient sshClient = env.getSshClient();
        terminalExecutor.setSshClient(sshClient);
        // Set HID fallback: ConnectionManager + targetOS for when SSH is unavailable
        terminalExecutor.setConnectionManager(env.getConnectionManager());
        terminalExecutor.setTargetOs(getTargetOs());
    }

    /**
     * Update the HidToolExecutor with the current ConnectionManager and target OS.
     * ConnectionManager is obtained from the environment.
     */
    private void updateHidExecutorConnection() {
        if (hidExecutor == null || !isAdded()) return;
        AgentEnvironment env = (AgentEnvironment) requireActivity();
        hidExecutor.setConnectionManager(env.getConnectionManager());
        hidExecutor.setTargetOs(getTargetOs());
    }

    /**
     * Update the MacroToolExecutor with the current ConnectionManager.
     */
    private void updateMacroExecutorConnection() {
        if (macroExecutor == null || !isAdded()) return;
        AgentEnvironment env = (AgentEnvironment) requireActivity();
        macroExecutor.setConnectionManager(env.getConnectionManager());
    }

    /**
     * Get the target OS for HID execution mode.
     * Priority: Agent-specific target OS (from TargetSettingsSheet)
     *         > active SSH profile's targetOs
     *         > default "linux".
     */
    @NonNull
    private String getTargetOs() {
        // 1. Agent-specific HID target OS (set via TargetSettingsSheet)
        String agentOs = requireContext()
                .getSharedPreferences("agent_prefs", android.content.Context.MODE_PRIVATE)
                .getString("agent_target_os", "");
        if (agentOs != null && !agentOs.isEmpty()) {
            return agentOs;
        }
        // 2. SSH profile's target OS
        if (isAdded()) {
            AgentEnvironment env = (AgentEnvironment) requireActivity();
            com.openterface.terminal.CredentialProfile profile = env.getActiveSshProfile();
            if (profile != null) {
                String os = profile.getTargetOs();
                if (os != null && !os.isEmpty()) {
                    return os;
                }
            }
        }
        // 3. Fallback
        return "linux";
    }

    /**
     * Re-evaluate engine state and gate visibility.
     * Called on resume to pick up settings changes.
     *
     * <p>Logic:
     * <ul>
     *   <li>No API key configured → show gate (Connect AI screen)</li>
     *   <li>API key configured but invalid → show chat interface with error bubble</li>
     *   <li>API key valid → show chat interface normally</li>
     * </ul>
     */
    private void refreshEngineState() {
        if (inputField == null) return; // views not bound yet
        if (!verifyRunning.compareAndSet(false, true)) {
            return; // verification already in progress
        }

        if (!isApiKeyConfigured()) {
            // No API key at all → show gate
            verifyRunning.set(false);
            disableEngine();
            lastVerifiedConfigHash = null;
            return;
        }

        // Compute config hash to check if we need to re-verify
        String currentConfigHash = computeConfigHash();
        boolean configChanged = !currentConfigHash.equals(lastVerifiedConfigHash);

        // API key is configured — hide gate and show chat interface
        showGate(false);
        if (demoPickerScroll != null) {
            demoPickerScroll.setVisibility(View.GONE);
        }

        // Only run API test if config changed or first time
        if (!configChanged && realEngineEnabled) {
            // Config unchanged and engine already enabled — skip verification
            verifyRunning.set(false);
            Log.d(TAG, "refreshEngineState: config unchanged, skipping API test");
            return;
        }

        // Verify API key works
        if (verifyExecutor == null) {
            verifyExecutor = Executors.newSingleThreadExecutor();
        }
        inputField.setHint(R.string.agent_verifying_api);
        inputField.setEnabled(false);
        sendButton.setEnabled(false);

        verifyExecutor.submit(() -> {
            boolean valid = false;
            try {
                valid = testApiConnection();
            } catch (Exception e) {
                Log.w(TAG, "API verification exception", e);
            } finally {
                verifyRunning.set(false);
            }

            if (!isAdded()) return;
            final boolean finalValid = valid;
            mainHandler.post(() -> {
                // Enable engine (this also hides gate)
                enableEngine();

                if (finalValid) {
                    // Mark config as verified
                    lastVerifiedConfigHash = currentConfigHash;
                }

                if (!finalValid) {
                    // Show error bubble in chat for invalid API key
                    chatMessages.add(AgentMessage.assistantError(
                            getString(R.string.agent_error_api_connection_failed), false));
                    adapter.submitList(new ArrayList<>(chatMessages));
                    showEmptyState(false);
                }
            });
        });
    }

    /** Test API connection using LlmHttpClient.testConnection() */
    private boolean testApiConnection() {
        try {
            AIConfigProvider config = AIConfigProvider.getInstance(requireContext());
            String endpoint = config.getEndpoint();
            String model = config.getModel();
            String apiKey = config.getApiKey();
            String providerName = config.getProviderName();

            LlmHttpClient client = new LlmHttpClient(apiKey, endpoint);
            client.testConnection(model, providerName);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "API verification failed", e);
            return false;
        }
    }

    /** Compute a hash of current API config to detect changes */
    @NonNull
    private String computeConfigHash() {
        try {
            AIConfigProvider config = AIConfigProvider.getInstance(requireContext());
            String endpoint = config.getEndpoint() != null ? config.getEndpoint() : "";
            String model = config.getModel() != null ? config.getModel() : "";
            String apiKey = config.getApiKey() != null ? config.getApiKey() : "";
            String providerName = config.getProviderName() != null ? config.getProviderName() : "";
            // Simple hash: concatenate key fields
            return (endpoint + "|" + model + "|" + apiKey.hashCode() + "|" + providerName).hashCode() + "";
        } catch (Exception e) {
            return "error_" + System.currentTimeMillis();
        }
    }

    private void enableEngine() {
        if (!realEngineEnabled) {
            realEngineEnabled = true;
            // Use getActivity() to get the Activity context.
            // MainActivity implements AgentEnvironment, providing SSH profile
            // and SshClient access without instanceof coupling.
            android.content.Context ctx = getActivity();
            if (ctx == null) ctx = requireContext();
            AgentEnvironment env = (AgentEnvironment) requireActivity();
            agentController = new AgentController(ctx, env);

            // TerminalToolExecutor for SSH command execution
            terminalExecutor = new TerminalToolExecutor(requireContext());
            terminalExecutor.setEnvironment(env);
            updateTerminalExecutorSshClient();

            // HidToolExecutor for wireless keyboard control
            hidExecutor = new HidToolExecutor();
            updateHidExecutorConnection();

            // MacroToolExecutor for macro playback
            macroExecutor = new MacroToolExecutor(requireContext());
            updateMacroExecutorConnection();

            // CompositeToolExecutor routes steps to terminal/hid/macro
            compositeExecutor = new CompositeToolExecutor();
            compositeExecutor.register(terminalExecutor);
            compositeExecutor.register(hidExecutor);
            compositeExecutor.register(macroExecutor);

            agentController.setToolExecutor(compositeExecutor);

            setupAgentController();
        }
        inputField.setEnabled(true);
        sendButton.setEnabled(true);
        inputField.setHint(R.string.agent_input_hint_real);
        sendButton.setOnClickListener(v -> submitToAgent());
        showGate(false);
        // Demo Picker only visible during Gate phase (AI not configured).
        // Once AI is configured, hide it permanently — FAQ Chips serve as suggestions.
        if (demoPickerScroll != null) {
            demoPickerScroll.setVisibility(View.GONE);
        }
        updateSessionBar();
        updateConnectionPill();
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
        emptyState = view.findViewById(R.id.agent_empty_state);
        inputField = view.findViewById(R.id.agent_input);
        sendButton = view.findViewById(R.id.agent_send_button);
        sessionBar = view.findViewById(R.id.agent_session_bar);
        sessionHint = view.findViewById(R.id.agent_session_hint);
        connectionPill = view.findViewById(R.id.agent_connection_pill);
        suggestedPromptsContainer = view.findViewById(R.id.agent_suggested_prompts_container);
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
                if (!realEngineEnabled || agentController == null) return;
                AgentPlan plan = agentController.getCurrentPlan();
                if (plan == null) return;

                EditPlanSheet sheet = EditPlanSheet.forPlan(plan, editedPlan -> {
                    agentController.updatePlan(editedPlan);
                    // Re-display the updated plan in the chat
                    showAgentPlan(editedPlan);
                });
                sheet.show(getChildFragmentManager(), "edit_plan");
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

            @Override
            public void onReexecute() {
                if (realEngineEnabled && agentController != null) {
                    agentController.reexecutePlan();
                }
            }
        });
        adapter.setRetryListener(() -> {
            if (agentController != null) {
                agentController.regeneratePlan();
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

        // Hide demo and GitHub buttons when no API is configured
        // Only show BYOK (Bring Your Own Key) button to guide user to configure API
        boolean hasApi = isApiKeyConfigured();
        github.setVisibility(hasApi ? View.VISIBLE : View.GONE);
        skip.setVisibility(hasApi ? View.VISIBLE : View.GONE);

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
        updateConnectionPill();
        showEmptyState(true);
    }

    /**
     * Update the connection pill text to reflect the currently selected AI Provider
     * and its model name, replacing the hardcoded "KeyMod · Ready" string.
     */
    public void updateConnectionPill() {
        if (connectionPill == null || !isAdded()) return;
        AIConfigProvider config = AIConfigProvider.getInstance(requireContext());
        AIProvider provider = config.getSelectedProvider();
        if (provider != null && provider.modelName != null) {
            connectionPill.setText(provider.modelName);
        } else {
            connectionPill.setText(R.string.agent_connection_ready);
        }
    }

    /**
     * Called from MainActivity when AgentSettingsBottomSheet is dismissed.
     * Resets any in-flight verification so the new provider is evaluated immediately,
     * then refreshes both the connection pill and the engine gate/conversation state.
     */
    public void onSettingsDismissed() {
        updateConnectionPill();
        verifyRunning.set(false);
        refreshEngineState();
    }

    private void showGate(boolean visible) {
        gateOverlay.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showEmptyState(boolean empty) {
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
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

    // ── Real Agent Engine ─────────────────────────────────────────────

    /**
     * Check if the active provider is configured.
     * Uses AIConfigProvider for unified access.
     * For providers that don't need an API key (Ollama, Local Qwen),
     * only checks that an endpoint is configured.
     */
    private boolean isApiKeyConfigured() {
        if (!isAdded()) return false;
        return AIConfigProvider.getInstance(requireContext()).isConfigured();
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
                updateSessionBar();
            }

            @Override
            public void onPlanReady(@NonNull AgentPlan plan) {
                if (!isAdded()) return;
                showAgentPlan(plan);
            }

            @Override
            public void onExecutionProgress(int currentStep, int totalSteps) {
                if (!isAdded()) return;
                if (totalSteps > 0) {
                    inputField.setHint("Executing step " + (currentStep + 1) + "/" + totalSteps + "...");
                } else {
                    inputField.setHint(R.string.agent_state_executing);
                }
            }

            @Override
            public void onRetry(int attempt, int maxAttempts) {
                if (!isAdded()) return;

                // ── Dim the last failed CLI card to a muted "Retried" grey ──
                // The Agent's LLM retry mechanism is about to regenerate an
                // alternative, so we don't want a loud red failure card lingering
                // in the terminal output.  Replace the card in-place so the user
                // sees the original command (greyed out) with a subtle hint.
                // Only dim cards that are explicitly failed (cliSuccess == false),
                // not cards that are still running (cliSuccess == null).
                for (int i = chatMessages.size() - 1; i >= 0; i--) {
                    AgentMessage msg = chatMessages.get(i);
                    if (msg.type == AgentMessage.Type.EXECUTION_CLI
                            && Boolean.FALSE.equals(msg.cliSuccess)) {
                        AgentMessage retried = AgentMessage.executionCliRetried(msg.terminalLines);
                        chatMessages.set(i, retried);
                        adapter.setItem(i, retried);
                        break;
                    }
                }

                // Insert retry status message and thinking indicator
                chatMessages.add(AgentMessage.assistant(
                        "🔄 Retry " + attempt + "/" + maxAttempts
                        + ": command failed. Asking for alternatives..."));
                chatMessages.add(AgentMessage.thinking(getString(R.string.agent_state_thinking)));
                adapter.submitList(new ArrayList<>(chatMessages));
                messagesList.post(() -> {
                    if (adapter.getItemCount() > 0) {
                        messagesList.scrollToPosition(adapter.getItemCount() - 1);
                    }
                });
            }

            @Override
            public void onExecutionComplete() {
                if (!isAdded()) return;
                // Mark execution complete BEFORE summaryPending — protects the THINKING
                // indicator from being removed when onStateChanged(IDLE) fires later
                // (from summarize()'s transitionTo(IDLE)).
                executionJustCompleted = true;
                summaryPending = true;
                summaryMessagePosition = -1;

                // Reuse existing THINKING message (e.g. from a retry phase) if present,
                // otherwise insert a new one. This avoids duplicate THINKING items.
                boolean updated = false;
                for (int i = chatMessages.size() - 1; i >= 0; i--) {
                    if (chatMessages.get(i).type == AgentMessage.Type.THINKING) {
                        AgentMessage thinkingMsg = AgentMessage.thinking(
                                getString(R.string.agent_state_thinking));
                        chatMessages.set(i, thinkingMsg);
                        adapter.setItem(i, thinkingMsg);
                        updated = true;
                        break;
                    }
                }
                if (!updated) {
                    AgentMessage thinkingMsg = AgentMessage.thinking(
                            getString(R.string.agent_state_thinking));
                    chatMessages.add(thinkingMsg);
                    adapter.addItem(thinkingMsg);
                }
                messagesList.post(() -> {
                    if (adapter.getItemCount() > 0) {
                        messagesList.scrollToPosition(adapter.getItemCount() - 1);
                    }
                });
                // Use View.post() — schedules after RecyclerView layout pass completes.
                // This guarantees the thinking message is rendered before summarize starts.
                if (agentController != null) {
                    messagesList.post(() -> {
                        if (isAdded() && agentController != null) {
                            agentController.startSummarize();
                        }
                    });
                }
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;

                boolean canRetry;
                String displayText;

                if (message.contains("SSH not connected")
                        || message.contains("SSH auto-connect")
                        || message.contains("SSH profile")) {
                    displayText = getString(R.string.agent_error_ssh_not_connected, message);
                    canRetry = false;  // SSH not connected, retry is meaningless
                } else if (message.contains("HID device not connected")) {
                    displayText = getString(R.string.agent_error_hid_not_connected, message);
                    canRetry = false;  // HID not connected, retry is meaningless
                } else if (message.contains("Macro not found")) {
                    displayText = getString(R.string.agent_error_macro_not_found,
                            message, getAvailableMacroNames());
                    canRetry = false;  // Macro doesn't exist, retry is meaningless
                } else {
                    // LLM call failure, plan parse failure, step timeout, etc. → retryable
                    displayText = message;
                    canRetry = true;
                }

                chatMessages.add(AgentMessage.assistantError(displayText, canRetry));
                adapter.submitList(new ArrayList<>(chatMessages));
                scrollToBottom();

                inputField.setEnabled(true);
                sendButton.setEnabled(true);
                executionJustCompleted = false;
            }

            @Override
            public void onPlanTruncated(int maxSteps) {
                if (!isAdded()) return;
                chatMessages.add(AgentMessage.assistant(
                        "Plan truncated to " + maxSteps + " steps (exceeds limit)."));
                adapter.submitList(new ArrayList<>(chatMessages));
                scrollToBottom();
            }

            @Override
            public void onStepStart(int stepIndex, @NonNull String command) {
                if (!isAdded()) return;
                // Remove thinking indicator and insert Running CLI card
                removeThinkingMessage();
                chatMessages.add(AgentMessage.executionCli(
                        java.util.Collections.singletonList(command)));
                // Use adapter.addItem() to sync the adapter's internal messages list
                // AND notify the RecyclerView in one step — prevents stale-data bugs
                // where the EXECUTION_CLI card wouldn't render.
                adapter.addItem(chatMessages.get(chatMessages.size() - 1));
                scrollToBottom();
            }

            @Override
            public void onStepOutput(int stepIndex, @NonNull List<String> lines,
                                      boolean isComplete, @Nullable Boolean success) {
                if (!isAdded()) return;
                // Find the most recent Running CLI card to update in place.
                boolean updated = false;
                for (int i = chatMessages.size() - 1; i >= 0; i--) {
                    AgentMessage existing = chatMessages.get(i);
                    if (existing.type == AgentMessage.Type.EXECUTION_CLI) {
                        if (isComplete) {
                            // Final state — mark card complete.
                            // If lines are provided (error case), replace; otherwise
                            // keep the lines already accumulated via streaming.
                            List<String> finalLines = lines.isEmpty()
                                    ? existing.terminalLines : lines;
                            AgentMessage msg = AgentMessage.executionCliComplete(
                                    finalLines, success != null ? success : true);
                            chatMessages.set(i, msg);
                            adapter.setItem(i, msg);
                        } else if (!lines.isEmpty()) {
                            // Streaming: append new lines to the running card.
                            List<String> merged = new ArrayList<>(existing.terminalLines);
                            merged.addAll(lines);
                            AgentMessage msg = AgentMessage.executionCli(merged);
                            chatMessages.set(i, msg);
                            adapter.setItem(i, msg);
                        }
                        // isComplete + empty lines → mark complete, keep existing lines
                        // !isComplete + empty lines → nothing to do
                        updated = true;
                        break;
                    }
                }
                if (!updated) {
                    // Fallback: no Running card found, add new one.
                    AgentMessage msg = isComplete
                            ? AgentMessage.executionCliComplete(lines, success != null ? success : true)
                            : AgentMessage.executionCli(lines);
                    chatMessages.add(msg);
                    adapter.addItem(msg);
                }
                scrollToBottom();
            }

            @Override
            public void onToken(@NonNull String token) {
                if (!isAdded()) return;
                onThinkingToken(token);
            }

            @Override
            public void onSummaryToken(@NonNull String token) {
                if (!isAdded()) return;
                appendSummaryToken(token);
            }

            @Override
            public void onSummaryReset() {
                // Reset buffer state so chatSync fallback content is treated as first token.
                // Keep summaryMessagePosition intact — the fallback content should replace
                // the orphaned message (e.g. "```") at that position, not append a new one.
                summaryContent.setLength(0);
                summaryStarted = false;
            }

            @Override
            public void onSummaryComplete() {
                if (!isAdded()) return;
                summaryPending = false;
                executionJustCompleted = false;
                summaryMessagePosition = -1;
                inputField.setEnabled(true);
                sendButton.setEnabled(true);
                inputField.setHint(R.string.agent_input_hint_real);
                showEmptyState(false);
            }
        });
    }

    /**
     * Get a comma-separated list of all available macro names.
     * Used for error messages when a macro is not found.
     */
    @NonNull
    private String getAvailableMacroNames() {
        if (!isAdded()) return "(unknown)";
        com.openterface.keymod.MacrosManager mm =
                com.openterface.keymod.MacrosManager.getInstance(requireContext());
        java.util.List<com.openterface.keymod.MacrosManager.Macro> macros = mm.getAllMacros();
        if (macros == null || macros.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < macros.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(macros.get(i).name);
        }
        return sb.toString();
    }

    /**
     * Submit user input to the real AgentController.
     */
    private void submitToAgent() {
        if (agentController == null) return;
        String prompt = inputField.getText().toString().trim();
        if (prompt.isEmpty()) return;

        // Check if at least one execution mode is configured:
        // - Terminal mode: requires an active SSH profile
        // - HID mode: requires a target OS to be explicitly set
        if (!isExecutionModeConfigured()) {
            // Show the chat list (in case it was hidden by empty state)
            showEmptyState(false);

            // Build error message with inline terminal icon
            String placeholder = "[]";
            String template = "No target configured.\n\n"
                    + "Tap the target icon " + placeholder + " in the top bar to set up:\n"
                    + "• Target OS for HID mode\n"
                    + "• Or connect an SSH host in Terminal tab";

            android.graphics.drawable.Drawable icon = getResources()
                    .getDrawable(R.drawable.ic_terminal, requireContext().getTheme());
            int iconSize = (int) (16 * getResources().getDisplayMetrics().density);
            icon.setBounds(0, 0, iconSize, iconSize);
            icon.setTintList(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(R.color.text_primary, requireContext().getTheme())));

            android.text.SpannableString spannable = new android.text.SpannableString(template);
            int start = template.indexOf(placeholder);
            spannable.setSpan(new android.text.style.ImageSpan(icon),
                    start, start + placeholder.length(),
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            chatMessages.add(AgentMessage.assistantError(spannable, false));
            adapter.submitList(new ArrayList<>(chatMessages));
            messagesList.post(() -> {
                if (adapter.getItemCount() > 0) {
                    messagesList.scrollToPosition(adapter.getItemCount() - 1);
                }
            });
            return;
        }

        // Reset summary state
        summaryContent.setLength(0);
        summaryStarted = false;
        summaryPending = false;
        summaryMessagePosition = -1;
        executionJustCompleted = false;

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
     * Check if at least one execution mode is properly configured.
     * - Terminal mode: requires an active SSH profile
     * - HID mode: requires a target OS to be explicitly set in agent_prefs
     */
    private boolean isExecutionModeConfigured() {
        if (!isAdded()) return false;

        // Check for active SSH profile
        if (isAdded()) {
            AgentEnvironment env = (AgentEnvironment) requireActivity();
            com.openterface.terminal.CredentialProfile profile = env.getActiveSshProfile();
            if (profile != null) {
                return true;  // Terminal mode is configured
            }
        }

        // Check for explicitly configured target OS (HID mode)
        String agentOs = requireContext()
                .getSharedPreferences("agent_prefs", android.content.Context.MODE_PRIVATE)
                .getString("agent_target_os", "");
        if (agentOs != null && !agentOs.isEmpty()) {
            return true;  // HID mode is configured
        }

        return false;
    }

    /**
     * Update UI elements based on Agent state.
     */
    private void updateUiForState(@NonNull AgentState state) {
        switch (state) {
            case IDLE:
                inputField.setEnabled(true);
                inputField.setHint(R.string.agent_input_hint_real);
                updateThinkingRow(false);
                break;
            case THINKING:
                inputField.setEnabled(false);
                inputField.setHint(R.string.agent_state_thinking);
                updateThinkingRow(true);
                // SSH connects in parallel with LLM thinking
                if (terminalExecutor != null) {
                    terminalExecutor.preConnectSsh(this::updateSessionBar);
                }
                break;
            case WAITING_APPROVE:
                // Enable input so user can enter a new request without approving
                inputField.setEnabled(true);
                inputField.setHint(R.string.agent_state_waiting_approve);
                updateThinkingRow(false);
                break;
            case EXECUTING:
                inputField.setEnabled(false);
                updateThinkingRow(false);
                break;
            case RETRYING:
                // Keep input disabled during retry
                break;
            case ERROR:
                inputField.setEnabled(true);
                updateThinkingRow(false);
                break;
        }
        updateSendButtonForState(state);
    }

    /**
     * Switch send button icon and action based on current Agent state.
     * <ul>
     *   <li>IDLE / WAITING_APPROVE / ERROR → send icon, calls {@link #submitToAgent()}</li>
     *   <li>THINKING / EXECUTING / RETRYING → stop icon, calls {@code agentController.cancel()}</li>
     * </ul>
     */
    private void updateSendButtonForState(@NonNull AgentState state) {
        boolean isBusy = state == AgentState.THINKING
                || state == AgentState.EXECUTING
                || state == AgentState.RETRYING;
        if (isBusy) {
            sendButton.setEnabled(true);
            sendButton.setImageResource(R.drawable.ic_hourglass);
            sendButton.setContentDescription(getString(R.string.agent_act_cancel));
            sendButton.setOnClickListener(v -> {
                if (agentController != null) {
                    agentController.cancel();
                }
            });
        } else {
            sendButton.setEnabled(true);
            sendButton.setImageResource(R.drawable.ic_toolbar_send);
            sendButton.setContentDescription(getString(R.string.agent_send_cd));
            sendButton.setOnClickListener(v -> submitToAgent());
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

        boolean isHidMode = "hid".equals(agentController.getPromptBuilder().getExecutionMode());

        // Intro text: LLM-generated one-line summary of what the plan will do
        if (!plan.summary.isEmpty()) {
            chatMessages.add(AgentMessage.assistant(plan.summary));
        }
        chatMessages.add(AgentMessage.plan(displaySteps, isHidMode));
        chatMessages.add(AgentMessage.actBar());
        adapter.submitList(new ArrayList<>(chatMessages));

        messagesList.post(() -> {
            if (adapter.getItemCount() > 0) {
                messagesList.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    // ─ Thinking Indicator ──────────────────────────────────────────

    private int receivedTokens = 0;
    private boolean isThinking = false;

    /**
     * Called when a streaming token arrives during THINKING phase.
     * Updates the last thinking message text.
     */
    private void onThinkingToken(@NonNull String token) {
        if (!isThinking) return;
        receivedTokens++;
        updateThinkingMessage("Receiving... " + receivedTokens + " tokens");
    }

    /** Add or remove the thinking indicator message in the chat list. */
    private void updateThinkingRow(boolean thinking) {
        // Protect the THINKING indicator from being removed when:
        // - A summary is pending (summaryPending=true), OR
        // - Execution just completed and we're waiting for summarize to start
        //   (executionJustCompleted=true — set before onStateChanged(IDLE) fires).
        if (!thinking && (summaryPending || executionJustCompleted)) return;
        isThinking = thinking;
        if (thinking) {
            receivedTokens = 0;
            chatMessages.add(AgentMessage.thinking(getString(R.string.agent_state_thinking)));
        } else {
            removeThinkingMessage();
        }
        adapter.submitList(new ArrayList<>(chatMessages));
        messagesList.post(() -> {
            if (adapter.getItemCount() > 0) {
                messagesList.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    /** Update the text of the last thinking message. */
    private void updateThinkingMessage(@NonNull String text) {
        // Sync chatMessages copy
        for (int i = chatMessages.size() - 1; i >= 0; i--) {
            if (chatMessages.get(i).type == AgentMessage.Type.THINKING) {
                chatMessages.set(i, AgentMessage.thinking(text));
                break;
            }
        }
        // Targeted update — falls back to full submitList if THINKING item
        // isn't found in adapter (e.g. ViewHolder not yet created after submitList)
        if (!adapter.updateThinkingMessage(text)) {
            adapter.submitList(new ArrayList<>(chatMessages));
        }
    }

    /** Remove the thinking message from the chat list. */
    private void removeThinkingMessage() {
        for (int i = chatMessages.size() - 1; i >= 0; i--) {
            if (chatMessages.get(i).type == AgentMessage.Type.THINKING) {
                chatMessages.remove(i);
                return;
            }
        }
    }

    // ─ Summarize Streaming ───────────────────────────────────────────

    /** Accumulates streaming tokens during Summarize phase. */
    private final StringBuilder summaryContent = new StringBuilder();
    private boolean summaryStarted = false;
    private int summaryMessagePosition = -1;
    private boolean summaryPending = false;
    private int summaryReceivedTokens = 0;
    /**
     * Set true in onExecutionComplete() — protects the THINKING indicator from being
     * removed by the IDLE state transition that fires before the summary phase starts.
     * Reset in onSummaryComplete() / submitToAgent() / onError().
     */
    private boolean executionJustCompleted = false;

    /**
     * Append a streaming token to the summary.
     * Optimized rendering logic:
     * - First few tokens: update THINKING message to show token count
     * - After 100 chars: replace THINKING with ASSISTANT summary message
     * - Subsequent tokens: update ASSISTANT message using payload-based partial rebind
     */
    private void appendSummaryToken(@NonNull String token) {
        summaryContent.append(token);
        summaryReceivedTokens++;

        // Large single token (chatSync fallback): show summary immediately
        if (!summaryStarted && token.length() > 50) {
            summaryStarted = true;
            doAppendSummaryToken(summaryContent.toString());
            return;
        }

        // First token: show "Receiving..." message
        if (!summaryStarted) {
            summaryStarted = true;
            updateThinkingMessage("Receiving… " + summaryReceivedTokens + " tokens");
            return;
        }

        // Check if enough content to replace THINKING with summary
        if (summaryContent.length() > 100 && summaryMessagePosition < 0) {
            doAppendSummaryToken(summaryContent.toString());
        } else if (summaryMessagePosition >= 0) {
            // Already have ASSISTANT message: update using payload
            doAppendSummaryToken(summaryContent.toString());
        } else {
            // Not enough yet: keep showing "Receiving… X tokens"
            updateThinkingMessage("Receiving… " + summaryReceivedTokens + " tokens");
        }
    }

    /**
     * Display or update the summary message.
     * Uses payload-based partial updates for efficient streaming.
     */
    private void doAppendSummaryToken(@NonNull String text) {
        if (summaryMessagePosition < 0) {
            // First display: find and replace the THINKING message with ASSISTANT summary
            boolean found = false;
            for (int i = chatMessages.size() - 1; i >= 0; i--) {
                if (chatMessages.get(i).type == AgentMessage.Type.THINKING) {
                    summaryMessagePosition = i;
                    AgentMessage summaryMsg = AgentMessage.assistant(text);
                    chatMessages.set(i, summaryMsg);
                    adapter.setItem(i, summaryMsg);
                    found = true;
                    break;
                }
            }

            if (!found) {
                // THINKING not found: try to update existing ASSISTANT or append new one
                for (int i = chatMessages.size() - 1; i >= 0; i--) {
                    if (chatMessages.get(i).type == AgentMessage.Type.ASSISTANT) {
                        summaryMessagePosition = i;
                        AgentMessage summaryMsg = AgentMessage.assistant(text);
                        chatMessages.set(i, summaryMsg);
                        adapter.setItem(i, summaryMsg);
                        scrollToBottom();
                        showEmptyState(false);
                        return;
                    }
                }
                // No ASSISTANT found: append new one
                AgentMessage summaryMsg = AgentMessage.assistant(text);
                chatMessages.add(summaryMsg);
                summaryMessagePosition = chatMessages.size() - 1;
                adapter.addItem(summaryMsg);
            }
        } else {
            // Subsequent tokens: use payload-based update for efficiency
            AgentMessage summaryMsg = AgentMessage.assistant(text);
            chatMessages.set(summaryMessagePosition, summaryMsg);

            // Try efficient payload-based update first, fall back to full rebind
            if (!adapter.updateLastAssistantMessageText(text)) {
                adapter.setItem(summaryMessagePosition, summaryMsg);
            }
        }

        scrollToBottom();
        showEmptyState(false);
    }

    // ── Profile Persistence ───────────────────────────────────────────

    /**
     * Restore the Agent's active SSH profile from SharedPreferences.
     *
     * <p>When the app restarts, {@code MainActivity.activeProfile} is null because
     * it's only set in-memory by TargetSettingsSheet. This method reads the persisted
     * profile ID from SharedPreferences and restores it so that AgentController,
     * session bar, and tool executors can find the correct profile on startup.
     *
     * <p>Safe to call multiple times — only restores if no profile is currently active
     * in Activity memory and a valid profile ID is persisted.
     */
    private void restoreAgentSshProfile() {
        if (!isAdded()) return;

        AgentEnvironment env = (AgentEnvironment) requireActivity();

        // Already restored (or user has an active profile in memory) → skip
        if (env.getActiveSshProfile() != null) return;

        // Read persisted profile ID
        String profileId = requireContext()
                .getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                .getString("agent_active_profile_id", null);
        if (profileId == null || profileId.isEmpty()) return;

        // Look up the profile in CredentialManager and restore it
        try {
            CredentialManager credentialManager = new CredentialManager(requireContext());
            for (CredentialProfile p : credentialManager.getAllProfiles()) {
                if (profileId.equals(p.getId())) {
                    env.setActiveSshProfile(p);
                    Log.d(TAG, "Restored Agent SSH profile: " + p.getDisplayLabel());
                    return;
                }
            }
            // Profile ID exists but profile no longer found (deleted) → clear stale pref
            Log.d(TAG, "Persisted Agent profile ID not found, clearing: " + profileId);
            requireContext().getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                    .edit().remove("agent_active_profile_id").apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to restore Agent SSH profile", e);
        }
    }

    // ── Session Bar ─────────────────────────────────────────────────

    /**
     * Update the session bar to reflect the current execution mode.
     * Checks the live active SSH profile state (not the controller's
     * executionMode, which is only set at controller creation time).
     * SSH mode shows the profile label, or "SSH connecting…" while connecting.
     * HID mode shows the target OS.
     */
    public void updateSessionBar() {
        if (sessionHint == null || !isAdded()) return;
        AgentEnvironment env = (AgentEnvironment) requireActivity();
        com.openterface.terminal.CredentialProfile profile = env.getActiveSshProfile();

        if (profile != null) {
            // Terminal (SSH) mode
            com.openterface.terminal.SshClient sshClient = env.getSshClient();
            if (sshClient != null && sshClient.isSessionConnected()) {
                // SSH connected — show profile label
                sessionHint.setText("Target: " + profile.getDisplayLabel());
            } else if (sshClient != null) {
                // SSH client exists but not yet connected — connecting
                sessionHint.setText("SSH connecting…");
            } else {
                // Profile selected but SSH not started yet
                sessionHint.setText("Target: SSH");
            }
        } else {
            // HID mode — check if a target OS is actually selected
            String agentOs = requireContext()
                    .getSharedPreferences("agent_prefs", android.content.Context.MODE_PRIVATE)
                    .getString("agent_target_os", "");
            if (agentOs != null && !agentOs.isEmpty()) {
                sessionHint.setText("Target: " + agentOs.toUpperCase());
            } else {
                // Neither SSH profile nor target OS selected — show hint with terminal icon
                String hintTemplate = getString(R.string.agent_target_none_hint);
                android.graphics.drawable.Drawable icon = getResources()
                        .getDrawable(R.drawable.ic_terminal, requireContext().getTheme());
                int iconSize = (int) (16 * getResources().getDisplayMetrics().density);
                icon.setBounds(0, 0, iconSize, iconSize);
                icon.setTintList(android.content.res.ColorStateList.valueOf(
                        getResources().getColor(R.color.text_secondary, requireContext().getTheme())));
                String placeholder = "￼";
                String fullText = hintTemplate.replace("%s", placeholder);
                android.text.SpannableString spannable = new android.text.SpannableString(fullText);
                int start = fullText.indexOf(placeholder);
                spannable.setSpan(new android.text.style.ImageSpan(icon),
                        start, start + placeholder.length(),
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sessionHint.setText(spannable);
            }
        }
    }

    /**
     * Scroll the messages list to the bottom.
     * Use after adding/updating messages to ensure the latest content is visible.
     */
    private void scrollToBottom() {
        if (messagesList != null && adapter != null && adapter.getItemCount() > 0) {
            messagesList.post(() -> {
                if (adapter.getItemCount() > 0) {
                    messagesList.scrollToPosition(adapter.getItemCount() - 1);
                }
            });
        }
    }

    /**
     * Fallback keyboard handler: if {@code adjustResize} fails to shrink the
     * root (device-specific), apply bottom padding so the input bar stays above
     * the keyboard. Normally a no-op when adjustResize works correctly.
     */
    private void setupKeyboardHandler() {
        if (inputField == null) return;
        final View rootView = getView();
        if (rootView == null) return;

        inputField.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                rootView.getViewTreeObserver()
                        .addOnGlobalLayoutListener(layoutListener);
            } else {
                rootView.getViewTreeObserver()
                        .removeOnGlobalLayoutListener(layoutListener);
                if (rootView.getPaddingBottom() > 0) {
                    rootView.setPadding(0, 0, 0, 0);
                }
            }
        });
    }

    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = () -> {
        View rootView = getView();
        if (rootView == null) return;

        Rect visibleFrame = new Rect();
        rootView.getWindowVisibleDisplayFrame(visibleFrame);

        int[] loc = new int[2];
        rootView.getLocationOnScreen(loc);
        int rootBottomOnScreen = loc[1] + rootView.getHeight();
        int covered = rootBottomOnScreen - visibleFrame.bottom;

        // adjustResize should handle the resize; only fall back to padding
        // if the system didn't shrink the root enough.
        if (covered > 50 && rootView.getPaddingBottom() < covered) {
            rootView.setPadding(0, 0, 0, covered);
        } else if (covered <= 50 && rootView.getPaddingBottom() > 0) {
            rootView.setPadding(0, 0, 0, 0);
        }
    };

    // ── Suggested Prompts (6 hardcoded FAQs, aligned with iOS) ──────

    private static final int[][] FAQS = {
            { R.drawable.ic_faq_info,    R.string.faq_os_version,      R.string.faq_os_version_prompt },
            { R.drawable.ic_faq_storage, R.string.faq_disk_size,       R.string.faq_disk_size_prompt  },
            { 0,                         R.string.faq_memory,          R.string.faq_memory_prompt     },
            { R.drawable.ic_faq_clock,   R.string.faq_uptime,          R.string.faq_uptime_prompt     },
            { R.drawable.ic_faq_network, R.string.faq_ip,              R.string.faq_ip_prompt         },
            { R.drawable.ic_faq_cpu,     R.string.faq_cpu,             R.string.faq_cpu_prompt        },
    };

    /**
     * Populate the suggested prompts grid with 6 hardcoded FAQ chips.
     * Layout: 2 columns × 3 rows, 8dp spacing, icon + title per chip.
     * Matches iOS faqChips LazyVGrid layout.
     */
    private void setupSuggestedPrompts() {
        if (suggestedPromptsContainer == null) return;
        suggestedPromptsContainer.removeAllViews();

        Context ctx = requireContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        int chipSpacing = Math.round(density * 8);
        int hPad = Math.round(density * 10);
        int vPad = Math.round(density * 8);
        int iconSize = Math.round(density * 16);
        int iconMarginEnd = Math.round(density * 6);
        int textPrimary = ctx.getResources().getColor(R.color.text_primary, null);

        for (int[] faq : FAQS) {
            LinearLayout chip = new LinearLayout(ctx);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
            chip.setBackgroundResource(R.drawable.agent_suggested_chip_bg);
            chip.setPadding(hPad, vPad, hPad, vPad);

            if (faq[0] != 0) {
                ImageView icon = new ImageView(ctx);
                icon.setImageResource(faq[0]);
                icon.setColorFilter(textPrimary);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconLp.setMarginEnd(iconMarginEnd);
                icon.setLayoutParams(iconLp);
                chip.addView(icon);
            }

            TextView title = new TextView(ctx);
            title.setText(faq[1]);
            title.setTextSize(13f);
            title.setTextColor(textPrimary);
            chip.addView(title);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.setMargins(0, 0, chipSpacing, chipSpacing);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            chip.setLayoutParams(lp);

            final int promptRes = faq[2];
            chip.setOnClickListener(v -> {
                if (inputField != null && agentController != null) {
                    inputField.setText(promptRes);
                    inputField.setSelection(inputField.getText().length());
                    inputField.requestFocus();
                }
            });

            suggestedPromptsContainer.addView(chip);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (agentController != null) {
            agentController.shutdown();
            agentController = null;
        }
        if (verifyExecutor != null) {
            verifyExecutor.shutdownNow();
            verifyExecutor = null;
        }
    }
}
