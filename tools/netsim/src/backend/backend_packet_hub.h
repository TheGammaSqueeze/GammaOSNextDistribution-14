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

#pragma once

// Use gRPC HCI PacketType definitions so we don't expose Rootcanal's version
// outside of the Bluetooth Facade.
#include <cstdint>

#include "netsim/common.pb.h"
#include "netsim/hci_packet.pb.h"
#include "rust/cxx.h"

namespace netsim {
namespace backend {

using netsim::common::ChipKind;

/* Handle packet responses for the backend. */

void HandleResponse(uint32_t chip_id, const std::vector<uint8_t> &packet,
                    /* optional */ packet::HCIPacket_PacketType packet_type);

void HandleResponseCxx(uint32_t chip_id, const rust::Vec<rust::u8> &packet,
                       /* optional */ uint8_t packet_type);

}  // namespace backend
}  // namespace netsim
