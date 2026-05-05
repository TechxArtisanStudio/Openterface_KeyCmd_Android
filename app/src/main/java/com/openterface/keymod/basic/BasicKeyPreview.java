package com.openterface.keymod.basic;

import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.openterface.keymod.R;

/**
 * Single reusable non-touchable popup shown above (or below if clipped) a key during press,
 * for KM Basic physical keyboard tap confirmation.
 */
public final class BasicKeyPreview {

    @Nullable
    private PopupWindow popup;

    public void dismiss() {
        if (popup != null) {
            try {
                if (popup.isShowing()) {
                    popup.dismiss();
                }
            } catch (IllegalArgumentException ignored) {
                // Anchor detached from window.
            }
            popup = null;
        }
    }

    public void show(View anchor, CharSequence text) {
        if (anchor == null || anchor.getContext() == null || text == null || text.length() == 0) {
            return;
        }
        dismiss();

        View root = LayoutInflater.from(anchor.getContext()).inflate(R.layout.basic_key_preview_popup, null);
        TextView tv = root.findViewById(R.id.basic_key_preview_text);
        tv.setText(text);

        PopupWindow win =
                new PopupWindow(root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        win.setTouchable(false);
        win.setOutsideTouchable(false);
        win.setClippingEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            win.setElevation(anchor.getResources().getDimension(R.dimen.basic_key_preview_elevation));
        }
        popup = win;

        root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int pw = root.getMeasuredWidth();
        int ph = root.getMeasuredHeight();

        int gap = anchor.getResources().getDimensionPixelSize(R.dimen.basic_key_preview_gap);
        int margin = anchor.getResources().getDimensionPixelSize(R.dimen.basic_key_preview_screen_margin);

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int anchorLeft = loc[0];
        int anchorTop = loc[1];
        int anchorW = anchor.getWidth();
        int anchorH = anchor.getHeight();

        Rect visible = new Rect();
        anchor.getWindowVisibleDisplayFrame(visible);

        int popupX = anchorLeft + (anchorW - pw) / 2;
        popupX = Math.max(visible.left + margin, Math.min(popupX, visible.right - pw - margin));

        int aboveY = anchorTop - ph - gap;
        int belowY = anchorTop + anchorH + gap;

        int popupY;
        if (aboveY >= visible.top + margin) {
            popupY = aboveY;
        } else if (belowY + ph <= visible.bottom - margin) {
            popupY = belowY;
        } else {
            popupY = Math.max(visible.top + margin, Math.min(aboveY, visible.bottom - ph - margin));
        }

        win.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY);
    }
}
