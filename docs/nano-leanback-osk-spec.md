# GammaOS Nano: Leanback On-Screen Keyboard - Master Implementation Spec

Status: implementation blueprint. Synthesized from the six OSK analysis reports
(`docs/osk-analysis/01-layouts.md` through `06-nano-render-input.md`).

This document is the single authoritative plan for replacing the current crude
5x10 grid OSK in the GammaOS Nano binary with a native C++ / OpenGL ES
reimplementation of the Android TV Leanback on-screen keyboard, driven by
D-pad / gamepad input into a plain `std::string`. No Android `InputConnection`,
no IME, no touch. All file paths are absolute. No em-dashes or en-dashes are
used anywhere; only ASCII hyphen.

--------------------------------------------------------------------------------

## 1. Goal, scope, and fidelity target

### 1.1 Goal

Replace the existing fixed 5x10 ASCII grid (`kOskLayout` in
`/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenuUtils.h`)
with a faithful native reimplementation of the Leanback IME keyboard found at
`/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/`, while
preserving every caller contract the rest of the Nano binary depends on
(search OSK, Wi-Fi password OSK, settings text-edit OSK).

### 1.2 Fidelity target: "1:1 with Leanback"

"1:1" means matching the Leanback keyboard at the level of:

- Layout topology: the exact 5-row, 11/11/11/11/5-key grid (49 keys) with the
  same key codes, labels, edge flags, and the 7-wide space bar.
- Per-language layout selection: the exact `LeanbackKeyboardContainer.initKeyboards()`
  first-match-wins locale chain (16 ABC layouts, 5 used SYM layouts, default
  qwerty_eu / sym_eu).
- Navigation feel: geometric nearest-in-direction D-pad navigation (NOT a
  neighbor table), with LEFT-at-left-edge not wrapping, RIGHT-at-right-edge
  going to the action button, remembered-column behavior across the space bar.
- Shift state machine: SHIFT_OFF / SHIFT_ON / SHIFT_LOCKED with software case
  folding of single-char labels (no separate uppercase layout files).
- Symbol toggle and accent long-press popups.
- Visual style: dark blue-grey panel `#FF384248`, near-white key text
  `#FFEEEEEE`, 40 percent white focus selector `#66EEEEEE`, teal action text
  `#FF30C6B4`, 28dp keys, 12dp/8dp gaps, focus overshoot 1.20, click scale 0.88.
- Gamepad shortcut semantics: A select, B back, X backspace, Y space,
  L1 cursor-left, R1 cursor-right, L3 symbols toggle, R3 caps lock.

### 1.3 Out of scope (deliberately omitted from the 1:1 port)

These are documented in the reports as removable with no layout impact:

- Voice input (KEYCODE_VOICE -7, RecognizerView, SpeechRecognizer). No shipped
  layout contains a -7 key. Stub permanently off.
- Word prediction / dictionary suggestions. Leanback has NO predictive engine;
  only a fixed 3-domain email list and app-provided completions (inert in a
  standalone binary). See section 10 for the suggestions decision.
- The number.xml keyboard (built but never installed by Leanback; numeric
  fields fall back to ABC).
- Device-lock / keyguard / secure-IME behavior (none exists in Leanback).
- The cm-to-pixel "physical keyboard" trackpad model (D-pad-only port).

### 1.4 Deliberate additions beyond 1:1 (because Nano has no host EditText)

Leanback never renders the text being typed; the host app's EditText does.
Nano types into a `std::string` with no host field, so we MUST add:

- A self-rendered text-preview / query line above the keyboard (already present
  in the current OSK as the "Search:" / prompt line; keep and restyle).
- Our own long-press timer and auto-repeat timer for held-A on Delete/Left/Right
  (Leanback rides Android key auto-repeat, which we do not get from evdev).
- Our own password masking in the preview field (Leanback never masks).

--------------------------------------------------------------------------------

## 2. Data model (C++)

Port the Leanback keyboards into a static, transcribed C++ data model. Generate
the tables in a dedicated header, `NanoOskLayouts.h`, by transcribing the
Leanback `res/xml/*.xml` files per report 1 section 3. The runtime keyboard
state and behavior live in `NanoOsk.cpp` / `NanoOsk.h`.

### 2.1 Special-code enum

Mirror the Leanback / framework constants exactly (report 1 section 2,
report 3 section 4.2). Use a strongly-typed enum plus the raw int values so
positive codepoints share the same field.

```cpp
// Special (negative) key codes. Positive values are Unicode codepoints.
enum OskCode : int {
    OSK_SHIFT        = -1,   // toggle shift (off <-> on); from locked -> off
    OSK_MODE_CHANGE  = -2,   // toggle ABC <-> SYM
    OSK_LEFT         = -3,   // move caret left
    OSK_RIGHT        = -4,   // move caret right
    OSK_DELETE       = -5,   // backspace
    OSK_CAPS_LOCK    = -6,   // toggle caps lock (injected, not a visible key)
    OSK_VOICE        = -7,   // unused (stub off)
    OSK_DISMISS_MINI = -8,   // dismiss accent/shift popup (injected)
    // positive: Unicode codepoint, committed as UTF-8
};
```

### 2.2 Special icon glyph enum (drawn instead of a label)

Leanback draws icons on delete/shift/space/left/right/mode keys. Per report 6
section 3, the recommended Nano approach is Unicode glyphs via `drawText`, no
new PNG assets. Define an enum that selects the rendered glyph:

```cpp
enum OskGlyph : int {
    GLYPH_NONE = 0,    // draw key.label as text
    GLYPH_DELETE,      // U+232B backspace, fallback label "DEL"
    GLYPH_SHIFT_OFF,   // U+21E7 hollow up-arrow
    GLYPH_SHIFT_ON,    // U+21E7 filled / accented
    GLYPH_SHIFT_LOCK,  // U+21EA up-arrow with bar, fallback "CAPS"
    GLYPH_SPACE,       // wide blank, optional label "space"
    GLYPH_LEFT,        // U+2190
    GLYPH_RIGHT,       // U+2192
    GLYPH_SYMBOLS,     // label "?!#" on ABC, label "ABC" on SYM
    GLYPH_ALPHABET,    // label "ABC"
};
```

Note: the shift key's icon is state-dependent (off/on/lock chosen at draw time
from the shift state), so `GLYPH_SHIFT_OFF` in the table is a marker meaning
"this is the shift key, pick the icon from current shift state at render time".
Same for the mode key (GLYPH_SYMBOLS marker, swaps to ABC text on the SYM page).

### 2.3 Key, Row, Keyboard structs

Geometry is stored as FRACTIONS of the keyboard's logical width/height so the
keyboard scales to any aspect ratio and panel size (section 4). The Leanback
absolute dp values (28dp key, 12dp/8dp gaps, 268dp space, 428dp row width,
172dp tall) are converted to fractions ONCE in the table generator.

