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

// This test is meant to cover all tests relating to IcingSearchEngine::Delete*.
class IcingSearchEngineDeleteTest : public testing::Test {
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

SchemaProto CreateMessageSchema() {
  return SchemaBuilder()
      .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
          PropertyConfigBuilder()
              .SetName("body")
              .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
              .SetCardinality(CARDINALITY_REQUIRED)))
      .Build();
}

SchemaProto CreateEmailSchema() {
  return SchemaBuilder()
      .AddType(SchemaTypeConfigBuilder()
                   .SetType("Email")
                   .AddProperty(PropertyConfigBuilder()
                                    .SetName("body")
                                    .SetDataTypeString(TERM_MATCH_PREFIX,
                                                       TOKENIZER_PLAIN)
                                    .SetCardinality(CARDINALITY_REQUIRED))
                   .AddProperty(PropertyConfigBuilder()
                                    .SetName("subject")
                                    .SetDataTypeString(TERM_MATCH_PREFIX,
                                                       TOKENIZER_PLAIN)
                                    .SetCardinality(CARDINALITY_REQUIRED)))
      .Build();
}

ScoringSpecProto GetDefaultScoringSpec() {
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);
  return scoring_spec;
}

TEST_F(IcingSearchEngineDeleteTest, DeleteBySchemaType) {
  SchemaProto schema;
  // Add an email type
  auto type = schema.add_types();
  type->set_schema_type("email");
  auto property = type->add_properties();
  property->set_property_name("subject");
  property->set_data_type(PropertyConfigProto::DataType::STRING);
  property->set_cardinality(PropertyConfigProto::Cardinality::OPTIONAL);
  property->mutable_string_indexing_config()->set_term_match_type(
      TermMatchType::EXACT_ONLY);
  property->mutable_string_indexing_config()->set_tokenizer_type(
      StringIndexingConfig::TokenizerType::PLAIN);
  // Add an message type
  type = schema.add_types();
  type->set_schema_type("message");
  property = type->add_properties();
  property->set_property_name("body");
  property->set_data_type(PropertyConfigProto::DataType::STRING);
  property->set_cardinality(PropertyConfigProto::Cardinality::OPTIONAL);
  property->mutable_string_indexing_config()->set_term_match_type(
      TermMatchType::EXACT_ONLY);
  property->mutable_string_indexing_config()->set_tokenizer_type(
      StringIndexingConfig::TokenizerType::PLAIN);
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("message")
          .AddStringProperty("body", "message body1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace2", "uri2")
          .SetSchema("email")
          .AddStringProperty("subject", "message body2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(7);
  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = document1;
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Delete the first type. The first doc should be irretrievable. The
  // second should still be present.
  DeleteBySchemaTypeResultProto result_proto =
      icing.DeleteBySchemaType("message");
  EXPECT_THAT(result_proto.status(), ProtoIsOk());
  DeleteStatsProto exp_stats;
  exp_stats.set_delete_type(DeleteStatsProto::DeleteType::SCHEMA_TYPE);
  exp_stats.set_latency_ms(7);
  exp_stats.set_num_documents_deleted(1);
  EXPECT_THAT(result_proto.delete_stats(), EqualsProto(exp_stats));

  expected_get_result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
  expected_get_result_proto.mutable_status()->set_message(
      "Document (namespace1, uri1) not found.");
  expected_get_result_proto.clear_document();
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  expected_get_result_proto.mutable_status()->clear_message();
  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Search for "message", only document2 should show up.
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("message");
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_F(IcingSearchEngineDeleteTest, DeleteSchemaTypeByQuery) {
  SchemaProto schema = CreateMessageSchema();
  // Add an email type
  SchemaProto tmp = CreateEmailSchema();
  *schema.add_types() = tmp.types(0);

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema(schema.types(0).schema_type())
          .AddStringProperty("body", "message body1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace2", "uri2")
          .SetSchema(schema.types(1).schema_type())
          .AddStringProperty("subject", "subject subject2")
          .AddStringProperty("body", "message body2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  EXPECT_THAT(icing.Put(document1).status(), ProtoIsOk());
  EXPECT_THAT(icing.Put(document2).status(), ProtoIsOk());

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = document1;
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Delete the first type. The first doc should be irretrievable. The
  // second should still be present.
  SearchSpecProto search_spec;
  search_spec.add_schema_type_filters(schema.types(0).schema_type());
  EXPECT_THAT(icing.DeleteByQuery(search_spec).status(), ProtoIsOk());

  expected_get_result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
  expected_get_result_proto.mutable_status()->set_message(
      "Document (namespace1, uri1) not found.");
  expected_get_result_proto.clear_document();
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  expected_get_result_proto.mutable_status()->clear_message();
  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  search_spec = SearchSpecProto::default_instance();
  search_spec.set_query("message");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_F(IcingSearchEngineDeleteTest, DeleteByNamespace) {
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace1", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace3", "uri3")
          .SetSchema("Message")
          .AddStringProperty("body", "message body2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(7);
  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = document1;
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace1", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() = document3;
  EXPECT_THAT(
      icing.Get("namespace3", "uri3", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Delete namespace1. Document1 and document2 should be irretrievable.
  // Document3 should still be present.
  DeleteByNamespaceResultProto result_proto =
      icing.DeleteByNamespace("namespace1");
  EXPECT_THAT(result_proto.status(), ProtoIsOk());
  DeleteStatsProto exp_stats;
  exp_stats.set_delete_type(DeleteStatsProto::DeleteType::NAMESPACE);
  exp_stats.set_latency_ms(7);
  exp_stats.set_num_documents_deleted(2);
  EXPECT_THAT(result_proto.delete_stats(), EqualsProto(exp_stats));

  expected_get_result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
  expected_get_result_proto.mutable_status()->set_message(
      "Document (namespace1, uri1) not found.");
  expected_get_result_proto.clear_document();
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  expected_get_result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
  expected_get_result_proto.mutable_status()->set_message(
      "Document (namespace1, uri2) not found.");
  expected_get_result_proto.clear_document();
  EXPECT_THAT(
      icing.Get("namespace1", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  expected_get_result_proto.mutable_status()->clear_message();
  *expected_get_result_proto.mutable_document() = document3;
  EXPECT_THAT(
      icing.Get("namespace3", "uri3", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Search for "message", only document3 should show up.
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("message");
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_F(IcingSearchEngineDeleteTest, DeleteNamespaceByQuery) {
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace2", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());
  EXPECT_THAT(icing.Put(document1).status(), ProtoIsOk());
  EXPECT_THAT(icing.Put(document2).status(), ProtoIsOk());

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = document1;
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Delete the first namespace. The first doc should be irretrievable. The
  // second should still be present.
  SearchSpecProto search_spec;
  search_spec.add_namespace_filters("namespace1");
  EXPECT_THAT(icing.DeleteByQuery(search_spec).status(), ProtoIsOk());

  expected_get_result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
  expected_get_result_proto.mutable_status()->set_message(
      "Document (namespace1, uri1) not found.");
  expected_get_result_proto.clear_document();
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  expected_get_result_proto.mutable_status()->clear_message();
  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  search_spec = SearchSpecProto::default_instance();
  search_spec.set_query("message");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_F(IcingSearchEngineDeleteTest, DeleteByQuery) {
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace2", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(7);
  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());
  EXPECT_THAT(icing.Put(document1).status(), ProtoIsOk());
  EXPECT_THAT(icing.Put(document2).status(), ProtoIsOk());

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = document1;
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Delete all docs containing 'body1'. The first doc should be irretrievable.
  // The second should still be present.
  SearchSpecProto search_spec;
  search_spec.set_query("body1");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  DeleteByQueryResultProto result_proto = icing.DeleteByQuery(search_spec);
  EXPECT_THAT(result_proto.status(), ProtoIsOk());
  DeleteByQueryStatsProto exp_stats;
  exp_stats.set_latency_ms(7);
  exp_stats.set_num_documents_deleted(1);
  exp_stats.set_query_length(search_spec.query().length());
  exp_stats.set_num_terms(1);
  exp_stats.set_num_namespaces_filtered(0);
  exp_stats.set_num_schema_types_filtered(0);
  exp_stats.set_parse_query_latency_ms(7);
  exp_stats.set_document_removal_latency_ms(7);
  EXPECT_THAT(result_proto.delete_by_query_stats(), EqualsProto(exp_stats));

  expected_get_result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
  expected_get_result_proto.mutable_status()->set_message(
      "Document (namespace1, uri1) not found.");
  expected_get_result_proto.clear_document();
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  expected_get_result_proto.mutable_status()->clear_message();
  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  search_spec = SearchSpecProto::default_instance();
  search_spec.set_query("message");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_F(IcingSearchEngineDeleteTest, DeleteByQueryReturnInfo) {
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace2", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace2", "uri3")
          .SetSchema("Message")
          .AddStringProperty("body", "message body3")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(7);
  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = document1;
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() = document3;
  EXPECT_THAT(
      icing.Get("namespace2", "uri3", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Delete all docs to test the information is correctly grouped.
  SearchSpecProto search_spec;
  search_spec.set_query("message");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  DeleteByQueryResultProto result_proto =
      icing.DeleteByQuery(search_spec, true);
  EXPECT_THAT(result_proto.status(), ProtoIsOk());
  DeleteByQueryStatsProto exp_stats;
  exp_stats.set_latency_ms(7);
  exp_stats.set_num_documents_deleted(3);
  exp_stats.set_query_length(search_spec.query().length());
  exp_stats.set_num_terms(1);
  exp_stats.set_num_namespaces_filtered(0);
  exp_stats.set_num_schema_types_filtered(0);
  exp_stats.set_parse_query_latency_ms(7);
  exp_stats.set_document_removal_latency_ms(7);
  EXPECT_THAT(result_proto.delete_by_query_stats(), EqualsProto(exp_stats));

  // Check that DeleteByQuery can return information for deleted documents.
  DeleteByQueryResultProto::DocumentGroupInfo info1, info2;
  info1.set_namespace_("namespace1");
  info1.set_schema("Message");
  info1.add_uris("uri1");
  info2.set_namespace_("namespace2");
  info2.set_schema("Message");
  info2.add_uris("uri3");
  info2.add_uris("uri2");
  EXPECT_THAT(result_proto.deleted_documents(),
              UnorderedElementsAre(EqualsProto(info1), EqualsProto(info2)));

  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance())
          .status()
          .code(),
      Eq(StatusProto::NOT_FOUND));
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance())
          .status()
          .code(),
      Eq(StatusProto::NOT_FOUND));
  EXPECT_THAT(
      icing.Get("namespace2", "uri3", GetResultSpecProto::default_instance())
          .status()
          .code(),
      Eq(StatusProto::NOT_FOUND));
}

TEST_F(IcingSearchEngineDeleteTest, DeleteByQueryNotFound) {
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace2", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());
  EXPECT_THAT(icing.Put(document1).status(), ProtoIsOk());
  EXPECT_THAT(icing.Put(document2).status(), ProtoIsOk());

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = document1;
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  // Delete all docs containing 'foo', which should be none of them. Both docs
  // should still be present.
  SearchSpecProto search_spec;
  search_spec.set_query("foo");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  EXPECT_THAT(icing.DeleteByQuery(search_spec).status(),
              ProtoStatusIs(StatusProto::NOT_FOUND));

  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  expected_get_result_proto.mutable_status()->clear_message();
  *expected_get_result_proto.mutable_document() = document1;
  EXPECT_THAT(
      icing.Get("namespace1", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  expected_get_result_proto.mutable_status()->clear_message();
  *expected_get_result_proto.mutable_document() = document2;
  EXPECT_THAT(
      icing.Get("namespace2", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  search_spec = SearchSpecProto::default_instance();
  search_spec.set_query("message");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

}  // namespace
}  // namespace lib
}  // namespace icing
