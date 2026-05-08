package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GamepadGestureLockTest {

    @Test
    public void classifyUpLeftWhenDominantUpAndLeft() {
        float d = 2f;
        assertEquals(
                GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_LEFT,
                GamepadGestureLock.classifyDiagonalSlot(-30f, -50f, d));
    }

    @Test
    public void classifyUpRightWhenDominantUpAndRight() {
        float d = 2f;
        assertEquals(
                GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_RIGHT,
                GamepadGestureLock.classifyDiagonalSlot(40f, -60f, d));
    }

    @Test
    public void classifyReturnsNullInsideMinRadius() {
        float d = 2f;
        assertNull(GamepadGestureLock.classifyDiagonalSlot(2f, -3f, d));
    }

    /**
     * With rMin = 9dp, at density 2 the threshold is 18px. A flick just above that should classify
     * (would have been null when rMin was 14dp → 28px).
     */
    @Test
    public void classifyAcceptsDiagonalJustAboveMinRadius() {
        float d = 2f;
        assertEquals(
                GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_LEFT,
                GamepadGestureLock.classifyDiagonalSlot(-11f, -16f, d));
    }

    @Test
    public void classifyCancelBeyondMaxRadius() {
        float d = 2f;
        assertEquals(
                GamepadGestureLock.RESULT_CANCEL,
                GamepadGestureLock.classifyDiagonalSlot(500f, 0f, d));
    }

    @Test
    public void resolvedAction_keyboardHoldLockOnly_defaultsUpRightAndUpLeft() {
        GamepadLayoutPresetDocument.GamepadModule m =
                new GamepadLayoutPresetDocument.GamepadModule();
        m.id = "button_x";
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        m.keyboardHoldLock = true;
        m.gestureLock = null;
        assertEquals(
                GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_HOLD_LOCK,
                GamepadGestureLock.resolvedActionForSlot(
                        m, GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_RIGHT));
        assertEquals(
                GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_TURBO,
                GamepadGestureLock.resolvedActionForSlot(
                        m, GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_LEFT));
        assertEquals(
                GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE,
                GamepadGestureLock.resolvedActionForSlot(
                        m, GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_DOWN_LEFT));
        assertEquals(
                GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE,
                GamepadGestureLock.resolvedActionForSlot(
                        m, GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_DOWN_RIGHT));
    }

    @Test
    public void resolvedAction_explicitNoneOnUpRight_doesNotApplyDefault() {
        GamepadLayoutPresetDocument.GamepadModule m =
                new GamepadLayoutPresetDocument.GamepadModule();
        m.id = "mouse_btn_l";
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON;
        m.keyboardHoldLock = true;
        GamepadLayoutPresetDocument.GestureLockConfig g =
                new GamepadLayoutPresetDocument.GestureLockConfig();
        GamepadLayoutPresetDocument.GestureLockSlot ur =
                new GamepadLayoutPresetDocument.GestureLockSlot();
        ur.action = GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE;
        g.upRight = ur;
        m.gestureLock = g;
        assertEquals(
                GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE,
                GamepadGestureLock.resolvedActionForSlot(
                        m, GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_RIGHT));
    }

    @Test
    public void resolvedAction_explicitOverrideBeatsDefault() {
        GamepadLayoutPresetDocument.GamepadModule m =
                new GamepadLayoutPresetDocument.GamepadModule();
        m.id = "button_a";
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        m.keyboardHoldLock = true;
        GamepadLayoutPresetDocument.GestureLockConfig g =
                new GamepadLayoutPresetDocument.GestureLockConfig();
        GamepadLayoutPresetDocument.GestureLockSlot ur =
                new GamepadLayoutPresetDocument.GestureLockSlot();
        ur.action = GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_TURBO;
        g.upRight = ur;
        m.gestureLock = g;
        assertEquals(
                GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_TURBO,
                GamepadGestureLock.resolvedActionForSlot(
                        m, GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_RIGHT));
    }

    @Test
    public void moduleUsesDiagonalGestureCommit_matchesGesturesEnabled() {
        GamepadLayoutPresetDocument.GamepadModule a =
                new GamepadLayoutPresetDocument.GamepadModule();
        a.keyboardHoldLock = true;
        assertTrue(GamepadGestureLock.moduleUsesDiagonalGestureCommit(a));
        GamepadLayoutPresetDocument.GamepadModule b =
                new GamepadLayoutPresetDocument.GamepadModule();
        b.keyboardHoldLock = null;
        GamepadLayoutPresetDocument.GestureLockConfig g =
                new GamepadLayoutPresetDocument.GestureLockConfig();
        GamepadLayoutPresetDocument.GestureLockSlot ul =
                new GamepadLayoutPresetDocument.GestureLockSlot();
        ul.action = GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_HOLD_LOCK;
        g.upLeft = ul;
        b.gestureLock = g;
        assertTrue(GamepadGestureLock.moduleUsesDiagonalGestureCommit(b));
        assertFalse(GamepadGestureLock.moduleUsesDiagonalGestureCommit(new GamepadLayoutPresetDocument.GamepadModule()));
    }
}
