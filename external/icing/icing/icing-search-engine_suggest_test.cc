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
using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
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

// This test is meant to cover all tests relating to IcingSearchEngine::Search
// and IcingSearchEngine::SearchSuggestions.
class IcingSearchEngineSuggestTest : public testing::Test {
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

IcingSearchEngineOptions GetDefaultIcingOptions() {
  IcingSearchEngineOptions icing_options;
  icing_options.set_base_dir(GetTestBaseDir());
  return icing_options;
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

TEST_F(IcingSearchEngineSuggestTest, SearchSuggestionsTest) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // Creates and inserts 6 documents, and index 6 termSix, 5 termFive, 4
  // termFour, 3 termThree, 2 termTwo and one termOne.
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty(
              "subject", "termOne termTwo termThree termFour termFive termSix")
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject",
                             "termTwo termThree termFour termFive termSix")
          .Build();
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri3")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "termThree termFour termFive termSix")
          .Build();
  DocumentProto document4 =
      DocumentBuilder()
          .SetKey("namespace", "uri4")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "termFour termFive termSix")
          .Build();
  DocumentProto document5 =
      DocumentBuilder()
          .SetKey("namespace", "uri5")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "termFive termSix")
          .Build();
  DocumentProto document6 = DocumentBuilder()
                                .SetKey("namespace", "uri6")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "termSix")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document4).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document5).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document6).status(), ProtoIsOk());

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("t");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  // Query all suggestions, and they will be ranked.
  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions().at(0).query(), "termsix");
  ASSERT_THAT(response.suggestions().at(1).query(), "termfive");
  ASSERT_THAT(response.suggestions().at(2).query(), "termfour");
  ASSERT_THAT(response.suggestions().at(3).query(), "termthree");
  ASSERT_THAT(response.suggestions().at(4).query(), "termtwo");
  ASSERT_THAT(response.suggestions().at(5).query(), "termone");

  // Query first three suggestions, and they will be ranked.
  suggestion_spec.set_num_to_return(3);
  response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions().at(0).query(), "termsix");
  ASSERT_THAT(response.suggestions().at(1).query(), "termfive");
  ASSERT_THAT(response.suggestions().at(2).query(), "termfour");
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_ShouldReturnInOneNamespace) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "foo fool")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace2", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionFoo;
  suggestionFoo.set_query("foo");
  SuggestionResponse::Suggestion suggestionFool;
  suggestionFool.set_query("fool");

  // namespace1 has 2 results.
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.add_namespace_filters("namespace1");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFoo),
                                   EqualsProto(suggestionFool)));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_ShouldReturnInMultipleNamespace) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fo")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace2", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "foo")
                                .Build();
  DocumentProto document3 = DocumentBuilder()
                                .SetKey("namespace3", "uri3")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionFoo;
  suggestionFoo.set_query("foo");
  SuggestionResponse::Suggestion suggestionFool;
  suggestionFool.set_query("fool");

  // namespace2 and namespace3 has 2 results.
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.add_namespace_filters("namespace2");
  suggestion_spec.add_namespace_filters("namespace3");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFoo),
                                   EqualsProto(suggestionFool)));
}

TEST_F(IcingSearchEngineSuggestTest, SearchSuggestionsTest_NamespaceNotFound) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fo")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace2", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "foo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // Search for non-exist namespace3
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.add_namespace_filters("namespace3");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  EXPECT_THAT(response.status().code(), Eq(StatusProto::OK));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_OtherNamespaceDontContributeToHitCount) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  // Index 4 documents,
  // namespace1 has 2 hit2 for term one
  // namespace2 has 2 hit2 for term two and 1 hit for term one.
  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "termone")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace1", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "termone")
                                .Build();
  DocumentProto document3 = DocumentBuilder()
                                .SetKey("namespace2", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "termone termtwo")
                                .Build();
  DocumentProto document4 = DocumentBuilder()
                                .SetKey("namespace2", "uri3")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "termtwo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document4).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionTermOne;
  suggestionTermOne.set_query("termone");
  SuggestionResponse::Suggestion suggestionTermTwo;
  suggestionTermTwo.set_query("termtwo");

  // only search suggestion for namespace2. The correctly order should be
  // {"termtwo", "termone"}. If we're not filtering out namespace1 when
  // calculating our score, then it will be {"termone", "termtwo"}.
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("t");
  suggestion_spec.add_namespace_filters("namespace2");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              ElementsAre(EqualsProto(suggestionTermTwo),
                          EqualsProto(suggestionTermOne)));
}

