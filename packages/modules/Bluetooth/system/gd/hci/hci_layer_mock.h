/*
 * Copyright 2021 The Android Open Source Project
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

#include "common/contextual_callback.h"
#include "hci/address.h"
#include "hci/hci_layer.h"
#include "hci/hci_packets.h"
#include "module.h"
#include "os/handler.h"

// Unit test interfaces
namespace bluetooth {
namespace hci {
namespace testing {

class MockHciLayer : public HciLayer {
 public:
  MOCK_METHOD(
      void,
      EnqueueCommand,
      (std::unique_ptr<CommandBuilder>, common::ContextualOnceCallback<void(CommandCompleteView)>),
      (override));
  MOCK_METHOD(
      void,
      EnqueueCommand,
      (std::unique_ptr<CommandBuilder>, common::ContextualOnceCallback<void(CommandStatusView)>),
      (override));
  MOCK_METHOD((common::BidiQueueEnd<AclBuilder, AclView>*), GetAclQueueEnd, (), (override));
  MOCK_METHOD((common::BidiQueueEnd<ScoBuilder, ScoView>*), GetScoQueueEnd, (), (override));
  MOCK_METHOD((common::BidiQueueEnd<IsoBuilder, IsoView>*), GetIsoQueueEnd, (), (override));
  MOCK_METHOD(
      (void),
      RegisterEventHandler,
      (EventCode, common::ContextualCallback<void(EventView)>),
      (override));
  MOCK_METHOD((void), UnregisterEventHandler, (EventCode), (override));
  MOCK_METHOD(
      (void),
      RegisterLeEventHandler,
      (SubeventCode, common::ContextualCallback<void(LeMetaEventView)>),
      (override));
  MOCK_METHOD((void), UnregisterLeEventHandler, (SubeventCode), (override));
  MOCK_METHOD(
      (SecurityInterface*),
      GetSecurityInterface,
      (common::ContextualCallback<void(EventView)> event_handler),
      (override));

  MOCK_METHOD(
      (LeSecurityInterface*),
      GetLeSecurityInterface,
      (common::ContextualCallback<void(LeMetaEventView)> event_handler),
      (override));

  MOCK_METHOD(
      (AclConnectionInterface*),
      GetAclConnectionInterface,
      (common::ContextualCallback<void(EventView)> event_handler,
       common::ContextualCallback<void(uint16_t, hci::ErrorCode)> on_disconnect,
       common::ContextualCallback<void(Address, ClassOfDevice)> on_connection_request,
       common::ContextualCallback<void(hci::ErrorCode, uint16_t, uint8_t, uint16_t, uint16_t)>
           on_read_remote_version_complete),
      (override));
  MOCK_METHOD((void), PutAclConnectionInterface, (), (override));

  MOCK_METHOD(
      (LeAclConnectionInterface*),
      GetLeAclConnectionInterface,
      (common::ContextualCallback<void(LeMetaEventView)> event_handler,
       common::ContextualCallback<void(uint16_t, hci::ErrorCode)> on_disconnect,
       common::ContextualCallback<void(hci::ErrorCode, uint16_t, uint8_t, uint16_t, uint16_t)>
           on_read_remote_version_complete),
      (override));
  MOCK_METHOD((void), PutLeAclConnectionInterface, (), (override));

  MOCK_METHOD(
      (LeAdvertisingInterface*),
      GetLeAdvertisingInterface,
      (common::ContextualCallback<void(LeMetaEventView)> event_handler),
      (override));

  MOCK_METHOD(
      (LeScanningInterface*),
      GetLeScanningInterface,
      (common::ContextualCallback<void(LeMetaEventView)> event_handler),
      (override));

  MOCK_METHOD(
      (LeIsoInterface*),
      GetLeIsoInterface,
      (common::ContextualCallback<void(LeMetaEventView)> event_handler),
      (override));

  MOCK_METHOD(
      (DistanceMeasurementInterface*),
      GetDistanceMeasurementInterface,
      (common::ContextualCallback<void(LeMetaEventView)> event_handler),
      (override));
};

}  // namespace testing
}  // namespace hci
}  // namespace bluetooth
