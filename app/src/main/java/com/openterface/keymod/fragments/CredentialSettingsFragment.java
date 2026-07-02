package com.openterface.keymod.fragments;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.openterface.keymod.R;
import com.openterface.keymod.util.SensitivePageShield;
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

    private SearchView searchView;
    private RecyclerView credentialList;
    private TextView emptyText;
    private MaterialButton addButton;
    private CredentialManager credentialManager;
    private CredentialAdapter adapter;
    private SensitivePageShield shield;
    private List<CredentialProfile> allProfiles = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_credentials, container, false);

        credentialManager = new CredentialManager(requireContext());
        credentialManager.migrateFromTerminalPrefs(requireContext());

        searchView = view.findViewById(R.id.credential_search_view);
        credentialList = view.findViewById(R.id.credential_list);
        emptyText = view.findViewById(R.id.credential_empty_text);
        addButton = view.findViewById(R.id.credential_add_button);

        credentialList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CredentialAdapter(new ArrayList<>());
        credentialList.setAdapter(adapter);

        addButton.setOnClickListener(v -> showAddEditDialog(null));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterProfiles(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterProfiles(newText);
                return true;
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
        // Enable sensitive page shielding (prevent screenshots/screen recording)
        if (shield == null) {
            shield = new SensitivePageShield(requireActivity());
        }
        shield.enable();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Disable shielding (restore screenshot capability when switching to other Tabs)
        if (shield != null) {
            shield.disable();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (shield != null) {
            shield.release();
            shield = null;
        }
    }

    private List<CredentialProfile> loadProfiles() {
        List<CredentialProfile> profiles = credentialManager.getAllProfiles();
        return profiles != null ? profiles : new ArrayList<>();
    }

    private void refreshList() {
        allProfiles = loadProfiles();
        filterProfiles(searchView.getQuery().toString());
    }

    private void filterProfiles(String query) {
        List<CredentialProfile> filtered = new ArrayList<>();
        String lowerQuery = query != null ? query.trim().toLowerCase() : "";

        for (CredentialProfile profile : allProfiles) {
            boolean matchesText = lowerQuery.isEmpty()
                    || profile.getName().toLowerCase().contains(lowerQuery)
                    || profile.getShortDescription().toLowerCase().contains(lowerQuery);
            if (!matchesText) continue;

            filtered.add(profile);
        }
        adapter.setProfiles(filtered);
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
        LinearLayout tagsContainer = dialogView.findViewById(R.id.credential_tags_container);
        ImageView tagsIcon = dialogView.findViewById(R.id.credential_tags_icon);
        ChipGroup tagsChipGroup = dialogView.findViewById(R.id.credential_tags_chip_group);
        EditText tagsInput = dialogView.findViewById(R.id.credential_tags_input);

        // Track tags selected for this profile
        final List<String> currentTags = new ArrayList<>();

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
            // Show "Imported Key" placeholder instead of actual key content
            if (existingProfile.isSshKeyAuth() && !existingProfile.getPrivateKey().isEmpty()) {
                privateKeyInput.setHint(R.string.credential_key_imported);
                privateKeyInput.setText("");
                hasExistingKey[0] = true;
            } else {
                privateKeyInput.setText(existingProfile.getPrivateKey());
            }
            keyPassphraseInput.setText(existingProfile.getKeyPassphrase());
            notesInput.setText(existingProfile.getNotes());
            // Load existing tags into currentTags list
            currentTags.addAll(existingProfile.getTags());
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

        // Initial chip display for existing tags
        refreshTagChips(tagsChipGroup, currentTags, () -> {});

        // Enter key adds tag as chip
        tagsInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                String tag = tagsInput.getText().toString().trim();
                if (!tag.isEmpty() && !currentTags.stream().anyMatch(t -> t.equalsIgnoreCase(tag))) {
                    currentTags.add(tag);
                    refreshTagChips(tagsChipGroup, currentTags, () -> {});
                }
                tagsInput.setText("");
                return true;
            }
            return false;
        });

        // Icon click opens select tags dialog
        tagsIcon.setOnClickListener(v -> {
            showSelectTagsDialog(currentTags, () -> {
                refreshTagChips(tagsChipGroup, currentTags, () -> {});
            });
        });

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

                    // Use currentTags directly (managed by select tags dialog)
                    List<String> tags = new ArrayList<>(currentTags);

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
                        existingProfile.setTags(tags);
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
                        profile.setTags(tags);
                        credentialManager.addProfile(profile);
                    }

                    Toast.makeText(getContext(), R.string.credential_saved, Toast.LENGTH_SHORT).show();
                    refreshList();
                })
                .setNegativeButton(R.string.credential_cancel, null)
                .show();
    }

    // ─── Tag Selection Dialog ────────────────────────────────────────────

    /**
     * Show the select tags dialog. Allows the user to see all existing tags,
     * create new custom tags, and select which tags apply to the current profile.
     *
     * @param currentTags mutable list of tags for the current profile (modified in place)
     * @param onUpdated   callback invoked when tags are changed, so the summary can refresh
     */
    private void showSelectTagsDialog(List<String> currentTags, Runnable onUpdated) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_select_tags, null);

        EditText tagInput = dialogView.findViewById(R.id.select_tags_input);
        ChipGroup allTagsGroup = dialogView.findViewById(R.id.select_tags_chip_group);
        ChipGroup selectedGroup = dialogView.findViewById(R.id.select_tags_selected_group);

        // Build the "all tags" chip list (checkable)
        List<String> allTags = credentialManager.getAllTags();
        // Merge currentTags that may not be in allTags yet (e.g. newly typed tags)
        for (String t : currentTags) {
            if (!allTags.contains(t)) {
                allTags.add(t);
            }
        }
        java.util.Collections.sort(allTags);

        for (String tag : allTags) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setChecked(currentTags.contains(tag));
            applyChipCheckedTextColor(chip);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!currentTags.contains(tag)) {
                        currentTags.add(tag);
                    }
                } else {
                    currentTags.remove(tag);
                }
                refreshSelectedChips(selectedGroup, currentTags);
                onUpdated.run();
            });
            // Long press to delete tag from all profiles
            chip.setOnLongClickListener(v -> {
                showDeleteTagConfirmDialog(tag, allTagsGroup, selectedGroup, currentTags, onUpdated, chip);
                return true;
            });
            allTagsGroup.addView(chip);
        }

        // Show initially selected tags
        refreshSelectedChips(selectedGroup, currentTags);

        // Add new tag on Enter
        tagInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                addNewTag(tagInput, allTagsGroup, selectedGroup, currentTags, onUpdated);
                return true;
            }
            return false;
        });

        // Add new tag on comma
        tagInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String text = s.toString();
                if (text.contains(",") || text.contains("，")) {
                    addNewTag(tagInput, allTagsGroup, selectedGroup, currentTags, onUpdated);
                }
            }
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.select_tags_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Add a new tag from the input field. Creates a checkable chip in the all-tags group,
     * auto-checks it, and adds it to the current profile's tags.
     */
    private void addNewTag(EditText tagInput, ChipGroup allTagsGroup, ChipGroup selectedGroup,
                            List<String> currentTags, Runnable onUpdated) {
        String raw = tagInput.getText().toString();
        String[] parts = raw.split("[,，]");
        for (String part : parts) {
            String tag = part.trim();
            if (tag.isEmpty()) continue;
            // Check if chip already exists
            boolean exists = false;
            for (int i = 0; i < allTagsGroup.getChildCount(); i++) {
                View child = allTagsGroup.getChildAt(i);
                if (child instanceof Chip && tag.equalsIgnoreCase(((Chip) child).getText().toString())) {
                    Chip existingChip = (Chip) child;
                    if (!existingChip.isChecked()) {
                        existingChip.setChecked(true);
                    }
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                // Add new checkable chip to all-tags group
                Chip chip = new Chip(requireContext());
                chip.setText(tag);
                chip.setCheckable(true);
                chip.setChecked(true);
                applyChipCheckedTextColor(chip);
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        if (!currentTags.contains(tag)) {
                            currentTags.add(tag);
                        }
                    } else {
                        currentTags.remove(tag);
                    }
                    refreshSelectedChips(selectedGroup, currentTags);
                    onUpdated.run();
                });
                // Long press to delete tag
                chip.setOnLongClickListener(v -> {
                    showDeleteTagConfirmDialog(tag, allTagsGroup, selectedGroup, currentTags, onUpdated, chip);
                    return true;
                });
                allTagsGroup.addView(chip);
            }
            if (!currentTags.contains(tag)) {
                currentTags.add(tag);
            }
        }
        tagInput.setText("");
        refreshSelectedChips(selectedGroup, currentTags);
        onUpdated.run();
    }

    /**
     * Refresh the "selected tags" chip group to reflect currentTags.
     */
    private void refreshSelectedChips(ChipGroup selectedGroup, List<String> currentTags) {
        selectedGroup.removeAllViews();
        for (String tag : currentTags) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag);
            chip.setTextSize(12f);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setCloseIconVisible(false);
            selectedGroup.addView(chip);
        }
    }

    /**
     * Show a confirmation dialog to delete a tag from all profiles.
     */
    private void showDeleteTagConfirmDialog(String tag, ChipGroup allTagsGroup,
                                             ChipGroup selectedGroup, List<String> currentTags,
                                             Runnable onUpdated, Chip chipToRemove) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_tag_title)
                .setMessage(getString(R.string.delete_tag_confirm, tag))
                .setPositiveButton(R.string.credential_delete, (dialog, which) -> {
                    // Remove tag from all profiles via CredentialManager
                    credentialManager.removeTagFromAllProfiles(tag);
                    // Remove from current profile's tags
                    currentTags.remove(tag);
                    // Remove the chip from the all-tags group
                    allTagsGroup.removeView(chipToRemove);
                    // Refresh selected chips display
                    refreshSelectedChips(selectedGroup, currentTags);
                    onUpdated.run();
                    Toast.makeText(getContext(),
                            getString(R.string.delete_tag_deleted, tag),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.credential_cancel, null)
                .show();
    }

    /**
     * Set checked text color to follow the theme's colorPrimary.
     */
    private void applyChipCheckedTextColor(Chip chip) {
        int primary = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorPrimary);
        int defaultColor = chip.getCurrentTextColor();
        ColorStateList textColors = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked}
                },
                new int[]{primary, defaultColor}
        );
        chip.setTextColor(textColors);
    }

    /**
     * Refresh the tag chips displayed inside the tags input box.
     * Each chip is clickable — tapping it removes the tag from currentTags.
     */
    private void refreshTagChips(ChipGroup chipGroup, List<String> currentTags, Runnable onUpdated) {
        chipGroup.removeAllViews();
        for (String tag : currentTags) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag);
            chip.setTextSize(12f);
            chip.setClickable(true);
            chip.setCheckable(false);
            chip.setCloseIconVisible(false);
            applyChipCheckedTextColor(chip);
            chip.setOnClickListener(v -> {
                currentTags.remove(tag);
                refreshTagChips(chipGroup, currentTags, onUpdated);
                onUpdated.run();
            });
            chipGroup.addView(chip);
        }
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

            // Display tags as chips
            holder.tagsGroup.removeAllViews();
            List<String> tags = profile.getTags();
            if (!tags.isEmpty()) {
                holder.tagsGroup.setVisibility(View.VISIBLE);
                for (String tag : tags) {
                    Chip chip = new Chip(holder.itemView.getContext());
                    chip.setText(tag);
                    chip.setTextSize(11f);
                    chip.setClickable(false);
                    chip.setCheckable(false);
                    chip.setCloseIconVisible(false);
                    holder.tagsGroup.addView(chip);
                }
            } else {
                holder.tagsGroup.setVisibility(View.GONE);
            }

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
            ChipGroup tagsGroup;
            ImageView authTypeIcon;
            ImageButton editButton;
            ImageButton deleteButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                activeRadio = itemView.findViewById(R.id.credential_active_radio);
                nameText = itemView.findViewById(R.id.credential_name);
                detailsText = itemView.findViewById(R.id.credential_details);
                tagsGroup = itemView.findViewById(R.id.credential_tags_group);
                authTypeIcon = itemView.findViewById(R.id.credential_auth_type_icon);
                editButton = itemView.findViewById(R.id.credential_edit_button);
                deleteButton = itemView.findViewById(R.id.credential_delete_button);
            }
        }
    }
}