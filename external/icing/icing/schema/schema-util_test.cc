// Copyright (C) 2019 Google LLC
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

#include "icing/schema/schema-util.h"

#include <initializer_list>
#include <string>
#include <string_view>
#include <unordered_set>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema-builder.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {
namespace {

using portable_equals_proto::EqualsProto;
using ::testing::Eq;
using ::testing::HasSubstr;
using ::testing::IsEmpty;
using ::testing::IsFalse;
using ::testing::IsTrue;
using ::testing::Pair;
using ::testing::Pointee;
using ::testing::SizeIs;
using ::testing::UnorderedElementsAre;

// Properties/fields in a schema type
constexpr char kEmailType[] = "EmailMessage";
constexpr char kMessageType[] = "Text";
constexpr char kPersonType[] = "Person";

class SchemaUtilTest : public ::testing::TestWithParam<bool> {};

TEST_P(SchemaUtilTest, DependentGraphAlphabeticalOrder) {
  // Create a schema with the following dependent relation:
  //         C
  //       /   \
  // A - B       E - F
  //       \   /
  //         D
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_e =
      SchemaTypeConfigBuilder()
          .SetType("E")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("f")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("F", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_f =
      SchemaTypeConfigBuilder()
          .SetType("F")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();

  // Provide these in alphabetical order: A, B, C, D, E, F
  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .AddType(type_e)
                           .AddType(type_f)
                           .Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, testing::SizeIs(5));
  EXPECT_THAT(
      d_map["F"],
      UnorderedElementsAre(Pair("A", IsEmpty()), Pair("B", IsEmpty()),
                           Pair("C", IsEmpty()), Pair("D", IsEmpty()),
                           Pair("E", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_e.properties(0)))))));
  EXPECT_THAT(d_map["E"],
              UnorderedElementsAre(
                  Pair("A", IsEmpty()), Pair("B", IsEmpty()),
                  Pair("C", UnorderedElementsAre(
                                Pointee(EqualsProto(type_c.properties(0))))),
                  Pair("D", UnorderedElementsAre(
                                Pointee(EqualsProto(type_d.properties(0)))))));
  EXPECT_THAT(
      d_map["D"],
      UnorderedElementsAre(Pair("A", IsEmpty()),
                           Pair("B", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_b.properties(1)))))));
  EXPECT_THAT(
      d_map["C"],
      UnorderedElementsAre(Pair("A", IsEmpty()),
                           Pair("B", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_b.properties(0)))))));
  EXPECT_THAT(d_map["B"], UnorderedElementsAre(Pair(
                              "A", UnorderedElementsAre(Pointee(
                                       EqualsProto(type_a.properties(0)))))));
}

TEST_P(SchemaUtilTest, DependentGraphReverseAlphabeticalOrder) {
  // Create a schema with the following dependent relation:
  //         C
  //       /   \
  // A - B       E - F
  //       \   /
  //         D
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_e =
      SchemaTypeConfigBuilder()
          .SetType("E")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("f")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("F", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_f =
      SchemaTypeConfigBuilder()
          .SetType("F")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();

  // Provide these in reverse alphabetical order:
  //   F, E, D, C, B, A
  SchemaProto schema = SchemaBuilder()
                           .AddType(type_f)
                           .AddType(type_e)
                           .AddType(type_d)
                           .AddType(type_c)
                           .AddType(type_b)
                           .AddType(type_a)
                           .Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, testing::SizeIs(5));
  EXPECT_THAT(
      d_map["F"],
      UnorderedElementsAre(Pair("A", IsEmpty()), Pair("B", IsEmpty()),
                           Pair("C", IsEmpty()), Pair("D", IsEmpty()),
                           Pair("E", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_e.properties(0)))))));
  EXPECT_THAT(d_map["E"],
              UnorderedElementsAre(
                  Pair("A", IsEmpty()), Pair("B", IsEmpty()),
                  Pair("C", UnorderedElementsAre(
                                Pointee(EqualsProto(type_c.properties(0))))),
                  Pair("D", UnorderedElementsAre(
                                Pointee(EqualsProto(type_d.properties(0)))))));
  EXPECT_THAT(
      d_map["D"],
      UnorderedElementsAre(Pair("A", IsEmpty()),
                           Pair("B", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_b.properties(1)))))));
  EXPECT_THAT(
      d_map["C"],
      UnorderedElementsAre(Pair("A", IsEmpty()),
                           Pair("B", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_b.properties(0)))))));
  EXPECT_THAT(d_map["B"], UnorderedElementsAre(Pair(
                              "A", UnorderedElementsAre(Pointee(
                                       EqualsProto(type_a.properties(0)))))));
}

TEST_P(SchemaUtilTest, DependentGraphMixedOrder) {
  // Create a schema with the following dependent relation:
  //         C
  //       /   \
  // A - B       E - F
  //       \   /
  //         D
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_e =
      SchemaTypeConfigBuilder()
          .SetType("E")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("f")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("F", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_f =
      SchemaTypeConfigBuilder()
          .SetType("F")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();

  // Provide these in a random order: C, E, F, A, B, D
  SchemaProto schema = SchemaBuilder()
                           .AddType(type_c)
                           .AddType(type_e)
                           .AddType(type_f)
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_d)
                           .Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, testing::SizeIs(5));
  EXPECT_THAT(
      d_map["F"],
      UnorderedElementsAre(Pair("A", IsEmpty()), Pair("B", IsEmpty()),
                           Pair("C", IsEmpty()), Pair("D", IsEmpty()),
                           Pair("E", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_e.properties(0)))))));
  EXPECT_THAT(d_map["E"],
              UnorderedElementsAre(
                  Pair("A", IsEmpty()), Pair("B", IsEmpty()),
                  Pair("C", UnorderedElementsAre(
                                Pointee(EqualsProto(type_c.properties(0))))),
                  Pair("D", UnorderedElementsAre(
                                Pointee(EqualsProto(type_d.properties(0)))))));
  EXPECT_THAT(
      d_map["D"],
      UnorderedElementsAre(Pair("A", IsEmpty()),
                           Pair("B", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_b.properties(1)))))));
  EXPECT_THAT(
      d_map["C"],
      UnorderedElementsAre(Pair("A", IsEmpty()),
                           Pair("B", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_b.properties(0)))))));
  EXPECT_THAT(d_map["B"], UnorderedElementsAre(Pair(
                              "A", UnorderedElementsAre(Pointee(
                                       EqualsProto(type_a.properties(0)))))));
}

TEST_P(SchemaUtilTest, TopLevelCycleIndexableTrueInvalid) {
  // Create a schema with the following nested-type relation:
  // A - B - B - B - B.... where all edges declare index_nested_properties=true
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, TopLevelCycleIndexableFalseNotJoinableOK) {
  if (GetParam() != true) {
    GTEST_SKIP() << "This is an invalid cycle if circular schema definitions "
                    "are not allowed.";
  }

  // Create a schema with the following nested-type relation and
  // index_nested_properties definition:
  // A -(true)-> B -(false)-> B -(false)-> B....
  // Edge B -(false)-> B breaks the invalid cycle, so this is allowed.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();
  // Assert Validate status is OK and check dependent map
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(1));
  EXPECT_THAT(d_map["B"],
              UnorderedElementsAre(
                  Pair("A", UnorderedElementsAre(
                                Pointee(EqualsProto(type_a.properties(0))))),
                  Pair("B", UnorderedElementsAre(
                                Pointee(EqualsProto(type_b.properties(0)))))));
}

TEST_P(SchemaUtilTest, MultiLevelCycleIndexableTrueInvalid) {
  // Create a schema with the following dependent relation:
  // A - B - C - A - B - C - A ...
  // where all edges declare index_nested_properties=true
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs((libtextclassifier3::StatusCode::INVALID_ARGUMENT),
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, MultiLevelCycleIndexableFalseNotJoinableOK) {
  if (GetParam() != true) {
    GTEST_SKIP() << "This is an invalid cycle if circular schema definitions "
                    "are not allowed.";
  }

  // Create a schema with the following nested-type relation:
  // A -(true)-> B -(false)-> C -(true)-> A -(true)-> B -(false)-> C ...
  // B -(false)-> C breaking the infinite cycle.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::OK));
}

TEST_P(SchemaUtilTest, MultiLevelCycleDependentMapOk) {
  if (GetParam() != true) {
    GTEST_SKIP() << "This is an invalid cycle if circular schema definitions "
                    "are not allowed.";
  }

  // Create a schema with the following nested-type dependent relation:
  // A -(false)-> B -(false)-> C -(false)-> A --> B --> C ...
  //  i.e. A is a property of B
  //       B is a property of C
  //       C is a property of A
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  // Assert Validate status is OK and check dependent map
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(3));
  EXPECT_THAT(
      d_map["A"],
      UnorderedElementsAre(Pair("A", IsEmpty()),
                           Pair("B", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_b.properties(0))))),
                           Pair("C", IsEmpty())));
  EXPECT_THAT(
      d_map["B"],
      UnorderedElementsAre(Pair("A", IsEmpty()), Pair("B", IsEmpty()),
                           Pair("C", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_c.properties(0)))))));
  EXPECT_THAT(
      d_map["C"],
      UnorderedElementsAre(Pair("A", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_a.properties(0))))),
                           Pair("B", IsEmpty()), Pair("C", IsEmpty())));
}

TEST_P(SchemaUtilTest, NestedCycleIndexableTrueInvalid) {
  // Create a schema with the following dependent relation:
  // A -(false)-> B <-(true)-> C -(false)-> D.
  // B <-(true)-> C creates an invalid cycle.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE))
          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, NestedCycleIndexableFalseNotJoinableOK) {
  if (GetParam() != true) {
    GTEST_SKIP() << "This is an invalid cycle if circular schema definitions "
                    "are not allowed.";
  }

  // Create a schema with the following nested-type relation:
  // A -(true)-> B -(true)-> C -(false)-> B -(true)-> D.
  //  C -(false)-> B breaks the invalid cycle in B - C - B.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE))
          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .Build();
  // Assert Validate status is OK and check dependent map
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(3));
  EXPECT_THAT(d_map["B"],
              UnorderedElementsAre(
                  Pair("A", UnorderedElementsAre(
                                Pointee(EqualsProto(type_a.properties(0))))),
                  Pair("B", IsEmpty()),
                  Pair("C", UnorderedElementsAre(
                                Pointee(EqualsProto(type_c.properties(0)))))));
  EXPECT_THAT(
      d_map["C"],
      UnorderedElementsAre(Pair("A", IsEmpty()),
                           Pair("B", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_b.properties(0))))),
                           Pair("C", IsEmpty())));
  EXPECT_THAT(d_map["D"],
              UnorderedElementsAre(
                  Pair("A", IsEmpty()),
                  Pair("B", UnorderedElementsAre(
                                Pointee(EqualsProto(type_b.properties(1))))),
                  Pair("C", UnorderedElementsAre(
                                Pointee(EqualsProto(type_c.properties(1)))))));
}

