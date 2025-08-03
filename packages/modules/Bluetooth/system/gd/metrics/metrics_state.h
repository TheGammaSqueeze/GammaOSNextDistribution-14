/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
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

#include <frameworks/proto_logging/stats/enums/bluetooth/le/enums.pb.h>

#include <chrono>
#include <cstdint>
#include <memory>
#include <unordered_map>
#include <utility>
#include <vector>

#include "common/strings.h"
#include "hci/address.h"
#include "os/metrics.h"

namespace bluetooth {

namespace metrics {

using android::bluetooth::le::LeAclConnectionState;
using android::bluetooth::le::LeConnectionOriginType;
using android::bluetooth::le::LeConnectionState;
using android::bluetooth::le::LeConnectionType;

using ClockTimePoint = std::chrono::time_point<std::chrono::high_resolution_clock>;

const static ClockTimePoint kInvalidTimePoint{};

inline int64_t get_timedelta_nanos(const ClockTimePoint& t1, const ClockTimePoint& t2) {
  if (t1 == kInvalidTimePoint || t2 == kInvalidTimePoint) {
    return -1;
  }
  return std::abs(std::chrono::duration_cast<std::chrono::nanoseconds>(t2 - t1).count());
}

class BaseMetricsLoggerModule {
 public:
  BaseMetricsLoggerModule() {}
  virtual void LogMetricBluetoothLESession(os::LEConnectionSessionOptions session_options) = 0;
  virtual ~BaseMetricsLoggerModule() {}
};

class MetricsLoggerModule : public BaseMetricsLoggerModule {
 public:
  MetricsLoggerModule() {}
  void LogMetricBluetoothLESession(os::LEConnectionSessionOptions session_options);
  virtual ~MetricsLoggerModule() {}
};

class LEConnectionMetricState {
 public:
  hci::Address address;
  LEConnectionMetricState(const hci::Address address) : address(address) {}
  LeConnectionState state;
  LeAclConnectionState acl_state;
  LeConnectionType input_connection_type = LeConnectionType::CONNECTION_TYPE_UNSPECIFIED;
  android::bluetooth::hci::StatusEnum acl_status_code;
  ClockTimePoint start_timepoint = kInvalidTimePoint;
  ClockTimePoint end_timepoint = kInvalidTimePoint;
  bool is_cancelled = false;
  LeConnectionOriginType connection_origin_type = LeConnectionOriginType::ORIGIN_UNSPECIFIED;

  bool IsStarted();
  bool IsEnded();
  bool IsCancelled();

  void AddStateChangedEvent(
      LeConnectionOriginType origin_type,
      LeConnectionType connection_type,
      LeConnectionState transaction_state,
      std::vector<std::pair<os::ArgumentType, int>> argument_list);

};

class LEConnectionMetricsRemoteDevice {
 public:
  LEConnectionMetricsRemoteDevice();

  LEConnectionMetricsRemoteDevice(BaseMetricsLoggerModule* baseMetricsLoggerModule);

  void AddStateChangedEvent(
      const hci::Address& address,
      LeConnectionOriginType origin_type,
      LeConnectionType connection_type,
      LeConnectionState transaction_state,
      std::vector<std::pair<os::ArgumentType, int>> argument_list);

  void UploadLEConnectionSession(const hci::Address& address);

 private:
  mutable std::mutex le_connection_metrics_remote_device_guard;
  std::vector<std::unique_ptr<LEConnectionMetricState>> device_metrics;
  std::unordered_map<hci::Address, LEConnectionMetricState*> opened_devices;
  BaseMetricsLoggerModule* metrics_logger_module;
};

class MetricsCollector {
 public:
  // getting the LE Connection Metrics Collector
  static LEConnectionMetricsRemoteDevice* GetLEConnectionMetricsCollector();

 private:
  static LEConnectionMetricsRemoteDevice* le_connection_metrics_remote_device;
};

}  // namespace metrics
}  // namespace bluetooth
