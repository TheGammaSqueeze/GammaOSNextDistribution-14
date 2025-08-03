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

#include "icing/schema/section-manager.h"

#include <memory>
#include <string>
#include <string_view>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema-builder.h"
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
using ::testing::IsEmpty;
using ::testing::Pointee;
using ::testing::SizeIs;

// type and property names of Email
static constexpr std::string_view kTypeEmail = "Email";
// indexable
static constexpr std::string_view kPropertyRecipientIds = "recipientIds";
static constexpr std::string_view kPropertyRecipients = "recipients";
static constexpr std::string_view kPropertySubject = "subject";
static constexpr std::string_view kPropertyTimestamp = "timestamp";
// non-indexable
static constexpr std::string_view kPropertyAttachment = "attachment";
static constexpr std::string_view kPropertyNonIndexableInteger =
    "nonIndexableInteger";
static constexpr std::string_view kPropertyText = "text";

// type and property names of Conversation
static constexpr std::string_view kTypeConversation = "Conversation";
// indexable
static constexpr std::string_view kPropertyEmails = "emails";
static constexpr std::string_view kPropertyName = "name";

// type and property names of Group
static constexpr std::string_view kTypeGroup = "Group";
// indexable
static constexpr std::string_view kPropertyConversation = "conversation";
static constexpr std::string_view kPropertyGroupName = "groupName";
// nested indexable
static constexpr std::string_view kPropertyNestedConversationName = "name";
static constexpr std::string_view kPropertyNestedConversationEmailRecipientIds =
    "emails.recipientIds";
static constexpr std::string_view kPropertyNestedConversationEmailRecipient =
    "emails.recipients";
static constexpr std::string_view kPropertyNestedConversationEmailSubject =
    "emails.subject";
// nested non-indexable
static constexpr std::string_view kPropertyNestedConversationEmailAttachment =
    "emails.attachment";
// non-existent property path
static constexpr std::string_view kPropertyNestedNonExistent =
    "emails.nonExistentNestedProperty";
static constexpr std::string_view kPropertyNestedNonExistent2 =
    "emails.nonExistentNestedProperty2";

constexpr int64_t kDefaultTimestamp = 1663274901;

PropertyConfigProto CreateRecipientIdsPropertyConfig() {
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

PropertyConfigProto CreateSubjectPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertySubject)
      .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
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

PropertyConfigProto CreateNamePropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertyName)
      .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
      .SetCardinality(CARDINALITY_OPTIONAL)
      .Build();
}

PropertyConfigProto CreateAttachmentPropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertyAttachment)
      .SetDataType(TYPE_BYTES)
      .SetCardinality(CARDINALITY_OPTIONAL)
      .Build();
}

PropertyConfigProto CreateGroupNamePropertyConfig() {
  return PropertyConfigBuilder()
      .SetName(kPropertyGroupName)
      .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
      .SetCardinality(CARDINALITY_OPTIONAL)
      .Build();
}

SchemaTypeConfigProto CreateEmailTypeConfig() {
  return SchemaTypeConfigBuilder()
      .SetType(kTypeEmail)
      .AddProperty(CreateSubjectPropertyConfig())
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyText)
                       .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE)
                       .SetCardinality(CARDINALITY_OPTIONAL))
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyAttachment)
                       .SetDataType(TYPE_BYTES)
                       .SetCardinality(CARDINALITY_REQUIRED))
      .AddProperty(CreateRecipientsPropertyConfig())
      .AddProperty(CreateRecipientIdsPropertyConfig())
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
      .AddProperty(CreateNamePropertyConfig())
      .AddProperty(PropertyConfigBuilder()
                       .SetName(kPropertyEmails)
                       .SetDataTypeDocument(kTypeEmail,
                                            /*index_nested_properties=*/true)
                       .SetCardinality(CARDINALITY_REPEATED))
      .Build();
}

SchemaTypeConfigProto CreateGroupTypeConfig() {
  return SchemaTypeConfigBuilder()
      .SetType(kTypeGroup)
      .AddProperty(CreateGroupNamePropertyConfig())
      .AddProperty(
          PropertyConfigBuilder()
              .SetName(kPropertyConversation)
              .SetDataTypeDocument(
                  kTypeConversation,
                  /*indexable_nested_properties_list=*/
                  {std::string(kPropertyNestedConversationName),
                   std::string(kPropertyNestedConversationEmailRecipientIds),
                   std::string(kPropertyNestedConversationEmailSubject),
                   std::string(kPropertyNestedConversationEmailRecipient),
                   std::string(kPropertyNestedConversationEmailAttachment),
                   std::string(kPropertyNestedNonExistent2),
                   std::string(kPropertyNestedNonExistent),
                   std::string(kPropertyNestedNonExistent)})
              .SetCardinality(CARDINALITY_REPEATED))
      .Build();
}

class SectionManagerTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_dir_ = GetTestTempDir() + "/icing";

    auto email_type = CreateEmailTypeConfig();
    auto conversation_type = CreateConversationTypeConfig();
    auto group_type = CreateGroupTypeConfig();
    type_config_map_.emplace(email_type.schema_type(), email_type);
    type_config_map_.emplace(conversation_type.schema_type(),
                             conversation_type);
    type_config_map_.emplace(group_type.schema_type(), group_type);

    // DynamicTrieKeyMapper uses 3 internal arrays for bookkeeping. Give each
    // one 128KiB so the total DynamicTrieKeyMapper should get 384KiB
    int key_mapper_size = 3 * 128 * 1024;
    ICING_ASSERT_OK_AND_ASSIGN(schema_type_mapper_,
                               DynamicTrieKeyMapper<SchemaTypeId>::Create(
                                   filesystem_, test_dir_, key_mapper_size));
    ICING_ASSERT_OK(schema_type_mapper_->Put(kTypeEmail, 0));
    ICING_ASSERT_OK(schema_type_mapper_->Put(kTypeConversation, 1));
    ICING_ASSERT_OK(schema_type_mapper_->Put(kTypeGroup, 2));

    email_document_ =
        DocumentBuilder()
            .SetKey("icing", "email/1")
            .SetSchema(std::string(kTypeEmail))
            .AddStringProperty(std::string(kPropertySubject), "the subject")
            .AddStringProperty(std::string(kPropertyText), "the text")
            .AddBytesProperty(std::string(kPropertyAttachment),
                              "attachment bytes")
            .AddStringProperty(std::string(kPropertyRecipients), "recipient1",
                               "recipient2", "recipient3")
            .AddInt64Property(std::string(kPropertyRecipientIds), 1, 2, 3)
            .AddInt64Property(std::string(kPropertyTimestamp),
                              kDefaultTimestamp)
            .AddInt64Property(std::string(kPropertyNonIndexableInteger), 100)
            .Build();

    conversation_document_ =
        DocumentBuilder()
            .SetKey("icing", "conversation/1")
            .SetSchema(std::string(kTypeConversation))
            .AddDocumentProperty(std::string(kPropertyEmails),
                                 DocumentProto(email_document_),
                                 DocumentProto(email_document_))
            .Build();

    group_document_ =
        DocumentBuilder()
            .SetKey("icing", "group/1")
            .SetSchema(std::string(kTypeGroup))
            .AddDocumentProperty(std::string(kPropertyConversation),
                                 DocumentProto(conversation_document_))
            .AddStringProperty(std::string(kPropertyGroupName), "group_name_1")
            .Build();
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
  DocumentProto group_document_;
};

TEST_F(SectionManagerTest, ExtractSections) {
  // Use SchemaTypeManager factory method to instantiate SectionManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  // Extracts all sections from 'Email' document
  ICING_ASSERT_OK_AND_ASSIGN(
      SectionGroup section_group,
      schema_type_manager->section_manager().ExtractSections(email_document_));

  // String sections
  EXPECT_THAT(section_group.string_sections, SizeIs(2));

  EXPECT_THAT(section_group.string_sections[0].metadata,
              EqualsSectionMetadata(/*expected_id=*/1,
                                    /*expected_property_path=*/"recipients",
                                    CreateRecipientsPropertyConfig()));
  EXPECT_THAT(section_group.string_sections[0].content,
              ElementsAre("recipient1", "recipient2", "recipient3"));

  EXPECT_THAT(section_group.string_sections[1].metadata,
              EqualsSectionMetadata(/*expected_id=*/2,
                                    /*expected_property_path=*/"subject",
                                    CreateSubjectPropertyConfig()));
  EXPECT_THAT(section_group.string_sections[1].content,
              ElementsAre("the subject"));

  // Integer sections
  EXPECT_THAT(section_group.integer_sections, SizeIs(2));

  EXPECT_THAT(section_group.integer_sections[0].metadata,
              EqualsSectionMetadata(/*expected_id=*/0,
                                    /*expected_property_path=*/"recipientIds",
                                    CreateRecipientIdsPropertyConfig()));
  EXPECT_THAT(section_group.integer_sections[0].content, ElementsAre(1, 2, 3));

  EXPECT_THAT(section_group.integer_sections[1].metadata,
              EqualsSectionMetadata(/*expected_id=*/3,
                                    /*expected_property_path=*/"timestamp",
                                    CreateTimestampPropertyConfig()));
  EXPECT_THAT(section_group.integer_sections[1].content,
              ElementsAre(kDefaultTimestamp));
}