TEST_P(SchemaUtilTest, MultiplePathsAnyPathContainsCycleIsInvalid) {
  // Create a schema with the following nested-type relation:
  // C -(false)-> B -(true)-> A
  //               ^         /
  //          (true)\       /(true)
  //                 \     v
  //                    D
  //  There is a cycle in B-A-D-B... so this is not allowed
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_d)
                           .AddType(type_c)
                           .AddType(type_b)
                           .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, MultipleCycles_anyCycleIndexableTrueInvalid) {
  // Create a schema with the following nested-type dependent relation:
  // Note that the arrows in this graph shows the direction of the dependent
  // relation, rather than nested-type relations.
  //    A -(F)-> B
  //    ^  \     |
  // (T)| (T)\   |(T)
  //    |      v v
  //    D <-(T)- C
  // There are two cycles: A-B-C-D and A-C-D. The first cycle is allowed because
  // A-B has nested-indexable=false, but A-C-D
  //
  // Schema nested-type property relation graph:
  // A <-- B
  // | ^   ^
  // v   \ |
  // D --> C
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(type_d)
                           .AddType(type_c)
                           .AddType(type_b)
                           .AddType(type_a)
                           .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, CycleWithSameTypedProps_allPropsIndexableFalseIsOK) {
  if (GetParam() != true) {
    GTEST_SKIP() << "This is an invalid cycle if circular schema definitions "
                    "are not allowed.";
  }

  // Create a schema with the following nested-type relation and
  // index_nested_properties definition:
  // A <-(true)- B <-(false)- A -(false)-> B -(true)-> A
  // A has 2 properties with type B. A - B breaks the invalid cycle only when
  // both properties declare index_nested_properties=false.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b1")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b2")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("A")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();
  // Assert Validate status is OK and check dependent map
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(2));
  EXPECT_THAT(
      d_map["A"],
      UnorderedElementsAre(Pair("A", IsEmpty()),
                           Pair("B", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_b.properties(0)))))));
  EXPECT_THAT(d_map["B"],
              UnorderedElementsAre(
                  Pair("A", UnorderedElementsAre(
                                Pointee(EqualsProto(type_a.properties(0))),
                                Pointee(EqualsProto(type_a.properties(1))))),
                  Pair("B", IsEmpty())));
}

TEST_P(SchemaUtilTest, CycleWithSameTypedProps_anyPropIndexableTrueIsInvalid) {
  // Create a schema with the following nested-type relation and
  // index_nested_properties definition:
  // A <-(true)- B <-(true)- A -(false)-> B -(true)-> A
  // A has 2 properties with type B. Prop 'b2' declares
  // index_nested_properties=true, so there is an invalid cycle.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b1")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b2")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("A")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, CycleWithJoinablePropertyNotAllowed) {
  // Create a schema with the following dependent relation:
  //                A
  //              /  ^
  //             v    \
  // (joinable) B ---> C
  // B also has a string property that is joinable on QUALIFIED_ID
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("joinableProp")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeJoinableString(JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/false))
          .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, NonNestedJoinablePropOutsideCycleOK) {
  if (GetParam() != true) {
    GTEST_SKIP() << "This is an invalid cycle if circular schema definitions "
                    "are not allowed.";
  }

  // Create a schema with the following dependent relation:
  // A -(false)-> B <-(false)-> C...
  // A has a string property that is joinable on QUALIFIED_ID, but the cycle is
  // B-C-B, and none of B or C depends on A, so this is fine.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("joinableProp")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeJoinableString(JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  // Assert Validate status is OK and check dependent map
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(2));
  EXPECT_THAT(d_map["B"],
              UnorderedElementsAre(
                  Pair("A", UnorderedElementsAre(
                                Pointee(EqualsProto(type_a.properties(0))))),
                  Pair("B", IsEmpty()),
                  Pair("C", UnorderedElementsAre(
                                Pointee(EqualsProto(type_c.properties(0)))))));
  EXPECT_THAT(
      d_map["C"],
      UnorderedElementsAre(Pair("A", IsEmpty()),
                           Pair("B", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_b.properties(0))))),
                           Pair("C", IsEmpty())));
}

TEST_P(SchemaUtilTest, DirectNestedJoinablePropOutsideCycleNotAllowed) {
  // Create a schema with the following dependent relation:
  //       A
  //     /  ^
  //    v    \
  //   B ---> C ---> D(joinable)
  // All edges have index_nested_properties=false and only D has a joinable
  // property. The cycle A-B-C... is not allowed since there is a type in the
  // cycle (C) which has a direct nested-type (D) with a joinable property.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("joinableProp")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeJoinableString(JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, TransitiveNestedJoinablePropOutsideCycleNotAllowed) {
  // Create a schema with the following dependent relation:
  //       A
  //     /  ^
  //    v    \
  //   B ---> C ---> D ---> E (joinable)
  // All edges have index_nested_properties=false and only D has a joinable
  // property. The cycle A-B-C... is not allowed since there is a type in the
  // cycle (C) which has a transitive nested-type (E) with a joinable property.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_e =
      SchemaTypeConfigBuilder()
          .SetType("E")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("joinableProp")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeJoinableString(JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .AddType(type_e)
                           .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest,
       NestedJoinablePropOutsideCycleNotAllowed_reverseIterationOrder) {
  // Create a schema with the following dependent relation:
  //       E
  //     /  ^
  //    v    \
  //   D ---> C ---> B ---> A (joinable)
  // All edges have index_nested_properties=false and only D has a joinable
  // property. The cycle A-B-C... is not allowed since there is a type in the
  // cycle (C) which has a transitive nested-type (E) with a joinable property.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("joinableProp")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeJoinableString(JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_e =
      SchemaTypeConfigBuilder()
          .SetType("E")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/false))
          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .AddType(type_e)
                           .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, ComplexCycleWithJoinablePropertyNotAllowed) {
  // Create a schema with the following dependent relation:
  //       A
  //     /   ^
  //    v     \
  //    B ---> E
  //   /  \    ^
  //  v    v    \
  //  C    D --> F
  //
  // Cycles: A-B-E-A, A-B-D-F-E-A.
  // All edges have index_nested_properties=false, but D has a joinable property
  // so the second cycle is not allowed.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("joinableProp")
                  .SetDataTypeJoinableString(JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("f")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("F", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("joinableProp")
                  .SetDataTypeJoinableString(JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaTypeConfigProto type_e =
      SchemaTypeConfigBuilder()
          .SetType("E")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_f =
      SchemaTypeConfigBuilder()
          .SetType("F")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/false))
          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .AddType(type_e)
                           .AddType(type_f)
                           .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, ComplexCycleWithIndexableTrueNotAllowed) {
  // Create a schema with the following dependent relation:
  //       A
  //     /   ^
  //    v     \
  //    B ---> E
  //   /  \    ^
  //  v    v    \
  //  C    D --> F
  //
  // Cycles: A-B-E-A, A-B-D-F-E-A.
  // B->E has index_nested_properties=false, so the first cycle is allowed.
  // All edges on the second cycle are nested_indexable, so the second cycle is
  // not allowed
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("joinableProp")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeJoinableString(JOINABLE_VALUE_TYPE_QUALIFIED_ID))
          .Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("f")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("F", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_e =
      SchemaTypeConfigBuilder()
          .SetType("E")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_f =
      SchemaTypeConfigBuilder()
          .SetType("F")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("e")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("E", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .AddType(type_e)
                           .AddType(type_f)
                           .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, InheritanceAndNestedTypeRelations_noCycle) {
  if (GetParam() != true) {
    GTEST_SKIP() << "This is an invalid cycle if circular schema definitions "
                    "are not allowed.";
  }

  // Create a schema with the following relations:
  // index_nested_properties definition:
  // 1. Nested-type relations:
  //    A -(true)-> B -(true)-> C
  //         (false)|   (false)/ \(false)
  //                B         B   C
  //    The properties in the second row are required for B and C to be
  //    compatible with their parents. index_nested_properties must be false in
  //    these properties so that no invalid cycle can be formed because of these
  //    self reference.
  //
  // 2. Inheritance relations:
  //    C -> B -> A (A is a parent of B, which is a parent of C)
  //
  // These two relations are separate and do not affect each other. In this
  // case there is no cycle.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddParentType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddParentType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE))
          .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(3));
  // Both A-B and A-C are inheritance relations.
  EXPECT_THAT(d_map["A"],
              UnorderedElementsAre(Pair("B", IsEmpty()), Pair("C", IsEmpty())));
  // B-A and B-B are nested-type relations, B-C is both a nested-type and an
  // inheritance relation.
  EXPECT_THAT(d_map["B"],
              UnorderedElementsAre(
                  Pair("A", UnorderedElementsAre(
                                Pointee(EqualsProto(type_a.properties(0))))),
                  Pair("B", UnorderedElementsAre(
                                Pointee(EqualsProto(type_b.properties(0))))),
                  Pair("C", UnorderedElementsAre(
                                Pointee(EqualsProto(type_c.properties(0)))))));
  // C-C, C-B and C-A are all nested-type relations.
  EXPECT_THAT(d_map["C"],
              UnorderedElementsAre(
                  Pair("B", UnorderedElementsAre(
                                Pointee(EqualsProto(type_b.properties(1))))),
                  Pair("C", UnorderedElementsAre(
                                Pointee(EqualsProto(type_c.properties(1))))),
                  Pair("A", IsEmpty())));

  ICING_ASSERT_OK_AND_ASSIGN(
      SchemaUtil::InheritanceMap i_map,
      SchemaUtil::BuildTransitiveInheritanceGraph(schema));
  EXPECT_THAT(i_map, SizeIs(2));
  EXPECT_THAT(i_map["A"],
              UnorderedElementsAre(Pair("B", IsTrue()), Pair("C", IsFalse())));
  EXPECT_THAT(i_map["B"], UnorderedElementsAre(Pair("C", IsTrue())));
}

TEST_P(SchemaUtilTest, InheritanceAndNestedTypeRelations_nestedTypeCycle) {
  // Create a schema with the following relations:
  // index_nested_properties definition:
  // 1. Nested-type relations:
  //    A -(true)-> B -(true)-> C
  //          (true)|   (false)/ \(false)
  //                B         B   C
  //
  // 2. Inheritance relations:
  //    C -> B -> A (A is a parent of B, which is a parent of C)
  //
  // These two relations are separate and do not affect each other, but there is
  // a cycle in nested-type relations: B - B
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddParentType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddParentType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE))
          .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, InheritanceAndNestedTypeRelations_inheritanceCycle) {
  // Create a schema with the following relations:
  // index_nested_properties definition:
  // 1. Nested-type relations:
  //    A -(true)-> B -(true)-> C
  //         (false)|   (false)/ \(false)
  //                B         B   C
  //
  // 2. Inheritance relations:
  //    C -> B -> A -> B (A is a parent of B, which is a parent of C and A)
  //
  // These two relations are separate and do not affect each other, but there is
  // a cycle in inheritance relation: B - A - B
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddParentType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddParentType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddParentType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE))
          .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("inherits from itself")));
}

