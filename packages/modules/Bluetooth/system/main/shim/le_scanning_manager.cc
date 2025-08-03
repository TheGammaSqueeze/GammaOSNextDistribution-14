/*
 * Copyright 2020 The Android Open Source Project
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

#define LOG_TAG "bt_shim_scanner"

#include "le_scanning_manager.h"

#include <base/functional/bind.h>
#include <base/logging.h>
#include <base/threading/thread.h>
#include <hardware/bluetooth.h>
#include <stdio.h>

#include "advertise_data_parser.h"
#include "btif/include/btif_common.h"
#include "hci/address.h"
#include "hci/le_scanning_manager.h"
#include "hci/msft.h"
#include "include/hardware/ble_scanner.h"
#include "main/shim/ble_scanner_interface_impl.h"
#include "main/shim/entry.h"
#include "main/shim/helpers.h"
#include "main/shim/le_scanning_manager.h"
#include "main/shim/shim.h"
#include "os/log.h"
#include "stack/btm/btm_int_types.h"
#include "stack/include/bt_dev_class.h"
#include "stack/include/btm_log_history.h"
#include "storage/device.h"
#include "storage/le_device.h"
#include "storage/storage_module.h"
#include "types/ble_address_with_type.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using bluetooth::ToGdAddress;
using bluetooth::ToRawAddress;

extern tBTM_CB btm_cb;

namespace {
constexpr char kBtmLogTag[] = "SCAN";
constexpr uint16_t kAllowServiceDataFilter = 0x0040;
// Bit 8 for enable AD Type Check
constexpr uint16_t kAllowADTypeFilter = 0x100;
constexpr uint8_t kFilterLogicOr = 0x00;
constexpr uint8_t kFilterLogicAnd = 0x01;
constexpr uint8_t kLowestRssiValue = 129;
constexpr uint16_t kAllowAllFilter = 0x00;
constexpr uint16_t kListLogicOr = 0x01;

class DefaultScanningCallback : public ::ScanningCallbacks {
  void OnScannerRegistered(const bluetooth::Uuid /* app_uuid */,
                           uint8_t /* scanner_id */,
                           uint8_t /* status */) override {
    LogUnused();
  }
  void OnSetScannerParameterComplete(uint8_t /* scanner_id */,
                                     uint8_t /* status */) override {
    LogUnused();
  }
  void OnScanResult(uint16_t /* event_type */, uint8_t /* address_type */,
                    RawAddress /* bda */, uint8_t /* primary_phy */,
                    uint8_t /* secondary_phy */, uint8_t /* advertising_sid */,
                    int8_t /* tx_power */, int8_t /* rssi */,
                    uint16_t /* periodic_advertising_interval */,
                    std::vector<uint8_t> /* advertising_data */) override {
    LogUnused();
  }
  void OnTrackAdvFoundLost(
      AdvertisingTrackInfo /* advertising_track_info */) override {
    LogUnused();
  }
  void OnBatchScanReports(int /* client_if */, int /* status */,
                          int /* report_format */, int /* num_records */,
                          std::vector<uint8_t> /* data */) override {
    LogUnused();
  }
  void OnBatchScanThresholdCrossed(int /* client_if */) override {
    LogUnused();
  }
  void OnPeriodicSyncStarted(int /* reg_id */, uint8_t /* status */,
                             uint16_t /* sync_handle */,
                             uint8_t /* advertising_sid */,
                             uint8_t /* address_type */,
                             RawAddress /* address */, uint8_t /* phy */,
                             uint16_t /* interval */) override {
    LogUnused();
  };
  void OnPeriodicSyncReport(uint16_t /* sync_handle */, int8_t /* tx_power */,
                            int8_t /* rssi */, uint8_t /* status */,
                            std::vector<uint8_t> /* data */) override {
    LogUnused();
  };
  void OnPeriodicSyncLost(uint16_t /* sync_handle */) override { LogUnused(); };
  void OnPeriodicSyncTransferred(int /* pa_source */, uint8_t /* status */,
                                 RawAddress /* address */) override {
    LogUnused();
  };

  void OnBigInfoReport(uint16_t /* sync_handle */,
                       bool /* encrypted */) override {
    LogUnused();
  };

 private:
  static void LogUnused() {
    LOG_WARN("BLE Scanning callbacks have not been registered");
  }
} default_scanning_callback_;

}  // namespace

::ScanningCallbacks* bluetooth::shim::default_scanning_callback =
    static_cast<::ScanningCallbacks*>(&default_scanning_callback_);
extern ::ScanningCallbacks* bluetooth::shim::default_scanning_callback;

extern tBTM_CB btm_cb;

void btm_ble_process_adv_pkt_cont_for_inquiry(
    uint16_t event_type, tBLE_ADDR_TYPE address_type,
    const RawAddress& raw_address, uint8_t primary_phy, uint8_t secondary_phy,
    uint8_t advertising_sid, int8_t tx_power, int8_t rssi,
    uint16_t periodic_adv_int, std::vector<uint8_t> advertising_data);

extern void btif_dm_update_ble_remote_properties(const RawAddress& bd_addr,
                                                 BD_NAME bd_name,
                                                 DEV_CLASS dev_class,
                                                 tBT_DEVICE_TYPE dev_type);

void btm_ble_process_adv_addr(RawAddress& raw_address,
                              tBLE_ADDR_TYPE* address_type);

extern DEV_CLASS btm_ble_get_appearance_as_cod(
    std::vector<uint8_t> const& data);

