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

#include "icing/icing-search-engine.h"

#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/file/mock-filesystem.h"
#include "icing/jni/jni-cache.h"
#include "icing/portable/endian.h"
#include "icing/portable/equals-proto.h"
#include "icing/portable/platform.h"
#include "icing/proto/debug.pb.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/document_wrapper.pb.h"
#include "icing/proto/initialize.pb.h"
#include "icing/proto/logging.pb.h"
#include "icing/proto/optimize.pb.h"
#include "icing/proto/persist.pb.h"
#include "icing/proto/reset.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/status.pb.h"
#include "icing/proto/storage.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/proto/usage.pb.h"
#include "icing/schema-builder.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::icing::lib::portable_equals_proto::EqualsProto;
using ::testing::Eq;
using ::testing::Ge;
using ::testing::Gt;
using ::testing::HasSubstr;
using ::testing::IsEmpty;
using ::testing::Return;
using ::testing::SizeIs;
using ::testing::StrEq;
using ::testing::UnorderedElementsAre;

// For mocking purpose, we allow tests to provide a custom Filesystem.
class TestIcingSearchEngine : public IcingSearchEngine {
 public:
  TestIcingSearchEngine(const IcingSearchEngineOptions& options,
                        std::unique_ptr<const Filesystem> filesystem,
                        std::unique_ptr<const IcingFilesystem> icing_filesystem,
                        std::unique_ptr<Clock> clock,
                        std::unique_ptr<JniCache> jni_cache)
      : IcingSearchEngine(options, std::move(filesystem),
                          std::move(icing_filesystem), std::move(clock),
                          std::move(jni_cache)) {}
};

std::string GetTestBaseDir() { return GetTestTempDir() + "/icing"; }

// This test is meant to cover all tests relating to IcingSearchEngine apis not
// specifically covered by the other IcingSearchEngine*Test.
class IcingSearchEngineTest : public testing::Test {
 protected:
  void SetUp() override {
    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      // If we've specified using the reverse-JNI method for segmentation (i.e.
      // not ICU), then we won't have the ICU data file included to set up.
      // Technically, we could choose to use reverse-JNI for segmentation AND
      // include an ICU data file, but that seems unlikely and our current BUILD
      // setup doesn't do this.
      // File generated via icu_data_file rule in //icing/BUILD.
      std::string icu_data_file_path =
          GetTestFilePath("icing/icu.dat");
      ICING_ASSERT_OK(
          icu_data_file_helper::SetUpICUDataFile(icu_data_file_path));
    }
    filesystem_.CreateDirectoryRecursively(GetTestBaseDir().c_str());
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(GetTestBaseDir().c_str());
  }

  const Filesystem* filesystem() const { return &filesystem_; }

 private:
  Filesystem filesystem_;
};

// Non-zero value so we don't override it to be the current time
constexpr int64_t kDefaultCreationTimestampMs = 1575492852000;

IcingSearchEngineOptions GetDefaultIcingOptions() {
  IcingSearchEngineOptions icing_options;
  icing_options.set_base_dir(GetTestBaseDir());
  return icing_options;
}

DocumentProto CreateMessageDocument(std::string name_space, std::string uri) {
  return DocumentBuilder()
      .SetKey(std::move(name_space), std::move(uri))
      .SetSchema("Message")
      .AddStringProperty("body", "message body")
      .SetCreationTimestampMs(kDefaultCreationTimestampMs)
      .Build();
}

SchemaProto CreateMessageSchema() {
  return SchemaBuilder()
      .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
          PropertyConfigBuilder()
              .SetName("body")
              .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
              .SetCardinality(CARDINALITY_REQUIRED)))
      .Build();
}

SchemaProto CreatePersonAndEmailSchema() {
  return SchemaBuilder()
      .AddType(SchemaTypeConfigBuilder()
                   .SetType("Person")
                   .AddProperty(PropertyConfigBuilder()
                                    .SetName("name")
                                    .SetDataTypeString(TERM_MATCH_PREFIX,
                                                       TOKENIZER_PLAIN)
                                    .SetCardinality(CARDINALITY_OPTIONAL))
                   .AddProperty(PropertyConfigBuilder()
                                    .SetName("emailAddress")
                                    .SetDataTypeString(TERM_MATCH_PREFIX,
                                                       TOKENIZER_PLAIN)
                                    .SetCardinality(CARDINALITY_OPTIONAL)))
      .AddType(
          SchemaTypeConfigBuilder()
              .SetType("Email")
              .AddProperty(
                  PropertyConfigBuilder()
                      .SetName("body")
                      .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                      .SetCardinality(CARDINALITY_OPTIONAL))
              .AddProperty(
                  PropertyConfigBuilder()
                      .SetName("subject")
                      .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                      .SetCardinality(CARDINALITY_OPTIONAL))
              .AddProperty(PropertyConfigBuilder()
                               .SetName("sender")
                               .SetDataTypeDocument(
                                   "Person", /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL)))
      .Build();
}

