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

#include <vector>

#include "common/init_flags.h"
#include "hci/hci_layer_mock.h"
#include "internal_include/bt_target.h"
#include "stack/btm/btm_ble_sec.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_sec.h"
#include "stack/btm/btm_sec_cb.h"
#include "stack/btm/security_device_record.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_main_shim_entry.h"
#include "types/raw_address.h"

using testing::Return;
using testing::Test;

namespace bluetooth {
namespace testing {
namespace legacy {

void wipe_secrets_and_remove(tBTM_SEC_DEV_REC* p_dev_rec);

}  // namespace legacy
}  // namespace testing
}  // namespace bluetooth

using bluetooth::testing::legacy::wipe_secrets_and_remove;

constexpr size_t kBtmSecMaxDeviceRecords =
    static_cast<size_t>(BTM_SEC_MAX_DEVICE_RECORDS + 1);

class StackBtmSecTest : public Test {
 public:
 protected:
  void SetUp() override { reset_mock_function_count_map(); }
  void TearDown() override {}
};

class StackBtmSecWithQueuesTest : public StackBtmSecTest {
 public:
 protected:
  void SetUp() override {
    StackBtmSecTest::SetUp();
    up_thread_ = new bluetooth::os::Thread(
        "up_thread", bluetooth::os::Thread::Priority::NORMAL);
    up_handler_ = new bluetooth::os::Handler(up_thread_);
    down_thread_ = new bluetooth::os::Thread(
        "down_thread", bluetooth::os::Thread::Priority::NORMAL);
    down_handler_ = new bluetooth::os::Handler(down_thread_);
    bluetooth::hci::testing::mock_hci_layer_ = &mock_hci_;
    bluetooth::hci::testing::mock_gd_shim_handler_ = up_handler_;
  }
  void TearDown() override {
    up_handler_->Clear();
    delete up_handler_;
    delete up_thread_;
    down_handler_->Clear();
    delete down_handler_;
    delete down_thread_;
    StackBtmSecTest::TearDown();
  }
  bluetooth::common::BidiQueue<bluetooth::hci::ScoView,
                               bluetooth::hci::ScoBuilder>
      sco_queue_{10};
  bluetooth::hci::testing::MockHciLayer mock_hci_;
  bluetooth::os::Thread* up_thread_;
  bluetooth::os::Handler* up_handler_;
  bluetooth::os::Thread* down_thread_;
  bluetooth::os::Handler* down_handler_;
};

class StackBtmSecWithInitFreeTest : public StackBtmSecWithQueuesTest {
 public:
 protected:
  void SetUp() override {
    StackBtmSecWithQueuesTest::SetUp();
    ::btm_sec_cb.Init(BTM_SEC_MODE_SC);
  }
  void TearDown() override {
    ::btm_sec_cb.Free();
    StackBtmSecWithQueuesTest::TearDown();
  }
};

