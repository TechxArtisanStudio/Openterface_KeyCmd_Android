package com.openterface.keymod.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/**
 * {@link NestedScrollView} whose height is at most {@link #setMaxHeightPx(int)} but still
 * {@code wrap_content} when content is shorter. Use under {@code MaterialAlertDialog} custom
 * views so tall module config forms scroll instead of clipping.
 */
public final class MaxHeightNestedScrollView extends NestedScrollView {

    private int maxHeightPx = -1;

    public MaxHeightNestedScrollView(Context context) {
        super(context);
    }

    public MaxHeightNestedScrollView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MaxHeightNestedScrollView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** Pass {@code <= 0} to disable the cap (default). */
    public void setMaxHeightPx(int maxHeightPx) {
        this.maxHeightPx = maxHeightPx;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeightPx > 0) {
            heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
