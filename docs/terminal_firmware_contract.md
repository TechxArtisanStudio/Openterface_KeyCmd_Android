# Terminal — App / Firmware Contract (Phase 2)

Draft checklist for App and firmware teams to align before production Terminal mode.
See also [terminal.md](./terminal.md) for current Android implementation.

## Product matrix

| Device | Wired (USB ECM) | Wireless (BLE-Eth) | Notes |
|--------|-----------------|--------------------|-------|
| KeyMod MINI | — | Primary | Lower throughput; UI should prefer compact terminal geometry |
| KeyMod PLUS | Primary (higher bandwidth) | Supported | USB ECM preferred when cable attached |

## Network / SSH assumptions

- Default target host: `192.168.11.1` (confirm with firmware DHCP/ECM bridge)
- SSH port: `22`
- Terminal type: `xterm-256color`
- Auth: password / keyboard-interactive (key-based TBD)

## BLE-Eth protocol (firmware responsibilities)

- Frame format: `[0x57][0xAB][ADDR][CMD][LEN][PAYLOAD][CHECKSUM]`
- Commands: CONNECT (0x10), DATA (0x11), DISCONNECT (0x12)
- Max payload per fragment: 246 bytes (App-side reassembly)
- Slot lifecycle: firmware must release TCP tunnel slots on DISCONNECT; App sends cleanup DISCONNECT burst before connect

## USB ECM (firmware responsibilities)

- ECM bridge mode exposes L2/L3 connectivity to target subnet
- Confirm IP assignment, routing, and whether phone reaches target SSH directly

## Error codes → user messaging (TBD)

| Condition | App behavior | Firmware signal |
|-----------|--------------|-----------------|
| BLE slot busy | Toast + retry | CONNECT_RESP failure code |
| ECM not ready | Disable USB transport in UI | USB mode != HID+ECM bridge |
| Link loss mid-session | Disconnect + overlay | GATT disconnect / ECM down |

## Open questions

1. Fixed vs DHCP IP for ECM bridge on PLUS?
2. Can BLE cleanup wait (currently ~5s) be reduced after firmware slot fix?
3. Recommended terminal rows/cols for BLE (docs suggest ~75×15 max)?
4. MINI-only SKU: hide USB transport in connection UI?

## Phase 2 deliverables

- [ ] Firmware spec sign-off (this doc + protocol examples)
- [ ] MINI / PLUS joint test matrix (latency, throughput, session stability)
- [ ] App: transport auto-select rules (USB when cable + ECM ready)
- [ ] Release: hide or gate marketing demo entry (`BuildConfig.DEBUG` / remote flag)
