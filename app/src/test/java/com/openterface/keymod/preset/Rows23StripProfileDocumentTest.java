package com.openterface.keymod.preset;

import com.google.gson.Gson;
import com.openterface.keymod.ShortcutProfileManager.Shortcut;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class Rows23StripProfileDocumentTest {

    @Test
    public void parseValidate_roundTrip() {
        Rows23StripProfileDocument doc = new Rows23StripProfileDocument();
        doc.format = Rows23StripProfileConstants.DOCUMENT_FORMAT;
        doc.schemaVersion = Rows23StripProfileConstants.SCHEMA_VERSION;
        doc.meta = new Rows23StripProfileDocument.Meta();
        doc.meta.displayName = "Test";
        doc.profile = new Rows23StripProfile();
        doc.profile.id = Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
        doc.profile.name = "Default";
        doc.profile.slotMap = new HashMap<>();
        doc.profile.slotMap.put("p0_r2_c0_base", "s1");
        doc.profile.shortcuts = new java.util.ArrayList<>();
        Shortcut s = new Shortcut("s1", "Hello", "Hello", 0, 0x04);
        doc.profile.shortcuts.add(s);

        Gson gson = new Gson();
        String json = gson.toJson(doc);
        Assert.assertTrue(Rows23StripProfileDocument.looksLikeDocument(json));

        Rows23StripProfileDocument parsed = Rows23StripProfileDocument.parseOrNull(json);
        Assert.assertNotNull(parsed);
        Rows23StripProfileDocument.validateOrThrow(parsed);
        Assert.assertEquals("s1", parsed.profile.slotMap.get("p0_r2_c0_base"));
        Assert.assertEquals(1, parsed.profile.shortcuts.size());
        Assert.assertEquals(0x04, parsed.profile.shortcuts.get(0).keyCode);
    }
}
