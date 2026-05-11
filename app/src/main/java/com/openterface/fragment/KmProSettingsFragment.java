package com.openterface.fragment;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.preset.FixedStripLayoutCatalog;
import com.openterface.keymod.preset.Rows23StripProfile;
import com.openterface.keymod.preset.Rows23StripProfileConstants;
import com.openterface.keymod.preset.Rows23StripProfileDocument;
import com.openterface.keymod.preset.Rows23StripProfileManager;
import com.openterface.keymod.preset.StripCatalogGridAdapter;
import com.openterface.keymod.preset.StripCatalogGridItem;
import com.openterface.keymod.preset.StripSlotMapStore;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Keyboard & Mouse Pro setup: Rows 2–3 strip layouts and a General tab for future Pro preferences.
 */
public class KmProSettingsFragment extends Fragment implements Rows23SlotEditorHost {

    private static final String TAG = "KmProSettingsFragment";
    private static final int REQ_IMPORT_STRIP_FILE = 301;

    private ShortcutProfileManager profileManager;
    private Rows23StripProfileManager stripProfileManager;

    private ImageButton closeButton;
    private TabLayout kmProSettingsTabs;
    private LinearLayout kmProTabContentStrip;
    @Nullable
    private View kmProTabContentGeneral;
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
    private StripProfilesRecyclerAdapter stripProfilesRecyclerAdapter;
    private final List<Rows23StripProfile> stripProfilesList = new ArrayList<>();
    private Rows23StripProfile selectedStripDetailProfile;
    private OnBackPressedCallback rootBackCallback;
    private boolean suppressKmProTabSelection;

    private int pendingImportFileRequestCode = REQ_IMPORT_STRIP_FILE;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_km_pro_settings, container, false);
        profileManager = new ShortcutProfileManager(requireContext());
        stripProfileManager = new Rows23StripProfileManager(requireContext(), profileManager);
        initializeViews(view);
        setupListeners();
        loadStripProfiles();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (tryPopRows23SlotEditor()) {
                    return;
                }
                if (panelStripProfileDetail != null && panelStripProfileDetail.getVisibility() == View.VISIBLE) {
                    showStripProfileList();
                    return;
                }
                dismissKmProSettings();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), rootBackCallback);

        getChildFragmentManager().addOnBackStackChangedListener(() -> {
            if (hubSlotEditorOverlay != null
                    && getChildFragmentManager().findFragmentById(R.id.hub_slot_editor_overlay) == null) {
                hubSlotEditorOverlay.setVisibility(View.GONE);
            }
        });
    }

    private final MainActivity.OnTargetOsChangeListener osChangeListener = os -> {
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
        loadStripProfiles();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).removeOsChangeListener(osChangeListener);
        }
    }

    private void dismissKmProSettings() {
        Activity a = getActivity();
        if (a instanceof MainActivity) {
            ((MainActivity) a).hideKmProSettingsOverlay();
        }
    }

    private void initializeViews(View view) {
        closeButton = view.findViewById(R.id.km_pro_settings_close_button);
        kmProSettingsTabs = view.findViewById(R.id.km_pro_settings_tabs);
        kmProTabContentStrip = view.findViewById(R.id.km_pro_tab_content_strip);
        kmProTabContentGeneral = view.findViewById(R.id.km_pro_tab_content_general);
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

        stripProfilesRecyclerAdapter = new StripProfilesRecyclerAdapter();
        stripProfilesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        stripProfilesRecyclerView.setAdapter(stripProfilesRecyclerAdapter);
    }

    private void setupListeners() {
        closeButton.setOnClickListener(v -> dismissKmProSettings());
        createStripProfileButton.setOnClickListener(v -> showCreateStripProfileDialog());
        importStripProfileButton.setOnClickListener(v -> showStripImportDialog());
        stripDetailBackButton.setOnClickListener(v -> {
            if (!tryPopRows23SlotEditor()) {
                showStripProfileList();
            }
        });
        stripDetailResetButton.setOnClickListener(v -> showResetStripProfileDetailDialog());

        if (kmProSettingsTabs != null) {
            kmProSettingsTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (suppressKmProTabSelection) {
                        return;
                    }
                    applyKmProTabVisibility(tab.getPosition());
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                }
            });
            applyKmProTabVisibility(kmProSettingsTabs.getSelectedTabPosition());
        }
    }

    private void applyKmProTabVisibility(int position) {
        if (kmProTabContentStrip == null) {
            return;
        }
        if (position == 0) {
            kmProTabContentStrip.setVisibility(View.GONE);
            if (kmProTabContentGeneral != null) {
                kmProTabContentGeneral.setVisibility(View.VISIBLE);
            }
        } else {
            kmProTabContentStrip.setVisibility(View.VISIBLE);
            if (kmProTabContentGeneral != null) {
                kmProTabContentGeneral.setVisibility(View.GONE);
            }
            loadStripProfiles();
            if (selectedStripDetailProfile != null && panelStripProfileDetail != null
                    && panelStripProfileDetail.getVisibility() == View.VISIBLE) {
                refreshStripCatalogForSelectedStrip();
            }
        }
    }

    private String getTargetOs() {
        return requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .getString("target_os", "macos");
    }

    private void openFilePicker(int requestCode) {
        pendingImportFileRequestCode = requestCode;
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        } else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/json");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, requestCode);
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
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQ_IMPORT_STRIP_FILE) {
            importStripJsonFromUri(uri);
        }
    }

    private void importStripJsonFromUri(@NonNull Uri uri) {
        try {
            java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                java.util.Scanner scanner = new java.util.Scanner(inputStream);
                scanner.useDelimiter("\\A");
                String json = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                inputStream.close();
                if (Rows23StripProfileDocument.looksLikeDocument(json)) {
                    importRows23StripProfileFromJson(json);
                } else {
                    Toast.makeText(getContext(), R.string.shortcut_hub_toast_import_invalid_json, Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Import from URI failed: " + e.getMessage());
            Toast.makeText(getContext(), getString(R.string.shortcut_hub_toast_import_failed_detail, e.getMessage()), Toast.LENGTH_LONG).show();
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
    }

    /**
     * Clears strip detail selection; use {@link #showStripProfileList()} to leave fullscreen strip editor.
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
    }

    private void showStripProfileDetail(@NonNull Rows23StripProfile profile) {
        selectedStripDetailProfile = profile;
        if (panelStripProfileList != null) {
            panelStripProfileList.setVisibility(View.GONE);
        }
        if (panelStripProfileDetail != null) {
            panelStripProfileDetail.setVisibility(View.VISIBLE);
        }
        refreshStripCatalogForSelectedStrip();
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
    @Override
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
    private static boolean stripSlotAssignmentNonEmpty(
            @Nullable Map<String, String> slotMap,
            @NonNull String slotKey
    ) {
        if (slotMap == null) {
            return false;
        }
        String v = slotMap.get(slotKey);
        if (v != null && !v.trim().isEmpty()) {
            return true;
        }
        String canon = StripSlotMapStore.canonicalSlotKeyOrSelf(slotKey);
        if (!canon.equals(slotKey)) {
            v = slotMap.get(canon);
            return v != null && !v.trim().isEmpty();
        }
        return false;
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
                float px = KmProSettingsFragment.this.stripCatalogPointerRvX;
                float py = KmProSettingsFragment.this.stripCatalogPointerRvY;
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
            float px = KmProSettingsFragment.this.stripCatalogPointerRvX;
            float py = KmProSettingsFragment.this.stripCatalogPointerRvY;
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
        if (!Rows23StripProfileConstants.isBuiltInProfileId(profile.id)) {
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

}
