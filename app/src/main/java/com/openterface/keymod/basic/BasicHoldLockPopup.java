package com.openterface.keymod.basic;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.PopupWindow;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.openterface.keymod.AlternatePopupGeometry;
import com.openterface.keymod.R;

/**
 * Minimal 3×3 gesture popup for KM Basic hold-lock: only the {@link AlternatePopupGeometry#SLOT_UP}
 * cell is selectable (Material lock icon). Same geometry as Pro alternates.
 *
 * <p>Gesture deltas use {@code gestureOriginRaw*} from the finger position when the lock UI
 * appears (callers pass the latest raw coords at popup time — same moment as Pro shows alternates).
 * {@link #updatePointer} does not depend on {@link PopupWindow#isShowing()}.
 *
 * <p>Selection uses the same {@link AlternatePopupGeometry#pickSlot} dp constants as {@code
 * CustomKeyboardView} Pro alternates; upper corners map to {@link AlternatePopupGeometry#SLOT_UP}
 * when only that slot is occupied.
 */
public final class BasicHoldLockPopup {

    /** Same as {@link com.openterface.keymod.CustomKeyboardView#ALT_GESTURE_R_MIN_DP}. */
    private static final int ALT_GESTURE_R_MIN_DP = 12;
    private static final int ALT_GESTURE_R_CANCEL_DP = 228;
    /** Same as {@link com.openterface.keymod.CustomKeyboardView} alternate axis deadzone. */
    private static final int ALT_GESTURE_AXIS_DEADZONE_DP = 18;
    private static final int ALT_POPUP_VERTICAL_OFFSET_DP = 96;
    private static final int ALT_POPUP_CONTAINER_PADDING_DP = 6;
    private static final int ALT_POPUP_CELL_MIN_SIZE_DP = 40;

    private PopupWindow popupWindow;
    private View anchorView;
    /** Raw screen coordinates at lock-phase start (see {@link #show}). */
    private float gestureOriginRawX;
    private float gestureOriginRawY;
    private int currentPick = AlternatePopupGeometry.RESULT_DEFAULT;
    private int lastAppliedVisualPick = Integer.MIN_VALUE;
    private final boolean[] slotOccupied = new boolean[AlternatePopupGeometry.SLOT_COUNT];
    @Nullable private AppCompatImageView lockIconView;
    @Nullable private View popupContent;

    public BasicHoldLockPopup() {
        slotOccupied[AlternatePopupGeometry.SLOT_UP] = true;
    }

