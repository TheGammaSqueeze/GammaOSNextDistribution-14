/*
 * Copyright 2022 The Android Open Source Project
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

#include "bta/include/bta_api.h"

#include <base/functional/bind.h>
#include <base/location.h>
#include <gtest/gtest.h>

#include <cstdint>
#include <utility>
#include <vector>

#include "common/init_flags.h"
#include "test/common/mock_functions.h"

using namespace std::chrono_literals;

namespace {

const char* test_flags[] = {
    "INIT_logging_debug_enabled_for_all=true",
    nullptr,
};

}  // namespace

class BtaApiTest : public testing::Test {
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    bluetooth::common::InitFlags::Load(test_flags);
  }
  void TearDown() override {}
};

TEST_F(BtaApiTest, bta_status_text) {
  std::vector<std::pair<tBTA_STATUS, std::string>> statuses = {
      std::make_pair(BTA_SUCCESS, "BTA_SUCCESS"),
      std::make_pair(BTA_FAILURE, "BTA_FAILURE"),
      std::make_pair(BTA_PENDING, "BTA_PENDING"),
      std::make_pair(BTA_BUSY, "BTA_BUSY"),
      std::make_pair(BTA_NO_RESOURCES, "BTA_NO_RESOURCES"),
      std::make_pair(BTA_WRONG_MODE, "BTA_WRONG_MODE"),
  };
  for (const auto& status : statuses) {
    ASSERT_STREQ(status.second.c_str(), bta_status_text(status.first).c_str());
  }
  auto unknown =
      base::StringPrintf("UNKNOWN[%d]", std::numeric_limits<uint8_t>::max());
  ASSERT_STREQ(unknown.c_str(),
               bta_status_text(static_cast<tBTA_STATUS>(
                                   std::numeric_limits<uint8_t>::max()))
                   .c_str());
}