TEST_F(SectionManagerTest, ExtractSectionsNested) {
  // Use SchemaTypeManager factory method to instantiate SectionManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  // Extracts all sections from 'Conversation' document
  ICING_ASSERT_OK_AND_ASSIGN(
      SectionGroup section_group,
      schema_type_manager->section_manager().ExtractSections(
          conversation_document_));

  // String sections
  EXPECT_THAT(section_group.string_sections, SizeIs(2));

  EXPECT_THAT(
      section_group.string_sections[0].metadata,
      EqualsSectionMetadata(/*expected_id=*/1,
                            /*expected_property_path=*/"emails.recipients",
                            CreateRecipientsPropertyConfig()));
  EXPECT_THAT(section_group.string_sections[0].content,
              ElementsAre("recipient1", "recipient2", "recipient3",
                          "recipient1", "recipient2", "recipient3"));

  EXPECT_THAT(section_group.string_sections[1].metadata,
              EqualsSectionMetadata(/*expected_id=*/2,
                                    /*expected_property_path=*/"emails.subject",
                                    CreateSubjectPropertyConfig()));
  EXPECT_THAT(section_group.string_sections[1].content,
              ElementsAre("the subject", "the subject"));

  // Integer sections
  EXPECT_THAT(section_group.integer_sections, SizeIs(2));

  EXPECT_THAT(
      section_group.integer_sections[0].metadata,
      EqualsSectionMetadata(/*expected_id=*/0,
                            /*expected_property_path=*/"emails.recipientIds",
                            CreateRecipientIdsPropertyConfig()));
  EXPECT_THAT(section_group.integer_sections[0].content,
              ElementsAre(1, 2, 3, 1, 2, 3));

  EXPECT_THAT(
      section_group.integer_sections[1].metadata,
      EqualsSectionMetadata(/*expected_id=*/3,
                            /*expected_property_path=*/"emails.timestamp",
                            CreateTimestampPropertyConfig()));
  EXPECT_THAT(section_group.integer_sections[1].content,
              ElementsAre(kDefaultTimestamp, kDefaultTimestamp));
}

TEST_F(SectionManagerTest, ExtractSectionsIndexableNestedPropertiesList) {
  // Use SchemaTypeManager factory method to instantiate SectionManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  // Extracts all sections from 'Group' document
  ICING_ASSERT_OK_AND_ASSIGN(
      SectionGroup section_group,
      schema_type_manager->section_manager().ExtractSections(group_document_));

  // SectionId assignments:
  //    0 -> conversation.emails.attachment (bytes, non-indexable)
  //    1 -> conversation.emails.recipientIds (int64)
  //    2 -> conversation.emails.recipients (string)
  //    3 -> conversation.emails.subject (string)
  //    4 -> conversation.name
  //         (string, but no entry for this in conversation_document_)
  //    5 -> groupName (string)
  //    6 -> conversation.emails.nonExistentNestedProperty
  //         (unknown, non-indexable)
  //    7 -> conversation.emails.nonExistentNestedProperty2
  //         (unknown, non-indexable)
  //
  // SectionId assignment order:
  // - We assign section ids to known (existing) properties first in alphabet
  //  order.
  // - After handling all known properties, we assign section ids to all unknown
  //   (non-existent) properties that are specified in the
  //  indexable_nested_properties_list.
  // - As a result, assignment of the entire section set is not done
  //   alphabetically, but assignment is still deterministic and alphabetical
  //   order is preserved inside the known properties and unknown properties
  //   sets individually.
  //
  // 'conversation.emails.attachment',
  // 'conversation.emails.nonExistentNestedProperty' and
  // 'conversation.emails.nonExistentNestedProperty2' are assigned sectionIds
  // even though they are non-indexable because they appear in 'Group' schema
  // type's indexable_nested_props_list.
  // However 'conversation.emails.attachment' does not exist in section_group
  // (even though the property exists and has a sectionId assignment) as
  // SectionManager::ExtractSections only extracts indexable string and integer
  // section data from a document.

  // String sections
  EXPECT_THAT(section_group.string_sections, SizeIs(3));

  EXPECT_THAT(section_group.string_sections[0].metadata,
              EqualsSectionMetadata(
                  /*expected_id=*/2,
                  /*expected_property_path=*/"conversation.emails.recipients",
                  CreateRecipientsPropertyConfig()));
  EXPECT_THAT(section_group.string_sections[0].content,
              ElementsAre("recipient1", "recipient2", "recipient3",
                          "recipient1", "recipient2", "recipient3"));

  EXPECT_THAT(section_group.string_sections[1].metadata,
              EqualsSectionMetadata(
                  /*expected_id=*/3,
                  /*expected_property_path=*/"conversation.emails.subject",
                  CreateSubjectPropertyConfig()));
  EXPECT_THAT(section_group.string_sections[1].content,
              ElementsAre("the subject", "the subject"));

  EXPECT_THAT(section_group.string_sections[2].metadata,
              EqualsSectionMetadata(
                  /*expected_id=*/5,
                  /*expected_property_path=*/"groupName",
                  CreateGroupNamePropertyConfig()));
  EXPECT_THAT(section_group.string_sections[2].content,
              ElementsAre("group_name_1"));

  // Integer sections
  EXPECT_THAT(section_group.integer_sections, SizeIs(1));

  EXPECT_THAT(section_group.integer_sections[0].metadata,
              EqualsSectionMetadata(
                  /*expected_id=*/1,
                  /*expected_property_path=*/"conversation.emails.recipientIds",
                  CreateRecipientIdsPropertyConfig()));
  EXPECT_THAT(section_group.integer_sections[0].content,
              ElementsAre(1, 2, 3, 1, 2, 3));
}

