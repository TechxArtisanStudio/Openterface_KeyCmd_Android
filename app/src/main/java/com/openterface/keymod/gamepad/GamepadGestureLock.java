package com.openterface.keymod.gamepad;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


/**
 * Helpers for {@link GamepadLayoutPresetDocument.GamepadModule#gestureLock}: enabled checks, diagonal
 * classification, validation, and runtime defaults when {@code keyboardHoldLock} is on (up-right →
 * hold lock, up-left → turbo) for slots omitted from JSON.
 */
public final class GamepadGestureLock {

    /** Minimum finger travel (px) before a diagonal sector is chosen. */
    public static final float DIAGONAL_R_MIN_DP = 14f;
    /** Beyond this radius (px) the gesture is treated as cancel. */
    public static final float DIAGONAL_R_CANCEL_DP = 200f;

    private GamepadGestureLock() {}

    public static boolean moduleGesturesEnabled(@Nullable GamepadLayoutPresetDocument.GamepadModule m) {
        if (m == null) {
            return false;
        }
        if (Boolean.TRUE.equals(m.keyboardHoldLock)) {
            return true;
        }
        return hasAnyNonNoneAction(m.gestureLock);
    }

    public static boolean hasAnyNonNoneAction(@Nullable GamepadLayoutPresetDocument.GestureLockConfig g) {
        if (g == null) {
            return false;
        }
        return isNonNone(slotAction(g.upLeft))
                || isNonNone(slotAction(g.upRight))
                || isNonNone(slotAction(g.downLeft))
                || isNonNone(slotAction(g.downRight));
    }

    /**
     * True when the preset declares at least one non-{@code none} diagonal slot in {@code gestureLock}
     * (diagonal config without relying on {@link GamepadLayoutPresetDocument.GamepadModule#keyboardHoldLock}
     * defaults).
     */
    public static boolean moduleUsesDiagonalGestures(@Nullable GamepadLayoutPresetDocument.GamepadModule m) {
        return m != null && hasAnyNonNoneAction(m.gestureLock);
    }

    /**
     * When true, {@link com.openterface.keymod.GamepadView} commits hold/turbo via
     * {@link #classifyDiagonalSlot} on pointer-up instead of {@code BasicHoldLockPopup}. Same as
     * {@link #moduleGesturesEnabled}: {@code keyboardHoldLock} and/or explicit {@code gestureLock} actions.
     */
    public static boolean moduleUsesDiagonalGestureCommit(@Nullable GamepadLayoutPresetDocument.GamepadModule m) {
        return moduleGesturesEnabled(m);
    }

    @Nullable
    public static GamepadLayoutPresetDocument.GestureLockSlot slotForKey(
            @Nullable GamepadLayoutPresetDocument.GestureLockConfig g, @NonNull String slotKey) {
        if (g == null) {
            return null;
        }
        if (GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_LEFT.equals(slotKey)) {
            return g.upLeft;
        }
        if (GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_RIGHT.equals(slotKey)) {
            return g.upRight;
        }
        if (GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_DOWN_LEFT.equals(slotKey)) {
            return g.downLeft;
        }
        if (GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_DOWN_RIGHT.equals(slotKey)) {
            return g.downRight;
        }
        return null;
    }

    @Nullable
    public static String slotAction(@Nullable GamepadLayoutPresetDocument.GestureLockSlot slot) {
        if (slot == null || slot.action == null) {
            return GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE;
        }
        String a = slot.action.trim();
        return a.isEmpty() ? GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE : a;
    }

    private static boolean isNonNone(@Nullable String action) {
        if (action == null) {
            return false;
        }
        String a = action.trim();
        return !a.isEmpty()
                && !GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE.equalsIgnoreCase(a);
    }

    /**
     * @return one of {@link GamepadLayoutPresetConstants} {@code GESTURE_LOCK_SLOT_*}, {@code null} if
     *     still inside dead radius (no commit), or {@link #RESULT_CANCEL} if beyond cancel radius.
     */
    @Nullable
    public static String classifyDiagonalSlot(float dx, float dy, float density) {
        double r = Math.hypot(dx, dy);
        float rMin = DIAGONAL_R_MIN_DP * density;
        float rCancel = DIAGONAL_R_CANCEL_DP * density;
        if (r <= rMin) {
            return null;
        }
        if (r > rCancel) {
            return RESULT_CANCEL;
        }
        float adx = Math.abs(dx);
        float ady = Math.abs(dy);
        if (adx < 1e-3f && ady < 1e-3f) {
            return null;
        }
        if (dy < 0 && ady >= adx) {
            return dx < 0f
                    ? GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_LEFT
                    : GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_RIGHT;
        }
        if (dy > 0 && ady >= adx) {
            return dx < 0f
                    ? GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_DOWN_LEFT
                    : GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_DOWN_RIGHT;
        }
        if (dx < 0f) {
            return dy <= 0f
                    ? GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_LEFT
                    : GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_DOWN_LEFT;
        }
        return dy <= 0f
                ? GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_RIGHT
                : GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_DOWN_RIGHT;
    }

