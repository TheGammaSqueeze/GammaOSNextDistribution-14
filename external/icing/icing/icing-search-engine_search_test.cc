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
#include "icing/icing-search-engine.h"
#include "icing/index/lite/term-id-hit-pair.h"
#include "icing/jni/jni-cache.h"
#include "icing/join/join-processor.h"
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
#include "icing/query/query-features.h"
#include "icing/result/result-state-manager.h"
#include "icing/schema-builder.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/util/snippet-helpers.h"

namespace icing {
namespace lib {

namespace {

using ::icing::lib::portable_equals_proto::EqualsProto;
using ::testing::DoubleEq;
using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::Gt;
using ::testing::IsEmpty;
using ::testing::Lt;
using ::testing::Ne;
using ::testing::SizeIs;

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

// This test is meant to cover all tests relating to IcingSearchEngine::Search
// and IcingSearchEngine::GetNextPage.
class IcingSearchEngineSearchTest
    : public ::testing::TestWithParam<SearchSpecProto::SearchType::Code> {
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
  icing_options.set_document_store_namespace_id_fingerprint(true);
  icing_options.set_use_new_qualified_id_join_index(true);
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

DocumentProto CreateEmailDocument(const std::string& name_space,
                                  const std::string& uri, int score,
                                  const std::string& subject_content,
                                  const std::string& body_content) {
  return DocumentBuilder()
      .SetKey(name_space, uri)
      .SetSchema("Email")
      .SetScore(score)
      .AddStringProperty("subject", subject_content)
      .AddStringProperty("body", body_content)
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

std::vector<std::string> GetUrisFromSearchResults(
    SearchResultProto& search_result_proto) {
  std::vector<std::string> result_uris;
  result_uris.reserve(search_result_proto.results_size());
  for (int i = 0; i < search_result_proto.results_size(); i++) {
    result_uris.push_back(
        search_result_proto.mutable_results(i)->document().uri());
  }
  return result_uris;
}

TEST_P(IcingSearchEngineSearchTest, SearchReturnsValidResults) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document_one = CreateMessageDocument("namespace", "uri1");
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two = CreateMessageDocument("namespace", "uri2");
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.mutable_snippet_spec()->set_max_window_utf32_length(64);
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(1);
  result_spec.mutable_snippet_spec()->set_num_to_snippet(1);

  SearchResultProto results =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(2));

  const DocumentProto& document = results.results(0).document();
  EXPECT_THAT(document, EqualsProto(document_two));

  const SnippetProto& snippet = results.results(0).snippet();
  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("message body"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)), ElementsAre("message"));

  EXPECT_THAT(results.results(1).document(), EqualsProto(document_one));
  EXPECT_THAT(results.results(1).snippet().entries(), IsEmpty());

  search_spec.set_query("foo");

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto actual_results =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchReturnsScoresDocumentScore) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document_one = CreateMessageDocument("namespace", "uri1");
  document_one.set_score(93);
  document_one.set_creation_timestamp_ms(10000);
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two = CreateMessageDocument("namespace", "uri2");
  document_two.set_score(15);
  document_two.set_creation_timestamp_ms(12000);
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());

  // Rank by DOCUMENT_SCORE and ensure that the score field is populated with
  // document score.
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  SearchResultProto results = icing.Search(search_spec, scoring_spec,
                                           ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(2));

  EXPECT_THAT(results.results(0).document(), EqualsProto(document_one));
  EXPECT_THAT(results.results(0).score(), 93);
  EXPECT_THAT(results.results(1).document(), EqualsProto(document_two));
  EXPECT_THAT(results.results(1).score(), 15);
}

TEST_P(IcingSearchEngineSearchTest, SearchReturnsScoresCreationTimestamp) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document_one = CreateMessageDocument("namespace", "uri1");
  document_one.set_score(93);
  document_one.set_creation_timestamp_ms(10000);
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two = CreateMessageDocument("namespace", "uri2");
  document_two.set_score(15);
  document_two.set_creation_timestamp_ms(12000);
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());

  // Rank by CREATION_TS and ensure that the score field is populated with
  // creation ts.
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);

  SearchResultProto results = icing.Search(search_spec, scoring_spec,
                                           ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(2));

  EXPECT_THAT(results.results(0).document(), EqualsProto(document_two));
  EXPECT_THAT(results.results(0).score(), 12000);
  EXPECT_THAT(results.results(1).document(), EqualsProto(document_one));
  EXPECT_THAT(results.results(1).score(), 10000);
}

TEST_P(IcingSearchEngineSearchTest, SearchReturnsOneResult) {
  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(1000);
  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document_one = CreateMessageDocument("namespace", "uri1");
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two = CreateMessageDocument("namespace", "uri2");
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document_two;

  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(search_result_proto.status(), ProtoIsOk());

  EXPECT_THAT(search_result_proto.query_stats().latency_ms(), Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats().document_retrieval_latency_ms(),
              Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats().lock_acquisition_latency_ms(),
              Eq(1000));
  // TODO(b/305098009): deprecate search-related flat fields in query_stats.
  EXPECT_THAT(search_result_proto.query_stats().parse_query_latency_ms(),
              Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats().scoring_latency_ms(), Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats().ranking_latency_ms(), Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .parse_query_latency_ms(),
              Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .scoring_latency_ms(),
              Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_documents_scored(),
              Eq(2));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_fetched_hits_lite_index(),
              Eq(2));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_fetched_hits_main_index(),
              Eq(0));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_fetched_hits_integer_index(),
              Eq(0));

  // The token is a random number so we don't verify it.
  expected_search_result_proto.set_next_page_token(
      search_result_proto.next_page_token());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchReturnsOneResult_readOnlyFalse) {
  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(1000);
  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document_one = CreateMessageDocument("namespace", "uri1");
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two = CreateMessageDocument("namespace", "uri2");
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());
  search_spec.set_use_read_only_search(false);

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document_two;

  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(search_result_proto.status(), ProtoIsOk());

  EXPECT_THAT(search_result_proto.query_stats().latency_ms(), Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats().document_retrieval_latency_ms(),
              Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats().lock_acquisition_latency_ms(),
              Eq(1000));
  // TODO(b/305098009): deprecate search-related flat fields in query_stats.
  EXPECT_THAT(search_result_proto.query_stats().parse_query_latency_ms(),
              Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats().scoring_latency_ms(), Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats().ranking_latency_ms(), Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .parse_query_latency_ms(),
              Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .scoring_latency_ms(),
              Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_documents_scored(),
              Eq(2));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_fetched_hits_lite_index(),
              Eq(2));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_fetched_hits_main_index(),
              Eq(0));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_fetched_hits_integer_index(),
              Eq(0));

  // The token is a random number so we don't verify it.
  expected_search_result_proto.set_next_page_token(
      search_result_proto.next_page_token());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchZeroResultLimitReturnsEmptyResults) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(0);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto actual_results =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchZeroResultLimitReturnsEmptyResults_readOnlyFalse) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("");
  search_spec.set_search_type(GetParam());
  search_spec.set_use_read_only_search(false);

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(0);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto actual_results =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchWithNumToScore) {
  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(1000);
  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document_one = CreateMessageDocument("namespace", "uri1");
  document_one.set_score(10);
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two = CreateMessageDocument("namespace", "uri2");
  document_two.set_score(5);
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(10);
  result_spec.set_num_to_score(10);

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();

  SearchResultProto expected_search_result_proto1;
  expected_search_result_proto1.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto1.mutable_results()->Add()->mutable_document() =
      document_one;
  *expected_search_result_proto1.mutable_results()->Add()->mutable_document() =
      document_two;

  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(search_result_proto.status(), ProtoIsOk());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto1));

  result_spec.set_num_to_score(1);
  // By setting num_to_score = 1, only document_two will be scored, ranked, and
  // returned.
  // - num_to_score cutoff is only affected by the reading order from posting
  //   list. IOW, since we read posting lists in doc id descending order,
  //   ScoringProcessor scores documents with higher doc ids first and cuts off
  //   if exceeding num_to_score.
  // - Therefore, even though document_one has higher score, ScoringProcessor
  //   still skips document_one, because posting list reads document_two first
  //   and ScoringProcessor stops after document_two given that total # of
  //   scored document has already reached num_to_score.
  SearchResultProto expected_search_result_google::protobuf;
  expected_search_result_google::protobuf.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_google::protobuf.mutable_results()->Add()->mutable_document() =
      document_two;

  search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(search_result_proto.status(), ProtoIsOk());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_google::protobuf));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchNegativeResultLimitReturnsInvalidArgument) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(-5);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(
      StatusProto::INVALID_ARGUMENT);
  expected_search_result_proto.mutable_status()->set_message(
      "ResultSpecProto.num_per_page cannot be negative.");
  SearchResultProto actual_results =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchNegativeResultLimitReturnsInvalidArgument_readOnlyFalse) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("");
  search_spec.set_search_type(GetParam());
  search_spec.set_use_read_only_search(false);

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(-5);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(
      StatusProto::INVALID_ARGUMENT);
  expected_search_result_proto.mutable_status()->set_message(
      "ResultSpecProto.num_per_page cannot be negative.");
  SearchResultProto actual_results =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchNonPositivePageTotalBytesLimitReturnsInvalidArgument) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.set_num_total_bytes_per_page_threshold(-1);

  SearchResultProto actual_results1 =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(actual_results1.status(),
              ProtoStatusIs(StatusProto::INVALID_ARGUMENT));

  result_spec.set_num_total_bytes_per_page_threshold(0);
  SearchResultProto actual_results2 =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(actual_results2.status(),
              ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchNegativeMaxJoinedChildrenPerParentReturnsInvalidArgument) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.set_max_joined_children_per_parent_to_return(-1);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(
      StatusProto::INVALID_ARGUMENT);
  expected_search_result_proto.mutable_status()->set_message(
      "ResultSpecProto.max_joined_children_per_parent_to_return cannot be "
      "negative.");
  SearchResultProto actual_results =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchNonPositiveNumToScoreReturnsInvalidArgument) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.set_num_to_score(-1);

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(
      StatusProto::INVALID_ARGUMENT);
  expected_search_result_proto.mutable_status()->set_message(
      "ResultSpecProto.num_to_score cannot be non-positive.");

  SearchResultProto actual_results1 =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(actual_results1, EqualsSearchResultIgnoreStatsAndScores(
                                   expected_search_result_proto));

  result_spec.set_num_to_score(0);
  SearchResultProto actual_results2 =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(actual_results2, EqualsSearchResultIgnoreStatsAndScores(
                                   expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchWithPersistenceReturnsValidResults) {
  IcingSearchEngineOptions icing_options = GetDefaultIcingOptions();

  {
    // Set the schema up beforehand.
    IcingSearchEngine icing(icing_options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());
    // Schema will be persisted to disk when icing goes out of scope.
  }

  {
    // Ensure that icing initializes the schema and section_manager
    // properly from the pre-existing file.
    IcingSearchEngine icing(icing_options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

    EXPECT_THAT(icing.Put(CreateMessageDocument("namespace", "uri")).status(),
                ProtoIsOk());
    // The index and document store will be persisted to disk when icing goes
    // out of scope.
  }

  {
    // Ensure that the index is brought back up without problems and we
    // can query for the content that we expect.
    IcingSearchEngine icing(icing_options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

    SearchSpecProto search_spec;
    search_spec.set_term_match_type(TermMatchType::PREFIX);
    search_spec.set_query("message");
    search_spec.set_search_type(GetParam());

    SearchResultProto expected_search_result_proto;
    expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
    *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
        CreateMessageDocument("namespace", "uri");

    SearchResultProto actual_results =
        icing.Search(search_spec, GetDefaultScoringSpec(),
                     ResultSpecProto::default_instance());
    EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                    expected_search_result_proto));

    search_spec.set_query("foo");

    SearchResultProto empty_result;
    empty_result.mutable_status()->set_code(StatusProto::OK);
    actual_results = icing.Search(search_spec, GetDefaultScoringSpec(),
                                  ResultSpecProto::default_instance());
    EXPECT_THAT(actual_results,
                EqualsSearchResultIgnoreStatsAndScores(empty_result));
  }
}

TEST_P(IcingSearchEngineSearchTest, SearchShouldReturnEmpty) {
  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(1000);
  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());

  // Empty result, no next-page token
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);

  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto.status(), ProtoIsOk());

  EXPECT_THAT(search_result_proto.query_stats().latency_ms(), Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats().document_retrieval_latency_ms(),
              Eq(0));
  EXPECT_THAT(search_result_proto.query_stats().lock_acquisition_latency_ms(),
              Eq(1000));
  // TODO(b/305098009): deprecate search-related flat fields in query_stats.
  EXPECT_THAT(search_result_proto.query_stats().parse_query_latency_ms(),
              Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats().scoring_latency_ms(), Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats().ranking_latency_ms(), Eq(0));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .parse_query_latency_ms(),
              Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .scoring_latency_ms(),
              Eq(1000));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_documents_scored(),
              Eq(0));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_fetched_hits_lite_index(),
              Eq(0));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_fetched_hits_main_index(),
              Eq(0));
  EXPECT_THAT(search_result_proto.query_stats()
                  .parent_search_stats()
                  .num_fetched_hits_integer_index(),
              Eq(0));

  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchShouldReturnMultiplePages) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates and inserts 5 documents
  DocumentProto document1 = CreateMessageDocument("namespace", "uri1");
  DocumentProto document2 = CreateMessageDocument("namespace", "uri2");
  DocumentProto document3 = CreateMessageDocument("namespace", "uri3");
  DocumentProto document4 = CreateMessageDocument("namespace", "uri4");
  DocumentProto document5 = CreateMessageDocument("namespace", "uri5");
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document4).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document5).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(2);

  // Searches and gets the first page, 2 results
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document5;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document4;
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(search_result_proto.next_page_token(), Gt(kInvalidNextPageToken));
  uint64_t next_page_token = search_result_proto.next_page_token();
  // Since the token is a random number, we don't need to verify
  expected_search_result_proto.set_next_page_token(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));

  // Second page, 2 results
  expected_search_result_proto.clear_results();
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  search_result_proto = icing.GetNextPage(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));

  // Third page, 1 result
  expected_search_result_proto.clear_results();
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;
  // Because there are no more results, we should not return the next page
  // token.
  expected_search_result_proto.clear_next_page_token();
  search_result_proto = icing.GetNextPage(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));

  // No more results
  expected_search_result_proto.clear_results();
  search_result_proto = icing.GetNextPage(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchWithNoScoringShouldReturnMultiplePages) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates and inserts 5 documents
  DocumentProto document1 = CreateMessageDocument("namespace", "uri1");
  DocumentProto document2 = CreateMessageDocument("namespace", "uri2");
  DocumentProto document3 = CreateMessageDocument("namespace", "uri3");
  DocumentProto document4 = CreateMessageDocument("namespace", "uri4");
  DocumentProto document5 = CreateMessageDocument("namespace", "uri5");
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document4).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document5).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::NONE);

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(2);

  // Searches and gets the first page, 2 results
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document5;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document4;
  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(search_result_proto.next_page_token(), Gt(kInvalidNextPageToken));
  uint64_t next_page_token = search_result_proto.next_page_token();
  // Since the token is a random number, we don't need to verify
  expected_search_result_proto.set_next_page_token(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));

  // Second page, 2 results
  expected_search_result_proto.clear_results();
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  search_result_proto = icing.GetNextPage(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));

  // Third page, 1 result
  expected_search_result_proto.clear_results();
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;
  // Because there are no more results, we should not return the next page
  // token.
  expected_search_result_proto.clear_next_page_token();
  search_result_proto = icing.GetNextPage(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));

  // No more results
  expected_search_result_proto.clear_results();
  search_result_proto = icing.GetNextPage(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchWithUnknownEnabledFeatureShouldReturnError) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());
  search_spec.add_enabled_features("BAD_FEATURE");

  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto.status(),
              ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
}

TEST_P(IcingSearchEngineSearchTest, ShouldReturnMultiplePagesWithSnippets) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates and inserts 5 documents
  DocumentProto document1 = CreateMessageDocument("namespace", "uri1");
  DocumentProto document2 = CreateMessageDocument("namespace", "uri2");
  DocumentProto document3 = CreateMessageDocument("namespace", "uri3");
  DocumentProto document4 = CreateMessageDocument("namespace", "uri4");
  DocumentProto document5 = CreateMessageDocument("namespace", "uri5");
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document4).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document5).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(2);
  result_spec.mutable_snippet_spec()->set_max_window_utf32_length(64);
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(1);
  result_spec.mutable_snippet_spec()->set_num_to_snippet(3);

  // Searches and gets the first page, 2 results with 2 snippets
  SearchResultProto search_result =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  ASSERT_THAT(search_result.status(), ProtoIsOk());
  ASSERT_THAT(search_result.results(), SizeIs(2));
  ASSERT_THAT(search_result.next_page_token(), Gt(kInvalidNextPageToken));

  const DocumentProto& document_result_1 = search_result.results(0).document();
  EXPECT_THAT(document_result_1, EqualsProto(document5));
  const SnippetProto& snippet_result_1 = search_result.results(0).snippet();
  EXPECT_THAT(snippet_result_1.entries(), SizeIs(1));
  EXPECT_THAT(snippet_result_1.entries(0).property_name(), Eq("body"));
  std::string_view content = GetString(
      &document_result_1, snippet_result_1.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet_result_1.entries(0)),
              ElementsAre("message body"));
  EXPECT_THAT(GetMatches(content, snippet_result_1.entries(0)),
              ElementsAre("message"));

  const DocumentProto& document_result_2 = search_result.results(1).document();
  EXPECT_THAT(document_result_2, EqualsProto(document4));
  const SnippetProto& snippet_result_2 = search_result.results(1).snippet();
  EXPECT_THAT(snippet_result_2.entries(0).property_name(), Eq("body"));
  content = GetString(&document_result_2,
                      snippet_result_2.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet_result_2.entries(0)),
              ElementsAre("message body"));
  EXPECT_THAT(GetMatches(content, snippet_result_2.entries(0)),
              ElementsAre("message"));

  // Second page, 2 result with 1 snippet
  search_result = icing.GetNextPage(search_result.next_page_token());
  ASSERT_THAT(search_result.status(), ProtoIsOk());
  ASSERT_THAT(search_result.results(), SizeIs(2));
  ASSERT_THAT(search_result.next_page_token(), Gt(kInvalidNextPageToken));

  const DocumentProto& document_result_3 = search_result.results(0).document();
  EXPECT_THAT(document_result_3, EqualsProto(document3));
  const SnippetProto& snippet_result_3 = search_result.results(0).snippet();
  EXPECT_THAT(snippet_result_3.entries(0).property_name(), Eq("body"));
  content = GetString(&document_result_3,
                      snippet_result_3.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet_result_3.entries(0)),
              ElementsAre("message body"));
  EXPECT_THAT(GetMatches(content, snippet_result_3.entries(0)),
              ElementsAre("message"));

  EXPECT_THAT(search_result.results(1).document(), EqualsProto(document2));
  EXPECT_THAT(search_result.results(1).snippet().entries(), IsEmpty());

  // Third page, 1 result with 0 snippets
  search_result = icing.GetNextPage(search_result.next_page_token());
  ASSERT_THAT(search_result.status(), ProtoIsOk());
  ASSERT_THAT(search_result.results(), SizeIs(1));
  ASSERT_THAT(search_result.next_page_token(), Eq(kInvalidNextPageToken));

  EXPECT_THAT(search_result.results(0).document(), EqualsProto(document1));
  EXPECT_THAT(search_result.results(0).snippet().entries(), IsEmpty());
}