TEST_F(SectionManagerTest, GetSectionMetadata) {
  // Use SchemaTypeManager factory method to instantiate SectionManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  // Email (section id -> section property path):
  //   0 -> recipientIds
  //   1 -> recipients
  //   2 -> subject
  //   3 -> timestamp
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/0, /*section_id=*/0),
              IsOkAndHolds(Pointee(EqualsSectionMetadata(
                  /*expected_id=*/0, /*expected_property_path=*/"recipientIds",
                  CreateRecipientIdsPropertyConfig()))));
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/0, /*section_id=*/1),
              IsOkAndHolds(Pointee(EqualsSectionMetadata(
                  /*expected_id=*/1, /*expected_property_path=*/"recipients",
                  CreateRecipientsPropertyConfig()))));

  // Conversation (section id -> section property path):
  //   0 -> emails.recipientIds
  //   1 -> emails.recipients
  //   2 -> emails.subject
  //   3 -> emails.timestamp
  //   4 -> name
  EXPECT_THAT(
      schema_type_manager->section_manager().GetSectionMetadata(
          /*schema_type_id=*/1, /*section_id=*/0),
      IsOkAndHolds(Pointee(EqualsSectionMetadata(
          /*expected_id=*/0, /*expected_property_path=*/"emails.recipientIds",
          CreateRecipientIdsPropertyConfig()))));
  EXPECT_THAT(
      schema_type_manager->section_manager().GetSectionMetadata(
          /*schema_type_id=*/1, /*section_id=*/1),
      IsOkAndHolds(Pointee(EqualsSectionMetadata(
          /*expected_id=*/1, /*expected_property_path=*/"emails.recipients",
          CreateRecipientsPropertyConfig()))));
  EXPECT_THAT(
      schema_type_manager->section_manager().GetSectionMetadata(
          /*schema_type_id=*/1, /*section_id=*/2),
      IsOkAndHolds(Pointee(EqualsSectionMetadata(
          /*expected_id=*/2, /*expected_property_path=*/"emails.subject",
          CreateSubjectPropertyConfig()))));
  EXPECT_THAT(
      schema_type_manager->section_manager().GetSectionMetadata(
          /*schema_type_id=*/1, /*section_id=*/3),
      IsOkAndHolds(Pointee(EqualsSectionMetadata(
          /*expected_id=*/3, /*expected_property_path=*/"emails.timestamp",
          CreateTimestampPropertyConfig()))));
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/1, /*section_id=*/4),
              IsOkAndHolds(Pointee(EqualsSectionMetadata(
                  /*expected_id=*/4, /*expected_property_path=*/"name",
                  CreateNamePropertyConfig()))));

  // Group (section id -> section property path):
  //    0 -> conversation.emails.attachment (non-indexable)
  //    1 -> conversation.emails.recipientIds
  //    2 -> conversation.emails.recipients
  //    3 -> conversation.emails.subject
  //    4 -> conversation.name
  //    5 -> groupName
  //    6 -> conversation.emails.nonExistentNestedProperty (non-indexable)
  //    7 -> conversation.emails.nonExistentNestedProperty2 (non-indexable)
  //
  // SectionId assignment order:
  // - We assign section ids to known (existing) properties first in alphabet
  //  order.
  // - After handling all known properties, we assign section ids to all unknown
  //   (non-existent) properties that are specified in the
  //  indexable_nested_properties_list.
  // - As a result, assignment of the entire section set is not done
  //   alphabetically, but assignment is still deterministic and alphabetical
  //   order is preserved inside the known properties and unknown properties
  //   sets individually.
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/2, /*section_id=*/0),
              IsOkAndHolds(Pointee(EqualsSectionMetadata(
                  /*expected_id=*/0,
                  /*expected_property_path=*/"conversation.emails.attachment",
                  CreateAttachmentPropertyConfig()))));
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/2, /*section_id=*/1),
              IsOkAndHolds(Pointee(EqualsSectionMetadata(
                  /*expected_id=*/1,
                  /*expected_property_path=*/"conversation.emails.recipientIds",
                  CreateRecipientIdsPropertyConfig()))));
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/2, /*section_id=*/2),
              IsOkAndHolds(Pointee(EqualsSectionMetadata(
                  /*expected_id=*/2,
                  /*expected_property_path=*/"conversation.emails.recipients",
                  CreateRecipientsPropertyConfig()))));
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/2, /*section_id=*/3),
              IsOkAndHolds(Pointee(EqualsSectionMetadata(
                  /*expected_id=*/3,
                  /*expected_property_path=*/"conversation.emails.subject",
                  CreateSubjectPropertyConfig()))));
  EXPECT_THAT(
      schema_type_manager->section_manager().GetSectionMetadata(
          /*schema_type_id=*/2, /*section_id=*/4),
      IsOkAndHolds(Pointee(EqualsSectionMetadata(
          /*expected_id=*/4, /*expected_property_path=*/"conversation.name",
          CreateNamePropertyConfig()))));
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/2, /*section_id=*/5),
              IsOkAndHolds(Pointee(EqualsSectionMetadata(
                  /*expected_id=*/5, /*expected_property_path=*/"groupName",
                  CreateGroupNamePropertyConfig()))));
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/2, /*section_id=*/6),
              IsOkAndHolds(Pointee(EqualsSectionMetadata(
                  /*expected_id=*/6,
                  /*expected_property_path=*/
                  "conversation.emails.nonExistentNestedProperty",
                  PropertyConfigBuilder()
                      .SetName("nonExistentNestedProperty")
                      .SetDataType(TYPE_UNKNOWN)
                      .Build()))));
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/2, /*section_id=*/7),
              IsOkAndHolds(Pointee(EqualsSectionMetadata(
                  /*expected_id=*/7,
                  /*expected_property_path=*/
                  "conversation.emails.nonExistentNestedProperty2",
                  PropertyConfigBuilder()
                      .SetName("nonExistentNestedProperty2")
                      .SetDataType(TYPE_UNKNOWN)
                      .Build()))));
  // Check that no more properties are indexed
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/2, /*section_id=*/8),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(SectionManagerTest, GetSectionMetadataInvalidSchemaTypeId) {
  // Use SchemaTypeManager factory method to instantiate SectionManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));
  ASSERT_THAT(type_config_map_, SizeIs(3));

  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/-1, /*section_id=*/0),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/3, /*section_id=*/0),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(SectionManagerTest, GetSectionMetadataInvalidSectionId) {
  // Use SchemaTypeManager factory method to instantiate SectionManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map_, schema_type_mapper_.get()));

  // Email (section id -> section property path):
  //   0 -> recipientIds
  //   1 -> recipients
  //   2 -> subject
  //   3 -> timestamp
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/0, /*section_id=*/-1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/0, /*section_id=*/4),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // Conversation (section id -> section property path):
  //   0 -> emails.recipientIds
  //   1 -> emails.recipients
  //   2 -> emails.subject
  //   3 -> emails.timestamp
  //   4 -> name
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/1, /*section_id=*/-1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(schema_type_manager->section_manager().GetSectionMetadata(
                  /*schema_type_id=*/1, /*section_id=*/5),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(SectionManagerTest,
       NonStringFieldsWithStringIndexingConfigDontCreateSections) {
  // Create a schema for an empty document.
  SchemaTypeConfigProto empty_type;
  empty_type.set_schema_type("EmptySchema");

  // Create a schema with all the non-string fields
  SchemaTypeConfigProto type_with_non_string_properties;
  type_with_non_string_properties.set_schema_type("Schema");

  // Create an int property with a string_indexing_config
  auto int_property = type_with_non_string_properties.add_properties();
  int_property->set_property_name("int");
  int_property->set_data_type(TYPE_INT64);
  int_property->set_cardinality(CARDINALITY_REQUIRED);
  int_property->mutable_string_indexing_config()->set_term_match_type(
      TERM_MATCH_EXACT);
  int_property->mutable_string_indexing_config()->set_tokenizer_type(
      TOKENIZER_PLAIN);

  // Create a double property with a string_indexing_config
  auto double_property = type_with_non_string_properties.add_properties();
  double_property->set_property_name("double");
  double_property->set_data_type(TYPE_DOUBLE);
  double_property->set_cardinality(CARDINALITY_REQUIRED);
  double_property->mutable_string_indexing_config()->set_term_match_type(
      TERM_MATCH_EXACT);
  double_property->mutable_string_indexing_config()->set_tokenizer_type(
      TOKENIZER_PLAIN);

  // Create a boolean property with a string_indexing_config
  auto boolean_property = type_with_non_string_properties.add_properties();
  boolean_property->set_property_name("boolean");
  boolean_property->set_data_type(TYPE_BOOLEAN);
  boolean_property->set_cardinality(CARDINALITY_REQUIRED);
  boolean_property->mutable_string_indexing_config()->set_term_match_type(
      TERM_MATCH_EXACT);
  boolean_property->mutable_string_indexing_config()->set_tokenizer_type(
      TOKENIZER_PLAIN);

  // Create a bytes property with a string_indexing_config
  auto bytes_property = type_with_non_string_properties.add_properties();
  bytes_property->set_property_name("bytes");
  bytes_property->set_data_type(TYPE_BYTES);
  bytes_property->set_cardinality(CARDINALITY_REQUIRED);
  bytes_property->mutable_string_indexing_config()->set_term_match_type(
      TERM_MATCH_EXACT);
  bytes_property->mutable_string_indexing_config()->set_tokenizer_type(
      TOKENIZER_PLAIN);

  // Create a document property with a string_indexing_config
  auto document_property = type_with_non_string_properties.add_properties();
  document_property->set_property_name("document");
  document_property->set_data_type(TYPE_DOCUMENT);
  document_property->set_schema_type(empty_type.schema_type());
  document_property->set_cardinality(CARDINALITY_REQUIRED);
  document_property->mutable_string_indexing_config()->set_term_match_type(
      TERM_MATCH_EXACT);
  document_property->mutable_string_indexing_config()->set_tokenizer_type(
      TOKENIZER_PLAIN);

  // Setup classes to create the section manager
  SchemaUtil::TypeConfigMap type_config_map;
  type_config_map.emplace(type_with_non_string_properties.schema_type(),
                          type_with_non_string_properties);
  type_config_map.emplace(empty_type.schema_type(), empty_type);

  // DynamicTrieKeyMapper uses 3 internal arrays for bookkeeping. Give each one
  // 128KiB so the total DynamicTrieKeyMapper should get 384KiB
  int key_mapper_size = 3 * 128 * 1024;
  std::string dir = GetTestTempDir() + "/non_string_fields";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, dir,
                                                 key_mapper_size));
  ICING_ASSERT_OK(schema_type_mapper->Put(
      type_with_non_string_properties.schema_type(), /*schema_type_id=*/0));
  ICING_ASSERT_OK(schema_type_mapper->Put(empty_type.schema_type(),
                                          /*schema_type_id=*/1));

  // Use SchemaTypeManager factory method to instantiate SectionManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, schema_type_mapper.get()));

  // Create an empty document to be nested
  DocumentProto empty_document = DocumentBuilder()
                                     .SetKey("icing", "uri1")
                                     .SetSchema(empty_type.schema_type())
                                     .Build();

  // Create a document that follows "Schema"
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "uri2")
          .SetSchema(type_with_non_string_properties.schema_type())
          .AddInt64Property("int", 1)
          .AddDoubleProperty("double", 0.2)
          .AddBooleanProperty("boolean", true)
          .AddBytesProperty("bytes", "attachment bytes")
          .AddDocumentProperty("document", empty_document)
          .Build();

  // Extracts sections from 'Schema' document
  ICING_ASSERT_OK_AND_ASSIGN(
      SectionGroup section_group,
      schema_type_manager->section_manager().ExtractSections(document));
  EXPECT_THAT(section_group.string_sections, IsEmpty());
  EXPECT_THAT(section_group.integer_sections, IsEmpty());
}

