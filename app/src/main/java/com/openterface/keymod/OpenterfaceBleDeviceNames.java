package com.openterface.keymod;

import java.util.regex.Pattern;

/**
 * Shared BLE advertisement name matching for Openterface / KVM / KeyMod / KeyCmd devices.
 */
public final class OpenterfaceBleDeviceNames {

    private static final Pattern NAME_PATTERN = Pattern.compile("(?i)(openterface|kvm|keymod|keycmd).*");

    private OpenterfaceBleDeviceNames() {}

    public static boolean matchesAdvertisedName(String sanitizedName) {
        return sanitizedName != null && NAME_PATTERN.matcher(sanitizedName).matches();
    }
}
