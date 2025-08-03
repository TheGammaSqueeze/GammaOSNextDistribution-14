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

// Adapted from avrcp_packet_test.cc
#include <stddef.h>
#include <stdint.h>
#include "avrcp_packet.h"
#include "avrcp_test_packets.h"
#include "packet_test_helper.h"

namespace bluetooth {

// A helper class that has public accessors to protected methods
class TestPacketBuilder : public PacketBuilder {
 public:
  static std::unique_ptr<TestPacketBuilder> MakeBuilder(
      std::vector<uint8_t> data) {
    std::unique_ptr<TestPacketBuilder> builder(new TestPacketBuilder(data));
    return builder;
  }

  // Make all the utility functions public
  using PacketBuilder::AddPayloadOctets1;
  using PacketBuilder::AddPayloadOctets2;
  using PacketBuilder::AddPayloadOctets3;
  using PacketBuilder::AddPayloadOctets4;
  using PacketBuilder::AddPayloadOctets6;
  using PacketBuilder::AddPayloadOctets8;
  using PacketBuilder::ReserveSpace;

  size_t size() const override { return data_.size(); };

  bool Serialize(const std::shared_ptr<Packet>& pkt) override {
    ReserveSpace(pkt, size());

    for (uint8_t byte : data_) {
      AddPayloadOctets1(pkt, byte);
    }

    return true;
  }

  explicit TestPacketBuilder(std::vector<uint8_t> data) : data_(data) {}

  std::vector<uint8_t> data_;
};

namespace avrcp {

using TestAvrcpPacket = TestPacketType<Packet>;

extern "C" int LLVMFuzzerTestOneInput(const char* data, size_t size) {
  std::vector<uint8_t> get_capabilities_request_payload;

  if (size >= 8) {
    get_capabilities_request.push_back(0);
    for (int x = 0; x < 6; x++) {
      get_capabilities_request_payload.push_back(data[x]);
    }

    auto cap_req_builder =
        TestPacketBuilder::MakeBuilder(get_capabilities_request_payload);

    auto builder = PacketBuilder::MakeBuilder(
        CType::STATUS, 0x09, 0x00, Opcode::VENDOR, std::move(cap_req_builder));

    auto test_packet = TestAvrcpPacket::Make();
  }

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
