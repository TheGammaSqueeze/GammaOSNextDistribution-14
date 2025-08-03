// Copyright (C) 2022 Google LLC
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

#include <limits>
#include <memory>
#include <string_view>
#include <vector>

#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/portable/equals-proto.h"
#include "icing/portable/platform.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/result/page-result.h"
#include "icing/result/result-adjustment-info.h"
#include "icing/result/result-retriever-v2.h"
#include "icing/result/result-state-v2.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/scoring/priority-queue-scored-document-hits-ranker.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/store/document-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/transform/normalizer.h"
#include "icing/util/snippet-helpers.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

using ::icing::lib::portable_equals_proto::EqualsProto;
using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::SizeIs;

class ResultRetrieverV2SnippetTest : public testing::Test {
 protected:
  ResultRetrieverV2SnippetTest() : test_dir_(GetTestTempDir() + "/icing") {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());
  }

  void SetUp() override {
    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }
    language_segmenter_factory::SegmenterOptions options(ULOC_US);
    ICING_ASSERT_OK_AND_ASSIGN(
        language_segmenter_,
        language_segmenter_factory::Create(std::move(options)));

    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));
    ICING_ASSERT_OK_AND_ASSIGN(normalizer_, normalizer_factory::Create(
                                                /*max_term_byte_size=*/10000));

    SchemaProto schema =
        SchemaBuilder()
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType("Email")
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("subject")
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("body")
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .AddType(SchemaTypeConfigBuilder().SetType("Person").AddProperty(
                PropertyConfigBuilder()
                    .SetName("name")
                    .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                    .SetCardinality(CARDINALITY_OPTIONAL)))
            .Build();
    ASSERT_THAT(schema_store_->SetSchema(
                    schema, /*ignore_errors_and_delete_documents=*/false,
                    /*allow_circular_schema_definitions=*/false),
                IsOk());

    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        DocumentStore::Create(
            &filesystem_, test_dir_, &fake_clock_, schema_store_.get(),
            /*force_recovery_and_revalidate_documents=*/false,
            /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
            /*use_persistent_hash_map=*/false,
            PortableFileBackedProtoLog<
                DocumentWrapper>::kDeflateCompressionLevel,
            /*initialize_stats=*/nullptr));
    document_store_ = std::move(create_result.document_store);
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  SectionId GetSectionId(const std::string& type, const std::string& property) {
    auto type_id_or = schema_store_->GetSchemaTypeId(type);
    if (!type_id_or.ok()) {
      return kInvalidSectionId;
    }
    SchemaTypeId type_id = type_id_or.ValueOrDie();
    for (SectionId section_id = 0; section_id <= kMaxSectionId; ++section_id) {
      auto metadata_or = schema_store_->GetSectionMetadata(type_id, section_id);
      if (!metadata_or.ok()) {
        break;
      }
      const SectionMetadata* metadata = metadata_or.ValueOrDie();
      if (metadata->path == property) {
        return metadata->id;
      }
    }
    return kInvalidSectionId;
  }

  const Filesystem filesystem_;
  const std::string test_dir_;
  std::unique_ptr<LanguageSegmenter> language_segmenter_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<Normalizer> normalizer_;
  std::unique_ptr<DocumentStore> document_store_;
  FakeClock fake_clock_;
};

ResultSpecProto::SnippetSpecProto CreateSnippetSpec() {
  ResultSpecProto::SnippetSpecProto snippet_spec;
  snippet_spec.set_num_to_snippet(std::numeric_limits<int>::max());
  snippet_spec.set_num_matches_per_property(std::numeric_limits<int>::max());
  snippet_spec.set_max_window_utf32_length(1024);
  return snippet_spec;
}

DocumentProto CreateEmailDocument(int id) {
  return DocumentBuilder()
      .SetKey("icing", "Email/" + std::to_string(id))
      .SetSchema("Email")
      .AddStringProperty("subject", "subject foo " + std::to_string(id))
      .AddStringProperty("body", "body bar " + std::to_string(id))
      .SetCreationTimestampMs(1574365086666 + id)
      .Build();
}

DocumentProto CreatePersonDocument(int id) {
  return DocumentBuilder()
      .SetKey("icing", "Person/" + std::to_string(id))
      .SetSchema("Person")
      .AddStringProperty("name", "person " + std::to_string(id))
      .SetCreationTimestampMs(1574365086666 + id)
      .Build();
}

