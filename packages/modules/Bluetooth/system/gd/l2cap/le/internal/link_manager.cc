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
#include <memory>
#include <unordered_map>

#include "hci/acl_manager/le_acl_connection.h"
#include "hci/address.h"
#include "l2cap/internal/scheduler_fifo.h"
#include "l2cap/le/internal/link.h"
#include "os/handler.h"
#include "os/log.h"

#include "l2cap/le/internal/link_manager.h"

namespace bluetooth {
namespace l2cap {
namespace le {
namespace internal {

void LinkManager::ConnectFixedChannelServices(hci::AddressWithType address_with_type,
                                              PendingFixedChannelConnection pending_fixed_channel_connection) {
  // Check if there is any service registered
  auto fixed_channel_services = fixed_channel_service_manager_->GetRegisteredServices();
  if (fixed_channel_services.empty()) {
    // If so, return error
    pending_fixed_channel_connection.handler_->Post(common::BindOnce(
        std::move(pending_fixed_channel_connection.on_fail_callback_),
        FixedChannelManager::ConnectionResult{
            .connection_result_code = FixedChannelManager::ConnectionResultCode::FAIL_NO_SERVICE_REGISTERED}));
    return;
  }
  // Otherwise, check if device has an ACL connection
  auto* link = GetLink(address_with_type);
  if (link != nullptr) {
    // If device already have an ACL connection
    // Check if all registered services have an allocated channel and allocate one if not already allocated
    int num_new_channels = 0;
    for (auto& fixed_channel_service : fixed_channel_services) {
      if (link->IsFixedChannelAllocated(fixed_channel_service.first)) {
        // This channel is already allocated for this link, do not allocated twice
        continue;
      }
      // Allocate channel for newly registered fixed channels
      auto fixed_channel_impl = link->AllocateFixedChannel(
          fixed_channel_service.first, SecurityPolicy::NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK);
      fixed_channel_service.second->NotifyChannelCreation(
          std::make_unique<FixedChannel>(fixed_channel_impl, l2cap_handler_));
      num_new_channels++;
    }
    // Declare connection failure if no new channels are created
    if (num_new_channels == 0) {
      pending_fixed_channel_connection.handler_->Post(common::BindOnce(
          std::move(pending_fixed_channel_connection.on_fail_callback_),
          FixedChannelManager::ConnectionResult{
              .connection_result_code = FixedChannelManager::ConnectionResultCode::FAIL_ALL_SERVICES_HAVE_CHANNEL}));
    }
    // No need to create ACL connection, return without saving any pending connections
    return;
  }
  // If not, create new ACL connection
  // Add request to pending link list first
  auto pending_link = pending_links_.find(address_with_type);
  if (pending_link == pending_links_.end()) {
    // Create pending link if not exist
    pending_links_.try_emplace(address_with_type);
    pending_link = pending_links_.find(address_with_type);
  }
  pending_link->second.pending_fixed_channel_connections_.push_back(std::move(pending_fixed_channel_connection));
  // Then create new ACL connection
  acl_manager_->CreateLeConnection(address_with_type, /* is_direct */ true);
}

void LinkManager::ConnectDynamicChannelServices(
    hci::AddressWithType device, Link::PendingDynamicChannelConnection pending_dynamic_channel_connection, Psm psm) {
  auto* link = GetLink(device);
  if (link == nullptr) {
    acl_manager_->CreateLeConnection(device, /* is_direct */ true);
    pending_dynamic_channels_[device].push_back(std::make_pair(psm, std::move(pending_dynamic_channel_connection)));
    return;
  }
  link->SendConnectionRequest(psm, std::move(pending_dynamic_channel_connection));
}

Link* LinkManager::GetLink(hci::AddressWithType address_with_type) {
  if (links_.find(address_with_type) == links_.end()) {
    return nullptr;
  }
  return &links_.find(address_with_type)->second;
}

void LinkManager::OnLeConnectSuccess(hci::AddressWithType connecting_address_with_type,
                                     std::unique_ptr<hci::acl_manager::LeAclConnection> acl_connection) {
  // Same link should not be connected twice
  hci::AddressWithType connected_address_with_type = acl_connection->GetRemoteAddress();
  uint16_t handle = acl_connection->GetHandle();
  ASSERT_LOG(
      GetLink(connected_address_with_type) == nullptr,
      "%s is connected twice without disconnection",
      ADDRESS_TO_LOGGABLE_CSTR(acl_connection->GetRemoteAddress()));
  links_.try_emplace(connected_address_with_type, l2cap_handler_, std::move(acl_connection), parameter_provider_,
                     dynamic_channel_service_manager_, fixed_channel_service_manager_, this);
  auto* link = GetLink(connected_address_with_type);

  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(
        link_property_listener_,
        &LinkPropertyListener::OnLinkConnected,
        connected_address_with_type,
        handle,
        link->GetRole());
  }

