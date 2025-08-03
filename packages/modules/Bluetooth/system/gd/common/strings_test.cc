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

#include "common/strings.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <array>
#include <memory>

namespace testing {

using bluetooth::common::BoolFromString;
using bluetooth::common::FromHexString;
using bluetooth::common::Int64FromString;
using bluetooth::common::StringFormat;
using bluetooth::common::StringFormatTime;
using bluetooth::common::StringFormatTimeWithMilliseconds;
using bluetooth::common::StringJoin;
using bluetooth::common::StringSplit;
using bluetooth::common::StringTrim;
using bluetooth::common::ToHexString;
using bluetooth::common::ToString;
using bluetooth::common::Uint64FromString;

static inline bool is_arch32() {
  return sizeof(long) == 4;
}
static inline bool is_arch64() {
  return sizeof(long) == 8;
}

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Winteger-overflow"
TEST(StringsTest, to_hex_string_from_number) {
  ASSERT_EQ(ToHexString(0), "0x00000000");
  ASSERT_EQ(ToHexString(3), "0x00000003");
  ASSERT_EQ(ToHexString(25), "0x00000019");
  ASSERT_EQ(ToHexString(-25), "-0x00000019");
  ASSERT_EQ(ToHexString(INT_MIN + 1), "-0x7fffffff");
  ASSERT_EQ(ToHexString(INT_MAX), "0x7fffffff");
  ASSERT_EQ(ToHexString(INT_MIN), "INT_MIN");
  if (is_arch32()) {
    ASSERT_EQ(ToHexString(1 + INT_MAX), "INT_MIN");
    ASSERT_EQ(ToHexString(2 + INT_MAX), "-0x7fffffff");  // Rolled over
    ASSERT_EQ(ToHexString(-1 - INT_MIN), "0x7fffffff");  // Rolled over
    ASSERT_EQ(ToHexString(LONG_MAX), "0x7fffffff");
    ASSERT_EQ(ToHexString(LONG_MAX - 1L), "0x7ffffffe");
    ASSERT_EQ(ToHexString(LONG_MIN), "LONG_MIN");
    ASSERT_EQ(ToHexString(LONG_MIN + 1L), "-0x7fffffff");
  } else if (is_arch64()) {
    ASSERT_EQ(ToHexString((signed long)INT_MIN), "-0x0000000080000000");
    ASSERT_EQ(ToHexString(1 + INT_MAX), "INT_MIN");      // Rolled over
    ASSERT_EQ(ToHexString(2 + INT_MAX), "-0x7fffffff");  // Rolled over
    ASSERT_EQ(ToHexString(1L + INT_MAX), "0x0000000080000000");
    ASSERT_EQ(ToHexString(2L + INT_MAX), "0x0000000080000001");
    ASSERT_EQ(ToHexString(-1L + INT_MIN), "-0x0000000080000001");
    ASSERT_EQ(ToHexString(LONG_MAX), "0x7fffffffffffffff");
    ASSERT_EQ(ToHexString(LONG_MAX - 1L), "0x7ffffffffffffffe");
    ASSERT_EQ(ToHexString(LONG_MIN), "LONG_MIN");
    ASSERT_EQ(ToHexString(LONG_MIN + 1L), "-0x7fffffffffffffff");
  } else {
    LOG_ERROR("Unknown architecture");
    ASSERT_TRUE(false);
  }
  ASSERT_EQ(ToHexString('a'), "0x61");
}

TEST(StringsTest, to_hex_string_from_number_unsigned_int) {
  ASSERT_EQ(ToHexString(0U), "0x00000000");
  ASSERT_EQ(ToHexString(1U), "0x00000001");
  ASSERT_EQ(ToHexString(3U), "0x00000003");
  ASSERT_EQ(ToHexString(25U), "0x00000019");
  ASSERT_EQ(ToHexString(UINT_MAX), "0xffffffff");
  ASSERT_EQ(ToHexString(1U + UINT_MAX), "0x00000000");  // Rolled over
  ASSERT_EQ(ToHexString(2U + UINT_MAX), "0x00000001");  // Rolled over
}
#pragma clang diagnostic pop

TEST(StringsTest, trim_string_test) {
  ASSERT_EQ(StringTrim("  aa bb"), "aa bb");
  ASSERT_EQ(StringTrim("aa bb "), "aa bb");
  ASSERT_EQ(StringTrim("  aa bb "), "aa bb");
  ASSERT_EQ(StringTrim("  aa bb \n"), "aa bb");
  ASSERT_EQ(StringTrim("  \raa bb\t \n"), "aa bb");
}

TEST(StringsTest, split_string_test) {
  ASSERT_THAT(StringSplit("", ","), ElementsAre(""));
  ASSERT_THAT(StringSplit("1,2,3", ","), ElementsAre("1", "2", "3"));
  ASSERT_THAT(StringSplit("1,2,3", "!"), ElementsAre("1,2,3"));
  ASSERT_THAT(StringSplit("1,2,3", ",", 2), ElementsAre("1", "2,3"));
  ASSERT_THAT(StringSplit("a,b,", ","), ElementsAre("a", "b", ""));
  ASSERT_THAT(StringSplit("ab,", ",", 2), ElementsAre("ab", ""));
  ASSERT_THAT(StringSplit("ab,,", ",", 2), ElementsAre("ab", ","));
  ASSERT_THAT(StringSplit("ab,,", ",", 1), ElementsAre("ab,,"));
  ASSERT_THAT(StringSplit("1,,2,,3", ",,"), ElementsAre("1", "2", "3"));
  ASSERT_THAT(StringSplit("1,,2,,3,,", ",,"), ElementsAre("1", "2", "3", ""));
  ASSERT_THAT(StringSplit("1,,2,,3,,", ",,", 2), ElementsAre("1", "2,,3,,"));
  ASSERT_THAT(StringSplit("1", ",,", 2), ElementsAre("1"));
  ASSERT_DEATH({ StringSplit("1,2,3", ""); }, "delim cannot be empty");
}

TEST(StringsTest, join_string_test) {
  ASSERT_THAT(StringJoin({{"1", "2", "3"}}, ","), StrEq("1,2,3"));
  ASSERT_THAT(StringJoin({{}}, ","), StrEq(""));
  ASSERT_THAT(StringJoin({{"1"}}, ","), StrEq("1"));
  ASSERT_THAT(StringJoin({{"1", "2", "3"}}, ",,"), StrEq("1,,2,,3"));
  ASSERT_THAT(StringJoin({{"1", ",", "3"}}, ",,"), StrEq("1,,,,,3"));
}

TEST(StringsTest, to_hex_string_test) {
  // normal
  ASSERT_THAT(ToHexString({0x12, 0x34, 0x56, 0xab}), Eq("123456ab"));
  // empty
  ASSERT_THAT(ToHexString({}), Eq(""));
  // unary
  ASSERT_THAT(ToHexString({0x12}), Eq("12"));
  // half
  ASSERT_THAT(ToHexString({0x6, 0x5, 0x56, 0xb}), Eq("0605560b"));
  // other types
  std::array<uint8_t, 2> a = {0x12, 0x56};
  ASSERT_THAT(ToHexString(a.begin(), a.end()), Eq("1256"));
  std::vector<uint8_t> b = {0x34, 0x78};
  ASSERT_THAT(ToHexString(b.begin(), b.end()), Eq("3478"));
}

TEST(StringsTest, from_hex_string_test) {
  // normal
  ASSERT_THAT(FromHexString("aabbccdd1122"), Optional(ElementsAre(0xaa, 0xbb, 0xcc, 0xdd, 0x11, 0x22)));
  // empty
  ASSERT_THAT(FromHexString(""), Optional(IsEmpty()));
  // unary
  ASSERT_THAT(FromHexString("aa"), Optional(ElementsAre(0xaa)));
  // half
  ASSERT_THAT(FromHexString("0605560b"), Optional(ElementsAre(0x6, 0x5, 0x56, 0xb)));
  // upper case letter
  ASSERT_THAT(FromHexString("AABBCC"), Optional(ElementsAre(0xaa, 0xbb, 0xcc)));
  // upper and lower case letter mixed
  ASSERT_THAT(FromHexString("aAbbCC"), Optional(ElementsAre(0xaa, 0xbb, 0xcc)));
  // Error: odd length
  ASSERT_FALSE(FromHexString("0605560"));
  // Error: non hex char
  ASSERT_FALSE(FromHexString("060u560b"));
}

TEST(StringsTest, int64_from_and_to_string_test) {
  ASSERT_THAT(Int64FromString("42"), Optional(Eq(int64_t(42))));
  ASSERT_THAT(Int64FromString("-42"), Optional(Eq(int64_t(-42))));
  ASSERT_THAT(Int64FromString("0"), Optional(Eq(int64_t(0))));
  ASSERT_FALSE(Int64FromString(""));
  // only base 10 is supported
  ASSERT_FALSE(Int64FromString("0x42ab"));
  ASSERT_FALSE(Int64FromString("-0x42"));
  // floating point not supported
  ASSERT_FALSE(Int64FromString("42.0"));
  ASSERT_FALSE(Int64FromString("-42.0"));
  ASSERT_FALSE(Int64FromString("42abc"));
  ASSERT_FALSE(Int64FromString(""));
  // INT32_MAX + 1
  ASSERT_THAT(Int64FromString("2147483648"), Optional(Eq(int64_t(2147483648))));
  ASSERT_THAT(ToString(int64_t(2147483648)), StrEq("2147483648"));
  // INT32_MIN - 1
  ASSERT_THAT(Int64FromString("-2147483649"), Optional(Eq(int64_t(-2147483649))));
  ASSERT_THAT(ToString(int64_t(-2147483649)), StrEq("-2147483649"));
  // INT64_MAX
  ASSERT_THAT(Int64FromString("9223372036854775807"), Optional(Eq(int64_t(9223372036854775807))));
  ASSERT_THAT(ToString(int64_t(9223372036854775807)), StrEq("9223372036854775807"));
  // INT64_MAX+1
  ASSERT_FALSE(Int64FromString("9223372036854775808"));
  // INT64_MIN
  ASSERT_THAT(Int64FromString("-9223372036854775808"), Optional(Eq(int64_t(-9223372036854775807LL - 1))));
  ASSERT_THAT(ToString(int64_t(-9223372036854775807LL - 1)), StrEq("-9223372036854775808"));
  // INT64_MIN-1
  ASSERT_FALSE(Int64FromString("-9223372036854775809"));
}

TEST(StringsTest, uint64_from_and_to_string_test) {
  ASSERT_THAT(Uint64FromString("42"), Optional(Eq(uint64_t(42))));
  ASSERT_THAT(Uint64FromString("0"), Optional(Eq(uint64_t(0))));
  ASSERT_FALSE(Uint64FromString(""));
  // only base 10 is supported
  ASSERT_FALSE(Uint64FromString("0x42ab"));
  // only positive number is supported
  ASSERT_FALSE(Uint64FromString("-42"));
  // floating point not supported
  ASSERT_FALSE(Uint64FromString("42.0"));
  ASSERT_FALSE(Uint64FromString("-42.0"));
  ASSERT_FALSE(Uint64FromString("42abc"));
  ASSERT_FALSE(Uint64FromString(""));
  // UINT32_MAX + 1
  ASSERT_THAT(Uint64FromString("4294967295"), Optional(Eq(uint64_t(4294967295))));
  ASSERT_THAT(ToString(uint64_t(4294967295)), StrEq("4294967295"));
  // UINT64_MAX
  ASSERT_THAT(Uint64FromString("18446744073709551615"), Optional(Eq(uint64_t(18446744073709551615ULL))));
  ASSERT_THAT(ToString(uint64_t(18446744073709551615ULL)), StrEq("18446744073709551615"));
  // UINT64_MAX+1
  ASSERT_FALSE(Uint64FromString("18446744073709551616"));
}

TEST(StringsTest, bool_from_and_to_string_test) {
  ASSERT_THAT(BoolFromString("true"), Optional(IsTrue()));
  ASSERT_THAT(BoolFromString("false"), Optional(IsFalse()));
  ASSERT_FALSE(BoolFromString("abc"));
  ASSERT_FALSE(BoolFromString("FALSE"));
  ASSERT_FALSE(BoolFromString("TRUE"));
  ASSERT_FALSE(BoolFromString(""));
  ASSERT_THAT(ToString(true), StrEq("true"));
  ASSERT_THAT(ToString(false), StrEq("false"));
}

TEST(StringsTest, string_format_test) {
  ASSERT_THAT(StringFormat("%s", "hello"), StrEq("hello"));
  ASSERT_THAT(StringFormat("%d", 42), StrEq("42"));
  ASSERT_THAT(StringFormat("%s world", "hello"), StrEq("hello world"));
  ASSERT_THAT(StringFormat("%d %.1f 0x%02x", 42, 43.123, 0x8), StrEq("42 43.1 0x08"));
}

TEST(StringsTest, string_format_time_test) {
  std::string format("%Y-%m-%d %H:%M:%S");
  time_t then = 123456789;
  struct std::tm tm;
  gmtime_r(&then, &tm);
  ASSERT_THAT(StringFormatTime(format, tm), StrEq("1973-11-29 21:33:09"));
}

TEST(StringsTest, string_format_time_with_ms_in_the_beginning_test) {
  std::string format("%Y-%m-%d %H:%M:%S");
  std::time_t from_time = 0;
  std::chrono::time_point<std::chrono::system_clock> time_point = std::chrono::system_clock::from_time_t(from_time);

  ASSERT_THAT(StringFormatTimeWithMilliseconds(format, time_point, gmtime), StrEq("1970-01-01 00:00:00.000"));
}

TEST(StringsTest, string_format_time_with_ms_test) {
  std::string format("%Y-%m-%d %H:%M:%S");
  std::time_t from_time1 = 1234567890;
  std::chrono::time_point<std::chrono::system_clock> time_point1 = std::chrono::system_clock::from_time_t(from_time1);
  std::time_t from_time2 = 1234567890;
  std::chrono::time_point<std::chrono::system_clock> time_point2 = std::chrono::system_clock::from_time_t(from_time2);

  time_point2 += std::chrono::milliseconds(1);

  ASSERT_THAT(StringFormatTimeWithMilliseconds(format, time_point1, gmtime), StrEq("2009-02-13 23:31:30.000"));
  ASSERT_THAT(StringFormatTimeWithMilliseconds(format, time_point2, gmtime), StrEq("2009-02-13 23:31:30.001"));
}

class ExampleClass {};
std::ostream& operator<<(std::ostream& os, const ExampleClass& /* obj */) {
  os << "ExampleClass";
  return os;
}

TEST(StringsTest, example_class_to_string_test) {
  ExampleClass obj;
  ASSERT_THAT(ToString(obj), StrEq("ExampleClass"));
}

}  // namespace testing
