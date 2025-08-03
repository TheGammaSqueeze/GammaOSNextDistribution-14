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
#include <vector>

#include "hci/acl_manager/classic_acl_connection.h"
#include "hci/address.h"
#include "hci/class_of_device.h"
#include "l2cap/classic/dynamic_channel_manager.h"
#include "l2cap/classic/fixed_channel_manager.h"
#include "l2cap/classic/internal/dynamic_channel_service_manager_impl.h"
#include "l2cap/classic/internal/fixed_channel_service_manager_impl.h"
#include "l2cap/classic/internal/link.h"
#include "l2cap/classic/link_property_listener.h"
#include "l2cap/classic/link_security_interface.h"
#include "l2cap/internal/parameter_provider.h"
#include "l2cap/internal/scheduler.h"
#include "os/handler.h"

namespace bluetooth {
namespace l2cap {
namespace classic {
namespace internal {

class DumpsysHelper;

class LinkManager : public hci::acl_manager::ConnectionCallbacks {
 public:
  LinkManager(os::Handler* l2cap_handler, hci::AclManager* acl_manager,
              FixedChannelServiceManagerImpl* fixed_channel_service_manager,
              DynamicChannelServiceManagerImpl* dynamic_channel_service_manager,
              l2cap::internal::ParameterProvider* parameter_provider)
      : l2cap_handler_(l2cap_handler), acl_manager_(acl_manager),
        fixed_channel_service_manager_(fixed_channel_service_manager),
        dynamic_channel_service_manager_(dynamic_channel_service_manager), parameter_provider_(parameter_provider) {
    acl_manager_->RegisterCallbacks(this, l2cap_handler_);
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

  Link* GetLink(hci::Address device);
  void OnConnectSuccess(std::unique_ptr<hci::acl_manager::ClassicAclConnection> acl_connection) override;
  void OnConnectRequest(hci::Address, hci::ClassOfDevice) override;
  void OnConnectFail(hci::Address device, hci::ErrorCode reason, bool locally_initiated) override;

  virtual void OnDisconnect(hci::Address device, hci::ErrorCode status);
  void OnAuthenticationComplete(hci::ErrorCode hci_status, hci::Address device);
  void OnEncryptionChange(hci::Address device, hci::EncryptionEnabled enabled);
  void OnReadRemoteVersionInformation(
      hci::ErrorCode hci_status,
      hci::Address device,
      uint8_t lmp_version,
      uint16_t manufacturer_name,
      uint16_t sub_version);
  void OnReadRemoteSupportedFeatures(hci::Address device, uint64_t features);
  void OnReadRemoteExtendedFeatures(
      hci::Address device, uint8_t page_number, uint8_t max_page_number, uint64_t features);
  void OnRoleChange(hci::ErrorCode hci_status, hci::Address remote, hci::Role role);
  void OnReadClockOffset(hci::Address remote, uint16_t clock_offset);
  void OnModeChange(hci::ErrorCode hci_status, hci::Address remote, hci::Mode mode, uint16_t interval);
  void OnSniffSubrating(
      hci::ErrorCode hci_status,
      hci::Address remote,
      uint16_t max_tx_lat,
      uint16_t max_rx_lat,
      uint16_t min_remote_timeout,
      uint16_t min_local_timeout);

  // FixedChannelManager methods

  void ConnectFixedChannelServices(hci::Address device, PendingFixedChannelConnection pending_fixed_channel_connection);

  // DynamicChannelManager methods

  void ConnectDynamicChannelServices(
      hci::Address device, Link::PendingDynamicChannelConnection pending_connection, Psm psm);

  // For SecurityModule to initiate an ACL link
  void InitiateConnectionForSecurity(hci::Address remote);

  // LinkManager will handle sending OnLinkConnected() callback and construct a LinkSecurityInterface proxy.
  void RegisterLinkSecurityInterfaceListener(os::Handler* handler, LinkSecurityInterfaceListener* listener);

  // For the link to get LinkSecurityInterfaceListener
  LinkSecurityInterfaceListener* GetLinkSecurityInterfaceListener();

  // Registerlink callbacks
  void RegisterLinkPropertyListener(os::Handler* handler, LinkPropertyListener* listener);

  // Reported by link to indicate how many pending packets are remaining to be set.
  // If there is anything outstanding, don't delete link
  void OnPendingPacketChange(hci::Address remote, int num_packets);

 private:
  // Handles requests from LinkSecurityInterface
  friend class LinkSecurityInterfaceImpl;
  friend class DumpsysHelper;
  void handle_link_security_hold(hci::Address remote);
  void handle_link_security_release(hci::Address remote);
  void handle_link_security_disconnect(hci::Address remote);
  void handle_link_security_ensure_authenticated(hci::Address remote);
  void handle_link_security_ensure_encrypted(hci::Address remote);

  // Dependencies
  os::Handler* l2cap_handler_;
  hci::AclManager* acl_manager_;
  FixedChannelServiceManagerImpl* fixed_channel_service_manager_;
  DynamicChannelServiceManagerImpl* dynamic_channel_service_manager_;
  l2cap::internal::ParameterProvider* parameter_provider_;

  // Internal states
  std::unordered_map<hci::Address, PendingLink> pending_links_;
  std::unordered_map<hci::Address, Link> links_;
  std::unordered_map<hci::Address, std::list<Psm>> pending_dynamic_channels_;
  std::unordered_map<hci::Address, std::list<Link::PendingDynamicChannelConnection>>
      pending_dynamic_channels_callbacks_;
  os::Handler* link_security_interface_listener_handler_ = nullptr;
  LinkSecurityInterfaceListener* link_security_interface_listener_ = nullptr;
  LinkPropertyListener* link_property_listener_ = nullptr;
  os::Handler* link_property_callback_handler_ = nullptr;
  std::unordered_set<hci::Address> disconnected_links_;
  std::unordered_set<hci::Address> links_with_pending_packets_;
};

}  // namespace internal
}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
