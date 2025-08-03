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

#include <gmock/gmock.h>

#include <memory>

#include "hci/address.h"
#include "hci/distance_measurement_manager.h"

// Unit test interfaces
namespace bluetooth {
namespace hci {

struct DistanceMeasurementManager::impl : public bluetooth::hci::LeAddressManagerCallback {};

namespace testing {

class MockDistanceMeasurementCallbacks : public DistanceMeasurementCallbacks {
  MOCK_METHOD(void, OnDistanceMeasurementStarted, (Address, DistanceMeasurementMethod));
  MOCK_METHOD(
      void,
      OnDistanceMeasurementStartFail,
      (Address, DistanceMeasurementErrorCode, DistanceMeasurementMethod));
  MOCK_METHOD(
      void,
      OnDistanceMeasurementStopped,
      (Address, DistanceMeasurementErrorCode, DistanceMeasurementMethod));
  MOCK_METHOD(
      void,
      OnDistanceMeasurementResult,
      (Address, uint32_t, uint32_t, int, int, int, int, DistanceMeasurementMethod));
};

class MockDistanceMeasurementManager : public DistanceMeasurementManager {
 public:
  MOCK_METHOD(void, RegisterDistanceMeasurementCallbacks, (DistanceMeasurementCallbacks*));
  MOCK_METHOD(void, StartDistanceMeasurement, (Address, uint16_t, DistanceMeasurementMethod));
};

}  // namespace testing
}  // namespace hci
}  // namespace bluetooth
