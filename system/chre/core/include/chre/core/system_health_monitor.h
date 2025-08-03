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

#ifndef CHRE_CORE_SYSTEM_HEALTH_MONITOR_H_
#define CHRE_CORE_SYSTEM_HEALTH_MONITOR_H_

#include <cstdint>

#include "chre/platform/assert.h"
#include "chre/platform/log.h"
#include "chre/util/enum.h"
#include "chre/util/non_copyable.h"

namespace chre {

/**
 * Types of different health check id
 * User should consider adding a new check id if current id does not describe
 * the case accurately
 *
 * The goal of this enum class is to be granular enough to produce useful debug
 * information and metric report
 */
enum class HealthCheckId : uint16_t {
  WifiScanResponseTimeout = 0,
  WifiConfigureScanMonitorTimeout = 1,
  WifiRequestRangingTimeout = 2,
  UnexpectedWifiPalCallback = 3,

  //! Must be last
  NumCheckIds
};

class SystemHealthMonitor : public NonCopyable {
 public:
  /**
   * Configures if onCheckFailureImpl() should crash
   *
   * @param enable true if onCheckFailureImpl() should log the error and crash,
   * false if onCheckFailureImpl() should only log the error
   */
  inline void setFatalErrorOnCheckFailure(bool enable) {
    mShouldCheckCrash = enable;
  }

  /**
   * Provides a runtime configurable way to call/skip FATAL_ERROR
   * to prevent crashing on programming errors that are low visibility
   * to users
   *
   * Also provides a counter to log the occurrence of each type of defined
   * HealthCheckId
   *
   * @param condition Boolean expression which evaluates to false in the failure
   *        case
   * @param id predefined HealthCheckId used to record occurrence of each
   * failure
   */
  static inline void check(bool condition, HealthCheckId id) {
    if (!condition) {
      SystemHealthMonitor::onFailure(id);
    }
  }

  /**
   * Similar to check() but should be called when HealthCheck has already failed
   *
   * @param id predefined HealthCheckId used to record occurrence of each
   * failure
   */
  static void onFailure(HealthCheckId id);

 private:
  bool mShouldCheckCrash = false;

  /**
   * Records how many times a check failed on a HealthCheckId
   */
  uint16_t mCheckIdOccurrenceCounter[asBaseType(HealthCheckId::NumCheckIds)];

  /**
   * Implements the logic once check encountered a false condition
   * This is needed to prevent the runtime overhead when calling a function
   * when it is not necessary while also have the ability to modify object
   * member
   *
   *  @param id which HealthCheckId that matches this failure
   */
  void onCheckFailureImpl(HealthCheckId id);
};

}  // namespace chre

#endif  // CHRE_CORE_SYSTEM_HEALTH_MONITOR_H_