TEST_F(StackBtmSecWithInitFreeTest, btm_sec_encrypt_change) {
  bluetooth::common::InitFlags::SetAllForTesting();

  RawAddress bd_addr = RawAddress({0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6});
  const uint16_t classic_handle = 0x1234;
  const uint16_t ble_handle = 0x9876;

  // Check the collision conditionals
  ::btm_sec_cb.collision_start_time = 0UL;
  btm_sec_encrypt_change(classic_handle, HCI_ERR_LMP_ERR_TRANS_COLLISION, 0x01);
  uint64_t collision_start_time = ::btm_sec_cb.collision_start_time;
  ASSERT_NE(0UL, collision_start_time);

  ::btm_sec_cb.collision_start_time = 0UL;
  btm_sec_encrypt_change(classic_handle, HCI_ERR_DIFF_TRANSACTION_COLLISION,
                         0x01);
  collision_start_time = ::btm_sec_cb.collision_start_time;
  ASSERT_NE(0UL, collision_start_time);

  // No device
  ::btm_sec_cb.collision_start_time = 0;
  btm_sec_encrypt_change(classic_handle, HCI_SUCCESS, 0x01);
  ASSERT_EQ(0UL, ::btm_sec_cb.collision_start_time);

  // Setup device
  tBTM_SEC_DEV_REC* device_record = btm_sec_allocate_dev_rec();
  ASSERT_NE(nullptr, device_record);
  ASSERT_EQ(BTM_SEC_IN_USE, device_record->sec_rec.sec_flags);
  device_record->bd_addr = bd_addr;
  device_record->hci_handle = classic_handle;
  device_record->ble_hci_handle = ble_handle;

  // With classic device encryption enable
  btm_sec_encrypt_change(classic_handle, HCI_SUCCESS, 0x01);
  ASSERT_EQ(BTM_SEC_IN_USE | BTM_SEC_AUTHENTICATED | BTM_SEC_ENCRYPTED,
            device_record->sec_rec.sec_flags);

  // With classic device encryption disable
  btm_sec_encrypt_change(classic_handle, HCI_SUCCESS, 0x00);
  ASSERT_EQ(BTM_SEC_IN_USE | BTM_SEC_AUTHENTICATED,
            device_record->sec_rec.sec_flags);
  device_record->sec_rec.sec_flags = BTM_SEC_IN_USE;

  // With le device encryption enable
  btm_sec_encrypt_change(ble_handle, HCI_SUCCESS, 0x01);
  ASSERT_EQ(BTM_SEC_IN_USE | BTM_SEC_LE_ENCRYPTED,
            device_record->sec_rec.sec_flags);

  // With le device encryption disable
  btm_sec_encrypt_change(ble_handle, HCI_SUCCESS, 0x00);
  ASSERT_EQ(BTM_SEC_IN_USE, device_record->sec_rec.sec_flags);
  device_record->sec_rec.sec_flags = BTM_SEC_IN_USE;

  wipe_secrets_and_remove(device_record);
}

TEST_F(StackBtmSecWithInitFreeTest, BTM_SetEncryption) {
  const RawAddress bd_addr = RawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
  const tBT_TRANSPORT transport{BT_TRANSPORT_LE};
  tBTM_SEC_CALLBACK* p_callback{nullptr};
  tBTM_BLE_SEC_ACT sec_act{BTM_BLE_SEC_ENCRYPT};

  // No device
  ASSERT_EQ(BTM_WRONG_MODE, BTM_SetEncryption(bd_addr, transport, p_callback,
                                              nullptr, sec_act));

  // With device
  tBTM_SEC_DEV_REC* device_record = btm_sec_allocate_dev_rec();
  ASSERT_NE(nullptr, device_record);
  device_record->bd_addr = bd_addr;
  device_record->hci_handle = 0x1234;

  ASSERT_EQ(BTM_WRONG_MODE, BTM_SetEncryption(bd_addr, transport, p_callback,
                                              nullptr, sec_act));

  wipe_secrets_and_remove(device_record);
}

TEST_F(StackBtmSecTest, btm_ble_sec_req_act_text) {
  ASSERT_EQ("BTM_BLE_SEC_REQ_ACT_NONE",
            btm_ble_sec_req_act_text(BTM_BLE_SEC_REQ_ACT_NONE));
  ASSERT_EQ("BTM_BLE_SEC_REQ_ACT_ENCRYPT",
            btm_ble_sec_req_act_text(BTM_BLE_SEC_REQ_ACT_ENCRYPT));
  ASSERT_EQ("BTM_BLE_SEC_REQ_ACT_PAIR",
            btm_ble_sec_req_act_text(BTM_BLE_SEC_REQ_ACT_PAIR));
  ASSERT_EQ("BTM_BLE_SEC_REQ_ACT_DISCARD",
            btm_ble_sec_req_act_text(BTM_BLE_SEC_REQ_ACT_DISCARD));
}

