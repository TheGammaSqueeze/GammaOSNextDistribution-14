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

#include <algorithm>
#include <optional>
#include <thread>  // NOLINT

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/portable/equals-proto.h"
#include "icing/result/page-result.h"
#include "icing/result/result-retriever-v2.h"
#include "icing/result/result-state-manager.h"
#include "icing/schema/schema-store.h"
#include "icing/scoring/priority-queue-scored-document-hits-ranker.h"
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

using ::testing::Eq;
using ::testing::Ge;
using ::testing::Not;
using ::testing::SizeIs;
using PageResultInfo = std::pair<uint64_t, PageResult>;

ResultSpecProto CreateResultSpec(int num_per_page) {
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(num_per_page);
  return result_spec;
}

DocumentProto CreateDocument(int document_id) {
  return DocumentBuilder()
      .SetNamespace("namespace")
      .SetUri(std::to_string(document_id))
      .SetSchema("Document")
      .SetCreationTimestampMs(1574365086666 + document_id)
      .SetScore(document_id)
      .Build();
}

class ResultStateManagerThreadSafetyTest : public testing::Test {
 protected:
  ResultStateManagerThreadSafetyTest()
      : test_dir_(GetTestTempDir() + "/icing") {
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

  Filesystem filesystem_;
  const std::string test_dir_;
  std::unique_ptr<FakeClock> clock_;
  std::unique_ptr<LanguageSegmenter> language_segmenter_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<Normalizer> normalizer_;
  std::unique_ptr<DocumentStore> document_store_;
  std::unique_ptr<ResultRetrieverV2> result_retriever_;
};

TEST_F(ResultStateManagerThreadSafetyTest,
       RequestSameResultStateSimultaneously) {
  // Create several threads to send GetNextPage requests with the same
  // ResultState.
  //
  // This test verifies the usage of ResultState per instance lock. Only one
  // thread is allowed to access ResultState, so there should be no crash and
  // the result documents in a single page should be continuous (i.e. no
  // interleaf).

  // Prepare documents.
  constexpr int kNumDocuments = 10000;
  std::vector<ScoredDocumentHit> scored_document_hits;
  for (int i = 0; i < kNumDocuments; ++i) {
    // Put a document with id and score = i.
    ICING_ASSERT_OK(document_store_->Put(CreateDocument(/*document_id=*/i)));
    scored_document_hits.push_back(
        ScoredDocumentHit(/*document_id=*/i, kSectionIdMaskNone, /*score=*/i));
  }

  constexpr int kNumPerPage = 100;
  ResultStateManager result_state_manager(/*max_total_hits=*/kNumDocuments,
                                          *document_store_);

  // Retrieve the first page.
  // Documents are ordered by score *ascending*, so the first page should
  // contain documents with scores [0, 1, 2, ..., kNumPerPage - 1].
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits), /*is_descending=*/false),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(kNumPerPage), *document_store_, *result_retriever_,
          clock_->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info1.second.results, SizeIs(kNumPerPage));
  for (int i = 0; i < kNumPerPage; ++i) {
    ASSERT_THAT(page_result_info1.second.results[i].score(), Eq(i));
  }

  uint64_t next_page_token = page_result_info1.first;
  ASSERT_THAT(next_page_token, Not(Eq(kInvalidNextPageToken)));

  // Create kNumThreads threads to call GetNextPage() with the same token at the
  // same time. Each thread should get a valid result.
  // Use page_results to store the result.
  constexpr int kNumThreads = 50;
  std::vector<std::optional<PageResultInfo>> page_results(kNumThreads);
  auto callable = [&](int thread_id) {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<ResultRetrieverV2> result_retriever,
        ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                  language_segmenter_.get(),
                                  normalizer_.get()));
    ICING_ASSERT_OK_AND_ASSIGN(
        PageResultInfo page_result_info,
        result_state_manager.GetNextPage(next_page_token, *result_retriever,
                                         clock_->GetSystemTimeMilliseconds()));
    page_results[thread_id] =
        std::make_optional<PageResultInfo>(std::move(page_result_info));
  };

  // Spawn threads for GetNextPage().
  std::vector<std::thread> thread_objs;
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs.emplace_back(callable, /*thread_id=*/i);
  }

  // Join threads.
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs[i].join();
    EXPECT_THAT(page_results[i], Not(Eq(std::nullopt)));
    EXPECT_THAT(page_results[i]->second.results, SizeIs(kNumPerPage));
  }

  // Since we have per instance lock for ResultState, only one thread is allowed
  // to access ResultState at a moment. Therefore, every thread should get
  // continuous scores instead of interleaved scores, regardless of the
  // execution order. IOW, within a particular page the scores of all results
  // should be ordered as: [N, N+1, N+2, N+3, ...] where N is dependent on the
  // execution order. Also there should be no crash.
  std::vector<int> first_doc_scores;
  for (const auto& page_result_info : page_results) {
    first_doc_scores.push_back(page_result_info->second.results[0].score());
    for (int i = 1; i < kNumPerPage; ++i) {
      EXPECT_THAT(page_result_info->second.results[i].score(),
                  Eq(page_result_info->second.results[i - 1].score() + 1));
    }
  }

  // Verify all first doc scores of page results are correct. Should be
  // kNumPerPage * 1, kNumPerPage * 2, ..., etc.
  // Note: the first score of the first page retrieved via GetNextPage should be
  // kNumPerPage because the *actual* first page with first score = 0 was
  // retrieved during CacheAndRetrieveFirstPage.
  std::sort(first_doc_scores.begin(), first_doc_scores.end());
  for (int i = 0; i < kNumThreads; ++i) {
    EXPECT_THAT(first_doc_scores[i], Eq(kNumPerPage * (i + 1)));
  }
}

