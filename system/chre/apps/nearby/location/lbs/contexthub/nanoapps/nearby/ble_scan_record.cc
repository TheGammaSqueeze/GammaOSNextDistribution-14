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
 * The implementation below follows BLE Core Spec 5.3 from
 * https://www.bluetooth.com/specifications/specs/core-specification/
 * Part C Generic Access Profile,
 *   Section 11 Advertisement and Scan Response Format ... Page 1357.
 * Also follows the Java reference implementation in
 * https://android.googlesource.com/platform/frameworks/base/+/b661bb7/core/java/android/bluetooth/le/ScanRecord.java
 */

#include "location/lbs/contexthub/nanoapps/nearby/ble_scan_record.h"

#include <chre.h>

namespace nearby {

BleScanRecord BleScanRecord::Parse(const uint8_t data[], const uint16_t size) {
  BleScanRecord record;

  // Scans through data and parses each advertisement.
  for (int i = 0; i < size;) {
    // First byte has the advertisement data length.
    uint8_t ad_data_length = data[i];
    // Early termination with zero length advertisement.
    if (ad_data_length == 0) break;
    // Terminates when advertisement length passes over the end of data buffer.
    if (ad_data_length >= size - i) break;
    // Second byte has advertisement data type.
    // Only retrieves service data for Nearby NanoApp.
    // Also, skip advertisement less than 3 bytes since a non-empty service data
    // include 2 bytes UUID plus at least 1 byte data.
    if (data[++i] == kDataTypeServiceData && ad_data_length >= 3) {
      BleServiceData service_data;
      // First two bytes of the service data are service data UUID in little
      // endian.
      service_data.uuid =
          static_cast<uint16_t>(data[i + 1] + (data[i + 2] << 8));
      // Third byte starts service data after uuid.
      service_data.data = &data[i + 3];
      // service data length is the total data length minus one byte of type
      // and two bytes of uuid.
      service_data.length = ad_data_length - 3;
      record.service_data.push_back(service_data);
    }
    // Moves to next advertisement.
    i += ad_data_length;
  }

  return record;
}

}  // namespace nearby
