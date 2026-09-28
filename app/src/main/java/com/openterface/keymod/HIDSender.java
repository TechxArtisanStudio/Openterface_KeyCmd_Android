package com.openterface.keymod;

import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.openterface.keymod.hid.ConsumerControlHidTransport;

import java.io.IOException;

/**
 * HID Sender - Sends keyboard/mouse/gamepad HID events via USB or Bluetooth
 * Implements CH9329 protocol for HID over serial
 */
public class HIDSender {

    private static final String TAG = "HIDSender";
    // CH9329 UART protocol headers
    private static final byte[] KBD_HEADER   = {(byte)0x57, (byte)0xAB, 0x00, 0x02, 0x08}; // keyboard CMD
    private static final byte[] MOUSE_HEADER = {(byte)0x57, (byte)0xAB, 0x00, 0x05, 0x05}; // mouse REL CMD

    /**
     * Send keyboard event via USB or Bluetooth
     */
    public static void sendKeyEvent(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                    int modifiers, int keyCode) {
        if (keyCode == 0) {
            Log.i(TAG, "KEY_SEND_IGNORED keyCode=0 modifiers=" + modifiers);
            return;
        }

        byte[] data = buildKeyboardPacket(modifiers, keyCode);
        Log.i(TAG, "KEY_SEND keyCode=" + keyCode
            + " key=" + describeKeyCode(keyCode)
            + " modifiers=" + modifiers
            + " [ctrl=" + ((modifiers & 0x01) != 0)
            + ",shift=" + ((modifiers & 0x02) != 0)
            + ",alt=" + ((modifiers & 0x04) != 0)
            + ",cmd=" + ((modifiers & 0x08) != 0)
            + "] packet=" + byteArrayToHexString(data));
        sendPacket(usbPort, bluetoothService, data, "Keyboard");
    }

    /**
     * Send a full keyboard HID report with up to 6 simultaneous keys.
     * @param modifiers Modifier keys
     * @param keyCodes Key codes to press (0–6 keys, extras ignored)
     */
    public static void sendKeyboardReport(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                          int modifiers, int[] keyCodes) {
        byte[] data = buildKeyboardPacketMulti(modifiers, keyCodes);
        boolean isRelease = keyCodes == null || keyCodes.length == 0
                         || (keyCodes.length == 1 && keyCodes[0] == 0);
        Log.i(TAG, "KEY_REPORT keys=" + java.util.Arrays.toString(keyCodes)
            + " modifiers=" + modifiers
            + " release=" + isRelease
            + " packet=" + byteArrayToHexString(data));
        sendPacket(usbPort, bluetoothService, data, isRelease ? "Key Release" : "Keyboard");
    }

    /**
     * Send key release event
     */
    public static void sendKeyRelease(UsbSerialPort usbPort, BluetoothService bluetoothService) {
        byte[] data = buildKeyboardPacket(0, 0);
        Log.i(TAG, "KEY_RELEASE packet=" + byteArrayToHexString(data));
        sendPacket(usbPort, bluetoothService, data, "Key Release");
    }

    /**
     * Send mouse movement eventj
     */
    public static void sendMouseMovement(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                         int deltaX, int deltaY, int buttons) {
        byte[] data = buildMousePacket(deltaX, deltaY, buttons);
        sendPacket(usbPort, bluetoothService, data, "Mouse Move");
    }

    /**
     * Send scroll / horizontal pan using the same relative-mouse report as Keyboard and Mouse
     * ({@code sendScrollData}): vertical delta in the wheel byte, horizontal delta as relative X.
     */
    public static void sendScroll(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                  int deltaX, int deltaY) {
        if (deltaX == 0 && deltaY == 0) {
            return;
        }
        if (deltaY != 0) {
            int wheel = Math.max(-127, Math.min(127, deltaY));
            byte[] data = buildMousePacketFull(0, 0, 0, wheel);
            sendPacket(usbPort, bluetoothService, data, "Mouse Scroll");
        }
        if (deltaX != 0) {
            int boundedX = Math.max(-127, Math.min(127, deltaX));
            byte[] data = buildMousePacketFull(boundedX, 0, 0, 0);
            sendPacket(usbPort, bluetoothService, data, "Mouse HorizScroll");
        }
    }

