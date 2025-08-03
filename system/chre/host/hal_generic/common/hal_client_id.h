/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ANDROID_HARDWARE_CONTEXTHUB_COMMON_HAL_CLIENT_ID_H_
#define ANDROID_HARDWARE_CONTEXTHUB_COMMON_HAL_CLIENT_ID_H_

#include <cstdint>
#include <limits>

namespace android::hardware::contexthub::common::implementation {

using HalClientId = uint16_t;

/** The max HAL client Id. */
constexpr HalClientId kMaxHalClientId = 0x1ff;

/** Max number of HAL clients supported. */
constexpr uint16_t kMaxNumOfHalClients = kMaxHalClientId - 1;

/**
 * The HAL client id indicating the message is actually sent to the HAL itself.
 */
constexpr HalClientId kHalId = kMaxHalClientId;

}  // namespace android::hardware::contexthub::common::implementation

#endif  // ANDROID_HARDWARE_CONTEXTHUB_COMMON_HAL_CLIENT_ID_H_
