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
#ifndef GD_RUST_TOPSHIM_GATT_GATT_BLE_SCANNER_SHIM_H
#define GD_RUST_TOPSHIM_GATT_GATT_BLE_SCANNER_SHIM_H

#include <memory>

#include "include/hardware/ble_scanner.h"
#include "include/hardware/bt_gatt.h"
#include "rust/cxx.h"

namespace bluetooth {
namespace topshim {
namespace rust {

struct RustApcfCommand;
struct RustMsftAdvMonitor;
struct RustGattFilterParam;
struct RustUuid;

class BleScannerIntf : public ScanningCallbacks {
 public:
  BleScannerIntf(BleScannerInterface* scanner_intf) : scanner_intf_(scanner_intf){};
  ~BleScannerIntf() = default;

  // ScanningCallbacks overrides
  void OnScannerRegistered(const bluetooth::Uuid app_uuid, uint8_t scannerId, uint8_t status) override;

  void OnSetScannerParameterComplete(uint8_t scannerId, uint8_t status) override;

  void OnScanResult(
      uint16_t event_type,
      uint8_t addr_type,
      RawAddress bda,
      uint8_t primary_phy,
      uint8_t secondary_phy,
      uint8_t advertising_sid,
      int8_t tx_power,
      int8_t rssi,
      uint16_t periodic_adv_int,
      std::vector<uint8_t> adv_data) override;

  void OnTrackAdvFoundLost(AdvertisingTrackInfo advertising_track_info) override;

  void OnBatchScanReports(
      int client_if, int status, int report_format, int num_records, std::vector<uint8_t> data) override;

  void OnBatchScanThresholdCrossed(int client_if) override;

  void OnPeriodicSyncStarted(
      int reg_id,
      uint8_t status,
      uint16_t sync_handle,
      uint8_t advertising_sid,
      uint8_t address_type,
      RawAddress address,
      uint8_t phy,
      uint16_t interval) override;
  void OnPeriodicSyncReport(
      uint16_t sync_handle, int8_t tx_power, int8_t rssi, uint8_t status, std::vector<uint8_t> data) override;
  void OnPeriodicSyncLost(uint16_t sync_handle) override;
  void OnPeriodicSyncTransferred(int pa_source, uint8_t status, RawAddress address) override;
  void OnBigInfoReport(uint16_t sync_handle, bool encrypted) override;

  // Implementations of BleScannerInterface. These don't inherit from
  // BleScannerInterface because the Rust FFI boundary requires some clever
  // modifications.

  // Register a scanner for a Uuid. Response comes back via
  // |OnRegisterCallback|.
  void RegisterScanner(RustUuid uuid);

  // Unregister a scanner with a |scanner_id|.
  void Unregister(uint8_t scanner_id);

  // Start/Stop LE scanning.
  void Scan(bool start);

  // Setup scan filter parameters. Get responses via
  // |OnFilterParamSetupCallback|.
  void ScanFilterParamSetup(uint8_t scanner_id, uint8_t action, uint8_t filter_index, RustGattFilterParam filter_param);

  // Adds filters to given filter index. Gets responses via
  // |OnFilterConfigCallback|.
  void ScanFilterAdd(uint8_t filter_index, ::rust::Vec<RustApcfCommand> filters);

  // Clear scan filter conditions for a specific index.
  void ScanFilterClear(uint8_t filter_index);

  // Enable/disable scan filter. Gets responses via |OnEnableCallback|.
  void ScanFilterEnable(bool enable);

  // Is MSFT Extension supported?
  bool IsMsftSupported();

  // Adds an MSFT filter. Gets responses via |OnMsftAdvMonitorAddCallback|.
  void MsftAdvMonitorAdd(uint32_t call_id, const RustMsftAdvMonitor& monitor);

  // Removes a previously added MSFT scan filter.
  void MsftAdvMonitorRemove(uint32_t call_id, uint8_t monitor_handle);