TEST_F(IcingSearchEngineSuggestTest, SearchSuggestionsTest_DeletionTest) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace2", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionFool;
  suggestionFool.set_query("fool");

  // namespace1 has this suggestion
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.add_namespace_filters("namespace1");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFool)));

  // namespace2 has this suggestion
  suggestion_spec.clear_namespace_filters();
  suggestion_spec.add_namespace_filters("namespace2");
  response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFool)));

  // delete document from namespace 1
  EXPECT_THAT(icing.Delete("namespace1", "uri1").status(), ProtoIsOk());

  // Now namespace1 will return empty
  suggestion_spec.clear_namespace_filters();
  suggestion_spec.add_namespace_filters("namespace1");
  response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(), IsEmpty());

  // namespace2 still has this suggestion, so we can prove the reason of
  // namespace 1 cannot find it is we filter it out, not it doesn't exist.
  suggestion_spec.add_namespace_filters("namespace2");
  response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFool)));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_ShouldReturnInOneDocument) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace1", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "foo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionFool;
  suggestionFool.set_query("fool");
  SuggestionResponse::Suggestion suggestionFoo;
  suggestionFoo.set_query("foo");

  // Only search in namespace1,uri1
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);
  NamespaceDocumentUriGroup* namespace1_uri1 =
      suggestion_spec.add_document_uri_filters();
  namespace1_uri1->set_namespace_("namespace1");
  namespace1_uri1->add_document_uris("uri1");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFool)));

  // Only search in namespace1,uri2
  suggestion_spec.clear_document_uri_filters();
  NamespaceDocumentUriGroup* namespace1_uri2 =
      suggestion_spec.add_document_uri_filters();
  namespace1_uri2->set_namespace_("namespace1");
  namespace1_uri2->add_document_uris("uri2");

  response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFoo)));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_ShouldReturnInMultipleDocument) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace1", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "foo")
                                .Build();
  DocumentProto document3 = DocumentBuilder()
                                .SetKey("namespace1", "uri3")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionFool;
  suggestionFool.set_query("fool");
  SuggestionResponse::Suggestion suggestionFoo;
  suggestionFoo.set_query("foo");

  // Only search document in namespace1,uri1 and namespace2,uri2
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);
  NamespaceDocumentUriGroup* namespace1_uri1_uri2 =
      suggestion_spec.add_document_uri_filters();
  namespace1_uri1_uri2->set_namespace_("namespace1");
  namespace1_uri1_uri2->add_document_uris("uri1");
  namespace1_uri1_uri2->add_document_uris("uri2");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFool),
                                   EqualsProto(suggestionFoo)));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_ShouldReturnInDesiredDocumentAndNamespace) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace2", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "foo")
                                .Build();
  DocumentProto document3 = DocumentBuilder()
                                .SetKey("namespace3", "uri3")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionFool;
  suggestionFool.set_query("fool");
  SuggestionResponse::Suggestion suggestionFoo;
  suggestionFoo.set_query("foo");

  // Only search document in namespace1,uri1 and all documents under namespace2
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);
  suggestion_spec.add_namespace_filters("namespace1");
  suggestion_spec.add_namespace_filters("namespace2");
  NamespaceDocumentUriGroup* namespace1_uri1 =
      suggestion_spec.add_document_uri_filters();
  namespace1_uri1->set_namespace_("namespace1");
  namespace1_uri1->add_document_uris("uri1");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFool),
                                   EqualsProto(suggestionFoo)));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_DocumentIdDoesntExist) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace2", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "foo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // Search for a non-exist document id : namespace3,uri3
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);
  suggestion_spec.add_namespace_filters("namespace3");
  NamespaceDocumentUriGroup* namespace3_uri3 =
      suggestion_spec.add_document_uri_filters();
  namespace3_uri3->set_namespace_("namespace3");
  namespace3_uri3->add_document_uris("uri3");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(), IsEmpty());
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_DocumentIdFilterDoesntMatchNamespaceFilter) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace2", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "foo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  // Search for the document namespace1,uri1 with namespace filter in
  // namespace2.
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);
  NamespaceDocumentUriGroup* namespace1_uri1 =
      suggestion_spec.add_document_uri_filters();
  namespace1_uri1->set_namespace_("namespace1");
  namespace1_uri1->add_document_uris("uri1");
  suggestion_spec.add_namespace_filters("namespace2");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  EXPECT_THAT(response.status().code(), Eq(StatusProto::INVALID_ARGUMENT));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_EmptyDocumentIdInNamespace) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());

  // Give empty document uris in namespace 1
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);
  NamespaceDocumentUriGroup* namespace1_uri1 =
      suggestion_spec.add_document_uri_filters();
  namespace1_uri1->set_namespace_("namespace1");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  EXPECT_THAT(response.status().code(), Eq(StatusProto::INVALID_ARGUMENT));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_ShouldReturnInDesiredSchemaType) {
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
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "fool")
          .AddDocumentProperty("sender", DocumentBuilder()
                                             .SetKey("namespace", "uri1-sender")
                                             .SetSchema("Person")
                                             .AddStringProperty("name", "foo")
                                             .Build())
          .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace1", "uri2")
                                .SetSchema("Message")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("body", "fo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionFool;
  suggestionFool.set_query("fool");
  SuggestionResponse::Suggestion suggestionFoo;
  suggestionFoo.set_query("foo");

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);
  suggestion_spec.add_schema_type_filters("Email");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFoo),
                                   EqualsProto(suggestionFool)));
}