ScoringSpecProto GetDefaultScoringSpec() {
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);
  return scoring_spec;
}

UsageReport CreateUsageReport(std::string name_space, std::string uri,
                              int64_t timestamp_ms,
                              UsageReport::UsageType usage_type) {
  UsageReport usage_report;
  usage_report.set_document_namespace(name_space);
  usage_report.set_document_uri(uri);
  usage_report.set_usage_timestamp_ms(timestamp_ms);
  usage_report.set_usage_type(usage_type);
  return usage_report;
}

TEST_F(IcingSearchEngineTest, GetDocument) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Simple put and get
  ASSERT_THAT(icing.Put(CreateMessageDocument("namespace", "uri")).status(),
              ProtoIsOk());

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() =
      CreateMessageDocument("namespace", "uri");
  ASSERT_THAT(
      icing.Get("namespace", "uri", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Put an invalid document
  PutResultProto put_result_proto = icing.Put(DocumentProto());
  EXPECT_THAT(put_result_proto.status(),
              ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
  EXPECT_THAT(put_result_proto.status().message(),
              HasSubstr("'namespace' is empty"));

  // Get a non-existing key
  expected_get_result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
  expected_get_result_proto.mutable_status()->set_message(
      "Document (wrong, uri) not found.");
  expected_get_result_proto.clear_document();
  ASSERT_THAT(icing.Get("wrong", "uri", GetResultSpecProto::default_instance()),
              EqualsProto(expected_get_result_proto));
}

TEST_F(IcingSearchEngineTest, GetDocumentProjectionEmpty) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document = CreateMessageDocument("namespace", "uri");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  GetResultSpecProto result_spec;
  TypePropertyMask* mask = result_spec.add_type_property_masks();
  mask->set_schema_type(document.schema());
  mask->add_paths("");

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = document;
  expected_get_result_proto.mutable_document()->clear_properties();
  ASSERT_THAT(icing.Get("namespace", "uri", result_spec),
              EqualsProto(expected_get_result_proto));
}

TEST_F(IcingSearchEngineTest, GetDocumentWildCardProjectionEmpty) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document = CreateMessageDocument("namespace", "uri");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  GetResultSpecProto result_spec;
  TypePropertyMask* mask = result_spec.add_type_property_masks();
  mask->set_schema_type("*");
  mask->add_paths("");

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = document;
  expected_get_result_proto.mutable_document()->clear_properties();
  ASSERT_THAT(icing.Get("namespace", "uri", result_spec),
              EqualsProto(expected_get_result_proto));
}

TEST_F(IcingSearchEngineTest, GetDocumentProjectionMultipleFieldPaths) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add an email document
  DocumentProto document =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender",
              DocumentBuilder()
                  .SetKey("namespace", "uri1")
                  .SetSchema("Person")
                  .AddStringProperty("name", "Meg Ryan")
                  .AddStringProperty("emailAddress", "shopgirl@aol.com")
                  .Build())
          .AddStringProperty("subject", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  GetResultSpecProto result_spec;
  TypePropertyMask* mask = result_spec.add_type_property_masks();
  mask->set_schema_type("Email");
  mask->add_paths("sender.name");
  mask->add_paths("subject");

  // 2. Verify that the returned result only contains the 'sender.name'
  // property and the 'subject' property.
  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty("sender",
                               DocumentBuilder()
                                   .SetKey("namespace", "uri1")
                                   .SetSchema("Person")
                                   .AddStringProperty("name", "Meg Ryan")
                                   .Build())
          .AddStringProperty("subject", "Hello World!")
          .Build();
  ASSERT_THAT(icing.Get("namespace", "uri1", result_spec),
              EqualsProto(expected_get_result_proto));
}

TEST_F(IcingSearchEngineTest, GetDocumentWildcardProjectionMultipleFieldPaths) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add an email document
  DocumentProto document =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender",
              DocumentBuilder()
                  .SetKey("namespace", "uri1")
                  .SetSchema("Person")
                  .AddStringProperty("name", "Meg Ryan")
                  .AddStringProperty("emailAddress", "shopgirl@aol.com")
                  .Build())
          .AddStringProperty("subject", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  GetResultSpecProto result_spec;
  TypePropertyMask* mask = result_spec.add_type_property_masks();
  mask->set_schema_type("*");
  mask->add_paths("sender.name");
  mask->add_paths("subject");

  // 2. Verify that the returned result only contains the 'sender.name'
  // property and the 'subject' property.
  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty("sender",
                               DocumentBuilder()
                                   .SetKey("namespace", "uri1")
                                   .SetSchema("Person")
                                   .AddStringProperty("name", "Meg Ryan")
                                   .Build())
          .AddStringProperty("subject", "Hello World!")
          .Build();
  ASSERT_THAT(icing.Get("namespace", "uri1", result_spec),
              EqualsProto(expected_get_result_proto));
}

