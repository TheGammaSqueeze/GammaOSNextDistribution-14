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

// Adapted from play_item_packet_test.cc

#include <gtest/gtest.h>

#include "avrcp_test_packets.h"
#include "packet_test_helper.h"
#include "play_item.h"

namespace bluetooth {
namespace avrcp {

using TestPlayItemReqPacket = TestPacketType<PlayItemRequest>;

extern "C" int LLVMFuzzerTestOneInput(const char* data, size_t size) {
  std::vector<uint8_t> play_item_request;

  // We will use stability testing to see the sensible max size for fuzzing.
  // Max BT packet size = 251
  // Expected packet size by the library is ~21 but previous tests account for
  // as low as 4
  if (size >= 21) {
    for (size_t x = 0; x < size; x++) {
      play_item_request.push_back(data[x]);
    }

    auto test_packet = TestPlayItemReqPacket::Make(play_item_request);

    test_packet->GetScope();
    test_packet->GetUid();
    test_packet->GetUidCounter();
    test_packet->IsValid();
    test_packet->GetData();
  }

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