TEST_P(IcingSearchEngineSearchTest, ShouldInvalidateNextPageToken) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document1 = CreateMessageDocument("namespace", "uri1");
  DocumentProto document2 = CreateMessageDocument("namespace", "uri2");
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);

  // Searches and gets the first page, 1 result
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(search_result_proto.next_page_token(), Gt(kInvalidNextPageToken));
  uint64_t next_page_token = search_result_proto.next_page_token();
  // Since the token is a random number, we don't need to verify
  expected_search_result_proto.set_next_page_token(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
  // Now document1 is still to be fetched.

  // Invalidates token
  icing.InvalidateNextPageToken(next_page_token);

  // Tries to fetch the second page, no result since it's invalidated
  expected_search_result_proto.clear_results();
  expected_search_result_proto.clear_next_page_token();
  search_result_proto = icing.GetNextPage(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchIncludesDocumentsBeforeTtl) {
  SchemaProto schema;
  auto type = schema.add_types();
  type->set_schema_type("Message");

  auto body = type->add_properties();
  body->set_property_name("body");
  body->set_data_type(PropertyConfigProto::DataType::STRING);
  body->set_cardinality(PropertyConfigProto::Cardinality::REQUIRED);
  body->mutable_string_indexing_config()->set_term_match_type(
      TermMatchType::PREFIX);
  body->mutable_string_indexing_config()->set_tokenizer_type(
      StringIndexingConfig::TokenizerType::PLAIN);

  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "uri")
                               .SetSchema("Message")
                               .AddStringProperty("body", "message body")
                               .SetCreationTimestampMs(100)
                               .SetTtlMs(500)
                               .Build();

  SearchSpecProto search_spec;
  search_spec.set_query("message");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_search_type(GetParam());

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document;

  // Time just has to be less than the document's creation timestamp (100) + the
  // document's ttl (500)
  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetSystemTimeMilliseconds(400);

  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());

  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  EXPECT_THAT(icing.Put(document).status(), ProtoIsOk());

  // Check that the document is returned as part of search results
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchDoesntIncludeDocumentsPastTtl) {
  SchemaProto schema;
  auto type = schema.add_types();
  type->set_schema_type("Message");

  auto body = type->add_properties();
  body->set_property_name("body");
  body->set_data_type(PropertyConfigProto::DataType::STRING);
  body->set_cardinality(PropertyConfigProto::Cardinality::REQUIRED);
  body->mutable_string_indexing_config()->set_term_match_type(
      TermMatchType::PREFIX);
  body->mutable_string_indexing_config()->set_tokenizer_type(
      StringIndexingConfig::TokenizerType::PLAIN);

  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "uri")
                               .SetSchema("Message")
                               .AddStringProperty("body", "message body")
                               .SetCreationTimestampMs(100)
                               .SetTtlMs(500)
                               .Build();

  SearchSpecProto search_spec;
  search_spec.set_query("message");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_search_type(GetParam());

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);

  // Time just has to be greater than the document's creation timestamp (100) +
  // the document's ttl (500)
  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetSystemTimeMilliseconds(700);

  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());

  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  EXPECT_THAT(icing.Put(document).status(), ProtoIsOk());

  // Check that the document is not returned as part of search results
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchWorksAfterSchemaTypesCompatiblyModified) {
  SchemaProto schema;
  auto type_config = schema.add_types();
  type_config->set_schema_type("message");

  auto property = type_config->add_properties();
  property->set_property_name("body");
  property->set_data_type(PropertyConfigProto::DataType::STRING);
  property->set_cardinality(PropertyConfigProto::Cardinality::OPTIONAL);

  DocumentProto message_document =
      DocumentBuilder()
          .SetKey("namespace", "message_uri")
          .SetSchema("message")
          .AddStringProperty("body", "foo")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(message_document).status(), ProtoIsOk());

  // Make sure we can search for message document
  SearchSpecProto search_spec;
  search_spec.set_query("foo");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_search_type(GetParam());

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);

  // The message isn't indexed, so we get nothing
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));

  // With just the schema type filter, we can search for the message
  search_spec.Clear();
  search_spec.add_schema_type_filters("message");

  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      message_document;

  search_result_proto = icing.Search(search_spec, GetDefaultScoringSpec(),
                                     ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));

  // Since SchemaTypeIds are assigned based on order in the SchemaProto, this
  // will force a change in the DocumentStore's cached SchemaTypeIds
  schema.clear_types();
  type_config = schema.add_types();
  type_config->set_schema_type("email");

  // Adding a new indexed property will require reindexing
  type_config = schema.add_types();
  type_config->set_schema_type("message");

  property = type_config->add_properties();
  property->set_property_name("body");
  property->set_data_type(PropertyConfigProto::DataType::STRING);
  property->set_cardinality(PropertyConfigProto::Cardinality::OPTIONAL);
  property->mutable_string_indexing_config()->set_term_match_type(
      TermMatchType::PREFIX);
  property->mutable_string_indexing_config()->set_tokenizer_type(
      StringIndexingConfig::TokenizerType::PLAIN);

  EXPECT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  search_spec.Clear();
  search_spec.set_query("foo");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.add_schema_type_filters("message");

  // We can still search for the message document
  search_result_proto = icing.Search(search_spec, GetDefaultScoringSpec(),
                                     ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchResultShouldBeRankedByDocumentScore) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 3 documents and ensures the relationship in terms of document
  // score is: document1 < document2 < document3
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetScore(2)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri/3")
          .SetSchema("Message")
          .AddStringProperty("body", "message3")
          .SetScore(3)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  // Intentionally inserts the documents in the order that is different than
  // their score order
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());

  // "m" will match all 3 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  // Result should be in descending score order
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchWorksForNestedSubtypeDocument) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Person").AddProperty(
              PropertyConfigBuilder()
                  .SetName("name")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
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
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("Company").AddProperty(
              PropertyConfigBuilder()
                  .SetName("employee")
                  .SetDataTypeDocument("Person",
                                       /*index_nested_properties=*/true)
                  .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  // Create a company with a person and an artist.
  DocumentProto document_company =
      DocumentBuilder()
          .SetKey("namespace", "uri")
          .SetCreationTimestampMs(1000)
          .SetSchema("Company")
          .AddDocumentProperty("employee",
                               DocumentBuilder()
                                   .SetKey("namespace", "uri1")
                                   .SetCreationTimestampMs(1000)
                                   .SetSchema("Person")
                                   .AddStringProperty("name", "name_person")
                                   .Build(),
                               DocumentBuilder()
                                   .SetKey("namespace", "uri2")
                                   .SetCreationTimestampMs(1000)
                                   .SetSchema("Artist")
                                   .AddStringProperty("name", "name_artist")
                                   .AddStringProperty("emailAddress", "email")
                                   .Build())
          .Build();
  ASSERT_THAT(icing.Put(document_company).status(), ProtoIsOk());

  SearchResultProto company_search_result_proto;
  company_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *company_search_result_proto.mutable_results()->Add()->mutable_document() =
      document_company;

  SearchResultProto empty_search_result_proto;
  empty_search_result_proto.mutable_status()->set_code(StatusProto::OK);

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_search_type(GetParam());

  // "name_person" should match the company.
  search_spec.set_query("name_person");
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       company_search_result_proto));

  // "name_artist" should match the company.
  search_spec.set_query("name_artist");
  search_result_proto = icing.Search(search_spec, GetDefaultScoringSpec(),
                                     ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       company_search_result_proto));

  // "email" should not match the company even though the artist has a matched
  // property. This is because the "employee" property is defined as Person
  // type, and indexing on document properties should be based on defined types,
  // instead of subtypes.
  search_spec.set_query("email");
  search_result_proto = icing.Search(search_spec, GetDefaultScoringSpec(),
                                     ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       empty_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchShouldAllowNoScoring) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 3 documents and ensures the relationship of them is:
  // document1 < document2 < document3
  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace", "uri/1")
                                .SetSchema("Message")
                                .AddStringProperty("body", "message1")
                                .SetScore(1)
                                .SetCreationTimestampMs(1571111111111)
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace", "uri/2")
                                .SetSchema("Message")
                                .AddStringProperty("body", "message2")
                                .SetScore(2)
                                .SetCreationTimestampMs(1572222222222)
                                .Build();
  DocumentProto document3 = DocumentBuilder()
                                .SetKey("namespace", "uri/3")
                                .SetSchema("Message")
                                .AddStringProperty("body", "message3")
                                .SetScore(3)
                                .SetCreationTimestampMs(1573333333333)
                                .Build();

  // Intentionally inserts the documents in the order that is different than
  // their score order
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // "m" will match all 3 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;

  // Results should not be ranked by score but returned in reverse insertion
  // order.
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::NONE);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultShouldBeRankedByCreationTimestamp) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 3 documents and ensures the relationship in terms of creation
  // timestamp score is: document1 < document2 < document3
  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace", "uri/1")
                                .SetSchema("Message")
                                .AddStringProperty("body", "message1")
                                .SetCreationTimestampMs(1571111111111)
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace", "uri/2")
                                .SetSchema("Message")
                                .AddStringProperty("body", "message2")
                                .SetCreationTimestampMs(1572222222222)
                                .Build();
  DocumentProto document3 = DocumentBuilder()
                                .SetKey("namespace", "uri/3")
                                .SetSchema("Message")
                                .AddStringProperty("body", "message3")
                                .SetCreationTimestampMs(1573333333333)
                                .Build();

  // Intentionally inserts the documents in the order that is different than
  // their score order
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // "m" will match all 3 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  // Result should be in descending timestamp order
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchResultShouldBeRankedByUsageCount) {
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
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri/3")
          .SetSchema("Message")
          .AddStringProperty("body", "message3")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  // Intentionally inserts the documents in a different order to eliminate the
  // possibility that the following results are sorted in the default reverse
  // insertion order.
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // Report usage for doc3 twice and doc2 once. The order will be doc3 > doc2 >
  // doc1 when ranked by USAGE_TYPE1_COUNT.
  UsageReport usage_report_doc3 = CreateUsageReport(
      /*name_space=*/"namespace", /*uri=*/"uri/3", /*timestamp_ms=*/0,
      UsageReport::USAGE_TYPE1);
  UsageReport usage_report_doc2 = CreateUsageReport(
      /*name_space=*/"namespace", /*uri=*/"uri/2", /*timestamp_ms=*/0,
      UsageReport::USAGE_TYPE1);
  ASSERT_THAT(icing.ReportUsage(usage_report_doc3).status(), ProtoIsOk());
  ASSERT_THAT(icing.ReportUsage(usage_report_doc3).status(), ProtoIsOk());
  ASSERT_THAT(icing.ReportUsage(usage_report_doc2).status(), ProtoIsOk());

  // "m" will match all 3 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  // Result should be in descending USAGE_TYPE1_COUNT order
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::USAGE_TYPE1_COUNT);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultShouldHaveDefaultOrderWithoutUsageCounts) {
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
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri/3")
          .SetSchema("Message")
          .AddStringProperty("body", "message3")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  // "m" will match all 3 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  // None of the documents have usage reports. Result should be in the default
  // reverse insertion order.
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::USAGE_TYPE1_COUNT);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultShouldBeRankedByUsageTimestamp) {
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
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri/3")
          .SetSchema("Message")
          .AddStringProperty("body", "message3")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  // Intentionally inserts the documents in a different order to eliminate the
  // possibility that the following results are sorted in the default reverse
  // insertion order.
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // Report usage for doc2 and doc3. The order will be doc3 > doc2 > doc1 when
  // ranked by USAGE_TYPE1_LAST_USED_TIMESTAMP.
  UsageReport usage_report_doc2 = CreateUsageReport(
      /*name_space=*/"namespace", /*uri=*/"uri/2", /*timestamp_ms=*/1000,
      UsageReport::USAGE_TYPE1);
  UsageReport usage_report_doc3 = CreateUsageReport(
      /*name_space=*/"namespace", /*uri=*/"uri/3", /*timestamp_ms=*/5000,
      UsageReport::USAGE_TYPE1);
  ASSERT_THAT(icing.ReportUsage(usage_report_doc2).status(), ProtoIsOk());
  ASSERT_THAT(icing.ReportUsage(usage_report_doc3).status(), ProtoIsOk());

  // "m" will match all 3 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  // Result should be in descending USAGE_TYPE1_LAST_USED_TIMESTAMP order
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::USAGE_TYPE1_LAST_USED_TIMESTAMP);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, Bm25fRelevanceScoringOneNamespace) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateEmailSchema()).status(), ProtoIsOk());

  // Create and index documents in namespace "namespace1".
  DocumentProto document = CreateEmailDocument(
      "namespace1", "namespace1/uri0", /*score=*/10, "sushi belmont",
      "fresh fish. inexpensive. good sushi.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri1", /*score=*/13, "peacock koriander",
      "indian food. buffet. spicy food. kadai chicken.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri2", /*score=*/4,
                                 "panda express",
                                 "chinese food. cheap. inexpensive. kung pao.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri3", /*score=*/23,
                                 "speederia pizza",
                                 "thin-crust pizza. good and fast.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri4", /*score=*/8,
                                 "whole foods",
                                 "salads. pizza. organic food. expensive.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri5", /*score=*/18, "peets coffee",
      "espresso. decaf. brewed coffee. whole beans. excellent coffee.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri6", /*score=*/4, "costco",
      "bulk. cheap whole beans. frozen fish. food samples.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri7", /*score=*/4,
                                 "starbucks coffee",
                                 "habit. birthday rewards. good coffee");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("coffee OR food");
  search_spec.set_search_type(GetParam());
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());

  // Result should be in descending score order
  EXPECT_THAT(search_result_proto.status(), ProtoIsOk());
  // Both doc5 and doc7 have "coffee" in name and text sections.
  // However, doc5 has more matches in the text section.
  // Documents with "food" are ranked lower as the term "food" is commonly
  // present in this corpus, and thus, has a lower IDF.
  EXPECT_THAT(GetUrisFromSearchResults(search_result_proto),
              ElementsAre("namespace1/uri5",    // 'coffee' 3 times
                          "namespace1/uri7",    // 'coffee' 2 times
                          "namespace1/uri1",    // 'food' 2 times
                          "namespace1/uri4",    // 'food' 2 times
                          "namespace1/uri2",    // 'food' 1 time
                          "namespace1/uri6"));  // 'food' 1 time
}

TEST_P(IcingSearchEngineSearchTest, Bm25fRelevanceScoringOneNamespaceAdvanced) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateEmailSchema()).status(), ProtoIsOk());

  // Create and index documents in namespace "namespace1".
  DocumentProto document = CreateEmailDocument(
      "namespace1", "namespace1/uri0", /*score=*/10, "sushi belmont",
      "fresh fish. inexpensive. good sushi.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri1", /*score=*/13, "peacock koriander",
      "indian food. buffet. spicy food. kadai chicken.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri2", /*score=*/4,
                                 "panda express",
                                 "chinese food. cheap. inexpensive. kung pao.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri3", /*score=*/23,
                                 "speederia pizza",
                                 "thin-crust pizza. good and fast.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri4", /*score=*/8,
                                 "whole foods",
                                 "salads. pizza. organic food. expensive.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri5", /*score=*/18, "peets coffee",
      "espresso. decaf. brewed coffee. whole beans. excellent coffee.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri6", /*score=*/4, "costco",
      "bulk. cheap whole beans. frozen fish. food samples.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri7", /*score=*/4,
                                 "starbucks coffee",
                                 "habit. birthday rewards. good coffee");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("coffee OR food");
  search_spec.set_search_type(GetParam());
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_advanced_scoring_expression("this.relevanceScore() * 2 + 1");
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::ADVANCED_SCORING_EXPRESSION);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());

  // Result should be in descending score order
  EXPECT_THAT(search_result_proto.status(), ProtoIsOk());
  // Both doc5 and doc7 have "coffee" in name and text sections.
  // However, doc5 has more matches in the text section.
  // Documents with "food" are ranked lower as the term "food" is commonly
  // present in this corpus, and thus, has a lower IDF.
  EXPECT_THAT(GetUrisFromSearchResults(search_result_proto),
              ElementsAre("namespace1/uri5",    // 'coffee' 3 times
                          "namespace1/uri7",    // 'coffee' 2 times
                          "namespace1/uri1",    // 'food' 2 times
                          "namespace1/uri4",    // 'food' 2 times
                          "namespace1/uri2",    // 'food' 1 time
                          "namespace1/uri6"));  // 'food' 1 time
}

TEST_P(IcingSearchEngineSearchTest,
       Bm25fRelevanceScoringOneNamespaceNotOperator) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateEmailSchema()).status(), ProtoIsOk());

  // Create and index documents in namespace "namespace1".
  DocumentProto document = CreateEmailDocument(
      "namespace1", "namespace1/uri0", /*score=*/10, "sushi belmont",
      "fresh fish. inexpensive. good sushi.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri1", /*score=*/13, "peacock koriander",
      "indian food. buffet. spicy food. kadai chicken.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri2", /*score=*/4,
                                 "panda express",
                                 "chinese food. cheap. inexpensive. kung pao.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri3", /*score=*/23, "speederia pizza",
      "thin-crust pizza. good and fast. nice coffee");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri4", /*score=*/8,
                                 "whole foods",
                                 "salads. pizza. organic food. expensive.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri5", /*score=*/18, "peets coffee",
      "espresso. decaf. brewed coffee. whole beans. excellent coffee.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri6", /*score=*/4, "costco",
      "bulk. cheap whole beans. frozen fish. food samples.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri7", /*score=*/4,
                                 "starbucks coffee",
                                 "habit. birthday rewards. good coffee");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("coffee -starbucks");
  search_spec.set_search_type(GetParam());
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());

  // Result should be in descending score order
  EXPECT_THAT(search_result_proto.status(), ProtoIsOk());
  EXPECT_THAT(
      GetUrisFromSearchResults(search_result_proto),
      ElementsAre("namespace1/uri5",    // 'coffee' 3 times, 'starbucks' 0 times
                  "namespace1/uri3"));  // 'coffee' 1 times, 'starbucks' 0 times
}

TEST_P(IcingSearchEngineSearchTest,
       Bm25fRelevanceScoringOneNamespaceSectionRestrict) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateEmailSchema()).status(), ProtoIsOk());

  // Create and index documents in namespace "namespace1".
  DocumentProto document = CreateEmailDocument(
      "namespace1", "namespace1/uri0", /*score=*/10, "sushi belmont",
      "fresh fish. inexpensive. good sushi.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri1", /*score=*/13, "peacock koriander",
      "indian food. buffet. spicy food. kadai chicken.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri2", /*score=*/4,
                                 "panda express",
                                 "chinese food. cheap. inexpensive. kung pao.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri3", /*score=*/23,
                                 "speederia pizza",
                                 "thin-crust pizza. good and fast.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri4", /*score=*/8,
                                 "whole foods",
                                 "salads. pizza. organic food. expensive.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document =
      CreateEmailDocument("namespace1", "namespace1/uri5", /*score=*/18,
                          "peets coffee, best coffee",
                          "espresso. decaf. whole beans. excellent coffee.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri6", /*score=*/4, "costco",
      "bulk. cheap whole beans. frozen fish. food samples.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri7", /*score=*/4, "starbucks",
      "habit. birthday rewards. good coffee. brewed coffee");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("subject:coffee OR body:food");
  search_spec.set_search_type(GetParam());
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());

  // Result should be in descending score order
  EXPECT_THAT(search_result_proto.status(), ProtoIsOk());
  // The term frequencies of "coffee" and "food" are calculated respectively
  // from the subject section and the body section.
  // Documents with "food" are ranked lower as the term "food" is commonly
  // present in this corpus, and thus, has a lower IDF.
  EXPECT_THAT(
      GetUrisFromSearchResults(search_result_proto),
      ElementsAre("namespace1/uri5",    // 'coffee' 2 times in section subject
                  "namespace1/uri1",    // 'food' 2 times in section body
                  "namespace1/uri4",    // 'food' 2 times in section body
                  "namespace1/uri2",    // 'food' 1 time in section body
                  "namespace1/uri6"));  // 'food' 1 time in section body
}

