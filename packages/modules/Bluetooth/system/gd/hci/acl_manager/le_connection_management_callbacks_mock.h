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

#include <cstdint>

#include "hci/acl_manager/le_connection_management_callbacks.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

class MockLeConnectionManagementCallbacks : public LeConnectionManagementCallbacks {
 public:
  MOCK_METHOD(
      void,
      OnConnectionUpdate,
      (hci::ErrorCode hci_status,
       uint16_t connection_interval,
       uint16_t connection_latency,
       uint16_t supervision_timeout),
      (override));
  MOCK_METHOD(
      void,
      OnDataLengthChange,
      (uint16_t tx_octets, uint16_t tx_time, uint16_t rx_octets, uint16_t rx_time),
      (override));
  MOCK_METHOD(void, OnDisconnection, (ErrorCode reason), (override));
  MOCK_METHOD(
      void,
      OnReadRemoteVersionInformationComplete,
      (hci::ErrorCode hci_status,
       uint8_t lmp_version,
       uint16_t manufacturer_name,
       uint16_t sub_version),
      (override));
  MOCK_METHOD(
      void,
      OnLeReadRemoteFeaturesComplete,
      (hci::ErrorCode hci_status, uint64_t features),
      (override));
  MOCK_METHOD(
      void, OnPhyUpdate, (hci::ErrorCode hci_status, uint8_t tx_phy, uint8_t rx_phy), (override));
  MOCK_METHOD(
      void,
      OnLeSubrateChange,
      (hci::ErrorCode hci_status,
       uint16_t subrate_factor,
       uint16_t peripheral_latency,
       uint16_t continuation_number,
       uint16_t supervision_timeout),
      (override));
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
