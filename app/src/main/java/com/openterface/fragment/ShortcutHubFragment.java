package com.openterface.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.CreateShortcutBottomSheet;
import com.openterface.keymod.preset.FixedStripLayoutCatalog;
import com.openterface.keymod.preset.StripCatalogGridAdapter;
import com.openterface.keymod.preset.StripCatalogGridItem;
import com.openterface.keymod.ShortcutProfileManager.Shortcut;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.tabs.TabLayout;

import com.openterface.keymod.MainActivity;
import com.openterface.keymod.preset.Rows23StripProfile;
import com.openterface.keymod.preset.Rows23StripProfileDocument;
import com.openterface.keymod.preset.Rows23StripProfileConstants;
import com.openterface.keymod.preset.Rows23StripProfileManager;
import com.openterface.keymod.preset.StripSlotMapStore;
import com.openterface.keymod.ProfileUiStrings;
import com.openterface.keymod.MyShortcutsReorderAdapter;
import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutSectionPickAdapter;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.ShortcutProfileManager.ShortcutProfile;
import com.openterface.keymod.ShortcutProfileManager.ProfileChangeListener;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Shortcut Hub Fragment - Profile management for app-specific shortcuts
 * Phase 3: Shortcut Hub
 */
public class ShortcutHubFragment extends Fragment implements ProfileChangeListener {

    private static final String TAG = "ShortcutHubFragment";
    private static final int REQ_IMPORT_PROFILE_FILE = 200;
    private static final int REQ_IMPORT_STRIP_FILE = 201;

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
    private TabLayout hubMainTabs;
    private View hubTabContentProfiles;
    private View hubTabContentStrip;
    private LinearLayout panelStripProfileList;
    private LinearLayout panelStripProfileDetail;
    private Button createStripProfileButton;
    private Button importStripProfileButton;
    private RecyclerView stripProfilesRecyclerView;
    private RecyclerView hubStripCatalogRecycler;
    @Nullable
    private StripCatalogGridAdapter stripCatalogGridAdapter;
    @Nullable
    private ItemTouchHelper stripCatalogSlotTouchHelper;
    /** Latest pointer in {@link #hubStripCatalogRecycler} coordinates (for strip drag hover hit-test). */
    private float stripCatalogPointerRvX = Float.NaN;
    private float stripCatalogPointerRvY = Float.NaN;
    @Nullable
    private View.OnTouchListener stripCatalogPointerTouchListener;
    private MaterialButton stripDetailResetButton;
    private MaterialButton stripDetailBackButton;
    @Nullable
    private FrameLayout hubSlotEditorOverlay;
    private TextView stripDetailTitle;
    private TextView stripDetailDescription;
    private Rows23StripProfileManager stripProfileManager;
    private StripProfilesRecyclerAdapter stripProfilesRecyclerAdapter;
    private final List<Rows23StripProfile> stripProfilesList = new ArrayList<>();
    private Rows23StripProfile selectedStripDetailProfile;
    /** When strip profile detail is fullscreen, mirrors strip Back for system back. */
    private OnBackPressedCallback stripDetailBackCallback;
    /** Avoid reacting when {@link #showProfileList()} resets the main hub tab. */
    private boolean suppressMainHubTabSelection;

    // UI Components - Shortcuts detail panel
    private LinearLayout panelShortcutsDetail;
    private Button backButton;
    private Button resetDefaultProfileButton;
    private Button addShortcutButton;
    private TextView detailProfileName;
    private TextView detailProfileDescription;
    private TabLayout hubDetailTabs;
    private RecyclerView browseShortcutsRecyclerView;
    private RecyclerView myShortcutsRecyclerView;
    private MyShortcutsReorderAdapter myShortcutsReorderAdapter;
    private ItemTouchHelper myShortcutsReorderTouchHelper;
    private ItemTouchHelper browseCategoryReorderTouchHelper;
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

