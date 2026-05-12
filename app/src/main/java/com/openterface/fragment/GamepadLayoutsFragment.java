package com.openterface.fragment;

import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import com.openterface.keymod.R;
import com.openterface.keymod.gamepad.GamepadLayoutPresetConstants;
import com.openterface.keymod.gamepad.GamepadLayoutPresetRepository;
import com.openterface.keymod.gamepad.GamepadPresetCardGridAdapter;

import java.util.List;

/**
 * Full-screen Layouts picker hosted as a child of {@link GamepadFragment}.
 */
public final class GamepadLayoutsFragment extends Fragment {

    @Nullable
    private GamepadFragment host() {
        Fragment p = getParentFragment();
        return p instanceof GamepadFragment ? (GamepadFragment) p : null;
    }

    private void closeSelf() {
        Fragment p = getParentFragment();
        if (p == null) {
            return;
        }
        FragmentManager fm = p.getChildFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gamepad_layouts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity()
                .getOnBackPressedDispatcher()
                .addCallback(
                        getViewLifecycleOwner(),
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                closeSelf();
                            }
                        });

        GamepadFragment h = host();
        if (h == null) {
            return;
        }

        Context ctx = requireContext();
        int sheetPad = ctx.getResources().getDimensionPixelSize(R.dimen.spacing_medium);
        view.setPadding(sheetPad, sheetPad, sheetPad, sheetPad);
        ViewCompat.setOnApplyWindowInsetsListener(
                view,
                (v, windowInsets) -> {
                    Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
                    v.setPadding(
                            sheetPad + bars.left,
                            sheetPad + Math.max(bars.top, cutout.top),
                            sheetPad + bars.right,
                            sheetPad + Math.max(bars.bottom, cutout.bottom));
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(view);

        RecyclerView recycler = view.findViewById(R.id.gamepad_presets_recycler);
        recycler.setHasFixedSize(true);
        recycler.setItemAnimator(null);
        recycler.setNestedScrollingEnabled(true);
        recycler.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        MaterialButton sheetBackBtn = view.findViewById(R.id.gamepad_presets_sheet_back);
        MaterialButton newLayoutBtn = view.findViewById(R.id.gamepad_presets_new_layout);
        MaterialButton resetShippedBtn = view.findViewById(R.id.gamepad_presets_reset_shipped_btn);
        MaterialButton importBtn = view.findViewById(R.id.gamepad_presets_import_btn);
        MaterialButton reorderBtn = view.findViewById(R.id.gamepad_presets_reorder);

        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        float density = dm.density;
        int spanBasisPx = dm.widthPixels;
        int screenWdp = (int) (spanBasisPx / Math.max(1f, density));
        int span = Math.max(2, Math.min(4, Math.round(screenWdp / 190f)));
        recycler.setLayoutManager(new GridLayoutManager(ctx, span));

        GamepadLayoutPresetRepository repo = h.getPresetRepository();
        final GamepadPresetCardGridAdapter[] presetListAdapterRef = new GamepadPresetCardGridAdapter[1];
        presetListAdapterRef[0] =
                new GamepadPresetCardGridAdapter(
                        new GamepadPresetCardGridAdapter.Listener() {
                            @Override
                            public void onPresetReorderFinished() {
                                List<String> ids = presetListAdapterRef[0].consumePendingReorderIds();
                                if (ids != null) {
                                    String err = repo.reorderPresets(ids);
                                    if (err != null) {
                                        refreshPresetSheetAdapter(presetListAdapterRef[0], h);
                                        Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                                    }
                                }
                            }

                            @Override
                            public void onActivatePreset(@NonNull String id) {
                                repo.persistActiveSnapshot();
                                String err = repo.activateAndApply(id);
                                if (err == null) {
                                    h.reloadFromPrefsAndApplyView();
                                    Toast.makeText(ctx, R.string.gamepad_presets_activated, Toast.LENGTH_SHORT)
                                            .show();
                                    closeSelf();
                                } else {
                                    Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                                }
                            }

                            @Override
                            public void onSavePreset(@NonNull String id) {
                                h.ensureExportCreatorNameThen(
                                        () -> {
                                            String displayName = h.presetDisplayName(id);
                                            String safe = displayName.replaceAll("[^a-zA-Z0-9_-]", "_");
                                            String base = !safe.isEmpty() ? safe : id;
                                            h.launchGamepadPresetSaveToDocument(
                                                    id, "KeyMod_gamepad_" + base + ".json");
                                        });
                            }

                            @Override
                            public void onSharePreset(@NonNull String id) {
                                h.ensureExportCreatorNameThen(
                                        () -> {
                                            closeSelf();
                                            h.sharePresetJson(id);
                                        });
                            }

                            @Override
                            public void onDeletePreset(@NonNull String id) {
                                confirmDeletePreset(id, presetListAdapterRef[0], h);
                            }

                            @Override
                            public void onOverflow(@NonNull String id, @NonNull View anchor) {
                                showPresetOverflowMenu(id, anchor, presetListAdapterRef[0], h);
                            }
                        },
                        repo,
                        h.previewCache());

        GamepadPresetCardGridAdapter adapter = presetListAdapterRef[0];
        recycler.setAdapter(adapter);
        refreshPresetSheetAdapter(adapter, h);
        if (reorderBtn != null) {
            reorderBtn.setChecked(false);
            reorderBtn.addOnCheckedChangeListener(
                    (btn, isChecked) -> presetListAdapterRef[0].setReorderMode(isChecked));
        }

        if (newLayoutBtn != null) {
            newLayoutBtn.setOnClickListener(
                    v -> {
                        closeSelf();
                        h.promptNewUserPreset();
                    });
        }
        if (resetShippedBtn != null) {
            resetShippedBtn.setOnClickListener(
                    v ->
                            new AlertDialog.Builder(ctx)
                                    .setTitle(R.string.gamepad_presets_reset_shipped_title)
                                    .setMessage(R.string.gamepad_presets_reset_shipped_message)
                                    .setPositiveButton(
                                            R.string.gamepad_presets_reset_shipped_confirm,
                                            (d, w) -> {
                                                String err = repo.resetAllShippedGamepadLayoutsFromAssets();
                                                if (err != null) {
                                                    Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                                                } else {
                                                    h.previewCache().invalidateAll();
                                                    Toast.makeText(
                                                                    ctx,
                                                                    R.string.gamepad_presets_reset_shipped_done,
                                                                    Toast.LENGTH_SHORT)
                                                            .show();
                                                    refreshPresetSheetAdapterAndResetListPresentation(
                                                            recycler, view, presetListAdapterRef[0], h);
                                                    h.reloadFromPrefsAndApplyView();
                                                    h.updateActivePresetNameUi();
                                                }
                                            })
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show());
        }
        if (importBtn != null) {
            importBtn.setOnClickListener(
                    v -> {
                        closeSelf();
                        h.launchGamepadPresetImport();
                    });
        }
        if (sheetBackBtn != null) {
            sheetBackBtn.setOnClickListener(v -> closeSelf());
        }

        recycler.post(
                () -> {
                    if (recycler.isAttachedToWindow()) {
                        recycler.requestFocus();
                    }
                });
    }

    private static void refreshPresetSheetAdapter(
            @NonNull GamepadPresetCardGridAdapter adapter, @NonNull GamepadFragment host) {
        adapter.setData(
                host.getPresetRepository().listPresets(), host.getPresetRepository().getActivePresetId());
    }

    private static void refreshPresetSheetAdapterAndResetListPresentation(
            @NonNull RecyclerView recycler,
            @NonNull View sheetRoot,
            @NonNull GamepadPresetCardGridAdapter adapter,
            @NonNull GamepadFragment host) {
        refreshPresetSheetAdapter(adapter, host);
        recycler.post(
                () ->
                        recycler.post(
                                () -> {
                                    recycler.stopScroll();
                                    RecyclerView.LayoutManager lm = recycler.getLayoutManager();
                                    if (lm instanceof GridLayoutManager) {
                                        ((GridLayoutManager) lm).scrollToPositionWithOffset(0, 0);
                                    } else if (lm instanceof LinearLayoutManager) {
                                        ((LinearLayoutManager) lm).scrollToPositionWithOffset(0, 0);
                                    } else {
                                        recycler.scrollToPosition(0);
                                    }
                                    recycler.requestLayout();
                                    sheetRoot.requestLayout();
                                }));
    }

    private void confirmDeletePreset(
            @NonNull String presetId,
            @NonNull GamepadPresetCardGridAdapter adapter,
            @NonNull GamepadFragment host) {
        if (GamepadLayoutPresetConstants.isPresetDeletionProtected(presetId)) {
            return;
        }
        Context ctx = requireContext();
        GamepadLayoutPresetRepository repo = host.getPresetRepository();
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.gamepad_preset_delete_title)
                .setMessage(R.string.gamepad_preset_delete_message)
                .setPositiveButton(
                        R.string.gamepad_preset_delete_confirm,
                        (d, w) -> {
                            String err = repo.deletePreset(presetId);
                            if (err != null) {
                                Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                                refreshPresetSheetAdapter(adapter, host);
                            } else {
                                host.previewCache().invalidatePreset(presetId);
                                closeSelf();
                                host.reloadFromPrefsAndApplyView();
                                host.updateActivePresetNameUi();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmResetDefaultPreset(
            @NonNull GamepadPresetCardGridAdapter adapter, @NonNull GamepadFragment host) {
        Context ctx = requireContext();
        GamepadLayoutPresetRepository repo = host.getPresetRepository();
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.gamepad_preset_reset_title)
                .setMessage(R.string.gamepad_preset_reset_message)
                .setPositiveButton(
                        R.string.gamepad_preset_reset_confirm,
                        (d, w) -> {
                            String err = repo.resetDefaultPresetFromBundled();
                            if (err != null) {
                                Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                            } else {
                                host.previewCache()
                                        .invalidatePreset(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID);
                                Toast.makeText(ctx, R.string.gamepad_preset_reset_done, Toast.LENGTH_SHORT)
                                        .show();
                                refreshPresetSheetAdapter(adapter, host);
                                if (GamepadLayoutPresetConstants.DEFAULT_PRESET_ID.equals(
                                        repo.getActivePresetId())) {
                                    host.reloadFromPrefsAndApplyView();
                                    host.updateActivePresetNameUi();
                                }
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showPresetOverflowMenu(
            @NonNull String presetId,
            @NonNull View anchor,
            @NonNull GamepadPresetCardGridAdapter adapter,
            @NonNull GamepadFragment host) {
        PopupMenu pm = new PopupMenu(anchor.getContext(), anchor);
        pm.getMenuInflater().inflate(R.menu.menu_gamepad_preset_row, pm.getMenu());
        if (GamepadLayoutPresetConstants.isPresetDeletionProtected(presetId)) {
            pm.getMenu().findItem(R.id.gamepad_preset_delete).setVisible(false);
        }
        if (!GamepadLayoutPresetConstants.DEFAULT_PRESET_ID.equals(presetId)) {
            pm.getMenu().findItem(R.id.gamepad_preset_reset_layout).setVisible(false);
        }
        pm.setOnMenuItemClickListener(
                item -> {
                    int id = item.getItemId();
                    if (id == R.id.gamepad_preset_rename) {
                        host.promptRenamePreset(
                                presetId,
                                () -> {
                                    refreshPresetSheetAdapter(adapter, host);
                                    host.updateActivePresetNameUi();
                                });
                        return true;
                    }
                    if (id == R.id.gamepad_preset_duplicate) {
                        GamepadLayoutPresetRepository.DuplicateResult dup =
                                host.getPresetRepository().duplicatePreset(presetId);
                        if (dup.isSuccess()) {
                            host.previewCache().invalidatePreset(presetId);
                            if (dup.newId != null) {
                                host.previewCache().invalidatePreset(dup.newId);
                            }
                            Toast.makeText(requireContext(), R.string.gamepad_preset_duplicated, Toast.LENGTH_SHORT)
                                    .show();
                            refreshPresetSheetAdapter(adapter, host);
                        } else {
                            Toast.makeText(
                                            requireContext(),
                                            dup.error != null
                                                    ? dup.error
                                                    : getString(R.string.gamepad_preset_action_failed),
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                        return true;
                    }
                    if (id == R.id.gamepad_preset_reset_layout) {
                        confirmResetDefaultPreset(adapter, host);
                        return true;
                    }
                    if (id == R.id.gamepad_preset_save_file) {
                        host.ensureExportCreatorNameThen(
                                () -> {
                                    String displayName = host.presetDisplayName(presetId);
                                    String safe = displayName.replaceAll("[^a-zA-Z0-9_-]", "_");
                                    String base = !safe.isEmpty() ? safe : presetId;
                                    host.launchGamepadPresetSaveToDocument(
                                            presetId, "KeyMod_gamepad_" + base + ".json");
                                });
                        return true;
                    }
                    if (id == R.id.gamepad_preset_share) {
                        host.ensureExportCreatorNameThen(
                                () -> {
                                    closeSelf();
                                    host.sharePresetJson(presetId);
                                });
                        return true;
                    }
                    if (id == R.id.gamepad_preset_delete) {
                        confirmDeletePreset(presetId, adapter, host);
                        return true;
                    }
                    return false;
                });
        pm.show();
    }
}
