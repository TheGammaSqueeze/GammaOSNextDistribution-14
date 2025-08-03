/*
 * Copyright 2019 The Android Open Source Project
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

#define PACKET_TESTING
#include "l2cap/l2cap_packets.h"

#include <gtest/gtest.h>
#include <forward_list>
#include <memory>

#include "os/log.h"
#include "packet/bit_inserter.h"
#include "packet/raw_builder.h"

using bluetooth::packet::BitInserter;
using bluetooth::packet::RawBuilder;
using std::vector;

namespace bluetooth {
namespace l2cap {

std::vector<uint8_t> extended_information_start_frame = {
    0x0B, /* First size byte */
    0x00, /* Second size byte */
    0xc1, /* First ChannelId byte */
    0xc2, /**/
    0x4A, /* 0x12 ReqSeq, Final, IFrame */
    0xD0, /* 0x13 ReqSeq */
    0x89, /* 0x21 TxSeq sar = START */
    0x8C, /* 0x23 TxSeq  */
    0x10, /* first length byte */
    0x11, /**/
    0x01, /* first payload byte */
    0x02, 0x03, 0x04, 0x05,
};

DEFINE_AND_INSTANTIATE_ExtendedInformationStartFrameReflectionTest(extended_information_start_frame);

std::vector<uint8_t> i_frame_with_fcs = {0x0E, 0x00, 0x40, 0x00, 0x02, 0x00, 0x00, 0x01, 0x02,
                                         0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x38, 0x61};
DEFINE_AND_INSTANTIATE_StandardInformationFrameWithFcsReflectionTest(i_frame_with_fcs);

std::vector<uint8_t> rr_frame_with_fcs = {0x04, 0x00, 0x40, 0x00, 0x01, 0x01, 0xD4, 0x14};
DEFINE_AND_INSTANTIATE_StandardSupervisoryFrameWithFcsReflectionTest(rr_frame_with_fcs);

std::vector<uint8_t> g_frame = {0x03, 0x00, 0x02, 0x00, 0x01, 0x02, 0x03};
DEFINE_AND_INSTANTIATE_GroupFrameReflectionTest(g_frame);

std::vector<uint8_t> config_mtu_request = {0x04, 0x05, 0x08, 0x00, 0x41, 0x00, 0x00, 0x00, 0x01, 0x02, 0xa0, 0x02};
DEFINE_AND_INSTANTIATE_ConfigurationRequestReflectionTest(config_mtu_request);

std::vector<uint8_t> config_request_one_defined_option = {0x04, 0x05, 0x08, 0x00, 0x41, 0x00,
                                                          0x00, 0x00, 0x01, 0x02, 0x12, 0x34};
std::vector<uint8_t> config_request_two_defined_options = {0x04, 0x05, 0x0c, 0x00, 0x41, 0x00, 0x00, 0x00,
                                                           0x01, 0x02, 0x12, 0x34, 0x02, 0x02, 0x56, 0x78};
std::vector<uint8_t> config_request_two_undefined_options = {0x04, 0x05, 0x0e, 0x00, 0x41, 0x00, 0x00, 0x00, 0x7f,
                                                             0x02, 0x01, 0x00, 0x7e, 0x04, 0x11, 0x11, 0x00, 0x00};
std::vector<uint8_t> config_request_hint_one_defined_option = {0x04, 0x05, 0x08, 0x00, 0x41, 0x00,
                                                               0x00, 0x00, 0x81, 0x02, 0x12, 0x34};
std::vector<uint8_t> config_request_hint_two_undefined_options = {0x04, 0x05, 0x0c, 0x00, 0x41, 0x00, 0x00, 0x00,
                                                                  0x90, 0x02, 0x01, 0x00, 0x91, 0x02, 0x11, 0x11};
