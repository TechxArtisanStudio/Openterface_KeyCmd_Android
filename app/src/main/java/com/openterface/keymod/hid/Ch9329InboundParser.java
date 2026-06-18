package com.openterface.keymod.hid;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Incrementally parses CH9329 UART frames (0x57 0xAB …) from inbound serial/BLE bytes.
 * When a successful {@code CMD_GET_INFO} response ({@code CMD == 0x81}, {@code LEN >= 3}) is seen,
 * forwards {@code DATA[2]} to {@link HostKeyboardLockLeds}.
 * When a USB mode response ({@code CMD == 0xB0}) is seen, forwards the raw frame to
 * {@link UsbModeResponseListener}.
 *
 * <p>Checksum: sum of all bytes except the final checksum byte, modulo 256, equals the final byte.
 */
public final class Ch9329InboundParser {

    private static final int MAX_BUFFER = 512;
    private static final int CMD_GET_INFO_ACK = 0x81;
    private static final int CMD_USB_MODE_RESPONSE = 0xB0;

    private final ArrayDeque<Byte> buf = new ArrayDeque<>();

    /** Listener for USB mode status responses (CMD 0xB0). */
    public interface UsbModeResponseListener {
        void onUsbModeResponse(byte[] frame);
    }

    @Nullable
    private UsbModeResponseListener usbModeListener;

    public void setUsbModeResponseListener(@Nullable UsbModeResponseListener listener) {
        usbModeListener = listener;
    }

    public synchronized void append(byte[] data, int len) {
        if (data == null || len <= 0) {
            return;
        }
        int n = Math.min(len, data.length);
        for (int i = 0; i < n; i++) {
            buf.addLast(data[i]);
            while (buf.size() > MAX_BUFFER) {
                buf.removeFirst();
            }
        }
        consume();
    }

    private void consume() {
        while (true) {
            trimUntil57Ab();
            if (buf.size() < 5) {
                return;
            }
            Iterator<Byte> peek = buf.iterator();
            peek.next(); // 57
            peek.next(); // AB
            peek.next(); // ADDR
            int cmd = peek.next() & 0xFF;
            int len = peek.next() & 0xFF;
            int total = 5 + len + 1;
            if (buf.size() < total) {
                return;
            }
            byte[] frame = new byte[total];
            for (int i = 0; i < total; i++) {
                frame[i] = buf.removeFirst();
            }
            if (!validChecksum(frame)) {
                continue;
            }
            if (cmd == CMD_GET_INFO_ACK && len >= 3) {
                int leds = frame[5 + 2] & 0xFF;
                HostKeyboardLockLeds.get().applyFromHidLedByte(leds);
            }
            if (cmd == CMD_USB_MODE_RESPONSE) {
                UsbModeResponseListener listener = usbModeListener;
                if (listener != null) {
                    listener.onUsbModeResponse(frame.clone());
                }
            }
        }
    }

    /** Drops bytes until the deque begins with 0x57 0xAB or is empty. */
    private void trimUntil57Ab() {
        while (!buf.isEmpty()) {
            int first = buf.peekFirst() & 0xFF;
            if (first != 0x57) {
                buf.removeFirst();
                continue;
            }
            if (buf.size() < 2) {
                return;
            }
            Iterator<Byte> it = buf.iterator();
            it.next();
            int second = it.next() & 0xFF;
            if (second == 0xAB) {
                return;
            }
            buf.removeFirst();
        }
    }

    private static boolean validChecksum(byte[] frame) {
        if (frame.length < 6) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < frame.length - 1; i++) {
            sum = (sum + (frame[i] & 0xFF)) & 0xFF;
        }
        int chk = frame[frame.length - 1] & 0xFF;
        return sum == chk;
    }
}
