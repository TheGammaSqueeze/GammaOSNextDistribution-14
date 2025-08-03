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
#include "hci/le_scanning_manager.h"

#include <android_bluetooth_flags.h>

#include <memory>
#include <unordered_map>

#include "hci/acl_manager.h"
#include "hci/controller.h"
#include "hci/event_checkers.h"
#include "hci/hci_layer.h"
#include "hci/hci_packets.h"
#include "hci/le_periodic_sync_manager.h"
#include "hci/le_scanning_interface.h"
#include "hci/le_scanning_reassembler.h"
#include "hci/vendor_specific_event_manager.h"
#include "module.h"
#include "os/handler.h"
#include "os/log.h"
#include "os/system_properties.h"
#include "storage/storage_module.h"

namespace bluetooth {
namespace hci {

constexpr uint16_t kLeScanWindowMin = 0x0004;
constexpr uint16_t kLeScanWindowMax = 0x4000;
constexpr int64_t kLeScanRssiMin = -127;
constexpr int64_t kLeScanRssiMax = 20;
constexpr int64_t kLeScanRssiUnknown = 127;
constexpr int64_t kLeRxPathLossCompMin = -128;
constexpr int64_t kLeRxPathLossCompMax = 127;
constexpr uint16_t kDefaultLeExtendedScanWindow = 4800;
constexpr uint16_t kLeExtendedScanWindowMax = 0xFFFF;
constexpr uint16_t kLeScanIntervalMin = 0x0004;
constexpr uint16_t kLeScanIntervalMax = 0x4000;
constexpr uint16_t kDefaultLeExtendedScanInterval = 4800;
constexpr uint16_t kLeExtendedScanIntervalMax = 0xFFFF;

constexpr uint8_t kScannableBit = 1;
constexpr uint8_t kDirectedBit = 2;
constexpr uint8_t kScanResponseBit = 3;
constexpr uint8_t kLegacyBit = 4;
constexpr uint8_t kDataStatusBits = 5;

// system properties
const std::string kLeRxPathLossCompProperty = "bluetooth.hardware.radio.le_rx_path_loss_comp_db";
const std::string kPropertyDisableApcfExtendedFeatures = "bluetooth.le.disable_apcf_extended_features";
bool kDisableApcfExtendedFeatures = false;

const ModuleFactory LeScanningManager::Factory = ModuleFactory([]() { return new LeScanningManager(); });

enum class ScanApiType {
  LEGACY = 1,
  ANDROID_HCI = 2,
  EXTENDED = 3,
};

struct Scanner {
  Uuid app_uuid;
  bool in_use;
};

class NullScanningCallback : public ScanningCallback {
  void OnScannerRegistered(
      const Uuid /* app_uuid */, ScannerId /* scanner_id */, ScanningStatus /* status */) override {
    LOG_INFO("OnScannerRegistered in NullScanningCallback");
  }
  void OnSetScannerParameterComplete(
      ScannerId /* scanner_id */, ScanningStatus /* status */) override {
    LOG_INFO("OnSetScannerParameterComplete in NullScanningCallback");
  }
  void OnScanResult(
      uint16_t /* event_type */,
      uint8_t /* address_type */,
      Address /* address */,
      uint8_t /* primary_phy */,
      uint8_t /* secondary_phy */,
      uint8_t /* advertising_sid */,
      int8_t /* tx_power */,
      int8_t /* rssi */,
      uint16_t /* periodic_advertising_interval */,
      std::vector<uint8_t> /* advertising_data */) override {
    LOG_INFO("OnScanResult in NullScanningCallback");
  }
  void OnTrackAdvFoundLost(
      AdvertisingFilterOnFoundOnLostInfo /* on_found_on_lost_info */) override {
    LOG_INFO("OnTrackAdvFoundLost in NullScanningCallback");
  }
  void OnBatchScanReports(
      int /* client_if */,
      int /* status */,
      int /* report_format */,
      int /* num_records */,
      std::vector<uint8_t> /* data */) override {
    LOG_INFO("OnBatchScanReports in NullScanningCallback");
  }
  void OnBatchScanThresholdCrossed(int /* client_if */) override {
    LOG_INFO("OnBatchScanThresholdCrossed in NullScanningCallback");
  }
  void OnTimeout() override {
    LOG_INFO("OnTimeout in NullScanningCallback");
  }
  void OnFilterEnable(Enable /* enable */, uint8_t /* status */) override {
    LOG_INFO("OnFilterEnable in NullScanningCallback");
  }
  void OnFilterParamSetup(
      uint8_t /* available_spaces */, ApcfAction /* action */, uint8_t /* status */) override {
    LOG_INFO("OnFilterParamSetup in NullScanningCallback");
  }
  void OnFilterConfigCallback(
      ApcfFilterType /* filter_type */,
      uint8_t /* available_spaces */,
      ApcfAction /* action */,
      uint8_t /* status */) override {
    LOG_INFO("OnFilterConfigCallback in NullScanningCallback");
  }
  void OnPeriodicSyncStarted(
      int /* reg_id */,
      uint8_t /* status */,
      uint16_t /* sync_handle */,
      uint8_t /* advertising_sid */,
      AddressWithType /* address_with_type */,
      uint8_t /* phy */,
      uint16_t /* interval */) override {
    LOG_INFO("OnPeriodicSyncStarted in NullScanningCallback");
  };
  void OnPeriodicSyncReport(
      uint16_t /* sync_handle */,
      int8_t /* tx_power */,
      int8_t /* rssi */,
      uint8_t /* status */,
      std::vector<uint8_t> /* data */) override {
    LOG_INFO("OnPeriodicSyncReport in NullScanningCallback");
  };
  void OnPeriodicSyncLost(uint16_t /* sync_handle */) override {
    LOG_INFO("OnPeriodicSyncLost in NullScanningCallback");
  };
  void OnPeriodicSyncTransferred(
      int /* pa_source */, uint8_t /* status */, Address /* address */) override {
    LOG_INFO("OnPeriodicSyncTransferred in NullScanningCallback");
  };
  void OnBigInfoReport(uint16_t /* sync_handle */, bool /* encrypted */) {
    LOG_INFO("OnBigInfoReport in NullScanningCallback");
  };
};

enum class BatchScanState {
  ERROR_STATE = 0,
  ENABLE_CALLED = 1,
  ENABLED_STATE = 2,
  DISABLE_CALLED = 3,
  DISABLED_STATE = 4,
};

#define BTM_BLE_BATCH_SCAN_MODE_DISABLE 0
#define BTM_BLE_BATCH_SCAN_MODE_PASS 1
#define BTM_BLE_BATCH_SCAN_MODE_ACTI 2
#define BTM_BLE_BATCH_SCAN_MODE_PASS_ACTI 3

struct BatchScanConfig {
  BatchScanState current_state;
  BatchScanMode scan_mode;
  uint32_t scan_interval;
  uint32_t scan_window;
  BatchScanDiscardRule discard_rule;
  ScannerId ref_value;
};

struct LeScanningManager::impl : public LeAddressManagerCallback {
  impl(Module* module) : module_(module), le_scanning_interface_(nullptr) {}

  ~impl() {
    if (address_manager_registered_) {
      le_address_manager_->Unregister(this);
    }
  }

  void start(
      os::Handler* handler,
      HciLayer* hci_layer,
      Controller* controller,
      AclManager* acl_manager,
      VendorSpecificEventManager* vendor_specific_event_manager,
      storage::StorageModule* storage_module) {
    module_handler_ = handler;
    hci_layer_ = hci_layer;
    controller_ = controller;
    acl_manager_ = acl_manager;
    vendor_specific_event_manager_ = vendor_specific_event_manager;
    storage_module_ = storage_module;
    le_address_manager_ = acl_manager->GetLeAddressManager();
    le_scanning_interface_ = hci_layer_->GetLeScanningInterface(
        module_handler_->BindOn(this, &LeScanningManager::impl::handle_scan_results));
    periodic_sync_manager_.Init(le_scanning_interface_, module_handler_);
    /* Check to see if the opcode is supported and C19 (support for extended advertising). */
    if (controller_->IsSupported(OpCode::LE_SET_EXTENDED_SCAN_PARAMETERS) &&
        controller->SupportsBleExtendedAdvertising()) {
      api_type_ = ScanApiType::EXTENDED;
      interval_ms_ = kDefaultLeExtendedScanInterval;
      window_ms_ = kDefaultLeExtendedScanWindow;
    } else if (controller_->IsSupported(OpCode::LE_EXTENDED_SCAN_PARAMS)) {
      api_type_ = ScanApiType::ANDROID_HCI;
    } else {
      api_type_ = ScanApiType::LEGACY;
    }
    is_filter_supported_ = controller_->IsSupported(OpCode::LE_ADV_FILTER);
    if (os::GetSystemProperty(kPropertyDisableApcfExtendedFeatures) == "1")
      kDisableApcfExtendedFeatures = true;
    if (is_filter_supported_ && !kDisableApcfExtendedFeatures) {
      le_scanning_interface_->EnqueueCommand(
          LeAdvFilterReadExtendedFeaturesBuilder::Create(),
          module_handler_->BindOnceOn(this, &impl::on_apcf_read_extended_features_complete));
    }
    is_batch_scan_supported_ = controller->IsSupported(OpCode::LE_BATCH_SCAN);
    is_periodic_advertising_sync_transfer_sender_supported_ =
        controller_->SupportsBlePeriodicAdvertisingSyncTransferSender();
    total_num_of_advt_tracked_ = controller->GetVendorCapabilities().total_num_of_advt_tracked_;
    if (is_batch_scan_supported_) {
      vendor_specific_event_manager_->RegisterEventHandler(
          VseSubeventCode::BLE_THRESHOLD, handler->BindOn(this, &LeScanningManager::impl::on_storage_threshold_breach));
      vendor_specific_event_manager_->RegisterEventHandler(
          VseSubeventCode::BLE_TRACKING, handler->BindOn(this, &LeScanningManager::impl::on_advertisement_tracking));
    }
    scanners_ = std::vector<Scanner>(kMaxAppNum + 1);
    for (size_t i = 0; i < scanners_.size(); i++) {
      scanners_[i].app_uuid = Uuid::kEmpty;
      scanners_[i].in_use = false;
    }
    batch_scan_config_.current_state = BatchScanState::DISABLED_STATE;
    batch_scan_config_.ref_value = kInvalidScannerId;
    le_rx_path_loss_comp_ = get_rx_path_loss_compensation();
  }

