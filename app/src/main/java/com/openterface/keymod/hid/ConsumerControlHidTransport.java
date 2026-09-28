package com.openterface.keymod.hid;

import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.openterface.keymod.BluetoothService;

import java.io.IOException;

/**
 * Consumer Control HID transport (CH9329 CMD 0x03).
 *
 * <p>Sends media / volume keys via the CH9329's dedicated Consumer Control
 * command, distinct from the keyboard (CMD 0x02) and mouse (CMD 0x05) paths.
 *
 * <h3>Frame format (10 bytes)</h3>
 * <pre>
 *   57 AB 00 03 04 02 &lt;usage&gt; 00 00 &lt;checksum&gt;
 * </pre>
 *
 * <h3>Usage bitmasks</h3>
 * <ul>
 *   <li>{@code 0x01} — Volume Up</li>
 *   <li>{@code 0x02} — Volume Down</li>
 *   <li>{@code 0x04} — Mute</li>
 *   <li>{@code 0x08} — Play / Pause</li>
 *   <li>{@code 0x10} — Next Track</li>
 *   <li>{@code 0x20} — Previous Track</li>
 *   <li>{@code 0x40} — Stop</li>
 *   <li>{@code 0x80} — Eject</li>
 *   <li>{@code 0x00} — Release (all keys up)</li>
 * </ul>
 */
public final class ConsumerControlHidTransport {

    private static final String TAG = "ConsumerControlHid";

    /**
     * CH9329 Consumer Control packet header: prefix(2) + addr(1) + cmd(1) + len(1) + reportId(1).
     * The cmd byte {@code 03} corresponds to {@code CH9329MSKBMap.CmdData("CmdCC_HID")}.
     */
    private static final String CC_HEADER = "57AB00030402";

    /** Delay between press and release in milliseconds. */
    private static final int TAP_DELAY_MS = 30;

    /** Pre-built release packet bytes (usage 0x00), avoids recomputing on every tap. */
    private static final byte[] RELEASE_PACKET = {
            0x57, (byte) 0xAB, 0x00, 0x03, 0x04, 0x02, 0x00, 0x00, 0x00, 0x0B
    };

    // ── Consumer Control usage bitmasks (CH9329 CMD 0x03, report ID 0x02) ──
    // Frame: 57 AB 00 03 04 02 <usage> 00 00 <checksum>

    /** Volume Up */
    public static final int USAGE_VOLUME_UP    = 0x01;
    /** Volume Down */
    public static final int USAGE_VOLUME_DOWN  = 0x02;
    /** Mute */
    public static final int USAGE_MUTE         = 0x04;
    /** Play / Pause */
    public static final int USAGE_PLAY_PAUSE   = 0x08;
    /** Next Track */
    public static final int USAGE_NEXT_TRACK   = 0x10;
    /** Previous Track */
    public static final int USAGE_PREV_TRACK   = 0x20;
    /** Stop */
    public static final int USAGE_STOP         = 0x40;
    /** Eject CD */
    public static final int USAGE_EJECT        = 0x80;
    /** Release (all keys up) */
    public static final int USAGE_RELEASE      = 0x00;

    private ConsumerControlHidTransport() {
    }

    /**
     * Build a 10-byte Consumer Control packet as a hex string.
     *
     * <pre>
     *   57 AB 00 03 04 02 &lt;usage&gt; 00 00 &lt;checksum&gt;
     * </pre>
     *
     * @param usageCode usage bitmask (0x01=Vol+, 0x02=Vol-, 0x04=Mute, 0x08=Play,
     *                  0x10=Next, 0x20=Prev, 0x40=Stop, 0x80=Eject, 0x00=Release)
     * @return hex string representation of the full packet including checksum
     */
    public static String buildPacketHex(int usageCode) {
        // Validate usage code is within expected range
        int masked = usageCode & 0xFF;
        if (masked != USAGE_RELEASE && masked != USAGE_VOLUME_UP && masked != USAGE_VOLUME_DOWN
                && masked != USAGE_MUTE && masked != USAGE_PLAY_PAUSE && masked != USAGE_NEXT_TRACK
                && masked != USAGE_PREV_TRACK && masked != USAGE_STOP && masked != USAGE_EJECT) {
            Log.w(TAG, "Invalid Consumer Control usage code: 0x" + Integer.toHexString(usageCode)
                    + " (masked: 0x" + Integer.toHexString(masked) + ")");
        }
        String data = String.format("%s%02X0000", CC_HEADER, masked);
        data += Ch9329PacketUtil.makeChecksum(data);
        return data;
    }

