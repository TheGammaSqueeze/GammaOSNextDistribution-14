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

#include "l2cap/le/link_options.h"

#include <cstdint>

#include "hci/hci_packets.h"
#include "l2cap/le/internal/link.h"

namespace bluetooth {
namespace l2cap {
namespace le {

LinkOptions::LinkOptions(hci::acl_manager::LeAclConnection* acl_connection, internal::Link* link,
                         os::Handler* l2cap_handler)
    : acl_connection_(acl_connection), link_(link), l2cap_handler_(l2cap_handler) {}

hci::Role LinkOptions::GetRole() const {
  return acl_connection_->GetRole();
}

uint16_t LinkOptions::GetHandle() const {
  return acl_connection_->GetHandle();
}

hci::AddressWithType LinkOptions::GetLocalAddress() const {
  return acl_connection_->GetLocalAddress();
}

bool LinkOptions::UpdateConnectionParameter(uint16_t conn_interval_min, uint16_t conn_interval_max,
                                            uint16_t conn_latency, uint16_t supervision_timeout, uint16_t min_ce_length,
                                            uint16_t max_ce_length) {
  if (conn_interval_min < 0x0006 || conn_interval_min > 0x0C80 || conn_interval_max < 0x0006 ||
      conn_interval_max > 0x0C80 || conn_latency > 0x01F3 || supervision_timeout < 0x000A ||
      supervision_timeout > 0x0C80) {
    LOG_ERROR("Invalid parameter");
    return false;
  }

  l2cap_handler_->Post(common::BindOnce(&internal::Link::SendConnectionParameterUpdate, common::Unretained(link_),
                                        conn_interval_min, conn_interval_max, conn_latency, supervision_timeout,
                                        min_ce_length, max_ce_length));

  return true;
}

bool LinkOptions::SetPhy(
    uint8_t /* all_phys */,
    uint8_t /* tx_phys */,
    uint8_t /* rx_phys */,
    uint16_t /* phy_options */) {
  LOG_ERROR("Not implemented");
  return false;
}

}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
