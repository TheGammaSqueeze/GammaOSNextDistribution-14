/*
 * Copyright 2022 The Android Open Source Project
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

#include <memory>
#include <vector>

#include "common/callback.h"
#include "hci/address_with_type.h"
#include "hci/hci_packets.h"
#include "hci/uuid.h"

namespace bluetooth {
namespace hci {

using ScannerId = uint8_t;

class AdvertisingFilterOnFoundOnLostInfo {
 public:
  // For MSFT-based advertisement events, the monitor handle associates every event with the monitor
  // filter this event comes from.
  uint8_t monitor_handle;
  uint8_t scanner_id;
  uint8_t filter_index;
  uint8_t advertiser_state;
  AdvtInfoPresent advertiser_info_present;
  Address advertiser_address;
  uint8_t advertiser_address_type;
  uint8_t tx_power;
  int8_t rssi;
  uint16_t time_stamp;
  std::vector<uint8_t> adv_packet;
  std::vector<uint8_t> scan_response;
};

class ScanningCallback {
 public:
  enum ScanningStatus {
    SUCCESS,
    NO_RESOURCES = 0x80,
    INTERNAL_ERROR = 0x85,
    ILLEGAL_PARAMETER = 0x87,
  };

  virtual ~ScanningCallback() = default;
  virtual void OnScannerRegistered(
      const bluetooth::hci::Uuid app_uuid, ScannerId scanner_id, ScanningStatus status) = 0;
  virtual void OnSetScannerParameterComplete(ScannerId scanner_id, ScanningStatus status) = 0;
  virtual void OnScanResult(
      uint16_t event_type,
      uint8_t address_type,
      Address address,
      uint8_t primary_phy,
      uint8_t secondary_phy,
      uint8_t advertising_sid,
      int8_t tx_power,
      int8_t rssi,
      uint16_t periodic_advertising_interval,
      std::vector<uint8_t> advertising_data) = 0;
  virtual void OnTrackAdvFoundLost(AdvertisingFilterOnFoundOnLostInfo on_found_on_lost_info) = 0;
  virtual void OnBatchScanReports(
      int client_if, int status, int report_format, int num_records, std::vector<uint8_t> data) = 0;
  virtual void OnBatchScanThresholdCrossed(int client_if) = 0;
  virtual void OnTimeout() = 0;
  virtual void OnFilterEnable(Enable enable, uint8_t status) = 0;
  virtual void OnFilterParamSetup(uint8_t available_spaces, ApcfAction action, uint8_t status) = 0;
  virtual void OnFilterConfigCallback(
      ApcfFilterType filter_type, uint8_t available_spaces, ApcfAction action, uint8_t status) = 0;
  virtual void OnPeriodicSyncStarted(
      int request_id,
      uint8_t status,
      uint16_t sync_handle,
      uint8_t advertising_sid,
      AddressWithType address_with_type,
      uint8_t phy,
      uint16_t interval) = 0;
  virtual void OnPeriodicSyncReport(
      uint16_t sync_handle, int8_t tx_power, int8_t rssi, uint8_t status, std::vector<uint8_t> data) = 0;
  virtual void OnPeriodicSyncLost(uint16_t sync_handle) = 0;
  virtual void OnPeriodicSyncTransferred(int pa_source, uint8_t status, Address address) = 0;
  virtual void OnBigInfoReport(uint16_t sync_handle, bool encrypted) = 0;
};

class AdvertisingPacketContentFilterCommand {
 public:
  ApcfFilterType filter_type;
  Address address;
  ApcfApplicationAddressType application_address_type;
  Uuid uuid;
  Uuid uuid_mask;
  std::vector<uint8_t> name;
  uint16_t company;
  uint16_t company_mask;
  uint8_t org_id;
  uint8_t tds_flags;
  uint8_t tds_flags_mask;
  ApcfMetaDataType meta_data_type;
  std::vector<uint8_t> meta_data;
  uint8_t ad_type;
  std::vector<uint8_t> data;
  std::vector<uint8_t> data_mask;
  std::array<uint8_t, 16> irk;
};

class AdvertisingFilterParameter {
 public:
  uint16_t feature_selection;
  uint16_t list_logic_type;
  uint8_t filter_logic_type;
  uint8_t rssi_high_thresh;
  DeliveryMode delivery_mode;
  uint16_t onfound_timeout;
  uint8_t onfound_timeout_cnt;
  uint8_t rssi_low_thresh;
  uint16_t onlost_timeout;
  uint16_t num_of_tracking_entries;
};

}  // namespace hci
}  // namespace bluetooth
