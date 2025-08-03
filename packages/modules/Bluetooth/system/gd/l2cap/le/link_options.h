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

#include <cstdint>

#include "hci/acl_manager/le_acl_connection.h"
#include "hci/hci_packets.h"
#include "os/handler.h"

namespace bluetooth {
namespace l2cap {
namespace le {
namespace internal {
class Link;
}

/**
 * Proxy for L2CAP user to get some link layer properties (connection handle, role), and set link layer options
 * (connection parameter update, set PHY). Only few special L2CAP users need to use it, including Security Manager,
 * Hearing Aid Profile, HID Profile, and Java API.
 * Note: Setting link layer options applies to the LINK, not single CHANNEL.
 */
class LinkOptions {
 public:
  /**
   * Get LL Role. Most applications should NOT know its LL role.
   */
  hci::Role GetRole() const;

  /**
   * Get ACL Handle. Most applications should NOT know its ACL handle.
   */
  uint16_t GetHandle() const;

  /**
   * Return Local address used for initiation of this connection.
   */
  hci::AddressWithType GetLocalAddress() const;

  /**
   * Update the LE link layer connection parameters.
   * Depending on the link role and supported features, may directly send HCI command to update link, or send L2CAP
   * request to advise the remote. The updated connection parameters are still determined by controller. It's a link
   * layer change for performance tuning, and no host layer change should be observable by user.
   * Parameters are defined in Core spec HCI 7.8.18.
   * @return true iff the request is sent to controller through HCI or remote through L2CAP
   * (Use it only if you know what you are doing!)
   */
  bool UpdateConnectionParameter(uint16_t conn_interval_min, uint16_t conn_interval_max, uint16_t conn_latency,
                                 uint16_t supervision_timeout, uint16_t min_ce_length, uint16_t max_ce_length);

  /**
   * Set PHY preference. The PHY is determined by the controller.
   * No host layer change should be observable by user.
   * Parameters are defined in Core spec HCI 7.8.49.
   * @return true iff the request is sent to controller through HCI
   * (Use it only if you know what you are doing!)
   */
  bool SetPhy(uint8_t all_phys, uint8_t tx_phys, uint8_t rx_phys, uint16_t phy_options);

  LinkOptions(hci::acl_manager::LeAclConnection* acl_connection, internal::Link* link, os::Handler* l2cap_handler);

 private:
  hci::acl_manager::LeAclConnection* acl_connection_ = nullptr;
  internal::Link* link_ = nullptr;
  os::Handler* l2cap_handler_ = nullptr;
};

}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
