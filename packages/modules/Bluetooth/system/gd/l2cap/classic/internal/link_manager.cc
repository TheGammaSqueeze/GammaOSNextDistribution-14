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

#include "hci/acl_manager/classic_acl_connection.h"
#include "hci/address.h"
#include "hci/class_of_device.h"
#include "l2cap/classic/internal/link.h"
#include "l2cap/internal/scheduler_fifo.h"
#include "os/log.h"

#include "l2cap/classic/internal/link_manager.h"

namespace bluetooth {
namespace l2cap {
namespace classic {
namespace internal {

void LinkManager::ConnectFixedChannelServices(hci::Address device,
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
  auto* link = GetLink(device);
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
      auto fixed_channel_impl = link->AllocateFixedChannel(fixed_channel_service.first);
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
  auto pending_link = pending_links_.find(device);
  if (pending_link == pending_links_.end()) {
    // Create pending link if not exist
    pending_links_.try_emplace(device);
    pending_link = pending_links_.find(device);
  }
  pending_link->second.pending_fixed_channel_connections_.push_back(std::move(pending_fixed_channel_connection));
  // Then create new ACL connection
  acl_manager_->CreateConnection(device);
}

void LinkManager::ConnectDynamicChannelServices(
    hci::Address device, Link::PendingDynamicChannelConnection pending_dynamic_channel_connection, Psm psm) {
  if (!IsPsmValid(psm)) {
    return;
  }
  auto* link = GetLink(device);
  if (link == nullptr) {
    acl_manager_->CreateConnection(device);
    if (pending_dynamic_channels_.find(device) != pending_dynamic_channels_.end()) {
      pending_dynamic_channels_[device].push_back(psm);
      pending_dynamic_channels_callbacks_[device].push_back(std::move(pending_dynamic_channel_connection));
    } else {
      pending_dynamic_channels_[device] = {psm};
      pending_dynamic_channels_callbacks_[device].push_back(std::move(pending_dynamic_channel_connection));
    }
    return;
  }
  link->SendConnectionRequest(psm, link->ReserveDynamicChannel(), std::move(pending_dynamic_channel_connection));
}

void LinkManager::InitiateConnectionForSecurity(hci::Address remote) {
  auto* link = GetLink(remote);
  if (link != nullptr) {
    LOG_ERROR("Link already exists for %s", ADDRESS_TO_LOGGABLE_CSTR(remote));
  }
  acl_manager_->CreateConnection(remote);
}

void LinkManager::RegisterLinkSecurityInterfaceListener(os::Handler* handler, LinkSecurityInterfaceListener* listener) {
  link_security_interface_listener_handler_ = handler;
  link_security_interface_listener_ = listener;
}

LinkSecurityInterfaceListener* LinkManager::GetLinkSecurityInterfaceListener() {
  return link_security_interface_listener_;
}

void LinkManager::RegisterLinkPropertyListener(os::Handler* handler, LinkPropertyListener* listener) {
  link_property_callback_handler_ = handler;
  link_property_listener_ = listener;
}

void LinkManager::OnPendingPacketChange(hci::Address remote, int num_packets) {
  if (disconnected_links_.count(remote) != 0 && num_packets == 0) {
    links_.erase(remote);
    links_with_pending_packets_.erase(remote);
  } else if (num_packets != 0) {
    links_with_pending_packets_.emplace(remote);
  } else {
    links_with_pending_packets_.erase(remote);
  }
}

Link* LinkManager::GetLink(const hci::Address device) {
  if (links_.find(device) == links_.end()) {
    return nullptr;
  }
  return &links_.find(device)->second;
}

void LinkManager::handle_link_security_hold(hci::Address remote) {
  auto link = GetLink(remote);
  if (link == nullptr) {
    LOG_WARN("Remote is disconnected");
    return;
  }
  link->AcquireSecurityHold();
}

void LinkManager::handle_link_security_release(hci::Address remote) {
  auto link = GetLink(remote);
  if (link == nullptr) {
    LOG_WARN("Remote is disconnected");
    return;
  }
  link->ReleaseSecurityHold();
}

void LinkManager::handle_link_security_disconnect(hci::Address remote) {
  auto link = GetLink(remote);
  if (link == nullptr) {
    LOG_WARN("Remote is disconnected");
    return;
  }
  link->Disconnect();
}

void LinkManager::handle_link_security_ensure_authenticated(hci::Address remote) {
  auto link = GetLink(remote);
  if (link == nullptr) {
    LOG_WARN("Remote is disconnected");
    return;
  }
  link->Authenticate();
}

void LinkManager::handle_link_security_ensure_encrypted(hci::Address remote) {
  auto link = GetLink(remote);
  if (link == nullptr) {
    LOG_WARN("Remote is disconnected");
    return;
  }
  link->Encrypt();
}

/**
 * The implementation for LinkSecurityInterface, which allows the SecurityModule to access some link functionalities.
 * Note: All public methods implementing this interface are invoked from external context.
 */
class LinkSecurityInterfaceImpl : public LinkSecurityInterface {
 public:
  LinkSecurityInterfaceImpl(os::Handler* handler, LinkManager* link_manager, Link* link)
      : handler_(handler),
        link_manager_(link_manager),
        remote_(link->GetDevice().GetAddress()),
        acl_handle_(link->GetAclHandle()) {}

