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

import com.openterface.keymod.AlternatePopupGeometry;
import com.openterface.keymod.R;

/**
 * Minimal 3×3 gesture popup for KM Basic hold-lock: only the {@link AlternatePopupGeometry#SLOT_UP}
 * cell is selectable (Material lock icon). Same geometry as Pro alternates.
 *
 * <p>Gesture deltas use {@code gestureOriginRaw*} from the key's {@link MotionEvent#ACTION_DOWN}
 * (same idea as Pro alternates). {@link #updatePointer} tracks the finger while the window is
 * being attached asynchronously — it must not depend on {@link PopupWindow#isShowing()}.
 */
public final class BasicHoldLockPopup {

    private static final int ALT_GESTURE_R_MIN_DP = 12;
    private static final int ALT_GESTURE_R_CANCEL_DP = 228;
    private static final int ALT_GESTURE_AXIS_DEADZONE_DP = 18;
    private static final int ALT_POPUP_VERTICAL_OFFSET_DP = 96;
    private static final int ALT_POPUP_CONTAINER_PADDING_DP = 6;
    private static final int ALT_POPUP_CELL_MIN_SIZE_DP = 40;

    private PopupWindow popupWindow;
    private View anchorView;
    /** Raw screen coordinates at ACTION_DOWN on the key (gesture origin for {@link AlternatePopupGeometry}). */
    private float gestureOriginRawX;
    private float gestureOriginRawY;
    private int currentPick = AlternatePopupGeometry.RESULT_DEFAULT;
    private int lastAppliedVisualPick = Integer.MIN_VALUE;
    private final boolean[] slotOccupied = new boolean[AlternatePopupGeometry.SLOT_COUNT];
    @Nullable private AppCompatImageView lockIconView;

    public BasicHoldLockPopup() {
        slotOccupied[AlternatePopupGeometry.SLOT_UP] = true;
    }

    /**
     * @param gestureOriginRawX raw X at {@link MotionEvent#ACTION_DOWN} on the anchor key
     * @param gestureOriginRawY raw Y at ACTION_DOWN
     */
    public void show(View anchor, float gestureOriginRawX, float gestureOriginRawY) {
        dismiss();
        anchorView = anchor;
        gestureOriginRawX = gestureOriginRawX;
        gestureOriginRawY = gestureOriginRawY;
        currentPick = AlternatePopupGeometry.RESULT_DEFAULT;
        lastAppliedVisualPick = Integer.MIN_VALUE;
        Context context = anchor.getContext();
        float density = context.getResources().getDisplayMetrics().density;

        GridLayout grid = new GridLayout(context);
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
        int pick =
                AlternatePopupGeometry.pickSlot(
                        dx,
                        dy,
                        ALT_GESTURE_R_MIN_DP * density,
                        ALT_GESTURE_R_CANCEL_DP * density,
                        ALT_GESTURE_AXIS_DEADZONE_DP * density,
                        slotOccupied);
        currentPick = pick;
        applyPickVisual(pick);
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
        currentPick = AlternatePopupGeometry.RESULT_DEFAULT;
        lastAppliedVisualPick = Integer.MIN_VALUE;
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
        int primary = ContextCompat.getColor(ctx, R.color.primary);
        int secondary = ContextCompat.getColor(ctx, R.color.text_secondary);
        if (pick == AlternatePopupGeometry.SLOT_UP) {
            lockIconView.setAlpha(1f);
            lockIconView.setScaleX(1.08f);
            lockIconView.setScaleY(1.08f);
            lockIconView.setImageTintList(ColorStateList.valueOf(primary));
        } else if (pick == AlternatePopupGeometry.RESULT_CANCEL) {
            lockIconView.setAlpha(0.42f);
            lockIconView.setScaleX(1f);
            lockIconView.setScaleY(1f);
            lockIconView.setImageTintList(ColorStateList.valueOf(secondary));
        } else {
            lockIconView.setAlpha(0.88f);
            lockIconView.setScaleX(1f);
            lockIconView.setScaleY(1f);
            lockIconView.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.text_primary)));
        }
    }
}
