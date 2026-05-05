package com.openterface.keymod.basic;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.R;

/**
 * Vertical drag strip for relative wheel HID. Uses the same {@code touchpad_scroll_sensitivity}
 * preference as {@link com.openterface.keymod.TouchPadView} two-finger scroll, with a separate
 * pixel gain ({@link #STRIP_PIXELS_PER_WHEEL_UNIT}) so the strip is not overly hot.
 */
public class BasicPortraitScrollStripView extends View {

    private static final String PREF_TOUCHPAD_SCROLL_SENSITIVITY = "touchpad_scroll_sensitivity";

    /** Pixels of finger travel per one wheel unit accumulated; higher = calmer strip scrolling. */
    private static final float STRIP_PIXELS_PER_WHEEL_UNIT = 5f;

    public interface OnStripScrollListener {
        void onStripScroll(int deltaX, int deltaY);
    }

    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnStripScrollListener listener;
    private float lastY;
    private float accumX;
    private float accumY;

    public BasicPortraitScrollStripView(Context context) {
        super(context);
        init();
    }

    public BasicPortraitScrollStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BasicPortraitScrollStripView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        dividerPaint.setColor(ContextCompat.getColor(getContext(), R.color.divider));
        dividerPaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
    }

    public void setOnStripScrollListener(OnStripScrollListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Divider on the edge toward the touchpad (strip sits on the right of the pad).
        float x = dividerPaint.getStrokeWidth() * 0.5f;
        canvas.drawLine(x, 0, x, getHeight(), dividerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastY = event.getY();
                accumX = 0f;
                accumY = 0f;
                return true;
            case MotionEvent.ACTION_MOVE:
                float y = event.getY();
                float dy = y - lastY;
                lastY = y;
                float sensitivity = getScrollSensitivity();
                accumY += (-dy / STRIP_PIXELS_PER_WHEEL_UNIT) * sensitivity;
                int sx = (int) accumX;
                int sy = (int) accumY;
                if (sx != 0) {
                    accumX -= sx;
                }
                if (sy != 0) {
                    accumY -= sy;
                }
                if (listener != null && (sx != 0 || sy != 0)) {
                    listener.onStripScroll(sx, sy);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastY = 0f;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private float getScrollSensitivity() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        int sensitivityPercent = prefs.getInt(PREF_TOUCHPAD_SCROLL_SENSITIVITY, 100);
        return Math.max(0.2f, Math.min(2.0f, sensitivityPercent / 100f));
    }
}
