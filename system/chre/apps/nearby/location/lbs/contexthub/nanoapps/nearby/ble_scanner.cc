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

#include "location/lbs/contexthub/nanoapps/nearby/ble_scanner.h"

#include <chre.h>

#include <utility>

#include "third_party/contexthub/chre/util/include/chre/util/macros.h"
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#ifdef MOCK_BLE
#include "chre/util/time.h"
#include "location/lbs/contexthub/nanoapps/nearby/mock_ble.h"

uint32_t mock_ble_timer_id = CHRE_TIMER_INVALID;
uint32_t mock_ble_flush_complete_timer_id = CHRE_TIMER_INVALID;
#endif

#define LOG_TAG "[NEARBY][BLE_SCANNER]"

namespace nearby {
#ifdef MOCK_BLE
BleScanner::BleScanner() {
  is_ble_scan_supported_ = true;
  is_batch_supported_ = nearby::MockBle::kBleBatchScanSupported;
  report_delay_ms_ = kBatchScanReportDelayLowPowerMilliSec;
}

void BleScanner::Start() {
  if (is_started_) {
    LOGD("Mock BLE scan already started.");
    return;
  }
  Restart();
}

void BleScanner::Restart() {
  LOGD("Start mock BLE events in scan mode %d.", scan_mode_);
  if (is_started_) {
    chreTimerCancel(mock_ble_timer_id);
  }
  mock_ble_timer_id =
      chreTimerSet(chre::Milliseconds(report_delay_ms_).toRawNanoseconds(),
                   &mock_ble_timer_id, false);
  is_started_ = true;
}

void BleScanner::Stop() {
  if (!is_started_) {
    LOGD("Mock BLE scan already stopped.");
    return;
  }
  LOGD("Stop mock BLE events.");
  chreTimerCancel(mock_ble_timer_id);
  if (mock_ble_flush_complete_timer_id != CHRE_TIMER_INVALID) {
    chreTimerCancel(mock_ble_flush_complete_timer_id);
    mock_ble_flush_complete_timer_id = CHRE_TIMER_INVALID;
  }
  is_started_ = false;
}

void BleScanner::UpdateBatchDelay(uint32_t delay_ms) {
  bool is_updated = false;
  if (!is_batch_supported_) {
    LOGD("Batch scan is not supported");
    return;
  }
  // avoids the report delay from being set too small for simulation
  if (delay_ms < nearby::MockBle::kBleReportDelayMinMs) {
    LOGE("Requested report delay is too small");
    return;
  }
  if (report_delay_ms_ != delay_ms) {
    report_delay_ms_ = delay_ms;
    is_updated = true;
  }
  // restart scan with new parameter if scan is already started
  if (is_updated && is_started_) {
    Restart();
  }
}

bool BleScanner::Flush() {
  if (!is_batch_supported_) {
    LOGD("Batch scan is not supported");
    return false;
  }
  if (!is_started_) {
    LOGD("Mock BLE scan was not started.");
    return false;
  }
  if (IsFlushing()) {
    LOGD("Flushing BLE scan is already in progress.");
    return true;
  }
  // stops normal BLE scan result timer internally
  chreTimerCancel(mock_ble_timer_id);
  // simulates the flushed scan results
  mock_ble_flush_complete_timer_id = chreTimerSet(
      chre::Milliseconds(nearby::MockBle::kBleFlushCompleteTimeoutMs)
          .toRawNanoseconds(),
      &mock_ble_flush_complete_timer_id, true);
  mock_ble_timer_id = chreTimerSet(
      chre::Milliseconds(nearby::MockBle::kBleFlushScanResultIntervalMs)
          .toRawNanoseconds(),
      &mock_ble_timer_id, false);
  is_batch_flushing_ = true;
  return true;
}

void BleScanner::HandleEvent(uint16_t event_type, const void *event_data) {
  const chreAsyncResult *async_result;
  switch (event_type) {
    case CHRE_EVENT_BLE_FLUSH_COMPLETE:
      async_result = static_cast<const chreAsyncResult *>(event_data);
      LOGD("Received mock flush complete event: return_code(%u) cookie(%p)",
           async_result->errorCode, async_result->cookie);
      // stops the flushed scan results timer internally
      chreTimerCancel(mock_ble_timer_id);
      mock_ble_flush_complete_timer_id = CHRE_TIMER_INVALID;
      is_batch_flushing_ = false;
      if (is_started_) {
        Restart();
      }
      break;
    default:
      LOGD("Unknown mock scan control event_type: %d", event_type);
  }
}
#else
constexpr chreBleGenericFilter kDefaultGenericFilters[] = {
    {
        .type = CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE,
        .len = 2,
        // Fast Pair Service UUID in OTA format.
        .data = {0x2c, 0xfe},
        .dataMask = {0xff, 0xff},
    },
    {
        .type = CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE,
        .len = 2,
        // Presence Service UUID in OTA format.
        .data = {0xf1, 0xfc},
        .dataMask = {0xff, 0xff},
    }};

BleScanner::BleScanner() {
  if (!(chreBleGetCapabilities() & CHRE_BLE_CAPABILITIES_SCAN)) {
    LOGE("BLE scan not supported.");
    is_ble_scan_supported_ = false;
  }
  if (!(chreBleGetFilterCapabilities() &
        CHRE_BLE_FILTER_CAPABILITIES_SERVICE_DATA)) {
    LOGI("BLE filter by service UUID not supported.");
  }
  if (chreBleGetCapabilities() & CHRE_BLE_CAPABILITIES_SCAN_RESULT_BATCHING) {
    is_batch_supported_ = true;
    report_delay_ms_ = kBatchScanReportDelayLowPowerMilliSec;
  }
}

void BleScanner::Start() {
  if (is_started_) {
    LOGD("BLE scan already started.");
    return;
  }
  Restart();
}

static bool ContainsFilter(
    const chre::DynamicVector<chreBleGenericFilter> &filters,
    const chreBleGenericFilter &src) {
  bool contained = false;
  for (const auto &dst : filters) {
    if (src.type == dst.type && src.len == dst.len) {
      for (int i = 0; i < src.len; i++) {
        if (src.data[i] != dst.data[i]) {
          continue;
        }
        if (src.dataMask[i] != dst.dataMask[i]) {
          continue;
        }
      }
      contained = true;
      break;
    }
  }
  return contained;
}

void BleScanner::Restart() {
  if (!is_ble_scan_supported_) {
    LOGE("Failed to start BLE scan on an unsupported device");
    return;
  }
  chre::DynamicVector<chreBleGenericFilter> generic_filters;
  if (is_default_generic_filter_enabled_) {
    for (size_t i = 0; i < ARRAY_SIZE(kDefaultGenericFilters); i++) {
      generic_filters.push_back(kDefaultGenericFilters[i]);
    }
  }
  for (auto &oem_generic_filters : generic_filters_list_) {
    for (auto &generic_filter : oem_generic_filters.filters) {
      if (!ContainsFilter(generic_filters, generic_filter)) {
        generic_filters.push_back(generic_filter);
      }
    }
  }
  chreBleScanFilter scan_filter;
  memset(&scan_filter, 0, sizeof(scan_filter));
  scan_filter.rssiThreshold = CHRE_BLE_RSSI_THRESHOLD_NONE;
  scan_filter.scanFilters = generic_filters.data();
  scan_filter.scanFilterCount = static_cast<uint8_t>(generic_filters.size());
  if (chreBleStartScanAsync(scan_mode_, report_delay_ms_, &scan_filter)) {
    LOGD("Succeeded to start BLE scan");
    // is_started_ is set to true here, but it can be set back to false
    // if CHRE_BLE_REQUEST_TYPE_START_SCAN request is failed in
    // CHRE_EVENT_BLE_ASYNC_RESULT event.
    is_started_ = true;
  } else {
    LOGE("Failed to start BLE scan");
  }
}

void BleScanner::Stop() {
  if (!is_started_) {
    LOGD("BLE scan already stopped.");
    return;
  }
  if (chreBleStopScanAsync()) {
    LOGD("Succeeded Stop BLE scan.");
    is_started_ = false;
  } else {
    LOGE("Failed to stop BLE scan");
  }
}

bool BleScanner::UpdateFilters(
    uint16_t host_end_point,
    chre::DynamicVector<chreBleGenericFilter> *generic_filters) {
  size_t index = 0;
  while (index < generic_filters_list_.size()) {
    if (generic_filters_list_[index].end_point == host_end_point) {
      if (generic_filters->empty()) {
        generic_filters_list_.erase(index);
      } else {
        generic_filters_list_[index].filters = std::move(*generic_filters);
      }
      return true;
    }
    ++index;
  }
  if (generic_filters_list_.push_back(GenericFilters(host_end_point))) {
    generic_filters_list_.back().filters = std::move(*generic_filters);
  } else {
    LOGE("Failed to add new hardware filter.");
    return false;
  }
  return true;
}

void BleScanner::UpdateBatchDelay(uint32_t delay_ms) {
  bool is_updated = false;
  if (!is_batch_supported_) {
    LOGD("Batch scan is not supported");
    return;
  }
  if (report_delay_ms_ != delay_ms) {
    report_delay_ms_ = delay_ms;
    is_updated = true;
  }
  // restart scan with new parameter if scan is already started
  if (is_updated && is_started_) {
    Restart();
  }
}

bool BleScanner::Flush() {
  if (!is_batch_supported_) {
    LOGD("Batch scan is not supported");
    return false;
  }
  if (!is_started_) {
    LOGE("BLE scan was not started.");
    return false;
  }
  if (IsFlushing()) {
    LOGD("Flushing BLE scan is already in progress.");
    return true;
  }
  LOGD("Flush batch scan results");
  if (!chreBleFlushAsync(nullptr)) {
    LOGE("Failed to call chreBleFlushAsync()");
    return false;
  }
  is_batch_flushing_ = true;
  return true;
}

void BleScanner::HandleEvent(uint16_t event_type, const void *event_data) {
  const chreAsyncResult *async_result =
      static_cast<const chreAsyncResult *>(event_data);
  switch (event_type) {
    case CHRE_EVENT_BLE_FLUSH_COMPLETE:
      LOGD("Received flush complete event: return_code(%u) cookie(%p)",
           async_result->errorCode, async_result->cookie);
      if (async_result->errorCode != CHRE_ERROR_NONE) {
        LOGE("Flush failed: %u", async_result->errorCode);
      }
      is_batch_flushing_ = false;
      break;
    case CHRE_EVENT_BLE_ASYNC_RESULT:
      if (async_result->errorCode != CHRE_ERROR_NONE) {
        LOGE(
            "Failed to complete the async request: "
            "request type (%u) error code(%u)",
            async_result->requestType, async_result->errorCode);
        if (async_result->requestType == CHRE_BLE_REQUEST_TYPE_START_SCAN) {
          LOGD("Failed in CHRE_BLE_REQUEST_TYPE_START_SCAN");
          if (is_started_) {
            is_started_ = false;
          }
        } else if (async_result->requestType ==
                   CHRE_BLE_REQUEST_TYPE_STOP_SCAN) {
          LOGD("Failed in CHRE_BLE_REQUEST_TYPE_STOP_SCAN");
        }
      }
      break;
    default:
      LOGD("Unknown scan control event_type: %d", event_type);
  }
}
#endif /* end ifdef MOCK_BLE */

}  // namespace nearby
