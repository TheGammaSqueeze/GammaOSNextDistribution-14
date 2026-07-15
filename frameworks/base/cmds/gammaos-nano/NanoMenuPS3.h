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

// GammaOS Nano PS3 XMB: shared constants and the responsive layout model.
//
// The XMB is authored in a fixed 1920x1080 virtual space and mapped to the
// real panel by a handful of layout globals, exactly like the source web
// recreation (index.html resize(), helpers XCP/XCF/XCL). Every draw call works
// in virtual coordinates; ps3::devX/devY/devS map them to logical device
// pixels (the mWidth x mHeight panel) so the same code lays out correctly on
// 16:9, 4:3, 3:2, 21:9, portrait and low-resolution panels without ever
// stretching (uniform scale + position compression, never anamorphic).
//
// All constants are firmware-verified values lifted verbatim from the source
// app's V{} table (vaddr citations preserved where given).

#ifndef GAMMAOS_NANO_PS3_H
#define GAMMAOS_NANO_PS3_H

namespace android {
namespace ps3 {

// ---- Virtual design space ----
constexpr float VW = 1920.0f;   // explore vaddr 0x270A38
constexpr float VH = 1080.0f;   // explore vaddr 0x270A3C

// Adaptive icon-texture cap. Source art ships at a fixed resolution (256/512),
// but on a given panel an icon is only ever drawn at gScale*virtualSize px.
// Return a power-of-two side >= 2x that max on-screen size (the 2x supersample
// headroom, computed off the uiScale=1.0 base, also covers the full user
// uiScale 0.5..2.0 zoom range without blur), clamped to [64, sourceSize].
// Result: small panels (Brick 1024x768) get 256/128 textures = big VRAM saving;
// high-DPI panels (1080p+) resolve back to the full source size = no blur. Pure
// function of the panel resolution, so it scales correctly to any screen.
inline int iconTexCap(int panelW, int panelH, float virtualSize, int sourceSize) {
    float sx = (float)panelW / VW, sy = (float)panelH / VH;
    float baseScale = sx < sy ? sx : sy;
    if (baseScale <= 0.0f) baseScale = 1.0f;
    int want = (int)(baseScale * virtualSize * 2.0f + 0.999f);
    int p = 64;
    while (p < want) p <<= 1;            // power-of-two ceiling, min 64
    if (p > sourceSize) p = sourceSize;  // never upscale past the source asset
    return p;
}

// ---- Category bar (measured from RPCS3 1080p capture) ----
constexpr float CAT_Y = 289.0f;
constexpr float CAT_Y_ACTIVE_OFFSET = -10.0f;
constexpr float CAT_X = 566.0f;
constexpr float CAT_SPACING = 203.0f;
constexpr float CAT_ICON_ACTIVE = 168.0f;
constexpr float CAT_ICON_INACTIVE = 117.0f;
constexpr float CAT_LABEL_Y = 366.0f;   // below the active icon (was web 342; lowered for clearance)
constexpr float CAT_LABEL_SIZE = 26.0f;   // bigger than web 20 (clearer on small panels)
constexpr float CAT_INACTIVE_ALPHA = 0.9f;
constexpr float CAT_FAR_ALPHA = 0.25f;

// ---- Item list ----
constexpr float ITEM_FOCUS_Y = 515.0f;
constexpr float ITEM_SPACING = 80.0f;
constexpr float ITEM_ACTIVE_PAD = 118.0f;   // bigger than web 87: makes room for the 3-line enlarged description below the active item (raised with ITEM_DESC_OFFSET for the extra subtitle gap)
constexpr float ITEM_ICON_X = 566.0f;
constexpr float ITEM_ICON_SIZE = 108.0f;   // bigger than web 78 to match the RetroArch console-icon size
// Content icons sourced from RetroArch/the launcher (console systems, ROM/game
// art, app icons) read larger than the PS3-style data icons at the same box, so
// scale just those down. PS3 data/setting icons keep the full ITEM_ICON_SIZE.
constexpr float RETRO_ICON_SCALE = 0.78f;
constexpr float ITEM_ICON_ACTIVE = 180.0f;
constexpr float ITEM_TEXT_X = 683.0f;
constexpr float ITEM_TEXT_SIZE = 30.0f;
constexpr float ITEM_TEXT_ACTIVE_SIZE = 34.0f;
constexpr float ITEM_DESC_OFFSET = 38.0f;   // gap below the glowing selected label to the subtitle top (raised ~2 device px for breathing room)
constexpr float ITEM_DESC_SIZE = 22.0f;   // base subtitle size (web is 18). The small-panel boost is applied via the resolution-gated DESC_BOOST (gDescSize), not a flat bump, so >=720p stays at this base.
constexpr float ITEM_DESC_LINEH = 1.12f;   // subtitle line-height multiplier (tighter than the label so 3 wrapped lines fit the active pad)
constexpr float ITEM_DESC_MARGIN = 4.0f;   // virtual px of breathing room below the 3rd subtitle line before the next item
constexpr float ITEM_VALUE_RIGHT_PAD = 80.0f;

// ---- Scale + alpha tiers (firmware-verified) ----
constexpr float ITEM_ACTIVE_SCALE = 1.3f;
constexpr float ITEM_INACTIVE_SCALE = 0.833f;
constexpr float ALPHA_FOCUS = 1.0f;
constexpr float ALPHA_INACTIVE = 0.48f;
constexpr float ALPHA_NEAR = 0.8f;
constexpr float ALPHA_MID = 0.5f;
constexpr float ALPHA_FAR = 0.3f;

// ---- Category in submenu ----
constexpr float CAT_SUBMENU_SHIFT_X = 257.0f;
constexpr float CAT_SUBMENU_Y_SHIFT = -10.0f;
constexpr float CAT_SUBMENU_SCALE = 0.833f;
constexpr float CAT_SUBMENU_ALPHA = 0.6f;

// ---- Submenu parent column ----
// On entering a submenu the parent item list COLLAPSES: the selected parent
// slides to the breadcrumb column (PARENT_COL_X) as a normal-size cube, the
// siblings gather into a faded uniform column at SIB_X, and the children slide
// in from the right by SLIDE_DIST. Mirrors index.html drawParentLayer 7531-7571.
constexpr float PARENT_COL_X = 316.0f;
constexpr float PARENT_ICON_SIZE = 78.0f;
constexpr float PARENT_SPACING = 80.0f;
constexpr float SIB_X = 455.0f;                          // faded sibling column (566 - 111)
constexpr float SLIDE_DIST = ITEM_ICON_X - PARENT_COL_X; // 250px child slide-in distance
// Left-anchored (XCP/XCL) virtual-px nudge applied to submenu CHILDREN only, on
// top of SLIDE_DIST, so they clear the faded sibling column instead of crowding
// it under the 4:3 + uiScale compression. NOT applied to right-anchored values.
constexpr float SUBMENU_CHILD_X_SHIFT = 60.0f;

// ---- Clock ----
constexpr float CLOCK_X = 1716.0f, CLOCK_Y = 103.0f, CLOCK_SIZE = 26.0f;
constexpr float CLOCK_FRAME_X = 1329.0f, CLOCK_FRAME_Y = 130.0f;   // lower than web 75: sits between the top edge and the category icons
constexpr float CLOCK_FRAME_W = 590.0f, CLOCK_FRAME_H = 57.0f;
constexpr float CLOCK_FRAME_CORNER = 6.0f, CLOCK_FRAME_GLOW = 6.0f;
constexpr float CLOCK_ICON_R = 16.0f, CLOCK_ICON_CX = 1789.5f;
constexpr float CLOCK_SPIN_MS = 620.0f;
constexpr int   CLOCK_MIN_TURNS = 2, CLOCK_HOUR_TURNS = 1;

// ---- Animation durations (firmware-verified) ----
constexpr float CAT_ANIM_MS = 250.0f;
constexpr float ITEM_ANIM_MS = 200.0f;
constexpr float SUBMENU_ANIM_MS = 250.0f;
constexpr float ITEM_DESC_FADE_MS = 250.0f;
constexpr float EASE_BACK_C1 = 1.70158f;

// ---- Input timings (firmware-verified) ----
constexpr float INPUT_INITIAL_DELAY_MS = 300.0f;
constexpr float INPUT_REPEAT_MS = 200.0f;
constexpr float INPUT_MIN_REPEAT_MS = 50.0f;
constexpr float INPUT_ACCEL_MULT = 1.4f;

// ---- Active-item text glow (measured, gaussian sigma ~4.5px) ----
constexpr float TEXT_GLOW_RADIUS = 15.0f;
constexpr float TEXT_GLOW_INNER_RADIUS = 5.0f;
constexpr float PULSE_PERIOD_MS = 1500.0f;
constexpr float PULSE_ALPHA_MIN = 0.42f, PULSE_ALPHA_MAX = 0.68f;
constexpr float PULSE_INNER_MIN = 0.38f, PULSE_INNER_MAX = 0.62f;

// ---- Font weights available in the Rodin family ----
enum RodinWeight { RODIN_LIGHT = 0, RODIN_REGULAR = 1, RODIN_BOLD = 2 };

// ============================================================================
// Responsive layout model. layoutCompute() is called once per frame with the
// logical panel size (post-DRM-rotation mWidth/mHeight) and fills these globals;
// every draw call then maps virtual coords through devX/devY/devS + the XC*
// helpers. Mirrors index.html resize() 1865-2047 exactly.
// ============================================================================

extern float gScale;        // virtual -> device px (uniform; never anamorphic)
extern float gDescSize;     // resolution-gated subtitle size in virtual px (ITEM_DESC_SIZE..ITEM_DESC_SIZE_SMALL), set per frame in layoutCompute
extern float gActivePad;    // active-item pad in virtual px, sized to fit 3 lines of gDescSize, set per frame in layoutCompute
extern float gOffX;         // device-px horizontal offset (frame centring / pillarbox)
extern float gOffY;         // device-px vertical offset (letterbox + portrait focus parking)
extern float LAYOUT_FIT;    // visible virtual width / VW (right edge factor)
extern float LAYOUT_XC;     // left-anchored horizontal position compression
extern float LAYOUT_XSHIFT; // extra leftward shift (virtual px) on narrow frames
extern float LAYOUT_VFIT;   // visible virtual height / VH (>1 in portrait)
extern float LAYOUT_VOFF;   // top of visible frame in virtual coords (>=0)
extern float DESC_BOOST;    // subtitle font multiplier on tiny frames
extern bool  gPortrait;     // frame taller than wide
// Content frame rect inside the panel, in device px. Equals the whole panel
// when filling natively; smaller (pillar/letterboxed, centred) when an aspect
// or resolution is forced. The renderer sets the wave/gradient viewport to this
// rect and clears the surrounding bars to black.
extern float gFrameX, gFrameY, gFrameW, gFrameH;

enum OrientationMode { ORIENT_AUTO = 0, ORIENT_LANDSCAPE = 1, ORIENT_PORTRAIT = 2 };

// Mirrors the source app's ?aspect / ?orientation / ?res model. aspectOverride
// <= 0 means use the panel's native aspect; simW/simH 0 means render at the
// native panel resolution (no resolution simulation). When an aspect or a
// resolution that does not match the panel is forced, the frame is fitted and
// centred inside the panel (integer-magnified for a simulated low resolution,
// like the web app's integer scaling), with black bars around it.
struct LayoutParams {
    int   panelW = 0;
    int   panelH = 0;
    float aspectOverride = 0.0f;          // 0 = native; else target W/H ratio
    int   orientation = ORIENT_AUTO;      // OrientationMode
    int   simW = 0;                       // 0 = native panel resolution
    int   simH = 0;
    // User UI-size preference: zooms the MENU (icons/text/spacing) about the
    // visible centre without touching the background frame, so the wave still
    // fills the panel under a larger menu. 1.0 = web-exact; >1 enlarges. Useful
    // on small physical panels (e.g. the 3.2" TrimUI Brick).
    float uiScale = 1.0f;
};

// Parse helpers, matching ASPECTS / RES_PRESETS / orientation in index.html.
float parseAspect(const char* s);                 // "4:3"/"16:9"/"1.333"... -> ratio (0 if none)
int   parseOrientation(const char* s);            // "portrait"/"landscape"/"auto"
bool  parseRes(const char* s, int* w, int* h);    // "640x480"/"480p"/"720p"... -> w,h (true if matched)

// Recompute the layout globals. Pass native panel size for the common case.
void layoutCompute(const LayoutParams& p);
inline void layoutComputeNative(int panelW, int panelH) {
    LayoutParams p; p.panelW = panelW; p.panelH = panelH; layoutCompute(p);
}

// Virtual-space horizontal position helpers (applied BEFORE devX).
inline float XCP(float x) { return x * LAYOUT_XC - LAYOUT_XSHIFT; }      // left-anchored
inline float XCF(float x) { return x * LAYOUT_FIT; }                    // right-anchored / centred / frame-spanning
inline float XCL(float cx, float halfW) { return cx * LAYOUT_XC - LAYOUT_XSHIFT - halfW; }
inline float frameTopV() { return -LAYOUT_VOFF; }
inline float frameHV()   { return VH * LAYOUT_VFIT; }

// Virtual -> logical device pixels. Folds in the frame-centring offsets so the
// same draw code lands correctly whether filling the panel or letterboxed.
inline float devX(float vx) { return gScale * vx + gOffX; }
inline float devY(float vy) { return gScale * vy + gOffY; }
inline float devS(float v)  { return gScale * v; }

// drawText() renders an em of ~16*scale device px (pixelScale=16*scale/48,
// baseline 0.8*em below the top y). So a virtual font of px maps to device em
// (px*gScale) at drawText scale (px*gScale)/16, with the top y = baseline -
// 0.8*em. fontScale() returns the drawText scale; baselineToTopY() converts a
// desired baseline y (device px) to the top y drawText expects.
inline float fontScale(float virtualPx) { return gScale * virtualPx / 16.0f; }
inline float emPx(float drawScale)       { return 16.0f * drawScale; }
inline float baselineToTopY(float baselineDevY, float drawScale) {
    return baselineDevY - 0.8f * emPx(drawScale);
}

// Full-panel, aspect-independent layout for the media transport control grids (the on-screen
// touch controls of the video, music and photo players). Unlike the XMB devX/devY/devS helpers
// above, this does NOT letterbox through the virtual 1920x1080 frame: the control row spans the
// FULL panel width and is sized to the panel's short side, so on ANY panel size/aspect (a square
// 720x720, portrait, ultrawide, 480p..1080p) the finger targets stay proportional, evenly spaced,
// and never run off-screen. The old letterboxed layout squeezed the grid into the pillarboxed
// content frame and then multiplied it by a 2x-3x UI/touch scale, so on a square panel the icons
// bloated and overran the edges. gxLo/gxHi are the min/max column indices in the caller's control
// table, gyLo/gyHi the min/max row indices. Both the draw and the hit-test of each player call this
// (via a shared per-player helper) so they can never drift apart.
//   ox      : device px of column 0 (the grid is centred horizontally on the full panel)
//   centerY : device px of the vertical centre of the row block (lower-middle of the panel);
//             each caller offsets its own gy==0 anchor from this using its own cy formula
//   cellX,cellY: column / row pitch (device px)
//   icon    : glyph size (device px)
struct MediaGrid { float ox, centerY, cellX, cellY, icon; };
inline MediaGrid mediaGrid(int panelW, int panelH, float gxLo, float gxHi, float gyLo, float gyHi) {
    MediaGrid g;
    const float W = (float)panelW, H = (float)panelH;
    const float shortSide = (W < H ? W : H);
    const float cols = (gxHi > gxLo) ? (gxHi - gxLo) : 1.0f;
    const float rows = (gyHi > gyLo) ? (gyHi - gyLo) : 1.0f;
    // Column pitch: span ~88% of the panel width with a half-cell margin at each edge. On a
    // square/narrow panel this uses the full width (the case the control panels were failing on);
    // the icon then follows the pitch, capped to the short side so it stays a sane finger target.
    float cellX = (W * 0.88f) / (cols + 1.0f);
    float icon = cellX * 0.62f;
    float iconCap = shortSide * 0.12f;
    if (icon > iconCap) icon = iconCap;
    // Wide/ultrawide panels: the short-side icon cap above would otherwise leave the columns
    // spread edge-to-edge with big empty gaps between small icons. Cap the pitch to ~1.9x the
    // icon so the grid stays a tight, centred cluster instead of stretching across the whole
    // width. (No effect on square/narrow panels, where the pitch is already close to the icon.)
    if (cellX > icon * 1.9f) cellX = icon * 1.9f;
    float cellY = icon * 1.55f;
    // Keep the whole row block within ~46% of the height so it never collides with the seek bar
    // (near the bottom) or the title (near the top) on a short panel; shrink uniformly if needed.
    float maxBlock = H * 0.46f;
    if (rows * cellY > maxBlock) {
        float sY = maxBlock / (rows * cellY);
        cellY *= sY; icon *= sY; cellX *= sY;
    }
    g.cellX = cellX; g.cellY = cellY; g.icon = icon;
    g.ox = W * 0.5f - (gxLo + gxHi) * 0.5f * cellX;   // centre the column span
    g.centerY = H * 0.5f;                             // centre the row block on the panel
    return g;
}

} // namespace ps3
} // namespace android

#endif // GAMMAOS_NANO_PS3_H
