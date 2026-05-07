package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class GamepadLayoutPresetDocumentTest {

    private static final String TINY_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Test
    public void validateAcceptsMinimalSimpleLayout() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsLayoutWithoutLeftThumbModule() {
        GamepadLayoutPresetDocument doc = minimalDocumentButtonAOnly();
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsOnlyAuxLeftStickWithoutPrimary() {
        GamepadLayoutPresetDocument doc = minimalDocumentButtonAOnly();
        GamepadLayoutPresetDocument.GamepadModule aux = new GamepadLayoutPresetDocument.GamepadModule();
        aux.id = "stick_left_2";
        aux.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        aux.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        aux.zIndex = 0;
        aux.scale = 1.0f;
        aux.anchorX = 0.2f;
        aux.anchorY = 0.5f;
        aux.stickUpKey = 26;
        aux.stickLeftKey = 4;
        aux.stickDownKey = 22;
        aux.stickRightKey = 7;
        doc.modules.add(0, aux);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void upgradeV6ToV7BumpsSchemaVersion() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V6;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
    }

    @Test
    public void addLeftDpadStickModuleCreatesPrimaryWhenMissing() {
        GamepadLayoutPresetDocument doc = minimalDocumentButtonAOnly();
        GamepadLayoutDocEditor.addLeftDpadStickModule(doc);
        GamepadLayoutPresetDocument.GamepadModule left = findModule(doc, "stick_left");
        assertNotNull(left);
        assertEquals(GamepadLayoutPresetConstants.MODULE_TYPE_DPAD, left.type);
        assertEquals(GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS, left.dpadVariant);
    }

    @Test
    public void addLeftDpadStickModuleAddsAuxWhenPrimaryPresent() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutDocEditor.addLeftDpadStickModule(doc);
        assertNotNull(findModule(doc, "stick_left"));
        assertNotNull(findModule(doc, "stick_left_2"));
    }

    @Test
    public void duplicateModuleCreatesNumericButtonId() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        assertTrue(GamepadLayoutDocEditor.canDuplicateModule("button_a", doc));
        String nid = GamepadLayoutDocEditor.duplicateModule(doc, "button_a");
        assertNotNull(nid);
        assertEquals("button_2", nid);
        assertEquals(40, (int) findModule(doc, "button_2").hidKey);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void duplicateModuleRefusesStickRight() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouse();
        assertFalse(GamepadLayoutDocEditor.canDuplicateModule("stick_right", doc));
        assertNull(GamepadLayoutDocEditor.duplicateModule(doc, "stick_right"));
    }

    @Test
    public void duplicateLeftStickCreatesAuxId() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        assertTrue(GamepadLayoutDocEditor.canDuplicateModule("stick_left", doc));
        String nid = GamepadLayoutDocEditor.duplicateModule(doc, "stick_left");
        assertEquals("stick_left_2", nid);
        GamepadLayoutPresetDocument.GamepadModule copy = findModule(doc, "stick_left_2");
        assertNotNull(copy);
        assertEquals(GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY, copy.type);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void duplicateModuleRefusesDpadSplitPrimary() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule sl = findModule(doc, "stick_left");
        sl.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        sl.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT;
        sl.dpadSplitGapRatio = 0.25f;
        sl.dpadSplitOuterReachRatio = 0.9f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertFalse(GamepadLayoutDocEditor.canDuplicateModule("stick_left", doc));
    }

    @Test
    public void validateAcceptsDpadCrossLeftStick() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        doc.modules.get(0).dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsAuxLeftStickDpadCross() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule aux = new GamepadLayoutPresetDocument.GamepadModule();
        aux.id = "stick_left_2";
        aux.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        aux.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        aux.zIndex = 5;
        aux.scale = 1.0f;
        aux.anchorX = 0.12f;
        aux.anchorY = 0.42f;
        aux.stickUpKey = 26;
        aux.stickLeftKey = 4;
        aux.stickDownKey = 22;
        aux.stickRightKey = 7;
        doc.modules.add(aux);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsAuxLeftStickDpadSplit() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule aux = new GamepadLayoutPresetDocument.GamepadModule();
        aux.id = "stick_left_2";
        aux.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        aux.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT;
        aux.zIndex = 5;
        aux.scale = 1.0f;
        aux.anchorX = 0.12f;
        aux.anchorY = 0.42f;
        aux.stickUpKey = 26;
        aux.stickLeftKey = 4;
        aux.stickDownKey = 22;
        aux.stickRightKey = 7;
        doc.modules.add(aux);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsStickLeft1ModuleId() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule bad = new GamepadLayoutPresetDocument.GamepadModule();
        bad.id = "stick_left_1";
        bad.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        bad.zIndex = 5;
        bad.scale = 1.0f;
        bad.anchorX = 0.12f;
        bad.anchorY = 0.42f;
        bad.stickUpKey = 26;
        bad.stickLeftKey = 4;
        bad.stickDownKey = 22;
        bad.stickRightKey = 7;
        doc.modules.add(bad);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void nextAuxLeftStickModuleIdSequential() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        assertEquals("stick_left_2", GamepadLayoutDocumentStore.nextAuxLeftStickModuleId(doc));
        GamepadLayoutPresetDocument.GamepadModule aux = new GamepadLayoutPresetDocument.GamepadModule();
        aux.id = "stick_left_2";
        aux.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        aux.zIndex = 5;
        aux.scale = 1.0f;
        aux.anchorX = 0.12f;
        aux.anchorY = 0.42f;
        aux.stickUpKey = 26;
        aux.stickLeftKey = 4;
        aux.stickDownKey = 22;
        aux.stickRightKey = 7;
        doc.modules.add(aux);
        assertEquals("stick_left_3", GamepadLayoutDocumentStore.nextAuxLeftStickModuleId(doc));
    }

    @Test
    public void stickCenterKeyRoundTripsInJson() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule left = doc.modules.get(0);
        left.stickCenterKey = 44;
        String json = GamepadLayoutPresetDocument.toJsonPretty(doc);
        GamepadLayoutPresetDocument parsed = GamepadLayoutPresetDocument.parseOrNull(json);
        assertNotNull(parsed);
        assertEquals(Integer.valueOf(44), parsed.modules.get(0).stickCenterKey);
    }

    @Test
    public void validateAcceptsDpadCrossWithStickCenterKey() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        doc.modules.get(0).dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        doc.modules.get(0).stickCenterKey = 40;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void stickPointerCenterFieldsRoundTripJson() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouse();
        GamepadLayoutPresetDocument.GamepadModule right = findModule(doc, "stick_right");
        assertNotNull(right);
        right.stickPointerCenterKey = 35;
        right.stickPointerCenterMouseMask = 4;
        String json = GamepadLayoutPresetDocument.toJsonPretty(doc);
        GamepadLayoutPresetDocument parsed = GamepadLayoutPresetDocument.parseOrNull(json);
        assertNotNull(parsed);
        GamepadLayoutPresetDocument.GamepadModule r2 = findModule(parsed, "stick_right");
        assertEquals(Integer.valueOf(35), r2.stickPointerCenterKey);
        assertEquals(Integer.valueOf(4), r2.stickPointerCenterMouseMask);
    }

    @Test
    public void validateAcceptsStickMousePointerHubMouseMask() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouse();
        findModule(doc, "stick_right").stickPointerCenterMouseMask = 2;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsStickPointerCenterMouseMaskInvalid() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouse();
        findModule(doc, "stick_right").stickPointerCenterMouseMask = 3;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsDpadSplitWithGapRatio() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        doc.modules.get(0).dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT;
        doc.modules.get(0).dpadSplitGapRatio = 0.22f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsDpadSplitGapOnCross() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        doc.modules.get(0).dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        doc.modules.get(0).dpadSplitGapRatio = 0.2f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsDpadSplitGapOutOfRange() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        doc.modules.get(0).dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT;
        doc.modules.get(0).dpadSplitGapRatio = 0.99f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsDpadSplitWithGapAndOuterReach() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        doc.modules.get(0).dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT;
        doc.modules.get(0).dpadSplitGapRatio = 0.22f;
        doc.modules.get(0).dpadSplitOuterReachRatio = 0.72f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsDpadSplitOuterOnCross() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        doc.modules.get(0).dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        doc.modules.get(0).dpadSplitOuterReachRatio = 0.9f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsDpadSplitOuterTooSmallForGap() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        doc.modules.get(0).dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT;
        doc.modules.get(0).dpadSplitGapRatio = 0.38f;
        doc.modules.get(0).dpadSplitOuterReachRatio = 0.28f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsDpadWithoutVariant() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        doc.modules.get(0).dpadVariant = null;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsDpadOnRightStick() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouse();
        GamepadLayoutPresetDocument.GamepadModule right = findModule(doc, "stick_right");
        assertNotNull(right);
        right.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        right.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsStickOtherId() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule extra = new GamepadLayoutPresetDocument.GamepadModule();
        extra.id = "stick_other";
        extra.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        extra.zIndex = 5;
        extra.scale = 1f;
        extra.anchorX = 0.5f;
        extra.anchorY = 0.6f;
        extra.stickUpKey = 82;
        extra.stickLeftKey = 80;
        extra.stickDownKey = 81;
        extra.stickRightKey = 79;
        doc.modules.add(extra);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void roundTripJson() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        String json = GamepadLayoutPresetDocument.toJsonPretty(doc);
        assertTrue(json.contains(GamepadLayoutPresetConstants.DOCUMENT_FORMAT));
        GamepadLayoutPresetDocument parsed = GamepadLayoutPresetDocument.parseOrNull(json);
        assertNotNull(parsed);
        GamepadLayoutPresetDocument.validateOrThrow(parsed);
        assertEquals(doc.modules.size(), parsed.modules.size());
        assertEquals("stick_left", parsed.modules.get(0).id);
    }

    @Test
    public void roundTripJsonPreservesModuleAccentArgb() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).moduleAccentArgb = 0xFFE91E63;
        String json = GamepadLayoutPresetDocument.toJsonPretty(doc);
        GamepadLayoutPresetDocument parsed = GamepadLayoutPresetDocument.parseOrNull(json);
        assertNotNull(parsed);
        GamepadLayoutPresetDocument.validateOrThrow(parsed);
        assertNotNull(parsed.modules.get(0).moduleAccentArgb);
        assertEquals(0xFFE91E63, (int) parsed.modules.get(0).moduleAccentArgb);
    }

    @Test
    public void roundTripJsonPreservesMappedKeyLabelVisible() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule btnA = doc.modules.get(1);
        assertEquals("button_a", btnA.id);
        btnA.mappedKeyLabelVisible = Boolean.FALSE;
        String json = GamepadLayoutPresetDocument.toJsonPretty(doc);
        GamepadLayoutPresetDocument parsed = GamepadLayoutPresetDocument.parseOrNull(json);
        assertNotNull(parsed);
        GamepadLayoutPresetDocument.validateOrThrow(parsed);
        assertNotNull(parsed.modules.get(1).mappedKeyLabelVisible);
        assertEquals(Boolean.FALSE, parsed.modules.get(1).mappedKeyLabelVisible);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsModuleAccentTooTransparent() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).moduleAccentArgb = 0x10000000;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsWrongFormat() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.format = "other";
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsCompleteBackgroundEmbed() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.layout.backgroundImageEncoding = GamepadLayoutPresetConstants.BACKGROUND_EMBED_ENCODING_BASE64;
        doc.layout.backgroundImageMediaType = GamepadLayoutPresetConstants.BACKGROUND_MEDIA_TYPE_PNG;
        doc.layout.backgroundImageData = TINY_PNG_BASE64;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsPartialBackgroundEmbed() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.layout.backgroundImageEncoding = GamepadLayoutPresetConstants.BACKGROUND_EMBED_ENCODING_BASE64;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsUnknownBackgroundEncoding() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.layout.backgroundImageEncoding = "hex";
        doc.layout.backgroundImageMediaType = GamepadLayoutPresetConstants.BACKGROUND_MEDIA_TYPE_PNG;
        doc.layout.backgroundImageData = TINY_PNG_BASE64;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsUnsupportedBackgroundMediaType() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.layout.backgroundImageEncoding = GamepadLayoutPresetConstants.BACKGROUND_EMBED_ENCODING_BASE64;
        doc.layout.backgroundImageMediaType = "image/gif";
        doc.layout.backgroundImageData = TINY_PNG_BASE64;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void upgradeV1SchemaThenValidates() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V1;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
    }

    @Test
    public void upgradeV2WasdCrossStringToV3Dpad() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V2;
        doc.modules.get(0).type = "WASD_CROSS";
        doc.modules.get(0).dpadVariant = null;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
        assertEquals(GamepadLayoutPresetConstants.MODULE_TYPE_DPAD, doc.modules.get(0).type);
        assertEquals(GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS, doc.modules.get(0).dpadVariant);
    }

    @Test
    public void validateAcceptsStickLayoutTemplate() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.layout.stickLayoutTemplate = GamepadLayoutPresetConstants.STICK_LAYOUT_OFFSET;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsInvalidStickLayoutTemplate() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.layout.stickLayoutTemplate = "xbox_only";
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsRightStickTouchpadMouseButtonsAndGain() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouse();
        doc.layout.rightStickMouseGain = 1.5f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsMultipleTouchpads() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouse();
        GamepadLayoutPresetDocument.GamepadModule tp2 = new GamepadLayoutPresetDocument.GamepadModule();
        tp2.id = "touchpad_2";
        tp2.type = GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD;
        tp2.zIndex = 50;
        tp2.scale = 1f;
        tp2.anchorX = 0.3f;
        tp2.anchorY = 0.4f;
        tp2.widthNorm = 0.22f;
        tp2.heightNorm = 0.22f;
        doc.modules.add(tp2);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsTouchpadWithNonNumericSuffixId() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouse();
        GamepadLayoutPresetDocument.GamepadModule bad = new GamepadLayoutPresetDocument.GamepadModule();
        bad.id = "touchpad_extra";
        bad.type = GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD;
        bad.zIndex = 50;
        bad.scale = 1f;
        bad.anchorX = 0.3f;
        bad.anchorY = 0.4f;
        bad.widthNorm = 0.22f;
        bad.heightNorm = 0.22f;
        doc.modules.add(bad);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsRightStickMouseGainOutOfRange() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.layout.rightStickMouseGain = 10f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsUnknownStickId() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouse();
        GamepadLayoutPresetDocument.GamepadModule rogue = new GamepadLayoutPresetDocument.GamepadModule();
        rogue.id = "joystick_1";
        rogue.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        rogue.zIndex = 99;
        rogue.scale = 1f;
        rogue.anchorX = 0.5f;
        rogue.anchorY = 0.5f;
        rogue.stickUpKey = 82;
        rogue.stickLeftKey = 80;
        rogue.stickDownKey = 81;
        rogue.stickRightKey = 79;
        doc.modules.add(rogue);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsDuplicateStickModuleId() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouse();
        GamepadLayoutPresetDocument.GamepadModule dup = new GamepadLayoutPresetDocument.GamepadModule();
        dup.id = "stick_left";
        dup.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        dup.zIndex = 100;
        dup.scale = 1f;
        dup.anchorX = 0.1f;
        dup.anchorY = 0.1f;
        dup.stickUpKey = 26;
        dup.stickLeftKey = 4;
        dup.stickDownKey = 22;
        dup.stickRightKey = 7;
        doc.modules.add(dup);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsMouseButtonIdMismatch() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule mb = new GamepadLayoutPresetDocument.GamepadModule();
        mb.id = GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID;
        mb.type = GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON;
        mb.zIndex = 5;
        mb.scale = 1f;
        mb.anchorX = 0.5f;
        mb.anchorY = 0.5f;
        mb.mouseButton = 3;
        doc.modules.add(mb);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void upgradeV3ToV4Schema() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V3;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
    }

    @Test
    public void upgradeV4ThroughV6KeepsStickRightId() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V4;
        GamepadLayoutPresetDocument.GamepadModule right = new GamepadLayoutPresetDocument.GamepadModule();
        right.id = "stick_right";
        right.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
        right.zIndex = 2;
        right.scale = 1f;
        right.anchorX = 0.75f;
        right.anchorY = 0.5f;
        right.stickUpKey = 12;
        right.stickLeftKey = 13;
        right.stickDownKey = 14;
        right.stickRightKey = 15;
        doc.modules.add(right);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
        GamepadLayoutPresetDocument.GamepadModule migratedRight = findModule(doc, "stick_right");
        assertNotNull(migratedRight);
        assertEquals(GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE, migratedRight.type);
    }

    @Test
    public void upgradeV5StickKeyExtraRenamesToStickRight() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V5;
        GamepadLayoutPresetDocument.GamepadModule extra = new GamepadLayoutPresetDocument.GamepadModule();
        extra.id = GamepadLayoutPresetConstants.LEGACY_STICK_KEY_EXTRA_MODULE_ID;
        extra.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
        extra.zIndex = 2;
        extra.scale = 1f;
        extra.anchorX = 0.75f;
        extra.anchorY = 0.5f;
        extra.stickUpKey = 12;
        extra.stickLeftKey = 13;
        extra.stickDownKey = 14;
        extra.stickRightKey = 15;
        extra.stickMouseSensitivity = 1.5f;
        doc.modules.add(extra);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
        assertNull(findModule(doc, GamepadLayoutPresetConstants.LEGACY_STICK_KEY_EXTRA_MODULE_ID));
        GamepadLayoutPresetDocument.GamepadModule right = findModule(doc, "stick_right");
        assertNotNull(right);
        assertEquals(Float.valueOf(1.5f), right.stickMouseSensitivity);
    }

    @Test
    public void validateAcceptsRightStickMouseWithSensitivity() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule right = new GamepadLayoutPresetDocument.GamepadModule();
        right.id = "stick_right";
        right.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
        right.zIndex = 3;
        right.scale = 1f;
        right.anchorX = 0.75f;
        right.anchorY = 0.5f;
        right.stickUpKey = 12;
        right.stickLeftKey = 13;
        right.stickDownKey = 14;
        right.stickRightKey = 15;
        right.stickMouseSensitivity = 1.5f;
        doc.modules.add(right);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsStickMouseSensitivityOnStickKey() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule right = new GamepadLayoutPresetDocument.GamepadModule();
        right.id = "stick_right";
        right.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        right.zIndex = 3;
        right.scale = 1f;
        right.anchorX = 0.75f;
        right.anchorY = 0.5f;
        right.stickUpKey = 12;
        right.stickLeftKey = 13;
        right.stickDownKey = 14;
        right.stickRightKey = 15;
        right.stickMouseSensitivity = 1.5f;
        doc.modules.add(right);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsObsoleteStickKeyExtraAtCurrentSchema() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        GamepadLayoutPresetDocument.GamepadModule legacy = new GamepadLayoutPresetDocument.GamepadModule();
        legacy.id = GamepadLayoutPresetConstants.LEGACY_STICK_KEY_EXTRA_MODULE_ID;
        legacy.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        legacy.zIndex = 2;
        legacy.scale = 1f;
        legacy.anchorX = 0.75f;
        legacy.anchorY = 0.5f;
        legacy.stickUpKey = 82;
        legacy.stickLeftKey = 80;
        legacy.stickDownKey = 81;
        legacy.stickRightKey = 79;
        doc.modules.add(legacy);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsInvalidFaceButtonTemplate() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.layout.faceButtonTemplate = "custom_cluster";
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsFaceButtonTemplateAndGyro() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.layout.faceButtonTemplate = GamepadLayoutPresetConstants.FACE_TEMPLATE_NINTENDO_DIAMOND;
        doc.layout.gyroEnabled = Boolean.TRUE;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsStickVisualVariantOnStick() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).stickVisualVariant = GamepadLayoutPresetConstants.STICK_VISUAL_CONCAVE;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsStickVisualVariantOnButton() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(1).stickVisualVariant = GamepadLayoutPresetConstants.STICK_VISUAL_CONVEX;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsButtonCornerRadiusNorm() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(1).buttonCornerRadiusNorm = 0f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        doc.modules.get(1).buttonCornerRadiusNorm = 1f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsButtonCornerRadiusNormOutOfRange() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(1).buttonCornerRadiusNorm = 1.01f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsButtonCornerRadiusNormOnStick() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).buttonCornerRadiusNorm = 0.5f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsButtonShapeRatiosAndRotation() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(1).buttonWidthRatio = 2f;
        doc.modules.get(1).buttonHeightRatio = 0.6f;
        doc.modules.get(1).buttonRotationDeg = 45f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsButtonWidthRatioOutOfRange() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(1).buttonWidthRatio = 10f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsButtonShapeRatioOnStick() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.modules.get(0).buttonWidthRatio = 1.5f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void validateAcceptsShoulderAndTriggerModules() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule sl = new GamepadLayoutPresetDocument.GamepadModule();
        sl.id = GamepadLayoutPresetConstants.SHOULDER_L_ID;
        sl.type = GamepadLayoutPresetConstants.MODULE_TYPE_SHOULDER;
        sl.zIndex = 8;
        sl.scale = 1f;
        sl.anchorX = 0.12f;
        sl.anchorY = 0.08f;
        sl.hidKey = 58;
        sl.displayLabel = "L1";
        doc.modules.add(sl);
        GamepadLayoutPresetDocument.GamepadModule tr = new GamepadLayoutPresetDocument.GamepadModule();
        tr.id = GamepadLayoutPresetConstants.TRIGGER_R_ID;
        tr.type = GamepadLayoutPresetConstants.MODULE_TYPE_TRIGGER;
        tr.zIndex = 9;
        tr.scale = 1f;
        tr.anchorX = 0.88f;
        tr.anchorY = 0.92f;
        tr.hidKey = 61;
        tr.triggerVariant = GamepadLayoutPresetConstants.TRIGGER_VARIANT_DIGITAL;
        tr.displayLabel = "RT";
        doc.modules.add(tr);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void applyFaceButtonTemplateNintendo() throws Exception {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadFaceButtonTemplates.applyTemplate(doc, GamepadLayoutPresetConstants.FACE_TEMPLATE_NINTENDO_DIAMOND);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertTrue(findModule(doc, "button_y") != null);
    }

    private static GamepadLayoutPresetDocument.GamepadModule findModule(
            GamepadLayoutPresetDocument doc, String id) {
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (m != null && id.equals(m.id)) {
                return m;
            }
        }
        return null;
    }

    private static GamepadLayoutPresetDocument minimalDocumentButtonAOnly() {
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.format = GamepadLayoutPresetConstants.DOCUMENT_FORMAT;
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        doc.meta = new GamepadLayoutPresetDocument.Meta();
        doc.meta.id = "test";
        doc.meta.displayName = "Test";
        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        doc.layout.mouseSensitivity = 1.0f;
        doc.layout.showTwoButtons = false;
        List<GamepadLayoutPresetDocument.GamepadModule> modules = new ArrayList<>();
        GamepadLayoutPresetDocument.GamepadModule btnA = new GamepadLayoutPresetDocument.GamepadModule();
        btnA.id = "button_a";
        btnA.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        btnA.zIndex = 0;
        btnA.scale = 1.0f;
        btnA.anchorX = 0.85f;
        btnA.anchorY = 0.5f;
        btnA.hidKey = 40;
        btnA.modifierMask = 0;
        modules.add(btnA);
        doc.modules = modules;
        return doc;
    }

    private static GamepadLayoutPresetDocument minimalValidDocument() {
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.format = GamepadLayoutPresetConstants.DOCUMENT_FORMAT;
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        doc.meta = new GamepadLayoutPresetDocument.Meta();
        doc.meta.id = "test";
        doc.meta.displayName = "Test";
        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        doc.layout.mouseSensitivity = 1.0f;
        doc.layout.showTwoButtons = false;
        List<GamepadLayoutPresetDocument.GamepadModule> modules = new ArrayList<>();
        GamepadLayoutPresetDocument.GamepadModule stick = new GamepadLayoutPresetDocument.GamepadModule();
        stick.id = "stick_left";
        stick.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        stick.zIndex = 0;
        stick.scale = 1.0f;
        stick.anchorX = 0.2f;
        stick.anchorY = 0.5f;
        stick.stickUpKey = 26;
        stick.stickLeftKey = 4;
        stick.stickDownKey = 22;
        stick.stickRightKey = 7;
        modules.add(stick);
        GamepadLayoutPresetDocument.GamepadModule btnA = new GamepadLayoutPresetDocument.GamepadModule();
        btnA.id = "button_a";
        btnA.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        btnA.zIndex = 1;
        btnA.scale = 1.0f;
        btnA.anchorX = 0.85f;
        btnA.anchorY = 0.5f;
        btnA.hidKey = 40;
        btnA.modifierMask = 0;
        modules.add(btnA);
        doc.modules = modules;
        return doc;
    }

    private static GamepadLayoutPresetDocument layoutWithRightStickTouchpadMouse() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule right = new GamepadLayoutPresetDocument.GamepadModule();
        right.id = "stick_right";
        right.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
        right.zIndex = 2;
        right.scale = 1f;
        right.anchorX = 0.75f;
        right.anchorY = 0.5f;
        right.stickUpKey = 12;
        right.stickLeftKey = 13;
        right.stickDownKey = 14;
        right.stickRightKey = 15;
        doc.modules.add(right);

        GamepadLayoutPresetDocument.GamepadModule tp = new GamepadLayoutPresetDocument.GamepadModule();
        tp.id = "touchpad_1";
        tp.type = GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD;
        tp.zIndex = 4;
        tp.scale = 1f;
        tp.anchorX = 0.5f;
        tp.anchorY = 0.35f;
        tp.widthNorm = 0.28f;
        tp.heightNorm = 0.28f;
        doc.modules.add(tp);

        doc.modules.add(mouseModule(GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID, 1, 5));
        doc.modules.add(mouseModule(GamepadLayoutPresetConstants.MOUSE_BTN_MIDDLE_ID, 2, 6));
        doc.modules.add(mouseModule(GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID, 3, 7));
        return doc;
    }

    private static GamepadLayoutPresetDocument.GamepadModule mouseModule(String id, int btn, int z) {
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = id;
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON;
        m.zIndex = z;
        m.scale = 1f;
        m.anchorX = 0.55f;
        m.anchorY = 0.2f;
        m.mouseButton = btn;
        return m;
    }
}