```cpp
struct OskKey {
    int        code;        // OskCode special (negative) or Unicode codepoint
    const char* label;      // UTF-8 glyph/word drawn on the key (may be "")
    OskGlyph   glyph;       // GLYPH_NONE => draw label; else draw the icon glyph
    // Geometry as fractions of the keyboard box [0..1]:
    float      fx;          // left edge
    float      fw;          // width   (space key ~0.6262 = 268/428)
    // fy / fh are taken from the row (all keys in a row share height/top)
    bool       edgeLeft;    // keyEdgeFlags="left"
    bool       edgeRight;   // keyEdgeFlags="right"
    int        popupIndex;  // index into kOskPopups[], or -1 (no popup)
    bool       caseFoldable; // true for single-char letters (length < 3)
};

struct OskRow {
    float           fy;     // top edge as fraction of keyboard height
    float           fh;     // height as fraction (all rows equal: ~0.1628)
    bool            edgeTop;
    bool            edgeBottom;
    const OskKey*   keys;
    int             keyCount;
};

struct OskKeyboard {
    const OskRow*   rows;
    int             rowCount;       // always 5 for ABC/SYM
    // The SYM keyboard this ABC keyboard toggles to (and vice versa) is wired
    // by the LayoutChoice (section 3), not stored here, so the same SYM table
    // can be shared across many ABC layouts.
};
```

A "mini keyboard" (accent / shift popup) is a degenerate single-row keyboard:

```cpp
struct OskPopup {
    const OskKey*   keys;       // 1..11 cells, single row
    int             keyCount;
};
```

### 2.4 Geometry constants (fractions, derived from Leanback dp)

Computed once in the generator from report 1 section 5 and report 4 section 1:

- Row content width = 428dp. Key width fraction = 28/428 = 0.065421.
- Horizontal gap fraction = 12/428 = 0.028037.
- Space key width fraction = 268/428 = 0.626168.
- Keyboard height = 5*28 + 4*8 = 172dp (rows + interior vertical gaps).
- Row height fraction = 28/172 = 0.162791.
- Vertical gap fraction = 8/172 = 0.046512.
- Each key's `fx` = cumulative (width + h-gap) within its row.

Store the precomputed `fx`/`fw`/`fy`/`fh` directly in the tables so there is no
per-frame layout arithmetic; render multiplies by the aspect-aware keyboard box
(section 4.3). All rows are exactly 428dp wide by design, so every row fills the
full keyboard width and no per-row centering is needed.

### 2.5 The layout tables to transcribe (NanoOskLayouts.h)

Transcribe these from report 1 section 3 (which already gives every key code and
label). The DELTA descriptions in the report are convenient for authoring but
the generated header should be FULLY EXPANDED (every key explicit) so there is
no runtime overlay logic for the base grids:

ABC base layouts (16, all Latin):
`qwerty_us`, `qwerty_eu` (default), `qwerty_en_gb`, `qwerty_en_in`,
`qwerty_es_eu`, `qwerty_es_us`, `qwerty_az`, `qwerty_ca`, `qwerty_da`,
`qwerty_et`, `qwerty_fi`, `qwerty_nb`, `qwerty_sv`, `qwertz`, `qwertz_ch`,
`azerty`.

SYM layouts (5 used): `sym_us`, `sym_eu`, `sym_en_gb`, `sym_en_in`,
`sym_azerty`. Do NOT port `sym_fr` (dead resource, byte-equivalent to
sym_azerty) or `number.xml` (never installed).

Accent popups (15): `accent_a c d e g i k l n o s t u y z` with the exact
codepoint lists in report 1 section 3.5. Preserve the accent_c quirk (first
variant is uppercase U+00C7) or normalize to lowercase; recommend normalize to
lowercase U+00E7 for consistency with shift_cc and qwerty_ca.

Digit / currency popups (13): `shift_0..shift_9`, `shift_4_en_gb` (pound),
`shift_4_en_in` (rupee), `shift_4_eu` (euro).

Letter-variant popups used by the locale base keys: `shift_aa ae ao cc oo ox
uu nn_es n_es`, plus the "same letter" placeholders (`shift_b f h j m p q r v
w x y`). The "same letter" placeholders are low value for a plain buffer; see
section 10 decision (c). If accents are shipped, transcribe all; if not, skip
all popups.

### 2.6 Layout-switch graph

Represent the per-language pairing of ABC + SYM + locale overrides as a
`LayoutChoice`, exactly as report 2 section 4.2 recommends. Because all 16 ABC
layouts are fully expanded in `NanoOskLayouts.h`, the `LayoutChoice` just names
which two tables to use; the `-2` Mode-change key is a pure toggle between them
(report 1 section 4.1). No overrides are needed at runtime since the base
tables are pre-expanded; keep the choice minimal:

```cpp
struct LayoutChoice {
    const OskKeyboard* abc;   // pointer to one of the 16 expanded ABC tables
    const OskKeyboard* sym;   // pointer to one of the 5 SYM tables
};
```

Runtime state (in `NanoOsk`):

```cpp
enum OskPage { PAGE_ABC, PAGE_SYM };
enum OskShift { SHIFT_OFF = 0, SHIFT_ON = 1, SHIFT_LOCKED = 2 };

OskPage  mPage;          // ABC or SYM
OskShift mShift;         // off / on / locked
int      mFocusRow, mFocusCol;   // focused key in current page's grid
bool     mMiniOpen;      // accent/shift popup overlaying the grid
int      mMiniBaseRow, mMiniBaseCol;  // where the popup run starts
const OskPopup* mMini;   // active popup, or nullptr
int      mMiniOriginRow, mMiniOriginCol;  // key that opened the popup
float    mRememberedX;   // remembered column pixel for space-bar UP/DOWN
```

--------------------------------------------------------------------------------

## 3. Language support

### 3.1 Layouts shipped

Three physical Latin families with locale variants (report 2 section 4.1):

- QWERTY family: qwerty_us, qwerty_eu (default), qwerty_en_gb, qwerty_en_in,
  qwerty_es_us, qwerty_es_eu, qwerty_az, qwerty_ca, qwerty_da, qwerty_et,
  qwerty_fi, qwerty_nb, qwerty_sv.
- QWERTZ family (Y/Z swap): qwertz, qwertz_ch.
- AZERTY family (full reflow): azerty.

There are ZERO non-Latin layouts in Leanback. Arabic, CJK, Cyrillic, Thai,
Hebrew, Greek, Indic all fall back to qwerty_eu Latin. A 1:1 port has only
Latin keyboards. (Real IME-style CJK / Arabic input is NEW functionality, not a
Leanback port; see section 10 decision (e).)

### 3.2 Active-language source and the language-code to layout map

The active Nano UI language is `nanoGetLocale()` (returns `NanoLocale` enum),
backed by `persist.sys.locale`; the 2-letter code and region come from
`nanoGetLocaleInfo(loc).code` / `.regionCode` (report 6 section 6). The OSK
reads `nanoGetLocale()` in-process at OPEN time (not the property directly) and
maps to a `LayoutChoice`.

Important nuance: the Nano `NanoLocale` enum has only 15 values (en, es, fr, de,
it, pt, nl, ru, ja, ko, zh_CN, zh_TW, ar, tr, pl). The Leanback chain keys on
language+region. Map the Nano locale's `code` (and `regionCode` only where it
materially differs) through the EXACT first-match-wins order from report 2
section 4.3. Reproduce the order; do not reorder. Concretely, for the 15 Nano
locales the result is:

| NanoLocale  | code | region | LayoutChoice (ABC / SYM)        |
|-------------|------|--------|---------------------------------|
| LOCALE_EN   | en   | US     | qwerty_us / sym_us              |
| LOCALE_ES   | es   | ES     | qwerty_es_eu / sym_eu           |
| LOCALE_FR   | fr   | FR     | azerty / sym_azerty             |
| LOCALE_DE   | de   | DE     | qwertz / sym_eu                 |
| LOCALE_IT   | it   | IT     | qwerty_eu / sym_eu (default; it_IT not special) |
| LOCALE_PT   | pt   | BR     | qwerty_eu / sym_eu (default)    |
| LOCALE_NL   | nl   | NL     | qwerty_eu / sym_eu (default; only nl_BE is AZERTY) |
| LOCALE_RU   | ru   | RU     | qwerty_eu / sym_eu (default, Latin fallback) |
| LOCALE_JA   | ja   | JP     | qwerty_eu / sym_eu (default, Latin fallback) |
| LOCALE_KO   | ko   | KR     | qwerty_eu / sym_eu (default, Latin fallback) |
| LOCALE_ZH_CN| zh   | CN     | qwerty_eu / sym_eu (default, Latin fallback) |
| LOCALE_ZH_TW| zh   | TW     | qwerty_eu / sym_eu (default, Latin fallback) |
| LOCALE_AR   | ar   | SA     | qwerty_eu / sym_eu (default, Latin fallback) |
| LOCALE_TR   | tr   | TR     | qwerty_eu / sym_eu (default)    |
| LOCALE_PL   | pl   | PL     | qwerty_eu / sym_eu (default)    |

Implement the FULL report-2 chain anyway (not just these 15 rows) via a
`pickLayout(code, region)` function, so that if the Nano locale set grows (e.g.
adds da, fi, sv, nb, ca, en_GB) the OSK already routes correctly. The function
is the pseudocode in report 2 section 4.3, ported verbatim with the same 16
ordered rules. Default for anything unmatched: qwerty_eu / sym_eu.

### 3.3 Languages that get a dedicated layout vs fall back

- Dedicated: en (en_GB, en_IN, generic en), es (es_ES vs other es), fr
  (fr_CA -> qwerty_us, fr_CH -> qwertz, other fr -> azerty), de (de_CH ->
  qwertz_ch, other de -> qwertz), it_CH, az, ca, da, et, fi, nb, sv, hr, cs,
  hu, sr, sl, sq, nl_BE.
- Fallback to qwerty_eu / sym_eu: everything else, including ru, ja, ko, zh,
  ar, th, he, el, hi, pt, pl, tr, vi, id, uk, it_IT, nl_NL.

### 3.4 How the active Nano UI language selects the layout

`NanoOsk::open*()` calls `pickLayout(nanoGetLocaleInfo(nanoGetLocale()).code,
.regionCode)` once at open, stores the resulting `LayoutChoice`, sets
`mPage = PAGE_ABC`. The keyboard does NOT rebuild on locale change while open
(matches Leanback, which bakes layout at IME init). A subsequent open re-reads
the locale. There is NO in-keyboard language switch key in stock Leanback;
section 10 decision (d) covers whether to add one.

--------------------------------------------------------------------------------

## 4. Rendering plan

All drawing uses the existing Nano primitives (report 6 section 1): `drawQuad`
(flat fill), `drawText` (UTF-8, batched, with shadow/outline), `measureText`,
`drawIcon` (icon atlas, not needed for OSK). Coordinates are top-left origin,
y down, in logical surface pixels (`mWidth` x `mHeight`); panel rotation is
applied automatically in the vertex shader, so the OSK emits logical-landscape
pixel coordinates and ignores rotation (unless it uses `glScissor`, which it
should NOT need; see section 7).

### 4.1 Overall panel structure (report 4 section 2)

Bottom-docked, horizontally centered panel. Top to bottom:

```
[ optional suggestions strip + its darker background bar ]   (only if enabled)
[ text preview / query line ]                                (Nano addition)
[ keyboard grid (ABC or SYM) ]   [ action button to the RIGHT of the grid ]
[ bottom padding ]
[ help / hint footer line ]                                  (Nano addition)
```

When suggestions are disabled (the default for a 1:1 buffer port, and forced
for password / number / no-suggestions fields), the suggestions strip and its
background collapse and the grid rises (report 4 section 2.1 item 3). If
suggestions are omitted entirely (section 10 decision (b)), always run in the
collapsed layout.

### 4.2 The aspect-aware keyboard box

Compute a keyboard box `(kbX, kbY, kbW, kbH)` that adapts to any aspect ratio
(1:1, 3:2, 4:3, 16:9) and to portrait. The Leanback grid has a fixed aspect of
428:172 = 2.488:1. Algorithm:

1. Pick a target width fraction of the surface: `wFrac` in [0.5 .. 0.92]. Use a
   smaller fraction on very wide (16:9 landscape) panels and a larger fraction
   on narrow / portrait panels so the keyboard never looks lost or cramped.
   - Landscape wide (aspect >= 1.6): wFrac = 0.78.
   - Landscape medium (1.2 <= aspect < 1.6, covers 4:3 and 3:2): wFrac = 0.86.
   - Square (0.85 <= aspect < 1.2): wFrac = 0.90.
   - Portrait (aspect < 0.85): wFrac = 0.96.
   (aspect = mWidth / mHeight in logical landscape coordinates.)
2. `kbW = mWidth * wFrac`. Reserve room on the right for the action button:
   `kbW -= actionButtonW + gap`. (Action button width measured from its label,
   section 4.6.)
3. `kbH = kbW / 2.488` (preserve the 428:172 aspect).
4. Clamp key size: compute `keyPx = kbW * 0.065421` (one key width). Enforce
   `minKeyPx` and `maxKeyPx` (section 7). If `keyPx` exceeds `maxKeyPx`, shrink
   `kbW`/`kbH` to hit the max; if below `minKeyPx`, the panel is allowed to
   exceed `wFrac` up to 0.98 to keep keys legible, else accept the min.
5. If `kbH` plus the query line, optional suggestions strip, footer, and
   paddings would exceed an allowed vertical budget (e.g. 0.62 * mHeight in
   landscape, 0.55 in portrait so the field above stays visible), scale the
   whole stack down uniformly to fit.
6. Center horizontally: `panelW = kbW + gap + actionButtonW`;
   `kbX = (mWidth - panelW) / 2`. Bottom-anchor:
   `kbY = mHeight - bottomPad - footerH - kbH`.

Keep a single scalar `sf = fminf(mWidth/1080.0f, mHeight/720.0f)` (as the
current OSK does) for font scales and paddings so text tracks the panel size.

### 4.3 Key rect computation

For each visible key in the current page:

```
keyX = kbX + key.fx * kbW;
keyY = kbY + row.fy * kbH;
keyW = key.fw * kbW;
keyH = row.fh * kbH;
```

This is the only geometry math at render time. Because fractions already encode
gaps, adjacent keys are naturally separated.

### 4.4 Drawing each key

1. Panel background: one `drawQuad` covering the whole panel rect at
   `#FF384248` (0.219, 0.259, 0.282) alpha ~0.92 (Leanback panel is opaque;
   use slightly translucent so the XMB shows through, matching current OSK's
   0.85). Optionally a subtle inset.
