/*
 * Copyright 2020 The Android Open Source Project
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

#include <cstdint>
#include <optional>
#include <string>

#define DEBUGGABLE_SYS_PROP_NAME "ro.debuggable"

namespace bluetooth {
namespace os {

/* System Property to indicate if the dual mode audio feature is enabled */
static const std::string kIsDualModeAudioEnabledProperty =
    "persist.bluetooth.enable_dual_mode_audio";

// Get |property| keyed system property from supported platform, return std::nullopt if the property does not exist
// or if the platform does not support system property
std::optional<std::string> GetSystemProperty(const std::string& property);

// Get |property| keyed system property as uint32_t from supported platform, return |default_value| if the property
// does not exist or if the platform does not support system property
uint32_t GetSystemPropertyUint32(const std::string& property, uint32_t default_value);

// Get |property| keyed system property as uint32_t from supported platform, return |default_value|
// if the property does not exist or if the platform does not support system property if property is
// found it will call stoul with |base|
uint32_t GetSystemPropertyUint32Base(
    const std::string& property, uint32_t default_value, int base = 0);

// Get |property| keyed property as bool from supported platform, return
// |default_value| if the property does not exist or if the platform
// does not support system property
bool GetSystemPropertyBool(const std::string& property, bool default_value);

// Set |property| keyed system property to |value|, return true if the set was successful and false if the set failed
// Replace existing value if property already exists
bool SetSystemProperty(const std::string& property, const std::string& value);

// Clear system properties for host only return true on success
bool ClearSystemPropertiesForHost();

// Check if the vendor image is using root canal simulated Bluetooth stack
bool IsRootCanalEnabled();

// Get Android Vendor Image release version in numeric value (e.g. Android R is 11), return 0 if not on Android or
// version not available
int GetAndroidVendorReleaseVersion();

}  // namespace os
}  // namespace bluetooth