TEST_F(SectionManagerTest,
       NonIntegerFieldsWithIntegerIndexingConfigDontCreateSections) {
  // Create a schema for an empty document.
  SchemaTypeConfigProto empty_type;
  empty_type.set_schema_type("EmptySchema");

  // Create a schema with all the non-integer fields
  SchemaTypeConfigProto type_with_non_integer_properties;
  type_with_non_integer_properties.set_schema_type("Schema");

  // Create an string property with a integer_indexing_config
  auto string_property = type_with_non_integer_properties.add_properties();
  string_property->set_property_name("string");
  string_property->set_data_type(TYPE_STRING);
  string_property->set_cardinality(CARDINALITY_REQUIRED);
  string_property->mutable_integer_indexing_config()->set_numeric_match_type(
      NUMERIC_MATCH_RANGE);

  // Create a double property with a integer_indexing_config
  auto double_property = type_with_non_integer_properties.add_properties();
  double_property->set_property_name("double");
  double_property->set_data_type(TYPE_DOUBLE);
  double_property->set_cardinality(CARDINALITY_REQUIRED);
  double_property->mutable_integer_indexing_config()->set_numeric_match_type(
      NUMERIC_MATCH_RANGE);

  // Create a boolean property with a integer_indexing_config
  auto boolean_property = type_with_non_integer_properties.add_properties();
  boolean_property->set_property_name("boolean");
  boolean_property->set_data_type(TYPE_BOOLEAN);
  boolean_property->set_cardinality(CARDINALITY_REQUIRED);
  boolean_property->mutable_integer_indexing_config()->set_numeric_match_type(
      NUMERIC_MATCH_RANGE);

  // Create a bytes property with a integer_indexing_config
  auto bytes_property = type_with_non_integer_properties.add_properties();
  bytes_property->set_property_name("bytes");
  bytes_property->set_data_type(TYPE_BYTES);
  bytes_property->set_cardinality(CARDINALITY_REQUIRED);
  bytes_property->mutable_integer_indexing_config()->set_numeric_match_type(
      NUMERIC_MATCH_RANGE);

  // Create a document property with a integer_indexing_config
  auto document_property = type_with_non_integer_properties.add_properties();
  document_property->set_property_name("document");
  document_property->set_data_type(TYPE_DOCUMENT);
  document_property->set_schema_type(empty_type.schema_type());
  document_property->set_cardinality(CARDINALITY_REQUIRED);
  document_property->mutable_integer_indexing_config()->set_numeric_match_type(
      NUMERIC_MATCH_RANGE);

  // Setup classes to create the section manager
  SchemaUtil::TypeConfigMap type_config_map;
  type_config_map.emplace(type_with_non_integer_properties.schema_type(),
                          type_with_non_integer_properties);
  type_config_map.emplace(empty_type.schema_type(), empty_type);

  // DynamicTrieKeyMapper uses 3 internal arrays for bookkeeping. Give each one
  // 128KiB so the total DynamicTrieKeyMapper should get 384KiB
  int key_mapper_size = 3 * 128 * 1024;
  std::string dir = GetTestTempDir() + "/non_integer_fields";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, dir,
                                                 key_mapper_size));
  ICING_ASSERT_OK(schema_type_mapper->Put(
      type_with_non_integer_properties.schema_type(), /*schema_type_id=*/0));
  ICING_ASSERT_OK(schema_type_mapper->Put(empty_type.schema_type(),
                                          /*schema_type_id=*/1));

  // Use SchemaTypeManager factory method to instantiate SectionManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, schema_type_mapper.get()));

  // Create an empty document to be nested
  DocumentProto empty_document = DocumentBuilder()
                                     .SetKey("icing", "uri1")
                                     .SetSchema(empty_type.schema_type())
                                     .Build();

  // Create a document that follows "Schema"
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "uri2")
          .SetSchema(type_with_non_integer_properties.schema_type())
          .AddStringProperty("string", "abc")
          .AddDoubleProperty("double", 0.2)
          .AddBooleanProperty("boolean", true)
          .AddBytesProperty("bytes", "attachment bytes")
          .AddDocumentProperty("document", empty_document)
          .Build();

  // Extracts sections from 'Schema' document
  ICING_ASSERT_OK_AND_ASSIGN(
      SectionGroup section_group,
      schema_type_manager->section_manager().ExtractSections(document));
  EXPECT_THAT(section_group.string_sections, IsEmpty());
  EXPECT_THAT(section_group.integer_sections, IsEmpty());
}

