/*
 * Copyright (C) 2026 GammaOS
 *
 * NanoSliderHud: the single source of truth for the in-app volume /
 * brightness slider HUD, so gammaos-nano (the PS3 XMB home) and
 * drastic-nano (the in-game overlay) render the exact same thing.
 *
 * The spec is the GammaOS "normal apps" slider that PhoneWindowManager
 * shows in nano mode (showNanoVolumeIndicator / showNanoBrightnessIndicator):
 * a flat 80%-black panel, top-centre, holding a white icon (speaker for
 * volume, sun for brightness), a thin white progress bar on a 25%-white
 * track, and a white "NN%" label. Auto-dismiss timing (~1.5s) is owned by
 * the caller.
 *
 * Both nano renderers expose the same three primitives (a filled rect, a
 * scaled text draw, and a text measure), so this header is templated over a
 * tiny Backend so the layout math, colours and icon geometry live in one
 * place. The icons are drawn procedurally (no PNG/vector assets) and the
 * whole thing is scaled off the viewport so it adapts to any panel size or
 * orientation, exactly like the WindowManager overlay scales off density.
 *
 * Backend concept (caller supplies a thin adapter):
 *   void  rect(float x, float y, float w, float h,
 *              float r, float g, float b, float a);   // top-left origin
 *   void  text(const char* s, float x, float y, float pxH,
 *              float r, float g, float b, float a);   // top-left of row
 *   float measure(const char* s, float pxH);          // advance width, px
 */

#pragma once

#include <cmath>
#include <cstdio>

