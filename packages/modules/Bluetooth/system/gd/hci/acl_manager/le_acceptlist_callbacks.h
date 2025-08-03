/*
 * Copyright 2020 The Android Open Source Project
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

#include "hci/address_with_type.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

/// @brief These are callbacks needed to track the state of the acceptlist, used by the
/// Rust connection manager.
class LeAcceptlistCallbacks {
 public:
  virtual ~LeAcceptlistCallbacks() = default;
  // Invoked when controller sends Connection Complete event with Success error code
  // AddressWithType is the address returned by the controller.
  virtual void OnLeConnectSuccess(AddressWithType) = 0;
  // Invoked when controller sends Connection Complete event with failing error code
  // AddressWithType is the address returned by the controller.
  virtual void OnLeConnectFail(AddressWithType, ErrorCode reason) = 0;
  // Invoked when an LE connection is disconnected. AddressWithType is the remote address
  // associated with this connection at the time of connection.
  virtual void OnLeDisconnection(AddressWithType) = 0;
  // Invoked when the resolving list has changed, so we need to re-resolve our addresses.
  virtual void OnResolvingListChange() = 0;
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
