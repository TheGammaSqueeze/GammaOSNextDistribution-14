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

// Canyon music visualizer (music vis 1): a 1:1 port of the web app's canyon_port.js
// terrain flythrough. Lazy GL module modeled on ps3globe: a static XZ lattice whose
// per-vertex height is precomputed on the CPU from the canyon normalmap (no vertex
// texture fetch, since nano runs a strict GLES2 context), rendered in 3 passes
// (terrain -> feedback motion blur -> Reinhard tonemap) into a frame FBO, then
// composited to the panel with the DRM rotation. Audio-reactive: bass -> camera
// speed, mid -> tonemap exposure. Active only inside the Now-Playing screen.

#ifndef GAMMAOS_NANO_MUSIC_CANYON_H
#define GAMMAOS_NANO_MUSIC_CANYON_H

#include <GLES2/gl2.h>

#include "NanoMenuAudioBands.h"

namespace android {
namespace ps3canyon {

// Compile programs, decode the normalmap, build the lattice + FBOs. Lazy: called on
// first Canyon use inside Now-Playing. Cheap to call repeatedly until ready.
bool init();

// Free all GL objects (call on leaving Now-Playing).
void shutdown();

// True once init() has fully succeeded.
bool ready();

// Reset the camera/preset to the start (call when switching to Canyon).
void reset();

// Render the visualizer to the currently-bound framebuffer at `alpha` (the
// visualizer crossfade), advancing by dt, driven by the audio bands. rotMat2 is the
// DRM GL rotation (identity {1,0,0,1} when inactive).
void render(int panelW, int panelH, const float rotMat2[4], float alpha,
            float dt, const nanoaudio::Bands& bands);

} // namespace ps3canyon
} // namespace android

#endif // GAMMAOS_NANO_MUSIC_CANYON_H
