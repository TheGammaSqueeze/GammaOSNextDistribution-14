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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BLE_SCANNER_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BLE_SCANNER_H_

#include <chre.h>

#include "third_party/contexthub/chre/util/include/chre/util/dynamic_vector.h"

namespace nearby {

struct GenericFilters {
  uint16_t end_point;
  chre::DynamicVector<chreBleGenericFilter> filters;

  explicit GenericFilters(uint16_t end_point) : end_point(end_point) {}

  friend bool operator==(const GenericFilters &c1, const GenericFilters &c2) {
    return c1.end_point == c2.end_point;
  }

  friend bool operator!=(const GenericFilters &c1, const GenericFilters &c2) {
    return c1.end_point != c2.end_point;
  }
};

class BleScanner {
 public:
  // Default value for report delay of batch scan results in low latency mode.
  static constexpr uint32_t kBatchScanReportDelayLowLatencyMilliSec = 0;

  // Default value for report delay of batch scan results in low power mode.
  static constexpr uint32_t kBatchScanReportDelayLowPowerMilliSec = 3000;

  // Constructs BLE Scanner and checks whether BLE batch scan is supported.
  BleScanner();

  // Starts BLE scan. If scan already started, nothing happens.
  void Start();

  // Stops BLE scan.
  void Stop();

  // Flushes the batched scan results.
  // Returns whether flush operation proceeds.
  bool Flush();

  // Returns whether BLE batch scan is flushing.
  bool IsFlushing() {
    return is_batch_flushing_;
  }

  // Returns whether BLE scan is running.
  bool isScanning() {
    return is_started_;
  }

  // Returns true if BLE scan is available in the device.
  bool isAvailable() {
    return is_ble_scan_supported_;
  }

  // Returns whether BLE batch scan is supported.
  bool IsBatchSupported() {
    return is_batch_supported_;
  }

  // Updates extended generic filters. Caller needs to call Restart() for the
  // updated filters to be effective. Returns true for successful update.
  bool UpdateFilters(
      uint16_t host_end_point,
      chre::DynamicVector<chreBleGenericFilter> *generic_filters);

  // Updates the report delay of batch scan
  void UpdateBatchDelay(uint32_t delay_ms);

  // Handles an event from CHRE.
  void HandleEvent(uint16_t event_type, const void *event_data);

  // Starts BLE scan. If scan already started, replacing the previous scan.
  void Restart();

  // Sets default generic filters.
  void SetDefaultFilters() {
    is_default_generic_filter_enabled_ = true;
  }

  // Clears default generic filters.
  void ClearDefaultFilters() {
    is_default_generic_filter_enabled_ = false;
  }

 private:
  // Whether BLE scan is started.
  bool is_started_ = false;

  // Whether BLE scan is supported.
  bool is_ble_scan_supported_ = true;

  // Whether BLE batch scan is supported.
  bool is_batch_supported_ = false;

  // Whether BLE batch scan is flushing.
  bool is_batch_flushing_ = false;

  // Whether default generic filter is enabled.
  bool is_default_generic_filter_enabled_ = false;

  // Current report delay for BLE batch scan
  uint32_t report_delay_ms_ = 0;

  // Current BLE scan mode
  chreBleScanMode scan_mode_ = CHRE_BLE_SCAN_MODE_BACKGROUND;

  chre::DynamicVector<GenericFilters> generic_filters_list_;
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BLE_SCANNER_H_
