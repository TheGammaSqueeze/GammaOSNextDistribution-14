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

// GammaOS Nano PS3 XMB "Time Zone" 3D Earth globe. 1:1 port of the web app's
// tzglobe screen (index.html initGlobe / renderGlobe / tzGlobeTick, 10285-10391).
//
// The web globe is NOT a sphere mesh: it is a single fullscreen quad with a
// fragment shader that ray-marches a unit sphere per pixel (uCenter/uR/uRes),
// rotates the surface normal by rotY(uLon)*rotX(-uLat), samples day/night/clouds
// equirectangular maps, applies the day/night terminator and a fresnel
// atmosphere rim, and paints space + a soft atmosphere halo outside the disc.
// So the port needs no depth buffer and no geometry: it is one more
// fullscreen-quad GLES2 program like ps3bg's FS_BG / FS_COMP.
//
// render() draws the globe into a frame-sized offscreen FBO in LOGICAL
// orientation (gFrame*), then composites it to the bound framebuffer with the
// DRM rotation matrix and a fade alpha (the web's XMB->globe cross-fade). The
// menu / wizard chrome is drawn on top by the NanoMenu caller.
//
// State (eased lon/lat -> target) lives here; the caller sets the target zone
// via setTarget()/snapTo() and advances the ease each frame with tick().

#ifndef GAMMAOS_NANO_PS3_GLOBE_H
#define GAMMAOS_NANO_PS3_GLOBE_H

#include <GLES2/gl2.h>

namespace android {
namespace ps3globe {

// Compile the globe + composite programs and load the three earth maps. Cheap to
// call every frame until ready (retries the asset loads). Returns true once the
// shaders linked (the globe still renders space + atmosphere if a map is
// missing, via the 1x1 fallback texture).
bool init();

// Free all GL objects.
void shutdown();

// True once init() has fully succeeded.
bool ready();

// Snap the rotation immediately to (lonRad, latRad) (on screen enter so the
// globe opens already showing the current zone, no spin-up from 0).
void snapTo(float lonRad, float latRad);

// Ease the rotation toward (lonRad, latRad) (on zone-selection change). Longitude
// takes the shortest path around the globe.
void setTarget(float lonRad, float latRad);

// Advance the eased rotation toward the target. Frame-rate independent (the web
// uses lon += dLon*0.12 per frame at ~60fps; this matches that time constant).
void tick(float dt);

// Render the globe into a frame-sized FBO (logical orientation) and composite it
// over the currently-bound framebuffer, confined to ps3::gFrame*, with rotMat2
// (DRM GL rotation; pass identity {1,0,0,1} when inactive) and faded in at
// alpha (0 = invisible, 1 = fully opaque, fully covering whatever is behind).
// The caller must have ps3::layoutCompute()'d for this panel first.
void render(int panelW, int panelH, const float rotMat2[4], float alpha);

} // namespace ps3globe
} // namespace android

#endif // GAMMAOS_NANO_PS3_GLOBE_H