SectionIdMask CreateSectionIdMask(const std::vector<SectionId>& section_ids) {
  SectionIdMask mask = 0;
  for (SectionId section_id : section_ids) {
    mask |= (UINT64_C(1) << section_id);
  }
  return mask;
}

SearchSpecProto CreateSearchSpec(TermMatchType::Code match_type) {
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(match_type);
  return search_spec;
}

ScoringSpecProto CreateScoringSpec(bool is_descending_order) {
  ScoringSpecProto scoring_spec;
  scoring_spec.set_order_by(is_descending_order ? ScoringSpecProto::Order::DESC
                                                : ScoringSpecProto::Order::ASC);
  return scoring_spec;
}

ResultSpecProto CreateResultSpec(int num_per_page) {
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(num_per_page);
  return result_spec;
}

TEST_F(ResultRetrieverV2SnippetTest,
       DefaultSnippetSpecShouldDisableSnippeting) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(CreateEmailDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(CreateEmailDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store_->Put(CreateEmailDocument(/*id=*/3)));

  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "subject"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0},
      {document_id3, hit_section_id_mask, /*score=*/0}};
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/3);

  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/true),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/true), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(3));
  EXPECT_THAT(page_result.results.at(0).snippet(),
              EqualsProto(SnippetProto::default_instance()));
  EXPECT_THAT(page_result.results.at(1).snippet(),
              EqualsProto(SnippetProto::default_instance()));
  EXPECT_THAT(page_result.results.at(2).snippet(),
              EqualsProto(SnippetProto::default_instance()));
  EXPECT_THAT(page_result.num_results_with_snippets, Eq(0));
}

TEST_F(ResultRetrieverV2SnippetTest, SimpleSnippeted) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(CreateEmailDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(CreateEmailDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store_->Put(CreateEmailDocument(/*id=*/3)));

  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "subject"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0},
      {document_id3, hit_section_id_mask, /*score=*/0}};
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // Create ResultSpec with custom snippet spec.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/3);
  *result_spec.mutable_snippet_spec() = CreateSnippetSpec();

  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"foo", "bar"}}})),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(3));
  EXPECT_THAT(page_result.num_results_with_snippets, Eq(3));

  const DocumentProto& result_document_one =
      page_result.results.at(0).document();
  const SnippetProto& result_snippet_one = page_result.results.at(0).snippet();
  EXPECT_THAT(result_document_one, EqualsProto(CreateEmailDocument(/*id=*/1)));
  EXPECT_THAT(result_snippet_one.entries(), SizeIs(2));
  EXPECT_THAT(result_snippet_one.entries(0).property_name(), Eq("body"));
  std::string_view content = GetString(
      &result_document_one, result_snippet_one.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_snippet_one.entries(0)),
              ElementsAre("body bar 1"));
  EXPECT_THAT(GetMatches(content, result_snippet_one.entries(0)),
              ElementsAre("bar"));
  EXPECT_THAT(result_snippet_one.entries(1).property_name(), Eq("subject"));
  content = GetString(&result_document_one,
                      result_snippet_one.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, result_snippet_one.entries(1)),
              ElementsAre("subject foo 1"));
  EXPECT_THAT(GetMatches(content, result_snippet_one.entries(1)),
              ElementsAre("foo"));

  const DocumentProto& result_document_two =
      page_result.results.at(1).document();
  const SnippetProto& result_snippet_two = page_result.results.at(1).snippet();
  EXPECT_THAT(result_document_two, EqualsProto(CreateEmailDocument(/*id=*/2)));
  EXPECT_THAT(result_snippet_two.entries(), SizeIs(2));
  EXPECT_THAT(result_snippet_two.entries(0).property_name(), Eq("body"));
  content = GetString(&result_document_two,
                      result_snippet_two.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_snippet_two.entries(0)),
              ElementsAre("body bar 2"));
  EXPECT_THAT(GetMatches(content, result_snippet_two.entries(0)),
              ElementsAre("bar"));
  EXPECT_THAT(result_snippet_two.entries(1).property_name(), Eq("subject"));
  content = GetString(&result_document_two,
                      result_snippet_two.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, result_snippet_two.entries(1)),
              ElementsAre("subject foo 2"));
  EXPECT_THAT(GetMatches(content, result_snippet_two.entries(1)),
              ElementsAre("foo"));

  const DocumentProto& result_document_three =
      page_result.results.at(2).document();
  const SnippetProto& result_snippet_three =
      page_result.results.at(2).snippet();
  EXPECT_THAT(result_document_three,
              EqualsProto(CreateEmailDocument(/*id=*/3)));
  EXPECT_THAT(result_snippet_three.entries(), SizeIs(2));
  EXPECT_THAT(result_snippet_three.entries(0).property_name(), Eq("body"));
  content = GetString(&result_document_three,
                      result_snippet_three.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_snippet_three.entries(0)),
              ElementsAre("body bar 3"));
  EXPECT_THAT(GetMatches(content, result_snippet_three.entries(0)),
              ElementsAre("bar"));
  EXPECT_THAT(result_snippet_three.entries(1).property_name(), Eq("subject"));
  content = GetString(&result_document_three,
                      result_snippet_three.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, result_snippet_three.entries(1)),
              ElementsAre("subject foo 3"));
  EXPECT_THAT(GetMatches(content, result_snippet_three.entries(1)),
              ElementsAre("foo"));
}