TEST_F(IcingSearchEngineTest,
       GetDocumentSpecificProjectionOverridesWildcardProjection) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add an email document
  DocumentProto document =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender",
              DocumentBuilder()
                  .SetKey("namespace", "uri1")
                  .SetSchema("Person")
                  .AddStringProperty("name", "Meg Ryan")
                  .AddStringProperty("emailAddress", "shopgirl@aol.com")
                  .Build())
          .AddStringProperty("subject", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  // 2. Add type property masks for the wildcard and the specific type of the
  // document 'Email'. The wildcard should be ignored and only the 'Email'
  // projection should apply.
  GetResultSpecProto result_spec;
  TypePropertyMask* mask = result_spec.add_type_property_masks();
  mask->set_schema_type("*");
  mask->add_paths("subject");
  mask = result_spec.add_type_property_masks();
  mask->set_schema_type("Email");
  mask->add_paths("body");

  // 3. Verify that the returned result only contains the 'body' property.
  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ASSERT_THAT(icing.Get("namespace", "uri1", result_spec),
              EqualsProto(expected_get_result_proto));
}

TEST_F(IcingSearchEngineTest, GetDocumentProjectionPolymorphism) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Person")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("name")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emailAddress")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Artist")
                       .AddParentType("Person")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("name")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emailAddress")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("company")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  // Add a person document and an artist document
  DocumentProto document_person =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Meg Ryan")
          .AddStringProperty("emailAddress", "shopgirl@aol.com")
          .Build();
  DocumentProto document_artist =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Artist")
          .AddStringProperty("name", "Meg Artist")
          .AddStringProperty("emailAddress", "artist@aol.com")
          .AddStringProperty("company", "aol")
          .Build();
  ASSERT_THAT(icing.Put(document_person).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document_artist).status(), ProtoIsOk());

  // Add type property masks
  GetResultSpecProto result_spec;
  TypePropertyMask* person_type_property_mask =
      result_spec.add_type_property_masks();
  person_type_property_mask->set_schema_type("Person");
  person_type_property_mask->add_paths("name");
  // Since Artist is a child type of Person, the TypePropertyMask for Person
  // will be merged to Artist's TypePropertyMask by polymorphism, so that 'name'
  // will also show in Artist's projection results.
  TypePropertyMask* artist_type_property_mask =
      result_spec.add_type_property_masks();
  artist_type_property_mask->set_schema_type("Artist");
  artist_type_property_mask->add_paths("emailAddress");

  // Verify that the returned person document only contains the 'name' property,
  // and the returned artist document contain both the 'name' and 'emailAddress'
  // properties.
  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Meg Ryan")
          .Build();
  ASSERT_THAT(icing.Get("namespace", "uri1", result_spec),
              EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Artist")
          .AddStringProperty("name", "Meg Artist")
          .AddStringProperty("emailAddress", "artist@aol.com")
          .Build();
  ASSERT_THAT(icing.Get("namespace", "uri2", result_spec),
              EqualsProto(expected_get_result_proto));
}

