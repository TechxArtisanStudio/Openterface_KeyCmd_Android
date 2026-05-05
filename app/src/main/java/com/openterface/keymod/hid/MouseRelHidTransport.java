package com.openterface.keymod.hid;

import android.util.Log;

import com.openterface.keymod.BluetoothService;
import com.openterface.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

/**
 * Relative mouse / wheel HID packets (CH9329), shared by {@link com.openterface.fragment.CompositeFragment}
 * and KM Basic touchpad.
 */
public final class MouseRelHidTransport {

    private static final String TAG = "MouseRelHidTransport";

    private MouseRelHidTransport() {
    }

    public static void releaseAll(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        String releaseSendMsData = "57AB00050501000000000D";
        if (bluetoothServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            try {
                byte[] bytes = Ch9329PacketUtil.hexStringToByteArray(releaseSendMsData);
                Thread.sleep(10);
                bluetoothService.sendData(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (port != null) {
            try {
                byte[] bytes = Ch9329PacketUtil.hexStringToByteArray(releaseSendMsData);
                Thread.sleep(10);
                port.write(bytes, 20);
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "releaseAll: " + e.getMessage());
            }
        }
    }

    public static void sendRelMove(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound,
            boolean dragButtonHeld,
            float startMoveMsX,
            float startMoveMsY,
            float lastMoveMsX,
            float lastMoveMsY) {
        new Thread(() -> {
            try {
                int xMovement = (int) (startMoveMsX - lastMoveMsX);
                int yMovement = (int) (startMoveMsY - lastMoveMsY);
                if (Math.abs(xMovement) < 2 && Math.abs(yMovement) < 2) {
                    return;
                }
                String xByte;
                if (xMovement == 0 || lastMoveMsX == 0) {
                    xByte = "00";
                } else if (xMovement > 0) {
                    xByte = String.format("%02X", Math.min(xMovement, 0x7F));
                } else {
                    xByte = String.format("%02X", 0x100 + xMovement);
                }
                String yByte;
                if (yMovement == 0 || lastMoveMsY == 0) {
                    yByte = "00";
                } else if (yMovement > 0) {
                    yByte = String.format("%02X", Math.min(yMovement, 0x7F));
                } else {
                    yByte = String.format("%02X", 0x100 + yMovement);
                }
                String buttonByte = dragButtonHeld ? "01" : CH9329MSKBMap.MSAbsData().get("SecNullData");
                String sendMsData =
                        CH9329MSKBMap.getKeyCodeMap().get("prefix1")
                                + CH9329MSKBMap.getKeyCodeMap().get("prefix2")
                                + CH9329MSKBMap.getKeyCodeMap().get("address")
                                + CH9329MSKBMap.CmdData().get("CmdMS_REL")
                                + CH9329MSKBMap.DataLen().get("DataLenRelMS")
                                + CH9329MSKBMap.MSRelData().get("FirstData")
                                + buttonByte
                                + xByte
                                + yByte
                                + CH9329MSKBMap.DataNull().get("DataNull");
                sendMsData = sendMsData + Ch9329PacketUtil.makeChecksum(sendMsData);
                if (sendMsData.length() % 2 != 0) {
                    sendMsData += "0";
                }
                byte[] bytes = Ch9329PacketUtil.hexStringToByteArray(sendMsData);
                if (bluetoothServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    bluetoothService.sendData(bytes);
                } else if (port != null) {
                    port.write(bytes, 20);
                }
            } catch (Exception e) {
                Log.e(TAG, "sendRelMove: " + e.getMessage());
            }
        }).start();
    }

    public static void sendScroll(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound,
            int deltaX,
            int deltaY) {
        new Thread(() -> {
            try {
                if (deltaX == 0 && deltaY == 0) {
                    return;
                }
                String base =
                        CH9329MSKBMap.getKeyCodeMap().get("prefix1")
                                + CH9329MSKBMap.getKeyCodeMap().get("prefix2")
                                + CH9329MSKBMap.getKeyCodeMap().get("address")
                                + CH9329MSKBMap.CmdData().get("CmdMS_REL")
                                + CH9329MSKBMap.DataLen().get("DataLenRelMS")
                                + CH9329MSKBMap.MSRelData().get("FirstData")
                                + "00";
                if (deltaY != 0) {
                    String wheelByte = deltaY > 0
                            ? String.format("%02X", Math.min(deltaY, 0x7F))
                            : String.format("%02X", 0x100 + Math.max(deltaY, -0x7F));
                    String packet = base + "00" + "00" + wheelByte;
                    packet += Ch9329PacketUtil.makeChecksum(packet);
                    byte[] bytes = Ch9329PacketUtil.hexStringToByteArray(packet);
                    writePacket(port, bluetoothService, bluetoothServiceBound, bytes);
                }
                if (deltaX != 0) {
                    int boundedX = Math.max(-127, Math.min(127, deltaX));
                    String xByte = boundedX >= 0
                            ? String.format("%02X", boundedX)
                            : String.format("%02X", 0x100 + boundedX);
                    String packet = base + xByte + "00" + "00";
                    packet += Ch9329PacketUtil.makeChecksum(packet);
                    byte[] bytes = Ch9329PacketUtil.hexStringToByteArray(packet);
                    writePacket(port, bluetoothService, bluetoothServiceBound, bytes);
                }
            } catch (Exception e) {
                Log.e(TAG, "sendScroll: " + e.getMessage());
            }
        }).start();
    }

    private static void writePacket(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound,
            byte[] bytes) throws IOException {
        if (bluetoothServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendData(bytes);
        } else if (port != null) {
            port.write(bytes, 20);
        }
    }

    public static void sendMouseClick(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound,
            String packetBodyWithChecksum) {
        new Thread(() -> {
            try {
                byte[] bytes = Ch9329PacketUtil.hexStringToByteArray(packetBodyWithChecksum);
                if (bluetoothServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    bluetoothService.sendData(bytes);
                } else if (port != null) {
                    port.write(bytes, 20);
                }
                Thread.sleep(30);
                releaseAll(port, bluetoothService, bluetoothServiceBound);
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "sendMouseClick: " + e.getMessage());
            }
        }).start();
    }

    public static void sendLeftClick(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        String base = "57AB0005050101000000";
        String data = base + Ch9329PacketUtil.makeChecksum(base);
        sendMouseClick(port, bluetoothService, bluetoothServiceBound, data);
    }

    public static void sendRightClick(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        String base = "57AB0005050102000000";
        String data = base + Ch9329PacketUtil.makeChecksum(base);
        sendMouseClick(port, bluetoothService, bluetoothServiceBound, data);
    }

    public static void sendMiddleClick(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        String base = "57AB0005050104000000";
        String data = base + Ch9329PacketUtil.makeChecksum(base);
        sendMouseClick(port, bluetoothService, bluetoothServiceBound, data);
    }

    public static void sendDoubleClick(
            UsbSerialPort port,
            BluetoothService bluetoothService,
            boolean bluetoothServiceBound) {
        new Thread(() -> {
            try {
                String base = "57AB0005050101000000";
                String clickData = base + Ch9329PacketUtil.makeChecksum(base);
                byte[] bytes = Ch9329PacketUtil.hexStringToByteArray(clickData);
                for (int i = 0; i < 2; i++) {
                    if (bluetoothServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                        bluetoothService.sendData(bytes);
                    } else if (port != null) {
                        port.write(bytes, 20);
                    }
                    Thread.sleep(30);
                    releaseAll(port, bluetoothService, bluetoothServiceBound);
                    Thread.sleep(30);
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "sendDoubleClick: " + e.getMessage());
            }
        }).start();
    }
}