  void stop() {
    for (auto subevent_code : LeScanningEvents) {
      hci_layer_->UnregisterLeEventHandler(subevent_code);
    }
    if (is_batch_scan_supported_) {
      // TODO implete vse module
      // hci_layer_->UnregisterVesEventHandler(VseSubeventCode::BLE_THRESHOLD);
      // hci_layer_->UnregisterVesEventHandler(VseSubeventCode::BLE_TRACKING);
    }
    batch_scan_config_.current_state = BatchScanState::DISABLED_STATE;
    batch_scan_config_.ref_value = kInvalidScannerId;
    scanning_callbacks_ = &null_scanning_callback_;
    periodic_sync_manager_.SetScanningCallback(scanning_callbacks_);
  }

  void handle_scan_results(LeMetaEventView event) {
    switch (event.GetSubeventCode()) {
      case SubeventCode::ADVERTISING_REPORT:
        handle_advertising_report(LeAdvertisingReportRawView::Create(event));
        break;
      case SubeventCode::DIRECTED_ADVERTISING_REPORT:
        handle_directed_advertising_report(LeDirectedAdvertisingReportView::Create(event));
        break;
      case SubeventCode::EXTENDED_ADVERTISING_REPORT:
        handle_extended_advertising_report(LeExtendedAdvertisingReportRawView::Create(event));
        break;
      case SubeventCode::PERIODIC_ADVERTISING_SYNC_ESTABLISHED:
        LePeriodicAdvertisingSyncEstablishedView::Create(event);
        periodic_sync_manager_.HandleLePeriodicAdvertisingSyncEstablished(
            LePeriodicAdvertisingSyncEstablishedView::Create(event));
        break;
      case SubeventCode::PERIODIC_ADVERTISING_REPORT:
        periodic_sync_manager_.HandleLePeriodicAdvertisingReport(LePeriodicAdvertisingReportView::Create(event));
        break;
      case SubeventCode::PERIODIC_ADVERTISING_SYNC_LOST:
        periodic_sync_manager_.HandleLePeriodicAdvertisingSyncLost(LePeriodicAdvertisingSyncLostView::Create(event));
        break;
      case SubeventCode::PERIODIC_ADVERTISING_SYNC_TRANSFER_RECEIVED:
        periodic_sync_manager_.HandleLePeriodicAdvertisingSyncTransferReceived(
            LePeriodicAdvertisingSyncTransferReceivedView::Create(event));
        break;
      case SubeventCode::SCAN_TIMEOUT:
        scanning_callbacks_->OnTimeout();
        break;
      case SubeventCode::BIG_INFO_ADVERTISING_REPORT:
        periodic_sync_manager_.HandleLeBigInfoAdvertisingReport(
            LeBigInfoAdvertisingReportView::Create(event));
        break;
      default:
        LOG_ALWAYS_FATAL("Unknown advertising subevent %s", SubeventCodeText(event.GetSubeventCode()).c_str());
    }
  }

  struct ExtendedEventTypeOptions {
    bool connectable{false};
    bool scannable{false};
    bool directed{false};
    bool scan_response{false};
    bool legacy{false};
    bool continuing{false};
    bool truncated{false};
  };

  int8_t get_rx_path_loss_compensation() {
    int8_t compensation = 0;
    auto compensation_prop = os::GetSystemProperty(kLeRxPathLossCompProperty);
    if (compensation_prop) {
      auto compensation_number = common::Int64FromString(compensation_prop.value());
      if (compensation_number) {
        int64_t number = compensation_number.value();
        if (number < kLeRxPathLossCompMin || number > kLeRxPathLossCompMax) {
          LOG_ERROR("Invalid number for rx path loss compensation: %" PRId64, number);
        } else {
          compensation = number;
        }
      }
    }
    LOG_INFO("Rx path loss compensation: %d", compensation);
    return compensation;
  }

  int8_t get_rssi_after_calibration(int8_t rssi) {
    if (le_rx_path_loss_comp_ == 0 || rssi == kLeScanRssiUnknown) {
      return rssi;
    }
    int8_t calibrated_rssi = rssi;
    int64_t number = rssi + le_rx_path_loss_comp_;
    if (number < kLeScanRssiMin || number > kLeScanRssiMax) {
      LOG_ERROR("Invalid number for calibrated rssi: %" PRId64, number);
    } else {
      calibrated_rssi = number;
    }
    return calibrated_rssi;
  }

  uint16_t transform_to_extended_event_type(ExtendedEventTypeOptions o) {
    return (o.connectable ? 0x0001 << 0 : 0) | (o.scannable ? 0x0001 << 1 : 0) |
           (o.directed ? 0x0001 << 2 : 0) | (o.scan_response ? 0x0001 << 3 : 0) |
           (o.legacy ? 0x0001 << 4 : 0) | (o.continuing ? 0x0001 << 5 : 0) |
           (o.truncated ? 0x0001 << 6 : 0);
  }

  void handle_advertising_report(LeAdvertisingReportRawView event_view) {
    if (!event_view.IsValid()) {
      LOG_INFO("Dropping invalid advertising event");
      return;
    }
    std::vector<LeAdvertisingResponseRaw> reports = event_view.GetResponses();
    if (reports.empty()) {
      LOG_INFO("Zero results in advertising event");
      return;
    }

    for (LeAdvertisingResponseRaw report : reports) {
      uint16_t extended_event_type = 0;
      switch (report.event_type_) {
        case AdvertisingEventType::ADV_IND:
          extended_event_type = transform_to_extended_event_type(
              {.connectable = true, .scannable = true, .legacy = true});
          break;
        case AdvertisingEventType::ADV_DIRECT_IND:
          extended_event_type = transform_to_extended_event_type(
              {.connectable = true, .directed = true, .legacy = true});
          break;
        case AdvertisingEventType::ADV_SCAN_IND:
          extended_event_type =
              transform_to_extended_event_type({.scannable = true, .legacy = true});
          break;
        case AdvertisingEventType::ADV_NONCONN_IND:
          extended_event_type = transform_to_extended_event_type({.legacy = true});
          break;
        case AdvertisingEventType::SCAN_RESPONSE:
          if (IS_FLAG_ENABLED(fix_nonconnectable_scannable_advertisement)) {
            // We don't know if the initial advertising report was connectable or not.
            // LeScanningReassembler fixes the connectable field.
            extended_event_type = transform_to_extended_event_type(
                {.scannable = true, .scan_response = true, .legacy = true});
          } else {
            extended_event_type = transform_to_extended_event_type(
                {.connectable = true, .scannable = true, .scan_response = true, .legacy = true});
          }
          break;
        default:
          LOG_WARN("Unsupported event type:%d", (uint16_t)report.event_type_);
          return;
      }

      process_advertising_package_content(
          extended_event_type,
          (uint8_t)report.address_type_,
          report.address_,
          (uint8_t)PrimaryPhyType::LE_1M,
          (uint8_t)SecondaryPhyType::NO_PACKETS,
          kAdvertisingDataInfoNotPresent,
          kTxPowerInformationNotPresent,
          report.rssi_,
          kNotPeriodicAdvertisement,
          report.advertising_data_);
    }
  }

  void handle_directed_advertising_report(LeDirectedAdvertisingReportView /*event_view*/) {
    LOG_WARN("HCI Directed Advertising Report events are not supported");
  }

  void handle_extended_advertising_report(LeExtendedAdvertisingReportRawView event_view) {
    if (!event_view.IsValid()) {
      LOG_INFO("Dropping invalid advertising event");
      return;
    }

    std::vector<LeExtendedAdvertisingResponseRaw> reports = event_view.GetResponses();
    if (reports.empty()) {
      LOG_INFO("Zero results in advertising event");
      return;
    }

    for (LeExtendedAdvertisingResponseRaw& report : reports) {
      uint16_t event_type = report.connectable_ | (report.scannable_ << kScannableBit) |
                            (report.directed_ << kDirectedBit) | (report.scan_response_ << kScanResponseBit) |
                            (report.legacy_ << kLegacyBit) | ((uint16_t)report.data_status_ << kDataStatusBits);
      process_advertising_package_content(
          event_type,
          (uint8_t)report.address_type_,
          report.address_,
          (uint8_t)report.primary_phy_,
          (uint8_t)report.secondary_phy_,
          report.advertising_sid_,
          report.tx_power_,
          report.rssi_,
          report.periodic_advertising_interval_,
          report.advertising_data_);
    }
  }

