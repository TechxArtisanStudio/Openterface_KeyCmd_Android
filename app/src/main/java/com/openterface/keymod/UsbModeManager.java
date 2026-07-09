package com.openterface.keymod;

import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;

/**
 * Manages USB work mode switching on the KeyMod device via CH9329 command 0x30.
 *
 * The firmware supports 4 persistent USB modes:
 *   1 = HID + ACM
 *   2 = HID + UAC + ACM
 *   3 = HID + ACM bridge
 *   4 = HID + ECM bridge
 *
 * Commands are sent as CH9329 frames (header 0x57 0xAB 0x00 CMD LEN + data + checksum)
 * over USB serial or BLE. The device responds with CMD 0xB0 containing status info.
 */
public class UsbModeManager {

    private static final String TAG = "UsbModeManager";

    /** CH9329 command ID for USB mode management. */
    private static final byte CMD_USB_MODE = 0x30;
    /** CH9329 response command ID (CMD | 0x80). */
    static final byte CMD_USB_MODE_RESPONSE = (byte) 0xB0;

    /** Sub-command: read status. */
    private static final byte SUB_READ = 0x00;
    /** Sub-command: write persistent mode. */
    private static final byte SUB_WRITE = 0x01;
    /** Sub-command: erase persistent mode record. */
    private static final byte SUB_CLEAR = 0x02;
    /** Sub-command: request NVIC_SystemReset(). */
    private static final byte SUB_REBOOT = 0x03;

    /** USB work mode IDs (match firmware USB_WORK_MODE defines). */
    public static final int MODE_HID_ACM = 1;
    public static final int MODE_HID_UAC_ACM = 2;
    public static final int MODE_HID_ACM_BRIDGE = 3;
    public static final int MODE_HID_ECM_BRIDGE = 4;

    /** Human-readable mode names. Index by mode number (index 0 unused). */
    private static final String[] MODE_NAMES = {
            null,
            "HID + ACM",
            "HID + UAC + ACM",
            "HID + ACM Bridge",
            "HID + ECM Bridge"
    };

    /** Callback interface for asynchronous status read results. */
    public interface StatusCallback {
        void onStatusReceived(UsbModeStatus status);
        void onError(String error);
    }

    /**
     * Immutable snapshot of the device's USB mode status.
     * Parsed from the CMD 0xB0 response frame.
     */
    public static class UsbModeStatus {
        public final int lastStatus;
        public final int compiledMode;
        public final int activeMode;
        public final int storedMode;
        public final int configStatus;
        public final int flags;
        public final int capabilityMask;

        UsbModeStatus(int lastStatus, int compiledMode, int activeMode,
                       int storedMode, int configStatus, int flags, int capabilityMask) {
            this.lastStatus = lastStatus;
            this.compiledMode = compiledMode;
            this.activeMode = activeMode;
            this.storedMode = storedMode;
            this.configStatus = configStatus;
            this.flags = flags;
            this.capabilityMask = capabilityMask;
        }

        /** Whether the given mode is supported by the current firmware build. */
        public boolean isModeSupported(int mode) {
            return (capabilityMask & (1 << mode)) != 0;
        }

        @Override
        public String toString() {
            return "UsbModeStatus{active=" + modeName(activeMode)
                    + ", stored=" + modeName(storedMode)
                    + ", compiled=" + modeName(compiledMode)
                    + ", supported=" + capabilityMaskString() + "}";
        }

