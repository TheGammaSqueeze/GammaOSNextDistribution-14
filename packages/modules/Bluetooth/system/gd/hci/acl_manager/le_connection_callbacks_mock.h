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

#include <memory>

#include "hci/acl_manager/le_acl_connection.h"
#include "hci/acl_manager/le_connection_callbacks.h"
#include "hci/address_with_type.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

class MockLeConnectionCallbacks : public LeConnectionCallbacks {
 public:
  MOCK_METHOD(
      void,
      OnLeConnectSuccess,
      (AddressWithType address_with_type, std::unique_ptr<LeAclConnection> connection),
      (override));
  MOCK_METHOD(
      void, OnLeConnectFail, (AddressWithType address_with_type, ErrorCode reason), (override));
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
