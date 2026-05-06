package com.openterface.keymod;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AlternatePopupGeometryTest {

    private static boolean[] allOn() {
        boolean[] o = new boolean[AlternatePopupGeometry.SLOT_COUNT];
        java.util.Arrays.fill(o, true);
        return o;
    }

    private static int pick(float dx, float dy, boolean[] occupied) {
        return AlternatePopupGeometry.pickSlot(dx, dy, 10f, 200f, 20f, occupied);
    }

    @Test
    public void insideMinRadius_returnsDefault() {
        int r = pick(3f, 2f, allOn());
        assertEquals(AlternatePopupGeometry.RESULT_DEFAULT, r);
    }

    @Test
    public void beyondCancel_returnsCancel() {
        int r = AlternatePopupGeometry.pickSlot(500f, 0f, 10f, 200f, 20f, allOn());
        assertEquals(AlternatePopupGeometry.RESULT_CANCEL, r);
    }

    @Test
    public void neutralCross_returnsDefault() {
        float s = 16f;
        int r = pick(s, s, allOn());
        assertEquals(AlternatePopupGeometry.RESULT_DEFAULT, r);
    }

    @Test
    public void straightUp_selectsSlotUp() {
        int r = pick(0f, -80f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_UP, r);
    }

    /** KM Basic hold-lock popup only enables the up cell (same geometry as full alternates). */
    @Test
    public void straightUp_selectsSlotUp_whenOnlyUpOccupied() {
        boolean[] o = new boolean[AlternatePopupGeometry.SLOT_COUNT];
        o[AlternatePopupGeometry.SLOT_UP] = true;
        int r = pick(0f, -80f, o);
        assertEquals(AlternatePopupGeometry.SLOT_UP, r);
    }

    @Test
    public void straightDown_selectsSlotDown() {
        int r = pick(0f, 80f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_DOWN, r);
    }

    @Test
    public void straightLeft_selectsSlotLeft() {
        int r = pick(-80f, 0f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_LEFT, r);
    }

    @Test
    public void straightRight_selectsSlotRight() {
        int r = pick(80f, 0f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_RIGHT, r);
    }

    @Test
    public void upLeft_selectsUpLeft() {
        int r = pick(-80f, -80f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_UP_LEFT, r);
    }

    @Test
    public void upRight_selectsUpRight() {
        int r = pick(80f, -80f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_UP_RIGHT, r);
    }

    @Test
    public void downLeft_selectsDownLeft() {
        int r = pick(-80f, 80f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_DOWN_LEFT, r);
    }

    @Test
    public void downRight_selectsDownRight() {
        int r = pick(80f, 80f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_DOWN_RIGHT, r);
    }

    @Test
    public void emptySlot_returnsCancel() {
        boolean[] o = allOn();
        o[AlternatePopupGeometry.SLOT_UP_RIGHT] = false;
        int r = AlternatePopupGeometry.pickSlot(80f, -80f, 10f, 200f, 20f, o);
        assertEquals(AlternatePopupGeometry.RESULT_CANCEL, r);
    }

    @Test
    public void diagonalMarkedOccupied_doesNotCancel() {
        boolean[] o = new boolean[AlternatePopupGeometry.SLOT_COUNT];
        // Runtime gesture fallback can mark empty diagonals occupied when vertical cardinal exists.
        o[AlternatePopupGeometry.SLOT_UP] = true;
        o[AlternatePopupGeometry.SLOT_UP_RIGHT] = true;
        int r = AlternatePopupGeometry.pickSlot(80f, -80f, 10f, 200f, 20f, o);
        assertEquals(AlternatePopupGeometry.SLOT_UP_RIGHT, r);
    }

    @Test
    public void diagonalNotOccupied_stillCancels() {
        boolean[] o = new boolean[AlternatePopupGeometry.SLOT_COUNT];
        o[AlternatePopupGeometry.SLOT_UP] = true;
        o[AlternatePopupGeometry.SLOT_UP_RIGHT] = false;
        int r = AlternatePopupGeometry.pickSlot(80f, -80f, 10f, 200f, 20f, o);
        assertEquals(AlternatePopupGeometry.RESULT_CANCEL, r);
    }
}
