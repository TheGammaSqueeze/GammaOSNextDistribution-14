/*
 * Copyright 2021 The Android Open Source Project
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

#ifdef __cplusplus
#include <cstdint>
#include <string>
#else
#include <stdint.h>
#endif

/* Device Types
 */
enum {
  BT_DEVICE_TYPE_UNKNOWN = 0,
  BT_DEVICE_TYPE_BREDR = (1 << 0),
  BT_DEVICE_TYPE_BLE = (1 << 1),
  BT_DEVICE_TYPE_DUMO = BT_DEVICE_TYPE_BREDR | BT_DEVICE_TYPE_BLE,
};
typedef uint8_t tBT_DEVICE_TYPE;

#ifdef __cplusplus
inline std::string DeviceTypeText(tBT_DEVICE_TYPE type) {
  switch (type) {
    case BT_DEVICE_TYPE_BREDR:
      return std::string("BR_EDR");
    case BT_DEVICE_TYPE_BLE:
      return std::string("BLE");
    case BT_DEVICE_TYPE_DUMO:
      return std::string("DUAL");
    default:
      return std::string("Unknown");
  }
}
#endif  // __cplusplus
