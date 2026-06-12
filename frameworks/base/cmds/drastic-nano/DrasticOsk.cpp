/*
 * Copyright (C) 2026 GammaOS
 *
 * See DrasticOsk.h. Faithful port of the GammaOS Nano PS3-XMB keyboard
 * (NanoOsk) onto OverlayGfx for the drastic-nano in-game overlay.
 */

#include "DrasticOsk.h"

#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <string>

// The exact Leanback-derived key tables. static const, so this is the single
// TU in drastic-nano that pulls them in (no ODR conflict).
#include "NanoOskLayouts.h"
#include "NanoI18n.h"   // trDyn() shared nano UI translations

namespace android {
namespace drastic_overlay {

using drastic_gfx::Color;
using drastic_gfx::rgba;

namespace {

// Leanback grid constants (mirrors NanoOsk.cpp).
constexpr float kBoxAspect = 2.488372f;   // 428:172
constexpr float kKeyFrac   = 0.065421f;   // 28/428 (one key cell)
constexpr float kFontCharH = 16.0f;       // Nano font cell height (FONT_CHAR_H)
constexpr int64_t kDoubleTapMs = 250;     // shift double-tap -> caps lock

// PS3-XMB OSK palette.
const Color kPanelBg = rgba(0.12f, 0.14f, 0.18f, 1.0f);
const Color kCapBg   = rgba(0.16f, 0.19f, 0.24f, 1.0f);
const Color kCapFoc  = rgba(0.96f, 0.97f, 0.99f, 0.97f);
const float kAccR = 0.27f, kAccG = 0.52f, kAccB = 0.96f;
const Color kTxt    = rgba(0.88f, 0.90f, 0.95f, 1.0f);
const Color kFocTxt = rgba(0.08f, 0.10f, 0.14f, 1.0f);

int64_t nowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

std::string utf8Encode(uint32_t cp) {
    std::string s;
    if (cp < 0x80) {
        s += (char)cp;
    } else if (cp < 0x800) {
        s += (char)(0xC0 | (cp >> 6));
        s += (char)(0x80 | (cp & 0x3F));
    } else if (cp < 0x10000) {
        s += (char)(0xE0 | (cp >> 12));
        s += (char)(0x80 | ((cp >> 6) & 0x3F));
        s += (char)(0x80 | (cp & 0x3F));
    } else {
        s += (char)(0xF0 | (cp >> 18));
        s += (char)(0x80 | ((cp >> 12) & 0x3F));
        s += (char)(0x80 | ((cp >> 6) & 0x3F));
        s += (char)(0x80 | (cp & 0x3F));
    }
    return s;
}

bool isCont(unsigned char c) { return (c & 0xC0) == 0x80; }

int utf8PrevStart(const std::string& s, int caret) {
    int i = caret - 1;
    while (i > 0 && isCont((unsigned char)s[i])) i--;
    return i < 0 ? 0 : i;
}

int utf8NextStart(const std::string& s, int caret) {
    int i = caret + 1;
    int n = (int)s.size();
    while (i < n && isCont((unsigned char)s[i])) i++;
    return i > n ? n : i;
}

uint32_t toUpperCp(uint32_t cp) {
    if (cp >= 'a' && cp <= 'z') return cp - 'a' + 'A';
    return cp;   // accents/popups are deferred, so ASCII fold is sufficient
}

// Mirror of DrasticOsk::Shift values (the enum is a private member, so this
// free function takes an int and uses the literal locked value).
constexpr int kShiftLocked = 2;

const char* glyphFor(OskGlyph g, int shift) {
    switch (g) {
        case GLYPH_SHIFT_OFF: return (shift == kShiftLocked) ? "\xE2\x87\xAA"   // U+21EA
                                                             : "\xE2\x87\xA7";  // U+21E7
        case GLYPH_LEFT:      return "\xE2\x86\x90";   // U+2190
        case GLYPH_RIGHT:     return "\xE2\x86\x92";   // U+2192
        case GLYPH_SYMBOLS:   return "?!#";
        case GLYPH_ALPHABET:  return "ABC";
        default:              return "";
    }
}

} // anonymous namespace

const void* DrasticOsk::currentKb() const {
    int id = (mPage == PAGE_SYM) ? OSK_KB_SYM_US : OSK_KB_QWERTY_US;
    return &kOskKb[id];
}

const char* DrasticOsk::actionLabel() const {
    return trDyn("Search");
}

void DrasticOsk::open(const std::string& title, const std::string& initial,
                      Mode mode, std::function<void(const std::string&)> onCommit) {
    mActive   = true;
    mTitle    = title;
    mBuffer   = initial;
    mCaret    = (int)initial.size();
    mMode     = mode;
    mPage     = PAGE_ABC;
    mShift    = SHIFT_OFF;
    mFocusRow = 1;   // top letter row
    mFocusCol = 0;
    mInAction = false;
    mRememberedX = -1.0f;
    mLastShiftMs = 0;
    mOnCommit = std::move(onCommit);
}

void DrasticOsk::close() {
    mActive = false;
    mKeyRects.clear();
    mLastVw = mLastVh = 0.0f;
    mOnCommit = nullptr;
}

void DrasticOsk::commit() {
    auto cb = mOnCommit;
    std::string out = mBuffer;
    close();
    if (cb) cb(out);
}

// ---------------------------------------------------------------------------
// Geometry (port of oskComputeBox / keyRect)
// ---------------------------------------------------------------------------

DrasticOsk::Box DrasticOsk::computeBox(float W, float H, float actLabelPx) const {
    // The bottom DS screen is dedicated to the keyboard, so fill it: panel spans
    // the whole screen, the Search button is a single button in the TOP-RIGHT
    // next to the preview field, and the grid fills the FULL width below.
    Box b;
    float sf = fminf(W / 1080.0f, H / 720.0f);
    if (sf < 0.5f) sf = 0.5f;
    b.sf = sf;
    b.actBelow = false;

    float margin   = 8.0f * sf;
    float panelPad = 8.0f * sf;
    float rowGap   = 6.0f * sf;
    float previewH = kFontCharH * 2.0f * sf + 8.0f * sf;
    float footerH  = kFontCharH * 1.3f * sf + 4.0f * sf;
    float actPadH  = 14.0f * sf;

    b.panelX = margin;
    b.panelY = margin;
    b.panelW = W - 2.0f * margin;
    b.panelH = H - 2.0f * margin;

    float innerX = b.panelX + panelPad;
    float innerW = b.panelW - 2.0f * panelPad;

    b.previewX = innerX;
    b.previewY = b.panelY + panelPad;
    b.actW = actLabelPx + 2.0f * actPadH;
    b.actX = innerX + innerW - b.actW;

    // Grid: full width, between the top field strip and the footer; the key
    // height is capped near square, so the grid is centred in the leftover
    // vertical space.
    float gridTop = b.previewY + previewH + rowGap;
    b.footerY = b.panelY + b.panelH - panelPad - footerH;
    float gridBottom = b.footerY - rowGap;
    b.kbX = innerX;
    b.kbW = innerW;
    float availH = gridBottom - gridTop;
    float maxKbH = b.kbW * (1.0f / kBoxAspect) * 1.40f;
    b.kbH = fminf(availH, maxKbH);
    b.kbY = gridTop + (availH - b.kbH) / 2.0f;
    if (b.kbW < 1.0f) b.kbW = 1.0f;
    if (b.kbH < 1.0f) b.kbH = 1.0f;

    // Search button fills the whole top field strip: from the top of the panel
    // content down to just above the grid (uses the full available height).
    b.actY = b.previewY;
    b.actH = (b.kbY - rowGap) - b.previewY;
    if (b.actH < previewH) b.actH = previewH;
    return b;
}

void DrasticOsk::keyRect(const Box& b, float fx, float fw, float fy, float fh,
                         Rect* out) const {
    // Lay the fraction grid out in a box inset by half an inter-key gap on each
    // side, so that when render() grows each cap back out by that half-gap the
    // EDGE keys end up the same size as the interior ones (no squashing) and
    // the whole grid still fills [kbX..kbX+kbW] x [kbY..kbY+kbH].
    float exH = 0.0140185f * b.kbW;
    float eyV = 0.0232556f * b.kbH;
    float lbX = b.kbX + exH, lbW = b.kbW - 2.0f * exH;
    float lbY = b.kbY + eyV, lbH = b.kbH - 2.0f * eyV;
    out->x = lbX + fx * lbW;
    out->y = lbY + fy * lbH;
    out->w = fw * lbW;
    out->h = fh * lbH;
}

void DrasticOsk::clampFocus() {
    const OskKeyboard* kb = (const OskKeyboard*)currentKb();
    if (mFocusRow < 0) mFocusRow = 0;
    if (mFocusRow >= kb->rowCount) mFocusRow = kb->rowCount - 1;
    int n = kb->rows[mFocusRow].keyCount;
    if (mFocusCol < 0) mFocusCol = 0;
    if (mFocusCol >= n) mFocusCol = n - 1;
}

// ---------------------------------------------------------------------------
// Navigation (port of oskMoveCursor: geometric nearest-in-direction)
// ---------------------------------------------------------------------------

void DrasticOsk::moveCursor(int dx, int dy) {
    if (!mActive) return;
    if (dy < 0)      moveFocus(0);   // Up
    else if (dy > 0) moveFocus(1);   // Down
    else if (dx < 0) moveFocus(2);   // Left
    else if (dx > 0) moveFocus(3);   // Right
}

void DrasticOsk::moveFocus(int dir) {
    const OskKeyboard* kb = (const OskKeyboard*)currentKb();
    clampFocus();
    Box b = (mLastVw > 0.0f) ? mBox : computeBox(640.0f, 480.0f, 80.0f);

    if (mInAction) {
        // Search button is top-right: Left or Down returns to the grid.
        if (dir == 2 || dir == 1) mInAction = false;
        return;
    }

    const OskRow& row = kb->rows[mFocusRow];
    const OskKey& cur = row.keys[mFocusCol];

    // Horizontal: move within the row by index (robust for the wide space bar).
    if (dir == 2) {                                  // Left
        if (mFocusCol > 0) mFocusCol--;
    } else if (dir == 3) {                           // Right
        if (cur.edgeRight || mFocusCol >= row.keyCount - 1) {
            mInAction = true; return;                // off the right edge -> Search
        }
        mFocusCol++;
    } else {                                         // Up / Down
        int targetRow = (dir == 0) ? mFocusRow - 1 : mFocusRow + 1;
        if (dir == 0 && targetRow < 0) { mInAction = true; return; }  // Up off top -> Search
        if (dir == 1 && targetRow >= kb->rowCount) return;            // Down off bottom

        Rect cr; keyRect(b, cur.fx, cur.fw, row.fy, row.fh, &cr);
        float probeX = (cur.code == 32 && mRememberedX >= 0.0f)
                       ? mRememberedX : (cr.x + cr.w / 2.0f);
        const OskRow& tr = kb->rows[targetRow];
        int bestC = 0; float bestD = 1e18f;
        for (int c = 0; c < tr.keyCount; c++) {
            Rect kr; keyRect(b, tr.keys[c].fx, tr.keys[c].fw, tr.fy, tr.fh, &kr);
            float dx = (probeX < kr.x) ? (kr.x - probeX)
                     : (probeX > kr.x + kr.w) ? (probeX - (kr.x + kr.w)) : 0.0f;
            if (dx < bestD) { bestD = dx; bestC = c; }
        }
        mFocusRow = targetRow;
        mFocusCol = bestC;
    }

    // Remember the column centre for vertical travel across the wide space bar.
    const OskKey& nk = kb->rows[mFocusRow].keys[mFocusCol];
    if (nk.code != 32) {
        Rect nr; keyRect(b, nk.fx, nk.fw, kb->rows[mFocusRow].fy,
                         kb->rows[mFocusRow].fh, &nr);
        mRememberedX = nr.x + nr.w / 2.0f;
    }
}

// ---------------------------------------------------------------------------
// Editing
// ---------------------------------------------------------------------------

void DrasticOsk::insertCp(unsigned cp) {
    std::string enc = utf8Encode(cp);
    if (mBuffer.size() + enc.size() > 256) return;
    if (mCaret < 0) mCaret = 0;
    if (mCaret > (int)mBuffer.size()) mCaret = (int)mBuffer.size();
    mBuffer.insert((size_t)mCaret, enc);
    mCaret += (int)enc.size();
}

void DrasticOsk::pressKeyAt(int r, int c) {
    const OskKeyboard* kb = (const OskKeyboard*)currentKb();
    if (r < 0 || r >= kb->rowCount) return;
    if (c < 0 || c >= kb->rows[r].keyCount) return;
    const OskKey& key = kb->rows[r].keys[c];
    switch (key.code) {
        case OSK_SHIFT:       toggleShift(); return;
        case OSK_MODE_CHANGE: toggleSym();   return;
        case OSK_LEFT:
            if (mCaret > 0) mCaret = utf8PrevStart(mBuffer, mCaret);
            return;
        case OSK_RIGHT:
            if (mCaret < (int)mBuffer.size()) mCaret = utf8NextStart(mBuffer, mCaret);
            return;
        case OSK_DELETE:      onBackspace(); return;
        case OSK_CAPS_LOCK:
            mShift = (mShift == SHIFT_LOCKED) ? SHIFT_OFF : SHIFT_LOCKED;
            return;
        default: break;
    }
    if (key.code > 0) {
        uint32_t cp = (uint32_t)key.code;
        if (key.caseFoldable && mShift != SHIFT_OFF) cp = toUpperCp(cp);
        insertCp(cp);
        if (mShift == SHIFT_ON) mShift = SHIFT_OFF;   // one-shot
    }
}

void DrasticOsk::activate() {
    if (!mActive) return;
    if (mInAction) { commit(); return; }
    clampFocus();
    pressKeyAt(mFocusRow, mFocusCol);
}

void DrasticOsk::onBackspace() {
    if (!mActive) return;
    if (mCaret <= 0 || mBuffer.empty()) {
        // Nothing to delete: B on an empty buffer cancels the keyboard.
        if (mBuffer.empty()) close();
        return;
    }
    if (mCaret > (int)mBuffer.size()) mCaret = (int)mBuffer.size();
    int start = utf8PrevStart(mBuffer, mCaret);
    mBuffer.erase((size_t)start, (size_t)(mCaret - start));
    mCaret = start;
}

void DrasticOsk::toggleShift() {
    int64_t now = nowMs();
    if (now - mLastShiftMs < kDoubleTapMs)      mShift = SHIFT_LOCKED;
    else if (mShift == SHIFT_OFF)               mShift = SHIFT_ON;
    else                                        mShift = SHIFT_OFF;
    mLastShiftMs = now;
}

void DrasticOsk::toggleSym() {
    mPage = (mPage == PAGE_ABC) ? PAGE_SYM : PAGE_ABC;
    mInAction = false;
    clampFocus();
}

// ---------------------------------------------------------------------------
// Touch
// ---------------------------------------------------------------------------

int DrasticOsk::hitTest(float nx, float ny, int* r, int* c) const {
    if (mLastVw <= 0.0f || mLastVh <= 0.0f) return 0;
    const float px = nx * mLastVw;
    const float py = ny * mLastVh;
    for (int ri = 0; ri < (int)mKeyRects.size(); ri++) {
        for (int ci = 0; ci < (int)mKeyRects[ri].size(); ci++) {
            const Rect& kr = mKeyRects[ri][ci];
            if (px >= kr.x && px < kr.x + kr.w && py >= kr.y && py < kr.y + kr.h) {
                *r = ri; *c = ci; return 1;
            }
        }
    }
    if (px >= mActRect.x && px < mActRect.x + mActRect.w &&
        py >= mActRect.y && py < mActRect.y + mActRect.h) {
        return 2;
    }
    return 0;
}

void DrasticOsk::touchMove(float nx, float ny) {
    if (!mActive) return;
    int r, c;
    int hit = hitTest(nx, ny, &r, &c);
    if (hit == 1) { mInAction = false; mFocusRow = r; mFocusCol = c; }
    else if (hit == 2) { mInAction = true; }
}

void DrasticOsk::touchTap(float nx, float ny) {
    if (!mActive) return;
    int r, c;
    int hit = hitTest(nx, ny, &r, &c);
    if (hit == 1) {
        mInAction = false;
        mFocusRow = r; mFocusCol = c;
        pressKeyAt(r, c);
    } else if (hit == 2) {
        commit();
    }
}

// ---------------------------------------------------------------------------
// Render (port of renderOsk, minus popups / IME / candidates / fade)
// ---------------------------------------------------------------------------

void DrasticOsk::render(drastic_gfx::OverlayGfx& gfx) {
    if (!mActive) return;
    const float vw = (float)gfx.viewportW();
    const float vh = (float)gfx.viewportH();
    const float basePx = (float)gfx.fontBasePx();

    // Pixel-height text helpers (OverlayGfx scale = pxH / base px).
    auto TP = [&](const char* s, float x, float y, float pxH, Color c) {
        return gfx.text(s, x, y, pxH / basePx, c);
    };
    auto MP = [&](const char* s, float pxH) {
        return gfx.measure(s, pxH / basePx);
    };

    const OskKeyboard* kb = (const OskKeyboard*)currentKb();
    clampFocus();

    const char* actLabel = actionLabel();
    float sf0 = fminf(vw / 1080.0f, vh / 720.0f);
    if (sf0 < 0.5f) sf0 = 0.5f;
    float actLabelPx = MP(actLabel, 26.0f * sf0);
    Box b = computeBox(vw, vh, actLabelPx);
    mBox = b;
    mLastVw = vw;
    mLastVh = vh;
    const float sf = b.sf;

    const float panelRad = 18.0f * sf;
    const float gpx = 2.0f * sf;                 // residual gap between caps
    // Half the (uniform) Leanback inter-key gaps; caps grow into them.
    const float exH = 0.0140185f * b.kbW;        // (0.093458 - 0.065421) / 2
    const float eyV = 0.0232556f * b.kbH;        // (0.209302 - 0.162791) / 2
    const float gridR = b.kbX + b.kbW, gridB = b.kbY + b.kbH;

    gfx.roundedRect(b.panelX, b.panelY, b.panelW, b.panelH, panelRad, kPanelBg);

    // Preview line: label (cyan) + value with a blinking caret.
    {
        const Color accent = rgba(0.45f, 0.78f, 1.0f, 1.0f);
        float ps = 30.0f * sf;                   // preview glyph height
        float x = b.previewX;
        float y = b.previewY + (b.actH - ps) * 0.5f;   // centre in the field strip
        if (!mTitle.empty()) {
            std::string lbl = std::string(trDyn(mTitle.c_str())) + ": ";
            x += TP(lbl.c_str(), x, y, ps, accent);
        }
        int caret = mCaret;
        if (caret < 0) caret = 0;
        if (caret > (int)mBuffer.size()) caret = (int)mBuffer.size();
        std::string before = mBuffer.substr(0, caret);
        std::string after  = mBuffer.substr(caret);
        float xAfterPre = x + TP(before.c_str(), x, y, ps, rgba(1, 1, 1, 1));
        mBlinkTick++;
        float blink = 0.55f + 0.45f * sinf(mBlinkTick * 0.12f);
        gfx.fillRect(xAfterPre, y, 2.5f * sf, ps, rgba(1, 1, 1, blink));
        if (!after.empty())
            TP(after.c_str(), xAfterPre + 4.0f * sf, y, ps, rgba(1, 1, 1, 1));
    }

    // Centred text inside a cap, auto-shrunk to fit the width.
    auto capText = [&](const char* s, float bx, float by, float bw, float bh,
                       float pxH, Color c) {
        float maxW = bw * 0.86f;
        float tw = MP(s, pxH);
        if (tw > maxW && tw > 0.0f) { pxH *= maxW / tw; tw = MP(s, pxH); }
        TP(s, bx + (bw - tw) / 2.0f, by + (bh - pxH) / 2.0f, pxH, c);
    };

    // Key caps.
    mKeyRects.assign(kb->rowCount, {});
    for (int r = 0; r < kb->rowCount; r++) {
        const OskRow& row = kb->rows[r];
        mKeyRects[r].resize(row.keyCount);
        for (int c = 0; c < row.keyCount; c++) {
            const OskKey& key = row.keys[c];
            Rect cell; keyRect(b, key.fx, key.fw, row.fy, row.fh, &cell);

            // Grow the cell into half of each inter-key gap, clamped to the
            // grid box, so the keys are big and the gaps tight. This expanded
            // rect is also the touch hit rect (no dead zones).
            float hx = cell.x - exH, hy = cell.y - eyV;
            float hr = cell.x + cell.w + exH, hb = cell.y + cell.h + eyV;
            if (hx < b.kbX) hx = b.kbX;
            if (hy < b.kbY) hy = b.kbY;
            if (hr > gridR) hr = gridR;
            if (hb > gridB) hb = gridB;
            mKeyRects[r][c] = Rect{hx, hy, hr - hx, hb - hy};

            bool focused = (!mInAction && r == mFocusRow && c == mFocusCol);
            float bx = hx + gpx, by = hy + gpx;
            float bw = (hr - hx) - 2 * gpx, bh = (hb - hy) - 2 * gpx;
            float keyRad = 0.16f * fminf(bw, bh);
            bool isAccentShift = (key.code == OSK_SHIFT && mShift != SHIFT_OFF);
            Color cap = focused ? kCapFoc
                      : (isAccentShift ? rgba(kAccR, kAccG, kAccB, 0.85f) : kCapBg);
            gfx.roundedRect(bx, by, bw, bh, keyRad, cap);

            // Space bar: slim rounded bar.
            if (key.glyph == GLYPH_SPACE) {
                float barW = bw * 0.5f, barH = fmaxf(3.0f * sf, bh * 0.07f);
                Color tc = focused ? kFocTxt : kTxt;
                gfx.roundedRect(bx + (bw - barW) / 2.0f, by + bh * 0.58f,
                                barW, barH, barH * 0.5f, tc);
                continue;
            }
            // Backspace: left arrowhead + rounded body + a cut-out X.
            if (key.glyph == GLYPH_DELETE) {
                Color tc = focused ? kFocTxt : kTxt;
                float cx = bx + bw / 2.0f, cy = by + bh / 2.0f, s = bh * 0.26f;
                gfx.triangle(cx - s * 0.95f, cy, cx - s * 0.15f, cy - s * 0.78f,
                             cx - s * 0.15f, cy + s * 0.78f, tc);
                gfx.roundedRect(cx - s * 0.15f, cy - s * 0.55f, s * 1.15f, s * 1.10f,
                                s * 0.22f, tc);
                float xh = s * 0.95f;
                float xw = MP("\xc3\x97", xh);
                TP("\xc3\x97", cx + s * 0.18f - xw / 2.0f, cy - xh / 2.0f, xh, cap);
                continue;
            }
            // Caret arrows + shift: drawn procedurally so they always render
            // (the OverlayGfx Roboto face may lack U+2190/U+2192/U+21E7).
            if (key.glyph == GLYPH_LEFT || key.glyph == GLYPH_RIGHT ||
                key.glyph == GLYPH_SHIFT_OFF) {
                Color tc = isAccentShift ? rgba(1, 1, 1, 1)
                                         : (focused ? kFocTxt : kTxt);
                float cx = bx + bw / 2.0f, cy = by + bh / 2.0f;
                float s = fminf(bw, bh) * 0.24f;
                if (key.glyph == GLYPH_LEFT) {
                    gfx.triangle(cx - s, cy, cx + s * 0.7f, cy - s,
                                 cx + s * 0.7f, cy + s, tc);
                } else if (key.glyph == GLYPH_RIGHT) {
                    gfx.triangle(cx + s, cy, cx - s * 0.7f, cy - s,
                                 cx - s * 0.7f, cy + s, tc);
                } else {  // shift: up-arrow head + stem (+ base bar when locked)
                    gfx.triangle(cx, cy - s, cx - s, cy + s * 0.15f,
                                 cx + s, cy + s * 0.15f, tc);
                    gfx.roundedRect(cx - s * 0.34f, cy + s * 0.15f,
                                    s * 0.68f, s * 0.85f, 1.0f * sf, tc);
                    if (mShift == SHIFT_LOCKED)
                        gfx.roundedRect(cx - s * 0.55f, cy + s * 1.08f,
                                        s * 1.10f, s * 0.28f, 1.0f * sf, tc);
                }
                continue;
            }

            // Text / glyph label, sized relative to the cap.
            std::string text;
            float pxH;
            Color tcol;
            if (key.glyph != GLYPH_NONE) {
                text = glyphFor(key.glyph, mShift);
                bool isMode = (key.glyph == GLYPH_SYMBOLS || key.glyph == GLYPH_ALPHABET);
                pxH = (isMode ? 0.34f : 0.52f) * bh;
                tcol = isAccentShift ? rgba(1, 1, 1, 1) : (focused ? kFocTxt : kTxt);
            } else {
                text = key.label ? key.label : "";
                if (key.caseFoldable && mShift != SHIFT_OFF && text.size() == 1 &&
                    text[0] >= 'a' && text[0] <= 'z') {
                    text[0] = (char)(text[0] - 'a' + 'A');
                }
                pxH = ((int)text.size() >= 2 ? 0.34f : 0.50f) * bh;
                tcol = focused ? kFocTxt : kTxt;
            }
            capText(text.c_str(), bx, by, bw, bh, pxH, tcol);

            // Digit-key superscript (US-style shifted symbol).
            if (key.code >= '0' && key.code <= '9' && key.glyph == GLYPH_NONE) {
                static const char kSup[] = ")!@#$%^&*(";
                char sup[2] = { kSup[key.code - '0'], 0 };
                float ss = 0.24f * bh;
                float sw = MP(sup, ss);
                Color sc = focused ? kFocTxt : rgba(0.60f, 0.64f, 0.72f, 0.85f);
                TP(sup, bx + bw - sw - 5.0f * sf, by + 4.0f * sf, ss, sc);
            }
        }
    }

    // Action button (Search / Enter): full-height accent key on the right.
    {
        mActRect = Rect{b.actX, b.actY, b.actW, b.actH};
        float keyRad = 0.16f * fminf(b.actW, b.actH);
        bool foc = mInAction;
        Color fill = foc ? kCapFoc : rgba(kAccR, kAccG, kAccB, 0.92f);
        gfx.roundedRect(b.actX, b.actY, b.actW, b.actH, keyRad, fill);
        Color tc = foc ? kFocTxt : rgba(1, 1, 1, 1);
        capText(actLabel, b.actX, b.actY, b.actW, b.actH, 30.0f * sf, tc);
    }

    // Footer hint.
    {
        float fpx = 19.0f * sf;
        const char* footer = trDyn(
            "A: Key   B: Back   L: Shift   R: Sym   Up/Right: Search");
        float fw = MP(footer, fpx);
        TP(footer, b.panelX + b.panelW / 2.0f - fw / 2.0f, b.footerY, fpx,
           rgba(0.58f, 0.60f, 0.68f, 0.95f));
    }
}

} // namespace drastic_overlay
} // namespace android
