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

#include "icing/schema/schema-type-manager.h"

#include <memory>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-util.h"
#include "icing/schema/section.h"
#include "icing/store/dynamic-trie-key-mapper.h"
#include "icing/store/key-mapper.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Pointee;

// type and property names of EmailMessage
static constexpr char kTypeEmail[] = "EmailMessage";
static constexpr SchemaTypeId kTypeEmailSchemaId = 0;
// indexable (in lexicographical order)
static constexpr char kPropertyRecipientIds[] = "recipientIds";
static constexpr char kPropertyRecipients[] = "recipients";
static constexpr char kPropertySenderQualifiedId[] =
    "senderQualifiedId";  // QUALIFIED_ID joinable
static constexpr char kPropertySubject[] = "subject";
static constexpr char kPropertyTimestamp[] = "timestamp";
// non-indexable
static constexpr char kPropertyAttachment[] = "attachment";
static constexpr char kPropertyNonIndexableInteger[] = "nonIndexableInteger";
static constexpr char kPropertyTagQualifiedId[] =
    "tagQualifiedId";  // QUALIFIED_ID joinable
static constexpr char kPropertyText[] = "text";

// type and property names of Conversation
static constexpr char kTypeConversation[] = "Conversation";
static constexpr SchemaTypeId kTypeConversationSchemaId = 1;
// indexable (in lexicographical order)
static constexpr char kPropertyEmails[] = "emails";
static constexpr char kPropertyGroupQualifiedId[] =
    "groupQualifiedId";  // QUALIFIED_ID joinable
static constexpr char kPropertyName[] = "name";
// non-indexable
static constexpr char kPropertyNestedNonIndexable[] = "nestedNonIndexable";
static constexpr char kPropertySuperTagQualifiedId[] =
    "superTagQualifiedId";  // QUALIFIED_ID joinable

PropertyConfigProto CreateReceipientIdsPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertyRecipientIds)
      .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
      .SetCardinality(CARDINALITY_REPEATED)
      .Build();
}

PropertyConfigProto CreateRecipientsPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertyRecipients)
      .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
      .SetCardinality(CARDINALITY_REPEATED)
      .Build();
}

PropertyConfigProto CreateSenderQualifiedIdPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertySenderQualifiedId)
      .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
      .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID, /*propagate_delete=*/true)
      .SetCardinality(CARDINALITY_REQUIRED)
      .Build();
}

PropertyConfigProto CreateSubjectPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertySubject)
      .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
      .SetCardinality(CARDINALITY_REQUIRED)
      .Build();
}

PropertyConfigProto CreateTimestampPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertyTimestamp)
      .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
      .SetCardinality(CARDINALITY_REQUIRED)
      .Build();
}

PropertyConfigProto CreateTagQualifiedIdPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertyTagQualifiedId)
      .SetDataType(TYPE_STRING)
      .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID, /*propagate_delete=*/true)
      .SetCardinality(CARDINALITY_REQUIRED)
      .Build();
}

PropertyConfigProto CreateGroupQualifiedIdPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertyGroupQualifiedId)
      .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
      .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID, /*propagate_delete=*/true)
      .SetCardinality(CARDINALITY_REQUIRED)
      .Build();
}

PropertyConfigProto CreateSuperTagQualifiedIdPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertySuperTagQualifiedId)
      .SetDataType(TYPE_STRING)
      .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID, /*propagate_delete=*/true)
      .SetCardinality(CARDINALITY_REQUIRED)
      .Build();
}

PropertyConfigProto CreateNamePropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertyName)
      .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
      .SetCardinality(CARDINALITY_OPTIONAL)
      .Build();
}

SchemaTypeConfigProto CreateEmailTypeConfig() {
  return SchemaTypeConfigBuilder()
      .SetType(kTypeEmail)
      .AddProperty(CreateTagQualifiedIdPropertyConfig())
      .AddProperty(CreateSubjectPropertyConfig())
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyText)
                       .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE)
                       .SetCardinality(CARDINALITY_OPTIONAL))
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyAttachment)
                       .SetDataType(TYPE_BYTES)
                       .SetCardinality(CARDINALITY_REQUIRED))
      .AddProperty(CreateSenderQualifiedIdPropertyConfig())
      .AddProperty(CreateRecipientsPropertyConfig())
      .AddProperty(CreateReceipientIdsPropertyConfig())
      .AddProperty(CreateTimestampPropertyConfig())
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyNonIndexableInteger)
                       .SetDataType(TYPE_INT64)
                       .SetCardinality(CARDINALITY_REQUIRED))
      .Build();
}

