package com.openterface.keymod.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.openterface.keymod.R;
import com.openterface.terminal.CredentialManager;
import com.openterface.terminal.CredentialProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings tab for managing SSH credential profiles.
 */
public class CredentialSettingsFragment extends Fragment {

    private RecyclerView credentialList;
    private TextView emptyText;
    private MaterialButton addButton;
    private CredentialManager credentialManager;
    private CredentialAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_credentials, container, false);

        credentialManager = new CredentialManager(requireContext());
        credentialManager.migrateFromTerminalPrefs(requireContext());

        credentialList = view.findViewById(R.id.credential_list);
        emptyText = view.findViewById(R.id.credential_empty_text);
        addButton = view.findViewById(R.id.credential_add_button);

        credentialList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CredentialAdapter(loadProfiles());
        credentialList.setAdapter(adapter);

        updateEmptyState();

        addButton.setOnClickListener(v -> showAddEditDialog(null));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
    }

    private List<CredentialProfile> loadProfiles() {
        List<CredentialProfile> profiles = credentialManager.getAllProfiles();
        return profiles != null ? profiles : new ArrayList<>();
    }

    private void refreshList() {
        adapter.setProfiles(loadProfiles());
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = adapter.getItemCount() == 0;
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        credentialList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showAddEditDialog(@Nullable CredentialProfile existingProfile) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_credential_profile, null);

        EditText nameInput = dialogView.findViewById(R.id.credential_name_input);
        EditText hostInput = dialogView.findViewById(R.id.credential_host_input);
        EditText portInput = dialogView.findViewById(R.id.credential_port_input);
        EditText usernameInput = dialogView.findViewById(R.id.credential_username_input);
        EditText passwordInput = dialogView.findViewById(R.id.credential_password_input);

        if (existingProfile != null) {
            nameInput.setText(existingProfile.getName());
            hostInput.setText(existingProfile.getHost());
            portInput.setText(String.valueOf(existingProfile.getPort()));
            usernameInput.setText(existingProfile.getUsername());
            passwordInput.setText(existingProfile.getPassword());
        } else {
            portInput.setText("22");
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(existingProfile != null ? R.string.credential_edit : R.string.credential_add)
                .setView(dialogView)
                .setPositiveButton(R.string.credential_save, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String host = hostInput.getText().toString().trim();
                    String portStr = portInput.getText().toString().trim();
                    String username = usernameInput.getText().toString().trim();
                    String password = passwordInput.getText().toString();

                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), R.string.credential_profile_name_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (host.isEmpty()) {
                        Toast.makeText(getContext(), R.string.credential_host_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (username.isEmpty()) {
                        Toast.makeText(getContext(), R.string.credential_username_required, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int port = 22;
                    try {
                        if (!portStr.isEmpty()) {
                            port = Integer.parseInt(portStr);
                        }
                    } catch (NumberFormatException ignored) {}

                    if (existingProfile != null) {
                        existingProfile.setName(name);
                        existingProfile.setHost(host);
                        existingProfile.setPort(port);
                        existingProfile.setUsername(username);
                        existingProfile.setPassword(password);
                        credentialManager.updateProfile(existingProfile);
                    } else {
                        CredentialProfile profile = new CredentialProfile();
                        profile.setName(name);
                        profile.setHost(host);
                        profile.setPort(port);
                        profile.setUsername(username);
                        profile.setPassword(password);
                        credentialManager.addProfile(profile);
                    }

                    Toast.makeText(getContext(), R.string.credential_saved, Toast.LENGTH_SHORT).show();
                    refreshList();
                })
                .setNegativeButton(R.string.credential_cancel, null)
                .show();
    }

    // ─── RecyclerView Adapter ────────────────────────────────────────────

    private class CredentialAdapter extends RecyclerView.Adapter<CredentialAdapter.ViewHolder> {

        private List<CredentialProfile> profiles;

        CredentialAdapter(List<CredentialProfile> profiles) {
            this.profiles = profiles;
        }

        void setProfiles(List<CredentialProfile> profiles) {
            this.profiles = profiles;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_credential_profile, parent, false);
            return new ViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CredentialProfile profile = profiles.get(position);
            holder.nameText.setText(profile.getDisplayLabel());
            holder.detailsText.setText(profile.getShortDescription());

            // Prevent loop: set checked without triggering listener
            holder.activeRadio.setOnCheckedChangeListener(null);
            holder.activeRadio.setChecked(profile.isActive());
            holder.activeRadio.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    credentialManager.setActiveProfileId(profile.getId());
                    refreshList();
                }
            });

            holder.editButton.setOnClickListener(v -> showAddEditDialog(profile));
            holder.deleteButton.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.credential_delete)
                        .setMessage(R.string.credential_confirm_delete)
                        .setPositiveButton(R.string.credential_delete, (dialog, which) -> {
                            credentialManager.deleteProfile(profile.getId());
                            Toast.makeText(getContext(), R.string.credential_deleted, Toast.LENGTH_SHORT).show();
                            refreshList();
                        })
                        .setNegativeButton(R.string.credential_cancel, null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return profiles != null ? profiles.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            RadioButton activeRadio;
            TextView nameText;
            TextView detailsText;
            ImageButton editButton;
            ImageButton deleteButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                activeRadio = itemView.findViewById(R.id.credential_active_radio);
                nameText = itemView.findViewById(R.id.credential_name);
                detailsText = itemView.findViewById(R.id.credential_details);
                editButton = itemView.findViewById(R.id.credential_edit_button);
                deleteButton = itemView.findViewById(R.id.credential_delete_button);
            }
        }
    }
}