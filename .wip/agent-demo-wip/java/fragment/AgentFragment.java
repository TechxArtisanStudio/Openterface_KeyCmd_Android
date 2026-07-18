package com.openterface.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.openterface.keymod.R;
import com.openterface.keymod.SettingsActivity;
import com.openterface.keymod.agent.AgentDemoPlayer;
import com.openterface.keymod.agent.AgentDemoScript;
import com.openterface.keymod.agent.AgentDemoScriptRegistry;
import com.openterface.keymod.agent.AgentMessage;
import com.openterface.keymod.agent.AgentMessageAdapter;

import java.util.List;

/** Agent mode marketing MVP — curated demo scripts with Plan/Act UI. */
public class AgentFragment extends Fragment {

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
    private TextView connectionPill;

    private AgentMessageAdapter adapter;
    private AgentDemoPlayer demoPlayer;
    @Nullable private AgentDemoScript pendingAutoScript;

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
        setupInput();
        setupGate(view);
        setupDemoPicker();
        applyLaunchArgs();
    }

    private void bindViews(@NonNull View view) {
        gateOverlay = view.findViewById(R.id.agent_gate_overlay);
        demoPickerScroll = view.findViewById(R.id.agent_demo_picker_scroll);
        demoPickerContainer = view.findViewById(R.id.agent_demo_picker_container);
        messagesList = view.findViewById(R.id.agent_messages_list);
        emptyHint = view.findViewById(R.id.agent_empty_hint);
        inputField = view.findViewById(R.id.agent_input);
        sendButton = view.findViewById(R.id.agent_send_button);
        connectionPill = view.findViewById(R.id.agent_connection_pill);
    }

    private void setupMessagesList() {
        adapter = new AgentMessageAdapter();
        adapter.setActBarListener(new AgentMessageAdapter.ActBarListener() {
            @Override
            public void onApprove() {
                if (demoPlayer != null) {
                    demoPlayer.approveAndRun();
                }
            }

            @Override
            public void onEditPlan() {
                Toast.makeText(requireContext(), R.string.agent_edit_plan_coming_soon, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancel() {
                if (demoPlayer != null) {
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
                        messagesList.smoothScrollToPosition(adapter.getItemCount() - 1);
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

    private void setupInput() {
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        sendButton.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.agent_input_demo_only, Toast.LENGTH_SHORT).show());
    }

    private void setupGate(@NonNull View view) {
        MaterialButton byok = view.findViewById(R.id.agent_gate_byok_button);
        MaterialButton github = view.findViewById(R.id.agent_gate_github_button);
        MaterialButton skip = view.findViewById(R.id.agent_gate_demo_skip_button);

        byok.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SettingsActivity.class);
            intent.putExtra("settings_tab_index", 2);
            startActivity(intent);
        });
        github.setOnClickListener(v -> mockConnect(getString(R.string.agent_gate_github_connected_toast)));
        skip.setOnClickListener(v -> mockConnect(null));

        if (isDemoTokenConnected() || shouldSkipGate()) {
            showConnectedUi();
            maybeStartAutoDemo();
        } else {
            showGate(true);
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
        demoPickerScroll.setVisibility(View.VISIBLE);
        connectionPill.setText(R.string.agent_connection_ready);
        showEmptyState(true);
    }

    private void showGate(boolean visible) {
        gateOverlay.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showEmptyState(boolean empty) {
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
                card.setStrokeColor(requireContext().getColor(R.color.color_agent));
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
}
