package com.openterface.keymod;

/**
 * Maps finger delta (from long-press start) to a 3×3 alternate grid using screen axes.
 * Screen Y increases downward. After leaving the inner radius, movement is classified into
 * eight outer cells; the center cell is only chosen via {@link #RESULT_DEFAULT} (release in
 * inner radius or neutral cross between axes).
 *
 * <p>Grid layout (row, col):</p>
 * <pre>
 *   UL   U   UR
 *    L   C    R
 *   DL   D   DR
 * </pre>
 */
public final class AlternatePopupGeometry {
    /** Release commits the center (default) option when finger stayed inside min radius or neutral cross. */
    public static final int RESULT_DEFAULT = -1;
    /** Oversized movement or empty cell — release does not send a character. */
    public static final int RESULT_CANCEL = -2;

    public static final int SLOT_COUNT = 9;

    /** Center: default on release (e.g. capital for a–z). */
    public static final int SLOT_CENTER = 0;
    public static final int SLOT_UP = 1;
    public static final int SLOT_DOWN = 2;
    public static final int SLOT_LEFT = 3;
    public static final int SLOT_RIGHT = 4;
    public static final int SLOT_UP_LEFT = 5;
    public static final int SLOT_UP_RIGHT = 6;
    public static final int SLOT_DOWN_LEFT = 7;
    public static final int SLOT_DOWN_RIGHT = 8;

    /**
     * Maps row (0 = up, 2 = down) and column (0 = left, 2 = right) to slot index.
     * Center (1,1) is never returned from {@link #pickSlot}; use {@link #RESULT_DEFAULT} instead.
     */
    private static final int[][] GRID_TO_SLOT = {
            {SLOT_UP_LEFT, SLOT_UP, SLOT_UP_RIGHT},
            {SLOT_LEFT, SLOT_CENTER, SLOT_RIGHT},
            {SLOT_DOWN_LEFT, SLOT_DOWN, SLOT_DOWN_RIGHT}
    };

    private AlternatePopupGeometry() {
    }

    /**
     * @param dx              finger X minus start X (px)
     * @param dy              finger Y minus start Y (px), positive downward
     * @param rMinPx          inside this radius → {@link #RESULT_DEFAULT}
     * @param rCancelPx       beyond this radius → {@link #RESULT_CANCEL}
     * @param axisDeadzonePx  half-width of neutral band on each axis; if both axes neutral and
     *                        {@code r > rMinPx}, returns {@link #RESULT_DEFAULT}
     * @param slotOccupied    length {@link #SLOT_COUNT}; true if that cell can be selected
     *                        (including corners satisfied via fallback to U/D in the model)
     */
    public static int pickSlot(
            float dx,
            float dy,
            float rMinPx,
            float rCancelPx,
            float axisDeadzonePx,
            boolean[] slotOccupied) {
        double r = Math.hypot(dx, dy);
        if (r > rCancelPx) {
            return RESULT_CANCEL;
        }
        if (r <= rMinPx) {
            return RESULT_DEFAULT;
        }
        int col;
        if (dx < -axisDeadzonePx) {
            col = 0;
        } else if (dx > axisDeadzonePx) {
            col = 2;
        } else {
            col = 1;
        }
        int row;
        if (dy < -axisDeadzonePx) {
            row = 0;
        } else if (dy > axisDeadzonePx) {
            row = 2;
        } else {
            row = 1;
        }
        if (row == 1 && col == 1) {
            return RESULT_DEFAULT;
        }
        int slot = GRID_TO_SLOT[row][col];
        if (slotOccupied != null
                && slot >= 0
                && slot < slotOccupied.length
                && !slotOccupied[slot]) {
            return RESULT_CANCEL;
        }
        return slot;
    }
}
