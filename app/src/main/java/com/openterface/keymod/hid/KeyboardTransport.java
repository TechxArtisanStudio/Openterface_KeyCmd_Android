package com.openterface.keymod.hid;

/**
 * Abstraction for keyboard report transport.
 *
 * <p>Decouples the keyboard rendering layer ({@code CustomKeyboardView}) from
 * the concrete transport implementation, so the same keyboard UI can drive:
 * <ul>
 *   <li>HID transport (USB / BT) &mdash; {@link HidKeyboardTransport}</li>
 *   <li>Terminal transport (ANSI escape sequences) &mdash; {@code TerminalKeyboardTransport}</li>
 * </ul>
 */
public interface KeyboardTransport {

    /**
     * Send a key event together with its modifier mask.
     *
     * @param modifierMask modifier mask built from {@code MOD_*} constants
     *                     (e.g. Ctrl=0x01, Shift=0x02, Alt=0x04, Win=0x08)
     * @param keyCode      HID usage code (e.g. 0x28 = Enter, 0x04 = a)
     */
    void sendKey(int modifierMask, int keyCode);

    /**
     * Send an "all keys released" report.
     *
     * <p>For HID transport this emits the standard all-keys-released report.
     * For terminal transport this is typically a no-op, since terminals
     * have no notion of key-release state.
     */
    void sendAllKeysReleased();

    /**
     * Return {@code true} if the transport channel is ready to send reports.
     *
     * <p>For example, USB port is connected or an SSH / serial session has
     * been established.
     */
    boolean isConnected();
}