    /**
     * Send mouse click event
     */
    public static void sendMouseClick(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                      int buttons, boolean press) {
        if (press) {
            sendMouseMovement(usbPort, bluetoothService, 0, 0, buttons);
        } else {
            sendMouseMovement(usbPort, bluetoothService, 0, 0, 0);
        }
    }

    /**
     * Send gamepad event (with analog stick positions)
     */
    public static void sendGamepadEvent(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                        int buttons, int leftX, int leftY, int rightX, int rightY) {
        // For now, send as keyboard events - full gamepad protocol TBD
        Log.v(TAG, "Gamepad event: buttons=" + buttons + ", sticks=(" + leftX + "," + leftY + "),(" + rightX + "," + rightY + ")");
    }

    // ── Consumer Control (media / volume keys via CH9329 CMD 0x03) ──
    // Reserved as convenience API for future use; current dispatch path uses ConsumerControlHidTransport directly

    /**
     * Send a Consumer Control key tap (press + release) via CH9329 CMD 0x03.
     *
     * @param usageCode one of the {@code USAGE_*} constants in
     *                  {@link ConsumerControlHidTransport}
     *                  ({@code 0x01}=Vol+, {@code 0x02}=Vol-, {@code 0x04}=Mute,
     *                  {@code 0x08}=Play, {@code 0x10}=Next, {@code 0x20}=Prev,
     *                  {@code 0x40}=Stop, {@code 0x80}=Eject)
     */
    @SuppressWarnings("unused")
    public static void sendConsumerControl(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                            int usageCode) {
        ConsumerControlHidTransport.sendConsumerControlTap(
                usbPort, bluetoothService, bluetoothService != null, usageCode);
    }

    @SuppressWarnings("unused")
    public static void sendVolumeUp(UsbSerialPort usbPort, BluetoothService bluetoothService) {
        sendConsumerControl(usbPort, bluetoothService, ConsumerControlHidTransport.USAGE_VOLUME_UP);
    }

    @SuppressWarnings("unused")
    public static void sendVolumeDown(UsbSerialPort usbPort, BluetoothService bluetoothService) {
        sendConsumerControl(usbPort, bluetoothService, ConsumerControlHidTransport.USAGE_VOLUME_DOWN);
    }

    @SuppressWarnings("unused")
    public static void sendMute(UsbSerialPort usbPort, BluetoothService bluetoothService) {
        sendConsumerControl(usbPort, bluetoothService, ConsumerControlHidTransport.USAGE_MUTE);
    }

    /**
     * Send raw packet via USB or Bluetooth
     */
    public static void sendPacket(UsbSerialPort usbPort, BluetoothService bluetoothService,
                                  byte[] data, String type) {
        // USB path
        if (usbPort != null) {
            try {
                // Split into 20-byte chunks for CH9329
                int offset = 0;
                while (offset < data.length) {
                    int chunkSize = Math.min(20, data.length - offset);
                    byte[] chunk = new byte[chunkSize];
                    System.arraycopy(data, offset, chunk, 0, chunkSize);
                    
                    usbPort.write(chunk, 100);
                    Thread.sleep(10);
                    offset += chunkSize;
                }
                Log.v(TAG, "Sent " + type + " via USB: " + byteArrayToHexString(data));
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error sending USB data: " + e.getMessage());
            }
        }
        // Bluetooth path
        else if (bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendData(data);
            Log.i(TAG, "Sent " + type + " via BLE: " + byteArrayToHexString(data));
        } else {
            Log.w(TAG, "No connection available for sending " + type);
        }
    }

