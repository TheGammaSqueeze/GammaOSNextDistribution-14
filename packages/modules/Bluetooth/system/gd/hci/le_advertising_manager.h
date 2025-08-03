/*
 * Copyright 2019 The Android Open Source Project
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
#include "module.h"

namespace bluetooth {
namespace hci {

class PeriodicAdvertisingParameters {
 public:
  bool enable;
  bool include_adi;
  uint16_t min_interval;
  uint16_t max_interval;
  uint16_t properties;
  enum AdvertisingProperty { INCLUDE_TX_POWER = 0x06 };
};

enum class AdvertiserAddressType {
  PUBLIC,
  RESOLVABLE_RANDOM,
  NONRESOLVABLE_RANDOM,
};

class AdvertisingConfig {
 public:
  std::vector<GapData> advertisement;
  std::vector<GapData> scan_response;
  uint16_t interval_min;
  uint16_t interval_max;
  AdvertisingType advertising_type;
  AdvertiserAddressType requested_advertiser_address_type;
  PeerAddressType peer_address_type;
  Address peer_address;
  uint8_t channel_map;
  AdvertisingFilterPolicy filter_policy;
  uint8_t tx_power;  // -127 to +20 (0x7f is no preference)
  bool connectable = false;
  bool discoverable = false;
  bool scannable = false;
  bool directed = false;
  bool high_duty_directed_connectable = false;
  bool legacy_pdus = false;
  bool anonymous = false;
  bool include_tx_power = false;
  bool use_le_coded_phy;       // Primary advertisement PHY is LE Coded
  uint8_t secondary_max_skip;  // maximum advertising events to be skipped, 0x0 send AUX_ADV_IND prior ot the next event
  SecondaryPhyType secondary_advertising_phy;
  uint8_t sid = 0x00;
  Enable enable_scan_request_notifications = Enable::DISABLED;
  std::vector<GapData> periodic_data;
  PeriodicAdvertisingParameters periodic_advertising_parameters;
  AdvertisingConfig() = default;
};

using AdvertiserId = uint8_t;

class AdvertisingCallback {
 public:
  enum AdvertisingStatus {
    SUCCESS,
    DATA_TOO_LARGE,
    TOO_MANY_ADVERTISERS,
    ALREADY_STARTED,
    INTERNAL_ERROR,
    FEATURE_UNSUPPORTED
  };

  virtual ~AdvertisingCallback() = default;
  virtual void OnAdvertisingSetStarted(
      int reg_id, uint8_t advertiser_id, int8_t tx_power, AdvertisingStatus status) = 0;
  virtual void OnAdvertisingEnabled(uint8_t advertiser_id, bool enable, uint8_t status) = 0;
  virtual void OnAdvertisingDataSet(uint8_t advertiser_id, uint8_t status) = 0;
  virtual void OnScanResponseDataSet(uint8_t advertiser_id, uint8_t status) = 0;
  virtual void OnAdvertisingParametersUpdated(uint8_t advertiser_id, int8_t tx_power, uint8_t status) = 0;
  virtual void OnPeriodicAdvertisingParametersUpdated(uint8_t advertiser_id, uint8_t status) = 0;
  virtual void OnPeriodicAdvertisingDataSet(uint8_t advertiser_id, uint8_t status) = 0;
  virtual void OnPeriodicAdvertisingEnabled(uint8_t advertiser_id, bool enable, uint8_t status) = 0;
  virtual void OnOwnAddressRead(uint8_t advertiser_id, uint8_t address_type, Address address) = 0;
};

class LeAdvertisingManager : public bluetooth::Module {
 public:
  static constexpr AdvertiserId kInvalidId = 0xFF;
  static constexpr uint8_t kInvalidHandle = 0xFF;
  static constexpr uint8_t kAdvertisingSetIdMask = 0x0F;
  static constexpr uint16_t kLeMaximumFragmentLength = 251;
  static constexpr uint16_t kLeMaximumPeriodicDataFragmentLength = 252;
  static constexpr uint16_t kLeMaximumGapDataLength = 255;
  static constexpr FragmentPreference kFragment_preference = FragmentPreference::CONTROLLER_SHOULD_NOT;
  LeAdvertisingManager();
  LeAdvertisingManager(const LeAdvertisingManager&) = delete;
  LeAdvertisingManager& operator=(const LeAdvertisingManager&) = delete;

  size_t GetNumberOfAdvertisingInstances() const;

  size_t GetNumberOfAdvertisingInstancesInUse() const;

  int GetAdvertiserRegId(AdvertiserId advertiser_id);

  void ExtendedCreateAdvertiser(
      uint8_t client_id,
      int reg_id,
      const AdvertisingConfig config,
      common::Callback<void(Address, AddressType)> scan_callback,
      common::Callback<void(ErrorCode, uint8_t, uint8_t)> set_terminated_callback,
      uint16_t duration,
      uint8_t max_extended_advertising_events,
      os::Handler* handler);

  void StartAdvertising(
      AdvertiserId advertiser_id,
      const AdvertisingConfig config,
      uint16_t duration,
      base::OnceCallback<void(uint8_t /* status */)> status_callback,
      base::OnceCallback<void(uint8_t /* status */)> timeout_callback,
      common::Callback<void(Address, AddressType)> scan_callback,
      common::Callback<void(ErrorCode, uint8_t, uint8_t)> set_terminated_callback,
      os::Handler* handler);

  void GetOwnAddress(uint8_t advertiser_id);

  void RegisterAdvertiser(
      common::ContextualOnceCallback<void(uint8_t /* inst_id */, uint8_t /* status */)> callback);

  void SetParameters(AdvertiserId advertiser_id, AdvertisingConfig config);

  void SetData(AdvertiserId advertiser_id, bool set_scan_rsp, std::vector<GapData> data);

  void EnableAdvertiser(
      AdvertiserId advertiser_id, bool enable, uint16_t duration, uint8_t max_extended_advertising_events);

  void SetPeriodicParameters(AdvertiserId advertiser_id, PeriodicAdvertisingParameters periodic_advertising_parameters);

  void SetPeriodicData(AdvertiserId advertiser_id, std::vector<GapData> data);

  void EnablePeriodicAdvertising(AdvertiserId advertiser_id, bool enable, bool include_adi);

  void RemoveAdvertiser(AdvertiserId advertiser_id);

  void RegisterAdvertisingCallback(AdvertisingCallback* advertising_callback);

  static const ModuleFactory Factory;

 protected:
  void ListDependencies(ModuleList* list) const override;

  void Start() override;

  void Stop() override;

  std::string ToString() const override;

 private:
  struct impl;
  std::unique_ptr<impl> pimpl_;
};

}  // namespace hci
}  // namespace bluetooth