  // Remove device from pending links list, if any
  pending_links_.erase(connecting_address_with_type);

  link->ReadRemoteVersionInformation();
}

void LinkManager::OnLeConnectFail(hci::AddressWithType address_with_type, hci::ErrorCode reason) {
  // Notify all pending links for this device
  auto pending_link = pending_links_.find(address_with_type);
  if (pending_link == pending_links_.end()) {
    // There is no pending link, exit
    LOG_INFO("Connection to %s failed without a pending link",
             ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
    return;
  }
  for (auto& pending_fixed_channel_connection : pending_link->second.pending_fixed_channel_connections_) {
    pending_fixed_channel_connection.handler_->Post(common::BindOnce(
        std::move(pending_fixed_channel_connection.on_fail_callback_),
        FixedChannelManager::ConnectionResult{
            .connection_result_code = FixedChannelManager::ConnectionResultCode::FAIL_HCI_ERROR, .hci_error = reason}));
  }
  // Remove entry in pending link list
  pending_links_.erase(pending_link);
}

void LinkManager::OnDisconnect(bluetooth::hci::AddressWithType address_with_type) {
  auto* link = GetLink(address_with_type);
  ASSERT_LOG(
      link != nullptr,
      "Device %s is disconnected but not in local database",
      ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
  if (links_with_pending_packets_.count(address_with_type) != 0) {
    disconnected_links_.emplace(address_with_type);
  } else {
    links_.erase(address_with_type);
  }

  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(
        link_property_listener_, &LinkPropertyListener::OnLinkDisconnected, address_with_type);
  }
}

void LinkManager::RegisterLinkPropertyListener(os::Handler* handler, LinkPropertyListener* listener) {
  link_property_callback_handler_ = handler;
  link_property_listener_ = listener;
}

void LinkManager::OnReadRemoteVersionInformationComplete(
    hci::ErrorCode hci_status,
    hci::AddressWithType address_with_type,
    uint8_t lmp_version,
    uint16_t manufacturer_name,
    uint16_t sub_version) {
  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(
        link_property_listener_,
        &LinkPropertyListener::OnReadRemoteVersionInformation,
        hci_status,
        address_with_type,
        lmp_version,
        manufacturer_name,
        sub_version);
  }

  auto* link = GetLink(address_with_type);
  // Allocate and distribute channels for all registered fixed channel services
  auto fixed_channel_services = fixed_channel_service_manager_->GetRegisteredServices();
  for (auto& fixed_channel_service : fixed_channel_services) {
    auto fixed_channel_impl = link->AllocateFixedChannel(
        fixed_channel_service.first, SecurityPolicy::NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK);
    fixed_channel_service.second->NotifyChannelCreation(
        std::make_unique<FixedChannel>(fixed_channel_impl, l2cap_handler_));
  }
  if (pending_dynamic_channels_.find(address_with_type) != pending_dynamic_channels_.end()) {
    for (auto& psm_callback : pending_dynamic_channels_[address_with_type]) {
      link->SendConnectionRequest(psm_callback.first, std::move(psm_callback.second));
    }
    pending_dynamic_channels_.erase(address_with_type);
  }
}

void LinkManager::OnPendingPacketChange(hci::AddressWithType remote, int num_packets) {
  if (disconnected_links_.count(remote) != 0 && num_packets == 0) {
    links_.erase(remote);
    links_with_pending_packets_.erase(remote);
  } else if (num_packets != 0) {
    links_with_pending_packets_.emplace(remote);
  } else {
    links_with_pending_packets_.erase(remote);
  }
}

}  // namespace internal
}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
