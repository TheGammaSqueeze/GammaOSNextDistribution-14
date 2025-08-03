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

#include "hci/address.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace l2cap {
namespace classic {

/**
 * This is a proxy for Security Module to unregister itself, or to initiate link connection.
 */
class SecurityInterface {
 public:
  virtual ~SecurityInterface() = default;

  /**
   * Page a remote device for ACL connection, when Security Module needs it for pairing. When the remote device is
   * connected, Security Module will receive a callback through LinkSecurityInterfaceListener.
   */
  virtual void InitiateConnectionForSecurity(hci::Address remote) = 0;

  /**
   * Unregister the security interface and the LinkSecurityInterfaceListener.
   */
  virtual void Unregister() = 0;
};

/**
 * This is a proxy for Security Module to access some link function. This object is passed to Security Module when a
 * link is established.
 */
class LinkSecurityInterface {
 public:
  virtual ~LinkSecurityInterface() = default;

  virtual hci::Address GetRemoteAddress() = 0;

  /**
   * Hold the ACL link connection. Don't disconnect the link until Release() is called.
   */
  virtual void Hold() = 0;

  /**
   * Release the ACL link connection. This doesn't guarantee link disconnection, if other L2cap services are using the
   * link.
   */
  virtual void Release() = 0;

  /**
   * Force the ACL link to disconnect.
   */
  virtual void Disconnect() = 0;

  /**
   * Initiate pairing to HCI layer.
   */
  virtual void EnsureAuthenticated() = 0;

  /**
   * Start encryption on an authenticated link (not necessarily MITM link key).
   */
  virtual void EnsureEncrypted() = 0;

  virtual uint16_t GetAclHandle() = 0;

  virtual hci::Role GetRole() {
    return hci::Role::CENTRAL;
  }
};

class LinkSecurityInterfaceListener {
 public:
  virtual ~LinkSecurityInterfaceListener() = default;

  /**
   * Each time when an ACL link is connected, security manager receives this callback to use LinkSecurityInterface
   * functions.
   */
  virtual void OnLinkConnected(std::unique_ptr<LinkSecurityInterface>) {}

  /**
   * When an ACL link is disconnected, security manager receives this callback. The corresponding LinkSecurityInterface
   * is invalidated then.
   * @param remote
   */
  virtual void OnLinkDisconnected(hci::Address /* remote */) {}

  /**
   * Invoked when AuthenticationComplete event is received for a given link
   */
  virtual void OnAuthenticationComplete(
      hci::ErrorCode /* hci_status */, hci::Address /* remote */) {}

  /**
   * Invoked when EncryptionChange event is received for a given link
   * @param encrypted
   */
  virtual void OnEncryptionChange(hci::Address /* remote */, bool /* encrypted */) {}
};

}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
