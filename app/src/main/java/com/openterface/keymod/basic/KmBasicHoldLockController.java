package com.openterface.keymod.basic;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.hid.KeyboardHidTransport;
import com.openterface.keymod.hid.MouseRelHidTransport;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Session-scoped keyboard modifier and mouse-button locks for KM Basic (lives on {@link
 * com.openterface.fragment.KeyboardMouseFragment}). Cleared when leaving that mode or on explicit
 * HID reset (e.g. disconnect).
 */
public final class KmBasicHoldLockController {

    public interface Listener {
        void onKmBasicHoldLocksChanged(KmBasicHoldLockController controller);
    }

    private final Object guard = new Object();
    private int lockedModMask;
    private int lockedMouseMask;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Listener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyListeners() {
        for (Listener l : listeners) {
            l.onKmBasicHoldLocksChanged(this);
        }
    }

    public int getLockedModMask() {
        synchronized (guard) {
            return lockedModMask & 0xFF;
        }
    }

    public int getLockedMouseMask() {
        synchronized (guard) {
            return lockedMouseMask & 0xFF;
        }
    }

    public boolean hasAnyLock() {
        synchronized (guard) {
            return lockedModMask != 0 || lockedMouseMask != 0;
        }
    }

    public boolean isModifierLocked(int modBit) {
        synchronized (guard) {
            return (lockedModMask & modBit) != 0;
        }
    }

    public boolean isMouseLocked(int mouseBit) {
        synchronized (guard) {
            return (lockedMouseMask & mouseBit) != 0;
        }
    }

    /**
     * Locks a keyboard modifier bit and asserts HID modifier-down (no keycode).
     */
    public void lockModifier(
            int modBit,
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        synchronized (guard) {
            lockedModMask |= (modBit & 0xFF);
        }
        notifyListeners();
        int m = getLockedModMask();
        if (m != 0) {
            KeyboardHidTransport.sendKeyReport(
                    port, bluetoothService, bluetoothServiceBound, m, 0);
        }
    }

    /**
     * Clears a modifier lock bit; sends updated modifier report or full release if none left.
     */
    public void unlockModifier(
            int modBit,
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        synchronized (guard) {
            lockedModMask &= ~(modBit & 0xFF);
        }
        notifyListeners();
        reassertKeyboardModifiersAfterRelease(port, bluetoothService, bluetoothServiceBound);
    }

    /**
     * Locks mouse button bits (HID relative); asserts button-down with zero motion.
     */
    public void lockMouseButtons(
            int mouseBits,
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        synchronized (guard) {
            lockedMouseMask |= (mouseBits & 0xFF);
        }
        notifyListeners();
        int m = getLockedMouseMask();
        if (m != 0) {
            MouseRelHidTransport.sendRelButtonsNoMotion(
                    port, bluetoothService, bluetoothServiceBound, m);
        }
    }

    public void unlockMouseButtons(
            int mouseBits,
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        synchronized (guard) {
            lockedMouseMask &= ~(mouseBits & 0xFF);
        }
        notifyListeners();
        int m = getLockedMouseMask();
        if (m == 0) {
            MouseRelHidTransport.releaseAll(port, bluetoothService, bluetoothServiceBound);
        } else {
            MouseRelHidTransport.sendRelButtonsNoMotion(
                    port, bluetoothService, bluetoothServiceBound, m);
        }
    }

    /**
     * After {@link KeyboardHidTransport#sendAllKeysReleased}, re-assert locked modifiers so the
     * host keeps them.
     */
    public void reassertKeyboardModifiersIfNeeded(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        int m = getLockedModMask();
        if (m != 0) {
            KeyboardHidTransport.sendKeyReport(
                    port, bluetoothService, bluetoothServiceBound, m, 0);
        }
    }

    private void reassertKeyboardModifiersAfterRelease(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        int m = getLockedModMask();
        if (m == 0) {
            KeyboardHidTransport.sendAllKeysReleased(
                    port, bluetoothService, bluetoothServiceBound);
        } else {
            KeyboardHidTransport.sendKeyReport(
                    port, bluetoothService, bluetoothServiceBound, m, 0);
        }
    }

    /**
     * Clears all locks and releases keyboard + mouse HID (no re-assert). Call when leaving KM
     * Basic or on disconnect.
     */
    public void clearAllAndReleaseHid(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        synchronized (guard) {
            lockedModMask = 0;
            lockedMouseMask = 0;
        }
        notifyListeners();
        KeyboardHidTransport.sendAllKeysReleased(
                port, bluetoothService, bluetoothServiceBound);
        MouseRelHidTransport.releaseAll(port, bluetoothService, bluetoothServiceBound);
    }
}
