/*
 *
 *  Copyright 2019 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
#include "security/channel/security_manager_channel.h"

#include "hci/address.h"

namespace bluetooth {
namespace security {
namespace channel {

/**
 * Main Constructor
 */
SecurityManagerChannel::SecurityManagerChannel(os::Handler* handler, hci::HciLayer* hci_layer)
    : listener_(nullptr),
      hci_security_interface_(
          hci_layer->GetSecurityInterface(handler->BindOn(this, &SecurityManagerChannel::OnHciEventReceived))),
      handler_(handler),
      l2cap_security_interface_(nullptr) {}

SecurityManagerChannel::~SecurityManagerChannel() {
  l2cap_security_interface_->Unregister();
  l2cap_security_interface_ = nullptr;
}

void SecurityManagerChannel::Connect(hci::Address address) {
  ASSERT_LOG(l2cap_security_interface_ != nullptr, "L2cap Security Interface is null!");
  auto entry = link_map_.find(address);
  if (entry != link_map_.end()) {
    LOG_WARN("Already connected to '%s'", ADDRESS_TO_LOGGABLE_CSTR(address));
    entry->second->Hold();
    entry->second->EnsureAuthenticated();
    return;
  }
  l2cap_security_interface_->InitiateConnectionForSecurity(address);
  outgoing_pairing_remote_devices_.insert(address);
}

void SecurityManagerChannel::Release(hci::Address address) {
  auto entry = link_map_.find(address);
  if (entry == link_map_.end()) {
    LOG_WARN("Unknown address '%s'", ADDRESS_TO_LOGGABLE_CSTR(address));
    return;
  }
  entry->second->Release();
}

void SecurityManagerChannel::Disconnect(hci::Address address) {
  outgoing_pairing_remote_devices_.erase(address);
  auto entry = link_map_.find(address);
  if (entry == link_map_.end()) {
    LOG_WARN("Unknown address '%s'", ADDRESS_TO_LOGGABLE_CSTR(address));
    return;
  }
  entry->second->Disconnect();
}

void SecurityManagerChannel::OnCommandComplete(hci::CommandCompleteView packet) {
  ASSERT_LOG(packet.IsValid(), "Bad command response");
}

void SecurityManagerChannel::SendCommand(std::unique_ptr<hci::SecurityCommandBuilder> command) {
  hci_security_interface_->EnqueueCommand(std::move(command),
                                          handler_->BindOnceOn(this, &SecurityManagerChannel::OnCommandComplete));
}

void SecurityManagerChannel::SendCommand(
    std::unique_ptr<hci::SecurityCommandBuilder> command, SecurityCommandStatusCallback callback) {
  hci_security_interface_->EnqueueCommand(std::move(command), std::forward<SecurityCommandStatusCallback>(callback));
}

void SecurityManagerChannel::OnHciEventReceived(hci::EventView packet) {
  ASSERT_LOG(listener_ != nullptr, "No listener set!");
  ASSERT(packet.IsValid());
  listener_->OnHciEventReceived(packet);
}

void SecurityManagerChannel::OnLinkConnected(std::unique_ptr<l2cap::classic::LinkSecurityInterface> link) {
  // Multiple links possible?
  auto remote = link->GetRemoteAddress();
  if (outgoing_pairing_remote_devices_.count(remote) == 1) {
    link->Hold();
    link->EnsureAuthenticated();
    outgoing_pairing_remote_devices_.erase(remote);
  }
  link_map_.emplace(remote, std::move(link));
}

void SecurityManagerChannel::OnLinkDisconnected(hci::Address address) {
  auto entry = link_map_.find(address);
  if (entry == link_map_.end()) {
    LOG_WARN("Unknown address '%s'", ADDRESS_TO_LOGGABLE_CSTR(address));
    return;
  }
  entry->second.reset();
  link_map_.erase(entry);
  ASSERT_LOG(listener_ != nullptr, "Set listener!");
  listener_->OnConnectionClosed(address);
}

void SecurityManagerChannel::OnAuthenticationComplete(
    hci::ErrorCode /* hci_status */, hci::Address remote) {
  ASSERT_LOG(l2cap_security_interface_ != nullptr, "L2cap Security Interface is null!");
  auto entry = link_map_.find(remote);
  if (entry != link_map_.end()) {
    entry->second->EnsureEncrypted();
    return;
  }
}

void SecurityManagerChannel::OnEncryptionChange(hci::Address /* remote */, bool /* encrypted */) {}

}  // namespace channel
}  // namespace security
}  // namespace bluetooth
