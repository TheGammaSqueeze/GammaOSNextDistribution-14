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

#include "icing/result/result-state-manager.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/portable/equals-proto.h"
#include "icing/result/page-result.h"
#include "icing/result/result-adjustment-info.h"
#include "icing/result/result-retriever-v2.h"
#include "icing/schema/schema-store.h"
#include "icing/scoring/priority-queue-scored-document-hits-ranker.h"
#include "icing/scoring/scored-document-hits-ranker.h"
#include "icing/store/document-store.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/transform/normalizer.h"
#include "icing/util/clock.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {
namespace {

using ::icing::lib::portable_equals_proto::EqualsProto;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::Not;
using ::testing::SizeIs;
using PageResultInfo = std::pair<uint64_t, PageResult>;

ScoringSpecProto CreateScoringSpec() {
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);
  return scoring_spec;
}

ResultSpecProto CreateResultSpec(
    int num_per_page, ResultSpecProto::ResultGroupingType result_group_type) {
  ResultSpecProto result_spec;
  result_spec.set_result_group_type(result_group_type);
  result_spec.set_num_per_page(num_per_page);
  return result_spec;
}

DocumentProto CreateDocument(int id) {
  return DocumentBuilder()
      .SetNamespace("namespace")
      .SetUri(std::to_string(id))
      .SetSchema("Document")
      .SetCreationTimestampMs(1574365086666 + id)
      .SetScore(1)
      .Build();
}

class ResultStateManagerTest : public testing::Test {
 protected:
  ResultStateManagerTest() : test_dir_(GetTestTempDir() + "/icing") {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());
  }

  void SetUp() override {
    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }

    clock_ = std::make_unique<FakeClock>();

    language_segmenter_factory::SegmenterOptions options(ULOC_US);
    ICING_ASSERT_OK_AND_ASSIGN(
        language_segmenter_,
        language_segmenter_factory::Create(std::move(options)));

    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, clock_.get()));
    SchemaProto schema;
    schema.add_types()->set_schema_type("Document");
    ICING_ASSERT_OK(schema_store_->SetSchema(
        std::move(schema), /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    ICING_ASSERT_OK_AND_ASSIGN(normalizer_, normalizer_factory::Create(
                                                /*max_term_byte_size=*/10000));

    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult result,
        DocumentStore::Create(
            &filesystem_, test_dir_, clock_.get(), schema_store_.get(),
            /*force_recovery_and_revalidate_documents=*/false,
            /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
            /*use_persistent_hash_map=*/false,
            PortableFileBackedProtoLog<
                DocumentWrapper>::kDeflateCompressionLevel,
            /*initialize_stats=*/nullptr));
    document_store_ = std::move(result.document_store);

    ICING_ASSERT_OK_AND_ASSIGN(
        result_retriever_, ResultRetrieverV2::Create(
                               document_store_.get(), schema_store_.get(),
                               language_segmenter_.get(), normalizer_.get()));
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
    clock_.reset();
  }

  std::pair<ScoredDocumentHit, DocumentProto> AddScoredDocument(
      DocumentId document_id) {
    DocumentProto document;
    document.set_namespace_("namespace");
    document.set_uri(std::to_string(document_id));
    document.set_schema("Document");
    document.set_creation_timestamp_ms(1574365086666 + document_id);
    document_store_->Put(document);
    return std::make_pair(
        ScoredDocumentHit(document_id, kSectionIdMaskNone, /*score=*/1),
        std::move(document));
  }

  std::pair<std::vector<ScoredDocumentHit>, std::vector<DocumentProto>>
  AddScoredDocuments(const std::vector<DocumentId>& document_ids) {
    std::vector<ScoredDocumentHit> scored_document_hits;
    std::vector<DocumentProto> document_protos;

    for (DocumentId document_id : document_ids) {
      std::pair<ScoredDocumentHit, DocumentProto> pair =
          AddScoredDocument(document_id);
      scored_document_hits.emplace_back(std::move(pair.first));
      document_protos.emplace_back(std::move(pair.second));
    }

    std::reverse(document_protos.begin(), document_protos.end());

    return std::make_pair(std::move(scored_document_hits),
                          std::move(document_protos));
  }

  FakeClock* clock() { return clock_.get(); }
  const FakeClock* clock() const { return clock_.get(); }

  DocumentStore& document_store() { return *document_store_; }
  const DocumentStore& document_store() const { return *document_store_; }

  SchemaStore& schema_store() { return *schema_store_; }
  const SchemaStore& schema_store() const { return *schema_store_; }

  const ResultRetrieverV2& result_retriever() const {
    return *result_retriever_;
  }

 private:
  Filesystem filesystem_;
  const std::string test_dir_;
  std::unique_ptr<FakeClock> clock_;
  std::unique_ptr<LanguageSegmenter> language_segmenter_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<Normalizer> normalizer_;
  std::unique_ptr<DocumentStore> document_store_;
  std::unique_ptr<ResultRetrieverV2> result_retriever_;
};

