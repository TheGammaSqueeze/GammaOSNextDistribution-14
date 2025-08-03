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

#define LOG_TAG "bt_shim_btm"

#include "main/shim/btm.h"

#include <base/logging.h>

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <mutex>

#include "hci/acl_manager.h"
#include "hci/controller_interface.h"
#include "hci/le_advertising_manager.h"
#include "hci/le_scanning_manager.h"
#include "main/shim/entry.h"
#include "main/shim/helpers.h"
#include "neighbor/connectability.h"
#include "neighbor/discoverability.h"
#include "neighbor/inquiry.h"
#include "neighbor/page.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_int_types.h"
#include "types/ble_address_with_type.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

extern tBTM_CB btm_cb;

static constexpr bool kActiveScanning = true;
static constexpr bool kPassiveScanning = false;

using BtmRemoteDeviceName = tBTM_REMOTE_DEV_NAME;

void btm_ble_process_adv_addr(RawAddress& raw_address,
                              tBLE_ADDR_TYPE* address_type);
void btm_ble_process_adv_pkt_cont(uint16_t event_type,
                                  tBLE_ADDR_TYPE address_type,
                                  const RawAddress& raw_address,
                                  uint8_t primary_phy, uint8_t secondary_phy,
                                  uint8_t advertising_sid, int8_t tx_power,
                                  int8_t rssi, uint16_t periodic_adv_int,
                                  uint8_t data_len, const uint8_t* data,
                                  const RawAddress& original_bda);