TEST_F(IcingSearchEngineTest, GetDocumentProjectionMultipleParentPolymorphism) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("sender")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("recipient")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("content")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("note")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("EmailMessage")
                       .AddParentType("Email")
                       .AddParentType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("sender")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("recipient")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("content")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("note")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  // Add an email document and a message document
  DocumentProto document_email =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("sender", "sender1")
          .AddStringProperty("recipient", "recipient1")
          .Build();
  DocumentProto document_message = DocumentBuilder()
                                       .SetKey("namespace", "uri2")
                                       .SetCreationTimestampMs(1000)
                                       .SetSchema("Message")
                                       .AddStringProperty("content", "content1")
                                       .AddStringProperty("note", "note1")
                                       .Build();
  // Add an emailMessage document
  DocumentProto document_email_message =
      DocumentBuilder()
          .SetKey("namespace", "uri3")
          .SetCreationTimestampMs(1000)
          .SetSchema("EmailMessage")
          .AddStringProperty("sender", "sender2")
          .AddStringProperty("recipient", "recipient2")
          .AddStringProperty("content", "content2")
          .AddStringProperty("note", "note2")
          .Build();

  ASSERT_THAT(icing.Put(document_email).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document_message).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document_email_message).status(), ProtoIsOk());

  // Add type property masks for Email and Message, and both of them will apply
  // to EmailMessage.
  GetResultSpecProto result_spec;
  TypePropertyMask* email_type_property_mask =
      result_spec.add_type_property_masks();
  email_type_property_mask->set_schema_type("Email");
  email_type_property_mask->add_paths("sender");

  TypePropertyMask* message_type_property_mask =
      result_spec.add_type_property_masks();
  message_type_property_mask->set_schema_type("Message");
  message_type_property_mask->add_paths("content");

  // Verify that
  // - The returned email document only contains the 'sender' property.
  // - The returned message document only contains the 'content' property.
  // - The returned email message document contains both the 'sender' and
  //   'content' properties,
  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("sender", "sender1")
          .Build();
  ASSERT_THAT(icing.Get("namespace", "uri1", result_spec),
              EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Message")
          .AddStringProperty("content", "content1")
          .Build();
  ASSERT_THAT(icing.Get("namespace", "uri2", result_spec),
              EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() =
      DocumentBuilder()
          .SetKey("namespace", "uri3")
          .SetCreationTimestampMs(1000)
          .SetSchema("EmailMessage")
          .AddStringProperty("sender", "sender2")
          .AddStringProperty("content", "content2")
          .Build();
  ASSERT_THAT(icing.Get("namespace", "uri3", result_spec),
              EqualsProto(expected_get_result_proto));
}

TEST_F(IcingSearchEngineTest, GetDocumentProjectionDiamondPolymorphism) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  // Create a schema with a diamond inheritance relation.
  //         Object
  //      /          \
  //   Email       Message
  //       \         /
  //      EmailMessage
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Object").AddProperty(
              PropertyConfigBuilder()
                  .SetName("objectId")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddParentType("Object")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("objectId")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("sender")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("recipient")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddParentType("Object")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("objectId")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("content")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("note")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("EmailMessage")
                       .AddParentType("Email")
                       .AddParentType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("objectId")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("sender")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("recipient")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("content")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("note")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  // Add an email document and a message document
  DocumentProto document_email =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("objectId", "object1")
          .AddStringProperty("sender", "sender1")
          .AddStringProperty("recipient", "recipient1")
          .Build();
  DocumentProto document_message = DocumentBuilder()
                                       .SetKey("namespace", "uri2")
                                       .SetCreationTimestampMs(1000)
                                       .SetSchema("Message")
                                       .AddStringProperty("objectId", "object2")
                                       .AddStringProperty("content", "content1")
                                       .AddStringProperty("note", "note1")
                                       .Build();
  // Add an emailMessage document
  DocumentProto document_email_message =
      DocumentBuilder()
          .SetKey("namespace", "uri3")
          .SetCreationTimestampMs(1000)
          .SetSchema("EmailMessage")
          .AddStringProperty("objectId", "object3")
          .AddStringProperty("sender", "sender2")
          .AddStringProperty("recipient", "recipient2")
          .AddStringProperty("content", "content2")
          .AddStringProperty("note", "note2")
          .Build();

  ASSERT_THAT(icing.Put(document_email).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document_message).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document_email_message).status(), ProtoIsOk());

  // Add type property masks for Object, which should apply to Email, Message
  // and EmailMessage.
  GetResultSpecProto result_spec;
  TypePropertyMask* email_type_property_mask =
      result_spec.add_type_property_masks();
  email_type_property_mask->set_schema_type("Object");
  email_type_property_mask->add_paths("objectId");

  // Verify that all the documents only contain the 'objectId' property.
  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("objectId", "object1")
          .Build();
  ASSERT_THAT(icing.Get("namespace", "uri1", result_spec),
              EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Message")
          .AddStringProperty("objectId", "object2")
          .Build();
  ASSERT_THAT(icing.Get("namespace", "uri2", result_spec),
              EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() =
      DocumentBuilder()
          .SetKey("namespace", "uri3")
          .SetCreationTimestampMs(1000)
          .SetSchema("EmailMessage")
          .AddStringProperty("objectId", "object3")
          .Build();
  ASSERT_THAT(icing.Get("namespace", "uri3", result_spec),
              EqualsProto(expected_get_result_proto));
}

TEST_F(IcingSearchEngineTest, OlderUsageTimestampShouldNotOverrideNewerOnes) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 3 test documents
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // Report usage for doc1 and doc2. The older timestamp 5000 shouldn't be
  // overridden by 1000. The order will be doc1 > doc2 when ranked by
  // USAGE_TYPE1_LAST_USED_TIMESTAMP.
  UsageReport usage_report_doc1_time1 = CreateUsageReport(
      /*name_space=*/"namespace", /*uri=*/"uri/1", /*timestamp_ms=*/1000,
      UsageReport::USAGE_TYPE1);
  UsageReport usage_report_doc1_time5 = CreateUsageReport(
      /*name_space=*/"namespace", /*uri=*/"uri/1", /*timestamp_ms=*/5000,
      UsageReport::USAGE_TYPE1);
  UsageReport usage_report_doc2_time3 = CreateUsageReport(
      /*name_space=*/"namespace", /*uri=*/"uri/2", /*timestamp_ms=*/3000,
      UsageReport::USAGE_TYPE1);
  ASSERT_THAT(icing.ReportUsage(usage_report_doc1_time5).status(), ProtoIsOk());
  ASSERT_THAT(icing.ReportUsage(usage_report_doc2_time3).status(), ProtoIsOk());
  ASSERT_THAT(icing.ReportUsage(usage_report_doc1_time1).status(), ProtoIsOk());

  // "m" will match both documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");

  // Result should be in descending USAGE_TYPE1_LAST_USED_TIMESTAMP order
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::USAGE_TYPE1_LAST_USED_TIMESTAMP);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_F(IcingSearchEngineTest, ImplicitPersistToDiskFullSavesEverything) {
  DocumentProto document = CreateMessageDocument("namespace", "uri");
  {
    IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
    EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
    EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());
    EXPECT_THAT(icing.Put(document).status(), ProtoIsOk());
  }  // Destructing calls a PersistToDisk(FULL)

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());

  // There should be no recovery since everything should be saved properly.
  InitializeResultProto init_result = icing.Initialize();
  EXPECT_THAT(init_result.status(), ProtoIsOk());
  EXPECT_THAT(init_result.initialize_stats().document_store_data_status(),
              Eq(InitializeStatsProto::NO_DATA_LOSS));
  EXPECT_THAT(init_result.initialize_stats().document_store_recovery_cause(),
              Eq(InitializeStatsProto::NONE));
  EXPECT_THAT(init_result.initialize_stats().schema_store_recovery_cause(),
              Eq(InitializeStatsProto::NONE));
  EXPECT_THAT(init_result.initialize_stats().index_restoration_cause(),
              Eq(InitializeStatsProto::NONE));

  // Schema is still intact.
  GetSchemaResultProto expected_get_schema_result_proto;
  expected_get_schema_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_schema_result_proto.mutable_schema() = CreateMessageSchema();

  EXPECT_THAT(icing.GetSchema(), EqualsProto(expected_get_schema_result_proto));

  // Documents are still intact.
  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = document;

  EXPECT_THAT(
      icing.Get("namespace", "uri", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Index is still intact.
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");  // Content in the Message document.

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document;

  SearchResultProto actual_results =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_search_result_proto));
}

