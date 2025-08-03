/*
 * Copyright 2022 The Android Open Source Project
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

#include <base/functional/bind.h>
#include <base/location.h>
#include <gtest/gtest.h>

#include "bta/av/bta_av_int.h"
#include "bta/hf_client/bta_hf_client_int.h"
#include "common/init_flags.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_osi_alarm.h"
#include "test/mock/mock_stack_acl.h"

using namespace std::chrono_literals;

bool btif_av_both_enable(void) { return true; }

namespace {
const RawAddress kRawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
}  // namespace

struct alarm_t {
  alarm_t(const char* name){};
  int any_value;
};

class BtaAvTest : public testing::Test {
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    bluetooth::common::InitFlags::SetAllForTesting();
  }
  void TearDown() override {
  }
};

TEST_F(BtaAvTest, nop) {
  bool status = true;
  ASSERT_EQ(true, status);
}

TEST_F(BtaAvTest, bta_av_rc_opened) {
  tBTA_AV_CB cb = {
      .p_cback =
          [](tBTA_AV_EVT event, tBTA_AV* p_data) {
            const tBTA_AV_RC_OPEN* rc_open = &p_data->rc_open;
            ASSERT_EQ(BTA_AV_RC_OPEN_EVT, event);
            ASSERT_EQ(kRawAddress, rc_open->peer_addr);
          },
  };
  tBTA_AV_DATA data = {
      .rc_conn_chg =
          {
              .hdr = {},
              .peer_addr = kRawAddress,
              .handle = 0,
          },
  };
  bta_av_rc_opened(&cb, &data);
}
