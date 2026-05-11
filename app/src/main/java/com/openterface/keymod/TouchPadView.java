package com.openterface.keymod;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.preference.PreferenceManager;

/**
 * TouchPadView — gesture mapping aligned with iOS TouchpadUIView:
 *
 *  1-finger pan      → mouse move
 *  single tap        → left click  (delayed 150 ms to distinguish from double-tap)
 *  double tap        → double click
 *  long press        → drag start (fragments); while drag is on, tap/double-tap release only
 *  2-finger tap      → right click
 *  2-finger pan      → scroll (natural direction, reduced sensitivity)
 */
public class TouchPadView extends View {

    // Thresholds — mirror iOS defaults
    private static final long   TAP_DELAY_MS       = 150;   // wait before confirming single tap
    private static final long   TAP_DURATION_MAX_MS = 400;  // finger must lift within this time
    private static final float  TAP_MOVE_THRESHOLD  = 10f;  // px — movement cancels tap
    private static final float  TWO_FINGER_TAP_MOVE_THRESHOLD = 12f;
    private static final String PREF_TOUCHPAD_SCROLL_SENSITIVITY = "touchpad_scroll_sensitivity";

    public interface OnTouchPadListener {
        void onTouchMove(float startX, float startY, float lastX, float lastY);
        void onTouchClick();
        void onTouchDoubleClick();
        void onTouchRightClick();
        /** Called when finger(s) are lifted/cancelled so host can release mouse state if needed */
        default void onTouchRelease() {}
        /** Called when a long-press is detected — fragments use this to enter drag (tap exits). */
        default void onTouchLongPress() {}
    }

    private OnTouchPadListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * When false, the pad still sends pointer moves and two-finger scroll, but does not emit
     * single-tap, double-tap, long-press (drag), or two-finger right-click. Used for KM Pro
     * "Pad + mouse keys" strip-only click mode.
     */
    private boolean padClickDragGesturesEnabled = true;

    // 1-finger move state
    private float lastMoveX, lastMoveY;
    private boolean isDragging = false;

    // Tap detection state
    private float tapDownX, tapDownY;
    private long  tapDownTime;
    private boolean tapCancelled = false;
    private boolean suppressSingleTapFromDoubleTap = false;
    private Runnable pendingSingleTap = null;

    // 2-finger scroll state
    private float twoFingerPrevX, twoFingerPrevY;
    private float twoFingerScrollAccumX = 0f;
    private float twoFingerScrollAccumY = 0f;
    private float twoFingerDownCenterX, twoFingerDownCenterY;
    private boolean twoFingerMoved = false;
    private boolean isTwoFingerScrolling = false;

    private final GestureDetector gestureDetector;

    public TouchPadView(Context context) {
        super(context);
        gestureDetector = buildGestureDetector(context);
        setClickable(true);
    }