using bluetooth::shim::BleScannerInterfaceImpl;

void BleScannerInterfaceImpl::Init() {
  LOG_INFO("init BleScannerInterfaceImpl");
  bluetooth::shim::GetScanning()->RegisterScanningCallback(this);

  if (bluetooth::shim::GetMsftExtensionManager()) {
    bluetooth::shim::GetMsftExtensionManager()->SetScanningCallback(this);
  }
}

/** Registers a scanner with the stack */
void BleScannerInterfaceImpl::RegisterScanner(const bluetooth::Uuid& uuid,
                                              RegisterCallback) {
  LOG(INFO) << __func__ << " in shim layer";
  auto app_uuid = bluetooth::hci::Uuid::From128BitBE(uuid.To128BitBE());
  bluetooth::shim::GetScanning()->RegisterScanner(app_uuid);
}

/** Unregister a scanner from the stack */
void BleScannerInterfaceImpl::Unregister(int scanner_id) {
  LOG(INFO) << __func__ << " in shim layer, scanner_id:" << scanner_id;
  bluetooth::shim::GetScanning()->Unregister(scanner_id);
}

  /** Start or stop LE device scanning */
void BleScannerInterfaceImpl::Scan(bool start) {
  LOG(INFO) << __func__ << " in shim layer " <<  ((start) ? "started" : "stopped");
  bluetooth::shim::GetScanning()->Scan(start);
  if (start && !btm_cb.ble_ctr_cb.is_ble_observe_active()) {
    btm_cb.neighbor.le_scan = {
        .start_time_ms = timestamper_in_milliseconds.GetTimestamp(),
        .results = 0,
    };
    BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Le scan started");
    btm_cb.ble_ctr_cb.set_ble_observe_active();
  } else if (!start && btm_cb.ble_ctr_cb.is_ble_observe_active()) {
    // stopped
    const unsigned long long duration_timestamp =
        timestamper_in_milliseconds.GetTimestamp() -
        btm_cb.neighbor.le_scan.start_time_ms;
    BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Le scan stopped",
                   base::StringPrintf("duration_s:%6.3f results:%-3lu",
                                      (double)duration_timestamp / 1000.0,
                                      btm_cb.neighbor.le_scan.results));
    btm_cb.ble_ctr_cb.reset_ble_observe();
    btm_cb.neighbor.le_scan = {};
  } else {
    LOG_WARN("Invalid state: start:%d, current scan state: %d", start,
             btm_cb.ble_ctr_cb.is_ble_observe_active());
    return;
  }

  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(&BleScannerInterfaceImpl::AddressCache::init,
                                  base::Unretained(&address_cache_)));
}

  /** Setup scan filter params */
void BleScannerInterfaceImpl::ScanFilterParamSetup(
    uint8_t client_if, uint8_t action, uint8_t filter_index,
    std::unique_ptr<btgatt_filt_param_setup_t> filt_param,
    FilterParamSetupCallback cb) {
  LOG(INFO) << __func__ << " in shim layer";

  auto apcf_action = static_cast<bluetooth::hci::ApcfAction>(action);
  bluetooth::hci::AdvertisingFilterParameter advertising_filter_parameter;

  if (filt_param != nullptr) {
    if (filt_param && filt_param->dely_mode == 1 &&
        apcf_action == hci::ApcfAction::ADD) {
      bluetooth::shim::GetScanning()->TrackAdvertiser(filter_index, client_if);
    }
    advertising_filter_parameter.feature_selection = filt_param->feat_seln;
    advertising_filter_parameter.list_logic_type = filt_param->list_logic_type;
    advertising_filter_parameter.filter_logic_type =
        filt_param->filt_logic_type;
    advertising_filter_parameter.rssi_high_thresh = filt_param->rssi_high_thres;
    advertising_filter_parameter.delivery_mode =
        static_cast<bluetooth::hci::DeliveryMode>(filt_param->dely_mode);
    if (filt_param && filt_param->dely_mode == 1) {
      advertising_filter_parameter.onfound_timeout = filt_param->found_timeout;
      advertising_filter_parameter.onfound_timeout_cnt =
          filt_param->found_timeout_cnt;
      advertising_filter_parameter.rssi_low_thresh = filt_param->rssi_low_thres;
      advertising_filter_parameter.onlost_timeout = filt_param->lost_timeout;
      advertising_filter_parameter.num_of_tracking_entries =
          filt_param->num_of_tracking_entries;
    }
  }

  bluetooth::shim::GetScanning()->ScanFilterParameterSetup(
      apcf_action, filter_index, advertising_filter_parameter);
  // TODO refactor callback mechanism
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(cb, 0, 0, btm_status_value(BTM_SUCCESS)));
}

/** Configure a scan filter condition  */
void BleScannerInterfaceImpl::ScanFilterAdd(int filter_index,
                                            std::vector<ApcfCommand> filters,
                                            FilterConfigCallback cb) {
  LOG(INFO) << __func__ << " in shim layer";
  std::vector<bluetooth::hci::AdvertisingPacketContentFilterCommand>
      new_filters = {};
  for (size_t i = 0; i < filters.size(); i++) {
    bluetooth::hci::AdvertisingPacketContentFilterCommand command{};
    if (!parse_filter_command(command, filters[i])) {
      LOG_ERROR("invalid apcf command");
      return;
    }
    new_filters.push_back(command);
  }
  bluetooth::shim::GetScanning()->ScanFilterAdd(filter_index, new_filters);
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(cb, 0, 0, 0, btm_status_value(BTM_SUCCESS)));
}