TEST_F(IcingSearchEngineTest, ExplicitPersistToDiskFullSavesEverything) {
  DocumentProto document = CreateMessageDocument("namespace", "uri");

  // Add schema and documents to our first icing1 instance.
  IcingSearchEngine icing1(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing1.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing1.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());
  EXPECT_THAT(icing1.Put(document).status(), ProtoIsOk());
  EXPECT_THAT(icing1.PersistToDisk(PersistType::FULL).status(), ProtoIsOk());

  // Initialize a second icing2 instance which should have it's own memory
  // space. If data from icing1 isn't being persisted to the files, then icing2
  // won't be able to see those changes.
  IcingSearchEngine icing2(GetDefaultIcingOptions(), GetTestJniCache());

  // There should be no recovery since everything should be saved properly.
  InitializeResultProto init_result = icing2.Initialize();
  EXPECT_THAT(init_result.status(), ProtoIsOk());
  EXPECT_THAT(init_result.initialize_stats().document_store_data_status(),
              Eq(InitializeStatsProto::NO_DATA_LOSS));
  EXPECT_THAT(init_result.initialize_stats().document_store_recovery_cause(),
              Eq(InitializeStatsProto::NONE));
  EXPECT_THAT(init_result.initialize_stats().schema_store_recovery_cause(),
              Eq(InitializeStatsProto::NONE));
  EXPECT_THAT(init_result.initialize_stats().index_restoration_cause(),
              Eq(InitializeStatsProto::NONE));

  // Schema is still intact.
  GetSchemaResultProto expected_get_schema_result_proto;
  expected_get_schema_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_schema_result_proto.mutable_schema() = CreateMessageSchema();

  EXPECT_THAT(icing2.GetSchema(),
              EqualsProto(expected_get_schema_result_proto));

  // Documents are still intact.
  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = document;

  EXPECT_THAT(
      icing2.Get("namespace", "uri", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Index is still intact.
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");  // Content in the Message document.

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document;

  SearchResultProto actual_results =
      icing2.Search(search_spec, GetDefaultScoringSpec(),
                    ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_search_result_proto));
}

TEST_F(IcingSearchEngineTest, NoPersistToDiskLosesAllDocumentsAndIndex) {
  IcingSearchEngine icing1(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing1.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing1.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());
  DocumentProto document = CreateMessageDocument("namespace", "uri");
  EXPECT_THAT(icing1.Put(document).status(), ProtoIsOk());
  EXPECT_THAT(
      icing1.Get("namespace", "uri", GetResultSpecProto::default_instance())
          .document(),
      EqualsProto(document));

  // It's intentional that no PersistToDisk call is made before initializing a
  // second instance of icing.

  IcingSearchEngine icing2(GetDefaultIcingOptions(), GetTestJniCache());
  InitializeResultProto init_result = icing2.Initialize();
  EXPECT_THAT(init_result.status(), ProtoIsOk());
  EXPECT_THAT(init_result.initialize_stats().document_store_data_status(),
              Eq(InitializeStatsProto::PARTIAL_LOSS));
  EXPECT_THAT(init_result.initialize_stats().document_store_recovery_cause(),
              Eq(InitializeStatsProto::DATA_LOSS));
  EXPECT_THAT(init_result.initialize_stats().schema_store_recovery_cause(),
              Eq(InitializeStatsProto::NONE));
  EXPECT_THAT(init_result.initialize_stats().index_restoration_cause(),
              Eq(InitializeStatsProto::NONE));

  // The document shouldn't be found because we forgot to call
  // PersistToDisk(LITE)!
  EXPECT_THAT(
      icing2.Get("namespace", "uri", GetResultSpecProto::default_instance())
          .status(),
      ProtoStatusIs(StatusProto::NOT_FOUND));

  // Searching also shouldn't get us anything because the index wasn't
  // recovered.
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");  // Content in the Message document.

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);

  SearchResultProto actual_results =
      icing2.Search(search_spec, GetDefaultScoringSpec(),
                    ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_search_result_proto));
}

