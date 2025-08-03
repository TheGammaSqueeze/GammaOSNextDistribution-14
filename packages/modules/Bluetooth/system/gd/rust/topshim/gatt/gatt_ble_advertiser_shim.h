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
#ifndef GD_RUST_TOPSHIM_GATT_GATT_BLE_ADVERTISER_SHIM_H
#define GD_RUST_TOPSHIM_GATT_GATT_BLE_ADVERTISER_SHIM_H

#include <memory>

#include "include/hardware/ble_advertiser.h"
#include "include/hardware/bt_gatt.h"
#include "rust/cxx.h"

namespace bluetooth {
namespace topshim {
namespace rust {

struct RustAdvertiseParameters;
struct RustPeriodicAdvertisingParameters;
struct RustUuid;

// See include/hardware/ble_advertiser.h for more documentation.
//
// This shim implementation just calls the underlying interface and binds the
// local callbacks in order to dispatch the Rust callbacks.
class BleAdvertiserIntf : public AdvertisingCallbacks {
 public:
  BleAdvertiserIntf(BleAdvertiserInterface* adv_intf) : adv_intf_(adv_intf){};
  ~BleAdvertiserIntf() = default;

  // AdvertisingCallbacks overrides
  void OnAdvertisingSetStarted(int reg_id, uint8_t advertiser_id, int8_t tx_power, uint8_t status) override;
  void OnAdvertisingEnabled(uint8_t advertiser_id, bool enable, uint8_t status) override;
  void OnAdvertisingDataSet(uint8_t advertiser_id, uint8_t status) override;
  void OnScanResponseDataSet(uint8_t advertiser_id, uint8_t status) override;
  void OnAdvertisingParametersUpdated(uint8_t advertiser_id, int8_t tx_power, uint8_t status) override;
  void OnPeriodicAdvertisingParametersUpdated(uint8_t advertiser_id, uint8_t status) override;
  void OnPeriodicAdvertisingDataSet(uint8_t advertiser_id, uint8_t status) override;
  void OnPeriodicAdvertisingEnabled(uint8_t advertiser_id, bool enable, uint8_t status) override;
  void OnOwnAddressRead(uint8_t advertiser_id, uint8_t address_type, RawAddress address) override;

  // BleAdvertiserInterface implementations

  void RegisterAdvertiser();
  void Unregister(uint8_t adv_id);

  void GetOwnAddress(uint8_t adv_id);
  void SetParameters(uint8_t adv_id, RustAdvertiseParameters params);
  void SetData(uint8_t adv_id, bool set_scan_rsp, ::rust::Vec<uint8_t> data);
  void Enable(uint8_t adv_id, bool enable, uint16_t duration, uint8_t max_ext_adv_events);
  void StartAdvertising(
      uint8_t adv_id,
      RustAdvertiseParameters params,
      ::rust::Vec<uint8_t> advertise_data,
      ::rust::Vec<uint8_t> scan_response_data,
      int32_t timeout_in_sec);
  void StartAdvertisingSet(
      int32_t reg_id,
      RustAdvertiseParameters params,
      ::rust::Vec<uint8_t> advertise_data,
      ::rust::Vec<uint8_t> scan_response_data,
      RustPeriodicAdvertisingParameters periodic_params,
      ::rust::Vec<uint8_t> periodic_data,
      uint16_t duration,
      uint8_t max_ext_adv_events);
  void SetPeriodicAdvertisingParameters(uint8_t adv_id, RustPeriodicAdvertisingParameters params);
  void SetPeriodicAdvertisingData(uint8_t adv_id, ::rust::Vec<uint8_t> data);
  void SetPeriodicAdvertisingEnable(uint8_t adv_id, bool enable, bool include_adi);

  void RegisterCallbacks();

 private:
  // In-band callbacks will get binded to these and sent to Rust via static
  // callbacks.
  void OnIdStatusCallback(uint8_t adv_id, uint8_t status);
  void OnIdTxPowerStatusCallback(uint8_t adv_id, int8_t tx_power, uint8_t status);
  void OnParametersCallback(uint8_t adv_id, uint8_t status, int8_t tx_power);
  void OnGetAddressCallback(uint8_t adv_id, uint8_t addr_type, RawAddress address);

  BleAdvertiserInterface* adv_intf_;
};

std::unique_ptr<BleAdvertiserIntf> GetBleAdvertiserIntf(const unsigned char* gatt_intf);

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth

#endif  // GD_RUST_TOPSHIM_GATT_GATT_BLE_ADVERTISER_SHIM_H
