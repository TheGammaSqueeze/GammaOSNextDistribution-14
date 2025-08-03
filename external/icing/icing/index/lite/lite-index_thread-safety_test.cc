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

#include <array>
#include <string>
#include <thread>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/index/lite/lite-index.h"
#include "icing/index/term-id-codec.h"
#include "icing/schema/section.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::Ge;
using ::testing::Le;
using ::testing::SizeIs;

// These tests cover concurrent FetchHits operations, as well as interleaving
// AddHit and FetchHits operations. Other usages of the LiteIndex other than
// these scenarios are not guaranteed with to be thread-safe as the LiteIndex is
// go/thread-compatible.
class LiteIndexThreadSafetyTest : public testing::Test {
 protected:
  void SetUp() override {
    index_dir_ = GetTestTempDir() + "/test_dir";
    ASSERT_TRUE(filesystem_.CreateDirectoryRecursively(index_dir_.c_str()));

    std::string lite_index_file_name =
        index_dir_ + "/test_file.lite-idx-thread-safety.index";
    LiteIndex::Options options(lite_index_file_name,
                               /*hit_buffer_want_merge_bytes=*/1024 * 1024,
                               /*hit_buffer_sort_at_indexing=*/true,
                               /*hit_buffer_sort_threshold_bytes=*/64);
    ICING_ASSERT_OK_AND_ASSIGN(lite_index_,
                               LiteIndex::Create(options, &icing_filesystem_));

    ICING_ASSERT_OK_AND_ASSIGN(
        term_id_codec_,
        TermIdCodec::Create(
            IcingDynamicTrie::max_value_index(IcingDynamicTrie::Options()),
            IcingDynamicTrie::max_value_index(options.lexicon_options)));
  }

  void TearDown() override {
    term_id_codec_.reset();
    lite_index_.reset();
    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(index_dir_.c_str()));
  }

  std::string index_dir_;
  Filesystem filesystem_;
  IcingFilesystem icing_filesystem_;
  std::unique_ptr<LiteIndex> lite_index_;
  std::unique_ptr<TermIdCodec> term_id_codec_;
};

constexpr NamespaceId kNamespace0 = 0;
constexpr DocumentId kDocumentId0 = 0;
constexpr DocumentId kDocumentId1 = 1;
constexpr SectionId kSectionId0 = 1;
constexpr SectionId kSectionId1 = 0b11;

static constexpr std::array<std::string_view, 100> kCommonWords = {
    "the",   "and",      "for",    "that",     "this",        "with",
    "you",   "not",      "are",    "from",     "your",        "all",
    "have",  "new",      "more",   "was",      "will",        "home",
    "can",   "about",    "page",   "has",      "search",      "free",
    "but",   "our",      "one",    "other",    "information", "time",
    "they",  "site",     "may",    "what",     "which",       "their",
    "news",  "out",      "use",    "any",      "there",       "see",
    "only",  "his",      "when",   "contact",  "here",        "business",
    "who",   "web",      "also",   "now",      "help",        "get",
    "view",  "online",   "first",  "been",     "would",       "how",
    "were",  "services", "some",   "these",    "click",       "its",
    "like",  "service",  "than",   "find",     "price",       "date",
    "back",  "top",      "people", "had",      "list",        "name",
    "just",  "over",     "state",  "year",     "day",         "into",
    "email", "two",      "health", "world",    "next",        "used",
    "work",  "last",     "most",   "products", "music",       "buy",
    "data",  "make",     "them",   "should"};

