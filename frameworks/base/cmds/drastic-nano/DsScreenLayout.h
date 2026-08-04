// DsScreenLayout.h
//
// Single-view layout geometry for the two Nintendo DS screens.
//
// The DS has two 256x192 screens (a top screen and a bottom touch screen). On a
// device that presents through a single panel or a single SurfaceFlinger window,
// both screens share one surface, so they need a layout that places each screen
// into its own rectangle. This module computes those rectangles for a target
// surface of arbitrary width and height, mirroring the layout presets of the
// advanced_drastic build (orientation, scaling, and a top/bottom swap).
//
// The math here is pure: it takes a LayoutConfig and a surface size and returns a
// LayoutPlan of destination rectangles in top-left pixel coordinates. It owns no
// GL state and reads no globals, so the render loop and any unit test consume the
// same result. The render loop turns each rectangle into a glViewport/glScissor
// and draws the matching DS screen into it; the OSK renderer and the touch mapper
// read bottomRect() so the on-screen keyboard and the touch coordinate mapping
// follow wherever the layout puts the bottom (touch) screen.

#pragma once

#include <cstdint>

namespace drastic_nano {

// Native pixel size of one DS screen. Both screens share these dimensions.
inline constexpr float kDsScreenW = 256.0f;
inline constexpr float kDsScreenH = 192.0f;

// How the two screens arrange relative to each other. The values match the
// advanced_drastic `screen_orientation` config so the in-game menu and the
// persisted property mirror the upstream naming.
enum class Orientation : int {
    Horizontal = 0,  // the two screens sit side by side (left/right)
    Vertical   = 1,  // the two screens stack (top/bottom)
    Single     = 2,  // only one DS screen shows, filling the surface
};

// How each screen is sized within the arrangement. The values match the
// advanced_drastic `screen_scaling` naming.
enum class Scaling : int {
    None    = 0,  // native 256x192 per screen, centered, no scaling
    Stretch = 1,  // equal screens scaled to fit while preserving aspect ratio
    S1x2x   = 2,  // asymmetric: one screen at 256x192 next to the other at 512x384
    S2x1x   = 3,  // asymmetric mirror of S1x2x (big screen leads)
};

// Which emulated DS screen a layout slot carries.
enum class DsScreen : int { Top, Bottom };

// Which corner the overlapping picture-in-picture inset screen sits in. Only
// takes effect when the PiP inset overlaps the big screen (a 4:3 or portrait
// panel, where there is no room to place it side by side); on a wide panel the
// inset sits beside the big screen and the corner has no effect.
enum class PipCorner : int {
    BottomRight = 0,  // default: the inset's original placement
    BottomLeft  = 1,
    TopRight    = 2,
    TopLeft     = 3,
};

// The user-selectable layout choice. `swap` exchanges which DS screen leads
// (which one is the first slot, or which one shows in Single mode), matching the
// runtime Screen-Swap action.
struct LayoutConfig {
    Orientation orient  = Orientation::Horizontal;
    Scaling     scaling = Scaling::Stretch;
    bool        swap    = false;
    // Gap between the two screens, as a fraction of the leading screen's size
    // along the stacking axis (the screen height when stacked, the width when
    // side by side). 0 keeps the screens touching, as before. The gap scales
    // with the layout so it looks the same at any resolution.
    float       gap     = 0.0f;
    // Predetermined handheld layout preset (the advanced_drastic / drastic_layout
    // presets: FullScreen, side-by-side, centered, picture-in-picture, asymmetric,
    // stacked...). -1 = use the parametric orient/scaling/swap/gap above; >= 0
    // selects an entry in the preset table and overrides them. See kLayoutPresets.
    int         preset  = -1;
    // Opacity (0..1) of the inset screen in a picture-in-picture preset, so the
    // big screen shows through where they overlap. 1 = opaque (no see-through).
    // Ignored by non-PiP layouts.
    float       pipAlpha = 1.0f;
    // Which corner the overlapping picture-in-picture inset sits in (default
    // bottom-right, the original placement). Only used when the PiP inset
    // overlaps the big screen; ignored otherwise.
    PipCorner   pipCorner = PipCorner::BottomRight;
    // Fine layout tuning applied as a single post-pass over the computed slots,
    // on top of ANY preset or parametric layout. These let the user nudge the
    // whole screen group so it lands correctly on a panel whose safe area or
    // bezel does not match the reference layout. tuneDx/tuneDy shift the group as
    // a fraction of the surface width/height; tuneScale scales every slot about
    // the group centre (1 = unchanged). All identity by default, so a user who
    // never touches them sees the exact same layout as before. Applied uniformly
    // so the bottom (touch) rect moves with the drawn quads and the OSK + DS
    // touch mapping (which read bottomRect()) stay aligned.
    float       tuneDx    = 0.0f;   // group X offset, fraction of surface W (-0.5..0.5)
    float       tuneDy    = 0.0f;   // group Y offset, fraction of surface H (-0.5..0.5)
    float       tuneScale = 1.0f;   // uniform group scale about centre (0.5..1.5)
};

// Number of predetermined layout presets (kLayoutPresets in DsScreenLayout.cpp).
int presetCount();
// The display name of a preset (e.g. "Side by Side"), or "" if out of range.
const char* presetName(int index);

// A destination rectangle in top-left pixel coordinates of the target surface.
struct Rect {
    float x = 0.0f, y = 0.0f, w = 0.0f, h = 0.0f;
};

// One screen placed at one rectangle.
struct SlotPlan {
    Rect     rect;
    DsScreen content = DsScreen::Top;
    // Draw opacity (0..1). < 1 only for the inset of a picture-in-picture preset;
    // the render loop blends that slot over the big screen beneath it.
    float    alpha   = 1.0f;
};

// The full placement for one frame: one slot in Single mode, two otherwise.
struct LayoutPlan {
    int      count = 0;
    SlotPlan slots[2];
};

// Computes the placement of the DS screens on a surface of width W by height H.
// The returned rectangles use top-left origin pixel coordinates; the caller flips
// y when it programs a bottom-left-origin glViewport. Rectangles round to whole
// pixels so the viewport lands on exact texels.
LayoutPlan compute(const LayoutConfig& cfg, uint32_t surfaceW, uint32_t surfaceH);

// Returns the rectangle of the bottom (touch) DS screen for a computed plan, or a
// zero-size rectangle when the plan shows no bottom screen (Single mode showing
// only the top). The OSK renderer and the DS touch mapper share this rectangle so
// the keyboard and the touch coordinate mapping track the bottom screen across
// every preset, including swapped and asymmetric ones.
Rect bottomRect(const LayoutPlan& plan);

} // namespace drastic_nano