TEST_P(SchemaUtilTest, NonExistentType) {
  // Create a schema with the following dependent relation:
  // A - B - C - X (does not exist)
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("c")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("C", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("x")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("X", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, SingleTypeIsBothDirectAndIndirectDependent) {
  // Create a schema with the following dependent relation, all of which are via
  // nested document. In this case, C is both a direct dependent and an indirect
  // dependent of A.
  //  A
  //  | \
  //  |  B
  //  | /
  //  C
  SchemaTypeConfigProto type_a = SchemaTypeConfigBuilder().SetType("A").Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(2));
  EXPECT_THAT(d_map["A"],
              UnorderedElementsAre(
                  Pair("B", UnorderedElementsAre(
                                Pointee(EqualsProto(type_b.properties(0))))),
                  Pair("C", UnorderedElementsAre(
                                Pointee(EqualsProto(type_c.properties(0)))))));
  EXPECT_THAT(d_map["B"], UnorderedElementsAre(Pair(
                              "C", UnorderedElementsAre(Pointee(
                                       EqualsProto(type_c.properties(1)))))));

  ICING_ASSERT_OK_AND_ASSIGN(
      SchemaUtil::InheritanceMap i_map,
      SchemaUtil::BuildTransitiveInheritanceGraph(schema));
  EXPECT_THAT(i_map, IsEmpty());
}

TEST_P(SchemaUtilTest, SimpleInheritance) {
  // Create a schema with the following inheritance relation:
  // A <- B
  SchemaTypeConfigProto type_a = SchemaTypeConfigBuilder().SetType("A").Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();

  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(1));
  EXPECT_THAT(d_map["A"], UnorderedElementsAre(Pair("B", IsEmpty())));

  ICING_ASSERT_OK_AND_ASSIGN(
      SchemaUtil::InheritanceMap i_map,
      SchemaUtil::BuildTransitiveInheritanceGraph(schema));
  EXPECT_THAT(i_map, SizeIs(1));
  EXPECT_THAT(i_map["A"], UnorderedElementsAre(Pair("B", IsTrue())));
}

TEST_P(SchemaUtilTest, SingleInheritanceTypeIsBothDirectAndIndirectChild) {
  // Create a schema with the following inheritance relation. In this case, C is
  // both a direct and an indirect child of A.
  //  A
  //  | \
  //  |  B
  //  | /
  //  C
  SchemaTypeConfigProto type_a = SchemaTypeConfigBuilder().SetType("A").Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();
  SchemaTypeConfigProto type_c = SchemaTypeConfigBuilder()
                                     .SetType("C")
                                     .AddParentType("A")
                                     .AddParentType("B")
                                     .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(2));
  EXPECT_THAT(d_map["A"],
              UnorderedElementsAre(Pair("B", IsEmpty()), Pair("C", IsEmpty())));
  EXPECT_THAT(d_map["B"], UnorderedElementsAre(Pair("C", IsEmpty())));

  ICING_ASSERT_OK_AND_ASSIGN(
      SchemaUtil::InheritanceMap i_map,
      SchemaUtil::BuildTransitiveInheritanceGraph(schema));
  EXPECT_THAT(i_map, SizeIs(2));
  EXPECT_THAT(i_map["A"],
              UnorderedElementsAre(Pair("B", IsTrue()), Pair("C", IsTrue())));
  EXPECT_THAT(i_map["B"], UnorderedElementsAre(Pair("C", IsTrue())));
}

TEST_P(SchemaUtilTest, ComplexInheritance) {
  // Create a schema with the following inheritance relation:
  //       A
  //     /   \
  //    B     E
  //   /  \
  //  C    D
  //       |
  //       F
  SchemaTypeConfigProto type_a = SchemaTypeConfigBuilder().SetType("A").Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder().SetType("C").AddParentType("B").Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder().SetType("D").AddParentType("B").Build();
  SchemaTypeConfigProto type_e =
      SchemaTypeConfigBuilder().SetType("E").AddParentType("A").Build();
  SchemaTypeConfigProto type_f =
      SchemaTypeConfigBuilder().SetType("F").AddParentType("D").Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .AddType(type_e)
                           .AddType(type_f)
                           .Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(3));
  EXPECT_THAT(d_map["A"],
              UnorderedElementsAre(Pair("B", IsEmpty()), Pair("C", IsEmpty()),
                                   Pair("D", IsEmpty()), Pair("E", IsEmpty()),
                                   Pair("F", IsEmpty())));
  EXPECT_THAT(d_map["B"],
              UnorderedElementsAre(Pair("C", IsEmpty()), Pair("D", IsEmpty()),
                                   Pair("F", IsEmpty())));
  EXPECT_THAT(d_map["D"], UnorderedElementsAre(Pair("F", IsEmpty())));

  ICING_ASSERT_OK_AND_ASSIGN(
      SchemaUtil::InheritanceMap i_map,
      SchemaUtil::BuildTransitiveInheritanceGraph(schema));
  EXPECT_THAT(i_map, SizeIs(3));
  EXPECT_THAT(i_map["A"],
              UnorderedElementsAre(Pair("B", IsTrue()), Pair("C", IsFalse()),
                                   Pair("D", IsFalse()), Pair("E", IsTrue()),
                                   Pair("F", IsFalse())));
  EXPECT_THAT(i_map["B"],
              UnorderedElementsAre(Pair("C", IsTrue()), Pair("D", IsTrue()),
                                   Pair("F", IsFalse())));
  EXPECT_THAT(i_map["D"], UnorderedElementsAre(Pair("F", IsTrue())));
}

TEST_P(SchemaUtilTest, InheritanceCycle) {
  // Create a schema with the following inheritance relation:
  // C <- A <- B <- C
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder().SetType("A").AddParentType("C").Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder().SetType("C").AddParentType("B").Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, SelfInheritance) {
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder().SetType("A").AddParentType("A").Build();

  SchemaProto schema = SchemaBuilder().AddType(type_a).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, NonExistentParentType) {
  // Create a schema with the following inheritance relation:
  // (does not exist) X <- A <- B <- C
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder().SetType("A").AddParentType("X").Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder().SetType("C").AddParentType("B").Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, SimpleInheritanceWithNestedType) {
  // Create a schema with the following dependent relation:
  // A - B (via inheritance)
  // B - C (via nested document)
  SchemaTypeConfigProto type_a = SchemaTypeConfigBuilder().SetType("A").Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder()
          .SetType("C")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema =
      SchemaBuilder().AddType(type_a).AddType(type_b).AddType(type_c).Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(2));
  // Nested-type dependency and inheritance dependencies are not transitive.
  EXPECT_THAT(d_map["A"], UnorderedElementsAre(Pair("B", IsEmpty())));
  EXPECT_THAT(d_map["B"], UnorderedElementsAre(Pair(
                              "C", UnorderedElementsAre(Pointee(
                                       EqualsProto(type_c.properties(0)))))));

  ICING_ASSERT_OK_AND_ASSIGN(
      SchemaUtil::InheritanceMap i_map,
      SchemaUtil::BuildTransitiveInheritanceGraph(schema));
  EXPECT_THAT(i_map, SizeIs(1));
  EXPECT_THAT(i_map["A"], UnorderedElementsAre(Pair("B", IsTrue())));
}

TEST_P(SchemaUtilTest, ComplexInheritanceWithNestedType) {
  // Create a schema with the following dependent relation:
  //       A
  //     /   \
  //    B     E
  //   /  \
  //  C    D
  //       |
  //       F
  // Approach:
  //   B extends A
  //   C extends B
  //   D has a nested document of type B
  //   E has a nested document of type A
  //   F has a nested document of type D
  SchemaTypeConfigProto type_a = SchemaTypeConfigBuilder().SetType("A").Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder().SetType("C").AddParentType("B").Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder()
          .SetType("D")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_e =
      SchemaTypeConfigBuilder()
          .SetType("E")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("A", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_f =
      SchemaTypeConfigBuilder()
          .SetType("F")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("d")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("D", /*index_nested_properties=*/true))
          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .AddType(type_e)
                           .AddType(type_f)
                           .Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(3));
  EXPECT_THAT(
      d_map["A"],
      UnorderedElementsAre(Pair("B", IsEmpty()), Pair("C", IsEmpty()),
                           Pair("E", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_e.properties(0)))))));
  EXPECT_THAT(
      d_map["B"],
      UnorderedElementsAre(Pair("C", IsEmpty()),
                           Pair("D", UnorderedElementsAre(Pointee(
                                         EqualsProto(type_d.properties(0))))),
                           Pair("F", IsEmpty())));
  EXPECT_THAT(d_map["D"], UnorderedElementsAre(Pair(
                              "F", UnorderedElementsAre(Pointee(
                                       EqualsProto(type_f.properties(0)))))));

  ICING_ASSERT_OK_AND_ASSIGN(
      SchemaUtil::InheritanceMap i_map,
      SchemaUtil::BuildTransitiveInheritanceGraph(schema));
  EXPECT_THAT(i_map, SizeIs(2));
  EXPECT_THAT(i_map["A"],
              UnorderedElementsAre(Pair("B", IsTrue()), Pair("C", IsFalse())));
  EXPECT_THAT(i_map["B"], UnorderedElementsAre(Pair("C", IsTrue())));
}

