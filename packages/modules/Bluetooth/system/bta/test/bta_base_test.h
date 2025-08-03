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

#include <gmock/gmock.h>

#include "btm_client_interface.h"
#include "test/common/mock_functions.h"
#include "test/fake/fake_osi.h"
#include "test/mock/mock_stack_btm_interface.h"

class BtaBaseTest : public testing::Test {
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    reset_mock_btm_client_interface();
    fake_osi_ = std::make_unique<test::fake::FakeOsi>();
    ASSERT_NE(get_btm_client_interface().lifecycle.btm_init, nullptr);
    ASSERT_NE(get_btm_client_interface().lifecycle.btm_free, nullptr);
  }

  void TearDown() override {}

  std::unique_ptr<test::fake::FakeOsi> fake_osi_;
};
