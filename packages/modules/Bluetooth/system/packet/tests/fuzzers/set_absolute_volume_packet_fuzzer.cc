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

// Adapted from set_absolute_volume_packet_test.cc

#include <gtest/gtest.h>

#include "avrcp_test_packets.h"
#include "packet_test_helper.h"
#include "set_absolute_volume.h"

namespace bluetooth {
namespace avrcp {

using TestSetVolumeRspPacket = TestPacketType<SetAbsoluteVolumeResponse>;

extern "C" int LLVMFuzzerTestOneInput(const char* data, size_t size) {
  std::vector<uint8_t> set_absolute_volume_response;

  // We will use stability testing to see the sensible max size for fuzzing.
  // Max BT packet size = 251
  // Expected packet size by the library is ~5
  if (size >= 12) {
    for (size_t x = 0; x < size; x++) {
      set_absolute_volume_response.push_back(data[x]);
    }
    auto test_packet =
        TestSetVolumeRspPacket::Make(set_absolute_volume_response);

    test_packet->IsValid();
    test_packet->GetVolume();
    test_packet->GetData();
  }

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
