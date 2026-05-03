package com.openterface.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openterface.keymod.ConnectionManager;
import com.google.android.material.tabs.TabLayout;

import com.openterface.keymod.MainActivity;
import com.openterface.keymod.MyShortcutsReorderAdapter;
import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutSectionPickAdapter;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.ShortcutProfileManager.ShortcutProfile;
import com.openterface.keymod.ShortcutProfileManager.ProfileChangeListener;
import com.openterface.keymod.util.KeyParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Shortcut Hub Fragment - Profile management for app-specific shortcuts
 * Phase 3: Shortcut Hub
 */
public class ShortcutHubFragment extends Fragment implements ProfileChangeListener {

    private static final String TAG = "ShortcutHubFragment";

    private ShortcutProfileManager profileManager;
    private Vibrator vibrator;
    private ConnectionManager connectionManager;

    // UI Components - Profile list panel
    private LinearLayout panelProfilesList;
    private RecyclerView profilesRecyclerView;
    private TextView emptyTextView;
    private TextView activeProfileText;
    private Button createProfileButton;
    private Button importButton;
    private Button exportButton;

    // UI Components - Shortcuts detail panel
    private LinearLayout panelShortcutsDetail;
    private Button backButton;
    private Button addShortcutButton;
    private TextView detailProfileName;
    private TextView detailProfileDescription;
    private TabLayout hubDetailTabs;
    private RecyclerView browseShortcutsRecyclerView;
    private RecyclerView myShortcutsRecyclerView;
    private MyShortcutsReorderAdapter myShortcutsReorderAdapter;
    private ItemTouchHelper myShortcutsReorderTouchHelper;
    private TextView emptyMyShortcuts;

    private static final String TAB_MY = "my";
    /** Browsing a named category (not Favorites). */
    private static final String TAB_BROWSE = "browse";
    private String currentTab = TAB_MY;  // Default to favorites
    private String currentCategoryId = null;  // Current category when in All tab

    private boolean suppressHubTabSelection;

