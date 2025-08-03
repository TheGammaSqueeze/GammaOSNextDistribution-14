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
#include "icing/schema/section-manager.h"
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

class SectionManagerBuilderTest : public ::testing::Test {
 protected:
  void SetUp() override { test_dir_ = GetTestTempDir() + "/icing"; }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  Filesystem filesystem_;
  std::string test_dir_;
};

TEST_F(SectionManagerBuilderTest, Build) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put("typeOne", 0));
  ICING_ASSERT_OK(schema_type_mapper->Put("typeTwo", 1));

  PropertyConfigProto prop_foo =
      PropertyConfigBuilder()
          .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_OPTIONAL)
          .Build();
  PropertyConfigProto prop_bar =
      PropertyConfigBuilder()
          .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build();
  PropertyConfigProto prop_baz =
      PropertyConfigBuilder()
          .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_OPTIONAL)
          .Build();

  SectionManager::Builder builder(*schema_type_mapper);
  ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
      /*schema_type_id=*/0, prop_foo, /*property_path=*/"foo"));
  ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
      /*schema_type_id=*/0, prop_bar, /*property_path=*/"bar"));
  ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
      /*schema_type_id=*/1, prop_baz, /*property_path=*/"baz"));

  std::unique_ptr<SectionManager> section_manager = std::move(builder).Build();
  // Check "typeOne"
  EXPECT_THAT(
      section_manager->GetMetadataList("typeOne"),
      IsOkAndHolds(Pointee(ElementsAre(
          EqualsSectionMetadata(/*expected_id=*/0,
                                /*expected_property_path=*/"foo", prop_foo),
          EqualsSectionMetadata(/*expected_id=*/1,
                                /*expected_property_path=*/"bar", prop_bar)))));
  // Check "typeTwo"
  EXPECT_THAT(section_manager->GetMetadataList("typeTwo"),
              IsOkAndHolds(Pointee(ElementsAre(EqualsSectionMetadata(
                  /*expected_id=*/0,
                  /*expected_property_path=*/"baz", prop_baz)))));
}

TEST_F(SectionManagerBuilderTest, TooManyPropertiesShouldFail) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put("type", 0));

  SectionManager::Builder builder(*schema_type_mapper);
  // Add kTotalNumSections indexable properties
  for (int i = 0; i < kTotalNumSections; i++) {
    PropertyConfigProto property_config =
        PropertyConfigBuilder()
            .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
            .SetCardinality(CARDINALITY_REQUIRED)
            .Build();
    ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
        /*schema_type_id=*/0, property_config,
        /*property_path=*/"property" + std::to_string(i)));
  }

  // Add another indexable property. This should fail.
  PropertyConfigProto property_config =
      PropertyConfigBuilder()
          .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build();
  EXPECT_THAT(builder.ProcessSchemaTypePropertyConfig(
                  /*schema_type_id=*/0, property_config,
                  /*property_path=*/"propertyExceed"),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE,
                       HasSubstr("Too many properties")));
}

TEST_F(SectionManagerBuilderTest, InvalidSchemaTypeIdShouldFail) {
  // Create a schema type mapper with invalid schema type id.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put("type", 0));

  PropertyConfigProto property_config =
      PropertyConfigBuilder()
          .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build();

  SectionManager::Builder builder(*schema_type_mapper);
  EXPECT_THAT(
      builder.ProcessSchemaTypePropertyConfig(
          /*schema_type_id=*/-1, property_config, /*property_path=*/"property"),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(SectionManagerBuilderTest,
       SchemaTypeIdInconsistentWithSchemaTypeMapperSizeShouldFail) {
  // Create a schema type mapper with schema type id = 2, but size of mapper is
  // 2.
  // Since SectionManagerBuilder expects 2 schema type ids = [0, 1], building
  // with schema type id = 2 should fail even though id = 2 is in schema type
  // mapper.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put("typeOne", 0));
  ICING_ASSERT_OK(schema_type_mapper->Put("typeTwo", 2));

  PropertyConfigProto property_config =
      PropertyConfigBuilder()
          .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_REQUIRED)
          .Build();

  SectionManager::Builder builder(*schema_type_mapper);
  EXPECT_THAT(
      builder.ProcessSchemaTypePropertyConfig(
          /*schema_type_id=*/2, property_config, /*property_path=*/"property"),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

class IndexableSectionManagerBuilderTest
    : public SectionManagerBuilderTest,
      public ::testing::WithParamInterface<PropertyConfigProto> {};

TEST_P(IndexableSectionManagerBuilderTest, Build) {
  static constexpr std::string_view kSchemaType = "type";
  static constexpr std::string_view kPropertyPath = "foo.bar";
  const PropertyConfigProto& property_config = GetParam();

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put(kSchemaType, 0));

  SectionManager::Builder builder(*schema_type_mapper);
  ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
      /*schema_type_id=*/0, property_config, std::string(kPropertyPath)));

  std::unique_ptr<SectionManager> section_manager = std::move(builder).Build();
  EXPECT_THAT(section_manager->GetMetadataList(std::string(kSchemaType)),
              IsOkAndHolds(Pointee(ElementsAre(EqualsSectionMetadata(
                  /*expected_id=*/0, kPropertyPath, property_config)))));
}

// The following types are considered indexable:
// - String with valid TermMatchType and TokenizerType
// - Int64 with valid NumericMatchType
INSTANTIATE_TEST_SUITE_P(
    IndexableSectionManagerBuilderTest, IndexableSectionManagerBuilderTest,
    testing::Values(PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_VERBATIM)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_RFC822)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                           TOKENIZER_VERBATIM)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build()));

class NonIndexableSectionManagerBuilderTest
    : public SectionManagerBuilderTest,
      public ::testing::WithParamInterface<PropertyConfigProto> {};

TEST_P(NonIndexableSectionManagerBuilderTest, Build) {
  static constexpr std::string_view kSchemaType = "type";
  static constexpr std::string_view kPropertyPath = "foo.bar";
  const PropertyConfigProto& property_config = GetParam();

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put(kSchemaType, 0));

  SectionManager::Builder builder(*schema_type_mapper);
  ICING_ASSERT_OK(builder.ProcessSchemaTypePropertyConfig(
      /*schema_type_id=*/0, property_config, std::string(kPropertyPath)));

  // NonIndexable sections will still consume a sectionId.
  std::unique_ptr<SectionManager> section_manager = std::move(builder).Build();
  EXPECT_THAT(section_manager->GetMetadataList(std::string(kSchemaType)),
              IsOkAndHolds(Pointee(ElementsAre(EqualsSectionMetadata(
                  /*expected_id=*/0, kPropertyPath, property_config)))));
}

// The following types are considered non-indexable:
// - String with TERM_MATCH_UNKNOWN or TOKENIZER_NONE
// - Int64 with NUMERIC_MATCH_UNKNOWN
// - Double
// - Boolean
// - Bytes
// - Document
INSTANTIATE_TEST_SUITE_P(
    NonIndexableSectionManagerBuilderTest,
    NonIndexableSectionManagerBuilderTest,
    testing::Values(PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_PLAIN)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_NONE)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeInt64(NUMERIC_MATCH_UNKNOWN)
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
                                             /*index_nested_properties=*/false)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build(),
                    PropertyConfigBuilder()
                        .SetName("property")
                        .SetDataTypeDocument("anotherSchema",
                                             /*index_nested_properties=*/true)
                        .SetCardinality(CARDINALITY_OPTIONAL)
                        .Build()));

}  // namespace

}  // namespace lib
}  // namespace icing