SchemaTypeConfigProto CreateConversationTypeConfig() {
  return SchemaTypeConfigBuilder()
      .SetType(kTypeConversation)
      .AddProperty(CreateSuperTagQualifiedIdPropertyConfig())
      .AddProperty(CreateNamePropertyConfig())
      .AddProperty(CreateGroupQualifiedIdPropertyConfig())
      .AddProperty(
          PropertyConfigBuilder()
              .SetName(kPropertyEmails)
              .SetDataTypeDocument(kTypeEmail, /*index_nested_properties=*/true)
              .SetCardinality(CARDINALITY_REPEATED))
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyNestedNonIndexable)
                       .SetDataTypeDocument(kTypeEmail,
                                            /*index_nested_properties=*/false)
                       .SetCardinality(CARDINALITY_REPEATED))
      .Build();
}

class SchemaTypeManagerTest : public ::testing::Test {
 protected:
  void SetUp() override { test_dir_ = GetTestTempDir() + "/icing"; }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  Filesystem filesystem_;
  std::string test_dir_;
};

TEST_F(SchemaTypeManagerTest, Create) {
  SchemaUtil::TypeConfigMap type_config_map;
  type_config_map.emplace(kTypeEmail, CreateEmailTypeConfig());
  type_config_map.emplace(kTypeConversation, CreateConversationTypeConfig());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));
  ICING_ASSERT_OK(schema_type_mapper->Put(kTypeEmail, kTypeEmailSchemaId));
  ICING_ASSERT_OK(
      schema_type_mapper->Put(kTypeConversation, kTypeConversationSchemaId));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, schema_type_mapper.get()));

  // Check SectionManager
  // In the Email type, "recipientIds", "recipients", "senderQualifiedId",
  // "subject" and "timestamp" are indexable properties. "attachment",
  // "nonIndexableInteger", "tagQualifiedId" and "text" are non-indexable.
  EXPECT_THAT(
      schema_type_manager->section_manager().GetMetadataList(kTypeEmail),
      IsOkAndHolds(Pointee(ElementsAre(
          EqualsSectionMetadata(/*expected_id=*/0,
                                /*expected_property_path=*/"recipientIds",
                                CreateReceipientIdsPropertyConfig()),
          EqualsSectionMetadata(/*expected_id=*/1,
                                /*expected_property_path=*/"recipients",
                                CreateRecipientsPropertyConfig()),
          EqualsSectionMetadata(/*expected_id=*/2,
                                /*expected_property_path=*/"senderQualifiedId",
                                CreateSenderQualifiedIdPropertyConfig()),
          EqualsSectionMetadata(/*expected_id=*/3,
                                /*expected_property_path=*/"subject",
                                CreateSubjectPropertyConfig()),
          EqualsSectionMetadata(/*expected_id=*/4,
                                /*expected_property_path=*/"timestamp",
                                CreateTimestampPropertyConfig())))));

  // In the Conversation type, "groupQualifiedId" and "name" are indexable
  // properties as are the indexable properties of the email in the "emails"
  // property. All properties of the email in the "nestedNonIndexable" property
  // are not indexable.
  EXPECT_THAT(
      schema_type_manager->section_manager().GetMetadataList(kTypeConversation),
      IsOkAndHolds(Pointee(ElementsAre(
          EqualsSectionMetadata(
              /*expected_id=*/0,
              /*expected_property_path=*/"emails.recipientIds",
              CreateReceipientIdsPropertyConfig()),
          EqualsSectionMetadata(/*expected_id=*/1,
                                /*expected_property_path=*/"emails.recipients",
                                CreateRecipientsPropertyConfig()),
          EqualsSectionMetadata(
              /*expected_id=*/2,
              /*expected_property_path=*/"emails.senderQualifiedId",
              CreateSenderQualifiedIdPropertyConfig()),
          EqualsSectionMetadata(/*expected_id=*/3,
                                /*expected_property_path=*/"emails.subject",
                                CreateSubjectPropertyConfig()),
          EqualsSectionMetadata(/*expected_id=*/4,
                                /*expected_property_path=*/"emails.timestamp",
                                CreateTimestampPropertyConfig()),
          EqualsSectionMetadata(/*expected_id=*/5,
                                /*expected_property_path=*/"groupQualifiedId",
                                CreateGroupQualifiedIdPropertyConfig()),
          EqualsSectionMetadata(/*expected_id=*/6,
                                /*expected_property_path=*/"name",
                                CreateNamePropertyConfig())))));

  // Check JoinablePropertyManager
  // In the Email type, "senderQualifiedId" and "tagQualifiedId" are joinable
  // properties.
  EXPECT_THAT(
      schema_type_manager->joinable_property_manager().GetMetadataList(
          kTypeEmail),
      IsOkAndHolds(Pointee(ElementsAre(
          EqualsJoinablePropertyMetadata(
              /*expected_id=*/0, /*expected_property_path=*/"senderQualifiedId",
              CreateSenderQualifiedIdPropertyConfig()),
          EqualsJoinablePropertyMetadata(
              /*expected_id=*/1, /*expected_property_path=*/"tagQualifiedId",
              CreateTagQualifiedIdPropertyConfig())))));
  // In the Conversation type, "groupQualifiedId" and "superTagQualifiedId" are
  // joinable properties as are the joinable properties of the email in the
  // "emails" and "nestedNonIndexable" property.
  EXPECT_THAT(
      schema_type_manager->joinable_property_manager().GetMetadataList(
          kTypeConversation),
      IsOkAndHolds(Pointee(ElementsAre(
          EqualsJoinablePropertyMetadata(
              /*expected_id=*/0,
              /*expected_property_path=*/"emails.senderQualifiedId",
              CreateSenderQualifiedIdPropertyConfig()),
          EqualsJoinablePropertyMetadata(
              /*expected_id=*/1,
              /*expected_property_path=*/"emails.tagQualifiedId",
              CreateTagQualifiedIdPropertyConfig()),
          EqualsJoinablePropertyMetadata(
              /*expected_id=*/2, /*expected_property_path=*/"groupQualifiedId",
              CreateGroupQualifiedIdPropertyConfig()),
          EqualsJoinablePropertyMetadata(
              /*expected_id=*/3,
              /*expected_property_path=*/"nestedNonIndexable.senderQualifiedId",
              CreateSenderQualifiedIdPropertyConfig()),
          EqualsJoinablePropertyMetadata(
              /*expected_id=*/4,
              /*expected_property_path=*/"nestedNonIndexable.tagQualifiedId",
              CreateTagQualifiedIdPropertyConfig()),
          EqualsJoinablePropertyMetadata(
              /*expected_id=*/5,
              /*expected_property_path=*/"superTagQualifiedId",
              CreateSuperTagQualifiedIdPropertyConfig())))));
}

TEST_F(SchemaTypeManagerTest, CreateWithNullPointerShouldFail) {
  SchemaUtil::TypeConfigMap type_config_map;
  EXPECT_THAT(SchemaTypeManager::Create(type_config_map,
                                        /*schema_type_mapper=*/nullptr),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_F(SchemaTypeManagerTest, CreateWithSchemaNotInSchemaTypeMapperShouldFail) {
  SchemaTypeConfigProto type_config;
  type_config.set_schema_type("type");

  auto property = type_config.add_properties();
  property->set_property_name("property");
  property->set_data_type(TYPE_STRING);
  property->set_cardinality(CARDINALITY_REQUIRED);
  property->mutable_string_indexing_config()->set_term_match_type(
      TERM_MATCH_EXACT);

  SchemaUtil::TypeConfigMap type_config_map;
  type_config_map.emplace("type", type_config);

  // Create an empty schema type mapper
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(
          filesystem_, test_dir_ + "/schema_type_mapper",
          /*maximum_size_bytes=*/3 * 128 * 1024));

  EXPECT_THAT(
      SchemaTypeManager::Create(type_config_map, schema_type_mapper.get()),
      StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

}  // namespace

}  // namespace lib
}  // namespace icing
