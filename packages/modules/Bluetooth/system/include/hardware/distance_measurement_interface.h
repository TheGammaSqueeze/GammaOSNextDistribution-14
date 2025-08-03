/*
 * Copyright 2022 The Android Open Source Project
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

#ifndef ANDROID_INCLUDE_DISTANCE_MEASUREMENT_INTERFACE_H
#define ANDROID_INCLUDE_DISTANCE_MEASUREMENT_INTERFACE_H

#include <raw_address.h>

/**
 * Distance measurement callbacks related callbacks invoked from from the
 * Bluetooth native stack All callbacks are invoked on the JNI thread
 */
class DistanceMeasurementCallbacks {
 public:
  virtual ~DistanceMeasurementCallbacks() = default;
  virtual void OnDistanceMeasurementStarted(RawAddress address,
                                            uint8_t method) = 0;
  virtual void OnDistanceMeasurementStartFail(RawAddress address,
                                              uint8_t reason,
                                              uint8_t method) = 0;
  virtual void OnDistanceMeasurementStopped(RawAddress address, uint8_t reason,
                                            uint8_t method) = 0;
  virtual void OnDistanceMeasurementResult(
      RawAddress address, uint32_t centimeter, uint32_t error_centimeter,
      int azimuth_angle, int error_azimuth_angle, int altitude_angle,
      int error_altitude_angle, uint8_t method) = 0;
};

class DistanceMeasurementInterface {
 public:
  virtual ~DistanceMeasurementInterface() = default;
  virtual void RegisterDistanceMeasurementCallbacks(
      DistanceMeasurementCallbacks* callbacks) = 0;
  virtual void StartDistanceMeasurement(RawAddress raw_address,
                                        uint16_t interval, uint8_t method) = 0;
  virtual void StopDistanceMeasurement(RawAddress raw_address,
                                       uint8_t method) = 0;
};

#endif /* ANDROID_INCLUDE_DISTANCE_MEASUREMENT_INTERFACE_H */