/** Clear all scan filter conditions for specific filter index*/
void BleScannerInterfaceImpl::ScanFilterClear(int /* filter_index */,
                                              FilterConfigCallback /* cb */) {
  LOG(INFO) << __func__ << " in shim layer";
  // This function doesn't used in java layer
}

/** Enable / disable scan filter feature*/
void BleScannerInterfaceImpl::ScanFilterEnable(bool enable, EnableCallback cb) {
  LOG(INFO) << __func__ << " in shim layer";
  bluetooth::shim::GetScanning()->ScanFilterEnable(enable);

  uint8_t action = enable ? 1 : 0;
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(cb, action, btm_status_value(BTM_SUCCESS)));
}

/** Is MSFT Extension supported? */
bool BleScannerInterfaceImpl::IsMsftSupported() {
  LOG_INFO("in shim layer");

  return bluetooth::shim::GetMsftExtensionManager()->SupportsMsftExtensions();
}

/** Adds MSFT filter */
void BleScannerInterfaceImpl::MsftAdvMonitorAdd(MsftAdvMonitor monitor,
                                                MsftAdvMonitorAddCallback cb) {
  LOG_INFO("in shim layer");
  msft_callbacks_.Add = cb;
  bluetooth::shim::GetMsftExtensionManager()->MsftAdvMonitorAdd(
      monitor, base::Bind(&BleScannerInterfaceImpl::OnMsftAdvMonitorAdd,
                          base::Unretained(this)));
}

/** Removes MSFT filter */
void BleScannerInterfaceImpl::MsftAdvMonitorRemove(
    uint8_t monitor_handle, MsftAdvMonitorRemoveCallback cb) {
  LOG_INFO("in shim layer");
  msft_callbacks_.Remove = cb;
  bluetooth::shim::GetMsftExtensionManager()->MsftAdvMonitorRemove(
      monitor_handle,
      base::Bind(&BleScannerInterfaceImpl::OnMsftAdvMonitorRemove,
                 base::Unretained(this)));
}

/** Enable / disable MSFT scan filter */
void BleScannerInterfaceImpl::MsftAdvMonitorEnable(
    bool enable, MsftAdvMonitorEnableCallback cb) {
  LOG_INFO("in shim layer");
  msft_callbacks_.Enable = cb;
  bluetooth::shim::GetMsftExtensionManager()->MsftAdvMonitorEnable(
      enable, base::Bind(&BleScannerInterfaceImpl::OnMsftAdvMonitorEnable,
                         base::Unretained(this), enable));
}

/** Callback of adding MSFT filter */
void BleScannerInterfaceImpl::OnMsftAdvMonitorAdd(
    uint8_t monitor_handle, bluetooth::hci::ErrorCode status) {
  LOG_INFO("in shim layer");
  msft_callbacks_.Add.Run(monitor_handle, (uint8_t)status);
}

/** Callback of removing MSFT filter */
void BleScannerInterfaceImpl::OnMsftAdvMonitorRemove(
    bluetooth::hci::ErrorCode status) {
  LOG_INFO("in shim layer");
  msft_callbacks_.Remove.Run((uint8_t)status);
}

/** Callback of enabling / disabling MSFT scan filter */
void BleScannerInterfaceImpl::OnMsftAdvMonitorEnable(
    bool enable, bluetooth::hci::ErrorCode status) {
  LOG_INFO("in shim layer");

  if (status == bluetooth::hci::ErrorCode::SUCCESS) {
    bluetooth::shim::GetScanning()->SetScanFilterPolicy(
        enable ? bluetooth::hci::LeScanningFilterPolicy::FILTER_ACCEPT_LIST_ONLY
               : bluetooth::hci::LeScanningFilterPolicy::ACCEPT_ALL);
  }

  msft_callbacks_.Enable.Run((uint8_t)status);
}

/** Sets the LE scan interval and window in units of N*0.625 msec */
void BleScannerInterfaceImpl::SetScanParameters(int scanner_id,
                                                uint8_t scan_type,
                                                int scan_interval,
                                                int scan_window,
                                                Callback /* cb */) {
  LOG(INFO) << __func__ << " in shim layer";
  if (BTM_BLE_ISVALID_PARAM(scan_interval, BTM_BLE_SCAN_INT_MIN,
                            BTM_BLE_EXT_SCAN_INT_MAX) &&
      BTM_BLE_ISVALID_PARAM(scan_window, BTM_BLE_SCAN_WIN_MIN,
                            BTM_BLE_EXT_SCAN_WIN_MAX)) {
    btm_cb.ble_ctr_cb.inq_var.scan_type = BTM_BLE_SCAN_MODE_ACTI;
    btm_cb.ble_ctr_cb.inq_var.scan_interval = scan_interval;
    btm_cb.ble_ctr_cb.inq_var.scan_window = scan_window;
  }

  bluetooth::shim::GetScanning()->SetScanParameters(
      scanner_id, static_cast<bluetooth::hci::LeScanType>(scan_type),
      scan_interval, scan_window);
}

/* Configure the batchscan storage */
void BleScannerInterfaceImpl::BatchscanConfigStorage(
    int client_if, int batch_scan_full_max, int batch_scan_trunc_max,
    int batch_scan_notify_threshold, Callback cb) {
  LOG(INFO) << __func__ << " in shim layer";
  bluetooth::shim::GetScanning()->BatchScanConifgStorage(
      batch_scan_full_max, batch_scan_trunc_max, batch_scan_notify_threshold,
      client_if);
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(cb, btm_status_value(BTM_SUCCESS)));
}

