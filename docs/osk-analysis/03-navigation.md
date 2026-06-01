# Leanback Keyboard: Focus / Navigation Model, Key Geometry, Selection Rendering, Accent Popups, Shift/Symbol State Machine

This document is the behavioral core of the Android TV Leanback on-screen keyboard (LeanbackIME), captured for a 1:1 reimplementation inside the GammaOS "Nano" native C++ / OpenGL ES renderer. The reimplementation is D-pad / gamepad driven (no touch, no Android `InputConnection`); it types into a plain `std::string`. Every section below quotes real file paths, line numbers, attribute names, and numeric constants.

## Source files analyzed

- `packages/inputmethods/LeanbackIME/src/com/android/inputmethod/leanback/LeanbackKeyboardContainer.java` (1570 lines)
- `packages/inputmethods/LeanbackIME/src/com/android/inputmethod/leanback/LeanbackKeyboardController.java` (600 lines)
- `packages/inputmethods/LeanbackIME/src/com/android/inputmethod/leanback/LeanbackKeyboardView.java` (570 lines)
- `packages/inputmethods/LeanbackIME/src/com/android/inputmethod/leanback/LeanbackUtils.java` (77 lines)
- `packages/inputmethods/LeanbackIME/src/com/android/inputmethod/leanback/service/LeanbackImeService.java` (the IME glue that turns abstract `onEntry` callbacks into `InputConnection` edits; in the native port these become `std::string` ops)
- Resources: `res/xml/qwerty_us.xml`, `res/xml/sym_us.xml`, `res/xml/number.xml`, `res/xml/accent_*.xml`, `res/xml/shift_*.xml`, `res/values/dimens.xml`, `res/values/integers.xml`, `res/values/config.xml`, `res/values/attrs.xml`, `res/layout/root_leanback.xml`, `res/layout/input_leanback.xml`, `res/layout/selector.xml`

A critical structural fact: the keyboard layout itself is parsed by the **platform** class `android.inputmethodservice.Keyboard` (constructed as `new Keyboard(mContext, R.xml.qwerty_us)` in `LeanbackKeyboardContainer.initKeyboards()`, lines 650-705). LeanbackIME does NOT implement the geometry parser; it relies on the AOSP framework `Keyboard`/`Keyboard.Key`/`Keyboard.Row` classes to compute `key.x`, `key.y`, `key.width`, `key.height`, `key.edgeFlags`, `key.codes`, `key.label`, `key.icon`, `key.popupResId`. For the native port you must reimplement the relevant subset of `android.inputmethodservice.Keyboard` geometry (documented in Section 1). The platform source lives at `frameworks/base/core/java/android/inputmethodservice/Keyboard.java` (not in this package, but its behavior is the de-facto spec).

---

## 1. Key geometry

### 1.1 Where geometry comes from

`LeanbackKeyboardView.setKeyboard(Keyboard keyboard)` (lines 245-259) takes a parsed `Keyboard` and copies its key list into `mKeys` via `setKeys(mKeyboard.getKeys())` (lines 562-569). Each `KeyHolder` wraps a `Keyboard.Key` whose `x`, `y`, `width`, `height`, `edgeFlags`, `codes`, `label`, `icon`, `popupResId` were already filled in by the platform `Keyboard` XML parser. LeanbackIME never recomputes them; it only reads them.

The overall measured size is set in `LeanbackKeyboardView.onMeasure` (lines 545-560):

```
int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
...
setMeasuredDimension(width, mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
```

So the keyboard pixel size is exactly `Keyboard.getMinWidth()` x `Keyboard.getHeight()` plus view padding (padding is `0,0,0,0` in practice; `mPadding = new Rect(0,0,0,0)` at line 129, and the FrameLayout has no padding set).

### 1.2 The geometry attributes (from `res/xml/qwerty_us.xml` + `res/values/dimens.xml`)

The `<Keyboard>` root element (qwerty_us.xml lines 17-21) declares:

| Attribute | Value resource | Resolved (from dimens.xml) |
|---|---|---|
| `android:keyHeight` | `@dimen/key_height` | **28dp** (dimens.xml:21) |
| `android:keyWidth` | `@dimen/key_width` | **28dp** (dimens.xml:23) |
| `android:verticalGap` | `@dimen/keyboard_vertical_gap` | **8dp** (dimens.xml:29) |
| `android:horizontalGap` | `@dimen/keyboard_horizontal_gap` | **12dp** (dimens.xml:27) |

Special widths:
- Space key: `android:keyWidth="@dimen/space_key_width"` = **268dp** (dimens.xml:25). The comment at dimens.xml:24 documents the formula: `268 = 7 * 28 (regular key) + 6 * 12 (horizontal gap) = 196 + 72`. So the space key spans exactly 7 logical columns plus the 6 internal gaps.

There is **no per-key `keyWidth` as a percentage of total** in LeanbackIME's XML; widths are absolute dp. (The platform `Keyboard` parser also accepts `%p` units, but these layouts use fixed dp.) The default key width/height is inherited from the `<Keyboard>` root unless a `<Key>` overrides `android:keyWidth`.

### 1.3 How the platform `Keyboard` parser computes each key rect (the spec to reimplement)

The AOSP `android.inputmethodservice.Keyboard` parser walks rows top to bottom. The algorithm (which the native port must replicate) is:

1. Start `y = 0` (top of keyboard, plus the row's vertical gap before the first row is typically 0).
2. For each `<Row>`: `x = 0` at row start. Row height = row's `keyHeight` (defaults to keyboard `keyHeight`).
3. For each `<Key>` in the row:
   - `key.width` = the key's `keyWidth` (default 28dp, or `space_key_width` 268dp for space).
   - `key.height` = row height (28dp).
   - `key.x = x` (current pen position), `key.y = y`.
   - After placing, advance `x += key.width + horizontalGap` (12dp).
4. After a row, advance `y += rowHeight + verticalGap` (28 + 8 = 36dp per row step).
5. `Keyboard.getMinWidth()` = max over rows of the total row width; `Keyboard.getHeight()` = total stacked height.

Edge flags come from XML: `android:keyEdgeFlags="left"` / `"right"` on a `<Key>` and `android:rowEdgeFlags="top"` / `"bottom"` on a `<Row>`. These map to `Keyboard.EDGE_LEFT` (1), `Keyboard.EDGE_RIGHT` (2), `Keyboard.EDGE_TOP` (4), `Keyboard.EDGE_BOTTOM` (8) and are stored in `Key.edgeFlags`. They are load-bearing for navigation (Section 2).

### 1.4 The qwerty_us.xml layout in detail (the canonical 5-row layout)

Declared `leanbackime:columnCount="11"` and `leanbackime:rowCount="5"` in `res/layout/input_leanback.xml` lines 39-40. The five rows:

- **Row 0** (`rowEdgeFlags="top"`): `1 2 3 4 5 6 7 8 9 0` then **Delete** (`codes="-5"`, `keyEdgeFlags="right"`, icon `ic_ime_delete`). 11 keys. First key `1` has `keyEdgeFlags="left"`. Each digit has a `popupKeyboard="@xml/shift_N"`.
- **Row 1**: `q w e r t y u i o p` then `@` (`codes="64"`, `keyEdgeFlags="right"`). 11 keys. `q` has `keyEdgeFlags="left"`. Each letter has a `popupKeyboard` (shift_q, shift_w, accent_e, shift_r, accent_t, accent_y, accent_u, accent_i, accent_o, shift_p).
- **Row 2**: `a s d f g h j k l _ &` (`_` is `codes="95"`, `&` is `codes="38"` with `keyEdgeFlags="right"`). 11 keys. `a` has `keyEdgeFlags="left"`. Accent popups on a,s,d,g,k,l; shift popups on f,h,j.
- **Row 3**: `z x c v b n m , . - ?` (`?` is `codes="63"` `keyEdgeFlags="right"`). 11 keys. `z` has `keyEdgeFlags="left"`. Accent popups on z,c,n; shift on x,v,b,m.
- **Row 4** (`rowEdgeFlags="bottom"`): **Mode-change** (`codes="-2"`, `keyEdgeFlags="left"`, icon `ic_ime_symbols`), **Shift** (`codes="-1"`, icon `ic_ime_shift_off`), **Space** (`codes="32"`, icon `ic_ime_space`, `keyWidth="@dimen/space_key_width"`), **Left-arrow** (`codes="-3"`, icon `ic_ime_left_arrow`), **Right-arrow** (`codes="-4"`, `keyEdgeFlags="right"`, icon `ic_ime_right_arrow`). 5 keys.

Total key count: 11+11+11+11+5 = **49 keys**. This 49-key count and the geometry of the bottom row drive the magic-number index remapping in `getNearestIndex()` (Section 2.2).

### 1.5 `getNearestIndex` (pixel -> key index): the grid model and the space-key fixups

`LeanbackKeyboardView.getNearestIndex(float x, float y)` (lines 194-235) converts a pixel position to a key index using a **uniform grid** assumption (rowCount x colCount = 5 x 11):

```
x -= getPaddingLeft();  y -= getPaddingTop();
height = measuredHeight - padTop - padBottom;
width  = measuredWidth  - padLeft - padRight;
rows = getRowCount();   // 5
cols = getColCount();   // 11
row = (int)(y / height * rows);   clamp to [0, rows-1]
col = (int)(x / width  * cols);   clamp to [0, cols-1]
index = mColCount * row + col;    // 11*row + col
```

Then two hard-coded fixups for the wide space key (lines 218-226):

```
// at space key (space key is 7 keys wide)
if (index > 46 && index < 53) { index = 46; }   // grid cols 47..52 collapse onto space
// beyond space, remove 6 extra slots for space
if (index >= 53) { index -= 6; }                 // left/right arrows shift back by 6
```

Final clamp to `[0, mKeys.length-1]` (lines 228-232).

Interpretation of the magic numbers for the 49-key qwerty layout: the bottom row occupies grid row 4 (`11*4 = 44`). Grid index 44 = mode-change, 45 = shift, 46..52 = the 7 grid columns covered by the wide space key (all collapse to real key index 46 = space), 53 = left-arrow, 54 = right-arrow. Subtracting 6 maps grid 53 -> real 47 (left), grid 54 -> real 48 (right). So real indices: 44 mode, 45 shift, 46 space, 47 left, 48 right. These constants (46, 53, 6) are tied to this exact layout. The native port should not hard-code them blindly; instead compute the real index by hit-testing actual key rects, OR replicate this exact mapping if reusing the same XML.

### 1.6 Logical pixel sizes (concrete, at mdpi where 1dp = 1px)

At mdpi the qwerty_us keyboard is:
- Row pitch = keyHeight + verticalGap = 28 + 8 = 36dp; 5 rows -> last row top at 4*36 = 144dp, bottom = 144 + 28 = 172dp tall.
- A standard row of 11 keys: 11*28 + 10*12 = 308 + 120 = 428dp wide (this is `getMinWidth()`).
- Bottom row: 28 (mode) + 12 + 28 (shift) + 12 + 268 (space) + 12 + 28 (left) + 12 + 28 (right) = 428dp. Matches, confirming space = 7 cols + 6 gaps.

The selector / focus indicator base size is `@dimen/selector_size` = **24dp** (dimens.xml:33), separate from key size (28dp). Voice recognizer view = 96dp (`recognizer_size`). Action (enter) button height = `enter_key_height` 32dp, font 18sp, horizontal padding 16dp.

### 1.7 The cm->pixel "physical" mapping (used by the touchpad path, mostly irrelevant for D-pad)

`LeanbackKeyboardContainer` models a virtual physical keyboard `PHYSICAL_WIDTH_CM = 12` (line 170), `PHYSICAL_HEIGHT_CM = 5` (line 175). `getAlignmentPosition()` (lines 916-926) and `getPhysicalPosition()` (lines 928-941) convert between cm and pixels for the touch-navigation (trackpad) input mode. **For a pure D-pad port these cm conversions are dead weight** and can be dropped; D-pad navigation works entirely in pixel space via `getBestFocus` / `getNextFocusInDirection` (Section 2). Note `getNextFocusInDirection`'s MAIN case does call `getPhysicalPosition(x, y, mTempPoint)` at line 1365 but **discards the result** (mTempPoint is unused afterward); the real work is `getBestFocus(x, y, nextFocus)` at line 1366. So the cm round-trip there is a no-op you can omit.

---

## 2. The D-pad navigation algorithm (exact)

### 2.1 Top-level dispatch

`LeanbackKeyboardController.handleKeyDownEvent(keyCode, repeatCount)` (lines 417-487) handles **directional keys on key-DOWN** (so they auto-repeat):

```
KEYCODE_DPAD_LEFT  -> onDirectionalMove(DIRECTION_LEFT)
KEYCODE_DPAD_RIGHT -> onDirectionalMove(DIRECTION_RIGHT)
KEYCODE_DPAD_UP    -> onDirectionalMove(DIRECTION_UP)
KEYCODE_DPAD_DOWN  -> onDirectionalMove(DIRECTION_DOWN)
```

Direction bit flags (`LeanbackKeyboardContainer` lines 151-158):
`DIRECTION_LEFT = 1`, `DIRECTION_DOWN = 2`, `DIRECTION_RIGHT = 4`, `DIRECTION_UP = 8`, plus diagonal combinations (used only by flick gestures, not D-pad).

`onDirectionalMove(dir)` (Controller lines 311-320):
```
if (mContainer.getNextFocusInDirection(dir, mDownFocus, mTempFocus)) {
    mContainer.setFocus(mTempFocus);
    mDownFocus.set(mTempFocus);
    clearKeyIfNecessary();
}
return true;
```
`mDownFocus` is seeded to the current focus on every key-down (`onKeyDown`, line 279: `mDownFocus.set(mContainer.getCurrFocus())`).

### 2.2 The core: `getNextFocusInDirection` is geometric "step + nearest", NOT a neighbor table

`LeanbackKeyboardContainer.getNextFocusInDirection(direction, startFocus, nextFocus)` (lines 1267-1371) is **NOT** a precomputed neighbor table and **NOT** pure row/column index arithmetic. It is a **geometric ray-step**: it computes a target pixel point offset from the current key's rect in the requested direction, then calls `getBestFocus(x, y, nextFocus)` which snaps to the nearest key (via `getNearestIndex`). The dispatch is by the **type** of the currently focused element (MAIN keyboard / ACTION button / SUGGESTION strip / VOICE).

The general move distance ("step") uses key height because all keys share the same height while widths vary. The comment at lines 1334-1335 says exactly this.

**Case `TYPE_MAIN`** (lines 1332-1367) - the common case:

```
Key key = getKey(startFocus.type, startFocus.index);
float extraSlide = startFocus.rect.height()/2.0f;   // half a key-height fudge
float x = startFocus.rect.centerX();
float y = startFocus.rect.centerY();
if (startFocus.code == ASCII_SPACE) { x = mX; }     // restore remembered column when leaving space

// LEFT
if ((direction & DIRECTION_LEFT) != 0) {
    if ((key.edgeFlags & Keyboard.EDGE_LEFT) == 0) {   // not already on left edge
        x = startFocus.rect.left - extraSlide;          // step one half-key left of the left edge
    }
    // (if on the left edge: x stays at centerX, so it does NOT wrap horizontally to the right edge)
}
// RIGHT
else if ((direction & DIRECTION_RIGHT) != 0) {
    if ((key.edgeFlags & Keyboard.EDGE_RIGHT) != 0) {  // on the right edge of the keyboard
        offsetRect(mRect, mActionButtonView);           // jump to the ACTION / enter button
        x = mRect.centerX();
    } else {
        x = startFocus.rect.right + extraSlide;          // step one half-key right of the right edge
    }
}
// UP / DOWN (no edge special-casing; layout positioning handles it)
if ((direction & DIRECTION_UP) != 0) {
    y -= startFocus.rect.height() * DIRECTION_STEP_MULTIPLIER;   // 1.25 * keyHeight up
} else if ((direction & DIRECTION_DOWN) != 0) {
    y += startFocus.rect.height() * DIRECTION_STEP_MULTIPLIER;   // 1.25 * keyHeight down
}
getPhysicalPosition(x, y, mTempPoint);   // result discarded
validNextFocus = getBestFocus(x, y, nextFocus);
```

`DIRECTION_STEP_MULTIPLIER = 1.25` (line 146). With keyHeight 28dp and verticalGap 8dp, the row pitch is 36dp; a vertical step of 1.25*28 = 35dp lands you almost exactly into the adjacent row's band, where `getNearestIndex` then snaps to the column whose center the x is nearest. This is why the comment says "Half the height is to ensure the next key is reached."

**Key navigation properties that emerge from this geometry:**

- **Crossing rows of different key counts**: handled automatically because vertical movement keeps the same `x` (column pixel position) and `getNearestIndex` recomputes `col = (int)(x/width*cols)`. The bottom row's wide space key and the index fixups (Section 1.5) mean that pressing DOWN from many letters in row 3 onto the bottom row will land on whichever of mode/shift/space/left/right is horizontally nearest. Pressing UP from space (whose x is restored to `mX`, the remembered column) returns you to roughly the column you came from rather than the middle of the wide space bar - this is the "column memory" behavior. `mX`/`mY` are the remembered last-good pixel coords, set inside `getBestFocus` (lines 992-993) and `resetFocusCursor` (lines 1424-1425).

- **LEFT at the left edge**: does NOT wrap. `x` stays at the key's centerX (because the `if` only moves x when NOT on the left edge), so `getBestFocus` re-snaps to the same key. The user is stuck at the left wall. (There is no horizontal wrap-around within the main keyboard.)

- **RIGHT at the right edge**: jumps to the **ACTION (enter) button** (lines 1348-1352). This is the only way RIGHT leaves the main grid.

- **Reaching Delete**: Delete is the right-edge key of row 0 (`codes="-5"`, `keyEdgeFlags="right"`). Navigate RIGHT along row 0 to reach it; one more RIGHT jumps to the action button. UP/DOWN reach it by column.

- **Reaching Space / Shift / Mode-change / Left / Right keys**: all live in the bottom row; reach them with DOWN from the row above, or LEFT/RIGHT within the bottom row. Note space is index 46; left=47; right=48 (after fixups).

### 2.3 Crossing INTO the suggestions strip (above) and the ACTION button (right)

The suggestions strip sits above the keyboard. From a MAIN key, pressing UP repeatedly steps `y` upward by 1.25*keyHeight each time; once `y < keyboardTop`, `getBestFocus` (lines 959-1006) routes into the suggestions branch:

```
offsetRect(mRect, mMainKeyboardView);  keyboardTop = mRect.top;
final int count = mSuggestions.getChildCount();
if (y < keyboardTop && count > 0 && mSuggestionsEnabled) {
    // walk suggestion children left-to-right; pick first whose right edge is past x (or last)
    ... suggestView.requestFocus(); configureFocus(focus, mRect, i, KeyFocus.TYPE_SUGGESTION);
} else if (y < keyboardTop && mEscapeNorthEnabled) {
    validFocus = false; escapeNorth();      // dismiss keyboard upward (Section 8)
} else if (x > actionLeft) {
    configureFocus(focus, mRect, 0, KeyFocus.TYPE_ACTION);   // action button
} else {
    // main view: getNearestIndex(x,y)
}
```

`actionLeft` is `mActionButtonView`'s left edge in root coords (lines 962-963). So whenever the target x is to the right of the action button's left edge, focus goes to the action button regardless of y.

Once focused on a **SUGGESTION** (`TYPE_SUGGESTION`), `getNextFocusInDirection` (lines 1286-1331):
- DOWN: back into the main keyboard at `(startFocus.rect.centerX(), mainKeyboard.top)`.
- UP: if `mEscapeNorthEnabled`, `escapeNorth()` (dismiss); else nothing.
- LEFT/RIGHT: move to sibling suggestion `index +/- 1`, with horizontal-scroll clamping against `mSuggestionsContainer`'s margins (lines 1303-1322). Each move calls `suggestView.requestFocus()`.

Once focused on the **ACTION** button (`TYPE_ACTION`), `getNextFocusInDirection` (lines 1274-1285):
- LEFT: `getBestFocus((float)mRect.right, null, nextFocus)` where mRect is the main keyboard rect. Passing `null` for y means "use last y" (`mY`), so you re-enter the keyboard on the **same row** you left from (the comment at lines 1276-1278 explains this lets the user "hold left and wrap around the keyboard while staying in the same row").
- UP: go to the suggestions strip at `(startFocus.rect.centerX(), suggestions.centerY())`.
- (No DOWN/RIGHT handling: action button is the right-most, bottom-aligned element.)

The **VOICE** focus type (`TYPE_VOICE`) has an empty `getNextFocusInDirection` case (lines 1271-1273, `// TODO move between voice button and kb button`). Voice focus is essentially a modal full-screen overlay handled separately (Section 7).

### 2.4 Summary table: navigation transitions

| From | UP | DOWN | LEFT | RIGHT |
|---|---|---|---|---|
| MAIN key (interior) | step 1.25*h up, snap nearest | step 1.25*h down, snap nearest | step to left of left-edge, snap | step to right of right-edge, snap |
| MAIN key, left edge | up | down | stays (no wrap) | step right |
| MAIN key, right edge | up | down | step left | jump to ACTION button |
| MAIN top row, UP | into SUGGESTION strip (if enabled) or escapeNorth (if enabled) | n/a | n/a | n/a |
| MAIN, x past action left | (lands on ACTION) | | | |
| ACTION button | to SUGGESTION strip | (none) | re-enter keyboard same row | (none) |
| SUGGESTION | escapeNorth (if enabled) | into keyboard top, same column | prev suggestion | next suggestion |
| VOICE | (none, TODO) | | | |

### 2.5 Auto-repeat and move accounting

Directional keys are handled on DOWN (lines 437-448) so holding the D-pad repeats the move (Android delivers repeated key-down events). `clearKeyIfNecessary()` (lines 322-328) increments `mMoveCount`; after 3 moves it nulls `mKeyDownKeyFocus` (lines 323-327) so that a center-press that was started before moving does not later commit a stale key. On key-UP of a direction (lines 524-529) it also calls `clearKeyIfNecessary()`.

---

## 3. Selection / focus indicator (rendering)

There are **two cooperating visual elements**: (a) the per-key scale animation on the focused key's ImageView, and (b) a separate floating "selector" view (`R.id.selector`) that slides/scales to surround the focused key.

### 3.1 The floating selector view

Defined in `res/layout/selector.xml`: a `FrameLayout` `@+id/selector` of size `@dimen/selector_size` (**24dp** x 24dp) with `clipChildren="false"`, containing an `ImageView` `@+id/key_selector` whose `background="@drawable/key_selector"` (a nine-patch, `key_selector.9.png`, present in drawable-mdpi/hdpi/xhdpi/xxhdpi). It is included at the end of `root_leanback.xml` (line 78) so it floats over the keyboard.

`mSelector = mRootView.findViewById(R.id.selector)` (Container line 582). It is moved/resized by `ScaleAnimation` (Container lines 455-504), an `android.view.animation.Animation` subclass that interpolates the view's `x`, `y`, `width`, `height` toward target bounds.

### 3.2 `setSelectorToFocus` - positioning + overestimate + square-snap

`setSelectorToFocus(Rect rect, boolean overestimateWidth, boolean overestimateHeight, boolean animate)` (lines 1075-1110):

```
width  = rect.width();  height = rect.height();
if (overestimateHeight) height *= mOverestimate;   // mOverestimate = focused_scale = 120%
if (overestimateWidth)  width  *= mOverestimate;
float major = max(width,height);  float minor = min(width,height);
if (major/minor < 1.1) { width = height = max(width,height); }  // near-square -> force square
float x = rect.exactCenterX() - width/2;
float y = rect.exactCenterY() - height/2;
mSelectorAnimation.cancel();
if (animate) { mSelectorAnimation.reset(); setAnimationBounds(x,y,width,height); mSelector.startAnimation(...); }
else         { mSelectorAnimation.setValues(x,y,width,height); }
```

`mOverestimate = focused_scale = 120%` (`config.xml:22`, read at Container line 585). So the selector is drawn ~20% larger than the key in the overestimated dimension(s). The "near-square within 10%" rule forces letter keys (28x28, ratio 1.0) to a perfect square; the wide space key keeps its aspect ratio.

`setKbFocus` (lines 1037-1073) decides the overestimate flags per focus type:
- `TYPE_MAIN`: `overestimateHeight = true`; `overestimateWidth = (focus.code != ASCII_SPACE)` (line 1061) - so letters overestimate both dimensions but the wide space bar overestimates only height.
- `TYPE_VOICE` / `TYPE_ACTION` / `TYPE_SUGGESTION`: no overestimate.

### 3.3 Slide / move animation timing

`ScaleAnimation` (lines 467-472) sets `setDuration(MOVEMENT_ANIMATION_DURATION)` = **150 ms** (line 105) and `setInterpolator(sMovementInterpolator)` where `sMovementInterpolator = new DecelerateInterpolator(1.5f)` (line 110). `applyTransformation` (lines 481-494) lerps from the captured start bounds (captured at `interpolatedTime == 0`) to the end bounds. So every focus move slides the selector over 150 ms with a decelerate(1.5) curve.

### 3.4 Per-key scale animation (in `LeanbackKeyboardView`)

`LeanbackKeyboardView.setFocus(index, clicked, showFocusScale)` (lines 442-483) animates the focused key's ImageView scale:
```
float scale = clicked ? mClickedScale : (showFocusScale ? mFocusedScale : 1.0f);
mCurrentFocusView = mKeyImageViews[index];
mCurrentFocusView.animate().scaleX(scale).scaleY(scale)
    .setInterpolator(sMovementInterpolator).setDuration(mClickAnimDur).start();
```
The previously focused key animates back to scale 1.0 with `setStartDelay(mUnfocusStartDelay)`.

Scale constants (from `config.xml` + `integers.xml`):
- `mFocusedScale = focused_scale = 120%` (config.xml:22) -> 1.20
- `mClickedScale = clicked_scale = 88%` (config.xml:20) -> 0.88 (the key shrinks while clicked)
- `mClickAnimDur = clicked_anim_duration = 100 ms` (integers.xml:20)
- `mUnfocusStartDelay = unfocused_anim_delay = 30 ms` (integers.xml:22)
- `mInactiveMiniKbAlpha = inactive_mini_kb_alpha = 40` (integers.xml:26; alpha 0-255) - dims non-mini keys when a mini keyboard is up.

### 3.5 The "click" scale animator on the selector

`mSelectorAnimator` (Container lines 588-597) is a `ValueAnimator.ofFloat(1.0f, clicked_scale)` (1.0 -> 0.88) over `mClickAnimDur` (100 ms), driving `mSelector.setScaleX/Y`. It is started/reversed by `setTouchState` (lines 1137-1169) on CLICK/MOVE transitions. For a D-pad port, the relevant trigger is: `onKeyDown` of center sets `TOUCH_STATE_CLICK` (Controller line 285), which shrinks the selector; `onKeyUp` returns to `TOUCH_STATE_TOUCH_SNAP` (Controller line 301), which reverses it. Net effect: the selector visibly "presses in" on click.

### 3.6 Touch-state machine (affects only selector size, mostly trackpad-oriented)

`TOUCH_STATE_NO_TOUCH=0`, `TOUCH_STATE_TOUCH_SNAP=1`, `TOUCH_STATE_TOUCH_MOVE=2`, `TOUCH_STATE_CLICK=3` (lines 116-136). For a D-pad-only port you only ever use NO_TOUCH, SNAP, and CLICK; MOVE is a trackpad free-cursor state you can ignore. The state only changes selector scale; it does not change which key is focused.

---

## 4. Key actions (A / center / click handlers)

### 4.1 Click delivery: which keys commit on DOWN vs UP

`getSimplifiedKey` (Controller lines 589-599) maps the activation buttons to `KEYCODE_DPAD_CENTER`: `DPAD_CENTER`, `NUMPAD_ENTER`, and `BUTTON_A` all become center; `BUTTON_B` becomes `KEYCODE_BACK`. So **gamepad A = click**, **gamepad B = back/cancel**.

On center DOWN (`handleKeyDownEvent`, lines 462-476):
- repeatCount 0: snapshot `mKeyDownKeyFocus = currFocus`, reset `mMoveCount`.
- repeatCount 1: `handleKeyLongPress(keyCode)` (long press fires after one repeat, i.e. the system long-press threshold).
- `if (isKeyHandledOnKeyDown(currKeyCode)) commitKey();` - keys handled on DOWN are Delete, Left, Right (lines 497-501), so backspace/cursor keys fire immediately and **repeat while held** (because center down repeats).

On center UP (`handleKeyUpEvent`, lines 535-541):
- if current key is `KEYCODE_SHIFT`: feed `mDoubleClickDetector.addEvent(currTime)` (double-click -> caps lock; Section 6).
- else if NOT handled-on-down: `commitKey(mKeyDownKeyFocus)` - commits the key that was focused at press time (guards against a move between down and up).

`commitKey` (lines 330-361) routes by focus type:
- `TYPE_VOICE` -> `mContainer.onVoiceClick()`.
- `TYPE_ACTION` -> `mInputListener.onEntry(ENTRY_TYPE_ACTION, 0, null)`.
- `TYPE_SUGGESTION` -> `onEntry(ENTRY_TYPE_SUGGESTION, 0, suggestionText(index))`.
- default (MAIN) -> `handleCommitKeyboardKey(key.codes[0], key.label)`.

### 4.2 `handleCommitKeyboardKey` - the per-keycode action table (Controller lines 363-415)

| key.codes[0] | Constant | Action |
|---|---|---|
| -2 | `Keyboard.KEYCODE_MODE_CHANGE` | `mContainer.onModeChangeClick()` -> toggle ABC <-> SYM keyboard |
| -6 | `LeanbackKeyboardView.KEYCODE_CAPS_LOCK` | `mContainer.onShiftDoubleClick(isCapsLockOn())` -> toggle caps lock |
| -1 | `Keyboard.KEYCODE_SHIFT` | `mContainer.onShiftClick()` -> toggle shift on/off |
| -8 | `KEYCODE_DISMISS_MINI_KEYBOARD` | `mContainer.dismissMiniKeyboard()` |
| -3 | `KEYCODE_LEFT` | `onEntry(ENTRY_TYPE_LEFT)` -> move text cursor left |
| -4 | `KEYCODE_RIGHT` | `onEntry(ENTRY_TYPE_RIGHT)` -> move text cursor right |
| -5 | `Keyboard.KEYCODE_DELETE` | `onEntry(ENTRY_TYPE_BACKSPACE)` -> backspace |
| 32 | `ASCII_SPACE` | `onEntry(ENTRY_TYPE_STRING, 32, " ")` then `mContainer.onSpaceEntry()` (shift fixup) |
| 46 | `ASCII_PERIOD` | `onEntry(ENTRY_TYPE_STRING, 46, label)` then `mContainer.onPeriodEntry()` |
| -7 | `KEYCODE_VOICE` | `mContainer.startVoiceRecording()` |
| default | (any printable) | `onEntry(ENTRY_TYPE_STRING, code, label)` then `mContainer.onTextEntry()`; if mini-kb on screen, dismiss it |

`LeanbackKeyboardView` special keycode constants (lines 81-90):
```
ASCII_SPACE=32, ASCII_PERIOD=46,
KEYCODE_SHIFT=-1, KEYCODE_SYM_TOGGLE=-2, KEYCODE_LEFT=-3, KEYCODE_RIGHT=-4,
KEYCODE_DELETE=-5, KEYCODE_CAPS_LOCK=-6, KEYCODE_VOICE=-7, KEYCODE_DISMISS_MINI_KEYBOARD=-8
```
(Note `KEYCODE_SYM_TOGGLE=-2` equals `Keyboard.KEYCODE_MODE_CHANGE=-2`; `KEYCODE_DELETE=-5` equals `Keyboard.KEYCODE_DELETE=-5`; `KEYCODE_SHIFT=-1` equals `Keyboard.KEYCODE_SHIFT=-1`. These are intentionally aligned with the platform `Keyboard` constants.)

### 4.3 What `onEntry` does to the text buffer (the std::string contract)

`LeanbackImeService.handleTextEntry(type, keyCode, c)` (service lines 262-339) is the spec for the native port's text-buffer ops. Replace `InputConnection` with cursor ops over a `std::string` (with a cursor position):

| ENTRY_TYPE | Value | Buffer operation |
|---|---|---|
| `ENTRY_TYPE_BACKSPACE` | 1 | delete 1 char before cursor (`ic.deleteSurroundingText(1,0)`); clear `mEnterSpaceBeforeCommitting` |
| `ENTRY_TYPE_LEFT` | 3 | cursor-- (clamped at 0) |
| `ENTRY_TYPE_RIGHT` | 4 | cursor++ (clamped at end) |
| `ENTRY_TYPE_STRING` | 0 | if auto-space pending and key is a letter, insert a space first; then insert `c` at cursor; if key was period (46), set `mEnterSpaceBeforeCommitting=true` (auto-space after `.`) |
| `ENTRY_TYPE_SUGGESTION` | 2 | replace whole field (or amend after `@`) with suggestion text, then perform editor action (falls through to ACTION) |
| `ENTRY_TYPE_VOICE` | 6 | same as suggestion (replace field with recognized text) then action |
| `ENTRY_TYPE_ACTION` | 5 | `sendDefaultEditorAction(false)` (Go/Next/Search/Send/Done) |
| `ENTRY_TYPE_DISMISS` | 7 | `performEditorAction(IME_ACTION_NONE)` -> dismiss keyboard |
| `ENTRY_TYPE_VOICE_DISMISS` | 8 | `performEditorAction(IME_ACTION_GO)` -> commit + go |

For a Nano-style buffer: STRING inserts the chars at the caret, BACKSPACE erases one char left of the caret, LEFT/RIGHT move the caret, ACTION/DISMISS close the keyboard, SUGGESTION/VOICE replace the buffer. The `mEnterSpaceBeforeCommitting` auto-space-after-period behavior is gated by `enableAutoEnterSpace()` and only fires for alphabet keys (`LeanbackUtils.isAlphabet`, lines 37-43, which is `Character.isLetter(keyCode)`).

### 4.4 Delete repeat-on-hold

Delete (code -5) is in `isKeyHandledOnKeyDown` (lines 497-501), so it fires on each center key-DOWN. Holding center while focused on Delete produces repeated `ENTRY_TYPE_BACKSPACE` events at the system key-repeat rate. Same for Left/Right cursor keys (-3/-4). There is no separate repeat timer in LeanbackIME; it piggybacks on Android's key auto-repeat. For the native port you must implement your own repeat timer for held-A-on-Delete (and Left/Right) since you are not getting framework key-repeat.

### 4.5 Gamepad shortcut buttons (bypass focus entirely)

`handleKeyDownEvent` maps face/shoulder buttons directly to actions regardless of focused key (lines 449-460):
- `BUTTON_X` -> commit Delete (backspace)
- `BUTTON_Y` -> commit Space
- `BUTTON_L1` -> commit KEYCODE_LEFT (cursor left)
- `BUTTON_R1` -> commit KEYCODE_RIGHT (cursor right)

And on key-UP (lines 542-547):
- `BUTTON_THUMBL` -> commit Mode-change (?123 / ABC toggle)
- `BUTTON_THUMBR` -> commit Caps-lock toggle
- `ENTER` -> commit suggestion (if focused) then `ENTRY_TYPE_DISMISS`

These are very useful for a gamepad-native build: X = backspace, Y = space, L1/R1 = cursor, L3 = symbols, R3 = caps, Start/Enter = done. `BUTTON_A` = click, `BUTTON_B` = back/cancel.

---

## 5. Accent / popup mini-keyboard

### 5.1 Trigger: long-press of center on a key with a popup

A key gets a popup if its XML has `android:popupKeyboard="@xml/accent_X"` or `"@xml/shift_X"`, which the platform parser stores as `Key.popupResId`. The trigger is a **long press of the activation button (center/A)**, not LEFT/RIGHT and not a dedicated key.

Flow: `handleKeyDownEvent` center with `repeatCount == 1` calls `handleKeyLongPress(keyCode)` (Controller line 468). `handleKeyLongPress` (lines 489-495):
```
mLongPressHandled = isEnterKey(keyCode) && mContainer.onKeyLongPress();
```
`LeanbackKeyboardContainer.onKeyLongPress()` (lines 1534-1549):
```
if (mCurrKeyInfo.code == Keyboard.KEYCODE_SHIFT) {     // long-press Shift => caps lock
    onToggleCapsLock(); setTouchState(NO_TOUCH); return true;
} else if (mCurrKeyInfo.type == TYPE_MAIN) {
    mMainKeyboardView.onKeyLongPress();                 // expand the accent mini-kb
    if (mMainKeyboardView.isMiniKeyboardOnScreen()) {
        mMiniKbKeyIndex = mCurrKeyInfo.index;           // remember the originating key
        moveFocusToIndex(mMainKeyboardView.getBaseMiniKbIndex(), TYPE_MAIN);  // focus first accent
        return true;
    }
}
return false;
```

### 5.2 Mini-keyboard layout: it overwrites in-place key slots, no separate window

`LeanbackKeyboardView.onKeyLongPress()` (lines 489-521) is the heart of the accent mechanism. It does NOT pop a separate floating window; it **replaces the existing key slots** of the main grid with the accent keys, in place:

```
int popupResId = mKeys[mFocusIndex].key.popupResId;
if (popupResId != 0) {
    dismissMiniKeyboard();
    mMiniKeyboardOnScreen = true;
    Keyboard miniKeyboard = new Keyboard(getContext(), popupResId);
    List<Key> accentKeys = miniKeyboard.getKeys();
    int totalAccentKeys = accentKeys.size();
    int baseIndex = mFocusIndex;
    int currentRow = mFocusIndex / mColCount;             // mColCount = 11
    int nextRow    = (mFocusIndex + totalAccentKeys) / mColCount;
    if (currentRow != nextRow) {                          // would overflow row end
        baseIndex = nextRow * mColCount - totalAccentKeys; // right-align the run in that row
    }
    mBaseMiniKbIndex = baseIndex;
    for (int i = 0; i < totalAccentKeys; i++) {
        Key accentKey = accentKeys.get(i);
        accentKey.x = mKeys[baseIndex + i].key.x;          // inherit original slot position
        accentKey.y = mKeys[baseIndex + i].key.y;
        accentKey.edgeFlags = mKeys[baseIndex + i].key.edgeFlags;
        mKeys[baseIndex + i].key = accentKey;              // overwrite the slot
        mKeys[baseIndex + i].isInMiniKb = true;
        mKeys[baseIndex + i].isInvertible = (i == 0);      // only first key inverts case
    }
    invalidateAllKeys();
}
```

So the accent options are laid horizontally starting at the popup key's slot, inheriting the x/y/edgeFlags of the keys they overwrite. If they would run off the end of the row, the whole run is **right-aligned to that row's end** (`baseIndex = nextRow*11 - totalAccentKeys`). The non-accent keys are dimmed: in `createKeyImageView` (lines 336-337) any key with `!keyHolder.isInMiniKb` while `mMiniKeyboardOnScreen` is drawn at `mInactiveMiniKbAlpha = 40`.

Example: long-pressing `e` (accent_e.xml has 8 keys: e e-grave e-acute e-circumflex e-diaeresis e-macron e-dot e-ogonek) overwrites 8 consecutive slots starting at e's slot; the first slot keeps the base letter `e` (and is the only `isInvertible` one). `$` (shift_4.xml) has a single key, etc.

### 5.3 Navigating and committing within the mini-kb

Because the accent keys are just regular slots in `mKeys` now, navigation uses the **same** `getNextFocusInDirection` LEFT/RIGHT geometry (Section 2.2). After expansion, focus is moved to `getBaseMiniKbIndex()` (the first accent slot) via `moveFocusToIndex` (Container lines 1551-1556). The user presses LEFT/RIGHT to move among the accent options (and UP/DOWN can leave the run, which dismisses the mini-kb - see below).

Committing an accent: pressing center on an accent slot is a normal key commit -> `handleCommitKeyboardKey(code, label)` default branch -> `onEntry(ENTRY_TYPE_STRING, code, label)` then `mContainer.onTextEntry()`; and `if (mContainer.isMiniKeyboardOnScreen()) mContainer.dismissMiniKeyboard()` (Controller lines 410-412). So selecting an accent both types the accented char and dismisses the mini-kb.

`onTextEntry` (Container lines 1195-1210) also, after dismissing the mini-kb, restores focus to the original popup key index: `if (dismissMiniKeyboard()) moveFocusToIndex(mMiniKbKeyIndex, TYPE_MAIN);`.

### 5.4 Dismissing the mini-kb

`LeanbackKeyboardView.dismissMiniKeyboard()` (lines 530-539):
```
if (mMiniKeyboardOnScreen) {
    mMiniKeyboardOnScreen = false;
    setKeys(mKeyboard.getKeys());   // reload the pristine base keyboard, discarding the accent overwrite
    invalidateAllKeys();
    return true;
}
return false;
```

It is also auto-dismissed whenever focus lands on a non-mini key: `setFocus` (lines 478-481) `if (NOT_A_KEY != index && !mKeys[index].isInMiniKb) dismissMiniKeyboard();`. So pressing UP/DOWN out of the accent run, or moving LEFT/RIGHT past its ends onto a normal key, closes it. The dedicated dismiss keycode `-8` (`KEYCODE_DISMISS_MINI_KEYBOARD`) also closes it but is not bound to any visible key in these layouts.

### 5.5 Case inversion in the mini-kb

`adjustCase` (View lines 394-411): for a 1-2 char label in the mini-kb, `invert = keyHolder.isInMiniKb && keyHolder.isInvertible` (only the first/base accent slot). The effective case is `mKeyboard.isShifted() ^ invert`. So when shift is ON, the base accent key shows lowercase (inverted) while accent variants follow normal shift; when shift is OFF it shows uppercase on the base slot. This lets the popup offer the opposite case of the base letter as one of the quick choices.

---

## 6. Shift state machine

### 6.1 The three states

`LeanbackKeyboardView` (lines 58-60):
```
SHIFT_OFF = 0;   SHIFT_ON = 1;   SHIFT_LOCKED = 2;
```
Stored in `mShiftState` (line 61). `isShifted()` returns `mShiftState == SHIFT_ON || == SHIFT_LOCKED` (lines 434-436). `getShiftState()` returns the raw int. `isCapsLockOn()` is `getShiftState() == SHIFT_LOCKED` (Container lines 1567-1569).

`setShiftState(int state)` (View lines 413-428):
```
if (mShiftState == state) return;
switch (state) {
    case SHIFT_OFF:    mKeyboard.setShifted(false); break;
    case SHIFT_ON:
    case SHIFT_LOCKED: mKeyboard.setShifted(true);  break;
}
mShiftState = state;
invalidateAllKeys();   // re-render every key with new case + new shift icon
```

### 6.2 Transitions

- **Single shift click** (`onShiftClick`, Container lines 1190-1193): `setShiftState(isShifted() ? SHIFT_OFF : SHIFT_ON)`. Toggles OFF<->ON. From LOCKED, `isShifted()` is true, so a single click goes to OFF (clears caps lock too).
- **Shift double-click / long-press -> caps lock**: `DoubleClickDetector` (Controller lines 96-115) with `DOUBLE_CLICK_TIMEOUT_MS = 200`. Two center-clicks on Shift within 200 ms call `mContainer.onShiftDoubleClick(firstClickShiftLocked)`. The first click of a pair already commits a normal shift toggle; the second click triggers `onShiftDoubleClick`. `onShiftDoubleClick(wasCapsLockOn)` (Container lines 1562-1565): `setShiftState(wasCapsLockOn ? SHIFT_OFF : SHIFT_LOCKED)`. Also reachable by **long-pressing Shift** (`onKeyLongPress` -> `onToggleCapsLock` -> `onShiftDoubleClick(isCapsLockOn())`, lines 1535-1537, 1558-1560), and by the gamepad `BUTTON_THUMBR` (R3) shortcut and the explicit caps-lock keycode -6.
- **Auto-shift after typing**: `onTextEntry` (lines 1195-1205): if shifted and NOT caps-lock and NOT cap-characters -> drop to `SHIFT_OFF` (one-shot shift consumed). If not shifted but caps-lock/cap-characters required -> set `SHIFT_LOCKED`.
- **Auto-shift around space**: `onSpaceEntry` (lines 1212-1222): drop to OFF unless caps-lock/capChars/capWords; or raise to `SHIFT_ON` if those auto-cap flags are set.
- **Auto-shift after period**: `onPeriodEntry` (lines 1224-1234): same pattern, additionally honoring `mCapSentences` (so a `.` re-enables a one-shot shift for the next sentence).

### 6.3 Initial shift on input start

`onStartInputView` (Container lines 769-775): `mCapCharacters -> SHIFT_LOCKED; else (mCapSentences || mCapWords) -> SHIFT_ON; else SHIFT_OFF`. These caps flags come from the field's `inputType` flags in `setImeOptions` (lines 848-854): `TYPE_TEXT_FLAG_CAP_SENTENCES` / `_CAP_WORDS` (also forced for person-name variation) / `_CAP_CHARACTERS`.

### 6.4 How the layout reflects shift: case re-rendering + shift icon swap, NOT a separate shift layout

Important for the port: **LeanbackIME does NOT swap to `shift_*` keyboard resources for uppercase.** The `shift_*.xml` files are **accent popups** (e.g. `shift_4.xml` is just `$`, `shift_q.xml` is just `q`), not alternate full layouts. Shift is implemented purely by:

1. `mKeyboard.setShifted(true/false)` on the platform `Keyboard` (which flips an internal flag), and
2. per-key re-rendering in `adjustCase` (View lines 394-411): for labels shorter than 3 chars, `mKeyboard.isShifted() ^ invert ? toUpperCase() : toLowerCase()`. This mutates `key.label`. So uppercase is achieved by `String.toUpperCase()` on the label, not by loading a different layout.
3. The Shift key icon swaps in `createKeyImageView` (View lines 283-297): `SHIFT_OFF -> ic_ime_shift_off`, `SHIFT_ON -> ic_ime_shift_on`, `SHIFT_LOCKED -> ic_ime_shift_lock_on`.

For the Nano native renderer: maintain one base layout, hold a `shiftState` enum, and uppercase the single-char labels when `shiftState != OFF`. Swap the shift glyph (off / up-arrow / lock) per state. There is no need to load separate uppercase layouts.

---

## 7. Voice key behavior (to stub out in a no-mic native build)

Voice is gated globally by `VOICE_SUPPORTED = true` (Container line 95) and per-field by `mVoiceEnabled` (disabled for number/phone/datetime/password/person-name/email/uri/web fields in `setImeOptions`, lines 808-839). If `!VOICE_SUPPORTED`, `mVoiceEnabled = false` (lines 899-901).

Pieces involved (all to be stubbed/omitted in a no-mic build):
- `mVoiceButtonView` is a `RecognizerView` (`R.id.voice`, recognizer_size 96dp) shown via the `VoiceIntroAnimator` cross-fade (lines 290-359; `mVoiceAnimDur = voice_anim_duration = 300 ms`, alpha in/out 100%/0%).
- `startVoiceRecording()` (lines 621-630): if `mVoiceKeyDismissesEnabled` (private IME option `voiceDismiss`), it instead dismisses (`onDismiss(true)` -> `ENTRY_TYPE_VOICE_DISMISS`); otherwise it plays the enter animation, which on `onAnimationStart` calls `startRecognition()`.
- `startRecognition()` (lines 1442-1528): builds a `SpeechRecognizer` with `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, `LANGUAGE_MODEL_FREE_FORM`, partial results on. On `onResults` it forwards `matches.get(0)` to `mVoiceListener.onVoiceResult` -> `onEntry(ENTRY_TYPE_VOICE, 0, result)` which replaces the field text.
- The Voice key itself is keycode `-7` (`KEYCODE_VOICE`); none of the shipped XML layouts (`qwerty_us`, `sym_us`, `number`) actually contain a `-7` key, so the on-keyboard voice key is effectively absent; voice is reached only via the voice button view overlay. `commitKey` `TYPE_VOICE` -> `onVoiceClick()` -> `mVoiceButtonView.onClick()`.

**Native build directive**: remove `RecognizerView`, `SpeechRecognizer`, `SpeechLevelSource`, `VoiceIntroAnimator`, `mVoiceEnterListener`/`mVoiceExitListener`, the `TYPE_VOICE` focus type and its (empty) navigation case, and the `ENTRY_TYPE_VOICE` / `ENTRY_TYPE_VOICE_DISMISS` paths. Set `mVoiceEnabled = false` permanently. No layout key needs removing since none reference `-7`.

---

## 8. Escape / dismiss / move-out-of-keyboard behaviors

### 8.1 Escape North (dismiss by going up past the suggestions)

`mEscapeNorthEnabled` is set from the field's private IME option `escapeNorth` (or legacy `EscapeNorth=1`), in `setImeOptions` (lines 856-861). When enabled:
- From the top row pressing UP (when suggestions are empty/disabled) -> `getBestFocus` hits the `else if (y < keyboardTop && mEscapeNorthEnabled)` branch (lines 984-986), returns `validFocus = false` and calls `escapeNorth()`.
- From a SUGGESTION pressing UP -> `getNextFocusInDirection` SUGGESTION/UP branch calls `escapeNorth()` (lines 1291-1294).

`escapeNorth()` (lines 1008-1011) -> `mDismissListener.onDismiss(false)` -> Controller `onDismiss(false)` (lines 576-583) -> `onEntry(ENTRY_TYPE_DISMISS)` -> service `performEditorAction(IME_ACTION_NONE)` then `requestHideSelf` path. Net: the keyboard closes and focus returns to the host UI / text field above. For the Nano port this is the "press UP at the top to leave the keyboard and go back to the field" gesture.

When `validNextFocus`/`validFocus` is false, `onDirectionalMove` (Controller lines 311-320) and `getBestFocus` callers do NOT change focus (the `if (...getNextFocusInDirection(...))` guard fails), so the move is consumed by the dismiss instead.

### 8.2 Back / cancel (gamepad B, hardware Back)

`getSimplifiedKey` maps `BUTTON_B` -> `KEYCODE_BACK` (Controller line 596). `handleKeyDownEvent` (lines 420-424) and `handleKeyUpEvent` (lines 511-514) **never trap Back**: they call `mContainer.cancelVoiceRecording()` and return false so the system handles Back (which dismisses the IME / pops the field). So **B / Back = cancel keyboard**. This is the primary "close keyboard" button in a gamepad build.

### 8.3 Enter / Done (the action button and ENTER key)

- The ACTION button (`R.id.enter`, `TYPE_ACTION`) commits `ENTRY_TYPE_ACTION` -> `sendDefaultEditorAction` (Go/Next/Search/Send/Done depending on `mEnterKeyTextResId`, chosen in `setImeOptions` lines 880-897). Its label text is set in `onStartInputView` (lines 761-767).
- Hardware `KEYCODE_ENTER` on key-UP (Controller lines 548-557): if a suggestion is focused, commit it first; then `onEntry(ENTRY_TYPE_DISMISS)`. So ENTER = commit-and-dismiss.

### 8.4 Voice dismiss

`mVoiceKeyDismissesEnabled` (private option `voiceDismiss`): pressing the voice trigger dismisses with `ENTRY_TYPE_VOICE_DISMISS` -> `performEditorAction(IME_ACTION_GO)` (service lines 330-332). Omit in no-mic build.

---

## 9. Constants and resource quick-reference (for the native port)

Numeric constants gathered:

- Movement/selector animation: `MOVEMENT_ANIMATION_DURATION = 150` ms; interpolator `DecelerateInterpolator(1.5f)` (Container 105, 110).
- Direction step: `DIRECTION_STEP_MULTIPLIER = 1.25` (Container 146). LEFT/RIGHT extra slide = `rect.height()/2` (half key).
- Click/focus scale: `focused_scale = 120%`, `clicked_scale = 88%` (config.xml 20-22). `clicked_anim_duration = 100` ms; `unfocused_anim_delay = 30` ms; `voice_anim_duration = 300` ms; `inactive_mini_kb_alpha = 40` (integers.xml).
- Double-click for caps lock: `DOUBLE_CLICK_TIMEOUT_MS = 200` (Controller 97).
- Key-change revert window (trackpad only): `KEY_CHANGE_REVERT_TIME_MS = 100`, history size 10 (Controller 56-57).
- Touch-move threshold: `resize_move_distance = 12dp`, squared in `mResizeSquareDistance` (Controller 161-162); `TOUCH_MOVE_MIN_DISTANCE = .1` cm (Container 140) - both trackpad-only.
- Physical model: `PHYSICAL_WIDTH_CM = 12`, `PHYSICAL_HEIGHT_CM = 5` (Container 170, 175) - trackpad-only, omit for D-pad.
- Geometry (dimens.xml): key 28x28dp, h-gap 12dp, v-gap 8dp, space 268dp (=7 keys + 6 gaps), selector 24dp, recognizer 96dp, action button height 32dp, key font 18sp, mode-change font 16sp.
- Layout grid: `columnCount=11`, `rowCount=5` (input_leanback.xml 39-40). 49 keys in qwerty_us.
- `getNearestIndex` magic: space collapse `index in (46,53) -> 46`; `index >= 53 -> index -= 6` (View 218-226). Tied to the 49-key layout.

Keycode constants: see Section 4.2 table. Shift states: `SHIFT_OFF=0/SHIFT_ON=1/SHIFT_LOCKED=2`. ENTRY_TYPE enum: STRING=0, BACKSPACE=1, SUGGESTION=2, LEFT=3, RIGHT=4, ACTION=5, VOICE=6, DISMISS=7, VOICE_DISMISS=8 (Controller 64-72).

---

## 10. Port guidance / risks

- **Reimplement `android.inputmethodservice.Keyboard` geometry** (row-by-row pen placement with h-gap/v-gap, edge flags, popupResId, codes, label, icon). LeanbackIME assumes this exists; it is not in this package. The de-facto spec is `frameworks/base/core/java/android/inputmethodservice/Keyboard.java`. Without it you have no `key.x/y/width/height/edgeFlags`.
- **Navigation is geometric, not a neighbor table.** Implement `getNearestIndex` (uniform 5x11 grid + space fixups) and the `getNextFocusInDirection` ray-step. Replicate `mX`/`mY` "remembered column" so vertical moves through the wide space bar feel right. Edge behavior: LEFT-at-left-edge does not wrap; RIGHT-at-right-edge jumps to the action button.
- **The `getNearestIndex` magic numbers (46, 53, 6) are layout-specific.** If you keep the exact qwerty_us 49-key layout you can reuse them; if you change the bottom-row geometry you must recompute. Safer: hit-test actual key rects for the bottom row.
- **Shift = uppercasing labels, not loading a shift layout.** `shift_*.xml` are accent popups, not uppercase layouts. Keep one layout per ABC/SYM and uppercase single-char labels per shift state.
- **Accent mini-kb overwrites in-place key slots** (no separate window). Replicate the right-align-if-overflow rule (`baseIndex = nextRow*colCount - totalAccentKeys`) and the dimming of non-mini keys to alpha 40, and the `isInvertible` case-flip on the first slot.
- **Long-press = repeatCount==1 on center.** You must implement your own long-press timer (and your own auto-repeat for held-A on Delete/Left/Right) because you are not riding Android key auto-repeat.
- **Voice is fully removable** with no layout changes (no `-7` key in shipped layouts). Stub `mVoiceEnabled=false`.
- **Text ops map cleanly to a `std::string` + caret**: STRING insert, BACKSPACE erase-left, LEFT/RIGHT caret move, SUGGESTION/VOICE replace, ACTION/DISMISS close. Keep the auto-space-after-period (`mEnterSpaceBeforeCommitting`) only if you want LatinIME parity; it is gated on `enableAutoEnterSpace()` (false for email/uri/web fields).
- **Number keyboard is wired but unused**: `mNumKeyboard` is built (`R.xml.number`) but `setImeOptions` always assigns `mAbcKeyboard` even for number/phone/datetime (lines 810-817 have a `// TODO use number keyboard` comment). So in practice only ABC and SYM are reachable via the mode-change key. number.xml is a 4-col, 4-row grid if you choose to wire it up.
