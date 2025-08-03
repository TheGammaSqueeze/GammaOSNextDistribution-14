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

#include "icing/schema/joinable-property-manager.h"

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/joinable-property.h"
#include "icing/schema/schema-type-manager.h"
#include "icing/schema/schema-util.h"
#include "icing/store/dynamic-trie-key-mapper.h"
#include "icing/store/key-mapper.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::IsNull;
using ::testing::Pointee;
using ::testing::SizeIs;

// type and property names of Email
static constexpr char kTypeEmail[] = "Email";
// joinable
static constexpr char kPropertyReceiverQualifiedId[] = "receiverQualifiedId";
static constexpr char kPropertySenderQualifiedId[] = "senderQualifiedId";
// non-joinable
static constexpr char kPropertyAttachment[] = "attachment";
static constexpr char kPropertySubject[] = "subject";
static constexpr char kPropertyText[] = "text";
static constexpr char kPropertyTimestamp[] = "timestamp";

// type and property names of Conversation
static constexpr char kTypeConversation[] = "Conversation";
// joinable
static constexpr char kPropertyEmails[] = "emails";
static constexpr char kPropertyGroupQualifiedId[] = "groupQualifiedId";
// non-joinable
static constexpr char kPropertyName[] = "name";
static constexpr char kPropertyNumber[] = "number";

constexpr int64_t kDefaultTimestamp = 1663274901;

PropertyConfigProto CreateSenderQualifiedIdPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertySenderQualifiedId)
      .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
      .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID, /*propagate_delete=*/true)
      .SetCardinality(CARDINALITY_OPTIONAL)
      .Build();
}

PropertyConfigProto CreateReceiverQualifiedIdPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertyReceiverQualifiedId)
      .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
      .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID, /*propagate_delete=*/true)
      .SetCardinality(CARDINALITY_OPTIONAL)
      .Build();
}

PropertyConfigProto CreateGroupQualifiedIdPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertyGroupQualifiedId)
      .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
      .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID, /*propagate_delete=*/false)
      .SetCardinality(CARDINALITY_OPTIONAL)
      .Build();
}

SchemaTypeConfigProto CreateEmailTypeConfig() {
  return SchemaTypeConfigBuilder()
      .SetType(kTypeEmail)
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertySubject)
                       .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                       .SetCardinality(CARDINALITY_OPTIONAL))
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyText)
                       .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE)
                       .SetCardinality(CARDINALITY_OPTIONAL))
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyAttachment)
                       .SetDataType(TYPE_BYTES)
                       .SetCardinality(CARDINALITY_OPTIONAL))
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyTimestamp)
                       .SetDataType(TYPE_INT64)
                       .SetCardinality(CARDINALITY_OPTIONAL))
      .AddProperty(CreateSenderQualifiedIdPropertyConfig())
      .AddProperty(CreateReceiverQualifiedIdPropertyConfig())
      .Build();
}

SchemaTypeConfigProto CreateConversationTypeConfig() {
  return SchemaTypeConfigBuilder()
      .SetType(kTypeConversation)
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyName)
                       .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                       .SetCardinality(CARDINALITY_OPTIONAL))
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyNumber)
                       .SetDataType(TYPE_INT64)
                       .SetCardinality(CARDINALITY_OPTIONAL))
      .AddProperty(CreateGroupQualifiedIdPropertyConfig())
      .AddProperty(
          PropertyConfigBuilder()
              .SetName(kPropertyEmails)
              .SetDataTypeDocument(kTypeEmail, /*index_nested_properties=*/true)
              .SetCardinality(CARDINALITY_OPTIONAL))
      .Build();
}

class JoinablePropertyManagerTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_dir_ = GetTestTempDir() + "/icing";

    type_config_map_.emplace(kTypeEmail, CreateEmailTypeConfig());
    type_config_map_.emplace(kTypeConversation, CreateConversationTypeConfig());

    email_document_ =
        DocumentBuilder()
            .SetKey("icing", "email/1")
            .SetSchema(kTypeEmail)
            .AddStringProperty(kPropertySubject, "the subject")
            .AddStringProperty(kPropertyText, "the text")
            .AddStringProperty(kPropertySenderQualifiedId, "pkg$db/ns#Person1")
            .AddStringProperty(kPropertyReceiverQualifiedId,
                               "pkg$db/ns#Person2")
            .AddBytesProperty(kPropertyAttachment, "attachment")
            .AddInt64Property(kPropertyTimestamp, kDefaultTimestamp)
            .Build();

    conversation_document_ =
        DocumentBuilder()
            .SetKey("icing", "conversation/1")
            .SetSchema(kTypeConversation)
            .AddStringProperty(kPropertyName, "the conversation")
            .AddInt64Property(kPropertyNumber, 2)
            .AddDocumentProperty(kPropertyEmails,
                                 DocumentProto(email_document_))
            .AddStringProperty(kPropertyGroupQualifiedId,
                               "pkg$db/ns#GroupQualifiedId1")
            .Build();

    // DynamicTrieKeyMapper uses 3 internal arrays for bookkeeping. Give each
    // one 128KiB so the total DynamicTrieKeyMapper should get 384KiB
    int key_mapper_size = 3 * 128 * 1024;
    ICING_ASSERT_OK_AND_ASSIGN(schema_type_mapper_,
                               DynamicTrieKeyMapper<SchemaTypeId>::Create(
                                   filesystem_, test_dir_, key_mapper_size));
    ICING_ASSERT_OK(schema_type_mapper_->Put(kTypeEmail, 0));
    ICING_ASSERT_OK(schema_type_mapper_->Put(kTypeConversation, 1));
  }

  void TearDown() override {
    schema_type_mapper_.reset();
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  Filesystem filesystem_;
  std::string test_dir_;
  SchemaUtil::TypeConfigMap type_config_map_;
  std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper_;

  DocumentProto email_document_;
  DocumentProto conversation_document_;
};

TEST_F(JoinablePropertyManagerTest, ExtractJoinableProperties) {
  // Use SchemaTypeManager factory method to instantiate
  // JoinablePropertyManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  // Extracts all joinable properties from 'Email' document
  ICING_ASSERT_OK_AND_ASSIGN(JoinablePropertyGroup joinable_property_group,
                             schema_type_manager->joinable_property_manager()
                                 .ExtractJoinableProperties(email_document_));

  // Qualified Id joinable properties
  EXPECT_THAT(joinable_property_group.qualified_id_properties, SizeIs(2));

  EXPECT_THAT(
      joinable_property_group.qualified_id_properties[0].metadata,
      EqualsJoinablePropertyMetadata(
          /*expected_id=*/0, /*expected_property_path=*/"receiverQualifiedId",
          CreateReceiverQualifiedIdPropertyConfig()));
  EXPECT_THAT(joinable_property_group.qualified_id_properties[0].values,
              ElementsAre("pkg$db/ns#Person2"));

  EXPECT_THAT(
      joinable_property_group.qualified_id_properties[1].metadata,
      EqualsJoinablePropertyMetadata(
          /*expected_id=*/1, /*expected_property_path=*/"senderQualifiedId",
          CreateSenderQualifiedIdPropertyConfig()));
  EXPECT_THAT(joinable_property_group.qualified_id_properties[1].values,
              ElementsAre("pkg$db/ns#Person1"));
}

TEST_F(JoinablePropertyManagerTest, ExtractJoinablePropertiesNested) {
  // Use SchemaTypeManager factory method to instantiate
  // JoinablePropertyManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  // Extracts all joinable properties from 'Conversation' document
  ICING_ASSERT_OK_AND_ASSIGN(
      JoinablePropertyGroup joinable_property_group,
      schema_type_manager->joinable_property_manager()
          .ExtractJoinableProperties(conversation_document_));

  // Qualified Id joinable properties
  EXPECT_THAT(joinable_property_group.qualified_id_properties, SizeIs(3));

  EXPECT_THAT(joinable_property_group.qualified_id_properties[0].metadata,
              EqualsJoinablePropertyMetadata(
                  /*expected_id=*/0,
                  /*expected_property_path=*/"emails.receiverQualifiedId",
                  CreateReceiverQualifiedIdPropertyConfig()));
  EXPECT_THAT(joinable_property_group.qualified_id_properties[0].values,
              ElementsAre("pkg$db/ns#Person2"));

  EXPECT_THAT(joinable_property_group.qualified_id_properties[1].metadata,
              EqualsJoinablePropertyMetadata(
                  /*expected_id=*/1,
                  /*expected_property_path=*/"emails.senderQualifiedId",
                  CreateSenderQualifiedIdPropertyConfig()));
  EXPECT_THAT(joinable_property_group.qualified_id_properties[1].values,
              ElementsAre("pkg$db/ns#Person1"));

  EXPECT_THAT(
      joinable_property_group.qualified_id_properties[2].metadata,
      EqualsJoinablePropertyMetadata(
          /*expected_id=*/2, /*expected_property_path=*/"groupQualifiedId",
          CreateGroupQualifiedIdPropertyConfig()));
  EXPECT_THAT(joinable_property_group.qualified_id_properties[2].values,
              ElementsAre("pkg$db/ns#GroupQualifiedId1"));
}

