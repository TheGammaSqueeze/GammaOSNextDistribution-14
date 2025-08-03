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

// Adapted from set_browsed_player_packet.cc

#include <gtest/gtest.h>

#include "avrcp_test_packets.h"
#include "packet_test_helper.h"
#include "set_browsed_player.h"

namespace bluetooth {
namespace avrcp {

using TestSetBrowsedPlayerPacket = TestPacketType<SetBrowsedPlayerRequest>;

extern "C" int LLVMFuzzerTestOneInput(const char* data, size_t size) {
  std::vector<uint8_t> set_browsed_player_response;

  // Expected packet size by the library is ~5
  if (size >= 5) {
    for (size_t x = 0; x < size; x++) {
      set_browsed_player_response.push_back(data[x]);
    }

    auto test_packet =
        TestSetBrowsedPlayerPacket::Make(set_browsed_player_response);

    test_packet->GetPlayerId();
    test_packet->GetData();
    test_packet->IsValid();
    test_packet->ToString();
  }

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