  hci::Address GetRemoteAddress() override {
    return remote_;
  }

  void Hold() override {
    handler_->CallOn(link_manager_, &LinkManager::handle_link_security_hold, remote_);
  }

  void Release() override {
    handler_->CallOn(link_manager_, &LinkManager::handle_link_security_release, remote_);
  }

  void Disconnect() override {
    handler_->CallOn(link_manager_, &LinkManager::handle_link_security_disconnect, remote_);
  }

  void EnsureAuthenticated() override {
    handler_->CallOn(link_manager_, &LinkManager::handle_link_security_ensure_authenticated, remote_);
  }

  void EnsureEncrypted() override {
    handler_->CallOn(link_manager_, &LinkManager::handle_link_security_ensure_encrypted, remote_);
  }

  uint16_t GetAclHandle() override {
    return acl_handle_;
  }

  hci::Role GetRole() override {
    return link_manager_->GetLink(remote_)->GetRole();
  }

  os::Handler* handler_;
  LinkManager* link_manager_;
  hci::Address remote_;
  uint16_t acl_handle_;
};

void LinkManager::OnConnectSuccess(std::unique_ptr<hci::acl_manager::ClassicAclConnection> acl_connection) {
  // Same link should not be connected twice
  hci::Address device = acl_connection->GetAddress();
  ASSERT_LOG(
      GetLink(device) == nullptr,
      "%s is connected twice without disconnection",
      ADDRESS_TO_LOGGABLE_CSTR(acl_connection->GetAddress()));
  links_.try_emplace(device, l2cap_handler_, std::move(acl_connection), parameter_provider_,
                     dynamic_channel_service_manager_, fixed_channel_service_manager_, this);
  auto* link = GetLink(device);
  ASSERT(link != nullptr);
  link->SendInformationRequest(InformationRequestInfoType::EXTENDED_FEATURES_SUPPORTED);
  link->SendInformationRequest(InformationRequestInfoType::FIXED_CHANNELS_SUPPORTED);
  link->ReadRemoteVersionInformation();
  link->ReadRemoteSupportedFeatures();
  link->ReadRemoteExtendedFeatures(1);

  // Allocate and distribute channels for all registered fixed channel services
  auto fixed_channel_services = fixed_channel_service_manager_->GetRegisteredServices();
  for (auto& fixed_channel_service : fixed_channel_services) {
    auto fixed_channel_impl = link->AllocateFixedChannel(fixed_channel_service.first);
    fixed_channel_service.second->NotifyChannelCreation(
        std::make_unique<FixedChannel>(fixed_channel_impl, l2cap_handler_));
  }
  if (pending_dynamic_channels_.find(device) != pending_dynamic_channels_.end()) {
    auto psm_list = pending_dynamic_channels_[device];
    auto& callback_list = pending_dynamic_channels_callbacks_[device];
    link->SetPendingDynamicChannels(psm_list, std::move(callback_list));
    pending_dynamic_channels_.erase(device);
    pending_dynamic_channels_callbacks_.erase(device);
  }
  // Notify link property listener
  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(
        link_property_listener_, &LinkPropertyListener::OnLinkConnected, device, link->GetAclHandle());
  }

  // Notify security manager
  if (link_security_interface_listener_handler_ != nullptr) {
    link_security_interface_listener_handler_->CallOn(
        link_security_interface_listener_,
        &LinkSecurityInterfaceListener::OnLinkConnected,
        std::make_unique<LinkSecurityInterfaceImpl>(l2cap_handler_, this, link));
  }

