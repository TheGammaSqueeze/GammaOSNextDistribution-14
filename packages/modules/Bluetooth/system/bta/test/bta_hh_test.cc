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

#include <gtest/gtest.h>

#include <array>

#include "bta/dm/bta_dm_int.h"
#include "bta/hh/bta_hh_int.h"
#include "bta/include/bta_hh_api.h"
#include "bta/include/bta_le_audio_api.h"
#include "osi/include/allocator.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_osi_allocator.h"

namespace {
std::array<uint8_t, 32> data32 = {
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b,
    0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,
    0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20,
};
}

class BtaHhTest : public ::testing::Test {
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    test::mock::osi_allocator::osi_malloc.body = [](size_t size) {
      return malloc(size);
    };
    test::mock::osi_allocator::osi_calloc.body = [](size_t size) {
      return calloc(1UL, size);
    };
    test::mock::osi_allocator::osi_free.body = [](void* ptr) { free(ptr); };
    test::mock::osi_allocator::osi_free_and_reset.body = [](void** ptr) {
      free(*ptr);
      *ptr = nullptr;
    };
  }

  void TearDown() override {
    bta_hh_cb.p_cback = nullptr;

    test::mock::osi_allocator::osi_malloc = {};
    test::mock::osi_allocator::osi_calloc = {};
    test::mock::osi_allocator::osi_free = {};
    test::mock::osi_allocator::osi_free_and_reset = {};
  }
};

TEST_F(BtaHhTest, simple) {}

TEST_F(BtaHhTest, bta_hh_ctrl_dat_act__BTA_HH_GET_RPT_EVT) {
  tBTA_HH_DEV_CB cb = {
      .w4_evt = BTA_HH_GET_RPT_EVT,
  };

  tBTA_HH_DATA data = {
      .hid_cback =
          {
              .hdr =
                  {
                      .event = 0,
                      .len = 0,
                      .offset = 0,
                      .layer_specific = 0,
                  },
              .link_spec.addrt.bda = RawAddress::kEmpty,
              .link_spec.addrt.type = BLE_ADDR_PUBLIC,
              .link_spec.transport = BT_TRANSPORT_AUTO,
              .data = 32,
              .p_data = static_cast<BT_HDR*>(osi_calloc(32 + sizeof(BT_HDR))),
          },
  };

  data.hid_cback.p_data->len = static_cast<uint16_t>(data32.size());
  uint8_t* p_data = (uint8_t*)(data.hid_cback.p_data + 1);
  int i = 0;
  for (const auto& byte : data32) {
    p_data[i++] = byte;
  }

  bta_hh_cb.p_cback = [](tBTA_HH_EVT event, tBTA_HH* p_data) {
    tBTA_HH_HSDATA& hs_data = p_data->hs_data;
    uint8_t* data = (uint8_t*)(hs_data.rsp_data.p_rpt_data + 1);
    ASSERT_EQ(BTA_HH_GET_RPT_EVT, event);
    int i = 0;
    for (const auto& byte : data32) {
      ASSERT_EQ(byte, data[i++]);
    }
  };

  bta_hh_ctrl_dat_act(&cb, &data);
  ASSERT_EQ(cb.w4_evt, 0);
}
