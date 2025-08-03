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

#include "btif/include/btif_dm.h"

#include <android_bluetooth_flags.h>
#include <flag_macros.h>
#include <gtest/gtest.h>

#include <memory>

#include "bta/include/bta_api_data_types.h"
#include "btif/include/btif_dm.h"
#include "btif/include/mock_core_callbacks.h"
#include "main/shim/stack.h"
#include "module.h"
#include "stack/include/bt_dev_class.h"
#include "stack/include/btm_ble_api_types.h"
#include "storage/storage_module.h"
#include "test/fake/fake_osi.h"
#include "test/mock/mock_osi_properties.h"

using bluetooth::core::testing::MockCoreInterface;
using ::testing::ElementsAre;

namespace {
const RawAddress kRawAddress = {{0x11, 0x22, 0x33, 0x44, 0x55, 0x66}};
constexpr char kBdName[] = {'k', 'B', 'd', 'N', 'a', 'm', 'e', '\0'};
}  // namespace

namespace bluetooth {
namespace legacy {
namespace testing {

void set_interface_to_profiles(
    bluetooth::core::CoreInterface* interfaceToProfiles);

void bta_energy_info_cb(tBTM_BLE_TX_TIME_MS tx_time,
                        tBTM_BLE_RX_TIME_MS rx_time,
                        tBTM_BLE_IDLE_TIME_MS idle_time,
                        tBTM_BLE_ENERGY_USED energy_used,
                        tBTM_CONTRL_STATE ctrl_state, tBTA_STATUS status);

void btif_dm_search_services_evt(tBTA_DM_SEARCH_EVT event,
                                 tBTA_DM_SEARCH* p_data);

}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth

namespace {
constexpr tBTM_BLE_TX_TIME_MS tx_time = 0x12345678;
constexpr tBTM_BLE_RX_TIME_MS rx_time = 0x87654321;
constexpr tBTM_BLE_IDLE_TIME_MS idle_time = 0x2468acd0;
constexpr tBTM_BLE_ENERGY_USED energy_used = 0x13579bdf;
}  // namespace

class BtifDmTest : public ::testing::Test {
 protected:
  void SetUp() override {
    fake_osi_ = std::make_unique<test::fake::FakeOsi>();
    mock_core_interface_ = std::make_unique<MockCoreInterface>();
    bluetooth::legacy::testing::set_interface_to_profiles(
        mock_core_interface_.get());
  }

  void TearDown() override {}

  std::unique_ptr<test::fake::FakeOsi> fake_osi_;
  std::unique_ptr<MockCoreInterface> mock_core_interface_;
};

TEST_F(BtifDmTest, bta_energy_info_cb__with_no_uid) {
  static bool invoke_energy_info_cb_entered = false;
  bluetooth::core::testing::mock_event_callbacks.invoke_energy_info_cb =
      [](bt_activity_energy_info /* energy_info */,
         bt_uid_traffic_t* /* uid_data */) {
        invoke_energy_info_cb_entered = true;
      };

  bluetooth::legacy::testing::bta_energy_info_cb(
      tx_time, rx_time, idle_time, energy_used, BTM_CONTRL_UNKNOWN,
      BTA_SUCCESS);

  ASSERT_FALSE(invoke_energy_info_cb_entered);
}

class BtifDmWithUidTest : public BtifDmTest {
 protected:
  void SetUp() override {
    BtifDmTest::SetUp();
    btif_dm_init(uid_set_create());
  }

  void TearDown() override {
    void btif_dm_cleanup();
    BtifDmTest::TearDown();
  }
};

TEST_F(BtifDmWithUidTest, bta_energy_info_cb__with_uid) {
  static bool invoke_energy_info_cb_entered = false;
  bluetooth::core::testing::mock_event_callbacks.invoke_energy_info_cb =
      [](bt_activity_energy_info /* energy_info */,
         bt_uid_traffic_t* /* uid_data */) {
        invoke_energy_info_cb_entered = true;
      };
  bluetooth::legacy::testing::bta_energy_info_cb(
      tx_time, rx_time, idle_time, energy_used, BTM_CONTRL_UNKNOWN,
      BTA_SUCCESS);

  ASSERT_TRUE(invoke_energy_info_cb_entered);
}

class BtifDmWithStackTest : public BtifDmTest {
 protected:
  void SetUp() override {
    BtifDmTest::SetUp();
    modules_.add<bluetooth::storage::StorageModule>();
    bluetooth::shim::Stack::GetInstance()->StartModuleStack(
        &modules_,
        new bluetooth::os::Thread("gd_stack_thread",
                                  bluetooth::os::Thread::Priority::NORMAL));
  }

