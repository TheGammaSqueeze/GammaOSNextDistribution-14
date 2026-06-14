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

// Tiny decoupling bridge between the audio engine (producer) and the music
// visualizers (consumers). The Now-Playing tick calls setBands() once per frame
// with the FFT result from NanoAudioPlayer::getBands(); the visualizers
// (ps3canyon, and optionally ps3bg) read current() during render. This keeps the
// visualizer modules free of any audio dependency.

#ifndef GAMMAOS_NANO_AUDIO_BANDS_H
#define GAMMAOS_NANO_AUDIO_BANDS_H

namespace android {
namespace nanoaudio {

struct Bands {
    float bass = 0.0f;
    float mid = 0.0f;
    float treble = 0.0f;
    float bins[256] = {0.0f};
    bool valid = false;
};

// Publish the latest FFT frame (called by the Now-Playing tick on the UI thread).
void setBands(const Bands& b);

// Read the latest published bands (called by visualizers on the render thread).
// Returns a zeroed {valid=false} until the first setBands().
const Bands& current();

// Reset to {valid=false} (call when leaving Now-Playing so a stale frame does not
// drive a visualizer briefly on the next entry).
void clearBands();

} // namespace nanoaudio
} // namespace android

#endif // GAMMAOS_NANO_AUDIO_BANDS_H
