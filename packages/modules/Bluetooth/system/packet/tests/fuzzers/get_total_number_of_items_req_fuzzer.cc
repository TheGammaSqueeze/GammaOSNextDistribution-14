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

// Adapted from get_total_number_of_items_test

#include <gtest/gtest.h>

#include "avrcp_test_packets.h"
#include "get_total_number_of_items.h"
#include "packet_test_helper.h"

namespace bluetooth {
namespace avrcp {

using TestGetTotalNumItemsReqPacket =
    TestPacketType<GetTotalNumberOfItemsRequest>;

extern "C" int LLVMFuzzerTestOneInput(const char* data, size_t size) {
  std::vector<uint8_t> get_total_number_of_items_request_now_playing;

  // Expected packet size by the library is ~4
  if (size >= 8) {
    for (size_t x = 0; x < size; x++) {
      get_total_number_of_items_request_now_playing.push_back(data[x]);
    }

    auto test_packet = TestGetTotalNumItemsReqPacket::Make(
        get_total_number_of_items_request_now_playing);
    test_packet->GetScope();
    test_packet->IsValid();
    test_packet->GetData();
  }

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