TEST_F(LiteIndexThreadSafetyTest, SimultaneousFetchHits_singleTerm) {
  // Add some hits
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_tvi,
      lite_index_->InsertTerm("foo", TermMatchType::PREFIX, kNamespace0));

  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(foo_tvi, TviType::LITE));
  Hit doc_hit0(/*section_id=*/kSectionId0, /*document_id=*/kDocumentId0,
               Hit::kDefaultTermFrequency, /*is_in_prefix_section=*/false);
  Hit doc_hit1(/*section_id=*/kSectionId0, /*document_id=*/kDocumentId1,
               Hit::kDefaultTermFrequency, /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foo_term_id, doc_hit0));
  ICING_ASSERT_OK(lite_index_->AddHit(foo_term_id, doc_hit1));

  // Create kNumThreads threads to call lite_index_->FetchHits()
  // simultaneously. Each thread should get a valid result of 2 hits for the
  // term 'foo', and there should be no crash.
  constexpr int kNumThreads = 50;
  std::vector<std::vector<DocHitInfo>> hits(kNumThreads);
  auto callable = [&](int thread_id) {
    lite_index_->FetchHits(
        foo_term_id, kSectionIdMaskAll,
        /*only_from_prefix_sections=*/false,
        SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
        /*namespace_checker=*/nullptr, &hits[thread_id]);
  };
  // Spawn threads for FetchHits().
  std::vector<std::thread> thread_objs;
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs.emplace_back(callable, /*thread_id=*/i);
  }

  // Join threads and verify results
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs[i].join();
    EXPECT_THAT(
        hits[i],
        ElementsAre(
            EqualsDocHitInfo(kDocumentId1, std::vector<SectionId>{kSectionId0}),
            EqualsDocHitInfo(kDocumentId0,
                             std::vector<SectionId>{kSectionId0})));
  }
}

TEST_F(LiteIndexThreadSafetyTest, SimultaneousFetchHits_multipleTerms) {
  // Add two hits for each of the first 50 terms in kCommonWords.
  for (int i = 0; i < 50; ++i) {
    ICING_ASSERT_OK_AND_ASSIGN(
        uint32_t tvi,
        lite_index_->InsertTerm(std::string(kCommonWords[i]),
                                TermMatchType::PREFIX, kNamespace0));
    ICING_ASSERT_OK_AND_ASSIGN(uint32_t term_id,
                               term_id_codec_->EncodeTvi(tvi, TviType::LITE));
    Hit doc_hit0(/*section_id=*/kSectionId0, /*document_id=*/kDocumentId0,
                 Hit::kDefaultTermFrequency, /*is_in_prefix_section=*/false);
    Hit doc_hit1(/*section_id=*/kSectionId0, /*document_id=*/kDocumentId1,
                 Hit::kDefaultTermFrequency, /*is_in_prefix_section=*/false);
    ICING_ASSERT_OK(lite_index_->AddHit(term_id, doc_hit0));
    ICING_ASSERT_OK(lite_index_->AddHit(term_id, doc_hit1));
  }

  // Create kNumThreads threads to call lite_index_->FetchHits()
  // simultaneously. Each thread should get a valid result of 2 hits for each
  // term, and there should be no crash.
  constexpr int kNumThreads = 50;
  std::vector<std::vector<DocHitInfo>> hits(kNumThreads);
  auto callable = [&](int thread_id) {
    ICING_ASSERT_OK_AND_ASSIGN(
        uint32_t tvi,
        lite_index_->InsertTerm(std::string(kCommonWords[thread_id]),
                                TermMatchType::PREFIX, kNamespace0));
    ICING_ASSERT_OK_AND_ASSIGN(uint32_t term_id,
                               term_id_codec_->EncodeTvi(tvi, TviType::LITE));
    lite_index_->FetchHits(
        term_id, kSectionIdMaskAll,
        /*only_from_prefix_sections=*/false,
        SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
        /*namespace_checker=*/nullptr, &hits[thread_id]);
  };

  // Spawn threads for FetchHits().
  std::vector<std::thread> thread_objs;
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs.emplace_back(callable, /*thread_id=*/i);
  }

  // Join threads and verify results
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs[i].join();
    EXPECT_THAT(
        hits[i],
        ElementsAre(
            EqualsDocHitInfo(kDocumentId1, std::vector<SectionId>{kSectionId0}),
            EqualsDocHitInfo(kDocumentId0,
                             std::vector<SectionId>{kSectionId0})));
  }
}