/* Enable batchscan */
void BleScannerInterfaceImpl::BatchscanEnable(int scan_mode, int scan_interval,
                                              int scan_window,
                                              int /* addr_type */,
                                              int discard_rule, Callback cb) {
  LOG(INFO) << __func__ << " in shim layer";
  auto batch_scan_mode = static_cast<bluetooth::hci::BatchScanMode>(scan_mode);
  auto batch_scan_discard_rule =
      static_cast<bluetooth::hci::BatchScanDiscardRule>(discard_rule);
  bluetooth::shim::GetScanning()->BatchScanEnable(
      batch_scan_mode, scan_window, scan_interval, batch_scan_discard_rule);
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(cb, btm_status_value(BTM_SUCCESS)));
}

/* Disable batchscan */
void BleScannerInterfaceImpl::BatchscanDisable(Callback cb) {
  LOG(INFO) << __func__ << " in shim layer";
  bluetooth::shim::GetScanning()->BatchScanDisable();
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(cb, btm_status_value(BTM_SUCCESS)));
}

/* Read out batchscan reports */
void BleScannerInterfaceImpl::BatchscanReadReports(int client_if,
                                                   int scan_mode) {
  LOG(INFO) << __func__ << " in shim layer";
  auto batch_scan_mode = static_cast<bluetooth::hci::BatchScanMode>(scan_mode);
  auto scanner_id = static_cast<bluetooth::hci::ScannerId>(client_if);
  bluetooth::shim::GetScanning()->BatchScanReadReport(scanner_id,
                                                      batch_scan_mode);
}

bool btm_random_pseudo_to_identity_addr(RawAddress* random_pseudo,
                                        tBLE_ADDR_TYPE* p_identity_addr_type);

bool btm_identity_addr_to_random_pseudo(RawAddress* bd_addr,
                                        tBLE_ADDR_TYPE* p_addr_type,
                                        bool refresh);

extern tACL_CONN* btm_acl_for_bda(const RawAddress& bd_addr,
                                  tBT_TRANSPORT transport);

void BleScannerInterfaceImpl::StartSync(uint8_t sid, RawAddress address,
                                        uint16_t skip, uint16_t timeout,
                                        int reg_id) {
  LOG(INFO) << __func__ << " in shim layer";
  tBLE_ADDR_TYPE address_type = BLE_ADDR_RANDOM;
  tINQ_DB_ENT* p_i = btm_inq_db_find(address);
  if (p_i) {
    address_type = p_i->inq_info.results.ble_addr_type;  // Random
  }
  btm_random_pseudo_to_identity_addr(&address, &address_type);
  address_type &= ~BLE_ADDR_TYPE_ID_BIT;
  bluetooth::shim::GetScanning()->StartSync(
      sid, ToAddressWithType(address, address_type), skip, timeout, reg_id);
}

void BleScannerInterfaceImpl::StopSync(uint16_t handle) {
  LOG(INFO) << __func__ << " in shim layer";
  bluetooth::shim::GetScanning()->StopSync(handle);
}

void BleScannerInterfaceImpl::CancelCreateSync(uint8_t sid,
                                               RawAddress address) {
  LOG(INFO) << __func__ << " in shim layer";
  bluetooth::shim::GetScanning()->CancelCreateSync(sid, ToGdAddress(address));
}

void BleScannerInterfaceImpl::TransferSync(RawAddress address,
                                           uint16_t service_data,
                                           uint16_t sync_handle,
                                           int pa_source) {
  LOG(INFO) << __func__ << " in shim layer";
  tACL_CONN* p_acl = btm_acl_for_bda(address, BT_TRANSPORT_LE);
  if (p_acl == NULL || !HCI_LE_PERIODIC_ADVERTISING_SYNC_TRANSFER_RECIPIENT(
                           p_acl->peer_le_features)) {
    LOG_ERROR("[PAST] Remote doesn't support PAST");
    scanning_callbacks_->OnPeriodicSyncTransferred(
        pa_source, BTM_MODE_UNSUPPORTED, address);
    return;
  }

  bluetooth::shim::GetScanning()->TransferSync(ToGdAddress(address),
                                               p_acl->Handle(), service_data,
                                               sync_handle, pa_source);
}

void BleScannerInterfaceImpl::TransferSetInfo(RawAddress address,
                                              uint16_t service_data,
                                              uint8_t adv_handle,
                                              int pa_source) {
  LOG(INFO) << __func__ << " in shim layer";
  tACL_CONN* p_acl = btm_acl_for_bda(address, BT_TRANSPORT_LE);
  if (p_acl == NULL || !HCI_LE_PERIODIC_ADVERTISING_SYNC_TRANSFER_RECIPIENT(
                           p_acl->peer_le_features)) {
    LOG_ERROR("[PAST] Remote doesn't support PAST");
    scanning_callbacks_->OnPeriodicSyncTransferred(
        pa_source, BTM_MODE_UNSUPPORTED, address);
    return;
  }

  bluetooth::shim::GetScanning()->TransferSetInfo(ToGdAddress(address),
                                                  p_acl->Handle(), service_data,
                                                  adv_handle, pa_source);
}

