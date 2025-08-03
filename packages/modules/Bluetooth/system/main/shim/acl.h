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

#include <future>
#include <memory>

#include "hci/acl_manager/connection_callbacks.h"
#include "hci/acl_manager/le_connection_callbacks.h"
#include "hci/address.h"
#include "hci/address_with_type.h"
#include "hci/class_of_device.h"
#include "main/shim/acl_legacy_interface.h"
#include "main/shim/link_connection_interface.h"
#include "main/shim/link_policy_interface.h"
#include "os/handler.h"
#include "packet/raw_builder.h"
#include "types/raw_address.h"

using LeRandCallback = base::OnceCallback<void(uint64_t)>;

namespace bluetooth {
namespace shim {
namespace legacy {

class Acl : public hci::acl_manager::ConnectionCallbacks,
            public hci::acl_manager::LeConnectionCallbacks,
            public LinkConnectionInterface,
            public LinkPolicyInterface {
 public:
  Acl(os::Handler* handler, const acl_interface_t& acl_interface,
      uint8_t max_acceptlist_size, uint8_t max_address_resolution_size);

  Acl(const Acl&) = delete;
  Acl& operator=(const Acl&) = delete;

  ~Acl();

  // hci::acl_manager::ConnectionCallbacks
  void OnConnectSuccess(
      std::unique_ptr<hci::acl_manager::ClassicAclConnection>) override;
  void OnConnectRequest(hci::Address, hci::ClassOfDevice) override;
  void OnConnectFail(hci::Address, hci::ErrorCode reason,
                     bool locally_initiated) override;

  void OnClassicLinkDisconnected(uint16_t handle, hci::ErrorCode reason);

  // hci::acl_manager::LeConnectionCallbacks
  void OnLeConnectSuccess(
      hci::AddressWithType,
      std::unique_ptr<hci::acl_manager::LeAclConnection>) override;
  void OnLeConnectFail(hci::AddressWithType, hci::ErrorCode reason) override;
  void OnLeLinkDisconnected(uint16_t handle, hci::ErrorCode reason);
  bluetooth::hci::AddressWithType GetConnectionLocalAddress(uint16_t handle,
                                                            bool ota_address);
  bluetooth::hci::AddressWithType GetConnectionPeerAddress(uint16_t handle,
                                                           bool ota_address);
  std::optional<uint8_t> GetAdvertisingSetConnectedTo(
      const RawAddress& remote_bda);

  // LinkConnectionInterface
  void CreateClassicConnection(const hci::Address& address) override;
  void CancelClassicConnection(const hci::Address& address) override;
  void AcceptLeConnectionFrom(const hci::AddressWithType& address_with_type,
                              bool is_direct,
                              std::promise<bool> promise) override;
  void IgnoreLeConnectionFrom(
      const hci::AddressWithType& address_with_type) override;
  void DisconnectClassic(uint16_t handle, tHCI_REASON reason,
                         std::string comment) override;
  void DisconnectLe(uint16_t handle, tHCI_REASON reason,
                    std::string comment) override;

  // Address Resolution List
  void AddToAddressResolution(const hci::AddressWithType& address_with_type,
                              const std::array<uint8_t, 16>& peer_irk,
                              const std::array<uint8_t, 16>& local_irk);
  void RemoveFromAddressResolution(
      const hci::AddressWithType& address_with_type);
  void ClearAddressResolution();

  // LinkPolicyInterface
  bool HoldMode(uint16_t hci_handle, uint16_t max_interval,
                uint16_t min_interval) override;
  bool SniffMode(uint16_t hci_handle, uint16_t max_interval,
                 uint16_t min_interval, uint16_t attempt,
                 uint16_t timeout) override;
  bool ExitSniffMode(uint16_t hci_handle) override;
  bool SniffSubrating(uint16_t hci_handle, uint16_t maximum_latency,
                      uint16_t minimum_remote_timeout,
                      uint16_t minimum_local_timeout) override;
  void LeSetDefaultSubrate(uint16_t subrate_min, uint16_t subrate_max,
                           uint16_t max_latency, uint16_t cont_num,
                           uint16_t sup_tout);
  void LeSubrateRequest(uint16_t hci_handle, uint16_t subrate_min,
                        uint16_t subrate_max, uint16_t max_latency,
                        uint16_t cont_num, uint16_t sup_tout);

  void WriteData(uint16_t hci_handle,
                 std::unique_ptr<packet::RawBuilder> packet);

  void Dump(int fd) const;
  void DumpConnectionHistory(int fd) const;

  void Shutdown();
  void FinalShutdown();

  void ClearFilterAcceptList();
  void DisconnectAllForSuspend();
  void LeRand(LeRandCallback cb);
  void SetSystemSuspendState(bool suspended);

 protected:
  void on_incoming_acl_credits(uint16_t handle, uint16_t credits);
  void write_data_sync(uint16_t hci_handle,
                       std::unique_ptr<packet::RawBuilder> packet);

 private:
  os::Handler* handler_;
  const acl_interface_t acl_interface_;

  bool CheckForOrphanedAclConnections() const;

  struct impl;
  std::unique_ptr<impl> pimpl_;
};

}  // namespace legacy
}  // namespace shim
}  // namespace bluetooth
