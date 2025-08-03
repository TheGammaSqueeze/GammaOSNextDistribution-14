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

#include "gtest/gtest.h"

#include "chre/core/ble_request.h"
#include "chre/core/ble_request_manager.h"
#include "chre_api/chre/ble.h"

namespace chre {

class BleRequestManagerTest : public ::testing::Test {
 protected:
  bool validateParams(const BleRequest &request) {
    return BleRequestManager::validateParams(request);
  }
};

TEST_F(BleRequestManagerTest, ValidateParamsSuccess) {
  chreBleGenericFilter filter;
  filter.type = CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE;
  filter.len = 1;
  filter.data[0] = 0x8c;
  filter.dataMask[0] = 0xfe;
  chreBleScanFilterV1_9 scanFilter{
      .rssiThreshold = CHRE_BLE_RSSI_THRESHOLD_NONE,
      .genericFilterCount = 1,
      .genericFilters = &filter};
  BleRequest request(
      /* instanceId */ 0, /* enable */ true, CHRE_BLE_SCAN_MODE_BACKGROUND,
      /* reportDelayMs */ 0, &scanFilter, /* cookie */ nullptr);
  EXPECT_TRUE(validateParams(request));
}

TEST_F(BleRequestManagerTest, ValidateParamsFailureMatchingMaskedData) {
  chreBleGenericFilter filter;
  filter.type = CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE;
  filter.len = 1;
  filter.data[0] = 0x8c;
  filter.dataMask[0] = 0x0c;
  chreBleScanFilterV1_9 scanFilter{
      .rssiThreshold = CHRE_BLE_RSSI_THRESHOLD_NONE,
      .genericFilterCount = 1,
      .genericFilters = &filter};
  BleRequest request(
      /* instanceId */ 0, /* enable */ true, CHRE_BLE_SCAN_MODE_BACKGROUND,
      /* reportDelayMs */ 0, &scanFilter, /* cookie */ nullptr);
  EXPECT_FALSE(validateParams(request));
}

}  // namespace chre