TEST_F(ResultRetrieverV2SnippetTest, OnlyOneDocumentSnippeted) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(CreateEmailDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(CreateEmailDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store_->Put(CreateEmailDocument(/*id=*/3)));

  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "subject"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0},
      {document_id3, hit_section_id_mask, /*score=*/0}};
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // Create ResultSpec with custom snippet spec.
  ResultSpecProto::SnippetSpecProto snippet_spec = CreateSnippetSpec();
  snippet_spec.set_num_to_snippet(1);
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/3);
  *result_spec.mutable_snippet_spec() = std::move(snippet_spec);

  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"foo", "bar"}}})),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(3));
  EXPECT_THAT(page_result.num_results_with_snippets, Eq(1));

  const DocumentProto& result_document = page_result.results.at(0).document();
  const SnippetProto& result_snippet = page_result.results.at(0).snippet();
  EXPECT_THAT(result_document, EqualsProto(CreateEmailDocument(/*id=*/1)));
  EXPECT_THAT(result_snippet.entries(), SizeIs(2));
  EXPECT_THAT(result_snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&result_document, result_snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_snippet.entries(0)),
              ElementsAre("body bar 1"));
  EXPECT_THAT(GetMatches(content, result_snippet.entries(0)),
              ElementsAre("bar"));
  EXPECT_THAT(result_snippet.entries(1).property_name(), Eq("subject"));
  content =
      GetString(&result_document, result_snippet.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, result_snippet.entries(1)),
              ElementsAre("subject foo 1"));
  EXPECT_THAT(GetMatches(content, result_snippet.entries(1)),
              ElementsAre("foo"));

  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(CreateEmailDocument(/*id=*/2)));
  EXPECT_THAT(page_result.results.at(1).snippet(),
              EqualsProto(SnippetProto::default_instance()));

  EXPECT_THAT(page_result.results.at(2).document(),
              EqualsProto(CreateEmailDocument(/*id=*/3)));
  EXPECT_THAT(page_result.results.at(2).snippet(),
              EqualsProto(SnippetProto::default_instance()));
}

TEST_F(ResultRetrieverV2SnippetTest, ShouldSnippetAllResults) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(CreateEmailDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(CreateEmailDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store_->Put(CreateEmailDocument(/*id=*/3)));

  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "subject"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0},
      {document_id3, hit_section_id_mask, /*score=*/0}};
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // Create ResultSpec with custom snippet spec.
  ResultSpecProto::SnippetSpecProto snippet_spec = CreateSnippetSpec();
  snippet_spec.set_num_to_snippet(5);
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/3);
  *result_spec.mutable_snippet_spec() = std::move(snippet_spec);

  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"foo", "bar"}}})),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  // num_to_snippet = 5, num_previously_returned_in = 0,
  // We can return 5 - 0 = 5 snippets at most. We're able to return all 3
  // snippets here.
  ASSERT_THAT(page_result.results, SizeIs(3));
  EXPECT_THAT(page_result.results.at(0).snippet().entries(), Not(IsEmpty()));
  EXPECT_THAT(page_result.results.at(1).snippet().entries(), Not(IsEmpty()));
  EXPECT_THAT(page_result.results.at(2).snippet().entries(), Not(IsEmpty()));
  EXPECT_THAT(page_result.num_results_with_snippets, Eq(3));
}

