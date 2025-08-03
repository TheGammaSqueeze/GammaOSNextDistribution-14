/*
 * Copyright 2019 The Android Open Source Project
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

#include "hci/acl_manager.h"
#include "hci/acl_manager/classic_acl_connection.h"
#include "hci/acl_manager/connection_callbacks.h"
#include "hci/acl_manager/connection_management_callbacks.h"
#include "hci/acl_manager/le_acl_connection.h"
#include "hci/acl_manager/le_connection_callbacks.h"
#include "hci/acl_manager/le_connection_management_callbacks.h"

#include <gmock/gmock.h>

// Unit test interfaces
namespace bluetooth {
namespace hci {
namespace testing {

using acl_manager::LeAclConnection;
using acl_manager::LeConnectionCallbacks;
using acl_manager::LeConnectionManagementCallbacks;

using acl_manager::ClassicAclConnection;
using acl_manager::ConnectionCallbacks;
using acl_manager::ConnectionManagementCallbacks;

class MockClassicAclConnection : public ClassicAclConnection {
 public:
  MOCK_METHOD(Address, GetAddress, (), (const, override));
  MOCK_METHOD(bool, Disconnect, (DisconnectReason reason), (override));
  MOCK_METHOD(void, RegisterCallbacks, (ConnectionManagementCallbacks * callbacks, os::Handler* handler), (override));
  MOCK_METHOD(bool, ReadRemoteVersionInformation, (), (override));
  MOCK_METHOD(bool, ReadRemoteSupportedFeatures, (), (override));
  MOCK_METHOD(bool, ReadRemoteExtendedFeatures, (uint8_t), (override));

  QueueUpEnd* GetAclQueueEnd() const override {
    return acl_queue_.GetUpEnd();
  }
  mutable common::BidiQueue<PacketView<kLittleEndian>, BasePacketBuilder> acl_queue_{10};
};

class MockLeAclConnection : public LeAclConnection {
 public:
  MOCK_METHOD(AddressWithType, GetLocalAddress, (), (const, override));
  MOCK_METHOD(AddressWithType, GetRemoteAddress, (), (const, override));
  MOCK_METHOD(void, Disconnect, (DisconnectReason reason), (override));
  MOCK_METHOD(void, RegisterCallbacks, (LeConnectionManagementCallbacks * callbacks, os::Handler* handler), (override));
  MOCK_METHOD(bool, ReadRemoteVersionInformation, (), (override));

  QueueUpEnd* GetAclQueueEnd() const override {
    return acl_queue_.GetUpEnd();
  }
  mutable common::BidiQueue<PacketView<kLittleEndian>, BasePacketBuilder> acl_queue_{10};
};

class MockAclManager : public AclManager {
 public:
  MOCK_METHOD(void, RegisterCallbacks, (ConnectionCallbacks * callbacks, os::Handler* handler), (override));
  MOCK_METHOD(void, RegisterLeCallbacks, (LeConnectionCallbacks * callbacks, os::Handler* handler), (override));
  MOCK_METHOD(void, CreateConnection, (Address address), (override));
  MOCK_METHOD(void, CreateLeConnection, (AddressWithType address_with_type, bool is_direct), (override));
  MOCK_METHOD(void, CancelConnect, (Address address), (override));
  MOCK_METHOD(
      void,
      SetPrivacyPolicyForInitiatorAddress,
      (LeAddressManager::AddressPolicy address_policy,
       AddressWithType fixed_address,
       std::chrono::milliseconds minimum_rotation_time,
       std::chrono::milliseconds maximum_rotation_time),
      (override));
};

}  // namespace testing
}  // namespace hci
}  // namespace bluetooth
