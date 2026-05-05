---
name: Gamepad touchpad multitouch
overview: "User retest: simultaneous **face buttons** + touchpad works. **Stick (WASD / STICK_KEY)** does not update while the touchpad finger is moving — caused by an exclusive `if / else if` in `GamepadView.onTouchDynamicLayout` ACTION_MOVE."
todos:
  - id: fix-move-branching
    content: "Refactor dynamic ACTION_MOVE so touchpad deltas and dynamicPointerStick stick updates both run (replace touchpad vs stick else-if with sequential logic or parallel blocks)"
  - id: verify-two-finger-stick
    content: "Manual test: finger A on touchpad moving, finger B on left stick — expect analog/stick-key HID while cursor moves"
  - id: optional-legacy-path
    content: "Confirm legacy onTouchEvent (non-dynamic) has no touchpad; if ever added, apply same non-exclusive MOVE pattern"
  - id: docs-optional
    content: "Optional FAQ one-liner: two-handed use for pad + stick once fixed"
---

# GamePad Mod: touchpad + stick (WASD)

## User observation (retest)

- **Buttons + touchpad**: works (simultaneous press/move OK).
- **Stick (WASD) + touchpad**: while moving the touchpad, stick movement / stick-key output does not behave as expected.

## Root cause (code)

In [`GamepadView.java`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/GamepadView.java) `onTouchDynamicLayout`, `ACTION_MOVE` uses a **single chain of `else if`**:

1. Background manipulation (two fingers on empty area)
2. **`touchpadPointerId >= 0`** → emit touchpad deltas only
3. **`!dynamicPointerStick.isEmpty()`** → update stick(s) and `analogStickListener`

Because (2) and (3) are mutually exclusive, **whenever the touchpad pointer is active, the stick branch never runs** on MOVE — even if another finger is on the stick (`dynamicPointerStick` populated on that pointer’s DOWN / POINTER_DOWN).

Relevant structure (conceptually):

```text
if (isManipulatingBg && ...) { ... }
else if (touchpadPointerId >= 0 && touchpadDeltaListener != null) { ... touchpad only ... }
else if (!dynamicPointerStick.isEmpty() && analogStickListener != null) { ... all sticks ... }
else if (draggedComponentId ...) { ... }
```

This matches “can press buttons but not move stick with pad”: **buttons** do not need per-frame MOVE handling (POINTER_DOWN fires `onButtonPress`); **sticks** require every MOVE to update deflection / stick-key HID.

## Intended fix (implementation direction)

- In **dynamic** `ACTION_MOVE` only:
  - If `touchpadPointerId >= 0` and listener present → compute and fire **touchpad delta** (same as today).
  - **Additionally**, if `!dynamicPointerStick.isEmpty()` and `analogStickListener != null` → run the **existing stick loop** (same body as today’s third branch), regardless of whether the touchpad branch ran.
- Keep background manipulation and edit-drag as higher-priority or mutually exclusive with pad/stick only where still correct (e.g. two-finger bg vs pad — preserve existing behavior).
- After change: verify **two fingers** — pad finger moving, stick finger deflecting — and **one finger on stick only** (no regression when `touchpadPointerId < 0`).

## Out of scope / already clarified

- Face buttons + touchpad: no code change required for basic HID (user confirmed).
- Earlier hypothesis about one-finger slide from pad to button remains valid for **buttons** only; not the stick issue.

## Files to touch

- Primary: [`app/src/main/java/com/openterface/keymod/GamepadView.java`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/GamepadView.java) — `onTouchDynamicLayout` `ACTION_MOVE` only.