        public String capabilityMaskString() {
            StringBuilder sb = new StringBuilder();
            for (int m = 1; m <= 4; m++) {
                if (isModeSupported(m)) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(modeName(m));
                }
            }
            return sb.toString();
        }
    }

    /**
     * Get the human-readable name for a mode number.
     */
    public static String modeName(int mode) {
        if (mode >= 1 && mode <= 4 && MODE_NAMES[mode] != null) {
            return MODE_NAMES[mode];
        }
        return "Unknown (" + mode + ")";
    }

    // ---------- Command Builders ----------

    /**
     * Build a CH9329 frame to read the current USB mode status.
     * Frame: 57 AB 00 30 01 00 CS  (LEN=1, data=[SUB_READ])
     */
    public static byte[] buildReadCommand() {
        return buildCommand(SUB_READ, null);
    }

    /**
     * Build a CH9329 frame to write a persistent USB mode.
     * Frame: 57 AB 00 30 03 01 <mode> <flags_lo> CS  (LEN=3)
     * or with flags: 57 AB 00 30 05 01 <mode> <flags_lo> <flags_hi> CS  (LEN=5)
     */
    public static byte[] buildWriteModeCommand(int mode, int flags) {
        if (flags == 0) {
            // No flags: LEN=2, data=[SUB_WRITE, mode]
            return buildCommand(SUB_WRITE, new byte[]{(byte) mode});
        } else {
            // With flags: LEN=4, data=[SUB_WRITE, mode, flags_lo, flags_hi]
            return buildCommand(SUB_WRITE, new byte[]{
                    (byte) mode,
                    (byte) (flags & 0xFF),
                    (byte) ((flags >> 8) & 0xFF)
            });
        }
    }

    /**
     * Build a CH9329 frame to erase the persistent mode record.
     * Frame: 57 AB 00 30 01 02 CS  (LEN=1, data=[SUB_CLEAR])
     */
    public static byte[] buildClearCommand() {
        return buildCommand(SUB_CLEAR, null);
    }

    /**
     * Build a CH9329 frame to request a device reboot.
     * Frame: 57 AB 00 30 01 03 CS  (LEN=1, data=[SUB_REBOOT])
     */
    public static byte[] buildRebootCommand() {
        return buildCommand(SUB_REBOOT, null);
    }

    /**
     * Build a CH9329 command frame with header 57 AB 00 CMD_USB_MODE LEN + subcmd + optional data + checksum.
     */
    private static byte[] buildCommand(byte subcmd, byte[] extraData) {
        int dataLen = 1 + (extraData != null ? extraData.length : 0);
        int totalLen = 5 + dataLen + 1; // header + data + checksum
        byte[] frame = new byte[totalLen];

        // Header: 57 AB 00 30 LEN
        frame[0] = (byte) 0x57;
        frame[1] = (byte) 0xAB;
        frame[2] = 0x00;
        frame[3] = CMD_USB_MODE;
        frame[4] = (byte) dataLen;

        // Data: subcmd [+ extra]
        frame[5] = subcmd;
        if (extraData != null) {
            System.arraycopy(extraData, 0, frame, 6, extraData.length);
        }

        // Checksum
        frame[totalLen - 1] = calculateChecksum(frame, totalLen - 1);

        return frame;
    }

    /**
     * Parse a CMD 0xB0 response frame into a UsbModeStatus object.
     *
     * Expected frame structure (from firmware):
     *   Header: 57 AB 00 B0 <LEN>
     *   Data: last_status(1) + compiled_mode(1) + active_mode(1) + stored_mode(1)
     *         + config_status(1) + flags(2) + capability_mask(4) = 11 bytes
     *   Checksum: 1 byte
     *
     * Total: 5 + 11 + 1 = 17 bytes
     *
     * @return parsed status or null if the frame is invalid
     */
    public static UsbModeStatus parseStatusResponse(byte[] frame) {
        if (frame == null || frame.length < 17) {
            Log.w(TAG, "parseStatusResponse: frame too short, length=" + (frame != null ? frame.length : 0));
            return null;
        }

        // Validate header
        if ((frame[0] & 0xFF) != 0x57 || (frame[1] & 0xFF) != 0xAB || (frame[2] & 0xFF) != 0x00) {
            Log.w(TAG, "parseStatusResponse: invalid header");
            return null;
        }

        int cmd = frame[3] & 0xFF;
        if (cmd != CMD_USB_MODE_RESPONSE) {
            Log.w(TAG, "parseStatusResponse: wrong CMD=" + String.format("0x%02X", cmd));
            return null;
        }

        // Validate checksum
        if (!validChecksum(frame)) {
            Log.w(TAG, "parseStatusResponse: checksum mismatch");
            return null;
        }

        // Parse data starting at offset 5
        int offset = 5;
        int lastStatus = frame[offset] & 0xFF;
        int compiledMode = frame[offset + 1] & 0xFF;
        int activeMode = frame[offset + 2] & 0xFF;
        int storedMode = frame[offset + 3] & 0xFF;
        int configStatus = frame[offset + 4] & 0xFF;
        int flags = (frame[offset + 5] & 0xFF) | ((frame[offset + 6] & 0xFF) << 8);
        int capabilityMask = (frame[offset + 7] & 0xFF)
                | ((frame[offset + 8] & 0xFF) << 8)
                | ((frame[offset + 9] & 0xFF) << 16)
                | ((frame[offset + 10] & 0xFF) << 24);

        return new UsbModeStatus(lastStatus, compiledMode, activeMode,
                storedMode, configStatus, flags, capabilityMask);
    }

    /**
     * Check if a frame has a valid CH9329 checksum.
     * Sum of all bytes except the last, mod 256, must equal the last byte.
     */
    private static boolean validChecksum(byte[] frame) {
        if (frame.length < 6) return false;
        byte expected = calculateChecksum(frame, frame.length - 1);
        return expected == frame[frame.length - 1];
    }

    private static byte calculateChecksum(byte[] data, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (data[i] & 0xFF);
        }
        return (byte) (sum & 0xFF);
    }

    // ---------- Transport ----------

    /**
     * Send a raw CH9329 frame via USB serial or BLE.
     * Follows the same dual-transport pattern as HIDSender.sendPacket().
     */
    public static void sendCommand(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                   byte[] data, String description) {
        // USB path
        if (usbPort != null) {
            try {
                int offset = 0;
                while (offset < data.length) {
                    int chunkSize = Math.min(20, data.length - offset);
                    byte[] chunk = new byte[chunkSize];
                    System.arraycopy(data, offset, chunk, 0, chunkSize);
                    usbPort.write(chunk, 100);
                    Thread.sleep(10);
                    offset += chunkSize;
                }
                Log.v(TAG, "Sent " + description + " via USB: " + toHexString(data));
            } catch (Exception e) {
                Log.e(TAG, "Error sending USB command: " + e.getMessage());
            }
        }
        // BLE path
        else if (bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendData(data);
            Log.v(TAG, "Sent " + description + " via BLE: " + toHexString(data));
        } else {
            Log.w(TAG, "No connection available for sending " + description);
        }
    }

    /**
     * Send a read-status command.
     */
    public static void readStatus(UsbSerialPort usbPort, BluetoothService bluetoothService) {
        byte[] cmd = buildReadCommand();
        sendCommand(usbPort, bluetoothService, cmd, "Read USB Mode Status");
    }

    /**
     * Send a write-mode command (no flags).
     */
    public static void writeMode(UsbSerialPort usbPort, BluetoothService bluetoothService, int mode) {
        writeMode(usbPort, bluetoothService, mode, 0);
    }

    /**
     * Send a write-mode command with flags.
     */
    public static void writeMode(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                 int mode, int flags) {
        byte[] cmd = buildWriteModeCommand(mode, flags);
        sendCommand(usbPort, bluetoothService, cmd, "Write USB Mode " + modeName(mode));
    }

    /**
     * Send a clear-persistent-config command.
     */
    public static void clearMode(UsbSerialPort usbPort, BluetoothService bluetoothService) {
        byte[] cmd = buildClearCommand();
        sendCommand(usbPort, bluetoothService, cmd, "Clear USB Mode Config");
    }

    /**
     * Send a reboot command.
     */
    public static void reboot(UsbSerialPort usbPort, BluetoothService bluetoothService) {
        byte[] cmd = buildRebootCommand();
        sendCommand(usbPort, bluetoothService, cmd, "Reboot Device");
    }

    private static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
