# KeyMod FAQ — Keyboard output, strip layouts, gamepad, and connections

For **app theme colors** (light/dark, accent families, KM Pro surfaces), see [THEME_AND_KM_PRO_COLORS.md](THEME_AND_KM_PRO_COLORS.md).

Common questions about what KeyMod can type over USB, especially **Rows 2–3 strip layouts** (edited from **Keyboard & Mouse Pro setup**). **Shortcut Hub** is for shortcut **profiles** and Favorites. Later sections cover **Keyboard & Mouse (Basic)** modifier behavior and **holding keys** (repeat vs real hold), **Gamepad Mode** (custom layouts, hold-and-lock, turbo, macros, and what the host sees), **Bluetooth topology**, and a short **gamepad preset glossary** at the end.

---

## What is the difference between “Keyboard & Mouse” and “Keyboard & Mouse Pro”?

**Keyboard & Mouse** is the **Basic** tier: a dedicated full-screen flow with its own keyboard, numpad, touchpad, and Compose & Send screen, plus a **Setup** gear icon (next to the target OS control) for KM Basic preferences. The usual app **header** (title, mode shortcuts, global target OS, connection cluster) is **hidden** while you are in Basic; those actions live on the **keyboard’s top row** instead.

**Keyboard & Mouse Pro** is the **advanced** composite mode: the familiar combined layout with strip rows, split options, and **IME** behavior—the same **type a buffer, then Send** flow as **Compose & Send** in Basic, with Pro’s strips, IME toggle, and layout options. In Pro, tap the **Setup** gear in the app header (**between** target OS and connection) to edit **strip layouts** and open the **General** tab (for more preferences over time). The active Row 1 shortcut profile and the active Rows 2–3 strip profile are chosen from **Keyboard & Mouse Pro** setup and **Shortcut Hub**, not from a dedicated strip swipe page.

---

## Does KeyMod send “Unicode” or special characters directly over USB?

**Not as a dedicated Unicode pipe.** KeyMod works like a **standard USB keyboard**: it sends **key presses and modifier keys** (Shift, Ctrl, Alt, Win/Command, and so on). The **computer you plug into** turns those into letters and symbols using your **keyboard layout** and software.

So KeyMod does **not** bypass the normal rules of “USB keyboard + host OS.” There is no mode where **every symbol printed on a key** is guaranteed to appear on screen, for all characters and all machines.

---

## Why don’t some strip keys type the same symbol that is shown on the key?

The fixed strip and Rows 2–3 overrides behave like a **normal keyboard**: the label is what you asked to show on the cap, but the **host layout and app** decide what actually appears unless you bound a **plain HID key** that matches that character on your layout.

**Rows 2–3** strip layouts (**Default**, **Mine**, and any **custom** layouts you create) let you assign **single-key HID** shortcuts per slot. That is the most reliable path: the device sends the same scan codes a physical key would.

---

## What usually works on “any” computer?

You can count on KeyMod behaving like a **real keyboard** for:

- Letters, digits, and common punctuation **as your host layout maps them**  
- **Keyboard shortcuts** (copy, paste, save, etc.—depending on OS and app)  
- **Function keys**, arrows, Tab, Enter, Escape, Space, and other **standard keys**

That is where results are most **consistent across different machines**.

---

## Rows 2–3 strip layouts in the app

Built-in **Default** and **Mine** start from the **factory strip layout** (no overrides until you edit). From **Keyboard & Mouse Pro**, open **Setup** (gear icon). You can **create additional layouts**, **import/export** JSON, and set the **active** Rows 2–3 strip profile from there or from **Shortcut Hub**.

---

## What is the difference between sticky modifiers and long-press chord?

The same choices apply to **Keyboard & Mouse (Basic)** (full keyboard) and to **Ctrl / Shift / Alt / Win** on **Keyboard & Mouse Pro** (main QWERTY row and top strip / fixed-row modifier cells). They share one preference: set them under **Keyboard & Mouse Basic setup** or under **Keyboard & Mouse Pro** setup → **Modifiers**.

**Sticky modifiers:** Tap **Ctrl**, **Shift**, **Alt**, or **Win/Cmd** once to **latch** it on (highlighted). Tap again to turn off. To type **!**, tap **Shift**, then tap **1** as separate steps—like sticky keys on a physical keyboard. **Press and hold ~1 second**, then **swipe up** on the lock hint, for a **host-side** swipe lock (Basic and Pro).

