package com.openterface.keymod.basic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.function.ToIntFunction;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.openterface.keymod.R;

/**
 * Vertical drag strip for relative wheel HID. Sensitivity comes from {@link KmBasicTouchpadPrefs}
 * (KM Basic → Setup in chrome); a separate pixel gain ({@link #STRIP_PIXELS_PER_WHEEL_UNIT})
 * keeps the strip usable at 100%.
 *
 * <p>Decorative chevrons are drawn at the top and bottom center of the strip; bounds come from
 * {@link #onDraw} using current width and height so layout stays correct in portrait and landscape.
 */
public class BasicPortraitScrollStripView extends View {

    /** Pixels of finger travel per one wheel unit accumulated; higher = calmer strip scrolling. */
    private static final float STRIP_PIXELS_PER_WHEEL_UNIT = 5f;

    private static final float CHEVRON_MAX_DP = 24f;
    private static final float CHEVRON_EDGE_PAD_DP = 6f;
    private static final float CHEVRON_MAX_WIDTH_FRACTION = 0.85f;
    private static final int CHEVRON_MIN_SHRUNK_PX = 8;

    public interface OnStripScrollListener {
        void onStripScroll(int deltaX, int deltaY);
    }

    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private Drawable chevronUp;
    @Nullable private Drawable chevronDown;
    private OnStripScrollListener listener;
    /**
     * When non-null, strip scroll sensitivity percent (20–200) comes from this instead of
     * {@link KmBasicTouchpadPrefs} (used for KM Pro composite touchpad).
     */
    @Nullable
    private ToIntFunction<Context> sensitivityPercentSupplier;

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

        int chevronTint = ContextCompat.getColor(getContext(), R.color.km_basic_scroll_strip_chevron);
        Drawable up = AppCompatResources.getDrawable(getContext(), R.drawable.km_basic_scroll_strip_chevron_up);
        if (up != null) {
            chevronUp = DrawableCompat.wrap(up.mutate());
            DrawableCompat.setTint(chevronUp, chevronTint);
        }
        Drawable down = AppCompatResources.getDrawable(getContext(), R.drawable.km_basic_scroll_strip_chevron_down);
        if (down != null) {
            chevronDown = DrawableCompat.wrap(down.mutate());
            DrawableCompat.setTint(chevronDown, chevronTint);
        }
    }

    public void setOnStripScrollListener(OnStripScrollListener listener) {
        this.listener = listener;
    }

    /** Pass null to use KM Basic touchpad prefs again. */
    public void setSensitivityPercentSupplier(@Nullable ToIntFunction<Context> supplier) {
        sensitivityPercentSupplier = supplier;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Divider on the edge toward the touchpad (strip sits on the right of the pad).
        float x = dividerPaint.getStrokeWidth() * 0.5f;
        canvas.drawLine(x, 0, x, getHeight(), dividerPaint);

        if (chevronUp == null || chevronDown == null) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(CHEVRON_EDGE_PAD_DP * density);
        int maxSize = Math.round(CHEVRON_MAX_DP * density);
        int size = Math.min(maxSize, Math.round(w * CHEVRON_MAX_WIDTH_FRACTION));
        size = Math.max(1, size);
        int needed = 2 * size + 2 * pad;
        if (h < needed) {
            int shrunk = (h - 2 * pad) / 2;
            if (shrunk < CHEVRON_MIN_SHRUNK_PX) {
                return;
            }
            size = shrunk;
        }
        int left = (w - size) / 2;
        chevronUp.setBounds(left, pad, left + size, pad + size);
        chevronUp.draw(canvas);
        int bottomTop = h - pad - size;
        chevronDown.setBounds(left, bottomTop, left + size, h - pad);
        chevronDown.draw(canvas);
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
        int sensitivityPercent =
                sensitivityPercentSupplier != null
                        ? sensitivityPercentSupplier.applyAsInt(getContext())
                        : KmBasicTouchpadPrefs.getStripScrollSensitivityPercent(getContext());
        return Math.max(0.2f, Math.min(2.0f, sensitivityPercent / 100f));
    }
}