2. Focus selector (drawn BEHIND the focused key glyph, or as a halo): a
   `drawQuad` sized to the focused key rect, overscaled by the focus factor
   1.20 in the overestimated dimension(s), tinted `#66EEEEEE` (1.0,1.0,1.0 at
   alpha 0.40). For letter keys overestimate both width and height and snap to
   square if within 10 percent (report 3 section 3.2); for the wide space bar
   overestimate height only. Animate position/size over 150 ms with a
   decelerate(1.5) curve toward the target rect (report 3 section 3.3); on
   A-press, scale to 0.88 over 100 ms then reverse after 30 ms (report 3
   section 3.5). Use `mFrameDt` for the animation clock.
3. Key face: if `key.glyph == GLYPH_NONE`, draw `key.label` centered via
   `measureText` for x-centering, in `#FFEEEEEE` (0.933) for normal keys,
   white (1,1,1) for the focused key. Apply case folding for `caseFoldable`
   keys per shift state (section 5.2). Multi-char labels ("ABC", "?!#") use the
   smaller mode-change font (16sp equivalent); single-char labels use the key
   font (18sp equivalent).
4. Icon keys (`key.glyph != GLYPH_NONE`): draw the chosen Unicode glyph via
   `drawText` centered. For shift, pick GLYPH_SHIFT_OFF/ON/LOCK from `mShift`.
   For the mode key, draw "?!#" on the ABC page and "ABC" on the SYM page.
5. When a mini popup is open, dim every non-popup key to alpha 40/255
   (report 3 section 5.2) by drawing them with reduced alpha.

### 4.5 The text preview / query line (Nano addition)

Above the grid, one line:

- Search mode: `"Search: " + buffer + "_"` in cyan (0, 0.85, 1), matching the
  current OSK.
- Password mode (masked): `prompt + ": " + maskPassword(buffer) + "_"` in
  orange (1, 0.75, 0.35).
- Password mode (plaintext, settings text-edit): `prompt + ": " + buffer + "_"`
  unmasked.
- The caret `_` shows insert position. If the caret model supports mid-string
  editing (section 5), render the caret at the caret index, not always at end.

Scale ~2.0 * sf. Left-align within the panel, or center if it fits.

### 4.6 The action button (Enter / Done)

To the RIGHT of the grid, vertically centered (report 4 section 2.2). Label is
"Done" by default, or Go/Next/Search/Send per the field's action (search mode
shows "Search"; Wi-Fi/text edit show "Done"). Teal text `#FF30C6B4`, 18sp,
16dp horizontal padding, 32dp tall. It is a focusable element: RIGHT from the
right edge of the grid moves focus to it (section 5.1). Pressing A on it fires
confirm (section 5).

### 4.7 The footer / help line (Nano addition, keep current behavior)

Bottom line, gray, ~1.5 * sf. Localize via `tr()` (report 6 section 6). Content
reflects the gamepad map, e.g.:
`A:Type  X:Backspace  L:Shift  R:Sym  Start:Done  B:Cancel`. Search mode shows
"Start:Search"; password mode "Start:Submit". Keep the existing strings but
extend for the new keys (Sym toggle, accent popup hint).

### 4.8 Colors and sizes (mapped from Leanback dimens/colors, report 4 section 1)

| Element            | Color (RGBA 0..1)                | Source |
|--------------------|----------------------------------|--------|
| Panel background   | 0.219,0.259,0.282, ~0.92         | #FF384248 |
| Key text normal    | 0.933,0.933,0.933, 1.0           | #FFEEEEEE |
| Key text focused   | 1,1,1, 1.0                       | white  |
| Focus selector     | 1,1,1, 0.40                      | #66EEEEEE |
| Idle selector      | 1,1,1, 0.15                      | #26EEEEEE |
| Suggestions bar bg | 0.149,0.196,0.220, 1.0           | #FF263238 |
| Suggestion / action text | 0.188,0.776,0.706, 1.0     | #FF30C6B4 teal |

Sizes (relative, scaled by the keyboard box, not absolute dp): key 28/172 of
height; selector overshoot 1.20; click 0.88; mini-kb dim alpha 40/255.

--------------------------------------------------------------------------------

## 5. Navigation, state machine, and input mapping

### 5.1 D-pad navigation: geometric nearest-in-direction (NOT a neighbor table)

Reproduce `getNextFocusInDirection` + `getBestFocus` + `getNearestIndex`
(report 3 section 2). Because the new geometry is fraction-based and the bottom
row has a wide space bar, DO NOT hard-code Leanback's magic indices (46, 53, 6).
Instead hit-test actual key rects. Algorithm for a move in `dir`:

1. Start from the focused key rect `(rx, ry, rw, rh)`. Compute a probe point:
   - `extraSlide = rh / 2`.
   - LEFT: if focused key has `edgeLeft`, do NOT move (no wrap; stay). Else
     `px = rx - extraSlide`, `py = centerY`.
   - RIGHT: if focused key has `edgeRight`, move focus to the ACTION button and
     return. Else `px = rx + rw + extraSlide`, `py = centerY`.
   - UP: `py = centerY - rh * 1.25`, `px = centerX`. If the focused key is the
     space bar, use `px = mRememberedX` (remembered column).
   - DOWN: `py = centerY + rh * 1.25`, `px = centerX` (space-bar remembered-X
     applies the same way).
2. If `py` is above the keyboard top:
   - If suggestions enabled and present, move into the suggestions strip at the
     nearest chip to `px`.
   - Else if "escape north" is desired (section 8), dismiss the keyboard.
   - Else clamp back into the top row.
3. Otherwise hit-test all keys in the current page: pick the key whose rect
   center is nearest to `(px, py)` (Euclidean, or the Leanback grid-snap; nearest
   center is simpler and equivalent for this grid). The wide space bar will be
   selected when `px` falls within its span. Set focus to it.
4. Update `mRememberedX = centerX of new focus` when the new key is NOT the space
   bar, so vertical moves through space preserve the column (report 3 section
   2.2). Restore `mRememberedX` when leaving the space bar upward/downward.

Action button navigation (report 3 section 2.3): from the action button, LEFT
re-enters the grid on the same row last left (`py = mRememberedY`); UP goes to
the suggestions strip if present. No DOWN/RIGHT.

Auto-repeat: directional moves come through the existing
`navPress`/`navRelease`/`tickNavRepeat` machinery (report 5 section 3, report 6
section 5), so hold-to-repeat cursor movement is free.

### 5.2 Shift state machine (report 1 section 2.5, report 3 section 6)

States SHIFT_OFF (0), SHIFT_ON (1), SHIFT_LOCKED (2). NO separate uppercase
layout; uppercase is software case folding of `caseFoldable` single-char labels.

- Single shift click (`-1` key, or L1 if we keep L1 as shift): if currently
  shifted (ON or LOCKED) -> OFF; else -> ON. (From LOCKED a single click goes
  to OFF, clearing caps.)
- Double-click shift within 200 ms, OR long-press shift, OR R3 (THUMBR), OR the
  -6 caps code: toggle caps lock (LOCKED <-> OFF).
- Auto-revert: after committing a normal character while SHIFT_ON (and not
  LOCKED), drop to SHIFT_OFF (one-shot shift). LOCKED persists.
- Initial shift on open: derive from field caps flags if present (caps-chars ->
  LOCKED, caps-sentences/words -> ON), else OFF. The current Nano OSK opens with
  shift ON (uppercase first); preserve that default for parity with current
  behavior unless a caps flag says otherwise.