TEST_F(ResultRetrieverV2SnippetTest, ShouldSnippetSomeResults) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(CreateEmailDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(CreateEmailDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store_->Put(CreateEmailDocument(/*id=*/3)));

  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "subject"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0},
      {document_id3, hit_section_id_mask, /*score=*/0}};
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // Create ResultSpec with custom snippet spec.
  ResultSpecProto::SnippetSpecProto snippet_spec = CreateSnippetSpec();
  snippet_spec.set_num_to_snippet(5);
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/3);
  *result_spec.mutable_snippet_spec() = std::move(snippet_spec);

  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"foo", "bar"}}})),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);
  {
    absl_ports::unique_lock l(&result_state.mutex);

    // Set remaining_num_to_snippet = 2
    result_state.parent_adjustment_info()->remaining_num_to_snippet = 2;
  }

  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(3));
  EXPECT_THAT(page_result.results.at(0).snippet().entries(), Not(IsEmpty()));
  EXPECT_THAT(page_result.results.at(1).snippet().entries(), Not(IsEmpty()));
  EXPECT_THAT(page_result.results.at(2).snippet().entries(), IsEmpty());
  EXPECT_THAT(page_result.num_results_with_snippets, Eq(2));
}

TEST_F(ResultRetrieverV2SnippetTest, ShouldNotSnippetAnyResults) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(CreateEmailDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(CreateEmailDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store_->Put(CreateEmailDocument(/*id=*/3)));

  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "subject"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0},
      {document_id3, hit_section_id_mask, /*score=*/0}};
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // Create ResultSpec with custom snippet spec.
  ResultSpecProto::SnippetSpecProto snippet_spec = CreateSnippetSpec();
  snippet_spec.set_num_to_snippet(5);
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/3);
  *result_spec.mutable_snippet_spec() = std::move(snippet_spec);

  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"foo", "bar"}}})),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);
  {
    absl_ports::unique_lock l(&result_state.mutex);

    // Set remaining_num_to_snippet = 0
    result_state.parent_adjustment_info()->remaining_num_to_snippet = 0;
  }

  // We can't return any snippets for this page.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(3));
  EXPECT_THAT(page_result.results.at(0).snippet().entries(), IsEmpty());
  EXPECT_THAT(page_result.results.at(1).snippet().entries(), IsEmpty());
  EXPECT_THAT(page_result.results.at(2).snippet().entries(), IsEmpty());
  EXPECT_THAT(page_result.num_results_with_snippets, Eq(0));
}

TEST_F(ResultRetrieverV2SnippetTest,
       ShouldNotSnippetAnyResultsForNonPositiveNumMatchesPerProperty) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(CreateEmailDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(CreateEmailDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store_->Put(CreateEmailDocument(/*id=*/3)));

  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "subject"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0},
      {document_id3, hit_section_id_mask, /*score=*/0}};
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // Create ResultSpec with custom snippet spec.
  ResultSpecProto::SnippetSpecProto snippet_spec = CreateSnippetSpec();
  snippet_spec.set_num_to_snippet(5);
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/3);
  *result_spec.mutable_snippet_spec() = std::move(snippet_spec);

  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"foo", "bar"}}})),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  {
    absl_ports::unique_lock l(&result_state.mutex);

    // Set num_matchers_per_property = 0
    result_state.parent_adjustment_info()
        ->snippet_context.snippet_spec.set_num_matches_per_property(0);
  }

  // We can't return any snippets for this page even though num_to_snippet > 0.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(3));
  EXPECT_THAT(page_result.results.at(0).snippet().entries(), IsEmpty());
  EXPECT_THAT(page_result.results.at(1).snippet().entries(), IsEmpty());
  EXPECT_THAT(page_result.results.at(2).snippet().entries(), IsEmpty());
  EXPECT_THAT(page_result.num_results_with_snippets, Eq(0));
}

