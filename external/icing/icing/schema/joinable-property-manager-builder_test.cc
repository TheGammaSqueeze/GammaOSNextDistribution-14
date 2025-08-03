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

#include <memory>
#include <string>
#include <string_view>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/proto/term.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/joinable-property-manager.h"
#include "icing/store/dynamic-trie-key-mapper.h"
#include "icing/store/key-mapper.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::HasSubstr;
using ::testing::IsEmpty;
using ::testing::Pointee;

class JoinablePropertyManagerBuilderTest : public ::testing::Test {
 protected:
  void SetUp() override { test_dir_ = GetTestTempDir() + "/icing"; }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  Filesystem filesystem_;
  std::string test_dir_;
};

TEST_F(JoinablePropertyManagerBuilderTest, Build) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put("SchemaTypeOne", 0));
  ICING_ASSERT_OK(schema_type_mapper->Put("SchemaTypeTwo", 1));

  PropertyConfigProto prop_foo =
      PropertyConfigBuilder()
          .SetDataType(TYPE_STRING)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/true)
          .SetCardinality(CARDINALITY_OPTIONAL)
          .Build();
  PropertyConfigProto prop_bar =
      PropertyConfigBuilder()
          .SetDataType(TYPE_STRING)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/false)
          .SetCardinality(CARDINALITY_OPTIONAL)
          .Build();
  PropertyConfigProto prop_baz =
      PropertyConfigBuilder()
          .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/true)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build();

  JoinablePropertyManager::Builder builder(*schema_type_mapper);
  // Add "foo" and "bar" to "SchemaTypeOne" (schema_type_id = 0).
  ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
      /*schema_type_id=*/0, prop_foo, /*property_path=*/"foo"));
  ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
      /*schema_type_id=*/0, prop_bar, /*property_path=*/"bar"));
  // Add "baz" to "SchemaTypeTwo" (schema_type_id = 1).
  ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
      /*schema_type_id=*/1, prop_baz, /*property_path=*/"baz"));

  std::unique_ptr<JoinablePropertyManager> joinable_property_manager =
      std::move(builder).Build();
  // Check "SchemaTypeOne"
  EXPECT_THAT(
      joinable_property_manager->GetMetadataList("SchemaTypeOne"),
      IsOkAndHolds(Pointee(ElementsAre(
          EqualsJoinablePropertyMetadata(
              /*expected_id=*/0, /*expected_property_path=*/"foo", prop_foo),
          EqualsJoinablePropertyMetadata(/*expected_id=*/1,
                                         /*expected_property_path=*/"bar",
                                         prop_bar)))));
  // Check "SchemaTypeTwo"
  EXPECT_THAT(
      joinable_property_manager->GetMetadataList("SchemaTypeTwo"),
      IsOkAndHolds(Pointee(ElementsAre(EqualsJoinablePropertyMetadata(
          /*expected_id=*/0, /*expected_property_path=*/"baz", prop_baz)))));
}

TEST_F(JoinablePropertyManagerBuilderTest, TooManyPropertiesShouldFail) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put("SchemaType", 0));

  JoinablePropertyManager::Builder builder(*schema_type_mapper);
  // Add kTotalNumJoinableProperties joinable properties
  for (int i = 0; i < kTotalNumJoinableProperties; i++) {
    PropertyConfigProto property_config =
        PropertyConfigBuilder()
            .SetDataType(TYPE_STRING)
            .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                         /*propagate_delete=*/true)
            .SetCardinality(CARDINALITY_REQUIRED)
            .Build();
    ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
        /*schema_type_id=*/0, property_config,
        /*property_path=*/"property" + std::to_string(i)));
  }

  // Add another joinable property. This should fail.
  PropertyConfigProto property_config =
      PropertyConfigBuilder()
          .SetDataType(TYPE_STRING)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/true)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build();
  EXPECT_THAT(builder.ProcessSchemaTypePropertyConfig(
                  /*schema_type_id=*/0, property_config,
                  /*property_path=*/"propertyExceed"),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE,
                       HasSubstr("Too many properties")));
}