TEST_P(IcingSearchEngineSearchTest, Bm25fRelevanceScoringTwoNamespaces) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateEmailSchema()).status(), ProtoIsOk());

  // Create and index documents in namespace "namespace1".
  DocumentProto document = CreateEmailDocument(
      "namespace1", "namespace1/uri0", /*score=*/10, "sushi belmont",
      "fresh fish. inexpensive. good sushi.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri1", /*score=*/13, "peacock koriander",
      "indian food. buffet. spicy food. kadai chicken.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri2", /*score=*/4,
                                 "panda express",
                                 "chinese food. cheap. inexpensive. kung pao.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri3", /*score=*/23,
                                 "speederia pizza",
                                 "thin-crust pizza. good and fast.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri4", /*score=*/8,
                                 "whole foods",
                                 "salads. pizza. organic food. expensive.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri5", /*score=*/18, "peets coffee",
      "espresso. decaf. brewed coffee. whole beans. excellent coffee.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri6", /*score=*/4, "costco",
      "bulk. cheap whole beans. frozen fish. food samples.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri7", /*score=*/4,
                                 "starbucks coffee",
                                 "habit. birthday rewards. good coffee");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  // Create and index documents in namespace "namespace2".
  document = CreateEmailDocument("namespace2", "namespace2/uri0", /*score=*/10,
                                 "sushi belmont",
                                 "fresh fish. inexpensive. good sushi.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace2", "namespace2/uri1", /*score=*/13, "peacock koriander",
      "indian food. buffet. spicy food. kadai chicken.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace2", "namespace2/uri2", /*score=*/4,
                                 "panda express",
                                 "chinese food. cheap. inexpensive. kung pao.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace2", "namespace2/uri3", /*score=*/23,
                                 "speederia pizza",
                                 "thin-crust pizza. good and fast.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace2", "namespace2/uri4", /*score=*/8,
                                 "whole foods",
                                 "salads. pizza. organic food. expensive.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace2", "namespace2/uri5", /*score=*/18, "peets coffee",
      "espresso. decaf. brewed coffee. whole beans. excellent coffee.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace2", "namespace2/uri6", /*score=*/4, "costco",
      "bulk. cheap whole beans. frozen fish. food samples.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace2", "namespace2/uri7", /*score=*/4,
                                 "starbucks coffee", "good coffee");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("coffee OR food");
  search_spec.set_search_type(GetParam());
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE);
  ResultSpecProto result_spec_proto;
  result_spec_proto.set_num_per_page(16);
  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec_proto);

  // Result should be in descending score order
  EXPECT_THAT(search_result_proto.status(), ProtoIsOk());
  // The two corpora have the same documents except for document 7, which in
  // "namespace2" is much shorter than the average dcoument length, so it is
  // boosted.
  EXPECT_THAT(GetUrisFromSearchResults(search_result_proto),
              ElementsAre("namespace2/uri7",    // 'coffee' 2 times, short doc
                          "namespace1/uri5",    // 'coffee' 3 times
                          "namespace2/uri5",    // 'coffee' 3 times
                          "namespace1/uri7",    // 'coffee' 2 times
                          "namespace1/uri1",    // 'food' 2 times
                          "namespace2/uri1",    // 'food' 2 times
                          "namespace1/uri4",    // 'food' 2 times
                          "namespace2/uri4",    // 'food' 2 times
                          "namespace1/uri2",    // 'food' 1 time
                          "namespace2/uri2",    // 'food' 1 time
                          "namespace1/uri6",    // 'food' 1 time
                          "namespace2/uri6"));  // 'food' 1 time
}

TEST_P(IcingSearchEngineSearchTest, Bm25fRelevanceScoringWithNamespaceFilter) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateEmailSchema()).status(), ProtoIsOk());

  // Create and index documents in namespace "namespace1".
  DocumentProto document = CreateEmailDocument(
      "namespace1", "namespace1/uri0", /*score=*/10, "sushi belmont",
      "fresh fish. inexpensive. good sushi.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri1", /*score=*/13, "peacock koriander",
      "indian food. buffet. spicy food. kadai chicken.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri2", /*score=*/4,
                                 "panda express",
                                 "chinese food. cheap. inexpensive. kung pao.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri3", /*score=*/23,
                                 "speederia pizza",
                                 "thin-crust pizza. good and fast.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri4", /*score=*/8,
                                 "whole foods",
                                 "salads. pizza. organic food. expensive.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri5", /*score=*/18, "peets coffee",
      "espresso. decaf. brewed coffee. whole beans. excellent coffee.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace1", "namespace1/uri6", /*score=*/4, "costco",
      "bulk. cheap whole beans. frozen fish. food samples.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace1", "namespace1/uri7", /*score=*/4,
                                 "starbucks coffee",
                                 "habit. birthday rewards. good coffee");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  // Create and index documents in namespace "namespace2".
  document = CreateEmailDocument("namespace2", "namespace2/uri0", /*score=*/10,
                                 "sushi belmont",
                                 "fresh fish. inexpensive. good sushi.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace2", "namespace2/uri1", /*score=*/13, "peacock koriander",
      "indian food. buffet. spicy food. kadai chicken.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace2", "namespace2/uri2", /*score=*/4,
                                 "panda express",
                                 "chinese food. cheap. inexpensive. kung pao.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace2", "namespace2/uri3", /*score=*/23,
                                 "speederia pizza",
                                 "thin-crust pizza. good and fast.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace2", "namespace2/uri4", /*score=*/8,
                                 "whole foods",
                                 "salads. pizza. organic food. expensive.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace2", "namespace2/uri5", /*score=*/18, "peets coffee",
      "espresso. decaf. brewed coffee. whole beans. excellent coffee.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument(
      "namespace2", "namespace2/uri6", /*score=*/4, "costco",
      "bulk. cheap whole beans. frozen fish. food samples.");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  document = CreateEmailDocument("namespace2", "namespace2/uri7", /*score=*/4,
                                 "starbucks coffee", "good coffee");
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("coffee OR food");
  search_spec.set_search_type(GetParam());
  // Now query only corpus 2
  search_spec.add_namespace_filters("namespace2");
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());
  search_result_proto = icing.Search(search_spec, scoring_spec,
                                     ResultSpecProto::default_instance());

  // Result from namespace "namespace2" should be in descending score order
  EXPECT_THAT(search_result_proto.status(), ProtoIsOk());
  // Both doc5 and doc7 have "coffee" in name and text sections.
  // Even though doc5 has more matches in the text section, doc7's length is
  // much shorter than the average corpus's length, so it's being boosted.
  // Documents with "food" are ranked lower as the term "food" is commonly
  // present in this corpus, and thus, has a lower IDF.
  EXPECT_THAT(GetUrisFromSearchResults(search_result_proto),
              ElementsAre("namespace2/uri7",    // 'coffee' 2 times, short doc
                          "namespace2/uri5",    // 'coffee' 3 times
                          "namespace2/uri1",    // 'food' 2 times
                          "namespace2/uri4",    // 'food' 2 times
                          "namespace2/uri2",    // 'food' 1 time
                          "namespace2/uri6"));  // 'food' 1 time
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultShouldHaveDefaultOrderWithoutUsageTimestamp) {
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
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri/3")
          .SetSchema("Message")
          .AddStringProperty("body", "message3")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  // "m" will match all 3 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  // None of the documents have usage reports. Result should be in the default
  // reverse insertion order.
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::USAGE_TYPE1_LAST_USED_TIMESTAMP);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchResultShouldBeRankedAscendingly) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 3 documents and ensures the relationship in terms of document
  // score is: document1 < document2 < document3
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetScore(2)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri/3")
          .SetSchema("Message")
          .AddStringProperty("body", "message3")
          .SetScore(3)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  // Intentionally inserts the documents in the order that is different than
  // their score order
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());

  // "m" will match all 3 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  // Result should be in ascending score order
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);
  scoring_spec.set_order_by(ScoringSpecProto::Order::ASC);
  SearchResultProto search_result_proto = icing.Search(
      search_spec, scoring_spec, ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultGroupingDuplicateNamespaceShouldReturnError) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 2 documents and ensures the relationship in terms of document
  // score is: document1 < document2
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace2", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetScore(2)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // "m" will match all 2 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  // Specify "namespace1" twice. This should result in an error.
  ResultSpecProto result_spec;
  result_spec.set_result_group_type(ResultSpecProto::NAMESPACE);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(1);
  entry->set_namespace_("namespace1");
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace2");
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace1");
  result_grouping = result_spec.add_result_groupings();
  entry = result_grouping->add_entry_groupings();
  result_grouping->set_max_results(1);
  entry->set_namespace_("namespace1");

  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(search_result_proto.status(),
              ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultGroupingDuplicateSchemaShouldReturnError) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 2 documents and ensures the relationship in terms of document
  // score is: document1 < document2
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace2", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetScore(2)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // "m" will match all 2 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  // Specify "Message" twice. This should result in an error.
  ResultSpecProto result_spec;
  result_spec.set_result_group_type(ResultSpecProto::SCHEMA_TYPE);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(1);
  entry->set_schema("Message");
  entry = result_grouping->add_entry_groupings();
  entry->set_schema("nonexistentMessage");
  result_grouping = result_spec.add_result_groupings();
  result_grouping->set_max_results(1);
  entry = result_grouping->add_entry_groupings();
  entry->set_schema("Message");

  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(search_result_proto.status(),
              ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultGroupingDuplicateNamespaceAndSchemaSchemaShouldReturnError) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 2 documents and ensures the relationship in terms of document
  // score is: document1 < document2
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace2", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetScore(2)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // "m" will match all 2 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  // Specify "namespace1xMessage" twice. This should result in an error.
  ResultSpecProto result_spec;
  result_spec.set_result_group_type(ResultSpecProto::NAMESPACE_AND_SCHEMA_TYPE);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(1);
  entry->set_namespace_("namespace1");
  entry->set_schema("Message");
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace2");
  entry->set_schema("Message");
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace1");
  entry->set_schema("Message");
  result_grouping = result_spec.add_result_groupings();
  result_grouping->set_max_results(1);
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace1");
  entry->set_schema("Message");

  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(search_result_proto.status(),
              ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultGroupingNonPositiveMaxResultsShouldReturnError) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 2 documents and ensures the relationship in terms of document
  // score is: document1 < document2
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace2", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetScore(2)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // "m" will match all 2 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  // Specify zero results. This should result in an error.
  ResultSpecProto result_spec;
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(0);
  entry->set_namespace_("namespace1");
  entry->set_schema("Message");
  result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace2");
  entry->set_schema("Message");

  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(search_result_proto.status(),
              ProtoStatusIs(StatusProto::INVALID_ARGUMENT));

  // Specify negative results. This should result in an error.
  result_spec.mutable_result_groupings(0)->set_max_results(-1);
  EXPECT_THAT(search_result_proto.status(),
              ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultGroupingMultiNamespaceGrouping) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 3 documents and ensures the relationship in terms of document
  // score is: document1 < document2 < document3 < document4 < document5 <
  // document6
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetScore(2)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace2", "uri/3")
          .SetSchema("Message")
          .AddStringProperty("body", "message3")
          .SetScore(3)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document4 =
      DocumentBuilder()
          .SetKey("namespace2", "uri/4")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(4)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document5 =
      DocumentBuilder()
          .SetKey("namespace3", "uri/5")
          .SetSchema("Message")
          .AddStringProperty("body", "message3")
          .SetScore(5)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document6 =
      DocumentBuilder()
          .SetKey("namespace3", "uri/6")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(6)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document4).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document5).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document6).status(), ProtoIsOk());

  // "m" will match all 6 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  ResultSpecProto result_spec;
  result_spec.set_result_group_type(ResultSpecProto::NAMESPACE);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(1);
  entry->set_namespace_("namespace1");
  result_grouping = result_spec.add_result_groupings();
  result_grouping->set_max_results(2);
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace2");
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace3");

  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec);

  // The last result (document1) in namespace "namespace1" should not be
  // included. "namespace2" and "namespace3" are grouped together. So only the
  // two highest scored documents between the two (both of which are in
  // "namespace3") should be returned.
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document6;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document5;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;

  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SearchResultGroupingMultiSchemaGrouping) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .AddType(SchemaTypeConfigBuilder().SetType("Person").AddProperty(
              PropertyConfigBuilder()
                  .SetName("name")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("sender")
                                        .SetDataTypeDocument(
                                            "Person",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Email")
          .SetScore(1)
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "foo")
          .AddDocumentProperty("sender", DocumentBuilder()
                                             .SetKey("namespace", "uri1-sender")
                                             .SetSchema("Person")
                                             .AddStringProperty("name", "foo")
                                             .Build())
          .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace1", "uri2")
                                .SetSchema("Message")
                                .SetScore(2)
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("body", "fo")
                                .Build();
  DocumentProto document3 = DocumentBuilder()
                                .SetKey("namespace2", "uri3")
                                .SetSchema("Message")
                                .SetScore(3)
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("body", "fo")
                                .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  // "f" will match all 3 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("f");
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  ResultSpecProto result_spec;
  result_spec.set_result_group_type(ResultSpecProto::SCHEMA_TYPE);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(1);
  entry->set_schema("Message");
  result_grouping = result_spec.add_result_groupings();
  result_grouping->set_max_results(1);
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("Email");

  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec);

  // Each of the highest scored documents of schema type "Message" (document3)
  // and "Email" (document1) should be returned.
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document3;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document1;

  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultGroupingMultiNamespaceAndSchemaGrouping) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 3 documents and ensures the relationship in terms of document
  // score is: document1 < document2 < document3 < document4 < document5 <
  // document6
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetScore(2)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace2", "uri/3")
          .SetSchema("Message")
          .AddStringProperty("body", "message3")
          .SetScore(3)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document4 =
      DocumentBuilder()
          .SetKey("namespace2", "uri/4")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(4)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document5 =
      DocumentBuilder()
          .SetKey("namespace3", "uri/5")
          .SetSchema("Message")
          .AddStringProperty("body", "message3")
          .SetScore(5)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document6 =
      DocumentBuilder()
          .SetKey("namespace3", "uri/6")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(6)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document4).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document5).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document6).status(), ProtoIsOk());

  // "m" will match all 6 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  ResultSpecProto result_spec;
  result_spec.set_result_group_type(ResultSpecProto::NAMESPACE_AND_SCHEMA_TYPE);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(1);
  entry->set_namespace_("namespace1");
  entry->set_schema("Message");
  result_grouping = result_spec.add_result_groupings();
  result_grouping->set_max_results(1);
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace2");
  entry->set_schema("Message");
  result_grouping = result_spec.add_result_groupings();
  result_grouping->set_max_results(1);
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace3");
  entry->set_schema("Message");

  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec);

  // The three highest scored documents that fit the criteria of
  // "namespace1xMessage" (document2), "namespace2xMessage" (document4),
  // and "namespace3xMessage" (document6) should be returned.
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document6;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document4;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;

  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultGroupingNonexistentNamespaceShouldBeIgnored) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 2 documents and ensures the relationship in terms of document
  // score is: document1 < document2
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetScore(2)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // "m" will match all 2 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  ResultSpecProto result_spec;
  result_spec.set_result_group_type(ResultSpecProto::NAMESPACE);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(1);
  entry->set_namespace_("namespace1");
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("nonexistentNamespace");

  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec);

  // Only the top ranked document in "namespace" (document2), should be
  // returned. The presence of "nonexistentNamespace" in the same result
  // grouping should have no effect.
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;

  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultGroupingNonexistentSchemaShouldBeIgnored) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 2 documents and ensures the relationship in terms of document
  // score is: document1 < document2
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetScore(2)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // "m" will match all 2 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  ResultSpecProto result_spec;
  result_spec.set_result_group_type(ResultSpecProto::SCHEMA_TYPE);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(1);
  entry->set_schema("Message");
  entry = result_grouping->add_entry_groupings();
  entry->set_schema("nonexistentMessage");

  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec);

  // Only the top ranked document in "Message" (document2), should be
  // returned. The presence of "nonexistentMessage" in the same result
  // grouping should have no effect.
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;

  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchResultGroupingNonexistentNamespaceAndSchemaShouldBeIgnored) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates 2 documents and ensures the relationship in terms of document
  // score is: document1 < document2
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/1")
          .SetSchema("Message")
          .AddStringProperty("body", "message1")
          .SetScore(1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace1", "uri/2")
          .SetSchema("Message")
          .AddStringProperty("body", "message2")
          .SetScore(2)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace2", "uri/3")
          .SetSchema("Message")
          .AddStringProperty("body", "message3")
          .SetScore(3)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  DocumentProto document4 =
      DocumentBuilder()
          .SetKey("namespace2", "uri/4")
          .SetSchema("Message")
          .AddStringProperty("body", "message4")
          .SetScore(4)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document4).status(), ProtoIsOk());

  // "m" will match all 2 documents
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("m");
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  ResultSpecProto result_spec;
  result_spec.set_result_group_type(ResultSpecProto::SCHEMA_TYPE);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(1);
  entry->set_namespace_("namespace2");
  entry->set_schema("Message");
  entry = result_grouping->add_entry_groupings();
  entry->set_schema("namespace1");
  entry->set_schema("nonexistentMessage");

  SearchResultProto search_result_proto =
      icing.Search(search_spec, scoring_spec, result_spec);

  // Only the top ranked document in "namespace2xMessage" (document4), should be
  // returned. The presence of "namespace1xnonexistentMessage" in the same
  // result grouping should have no effect. If either the namespace or the
  // schema type is nonexistent, the entire entry will be ignored.
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document4;

  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_P(IcingSearchEngineSearchTest, SnippetNormalization) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "MDI zurich Team Meeting")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "mdi Zürich Team Meeting")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("mdi Zürich");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.mutable_snippet_spec()->set_max_window_utf32_length(64);
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(2);
  result_spec.mutable_snippet_spec()->set_num_to_snippet(2);

  SearchResultProto results =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  ASSERT_THAT(results.results(), SizeIs(2));
  const DocumentProto& result_document_1 = results.results(0).document();
  const SnippetProto& result_snippet_1 = results.results(0).snippet();
  EXPECT_THAT(result_document_1, EqualsProto(document_two));
  EXPECT_THAT(result_snippet_1.entries(), SizeIs(1));
  EXPECT_THAT(result_snippet_1.entries(0).property_name(), Eq("body"));
  std::string_view content = GetString(
      &result_document_1, result_snippet_1.entries(0).property_name());
  EXPECT_THAT(
      GetWindows(content, result_snippet_1.entries(0)),
      ElementsAre("mdi Zürich Team Meeting", "mdi Zürich Team Meeting"));
  EXPECT_THAT(GetMatches(content, result_snippet_1.entries(0)),
              ElementsAre("mdi", "Zürich"));

  const DocumentProto& result_document_2 = results.results(1).document();
  const SnippetProto& result_snippet_2 = results.results(1).snippet();
  EXPECT_THAT(result_document_2, EqualsProto(document_one));
  EXPECT_THAT(result_snippet_2.entries(), SizeIs(1));
  EXPECT_THAT(result_snippet_2.entries(0).property_name(), Eq("body"));
  content = GetString(&result_document_2,
                      result_snippet_2.entries(0).property_name());
  EXPECT_THAT(
      GetWindows(content, result_snippet_2.entries(0)),
      ElementsAre("MDI zurich Team Meeting", "MDI zurich Team Meeting"));
  EXPECT_THAT(GetMatches(content, result_snippet_2.entries(0)),
              ElementsAre("MDI", "zurich"));
}

