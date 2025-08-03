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

#include "icing/schema/backup-schema-producer.h"

#include <string>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/portable/equals-proto.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-type-manager.h"
#include "icing/schema/schema-util.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/dynamic-trie-key-mapper.h"
#include "icing/store/key-mapper.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;
using ::testing::Pointee;
using ::testing::SizeIs;

class BackupSchemaProducerTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_dir_ = GetTestTempDir() + "/icing";
    schema_store_dir_ = test_dir_ + "/schema_store";
    filesystem_.CreateDirectoryRecursively(schema_store_dir_.c_str());
  }

  void TearDown() override {
    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(test_dir_.c_str()));
  }

  Filesystem filesystem_;
  std::string test_dir_;
  std::string schema_store_dir_;
};

TEST_F(BackupSchemaProducerTest, EmptySchema) {
  SchemaProto empty;
  SchemaUtil::TypeConfigMap type_config_map;
  SchemaUtil::BuildTypeConfigMap(empty, &type_config_map);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DynamicTrieKeyMapper<SchemaTypeId>> type_id_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, schema_store_dir_,
                                                 /*maximum_size_bytes=*/10000));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, type_id_mapper.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      BackupSchemaProducer backup_producer,
      BackupSchemaProducer::Create(empty,
                                   schema_type_manager->section_manager()));
  EXPECT_THAT(backup_producer.is_backup_necessary(), Eq(false));
}

TEST_F(BackupSchemaProducerTest, NoIndexedPropertySchema) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TypeA")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetCardinality(CARDINALITY_OPTIONAL)
                                        .SetDataType(TYPE_STRING))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetCardinality(CARDINALITY_REQUIRED)
                                        .SetDataType(TYPE_INT64)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TypeB")
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("prop3")
                               .SetCardinality(CARDINALITY_OPTIONAL)
                               .SetDataTypeDocument(
                                   "TypeA", /*index_nested_properties=*/false))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop4")
                                        .SetCardinality(CARDINALITY_REPEATED)
                                        .SetDataType(TYPE_STRING)))
          .Build();

  SchemaUtil::TypeConfigMap type_config_map;
  SchemaUtil::BuildTypeConfigMap(schema, &type_config_map);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DynamicTrieKeyMapper<SchemaTypeId>> type_id_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, schema_store_dir_,
                                                 /*maximum_size_bytes=*/10000));
  ASSERT_THAT(type_id_mapper->Put("TypeA", 0), IsOk());
  ASSERT_THAT(type_id_mapper->Put("TypeB", 1), IsOk());
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, type_id_mapper.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      BackupSchemaProducer backup_producer,
      BackupSchemaProducer::Create(schema,
                                   schema_type_manager->section_manager()));
  EXPECT_THAT(backup_producer.is_backup_necessary(), Eq(false));
}

TEST_F(BackupSchemaProducerTest, RollbackCompatibleSchema) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TypeA")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetCardinality(CARDINALITY_OPTIONAL)
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetCardinality(CARDINALITY_REQUIRED)
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TypeB")
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("prop3")
                               .SetCardinality(CARDINALITY_OPTIONAL)
                               .SetDataTypeDocument(
                                   "TypeA", /*index_nested_properties=*/true))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop4")
                                        .SetCardinality(CARDINALITY_REPEATED)
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_VERBATIM)))
          .Build();

  SchemaUtil::TypeConfigMap type_config_map;
  SchemaUtil::BuildTypeConfigMap(schema, &type_config_map);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DynamicTrieKeyMapper<SchemaTypeId>> type_id_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, schema_store_dir_,
                                                 /*maximum_size_bytes=*/10000));
  ASSERT_THAT(type_id_mapper->Put("TypeA", 0), IsOk());
  ASSERT_THAT(type_id_mapper->Put("TypeB", 1), IsOk());
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, type_id_mapper.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      BackupSchemaProducer backup_producer,
      BackupSchemaProducer::Create(schema,
                                   schema_type_manager->section_manager()));
  EXPECT_THAT(backup_producer.is_backup_necessary(), Eq(false));
}

