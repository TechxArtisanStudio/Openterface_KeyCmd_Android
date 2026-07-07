package com.openterface.keymod.fragments;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.AutoCompleteTextView;
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
import androidx.appcompat.app.AlertDialog;
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
import com.openterface.terminal.SshKeyGenerator;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings tab for managing SSH credential profiles.
 */
public class CredentialSettingsFragment extends Fragment {

    private static final int REQUEST_CODE_IMPORT_KEY = 1001;
    public static final String ARG_EDIT_PROFILE_ID = "arg_edit_profile_id";

    private SearchView searchView;
    private RecyclerView credentialList;
    private TextView emptyText;
    private MaterialButton addButton;
    private CredentialManager credentialManager;
    private CredentialAdapter adapter;
    private SensitivePageShield shield;
    private List<CredentialProfile> allProfiles = new ArrayList<>();
    private boolean autoEditConsumed = false;

    // Bridging fields for import key data loss fix
    private String[] importKeyDataRef;
    private String[] importPassphraseDataRef;
    private TextView importKeyStatusRef;

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
        // Auto-open edit dialog if launched with a profile ID (e.g., from terminal device info)
        if (!autoEditConsumed && getArguments() != null) {
            String editId = getArguments().getString(ARG_EDIT_PROFILE_ID);
            if (editId != null) {
                autoEditConsumed = true;
                CredentialProfile target = credentialManager.getProfile(editId);
                if (target != null) {
                    // Post to ensure list is rendered before showing dialog
                    credentialList.post(() -> showAddEditDialog(target));
                }
            }
        }
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT_KEY && resultCode == android.app.Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    reader.close();
                    String keyContent = stringBuilder.toString().trim();

