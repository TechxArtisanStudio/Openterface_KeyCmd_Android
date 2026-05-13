# KM Pro QWER tap vs KM Basic (re-assessed)

## Your observation (clarified)

- **KM Basic** QWER: light touch / quick “swipe-tap” on a base key (e.g. **K**) usually still produces the character on the target.
- **KM Pro** built-in QWER: same gesture sometimes produces **no HID**, while feedback can still feel like a press.

This is **not** about KM Pro “strip scroll sensitivity” (that only affects the side scroll strip).

## Root cause: different HID timing between Basic and Pro

### KM Basic — letters use DOWN-first HID

[`BasicPhysicalKeyboardView.wireKeyedRepeatOrHold`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/basic/BasicPhysicalKeyboardView.java) wires QWER rows with `wireRepeatableTap` → [`BasicKeyFeedback.repeatableKeyTouchListener`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/basic/BasicKeyFeedback.java).

In `repeatableKeyTouchListener`, on **ACTION_DOWN** (after haptic + pressed):

- It immediately runs **`action.run()`** → `tapKey(...)` → **HID is sent on finger down**, before the finger leaves the key or before any swipe completes.
- Listener **returns `true`** on DOWN / MOVE / UP so the key **owns the full gesture**.

So a “glancing” or “swipe across” tap that still has a valid **DOWN inside K** will **already have emitted K** even if **UP** lands outside the key or the stream ends oddly.

### KM Pro — many keys use UP-only HID (Alternate hints path)

[`CustomKeyboardView.attachKeyListeners`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java): when **Alternate hints** are on (`keyboardAlternatesHintsEnabled`), `gamingTouch` is false for keys that support long-press alternates. Then:

- **Haptic** still runs on **ACTION_DOWN**.
- **HID** runs only from **`handleKeyPress` on ACTION_UP** when `!holdRepeatSuppressUpTap && isTouchInsideView(v, event)`.
- For plain letters, **ACTION_DOWN often returns `false`** (listener does not consume), unlike Basic’s always-`true`.

So the same physical gesture that **felt** like a tap can **fail to send** if:

1. **UP** coordinates are slightly **outside** the key bounds (quick swipe-off / jitter), or  
2. The stream becomes **ACTION_CANCEL** (parent intercept, etc.) — CANCEL path does not call `handleKeyPress`, or  
3. Any other path where UP does not run the inside check as expected.

**Bottom line:** Basic is more forgiving for “swipe-tap” because the **character is committed on DOWN**; Pro (with hints on) waits for a **clean UP inside the key**, which matches long-press alternates UX but is **strict** for glancing touches.

## Long-press alternates popup: product tradeoff vs “delay / inconsistent” feeling

The alternate-hints path is **not** mainly “waiting `ViewConfiguration.getLongPressTimeout()` on every tap.” On a short successful tap, the popup **never opens**; `longPressHandler` removes the pending `openAlternates` runnable on **ACTION_UP** (see `attachKeyListeners` in [`CustomKeyboardView.java`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) around `ALT_LONG_PRESS_TIMEOUT_MS` / `showAlternatesPopup`). So **wall-clock delay before HID** on a crisp tap is dominated by **finger-down to finger-up**, not by the long-press timer.

What the feature **does** introduce is a **different interaction contract** than KM Basic (and than many users’ muscle memory):

| Dimension | KM Basic (letters) | KM Pro + Alternate hints |
|-----------|-------------------|---------------------------|
| When base character is committed | **DOWN** (immediate `tapKey`) | **UP** (after gesture resolves as “tap”, not popup / not cancel) |
| When haptic / pressed UI fires | DOWN | DOWN |
| Second modality on same key | Repeat after ~400ms | Long-press → **popup** (same DOWN starts both timers mentally) |

So the “inconsistent UX in mental model” is largely **true**: the **sensory affordance** (“I touched the key”) is tied to **DOWN**, but the **host-visible action** for a simple character is tied to **UP** and extra conditions. Users who compare to Basic (or a hardware keyboard) reasonably expect **commitment aligned with the first strong feedback**. That mismatch is **structural** to “tap = UP to distinguish from long-press that never sends base key,” not a random bug.

**Is this a drawback of using long-press-popup alternates?** In a narrow engineering sense: **yes, it couples two goals** on the same surface—(1) fast base taps and (2) discoverable alternate entry—so you must spend UX budget on **gesture ownership**, **hit tolerance**, and **clear expectations**. In a broader product sense: the feature is still defensible if mitigations restore predictability without removing alternates.

Optional **product-level** directions (for the plan backlog, not all need code in v1):

- Treat **Alternate hints off** as the supported “**Basic-like latency**” mode and surface that clearly in settings copy.
- Reduce dual modality on the **same** gesture channel (e.g. alternates only via a dedicated control, Fn layer, or swipe-from-key) so base keys can return to **DOWN-first** HID without ambiguity—larger design change.

## Hit-test parity

[`BasicKeyFeedback.isInsideView`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/basic/BasicKeyFeedback.java) vs [`CustomKeyboardView.isTouchInsideView`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) are essentially the same strict local bounds (Basic uses `< width`; Pro uses `<= width` — negligible). The dominant difference is **DOWN vs UP for HID**, not a different slop implementation.

## Improvement directions