  void process_advertising_package_content(
      uint16_t event_type,
      uint8_t address_type,
      Address address,
      uint8_t primary_phy,
      uint8_t secondary_phy,
      uint8_t advertising_sid,
      int8_t tx_power,
      int8_t rssi,
      uint16_t periodic_advertising_interval,
      const std::vector<uint8_t>& advertising_data) {
    // When using the vendor command Le Set Extended Params to
    // configure a filter accept list based e.g. on the service UUIDs
    // found in the report, we ignore the scan responses as we cannot be
    // certain that they will not be dropped by the filter.
    // TODO(b/275754998): Improve the decision on what to do with scan responses: Only when used
    // with hardware-filtering features should we ignore waiting for scan response, and make sure
    // scan responses are still reported too.
    scanning_reassembler_.SetIgnoreScanResponses(
        le_scan_type_ == LeScanType::PASSIVE ||
        filter_policy_ == LeScanningFilterPolicy::FILTER_ACCEPT_LIST_ONLY);

    std::optional<LeScanningReassembler::CompleteAdvertisingData> processed_report =
        scanning_reassembler_.ProcessAdvertisingReport(
            event_type, address_type, address, advertising_sid, advertising_data);

    if (processed_report.has_value()) {
      switch (address_type) {
        case (uint8_t)AddressType::PUBLIC_DEVICE_ADDRESS:
        case (uint8_t)AddressType::PUBLIC_IDENTITY_ADDRESS:
          address_type = (uint8_t)AddressType::PUBLIC_DEVICE_ADDRESS;
          break;
        case (uint8_t)AddressType::RANDOM_DEVICE_ADDRESS:
        case (uint8_t)AddressType::RANDOM_IDENTITY_ADDRESS:
          address_type = (uint8_t)AddressType::RANDOM_DEVICE_ADDRESS;
          break;
      }

      const uint16_t result_event_type = IS_FLAG_ENABLED(fix_nonconnectable_scannable_advertisement)
                                             ? processed_report->extended_event_type
                                             : event_type;

      scanning_callbacks_->OnScanResult(
          result_event_type,
          address_type,
          address,
          primary_phy,
          secondary_phy,
          advertising_sid,
          tx_power,
          get_rssi_after_calibration(rssi),
          periodic_advertising_interval,
          std::move(processed_report->data));
    }
  }

  void configure_scan() {
    std::vector<PhyScanParameters> parameter_vector;
    PhyScanParameters phy_scan_parameters;
    phy_scan_parameters.le_scan_window_ = window_ms_;
    phy_scan_parameters.le_scan_interval_ = interval_ms_;
    phy_scan_parameters.le_scan_type_ = le_scan_type_;
    parameter_vector.push_back(phy_scan_parameters);
    uint8_t phys_in_use = 1;

    // The Host shall not issue set scan parameter command when scanning is enabled
    stop_scan();

    if (le_address_manager_->GetAddressPolicy() != LeAddressManager::USE_PUBLIC_ADDRESS) {
      own_address_type_ = OwnAddressType::RANDOM_DEVICE_ADDRESS;
    } else {
      own_address_type_ = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
    }

    switch (api_type_) {
      case ScanApiType::EXTENDED:
        le_scanning_interface_->EnqueueCommand(
            LeSetExtendedScanParametersBuilder::Create(
                own_address_type_, filter_policy_, phys_in_use, parameter_vector),
            module_handler_->BindOnceOn(this, &impl::on_set_scan_parameter_complete));
        break;
      case ScanApiType::ANDROID_HCI:
        le_scanning_interface_->EnqueueCommand(
            LeExtendedScanParamsBuilder::Create(
                le_scan_type_, interval_ms_, window_ms_, own_address_type_, filter_policy_),
            module_handler_->BindOnceOn(this, &impl::on_set_scan_parameter_complete));

        break;
      case ScanApiType::LEGACY:
        le_scanning_interface_->EnqueueCommand(

            LeSetScanParametersBuilder::Create(
                le_scan_type_, interval_ms_, window_ms_, own_address_type_, filter_policy_),
            module_handler_->BindOnceOn(this, &impl::on_set_scan_parameter_complete));
        break;
    }
  }

  void register_scanner(const Uuid app_uuid) {
    for (uint8_t i = 1; i <= kMaxAppNum; i++) {
      if (scanners_[i].in_use && scanners_[i].app_uuid == app_uuid) {
        LOG_ERROR("Application already registered %s", app_uuid.ToString().c_str());
        scanning_callbacks_->OnScannerRegistered(app_uuid, 0x00, ScanningCallback::ScanningStatus::INTERNAL_ERROR);
        return;
      }
    }

    // valid value of scanner id : 1 ~ kMaxAppNum
    for (uint8_t i = 1; i <= kMaxAppNum; i++) {
      if (!scanners_[i].in_use) {
        scanners_[i].app_uuid = app_uuid;
        scanners_[i].in_use = true;
        scanning_callbacks_->OnScannerRegistered(app_uuid, i, ScanningCallback::ScanningStatus::SUCCESS);
        return;
      }
    }

    LOG_ERROR("Unable to register scanner, max client reached:%d", kMaxAppNum);
    scanning_callbacks_->OnScannerRegistered(app_uuid, 0x00, ScanningCallback::ScanningStatus::NO_RESOURCES);
  }

  void unregister_scanner(ScannerId scanner_id) {
    if (scanner_id <= 0 || scanner_id > kMaxAppNum) {
      LOG_WARN("Invalid scanner id");
      return;
    }

    if (scanners_[scanner_id].in_use) {
      scanners_[scanner_id].in_use = false;
      scanners_[scanner_id].app_uuid = Uuid::kEmpty;
    } else {
      LOG_WARN("Unregister scanner with unused scanner id");
    }
  }

  void scan(bool start) {
    // On-resume flag should always be reset if there is an explicit start/stop call.
    scan_on_resume_ = false;
    if (start) {
      configure_scan();
      start_scan();
    } else {
      if (address_manager_registered_) {
        le_address_manager_->Unregister(this);
        address_manager_registered_ = false;
        paused_ = false;
      }
      stop_scan();
    }
  }

  void start_scan() {
    // If we receive start_scan during paused, set scan_on_resume_ to true
    if (paused_ && address_manager_registered_) {
      scan_on_resume_ = true;
      return;
    }
    is_scanning_ = true;
    if (!address_manager_registered_) {
      le_address_manager_->Register(this);
      address_manager_registered_ = true;
    }

    switch (api_type_) {
      case ScanApiType::EXTENDED:
        le_scanning_interface_->EnqueueCommand(
            LeSetExtendedScanEnableBuilder::Create(
                Enable::ENABLED,
#if TARGET_FLOSS
                FilterDuplicates::ENABLED /* filter duplicates */,
#else
                FilterDuplicates::DISABLED /* filter duplicates */,
#endif
                0,
                0),
            module_handler_->BindOnce(check_complete<LeSetExtendedScanEnableCompleteView>));
        break;
      case ScanApiType::ANDROID_HCI:
      case ScanApiType::LEGACY:
        le_scanning_interface_->EnqueueCommand(
            LeSetScanEnableBuilder::Create(
                Enable::ENABLED, Enable::DISABLED /* filter duplicates */),
            module_handler_->BindOnce(check_complete<LeSetScanEnableCompleteView>));
        break;
    }
  }

  void stop_scan() {
    if (!is_scanning_) {
      LOG_INFO("Scanning already stopped, return!");
      return;
    }
    is_scanning_ = false;

    switch (api_type_) {
      case ScanApiType::EXTENDED:
        le_scanning_interface_->EnqueueCommand(
            LeSetExtendedScanEnableBuilder::Create(
                Enable::DISABLED,
#if TARGET_FLOSS
                FilterDuplicates::ENABLED /* filter duplicates */,
#else
                FilterDuplicates::DISABLED /* filter duplicates */,
#endif
                0,
                0),
            module_handler_->BindOnce(check_complete<LeSetExtendedScanEnableCompleteView>));
        break;
      case ScanApiType::ANDROID_HCI:
      case ScanApiType::LEGACY:
        le_scanning_interface_->EnqueueCommand(
            LeSetScanEnableBuilder::Create(
                Enable::DISABLED, Enable::DISABLED /* filter duplicates */),
            module_handler_->BindOnce(check_complete<LeSetScanEnableCompleteView>));
        break;
    }
  }

  void set_scan_parameters(ScannerId scanner_id, LeScanType scan_type, uint16_t scan_interval, uint16_t scan_window) {
    uint32_t max_scan_interval = kLeScanIntervalMax;
    uint32_t max_scan_window = kLeScanWindowMax;
    if (api_type_ == ScanApiType::EXTENDED) {
      max_scan_interval = kLeExtendedScanIntervalMax;
      max_scan_window = kLeExtendedScanWindowMax;
    }

    if (scan_type != LeScanType::ACTIVE && scan_type != LeScanType::PASSIVE) {
      LOG_ERROR("Invalid scan type");
      scanning_callbacks_->OnSetScannerParameterComplete(
          scanner_id, ScanningCallback::ScanningStatus::ILLEGAL_PARAMETER);
      return;
    }
    if (scan_interval > max_scan_interval || scan_interval < kLeScanIntervalMin) {
      LOG_ERROR("Invalid scan_interval %d", scan_interval);
      scanning_callbacks_->OnSetScannerParameterComplete(
          scanner_id, ScanningCallback::ScanningStatus::ILLEGAL_PARAMETER);
      return;
    }
    if (scan_window > max_scan_window || scan_window < kLeScanWindowMin) {
      LOG_ERROR("Invalid scan_window %d", scan_window);
      scanning_callbacks_->OnSetScannerParameterComplete(
          scanner_id, ScanningCallback::ScanningStatus::ILLEGAL_PARAMETER);
      return;
    }
    le_scan_type_ = scan_type;
    interval_ms_ = scan_interval;
    window_ms_ = scan_window;
    scanning_callbacks_->OnSetScannerParameterComplete(scanner_id, ScanningCallback::SUCCESS);
  }

  void set_scan_filter_policy(LeScanningFilterPolicy filter_policy) {
    filter_policy_ = filter_policy;
  }

