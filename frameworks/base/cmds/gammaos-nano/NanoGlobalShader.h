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

// The GammaOS global shader (persist.gammaos.shader.*) for the DRM-direct home.
//
// In Android, SurfaceFlinger's RenderEngine post-processes every composited frame with the
// global shader: the builtin CRT / LCD3x / LCD / blur-fill effects, or a RetroArch preset
// (.slangp through its own chain, .glslp through a GL chain). The home scans out through DRM
// without a compositor, so nothing applied it there. This module is that post-process for the
// home's own panels: the same properties, the same effects and the same preset handling
// (orientation, resolution scale, scanline density, parameters, LUTs, feedback and history),
// run on the home's GL context over each panel's finished frame.
//
// drastic-nano and azahar-nano keep their own screen shaders; this is the home only.

#pragma once

#include <GLES3/gl3.h>

namespace android {
namespace gshader {

// Reads the properties (a serial check per frame, the values once per change). True while a
// global shader is on and recognised: the panels then render through the scratch and apply()
// runs over each. Call once per frame on the render thread.
bool poll();

// Post-processes one panel's finished frame. `src` is the panel-native texture (w x h, GL
// bottom-left rows), `installDeg` the panel's install rotation so scanline effects run upright.
// Returns the processed texture in panel space (w x h), or 0 to present `src` unchanged (the
// effect is off, failed, or not ready). Textures returned stay valid until the next call for
// that panel. GL context required.
GLuint apply(int panel, GLuint src, int w, int h, int installDeg);

// Frees every GL object (context loss / shutdown).
void releaseGl();

}  // namespace gshader
}  // namespace android
