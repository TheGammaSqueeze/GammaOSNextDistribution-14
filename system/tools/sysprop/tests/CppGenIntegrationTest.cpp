/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <unistd.h>
#include <initializer_list>
#include <string>

#include <TestProperties.sysprop.h>
#include <android-base/properties.h>
#include <gtest/gtest.h>

// Tests generated sysprop API by:
// 1. calling setter with prop_value
// 2. comparing raw property string to expected_raw_value
// 3. calling getter and comparing the return value to prop_value
#define TEST_API(api_name, prop_name, prop_value, expected_raw_value)       \
  EXPECT_TRUE(api_name(prop_value));                                        \
  EXPECT_EQ(expected_raw_value, android::base::GetProperty(prop_name, "")); \
  EXPECT_TRUE(api_name().has_value());                                      \
  EXPECT_EQ(prop_value, api_name().value())

// Tests generated sysprop list API by:
// 1. calling setter with prop_value
// 2. comparing raw property string to expected_raw_value
// 3. calling getter and comparing the return value to prop_value
#define TEST_LIST_API(api_name, prop_name, prop_values, expected_raw_value) \
  EXPECT_TRUE(api_name(prop_values));                                       \
  EXPECT_EQ(expected_raw_value, android::base::GetProperty(prop_name, "")); \
  EXPECT_EQ(prop_values, api_name())

namespace {

template <typename T>
std::vector<std::optional<T>> OptionalList(std::initializer_list<T> l) {
  std::vector<std::optional<T>> ret;
  for (auto& e : l) ret.push_back(std::make_optional(e));
  return ret;
}

}  // namespace

TEST(SyspropTest, CppGenIntegrationTest) {
  using namespace android::sysprop::test::TestProperties;

  TEST_API(test_bool_as_int, "test.bool_as_int", true, "1");
  TEST_API(test_bool, "test.bool", true, "true");
  TEST_API(test_int, "test.int", -123, "-123");
  TEST_API(test_uint, "test.uint", 2147483648, "2147483648");
  TEST_API(test_long, "test.long", -9876543210, "-9876543210");
  TEST_API(test_ulong, "test.ulong", 123456789012345, "123456789012345");
  TEST_API(test_double, "test.double", 3.14159265358979323846,
           "3.1415926535897931");
  TEST_API(test_string, "test.string", "hello world", "hello world");
  TEST_API(test_enum, "test.enum", test_enum_values::C, "c");

  TEST_LIST_API(test_bool_as_int_list, "test.bool_as_int_list",
                OptionalList({true, false}), "1,0");

  TEST_LIST_API(test_bool_list, "test.bool_list",
                OptionalList({false, true, false, false, true}),
                "false,true,false,false,true");

  TEST_LIST_API(test_int_list, "test.int_list", OptionalList({-1337}), "-1337");

  TEST_LIST_API(test_uint_list, "test.uint_list",
                OptionalList({0u, 2147483648u, 1u}), "0,2147483648,1");

  TEST_LIST_API(test_long_list, "test.long_list",
                OptionalList<std::int64_t>({}), "");

  TEST_LIST_API(test_ulong_list, "test.ulong_list",
                OptionalList<std::uint64_t>({123456789012345}),
                "123456789012345");

  TEST_LIST_API(test_double_list, "test.double_list",
                OptionalList({0.0, 1.25, -3.0}), "0,1.25,-3");

  TEST_LIST_API(test_string_list, "test.string_list",
                OptionalList<std::string>({"hi,there"}), "hi\\,there");

  TEST_LIST_API(
      test_enum_list, "test.enum_list",
      OptionalList({test_enum_list_values::A, test_enum_list_values::B}), "a,b");
}