TEST_P(IcingSearchEngineSearchTest, SnippetNormalizationPrefix) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "MDI zurich Team Meeting")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "mdi Zürich Team Meeting")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("md Zür");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.mutable_snippet_spec()->set_max_window_utf32_length(64);
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(2);
  result_spec.mutable_snippet_spec()->set_num_to_snippet(2);

  SearchResultProto results =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  ASSERT_THAT(results.results(), SizeIs(2));
  const DocumentProto& result_document_1 = results.results(0).document();
  const SnippetProto& result_snippet_1 = results.results(0).snippet();
  EXPECT_THAT(result_document_1, EqualsProto(document_two));
  EXPECT_THAT(result_snippet_1.entries(), SizeIs(1));
  EXPECT_THAT(result_snippet_1.entries(0).property_name(), Eq("body"));
  std::string_view content = GetString(
      &result_document_1, result_snippet_1.entries(0).property_name());
  EXPECT_THAT(
      GetWindows(content, result_snippet_1.entries(0)),
      ElementsAre("mdi Zürich Team Meeting", "mdi Zürich Team Meeting"));
  EXPECT_THAT(GetMatches(content, result_snippet_1.entries(0)),
              ElementsAre("mdi", "Zürich"));

  const DocumentProto& result_document_2 = results.results(1).document();
  const SnippetProto& result_snippet_2 = results.results(1).snippet();
  EXPECT_THAT(result_document_2, EqualsProto(document_one));
  EXPECT_THAT(result_snippet_2.entries(), SizeIs(1));
  EXPECT_THAT(result_snippet_2.entries(0).property_name(), Eq("body"));
  content = GetString(&result_document_2,
                      result_snippet_2.entries(0).property_name());
  EXPECT_THAT(
      GetWindows(content, result_snippet_2.entries(0)),
      ElementsAre("MDI zurich Team Meeting", "MDI zurich Team Meeting"));
  EXPECT_THAT(GetMatches(content, result_snippet_2.entries(0)),
              ElementsAre("MDI", "zurich"));
}

TEST_P(IcingSearchEngineSearchTest, SnippetSectionRestrict) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateEmailSchema()).status(), ProtoIsOk());

  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Email")
          .AddStringProperty("subject", "MDI zurich Team Meeting")
          .AddStringProperty("body", "MDI zurich Team Meeting")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Email")
          .AddStringProperty("subject", "MDI zurich trip")
          .AddStringProperty("body", "Let's travel to zurich")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("body:Zür");
  search_spec->set_search_type(GetParam());

  auto result_spec = std::make_unique<ResultSpecProto>();
  result_spec->set_num_per_page(1);
  result_spec->mutable_snippet_spec()->set_max_window_utf32_length(64);
  result_spec->mutable_snippet_spec()->set_num_matches_per_property(10);
  result_spec->mutable_snippet_spec()->set_num_to_snippet(10);

  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();

  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  ASSERT_THAT(results.results(), SizeIs(1));

  const DocumentProto& result_document_two = results.results(0).document();
  const SnippetProto& result_snippet_two = results.results(0).snippet();
  EXPECT_THAT(result_document_two, EqualsProto(document_two));
  EXPECT_THAT(result_snippet_two.entries(), SizeIs(1));
  EXPECT_THAT(result_snippet_two.entries(0).property_name(), Eq("body"));
  std::string_view content = GetString(
      &result_document_two, result_snippet_two.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_snippet_two.entries(0)),
              ElementsAre("Let's travel to zurich"));
  EXPECT_THAT(GetMatches(content, result_snippet_two.entries(0)),
              ElementsAre("zurich"));

  search_spec.reset();
  scoring_spec.reset();
  result_spec.reset();

  results = icing.GetNextPage(results.next_page_token());
  EXPECT_THAT(results.status(), ProtoIsOk());
  ASSERT_THAT(results.results(), SizeIs(1));

  const DocumentProto& result_document_one = results.results(0).document();
  const SnippetProto& result_snippet_one = results.results(0).snippet();
  EXPECT_THAT(result_document_one, EqualsProto(document_one));
  EXPECT_THAT(result_snippet_one.entries(), SizeIs(1));
  EXPECT_THAT(result_snippet_one.entries(0).property_name(), Eq("body"));
  content = GetString(&result_document_one,
                      result_snippet_one.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_snippet_one.entries(0)),
              ElementsAre("MDI zurich Team Meeting"));
  EXPECT_THAT(GetMatches(content, result_snippet_one.entries(0)),
              ElementsAre("zurich"));
}

TEST_P(IcingSearchEngineSearchTest, Hyphens) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  SchemaProto schema;
  SchemaTypeConfigProto* type = schema.add_types();
  type->set_schema_type("MyType");
  PropertyConfigProto* prop = type->add_properties();
  prop->set_property_name("foo");
  prop->set_data_type(PropertyConfigProto::DataType::STRING);
  prop->set_cardinality(PropertyConfigProto::Cardinality::REQUIRED);
  prop->mutable_string_indexing_config()->set_term_match_type(
      TermMatchType::EXACT_ONLY);
  prop->mutable_string_indexing_config()->set_tokenizer_type(
      StringIndexingConfig::TokenizerType::PLAIN);
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("MyType")
          .AddStringProperty("foo", "foo bar-baz bat")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("MyType")
          .AddStringProperty("foo", "bar for baz bat-man")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("foo:bar-baz");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  SearchResultProto results =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);

  EXPECT_THAT(results.status(), ProtoIsOk());
  ASSERT_THAT(results.results(), SizeIs(2));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document_two));
  EXPECT_THAT(results.results(1).document(), EqualsProto(document_one));
}

TEST_P(IcingSearchEngineSearchTest, SearchWithProjectionEmptyFieldPath) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one =
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
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("subject", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // 2. Issue a query that will match those documents and use an empty field
  // mask to request NO properties.
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("hello");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  // Retrieve only one result at a time to make sure that projection works when
  // retrieving all pages.
  result_spec.set_num_per_page(1);
  TypePropertyMask* email_field_mask = result_spec.add_type_property_masks();
  email_field_mask->set_schema_type("Email");
  email_field_mask->add_paths("");

  SearchResultProto results =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));

  // 3. Verify that the returned results contain no properties.
  DocumentProto projected_document_two = DocumentBuilder()
                                             .SetKey("namespace", "uri2")
                                             .SetCreationTimestampMs(1000)
                                             .SetSchema("Email")
                                             .Build();
  EXPECT_THAT(results.results(0).document(),
              EqualsProto(projected_document_two));

  results = icing.GetNextPage(results.next_page_token());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));
  DocumentProto projected_document_one = DocumentBuilder()
                                             .SetKey("namespace", "uri1")
                                             .SetCreationTimestampMs(1000)
                                             .SetSchema("Email")
                                             .Build();
  EXPECT_THAT(results.results(0).document(),
              EqualsProto(projected_document_one));
}

TEST_P(IcingSearchEngineSearchTest, SearchWithProjectionMultipleFieldPaths) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one =
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
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("subject", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // 2. Issue a query that will match those documents and request only
  // 'sender.name' and 'subject' properties.
  // Create all of search_spec, result_spec and scoring_spec as objects with
  // scope that will end before the call to GetNextPage to ensure that the
  // implementation isn't relying on references to any of them.
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("hello");
  search_spec->set_search_type(GetParam());

  auto result_spec = std::make_unique<ResultSpecProto>();
  // Retrieve only one result at a time to make sure that projection works when
  // retrieving all pages.
  result_spec->set_num_per_page(1);
  TypePropertyMask* email_field_mask = result_spec->add_type_property_masks();
  email_field_mask->set_schema_type("Email");
  email_field_mask->add_paths("sender.name");
  email_field_mask->add_paths("subject");

  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));

  // 3. Verify that the first returned result only contains the 'sender.name'
  // property.
  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty("sender",
                               DocumentBuilder()
                                   .SetKey("namespace", "uri2")
                                   .SetSchema("Person")
                                   .AddStringProperty("name", "Tom Hanks")
                                   .Build())
          .AddStringProperty("subject", "Goodnight Moon!")
          .Build();
  EXPECT_THAT(results.results(0).document(),
              EqualsProto(projected_document_two));

  // 4. Now, delete all of the specs used in the search. GetNextPage should have
  // no problem because it shouldn't be keeping any references to them.
  search_spec.reset();
  result_spec.reset();
  scoring_spec.reset();

  // 5. Verify that the second returned result only contains the 'sender.name'
  // property.
  results = icing.GetNextPage(results.next_page_token());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));
  DocumentProto projected_document_one =
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
  EXPECT_THAT(results.results(0).document(),
              EqualsProto(projected_document_one));
}

TEST_P(IcingSearchEngineSearchTest, SearchWithPropertyFilters) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one =
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
                  .AddStringProperty("emailAddress", "hellogirl@aol.com")
                  .Build())
          .AddStringProperty("subject", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("subject", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // 2. Issue a query with property filters of sender.name and subject for the
  // Email schema type.
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("hello");
  search_spec->set_search_type(GetParam());
  TypePropertyMask* email_property_filters =
      search_spec->add_type_property_filters();
  email_property_filters->set_schema_type("Email");
  email_property_filters->add_paths("sender.name");
  email_property_filters->add_paths("subject");

  auto result_spec = std::make_unique<ResultSpecProto>();

  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));

  // 3. Verify that only the first document is returned. Although 'hello' is
  // present in document_two, it shouldn't be in the result since 'hello' is not
  // in the specified property filter.
  EXPECT_THAT(results.results(0).document(), EqualsProto(document_one));
}

TEST_P(IcingSearchEngineSearchTest, EmptySearchWithPropertyFilter) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one =
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
                  .AddStringProperty("emailAddress", "hellogirl@aol.com")
                  .Build())
          .AddStringProperty("subject", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("subject", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // 2. Issue a query with a property filter
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("");
  search_spec->set_search_type(GetParam());
  TypePropertyMask* email_property_filters =
      search_spec->add_type_property_filters();
  email_property_filters->set_schema_type("Email");
  email_property_filters->add_paths("subject");

  auto result_spec = std::make_unique<ResultSpecProto>();

  // 3. Verify that both documents are returned.
  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(2));
}

TEST_P(IcingSearchEngineSearchTest, EmptySearchWithEmptyPropertyFilter) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one =
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
                  .AddStringProperty("emailAddress", "hellogirl@aol.com")
                  .Build())
          .AddStringProperty("subject", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("subject", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // 2. Issue a query with a property filter
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("");
  search_spec->set_search_type(GetParam());
  TypePropertyMask* email_property_filters =
      search_spec->add_type_property_filters();
  // Add empty list for Email's property filters
  email_property_filters->set_schema_type("Email");

  auto result_spec = std::make_unique<ResultSpecProto>();

  // 3. Verify that both documents are returned.
  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(2));
}

TEST_P(IcingSearchEngineSearchTest, SearchWithPropertyFiltersOnMultipleSchema) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  // Add Person and Organization schema with a property 'name' in both.
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
                       .SetType("Organization")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("name")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("address")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  // 1. Add person document
  DocumentProto person_document =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Meg Ryan")
          .AddStringProperty("emailAddress", "hellogirl@aol.com")
          .Build();
  ASSERT_THAT(icing.Put(person_document).status(), ProtoIsOk());

  // 1. Add organization document
  DocumentProto organization_document =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Organization")
          .AddStringProperty("name", "Meg Corp")
          .AddStringProperty("address", "Universal street")
          .Build();
  ASSERT_THAT(icing.Put(organization_document).status(), ProtoIsOk());

  // 2. Issue a query with property filters. Person schema has name in it's
  // property filter but Organization schema doesn't.
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("Meg");
  search_spec->set_search_type(GetParam());
  TypePropertyMask* person_property_filters =
      search_spec->add_type_property_filters();
  person_property_filters->set_schema_type("Person");
  person_property_filters->add_paths("name");
  TypePropertyMask* organization_property_filters =
      search_spec->add_type_property_filters();
  organization_property_filters->set_schema_type("Organization");
  organization_property_filters->add_paths("address");

  auto result_spec = std::make_unique<ResultSpecProto>();

  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));

  // 3. Verify that only the person document is returned. Although 'Meg' is
  // present in organization document, it shouldn't be in the result since
  // the name field is not specified in the Organization property filter.
  EXPECT_THAT(results.results(0).document(), EqualsProto(person_document));
}

TEST_P(IcingSearchEngineSearchTest, SearchWithWildcardPropertyFilters) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one =
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
                  .AddStringProperty("emailAddress", "hellogirl@aol.com")
                  .Build())
          .AddStringProperty("subject", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("subject", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // 2. Issue a query with property filters of sender.name and subject for the
  // wildcard(*) schema type.
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("hello");
  search_spec->set_search_type(GetParam());
  TypePropertyMask* wildcard_property_filters =
      search_spec->add_type_property_filters();
  wildcard_property_filters->set_schema_type("*");
  wildcard_property_filters->add_paths("sender.name");
  wildcard_property_filters->add_paths("subject");

  auto result_spec = std::make_unique<ResultSpecProto>();

  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));

  // 3. Verify that only the first document is returned since the second
  // document doesn't contain the word 'hello' in either of fields specified in
  // the property filter. This confirms that the property filters for the
  // wildcard entry have been applied to the Email schema as well.
  EXPECT_THAT(results.results(0).document(), EqualsProto(document_one));
}

TEST_P(IcingSearchEngineSearchTest, SearchWithMixedPropertyFilters) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one =
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
                  .AddStringProperty("emailAddress", "hellogirl@aol.com")
                  .Build())
          .AddStringProperty("subject", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("subject", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // 2. Issue a query with property filters of sender.name and subject for the
  // wildcard(*) schema type plus property filters of sender.name and body for
  // the Email schema type.
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("hello");
  search_spec->set_search_type(GetParam());
  TypePropertyMask* wildcard_property_filters =
      search_spec->add_type_property_filters();
  wildcard_property_filters->set_schema_type("*");
  wildcard_property_filters->add_paths("sender.name");
  wildcard_property_filters->add_paths("subject");
  TypePropertyMask* email_property_filters =
      search_spec->add_type_property_filters();
  email_property_filters->set_schema_type("Email");
  email_property_filters->add_paths("sender.name");
  email_property_filters->add_paths("body");

  auto result_spec = std::make_unique<ResultSpecProto>();

  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));

  // 3. Verify that only the second document is returned since the first
  // document doesn't contain the word 'hello' in either of fields sender.name
  // or body. This confirms that the property filters specified for Email schema
  // have been applied and the ones specified for wildcard entry have been
  // ignored.
  EXPECT_THAT(results.results(0).document(), EqualsProto(document_two));
}

TEST_P(IcingSearchEngineSearchTest, SearchWithNonApplicablePropertyFilters) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one =
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
                  .AddStringProperty("emailAddress", "hellogirl@aol.com")
                  .Build())
          .AddStringProperty("subject", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("subject", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // 2. Issue a query with property filters of sender.name and subject for an
  // unknown schema type.
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("hello");
  search_spec->set_search_type(GetParam());
  TypePropertyMask* email_property_filters =
      search_spec->add_type_property_filters();
  email_property_filters->set_schema_type("unknown");
  email_property_filters->add_paths("sender.name");
  email_property_filters->add_paths("subject");

  auto result_spec = std::make_unique<ResultSpecProto>();

  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(2));

  // 3. Verify that both the documents are returned since each of them have the
  // word 'hello' in at least 1 property. The second document being returned
  // confirms that the body field was searched and the specified property
  // filters were not applied to the Email schema type.
  EXPECT_THAT(results.results(0).document(), EqualsProto(document_two));
  EXPECT_THAT(results.results(1).document(), EqualsProto(document_one));
}

TEST_P(IcingSearchEngineSearchTest, SearchWithEmptyPropertyFilter) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one = DocumentBuilder()
                                   .SetKey("namespace", "uri1")
                                   .SetCreationTimestampMs(1000)
                                   .SetSchema("Message")
                                   .AddStringProperty("body", "Hello World!")
                                   .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  // 2. Issue a query with empty property filter for Message schema.
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("hello");
  search_spec->set_search_type(GetParam());
  TypePropertyMask* message_property_filters =
      search_spec->add_type_property_filters();
  message_property_filters->set_schema_type("Message");

  auto result_spec = std::make_unique<ResultSpecProto>();

  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());

  // 3. Verify that no documents are returned. Although 'hello' is present in
  // the indexed document, it shouldn't be returned since the Message property
  // filter doesn't allow any properties to be searched.
  ASSERT_THAT(results.results(), IsEmpty());
}

TEST_P(IcingSearchEngineSearchTest,
       SearchWithPropertyFilterHavingInvalidProperty) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one = DocumentBuilder()
                                   .SetKey("namespace", "uri1")
                                   .SetCreationTimestampMs(1000)
                                   .SetSchema("Message")
                                   .AddStringProperty("body", "Hello World!")
                                   .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  // 2. Issue a query with property filter having invalid/unknown property for
  // Message schema.
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("hello");
  search_spec->set_search_type(GetParam());
  TypePropertyMask* message_property_filters =
      search_spec->add_type_property_filters();
  message_property_filters->set_schema_type("Message");
  message_property_filters->add_paths("unknown");

  auto result_spec = std::make_unique<ResultSpecProto>();

  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());

  // 3. Verify that no documents are returned. Although 'hello' is present in
  // the indexed document, it shouldn't be returned since the Message property
  // filter doesn't allow any valid properties to be searched. Any
  // invalid/unknown properties specified in the property filters will be
  // ignored while searching.
  ASSERT_THAT(results.results(), IsEmpty());
}

TEST_P(IcingSearchEngineSearchTest, SearchWithPropertyFiltersWithNesting) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one =
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
                  .AddStringProperty("emailAddress", "hellogirl@aol.com")
                  .Build())
          .AddStringProperty("subject", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("subject", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // 2. Issue a query with property filter of sender.emailAddress for the Email
  // schema type.
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("hello");
  search_spec->set_search_type(GetParam());
  TypePropertyMask* email_property_filters =
      search_spec->add_type_property_filters();
  email_property_filters->set_schema_type("Email");
  email_property_filters->add_paths("sender.emailAddress");

  auto result_spec = std::make_unique<ResultSpecProto>();

  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  *scoring_spec = GetDefaultScoringSpec();
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));

  // 3. Verify that only the first document is returned since the second
  // document doesn't contain the word 'hello' in sender.emailAddress. The first
  // document being returned confirms that the nested property
  // sender.emailAddress was actually searched.
  EXPECT_THAT(results.results(0).document(), EqualsProto(document_one));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchWithPropertyFilter_RelevanceScoreUnaffectedByExcludedSectionHits) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add two email documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri1")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Hello Ryan")
                            .AddStringProperty("emailAddress", "hello@aol.com")
                            .Build())
          .AddStringProperty("subject", "Hello Hello!")
          .AddStringProperty("body", "hello1 hello2 hello3 hello4 hello5")
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "world@aol.com")
                            .Build())
          .AddStringProperty("subject", "Hello Hello!")
          .AddStringProperty("body", "one1 two2 three3 four4 five5")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // 2. Issue a query with a property filter
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("Hello");
  search_spec->set_search_type(GetParam());
  TypePropertyMask* email_property_filters =
      search_spec->add_type_property_filters();
  email_property_filters->set_schema_type("Email");
  email_property_filters->add_paths("subject");

  auto result_spec = std::make_unique<ResultSpecProto>();

  // 3. Verify that both documents are returned and have equal relevance score
  // Note, the total number of tokens must be equal in the documents
  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  scoring_spec->set_rank_by(ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE);
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  ASSERT_THAT(results.results(), SizeIs(2));
  EXPECT_THAT(results.results(0).score(), DoubleEq(results.results(1).score()));
}

