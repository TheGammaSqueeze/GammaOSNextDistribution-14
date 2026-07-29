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

// Runtime state and helpers for the GammaOS Nano on-screen keyboard, a native
// reimplementation of the Android TV Leanback IME keyboard extended into a
// multi-script input method (Latin + CJK + Arabic/RTL).
//
// The behavior methods (renderOsk, oskMoveCursor, oskActivateKey, ...) are
// declared on the NanoMenu class in NanoMenu.h so they can reuse drawText /
// drawQuad / measureText / mWidth / mHeight without friend plumbing. This
// header defines the value types those methods operate on: NanoOskState (all
// runtime keyboard state) and the per-script OskInputMethod plugin interface.
//
// Layout DATA lives in the generated NanoOskLayouts.h (do not hand-edit).

#ifndef GAMMAOS_NANO_OSK_H
#define GAMMAOS_NANO_OSK_H

#include <cstdint>
#include <string>
#include <vector>

#include "NanoOskTypes.h"

namespace android {

// Which page of the active layout is showing.
enum OskPage : int { PAGE_ABC = 0, PAGE_SYM = 1 };

// Shift state machine (no separate uppercase layout; labels are case-folded).
enum OskShift : int { SHIFT_OFF = 0, SHIFT_ON = 1, SHIFT_LOCKED = 2 };

// Text direction of the committed buffer for the active script.
enum OskScriptDir : int { OSK_LTR = 0, OSK_RTL = 1 };

// Resolved layout pair (indices into kOskKb[] from NanoOskLayouts.h).
struct OskLayoutChoice {
    int abcId;   // OskKbId of the ABC keyboard
    int symId;   // OskKbId of the SYM keyboard
};

// Computed on-screen geometry for the keyboard panel (logical surface pixels,
// top-left origin). Produced by NanoMenu::oskLayoutBox(); consumed by the
// renderer and the navigation hit-test so both agree on key rects.
struct OskBox {
    float sf;                       // font/padding scale factor
    float kbX, kbY, kbW, kbH;       // keyboard grid rect
    float keyPx;                    // one key-cell width in pixels
    float actX, actY, actW, actH;   // action (Done/Search) button rect
    bool  actBelow;                 // action button stacked below grid (portrait)
    float previewX, previewY;       // top-left of the text-preview line
    float footerY;                  // baseline y for the help footer
    float panelX, panelY, panelW, panelH; // full translucent panel background
    bool  portrait;
};

// ---------------------------------------------------------------------------
// Per-script input method plugin (Phase B and later).
//
// Latin/Cyrillic/Greek/Thai use the trivial direct path (no engine): a key
// codepoint commits straight into the buffer. Complex scripts (Hangul, Pinyin,
// Japanese) install an OskInputMethod that accumulates a COMPOSING region and
// produces CANDIDATES, committing only when the user picks one. Arabic/Hebrew
// commit directly but mark the buffer RTL (shaping/ordering happens at render).
//
// The keyboard owns the committed buffer; the engine owns its composing state
// and mirrors it via composingText(). onCodepoint returns true if it consumed
// the key (composing), false to let the keyboard commit the codepoint directly.
// ---------------------------------------------------------------------------
struct OskBuffer {
    std::string* text;   // committed buffer (NanoMenu::mOskQuery)
    int*         caret;  // caret byte offset into *text
};

struct OskInputMethod {
    virtual ~OskInputMethod() {}
    virtual OskScriptDir direction() const { return OSK_LTR; }
    virtual bool wantsCandidates() const { return false; }

    // Feed one key codepoint. Return true if consumed into composing state,
    // false to let the keyboard insert it directly.
    virtual bool onCodepoint(uint32_t cp, OskBuffer& buf) = 0;
    // Backspace while composing. Return true if it edited composing state,
    // false to let the keyboard delete from the committed buffer.
    virtual bool onBackspace(OskBuffer& buf) = 0;

    virtual const std::vector<std::string>& candidates() const = 0;
    virtual void chooseCandidate(int index, OskBuffer& buf) = 0;
    virtual std::string composingText() const { return std::string(); }