    /**
     * Build keyboard HID packet (CH9329: 5-byte header + 8-byte data + 1-byte checksum = 14 bytes)
     * Format: 57 AB 00 02 08 | modifier | 00 | key1..key6 | checksum
     */
    private static byte[] buildKeyboardPacket(int modifiers, int keyCode) {
        byte[] data = new byte[14]; // 5 header + 8 data + 1 checksum
        System.arraycopy(KBD_HEADER, 0, data, 0, 5);
        data[5]  = (byte) modifiers;
        data[6]  = 0;              // Reserved
        data[7]  = (byte) keyCode; // Key slot 1
        data[8]  = 0;              // Key slot 2
        data[9]  = 0;              // Key slot 3
        data[10] = 0;              // Key slot 4
        data[11] = 0;              // Key slot 5
        data[12] = 0;              // Key slot 6
        data[13] = calculateChecksum(data);
        return data;
    }

    /**
     * Build keyboard HID packet with multiple simultaneous keys.
     * Fills up to 6 key slots; extras are silently ignored.
     */
    private static byte[] buildKeyboardPacketMulti(int modifiers, int[] keyCodes) {
        byte[] data = new byte[14];
        System.arraycopy(KBD_HEADER, 0, data, 0, 5);
        data[5] = (byte) modifiers;
        data[6] = 0; // Reserved
        if (keyCodes != null) {
            for (int i = 0; i < Math.min(keyCodes.length, 6); i++) {
                data[7 + i] = (byte) keyCodes[i];
            }
        }
        data[13] = calculateChecksum(data);
        return data;
    }

    /**
     * Build relative mouse HID packet (CH9329: 5-byte header + 5-byte data + 1-byte checksum = 11 bytes)
     * Format: 57 AB 00 05 05 | 01(relative) | buttons | deltaX | deltaY | wheel | checksum
     */
    private static byte[] buildMousePacket(int deltaX, int deltaY, int buttons) {
        return buildMousePacketFull(deltaX, deltaY, buttons, 0);
    }

    private static byte[] buildMousePacketFull(int deltaX, int deltaY, int buttons, int wheel) {
        byte[] data = new byte[11]; // 5 header + 5 data + 1 checksum
        System.arraycopy(MOUSE_HEADER, 0, data, 0, 5);
        data[5] = 0x01;            // Relative mode
        data[6] = (byte) buttons;  // Button mask: bit0=left, bit1=right, bit2=middle
        data[7] = (byte) deltaX;   // Signed X delta
        data[8] = (byte) deltaY;   // Signed Y delta
        data[9] = (byte) wheel;    // Scroll wheel (vertical)
        data[10] = calculateChecksum(data);
        return data;
    }

    /**
     * Calculate packet checksum
     */
    private static byte calculateChecksum(byte[] data) {
        int sum = 0;
        for (int i = 0; i < data.length - 1; i++) {
            sum += (data[i] & 0xFF);
        }
        return (byte) (sum & 0xFF);
    }

    /**
     * Convert byte array to hex string (for debugging)
     */
    public static String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static String describeKeyCode(int keyCode) {
        switch (keyCode) {
            case 40: return "Enter";
            case 41: return "Escape";
            case 42: return "Backspace";
            case 43: return "Tab";
            case 44: return "Space";
            case 74: return "Home";
            case 77: return "End";
            case 79: return "Right";
            case 80: return "Left";
            case 81: return "Down";
            case 82: return "Up";
            default:
                if (keyCode >= 4 && keyCode <= 29) {
                    char c = (char) ('A' + (keyCode - 4));
                    return String.valueOf(c);
                }
                if (keyCode >= 30 && keyCode <= 38) {
                    char c = (char) ('1' + (keyCode - 30));
                    return String.valueOf(c);
                }
                if (keyCode == 39) {
                    return "0";
                }
                return "Unknown";
        }
    }
}
