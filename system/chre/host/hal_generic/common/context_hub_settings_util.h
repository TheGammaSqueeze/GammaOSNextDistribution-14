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

#ifndef ANDROID_HARDWARE_CONTEXTHUB_COMMON_CONTEXT_HUB_SETTINGS_UTIL_H
#define ANDROID_HARDWARE_CONTEXTHUB_COMMON_CONTEXT_HUB_SETTINGS_UTIL_H

#include <cinttypes>

#include <android/hardware/contexthub/1.1/IContexthub.h>
#include <android/hardware/contexthub/1.2/IContexthub.h>

#include "chre_host/host_protocol_host.h"

namespace android {
namespace hardware {
namespace contexthub {
namespace common {
namespace implementation {

/**
 * @param setting The HAL Setting (<= v1.2).
 * @param fbsSetting A non-null pointer where the corresponding flatbuffers
 * settings value will be stored.
 *
 * @return true if the flatbuffers setting value was found and populated.
 */
bool getFbsSetting(
    const ::android::hardware::contexthub::V1_2::Setting &setting,
    ::chre::fbs::Setting *fbsSetting);

/**
 * @param setting The HAL v1.1 SettingValue.
 * @param fbsSetting A non-null pointer where the corresponding flatbuffers
 * setting state value will be stored.
 *
 * @return true if the flatbuffers setting state value was found and populated.
 */
bool getFbsSettingValue(
    const ::android::hardware::contexthub::V1_1::SettingValue &newValue,
    ::chre::fbs::SettingState *fbsState);

}  // namespace implementation
}  // namespace common
}  // namespace contexthub
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_CONTEXTHUB_COMMON_CONTEXT_HUB_SETTINGS_UTIL_H
