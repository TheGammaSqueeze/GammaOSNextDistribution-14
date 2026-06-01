# Leanback IME Keyboard XML Layout System - Complete Reference

Scope: this document fully describes the on-screen keyboard (OSK) data model used by the
Android TV Leanback IME, so it can be reimplemented 1:1 in the native C++ / OpenGL ES "Nano"
renderer driven by D-pad / gamepad input into a plain `std::string`.

Source tree analyzed:
`/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/`

Key files:

- Layouts: `res/xml/*.xml` (75 files: base layouts, symbol layouts, number layout, shift_* and accent_* mini-keyboards)
- Styleable attrs (Leanback's own): `res/values/attrs.xml`
- Geometry constants: `res/values/dimens.xml`, `res/values/integers.xml`, `res/values/config.xml`
- Key parsing: done by the Android framework class `android.inputmethodservice.Keyboard` / `Keyboard.Key` / `Keyboard.Row` (NOT by Leanback). Leanback only consumes the parsed objects.
- View / special-code constants: `src/com/android/inputmethod/leanback/LeanbackKeyboardView.java`
- Layout switching + shift behavior: `src/com/android/inputmethod/leanback/LeanbackKeyboardContainer.java`
- Code dispatch + gamepad mapping: `src/com/android/inputmethod/leanback/LeanbackKeyboardController.java`
- Locale -> layout mapping: `src/com/android/inputmethod/leanback/LeanbackLocales.java`

IMPORTANT PARSING FACT: The `<Keyboard>` / `<Row>` / `<Key>` XML is NOT parsed by any Leanback code.
It is parsed by the stock AOSP framework class `android.inputmethodservice.Keyboard`, constructed in
`LeanbackKeyboardContainer.initKeyboards()` via `new Keyboard(mContext, R.xml.<name>)`
(see container lines 654-704). For a native reimplementation you must replicate the framework's
`Keyboard` XML semantics yourself; they are summarized in Section 1. Leanback's own `attrs.xml`
declares only TWO keyboard-grid attributes (`rowCount`, `columnCount`) which belong to the
`LeanbackKeyboardView` VIEW, not to the `<Keyboard>` XML element.

--------------------------------------------------------------------------------

## 0. The two attribute namespaces (do not confuse them)

There are two completely separate sets of attributes:

### 0a. `LeanbackKeyboardView` styleable (from `res/values/attrs.xml`, lines 22-25)

These are XML attributes on the `<LeanbackKeyboardView>` VIEW tag inside `res/layout/root_leanback.xml`
(the view container), NOT on the keyboard data files. Only two exist:

| Attr          | Format  | Read at                              | Meaning |
|---------------|---------|--------------------------------------|---------|
| `rowCount`    | integer | `LeanbackKeyboardView.java:118`      | total rows of the keyboard grid |
| `columnCount` | integer | `LeanbackKeyboardView.java:119`      | total columns of the keyboard grid |

These drive `getNearestIndex(x,y)` (LeanbackKeyboardView.java:194-235), which maps a pixel position
to a key index by computing `row = y/height*rows`, `col = x/width*cols`, `index = colCount*row + col`,
then applying the space-key fixups described in Section 5.

The remaining attrs.xml entries (lines 27-47: `enabledBackgroundColor`, `primaryLevels`, ...,
`BitmapSoundLevelView`) are ONLY for the voice/sound-level UI and are irrelevant to typing.

### 0b. `android.inputmethodservice.Keyboard` framework attributes (the `res/xml/*.xml` schema)

These are the attributes actually used inside every `res/xml/*.xml` keyboard data file. They belong to
the AOSP framework's `R.styleable.Keyboard` / `Keyboard_Row` / `Keyboard_Key`. Section 1 documents
exactly which of these are used and with what values.

--------------------------------------------------------------------------------

## 1. The XML schema actually used in `res/xml/*.xml`

Root element is always `<Keyboard>`, containing one or more `<Row>`, each containing one or more `<Key>`.
Namespace prefix is always `android:` (`xmlns:android="http://schemas.android.com/apk/res/android"`).

### 1.1 `<Keyboard>` element attributes (the only four ever used)

Every keyboard file (base, sym, number, shift_*, accent_*) opens with the SAME four attributes
(qwerty_us.xml:17-21, identical everywhere):

```
<Keyboard xmlns:android="http://schemas.android.com/apk/res/android"
    android:keyHeight="@dimen/key_height"          (= 28dp)
    android:keyWidth="@dimen/key_width"            (= 28dp)
    android:verticalGap="@dimen/keyboard_vertical_gap"   (= 8dp)
    android:horizontalGap="@dimen/keyboard_horizontal_gap" > (= 12dp)
```

| Attribute              | Value used                | Resolved value | Meaning |
|------------------------|---------------------------|----------------|---------|
| `android:keyHeight`    | `@dimen/key_height`       | 28dp           | default height of every key/row |
| `android:keyWidth`     | `@dimen/key_width`        | 28dp           | default width of every key. In the framework this is parseable as a fraction of total keyboard width ("%p" or "%"), but here it is a fixed dp dimension. |
| `android:verticalGap`  | `@dimen/keyboard_vertical_gap` | 8dp       | gap between rows (added below each row) |
| `android:horizontalGap`| `@dimen/keyboard_horizontal_gap` | 12dp    | default gap to the LEFT of / between keys |

Framework default semantics (since these resolve to dp, not %, the framework treats them as absolute):
the framework's `Keyboard.getMinWidth()` returns the widest row; `getHeight()` is the sum of row heights
plus vertical gaps. `LeanbackKeyboardView.onMeasure()` (lines 546-560) uses
`mKeyboard.getMinWidth()` + padding for width and `mKeyboard.getHeight()` + padding for height.

NO keyboard file uses `android:keyboardMode`. (Verified: precise grep for `android:keyboardMode`
returned NONE FOUND.) So there is a single mode per file.

### 1.2 `<Row>` element attributes

Only ONE Row attribute is ever used: `android:rowEdgeFlags`.

| Attribute              | Values seen   | Count | Meaning |
|------------------------|---------------|-------|---------|
| `android:rowEdgeFlags` | `top`         | 16 occurrences | marks the first/top row (used for vertical edge detection / focus wrap) |
| `android:rowEdgeFlags` | `bottom`      | 29 occurrences | marks the last/bottom row |

Rows without `rowEdgeFlags` are interior rows. Per-row `keyWidth`/`keyHeight`/`horizontalGap`/
`verticalGap` overrides are NOT used by any file (only the `<Keyboard>`-level defaults apply, except
per-key `keyWidth` on the space key). The framework would allow them, but Leanback does not use them.

### 1.3 `<Key>` element attributes

Across ALL 75 files, exactly SEVEN key attributes appear. The full set, with format and meaning:

| Attribute               | Format / values                          | Meaning |
|-------------------------|------------------------------------------|---------|
| `android:codes`         | integer (decimal); always a single value | The key's primary key code. Leanback only ever uses `key.codes[0]` (LeanbackKeyboardContainer.java:1025,1119; LeanbackKeyboardController.java:354). For printable keys this is the Unicode code point in decimal (e.g. `97`=a, `8364`=euro). For function keys it is a NEGATIVE special code (see Section 2). |
| `android:keyLabel`      | string (literal char, or `@string/...`)  | The glyph/text drawn on the key and (for printable keys) the text committed. May be a single character, a multi-char word like "Done"/"Space" (via `@string/...`), or an escaped char (`\@`, `\#`, `\?`, `\\`, `\'`). |
| `android:keyIcon`       | drawable ref `@drawable/...`             | If present, an icon is drawn INSTEAD of the label (LeanbackKeyboardView.java:283-306). Used for delete, shift, space, left, right, symbols, alphabet keys. |
| `android:keyWidth`      | dimension ref (only `@dimen/space_key_width` = 268dp) | Per-key width override. Used ONLY on the space key (code 32). All other keys inherit 28dp. |
| `android:keyEdgeFlags`  | `left` or `right`                        | Marks a key as being on the left/right edge of its row. 114 `left` + 114 `right` occurrences. Used for D-pad horizontal navigation/wrapping (LeanbackKeyboardContainer.java:1343-1356): a RIGHT press on an `EDGE_RIGHT` key jumps to the action button; a LEFT press on an `EDGE_LEFT` key does not move x. |
| `android:popupKeyboard` | xml ref `@xml/shift_* | @xml/accent_*`    | The mini-keyboard shown on long-press of this key (LeanbackKeyboardView.java:489-521). Maps to `Keyboard.Key.popupResId`. |

ATTRIBUTES THAT ARE PART OF THE FRAMEWORK SCHEMA BUT ARE NOT USED ANYWHERE (verified by precise grep,
"NONE FOUND"): `keyOutputText`, `popupCharacters`, `isModifier`, `isSticky`, `isRepeatable`,
`keyboardMode`, `iconPreview`, `codesArray`. Notes on these unused attributes for completeness:

- `keyOutputText` (would commit a multi-char string): not used; instead multi-char output never occurs except the `@string`-labeled function keys whose codes are special.
- `popupCharacters` (a quick-string alternative to `popupKeyboard`): not used; Leanback always uses a full `popupKeyboard` xml file for popups.
- `isModifier` / `isSticky`: not used. Shift is handled in software via shift STATE (Section 4), not via a sticky modifier key.
- `isRepeatable`: not used. Auto-repeat of delete/arrows is implemented in Java (controller handles them on key-down), not via the XML flag.

### 1.4 Note on Leanback-specific handling of `key.label` / `key.icon`

- `LeanbackKeyboardView.adjustCase()` (lines 394-411): if `label.length() < 3`, it upper/lowercases the label based on shift state (XORed with mini-kb invert flag). This is how a single set of lowercase letter keys renders as uppercase when shifted - there is NO separate uppercase layout file.
- `createKeyImageView()` (lines 307-316): labels with `length() > 1` (e.g. "Done", "Mode change") are drawn at `function_key_mode_change_font_size` (16sp) with `sans-serif`; single-char labels use `key_font_size` (18sp) with `sans-serif-light`.
- Shift key icon is swapped at draw time based on shift state (lines 284-296): `ic_ime_shift_off` / `ic_ime_shift_on` / `ic_ime_shift_lock_on`.

--------------------------------------------------------------------------------

## 2. Complete set of special key CODES

These are the negative/special key codes. The framework defines the first set in
`android.inputmethodservice.Keyboard`; Leanback defines extras in `LeanbackKeyboardView`
(lines 81-90). All values cross-checked against the Java.

### 2.1 Framework constants (`android.inputmethodservice.Keyboard`)

| Constant               | Value | Appears in XML as | Behavior |
|------------------------|-------|-------------------|----------|
| `Keyboard.KEYCODE_SHIFT`       | `-1`  | `android:codes="-1"` | Toggle shift. Dispatched in controller line 374 -> `mContainer.onShiftClick()`. |
| `Keyboard.KEYCODE_MODE_CHANGE` | `-2`  | `android:codes="-2"` | Switch ABC <-> SYM keyboard. Controller line 365 -> `mContainer.onModeChangeClick()`. |
| `Keyboard.KEYCODE_CANCEL`      | `-3`  | (NOT used as -3 here; -3 is reused by Leanback as KEYCODE_LEFT) | n/a in this app |
| `Keyboard.KEYCODE_DONE`        | `-4`  | (NOT used as -4 here; -4 is reused by Leanback as KEYCODE_RIGHT) | n/a in this app |
| `Keyboard.KEYCODE_DELETE`      | `-5`  | `android:codes="-5"` | Backspace. Controller line 391 -> `ENTRY_TYPE_BACKSPACE`. |
| `Keyboard.KEYCODE_ALT`         | `-6`  | (NOT used as -6 here; -6 is reused by Leanback as KEYCODE_CAPS_LOCK) | n/a in this app |

IMPORTANT: Leanback DELIBERATELY reuses framework values -3, -4, -6 with DIFFERENT meanings (see 2.2).
In the XML, the negative codes -1, -2, -3, -4, -5 all appear (the bottom function row), plus -6/-7/-8
only as Java constants (not in XML; they are injected by the controller from gamepad buttons / long-press).

### 2.2 Leanback-specific constants (`LeanbackKeyboardView.java`, lines 81-90)

| Constant                                    | Value | In XML? | Behavior |
|---------------------------------------------|-------|---------|----------|
| `ASCII_SPACE`                               | `32`  | yes (`codes="32"`) | Commit a single space `" "`. Controller line 394 -> `ENTRY_TYPE_STRING " "` then `mContainer.onSpaceEntry()` (re-applies caps state). |
| `ASCII_PERIOD`                              | `46`  | yes (`codes="46"`, the "." key) | Commit "." then `mContainer.onPeriodEntry()` (re-applies caps state). Controller line 398. NOTE: 46 is also the plain period code point so this is just the "." key with extra shift bookkeeping. |
| `KEYCODE_SHIFT`                             | `-1`  | yes | same as framework -1; toggle shift. |
| `KEYCODE_SYM_TOGGLE`                        | `-2`  | yes | same VALUE as framework KEYCODE_MODE_CHANGE; switch ABC<->SYM. (Controller dispatches on `Keyboard.KEYCODE_MODE_CHANGE` which equals -2.) |
| `KEYCODE_LEFT`                              | `-3`  | yes | Move text cursor left. Controller line 385 -> `ENTRY_TYPE_LEFT`. |
| `KEYCODE_RIGHT`                             | `-4`  | yes | Move text cursor right. Controller line 388 -> `ENTRY_TYPE_RIGHT`. |
| `KEYCODE_DELETE`                            | `-5`  | yes | Backspace (alias of framework -5). |
| `KEYCODE_CAPS_LOCK`                         | `-6`  | NO (injected) | Toggle caps lock. Controller line 371 -> `mContainer.onShiftDoubleClick(isCapsLockOn())`. Injected from gamepad R3 (KEYCODE_BUTTON_THUMBR, controller line 546) and from shift double-click / long-press. |
| `KEYCODE_VOICE`                             | `-7`  | NO (injected) | Start voice recording. Controller line 402 -> `mContainer.startVoiceRecording()`. There is no voice KEY in the keyboard XML; voice is a separate view. |
| `KEYCODE_DISMISS_MINI_KEYBOARD`            | `-8`  | NO (injected) | Dismiss the accent/shift mini-keyboard. Controller line 382 -> `mContainer.dismissMiniKeyboard()`. |

### 2.3 Printable / positive codes

For all printable keys, `android:codes` is the Unicode code point in DECIMAL, and it equals the
committed character (the label, lower/uppercased per shift). Examples: `97`=a, `122`=z, `49`='1',
`64`='@', `8364`='euro', `8377`='rupee', `8226`='bullet', `176`='degree', `191`='inverted ?'.
The commit path for any positive code that is not 32/46 is the `default` branch
(LeanbackKeyboardController.java:406-413): `onEntry(ENTRY_TYPE_STRING, code, label)` then `onTextEntry()`.

### 2.4 The `InputListener` entry types (LeanbackKeyboardController.java:64-72)

These are what the native renderer must translate into edits of the `std::string` buffer:

| Constant                    | Value | Native action |
|-----------------------------|-------|---------------|
| `ENTRY_TYPE_STRING`         | 0     | append the committed `CharSequence` (a glyph or " ") to the buffer |
| `ENTRY_TYPE_BACKSPACE`      | 1     | delete one char before cursor |
| `ENTRY_TYPE_SUGGESTION`     | 2     | replace current word with suggestion text (suggestions are out of scope for a plain buffer) |
| `ENTRY_TYPE_LEFT`           | 3     | move cursor left |
| `ENTRY_TYPE_RIGHT`          | 4     | move cursor right |
| `ENTRY_TYPE_ACTION`         | 5     | the action/enter button (Go/Next/Search/Send/Done) was pressed -> finish input |
| `ENTRY_TYPE_VOICE`          | 6     | commit voice result string (out of scope) |
| `ENTRY_TYPE_DISMISS`        | 7     | close keyboard |
| `ENTRY_TYPE_VOICE_DISMISS`  | 8     | close keyboard from voice button |

### 2.5 Shift state machine (`LeanbackKeyboardView.java:58-60`)

| State          | Value | Meaning |
|----------------|-------|---------|
| `SHIFT_OFF`    | 0     | lowercase |
| `SHIFT_ON`     | 1     | next letter uppercase, then auto-revert to OFF after a text entry (see container `onTextEntry`) |
| `SHIFT_LOCKED` | 2     | caps lock; stays uppercase |

--------------------------------------------------------------------------------

## 3. FULL machine-usable dump of EVERY layout file

Conventions:
- "code" is `android:codes`. "label" is `android:keyLabel`.
- All keys are 28dp wide and 28dp tall EXCEPT space (code 32) which is 268dp wide.
- `[L]` = `keyEdgeFlags="left"`, `[R]` = `keyEdgeFlags="right"`. No flag = interior.
- `popup=X` is `android:popupKeyboard="@xml/X"`. `icon=X` is `android:keyIcon="@drawable/X"`.
- Row header shows `rowEdgeFlags` if present.
- Labels shown literally; framework un-escapes `\@`->@, `\#`->#, `\?`->?, `\\`->\, `\'`->'.
- `@string/...` labels resolve to: keycode_delete="Delete", keycode_space="Space",
  keycode_mode_change="Mode change", keycode_shift="Shift", keycode_left="Left", keycode_right="Right".

### 3.1 BASE ALPHA LAYOUTS

All base alpha layouts share an IDENTICAL grid skeleton (5 rows, 11/11/11/11/5 keys = 49 keys total).
The full canonical layout is `qwerty_us.xml`; every other base layout is described as a DELTA from it.
The space row is always: `[-2 mode/symbols][-1 shift][32 space, 268dp][-3 left][-4 right]`.

#### qwerty_us.xml (CANONICAL - full dump)

Row 1 (rowEdgeFlags=top), 11 keys:
| pos | code | label | edge | popup | icon |
|-----|------|-------|------|-------|------|
| 1 | 49 | 1 | [L] | shift_1 | - |
| 2 | 50 | 2 | - | shift_2 | - |
| 3 | 51 | 3 | - | shift_3 | - |
| 4 | 52 | 4 | - | shift_4 | - |
| 5 | 53 | 5 | - | shift_5 | - |
| 6 | 54 | 6 | - | shift_6 | - |
| 7 | 55 | 7 | - | shift_7 | - |
| 8 | 56 | 8 | - | shift_8 | - |
| 9 | 57 | 9 | - | shift_9 | - |
| 10 | 48 | 0 | - | shift_0 | - |
| 11 | -5 | Delete | [R] | - | ic_ime_delete |

Row 2, 11 keys:
| pos | code | label | edge | popup |
|-----|------|-------|------|-------|
| 1 | 113 | q | [L] | shift_q |
| 2 | 119 | w | - | shift_w |
| 3 | 101 | e | - | accent_e |
| 4 | 114 | r | - | shift_r |
| 5 | 116 | t | - | accent_t |
| 6 | 121 | y | - | accent_y |
| 7 | 117 | u | - | accent_u |
| 8 | 105 | i | - | accent_i |
| 9 | 111 | o | - | accent_o |
| 10 | 112 | p | - | shift_p |
| 11 | 64 | @ | [R] | - |

Row 3, 11 keys:
| pos | code | label | edge | popup |
|-----|------|-------|------|-------|
| 1 | 97 | a | [L] | accent_a |
| 2 | 115 | s | - | accent_s |
| 3 | 100 | d | - | accent_d |
| 4 | 102 | f | - | shift_f |
| 5 | 103 | g | - | accent_g |
| 6 | 104 | h | - | shift_h |
| 7 | 106 | j | - | shift_j |
| 8 | 107 | k | - | accent_k |
| 9 | 108 | l | - | accent_l |
| 10 | 95 | _ | - | - |
| 11 | 38 | & | [R] | - |

Row 4, 11 keys:
| pos | code | label | edge | popup |
|-----|------|-------|------|-------|
| 1 | 122 | z | [L] | accent_z |
| 2 | 120 | x | - | shift_x |
| 3 | 99 | c | - | accent_c |
| 4 | 118 | v | - | shift_v |
| 5 | 98 | b | - | shift_b |
| 6 | 110 | n | - | accent_n |
| 7 | 109 | m | - | shift_m |
| 8 | 44 | , | - | - |
| 9 | 46 | . | - | - |
| 10 | 45 | - | - | - |
| 11 | 63 | ? | [R] | - |

Row 5 (rowEdgeFlags=bottom), 5 keys (the FUNCTION/space row):
| pos | code | label | edge | icon | width |
|-----|------|-------|------|------|-------|
| 1 | -2 | Mode change | [L] | ic_ime_symbols | 28dp |
| 2 | -1 | Shift | - | ic_ime_shift_off | 28dp |
| 3 | 32 | Space | - | ic_ime_space | 268dp |
| 4 | -3 | Left | - | ic_ime_left_arrow | 28dp |
| 5 | -4 | Right | [R] | ic_ime_right_arrow | 28dp |

Note: this Row 5 is BYTE-IDENTICAL across all base alpha layouts AND across all sym_* layouts
(except sym_* use icon `ic_ime_alphabet` instead of `ic_ime_symbols` on the -2 key).

#### qwerty_en_gb.xml = qwerty_us EXCEPT:
- Row1 key4 (code 52, "4"): popup `shift_4` -> `shift_4_en_gb` (pound currency).
- Row2 key6 (code 121, "y"): popup `accent_y` -> `shift_y`.

#### qwerty_en_in.xml = qwerty_us EXCEPT:
- Row1 key4 (code 52): popup `shift_4` -> `shift_4_en_in` (rupee).
- Row2 key6 (code 121): popup `accent_y` -> `shift_y`.

#### qwerty_eu.xml = qwerty_us EXCEPT:
- Row1 key4 (code 52): popup `shift_4` -> `shift_4_eu` (euro).
- Row2 key6 (code 121): popup `accent_y` -> `shift_y`.
(This is the DEFAULT fallback layout for any locale not explicitly matched; see container line 700.)

#### qwerty_es_eu.xml = qwerty_us EXCEPT:
- Row1 key4 (code 52): popup `shift_4` -> `shift_4_eu`.
- Row2 key6 (code 121): popup `accent_y` -> `shift_y`.
- Row3 key10: REPLACED. Was `95 "_"` (no popup) -> now `241 "ñ"` with popup `shift_nn_es`.
- Row4 key6 (code 110, "n"): popup `accent_n` -> `shift_n_es`.

#### qwerty_es_us.xml = qwerty_us EXCEPT:
- Row2 key6 (code 121): popup `accent_y` -> `shift_y`. (Row1 key4 keeps `shift_4` = dollar.)
- Row3 key10: REPLACED `95 "_"` -> `241 "ñ"` popup `shift_nn_es`.
- Row4 key6 (code 110): popup `accent_n` -> `shift_n_es`.

#### qwerty_az.xml = qwerty_us EXCEPT:
- Row1 key4: popup `shift_4` -> `shift_4_eu`.
- Row2 key6: popup `accent_y` -> `shift_y`.

#### qwerty_ca.xml = qwerty_us EXCEPT:
- Row1 key4: popup `shift_4` -> `shift_4_eu`.
- Row2 key6: popup `accent_y` -> `shift_y`.
- Row3 key10: REPLACED `95 "_"` -> `231 "ç"` (lowercase, U+00E7) popup `shift_cc`.

#### qwerty_da.xml (Danish) = qwerty_us EXCEPT:
- Row1 key4: popup `shift_4` -> `shift_4_eu`.
- Row2 key6: popup `accent_y` -> `shift_y`.
- Row2 key11: REPLACED `64 "@"` (no popup) -> `229 "å"` popup `shift_ao`.
- Row3 key10: REPLACED `95 "_"` -> `230 "æ"` popup `shift_ae`.
- Row3 key11: REPLACED `38 "&"` -> `248 "ø"` popup `shift_ox`.

#### qwerty_et.xml (Estonian) = qwerty_us EXCEPT:
- Row1 key4: popup `shift_4_eu`. Row2 key6: popup `shift_y`.
- Row2 key11: REPLACED `64 "@"` -> `252 "ü"` popup `shift_uu`.
- Row3 key10: REPLACED `95 "_"` -> `246 "ö"` popup `shift_oo`.
- Row3 key11: REPLACED `38 "&"` -> `228 "ä"` popup `shift_aa`.

#### qwerty_fi.xml (Finnish) = qwerty_us EXCEPT:
- Row1 key4: popup `shift_4_eu`. Row2 key6: popup `shift_y`.
- Row2 key11: REPLACED `64 "@"` -> `229 "å"` popup `shift_ao`.
- Row3 key10: REPLACED `95 "_"` -> `246 "ö"` popup `shift_oo`.
- Row3 key11: REPLACED `38 "&"` -> `228 "ä"` popup `shift_aa`.

#### qwerty_nb.xml (Norwegian Bokmal) = qwerty_us EXCEPT:
- Row2 key6: popup `accent_y` -> `shift_y`. (Row1 key4 keeps `shift_4` = dollar - nb uses US symbols, container line 681-683.)
- Row2 key11: REPLACED `64 "@"` -> `229 "å"` popup `shift_ao`.
- Row3 key10: REPLACED `95 "_"` -> `248 "ø"` popup `shift_ox`.
- Row3 key11: REPLACED `38 "&"` -> `230 "æ"` popup `shift_ae`.

#### qwerty_sv.xml (Swedish) = qwerty_us EXCEPT:
- Row1 key4: popup `shift_4_eu`. Row2 key6: popup `shift_y`.
- Row2 key11: REPLACED `64 "@"` -> `229 "å"` popup `shift_ao`.
- Row3 key10: REPLACED `95 "_"` -> `246 "ö"` popup `shift_oo`.
- Row3 key11: REPLACED `38 "&"` -> `228 "ä"` popup `shift_aa`.

#### qwertz.xml (German etc.) = qwerty_us EXCEPT (Y/Z swap):
- Row1 key4: popup `shift_4` -> `shift_4_eu`.
- Row2 key6: code `121 "y"` (popup accent_y) -> REPLACED with code `122 "z"` popup `accent_z`.
- Row4 key1: code `122 "z"` (popup accent_z) -> REPLACED with code `121 "y"` popup `shift_y`.
(Net effect: Z is in the top letter row position 6, Y is in the bottom letter row position 1.)

#### qwertz_ch.xml (Swiss German/Italian) = qwertz EXCEPT additionally:
- Row1 key4: popup `shift_4_eu`.
- Row2 key6: `121 "y"` -> `122 "z"` popup `accent_z` (same Y/Z swap as qwertz).
- Row2 key11: REPLACED `64 "@"` -> `252 "ü"` popup `shift_uu`.
- Row3 key10: REPLACED `95 "_"` -> `246 "ö"` popup `shift_oo`.
- Row3 key11: REPLACED `38 "&"` -> `228 "ä"` popup `shift_aa`.
- Row4 key1: `122 "z"` -> `121 "y"` popup `shift_y`.

#### azerty.xml (French, Belgian Dutch) = qwerty_us EXCEPT:
- Row1 key4: popup `shift_4` -> `shift_4_eu`.
- Row2 keys 1-2: were `113 "q"` (popup shift_q), `119 "w"` (popup shift_w).
  REPLACED with `97 "a"` (popup accent_a), `122 "z"` (popup accent_z).
- Row2 key6 (code 121 "y"): popup `accent_y` -> `shift_y`.
- Row3 key1: was `97 "a"` (popup accent_a) -> REPLACED with `113 "q"` popup `shift_q`.
- Row3 key10: was `95 "_"` (no popup) -> REPLACED with `109 "m"` popup `shift_m`.
- Row4 key2: was `120 "x"` ... actually Row4 key1 stays; the diff shows Row4 letter `122 "z"`(accent_z)
  -> `119 "w"` (popup shift_w), and the former `109 "m"` (shift_m) slot in Row4 -> `39 "'"` (apostrophe, no popup).
  Net AZERTY top letter row: a z e r t y u i o p; home row: q s d f g h j k l m; bottom row: w x c v b n ' , . - ?
  (The Q and A are swapped vs qwerty, W and Z move, M moves to the home row end, and the bottom row gains an apostrophe.)

NOTE for the implementer: the azerty diff is the most structurally different. The exact final AZERTY
key list (codes,label) per row, reconstructed from `qwerty_us` + the azerty diff:
- Row1 (top): 49"1" 50"2" 51"3" 52"4"(shift_4_eu) 53"5" 54"6" 55"7" 56"8" 57"9" 48"0" -5 Delete
- Row2: 97"a"(accent_a)[L] 122"z"(accent_z) 101"e"(accent_e) 114"r"(shift_r) 116"t"(accent_t) 121"y"(shift_y) 117"u"(accent_u) 105"i"(accent_i) 111"o"(accent_o) 112"p"(shift_p) 64"@"[R]
- Row3: 113"q"(shift_q)[L] 115"s"(accent_s) 100"d"(accent_d) 102"f"(shift_f) 103"g"(accent_g) 104"h"(shift_h) 106"j"(shift_j) 107"k"(accent_k) 108"l"(accent_l) 109"m"(shift_m) 38"&"[R]
- Row4: 119"w"(shift_w)[L] 120"x"(shift_x) 99"c"(accent_c) 118"v"(shift_v) 98"b"(shift_b) 110"n"(accent_n) 39"'" 44"," 46"." 45"-" 63"?"[R]
- Row5: identical function/space row.

### 3.2 SYMBOL LAYOUTS (sym_*)

Reached by pressing the `-2` (Mode change) key on a base layout. 5 rows (11/11/11/11/5 = 49 keys).
The function row (Row 5) is identical to base layouts EXCEPT the `-2` key uses
`icon=ic_ime_alphabet` (so it reads as "back to ABC") instead of `ic_ime_symbols`.
NO sym_* key has a `popupKeyboard`.

#### sym_us.xml (CANONICAL symbol layout - full dump)

Row 1 (11 keys): 33"!"[L]  64"@"  35"#"  36"$"  37"%"  94"^"  38"&"  42"*"  40"("  41")"  -5 Delete[R] icon=ic_ime_delete

Row 2 (11 keys): 126"~"[L]  96"`"  91"["  93"]"  123"{"  125"}"  124"|"  43"+"  61"="  47"/"  92"\"[R]

Row 3 (11 keys): 176"°"[L]  191"¿"  161"¡"  247"÷"  215"×"  59";"  58":"  34"\""  39"'"  95"_"  38"&"[R]

Row 4 (rowEdgeFlags=bottom, 11 keys): 8364"€"[L]  165"¥"  162"¢"  163"£"  60"<"  62">"  8226"•"  44","  46"."  45"-"  63"?"[R]

Row 5 (rowEdgeFlags=bottom, function row, 5 keys):
| code | label | edge | icon | width |
|------|-------|------|------|-------|
| -2 | Mode change | [L] | ic_ime_alphabet | 28dp |
| -1 | Shift | - | ic_ime_shift_off | 28dp |
| 32 | Space | - | ic_ime_space | 268dp |
| -3 | Left | - | ic_ime_left_arrow | 28dp |
| -4 | Right | [R] | ic_ime_right_arrow | 28dp |

#### sym_eu.xml = sym_us EXCEPT:
- Row1 key4: `36 "$"` -> `8364 "€"` (euro promoted to the $ slot).
- Row4 key1: `8364 "€"` -> `36 "$"` (dollar demoted to the euro slot). (Swap of $ and euro positions.)

#### sym_en_gb.xml = sym_us EXCEPT:
- Row1 key4: `36 "$"` -> `163 "£"` (pound).
- Row4 key4: `163 "£"` -> `36 "$"`. (Swap of $ and pound positions.)

#### sym_en_in.xml = sym_us EXCEPT:
- Row1 key4: `36 "$"` -> `8377 "₹"` (rupee).
- Row4 key2: `165 "¥"` -> `36 "$"`. (Rupee takes $ slot; $ takes the yen slot.)

#### sym_fr.xml = sym_us EXCEPT:
- Row1 key4: `36 "$"` -> `8364 "€"`.
- Row3 key9: `39 "'"` -> `8226 "•"` (bullet).
- Row4 key1: `8364 "€"` -> `36 "$"`.
- Row4 key7: `8226 "•"` -> `39 "'"` (apostrophe). (Net: euro in $ slot, $ in euro slot, bullet/apostrophe swapped between row3 and row4.)

#### sym_azerty.xml = sym_us with EXACT SAME diffs as sym_fr (euro<->$, bullet<->apostrophe). Byte-identical changes to sym_fr.

### 3.3 NUMBER LAYOUT (number.xml)

A compact 4x4 numeric pad (4 rows of 4 keys = 16 keys). NOTE: this layout is built
(`mNumKeyboard = new Keyboard(mContext, R.xml.number)`, container line 704) but `setImeOptions`
currently always sets `mInitialMainKeyboard = mAbcKeyboard` even for NUMBER/PHONE/DATETIME input
classes (container lines 810-817, with a `// TODO use number keyboard` comment). So in the SHIPPING
behavior the number keyboard is never shown. It is documented here for completeness.

Row 1: 49"1"[L]  50"2"  51"3"  95"_"[R]
Row 2: 52"4"[L]  53"5"  54"6"  46"."[R]
Row 3: 55"7"[L]  56"8"  57"9"  -5 Delete[R] icon=ic_ime_delete
Row 4 (rowEdgeFlags=bottom): 42"*"[L]  48"0"  35"#"  63"?"[R]

No shift/mode/space keys; only ONE special code (`-5` delete).

### 3.4 SHIFT MINI-KEYBOARDS (shift_*) - popups for number/symbol/letter long-press

Each `shift_N` is the long-press popup for the digit `N` on the top row of base layouts (it shows the
SHIFTED symbol of that digit, US keyboard convention). Each `shift_<letter>` popup shows the accented/
alternate form, or just the same letter when there is no alternate. All are SINGLE-ROW, SINGLE-KEY
(except none have multiple keys). All inherit the standard `<Keyboard>` header (28dp keys, 12dp/8dp gaps).
No `keyEdgeFlags` (position/edge are inherited from the base key at long-press time, see
LeanbackKeyboardView.java:508-516).

Digit popups (long-press a top-row digit):
| file | code | label |
|------|------|-------|
| shift_0 | 41 | ) |
| shift_1 | 33 | ! |
| shift_2 | 64 | @ |
| shift_3 | 35 | # |
| shift_4 | 36 | $ |
| shift_5 | 37 | % |
| shift_6 | 94 | ^ |
| shift_7 | 38 | & |
| shift_8 | 42 | * |
| shift_9 | 40 | ( |
| shift_4_en_gb | 163 | £ |
| shift_4_en_in | 8377 | ₹ |
| shift_4_eu | 8364 | € |

Letter / accent popups (single alternate-letter popups):
| file | code | label | notes |
|------|------|-------|-------|
| shift_aa | 228 | ä | |
| shift_ae | 230 | æ | |
| shift_ao | 229 | å | |
| shift_b | 98 | b | same letter (no alternate) |
| shift_cc | 231 | ç | lowercase (U+00E7) |
| shift_f | 102 | f | same letter |
| shift_h | 104 | h | same letter |
| shift_j | 106 | j | same letter |
| shift_m | 109 | m | same letter |
| shift_n_es | 110 | n | same letter (Spanish n, paired with separate ñ key) |
| shift_nn_es | 241 | ñ | |
| shift_oo | 246 | ö | |
| shift_ox | 248 | ø | |
| shift_p | 112 | p | same letter |
| shift_q | 113 | q | same letter |
| shift_r | 114 | r | same letter |
| shift_uu | 252 | ü | |
| shift_v | 118 | v | same letter |
| shift_w | 119 | w | same letter |
| shift_x | 120 | x | same letter |
| shift_y | 121 | y | same letter |

(The "same letter" popups exist so that EVERY base letter key has a non-null `popupResId`; on long-press
they show a 1-key mini-kb of just that letter. They appear to be placeholders / give a consistent
long-press affordance. The `isInvertible` flag on the first mini-kb key (LeanbackKeyboardView.java:516)
makes that first key follow the OPPOSITE case of the main keyboard's shift state via `adjustCase`,
so long-pressing a lowercase letter shows its UPPERCASE in the first popup cell.)

### 3.5 ACCENT MINI-KEYBOARDS (accent_*) - multi-glyph popups

Each `accent_<letter>` is a single-row popup whose FIRST key is the base letter and the rest are accented
variants. Full code/label lists (all 28dp keys, single row, no edge flags):

| file | keys (code:label, in order) |
|------|------------------------------|
| accent_a | 97:a  224:à  225:á  226:â  227:ã  228:ä  229:å  230:æ  257:ā  259:ă  261:ą |
| accent_c | 99:c  231:Ç  263:ć  269:č   (NOTE: first variant 231 is UPPERCASE "Ç" U+00C7 in THIS file, a known AOSP quirk; the standalone shift_cc.xml and the qwerty_ca base key both use lowercase ç U+00E7) |
| accent_d | 100:d  273:đ |
| accent_e | 101:e  232:è  233:é  234:ê  235:ë  275:ē  279:ė  281:ę |
| accent_g | 103:g  287:ğ  291:ģ |
| accent_i | 105:i  236:ì  237:í  238:î  239:ï  299:ī  303:į |
| accent_k | 107:k  311:ķ |
| accent_l | 108:l  316:ļ  322:ł |
| accent_n | 110:n  241:ñ  324:ń  326:ņ |
| accent_o | 111:o  242:ò  243:ó  244:ô  245:õ  246:ö  248:ø  333:ō  339:œ  417:ơ |
| accent_s | 115:s  223:ß  347:ś  351:ş  353:š |
| accent_t | 116:t  355:ț |
| accent_u | 117:u  249:ù  250:ú  251:û  252:ü  363:ū  371:ų  432:ư |
| accent_y | 121:y  255:ÿ |
| accent_z | 122:z  378:ź  380:ż  382:ž |

These accent popups are the multi-key mini-keyboards; the D-pad navigates among the cells and DPAD_CENTER
commits the selected accented char (with the first cell case-inverted per shift state).

--------------------------------------------------------------------------------

## 4. Layout-switching graph

There are only THREE switchable main keyboards held in the container:
`mAbcKeyboard`, `mSymKeyboard`, `mNumKeyboard` (LeanbackKeyboardContainer.java:425-427), chosen at
construction by locale (`initKeyboards()`, lines 650-705).

### 4.1 The mode-change toggle (ABC <-> SYM)

Pressing the `-2` Mode-change key fires `onModeChangeClick()` (container lines 1181-1188):

```
if (current keyboard == mSymKeyboard) setKeyboard(mAbcKeyboard);
else                                  setKeyboard(mSymKeyboard);
```

So the `-2` key is a pure TOGGLE between the two file-backed keyboards:
- On a base alpha layout, `-2` (labeled with `ic_ime_symbols`) -> the matching sym layout.
- On a sym layout, `-2` (labeled with `ic_ime_alphabet`) -> back to the base alpha layout.

The ABC<->SYM pairing is fixed per locale at init time (container lines 653-702). Pairs actually wired:

| Locale group (LeanbackLocales) | ABC layout (R.xml)   | SYM layout (R.xml) |
|--------------------------------|----------------------|--------------------|
| QWERTY_GB (en_GB)              | qwerty_en_gb         | sym_en_gb          |
| QWERTY_IN (en_IN)              | qwerty_en_in         | sym_en_in          |
| QWERTY_ES_EU (es_ES, gl_ES, eu_ES) | qwerty_es_eu     | sym_eu             |
| QWERTY_ES_US (es_*)            | qwerty_es_us         | sym_us             |
| QWERTY_AZ (az)                 | qwerty_az            | sym_eu             |
| QWERTY_CA (ca)                 | qwerty_ca            | sym_eu             |
| QWERTY_DA (da)                 | qwerty_da            | sym_eu             |
| QWERTY_ET (et)                 | qwerty_et            | sym_eu             |
| QWERTY_FI (fi)                 | qwerty_fi            | sym_eu             |
| QWERTY_NB (nb)                 | qwerty_nb            | sym_us  (US symbols by comment, line 681) |
| QWERTY_SV (sv)                 | qwerty_sv            | sym_eu             |
| QWERTY_US (en, fr_CA)          | qwerty_us            | sym_us             |
| QWERTZ_CH (de_CH, it_CH)       | qwertz_ch            | sym_eu             |
| QWERTZ (de, hr, cs, fr_CH, it_CH, hu, sr, sl, sq) | qwertz | sym_eu          |
| AZERTY (fr, nl_BE)             | azerty               | sym_azerty         |
| (any other locale, fallback)  | qwerty_eu            | sym_eu             |

`mNumKeyboard = number` is constructed but never installed as `mInitialMainKeyboard` in the current code
(Section 3.3). There is NO key code that switches to the number keyboard.

### 4.2 Shift: NO layout file switch (in-place re-render)

The shift key (`-1`) does NOT swap layout files. `onShiftClick()` (container lines 1190-1193) toggles
`SHIFT_OFF <-> SHIFT_ON`; `setShiftState()` (view lines 413-428) calls `mKeyboard.setShifted(...)`
then `invalidateAllKeys()`, which re-renders the SAME keyboard with `adjustCase()` upper/lowercasing
each single-char label (view lines 394-411). So there are no `qwerty_us_upper.xml` files; uppercase is
produced by software case-folding the label string. This is critical for the native port: keep ONE set
of lowercase letter keys, render uppercase when `shiftState != SHIFT_OFF`.

Shift auto-revert (container `onTextEntry`, lines 1195-1210): after committing a normal character in
SHIFT_ON, shift drops to SHIFT_OFF (unless caps lock or cap-characters auto-cap is in effect).
`onSpaceEntry` / `onPeriodEntry` re-apply auto-cap based on `mCapWords`/`mCapSentences`.

Caps lock: shift double-click (controller `DoubleClickDetector`, lines 96-115, 200ms window) or shift
long-press (container `onKeyLongPress` line 1535) -> `onShiftDoubleClick` -> SHIFT_LOCKED / SHIFT_OFF.

### 4.3 Mini-keyboard (popup) entry/exit

- Long-press DPAD_CENTER on a key with a `popupKeyboard` -> `LeanbackKeyboardView.onKeyLongPress()`
  (lines 489-521) inflates `new Keyboard(context, popupResId)`, overlays its keys onto the main grid
  starting at the focused key index (right-aligned if it would overflow the row, lines 498-504), and sets
  `mMiniKeyboardOnScreen = true`.
- The accent/shift cells become focusable; DPAD navigates them; DPAD_CENTER commits the selected glyph.
- Exit: committing any cell, focusing a non-mini key, or code `-8`
  (`KEYCODE_DISMISS_MINI_KEYBOARD`) calls `dismissMiniKeyboard()` (view lines 530-539) restoring the base
  keys. After commit, focus returns to the original key index (container `onTextEntry` lines 1207-1209).

### 4.4 Full switching graph summary (textual)

```
                 press -2 (Mode change)
   [base alpha] <----------------------> [sym for locale]
        |  ^                                   |  ^
        |  | press -1 (shift): in-place        |  | press -1 (shift): in-place
        |  |  case re-render, NO file switch   |  |  case re-render
        v  |                                   v  |
   (SHIFT_ON/LOCKED render same file)     (sym has shift key but symbols
                                           generally not case-folded)

   long-press a key with popupKeyboard:
   [base/sym key] --------> [overlay shift_*/accent_* mini-kb] --(commit or -8)--> back
```

--------------------------------------------------------------------------------

## 5. Default key geometry

From `res/values/dimens.xml` and the `<Keyboard>` headers:

| Property                | Resource                          | Value | Notes |
|-------------------------|-----------------------------------|-------|-------|
| default key width       | `@dimen/key_width`                | 28dp  | every key except space |
| default key height      | `@dimen/key_height`               | 28dp  | every key/row |
| space key width         | `@dimen/space_key_width`          | 268dp | = 7*28 + 6*12 = 196 + 72 = 268 (7 key-widths + 6 horizontal gaps). Comment in dimens.xml:24 confirms "7x regular key + 6*horz_margin". |
| horizontal gap          | `@dimen/keyboard_horizontal_gap`  | 12dp  | gap to the left of / between keys |
| vertical gap            | `@dimen/keyboard_vertical_gap`    | 8dp   | gap below each row |
| key font size           | `@dimen/key_font_size`            | 18sp  | single-char labels (sans-serif-light) |
| function-key font size  | `@dimen/function_key_mode_change_font_size` | 16sp | multi-char labels like "Done" (sans-serif) |
| selector size           | `@dimen/selector_size`            | 24dp  | the focus cursor base size |
| recognizer size         | `@dimen/recognizer_size`          | 96dp  | voice button (out of scope) |

Other relevant constants:
- `res/values/integers.xml`: `clicked_anim_duration`=100, `unfocused_anim_delay`=30,
  `voice_anim_duration`=300, `inactive_mini_kb_alpha`=40 (out-of-mini-kb keys dimmed to alpha 40/255).
- `res/values/config.xml` fractions: `clicked_scale`=88% (key shrinks to 0.88 on click),
  `focused_scale`=120% (selector over-estimates focused key by 1.2x, also used as `mOverestimate`),
  `alpha_in`=100%, `alpha_out`=0%.

### 5.1 Row width totals and alignment

Each full alpha/symbol row has 11 keys: total content width = 11*28 + 10*12 (interior gaps) plus the
leading 12dp = depends on framework gap accounting. The framework lays keys left-to-right: x starts at 0
(or after `horizontalGap`), each key advances by `width + horizontalGap`. Rows 1-4 (11 keys) are the
WIDEST rows and define `getMinWidth()`. Row 5 (function row) total = 28 + 28 + 268 + 28 + 28 plus 4
interior gaps = 380 + 48 = 428; the 11-key rows = 11*28 + 10*12 = 308 + 120 = 428. So BY DESIGN the
space row width (428) exactly equals an 11-key row width (428): every row fills 100% of the keyboard
width. There is NO partial-row alignment needed - all rows are the same total width. (This is also why
`getNearestIndex` can treat the grid as 5 rows x 11 columns; see below.)

### 5.2 Rows that do not fill 100% (number.xml)

`number.xml` has 4 keys per row (all 28dp) = 4*28 + 3*12 = 112 + 36 = 148 wide; all four rows are equal,
so again every row fills the keyboard width and no special alignment is required. The framework
left-aligns keys and there is no centering attribute used.

### 5.3 Space-key index fixup (grid model)

Because `LeanbackKeyboardView` models the keyboard as a uniform `rowCount` x `columnCount` grid for
`getNearestIndex` but the space key spans 7 columns, the code special-cases space
(LeanbackKeyboardView.java:218-226): a grid index in the open interval (46, 53) snaps to 46 (the space
key), and any index >= 53 is decremented by 6 to skip the 6 phantom space columns. This assumes the
standard 5x?? grid where the function row is row 5 and space starts at the 3rd column of that row.
`rowCount`/`columnCount` for the main view come from the `<LeanbackKeyboardView>` tag attrs in
`res/layout/root_leanback.xml` (not in the data files); from the index arithmetic the implied grid is
12 columns wide x 5 rows (so the space key occupies columns 3-9 of row 5, indices 46-52, with
index 46 = the function row's 3rd slot = `5*? ...`; concretely `mColCount = 12`).

--------------------------------------------------------------------------------

## 6. Gamepad / D-pad input mapping (for the native driver)

From `LeanbackKeyboardController.handleKeyDownEvent` / `handleKeyUpEvent` (lines 417-563) and
`getSimplifiedKey` (lines 589-599). This is exactly how hardware buttons map to keyboard actions and is
directly portable:

| Android KeyEvent                                   | Mapped to | Action |
|----------------------------------------------------|-----------|--------|
| DPAD_LEFT / RIGHT / UP / DOWN                       | directional move | move focus (handled on key DOWN to allow repeat) |
| DPAD_CENTER, NUMPAD_ENTER, BUTTON_A                 | "enter key" (DPAD_CENTER) | commit focused key; long-press (repeatCount==1) opens popup / caps-lock |
| BUTTON_B                                            | BACK | cancels voice; never trapped (returns false) |
| BUTTON_X                                            | commit code -5  | DELETE / backspace (on key down) |
| BUTTON_Y                                            | commit code 32  | SPACE (on key down) |
| BUTTON_L1                                           | commit code -3  | cursor LEFT (on key down) |
| BUTTON_R1                                           | commit code -4  | cursor RIGHT (on key down) |
| BUTTON_THUMBL (L3)                                  | commit code -2  | Mode change ABC<->SYM (on key UP) |
| BUTTON_THUMBR (R3)                                  | commit code -6  | Caps lock toggle (on key UP) |
| ENTER                                               | dismiss | commit suggestion if focused, then ENTRY_TYPE_DISMISS |
| BACK                                                | not trapped | passes through to dismiss IME |

`getSimplifiedKey` folds {DPAD_CENTER, NUMPAD_ENTER, BUTTON_A} -> DPAD_CENTER, and BUTTON_B -> BACK.
Keys handled on DOWN (so they auto-repeat): DELETE(-5), LEFT(-3), RIGHT(-4)
(`isKeyHandledOnKeyDown`, lines 497-501). Others commit on UP.

--------------------------------------------------------------------------------

## 7. Reimplementation checklist (concrete)

1. Data model: parse each `res/xml/*.xml` into rows of keys with fields {code:int, label:utf8 string,
   icon:enum, width:dp (28 default / 268 for space), edgeFlags:{left,right}, popupRef:filename}.
   You can hardcode the 75 files since they are static; Section 3 has every key.
2. Keep ONE lowercase letter set; render uppercase when shiftState != OFF via Unicode upper-casing of
   single-char (length<3) labels only. Do NOT upper-case multi-char/function labels.
3. Geometry: 28dp keys, 12dp horizontal gap, 8dp vertical gap, 268dp space; all rows are 428dp wide so no
   centering. Scale dp to your panel.
4. Special codes: -1 shift toggle, -2 ABC/SYM toggle, -3 cursor-left, -4 cursor-right, -5 backspace,
   -6 caps-lock, -7 voice(skip), -8 dismiss-popup, 32 space, others = Unicode code point to append.
5. Layout selection: pick ABC+SYM pair by locale per the table in Section 4.1 (default qwerty_eu/sym_eu).
6. Popups: on long-press, overlay the referenced shift_*/accent_* keys onto the grid starting at the
   pressed key, right-aligned if overflow; DPAD navigates, center commits, first cell is case-inverted.
7. Input driver: map gamepad buttons per Section 6; emit ENTRY_TYPE_STRING/BACKSPACE/LEFT/RIGHT/ACTION/
   DISMISS into your `std::string` (ACTION = finish, DISMISS = close).

--------------------------------------------------------------------------------

## 8. Quirks / gotchas to preserve or knowingly skip

- accent_c.xml's first accent cell uses UPPERCASE "Ç" (U+00C7) while shift_cc.xml and the qwerty_ca base
  key use lowercase "ç" (U+00E7). This is an upstream AOSP inconsistency. Reproduce or normalize as desired.
- The "same letter" shift_* popups (shift_b, shift_f, shift_h, shift_j, shift_m, shift_p, shift_q,
  shift_r, shift_v, shift_w, shift_x, shift_y, shift_n_es) exist only so every letter key has a popup;
  the popup just shows that letter (case-inverted in cell 0). Low value for a plain-buffer port; can skip.
- number.xml is built but never shown (TODO in setImeOptions). Number/phone/datetime fields currently use
  the ABC keyboard.
- Voice (KEYCODE_VOICE -7) and suggestions (ENTRY_TYPE_SUGGESTION) are separate UI surfaces, not keyboard
  keys; out of scope for a plain std::string typing buffer.
- No `keyOutputText`/`popupCharacters`/`isModifier`/`isSticky`/`isRepeatable`/`keyboardMode` anywhere -
  the schema is minimal. Auto-repeat and modifier behavior are all in Java, not XML.
