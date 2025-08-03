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
#include "hci/le_scanning_callback.h"
#include "hci/uuid.h"
#include "module.h"

namespace bluetooth {
namespace hci {

enum class BatchScanMode : uint8_t {
  DISABLE = 0,
  TRUNCATED = 1,
  FULL = 2,
  TRUNCATED_AND_FULL = 3,
};

class LeScanningManager : public bluetooth::Module {
 public:
  static constexpr uint8_t kMaxAppNum = 32;
  static constexpr uint8_t kAdvertisingDataInfoNotPresent = 0xff;
  static constexpr uint8_t kTxPowerInformationNotPresent = 0x7f;
  static constexpr uint8_t kNotPeriodicAdvertisement = 0x00;
  static constexpr ScannerId kInvalidScannerId = 0xFF;
  LeScanningManager();
  LeScanningManager(const LeScanningManager&) = delete;
  LeScanningManager& operator=(const LeScanningManager&) = delete;

  virtual void RegisterScanner(const Uuid app_uuid);

  virtual void Unregister(ScannerId scanner_id);

  virtual void Scan(bool start);

  virtual void SetScanParameters(
      ScannerId scanner_id, LeScanType scan_type, uint16_t scan_interval, uint16_t scan_window);

  virtual void SetScanFilterPolicy(LeScanningFilterPolicy filter_policy);

  /* Scan filter */
  virtual void ScanFilterEnable(bool enable);

  virtual void ScanFilterParameterSetup(
      ApcfAction action, uint8_t filter_index, AdvertisingFilterParameter advertising_filter_parameter);

  virtual void ScanFilterAdd(uint8_t filter_index, std::vector<AdvertisingPacketContentFilterCommand> filters);

  /*Batch Scan*/
  virtual void BatchScanConifgStorage(
      uint8_t batch_scan_full_max,
      uint8_t batch_scan_truncated_max,
      uint8_t batch_scan_notify_threshold,
      ScannerId scanner_id);
  virtual void BatchScanEnable(
      BatchScanMode scan_mode,
      uint32_t duty_cycle_scan_window_slots,
      uint32_t duty_cycle_scan_interval_slots,
      BatchScanDiscardRule batch_scan_discard_rule);
  virtual void BatchScanDisable();
  virtual void BatchScanReadReport(ScannerId scanner_id, BatchScanMode scan_mode);

  virtual void StartSync(uint8_t sid, const AddressWithType& address, uint16_t skip, uint16_t timeout, int reg_id);

  virtual void StopSync(uint16_t handle);

  virtual void CancelCreateSync(uint8_t sid, const Address& address);

  virtual void TransferSync(
      const Address& address,
      uint16_t handle,
      uint16_t service_data,
      uint16_t sync_handle,
      int pa_source);

  virtual void TransferSetInfo(
      const Address& address,
      uint16_t handle,
      uint16_t service_data,
      uint8_t adv_handle,
      int pa_source);

  virtual void SyncTxParameters(const Address& addr, uint8_t mode, uint16_t skip, uint16_t timeout, int reg_id);

  virtual void TrackAdvertiser(uint8_t filter_index, ScannerId scanner_id);

  virtual void RegisterScanningCallback(ScanningCallback* scanning_callback);

  virtual bool IsAdTypeFilterSupported() const;

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