void BleScannerInterfaceImpl::SyncTxParameters(RawAddress addr, uint8_t mode,
                                               uint16_t skip, uint16_t timeout,
                                               int reg_id) {
  LOG(INFO) << __func__ << " in shim layer";
  bluetooth::shim::GetScanning()->SyncTxParameters(ToGdAddress(addr), mode,
                                                   skip, timeout, reg_id);
}

void BleScannerInterfaceImpl::RegisterCallbacks(ScanningCallbacks* callbacks) {
  LOG(INFO) << __func__ << " in shim layer";
  scanning_callbacks_ = callbacks;
}

void BleScannerInterfaceImpl::OnScannerRegistered(
    const bluetooth::hci::Uuid app_uuid, bluetooth::hci::ScannerId scanner_id,
    ScanningStatus status) {
  auto uuid = bluetooth::Uuid::From128BitBE(app_uuid.To128BitBE());
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(&ScanningCallbacks::OnScannerRegistered,
                                  base::Unretained(scanning_callbacks_), uuid,
                                  scanner_id, status));
}

void BleScannerInterfaceImpl::OnSetScannerParameterComplete(
    bluetooth::hci::ScannerId scanner_id, ScanningStatus status) {
  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(&ScanningCallbacks::OnSetScannerParameterComplete,
                     base::Unretained(scanning_callbacks_), scanner_id,
                     status));
}

void BleScannerInterfaceImpl::OnScanResult(
    uint16_t event_type, uint8_t address_type, bluetooth::hci::Address address,
    uint8_t primary_phy, uint8_t secondary_phy, uint8_t advertising_sid,
    int8_t tx_power, int8_t rssi, uint16_t periodic_advertising_interval,
    std::vector<uint8_t> advertising_data) {
  RawAddress raw_address = ToRawAddress(address);
  tBLE_ADDR_TYPE ble_addr_type = to_ble_addr_type(address_type);

  btm_cb.neighbor.le_scan.results++;
  if (ble_addr_type != BLE_ADDR_ANONYMOUS) {
    btm_ble_process_adv_addr(raw_address, &ble_addr_type);
  }

  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(&BleScannerInterfaceImpl::handle_remote_properties,
                     base::Unretained(this), raw_address, ble_addr_type,
                     advertising_data));

  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(&ScanningCallbacks::OnScanResult,
                     base::Unretained(scanning_callbacks_), event_type,
                     static_cast<uint8_t>(address_type), raw_address,
                     primary_phy, secondary_phy, advertising_sid, tx_power,
                     rssi, periodic_advertising_interval, advertising_data));

  // TODO: Remove when StartInquiry in GD part implemented
  btm_ble_process_adv_pkt_cont_for_inquiry(
      event_type, ble_addr_type, raw_address, primary_phy, secondary_phy,
      advertising_sid, tx_power, rssi, periodic_advertising_interval,
      advertising_data);
}

void BleScannerInterfaceImpl::OnTrackAdvFoundLost(
    bluetooth::hci::AdvertisingFilterOnFoundOnLostInfo on_found_on_lost_info) {
  AdvertisingTrackInfo track_info = {};
  RawAddress raw_address =
      ToRawAddress(on_found_on_lost_info.advertiser_address);

  if (on_found_on_lost_info.advertiser_address_type != BLE_ADDR_ANONYMOUS) {
    btm_ble_process_adv_addr(raw_address,
                             &on_found_on_lost_info.advertiser_address_type);
  }

  track_info.monitor_handle = on_found_on_lost_info.monitor_handle;
  track_info.advertiser_address = raw_address;
  track_info.advertiser_address_type =
      on_found_on_lost_info.advertiser_address_type;
  track_info.scanner_id = on_found_on_lost_info.scanner_id;
  track_info.filter_index = on_found_on_lost_info.filter_index;
  track_info.advertiser_state = on_found_on_lost_info.advertiser_state;
  track_info.advertiser_info_present =
      static_cast<uint8_t>(on_found_on_lost_info.advertiser_info_present);
  if (on_found_on_lost_info.advertiser_info_present ==
      bluetooth::hci::AdvtInfoPresent::ADVT_INFO_PRESENT) {
    track_info.tx_power = on_found_on_lost_info.tx_power;
    track_info.rssi = on_found_on_lost_info.rssi;
    track_info.time_stamp = on_found_on_lost_info.time_stamp;
    auto adv_data = on_found_on_lost_info.adv_packet;
    track_info.adv_packet_len = (uint8_t)adv_data.size();
    track_info.adv_packet.reserve(adv_data.size());
    track_info.adv_packet.insert(track_info.adv_packet.end(), adv_data.begin(),
                                 adv_data.end());
    auto scan_rsp_data = on_found_on_lost_info.scan_response;
    track_info.scan_response_len = (uint8_t)scan_rsp_data.size();
    track_info.scan_response.reserve(adv_data.size());
    track_info.scan_response.insert(track_info.scan_response.end(),
                                    scan_rsp_data.begin(), scan_rsp_data.end());
  }
  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(&ScanningCallbacks::OnTrackAdvFoundLost,
                     base::Unretained(scanning_callbacks_), track_info));
}

void BleScannerInterfaceImpl::OnBatchScanReports(int client_if, int status,
                                                 int report_format,
                                                 int num_records,
                                                 std::vector<uint8_t> data) {
  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(&ScanningCallbacks::OnBatchScanReports,
                     base::Unretained(scanning_callbacks_), client_if, status,
                     report_format, num_records, data));
}