Engineering items (1–2, 4) **do not remove** the long-press alternates feature; they narrow the gap between “felt press” and “successful UP-gated tap” so the **structural** UP-vs-DOWN contract is less painful in practice. Item 3 and the product bullets above address the tradeoff **explicitly** for users who prioritize Basic-like behavior.

1. **Gesture ownership (high value, low risk)**  
   For Pro keys that currently return `false` on DOWN but fire HID on UP: return **`true` on DOWN** (and MOVE while tracking) like Basic, and optionally **`requestDisallowInterceptTouchEvent(true)`** on DOWN so parents are less likely to inject CANCEL.

2. **Relax UP “inside” for tap completion**  
   Add **touch slop** (or small px margin) to the UP inside test so quick swipe-taps that still “intended” the key are not dropped.

3. **Optional parity mode**  
   Turning **Alternate hints** off already switches those keys to the **gaming** path (HID on DOWN + repeat) in `attachKeyListeners` — closer to Basic feel, at the cost of long-press alternate popup behavior for those keys.

4. **Release scheduling** (secondary)  
   Coalesce `sendReleaseData` posts / threads to reduce races on very fast multi-key input (see earlier analysis).

5. **Settings / onboarding copy**  
   Briefly explain that with **Alternate hints on**, base characters commit on **release** (and long-press opens alternates), vs **off** → behavior closer to KM Basic. Reduces “bug?” framing into informed tradeoff.

## Legitimacy review (plan vs your reported issues)

**Verdict: the plan is legitimate** — conclusions are grounded in a direct comparison of [`CustomKeyboardView.attachKeyListeners`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) (≈2818–2939) and KM Basic’s [`BasicKeyFeedback.repeatableKeyTouchListener`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/basic/BasicKeyFeedback.java) (≈127–137). No hand-waving: the behaviors differ in code, not only in subjective feel.

**Mapping to your issues**

1. **Confusion (haptic + visual, but no character)** — Explained by **decoupling**: `performKeyHapticFeedback` runs on **ACTION_DOWN** (≈2831) while `handleKeyPress` → HID runs on **ACTION_UP** only on the hints-on path (≈2904–2905). The user’s nervous system binds to DOWN; the host only updates after UP passes gates. That is a predictable mismatch, not an unexplained flake.

2. **Missing keys on very quick taps** — Consistent with **UP-gated** send plus **strict** `isTouchInsideView` (≈2942–2945) and **ACTION_CANCEL** paths that remove the pending alternates runnable but **never** call `handleKeyPress` (≈2911–2934). Basic avoids this class of loss for letters because **HID already left on DOWN** in `repeatableKeyTouchListener`.

3. **“Slightly delayed” vs Basic** — For a successful tap, HID is emitted at **finger-up** in Pro (hints on) vs **finger-down** in Basic. That is real **perceptual latency** (contact → lift) even when nothing is “slow” in the handler. It is **not** the same as blocking the whole `ViewConfiguration.getLongPressTimeout()` on every tap; the long-press runnable is **cancelled on UP** when the popup never opened (≈2882–2886).

4. **Suspicion: long-press alternates conflict** — **Partially confirmed, with precision.** The conflict is **architectural** (same key must support “tap = base” vs “hold = alternates” without sending base on DOWN), not a timer **stalling** each key. The alternates machinery (`postDelayed(openAlternates)`, MOVE returning `true` while pending — ≈2846–2867) exists to keep the gesture alive for popup; it **correlates** with choosing UP-only HID, which in turn **correlates** with drops on cancel / sloppy UP. It is not proven that `showAlternatesPopup` fires on your failed taps.

**What this plan does *not* yet prove (honest limits)**

- **Transport / firmware**: USB/BT write failures or CH9329 buffering could add rare drops; the current symptoms (feedback without HID) are **fully explainable in UI logic**, so treat transport as a **secondary** hypothesis unless logs show failed writes.
- **Hit-testing on hint strip**: For keys wrapped in a `FrameLayout` with a top `hintRow` ([≈2737–2761](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java)), the **touch listener is on `textButton` only** (`listenerTarget = textButton`, ≈2793–2796). Taps that land primarily on the **hint overlay** might not hit the same code path as taps on the letter face; worth a focused QA pass if misses cluster near the top of keycaps **and** lack the Pro listener haptic.

**Falsification / how you could disprove the plan**

- If **Alternate hints off** (gaming / DOWN-first path) still shows the same **missing** HID rate as hints on for the same gesture, then look beyond UP-gating (e.g. overlay hits, transport). The plan predicts **drops and “ghost feedback”** improve markedly when hints are off for keys that support alternates.

## Key files

- Pro: [`CustomKeyboardView.java`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) — `attachKeyListeners`, `gamingTouch`, `isTouchInsideView`, `handleKeyPress`, `sendReleaseData`.
- Basic: [`BasicPhysicalKeyboardView.java`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/basic/BasicPhysicalKeyboardView.java), [`BasicKeyFeedback.java`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/basic/BasicKeyFeedback.java) — `repeatableKeyTouchListener` / `handleStandardKeyTouch`.

## Verification

- Same device: Basic Q row vs Pro built-in Q row — **quick brush** across a letter; count misses on target host.  
- Pro with **Alternate hints off** — should behave much closer to Basic for those keys.  
- After (1)+(2): repeat without changing hints preference.
