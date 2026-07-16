package com.openterface.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.nio.charset.StandardCharsets;

/**
 * Terminal display surface. Renders characters using the TerminalSession
 * screen buffer and handles touch/keyboard input.
 *
 * Phase 1: Basic character rendering with cursor.
 * Phase 4+: Full ANSI color support, selection, copy/paste.
 */
public class TerminalView extends View {

    private TerminalSession session;
    private Paint textPaint;
    private Paint bgPaint;
    private Paint cursorPaint;
    private float fontSizeSp = 16f;
    private float charWidth, charHeight, lineHeight;
    private boolean autoFitFontSize = true;
    private boolean cursorVisible = true;
    private long cursorBlinkLast = 0;
    private static final long CURSOR_BLINK_INTERVAL = 500; // ms

    // Pinch-to-zoom
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private static final float MIN_FONT_SIZE_SP = 12f;
    private static final float MAX_FONT_SIZE_SP = 28f;
    private static final float AUTO_FONT_MIN_SP = 16f;
    private static final float AUTO_FONT_MAX_SP = 24f;
    private static final float AUTO_FIT_WIDTH_FILL = 0.97f;
    private static final float AUTO_FIT_HEIGHT_FILL = 0.94f;

    // Horizontal scroll
    private int scrollOffsetX = 0;        // Horizontal scroll offset in columns
    private int maxContentWidth = 0;      // Max content width of visible rows (columns)
    private float hScrollLastX = 0;       // Last touch X for horizontal drag
    private boolean hScrolling = false;   // Whether horizontal scroll is active

    // Color scheme
    private static final int DEFAULT_BG = Color.BLACK;
    private static final int DEFAULT_FG = 0xFFD0D0D0; // light gray for readability
    private static final int CURSOR_COLOR = 0x88FFFFFF;
    private static final int SCROLLBAR_COLOR = 0x40FFFFFF; // semi-transparent white

    public TerminalView(Context context) {
        super(context);
        init();
    }

    public TerminalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Load bundled Noto Sans Mono font for better Unicode support
        try {
            android.graphics.Typeface monoTypeface = android.graphics.Typeface.createFromAsset(
                getContext().getAssets(), "fonts/NotoSansMono.ttf");
            textPaint.setTypeface(monoTypeface);
        } catch (Exception e) {
            // Fallback to system monospace if bundled font fails to load
            textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        }
        textPaint.setTextSize(spToPx(fontSizeSp));
        textPaint.setColor(DEFAULT_FG);

        bgPaint = new Paint();
        bgPaint.setColor(DEFAULT_BG);

