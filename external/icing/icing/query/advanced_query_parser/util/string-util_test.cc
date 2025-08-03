// Copyright (C) 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "icing/query/advanced_query_parser/util/string-util.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;
using ::testing::IsEmpty;

TEST(StringUtilTest, UnescapeStringEmptyString) {
  EXPECT_THAT(string_util::UnescapeStringValue(""), IsOkAndHolds(IsEmpty()));
}

TEST(StringUtilTest, UnescapeStringStringWithNoEscapes) {
  EXPECT_THAT(string_util::UnescapeStringValue("foo"), IsOkAndHolds("foo"));
  EXPECT_THAT(string_util::UnescapeStringValue("f o o"), IsOkAndHolds("f o o"));
  EXPECT_THAT(string_util::UnescapeStringValue("f\to\to"),
              IsOkAndHolds("f\to\to"));
  EXPECT_THAT(string_util::UnescapeStringValue("f.o.o"), IsOkAndHolds("f.o.o"));
}

TEST(StringUtilTest, UnescapeStringStringWithEscapes) {
  EXPECT_THAT(string_util::UnescapeStringValue("f\\oo"), IsOkAndHolds("foo"));
  EXPECT_THAT(string_util::UnescapeStringValue("f\\\\oo"),
              IsOkAndHolds("f\\oo"));
  EXPECT_THAT(string_util::UnescapeStringValue("f\\\"oo"),
              IsOkAndHolds("f\"oo"));
  EXPECT_THAT(string_util::UnescapeStringValue("foo\\"), IsOkAndHolds("foo"));
  EXPECT_THAT(string_util::UnescapeStringValue("foo b\\a\\\"r baz"),
              IsOkAndHolds("foo ba\"r baz"));
  EXPECT_THAT(string_util::UnescapeStringValue("bar b\\aar bar\\s bart"),
              IsOkAndHolds("bar baar bars bart"));
  EXPECT_THAT(string_util::UnescapeStringValue("\\\\\\\\a"),
              IsOkAndHolds("\\\\a"));
}

TEST(StringUtilTest, UnescapeStringQuoteWithoutEscape) {
  EXPECT_THAT(string_util::UnescapeStringValue("f\\o\"o"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(string_util::UnescapeStringValue("f\"oo"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(StringUtilTest, FindEscapedTokenEmptyUnescapedToken) {
  EXPECT_THAT(string_util::FindEscapedToken("foo b\\a\\\"r baz", ""),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(StringUtilTest, FindEscapedTokenTokenNotPresent) {
  EXPECT_THAT(string_util::FindEscapedToken("foo b\\a\\\"r baz", "elephant"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(string_util::FindEscapedToken("foo b\\a\\\"r baz", "bat"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(string_util::FindEscapedToken("foo b\\a\\\"r baz", "taz"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(string_util::FindEscapedToken("foo b\\a\\\"r baz", "bazz"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(StringUtilTest, FindEscapedTokenMatchInMiddleToken) {
  EXPECT_THAT(string_util::FindEscapedToken("babar", "bar"),
              IsOkAndHolds("bar"));
}

TEST(StringUtilTest, FindEscapedTokenMatches) {
  EXPECT_THAT(string_util::FindEscapedToken("foo b\\a\\\"r baz", "ba\"r"),
              IsOkAndHolds("b\\a\\\"r"));
  EXPECT_THAT(string_util::FindEscapedToken("\\\\\\\\a", "\\\\a"),
              IsOkAndHolds("\\\\\\\\a"));
}

TEST(StringUtilTest, FindEscapedTokenTraversesThroughEscapedText) {
  std::string_view escaped_text = "bar b\\aar bar\\s bart";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::string_view result,
      string_util::FindEscapedToken(escaped_text, "bar"));
  // escaped_text = "bar b\\aar bar\\s bart";
  // escaped_token   ^  ^
  EXPECT_THAT(result, Eq("bar"));

  // escaped_text = "b\\aar bar\\s bart";
  // escaped_token          ^  ^
  const char* result_end = result.data() + result.length();
  escaped_text = escaped_text.substr(result_end - escaped_text.data());
  ICING_ASSERT_OK_AND_ASSIGN(
      result, string_util::FindEscapedToken(escaped_text, "bar"));
  EXPECT_THAT(result, Eq("bar"));

  // escaped_text = "\\s bart";
  // escaped_token       ^  ^
  result_end = result.data() + result.length();
  escaped_text = escaped_text.substr(result_end - escaped_text.data());
  ICING_ASSERT_OK_AND_ASSIGN(
      result, string_util::FindEscapedToken(escaped_text, "bar"));
  EXPECT_THAT(result, Eq("bar"));

  result_end = result.data() + result.length();
  escaped_text = escaped_text.substr(result_end - escaped_text.data());
  EXPECT_THAT(string_util::FindEscapedToken(escaped_text, "bar"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

}  // namespace

}  // namespace lib
}  // namespace icing