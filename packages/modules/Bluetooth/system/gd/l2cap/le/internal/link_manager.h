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

#include <memory>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include "hci/acl_manager/le_acl_connection.h"
#include "hci/address.h"
#include "hci/address_with_type.h"
#include "hci/le_advertising_manager.h"
#include "l2cap/internal/parameter_provider.h"
#include "l2cap/internal/scheduler.h"
#include "l2cap/le/fixed_channel_manager.h"
#include "l2cap/le/internal/dynamic_channel_service_manager_impl.h"
#include "l2cap/le/internal/fixed_channel_service_manager_impl.h"
#include "l2cap/le/internal/link.h"
#include "l2cap/le/link_property_listener.h"
#include "os/handler.h"

namespace bluetooth {
namespace l2cap {
namespace le {
namespace internal {

class LinkManager : public hci::acl_manager::LeConnectionCallbacks {
 public:
  LinkManager(
      os::Handler* l2cap_handler,
      hci::AclManager* acl_manager,
      FixedChannelServiceManagerImpl* service_manager,
      DynamicChannelServiceManagerImpl* dynamic_service_manager,
      l2cap::internal::ParameterProvider* parameter_provider)
      : l2cap_handler_(l2cap_handler),
        acl_manager_(acl_manager),
        fixed_channel_service_manager_(service_manager),
        dynamic_channel_service_manager_(dynamic_service_manager),
        parameter_provider_(parameter_provider) {
    acl_manager_->RegisterLeCallbacks(this, l2cap_handler_);
  }

  LinkManager(const LinkManager&) = delete;
  LinkManager& operator=(const LinkManager&) = delete;

  struct PendingFixedChannelConnection {
    os::Handler* handler_;
    FixedChannelManager::OnConnectionFailureCallback on_fail_callback_;
  };

  struct PendingLink {
    std::vector<PendingFixedChannelConnection> pending_fixed_channel_connections_;
  };

  // ACL methods

  Link* GetLink(hci::AddressWithType address_with_type);
  void OnLeConnectSuccess(hci::AddressWithType connecting_address_with_type,
                          std::unique_ptr<hci::acl_manager::LeAclConnection> acl_connection) override;
  void OnLeConnectFail(hci::AddressWithType address_with_type, hci::ErrorCode reason) override;

  // FixedChannelManager methods

  void ConnectFixedChannelServices(hci::AddressWithType address_with_type,
                                   PendingFixedChannelConnection pending_fixed_channel_connection);

  // DynamicChannelManager methods

  void ConnectDynamicChannelServices(hci::AddressWithType device,
                                     Link::PendingDynamicChannelConnection pending_dynamic_channel_connection, Psm psm);

  void OnDisconnect(hci::AddressWithType address_with_type);

  // Link methods

  void RegisterLinkPropertyListener(os::Handler* handler, LinkPropertyListener* listener);

  void OnReadRemoteVersionInformationComplete(
      hci::ErrorCode hci_status,
      hci::AddressWithType address_with_type,
      uint8_t lmp_version,
      uint16_t manufacturer_name,
      uint16_t sub_version);

  // Reported by link to indicate how many pending packets are remaining to be set.
  // If there is anything outstanding, don't delete link
  void OnPendingPacketChange(hci::AddressWithType remote, int num_packets);

 private:
  // Dependencies
  os::Handler* l2cap_handler_;
  hci::AclManager* acl_manager_;
  FixedChannelServiceManagerImpl* fixed_channel_service_manager_;
  DynamicChannelServiceManagerImpl* dynamic_channel_service_manager_;
  l2cap::internal::ParameterProvider* parameter_provider_;

  // Internal states
  std::unordered_map<hci::AddressWithType, PendingLink> pending_links_;
  std::unordered_map<hci::AddressWithType, Link> links_;
  std::unordered_map<hci::AddressWithType, std::list<std::pair<Psm, Link::PendingDynamicChannelConnection>>>
      pending_dynamic_channels_;
  os::Handler* link_property_callback_handler_ = nullptr;
  LinkPropertyListener* link_property_listener_ = nullptr;
  std::unordered_set<hci::AddressWithType> disconnected_links_;
  std::unordered_set<hci::AddressWithType> links_with_pending_packets_;
};

}  // namespace internal
}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