TEST_F(JoinablePropertyManagerTest,
       ExtractJoinablePropertiesShouldIgnoreEmptyContents) {
  // Use SchemaTypeManager factory method to instantiate
  // JoinablePropertyManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  // Create an email document without receiverQualifiedId.
  DocumentProto another_email_document =
      DocumentBuilder()
          .SetKey("icing", "email/2")
          .SetSchema(kTypeEmail)
          .AddStringProperty(kPropertySubject, "the subject")
          .AddStringProperty(kPropertyText, "the text")
          .AddBytesProperty(kPropertyAttachment, "attachment")
          .AddStringProperty(kPropertySenderQualifiedId, "pkg$db/ns#Person1")
          .AddInt64Property(kPropertyTimestamp, kDefaultTimestamp)
          .Build();

  ICING_ASSERT_OK_AND_ASSIGN(
      JoinablePropertyGroup joinable_property_group,
      schema_type_manager->joinable_property_manager()
          .ExtractJoinableProperties(another_email_document));

  // ExtractJoinableProperties should ignore receiverQualifiedId and not append
  // a JoinableProperty instance of it into the vector.
  EXPECT_THAT(joinable_property_group.qualified_id_properties, SizeIs(1));
  EXPECT_THAT(
      joinable_property_group.qualified_id_properties[0].metadata,
      EqualsJoinablePropertyMetadata(
          /*expected_id=*/1, /*expected_property_path=*/"senderQualifiedId",
          CreateSenderQualifiedIdPropertyConfig()));
  EXPECT_THAT(joinable_property_group.qualified_id_properties[0].values,
              ElementsAre("pkg$db/ns#Person1"));
}

TEST_F(JoinablePropertyManagerTest, GetJoinablePropertyMetadata) {
  // Use SchemaTypeManager factory method to instantiate
  // JoinablePropertyManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  // Email (joinable property id -> joinable property path):
  //   0 -> receiverQualifiedId
  //   1 -> senderQualifiedId
  EXPECT_THAT(
      schema_type_manager->joinable_property_manager()
          .GetJoinablePropertyMetadata(/*schema_type_id=*/0,
                                       /*joinable_property_id=*/0),
      IsOkAndHolds(Pointee(EqualsJoinablePropertyMetadata(
          /*expected_id=*/0, /*expected_property_path=*/"receiverQualifiedId",
          CreateReceiverQualifiedIdPropertyConfig()))));
  EXPECT_THAT(
      schema_type_manager->joinable_property_manager()
          .GetJoinablePropertyMetadata(/*schema_type_id=*/0,
                                       /*joinable_property_id=*/1),
      IsOkAndHolds(Pointee(EqualsJoinablePropertyMetadata(
          /*expected_id=*/1, /*expected_property_path=*/"senderQualifiedId",
          CreateSenderQualifiedIdPropertyConfig()))));

  // Conversation (joinable property id -> joinable property path):
  //   0 -> emails.receiverQualifiedId
  //   1 -> emails.senderQualifiedId
  //   2 -> groupQualifiedId
  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/1,
                                               /*joinable_property_id=*/0),
              IsOkAndHolds(Pointee(EqualsJoinablePropertyMetadata(
                  /*expected_id=*/0,
                  /*expected_property_path=*/"emails.receiverQualifiedId",
                  CreateReceiverQualifiedIdPropertyConfig()))));
  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/1,
                                               /*joinable_property_id=*/1),
              IsOkAndHolds(Pointee(EqualsJoinablePropertyMetadata(
                  /*expected_id=*/1,
                  /*expected_property_path=*/"emails.senderQualifiedId",
                  CreateSenderQualifiedIdPropertyConfig()))));
  EXPECT_THAT(
      schema_type_manager->joinable_property_manager()
          .GetJoinablePropertyMetadata(/*schema_type_id=*/1,
                                       /*joinable_property_id=*/2),
      IsOkAndHolds(Pointee(EqualsJoinablePropertyMetadata(
          /*expected_id=*/2, /*expected_property_path=*/"groupQualifiedId",
          CreateGroupQualifiedIdPropertyConfig()))));
}

