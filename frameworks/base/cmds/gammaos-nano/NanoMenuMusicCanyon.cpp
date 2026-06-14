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

// Canyon visualizer - implementation filled in Phase 5. Stub for now so the build
// wiring (Android.bp srcs) is in place and the Now-Playing screen can reference the
// ps3canyon API without a second glob-regenerating build-file change later.

#include "NanoMenuMusicCanyon.h"

namespace android {
namespace ps3canyon {

bool init() { return false; }
void shutdown() {}
bool ready() { return false; }
void reset() {}
void render(int, int, const float[4], float, float, const nanoaudio::Bands&) {}

} // namespace ps3canyon
} // namespace android
