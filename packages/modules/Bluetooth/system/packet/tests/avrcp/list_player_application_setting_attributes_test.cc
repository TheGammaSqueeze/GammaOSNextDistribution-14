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

#include "list_player_application_setting_attributes.h"

#include <gtest/gtest.h>

#include "avrcp_test_packets.h"
#include "packet_test_helper.h"

namespace bluetooth {
namespace avrcp {

using TestListPlayerApplicationSettingAttributesRspPacket =
    TestPacketType<Packet>;

TEST(ListPlayerApplicationSettingAttributesResponseBuilderTest, builderTest) {
  std::vector<PlayerAttribute> attrs = {PlayerAttribute::REPEAT,
                                        PlayerAttribute::SHUFFLE};
  auto builder =
      ListPlayerApplicationSettingAttributesResponseBuilder::MakeBuilder(attrs);

  ASSERT_EQ(builder->size(),
            list_player_application_setting_attributes_response.size());

  auto test_packet =
      TestListPlayerApplicationSettingAttributesRspPacket::Make();
  builder->Serialize(test_packet);
  ASSERT_EQ(test_packet->GetData(),
            list_player_application_setting_attributes_response);
}

}  // namespace avrcp
}  // namespace bluetooth