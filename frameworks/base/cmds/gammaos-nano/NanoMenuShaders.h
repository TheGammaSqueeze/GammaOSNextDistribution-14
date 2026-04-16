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

#ifndef GAMMAOS_NANO_MENU_SHADERS_H
#define GAMMAOS_NANO_MENU_SHADERS_H

#include <GLES2/gl2.h>

namespace android {

// ---------------------------------------------------------------------------
// Shared shader sources (implementations in NanoMenuShaders.cpp)
// ---------------------------------------------------------------------------

extern const char VERTEX_SHADER[];
extern const char FRAGMENT_SHADER[];
extern const char PARTICLE_VERTEX_SHADER[];
extern const char PARTICLE_FRAGMENT_SHADER[];
extern const char FX_VERTEX_SHADER[];
extern const char FX_FRAGMENT_SHADER[];
extern const char XMB_FRAGMENT_SHADER[];
extern const char TEXT_VERTEX_SHADER[];
extern const char TEXT_FRAGMENT_SHADER[];

// ---------------------------------------------------------------------------
// Shader compile + link helpers
// ---------------------------------------------------------------------------

GLuint compileShader(GLenum type, const char* source);
GLuint linkProgram(GLuint vs, GLuint fs);

// ---------------------------------------------------------------------------
// Effect tables (shared across NanoMenu.cpp input handlers and NanoMenu ctor)
// ---------------------------------------------------------------------------

// All effect IDs 0..NUM_EFFECTS (NUM_EFFECTS from NanoMenu.h).
extern const char* kEffectNames[];
// Subset of active effects the user cycles through (disables e.g. Dust,
// Static, Scanlines, Mosaic, Matrix).
extern const int kActiveEffects[];
extern const int kNumActiveEffects;
// Current index into kActiveEffects (NOT the effect ID itself). Persisted
// across ctor/threadLoop startup by a property, mutated by input handlers.
extern int sActiveEffectIdx;

// ---------------------------------------------------------------------------
// Font layout constants used by both primary NanoMenu rendering and the XMB
// translation unit. Kept here so both TUs include the same constant.
// ---------------------------------------------------------------------------

static const int FONT_CHAR_W = 8;
static const int FONT_CHAR_H = 16;

} // namespace android

#endif // GAMMAOS_NANO_MENU_SHADERS_H