TEST_P(SchemaUtilTest, InheritanceWithNestedTypeCycle) {
  // Create a schema that A and B depend on each other, in the sense that B
  // extends A but A has a nested document of type B.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();

  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, EmptySchemaProtoIsValid) {
  SchemaProto schema;
  ICING_ASSERT_OK(SchemaUtil::Validate(schema, GetParam()));
}

TEST_P(SchemaUtilTest, Valid_Nested) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("sender")
                                        .SetDataTypeDocument(
                                            kPersonType,
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("name")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  ICING_ASSERT_OK(SchemaUtil::Validate(schema, GetParam()));
}

TEST_P(SchemaUtilTest, ClearedPropertyConfigsIsValid) {
  // No property fields is technically ok, but probably not realistic.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType(kEmailType))
          .Build();
  ICING_ASSERT_OK(SchemaUtil::Validate(schema, GetParam()));
}

TEST_P(SchemaUtilTest, ClearedSchemaTypeIsInvalid) {
  SchemaProto schema =
      SchemaBuilder().AddType(SchemaTypeConfigBuilder()).Build();
  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, EmptySchemaTypeIsInvalid) {
  SchemaProto schema =
      SchemaBuilder().AddType(SchemaTypeConfigBuilder().SetType("")).Build();

  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, AnySchemaTypeOk) {
  SchemaProto schema = SchemaBuilder()
                           .AddType(SchemaTypeConfigBuilder().SetType(
                               "abc123!@#$%^&*()_-+=[{]}|\\;:'\",<.>?你好"))
                           .Build();

  ICING_ASSERT_OK(SchemaUtil::Validate(schema, GetParam()));
}

TEST_P(SchemaUtilTest, ClearedPropertyNameIsInvalid) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("foo")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();
  schema.mutable_types(0)->mutable_properties(0)->clear_property_name();
  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, EmptyPropertyNameIsInvalid) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, NonAlphanumericPropertyNameIsInvalid) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("a_b")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, AlphanumericPropertyNameOk) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("abc123")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  ICING_ASSERT_OK(SchemaUtil::Validate(schema, GetParam()));
}

TEST_P(SchemaUtilTest, DuplicatePropertyNameIsInvalid) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("DuplicatedProperty")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("DuplicatedProperty")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();
  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::ALREADY_EXISTS));
}

TEST_P(SchemaUtilTest, ClearedDataTypeIsInvalid) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewProperty")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();
  schema.mutable_types(0)->mutable_properties(0)->clear_data_type();
  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, UnknownDataTypeIsInvalid) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType(kEmailType)
                  .AddProperty(
                      PropertyConfigBuilder()
                          .SetName("NewProperty")
                          .SetDataType(PropertyConfigProto::DataType::UNKNOWN)
                          .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();
  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, ClearedCardinalityIsInvalid) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewProperty")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();
  schema.mutable_types(0)->mutable_properties(0)->clear_cardinality();
  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, UnknownCardinalityIsInvalid) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewProperty")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_UNKNOWN)))
          .Build();
  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, ClearedPropertySchemaTypeIsInvalid) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewProperty")
                                        .SetDataType(TYPE_DOCUMENT)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, Invalid_EmptyPropertySchemaType) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewProperty")
                                        .SetDataTypeDocument(
                                            /*schema_type=*/"",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, NoMatchingSchemaTypeIsInvalid) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewProperty")
                                        .SetDataTypeDocument(
                                            /*schema_type=*/"NewSchemaType",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  ASSERT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Undefined 'schema_type'")));
}

TEST_P(SchemaUtilTest, NewOptionalPropertyIsCompatible) {
  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  // Configure new schema with an optional field, not considered incompatible
  // since it's fine if old data doesn't have this optional field
  SchemaProto new_schema_with_optional =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewOptional")
                                        .SetDataType(TYPE_DOUBLE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_changed_fully_compatible.insert(kEmailType);
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(
                  old_schema, new_schema_with_optional, no_dependents_map),
              Eq(schema_delta));
}

TEST_P(SchemaUtilTest, NewRequiredPropertyIsIncompatible) {
  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  // Configure new schema with a required field, considered incompatible since
  // old data won't have this required field
  SchemaProto new_schema_with_required =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewRequired")
                                        .SetDataType(TYPE_DOUBLE)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_incompatible.emplace(kEmailType);
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(
                  old_schema, new_schema_with_required, no_dependents_map),
              Eq(schema_delta));
}

TEST_P(SchemaUtilTest, NewSchemaMissingPropertyIsIncompatible) {
  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("OldOptional")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema, new schema needs to at least have all the
  // previously defined properties
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_incompatible.emplace(kEmailType);
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                                    no_dependents_map),
              Eq(schema_delta));
}

TEST_P(SchemaUtilTest, CompatibilityOfDifferentCardinalityOk) {
  // Configure less restrictive schema based on cardinality
  SchemaProto less_restrictive_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Configure more restrictive schema based on cardinality
  SchemaProto more_restrictive_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // We can't have a new schema be more restrictive, REPEATED->OPTIONAL
  SchemaUtil::SchemaDelta incompatible_schema_delta;
  incompatible_schema_delta.schema_types_incompatible.emplace(kEmailType);
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(
                  /*old_schema=*/less_restrictive_schema,
                  /*new_schema=*/more_restrictive_schema, no_dependents_map),
              Eq(incompatible_schema_delta));

  // We can have the new schema be less restrictive, OPTIONAL->REPEATED;
  SchemaUtil::SchemaDelta compatible_schema_delta;
  compatible_schema_delta.schema_types_changed_fully_compatible.insert(
      kEmailType);
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(
                  /*old_schema=*/more_restrictive_schema,
                  /*new_schema=*/less_restrictive_schema, no_dependents_map),
              Eq(compatible_schema_delta));
}

TEST_P(SchemaUtilTest, DifferentDataTypeIsIncompatible) {
  // Configure old schema, with an int64_t property
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Configure new schema, with a double property
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataType(TYPE_DOUBLE)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_incompatible.emplace(kEmailType);
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                                    no_dependents_map),
              Eq(schema_delta));
}

TEST_P(SchemaUtilTest, DifferentSchemaTypeIsIncompatible) {
  // Configure old schema, where Property is supposed to be a Person type
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kMessageType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeDocument(
                                            kPersonType,
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Configure new schema, where Property is supposed to be an Email type
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kMessageType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeDocument(
                                            kMessageType,
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_incompatible.emplace(kEmailType);
  // kEmailType depends on kMessageType
  SchemaUtil::DependentMap dependents_map = {
      {kMessageType, {{kEmailType, {}}}}};
  SchemaUtil::SchemaDelta actual = SchemaUtil::ComputeCompatibilityDelta(
      old_schema, new_schema, dependents_map);
  EXPECT_THAT(actual, Eq(schema_delta));
  EXPECT_THAT(actual.schema_types_incompatible,
              testing::ElementsAre(kEmailType));
  EXPECT_THAT(actual.schema_types_deleted, testing::IsEmpty());
}

TEST_P(SchemaUtilTest, SameNumberOfRequiredFieldsCanBeIncompatible) {
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property1")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("Property1")
                               .SetDataType(TYPE_STRING)
                               // Changing required to optional should be fine
                               .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("Property2")
                               .SetDataType(TYPE_STRING)
                               // Adding a new required property is incompatible
                               .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  SchemaUtil::SchemaDelta delta = SchemaUtil::ComputeCompatibilityDelta(
      old_schema, new_schema, /*new_schema_dependent_map=*/{});
  EXPECT_THAT(delta.schema_types_incompatible,
              testing::ElementsAre(kEmailType));
  EXPECT_THAT(delta.schema_types_index_incompatible, testing::IsEmpty());
  EXPECT_THAT(delta.schema_types_deleted, testing::IsEmpty());
}

TEST_P(SchemaUtilTest, SameNumberOfIndexedPropertiesCanMakeIndexIncompatible) {
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property1")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property1")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property2")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta delta = SchemaUtil::ComputeCompatibilityDelta(
      old_schema, new_schema, /*new_schema_dependent_map=*/{});
  EXPECT_THAT(delta.schema_types_incompatible, testing::IsEmpty());
  EXPECT_THAT(delta.schema_types_index_incompatible,
              testing::ElementsAre(kEmailType));
  EXPECT_THAT(delta.schema_types_deleted, testing::IsEmpty());
}

TEST_P(SchemaUtilTest, SameNumberOfJoinablePropertiesCanMakeJoinIncompatible) {
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property1")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property1")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property2")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta delta = SchemaUtil::ComputeCompatibilityDelta(
      old_schema, new_schema, /*new_schema_dependent_map=*/{});
  EXPECT_THAT(delta.schema_types_incompatible, testing::IsEmpty());
  EXPECT_THAT(delta.schema_types_index_incompatible, testing::IsEmpty());
  EXPECT_THAT(delta.schema_types_deleted, testing::IsEmpty());
  EXPECT_THAT(delta.schema_types_join_incompatible,
              testing::ElementsAre(kEmailType));
}

TEST_P(SchemaUtilTest, ChangingIndexedStringPropertiesMakesIndexIncompatible) {
  // Configure old schema
  SchemaProto schema_with_indexed_property =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema
  SchemaProto schema_with_unindexed_property =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_UNKNOWN,
                                                           TOKENIZER_NONE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_index_incompatible.insert(kPersonType);

  // New schema gained a new indexed string property.
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(
                  schema_with_unindexed_property, schema_with_indexed_property,
                  no_dependents_map),
              Eq(schema_delta));

  // New schema lost an indexed string property.
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(
                  schema_with_indexed_property, schema_with_unindexed_property,
                  no_dependents_map),
              Eq(schema_delta));
}

TEST_P(SchemaUtilTest, AddingNewIndexedStringPropertyMakesIndexIncompatible) {
  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewIndexedProperty")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_index_incompatible.insert(kPersonType);
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                                    no_dependents_map),
              Eq(schema_delta));
}