TEST_F(LiteIndexThreadSafetyTest, SimultaneousAddHitAndFetchHits_singleTerm) {
  // Add some hits
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_tvi,
      lite_index_->InsertTerm("foo", TermMatchType::PREFIX, kNamespace0));

  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(foo_tvi, TviType::LITE));
  Hit doc_hit0(/*section_id=*/kSectionId0, /*document_id=*/kDocumentId0,
               Hit::kDefaultTermFrequency, /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foo_term_id, doc_hit0));

  // Create kNumThreads threads. Every even-numbered thread calls FetchHits and
  // every odd numbered thread calls AddHit.
  // Each AddHit operation adds the term 'foo' to a new section of the same doc.
  // Each query result should contain one hit, and there should be no crash.
  constexpr int kNumThreads = 50;
  std::vector<std::vector<DocHitInfo>> hits(kNumThreads);
  auto callable = [&](int thread_id) {
    if (thread_id % 2 == 0) {
      // Even-numbered thread calls FetchHits.
      lite_index_->FetchHits(
          foo_term_id, kSectionIdMaskAll,
          /*only_from_prefix_sections=*/false,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          /*namespace_checker=*/nullptr, &hits[thread_id]);
    } else {
      // Odd-numbered thread calls AddHit.
      Hit doc_hit(/*section_id=*/thread_id / 2, /*document_id=*/kDocumentId0,
                  Hit::kDefaultTermFrequency, /*is_in_prefix_section=*/false);
      ICING_ASSERT_OK(lite_index_->AddHit(foo_term_id, doc_hit));
    }
  };

  // Spawn threads.
  std::vector<std::thread> thread_objs;
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs.emplace_back(callable, /*thread_id=*/i);
  }

  // Join threads and verify results.
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs[i].join();
    // All AddHit operations add 'foo' to the same document, so there should
    // only be one DocHitInfo per run.
    if (i % 2 == 0) {
      EXPECT_THAT(hits[i], SizeIs(1));
      EXPECT_THAT(hits[i].back().document_id(), Eq(0));
    }
  }

  // After all threads have executed, hits should come from sections 0-24.
  std::vector<DocHitInfo> final_hits;
  lite_index_->FetchHits(
      foo_term_id, kSectionIdMaskAll,
      /*only_from_prefix_sections=*/false,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
      /*namespace_checker=*/nullptr, &final_hits);
  EXPECT_THAT(final_hits, SizeIs(1));
  EXPECT_THAT(final_hits.back().document_id(), Eq(0));
  // Section mask of sections 0-24.
  EXPECT_THAT(final_hits.back().hit_section_ids_mask(), Eq((1 << 25) - 1));
}

TEST_F(LiteIndexThreadSafetyTest,
       SimultaneousAddHitAndFetchHits_multipleTerms) {
  // Add the initial hit 'foo'.
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_tvi,
      lite_index_->InsertTerm("foo", TermMatchType::PREFIX, kNamespace0));

  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(foo_tvi, TviType::LITE));
  Hit doc_hit0(/*section_id=*/kSectionId0, /*document_id=*/kDocumentId0,
               Hit::kDefaultTermFrequency, /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foo_term_id, doc_hit0));

  // Create kNumThreads threads. Every even-numbered thread calls FetchHits and
  // every odd numbered thread calls AddHit.
  // Each AddHit operation adds a different term to a new doc.
  // Queries always search for the term 'foo' added above so there will always
  // be a hit. There should be no crash.
  constexpr int kNumThreads = 50;
  std::vector<std::vector<DocHitInfo>> hits(kNumThreads);
  auto callable = [&](int thread_id) {
    // Create new tvi and term_id for new term kCommonWords[thread_id].
    ICING_ASSERT_OK_AND_ASSIGN(
        uint32_t tvi,
        lite_index_->InsertTerm(std::string(kCommonWords[thread_id]),
                                TermMatchType::PREFIX, kNamespace0));
    ICING_ASSERT_OK_AND_ASSIGN(uint32_t term_id,
                               term_id_codec_->EncodeTvi(tvi, TviType::LITE));

    if (thread_id % 2 == 0) {
      // Even-numbered thread calls FetchHits.
      lite_index_->FetchHits(
          foo_term_id, kSectionIdMaskAll, /*only_from_prefix_sections=*/false,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          /*namespace_checker=*/nullptr, &hits[thread_id]);
    } else {
      // Odd-numbered thread calls AddHit.
      // AddHit to section 0 of a new doc.
      Hit doc_hit(/*section_id=*/kSectionId0, /*document_id=*/thread_id / 2,
                  Hit::kDefaultTermFrequency, /*is_in_prefix_section=*/false);
      ICING_ASSERT_OK(lite_index_->AddHit(term_id, doc_hit));
    }
  };

  // Spawn threads.
  std::vector<std::thread> thread_objs;
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs.emplace_back(callable, /*thread_id=*/i);
  }

  // Join threads and verify results. Queries always search for the term 'foo'
  // so there will always be a hit
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs[i].join();
    if (i % 2 == 0) {
      EXPECT_THAT(hits[i],
                  ElementsAre(EqualsDocHitInfo(
                      kDocumentId0, std::vector<SectionId>{kSectionId0})));
    }
  }
}

