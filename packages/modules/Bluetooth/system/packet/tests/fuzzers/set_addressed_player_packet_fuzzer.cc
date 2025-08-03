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

// set_addressed_player_packet_test.cc

#include <gtest/gtest.h>

#include "avrcp_test_packets.h"
#include "packet_test_helper.h"
#include "set_addressed_player.h"

namespace bluetooth {
namespace avrcp {

using TestSetAddrPlayerPacket = TestPacketType<SetAddressedPlayerRequest>;

extern "C" int LLVMFuzzerTestOneInput(const char* data, size_t size) {
  std::vector<uint8_t> short_set_addressed_player_request;
  // Expected packet size by the library is ~9
  if (size >= 9) {
    for (size_t x = 0; x < size; x++) {
      short_set_addressed_player_request.push_back(data[x]);
    }
    auto test_packet =
        TestSetAddrPlayerPacket::Make(set_addressed_player_request);

    test_packet->GetPlayerId();
    test_packet->GetData();
  }
  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