TEST_F(SectionManagerTest, AssignSectionsRecursivelyForDocumentFields) {
  // Create the inner schema that the document property is.
  SchemaTypeConfigProto document_type;
  document_type.set_schema_type("DocumentSchema");

  auto string_property = document_type.add_properties();
  string_property->set_property_name("string");
  string_property->set_data_type(TYPE_STRING);
  string_property->set_cardinality(CARDINALITY_REQUIRED);
  string_property->mutable_string_indexing_config()->set_term_match_type(
      TERM_MATCH_EXACT);
  string_property->mutable_string_indexing_config()->set_tokenizer_type(
      TOKENIZER_PLAIN);

  auto integer_property = document_type.add_properties();
  integer_property->set_property_name("integer");
  integer_property->set_data_type(TYPE_INT64);
  integer_property->set_cardinality(CARDINALITY_REQUIRED);
  integer_property->mutable_integer_indexing_config()->set_numeric_match_type(
      NUMERIC_MATCH_RANGE);

  // Create the outer schema which has the document property.
  SchemaTypeConfigProto type;
  type.set_schema_type("Schema");

  auto document_property = type.add_properties();
  document_property->set_property_name("document");
  document_property->set_data_type(TYPE_DOCUMENT);
  document_property->set_schema_type(document_type.schema_type());
  document_property->set_cardinality(CARDINALITY_REQUIRED);

  // Opt into recursing into the document fields.
  document_property->mutable_document_indexing_config()
      ->set_index_nested_properties(true);

  // Create the inner document.
  DocumentProto inner_document = DocumentBuilder()
                                     .SetKey("icing", "uri1")
                                     .SetSchema(document_type.schema_type())
                                     .AddStringProperty("string", "foo")
                                     .AddInt64Property("integer", 123)
                                     .Build();

  // Create the outer document that holds the inner document
  DocumentProto outer_document =
      DocumentBuilder()
          .SetKey("icing", "uri2")
          .SetSchema(type.schema_type())
          .AddDocumentProperty("document", inner_document)
          .Build();

  // Setup classes to create the section manager
  SchemaUtil::TypeConfigMap type_config_map;
  type_config_map.emplace(type.schema_type(), type);
  type_config_map.emplace(document_type.schema_type(), document_type);

  // DynamicTrieKeyMapper uses 3 internal arrays for bookkeeping. Give each one
  // 128KiB so the total DynamicTrieKeyMapper should get 384KiB
  int key_mapper_size = 3 * 128 * 1024;
  std::string dir = GetTestTempDir() + "/recurse_into_document";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, dir,
                                                 key_mapper_size));
  int type_schema_type_id = 0;
  int document_type_schema_type_id = 1;
  ICING_ASSERT_OK(
      schema_type_mapper->Put(type.schema_type(), type_schema_type_id));
  ICING_ASSERT_OK(schema_type_mapper->Put(document_type.schema_type(),
                                          document_type_schema_type_id));

  // Use SchemaTypeManager factory method to instantiate SectionManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, schema_type_mapper.get()));

  // Extracts sections from 'Schema' document; there should be the 1 string
  // property and 1 integer property inside the document.
  ICING_ASSERT_OK_AND_ASSIGN(
      SectionGroup section_group,
      schema_type_manager->section_manager().ExtractSections(outer_document));
  EXPECT_THAT(section_group.string_sections, SizeIs(1));
  EXPECT_THAT(section_group.integer_sections, SizeIs(1));
}

