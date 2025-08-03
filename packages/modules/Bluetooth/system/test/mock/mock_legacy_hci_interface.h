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

#include <gmock/gmock.h>

#include "stack/include/hcimsgs.h"

namespace bluetooth::legacy::hci::testing {
class MockInterface : public Interface {
 public:
  MOCK_METHOD(void, Disconnect, (uint16_t handle, uint8_t reason), (const));
  MOCK_METHOD(void, ChangeConnectionPacketType,
              (uint16_t handle, uint16_t packet_types), (const));
  MOCK_METHOD(void, StartRoleSwitch, (const RawAddress& bd_addr, uint8_t role),
              (const));
  MOCK_METHOD(void, ConfigureDataPath,
              (hci_data_direction_t data_path_direction, uint8_t data_path_id,
               std::vector<uint8_t> vendor_config),
              (const));
};
void SetMock(const MockInterface& interface);
}  // namespace bluetooth::legacy::hci::testing
