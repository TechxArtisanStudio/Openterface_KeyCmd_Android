package com.openterface.keymod.hid;

/**
 * Shared CH9329 hex framing helpers used by keyboard and mouse HID paths.
 */
public final class Ch9329PacketUtil {

    private Ch9329PacketUtil() {
    }

    public static String makeChecksum(String data) {
        int total = 0;
        for (int i = 0; i < data.length(); i += 2) {
            String byteStr = data.substring(i, Math.min(i + 2, data.length()));
            total += Integer.parseInt(byteStr, 16);
        }
        int mod = total % 256;
        return String.format("%02X", mod);
    }

    public static byte[] hexStringToByteArray(String byteData) {
        if (byteData.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even length");
        }
        int len = byteData.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(byteData.charAt(i), 16) << 4)
                    + Character.digit(byteData.charAt(i + 1), 16));
        }
        return data;
    }
}
