/*
 * Copyright 2023 The Android Open Source Project
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

#include "get_current_player_application_setting_value.h"

#include <gtest/gtest.h>

#include "avrcp_test_packets.h"
#include "packet_test_helper.h"

namespace bluetooth {
namespace avrcp {

using GetCurrentPlayerApplicationSettingValueRequestTestPacket =
    TestPacketType<GetCurrentPlayerApplicationSettingValueRequest>;
using GetCurrentPlayerApplicationSettingValueRspTestPacket =
    TestPacketType<Packet>;

// Test parsing a Get Current Player Application Setting Value Request
TEST(GetCurrentPlayerApplicationSettingValueRequestPacketTest, getterTest) {
  std::vector<PlayerAttribute> attrs = {PlayerAttribute::REPEAT,
                                        PlayerAttribute::SHUFFLE};
  auto test_packet =
      GetCurrentPlayerApplicationSettingValueRequestTestPacket::Make(
          get_current_player_application_setting_value_request);

  ASSERT_EQ(test_packet->GetNumberOfRequestedAttributes(), 2);
  ASSERT_EQ(test_packet->GetRequestedAttributes(), attrs);
}

TEST(GetCurrentPlayerApplicationSettingValueRequestPacketTest, validTest) {
  auto test_packet =
      GetCurrentPlayerApplicationSettingValueRequestTestPacket::Make(
          get_current_player_application_setting_value_request);
  ASSERT_TRUE(test_packet->IsValid());
}

TEST(GetCurrentPlayerApplicationSettingValueRequestPacketTest, invalidTest) {
  std::vector<uint8_t> packet_copy =
      get_current_player_application_setting_value_request;
  packet_copy.push_back(0x00);
  auto test_packet =
      GetCurrentPlayerApplicationSettingValueRequestTestPacket::Make(
          packet_copy);
  ASSERT_FALSE(test_packet->IsValid());

  std::vector<uint8_t> short_packet = {
      0, 1, 2, 3, 4, 5, 6,
  };
  test_packet = GetCurrentPlayerApplicationSettingValueRequestTestPacket::Make(
      short_packet);
  ASSERT_FALSE(test_packet->IsValid());
}

TEST(GetCurrentPlayerApplicationSettingValueResponseBuilderTest, builderTest) {
  std::vector<PlayerAttribute> attrs = {PlayerAttribute::REPEAT,
                                        PlayerAttribute::SHUFFLE};
  std::vector<uint8_t> vals = {0x01, 0x01};  // All values: OFF
  auto builder =
      GetCurrentPlayerApplicationSettingValueResponseBuilder::MakeBuilder(attrs,
                                                                          vals);

  ASSERT_EQ(builder->size(),
            get_current_player_application_setting_value_response.size());

  auto test_packet =
      GetCurrentPlayerApplicationSettingValueRspTestPacket::Make();
  builder->Serialize(test_packet);
  ASSERT_EQ(test_packet->GetData(),
            get_current_player_application_setting_value_response);
}

}  // namespace avrcp
}  // namespace bluetooth