TEST_F(JoinablePropertyManagerTest,
       GetJoinablePropertyMetadataInvalidSchemaTypeId) {
  // Use SchemaTypeManager factory method to instantiate
  // JoinablePropertyManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));
  ASSERT_THAT(type_config_map_, SizeIs(2));

  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/-1,
                                               /*joinable_property_id=*/0),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/2,
                                               /*joinable_property_id=*/0),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(JoinablePropertyManagerTest,
       GetJoinablePropertyMetadataInvalidJoinablePropertyId) {
  // Use SchemaTypeManager factory method to instantiate
  // JoinablePropertyManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  // Email (joinable property id -> joinable property path):
  //   0 -> receiverQualifiedId
  //   1 -> senderQualifiedId
  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/0,
                                               /*joinable_property_id=*/-1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/0,
                                               /*joinable_property_id=*/2),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // Conversation (joinable property id -> joinable property path):
  //   0 -> emails.receiverQualifiedId
  //   1 -> emails.senderQualifiedId
  //   2 -> groupQualifiedId
  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/1,
                                               /*joinable_property_id=*/-1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/1,
                                               /*joinable_property_id=*/3),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(JoinablePropertyManagerTest, GetJoinablePropertyMetadataByPath) {
  // Use SchemaTypeManager factory method to instantiate
  // JoinablePropertyManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  // Email (joinable property id -> joinable property path):
  //   0 -> receiverQualifiedId
  //   1 -> senderQualifiedId
  EXPECT_THAT(
      schema_type_manager->joinable_property_manager()
          .GetJoinablePropertyMetadata(/*schema_type_id=*/0,
                                       "receiverQualifiedId"),
      IsOkAndHolds(Pointee(EqualsJoinablePropertyMetadata(
          /*expected_id=*/0, /*expected_property_path=*/"receiverQualifiedId",
          CreateReceiverQualifiedIdPropertyConfig()))));
  EXPECT_THAT(
      schema_type_manager->joinable_property_manager()
          .GetJoinablePropertyMetadata(/*schema_type_id=*/0,
                                       "senderQualifiedId"),
      IsOkAndHolds(Pointee(EqualsJoinablePropertyMetadata(
          /*expected_id=*/1, /*expected_property_path=*/"senderQualifiedId",
          CreateSenderQualifiedIdPropertyConfig()))));

  // Conversation (joinable property id -> joinable property path):
  //   0 -> emails.receiverQualifiedId
  //   1 -> emails.senderQualifiedId
  //   2 -> groupQualifiedId
  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/1,
                                               "emails.receiverQualifiedId"),
              IsOkAndHolds(Pointee(EqualsJoinablePropertyMetadata(
                  /*expected_id=*/0,
                  /*expected_property_path=*/"emails.receiverQualifiedId",
                  CreateReceiverQualifiedIdPropertyConfig()))));
  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/1,
                                               "emails.senderQualifiedId"),
              IsOkAndHolds(Pointee(EqualsJoinablePropertyMetadata(
                  /*expected_id=*/1,
                  /*expected_property_path=*/"emails.senderQualifiedId",
                  CreateSenderQualifiedIdPropertyConfig()))));
  EXPECT_THAT(
      schema_type_manager->joinable_property_manager()
          .GetJoinablePropertyMetadata(/*schema_type_id=*/1,
                                       "groupQualifiedId"),
      IsOkAndHolds(Pointee(EqualsJoinablePropertyMetadata(
          /*expected_id=*/2, /*expected_property_path=*/"groupQualifiedId",
          CreateGroupQualifiedIdPropertyConfig()))));
}

TEST_F(JoinablePropertyManagerTest,
       GetJoinablePropertyMetadataByPathInvalidSchemaTypeId) {
  // Use SchemaTypeManager factory method to instantiate
  // JoinablePropertyManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));
  ASSERT_THAT(type_config_map_, SizeIs(2));

  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/-1,
                                               "receiverQualifiedId"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/2,
                                               "receiverQualifiedId"),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(JoinablePropertyManagerTest, GetJoinablePropertyMetadataByPathNotExist) {
  // Use SchemaTypeManager factory method to instantiate
  // JoinablePropertyManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  EXPECT_THAT(
      schema_type_manager->joinable_property_manager()
          .GetJoinablePropertyMetadata(/*schema_type_id=*/0, "nonExistingPath"),
      IsOkAndHolds(IsNull()));
  EXPECT_THAT(schema_type_manager->joinable_property_manager()
                  .GetJoinablePropertyMetadata(/*schema_type_id=*/1,
                                               "emails.nonExistingPath"),
              IsOkAndHolds(IsNull()));
}

// Note: valid GetMetadataList has been tested in
// JoinablePropertyManagerBuildTest.
TEST_F(JoinablePropertyManagerTest, GetMetadataListInvalidSchemaTypeName) {
  // Use SchemaTypeManager factory method to instantiate
  // JoinablePropertyManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  EXPECT_THAT(schema_type_manager->joinable_property_manager().GetMetadataList(
                  "NonExistingSchemaTypeName"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

}  // namespace

}  // namespace lib
}  // namespace icing