  void scan_filter_enable(bool enable) {
    if (!is_filter_supported_) {
      LOG_WARN("Advertising filter is not supported");
      return;
    }

    Enable apcf_enable = enable ? Enable::ENABLED : Enable::DISABLED;
    le_scanning_interface_->EnqueueCommand(
        LeAdvFilterEnableBuilder::Create(apcf_enable),
        module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
  }

  bool is_bonded(Address target_address) {
    for (auto device : storage_module_->GetBondedDevices()) {
      if (device.GetAddress() == target_address) {
        LOG_DEBUG("Addresses match!");
        return true;
      }
    }
    LOG_DEBUG("Addresse DON'Ts match!");
    return false;
  }

  void scan_filter_parameter_setup(
      ApcfAction action, uint8_t filter_index, AdvertisingFilterParameter advertising_filter_parameter) {
    if (!is_filter_supported_) {
      LOG_WARN("Advertising filter is not supported");
      return;
    }

    auto entry = remove_me_later_map_.find(filter_index);
    switch (action) {
      case ApcfAction::ADD:
        le_scanning_interface_->EnqueueCommand(
            LeAdvFilterAddFilteringParametersBuilder::Create(
                filter_index,
                advertising_filter_parameter.feature_selection,
                advertising_filter_parameter.list_logic_type,
                advertising_filter_parameter.filter_logic_type,
                advertising_filter_parameter.rssi_high_thresh,
                advertising_filter_parameter.delivery_mode,
                advertising_filter_parameter.onfound_timeout,
                advertising_filter_parameter.onfound_timeout_cnt,
                advertising_filter_parameter.rssi_low_thresh,
                advertising_filter_parameter.onlost_timeout,
                advertising_filter_parameter.num_of_tracking_entries),
            module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
        break;
      case ApcfAction::DELETE:
        tracker_id_map_.erase(filter_index);
        le_scanning_interface_->EnqueueCommand(
            LeAdvFilterDeleteFilteringParametersBuilder::Create(filter_index),
            module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));

        // IRK Scanning
        if (entry != remove_me_later_map_.end()) {
          // Don't want to remove for a bonded device
          if (!is_bonded(entry->second.GetAddress())) {
            le_address_manager_->RemoveDeviceFromResolvingList(
                static_cast<PeerAddressType>(entry->second.GetAddressType()), entry->second.GetAddress());
          }
          remove_me_later_map_.erase(filter_index);
        }

        break;
      case ApcfAction::CLEAR:
        le_scanning_interface_->EnqueueCommand(
            LeAdvFilterClearFilteringParametersBuilder::Create(),
            module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));

        // IRK Scanning
        if (entry != remove_me_later_map_.end()) {
          // Don't want to remove for a bonded device
          if (!is_bonded(entry->second.GetAddress())) {
            le_address_manager_->RemoveDeviceFromResolvingList(
                static_cast<PeerAddressType>(entry->second.GetAddressType()), entry->second.GetAddress());
          }
          remove_me_later_map_.erase(filter_index);
        }

        break;
      default:
        LOG_ERROR("Unknown action type: %d", (uint16_t)action);
        break;
    }
  }

  void scan_filter_add(uint8_t filter_index, std::vector<AdvertisingPacketContentFilterCommand> filters) {
    if (!is_filter_supported_) {
      LOG_WARN("Advertising filter is not supported");
      return;
    }

    ApcfAction apcf_action = ApcfAction::ADD;
    for (auto filter : filters) {
      /* If data is passed, both mask and data have to be the same length */
      if (filter.data.size() != filter.data_mask.size() && filter.data.size() != 0 && filter.data_mask.size() != 0) {
        LOG_ERROR("data and data_mask are of different size");
        continue;
      }

      switch (filter.filter_type) {
        case ApcfFilterType::BROADCASTER_ADDRESS: {
          update_address_filter(apcf_action, filter_index, filter.address, filter.application_address_type, filter.irk);
          break;
        }
        case ApcfFilterType::SERVICE_UUID:
        case ApcfFilterType::SERVICE_SOLICITATION_UUID: {
          update_uuid_filter(apcf_action, filter_index, filter.filter_type, filter.uuid, filter.uuid_mask);
          break;
        }
        case ApcfFilterType::LOCAL_NAME: {
          update_local_name_filter(apcf_action, filter_index, filter.name);
          break;
        }
        case ApcfFilterType::MANUFACTURER_DATA: {
          update_manufacturer_data_filter(
              apcf_action, filter_index, filter.company, filter.company_mask, filter.data, filter.data_mask);
          break;
        }
        case ApcfFilterType::SERVICE_DATA: {
          update_service_data_filter(apcf_action, filter_index, filter.data, filter.data_mask);
          break;
        }
        case ApcfFilterType::TRANSPORT_DISCOVERY_DATA: {
          update_transport_discovery_data_filter(
              apcf_action,
              filter_index,
              filter.org_id,
              filter.tds_flags,
              filter.tds_flags_mask,
              filter.data,
              filter.data_mask,
              filter.meta_data_type,
              filter.meta_data);
          break;
        }
        case ApcfFilterType::AD_TYPE: {
          update_ad_type_filter(apcf_action, filter_index, filter.ad_type, filter.data, filter.data_mask);
          break;
        }
        default:
          LOG_ERROR("Unknown filter type: %d", (uint16_t)filter.filter_type);
          break;
      }
    }
  }

  std::unordered_map<uint8_t, AddressWithType> remove_me_later_map_;

