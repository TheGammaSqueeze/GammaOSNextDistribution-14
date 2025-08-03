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

#include <bluetooth/log.h>

#include "ble_scanner.h"
#include "ble_scanner_hci_interface.h"
#include "internal_include/bt_target.h"
#include "internal_include/stack_config.h"
#include "stack/btm/btm_ble_int.h"

using namespace bluetooth;

std::mutex lock1;

namespace {

class BleScanningManagerImpl;

BleScanningManager* instance;
base::WeakPtr<BleScanningManagerImpl> instance_weakptr;

static void status_callback(uint8_t status) {
  log::verbose("Received status_cb with status:{}", status);
}

class BleScanningManagerImpl
    : public BleScanningManager,
      public BleScannerHciInterface::ScanEventObserver {
 public:
  BleScanningManagerImpl(BleScannerHciInterface* interface)
      : hci_interface(interface), weak_factory_(this) {}

  ~BleScanningManagerImpl() {}

  void PeriodicScanStart(uint8_t options, uint8_t set_id, uint8_t adv_addr_type,
                         const RawAddress& adv_addr, uint16_t skip_num,
                         uint16_t sync_timeout,
                         uint8_t sync_cte_type) override {
    GetHciInterface()->PeriodicScanStart(options, set_id, adv_addr_type,
                                         adv_addr, skip_num, sync_timeout,
                                         sync_cte_type);
  }

  void PeriodicScanCancelStart() override {
    GetHciInterface()->PeriodicScanCancelStart(base::Bind(&status_callback));
  }

  void PeriodicScanTerminate(uint16_t sync_handle) override {
    GetHciInterface()->PeriodicScanTerminate(sync_handle,
                                             base::Bind(&status_callback));
  }

  void PeriodicAdvSyncTransfer(
      const RawAddress& bd_addr, uint16_t service_data, uint16_t sync_handle,
      BleScannerHciInterface::handle_cb command_complete) override {
    GetHciInterface()->PeriodicAdvSyncTransfer(bd_addr, service_data,
                                               sync_handle, command_complete);
  }

  void PeriodicAdvSetInfoTransfer(const RawAddress& bd_addr,
                                  uint16_t service_data, uint8_t adv_handle,
                                  handle_cb command_complete) override {
    GetHciInterface()->PeriodicAdvSetInfoTransfer(bd_addr, service_data,
                                                  adv_handle, command_complete);
  }

  void SetPeriodicAdvSyncTransferParams(const RawAddress& bd_addr, uint8_t mode,
                                        uint16_t skip, uint16_t sync_timeout,
                                        uint8_t cte_type, bool set_defaults,
                                        status_cb command_complete) override {
    GetHciInterface()->SetPeriodicAdvSyncTransferParams(
        bd_addr, mode, skip, sync_timeout, cte_type, set_defaults,
        command_complete);
  }

  void OnPeriodicScanResult(uint16_t sync_handle, uint8_t tx_power, int8_t rssi,
                            uint8_t cte_type, uint8_t pkt_data_status,
                            uint8_t pkt_data_len,
                            const uint8_t* pkt_data) override {
    btm_ble_periodic_adv_report(sync_handle, tx_power, rssi, cte_type,
                                pkt_data_status, pkt_data_len, pkt_data);
  }

  void OnPeriodicScanEstablished(uint8_t status, uint16_t sync_handle,
                                 uint8_t set_id, uint8_t adv_addr_type,
                                 const RawAddress& adv_addr, uint8_t adv_phy,
                                 uint16_t adv_interval,
                                 uint8_t adv_clock_accuracy) override {
    btm_ble_periodic_adv_sync_established(status, sync_handle, set_id,
                                          adv_addr_type, adv_addr, adv_phy,
                                          adv_interval, adv_clock_accuracy);
  }

  void OnPeriodicScanLost(uint16_t sync_handle) override {
    btm_ble_periodic_adv_sync_lost(sync_handle);
  }

  base::WeakPtr<BleScanningManagerImpl> GetWeakPtr() {
    return weak_factory_.GetWeakPtr();
  }

 private:
  BleScannerHciInterface* GetHciInterface() { return hci_interface; }
  BleScannerHciInterface* hci_interface = nullptr;

  // Member variables should appear before the WeakPtrFactory, to ensure
  // that any WeakPtrs are invalidated before its members
  // variable's destructors are executed, rendering them invalid.
  base::WeakPtrFactory<BleScanningManagerImpl> weak_factory_;
};

}  // namespace

void BleScanningManager::Initialize(BleScannerHciInterface* interface) {
  instance = new BleScanningManagerImpl(interface);
  instance_weakptr = ((BleScanningManagerImpl*)instance)->GetWeakPtr();
}

bool BleScanningManager::IsInitialized() { return instance; }

base::WeakPtr<BleScanningManager> BleScanningManager::Get() {
  return instance_weakptr;
};

void BleScanningManager::CleanUp() {
  delete instance;
  instance = nullptr;
};

/**
 * This function initializes the scanning manager.
 **/
void btm_ble_scanner_init() {
  BleScannerHciInterface::Initialize();
  if (BleScannerHciInterface::Get()) {
    BleScanningManager::Initialize(BleScannerHciInterface::Get());
  } else {
    log::verbose("BleScannerHciInterface::Get() returns null");
  }
  if ((BleScannerHciInterface::Get()) && (BleScanningManager::Get())) {
    BleScannerHciInterface::Get()->SetScanEventObserver(
        (BleScanningManagerImpl*)BleScanningManager::Get().get());
  } else {
    log::verbose("BleScannerHciInterface or BleScanningManager is null");
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_scanner_cleanup
 *
 * Description      This function cleans up scanner control block.
 *
 * Parameters
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_scanner_cleanup(void) {
  std::lock_guard<std::mutex> lock(lock1);
  BleScanningManager::CleanUp();
  BleScannerHciInterface::CleanUp();
}
