package com.openterface.keymod.hid;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Host keyboard lock indicators (Num / Caps / Scroll) mirrored from CH9329 {@code CMD_GET_INFO}
 * response {@code DATA[2]}, which follows USB HID keyboard LED output semantics:
 * bit0 Num Lock, bit1 Caps Lock, bit2 Scroll Lock.
 *
 * <p>Reference: WCH CH9329 protocol; open-source {@code CH9329::cmdGetInfo} / {@code isNumLock}
 * patterns (e.g. ximu-tao/CH9329 on GitHub).
 */
public final class HostKeyboardLockLeds {

    private static final HostKeyboardLockLeds INSTANCE = new HostKeyboardLockLeds();

    public static HostKeyboardLockLeds get() {
        return INSTANCE;
    }

    public interface Listener {
        @MainThread
        void onHostKeyboardLocksChanged(
                boolean numLock, boolean capsLock, boolean scrollLock);
    }

    private final Object lock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private boolean numLock;
    private boolean capsLock;
    private boolean scrollLock;
    /** True after at least one successful LED parse (GET_INFO ack). */
    private boolean receivedFromHost;

    private HostKeyboardLockLeds() {
    }

    public boolean hasReceivedLedFromHost() {
        synchronized (lock) {
            return receivedFromHost;
        }
    }

    public boolean isNumLock() {
        synchronized (lock) {
            return numLock;
        }
    }

    public boolean isCapsLock() {
        synchronized (lock) {
            return capsLock;
        }
    }

    public boolean isScrollLock() {
        synchronized (lock) {
            return scrollLock;
        }
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * @param leds lower 3 bits: USB HID keyboard LEDs (Num, Caps, Scroll).
     */
    @AnyThread
    public void applyFromHidLedByte(int leds) {
        final boolean n = (leds & 0x01) != 0;
        final boolean c = (leds & 0x02) != 0;
        final boolean s = (leds & 0x04) != 0;
        final boolean changed;
        synchronized (lock) {
            changed = n != numLock || c != capsLock || s != scrollLock || !receivedFromHost;
            numLock = n;
            capsLock = c;
            scrollLock = s;
            receivedFromHost = true;
        }
        if (changed) {
            dispatchOnMainThread(n, c, s);
        }
    }

    /** Clears host-sync state (e.g. on disconnect). */
    @AnyThread
    public void reset() {
        final boolean hadHost;
        synchronized (lock) {
            hadHost = receivedFromHost;
            numLock = false;
            capsLock = false;
            scrollLock = false;
            receivedFromHost = false;
        }
        if (hadHost) {
            dispatchOnMainThread(false, false, false);
        }
    }

    private void dispatchOnMainThread(boolean n, boolean c, boolean s) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifyListeners(n, c, s);
        } else {
            mainHandler.post(() -> notifyListeners(n, c, s));
        }
    }

    private void notifyListeners(boolean n, boolean c, boolean s) {
        for (Listener l : listeners) {
            l.onHostKeyboardLocksChanged(n, c, s);
        }
    }
}