TEST_F(BackupSchemaProducerTest, RemoveRfc822) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("TypeA").AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop1")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822)))
          .Build();

  SchemaUtil::TypeConfigMap type_config_map;
  SchemaUtil::BuildTypeConfigMap(schema, &type_config_map);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DynamicTrieKeyMapper<SchemaTypeId>> type_id_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, schema_store_dir_,
                                                 /*maximum_size_bytes=*/10000));
  ASSERT_THAT(type_id_mapper->Put("TypeA", 0), IsOk());
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, type_id_mapper.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      BackupSchemaProducer backup_producer,
      BackupSchemaProducer::Create(schema,
                                   schema_type_manager->section_manager()));
  EXPECT_THAT(backup_producer.is_backup_necessary(), Eq(true));
  SchemaProto backup = std::move(backup_producer).Produce();

  SchemaProto expected_backup =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("TypeA").AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop1")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataType(TYPE_STRING)))
          .Build();
  EXPECT_THAT(backup, portable_equals_proto::EqualsProto(expected_backup));
}

TEST_F(BackupSchemaProducerTest, MakeExtraStringIndexedPropertiesUnindexed) {
  PropertyConfigBuilder indexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN);
  SchemaTypeConfigProto type =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(indexed_string_property_builder.SetName("prop1"))
          .AddProperty(indexed_string_property_builder.SetName("prop2"))
          .AddProperty(indexed_string_property_builder.SetName("prop3"))
          .AddProperty(indexed_string_property_builder.SetName("prop4"))
          .AddProperty(indexed_string_property_builder.SetName("prop5"))
          .AddProperty(indexed_string_property_builder.SetName("prop6"))
          .AddProperty(indexed_string_property_builder.SetName("prop7"))
          .AddProperty(indexed_string_property_builder.SetName("prop8"))
          .AddProperty(indexed_string_property_builder.SetName("prop9"))
          .AddProperty(indexed_string_property_builder.SetName("prop10"))
          .AddProperty(indexed_string_property_builder.SetName("prop11"))
          .AddProperty(indexed_string_property_builder.SetName("prop12"))
          .AddProperty(indexed_string_property_builder.SetName("prop13"))
          .AddProperty(indexed_string_property_builder.SetName("prop14"))
          .AddProperty(indexed_string_property_builder.SetName("prop15"))
          .AddProperty(indexed_string_property_builder.SetName("prop16"))
          .AddProperty(indexed_string_property_builder.SetName("prop17"))
          .AddProperty(indexed_string_property_builder.SetName("prop18"))
          .AddProperty(indexed_string_property_builder.SetName("prop19"))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type).Build();

  SchemaUtil::TypeConfigMap type_config_map;
  SchemaUtil::BuildTypeConfigMap(schema, &type_config_map);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DynamicTrieKeyMapper<SchemaTypeId>> type_id_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, schema_store_dir_,
                                                 /*maximum_size_bytes=*/10000));
  ASSERT_THAT(type_id_mapper->Put("TypeA", 0), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, type_id_mapper.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      BackupSchemaProducer backup_producer,
      BackupSchemaProducer::Create(schema,
                                   schema_type_manager->section_manager()));
  EXPECT_THAT(backup_producer.is_backup_necessary(), Eq(true));
  SchemaProto backup = std::move(backup_producer).Produce();

  PropertyConfigBuilder unindexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataType(TYPE_STRING);
  SchemaTypeConfigProto expected_type =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(indexed_string_property_builder.SetName("prop1"))
          .AddProperty(indexed_string_property_builder.SetName("prop2"))
          .AddProperty(indexed_string_property_builder.SetName("prop3"))
          .AddProperty(indexed_string_property_builder.SetName("prop4"))
          .AddProperty(indexed_string_property_builder.SetName("prop5"))
          .AddProperty(indexed_string_property_builder.SetName("prop6"))
          .AddProperty(indexed_string_property_builder.SetName("prop7"))
          .AddProperty(indexed_string_property_builder.SetName("prop8"))
          .AddProperty(indexed_string_property_builder.SetName("prop9"))
          .AddProperty(indexed_string_property_builder.SetName("prop10"))
          .AddProperty(indexed_string_property_builder.SetName("prop11"))
          .AddProperty(indexed_string_property_builder.SetName("prop12"))
          .AddProperty(indexed_string_property_builder.SetName("prop13"))
          .AddProperty(indexed_string_property_builder.SetName("prop14"))
          .AddProperty(indexed_string_property_builder.SetName("prop15"))
          .AddProperty(unindexed_string_property_builder.SetName("prop16"))
          .AddProperty(unindexed_string_property_builder.SetName("prop17"))
          .AddProperty(unindexed_string_property_builder.SetName("prop18"))
          .AddProperty(unindexed_string_property_builder.SetName("prop19"))
          .Build();
  SchemaProto expected_backup = SchemaBuilder().AddType(expected_type).Build();
  EXPECT_THAT(backup, portable_equals_proto::EqualsProto(expected_backup));
}