TEST_F(JoinablePropertyManagerBuilderTest, InvalidSchemaTypeIdShouldFail) {
  // Create a schema type mapper with invalid schema type id.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put("SchemaType", 0));

  PropertyConfigProto property_config =
      PropertyConfigBuilder()
          .SetDataType(TYPE_STRING)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/true)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build();

  JoinablePropertyManager::Builder builder(*schema_type_mapper);
  EXPECT_THAT(
      builder.ProcessSchemaTypePropertyConfig(
          /*schema_type_id=*/-1, property_config, /*property_path=*/"property"),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(JoinablePropertyManagerBuilderTest,
       SchemaTypeIdInconsistentWithSchemaTypeMapperSizeShouldFail) {
  // Create a schema type mapper with schema type id = 2, but size of mapper is
  // 2.
  // Since JoinablePropertyManagerBuilder expects 2 schema type ids = [0, 1],
  // building with schema type id = 2 should fail even though id = 2 is in
  // schema type mapper.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put("SchemaTypeOne", 0));
  ICING_ASSERT_OK(schema_type_mapper->Put("SchemaTypeTwo", 2));

  PropertyConfigProto property_config =
      PropertyConfigBuilder()
          .SetDataType(TYPE_STRING)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/true)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build();

  JoinablePropertyManager::Builder builder(*schema_type_mapper);
  EXPECT_THAT(
      builder.ProcessSchemaTypePropertyConfig(
          /*schema_type_id=*/2, property_config, /*property_path=*/"property"),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(JoinablePropertyManagerBuilderTest,
       NonStringPropertiesWithQualifiedIdJoinableConfigShouldNotProcess) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put("SchemaTypeOne", 0));
  ICING_ASSERT_OK(schema_type_mapper->Put("SchemaTypeTwo", 1));

  // Create non-string properties with QUALIFIED_ID joinable value type.
  std::vector<PropertyConfigProto> properties = {
      PropertyConfigBuilder()
          .SetName("int1")
          .SetDataType(TYPE_INT64)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/true)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build(),
      PropertyConfigBuilder()
          .SetName("int2")
          .SetDataType(TYPE_INT64)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/false)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build(),
      PropertyConfigBuilder()
          .SetName("double1")
          .SetDataType(TYPE_DOUBLE)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/true)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build(),
      PropertyConfigBuilder()
          .SetName("double2")
          .SetDataType(TYPE_DOUBLE)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/false)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build(),
      PropertyConfigBuilder()
          .SetName("boolean1")
          .SetDataType(TYPE_BOOLEAN)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/true)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build(),
      PropertyConfigBuilder()
          .SetName("boolean2")
          .SetDataType(TYPE_BOOLEAN)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/false)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build(),
      PropertyConfigBuilder()
          .SetName("bytes1")
          .SetDataType(TYPE_BYTES)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/true)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build(),
      PropertyConfigBuilder()
          .SetName("bytes2")
          .SetDataType(TYPE_BYTES)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/false)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build(),
      PropertyConfigBuilder()
          .SetName("document1")
          .SetDataTypeDocument(/*schema_type=*/"SchemaTypeTwo",
                               /*index_nested_properties=*/true)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/true)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build(),
      PropertyConfigBuilder()
          .SetName("document2")
          .SetDataTypeDocument(/*schema_type=*/"SchemaTypeTwo",
                               /*index_nested_properties=*/true)
          .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                       /*propagate_delete=*/false)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build()};

  JoinablePropertyManager::Builder builder(*schema_type_mapper);
  for (const PropertyConfigProto& property_config : properties) {
    ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
        /*schema_type_id=*/0, property_config,
        std::string(property_config.property_name())));
  }

  std::unique_ptr<JoinablePropertyManager> joinable_property_manager =
      std::move(builder).Build();
  EXPECT_THAT(joinable_property_manager->GetMetadataList("SchemaTypeOne"),
              IsOkAndHolds(Pointee(IsEmpty())));
}

