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

// The display colour transform for programs that scan out without SurfaceFlinger.
//
// Android composes every display colour correction (LiveDisplay's colour calibration and
// reading mode, Night Light, the saturation level, colour inversion) into one 4x4 matrix in
// DisplayTransformManager and hands it to SurfaceFlinger, which applies it to the windows it
// composes. The DRM-direct programs (the gammaos-nano home, drastic-nano, azahar-nano) never
// pass through SurfaceFlinger, so the framework also publishes that matrix as
// persist.gammaos.display.color_matrix (twelve values, column-major: the 3x3 and the
// translation column; empty = identity) and they apply it themselves as a final pass over each
// panel's frame. One correction, one setting, every surface.

#pragma once

#include <GLES2/gl2.h>

#include <stdint.h>

namespace android {
namespace colormatrix {

constexpr const char* kProp = "persist.gammaos.display.color_matrix";

struct Matrix {
    float m[12] = { 1, 0, 0,  0, 1, 0,  0, 0, 1,  0, 0, 0 };   // columns 0..2, then the offset
    bool identity = true;
};

// Parses a property value into `out`; an empty or malformed value is the identity.
void parse(const char* value, Matrix* out);

// The current transform. Cheap to call every frame: the property is re-read only when its
// serial changed. Returns true when the matrix differs from the previous call's.
bool poll(Matrix* out);

// Applies `m` to the rectangle (x, y, w, h) of the bound framebuffer in place: the pixels are
// copied to a scratch texture and drawn back through the matrix. Nothing is drawn for the
// identity. Needs a current GL context; leaves no program bound.
void apply(const Matrix& m, int x, int y, int w, int h);

// Drops the GL objects (context loss / shutdown).
void releaseGl();

// ---- per-screen calibration -------------------------------------------------------------
//
// Screen Calibration (Display settings) corrects each panel on its own, on top of LiveDisplay:
// nano turns its persist.gammaos.nano.screencal.<top|bottom>.* values into one matrix per
// screen and publishes them as persist.gammaos.display.screen_matrix.<top|bottom> (same
// format as kProp). SurfaceFlinger folds the top one into its primary display and the bottom
// one into the other, and the DRM-direct programs apply screen x global to each panel. A
// single-screen device uses the top one.

constexpr const char* kScreenPropTop = "persist.gammaos.display.screen_matrix.top";
constexpr const char* kScreenPropBottom = "persist.gammaos.display.screen_matrix.bottom";
constexpr int kTop = 0, kBottom = 1;

// The calibration of one screen: brightness, contrast, saturation, hue (0..100, 50 untouched)
// and the red, green, blue levels (percent, 100 untouched), as a matrix.
void fromCalibration(int brightness, int contrast, int saturation, int hue, int red, int green,
                     int blue, Matrix* out);
// The property text of a matrix (empty for the identity).
void format(const Matrix& m, char* out, size_t outSize);
// out = a * b (b applied first).
void multiply(const Matrix& a, const Matrix& b, Matrix* out);
// The matrix a panel applies: its screen matrix times the global transform. Serial checked
// like poll(); returns true when it changed since the previous call for that screen.
bool pollScreen(int screen, Matrix* out);

}  // namespace colormatrix
}  // namespace android