void BleScannerInterfaceImpl::OnBatchScanThresholdCrossed(int client_if) {
  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(&ScanningCallbacks::OnBatchScanThresholdCrossed,
                     base::Unretained(scanning_callbacks_), client_if));
}

void BleScannerInterfaceImpl::OnPeriodicSyncStarted(
    int reg_id, uint8_t status, uint16_t sync_handle, uint8_t advertising_sid,
    bluetooth::hci::AddressWithType address_with_type, uint8_t phy,
    uint16_t interval) {
  RawAddress raw_address = ToRawAddress(address_with_type.GetAddress());
  tBLE_ADDR_TYPE ble_addr_type =
      to_ble_addr_type((uint8_t)address_with_type.GetAddressType());
  if (ble_addr_type & BLE_ADDR_TYPE_ID_BIT) {
    btm_identity_addr_to_random_pseudo(&raw_address, &ble_addr_type, true);
  }

  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(&ScanningCallbacks::OnPeriodicSyncStarted,
                                  base::Unretained(scanning_callbacks_), reg_id,
                                  status, sync_handle, advertising_sid,
                                  static_cast<int>(ble_addr_type), raw_address,
                                  phy, interval));
}

void BleScannerInterfaceImpl::OnPeriodicSyncReport(uint16_t sync_handle,
                                                   int8_t tx_power, int8_t rssi,
                                                   uint8_t status,
                                                   std::vector<uint8_t> data) {
  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(&ScanningCallbacks::OnPeriodicSyncReport,
                     base::Unretained(scanning_callbacks_), sync_handle,
                     tx_power, rssi, status, std::move(data)));
}

void BleScannerInterfaceImpl::OnPeriodicSyncLost(uint16_t sync_handle) {
  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(&ScanningCallbacks::OnPeriodicSyncLost,
                     base::Unretained(scanning_callbacks_), sync_handle));
}

void BleScannerInterfaceImpl::OnPeriodicSyncTransferred(
    int pa_source, uint8_t status, bluetooth::hci::Address address) {
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(&ScanningCallbacks::OnPeriodicSyncTransferred,
                                  base::Unretained(scanning_callbacks_),
                                  pa_source, status, ToRawAddress(address)));
}

void BleScannerInterfaceImpl::OnBigInfoReport(uint16_t sync_handle, bool encrypted) {
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(&ScanningCallbacks::OnBigInfoReport,
                   base::Unretained(scanning_callbacks_), sync_handle, encrypted));
}

void BleScannerInterfaceImpl::OnTimeout() {}
void BleScannerInterfaceImpl::OnFilterEnable(
    bluetooth::hci::Enable /* enable */, uint8_t /* status */) {}
void BleScannerInterfaceImpl::OnFilterParamSetup(
    uint8_t /* available_spaces */, bluetooth::hci::ApcfAction /* action */,
    uint8_t /* status */) {}
void BleScannerInterfaceImpl::OnFilterConfigCallback(
    bluetooth::hci::ApcfFilterType /* filter_type */,
    uint8_t /* available_spaces */, bluetooth::hci::ApcfAction /* action */,
    uint8_t /* status */) {}

bool BleScannerInterfaceImpl::parse_filter_command(
    bluetooth::hci::AdvertisingPacketContentFilterCommand&
        advertising_packet_content_filter_command,
    ApcfCommand apcf_command) {
  advertising_packet_content_filter_command.filter_type =
      static_cast<bluetooth::hci::ApcfFilterType>(apcf_command.type);
  bluetooth::hci::Address address = ToGdAddress(apcf_command.address);
  advertising_packet_content_filter_command.address = address;
  advertising_packet_content_filter_command.application_address_type =
      static_cast<bluetooth::hci::ApcfApplicationAddressType>(
          apcf_command.addr_type);

  if (!apcf_command.uuid.IsEmpty()) {
    uint8_t uuid_len = apcf_command.uuid.GetShortestRepresentationSize();
    switch (uuid_len) {
      case bluetooth::Uuid::kNumBytes16: {
        advertising_packet_content_filter_command.uuid =
            bluetooth::hci::Uuid::From16Bit(apcf_command.uuid.As16Bit());
      } break;
      case bluetooth::Uuid::kNumBytes32: {
        advertising_packet_content_filter_command.uuid =
            bluetooth::hci::Uuid::From32Bit(apcf_command.uuid.As32Bit());
      } break;
      case bluetooth::Uuid::kNumBytes128: {
        advertising_packet_content_filter_command.uuid =
            bluetooth::hci::Uuid::From128BitBE(apcf_command.uuid.To128BitBE());
      } break;
      default:
        LOG_WARN("illegal UUID length %d", (uint16_t)uuid_len);
        return false;
    }
  }

  if (!apcf_command.uuid_mask.IsEmpty()) {
    uint8_t uuid_len = apcf_command.uuid.GetShortestRepresentationSize();
    switch (uuid_len) {
      case bluetooth::Uuid::kNumBytes16: {
        advertising_packet_content_filter_command.uuid_mask =
            bluetooth::hci::Uuid::From16Bit(apcf_command.uuid_mask.As16Bit());
      } break;
      case bluetooth::Uuid::kNumBytes32: {
        advertising_packet_content_filter_command.uuid_mask =
            bluetooth::hci::Uuid::From32Bit(apcf_command.uuid_mask.As32Bit());
      } break;
      case bluetooth::Uuid::kNumBytes128: {
        advertising_packet_content_filter_command.uuid_mask =
            bluetooth::hci::Uuid::From128BitBE(
                apcf_command.uuid_mask.To128BitBE());
      } break;
      default:
        LOG_WARN("illegal UUID length %d", (uint16_t)uuid_len);
        return false;
    }
  }

  advertising_packet_content_filter_command.name.assign(
      apcf_command.name.begin(), apcf_command.name.end());
  advertising_packet_content_filter_command.company = apcf_command.company;
  advertising_packet_content_filter_command.company_mask =
      apcf_command.company_mask;
  advertising_packet_content_filter_command.ad_type = apcf_command.ad_type;
  advertising_packet_content_filter_command.org_id = apcf_command.org_id;
  advertising_packet_content_filter_command.tds_flags = apcf_command.tds_flags;
  advertising_packet_content_filter_command.tds_flags_mask =
      apcf_command.tds_flags_mask;
  advertising_packet_content_filter_command.meta_data_type =
      static_cast<bluetooth::hci::ApcfMetaDataType>(
          apcf_command.meta_data_type);
  advertising_packet_content_filter_command.meta_data.assign(
      apcf_command.meta_data.begin(), apcf_command.meta_data.end());
  advertising_packet_content_filter_command.data.assign(
      apcf_command.data.begin(), apcf_command.data.end());
  advertising_packet_content_filter_command.data_mask.assign(
      apcf_command.data_mask.begin(), apcf_command.data_mask.end());
  advertising_packet_content_filter_command.irk = apcf_command.irk;
  return true;
}