TEST_F(IcingSearchEngineSuggestTest, SearchSuggestionsTest_SchemaTypeNotFound) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Message")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("body", "fo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);
  suggestion_spec.add_schema_type_filters("Email");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(), IsEmpty());
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_ShouldReturnInDesiredProperty) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "fool")
          .AddDocumentProperty("sender",
                               DocumentBuilder()
                                   .SetKey("namespace", "uri1-sender")
                                   .SetSchema("Person")
                                   .AddStringProperty("name", "foo")
                                   .AddStringProperty("emailAddress", "fo")
                                   .Build())
          .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionFool;
  suggestionFool.set_query("fool");
  SuggestionResponse::Suggestion suggestionFoo;
  suggestionFoo.set_query("foo");

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  // Only search in subject.
  TypePropertyMask* mask = suggestion_spec.add_type_property_filters();
  mask->set_schema_type("Email");
  mask->add_paths("subject");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFool)));

  // Search in subject and sender.name
  suggestion_spec.clear_type_property_filters();
  mask = suggestion_spec.add_type_property_filters();
  mask->set_schema_type("Email");
  mask->add_paths("subject");
  mask->add_paths("sender.name");

  response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFoo),
                                   EqualsProto(suggestionFool)));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_NestedPropertyReturnNothing) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "fool")
          .AddDocumentProperty("sender", DocumentBuilder()
                                             .SetKey("namespace", "uri1-sender")
                                             .SetSchema("Person")
                                             .AddStringProperty("name", "foo")
                                             .Build())
          .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  // Only search in Person.name.
  suggestion_spec.add_schema_type_filters("Person");
  TypePropertyMask* mask = suggestion_spec.add_type_property_filters();
  mask->set_schema_type("Person");
  mask->add_paths("name");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(), IsEmpty());
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_PropertyFilterAndSchemaFilter) {
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
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "fool")
          .AddDocumentProperty("sender", DocumentBuilder()
                                             .SetKey("namespace", "uri1-sender")
                                             .SetSchema("Person")
                                             .AddStringProperty("name", "foo")
                                             .Build())
          .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace1", "uri2")
                                .SetSchema("Message")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("body", "fo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionFoo;
  suggestionFoo.set_query("foo");
  SuggestionResponse::Suggestion suggestionFo;
  suggestionFo.set_query("fo");

  // Search in sender.name of Email and everything in Message.
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);
  suggestion_spec.add_schema_type_filters("Email");
  suggestion_spec.add_schema_type_filters("Message");
  TypePropertyMask* mask1 = suggestion_spec.add_type_property_filters();
  mask1->set_schema_type("Email");
  mask1->add_paths("sender.name");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionFoo),
                                   EqualsProto(suggestionFo)));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_PropertyFilterNotMatchSchemaFilter) {
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

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Message")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("body", "fo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());

  // Search in sender.name of Email but schema type is Message.
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);
  suggestion_spec.add_schema_type_filters("Message");
  TypePropertyMask* mask1 = suggestion_spec.add_type_property_filters();
  mask1->set_schema_type("Email");
  mask1->add_paths("sender.name");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  EXPECT_THAT(response.status().code(), Eq(StatusProto::INVALID_ARGUMENT));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_OrderByTermFrequency) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Message")
          .SetCreationTimestampMs(10)
          .AddStringProperty(
              "body", "termthree termthree termthree termtwo termtwo termone")
          .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());

  // Search in sender.name of Email but schema type is Message.
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("t");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::EXACT_ONLY);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::TERM_FREQUENCY);

  SuggestionResponse::Suggestion suggestionTermOne;
  suggestionTermOne.set_query("termone");
  SuggestionResponse::Suggestion suggestionTermTwo;
  suggestionTermTwo.set_query("termtwo");
  SuggestionResponse::Suggestion suggestionTermThree;
  suggestionTermThree.set_query("termthree");

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              ElementsAre(EqualsProto(suggestionTermThree),
                          EqualsProto(suggestionTermTwo),
                          EqualsProto(suggestionTermOne)));
}