TEST_P(IcingSearchEngineSearchTest,
       SearchWithPropertyFilter_ExcludingSectionsWithHitsLowersRelevanceScore) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // 1. Add an email document
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri1")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Hello Ryan")
                            .AddStringProperty("emailAddress", "hello@aol.com")
                            .Build())
          .AddStringProperty("subject", "Hello Hello!")
          .AddStringProperty("body", "hello hello hello hello hello")
          .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  // 2. Issue a query without property filter
  auto search_spec = std::make_unique<SearchSpecProto>();
  search_spec->set_term_match_type(TermMatchType::PREFIX);
  search_spec->set_query("Hello");
  search_spec->set_search_type(GetParam());

  auto result_spec = std::make_unique<ResultSpecProto>();

  // 3. Get the relevance score without property filter
  auto scoring_spec = std::make_unique<ScoringSpecProto>();
  scoring_spec->set_rank_by(ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE);
  SearchResultProto results =
      icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  ASSERT_THAT(results.results(), SizeIs(1));
  double original_relevance_score = results.results(0).score();

  // 4. Relevance score with property filter should be lower
  TypePropertyMask* email_property_filters =
      search_spec->add_type_property_filters();
  email_property_filters->set_schema_type("Email");
  email_property_filters->add_paths("subject");
  results = icing.Search(*search_spec, *scoring_spec, *result_spec);
  EXPECT_THAT(results.status(), ProtoIsOk());
  ASSERT_THAT(results.results(), SizeIs(1));
  EXPECT_THAT(results.results(0).score(), Lt(original_relevance_score));
}

TEST_P(IcingSearchEngineSearchTest, QueryStatsProtoTest) {
  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(5);

  // Set index merge size to 6 hits. This will cause document1, document2,
  // document3's hits being merged into the main index, and document4,
  // document5's hits will remain in the lite index.
  IcingSearchEngineOptions options = GetDefaultIcingOptions();
  options.set_index_merge_size(sizeof(TermIdHitPair::Value) * 6);

  TestIcingSearchEngine icing(options, std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // Creates and inserts 5 documents
  DocumentProto document1 = CreateMessageDocument("namespace", "uri1");
  DocumentProto document2 = CreateMessageDocument("namespace", "uri2");
  DocumentProto document3 = CreateMessageDocument("namespace", "uri3");
  DocumentProto document4 = CreateMessageDocument("namespace", "uri4");
  DocumentProto document5 = CreateMessageDocument("namespace", "uri5");
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document4).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document5).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.add_namespace_filters("namespace");
  search_spec.add_schema_type_filters(document1.schema());
  search_spec.set_query("message");
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(2);
  result_spec.mutable_snippet_spec()->set_max_window_utf32_length(64);
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(1);
  result_spec.mutable_snippet_spec()->set_num_to_snippet(3);

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);

  // Searches and gets the first page, 2 results with 2 snippets
  SearchResultProto search_result =
      icing.Search(search_spec, scoring_spec, result_spec);
  ASSERT_THAT(search_result.status(), ProtoIsOk());
  ASSERT_THAT(search_result.results(), SizeIs(2));
  ASSERT_THAT(search_result.next_page_token(), Ne(kInvalidNextPageToken));

  // Check the stats
  // TODO(b/305098009): deprecate search-related flat fields in query_stats.
  QueryStatsProto exp_stats;
  exp_stats.set_query_length(7);
  exp_stats.set_num_terms(1);
  exp_stats.set_num_namespaces_filtered(1);
  exp_stats.set_num_schema_types_filtered(1);
  exp_stats.set_ranking_strategy(
      ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);
  exp_stats.set_is_first_page(true);
  exp_stats.set_requested_page_size(2);
  exp_stats.set_num_results_returned_current_page(2);
  exp_stats.set_num_documents_scored(5);
  exp_stats.set_num_results_with_snippets(2);
  exp_stats.set_latency_ms(5);
  exp_stats.set_parse_query_latency_ms(5);
  exp_stats.set_scoring_latency_ms(5);
  exp_stats.set_ranking_latency_ms(5);
  exp_stats.set_document_retrieval_latency_ms(5);
  exp_stats.set_lock_acquisition_latency_ms(5);
  exp_stats.set_num_joined_results_returned_current_page(0);
  // document4, document5's hits will remain in the lite index (# of hits: 4).
  exp_stats.set_lite_index_hit_buffer_byte_size(4 *
                                                sizeof(TermIdHitPair::Value));
  exp_stats.set_lite_index_hit_buffer_unsorted_byte_size(
      4 * sizeof(TermIdHitPair::Value));

  QueryStatsProto::SearchStats* exp_parent_search_stats =
      exp_stats.mutable_parent_search_stats();
  exp_parent_search_stats->set_query_length(7);
  exp_parent_search_stats->set_num_terms(1);
  exp_parent_search_stats->set_num_namespaces_filtered(1);
  exp_parent_search_stats->set_num_schema_types_filtered(1);
  exp_parent_search_stats->set_ranking_strategy(
      ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);
  exp_parent_search_stats->set_num_documents_scored(5);
  exp_parent_search_stats->set_parse_query_latency_ms(5);
  exp_parent_search_stats->set_scoring_latency_ms(5);
  exp_parent_search_stats->set_num_fetched_hits_lite_index(2);
  exp_parent_search_stats->set_num_fetched_hits_main_index(3);
  exp_parent_search_stats->set_num_fetched_hits_integer_index(0);
  if (GetParam() ==
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY) {
    exp_parent_search_stats->set_query_processor_lexer_extract_token_latency_ms(
        5);
    exp_parent_search_stats
        ->set_query_processor_parser_consume_query_latency_ms(5);
    exp_parent_search_stats->set_query_processor_query_visitor_latency_ms(5);
  }

  EXPECT_THAT(search_result.query_stats(), EqualsProto(exp_stats));

  // Second page, 2 result with 1 snippet
  search_result = icing.GetNextPage(search_result.next_page_token());
  ASSERT_THAT(search_result.status(), ProtoIsOk());
  ASSERT_THAT(search_result.results(), SizeIs(2));
  ASSERT_THAT(search_result.next_page_token(), Gt(kInvalidNextPageToken));

  exp_stats = QueryStatsProto();
  exp_stats.set_is_first_page(false);
  exp_stats.set_requested_page_size(2);
  exp_stats.set_num_results_returned_current_page(2);
  exp_stats.set_num_results_with_snippets(1);
  exp_stats.set_latency_ms(5);
  exp_stats.set_document_retrieval_latency_ms(5);
  exp_stats.set_lock_acquisition_latency_ms(5);
  exp_stats.set_num_joined_results_returned_current_page(0);
  EXPECT_THAT(search_result.query_stats(), EqualsProto(exp_stats));

  // Third page, 1 result with 0 snippets
  search_result = icing.GetNextPage(search_result.next_page_token());
  ASSERT_THAT(search_result.status(), ProtoIsOk());
  ASSERT_THAT(search_result.results(), SizeIs(1));
  ASSERT_THAT(search_result.next_page_token(), Eq(kInvalidNextPageToken));

  exp_stats = QueryStatsProto();
  exp_stats.set_is_first_page(false);
  exp_stats.set_requested_page_size(2);
  exp_stats.set_num_results_returned_current_page(1);
  exp_stats.set_num_results_with_snippets(0);
  exp_stats.set_latency_ms(5);
  exp_stats.set_document_retrieval_latency_ms(5);
  exp_stats.set_lock_acquisition_latency_ms(5);
  exp_stats.set_num_joined_results_returned_current_page(0);
  EXPECT_THAT(search_result.query_stats(), EqualsProto(exp_stats));
}

TEST_P(IcingSearchEngineSearchTest, JoinQueryStatsProtoTest) {
  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(5);

  // Set index merge size to 13 hits. This will cause person1, person2, email1,
  // email2, email3's hits being merged into the main index, and person3,
  // email4's hits will remain in the lite index.
  IcingSearchEngineOptions options = GetDefaultIcingOptions();
  options.set_index_merge_size(sizeof(TermIdHitPair::Value) * 13);

  TestIcingSearchEngine icing(options, std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());

  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Person")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("firstName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("lastName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emailAddress")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("personQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  DocumentProto person1 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person1")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first1")
          .AddStringProperty("lastName", "last1")
          .AddStringProperty("emailAddress", "email1@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(1)
          .Build();
  DocumentProto person2 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person2")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first2")
          .AddStringProperty("lastName", "last2")
          .AddStringProperty("emailAddress", "email2@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(2)
          .Build();
  DocumentProto person3 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person3")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first3")
          .AddStringProperty("lastName", "last3")
          .AddStringProperty("emailAddress", "email3@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(3)
          .Build();

  DocumentProto email1 =
      DocumentBuilder()
          .SetKey("namespace", "email1")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 1")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(3)
          .Build();
  DocumentProto email2 =
      DocumentBuilder()
          .SetKey("namespace", "email2")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 2")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(2)
          .Build();
  DocumentProto email3 =
      DocumentBuilder()
          .SetKey("namespace", "email3")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 3")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(1)
          .Build();
  DocumentProto email4 =
      DocumentBuilder()
          .SetKey("namespace", "email4")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 4")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(0)
          .Build();

  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email4).status(), ProtoIsOk());

  // Parent SearchSpec
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("firstName:first");
  search_spec.set_search_type(GetParam());

  // JoinSpec
  JoinSpecProto* join_spec = search_spec.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("personQualifiedId");
  join_spec->set_aggregation_scoring_strategy(
      JoinSpecProto::AggregationScoringStrategy::COUNT);
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::PREFIX);
  nested_search_spec->set_query("subject:test");
  nested_search_spec->set_search_type(GetParam());
  *nested_spec->mutable_scoring_spec() = GetDefaultScoringSpec();
  *nested_spec->mutable_result_spec() = ResultSpecProto::default_instance();

  // Parent ScoringSpec
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::JOIN_AGGREGATE_SCORE);
  scoring_spec.set_order_by(ScoringSpecProto::Order::DESC);

  // Parent ResultSpec
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);
  result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());

  // Since we:
  // - Use COUNT for aggregation scoring strategy.
  // - (Default) use DOCUMENT_SCORE to score child documents.
  // - (Default) use DESC as the ranking order.
  //
  // person1 with [email1, email2, email4] should have the highest aggregated
  // score (3) and be returned first. person2 with [email3] (aggregated score =
  // 1) should be the second, and person3 with no child (aggregated score = 0)
  // should be the last.
  SearchResultProto expected_result1;
  expected_result1.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto::ResultProto* result_proto1 =
      expected_result1.mutable_results()->Add();
  *result_proto1->mutable_document() = person1;
  *result_proto1->mutable_joined_results()->Add()->mutable_document() = email1;
  *result_proto1->mutable_joined_results()->Add()->mutable_document() = email2;
  *result_proto1->mutable_joined_results()->Add()->mutable_document() = email4;

  SearchResultProto expected_result2;
  expected_result2.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto::ResultProto* result_google::protobuf =
      expected_result2.mutable_results()->Add();
  *result_google::protobuf->mutable_document() = person2;
  *result_google::protobuf->mutable_joined_results()->Add()->mutable_document() = email3;

  SearchResultProto expected_result3;
  expected_result3.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto::ResultProto* result_proto3 =
      expected_result3.mutable_results()->Add();
  *result_proto3->mutable_document() = person3;

  SearchResultProto search_result =
      icing.Search(search_spec, scoring_spec, result_spec);
  uint64_t next_page_token = search_result.next_page_token();
  EXPECT_THAT(next_page_token, Ne(kInvalidNextPageToken));
  expected_result1.set_next_page_token(next_page_token);
  ASSERT_THAT(search_result,
              EqualsSearchResultIgnoreStatsAndScores(expected_result1));

  // Check the stats
  // TODO(b/305098009): deprecate search-related flat fields in query_stats.
  QueryStatsProto exp_stats;
  exp_stats.set_query_length(15);
  exp_stats.set_num_terms(1);
  exp_stats.set_num_namespaces_filtered(0);
  exp_stats.set_num_schema_types_filtered(0);
  exp_stats.set_ranking_strategy(
      ScoringSpecProto::RankingStrategy::JOIN_AGGREGATE_SCORE);
  exp_stats.set_is_first_page(true);
  exp_stats.set_requested_page_size(1);
  exp_stats.set_num_results_returned_current_page(1);
  exp_stats.set_num_documents_scored(3);
  exp_stats.set_num_results_with_snippets(0);
  exp_stats.set_latency_ms(5);
  exp_stats.set_parse_query_latency_ms(5);
  exp_stats.set_scoring_latency_ms(5);
  exp_stats.set_ranking_latency_ms(5);
  exp_stats.set_document_retrieval_latency_ms(5);
  exp_stats.set_lock_acquisition_latency_ms(5);
  exp_stats.set_num_joined_results_returned_current_page(3);
  exp_stats.set_join_latency_ms(5);
  exp_stats.set_is_join_query(true);
  // person3, email4's hits will remain in the lite index (# of hits: 5).
  exp_stats.set_lite_index_hit_buffer_byte_size(5 *
                                                sizeof(TermIdHitPair::Value));
  exp_stats.set_lite_index_hit_buffer_unsorted_byte_size(
      5 * sizeof(TermIdHitPair::Value));

  QueryStatsProto::SearchStats* exp_parent_search_stats =
      exp_stats.mutable_parent_search_stats();
  exp_parent_search_stats->set_query_length(15);
  exp_parent_search_stats->set_num_terms(1);
  exp_parent_search_stats->set_num_namespaces_filtered(0);
  exp_parent_search_stats->set_num_schema_types_filtered(0);
  exp_parent_search_stats->set_ranking_strategy(
      ScoringSpecProto::RankingStrategy::JOIN_AGGREGATE_SCORE);
  exp_parent_search_stats->set_num_documents_scored(3);
  exp_parent_search_stats->set_parse_query_latency_ms(5);
  exp_parent_search_stats->set_scoring_latency_ms(5);
  exp_parent_search_stats->set_num_fetched_hits_lite_index(1);
  exp_parent_search_stats->set_num_fetched_hits_main_index(2);
  exp_parent_search_stats->set_num_fetched_hits_integer_index(0);
  if (GetParam() ==
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY) {
    exp_parent_search_stats->set_query_processor_lexer_extract_token_latency_ms(
        5);
    exp_parent_search_stats
        ->set_query_processor_parser_consume_query_latency_ms(5);
    exp_parent_search_stats->set_query_processor_query_visitor_latency_ms(5);
  }

  QueryStatsProto::SearchStats* exp_child_search_stats =
      exp_stats.mutable_child_search_stats();
  exp_child_search_stats->set_query_length(12);
  exp_child_search_stats->set_num_terms(1);
  exp_child_search_stats->set_num_namespaces_filtered(0);
  exp_child_search_stats->set_num_schema_types_filtered(0);
  exp_child_search_stats->set_ranking_strategy(
      ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);
  exp_child_search_stats->set_num_documents_scored(4);
  exp_child_search_stats->set_parse_query_latency_ms(5);
  exp_child_search_stats->set_scoring_latency_ms(5);
  exp_child_search_stats->set_num_fetched_hits_lite_index(1);
  exp_child_search_stats->set_num_fetched_hits_main_index(3);
  exp_child_search_stats->set_num_fetched_hits_integer_index(0);
  if (GetParam() ==
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY) {
    exp_child_search_stats->set_query_processor_lexer_extract_token_latency_ms(
        5);
    exp_child_search_stats->set_query_processor_parser_consume_query_latency_ms(
        5);
    exp_child_search_stats->set_query_processor_query_visitor_latency_ms(5);
  }

  EXPECT_THAT(search_result.query_stats(), EqualsProto(exp_stats));

  // Second page, 1 child doc.
  search_result = icing.GetNextPage(next_page_token);
  next_page_token = search_result.next_page_token();
  EXPECT_THAT(next_page_token, Ne(kInvalidNextPageToken));
  expected_result2.set_next_page_token(next_page_token);
  EXPECT_THAT(search_result,
              EqualsSearchResultIgnoreStatsAndScores(expected_result2));

  exp_stats = QueryStatsProto();
  exp_stats.set_is_first_page(false);
  exp_stats.set_requested_page_size(1);
  exp_stats.set_num_results_returned_current_page(1);
  exp_stats.set_num_results_with_snippets(0);
  exp_stats.set_latency_ms(5);
  exp_stats.set_document_retrieval_latency_ms(5);
  exp_stats.set_lock_acquisition_latency_ms(5);
  exp_stats.set_num_joined_results_returned_current_page(1);
  EXPECT_THAT(search_result.query_stats(), EqualsProto(exp_stats));

  // Third page, 0 child docs.
  search_result = icing.GetNextPage(next_page_token);
  next_page_token = search_result.next_page_token();
  ASSERT_THAT(search_result.status(), ProtoIsOk());
  ASSERT_THAT(search_result.results(), SizeIs(1));
  ASSERT_THAT(search_result.next_page_token(), Eq(kInvalidNextPageToken));

  exp_stats = QueryStatsProto();
  exp_stats.set_is_first_page(false);
  exp_stats.set_requested_page_size(1);
  exp_stats.set_num_results_returned_current_page(1);
  exp_stats.set_num_joined_results_returned_current_page(0);
  exp_stats.set_latency_ms(5);
  exp_stats.set_document_retrieval_latency_ms(5);
  exp_stats.set_lock_acquisition_latency_ms(5);
  exp_stats.set_num_results_with_snippets(0);
  ASSERT_THAT(search_result,
              EqualsSearchResultIgnoreStatsAndScores(expected_result3));
  EXPECT_THAT(search_result.query_stats(), EqualsProto(exp_stats));

  ASSERT_THAT(search_result.next_page_token(), Eq(kInvalidNextPageToken));

  search_result = icing.GetNextPage(search_result.next_page_token());
  ASSERT_THAT(search_result.status(), ProtoIsOk());
  ASSERT_THAT(search_result.results(), IsEmpty());
  ASSERT_THAT(search_result.next_page_token(), Eq(kInvalidNextPageToken));

  exp_stats = QueryStatsProto();
  exp_stats.set_is_first_page(false);
  exp_stats.set_lock_acquisition_latency_ms(5);
  EXPECT_THAT(search_result.query_stats(), EqualsProto(exp_stats));
}

TEST_P(IcingSearchEngineSearchTest, SnippetErrorTest) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Generic").AddProperty(
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetScore(10)
          .SetSchema("Generic")
          .AddStringProperty("subject", "I like cats", "I like dogs",
                             "I like birds", "I like fish")
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetScore(20)
          .SetSchema("Generic")
          .AddStringProperty("subject", "I like red", "I like green",
                             "I like blue", "I like yellow")
          .Build();
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri3")
          .SetScore(5)
          .SetSchema("Generic")
          .AddStringProperty("subject", "I like cupcakes", "I like donuts",
                             "I like eclairs", "I like froyo")
          .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.add_schema_type_filters("Generic");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("like");
  search_spec.set_search_type(GetParam());
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);
  ResultSpecProto result_spec;
  result_spec.mutable_snippet_spec()->set_num_to_snippet(2);
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(3);
  result_spec.mutable_snippet_spec()->set_max_window_utf32_length(4);
  SearchResultProto search_results =
      icing.Search(search_spec, scoring_spec, result_spec);

  ASSERT_THAT(search_results.results(), SizeIs(3));
  const SearchResultProto::ResultProto* result = &search_results.results(0);
  EXPECT_THAT(result->document().uri(), Eq("uri2"));
  ASSERT_THAT(result->snippet().entries(), SizeIs(3));
  const SnippetProto::EntryProto* entry = &result->snippet().entries(0);
  EXPECT_THAT(entry->property_name(), "subject[0]");
  std::string_view content = GetString(&result->document(), "subject[0]");
  EXPECT_THAT(GetMatches(content, *entry), ElementsAre("like"));

  entry = &result->snippet().entries(1);
  EXPECT_THAT(entry->property_name(), "subject[1]");
  content = GetString(&result->document(), "subject[1]");
  EXPECT_THAT(GetMatches(content, *entry), ElementsAre("like"));

  entry = &result->snippet().entries(2);
  EXPECT_THAT(entry->property_name(), "subject[2]");
  content = GetString(&result->document(), "subject[2]");
  EXPECT_THAT(GetMatches(content, *entry), ElementsAre("like"));

  result = &search_results.results(1);
  EXPECT_THAT(result->document().uri(), Eq("uri1"));
  ASSERT_THAT(result->snippet().entries(), SizeIs(3));
  entry = &result->snippet().entries(0);
  EXPECT_THAT(entry->property_name(), "subject[0]");
  content = GetString(&result->document(), "subject[0]");
  EXPECT_THAT(GetMatches(content, *entry), ElementsAre("like"));

  entry = &result->snippet().entries(1);
  ASSERT_THAT(entry->property_name(), "subject[1]");
  content = GetString(&result->document(), "subject[1]");
  EXPECT_THAT(GetMatches(content, *entry), ElementsAre("like"));

  entry = &result->snippet().entries(2);
  ASSERT_THAT(entry->property_name(), "subject[2]");
  content = GetString(&result->document(), "subject[2]");
  EXPECT_THAT(GetMatches(content, *entry), ElementsAre("like"));

  result = &search_results.results(2);
  ASSERT_THAT(result->document().uri(), Eq("uri3"));
  ASSERT_THAT(result->snippet().entries(), IsEmpty());
}

