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

#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

#include "hci/acl_manager/acl_connection.h"
#include "hci/address_with_type.h"
#include "os/handler.h"
#include "os/log.h"
#include "packet/packet_view.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

constexpr size_t kMaxQueuedPacketsPerConnection = 10;
constexpr size_t kL2capBasicFrameHeaderSize = 4;

namespace {
// This is a helper class to keep the state of the assembler and expose PacketView<>::Append.
class PacketViewForRecombination : public packet::PacketView<packet::kLittleEndian> {
 public:
  PacketViewForRecombination(const PacketView& packetView)
      : PacketView(packetView), received_first_(true) {}

  PacketViewForRecombination()
      : PacketView(PacketView<packet::kLittleEndian>(std::make_shared<std::vector<uint8_t>>())) {}

  void AppendPacketView(packet::PacketView<packet::kLittleEndian> to_append) {
    Append(to_append);
  }

  bool ReceivedFirstPacket() {
    return received_first_;
  }

 private:
  bool received_first_{};
};

// Per spec 5.1 Vol 2 Part B 5.3, ACL link shall carry L2CAP data. Therefore, an ACL packet shall
// contain L2CAP PDU. This function returns the PDU size of the L2CAP starting packet, or
// kL2capBasicFrameHeaderSize if it's invalid.
size_t GetL2capPduSize(packet::PacketView<packet::kLittleEndian> pdu) {
  if (pdu.size() < 2) {
    return kL2capBasicFrameHeaderSize;  // We need at least 4 bytes to send it to L2CAP
  }
  return (static_cast<size_t>(pdu[1]) << 8u) + pdu[0];
}

}  // namespace

struct assembler {
  assembler(AddressWithType address_with_type, AclConnection::QueueDownEnd* down_end, os::Handler* handler)
      : address_with_type_(address_with_type), down_end_(down_end), handler_(handler) {}
  AddressWithType address_with_type_;
  AclConnection::QueueDownEnd* down_end_;
  os::Handler* handler_;
  PacketViewForRecombination recombination_stage_{};
  std::shared_ptr<std::atomic_bool> enqueue_registered_ = std::make_shared<std::atomic_bool>(false);
  std::queue<packet::PacketView<packet::kLittleEndian>> incoming_queue_;

  ~assembler() {
    if (enqueue_registered_->exchange(false)) {
      down_end_->UnregisterEnqueue();
    }
  }

  // Invoked from some external Queue Reactable context
  std::unique_ptr<packet::PacketView<packet::kLittleEndian>> on_data_ready() {
    auto packet = incoming_queue_.front();
    incoming_queue_.pop();
    if (incoming_queue_.empty() && enqueue_registered_->exchange(false)) {
      down_end_->UnregisterEnqueue();
    }
    return std::make_unique<PacketView<packet::kLittleEndian>>(packet);
  }

  void on_incoming_packet(AclView packet) {
    PacketView<packet::kLittleEndian> payload = packet.GetPayload();
    auto broadcast_flag = packet.GetBroadcastFlag();
    if (broadcast_flag == BroadcastFlag::ACTIVE_PERIPHERAL_BROADCAST) {
      LOG_WARN("Dropping broadcast from remote");
      return;
    }
    auto packet_boundary_flag = packet.GetPacketBoundaryFlag();
    if (packet_boundary_flag == PacketBoundaryFlag::FIRST_NON_AUTOMATICALLY_FLUSHABLE) {
      LOG_ERROR("Controller is not allowed to send FIRST_NON_AUTOMATICALLY_FLUSHABLE to host except loopback mode");
      return;
    }
    if (packet_boundary_flag == PacketBoundaryFlag::CONTINUING_FRAGMENT) {
      if (!recombination_stage_.ReceivedFirstPacket()) {
        LOG_ERROR("Continuing fragment received without previous first, dropping it.");
        return;
      }
      recombination_stage_.AppendPacketView(payload);
    } else if (packet_boundary_flag == PacketBoundaryFlag::FIRST_AUTOMATICALLY_FLUSHABLE) {
      if (recombination_stage_.ReceivedFirstPacket()) {
        LOG_ERROR("Controller sent a starting packet without finishing previous packet. Drop previous one.");
      }
      recombination_stage_ = payload;
    }
    // Check the size of the packet
    size_t expected_size = GetL2capPduSize(recombination_stage_) + kL2capBasicFrameHeaderSize;
    if (expected_size < recombination_stage_.size()) {
      LOG_INFO("Packet size doesn't match L2CAP header, dropping it.");
      recombination_stage_ = PacketViewForRecombination();
      return;
    } else if (expected_size > recombination_stage_.size()) {
      // Wait for the next fragment before sending
      return;
    }
    if (incoming_queue_.size() > kMaxQueuedPacketsPerConnection) {
      LOG_ERROR("Dropping packet from %s due to congestion",
                 ADDRESS_TO_LOGGABLE_CSTR(address_with_type_));
      recombination_stage_ = PacketViewForRecombination();
      return;
    }

    incoming_queue_.push(recombination_stage_);
    recombination_stage_ = PacketViewForRecombination();
    if (!enqueue_registered_->exchange(true)) {
      down_end_->RegisterEnqueue(
          handler_, common::Bind(&assembler::on_data_ready, common::Unretained(this)));
    }
  }
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