TEST_F(IcingSearchEngineTest, PersistToDiskLiteSavesGroundTruth) {
  DocumentProto document = CreateMessageDocument("namespace", "uri");

  IcingSearchEngine icing1(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing1.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing1.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());
  EXPECT_THAT(icing1.Put(document).status(), ProtoIsOk());
  EXPECT_THAT(icing1.PersistToDisk(PersistType::LITE).status(), ProtoIsOk());
  EXPECT_THAT(
      icing1.Get("namespace", "uri", GetResultSpecProto::default_instance())
          .document(),
      EqualsProto(document));

  IcingSearchEngine icing2(GetDefaultIcingOptions(), GetTestJniCache());
  InitializeResultProto init_result = icing2.Initialize();
  EXPECT_THAT(init_result.status(), ProtoIsOk());
  EXPECT_THAT(init_result.initialize_stats().document_store_data_status(),
              Eq(InitializeStatsProto::NO_DATA_LOSS));
  EXPECT_THAT(init_result.initialize_stats().schema_store_recovery_cause(),
              Eq(InitializeStatsProto::NONE));

  // A checksum mismatch gets reported as an IO error. The document store and
  // index didn't have their derived files included in the checksum previously,
  // so reinitializing will trigger a checksum mismatch.
  EXPECT_THAT(init_result.initialize_stats().document_store_recovery_cause(),
              Eq(InitializeStatsProto::IO_ERROR));
  EXPECT_THAT(init_result.initialize_stats().index_restoration_cause(),
              Eq(InitializeStatsProto::IO_ERROR));

  // Schema is still intact.
  GetSchemaResultProto expected_get_schema_result_proto;
  expected_get_schema_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_schema_result_proto.mutable_schema() = CreateMessageSchema();

  EXPECT_THAT(icing2.GetSchema(),
              EqualsProto(expected_get_schema_result_proto));

  // The document should be found because we called PersistToDisk(LITE)!
  EXPECT_THAT(
      icing2.Get("namespace", "uri", GetResultSpecProto::default_instance())
          .document(),
      EqualsProto(document));

  // Recovered index is still intact.
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");  // Content in the Message document.

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document;

  SearchResultProto actual_results =
      icing2.Search(search_spec, GetDefaultScoringSpec(),
                    ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_search_result_proto));
}