  void TearDown() override {
    bluetooth::shim::Stack::GetInstance()->Stop();
    BtifDmTest::TearDown();
  }
  bluetooth::ModuleList modules_;
};

#define MY_PACKAGE com::android::bluetooth::flags

TEST_F_WITH_FLAGS(BtifDmWithStackTest,
                  btif_dm_search_services_evt__BTA_DM_NAME_READ_EVT,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      MY_PACKAGE, rnr_present_during_service_discovery))) {
  static struct {
    bt_status_t status;
    RawAddress bd_addr;
    int num_properties;
    std::vector<bt_property_t> properties;
  } invoke_remote_device_properties_cb{
      .status = BT_STATUS_NOT_READY,
      .bd_addr = RawAddress::kEmpty,
      .num_properties = -1,
      .properties = {},
  };

  bluetooth::core::testing::mock_event_callbacks
      .invoke_remote_device_properties_cb =
      [](bt_status_t status, RawAddress bd_addr, int num_properties,
         bt_property_t* properties) {
        invoke_remote_device_properties_cb = {
            .status = status,
            .bd_addr = bd_addr,
            .num_properties = num_properties,
            .properties = std::vector<bt_property_t>(
                properties, properties + (size_t)num_properties),
        };
      };

  tBTA_DM_SEARCH data = {
      .disc_res =
          {
              // tBTA_DM_DISC_RES
              .bd_addr = kRawAddress,
              .bd_name = {},
              .services = 0,
              .device_type = BT_DEVICE_TYPE_UNKNOWN,
              .num_uuids = 0,
              .p_uuid_list = nullptr,
              .result = BTA_SUCCESS,
              .hci_status = HCI_SUCCESS,
          },
  };
  bd_name_copy(data.disc_res.bd_name, kBdName);

  bluetooth::legacy::testing::btif_dm_search_services_evt(BTA_DM_NAME_READ_EVT,
                                                          &data);

  ASSERT_EQ(BT_STATUS_SUCCESS, invoke_remote_device_properties_cb.status);
  ASSERT_EQ(kRawAddress, invoke_remote_device_properties_cb.bd_addr);
  ASSERT_EQ(1, invoke_remote_device_properties_cb.num_properties);
  ASSERT_EQ(BT_PROPERTY_BDNAME,
            invoke_remote_device_properties_cb.properties[0].type);
  ASSERT_EQ((int)strlen(kBdName),
            invoke_remote_device_properties_cb.properties[0].len);
  ASSERT_STREQ(
      kBdName,
      (const char*)invoke_remote_device_properties_cb.properties[0].val);
}

TEST_F(BtifDmWithStackTest, btif_dm_get_local_class_of_device__default) {
  DEV_CLASS dev_class = btif_dm_get_local_class_of_device();
  ASSERT_EQ(dev_class, kDevClassUnclassified);
}

std::string kClassOfDeviceText = "1,2,3";
DEV_CLASS kClassOfDevice = {1, 2, 3};
TEST_F(BtifDmWithStackTest, btif_dm_get_local_class_of_device__with_property) {
  test::mock::osi_properties::osi_property_get.body =
      [](const char* /* key */, char* value, const char* /* default_value */) {
        std::copy(kClassOfDeviceText.begin(), kClassOfDeviceText.end(), value);
        return kClassOfDeviceText.size();
      };

  DEV_CLASS dev_class = btif_dm_get_local_class_of_device();
  if (dev_class != kClassOfDevice) {
    // If BAP is enabled, an extra bit gets set.
    DEV_CLASS dev_class_with_bap = kClassOfDevice;
    dev_class_with_bap[1] |= 0x01 << 6;
    ASSERT_EQ(dev_class, dev_class_with_bap);
  }
  test::mock::osi_properties::osi_property_get = {};
}
