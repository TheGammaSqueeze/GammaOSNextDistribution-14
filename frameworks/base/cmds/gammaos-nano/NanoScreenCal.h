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

// Screen Calibration for the Anbernic RG DS and RG DS Plus (Display > Screen Calibration).
//
// Both screens are corrected by the RK356x display controller (VOP2) after composition, so the
// correction covers everything on the panels: the home, drastic-nano's DS games (which bypass
// SurfaceFlinger and therefore LiveDisplay) and Android apps alike, at no GPU cost.
//
//   Brightness, contrast, saturation, hue: the per-port BCSH block, on both screens
//     (the connector properties brightness/contrast/saturation/hue, 50 = untouched).
//   Red, green, blue levels: the RK3568 VOP has ONE gamma LUT, usable by one port at a time
//     (nr_gammas = 1), and a 9x9x9 3D LUT on port 0 only. The bottom screen (port 0) therefore
//     gets its levels through the 3D LUT and the top screen (port 1) through the gamma LUT.
//
// The settings are nano's own properties (persist.gammaos.nano.screencal.<top|bottom>.<field>).
// They are applied live, re-applied at boot and after wake, and mirrored into the baseparameter
// partition, which u-boot reads (it hands BCSH to the kernel with the boot logo) and the Rockchip
// composer re-applies at boot and on every screen-on, so the correction holds from power on.
// The composer maps baseparameter's DSI records wrongly for the second screen, so each screen's
// BCSH is also mirrored into its per-display properties (persist.vendor.*.main / .aux), which it
// prefers.

#pragma once

#include <string>

namespace android {
namespace screencal {

constexpr const char* kPropPrefix = "persist.gammaos.nano.screencal.";

// True on the RG DS and RG DS Plus (ro.gammaos.device), the only devices this applies to.
bool supported();

// Apply the stored settings to both screens now (live), then mirror them into baseparameter
// after a short quiet period. Cheap to call repeatedly; does nothing on other devices.
void applyFromProps();

// Slider preview: apply the stored settings with `key` temporarily at `value`, without writing
// anything. The dialog's cancel reverts with applyFromProps().
void preview(const std::string& key, const std::string& value);

// True when `key` is one of the Screen Calibration properties.
bool isCalibrationKey(const std::string& key);

// Menu actions.
void copyTopToBottom();
void resetAll();

}  // namespace screencal
}  // namespace android
