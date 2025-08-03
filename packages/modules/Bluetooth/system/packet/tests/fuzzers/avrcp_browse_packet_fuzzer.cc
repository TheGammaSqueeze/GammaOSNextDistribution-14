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

// Adapted from AVRCP Browse Packet Test

#include <gtest/gtest.h>

#include "avrcp_browse_packet.h"
#include "avrcp_test_packets.h"
#include "packet_test_helper.h"

namespace bluetooth {

namespace avrcp {

using TestBrowsePacket = TestPacketType<BrowsePacket>;

// buildpacket test
extern "C" int LLVMFuzzerTestOneInput(const char* data, size_t size) {
  std::vector<uint8_t> get_folder_items_request;

  // Expected packet size by the library is ~8
  if (size > 10) {
    for (size_t x = 0; x < size; x++) {
      get_folder_items_request.push_back(data[x]);
    }

    auto test_packet = TestBrowsePacket::Make(get_folder_items_request);

    test_packet->GetPdu();
    test_packet->GetLength();
  }

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