TEST_P(SchemaUtilTest,
       AddingNewNonIndexedStringPropertyShouldRemainIndexCompatible) {
  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewProperty")
                                        .SetDataTypeString(TERM_MATCH_UNKNOWN,
                                                           TOKENIZER_NONE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                                    no_dependents_map)
                  .schema_types_index_incompatible,
              IsEmpty());
}

TEST_P(SchemaUtilTest, ChangingIndexedIntegerPropertiesMakesIndexIncompatible) {
  // Configure old schema
  SchemaProto schema_with_indexed_property =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema
  SchemaProto schema_with_unindexed_property =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_UNKNOWN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_index_incompatible.insert(kPersonType);

  // New schema gained a new indexed integer property.
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(
                  schema_with_unindexed_property, schema_with_indexed_property,
                  no_dependents_map),
              Eq(schema_delta));

  // New schema lost an indexed integer property.
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(
                  schema_with_indexed_property, schema_with_unindexed_property,
                  no_dependents_map),
              Eq(schema_delta));
}

TEST_P(SchemaUtilTest, AddingNewIndexedIntegerPropertyMakesIndexIncompatible) {
  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewIndexedProperty")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_index_incompatible.insert(kPersonType);
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                                    no_dependents_map),
              Eq(schema_delta));
}

TEST_P(SchemaUtilTest,
       AddingNewNonIndexedIntegerPropertyShouldRemainIndexCompatible) {
  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewProperty")
                                        .SetDataTypeInt64(NUMERIC_MATCH_UNKNOWN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                                    no_dependents_map)
                  .schema_types_index_incompatible,
              IsEmpty());
}

TEST_P(SchemaUtilTest,
       AddingNewIndexedDocumentPropertyMakesIndexAndJoinIncompatible) {
  SchemaTypeConfigProto nested_schema =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("NewEmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_index_incompatible.insert(kPersonType);
  schema_delta.schema_types_join_incompatible.insert(kPersonType);

  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta result_schema_delta =
      SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                            dependents_map);
  EXPECT_THAT(result_schema_delta, Eq(schema_delta));
}

TEST_P(
    SchemaUtilTest,
    AddingNewIndexedDocumentPropertyWithIndexableListMakesIndexAndJoinIncompatible) {
  SchemaTypeConfigProto nested_schema =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema. The added nested document property is indexed, so
  // this is both index and join incompatible
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType(kPersonType)
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("Property")
                                   .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                   .SetCardinality(CARDINALITY_OPTIONAL))
                  .AddProperty(
                      PropertyConfigBuilder()
                          .SetName("NewEmailProperty")
                          .SetDataTypeDocument(
                              kEmailType,
                              /*indexable_nested_properties_list=*/
                              std::initializer_list<std::string>{"subject"})
                          .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_index_incompatible.insert(kPersonType);
  schema_delta.schema_types_join_incompatible.insert(kPersonType);

  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta result_schema_delta =
      SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                            dependents_map);
  EXPECT_THAT(result_schema_delta, Eq(schema_delta));
}

TEST_P(SchemaUtilTest,
       AddingNewNonIndexedDocumentPropertyMakesJoinIncompatible) {
  SchemaTypeConfigProto nested_schema =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema. The added nested document property is not indexed, so
  // this is index compatible, but join incompatible
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewEmailProperty")
                                        .SetDataTypeDocument(
                                            kEmailType,
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_join_incompatible.insert(kPersonType);

  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta result_schema_delta =
      SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                            dependents_map);
  EXPECT_THAT(result_schema_delta, Eq(schema_delta));
}

TEST_P(SchemaUtilTest, DeletingIndexedDocumentPropertyIsIncompatible) {
  SchemaTypeConfigProto nested_schema =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  // Configure old schemam with two nested document properties of the same type
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("EmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("AnotherEmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema and drop one of the nested document properties
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("EmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_incompatible.insert(kPersonType);
  schema_delta.schema_types_index_incompatible.insert(kPersonType);
  schema_delta.schema_types_join_incompatible.insert(kPersonType);

  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta result_schema_delta =
      SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                            dependents_map);
  EXPECT_THAT(result_schema_delta, Eq(schema_delta));
}

TEST_P(SchemaUtilTest, DeletingNonIndexedDocumentPropertyIsIncompatible) {
  SchemaTypeConfigProto nested_schema =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  // Configure old schemam with two nested document properties of the same type
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("EmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("AnotherEmailProperty")
                                        .SetDataTypeDocument(
                                            kEmailType,
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema and drop the non-indexed nested document property
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("EmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_incompatible.insert(kPersonType);
  schema_delta.schema_types_join_incompatible.insert(kPersonType);

  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta result_schema_delta =
      SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                            dependents_map);
  EXPECT_THAT(result_schema_delta, Eq(schema_delta));
}

TEST_P(SchemaUtilTest, ChangingIndexedDocumentPropertyIsIncompatible) {
  SchemaTypeConfigProto nested_schema =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  // Configure old schemam with two nested document properties of the same type
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("EmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("AnotherEmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema and change one of the nested document properties
  // to a different name (this is the same as deleting a property and adding
  // another)
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("EmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("DifferentEmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_incompatible.insert(kPersonType);
  schema_delta.schema_types_index_incompatible.insert(kPersonType);
  schema_delta.schema_types_join_incompatible.insert(kPersonType);

  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta result_schema_delta =
      SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                            dependents_map);
  EXPECT_THAT(result_schema_delta, Eq(schema_delta));
}

TEST_P(SchemaUtilTest, ChangingNonIndexedDocumentPropertyIsIncompatible) {
  SchemaTypeConfigProto nested_schema =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  // Configure old schemam with two nested document properties of the same type
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("EmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("AnotherEmailProperty")
                                        .SetDataTypeDocument(
                                            kEmailType,
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema and change the non-indexed nested document property to
  // a different name (this is the same as deleting a property and adding
  // another)
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(nested_schema)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("EmailProperty")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("DifferentEmailProperty")
                                        .SetDataTypeDocument(
                                            kEmailType,
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_incompatible.insert(kPersonType);
  schema_delta.schema_types_join_incompatible.insert(kPersonType);

  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta result_schema_delta =
      SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                            dependents_map);
  EXPECT_THAT(result_schema_delta, Eq(schema_delta));
}

TEST_P(SchemaUtilTest, ChangingJoinablePropertiesMakesJoinIncompatible) {
  // Configure old schema
  SchemaProto schema_with_joinable_property =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema
  SchemaProto schema_with_non_joinable_property =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_NONE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta expected_schema_delta;
  expected_schema_delta.schema_types_join_incompatible.insert(kPersonType);

  // New schema gained a new joinable property.
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(
                  schema_with_non_joinable_property,
                  schema_with_joinable_property, no_dependents_map),
              Eq(expected_schema_delta));

  // New schema lost a joinable property.
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(
                  schema_with_joinable_property,
                  schema_with_non_joinable_property, no_dependents_map),
              Eq(expected_schema_delta));
}

TEST_P(SchemaUtilTest, AddingNewJoinablePropertyMakesJoinIncompatible) {
  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewJoinableProperty")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta expected_schema_delta;
  expected_schema_delta.schema_types_join_incompatible.insert(kPersonType);
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                                    no_dependents_map),
              Eq(expected_schema_delta));
}

TEST_P(SchemaUtilTest, AddingNewNonJoinablePropertyShouldRemainJoinCompatible) {
  // Configure old schema
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("JoinableProperty")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Configure new schema
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("JoinableProperty")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NewProperty")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                                    no_dependents_map)
                  .schema_types_join_incompatible,
              IsEmpty());
}

TEST_P(SchemaUtilTest, AddingTypeIsCompatible) {
  // Can add a new type, existing data isn't incompatible, since none of them
  // are of this new schema type
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_new.insert(kEmailType);
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                                    no_dependents_map),
              Eq(schema_delta));
}

TEST_P(SchemaUtilTest, DeletingTypeIsNoted) {
  // Can't remove an old type, new schema needs to at least have all the
  // previously defined schema otherwise the Documents of the missing schema
  // are invalid
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_deleted.emplace(kPersonType);
  SchemaUtil::DependentMap no_dependents_map;
  EXPECT_THAT(SchemaUtil::ComputeCompatibilityDelta(old_schema, new_schema,
                                                    no_dependents_map),
              Eq(schema_delta));
}

TEST_P(SchemaUtilTest, DeletingPropertyAndChangingProperty) {
  SchemaProto old_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property1")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property2")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  // Remove Property2 and make Property1 indexed now. Removing Property2 should
  // be incompatible.
  SchemaProto new_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kEmailType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Property1")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_incompatible.emplace(kEmailType);
  schema_delta.schema_types_index_incompatible.emplace(kEmailType);
  SchemaUtil::DependentMap no_dependents_map;
  SchemaUtil::SchemaDelta actual = SchemaUtil::ComputeCompatibilityDelta(
      old_schema, new_schema, no_dependents_map);
  EXPECT_THAT(actual, Eq(schema_delta));
}