- Render: case-fold only labels with `caseFoldable == true` (single char,
  length < 3); never fold "ABC" / "?!#" / multi-char labels. Swap the shift key
  glyph per state (off / up-arrow / lock).

### 5.3 Symbol toggle (report 1 section 4.1)

The `-2` Mode-change key (and L3 / THUMBL, and R1 if we choose to map R1 to Sym;
see section 5.6) toggles `mPage` between PAGE_ABC and PAGE_SYM, using the
`LayoutChoice.abc` / `.sym` tables. Focus is reset to a sensible key (keep the
same row/col if valid, else clamp). The mode key glyph reads "?!#" on ABC and
"ABC" on SYM.

### 5.4 Accent / shift popup mini-keyboard (report 1 section 3.4-3.5, report 3
section 5)

Trigger: long-press A (center) on a key with `popupIndex >= 0`. Long-press =
hold A past a threshold (implement a timer, e.g. 400 ms, since we do not get
Android long-press). On trigger:

1. Load the popup (`kOskPopups[popupIndex]`), a single row of 1..11 cells.
2. Overlay it in place starting at the originating key's slot, inheriting that
   slot's x/y. If the run would overflow the row's right end, right-align it to
   the row end (`baseCol = rowKeyCount - popupKeyCount`).
3. Set `mMiniOpen = true`, remember `mMiniOriginRow/Col`, move focus to the
   first popup cell. Dim all non-popup keys to alpha 40/255.
4. Navigate among popup cells with LEFT/RIGHT (same geometric nav, but bounded
   to the popup run). UP/DOWN out of the run, or LEFT/RIGHT past its ends onto a
   normal key, dismisses the popup.
5. The first popup cell is case-inverted relative to the main shift state
   (`isInvertible`), so long-pressing a lowercase letter offers its uppercase as
   the first choice (report 3 section 5.5).
6. Commit a popup cell (A): append its codepoint as UTF-8, dismiss the popup,
   restore focus to the originating key. (`-8` dismiss code also closes it.)

If accents are omitted (section 10 decision (c)), `popupIndex` is always -1 and
this whole subsystem is compiled out.

### 5.5 Text-buffer operations (report 1 section 2.4, report 3 section 4.3)

The buffer is a UTF-8 `std::string` with an optional caret index (in codepoints
or byte offset; recommend byte offset with codepoint-aware move). Operations:

- STRING (positive codepoint, space 32, period 46): insert the codepoint's
  UTF-8 bytes at the caret; advance caret. Enforce a length cap (keep the
  existing 64-char cap, or raise for password fields if desired).
- BACKSPACE (-5): delete one codepoint before the caret (UTF-8 aware: remove a
  full multibyte sequence, not one byte).
- LEFT (-3) / RIGHT (-4): move caret one codepoint left/right, clamped.
- ACTION (action button / Start / Enter): confirm. Search -> run search;
  password/text -> fire callback. (See section 6.)
- DISMISS / cancel (B / Back): close without confirming.

The current Nano OSK has NO caret (append-only, backspace pops the last char).
Adding a caret is a faithful Leanback behavior (LEFT/RIGHT keys exist in the
bottom row and as L1/R1 shortcuts). RECOMMENDATION: implement a real caret so
the bottom-row Left/Right keys and L1/R1 do something meaningful; if the product
prefers minimal scope, the Left/Right keys and L1/R1 can be no-ops and the caret
is always at end (documented as a deviation).

### 5.6 Gamepad button mapping

Reconcile Leanback semantics (report 1 section 6, report 3 section 4.5) with the
existing Nano OSK help text and the existing pollInput dispatch (report 5
section 3). The existing Nano map is: A=type, B=cancel, X=backspace, Y=toggle
keyboard, Start/Enter=confirm, L1=shift, R1=quick-resume (leaks through), dpad=
move. Target map for the Leanback OSK:

| Button (Linux code)        | Action                                            |
|----------------------------|---------------------------------------------------|
| A / BTN_SOUTH              | activate focused key (type / shift / mode / etc.); hold = long-press popup |
| B / BTN_EAST / KEY_BACK    | if popup open, dismiss popup; else cancel/close OSK |
| X / BTN_NORTH              | backspace (-5)                                    |
| Y / BTN_WEST               | toggle OSK up/down (keep current Nano behavior)   |
| Start / BTN_START          | confirm (search / submit)                         |
| KEY_ENTER                  | confirm                                           |
| L1 / BTN_TL / KEY_L        | shift toggle (keep current Nano L1=shift)         |
| R1 / BTN_TR / KEY_R        | symbol page toggle (NEW; replaces quick-resume leak inside OSK) |
| L3 / BTN_THUMBL            | symbol page toggle (Leanback parity, optional)    |
| R3 / BTN_THUMBR            | caps lock toggle (Leanback parity, optional)      |
| D-pad / HAT / left stick   | move focus (with hold-to-repeat)                  |

Notes:
- Leanback maps X=delete, Y=space, L1=cursor-left, R1=cursor-right. The existing
  Nano OSK uses Y=toggle and L1=shift. Keeping the Nano conventions avoids
  retraining users and keeps the keep-stable input contract (report 5 section
  5.5). RECOMMENDATION: keep Nano's X=backspace, Y=toggle, L1=shift; add
  R1=symbol-toggle inside the OSK (currently R1 leaks to quick-resume, which
  report 5 section 3 explicitly says is free to repurpose). Optionally add
  L3=sym and R3=caps for power users. Document this divergence from Leanback's
  exact button map as an intentional Nano-consistency choice.
- Space and Enter are reachable as on-grid keys (space bar, action button), so a
  dedicated Y=space is not required; Y stays the OSK toggle.

--------------------------------------------------------------------------------

## 6. Integration (preserve every caller contract)

The new OSK must drop in without touching callers. Report 5 section 5 is the
authoritative keep-stable list. The cleanest approach (report 6 section 5):
keep the existing public method names and the `mOsk*` members that external code
reads/writes, and route their bodies into the new `NanoOsk` implementation.

### 6.1 Public methods to keep stable (exact signatures)

Declared in `NanoMenu.h`, called from outside the OSK:

- `void openOsk()` - fresh search OSK (clears buffer, password mode off).
  Caller: `NanoMenuInput.cpp:843` (Y in XMB).
- `void closeOsk()` - cancel/dismiss; clear `mOskActive`; in search mode clear
  `mSearchActive` only if buffer empty. Callers: `NanoMenuInput.cpp:250`,
  `:839`.
- `void oskType(char c)` - keep the signature for compatibility, but route A
  through a new `oskActivateKey()` internally (the new OSK types whole UTF-8
  sequences, not single chars). Caller: `NanoMenuInput.cpp:295`.
- `void oskBackspace()` - delete one codepoint. Caller: `NanoMenuInput.cpp:857`.
- `void oskConfirm()` - submit: password -> fire callback and tear down; search
  -> set `mSearchActive` and `updateSearchResults()`. Callers:
  `NanoMenuInput.cpp:814`, `:823`.
- `void updateSearchResults()` - unchanged; populates `mSearchResults`.
- `void renderOsk()` - draws the keyboard as the LAST overlay; no-op when not
  active. Callers: `NanoMenuRender.cpp:994`, `:1002`.
- `void openOskForPassword(const std::string& prompt,
  std::function<void(const std::string&)> onSubmit)` - masked password/text OSK.
  Callers: `NanoMenuSettings.cpp:726`, `NanoMenuSettingsTree.cpp:954`.