TEST_F(BackupSchemaProducerTest, MakeExtraIntIndexedPropertiesUnindexed) {
  PropertyConfigBuilder indexed_int_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeInt64(NUMERIC_MATCH_RANGE);
  SchemaTypeConfigProto type =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(indexed_int_property_builder.SetName("prop0"))
          .AddProperty(indexed_int_property_builder.SetName("prop1"))
          .AddProperty(indexed_int_property_builder.SetName("prop2"))
          .AddProperty(indexed_int_property_builder.SetName("prop3"))
          .AddProperty(indexed_int_property_builder.SetName("prop4"))
          .AddProperty(indexed_int_property_builder.SetName("prop5"))
          .AddProperty(indexed_int_property_builder.SetName("prop6"))
          .AddProperty(indexed_int_property_builder.SetName("prop7"))
          .AddProperty(indexed_int_property_builder.SetName("prop8"))
          .AddProperty(indexed_int_property_builder.SetName("prop9"))
          .AddProperty(indexed_int_property_builder.SetName("prop10"))
          .AddProperty(indexed_int_property_builder.SetName("prop11"))
          .AddProperty(indexed_int_property_builder.SetName("prop12"))
          .AddProperty(indexed_int_property_builder.SetName("prop13"))
          .AddProperty(indexed_int_property_builder.SetName("prop14"))
          .AddProperty(indexed_int_property_builder.SetName("prop15"))
          .AddProperty(indexed_int_property_builder.SetName("prop16"))
          .AddProperty(indexed_int_property_builder.SetName("prop17"))
          .AddProperty(indexed_int_property_builder.SetName("prop18"))
          .AddProperty(indexed_int_property_builder.SetName("prop19"))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type).Build();

  SchemaUtil::TypeConfigMap type_config_map;
  SchemaUtil::BuildTypeConfigMap(schema, &type_config_map);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DynamicTrieKeyMapper<SchemaTypeId>> type_id_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, schema_store_dir_,
                                                 /*maximum_size_bytes=*/10000));
  ASSERT_THAT(type_id_mapper->Put("TypeA", 0), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, type_id_mapper.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      BackupSchemaProducer backup_producer,
      BackupSchemaProducer::Create(schema,
                                   schema_type_manager->section_manager()));
  EXPECT_THAT(backup_producer.is_backup_necessary(), Eq(true));
  SchemaProto backup = std::move(backup_producer).Produce();

  PropertyConfigBuilder unindexed_int_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataType(TYPE_INT64);
  SchemaTypeConfigProto expected_type =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(indexed_int_property_builder.SetName("prop0"))
          .AddProperty(indexed_int_property_builder.SetName("prop1"))
          .AddProperty(indexed_int_property_builder.SetName("prop2"))
          .AddProperty(indexed_int_property_builder.SetName("prop3"))
          .AddProperty(indexed_int_property_builder.SetName("prop4"))
          .AddProperty(indexed_int_property_builder.SetName("prop5"))
          .AddProperty(indexed_int_property_builder.SetName("prop6"))
          .AddProperty(indexed_int_property_builder.SetName("prop7"))
          .AddProperty(indexed_int_property_builder.SetName("prop8"))
          .AddProperty(indexed_int_property_builder.SetName("prop9"))
          .AddProperty(indexed_int_property_builder.SetName("prop10"))
          .AddProperty(indexed_int_property_builder.SetName("prop11"))
          .AddProperty(indexed_int_property_builder.SetName("prop12"))
          .AddProperty(indexed_int_property_builder.SetName("prop13"))
          .AddProperty(indexed_int_property_builder.SetName("prop14"))
          .AddProperty(indexed_int_property_builder.SetName("prop15"))
          .AddProperty(unindexed_int_property_builder.SetName("prop16"))
          .AddProperty(unindexed_int_property_builder.SetName("prop17"))
          .AddProperty(unindexed_int_property_builder.SetName("prop18"))
          .AddProperty(unindexed_int_property_builder.SetName("prop19"))
          .Build();
  SchemaProto expected_backup = SchemaBuilder().AddType(expected_type).Build();
  EXPECT_THAT(backup, portable_equals_proto::EqualsProto(expected_backup));
}

