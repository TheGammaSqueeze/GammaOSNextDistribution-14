/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "stack/btm/sco_pkt_status.h"

#include <gtest/gtest.h>

namespace {

using testing::Test;

class ScoPktStatusTest : public Test {
 public:
 protected:
  void SetUp() override {}
  void TearDown() override {}
};

TEST_F(ScoPktStatusTest, Update) {
  tBTM_SCO_PKT_STATUS pkt_status;
  pkt_status.init();
  pkt_status.update(true);
  ASSERT_NE(pkt_status.begin_ts_raw_us(), (uint64_t)0);
  ASSERT_EQ(pkt_status.end_ts_raw_us(), pkt_status.begin_ts_raw_us() + 7500);
  ASSERT_EQ(pkt_status.data_to_hex_string(), "01");
  ASSERT_EQ(pkt_status.data_to_binary_string(), "1");
}

TEST_F(ScoPktStatusTest, data_to_string) {
  bool pl[9] = {1, 0, 1, 1, 0, 1, 1, 1, 1};
  tBTM_SCO_PKT_STATUS pkt_status;
  pkt_status.init();
  for (bool b : pl) pkt_status.update(b);
  ASSERT_EQ(pkt_status.data_to_binary_string(), "101101111");
  ASSERT_EQ(pkt_status.data_to_hex_string(), "ed01");
}

TEST_F(ScoPktStatusTest, data_full) {
  bool pl[BTM_PKT_STATUS_LEN * 8] = {
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 0, 0, 0, 0, 0, /* 00 */
      0, 0, 0, 1, 1, 1, 1, 1, /* f8 */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 1, /* ff */
      1, 1, 1, 1, 1, 1, 1, 0, /* 7f */
      0, 0, 0, 0, 0, 0, 0, 0  /* 00 */
  };
  tBTM_SCO_PKT_STATUS pkt_status;
  pkt_status.init();
  for (bool b : pl) pkt_status.update(b);
  ASSERT_EQ(pkt_status.data_to_binary_string(),
            "111111110000000000000000000000000000000000000000000000000000000000"
            "000000000000000000000000000000000000000000000000000000000000000000"
            "000000000000000000000000000000000000000000000000000000000000000000"
            "000000000000000000000000000000000000000000000000000000000000000000"
            "000000000000000000000000000000000000000000000000000000000000000000"
            "000000000111111111111111111111111111111111111111111111111111111111"
            "111111111111111111111111111111111111111111111111111111111111111111"
            "11111111111111111111111111111111111111111000000000");
  uint64_t begin_ts_raw_us = pkt_status.begin_ts_raw_us();
  ASSERT_EQ(pkt_status.data_to_hex_string(),
            "ff0000000000000000000000000000000000000000000000000000000000000000"
            "000000000000000000f8ffffffffffffffffffffffffffffffffffffff7f00");
  pkt_status.update(true);
  ASSERT_NE(begin_ts_raw_us, pkt_status.begin_ts_raw_us());
  ASSERT_EQ(pkt_status.end_ts_raw_us(),
            pkt_status.begin_ts_raw_us() +
                BTM_PKT_STATUS_WBS_FRAME_US * BTM_PKT_STATUS_LEN * 8);
  ASSERT_EQ(pkt_status.data_to_binary_string(),
            "11111110000000000000000000000000000000000000000000000000000000000"
            "000000000000000000000000000000000000000000000000000000000000000000"
            "000000000000000000000000000000000000000000000000000000000000000000"
            "000000000000000000000000000000000000000000000000000000000000000000"
            "000000000000000000000000000000000000000000000000000000000000000000"
            "000000000111111111111111111111111111111111111111111111111111111111"
            "111111111111111111111111111111111111111111111111111111111111111111"
            "111111111111111111111111111111111111111110000000001");
  ASSERT_EQ(pkt_status.data_to_hex_string(),
            "fe0000000000000000000000000000000000000000000000000000000000000000"
            "000000000000000000f9ffffffffffffffffffffffffffffffffffffff7e01");
}

}  // namespace
