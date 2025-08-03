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

#include "rust/topshim/gatt/gatt_ble_advertiser_shim.h"

#include <base/functional/bind.h>
#include <base/functional/callback.h>

#include <algorithm>
#include <iterator>
#include <memory>
#include <vector>

#include "bind_helpers.h"
#include "include/hardware/bt_common_types.h"
#include "rust/cxx.h"
#include "src/profiles/gatt.rs.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace topshim {
namespace rust {

namespace rusty = ::bluetooth::topshim::rust;

namespace internal {
AdvertiseParameters ConvertRustAdvParams(const RustAdvertiseParameters& params) {
  AdvertiseParameters converted = {
      .advertising_event_properties = params.advertising_event_properties,
      .min_interval = params.min_interval,
      .max_interval = params.max_interval,
      .channel_map = params.channel_map,
      .tx_power = params.tx_power,
      .primary_advertising_phy = params.primary_advertising_phy,
      .secondary_advertising_phy = params.secondary_advertising_phy,
      .scan_request_notification_enable = params.scan_request_notification_enable,
      .own_address_type = params.own_address_type,
  };

  return converted;
}
PeriodicAdvertisingParameters ConvertRustPeriodicAdvParams(const RustPeriodicAdvertisingParameters& params) {
  PeriodicAdvertisingParameters converted = {
      .enable = params.enable,
      .include_adi = params.include_adi,
      .min_interval = params.min_interval,
      .max_interval = params.max_interval,
      .periodic_advertising_properties = params.periodic_advertising_properties,
  };

  return converted;
}
}  // namespace internal

// AdvertisingCallbacks implementations

void BleAdvertiserIntf::OnAdvertisingSetStarted(int reg_id, uint8_t advertiser_id, int8_t tx_power, uint8_t status) {
  rusty::gdadv_on_advertising_set_started(reg_id, advertiser_id, tx_power, status);
}
void BleAdvertiserIntf::OnAdvertisingEnabled(uint8_t advertiser_id, bool enable, uint8_t status) {
  rusty::gdadv_on_advertising_enabled(advertiser_id, enable, status);
}
void BleAdvertiserIntf::OnAdvertisingDataSet(uint8_t advertiser_id, uint8_t status) {
  rusty::gdadv_on_advertising_data_set(advertiser_id, status);
}
void BleAdvertiserIntf::OnScanResponseDataSet(uint8_t advertiser_id, uint8_t status) {
  rusty::gdadv_on_scan_response_data_set(advertiser_id, status);
}
void BleAdvertiserIntf::OnAdvertisingParametersUpdated(uint8_t advertiser_id, int8_t tx_power, uint8_t status) {
  rusty::gdadv_on_advertising_parameters_updated(advertiser_id, tx_power, status);
}
void BleAdvertiserIntf::OnPeriodicAdvertisingParametersUpdated(uint8_t advertiser_id, uint8_t status) {
  rusty::gdadv_on_periodic_advertising_parameters_updated(advertiser_id, status);
}
void BleAdvertiserIntf::OnPeriodicAdvertisingDataSet(uint8_t advertiser_id, uint8_t status) {
  rusty::gdadv_on_periodic_advertising_data_set(advertiser_id, status);
}
void BleAdvertiserIntf::OnPeriodicAdvertisingEnabled(uint8_t advertiser_id, bool enable, uint8_t status) {
  rusty::gdadv_on_periodic_advertising_enabled(advertiser_id, enable, status);
}
void BleAdvertiserIntf::OnOwnAddressRead(uint8_t advertiser_id, uint8_t address_type, RawAddress addr) {
  rusty::gdadv_on_own_address_read(advertiser_id, address_type, &addr);
}

// BleAdvertiserInterface implementations

void BleAdvertiserIntf::RegisterAdvertiser() {
  adv_intf_->RegisterAdvertiser(base::Bind(&BleAdvertiserIntf::OnIdStatusCallback, base::Unretained(this)));
}

void BleAdvertiserIntf::Unregister(uint8_t adv_id) {
  adv_intf_->Unregister(adv_id);
}

void BleAdvertiserIntf::GetOwnAddress(uint8_t adv_id) {
  adv_intf_->GetOwnAddress(
      adv_id, base::Bind(&BleAdvertiserIntf::OnGetAddressCallback, base::Unretained(this), adv_id));
}

void BleAdvertiserIntf::SetParameters(uint8_t adv_id, RustAdvertiseParameters params) {
  AdvertiseParameters converted = internal::ConvertRustAdvParams(params);
  adv_intf_->SetParameters(
      adv_id, converted, base::Bind(&BleAdvertiserIntf::OnParametersCallback, base::Unretained(this), adv_id));
}

void BleAdvertiserIntf::SetData(uint8_t adv_id, bool set_scan_rsp, ::rust::Vec<uint8_t> data) {
  std::vector<uint8_t> converted;
  std::copy(data.begin(), data.end(), std::back_inserter(converted));

  adv_intf_->SetData(
      adv_id,
      set_scan_rsp,
      converted,
      base::Bind(&BleAdvertiserIntf::OnIdStatusCallback, base::Unretained(this), adv_id));
}

void BleAdvertiserIntf::Enable(uint8_t adv_id, bool enable, uint16_t duration, uint8_t max_ext_adv_events) {
  adv_intf_->Enable(
      adv_id,
      enable,
      base::Bind(&BleAdvertiserIntf::OnIdStatusCallback, base::Unretained(this), adv_id),
      duration,
      max_ext_adv_events,
      base::Bind(&BleAdvertiserIntf::OnIdStatusCallback, base::Unretained(this), adv_id));
}

void BleAdvertiserIntf::StartAdvertising(
    uint8_t adv_id,
    RustAdvertiseParameters params,
    ::rust::Vec<uint8_t> advertise_data,
    ::rust::Vec<uint8_t> scan_response_data,
    int32_t timeout_in_sec) {
  AdvertiseParameters converted_params = internal::ConvertRustAdvParams(params);
  std::vector<uint8_t> converted_adv_data, converted_scan_rsp_data;
  std::copy(advertise_data.begin(), advertise_data.end(), std::back_inserter(converted_adv_data));
  std::copy(scan_response_data.begin(), scan_response_data.end(), std::back_inserter(converted_scan_rsp_data));

  adv_intf_->StartAdvertising(
      adv_id,
      base::Bind(&BleAdvertiserIntf::OnIdStatusCallback, base::Unretained(this), adv_id),
      converted_params,
      converted_adv_data,
      converted_scan_rsp_data,
      timeout_in_sec,
      base::Bind(&BleAdvertiserIntf::OnIdStatusCallback, base::Unretained(this), adv_id));
}

void BleAdvertiserIntf::StartAdvertisingSet(
    int32_t reg_id,
    RustAdvertiseParameters params,
    ::rust::Vec<uint8_t> advertise_data,
    ::rust::Vec<uint8_t> scan_response_data,
    RustPeriodicAdvertisingParameters periodic_params,
    ::rust::Vec<uint8_t> periodic_data,
    uint16_t duration,
    uint8_t max_ext_adv_events) {
  AdvertiseParameters converted_params = internal::ConvertRustAdvParams(params);
  PeriodicAdvertisingParameters converted_periodic_params = internal::ConvertRustPeriodicAdvParams(periodic_params);
  std::vector<uint8_t> converted_adv_data, converted_scan_rsp_data, converted_periodic_data;
  std::copy(advertise_data.begin(), advertise_data.end(), std::back_inserter(converted_adv_data));
  std::copy(scan_response_data.begin(), scan_response_data.end(), std::back_inserter(converted_scan_rsp_data));
  std::copy(periodic_data.begin(), periodic_data.end(), std::back_inserter(converted_periodic_data));

  adv_intf_->StartAdvertisingSet(
      kAdvertiserClientIdJni,
      reg_id,
      base::Bind(&BleAdvertiserIntf::OnIdTxPowerStatusCallback, base::Unretained(this)),
      converted_params,
      converted_adv_data,
      converted_scan_rsp_data,
      converted_periodic_params,
      converted_periodic_data,
      duration,
      max_ext_adv_events,
      base::Bind(&BleAdvertiserIntf::OnIdStatusCallback, base::Unretained(this)));
}

void BleAdvertiserIntf::SetPeriodicAdvertisingParameters(uint8_t adv_id, RustPeriodicAdvertisingParameters params) {
  PeriodicAdvertisingParameters converted = internal::ConvertRustPeriodicAdvParams(params);

  adv_intf_->SetPeriodicAdvertisingParameters(
      adv_id, converted, base::Bind(&BleAdvertiserIntf::OnIdStatusCallback, base::Unretained(this), adv_id));
}

void BleAdvertiserIntf::SetPeriodicAdvertisingData(uint8_t adv_id, ::rust::Vec<uint8_t> data) {
  std::vector<uint8_t> converted;
  std::copy(data.begin(), data.end(), std::back_inserter(converted));

  adv_intf_->SetPeriodicAdvertisingData(
      adv_id, converted, base::Bind(&BleAdvertiserIntf::OnIdStatusCallback, base::Unretained(this), adv_id));
}

void BleAdvertiserIntf::SetPeriodicAdvertisingEnable(uint8_t adv_id, bool enable, bool include_adi) {
  adv_intf_->SetPeriodicAdvertisingEnable(
      adv_id, enable, include_adi, base::Bind(&BleAdvertiserIntf::OnIdStatusCallback, base::Unretained(this), adv_id));
}

void BleAdvertiserIntf::RegisterCallbacks() {
  adv_intf_->RegisterCallbacks(this);
}

// Inband callbacks

void BleAdvertiserIntf::OnIdStatusCallback(uint8_t adv_id, uint8_t status) {
  gdadv_idstatus_callback(adv_id, status);
}
void BleAdvertiserIntf::OnIdTxPowerStatusCallback(uint8_t adv_id, int8_t tx_power, uint8_t status) {
  gdadv_idtxpowerstatus_callback(adv_id, tx_power, status);
}
void BleAdvertiserIntf::OnParametersCallback(uint8_t adv_id, uint8_t status, int8_t tx_power) {
  gdadv_parameters_callback(adv_id, status, tx_power);
}
void BleAdvertiserIntf::OnGetAddressCallback(uint8_t adv_id, uint8_t addr_type, RawAddress addr) {
  gdadv_getaddress_callback(adv_id, addr_type, &addr);
}

std::unique_ptr<BleAdvertiserIntf> GetBleAdvertiserIntf(const unsigned char* gatt_intf) {
  return std::make_unique<BleAdvertiserIntf>(reinterpret_cast<const btgatt_interface_t*>(gatt_intf)->advertiser);
}

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
