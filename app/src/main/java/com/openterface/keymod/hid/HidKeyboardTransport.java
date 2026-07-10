package com.openterface.keymod.hid;

import android.content.Context;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.openterface.keymod.BluetoothService;

/**
 * HID transport adapter.
 *
 * <p>Wraps the existing {@link KeyboardHidTransport} static methods so they
 * conform to the {@link KeyboardTransport} interface, allowing the KM Pro
 * keyboard UI to drive USB / BT HID reports through the same abstraction
 * used by any other transport.
 */
public class HidKeyboardTransport implements KeyboardTransport {

    private final UsbSerialPort port;
    private final BluetoothService bluetoothService;
    private final boolean bluetoothServiceBound;
    private final Context context;

    /**
     * Construct a HID transport instance.
     *
     * @param context              application context (will be converted to
     *                             ApplicationContext to avoid leaks)
     * @param port                 USB serial port (may be {@code null} for BT-only)
     * @param bluetoothService     BT service instance (may be {@code null} for USB-only)
     * @param bluetoothServiceBound whether the BT service is currently bound
     */
    public HidKeyboardTransport(Context context,
                                UsbSerialPort port,
                                BluetoothService bluetoothService,
                                boolean bluetoothServiceBound) {
        this.context = context.getApplicationContext();
        this.port = port;
        this.bluetoothService = bluetoothService;
        this.bluetoothServiceBound = bluetoothServiceBound;
    }

    @Override
    public void sendKey(int modifierMask, int keyCode) {
        KeyboardHidTransport.sendKeyReport(
                port, bluetoothService, bluetoothServiceBound, modifierMask, keyCode);
    }

    @Override
    public void sendAllKeysReleased() {
        KeyboardHidTransport.sendAllKeysReleased(port, bluetoothService, bluetoothServiceBound);
    }

    @Override
    public boolean isConnected() {
        return port != null
                || (bluetoothService != null && bluetoothServiceBound);
    }
}
