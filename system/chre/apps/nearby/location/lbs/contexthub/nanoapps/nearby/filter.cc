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

#include "location/lbs/contexthub/nanoapps/nearby/filter.h"

#include <chre/util/macros.h>
#include <inttypes.h>
#include <pb_decode.h>

#include <cstdint>
#include <iterator>

#include "chre_api/chre.h"
#include "location/lbs/contexthub/nanoapps/nearby/ble_scan_record.h"
#include "location/lbs/contexthub/nanoapps/nearby/fast_pair_filter.h"
#ifdef ENABLE_PRESENCE
#include "location/lbs/contexthub/nanoapps/nearby/presence_crypto_identity_v1.h"
#include "location/lbs/contexthub/nanoapps/nearby/presence_crypto_v1.h"
#include "location/lbs/contexthub/nanoapps/nearby/presence_filter.h"
#endif
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#define LOG_TAG "[NEARBY][FILTER]"

namespace nearby {

constexpr nearby_BleFilters kDefaultBleFilters = nearby_BleFilters_init_zero;

bool Filter::Update(const uint8_t *message, uint32_t message_size) {
  LOGD("Decode a Filters message with size %" PRIu32, message_size);
  ble_filters_ = kDefaultBleFilters;
  pb_istream_t stream = pb_istream_from_buffer(message, message_size);
  if (!pb_decode(&stream, nearby_BleFilters_fields, &ble_filters_)) {
    LOGE("Failed to decode a Filters message.");
    return false;
  }
  // Print filters for debug.
  LOGD_SENSITIVE_INFO("BLE filters counter %d", ble_filters_.filter_count);
  if (ble_filters_.filter_count > 0) {
    LOGD_SENSITIVE_INFO("BLE filter 0 data element count %d",
                        ble_filters_.filter[0].data_element_count);
    if (ble_filters_.filter[0].data_element_count > 0) {
      LOGD_SENSITIVE_INFO(
          "Data Element 0, key: %" PRIi32
          " value[0]: %d,"
          " has key: %d, has value: %d, has value length %d,"
          " value length %" PRIu32,
          ble_filters_.filter[0].data_element[0].key,
          ble_filters_.filter[0].data_element[0].value[0],
          ble_filters_.filter[0].data_element[0].has_key,
          ble_filters_.filter[0].data_element[0].has_value,
          ble_filters_.filter[0].data_element[0].has_value_length,
          ble_filters_.filter[0].data_element[0].value_length);
    }
  }
  for (int i = 0; i < ble_filters_.filter_count; i++) {
    const nearby_BleFilter *filter = &ble_filters_.filter[i];
    // Sets the scan interval to satisfy the minimal latency requirement.
    if (filter->has_latency_ms && filter->latency_ms < scan_interval_ms_) {
      scan_interval_ms_ = filter->latency_ms;
    }
  }
  return true;
}

void Filter::MatchBle(
    const chreBleAdvertisingReport &report,
    chre::DynamicVector<nearby_BleFilterResult> *filter_results,
    chre::DynamicVector<nearby_BleFilterResult> *fp_filter_results) {
#ifndef ENABLE_PRESENCE
  UNUSED_VAR(filter_results);
#endif
  LOGD("MatchBle");

  nearby_BleFilterResult result;
  auto record = BleScanRecord::Parse(report.data, report.dataLength);
  // Log the service data for debug only.
  for (const auto &ble_service_data : record.service_data) {
    LOGD_SENSITIVE_INFO("Receive service data with uuid %" PRIX16,
                        ble_service_data.uuid);
    for (int i = 0; i < ble_service_data.length; i++) {
      LOGD_SENSITIVE_INFO("%" PRIx8, ble_service_data.data[i]);
    }
    LOGD_SENSITIVE_INFO("Service data end.");
  }
  for (int filter_index = 0; filter_index < ble_filters_.filter_count;
       filter_index++) {
    LOGD("MatchPresence advertisements.");
    // TODO(b/193756395): multiple matched results can share the same BLE
    // event. Optimize the memory usage by avoiding duplicated BLE events
    // across multiple results.
    result = nearby_BleFilterResult_init_zero;
    result.has_id = true;
    result.id = static_cast<uint32_t>(filter_index);
    result.has_tx_power = true;
    result.tx_power = static_cast<int32_t>(report.txPower);
    result.has_rssi = true;
    result.rssi = static_cast<int32_t>(report.rssi);
    result.has_bluetooth_address = true;
    // The buffer size has already been checked.
    static_assert(std::size(result.bluetooth_address) == CHRE_BLE_ADDRESS_LEN);
    memcpy(result.bluetooth_address, report.address, std::size(report.address));
    result.has_timestamp_ns = true;
    result.timestamp_ns =
        report.timestamp +
        static_cast<uint64_t>(chreGetEstimatedHostTimeOffset());
    if (MatchFastPair(ble_filters_.filter[filter_index], record, &result)) {
      LOGD("Add a matched Fast Pair filter result");
      fp_filter_results->push_back(result);
      return;
    }
#ifdef ENABLE_PRESENCE
    if (MatchPresenceV0(ble_filters_.filter[filter_index], record, &result) ||
        MatchPresenceV1(ble_filters_.filter[filter_index], record,
                        PresenceCryptoV1Impl(), PresenceCryptoIdentityV1Impl(),
                        &result)) {
      LOGD("Filter result TX power %" PRId32 ", RSSI %" PRId32, result.tx_power,
           result.rssi);

      LOGD("Add a matched Presence filter result");
      filter_results->push_back(result);
    }
#endif
  }
}

}  // namespace nearby