TEST_F(ResultStateManagerThreadSafetyTest, InvalidateResultStateWhileUsing) {
  // Create several threads to send GetNextPage requests with the same
  // ResultState and another single thread to invalidate this ResultState.
  //
  // This test verifies the usage of std::shared_ptr. Even after invalidating
  // the original copy of std::shared_ptr in the cache, the ResultState instance
  // should be still valid and no crash should occur in threads that are still
  // holding a copy of std::shared_ptr pointing to the same ResultState
  // instance.

  // Prepare documents.
  constexpr int kNumDocuments = 10000;
  std::vector<ScoredDocumentHit> scored_document_hits;
  for (int i = 0; i < kNumDocuments; ++i) {
    // Put a document with id and score = i.
    ICING_ASSERT_OK(document_store_->Put(CreateDocument(/*document_id=*/i)));
    scored_document_hits.push_back(
        ScoredDocumentHit(/*document_id=*/i, kSectionIdMaskNone, /*score=*/i));
  }

  constexpr int kNumPerPage = 100;
  ResultStateManager result_state_manager(/*max_total_hits=*/kNumDocuments,
                                          *document_store_);

  // Retrieve the first page.
  // Documents are ordered by score *ascending*, so the first page should
  // contain documents with scores [0, 1, 2, ..., kNumPerPage - 1].
  ICING_ASSERT_OK_AND_ASSIGN(
      PageResultInfo page_result_info1,
      result_state_manager.CacheAndRetrieveFirstPage(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits), /*is_descending=*/false),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(kNumPerPage), *document_store_, *result_retriever_,
          clock_->GetSystemTimeMilliseconds()));
  ASSERT_THAT(page_result_info1.second.results, SizeIs(kNumPerPage));
  for (int i = 0; i < kNumPerPage; ++i) {
    ASSERT_THAT(page_result_info1.second.results[i].score(), Eq(i));
  }

  uint64_t next_page_token = page_result_info1.first;
  ASSERT_THAT(next_page_token, Not(Eq(kInvalidNextPageToken)));

  // Create kNumThreads threads to call GetNextPage() with the same token at the
  // same time. The ResultState might have been invalidated, so it is normal to
  // get NOT_FOUND error.
  // Use page_results to store the result.
  constexpr int kNumThreads = 50;
  std::vector<std::optional<PageResultInfo>> page_results(kNumThreads);
  auto callable = [&](int thread_id) {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<ResultRetrieverV2> result_retriever,
        ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                  language_segmenter_.get(),
                                  normalizer_.get()));

    libtextclassifier3::StatusOr<PageResultInfo> page_result_info_or =
        result_state_manager.GetNextPage(next_page_token, *result_retriever,
                                         clock_->GetSystemTimeMilliseconds());
    if (page_result_info_or.ok()) {
      page_results[thread_id] = std::make_optional<PageResultInfo>(
          std::move(page_result_info_or).ValueOrDie());
    } else {
      EXPECT_THAT(page_result_info_or,
                  StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
    }
  };

  // Spawn threads for GetNextPage().
  std::vector<std::thread> thread_objs;
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs.emplace_back(callable, /*thread_id=*/i);
  }

  // Spawn another single thread to invalidate the ResultState.
  std::thread invalidating_thread([&]() -> void {
    result_state_manager.InvalidateResultState(next_page_token);
  });

  // Join threads.
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs[i].join();
    if (page_results[i] != std::nullopt) {
      EXPECT_THAT(page_results[i]->second.results, SizeIs(kNumPerPage));
    }
  }
  invalidating_thread.join();

  // Threads fetching ResultState before invalidation will get normal results,
  // while others will get NOT_FOUND error.
  std::vector<int> first_doc_scores;
  for (const auto& page_result_info : page_results) {
    if (page_result_info == std::nullopt) {
      continue;
    }

    first_doc_scores.push_back(page_result_info->second.results[0].score());
    for (int i = 1; i < kNumPerPage; ++i) {
      EXPECT_THAT(page_result_info->second.results[i].score(),
                  Eq(page_result_info->second.results[i - 1].score() + 1));
    }
  }

  // Verify all first doc scores of page results are correct. Should be
  // kNumPerPage * 1, kNumPerPage * 2, ..., etc.
  std::sort(first_doc_scores.begin(), first_doc_scores.end());
  for (int i = 0; i < first_doc_scores.size(); ++i) {
    EXPECT_THAT(first_doc_scores[i], Eq(kNumPerPage * (i + 1)));
  }

  // Verify num_total_hits should be decremented correctly.
  EXPECT_THAT(result_state_manager.num_total_hits(), Eq(0));
}

