/*
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
 */

#include <gtest/gtest.h>

#include "ble_hci_link_interface.h"
#include "bt_hdr.h"
#include "btm_ble_api_types.h"
#include "hci_error_code.h"
#include "osi/include/allocator.h"

namespace {

class StackBTMRegressionTests : public ::testing::Test {
 protected:
  void SetUp() override {}
  void TearDown() override {}
};

// regression test for b/260078907
TEST_F(StackBTMRegressionTests,
       OOB_in_btm_ble_add_resolving_list_entry_complete) {
  BT_HDR event{};
  btm_ble_add_resolving_list_entry_complete(event.data, 0);
}

// regression test for b/255304475
TEST_F(StackBTMRegressionTests,
       OOB_in_btm_ble_clear_resolving_list_complete) {
  BT_HDR event{};
  btm_ble_clear_resolving_list_complete(event.data, 0);
}

}  // namespace
