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

#include "gtest/gtest.h"

#include <string.h>

#include "chre/util/system/ble_util.h"

TEST(BleUtil, PopulateTxPower) {
  chreBleAdvertisingReport report;
  memset(&report, 0, sizeof(report));
  report.txPower = CHRE_BLE_TX_POWER_NONE;
  report.eventTypeAndDataStatus = CHRE_BLE_EVENT_TYPE_FLAG_LEGACY;
  int8_t txPower = -11;
  uint8_t data[3] = {0x02, 0x0A, static_cast<uint8_t>(txPower)};
  report.data = data;
  report.dataLength = 3;
  chre::populateLegacyAdvertisingReportFields(report);
  EXPECT_EQ(report.txPower, txPower);
}