TEST_F(LiteIndexThreadSafetyTest, ManyAddHitAndOneFetchHits_multipleTerms) {
  // Add two hits for each of the first 20 terms in kCommonWords.
  for (int i = 0; i < 20; ++i) {
    ICING_ASSERT_OK_AND_ASSIGN(
        uint32_t tvi,
        lite_index_->InsertTerm(std::string(kCommonWords[i]),
                                TermMatchType::PREFIX, kNamespace0));
    ICING_ASSERT_OK_AND_ASSIGN(uint32_t term_id,
                               term_id_codec_->EncodeTvi(tvi, TviType::LITE));
    Hit doc_hit0(/*section_id=*/kSectionId0, /*document_id=*/kDocumentId0,
                 Hit::kDefaultTermFrequency, /*is_in_prefix_section=*/false);
    Hit doc_hit1(/*section_id=*/kSectionId1, /*document_id=*/kDocumentId0,
                 Hit::kDefaultTermFrequency, /*is_in_prefix_section=*/false);
    ICING_ASSERT_OK(lite_index_->AddHit(term_id, doc_hit0));
    ICING_ASSERT_OK(lite_index_->AddHit(term_id, doc_hit1));
  }

  // Create kNumThreads threads. Call one FetchHits operation after every 5
  // AddHit operations.
  // Each AddHit operation adds a different term to a new doc.
  // Queries always search for the term 'foo' added above so there will always
  // be a hit. There should be no crash.
  constexpr int kNumThreads = 100;
  std::vector<std::vector<DocHitInfo>> hits(kNumThreads);
  auto callable = [&](int thread_id) {
    // Create new tvi and term_id for new term kCommonWords[thread_id].
    ICING_ASSERT_OK_AND_ASSIGN(
        uint32_t tvi,
        lite_index_->InsertTerm(std::string(kCommonWords[thread_id / 5]),
                                TermMatchType::PREFIX, kNamespace0));
    ICING_ASSERT_OK_AND_ASSIGN(uint32_t term_id,
                               term_id_codec_->EncodeTvi(tvi, TviType::LITE));

    if (thread_id % 5 == 0) {
      // Call FetchHits on term kCommonWords[thread_id / 5]
      lite_index_->FetchHits(
          term_id, kSectionIdMaskAll,
          /*only_from_prefix_sections=*/false,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          /*namespace_checker=*/nullptr, &hits[thread_id]);
    } else {
      // Odd-numbered thread calls AddHit.
      // AddHit to section (thread_id % 5 + 1) of doc 0.
      Hit doc_hit(/*section_id=*/thread_id % 5 + 1,
                  /*document_id=*/kDocumentId0, Hit::kDefaultTermFrequency,
                  /*is_in_prefix_section=*/false);
      ICING_ASSERT_OK(lite_index_->AddHit(term_id, doc_hit));
    }
  };
  // Spawn threads.
  std::vector<std::thread> thread_objs;
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs.emplace_back(callable, /*thread_id=*/i);
  }

  // Join threads and verify FetchHits results.
  // Every query should see a hit in doc 0 sections 0 and 1. Additional hits
  // might also be found in sections 2-6 depending on thread execution order.
  for (int i = 0; i < kNumThreads; ++i) {
    thread_objs[i].join();
    if (i % 5 == 0) {
      EXPECT_THAT(hits[i], SizeIs(1));
      EXPECT_THAT(hits[i].back().document_id(), Eq(0));
      EXPECT_THAT(hits[i].back().hit_section_ids_mask(), Ge(0b11));
      EXPECT_THAT(hits[i].back().hit_section_ids_mask(), Le(0b1111111));
    }
  }
}

}  // namespace
}  // namespace lib
}  // namespace icing