TEST_F(ResultStateManagerTest, ShouldCacheAndRetrieveFirstPageOnePage) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store().Put(CreateDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store().Put(CreateDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store().Put(CreateDocument(/*id=*/3)));
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, kSectionIdMaskNone, /*score=*/1},
      {document_id2, kSectionIdMaskNone, /*score=*/1},
      {document_id3, kSectionIdMaskNone, /*score=*/1}};
  std::unique_ptr<ScoredDocumentHitsRanker> ranker = std::make_unique<
      PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
      std::move(scored_document_hits), /*is_descending=*/true);

  ResultStateManager result_state_manager(
      /*max_total_hits=*/std::numeric_limits<int>::max(), document_store());

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::move(ranker), /*parent_adjustment_info=*/nullptr,
          /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/10, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  EXPECT_THAT(page_result_info.first, Eq(kInvalidNextPageToken));

  // Should get docs.
  ASSERT_THAT(page_result_info.second.results, SizeIs(3));
  EXPECT_THAT(page_result_info.second.results.at(0).document(),
              EqualsProto(CreateDocument(/*id=*/3)));
  EXPECT_THAT(page_result_info.second.results.at(1).document(),
              EqualsProto(CreateDocument(/*id=*/2)));
  EXPECT_THAT(page_result_info.second.results.at(2).document(),
              EqualsProto(CreateDocument(/*id=*/1)));
}

TEST_F(ResultStateManagerTest, ShouldCacheAndRetrieveFirstPageMultiplePages) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store().Put(CreateDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store().Put(CreateDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store().Put(CreateDocument(/*id=*/3)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id4,
                             document_store().Put(CreateDocument(/*id=*/4)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id5,
                             document_store().Put(CreateDocument(/*id=*/5)));
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, kSectionIdMaskNone, /*score=*/1},
      {document_id2, kSectionIdMaskNone, /*score=*/1},
      {document_id3, kSectionIdMaskNone, /*score=*/1},
      {document_id4, kSectionIdMaskNone, /*score=*/1},
      {document_id5, kSectionIdMaskNone, /*score=*/1}};
  std::unique_ptr<ScoredDocumentHitsRanker> ranker = std::make_unique<
      PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
      std::move(scored_document_hits), /*is_descending=*/true);

  ResultStateManager result_state_manager(
      /*max_total_hits=*/std::numeric_limits<int>::max(), document_store());

  // First page, 2 results
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::move(ranker), /*parent_adjustment_info=*/nullptr,
          /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/2, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));
  EXPECT_THAT(page_result_info1.first, Not(Eq(kInvalidNextPageToken)));
  ASSERT_THAT(page_result_info1.second.results, SizeIs(2));
  EXPECT_THAT(page_result_info1.second.results.at(0).document(),
              EqualsProto(CreateDocument(/*id=*/5)));
  EXPECT_THAT(page_result_info1.second.results.at(1).document(),
              EqualsProto(CreateDocument(/*id=*/4)));

  uint64_t next_page_token = page_result_info1.first;

  // Second page, 2 results
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.GetNextPage(next_page_token, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()));
  EXPECT_THAT(page_result_info2.first, Eq(next_page_token));
  ASSERT_THAT(page_result_info2.second.results, SizeIs(2));
  EXPECT_THAT(page_result_info2.second.results.at(0).document(),
              EqualsProto(CreateDocument(/*id=*/3)));
  EXPECT_THAT(page_result_info2.second.results.at(1).document(),
              EqualsProto(CreateDocument(/*id=*/2)));

  // Third page, 1 result
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info3,
      result_state_manager.GetNextPage(next_page_token, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()));
  EXPECT_THAT(page_result_info3.first, Eq(kInvalidNextPageToken));
  ASSERT_THAT(page_result_info3.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info3.second.results.at(0).document(),
              EqualsProto(CreateDocument(/*id=*/1)));

  // No results
  EXPECT_THAT(
      result_state_manager.GetNextPage(next_page_token, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()),
      StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(ResultStateManagerTest, NullRankerShouldReturnError) {
  ResultStateManager result_state_manager(
      /*max_total_hits=*/std::numeric_limits<int>::max(), document_store());

  EXPECT_THAT(
      result_state_manager.CacheAndRetrieveFirstPage(
          /*ranker=*/nullptr, /*parent_adjustment_info=*/nullptr,
          /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(ResultStateManagerTest, EmptyRankerShouldReturnEmptyFirstPage) {
  ResultStateManager result_state_manager(
      /*max_total_hits=*/std::numeric_limits<int>::max(), document_store());
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::vector<ScoredDocumentHit>(), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  EXPECT_THAT(page_result_info.first, Eq(kInvalidNextPageToken));
  EXPECT_THAT(page_result_info.second.results, IsEmpty());
}

TEST_F(ResultStateManagerTest, ShouldAllowEmptyFirstPage) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store().Put(CreateDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store().Put(CreateDocument(/*id=*/2)));
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, kSectionIdMaskNone, /*score=*/1},
      {document_id2, kSectionIdMaskNone, /*score=*/1}};

  ResultStateManager result_state_manager(
      /*max_total_hits=*/std::numeric_limits<int>::max(), document_store());

  // Create a ResultSpec that limits "namespace" to 0 results.
  ResultSpecProto result_spec =
      CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(0);
  entry->set_namespace_("namespace");

  // First page, no result.
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          result_spec, document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));
  // If the first page has no result, then it should be the last page.
  EXPECT_THAT(page_result_info.first, Eq(kInvalidNextPageToken));
  EXPECT_THAT(page_result_info.second.results, IsEmpty());
}

