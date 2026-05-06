package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GamepadLayoutPresetConstantsSplitGeometryTest {

    @Test
    public void largestGapUpToOuter_returnsUpperWhenAlreadyFeasible() {
        float upper = 0.38f;
        float outer = 1.0f;
        float g = GamepadLayoutPresetConstants.largestGapRatioUpToOuterReach(outer, upper);
        assertEquals(upper, g, 1e-5f);
    }

    @Test
    public void largestGapUpToOuter_shrinksWhenOuterTooTightForWideGap() {
        float upper = 0.38f;
        float outer = 0.72f;
        float g = GamepadLayoutPresetConstants.largestGapRatioUpToOuterReach(outer, upper);
        assertTrue(g < upper);
        assertTrue(GamepadLayoutPresetConstants.minOuterReachRatioForGapRatio(g) <= outer + 1e-4f);
    }

    @Test
    public void largestGapUpToOuter_returnsMinWhenOuterBelowMinimumFeasible() {
        float upper = 0.38f;
        float outer = GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_MIN;
        float g = GamepadLayoutPresetConstants.largestGapRatioUpToOuterReach(outer, upper);
        assertEquals(GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_MIN, g, 1e-4f);
    }
}
