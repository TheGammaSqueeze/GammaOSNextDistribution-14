/*
 * Copyright 2021 The Android Open Source Project
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

#ifndef BLE_SCANNER_H
#define BLE_SCANNER_H

#include <base/functional/bind.h>
#include <base/memory/weak_ptr.h>

#include <vector>

#include "btm_ble_api.h"

using status_cb = base::Callback<void(uint8_t /* status */)>;
using handle_cb =
    base::Callback<void(uint8_t /* status */, uint16_t /* adv_handle */)>;

// methods we expose to c code:
void btm_ble_scanner_cleanup(void);
void btm_ble_scanner_init();

class BleScannerHciInterface;

class BleScanningManager {
 public:
  virtual ~BleScanningManager() = default;

  static void Initialize(BleScannerHciInterface* interface);
  static void CleanUp();
  static bool IsInitialized();
  static base::WeakPtr<BleScanningManager> Get();

  virtual void PeriodicScanStart(uint8_t options, uint8_t set_id,
                                 uint8_t adv_addr_type,
                                 const RawAddress& adv_addr, uint16_t skip_num,
                                 uint16_t sync_timeout,
                                 uint8_t sync_cte_type) = 0;
  virtual void PeriodicScanCancelStart(/*status_cb command_complete*/) = 0;
  virtual void PeriodicScanTerminate(uint16_t sync_handle/*,
                                     status_cb command_complete*/) = 0;
  virtual void PeriodicAdvSyncTransfer(const RawAddress& bd_addr,
                                       uint16_t service_data,
                                       uint16_t sync_handle,
                                       handle_cb command_complete) = 0;
  virtual void PeriodicAdvSetInfoTransfer(const RawAddress& bd_addr,
                                          uint16_t service_data,
                                          uint8_t adv_handle,
                                          handle_cb command_complete) = 0;
  virtual void SetPeriodicAdvSyncTransferParams(const RawAddress& bd_addr,
                                                uint8_t mode, uint16_t skip,
                                                uint16_t sync_timeout,
                                                uint8_t cte_type,
                                                bool set_defaults,
                                                status_cb command_complete) = 0;

  virtual void OnPeriodicScanResult(uint16_t sync_handle, uint8_t tx_power,
                                    int8_t rssi, uint8_t cte_type,
                                    uint8_t pkt_data_status,
                                    uint8_t pkt_data_len,
                                    const uint8_t* pkt_data) = 0;
  virtual void OnPeriodicScanEstablished(uint8_t status, uint16_t sync_handle,
                                         uint8_t set_id, uint8_t adv_addr_type,
                                         const RawAddress& adv_addr,
                                         uint8_t adv_phy, uint16_t adv_interval,
                                         uint8_t adv_clock_accuracy) = 0;
  virtual void OnPeriodicScanLost(uint16_t sync_handle) = 0;
};

#endif  // BLE_SCANNER_H