    /** Used after storage permission grant to reopen the correct file picker. */
    private int pendingImportFileRequestCode = REQ_IMPORT_PROFILE_FILE;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shortcut_hub, container, false);

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        profileManager = new ShortcutProfileManager(requireContext());
        profileManager.setListener(this);
        stripProfileManager = new Rows23StripProfileManager(requireContext(), profileManager);

        if (getActivity() instanceof MainActivity) {
            connectionManager = ((MainActivity) getActivity()).getConnectionManager();
        }

        initializeViews(view);
        loadProfiles();
        setupListeners();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        stripDetailBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (tryPopRows23SlotEditor()) {
                    return;
                }
                showStripProfileList();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), stripDetailBackCallback);

        getChildFragmentManager().addOnBackStackChangedListener(() -> {
            if (hubSlotEditorOverlay != null
                    && getChildFragmentManager().findFragmentById(R.id.hub_slot_editor_overlay) == null) {
                hubSlotEditorOverlay.setVisibility(View.GONE);
            }
        });
    }

    private final MainActivity.OnTargetOsChangeListener osChangeListener = os -> {
        if (browsePickAdapter != null) {
            browsePickAdapter.notifyDataSetChanged();
        }
        if (myShortcutsReorderAdapter != null) {
            myShortcutsReorderAdapter.notifyDataSetChanged();
        }
        if (selectedStripDetailProfile != null && panelStripProfileDetail != null
                && panelStripProfileDetail.getVisibility() == View.VISIBLE) {
            refreshStripCatalogForSelectedStrip();
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
        hubMainTabs = view.findViewById(R.id.hub_main_tabs);
        hubTabContentProfiles = view.findViewById(R.id.hub_tab_content_profiles);
        hubTabContentStrip = view.findViewById(R.id.hub_tab_content_strip);
        panelStripProfileList = view.findViewById(R.id.panel_strip_profile_list);
        panelStripProfileDetail = view.findViewById(R.id.panel_strip_profile_detail);
        createStripProfileButton = view.findViewById(R.id.create_strip_profile_button);
        importStripProfileButton = view.findViewById(R.id.import_strip_profile_button);
        stripProfilesRecyclerView = view.findViewById(R.id.strip_profiles_recycler);
        hubStripCatalogRecycler = view.findViewById(R.id.hub_strip_catalog_recycler);
        stripDetailBackButton = view.findViewById(R.id.strip_detail_back_button);
        stripDetailTitle = view.findViewById(R.id.strip_detail_title);
        stripDetailDescription = view.findViewById(R.id.strip_detail_description);
        stripDetailResetButton = view.findViewById(R.id.strip_detail_reset_button);
        hubSlotEditorOverlay = view.findViewById(R.id.hub_slot_editor_overlay);

        // Shortcuts detail panel
        panelShortcutsDetail = view.findViewById(R.id.panel_shortcuts_detail);
        backButton = view.findViewById(R.id.back_button);
        resetDefaultProfileButton = view.findViewById(R.id.reset_default_profile_button);
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

        stripProfilesRecyclerAdapter = new StripProfilesRecyclerAdapter();
        stripProfilesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        stripProfilesRecyclerView.setAdapter(stripProfilesRecyclerAdapter);

        browsePickAdapter = new ShortcutSectionPickAdapter(requireContext(), getTargetOs(), new ArrayList<>());
        browsePickAdapter.setRowInteraction(new ShortcutSectionPickAdapter.RowInteraction() {
            @Override
            public void onRowClick(@NonNull ShortcutProfileManager.Shortcut shortcut, @NonNull View rowContent) {
                pulseShortcutRowFeedback(rowContent);
                executeShortcut(shortcut);
            }

            @Override
            public void onRowLongClick(@NonNull ShortcutProfileManager.Shortcut shortcut) {
                showShortcutActionsMenu(shortcut);
            }
        });
        browsePickAdapter.setOnEditShortcutClickListener(this::openEditShortcutFromBrowse);
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
        removeShortcutFromMyShortcuts(shortcut);
    }

    private void removeShortcutFromMyShortcuts(ShortcutProfileManager.Shortcut shortcut) {
        if (selectedProfile == null || shortcut == null || shortcut.id == null || shortcut.id.isEmpty()) {
            return;
        }
        boolean removed = myShortcutsList.removeIf(s -> s.id != null && s.id.equals(shortcut.id));
        if (!removed) {
            return;
        }
        profileManager.updateMyShortcuts(selectedProfile.id, myShortcutsList);
        Toast.makeText(requireContext(), R.string.my_shortcuts_removed_from_my, Toast.LENGTH_SHORT).show();
        if (browsePickAdapter != null) {
            browsePickAdapter.notifyDataSetChanged();
        }
        refreshShortcutsGrid();
    }

    private void loadProfiles() {
        profilesList.clear();
        profilesList.addAll(profileManager.getProfilesForUiPicking());
        
        activeProfile = profileManager.getActiveProfile();
        profilesRecyclerAdapter.setActiveProfileId(activeProfile != null ? activeProfile.id : null);
        profilesRecyclerAdapter.notifyDataSetChanged();
        updateActiveProfileDisplay();
        updateEmptyState();
        loadStripProfiles();
        if (selectedStripDetailProfile != null && panelStripProfileDetail != null
                && panelStripProfileDetail.getVisibility() == View.VISIBLE) {
            refreshStripCatalogForSelectedStrip();
        }
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

        createStripProfileButton.setOnClickListener(v -> showCreateStripProfileDialog());
        importStripProfileButton.setOnClickListener(v -> showStripImportDialog());
        stripDetailBackButton.setOnClickListener(v -> {
            if (!tryPopRows23SlotEditor()) {
                showStripProfileList();
            }
        });
        stripDetailResetButton.setOnClickListener(v -> showResetStripProfileDetailDialog());

        if (hubMainTabs != null) {
            hubMainTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (suppressMainHubTabSelection) {
                        return;
                    }
                    applyMainHubTabVisibility(tab.getPosition());
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                }
            });
            applyMainHubTabVisibility(hubMainTabs.getSelectedTabPosition());
        }

        // Back button - return to profile list
        backButton.setOnClickListener(v -> showProfileList());

        resetDefaultProfileButton.setOnClickListener(v -> showResetDefaultProfileDialog());

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
        dismissStripDetailState();
        selectedProfile = profile;
        currentTab = TAB_MY;  // Default to My Shortcuts
        currentCategoryId = null;

        detailProfileName.setText(ProfileUiStrings.displayName(requireContext(), profile));
        detailProfileDescription.setText(ProfileUiStrings.displayDescription(requireContext(), profile));

        resetDefaultProfileButton.setVisibility("default".equals(profile.id) ? View.VISIBLE : View.GONE);

        // Load persisted My Shortcuts for this profile (sanitize dedupes / drops orphans)
        profileManager.sanitizeMyShortcutsForProfile(profile.id);
        myShortcutsList = profileManager.getMyShortcuts(profile.id);

        // Build dynamic category tabs
        rebuildCategoryTabs(profile);
        syncHubDetailTabsSelection();
        refreshShortcutsGrid();

        panelProfilesList.setVisibility(View.GONE);
        panelShortcutsDetail.setVisibility(View.VISIBLE);
    }

    private void showProfileList() {
        dismissStripDetailState();
        selectedProfile = null;
        panelShortcutsDetail.setVisibility(View.GONE);
        panelProfilesList.setVisibility(View.VISIBLE);
        if (hubMainTabs != null) {
            suppressMainHubTabSelection = true;
            TabLayout.Tab first = hubMainTabs.getTabAt(0);
            if (first != null) {
                first.select();
            }
            applyMainHubTabVisibility(0);
            suppressMainHubTabSelection = false;
        }
    }

    private void applyMainHubTabVisibility(int position) {
        if (hubTabContentProfiles == null || hubTabContentStrip == null) {
            return;
        }
        if (position == 1) {
            hubTabContentProfiles.setVisibility(View.GONE);
            hubTabContentStrip.setVisibility(View.VISIBLE);
            loadStripProfiles();
            if (selectedStripDetailProfile != null && panelStripProfileDetail.getVisibility() == View.VISIBLE) {
                refreshStripCatalogForSelectedStrip();
            }
        } else {
            hubTabContentProfiles.setVisibility(View.VISIBLE);
            hubTabContentStrip.setVisibility(View.GONE);
        }
    }

    private void loadStripProfiles() {
        if (stripProfileManager == null) {
            return;
        }
        stripProfilesList.clear();
        stripProfilesList.addAll(stripProfileManager.getProfiles());
        if (stripProfilesRecyclerAdapter != null) {
            stripProfilesRecyclerAdapter.setActiveStripId(stripProfileManager.getActiveProfileId());
            stripProfilesRecyclerAdapter.notifyDataSetChanged();
        }
    }

    private void showStripProfileList() {
        dismissStripDetailState();
        if (panelProfilesList != null) {
            panelProfilesList.setVisibility(View.VISIBLE);
        }
        if (panelShortcutsDetail != null) {
            panelShortcutsDetail.setVisibility(View.GONE);
        }
    }

    /**
     * Clears strip detail selection and in-tab list/detail views; does not show or hide the hub shell
     * ({@link #panelProfilesList}) — use {@link #showStripProfileList()} to restore the hub after fullscreen detail.
     */
    private void dismissStripDetailState() {
        selectedStripDetailProfile = null;
        dismissRows23SlotEditorOverlay();
        if (panelStripProfileDetail != null) {
            panelStripProfileDetail.setVisibility(View.GONE);
        }
        if (panelStripProfileList != null) {
            panelStripProfileList.setVisibility(View.VISIBLE);
        }
        stripCatalogGridAdapter = null;
        if (stripDetailBackCallback != null) {
            stripDetailBackCallback.setEnabled(false);
        }
    }

    private void showStripProfileDetail(@NonNull Rows23StripProfile profile) {
        selectedStripDetailProfile = profile;
        if (panelStripProfileList != null) {
            panelStripProfileList.setVisibility(View.GONE);
        }
        if (panelProfilesList != null) {
            panelProfilesList.setVisibility(View.GONE);
        }
        if (panelShortcutsDetail != null) {
            panelShortcutsDetail.setVisibility(View.GONE);
        }
        if (panelStripProfileDetail != null) {
            panelStripProfileDetail.setVisibility(View.VISIBLE);
        }
        refreshStripCatalogForSelectedStrip();
        if (stripDetailBackCallback != null) {
            stripDetailBackCallback.setEnabled(true);
        }
    }

    private void updateStripDetailHeader() {
        if (stripDetailTitle == null || selectedStripDetailProfile == null) {
            return;
        }
        Rows23StripProfile profile = stripProfileManager != null
                ? stripProfileManager.getProfileById(selectedStripDetailProfile.id)
                : null;
        if (profile == null) {
            profile = selectedStripDetailProfile;
        }
        stripDetailTitle.setText(profile.name != null ? profile.name : profile.id);
        if (stripDetailDescription != null) {
            int n = profile.slotMap != null ? profile.slotMap.size() : 0;
            if (n == 0) {
                stripDetailDescription.setText(R.string.shortcut_hub_strip_profile_factory_layout);
            } else {
                stripDetailDescription.setText(
                        getString(R.string.shortcut_hub_strip_profile_slot_count, n));
            }
        }
    }

    private void refreshStripCatalogForSelectedStrip() {
        updateStripDetailHeader();
        if (hubStripCatalogRecycler == null || profileManager == null || selectedStripDetailProfile == null) {
            return;
        }
        if (stripCatalogSlotTouchHelper != null) {
            stripCatalogSlotTouchHelper.attachToRecyclerView(null);
            stripCatalogSlotTouchHelper = null;
        }
        hubStripCatalogRecycler.setOnTouchListener(null);
        stripCatalogPointerTouchListener = null;
        stripCatalogPointerRvX = Float.NaN;
        stripCatalogPointerRvY = Float.NaN;
        List<StripCatalogGridItem> gridItems =
                FixedStripLayoutCatalog.buildGridItems(requireContext(), profileManager, getTargetOs());
        final List<StripCatalogGridItem> gridItemsForSpan = gridItems;
        GridLayoutManager glm = new GridLayoutManager(requireContext(), 2);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position < 0 || position >= gridItemsForSpan.size()) {
                    return 2;
                }
                return gridItemsForSpan.get(position).viewType == StripCatalogGridItem.VIEW_TYPE_SLOT_CELL
                        ? 1 : 2;
            }
        });
        hubStripCatalogRecycler.setLayoutManager(glm);
        stripCatalogGridAdapter = new StripCatalogGridAdapter(
                requireContext(), profileManager, stripProfileManager, selectedStripDetailProfile.id,
                getTargetOs());
        stripCatalogGridAdapter.setItems(gridItems);
        stripCatalogGridAdapter.setOnStripCatalogCellClickListener(this::onStripCatalogSlotCellClicked);
        hubStripCatalogRecycler.setAdapter(stripCatalogGridAdapter);
        if (stripProfileManager != null && selectedStripDetailProfile != null) {
            stripCatalogSlotTouchHelper = new ItemTouchHelper(
                    new StripCatalogSlotSwapCallback(stripProfileManager, selectedStripDetailProfile.id));
            stripCatalogSlotTouchHelper.attachToRecyclerView(hubStripCatalogRecycler);
            stripCatalogGridAdapter.setStripSlotItemTouchHelper(stripCatalogSlotTouchHelper);
            // ItemTouchHelper often owns the gesture stream; OnItemTouchListener may miss MOVE.
            // OnTouchListener on the RecyclerView runs first for events dispatched here and sees drags.
            stripCatalogPointerTouchListener = (v, event) -> {
                stripCatalogPointerRvX = event.getX();
                stripCatalogPointerRvY = event.getY();
                return false;
            };
            hubStripCatalogRecycler.setOnTouchListener(stripCatalogPointerTouchListener);
        } else {
            stripCatalogGridAdapter.setStripSlotItemTouchHelper(null);
        }
    }

    private void showCreateStripProfileDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.shortcut_hub_strip_enter_name);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.shortcut_hub_create_strip_profile)
                .setView(input)
                .setPositiveButton(R.string.shortcut_hub_create_profile, (d, which) -> {
                    String name = input.getText() != null ? input.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.shortcut_hub_toast_enter_profile_name, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    stripProfileManager.createProfile(name);
                    loadStripProfiles();
                    Toast.makeText(requireContext(), getString(R.string.shortcut_hub_toast_created_profile, name), Toast.LENGTH_SHORT).show();
                    notifyKeyboardStripRefresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onStripCatalogSlotCellClicked(
            @NonNull StripCatalogGridItem cell,
            @Nullable String shortcutId
    ) {
        if (selectedStripDetailProfile == null || cell.slotKey == null) {
            return;
        }
        showRows23SlotEditor(cell, shortcutId);
    }

    /**
     * Opens the full-screen Rows 2–3 strip slot editor (single HID key, name, icon, persistence key).
     */
    private void showRows23SlotEditor(@NonNull StripCatalogGridItem cell, @Nullable String shortcutId) {
        if (selectedStripDetailProfile == null || hubSlotEditorOverlay == null) {
            return;
        }
        String phy = cell.physicalLabel != null ? cell.physicalLabel : "";
        String ev = cell.keyEventLabel != null ? cell.keyEventLabel : "";
        Rows23SlotEditorFragment frag = Rows23SlotEditorFragment.newInstance(
                selectedStripDetailProfile.id,
                cell.slotKey,
                shortcutId,
                phy,
                ev,
                getTargetOs());
        hubSlotEditorOverlay.setVisibility(View.VISIBLE);
        getChildFragmentManager().beginTransaction()
                .replace(R.id.hub_slot_editor_overlay, frag)
                .addToBackStack("rows23_slot_editor")
                .commit();
    }

    private boolean tryPopRows23SlotEditor() {
        if (hubSlotEditorOverlay == null || hubSlotEditorOverlay.getVisibility() != View.VISIBLE) {
            return false;
        }
        if (getChildFragmentManager().findFragmentById(R.id.hub_slot_editor_overlay) != null) {
            getChildFragmentManager().popBackStack();
            return true;
        }
        hubSlotEditorOverlay.setVisibility(View.GONE);
        return false;
    }

    private void dismissRows23SlotEditorOverlay() {
        Fragment f = getChildFragmentManager().findFragmentById(R.id.hub_slot_editor_overlay);
        if (f != null) {
            getChildFragmentManager().beginTransaction().remove(f).commitAllowingStateLoss();
        }
        if (hubSlotEditorOverlay != null) {
            hubSlotEditorOverlay.setVisibility(View.GONE);
        }
    }

    /** Called by {@link Rows23SlotEditorFragment} after save or reset. */
    public void onRows23SlotEditorFinished() {
        afterStripProfileStorageChanged();
    }

    private void afterStripProfileStorageChanged() {
        if (stripProfileManager != null) {
            stripProfileManager.reloadFromStorage();
        }
        loadStripProfiles();
        if (selectedStripDetailProfile != null) {
            Rows23StripProfile updated = stripProfileManager.getProfileById(selectedStripDetailProfile.id);
            if (updated != null) {
                selectedStripDetailProfile = updated;
            }
            refreshStripCatalogForSelectedStrip();
        }
        notifyKeyboardStripRefresh();
    }

    private void showResetStripProfileDetailDialog() {
        if (selectedStripDetailProfile == null || stripProfileManager == null) {
            return;
        }
        Rows23StripProfile prof = selectedStripDetailProfile;
        String displayName = (prof.name != null && !prof.name.trim().isEmpty())
                ? prof.name.trim()
                : (prof.id != null ? prof.id : "");
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.shortcut_hub_strip_reset_profile_title)
                .setMessage(getString(R.string.shortcut_hub_strip_reset_profile_message, displayName))
                .setPositiveButton(R.string.shortcut_hub_strip_reset, (d, w) -> {
                    dismissRows23SlotEditorOverlay();
                    stripProfileManager.resetProfileToFactoryLayout(prof.id);
                    afterStripProfileStorageChanged();
                    Toast.makeText(
                            requireContext(),
                            R.string.shortcut_hub_strip_reset_profile_toast,
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void shareStripProfileJson(@NonNull Rows23StripProfile profile) {
        String json = stripProfileManager.exportProfileToJson(profile.id);
        if (json == null) {
            Toast.makeText(requireContext(), R.string.shortcut_hub_toast_export_failed_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // Must match <cache-path name="share" path="share/" /> in res/xml/file_paths.xml
            File shareDir = new File(requireContext().getCacheDir(), "share");
            if (!shareDir.isDirectory() && !shareDir.mkdirs()) {
                Toast.makeText(requireContext(), R.string.shortcut_hub_toast_export_failed_generic, Toast.LENGTH_SHORT).show();
                return;
            }
            String safeId = profile.id != null
                    ? profile.id.replaceAll("[^a-zA-Z0-9]", "_")
                    : "strip";
            File outFile = new File(shareDir, "keymod_rows23_strip_" + safeId + "_" + System.currentTimeMillis() + ".json");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", outFile);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.putExtra(Intent.EXTRA_SUBJECT, profile.name != null ? profile.name : profile.id);
            share.setClipData(ClipData.newUri(requireContext().getContentResolver(),
                    getString(R.string.app_name), uri));
            startActivity(Intent.createChooser(share, getString(R.string.shortcut_hub_share_strip_profile_chooser)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.shortcut_hub_toast_export_failed_generic, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "share strip: " + e.getMessage());
            Toast.makeText(requireContext(), getString(R.string.shortcut_hub_toast_export_failed_detail, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    private String getStripImportTargetProfileId() {
        if (selectedStripDetailProfile != null && panelStripProfileDetail != null
                && panelStripProfileDetail.getVisibility() == View.VISIBLE) {
            return selectedStripDetailProfile.id;
        }
        return stripProfileManager.getActiveProfileId();
    }

    private void importRows23StripProfileFromJson(@NonNull String json) {
        String err = stripProfileManager.importDocumentIntoProfile(getStripImportTargetProfileId(), json);
        if (err == null) {
            loadStripProfiles();
            if (selectedStripDetailProfile != null) {
                Rows23StripProfile u = stripProfileManager.getProfileById(selectedStripDetailProfile.id);
                if (u != null) {
                    selectedStripDetailProfile = u;
                    refreshStripCatalogForSelectedStrip();
                }
            }
            Toast.makeText(getContext(), R.string.shortcut_hub_strip_import_ok, Toast.LENGTH_SHORT).show();
            notifyKeyboardStripRefresh();
        } else {
            Toast.makeText(getContext(), getString(R.string.shortcut_hub_strip_import_failed, err), Toast.LENGTH_LONG).show();
        }
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

    private void refreshShortcutsGrid() {
        if (selectedProfile == null) return;

        myShortcutsList.clear();
        myShortcutsList.addAll(profileManager.getMyShortcuts(selectedProfile.id));

        boolean hasCategories = selectedProfile.categories != null && !selectedProfile.categories.isEmpty();

        List<ShortcutProfileManager.Shortcut> toShow = new ArrayList<>();

        // Priority 1: Show category shortcuts (not when Favorites tab is active)
        if (currentCategoryId != null && hasCategories && !TAB_MY.equals(currentTab)) {
            detachMyShortcutsReorderTouchHelper();
            detachBrowseCategoryReorderTouchHelper();
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

            ItemTouchHelper.Callback categoryCallback = new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView,
                        @NonNull RecyclerView.ViewHolder viewHolder,
                        @NonNull RecyclerView.ViewHolder target) {
                    ShortcutSectionPickAdapter adapter = (ShortcutSectionPickAdapter) recyclerView.getAdapter();
                    if (adapter == null || selectedProfile == null || currentCategoryId == null) {
                        return false;
                    }
                    int from = viewHolder.getBindingAdapterPosition();
                    int to = target.getBindingAdapterPosition();
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                        return false;
                    }
                    adapter.moveItem(from, to);
                    profileManager.reorderCategoryShortcuts(
                            selectedProfile.id,
                            currentCategoryId,
                            new ArrayList<>(adapter.getItems()),
                            false);
                    ShortcutProfile refreshed = profileManager.getProfileById(selectedProfile.id);
                    if (refreshed != null) {
                        selectedProfile = refreshed;
                    }
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
            browseCategoryReorderTouchHelper = new ItemTouchHelper(categoryCallback);
            browseCategoryReorderTouchHelper.attachToRecyclerView(browseShortcutsRecyclerView);
            browsePickAdapter.setDragHelper(browseCategoryReorderTouchHelper);
            return;
        }
        // Priority 2: Show My Shortcuts favorites (drag-reorder list; top strip uses same order)
        if (TAB_MY.equals(currentTab)) {
            detachBrowseCategoryReorderTouchHelper();
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
        detachBrowseCategoryReorderTouchHelper();
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

    private void detachBrowseCategoryReorderTouchHelper() {
        if (browseCategoryReorderTouchHelper != null) {
            browseCategoryReorderTouchHelper.attachToRecyclerView(null);
            browseCategoryReorderTouchHelper = null;
        }
        if (browsePickAdapter != null) {
            browsePickAdapter.setDragHelper(null);
        }
    }

    private void pulseShortcutRowFeedback(@NonNull View rowContent) {
        rowContent.animate().cancel();
        Object prevAnim = rowContent.getTag(R.id.tag_shortcut_hub_row_flash_animator);
        if (prevAnim instanceof ValueAnimator) {
            ((ValueAnimator) prevAnim).cancel();
        }

        rowContent.setScaleX(1f);
        rowContent.setScaleY(1f);
        rowContent.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(50)
                .withEndAction(() -> rowContent.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start());

        int primary = MaterialColors.getColor(
                rowContent,
                com.google.android.material.R.attr.colorPrimary,
                0xFFFF9800);
        int transparent = ColorUtils.setAlphaComponent(primary, 0);
        int peak = ColorUtils.setAlphaComponent(primary, 96);

        Drawable previousForeground = rowContent.getForeground();
        GradientDrawable highlight = new GradientDrawable();
        highlight.setShape(GradientDrawable.RECTANGLE);
        float cornerPx = 8f * rowContent.getResources().getDisplayMetrics().density;
        highlight.setCornerRadius(cornerPx);
        highlight.setColor(transparent);
        rowContent.setForeground(highlight);

        ValueAnimator flash = ValueAnimator.ofArgb(transparent, peak, transparent);
        flash.setDuration(220);
        flash.setInterpolator(new DecelerateInterpolator());
        flash.addUpdateListener(a -> highlight.setColor((Integer) a.getAnimatedValue()));
        flash.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                rowContent.setTag(R.id.tag_shortcut_hub_row_flash_animator, null);
                rowContent.setForeground(previousForeground);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                rowContent.setTag(R.id.tag_shortcut_hub_row_flash_animator, null);
                rowContent.setForeground(previousForeground);
            }
        });
        rowContent.setTag(R.id.tag_shortcut_hub_row_flash_animator, flash);
        flash.start();
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
                public void onRowClick(ShortcutProfileManager.Shortcut shortcut, @NonNull View rowContent) {
                    pulseShortcutRowFeedback(rowContent);
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

        myShortcutsReorderAdapter.setOnEditShortcutClickListener(this::openEditShortcutFromBrowse);
        myShortcutsReorderAdapter.setRemoveFavoriteClickListener((shortcut, position) -> {
            if (shortcut == null || shortcut.id == null || shortcut.id.isEmpty()) {
                return;
            }
            removeShortcutFromMyShortcuts(shortcut);
        });

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
        if (selectedProfile == null || shortcut == null || shortcut.id == null) {
            return;
        }
        for (ShortcutProfileManager.Shortcut s : myShortcutsList) {
            if (s != null && s.id != null && s.id.equals(shortcut.id)) {
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
                    if (!profileManager.appendCloneIfAbsent(myShortcutsList, shortcut)) {
                        Toast.makeText(requireContext(), R.string.my_shortcuts_already_in_my, Toast.LENGTH_SHORT).show();
                        refreshShortcutsGrid();
                        return;
                    }
                    profileManager.updateMyShortcuts(selectedProfile.id, new ArrayList<>(myShortcutsList));
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
                .setPositiveButton("Remove", (d, w) -> removeShortcutFromMyShortcuts(shortcut))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showShortcutActionsMenu(ShortcutProfileManager.Shortcut shortcut) {
        if (shortcut == null || selectedProfile == null) {
            return;
        }
        final boolean isFavorite = shortcut.id != null
                && myShortcutsList.stream().anyMatch(s -> s != null && s.id != null && s.id.equals(shortcut.id));

        java.util.ArrayList<String> options = new java.util.ArrayList<>();
        options.add(getString(R.string.shortcut_hub_run_shortcut));
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
                            executeShortcut(shortcut);
                            break;
                        case 1: {
                            ShortcutProfileManager.Shortcut t = profileManager.findShortcutInProfile(
                                    selectedProfile, shortcut.id);
                            showEditShortcutDialog(t != null ? t : shortcut);
                            break;
                        }
                        case 2:
                            confirmDeleteShortcut(shortcut);
                            break;
                        case 3:
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

    private void openEditShortcutFromBrowse(ShortcutProfileManager.Shortcut shortcut) {
        if (selectedProfile == null || shortcut == null || shortcut.id == null) {
            return;
        }
        ShortcutProfileManager.Shortcut canonical = profileManager.findShortcutInProfile(
                selectedProfile, shortcut.id);
        showEditShortcutDialog(canonical != null ? canonical : shortcut);
    }

    private void showResetDefaultProfileDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.shortcut_hub_reset_default_title)
                .setMessage(R.string.shortcut_hub_reset_default_message)
                .setPositiveButton(R.string.shortcut_hub_reset_default, (d, w) -> {
                    profileManager.resetDefaultProfileAndFavoritesToFactory();
                    loadProfiles();
                    notifyKeyboardStripRefresh();
                    Toast.makeText(requireContext(), R.string.shortcut_hub_reset_default_toast, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditShortcutDialog(ShortcutProfileManager.Shortcut shortcut) {
        if (selectedProfile == null || shortcut == null) {
            return;
        }
        ShortcutProfileManager.Shortcut resolved = profileManager.findShortcutInProfile(
                selectedProfile, shortcut.id);
        final ShortcutProfileManager.Shortcut editTarget = resolved != null ? resolved : shortcut;
        if (!(requireActivity() instanceof AppCompatActivity)) {
            Toast.makeText(requireContext(), R.string.create_shortcut_no_profile, Toast.LENGTH_SHORT).show();
            return;
        }
        AppCompatActivity act = (AppCompatActivity) requireActivity();
        CreateShortcutBottomSheet.showEdit(act, profileManager, selectedProfile.id, getTargetOs(), editTarget, () -> {
            loadProfiles();
            refreshSelectedProfileAndGrid();
        });
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
                    Toast.makeText(getContext(), getString(R.string.shortcut_hub_toast_deleted_name, shortcut.name), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(getContext(), R.string.shortcut_hub_toast_not_connected, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getContext(), getString(R.string.shortcut_hub_toast_created_profile, profile.name), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), R.string.shortcut_hub_toast_enter_profile_name, Toast.LENGTH_SHORT).show();
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
            .setTitle(ProfileUiStrings.displayName(requireContext(), profile))
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
        if (profile == null || profile.id == null) {
            return;
        }
        if (!(requireActivity() instanceof AppCompatActivity)) {
            Toast.makeText(requireContext(), R.string.create_shortcut_no_profile, Toast.LENGTH_SHORT).show();
            return;
        }
        AppCompatActivity act = (AppCompatActivity) requireActivity();

        boolean hasCategories = profile.categories != null && !profile.categories.isEmpty();
        boolean detailForThisProfile = selectedProfile != null
                && profile.id.equals(selectedProfile.id)
                && panelShortcutsDetail != null
                && panelShortcutsDetail.getVisibility() == View.VISIBLE;

        CreateShortcutBottomSheet.CreateMode mode;
        String categoryId = null;
        if (!hasCategories) {
            mode = CreateShortcutBottomSheet.CreateMode.GENERAL_AND_FAVORITES;
        } else if (detailForThisProfile && TAB_BROWSE.equals(currentTab) && currentCategoryId != null) {
            mode = CreateShortcutBottomSheet.CreateMode.CATEGORY_ONLY;
            categoryId = currentCategoryId;
        } else {
            mode = CreateShortcutBottomSheet.CreateMode.GENERAL_AND_FAVORITES;
        }

        CreateShortcutBottomSheet.show(act, profileManager, profile.id, getTargetOs(), mode, categoryId,
                this::hubAfterShortcutCreated);
    }

    /** After New shortcut sheet saves from Shortcut Hub (same UX as top-strip CREATE). */
    private void hubAfterShortcutCreated() {
        loadProfiles();
    }

    private void viewShortcuts(ShortcutProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append(ProfileUiStrings.displayName(requireContext(), profile)).append("\n\n");
        for (ShortcutProfileManager.Shortcut shortcut : profile.shortcuts) {
            sb.append(shortcut.name).append(": ").append(shortcut.label).append("\n");
        }
        
        new AlertDialog.Builder(requireContext())
            .setTitle(ProfileUiStrings.displayName(requireContext(), profile))
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    private void duplicateProfile(ShortcutProfile profile) {
        ShortcutProfile duplicate = profileManager.duplicateProfile(profile.id);
        if (duplicate != null) {
            loadProfiles();
            Toast.makeText(getContext(), getString(R.string.shortcut_hub_toast_duplicated_profile, duplicate.name), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportProfile(ShortcutProfile profile) {
        exportProfileToFile(profile);
    }

    private void shareProfileJson(ShortcutProfile profile) {
        if (profile == null || profile.id == null) {
            return;
        }
        String json = profileManager.exportProfile(profile.id);
        if (json == null) {
            Toast.makeText(getContext(), R.string.shortcut_hub_toast_export_failed_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String safeName = profile.name != null
                    ? profile.name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase()
                    : "profile";
            File shareDir = new File(requireContext().getCacheDir(), "share");
            if (!shareDir.isDirectory() && !shareDir.mkdirs()) {
                Toast.makeText(getContext(), R.string.shortcut_hub_toast_export_failed_generic, Toast.LENGTH_SHORT).show();
                return;
            }
            String filename = "keymod_profile_" + safeName + "_" + System.currentTimeMillis() + ".json";
            File outFile = new File(shareDir, filename);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", outFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newUri(requireContext().getContentResolver(),
                    getString(R.string.app_name), uri));
            startActivity(Intent.createChooser(intent, getString(R.string.shortcut_hub_share_profile_chooser)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(getContext(), R.string.shortcut_hub_toast_export_failed_generic, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Share profile failed: " + e.getMessage());
            Toast.makeText(getContext(), getString(R.string.shortcut_hub_toast_export_failed_detail, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void exportProfileToFile(ShortcutProfile profile) {
        String json = profileManager.exportProfile(profile.id);
        if (json == null) {
            Toast.makeText(getContext(), R.string.shortcut_hub_toast_export_failed_generic, Toast.LENGTH_SHORT).show();
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
            
            Toast.makeText(getContext(), getString(R.string.shortcut_hub_toast_saved_to_path, outputFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
            Log.d(TAG, "Exported profile to: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Export failed: " + e.getMessage());
            Toast.makeText(getContext(), getString(R.string.shortcut_hub_toast_export_failed_detail, e.getMessage()), Toast.LENGTH_LONG).show();
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
            openFilePicker(REQ_IMPORT_PROFILE_FILE);
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
                    Toast.makeText(getContext(), R.string.shortcut_hub_toast_paste_json_required, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openFilePicker(int requestCode) {
        pendingImportFileRequestCode = requestCode;
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
            startActivityForResult(intent, requestCode);
        }
    }

    private void importProfileFromJson(String json) {
        ShortcutProfileManager.ShortcutProfile profile = profileManager.importProfile(json);
        if (profile != null) {
            loadProfiles();
            Toast.makeText(getContext(), getString(R.string.shortcut_hub_toast_imported_profile, profile.name), Toast.LENGTH_SHORT).show();
            notifyKeyboardStripRefresh();
        } else {
            Toast.makeText(getContext(), R.string.shortcut_hub_toast_import_invalid_json, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openFilePicker(pendingImportFileRequestCode);
            } else {
                Toast.makeText(getContext(), R.string.shortcut_hub_toast_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            return;
        }
        android.net.Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQ_IMPORT_PROFILE_FILE) {
            importJsonFromUri(uri, false);
        } else if (requestCode == REQ_IMPORT_STRIP_FILE) {
            importJsonFromUri(uri, true);
        }
    }

    private void importJsonFromUri(android.net.Uri uri, boolean stripOnly) {
        try {
            java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                java.util.Scanner scanner = new java.util.Scanner(inputStream);
                scanner.useDelimiter("\\A");
                String json = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                inputStream.close();
                if (stripOnly) {
                    if (Rows23StripProfileDocument.looksLikeDocument(json)) {
                        importRows23StripProfileFromJson(json);
                    } else {
                        Toast.makeText(getContext(), R.string.shortcut_hub_toast_import_invalid_json, Toast.LENGTH_LONG).show();
                    }
                } else {
                    importProfileFromJson(json);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Import from URI failed: " + e.getMessage());
            Toast.makeText(getContext(), getString(R.string.shortcut_hub_toast_import_failed_detail, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void importProfileFromUri(android.net.Uri uri) {
        importJsonFromUri(uri, false);
    }

    private void notifyKeyboardStripRefresh() {
        Activity a = getActivity();
        if (a instanceof MainActivity) {
            ((MainActivity) a).refreshOpenKeyboardShortcutStripFromPrefs();
        }
    }

    private void showStripImportDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.shortcut_hub_import_strip_profile)
                .setMessage("Select import method:")
                .setPositiveButton("📋 Paste JSON", (dialog, which) -> showPasteStripJsonDialog())
                .setNegativeButton("📁 Browse Files", (dialog, which) -> openFilePicker(REQ_IMPORT_STRIP_FILE))
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void showPasteStripJsonDialog() {
        EditText input = new EditText(getContext());
        input.setHint(R.string.shortcut_hub_strip_paste_json_hint);
        input.setMinLines(5);
        input.setGravity(android.view.Gravity.TOP);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.shortcut_hub_import_strip_profile)
                .setView(input)
                .setPositiveButton(R.string.shortcut_hub_import, (dialog, which) -> {
                    String json = input.getText().toString().trim();
                    if (!json.isEmpty()) {
                        importRows23StripProfileFromJson(json);
                    } else {
                        Toast.makeText(getContext(), R.string.shortcut_hub_toast_paste_json_required, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteProfile(ShortcutProfile profile) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Delete Profile")
            .setMessage("Delete '" + profile.name + "'?")
            .setPositiveButton("Delete", (dialog, which) -> {
                profileManager.deleteProfile(profile.id);
                loadProfiles();
                Toast.makeText(getContext(), getString(R.string.shortcut_hub_toast_deleted_name, profile.name), Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updateActiveProfileDisplay() {
        if (activeProfile != null) {
            activeProfileText.setText(getString(R.string.shortcut_hub_active_profile,
                    ProfileUiStrings.displayName(requireContext(), activeProfile)));
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

    private static boolean stripSlotAssignmentNonEmpty(
            @Nullable Map<String, String> slotMap,
            @NonNull String slotKey
    ) {
        if (slotMap == null) {
            return false;
        }
        String v = slotMap.get(slotKey);
        return v != null && !v.trim().isEmpty();
    }

    /**
     * Drag-drop between two catalog cells swaps only those two strip {@code slotMap} entries
     * (e.g. Fn F7 slot with Base 8 slot), not whole columns. Printed factory caps stay fixed; assigned
     * shortcuts move between the two keys.
     */
    private final class StripCatalogSlotSwapCallback extends ItemTouchHelper.SimpleCallback {

        private final Rows23StripProfileManager manager;
        private final String profileId;
        private int dragSourcePos = RecyclerView.NO_POSITION;
        private int hoverTargetPos = RecyclerView.NO_POSITION;

        StripCatalogSlotSwapCallback(
                @NonNull Rows23StripProfileManager manager,
                @NonNull String profileId
        ) {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
            this.manager = manager;
            this.profileId = profileId;
        }

        @Nullable
        private StripCatalogGridAdapter adapter(@NonNull RecyclerView rv) {
            RecyclerView.Adapter<?> a = rv.getAdapter();
            return a instanceof StripCatalogGridAdapter ? (StripCatalogGridAdapter) a : null;
        }

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                dragSourcePos = viewHolder.getBindingAdapterPosition();
                hoverTargetPos = RecyclerView.NO_POSITION;
            }
            // Do not clear dragSourcePos / hoverTargetPos on ACTION_STATE_IDLE: ItemTouchHelper may call
            // onSelectedChanged(IDLE) before clearView(), which would make tryCommitSwap() a no-op.
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            // Read swap keys before resetting positions. Do not call afterStripProfileStorageChanged() (which
            // rebuilds this RecyclerView and detaches ItemTouchHelper) until after super.clearView(): doing
            // that synchronously here was leaving ItemTouchHelper mid-teardown and caused crashes.
            StripCatalogGridAdapter adForSnap = adapter(recyclerView);
            int fpSnap = dragSourcePos;
            if (adForSnap != null
                    && fpSnap != RecyclerView.NO_POSITION
                    && fpSnap < adForSnap.getItemCount()
                    && adForSnap.getItemViewType(fpSnap) == StripCatalogGridItem.VIEW_TYPE_SLOT_CELL) {
                float px = ShortcutHubFragment.this.stripCatalogPointerRvX;
                float py = ShortcutHubFragment.this.stripCatalogPointerRvY;
                if (Float.isNaN(px) || Float.isNaN(py)) {
                    View iv = viewHolder.itemView;
                    px = iv.getX() + iv.getWidth() / 2f;
                    py = iv.getY() + iv.getHeight() / 2f;
                }
                if (!snapHoverToFinger(recyclerView, adForSnap, viewHolder.itemView, fpSnap, px, py)
                        && !Float.isNaN(px) && !Float.isNaN(py)) {
                    float step = ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop() * 2.5f;
                    for (int k = 0; k < 8; k++) {
                        double ang = (Math.PI / 4d) * k;
                        if (snapHoverToFinger(recyclerView, adForSnap, viewHolder.itemView, fpSnap,
                                px + (float) (Math.cos(ang) * step),
                                py + (float) (Math.sin(ang) * step))) {
                            break;
                        }
                    }
                }
            }
            int fp = dragSourcePos;
            int tp = hoverTargetPos;
            StripCatalogGridAdapter ad = adapter(recyclerView);
            boolean startedFromSlot = ad != null
                    && fp != RecyclerView.NO_POSITION
                    && fp < ad.getItemCount()
                    && ad.getItemViewType(fp) == StripCatalogGridItem.VIEW_TYPE_SLOT_CELL;
            StripCatalogGridItem ca = null;
            StripCatalogGridItem cb = null;
            boolean willSwap = false;
            if (startedFromSlot
                    && ad != null
                    && tp != RecyclerView.NO_POSITION
                    && fp != tp
                    && tp < ad.getItemCount()
                    && ad.getItemViewType(tp) == StripCatalogGridItem.VIEW_TYPE_SLOT_CELL) {
                ca = ad.getSlotCellAt(fp);
                cb = ad.getSlotCellAt(tp);
                if (ca != null && cb != null && ca.slotKey != null && cb.slotKey != null
                        && !ca.slotKey.equals(cb.slotKey)) {
                    willSwap = true;
                }
            }
            dragSourcePos = RecyclerView.NO_POSITION;
            hoverTargetPos = RecyclerView.NO_POSITION;
            super.clearView(recyclerView, viewHolder);
            Rows23StripProfile prof = manager.getProfileById(profileId);
            Map<String, String> slotMap = prof != null && prof.slotMap != null
                    ? prof.slotMap
                    : Collections.emptyMap();
            if (willSwap && ca != null && cb != null) {
                boolean hadAny = stripSlotAssignmentNonEmpty(slotMap, ca.slotKey)
                        || stripSlotAssignmentNonEmpty(slotMap, cb.slotKey);
                manager.swapSlotAssignments(profileId, ca.slotKey, cb.slotKey);
                if (isAdded()) {
                    int msg = hadAny
                            ? R.string.shortcut_hub_strip_catalog_swap_success
                            : R.string.shortcut_hub_strip_catalog_swap_empty;
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                }
                recyclerView.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    afterStripProfileStorageChanged();
                });
            } else if (startedFromSlot && tp == RecyclerView.NO_POSITION && isAdded()) {
                Toast.makeText(
                        requireContext(),
                        R.string.shortcut_hub_strip_catalog_swap_need_target,
                        Toast.LENGTH_SHORT
                ).show();
            }
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            StripCatalogGridAdapter ad = adapter(recyclerView);
            if (ad == null) {
                return makeMovementFlags(0, 0);
            }
            int pos = viewHolder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION
                    || ad.getItemViewType(pos) != StripCatalogGridItem.VIEW_TYPE_SLOT_CELL) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        /**
         * Default 0.5f delays swap-target detection until the finger has moved half a cell; that
         * often prevents {@link #onMove} from ever running. Use a low threshold so
         * {@link #moveIfNecessary} runs, and we also resolve the hover slot in {@link #onChildDraw}.
         */
        @Override
        public float getMoveThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
            return 0.08f;
        }

        /**
         * Each draw frame, resolve which catalog cell sits under the drag (ahead of motion), so
         * {@link #clearView} has a valid {@link #hoverTargetPos} even when {@link #onMove} does not run.
         */
        @Override
        public void onChildDraw(
                @NonNull Canvas c,
                @NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder viewHolder,
                float dX,
                float dY,
                int actionState,
                boolean isCurrentlyActive
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            updateHoverTargetFromDragVisual(recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        private void updateHoverTargetFromDragVisual(
                @NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder dragged,
                float dX,
                float dY,
                int actionState,
                boolean isCurrentlyActive
        ) {
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) {
                return;
            }
            StripCatalogGridAdapter ad = adapter(recyclerView);
            if (ad == null) {
                return;
            }
            int sp = dragSourcePos;
            if (sp == RecyclerView.NO_POSITION) {
                sp = dragged.getBindingAdapterPosition();
            }
            if (sp == RecyclerView.NO_POSITION || sp < 0 || sp >= ad.getItemCount()) {
                return;
            }
            View v = dragged.itemView;
            float px = ShortcutHubFragment.this.stripCatalogPointerRvX;
            float py = ShortcutHubFragment.this.stripCatalogPointerRvY;
            if (Float.isNaN(px) || Float.isNaN(py)) {
                float mag = (float) Math.hypot(dX, dY);
                if (mag > 0.5f) {
                    float nx = dX / mag;
                    float ny = dY / mag;
                    px = v.getX() + v.getWidth() / 2f + nx * (v.getWidth() * 0.55f);
                    py = v.getY() + v.getHeight() / 2f + ny * (v.getHeight() * 0.55f);
                } else {
                    px = v.getX() + v.getWidth() * 0.85f;
                    py = v.getY() + v.getHeight() / 2f;
                }
            }
            if (!snapHoverToFinger(recyclerView, ad, v, sp, px, py)) {
                float step = ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop() * 2.5f;
                for (int k = 0; k < 8; k++) {
                    double ang = (Math.PI / 4d) * k;
                    if (snapHoverToFinger(recyclerView, ad, v, sp,
                            px + (float) (Math.cos(ang) * step),
                            py + (float) (Math.sin(ang) * step))) {
                        break;
                    }
                }
            }
        }

        /**
         * {@link RecyclerView#findChildViewUnder} returns the dragged row while it is elevated, so we
         * test the finger against each visible row's decorated bounds instead (skipping the source row).
         *
         * @return true if a valid drop target was found
         */
        private boolean snapHoverToFinger(
                @NonNull RecyclerView recyclerView,
                @NonNull StripCatalogGridAdapter ad,
                @NonNull View draggedItemView,
                int sourceAdapterPos,
                float px,
                float py
        ) {
            hoverTargetPos = RecyclerView.NO_POSITION;
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            if (lm == null) {
                return false;
            }
            StripCatalogGridItem src = ad.getSlotCellAt(sourceAdapterPos);
            if (src == null || src.slotKey == null) {
                return false;
            }
            float slop = ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop();
            int bestPos = RecyclerView.NO_POSITION;
            float bestDistSq = Float.MAX_VALUE;
            for (int i = 0; i < lm.getChildCount(); i++) {
                View child = lm.getChildAt(i);
                if (child == draggedItemView) {
                    continue;
                }
                RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(child);
                int tp = vh.getBindingAdapterPosition();
                if (tp == RecyclerView.NO_POSITION || tp < 0 || tp >= ad.getItemCount()) {
                    continue;
                }
                if (ad.getItemViewType(tp) != StripCatalogGridItem.VIEW_TYPE_SLOT_CELL) {
                    continue;
                }
                Rect r = new Rect();
                lm.getDecoratedBoundsWithMargins(child, r);
                r.offset((int) child.getTranslationX(), (int) child.getTranslationY());
                RectF rf = new RectF(r);
                rf.inset(-slop, -slop);
                if (!rf.contains(px, py)) {
                    continue;
                }
                StripCatalogGridItem dst = ad.getSlotCellAt(tp);
                if (dst == null || dst.slotKey == null || dst.slotKey.equals(src.slotKey)) {
                    continue;
                }
                float cx = rf.centerX();
                float cy = rf.centerY();
                float d = (px - cx) * (px - cx) + (py - cy) * (py - cy);
                if (d < bestDistSq) {
                    bestDistSq = d;
                    bestPos = tp;
                }
            }
            if (bestPos != RecyclerView.NO_POSITION) {
                hoverTargetPos = bestPos;
                return true;
            }
            return false;
        }

        @Override
        public boolean canDropOver(
                @NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder current,
                @NonNull RecyclerView.ViewHolder target
        ) {
            StripCatalogGridAdapter ad = adapter(recyclerView);
            if (ad == null) {
                return false;
            }
            int fp = current.getBindingAdapterPosition();
            int tp = target.getBindingAdapterPosition();
            if (fp == RecyclerView.NO_POSITION || tp == RecyclerView.NO_POSITION) {
                return false;
            }
            return ad.getItemViewType(fp) == StripCatalogGridItem.VIEW_TYPE_SLOT_CELL
                    && ad.getItemViewType(tp) == StripCatalogGridItem.VIEW_TYPE_SLOT_CELL;
        }

        @Override
        public boolean onMove(
                @NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder viewHolder,
                @NonNull RecyclerView.ViewHolder target
        ) {
            // Drop target is tracked in onChildDraw (runs every frame); adapter data does not reorder.
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        }
    }

    private final class StripProfilesRecyclerAdapter extends RecyclerView.Adapter<StripProfilesRecyclerAdapter.VH> {

        private String activeStripId;

        void setActiveStripId(String id) {
            this.activeStripId = id;
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
            Rows23StripProfile p = stripProfilesList.get(position);
            holder.name.setText(p.name != null ? p.name : p.id);
            int n = p.slotMap != null ? p.slotMap.size() : 0;
            if (n == 0) {
                holder.desc.setText(R.string.shortcut_hub_strip_profile_factory_layout);
            } else {
                holder.desc.setText(getString(R.string.shortcut_hub_strip_profile_slot_count, n));
            }
            boolean isActive = p.id != null && p.id.equals(activeStripId);
            holder.activeIndicator.setVisibility(isActive ? View.VISIBLE : View.GONE);
            holder.activeBadge.setVisibility(isActive ? View.VISIBLE : View.GONE);
            holder.count.setVisibility(View.GONE);

            holder.profileShareButton.setOnClickListener(v -> shareStripProfileJson(p));

            holder.itemView.setOnClickListener(v -> showStripProfileDetail(p));
            holder.itemView.setOnLongClickListener(v -> {
                showStripProfileOptionsDialog(p);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return stripProfilesList.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final View activeIndicator;
            final TextView name;
            final TextView desc;
            final TextView activeBadge;
            final TextView count;
            final ImageView profileShareButton;

            VH(@NonNull View itemView) {
                super(itemView);
                activeIndicator = itemView.findViewById(R.id.profile_active_indicator);
                name = itemView.findViewById(R.id.profile_name);
                desc = itemView.findViewById(R.id.profile_description);
                activeBadge = itemView.findViewById(R.id.profile_active_badge);
                count = itemView.findViewById(R.id.profile_count);
                profileShareButton = itemView.findViewById(R.id.profile_share_button);
            }
        }
    }

    private void showStripProfileOptionsDialog(@NonNull Rows23StripProfile profile) {
        List<CharSequence> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.shortcut_hub_strip_set_active));
        actions.add(() -> {
            stripProfileManager.setActiveProfileId(profile.id);
            loadStripProfiles();
            notifyKeyboardStripRefresh();
        });
        if (!Rows23StripProfileConstants.DEFAULT_PROFILE_ID.equals(profile.id)) {
            labels.add(getString(R.string.macros_action_delete));
            actions.add(() -> new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.shortcut_hub_strip_delete_profile_title)
                    .setMessage(profile.name != null ? profile.name : profile.id)
                    .setPositiveButton(R.string.macros_action_delete, (d, w) -> {
                        stripProfileManager.deleteProfile(profile.id);
                        if (selectedStripDetailProfile != null
                                && profile.id.equals(selectedStripDetailProfile.id)) {
                            showStripProfileList();
                        }
                        loadStripProfiles();
                        notifyKeyboardStripRefresh();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(profile.name != null ? profile.name : profile.id)
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
            holder.name.setText(ProfileUiStrings.displayName(holder.itemView.getContext(), profile));
            holder.desc.setText(ProfileUiStrings.displayDescription(holder.itemView.getContext(), profile));
            boolean isActive = profile.id != null && profile.id.equals(activeProfileId);
            holder.activeIndicator.setVisibility(isActive ? View.VISIBLE : View.GONE);
            holder.activeBadge.setVisibility(isActive ? View.VISIBLE : View.GONE);
            holder.count.setText(getString(R.string.shortcut_hub_profile_shortcut_count, profile.getShortcutCount()));

            holder.profileShareButton.setOnClickListener(v -> shareProfileJson(profile));

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
            final ImageView profileShareButton;

            VH(@NonNull View itemView) {
                super(itemView);
                activeIndicator = itemView.findViewById(R.id.profile_active_indicator);
                name = itemView.findViewById(R.id.profile_name);
                desc = itemView.findViewById(R.id.profile_description);
                activeBadge = itemView.findViewById(R.id.profile_active_badge);
                count = itemView.findViewById(R.id.profile_count);
                profileShareButton = itemView.findViewById(R.id.profile_share_button);
            }
        }
    }
}
