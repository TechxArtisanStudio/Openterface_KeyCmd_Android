# KeyMod FAQ — Keyboard output and strip profiles

Common questions about what KeyMod can type over USB, especially **Shortcut Hub** “Rows 2–3 · Strip” profiles. Later sections cover **Keyboard & Mouse (Basic)** modifier behavior and **holding keys** (repeat vs real hold).

---

## What is the difference between “Keyboard & Mouse” and “Keyboard & Mouse Pro”?

**Keyboard & Mouse** is the **Basic** tier: a dedicated full-screen flow with its own keyboard, numpad, touchpad, and IME-style compose screen. The usual app **header** (title, mode shortcuts, global target OS, connection cluster) is **hidden** while you are in Basic; those actions live on the **keyboard’s top row** instead.

**Keyboard & Mouse Pro** is the **advanced** composite mode: the familiar combined layout with **Shortcut Hub** strip rows, split options, and richer IME behavior—what power users already expect from the single “keyboard + mouse” experience.

---

## Does KeyMod send “Unicode” or special characters directly over USB?

**Not as a dedicated Unicode pipe.** KeyMod works like a **standard USB keyboard**: it sends **key presses and modifier keys** (Shift, Ctrl, Alt, Win/Command, and so on). The **computer you plug into** turns those into letters and symbols using your **keyboard layout** and software.

So KeyMod does **not** bypass the normal rules of “USB keyboard + host OS.” There is no mode where **every symbol printed on a key** is guaranteed to appear on screen, for all characters and all machines.

---

## Why don’t some strip keys type the same symbol that is shown on the key?

The fixed strip and Rows 2–3 overrides behave like a **normal keyboard**: the label is what you asked to show on the cap, but the **host layout and app** decide what actually appears unless you bound a **plain HID key** that matches that character on your layout.

**Rows 2–3 · Strip** profiles (**Default**, **Mine**, and any **custom** profiles you create) let you assign **single-key HID** shortcuts per slot. That is the most reliable path: the device sends the same scan codes a physical key would.

---

## What usually works on “any” computer?

You can count on KeyMod behaving like a **real keyboard** for:

- Letters, digits, and common punctuation **as your host layout maps them**  
- **Keyboard shortcuts** (copy, paste, save, etc.—depending on OS and app)  
- **Function keys**, arrows, Tab, Enter, Escape, Space, and other **standard keys**

That is where results are most **consistent across different machines**.

---

## Rows 2–3 strip profiles in the app

Built-in **Default** and **Mine** start from the **factory strip layout** (no overrides until you edit). You can **create additional strip profiles**, **import/export** JSON, and **assign** profiles to the page 3 strip quick-toggle row—same customization as before, without bundled decorative or Unicode-heavy presets.

---

## What is the difference between sticky modifiers and long-press chord on the Basic keyboard?

These options apply only to the **full keyboard** in **Keyboard & Mouse (Basic)** (**Settings → Keyboard & Mouse**).

**Sticky modifiers:** Tap **Ctrl**, **Shift**, **Alt**, or **Win/Cmd** once to **latch** it on (highlighted). Tap again to turn off. To type **!**, tap **Shift**, then tap **1** as separate steps—like sticky keys on a physical keyboard.

**Momentary and long-press chord (default):** A **short tap** sends that modifier **once** to the target (quick press and release). **Long-press** a modifier and **keep your finger on it**, then tap other keys to chord (for example long-press **Shift** and tap **1** for **!**). **Lift your finger** from the modifier to stop.

Changing modifier mode **rebuilds** the Basic keyboard and clears latched modifiers.

---

## What does “Hold modifier on target while chording” do?

This switch appears when **Momentary and long-press chord** is selected. It is **on** by default.

When **on**, after you long-press a modifier, KeyMod sends a **real modifier-down** to the connected device and, after each chorded key, sends the **hold again** so the target keeps treating the modifier as pressed while your finger stays on the modifier. That supports several shifted symbols in a row (for example **!** then **@** without releasing Shift between number taps).

When **off**, you still get the on-screen chord highlight, but the host may only see the modifier combined with each single key event. Turn it **off** if a specific computer misbehaves with sustained modifier-down over USB.

---

## On the Basic full keyboard, holding a key types the same character many times. Can it act like one long press instead?

**Yes.** In **Settings → Keyboard & Mouse**, in the **KM Basic keyboard** section, pick **Hold key down on the target** instead of **Repeat key presses while held (default)**.

| Option | What happens on the connected computer |
|--------|------------------------------------------|
| **Repeat (default)** | After a short pause, KeyMod sends **many separate key taps** while your finger stays down—similar to auto-repeat when you hold a key for typing (for example a row of **w** characters). |
| **Hold** | KeyMod sends **one key-down** when you press and **releases** when you lift your finger. The host treats it like a **physically held key**—often what games or movement controls expect. |

This applies to the **full keyboard** in **Keyboard & Mouse (Basic)** (letters, numbers, function row, Space, arrows, and similar keys). It does **not** change **Keyboard & Mouse Pro** or the shortcut strip.

Changing this option **rebuilds** the Basic keyboard layout so the new behavior applies immediately.

---

## Do Basic modifier settings affect Pro mode or the shortcut strip?

**No.** Sticky vs chord and “Hold modifier on target while chording” apply to **Keyboard & Mouse (Basic)** only. **Keyboard & Mouse Pro** and the **Shortcut Hub** strip use separate behavior.

---

## GamePad Mod: can I move the touchpad and use the stick (WASD) at the same time?

**Yes.** Use **one finger on the touchpad** for the cursor and **another finger on the stick** (or face buttons). The layout treats those as separate pointers so mouse movement and stick or keys can run together.

---

## See also

- [USER_GUIDE.md](USER_GUIDE.md) — general use, connection, and strip overview  
- [KEYBOARD_ALTERNATES.md](KEYBOARD_ALTERNATES.md) — how some alternate characters use the same Unicode-style entry path as long-press options  
