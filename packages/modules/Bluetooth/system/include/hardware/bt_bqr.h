/*
 * Copyright 2023 The Android Open Source Project
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

#pragma once

#include <stdint.h>

#include <vector>

#include "types/raw_address.h"

namespace bluetooth {
namespace bqr {

class BluetoothQualityReportCallbacks {
 public:
  virtual ~BluetoothQualityReportCallbacks() = default;

  /** Callback for BQR delivery to app level. */
  virtual void bqr_delivery_callback(const RawAddress remote_bd_addr,
                                     uint8_t lmp_ver, uint16_t lmp_subver,
                                     uint16_t manufacturer_id,
                                     std::vector<uint8_t> bqr_raw_data) = 0;
};

class BluetoothQualityReportInterface {
 public:
  virtual ~BluetoothQualityReportInterface() = default;

  /** Register the bluetooth keystore callbacks */
  virtual void init(BluetoothQualityReportCallbacks* callbacks) = 0;

  /** Event for BQR delivery to app level. */
  virtual void bqr_delivery_event(const RawAddress& bd_addr,
                                  const uint8_t* bqr_raw_data,
                                  uint32_t bqr_raw_data_len) = 0;
};

}  // namespace bqr
}  // namespace bluetooth