TEST_F(SectionManagerTest, DontAssignSectionsRecursivelyForDocumentFields) {
  // Create the inner schema that the document property is.
  SchemaTypeConfigProto document_type;
  document_type.set_schema_type("DocumentSchema");

  auto string_property = document_type.add_properties();
  string_property->set_property_name("string");
  string_property->set_data_type(TYPE_STRING);
  string_property->set_cardinality(CARDINALITY_REQUIRED);
  string_property->mutable_string_indexing_config()->set_term_match_type(
      TERM_MATCH_EXACT);
  string_property->mutable_string_indexing_config()->set_tokenizer_type(
      TOKENIZER_PLAIN);

  auto integer_property = document_type.add_properties();
  integer_property->set_property_name("integer");
  integer_property->set_data_type(TYPE_INT64);
  integer_property->set_cardinality(CARDINALITY_REQUIRED);
  integer_property->mutable_integer_indexing_config()->set_numeric_match_type(
      NUMERIC_MATCH_RANGE);

  // Create the outer schema which has the document property.
  SchemaTypeConfigProto type;
  type.set_schema_type("Schema");

  auto document_property = type.add_properties();
  document_property->set_property_name("document");
  document_property->set_data_type(TYPE_DOCUMENT);
  document_property->set_schema_type(document_type.schema_type());
  document_property->set_cardinality(CARDINALITY_REQUIRED);

  // Opt into recursing into the document fields.
  document_property->mutable_document_indexing_config()
      ->set_index_nested_properties(false);

  // Create the inner document.
  DocumentProto inner_document = DocumentBuilder()
                                     .SetKey("icing", "uri1")
                                     .SetSchema(document_type.schema_type())
                                     .AddStringProperty("string", "foo")
                                     .AddInt64Property("integer", 123)
                                     .Build();

  // Create the outer document that holds the inner document
  DocumentProto outer_document =
      DocumentBuilder()
          .SetKey("icing", "uri2")
          .SetSchema(type.schema_type())
          .AddDocumentProperty("document", inner_document)
          .Build();

  // Setup classes to create the section manager
  SchemaUtil::TypeConfigMap type_config_map;
  type_config_map.emplace(type.schema_type(), type);
  type_config_map.emplace(document_type.schema_type(), document_type);

  // DynamicTrieKeyMapper uses 3 internal arrays for bookkeeping. Give each one
  // 128KiB so the total DynamicTrieKeyMapper should get 384KiB
  int key_mapper_size = 3 * 128 * 1024;
  std::string dir = GetTestTempDir() + "/recurse_into_document";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<SchemaTypeId>> schema_type_mapper,
      DynamicTrieKeyMapper<SchemaTypeId>::Create(filesystem_, dir,
                                                 key_mapper_size));
  int type_schema_type_id = 0;
  int document_type_schema_type_id = 1;
  ICING_ASSERT_OK(
      schema_type_mapper->Put(type.schema_type(), type_schema_type_id));
  ICING_ASSERT_OK(schema_type_mapper->Put(document_type.schema_type(),
                                          document_type_schema_type_id));

  // Use SchemaTypeManager factory method to instantiate SectionManager.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaTypeManager> schema_type_manager,
      SchemaTypeManager::Create(type_config_map, schema_type_mapper.get()));

  // Extracts sections from 'Schema' document; there won't be any since we
  // didn't recurse into the document to see the inner string property
  ICING_ASSERT_OK_AND_ASSIGN(
      SectionGroup section_group,
      schema_type_manager->section_manager().ExtractSections(outer_document));
  EXPECT_THAT(section_group.string_sections, IsEmpty());
  EXPECT_THAT(section_group.integer_sections, IsEmpty());
}

}  // namespace

}  // namespace lib
}  // namespace icing