TEST_F(ResultStateManagerTest, ShouldAllowEmptyLastPage) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store().Put(CreateDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store().Put(CreateDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store().Put(CreateDocument(/*id=*/3)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id4,
                             document_store().Put(CreateDocument(/*id=*/4)));
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, kSectionIdMaskNone, /*score=*/1},
      {document_id2, kSectionIdMaskNone, /*score=*/1},
      {document_id3, kSectionIdMaskNone, /*score=*/1},
      {document_id4, kSectionIdMaskNone, /*score=*/1}};

  ResultStateManager result_state_manager(
      /*max_total_hits=*/std::numeric_limits<int>::max(), document_store());

  // Create a ResultSpec that limits "namespace" to 2 results.
  ResultSpecProto result_spec =
      CreateResultSpec(/*num_per_page=*/2, ResultSpecProto::NAMESPACE);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(2);
  entry->set_namespace_("namespace");

  // First page, 2 results.
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          result_spec, document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));
  EXPECT_THAT(page_result_info1.first, Not(Eq(kInvalidNextPageToken)));
  ASSERT_THAT(page_result_info1.second.results, SizeIs(2));
  EXPECT_THAT(page_result_info1.second.results.at(0).document(),
              EqualsProto(CreateDocument(/*id=*/4)));
  EXPECT_THAT(page_result_info1.second.results.at(1).document(),
              EqualsProto(CreateDocument(/*id=*/3)));

  uint64_t next_page_token = page_result_info1.first;

  // Second page, all remaining documents will be filtered out by group result
  // limiter, so we should get an empty page.
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.GetNextPage(next_page_token, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()));
  EXPECT_THAT(page_result_info2.first, Eq(kInvalidNextPageToken));
  EXPECT_THAT(page_result_info2.second.results, IsEmpty());
}

