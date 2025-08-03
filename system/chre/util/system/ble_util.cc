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

#include "chre/util/system/ble_util.h"

#include <cinttypes>

#include "chre/platform/log.h"

namespace chre {

namespace {

// Tx Power Level AD Type defined in the BT Core Spec v5.3 Assigned Numbers,
// Generic Access Profile (ref:
// https://www.bluetooth.com/specifications/assigned-numbers/).
constexpr uint8_t kTxPowerLevelAdType = 0x0A;
constexpr uint8_t kAdTypeOffset = 1;
constexpr uint8_t kExpectedAdDataLength = 2;

/**
 * Gets the TX Power from the data field of legacy AD reports. This function
 * parses the advertising data which is defined in the BT Core Spec v5.3, Vol 3,
 * Part C, Section 11, Advertising and Scan Response Data Format, and checks for
 * the presence of the Tx Power Level AD Type.
 *
 * @param data Advertising data.
 * @param dataLength Length of advertising data.
 * @return int8_t Tx Power value.
 */
int8_t getTxPowerFromLegacyReport(const uint8_t *data, size_t dataLength) {
  size_t i = 0;
  while (i < dataLength) {
    uint8_t adDataLength = data[i];
    if (adDataLength == 0 || (adDataLength >= dataLength - i)) {
      break;
    }
    if (data[i + kAdTypeOffset] == kTxPowerLevelAdType &&
        adDataLength == kExpectedAdDataLength) {
      return static_cast<int8_t>(data[i + kExpectedAdDataLength]);
    }
    i += kAdTypeOffset + adDataLength;
  }
  return CHRE_BLE_TX_POWER_NONE;
}

}  // namespace

void populateLegacyAdvertisingReportFields(chreBleAdvertisingReport &report) {
  if ((report.eventTypeAndDataStatus & CHRE_BLE_EVENT_TYPE_FLAG_LEGACY) != 0 &&
      report.txPower == CHRE_BLE_TX_POWER_NONE) {
    report.txPower = getTxPowerFromLegacyReport(report.data, report.dataLength);
  }
}

}  // namespace chre