namespace nano_slider {

enum Kind { kVolume = 0, kBrightness = 1 };

// All sizes are in "design units" multiplied by the viewport scale below.
// They mirror the dp values of the WindowManager overlay's layout.
struct Spec {
    // Panel
    static constexpr float kPadH      = 16.0f;  // panel horizontal padding
    static constexpr float kPadV      = 11.0f;  // panel vertical padding
    static constexpr float kTopMargin = 18.0f;  // gap from the top edge
    static constexpr float kStackGap  = 10.0f;  // gap between stacked panels
    // Content
    static constexpr float kIcon      = 26.0f;  // icon box (square)
    static constexpr float kIconGap   = 10.0f;  // icon -> bar gap
    static constexpr float kBarW      = 230.0f; // progress bar width
    static constexpr float kBarH      = 10.0f;  // progress bar height
    static constexpr float kBarGap    = 14.0f;  // bar -> label gap
    static constexpr float kText      = 20.0f;  // label pixel height
};

// Viewport scale: adaptive, matched to the existing nano HUD basis so the
// on-device size is familiar across panels (640x480 .. 1080p, any orientation).
inline float scaleFor(float vw, float vh) {
    float s = fminf(vw / 1080.0f, vh / 720.0f);
    if (s < 0.5f) s = 0.5f;
    if (s > 4.0f) s = 4.0f;
    return s;
}

// Panel height in pixels (content height + vertical padding). Constant for a
// given scale, so callers can stack volume/brightness deterministically.
inline float panelHeight(float vw, float vh) {
    const float s = scaleFor(vw, vh);
    return Spec::kIcon * s + Spec::kPadV * 2.0f * s;
}

// ---------------------------------------------------------------------------
// Procedural icons. Drawn white into the box [ix, iy, isz, isz] via be.rect.
// Geometry is transcribed from the WindowManager drawables: ic_audio_vol
// (48x48 speaker) and ic_gammaos_brightness (24x24 sun).
// ---------------------------------------------------------------------------

template <class Backend>
inline void drawSpeaker(Backend& be, float ix, float iy, float isz) {
    const float u = isz / 48.0f;   // viewport-unit -> pixels
    auto R = [&](float vx, float vy, float vw, float vh) {
        if (vw <= 0.0f || vh <= 0.0f) return;
        be.rect(ix + vx * u, iy + vy * u, vw * u, vh * u, 1.f, 1.f, 1.f, 1.f);
    };

    // Body + cone: polygon (6,18)-(6,30)-(14,30)-(24,40)-(24,8)-(14,18).
    // Right edge is x=24 throughout; the left edge is the cone diagonals on
    // top/bottom and the box edge (x=6) in the middle. Scanline over y.
    {
        const int rows = (int)fmaxf(6.0f, roundf(32.0f * u));
        const float step = 32.0f / (float)rows;   // viewport units per row
        for (int k = 0; k < rows; ++k) {
            float vyTop = 8.0f + k * step;
            float vyMid = vyTop + step * 0.5f;
            float left;
            if (vyMid < 18.0f)      left = 32.0f - vyMid;   // top cone diagonal
            else if (vyMid <= 30.0f) left = 6.0f;           // box span
            else                    left = vyMid - 16.0f;   // bottom cone diagonal
            R(left, vyTop, 24.0f - left, step);
        }
    }

    // Two sound-wave arcs to the right of the cone, as right-half annuli
    // centred on the cone mouth (14,24). Inner wave R 11..14, outer R 21..24.
    auto arc = [&](float rIn, float rOut, float yReach) {
        const int rows = (int)fmaxf(6.0f, roundf(2.0f * yReach * u));
        const float step = (2.0f * yReach) / (float)rows;
        for (int k = 0; k < rows; ++k) {
            float vyTop = (24.0f - yReach) + k * step;
            float dy = (vyTop + step * 0.5f) - 24.0f;
            float oo = rOut * rOut - dy * dy;
            if (oo <= 0.0f) continue;
            float outer = 14.0f + sqrtf(oo);
            float ii = rIn * rIn - dy * dy;
            float inner = (ii > 0.0f) ? (14.0f + sqrtf(ii)) : 25.0f;
            if (inner < 25.0f) inner = 25.0f;   // keep clear of the cone (x=24)
            R(inner, vyTop, outer - inner, step);
        }
    };
    arc(11.0f, 14.0f, 9.0f);    // inner (short) wave
    arc(21.0f, 24.0f, 18.0f);   // outer (long) wave
}

template <class Backend>
inline void drawSun(Backend& be, float ix, float iy, float isz) {
    const float u = isz / 24.0f;
    auto R = [&](float vx, float vy, float vw, float vh) {
        if (vw <= 0.0f || vh <= 0.0f) return;
        be.rect(ix + vx * u, iy + vy * u, vw * u, vh * u, 1.f, 1.f, 1.f, 1.f);
    };

    // Centre ring: outer radius 5, inner radius 3, centre (12,12). Scanline as
    // an annulus so the middle stays hollow like the drawable.
    {
        const int rows = (int)fmaxf(4.0f, roundf(10.0f * u));
        const float step = 10.0f / (float)rows;
        for (int k = 0; k < rows; ++k) {
            float vyTop = 7.0f + k * step;
            float dy = (vyTop + step * 0.5f) - 12.0f;
            float oo = 25.0f - dy * dy;
            if (oo <= 0.0f) continue;
            float outer = sqrtf(oo);
            float ii = 9.0f - dy * dy;
            if (ii > 0.0f) {
                float inner = sqrtf(ii);
                R(12.0f - outer, vyTop, outer - inner, step);   // left span
                R(12.0f + inner, vyTop, outer - inner, step);   // right span
            } else {
                R(12.0f - outer, vyTop, 2.0f * outer, step);    // solid row
            }
        }
    }

    // Four orthogonal rays (2x3 / 3x2 nubs from the drawable).
    R(11.0f, 1.0f, 2.0f, 3.0f);    // top
    R(11.0f, 20.0f, 2.0f, 3.0f);   // bottom
    R(1.0f, 11.0f, 3.0f, 2.0f);    // left
    R(20.0f, 11.0f, 3.0f, 2.0f);   // right
    // Four diagonal ray tips (small squares centred at the 45-degree points).
    R(3.5f, 3.5f, 3.0f, 3.0f);     // NW
    R(17.5f, 3.5f, 3.0f, 3.0f);    // NE
    R(3.5f, 17.5f, 3.0f, 3.0f);    // SW
    R(17.5f, 17.5f, 3.0f, 3.0f);   // SE
}

// ---------------------------------------------------------------------------
// The slider itself. slot 0 = top panel, slot 1 = stacked below it. pct is
// clamped to 0..100. Returns the panel height in pixels.
// ---------------------------------------------------------------------------

template <class Backend>
inline float draw(Backend& be, float vw, float vh, Kind kind, int pct, int slot) {
    if (pct < 0) pct = 0;
    if (pct > 100) pct = 100;
    const float s = scaleFor(vw, vh);

    const float padH = Spec::kPadH * s;
    const float padV = Spec::kPadV * s;
    const float icon = Spec::kIcon * s;
    const float iconGap = Spec::kIconGap * s;
    const float barW = Spec::kBarW * s;
    const float barH = Spec::kBarH * s;
    const float barGap = Spec::kBarGap * s;
    const float textPx = Spec::kText * s;

    char pctStr[8];
    snprintf(pctStr, sizeof(pctStr), "%d%%", pct);
    const float textW = be.measure(pctStr, textPx);

    const float contentH = icon;                         // tallest element
    const float bgH = contentH + padV * 2.0f;
    const float bgW = padH * 2.0f + icon + iconGap + barW + barGap + textW;
    const float bgX = (vw - bgW) * 0.5f;
    const float bgY = Spec::kTopMargin * s + slot * (bgH + Spec::kStackGap * s);

    // Panel: flat 80% black, no rounding (matches setBackgroundColor 0xCC000000).
    be.rect(bgX, bgY, bgW, bgH, 0.f, 0.f, 0.f, 0.8f);

    // Icon, vertically centred.
    const float iconX = bgX + padH;
    const float iconY = bgY + (bgH - icon) * 0.5f;
    if (kind == kVolume) drawSpeaker(be, iconX, iconY, icon);
    else                 drawSun(be, iconX, iconY, icon);

    // Progress bar: 25%-white track + white fill, vertically centred.
    const float barX = iconX + icon + iconGap;
    const float barY = bgY + (bgH - barH) * 0.5f;
    be.rect(barX, barY, barW, barH, 1.f, 1.f, 1.f, 0.25f);
    be.rect(barX, barY, barW * (float)pct / 100.0f, barH, 1.f, 1.f, 1.f, 1.f);

    // Label, vertically centred.
    const float textX = barX + barW + barGap;
    const float textY = bgY + (bgH - textPx) * 0.5f;
    be.text(pctStr, textX, textY, textPx, 1.f, 1.f, 1.f, 1.f);

    return bgH;
}

} // namespace nano_slider
