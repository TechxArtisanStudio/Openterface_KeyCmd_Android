# KeyMod FAQ — Keyboard output and strip profiles

Common questions about what KeyMod can type over USB, especially **Shortcut Hub** “Rows 2–3 · Strip” profiles.

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

## See also

- [USER_GUIDE.md](USER_GUIDE.md) — general use, connection, and strip overview  
- [KEYBOARD_ALTERNATES.md](KEYBOARD_ALTERNATES.md) — how some alternate characters use the same Unicode-style entry path as long-press options  