TEST_P(IcingSearchEngineSearchTest, CJKSnippetTest) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // String:     "我每天走路去上班。"
  //              ^ ^  ^   ^^
  // UTF8 idx:    0 3  9  15 18
  // UTF16 idx:   0 1  3   5 6
  // Breaks into segments: "我", "每天", "走路", "去", "上班"
  constexpr std::string_view kChinese = "我每天走路去上班。";
  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "uri1")
                               .SetSchema("Message")
                               .AddStringProperty("body", kChinese)
                               .Build();
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  // Search and request snippet matching but no windowing.
  SearchSpecProto search_spec;
  search_spec.set_query("走");
  search_spec.set_term_match_type(TERM_MATCH_PREFIX);
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.mutable_snippet_spec()->set_num_to_snippet(
      std::numeric_limits<int>::max());
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(
      std::numeric_limits<int>::max());

  // Search and make sure that we got a single successful result
  SearchResultProto search_results = icing.Search(
      search_spec, ScoringSpecProto::default_instance(), result_spec);
  ASSERT_THAT(search_results.status(), ProtoIsOk());
  ASSERT_THAT(search_results.results(), SizeIs(1));
  const SearchResultProto::ResultProto* result = &search_results.results(0);
  EXPECT_THAT(result->document().uri(), Eq("uri1"));

  // Ensure that one and only one property was matched and it was "body"
  ASSERT_THAT(result->snippet().entries(), SizeIs(1));
  const SnippetProto::EntryProto* entry = &result->snippet().entries(0);
  EXPECT_THAT(entry->property_name(), Eq("body"));

  // Get the content for "subject" and see what the match is.
  std::string_view content = GetString(&result->document(), "body");
  ASSERT_THAT(content, Eq(kChinese));

  // Ensure that there is one and only one match within "subject"
  ASSERT_THAT(entry->snippet_matches(), SizeIs(1));
  const SnippetMatchProto& match_proto = entry->snippet_matches(0);

  EXPECT_THAT(match_proto.exact_match_byte_position(), Eq(9));
  EXPECT_THAT(match_proto.exact_match_byte_length(), Eq(6));
  std::string_view match =
      content.substr(match_proto.exact_match_byte_position(),
                     match_proto.exact_match_byte_length());
  ASSERT_THAT(match, Eq("走路"));

  // Ensure that the utf-16 values are also as expected
  EXPECT_THAT(match_proto.exact_match_utf16_position(), Eq(3));
  EXPECT_THAT(match_proto.exact_match_utf16_length(), Eq(2));
}

TEST_P(IcingSearchEngineSearchTest, InvalidToEmptyQueryTest) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // String:     "Luca Brasi sleeps with the 🐟🐟🐟."
  //              ^    ^     ^      ^    ^   ^ ^  ^ ^
  // UTF8 idx:    0    5     11     18   23 27 3135 39
  // UTF16 idx:   0    5     11     18   23 27 2931 33
  // Breaks into segments: "Luca", "Brasi", "sleeps", "with", "the", "🐟", "🐟"
  // and "🐟".
  constexpr std::string_view kSicilianMessage =
      "Luca Brasi sleeps with the 🐟🐟🐟.";
  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "uri1")
                               .SetSchema("Message")
                               .AddStringProperty("body", kSicilianMessage)
                               .Build();
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "Some other content.")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // Search and request snippet matching but no windowing.
  SearchSpecProto search_spec;
  search_spec.set_query("?");
  search_spec.set_term_match_type(TERM_MATCH_PREFIX);
  search_spec.set_search_type(GetParam());
  ScoringSpecProto scoring_spec;
  ResultSpecProto result_spec;

  // Search and make sure that we got a single successful result
  SearchResultProto search_results =
      icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(search_results.status(), ProtoIsOk());
  if (GetParam() ==
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY) {
    // This is the actual correct behavior.
    EXPECT_THAT(search_results.results(), IsEmpty());
  } else {
    EXPECT_THAT(search_results.results(), SizeIs(2));
  }

  search_spec.set_query("。");
  search_results = icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(search_results.status(), ProtoIsOk());
  if (GetParam() ==
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY) {
    // This is the actual correct behavior.
    EXPECT_THAT(search_results.results(), IsEmpty());
  } else {
    EXPECT_THAT(search_results.results(), SizeIs(2));
  }

  search_spec.set_query("-");
  search_results = icing.Search(search_spec, scoring_spec, result_spec);
  if (GetParam() ==
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY) {
    // This is the actual correct behavior.
    EXPECT_THAT(search_results.status(),
                ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
  } else {
    EXPECT_THAT(search_results.status(), ProtoIsOk());
    EXPECT_THAT(search_results.results(), SizeIs(2));
  }

  search_spec.set_query(":");
  search_results = icing.Search(search_spec, scoring_spec, result_spec);
  if (GetParam() ==
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY) {
    // This is the actual correct behavior.
    EXPECT_THAT(search_results.status(),
                ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
  } else {
    EXPECT_THAT(search_results.status(), ProtoIsOk());
    EXPECT_THAT(search_results.results(), SizeIs(2));
  }

  search_spec.set_query("OR");
  search_results = icing.Search(search_spec, scoring_spec, result_spec);
  if (GetParam() ==
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY) {
    EXPECT_THAT(search_results.status(),
                ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
  } else {
    EXPECT_THAT(search_results.status(), ProtoIsOk());
    EXPECT_THAT(search_results.results(), SizeIs(2));
  }

  search_spec.set_query(" ");
  search_results = icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(search_results.status(), ProtoIsOk());
  EXPECT_THAT(search_results.results(), SizeIs(2));
}

TEST_P(IcingSearchEngineSearchTest, EmojiSnippetTest) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  // String:     "Luca Brasi sleeps with the 🐟🐟🐟."
  //              ^    ^     ^      ^    ^   ^ ^  ^ ^
  // UTF8 idx:    0    5     11     18   23 27 3135 39
  // UTF16 idx:   0    5     11     18   23 27 2931 33
  // Breaks into segments: "Luca", "Brasi", "sleeps", "with", "the", "🐟", "🐟"
  // and "🐟".
  constexpr std::string_view kSicilianMessage =
      "Luca Brasi sleeps with the 🐟🐟🐟.";
  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "uri1")
                               .SetSchema("Message")
                               .AddStringProperty("body", kSicilianMessage)
                               .Build();
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "Some other content.")
          .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  // Search and request snippet matching but no windowing.
  SearchSpecProto search_spec;
  search_spec.set_query("🐟");
  search_spec.set_term_match_type(TERM_MATCH_PREFIX);
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.mutable_snippet_spec()->set_num_to_snippet(1);
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(1);

  // Search and make sure that we got a single successful result
  SearchResultProto search_results = icing.Search(
      search_spec, ScoringSpecProto::default_instance(), result_spec);
  ASSERT_THAT(search_results.status(), ProtoIsOk());
  ASSERT_THAT(search_results.results(), SizeIs(1));
  const SearchResultProto::ResultProto* result = &search_results.results(0);
  EXPECT_THAT(result->document().uri(), Eq("uri1"));

  // Ensure that one and only one property was matched and it was "body"
  ASSERT_THAT(result->snippet().entries(), SizeIs(1));
  const SnippetProto::EntryProto* entry = &result->snippet().entries(0);
  EXPECT_THAT(entry->property_name(), Eq("body"));

  // Get the content for "subject" and see what the match is.
  std::string_view content = GetString(&result->document(), "body");
  ASSERT_THAT(content, Eq(kSicilianMessage));

  // Ensure that there is one and only one match within "subject"
  ASSERT_THAT(entry->snippet_matches(), SizeIs(1));
  const SnippetMatchProto& match_proto = entry->snippet_matches(0);

  EXPECT_THAT(match_proto.exact_match_byte_position(), Eq(27));
  EXPECT_THAT(match_proto.exact_match_byte_length(), Eq(4));
  std::string_view match =
      content.substr(match_proto.exact_match_byte_position(),
                     match_proto.exact_match_byte_length());
  ASSERT_THAT(match, Eq("🐟"));

  // Ensure that the utf-16 values are also as expected
  EXPECT_THAT(match_proto.exact_match_utf16_position(), Eq(27));
  EXPECT_THAT(match_proto.exact_match_utf16_length(), Eq(2));
}

TEST_P(IcingSearchEngineSearchTest, JoinByQualifiedId) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Person")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("firstName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("lastName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emailAddress")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("personQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  DocumentProto person1 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person1")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first1")
          .AddStringProperty("lastName", "last1")
          .AddStringProperty("emailAddress", "email1@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(1)
          .Build();
  DocumentProto person2 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person2")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first2")
          .AddStringProperty("lastName", "last2")
          .AddStringProperty("emailAddress", "email2@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(2)
          .Build();
  DocumentProto person3 =
      DocumentBuilder()
          .SetKey(R"(pkg$db/name#space\\)", "person3")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first3")
          .AddStringProperty("lastName", "last3")
          .AddStringProperty("emailAddress", "email3@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(3)
          .Build();

  DocumentProto email1 =
      DocumentBuilder()
          .SetKey("namespace", "email1")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 1")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(3)
          .Build();
  DocumentProto email2 =
      DocumentBuilder()
          .SetKey("namespace", "email2")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 2")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(2)
          .Build();
  DocumentProto email3 =
      DocumentBuilder()
          .SetKey("namespace", "email3")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 3")
          .AddStringProperty("personQualifiedId",
                             R"(pkg$db/name\#space\\\\#person3)")  // escaped
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(1)
          .Build();

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email3).status(), ProtoIsOk());

  // Parent SearchSpec
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("firstName:first");
  search_spec.set_search_type(GetParam());

  // JoinSpec
  JoinSpecProto* join_spec = search_spec.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("personQualifiedId");
  join_spec->set_aggregation_scoring_strategy(
      JoinSpecProto::AggregationScoringStrategy::MAX);
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::PREFIX);
  nested_search_spec->set_query("subject:test");
  nested_search_spec->set_search_type(GetParam());
  *nested_spec->mutable_scoring_spec() = GetDefaultScoringSpec();
  *nested_spec->mutable_result_spec() = ResultSpecProto::default_instance();

  // Parent ScoringSpec
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();

  // Parent ResultSpec
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);
  result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());

  // Since we:
  // - Use MAX for aggregation scoring strategy.
  // - (Default) use DOCUMENT_SCORE to score child documents.
  // - (Default) use DESC as the ranking order.
  //
  // person1 + email1 should have the highest aggregated score (3) and be
  // returned first. person2 + email2 (aggregated score = 2) should be the
  // second, and person3 + email3 (aggregated score = 1) should be the last.
  SearchResultProto expected_result1;
  expected_result1.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto::ResultProto* result_proto1 =
      expected_result1.mutable_results()->Add();
  *result_proto1->mutable_document() = person1;
  *result_proto1->mutable_joined_results()->Add()->mutable_document() = email1;

  SearchResultProto expected_result2;
  expected_result2.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto::ResultProto* result_google::protobuf =
      expected_result2.mutable_results()->Add();
  *result_google::protobuf->mutable_document() = person2;
  *result_google::protobuf->mutable_joined_results()->Add()->mutable_document() = email2;

  SearchResultProto expected_result3;
  expected_result3.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto::ResultProto* result_proto3 =
      expected_result3.mutable_results()->Add();
  *result_proto3->mutable_document() = person3;
  *result_proto3->mutable_joined_results()->Add()->mutable_document() = email3;

  SearchResultProto result1 =
      icing.Search(search_spec, scoring_spec, result_spec);
  uint64_t next_page_token = result1.next_page_token();
  EXPECT_THAT(next_page_token, Ne(kInvalidNextPageToken));
  expected_result1.set_next_page_token(next_page_token);
  EXPECT_THAT(result1,
              EqualsSearchResultIgnoreStatsAndScores(expected_result1));

  SearchResultProto result2 = icing.GetNextPage(next_page_token);
  next_page_token = result2.next_page_token();
  EXPECT_THAT(next_page_token, Ne(kInvalidNextPageToken));
  expected_result2.set_next_page_token(next_page_token);
  EXPECT_THAT(result2,
              EqualsSearchResultIgnoreStatsAndScores(expected_result2));

  SearchResultProto result3 = icing.GetNextPage(next_page_token);
  next_page_token = result3.next_page_token();
  EXPECT_THAT(next_page_token, Eq(kInvalidNextPageToken));
  EXPECT_THAT(result3,
              EqualsSearchResultIgnoreStatsAndScores(expected_result3));
}

TEST_P(IcingSearchEngineSearchTest, JoinByQualifiedIdMultipleNamespaces) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Person")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("firstName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("lastName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emailAddress")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("personQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  DocumentProto person1 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace1", "person")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first1")
          .AddStringProperty("lastName", "last1")
          .AddStringProperty("emailAddress", "email1@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(1)
          .Build();
  DocumentProto person2 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace2", "person")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first2")
          .AddStringProperty("lastName", "last2")
          .AddStringProperty("emailAddress", "email2@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(2)
          .Build();

  DocumentProto email1 =
      DocumentBuilder()
          .SetKey("namespace1", "email1")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 1")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace1#person")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(3)
          .Build();
  DocumentProto email2 =
      DocumentBuilder()
          .SetKey("namespace2", "email2")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 2")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace1#person")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(2)
          .Build();
  DocumentProto email3 =
      DocumentBuilder()
          .SetKey("namespace2", "email3")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 3")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace2#person")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(1)
          .Build();

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email3).status(), ProtoIsOk());

  // Parent SearchSpec
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("firstName:first");
  search_spec.set_search_type(GetParam());

  // JoinSpec
  JoinSpecProto* join_spec = search_spec.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("personQualifiedId");
  join_spec->set_aggregation_scoring_strategy(
      JoinSpecProto::AggregationScoringStrategy::COUNT);
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::PREFIX);
  nested_search_spec->set_query("subject:test");
  nested_search_spec->set_search_type(GetParam());
  *nested_spec->mutable_scoring_spec() = GetDefaultScoringSpec();
  *nested_spec->mutable_result_spec() = ResultSpecProto::default_instance();

  // Parent ScoringSpec
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();

  // Parent ResultSpec
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);
  result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());

  // Since we:
  // - Use COUNT for aggregation scoring strategy.
  // - (Default) use DESC as the ranking order.
  //
  // pkg$db/namespace1#person + email1, email2 should have the highest
  // aggregated score (2) and be returned first. pkg$db/namespace2#person +
  // email3 (aggregated score = 1) should be the second.
  SearchResultProto expected_result1;
  expected_result1.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto::ResultProto* result_proto1 =
      expected_result1.mutable_results()->Add();
  *result_proto1->mutable_document() = person1;
  *result_proto1->mutable_joined_results()->Add()->mutable_document() = email1;
  *result_proto1->mutable_joined_results()->Add()->mutable_document() = email2;

  SearchResultProto expected_result2;
  expected_result2.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto::ResultProto* result_google::protobuf =
      expected_result2.mutable_results()->Add();
  *result_google::protobuf->mutable_document() = person2;
  *result_google::protobuf->mutable_joined_results()->Add()->mutable_document() = email3;

  SearchResultProto result1 =
      icing.Search(search_spec, scoring_spec, result_spec);
  uint64_t next_page_token = result1.next_page_token();
  EXPECT_THAT(next_page_token, Ne(kInvalidNextPageToken));
  expected_result1.set_next_page_token(next_page_token);
  EXPECT_THAT(result1,
              EqualsSearchResultIgnoreStatsAndScores(expected_result1));

  SearchResultProto result2 = icing.GetNextPage(next_page_token);
  next_page_token = result2.next_page_token();
  EXPECT_THAT(next_page_token, Eq(kInvalidNextPageToken));
  EXPECT_THAT(result2,
              EqualsSearchResultIgnoreStatsAndScores(expected_result2));
}

TEST_P(IcingSearchEngineSearchTest,
       JoinShouldLimitNumChildDocumentsByMaxJoinedChildPerParent) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Person")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("firstName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("lastName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emailAddress")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("personQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  DocumentProto person1 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person1")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first1")
          .AddStringProperty("lastName", "last1")
          .AddStringProperty("emailAddress", "email1@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(1)
          .Build();
  DocumentProto person2 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person2")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first2")
          .AddStringProperty("lastName", "last2")
          .AddStringProperty("emailAddress", "email2@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(2)
          .Build();

  DocumentProto email1 =
      DocumentBuilder()
          .SetKey("namespace", "email1")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 1")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(100)
          .Build();
  DocumentProto email2 =
      DocumentBuilder()
          .SetKey("namespace", "email2")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 2")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(99)
          .Build();
  DocumentProto email3 =
      DocumentBuilder()
          .SetKey("namespace", "email3")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 3")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(98)
          .Build();
  DocumentProto email4 =
      DocumentBuilder()
          .SetKey("namespace", "email4")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 4")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(97)
          .Build();

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email4).status(), ProtoIsOk());

  // Parent SearchSpec
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("firstName:first");
  search_spec.set_search_type(GetParam());

  // JoinSpec
  JoinSpecProto* join_spec = search_spec.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("personQualifiedId");
  join_spec->set_aggregation_scoring_strategy(
      JoinSpecProto::AggregationScoringStrategy::COUNT);
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::PREFIX);
  nested_search_spec->set_query("subject:test");
  nested_search_spec->set_search_type(GetParam());
  *nested_spec->mutable_scoring_spec() = GetDefaultScoringSpec();
  *nested_spec->mutable_result_spec() = ResultSpecProto::default_instance();

  // Parent ScoringSpec
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();

  // Parent ResultSpec with max_joined_children_per_parent_to_return = 2
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);
  result_spec.set_max_joined_children_per_parent_to_return(2);

  // - Use COUNT for aggregation scoring strategy.
  // - max_joined_children_per_parent_to_return = 2.
  // - (Default) use DESC as the ranking order.
  //
  // person2 should have the highest aggregated score (3) since email2, email3,
  // email4 are joined to it and the COUNT aggregated score is 3. However, only
  // email2 and email3 should be attached to person2 due to
  // max_joined_children_per_parent_to_return limitation in result_spec.
  // person1 should be the second (aggregated score = 1).
  SearchResultProto::ResultProto expected_result_proto1;
  *expected_result_proto1.mutable_document() = person2;
  expected_result_proto1.set_score(3);
  SearchResultProto::ResultProto* child_result_proto1 =
      expected_result_proto1.mutable_joined_results()->Add();
  *child_result_proto1->mutable_document() = email2;
  child_result_proto1->set_score(99);
  SearchResultProto::ResultProto* child_result_google::protobuf =
      expected_result_proto1.mutable_joined_results()->Add();
  *child_result_google::protobuf->mutable_document() = email3;
  child_result_google::protobuf->set_score(98);

  SearchResultProto::ResultProto expected_result_google::protobuf;
  *expected_result_google::protobuf.mutable_document() = person1;
  expected_result_google::protobuf.set_score(1);
  SearchResultProto::ResultProto* child_result_proto3 =
      expected_result_google::protobuf.mutable_joined_results()->Add();
  *child_result_proto3->mutable_document() = email1;
  child_result_proto3->set_score(100);

  SearchResultProto result1 =
      icing.Search(search_spec, scoring_spec, result_spec);
  uint64_t next_page_token = result1.next_page_token();
  EXPECT_THAT(next_page_token, Ne(kInvalidNextPageToken));
  EXPECT_THAT(result1.results(),
              ElementsAre(EqualsProto(expected_result_proto1)));

  SearchResultProto result2 = icing.GetNextPage(next_page_token);
  next_page_token = result2.next_page_token();
  EXPECT_THAT(next_page_token, Eq(kInvalidNextPageToken));
  EXPECT_THAT(result2.results(),
              ElementsAre(EqualsProto(expected_result_google::protobuf)));
}

