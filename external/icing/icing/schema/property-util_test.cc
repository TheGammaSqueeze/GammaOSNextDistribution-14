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

#include "icing/schema/property-util.h"

#include <cstdint>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/proto/document.pb.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::IsEmpty;

static constexpr std::string_view kTypeTest = "Test";
static constexpr std::string_view kPropertySingleString = "singleString";
static constexpr std::string_view kPropertyRepeatedString = "repeatedString";
static constexpr std::string_view kPropertySingleInteger = "singleInteger";
static constexpr std::string_view kPropertyRepeatedInteger = "repeatedInteger";

static constexpr std::string_view kTypeNestedTest = "NestedTest";
static constexpr std::string_view kPropertyStr = "str";
static constexpr std::string_view kPropertyNestedDocument = "nestedDocument";

TEST(PropertyUtilTest, IsParentPropertyPath) {
  EXPECT_TRUE(property_util::IsParentPropertyPath("foo", "foo"));
  EXPECT_TRUE(property_util::IsParentPropertyPath("foo", "foo.bar"));
  EXPECT_TRUE(property_util::IsParentPropertyPath("foo", "foo.bar.foo"));
  EXPECT_TRUE(property_util::IsParentPropertyPath("foo", "foo.foo.bar"));
  EXPECT_TRUE(property_util::IsParentPropertyPath("foo.bar", "foo.bar.foo"));

  EXPECT_FALSE(property_util::IsParentPropertyPath("foo", "foofoo.bar"));
  EXPECT_FALSE(property_util::IsParentPropertyPath("foo.bar", "foo.foo.bar"));
  EXPECT_FALSE(property_util::IsParentPropertyPath("foo.bar", "foofoo.bar"));
  EXPECT_FALSE(property_util::IsParentPropertyPath("foo.bar.foo", "foo"));
  EXPECT_FALSE(property_util::IsParentPropertyPath("foo.bar.foo", "foo.bar"));
  EXPECT_FALSE(
      property_util::IsParentPropertyPath("foo.foo.bar", "foo.bar.foo"));
  EXPECT_FALSE(property_util::IsParentPropertyPath("foo", "foo#bar.foo"));
}

TEST(PropertyUtilTest, ExtractPropertyValuesTypeString) {
  PropertyProto property;
  property.mutable_string_values()->Add("Hello, world");
  property.mutable_string_values()->Add("Foo");
  property.mutable_string_values()->Add("Bar");

  EXPECT_THAT(property_util::ExtractPropertyValues<std::string>(property),
              IsOkAndHolds(ElementsAre("Hello, world", "Foo", "Bar")));

  EXPECT_THAT(property_util::ExtractPropertyValues<std::string_view>(property),
              IsOkAndHolds(ElementsAre("Hello, world", "Foo", "Bar")));
}

TEST(PropertyUtilTest, ExtractPropertyValuesTypeInteger) {
  PropertyProto property;
  property.mutable_int64_values()->Add(123);
  property.mutable_int64_values()->Add(-456);
  property.mutable_int64_values()->Add(0);

  EXPECT_THAT(property_util::ExtractPropertyValues<int64_t>(property),
              IsOkAndHolds(ElementsAre(123, -456, 0)));
}

TEST(PropertyUtilTest, ExtractPropertyValuesMismatchedType) {
  PropertyProto property;
  property.mutable_int64_values()->Add(123);
  property.mutable_int64_values()->Add(-456);
  property.mutable_int64_values()->Add(0);

  EXPECT_THAT(property_util::ExtractPropertyValues<std::string_view>(property),
              IsOkAndHolds(IsEmpty()));
}

TEST(PropertyUtilTest, ExtractPropertyValuesEmpty) {
  PropertyProto property;
  EXPECT_THAT(property_util::ExtractPropertyValues<std::string>(property),
              IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(property_util::ExtractPropertyValues<std::string_view>(property),
              IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(property_util::ExtractPropertyValues<int64_t>(property),
              IsOkAndHolds(IsEmpty()));
}

TEST(PropertyUtilTest, ExtractPropertyValuesTypeUnimplemented) {
  PropertyProto property;
  EXPECT_THAT(property_util::ExtractPropertyValues<int32_t>(property),
              StatusIs(libtextclassifier3::StatusCode::UNIMPLEMENTED));
}

TEST(PropertyUtilTest, ExtractPropertyValuesFromDocument) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "test/1")
          .SetSchema(std::string(kTypeTest))
          .AddStringProperty(std::string(kPropertySingleString), "single")
          .AddStringProperty(std::string(kPropertyRepeatedString), "repeated1",
                             "repeated2", "repeated3")
          .AddInt64Property(std::string(kPropertySingleInteger), 123)
          .AddInt64Property(std::string(kPropertyRepeatedInteger), 1, 2, 3)
          .Build();

  // Single string
  EXPECT_THAT(
      property_util::ExtractPropertyValuesFromDocument<std::string_view>(
          document, /*property_path=*/kPropertySingleString),
      IsOkAndHolds(ElementsAre("single")));
  // Repeated string
  EXPECT_THAT(
      property_util::ExtractPropertyValuesFromDocument<std::string_view>(
          document, /*property_path=*/kPropertyRepeatedString),
      IsOkAndHolds(ElementsAre("repeated1", "repeated2", "repeated3")));
  // Single integer
  EXPECT_THAT(property_util::ExtractPropertyValuesFromDocument<int64_t>(
                  document, /*property_path=*/kPropertySingleInteger),
              IsOkAndHolds(ElementsAre(123)));
  // Repeated integer
  EXPECT_THAT(property_util::ExtractPropertyValuesFromDocument<int64_t>(
                  document, /*property_path=*/kPropertyRepeatedInteger),
              IsOkAndHolds(ElementsAre(1, 2, 3)));
}