    // Commit whatever is composing (e.g. on space/enter) and clear it.
    virtual void commitComposing(OskBuffer& /*buf*/) {}
    virtual void reset() {}
};

// ---------------------------------------------------------------------------
// All runtime keyboard state. One instance lives on NanoMenu (mOsk). The
// keep-stable external members (mOskActive, mOskQuery, mOskPasswordMode,
// mOskPlaintext, mSearch*) stay as NanoMenu members so existing callers keep
// compiling; everything NEW lives here.
// ---------------------------------------------------------------------------
struct NanoOskState {
    // --- Active layout (pointers so non-registry scripts like Korean fit) ---
    const OskKeyboard* abcKb = nullptr;      // ABC page keyboard
    const OskKeyboard* symKb = nullptr;      // SYM page keyboard
    OskPage  page        = PAGE_ABC;

    // --- Shift ---
    OskShift shift            = SHIFT_OFF;
    int64_t  lastShiftClickMs = 0;           // for double-click caps detection

    // --- Focus (row/col into the current page's grid) ---
    int      focusRow = 0, focusCol = 0;
    float    rememberedX = -1.0f;            // remembered key center-x (pixels)
                                             // for vertical moves over the space bar

    // --- Animated selector rectangle (pixels), lerps toward the focus rect ---
    bool     selInit     = false;
    float    selX = 0, selY = 0, selW = 0, selH = 0;
    float    pressScale  = 1.0f;             // click squash 1.0 -> 0.88 -> 1.0
    int64_t  pressStartMs = 0;

    // --- Accent / shift popup overlay ---
    bool     miniOpen    = false;
    int      miniPopupIndex = -1;            // index into kOskPopups[]
    int      miniOriginRow = 0, miniOriginCol = 0;
    int      miniBaseCol = 0;                // grid column the popup run starts at
    int      miniFocus   = 0;                // focused cell within the popup

    // --- Long-press (popup trigger) timing ---
    int64_t  aDownMs     = 0;                // when A pressed (0 = not held)
    bool     aLongFired  = false;            // popup already opened for this hold
    // Latched focus at the moment A went down. Every letter key is "deferred"
    // (it commits on release, not press, so a hold can open the accent popup),
    // so the committed key MUST be the one under the cursor when A was pressed,
    // NOT wherever the cursor drifted to by release. Fast typing interleaves the
    // move-to-next-key d-pad event ahead of the current key's A-release, which
    // otherwise commits the wrong (or a stray extra) letter. -1 = not latched.
    int      aPressRow   = -1, aPressCol = -1;

    // --- Caret (byte offset into the committed buffer) ---
    int      caret       = 0;

    // --- Language switch (curated cycle) ---
    int      langCycleIndex = -1;            // -1 = follow UI locale
    char     langLabel[8]   = "EN";          // short script badge (e.g. EN, FR, 한)

    // --- Composition / candidates (Phase B+; inert for direct Latin) ---
    OskScriptDir          dir = OSK_LTR;     // committed-buffer direction
    bool     arabicShape  = false;           // shape buffer to Arabic presentation forms
    OskInputMethod*       im  = nullptr;     // active engine, or nullptr (direct)
    std::vector<std::string> candidates;     // mirror of im->candidates()
    int      candFocus       = -1;           // focused candidate index
    bool     inCandidateBar  = false;        // D-pad focus is on the candidate bar
    bool     inAction        = false;        // D-pad focus is on the Done/Search button

    // Show/hide fade transition.
    float    anim            = 0.0f;         // 0 = hidden, 1 = fully shown
    bool     closing         = false;        // fading out toward hide

    void resetForOpen() {
        page = PAGE_ABC;
        shift = SHIFT_OFF;
        lastShiftClickMs = 0;
        focusRow = 0; focusCol = 0;
        rememberedX = -1.0f;
        selInit = false;
        pressScale = 1.0f; pressStartMs = 0;
        miniOpen = false; miniPopupIndex = -1; miniFocus = 0;
        aDownMs = 0; aLongFired = false;
        aPressRow = -1; aPressCol = -1;
        caret = 0;
        candidates.clear(); candFocus = -1; inCandidateBar = false;
        inAction = false;
        anim = 0.0f; closing = false;
    }
};

// Map an ISO language code + region to a Leanback layout pair. Implements the
// full LeanbackKeyboardContainer.initKeyboards() first-match-wins chain.
// Returns qwerty_eu / sym_eu for anything unmatched.
OskLayoutChoice oskPickLayout(const char* code, const char* region);

} // namespace android

#endif // GAMMAOS_NANO_OSK_H
