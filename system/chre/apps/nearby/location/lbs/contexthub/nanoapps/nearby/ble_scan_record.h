/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 * Simplified BLE Scan Record implementation for Nearby nanoapp only.
 */

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BLE_SCAN_RECORD_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BLE_SCAN_RECORD_H_

#include <stddef.h>

#include <cstdint>

#include "third_party/contexthub/chre/util/include/chre/util/dynamic_vector.h"
#include "third_party/contexthub/chre/util/include/chre/util/optional.h"

namespace nearby {

// BLE service data with its UUID.
struct BleServiceData {
  // See 16-bit UUIDs in
  // https://www.bluetooth.com/specifications/assigned-numbers/
  uint16_t uuid;

  // length of service data, which is always less than 256, the max size of BLE
  // advertisement.
  uint8_t length;
  // byte array of service data, or null if length is 0.
  const uint8_t *data;
};

struct BleScanRecord {
  // Returns a BLE scan record by parsing the data.
  // Note, the returned BleScanRecord has dependence on the lifetime of data,
  // and the return will be invalid if data is altered/destructed after parsing.
  static BleScanRecord Parse(const uint8_t data[], const uint16_t size);
  chre::DynamicVector<BleServiceData> service_data;

  static constexpr int8_t kDataTypeServiceData = 0x16;
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BLE_SCAN_RECORD_H_
