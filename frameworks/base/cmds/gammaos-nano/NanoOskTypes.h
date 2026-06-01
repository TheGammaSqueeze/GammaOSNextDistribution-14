/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Shared type definitions for the GammaOS Nano on-screen keyboard.
//
// These structs/enums are the contract between the GENERATED layout tables
// (NanoOskLayouts.h, produced by tools/osk/gen_osk_layouts.py from the
// LeanbackIME res/xml keyboards) and the runtime (NanoOsk.cpp). Geometry is
// stored as fractions of the keyboard's logical box so a single set of tables
// scales to any aspect ratio (1:1, 3:2, 4:3, 16:9) and to portrait.
//
// Do NOT hand-edit the generated layout data; edit the Leanback XML or the
// generator and re-run it. This header is hand-written and stable.

#ifndef GAMMAOS_NANO_OSK_TYPES_H
#define GAMMAOS_NANO_OSK_TYPES_H

namespace android {

// Special (negative) key codes, mirrored from Leanback / the AOSP
// android.inputmethodservice.Keyboard framework constants. Any positive code
// is a Unicode codepoint that commits as UTF-8.
enum OskCode : int {
    OSK_SHIFT        = -1,   // toggle shift (off <-> on; from locked -> off)
    OSK_MODE_CHANGE  = -2,   // toggle ABC <-> SYM page
    OSK_LEFT         = -3,   // move caret left
    OSK_RIGHT        = -4,   // move caret right
    OSK_DELETE       = -5,   // backspace one codepoint
    OSK_CAPS_LOCK    = -6,   // toggle caps lock
    OSK_VOICE        = -7,   // unused in Nano (no mic); never on a shipped key
    OSK_DISMISS_MINI = -8,   // dismiss accent/shift popup
};

// Which glyph/icon a key draws instead of (or in addition to) its label.
// Leanback uses PNG icons on these keys; Nano draws Unicode glyphs via
// drawText so no new PNG assets are required. SHIFT and the mode key are
// "markers": the actual glyph/text is chosen at draw time from the live
// shift state / current page.
enum OskGlyph : int {
    GLYPH_NONE = 0,     // draw key.label as text
    GLYPH_DELETE,       // backspace icon
    GLYPH_SHIFT_OFF,    // shift key marker: pick off/on/lock from mShift at draw
    GLYPH_SHIFT_ON,     // (reserved; markers normally use GLYPH_SHIFT_OFF)
    GLYPH_SHIFT_LOCK,   // (reserved)
    GLYPH_SPACE,        // space bar icon
    GLYPH_LEFT,         // left-arrow caret key
    GLYPH_RIGHT,        // right-arrow caret key
    GLYPH_SYMBOLS,      // mode key on ABC page: draws "?!#"
    GLYPH_ALPHABET,     // mode key on SYM page: draws "ABC"
    GLYPH_ACCENT_CLOSE, // close-popup affordance (unused on base grids)
};

// Stable identifiers for the shipped Leanback keyboards, in the SAME order the
// generator (tools/osk/gen_osk_layouts.py) emits kOskKb[]. The generated
// NanoOskLayouts.h static_asserts that OSK_KB_COUNT matches its array length,
// so this enum and the data cannot silently drift. This enum carries no data,
// so it is safe to include widely (via NanoOsk.h -> NanoMenu.h); the bulky
// kOskKb[] tables stay confined to the single TU that includes NanoOskLayouts.h.
enum OskKbId : int {
    OSK_KB_QWERTY_US = 0,
    OSK_KB_QWERTY_EU,
    OSK_KB_QWERTY_EN_GB,
    OSK_KB_QWERTY_EN_IN,
    OSK_KB_QWERTY_ES_EU,
    OSK_KB_QWERTY_ES_US,
    OSK_KB_QWERTY_AZ,
    OSK_KB_QWERTY_CA,
    OSK_KB_QWERTY_DA,
    OSK_KB_QWERTY_ET,
    OSK_KB_QWERTY_FI,
    OSK_KB_QWERTY_NB,
    OSK_KB_QWERTY_SV,
    OSK_KB_QWERTZ,
    OSK_KB_QWERTZ_CH,
    OSK_KB_AZERTY,
    OSK_KB_SYM_US,
    OSK_KB_SYM_EU,
    OSK_KB_SYM_EN_GB,
    OSK_KB_SYM_EN_IN,
    OSK_KB_SYM_AZERTY,
    OSK_KB_COUNT,
};

// One key. Geometry is fractions of the keyboard box [0..1]; fy/fh come from
// the owning row (all keys in a row share top/height).
struct OskKey {
    int         code;         // OskCode (negative) or Unicode codepoint
    const char* label;        // UTF-8 face text (may be ""); ignored if glyph!=NONE
    OskGlyph    glyph;        // GLYPH_NONE => draw label; else draw the icon glyph
    float       fx;           // left edge as fraction of keyboard width
    float       fw;           // width as fraction of keyboard width
    bool        edgeLeft;     // keyEdgeFlags="left"  (no LEFT-wrap past this)
    bool        edgeRight;    // keyEdgeFlags="right" (RIGHT exits to action button)
    int         popupIndex;   // index into kOskPopups[], or -1 for none
    bool        caseFoldable; // single letter: uppercases under shift
};

struct OskRow {
    float          fy;        // top edge as fraction of keyboard height
    float          fh;        // height as fraction of keyboard height
    bool           edgeTop;   // rowEdgeFlags="top"
    bool           edgeBottom;// rowEdgeFlags="bottom"
    const OskKey*  keys;
    int            keyCount;
};

struct OskKeyboard {
    const OskRow*  rows;
    int            rowCount;  // 5 for ABC/SYM grids
};

// An accent/shift popup is a degenerate single-row keyboard overlaid on the
// grid starting at the originating key's slot.
struct OskPopup {
    const OskKey*  keys;      // 1..11 cells
    int            keyCount;
};

} // namespace android

#endif // GAMMAOS_NANO_OSK_TYPES_H