TEST_F(BackupSchemaProducerTest, MakeExtraDocumentIndexedPropertiesUnindexed) {
  PropertyConfigBuilder indexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN);
  SchemaTypeConfigProto typeB =
      SchemaTypeConfigBuilder()
          .SetType("TypeB")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(indexed_string_property_builder.SetName("prop1"))
          .AddProperty(indexed_string_property_builder.SetName("prop2"))
          .AddProperty(indexed_string_property_builder.SetName("prop3"))
          .AddProperty(indexed_string_property_builder.SetName("prop4"))
          .AddProperty(indexed_string_property_builder.SetName("prop5"))
          .AddProperty(indexed_string_property_builder.SetName("prop6"))
          .AddProperty(indexed_string_property_builder.SetName("prop7"))
          .AddProperty(indexed_string_property_builder.SetName("prop8"))
          .AddProperty(indexed_string_property_builder.SetName("prop9"))
          .Build();

  PropertyConfigBuilder indexed_document_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeDocument("TypeB", /*index_nested_properties=*/true);
  SchemaTypeConfigProto typeA =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(indexed_document_property_builder.SetName("propA"))
          .AddProperty(indexed_document_property_builder.SetName("propB"))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(typeA).AddType(typeB).Build();

  SchemaUtil::TypeConfigMap type_config_map;
  SchemaUtil::BuildTypeConfigMap(schema, &type_config_map);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DynamicTrieKeyMapper<SchemaTypeId>> type_id_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, schema_store_dir_,
                                                 /*maximum_size_bytes=*/10000));
  ASSERT_THAT(type_id_mapper->Put("TypeA", 0), IsOk());
  ASSERT_THAT(type_id_mapper->Put("TypeB", 1), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, type_id_mapper.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      BackupSchemaProducer backup_producer,
      BackupSchemaProducer::Create(schema,
                                   schema_type_manager->section_manager()));
  EXPECT_THAT(backup_producer.is_backup_necessary(), Eq(true));
  SchemaProto backup = std::move(backup_producer).Produce();

  PropertyConfigProto unindexed_document_property =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataType(TYPE_DOCUMENT)
          .Build();
  unindexed_document_property.set_schema_type("TypeB");
  PropertyConfigBuilder unindexed_document_property_builder(
      unindexed_document_property);
  SchemaTypeConfigProto expected_typeA =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(indexed_document_property_builder.SetName("propA"))
          .AddProperty(unindexed_document_property_builder.SetName("propB"))
          .Build();
  SchemaProto expected_backup =
      SchemaBuilder().AddType(expected_typeA).AddType(typeB).Build();
  EXPECT_THAT(backup, portable_equals_proto::EqualsProto(expected_backup));
}

