// DsScreenLayout.cpp
//
// Implements the single-view DS screen layouts. See DsScreenLayout.h for the
// model. The presets and their rectangle math match the advanced_drastic build
// so a user who knows that layout picker sees the same arrangements here.

#include "DsScreenLayout.h"

#include <algorithm>
#include <cmath>

namespace drastic_nano {
namespace {

// Rounds a rectangle to whole pixels. The viewport and scissor that consume a
// rectangle take integers, so rounding here keeps each screen aligned to exact
// texels instead of straddling a pixel boundary.
Rect roundRect(Rect r) {
    r.x = std::round(r.x);
    r.y = std::round(r.y);
    r.w = std::round(r.w);
    r.h = std::round(r.h);
    return r;
}

// Uniform-fit a content box of CW x CH into a W x H surface: the largest scale
// that keeps the whole box on screen, centered. The asymmetric presets lay their
// two screens out inside a fixed content box (for example 768 x 384) and then
// fit that whole box, which keeps the big and small screens locked in proportion
// to each other regardless of the surface aspect ratio.
struct Fit { float s, ox, oy; };
Fit fitUniform(float cw, float ch, float w, float h) {
    float s = std::min(w / cw, h / ch);
    return { s, (w - cw * s) * 0.5f, (h - ch * s) * 0.5f };
}

// Places a sub-rectangle (given in content-box coordinates) onto the surface
// using a uniform fit's scale and offset.
Rect place(const Fit& f, float nx, float ny, float nw, float nh) {
    return { f.ox + nx * f.s, f.oy + ny * f.s, nw * f.s, nh * f.s };
}

// Fine layout tuning post-pass. Runs over an already-computed LayoutPlan, in the
// same logical (pre-rotation) pixel space compute() produced. It scales every
// slot about the group centre (the centre of the union bounding box of the
// slots) and then offsets the whole group. Because it edits plan.slots in place,
// every consumer inherits the tuning for free: the render loop's viewport/scissor
// AND bottomRect() (so the OSK and the DS touch mapping follow the moved bottom
// screen). Identity (no offset, unit scale) leaves the plan untouched.
void applyTune(LayoutPlan& p, const LayoutConfig& c, float W, float H) {
    if (c.tuneScale == 1.0f && c.tuneDx == 0.0f && c.tuneDy == 0.0f) return;
    if (p.count <= 0) return;

    // Union bounding box of the slots -> group centre.
    float minx = 1e9f, miny = 1e9f, maxx = -1e9f, maxy = -1e9f;
    for (int i = 0; i < p.count; i++) {
        const Rect& r = p.slots[i].rect;
        minx = std::min(minx, r.x);       miny = std::min(miny, r.y);
        maxx = std::max(maxx, r.x + r.w); maxy = std::max(maxy, r.y + r.h);
    }
    const float cx = (minx + maxx) * 0.5f;
    const float cy = (miny + maxy) * 0.5f;
    const float ox = c.tuneDx * W;
    const float oy = c.tuneDy * H;
    const float s  = c.tuneScale;

    for (int i = 0; i < p.count; i++) {
        Rect& r = p.slots[i].rect;
        r.x = cx + (r.x - cx) * s + ox;
        r.y = cy + (r.y - cy) * s + oy;
        r.w = r.w * s;
        r.h = r.h * s;
        r = roundRect(r);
    }
}

// --- Predetermined handheld layout presets -------------------------------
// Authored at a 16:9 reference resolution (the advanced_drastic / drastic_layout
// TSP layout.json, 1280x720); compute() scales the rectangles to the real
// surface. The upstream JSON calls the DS top screen "screen0" and the bottom
// (touch) screen "screen1"; each preset lists its visible screens in back-to-
// front order so an overlapping picture-in-picture draws the inset last. The
// vertical (90/270) presets are intentionally omitted -- Display Rotation covers
// portrait play.
constexpr float kPresetRefW = 1280.0f;
constexpr float kPresetRefH = 720.0f;

struct PresetScreen { DsScreen content; float x, y, w, h; };   // reference pixels
// pipFrac > 0 marks a picture-in-picture preset that is COMPUTED rather than
// taken from the reference rects: the big screen is fit 4:3 (centred) and the
// inset is placed 4:3 in the configured corner (LayoutConfig::pipCorner, default
// bottom-right) at pipFrac of the surface width, so the inset sits mostly in a
// wide screen's side margin (minimal overlap) and can be drawn translucent
// (LayoutConfig::pipAlpha). The reference rects are ignored.
// pipFrac semantics: 0 = static (use s[] rects); > 0 = fixed-size inset of that
// fraction of the surface width (placed side by side when there is room, else an
// overlapping translucent inset in the configured corner); < 0 = "Auto" (the second screen
// dynamically fills the width left over beside the 4:3 big screen).
// `fill` true marks a stretch-to-panel preset (Full Screen): the single screen is
// intentionally drawn over the whole surface, ignoring aspect. Every other static
// preset is an aspect-preserving arrangement whose screens' bounding box is fit
// uniformly so the 4:3 screens never distort on a non-16:9 panel.
struct LayoutPreset { const char* name; int count; PresetScreen s[2]; float pipFrac; bool fill; };

const LayoutPreset kLayoutPresets[] = {
    { "Full Screen",  1, { { DsScreen::Bottom,   0,   0, 1280, 720 } }, 0.0f, true  },
    { "PiP Tiny",     0, { {} },  0.125f, false },   // computed: big 4:3 + small second screen
    { "PiP Small",    0, { {} },  0.200f, false },
    { "PiP Auto",     0, { {} }, -1.0f, false },     // big 4:3 + second screen fills the rest
    { "4:3 Centered", 1, { { DsScreen::Bottom, 160,   0,  960, 720 } }, 0.0f, false },
    { "Side by Side", 2, { { DsScreen::Top,      0, 120,  640, 480 },
                           { DsScreen::Bottom, 640, 120,  640, 480 } }, 0.0f, false },
    { "Big + Small",  2, { { DsScreen::Bottom,   0,  72,  768, 576 },
                           { DsScreen::Top,    768, 168,  512, 384 } }, 0.0f, false },
    { "Big + Tiny",   2, { { DsScreen::Bottom,   0,   0,  960, 720 },
                           { DsScreen::Top,    960, 240,  320, 240 } }, 0.0f, false },
    { "Stacked",      2, { { DsScreen::Top,    400,   0,  480, 360 },
                           { DsScreen::Bottom, 400, 360,  480, 360 } }, 0.0f, false },
};
const int kPresetCount = (int)(sizeof(kLayoutPresets) / sizeof(kLayoutPresets[0]));

} // namespace

int presetCount() { return kPresetCount; }
const char* presetName(int index) {
    return (index >= 0 && index < kPresetCount) ? kLayoutPresets[index].name : "";
}

LayoutPlan compute(const LayoutConfig& cfg, uint32_t surfaceW, uint32_t surfaceH) {
    const float W = static_cast<float>(surfaceW);
    const float H = static_cast<float>(surfaceH);
    const float SW = kDsScreenW;   // 256
    const float SH = kDsScreenH;   // 192

    LayoutPlan plan;

    // Predetermined handheld preset. Overrides the parametric layout. Swap still
    // exchanges which DS screen each slot carries (so a "wrong way round" preset is
    // one button press from corrected) without moving a rect.
    if (cfg.preset >= 0 && cfg.preset < kPresetCount) {
        const LayoutPreset& p = kLayoutPresets[cfg.preset];
        const DsScreen big = cfg.swap ? DsScreen::Top : DsScreen::Bottom;
        const DsScreen ins = cfg.swap ? DsScreen::Bottom : DsScreen::Top;

        // Picture-in-picture. The big screen is fit 4:3 at full height; the second
        // (inset) screen is a fixed fraction of the width (Tiny/Small) or, for Auto,
        // fills whatever width is left beside the big screen. When that inset fits
        // in the space beside the big screen the two go SIDE BY SIDE with no
        // overlap (opaque); only when it does not (a square/portrait surface) does
        // it fall back to an overlapping bottom-right inset, drawn translucent
        // (LayoutConfig::pipAlpha) so the big screen shows through.
        if (p.pipFrac != 0.0f) {
            const float gap  = 0.02f * W;
            const Fit   bf   = fitUniform(SW, SH, W, H);      // big 4:3, full height
            const float bigW = SW * bf.s, bigH = SH * bf.s;
            const float roomBeside = W - bigW - gap;          // free width beside big
            const float wantW = (p.pipFrac < 0.0f) ? roomBeside : (p.pipFrac * W);

            if (wantW > 4.0f && roomBeside >= wantW - 0.5f) {
                // Side by side, no overlap (opaque). Auto fills the full width.
                const Fit insFit = fitUniform(SW, SH, wantW, H);
                const float iW = SW * insFit.s, iH = SH * insFit.s;
                const float groupW = bigW + gap + iW;
                const float gx = (W - groupW) * 0.5f;
                Rect bigR = { gx,                 (H - bigH) * 0.5f, bigW, bigH };
                Rect insR = { gx + bigW + gap,     (H - iH)   * 0.5f, iW,   iH   };
                plan.count = 2;
                plan.slots[0] = { roundRect(bigR), big, 1.0f };
                plan.slots[1] = { roundRect(insR), ins, 1.0f };
                applyTune(plan, cfg, W, H);
                return plan;
            }
            if (p.pipFrac < 0.0f) {
                // Auto with no room beside the big screen: just the big, full.
                plan.count = 1;
                plan.slots[0] = { roundRect({ bf.ox, bf.oy, bigW, bigH }), big, 1.0f };
                applyTune(plan, cfg, W, H);
                return plan;
            }
            // Overlapping translucent inset, placed in the configured corner
            // (default bottom-right). `inset` is the margin held from the two
            // edges nearest the chosen corner.
            const float inset = 0.015f * H;
            const float iW = p.pipFrac * W, iH = iW * (SH / SW);
            float a = cfg.pipAlpha;
            if (a < 0.0f) a = 0.0f; else if (a > 1.0f) a = 1.0f;
            const bool right = (cfg.pipCorner == PipCorner::BottomRight ||
                                cfg.pipCorner == PipCorner::TopRight);
            const bool bottom = (cfg.pipCorner == PipCorner::BottomRight ||
                                 cfg.pipCorner == PipCorner::BottomLeft);
            const float px = right  ? (W - iW - inset) : inset;
            const float py = bottom ? (H - iH - inset) : inset;
            plan.count = 2;
            plan.slots[0] = { roundRect({ bf.ox, bf.oy, bigW, bigH }), big, 1.0f };
            plan.slots[1] = { roundRect({ px, py, iW, iH }), ins, a };
            applyTune(plan, cfg, W, H);
            return plan;
        }

        // Full-panel stretch preset: fill the surface, ignoring aspect (the DS
        // screen is intentionally stretched to the whole panel).
        if (p.fill) {
            DsScreen c = p.s[0].content;
            if (cfg.swap) c = (c == DsScreen::Top) ? DsScreen::Bottom : DsScreen::Top;
            plan.count = 1;
            plan.slots[0] = { roundRect({ 0.0f, 0.0f, W, H }), c, 1.0f };
            applyTune(plan, cfg, W, H);
            return plan;
        }

        // Static aspect-preserving preset. Insert the inter-screen gap (stacked /
        // side-by-side / asymmetric two-screen presets) then fit the BOUNDING BOX
        // of the screens uniformly to the surface. A uniform fit (not the old
        // independent sx=W/refW, sy=H/refH) is required so the 4:3 screens keep
        // their aspect on any non-16:9 panel -- the 1024x768 Brick scaled x by 0.8
        // and y by 1.067, squishing every screen. Fitting the screens' own
        // bounding box (rather than the full 1280x720 reference frame) fills the
        // panel instead of letterboxing the layout. On a true 16:9 panel the
        // result is identical to the old scaling.
        PresetScreen s0 = p.s[0];
        PresetScreen s1 = (p.count > 1) ? p.s[1] : p.s[0];
        if (p.count == 2) {
            // Push the two screens apart by the gap along whichever axis separates
            // them (vertical for Stacked, horizontal for Side by Side / Big + *).
            const float gapFrac = std::max(0.0f, std::min(cfg.gap, 1.0f));
            if (gapFrac > 0.0f) {
                const float c0x = s0.x + s0.w * 0.5f, c0y = s0.y + s0.h * 0.5f;
                const float c1x = s1.x + s1.w * 0.5f, c1y = s1.y + s1.h * 0.5f;
                const bool  vertical = std::fabs(c1y - c0y) >= std::fabs(c1x - c0x);
                const float gapRef = gapFrac * (vertical ? s0.h : s0.w);
                if (vertical) { if (c1y >= c0y) s1.y += gapRef; else s0.y += gapRef; }
                else          { if (c1x >= c0x) s1.x += gapRef; else s0.x += gapRef; }
            }
        }
        // Bounding box of the (possibly gap-shifted) screens, fit uniformly.
        float minX = s0.x, minY = s0.y, maxX = s0.x + s0.w, maxY = s0.y + s0.h;
        if (p.count == 2) {
            minX = std::min(minX, s1.x);         minY = std::min(minY, s1.y);
            maxX = std::max(maxX, s1.x + s1.w);   maxY = std::max(maxY, s1.y + s1.h);
        }
        const Fit f = fitUniform(maxX - minX, maxY - minY, W, H);
        const PresetScreen* arr[2] = { &s0, &s1 };
        plan.count = p.count;
        for (int i = 0; i < p.count; i++) {
            const PresetScreen& ps = *arr[i];
            DsScreen c = ps.content;
            if (cfg.swap) c = (c == DsScreen::Top) ? DsScreen::Bottom : DsScreen::Top;
            plan.slots[i] = { roundRect(place(f, ps.x - minX, ps.y - minY, ps.w, ps.h)), c, 1.0f };
        }
        applyTune(plan, cfg, W, H);
        return plan;
    }

    // The first slot leads. Swap exchanges which DS screen leads, so the same
    // geometry serves both orderings and the swap toggle never moves a rectangle,
    // only the content drawn into it.
    const DsScreen aContent = cfg.swap ? DsScreen::Bottom : DsScreen::Top;
    const DsScreen bContent = cfg.swap ? DsScreen::Top : DsScreen::Bottom;

    // Single mode shows exactly one screen filling the surface. The asymmetric
    // scalings have no meaning with one screen, so they fall back to Stretch.
    if (cfg.orient == Orientation::Single) {
        Rect r;
        if (cfg.scaling == Scaling::None) {
            r = { (W - SW) * 0.5f, (H - SH) * 0.5f, SW, SH };
        } else {
            const float s = std::min(W / SW, H / SH);
            const float dw = SW * s, dh = SH * s;
            r = { (W - dw) * 0.5f, (H - dh) * 0.5f, dw, dh };
        }
        plan.count = 1;
        plan.slots[0] = { roundRect(r), cfg.swap ? DsScreen::Bottom : DsScreen::Top };
        applyTune(plan, cfg, W, H);
        return plan;
    }

    plan.count = 2;
    const bool horiz = (cfg.orient == Orientation::Horizontal);

    // Gap between the two screens along the stacking axis, expressed in DS
    // pixels so it scales with the screens. Clamp to a sane range so an extreme
    // value can never collapse the screens to nothing. Horizontal layouts space
    // the screens by the screen width, stacked layouts by the screen height.
    const float gapFrac = std::max(0.0f, std::min(cfg.gap, 1.0f));
    const float gH = SW * gapFrac;   // gap for side-by-side layouts
    const float gV = SH * gapFrac;   // gap for stacked layouts

    switch (cfg.scaling) {
        case Scaling::None: {
            // Native 256x192 per screen, the pair centered with the chosen gap.
            Rect a, b;
            if (horiz) {
                const float ox = (W - (2 * SW + gH)) * 0.5f, oy = (H - SH) * 0.5f;
                a = { ox, oy, SW, SH };
                b = { ox + SW + gH, oy, SW, SH };
            } else {
                const float ox = (W - SW) * 0.5f, oy = (H - (2 * SH + gV)) * 0.5f;
                a = { ox, oy, SW, SH };
                b = { ox, oy + SH + gV, SW, SH };
            }
            plan.slots[0] = { roundRect(a), aContent };
            plan.slots[1] = { roundRect(b), bContent };
            break;
        }
        case Scaling::Stretch: {
            // Two equal screens scaled to fill the surface while preserving the
            // 4:3 aspect ratio, the pair centered with the chosen gap folded into
            // the fit so both screens plus the gap stay on screen. This is the
            // sensible handheld default: on a 1024x768 landscape panel it yields
            // two 512x384 screens side by side.
            Rect a, b;
            if (horiz) {
                const float s = std::min(W / (2 * SW + gH), H / SH);
                const float dw = SW * s, dh = SH * s, gs = gH * s;
                const float ox = (W - (2 * dw + gs)) * 0.5f, oy = (H - dh) * 0.5f;
                a = { ox, oy, dw, dh };
                b = { ox + dw + gs, oy, dw, dh };
            } else {
                const float s = std::min(W / SW, H / (2 * SH + gV));
                const float dw = SW * s, dh = SH * s, gs = gV * s;
                const float ox = (W - dw) * 0.5f, oy = (H - (2 * dh + gs)) * 0.5f;
                a = { ox, oy, dw, dh };
                b = { ox, oy + dh + gs, dw, dh };
            }
            plan.slots[0] = { roundRect(a), aContent };
            plan.slots[1] = { roundRect(b), bContent };
            break;
        }
        case Scaling::S1x2x: {
            // One screen at native 256x192 next to the other at 512x384, the
            // small screen leading. The content box holds both at their fixed
            // sizes (768x384 horizontal, 512x576 vertical) plus the gap so the
            // size ratio stays exactly 1:2 whatever the surface aspect ratio is.
            Rect small, big;
            if (horiz) {
                const Fit f = fitUniform(768.0f + gH, 384.0f, W, H);
                small = place(f, 0.0f,   96.0f, SW, SH);          // vertically centered against the big one
                big   = place(f, 256.0f + gH, 0.0f, 2 * SW, 2 * SH);
            } else {
                const Fit f = fitUniform(512.0f, 576.0f + gV, W, H);
                big   = place(f, 0.0f,   0.0f, 2 * SW, 2 * SH);
                small = place(f, 128.0f, 384.0f + gV, SW, SH);    // horizontally centered under the big one
            }
            plan.slots[0] = { roundRect(small), aContent };
            plan.slots[1] = { roundRect(big),   bContent };
            break;
        }
        case Scaling::S2x1x: {
            // Mirror of S1x2x: the big 512x384 screen leads, the small 256x192
            // screen trails, separated by the gap.
            Rect big, small;
            if (horiz) {
                const Fit f = fitUniform(768.0f + gH, 384.0f, W, H);
                big   = place(f, 0.0f,    0.0f, 2 * SW, 2 * SH);
                small = place(f, 512.0f + gH, 96.0f, SW, SH);
            } else {
                const Fit f = fitUniform(512.0f, 576.0f + gV, W, H);
                small = place(f, 128.0f, 0.0f, SW, SH);
                big   = place(f, 0.0f, 192.0f + gV, 2 * SW, 2 * SH);
            }
            plan.slots[0] = { roundRect(big),   aContent };
            plan.slots[1] = { roundRect(small), bContent };
            break;
        }
    }

    applyTune(plan, cfg, W, H);
    return plan;
}

Rect bottomRect(const LayoutPlan& plan) {
    for (int i = 0; i < plan.count; i++) {
        if (plan.slots[i].content == DsScreen::Bottom) return plan.slots[i].rect;
    }
    return Rect{};   // no bottom screen on screen (Single mode showing the top)
}

} // namespace drastic_nano
