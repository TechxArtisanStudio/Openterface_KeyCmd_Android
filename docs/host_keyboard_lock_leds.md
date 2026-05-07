# Host Num / Caps / Scroll lock sync

The app mirrors the **host PC** keyboard LED state (Num Lock, Caps Lock, Scroll Lock) when the bridge exposes it over CH9329 serial or BLE.

## Wire format (CH9329)

The app periodically sends a **GET_INFO** query (same pattern as common `CH9329::cmdGetInfo` Arduino code):

- Bytes: `57 AB 00 01 00 03` (hex `57AB00010003`; see `Ch9329HostLockQuery.GET_INFO_PACKET`).

The device responds with a normal framed reply: header `57 AB`, address, **`CMD = 0x81`** (GET_INFO ack), **`LEN >= 3`**, payload bytes, checksum. The **USB HID keyboard LED byte** is taken from **`DATA[2]`** (third data byte, 0-based index 2 in the data field). `Ch9329InboundParser` validates the frame checksum before applying.

## HID LED byte (DATA[2])

Lower three bits follow USB HID keyboard output report usage:

| Bit | Meaning      |
|-----|--------------|
| 0   | Num Lock     |
| 1   | Caps Lock    |
| 2   | Scroll Lock  |

## Transport

- **USB**: `MainActivity` polls GET_INFO on a timer while the serial port is open, writes the query, and feeds inbound `read()` data into `Ch9329InboundParser`.
- **BLE**: If the firmware notifies the same frames on FFF1, `BluetoothService` forwards notify payloads into the same parser.

If no valid GET_INFO ack is ever received, `HostKeyboardLockLeds.hasReceivedLedFromHost()` stays false and **Caps** (and other locks) keep the previous **local-only** behavior where applicable.

## QA matrix

| Scenario | Expected |
|----------|----------|
| Host physical keyboard: toggle Caps | KM Basic Caps key selected state follows host (Pro keyboard has no separate lock dot). |
| Host: toggle Num / Scroll | `HostKeyboardLockLeds` updates; there is no on-key dot for Num / Scroll on the Pro grid or Basic numpad. |
| On-screen Caps tap while host sync active | HID key still sent; local sticky flip is skipped so UI stays aligned with host. |
| Disconnect USB/BLE | `HostKeyboardLockLeds.reset()`; Basic Caps selection falls back until host sync resumes. |

Dependency: firmware must answer GET_INFO with the LED byte in `DATA[2]` as above. If your build does not, Caps behavior stays local-only where the UI still supports it.
