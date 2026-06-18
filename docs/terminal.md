# Terminal Feature

This document describes the Terminal feature in KeyCmd, which provides SSH connectivity over BLE-Eth and USB ECM transports.

---

## Architecture

```
TerminalFragment → SshClient (JSch 0.2.17) → TransportAdapter
                                                  │
                                     ┌────────────┴────────────┐
                                     │                         │
                              UsbEcmTransport          BleEthTransport
                              (TCP Socket)            (BLE GATT tunnel)
                                                         │
                                                    QueuePipe (×2)
                                                    inbound / outbound
                                                         │
                                                   BluetoothService
```

## Transports

| Transport | Latency | Throughput | Fragmentation | Terminal Size |
|-----------|---------|------------|---------------|---------------|
| USB ECM   | 5–10ms  | ~500 KB/s  | None          | Unlimited     |
| BLE-Eth   | 50–100ms| ~5 KB/s    | 246 B max     | 75×15 rec.   |

## BLE-Eth Protocol

**Frame format:** `[0x57][0xAB][ADDR][CMD][LEN][PAYLOAD][CHECKSUM]`

**Commands:** `0x10` CONNECT, `0x11` DATA, `0x12` DISCONNECT, `0x90` CONNECT_RESP, `0x91` DATA_RESP, `0x92` DISCONN_RESP, `0xD2` CONN_CLOSED

**Fragmentation:** 3-byte header (flags/seq/connId), 5ms inter-fragment delay.

## QueuePipe Fix (v1.1.0)

**Problem:** Java's `PipedInputStream`/`PipedOutputStream` tracks the last writing thread. JSch's SSH handshake writes from a transient "connect" thread. When that thread exits, reads throw `"Write end dead"`.

**Solution:** `QueuePipe` uses `LinkedBlockingQueue<byte[]>` instead — thread-safe, no thread-liveness dependency, zero-length sentinel for EOF.

## SSH Configuration

| Setting | Value |
|---------|-------|
| KEX | `curve25519-sha256` |
| Host key | `ssh-ed25519,rsa-sha2-512,rsa-sha2-256` |
| Cipher | `aes128-ctr` |
| MAC | `hmac-sha2-256` |
| Compression | none |
| Auth | `password,keyboard-interactive` |
| Handshake timeout | 20s |

## Known Issues

1. **KEXINIT timeout** — mitigated: narrow algorithm list, 5ms fragment delay, 20s timeout
2. **BLE slot exhaustion** — mitigated: send 6 DISCONNECT frames before each connect, 5s cleanup wait
3. **Data loss** — mitigated: ACK mechanism, checksums; retransmission not yet implemented

## Files

| File | Purpose |
|------|---------|
| `TerminalFragment.java` | UI controller |
| `TerminalView.java` | Terminal renderer |
| `TerminalSession.java` | Screen buffer |
| `SshClient.java` | JSch wrapper |
| `BleEthTransport.java` | BLE-Eth protocol |
| `UsbEcmTransport.java` | USB ECM transport |
| `BleEthSocketFactory.java` | JSch socket factory |
| `BleEthSocket.java` | BLE socket adapter |
| `TransportAdapter.java` | Transport interface |
| `QueuePipe.java` | Thread-safe byte pipe |
| `FrameParser.java` | BLE frame parser |
| `DataReassembler.java` | Fragment reassembler |
| `AnsiEscapeParser.java` | ANSI escape handler |

## Tests

- `QueuePipeTest.java` — 18 unit tests covering multi-thread stress, writer-exit scenarios, partial reads, backpressure, EOF
- `FrameParserTest.java` — Frame parsing
- `DataReassemblerTest.java` — Fragment reassembly
- `BleEthTransportTest.java` — Transport framing
- `AnsiEscapeParserTest.java` — ANSI sequences
- `TerminalSessionTest.java` — Session state

## Debugging

```bash
# BLE connection
adb logcat | grep "BluetoothService" | grep -E "connected|disconnected|error"

# Data transfer
adb logcat | grep "BleEthTransport" | grep -E "TX|RX|fragment|reassemble"

# SSH handshake
adb logcat | grep "SshClient" | grep -E "banner|KEX|auth|channel"

# QueuePipe
adb logcat | grep "QueuePipe"
```