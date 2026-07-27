package com.openterface.keymod;

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
import com.openterface.terminal.CredentialManager;
import com.openterface.terminal.CredentialProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet with two sections: Target OS picker + Terminal Profile list.
 *
 * <p>Shared by the main header button and the Agent top bar. Selecting a profile
 * mirrors its {@code targetOs} to the global {@code PREF_TARGET_OS} so that HID,
 * Compose, Help and other features follow the switch. Selecting an OS directly
 * does NOT write back to the active profile (one-way mirror).</p>
 */
public class TargetSettingsSheet extends BottomSheetDialogFragment {

    private static final String TAG = "TargetSettingsSheet";

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

    // ── Lifecycle ────────────────────────────────────────────────────────

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) d).getBehavior();
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setHideable(true);
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        credentialManager = new CredentialManager(requireContext());

        bindViews(view);
        setupOsButtons();
        setupProfileList();
        refreshUi();
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
        // Store OS in Agent-specific preference — does NOT affect KM Pro / KM Basic / Terminal
        requireContext().getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                .edit().putString("agent_target_os", os).apply();
        currentOs = os;
        refreshOsButtonStyles();
        // Do NOT close the sheet — user may still want to pick a profile.
    }

    private void refreshOsButtonStyles() {
        int selectedStroke = MaterialColors.getColor(
                requireView(), com.google.android.material.R.attr.colorPrimary);
        int neutralStroke = 0;

        setButtonStroke(osButtonMacos,   "macos".equals(currentOs)   ? selectedStroke : neutralStroke);
        setButtonStroke(osButtonWindows, "windows".equals(currentOs) ? selectedStroke : neutralStroke);
        setButtonStroke(osButtonLinux,   "linux".equals(currentOs)   ? selectedStroke : neutralStroke);
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
            public void onProfileClick(@NonNull CredentialProfile profile) {
                onProfileSelected(profile);
            }
        });
        profileList.setLayoutManager(new LinearLayoutManager(requireContext()));
        profileList.setAdapter(profileAdapter);
    }

    private void onProfileSelected(@NonNull CredentialProfile profile) {
        // 1. Activate this profile
        credentialManager.setActiveProfileId(profile.getId());

        // 2. Mirror profile.targetOs → Agent-specific preference (NOT global)
        String os = profile.getTargetOs();
        requireContext().getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                .edit().putString("agent_target_os", os).apply();

        // 3. Store profile for Agent auto-connect
        MainActivity activity = (MainActivity) requireActivity();
        activity.setActiveSshProfile(profile);

        currentOs = os;

        // 4. Refresh and close
        refreshUi();
        dismiss();
    }

    // ── Refresh ──────────────────────────────────────────────────────────

    private void refreshUi() {
        // Read Agent-specific OS (not global)
        currentOs = requireContext().getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
                .getString("agent_target_os", "macos");
        refreshOsButtonStyles();

        List<CredentialProfile> profiles = credentialManager.getAllProfiles();
        if (profiles.isEmpty()) {
            profileList.setVisibility(View.GONE);
            profileEmpty.setVisibility(View.VISIBLE);
        } else {
            profileList.setVisibility(View.VISIBLE);
            profileEmpty.setVisibility(View.GONE);
            profileAdapter.submitList(new ArrayList<>(profiles));
        }
    }

    // ── Profile Adapter ──────────────────────────────────────────────────

    static class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ViewHolder> {

        interface OnItemClickListener {
            void onProfileClick(@NonNull CredentialProfile profile);
        }

        private List<CredentialProfile> profiles = new ArrayList<>();
        private final OnItemClickListener listener;
        private String activeProfileId;

        ProfileAdapter(@NonNull OnItemClickListener listener) {
            this.listener = listener;
        }

        void submitList(@NonNull List<CredentialProfile> newList) {
            this.profiles = newList;
            // Find active profile id
            for (CredentialProfile p : newList) {
                if (p.isActive()) {
                    activeProfileId = p.getId();
                    break;
                }
            }
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
            boolean isActive = profile.getId().equals(activeProfileId);

            holder.name.setText(profile.getDisplayLabel());
            holder.host.setText(profile.getShortDescription());

            // OS icon
            int osIcon;
            switch (profile.getTargetOs()) {
                case "windows": osIcon = R.drawable.ic_os_windows; break;
                case "linux":   osIcon = R.drawable.ic_os_linux;   break;
                default:        osIcon = R.drawable.ic_os_macos;   break;
            }
            holder.osIcon.setImageResource(osIcon);

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

            card.setOnClickListener(v -> listener.onProfileClick(profile));
        }

        @Override
        public int getItemCount() {
            return profiles.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final TextView name;
            final TextView host;
            final ImageView osIcon;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                card   = itemView.findViewById(R.id.profile_card);
                name   = itemView.findViewById(R.id.profile_name);
                host   = itemView.findViewById(R.id.profile_host);
                osIcon = itemView.findViewById(R.id.profile_os_icon);
            }
        }
    }
}
