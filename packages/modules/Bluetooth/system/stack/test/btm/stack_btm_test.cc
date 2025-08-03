/*
 *  Copyright 2020 The Android Open Source Project
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstddef>
#include <iostream>
#include <sstream>

#include "common/init_flags.h"
#include "hci/controller_interface_mock.h"
#include "hci/hci_layer_mock.h"
#include "internal_include/bt_target.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_int_types.h"
#include "stack/btm/btm_sco.h"
#include "stack/btm/btm_sec.h"
#include "stack/btm/btm_sec_cb.h"
#include "stack/include/acl_api.h"
#include "stack/include/acl_hci_link_interface.h"
#include "stack/include/btm_client_interface.h"
#include "stack/l2cap/l2c_int.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_legacy_hci_interface.h"
#include "test/mock/mock_main_shim_entry.h"
#include "types/raw_address.h"

using testing::Each;
using testing::Eq;

extern tBTM_CB btm_cb;

tL2C_CB l2cb;

const std::string kSmpOptions("mock smp options");
const std::string kBroadcastAudioConfigOptions(
    "mock broadcast audio config options");

namespace {

using testing::Return;
using testing::Test;

class StackBtmTest : public Test {
 public:
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    bluetooth::hci::testing::mock_controller_ = &controller_;
  }
  void TearDown() override {
    bluetooth::hci::testing::mock_controller_ = nullptr;
  }
  bluetooth::hci::testing::MockControllerInterface controller_;
};

class StackBtmWithQueuesTest : public StackBtmTest {
 public:
 protected:
  void SetUp() override {
    StackBtmTest::SetUp();
    up_thread_ = new bluetooth::os::Thread(
        "up_thread", bluetooth::os::Thread::Priority::NORMAL);
    up_handler_ = new bluetooth::os::Handler(up_thread_);
    down_thread_ = new bluetooth::os::Thread(
        "down_thread", bluetooth::os::Thread::Priority::NORMAL);
    down_handler_ = new bluetooth::os::Handler(down_thread_);
    bluetooth::hci::testing::mock_hci_layer_ = &mock_hci_;
    bluetooth::hci::testing::mock_gd_shim_handler_ = up_handler_;
    bluetooth::legacy::hci::testing::SetMock(legacy_hci_mock_);
  }
  void TearDown() override {
    up_handler_->Clear();
    delete up_handler_;
    delete up_thread_;
    down_handler_->Clear();
    delete down_handler_;
    delete down_thread_;
    StackBtmTest::TearDown();
  }
  bluetooth::common::BidiQueue<bluetooth::hci::ScoView,
                               bluetooth::hci::ScoBuilder>
      sco_queue_{10};
  bluetooth::hci::testing::MockHciLayer mock_hci_;
  bluetooth::legacy::hci::testing::MockInterface legacy_hci_mock_;
  bluetooth::os::Thread* up_thread_;
  bluetooth::os::Handler* up_handler_;
  bluetooth::os::Thread* down_thread_;
  bluetooth::os::Handler* down_handler_;
};

class StackBtmWithInitFreeTest : public StackBtmWithQueuesTest {
 public:
 protected:
  void SetUp() override {
    StackBtmWithQueuesTest::SetUp();
    EXPECT_CALL(mock_hci_, GetScoQueueEnd())
        .WillOnce(Return(sco_queue_.GetUpEnd()));

    btm_cb.Init();
    btm_sec_cb.Init(BTM_SEC_MODE_SC);
  }
  void TearDown() override {
    btm_sec_cb.Free();
    btm_cb.Free();
    StackBtmWithQueuesTest::TearDown();
  }
};

TEST_F(StackBtmWithQueuesTest, GlobalLifecycle) {
  EXPECT_CALL(mock_hci_, GetScoQueueEnd())
      .WillOnce(Return(sco_queue_.GetUpEnd()));
  get_btm_client_interface().lifecycle.btm_init();
  get_btm_client_interface().lifecycle.btm_free();
}

TEST_F(StackBtmTest, DynamicLifecycle) {
  auto* btm = new tBTM_CB();
  delete btm;
}

TEST_F(StackBtmWithQueuesTest, InitFree) {
  EXPECT_CALL(mock_hci_, GetScoQueueEnd())
      .WillOnce(Return(sco_queue_.GetUpEnd()));
  btm_cb.Init();
  btm_cb.Free();
}

TEST_F(StackBtmWithQueuesTest, tSCO_CB) {
  EXPECT_CALL(mock_hci_, GetScoQueueEnd())
      .WillOnce(Return(sco_queue_.GetUpEnd()));
  bluetooth::common::InitFlags::SetAllForTesting();
  tSCO_CB* p_sco = &btm_cb.sco_cb;
  p_sco->Init();
  p_sco->Free();
}

TEST_F(StackBtmWithQueuesTest, InformClientOnConnectionSuccess) {
  EXPECT_CALL(mock_hci_, GetScoQueueEnd())
      .WillOnce(Return(sco_queue_.GetUpEnd()));
  get_btm_client_interface().lifecycle.btm_init();

  RawAddress bda({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});

  btm_acl_connected(bda, 2, HCI_SUCCESS, false);
  ASSERT_EQ(1, get_func_call_count("BTA_dm_acl_up"));

  get_btm_client_interface().lifecycle.btm_free();
}

TEST_F(StackBtmWithQueuesTest, NoInformClientOnConnectionFail) {
  EXPECT_CALL(mock_hci_, GetScoQueueEnd())
      .WillOnce(Return(sco_queue_.GetUpEnd()));
  get_btm_client_interface().lifecycle.btm_init();

  RawAddress bda({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});

  btm_acl_connected(bda, 2, HCI_ERR_NO_CONNECTION, false);
  ASSERT_EQ(0, get_func_call_count("BTA_dm_acl_up"));

  get_btm_client_interface().lifecycle.btm_free();
}

TEST_F(StackBtmWithQueuesTest, default_packet_type) {
  EXPECT_CALL(mock_hci_, GetScoQueueEnd())
      .WillOnce(Return(sco_queue_.GetUpEnd()));
  get_btm_client_interface().lifecycle.btm_init();

  btm_cb.acl_cb_.SetDefaultPacketTypeMask(0x4321);
  ASSERT_EQ(0x4321, btm_cb.acl_cb_.DefaultPacketTypes());

  get_btm_client_interface().lifecycle.btm_free();
}

TEST_F(StackBtmWithQueuesTest, change_packet_type) {
  EXPECT_CALL(mock_hci_, GetScoQueueEnd())
      .WillOnce(Return(sco_queue_.GetUpEnd()));
  get_btm_client_interface().lifecycle.btm_init();

  uint16_t handle = 0x123;

  btm_cb.acl_cb_.SetDefaultPacketTypeMask(0xffff);
  ASSERT_EQ(0xffff, btm_cb.acl_cb_.DefaultPacketTypes());

  // Create connection
  RawAddress bda({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
  btm_acl_created(bda, handle, HCI_ROLE_CENTRAL, BT_TRANSPORT_BR_EDR);

  uint64_t features = 0xffffffffffffffff;
  acl_process_supported_features(0x123, features);

  EXPECT_CALL(legacy_hci_mock_, ChangeConnectionPacketType(
                                    handle, 0x4400 | HCI_PKT_TYPES_MASK_DM1));
  EXPECT_CALL(legacy_hci_mock_, ChangeConnectionPacketType(
                                    handle, (0xcc00 | HCI_PKT_TYPES_MASK_DM1 |
                                             HCI_PKT_TYPES_MASK_DH1)));

  btm_set_packet_types_from_address(bda, 0x55aa);
  btm_set_packet_types_from_address(bda, 0xffff);
  // Illegal mask, won't be sent.
  btm_set_packet_types_from_address(bda, 0x0);

  get_btm_client_interface().lifecycle.btm_free();
}

TEST(BtmTest, BTM_EIR_MAX_SERVICES) { ASSERT_EQ(46, BTM_EIR_MAX_SERVICES); }

}  // namespace

void btm_sec_rmt_name_request_complete(const RawAddress* p_bd_addr,
                                       const uint8_t* p_bd_name,
                                       tHCI_STATUS status);

struct {
  RawAddress bd_addr;
  DEV_CLASS dc;
  tBTM_BD_NAME bd_name;
} btm_test;

TEST_F(StackBtmWithInitFreeTest, btm_sec_rmt_name_request_complete) {
  bluetooth::common::InitFlags::SetAllForTesting();

  ASSERT_TRUE(BTM_SecAddRmtNameNotifyCallback(
      [](const RawAddress& bd_addr, DEV_CLASS dc, tBTM_BD_NAME bd_name) {
        btm_test.bd_addr = bd_addr;
        btm_test.dc = dc;
        memcpy(btm_test.bd_name, bd_name, BTM_MAX_REM_BD_NAME_LEN);
      }));

  RawAddress bd_addr = RawAddress({0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6});
  const uint8_t* p_bd_name = (const uint8_t*)"MyTestName";

  btm_test = {};
  btm_sec_rmt_name_request_complete(&bd_addr, p_bd_name, HCI_SUCCESS);

  ASSERT_THAT(btm_test.bd_name, Each(Eq(0)));
  ASSERT_THAT(btm_test.dc, Each(Eq(0)));
  ASSERT_EQ(bd_addr, btm_test.bd_addr);

  btm_test = {};
  ASSERT_TRUE(btm_find_or_alloc_dev(bd_addr) != nullptr);
  btm_sec_rmt_name_request_complete(&bd_addr, p_bd_name, HCI_SUCCESS);

  ASSERT_STREQ((const char*)p_bd_name, (const char*)btm_test.bd_name);
  ASSERT_THAT(btm_test.dc, Each(Eq(0)));
  ASSERT_EQ(bd_addr, btm_test.bd_addr);
}

TEST_F(StackBtmTest, sco_state_text) {
  std::vector<std::pair<tSCO_STATE, std::string>> states = {
      std::make_pair(SCO_ST_UNUSED, "SCO_ST_UNUSED"),
      std::make_pair(SCO_ST_LISTENING, "SCO_ST_LISTENING"),
      std::make_pair(SCO_ST_W4_CONN_RSP, "SCO_ST_W4_CONN_RSP"),
      std::make_pair(SCO_ST_CONNECTING, "SCO_ST_CONNECTING"),
      std::make_pair(SCO_ST_CONNECTED, "SCO_ST_CONNECTED"),
      std::make_pair(SCO_ST_DISCONNECTING, "SCO_ST_DISCONNECTING"),
      std::make_pair(SCO_ST_PEND_UNPARK, "SCO_ST_PEND_UNPARK"),
      std::make_pair(SCO_ST_PEND_ROLECHANGE, "SCO_ST_PEND_ROLECHANGE"),
      std::make_pair(SCO_ST_PEND_MODECHANGE, "SCO_ST_PEND_MODECHANGE"),
  };
  for (const auto& state : states) {
    ASSERT_STREQ(state.second.c_str(), sco_state_text(state.first).c_str());
  }
  std::ostringstream oss;
  oss << "unknown_sco_state: " << std::numeric_limits<std::uint16_t>::max();
  ASSERT_STREQ(oss.str().c_str(),
               sco_state_text(static_cast<tSCO_STATE>(
                                  std::numeric_limits<std::uint16_t>::max()))
                   .c_str());
}

bool is_disconnect_reason_valid(const tHCI_REASON& reason);
TEST_F(StackBtmWithInitFreeTest, is_disconnect_reason_valid) {
  std::set<tHCI_REASON> valid_reason_set{
      HCI_ERR_AUTH_FAILURE,
      HCI_ERR_PEER_USER,
      HCI_ERR_REMOTE_LOW_RESOURCE,
      HCI_ERR_REMOTE_POWER_OFF,
      HCI_ERR_UNSUPPORTED_REM_FEATURE,
      HCI_ERR_PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED,
      HCI_ERR_UNACCEPT_CONN_INTERVAL,
  };
  for (unsigned u = 0; u < 256; u++) {
    const tHCI_REASON reason = static_cast<tHCI_REASON>(u);
    if (valid_reason_set.count(reason))
      ASSERT_TRUE(is_disconnect_reason_valid(reason));
    else
      ASSERT_FALSE(is_disconnect_reason_valid(reason));
  }
}

TEST_F(StackBtmWithInitFreeTest, Init) {
  ASSERT_FALSE(btm_cb.btm_inq_vars.remname_active);
}