- `std::string maskPassword(const std::string& s)` - internal callers only; can
  fold into the renderer.
- `void renderPasswordPromptOverlay()` - internal callers only; can fold into
  renderOsk.

### 6.2 State the rest of the codebase reads/writes directly (keep or proxy)

These are touched outside the OSK and form part of the contract (report 5
section 5.2):

- `mOskActive` (bool) - THE master gate. Read at the top of `handleSelect`,
  `handleBack`, `handleUp`, `handleDown`, `handleLeft`, `handleRight`, and in
  `pollInput`. Written externally by the Y re-show (`NanoMenuInput.cpp:841`),
  the L1-leaving-XMB clear (`:885`), and the launch paths
  (`NanoMenuXmb.cpp:1218, 1251, 1408, 1452`). MUST remain a bool meaning "OSK is
  up and consuming input". Keep the member.
- `mOskQuery` (std::string) - the typed buffer. Read at `NanoMenuXmb.cpp:1895`
  (search header), cleared at `NanoMenuInput.cpp:267`, pre-seeded at
  `NanoMenuSettingsTree.cpp:960`. The new OSK MUST keep `mOskQuery` as its
  buffer (or proxy) so pre-seed/clear/read keep working. If a caret is added,
  the caret index resets to end of buffer when `mOskQuery` is set externally.
- `mOskShift` (bool) - toggled by L1 (`NanoMenuInput.cpp:871`), read by
  `handleSelect` and renderOsk. The new OSK has a 3-state `mShift`; keep
  `mOskShift` as a synced view (true when `mShift != OFF`) OR move L1 handling
  into the OSK. RECOMMENDATION: move L1's shift toggle into an OSK method
  (`oskToggleShift()`) and keep `mOskShift` as a derived mirror for any external
  reader, to preserve the 3-state machine.
- `mOskPasswordMode` (bool) - read at `NanoMenuSettings.cpp:1268, 1418`,
  `NanoMenuSettingsRender.cpp:186`. Keep.
- `mOskPlaintext` (bool) - set at `NanoMenuSettingsTree.cpp:961`. Keep.
- `mOskCursorX` / `mOskCursorY` (int) - written by the dpad handlers
  (`NanoMenuInput.cpp:464, 515`; `NanoMenuXmb.cpp:1041, 1066`) and read at
  `NanoMenuInput.cpp:293`. THIS IS THE MAIN COUPLING THAT THE NEW GEOMETRY
  BREAKS. The old code pokes `mOskCursorX/Y` directly and indexes
  `kOskLayout[mOskCursorY][mOskCursorX]`. The fix (report 5 section 5.4,
  report 6 section 5): introduce `oskMoveCursor(NavDir)` and
  `oskActivateKey()`, change the dpad/select handlers to call those instead of
  poking the cursor and indexing `kOskLayout`, and delete the direct
  `mOskCursorX/Y` writes. `mFocusRow/mFocusCol` (the new focus) live inside the
  OSK. This requires EDITING `NanoMenuInput.cpp` and `NanoMenuXmb.cpp` handler
  bodies (the only handler edits needed).
- `mOskPasswordPrompt`, `mOskPasswordCallback` - fully internal. Keep the
  contract: callback fires exactly once on confirm with the raw string, never on
  cancel, and is moved out + cleared before invocation so it can re-open the OSK.
- `mSearchResults` / `mSearchActive` / `mSearchSelectedIndex` - the search
  results model. On confirm with non-empty buffer, set `mSearchActive = true`
  and fill `mSearchResults` with `{sysIdx, gameIdx}`. Keep `updateSearchResults`
  behavior (live incremental search on keystroke in search mode).

### 6.3 Files to ADD

- `NanoOsk.h` - the new OSK class/state declarations (or, to stay within the
  `NanoMenu` class, a new set of `NanoMenu` methods + a `NanoOskState` struct
  member; recommend keeping it as `NanoMenu` methods to reuse `drawText` etc.
  without friend plumbing).
- `NanoOsk.cpp` - the new OSK implementation: layout selection, navigation,
  shift/sym/popup state machine, buffer ops, and `renderOsk()`.
- `NanoOskLayouts.h` - the transcribed static tables (16 ABC + 5 SYM keyboards,
  accent + digit popups), generated from report 1 section 3. Header-only, all
  `static const`.

(If you prefer to keep `renderOsk`/`oskConfirm`/etc. as `NanoMenu` members,
`NanoOsk.cpp` simply defines those `NanoMenu::` methods; `NanoOsk.h` then only
holds the `NanoOskState` struct and the enums. Either factoring is acceptable;
the table header is mandatory either way.)

### 6.4 Files to EDIT

- `NanoMenu.h` - replace the search-OSK member cluster geometry (`mOskCursorX/Y`
  semantics) with the new `NanoOskState` (page, shift, focusRow/Col,
  mini-popup, remembered-X, caret), keep `mOskActive`, `mOskQuery`,
  `mOskPasswordMode`, `mOskPlaintext`, `mOskPasswordPrompt`,
  `mOskPasswordCallback`, `mSearch*`. Add new method declarations
  (`oskMoveCursor`, `oskActivateKey`, `oskToggleShift`, `oskToggleSym`,
  `oskOpenPopup`, etc.). Keep all section 6.1 signatures.
- `NanoMenuXmb.cpp` - remove the old `renderOsk`, `openOsk`, `closeOsk`,
  `oskType`, `oskBackspace`, `oskConfirm`, and the OSK branches in
  `handleLeft`/`handleRight` (move grid-cursor logic into the OSK). The
  definitions move to `NanoOsk.cpp`. Keep `updateSearchResults` here or move it;
  it reads XMB systems.
- `NanoMenuInput.cpp` - change the OSK branches in `handleSelect`, `handleUp`,
  `handleDown` (and the `pollInput` switch) to call `oskActivateKey()` /
  `oskMoveCursor(DIR)` instead of poking `mOskCursorX/Y` and indexing
  `kOskLayout`. Add R1=symbol-toggle (and optional L3/R3) inside the OSK gate.
  Keep the volume/brightness/power handling BEFORE the OSK switch (report 5
  section 3): those must never be blocked by the OSK.
- `NanoMenuSettings.cpp` - `openOskForPassword`, `maskPassword`,
  `renderPasswordPromptOverlay` either stay (calling into the new state) or fold
  into `NanoOsk.cpp`. Keep the Wi-Fi caller and the password-overlay render
  guards (`mOskActive && mOskPasswordMode`).
- `Android.bp` - add `NanoOsk.cpp` to `srcs` (and `NanoOsk.h` / `NanoOskLayouts.h`
  are headers, no `srcs` entry needed). No new dependencies (FreeType `libft2`,
  `libpng`, `libGLESv2`, `libEGL` already present).
- `NanoMenuStrings.h` / `NanoMenuStrings.cpp` - add any new `StringId` rows for
  OSK labels/footer ("Done", "Space", "Shift", "Symbols", help text) following
  the 15-column-per-row pattern, so footer/labels follow the active language.

### 6.5 Render-order and input-dispatch hook points

