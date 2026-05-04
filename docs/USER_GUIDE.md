# KeyMod Android - User Guide

> **KeyMod** is a companion Android app for the [Openterface KeyMod](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android) — a hardware KVM (Keyboard-Video-Mouse) device that lets you control any computer from your phone via USB or Bluetooth.

---

## 📱 Quick Start

### 1. Install the App

- Download the latest APK from [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions) or [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases)
- Enable **Install Unknown Apps** on your Android device
- Install the APK

> **Requirements:** Android 8.0+ (API 26), Android phone/tablet with USB OTG support

### 2. Choose Your Connection

KeyMod supports two connection methods:

| Method | How | Notes |
|--------|-----|-------|
| **USB** | Connect phone to KeyMod device via USB-C | Most reliable, lowest latency |
| **Bluetooth (BLE)** | Pair with KeyMod device via Bluetooth | Wireless convenience |

### 3. Select a Mode

On the **Welcome & Guide** screen (first launch or side menu), tap one of six mode cards:

| Mode | What It Does |
|------|-------------|
| ⌨️ **Keyboard** | Full virtual keyboard with all keys |
| 🎮 **Gamepad** | Game controller with analog sticks + buttons |
| 📋 **Macros** | Programmable macro sequences |
| ⚡ **Shortcuts** | Pre-built keyboard shortcuts (Ctrl+C, Win+L, etc.) |
| 🎤 **Voice** | Voice-to-keyboard input with AI |
| 🖥️ **Presentation** | Slide/presenter controls for decks |

---

## 🔌 Connection Guide

### USB Connection

1. Connect your phone to the KeyMod device via USB-C cable
2. Open the app → tap the **connection icon** (top-right) or go to **Settings → General**
3. Tap **USB Connection**
4. Accept the USB permission dialog when prompted
5. Status changes to ✅ **Connected**

### Bluetooth Connection

1. Turn on Bluetooth on your phone
2. Open the app → tap the **connection icon**
3. Tap **Bluetooth Connection**
4. Select your KeyMod device from the scan results
5. Status changes to ✅ **Connected**

### Auto-Connect

Enable **Auto-connect on startup** in the connection dialog to automatically reconnect to your last-used device when the app launches.

---

## ⌨️ Keyboard Mode

### Layout

The virtual keyboard provides a **full QWERTY layout** with these sections:

#### Modifier Keys
- **Ctrl** — Control key
- **Alt** — Alt key  
- **Win** — Windows key
- **Cmd** — Command key (Mac)
- **Super** — Super key (Linux)
- **Shift** — Shift (toggle caps)
- **Caps** — Caps Lock toggle
- **Tab** — Tab
- **Esc** — Escape
- **Fn** — Function key toggle