TEST(L2capPacketsTest, testConfigRequestOptions) {
  {
    std::shared_ptr<std::vector<uint8_t>> view_bytes =
        std::make_shared<std::vector<uint8_t>>(config_request_one_defined_option);

    PacketView<kLittleEndian> packet_bytes_view(view_bytes);
    auto view = ConfigurationRequestView::Create(ControlView::Create(packet_bytes_view));
    ASSERT_TRUE(view.IsValid());
    ASSERT_EQ(1ul, view.GetConfig().size());
  }

  {
    std::shared_ptr<std::vector<uint8_t>> view_bytes =
        std::make_shared<std::vector<uint8_t>>(config_request_two_defined_options);

    PacketView<kLittleEndian> packet_bytes_view(view_bytes);
    auto view = ConfigurationRequestView::Create(ControlView::Create(packet_bytes_view));
    ASSERT_TRUE(view.IsValid());
    ASSERT_EQ(2ul, view.GetConfig().size());
  }

  {
    std::shared_ptr<std::vector<uint8_t>> view_bytes =
        std::make_shared<std::vector<uint8_t>>(config_request_two_undefined_options);

    PacketView<kLittleEndian> packet_bytes_view(view_bytes);
    auto view = ConfigurationRequestView::Create(ControlView::Create(packet_bytes_view));
    ASSERT_TRUE(view.IsValid());
    ASSERT_EQ(2ul, view.GetConfig().size());
  }

  {
    std::shared_ptr<std::vector<uint8_t>> view_bytes =
        std::make_shared<std::vector<uint8_t>>(config_request_hint_one_defined_option);

    PacketView<kLittleEndian> packet_bytes_view(view_bytes);
    auto view = ConfigurationRequestView::Create(ControlView::Create(packet_bytes_view));
    ASSERT_TRUE(view.IsValid());
    ASSERT_EQ(1ul, view.GetConfig().size());
  }

  {
    std::shared_ptr<std::vector<uint8_t>> view_bytes =
        std::make_shared<std::vector<uint8_t>>(config_request_hint_two_undefined_options);

    PacketView<kLittleEndian> packet_bytes_view(view_bytes);
    auto view = ConfigurationRequestView::Create(ControlView::Create(packet_bytes_view));
    ASSERT_TRUE(view.IsValid());
    ASSERT_EQ(2ul, view.GetConfig().size());
  }
}

DEFINE_ConfigurationRequestReflectionFuzzTest();

TEST(L2capFuzzRegressions, ConfigurationRequestFuzz_5691566077247488) {
  uint8_t bluetooth_gd_fuzz_test_5691566077247488[] = {
      0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
  };
  RunConfigurationRequestReflectionFuzzTest(bluetooth_gd_fuzz_test_5691566077247488,
                                            sizeof(bluetooth_gd_fuzz_test_5691566077247488));
}

TEST(L2capFuzzRegressions, ConfigurationRequestFuzz_5747922062802944) {
  uint8_t bluetooth_gd_fuzz_test_5747922062802944[] = {
      0x04, 0x02, 0x02, 0x7f, 0x3f, 0x7f, 0x3f, 0x7e, 0x7f,
  };
  RunConfigurationRequestReflectionFuzzTest(bluetooth_gd_fuzz_test_5747922062802944,
                                            sizeof(bluetooth_gd_fuzz_test_5747922062802944));
}

TEST(L2capFuzzRegressions, ConfigurationRequestFuzz_5202709231697920) {
  uint8_t bluetooth_gd_fuzz_test_5747922062802944[] = {
      0x04, 0x01, 0x45, 0x45, 0x05, 0x01, 0x01, 0x45, 0x05, 0x01,
  };

  RunConfigurationRequestReflectionFuzzTest(bluetooth_gd_fuzz_test_5747922062802944,
                                            sizeof(bluetooth_gd_fuzz_test_5747922062802944));
}

TEST(L2capFuzzRegressions, ConfigurationRequestFuzz_manual_5655429176229888) {
  std::vector<uint8_t> vec{0xc7, 0x0f, 0x0b, 0xe8, 0xfb, 0xff};

  auto shared_bytes = std::make_shared<std::vector<uint8_t>>(vec);
  PacketView<kLittleEndian> packet_bytes_view(shared_bytes);

  auto bfwf = BasicFrameWithFcsView::Create(packet_bytes_view);
  ASSERT_FALSE(bfwf.IsValid());
  auto sfwf = StandardFrameWithFcsView::Create(bfwf);
  ASSERT_FALSE(sfwf.IsValid());
  auto sif = StandardInformationFrameWithFcsView::Create(sfwf);
  ASSERT_FALSE(sif.IsValid());
}
}  // namespace l2cap
}  // namespace bluetooth