TEST_P(IcingSearchEngineSearchTest, JoinWithZeroMaxJoinedChildPerParent) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Person")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("firstName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("lastName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emailAddress")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("personQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  DocumentProto person1 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person1")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first1")
          .AddStringProperty("lastName", "last1")
          .AddStringProperty("emailAddress", "email1@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(1)
          .Build();
  DocumentProto person2 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person2")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first2")
          .AddStringProperty("lastName", "last2")
          .AddStringProperty("emailAddress", "email2@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(2)
          .Build();

  DocumentProto email1 =
      DocumentBuilder()
          .SetKey("namespace", "email1")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 1")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(100)
          .Build();
  DocumentProto email2 =
      DocumentBuilder()
          .SetKey("namespace", "email2")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 2")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(99)
          .Build();
  DocumentProto email3 =
      DocumentBuilder()
          .SetKey("namespace", "email3")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 3")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(98)
          .Build();
  DocumentProto email4 =
      DocumentBuilder()
          .SetKey("namespace", "email4")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 4")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(97)
          .Build();

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email4).status(), ProtoIsOk());

  // Parent SearchSpec
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("firstName:first");
  search_spec.set_search_type(GetParam());

  // JoinSpec
  JoinSpecProto* join_spec = search_spec.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("personQualifiedId");
  join_spec->set_aggregation_scoring_strategy(
      JoinSpecProto::AggregationScoringStrategy::COUNT);
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::PREFIX);
  nested_search_spec->set_query("subject:test");
  nested_search_spec->set_search_type(GetParam());
  *nested_spec->mutable_scoring_spec() = GetDefaultScoringSpec();
  *nested_spec->mutable_result_spec() = ResultSpecProto::default_instance();

  // Parent ScoringSpec
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();

  // Parent ResultSpec with max_joined_children_per_parent_to_return = 0
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);
  result_spec.set_max_joined_children_per_parent_to_return(0);

  // - Use COUNT for aggregation scoring strategy.
  // - max_joined_children_per_parent_to_return = 0.
  // - (Default) use DESC as the ranking order.
  //
  // person2 should have the highest aggregated score (3) since email2, email3,
  // email4 are joined to it and the COUNT aggregated score is 3. However, no
  // child documents should be attached to person2 due to
  // max_joined_children_per_parent_to_return limitation in result_spec.
  // person1 should be the second (aggregated score = 1) with no attached child
  // documents.
  SearchResultProto::ResultProto expected_result_proto1;
  *expected_result_proto1.mutable_document() = person2;
  expected_result_proto1.set_score(3);

  SearchResultProto::ResultProto expected_result_google::protobuf;
  *expected_result_google::protobuf.mutable_document() = person1;
  expected_result_google::protobuf.set_score(1);

  SearchResultProto result1 =
      icing.Search(search_spec, scoring_spec, result_spec);
  uint64_t next_page_token = result1.next_page_token();
  EXPECT_THAT(next_page_token, Ne(kInvalidNextPageToken));
  EXPECT_THAT(result1.results(),
              ElementsAre(EqualsProto(expected_result_proto1)));

  SearchResultProto result2 = icing.GetNextPage(next_page_token);
  next_page_token = result2.next_page_token();
  EXPECT_THAT(next_page_token, Eq(kInvalidNextPageToken));
  EXPECT_THAT(result2.results(),
              ElementsAre(EqualsProto(expected_result_google::protobuf)));
}

TEST_P(IcingSearchEngineSearchTest, JoinSnippet) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Person")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("firstName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("lastName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emailAddress")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("personQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  DocumentProto person =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first")
          .AddStringProperty("lastName", "last")
          .AddStringProperty("emailAddress", "email@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(1)
          .Build();

  DocumentProto email =
      DocumentBuilder()
          .SetKey("namespace", "email")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(3)
          .Build();

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email).status(), ProtoIsOk());

  // Parent SearchSpec
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("firstName:first");
  search_spec.set_search_type(GetParam());

  // JoinSpec
  JoinSpecProto* join_spec = search_spec.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("personQualifiedId");
  join_spec->set_aggregation_scoring_strategy(
      JoinSpecProto::AggregationScoringStrategy::MAX);
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::PREFIX);
  nested_search_spec->set_query("subject:test");
  nested_search_spec->set_search_type(GetParam());
  // Child ResultSpec (with snippet)
  ResultSpecProto* nested_result_spec = nested_spec->mutable_result_spec();
  nested_result_spec->mutable_snippet_spec()->set_max_window_utf32_length(64);
  nested_result_spec->mutable_snippet_spec()->set_num_matches_per_property(1);
  nested_result_spec->mutable_snippet_spec()->set_num_to_snippet(1);
  *nested_spec->mutable_scoring_spec() = GetDefaultScoringSpec();

  // Parent ScoringSpec
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();

  // Parent ResultSpec (without snippet)
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);
  result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());

  SearchResultProto result =
      icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(result.status(), ProtoIsOk());
  EXPECT_THAT(result.next_page_token(), Eq(kInvalidNextPageToken));

  ASSERT_THAT(result.results(), SizeIs(1));
  // Check parent doc (person).
  const DocumentProto& result_parent_document = result.results(0).document();
  EXPECT_THAT(result_parent_document, EqualsProto(person));
  EXPECT_THAT(result.results(0).snippet().entries(), IsEmpty());

  // Check child doc (email).
  ASSERT_THAT(result.results(0).joined_results(), SizeIs(1));
  const DocumentProto& result_child_document =
      result.results(0).joined_results(0).document();
  const SnippetProto& result_child_snippet =
      result.results(0).joined_results(0).snippet();
  EXPECT_THAT(result_child_document, EqualsProto(email));
  ASSERT_THAT(result_child_snippet.entries(), SizeIs(1));
  EXPECT_THAT(result_child_snippet.entries(0).property_name(), Eq("subject"));
  std::string_view content = GetString(
      &result_child_document, result_child_snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_child_snippet.entries(0)),
              ElementsAre("test subject"));
  EXPECT_THAT(GetMatches(content, result_child_snippet.entries(0)),
              ElementsAre("test"));
}

TEST_P(IcingSearchEngineSearchTest, JoinProjection) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Person")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("firstName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("lastName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emailAddress")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("personQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  DocumentProto person =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first")
          .AddStringProperty("lastName", "last")
          .AddStringProperty("emailAddress", "email@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(1)
          .Build();

  DocumentProto email =
      DocumentBuilder()
          .SetKey("namespace", "email")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(3)
          .Build();

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email).status(), ProtoIsOk());

  // Parent SearchSpec
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("firstName:first");
  search_spec.set_search_type(GetParam());

  // JoinSpec
  JoinSpecProto* join_spec = search_spec.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("personQualifiedId");
  join_spec->set_aggregation_scoring_strategy(
      JoinSpecProto::AggregationScoringStrategy::MAX);
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::PREFIX);
  nested_search_spec->set_query("subject:test");
  nested_search_spec->set_search_type(GetParam());
  // Child ResultSpec (with projection)
  ResultSpecProto* nested_result_spec = nested_spec->mutable_result_spec();
  TypePropertyMask* type_property_mask =
      nested_result_spec->add_type_property_masks();
  type_property_mask->set_schema_type("Email");
  type_property_mask->add_paths("subject");
  *nested_spec->mutable_scoring_spec() = GetDefaultScoringSpec();

  // Parent ScoringSpec
  ScoringSpecProto scoring_spec = GetDefaultScoringSpec();

  // Parent ResultSpec (with projection)
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);
  result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());
  type_property_mask = result_spec.add_type_property_masks();
  type_property_mask->set_schema_type("Person");
  type_property_mask->add_paths("emailAddress");

  SearchResultProto result =
      icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(result.status(), ProtoIsOk());
  EXPECT_THAT(result.next_page_token(), Eq(kInvalidNextPageToken));

  ASSERT_THAT(result.results(), SizeIs(1));
  // Check parent doc (person): should contain only the "emailAddress" property.
  DocumentProto projected_person_document =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person")
          .SetSchema("Person")
          .AddStringProperty("emailAddress", "email@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(1)
          .Build();
  EXPECT_THAT(result.results().at(0).document(),
              EqualsProto(projected_person_document));

  // Check child doc (email): should contain only the "subject" property.
  ASSERT_THAT(result.results(0).joined_results(), SizeIs(1));
  DocumentProto projected_email_document =
      DocumentBuilder()
          .SetKey("namespace", "email")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(3)
          .Build();
  EXPECT_THAT(result.results(0).joined_results(0).document(),
              EqualsProto(projected_email_document));
}

TEST_F(IcingSearchEngineSearchTest, JoinWithAdvancedScoring) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Person")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("firstName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("lastName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emailAddress")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("personQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  const int32_t person1_doc_score = 10;
  const int32_t person2_doc_score = 25;
  const int32_t person3_doc_score = 123;
  const int32_t email1_doc_score = 10;
  const int32_t email2_doc_score = 15;
  const int32_t email3_doc_score = 40;

  // person1 has children email1 and email2.
  DocumentProto person1 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person1")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first1")
          .AddStringProperty("lastName", "last1")
          .AddStringProperty("emailAddress", "email1@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(person1_doc_score)
          .Build();
  // person2 has a single child email3
  DocumentProto person2 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person2")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first2")
          .AddStringProperty("lastName", "last2")
          .AddStringProperty("emailAddress", "email2@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(person2_doc_score)
          .Build();
  // person3 has no child.
  DocumentProto person3 =
      DocumentBuilder()
          .SetKey("pkg$db/namespace", "person3")
          .SetSchema("Person")
          .AddStringProperty("firstName", "first3")
          .AddStringProperty("lastName", "last3")
          .AddStringProperty("emailAddress", "email3@gmail.com")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(person3_doc_score)
          .Build();

  DocumentProto email1 =
      DocumentBuilder()
          .SetKey("namespace", "email1")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 1")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(email1_doc_score)
          .Build();
  DocumentProto email2 =
      DocumentBuilder()
          .SetKey("namespace", "email2")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 2")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(email2_doc_score)
          .Build();
  DocumentProto email3 =
      DocumentBuilder()
          .SetKey("namespace", "email3")
          .SetSchema("Email")
          .AddStringProperty("subject", "test subject 3")
          .AddStringProperty("personQualifiedId", "pkg$db/namespace#person2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .SetScore(email3_doc_score)
          .Build();

  // Set children scoring expression and their expected value.
  ScoringSpecProto child_scoring_spec = GetDefaultScoringSpec();
  child_scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::ADVANCED_SCORING_EXPRESSION);
  child_scoring_spec.set_advanced_scoring_expression(
      "this.documentScore() * 2 + 1");
  const int32_t exp_email1_score = email1_doc_score * 2 + 1;
  const int32_t exp_email2_score = email2_doc_score * 2 + 1;
  const int32_t exp_email3_score = email3_doc_score * 2 + 1;

  // Set parent scoring expression and their expected value.
  ScoringSpecProto parent_scoring_spec = GetDefaultScoringSpec();
  parent_scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::ADVANCED_SCORING_EXPRESSION);
  parent_scoring_spec.set_advanced_scoring_expression(
      "this.documentScore() * sum(this.childrenRankingSignals())");
  const int32_t exp_person1_score =
      person1_doc_score * (exp_email1_score + exp_email2_score);
  const int32_t exp_person2_score = person2_doc_score * exp_email3_score;
  const int32_t exp_person3_score = person3_doc_score * 0;

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(person3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(email3).status(), ProtoIsOk());

  // Parent SearchSpec
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("firstName:first");

  // JoinSpec
  JoinSpecProto* join_spec = search_spec.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("personQualifiedId");
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::PREFIX);
  nested_search_spec->set_query("subject:test");
  *nested_spec->mutable_scoring_spec() = child_scoring_spec;
  *nested_spec->mutable_result_spec() = ResultSpecProto::default_instance();

  // Parent ResultSpec
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);
  result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());

  SearchResultProto results =
      icing.Search(search_spec, parent_scoring_spec, result_spec);
  uint64_t next_page_token = results.next_page_token();
  EXPECT_THAT(next_page_token, Ne(kInvalidNextPageToken));
  ASSERT_THAT(results.results(), SizeIs(1));
  EXPECT_THAT(results.results(0).document().uri(), Eq("person2"));
  // exp_person2_score = 2025
  EXPECT_THAT(results.results(0).score(), Eq(exp_person2_score));

  results = icing.GetNextPage(next_page_token);
  next_page_token = results.next_page_token();
  EXPECT_THAT(next_page_token, Ne(kInvalidNextPageToken));
  ASSERT_THAT(results.results(), SizeIs(1));
  EXPECT_THAT(results.results(0).document().uri(), Eq("person1"));
  // exp_person1_score = 520
  EXPECT_THAT(results.results(0).score(), Eq(exp_person1_score));

  results = icing.GetNextPage(next_page_token);
  next_page_token = results.next_page_token();
  EXPECT_THAT(next_page_token, Eq(kInvalidNextPageToken));
  ASSERT_THAT(results.results(), SizeIs(1));
  EXPECT_THAT(results.results(0).document().uri(), Eq("person3"));
  // exp_person3_score = 0
  EXPECT_THAT(results.results(0).score(), Eq(exp_person3_score));
}

TEST_F(IcingSearchEngineSearchTest, NumericFilterAdvancedQuerySucceeds) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  // Create the schema and document store
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("transaction")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("price")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("cost")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  DocumentProto document_one = DocumentBuilder()
                                   .SetKey("namespace", "1")
                                   .SetSchema("transaction")
                                   .SetCreationTimestampMs(1)
                                   .AddInt64Property("price", 10)
                                   .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two = DocumentBuilder()
                                   .SetKey("namespace", "2")
                                   .SetSchema("transaction")
                                   .SetCreationTimestampMs(1)
                                   .AddInt64Property("price", 25)
                                   .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  DocumentProto document_three = DocumentBuilder()
                                     .SetKey("namespace", "3")
                                     .SetSchema("transaction")
                                     .SetCreationTimestampMs(1)
                                     .AddInt64Property("cost", 2)
                                     .Build();
  ASSERT_THAT(icing.Put(document_three).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_query("price < 20");
  search_spec.set_search_type(
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY);
  search_spec.add_enabled_features(std::string(kNumericSearchFeature));

  SearchResultProto results =
      icing.Search(search_spec, ScoringSpecProto::default_instance(),
                   ResultSpecProto::default_instance());
  ASSERT_THAT(results.results(), SizeIs(1));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document_one));

  search_spec.set_query("price == 25");
  results = icing.Search(search_spec, ScoringSpecProto::default_instance(),
                         ResultSpecProto::default_instance());
  ASSERT_THAT(results.results(), SizeIs(1));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document_two));

  search_spec.set_query("cost > 2");
  results = icing.Search(search_spec, ScoringSpecProto::default_instance(),
                         ResultSpecProto::default_instance());
  EXPECT_THAT(results.results(), IsEmpty());

  search_spec.set_query("cost >= 2");
  results = icing.Search(search_spec, ScoringSpecProto::default_instance(),
                         ResultSpecProto::default_instance());
  ASSERT_THAT(results.results(), SizeIs(1));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document_three));

  search_spec.set_query("price <= 25");
  results = icing.Search(search_spec, ScoringSpecProto::default_instance(),
                         ResultSpecProto::default_instance());
  ASSERT_THAT(results.results(), SizeIs(2));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document_two));
  EXPECT_THAT(results.results(1).document(), EqualsProto(document_one));
}