TEST_F(StackBtmSecWithInitFreeTest, btm_sec_allocate_dev_rec__all) {
  tBTM_SEC_DEV_REC* records[kBtmSecMaxDeviceRecords];

  // Fill up the records
  for (size_t i = 0; i < kBtmSecMaxDeviceRecords; i++) {
    ASSERT_EQ(i, list_length(::btm_sec_cb.sec_dev_rec));
    records[i] = btm_sec_allocate_dev_rec();
    ASSERT_NE(nullptr, records[i]);
  }

  // Second pass up the records
  for (size_t i = 0; i < kBtmSecMaxDeviceRecords; i++) {
    ASSERT_EQ(kBtmSecMaxDeviceRecords, list_length(::btm_sec_cb.sec_dev_rec));
    records[i] = btm_sec_allocate_dev_rec();
    ASSERT_NE(nullptr, records[i]);
  }

  // NOTE: The memory allocated for each record is automatically
  // allocated by the btm module and freed when the device record
  // list is freed.
  // Further, the memory for each record is reused when necessary.
}

TEST_F(StackBtmSecTest, btm_oob_data_text) {
  std::vector<std::pair<tBTM_OOB_DATA, std::string>> datas = {
      std::make_pair(BTM_OOB_NONE, "BTM_OOB_NONE"),
      std::make_pair(BTM_OOB_PRESENT_192, "BTM_OOB_PRESENT_192"),
      std::make_pair(BTM_OOB_PRESENT_256, "BTM_OOB_PRESENT_256"),
      std::make_pair(BTM_OOB_PRESENT_192_AND_256,
                     "BTM_OOB_PRESENT_192_AND_256"),
      std::make_pair(BTM_OOB_UNKNOWN, "BTM_OOB_UNKNOWN"),
  };
  for (const auto& data : datas) {
    ASSERT_STREQ(data.second.c_str(), btm_oob_data_text(data.first).c_str());
  }
  auto unknown = base::StringPrintf("UNKNOWN[%hhu]",
                                    std::numeric_limits<std::uint8_t>::max());
  ASSERT_STREQ(unknown.c_str(),
               btm_oob_data_text(static_cast<tBTM_OOB_DATA>(
                                     std::numeric_limits<std::uint8_t>::max()))
                   .c_str());
}

TEST_F(StackBtmSecTest, bond_type_text) {
  std::vector<std::pair<tBTM_BOND_TYPE, std::string>> datas = {
      std::make_pair(BOND_TYPE_UNKNOWN, "BOND_TYPE_UNKNOWN"),
      std::make_pair(BOND_TYPE_PERSISTENT, "BOND_TYPE_PERSISTENT"),
      std::make_pair(BOND_TYPE_TEMPORARY, "BOND_TYPE_TEMPORARY"),
  };
  for (const auto& data : datas) {
    ASSERT_STREQ(data.second.c_str(), bond_type_text(data.first).c_str());
  }
  auto unknown = base::StringPrintf("UNKNOWN[%hhu]",
                                    std::numeric_limits<std::uint8_t>::max());
  ASSERT_STREQ(unknown.c_str(),
               bond_type_text(static_cast<tBTM_BOND_TYPE>(
                                  std::numeric_limits<std::uint8_t>::max()))
                   .c_str());
}

TEST_F(StackBtmSecWithInitFreeTest, wipe_secrets_and_remove) {
  bluetooth::common::InitFlags::SetAllForTesting();

  RawAddress bd_addr = RawAddress({0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6});
  const uint16_t classic_handle = 0x1234;
  const uint16_t ble_handle = 0x9876;

  // Setup device
  tBTM_SEC_DEV_REC* device_record = btm_sec_allocate_dev_rec();
  ASSERT_NE(nullptr, device_record);
  ASSERT_EQ(BTM_SEC_IN_USE, device_record->sec_rec.sec_flags);
  device_record->bd_addr = bd_addr;
  device_record->hci_handle = classic_handle;
  device_record->ble_hci_handle = ble_handle;

  wipe_secrets_and_remove(device_record);
}
