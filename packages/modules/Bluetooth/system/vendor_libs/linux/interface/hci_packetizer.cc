//
// Copyright 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include "hci_packetizer.h"

#define LOG_TAG "android.hardware.bluetooth.hci_packetizer"
#include <dlfcn.h>
#include <fcntl.h>
#include <log/log.h>

namespace {

const size_t header_size_for_type[] = {0,
                                       HCI_COMMAND_PREAMBLE_SIZE,
                                       HCI_ACL_PREAMBLE_SIZE,
                                       HCI_SCO_PREAMBLE_SIZE,
                                       HCI_EVENT_PREAMBLE_SIZE,
                                       HCI_ISO_PREAMBLE_SIZE};
const size_t packet_length_offset_for_type[] = {0,
                                                HCI_LENGTH_OFFSET_CMD,
                                                HCI_LENGTH_OFFSET_ACL,
                                                HCI_LENGTH_OFFSET_SCO,
                                                HCI_LENGTH_OFFSET_EVT,
                                                HCI_LENGTH_OFFSET_ISO};

size_t HciGetPacketLengthForType(HciPacketType type,
                                 const std::vector<uint8_t>& preamble) {
  size_t offset = packet_length_offset_for_type[type];
  if (type != HCI_PACKET_TYPE_ACL_DATA) return preamble[offset];
  return (((preamble[offset + 1]) << 8) | preamble[offset]);
}

}  // namespace

namespace android {
namespace hardware {
namespace bluetooth {
namespace hci {

const hidl_vec<uint8_t>& HciPacketizer::GetPacket() const { return packet_; }

size_t HciPacketizer::fill_header(HciPacketType packet_type,
                                  const std::vector<uint8_t>& buffer,
                                  size_t offset) {
  size_t header_size = header_size_for_type[static_cast<size_t>(packet_type)];
  if (bytes_remaining_ == 0) {
    bytes_remaining_ = header_size;
    packet_buffer_.clear();
  }
  // Add as much of the header as is available to the packet.
  size_t bytes_to_copy = std::min(bytes_remaining_, buffer.size() - offset);
  packet_buffer_.insert(packet_buffer_.end(), buffer.begin() + offset,
                        buffer.begin() + offset + bytes_to_copy);
  bytes_remaining_ -= bytes_to_copy;

  // If the header is complete, find the payload size and transition.
  if (bytes_remaining_ == 0) {
    bytes_remaining_ = HciGetPacketLengthForType(packet_type, packet_buffer_);
    // If there are no bytes remaining, this is a completed packet.
    if (bytes_remaining_ > 0) {
      state_ = HCI_PAYLOAD;
    }
  }
  return bytes_to_copy;
}

void HciPacketizer::fill_payload(const std::vector<uint8_t>& buffer,
                                 size_t offset) {
  // Add as much of the payload as is available to the end of the packet.
  size_t bytes_to_copy = std::min(bytes_remaining_, buffer.size() - offset);
  packet_buffer_.insert(packet_buffer_.end(), buffer.begin() + offset,
                        buffer.begin() + offset + bytes_to_copy);
  bytes_remaining_ -= bytes_to_copy;

  // If there are no bytes remaining, this is a completed packet.
  if (bytes_remaining_ == 0) {
    state_ = HCI_HEADER;
  }
}

bool HciPacketizer::OnDataReady(HciPacketType packet_type,
                                const std::vector<uint8_t>& buffer,
                                size_t offset) {
  // Start with the header.
  size_t header_bytes = 0;
  if (state_ == HCI_HEADER) {
    header_bytes = fill_header(packet_type, buffer, offset);
  }
  // If there are bytes left in this packet, fill the payload.
  if (state_ == HCI_PAYLOAD && bytes_remaining_ > 0) {
    if (offset + header_bytes < buffer.size()) {
      fill_payload(buffer, offset + header_bytes);
    }
  }
  // If there are no bytes remaining, this is a completed packet.
  if (bytes_remaining_ == 0) {
    packet_.setToExternal(packet_buffer_.data(), packet_buffer_.size());
  }
  return bytes_remaining_ == 0;
}

}  // namespace hci
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
