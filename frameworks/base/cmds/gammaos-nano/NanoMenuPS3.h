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
constexpr float ITEM_ACTIVE_PAD = 108.0f;   // bigger than web 87: makes room for the 3-line enlarged description below the active item
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
constexpr float ITEM_DESC_OFFSET = 26.0f;
constexpr float ITEM_DESC_SIZE = 22.0f;   // bigger than web 18 (too small on small panels), sized to fit 3 wrapped lines
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

} // namespace ps3
} // namespace android

#endif // GAMMAOS_NANO_PS3_H