TEST(PropertyUtilTest, ExtractPropertyValuesFromDocumentNested) {
  DocumentProto nested_document =
      DocumentBuilder()
          .SetKey("icing", "nested/1")
          .SetSchema(std::string(kTypeNestedTest))
          .AddStringProperty(std::string(kPropertyStr), "a", "b", "c")
          .AddDocumentProperty(
              std::string(kPropertyNestedDocument),
              DocumentBuilder()
                  .SetSchema(std::string(kTypeTest))
                  .AddStringProperty(std::string(kPropertySingleString),
                                     "single1")
                  .AddStringProperty(std::string(kPropertyRepeatedString),
                                     "repeated1", "repeated2", "repeated3")
                  .AddInt64Property(std::string(kPropertySingleInteger), 123)
                  .AddInt64Property(std::string(kPropertyRepeatedInteger), 1, 2,
                                    3)
                  .Build(),
              DocumentBuilder()
                  .SetSchema(std::string(kTypeTest))
                  .AddStringProperty(std::string(kPropertySingleString),
                                     "single2")
                  .AddStringProperty(std::string(kPropertyRepeatedString),
                                     "repeated4", "repeated5", "repeated6")
                  .AddInt64Property(std::string(kPropertySingleInteger), 456)
                  .AddInt64Property(std::string(kPropertyRepeatedInteger), 4, 5,
                                    6)
                  .Build())
          .Build();

  // Since there are 2 nested documents, all of values at leaf will be returned.
  EXPECT_THAT(
      property_util::ExtractPropertyValuesFromDocument<std::string_view>(
          nested_document, /*property_path=*/"nestedDocument.singleString"),
      IsOkAndHolds(ElementsAre("single1", "single2")));
  EXPECT_THAT(
      property_util::ExtractPropertyValuesFromDocument<std::string_view>(
          nested_document, /*property_path=*/"nestedDocument.repeatedString"),
      IsOkAndHolds(ElementsAre("repeated1", "repeated2", "repeated3",
                               "repeated4", "repeated5", "repeated6")));
  EXPECT_THAT(
      property_util::ExtractPropertyValuesFromDocument<int64_t>(
          nested_document, /*property_path=*/"nestedDocument.singleInteger"),
      IsOkAndHolds(ElementsAre(123, 456)));
  EXPECT_THAT(
      property_util::ExtractPropertyValuesFromDocument<int64_t>(
          nested_document, /*property_path=*/"nestedDocument.repeatedInteger"),
      IsOkAndHolds(ElementsAre(1, 2, 3, 4, 5, 6)));

  // Test the property at first level
  EXPECT_THAT(
      property_util::ExtractPropertyValuesFromDocument<std::string_view>(
          nested_document, kPropertyStr),
      IsOkAndHolds(ElementsAre("a", "b", "c")));
}

TEST(PropertyUtilTest, ExtractPropertyValuesFromDocumentNonExistingPaths) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "test/1")
          .SetSchema(std::string(kTypeTest))
          .AddStringProperty(std::string(kPropertySingleString), "single")
          .AddStringProperty(std::string(kPropertyRepeatedString), "repeated1",
                             "repeated2", "repeated3")
          .AddInt64Property(std::string(kPropertySingleInteger), 123)
          .AddInt64Property(std::string(kPropertyRepeatedInteger), 1, 2, 3)
          .Build();
  EXPECT_THAT(
      property_util::ExtractPropertyValuesFromDocument<std::string_view>(
          document, /*property_path=*/"invalid"),
      IsOkAndHolds(IsEmpty()));

  DocumentProto nested_document =
      DocumentBuilder()
          .SetKey("icing", "nested/1")
          .SetSchema(std::string(kTypeNestedTest))
          .AddStringProperty(std::string(kPropertyStr), "a", "b", "c")
          .AddDocumentProperty(std::string(kPropertyNestedDocument),
                               DocumentProto(document), DocumentProto(document))
          .Build();
  EXPECT_THAT(
      property_util::ExtractPropertyValuesFromDocument<std::string_view>(
          nested_document, /*property_path=*/kPropertySingleString),
      IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(
      property_util::ExtractPropertyValuesFromDocument<std::string_view>(
          nested_document, /*property_path=*/"nestedDocument.invalid"),
      IsOkAndHolds(IsEmpty()));
}

TEST(PropertyUtilTest, ExtractPropertyValuesFromDocumentTypeUnimplemented) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "test/1")
          .SetSchema(std::string(kTypeTest))
          .AddStringProperty(std::string(kPropertySingleString), "single")
          .AddStringProperty(std::string(kPropertyRepeatedString), "repeated1",
                             "repeated2", "repeated3")
          .AddInt64Property(std::string(kPropertySingleInteger), 123)
          .AddInt64Property(std::string(kPropertyRepeatedInteger), 1, 2, 3)
          .Build();
  EXPECT_THAT(property_util::ExtractPropertyValuesFromDocument<int32_t>(
                  document, /*property_path=*/kPropertySingleString),
              StatusIs(libtextclassifier3::StatusCode::UNIMPLEMENTED));
}

}  // namespace

}  // namespace lib
}  // namespace icing
