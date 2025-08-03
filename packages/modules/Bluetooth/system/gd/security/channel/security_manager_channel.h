/*
 *
 *  Copyright 2019 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0;
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
#pragma once

#include <memory>
#include <unordered_map>

#include "common/contextual_callback.h"
#include "hci/hci_layer.h"
#include "hci/hci_packets.h"
#include "hci/security_interface.h"
#include "l2cap/classic/link_security_interface.h"

namespace bluetooth {
namespace security {
namespace channel {

using SecurityCommandStatusCallback = common::ContextualOnceCallback<void(hci::CommandCompleteView)>;

/**
 * Interface for listening to the channel for SMP commands.
 */
class ISecurityManagerChannelListener {
 public:
  virtual ~ISecurityManagerChannelListener() = default;
  virtual void OnHciEventReceived(hci::EventView packet) = 0;
  virtual void OnConnectionClosed(hci::Address) = 0;
};

/**
 * Channel for consolidating traffic and making the transport agnostic.
 */
class SecurityManagerChannel : public l2cap::classic::LinkSecurityInterfaceListener {
 public:
  SecurityManagerChannel(os::Handler* handler, hci::HciLayer* hci_layer);

  virtual ~SecurityManagerChannel();

  /**
   * Creates a connection to the device which triggers pairing
   *
   * @param address remote address of device to pair with
   */
  void Connect(hci::Address address);

  /**
   * Releases link hold so it can disconnect as normally
   *
   * i.e. signals we no longer need this if acl manager wants to clean it up
   *
   * @param address remote address to disconnect
   */
  void Release(hci::Address address);

  /**
   * Immediately disconnects currently connected channel
   *
   * i.e. force disconnect
   *
   * @param address remote address to disconnect
   */
  void Disconnect(hci::Address address);

  /**
   * Send a given SMP command over the SecurityManagerChannel
   *
   * @param command smp command to send
   */
  void SendCommand(std::unique_ptr<hci::SecurityCommandBuilder> command);

  /**
   * Send a given SMP command over the SecurityManagerChannel
   *
   * @param command smp command to send
   * @param callback listener to call when command status complete
   */
  void SendCommand(std::unique_ptr<hci::SecurityCommandBuilder> command, SecurityCommandStatusCallback callback);

  /**
   * Sets the listener to listen for channel events
   *
   * @param listener the caller interested in events
   */
  void SetChannelListener(ISecurityManagerChannelListener* listener) {
    listener_ = listener;
  }

  void SetSecurityInterface(l2cap::classic::SecurityInterface* security_interface) {
    l2cap_security_interface_ = security_interface;
  }

  /**
   * Called when an incoming HCI event happens
   *
   * @param event_packet
   */
  void OnHciEventReceived(hci::EventView packet);

  /**
   * Called when an HCI command is completed
   *
   * @param on_complete
   */
  void OnCommandComplete(hci::CommandCompleteView packet);

  // Interface overrides
  void OnLinkConnected(std::unique_ptr<l2cap::classic::LinkSecurityInterface> link) override;
  void OnLinkDisconnected(hci::Address address) override;
  void OnAuthenticationComplete(hci::ErrorCode hci_status, hci::Address remote) override;
  void OnEncryptionChange(hci::Address, bool encrypted) override;

 private:
  ISecurityManagerChannelListener* listener_{nullptr};
  hci::SecurityInterface* hci_security_interface_{nullptr};
  os::Handler* handler_{nullptr};
  l2cap::classic::SecurityInterface* l2cap_security_interface_{nullptr};
  std::unordered_map<hci::Address, std::unique_ptr<l2cap::classic::LinkSecurityInterface>> link_map_;
  std::set<hci::Address> outgoing_pairing_remote_devices_;
};

}  // namespace channel
}  // namespace security
}  // namespace bluetooth
