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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_FILTER_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_FILTER_H_

#include "location/lbs/contexthub/nanoapps/nearby/proto/ble_filter.nanopb.h"
#include "third_party/contexthub/chre/util/include/chre/util/dynamic_vector.h"

namespace nearby {

// Filter monitors BLE events and notifies host when an event matches the host
// interest.
class Filter {
  friend class FilterTest;

 public:
  // Updates filters with new rules. Returns false if message cannot be decoded
  // as nearby_Filters.
  bool Update(const uint8_t *message, uint32_t message_size);
  // Returns true when filters are cleared.
  bool IsEmpty() {
    return ble_filters_.filter_count == 0;
  }

  // Matches a BLE advertisement report against BLE Filters.
  // Returns matched result in filter_results, which includes a BleFilterResult
  // when an advertisement matches a Filter.
  // Fast Pair filter result is returned separately in fp_filter_results.
  void MatchBle(const chreBleAdvertisingReport &report,
                chre::DynamicVector<nearby_BleFilterResult> *filter_results,
                chre::DynamicVector<nearby_BleFilterResult> *fp_filter_results);

 private:
  nearby_BleFilters ble_filters_ = nearby_BleFilters_init_zero;
  // BLE Scan interval. Default to 1 minute.
  uint64_t scan_interval_ms_ = 60 * 1000;
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_FILTER_H_