TEST_F(IcingSearchEngineSuggestTest, SearchSuggestionsTest_ExpiredTest) {
  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(100)
                                .SetTtlMs(500)
                                .AddStringProperty("subject", "fool")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace2", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(100)
                                .SetTtlMs(1000)
                                .AddStringProperty("subject", "fool")
                                .Build();
  {
    auto fake_clock = std::make_unique<FakeClock>();
    fake_clock->SetSystemTimeMilliseconds(400);

    TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                                std::make_unique<Filesystem>(),
                                std::make_unique<IcingFilesystem>(),
                                std::move(fake_clock), GetTestJniCache());
    EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
                ProtoIsOk());

    ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

    SuggestionResponse::Suggestion suggestionFool;
    suggestionFool.set_query("fool");

    // namespace1 has this suggestion
    SuggestionSpecProto suggestion_spec;
    suggestion_spec.set_prefix("f");
    suggestion_spec.add_namespace_filters("namespace1");
    suggestion_spec.set_num_to_return(10);
    suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
        TermMatchType::PREFIX);
    suggestion_spec.mutable_scoring_spec()->set_rank_by(
        SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

    SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
    ASSERT_THAT(response.status(), ProtoIsOk());
    ASSERT_THAT(response.suggestions(),
                UnorderedElementsAre(EqualsProto(suggestionFool)));

    // namespace2 has this suggestion
    suggestion_spec.clear_namespace_filters();
    suggestion_spec.add_namespace_filters("namespace2");
    response = icing.SearchSuggestions(suggestion_spec);
    ASSERT_THAT(response.status(), ProtoIsOk());
    ASSERT_THAT(response.suggestions(),
                UnorderedElementsAre(EqualsProto(suggestionFool)));
  }
  // We reinitialize here so we can feed in a fake clock this time
  {
    // Time needs to be past document1 creation time (100) + ttl (500) for it
    // to count as "expired". document2 is not expired since its ttl is 1000.
    auto fake_clock = std::make_unique<FakeClock>();
    fake_clock->SetSystemTimeMilliseconds(800);

    TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                                std::make_unique<Filesystem>(),
                                std::make_unique<IcingFilesystem>(),
                                std::move(fake_clock), GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

    SuggestionSpecProto suggestion_spec;
    suggestion_spec.set_prefix("f");
    suggestion_spec.add_namespace_filters("namespace1");
    suggestion_spec.set_num_to_return(10);
    suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
        TermMatchType::PREFIX);
    suggestion_spec.mutable_scoring_spec()->set_rank_by(
        SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

    // Now namespace1 will return empty
    suggestion_spec.clear_namespace_filters();
    suggestion_spec.add_namespace_filters("namespace1");
    SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
    ASSERT_THAT(response.status(), ProtoIsOk());
    ASSERT_THAT(response.suggestions(), IsEmpty());

    // namespace2 still has this suggestion
    SuggestionResponse::Suggestion suggestionFool;
    suggestionFool.set_query("fool");

    suggestion_spec.add_namespace_filters("namespace2");
    response = icing.SearchSuggestions(suggestion_spec);
    ASSERT_THAT(response.status(), ProtoIsOk());
    ASSERT_THAT(response.suggestions(),
                UnorderedElementsAre(EqualsProto(suggestionFool)));
  }
}