                    // Fix: Use bridging fields to pass imported key data back to the dialog
                    if (importKeyDataRef != null && importKeyStatusRef != null) {
                        importKeyDataRef[0] = keyContent;
                        importKeyStatusRef.setText(R.string.credential_key_imported);
                    }
                    Toast.makeText(requireContext(), R.string.credential_key_imported, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(requireContext(), R.string.credential_key_import_failed, Toast.LENGTH_SHORT).show();
                }
            }
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
        EditText notesInput = dialogView.findViewById(R.id.credential_notes_input);
        Spinner authTypeSpinner = dialogView.findViewById(R.id.credential_auth_type_spinner);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.credential_password_layout);
        LinearLayout tagsContainer = dialogView.findViewById(R.id.credential_tags_container);
        ImageView tagsIcon = dialogView.findViewById(R.id.credential_tags_icon);
        ChipGroup tagsChipGroup = dialogView.findViewById(R.id.credential_tags_chip_group);
        EditText tagsInput = dialogView.findViewById(R.id.credential_tags_input);

        // Key section
        View keyRow = dialogView.findViewById(R.id.credential_key_row);
        TextView keyStatus = dialogView.findViewById(R.id.credential_key_status);
        ImageView keyIcon = dialogView.findViewById(R.id.credential_key_icon);

        // Track key data internally
        final String[] privateKeyData = {""};
        final String[] publicKeyData = {""};
        final String[] keyPassphraseData = {""};

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
        // Preserve key data when toggling between auth types so switching
        // SSH Key → Password → SSH Key restores the original key.
        final String[] preservedKeyData = {""};
        final String[] preservedPassphraseData = {""};
        final String[] preservedKeyStatusText = {""};

        authTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    // Leaving SSH Key → save key data before hiding
                    if (CredentialProfile.AUTH_TYPE_SSH_KEY.equals(selectedAuthType[0])
                            && !privateKeyData[0].isEmpty()) {
                        preservedKeyData[0] = privateKeyData[0];
                        preservedPassphraseData[0] = keyPassphraseData[0];
                        preservedKeyStatusText[0] = keyStatus.getText().toString();
                    }
                    selectedAuthType[0] = CredentialProfile.AUTH_TYPE_PASSWORD;
                    passwordLayout.setVisibility(View.VISIBLE);
                    keyRow.setVisibility(View.GONE);
                } else {
                    selectedAuthType[0] = CredentialProfile.AUTH_TYPE_SSH_KEY;
                    passwordLayout.setVisibility(View.GONE);
                    keyRow.setVisibility(View.VISIBLE);
                    // Restore preserved key data when switching back to SSH Key
                    if (!preservedKeyData[0].isEmpty()) {
                        privateKeyData[0] = preservedKeyData[0];
                        keyPassphraseData[0] = preservedPassphraseData[0];
                        keyStatus.setText(preservedKeyStatusText[0]);
                    }
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
            // Always load key data from profile so that switching auth type
            // and back still has the key available (preserved in preservedKeyData).
            if (!existingProfile.getPrivateKey().isEmpty()) {
                privateKeyData[0] = existingProfile.getPrivateKey();
                publicKeyData[0] = existingProfile.getPublicKey();
                keyPassphraseData[0] = existingProfile.getKeyPassphrase();
                keyStatus.setText(R.string.credential_key_imported);
                hasExistingKey[0] = true;
            }
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

        // Key icon click shows popup menu
        keyIcon.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(requireContext(), v);
            popup.getMenu().add(0, 1, 0, R.string.credential_key_paste);
            popup.getMenu().add(0, 2, 1, R.string.credential_key_import_file);
            popup.getMenu().add(0, 3, 2, R.string.credential_key_generate);
            // Show Edit option only when key data is already set
            if (!privateKeyData[0].isEmpty()) {
                popup.getMenu().add(0, 4, 3, R.string.credential_edit_key);
            }
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: // Paste
                        showPasteKeyDialog(privateKeyData, keyPassphraseData, keyStatus);
                        break;
                    case 2: // Import from file
                        // Set bridging fields so onActivityResult can update the key data
                        importKeyDataRef = privateKeyData;
                        importPassphraseDataRef = keyPassphraseData;
                        importKeyStatusRef = keyStatus;
                        importKeyFromFile();
                        break;
                    case 3: // Generate
                        showGenerateKeyDialog((keyPair, passphrase) -> {
                            privateKeyData[0] = keyPair.privateKey;
                            keyPassphraseData[0] = passphrase != null ? passphrase : "";
                            publicKeyData[0] = keyPair.publicKey;
                            keyStatus.setText(R.string.credential_key_generated);
                        });
                        break;
                    case 4: // Edit existing key
                        showEditKeyDialog(privateKeyData[0], publicKeyData[0], keyPassphraseData[0],
                                (updatedKey, updatedPassphrase) -> {
                                    privateKeyData[0] = updatedKey;
                                    keyPassphraseData[0] = updatedPassphrase;
                                    keyStatus.setText(R.string.credential_key_imported);
                                });
                        break;
                }
                return true;
            });
            popup.show();
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
                    String privateKey = privateKeyData[0];
                    String publicKey = publicKeyData[0];
                    String keyPassphrase = keyPassphraseData[0];
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
                        // Key data is always loaded from profile (regardless of auth type)
                        // and preserved when toggling auth types, so always write it back.
                        existingProfile.setPrivateKey(privateKey);
                        existingProfile.setPublicKey(publicKey);
                        existingProfile.setKeyPassphrase(keyPassphrase);
                        existingProfile.setAuthType(authType);
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
                        profile.setPublicKey(publicKey);
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

    // ─── SSH Key Generation Dialogs ─────────────────────────────────────

    /**
     * Show dialog for pasting a private key.
     */
    private void showPasteKeyDialog(String[] privateKeyData, String[] keyPassphraseData, TextView keyStatus) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_paste_key, null);

        EditText privateKeyInput = dialogView.findViewById(R.id.paste_key_input);
        EditText passphraseInput = dialogView.findViewById(R.id.paste_passphrase_input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.credential_key_paste)
                .setView(dialogView)
                .setPositiveButton(R.string.credential_save, (dialog, which) -> {
                    String key = privateKeyInput.getText().toString().trim();
                    String passphrase = passphraseInput.getText().toString();
                    if (!key.isEmpty()) {
                        privateKeyData[0] = key;
                        keyPassphraseData[0] = passphrase;
                        keyStatus.setText(R.string.credential_key_set);
                        Toast.makeText(requireContext(), R.string.credential_key_set, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.credential_cancel, null)
                .show();
    }

    /**
     * Import key from file using file picker.
     */
    private void importKeyFromFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE_IMPORT_KEY);
    }

    /**
     * Show dialog for generating SSH key pair.
     * After generation the dialog switches to "Edit Key" mode so the user
     * can review and adjust key properties before saving.
     *
     * @param onGenerated callback receiving (keyPairResult, passphrase)
     */
    private void showGenerateKeyDialog(java.util.function.BiConsumer<SshKeyGenerator.KeyPairResult, String> onGenerated) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_generate_ssh_key, null);

        // ── Generate section views ──
        View generateSection = dialogView.findViewById(R.id.key_generate_section);
        EditText nameInput = dialogView.findViewById(R.id.key_name_input);
        AutoCompleteTextView algorithmDropdown = dialogView.findViewById(R.id.key_algorithm_dropdown);
        EditText passphraseInput = dialogView.findViewById(R.id.key_passphrase_input);
        TextInputLayout roundsLayout = dialogView.findViewById(R.id.key_rounds_layout);
        EditText roundsInput = dialogView.findViewById(R.id.key_rounds_input);
        // Save-passphrase checkbox has no effect yet (passphrase is always stored); hide it.
        View savePassphraseCheckbox = dialogView.findViewById(R.id.key_save_passphrase_checkbox);
        if (savePassphraseCheckbox != null) savePassphraseCheckbox.setVisibility(View.GONE);

        // ── Edit section views ──
        View editSection = dialogView.findViewById(R.id.key_edit_section);
        EditText editNameInput = dialogView.findViewById(R.id.key_edit_name_input);
        EditText editAlgorithmInput = dialogView.findViewById(R.id.key_edit_algorithm_input);
        ImageView editAlgorithmMenu = dialogView.findViewById(R.id.key_edit_algorithm_menu);
        TextView privateKeyText = dialogView.findViewById(R.id.key_edit_private_key_text);
        TextView publicKeyText = dialogView.findViewById(R.id.key_edit_public_key_text);
        ImageView publicKeyMenu = dialogView.findViewById(R.id.key_edit_public_key_menu);
        EditText editPassphraseInput = dialogView.findViewById(R.id.key_edit_passphrase_input);

        // Setup algorithm dropdown
        String[] algorithms = {
                getString(R.string.credential_key_ed25519),
                getString(R.string.credential_key_rsa_4096),
                getString(R.string.credential_key_rsa_2048)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, algorithms);
        algorithmDropdown.setAdapter(adapter);
        algorithmDropdown.setText(algorithms[0], false);

        // Show rounds field only when passphrase is not empty
        passphraseInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                roundsLayout.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });

        // Mutable handler reference — null keeps the dialog open, non-null saves and dismisses.
        final Runnable[] saveHandler = {null};

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.credential_generate_key)
                .setView(dialogView)
                .setPositiveButton(R.string.credential_generate, null) // Generate: don't auto-close
                .setNegativeButton(R.string.credential_cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (saveHandler[0] == null) {
                    // ── Generate phase ──
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.credential_profile_name_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String passphrase = passphraseInput.getText().toString();
                    final String selectedAlgo = algorithmDropdown.getText().toString();
                    // Parse rounds (default 16 if empty or invalid)
                    int parsedRounds = 16;
                    String roundsStr = roundsInput.getText().toString().trim();
                    if (!roundsStr.isEmpty()) {
                        try { parsedRounds = Integer.parseInt(roundsStr); } catch (NumberFormatException ignored) {}
                    }
                    final int rounds = parsedRounds;

                    ProgressDialog progress = new ProgressDialog(requireContext());
                    progress.setMessage(getString(R.string.credential_key_generating));
                    progress.setCancelable(false);
                    progress.show();

                    new Thread(() -> {
                        try {
                            SshKeyGenerator.KeyPairResult result;
                            // Pass passphrase to generator for encryption
                            if (selectedAlgo.equals(algorithms[1])) {
                                result = SshKeyGenerator.generateRSA(4096, name, passphrase);
                            } else if (selectedAlgo.equals(algorithms[2])) {
                                result = SshKeyGenerator.generateRSA(2048, name, passphrase);
                            } else {
                                result = SshKeyGenerator.generateEd25519(name, passphrase, rounds);
                            }

                            requireActivity().runOnUiThread(() -> {
                                progress.dismiss();

                                // Switch to edit mode
                                generateSection.setVisibility(View.GONE);
                                editSection.setVisibility(View.VISIBLE);
                                // Hide Name field in Edit phase — name is only a comment during generation
                                ((View) editNameInput.getParent()).setVisibility(View.GONE);
                                editAlgorithmInput.setText(selectedAlgo);
                                publicKeyText.setText(result.publicKey);
                                editPassphraseInput.setText(passphrase);

                                // Public key three-dot menu: Copy / Reveal
                                final boolean[] publicKeyExpanded = {false};
                                publicKeyMenu.setOnClickListener(view -> {
                                    androidx.appcompat.widget.PopupMenu pubKeyMenu = new androidx.appcompat.widget.PopupMenu(requireContext(), view);
                                    pubKeyMenu.getMenu().add(0, 1, 0, R.string.credential_key_copy);
                                    pubKeyMenu.getMenu().add(0, 2, 1, publicKeyExpanded[0]
                                            ? R.string.credential_key_collapse : R.string.credential_key_reveal);
                                    pubKeyMenu.setOnMenuItemClickListener(item -> {
                                        if (item.getItemId() == 1) {
                                            // Copy public key to clipboard
                                            ClipboardManager clipboard = (ClipboardManager) requireContext()
                                                    .getSystemService(Context.CLIPBOARD_SERVICE);
                                            clipboard.setPrimaryClip(
                                                    ClipData.newPlainText("SSH Public Key", result.publicKey));
                                            Toast.makeText(requireContext(),
                                                    R.string.credential_public_key_copied, Toast.LENGTH_SHORT).show();
                                        } else if (item.getItemId() == 2) {
                                            // Toggle public key expanded state
                                            publicKeyExpanded[0] = !publicKeyExpanded[0];
                                            if (publicKeyExpanded[0]) {
                                                publicKeyText.setMaxLines(Integer.MAX_VALUE);
                                                publicKeyText.setEllipsize(null);
                                            } else {
                                                publicKeyText.setMaxLines(1);
                                                publicKeyText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                                            }
                                        }
                                        return true;
                                    });
                                    pubKeyMenu.show();
                                });

                                // Key Info three-dot menu: Copy / Reveal private key (inline multi-line)
                                final boolean[] privateKeyVisible = {false};
                                editAlgorithmMenu.setOnClickListener(view -> {
                                    androidx.appcompat.widget.PopupMenu algoMenu = new androidx.appcompat.widget.PopupMenu(requireContext(), view);
                                    algoMenu.getMenu().add(0, 1, 0, R.string.credential_key_copy);
                                    algoMenu.getMenu().add(0, 2, 1, privateKeyVisible[0]
                                            ? R.string.credential_key_hide : R.string.credential_key_reveal);
                                    algoMenu.setOnMenuItemClickListener(item -> {
                                        if (item.getItemId() == 1) {
                                            // Copy encrypted private key to clipboard
                                            ClipboardManager clipboard = (ClipboardManager) requireContext()
                                                    .getSystemService(Context.CLIPBOARD_SERVICE);
                                            clipboard.setPrimaryClip(
                                                    ClipData.newPlainText("SSH Private Key", result.privateKey));
                                            Toast.makeText(requireContext(), R.string.credential_private_key_copied,
                                                    Toast.LENGTH_SHORT).show();
                                        } else if (item.getItemId() == 2) {
                                            // Toggle private key inline multi-line display
                                            privateKeyVisible[0] = !privateKeyVisible[0];
                                            if (privateKeyVisible[0]) {
                                                privateKeyText.setText(result.privateKey);
                                                privateKeyText.setVisibility(View.VISIBLE);
                                            } else {
                                                privateKeyText.setVisibility(View.GONE);
                                            }
                                        }
                                        return true;
                                    });
                                    algoMenu.show();
                                });

                                dialog.setTitle(R.string.credential_edit_key);
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                        .setText(getString(R.string.credential_save));
                                // Read passphrase from Edit input at save time
                                saveHandler[0] = () -> {
                                    String editedPassphrase = editPassphraseInput.getText().toString();
                                    onGenerated.accept(result, editedPassphrase);
                                };
                            });
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() -> {
                                progress.dismiss();
                                Toast.makeText(requireContext(),
                                        "Failed to generate key: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }
                    }, "KeyGenerator").start();
                } else {
                    // ── Save phase ──
                    saveHandler[0].run();
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }

    /**
     * Show the Edit Key dialog for an existing SSH key.
     * Allows the user to view the public key, view the private key, and modify the passphrase.
     *
     * @param privateKey  the existing private key PEM content
     * @param publicKey   the stored public key (may be empty for keys created before this field was added)
     * @param passphrase  the existing passphrase (may be empty)
     * @param onSaved     callback receiving (privateKey, updatedPassphrase)
     */
    private void showEditKeyDialog(String privateKey, String publicKey, String passphrase,
                                   java.util.function.BiConsumer<String, String> onSaved) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_generate_ssh_key, null);

        // ── Edit section views ──
        View editSection = dialogView.findViewById(R.id.key_edit_section);
        View generateSection = dialogView.findViewById(R.id.key_generate_section);
        EditText editAlgorithmInput = dialogView.findViewById(R.id.key_edit_algorithm_input);
        ImageView editAlgorithmMenu = dialogView.findViewById(R.id.key_edit_algorithm_menu);
        TextView privateKeyText = dialogView.findViewById(R.id.key_edit_private_key_text);
        TextView publicKeyText = dialogView.findViewById(R.id.key_edit_public_key_text);
        ImageView publicKeyMenu = dialogView.findViewById(R.id.key_edit_public_key_menu);
        EditText editPassphraseInput = dialogView.findViewById(R.id.key_edit_passphrase_input);

        // Jump straight to edit mode (skip generate phase)
        generateSection.setVisibility(View.GONE);
        editSection.setVisibility(View.VISIBLE);
        // Hide Name field in Edit phase
        View nameLayout = dialogView.findViewById(R.id.key_edit_name_input);
        if (nameLayout != null && nameLayout.getParent() != null) {
            ((View) nameLayout.getParent()).setVisibility(View.GONE);
        }

        // Detect algorithm from stored public key (most reliable) or private key binary content.
        String algoLabel = detectKeyAlgorithm(publicKey, privateKey);
        editAlgorithmInput.setText(algoLabel);
        editAlgorithmInput.setEnabled(false);

        // Set passphrase
        editPassphraseInput.setText(passphrase != null ? passphrase : "");

        // ── Public key display ──
        final String displayPublicKey = publicKey != null ? publicKey : "";
        if (!displayPublicKey.isEmpty()) {
            publicKeyText.setText(displayPublicKey);
            publicKeyText.setMaxLines(1);
            publicKeyText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        } else {
            publicKeyText.setText(R.string.credential_public_key_not_available);
        }

        // Public key three-dot menu: Copy / Reveal
        final boolean[] publicKeyExpanded = {false};
        publicKeyMenu.setOnClickListener(view -> {
            androidx.appcompat.widget.PopupMenu pubKeyMenu =
                    new androidx.appcompat.widget.PopupMenu(requireContext(), view);
            pubKeyMenu.getMenu().add(0, 1, 0, R.string.credential_key_copy);
            pubKeyMenu.getMenu().add(0, 2, 1, publicKeyExpanded[0]
                    ? R.string.credential_key_collapse : R.string.credential_key_reveal);
            pubKeyMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    if (!displayPublicKey.isEmpty()) {
                        ClipboardManager clipboard = (ClipboardManager) requireContext()
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(
                                ClipData.newPlainText("SSH Public Key", displayPublicKey));
                        Toast.makeText(requireContext(),
                                R.string.credential_public_key_copied, Toast.LENGTH_SHORT).show();
                    }
                } else if (item.getItemId() == 2) {
                    publicKeyExpanded[0] = !publicKeyExpanded[0];
                    if (publicKeyExpanded[0]) {
                        publicKeyText.setMaxLines(Integer.MAX_VALUE);
                        publicKeyText.setEllipsize(null);
                    } else {
                        publicKeyText.setMaxLines(1);
                        publicKeyText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    }
                }
                return true;
            });
            pubKeyMenu.show();
        });

        // ── Private key menu: Copy / Reveal ──
        final boolean[] privateKeyVisible = {false};
        editAlgorithmMenu.setOnClickListener(view -> {
            androidx.appcompat.widget.PopupMenu algoMenu =
                    new androidx.appcompat.widget.PopupMenu(requireContext(), view);
            algoMenu.getMenu().add(0, 1, 0, R.string.credential_key_copy);
            algoMenu.getMenu().add(0, 2, 1, privateKeyVisible[0]
                    ? R.string.credential_key_hide : R.string.credential_key_reveal);
            algoMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    ClipboardManager clipboard = (ClipboardManager) requireContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(
                            ClipData.newPlainText("SSH Private Key", privateKey));
                    Toast.makeText(requireContext(),
                            R.string.credential_private_key_copied, Toast.LENGTH_SHORT).show();
                } else if (item.getItemId() == 2) {
                    privateKeyVisible[0] = !privateKeyVisible[0];
                    if (privateKeyVisible[0]) {
                        privateKeyText.setText(privateKey);
                        privateKeyText.setVisibility(View.VISIBLE);
                    } else {
                        privateKeyText.setVisibility(View.GONE);
                    }
                }
                return true;
            });
            algoMenu.show();
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.credential_edit_key)
                .setView(dialogView)
                .setPositiveButton(R.string.credential_save, (dialog, which) -> {
                    String updatedPassphrase = editPassphraseInput.getText().toString();
                    onSaved.accept(privateKey, updatedPassphrase);
                })
                .setNegativeButton(R.string.credential_cancel, null)
                .show();
    }

    // ─── Key Algorithm Detection ─────────────────────────────────────────

    /**
     * Detect the key algorithm label ("ED25519" or "RSA").
     * Uses the stored public key first (most reliable — plain text like "ssh-ed25519 ...").
     * Falls back to parsing the OpenSSH binary format of the private key.
     */
    private static String detectKeyAlgorithm(String publicKey, String privateKey) {
        // 1. Use public key if available (definitive — always a readable string)
        if (publicKey != null && !publicKey.isEmpty()) {
            if (publicKey.startsWith("ssh-ed25519")) return "ED25519";
            if (publicKey.startsWith("ssh-rsa")) return "RSA";
        }

        // 2. Traditional RSA PEM header
        if (privateKey != null && privateKey.contains("RSA PRIVATE KEY")) {
            return "RSA";
        }

        // 3. OpenSSH format: decode base64 and read key type from binary content.
        // The key type bytes are split across base64 encoding boundaries, so we
        // must decode first — simple string contains() won't work.
        if (privateKey != null && privateKey.contains("OPENSSH PRIVATE KEY")) {
            try {
                String b64 = privateKey
                        .replace("-----BEGIN OPENSSH PRIVATE KEY-----", "")
                        .replace("-----END OPENSSH PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
                byte[] decoded = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                // OpenSSH binary layout:
                //   0-14:  "openssh-key-v1\0" (15 bytes)
                //   15-18: cipher name length (uint32)
                //   19..:  cipher name ("none" = 4 bytes)
                //   next:  kdf name length (uint32) + kdf name
                //   next:  key type length (uint32) + key type string
                int off = 15;
                if (decoded.length < off + 4) return "RSA";
                int cipherLen = readUint32(decoded, off);
                off += 4 + cipherLen;
                if (decoded.length < off + 4) return "RSA";
                int kdfLen = readUint32(decoded, off);
                off += 4 + kdfLen;
                if (decoded.length < off + 4) return "RSA";
                int typeLen = readUint32(decoded, off);
                off += 4;
                if (off + typeLen > decoded.length) return "RSA";
                String keyType = new String(decoded, off, typeLen, "UTF-8");
                if ("ssh-ed25519".equals(keyType)) return "ED25519";
                if ("ssh-rsa".equals(keyType)) return "RSA";
            } catch (Exception e) {
                // Fall through
            }
        }

        return "RSA";
    }

    private static int readUint32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
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