/*
 * Copyright 2023 The Android Open Source Project
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

#include <base/logging.h>

#include <cstdint>
#include <memory>

#include "types/raw_address.h"

namespace power_telemetry {

struct PowerTelemetryImpl;

class PowerTelemetry {
 public:
  PowerTelemetry();

  void RecordLogDataContainer();
  void LogScanStarted();

  void LogHciCmdDetail();
  void LogHciEvtDetail();

  void LogLinkDetails(uint16_t handle, const RawAddress& bdaddr,
                      bool isConnected, bool is_acl_link);
  void LogRxAclPktData(uint16_t len);
  void LogTxAclPktData(uint16_t len);

  void LogChannelConnected(uint16_t psm, int32_t src_id, int32_t dst_id,
                           const RawAddress& bd_addr);
  void LogChannelDisconnected(uint16_t psm, int32_t src_id, int32_t dst_id,
                              const RawAddress& bd_addr);
  void LogRxBytes(uint16_t psm, int32_t src_id, int32_t dst_id,
                  const RawAddress& bd_addr, int32_t num_bytes);
  void LogTxBytes(uint16_t psm, int32_t src_id, int32_t dst_id,
                  const RawAddress& bd_addr, int32_t num_bytes);

  void LogSniffStarted(uint16_t handle, const RawAddress& bdaddr);
  void LogSniffStopped(uint16_t handle, const RawAddress& bdaddr);
  void LogAclTxPowerLevel(uint16_t handle, uint8_t txPower);
  void LogInqScanStarted();
  void LogInqScanStopped();
  void LogBleScan(uint16_t num_resps);
  void LogBleAdvStarted();
  void LogBleAdvStopped();

  void LogTxPower(void* res);
  void LogTrafficData();

  void Dumpsys(int32_t fd);

  std::unique_ptr<PowerTelemetryImpl> pimpl_;
};

PowerTelemetry& GetInstance();

}  // namespace power_telemetry