void BleScannerInterfaceImpl::handle_remote_properties(
    RawAddress bd_addr, tBLE_ADDR_TYPE addr_type,
    std::vector<uint8_t> advertising_data) {
  if (!bluetooth::shim::is_gd_stack_started_up()) {
    LOG_WARN("Gd stack is stopped, return");
    return;
  }

  // skip anonymous advertisment
  if (addr_type == BLE_ADDR_ANONYMOUS) {
    return;
  }

  auto device_type = bluetooth::hci::DeviceType::LE;
  uint8_t flag_len;
  const uint8_t* p_flag = AdvertiseDataParser::GetFieldByType(
      advertising_data, BTM_BLE_AD_TYPE_FLAG, &flag_len);

  if (p_flag != NULL && flag_len != 0) {
    if ((BTM_BLE_BREDR_NOT_SPT & *p_flag) == 0) {
      device_type = bluetooth::hci::DeviceType::DUAL;
    }
  }

  uint8_t remote_name_len;
  const uint8_t* p_eir_remote_name = AdvertiseDataParser::GetFieldByType(
      advertising_data, HCI_EIR_COMPLETE_LOCAL_NAME_TYPE, &remote_name_len);

  if (p_eir_remote_name == NULL) {
    p_eir_remote_name = AdvertiseDataParser::GetFieldByType(
        advertising_data, HCI_EIR_SHORTENED_LOCAL_NAME_TYPE, &remote_name_len);
  }

  bt_bdname_t bdname = {0};

  // update device name
  if (p_eir_remote_name) {
    if (!address_cache_.find(bd_addr)) {
      address_cache_.add(bd_addr);

      if (remote_name_len > BD_NAME_LEN + 1 ||
          (remote_name_len == BD_NAME_LEN + 1 &&
           p_eir_remote_name[BD_NAME_LEN] != '\0')) {
        LOG_INFO("%s dropping invalid packet - device name too long: %d",
                 __func__, remote_name_len);
        return;
      }

      memcpy(bdname.name, p_eir_remote_name, remote_name_len);
      if (remote_name_len < BD_NAME_LEN + 1)
        bdname.name[remote_name_len] = '\0';
      btif_dm_update_ble_remote_properties(bd_addr, bdname.name, kDevClassEmpty,
                                           device_type);
    }
  }

  DEV_CLASS dev_class = btm_ble_get_appearance_as_cod(advertising_data);
  if (dev_class != kDevClassUnclassified) {
    btif_dm_update_ble_remote_properties(bd_addr, bdname.name, dev_class,
                                         device_type);
  }

  auto* storage_module = bluetooth::shim::GetStorage();
  bluetooth::hci::Address address = ToGdAddress(bd_addr);

  // update device type
  auto mutation = storage_module->Modify();
  bluetooth::storage::Device device =
      storage_module->GetDeviceByLegacyKey(address);
  mutation.Add(device.SetDeviceType(device_type));
  mutation.Commit();

  // update address type
  auto mutation2 = storage_module->Modify();
  bluetooth::storage::LeDevice le_device = device.Le();
  mutation2.Add(
      le_device.SetAddressType((bluetooth::hci::AddressType)addr_type));
  mutation2.Commit();
}

void BleScannerInterfaceImpl::AddressCache::add(const RawAddress& p_bda) {
  // Remove the oldest entries
  while (remote_bdaddr_cache_.size() >= remote_bdaddr_cache_max_size_) {
    const RawAddress& raw_address = remote_bdaddr_cache_ordered_.front();
    remote_bdaddr_cache_.erase(raw_address);
    remote_bdaddr_cache_ordered_.pop();
  }
  remote_bdaddr_cache_.insert(p_bda);
  remote_bdaddr_cache_ordered_.push(p_bda);
}

bool BleScannerInterfaceImpl::AddressCache::find(const RawAddress& p_bda) {
  return (remote_bdaddr_cache_.find(p_bda) != remote_bdaddr_cache_.end());
}