TEST_F(ResultStateManagerTest,
       ShouldInvalidateExpiredTokensWhenCacheAndRetrieveFirstPage) {
  auto [scored_document_hits1, document_protos1] = AddScoredDocuments(
      {/*document_id=*/0, /*document_id=*/1, /*document_id=*/2});
  auto [scored_document_hits2, document_protos2] = AddScoredDocuments(
      {/*document_id=*/3, /*document_id=*/4, /*document_id=*/5});

  ResultStateManager result_state_manager(
      /*max_total_hits=*/std::numeric_limits<int>::max(), document_store());

  SectionRestrictQueryTermsMap query_terms;
  SearchSpecProto search_spec;
  ScoringSpecProto scoring_spec = CreateScoringSpec();
  ResultSpecProto result_spec =
      CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE);

  // Set time as 1s and add state 1.
  clock()->SetSystemTimeMilliseconds(1000);
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/
          std::make_unique<ResultAdjustmentInfo>(search_spec, scoring_spec,
                                                 result_spec, &schema_store(),
                                                 query_terms),
          /*child_adjustment_info=*/nullptr, result_spec, document_store(),
          result_retriever(), clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info1.first, Not(Eq(kInvalidNextPageToken)));

  // Set time as 1hr1s and add state 2.
  clock()->SetSystemTimeMilliseconds(kDefaultResultStateTtlInMs + 1000);
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/
          std::make_unique<ResultAdjustmentInfo>(search_spec, scoring_spec,
                                                 result_spec, &schema_store(),
                                                 query_terms),
          /*child_adjustment_info=*/nullptr, result_spec, document_store(),
          result_retriever(), clock()->GetSystemTimeMilliseconds()));

  // Calling CacheAndRetrieveFirstPage() on state 2 should invalidate the
  // expired state 1 internally.
  //
  // We test the behavior by setting time back to 1s, to make sure the
  // invalidation of state 1 was done by the previous
  // CacheAndRetrieveFirstPage() instead of the following GetNextPage().
  clock()->SetSystemTimeMilliseconds(1000);
  // page_result_info1's token (page_result_info1.first) shouldn't be found.
  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info1.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(ResultStateManagerTest,
       ShouldInvalidateExpiredTokensWhenGetNextPageOnOthers) {
  auto [scored_document_hits1, document_protos1] = AddScoredDocuments(
      {/*document_id=*/0, /*document_id=*/1, /*document_id=*/2});
  auto [scored_document_hits2, document_protos2] = AddScoredDocuments(
      {/*document_id=*/3, /*document_id=*/4, /*document_id=*/5});

  ResultStateManager result_state_manager(
      /*max_total_hits=*/std::numeric_limits<int>::max(), document_store());

  // Set time as 1s and add state 1.
  clock()->SetSystemTimeMilliseconds(1000);
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info1.first, Not(Eq(kInvalidNextPageToken)));

  // Set time as 2s and add state 2.
  clock()->SetSystemTimeMilliseconds(2000);
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info2.first, Not(Eq(kInvalidNextPageToken)));

  // 1. Set time as 1hr1s.
  // 2. Call GetNextPage() on state 2. It should correctly invalidate the
  //    expired state 1.
  // 3. Then calling GetNextPage() on state 1 shouldn't get anything.
  clock()->SetSystemTimeMilliseconds(kDefaultResultStateTtlInMs + 1000);
  // page_result_info2's token (page_result_info2.first) should be found
  ICING_ASSERT_OK_AND_ASSIGN(page_result_info2,
                             result_state_manager.GetNextPage(
                                 page_result_info2.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  // We test the behavior by setting time back to 2s, to make sure the
  // invalidation of state 1 was done by the previous GetNextPage() instead of
  // the following GetNextPage().
  clock()->SetSystemTimeMilliseconds(2000);
  // page_result_info1's token (page_result_info1.first) shouldn't be found.
  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info1.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(ResultStateManagerTest,
       ShouldInvalidateExpiredTokensWhenGetNextPageOnItself) {
  auto [scored_document_hits1, document_protos1] = AddScoredDocuments(
      {/*document_id=*/0, /*document_id=*/1, /*document_id=*/2});
  auto [scored_document_hits2, document_protos2] = AddScoredDocuments(
      {/*document_id=*/3, /*document_id=*/4, /*document_id=*/5});

  ResultStateManager result_state_manager(
      /*max_total_hits=*/std::numeric_limits<int>::max(), document_store());

  // Set time as 1s and add state.
  clock()->SetSystemTimeMilliseconds(1000);
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info.first, Not(Eq(kInvalidNextPageToken)));

  // 1. Set time as 1hr1s.
  // 2. Then calling GetNextPage() on the state shouldn't get anything.
  clock()->SetSystemTimeMilliseconds(kDefaultResultStateTtlInMs + 1000);
  // page_result_info's token (page_result_info.first) shouldn't be found.
  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(ResultStateManagerTest, ShouldInvalidateOneToken) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store().Put(CreateDocument(/*id=*/1)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store().Put(CreateDocument(/*id=*/2)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store().Put(CreateDocument(/*id=*/3)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id4,
                             document_store().Put(CreateDocument(/*id=*/4)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id5,
                             document_store().Put(CreateDocument(/*id=*/5)));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id6,
                             document_store().Put(CreateDocument(/*id=*/6)));
  std::vector<ScoredDocumentHit> scored_document_hits1 = {
      {document_id1, kSectionIdMaskNone, /*score=*/1},
      {document_id2, kSectionIdMaskNone, /*score=*/1},
      {document_id3, kSectionIdMaskNone, /*score=*/1}};
  std::vector<ScoredDocumentHit> scored_document_hits2 = {
      {document_id4, kSectionIdMaskNone, /*score=*/1},
      {document_id5, kSectionIdMaskNone, /*score=*/1},
      {document_id6, kSectionIdMaskNone, /*score=*/1}};

  ResultStateManager result_state_manager(
      /*max_total_hits=*/std::numeric_limits<int>::max(), document_store());

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // Invalidate first result state by the token.
  result_state_manager.InvalidateResultState(page_result_info1.first);

  // page_result_info1's token (page_result_info1.first) shouldn't be found
  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info1.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  // page_result_info2's token (page_result_info2.first) should still exist
  ICING_ASSERT_OK_AND_ASSIGN(page_result_info2,
                             result_state_manager.GetNextPage(
                                 page_result_info2.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  // Should get docs.
  ASSERT_THAT(page_result_info2.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info2.second.results.at(0).document(),
              EqualsProto(CreateDocument(/*id=*/5)));
}

TEST_F(ResultStateManagerTest, ShouldInvalidateAllTokens) {
  auto [scored_document_hits1, document_protos1] = AddScoredDocuments(
      {/*document_id=*/0, /*document_id=*/1, /*document_id=*/2});
  auto [scored_document_hits2, document_protos2] = AddScoredDocuments(
      {/*document_id=*/3, /*document_id=*/4, /*document_id=*/5});

  ResultStateManager result_state_manager(
      /*max_total_hits=*/std::numeric_limits<int>::max(), document_store());

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  result_state_manager.InvalidateAllResultStates();

  // page_result_info1's token (page_result_info1.first) shouldn't be found
  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info1.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  // page_result_info2's token (page_result_info2.first) shouldn't be found
  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info2.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(ResultStateManagerTest, ShouldRemoveOldestResultState) {
  auto [scored_document_hits1, document_protos1] =
      AddScoredDocuments({/*document_id=*/0, /*document_id=*/1});
  auto [scored_document_hits2, document_protos2] =
      AddScoredDocuments({/*document_id=*/2, /*document_id=*/3});
  auto [scored_document_hits3, document_protos3] =
      AddScoredDocuments({/*document_id=*/4, /*document_id=*/5});

  ResultStateManager result_state_manager(/*max_total_hits=*/2,
                                          document_store());

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // Adding state 3 should cause state 1 to be removed.
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info3,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits3), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info1.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info2,
                             result_state_manager.GetNextPage(
                                 page_result_info2.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info2.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info2.second.results.at(0).document(),
              EqualsProto(document_protos2.at(1)));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info3,
                             result_state_manager.GetNextPage(
                                 page_result_info3.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info3.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info3.second.results.at(0).document(),
              EqualsProto(document_protos3.at(1)));
}

TEST_F(ResultStateManagerTest,
       InvalidatedResultStateShouldDecreaseCurrentHitsCount) {
  auto [scored_document_hits1, document_protos1] =
      AddScoredDocuments({/*document_id=*/0, /*document_id=*/1});
  auto [scored_document_hits2, document_protos2] =
      AddScoredDocuments({/*document_id=*/2, /*document_id=*/3});
  auto [scored_document_hits3, document_protos3] =
      AddScoredDocuments({/*document_id=*/4, /*document_id=*/5});

  // Add the first three states. Remember, the first page for each result state
  // won't be cached (since it is returned immediately from
  // CacheAndRetrieveFirstPage). Each result state has a page size of 1 and a
  // result set of 2 hits. So each result will take up one hit of our three hit
  // budget.
  ResultStateManager result_state_manager(/*max_total_hits=*/3,
                                          document_store());

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info3,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits3), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // Invalidates state 2, so that the number of hits current cached should be
  // decremented to 2.
  result_state_manager.InvalidateResultState(page_result_info2.first);

  // If invalidating state 2 correctly decremented the current hit count to 2,
  // then adding state 4 should still be within our budget and no other result
  // states should be evicted.
  auto [scored_document_hits4, document_protos4] =
      AddScoredDocuments({/*document_id=*/6, /*document_id=*/7});
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info4,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits4), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info1,
                             result_state_manager.GetNextPage(
                                 page_result_info1.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info1.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info1.second.results.at(0).document(),
              EqualsProto(document_protos1.at(1)));

  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info2.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info3,
                             result_state_manager.GetNextPage(
                                 page_result_info3.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info3.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info3.second.results.at(0).document(),
              EqualsProto(document_protos3.at(1)));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info4,
                             result_state_manager.GetNextPage(
                                 page_result_info4.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info4.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info4.second.results.at(0).document(),
              EqualsProto(document_protos4.at(1)));
}

TEST_F(ResultStateManagerTest,
       InvalidatedAllResultStatesShouldResetCurrentHitCount) {
  auto [scored_document_hits1, document_protos1] =
      AddScoredDocuments({/*document_id=*/0, /*document_id=*/1});
  auto [scored_document_hits2, document_protos2] =
      AddScoredDocuments({/*document_id=*/2, /*document_id=*/3});
  auto [scored_document_hits3, document_protos3] =
      AddScoredDocuments({/*document_id=*/4, /*document_id=*/5});

  // Add the first three states. Remember, the first page for each result state
  // won't be cached (since it is returned immediately from
  // CacheAndRetrieveFirstPage). Each result state has a page size of 1 and a
  // result set of 2 hits. So each result will take up one hit of our three hit
  // budget.
  ResultStateManager result_state_manager(/*max_total_hits=*/3,
                                          document_store());

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info3,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits3), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // Invalidates all states so that the current hit count will be 0.
  result_state_manager.InvalidateAllResultStates();

  // If invalidating all states correctly reset the current hit count to 0,
  // then adding state 4, 5, 6 should still be within our budget and no other
  // result states should be evicted.
  auto [scored_document_hits4, document_protos4] =
      AddScoredDocuments({/*document_id=*/6, /*document_id=*/7});
  auto [scored_document_hits5, document_protos5] =
      AddScoredDocuments({/*document_id=*/8, /*document_id=*/9});
  auto [scored_document_hits6, document_protos6] =
      AddScoredDocuments({/*document_id=*/10, /*document_id=*/11});

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info4,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits4), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info5,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits5), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info6,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits6), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info1.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info2.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info3.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info4,
                             result_state_manager.GetNextPage(
                                 page_result_info4.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info4.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info4.second.results.at(0).document(),
              EqualsProto(document_protos4.at(1)));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info5,
                             result_state_manager.GetNextPage(
                                 page_result_info5.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info5.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info5.second.results.at(0).document(),
              EqualsProto(document_protos5.at(1)));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info6,
                             result_state_manager.GetNextPage(
                                 page_result_info6.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info6.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info6.second.results.at(0).document(),
              EqualsProto(document_protos6.at(1)));
}

