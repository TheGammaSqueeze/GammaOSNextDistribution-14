/*
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
 */

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "common/message_loop_thread.h"
#include "stack/hid/hidh_int.h"
#include "stack/include/hci_error_code.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_stack_l2cap_api.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

bluetooth::common::MessageLoopThread* get_main_thread() { return nullptr; }
tHCI_REASON btm_get_acl_disc_reason_code(void) { return HCI_SUCCESS; }

bool BTM_IsAclConnectionUp(const RawAddress& remote_bda,
                           tBT_TRANSPORT transport) {
  return true;
}
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

class StackHidTest : public Test {
 public:
 protected:
  void SetUp() override { reset_mock_function_count_map(); }
  void TearDown() override {}
};

TEST_F(StackHidTest, disconnect_bad_cid) {
  tL2CAP_APPL_INFO l2cap_callbacks;

  test::mock::stack_l2cap_api::L2CA_Register2.body =
      [&l2cap_callbacks](uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                         bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                         uint16_t my_mtu, uint16_t required_remote_mtu,
                         uint16_t sec_level) {
        l2cap_callbacks = p_cb_info;
        return psm;
      };

  tHID_STATUS status = hidh_conn_reg();
  ASSERT_EQ(HID_SUCCESS, status);

  l2cap_callbacks.pL2CA_Error_Cb(123, 456);
}

}  // namespace
