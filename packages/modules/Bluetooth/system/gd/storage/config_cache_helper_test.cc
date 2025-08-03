/*
 * Copyright 2020 The Android Open Source Project
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

#include "storage/config_cache_helper.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <limits>
#include <vector>

#include "storage/device.h"

namespace testing {

using bluetooth::storage::ConfigCache;
using bluetooth::storage::ConfigCacheHelper;
using bluetooth::storage::Device;

TEST(ConfigCacheHelperTest, set_get_bool_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  // true
  ConfigCacheHelper(config).SetBool("A", "B", true);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("true")));
  ASSERT_THAT(ConfigCacheHelper(config).GetBool("A", "B"), Optional(IsTrue()));
  // false
  ConfigCacheHelper(config).SetBool("A", "B", false);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("false")));
  ASSERT_THAT(ConfigCacheHelper(config).GetBool("A", "B"), Optional(IsFalse()));
}

TEST(ConfigCacheHelperTest, set_get_uint64_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  // small
  ConfigCacheHelper(config).SetUint64("A", "B", 123);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("123")));
  ASSERT_THAT(ConfigCacheHelper(config).GetUint64("A", "B"), Optional(Eq(uint64_t(123))));
  // big
  uint64_t num = std::numeric_limits<int>::max();
  num = num * 10;
  ConfigCacheHelper(config).SetUint64("A", "B", num);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq(std::to_string(std::numeric_limits<int>::max()) + "0")));
  ASSERT_THAT(ConfigCacheHelper(config).GetUint64("A", "B"), Optional(Eq(num)));
  // zero
  ConfigCacheHelper(config).SetUint64("A", "B", 0);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("0")));
  ASSERT_THAT(ConfigCacheHelper(config).GetUint64("A", "B"), Optional(Eq(0ul)));
}

TEST(ConfigCacheHelperTest, set_get_uint32_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  // small
  ConfigCacheHelper(config).SetUint32("A", "B", 123);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("123")));
  ASSERT_THAT(ConfigCacheHelper(config).GetUint64("A", "B"), Optional(Eq(uint32_t(123))));
  // big
  uint64_t num = std::numeric_limits<uint32_t>::max();
  num *= 10;
  ConfigCacheHelper(config).SetUint64("A", "B", num);
  ASSERT_THAT(
      config.GetProperty("A", "B"), Optional(StrEq(std::to_string(std::numeric_limits<uint32_t>::max()) + "0")));
  ASSERT_FALSE(ConfigCacheHelper(config).GetUint32("A", "B"));
  // zero
  ConfigCacheHelper(config).SetUint32("A", "B", 0);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("0")));
  ASSERT_THAT(ConfigCacheHelper(config).GetUint32("A", "B"), Optional(Eq(0u)));
}

TEST(ConfigCacheHelperTest, set_get_int64_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  // positive
  int64_t num = std::numeric_limits<int32_t>::max();
  num *= 10;
  ConfigCacheHelper(config).SetInt64("A", "B", num);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq(std::to_string(std::numeric_limits<int32_t>::max()) + "0")));
  ASSERT_THAT(ConfigCacheHelper(config).GetInt64("A", "B"), Optional(Eq(int64_t(num))));
  // negative
  ConfigCacheHelper(config).SetInt64("A", "B", -1 * num);
  ASSERT_THAT(
      config.GetProperty("A", "B"), Optional(StrEq("-" + std::to_string(std::numeric_limits<int32_t>::max()) + "0")));
  ASSERT_THAT(ConfigCacheHelper(config).GetInt64("A", "B"), Optional(Eq(-1 * num)));
  // zero
  ConfigCacheHelper(config).SetInt("A", "B", 0);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("0")));
  ASSERT_THAT(ConfigCacheHelper(config).GetInt64("A", "B"), Optional(Eq(int64_t(0))));
}

TEST(ConfigCacheHelperTest, set_get_int_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  // positive
  ConfigCacheHelper(config).SetInt("A", "B", 123);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("123")));
  ASSERT_THAT(ConfigCacheHelper(config).GetInt("A", "B"), Optional(Eq(int(123))));
  // negative
  ConfigCacheHelper(config).SetInt("A", "B", -123);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("-123")));
  ASSERT_THAT(ConfigCacheHelper(config).GetInt("A", "B"), Optional(Eq(int(-123))));
  // zero
  ConfigCacheHelper(config).SetInt("A", "B", 0);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("0")));
  ASSERT_THAT(ConfigCacheHelper(config).GetInt("A", "B"), Optional(Eq(int(0))));
  // big
  int64_t num = std::numeric_limits<int32_t>::max();
  num *= 10;
  ConfigCacheHelper(config).SetInt64("A", "B", num);
  ASSERT_FALSE(ConfigCacheHelper(config).GetInt("A", "B"));
}

TEST(ConfigCacheHelperTest, set_get_bin_test) {
  ConfigCache config(100, Device::kLinkKeyProperties);
  // empty
  std::vector<uint8_t> data;
  ConfigCacheHelper(config).SetBin("A", "B", data);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("")));
  ASSERT_THAT(ConfigCacheHelper(config).GetBin("A", "B"), Optional(ContainerEq(data)));
  // non-empty
  std::vector<uint8_t> data2 = {0xAB, 0x5D, 0x42};
  ConfigCacheHelper(config).SetBin("A", "B", data2);
  ASSERT_THAT(config.GetProperty("A", "B"), Optional(StrEq("ab5d42")));
  ASSERT_THAT(ConfigCacheHelper(config).GetBin("A", "B"), Optional(ContainerEq(data2)));
}

}  // namespace testing