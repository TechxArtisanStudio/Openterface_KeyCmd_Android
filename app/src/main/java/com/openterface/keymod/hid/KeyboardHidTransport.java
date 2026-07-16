package com.openterface.keymod.hid;

import android.os.SystemClock;
import android.util.Log;

import com.openterface.keymod.BluetoothService;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

/**
 * USB / Bluetooth keyboard report transport shared by {@code CustomKeyboardView} and KM Basic UI.
 */
public final class KeyboardHidTransport {

    private static final String TAG = "KeyboardHidTransport";

    private KeyboardHidTransport() {
    }

    public static void sendKeyReport(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound,
            int modifiers,
            int keyCode) {
        String sendKbData = String.format("57AB000208%02X00%02X0000000000", modifiers & 0xFF, keyCode & 0xFF);
        sendKbData += Ch9329PacketUtil.makeChecksum(sendKbData);
        byte[] bytes = Ch9329PacketUtil.hexStringToByteArray(sendKbData);
        if (port != null) {
            try {
                port.write(bytes, 20);
                Log.v(TAG, "USB keyboard: " + sendKbData);
                if (Log.isLoggable("KmProTouch", Log.DEBUG)) {
                    Log.v(
                            "KmProTouch",
                            SystemClock.uptimeMillis()
                                    + " transport USB sendKeyReport mod=0x"
                                    + String.format("%02X", modifiers & 0xFF)
                                    + " key=0x"
                                    + String.format("%02X", keyCode & 0xFF));
                }
            } catch (IOException e) {
                Log.e(TAG, "USB keyboard write failed: " + e.getMessage());
            }
        } else if (bluetoothServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendData(bytes);
            Log.v(TAG, "BT keyboard: " + sendKbData);
            if (Log.isLoggable("KmProTouch", Log.DEBUG)) {
                Log.v(
                        "KmProTouch",
                        SystemClock.uptimeMillis()
                                + " transport BT sendKeyReport mod=0x"
                                + String.format("%02X", modifiers & 0xFF)
                                + " key=0x"
                                + String.format("%02X", keyCode & 0xFF));
            }
        } else {
            Log.w(TAG, "No connection for keyboard HID");
            if (Log.isLoggable("KmProTouch", Log.DEBUG)) {
                Log.v(
                        "KmProTouch",
                        SystemClock.uptimeMillis()
                                + " transport sendKeyReport SKIPPED (no usb/bt)");
            }
        }
    }

    public static void sendAllKeysReleased(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        final String releasePacket = "57AB00020800000000000000000C";
        byte[] bytes = Ch9329PacketUtil.hexStringToByteArray(releasePacket);
        if (port != null) {
            try {
                port.write(bytes, 20);
                if (Log.isLoggable("KmProTouch", Log.DEBUG)) {
                    Log.v("KmProTouch", SystemClock.uptimeMillis() + " transport USB allKeysReleased");
                }
            } catch (IOException e) {
                Log.e(TAG, "Keyboard release write failed: " + e.getMessage());
            }
        } else if (bluetoothServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendData(bytes);
            if (Log.isLoggable("KmProTouch", Log.DEBUG)) {
                Log.v("KmProTouch", SystemClock.uptimeMillis() + " transport BT allKeysReleased");
            }
        } else if (Log.isLoggable("KmProTouch", Log.DEBUG)) {
            Log.v(
                    "KmProTouch",
                    SystemClock.uptimeMillis() + " transport allKeysReleased SKIPPED (no usb/bt)");
        }
    }
}
