/*
 * Copyright (C) 2020 The Android Open Source Project
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

#pragma once

#include <map>

#include <android-base/result.h>
#include <stdint.h>
#include <vintf/Level.h>

namespace android::vintf::testing {

// Return success if:
// - |shipping_api_level| is not 0
// - |shipping_api_level| is a recognized value
// - |shipping_fcm_version| >= required target FCM version for
// |shipping_api_level|.
// Otherwise return error with message.
android::base::Result<void> TestTargetFcmVersion(Level shipping_fcm_version,
                                                 uint64_t shipping_api_level);

// Map VINTF levels to API levels.
android::base::Result<Level> GetFcmVersionFromApiLevel(uint64_t api_level);

}  // namespace android::vintf::testing
