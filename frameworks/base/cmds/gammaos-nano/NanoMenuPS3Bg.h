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

// GammaOS Nano PS3 XMB background: the per-month gradient + captured cloth wave.
// 1:1 port of the source web app's drawBGWebGL steady path:
//   1. FS_BG gradient (per-month hue + dark-top/bright-bottom value+saturation
//      ramp + corner vignette + day/night) rendered into a cached texture,
//   2. the captured 128x128 clip-space cloth mesh drawn ADDITIVELY (silk shader),
//   3. an exp2 tonemap composite to the panel.
// The gradient is cached and only re-rendered when the day/night blend, month,
// or layout changes, so the per-frame cost is one cheap blit + the wave + the
// tonemap. This is the default wallpaper ("XMB wave", wallpaper effect id 21).
//
// All state is file-local; ps3bg reads ps3:: layout globals (NanoMenuPS3.h) and
// the DRM rotation matrix, so it does not touch the NanoMenu class. init() is
// lazy and safe to call repeatedly; render() no-ops until assets are ready.

#ifndef GAMMAOS_NANO_PS3_BG_H
#define GAMMAOS_NANO_PS3_BG_H

#include <GLES2/gl2.h>

namespace android {
namespace ps3bg {

// Compile shaders, load wave geometry/sequence + month textures, create FBOs.
// Returns true once everything is ready to render. Cheap to call every frame
// until ready (it retries asset loads that have not yet succeeded).
bool init();

// Free all GL objects and CPU keyframe buffers.
void shutdown();

// True once init() has fully succeeded and render() will draw the real wave.
bool ready();

// Render the background to the currently-bound framebuffer. The caller sets the
// panel viewport and clears it (letterbox bars stay the clear colour). The
// gradient/wave are confined to ps3::gFrame* and the composite applies rotMat2
// (DRM GL rotation; pass identity {1,0,0,1} when inactive). dt is seconds since
// the last frame (for the wave's frame-rate-independent animation).
// compositeToScreen=false updates the offscreen work texture (gradient + wave,
// see workTex()) WITHOUT drawing it to the panel, so callers that want a
// different visible background (e.g. the in-game overlay, which keeps the
// SurfaceFlinger-blurred app visible) can still get the glass-icon refraction
// source. The glitter field is also skipped in that mode (it draws to the panel).
void render(int panelW, int panelH, float dt,
            const float rotMat2[4], bool rotActive, bool compositeToScreen = true);

// Force the cached gradient to be re-rendered next frame (call on layout change
// or a forced day/night refresh; month + time-of-day changes are detected
// automatically).
void invalidateGradient();

// The work texture holding the composited (gradient + additive wave) scene in
// LINEAR space, sampled by the glass-icon shader for live-wave refraction. 0
// until render() has run at least once. The texture spans ps3::gFrame* exactly:
// texcoord (0,0) = frame bottom-left, (1,1) = frame top-right (GL y-up).
GLuint workTex();

// Cold-boot wave brightness multiplier on the silk-wave fade (uFade). 1.0 = the
// normal steady wave; the boot intro ramps this 0->1 so the wave emerges from
// black, then resets it to 1.0 when the intro ends. Default 1.0 = steady path
// byte-identical (no effect unless the boot intro is driving it).
void setBootWaveBrightness(float b);

// Theme Settings (set from the menu's Theme Settings choosers). setThemeColor
// overrides the per-month background base colour with a user-chosen RGB; clear
// reverts; setDayNightBlend forces the time-of-day lighting (<0 = auto). Each
// triggers a one-frame gradient recache.
void setThemeColor(float r, float g, float b);
void clearThemeColor();
void setDayNightBlend(float b);

// Background == Classic removes the glitter particle field. true (default) shows
// the particles; false hides them (Theme Settings -> Background -> Classic).
void setParticlesEnabled(bool enabled);

// Luminance of the current background base colour (0 dark .. ~1.2 light). Used
// by the menu to scale the text stroke shadow with the wallpaper brightness.
float backgroundLuma();

// True while a Colour / Day-Night cross-fade is still settling. The menu samples
// the frosted backdrop at 60Hz during a live theme preview (vs 15Hz settled).
bool themeFading();

} // namespace ps3bg
} // namespace android

#endif // GAMMAOS_NANO_PS3_BG_H
