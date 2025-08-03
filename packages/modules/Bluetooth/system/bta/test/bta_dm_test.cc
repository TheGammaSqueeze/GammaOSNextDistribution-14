/*
 * Copyright 2021 The Android Open Source Project
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
#include <bluetooth/log.h>
#include <com_android_bluetooth_flags.h>
#include <flag_macros.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <string>

#include "bta/dm/bta_dm_disc.h"
#include "bta/dm/bta_dm_disc_int.h"
#include "bta/dm/bta_dm_int.h"
#include "bta/dm/bta_dm_pm.cc"
#include "bta/dm/bta_dm_sec_int.h"
#include "bta/hf_client/bta_hf_client_int.h"
#include "bta/include/bta_api.h"
#include "bta/test/bta_base_test.h"
#include "osi/include/compat.h"
#include "osi/include/osi.h"
#include "stack/include/btm_status.h"
#include "test/common/main_handler.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_osi_alarm.h"
#include "test/mock/mock_osi_allocator.h"
#include "test/mock/mock_osi_properties.h"
#include "test/mock/mock_stack_acl.h"
#include "test/mock/mock_stack_btm_interface.h"

#define TEST_BT com::android::bluetooth::flags

using namespace std::chrono_literals;
using namespace bluetooth;

namespace {
constexpr uint8_t kUnusedTimer = BTA_ID_MAX;
const RawAddress kRawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
const RawAddress kRawAddress2({0x12, 0x34, 0x56, 0x78, 0x9a, 0xbc});

constexpr char kRemoteName[] = "TheRemoteName";

bool bta_dm_search_sm_execute(const BT_HDR_RIGID* p_msg) { return true; }
void bta_dm_search_sm_disable() { bta_sys_deregister(BTA_ID_DM_SEARCH); }

const tBTA_SYS_REG bta_dm_search_reg = {bta_dm_search_sm_execute,
                                        bta_dm_search_sm_disable};

}  // namespace

namespace bluetooth {
namespace legacy {
namespace testing {

const tBTA_DM_SEARCH_CB& bta_dm_disc_search_cb();
tBTA_DM_SEARCH_CB bta_dm_disc_get_search_cb();
void bta_dm_deinit_cb();
void bta_dm_disc_search_cb(const tBTA_DM_SEARCH_CB& search_cb);
void bta_dm_init_cb();
void bta_dm_remote_name_cmpl(const tBTA_DM_MSG* p_data);
void bta_dm_sdp_result(tBTA_DM_MSG* p_data);

}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth

class BtaDmTest : public BtaBaseTest {
 protected:
  void SetUp() override {
    BtaBaseTest::SetUp();
    main_thread_start_up();
    post_on_bt_main([]() { log::info("Main thread started up"); });

    bta_sys_register(BTA_ID_DM_SEARCH, &bta_dm_search_reg);
    bluetooth::legacy::testing::bta_dm_init_cb();

    for (int i = 0; i < BTA_DM_NUM_PM_TIMER; i++) {
      for (int j = 0; j < BTA_DM_PM_MODE_TIMER_MAX; j++) {
        bta_dm_cb.pm_timer[i].srvc_id[j] = kUnusedTimer;
      }
    }
  }
  void TearDown() override {
    bta_sys_deregister(BTA_ID_DM_SEARCH);
    bluetooth::legacy::testing::bta_dm_deinit_cb();
    post_on_bt_main([]() { log::info("Main thread shutting down"); });
    main_thread_shut_down();
    BtaBaseTest::TearDown();
  }
};

class BtaDmCustomAlarmTest : public BtaDmTest {
 protected:
  void SetUp() override {
    BtaDmTest::SetUp();
    test::mock::osi_alarm::alarm_set_on_mloop.body =
        [this](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
               void* data) {
          ASSERT_TRUE(alarm != nullptr);
          this->alarm_callback = cb;
          this->alarm_data = data;
        };
  }
  void TearDown() override {
    test::mock::osi_alarm::alarm_set_on_mloop = {};
    BtaDmTest::TearDown();
  }
  alarm_callback_t alarm_callback;
  void* alarm_data{nullptr};
};

TEST_F(BtaDmTest, nop) {
  bool status = true;
  ASSERT_EQ(true, status);
}

TEST_F(BtaDmCustomAlarmTest, disable_no_acl_links) {
  bta_dm_cb.disabling = true;

  bta_dm_disable();  // Waiting for all ACL connections to drain
  ASSERT_EQ(0, get_func_call_count("btm_remove_acl"));
  ASSERT_EQ(1, get_func_call_count("alarm_set_on_mloop"));

  // Execute timer callback
  alarm_callback(this->alarm_data);
  ASSERT_EQ(1, get_func_call_count("alarm_set_on_mloop"));
  ASSERT_EQ(0, get_func_call_count("BTIF_dm_disable"));
  ASSERT_EQ(1, get_func_call_count("future_ready"));
  ASSERT_TRUE(!bta_dm_cb.disabling);
}

TEST_F(BtaDmCustomAlarmTest, disable_first_pass_with_acl_links) {
  test::mock::stack_acl::BTM_GetNumAclLinks.body = []() { return 1; };
  bta_dm_cb.disabling = true;
  // ACL link is open
  bta_dm_cb.device_list.count = 1;

  bta_dm_disable();              // Waiting for all ACL connections to drain
  ASSERT_EQ(1, get_func_call_count("alarm_set_on_mloop"));
  ASSERT_EQ(0, get_func_call_count("BTIF_dm_disable"));

  test::mock::stack_acl::BTM_GetNumAclLinks.body = []() { return 0; };
  // First disable pass
  alarm_callback(this->alarm_data);
  ASSERT_EQ(1, get_func_call_count("alarm_set_on_mloop"));
  ASSERT_EQ(1, get_func_call_count("BTIF_dm_disable"));
  ASSERT_TRUE(!bta_dm_cb.disabling);

  test::mock::stack_acl::BTM_GetNumAclLinks = {};
}

TEST_F(BtaDmCustomAlarmTest, disable_second_pass_with_acl_links) {
  test::mock::stack_acl::BTM_GetNumAclLinks.body = []() { return 1; };
  bta_dm_cb.disabling = true;
  // ACL link is open
  bta_dm_cb.device_list.count = 1;

  bta_dm_disable();  // Waiting for all ACL connections to drain
  ASSERT_EQ(1, get_func_call_count("alarm_set_on_mloop"));
  ASSERT_EQ(0, get_func_call_count("BTIF_dm_disable"));

  // First disable pass
  alarm_callback(alarm_data);
  ASSERT_EQ(2, get_func_call_count("alarm_set_on_mloop"));
  ASSERT_EQ(0, get_func_call_count("BTIF_dm_disable"));
  ASSERT_EQ(1, get_func_call_count("btm_remove_acl"));

  // Second disable pass
  alarm_callback(alarm_data);
  ASSERT_EQ(1, get_func_call_count("BTIF_dm_disable"));
  ASSERT_TRUE(!bta_dm_cb.disabling);

  test::mock::stack_acl::BTM_GetNumAclLinks = {};
}

namespace {

struct BTA_DM_ENCRYPT_CBACK_parms {
  const RawAddress bd_addr;
  tBT_TRANSPORT transport;
  tBTA_STATUS result;
};

std::queue<BTA_DM_ENCRYPT_CBACK_parms> BTA_DM_ENCRYPT_CBACK_queue;

void BTA_DM_ENCRYPT_CBACK(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                          tBTA_STATUS result) {
  BTA_DM_ENCRYPT_CBACK_queue.push({bd_addr, transport, result});
}

}  // namespace

namespace bluetooth {
namespace legacy {
namespace testing {
tBTA_DM_PEER_DEVICE* allocate_device_for(const RawAddress& bd_addr,
                                         tBT_TRANSPORT transport);

void bta_dm_remname_cback(const tBTM_REMOTE_DEV_NAME* p);

tBT_TRANSPORT bta_dm_determine_discovery_transport(
    const RawAddress& remote_bd_addr);

void btm_set_local_io_caps(uint8_t io_caps);

tBTM_STATUS bta_dm_sp_cback(tBTM_SP_EVT event, tBTM_SP_EVT_DATA* p_data);

void BTA_dm_on_hw_on();

}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth

TEST_F(BtaDmTest, bta_dm_set_encryption) {
  const tBT_TRANSPORT transport{BT_TRANSPORT_LE};
  const tBTM_BLE_SEC_ACT sec_act{BTM_BLE_SEC_NONE};

  // Callback not provided
  bta_dm_set_encryption(kRawAddress, transport, nullptr, sec_act);

  // Device connection does not exist
  bta_dm_set_encryption(kRawAddress, transport, BTA_DM_ENCRYPT_CBACK, sec_act);

  // Setup a connected device
  tBTA_DM_PEER_DEVICE* device =
      bluetooth::legacy::testing::allocate_device_for(kRawAddress, transport);
  ASSERT_TRUE(device != nullptr);
  device->conn_state = BTA_DM_CONNECTED;
  device->p_encrypt_cback = nullptr;

  // Setup a device that is busy with another encryption
  // Fake indication that the encryption is in progress with non-null callback
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_set_encryption(kRawAddress, transport, BTA_DM_ENCRYPT_CBACK, sec_act);
  ASSERT_EQ(0, get_func_call_count("BTM_SetEncryption"));
  ASSERT_EQ(1UL, BTA_DM_ENCRYPT_CBACK_queue.size());
  auto params = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_BUSY, params.result);
  device->p_encrypt_cback = nullptr;

  // Setup a device that fails encryption
  mock_btm_client_interface.security.BTM_SetEncryption =
      [](const RawAddress& bd_addr, tBT_TRANSPORT transport,
         tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
         tBTM_BLE_SEC_ACT sec_act) -> tBTM_STATUS {
    inc_func_call_count("BTM_SetEncryption");
    return BTM_MODE_UNSUPPORTED;
  };

  bta_dm_set_encryption(kRawAddress, transport, BTA_DM_ENCRYPT_CBACK, sec_act);
  ASSERT_EQ(1, get_func_call_count("BTM_SetEncryption"));
  ASSERT_EQ(0UL, BTA_DM_ENCRYPT_CBACK_queue.size());
  device->p_encrypt_cback = nullptr;

  // Setup a device that successfully starts encryption
  mock_btm_client_interface.security.BTM_SetEncryption =
      [](const RawAddress& bd_addr, tBT_TRANSPORT transport,
         tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
         tBTM_BLE_SEC_ACT sec_act) -> tBTM_STATUS {
    inc_func_call_count("BTM_SetEncryption");
    return BTM_CMD_STARTED;
  };

  bta_dm_set_encryption(kRawAddress, transport, BTA_DM_ENCRYPT_CBACK, sec_act);
  ASSERT_EQ(2, get_func_call_count("BTM_SetEncryption"));
  ASSERT_EQ(0UL, BTA_DM_ENCRYPT_CBACK_queue.size());
  ASSERT_NE(nullptr, device->p_encrypt_cback);

  BTA_DM_ENCRYPT_CBACK_queue = {};
}

void bta_dm_encrypt_cback(const RawAddress* bd_addr, tBT_TRANSPORT transport,
                          UNUSED_ATTR void* p_ref_data, tBTM_STATUS result);

TEST_F(BtaDmTest, bta_dm_encrypt_cback) {
  const tBT_TRANSPORT transport{BT_TRANSPORT_LE};

  // Setup a connected device
  tBTA_DM_PEER_DEVICE* device =
      bluetooth::legacy::testing::allocate_device_for(kRawAddress, transport);
  ASSERT_TRUE(device != nullptr);
  device->conn_state = BTA_DM_CONNECTED;

  // Encryption with no callback set
  device->p_encrypt_cback = nullptr;
  bta_dm_encrypt_cback(&kRawAddress, transport, nullptr, BTM_SUCCESS);
  ASSERT_EQ(0UL, BTA_DM_ENCRYPT_CBACK_queue.size());

  // Encryption with callback
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_encrypt_cback(&kRawAddress, transport, nullptr, BTM_SUCCESS);
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_encrypt_cback(&kRawAddress, transport, nullptr, BTM_WRONG_MODE);
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_encrypt_cback(&kRawAddress, transport, nullptr, BTM_NO_RESOURCES);
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_encrypt_cback(&kRawAddress, transport, nullptr, BTM_BUSY);
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_encrypt_cback(&kRawAddress, transport, nullptr, BTM_ILLEGAL_VALUE);

  ASSERT_EQ(5UL, BTA_DM_ENCRYPT_CBACK_queue.size());

  auto params_BTM_SUCCESS = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_SUCCESS, params_BTM_SUCCESS.result);
  auto params_BTM_WRONG_MODE = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_WRONG_MODE, params_BTM_WRONG_MODE.result);
  auto params_BTM_NO_RESOURCES = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_NO_RESOURCES, params_BTM_NO_RESOURCES.result);
  auto params_BTM_BUSY = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_BUSY, params_BTM_BUSY.result);
  auto params_BTM_ILLEGAL_VALUE = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_FAILURE, params_BTM_ILLEGAL_VALUE.result);
}

TEST_F(BtaDmTest, bta_dm_event_text) {
  std::vector<std::pair<tBTA_DM_EVT, std::string>> events = {
      std::make_pair(BTA_DM_API_SEARCH_EVT, "BTA_DM_API_SEARCH_EVT"),
      std::make_pair(BTA_DM_API_DISCOVER_EVT, "BTA_DM_API_DISCOVER_EVT"),
      std::make_pair(BTA_DM_INQUIRY_CMPL_EVT, "BTA_DM_INQUIRY_CMPL_EVT"),
      std::make_pair(BTA_DM_REMT_NAME_EVT, "BTA_DM_REMT_NAME_EVT"),
      std::make_pair(BTA_DM_SDP_RESULT_EVT, "BTA_DM_SDP_RESULT_EVT"),
      std::make_pair(BTA_DM_SEARCH_CMPL_EVT, "BTA_DM_SEARCH_CMPL_EVT"),
      std::make_pair(BTA_DM_DISCOVERY_RESULT_EVT,
                     "BTA_DM_DISCOVERY_RESULT_EVT"),
      std::make_pair(BTA_DM_DISC_CLOSE_TOUT_EVT, "BTA_DM_DISC_CLOSE_TOUT_EVT"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), bta_dm_event_text(event.first).c_str());
  }
  ASSERT_STREQ(base::StringPrintf("UNKNOWN[0x%04x]",
                                  std::numeric_limits<uint16_t>::max())
                   .c_str(),
               bta_dm_event_text(static_cast<tBTA_DM_EVT>(
                                     std::numeric_limits<uint16_t>::max()))
                   .c_str());
}

TEST_F(BtaDmTest, bta_dm_state_text) {
  std::vector<std::pair<tBTA_DM_STATE, std::string>> states = {
      std::make_pair(BTA_DM_SEARCH_IDLE, "BTA_DM_SEARCH_IDLE"),
      std::make_pair(BTA_DM_SEARCH_ACTIVE, "BTA_DM_SEARCH_ACTIVE"),
      std::make_pair(BTA_DM_SEARCH_CANCELLING, "BTA_DM_SEARCH_CANCELLING"),
      std::make_pair(BTA_DM_DISCOVER_ACTIVE, "BTA_DM_DISCOVER_ACTIVE"),
  };
  for (const auto& state : states) {
    ASSERT_STREQ(state.second.c_str(), bta_dm_state_text(state.first).c_str());
  }
  auto unknown =
      base::StringPrintf("UNKNOWN[%d]", std::numeric_limits<int>::max());
  ASSERT_STREQ(unknown.c_str(),
               bta_dm_state_text(
                   static_cast<tBTA_DM_STATE>(std::numeric_limits<int>::max()))
                   .c_str());
}

TEST_F(BtaDmTest, bta_dm_remname_cback__typical) {
  tBTA_DM_SEARCH_CB search_cb =
      bluetooth::legacy::testing::bta_dm_disc_get_search_cb();
  search_cb.peer_bdaddr = kRawAddress, search_cb.name_discover_done = false,
  bluetooth::legacy::testing::bta_dm_disc_search_cb(search_cb);

  tBTM_REMOTE_DEV_NAME name = {
      .status = BTM_SUCCESS,
      .bd_addr = kRawAddress,
      .length = static_cast<uint16_t>(strlen(kRemoteName)),
      .remote_bd_name = {},
      .hci_status = HCI_SUCCESS,
  };
  strlcpy(reinterpret_cast<char*>(&name.remote_bd_name), kRemoteName,
          strlen(kRemoteName));

  mock_btm_client_interface.security.BTM_SecDeleteRmtNameNotifyCallback =
      [](tBTM_RMT_NAME_CALLBACK*) -> bool {
    inc_func_call_count("BTM_SecDeleteRmtNameNotifyCallback");
    return true;
  };
  bluetooth::legacy::testing::bta_dm_remname_cback(&name);

  sync_main_handler();

  ASSERT_EQ(1, get_func_call_count("BTM_SecDeleteRmtNameNotifyCallback"));
  ASSERT_TRUE(
      bluetooth::legacy::testing::bta_dm_disc_search_cb().name_discover_done);
}

TEST_F(BtaDmTest, bta_dm_remname_cback__wrong_address) {
  tBTA_DM_SEARCH_CB search_cb =
      bluetooth::legacy::testing::bta_dm_disc_get_search_cb();
  search_cb.p_search_cback = nullptr;
  search_cb.peer_bdaddr = kRawAddress;
  search_cb.name_discover_done = false;
  bluetooth::legacy::testing::bta_dm_disc_search_cb(search_cb);

  tBTM_REMOTE_DEV_NAME name = {
      .status = BTM_SUCCESS,
      .bd_addr = kRawAddress2,
      .length = static_cast<uint16_t>(strlen(kRemoteName)),
      .remote_bd_name = {},
      .hci_status = HCI_SUCCESS,
  };
  strlcpy(reinterpret_cast<char*>(&name.remote_bd_name), kRemoteName,
          strlen(kRemoteName));

  mock_btm_client_interface.security.BTM_SecDeleteRmtNameNotifyCallback =
      [](tBTM_RMT_NAME_CALLBACK*) -> bool {
    inc_func_call_count("BTM_SecDeleteRmtNameNotifyCallback");
    return true;
  };
  bluetooth::legacy::testing::bta_dm_remname_cback(&name);

  sync_main_handler();

  ASSERT_EQ(0, get_func_call_count("BTM_SecDeleteRmtNameNotifyCallback"));
}

TEST_F(BtaDmTest, bta_dm_remname_cback__HCI_ERR_CONNECTION_EXISTS) {
  tBTA_DM_SEARCH_CB search_cb =
      bluetooth::legacy::testing::bta_dm_disc_get_search_cb();
  search_cb.peer_bdaddr = kRawAddress;
  search_cb.name_discover_done = false;

  tBTM_REMOTE_DEV_NAME name = {
      .status = BTM_SUCCESS,
      .bd_addr = RawAddress::kEmpty,
      .length = static_cast<uint16_t>(strlen(kRemoteName)),
      .remote_bd_name = {},
      .hci_status = HCI_ERR_CONNECTION_EXISTS,
  };
  strlcpy(reinterpret_cast<char*>(&name.remote_bd_name), kRemoteName,
          strlen(kRemoteName));

  mock_btm_client_interface.security.BTM_SecDeleteRmtNameNotifyCallback =
      [](tBTM_RMT_NAME_CALLBACK*) -> bool {
    inc_func_call_count("BTM_SecDeleteRmtNameNotifyCallback");
    return true;
  };
  bluetooth::legacy::testing::bta_dm_remname_cback(&name);

  sync_main_handler();

  ASSERT_EQ(1, get_func_call_count("BTM_SecDeleteRmtNameNotifyCallback"));
  ASSERT_TRUE(
      bluetooth::legacy::testing::bta_dm_disc_search_cb().name_discover_done);
}

TEST_F(BtaDmTest, bta_dm_determine_discovery_transport__BT_TRANSPORT_BR_EDR) {
  tBTA_DM_SEARCH_CB search_cb =
      bluetooth::legacy::testing::bta_dm_disc_get_search_cb();
  search_cb.transport = BT_TRANSPORT_BR_EDR;
  bluetooth::legacy::testing::bta_dm_disc_search_cb(search_cb);

  ASSERT_EQ(BT_TRANSPORT_BR_EDR,
            bluetooth::legacy::testing::bta_dm_determine_discovery_transport(
                kRawAddress));
}

TEST_F(BtaDmTest, bta_dm_determine_discovery_transport__BT_TRANSPORT_LE) {
  tBTA_DM_SEARCH_CB search_cb =
      bluetooth::legacy::testing::bta_dm_disc_get_search_cb();
  search_cb.transport = BT_TRANSPORT_LE;
  bluetooth::legacy::testing::bta_dm_disc_search_cb(search_cb);

  ASSERT_EQ(BT_TRANSPORT_LE,
            bluetooth::legacy::testing::bta_dm_determine_discovery_transport(
                kRawAddress));
}

TEST_F(BtaDmTest,
       bta_dm_determine_discovery_transport__BT_TRANSPORT_AUTO__BR_EDR) {
  tBTA_DM_SEARCH_CB search_cb =
      bluetooth::legacy::testing::bta_dm_disc_get_search_cb();
  search_cb.transport = BT_TRANSPORT_AUTO;
  bluetooth::legacy::testing::bta_dm_disc_search_cb(search_cb);

  mock_btm_client_interface.peer.BTM_ReadDevInfo =
      [](const RawAddress& remote_bda, tBT_DEVICE_TYPE* p_dev_type,
         tBLE_ADDR_TYPE* p_addr_type) {
        *p_dev_type = BT_DEVICE_TYPE_BREDR;
        *p_addr_type = BLE_ADDR_PUBLIC;
      };

  ASSERT_EQ(BT_TRANSPORT_BR_EDR,
            bluetooth::legacy::testing::bta_dm_determine_discovery_transport(
                kRawAddress));
}

TEST_F(BtaDmTest,
       bta_dm_determine_discovery_transport__BT_TRANSPORT_AUTO__BLE__PUBLIC) {
  tBTA_DM_SEARCH_CB search_cb =
      bluetooth::legacy::testing::bta_dm_disc_get_search_cb();
  search_cb.transport = BT_TRANSPORT_AUTO;
  bluetooth::legacy::testing::bta_dm_disc_search_cb(search_cb);

  mock_btm_client_interface.peer.BTM_ReadDevInfo =
      [](const RawAddress& remote_bda, tBT_DEVICE_TYPE* p_dev_type,
         tBLE_ADDR_TYPE* p_addr_type) {
        *p_dev_type = BT_DEVICE_TYPE_BLE;
        *p_addr_type = BLE_ADDR_PUBLIC;
      };

  ASSERT_EQ(BT_TRANSPORT_LE,
            bluetooth::legacy::testing::bta_dm_determine_discovery_transport(
                kRawAddress));
}

TEST_F(BtaDmTest,
       bta_dm_determine_discovery_transport__BT_TRANSPORT_AUTO__DUMO) {
  tBTA_DM_SEARCH_CB search_cb =
      bluetooth::legacy::testing::bta_dm_disc_get_search_cb();
  search_cb.transport = BT_TRANSPORT_AUTO;
  bluetooth::legacy::testing::bta_dm_disc_search_cb(search_cb);

  mock_btm_client_interface.peer.BTM_ReadDevInfo =
      [](const RawAddress& remote_bda, tBT_DEVICE_TYPE* p_dev_type,
         tBLE_ADDR_TYPE* p_addr_type) {
        *p_dev_type = BT_DEVICE_TYPE_DUMO;
        *p_addr_type = BLE_ADDR_PUBLIC;
      };

  ASSERT_EQ(BT_TRANSPORT_BR_EDR,
            bluetooth::legacy::testing::bta_dm_determine_discovery_transport(
                kRawAddress));
}

TEST_F(BtaDmTest, bta_dm_search_evt_text) {
  std::vector<std::pair<tBTA_DM_SEARCH_EVT, std::string>> events = {
      std::make_pair(BTA_DM_INQ_RES_EVT, "BTA_DM_INQ_RES_EVT"),
      std::make_pair(BTA_DM_INQ_CMPL_EVT, "BTA_DM_INQ_CMPL_EVT"),
      std::make_pair(BTA_DM_DISC_RES_EVT, "BTA_DM_DISC_RES_EVT"),
      std::make_pair(BTA_DM_GATT_OVER_LE_RES_EVT,
                     "BTA_DM_GATT_OVER_LE_RES_EVT"),
      std::make_pair(BTA_DM_DISC_CMPL_EVT, "BTA_DM_DISC_CMPL_EVT"),
      std::make_pair(BTA_DM_SEARCH_CANCEL_CMPL_EVT,
                     "BTA_DM_SEARCH_CANCEL_CMPL_EVT"),
      std::make_pair(BTA_DM_DID_RES_EVT, "BTA_DM_DID_RES_EVT"),
      std::make_pair(BTA_DM_GATT_OVER_SDP_RES_EVT,
                     "BTA_DM_GATT_OVER_SDP_RES_EVT"),
      std::make_pair(BTA_DM_NAME_READ_EVT, "BTA_DM_NAME_READ_EVT"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(),
                 bta_dm_search_evt_text(event.first).c_str());
  }
  ASSERT_STREQ(
      base::StringPrintf("UNKNOWN[%hhu]", std::numeric_limits<uint8_t>::max())
          .c_str(),
      bta_dm_search_evt_text(
          static_cast<tBTA_DM_SEARCH_EVT>(std::numeric_limits<uint8_t>::max()))
          .c_str());
}

TEST_F(BtaDmTest, bta_dm_remote_name_cmpl) {
  tBTA_DM_MSG msg = {
      .remote_name_msg =
          {
              // tBTA_DM_REMOTE_NAME
              .hdr = {},
              .bd_addr = kRawAddress,
              .bd_name = {0},
              .hci_status = HCI_SUCCESS,
          },
  };
  bluetooth::legacy::testing::bta_dm_remote_name_cmpl(&msg);
  ASSERT_EQ(1, get_func_call_count("BTM_InqDbRead"));
}

TEST_F(BtaDmTest, bta_dm_disc_start__true) { bta_dm_disc_start(true); }
TEST_F(BtaDmTest, bta_dm_disc_start__false) { bta_dm_disc_start(false); }

TEST_F(BtaDmTest, bta_dm_disc_stop) { bta_dm_disc_stop(); }

TEST_F(BtaDmCustomAlarmTest, bta_dm_sniff_cback) {
  // Setup a connected device
  const tBT_TRANSPORT transport{BT_TRANSPORT_BR_EDR};
  tBTA_DM_PEER_DEVICE* device =
      bluetooth::legacy::testing::allocate_device_for(kRawAddress, transport);
  ASSERT_TRUE(device != nullptr);

  // Trigger a sniff timer
  bta_dm_pm_start_timer(&bta_dm_cb.pm_timer[0],
                        bta_pm_action_to_timer_idx(BTA_DM_PM_SNIFF), 10, 1,
                        BTA_DM_PM_SNIFF);
  bta_dm_cb.pm_timer[0].peer_bdaddr = kRawAddress;
  ASSERT_EQ(1, get_func_call_count("alarm_set_on_mloop"));

  // Trigger reset sniff
  bta_dm_sniff_cback(BTA_ID_JV, 1, kRawAddress);
  ASSERT_EQ(1, get_func_call_count("alarm_cancel"));
  ASSERT_EQ(2, get_func_call_count("alarm_set_on_mloop"));
}

TEST_F_WITH_FLAGS(BtaDmCustomAlarmTest, sniff_offload_feature__enable_flag,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      enable_sniff_offload))) {
  bool is_property_enabled = true;
  test::mock::osi_properties::osi_property_get_bool.body =
      [&](const char* key, bool default_value) -> int {
    return is_property_enabled;
  };

  // Expect not to trigger bta_dm_init_pm due to both flag and prop are enabled
  // and reset the value of .srvc_id.
  is_property_enabled = true;
  bluetooth::legacy::testing::BTA_dm_on_hw_on();
  ASSERT_EQ(0, bta_dm_cb.pm_timer[0].srvc_id[0]);

  // Expect to trigger bta_dm_init_pm and init the value of .srvc_id to
  // BTA_ID_MAX.
  is_property_enabled = false;
  bluetooth::legacy::testing::BTA_dm_on_hw_on();
  ASSERT_EQ((uint8_t)BTA_ID_MAX, bta_dm_cb.pm_timer[0].srvc_id[0]);
}

TEST_F_WITH_FLAGS(BtaDmCustomAlarmTest, sniff_offload_feature__disable_flag,
                  REQUIRES_FLAGS_DISABLED(ACONFIG_FLAG(TEST_BT,
                                                       enable_sniff_offload))) {
  bool is_property_enabled = true;
  test::mock::osi_properties::osi_property_get_bool.body =
      [&](const char* key, bool default_value) -> int {
    return is_property_enabled;
  };

  // Expect to trigger bta_dm_init_pm and init the value of .srvc_id to
  // BTA_ID_MAX.
  is_property_enabled = true;
  bluetooth::legacy::testing::BTA_dm_on_hw_on();
  ASSERT_EQ((uint8_t)BTA_ID_MAX, bta_dm_cb.pm_timer[0].srvc_id[0]);

  // Expect to trigger bta_dm_init_pm and init the value of .srvc_id to
  // BTA_ID_MAX.
  is_property_enabled = false;
  bluetooth::legacy::testing::BTA_dm_on_hw_on();
  ASSERT_EQ((uint8_t)BTA_ID_MAX, bta_dm_cb.pm_timer[0].srvc_id[0]);
}