TEST_F(
    BackupSchemaProducerTest,
    MakeExtraDocumentIndexedPropertiesWithIndexableNestedPropertiesListUnindexed) {
  PropertyConfigBuilder indexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN);
  PropertyConfigBuilder indexed_int_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeInt64(NUMERIC_MATCH_RANGE);
  SchemaTypeConfigProto typeB =
      SchemaTypeConfigBuilder()
          .SetType("TypeB")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(indexed_int_property_builder.SetName("prop1"))
          .AddProperty(indexed_string_property_builder.SetName("prop2"))
          .AddProperty(indexed_int_property_builder.SetName("prop3"))
          .AddProperty(indexed_string_property_builder.SetName("prop4"))
          .AddProperty(indexed_int_property_builder.SetName("prop5"))
          .AddProperty(indexed_string_property_builder.SetName("prop6"))
          .AddProperty(indexed_int_property_builder.SetName("prop7"))
          .AddProperty(indexed_string_property_builder.SetName("prop8"))
          .AddProperty(indexed_int_property_builder.SetName("prop9"))
          .Build();

  // Create indexed document property by using indexable nested properties list.
  PropertyConfigBuilder indexed_document_property_with_list_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeDocument(
              "TypeB", /*indexable_nested_properties_list=*/{
                  "prop0", "prop1", "prop2", "prop3", "prop4", "prop5",
                  "unknown1", "unknown2", "unknown3"});
  SchemaTypeConfigProto typeA =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(
              indexed_document_property_with_list_builder.SetName("propA"))
          .AddProperty(
              indexed_document_property_with_list_builder.SetName("propB"))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(typeA).AddType(typeB).Build();

  SchemaUtil::TypeConfigMap type_config_map;
  SchemaUtil::BuildTypeConfigMap(schema, &type_config_map);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DynamicTrieKeyMapper<SchemaTypeId>> type_id_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, schema_store_dir_,
                                                 /*maximum_size_bytes=*/10000));
  ASSERT_THAT(type_id_mapper->Put("TypeA", 0), IsOk());
  ASSERT_THAT(type_id_mapper->Put("TypeB", 1), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, type_id_mapper.get()));
  ASSERT_THAT(schema_type_manager->section_manager().GetMetadataList("TypeA"),
              IsOkAndHolds(Pointee(SizeIs(18))));

  ICING_ASSERT_OK_AND_ASSIGN(
      BackupSchemaProducer backup_producer,
      BackupSchemaProducer::Create(schema,
                                   schema_type_manager->section_manager()));
  EXPECT_THAT(backup_producer.is_backup_necessary(), Eq(true));
  SchemaProto backup = std::move(backup_producer).Produce();

  PropertyConfigProto unindexed_document_property =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataType(TYPE_DOCUMENT)
          .Build();
  unindexed_document_property.set_schema_type("TypeB");
  PropertyConfigBuilder unindexed_document_property_builder(
      unindexed_document_property);

  // "propA" and "propB" both have 9 sections respectively, so we have to drop
  // "propB" indexing config to make total # of sections <= 16.
  SchemaTypeConfigProto expected_typeA =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(
              indexed_document_property_with_list_builder.SetName("propA"))
          .AddProperty(unindexed_document_property_builder.SetName("propB"))
          .Build();
  SchemaProto expected_backup =
      SchemaBuilder().AddType(expected_typeA).AddType(typeB).Build();
  EXPECT_THAT(backup, portable_equals_proto::EqualsProto(expected_backup));
}