TEST_F(
    ResultStateManagerTest,
    InvalidatedResultStateShouldDecreaseCurrentHitsCountByExactStateHitCount) {
  auto [scored_document_hits1, document_protos1] =
      AddScoredDocuments({/*document_id=*/0, /*document_id=*/1});
  auto [scored_document_hits2, document_protos2] =
      AddScoredDocuments({/*document_id=*/2, /*document_id=*/3});
  auto [scored_document_hits3, document_protos3] =
      AddScoredDocuments({/*document_id=*/4, /*document_id=*/5});

  // Add the first three states. Remember, the first page for each result state
  // won't be cached (since it is returned immediately from
  // CacheAndRetrieveFirstPage). Each result state has a page size of 1 and a
  // result set of 2 hits. So each result will take up one hit of our three hit
  // budget.
  ResultStateManager result_state_manager(/*max_total_hits=*/3,
                                          document_store());

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info3,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits3), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // Invalidates state 2, so that the number of hits current cached should be
  // decremented to 2.
  result_state_manager.InvalidateResultState(page_result_info2.first);

  // If invalidating state 2 correctly decremented the current hit count to 2,
  // then adding state 4 should still be within our budget and no other result
  // states should be evicted.
  auto [scored_document_hits4, document_protos4] =
      AddScoredDocuments({/*document_id=*/6, /*document_id=*/7});
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info4,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits4), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // If invalidating result state 2 correctly decremented the current hit count
  // to 2 and adding state 4 correctly incremented it to 3, then adding this
  // result state should trigger the eviction of state 1.
  auto [scored_document_hits5, document_protos5] =
      AddScoredDocuments({/*document_id=*/8, /*document_id=*/9});
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info5,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits5), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info1.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info2.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info3,
                             result_state_manager.GetNextPage(
                                 page_result_info3.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info3.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info3.second.results.at(0).document(),
              EqualsProto(document_protos3.at(1)));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info4,
                             result_state_manager.GetNextPage(
                                 page_result_info4.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info4.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info4.second.results.at(0).document(),
              EqualsProto(document_protos4.at(1)));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info5,
                             result_state_manager.GetNextPage(
                                 page_result_info5.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info5.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info5.second.results.at(0).document(),
              EqualsProto(document_protos5.at(1)));
}

