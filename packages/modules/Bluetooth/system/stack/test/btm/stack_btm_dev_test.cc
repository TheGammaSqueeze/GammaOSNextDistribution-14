/*
 *  Copyright 2024 The Android Open Source Project
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

#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_sec_cb.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_main_shim_entry.h"

class StackBtmTest : public testing::Test {
 public:
 protected:
  void SetUp() override { reset_mock_function_count_map(); }
  void TearDown() override {}
};

class StackBtmDevTest : public StackBtmTest {
 protected:
  void SetUp() override { StackBtmTest::SetUp(); }
  void TearDown() override { StackBtmTest::TearDown(); }
};

TEST_F(StackBtmDevTest, btm_sec_allocate_dev_rec__no_list) {
  ASSERT_EQ(nullptr, btm_sec_allocate_dev_rec());
  ::btm_sec_cb.Init(BTM_SEC_MODE_SC);
  ::btm_sec_cb.Free();
  ASSERT_EQ(nullptr, btm_sec_allocate_dev_rec());
}

TEST_F(StackBtmDevTest, btm_sec_allocate_dev_rec__with_list) {
  ::btm_sec_cb.Init(BTM_SEC_MODE_SC);
  ASSERT_NE(nullptr, btm_sec_allocate_dev_rec());
  ::btm_sec_cb.Free();
}
