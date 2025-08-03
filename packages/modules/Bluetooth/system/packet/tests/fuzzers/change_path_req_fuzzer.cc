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

// Adapted from change_path_packet_test.cc

#include <base/logging.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "avrcp_test_packets.h"
#include "change_path.h"
#include "packet_test_helper.h"

namespace bluetooth {
namespace avrcp {

using TestChangePathReqPacket = TestPacketType<ChangePathRequest>;

// Getter
extern "C" int LLVMFuzzerTestOneInput(const char* data, size_t size) {
  std::vector<uint8_t> change_path_request_data;

  // Minimum for this type of packet appears to be 14 bytes.
  if (size > 14) {
    for (size_t x = 0; x < size; x++) {
      change_path_request_data.push_back(data[x]);
    }

    auto test_packet = TestChangePathReqPacket::Make(change_path_request_data);

    test_packet->GetUidCounter();
    test_packet->GetDirection();
    test_packet->GetUid();
    test_packet->IsValid();
    test_packet->ToString();
  }

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