    /**
     * Send a Consumer Control press-then-release ("tap") sequence.
     *
     * @param port             USB serial port (may be {@code null} for BT-only)
     * @param bluetoothService BT service instance (may be {@code null} for USB-only)
     * @param bluetoothServiceBound whether the BT service is currently bound
     * @param usageCode        usage bitmask (0x01=Vol+, 0x02=Vol-, 0x04=Mute, ...)
     */
    public static void sendConsumerControlTap(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound,
            int usageCode) {
        // Press
        String pressHex = buildPacketHex(usageCode);
        byte[] pressBytes = Ch9329PacketUtil.hexStringToByteArray(pressHex);
        sendRaw(port, bluetoothService, bluetoothServiceBound, pressBytes, "Press");

        // Release after short delay
        try {
            Thread.sleep(TAP_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        sendRaw(port, bluetoothService, bluetoothServiceBound, RELEASE_PACKET, "Release");
    }

    // ── Convenience methods ──────────────────────────────────────────
    // Reserved as convenience API for future use; current dispatch path calls sendConsumerControlTap() directly

    /** Sends a Volume Up tap ({@link #USAGE_VOLUME_UP}). */
    @SuppressWarnings("unused")
    public static void sendVolumeUp(UsbSerialPort port, BluetoothService bt, boolean btBound) {
        sendConsumerControlTap(port, bt, btBound, USAGE_VOLUME_UP);
    }

    /** Sends a Volume Down tap ({@link #USAGE_VOLUME_DOWN}). */
    @SuppressWarnings("unused")
    public static void sendVolumeDown(UsbSerialPort port, BluetoothService bt, boolean btBound) {
        sendConsumerControlTap(port, bt, btBound, USAGE_VOLUME_DOWN);
    }

    /** Sends a Mute tap ({@link #USAGE_MUTE}). */
    @SuppressWarnings("unused")
    public static void sendMute(UsbSerialPort port, BluetoothService bt, boolean btBound) {
        sendConsumerControlTap(port, bt, btBound, USAGE_MUTE);
    }

    /** Sends a Play/Pause tap ({@link #USAGE_PLAY_PAUSE}). */
    @SuppressWarnings("unused")
    public static void sendPlayPause(UsbSerialPort port, BluetoothService bt, boolean btBound) {
        sendConsumerControlTap(port, bt, btBound, USAGE_PLAY_PAUSE);
    }

    /** Sends a Next Track tap ({@link #USAGE_NEXT_TRACK}). */
    @SuppressWarnings("unused")
    public static void sendNextTrack(UsbSerialPort port, BluetoothService bt, boolean btBound) {
        sendConsumerControlTap(port, bt, btBound, USAGE_NEXT_TRACK);
    }

    /** Sends a Previous Track tap ({@link #USAGE_PREV_TRACK}). */
    @SuppressWarnings("unused")
    public static void sendPrevTrack(UsbSerialPort port, BluetoothService bt, boolean btBound) {
        sendConsumerControlTap(port, bt, btBound, USAGE_PREV_TRACK);
    }

    /** Sends a Stop tap ({@link #USAGE_STOP}). */
    @SuppressWarnings("unused")
    public static void sendStop(UsbSerialPort port, BluetoothService bt, boolean btBound) {
        sendConsumerControlTap(port, bt, btBound, USAGE_STOP);
    }

    /** Sends an Eject CD tap ({@link #USAGE_EJECT}). */
    @SuppressWarnings("unused")
    public static void sendEject(UsbSerialPort port, BluetoothService bt, boolean btBound) {
        sendConsumerControlTap(port, bt, btBound, USAGE_EJECT);
    }

    // ── Internal ─────────────────────────────────────────────────────

    private static void sendRaw(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound,
            byte[] bytes,
            String action) {
        if (port != null) {
            try {
                port.write(bytes, 100);
                Log.v(TAG, "USB CC " + action + ": " + bytesToHex(bytes));
            } catch (IOException e) {
                Log.e(TAG, "USB CC write failed (" + action + "): " + e.getMessage());
            }
        } else if (bluetoothServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendData(bytes);
            Log.v(TAG, "BT CC " + action + ": " + bytesToHex(bytes));
        } else {
            Log.w(TAG, "No connection for Consumer Control " + action);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
