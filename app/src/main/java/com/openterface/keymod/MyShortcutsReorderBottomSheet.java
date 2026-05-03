package com.openterface.keymod;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.openterface.keymod.util.MyShortcutsReorderHelpReadModeDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom sheet: scrollable section tabs (Favorites + profile categories or All), drag-reorder list,
 * bookmark control on browse rows to stage copies in Favorites or remove them from the staged list; Done persists once.
 */
public final class MyShortcutsReorderBottomSheet {

    private MyShortcutsReorderBottomSheet() {
    }

    private static final class BrowsePage {
        final String title;
        final String categoryId;
        final List<ShortcutProfileManager.Shortcut> shortcuts;

        BrowsePage(String title, String categoryId, List<ShortcutProfileManager.Shortcut> shortcuts) {
            this.title = title;
            this.categoryId = categoryId;
            this.shortcuts = shortcuts;
        }
    }

    private static List<BrowsePage> buildBrowsePages(
            AppCompatActivity activity,
            ShortcutProfileManager.ShortcutProfile ap
    ) {
        List<BrowsePage> out = new ArrayList<>();
        if (ap.categories != null && !ap.categories.isEmpty()) {
            for (ShortcutProfileManager.ShortcutCategory cat : ap.categories) {
                if (cat == null || cat.shortcuts == null || cat.shortcuts.isEmpty()) {
                    continue;
                }
                String label = cat.name != null && !cat.name.trim().isEmpty() ? cat.name.trim() : cat.id;
                if ("general".equals(cat.id)) {
                    label = activity.getString(R.string.my_shortcuts_tab_general);
                }
                out.add(new BrowsePage(label, cat.id, new ArrayList<>(cat.shortcuts)));
            }
        } else {
            List<ShortcutProfileManager.Shortcut> flat = ap.getAllShortcutsFlat();
            if (flat != null && !flat.isEmpty()) {
                out.add(new BrowsePage(
                        activity.getString(R.string.my_shortcuts_tab_all),
                        null,
                        new ArrayList<>(flat)));
            }
        }
        return out;
    }

    /** Removes first shortcut with matching id from the working list (staged My). */
    private static boolean removeShortcutFromWorkingListById(
            @NonNull List<ShortcutProfileManager.Shortcut> working,
            @Nullable String shortcutId
    ) {
        if (shortcutId == null || shortcutId.isEmpty()) {
            return false;
        }
        for (int i = working.size() - 1; i >= 0; i--) {
            ShortcutProfileManager.Shortcut s = working.get(i);
            if (s != null && shortcutId.equals(s.id)) {
                working.remove(i);
                return true;
            }
        }
        return false;
    }