TEST_F(IcingSearchEngineTest, ResetOk) {
  SchemaProto message_schema = CreateMessageSchema();
  SchemaProto empty_schema = SchemaProto(message_schema);
  empty_schema.clear_types();

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(message_schema).status(), ProtoIsOk());

  int64_t empty_state_size =
      filesystem()->GetFileDiskUsage(GetTestBaseDir().c_str());

  DocumentProto document = CreateMessageDocument("namespace", "uri");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  // Check that things have been added
  EXPECT_THAT(filesystem()->GetDiskUsage(GetTestBaseDir().c_str()),
              Gt(empty_state_size));

  EXPECT_THAT(icing.Reset().status(), ProtoIsOk());

  // Check that we're back to an empty state
  EXPECT_EQ(filesystem()->GetFileDiskUsage(GetTestBaseDir().c_str()),
            empty_state_size);

  // Sanity check that we can still call other APIs. If things aren't cleared,
  // then this should raise an error since the empty schema is incompatible with
  // the old message_schema.
  EXPECT_THAT(icing.SetSchema(empty_schema).status(), ProtoIsOk());
}

TEST_F(IcingSearchEngineTest, ResetDeleteFailureCausesInternalError) {
  auto mock_filesystem = std::make_unique<MockFilesystem>();

  // This fails IcingSearchEngine::Reset() with status code INTERNAL and leaves
  // the IcingSearchEngine instance in an uninitialized state.
  ON_CALL(*mock_filesystem,
          DeleteDirectoryRecursively(StrEq(GetTestBaseDir().c_str())))
      .WillByDefault(Return(false));

  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::move(mock_filesystem),
                              std::make_unique<IcingFilesystem>(),
                              std::make_unique<FakeClock>(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document = CreateMessageDocument("namespace", "uri");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  EXPECT_THAT(icing.Reset().status(), ProtoStatusIs(StatusProto::INTERNAL));

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(
      StatusProto::FAILED_PRECONDITION);
  *expected_get_result_proto.mutable_document() = document;
  EXPECT_THAT(icing
                  .Get(document.namespace_(), document.uri(),
                       GetResultSpecProto::default_instance())
                  .status(),
              ProtoStatusIs(StatusProto::FAILED_PRECONDITION));
}

TEST_F(IcingSearchEngineTest, GetAllNamespaces) {
  DocumentProto namespace1 = DocumentBuilder()
                                 .SetKey("namespace1", "uri")
                                 .SetSchema("Message")
                                 .AddStringProperty("body", "message body")
                                 .SetCreationTimestampMs(100)
                                 .SetTtlMs(1000)
                                 .Build();
  DocumentProto namespace2_uri1 = DocumentBuilder()
                                      .SetKey("namespace2", "uri1")
                                      .SetSchema("Message")
                                      .AddStringProperty("body", "message body")
                                      .SetCreationTimestampMs(100)
                                      .SetTtlMs(1000)
                                      .Build();
  DocumentProto namespace2_uri2 = DocumentBuilder()
                                      .SetKey("namespace2", "uri2")
                                      .SetSchema("Message")
                                      .AddStringProperty("body", "message body")
                                      .SetCreationTimestampMs(100)
                                      .SetTtlMs(1000)
                                      .Build();

  DocumentProto namespace3 = DocumentBuilder()
                                 .SetKey("namespace3", "uri")
                                 .SetSchema("Message")
                                 .AddStringProperty("body", "message body")
                                 .SetCreationTimestampMs(100)
                                 .SetTtlMs(500)
                                 .Build();
  {
    // Some arbitrary time that's less than all the document's creation time +
    // ttl
    auto fake_clock = std::make_unique<FakeClock>();
    fake_clock->SetSystemTimeMilliseconds(500);

    TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                                std::make_unique<Filesystem>(),
                                std::make_unique<IcingFilesystem>(),
                                std::move(fake_clock), GetTestJniCache());

    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

    // No namespaces exist yet
    GetAllNamespacesResultProto result = icing.GetAllNamespaces();
    EXPECT_THAT(result.status(), ProtoIsOk());
    EXPECT_THAT(result.namespaces(), IsEmpty());

    ASSERT_THAT(icing.Put(namespace1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(namespace2_uri1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(namespace2_uri2).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(namespace3).status(), ProtoIsOk());

    // All namespaces should exist now
    result = icing.GetAllNamespaces();
    EXPECT_THAT(result.status(), ProtoIsOk());
    EXPECT_THAT(result.namespaces(),
                UnorderedElementsAre("namespace1", "namespace2", "namespace3"));

    // After deleting namespace2_uri1 document, we still have namespace2_uri2 in
    // "namespace2" so it should still show up
    ASSERT_THAT(icing.Delete("namespace2", "uri1").status(), ProtoIsOk());

    result = icing.GetAllNamespaces();
    EXPECT_THAT(result.status(), ProtoIsOk());
    EXPECT_THAT(result.namespaces(),
                UnorderedElementsAre("namespace1", "namespace2", "namespace3"));

    // After deleting namespace2_uri2 document, we no longer have any documents
    // in "namespace2"
    ASSERT_THAT(icing.Delete("namespace2", "uri2").status(), ProtoIsOk());

    result = icing.GetAllNamespaces();
    EXPECT_THAT(result.status(), ProtoIsOk());
    EXPECT_THAT(result.namespaces(),
                UnorderedElementsAre("namespace1", "namespace3"));
  }

  // We reinitialize here so we can feed in a fake clock this time
  {
    // Time needs to be past namespace3's creation time (100) + ttl (500) for it
    // to count as "expired"
    auto fake_clock = std::make_unique<FakeClock>();
    fake_clock->SetSystemTimeMilliseconds(1000);

    TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                                std::make_unique<Filesystem>(),
                                std::make_unique<IcingFilesystem>(),
                                std::move(fake_clock), GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

    // Only valid document left is the one in "namespace1"
    GetAllNamespacesResultProto result = icing.GetAllNamespaces();
    EXPECT_THAT(result.status(), ProtoIsOk());
    EXPECT_THAT(result.namespaces(), UnorderedElementsAre("namespace1"));
  }
}

TEST_F(IcingSearchEngineTest, StorageInfoTest) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Create three documents.
  DocumentProto document1 = CreateMessageDocument("namespace", "uri1");
  DocumentProto document2 = CreateMessageDocument("namespace", "uri2");
  DocumentProto document3 = CreateMessageDocument("namespace", "uri3");
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  // Ensure that total_storage_size is set. All the other stats are covered by
  // the classes that generate them.
  StorageInfoResultProto result = icing.GetStorageInfo();
  EXPECT_THAT(result.status(), ProtoIsOk());
  EXPECT_THAT(result.storage_info().total_storage_size(), Ge(0));
}

TEST_F(IcingSearchEngineTest, GetDebugInfoVerbosityBasicSucceeds) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Create a document.
  DocumentProto document = CreateMessageDocument("namespace", "email");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  DebugInfoResultProto result = icing.GetDebugInfo(DebugInfoVerbosity::BASIC);
  EXPECT_THAT(result.status(), ProtoIsOk());

  // Some sanity checks
  DebugInfoProto debug_info = result.debug_info();
  EXPECT_THAT(
      debug_info.document_info().document_storage_info().num_alive_documents(),
      Eq(1));
  EXPECT_THAT(debug_info.document_info().corpus_info(),
              IsEmpty());  // because verbosity=BASIC
  EXPECT_THAT(debug_info.schema_info().crc(), Gt(0));
}