TEST_F(BackupSchemaProducerTest, MakeRfcPropertiesUnindexedFirst) {
  PropertyConfigBuilder indexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN);
  SchemaTypeConfigProto typeA =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(indexed_string_property_builder.SetName("prop1"))
          .AddProperty(indexed_string_property_builder.SetName("prop2"))
          .AddProperty(indexed_string_property_builder.SetName("prop3"))
          .AddProperty(indexed_string_property_builder.SetName("prop4"))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .AddProperty(indexed_string_property_builder.SetName("prop6"))
          .AddProperty(indexed_string_property_builder.SetName("prop7"))
          .AddProperty(indexed_string_property_builder.SetName("prop8"))
          .AddProperty(indexed_string_property_builder.SetName("prop9"))
          .AddProperty(indexed_string_property_builder.SetName("prop10"))
          .AddProperty(indexed_string_property_builder.SetName("prop11"))
          .AddProperty(indexed_string_property_builder.SetName("prop12"))
          .AddProperty(indexed_string_property_builder.SetName("prop13"))
          .AddProperty(indexed_string_property_builder.SetName("prop14"))
          .AddProperty(indexed_string_property_builder.SetName("prop15"))
          .AddProperty(indexed_string_property_builder.SetName("prop16"))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(typeA).Build();

  SchemaUtil::TypeConfigMap type_config_map;
  SchemaUtil::BuildTypeConfigMap(schema, &type_config_map);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DynamicTrieKeyMapper<SchemaTypeId>> type_id_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, schema_store_dir_,
                                                 /*maximum_size_bytes=*/10000));
  ASSERT_THAT(type_id_mapper->Put("TypeA", 0), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, type_id_mapper.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      BackupSchemaProducer backup_producer,
      BackupSchemaProducer::Create(schema,
                                   schema_type_manager->section_manager()));
  EXPECT_THAT(backup_producer.is_backup_necessary(), Eq(true));
  SchemaProto backup = std::move(backup_producer).Produce();

  SchemaTypeConfigProto expected_typeA =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(indexed_string_property_builder.SetName("prop1"))
          .AddProperty(indexed_string_property_builder.SetName("prop2"))
          .AddProperty(indexed_string_property_builder.SetName("prop3"))
          .AddProperty(indexed_string_property_builder.SetName("prop4"))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("propRfc")
                           .SetCardinality(CARDINALITY_OPTIONAL)
                           .SetDataType(TYPE_STRING))
          .AddProperty(indexed_string_property_builder.SetName("prop6"))
          .AddProperty(indexed_string_property_builder.SetName("prop7"))
          .AddProperty(indexed_string_property_builder.SetName("prop8"))
          .AddProperty(indexed_string_property_builder.SetName("prop9"))
          .AddProperty(indexed_string_property_builder.SetName("prop10"))
          .AddProperty(indexed_string_property_builder.SetName("prop11"))
          .AddProperty(indexed_string_property_builder.SetName("prop12"))
          .AddProperty(indexed_string_property_builder.SetName("prop13"))
          .AddProperty(indexed_string_property_builder.SetName("prop14"))
          .AddProperty(indexed_string_property_builder.SetName("prop15"))
          .AddProperty(indexed_string_property_builder.SetName("prop16"))
          .Build();
  SchemaProto expected_backup = SchemaBuilder().AddType(expected_typeA).Build();
  EXPECT_THAT(backup, portable_equals_proto::EqualsProto(expected_backup));
}

