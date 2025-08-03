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

#include "hci/acl_manager/connection_callbacks.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

class MockConnectionCallback : public ConnectionCallbacks {
 public:
  MOCK_METHOD(
      void, OnConnectSuccess, (std::unique_ptr<ClassicAclConnection> connection), (override));
  MOCK_METHOD(void, OnConnectRequest, (Address, ClassOfDevice), (override));
  MOCK_METHOD(void, OnConnectFail, (Address, ErrorCode reason, bool locally_initiated), (override));
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