TEST_F(ResultStateManagerThreadSafetyTest, MultipleResultStates) {
  // Create several threads to send GetNextPage requests with different
  // ResultStates.
  //
  // This test verifies each ResultState should work independently and correctly
  // with each thread. Also it verifies there should be no race condition for
  // num_total_hits, which will be incremented/decremented by multiple threads.

  // Prepare documents.
  constexpr int kNumDocuments = 2000;
  std::vector<ScoredDocumentHit> scored_document_hits;
  for (int i = 0; i < kNumDocuments; ++i) {
    // Put a document with id and score = i.
    ICING_ASSERT_OK(document_store_->Put(CreateDocument(/*document_id=*/i)));
    scored_document_hits.push_back(
        ScoredDocumentHit(/*document_id=*/i, kSectionIdMaskNone, /*score=*/i));
  }

  constexpr int kNumThreads = 50;
  constexpr int kNumPerPage = 30;
  ResultStateManager result_state_manager(
      /*max_total_hits=*/kNumDocuments * kNumThreads, *document_store_);

  // Create kNumThreads threads to:
  // - Call CacheAndRetrieveFirstPage() once to create its own ResultState.
  // - Call GetNextPage() on its own ResultState for thread_id times.
  //
  // Each thread will get (thread_id + 1) pages, i.e. kNumPerPage *
  // (thread_id + 1) docs.
  ASSERT_THAT(kNumDocuments, Ge(kNumPerPage * kNumThreads));
  auto callable = [&](int thread_id) {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<ResultRetrieverV2> result_retriever,
        ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                  language_segmenter_.get(),
                                  normalizer_.get()));

    // Retrieve the first page.
    // Documents are ordered by score *ascending*, so the first page should
    // contain documents with scores [0, 1, 2, ..., kNumPerPage - 1].
    std::vector<ScoredDocumentHit> scored_document_hits_copy(
        scored_document_hits);
    ICING_ASSERT_OK_AND_ASSIGN(
        PageResultInfo page_result_info1,
        result_state_manager.CacheAndRetrieveFirstPage(
            std::make_unique<
                PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
                std::move(scored_document_hits_copy), /*is_descending=*/false),
            /*parent_adjustment_info=*/nullptr,
            /*child_adjustment_info=*/nullptr, CreateResultSpec(kNumPerPage),
            *document_store_, *result_retriever,
            clock_->GetSystemTimeMilliseconds()));
    EXPECT_THAT(page_result_info1.second.results, SizeIs(kNumPerPage));
    for (int i = 0; i < kNumPerPage; ++i) {
      EXPECT_THAT(page_result_info1.second.results[i].score(), Eq(i));
    }

    uint64_t next_page_token = page_result_info1.first;
    ASSERT_THAT(next_page_token, Not(Eq(kInvalidNextPageToken)));

    // Retrieve some of the subsequent pages. We use thread_id as how many
    // subsequent pages should be retrieved (how many times GetNextPage should
    // be called) for each thread in order to:
    // - Vary the number of pages that we're retrieving in each thread.
    // - Still make the total number of hits remaining (num_total_hits) a
    //   predictable number.
    // Then, including the first page (retrieved by CacheAndRetrieveFirstPage),
    // each thread should retrieve 1, 2, 3, ..., kNumThreads pages.
    int num_subsequent_pages_to_retrieve = thread_id;
    for (int i = 0; i < num_subsequent_pages_to_retrieve; ++i) {
      ICING_ASSERT_OK_AND_ASSIGN(PageResultInfo page_result_info,
                                 result_state_manager.GetNextPage(
                                     next_page_token, *result_retriever,
                                     clock_->GetSystemTimeMilliseconds()));
      EXPECT_THAT(page_result_info.second.results, SizeIs(kNumPerPage));
      for (int j = 0; j < kNumPerPage; ++j) {
        EXPECT_THAT(page_result_info.second.results[j].score(),
                    Eq(kNumPerPage * (i + 1) + j));
      }
    }
  };

  // Spawn threads.
  std::vector<std::thread> thread_objs;
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs.emplace_back(callable, /*thread_id=*/i);
  }

  // Join threads.
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs[i].join();
  }

  // There will be kNumThreads * kNumDocuments ScoredDocumentHits being created
  // in the beginning, and kNumPerPage * (1 + 2 + ... + kNumThreads) docs should
  // be returned after retrieval, since each thread should retrieve 1, 2, 3,
  // ..., kNumThreads pages. Thus, all retrieved ScoredDocumentHits should be
  // removed from the cache and num_total_hits should be decremented correctly.
  int expected_remaining_hits =
      kNumThreads * kNumDocuments -
      kNumPerPage * (kNumThreads * (kNumThreads + 1) / 2);
  EXPECT_THAT(result_state_manager.num_total_hits(),
              Eq(expected_remaining_hits));
}

}  // namespace
}  // namespace lib
}  // namespace icing
