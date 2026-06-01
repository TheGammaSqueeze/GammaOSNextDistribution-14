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

// GammaOS Nano PS3 XMB particle glitter field. 1:1 port of the web app's
// firmware-extracted particle system (index.html VS/FS_PARTICLE, spawnParticle,
// stepParticles, buildParticleData, particle_cloud.js): a 1400-point eye-space
// glitter cloud projected with the firmware VP matrix, with a spinning-normal
// specular twinkle, depth-of-field, wave-coupling and bright metallic edge
// reflections. Drawn as additive round point-sprite glints into the ps3bg work
// FBO over the wave (so the composite tonemap + DRM rotation apply uniformly,
// which keeps the field aligned across every aspect ratio / orientation).

#ifndef GAMMAOS_NANO_PS3_PARTICLES_H
#define GAMMAOS_NANO_PS3_PARTICLES_H

namespace android {
namespace ps3part {

// Compile the point-sprite program and seed the particle field. Cheap to call
// repeatedly until ready. Returns true once usable.
bool init();

// Advance the field by dt seconds (fixed 1/60 s substeps, frame-rate safe).
void update(float dt);

// Draw the glitter additively to the PANEL after the composite (so the faint
// HDR glints survive, like the web's post-composite pass). scaleX/scaleY/yFlip
// match the wave (so the band rides it and adapts to the frame aspect); the
// work-space position is then mapped into the frame rect (frameNdc = nx0,ny_bot,
// nx1,ny_top in panel NDC, matching the ps3bg composite quad) and rotated by
// rotMat (DRM GL rotation). frameH is the point-size pixel scale; nightBlend
// brightens at night; waveT advances the wave-coupling undulation.
void render(float scaleX, float scaleY, float yFlip, float frameH,
            float nightBlend, float waveT,
            const float frameNdc[4], const float rotMat[4]);

// Free GL objects.
void shutdown();

} // namespace ps3part
} // namespace android

#endif // GAMMAOS_NANO_PS3_PARTICLES_H
