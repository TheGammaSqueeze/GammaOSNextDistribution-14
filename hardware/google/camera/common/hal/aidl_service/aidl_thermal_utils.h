/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_THERMAL_UTILS_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_THERMAL_UTILS_H_

#include <aidl/android/hardware/thermal/BnThermalChangedCallback.h>
#include <aidl/android/hardware/thermal/IThermal.h>

#include "thermal_types.h"

namespace android {
namespace hardware {
namespace aidl_thermal_utils {

// ThermalChangedCallback implements the AIDL thermal changed callback
// interface, IThermalChangedCallback, to be registered for thermal status
// change.
class ThermalChangedCallback
    : public aidl::android::hardware::thermal::BnThermalChangedCallback {
 public:
  explicit ThermalChangedCallback(
      google_camera_hal::NotifyThrottlingFunc notify_throttling);
  virtual ~ThermalChangedCallback() = default;

  // Override functions in HidlThermalChangedCallback.
  ndk::ScopedAStatus notifyThrottling(
      const aidl::android::hardware::thermal::Temperature& temperature) override;
  // End of override functions in HidlThermalChangedCallback.

 private:
  const google_camera_hal::NotifyThrottlingFunc notify_throttling_;

  status_t ConvertToHalTemperatureType(
      const aidl::android::hardware::thermal::TemperatureType&
          aidl_temperature_type,
      google_camera_hal::TemperatureType* hal_temperature_type);

  status_t ConvertToHalThrottlingSeverity(
      const aidl::android::hardware::thermal::ThrottlingSeverity&
          aidl_throttling_severity,
      google_camera_hal::ThrottlingSeverity* hal_throttling_severity);

  status_t ConvertToHalTemperature(
      const aidl::android::hardware::thermal::Temperature& aidl_temperature,
      google_camera_hal::Temperature* hal_temperature);
};

status_t ConvertToAidlTemperatureType(
    const google_camera_hal::TemperatureType& hal_temperature_type,
    aidl::android::hardware::thermal::TemperatureType* aidl_temperature_type);

}  // namespace aidl_thermal_utils
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_THERMAL_UTILS_H_