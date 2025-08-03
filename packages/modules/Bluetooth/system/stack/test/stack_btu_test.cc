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

#include <gtest/gtest.h>

#include <cstdint>

#include "stack/include/btu_hcif.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/hcidefs.h"
#include "test/common/mock_functions.h"

/* Function for test provided by btu_hcif.cc */
void btu_hcif_hdl_command_status(uint16_t opcode, uint8_t status,
                                 const uint8_t* p_cmd);

class StackBtuTest : public ::testing::Test {
 protected:
  void SetUp() override { reset_mock_function_count_map(); }
};

TEST_F(StackBtuTest, post_on_main) {}

TEST_F(StackBtuTest, btm_sco_connection_failed_called) {
  uint8_t p_cmd[10];  // garbage data for testing
  bluetooth::legacy::testing::btu_hcif_hdl_command_status(
      HCI_SETUP_ESCO_CONNECTION, HCI_ERR_UNSPECIFIED, p_cmd);
  ASSERT_EQ(1, get_func_call_count("btm_sco_connection_failed"));
}