TEST_P(SchemaUtilTest, IndexNestedDocumentsIndexIncompatible) {
  // Make two schemas. One that sets index_nested_properties to false and one
  // that sets it to true.
  SchemaTypeConfigProto email_type_config =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto no_nested_index_schema =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emails")
                                        .SetDataTypeDocument(
                                            kEmailType,
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  SchemaProto nested_index_schema =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("emails")
                               .SetDataTypeDocument(
                                   kEmailType, /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Going from index_nested_properties=false to index_nested_properties=true
  // should make kPersonType index_incompatible. kEmailType should be
  // unaffected.
  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_index_incompatible.emplace(kPersonType);
  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta actual = SchemaUtil::ComputeCompatibilityDelta(
      no_nested_index_schema, nested_index_schema, dependents_map);
  EXPECT_THAT(actual, Eq(schema_delta));

  // Going from index_nested_properties=true to index_nested_properties=false
  // should also make kPersonType index_incompatible. kEmailType should be
  // unaffected.
  actual = SchemaUtil::ComputeCompatibilityDelta(
      nested_index_schema, no_nested_index_schema, dependents_map);
  EXPECT_THAT(actual, Eq(schema_delta));
}

TEST_P(SchemaUtilTest, AddOrDropIndexableNestedProperties_IndexIncompatible) {
  SchemaTypeConfigProto email_type_config =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("recipient")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("body")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema_1 =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType(kPersonType)
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("emails")
                                   .SetDataTypeDocument(
                                       kEmailType,
                                       /*indexable_nested_properties_list=*/
                                       {"recipient", "subject", "body"})
                                   .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  SchemaProto schema_2 =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emails")
                                        .SetDataTypeDocument(
                                            kEmailType,
                                            /*indexable_nested_properties=*/
                                            {"recipient", "subject"})
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Dropping some indexable_nested_properties should make kPersonType
  // index_incompatible. kEmailType should be unaffected.
  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_index_incompatible.emplace(kPersonType);
  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta actual =
      SchemaUtil::ComputeCompatibilityDelta(schema_1, schema_2, dependents_map);
  EXPECT_THAT(actual, Eq(schema_delta));

  // Adding some indexable_nested_properties should also make kPersonType
  // index_incompatible. kEmailType should be unaffected.
  actual =
      SchemaUtil::ComputeCompatibilityDelta(schema_2, schema_1, dependents_map);
  EXPECT_THAT(actual, Eq(schema_delta));
}

TEST_P(SchemaUtilTest, ChangingIndexableNestedProperties_IndexIncompatible) {
  SchemaTypeConfigProto email_type_config =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("recipient")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("body")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema_1 =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType(kPersonType)
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("emails")
                                   .SetDataTypeDocument(
                                       kEmailType,
                                       /*indexable_nested_properties_list=*/
                                       {"recipient", "subject"})
                                   .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  SchemaProto schema_2 =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType(kPersonType)
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("emails")
                                   .SetDataTypeDocument(
                                       kEmailType,
                                       /*indexable_nested_properties_list=*/
                                       {"recipient", "body"})
                                   .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Changing 'subject' to 'body' for indexable_nested_properties_list should
  // make kPersonType index_incompatible. kEmailType should be unaffected.
  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_index_incompatible.emplace(kPersonType);
  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta actual =
      SchemaUtil::ComputeCompatibilityDelta(schema_1, schema_2, dependents_map);
  EXPECT_THAT(actual, Eq(schema_delta));
}

TEST_P(SchemaUtilTest, IndexableNestedPropertiesFullSet_IndexIncompatible) {
  SchemaTypeConfigProto email_type_config =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("recipient")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("body")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema_1 =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(SchemaTypeConfigBuilder()
                       .SetType(kPersonType)
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emails")
                                        .SetDataTypeDocument(
                                            kEmailType,
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  SchemaProto schema_2 =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType(kPersonType)
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("emails")
                                   .SetDataTypeDocument(
                                       kEmailType,
                                       /*indexable_nested_properties_list=*/
                                       {"recipient", "body", "subject"})
                                   .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // This scenario also invalidates kPersonType and triggers an index rebuild at
  // the moment, even though the set of indexable_nested_properties from
  // schema_1 to schema_2 should be the same.
  SchemaUtil::SchemaDelta schema_delta;
  schema_delta.schema_types_index_incompatible.emplace(kPersonType);
  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta actual =
      SchemaUtil::ComputeCompatibilityDelta(schema_1, schema_2, dependents_map);
  EXPECT_THAT(actual, Eq(schema_delta));
}

TEST_P(SchemaUtilTest,
       ChangingIndexableNestedPropertiesOrder_IndexIsCompatible) {
  SchemaTypeConfigProto email_type_config =
      SchemaTypeConfigBuilder()
          .SetType(kEmailType)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("recipient")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("body")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema_1 =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType(kPersonType)
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("emails")
                                   .SetDataTypeDocument(
                                       kEmailType,
                                       /*indexable_nested_properties_list=*/
                                       {"recipient", "subject", "body"})
                                   .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  SchemaProto schema_2 =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType(kPersonType)
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("emails")
                                   .SetDataTypeDocument(
                                       kEmailType,
                                       /*indexable_nested_properties_list=*/
                                       {"subject", "body", "recipient"})
                                   .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Changing order of elements in indexable_nested_properties_list should have
  // no effect on schema compatibility.
  SchemaUtil::SchemaDelta schema_delta;
  SchemaUtil::DependentMap dependents_map = {{kEmailType, {{kPersonType, {}}}}};
  SchemaUtil::SchemaDelta actual =
      SchemaUtil::ComputeCompatibilityDelta(schema_1, schema_2, dependents_map);
  EXPECT_THAT(actual, Eq(schema_delta));
  EXPECT_THAT(actual.schema_types_index_incompatible, IsEmpty());
}

TEST_P(SchemaUtilTest, ValidateStringIndexingConfigShouldHaveTermMatchType) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  // Error if we don't set a term match type
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // Passes once we set a term match type
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("Foo")
                       .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                       .SetCardinality(CARDINALITY_REQUIRED)))
               .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(SchemaUtilTest, ValidateStringIndexingConfigShouldHaveTokenizer) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_NONE)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  // Error if we don't set a tokenizer type
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // Passes once we set a tokenizer type
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("Foo")
                       .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                       .SetCardinality(CARDINALITY_REQUIRED)))
               .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(SchemaUtilTest,
       ValidateJoinablePropertyTypeQualifiedIdShouldHaveStringDataType) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_INT64)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  // Error if data type is not STRING for qualified id joinable value type.
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // Passes once we set STRING as the data type.
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("Foo")
                       .SetDataType(TYPE_STRING)
                       .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                    /*propagate_delete=*/false)
                       .SetCardinality(CARDINALITY_REQUIRED)))
               .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(SchemaUtilTest,
       ValidateJoinablePropertyShouldNotHaveRepeatedCardinality) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_STRING)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Error if using REPEATED cardinality for joinable property.
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // Passes once we use OPTIONAL cardinality with joinable property.
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("Foo")
                       .SetDataType(TYPE_STRING)
                       .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                    /*propagate_delete=*/false)
                       .SetCardinality(CARDINALITY_OPTIONAL)))
               .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());

  // Passes once we use REQUIRED cardinality with joinable property.
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("Foo")
                       .SetDataType(TYPE_STRING)
                       .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                    /*propagate_delete=*/false)
                       .SetCardinality(CARDINALITY_REQUIRED)))
               .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());

  // Passes once we use REPEATED cardinality with non-joinable property.
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("Foo")
                       .SetDataType(TYPE_STRING)
                       .SetJoinable(JOINABLE_VALUE_TYPE_NONE,
                                    /*propagate_delete=*/false)
                       .SetCardinality(CARDINALITY_REPEATED)))
               .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(SchemaUtilTest,
       ValidateJoinablePropertyWithDeletePropagationShouldHaveTypeQualifiedId) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_STRING)
                  .SetJoinable(JOINABLE_VALUE_TYPE_NONE,
                               /*propagate_delete=*/true)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  // Error if enabling delete propagation with non qualified id joinable value
  // type.
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // Passes once we set qualified id joinable value type with delete propagation
  // enabled.
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("Foo")
                       .SetDataType(TYPE_STRING)
                       .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                    /*propagate_delete=*/true)
                       .SetCardinality(CARDINALITY_REQUIRED)))
               .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());

  // Passes once we disable delete propagation.
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("MyType").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("Foo")
                       .SetDataType(TYPE_STRING)
                       .SetJoinable(JOINABLE_VALUE_TYPE_NONE,
                                    /*propagate_delete=*/false)
                       .SetCardinality(CARDINALITY_REQUIRED)))
               .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(SchemaUtilTest,
       ValidateNestedJoinablePropertyShouldNotHaveNestedRepeatedCardinality) {
  // Dependency and nested document property cardinality:
  //   "C" --(REPEATED)--> "B" --(OPTIONAL)--> "A"
  // where "A" contains joinable property. This should not be allowed.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("A").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_STRING)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("B").AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("C").AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetDataTypeDocument("B",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // Passes once we use non-REPEATED cardinality for "C.b", i.e. the dependency
  // and nested document property cardinality becomes:
  //   "C" --(OPTIONAL)--> "B" --(OPTIONAL)--> "A"
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("A").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("Foo")
                       .SetDataType(TYPE_STRING)
                       .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                    /*propagate_delete=*/false)
                       .SetCardinality(CARDINALITY_OPTIONAL)))
               .AddType(SchemaTypeConfigBuilder().SetType("B").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("a")
                       .SetDataTypeDocument("A",
                                            /*index_nested_properties=*/false)
                       .SetCardinality(CARDINALITY_OPTIONAL)))
               .AddType(SchemaTypeConfigBuilder().SetType("C").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("b")
                       .SetDataTypeDocument("B",
                                            /*index_nested_properties=*/false)
                       .SetCardinality(CARDINALITY_OPTIONAL)))
               .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(
    SchemaUtilTest,
    ValidateNestedJoinablePropertyShouldAllowRepeatedCardinalityIfNoJoinableProperty) {
  // Dependency and nested document property cardinality:
  //   "C" --(OPTIONAL)--> "B" --(REPEATED)--> "A"
  // where only "B" contains joinable property. This should be allowed.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("A").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_STRING)
                  .SetJoinable(JOINABLE_VALUE_TYPE_NONE,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("B")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("a")
                                        .SetDataTypeDocument(
                                            "A",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("Bar")
                               .SetDataType(TYPE_STRING)
                               .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                            /*propagate_delete=*/false)
                               .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("C").AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetDataTypeDocument("B",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Passes since nested schema type with REPEATED cardinality doesn't have
  // joinable property.
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(SchemaUtilTest,
       ValidateNestedJoinablePropertyMultiplePropertiesWithSameSchema) {
  // Dependency and nested document property cardinality:
  //        --(a1: OPTIONAL)--
  //      /                    \
  // B --                        --> A
  //      \                    /
  //        --(a2: REPEATED)--
  // where "A" contains joinable property. This should not be allowed.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("A").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_STRING)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("B")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("a1")
                                        .SetDataTypeDocument(
                                            "A",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("a2")
                                        .SetDataTypeDocument(
                                            "A",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // Passes once we use non-REPEATED cardinality for "B.a2", i.e. the dependency
  // and nested document property cardinality becomes:
  //        --(a1: OPTIONAL)--
  //      /                    \
  // B --                        --> A
  //      \                    /
  //        --(a2: OPTIONAL)--
  schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("A").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_STRING)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("B")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("a1")
                                        .SetDataTypeDocument(
                                            "A",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("a2")
                                        .SetDataTypeDocument(
                                            "A",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(SchemaUtilTest, ValidateNestedJoinablePropertyDiamondRelationship) {
  // Dependency and nested document property cardinality:
  //           B
  //         /   \
  // (OPTIONAL) (OPTIONAL)
  //       /       \
  // D ---           --> A
  //       \       /
  // (OPTIONAL) (OPTIONAL)
  //         \   /
  //           C
  // where "A" contains joinable property. This should be allowed.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("A").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_STRING)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("B").AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("C").AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("D")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("b")
                                        .SetDataTypeDocument(
                                            "B",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("c")
                                        .SetDataTypeDocument(
                                            "C",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());

  // Fails once we change any of edge to REPEATED cardinality.
  //           B
  //         /   \
  // (REPEATED) (OPTIONAL)
  //       /       \
  // D ---           --> A
  //       \       /
  // (OPTIONAL) (OPTIONAL)
  //         \   /
  //           C
  schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("A").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_STRING)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("B").AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("C").AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("D")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("b")
                                        .SetDataTypeDocument(
                                            "B",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("c")
                                        .SetDataTypeDocument(
                                            "C",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  //           B
  //         /   \
  // (OPTIONAL) (REPEATED)
  //       /       \
  // D ---           --> A
  //       \       /
  // (OPTIONAL) (OPTIONAL)
  //         \   /
  //           C
  schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("A").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_STRING)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("B").AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_REPEATED)))
          .AddType(SchemaTypeConfigBuilder().SetType("C").AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("D")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("b")
                                        .SetDataTypeDocument(
                                            "B",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("c")
                                        .SetDataTypeDocument(
                                            "C",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  //           B
  //         /   \
  // (OPTIONAL) (OPTIONAL)
  //       /       \
  // D ---           --> A
  //       \       /
  // (REPEATED) (OPTIONAL)
  //         \   /
  //           C
  schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("A").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_STRING)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("B").AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("C").AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("D")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("b")
                                        .SetDataTypeDocument(
                                            "B",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("c")
                                        .SetDataTypeDocument(
                                            "C",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  //           B
  //         /   \
  // (OPTIONAL) (OPTIONAL)
  //       /       \
  // D ---           --> A
  //       \       /
  // (OPTIONAL) (REPEATED)
  //         \   /
  //           C
  schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("A").AddProperty(
              PropertyConfigBuilder()
                  .SetName("Foo")
                  .SetDataType(TYPE_STRING)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("B").AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("C").AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_REPEATED)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("D")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("b")
                                        .SetDataTypeDocument(
                                            "B",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("c")
                                        .SetDataTypeDocument(
                                            "C",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest,
       ValidDocumentIndexingConfigFields_emptyIndexableListBooleanTrue) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("InnerSchema")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_UNKNOWN,
                                                           TOKENIZER_NONE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("OuterSchema")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("InnerProperty")
                                        .SetDataTypeDocument(
                                            "InnerSchema",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  SchemaTypeConfigProto* outerSchemaType = schema.mutable_types(1);
  outerSchemaType->mutable_properties(0)
      ->mutable_document_indexing_config()
      ->clear_indexable_nested_properties_list();

  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(SchemaUtilTest,
       ValidDocumentIndexingConfigFields_emptyIndexableListBooleanFalse) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("InnerSchema")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_UNKNOWN,
                                                           TOKENIZER_NONE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("OuterSchema")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("InnerProperty")
                                        .SetDataTypeDocument(
                                            "InnerSchema",
                                            /*index_nested_properties=*/false)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  SchemaTypeConfigProto* outerSchemaType = schema.mutable_types(1);
  outerSchemaType->mutable_properties(0)
      ->mutable_document_indexing_config()
      ->clear_indexable_nested_properties_list();

  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(SchemaUtilTest,
       ValidDocumentIndexingConfigFields_nonEmptyIndexableList) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("InnerSchema")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("OuterSchema")
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("InnerProperty")
                               .SetDataTypeDocument(
                                   "InnerSchema",
                                   /*indexable_nested_properties_list=*/
                                   std::initializer_list<std::string>{"prop1"})
                               .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  SchemaTypeConfigProto* outerSchemaType = schema.mutable_types(1);
  outerSchemaType->mutable_properties(0)
      ->mutable_document_indexing_config()
      ->set_index_nested_properties(false);
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(SchemaUtilTest, InvalidDocumentIndexingConfigFields) {
  // If indexable_nested_properties is non-empty, index_nested_properties is
  // required to be false.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("InnerSchema")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("OuterSchema")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("InnerProperty")
                                        .SetDataTypeDocument(
                                            "InnerSchema",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Setting a non-empty indexable_nested_properties_list while
  // index_nested_properties=true is invalid.
  SchemaTypeConfigProto* outerSchemaType = schema.mutable_types(1);
  outerSchemaType->mutable_properties(0)
      ->mutable_document_indexing_config()
      ->add_indexable_nested_properties_list("prop");

  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(SchemaUtilTest, MultipleReferencesToSameNestedSchemaOk) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("InnerSchema"))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("OuterSchema")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("InnerProperty1")
                                        .SetDataTypeDocument(
                                            "InnerSchema",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("InnerProperty2")
                                        .SetDataTypeDocument(
                                            "InnerSchema",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()), IsOk());
}

TEST_P(SchemaUtilTest, InvalidSelfReference) {
  // Create a schema with a self-reference cycle in it: OwnSchema -> OwnSchema
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("OwnSchema")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NestedDocument")
                                        .SetDataTypeDocument(
                                            "OwnSchema",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, InvalidSelfReferenceEvenWithOtherProperties) {
  // Create a schema with a self-reference cycle in it: OwnSchema -> OwnSchema
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("OwnSchema")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("NestedDocument")
                                        .SetDataTypeDocument(
                                            "OwnSchema",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("SomeString")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, InvalidInfiniteLoopTwoDegrees) {
  // Create a schema for the outer schema
  SchemaProto schema =
      SchemaBuilder()
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType("A")
                  // Reference schema B, so far so good
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("NestedDocument")
                                   .SetDataTypeDocument(
                                       "B", /*index_nested_properties=*/true)
                                   .SetCardinality(CARDINALITY_OPTIONAL)))
          // Create the inner schema
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType("B")
                  // Reference the schema A, causing an invalid cycle of
                  // references.
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("NestedDocument")
                                   .SetDataTypeDocument(
                                       "A", /*index_nested_properties=*/true)
                                   .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Two degrees of referencing: A -> B -> A
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, InvalidInfiniteLoopThreeDegrees) {
  SchemaProto schema =
      SchemaBuilder()
          // Create a schema for the outer schema
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType("A")
                  // Reference schema B, so far so good
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("NestedDocument")
                                   .SetDataTypeDocument(
                                       "B", /*index_nested_properties=*/true)
                                   .SetCardinality(CARDINALITY_OPTIONAL)))
          // Create the inner schema
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType("B")
                  // Reference schema C, so far so good
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("NestedDocument")
                                   .SetDataTypeDocument(
                                       "C", /*index_nested_properties=*/true)
                                   .SetCardinality(CARDINALITY_REPEATED)))
          // Create the inner schema
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType("C")
                  // Reference schema C, so far so good
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("NestedDocument")
                                   .SetDataTypeDocument(
                                       "A", /*index_nested_properties=*/true)
                                   .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Three degrees of referencing: A -> B -> C -> A
  EXPECT_THAT(SchemaUtil::Validate(schema, GetParam()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
                       HasSubstr("Invalid cycle")));
}

TEST_P(SchemaUtilTest, ChildMissingOptionalAndRepeatedPropertiesNotOk) {
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();

  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();
  EXPECT_THAT(
      SchemaUtil::Validate(schema, GetParam()),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
               HasSubstr("Property text is not present in child type")));
}

TEST_P(SchemaUtilTest, ChildMissingRequiredPropertyNotOk) {
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetCardinality(CARDINALITY_REQUIRED)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();

  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();
  EXPECT_THAT(
      SchemaUtil::Validate(schema, GetParam()),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
               HasSubstr("Property text is not present in child type")));
}

TEST_P(SchemaUtilTest, ChildCompatiblePropertyOk) {
  SchemaTypeConfigProto message_type =
      SchemaTypeConfigBuilder()
          .SetType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetCardinality(CARDINALITY_REPEATED)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("person")
                           .SetCardinality(CARDINALITY_OPTIONAL)
                           .SetDataTypeDocument(
                               "Person", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto artist_message_type =
      SchemaTypeConfigBuilder()
          .SetType("ArtistMessage")
          .AddParentType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  // OPTIONAL is compatible with REPEATED.
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              // An extra text is compatible.
              PropertyConfigBuilder()
                  .SetName("extraText")
                  .SetCardinality(CARDINALITY_REPEATED)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              // An extra double is compatible
              PropertyConfigBuilder()
                  .SetName("extraDouble")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataType(TYPE_DOUBLE))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("person")
                           // REQUIRED is compatible with OPTIONAL.
                           .SetCardinality(CARDINALITY_REQUIRED)
                           // Artist is compatible with Person.
                           .SetDataTypeDocument(
                               "Artist", /*index_nested_properties=*/true))
          .Build();

  SchemaTypeConfigProto person_type =
      SchemaTypeConfigBuilder().SetType("Person").Build();
  SchemaTypeConfigProto artist_type = SchemaTypeConfigBuilder()
                                          .SetType("Artist")
                                          .AddParentType("Person")
                                          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(message_type)
                           .AddType(artist_message_type)
                           .AddType(person_type)
                           .AddType(artist_type)
                           .Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(3));
  EXPECT_THAT(d_map["Message"],
              UnorderedElementsAre(Pair("ArtistMessage", IsEmpty())));
  EXPECT_THAT(d_map["Person"],
              UnorderedElementsAre(
                  Pair("Message", UnorderedElementsAre(Pointee(EqualsProto(
                                      message_type.properties(1))))),
                  Pair("Artist", IsEmpty())));
  EXPECT_THAT(d_map["Artist"],
              UnorderedElementsAre(Pair(
                  "ArtistMessage", UnorderedElementsAre(Pointee(EqualsProto(
                                       artist_message_type.properties(3)))))));
}

TEST_P(SchemaUtilTest, ChildIncompatibleCardinalityPropertyNotOk) {
  SchemaTypeConfigProto message_type =
      SchemaTypeConfigBuilder()
          .SetType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetCardinality(CARDINALITY_REPEATED)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("person")
                           .SetCardinality(CARDINALITY_OPTIONAL)
                           .SetDataTypeDocument(
                               "Person", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto artist_message_type =
      SchemaTypeConfigBuilder()
          .SetType("ArtistMessage")
          .AddParentType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("extraText")
                  .SetCardinality(CARDINALITY_REPEATED)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("person")
                           // Overwrite OPTIONAL to REPEATED is not ok.
                           .SetCardinality(CARDINALITY_REPEATED)
                           .SetDataTypeDocument(
                               "Artist", /*index_nested_properties=*/true))
          .Build();

  SchemaTypeConfigProto person_type =
      SchemaTypeConfigBuilder().SetType("Person").Build();
  SchemaTypeConfigProto artist_type = SchemaTypeConfigBuilder()
                                          .SetType("Artist")
                                          .AddParentType("Person")
                                          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(message_type)
                           .AddType(artist_message_type)
                           .AddType(person_type)
                           .AddType(artist_type)
                           .Build();
  EXPECT_THAT(
      SchemaUtil::Validate(schema, GetParam()),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
               HasSubstr("Property person from child type ArtistMessage is not "
                         "compatible to the parent type Message.")));
}

TEST_P(SchemaUtilTest, ChildIncompatibleDataTypePropertyNotOk) {
  SchemaTypeConfigProto message_type =
      SchemaTypeConfigBuilder()
          .SetType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetCardinality(CARDINALITY_REPEATED)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("person")
                           .SetCardinality(CARDINALITY_OPTIONAL)
                           .SetDataTypeDocument(
                               "Person", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto artist_message_type =
      SchemaTypeConfigBuilder()
          .SetType("ArtistMessage")
          .AddParentType("Message")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("text")
                           .SetCardinality(CARDINALITY_OPTIONAL)
                           // Double is not compatible to string.
                           .SetDataType(TYPE_DOUBLE))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("extraText")
                  .SetCardinality(CARDINALITY_REPEATED)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("person")
                           .SetCardinality(CARDINALITY_REQUIRED)
                           .SetDataTypeDocument(
                               "Artist", /*index_nested_properties=*/true))
          .Build();

  SchemaTypeConfigProto person_type =
      SchemaTypeConfigBuilder().SetType("Person").Build();
  SchemaTypeConfigProto artist_type = SchemaTypeConfigBuilder()
                                          .SetType("Artist")
                                          .AddParentType("Person")
                                          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(message_type)
                           .AddType(artist_message_type)
                           .AddType(person_type)
                           .AddType(artist_type)
                           .Build();
  EXPECT_THAT(
      SchemaUtil::Validate(schema, GetParam()),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
               HasSubstr("Property text from child type ArtistMessage is not "
                         "compatible to the parent type Message.")));
}

TEST_P(SchemaUtilTest, ChildIncompatibleDocumentTypePropertyNotOk) {
  SchemaTypeConfigProto message_type =
      SchemaTypeConfigBuilder()
          .SetType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetCardinality(CARDINALITY_REPEATED)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("person")
                           .SetCardinality(CARDINALITY_OPTIONAL)
                           .SetDataTypeDocument(
                               "Person", /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto artist_message_type =
      SchemaTypeConfigBuilder()
          .SetType("ArtistMessage")
          .AddParentType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("extraText")
                  .SetCardinality(CARDINALITY_REPEATED)
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("person")
                  .SetCardinality(CARDINALITY_REQUIRED)
                  // Artist is not a subtype of Person, thus incompatible
                  .SetDataTypeDocument("Artist",
                                       /*index_nested_properties=*/true))
          .Build();

  SchemaTypeConfigProto person_type =
      SchemaTypeConfigBuilder().SetType("Person").Build();
  // In this test, Artist is not a subtype of Person.
  SchemaTypeConfigProto artist_type =
      SchemaTypeConfigBuilder().SetType("Artist").Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(message_type)
                           .AddType(artist_message_type)
                           .AddType(person_type)
                           .AddType(artist_type)
                           .Build();
  EXPECT_THAT(
      SchemaUtil::Validate(schema, GetParam()),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
               HasSubstr("Property person from child type ArtistMessage is not "
                         "compatible to the parent type Message.")));
}

