package com.openterface.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
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
        textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        textPaint.setTextSize(fontSize);
        textPaint.setColor(DEFAULT_FG);

        bgPaint = new Paint();
        bgPaint.setColor(DEFAULT_BG);

        cursorPaint = new Paint();
        cursorPaint.setColor(CURSOR_COLOR);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(DEFAULT_BG);

        measureCharSize();
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

            for (int col = 0; col < drawCols; col++) {
                char ch = line[col];
                if (ch == 0 || ch == ' ') continue; // blank

                textPaint.setColor(DEFAULT_FG);
                float x = col * charWidth;
                float y = row * charHeight + textOffsetY;
                canvas.drawText(String.valueOf(ch), x, y, textPaint);
            }
        }

        // Draw cursor
        updateCursorBlink();
        if (cursorVisible) {
            int cx = session.getCursorX();
            int cy = session.getCursorY();
            if (cx < drawCols && cy < drawRows) {
                float x = cx * charWidth;
                float y = cy * charHeight;
                canvas.drawRect(x, y, x + charWidth, y + charHeight, cursorPaint);
            }
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
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                return new byte[]{'\r'};
            case KeyEvent.KEYCODE_TAB:
                return new byte[]{'\t'};
            case KeyEvent.KEYCODE_ESCAPE:
                return new byte[]{0x1B};
            case KeyEvent.KEYCODE_DPAD_UP:
                return "\033[A".getBytes();
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return "\033[B".getBytes();
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return "\033[D".getBytes();
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return "\033[C".getBytes();
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
