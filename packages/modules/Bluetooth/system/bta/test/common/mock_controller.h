/*
 * Copyright 2021 HIMSA II K/S - www.himsa.dk.
 * Represented by EHIMA - www.ehima.com
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

#include <base/functional/callback.h>
#include <gmock/gmock.h>

namespace controller {
class ControllerInterface {
 public:
  virtual uint8_t GetIsoBufferCount(void) = 0;
  virtual uint16_t GetIsoDataSize(void) = 0;
  virtual bool SupportsBleConnectedIsochronousStreamCentral(void) = 0;
  virtual bool SupportsBleConnectedIsochronousStreamPeripheral(void) = 0;
  virtual bool SupportsBleIsochronousBroadcaster(void) = 0;
  virtual bool SupportsBle2mPhy(void) = 0;
  virtual bool SupportsConfigureDataPath(void) = 0;

  virtual ~ControllerInterface() = default;
};

class MockControllerInterface : public ControllerInterface {
 public:
  MOCK_METHOD((uint8_t), GetIsoBufferCount, (), (override));
  MOCK_METHOD((uint16_t), GetIsoDataSize, (), (override));
  MOCK_METHOD((bool), SupportsBleConnectedIsochronousStreamCentral, (),
              (override));
  MOCK_METHOD((bool), SupportsBleConnectedIsochronousStreamPeripheral, (),
              (override));
  MOCK_METHOD((bool), SupportsBleIsochronousBroadcaster, (), (override));
  MOCK_METHOD((bool), SupportsBle2mPhy, (), (override));
  MOCK_METHOD((bool), SupportsConfigureDataPath, (), (override));
};

void SetMockControllerInterface(
    MockControllerInterface* mock_controller_interface);
}  // namespace controller