class JoinablePropertyManagerBuilderWithJoinablePropertyTest
    : public JoinablePropertyManagerBuilderTest,
      public ::testing::WithParamInterface<PropertyConfigProto> {};

TEST_P(JoinablePropertyManagerBuilderWithJoinablePropertyTest, Build) {
  static constexpr std::string_view kSchemaType = "type";
  static constexpr std::string_view kPropertyPath = "foo.bar";
  const PropertyConfigProto& property_config = GetParam();

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put(kSchemaType, 0));

  JoinablePropertyManager::Builder builder(*schema_type_mapper);
  ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
      /*schema_type_id=*/0, property_config, std::string(kPropertyPath)));

  std::unique_ptr<JoinablePropertyManager> joinable_property_manager =
      std::move(builder).Build();
  EXPECT_THAT(
      joinable_property_manager->GetMetadataList(std::string(kSchemaType)),
      IsOkAndHolds(Pointee(ElementsAre(EqualsJoinablePropertyMetadata(
          /*expected_id=*/0, kPropertyPath, property_config)))));
}

// The following type is considered joinable:
// - String with QUALIFIED_ID joinable value type
INSTANTIATE_TEST_SUITE_P(
    JoinablePropertyManagerBuilderWithJoinablePropertyTest,
    JoinablePropertyManagerBuilderWithJoinablePropertyTest,
    testing::Values(PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataType(TYPE_STRING)
                        .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                     /*propagate_delete=*/true)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataType(TYPE_STRING)
                        .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                     /*propagate_delete=*/false)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    // Indexable string can be configured joinable as well. For
                    // convenience, just test one indexable string config.
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                        .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                     /*propagate_delete=*/true)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                        .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                     /*propagate_delete=*/false)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build()));

class JoinablePropertyManagerBuilderWithNonJoinablePropertyTest
    : public JoinablePropertyManagerBuilderTest,
      public ::testing::WithParamInterface<PropertyConfigProto> {};

TEST_P(JoinablePropertyManagerBuilderWithNonJoinablePropertyTest, Build) {
  static constexpr std::string_view kSchemaType = "type";
  static constexpr std::string_view kPropertyPath = "foo.bar";
  const PropertyConfigProto& property_config = GetParam();

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put(kSchemaType, 0));

  JoinablePropertyManager::Builder builder(*schema_type_mapper);
  ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
      /*schema_type_id=*/0, property_config, std::string(kPropertyPath)));

  std::unique_ptr<JoinablePropertyManager> joinable_property_manager =
      std::move(builder).Build();
  EXPECT_THAT(
      joinable_property_manager->GetMetadataList(std::string(kSchemaType)),
      IsOkAndHolds(Pointee(IsEmpty())));
}

// All types without JoinableConfig (i.e. joinable value type = NONE by default)
// are considered non-joinable. Other mismatching types (e.g. non-string
// properties with QUALIFIED_ID joinable value type) were tested individually
// above.
INSTANTIATE_TEST_SUITE_P(
    JoinablePropertyManagerBuilderWithNonJoinablePropertyTest,
    JoinablePropertyManagerBuilderWithNonJoinablePropertyTest,
    testing::Values(PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataType(TYPE_STRING)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    // Indexable but non-joinable string
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataType(TYPE_INT64)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataType(TYPE_DOUBLE)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataType(TYPE_BOOLEAN)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataType(TYPE_BYTES)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeDocument("anotherSchema",
                                             /*index_nested_properties=*/true)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeDocument("anotherSchema",
                                             /*index_nested_properties=*/false)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build()));

}  // namespace

}  // namespace lib
}  // namespace icing
