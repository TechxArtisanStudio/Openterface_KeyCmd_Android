package com.openterface.keymod.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

import androidx.annotation.Nullable;

/**
 * {@link HorizontalScrollView} placed inside a vertical {@link androidx.core.widget.NestedScrollView}
 * (e.g. gamepad module config sheets). Without this, the parent often intercepts touch and vertical
 * scrolling wins, so horizontal drags do not reach the far end of the swatch row.
 */
public final class NestedFriendlyHorizontalScrollView extends HorizontalScrollView {

    public NestedFriendlyHorizontalScrollView(Context context) {
        super(context);
    }

    public NestedFriendlyHorizontalScrollView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NestedFriendlyHorizontalScrollView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        boolean handled = super.dispatchTouchEvent(ev);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
        }
        return handled;
    }
}