TEST_F(ResultStateManagerTest, GetNextPageShouldDecreaseCurrentHitsCount) {
  auto [scored_document_hits1, document_protos1] =
      AddScoredDocuments({/*document_id=*/0, /*document_id=*/1});
  auto [scored_document_hits2, document_protos2] =
      AddScoredDocuments({/*document_id=*/2, /*document_id=*/3});
  auto [scored_document_hits3, document_protos3] =
      AddScoredDocuments({/*document_id=*/4, /*document_id=*/5});

  // Add the first three states. Remember, the first page for each result state
  // won't be cached (since it is returned immediately from
  // CacheAndRetrieveFirstPage). Each result state has a page size of 1 and a
  // result set of 2 hits. So each result will take up one hit of our three hit
  // budget.
  ResultStateManager result_state_manager(/*max_total_hits=*/3,
                                          document_store());

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info3,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits3), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // GetNextPage for result state 1 should return its result and decrement the
  // number of cached hits to 2.
  ICING_ASSERT_OK_AND_ASSIGN(page_result_info1,
                             result_state_manager.GetNextPage(
                                 page_result_info1.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info1.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info1.second.results.at(0).document(),
              EqualsProto(document_protos1.at(1)));

  // If retrieving the next page for result state 1 correctly decremented the
  // current hit count to 2, then adding state 4 should still be within our
  // budget and no other result states should be evicted.
  auto [scored_document_hits4, document_protos4] =
      AddScoredDocuments({/*document_id=*/6, /*document_id=*/7});
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info4,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits4), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info1.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info2,
                             result_state_manager.GetNextPage(
                                 page_result_info2.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info2.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info2.second.results.at(0).document(),
              EqualsProto(document_protos2.at(1)));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info3,
                             result_state_manager.GetNextPage(
                                 page_result_info3.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info3.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info3.second.results.at(0).document(),
              EqualsProto(document_protos3.at(1)));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info4,
                             result_state_manager.GetNextPage(
                                 page_result_info4.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info4.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info4.second.results.at(0).document(),
              EqualsProto(document_protos4.at(1)));
}