        cursorPaint = new Paint();
        cursorPaint.setColor(CURSOR_COLOR);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(DEFAULT_BG);

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float newFontSize = fontSizeSp * detector.getScaleFactor();
                newFontSize = Math.max(MIN_FONT_SIZE_SP, Math.min(MAX_FONT_SIZE_SP, newFontSize));
                autoFitFontSize = false;
                setFontSize(newFontSize);
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                new TerminalPrefs(getContext()).setFontSize(fontSizeSp);
            }
        });

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                // Manually trigger OnClickListener since onTouchEvent() returns true
                performClick();
                return true;
            }
        });

        measureCharSize();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        // Horizontal scroll: single-finger drag when content exceeds view width
        int viewCols = charWidth > 0 ? (int) (getWidth() / charWidth) : 0;
        boolean canScrollH = maxContentWidth > viewCols;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                hScrollLastX = event.getX();
                hScrolling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (canScrollH && !scaleDetector.isInProgress()) {
                    float dx = event.getX() - hScrollLastX;
                    float dy = event.getY() - (hScrollLastY != 0 ? hScrollLastY : event.getY());
                    // Determine if this is primarily a horizontal gesture
                    if (!hScrolling && Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > charWidth) {
                        hScrolling = true;
                    }
                    if (hScrolling) {
                        int colDelta = (int) (dx / charWidth);
                        if (colDelta != 0) {
                            scrollHorizontally(-colDelta);
                            hScrollLastX = event.getX();
                        }
                        return true;
                    }
                }
                hScrollLastX = event.getX();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                hScrolling = false;
                break;
        }

        gestureDetector.onTouchEvent(event);
        return true;
    }

    private float hScrollLastY = 0;

    /** Scroll horizontally by the given number of columns (positive = right). */
    private void scrollHorizontally(int colDelta) {
        if (session == null || charWidth <= 0) return;
        int viewCols = (int) (getWidth() / charWidth);
        int maxOffset = Math.max(0, maxContentWidth - viewCols);
        scrollOffsetX = Math.max(0, Math.min(maxOffset, scrollOffsetX + colDelta));
        invalidate();
    }

    /** Reset horizontal scroll offset to 0. */
    public void resetScrollX() {
        scrollOffsetX = 0;
        invalidate();
    }

    /** Get current horizontal scroll offset in columns. */
    public int getScrollOffsetX() {
        return scrollOffsetX;
    }

    public void setTerminalSession(TerminalSession session) {
        this.session = session;
        applyAutoFitFontSizeIfNeeded(getWidth(), getHeight());
        requestLayout();
        invalidate();
    }

    public void setFontSize(float sizeSp) {
        this.fontSizeSp = Math.max(MIN_FONT_SIZE_SP, Math.min(MAX_FONT_SIZE_SP, sizeSp));
        textPaint.setTextSize(spToPx(fontSizeSp));
        measureCharSize();
        invalidate();
    }

    public void setAutoFitFontSize(boolean enabled) {
        autoFitFontSize = enabled;
        applyAutoFitFontSizeIfNeeded(getWidth(), getHeight());
    }

    private void measureCharSize() {
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        lineHeight = fm.descent - fm.ascent;
        charHeight = lineHeight;
        // Measure character width using 'M' (widest common char in monospace)
        charWidth = textPaint.measureText("M");
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                getResources().getDisplayMetrics()
        );
    }

    private void applyAutoFitFontSizeIfNeeded(int width, int height) {
        if (!autoFitFontSize || session == null || width <= 0 || height <= 0) {
            return;
        }

        float currentSp = fontSizeSp;
        float currentWidth = Math.max(1f, charWidth);
        float currentHeight = Math.max(1f, charHeight);
        float targetByCols = currentSp * width * AUTO_FIT_WIDTH_FILL
                / (session.getColumns() * currentWidth);
        float targetByRows = currentSp * height * AUTO_FIT_HEIGHT_FILL
                / (session.getRows() * currentHeight);
        float targetSp = Math.min(targetByCols, targetByRows);
        targetSp = Math.max(AUTO_FONT_MIN_SP, Math.min(AUTO_FONT_MAX_SP, targetSp));

        if (Math.abs(targetSp - fontSizeSp) >= 0.25f) {
            fontSizeSp = targetSp;
            textPaint.setTextSize(spToPx(fontSizeSp));
            measureCharSize();
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyAutoFitFontSizeIfNeeded(w, h);
        // Dynamically grow terminal buffer to match view size
        if (session != null && charHeight > 0 && charWidth > 0) {
            int viewRows = Math.max(1, (int) (h / charHeight));
            int viewCols = Math.max(1, (int) (w / charWidth));
            session.growIfNeeded(viewRows, viewCols);
        }
        // Reset horizontal scroll on size change
        scrollOffsetX = 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (session == null) return;

        // Draw background
        canvas.drawColor(DEFAULT_BG);

        // Hold session lock for the entire draw to get an atomic snapshot.
        // This prevents the VT parser thread from modifying screen data
        // mid-render (which would cause cursor tearing and character corruption).
        synchronized (session) {
            // Draw text from screen buffer
            int cols = session.getColumns();
            int rows = session.getRows();

            // Calculate offset to fit within view bounds
            int viewRows = (int) (getHeight() / charHeight);
            int viewCols = (int) (getWidth() / charWidth);
            int drawRows = Math.min(rows, viewRows);
            int drawCols = Math.min(cols, viewCols);

            // Show content around cursor, but skip empty rows above
            int cursorY = session.getCursorY();
            int startRow = Math.max(0, cursorY - drawRows + 1);

            // Find first non-empty row from startRow downward
            // If all rows are empty, keep the original startRow (cursor at bottom)
            for (int r = startRow; r <= cursorY; r++) {
                char[] line = session.getLineChars(r);
                if (line != null && !isLineEmpty(line, drawCols)) {
                    startRow = r;
                    break;
                }
            }

            float textOffsetY = -textPaint.getFontMetrics().top; // baseline offset

            // Calculate max content width of visible rows (for horizontal scroll)
            maxContentWidth = 0;
            for (int r = 0; r < drawRows; r++) {
                char[] line = session.getLineChars(startRow + r);
                if (line == null) continue;
                for (int c = line.length - 1; c >= 0; c--) {
                    if (line[c] != ' ' && line[c] != 0 && line[c] != '\t'
                            && line[c] != '\r' && line[c] != '\n') {
                        maxContentWidth = Math.max(maxContentWidth, c + 1);
                        break;
                    }
                }
            }

            // Clamp horizontal scroll offset
            int maxScrollOffset = Math.max(0, maxContentWidth - viewCols);
            scrollOffsetX = Math.max(0, Math.min(maxScrollOffset, scrollOffsetX));

            // Auto-scroll to keep cursor visible
            int cursorX = session.getCursorX();
            if (cursorX < scrollOffsetX) {
                scrollOffsetX = cursorX;
            } else if (cursorX >= scrollOffsetX + viewCols) {
                scrollOffsetX = cursorX - viewCols + 1;
            }
            scrollOffsetX = Math.max(0, Math.min(maxScrollOffset, scrollOffsetX));

            // Determine column range to draw
            int startCol = scrollOffsetX;
            int endCol = Math.min(cols, scrollOffsetX + viewCols);

            for (int row = 0; row < drawRows; row++) {
                char[] line = session.getLineChars(startRow + row);
                if (line == null) continue;
                CellAttribute[] lineAttrs = session.getLineAttrs(startRow + row);

                int col = startCol;
                while (col < endCol) {
                    char ch = line[col];
                    CellAttribute attr = (lineAttrs != null && col < lineAttrs.length)
                        ? lineAttrs[col] : CellAttribute.DEFAULT;

                    float x = (col - scrollOffsetX) * charWidth;
                    float y = row * charHeight + textOffsetY;

                    // Resolve inverse: swap fg/bg
                    int fg = attr.fgColor;
                    int bg = attr.bgColor;
                    if (attr.inverse) {
                        int tmp = fg;
                        fg = bg;
                        bg = tmp;
                    }

                    // Draw background cell if non-default
                    if (bg != CellAttribute.DEFAULT_BG) {
                        bgPaint.setColor(bg);
                        canvas.drawRect(x, row * charHeight, x + charWidth, (row + 1) * charHeight, bgPaint);
                    }

                    // Skip blank cells (no glyph to draw)
                    if (ch != 0 && ch != ' ') {
                        // Map characters missing from font to visual equivalents
                        ch = mapMissingGlyph(ch);

                        // Configure paint for this cell
                        textPaint.setColor(fg);
                        textPaint.setFakeBoldText(attr.bold);
                        textPaint.setTextSkewX(attr.italic ? -0.25f : 0);
                        textPaint.setUnderlineText(attr.underline);

                        // Handle wide (CJK) characters: draw at current position, skip next cell
                        if (isWideChar(ch)) {
                            // Draw wide character spanning 2 cells
                            canvas.drawText(String.valueOf(ch), x, y, textPaint);
                            col++; // Skip next cell
                        } else {
                            canvas.drawText(String.valueOf(ch), x, y, textPaint);
                        }
                    }

                    col++;
                }
            }

            // Draw cursor (respect DEC private mode 25)
            updateCursorBlink();
            if (cursorVisible && session.isCursorVisible()) {
                int cx = session.getCursorX();
                int cy = session.getCursorY();
                // Adjust cursor Y for the scroll offset (we display from startRow)
                int displayCy = cy - startRow;
                if (cx >= scrollOffsetX && cx < scrollOffsetX + viewCols
                        && displayCy >= 0 && displayCy < drawRows) {
                    float x = (cx - scrollOffsetX) * charWidth;
                    float y = displayCy * charHeight;
                    canvas.drawRect(x, y, x + charWidth, y + charHeight, cursorPaint);
                }
            }

            // Draw horizontal scrollbar if content exceeds view width
            if (maxContentWidth > viewCols) {
                float scrollbarHeight = 4 * getResources().getDisplayMetrics().density;
                float scrollbarY = getHeight() - scrollbarHeight;
                float totalWidth = maxContentWidth * charWidth;
                float visibleWidth = viewCols * charWidth;
                float thumbWidth = Math.max(scrollbarHeight * 2,
                        visibleWidth * visibleWidth / totalWidth);
                float thumbX = scrollOffsetX * charWidth * visibleWidth / totalWidth;

                bgPaint.setColor(SCROLLBAR_COLOR);
                canvas.drawRect(thumbX, scrollbarY, thumbX + thumbWidth,
                        scrollbarY + scrollbarHeight, bgPaint);
            }
        }
    }

    /** Check if a line is empty (only spaces or null chars) */
    private boolean isLineEmpty(char[] line, int maxCols) {
        if (line == null || maxCols <= 0) return true;
        int limit = Math.min(line.length, maxCols);
        for (int i = 0; i < limit; i++) {
            char ch = line[i];
            if (ch != ' ' && ch != 0 && ch != '\t' && ch != '\r' && ch != '\n') {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a character is a wide (double-width) character.
     * CJK characters and other fullwidth characters occupy 2 terminal columns.
     */
    private boolean isWideChar(char ch) {
        // Common CJK and fullwidth ranges
        if (ch >= 0x1100 && ch <= 0x115F) return true; // Hangul Jamo
        if (ch >= 0x2E80 && ch <= 0x303E) return true; // CJK Radicals Supplement, etc.
        if (ch >= 0x3040 && ch <= 0x33BF) return true; // Japanese, Korean, CJK Compatibility
        if (ch >= 0x3400 && ch <= 0x4DBF) return true; // CJK Unified Ideographs Extension A
        if (ch >= 0x4E00 && ch <= 0x9FFF) return true; // CJK Unified Ideographs
        if (ch >= 0xA000 && ch <= 0xA4CF) return true; // Yi Syllables
        if (ch >= 0xAC00 && ch <= 0xD7AF) return true; // Hangul Syllables
        if (ch >= 0xF900 && ch <= 0xFAFF) return true; // CJK Compatibility Ideographs
        if (ch >= 0xFE30 && ch <= 0xFE6F) return true; // CJK Compatibility Forms
        if (ch >= 0xFF01 && ch <= 0xFF60) return true; // Fullwidth Forms
        if (ch >= 0xFFE0 && ch <= 0xFFE6) return true; // Fullwidth Signs
        return false;
    }

    /**
     * Map characters that are missing from the font to visual equivalents.
     * DroidSansMono lacks U+23F4/23F5/23F6/23F7 (media control triangles).
     * We map them to similar-looking geometric shapes that ARE in the font.
     */
    private char mapMissingGlyph(char ch) {
        switch (ch) {
            case '\u23F5': return '\u25B6'; // ⏵ → ▶ (black right-pointing triangle)
            case '\u23F4': return '\u25C0'; // ⏴ → ◀ (black left-pointing triangle)
            case '\u23F6': return '\u25B2'; // ⏶ → ▲ (black up-pointing triangle)
            case '\u23F7': return '\u25BC'; // ⏷ → ▼ (black down-pointing triangle)
            default: return ch;
        }
    }

    private void updateCursorBlink() {
        long now = System.currentTimeMillis();
        if (now - cursorBlinkLast > CURSOR_BLINK_INTERVAL) {
            cursorVisible = !cursorVisible;
            cursorBlinkLast = now;
            invalidate();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (session == null) return super.onKeyDown(keyCode, event);

        byte[] seq = keyToByteSequence(keyCode, event);
        if (seq != null) {
            session.onKeyInput(seq);
            invalidate();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Map Android key codes to terminal byte sequences.
     * E.g., Enter -> \r, Tab -> \t, Escape -> 0x1B, arrow keys -> CSI sequences.
     */
    private byte[] keyToByteSequence(int keyCode, KeyEvent event) {
        // Application cursor keys mode (DECCKM): send SS3 sequences (ESC O A/B/C/D)
        boolean appMode = (session != null && session.isApplicationCursorKeys());

        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                return new byte[]{'\r'};
            case KeyEvent.KEYCODE_TAB:
                return new byte[]{'\t'};
            case KeyEvent.KEYCODE_ESCAPE:
                return new byte[]{0x1B};
            case KeyEvent.KEYCODE_DPAD_UP:
                return appMode ? "\033OA".getBytes(StandardCharsets.US_ASCII) : "\033[A".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return appMode ? "\033OB".getBytes(StandardCharsets.US_ASCII) : "\033[B".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return appMode ? "\033OD".getBytes(StandardCharsets.US_ASCII) : "\033[D".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return appMode ? "\033OC".getBytes(StandardCharsets.US_ASCII) : "\033[C".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_DEL:
                return new byte[]{0x7F}; // Backspace
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return "\033[3~".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_MOVE_HOME:
                return "\033[H".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_MOVE_END:
                return "\033[F".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_INSERT:
                return "\033[2~".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_PAGE_UP:
                return "\033[5~".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return "\033[6~".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F1:
                return "\033OP".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F2:
                return "\033OQ".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F3:
                return "\033OR".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F4:
                return "\033OS".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F5:
                return "\033[15~".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F6:
                return "\033[17~".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F7:
                return "\033[18~".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F8:
                return "\033[19~".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F9:
                return "\033[20~".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F10:
                return "\033[21~".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F11:
                return "\033[23~".getBytes(StandardCharsets.US_ASCII);
            case KeyEvent.KEYCODE_F12:
                return "\033[24~".getBytes(StandardCharsets.US_ASCII);
            default:
                // Number keys (main keyboard and numpad)
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    return new byte[]{(byte) ('0' + keyCode - KeyEvent.KEYCODE_0)};
                }
                if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                    return new byte[]{(byte) ('0' + keyCode - KeyEvent.KEYCODE_NUMPAD_0)};
                }
                // Numpad operators
                if (keyCode == KeyEvent.KEYCODE_NUMPAD_DOT)      return new byte[]{(byte) '.'};
                if (keyCode == KeyEvent.KEYCODE_NUMPAD_COMMA)    return new byte[]{(byte) ','};
                if (keyCode == KeyEvent.KEYCODE_NUMPAD_ADD)      return new byte[]{(byte) '+'};
                if (keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT) return new byte[]{(byte) '-'};
                if (keyCode == KeyEvent.KEYCODE_NUMPAD_MULTIPLY) return new byte[]{(byte) '*'};
                if (keyCode == KeyEvent.KEYCODE_NUMPAD_DIVIDE)   return new byte[]{(byte) '/'};
                if (keyCode == KeyEvent.KEYCODE_NUMPAD_EQUALS)   return new byte[]{(byte) '='};
                if (keyCode == KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN)  return new byte[]{(byte) '('};
                if (keyCode == KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN) return new byte[]{(byte) ')'};
                // Space
                if (keyCode == KeyEvent.KEYCODE_SPACE) {
                    return new byte[]{(byte) ' '};
                }
                // Ctrl + any key → control character
                if (event.isCtrlPressed()) {
                    int ascii = event.getUnicodeChar();
                    if (ascii >= 'a' && ascii <= 'z') {
                        return new byte[]{(byte) (ascii - 'a' + 1)}; // Ctrl+A = 0x01
                    }
                    if (ascii >= 'A' && ascii <= 'Z') {
                        return new byte[]{(byte) (ascii - 'A' + 1)};
                    }
                }
                // General fallback: convert printable characters via Unicode
                int unicodeChar = event.getUnicodeChar(0);
                if (unicodeChar != 0 && unicodeChar != KeyEvent.KEYCODE_UNKNOWN) {
                    String str = new String(Character.toChars(unicodeChar));
                    return str.getBytes(StandardCharsets.UTF_8);
                }
                return null;
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT
                | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        return new TerminalInputConnection(this, false);
    }

    /** Custom input connection for terminal text input. */
    private static class TerminalInputConnection extends BaseInputConnection {
        private final TerminalView view;

        TerminalInputConnection(TerminalView view, boolean fullEditor) {
            super(view, fullEditor);
            this.view = view;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (view.session != null) {
                view.session.onKeyInput(text.toString().getBytes(StandardCharsets.UTF_8));
                view.invalidate();
            }
            return true;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            return view.onKeyDown(event.getKeyCode(), event);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            // Backspace from soft keyboard
            if (view.session != null && beforeLength > 0) {
                view.session.onKeyInput(new byte[]{0x7F});
                view.invalidate();
            }
            return true;
        }
    }

    /** Show soft keyboard. */
    public void showKeyboard() {
        requestFocus();
        InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.restartInput(this);
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /** Hide soft keyboard. */
    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    /** Send a key sequence from the bottom bar buttons. */
    public void sendSpecialKey(String key) {
        if (session == null) return;
        switch (key) {
            case "Ctrl":
                // Toggle Ctrl mode — for now just send a placeholder
                showKeyboard();
                break;
            case "Esc":
                session.onKeyInput(new byte[]{0x1B});
                invalidate();
                break;
            case "Tab":
                session.onKeyInput(new byte[]{'\t'});
                invalidate();
                break;
        }
    }
}