TEST_F(IcingSearchEngineSuggestTest, SearchSuggestionsTest_emptyPrefix) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  ASSERT_THAT(icing.SearchSuggestions(suggestion_spec).status(),
              ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_NonPositiveNumToReturn) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("prefix");
  suggestion_spec.set_num_to_return(0);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  ASSERT_THAT(icing.SearchSuggestions(suggestion_spec).status(),
              ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
}

TEST_F(IcingSearchEngineSuggestTest, SearchSuggestionsTest_MultipleTerms_And) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "bar fo")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "foo")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionBarFo;
  suggestionBarFo.set_query("bar fo");

  // Search "bar AND f" only document 1 should match the search.
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("bar f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionBarFo)));
}

TEST_F(IcingSearchEngineSuggestTest, SearchSuggestionsTest_MultipleTerms_Or) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace", "uri1")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "bar fo")
                                .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace", "uri2")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "cat foo")
                                .Build();
  DocumentProto document3 = DocumentBuilder()
                                .SetKey("namespace", "uri3")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  SuggestionResponse::Suggestion suggestionBarCatFo;
  suggestionBarCatFo.set_query("bar OR cat fo");
  SuggestionResponse::Suggestion suggestionBarCatFoo;
  suggestionBarCatFoo.set_query("bar OR cat foo");

  // Search for "(bar OR cat) AND f" both document1 "bar fo" and document2 "cat
  // foo" could match.
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("bar OR cat f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionBarCatFo),
                                   EqualsProto(suggestionBarCatFoo)));
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_PropertyRestriction) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "fool")
          .AddDocumentProperty("sender",
                               DocumentBuilder()
                                   .SetKey("namespace", "uri1-sender")
                                   .SetSchema("Person")
                                   .AddStringProperty("name", "foo")
                                   .AddStringProperty("emailAddress", "fo")
                                   .Build())
          .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());

  // Add property restriction, only search for subject.
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("subject:f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  SuggestionResponse::Suggestion suggestionSubjectFool;
  suggestionSubjectFool.set_query("subject:fool");
  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionSubjectFool)));

  // Add property restriction, only search for nested sender.name
  suggestion_spec.set_prefix("sender.name:f");
  SuggestionResponse::Suggestion suggestionSenderNameFoo;
  suggestionSenderNameFoo.set_query("sender.name:foo");

  response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionSenderNameFoo)));

  // Add property restriction, only search for nonExist section
  suggestion_spec.set_prefix("none:f");

  response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(), IsEmpty());
}

