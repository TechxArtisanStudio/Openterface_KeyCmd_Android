package com.openterface.keymod.gamepad;

import android.content.Context;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.openterface.keymod.R;

/**
 * Bottom sheet for the gamepad Layouts picker. In landscape, applies width and end gravity to the
 * Material bottom sheet container before the first frame so the panel does not jump from full
 * width to the side-aligned width after opening.
 */
public final class GamepadPresetsBottomSheetDialog extends BottomSheetDialog {

    public GamepadPresetsBottomSheetDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (!isLandscape()) {
            return;
        }
        View bottom = findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottom == null) {
            return;
        }
        ViewTreeObserver observer = bottom.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                bottom.getViewTreeObserver().removeOnPreDrawListener(this);
                applyLandscapeSidePanelLayout(bottom);
                return false;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        BottomSheetBehavior<?> behavior = getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        if (isLandscape()) {
            View bottom = findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottom != null) {
                applyLandscapeSidePanelLayout(bottom);
            }
        }
    }

    private boolean isLandscape() {
        Context c = getContext();
        return c != null
                && c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void applyLandscapeSidePanelLayout(@NonNull View bottom) {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        ViewGroup.LayoutParams lp = bottom.getLayoutParams();
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int maxW = ctx.getResources().getDimensionPixelSize(R.dimen.gamepad_presets_bottom_sheet_max_width);
        int minW = ctx.getResources().getDimensionPixelSize(R.dimen.gamepad_presets_bottom_sheet_min_width);
        int targetW = Math.min(Math.max(Math.round(screenW * 0.38f), minW), maxW);
        targetW = Math.min(targetW, screenW);
        if (targetW <= 0) {
            return;
        }
        lp.width = targetW;
        if (lp instanceof CoordinatorLayout.LayoutParams) {
            ((CoordinatorLayout.LayoutParams) lp).gravity = Gravity.BOTTOM | Gravity.END;
        }
        bottom.setLayoutParams(lp);
    }
}