  void update_address_filter(
      ApcfAction action,
      uint8_t filter_index,
      Address address,
      ApcfApplicationAddressType address_type,
      std::array<uint8_t, 16> irk) {
    if (action != ApcfAction::CLEAR) {
      /*
       * The vendor command (APCF Filtering 0x0157) takes Public (0) or Random (1)
       * or Addresses type not applicable (2).
       *
       * Advertising results have four types:
       * ￼    -  Public = 0
       * ￼    -  Random = 1
       * ￼    -  Public ID = 2
       * ￼    -  Random ID = 3
       *
       * e.g. specifying PUBLIC (0) will only return results with a public
       * address. It will ignore resolved addresses, since they return PUBLIC
       * IDENTITY (2). For this, Addresses type not applicable (0x02) must be specified.
       * This should also cover if the RPA is derived from RANDOM STATIC.
       */
      le_scanning_interface_->EnqueueCommand(
          LeAdvFilterBroadcasterAddressBuilder::Create(
              action, filter_index, address, ApcfApplicationAddressType::NOT_APPLICABLE),
          module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
      if (!is_empty_128bit(irk)) {
        // If an entry exists for this filter index, replace data because the filter has been
        // updated.
        auto entry = remove_me_later_map_.find(filter_index);
        // IRK Scanning
        if (entry != remove_me_later_map_.end()) {
          // Don't want to remove for a bonded device
          if (!is_bonded(entry->second.GetAddress())) {
            le_address_manager_->RemoveDeviceFromResolvingList(
                static_cast<PeerAddressType>(entry->second.GetAddressType()), entry->second.GetAddress());
          }
          remove_me_later_map_.erase(filter_index);
        }

        // Now replace it with a new one
        std::array<uint8_t, 16> empty_irk;
        le_address_manager_->AddDeviceToResolvingList(
            static_cast<PeerAddressType>(address_type), address, irk, empty_irk);
        remove_me_later_map_.emplace(filter_index, AddressWithType(address, static_cast<AddressType>(address_type)));
      }
    } else {
      le_scanning_interface_->EnqueueCommand(
          LeAdvFilterClearBroadcasterAddressBuilder::Create(filter_index),
          module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
      auto entry = remove_me_later_map_.find(filter_index);
      if (entry != remove_me_later_map_.end()) {
        // TODO(optedoblivion): If not bonded
        le_address_manager_->RemoveDeviceFromResolvingList(static_cast<PeerAddressType>(address_type), address);
        remove_me_later_map_.erase(filter_index);
      }
    }
  }

  bool is_empty_128bit(const std::array<uint8_t, 16> data) {
    for (int i = 0; i < 16; i++) {
      if (data[i] != (uint8_t)0) {
        return false;
      }
    }
    return true;
  }

  void update_uuid_filter(
      ApcfAction action, uint8_t filter_index, ApcfFilterType filter_type, Uuid uuid, Uuid uuid_mask) {
    std::vector<uint8_t> combined_data = {};
    if (action != ApcfAction::CLEAR) {
      uint8_t uuid_len = uuid.GetShortestRepresentationSize();
      if (uuid_len == Uuid::kNumBytes16) {
        uint16_t data = uuid.As16Bit();
        combined_data.push_back((uint8_t)data);
        combined_data.push_back((uint8_t)(data >> 8));
      } else if (uuid_len == Uuid::kNumBytes32) {
        uint32_t data = uuid.As32Bit();
        combined_data.push_back((uint8_t)data);
        combined_data.push_back((uint8_t)(data >> 8));
        combined_data.push_back((uint8_t)(data >> 16));
        combined_data.push_back((uint8_t)(data >> 24));
      } else if (uuid_len == Uuid::kNumBytes128) {
        auto data = uuid.To128BitLE();
        combined_data.insert(combined_data.end(), data.begin(), data.end());
      } else {
        LOG_ERROR("illegal UUID length: %d", (uint16_t)uuid_len);
        return;
      }

      if (!uuid_mask.IsEmpty()) {
        if (uuid_len == Uuid::kNumBytes16) {
          uint16_t data = uuid_mask.As16Bit();
          combined_data.push_back((uint8_t)data);
          combined_data.push_back((uint8_t)(data >> 8));
        } else if (uuid_len == Uuid::kNumBytes32) {
          uint32_t data = uuid_mask.As32Bit();
          combined_data.push_back((uint8_t)data);
          combined_data.push_back((uint8_t)(data >> 8));
          combined_data.push_back((uint8_t)(data >> 16));
          combined_data.push_back((uint8_t)(data >> 24));
        } else if (uuid_len == Uuid::kNumBytes128) {
          auto data = uuid_mask.To128BitLE();
          combined_data.insert(combined_data.end(), data.begin(), data.end());
        }
      } else {
        std::vector<uint8_t> data(uuid_len, 0xFF);
        combined_data.insert(combined_data.end(), data.begin(), data.end());
      }
    }

    if (filter_type == ApcfFilterType::SERVICE_UUID) {
      le_scanning_interface_->EnqueueCommand(
          LeAdvFilterServiceUuidBuilder::Create(action, filter_index, combined_data),
          module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
    } else {
      le_scanning_interface_->EnqueueCommand(
          LeAdvFilterSolicitationUuidBuilder::Create(action, filter_index, combined_data),
          module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
    }
  }

  void update_local_name_filter(ApcfAction action, uint8_t filter_index, std::vector<uint8_t> name) {
    le_scanning_interface_->EnqueueCommand(
        LeAdvFilterLocalNameBuilder::Create(action, filter_index, name),
        module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
  }

  void update_manufacturer_data_filter(
      ApcfAction action,
      uint8_t filter_index,
      uint16_t company_id,
      uint16_t company_id_mask,
      std::vector<uint8_t> data,
      std::vector<uint8_t> data_mask) {
    if (data.size() != data_mask.size()) {
      LOG_ERROR("manufacturer data mask should have the same length as manufacturer data");
      return;
    }
    std::vector<uint8_t> combined_data = {};
    if (action != ApcfAction::CLEAR) {
      combined_data.push_back((uint8_t)company_id);
      combined_data.push_back((uint8_t)(company_id >> 8));
      if (data.size() != 0) {
        combined_data.insert(combined_data.end(), data.begin(), data.end());
      }
      if (company_id_mask != 0) {
        combined_data.push_back((uint8_t)company_id_mask);
        combined_data.push_back((uint8_t)(company_id_mask >> 8));
      } else {
        combined_data.push_back(0xFF);
        combined_data.push_back(0xFF);
      }
      if (data_mask.size() != 0) {
        combined_data.insert(combined_data.end(), data_mask.begin(), data_mask.end());
      }
    }

    le_scanning_interface_->EnqueueCommand(
        LeAdvFilterManufacturerDataBuilder::Create(action, filter_index, combined_data),
        module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
  }

  void update_service_data_filter(
      ApcfAction action, uint8_t filter_index, std::vector<uint8_t> data, std::vector<uint8_t> data_mask) {
    if (data.size() != data_mask.size()) {
      LOG_ERROR("service data mask should have the same length as service data");
      return;
    }
    std::vector<uint8_t> combined_data = {};
    if (action != ApcfAction::CLEAR && data.size() != 0) {
      combined_data.insert(combined_data.end(), data.begin(), data.end());
      combined_data.insert(combined_data.end(), data_mask.begin(), data_mask.end());
    }

    le_scanning_interface_->EnqueueCommand(
        LeAdvFilterServiceDataBuilder::Create(action, filter_index, combined_data),
        module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
  }

  void update_transport_discovery_data_filter(
      ApcfAction action,
      uint8_t filter_index,
      uint8_t org_id,
      uint8_t tds_flags,
      uint8_t tds_flags_mask,
      std::vector<uint8_t> transport_data,
      std::vector<uint8_t> transport_data_mask,
      ApcfMetaDataType meta_data_type,
      std::vector<uint8_t> meta_data) {
    LocalVersionInformation local_version_information = controller_->GetLocalVersionInformation();

    // In QTI controller, transport discovery data filter are supported by default. Check is added
    // to keep backward compatibility.
    if (!is_transport_discovery_data_filter_supported_ &&
        !(local_version_information.manufacturer_name_ == LMP_COMPID_QTI)) {
      LOG_WARN("transport discovery data filter isn't supported");
      return;
    }

    LOG_INFO(
        "org id: %d, tds_flags: %d, tds_flags_mask = %d,"
        "transport_data size: %zu, transport_data_mask size: %zu"
        "meta_data_type: %u, meta_data size: %zu",
        org_id,
        tds_flags,
        tds_flags_mask,
        transport_data.size(),
        transport_data_mask.size(),
        (uint8_t)meta_data_type,
        meta_data.size());

    // 0x02 Wi-Fi Alliance Neighbor Awareness Networking & meta_data_type is 0x01 for NAN Hash.
    if (org_id == 0x02) {
      // meta data contains WIFI NAN hash, reverse it before sending controller.
      switch (meta_data_type) {
        case ApcfMetaDataType::WIFI_NAN_HASH:
          std::reverse(meta_data.begin(), meta_data.end());
          break;
        default:
          break;
      }
    }

    if (is_transport_discovery_data_filter_supported_) {
      le_scanning_interface_->EnqueueCommand(
          LeAdvFilterTransportDiscoveryDataBuilder::Create(
              action,
              filter_index,
              org_id,
              tds_flags,
              tds_flags_mask,
              transport_data,
              transport_data_mask,
              meta_data_type,
              meta_data),
          module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
    } else {
      // In QTI controller, transport discovery data filter are supported by default.
      // keeping old version for backward compatibility
      std::vector<uint8_t> combined_data = {};
      if (action != ApcfAction::CLEAR) {
        combined_data.push_back((uint8_t)org_id);
        combined_data.push_back((uint8_t)tds_flags);
        combined_data.push_back((uint8_t)tds_flags_mask);
        if (org_id == 0x02 && meta_data_type == ApcfMetaDataType::WIFI_NAN_HASH) {
          // meta data contains WIFI NAN hash
          combined_data.insert(combined_data.end(), meta_data.begin(), meta_data.end());
        }
      }
      le_scanning_interface_->EnqueueCommand(
          LeAdvFilterTransportDiscoveryDataOldBuilder::Create(action, filter_index, combined_data),
          module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
    }
  }

  void update_ad_type_filter(
      ApcfAction action,
      uint8_t filter_index,
      uint8_t ad_type,
      std::vector<uint8_t> data,
      std::vector<uint8_t> data_mask) {
    if (!is_ad_type_filter_supported_) {
      LOG_ERROR("AD type filter isn't supported");
      return;
    }

    if (data.size() != data_mask.size()) {
      LOG_ERROR("ad type mask should have the same length as ad type data");
      return;
    }
    std::vector<uint8_t> combined_data = {};
    if (action != ApcfAction::CLEAR) {
      combined_data.push_back((uint8_t)ad_type);
      combined_data.push_back((uint8_t)(data.size()));
      if (data.size() != 0) {
        combined_data.insert(combined_data.end(), data.begin(), data.end());
        combined_data.insert(combined_data.end(), data_mask.begin(), data_mask.end());
      }
    }

    le_scanning_interface_->EnqueueCommand(
        LeAdvFilterADTypeBuilder::Create(action, filter_index, combined_data),
        module_handler_->BindOnceOn(this, &impl::on_advertising_filter_complete));
  }

  void batch_scan_set_storage_parameter(
      uint8_t batch_scan_full_max,
      uint8_t batch_scan_truncated_max,
      uint8_t batch_scan_notify_threshold,
      ScannerId scanner_id) {
    if (!is_batch_scan_supported_) {
      LOG_WARN("Batch scan is not supported");
      return;
    }
    // scanner id for OnBatchScanThresholdCrossed
    batch_scan_config_.ref_value = scanner_id;

    if (batch_scan_config_.current_state == BatchScanState::ERROR_STATE ||
        batch_scan_config_.current_state == BatchScanState::DISABLED_STATE ||
        batch_scan_config_.current_state == BatchScanState::DISABLE_CALLED) {
      batch_scan_config_.current_state = BatchScanState::ENABLE_CALLED;
      le_scanning_interface_->EnqueueCommand(
          LeBatchScanEnableBuilder::Create(Enable::ENABLED),
          module_handler_->BindOnceOn(this, &impl::on_batch_scan_enable_complete));
    }

    le_scanning_interface_->EnqueueCommand(
        LeBatchScanSetStorageParametersBuilder::Create(
            batch_scan_full_max, batch_scan_truncated_max, batch_scan_notify_threshold),
        module_handler_->BindOnceOn(this, &impl::on_batch_scan_complete));
  }

  void batch_scan_enable(
      BatchScanMode scan_mode,
      uint32_t duty_cycle_scan_window_slots,
      uint32_t duty_cycle_scan_interval_slots,
      BatchScanDiscardRule batch_scan_discard_rule) {
    if (!is_batch_scan_supported_) {
      LOG_WARN("Batch scan is not supported");
      return;
    }

    if (batch_scan_config_.current_state == BatchScanState::ERROR_STATE ||
        batch_scan_config_.current_state == BatchScanState::DISABLED_STATE ||
        batch_scan_config_.current_state == BatchScanState::DISABLE_CALLED) {
      batch_scan_config_.current_state = BatchScanState::ENABLE_CALLED;
      le_scanning_interface_->EnqueueCommand(
          LeBatchScanEnableBuilder::Create(Enable::ENABLED),
          module_handler_->BindOnceOn(this, &impl::on_batch_scan_enable_complete));
    }

    batch_scan_config_.scan_mode = scan_mode;
    batch_scan_config_.scan_interval = duty_cycle_scan_interval_slots;
    batch_scan_config_.scan_window = duty_cycle_scan_window_slots;
    batch_scan_config_.discard_rule = batch_scan_discard_rule;
    /* This command starts batch scanning, if enabled */
    batch_scan_set_scan_parameter(
        scan_mode, duty_cycle_scan_window_slots, duty_cycle_scan_interval_slots, batch_scan_discard_rule);
  }

  void batch_scan_disable() {
    if (!is_batch_scan_supported_) {
      LOG_WARN("Batch scan is not supported");
      return;
    }
    batch_scan_config_.current_state = BatchScanState::DISABLE_CALLED;
    batch_scan_set_scan_parameter(
        BatchScanMode::DISABLE,
        batch_scan_config_.scan_window,
        batch_scan_config_.scan_interval,
        batch_scan_config_.discard_rule);
  }

  void batch_scan_set_scan_parameter(
      BatchScanMode scan_mode,
      uint32_t duty_cycle_scan_window_slots,
      uint32_t duty_cycle_scan_interval_slots,
      BatchScanDiscardRule batch_scan_discard_rule) {
    if (!is_batch_scan_supported_) {
      LOG_WARN("Batch scan is not supported");
      return;
    }
    PeerAddressType own_address_type = PeerAddressType::PUBLIC_DEVICE_OR_IDENTITY_ADDRESS;
    if (own_address_type_ == OwnAddressType::RANDOM_DEVICE_ADDRESS ||
        own_address_type_ == OwnAddressType::RESOLVABLE_OR_RANDOM_ADDRESS) {
      own_address_type = PeerAddressType::RANDOM_DEVICE_OR_IDENTITY_ADDRESS;
    }
    uint8_t truncated_mode_enabled = 0x00;
    uint8_t full_mode_enabled = 0x00;
    if (scan_mode == BatchScanMode::TRUNCATED || scan_mode == BatchScanMode::TRUNCATED_AND_FULL) {
      truncated_mode_enabled = 0x01;
    }
    if (scan_mode == BatchScanMode::FULL || scan_mode == BatchScanMode::TRUNCATED_AND_FULL) {
      full_mode_enabled = 0x01;
    }

    if (scan_mode == BatchScanMode::DISABLE) {
      le_scanning_interface_->EnqueueCommand(
          LeBatchScanSetScanParametersBuilder::Create(
              truncated_mode_enabled,
              full_mode_enabled,
              duty_cycle_scan_window_slots,
              duty_cycle_scan_interval_slots,
              own_address_type,
              batch_scan_discard_rule),
          module_handler_->BindOnceOn(this, &impl::on_batch_scan_disable_complete));
    } else {
      le_scanning_interface_->EnqueueCommand(
          LeBatchScanSetScanParametersBuilder::Create(
              truncated_mode_enabled,
              full_mode_enabled,
              duty_cycle_scan_window_slots,
              duty_cycle_scan_interval_slots,
              own_address_type,
              batch_scan_discard_rule),
          module_handler_->BindOnceOn(this, &impl::on_batch_scan_complete));
    }
  }

  void batch_scan_read_results(ScannerId scanner_id, uint16_t total_num_of_records, BatchScanMode scan_mode) {
    if (!is_batch_scan_supported_) {
      LOG_WARN("Batch scan is not supported");
      int status = static_cast<int>(ErrorCode::UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE);
      scanning_callbacks_->OnBatchScanReports(scanner_id, status, 0, 0, {});
      return;
    }

    if (scan_mode != BatchScanMode::FULL && scan_mode != BatchScanMode::TRUNCATED) {
      LOG_WARN("Invalid scan mode %d", (uint16_t)scan_mode);
      int status = static_cast<int>(ErrorCode::INVALID_HCI_COMMAND_PARAMETERS);
      scanning_callbacks_->OnBatchScanReports(scanner_id, status, 0, 0, {});
      return;
    }

    if (batch_scan_result_cache_.find(scanner_id) == batch_scan_result_cache_.end()) {
      std::vector<uint8_t> empty_data = {};
      batch_scan_result_cache_.emplace(scanner_id, empty_data);
    }

    le_scanning_interface_->EnqueueCommand(
        LeBatchScanReadResultParametersBuilder::Create(static_cast<BatchScanDataRead>(scan_mode)),
        module_handler_->BindOnceOn(this, &impl::on_batch_scan_read_result_complete, scanner_id, total_num_of_records));
  }

  void start_sync(
      uint8_t sid, const AddressWithType& address_with_type, uint16_t skip, uint16_t timeout, int request_id) {
    if (!is_periodic_advertising_sync_transfer_sender_supported_) {
      LOG_WARN("PAST sender not supported on this device");
      int status = static_cast<int>(ErrorCode::UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE);
      scanning_callbacks_->OnPeriodicSyncStarted(request_id, status, -1, sid, address_with_type, 0, 0);
      return;
    }
    PeriodicSyncStates request{
        .request_id = request_id,
        .advertiser_sid = sid,
        .address_with_type = address_with_type,
        .sync_handle = 0,
        .sync_state = PeriodicSyncState::PERIODIC_SYNC_STATE_IDLE,
    };
    periodic_sync_manager_.StartSync(request, skip, timeout);
  }

  void stop_sync(uint16_t handle) {
    if (!is_periodic_advertising_sync_transfer_sender_supported_) {
      LOG_WARN("PAST sender not supported on this device");
      return;
    }
    periodic_sync_manager_.StopSync(handle);
  }

  void cancel_create_sync(uint8_t sid, const Address& address) {
    if (!is_periodic_advertising_sync_transfer_sender_supported_) {
      LOG_WARN("PAST sender not supported on this device");
      return;
    }
    periodic_sync_manager_.CancelCreateSync(sid, address);
  }

  void transfer_sync(
      const Address& address,
      uint16_t connection_handle,
      uint16_t service_data,
      uint16_t sync_handle,
      int pa_source) {
    if (!is_periodic_advertising_sync_transfer_sender_supported_) {
      LOG_WARN("PAST sender not supported on this device");
      int status = static_cast<int>(ErrorCode::UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE);
      scanning_callbacks_->OnPeriodicSyncTransferred(pa_source, status, address);
      return;
    }
    if (connection_handle == 0xFFFF) {
      LOG_ERROR("[PAST]: Invalid connection handle or no LE ACL link");
      int status = static_cast<int>(ErrorCode::UNKNOWN_CONNECTION);
      scanning_callbacks_->OnPeriodicSyncTransferred(pa_source, status, address);
      return;
    }
    periodic_sync_manager_.TransferSync(address, service_data, sync_handle, pa_source, connection_handle);
  }

  void transfer_set_info(
      const Address& address,
      uint16_t connection_handle,
      uint16_t service_data,
      uint8_t adv_handle,
      int pa_source) {
    if (!is_periodic_advertising_sync_transfer_sender_supported_) {
      LOG_WARN("PAST sender not supported on this device");
      int status = static_cast<int>(ErrorCode::UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE);
      scanning_callbacks_->OnPeriodicSyncTransferred(pa_source, status, address);
      return;
    }
    if (connection_handle == 0xFFFF) {
      LOG_ERROR("[PAST]:Invalid connection handle or no LE ACL link");
      int status = static_cast<int>(ErrorCode::UNKNOWN_CONNECTION);
      scanning_callbacks_->OnPeriodicSyncTransferred(pa_source, status, address);
      return;
    }
    periodic_sync_manager_.SyncSetInfo(address, service_data, adv_handle, pa_source, connection_handle);
  }

  void sync_tx_parameters(const Address& address, uint8_t mode, uint16_t skip, uint16_t timeout, int reg_id) {
    if (!is_periodic_advertising_sync_transfer_sender_supported_) {
      LOG_WARN("PAST sender not supported on this device");
      int status = static_cast<int>(ErrorCode::UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE);
      AddressWithType address_with_type(address, AddressType::RANDOM_DEVICE_ADDRESS);
      scanning_callbacks_->OnPeriodicSyncStarted(reg_id, status, -1, -1, address_with_type, 0, 0);
      return;
    }
    periodic_sync_manager_.SyncTxParameters(address, mode, skip, timeout, reg_id);
  }

  void track_advertiser(uint8_t filter_index, ScannerId scanner_id) {
    if (total_num_of_advt_tracked_ <= 0) {
      LOG_WARN("advertisement tracking is not supported");
      AdvertisingFilterOnFoundOnLostInfo on_found_on_lost_info = {};
      on_found_on_lost_info.scanner_id = scanner_id;
      on_found_on_lost_info.advertiser_info_present = AdvtInfoPresent::NO_ADVT_INFO_PRESENT;
      scanning_callbacks_->OnTrackAdvFoundLost(on_found_on_lost_info);
      return;
    } else if (tracker_id_map_.size() >= total_num_of_advt_tracked_) {
      AdvertisingFilterOnFoundOnLostInfo on_found_on_lost_info = {};
      on_found_on_lost_info.scanner_id = scanner_id;
      on_found_on_lost_info.advertiser_info_present = AdvtInfoPresent::NO_ADVT_INFO_PRESENT;
      scanning_callbacks_->OnTrackAdvFoundLost(on_found_on_lost_info);
      return;
    }
    LOG_INFO("track_advertiser scanner_id %d, filter_index %d", (uint16_t)scanner_id, (uint16_t)filter_index);
    tracker_id_map_[filter_index] = scanner_id;
  }

  void register_scanning_callback(ScanningCallback* scanning_callbacks) {
    scanning_callbacks_ = scanning_callbacks;
    periodic_sync_manager_.SetScanningCallback(scanning_callbacks_);
  }

  bool is_ad_type_filter_supported() {
    return is_ad_type_filter_supported_;
  }

  void on_set_scan_parameter_complete(CommandCompleteView view) {
    switch (view.GetCommandOpCode()) {
      case (OpCode::LE_SET_SCAN_PARAMETERS): {
        auto status_view = LeSetScanParametersCompleteView::Create(view);
        ASSERT(status_view.IsValid());
        if (status_view.GetStatus() != ErrorCode::SUCCESS) {
          LOG_INFO(
              "Receive set scan parameter complete with error code %s", ErrorCodeText(status_view.GetStatus()).c_str());
        }
      } break;
      case (OpCode::LE_EXTENDED_SCAN_PARAMS): {
        auto status_view = LeExtendedScanParamsCompleteView::Create(view);
        ASSERT(status_view.IsValid());
        if (status_view.GetStatus() != ErrorCode::SUCCESS) {
          LOG_INFO(
              "Receive extended scan parameter complete with error code %s",
              ErrorCodeText(status_view.GetStatus()).c_str());
        }
      } break;
      case (OpCode::LE_SET_EXTENDED_SCAN_PARAMETERS): {
        auto status_view = LeSetExtendedScanParametersCompleteView::Create(view);
        ASSERT(status_view.IsValid());
        if (status_view.GetStatus() != ErrorCode::SUCCESS) {
          LOG_INFO(
              "Receive set extended scan parameter complete with error code %s",
              ErrorCodeText(status_view.GetStatus()).c_str());
        }
      } break;
      default:
        LOG_ALWAYS_FATAL("Unhandled event %s", OpCodeText(view.GetCommandOpCode()).c_str());
    }
  }

  void on_advertising_filter_complete(CommandCompleteView view) {
    ASSERT(view.IsValid());
    auto status_view = LeAdvFilterCompleteView::Create(view);
    ASSERT(status_view.IsValid());
    if (status_view.GetStatus() != ErrorCode::SUCCESS) {
      LOG_INFO(
          "Got a Command complete %s, status %s",
          OpCodeText(view.GetCommandOpCode()).c_str(),
          ErrorCodeText(status_view.GetStatus()).c_str());
    }

    ApcfOpcode apcf_opcode = status_view.GetApcfOpcode();
    switch (apcf_opcode) {
      case ApcfOpcode::ENABLE: {
        auto complete_view = LeAdvFilterEnableCompleteView::Create(status_view);
        ASSERT(complete_view.IsValid());
        scanning_callbacks_->OnFilterEnable(complete_view.GetApcfEnable(), (uint8_t)complete_view.GetStatus());
      } break;
      case ApcfOpcode::SET_FILTERING_PARAMETERS: {
        auto complete_view = LeAdvFilterSetFilteringParametersCompleteView::Create(status_view);
        ASSERT(complete_view.IsValid());
        scanning_callbacks_->OnFilterParamSetup(
            complete_view.GetApcfAvailableSpaces(), complete_view.GetApcfAction(), (uint8_t)complete_view.GetStatus());
      } break;
      case ApcfOpcode::BROADCASTER_ADDRESS: {
        auto complete_view = LeAdvFilterBroadcasterAddressCompleteView::Create(status_view);
        ASSERT(complete_view.IsValid());
        scanning_callbacks_->OnFilterConfigCallback(
            ApcfFilterType::BROADCASTER_ADDRESS,
            complete_view.GetApcfAvailableSpaces(),
            complete_view.GetApcfAction(),
            (uint8_t)complete_view.GetStatus());
      } break;
      case ApcfOpcode::SERVICE_UUID: {
        auto complete_view = LeAdvFilterServiceUuidCompleteView::Create(status_view);
        ASSERT(complete_view.IsValid());
        scanning_callbacks_->OnFilterConfigCallback(
            ApcfFilterType::SERVICE_UUID,
            complete_view.GetApcfAvailableSpaces(),
            complete_view.GetApcfAction(),
            (uint8_t)complete_view.GetStatus());
      } break;
      case ApcfOpcode::SERVICE_SOLICITATION_UUID: {
        auto complete_view = LeAdvFilterSolicitationUuidCompleteView::Create(status_view);
        ASSERT(complete_view.IsValid());
        scanning_callbacks_->OnFilterConfigCallback(
            ApcfFilterType::SERVICE_SOLICITATION_UUID,
            complete_view.GetApcfAvailableSpaces(),
            complete_view.GetApcfAction(),
            (uint8_t)complete_view.GetStatus());
      } break;
      case ApcfOpcode::LOCAL_NAME: {
        auto complete_view = LeAdvFilterLocalNameCompleteView::Create(status_view);
        ASSERT(complete_view.IsValid());
        scanning_callbacks_->OnFilterConfigCallback(
            ApcfFilterType::LOCAL_NAME,
            complete_view.GetApcfAvailableSpaces(),
            complete_view.GetApcfAction(),
            (uint8_t)complete_view.GetStatus());
      } break;
      case ApcfOpcode::MANUFACTURER_DATA: {
        auto complete_view = LeAdvFilterManufacturerDataCompleteView::Create(status_view);
        ASSERT(complete_view.IsValid());
        scanning_callbacks_->OnFilterConfigCallback(
            ApcfFilterType::MANUFACTURER_DATA,
            complete_view.GetApcfAvailableSpaces(),
            complete_view.GetApcfAction(),
            (uint8_t)complete_view.GetStatus());
      } break;
      case ApcfOpcode::SERVICE_DATA: {
        auto complete_view = LeAdvFilterServiceDataCompleteView::Create(status_view);
        ASSERT(complete_view.IsValid());
        scanning_callbacks_->OnFilterConfigCallback(
            ApcfFilterType::SERVICE_DATA,
            complete_view.GetApcfAvailableSpaces(),
            complete_view.GetApcfAction(),
            (uint8_t)complete_view.GetStatus());
      } break;
      case ApcfOpcode::TRANSPORT_DISCOVERY_DATA: {
        auto complete_view = LeAdvFilterTransportDiscoveryDataCompleteView::Create(status_view);
        ASSERT(complete_view.IsValid());
        scanning_callbacks_->OnFilterConfigCallback(
            ApcfFilterType::TRANSPORT_DISCOVERY_DATA,
            complete_view.GetApcfAvailableSpaces(),
            complete_view.GetApcfAction(),
            (uint8_t)complete_view.GetStatus());
      } break;
      case ApcfOpcode::AD_TYPE: {
        auto complete_view = LeAdvFilterADTypeCompleteView::Create(status_view);
        ASSERT(complete_view.IsValid());
        scanning_callbacks_->OnFilterConfigCallback(
            ApcfFilterType::AD_TYPE,
            complete_view.GetApcfAvailableSpaces(),
            complete_view.GetApcfAction(),
            (uint8_t)complete_view.GetStatus());
      } break;
      default:
        LOG_WARN("Unexpected event type %s", OpCodeText(view.GetCommandOpCode()).c_str());
    }
  }

  void on_apcf_read_extended_features_complete(CommandCompleteView view) {
    ASSERT(view.IsValid());
    auto status_view = LeAdvFilterCompleteView::Create(view);
    if (!status_view.IsValid()) {
      LOG_WARN("Can not get valid LeAdvFilterCompleteView, return");
      return;
    }
    if (status_view.GetStatus() != ErrorCode::SUCCESS) {
      LOG_WARN(
          "Got a Command complete %s, status %s",
          OpCodeText(view.GetCommandOpCode()).c_str(),
          ErrorCodeText(status_view.GetStatus()).c_str());
      return;
    }
    auto complete_view = LeAdvFilterReadExtendedFeaturesCompleteView::Create(status_view);
    ASSERT(complete_view.IsValid());
    is_transport_discovery_data_filter_supported_ =
        complete_view.GetTransportDiscoveryDataFilter() == 1;
    is_ad_type_filter_supported_ = complete_view.GetAdTypeFilter() == 1;
    LOG_INFO(
        "set is_ad_type_filter_supported_ to %d & "
        "is_transport_discovery_data_filter_supported_ to %d",
        is_ad_type_filter_supported_,
        is_transport_discovery_data_filter_supported_);
  }

  void on_batch_scan_complete(CommandCompleteView view) {
    ASSERT(view.IsValid());
    auto status_view = LeBatchScanCompleteView::Create(view);
    ASSERT(status_view.IsValid());
    if (status_view.GetStatus() != ErrorCode::SUCCESS) {
      LOG_INFO(
          "Got a Command complete %s, status %s, batch_scan_opcode %s",
          OpCodeText(view.GetCommandOpCode()).c_str(),
          ErrorCodeText(status_view.GetStatus()).c_str(),
          BatchScanOpcodeText(status_view.GetBatchScanOpcode()).c_str());
    }
  }

  void on_batch_scan_enable_complete(CommandCompleteView view) {
    ASSERT(view.IsValid());
    auto status_view = LeBatchScanCompleteView::Create(view);
    ASSERT(status_view.IsValid());
    auto complete_view = LeBatchScanEnableCompleteView::Create(status_view);
    ASSERT(complete_view.IsValid());
    if (status_view.GetStatus() != ErrorCode::SUCCESS) {
      LOG_INFO("Got batch scan enable complete, status %s", ErrorCodeText(status_view.GetStatus()).c_str());
      batch_scan_config_.current_state = BatchScanState::ERROR_STATE;
    } else {
      batch_scan_config_.current_state = BatchScanState::ENABLED_STATE;
    }
  }

  void on_batch_scan_disable_complete(CommandCompleteView view) {
    ASSERT(view.IsValid());
    auto status_view = LeBatchScanCompleteView::Create(view);
    ASSERT(status_view.IsValid());
    auto complete_view = LeBatchScanSetScanParametersCompleteView::Create(status_view);
    ASSERT(complete_view.IsValid());
    ASSERT(status_view.GetStatus() == ErrorCode::SUCCESS);
    batch_scan_config_.current_state = BatchScanState::DISABLED_STATE;
  }

  void on_batch_scan_read_result_complete(
      ScannerId scanner_id, uint16_t total_num_of_records, CommandCompleteView view) {
    ASSERT(view.IsValid());
    auto status_view = LeBatchScanCompleteView::Create(view);
    ASSERT(status_view.IsValid());
    auto complete_view = LeBatchScanReadResultParametersCompleteRawView::Create(status_view);
    ASSERT(complete_view.IsValid());
    if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      LOG_INFO("Got batch scan read result complete, status %s", ErrorCodeText(status_view.GetStatus()).c_str());
    }
    uint8_t num_of_records = complete_view.GetNumOfRecords();
    auto report_format = complete_view.GetBatchScanDataRead();
    if (num_of_records == 0) {
      scanning_callbacks_->OnBatchScanReports(
          scanner_id, 0x00, (int)report_format, total_num_of_records, batch_scan_result_cache_[scanner_id]);
      batch_scan_result_cache_.erase(scanner_id);
    } else {
      auto raw_data = complete_view.GetRawData();
      batch_scan_result_cache_[scanner_id].insert(
          batch_scan_result_cache_[scanner_id].end(), raw_data.begin(), raw_data.end());
      total_num_of_records += num_of_records;
      batch_scan_read_results(scanner_id, total_num_of_records, static_cast<BatchScanMode>(report_format));
    }
  }

  void on_storage_threshold_breach(VendorSpecificEventView /* event */) {
    if (batch_scan_config_.ref_value == kInvalidScannerId) {
      LOG_WARN("storage threshold was not set !!");
      return;
    }
    scanning_callbacks_->OnBatchScanThresholdCrossed(static_cast<int>(batch_scan_config_.ref_value));
  }

  void on_advertisement_tracking(VendorSpecificEventView event) {
    auto view = LEAdvertisementTrackingEventView::Create(event);
    ASSERT(view.IsValid());
    uint8_t filter_index = view.GetApcfFilterIndex();
    if (tracker_id_map_.find(filter_index) == tracker_id_map_.end()) {
      LOG_WARN("Advertisement track for filter_index %d is not register", (uint16_t)filter_index);
      return;
    }
    AdvertisingFilterOnFoundOnLostInfo on_found_on_lost_info = {};
    on_found_on_lost_info.scanner_id = tracker_id_map_[filter_index];
    on_found_on_lost_info.filter_index = filter_index;
    on_found_on_lost_info.advertiser_state = view.GetAdvertiserState();
    on_found_on_lost_info.advertiser_address = view.GetAdvertiserAddress();
    on_found_on_lost_info.advertiser_address_type = view.GetAdvertiserAddressType();
    on_found_on_lost_info.advertiser_info_present = view.GetAdvtInfoPresent();
    /* Extract the adv info details */
    if (on_found_on_lost_info.advertiser_info_present == AdvtInfoPresent::ADVT_INFO_PRESENT) {
      auto info_view = LEAdvertisementTrackingWithInfoEventView::Create(view);
      ASSERT(info_view.IsValid());
      on_found_on_lost_info.tx_power = info_view.GetTxPower();
      on_found_on_lost_info.rssi = info_view.GetRssi();
      on_found_on_lost_info.time_stamp = info_view.GetTimestamp();
      auto adv_data = info_view.GetAdvPacket();
      on_found_on_lost_info.adv_packet.reserve(adv_data.size());
      on_found_on_lost_info.adv_packet.insert(on_found_on_lost_info.adv_packet.end(), adv_data.begin(), adv_data.end());
      auto scan_rsp_data = info_view.GetScanResponse();
      on_found_on_lost_info.scan_response.reserve(scan_rsp_data.size());
      on_found_on_lost_info.scan_response.insert(
          on_found_on_lost_info.scan_response.end(), scan_rsp_data.begin(), scan_rsp_data.end());
    }
    scanning_callbacks_->OnTrackAdvFoundLost(on_found_on_lost_info);
  }

  void OnPause() override {
    if (!address_manager_registered_) {
      LOG_WARN("Unregistered!");
      return;
    }
    paused_ = true;
    scan_on_resume_ = is_scanning_;
    stop_scan();
    ack_pause();
  }

  void ack_pause() {
    le_address_manager_->AckPause(this);
  }

  void OnResume() override {
    if (!address_manager_registered_) {
      LOG_WARN("Unregistered!");
      return;
    }
    paused_ = false;
    if (scan_on_resume_ == true) {
      scan_on_resume_ = false;
      start_scan();
    }
    le_address_manager_->AckResume(this);
  }

  ScanApiType api_type_;

  Module* module_;
  os::Handler* module_handler_;
  HciLayer* hci_layer_;
  Controller* controller_;
  AclManager* acl_manager_;
  VendorSpecificEventManager* vendor_specific_event_manager_;
  storage::StorageModule* storage_module_;
  LeScanningInterface* le_scanning_interface_;
  LeAddressManager* le_address_manager_;
  bool address_manager_registered_ = false;
  NullScanningCallback null_scanning_callback_;
  ScanningCallback* scanning_callbacks_ = &null_scanning_callback_;
  PeriodicSyncManager periodic_sync_manager_{&null_scanning_callback_};
  std::vector<Scanner> scanners_;
  bool is_scanning_ = false;
  bool scan_on_resume_ = false;
  bool paused_ = false;
  LeScanningReassembler scanning_reassembler_;
  bool is_filter_supported_ = false;
  bool is_ad_type_filter_supported_ = false;
  bool is_batch_scan_supported_ = false;
  bool is_periodic_advertising_sync_transfer_sender_supported_ = false;
  bool is_transport_discovery_data_filter_supported_ = false;

  LeScanType le_scan_type_ = LeScanType::ACTIVE;
  uint32_t interval_ms_{1000};
  uint16_t window_ms_{1000};
  OwnAddressType own_address_type_{OwnAddressType::PUBLIC_DEVICE_ADDRESS};
  LeScanningFilterPolicy filter_policy_{LeScanningFilterPolicy::ACCEPT_ALL};
  BatchScanConfig batch_scan_config_;
  std::map<ScannerId, std::vector<uint8_t>> batch_scan_result_cache_;
  std::unordered_map<uint8_t, ScannerId> tracker_id_map_;
  uint16_t total_num_of_advt_tracked_ = 0x00;
  int8_t le_rx_path_loss_comp_ = 0;
};

LeScanningManager::LeScanningManager() {
  pimpl_ = std::make_unique<impl>(this);
}

void LeScanningManager::ListDependencies(ModuleList* list) const {
  list->add<HciLayer>();
  list->add<VendorSpecificEventManager>();
  list->add<Controller>();
  list->add<AclManager>();
  list->add<storage::StorageModule>();
}

void LeScanningManager::Start() {
  pimpl_->start(
      GetHandler(),
      GetDependency<HciLayer>(),
      GetDependency<Controller>(),
      GetDependency<AclManager>(),
      GetDependency<VendorSpecificEventManager>(),
      GetDependency<storage::StorageModule>());
}

void LeScanningManager::Stop() {
  pimpl_->stop();
  pimpl_.reset();
}

std::string LeScanningManager::ToString() const {
  return "Le Scanning Manager";
}

void LeScanningManager::RegisterScanner(Uuid app_uuid) {
  CallOn(pimpl_.get(), &impl::register_scanner, app_uuid);
}

void LeScanningManager::Unregister(ScannerId scanner_id) {
  CallOn(pimpl_.get(), &impl::unregister_scanner, scanner_id);
}

void LeScanningManager::Scan(bool start) {
  CallOn(pimpl_.get(), &impl::scan, start);
}

void LeScanningManager::SetScanParameters(
    ScannerId scanner_id, LeScanType scan_type, uint16_t scan_interval, uint16_t scan_window) {
  CallOn(pimpl_.get(), &impl::set_scan_parameters, scanner_id, scan_type, scan_interval, scan_window);
}

void LeScanningManager::SetScanFilterPolicy(LeScanningFilterPolicy filter_policy) {
  CallOn(pimpl_.get(), &impl::set_scan_filter_policy, filter_policy);
}

void LeScanningManager::ScanFilterEnable(bool enable) {
  CallOn(pimpl_.get(), &impl::scan_filter_enable, enable);
}

void LeScanningManager::ScanFilterParameterSetup(
    ApcfAction action, uint8_t filter_index, AdvertisingFilterParameter advertising_filter_parameter) {
  CallOn(pimpl_.get(), &impl::scan_filter_parameter_setup, action, filter_index, advertising_filter_parameter);
}

void LeScanningManager::ScanFilterAdd(
    uint8_t filter_index, std::vector<AdvertisingPacketContentFilterCommand> filters) {
  CallOn(pimpl_.get(), &impl::scan_filter_add, filter_index, filters);
}

void LeScanningManager::BatchScanConifgStorage(
    uint8_t batch_scan_full_max,
    uint8_t batch_scan_truncated_max,
    uint8_t batch_scan_notify_threshold,
    ScannerId scanner_id) {
  CallOn(
      pimpl_.get(),
      &impl::batch_scan_set_storage_parameter,
      batch_scan_full_max,
      batch_scan_truncated_max,
      batch_scan_notify_threshold,
      scanner_id);
}

void LeScanningManager::BatchScanEnable(
    BatchScanMode scan_mode,
    uint32_t duty_cycle_scan_window_slots,
    uint32_t duty_cycle_scan_interval_slots,
    BatchScanDiscardRule batch_scan_discard_rule) {
  CallOn(
      pimpl_.get(),
      &impl::batch_scan_enable,
      scan_mode,
      duty_cycle_scan_window_slots,
      duty_cycle_scan_interval_slots,
      batch_scan_discard_rule);
}

void LeScanningManager::BatchScanDisable() {
  CallOn(pimpl_.get(), &impl::batch_scan_disable);
}

void LeScanningManager::BatchScanReadReport(ScannerId scanner_id, BatchScanMode scan_mode) {
  CallOn(pimpl_.get(), &impl::batch_scan_read_results, scanner_id, 0, scan_mode);
}

void LeScanningManager::StartSync(
    uint8_t sid, const AddressWithType& address_with_type, uint16_t skip, uint16_t timeout, int reg_id) {
  CallOn(pimpl_.get(), &impl::start_sync, sid, address_with_type, skip, timeout, reg_id);
}

void LeScanningManager::StopSync(uint16_t handle) {
  CallOn(pimpl_.get(), &impl::stop_sync, handle);
}

void LeScanningManager::CancelCreateSync(uint8_t sid, const Address& address) {
  CallOn(pimpl_.get(), &impl::cancel_create_sync, sid, address);
}

void LeScanningManager::TransferSync(
    const Address& address,
    uint16_t handle,
    uint16_t service_data,
    uint16_t sync_handle,
    int pa_source) {
  CallOn(pimpl_.get(), &impl::transfer_sync, address, handle, service_data, sync_handle, pa_source);
}

void LeScanningManager::TransferSetInfo(
    const Address& address,
    uint16_t handle,
    uint16_t service_data,
    uint8_t adv_handle,
    int pa_source) {
  CallOn(
      pimpl_.get(), &impl::transfer_set_info, address, handle, service_data, adv_handle, pa_source);
}

void LeScanningManager::SyncTxParameters(
    const Address& address, uint8_t mode, uint16_t skip, uint16_t timeout, int reg_id) {
  CallOn(pimpl_.get(), &impl::sync_tx_parameters, address, mode, skip, timeout, reg_id);
}

void LeScanningManager::TrackAdvertiser(uint8_t filter_index, ScannerId scanner_id) {
  CallOn(pimpl_.get(), &impl::track_advertiser, filter_index, scanner_id);
}

void LeScanningManager::RegisterScanningCallback(ScanningCallback* scanning_callback) {
  CallOn(pimpl_.get(), &impl::register_scanning_callback, scanning_callback);
}

bool LeScanningManager::IsAdTypeFilterSupported() const {
  return pimpl_->is_ad_type_filter_supported();
}

}  // namespace hci
}  // namespace bluetooth
