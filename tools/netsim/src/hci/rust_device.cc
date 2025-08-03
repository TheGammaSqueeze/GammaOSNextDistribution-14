// Copyright 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "hci/rust_device.h"

#include <cstdint>

#include "netsim-daemon/src/ffi.rs.h"
#include "packets/link_layer_packets.h"
#include "phy.h"
#include "rust/cxx.h"

namespace netsim::hci::facade {
void RustDevice::Tick() { ::netsim::hci::facade::Tick(*callbacks_); }

void RustDevice::ReceiveLinkLayerPacket(
    ::model::packets::LinkLayerPacketView packet, rootcanal::Phy::Type type,
    int8_t rssi) {
  auto packet_vec = packet.bytes().bytes();
  auto slice = rust::Slice<const uint8_t>(packet_vec.data(), packet_vec.size());

  ::netsim::hci::facade::ReceiveLinkLayerPacket(
      *callbacks_, packet.GetSourceAddress().ToString(),
      packet.GetDestinationAddress().ToString(),
      static_cast<int8_t>(packet.GetType()), slice);
}

void RustBluetoothChip::SendLinkLayerLePacket(
    const rust::Slice<const uint8_t> packet, int8_t tx_power) const {
  std::vector<uint8_t> buffer(packet.begin(), packet.end());
  rust_device->SendLinkLayerPacket(buffer, rootcanal::Phy::Type::LOW_ENERGY,
                                   tx_power);
}
}  // namespace netsim::hci::facade
