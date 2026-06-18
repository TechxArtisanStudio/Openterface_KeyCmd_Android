package com.openterface.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

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
    private float fontSize = 14f;
    private float charWidth, charHeight, lineHeight;
    private boolean cursorVisible = true;
    private long cursorBlinkLast = 0;
    private static final long CURSOR_BLINK_INTERVAL = 500; // ms

    // Pinch-to-zoom
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private static final float MIN_FONT_SIZE = 6f;
    private static final float MAX_FONT_SIZE = 48f;

    // Color scheme
    private static final int DEFAULT_BG = Color.BLACK;
    private static final int DEFAULT_FG = 0xFFD0D0D0; // light gray for readability
    private static final int CURSOR_COLOR = 0x88FFFFFF;

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
        textPaint.setTextSize(fontSize);
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
                float newFontSize = fontSize * detector.getScaleFactor();
                newFontSize = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, newFontSize));
                setFontSize(newFontSize);
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                new TerminalPrefs(getContext()).setFontSize(fontSize);
            }
        });

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                showKeyboard();
                return true;
            }
        });

        measureCharSize();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    public void setTerminalSession(TerminalSession session) {
        this.session = session;
        requestLayout();
        invalidate();
    }

    public void setFontSize(float size) {
        this.fontSize = size;
        textPaint.setTextSize(fontSize);
        measureCharSize();
        invalidate();
    }

    private void measureCharSize() {
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        lineHeight = fm.descent - fm.ascent;
        charHeight = lineHeight;
        // Measure character width using 'M' (widest common char in monospace)
        charWidth = textPaint.measureText("M");
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (session == null) return;

        // Draw background
        canvas.drawColor(DEFAULT_BG);

        // Draw text from screen buffer
        int cols = session.getColumns();
        int rows = session.getRows();

        // Calculate offset to fit within view bounds
        int viewRows = (int) (getHeight() / charHeight);
        int viewCols = (int) (getWidth() / charWidth);
        int drawRows = Math.min(rows, viewRows);
        int drawCols = Math.min(cols, viewCols);

        float textOffsetY = -textPaint.getFontMetrics().top; // baseline offset

        for (int row = 0; row < drawRows; row++) {
            char[] line = session.getLineChars(row);
            if (line == null) continue;
            CellAttribute[] lineAttrs = session.getLineAttrs(row);

            for (int col = 0; col < drawCols; col++) {
                char ch = line[col];
                CellAttribute attr = (lineAttrs != null && col < lineAttrs.length)
                    ? lineAttrs[col] : CellAttribute.DEFAULT;

                float x = col * charWidth;
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
                if (ch == 0 || ch == ' ') continue;

                // Map characters missing from DroidSansMono to visual equivalents
                ch = mapMissingGlyph(ch);

                // Configure paint for this cell
                textPaint.setColor(fg);
                textPaint.setFakeBoldText(attr.bold);
                textPaint.setTextSkewX(attr.italic ? -0.25f : 0);
                textPaint.setUnderlineText(attr.underline);

                canvas.drawText(String.valueOf(ch), x, y, textPaint);
            }
        }

        // Draw cursor (respect DEC private mode 25)
        updateCursorBlink();
        if (cursorVisible && session.isCursorVisible()) {
            int cx = session.getCursorX();
            int cy = session.getCursorY();
            if (cx < drawCols && cy < drawRows) {
                float x = cx * charWidth;
                float y = cy * charHeight;
                canvas.drawRect(x, y, x + charWidth, y + charHeight, cursorPaint);
            }
        }
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
                return appMode ? "\033OA".getBytes() : "\033[A".getBytes();
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return appMode ? "\033OB".getBytes() : "\033[B".getBytes();
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return appMode ? "\033OD".getBytes() : "\033[D".getBytes();
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return appMode ? "\033OC".getBytes() : "\033[C".getBytes();
            case KeyEvent.KEYCODE_DEL:
                return new byte[]{0x7F}; // Backspace
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return "\033[3~".getBytes();
            case KeyEvent.KEYCODE_MOVE_HOME:
                return "\033[H".getBytes();
            case KeyEvent.KEYCODE_MOVE_END:
                return "\033[F".getBytes();
            case KeyEvent.KEYCODE_INSERT:
                return "\033[2~".getBytes();
            case KeyEvent.KEYCODE_PAGE_UP:
                return "\033[5~".getBytes();
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return "\033[6~".getBytes();
            case KeyEvent.KEYCODE_F1:
                return "\033OP".getBytes();
            case KeyEvent.KEYCODE_F2:
                return "\033OQ".getBytes();
            case KeyEvent.KEYCODE_F3:
                return "\033OR".getBytes();
            case KeyEvent.KEYCODE_F4:
                return "\033OS".getBytes();
            case KeyEvent.KEYCODE_F5:
                return "\033[15~".getBytes();
            case KeyEvent.KEYCODE_F6:
                return "\033[17~".getBytes();
            case KeyEvent.KEYCODE_F7:
                return "\033[18~".getBytes();
            case KeyEvent.KEYCODE_F8:
                return "\033[19~".getBytes();
            case KeyEvent.KEYCODE_F9:
                return "\033[20~".getBytes();
            case KeyEvent.KEYCODE_F10:
                return "\033[21~".getBytes();
            case KeyEvent.KEYCODE_F11:
                return "\033[23~".getBytes();
            case KeyEvent.KEYCODE_F12:
                return "\033[24~".getBytes();
            default:
                if (event.isCtrlPressed()) {
                    int ascii = event.getUnicodeChar();
                    if (ascii >= 'a' && ascii <= 'z') {
                        return new byte[]{(byte) (ascii - 'a' + 1)}; // Ctrl+A = 0x01
                    }
                    if (ascii >= 'A' && ascii <= 'Z') {
                        return new byte[]{(byte) (ascii - 'A' + 1)};
                    }
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
                view.session.onKeyInput(text.toString().getBytes());
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