TEST_F(IcingSearchEngineSuggestTest,
       SearchSuggestionsTest_AndOperatorPlusPropertyRestriction) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "bar fo")  // "bar fo"
          .AddStringProperty("body", "fool")
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace1", "uri2")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "bar cat foo")  // "bar cat fool"
          .AddStringProperty("body", "fool")
          .Build();
  DocumentProto document3 = DocumentBuilder()
                                .SetKey("namespace1", "uri3")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")  // "fool"
                                .AddStringProperty("body", "fool")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  // Search for "bar AND subject:f"
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("bar subject:f");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  SuggestionResponse::Suggestion suggestionBarSubjectFo;
  suggestionBarSubjectFo.set_query("bar subject:fo");
  SuggestionResponse::Suggestion suggestionBarSubjectFoo;
  suggestionBarSubjectFoo.set_query("bar subject:foo");
  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionBarSubjectFo),
                                   EqualsProto(suggestionBarSubjectFoo)));

  // Search for "bar AND cat AND subject:f"
  suggestion_spec.set_prefix("bar cat subject:f");
  SuggestionResponse::Suggestion suggestionBarCatSubjectFoo;
  suggestionBarCatSubjectFoo.set_query("bar cat subject:foo");

  response = icing.SearchSuggestions(suggestion_spec);
  ASSERT_THAT(response.status(), ProtoIsOk());
  ASSERT_THAT(response.suggestions(),
              UnorderedElementsAre(EqualsProto(suggestionBarCatSubjectFoo)));
}

TEST_F(IcingSearchEngineSuggestTest, SearchSuggestionsTest_InvalidPrefixTest) {
  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(CreatePersonAndEmailSchema()).status(),
              ProtoIsOk());

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace1", "uri1")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "bar fo")  // "bar fo"
          .AddStringProperty("body", "fool")
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace1", "uri2")
          .SetSchema("Email")
          .SetCreationTimestampMs(10)
          .AddStringProperty("subject", "bar cat foo")  // "bar cat fool"
          .AddStringProperty("body", "fool")
          .Build();
  DocumentProto document3 = DocumentBuilder()
                                .SetKey("namespace1", "uri3")
                                .SetSchema("Email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "fool")  // "fool"
                                .AddStringProperty("body", "fool")
                                .Build();
  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());

  // Search for "f OR"
  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f OR");
  suggestion_spec.set_num_to_return(10);
  suggestion_spec.mutable_scoring_spec()->set_scoring_match_type(
      TermMatchType::PREFIX);
  suggestion_spec.mutable_scoring_spec()->set_rank_by(
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT);

  SuggestionResponse response = icing.SearchSuggestions(suggestion_spec);
  if (SearchSpecProto::default_instance().search_type() ==
      SearchSpecProto::SearchType::ICING_RAW_QUERY) {
    EXPECT_THAT(response.status(), ProtoIsOk());
    EXPECT_THAT(response.suggestions(), IsEmpty());
  } else {
    EXPECT_THAT(response.status(),
                ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
    EXPECT_THAT(response.suggestions(), IsEmpty());
  }

  // TODO(b/208654892): Update handling for hyphens to only consider it a hyphen
  // within a TEXT token (rather than a MINUS token) when surrounded on both
  // sides by TEXT rather than just preceded by TEXT.
  // Search for "f-"
  suggestion_spec.set_prefix("f-");
  response = icing.SearchSuggestions(suggestion_spec);
  EXPECT_THAT(response.status(), ProtoIsOk());
  EXPECT_THAT(response.suggestions(), IsEmpty());

  // Search for "f:"
  suggestion_spec.set_prefix("f:");
  response = icing.SearchSuggestions(suggestion_spec);
  if (SearchSpecProto::default_instance().search_type() ==
      SearchSpecProto::SearchType::ICING_RAW_QUERY) {
    EXPECT_THAT(response.status(), ProtoIsOk());
    EXPECT_THAT(response.suggestions(), IsEmpty());
  } else {
    EXPECT_THAT(response.status(),
                ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
    EXPECT_THAT(response.suggestions(), IsEmpty());
  }

  // Search for "OR OR - :"
  suggestion_spec.set_prefix("OR OR - :");
  response = icing.SearchSuggestions(suggestion_spec);
  if (SearchSpecProto::default_instance().search_type() ==
      SearchSpecProto::SearchType::ICING_RAW_QUERY) {
    EXPECT_THAT(response.status(), ProtoIsOk());
    EXPECT_THAT(response.suggestions(), IsEmpty());
  } else {
    EXPECT_THAT(response.status(),
                ProtoStatusIs(StatusProto::INVALID_ARGUMENT));
    EXPECT_THAT(response.suggestions(), IsEmpty());
  }
}

}  // namespace
}  // namespace lib
}  // namespace icing
