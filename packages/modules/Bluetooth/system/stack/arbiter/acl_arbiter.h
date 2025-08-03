/*
 * Copyright 2022 The Android Open Source Project
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

/// This class intercepts incoming connection requests and data packets, and
/// decides whether to intercept them or pass them to the legacy stack
///
/// It allows us to easily gate changes to the datapath and roll back to legacy
/// behavior if needed.

#pragma once

#include "rust/cxx.h"
#include "stack/include/bt_hdr.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace shim {
namespace arbiter {

enum class InterceptAction {
  /// The packet should be forwarded to the legacy stack
  FORWARD,
  /// The packet should be dropped and not sent to legacy
  DROP
};

class AclArbiter {
 public:
  virtual void OnLeConnect(uint8_t tcb_idx, uint16_t advertiser_id) = 0;
  virtual void OnLeDisconnect(uint8_t tcb_idx) = 0;
  virtual InterceptAction InterceptAttPacket(uint8_t tcb_idx,
                                             const BT_HDR* packet) = 0;

  virtual void OnOutgoingMtuReq(uint8_t tcb_idx) = 0;
  virtual void OnIncomingMtuResp(uint8_t tcb_idx, size_t mtu) = 0;
  virtual void OnIncomingMtuReq(uint8_t tcb_idx, size_t mtu) = 0;

  AclArbiter() = default;
  AclArbiter(AclArbiter&& other) = default;
  AclArbiter& operator=(AclArbiter&& other) = default;
  virtual ~AclArbiter() = default;
};

void StoreCallbacksFromRust(
    ::rust::Fn<void(uint8_t tcb_idx, uint8_t advertiser)> on_le_connect,
    ::rust::Fn<void(uint8_t tcb_idx)> on_le_disconnect,
    ::rust::Fn<InterceptAction(uint8_t tcb_idx, ::rust::Vec<uint8_t> buffer)>
        intercept_packet,
    ::rust::Fn<void(uint8_t tcb_idx)> on_outgoing_mtu_req,
    ::rust::Fn<void(uint8_t tcb_idx, size_t mtu)> on_incoming_mtu_resp,
    ::rust::Fn<void(uint8_t tcb_idx, size_t mtu)> on_incoming_mtu_req);

void SendPacketToPeer(uint8_t tcb_idx, ::rust::Vec<uint8_t> buffer);

AclArbiter& GetArbiter();

}  // namespace arbiter
}  // namespace shim
}  // namespace bluetooth
