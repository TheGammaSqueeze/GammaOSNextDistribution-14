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

#include "list_player_application_setting_values.h"

#include <android-base/silent_death_test.h>
#include <gtest/gtest.h>

#include "avrcp_test_packets.h"
#include "packet_test_helper.h"

namespace bluetooth {
namespace avrcp {

using ListPlayerApplicationSettingValuesRequestTestPacket =
    TestPacketType<ListPlayerApplicationSettingValuesRequest>;
using ListPlayerApplicationSettingAttributeValuesRspTestPacket =
    TestPacketType<Packet>;

// Test parsing a List Player Application Setting Values Request
TEST(ListPlayerApplicationSettingValuesRequestPacketTest, getterTest) {
  auto test_packet = ListPlayerApplicationSettingValuesRequestTestPacket::Make(
      list_player_application_setting_attribute_values_request);

  ASSERT_EQ(test_packet->GetRequestedAttribute(), PlayerAttribute::REPEAT);
}

TEST(ListPlayerApplicationSettingValuesRequestPacketTest, validTest) {
  auto test_packet = ListPlayerApplicationSettingValuesRequestTestPacket::Make(
      list_player_application_setting_attribute_values_request);
  ASSERT_TRUE(test_packet->IsValid());
}

TEST(ListPlayerApplicationSettingValuesRequestPacketTest, invalidTest) {
  std::vector<uint8_t> packet_copy =
      list_player_application_setting_attribute_values_request;
  packet_copy.push_back(0x00);
  auto test_packet =
      ListPlayerApplicationSettingValuesRequestTestPacket::Make(packet_copy);
  ASSERT_FALSE(test_packet->IsValid());

  std::vector<uint8_t> short_packet = {
      0, 1, 2, 3, 4, 5, 6,
  };
  test_packet =
      ListPlayerApplicationSettingValuesRequestTestPacket::Make(short_packet);
  ASSERT_FALSE(test_packet->IsValid());
}

TEST(ListPlayerApplicationSettingValuesResponseBuilderTest, builderTest) {
  std::vector<uint8_t> vals = {0x01, 0x02, 0x03,
                               0x04};  // All possible repeat vals
  auto builder =
      ListPlayerApplicationSettingValuesResponseBuilder::MakeBuilder(vals);

  ASSERT_EQ(builder->size(),
            list_player_application_setting_attribute_values_response.size());

  auto test_packet =
      ListPlayerApplicationSettingAttributeValuesRspTestPacket::Make();
  builder->Serialize(test_packet);
  ASSERT_EQ(test_packet->GetData(),
            list_player_application_setting_attribute_values_response);
}

}  // namespace avrcp
}  // namespace bluetooth