    /**
     * @param startRawX raw X when the lock phase starts (finger position as the popup appears)
     * @param startRawY raw Y at the same moment
     */
    public void show(View anchor, float startRawX, float startRawY) {
        dismiss();
        anchorView = anchor;
        // Must not name parameters like the fields — Java would shadow and self-assign (fields stay 0).
        this.gestureOriginRawX = startRawX;
        this.gestureOriginRawY = startRawY;
        currentPick = AlternatePopupGeometry.RESULT_DEFAULT;
        lastAppliedVisualPick = Integer.MIN_VALUE;
        Context context = anchor.getContext();
        float density = context.getResources().getDisplayMetrics().density;

        GridLayout grid = new GridLayout(context);
        popupContent = grid;
        grid.setColumnCount(1);
        grid.setRowCount(1);
        grid.setBackgroundResource(R.drawable.alternate_popup_background);
        int padPx = (int) (ALT_POPUP_CONTAINER_PADDING_DP * density + 0.5f);
        grid.setPadding(padPx, padPx, padPx, padPx);

        AppCompatImageView lock = new AppCompatImageView(context);
        lockIconView = lock;
        lock.setImageResource(R.drawable.ic_lock_24);
        lock.setContentDescription(context.getString(R.string.km_basic_hold_lock_popup_lock_cd));
        applyPickVisual(AlternatePopupGeometry.RESULT_DEFAULT);
        int minCell = (int) (ALT_POPUP_CELL_MIN_SIZE_DP * density + 0.5f);
        lock.setMinimumWidth(minCell);
        lock.setMinimumHeight(minCell);
        lock.setPadding(padPx, padPx, padPx, padPx);
        GridLayout.LayoutParams glp =
                new GridLayout.LayoutParams(GridLayout.spec(0), GridLayout.spec(0));
        glp.width = GridLayout.LayoutParams.WRAP_CONTENT;
        glp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        lock.setLayoutParams(glp);
        grid.addView(lock);

        popupWindow =
                new PopupWindow(
                        grid,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        false);
        // Let the gesture continue on the key/strip underneath; otherwise the popup sits above the
        // finger and steals MOVE/UP so pick/commit never updates (same as Pro alternates pattern).
        popupWindow.setTouchable(false);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setClippingEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupWindow.setElevation(12f * density);
        }
        grid.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        anchor.post(
                () -> {
                    if (anchorView == null || popupWindow == null) {
                        return;
                    }
                    int[] loc = new int[2];
                    anchor.getLocationOnScreen(loc);
                    int popupX =
                            (int)
                                    (loc[0]
                                            + (anchor.getWidth() / 2f)
                                            - (grid.getMeasuredWidth() / 2f));
                    int popupY =
                            loc[1]
                                    - (int)
                                            (ALT_POPUP_VERTICAL_OFFSET_DP * density
                                                    + 0.5f);
                    popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY);
                });
    }

    /**
     * Same gesture semantics as {@link #show(View, float, float)}, but positions the popup from raw screen
     * coordinates (for canvas controls such as the gamepad that have no per-key {@link View} anchor).
     *
     * @param host root used for {@link PopupWindow#showAtLocation}; typically the gamepad surface
     * @param anchorRawCenterX raw screen X of the gesture anchor (finger / control center)
     * @param anchorRawCenterY raw screen Y of the gesture anchor
     */
    public void showAboveScreenPoint(View host, float anchorRawCenterX, float anchorRawCenterY) {
        dismiss();
        anchorView = host;
        this.gestureOriginRawX = anchorRawCenterX;
        this.gestureOriginRawY = anchorRawCenterY;
        currentPick = AlternatePopupGeometry.RESULT_DEFAULT;
        lastAppliedVisualPick = Integer.MIN_VALUE;
        Context context = host.getContext();
        float density = context.getResources().getDisplayMetrics().density;

        GridLayout grid = new GridLayout(context);
        popupContent = grid;
        grid.setColumnCount(1);
        grid.setRowCount(1);
        grid.setBackgroundResource(R.drawable.alternate_popup_background);
        int padPx = (int) (ALT_POPUP_CONTAINER_PADDING_DP * density + 0.5f);
        grid.setPadding(padPx, padPx, padPx, padPx);

        AppCompatImageView lock = new AppCompatImageView(context);
        lockIconView = lock;
        lock.setImageResource(R.drawable.ic_lock_24);
        lock.setContentDescription(context.getString(R.string.km_basic_hold_lock_popup_lock_cd));
        applyPickVisual(AlternatePopupGeometry.RESULT_DEFAULT);
        int minCell = (int) (ALT_POPUP_CELL_MIN_SIZE_DP * density + 0.5f);
        lock.setMinimumWidth(minCell);
        lock.setMinimumHeight(minCell);
        lock.setPadding(padPx, padPx, padPx, padPx);
        GridLayout.LayoutParams glp =
                new GridLayout.LayoutParams(GridLayout.spec(0), GridLayout.spec(0));
        glp.width = GridLayout.LayoutParams.WRAP_CONTENT;
        glp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        lock.setLayoutParams(glp);
        grid.addView(lock);

        popupWindow =
                new PopupWindow(
                        grid,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        false);
        popupWindow.setTouchable(false);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setClippingEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupWindow.setElevation(12f * density);
        }
        grid.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        host.post(
                () -> {
                    if (anchorView == null || popupWindow == null) {
                        return;
                    }
                    int popupX =
                            (int) (anchorRawCenterX - (grid.getMeasuredWidth() / 2f));
                    int popupY =
                            (int)
                                    (anchorRawCenterY
                                            - (ALT_POPUP_VERTICAL_OFFSET_DP * density + 0.5f)
                                            - grid.getMeasuredHeight());
                    popupWindow.showAtLocation(host, Gravity.NO_GRAVITY, popupX, popupY);
                });
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    public void updatePointer(float rawX, float rawY) {
        if (anchorView == null) {
            return;
        }
        Context context = anchorView.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        float dx = rawX - gestureOriginRawX;
        float dy = rawY - gestureOriginRawY;
        float rMinPx = ALT_GESTURE_R_MIN_DP * density;
        float rCancelPx = ALT_GESTURE_R_CANCEL_DP * density;
        float axisDeadPx = ALT_GESTURE_AXIS_DEADZONE_DP * density;
        int pick =
                AlternatePopupGeometry.pickSlot(
                        dx, dy, rMinPx, rCancelPx, axisDeadPx, slotOccupied);
        currentPick = normalizeDiagonalUpToLock(pick);
        applyPickVisual(currentPick);
    }

    /** @return true if user committed the lock gesture (swipe to up slot). */
    public boolean commitIfLockSelected() {
        if (anchorView == null) {
            return false;
        }
        return currentPick == AlternatePopupGeometry.SLOT_UP;
    }

    public void dismiss() {
        if (popupWindow != null) {
            try {
                popupWindow.dismiss();
            } catch (Exception ignored) {
            }
            popupWindow = null;
        }
        anchorView = null;
        lockIconView = null;
        popupContent = null;
        currentPick = AlternatePopupGeometry.RESULT_DEFAULT;
        lastAppliedVisualPick = Integer.MIN_VALUE;
    }

    /** Map upper-corner wedges to {@link AlternatePopupGeometry#SLOT_UP} when only that slot is used. */
    private static int normalizeDiagonalUpToLock(int rawPick) {
        if (rawPick == AlternatePopupGeometry.SLOT_UP_LEFT
                || rawPick == AlternatePopupGeometry.SLOT_UP_RIGHT) {
            return AlternatePopupGeometry.SLOT_UP;
        }
        return rawPick;
    }

    private void applyPickVisual(int pick) {
        if (lockIconView == null) {
            return;
        }
        if (pick == lastAppliedVisualPick) {
            return;
        }
        lastAppliedVisualPick = pick;
        Context ctx = lockIconView.getContext();
        int primary =
                MaterialColors.getColor(
                        lockIconView,
                        com.google.android.material.R.attr.colorPrimary,
                        ContextCompat.getColor(ctx, R.color.primary));
        int onPrimary =
                MaterialColors.getColor(
                        lockIconView,
                        com.google.android.material.R.attr.colorOnPrimary,
                        ContextCompat.getColor(ctx, R.color.white));
        int secondary = ContextCompat.getColor(ctx, R.color.text_secondary);
        if (pick == AlternatePopupGeometry.SLOT_UP) {
            lockIconView.setAlpha(1f);
            lockIconView.setScaleX(1.08f);
            lockIconView.setScaleY(1.08f);
            lockIconView.setImageTintList(ColorStateList.valueOf(onPrimary));
            if (popupContent != null) {
                popupContent.setBackgroundTintList(ColorStateList.valueOf(primary));
            }
        } else if (pick == AlternatePopupGeometry.RESULT_CANCEL) {
            lockIconView.setAlpha(0.42f);
            lockIconView.setScaleX(1f);
            lockIconView.setScaleY(1f);
            lockIconView.setImageTintList(ColorStateList.valueOf(secondary));
            if (popupContent != null) {
                popupContent.setBackgroundTintList(null);
            }
        } else {
            lockIconView.setAlpha(0.88f);
            lockIconView.setScaleX(1f);
            lockIconView.setScaleY(1f);
            lockIconView.setImageTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.text_primary)));
            if (popupContent != null) {
                popupContent.setBackgroundTintList(null);
            }
        }
    }
}