- Render: `renderOsk()` stays the LAST overlay in BOTH the setup-wizard path
  (`NanoMenuRender.cpp:994`) and the XMB path (`NanoMenuRender.cpp:1002`), drawn
  AFTER `renderWifiScreen`/`renderBtScreen`/`renderSettingsTree` so the password
  keyboard sits on top of those overlays (report 5 section 4). Do NOT change
  this ordering.
- Input: keep the `mOskActive`-first gate at the top of every `handle*` and in
  `pollInput`. The OSK consumes the event and returns; no downstream menu logic
  runs. Volume/brightness/power are handled before the OSK switch and must stay
  there.

--------------------------------------------------------------------------------

## 7. Portrait and aspect-ratio handling (concrete rules)

The Leanback original has no portrait layout; it keeps a fixed-dp grid centered
at the bottom (report 4 section 2.7). The Nano port must adapt to 1:1, 3:2, 4:3,
16:9, and portrait. Concrete rules:

- Keyboard aspect is FIXED at 428:172 (2.488:1). Never reflow rows; the row
  count and per-row key counts are constant. Scale the whole grid uniformly.
- Width fraction `wFrac` by aspect (section 4.2 step 1): 0.78 (16:9 wide),
  0.86 (4:3 and 3:2), 0.90 (square), 0.96 (portrait). These keep the keyboard
  visually balanced without it dominating wide screens or shrinking on tall
  ones.
- Min key size: `minKeyPx = 22 * sf` (legibility floor; below this, allow the
  panel to widen up to wFrac 0.98 before accepting the min).
- Max key size: `maxKeyPx = 64 * sf` (so on huge or square panels the keyboard
  does not become comically large; shrink the box to hit the max).
- Max keyboard width fraction: 0.98 of `mWidth` (hard ceiling after the
  min-key-size escape).
- Vertical budget: keyboard + query line + optional suggestions + footer +
  paddings must fit within 0.62 * mHeight (landscape) or 0.55 * mHeight
  (portrait). If it does not, scale the entire stack down uniformly so the
  preview field and the content above remain visible.
- Action button: in very narrow / portrait layouts where placing the action
  button to the RIGHT of the grid would force the grid too small, STACK the
  action button below the grid (or rely on Start/Enter to confirm and shrink the
  on-screen action button to an icon). RECOMMENDATION: in portrait (aspect <
  0.85), drop the on-screen action button to a compact "Done" chip centered
  below the grid; RIGHT-from-right-edge then targets that chip.
- Suggestions strip: when shown, it sits above the query line; in portrait it
  may wrap to fewer visible chips (horizontal scroll). When suggestions are
  disabled (default for the buffer port), it is absent and the grid rises.
- DRM rotation: the OSK emits logical-landscape pixel coordinates; the vertex
  shader applies panel rotation (0/90/180/270) automatically (report 6 section
  1). The OSK must NOT use `glScissor` (which would require manual rotation
  remap); all clipping is implicit in the quads it draws. If a future scissor is
  needed, follow `NanoMenuXmb.cpp:1803` and remap by `sDrmGlRotation` /
  `sDrmRotationDeg`.

--------------------------------------------------------------------------------

## 8. Escape / dismiss / action behaviors

- Cancel (B / Back): close the OSK without confirming. If a mini popup is open,
  B dismisses the popup first, then a second B closes the OSK. `closeOsk()`
  clears `mOskActive`; in search mode clears `mSearchActive` only if the buffer
  is empty (otherwise results stay visible).
- Confirm (Start / Enter / action button A): `oskConfirm()`. Search -> set
  `mSearchActive` and `updateSearchResults()`. Password/text -> fire the
  callback with the raw buffer, tear down all password state, then invoke (so
  the callback may re-open the OSK safely).
- Escape north (optional, report 3 section 8.1): pressing UP at the top row with
  no suggestions can dismiss the OSK. RECOMMENDATION: omit by default (the Nano
  OSK has no escape-north today); B/Back is the close gesture. Add only if a
  caller requests it.

--------------------------------------------------------------------------------

## 9. Build

- `Android.bp` (`/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/Android.bp`):
  add `"NanoOsk.cpp"` to the `cc_binary` `srcs` list. Headers (`NanoOsk.h`,
  `NanoOskLayouts.h`) need no `srcs` entry.
- No new shared_libs. The OSK uses only existing primitives: `libft2`
  (FreeType, already linked), `libGLESv2`/`libEGL`, `libpng`/`libz` (already
  linked). No HarfBuzz, no ICU, no new deps.
- No init.rc / sepolicy changes (the OSK is in-process, no new device access).
- Keep the existing `-O3 -ffast-math` flags; the OSK has no IEEE-correctness
  needs.

--------------------------------------------------------------------------------

## 10. Open questions for the product owner

Each is framed with a recommended default. These materially change scope.

(a) Voice input. RECOMMENDATION: OMIT. No shipped Leanback layout has a voice
    key (-7); Nano has no mic / voice path. Stub `mVoiceEnabled = false`
    permanently. Zero layout impact. Decision needed only if voice is a product
    requirement (it is NOT a 1:1 Leanback feature on the keyboard surface).

(b) Word suggestions / autocomplete strip. RECOMMENDATION: OMIT the general
    suggestions strip; Leanback has no predictive engine (only a fixed 3-entry
    email-domain list and inert app-completions). Optionally KEEP the email
    domain auto-complete (`@gmail.com`, `@yahoo.com`, `@hotmail.com`, optionally
    the 8-entry domain list) gated to email fields. Default: omit entirely and
    always run the suggestions-off (collapsed) layout, matching password/number
    behavior. This simplifies layout and navigation. Decision: include
    email-domain chips, or omit all suggestions.

(c) Accent long-press popups. RECOMMENDATION: INCLUDE for Latin-diacritic
    languages (a/e/i/o/u/c/n/s/z accents), since the glyphs already render via
    the existing FreeType atlas and accents are a visible Leanback feature.
    SKIP the low-value "same letter" shift_* placeholders (shift_b/f/h/j/m/p/q/
    r/v/w/x/y) which just re-show the base letter. Decision: ship accents (with
    the long-press timer + popup mini-kb), or omit popups entirely for a flatter
    keyboard.

(d) Language selection model. Stock Leanback follows the system locale only (no
    in-keyboard switch, no globe key). Options: (i) follow the Nano UI language
    automatically (read `nanoGetLocale()` at open); (ii) add an in-keyboard
    language switch key / globe; (iii) both. RECOMMENDATION: (i) follow the Nano
    UI language automatically for 1:1 Leanback parity. Add an in-keyboard switch
    only if users need to type in a language other than the UI language without
    changing the whole UI; if so, prefer cycling among a small curated set
    (e.g. the current UI language plus QWERTY-US) on a spare button rather than a
    full globe picker.

(e) Exact set of languages / layouts to ship. The full Leanback set is 16 Latin
    ABC layouts + 5 SYM layouts, covering en/es/fr/de/it_CH/az/ca/da/et/fi/nb/sv
    /hr/cs/hu/sr/sl/sq/nl_BE and a default. The current Nano locale enum has 15
    UI languages, most of which fall back to qwerty_eu. RECOMMENDATION: transcribe
    ALL 16 ABC + 5 SYM layouts (the data is static and small) so the keyboard is
    correct for any locale the Nano UI ever adds, and route via the full
    report-2 chain. Non-Latin scripts (CJK, Arabic, Cyrillic, Thai, etc.) have NO
    Leanback layout and fall back to Latin qwerty_eu; true CJK/Arabic IME input
    is NEW work (requires shaping/BiDi for Arabic per report 6 section 4, and an
    input-method engine for CJK) and is explicitly out of a 1:1 Leanback port.
    Decision: confirm "all 16 Latin layouts + default, non-Latin falls back to
    Latin" is acceptable, or scope a separate CJK/Arabic IME effort.

