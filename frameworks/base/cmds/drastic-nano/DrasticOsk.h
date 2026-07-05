/*
 * Copyright (C) 2026 GammaOS
 *
 * On-screen keyboard for the drastic-nano in-game overlay. This is a faithful
 * port of the GammaOS Nano PS3-XMB keyboard (NanoOsk): it reuses the exact
 * Leanback-derived key tables (kOskKb[] from NanoOskLayouts.h, QWERTY US ABC +
 * SYM pages), the fraction-based geometry (oskComputeBox/keyRect), the
 * geometric nearest-in-direction navigation, the glyph keys (shift arrow,
 * backspace icon, space bar, mode key, caret arrows) and the side
 * Search/Enter action button. Rendered through OverlayGfx (rounded-rect +
 * triangle + text). Driven by the overlay's hold-to-repeat navigation and by
 * touch on the bottom DS panel.
 */

#pragma once

#include <functional>
#include <string>
#include <vector>

#include "OverlayGfx.h"

namespace android {
namespace drastic_overlay {

class DrasticOsk {
public:
    // Mode only affects the caption/validation hint; the layout is the Nano
    // QWERTY (ABC + SYM) for both. Hex entry types hex digits + spaces (the
    // AR-code parser splits on whitespace).
    enum class Mode { Text, Hex };

    void open(const std::string& title, const std::string& initial,
              Mode mode, std::function<void(const std::string&)> onCommit);
    void close();                       // deactivate without committing
    bool active() const { return mActive; }

    // Driven by OverlayMenu (which owns the hold-to-repeat scheduler).
    void moveCursor(int dx, int dy);    // geometric nearest-in-direction nav
    void activate();                    // A: press the focused key / action btn
    void onBackspace();                 // B: delete a char / cancel if empty
    void toggleShift();                 // L shortcut
    void toggleSym();                   // R shortcut
    void submit();                      // Start/Enter: commit the text

    // Touchscreen input on the bottom DS panel. (nx, ny) are normalized panel
    // coordinates in [0,1]. touchMove moves the focus to the key/button under
    // the finger; touchTap (press edge) focuses AND presses it.
    void touchMove(float nx, float ny);
    void touchTap(float nx, float ny);

    void render(drastic_gfx::OverlayGfx& gfx);

    const std::string& buffer() const { return mBuffer; }

private:
    enum Page  { PAGE_ABC = 0, PAGE_SYM = 1 };
    enum Shift { SHIFT_OFF = 0, SHIFT_ON = 1, SHIFT_LOCKED = 2 };

    // Computed panel geometry (logical surface pixels, top-left origin).
    struct Box {
        float sf = 1.0f;
        float kbX = 0, kbY = 0, kbW = 0, kbH = 0;
        float actX = 0, actY = 0, actW = 0, actH = 0;
        bool  actBelow = false;
        float previewX = 0, previewY = 0;
        float footerY = 0;
        float panelX = 0, panelY = 0, panelW = 0, panelH = 0;
    };
    struct Rect { float x = 0, y = 0, w = 0, h = 0; };

    const void* currentKb() const;      // const OskKeyboard*
    Box computeBox(float vw, float vh, float actLabelPx) const;
    void keyRect(const Box& b, float fx, float fw, float fy, float fh,
                 Rect* out) const;
    void clampFocus();
    void pressKeyAt(int r, int c);      // dispatch the key at row r, col c
    void insertCp(unsigned cp);
    void commit();                      // fire onCommit + close
    const char* actionLabel() const;
    // Geometric move helper (dir: 0=Up 1=Down 2=Left 3=Right).
    void moveFocus(int dir);
    // Touch hit-test against the cached rects. Returns 0 = nothing, 1 = a key
    // (fills *r/*c), 2 = the action button.
    int hitTest(float nx, float ny, int* r, int* c) const;

    bool mActive = false;
    Mode mMode = Mode::Text;
    std::string mTitle;
    std::string mBuffer;
    int   mCaret = 0;                   // byte offset into mBuffer
    Page  mPage = PAGE_ABC;
    Shift mShift = SHIFT_OFF;
    int   mFocusRow = 1;                // start on the top letter row
    int   mFocusCol = 0;
    bool  mInAction = false;
    float mRememberedX = -1.0f;         // column memory across the space bar
    int   mBlinkTick = 0;
    int64_t mLastShiftMs = 0;           // double-tap -> caps lock
    std::function<void(const std::string&)> mOnCommit;

    // Cached geometry from the last render(), for touch hit-testing.
    Box   mBox;
    std::vector<std::vector<Rect>> mKeyRects;  // parallel to current kb rows
    Rect  mActRect;
    float mLastVw = 0.0f;
    float mLastVh = 0.0f;
};

} // namespace drastic_overlay
} // namespace android
