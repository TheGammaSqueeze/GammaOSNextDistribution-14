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
 */

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_MOCK_BLE_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_MOCK_BLE_H_

#include <chre.h>

namespace nearby {

struct MockBle {
#if defined(MOCK_FAST_PAIR)
  // A BLEScanRecord consists of one advertisement of Fast Pair initial pair.
  static constexpr uint8_t kBleScanRecordData[] = {
      // Advertisement.
      6,     // byte length of ad below.
      0x16,  // type of ad data (service data).
      0x2C,  // 2 bytes uuid in little-endian (Fast Pair).
      0xFE,
      0x1F,  // 3 bytes Fast Pair initial pair ervice data (model ID).
      0xD7,  // second byte of model ID.
      0xD0,  // third byte of model ID.
  };
#elif defined(MOCK_SUBSEQUENT_PAIR)
  // A BLEScanRecord consists of one advertisement of Fast Pair subsequent pair.
  static constexpr uint8_t kBleScanRecordData[] = {
      // Advertisement.
      12,    // byte length of ad below.
      0x16,  // type of ad data (service data).
      0x2C,  // 2 bytes uuid in little-endian (Fast Pair).
      0xFE,
      0x00,  // Version 0 with Flag 0
      0x40,  // 4 bytes Bloom Filter
      0x02, 0x0C, 0x80, 0x2A,
      0x21,  // 2 bytes salt.
      0xC7, 0xC8,
  };
#elif defined(MOCK_PRESENCE_V0)
  // A BLEScanRecord consists of one advertisement of Presence V0.
  static constexpr uint8_t kBleScanRecordData[] = {
      // Advertisement.
      0x0B,  // byte length of ad below.
      0x16,  // type of ad data (service data).
      0xF1,  // uuid in little-endian (Nearby Presence)
      0xFC,
      // Presence service data below.
      0b00100100,  // service data header (format 0bVVVLLLLR) with 2 fields.
      // Intent field below, 1 byte header plus 2 byte value.
      0b00100101,  // field header with 0b0101 type
      1,           // first intent as 1
      5,           // second intent as 5
      // Model ID, 3 bytes length with 0b0111 type.
      0b00110111,
      0b00000001,
      0b00000010,
      0b00000100,
  };
#else
  // A BLEScanRecord consists of one advertisement of Presence V1.
#define IDENTITY_VALUE 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
#define DE_SIGNATURE 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
  static constexpr uint8_t kBleScanRecordData[] = {
      // Advertisement.
      51,    // byte length of ad below.
      0x16,  // type of ad data (service data).
      0xF1,  // uuid in little-endian (Nearby Presence)
      0xFC,
      // Presence service data below.
      0b00100000,  // Header with version v1.
      0b00100000,  // Salt header: length 2, type 0
      2,
      3,           // Salt value.
      0b10010000,  // Identity header: length 16, type 4
      0b00000100,
      IDENTITY_VALUE,  // Identity value: 16 bytes.
      0b00010110,      // Action header: length 1, type 6
      1,
      0b00010110,  // Action header: length 1, type 6
      124,
      0b00010101,  // TX power header: length 1, type 5
      20,
      0b00110111,  // Model ID header: length 3, type 7
      0, 1, 2,
      DE_SIGNATURE,  // Data Element signature: 16 bytes
  };
#endif

  static constexpr chreBleAdvertisingReport kReport = {
      .address = {1, 2, 3, 4, 5, 6},
      .txPower = 20,
      .rssi = 10,
      .directAddress = {1, 2, 3, 4, 5, 6},
      .dataLength = sizeof(kBleScanRecordData),
      .data = kBleScanRecordData,
  };
  static constexpr chreBleAdvertisementEvent kBleEvent = {
      .numReports = 1,
      .reports = &kReport,
  };
  static constexpr chreAsyncResult kBleFlushCompleteEvent = {
      .requestType = CHRE_BLE_REQUEST_TYPE_FLUSH,
      .success = true,
      .errorCode = CHRE_ERROR_NONE,
      .reserved = 0,
      .cookie = nullptr};
  static constexpr chreBatchCompleteEvent kBleBatchCompleteEvent = {
      .eventType = CHRE_EVENT_BLE_ADVERTISEMENT};
#ifdef MOCK_BLE_BATCH_SCAN
  static constexpr bool kBleBatchScanSupported = true;
#else
  static constexpr bool kBleBatchScanSupported = false;
#endif
  static constexpr uint32_t kBleFlushCompleteTimeoutMs = 50;
  static constexpr uint32_t kBleFlushScanResultIntervalMs = 10;
  static constexpr uint32_t kBleReportDelayMinMs = 10;
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_MOCK_BLE_H_
