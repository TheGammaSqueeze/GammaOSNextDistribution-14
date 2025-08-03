/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef ANDROID_HARDWARE_CONTEXTHUB_COMMON_PERMISSIONS_UTIL_H
#define ANDROID_HARDWARE_CONTEXTHUB_COMMON_PERMISSIONS_UTIL_H

#include <string>
#include <vector>

namespace android {
namespace hardware {
namespace contexthub {
namespace common {
namespace implementation {

// List of permissions supported by this HAL.
const std::string kBackgroundLocationPerm =
    "android.permission.ACCESS_BACKGROUND_LOCATION";
const std::string kFineLocationPerm = "android.permission.ACCESS_FINE_LOCATION";
const std::string kRecordAudioPerm = "android.permission.RECORD_AUDIO";
const std::string kBluetoothScanPerm = "android.permission.BLUETOOTH_SCAN";
const std::vector<std::string> kSupportedPermissions = {
    kBackgroundLocationPerm, kFineLocationPerm, kRecordAudioPerm,
    kBluetoothScanPerm};

//! Converts the CHRE permissions bitmask to a list of CHRE permissions.
std::vector<std::string> chreToAndroidPermissions(uint32_t chrePermissions);

}  // namespace implementation
}  // namespace common
}  // namespace contexthub
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_CONTEXTHUB_COMMON_PERMISSIONS_UTIL_H