void BleScannerInterfaceImpl::AddressCache::init(void) {
  remote_bdaddr_cache_.clear();
  remote_bdaddr_cache_ordered_ = {};
}

BleScannerInterfaceImpl* bt_le_scanner_instance = nullptr;

BleScannerInterface* bluetooth::shim::get_ble_scanner_instance() {
  if (bt_le_scanner_instance == nullptr) {
    bt_le_scanner_instance = new BleScannerInterfaceImpl();
  }
  return bt_le_scanner_instance;
}

void bluetooth::shim::init_scanning_manager() {
  static_cast<BleScannerInterfaceImpl*>(
      bluetooth::shim::get_ble_scanner_instance())
      ->Init();
}

bool bluetooth::shim::is_ad_type_filter_supported() {
  return bluetooth::shim::GetScanning()->IsAdTypeFilterSupported();
}

void bluetooth::shim::set_ad_type_rsi_filter(bool enable) {
  bluetooth::hci::AdvertisingFilterParameter advertising_filter_parameter;
  bluetooth::shim::GetScanning()->ScanFilterParameterSetup(
      bluetooth::hci::ApcfAction::DELETE, 0x00, advertising_filter_parameter);
  if (enable) {
    std::vector<bluetooth::hci::AdvertisingPacketContentFilterCommand> filters =
        {};
    bluetooth::hci::AdvertisingPacketContentFilterCommand filter{};
    filter.filter_type = bluetooth::hci::ApcfFilterType::AD_TYPE;
    filter.ad_type = BTM_BLE_AD_TYPE_RSI;
    filters.push_back(filter);
    bluetooth::shim::GetScanning()->ScanFilterAdd(0x00, filters);

    advertising_filter_parameter.delivery_mode =
        bluetooth::hci::DeliveryMode::IMMEDIATE;
    advertising_filter_parameter.feature_selection = kAllowADTypeFilter;
    advertising_filter_parameter.list_logic_type = kAllowADTypeFilter;
    advertising_filter_parameter.filter_logic_type = kFilterLogicOr;
    advertising_filter_parameter.rssi_high_thresh = kLowestRssiValue;
    bluetooth::shim::GetScanning()->ScanFilterParameterSetup(
        bluetooth::hci::ApcfAction::ADD, 0x00, advertising_filter_parameter);
  }
}

void bluetooth::shim::set_empty_filter(bool enable) {
  bluetooth::hci::AdvertisingFilterParameter advertising_filter_parameter;
  bluetooth::shim::GetScanning()->ScanFilterParameterSetup(
      bluetooth::hci::ApcfAction::DELETE, 0x00, advertising_filter_parameter);
  if (enable) {
    /* Add an allow-all filter on index 0 */
    advertising_filter_parameter.delivery_mode =
        bluetooth::hci::DeliveryMode::IMMEDIATE;
    advertising_filter_parameter.feature_selection = kAllowAllFilter;
    advertising_filter_parameter.list_logic_type = kListLogicOr;
    advertising_filter_parameter.filter_logic_type = kFilterLogicOr;
    advertising_filter_parameter.rssi_high_thresh = kLowestRssiValue;
    bluetooth::shim::GetScanning()->ScanFilterParameterSetup(
        bluetooth::hci::ApcfAction::ADD, 0x00, advertising_filter_parameter);
  }
}

void bluetooth::shim::set_target_announcements_filter(bool enable) {
  uint8_t filter_index = 0x03;

  LOG_DEBUG(" enable %d", enable);

  bluetooth::hci::AdvertisingFilterParameter advertising_filter_parameter = {};
  bluetooth::shim::GetScanning()->ScanFilterParameterSetup(
      bluetooth::hci::ApcfAction::DELETE, filter_index,
      advertising_filter_parameter);

  if (!enable) return;

  advertising_filter_parameter.delivery_mode =
      bluetooth::hci::DeliveryMode::IMMEDIATE;
  advertising_filter_parameter.feature_selection = kAllowServiceDataFilter;
  advertising_filter_parameter.list_logic_type = kListLogicOr;
  advertising_filter_parameter.filter_logic_type = kFilterLogicAnd;
  advertising_filter_parameter.rssi_high_thresh = kLowestRssiValue;

  /* Add targeted announcements filter on index 4 */
  std::vector<bluetooth::hci::AdvertisingPacketContentFilterCommand>
      cap_bap_filter = {};

  bluetooth::hci::AdvertisingPacketContentFilterCommand cap_filter{};
  cap_filter.filter_type = bluetooth::hci::ApcfFilterType::SERVICE_DATA;
  cap_filter.data = {0x53, 0x18, 0x01};
  cap_filter.data_mask = {0x53, 0x18, 0xFF};
  cap_bap_filter.push_back(cap_filter);

  bluetooth::hci::AdvertisingPacketContentFilterCommand bap_filter{};
  bap_filter.filter_type = bluetooth::hci::ApcfFilterType::SERVICE_DATA;
  bap_filter.data = {0x4e, 0x18, 0x01};
  bap_filter.data_mask = {0x4e, 0x18, 0xFF};

  cap_bap_filter.push_back(bap_filter);
  bluetooth::shim::GetScanning()->ScanFilterAdd(filter_index, cap_bap_filter);

  bluetooth::shim::GetScanning()->ScanFilterParameterSetup(
      bluetooth::hci::ApcfAction::ADD, filter_index,
      advertising_filter_parameter);
}
