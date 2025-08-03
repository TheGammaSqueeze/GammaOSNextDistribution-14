/******************************************************************************
 *
 *  Copyright 2022 The Android Open Source Project
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
 ******************************************************************************/

#include <gtest/gtest.h>

#include "bta/hf_client/bta_hf_client_int.h"
#include "bta/include/bta_hf_client_api.h"
#include "common/message_loop_thread.h"
#include "device/include/esco_parameters.h"
#include "test/mock/mock_device_controller.h"
#include "types/raw_address.h"

namespace base {
class MessageLoop;
}  // namespace base

bluetooth::common::MessageLoopThread* get_main_thread() { return nullptr; }
void do_in_main_thread(base::Location const&, base::OnceCallback<void()>) {
  return;
}

namespace {
const RawAddress bdaddr1({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
}  // namespace

class BtaHfClientSecurityTest : public testing::Test {
 protected:
  void SetUp() override {
    // Reset the memory block, this is the state on which the allocate handle
    // would start operating
    bta_hf_client_cb_arr_init();
  }
};

// Attempt to parse a buffer which exceeds available buffer space.
// This should fail but not crash
TEST_F(BtaHfClientSecurityTest, test_parse_overflow_buffer) {
  uint16_t p_handle;
  bool status = bta_hf_client_allocate_handle(bdaddr1, &p_handle);

  tBTA_HF_CLIENT_CB* cb;

  // Allocation should succeed
  ASSERT_EQ(true, status);
  ASSERT_GT(p_handle, 0);

  cb = bta_hf_client_find_cb_by_bda(bdaddr1);

  ASSERT_TRUE(cb != NULL);

  uint16_t len = BTA_HF_CLIENT_AT_PARSER_MAX_LEN * 2 + 3;
  char buf[BTA_HF_CLIENT_AT_PARSER_MAX_LEN * 2 + 3] = {'\n'};

  bta_hf_client_at_parse(cb, (char*)(&buf[0]), len);

  ASSERT_TRUE(len);
  ASSERT_TRUE(buf != NULL);

  ASSERT_TRUE(1);
}