**Momentary and long-press chord (default):** A **short tap** sends that modifier **once** to the target (quick press and release). **Long-press** a modifier and **keep your finger on it**, then tap other keys to chord (for example long-press **Shift** and tap **1** for **!**). **Lift your finger** from the modifier to stop.

Changing modifier mode **rebuilds** the active keyboard (**Basic** or **Pro**) and clears latched modifiers and chord UI state.

---

## What does “Hold modifier on target while chording” do?

This switch appears when **Momentary and long-press chord** is selected. It is **on** by default. It applies to **Basic** full-keyboard chording and to **Pro** chording on the main keyboard and strip modifier keys.

When **on**, after you long-press a modifier, KeyMod sends a **real modifier-down** to the connected device and, after each chorded key, sends the **hold again** so the target keeps treating the modifier as pressed while your finger stays on the modifier. That supports several shifted symbols in a row (for example **!** then **@** without releasing Shift between number taps).

When **off**, you still get the on-screen chord highlight, but the host may only see the modifier combined with each single key event. Turn it **off** if a specific computer misbehaves with sustained modifier-down over USB.

---

## On the Basic full keyboard, holding a key types the same character many times. Can it act like one long press instead?

**Yes.** In **Keyboard & Mouse (Basic)**, tap the **Setup** gear icon; on **Keyboard & Mouse Basic setup**, pick **Hold key down on the target** instead of **Repeat key presses while held (default)**.

| Option | What happens on the connected computer |
|--------|------------------------------------------|
| **Repeat (default)** | After a short pause, KeyMod sends **many separate key taps** while your finger stays down—similar to auto-repeat when you hold a key for typing (for example a row of **w** characters). |
| **Hold** | KeyMod sends **one key-down** when you press and **releases** when you lift your finger. The host treats it like a **physically held key**—often what games or movement controls expect. |

This applies to the **full keyboard** in **Keyboard & Mouse (Basic)** (letters, numbers, function row, Space, arrows, and similar keys). It does **not** change **Keyboard & Mouse Pro** or the shortcut strip.

Changing this option **rebuilds** the Basic keyboard layout so the new behavior applies immediately.

---

## Do Basic modifier settings affect Pro mode or the shortcut strip?

**Yes, for modifier keys.** Sticky vs chord and **Hold modifier on target while chording** use the **same app preferences** for **Keyboard & Mouse (Basic)** and for **Ctrl / Shift / Alt / Win** on **Keyboard & Mouse Pro** (QWERTY row and strip). Other strip shortcuts and non-modifier keys are unchanged.

Swipe-locked modifiers from the hold gesture are **cleared** when you leave Pro or when the HID connection drops, so they do not leak into other modes.

---

## GamePad Mod: can I move the touchpad and use the stick (WASD) at the same time?

**Yes.** Use **one finger on the touchpad** for the cursor and **another finger on the stick** (or face buttons). The layout treats those as separate pointers so mouse movement and stick or keys can run together.

---

## Why do some gamepad layouts look crowded or “messy” in screenshots?

**That is expected.** Gamepad Mode is built around **fully customizable** layouts: you choose positions, sizes, modules, and bindings. A layout that looks busy to someone else may match **one player’s muscle memory and game**. The point is not a single “official” look—it is **software-defined controls** you reshape for your own workflow.

---

## What are hold-and-lock, turbo, and macros in Gamepad Mode?

These are **gamepad-oriented** conveniences (separate from **Keyboard & Mouse (Basic)** “hold key down on the target,” which applies to the Basic full keyboard only):

| Feature | Typical use |
|--------|----------------|
| **Hold-and-lock** | Keep an action logically **held** on the target (for example **hold left mouse button** for repeated actions in a game) without keeping your finger on the on-screen control. |
| **Turbo / rapid-fire** | A button **fires automatically** on a timer while active—useful for repeated clicks or taps. |
| **Macros** | **Chain several actions** in order; this area is still being **polished** over time in the app. |

Exact controls and labels follow the **Gamepad** screen and preset editor in your app build.

---

## Where does Bluetooth actually connect? Is KeyMod a Bluetooth receiver for my PC?

**No.** The usual **Bluetooth** link for KeyMod is **between your phone and the KeyMod hardware**. KeyMod does **not** act as a **Bluetooth dongle** or receiver for the **target computer** you plug into over USB.

Think of KeyMod as a **portable keyboard and mouse** to the host: **hardware control** (real **keyboard and mouse HID** over USB or the phone–KeyMod link), combined with **software flexibility** on the phone (layouts, mapping, and presets). The target machine does not pair with your phone’s Bluetooth keyboard—that path is **not** “Bluetooth keyboard → PC via KeyMod pairing.”