--------------------------------------------------------------------------------

## 11. Phased implementation plan (dependency-aware, parallelizable)

Phase ordering respects data-before-logic dependencies. Tasks marked [P] can run
in parallel once their inputs exist.

Phase 0 - Scaffolding (blocks everything)
- 0.1 Add `NanoOsk.h` (enums, structs, `NanoOskState`), `NanoOsk.cpp` (empty
  stubs of the keep-stable methods), `NanoOskLayouts.h` (empty), and wire
  `NanoOsk.cpp` into `Android.bp`. Confirm it builds.

Phase 1 - Data tables (blocks render, nav, language)
- 1.1 [P] Transcribe the 16 ABC keyboards into `NanoOskLayouts.h` (fully
  expanded, fraction geometry) from report 1 section 3.1.
- 1.2 [P] Transcribe the 5 SYM keyboards from report 1 section 3.2.
- 1.3 [P] Transcribe accent + digit + currency popups from report 1 sections
  3.4-3.5 (only if accents are in scope per decision (c)).
- 1.4 [P] Implement `pickLayout(code, region)` (the full 16-rule report-2
  chain) and a unit smoke test that maps each of the 15 NanoLocales to the
  expected `LayoutChoice`.

Phase 2 - Core render (depends on Phase 1 tables)
- 2.1 Implement the aspect-aware keyboard box (section 4.2) and key-rect
  computation (section 4.3).
- 2.2 Implement `renderOsk()`: panel, grid keys (labels + icon glyphs), focus
  selector with overshoot/click animation, query/preview line, action button,
  footer. Wire `mOskActive` guard and the last-overlay render order.
- 2.3 [P] Add OSK `StringId` rows to `NanoMenuStrings` for labels/footer.

Phase 3 - Navigation + state machine (depends on Phase 2 geometry)
- 3.1 Implement `oskMoveCursor(NavDir)` geometric nearest-in-direction with
  edge rules, remembered-column, and action-button transitions (section 5.1).
- 3.2 Implement shift state machine (off/on/locked, case folding, double-click /
  long-press caps) (section 5.2).
- 3.3 Implement `oskToggleSym()` page toggle (section 5.3).
- 3.4 [P] Implement accent popup open/navigate/commit/dismiss + long-press timer
  (section 5.4), if in scope.
- 3.5 Implement buffer ops with caret (insert/backspace/left/right, UTF-8 aware)
  (section 5.5).

Phase 4 - Integration (depends on Phases 2-3)
- 4.1 Implement `openOsk`, `closeOsk`, `oskConfirm`, `oskActivateKey`,
  `oskBackspace` against the new state, preserving the keep-stable contracts
  (section 6.1-6.2).
- 4.2 Implement `openOskForPassword` (+ pre-seed via `mOskQuery`, `mOskPlaintext`)
  and the password preview/mask. Preserve the callback-fires-once-on-confirm,
  never-on-cancel contract.
- 4.3 Edit `NanoMenuInput.cpp` + `NanoMenuXmb.cpp` handler bodies to call
  `oskMoveCursor`/`oskActivateKey` instead of poking `mOskCursorX/Y` and
  `kOskLayout`. Add R1=sym (and optional L3/R3) inside the OSK gate. Keep
  volume/brightness/power before the OSK switch.
- 4.4 Remove the old `kOskLayout`/`kOskRows`/`kOskCols` once nothing references
  them; verify the Wi-Fi password and settings text-edit flows still work.

Phase 5 - Language wiring (depends on Phase 1.4 + Phase 4)
- 5.1 Call `pickLayout(nanoGetLocaleInfo(nanoGetLocale())...)` at open; store the
  `LayoutChoice`; default to qwerty_eu/sym_eu.
- 5.2 Verify ABC<->SYM toggle uses the chosen pair.

Phase 6 - Portrait / aspect polish (depends on Phase 2)
- 6.1 [P] Implement the wFrac-by-aspect rules, min/max key size clamps, vertical
  budget, and the portrait action-button-stacking rule (section 7).
- 6.2 [P] Verify on 1:1, 3:2, 4:3, 16:9, and portrait by overriding mWidth/
  mHeight or testing on representative panels.

Phase 7 - Per-language verification (depends on Phase 5)
- 7.1 [P] For each of QWERTY-US, QWERTY-EU (default), QWERTZ (de), AZERTY (fr),
  and the diacritic layouts (es, da, fi, sv, nb, et, ca, en_GB pound, en_IN
  rupee), confirm the visible key set, the SYM currency key, and the accent
  popups match report 1 section 3.
- 7.2 [P] Confirm fallback locales (ru, ja, ko, zh, ar, pt, pl, tr, it_IT,
  nl_NL) all render qwerty_eu/sym_eu.
- 7.3 Confirm Arabic typed text limitation (unshaped, logical order) is the
  documented known risk, not a regression (report 6 section 4).

Phase 8 - Final pass
- 8.1 Confirm render-order (OSK last overlay) and input-gate ordering unchanged.
- 8.2 Confirm the four keep-stable entry points and all `mOsk*` external
  read/write sites behave identically (report 5 section 5.5 checklist).

--------------------------------------------------------------------------------

## 12. Risks and known limitations

- Arabic (and any RTL) typed text renders unshaped and in logical (left-to-right)
  order: `drawText` has no HarfBuzz shaping and no BiDi (report 6 section 4).
  Isolated Arabic KEY faces render acceptably; the typed BUFFER does not. True
  Arabic support is a renderer-wide change, out of scope for a 1:1 Leanback port
  (Leanback itself gives Arabic only the Latin fallback anyway).
- CJK / non-Latin INPUT METHODS are not part of Leanback and not part of this
  port; those locales get the Latin fallback keyboard, exactly like stock
  Leanback.
- The geometric navigation must hit-test real key rects (not Leanback's hard
  magic indices 46/53/6) because the new geometry is fraction-based; getting the
  remembered-column and edge rules right is the trickiest behavioral parity item.
- The `mOskCursorX/Y` external pokes are the one coupling that the new geometry
  breaks; the handler-body edits in `NanoMenuInput.cpp` / `NanoMenuXmb.cpp` are
  mandatory and must be done carefully to preserve hold-to-repeat and the
  volume/brightness/power-before-OSK ordering.
- Long-press and key auto-repeat must be implemented with Nano-side timers
  (evdev gives no Android long-press / auto-repeat); the accent popup trigger and
  held-A-on-Delete/Left/Right depend on this.
- Adding a real caret (LEFT/RIGHT keys + L1/R1) changes the current append-only
  buffer behavior; if caret editing is descoped, the bottom-row Left/Right keys
  and L1/R1 become no-ops (a visible deviation to confirm with the product owner).
- Button-map divergence from Leanback (Nano keeps Y=toggle, L1=shift, adds
  R1=sym) is an intentional consistency choice with the existing Nano OSK help
  text; confirm this is acceptable rather than matching Leanback's exact
  X=del/Y=space/L1=left/R1=right map.
