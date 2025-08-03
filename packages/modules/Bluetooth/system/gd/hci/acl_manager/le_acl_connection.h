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

#include <memory>
#include <variant>

#include "hci/acl_manager/acl_connection.h"
#include "hci/acl_manager/le_connection_management_callbacks.h"
#include "hci/address_with_type.h"
#include "hci/hci_packets.h"
#include "hci/le_acl_connection_interface.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

struct DataAsCentral {
  // the address used when initiating the connection
  AddressWithType local_address;
};

struct DataAsPeripheral {
  // the address of the advertising set that the peer connected to
  AddressWithType local_address;
  // the advertising set ID that the peer connected to - in LE, our role is peripheral iff the peer
  // initiated a connection to our advertisement
  std::optional<uint8_t> advertising_set_id;
  // whether the peripheral connected to a discoverable advertisement (this affects the readability
  // of GAP characteristics)
  bool connected_to_discoverable;
};

// when we know it's a peripheral, but we don't yet have all the data about the set it connected to
// this state should never remain after the connection is fully populated
struct DataAsUninitializedPeripheral {};

using RoleSpecificData =
    std::variant<DataAsUninitializedPeripheral, DataAsCentral, DataAsPeripheral>;

class LeAclConnection : public AclConnection {
 public:
  LeAclConnection();
  LeAclConnection(
      std::shared_ptr<Queue> queue,
      LeAclConnectionInterface* le_acl_connection_interface,
      uint16_t handle,
      RoleSpecificData role_specific_data,
      AddressWithType remote_address);
  LeAclConnection(const LeAclConnection&) = delete;
  LeAclConnection& operator=(const LeAclConnection&) = delete;

  ~LeAclConnection();

  virtual AddressWithType GetLocalAddress() const;

  virtual Role GetRole() const;

  const RoleSpecificData& GetRoleSpecificData() const;

  void UpdateRoleSpecificData(RoleSpecificData role_specific_data) {
    role_specific_data_ = role_specific_data;
  }

  virtual AddressWithType GetRemoteAddress() const {
    return remote_address_;
  }

  // The peer address and type returned from the Connection Complete Event
  AddressWithType peer_address_with_type_;

  // 5.2::7.7.65.10 Connection interval used on this connection.
  // Range: 0x0006 to 0x0C80
  // Time = N * 1.25 ms
  // Time Range: 7.5 ms to 4000 ms.
  uint16_t interval_;
  // 5.2::7.7.65.10 Peripheral latency for the connection in number of connection events.
  // Range: 0x0000 to 0x01F3
  uint16_t latency_;
  // 5.2::7.7.65.10 Connection supervision timeout.
  // Range: 0x000A to 0x0C80
  // Time = N * 10 ms
  // Time Range: 100 ms to 32 s
  uint16_t supervision_timeout_;

  // True if connection address was in the filter accept list, false otherwise
  bool in_filter_accept_list_;
  bool IsInFilterAcceptList() const {
    return in_filter_accept_list_;
  }

  Address local_resolvable_private_address_ = Address::kEmpty;
  Address peer_resolvable_private_address_ = Address::kEmpty;

  virtual AddressWithType GetPeerAddress() const {
    return peer_address_with_type_;
  }

  // This function return actual peer address which was used for the connection over the air.
  virtual AddressWithType GetPeerOtaAddress() const {
    if (peer_resolvable_private_address_ == Address::kEmpty) {
      return GetPeerAddress();
    }
    return AddressWithType(peer_resolvable_private_address_, AddressType::RANDOM_DEVICE_ADDRESS);
  }

  // This function return actual local address which was used for the connection over the air.
  virtual AddressWithType GetLocalOtaAddress() const {
    if (local_resolvable_private_address_ == Address::kEmpty) {
      return GetLocalAddress();
    }

    return AddressWithType(local_resolvable_private_address_, AddressType::RANDOM_DEVICE_ADDRESS);
  }

  virtual void RegisterCallbacks(LeConnectionManagementCallbacks* callbacks, os::Handler* handler);
  virtual void Disconnect(DisconnectReason reason);

  virtual bool LeConnectionUpdate(
      uint16_t conn_interval_min,
      uint16_t conn_interval_max,
      uint16_t conn_latency,
      uint16_t supervision_timeout,
      uint16_t min_ce_length,
      uint16_t max_ce_length);

  virtual bool ReadRemoteVersionInformation() override;
  virtual bool LeReadRemoteFeatures();

  virtual void LeSubrateRequest(
      uint16_t subrate_min, uint16_t subrate_max, uint16_t max_latency, uint16_t cont_num, uint16_t sup_tout);

  // TODO implement LeRemoteConnectionParameterRequestReply, LeRemoteConnectionParameterRequestNegativeReply

  // Called once before passing the connection to the client
  virtual LeConnectionManagementCallbacks* GetEventCallbacks(std::function<void(uint16_t)> invalidate_callbacks);

 protected:
  AddressWithType remote_address_;
  RoleSpecificData role_specific_data_;

 private:
  void OnLeSubrateRequestStatus(CommandStatusView status);
  virtual bool check_connection_parameters(
      uint16_t conn_interval_min,
      uint16_t conn_interval_max,
      uint16_t expected_conn_latency,
      uint16_t expected_supervision_timeout);
  struct impl;
  struct impl* pimpl_ = nullptr;
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
