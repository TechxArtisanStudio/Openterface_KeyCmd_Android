package com.openterface.keymod.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.openterface.keymod.R;
import com.openterface.terminal.CredentialManager;
import com.openterface.terminal.CredentialProfile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        EditText privateKeyInput = dialogView.findViewById(R.id.credential_private_key_input);
        EditText keyPassphraseInput = dialogView.findViewById(R.id.credential_key_passphrase_input);
        EditText notesInput = dialogView.findViewById(R.id.credential_notes_input);
        Spinner authTypeSpinner = dialogView.findViewById(R.id.credential_auth_type_spinner);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.credential_password_layout);
        TextInputLayout privateKeyLayout = dialogView.findViewById(R.id.credential_private_key_layout);
        TextInputLayout keyPassphraseLayout = dialogView.findViewById(R.id.credential_key_passphrase_layout);

        // Setup auth type spinner
        String[] authTypeLabels = new String[]{
                getString(R.string.credential_auth_type_password),
                getString(R.string.credential_auth_type_ssh_key)
        };
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, authTypeLabels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        authTypeSpinner.setAdapter(spinnerAdapter);

        // Track selected auth type
        final String[] selectedAuthType = {CredentialProfile.AUTH_TYPE_PASSWORD};
        // Track if editing an existing SSH key
        final boolean[] hasExistingKey = {false};

        authTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedAuthType[0] = CredentialProfile.AUTH_TYPE_PASSWORD;
                    passwordLayout.setVisibility(View.VISIBLE);
                    privateKeyLayout.setVisibility(View.GONE);
                    keyPassphraseLayout.setVisibility(View.GONE);
                } else {
                    selectedAuthType[0] = CredentialProfile.AUTH_TYPE_SSH_KEY;
                    passwordLayout.setVisibility(View.GONE);
                    privateKeyLayout.setVisibility(View.VISIBLE);
                    keyPassphraseLayout.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No-op
            }
        });

        if (existingProfile != null) {
            nameInput.setText(existingProfile.getName());
            hostInput.setText(existingProfile.getHost());
            portInput.setText(String.valueOf(existingProfile.getPort()));
            usernameInput.setText(existingProfile.getUsername());
            passwordInput.setText(existingProfile.getPassword());
            // Show "已导入 Key" placeholder instead of actual key content
            if (existingProfile.isSshKeyAuth() && !existingProfile.getPrivateKey().isEmpty()) {
                privateKeyInput.setHint(R.string.credential_key_imported);
                privateKeyInput.setText("");
                hasExistingKey[0] = true;
            } else {
                privateKeyInput.setText(existingProfile.getPrivateKey());
            }
            keyPassphraseInput.setText(existingProfile.getKeyPassphrase());
            notesInput.setText(existingProfile.getNotes());
            // Set spinner to match existing auth type
            if (existingProfile.isSshKeyAuth()) {
                authTypeSpinner.setSelection(1);
            } else {
                authTypeSpinner.setSelection(0);
            }
        } else {
            portInput.setText("22");
            authTypeSpinner.setSelection(0);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existingProfile != null ? R.string.credential_edit : R.string.credential_add)
                .setView(dialogView)
                .setPositiveButton(R.string.credential_save, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String host = hostInput.getText().toString().trim();
                    String portStr = portInput.getText().toString().trim();
                    String username = usernameInput.getText().toString().trim();
                    String password = passwordInput.getText().toString();
                    String privateKey = privateKeyInput.getText().toString();
                    String keyPassphrase = keyPassphraseInput.getText().toString();
                    String notes = notesInput.getText().toString().trim();
                    String authType = selectedAuthType[0];

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
                    if (CredentialProfile.AUTH_TYPE_PASSWORD.equals(authType) && password.isEmpty()) {
                        Toast.makeText(getContext(), R.string.credential_password_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (CredentialProfile.AUTH_TYPE_SSH_KEY.equals(authType)) {
                        // Allow empty privateKey when editing existing key (keep original)
                        if (privateKey.isEmpty() && !hasExistingKey[0]) {
                            Toast.makeText(getContext(), R.string.credential_private_key_required, Toast.LENGTH_SHORT).show();
                            return;
                        }
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
                        existingProfile.setAuthType(authType);
                        // Keep existing key when editing and privateKey field is empty
                        if (privateKey.isEmpty() && hasExistingKey[0] && existingProfile.isSshKeyAuth()) {
                            // Don't update privateKey, keep original
                        } else {
                            existingProfile.setPrivateKey(privateKey);
                        }
                        existingProfile.setKeyPassphrase(keyPassphrase);
                        existingProfile.setNotes(notes);
                        credentialManager.updateProfile(existingProfile);
                    } else {
                        CredentialProfile profile = new CredentialProfile();
                        profile.setName(name);
                        profile.setHost(host);
                        profile.setPort(port);
                        profile.setUsername(username);
                        profile.setPassword(password);
                        profile.setAuthType(authType);
                        profile.setPrivateKey(privateKey);
                        profile.setKeyPassphrase(keyPassphrase);
                        profile.setNotes(notes);
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

            // Auth type icon
            if (holder.authTypeIcon != null) {
                if (CredentialProfile.AUTH_TYPE_SSH_KEY.equals(profile.getAuthType())) {
                    holder.authTypeIcon.setImageResource(R.drawable.ic_vpn_key_24);
                    holder.authTypeIcon.setVisibility(View.VISIBLE);
                    holder.authTypeIcon.setContentDescription(
                            holder.itemView.getContext().getString(R.string.credential_auth_type_ssh_key));
                } else {
                    holder.authTypeIcon.setImageResource(R.drawable.ic_lock_24);
                    holder.authTypeIcon.setVisibility(View.VISIBLE);
                    holder.authTypeIcon.setContentDescription(
                            holder.itemView.getContext().getString(R.string.credential_auth_type_password));
                }
            }

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
                new MaterialAlertDialogBuilder(requireContext())
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
            ImageView authTypeIcon;
            ImageButton editButton;
            ImageButton deleteButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                activeRadio = itemView.findViewById(R.id.credential_active_radio);
                nameText = itemView.findViewById(R.id.credential_name);
                detailsText = itemView.findViewById(R.id.credential_details);
                authTypeIcon = itemView.findViewById(R.id.credential_auth_type_icon);
                editButton = itemView.findViewById(R.id.credential_edit_button);
                deleteButton = itemView.findViewById(R.id.credential_delete_button);
            }
        }
    }
}