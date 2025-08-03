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

#include <array>
#include <cstdint>

#include "hci/acl_manager/connection_management_callbacks.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

class MockConnectionManagementCallbacks : public ConnectionManagementCallbacks {
 public:
  MOCK_METHOD(void, OnConnectionPacketTypeChanged, (uint16_t packet_type), (override));
  MOCK_METHOD(void, OnAuthenticationComplete, (ErrorCode hci_status), (override));
  MOCK_METHOD(void, OnEncryptionChange, (EncryptionEnabled enabled)), (override);
  MOCK_METHOD(void, OnChangeConnectionLinkKeyComplete, (), (override));
  MOCK_METHOD(void, OnReadClockOffsetComplete, (uint16_t clock_offse), (override));
  MOCK_METHOD(
      void, OnModeChange, (ErrorCode status, Mode current_mode, uint16_t interval), (override));
  MOCK_METHOD(
      void,
      OnSniffSubrating,
      (ErrorCode status,
       uint16_t maximum_transmit_latency,
       uint16_t maximum_receive_latency,
       uint16_t minimum_remote_timeout,
       uint16_t minimum_local_timeout),
      (override));
  MOCK_METHOD(
      void,
      OnQosSetupComplete,
      (ServiceType service_type,
       uint32_t token_rate,
       uint32_t peak_bandwidth,
       uint32_t latency,
       uint32_t delay_variation),
      (override));
  MOCK_METHOD(
      void,
      OnFlowSpecificationComplete,
      (FlowDirection flow_direction,
       ServiceType service_type,
       uint32_t token_rate,
       uint32_t token_bucket_size,
       uint32_t peak_bandwidth,
       uint32_t access_latency),
      (override));
  MOCK_METHOD(void, OnFlushOccurred, (), (override));
  MOCK_METHOD(void, OnRoleDiscoveryComplete, (Role current_role), (override));
  MOCK_METHOD(void, OnReadLinkPolicySettingsComplete, (uint16_t link_policy_settings), (override));
  MOCK_METHOD(void, OnReadAutomaticFlushTimeoutComplete, (uint16_t flush_timeout), (override));
  MOCK_METHOD(void, OnReadTransmitPowerLevelComplete, (uint8_t transmit_power_level), (override));
  MOCK_METHOD(
      void, OnReadLinkSupervisionTimeoutComplete, (uint16_t link_supervision_timeout), (override));
  MOCK_METHOD(
      void, OnReadFailedContactCounterComplete, (uint16_t failed_contact_counter), (override));
  MOCK_METHOD(void, OnReadLinkQualityComplete, (uint8_t link_quality), (override));
  MOCK_METHOD(
      void,
      OnReadAfhChannelMapComplete,
      (AfhMode afh_mode, (std::array<uint8_t, 10>)afh_channel_map),
      (override));
  MOCK_METHOD(void, OnReadRssiComplete, (uint8_t rssi), (override));
  MOCK_METHOD(void, OnReadClockComplete, (uint32_t clock, uint16_t accuracy), (override));
  MOCK_METHOD(void, OnCentralLinkKeyComplete, (KeyFlag flag), (override));
  MOCK_METHOD(void, OnRoleChange, (ErrorCode hci_status, Role new_role), (override));
  MOCK_METHOD(void, OnDisconnection, (ErrorCode reason), (override));
  MOCK_METHOD(
      void,
      OnReadRemoteVersionInformationComplete,
      (ErrorCode hci_status, uint8_t lmp_version, uint16_t manufacturer_name, uint16_t sub_version),
      (override));
  MOCK_METHOD(void, OnReadRemoteSupportedFeaturesComplete, (uint64_t features), (override));
  MOCK_METHOD(
      void,
      OnReadRemoteExtendedFeaturesComplete,
      (uint8_t page_number, uint8_t max_page_number, uint64_t features),
      (override));
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
