/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0(the "License");
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
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstdint>

#include "common/init_flags.h"
#include "hci/controller_interface_mock.h"
#include "stack/include/acl_api.h"
#include "stack/include/acl_hci_link_interface.h"
#include "stack/include/hci_error_code.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_main_shim_entry.h"
#include "types/raw_address.h"

using testing::Return;

namespace {
const char* test_flags[] = {
    "INIT_default_log_level_str=LOG_DEBUG",
    nullptr,
};

const RawAddress kRawAddress = RawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
const uint16_t kHciHandle = 123;

}  // namespace

struct power_mode_callback {
  const RawAddress bd_addr;
  tBTM_PM_STATUS status;
  uint16_t value;
  tHCI_STATUS hci_status;
};

#include <deque>
std::deque<power_mode_callback> power_mode_callback_queue;

class StackBtmPowerMode : public testing::Test {
 protected:
  void SetUp() override {
    ON_CALL(controller_, SupportsSniffMode).WillByDefault(Return(true));
    bluetooth::hci::testing::mock_controller_ = &controller_;
    power_mode_callback_queue.clear();
    reset_mock_function_count_map();
    bluetooth::common::InitFlags::Load(test_flags);
    ASSERT_EQ(BTM_SUCCESS,
              BTM_PmRegister(BTM_PM_REG_SET, &pm_id_,
                             [](const RawAddress& p_bda, tBTM_PM_STATUS status,
                                uint16_t value, tHCI_STATUS hci_status) {
                               power_mode_callback_queue.push_back(
                                   power_mode_callback{
                                       .bd_addr = p_bda,
                                       .status = status,
                                       .value = value,
                                       .hci_status = hci_status,
                                   });
                             }));
  }

  void TearDown() override {
    ASSERT_EQ(BTM_SUCCESS,
              BTM_PmRegister(BTM_PM_DEREG, &pm_id_,
                             [](const RawAddress& p_bda, tBTM_PM_STATUS status,
                                uint16_t value, tHCI_STATUS hci_status) {}));
    bluetooth::hci::testing::mock_controller_ = nullptr;
  }

  bluetooth::hci::testing::MockControllerInterface controller_;
  uint8_t pm_id_{0};
};

class StackBtmPowerModeConnected : public StackBtmPowerMode {
 protected:
  void SetUp() override {
    StackBtmPowerMode::SetUp();
    BTM_PM_OnConnected(kHciHandle, kRawAddress);
  }

  void TearDown() override {
    BTM_PM_OnDisconnected(kHciHandle);
    StackBtmPowerMode::TearDown();
  }
};

TEST_F(StackBtmPowerMode, BTM_SetPowerMode__Undefined) {
  tBTM_PM_PWR_MD mode = {};
  ASSERT_EQ(BTM_UNKNOWN_ADDR, BTM_SetPowerMode(pm_id_, kRawAddress, &mode));
}

TEST_F(StackBtmPowerModeConnected, BTM_SetPowerMode__AlreadyActive) {
  tBTM_PM_PWR_MD mode = {};
  ASSERT_EQ(BTM_SUCCESS, BTM_SetPowerMode(pm_id_, kRawAddress, &mode));
}

TEST_F(StackBtmPowerModeConnected, BTM_SetPowerMode__ActiveToSniff) {
  tBTM_PM_PWR_MD mode = {
      .mode = BTM_PM_MD_SNIFF,
  };
  ASSERT_EQ("BTM_CMD_STARTED",
            btm_status_text(BTM_SetPowerMode(pm_id_, kRawAddress, &mode)));
  ASSERT_EQ(1, get_func_call_count("btsnd_hcic_sniff_mode"));

  // Respond with successful command status for mode command
  btm_pm_proc_cmd_status(HCI_SUCCESS);

  // Check power mode state directly
  {
    tBTM_PM_MODE current_power_mode;
    ASSERT_TRUE(BTM_ReadPowerMode(kRawAddress, &current_power_mode));
    ASSERT_EQ(BTM_PM_STS_PENDING, current_power_mode);
  }

  // Check power mode state from callback
  ASSERT_EQ(1U, power_mode_callback_queue.size());
  {
    const auto cb = power_mode_callback_queue.front();
    power_mode_callback_queue.pop_front();

    ASSERT_EQ(kRawAddress, cb.bd_addr);
    ASSERT_EQ(BTM_PM_STS_PENDING, cb.status);
    ASSERT_EQ(0, cb.value);
    ASSERT_EQ(HCI_SUCCESS, cb.hci_status);
  }

  // Respond with a successful mode change event
  btm_pm_proc_mode_change(HCI_SUCCESS, kHciHandle, HCI_MODE_SNIFF, 0);

  {
    tBTM_PM_MODE current_power_mode;
    ASSERT_TRUE(BTM_ReadPowerMode(kRawAddress, &current_power_mode));
    ASSERT_EQ(BTM_PM_STS_SNIFF, current_power_mode);
  }

  // Check power mode state from callback
  ASSERT_EQ(1U, power_mode_callback_queue.size());
  {
    const auto cb = power_mode_callback_queue.front();
    power_mode_callback_queue.pop_front();

    ASSERT_EQ(kRawAddress, cb.bd_addr);
    ASSERT_EQ(BTM_PM_STS_SNIFF, cb.status);
    ASSERT_EQ(0, cb.value);
    ASSERT_EQ(HCI_SUCCESS, cb.hci_status);
  }
}

TEST_F(StackBtmPowerModeConnected, BTM_SetPowerMode__ActiveToSniffTwice) {
  tBTM_PM_PWR_MD mode = {
      .mode = BTM_PM_MD_SNIFF,
  };
  ASSERT_EQ("BTM_CMD_STARTED",
            btm_status_text(BTM_SetPowerMode(pm_id_, kRawAddress, &mode)));
  ASSERT_EQ(1, get_func_call_count("btsnd_hcic_sniff_mode"));

  // Respond with successful command status for mode command
  btm_pm_proc_cmd_status(HCI_SUCCESS);

  // Check power mode state directly
  {
    tBTM_PM_MODE current_power_mode;
    ASSERT_TRUE(BTM_ReadPowerMode(kRawAddress, &current_power_mode));
    ASSERT_EQ(BTM_PM_STS_PENDING, current_power_mode);
  }

  // Check power mode state from callback
  ASSERT_EQ(1U, power_mode_callback_queue.size());
  {
    const auto cb = power_mode_callback_queue.front();
    power_mode_callback_queue.pop_front();

    ASSERT_EQ(kRawAddress, cb.bd_addr);
    ASSERT_EQ(BTM_PM_STS_PENDING, cb.status);
    ASSERT_EQ(0, cb.value);
    ASSERT_EQ(HCI_SUCCESS, cb.hci_status);
  }

  // Send a second active to sniff command
  ASSERT_EQ("BTM_CMD_STORED",
            btm_status_text(BTM_SetPowerMode(pm_id_, kRawAddress, &mode)));
  // No command should be issued
  ASSERT_EQ(1, get_func_call_count("btsnd_hcic_sniff_mode"));

  // Check power mode state directly
  {
    tBTM_PM_MODE current_power_mode;
    ASSERT_TRUE(BTM_ReadPowerMode(kRawAddress, &current_power_mode));
    // NOTE: The mixed enum values
    ASSERT_EQ(
        static_cast<tBTM_PM_MODE>(BTM_PM_STS_PENDING | BTM_PM_STORED_MASK),
        current_power_mode);
  }
}
