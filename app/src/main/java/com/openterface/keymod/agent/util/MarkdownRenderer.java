package com.openterface.keymod.agent.util;

import android.text.Html;
import android.text.Spanned;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown-to-Spanned converter for rendering LLM responses in TextView.
 *
 * <p>Supports block-level elements:
 * <ul>
 *   <li>Headings: {@code # } through {@code ###### }</li>
 *   <li>Code blocks: triple backticks with optional language</li>
 *   <li>Blockquotes: {@code > }</li>
 *   <li>Unordered lists: {@code - } or {@code * }</li>
 *   <li>Ordered lists: {@code 1. }, {@code 2. }, etc.</li>
 *   <li>Tables: pipe-delimited with separator row</li>
 *   <li>Horizontal rules: {@code ---}, {@code ***}, {@code ___}</li>
 *   <li>Paragraphs: separated by blank lines</li>
 * </ul>
 *
 * <p>Inline formatting:
 * <ul>
 *   <li>Bold: {@code **text**} or {@code __text__}</li>
 *   <li>Italic: {@code *text*} or {@code _text_}</li>
 *   <li>Inline code: {@code `code`}</li>
 *   <li>Strikethrough: {@code ~~text~~}</li>
 *   <li>Links: {@code [text](url)}</li>
 * </ul>
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {
        // Static utility class
    }

    /**
     * Convert Markdown text to a Spanned that can be displayed in a TextView.
     *
     * @param markdown the Markdown text to convert
     * @return Spanned text with formatting applied
     */
    @NonNull
    public static Spanned toSpanned(@NonNull String markdown) {
        if (markdown.isEmpty()) {
            return Html.fromHtml("", Html.FROM_HTML_MODE_COMPACT);
        }

        String html = markdownToHtml(markdown);
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
    }

    /**
     * Convert Markdown to HTML using a line-by-line state machine.
     */
    @NonNull
    private static String markdownToHtml(@NonNull String markdown) {
        StringBuilder html = new StringBuilder();
        String[] lines = markdown.split("\n", -1);

        boolean inCodeBlock = false;
        boolean inList = false;
        String listType = null; // "ul" or "ol"
        boolean inBlockquote = false;
        boolean inTable = false;
        StringBuilder codeBlockContent = new StringBuilder();
        List<List<String>> tableRows = new ArrayList<>();
        int tableHeaderRowCount = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // ── Code block state ──
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // End code block
                    html.append("<pre><code>");
                    html.append(escapeHtml(codeBlockContent.toString().trim()));
                    html.append("</code></pre>");
                    codeBlockContent.setLength(0);
                    inCodeBlock = false;
                } else {
                    // Close any open block first
                    if (inList) { html.append(closeList(listType)); inList = false; listType = null; }
                    if (inBlockquote) { html.append("</blockquote>"); inBlockquote = false; }
                    if (inTable) { html.append(flushTable(tableRows, tableHeaderRowCount)); tableRows.clear(); tableHeaderRowCount = 0; inTable = false; }
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                codeBlockContent.append(line).append("\n");
                continue;
            }

            // ── Table detection ──
            if (isTableRow(trimmed)) {
                // Close other blocks if needed
                if (inList) { html.append(closeList(listType)); inList = false; listType = null; }
                if (inBlockquote) { html.append("</blockquote>"); inBlockquote = false; }

                if (isTableSeparator(trimmed)) {
                    // Separator row — marks previous row as header
                    tableHeaderRowCount = tableRows.size();
                    tableRows.add(parseTableRow(trimmed));
                    inTable = true;
                } else {
                    tableRows.add(parseTableRow(trimmed));
                    inTable = true;
                }
                continue;
            } else if (inTable) {
                // Table ended — flush it
                html.append(flushTable(tableRows, tableHeaderRowCount));
                tableRows.clear();
                tableHeaderRowCount = 0;
                inTable = false;
            }

            // ── Empty line → paragraph break ──
            if (trimmed.isEmpty()) {
                if (inList) { html.append(closeList(listType)); inList = false; listType = null; }
                if (inBlockquote) { html.append("</blockquote>"); inBlockquote = false; }
                html.append("<br>");
                continue;
            }

            // ── Horizontal rule ──
            if (isHorizontalRule(trimmed)) {
                if (inList) { html.append(closeList(listType)); inList = false; listType = null; }
                if (inBlockquote) { html.append("</blockquote>"); inBlockquote = false; }
                html.append("<hr>");
                continue;
            }

            // ── Heading ──
            int headingLevel = getHeadingLevel(trimmed);
            if (headingLevel > 0) {
                if (inList) { html.append(closeList(listType)); inList = false; listType = null; }
                if (inBlockquote) { html.append("</blockquote>"); inBlockquote = false; }
                String content = trimmed.substring(headingLevel).trim();
                html.append("<h").append(headingLevel).append(">");
                html.append(processInlineFormatting(content));
                html.append("</h").append(headingLevel).append(">");
                continue;
            }

            // ── Blockquote ──
            if (trimmed.startsWith("> ")) {
                if (inList) { html.append(closeList(listType)); inList = false; listType = null; }
                if (!inBlockquote) {
                    html.append("<blockquote>");
                    inBlockquote = true;
                }
                html.append(processInlineFormatting(trimmed.substring(2))).append("<br>");
                continue;
            }

            // ── Unordered list ──
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (inBlockquote) { html.append("</blockquote>"); inBlockquote = false; }
                if (!inList || !"ul".equals(listType)) {
                    if (inList) { html.append(closeList(listType)); }
                    html.append("<ul>");
                    inList = true;
                    listType = "ul";
                }
                html.append("<li>").append(processInlineFormatting(trimmed.substring(2))).append("</li>");
                continue;
            }

            // ── Ordered list ──
            int olMatch = getOrderedListIndex(trimmed);
            if (olMatch > 0) {
                if (inBlockquote) { html.append("</blockquote>"); inBlockquote = false; }
                if (!inList || !"ol".equals(listType)) {
                    if (inList) { html.append(closeList(listType)); }
                    html.append("<ol>");
                    inList = true;
                    listType = "ol";
                }
                String content = trimmed.substring(olMatch).trim();
                html.append("<li>").append(processInlineFormatting(content)).append("</li>");
                continue;
            }

            // ── Regular paragraph line ──
            if (inList) { html.append(closeList(listType)); inList = false; listType = null; }
            if (inBlockquote) { html.append("</blockquote>"); inBlockquote = false; }
            html.append(processInlineFormatting(trimmed)).append("<br>");
        }

        // ── Close any open blocks ──
        if (inCodeBlock) {
            html.append("<pre><code>");
            html.append(escapeHtml(codeBlockContent.toString().trim()));
            html.append("</code></pre>");
        }
        if (inList) { html.append(closeList(listType)); }
        if (inBlockquote) { html.append("</blockquote>"); }
        if (inTable) { html.append(flushTable(tableRows, tableHeaderRowCount)); }

        return html.toString();
    }

    // ── Inline formatting ──────────────────────────────────────────────

    /**
     * Process inline Markdown formatting (bold, italic, inline code, strikethrough, links).
     */
    @NonNull
    private static String processInlineFormatting(@NonNull String text) {
        String result = escapeHtml(text);

        // Inline code (must be processed first to avoid conflicts)
        result = result.replaceAll("`([^`]+)`", "<code>$1</code>");

        // Bold: **text** or __text__
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        result = result.replaceAll("__(.+?)__", "<b>$1</b>");

        // Strikethrough: ~~text~~
        result = result.replaceAll("~~(.+?)~~", "<s>$1</s>");

        // Italic: *text* or _text_ (but not inside words)
        result = result.replaceAll("(?<!\\w)\\*(.+?)\\*(?!\\w)", "<i>$1</i>");
        result = result.replaceAll("(?<!\\w)_(.+?)_(?!\\w)", "<i>$1</i>");

        // Links: [text](url) — after HTML escaping, brackets are escaped
        // We need to handle the escaped versions: &#91; &#93;
        // Actually, escapeHtml doesn't escape [ and ], so they remain as-is
        result = result.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)",
                "<a href=\"$2\">$1</a>");

        return result;
    }

    // ── Heading detection ──────────────────────────────────────────────

    /**
     * Returns the heading level (1-6) if the line is a heading, 0 otherwise.
     */
    private static int getHeadingLevel(@NonNull String trimmed) {
        if (!trimmed.startsWith("#")) return 0;
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        if (level > 6) return 0;
        // Must have a space after the hashes (or be just hashes)
        if (level < trimmed.length() && trimmed.charAt(level) != ' ') return 0;
        return level;
    }

    // ── Ordered list detection ─────────────────────────────────────────

    /**
     * Returns the index after "N. " if the line is an ordered list item, 0 otherwise.
     */
    private static int getOrderedListIndex(@NonNull String trimmed) {
        if (trimmed.isEmpty() || !Character.isDigit(trimmed.charAt(0))) return 0;
        int i = 0;
        while (i < trimmed.length() && Character.isDigit(trimmed.charAt(i))) {
            i++;
        }
        if (i < trimmed.length() && trimmed.charAt(i) == '.') {
            i++;
            if (i < trimmed.length() && trimmed.charAt(i) == ' ') {
                return i + 1;
            }
        }
        return 0;
    }

    // ── Horizontal rule detection ──────────────────────────────────────

    private static boolean isHorizontalRule(@NonNull String trimmed) {
        if (trimmed.length() < 3) return false;
        char c = trimmed.charAt(0);
        if (c != '-' && c != '*' && c != '_') return false;
        for (int i = 1; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != c && trimmed.charAt(i) != ' ') return false;
        }
        return true;
    }

    // ── Table support ──────────────────────────────────────────────────

    /**
     * Check if a line looks like a table row (contains pipes).
     */
    private static boolean isTableRow(@NonNull String trimmed) {
        return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() >= 3;
    }

    /**
     * Check if a line is a table separator row (e.g., |---|---|).
     */
    private static boolean isTableSeparator(@NonNull String trimmed) {
        if (!isTableRow(trimmed)) return false;
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        // Must contain only |, -, :, and spaces
        for (char c : inner.toCharArray()) {
            if (c != '-' && c != ':' && c != '|' && c != ' ') return false;
        }
        // Must contain at least one dash
        return inner.contains("-");
    }

    /**
     * Parse a table row into cells.
     */
    @NonNull
    private static List<String> parseTableRow(@NonNull String trimmed) {
        List<String> cells = new ArrayList<>();
        // Remove leading and trailing pipes
        String inner = trimmed.substring(1, trimmed.length() - 1);
        String[] parts = inner.split("\\|", -1);
        for (String part : parts) {
            cells.add(part.trim());
        }
        return cells;
    }

    /**
     * Render accumulated table rows as HTML using monospace text formatting.
     * Since Android's Html.fromHtml() doesn't support <table>, we use
     * a styled text representation with box-drawing characters.
     */
    @NonNull
    private static String flushTable(@NonNull List<List<String>> rows, int headerRowCount) {
        if (rows.isEmpty()) return "";

        // Determine column count and max width per column
        int colCount = 0;
        for (List<String> row : rows) {
            colCount = Math.max(colCount, row.size());
        }
        if (colCount == 0) return "";

        int[] colWidths = new int[colCount];
        for (List<String> row : rows) {
            for (int c = 0; c < row.size() && c < colCount; c++) {
                colWidths[c] = Math.max(colWidths[c], row.get(c).length());
            }
        }
        // Cap column widths to avoid overly wide tables
        for (int c = 0; c < colCount; c++) {
            colWidths[c] = Math.min(colWidths[c], 30);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin:8px 0;\">");

        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            boolean isHeader = (r < headerRowCount);

            sb.append("<tt>");
            for (int c = 0; c < colCount; c++) {
                String cell = (c < row.size()) ? row.get(c) : "";
                // Truncate if too long
                if (cell.length() > 30) cell = cell.substring(0, 27) + "...";
                String padded = padRight(cell, colWidths[c]);
                if (isHeader) {
                    sb.append("<b>").append(escapeHtml(padded)).append("</b>");
                } else {
                    sb.append(escapeHtml(padded));
                }
                if (c < colCount - 1) {
                    sb.append(" <font color=\"#888888\">|</font> ");
                }
            }
            sb.append("</tt><br>");

            // Draw separator after header row
            if (r == headerRowCount - 1) {
                sb.append("<tt><font color=\"#888888\">");
                for (int c = 0; c < colCount; c++) {
                    for (int j = 0; j < colWidths[c]; j++) sb.append("-");
                    if (c < colCount - 1) sb.append("-+-");
                }
                sb.append("</font></tt><br>");
            }
        }

        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Pad a string to the right with spaces.
     */
    @NonNull
    private static String padRight(@NonNull String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    // ── List helpers ───────────────────────────────────────────────────

    @NonNull
    private static String closeList(@NonNull String listType) {
        return "ol".equals(listType) ? "</ol>" : "</ul>";
    }

    // ── HTML escaping ──────────────────────────────────────────────────

    /**
     * Escape HTML special characters.
     */
    @NonNull
    private static String escapeHtml(@NonNull String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
