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

#ifndef CHRE_UTIL_NANOAPP_BLE_H_
#define CHRE_UTIL_NANOAPP_BLE_H_

#include <inttypes.h>

#include "chre_api/chre.h"

namespace chre {

namespace ble_constants {

/**
 * The minimum threshold for RSSI. Used to filter out RSSI values below this.
 */
constexpr int8_t kRssiThreshold = -128;

/**
 * The length of the UUID data at the beginning of the data in the BLE packet.
 */
constexpr uint16_t kGoogleUuidDataLength = 2;

/**
 * The mask to get the UUID from the data in the BLE packet.
 */
constexpr uint8_t kGoogleUuidMask[kGoogleUuidDataLength] = {0xFF, 0xFF};

/**
 * The Google Eddystone BLE beacon UUID.
 */
constexpr uint8_t kGoogleEddystoneUuid[kGoogleUuidDataLength] = {0xAA, 0xFE};

/**
 * The Google Nearby Fastpair BLE beacon UUID.
 */
constexpr uint8_t kGoogleNearbyFastpairUuid[kGoogleUuidDataLength] = {0x2C,
                                                                      0xFE};

/**
 * The number of generic filters (equal to the number of known beacons).
 */
constexpr uint8_t kNumScanFilters = 2;
}  // namespace ble_constants

/**
 * Create a BLE generic filter object.
 *
 * @param type                              the filter type.
 * @param len                               the filter length.
 * @param data                              the filter data.
 * @param mask                              the filter mask.
 * @return                                  the filter.
 */
chreBleGenericFilter createBleGenericFilter(uint8_t type, uint8_t len,
                                            const uint8_t *data,
                                            const uint8_t *mask);

/**
 * Creates a chreBleScanFilter that filters for the Google eddystone UUID,
 * the Google nearby fastpair UUID, and a RSSI threshold of kRssiThreshold.
 *
 * @param filter                            (out) the output filter.
 * @param genericFilters                    (out) the output generic filters
 * array.
 * @param numGenericFilters                 the size of the generic filters
 * array. must be >= kNumScanFilters.
 *
 * @return true                             the operation was successful
 * @return false                            the operation was not successful
 */
bool createBleScanFilterForKnownBeacons(struct chreBleScanFilter &filter,
                                        chreBleGenericFilter *genericFilters,
                                        uint8_t numGenericFilters);

/**
 * Similar to createBleScanFilterForKnownBeacons but creates a
 * chreBleScanFilterV1_9 instead of a chreBleScanFilter. The
 * broadcasterAddressFilters are set to empty.
 */
bool createBleScanFilterForKnownBeaconsV1_9(
    struct chreBleScanFilterV1_9 &filter, chreBleGenericFilter *genericFilters,
    uint8_t numGenericFilters);

}  // namespace chre

#endif  // CHRE_UTIL_NANOAPP_BLE_H_
