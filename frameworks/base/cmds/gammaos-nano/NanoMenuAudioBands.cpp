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

#include "NanoMenuAudioBands.h"

namespace android {
namespace nanoaudio {

// Single-writer (UI/Now-Playing tick) / single-reader (render thread, same thread
// in nano's single-render-thread model) state. No locking needed: setBands and the
// visualizers both run on the render thread within one frame.
static Bands sBands;

void setBands(const Bands& b) { sBands = b; sBands.valid = true; }
const Bands& current() { return sBands; }
void clearBands() { sBands = Bands(); }

} // namespace nanoaudio
} // namespace android
