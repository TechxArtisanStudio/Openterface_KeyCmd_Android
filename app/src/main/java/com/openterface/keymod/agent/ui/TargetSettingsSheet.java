package com.openterface.keymod.agent.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.terminal.CredentialManager;
import com.openterface.terminal.CredentialProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet with two sections: Target OS picker + Terminal Profile list.
 *
 * <p>The two sections are mutually exclusive (aligned with iOS):</p>
 * <ul>
 *   <li>Selecting an OS clears the active SSH profile → HID mode</li>
 *   <li>Selecting a profile clears the OS highlight → Terminal (SSH) mode</li>
 * </ul>
 */
public class TargetSettingsSheet extends BottomSheetDialogFragment {

    private static final String TAG = "TargetSettingsSheet";

    /** Called when the user changes the target (OS or profile). */
    public interface OnTargetChangedListener {
        void onTargetChanged();
    }

    // OS button views
    private ImageButton osButtonMacos;
    private ImageButton osButtonWindows;
    private ImageButton osButtonLinux;

    // Profile list
    private RecyclerView profileList;
    private TextView profileEmpty;
    private ProfileAdapter profileAdapter;

    private CredentialManager credentialManager;
    private String currentOs = "macos";
    /** Agent's own active profile ID — independent from CredentialManager's global state. */
    @Nullable private String agentActiveProfileId;
    @Nullable private OnTargetChangedListener targetChangedListener;

    // ── Lifecycle ────────────────────────────────────────────────────────

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        // Full window + stronger dim behind sheet
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