TEST_P(SchemaUtilTest, ChildCompatibleMultipleParentPropertyOk) {
  SchemaTypeConfigProto email_type =
      SchemaTypeConfigBuilder()
          .SetType("Email")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("sender")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("recipient")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaTypeConfigProto message_type =
      SchemaTypeConfigBuilder()
          .SetType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("content")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaTypeConfigProto email_message_type =
      SchemaTypeConfigBuilder()
          .SetType("EmailMessage")
          .AddParentType("Email")
          .AddParentType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("sender")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("recipient")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("content")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  SchemaProto schema = SchemaBuilder()
                           .AddType(email_type)
                           .AddType(message_type)
                           .AddType(email_message_type)
                           .Build();
  ICING_ASSERT_OK_AND_ASSIGN(SchemaUtil::DependentMap d_map,
                             SchemaUtil::Validate(schema, GetParam()));
  EXPECT_THAT(d_map, SizeIs(2));
  EXPECT_THAT(d_map["Email"],
              UnorderedElementsAre(Pair("EmailMessage", IsEmpty())));
  EXPECT_THAT(d_map["Message"],
              UnorderedElementsAre(Pair("EmailMessage", IsEmpty())));
}

TEST_P(SchemaUtilTest, ChildIncompatibleMultipleParentPropertyNotOk) {
  SchemaTypeConfigProto email_type =
      SchemaTypeConfigBuilder()
          .SetType("Email")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("sender")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("recipient")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaTypeConfigProto message_type =
      SchemaTypeConfigBuilder()
          .SetType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("content")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  // Missing the "sender" field from parent "Email", thus incompatible.
  SchemaTypeConfigProto email_message_type1 =
      SchemaTypeConfigBuilder()
          .SetType("EmailMessage")
          .AddParentType("Email")
          .AddParentType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("recipient")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("content")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema1 = SchemaBuilder()
                            .AddType(email_type)
                            .AddType(message_type)
                            .AddType(email_message_type1)
                            .Build();
  EXPECT_THAT(
      SchemaUtil::Validate(schema1, GetParam()),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT,
               HasSubstr(
                   "Property sender is not present in child type EmailMessage, "
                   "but it is defined in the parent type Email.")));

  // Missing the "content" field from parent "Message", thus incompatible.
  SchemaTypeConfigProto email_message_type2 =
      SchemaTypeConfigBuilder()
          .SetType("EmailMessage")
          .AddParentType("Email")
          .AddParentType("Message")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("sender")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("recipient")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema2 = SchemaBuilder()
                            .AddType(email_type)
                            .AddType(message_type)
                            .AddType(email_message_type2)
                            .Build();
  EXPECT_THAT(
      SchemaUtil::Validate(schema2, GetParam()),
      StatusIs(
          libtextclassifier3::StatusCode::INVALID_ARGUMENT,
          HasSubstr(
              "Property content is not present in child type EmailMessage, "
              "but it is defined in the parent type Message.")));
}

