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

#include "hci/address_with_type.h"
#include "l2cap/cid.h"

namespace bluetooth {
namespace l2cap {
namespace internal {

/**
 * Common interface for link (Classic ACL and LE)
 */
class ILink {
 public:
  virtual ~ILink() = default;
  virtual void SendDisconnectionRequest(Cid local_cid, Cid remote_cid) = 0;
  virtual hci::AddressWithType GetDevice() const = 0;

  // Used by sender to indicate whether there is any pending packet to be sent.
  // If there is pending packet, don't delete the link.

  virtual void OnPendingPacketChange(Cid /* local_cid */, bool /* has_packet */) {}
  // To be used by LE credit based channel data controller over LE link
  virtual void SendLeCredit(Cid /* local_cid */, uint16_t /* credit */) {}

  // Used by A2dp software encoding
  virtual void SetChannelTxPriority(Cid /* local_cid */, bool /* high_priority */) {}
};
}  // namespace internal
}  // namespace l2cap
}  // namespace bluetooth
