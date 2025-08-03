/*
 *
 *  Copyright 2023 The Android Open Source Project
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

#include "bta_rfcomm_scn.h"

#include <gtest/gtest.h>

#include "bta/jv/bta_jv_int.h"      // tBTA_JV_CB
#include "stack/include/rfcdefs.h"  // RFCOMM_MAX_SCN

using testing::Test;

class BtaRfcommScnTest : public Test {
 public:
 protected:
  void SetUp() override {
    tBTA_JV_DM_CBACK* p_cback = [](tBTA_JV_EVT, tBTA_JV*, uint32_t) {};
    bta_jv_enable(p_cback);
  }

  void TearDown() override {}
};

TEST_F(BtaRfcommScnTest, scn_available_after_available_index) {
  ASSERT_EQ(BTA_AllocateSCN(), 2);
  ASSERT_EQ(BTA_AllocateSCN(), 3);
  ASSERT_TRUE(BTA_TryAllocateSCN(4));
  ASSERT_TRUE(BTA_TryAllocateSCN(5));

  // Available index should be 3, and the next available scn is 6
  ASSERT_EQ(BTA_AllocateSCN(), 6);
}

TEST_F(BtaRfcommScnTest, scn_available_before_available_index) {
  for (uint8_t scn = 2; scn <= RFCOMM_MAX_SCN; scn++) {
    ASSERT_TRUE(BTA_TryAllocateSCN(scn));
  }
  ASSERT_TRUE(BTA_FreeSCN(28));
  ASSERT_EQ(BTA_AllocateSCN(), 28);
  ASSERT_TRUE(BTA_FreeSCN(2));

  // Available index is 27, and the available scn is 2
  ASSERT_EQ(BTA_AllocateSCN(), 2);
}

TEST_F(BtaRfcommScnTest, can_allocate_all_scns) {
  for (uint8_t scn = 2; scn <= RFCOMM_MAX_SCN; scn++) {
    ASSERT_EQ(BTA_AllocateSCN(), scn);
  }
}

TEST_F(BtaRfcommScnTest, only_last_scn_available) {
  // Fill all relevant SCN except the last
  for (uint8_t scn = 2; scn < RFCOMM_MAX_SCN; scn++) {
    ASSERT_EQ(BTA_AllocateSCN(), scn);
  }

  ASSERT_EQ(BTA_AllocateSCN(), RFCOMM_MAX_SCN);
}

TEST_F(BtaRfcommScnTest, no_scn_available) {
  for (uint8_t scn = 2; scn <= RFCOMM_MAX_SCN; scn++) {
    ASSERT_EQ(BTA_AllocateSCN(), scn);
  }

  ASSERT_EQ(BTA_AllocateSCN(), 0);
}
