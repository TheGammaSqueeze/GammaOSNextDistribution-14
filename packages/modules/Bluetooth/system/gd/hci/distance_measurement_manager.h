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
#pragma once

#include "address.h"
#include "module.h"

namespace bluetooth {
namespace hci {

enum DistanceMeasurementMethod {
  METHOD_AUTO,
  METHOD_RSSI,
  METHOD_CS,
};

enum DistanceMeasurementErrorCode {
  REASON_FEATURE_NOT_SUPPORTED_LOCAL,
  REASON_FEATURE_NOT_SUPPORTED_REMOTE,
  REASON_LOCAL_REQUEST,
  REASON_REMOTE_REQUEST,
  REASON_DURATION_TIMEOUT,
  REASON_NO_LE_CONNECTION,
  REASON_INVALID_PARAMETERS,
  REASON_INTERNAL_ERROR,
};

struct DistanceMeasurementResult {
  Address address;
  uint32_t centimeter;
  uint32_t error_centimeter;
  DistanceMeasurementMethod method;
};

class DistanceMeasurementCallbacks {
 public:
  virtual ~DistanceMeasurementCallbacks() = default;
  virtual void OnDistanceMeasurementStarted(Address address, DistanceMeasurementMethod method) = 0;
  virtual void OnDistanceMeasurementStartFail(
      Address address, DistanceMeasurementErrorCode reason, DistanceMeasurementMethod method) = 0;
  virtual void OnDistanceMeasurementStopped(
      Address address, DistanceMeasurementErrorCode reason, DistanceMeasurementMethod method) = 0;
  virtual void OnDistanceMeasurementResult(
      Address address,
      uint32_t centimeter,
      uint32_t error_centimeter,
      int azimuth_angle,
      int error_azimuth_angle,
      int altitude_angle,
      int error_altitude_angle,
      DistanceMeasurementMethod method) = 0;
};

class DistanceMeasurementManager : public bluetooth::Module {
 public:
  DistanceMeasurementManager();
  ~DistanceMeasurementManager();
  DistanceMeasurementManager(const DistanceMeasurementManager&) = delete;
  DistanceMeasurementManager& operator=(const DistanceMeasurementManager&) = delete;

  void RegisterDistanceMeasurementCallbacks(DistanceMeasurementCallbacks* callbacks);
  void StartDistanceMeasurement(
      const Address&, uint16_t interval, DistanceMeasurementMethod method);
  void StopDistanceMeasurement(const Address& address, DistanceMeasurementMethod method);

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