    public TouchPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = buildGestureDetector(context);
        setClickable(true);
    }

    private GestureDetector buildGestureDetector(Context context) {
        return new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                // Must return true so GestureDetector continues tracking for double-tap.
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                cancelPendingSingleTap();
                suppressSingleTapFromDoubleTap = true;
                if (padClickDragGesturesEnabled && listener != null) {
                    listener.onTouchDoubleClick();
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (e.getPointerCount() == 1) {
                    cancelPendingSingleTap();
                    if (padClickDragGesturesEnabled && listener != null) {
                        listener.onTouchLongPress();
                    }
                }
            }
        });
    }

    /**
     * @param enabled false for strip-only clicks (pointer + two-finger scroll only).
     */
    public void setPadClickDragGesturesEnabled(boolean enabled) {
        if (padClickDragGesturesEnabled == enabled) {
            return;
        }
        padClickDragGesturesEnabled = enabled;
        cancelPendingSingleTap();
        suppressSingleTapFromDoubleTap = false;
    }

    public boolean isPadClickDragGesturesEnabled() {
        return padClickDragGesturesEnabled;
    }

    public void setOnTouchPadListener(OnTouchPadListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Let GestureDetector handle double-tap and long-press on the raw events
        gestureDetector.onTouchEvent(event);

        int pointerCount = event.getPointerCount();
        int action = event.getActionMasked();

        // ---- 2-finger gestures ----
        if (pointerCount == 2) {
            cancelPendingSingleTap();
            isDragging = false;

            switch (action) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    twoFingerDownCenterX = (event.getX(0) + event.getX(1)) / 2f;
                    twoFingerDownCenterY = (event.getY(0) + event.getY(1)) / 2f;
                    twoFingerPrevX = twoFingerDownCenterX;
                    twoFingerPrevY = twoFingerDownCenterY;
                    twoFingerScrollAccumX = 0f;
                    twoFingerScrollAccumY = 0f;
                    twoFingerMoved = false;
                    isTwoFingerScrolling = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float cx = (event.getX(0) + event.getX(1)) / 2f;
                    float cy = (event.getY(0) + event.getY(1)) / 2f;
                    float totalDist = dist(cx, cy, twoFingerDownCenterX, twoFingerDownCenterY);
                    if (totalDist > TWO_FINGER_TAP_MOVE_THRESHOLD) {
                        twoFingerMoved = true;
                    }

                    float dx = cx - twoFingerPrevX;
                    float dy = cy - twoFingerPrevY;
                    if (Math.abs(dx) > 0f || Math.abs(dy) > 0f) {
                        isTwoFingerScrolling = true;
                    }

                    float sensitivity = getScrollSensitivity();
                    twoFingerScrollAccumX += (dx / 3f) * sensitivity;
                    twoFingerScrollAccumY += (-dy / 3f) * sensitivity;

                    int scrollX = (int) twoFingerScrollAccumX;
                    int scrollY = (int) twoFingerScrollAccumY;

                    if (scrollX != 0) {
                        twoFingerScrollAccumX -= scrollX;
                    }
                    if (scrollY != 0) {
                        twoFingerScrollAccumY -= scrollY;
                    }

                    if (listener != null && (scrollX != 0 || scrollY != 0)) {
                        listener.onTouchMove(scrollX, scrollY, 0, 0);
                    }
                    twoFingerPrevX = cx;
                    twoFingerPrevY = cy;
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    // 2-finger tap if there wasn't meaningful movement/scrolling
                    if (padClickDragGesturesEnabled && !twoFingerMoved && !isTwoFingerScrolling) {
                        if (listener != null) {
                            listener.onTouchRightClick();
                        }
                    }
                    if (listener != null) listener.onTouchRelease();
                    twoFingerScrollAccumX = 0f;
                    twoFingerScrollAccumY = 0f;
                    twoFingerMoved = false;
                    isTwoFingerScrolling = false;
                    break;
            }
            return true;
        }

        twoFingerMoved = false;
        isTwoFingerScrolling = false;

        // ---- 1-finger gestures ----
        float x = event.getX();
        float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastMoveX = x;
                lastMoveY = y;
                tapDownX = x;
                tapDownY = y;
                tapDownTime = event.getEventTime();
                tapCancelled = false;
                isDragging = false;
                break;

            case MotionEvent.ACTION_MOVE:
                float distMoved = dist(x, y, tapDownX, tapDownY);
                if (distMoved > TAP_MOVE_THRESHOLD) {
                    cancelPendingSingleTap();
                    tapCancelled = true;
                    isDragging = true;
                }
                // Always forward move events for smooth cursor tracking
                if (listener != null) {
                    listener.onTouchMove(x, y, lastMoveX, lastMoveY);
                }
                lastMoveX = x;
                lastMoveY = y;
                break;

            case MotionEvent.ACTION_UP:
                long duration = event.getEventTime() - tapDownTime;
                float distLifted = dist(x, y, tapDownX, tapDownY);
                if (suppressSingleTapFromDoubleTap) {
                    suppressSingleTapFromDoubleTap = false;
                    if (listener != null) listener.onTouchRelease();
                    lastMoveX = 0;
                    lastMoveY = 0;
                    isDragging = false;
                    break;
                }
                boolean validTap = !tapCancelled
                        && duration < TAP_DURATION_MAX_MS
                        && distLifted <= TAP_MOVE_THRESHOLD;

                if (validTap && padClickDragGesturesEnabled) {
                    // Schedule a delayed single-tap; GestureDetector will fire
                    // onDoubleTap before this fires if a second tap comes in time.
                    pendingSingleTap = () -> {
                        if (listener != null) listener.onTouchClick();
                        pendingSingleTap = null;
                    };
                    mainHandler.postDelayed(pendingSingleTap, TAP_DELAY_MS);
                }

                if (listener != null) listener.onTouchRelease();

                lastMoveX = 0;
                lastMoveY = 0;
                isDragging = false;
                break;

            case MotionEvent.ACTION_CANCEL:
                cancelPendingSingleTap();
                if (listener != null) listener.onTouchRelease();
                isDragging = false;
                break;
        }
        return true;
    }

    private void cancelPendingSingleTap() {
        if (pendingSingleTap != null) {
            mainHandler.removeCallbacks(pendingSingleTap);
            pendingSingleTap = null;
        }
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float getScrollSensitivity() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        int sensitivityPercent = prefs.getInt(PREF_TOUCHPAD_SCROLL_SENSITIVITY, 100);
        return Math.max(0.2f, Math.min(2.0f, sensitivityPercent / 100f));
    }
}
