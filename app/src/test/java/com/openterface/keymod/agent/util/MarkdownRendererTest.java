package com.openterface.keymod.agent.util;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.text.Spanned;

/**
 * Unit tests for {@link MarkdownRenderer}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MarkdownRendererTest {

    // ── Empty input ────────────────────────────────────────────────────

    @Test
    public void testToSpannedEmpty() {
        Spanned result = MarkdownRenderer.toSpanned("");
        assertNotNull(result);
        assertEquals(0, result.length());
    }

    // ── Plain text ─────────────────────────────────────────────────────

    @Test
    public void testToSpannedPlainText() {
        Spanned result = MarkdownRenderer.toSpanned("Hello world");
        assertNotNull(result);
        assertTrue(result.toString().contains("Hello world"));
    }

    // ── Headings ───────────────────────────────────────────────────────

    @Test
    public void testToSpannedHeading() {
        Spanned result = MarkdownRenderer.toSpanned("# Heading 1");
        assertNotNull(result);
        assertTrue(result.toString().contains("Heading 1"));
    }

    @Test
    public void testToSpannedMultipleHeadingLevels() {
        String md = "# H1\n## H2\n### H3\n#### H4";
        Spanned result = MarkdownRenderer.toSpanned(md);
        assertNotNull(result);
        String text = result.toString();
        assertTrue(text.contains("H1"));
        assertTrue(text.contains("H2"));
        assertTrue(text.contains("H3"));
        assertTrue(text.contains("H4"));
    }

    // ── Bold / Italic ──────────────────────────────────────────────────

    @Test
    public void testToSpannedBold() {
        Spanned result = MarkdownRenderer.toSpanned("This is **bold** text");
        assertNotNull(result);
        assertTrue(result.toString().contains("bold"));
    }

    @Test
    public void testToSpannedItalic() {
        Spanned result = MarkdownRenderer.toSpanned("This is *italic* text");
        assertNotNull(result);
        assertTrue(result.toString().contains("italic"));
    }

    // ── Inline code ────────────────────────────────────────────────────

    @Test
    public void testToSpannedInlineCode() {
        Spanned result = MarkdownRenderer.toSpanned("Use `println!()` macro");
        assertNotNull(result);
        assertTrue(result.toString().contains("println!()"));
    }

    // ── Code blocks ────────────────────────────────────────────────────

    @Test
    public void testToSpannedCodeBlock() {
        String md = "```java\nSystem.out.println(\"Hello\");\n```";
        Spanned result = MarkdownRenderer.toSpanned(md);
        assertNotNull(result);
        assertTrue(result.toString().contains("System.out.println"));
    }

    @Test
    public void testToSpannedCodeBlockWithLanguage() {
        String md = "```python\nprint('hello')\n```";
        Spanned result = MarkdownRenderer.toSpanned(md);
        assertNotNull(result);
        assertTrue(result.toString().contains("print"));
    }

    // ── Lists ──────────────────────────────────────────────────────────

    @Test
    public void testToSpannedUnorderedList() {
        String md = "- Item 1\n- Item 2\n- Item 3";
        Spanned result = MarkdownRenderer.toSpanned(md);
        assertNotNull(result);
        String text = result.toString();
        assertTrue(text.contains("Item 1"));
        assertTrue(text.contains("Item 2"));
        assertTrue(text.contains("Item 3"));
    }

    @Test
    public void testToSpannedOrderedList() {
        String md = "1. First\n2. Second\n3. Third";
        Spanned result = MarkdownRenderer.toSpanned(md);
        assertNotNull(result);
        String text = result.toString();
        assertTrue(text.contains("First"));
        assertTrue(text.contains("Second"));
        assertTrue(text.contains("Third"));
    }

    // ── Blockquotes ────────────────────────────────────────────────────

    @Test
    public void testToSpannedBlockquote() {
        Spanned result = MarkdownRenderer.toSpanned("> This is a quote");
        assertNotNull(result);
        assertTrue(result.toString().contains("This is a quote"));
    }

    // ── Horizontal rule ────────────────────────────────────────────────

    @Test
    public void testToSpannedHorizontalRule() {
        Spanned result = MarkdownRenderer.toSpanned("Before\n\n---\n\nAfter");
        assertNotNull(result);
        String text = result.toString();
        assertTrue(text.contains("Before"));
        assertTrue(text.contains("After"));
    }

    // ── Tables ─────────────────────────────────────────────────────────

    @Test
    public void testToSpannedTableDoesNotCrash() {
        String md = "| Col1 | Col2 |\n|---|---|\n| A | B |\n| C | D |";
        Spanned result = MarkdownRenderer.toSpanned(md);
        assertNotNull(result);
        String text = result.toString();
        assertTrue(text.contains("Col1"));
        assertTrue(text.contains("A"));
    }

    // ── Links ──────────────────────────────────────────────────────────

    @Test
    public void testToSpannedLink() {
        Spanned result = MarkdownRenderer.toSpanned("[Click here](https://example.com)");
        assertNotNull(result);
        assertTrue(result.toString().contains("Click here"));
    }

    // ── HTML escaping ──────────────────────────────────────────────────

    @Test
    public void testToSpannedEscapesHtml() {
        Spanned result = MarkdownRenderer.toSpanned("Use <div> tag");
        assertNotNull(result);
        // HTML tags should be escaped — not rendered as actual HTML elements
        String text = result.toString();
        assertTrue(text.contains("<div>") || text.contains("div"));
    }

    // ── Strikethrough ──────────────────────────────────────────────────

    @Test
    public void testToSpannedStrikethrough() {
        Spanned result = MarkdownRenderer.toSpanned("~~deleted~~");
        assertNotNull(result);
        assertTrue(result.toString().contains("deleted"));
    }

    // ── Complex document ───────────────────────────────────────────────

    @Test
    public void testToSpannedComplexDocument() {
        String md = "# Title\n\nSome **bold** and *italic* text.\n\n"
                + "- Item 1\n- Item 2\n\n"
                + "```bash\necho hello\n```\n\n"
                + "> A quote\n\n"
                + "| H1 | H2 |\n|---|---|\n| a | b |";
        Spanned result = MarkdownRenderer.toSpanned(md);
        assertNotNull(result);
        String text = result.toString();
        assertTrue(text.contains("Title"));
        assertTrue(text.contains("bold"));
        assertTrue(text.contains("Item 1"));
        assertTrue(text.contains("echo hello"));
        assertTrue(text.contains("A quote"));
    }
}
