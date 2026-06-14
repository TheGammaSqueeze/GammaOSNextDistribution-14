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

// Music Globe visualizer (music vis 2) - a compact port of the real XMB web globe
// (globe_mp.js / MPGlobe), reproducing its STYLE and EFFECTS rather than the
// byte-exact firmware pipeline (whose 2.1GB of captured DXT1 patch tiles + HDR LUTs
// + per-scene bloom harvests cannot fit on the device). It renders a free-camera
// ray-marched earth (day/night/cloud/terminator/ocean-glint, shipped equirect maps)
// + the analytic atmosphere limb glow + the anisotropic sun disc into an RGBA8 scene
// buffer, then runs the signature bright-pass + Gaussian bloom pyramid (the golden
// corona / eclipse burst) and composites to the panel with the DRM rotation + a
// cross-fade alpha. The camera REPLAYS the real firmware camera paths for scenes 0-4
// (decoded from the scene JSONs' captured MVP registers), cycling with a fade.

#ifndef GAMMAOS_NANO_MUSIC_GLOBE_H
#define GAMMAOS_NANO_MUSIC_GLOBE_H

#include <GLES2/gl2.h>

#include "NanoMenuAudioBands.h"

namespace android {
namespace ps3mpglobe {

// Compile programs, load the earth maps, build the FBOs. Lazy: called on first use
// inside Now-Playing. Cheap to call repeatedly until ready.
bool init();

// Free all GL objects (call on leaving Now-Playing).
void shutdown();

// True once init() has fully succeeded.
bool ready();

// Restart the scene cycle from scene 0 (call when switching to the Globe).
void reset();

// Render the visualizer to the currently-bound framebuffer at `alpha` (the visualizer
// crossfade), advancing by dt, driven by the audio bands. rotMat2 is the DRM GL
// rotation (identity {1,0,0,1} when inactive).
void render(int panelW, int panelH, const float rotMat2[4], float alpha,
            float dt, const nanoaudio::Bands& bands);

} // namespace ps3mpglobe
} // namespace android

#endif // GAMMAOS_NANO_MUSIC_GLOBE_H