    private ProfilesRecyclerAdapter profilesRecyclerAdapter;
    private ShortcutSectionPickAdapter browsePickAdapter;
    private List<ShortcutProfile> profilesList;
    private List<ShortcutProfileManager.Shortcut> myShortcutsList = new ArrayList<>();
    private ShortcutProfile activeProfile;
    private ShortcutProfile selectedProfile;  // profile whose shortcuts are shown

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shortcut_hub, container, false);

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        profileManager = new ShortcutProfileManager(requireContext());
        profileManager.setListener(this);

        if (getActivity() instanceof MainActivity) {
            connectionManager = ((MainActivity) getActivity()).getConnectionManager();
        }

        initializeViews(view);
        loadProfiles();
        setupListeners();

        return view;
    }

    private final MainActivity.OnTargetOsChangeListener osChangeListener = os -> {
        if (browsePickAdapter != null) {
            browsePickAdapter.notifyDataSetChanged();
        }
        if (myShortcutsReorderAdapter != null) {
            myShortcutsReorderAdapter.notifyDataSetChanged();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).addOsChangeListener(osChangeListener);
        }
        if (selectedProfile != null && panelShortcutsDetail.getVisibility() == View.VISIBLE) {
            profileManager.reloadProfilesFromPreferences();
            loadProfiles();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).removeOsChangeListener(osChangeListener);
        }
    }

    private void initializeViews(View view) {
        // Profile list panel
        panelProfilesList = view.findViewById(R.id.panel_profiles_list);
        profilesRecyclerView = view.findViewById(R.id.profiles_recycler);
        emptyTextView = view.findViewById(R.id.empty_textview);
        activeProfileText = view.findViewById(R.id.active_profile_text);
        createProfileButton = view.findViewById(R.id.create_profile_button);
        importButton = view.findViewById(R.id.import_button);
        exportButton = view.findViewById(R.id.export_button);

        // Shortcuts detail panel
        panelShortcutsDetail = view.findViewById(R.id.panel_shortcuts_detail);
        backButton = view.findViewById(R.id.back_button);
        addShortcutButton = view.findViewById(R.id.add_shortcut_button);
        detailProfileName = view.findViewById(R.id.detail_profile_name);
        detailProfileDescription = view.findViewById(R.id.detail_profile_description);
        hubDetailTabs = view.findViewById(R.id.hub_detail_tabs);
        browseShortcutsRecyclerView = view.findViewById(R.id.browse_shortcuts_recycler);
        myShortcutsRecyclerView = view.findViewById(R.id.my_shortcuts_recycler);
        emptyMyShortcuts = view.findViewById(R.id.empty_my_shortcuts);

        profilesList = new ArrayList<>();
        profilesRecyclerAdapter = new ProfilesRecyclerAdapter();
        profilesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        profilesRecyclerView.setAdapter(profilesRecyclerAdapter);

        browsePickAdapter = new ShortcutSectionPickAdapter(requireContext(), getTargetOs(), new ArrayList<>());
        browsePickAdapter.setRowInteraction(new ShortcutSectionPickAdapter.RowInteraction() {
            @Override
            public void onRowClick(@NonNull ShortcutProfileManager.Shortcut shortcut) {
                executeShortcut(shortcut);
            }

            @Override
            public void onRowLongClick(@NonNull ShortcutProfileManager.Shortcut shortcut) {
                showShortcutActionsMenu(shortcut);
            }
        });
        browsePickAdapter.setFavoriteMembershipChecker(shortcut -> {
            if (shortcut == null || shortcut.id == null) {
                return false;
            }
            for (ShortcutProfileManager.Shortcut x : myShortcutsList) {
                if (x != null && shortcut.id.equals(x.id)) {
                    return true;
                }
            }
            return false;
        });
        browsePickAdapter.setBookmarkListener(new ShortcutSectionPickAdapter.OnBookmarkActionListener() {
            @Override
            public void onAddToFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut) {
                onBrowsePickAddToFavorites(shortcut);
            }

            @Override
            public void onRemoveFromFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut) {
                onBrowsePickRemoveFromFavorites(shortcut);
            }
        });
        browseShortcutsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        browseShortcutsRecyclerView.setAdapter(browsePickAdapter);
    }

    private String getTargetOs() {
        return requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .getString("target_os", "macos");
    }

    private void onBrowsePickAddToFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut) {
        if (selectedProfile == null) {
            return;
        }
        if (!profileManager.appendCloneIfAbsent(myShortcutsList, shortcut)) {
            Toast.makeText(requireContext(), R.string.my_shortcuts_already_in_my, Toast.LENGTH_SHORT).show();
            browsePickAdapter.notifyDataSetChanged();
            return;
        }
        profileManager.updateMyShortcuts(selectedProfile.id, new ArrayList<>(myShortcutsList));
        Toast.makeText(requireContext(), R.string.my_shortcuts_added_to_my, Toast.LENGTH_SHORT).show();
        browsePickAdapter.notifyDataSetChanged();
    }

    private void onBrowsePickRemoveFromFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut) {
        if (selectedProfile == null) {
            return;
        }
        myShortcutsList.removeIf(s -> s.id != null && s.id.equals(shortcut.id));
        profileManager.updateMyShortcuts(selectedProfile.id, myShortcutsList);
        Toast.makeText(requireContext(), R.string.my_shortcuts_removed_from_my, Toast.LENGTH_SHORT).show();
        browsePickAdapter.notifyDataSetChanged();
    }

    private void loadProfiles() {
        profilesList.clear();
        profilesList.addAll(profileManager.getAllProfiles());
        
        activeProfile = profileManager.getActiveProfile();
        profilesRecyclerAdapter.setActiveProfileId(activeProfile != null ? activeProfile.id : null);
        profilesRecyclerAdapter.notifyDataSetChanged();
        updateActiveProfileDisplay();
        updateEmptyState();
        if (selectedProfile != null && panelShortcutsDetail != null
                && panelShortcutsDetail.getVisibility() == View.VISIBLE) {
            myShortcutsList = profileManager.getMyShortcuts(selectedProfile.id);
            ShortcutProfile updated = profileManager.getProfileById(selectedProfile.id);
            if (updated != null) {
                selectedProfile = updated;
                rebuildCategoryTabs(selectedProfile);
            }
            refreshShortcutsGrid();
        }
    }

    private void setupListeners() {
        // Create profile button
        createProfileButton.setOnClickListener(v -> showCreateProfileDialog());

        // Import button
        importButton.setOnClickListener(v -> {
            showImportDialog();
        });

        // Export button
        exportButton.setOnClickListener(v -> {
            if (activeProfile != null) {
                exportProfileToFile(activeProfile);
            } else {
                Toast.makeText(getContext(), "No active profile", Toast.LENGTH_SHORT).show();
            }
        });

        // Back button - return to profile list
        backButton.setOnClickListener(v -> showProfileList());

        // Add shortcut button
        addShortcutButton.setOnClickListener(v -> {
            if (selectedProfile != null) {
                showAddShortcutDialog(selectedProfile);
            }
        });

        hubDetailTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (suppressHubTabSelection) {
                    return;
                }
                int pos = tab.getPosition();
                if (pos == 0) {
                    currentTab = TAB_MY;
                    currentCategoryId = null;
                    refreshShortcutsGrid();
                } else if (selectedProfile != null && selectedProfile.categories != null) {
                    int idx = pos - 1;
                    if (idx >= 0 && idx < selectedProfile.categories.size()) {
                        currentTab = TAB_BROWSE;
                        currentCategoryId = selectedProfile.categories.get(idx).id;
                        refreshShortcutsGrid();
                    }
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void showShortcutsDetail(ShortcutProfile profile) {
        selectedProfile = profile;
        currentTab = TAB_MY;  // Default to My Shortcuts
        currentCategoryId = null;

        detailProfileName.setText(profile.name);
        detailProfileDescription.setText(profile.description);

        // Load persisted My Shortcuts for this profile
        myShortcutsList = profileManager.getMyShortcuts(profile.id);

        // Build dynamic category tabs
        rebuildCategoryTabs(profile);
        syncHubDetailTabsSelection();
        refreshShortcutsGrid();

        panelProfilesList.setVisibility(View.GONE);
        panelShortcutsDetail.setVisibility(View.VISIBLE);
    }

    private void showProfileList() {
        selectedProfile = null;
        panelShortcutsDetail.setVisibility(View.GONE);
        panelProfilesList.setVisibility(View.VISIBLE);
    }

    private void rebuildCategoryTabs(ShortcutProfileManager.ShortcutProfile profile) {
        if (hubDetailTabs == null) {
            return;
        }
        hubDetailTabs.removeAllTabs();
        hubDetailTabs.addTab(hubDetailTabs.newTab().setText(R.string.my_shortcuts_tab_favorites), false);
        if (profile.categories != null && !profile.categories.isEmpty()) {
            for (ShortcutProfileManager.ShortcutCategory cat : profile.categories) {
                hubDetailTabs.addTab(hubDetailTabs.newTab().setText(cat.name), false);
            }
        }
        syncHubDetailTabsSelection();
    }

    /**
     * Keeps {@link TabLayout} selection aligned with {@link #currentTab} / {@link #currentCategoryId}.
     */
    private void syncHubDetailTabsSelection() {
        if (hubDetailTabs == null) {
            return;
        }
        int count = hubDetailTabs.getTabCount();
        if (count == 0) {
            return;
        }
        int target = 0;
        if (TAB_MY.equals(currentTab)) {
            target = 0;
        } else if (TAB_BROWSE.equals(currentTab)
                && currentCategoryId != null
                && selectedProfile != null
                && selectedProfile.categories != null) {
            for (int i = 0; i < selectedProfile.categories.size(); i++) {
                if (currentCategoryId.equals(selectedProfile.categories.get(i).id)) {
                    target = i + 1;
                    break;
                }
            }
        }
        if (target < 0 || target >= count) {
            target = 0;
        }
        if (hubDetailTabs.getSelectedTabPosition() == target) {
            return;
        }
        TabLayout.Tab tab = hubDetailTabs.getTabAt(target);
        if (tab == null) {
            return;
        }
        suppressHubTabSelection = true;
        tab.select();
        suppressHubTabSelection = false;
    }

    private int dpToPx(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
    }

    private void refreshShortcutsGrid() {
        if (selectedProfile == null) return;

        boolean hasCategories = selectedProfile.categories != null && !selectedProfile.categories.isEmpty();

        List<ShortcutProfileManager.Shortcut> toShow = new ArrayList<>();

        // Priority 1: Show category shortcuts (not when Favorites tab is active)
        if (currentCategoryId != null && hasCategories && !TAB_MY.equals(currentTab)) {
            detachMyShortcutsReorderTouchHelper();
            myShortcutsRecyclerView.setVisibility(View.GONE);
            for (ShortcutProfileManager.ShortcutCategory cat : selectedProfile.categories) {
                if (cat.id.equals(currentCategoryId)) {
                    toShow = new ArrayList<>(cat.shortcuts);
                    break;
                }
            }
            browseShortcutsRecyclerView.setVisibility(View.VISIBLE);
            emptyMyShortcuts.setVisibility(View.GONE);
            sortShortcutsForDisplay(toShow);
            browsePickAdapter.setItems(toShow);
            browsePickAdapter.notifyDataSetChanged();
            return;
        }
        // Priority 2: Show My Shortcuts favorites (drag-reorder list; top strip uses same order)
        if (TAB_MY.equals(currentTab)) {
            myShortcutsList.clear();
            myShortcutsList.addAll(profileManager.getMyShortcuts(selectedProfile.id));
            sortShortcutsForDisplay(myShortcutsList);
            toShow = myShortcutsList;
            if (toShow.isEmpty()) {
                detachMyShortcutsReorderTouchHelper();
                myShortcutsRecyclerView.setVisibility(View.GONE);
                browseShortcutsRecyclerView.setVisibility(View.GONE);
                emptyMyShortcuts.setVisibility(View.VISIBLE);
                return;
            }
            browseShortcutsRecyclerView.setVisibility(View.GONE);
            emptyMyShortcuts.setVisibility(View.GONE);
            myShortcutsRecyclerView.setVisibility(View.VISIBLE);
            bindMyShortcutsReorderRecycler();
            return;
        }
        // Priority 3: Show all flat shortcuts (for profiles without categories)
        detachMyShortcutsReorderTouchHelper();
        myShortcutsRecyclerView.setVisibility(View.GONE);
        if (!hasCategories) {
            toShow = selectedProfile.shortcuts != null ? selectedProfile.shortcuts : new ArrayList<>();
        }

        browseShortcutsRecyclerView.setVisibility(View.VISIBLE);
        emptyMyShortcuts.setVisibility(View.GONE);
        sortShortcutsForDisplay(toShow);
        browsePickAdapter.setItems(toShow);
        browsePickAdapter.notifyDataSetChanged();
    }

    private void detachMyShortcutsReorderTouchHelper() {
        if (myShortcutsReorderTouchHelper != null) {
            myShortcutsReorderTouchHelper.attachToRecyclerView(null);
            myShortcutsReorderTouchHelper = null;
        }
    }

    private void bindMyShortcutsReorderRecycler() {
        if (selectedProfile == null || myShortcutsRecyclerView == null) {
            return;
        }
        String targetOs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .getString("target_os", "macos");
        List<ShortcutProfileManager.Shortcut> ordered = new ArrayList<>(
                profileManager.getOrderedShortcutsForTopStrip(selectedProfile.id));

        if (myShortcutsReorderAdapter == null) {
            myShortcutsReorderAdapter = new MyShortcutsReorderAdapter(requireContext(), targetOs, ordered);
            myShortcutsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            myShortcutsRecyclerView.setAdapter(myShortcutsReorderAdapter);
            myShortcutsReorderAdapter.setRowInteraction(new MyShortcutsReorderAdapter.RowInteraction() {
                @Override
                public void onRowClick(ShortcutProfileManager.Shortcut shortcut) {
                    executeShortcut(shortcut);
                }

                @Override
                public void onRowLongClick(ShortcutProfileManager.Shortcut shortcut) {
                    showShortcutActionsMenu(shortcut);
                }
            });
        } else {
            myShortcutsReorderAdapter.replaceItems(ordered);
        }

        detachMyShortcutsReorderTouchHelper();

        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                MyShortcutsReorderAdapter adapter = (MyShortcutsReorderAdapter) recyclerView.getAdapter();
                if (adapter == null || selectedProfile == null) {
                    return false;
                }
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false;
                }
                adapter.moveItem(from, to);
                profileManager.reorderMyShortcuts(
                        selectedProfile.id, new ArrayList<>(adapter.getItems()), false);
                myShortcutsList.clear();
                myShortcutsList.addAll(profileManager.getMyShortcuts(selectedProfile.id));
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
                }
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }
        };
        myShortcutsReorderTouchHelper = new ItemTouchHelper(callback);
        myShortcutsReorderTouchHelper.attachToRecyclerView(myShortcutsRecyclerView);
        myShortcutsReorderAdapter.setDragHelper(myShortcutsReorderTouchHelper);
        myShortcutsReorderAdapter.notifyDataSetChanged();
    }

    private void sortShortcutsForDisplay(List<ShortcutProfileManager.Shortcut> shortcuts) {
        if (shortcuts == null || shortcuts.size() < 2) {
            return;
        }
        final java.util.HashMap<String, Integer> existing = new java.util.HashMap<>();
        for (int i = 0; i < shortcuts.size(); i++) {
            ShortcutProfileManager.Shortcut shortcut = shortcuts.get(i);
            existing.put(shortcut.id != null ? shortcut.id : ("idx_" + i), i);
        }
        Collections.sort(shortcuts, Comparator
                .comparingInt((ShortcutProfileManager.Shortcut s) -> s.displayOrder > 0 ? s.displayOrder : Integer.MAX_VALUE)
                .thenComparingInt(s -> existing.getOrDefault(s.id != null ? s.id : "", Integer.MAX_VALUE)));
    }

    private void addToMyFavorites(ShortcutProfileManager.Shortcut shortcut) {
        if (selectedProfile == null) return;
        for (ShortcutProfileManager.Shortcut s : myShortcutsList) {
            if (s.id.equals(shortcut.id)) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.shortcut_hub_already_in_favorites_title)
                        .setMessage("'" + shortcut.name + "' is already in Favorites.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.shortcut_hub_add_to_favorites_title)
                .setMessage("Add '" + shortcut.name + "' (" + shortcut.label + ") to Favorites?")
                .setPositiveButton("Add", (d, w) -> {
                    myShortcutsList.add(shortcut);
                    profileManager.updateMyShortcuts(selectedProfile.id, myShortcutsList);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                    Toast.makeText(getContext(),
                            getString(R.string.shortcut_hub_added_to_favorites_toast, shortcut.name),
                            Toast.LENGTH_SHORT).show();
                    refreshShortcutsGrid();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemoveFromFavorites(ShortcutProfileManager.Shortcut shortcut) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove Favorite")
                .setMessage(getString(R.string.shortcut_hub_remove_from_favorites_message, shortcut.name))
                .setPositiveButton("Remove", (d, w) -> {
                    myShortcutsList.removeIf(s -> s.id.equals(shortcut.id));
                    profileManager.updateMyShortcuts(selectedProfile.id, myShortcutsList);
                    refreshShortcutsGrid();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showShortcutActionsMenu(ShortcutProfileManager.Shortcut shortcut) {
        final boolean isFavorite = myShortcutsList.stream()
                .anyMatch(s -> s.id.equals(shortcut.id));

        java.util.ArrayList<String> options = new java.util.ArrayList<>();
        options.add("Edit");
        options.add("Delete");
        if (isFavorite) {
            options.add("Remove from My");
        } else {
            options.add("Add to My");
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(shortcut.name)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showEditShortcutDialog(shortcut);
                            break;
                        case 1:
                            confirmDeleteShortcut(shortcut);
                            break;
                        case 2:
                            if (isFavorite) {
                                confirmRemoveFromFavorites(shortcut);
                            } else {
                                addToMyFavorites(shortcut);
                            }
                            break;
                    }
                })
                .show();
    }

    private void showEditShortcutDialog(ShortcutProfileManager.Shortcut shortcut) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_shortcut, null, false);

        EditText nameInput = dialogView.findViewById(R.id.shortcut_name_input);
        EditText dataInput = dialogView.findViewById(R.id.shortcut_data_input);
        EditText iconInput = dialogView.findViewById(R.id.shortcut_icon_input);
        EditText orderInput = dialogView.findViewById(R.id.shortcut_order_input);
        LinearLayout chipsRow = dialogView.findViewById(R.id.key_chips_row);
        TextView previewText = dialogView.findViewById(R.id.shortcut_preview);

        nameInput.setText(shortcut.name);
        String dataToken = KeyParser.toToken(shortcut.keyCode, shortcut.modifiers);
        dataInput.setText(dataToken);
        iconInput.setText(shortcut.icon != null ? shortcut.icon : "");
        if (shortcut.displayOrder > 0) {
            orderInput.setText(String.valueOf(shortcut.displayOrder));
        }

        String[][] tokens = {
            {"⎇ Alt", "<ALT>"}, {"^ Ctrl", "<CTRL>"}, {"⇧ Shift", "<SHIFT>"}, {"⌘ Cmd", "<CMD>"},
            {"</ALT>", "</ALT>"}, {"</CTRL>", "</CTRL>"}, {"</SHIFT>", "</SHIFT>"}, {"</CMD>", "</CMD>"},
            {"⎋ Esc", "<ESC>"}, {"⌫ Back", "<BACK>"}, {"⏎ Enter", "<ENTER>"}, {"␣ Space", "<SPACE>"},
            {"←", "<LEFT>"}, {"→", "<RIGHT>"}, {"↑", "<UP>"}, {"↓", "<DOWN>"},
            {"⇱ Home", "<HOME>"}, {"⇲ End", "<END>"}, {"⇥ Tab", "<TAB>"}, {"⌦ Del", "<DEL>"},
            {"F1", "<F1>"}, {"F2", "<F2>"}, {"F3", "<F3>"}, {"F4", "<F4>"},
            {"F5", "<F5>"}, {"F6", "<F6>"}, {"F7", "<F7>"}, {"F8", "<F8>"},
            {"F9", "<F9>"}, {"F10", "<F10>"}, {"F11", "<F11>"}, {"F12", "<F12>"}
        };

        for (String[] entry : tokens) {
            Button chip = new Button(requireContext());
            chip.setText(entry[0]);
            chip.setAllCaps(false);
            chip.setTextSize(11);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            int pad = dpToPx(8);
            chip.setPadding(pad, dpToPx(2), pad, dpToPx(2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dpToPx(6), 0);
            chip.setLayoutParams(lp);
            final String token = entry[1];
            chip.setOnClickListener(v -> {
                int start = Math.max(dataInput.getSelectionStart(), 0);
                int end = Math.max(dataInput.getSelectionEnd(), 0);
                dataInput.getText().replace(Math.min(start, end), Math.max(start, end), token, 0, token.length());
            });
            chipsRow.addView(chip);
        }

        dataInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String d = s.toString().trim();
                if (d.isEmpty()) {
                    previewText.setText("Preview: ...");
                    return;
                }
                KeyParser.ParsedKey parsed = KeyParser.parse(d);
                if (parsed.keyCode >= 0) {
                    previewText.setText("Preview: " + KeyParser.toLabelForTargetOs(
                            parsed.keyCode, parsed.modifiers, getTargetOs()));
                } else {
                    previewText.setText("Preview: no valid key detected");
                }
            }
        });
        previewText.setText("Preview: " + shortcut.label);

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit Shortcut")
                .setView(dialogView)
                .setPositiveButton("Save", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String inputData = dataInput.getText().toString().trim();
                    if (name.isEmpty() || inputData.isEmpty()) {
                        Toast.makeText(getContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    KeyParser.ParsedKey parsed = KeyParser.parse(inputData);
                    if (parsed.keyCode < 0) {
                        Toast.makeText(getContext(), "No valid key found in shortcut data", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String label = KeyParser.toLabelForTargetOs(parsed.keyCode, parsed.modifiers, getTargetOs());
                    shortcut.name = name;
                    shortcut.label = label;
                    shortcut.modifiers = parsed.modifiers;
                    shortcut.keyCode = parsed.keyCode;
                    shortcut.icon = iconInput.getText().toString().trim();
                    shortcut.displayOrder = parseDisplayOrder(orderInput.getText().toString().trim(), shortcut.displayOrder);

                    profileManager.updateProfile(selectedProfile);
                    loadProfiles();
                    refreshSelectedProfileAndGrid();
                    Toast.makeText(getContext(), "Saved: " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteShortcut(ShortcutProfileManager.Shortcut shortcut) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Shortcut")
                .setMessage("Delete '" + shortcut.name + "'?")
                .setPositiveButton("Delete", (d, w) -> {
                    boolean removed = false;

                    // Try removing from flat list
                    if (selectedProfile.shortcuts != null) {
                        removed = selectedProfile.shortcuts.removeIf(s -> s.id.equals(shortcut.id));
                    }

                    // Also try removing from categories
                    if (selectedProfile.categories != null) {
                        for (ShortcutProfileManager.ShortcutCategory cat : selectedProfile.categories) {
                            if (cat.shortcuts != null) {
                                removed = cat.shortcuts.removeIf(s -> s.id.equals(shortcut.id)) || removed;
                            }
                        }
                    }

                    // Also remove from favorites if present
                    myShortcutsList.removeIf(s -> s.id.equals(shortcut.id));
                    profileManager.updateMyShortcuts(selectedProfile.id, myShortcutsList);

                    if (removed) {
                        profileManager.updateProfile(selectedProfile);
                        loadProfiles();
                        refreshSelectedProfileAndGrid();
                    }
                    Toast.makeText(getContext(), "Deleted: " + shortcut.name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshSelectedProfileAndGrid() {
        for (ShortcutProfile p : profilesList) {
            if (p.id.equals(selectedProfile.id)) {
                selectedProfile = p;
                break;
            }
        }
        rebuildCategoryTabs(selectedProfile);
        syncHubDetailTabsSelection();
        refreshShortcutsGrid();
    }

    private void executeShortcut(ShortcutProfileManager.Shortcut shortcut) {
        if (connectionManager == null) {
            Toast.makeText(getContext(), "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        connectionManager.sendKeyEvent(normalizeModifiersForTargetOs(shortcut.modifiers), shortcut.keyCode);
        // Small delay then release key
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (connectionManager != null) {
                connectionManager.sendKeyRelease();
            }
        }, 80);

        // Haptic feedback
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
        }

        Log.d(TAG, "Sent shortcut: " + shortcut.name + " (" + shortcut.label + ")"
                + " modifiers=" + shortcut.modifiers + " key=" + shortcut.keyCode);
    }

    private int normalizeModifiersForTargetOs(int modifiers) {
        String targetOs = getTargetOs();
        final int modCtrl = 0x01;
        final int modCmd = 0x08;
        if ("macos".equals(targetOs)) {
            boolean hasCtrl = (modifiers & modCtrl) != 0;
            boolean hasCmd = (modifiers & modCmd) != 0;
            if (hasCtrl && !hasCmd) {
                return (modifiers & ~modCtrl) | modCmd;
            }
        }
        return modifiers;
    }

    private void showCreateProfileDialog() {
        EditText input = new EditText(getContext());
        input.setHint("Profile name (e.g., 'My App')");
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Create New Profile")
            .setMessage("Enter profile name:")
            .setView(input)
            .setPositiveButton("Create", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    ShortcutProfile profile = profileManager.createProfile(name, "Custom profile");
                    loadProfiles();
                    Toast.makeText(getContext(), "Created: " + profile.name, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Please enter a name", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showProfileOptionsDialog(ShortcutProfile profile) {
        String[] options;
        if ("default".equals(profile.id)) {
            options = new String[]{"View Shortcuts", "Add Shortcut", "Duplicate", "Export"};
        } else {
            options = new String[]{"View Shortcuts", "Add Shortcut", "Duplicate", "Export", "Delete"};
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(profile.name)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // View Shortcuts
                        viewShortcuts(profile);
                        break;
                    case 1: // Add Shortcut
                        showAddShortcutDialog(profile);
                        break;
                    case 2: // Duplicate
                        duplicateProfile(profile);
                        break;
                    case 3: // Export
                        exportProfile(profile);
                        break;
                    case 4: // Delete (only for non-default)
                        if (!"default".equals(profile.id)) {
                            deleteProfile(profile);
                        }
                        break;
                }
            })
            .show();
    }

    private void showAddShortcutDialog(ShortcutProfile profile) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_shortcut, null, false);

        EditText nameInput = dialogView.findViewById(R.id.shortcut_name_input);
        EditText dataInput = dialogView.findViewById(R.id.shortcut_data_input);
        EditText iconInput = dialogView.findViewById(R.id.shortcut_icon_input);
        EditText orderInput = dialogView.findViewById(R.id.shortcut_order_input);
        LinearLayout chipsRow = dialogView.findViewById(R.id.key_chips_row);
        TextView previewText = dialogView.findViewById(R.id.shortcut_preview);

        String[][] tokens = {
            {"⎇ Alt", "<ALT>"}, {"^ Ctrl", "<CTRL>"}, {"⇧ Shift", "<SHIFT>"}, {"⌘ Cmd", "<CMD>"},
            {"</ALT>", "</ALT>"}, {"</CTRL>", "</CTRL>"}, {"</SHIFT>", "</SHIFT>"}, {"</CMD>", "</CMD>"},
            {"⎋ Esc", "<ESC>"}, {"⌫ Back", "<BACK>"}, {"⏎ Enter", "<ENTER>"}, {"␣ Space", "<SPACE>"},
            {"←", "<LEFT>"}, {"→", "<RIGHT>"}, {"↑", "<UP>"}, {"↓", "<DOWN>"},
            {"⇱ Home", "<HOME>"}, {"⇲ End", "<END>"}, {"⇥ Tab", "<TAB>"}, {"⌦ Del", "<DEL>"},
            {"F1", "<F1>"}, {"F2", "<F2>"}, {"F3", "<F3>"}, {"F4", "<F4>"},
            {"F5", "<F5>"}, {"F6", "<F6>"}, {"F7", "<F7>"}, {"F8", "<F8>"},
            {"F9", "<F9>"}, {"F10", "<F10>"}, {"F11", "<F11>"}, {"F12", "<F12>"}
        };

        for (String[] entry : tokens) {
            Button chip = new Button(requireContext());
            chip.setText(entry[0]);
            chip.setAllCaps(false);
            chip.setTextSize(11);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            int pad = dpToPx(8);
            chip.setPadding(pad, dpToPx(2), pad, dpToPx(2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dpToPx(6), 0);
            chip.setLayoutParams(lp);
            final String token = entry[1];
            chip.setOnClickListener(v -> {
                int start = Math.max(dataInput.getSelectionStart(), 0);
                int end = Math.max(dataInput.getSelectionEnd(), 0);
                dataInput.getText().replace(Math.min(start, end), Math.max(start, end), token, 0, token.length());
            });
            chipsRow.addView(chip);
        }

        // Live preview: parse data input and show computed label
        dataInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String data = s.toString().trim();
                if (data.isEmpty()) {
                    previewText.setText("Preview: ...");
                    return;
                }
                KeyParser.ParsedKey parsed = KeyParser.parse(data);
                if (parsed.keyCode >= 0) {
                    String label = KeyParser.toLabelForTargetOs(parsed.keyCode, parsed.modifiers, getTargetOs());
                    previewText.setText("Preview: " + label);
                } else {
                    previewText.setText("Preview: no valid key detected");
                }
            }
        });

        new AlertDialog.Builder(requireContext())
                .setTitle("Add Shortcut to " + profile.name)
                .setView(dialogView)
                .setPositiveButton("Add", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String data = dataInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "Name is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (data.isEmpty()) {
                        Toast.makeText(getContext(), "Shortcut data is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    KeyParser.ParsedKey parsed = KeyParser.parse(data);
                    if (parsed.keyCode < 0) {
                        Toast.makeText(getContext(), "No valid key found in shortcut data", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String label = KeyParser.toLabelForTargetOs(parsed.keyCode, parsed.modifiers, getTargetOs());
                    ShortcutProfileManager.Shortcut shortcut = new ShortcutProfileManager.Shortcut(
                            "user-" + System.currentTimeMillis(), name, label, parsed.modifiers, parsed.keyCode);
                    shortcut.icon = iconInput.getText().toString().trim();
                    shortcut.displayOrder = parseDisplayOrder(
                            orderInput.getText().toString().trim(),
                            nextDisplayOrder(profile));

                    profile.shortcuts.add(shortcut);
                    profileManager.updateProfile(profile);
                    loadProfiles();

                    // Rebind selectedProfile to the refreshed copy
                    for (ShortcutProfile p : profilesList) {
                        if (p.id.equals(profile.id)) {
                            selectedProfile = p;
                            break;
                        }
                    }

                    // If profile has categories, switch to the last category to show the new shortcut
                    if (selectedProfile.categories != null && !selectedProfile.categories.isEmpty()) {
                        String lastCatId = selectedProfile.categories.get(
                                selectedProfile.categories.size() - 1).id;
                        currentTab = TAB_BROWSE;
                        currentCategoryId = lastCatId;
                        rebuildCategoryTabs(selectedProfile);
                    }
                    syncHubDetailTabsSelection();
                    refreshShortcutsGrid();

                    Toast.makeText(getContext(), "Added shortcut: " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int nextDisplayOrder(ShortcutProfile profile) {
        int max = 0;
        List<ShortcutProfileManager.Shortcut> all = profile.getAllShortcutsFlat();
        for (ShortcutProfileManager.Shortcut shortcut : all) {
            if (shortcut != null && shortcut.displayOrder > max) {
                max = shortcut.displayOrder;
            }
        }
        return max + 1;
    }

    private int parseDisplayOrder(String raw, int fallback) {
        if (TextUtils.isEmpty(raw)) {
            return fallback > 0 ? fallback : 0;
        }
        try {
            int value = Integer.parseInt(raw);
            return Math.max(value, 0);
        } catch (NumberFormatException ignored) {
            return fallback > 0 ? fallback : 0;
        }
    }

    private void viewShortcuts(ShortcutProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append(profile.name).append("\n\n");
        for (ShortcutProfileManager.Shortcut shortcut : profile.shortcuts) {
            sb.append(shortcut.name).append(": ").append(shortcut.label).append("\n");
        }
        
        new AlertDialog.Builder(requireContext())
            .setTitle(profile.name)
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    private void duplicateProfile(ShortcutProfile profile) {
        ShortcutProfile duplicate = profileManager.duplicateProfile(profile.id);
        if (duplicate != null) {
            loadProfiles();
            Toast.makeText(getContext(), "Duplicated: " + duplicate.name, Toast.LENGTH_SHORT).show();
        }
    }

    private void exportProfile(ShortcutProfile profile) {
        exportProfileToFile(profile);
    }

    private void exportProfileToFile(ShortcutProfile profile) {
        String json = profileManager.exportProfile(profile.id);
        if (json == null) {
            Toast.makeText(getContext(), "Export failed", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save to Downloads folder
        try {
            String filename = "keymod_profile_" + profile.name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase() + ".json";
            java.io.File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
            java.io.File outputFile = new java.io.File(downloadsDir, filename);
            
            java.io.FileWriter writer = new java.io.FileWriter(outputFile);
            writer.write(json);
            writer.close();
            
            Toast.makeText(getContext(), "Saved to: " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            Log.d(TAG, "Exported profile to: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Export failed: " + e.getMessage());
            Toast.makeText(getContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Import Profile");
        builder.setMessage("Select import method:");
        
        // Option 1: Paste JSON
        builder.setPositiveButton("📋 Paste JSON", (dialog, which) -> {
            showPasteJsonDialog();
        });
        
        // Option 2: Browse files
        builder.setNegativeButton("📁 Browse Files", (dialog, which) -> {
            openFilePicker();
        });
        
        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    private void showPasteJsonDialog() {
        EditText input = new EditText(getContext());
        input.setHint("Paste profile JSON here");
        input.setMinLines(5);
        input.setGravity(android.view.Gravity.TOP);
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Import from JSON")
            .setView(input)
            .setPositiveButton("Import", (dialog, which) -> {
                String json = input.getText().toString().trim();
                if (!json.isEmpty()) {
                    importProfileFromJson(json);
                } else {
                    Toast.makeText(getContext(), "Please paste JSON", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openFilePicker() {
        // Request storage permission first
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), 
                android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        } else {
            // Permission already granted, open file picker
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/json");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 200);
        }
    }

    private void importProfileFromJson(String json) {
        ShortcutProfileManager.ShortcutProfile profile = profileManager.importProfile(json);
        if (profile != null) {
            loadProfiles();
            Toast.makeText(getContext(), "Imported: " + profile.name, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Import failed - Invalid JSON", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                importProfileFromUri(uri);
            }
        }
    }

    private void importProfileFromUri(android.net.Uri uri) {
        try {
            java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                java.util.Scanner scanner = new java.util.Scanner(inputStream);
                scanner.useDelimiter("\\A");
                String json = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                inputStream.close();
                
                importProfileFromJson(json);
            }
        } catch (Exception e) {
            Log.e(TAG, "Import from URI failed: " + e.getMessage());
            Toast.makeText(getContext(), "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void deleteProfile(ShortcutProfile profile) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Delete Profile")
            .setMessage("Delete '" + profile.name + "'?")
            .setPositiveButton("Delete", (dialog, which) -> {
                profileManager.deleteProfile(profile.id);
                loadProfiles();
                Toast.makeText(getContext(), "Deleted: " + profile.name, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updateActiveProfileDisplay() {
        if (activeProfile != null) {
            activeProfileText.setText(getString(R.string.shortcut_hub_active_profile, activeProfile.name));
        } else {
            activeProfileText.setText("");
        }
    }

    private void updateEmptyState() {
        if (profilesList.isEmpty()) {
            profilesRecyclerView.setVisibility(View.GONE);
            emptyTextView.setVisibility(View.VISIBLE);
        } else {
            profilesRecyclerView.setVisibility(View.VISIBLE);
            emptyTextView.setVisibility(View.GONE);
        }
    }

    // ProfileChangeListener callbacks
    @Override
    public void onProfileChanged(String profileId) {
        loadProfiles();
    }

    @Override
    public void onProfileCreated(ShortcutProfile profile) {
        loadProfiles();
    }

    @Override
    public void onProfileUpdated(ShortcutProfile profile) {
        loadProfiles();
    }

    @Override
    public void onProfileDeleted(String profileId) {
        loadProfiles();
    }

    @Override
    public void onProfileImported(ShortcutProfile profile) {
        loadProfiles();
    }

    private final class ProfilesRecyclerAdapter extends RecyclerView.Adapter<ProfilesRecyclerAdapter.VH> {

        private String activeProfileId;

        void setActiveProfileId(String activeProfileId) {
            this.activeProfileId = activeProfileId;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_shortcut_hub_profile_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ShortcutProfile profile = profilesList.get(position);
            holder.name.setText(profile.name);
            holder.desc.setText(profile.description != null ? profile.description : "");
            boolean isActive = profile.id != null && profile.id.equals(activeProfileId);
            holder.activeIndicator.setVisibility(isActive ? View.VISIBLE : View.GONE);
            holder.activeBadge.setVisibility(isActive ? View.VISIBLE : View.GONE);
            holder.count.setText(getString(R.string.shortcut_hub_profile_shortcut_count, profile.getShortcutCount()));

            holder.itemView.setOnClickListener(v -> showShortcutsDetail(profile));
            holder.itemView.setOnLongClickListener(v -> {
                showProfileOptionsDialog(profile);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return profilesList.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final View activeIndicator;
            final TextView name;
            final TextView desc;
            final TextView activeBadge;
            final TextView count;

            VH(@NonNull View itemView) {
                super(itemView);
                activeIndicator = itemView.findViewById(R.id.profile_active_indicator);
                name = itemView.findViewById(R.id.profile_name);
                desc = itemView.findViewById(R.id.profile_description);
                activeBadge = itemView.findViewById(R.id.profile_active_badge);
                count = itemView.findViewById(R.id.profile_count);
            }
        }
    }
}