For wire-format details on how keys reach the host, see **HID Protocol (CH9329)** in [USER_GUIDE.md](USER_GUIDE.md).

---

## Can I pair a Bluetooth keyboard (or controller) only to my phone and still control the target through KeyMod?

**In principle, yes** at the **software** level: the app can treat **different input sources** on the phone (on-screen controls, presets, and—where supported—**mapped** external input) and translate them into what KeyMod sends.

Important caveats:

- There is **no generic “raw Bluetooth HID pipe”** through KeyMod to the PC. Anything coming from another device is handled by **Android and the KeyMod app**, then turned into **outgoing** KeyMod traffic the firmware understands.
- You still need a **working phone ↔ KeyMod** session for the bridge to run.

So the mental model is **“phone + app in the middle,”** not “KeyMod becomes the PC’s Bluetooth radio.”

---

## If I use a Bluetooth game controller paired to my phone, does the target PC see a real gamepad (Xbox / PlayStation style)?

**Not with current KeyMod hardware.** Today, KeyMod outputs **standard keyboard and mouse HID** to the host (the same family of behavior described around **CH9329** in [USER_GUIDE.md](USER_GUIDE.md)). The app can **map** sticks and buttons to **keys and mouse actions**, so the **target sees a keyboard and mouse**, not native **gamepad HID**.

**Native gamepad HID to the host** may be possible in the future but would need **firmware-level** work, not only app UI.

---

## My desktop has no Bluetooth—can I use KeyMod plus my phone as a kind of “relay”?

**Yes as a workflow idea, with limits.** You can plug KeyMod into the PC over **USB**, connect **phone ↔ KeyMod** (Bluetooth as your setup uses), and drive everything from the **KeyMod app** on the phone—**without** putting a Bluetooth dongle in the PC or pairing a keyboard directly to Windows.

If you also use a **Bluetooth keyboard or controller paired only to the phone**, you are still inside the model above: the **PC never pairs** with that peripheral; the **app** decides how (or whether) those inputs map into **keyboard/mouse** actions toward KeyMod. You do **not** get “the PC thinks my phone is a generic Bluetooth keyboard.”

---

## Can Android talk to KeyMod and another Bluetooth device at the same time?

**Often yes in practice**—many Android devices can keep **more than one Bluetooth connection** alive. Whether **your** phone can reliably use **KeyMod plus** a given **keyboard or controller** at once depends on **Android version, OEM Bluetooth stack, and the peripheral**. For KeyMod, what matters is that the **KeyMod session stays stable** and that any **extra** device is one the **app and OS** can expose for **mapping** into outgoing control—not a guarantee for every accessory on every phone.

---

## Gamepad preset glossary (quick)

- **D-pad** — preset type **DPAD** (left slot only); variants include cross, **split** (four separate hit targets), disc, pivot, floating, clicky (extra haptic).
- **Analog sticks** — **STICK_MOUSE** or **STICK_KEY** on **`stick_left`** and **`stick_right`**; optional **stickVisualVariant** changes cap art (concave / convex / low-profile / C-stick); Hall-effect is label-only.
- **Face buttons** — **BUTTON** modules; **faceButtonTemplate** can snap common face-cluster shapes (e.g. diamond, ABXY grid, symbol set).
- **Shoulders / triggers** — **SHOULDER** / **TRIGGER** modules with **hidKey**; **triggerVariant** is mainly documentation (`digital`, `analog`, `hair`, `adaptive`).
- **Gyro** — **layout.gyroEnabled**: tilt drives small mouse deltas while the gamepad screen is active and connected.
- **Canvas background (share/import)** — When you **share** a gamepad preset as JSON, the app can embed the background image as **base64** plus **`layout.backgroundImageMediaType`** (`image/png`, `image/jpeg`, or `image/webp`). On **import** or apply, that payload is written back to a file under app storage and the embed fields are removed so prefs stay small. Decoded size is capped (currently about **6 MiB**); older app builds ignore unknown JSON fields and will not show an embedded background until updated.

For full detail see **Gamepad Mode → Preset vocabulary** in [USER_GUIDE.md](USER_GUIDE.md).

---

## See also

- [USER_GUIDE.md](USER_GUIDE.md) — general use, connection, CH9329 / HID overview, and strip overview  
- [KEYBOARD_ALTERNATES.md](KEYBOARD_ALTERNATES.md) — how some alternate characters use the same Unicode-style entry path as long-press options  
