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

// Adapted from register_notification_packet_test.cc

#include <gtest/gtest.h>

#include "avrcp_test_packets.h"
#include "packet_test_helper.h"
#include "register_notification_packet.h"

namespace bluetooth {
namespace avrcp {

using TestRegNotifReqPacket = TestPacketType<RegisterNotificationRequest>;
using TestRegNotifRspPacket = TestPacketType<RegisterNotificationResponse>;
// as small as 4

extern "C" int LLVMFuzzerTestOneInput(const char* data, size_t size) {
  std::vector<uint8_t> register_play_status_notification;

  // We will use stability testing to see the sensible max size for fuzzing.
  // Max BT packet size = 251
  // Expected packet size by the library is as small as 4.
  // Seems to raise exceptions below 15.

  if (size >= 15) {
    for (size_t x = 0; x < size; x++) {
      register_play_status_notification.push_back(data[x]);
    }

    auto test_packet =
        TestRegNotifReqPacket::Make(register_play_status_notification);

    test_packet->GetEventRegistered();
    test_packet->GetInterval();
    test_packet->GetData();
    test_packet->ToString();
  }

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
