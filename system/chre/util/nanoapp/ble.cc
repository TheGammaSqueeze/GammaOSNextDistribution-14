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

#include "chre/util/nanoapp/ble.h"

namespace chre {

using ble_constants::kGoogleEddystoneUuid;
using ble_constants::kGoogleNearbyFastpairUuid;
using ble_constants::kGoogleUuidDataLength;
using ble_constants::kGoogleUuidMask;
using ble_constants::kNumScanFilters;
using ble_constants::kRssiThreshold;

chreBleGenericFilter createBleGenericFilter(uint8_t type, uint8_t len,
                                            const uint8_t *data,
                                            const uint8_t *mask) {
  chreBleGenericFilter filter;
  memset(&filter, 0, sizeof(filter));
  filter.type = type;
  filter.len = len;
  memcpy(filter.data, data, sizeof(uint8_t) * len);
  memcpy(filter.dataMask, mask, sizeof(uint8_t) * len);
  return filter;
}

bool createBleScanFilterForKnownBeacons(struct chreBleScanFilter &filter,
                                        chreBleGenericFilter *genericFilters,
                                        uint8_t numGenericFilters) {
  if (numGenericFilters < kNumScanFilters) {
    return false;
  }
  memset(&filter, 0, sizeof(filter));

  genericFilters[0] = createBleGenericFilter(
      CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE, kGoogleUuidDataLength,
      kGoogleEddystoneUuid, kGoogleUuidMask);
  genericFilters[1] = createBleGenericFilter(
      CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE, kGoogleUuidDataLength,
      kGoogleNearbyFastpairUuid, kGoogleUuidMask);

  filter.rssiThreshold = kRssiThreshold;
  filter.scanFilterCount = kNumScanFilters;
  filter.scanFilters = genericFilters;
  return true;
}

bool createBleScanFilterForKnownBeaconsV1_9(
    struct chreBleScanFilterV1_9 &filter, chreBleGenericFilter *genericFilters,
    uint8_t numGenericFilters) {
  if (numGenericFilters < kNumScanFilters) {
    return false;
  }
  memset(&filter, 0, sizeof(filter));

  genericFilters[0] = createBleGenericFilter(
      CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE, kGoogleUuidDataLength,
      kGoogleEddystoneUuid, kGoogleUuidMask);
  genericFilters[1] = createBleGenericFilter(
      CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE, kGoogleUuidDataLength,
      kGoogleNearbyFastpairUuid, kGoogleUuidMask);

  filter.rssiThreshold = kRssiThreshold;
  filter.genericFilterCount = kNumScanFilters;
  filter.genericFilters = genericFilters;

  filter.broadcasterAddressFilterCount = 0;
  filter.broadcasterAddressFilters = nullptr;
  return true;
}

}  // namespace chre
