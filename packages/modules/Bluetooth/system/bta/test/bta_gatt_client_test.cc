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

#include <base/strings/stringprintf.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <sys/socket.h>

#include <string>

#include "bta/dm/bta_dm_gatt_client.h"
#include "common/circular_buffer.h"

using namespace bluetooth::common;

// Test hooks
namespace bluetooth {
namespace legacy {
namespace testing {

std::vector<TimestampedEntry<std::string>> PullCopyOfGattHistory();

}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth

class BtaDiscTest : public testing::Test {
 protected:
  void SetUp() override {}

  void TearDown() override {}
};

TEST_F(BtaDiscTest, nop) {}

TEST_F(BtaDiscTest, gatt_history_callback) {
  std::array<std::string, 3> a = {
      "ThisIsATest 0",
      "ThisIsATest 1",
      "ThisIsATest 2",
  };

  // C string
  gatt_history_callback(base::StringPrintf("%s", a[0].c_str()));
  // Cpp string
  gatt_history_callback(a[1]);
  // Third entry for "fun"
  gatt_history_callback(base::StringPrintf("%s", a[2].c_str()));

  std::vector<bluetooth::common::TimestampedEntry<std::string>> history =
      bluetooth::legacy::testing::PullCopyOfGattHistory();
  ASSERT_EQ(3UL, history.size());
  ASSERT_STREQ(a[0].c_str(), history[0].entry.c_str());
  ASSERT_STREQ(a[1].c_str(), history[1].entry.c_str());
  ASSERT_STREQ(a[2].c_str(), history[2].entry.c_str());
}
