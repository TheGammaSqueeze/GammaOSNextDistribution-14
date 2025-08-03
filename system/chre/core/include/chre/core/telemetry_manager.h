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

#ifndef CHRE_CORE_TELEMETRY_MANAGER_H_
#define CHRE_CORE_TELEMETRY_MANAGER_H_

#include <cinttypes>

#include "chre/util/non_copyable.h"

namespace chre {

/**
 * Container class that handles reporting system telemetry metrics to the host.
 */
class TelemetryManager : public NonCopyable {
 public:
  /**
   * Enum type to describe a CHRE PAL.
   */
  enum PalType : uint8_t {
    UNKNOWN = 0,
    SENSOR,
    WIFI,
    GNSS,
    WWAN,
    AUDIO,
    BLE,
  };

  TelemetryManager();

  /**
   * Sends telemetry data related to a PAL open failure.
   *
   * @param type The type of PAL that failed to open.
   */
  void onPalOpenFailure(PalType type);

  /**
   * Collects system-level metrics to send to the host for logging.
   */
  void collectSystemMetrics();

 private:
  /**
   * Schedules a periodic timer to collect system metrics.
   */
  void scheduleMetricTimer();
};

}  // namespace chre

#endif  // CHRE_CORE_TELEMETRY_MANAGER_H_
