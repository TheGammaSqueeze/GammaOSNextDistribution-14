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

#include <base/strings/stringprintf.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <sys/socket.h>

#include "bta/dm/bta_dm_sec_int.h"
#include "bta/test/bta_base_test.h"
#include "test/mock/mock_stack_btm_inq.h"
#include "test/mock/mock_stack_btm_interface.h"
#include "types/raw_address.h"

using ::testing::ElementsAre;

namespace {
const RawAddress kRawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
const RawAddress kRawAddress2({0x12, 0x34, 0x56, 0x78, 0x9a, 0xbc});
const DEV_CLASS kDeviceClass = {0x11, 0x22, 0x33};

constexpr char kRemoteName[] = "TheRemoteName";

}  // namespace

// Test hooks
namespace bluetooth {
namespace legacy {
namespace testing {

tBTM_STATUS bta_dm_sp_cback(tBTM_SP_EVT event, tBTM_SP_EVT_DATA* p_data);
void btm_set_local_io_caps(uint8_t io_caps);

}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth

class BtaSecTest : public BtaBaseTest {
 protected:
  void SetUp() override { BtaBaseTest::SetUp(); }

  void TearDown() override { BtaBaseTest::TearDown(); }
};

TEST_F(BtaSecTest, bta_dm_sp_cback__BTM_SP_CFM_REQ_EVT_WithName) {
  constexpr uint32_t kNumVal = 1234;
  static bool callback_sent = false;
  mock_btm_client_interface.peer.BTM_ReadRemoteDeviceName =
      [](const RawAddress& remote_bda, tBTM_NAME_CMPL_CB* p_cb,
         tBT_TRANSPORT transport) -> tBTM_STATUS { return BTM_CMD_STARTED; };

  static tBTA_DM_SP_CFM_REQ cfm_req{};
  bta_dm_sec_enable([](tBTA_DM_SEC_EVT event, tBTA_DM_SEC* p_data) {
    callback_sent = true;
    cfm_req = p_data->cfm_req;
  });

  bluetooth::legacy::testing::btm_set_local_io_caps(0xff);

  tBTM_SP_EVT_DATA data = {
      .cfm_req =
          {
              // tBTM_SP_CFM_REQ
              .bd_addr = kRawAddress,
              .dev_class = {},
              .bd_name = {},
              .num_val = kNumVal,
              .just_works = false,
              .loc_auth_req = BTM_AUTH_SP_YES,
              .rmt_auth_req = BTM_AUTH_SP_YES,
              .loc_io_caps = BTM_IO_CAP_NONE,
              .rmt_io_caps = BTM_IO_CAP_NONE,
          },
  };
  data.cfm_req.dev_class = kDeviceClass;
  bd_name_copy(data.cfm_req.bd_name, kRemoteName);

  ASSERT_EQ(btm_status_text(BTM_CMD_STARTED),
            btm_status_text(bluetooth::legacy::testing::bta_dm_sp_cback(
                BTM_SP_CFM_REQ_EVT, &data)));
  ASSERT_EQ(kNumVal, bta_dm_sec_cb.num_val);
  ASSERT_TRUE(callback_sent);

  ASSERT_EQ(kRawAddress, cfm_req.bd_addr);
  ASSERT_THAT(cfm_req.dev_class,
              ElementsAre(kDeviceClass[0], kDeviceClass[1], kDeviceClass[2]));
  ASSERT_STREQ(kRemoteName, reinterpret_cast<const char*>(cfm_req.bd_name));
  ASSERT_EQ(kNumVal, cfm_req.num_val);
  ASSERT_EQ(false, cfm_req.just_works);
  ASSERT_EQ(BTM_AUTH_SP_YES, cfm_req.loc_auth_req);
  ASSERT_EQ(BTM_AUTH_SP_YES, cfm_req.rmt_auth_req);
  ASSERT_EQ(BTM_IO_CAP_NONE, cfm_req.loc_io_caps);
  ASSERT_EQ(BTM_IO_CAP_NONE, cfm_req.rmt_io_caps);
}

TEST_F(BtaSecTest, bta_dm_sp_cback__BTM_SP_CFM_REQ_EVT_WithoutName_RNRSuccess) {
  constexpr uint32_t kNumVal = 1234;
  static bool callback_sent = false;
  test::mock::stack_btm_inq::BTM_ReadRemoteDeviceName.body =
      [](const RawAddress& remote_bda, tBTM_NAME_CMPL_CB* p_cb,
         tBT_TRANSPORT transport) -> tBTM_STATUS { return BTM_CMD_STARTED; };

  static tBTA_DM_SP_CFM_REQ cfm_req{};
  bta_dm_sec_enable([](tBTA_DM_SEC_EVT event, tBTA_DM_SEC* p_data) {
    callback_sent = true;
    cfm_req = p_data->cfm_req;
  });

  bluetooth::legacy::testing::btm_set_local_io_caps(0xff);

  tBTM_SP_EVT_DATA data = {
      .cfm_req =
          {
              // tBTM_SP_CFM_REQ
              .bd_addr = kRawAddress,
              .dev_class = {},
              .bd_name = {0},  // No name available
              .num_val = kNumVal,
              .just_works = false,
              .loc_auth_req = BTM_AUTH_SP_YES,
              .rmt_auth_req = BTM_AUTH_SP_YES,
              .loc_io_caps = BTM_IO_CAP_NONE,
              .rmt_io_caps = BTM_IO_CAP_NONE,
          },
  };
  data.cfm_req.dev_class = kDeviceClass;

  ASSERT_EQ(btm_status_text(BTM_CMD_STARTED),
            btm_status_text(bluetooth::legacy::testing::bta_dm_sp_cback(
                BTM_SP_CFM_REQ_EVT, &data)));
  ASSERT_EQ(kNumVal, bta_dm_sec_cb.num_val);
  ASSERT_FALSE(callback_sent);

  test::mock::stack_btm_inq::BTM_ReadRemoteDeviceName = {};
}

TEST_F(BtaSecTest, bta_dm_sp_cback__BTM_SP_CFM_REQ_EVT_WithoutName_RNRFail) {
  constexpr uint32_t kNumVal = 1234;
  static bool callback_sent = false;
  mock_btm_client_interface.peer.BTM_ReadRemoteDeviceName =
      [](const RawAddress& remote_bda, tBTM_NAME_CMPL_CB* p_cb,
         tBT_TRANSPORT transport) -> tBTM_STATUS { return BTM_SUCCESS; };

  static tBTA_DM_SP_CFM_REQ cfm_req{};
  bta_dm_sec_enable([](tBTA_DM_SEC_EVT event, tBTA_DM_SEC* p_data) {
    callback_sent = true;
    cfm_req = p_data->cfm_req;
  });

  bluetooth::legacy::testing::btm_set_local_io_caps(0xff);

  tBTM_SP_EVT_DATA data = {
      .cfm_req =
          {
              // tBTM_SP_CFM_REQ
              .bd_addr = kRawAddress,
              .dev_class = {},
              .bd_name = {0},
              .num_val = kNumVal,
              .just_works = false,
              .loc_auth_req = BTM_AUTH_SP_YES,
              .rmt_auth_req = BTM_AUTH_SP_YES,
              .loc_io_caps = BTM_IO_CAP_NONE,
              .rmt_io_caps = BTM_IO_CAP_NONE,
          },
  };
  data.cfm_req.dev_class = kDeviceClass;

  ASSERT_EQ(btm_status_text(BTM_CMD_STARTED),
            btm_status_text(bluetooth::legacy::testing::bta_dm_sp_cback(
                BTM_SP_CFM_REQ_EVT, &data)));
  ASSERT_EQ(kNumVal, bta_dm_sec_cb.num_val);
  ASSERT_TRUE(callback_sent);

  ASSERT_EQ(kRawAddress, cfm_req.bd_addr);
  ASSERT_THAT(cfm_req.dev_class,
              ElementsAre(kDeviceClass[0], kDeviceClass[1], kDeviceClass[2]));
  ASSERT_EQ(kNumVal, cfm_req.num_val);
  ASSERT_EQ(false, cfm_req.just_works);
  ASSERT_EQ(BTM_AUTH_SP_YES, cfm_req.loc_auth_req);
  ASSERT_EQ(BTM_AUTH_SP_YES, cfm_req.rmt_auth_req);
  ASSERT_EQ(BTM_IO_CAP_NONE, cfm_req.loc_io_caps);
  ASSERT_EQ(BTM_IO_CAP_NONE, cfm_req.rmt_io_caps);
}

TEST_F(BtaSecTest, bta_dm_sp_cback__BTM_SP_KEY_NOTIF_EVT) {
  constexpr uint32_t kPassKey = 1234;
  static bool callback_sent = false;
  mock_btm_client_interface.peer.BTM_ReadRemoteDeviceName =
      [](const RawAddress& remote_bda, tBTM_NAME_CMPL_CB* p_cb,
         tBT_TRANSPORT transport) -> tBTM_STATUS { return BTM_CMD_STARTED; };

  static tBTA_DM_SP_KEY_NOTIF key_notif{};
  bta_dm_sec_enable([](tBTA_DM_SEC_EVT event, tBTA_DM_SEC* p_data) {
    callback_sent = true;
    key_notif = p_data->key_notif;
  });
  bluetooth::legacy::testing::btm_set_local_io_caps(0xff);

  tBTM_SP_EVT_DATA data = {
      .key_notif =
          {
              // tBTM_SP_KEY_NOTIF
              .bd_addr = kRawAddress,
              .dev_class = {},
              .bd_name = {},
              .passkey = kPassKey,
          },
  };
  data.key_notif.dev_class = kDeviceClass;
  bd_name_copy(data.key_notif.bd_name, kRemoteName);

  ASSERT_EQ(btm_status_text(BTM_CMD_STARTED),
            btm_status_text(bluetooth::legacy::testing::bta_dm_sp_cback(
                BTM_SP_KEY_NOTIF_EVT, &data)));
  ASSERT_EQ(kPassKey, bta_dm_sec_cb.num_val);
  ASSERT_TRUE(callback_sent);

  ASSERT_EQ(kRawAddress, key_notif.bd_addr);
  ASSERT_THAT(key_notif.dev_class,
              ElementsAre(kDeviceClass[0], kDeviceClass[1], kDeviceClass[2]));
  ASSERT_STREQ(kRemoteName, reinterpret_cast<const char*>(key_notif.bd_name));
  ASSERT_EQ(kPassKey, key_notif.passkey);
}
