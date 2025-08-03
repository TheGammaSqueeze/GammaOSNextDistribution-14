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

#include "stack/arbiter/acl_arbiter.h"

#include <base/functional/bind.h>
#include <bluetooth/log.h>

#include <iterator>

#include "osi/include/allocator.h"
#include "stack/gatt/gatt_int.h"
#include "stack/include/l2c_api.h"
#include "stack/include/main_thread.h"

namespace bluetooth {
namespace shim {
namespace arbiter {

class PassthroughAclArbiter : public AclArbiter {
 public:
  virtual void OnLeConnect(uint8_t tcb_idx, uint16_t advertiser_id) override {
    // no-op
  }

  virtual void OnLeDisconnect(uint8_t tcb_idx) override {
    // no-op
  }

  virtual InterceptAction InterceptAttPacket(uint8_t tcb_idx,
                                             const BT_HDR* packet) override {
    return InterceptAction::FORWARD;
  }

  virtual void OnOutgoingMtuReq(uint8_t tcb_idx) override {
    // no-op
  }

  virtual void OnIncomingMtuResp(uint8_t tcb_idx, size_t mtu) {
    // no-op
  }

  virtual void OnIncomingMtuReq(uint8_t tcb_idx, size_t mtu) {
    // no-op
  }

  static PassthroughAclArbiter& Get() {
    static auto singleton = PassthroughAclArbiter();
    return singleton;
  }
};

namespace {
struct RustArbiterCallbacks {
  ::rust::Fn<void(uint8_t tcb_idx, uint8_t advertiser)> on_le_connect;
  ::rust::Fn<void(uint8_t tcb_idx)> on_le_disconnect;
  ::rust::Fn<InterceptAction(uint8_t tcb_idx, ::rust::Vec<uint8_t> buffer)>
      intercept_packet;
  ::rust::Fn<void(uint8_t tcb_idx)> on_outgoing_mtu_req;
  ::rust::Fn<void(uint8_t tcb_idx, size_t mtu)> on_incoming_mtu_resp;
  ::rust::Fn<void(uint8_t tcb_idx, size_t mtu)> on_incoming_mtu_req;
};

RustArbiterCallbacks callbacks_{};
}  // namespace

class RustGattAclArbiter : public AclArbiter {
 public:
  virtual void OnLeConnect(uint8_t tcb_idx, uint16_t advertiser_id) override {
    log::info("Notifying Rust of LE connection");
    callbacks_.on_le_connect(tcb_idx, advertiser_id);
  }

  virtual void OnLeDisconnect(uint8_t tcb_idx) override {
    log::info("Notifying Rust of LE disconnection");
    callbacks_.on_le_disconnect(tcb_idx);
  }

  virtual InterceptAction InterceptAttPacket(uint8_t tcb_idx,
                                             const BT_HDR* packet) override {
    log::debug("Intercepting ATT packet and forwarding to Rust");

    uint8_t* packet_start = (uint8_t*)(packet + 1) + packet->offset;
    uint8_t* packet_end = packet_start + packet->len;

    auto vec = ::rust::Vec<uint8_t>();
    std::copy(packet_start, packet_end, std::back_inserter(vec));
    return callbacks_.intercept_packet(tcb_idx, std::move(vec));
  }

  virtual void OnOutgoingMtuReq(uint8_t tcb_idx) override {
    log::debug("Notifying Rust of outgoing MTU request");
    callbacks_.on_outgoing_mtu_req(tcb_idx);
  }

  virtual void OnIncomingMtuResp(uint8_t tcb_idx, size_t mtu) {
    log::debug("Notifying Rust of incoming MTU response {}", mtu);
    callbacks_.on_incoming_mtu_resp(tcb_idx, mtu);
  }

  virtual void OnIncomingMtuReq(uint8_t tcb_idx, size_t mtu) {
    log::debug("Notifying Rust of incoming MTU request {}", mtu);
    callbacks_.on_incoming_mtu_req(tcb_idx, mtu);
  }

  void SendPacketToPeer(uint8_t tcb_idx, ::rust::Vec<uint8_t> buffer) {
    tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);
    if (p_tcb != nullptr) {
      BT_HDR* p_buf = (BT_HDR*)osi_malloc(sizeof(BT_HDR) + buffer.size() +
                                          L2CAP_MIN_OFFSET);
      if (p_buf == nullptr) {
        log::fatal("OOM when sending packet");
      }
      auto p = (uint8_t*)(p_buf + 1) + L2CAP_MIN_OFFSET;
      std::copy(buffer.begin(), buffer.end(), p);
      p_buf->offset = L2CAP_MIN_OFFSET;
      p_buf->len = buffer.size();
      L2CA_SendFixedChnlData(L2CAP_ATT_CID, p_tcb->peer_bda, p_buf);
    } else {
      log::error("Dropping packet since connection no longer exists");
    }
  }

  static RustGattAclArbiter& Get() {
    static auto singleton = RustGattAclArbiter();
    return singleton;
  }
};

void StoreCallbacksFromRust(
    ::rust::Fn<void(uint8_t tcb_idx, uint8_t advertiser)> on_le_connect,
    ::rust::Fn<void(uint8_t tcb_idx)> on_le_disconnect,
    ::rust::Fn<InterceptAction(uint8_t tcb_idx, ::rust::Vec<uint8_t> buffer)>
        intercept_packet,
    ::rust::Fn<void(uint8_t tcb_idx)> on_outgoing_mtu_req,
    ::rust::Fn<void(uint8_t tcb_idx, size_t mtu)> on_incoming_mtu_resp,
    ::rust::Fn<void(uint8_t tcb_idx, size_t mtu)> on_incoming_mtu_req) {
  log::info("Received callbacks from Rust, registering in Arbiter");
  callbacks_ = {on_le_connect,       on_le_disconnect,     intercept_packet,
                on_outgoing_mtu_req, on_incoming_mtu_resp, on_incoming_mtu_req};
}

void SendPacketToPeer(uint8_t tcb_idx, ::rust::Vec<uint8_t> buffer) {
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&RustGattAclArbiter::SendPacketToPeer,
                                   base::Unretained(&RustGattAclArbiter::Get()),
                                   tcb_idx, std::move(buffer)));
}

AclArbiter& GetArbiter() {
  return common::init_flags::private_gatt_is_enabled()
             ? static_cast<AclArbiter&>(RustGattAclArbiter::Get())
             : static_cast<AclArbiter&>(PassthroughAclArbiter::Get());
}

}  // namespace arbiter
}  // namespace shim
}  // namespace bluetooth