TEST_F(IcingSearchEngineSearchTest,
       NumericFilterAdvancedQueryWithPersistenceSucceeds) {
  IcingSearchEngineOptions icing_options = GetDefaultIcingOptions();

  {
    // Create the schema and document store
    SchemaProto schema =
        SchemaBuilder()
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType("transaction")
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("price")
                                     .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("cost")
                                     .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .Build();

    IcingSearchEngine icing(icing_options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
    // Schema will be persisted to disk when icing goes out of scope.
  }

  DocumentProto document_one = DocumentBuilder()
                                   .SetKey("namespace", "1")
                                   .SetSchema("transaction")
                                   .SetCreationTimestampMs(1)
                                   .AddInt64Property("price", 10)
                                   .Build();
  DocumentProto document_two = DocumentBuilder()
                                   .SetKey("namespace", "2")
                                   .SetSchema("transaction")
                                   .SetCreationTimestampMs(1)
                                   .AddInt64Property("price", 25)
                                   .AddInt64Property("cost", 2)
                                   .Build();
  {
    // Ensure that icing initializes the schema and section_manager
    // properly from the pre-existing file.
    IcingSearchEngine icing(icing_options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());
    // The index and document store will be persisted to disk when icing goes
    // out of scope.
  }

  {
    // Ensure that the index is brought back up without problems and we
    // can query for the content that we expect.
    IcingSearchEngine icing(icing_options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

    SearchSpecProto search_spec;
    search_spec.set_query("price < 20");
    search_spec.set_search_type(
        SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY);
    search_spec.add_enabled_features(std::string(kNumericSearchFeature));

    SearchResultProto results =
        icing.Search(search_spec, ScoringSpecProto::default_instance(),
                     ResultSpecProto::default_instance());
    ASSERT_THAT(results.results(), SizeIs(1));
    EXPECT_THAT(results.results(0).document(), EqualsProto(document_one));

    search_spec.set_query("price == 25");
    results = icing.Search(search_spec, ScoringSpecProto::default_instance(),
                           ResultSpecProto::default_instance());
    ASSERT_THAT(results.results(), SizeIs(1));
    EXPECT_THAT(results.results(0).document(), EqualsProto(document_two));

    search_spec.set_query("cost > 2");
    results = icing.Search(search_spec, ScoringSpecProto::default_instance(),
                           ResultSpecProto::default_instance());
    EXPECT_THAT(results.results(), IsEmpty());

    search_spec.set_query("cost >= 2");
    results = icing.Search(search_spec, ScoringSpecProto::default_instance(),
                           ResultSpecProto::default_instance());
    ASSERT_THAT(results.results(), SizeIs(1));
    EXPECT_THAT(results.results(0).document(), EqualsProto(document_two));

    search_spec.set_query("price <= 25");
    results = icing.Search(search_spec, ScoringSpecProto::default_instance(),
                           ResultSpecProto::default_instance());
    ASSERT_THAT(results.results(), SizeIs(2));
    EXPECT_THAT(results.results(0).document(), EqualsProto(document_two));
    EXPECT_THAT(results.results(1).document(), EqualsProto(document_one));
  }
}

TEST_F(IcingSearchEngineSearchTest, NumericFilterOldQueryFails) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  // Create the schema and document store
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("transaction")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("price")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("cost")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  DocumentProto document_one = DocumentBuilder()
                                   .SetKey("namespace", "1")
                                   .SetSchema("transaction")
                                   .SetCreationTimestampMs(1)
                                   .AddInt64Property("price", 10)
                                   .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two = DocumentBuilder()
                                   .SetKey("namespace", "2")
                                   .SetSchema("transaction")
                                   .SetCreationTimestampMs(1)
                                   .AddInt64Property("price", 25)
                                   .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  DocumentProto document_three = DocumentBuilder()
                                     .SetKey("namespace", "3")
                                     .SetSchema("transaction")
                                     .SetCreationTimestampMs(1)
                                     .AddInt64Property("cost", 2)
                                     .Build();
  ASSERT_THAT(icing.Put(document_three).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_query("price < 20");
  search_spec.set_search_type(SearchSpecProto::SearchType::ICING_RAW_QUERY);
  search_spec.add_enabled_features(std::string(kNumericSearchFeature));

  SearchResultProto results =
      icing.Search(search_spec, ScoringSpecProto::default_instance(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
}

TEST_F(IcingSearchEngineSearchTest, NumericFilterQueryStatsProtoTest) {
  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(5);

  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::make_unique<Filesystem>(),
                              std::make_unique<IcingFilesystem>(),
                              std::move(fake_clock), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  // Create the schema and document store
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("transaction")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("price")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("cost")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  DocumentProto document_one = DocumentBuilder()
                                   .SetKey("namespace", "1")
                                   .SetSchema("transaction")
                                   .SetCreationTimestampMs(1)
                                   .AddInt64Property("price", 10)
                                   .Build();
  ASSERT_THAT(icing.Put(document_one).status(), ProtoIsOk());

  DocumentProto document_two = DocumentBuilder()
                                   .SetKey("namespace", "2")
                                   .SetSchema("transaction")
                                   .SetCreationTimestampMs(2)
                                   .AddInt64Property("price", 25)
                                   .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  DocumentProto document_three = DocumentBuilder()
                                     .SetKey("namespace", "3")
                                     .SetSchema("transaction")
                                     .SetCreationTimestampMs(3)
                                     .AddInt64Property("cost", 2)
                                     .Build();
  ASSERT_THAT(icing.Put(document_three).status(), ProtoIsOk());

  DocumentProto document_four = DocumentBuilder()
                                    .SetKey("namespace", "3")
                                    .SetSchema("transaction")
                                    .SetCreationTimestampMs(4)
                                    .AddInt64Property("price", 15)
                                    .Build();
  ASSERT_THAT(icing.Put(document_four).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.add_namespace_filters("namespace");
  search_spec.add_schema_type_filters(document_one.schema());
  search_spec.set_query("price < 20");
  search_spec.add_enabled_features(std::string(kNumericSearchFeature));

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(5);

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);

  SearchResultProto results =
      icing.Search(search_spec, scoring_spec, result_spec);
  ASSERT_THAT(results.results(), SizeIs(2));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document_four));
  EXPECT_THAT(results.results(1).document(), EqualsProto(document_one));

  // Check the stats
  // TODO(b/305098009): deprecate search-related flat fields in query_stats.
  QueryStatsProto exp_stats;
  exp_stats.set_query_length(10);
  exp_stats.set_num_terms(0);
  exp_stats.set_num_namespaces_filtered(1);
  exp_stats.set_num_schema_types_filtered(1);
  exp_stats.set_ranking_strategy(
      ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);
  exp_stats.set_is_first_page(true);
  exp_stats.set_requested_page_size(5);
  exp_stats.set_num_results_returned_current_page(2);
  exp_stats.set_num_documents_scored(2);
  exp_stats.set_num_results_with_snippets(0);
  exp_stats.set_latency_ms(5);
  exp_stats.set_parse_query_latency_ms(5);
  exp_stats.set_scoring_latency_ms(5);
  exp_stats.set_ranking_latency_ms(5);
  exp_stats.set_document_retrieval_latency_ms(5);
  exp_stats.set_lock_acquisition_latency_ms(5);
  exp_stats.set_num_joined_results_returned_current_page(0);
  exp_stats.set_lite_index_hit_buffer_byte_size(0);
  exp_stats.set_lite_index_hit_buffer_unsorted_byte_size(0);

  QueryStatsProto::SearchStats* exp_parent_search_stats =
      exp_stats.mutable_parent_search_stats();
  exp_parent_search_stats->set_query_length(10);
  exp_parent_search_stats->set_num_terms(0);
  exp_parent_search_stats->set_num_namespaces_filtered(1);
  exp_parent_search_stats->set_num_schema_types_filtered(1);
  exp_parent_search_stats->set_ranking_strategy(
      ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);
  exp_parent_search_stats->set_is_numeric_query(true);
  exp_parent_search_stats->set_num_documents_scored(2);
  exp_parent_search_stats->set_parse_query_latency_ms(5);
  exp_parent_search_stats->set_scoring_latency_ms(5);
  exp_parent_search_stats->set_num_fetched_hits_lite_index(0);
  exp_parent_search_stats->set_num_fetched_hits_main_index(0);
  // Since we will inspect 1 bucket from "price" in integer index and it
  // contains 3 hits, we will fetch 3 hits (but filter out one of them).
  exp_parent_search_stats->set_num_fetched_hits_integer_index(3);
  exp_parent_search_stats->set_query_processor_lexer_extract_token_latency_ms(
      5);
  exp_parent_search_stats->set_query_processor_parser_consume_query_latency_ms(
      5);
  exp_parent_search_stats->set_query_processor_query_visitor_latency_ms(5);

  EXPECT_THAT(results.query_stats(), EqualsProto(exp_stats));
}

TEST_P(IcingSearchEngineSearchTest, BarisNormalizationTest) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Person").AddProperty(
              PropertyConfigBuilder()
                  .SetName("name")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "uri")
                               .SetSchema("Person")
                               .SetCreationTimestampMs(1)
                               .AddStringProperty("name", "Barış")
                               .Build();
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
  DocumentProto document_two = DocumentBuilder()
                                   .SetKey("namespace", "uri2")
                                   .SetSchema("Person")
                                   .SetCreationTimestampMs(1)
                                   .AddStringProperty("name", "ıbar")
                                   .Build();
  ASSERT_THAT(icing.Put(document_two).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TERM_MATCH_PREFIX);
  search_spec.set_search_type(GetParam());

  ScoringSpecProto scoring_spec;
  ResultSpecProto result_spec;

  SearchResultProto exp_results;
  exp_results.mutable_status()->set_code(StatusProto::OK);
  *exp_results.add_results()->mutable_document() = document;

  search_spec.set_query("barış");
  SearchResultProto results =
      icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(results, EqualsSearchResultIgnoreStatsAndScores(exp_results));

  search_spec.set_query("barıs");
  results = icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(results, EqualsSearchResultIgnoreStatsAndScores(exp_results));

  search_spec.set_query("baris");
  results = icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(results, EqualsSearchResultIgnoreStatsAndScores(exp_results));

  SearchResultProto exp_results2;
  exp_results2.mutable_status()->set_code(StatusProto::OK);
  *exp_results2.add_results()->mutable_document() = document_two;
  search_spec.set_query("ı");
  results = icing.Search(search_spec, scoring_spec, result_spec);
  EXPECT_THAT(results, EqualsSearchResultIgnoreStatsAndScores(exp_results2));
}

TEST_P(IcingSearchEngineSearchTest, LatinSnippetTest) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

  constexpr std::string_view kLatin = "test ḞÖÖḸĬŞĤ test";
  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "uri1")
                               .SetSchema("Message")
                               .AddStringProperty("body", kLatin)
                               .Build();
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_query("foo");
  search_spec.set_term_match_type(TERM_MATCH_PREFIX);
  search_spec.set_search_type(GetParam());

  ResultSpecProto result_spec;
  result_spec.mutable_snippet_spec()->set_num_to_snippet(
      std::numeric_limits<int>::max());
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(
      std::numeric_limits<int>::max());

  // Search and make sure that we got a single successful result
  SearchResultProto search_results = icing.Search(
      search_spec, ScoringSpecProto::default_instance(), result_spec);
  ASSERT_THAT(search_results.status(), ProtoIsOk());
  ASSERT_THAT(search_results.results(), SizeIs(1));
  const SearchResultProto::ResultProto* result = &search_results.results(0);
  EXPECT_THAT(result->document().uri(), Eq("uri1"));

  // Ensure that one and only one property was matched and it was "body"
  ASSERT_THAT(result->snippet().entries(), SizeIs(1));
  const SnippetProto::EntryProto* entry = &result->snippet().entries(0);
  EXPECT_THAT(entry->property_name(), Eq("body"));

  // Ensure that there is one and only one match within "body"
  ASSERT_THAT(entry->snippet_matches(), SizeIs(1));

  // Check that the match is "ḞÖÖḸĬŞĤ".
  const SnippetMatchProto& match_proto = entry->snippet_matches(0);
  std::string_view match =
      kLatin.substr(match_proto.exact_match_byte_position(),
                    match_proto.submatch_byte_length());
  ASSERT_THAT(match, Eq("ḞÖÖ"));
}

TEST_P(IcingSearchEngineSearchTest,
       DocumentStoreNamespaceIdFingerprintCompatible) {
  DocumentProto document1 = CreateMessageDocument("namespace", "uri1");
  DocumentProto document2 = CreateMessageDocument("namespace", "uri2");
  DocumentProto document3 = CreateMessageDocument("namespace", "uri3");

  // Initialize with some documents with document_store_namespace_id_fingerprint
  // being false.
  {
    IcingSearchEngineOptions options = GetDefaultIcingOptions();
    options.set_document_store_namespace_id_fingerprint(false);
    IcingSearchEngine icing(options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(CreateMessageSchema()).status(), ProtoIsOk());

    // Creates and inserts 3 documents
    ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  }

  // Reinitializate with document_store_namespace_id_fingerprint being true,
  // and test that we are still able to read/query docs.
  {
    IcingSearchEngineOptions options = GetDefaultIcingOptions();
    options.set_document_store_namespace_id_fingerprint(true);
    IcingSearchEngine icing(options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

    ASSERT_THAT(
        icing.Get("namespace", "uri1", GetResultSpecProto::default_instance())
            .status(),
        ProtoIsOk());
    ASSERT_THAT(
        icing.Get("namespace", "uri2", GetResultSpecProto::default_instance())
            .status(),
        ProtoIsOk());
    ASSERT_THAT(
        icing.Get("namespace", "uri3", GetResultSpecProto::default_instance())
            .status(),
        ProtoIsOk());

    SearchSpecProto search_spec;
    search_spec.set_term_match_type(TermMatchType::PREFIX);
    search_spec.set_query("message");
    search_spec.set_search_type(GetParam());
    SearchResultProto results =
        icing.Search(search_spec, ScoringSpecProto::default_instance(),
                     ResultSpecProto::default_instance());
    ASSERT_THAT(results.results(), SizeIs(3));
    EXPECT_THAT(results.results(0).document(), EqualsProto(document3));
    EXPECT_THAT(results.results(1).document(), EqualsProto(document2));
    EXPECT_THAT(results.results(2).document(), EqualsProto(document1));
  }

  // Reinitializate with document_store_namespace_id_fingerprint being false,
  // and test that we are still able to read/query docs.
  {
    IcingSearchEngineOptions options = GetDefaultIcingOptions();
    options.set_document_store_namespace_id_fingerprint(false);
    IcingSearchEngine icing(options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

    ASSERT_THAT(
        icing.Get("namespace", "uri1", GetResultSpecProto::default_instance())
            .status(),
        ProtoIsOk());
    ASSERT_THAT(
        icing.Get("namespace", "uri2", GetResultSpecProto::default_instance())
            .status(),
        ProtoIsOk());
    ASSERT_THAT(
        icing.Get("namespace", "uri3", GetResultSpecProto::default_instance())
            .status(),
        ProtoIsOk());

    SearchSpecProto search_spec;
    search_spec.set_term_match_type(TermMatchType::PREFIX);
    search_spec.set_query("message");
    search_spec.set_search_type(GetParam());
    SearchResultProto results =
        icing.Search(search_spec, ScoringSpecProto::default_instance(),
                     ResultSpecProto::default_instance());
    ASSERT_THAT(results.results(), SizeIs(3));
    EXPECT_THAT(results.results(0).document(), EqualsProto(document3));
    EXPECT_THAT(results.results(1).document(), EqualsProto(document2));
    EXPECT_THAT(results.results(2).document(), EqualsProto(document1));
  }
}

TEST_P(IcingSearchEngineSearchTest, HasPropertyQuery) {
  if (GetParam() !=
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY) {
    GTEST_SKIP()
        << "The hasProperty() function is only supported in advanced query.";
  }
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Value")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("timestamp")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("score")
                                        .SetDataType(TYPE_DOUBLE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Create a document with every property.
  DocumentProto document0 = DocumentBuilder()
                                .SetKey("icing", "uri0")
                                .SetSchema("Value")
                                .SetCreationTimestampMs(1)
                                .AddStringProperty("body", "foo")
                                .AddInt64Property("timestamp", 123)
                                .AddDoubleProperty("score", 456.789)
                                .Build();
  // Create a document with missing body.
  DocumentProto document1 = DocumentBuilder()
                                .SetKey("icing", "uri1")
                                .SetSchema("Value")
                                .SetCreationTimestampMs(1)
                                .AddInt64Property("timestamp", 123)
                                .AddDoubleProperty("score", 456.789)
                                .Build();
  // Create a document with missing timestamp.
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("icing", "uri2")
                                .SetSchema("Value")
                                .SetCreationTimestampMs(1)
                                .AddStringProperty("body", "foo")
                                .AddDoubleProperty("score", 456.789)
                                .Build();

  IcingSearchEngineOptions options = GetDefaultIcingOptions();
  options.set_build_property_existence_metadata_hits(true);
  IcingSearchEngine icing(options, GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document0).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // Get all documents that have "body".
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_search_type(GetParam());
  search_spec.add_enabled_features(std::string(kHasPropertyFunctionFeature));
  search_spec.add_enabled_features(
      std::string(kListFilterQueryLanguageFeature));
  search_spec.set_query("hasProperty(\"body\")");
  SearchResultProto results = icing.Search(search_spec, GetDefaultScoringSpec(),
                                           ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(2));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document2));
  EXPECT_THAT(results.results(1).document(), EqualsProto(document0));

  // Get all documents that have "timestamp".
  search_spec.set_query("hasProperty(\"timestamp\")");
  results = icing.Search(search_spec, GetDefaultScoringSpec(),
                         ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(2));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document1));
  EXPECT_THAT(results.results(1).document(), EqualsProto(document0));

  // Get all documents that have "score".
  search_spec.set_query("hasProperty(\"score\")");
  results = icing.Search(search_spec, GetDefaultScoringSpec(),
                         ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(3));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document2));
  EXPECT_THAT(results.results(1).document(), EqualsProto(document1));
  EXPECT_THAT(results.results(2).document(), EqualsProto(document0));
}

TEST_P(IcingSearchEngineSearchTest,
       HasPropertyQueryDoesNotWorkWithoutMetadataHits) {
  if (GetParam() !=
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY) {
    GTEST_SKIP()
        << "The hasProperty() function is only supported in advanced query.";
  }
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Value")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("timestamp")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("score")
                                        .SetDataType(TYPE_DOUBLE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Create a document with every property.
  DocumentProto document0 = DocumentBuilder()
                                .SetKey("icing", "uri0")
                                .SetSchema("Value")
                                .SetCreationTimestampMs(1)
                                .AddStringProperty("body", "foo")
                                .AddInt64Property("timestamp", 123)
                                .AddDoubleProperty("score", 456.789)
                                .Build();
  // Create a document with missing body.
  DocumentProto document1 = DocumentBuilder()
                                .SetKey("icing", "uri1")
                                .SetSchema("Value")
                                .SetCreationTimestampMs(1)
                                .AddInt64Property("timestamp", 123)
                                .AddDoubleProperty("score", 456.789)
                                .Build();
  // Create a document with missing timestamp.
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("icing", "uri2")
                                .SetSchema("Value")
                                .SetCreationTimestampMs(1)
                                .AddStringProperty("body", "foo")
                                .AddDoubleProperty("score", 456.789)
                                .Build();

  IcingSearchEngineOptions options = GetDefaultIcingOptions();
  options.set_build_property_existence_metadata_hits(false);
  IcingSearchEngine icing(options, GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document0).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // Check that none of the following hasProperty queries can return any
  // results.
  //
  // Get all documents that have "body".
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_search_type(GetParam());
  search_spec.add_enabled_features(std::string(kHasPropertyFunctionFeature));
  search_spec.add_enabled_features(
      std::string(kListFilterQueryLanguageFeature));
  search_spec.set_query("hasProperty(\"body\")");
  SearchResultProto results = icing.Search(search_spec, GetDefaultScoringSpec(),
                                           ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), IsEmpty());

  // Get all documents that have "timestamp".
  search_spec.set_query("hasProperty(\"timestamp\")");
  results = icing.Search(search_spec, GetDefaultScoringSpec(),
                         ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), IsEmpty());

  // Get all documents that have "score".
  search_spec.set_query("hasProperty(\"score\")");
  results = icing.Search(search_spec, GetDefaultScoringSpec(),
                         ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), IsEmpty());
}

TEST_P(IcingSearchEngineSearchTest, HasPropertyQueryNestedDocument) {
  if (GetParam() !=
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY) {
    GTEST_SKIP()
        << "The hasProperty() function is only supported in advanced query.";
  }
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Value")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("timestamp")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("score")
                                        .SetDataType(TYPE_DOUBLE)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TreeNode")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("name")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(
                           PropertyConfigBuilder()
                               .SetName("value")
                               .SetDataTypeDocument(
                                   "Value", /*index_nested_properties=*/true)
                               .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Create a complex nested root_document with the following property paths.
  // - name
  // - value
  // - value.body
  // - value.score
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "uri")
          .SetSchema("TreeNode")
          .SetCreationTimestampMs(1)
          .AddStringProperty("name", "root")
          .AddDocumentProperty("value", DocumentBuilder()
                                            .SetKey("icing", "uri")
                                            .SetSchema("Value")
                                            .AddStringProperty("body", "foo")
                                            .AddDoubleProperty("score", 456.789)
                                            .Build())
          .Build();

  IcingSearchEngineOptions options = GetDefaultIcingOptions();
  options.set_build_property_existence_metadata_hits(true);
  IcingSearchEngine icing(options, GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  // Check that the document can be found by `hasProperty("name")`.
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_search_type(GetParam());
  search_spec.add_enabled_features(std::string(kHasPropertyFunctionFeature));
  search_spec.add_enabled_features(
      std::string(kListFilterQueryLanguageFeature));
  search_spec.set_query("hasProperty(\"name\")");
  SearchResultProto results = icing.Search(search_spec, GetDefaultScoringSpec(),
                                           ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document));

  // Check that the document can be found by `hasProperty("value")`.
  search_spec.set_query("hasProperty(\"value\")");
  results = icing.Search(search_spec, GetDefaultScoringSpec(),
                         ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document));

  // Check that the document can be found by `hasProperty("value.body")`.
  search_spec.set_query("hasProperty(\"value.body\")");
  results = icing.Search(search_spec, GetDefaultScoringSpec(),
                         ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document));

  // Check that the document can be found by `hasProperty("value.score")`.
  search_spec.set_query("hasProperty(\"value.score\")");
  results = icing.Search(search_spec, GetDefaultScoringSpec(),
                         ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), SizeIs(1));
  EXPECT_THAT(results.results(0).document(), EqualsProto(document));

  // Check that the document can NOT be found by `hasProperty("body")`.
  search_spec.set_query("hasProperty(\"body\")");
  results = icing.Search(search_spec, GetDefaultScoringSpec(),
                         ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), IsEmpty());

  // Check that the document can NOT be found by `hasProperty("score")`.
  search_spec.set_query("hasProperty(\"score\")");
  results = icing.Search(search_spec, GetDefaultScoringSpec(),
                         ResultSpecProto::default_instance());
  EXPECT_THAT(results.status(), ProtoIsOk());
  EXPECT_THAT(results.results(), IsEmpty());
}

INSTANTIATE_TEST_SUITE_P(
    IcingSearchEngineSearchTest, IcingSearchEngineSearchTest,
    testing::Values(
        SearchSpecProto::SearchType::ICING_RAW_QUERY,
        SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY));

}  // namespace
}  // namespace lib
}  // namespace icing