TEST_F(IcingSearchEngineTest,
       GetDebugInfoVerbosityDetailedSucceedsWithCorpusInfo) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Create 4 documents.
  DocumentProto document1 = CreateMessageDocument("namespace1", "email/1");
  DocumentProto document2 = CreateMessageDocument("namespace1", "email/2");
  DocumentProto document3 = CreateMessageDocument("namespace2", "email/3");
  DocumentProto document4 = CreateMessageDocument("namespace2", "email/4");
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document4).status(), ProtoIsOk());

  DebugInfoResultProto result =
      icing.GetDebugInfo(DebugInfoVerbosity::DETAILED);
  EXPECT_THAT(result.status(), ProtoIsOk());

  // Some sanity checks
  DebugInfoProto debug_info = result.debug_info();
  EXPECT_THAT(
      debug_info.document_info().document_storage_info().num_alive_documents(),
      Eq(4));
  EXPECT_THAT(debug_info.document_info().corpus_info(), SizeIs(2));
  EXPECT_THAT(debug_info.schema_info().crc(), Gt(0));
}

TEST_F(IcingSearchEngineTest, GetDebugInfoUninitialized) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  DebugInfoResultProto result =
      icing.GetDebugInfo(DebugInfoVerbosity::DETAILED);
  EXPECT_THAT(result.status(), ProtoStatusIs(StatusProto::FAILED_PRECONDITION));
}

TEST_F(IcingSearchEngineTest, GetDebugInfoNoSchemaNoDocumentsSucceeds) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  DebugInfoResultProto result =
      icing.GetDebugInfo(DebugInfoVerbosity::DETAILED);
  ASSERT_THAT(result.status(), ProtoIsOk());
}

TEST_F(IcingSearchEngineTest, GetDebugInfoWithSchemaNoDocumentsSucceeds) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());
  DebugInfoResultProto result =
      icing.GetDebugInfo(DebugInfoVerbosity::DETAILED);
  ASSERT_THAT(result.status(), ProtoIsOk());
}

}  // namespace
}  // namespace lib
}  // namespace icing