namespace bluetooth {

namespace shim {

bool Btm::ReadRemoteName::Start(RawAddress raw_address) {
  std::unique_lock<std::mutex> lock(mutex_);
  if (in_progress_) {
    return false;
  }
  raw_address_ = raw_address;
  in_progress_ = true;
  return true;
}

void Btm::ReadRemoteName::Stop() {
  std::unique_lock<std::mutex> lock(mutex_);
  raw_address_ = RawAddress::kEmpty;
  in_progress_ = false;
}

bool Btm::ReadRemoteName::IsInProgress() const { return in_progress_; }
std::string Btm::ReadRemoteName::AddressString() const {
  return raw_address_.ToString();
}

void Btm::ScanningCallbacks::OnScannerRegistered(
    const bluetooth::hci::Uuid /* app_uuid */,
    bluetooth::hci::ScannerId /* scanner_id */, ScanningStatus /* status */){};

void Btm::ScanningCallbacks::OnSetScannerParameterComplete(
    bluetooth::hci::ScannerId /* scanner_id */, ScanningStatus /* status */){};

void Btm::ScanningCallbacks::OnScanResult(
    uint16_t /* event_type */, uint8_t address_type,
    bluetooth::hci::Address address, uint8_t primary_phy, uint8_t secondary_phy,
    uint8_t advertising_sid, int8_t tx_power, int8_t rssi,
    uint16_t periodic_advertising_interval,
    std::vector<uint8_t> advertising_data) {
  tBLE_ADDR_TYPE ble_address_type = to_ble_addr_type(address_type);
  uint16_t extended_event_type = 0;

  RawAddress raw_address;
  RawAddress::FromString(address.ToString(), raw_address);

  if (ble_address_type != BLE_ADDR_ANONYMOUS) {
    btm_ble_process_adv_addr(raw_address, &ble_address_type);
  }

  // Pass up to GattService#onScanResult
  RawAddress original_bda = raw_address;
  btm_ble_process_adv_addr(raw_address, &ble_address_type);
  btm_ble_process_adv_pkt_cont(
      extended_event_type, ble_address_type, raw_address, primary_phy,
      secondary_phy, advertising_sid, tx_power, rssi,
      periodic_advertising_interval, advertising_data.size(),
      advertising_data.data(), original_bda);
}

void Btm::ScanningCallbacks::OnTrackAdvFoundLost(
    bluetooth::hci::
        AdvertisingFilterOnFoundOnLostInfo /* on_found_on_lost_info */){};
void Btm::ScanningCallbacks::OnBatchScanReports(
    int /* client_if */, int /* status */, int /* report_format */,
    int /* num_records */, std::vector<uint8_t> /* data */){};

void Btm::ScanningCallbacks::OnBatchScanThresholdCrossed(int /* client_if */){};
void Btm::ScanningCallbacks::OnTimeout(){};
void Btm::ScanningCallbacks::OnFilterEnable(bluetooth::hci::Enable /* enable */,
                                            uint8_t /* status */){};
void Btm::ScanningCallbacks::OnFilterParamSetup(
    uint8_t /* available_spaces */, bluetooth::hci::ApcfAction /* action */,
    uint8_t /* status */){};
void Btm::ScanningCallbacks::OnFilterConfigCallback(
    bluetooth::hci::ApcfFilterType /* filter_type */,
    uint8_t /* available_spaces */, bluetooth::hci::ApcfAction /* action */,
    uint8_t /* status */){};
void Btm::ScanningCallbacks::OnPeriodicSyncStarted(
    int /* reg_id */, uint8_t /* status */, uint16_t /* sync_handle */,
    uint8_t /* advertising_sid */,
    bluetooth::hci::AddressWithType /* address_with_type */, uint8_t /* phy */,
    uint16_t /* interval */) {}
void Btm::ScanningCallbacks::OnPeriodicSyncReport(
    uint16_t /* sync_handle */, int8_t /* tx_power */, int8_t /* rssi */,
    uint8_t /* status */, std::vector<uint8_t> /* data */) {}
void Btm::ScanningCallbacks::OnPeriodicSyncLost(uint16_t /* sync_handle */) {}
void Btm::ScanningCallbacks::OnPeriodicSyncTransferred(
    int /* pa_source */, uint8_t /* status */,
    bluetooth::hci::Address /* address */) {}
void Btm::ScanningCallbacks::OnBigInfoReport(uint16_t /* sync_handle */,
                                             bool /* encrypted */) {}

Btm::Btm(os::Handler* handler, neighbor::InquiryModule* inquiry)
    : scanning_timer_(handler), observing_timer_(handler) {
  ASSERT(handler != nullptr);
  ASSERT(inquiry != nullptr);
}

void Btm::SetStandardInquiryResultMode() {
  GetInquiry()->SetStandardInquiryResultMode();
}

void Btm::SetInquiryWithRssiResultMode() {
  GetInquiry()->SetInquiryWithRssiResultMode();
}

void Btm::SetExtendedInquiryResultMode() {
  GetInquiry()->SetExtendedInquiryResultMode();
}

void Btm::SetInterlacedInquiryScan() { GetInquiry()->SetInterlacedScan(); }

void Btm::SetStandardInquiryScan() { GetInquiry()->SetStandardScan(); }

bool Btm::IsInterlacedScanSupported() const {
  return bluetooth::shim::GetController()->SupportsInterlacedInquiryScan();
}

/**
 * One shot inquiry
 */
bool Btm::StartInquiry(
    uint8_t mode, uint8_t duration, uint8_t max_responses,
    LegacyInquiryCompleteCallback legacy_inquiry_complete_callback) {
  switch (mode) {
    case kInquiryModeOff:
      LOG_INFO("%s Stopping inquiry mode", __func__);
      if (limited_inquiry_active_ || general_inquiry_active_) {
        GetInquiry()->StopInquiry();
        limited_inquiry_active_ = false;
        general_inquiry_active_ = false;
      }
      active_inquiry_mode_ = kInquiryModeOff;
      break;

    case kLimitedInquiryMode:
    case kGeneralInquiryMode: {
      if (mode == kLimitedInquiryMode) {
        LOG_INFO(

            "%s Starting limited inquiry mode duration:%hhd max responses:%hhd",
            __func__, duration, max_responses);
        limited_inquiry_active_ = true;
        GetInquiry()->StartLimitedInquiry(duration, max_responses);
        active_inquiry_mode_ = kLimitedInquiryMode;
      } else {
        LOG_INFO(

            "%s Starting general inquiry mode duration:%hhd max responses:%hhd",
            __func__, duration, max_responses);
        general_inquiry_active_ = true;
        GetInquiry()->StartGeneralInquiry(duration, max_responses);
        legacy_inquiry_complete_callback_ = legacy_inquiry_complete_callback;
      }
    } break;

    default:
      LOG_WARN("%s Unknown inquiry mode:%d", __func__, mode);
      return false;
  }
  return true;
}

void Btm::CancelInquiry() {
  LOG_INFO("%s", __func__);
  if (limited_inquiry_active_ || general_inquiry_active_) {
    GetInquiry()->StopInquiry();
    limited_inquiry_active_ = false;
    general_inquiry_active_ = false;
  }
}

bool Btm::IsInquiryActive() const {
  return IsGeneralInquiryActive() || IsLimitedInquiryActive();
}

bool Btm::IsGeneralInquiryActive() const { return general_inquiry_active_; }

bool Btm::IsLimitedInquiryActive() const { return limited_inquiry_active_; }

/**
 * Periodic
 */
bool Btm::StartPeriodicInquiry(uint8_t mode, uint8_t duration,
                               uint8_t max_responses, uint16_t max_delay,
                               uint16_t min_delay,
                               tBTM_INQ_RESULTS_CB* /* p_results_cb */) {
  switch (mode) {
    case kInquiryModeOff:
      limited_periodic_inquiry_active_ = false;
      general_periodic_inquiry_active_ = false;
      GetInquiry()->StopPeriodicInquiry();
      break;

    case kLimitedInquiryMode:
    case kGeneralInquiryMode: {
      if (mode == kLimitedInquiryMode) {
        LOG_INFO("%s Starting limited periodic inquiry mode", __func__);
        limited_periodic_inquiry_active_ = true;
        GetInquiry()->StartLimitedPeriodicInquiry(duration, max_responses,
                                                  max_delay, min_delay);
      } else {
        LOG_INFO("%s Starting general periodic inquiry mode", __func__);
        general_periodic_inquiry_active_ = true;
        GetInquiry()->StartGeneralPeriodicInquiry(duration, max_responses,
                                                  max_delay, min_delay);
      }
    } break;

    default:
      LOG_WARN("%s Unknown inquiry mode:%d", __func__, mode);
      return false;
  }
  return true;
}

bool Btm::IsGeneralPeriodicInquiryActive() const {
  return general_periodic_inquiry_active_;
}

bool Btm::IsLimitedPeriodicInquiryActive() const {
  return limited_periodic_inquiry_active_;
}

/**
 * Discoverability
 */

bluetooth::neighbor::ScanParameters params_{
    .interval = 0,
    .window = 0,
};

void Btm::SetClassicGeneralDiscoverability(uint16_t window, uint16_t interval) {
  params_.window = window;
  params_.interval = interval;

  GetInquiry()->SetScanActivity(params_);
  GetDiscoverability()->StartGeneralDiscoverability();
}

void Btm::SetClassicLimitedDiscoverability(uint16_t window, uint16_t interval) {
  params_.window = window;
  params_.interval = interval;
  GetInquiry()->SetScanActivity(params_);
  GetDiscoverability()->StartLimitedDiscoverability();
}

void Btm::SetClassicDiscoverabilityOff() {
  GetDiscoverability()->StopDiscoverability();
}

DiscoverabilityState Btm::GetClassicDiscoverabilityState() const {
  DiscoverabilityState state{.mode = BTM_NON_DISCOVERABLE,
                             .interval = params_.interval,
                             .window = params_.window};

  if (GetDiscoverability()->IsGeneralDiscoverabilityEnabled()) {
    state.mode = BTM_GENERAL_DISCOVERABLE;
  } else if (GetDiscoverability()->IsLimitedDiscoverabilityEnabled()) {
    state.mode = BTM_LIMITED_DISCOVERABLE;
  }
  return state;
}

void Btm::SetLeGeneralDiscoverability() {
  LOG_WARN("UNIMPLEMENTED %s", __func__);
}

void Btm::SetLeLimitedDiscoverability() {
  LOG_WARN("UNIMPLEMENTED %s", __func__);
}

void Btm::SetLeDiscoverabilityOff() { LOG_WARN("UNIMPLEMENTED %s", __func__); }

DiscoverabilityState Btm::GetLeDiscoverabilityState() const {
  DiscoverabilityState state{
      .mode = kDiscoverableModeOff,
      .interval = 0,
      .window = 0,
  };
  LOG_WARN("UNIMPLEMENTED %s", __func__);
  return state;
}

/**
 * Connectability
 */
void Btm::SetClassicConnectibleOn() {
  GetConnectability()->StartConnectability();
}

void Btm::SetClassicConnectibleOff() {
  GetConnectability()->StopConnectability();
}

ConnectabilityState Btm::GetClassicConnectabilityState() const {
  ConnectabilityState state{.interval = params_.interval,
                            .window = params_.window};

  if (GetConnectability()->IsConnectable()) {
    state.mode = BTM_CONNECTABLE;
  } else {
    state.mode = BTM_NON_CONNECTABLE;
  }
  return state;
}

void Btm::SetInterlacedPageScan() { GetPage()->SetInterlacedScan(); }

void Btm::SetStandardPageScan() { GetPage()->SetStandardScan(); }

void Btm::SetLeConnectibleOn() { LOG_WARN("UNIMPLEMENTED %s", __func__); }

void Btm::SetLeConnectibleOff() { LOG_WARN("UNIMPLEMENTED %s", __func__); }

ConnectabilityState Btm::GetLeConnectabilityState() const {
  ConnectabilityState state{
      .mode = kConnectibleModeOff,
      .interval = 0,
      .window = 0,
  };
  LOG_WARN("UNIMPLEMENTED %s", __func__);
  return state;
}

bool Btm::UseLeLink(const RawAddress& raw_address) const {
  if (GetAclManager()->HACK_GetHandle(ToGdAddress(raw_address)) != 0xFFFF) {
    return false;
  }
  if (GetAclManager()->HACK_GetLeHandle(ToGdAddress(raw_address)) != 0xFFFF) {
    return true;
  }
  // TODO(hsz): use correct transport by using storage records.  For now assume
  // LE for GATT and HID.
  return true;
}

BtmStatus Btm::ReadClassicRemoteDeviceName(const RawAddress& /* raw_address */,
                                           tBTM_NAME_CMPL_CB* /* callback */) {
  LOG_ALWAYS_FATAL("unreachable");
  return BTM_UNDEFINED;
}

BtmStatus Btm::CancelAllReadRemoteDeviceName() {
  LOG_ALWAYS_FATAL("unreachable");
  return BTM_UNDEFINED;
}

void Btm::StartAdvertising() { LOG_ALWAYS_FATAL("unreachable"); }

void Btm::StopAdvertising() {
  if (advertiser_id_ == hci::LeAdvertisingManager::kInvalidId) {
    LOG_WARN("%s No active advertising", __func__);
    return;
  }
  GetAdvertising()->RemoveAdvertiser(advertiser_id_);
  advertiser_id_ = hci::LeAdvertisingManager::kInvalidId;
  LOG_INFO("%s Stopped advertising", __func__);
}

void Btm::StartConnectability() { StartAdvertising(); }

void Btm::StopConnectability() { StopAdvertising(); }

void Btm::StartActiveScanning() { StartScanning(kActiveScanning); }

void Btm::StopActiveScanning() { GetScanning()->Scan(false); }

void Btm::SetScanningTimer(uint64_t duration_ms,
                           common::OnceCallback<void()> callback) {
  scanning_timer_.Schedule(std::move(callback),
                           std::chrono::milliseconds(duration_ms));
}

void Btm::CancelScanningTimer() { scanning_timer_.Cancel(); }

void Btm::StartObserving() { StartScanning(kPassiveScanning); }

void Btm::StopObserving() { StopActiveScanning(); }

void Btm::SetObservingTimer(uint64_t duration_ms,
                            common::OnceCallback<void()> callback) {
  observing_timer_.Schedule(std::move(callback),
                            std::chrono::milliseconds(duration_ms));
}

void Btm::CancelObservingTimer() { observing_timer_.Cancel(); }

void Btm::StartScanning(bool /* use_active_scanning */) {
  GetScanning()->RegisterScanningCallback(&scanning_callbacks_);
  GetScanning()->Scan(true);
}

size_t Btm::GetNumberOfAdvertisingInstances() const {
  return GetAdvertising()->GetNumberOfAdvertisingInstances();
}

size_t Btm::GetNumberOfAdvertisingInstancesInUse() const {
  return GetAdvertising()->GetNumberOfAdvertisingInstancesInUse();
}

uint16_t Btm::GetAclHandle(const RawAddress& remote_bda,
                           tBT_TRANSPORT transport) {
  auto acl_manager = GetAclManager();
  if (transport == BT_TRANSPORT_BR_EDR) {
    return acl_manager->HACK_GetHandle(ToGdAddress(remote_bda));
  } else {
    return acl_manager->HACK_GetLeHandle(ToGdAddress(remote_bda));
  }
}

hci::AddressWithType Btm::GetAddressAndType(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec != NULL && p_dev_rec->device_type & BT_DEVICE_TYPE_BLE) {
    if (!p_dev_rec->ble.identity_address_with_type.bda.IsEmpty()) {
      return ToAddressWithType(p_dev_rec->ble.identity_address_with_type.bda,
                               p_dev_rec->ble.identity_address_with_type.type);
    } else {
      return ToAddressWithType(p_dev_rec->ble.pseudo_addr,
                               p_dev_rec->ble.AddressType());
    }
  }
  LOG(ERROR) << "Unknown bd_addr. Use public address";
  return ToAddressWithType(bd_addr, BLE_ADDR_PUBLIC);
}

}  // namespace shim

}  // namespace bluetooth