INSTANTIATE_TEST_SUITE_P(
    SchemaUtilTest, SchemaUtilTest,
    testing::Values(/*allow_circular_schema_definitions=*/true, false));

struct IsIndexedPropertyTestParam {
  PropertyConfigProto property_config;
  bool expected_result;

  explicit IsIndexedPropertyTestParam(PropertyConfigProto property_config_in,
                                      bool expected_result_in)
      : property_config(std::move(property_config_in)),
        expected_result(expected_result_in) {}
};

class SchemaUtilIsIndexedPropertyTest
    : public ::testing::TestWithParam<IsIndexedPropertyTestParam> {};

TEST_P(SchemaUtilIsIndexedPropertyTest, IsIndexedProperty) {
  const IsIndexedPropertyTestParam& test_param = GetParam();
  EXPECT_THAT(SchemaUtil::IsIndexedProperty(test_param.property_config),
              Eq(test_param.expected_result));
}

INSTANTIATE_TEST_SUITE_P(
    SchemaUtilIsIndexedPropertyTest, SchemaUtilIsIndexedPropertyTest,
    testing::Values(
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_UNKNOWN,
                                                          TOKENIZER_NONE)
                                       .Build(),
                                   false),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_UNKNOWN,
                                                          TOKENIZER_PLAIN)
                                       .Build(),
                                   false),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_UNKNOWN,
                                                          TOKENIZER_VERBATIM)
                                       .Build(),
                                   false),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_UNKNOWN,
                                                          TOKENIZER_RFC822)
                                       .Build(),
                                   false),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_UNKNOWN,
                                                          TOKENIZER_URL)
                                       .Build(),
                                   false),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_EXACT,
                                                          TOKENIZER_NONE)
                                       .Build(),
                                   false),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_EXACT,
                                                          TOKENIZER_PLAIN)
                                       .Build(),
                                   true),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_EXACT,
                                                          TOKENIZER_VERBATIM)
                                       .Build(),
                                   true),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_EXACT,
                                                          TOKENIZER_RFC822)
                                       .Build(),
                                   true),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_EXACT,
                                                          TOKENIZER_URL)
                                       .Build(),
                                   true),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_PREFIX,
                                                          TOKENIZER_NONE)
                                       .Build(),
                                   false),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_PREFIX,
                                                          TOKENIZER_PLAIN)
                                       .Build(),
                                   true),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_PREFIX,
                                                          TOKENIZER_VERBATIM)
                                       .Build(),
                                   true),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_PREFIX,
                                                          TOKENIZER_RFC822)
                                       .Build(),
                                   true),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeString(TERM_MATCH_PREFIX,
                                                          TOKENIZER_URL)
                                       .Build(),
                                   true),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeInt64(NUMERIC_MATCH_UNKNOWN)
                                       .Build(),
                                   false),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                       .Build(),
                                   true),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataType(TYPE_DOUBLE)
                                       .Build(),
                                   false),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataType(TYPE_BOOLEAN)
                                       .Build(),
                                   false),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataType(TYPE_BYTES)
                                       .Build(),
                                   false),
        IsIndexedPropertyTestParam(PropertyConfigBuilder()
                                       .SetName("property")
                                       .SetDataType(TYPE_DOCUMENT)
                                       .Build(),
                                   false)));

}  // namespace

}  // namespace lib
}  // namespace icing
