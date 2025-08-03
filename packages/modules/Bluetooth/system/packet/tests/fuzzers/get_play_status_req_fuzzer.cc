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

// Adapted from get_play_status_packet_test.cc

#include <fuzzer/FuzzedDataProvider.h>
#include <gtest/gtest.h>
#include <string.h>

#include "avrcp_test_packets.h"
#include "get_play_status_packet.h"
#include "packet_test_helper.h"

namespace bluetooth {
namespace avrcp {

using TestGetPlayStatusRspPacket = TestPacketType<Packet>;

extern "C" int LLVMFuzzerTestOneInput(uint8_t* data, size_t size) {
  FuzzedDataProvider data_provider(data, size);

  auto builder = GetPlayStatusResponseBuilder::MakeBuilder(
      0, data_provider.ConsumeIntegral<uint32_t>(), 0);
  auto test_packet = TestGetPlayStatusRspPacket::Make();
  builder->Serialize(test_packet);

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
