// Copyright (C) 2022 Google LLC
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

#include "icing/join/qualified-id.h"

#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;

TEST(QualifiedIdTest, ValidQualifiedIdWithoutSpecialCharacters) {
  // "namespace#uri" -> "namespace" + "uri"
  ICING_ASSERT_OK_AND_ASSIGN(QualifiedId id,
                             QualifiedId::Parse(R"(namespace#uri)"));
  EXPECT_THAT(id.name_space(), Eq(R"(namespace)"));
  EXPECT_THAT(id.uri(), R"(uri)");
}

TEST(QualifiedIdTest, ValidQualifiedIdWithEscapedSpecialCharacters) {
  // "namespace\\#uri" -> "namespace\" + "uri"
  ICING_ASSERT_OK_AND_ASSIGN(QualifiedId id1,
                             QualifiedId::Parse(R"(namespace\\#uri)"));
  EXPECT_THAT(id1.name_space(), Eq(R"(namespace\)"));
  EXPECT_THAT(id1.uri(), R"(uri)");

  // "namespace\\\##uri" -> "namespace\#" + "uri"
  ICING_ASSERT_OK_AND_ASSIGN(QualifiedId id2,
                             QualifiedId::Parse(R"(namespace\\\##uri)"));
  EXPECT_THAT(id2.name_space(), Eq(R"(namespace\#)"));
  EXPECT_THAT(id2.uri(), R"(uri)");

  // "namespace#\#\\uri" -> "namespace" + "#\uri"
  ICING_ASSERT_OK_AND_ASSIGN(QualifiedId id3,
                             QualifiedId::Parse(R"(namespace#\#\\uri)"));
  EXPECT_THAT(id3.name_space(), Eq(R"(namespace)"));
  EXPECT_THAT(id3.uri(), R"(#\uri)");

  // "namespace\\\##\#\\uri" -> "namespace\#" + "#\uri"
  ICING_ASSERT_OK_AND_ASSIGN(QualifiedId id4,
                             QualifiedId::Parse(R"(namespace\\\##\#\\uri)"));
  EXPECT_THAT(id4.name_space(), Eq(R"(namespace\#)"));
  EXPECT_THAT(id4.uri(), R"(#\uri)");
}

TEST(QualifiedIdTest, InvalidQualifiedIdWithEmptyNamespaceOrUri) {
  // "#uri"
  EXPECT_THAT(QualifiedId::Parse(R"(#uri)"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // "namespace#"
  EXPECT_THAT(QualifiedId::Parse(R"(namespace#)"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // "#"
  EXPECT_THAT(QualifiedId::Parse(R"(#)"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(QualifiedIdTest, InvalidQualifiedIdWithInvalidEscape) {
  // "namespace\"
  // Add an additional '#' and use string_view trick to cover the index safe
  // check when skipping the last '\'.
  std::string str1 = R"(namespace\)"
                     R"(#)";
  EXPECT_THAT(
      QualifiedId::Parse(std::string_view(str1.data(), str1.length() - 1)),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // "names\pace#uri"
  EXPECT_THAT(QualifiedId::Parse(R"(names\pace#uri)"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // "names\\\pace#uri"
  EXPECT_THAT(QualifiedId::Parse(R"(names\\\pace#uri)"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // "namespace#uri\"
  // Add an additional '#' and use string_view trick to cover the index safe
  // check when skipping the last '\'.
  std::string str2 = R"(namespace#uri\)"
                     R"(#)";
  EXPECT_THAT(
      QualifiedId::Parse(std::string_view(str2.data(), str2.length() - 1)),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(QualifiedIdTest, InvalidQualifiedIdWithWrongNumberOfSeparators) {
  // ""
  EXPECT_THAT(QualifiedId::Parse(R"()"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // "namespaceuri"
  EXPECT_THAT(QualifiedId::Parse(R"(namespaceuri)"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // "namespace##uri"
  EXPECT_THAT(QualifiedId::Parse(R"(namespace##uri)"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // "namespace#uri#others"
  EXPECT_THAT(QualifiedId::Parse(R"(namespace#uri#others)"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // "namespace\#uri"
  EXPECT_THAT(QualifiedId::Parse(R"(namespace\#uri)"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // "namespace\\##uri"
  EXPECT_THAT(QualifiedId::Parse(R"(namespace\\##uri)"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // "namespace#uri\\#others"
  EXPECT_THAT(QualifiedId::Parse(R"(namespace#uri\\#)"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(QualifiedIdTest, InvalidQualifiedIdWithStringTerminator) {
  const char invalid_qualified_id1[] = "names\0pace#uri";
  EXPECT_THAT(QualifiedId::Parse(std::string_view(invalid_qualified_id1, 14)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  const char invalid_qualified_id2[] = "namespace#ur\0i";
  EXPECT_THAT(QualifiedId::Parse(std::string_view(invalid_qualified_id2, 14)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  const char invalid_qualified_id3[] = "\0namespace#uri";
  EXPECT_THAT(QualifiedId::Parse(std::string_view(invalid_qualified_id3, 14)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  const char invalid_qualified_id4[] = "namespace#uri\0";
  EXPECT_THAT(QualifiedId::Parse(std::string_view(invalid_qualified_id4, 14)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

}  // namespace

}  // namespace lib
}  // namespace icing