    public static void show(
            @NonNull AppCompatActivity activity,
            @NonNull ShortcutProfileManager profileManager,
            @NonNull String profileId,
            @NonNull String targetOs,
            int scrollToIndex,
            @Nullable Runnable onSaved
    ) {
        ShortcutProfileManager.ShortcutProfile ap = profileManager.getProfileById(profileId);
        if (ap == null) {
            return;
        }
        final List<ShortcutProfileManager.Shortcut> data = new ArrayList<>(
                profileManager.getOrderedShortcutsForTopStrip(profileId));
        final List<BrowsePage> browsePages = new ArrayList<>(buildBrowsePages(activity, ap));
        if (data.isEmpty() && browsePages.isEmpty()) {
            Toast.makeText(activity, R.string.my_shortcuts_sheet_empty_profile, Toast.LENGTH_SHORT).show();
            return;
        }

        View root = activity.getLayoutInflater().inflate(R.layout.bottomsheet_my_shortcuts_reorder, null, false);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(root);

        TextView emptyMyHint = root.findViewById(R.id.reorder_my_empty_hint);
        TabLayout tabLayout = root.findViewById(R.id.reorder_tab_layout);
        ImageButton infoButton = root.findViewById(R.id.reorder_sheet_info);
        String profileTitleRaw = ap.name != null ? ap.name.trim() : "";
        final String profileTitleForHelp = profileTitleRaw.isEmpty() ? "—" : profileTitleRaw;

        RecyclerView recycler = root.findViewById(R.id.reorder_recycler);
        recycler.setFocusable(false);
        recycler.setFocusableInTouchMode(false);
        int listHeightPx = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.52f);
        ViewGroup.LayoutParams lpRv = recycler.getLayoutParams();
        if (lpRv instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lpRv).height = listHeightPx;
            recycler.setLayoutParams(lpRv);
        } else {
            recycler.setMinimumHeight(listHeightPx);
        }
        recycler.setNestedScrollingEnabled(true);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        NestedScrollView containerScroll = root instanceof NestedScrollView ? (NestedScrollView) root : null;

        Runnable anchorListsToTop = () -> {
            if (containerScroll != null) {
                containerScroll.scrollTo(0, 0);
                containerScroll.post(() -> containerScroll.scrollTo(0, 0));
            }
            recycler.stopScroll();
            RecyclerView.LayoutManager lm = recycler.getLayoutManager();
            if (lm instanceof LinearLayoutManager) {
                ((LinearLayoutManager) lm).scrollToPositionWithOffset(0, 0);
                recycler.post(() -> {
                    RecyclerView.LayoutManager innerLm = recycler.getLayoutManager();
                    if (innerLm instanceof LinearLayoutManager) {
                        ((LinearLayoutManager) innerLm).scrollToPositionWithOffset(0, 0);
                    }
                });
            }
        };

        if (infoButton != null) {
            infoButton.setOnClickListener(v -> MyShortcutsReorderHelpReadModeDialog.show(
                    activity,
                    MyShortcutsReorderHelpReadModeDialog.formatHelpText(
                            activity, profileTitleForHelp, data.size())));
        }

        MyShortcutsReorderAdapter myAdapter = new MyShortcutsReorderAdapter(activity, targetOs, data);
        ShortcutSectionPickAdapter pickAdapter = new ShortcutSectionPickAdapter(activity, targetOs, new ArrayList<>());

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false;
                }
                myAdapter.moveItem(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }
        });
        myAdapter.setDragHelper(touchHelper);

        Runnable updateEmptyHint = () -> {
            int idx = tabLayout.getSelectedTabPosition();
            emptyMyHint.setVisibility(
                    idx == 0 && myAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        };

        final String[] selectedCategoryId = new String[]{null};

        Runnable refreshDataFromManager = () -> {
            data.clear();
            data.addAll(profileManager.getOrderedShortcutsForTopStrip(profileId));
            ShortcutProfileManager.ShortcutProfile p = profileManager.getProfileById(profileId);
            browsePages.clear();
            if (p != null) {
                browsePages.addAll(buildBrowsePages(activity, p));
            }
            tabLayout.removeAllTabs();
            tabLayout.addTab(tabLayout.newTab().setText(R.string.my_shortcuts_tab_favorites));
            for (BrowsePage page : browsePages) {
                tabLayout.addTab(tabLayout.newTab().setText(page.title));
            }
            int current = tabLayout.getSelectedTabPosition();
            if (current < 0) {
                current = 0;
            }
            int safe = Math.min(current, Math.max(0, tabLayout.getTabCount() - 1));
            TabLayout.Tab target = tabLayout.getTabAt(safe);
            if (target != null) {
                target.select();
            }
        };

        myAdapter.setRemoveFavoriteClickListener((shortcut, position) -> {
            if (shortcut == null || shortcut.id == null || shortcut.id.isEmpty()) {
                return;
            }
            if (removeShortcutFromWorkingListById(data, shortcut.id)) {
                myAdapter.notifyDataSetChanged();
                pickAdapter.notifyDataSetChanged();
                updateEmptyHint.run();
                Toast.makeText(activity, R.string.my_shortcuts_removed_from_my, Toast.LENGTH_SHORT).show();
            }
        });

        Runnable selectMyTab = () -> {
            touchHelper.attachToRecyclerView(null);
            recycler.setAdapter(myAdapter);
            myAdapter.setDragHelper(touchHelper);
            touchHelper.attachToRecyclerView(recycler);
            myAdapter.notifyDataSetChanged();
            anchorListsToTop.run();
            updateEmptyHint.run();
        };

        Runnable selectBrowseTab = () -> {
            int idx = tabLayout.getSelectedTabPosition();
            if (idx <= 0) {
                return;
            }
            touchHelper.attachToRecyclerView(null);
            int pageIndex = idx - 1;
            if (pageIndex < 0 || pageIndex >= browsePages.size()) {
                return;
            }
            BrowsePage page = browsePages.get(pageIndex);
            selectedCategoryId[0] = page.categoryId;
            pickAdapter.setItems(page.shortcuts);
            recycler.setAdapter(pickAdapter);
            anchorListsToTop.run();
            updateEmptyHint.run();
        };

        pickAdapter.setFavoriteMembershipChecker(s -> {
            if (s == null || s.id == null || s.id.isEmpty()) {
                return false;
            }
            for (ShortcutProfileManager.Shortcut x : data) {
                if (x != null && s.id.equals(x.id)) {
                    return true;
                }
            }
            return false;
        });

        pickAdapter.setBookmarkListener(new ShortcutSectionPickAdapter.OnBookmarkActionListener() {
            @Override
            public void onAddToFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut) {
                if (profileManager.appendCloneIfAbsent(data, shortcut)) {
                    myAdapter.notifyDataSetChanged();
                    pickAdapter.notifyDataSetChanged();
                    Toast.makeText(activity, R.string.my_shortcuts_added_to_my, Toast.LENGTH_SHORT).show();
                } else {
                    pickAdapter.notifyDataSetChanged();
                    Toast.makeText(activity, R.string.my_shortcuts_already_in_my, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRemoveFromFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut) {
                if (removeShortcutFromWorkingListById(data, shortcut.id)) {
                    myAdapter.notifyDataSetChanged();
                    pickAdapter.notifyDataSetChanged();
                    Toast.makeText(activity, R.string.my_shortcuts_removed_from_my, Toast.LENGTH_SHORT).show();
                }
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int idx = tab.getPosition();
                if (idx == 0) {
                    selectedCategoryId[0] = null;
                    selectMyTab.run();
                } else {
                    selectBrowseTab.run();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        refreshDataFromManager.run();

        recycler.post(anchorListsToTop);

        Button cancel = root.findViewById(R.id.reorder_cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());

        Button create = root.findViewById(R.id.reorder_create);
        create.setOnClickListener(v -> {
            int idx = tabLayout.getSelectedTabPosition();
            CreateShortcutBottomSheet.CreateMode mode =
                    idx == 0
                            ? CreateShortcutBottomSheet.CreateMode.GENERAL_AND_FAVORITES
                            : CreateShortcutBottomSheet.CreateMode.CATEGORY_ONLY;
            String categoryId = idx == 0 ? null : selectedCategoryId[0];
            CreateShortcutBottomSheet.show(
                    activity,
                    profileManager,
                    profileId,
                    targetOs,
                    mode,
                    categoryId,
                    () -> {
                        refreshDataFromManager.run();
                        if (onSaved != null) {
                            onSaved.run();
                        }
                    });
        });

        Button reset = root.findViewById(R.id.reorder_reset);
        boolean resetSupported = profileManager.isBuiltInProfileId(profileId);
        reset.setEnabled(resetSupported);
        reset.setAlpha(resetSupported ? 1f : 0.45f);
        reset.setOnClickListener(v -> {
            if (!profileManager.isBuiltInProfileId(profileId)) {
                Toast.makeText(activity, R.string.my_shortcuts_reorder_reset_unsupported, Toast.LENGTH_SHORT).show();
                return;
            }
            ShortcutProfileManager.ShortcutProfile p = profileManager.getProfileById(profileId);
            String profileName = p != null && p.name != null && !p.name.trim().isEmpty() ? p.name.trim() : profileId;
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.my_shortcuts_reorder_reset_confirm_title)
                    .setMessage(activity.getString(
                            R.string.my_shortcuts_reorder_reset_confirm_message, profileName))
                    .setPositiveButton(R.string.my_shortcuts_reorder_reset, (d, w) -> {
                        if (profileManager.resetMyShortcutsToDefaultForProfile(profileId)) {
                            refreshDataFromManager.run();
                            if (onSaved != null) {
                                onSaved.run();
                            }
                            Toast.makeText(activity, R.string.my_shortcuts_reorder_reset_success, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, R.string.my_shortcuts_reorder_reset_unsupported, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        Button done = root.findViewById(R.id.reorder_done);
        done.setOnClickListener(v -> {
            profileManager.reorderMyShortcuts(profileId, new ArrayList<>(myAdapter.getItems()));
            dialog.dismiss();
            if (onSaved != null) {
                onSaved.run();
            }
        });

        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            View cancelButton = root.findViewById(R.id.reorder_cancel);
            if (cancelButton != null) {
                cancelButton.requestFocus();
            }
            anchorListsToTop.run();
            recycler.post(anchorListsToTop);
        });

        dialog.show();
    }
}