TEST_F(ResultStateManagerTest,
       GetNextPageShouldDecreaseCurrentHitsCountByExactlyOnePage) {
  auto [scored_document_hits1, document_protos1] =
      AddScoredDocuments({/*document_id=*/0, /*document_id=*/1});
  auto [scored_document_hits2, document_protos2] =
      AddScoredDocuments({/*document_id=*/2, /*document_id=*/3});
  auto [scored_document_hits3, document_protos3] =
      AddScoredDocuments({/*document_id=*/4, /*document_id=*/5});

  // Add the first three states. Remember, the first page for each result state
  // won't be cached (since it is returned immediately from
  // CacheAndRetrieveFirstPage). Each result state has a page size of 1 and a
  // result set of 2 hits. So each result will take up one hit of our three hit
  // budget.
  ResultStateManager result_state_manager(/*max_total_hits=*/3,
                                          document_store());

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info3,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits3), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // GetNextPage for result state 1 should return its result and decrement the
  // number of cached hits to 2.
  ICING_ASSERT_OK_AND_ASSIGN(page_result_info1,
                             result_state_manager.GetNextPage(
                                 page_result_info1.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info1.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info1.second.results.at(0).document(),
              EqualsProto(document_protos1.at(1)));

  // If retrieving the next page for result state 1 correctly decremented the
  // current hit count to 2, then adding state 4 should still be within our
  // budget and no other result states should be evicted.
  auto [scored_document_hits4, document_protos4] =
      AddScoredDocuments({/*document_id=*/6, /*document_id=*/7});
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info4,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits4), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // If retrieving the next page for result state 1 correctly decremented the
  // current hit count to 2 and adding state 4 correctly incremented it to 3,
  // then adding this result state should trigger the eviction of state 2.
  auto [scored_document_hits5, document_protos5] =
      AddScoredDocuments({/*document_id=*/8, /*document_id=*/9});
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info5,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits5), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info1.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info2.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info3,
                             result_state_manager.GetNextPage(
                                 page_result_info3.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info3.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info3.second.results.at(0).document(),
              EqualsProto(document_protos3.at(1)));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info4,
                             result_state_manager.GetNextPage(
                                 page_result_info4.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info4.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info4.second.results.at(0).document(),
              EqualsProto(document_protos4.at(1)));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info5,
                             result_state_manager.GetNextPage(
                                 page_result_info5.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info5.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info5.second.results.at(0).document(),
              EqualsProto(document_protos5.at(1)));
}