TEST_F(BackupSchemaProducerTest, MakeExtraPropertiesUnindexedMultipleTypes) {
  PropertyConfigBuilder indexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN);
  PropertyConfigBuilder indexed_int_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeInt64(NUMERIC_MATCH_RANGE);
  SchemaTypeConfigProto typeB =
      SchemaTypeConfigBuilder()
          .SetType("TypeB")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(indexed_int_property_builder.SetName("prop1"))
          .AddProperty(indexed_string_property_builder.SetName("prop2"))
          .AddProperty(indexed_int_property_builder.SetName("prop3"))
          .AddProperty(indexed_string_property_builder.SetName("prop4"))
          .Build();

  PropertyConfigBuilder indexed_document_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeDocument("TypeB", /*index_nested_properties=*/true);
  PropertyConfigBuilder indexed_document_property_with_list_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeDocument(
              "TypeB", /*indexable_nested_properties_list=*/{
                  "prop0", "prop4", "unknown1", "unknown2", "unknown3"});
  SchemaTypeConfigProto typeA =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(indexed_string_property_builder.SetName("propA"))
          .AddProperty(
              indexed_document_property_with_list_builder.SetName("propB"))
          .AddProperty(indexed_string_property_builder.SetName("propC"))
          .AddProperty(indexed_document_property_builder.SetName("propD"))
          .AddProperty(indexed_string_property_builder.SetName("propE"))
          .AddProperty(indexed_int_property_builder.SetName("propF"))
          .AddProperty(indexed_document_property_builder.SetName("propG"))
          .AddProperty(indexed_string_property_builder.SetName("propH"))
          .AddProperty(indexed_int_property_builder.SetName("propI"))
          .AddProperty(
              indexed_document_property_with_list_builder.SetName("propJ"))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(typeA).AddType(typeB).Build();

  SchemaUtil::TypeConfigMap type_config_map;
  SchemaUtil::BuildTypeConfigMap(schema, &type_config_map);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DynamicTrieKeyMapper<SchemaTypeId>> type_id_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, schema_store_dir_,
                                                 /*maximum_size_bytes=*/10000));
  ASSERT_THAT(type_id_mapper->Put("TypeA", 0), IsOk());
  ASSERT_THAT(type_id_mapper->Put("TypeB", 1), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, type_id_mapper.get()));
  ASSERT_THAT(schema_type_manager->section_manager().GetMetadataList("TypeA"),
              IsOkAndHolds(Pointee(SizeIs(26))));

  ICING_ASSERT_OK_AND_ASSIGN(
      BackupSchemaProducer backup_producer,
      BackupSchemaProducer::Create(schema,
                                   schema_type_manager->section_manager()));
  EXPECT_THAT(backup_producer.is_backup_necessary(), Eq(true));
  SchemaProto backup = std::move(backup_producer).Produce();

  PropertyConfigBuilder unindexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataType(TYPE_STRING);
  PropertyConfigBuilder unindexed_int_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataType(TYPE_INT64);
  PropertyConfigProto unindexed_document_property =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataType(TYPE_DOCUMENT)
          .Build();
  unindexed_document_property.set_schema_type("TypeB");
  PropertyConfigBuilder unindexed_document_property_builder(
      unindexed_document_property);

  // On version 0 (Android T):
  // - Only "propA", "propC", "propD.prop0", "propD.prop1", "propD.prop2",
  //   "propD.prop3", "propD.prop4", "propE", "propF" will be assigned sections.
  // - Unlike version 2, "propB.prop0", "propB.prop4", "propB.unknown1",
  //   "propB.unknown2", "propB.unknown3" will be ignored because version 0
  //   doesn't recognize indexable nested properties list.
  // - So there will be only 9 sections on version 0. We still have potential to
  //   avoid dropping "propG", "propH", "propI" indexing configs on version 0
  //   (in this case it will be 16 sections), but it is ok to make it simple as
  //   long as total # of sections <= 16.
  SchemaTypeConfigProto expected_typeA =
      SchemaTypeConfigBuilder()
          .SetType("TypeA")
          .AddProperty(indexed_string_property_builder.SetName("propA"))
          .AddProperty(
              indexed_document_property_with_list_builder.SetName("propB"))
          .AddProperty(indexed_string_property_builder.SetName("propC"))
          .AddProperty(indexed_document_property_builder.SetName("propD"))
          .AddProperty(indexed_string_property_builder.SetName("propE"))
          .AddProperty(indexed_int_property_builder.SetName("propF"))
          .AddProperty(unindexed_document_property_builder.SetName("propG"))
          .AddProperty(unindexed_string_property_builder.SetName("propH"))
          .AddProperty(unindexed_int_property_builder.SetName("propI"))
          .AddProperty(unindexed_document_property_builder.SetName("propJ"))
          .Build();
  SchemaProto expected_backup =
      SchemaBuilder().AddType(expected_typeA).AddType(typeB).Build();
  EXPECT_THAT(backup, portable_equals_proto::EqualsProto(expected_backup));
}

}  // namespace

}  // namespace lib
}  // namespace icing