TEST_F(ResultRetrieverV2SnippetTest, JoinSnippeted) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId person_document_id1,
      document_store_->Put(CreatePersonDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId person_document_id2,
      document_store_->Put(CreatePersonDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId person_document_id3,
      document_store_->Put(CreatePersonDocument(/*id=*/3)));

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId email_document_id1,
      document_store_->Put(CreateEmailDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId email_document_id2,
      document_store_->Put(CreateEmailDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId email_document_id3,
      document_store_->Put(CreateEmailDocument(/*id=*/3)));

  std::vector<SectionId> person_hit_section_ids = {
      GetSectionId("Person", "name")};
  std::vector<SectionId> email_hit_section_ids = {
      GetSectionId("Email", "subject"), GetSectionId("Email", "body")};
  SectionIdMask person_hit_section_id_mask =
      CreateSectionIdMask(person_hit_section_ids);
  SectionIdMask email_hit_section_id_mask =
      CreateSectionIdMask(email_hit_section_ids);

  ScoredDocumentHit person1_scored_doc_hit(
      person_document_id1, person_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit person2_scored_doc_hit(
      person_document_id2, person_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit person3_scored_doc_hit(
      person_document_id3, person_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit email1_scored_doc_hit(
      email_document_id1, email_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit email2_scored_doc_hit(
      email_document_id2, email_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit email3_scored_doc_hit(
      email_document_id3, email_hit_section_id_mask, /*score=*/0);

  // Create JoinedScoredDocumentHits mapping:
  // - Person1 to Email1 and Email2
  // - Person2 to empty
  // - Person3 to Email3
  JoinedScoredDocumentHit joined_scored_document_hit1(
      /*final_score=*/0, /*parent_scored_document_hit=*/person1_scored_doc_hit,
      /*child_scored_document_hits=*/
      {email1_scored_doc_hit, email2_scored_doc_hit});
  JoinedScoredDocumentHit joined_scored_document_hit2(
      /*final_score=*/0, /*parent_scored_document_hit=*/person2_scored_doc_hit,
      /*child_scored_document_hits=*/{});
  JoinedScoredDocumentHit joined_scored_document_hit3(
      /*final_score=*/0, /*parent_scored_document_hit=*/person3_scored_doc_hit,
      /*child_scored_document_hits=*/{email3_scored_doc_hit});

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // Create parent ResultSpec with custom snippet spec.
  ResultSpecProto parent_result_spec = CreateResultSpec(/*num_per_page=*/3);
  parent_result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());
  *parent_result_spec.mutable_snippet_spec() = CreateSnippetSpec();

  // Create child ResultSpec with custom snippet spec.
  ResultSpecProto child_result_spec;
  *child_result_spec.mutable_snippet_spec() = CreateSnippetSpec();

  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<JoinedScoredDocumentHit>>(
          std::vector<JoinedScoredDocumentHit>{joined_scored_document_hit1,
                                               joined_scored_document_hit2,
                                               joined_scored_document_hit3},
          /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), parent_result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"person"}}})),
      /*child_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), child_result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"foo", "bar"}}})),
      parent_result_spec, *document_store_);

  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(3));
  EXPECT_THAT(page_result.num_results_with_snippets, Eq(3));

  // Result1: Person1 for parent and [Email1, Email2] for children.
  // Check parent doc (Person1).
  const DocumentProto& result_parent_document_one =
      page_result.results.at(0).document();
  const SnippetProto& result_parent_snippet_one =
      page_result.results.at(0).snippet();
  EXPECT_THAT(result_parent_document_one,
              EqualsProto(CreatePersonDocument(/*id=*/1)));
  ASSERT_THAT(result_parent_snippet_one.entries(), SizeIs(1));
  EXPECT_THAT(result_parent_snippet_one.entries(0).property_name(), Eq("name"));
  std::string_view content =
      GetString(&result_parent_document_one,
                result_parent_snippet_one.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_parent_snippet_one.entries(0)),
              ElementsAre("person 1"));
  EXPECT_THAT(GetMatches(content, result_parent_snippet_one.entries(0)),
              ElementsAre("person"));

  // Check child docs.
  ASSERT_THAT(page_result.results.at(0).joined_results(), SizeIs(2));
  // Check Email1.
  const DocumentProto& result_child_document_one =
      page_result.results.at(0).joined_results(0).document();
  const SnippetProto& result_child_snippet_one =
      page_result.results.at(0).joined_results(0).snippet();
  EXPECT_THAT(result_child_document_one,
              EqualsProto(CreateEmailDocument(/*id=*/1)));
  ASSERT_THAT(result_child_snippet_one.entries(), SizeIs(2));
  EXPECT_THAT(result_child_snippet_one.entries(0).property_name(), Eq("body"));
  content = GetString(&result_child_document_one,
                      result_child_snippet_one.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_child_snippet_one.entries(0)),
              ElementsAre("body bar 1"));
  EXPECT_THAT(GetMatches(content, result_child_snippet_one.entries(0)),
              ElementsAre("bar"));
  EXPECT_THAT(result_child_snippet_one.entries(1).property_name(),
              Eq("subject"));
  content = GetString(&result_child_document_one,
                      result_child_snippet_one.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, result_child_snippet_one.entries(1)),
              ElementsAre("subject foo 1"));
  EXPECT_THAT(GetMatches(content, result_child_snippet_one.entries(1)),
              ElementsAre("foo"));
  // Check Email2.
  const DocumentProto& result_child_document_two =
      page_result.results.at(0).joined_results(1).document();
  const SnippetProto& result_child_snippet_two =
      page_result.results.at(0).joined_results(1).snippet();
  EXPECT_THAT(result_child_document_two,
              EqualsProto(CreateEmailDocument(/*id=*/2)));
  ASSERT_THAT(result_child_snippet_two.entries(), SizeIs(2));
  EXPECT_THAT(result_child_snippet_two.entries(0).property_name(), Eq("body"));
  content = GetString(&result_child_document_two,
                      result_child_snippet_two.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_child_snippet_two.entries(0)),
              ElementsAre("body bar 2"));
  EXPECT_THAT(GetMatches(content, result_child_snippet_two.entries(0)),
              ElementsAre("bar"));
  EXPECT_THAT(result_child_snippet_two.entries(1).property_name(),
              Eq("subject"));
  content = GetString(&result_child_document_two,
                      result_child_snippet_two.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, result_child_snippet_two.entries(1)),
              ElementsAre("subject foo 2"));
  EXPECT_THAT(GetMatches(content, result_child_snippet_two.entries(1)),
              ElementsAre("foo"));

  // Result2: Person2 for parent and [] for children.
  // Check parent doc (Person1).
  const DocumentProto& result_parent_document_two =
      page_result.results.at(1).document();
  const SnippetProto& result_parent_snippet_two =
      page_result.results.at(1).snippet();
  EXPECT_THAT(result_parent_document_two,
              EqualsProto(CreatePersonDocument(/*id=*/2)));
  ASSERT_THAT(result_parent_snippet_two.entries(), SizeIs(1));
  EXPECT_THAT(result_parent_snippet_two.entries(0).property_name(), Eq("name"));
  content = GetString(&result_parent_document_two,
                      result_parent_snippet_two.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_parent_snippet_two.entries(0)),
              ElementsAre("person 2"));
  EXPECT_THAT(GetMatches(content, result_parent_snippet_two.entries(0)),
              ElementsAre("person"));
  // Check child docs.
  ASSERT_THAT(page_result.results.at(1).joined_results(), IsEmpty());

  // Result3: Person3 for parent and [Email3] for children.
  // Check parent doc (Person3).
  const DocumentProto& result_parent_document_three =
      page_result.results.at(2).document();
  const SnippetProto& result_parent_snippet_three =
      page_result.results.at(2).snippet();
  EXPECT_THAT(result_parent_document_three,
              EqualsProto(CreatePersonDocument(/*id=*/3)));
  ASSERT_THAT(result_parent_snippet_three.entries(), SizeIs(1));
  EXPECT_THAT(result_parent_snippet_three.entries(0).property_name(),
              Eq("name"));
  content = GetString(&result_parent_document_three,
                      result_parent_snippet_three.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_parent_snippet_three.entries(0)),
              ElementsAre("person 3"));
  EXPECT_THAT(GetMatches(content, result_parent_snippet_three.entries(0)),
              ElementsAre("person"));

  // Check child docs.
  ASSERT_THAT(page_result.results.at(2).joined_results(), SizeIs(1));
  // Check Email3.
  const DocumentProto& result_child_document_three =
      page_result.results.at(2).joined_results(0).document();
  const SnippetProto& result_child_snippet_three =
      page_result.results.at(2).joined_results(0).snippet();
  EXPECT_THAT(result_child_document_three,
              EqualsProto(CreateEmailDocument(/*id=*/3)));
  ASSERT_THAT(result_child_snippet_three.entries(), SizeIs(2));
  EXPECT_THAT(result_child_snippet_three.entries(0).property_name(),
              Eq("body"));
  content = GetString(&result_child_document_three,
                      result_child_snippet_three.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, result_child_snippet_three.entries(0)),
              ElementsAre("body bar 3"));
  EXPECT_THAT(GetMatches(content, result_child_snippet_three.entries(0)),
              ElementsAre("bar"));
  EXPECT_THAT(result_child_snippet_three.entries(1).property_name(),
              Eq("subject"));
  content = GetString(&result_child_document_three,
                      result_child_snippet_three.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, result_child_snippet_three.entries(1)),
              ElementsAre("subject foo 3"));
  EXPECT_THAT(GetMatches(content, result_child_snippet_three.entries(1)),
              ElementsAre("foo"));
}