  // Remove device from pending links list, if any
  pending_links_.erase(device);
}

void LinkManager::OnConnectRequest(hci::Address /* device */, hci::ClassOfDevice /* cod */) {
  LOG_ERROR("Remote connect request unimplemented");
}

void LinkManager::OnConnectFail(hci::Address device, hci::ErrorCode reason, bool) {
  // Notify all pending links for this device
  auto pending_link = pending_links_.find(device);
  if (pending_link == pending_links_.end()) {
    // There is no pending link, exit
    LOG_INFO(
        "Connection to %s failed without a pending link; reason: %s",
        ADDRESS_TO_LOGGABLE_CSTR(device),
        hci::ErrorCodeText(reason).c_str());
    if (pending_dynamic_channels_callbacks_.find(device) != pending_dynamic_channels_callbacks_.end()) {
      for (Link::PendingDynamicChannelConnection& callbacks : pending_dynamic_channels_callbacks_[device]) {
        callbacks.on_fail_callback_.Invoke(DynamicChannelManager::ConnectionResult{
            .hci_error = hci::ErrorCode::CONNECTION_TIMEOUT,
        });
      }
      pending_dynamic_channels_.erase(device);
      pending_dynamic_channels_callbacks_.erase(device);
    }
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

void LinkManager::OnDisconnect(hci::Address device, hci::ErrorCode status) {
  auto* link = GetLink(device);
  ASSERT_LOG(
      link != nullptr,
      "Device %s is disconnected with reason 0x%x, but not in local database",
      ADDRESS_TO_LOGGABLE_CSTR(device),
      static_cast<uint8_t>(status));
  if (link_security_interface_listener_handler_ != nullptr) {
    link_security_interface_listener_handler_->CallOn(
        link_security_interface_listener_, &LinkSecurityInterfaceListener::OnLinkDisconnected, device);
  }
  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(link_property_listener_, &LinkPropertyListener::OnLinkDisconnected, device);
  }

  if (links_with_pending_packets_.count(device) != 0) {
    disconnected_links_.emplace(device);
  } else {
    links_.erase(device);
  }
}

void LinkManager::OnAuthenticationComplete(hci::ErrorCode hci_status, hci::Address device) {
  if (link_security_interface_listener_handler_ != nullptr) {
    link_security_interface_listener_handler_->CallOn(
        link_security_interface_listener_,
        &LinkSecurityInterfaceListener::OnAuthenticationComplete,
        hci_status,
        device);
  }
}

void LinkManager::OnEncryptionChange(hci::Address device, hci::EncryptionEnabled enabled) {
  if (link_security_interface_listener_handler_ != nullptr) {
    link_security_interface_listener_handler_->CallOn(
        link_security_interface_listener_,
        &LinkSecurityInterfaceListener::OnEncryptionChange,
        device,
        enabled == hci::EncryptionEnabled::ON || enabled == hci::EncryptionEnabled::BR_EDR_AES_CCM);
  }
}

void LinkManager::OnReadRemoteVersionInformation(
    hci::ErrorCode hci_status,
    hci::Address device,
    uint8_t lmp_version,
    uint16_t manufacturer_name,
    uint16_t sub_version) {
  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(
        link_property_listener_,
        &LinkPropertyListener::OnReadRemoteVersionInformation,
        hci_status,
        device,
        lmp_version,
        manufacturer_name,
        sub_version);
  }
}

void LinkManager::OnReadRemoteSupportedFeatures(hci::Address device, uint64_t features) {
  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(
        link_property_listener_, &LinkPropertyListener::OnReadRemoteSupportedFeatures, device, features);
  }
}

void LinkManager::OnReadRemoteExtendedFeatures(
    hci::Address device, uint8_t page_number, uint8_t max_page_number, uint64_t features) {
  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(
        link_property_listener_,
        &LinkPropertyListener::OnReadRemoteExtendedFeatures,
        device,
        page_number,
        max_page_number,
        features);
  }
}

void LinkManager::OnRoleChange(hci::ErrorCode hci_status, hci::Address remote, hci::Role role) {
  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(
        link_property_listener_, &LinkPropertyListener::OnRoleChange, hci_status, remote, role);
  }
}

void LinkManager::OnReadClockOffset(hci::Address remote, uint16_t clock_offset) {
  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(
        link_property_listener_, &LinkPropertyListener::OnReadClockOffset, remote, clock_offset);
  }
}

void LinkManager::OnModeChange(hci::ErrorCode hci_status, hci::Address remote, hci::Mode mode, uint16_t interval) {
  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(
        link_property_listener_, &LinkPropertyListener::OnModeChange, hci_status, remote, mode, interval);
  }
}

void LinkManager::OnSniffSubrating(
    hci::ErrorCode hci_status,
    hci::Address remote,
    uint16_t max_tx_lat,
    uint16_t max_rx_lat,
    uint16_t min_remote_timeout,
    uint16_t min_local_timeout) {
  if (link_property_callback_handler_ != nullptr) {
    link_property_callback_handler_->CallOn(
        link_property_listener_,
        &LinkPropertyListener::OnSniffSubrating,
        hci_status,
        remote,
        max_tx_lat,
        max_rx_lat,
        min_remote_timeout,
        min_local_timeout);
  }
}

}  // namespace internal
}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