  // Enables or disables MSFT advertisement monitor.
  void MsftAdvMonitorEnable(uint32_t call_id, bool enable);

  // Sets the LE scan interval and window in units of N * 0.625 msec. The result
  // of this action is returned via |OnStatusCallback|.
  void SetScanParameters(
      uint8_t scanner_id, uint8_t scan_type, uint16_t scan_interval, uint16_t scan_window);

  // Configure the batchscan storage and get a response via |OnStatusCallback|.
  void BatchscanConfigStorage(
      uint8_t scanner_id,
      int32_t batch_scan_full_max,
      int32_t batch_scan_trunc_max,
      int32_t batch_scan_notify_threshold);

  // Enable batchscan. Gets responses via |OnStatusCallback| with scanner id
  // = 0 (since multiple scanners can be registered).
  void BatchscanEnable(
      int32_t scan_mode, uint16_t scan_interval, uint16_t scan_window, int32_t addr_type, int32_t discard_rule);

  // Disable batchscan. Gets responses via |OnStatusCallback| with a scanner id
  // = 0 (since multiple scanners can be registered).
  void BatchscanDisable();

  // Read out batchscan report for a specific scanner. Gets responses via
  // |ScanningCallbacks::OnBatchScanReports|.
  void BatchscanReadReports(uint8_t scanner_id, int32_t scan_mode);

  // Start periodic sync. Gets responses via |OnStartSyncCb|. Periodic reports
  // come via |OnSyncReportCb| and |OnSyncLostCb|.
  void StartSync(uint8_t sid, RawAddress addr, uint16_t skip, uint16_t timeout);

  // Stop periodic sync.
  void StopSync(uint16_t handle);

  // Cancel creating a periodic sync.
  void CancelCreateSync(uint8_t sid, RawAddress addr);

  // Transfer sync data to target address. Gets responses via
  // |OnSyncTransferCb|.
  void TransferSync(RawAddress addr, uint16_t service_data, uint16_t sync_handle);

  // Transfer set info to target address. Gets responses via |OnSyncTransferCb|.
  void TransferSetInfo(RawAddress addr, uint16_t service_data, uint8_t adv_handle);

  // Sync tx parameters to target address. Gets responses via |OnStartSyncCb|.
  void SyncTxParameters(RawAddress addr, uint8_t mode, uint16_t skip, uint16_t timeout);

  // Register scanning callbacks to be dispatched to the Rust layer via static
  // methods.
  void RegisterCallbacks();

 private:
  // The callback functions below will get base::Bind to the apis that need it
  // and will call the same Rust function with all the parameters. Some of these
  // callbacks don't have all the parameters coming back in the original
  // callback and will need the values to be base::Bind at the callsite.

  void OnRegisterCallback(RustUuid uuid, uint8_t scanner_id, uint8_t btm_status);
  void OnStatusCallback(uint8_t scanner_id, uint8_t btm_status);
  void OnEnableCallback(uint8_t action, uint8_t btm_status);
  void OnFilterParamSetupCallback(uint8_t scanner_id, uint8_t avbl_space, uint8_t action_type, uint8_t btm_status);
  void OnFilterConfigCallback(
      uint8_t filt_index, uint8_t filt_type, uint8_t avbl_space, uint8_t action, uint8_t btm_status);
  void OnMsftAdvMonitorAddCallback(uint32_t call_id, uint8_t monitor_handle, uint8_t status);
  void OnMsftAdvMonitorRemoveCallback(uint32_t call_id, uint8_t status);
  void OnMsftAdvMonitorEnableCallback(uint32_t call_id, uint8_t status);

  BleScannerInterface* scanner_intf_;
};

std::unique_ptr<BleScannerIntf> GetBleScannerIntf(const unsigned char* gatt_intf);

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth

#endif  // GD_RUST_TOPSHIM_GATT_GATT_BLE_SCANNER_SHIM_H