TEST_F(ResultRetrieverV2SnippetTest, ShouldSnippetAllJoinedResults) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId person_document_id1,
      document_store_->Put(CreatePersonDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId person_document_id2,
      document_store_->Put(CreatePersonDocument(/*id=*/2)));

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId email_document_id1,
      document_store_->Put(CreateEmailDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId email_document_id2,
      document_store_->Put(CreateEmailDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId email_document_id3,
      document_store_->Put(CreateEmailDocument(/*id=*/3)));

  std::vector<SectionId> person_hit_section_ids = {
      GetSectionId("Person", "name")};
  std::vector<SectionId> email_hit_section_ids = {
      GetSectionId("Email", "subject"), GetSectionId("Email", "body")};
  SectionIdMask person_hit_section_id_mask =
      CreateSectionIdMask(person_hit_section_ids);
  SectionIdMask email_hit_section_id_mask =
      CreateSectionIdMask(email_hit_section_ids);

  ScoredDocumentHit person1_scored_doc_hit(
      person_document_id1, person_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit person2_scored_doc_hit(
      person_document_id2, person_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit email1_scored_doc_hit(
      email_document_id1, email_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit email2_scored_doc_hit(
      email_document_id2, email_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit email3_scored_doc_hit(
      email_document_id3, email_hit_section_id_mask, /*score=*/0);

  // Create JoinedScoredDocumentHits mapping:
  // - Person1 to Email1
  // - Person2 to Email2, Email3
  JoinedScoredDocumentHit joined_scored_document_hit1(
      /*final_score=*/0, /*parent_scored_document_hit=*/person1_scored_doc_hit,
      /*child_scored_document_hits=*/
      {email1_scored_doc_hit});
  JoinedScoredDocumentHit joined_scored_document_hit2(
      /*final_score=*/0, /*parent_scored_document_hit=*/person2_scored_doc_hit,
      /*child_scored_document_hits=*/
      {email2_scored_doc_hit, email3_scored_doc_hit});

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // Create parent ResultSpec with custom snippet spec.
  ResultSpecProto::SnippetSpecProto parent_snippet_spec = CreateSnippetSpec();
  parent_snippet_spec.set_num_to_snippet(1);
  ResultSpecProto parent_result_spec = CreateResultSpec(/*num_per_page=*/3);
  parent_result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());
  *parent_result_spec.mutable_snippet_spec() = std::move(parent_snippet_spec);

  // Create child ResultSpec with custom snippet spec.
  ResultSpecProto::SnippetSpecProto child_snippet_spec = CreateSnippetSpec();
  child_snippet_spec.set_num_to_snippet(3);
  ResultSpecProto child_result_spec;
  *child_result_spec.mutable_snippet_spec() = std::move(child_snippet_spec);

  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<JoinedScoredDocumentHit>>(
          std::vector<JoinedScoredDocumentHit>{joined_scored_document_hit1,
                                               joined_scored_document_hit2},
          /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), parent_result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"person"}}})),
      /*child_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), child_result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"foo", "bar"}}})),
      parent_result_spec, *document_store_);

  // Only 1 parent document should be snippeted, but all of the child documents
  // should be snippeted.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  // Result1: Person1 for parent and [Email1] for children.
  // Check parent doc (Person1).
  EXPECT_THAT(page_result.results.at(0).snippet().entries(), Not(IsEmpty()));
  // Check child docs.
  ASSERT_THAT(page_result.results.at(0).joined_results(), SizeIs(1));
  EXPECT_THAT(page_result.results.at(0).joined_results(0).snippet().entries(),
              Not(IsEmpty()));

  // Result2: Person2 for parent and [Email2, Email3] for children.
  // Check parent doc (Person2).
  EXPECT_THAT(page_result.results.at(1).snippet().entries(), IsEmpty());
  // Check child docs.
  ASSERT_THAT(page_result.results.at(1).joined_results(), SizeIs(2));
  EXPECT_THAT(page_result.results.at(1).joined_results(0).snippet().entries(),
              Not(IsEmpty()));
  EXPECT_THAT(page_result.results.at(1).joined_results(1).snippet().entries(),
              Not(IsEmpty()));

  EXPECT_THAT(page_result.num_results_with_snippets, Eq(1));
}

TEST_F(ResultRetrieverV2SnippetTest, ShouldSnippetSomeJoinedResults) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId person_document_id1,
      document_store_->Put(CreatePersonDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId person_document_id2,
      document_store_->Put(CreatePersonDocument(/*id=*/2)));

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId email_document_id1,
      document_store_->Put(CreateEmailDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId email_document_id2,
      document_store_->Put(CreateEmailDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId email_document_id3,
      document_store_->Put(CreateEmailDocument(/*id=*/3)));

  std::vector<SectionId> person_hit_section_ids = {
      GetSectionId("Person", "name")};
  std::vector<SectionId> email_hit_section_ids = {
      GetSectionId("Email", "subject"), GetSectionId("Email", "body")};
  SectionIdMask person_hit_section_id_mask =
      CreateSectionIdMask(person_hit_section_ids);
  SectionIdMask email_hit_section_id_mask =
      CreateSectionIdMask(email_hit_section_ids);

  ScoredDocumentHit person1_scored_doc_hit(
      person_document_id1, person_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit person2_scored_doc_hit(
      person_document_id2, person_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit email1_scored_doc_hit(
      email_document_id1, email_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit email2_scored_doc_hit(
      email_document_id2, email_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit email3_scored_doc_hit(
      email_document_id3, email_hit_section_id_mask, /*score=*/0);

  // Create JoinedScoredDocumentHits mapping:
  // - Person1 to Email1
  // - Person2 to Email2, Email3
  JoinedScoredDocumentHit joined_scored_document_hit1(
      /*final_score=*/0, /*parent_scored_document_hit=*/person1_scored_doc_hit,
      /*child_scored_document_hits=*/
      {email1_scored_doc_hit});
  JoinedScoredDocumentHit joined_scored_document_hit2(
      /*final_score=*/0, /*parent_scored_document_hit=*/person2_scored_doc_hit,
      /*child_scored_document_hits=*/
      {email2_scored_doc_hit, email3_scored_doc_hit});

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // Create parent ResultSpec with custom snippet spec.
  ResultSpecProto::SnippetSpecProto parent_snippet_spec = CreateSnippetSpec();
  parent_snippet_spec.set_num_to_snippet(3);
  ResultSpecProto parent_result_spec = CreateResultSpec(/*num_per_page=*/3);
  parent_result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());
  *parent_result_spec.mutable_snippet_spec() = std::move(parent_snippet_spec);

  // Create child ResultSpec with custom snippet spec.
  ResultSpecProto::SnippetSpecProto child_snippet_spec = CreateSnippetSpec();
  child_snippet_spec.set_num_to_snippet(2);
  ResultSpecProto child_result_spec;
  *child_result_spec.mutable_snippet_spec() = std::move(child_snippet_spec);

  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<JoinedScoredDocumentHit>>(
          std::vector<JoinedScoredDocumentHit>{joined_scored_document_hit1,
                                               joined_scored_document_hit2},
          /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), parent_result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"person"}}})),
      /*child_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), child_result_spec,
          schema_store_.get(),
          SectionRestrictQueryTermsMap({{"", {"foo", "bar"}}})),
      parent_result_spec, *document_store_);

  // All parents document should be snippeted. Only 2 child documents should be
  // snippeted.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  // Result1: Person1 for parent and [Email1] for children.
  // Check parent doc (Person1).
  EXPECT_THAT(page_result.results.at(0).snippet().entries(), Not(IsEmpty()));
  // Check child docs.
  ASSERT_THAT(page_result.results.at(0).joined_results(), SizeIs(1));
  EXPECT_THAT(page_result.results.at(0).joined_results(0).snippet().entries(),
              Not(IsEmpty()));

  // Result2: Person2 for parent and [Email2, Email3] for children.
  // Check parent doc (Person2).
  EXPECT_THAT(page_result.results.at(1).snippet().entries(), Not(IsEmpty()));
  // Check child docs.
  ASSERT_THAT(page_result.results.at(1).joined_results(), SizeIs(2));
  EXPECT_THAT(page_result.results.at(1).joined_results(0).snippet().entries(),
              Not(IsEmpty()));
  EXPECT_THAT(page_result.results.at(1).joined_results(1).snippet().entries(),
              IsEmpty());

  EXPECT_THAT(page_result.num_results_with_snippets, Eq(2));
}

}  // namespace

}  // namespace lib
}  // namespace icing
