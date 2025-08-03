/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ANDROID_VINTF_LEVEL_H
#define ANDROID_VINTF_LEVEL_H

#include <stddef.h>
#include <stdint.h>
#include <array>
#include <string>

namespace android {
namespace vintf {

// Manifest and Compatibility Matrix Level, a.k.a FCM Version, is a number assigned to each
// manifest / matrix.
// - For manifest, the FCM Version that it implements
// - For matrix, the single FCM Version that this matrix file details.
enum class Level : size_t {
    // LINT.IfChange
    // Non-Treble devices.
    LEGACY = 0,
    // Actual values starts from 1.
    O = 1,
    O_MR1 = 2,
    P = 3,
    Q = 4,
    R = 5,
    S = 6,
    T = 7,
    U = 8,
    V = 202404,
    // To add new values:
    // (1) add above this line.
    // (2) edit array below
    // (3) edit:
    // - RuntimeInfo::gkiAndroidReleaseToLevel
    // - analyze_matrix.cpp, GetDescription()
    // LINT.ThenChange(/analyze_matrix/analyze_matrix.cpp)

    // For older manifests and compatibility matrices, "level" is not specified.
    UNSPECIFIED = SIZE_MAX,
};

inline bool IsValid(Level level) {
    constexpr std::array kValidLevels = {
        // clang-format off
        Level::LEGACY,
        Level::O,
        Level::O_MR1,
        Level::P,
        Level::Q,
        Level::R,
        Level::S,
        Level::T,
        Level::U,
        Level::V,
        Level::UNSPECIFIED,
        // clang-format on
    };

    return std::find(kValidLevels.begin(), kValidLevels.end(), level) != kValidLevels.end();
}

std::string GetDescription(Level level);

}  // namespace vintf
}  // namespace android

#endif  // ANDROID_VINTF_LEVEL_H