    /** Sentinel returned from {@link #classifyDiagonalSlot} meaning overshoot / cancel. */
    public static final String RESULT_CANCEL = "__cancel__";

    /**
     * Resolves the action for a classified diagonal slot. Explicit {@code gestureLock} entries win;
     * a present slot with {@code action: none} is not replaced by defaults. When the slot object is
     * absent (or {@code gestureLock} is null) and {@link GamepadLayoutPresetDocument.GamepadModule#keyboardHoldLock}
     * is true, {@code upRight} defaults to {@code hold_lock} and {@code upLeft} to {@code turbo}.
     */
    @NonNull
    public static String resolvedActionForSlot(
            @Nullable GamepadLayoutPresetDocument.GamepadModule m, @Nullable String slotKey) {
        if (m == null || slotKey == null || RESULT_CANCEL.equals(slotKey)) {
            return GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE;
        }
        boolean slotObjectPresent = false;
        String explicit = GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE;
        if (m.gestureLock != null) {
            GamepadLayoutPresetDocument.GestureLockSlot slot = slotForKey(m.gestureLock, slotKey);
            if (slot != null) {
                slotObjectPresent = true;
                explicit = slotAction(slot);
            }
        }
        if (isNonNone(explicit)) {
            return explicit;
        }
        if (!Boolean.TRUE.equals(m.keyboardHoldLock)) {
            return GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE;
        }
        if (slotObjectPresent) {
            return GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE;
        }
        if (GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_RIGHT.equals(slotKey)) {
            return GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_HOLD_LOCK;
        }
        if (GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_LEFT.equals(slotKey)) {
            return GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_TURBO;
        }
        return GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE;
    }

    public static void validateGestureLockOnModule(@NonNull GamepadLayoutPresetDocument.GamepadModule m)
            throws IllegalArgumentException {
        if (m.gestureLock == null) {
            return;
        }
        GamepadLayoutPresetDocument.GestureLockConfig g = m.gestureLock;
        validateOneSlot(m.id, g.upLeft);
        validateOneSlot(m.id, g.upRight);
        validateOneSlot(m.id, g.downLeft);
        validateOneSlot(m.id, g.downRight);
        if (!hasAnyNonNoneAction(g) && !Boolean.TRUE.equals(m.keyboardHoldLock)) {
            throw new IllegalArgumentException(
                    "Module " + m.id
                            + ": gestureLock must include at least one non-none action, or enable hold lock switch");
        }
        boolean mouse = GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON.equals(m.type);
        if (mouse) {
            for (String sk : new String[] {
                GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_LEFT,
                GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_RIGHT,
                GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_DOWN_LEFT,
                GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_DOWN_RIGHT
            }) {
                GamepadLayoutPresetDocument.GestureLockSlot s = slotForKey(g, sk);
                String a = slotAction(s);
                if (GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_KEY_HOLD.equalsIgnoreCase(a)
                        || GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_KEY_TURBO.equalsIgnoreCase(a)) {
                    throw new IllegalArgumentException(
                            "Module " + m.id + ": key_hold/key_turbo are not valid on MOUSE_BUTTON");
                }
            }
        }
    }

    private static void validateOneSlot(
            @NonNull String moduleId, @Nullable GamepadLayoutPresetDocument.GestureLockSlot s)
            throws IllegalArgumentException {
        if (s == null) {
            return;
        }
        String a = slotAction(s);
        if (GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE.equalsIgnoreCase(a)) {
            if (s.hidKey != null || s.modifierMask != null) {
                throw new IllegalArgumentException("Module " + moduleId + ": gesture slot with none must omit keys");
            }
            return;
        }
        if (!GamepadLayoutPresetConstants.isAllowedGestureLockAction(a)) {
            throw new IllegalArgumentException("Module " + moduleId + ": invalid gestureLock action: " + s.action);
        }
        if (GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_KEY_HOLD.equalsIgnoreCase(a)
                || GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_KEY_TURBO.equalsIgnoreCase(a)) {
            if (s.hidKey == null || s.hidKey < 1 || s.hidKey > 255) {
                throw new IllegalArgumentException("Module " + moduleId + ": key_* gesture requires hidKey 1–255");
            }
            if (s.modifierMask != null && (s.modifierMask < 0 || s.modifierMask > 0xFFFF)) {
                throw new IllegalArgumentException("Module " + moduleId + ": invalid modifierMask");
            }
        } else if (s.hidKey != null || s.modifierMask != null) {
            throw new IllegalArgumentException(
                    "Module " + moduleId + ": hidKey/modifierMask only allowed for key_hold/key_turbo");
        }
    }

    public static boolean gesturesAllowedModuleType(@Nullable String type) {
        return GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(type)
                || GamepadLayoutPresetConstants.MODULE_TYPE_SHOULDER.equals(type)
                || GamepadLayoutPresetConstants.MODULE_TYPE_TRIGGER.equals(type)
                || GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON.equals(type);
    }
}
