package com.openterface.keymod.hid;

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
                Log.d(TAG, "USB keyboard: " + sendKbData);
            } catch (IOException e) {
                Log.e(TAG, "USB keyboard write failed: " + e.getMessage());
            }
        } else if (bluetoothServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendData(bytes);
            Log.d(TAG, "BT keyboard: " + sendKbData);
        } else {
            Log.w(TAG, "No connection for keyboard HID");
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
            } catch (IOException e) {
                Log.e(TAG, "Keyboard release write failed: " + e.getMessage());
            }
        } else if (bluetoothServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendData(bytes);
        }
    }
}