TEST_F(ResultStateManagerTest,
       AddingOverBudgetResultStateShouldEvictAllStates) {
  auto [scored_document_hits1, document_protos1] = AddScoredDocuments(
      {/*document_id=*/0, /*document_id=*/1, /*document_id=*/2});
  auto [scored_document_hits2, document_protos2] =
      AddScoredDocuments({/*document_id=*/3, /*document_id=*/4});

  // Add the first two states. Remember, the first page for each result state
  // won't be cached (since it is returned immediately from
  // CacheAndRetrieveFirstPage). Each result state has a page size of 1. So 3
  // hits will remain cached.
  ResultStateManager result_state_manager(/*max_total_hits=*/4,
                                          document_store());

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // Add a result state that is larger than the entire budget. This should
  // result in all previous result states being evicted, the first hit from
  // result state 3 being returned and the next four hits being cached (the last
  // hit should be dropped because it exceeds the max).
  auto [scored_document_hits3, document_protos3] = AddScoredDocuments(
      {/*document_id=*/5, /*document_id=*/6, /*document_id=*/7,
       /*document_id=*/8, /*document_id=*/9, /*document_id=*/10});
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info3,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits3), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));
  EXPECT_THAT(page_result_info3.first, Not(Eq(kInvalidNextPageToken)));

  // GetNextPage for result state 1 and 2 should return NOT_FOUND.
  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info1.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info2.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  // Only the next four results in state 3 should be retrievable.
  uint64_t next_page_token3 = page_result_info3.first;
  ICING_ASSERT_OK_AND_ASSIGN(
      page_result_info3,
      result_state_manager.GetNextPage(next_page_token3, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()));
  EXPECT_THAT(page_result_info3.first, Eq(next_page_token3));
  ASSERT_THAT(page_result_info3.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info3.second.results.at(0).document(),
              EqualsProto(document_protos3.at(1)));

  ICING_ASSERT_OK_AND_ASSIGN(
      page_result_info3,
      result_state_manager.GetNextPage(next_page_token3, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()));
  EXPECT_THAT(page_result_info3.first, Eq(next_page_token3));
  ASSERT_THAT(page_result_info3.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info3.second.results.at(0).document(),
              EqualsProto(document_protos3.at(2)));

  ICING_ASSERT_OK_AND_ASSIGN(
      page_result_info3,
      result_state_manager.GetNextPage(next_page_token3, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()));
  EXPECT_THAT(page_result_info3.first, Eq(next_page_token3));
  ASSERT_THAT(page_result_info3.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info3.second.results.at(0).document(),
              EqualsProto(document_protos3.at(3)));

  ICING_ASSERT_OK_AND_ASSIGN(
      page_result_info3,
      result_state_manager.GetNextPage(next_page_token3, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()));
  // The final document should have been dropped because it exceeded the budget,
  // so the next page token of the second last round should be
  // kInvalidNextPageToken.
  EXPECT_THAT(page_result_info3.first, Eq(kInvalidNextPageToken));
  ASSERT_THAT(page_result_info3.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info3.second.results.at(0).document(),
              EqualsProto(document_protos3.at(4)));

  // Double check that next_page_token3 is not retrievable anymore.
  EXPECT_THAT(
      result_state_manager.GetNextPage(next_page_token3, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()),
      StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(ResultStateManagerTest,
       AddingResultStateShouldEvictOverBudgetResultState) {
  // Add a result state that is larger than the entire budget. The entire result
  // state will still be cached
  auto [scored_document_hits1, document_protos1] = AddScoredDocuments(
      {/*document_id=*/0, /*document_id=*/1, /*document_id=*/2,
       /*document_id=*/3, /*document_id=*/4, /*document_id=*/5});

  ResultStateManager result_state_manager(/*max_total_hits=*/4,
                                          document_store());

  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits1), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // Add a result state. Because state2 + state1 is larger than the budget,
  // state1 should be evicted.
  auto [scored_document_hits2, document_protos2] =
      AddScoredDocuments({/*document_id=*/6, /*document_id=*/7});
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/1, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // state1 should have been evicted and state2 should still be retrievable.
  EXPECT_THAT(result_state_manager.GetNextPage(
                  page_result_info1.first, result_retriever(),
                  clock()->GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  ICING_ASSERT_OK_AND_ASSIGN(page_result_info2,
                             result_state_manager.GetNextPage(
                                 page_result_info2.first, result_retriever(),
                                 clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info2.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info2.second.results.at(0).document(),
              EqualsProto(document_protos2.at(1)));
}

TEST_F(ResultStateManagerTest,
       AddingResultStateShouldNotTruncatedAfterFirstPage) {
  // Add a result state that is larger than the entire budget, but within the
  // entire budget after the first page. The entire result state will still be
  // cached and not truncated.
  auto [scored_document_hits, document_protos] = AddScoredDocuments(
      {/*document_id=*/0, /*document_id=*/1, /*document_id=*/2,
       /*document_id=*/3, /*document_id=*/4});

  ResultStateManager result_state_manager(/*max_total_hits=*/4,
                                          document_store());

  // The 5 input scored document hits will not be truncated. The first page of
  // two hits will be returned immediately and the other three hits will fit
  // within our caching budget.
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits), /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/2, ResultSpecProto::NAMESPACE),
          document_store(), result_retriever(),
          clock()->GetSystemTimeMilliseconds()));

  // First page, 2 results
  ASSERT_THAT(page_result_info1.second.results, SizeIs(2));
  EXPECT_THAT(page_result_info1.second.results.at(0).document(),
              EqualsProto(document_protos.at(0)));
  EXPECT_THAT(page_result_info1.second.results.at(1).document(),
              EqualsProto(document_protos.at(1)));

  uint64_t next_page_token = page_result_info1.first;

  // Second page, 2 results.
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info2,
      result_state_manager.GetNextPage(next_page_token, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info2.second.results, SizeIs(2));
  EXPECT_THAT(page_result_info2.second.results.at(0).document(),
              EqualsProto(document_protos.at(2)));
  EXPECT_THAT(page_result_info2.second.results.at(1).document(),
              EqualsProto(document_protos.at(3)));

  // Third page, 1 result.
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info3,
      result_state_manager.GetNextPage(next_page_token, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info3.second.results, SizeIs(1));
  EXPECT_THAT(page_result_info3.second.results.at(0).document(),
              EqualsProto(document_protos.at(4)));

  // Fourth page, 0 results.
  EXPECT_THAT(
      result_state_manager.GetNextPage(next_page_token, result_retriever(),
                                       clock()->GetSystemTimeMilliseconds()),
      StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

}  // namespace
}  // namespace lib
}  // namespace icing
