/*
 *
 *  Copyright 2021 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

#include "stack/acl/peer_packet_types.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace {

using testing::_;
using testing::DoAll;
using testing::NotNull;
using testing::Pointee;
using testing::Return;
using testing::SaveArg;
using testing::SaveArgPointee;
using testing::StrEq;
using testing::StrictMock;
using testing::Test;

class PeerPacketTest : public Test {
 public:
 protected:
  void SetUp() override {}
  void TearDown() override {}
};

TEST_F(PeerPacketTest, all_ones) {
  const BD_FEATURES bd_features = {0xff, 0xff, 0xff, 0xff,
                                   0xff, 0xff, 0xff, 0xff};
  PeerPacketTypes peer_packet_types(bd_features);
  ASSERT_EQ(peer_packet_types.acl.supported, 0xcc18);
  ASSERT_EQ(peer_packet_types.acl.unsupported, 0x0);
}

TEST_F(PeerPacketTest, 3SLOT_DH3_DM3) {
  const BD_FEATURES bd_features = {0x01, 0x00, 0x00, 0x00,
                                   0x00, 0x00, 0x00, 0x00};
  PeerPacketTypes peer_packet_types(bd_features);
  ASSERT_EQ(peer_packet_types.acl.supported, 0x0c18);
  ASSERT_EQ(peer_packet_types.acl.unsupported, 0x3306);
}

TEST_F(PeerPacketTest, 5SLOT_DH5_DM5) {
  const BD_FEATURES bd_features = {0x02, 0x00, 0x00, 0x00,
                                   0x00, 0x00, 0x00, 0x00};
  PeerPacketTypes peer_packet_types(bd_features);
  ASSERT_EQ(peer_packet_types.acl.supported, 0xc018);
  ASSERT_EQ(peer_packet_types.acl.unsupported, 0x3306);
}

TEST_F(PeerPacketTest, 2Mb_support) {
  const BD_FEATURES bd_features = {0x00, 0x00, 0x00, 0x02,
                                   0x00, 0x00, 0x00, 0x00};
  PeerPacketTypes peer_packet_types(bd_features);
  ASSERT_EQ(peer_packet_types.acl.supported, 0x0018);
  ASSERT_EQ(peer_packet_types.acl.unsupported, 0x3304);
}

TEST_F(PeerPacketTest, 3Mb_support) {
  const BD_FEATURES bd_features = {0x00, 0x00, 0x00, 0x04,
                                   0x00, 0x00, 0x00, 0x00};
  PeerPacketTypes peer_packet_types(bd_features);
  ASSERT_EQ(peer_packet_types.acl.supported, 0x0018);
  ASSERT_EQ(peer_packet_types.acl.unsupported, 0x3302);
}

}  // namespace