            //  Rounded top corners (16dp) via ViewOutlineProvider ──
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
        return inflater.inflate(R.layout.dialog_target_settings, container, false);
    }

    private static final String PREF_AGENT_PROFILE_ID = "agent_active_profile_id";

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        credentialManager = new CredentialManager(requireContext());

        // Restore Agent's active profile:
        // 1. Try Activity memory (survives sheet dismiss while app is alive)
        // 2. Fall back to SharedPreferences (survives app restart)
        MainActivity activity = (MainActivity) requireActivity();
        CredentialProfile agentProfile = activity.getActiveSshProfile();
        if (agentProfile != null) {
            agentActiveProfileId = agentProfile.getId();
        } else {
            agentActiveProfileId = requireContext()
                    .getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                    .getString(PREF_AGENT_PROFILE_ID, null);
            // Also restore to Activity so AgentController can find it
            if (agentActiveProfileId != null) {
                for (CredentialProfile p : credentialManager.getAllProfiles()) {
                    if (p.getId().equals(agentActiveProfileId)) {
                        activity.setActiveSshProfile(p);
                        break;
                    }
                }
            }
        }

        bindViews(view);
        setupOsButtons();
        setupProfileList();
        refreshUi();
    }

    /** Set a listener to be notified when the user changes the target (OS or profile). */
    public void setTargetChangedListener(@Nullable OnTargetChangedListener listener) {
        this.targetChangedListener = listener;
    }

    private void notifyTargetChanged() {
        if (targetChangedListener != null) {
            targetChangedListener.onTargetChanged();
        }
    }

    // ── View binding ─────────────────────────────────────────────────────

    private void bindViews(@NonNull View view) {
        osButtonMacos   = view.findViewById(R.id.os_button_macos);
        osButtonWindows = view.findViewById(R.id.os_button_windows);
        osButtonLinux   = view.findViewById(R.id.os_button_linux);
        profileList     = view.findViewById(R.id.profile_list);
        profileEmpty    = view.findViewById(R.id.profile_empty);
    }

    // ── Section 1: Target OS ─────────────────────────────────────────────

    private void setupOsButtons() {
        osButtonMacos.setOnClickListener(v -> onOsSelected("macos"));
        osButtonWindows.setOnClickListener(v -> onOsSelected("windows"));
        osButtonLinux.setOnClickListener(v -> onOsSelected("linux"));
    }

    private void onOsSelected(@NonNull String os) {
        if (os.equals(currentOs)) {
            // Toggle off: deselect this OS (stay in HID mode with default OS)
            requireContext().getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                    .edit().remove("agent_target_os").apply();
            currentOs = "";
        } else {
            // Select this OS
            requireContext().getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                    .edit().putString("agent_target_os", os).apply();
            currentOs = os;
        }

        // Agent only: clear Agent's own SSH profile reference → HID mode.
        // Do NOT touch credentialManager — Terminal tab's active profile is independent.
        MainActivity activity = (MainActivity) requireActivity();
        activity.setActiveSshProfile(null);
        agentActiveProfileId = null;
        requireContext().getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                .edit().remove(PREF_AGENT_PROFILE_ID).apply();

        refreshUi();
        notifyTargetChanged();
    }

    /**
     * Refresh OS button styles.
     * @param highlightOs OS to highlight, or null to clear all highlights (Terminal mode).
     */
    private void refreshOsButtonStyles(@Nullable String highlightOs) {
        int selectedStroke = MaterialColors.getColor(
                requireView(), com.google.android.material.R.attr.colorPrimary);

        setButtonStroke(osButtonMacos,   "macos".equals(highlightOs)   ? selectedStroke : 0);
        setButtonStroke(osButtonWindows, "windows".equals(highlightOs) ? selectedStroke : 0);
        setButtonStroke(osButtonLinux,   "linux".equals(highlightOs)   ? selectedStroke : 0);
    }

    private void setButtonStroke(@NonNull ImageButton button, int color) {
        // Use background tint as a simple selected indicator.
        if (color != 0) {
            button.setColorFilter(color);
        } else {
            button.clearColorFilter();
            button.setColorFilter(getResources().getColor(R.color.text_primary, null));
        }
    }

    // ── Section 2: Terminal Profile ──────────────────────────────────────

    private void setupProfileList() {
        profileAdapter = new ProfileAdapter(new ProfileAdapter.OnItemClickListener() {
            @Override
            public void onProfileClick(@NonNull CredentialProfile profile, boolean wasActive) {
                if (wasActive) {
                    // Toggle off: deselect this profile → enter HID mode
                    onProfileDeselected();
                } else {
                    onProfileSelected(profile);
                }
            }
        });
        profileList.setLayoutManager(new LinearLayoutManager(requireContext()));
        profileList.setAdapter(profileAdapter);
    }

    private void onProfileSelected(@NonNull CredentialProfile profile) {
        // Agent only: store profile for Agent auto-connect (does NOT affect Terminal tab)
        MainActivity activity = (MainActivity) requireActivity();
        activity.setActiveSshProfile(profile);
        agentActiveProfileId = profile.getId();

        // Persist so selection survives app restart
        requireContext().getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                .edit().putString(PREF_AGENT_PROFILE_ID, profile.getId()).apply();

        refreshUi();
        notifyTargetChanged();
    }

    /** Deselect the active profile — clears Agent's SSH mode and returns to HID mode. */
    private void onProfileDeselected() {
        // Agent only: clear SSH profile reference → enter HID mode
        MainActivity activity = (MainActivity) requireActivity();
        activity.setActiveSshProfile(null);
        agentActiveProfileId = null;

        // Clear persisted profile ID
        requireContext().getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                .edit().remove(PREF_AGENT_PROFILE_ID).apply();

        refreshUi();
        notifyTargetChanged();
    }

    // ── Refresh ──────────────────────────────────────────────────────────

    private void refreshUi() {
        // Check current mode: Terminal (Agent SSH profile active) or HID
        MainActivity activity = (MainActivity) requireActivity();
        boolean isTerminalMode = activity.getActiveSshProfile() != null;

        // Read OS from prefs
        currentOs = requireContext().getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                .getString("agent_target_os", "");

        // OS buttons: highlight only in HID mode
        refreshOsButtonStyles(isTerminalMode ? null : (currentOs.isEmpty() ? null : currentOs));

        // Profile list
        List<CredentialProfile> profiles = credentialManager.getAllProfiles();
        if (profiles.isEmpty()) {
            profileList.setVisibility(View.GONE);
            profileEmpty.setVisibility(View.VISIBLE);
        } else {
            profileList.setVisibility(View.VISIBLE);
            profileEmpty.setVisibility(View.GONE);
            // Use Agent's own activeProfileId for highlighting (independent from CredentialManager)
            profileAdapter.submitList(new ArrayList<>(profiles), agentActiveProfileId);
        }
    }

    // ── Profile Adapter ──────────────────────────────────────────────────

    static class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ViewHolder> {

        interface OnItemClickListener {
            void onProfileClick(@NonNull CredentialProfile profile, boolean wasActive);
        }

        private List<CredentialProfile> profiles = new ArrayList<>();
        private final OnItemClickListener listener;
        private String activeProfileId;

        ProfileAdapter(@NonNull OnItemClickListener listener) {
            this.listener = listener;
        }

        /**
         * @param newList profiles to display
         * @param agentActiveId Agent's own active profile ID, or null for no highlight
         */
        void submitList(@NonNull List<CredentialProfile> newList, @Nullable String agentActiveId) {
            this.profiles = newList;
            this.activeProfileId = agentActiveId;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_target_settings_profile, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CredentialProfile profile = profiles.get(position);
            boolean isActive = activeProfileId != null && profile.getId().equals(activeProfileId);

            holder.name.setText(profile.getDisplayLabel());
            holder.host.setText(profile.getShortDescription());

            // Show checkmark for active profile
            holder.checkIcon.setVisibility(isActive ? View.VISIBLE : View.GONE);

            // Active state — highlight card border
            MaterialCardView card = holder.card;
            if (isActive) {
                int primary = MaterialColors.getColor(
                        card, com.google.android.material.R.attr.colorPrimary);
                card.setStrokeWidth(2);
                card.setStrokeColor(primary);
            } else {
                card.setStrokeWidth(0);
            }

            card.setOnClickListener(v -> listener.onProfileClick(profile, isActive));
        }

        @Override
        public int getItemCount() {
            return profiles.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final TextView name;
            final TextView host;
            final ImageView checkIcon;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                card      = itemView.findViewById(R.id.profile_card);
                name      = itemView.findViewById(R.id.profile_name);
                host      = itemView.findViewById(R.id.profile_host);
                checkIcon = itemView.findViewById(R.id.profile_check_icon);
            }
        }
    }
}
