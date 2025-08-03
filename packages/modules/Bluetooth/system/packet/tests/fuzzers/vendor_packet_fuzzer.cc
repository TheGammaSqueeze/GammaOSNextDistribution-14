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

// Adapted from vendor_packet_test.cc

#include <gtest/gtest.h>
#include <tuple>

#include "avrcp_test_packets.h"
#include "packet_test_helper.h"
#include "vendor_packet.h"

namespace bluetooth {

namespace avrcp {

using TestVendorPacket = TestPacketType<VendorPacket>;

using TestParam = std::tuple<std::vector<uint8_t>, CommandPdu>;
class VendorPacketTest : public ::testing::TestWithParam<TestParam> {
 public:
  std::vector<uint8_t> GetPacketData() { return std::get<0>(GetParam()); }
  CommandPdu GetCommandPdu() { return std::get<1>(GetParam()); }
};

extern "C" int LLVMFuzzerTestOneInput(const char* data, size_t size) {
  std::vector<uint8_t> short_vendor_packet;

  // Expected packet size by the library is > 19
  if (size >= 19) {
    for (size_t x = 0; x < size; x++) {
      short_vendor_packet.push_back(data[x]);
    }

  } else {
    return 0;
  }

  auto test_packet = TestVendorPacket::Make(short_vendor_packet);

  test_packet->GetCompanyId();
  test_packet->GetCommandPdu();
  test_packet->GetPacketType();
  test_packet->GetParameterLength();
  test_packet->IsValid();
  test_packet->ToString();

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