#### Key Sections
- **Letter keys** (QWERTY layout, tap Shift/Caps for uppercase)
- **Number keys** (0-9, with `!@#$%^&*()` on shift)
- **Symbol keys** (~`-_=+[]{}\|;:'",<.>/?)
- **Function keys** (F1-F12)
- **Navigation keys** (Home, End, PgUp, PgDn, Ins, Del)
- **System keys** (PrtSc, ScrLk, Pause)
- **Arrow keys** (Up, Down, Left, Right)
- **Space, Enter, BackSpace**

### Usage Tips

- Hold **modifier keys** (Ctrl, Alt, Win) then tap a letter for combos like `Ctrl+C`
- Tap **Shift** for uppercase or top-row symbols
- Tap **Fn** to access F1-F12 keys
- Tap **?123** to switch to number/symbol layout

### Fixed Shortcut Strip (Top Rows)

In keyboard mode, the fixed shortcut strip uses two rows of keys beneath the swipeable favorites row.

- **Target OS setting** (`Windows`, `Linux`, `macOS`) changes modifier **labels/icons** on the main keyboard and on fixed strip **Page 1** row 2 (Ctrl / Alt / Win or Control / Option / Command).

On **Page 0** of the fixed strip (F‑key / digit row):

- **`Fn` cleared / local Fn off:** row 2 is **F7–F12** and **`=`** (sends equals); row 3 is **F1–F6** and **`Fn`**.
- **`Fn` latched / local Fn on:** row 2 is **`7`** **`8`** **`9`** **`0`** **`+`** **`-`** **`*`**; row 3 is **`1`** **`2`** **`3`** **`4`** **`5`** **`6`**; **`Fn`** still toggles latch.

**Shortcut strip display** (names vs icons vs combo text such as Alt+X) is controlled by the **DISPLAY** key on the **swipeable favorites row**: it appears on the **same page as “Create shortcut”**, immediately **after** that button. Each tap cycles through three modes: shortcut **name**, **icon** (when the shortcut has a drawable icon; otherwise combo text), and **chord** (combo text).

On **Page 1** of the fixed strip (ESC / navigation page), when **local Fn** (row 3 col 7) is on:

- **Row 2** sends **Scroll Lock**, **PrtSc**, **Caps Lock**, **Pause/Break**, **Home**, **PgUp**; the IME toggle key is unchanged.
- **Row 3** sends **Space**, **Bksp**, **Del**, **Insert**, **End**, **PgDn**.  
  Ctrl/Alt/Win positions still support **long‑press** modifier lock using the underlying modifier keys.

On **Page 2** of the fixed strip (**Shortcut Hub**), **local Fn** toggles two full punctuation rows (strip display modes use the row‑1 DISPLAY key after Create, not the fixed strip).

On that page, the **small top‑right corner hints** show the **other local‑Fn latch cap for the same strip column** (slot‑symmetric with the Shortcut Hub strip catalog), not alternate glyphs from a decorative strip skin.

- **Fn off — upper row:** **`(`**, **`)`**, **`[`**, **`]`**, **`:`**, **`#`**, **`@`**
- **Fn off — lower row:** **`/`**, **`\`**, **`|`**, **`?`**, **`-`**, **`_`**, **`Fn`** (toggle)
- **Fn on — upper row:** two **Shortcut Hub profile** slots (**tap** = activate profile, **long‑press** = assign), then **`~`** **`'`** **`"`** **`%`** **`^`**
- **Fn on — lower row:** **`<`** **`>`** **`*`** **`&`** **`,`** **`.`**, **`Fn`**

Shifted glyphs assume a US‑QWERTY‑style host layout; other layouts may produce different characters.

Media keys are not sent as Consumer HID in this app build; use keyboard shortcuts on the host where needed.

---

## 🎮 Gamepad Mode

Virtual game controller with:
- **D-pad** (directional pad)
- **Action buttons** (A, B, X, Y)
- **Shoulder buttons** (L1, R1, L2, R2)
- **Analog sticks** (Left & Right)
- **Start / Select** buttons

> ⚠️ Gamepad HID protocol is under active development. Basic button support is available.

---

## ⚡ Shortcuts Mode

Pre-configured keyboard shortcuts for quick access:

### Common Shortcuts
| Shortcut | Action |
|----------|--------|
| Ctrl+C | Copy |
| Ctrl+V | Paste |
| Ctrl+A | Select All |
| Ctrl+X | Cut |
| Ctrl+Z | Undo |
| Ctrl+S | Save |

### Windows Shortcuts
| Shortcut | Action |
|----------|--------|
| Win+Tab | Task View |
| Win+S | Search |
| Win+E | Explorer |
| Win+R | Run Command |
| Win+D | Show Desktop |
| Win+L | Lock PC |

### System Shortcuts
| Shortcut | Action |
|----------|--------|
| Alt+F4 | Close Window |
| Ctrl+Alt+Del | Task Manager |
| Alt+PrtScr | Window Screenshot |

---

## 📋 Macros Mode

Create and save custom key sequences to execute complex actions with a single tap.

### Features
- **Create macros** by recording key sequences
- **Save** macros with custom names
- **Edit** existing macros
- **Delete** macros you no longer need
- **Execute** with one tap

---

## 🎤 Voice Mode

Voice-controlled keyboard input powered by AI:

### Configuration
Go to **Settings → Voice Input** to configure:
- Voice recognition engine
- AI backend settings
- Language preferences

### AI Integration
Go to **Settings → AI Settings** to configure AI model connections for voice-to-command processing.

---

## ⚙️ Settings

Access via the **⚙️ gear icon** on the main screen. Four tabs:

### General
- Connection type (USB / Bluetooth)
- Auto-connect toggle
- Input mode preferences
- Screen orientation settings

### Voice Input
- Voice recognition configuration
- Microphone settings
- Language options

### AI Settings
- AI model configuration
- API endpoint settings
- Model parameters

### History
- View your recent macro executions
- Connection history
- Activity log

---

## 📐 Orientation

KeyMod supports both portrait and landscape modes:
- **Portrait** (2×3 grid) — thumb-friendly for phones
- **Landscape** (3×2 grid) — desktop-style for tablets

The app auto-rotates when you turn your device.

---

## 🔧 Troubleshooting

### App shows "Not Connected"
- Check USB cable is firmly connected
- Try disconnecting and reconnecting
- For Bluetooth: ensure device is discoverable

### USB permission denied
- Go to Android **Settings → Apps → KeyMod → Permissions**
- Enable USB access
- Re-launch the app

### Keys not sending
- Verify connection status is **Connected**
- Try switching modes and back
- Check if the KeyMod device is powered on

### Bluetooth won't pair
- Turn Bluetooth off and on
- Forget the device and re-pair
- Ensure KeyMod device is in pairing mode

---

## 📦 Build from Source

```bash
# Clone the repo
git clone https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android.git
cd Openterface_KeyMod_Android

# Build (requires Java 21, Android SDK 35)
./gradlew assembleDebug

# APK output
ls app/build/outputs/apk/debug/KeyMod-debug.apk

# Install on device
adb install -r app/build/outputs/apk/debug/KeyMod-debug.apk
```

---

## 🏗️ Technical Details

| Detail | Value |
|--------|-------|
| **Package** | `com.openterface.keymod` |
| **Min SDK** | Android 8.0 (API 26) |
| **Target SDK** | Android 15 (API 35) |
| **Version** | 1.0 (code 1) |
| **HID Protocol** | CH9329 UART |
| **USB Serial** | usb-serial-for-android |
| **Bluetooth** | RxAndroidBle 1.19.0 |
| **Connection Modes** | USB / BLE Composite |

### HID Protocol (CH9329)

KeyMod communicates with the target computer using the **CH9329 protocol** over USB serial or BLE:

- **Keyboard**: 5-byte header + 8-byte data + 1-byte checksum (14 bytes total)
- **Mouse**: 5-byte header + 5-byte data + 1-byte checksum (11 bytes total)
- Data is sent in **20-byte chunks** with 10ms delay between chunks
- Supports keyboard (8-key rollover) and relative mouse movement

---

## 🤝 Support

- **GitHub Issues:** [Report bugs](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/issues)
- **Community:** [TechxArtisan Discord](https://discord.gg/techxartisan)
- **Openterface Project:** [TechxArtisan Studio](https://github.com/TechxArtisanStudio)

---

## 📄 License

Open source. See the project repository for details.

---

*Last updated: 2026-04-20*
