package com.openterface.keymod.hid;

/**
 * CH9329 {@code CMD_GET_INFO} (0x01) with {@code LEN=0x00}: requests chip version, USB enum state,
 * and keyboard LED status in the response {@code DATA[2]}.
 *
 * <p>Packet matches open-source CH9329 Arduino driver: {@code 57 AB 00 01 00 03} (checksum 0x03).
 */
public final class Ch9329HostLockQuery {

    private Ch9329HostLockQuery() {
    }

    /** Pre-built GET_INFO query (includes checksum). */
    public static final byte[] GET_INFO_PACKET =
            Ch9329PacketUtil.hexStringToByteArray("57AB00010003");
}
