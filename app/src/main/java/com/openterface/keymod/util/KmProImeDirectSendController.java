package com.openterface.keymod.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bridges KM Pro portrait {@code km_pro_ime_host} text changes to HID via {@link HidTextKeystrokeSender}
 * (ASCII direct-send style).
 */
public final class KmProImeDirectSendController {

    private static final String TAG = "KmProImeDirectSend";

    private final Fragment host;
    @Nullable private ExecutorService sendExecutor;

    @Nullable private EditText editText;
    @Nullable private TextWatcher textWatcher;
    private volatile String lastProcessed = "";
    private boolean suppress;

    public KmProImeDirectSendController(@NonNull Fragment host) {
        this.host = host;
    }

    public void attach(@NonNull EditText edit) {
        detach();
        sendExecutor =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "km-pro-ime-hid");
                            t.setDaemon(true);
                            return t;
                        });
        editText = edit;
        lastProcessed = safeText(edit);
        textWatcher =
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (suppress) {
                            return;
                        }
                        final String now = s.toString();
                        ExecutorService ex = sendExecutor;
                        if (ex != null && !ex.isShutdown()) {
                            ex.execute(() -> applyDiffAndUpdateLast(now));
                        }
                    }
                };
        edit.addTextChangedListener(textWatcher);
    }

    public void detach() {
        if (editText != null && textWatcher != null) {
            editText.removeTextChangedListener(textWatcher);
        }
        editText = null;
        textWatcher = null;
        lastProcessed = "";
        if (sendExecutor != null) {
            sendExecutor.shutdownNow();
            sendExecutor = null;
        }
    }

    /** Clears the editor and HID diff baseline (e.g. when leaving IME mode). */
    public void clearEditorAndState() {
        suppress = true;
        try {
            lastProcessed = "";
            if (editText != null) {
                editText.setText("");
            }
        } finally {
            suppress = false;
        }
    }

    private static String safeText(@NonNull EditText e) {
        Editable ed = e.getText();
        return ed != null ? ed.toString() : "";
    }

    private void applyDiffAndUpdateLast(@NonNull String now) {
        String oldS = lastProcessed;
        if (oldS.equals(now)) {
            return;
        }
        try {
            ConnectionManager cm = peekConnectionManager();
            if (cm != null && cm.isConnected()) {
                applyLcpDiff(oldS, now, cm, peekTargetOs());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "HID send interrupted");
        } finally {
            lastProcessed = now;
        }
    }

    private void applyLcpDiff(String oldS, String newS, ConnectionManager cm, String targetOs)
            throws InterruptedException {
        int lcp = 0;
        int n = Math.min(oldS.length(), newS.length());
        while (lcp < n && oldS.charAt(lcp) == newS.charAt(lcp)) {
            lcp++;
        }
        int deleteCount = oldS.length() - lcp;
        for (int i = 0; i < deleteCount; i++) {
            sendBackspace(cm);
        }
        String insert = newS.substring(lcp);
        if (!insert.isEmpty()) {
            HidTextKeystrokeSender.send(insert, cm, targetOs, false, null);
        }
    }

    private static void sendBackspace(ConnectionManager cm) throws InterruptedException {
        cm.sendKeyEvent(0, 0x2A);
        Thread.sleep(30);
        cm.sendKeyRelease();
        Thread.sleep(10);
    }

    @Nullable
    private ConnectionManager peekConnectionManager() {
        if (!host.isAdded()) {
            return null;
        }
        if (host.requireActivity() instanceof MainActivity) {
            return ((MainActivity) host.requireActivity()).getConnectionManager();
        }
        return null;
    }

    @NonNull
    private String peekTargetOs() {
        if (host.isAdded() && host.requireActivity() instanceof MainActivity) {
            String os = ((MainActivity) host.requireActivity()).getTargetOs();
            return os != null ? os : "windows";
        }
        return "windows";
    }
}
