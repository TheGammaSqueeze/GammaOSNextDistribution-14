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

#include "icing/index/lite/lite-index.h"

#include <cstdint>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/hit/hit.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/lite/doc-hit-info-iterator-term-lite.h"
#include "icing/index/lite/lite-index-header.h"
#include "icing/index/lite/term-id-hit-pair.h"
#include "icing/index/term-id-codec.h"
#include "icing/legacy/index/icing-dynamic-trie.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema/section.h"
#include "icing/store/namespace-id.h"
#include "icing/testing/always-false-suggestion-result-checker-impl.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::IsFalse;
using ::testing::IsTrue;
using ::testing::Pointee;
using ::testing::SizeIs;

class LiteIndexTest : public testing::Test {
 protected:
  void SetUp() override {
    index_dir_ = GetTestTempDir() + "/test_dir";
    ASSERT_TRUE(filesystem_.CreateDirectoryRecursively(index_dir_.c_str()));
  }

  void TearDown() override {
    term_id_codec_.reset();
    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(index_dir_.c_str()));
  }

  std::string index_dir_;
  Filesystem filesystem_;
  IcingFilesystem icing_filesystem_;
  std::unique_ptr<TermIdCodec> term_id_codec_;
};

constexpr NamespaceId kNamespace0 = 0;

TEST_F(LiteIndexTest,
       LiteIndexFetchHits_sortAtQuerying_unsortedHitsBelowSortThreshold) {
  // Set up LiteIndex and TermIdCodec
  std::string lite_index_file_name = index_dir_ + "/test_file.lite-idx.index";
  // At 64 bytes the unsorted tail can contain a max of 8 TermHitPairs.
  LiteIndex::Options options(lite_index_file_name,
                             /*hit_buffer_want_merge_bytes=*/1024 * 1024,
                             /*hit_buffer_sort_at_indexing=*/false,
                             /*hit_buffer_sort_threshold_bytes=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LiteIndex> lite_index,
                             LiteIndex::Create(options, &icing_filesystem_));
  ICING_ASSERT_OK_AND_ASSIGN(
      term_id_codec_,
      TermIdCodec::Create(
          IcingDynamicTrie::max_value_index(IcingDynamicTrie::Options()),
          IcingDynamicTrie::max_value_index(options.lexicon_options)));

  // Add some hits
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_tvi,
      lite_index->InsertTerm("foo", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(foo_tvi, TviType::LITE));
  Hit foo_hit0(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  Hit foo_hit1(/*section_id=*/1, /*document_id=*/1, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, foo_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, foo_hit1));

  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t bar_tvi,
      lite_index->InsertTerm("bar", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t bar_term_id,
                             term_id_codec_->EncodeTvi(bar_tvi, TviType::LITE));
  Hit bar_hit0(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  Hit bar_hit1(/*section_id=*/1, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, bar_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, bar_hit1));

  // Check the total size and unsorted size of the hit buffer.
  EXPECT_THAT(lite_index, Pointee(SizeIs(4)));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedSize(), Eq(4));
  // Check that unsorted hits does not exceed the sort threshold.
  EXPECT_THAT(lite_index->HasUnsortedHitsExceedingSortThreshold(), IsFalse());

  // Query the LiteIndex
  std::vector<DocHitInfo> hits1;
  lite_index->FetchHits(
      foo_term_id, kSectionIdMaskAll,
      /*only_from_prefix_sections=*/false,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
      /*namespace_checker=*/nullptr, &hits1);
  EXPECT_THAT(hits1, SizeIs(1));
  EXPECT_THAT(hits1.back().document_id(), Eq(1));
  // Check that the hits are coming from section 0 and section 1.
  EXPECT_THAT(hits1.back().hit_section_ids_mask(), Eq(0b11));

  std::vector<DocHitInfo> hits2;
  AlwaysFalseSuggestionResultCheckerImpl always_false_suggestion_result_checker;
  lite_index->FetchHits(
      foo_term_id, kSectionIdMaskAll,
      /*only_from_prefix_sections=*/false,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
      &always_false_suggestion_result_checker, &hits2);
  // Check that no hits are returned because they get skipped by the namespace
  // checker.
  EXPECT_THAT(hits2, IsEmpty());

  // Check the total size and unsorted size of the hit buffer. Hits should be
  // sorted after querying LiteIndex.
  EXPECT_THAT(lite_index, Pointee(SizeIs(4)));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedSize(), Eq(0));
}

TEST_F(LiteIndexTest,
       LiteIndexFetchHits_sortAtIndexing_unsortedHitsBelowSortThreshold) {
  // Set up LiteIndex and TermIdCodec
  std::string lite_index_file_name = index_dir_ + "/test_file.lite-idx.index";
  // At 64 bytes the unsorted tail can contain a max of 8 TermHitPairs.
  // However note that in these tests we're unable to sort hits after
  // indexing, as sorting performed by the string-section-indexing-handler
  // after indexing all hits in an entire document, rather than after each
  // AddHits() operation.
  LiteIndex::Options options(lite_index_file_name,
                             /*hit_buffer_want_merge_bytes=*/1024 * 1024,
                             /*hit_buffer_sort_at_indexing=*/true,
                             /*hit_buffer_sort_threshold_bytes=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LiteIndex> lite_index,
                             LiteIndex::Create(options, &icing_filesystem_));
  ICING_ASSERT_OK_AND_ASSIGN(
      term_id_codec_,
      TermIdCodec::Create(
          IcingDynamicTrie::max_value_index(IcingDynamicTrie::Options()),
          IcingDynamicTrie::max_value_index(options.lexicon_options)));

  // Add some hits
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_tvi,
      lite_index->InsertTerm("foo", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(foo_tvi, TviType::LITE));
  Hit foo_hit0(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  Hit foo_hit1(/*section_id=*/1, /*document_id=*/1, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, foo_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, foo_hit1));

  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t bar_tvi,
      lite_index->InsertTerm("bar", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t bar_term_id,
                             term_id_codec_->EncodeTvi(bar_tvi, TviType::LITE));
  Hit bar_hit0(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  Hit bar_hit1(/*section_id=*/1, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, bar_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, bar_hit1));

  // Check the total size and unsorted size of the hit buffer.
  EXPECT_THAT(lite_index, Pointee(SizeIs(4)));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedSize(), Eq(4));
  // Check that unsorted hits does not exceed the sort threshold.
  EXPECT_THAT(lite_index->HasUnsortedHitsExceedingSortThreshold(), IsFalse());

  // Query the LiteIndex
  std::vector<DocHitInfo> hits1;
  lite_index->FetchHits(
      foo_term_id, kSectionIdMaskAll,
      /*only_from_prefix_sections=*/false,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
      /*namespace_checker=*/nullptr, &hits1);
  EXPECT_THAT(hits1, SizeIs(1));
  EXPECT_THAT(hits1.back().document_id(), Eq(1));
  // Check that the hits are coming from section 0 and section 1.
  EXPECT_THAT(hits1.back().hit_section_ids_mask(), Eq(0b11));

  std::vector<DocHitInfo> hits2;
  AlwaysFalseSuggestionResultCheckerImpl always_false_suggestion_result_checker;
  lite_index->FetchHits(
      foo_term_id, kSectionIdMaskAll,
      /*only_from_prefix_sections=*/false,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
      &always_false_suggestion_result_checker, &hits2);
  // Check that no hits are returned because they get skipped by the namespace
  // checker.
  EXPECT_THAT(hits2, IsEmpty());

  // Check the total size and unsorted size of the hit buffer. Hits should be
  // still unsorted after querying LiteIndex because the HitBuffer unsorted size
  // is still below the sort threshold, and we've enabled sort_at_indexing.
  EXPECT_THAT(lite_index, Pointee(SizeIs(4)));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedSize(), Eq(4));
}

TEST_F(
    LiteIndexTest,
    LiteIndexFetchHits_sortAtQuerying_unsortedHitsExceedingSortAtIndexThreshold) {
  // Set up LiteIndex and TermIdCodec
  std::string lite_index_file_name = index_dir_ + "/test_file.lite-idx.index";
  // At 64 bytes the unsorted tail can contain a max of 8 TermHitPairs.
  // However note that in these tests we're unable to sort hits after
  // indexing, as sorting performed by the string-section-indexing-handler
  // after indexing all hits in an entire document, rather than after each
  // AddHits() operation.
  LiteIndex::Options options(lite_index_file_name,
                             /*hit_buffer_want_merge_bytes=*/1024 * 1024,
                             /*hit_buffer_sort_at_indexing=*/false,
                             /*hit_buffer_sort_threshold_bytes=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LiteIndex> lite_index,
                             LiteIndex::Create(options, &icing_filesystem_));
  ICING_ASSERT_OK_AND_ASSIGN(
      term_id_codec_,
      TermIdCodec::Create(
          IcingDynamicTrie::max_value_index(IcingDynamicTrie::Options()),
          IcingDynamicTrie::max_value_index(options.lexicon_options)));

  // Create 4 hits for docs 0-2, and 2 hits for doc 3 -- 14 in total
  // Doc 0
  Hit doc0_hit0(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc0_hit1(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc0_hit2(/*section_id=*/1, /*document_id=*/0, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc0_hit3(/*section_id=*/2, /*document_id=*/0, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  // Doc 1
  Hit doc1_hit0(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc1_hit1(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc1_hit2(/*section_id=*/1, /*document_id=*/1, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc1_hit3(/*section_id=*/2, /*document_id=*/1, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  // Doc 2
  Hit doc2_hit0(/*section_id=*/0, /*document_id=*/2, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc2_hit1(/*section_id=*/0, /*document_id=*/2, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc2_hit2(/*section_id=*/1, /*document_id=*/2, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc2_hit3(/*section_id=*/2, /*document_id=*/2, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  // Doc 3
  Hit doc3_hit0(/*section_id=*/0, /*document_id=*/3, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc3_hit1(/*section_id=*/0, /*document_id=*/3, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);

  // Create terms
  // Foo
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_tvi,
      lite_index->InsertTerm("foo", TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(foo_tvi, TviType::LITE));
  // Bar
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t bar_tvi,
      lite_index->InsertTerm("bar", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t bar_term_id,
                             term_id_codec_->EncodeTvi(bar_tvi, TviType::LITE));
  // Baz
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t baz_tvi,
      lite_index->InsertTerm("baz", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t baz_term_id,
                             term_id_codec_->EncodeTvi(baz_tvi, TviType::LITE));
  // Qux
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t qux_tvi,
      lite_index->InsertTerm("qux", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t qux_term_id,
                             term_id_codec_->EncodeTvi(qux_tvi, TviType::LITE));

  // Add 14 hits and make sure that termIds are added in unsorted order.
  // Documents should be inserted in order as new incoming hits should have
  // larger document ids.
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc0_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, doc0_hit1));
  ICING_ASSERT_OK(lite_index->AddHit(baz_term_id, doc0_hit2));
  ICING_ASSERT_OK(lite_index->AddHit(qux_term_id, doc0_hit3));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc1_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, doc1_hit1));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc1_hit2));
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, doc1_hit3));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc2_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(baz_term_id, doc2_hit1));
  ICING_ASSERT_OK(lite_index->AddHit(qux_term_id, doc2_hit2));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc2_hit3));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc3_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(baz_term_id, doc3_hit1));
  // Check the total size and unsorted size of the hit buffer. The HitBuffer has
  // not been sorted.
  EXPECT_THAT(lite_index, Pointee(SizeIs(14)));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedSize(), Eq(14));
  EXPECT_THAT(lite_index->HasUnsortedHitsExceedingSortThreshold(), IsTrue());

  // We now have the following in the hit buffer:
  // <term>: {(docId, sectionId)...}
  // foo: {(0, 0); (1, 0); (1, 1); (2, 0); (2, 2); (3, 0)}
  // bar: {(0, 0); (1, 0); (1, 2)}
  // baz: {(0, 1); (2, 0); (3, 0)}
  // quz: {(0, 2); (2, 1)}

  // Search over the HitBuffer.
  std::vector<DocHitInfo> hits1;
  lite_index->FetchHits(
      foo_term_id, kSectionIdMaskAll,
      /*only_from_prefix_sections=*/false,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
      /*namespace_checker=*/nullptr, &hits1);
  EXPECT_THAT(hits1, SizeIs(4));
  // Check that hits are retrieved in descending order of docIds.
  EXPECT_THAT(hits1[0].document_id(), Eq(3));
  EXPECT_THAT(hits1[0].hit_section_ids_mask(), Eq(0b1));
  EXPECT_THAT(hits1[1].document_id(), Eq(2));
  EXPECT_THAT(hits1[1].hit_section_ids_mask(), Eq(0b101));
  EXPECT_THAT(hits1[2].document_id(), Eq(1));
  EXPECT_THAT(hits1[2].hit_section_ids_mask(), Eq(0b11));
  EXPECT_THAT(hits1[3].document_id(), Eq(0));
  EXPECT_THAT(hits1[3].hit_section_ids_mask(), Eq(0b1));

  std::vector<DocHitInfo> hits2;
  AlwaysFalseSuggestionResultCheckerImpl always_false_suggestion_result_checker;
  lite_index->FetchHits(
      foo_term_id, kSectionIdMaskAll,
      /*only_from_prefix_sections=*/false,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
      &always_false_suggestion_result_checker, &hits2);
  // Check that no hits are returned because they get skipped by the namespace
  // checker.
  EXPECT_THAT(hits2, IsEmpty());

  std::vector<DocHitInfo> hits3;
  lite_index->FetchHits(
      bar_term_id, 0b1,
      /*only_from_prefix_sections=*/false,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
      /*namespace_checker=*/nullptr, &hits3);
  EXPECT_THAT(hits3, SizeIs(2));
  // Check fetching hits with SectionIdMask.
  EXPECT_THAT(hits3[0].document_id(), Eq(1));
  EXPECT_THAT(hits3[1].hit_section_ids_mask(), Eq(0b1));
  EXPECT_THAT(hits3[1].document_id(), Eq(0));
  EXPECT_THAT(hits3[1].hit_section_ids_mask(), Eq(0b1));

  // Check the total size and unsorted size of the hit buffer. The HitBuffer
  // should be sorted after the query call.
  EXPECT_THAT(lite_index, Pointee(SizeIs(14)));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedSize(), Eq(0));
  EXPECT_THAT(lite_index->HasUnsortedHitsExceedingSortThreshold(), IsFalse());
}

TEST_F(
    LiteIndexTest,
    LiteIndexFetchHits_sortAtIndexing_unsortedHitsExceedingSortAtIndexThreshold) {
  // Set up LiteIndex and TermIdCodec
  std::string lite_index_file_name = index_dir_ + "/test_file.lite-idx.index";
  // At 64 bytes the unsorted tail can contain a max of 8 TermHitPairs.
  LiteIndex::Options options(lite_index_file_name,
                             /*hit_buffer_want_merge_bytes=*/1024 * 1024,
                             /*hit_buffer_sort_at_indexing=*/true,
                             /*hit_buffer_sort_threshold_bytes=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LiteIndex> lite_index,
                             LiteIndex::Create(options, &icing_filesystem_));
  ICING_ASSERT_OK_AND_ASSIGN(
      term_id_codec_,
      TermIdCodec::Create(
          IcingDynamicTrie::max_value_index(IcingDynamicTrie::Options()),
          IcingDynamicTrie::max_value_index(options.lexicon_options)));

  // Create 4 hits for docs 0-2, and 2 hits for doc 3 -- 14 in total
  // Doc 0
  Hit doc0_hit0(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc0_hit1(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc0_hit2(/*section_id=*/1, /*document_id=*/0, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc0_hit3(/*section_id=*/2, /*document_id=*/0, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  // Doc 1
  Hit doc1_hit0(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc1_hit1(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc1_hit2(/*section_id=*/1, /*document_id=*/1, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc1_hit3(/*section_id=*/2, /*document_id=*/1, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  // Doc 2
  Hit doc2_hit0(/*section_id=*/0, /*document_id=*/2, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc2_hit1(/*section_id=*/0, /*document_id=*/2, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc2_hit2(/*section_id=*/1, /*document_id=*/2, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc2_hit3(/*section_id=*/2, /*document_id=*/2, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  // Doc 3
  Hit doc3_hit0(/*section_id=*/0, /*document_id=*/3, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc3_hit1(/*section_id=*/0, /*document_id=*/3, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc3_hit2(/*section_id=*/1, /*document_id=*/3, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc3_hit3(/*section_id=*/2, /*document_id=*/3, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  // Doc 4
  Hit doc4_hit0(/*section_id=*/0, /*document_id=*/4, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc4_hit1(/*section_id=*/0, /*document_id=*/4, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc4_hit2(/*section_id=*/1, /*document_id=*/4, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
  Hit doc4_hit3(/*section_id=*/2, /*document_id=*/4, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);

  // Create terms
  // Foo
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_tvi,
      lite_index->InsertTerm("foo", TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(foo_tvi, TviType::LITE));
  // Bar
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t bar_tvi,
      lite_index->InsertTerm("bar", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t bar_term_id,
                             term_id_codec_->EncodeTvi(bar_tvi, TviType::LITE));
  // Baz
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t baz_tvi,
      lite_index->InsertTerm("baz", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t baz_term_id,
                             term_id_codec_->EncodeTvi(baz_tvi, TviType::LITE));
  // Qux
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t qux_tvi,
      lite_index->InsertTerm("qux", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t qux_term_id,
                             term_id_codec_->EncodeTvi(qux_tvi, TviType::LITE));

  // Add hits and make sure that termIds are added in unsorted order.
  // Documents should be inserted in order as new incoming hits should have
  // larger document ids.
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc0_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, doc0_hit1));
  ICING_ASSERT_OK(lite_index->AddHit(baz_term_id, doc0_hit2));
  ICING_ASSERT_OK(lite_index->AddHit(qux_term_id, doc0_hit3));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc1_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, doc1_hit1));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc1_hit2));
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, doc1_hit3));
  // Adding 8 hits exceeds the sort threshold. However when sort_at_indexing is
  // enabled, sorting is done in the string-section-indexing-handler rather than
  // AddHit() itself, we need to invoke SortHits() manually.
  EXPECT_THAT(lite_index->HasUnsortedHitsExceedingSortThreshold(), IsTrue());
  lite_index->SortHits();
  // Check the total size and unsorted size of the hit buffer. The HitBuffer
  // should be sorted after calling SortHits().
  EXPECT_THAT(lite_index, Pointee(SizeIs(8)));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedSize(), Eq(0));
  EXPECT_THAT(lite_index->HasUnsortedHitsExceedingSortThreshold(), IsFalse());

  // Add 12 more hits so that sort threshold is exceeded again.
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc2_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(baz_term_id, doc2_hit1));
  ICING_ASSERT_OK(lite_index->AddHit(qux_term_id, doc2_hit2));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc2_hit3));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc3_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(baz_term_id, doc3_hit1));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc3_hit2));
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, doc3_hit3));
  ICING_ASSERT_OK(lite_index->AddHit(baz_term_id, doc4_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(qux_term_id, doc4_hit1));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc4_hit2));
  ICING_ASSERT_OK(lite_index->AddHit(bar_term_id, doc4_hit3));

  // Adding these hits exceeds the sort threshold. However when sort_at_indexing
  // is enabled, sorting is done in the string-section-indexing-handler rather
  // than AddHit() itself.
  EXPECT_THAT(lite_index, Pointee(SizeIs(20)));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedSize(), Eq(12));
  EXPECT_THAT(lite_index->HasUnsortedHitsExceedingSortThreshold(), IsTrue());

  // We now have the following in the hit buffer:
  // <term>: {(docId, sectionId)...}
  // foo: {(0, 0); (1, 0); (1, 1); (2, 0); (2, 2); (3, 0); (3, 1); (4, 1)}
  // bar: {(0, 0); (1, 0); (1, 2); (3, 2); (4, 2)}
  // baz: {(0, 1); (2, 0); (3, 0); (4, 0)}
  // quz: {(0, 2); (2, 1); (4, 0)}

  // Search over the HitBuffer.
  std::vector<DocHitInfo> hits1;
  lite_index->FetchHits(
      foo_term_id, kSectionIdMaskAll,
      /*only_from_prefix_sections=*/false,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
      /*namespace_checker=*/nullptr, &hits1);
  EXPECT_THAT(hits1, SizeIs(5));
  // Check that hits are retrieved in descending order of docIds.
  EXPECT_THAT(hits1[0].document_id(), Eq(4));
  EXPECT_THAT(hits1[0].hit_section_ids_mask(), Eq(0b10));
  EXPECT_THAT(hits1[1].document_id(), Eq(3));
  EXPECT_THAT(hits1[1].hit_section_ids_mask(), Eq(0b11));
  EXPECT_THAT(hits1[2].document_id(), Eq(2));
  EXPECT_THAT(hits1[2].hit_section_ids_mask(), Eq(0b101));
  EXPECT_THAT(hits1[3].document_id(), Eq(1));
  EXPECT_THAT(hits1[3].hit_section_ids_mask(), Eq(0b11));
  EXPECT_THAT(hits1[4].document_id(), Eq(0));
  EXPECT_THAT(hits1[4].hit_section_ids_mask(), Eq(0b1));

  std::vector<DocHitInfo> hits2;
  AlwaysFalseSuggestionResultCheckerImpl always_false_suggestion_result_checker;
  lite_index->FetchHits(
      foo_term_id, kSectionIdMaskAll,
      /*only_from_prefix_sections=*/false,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
      &always_false_suggestion_result_checker, &hits2);
  // Check that no hits are returned because they get skipped by the namespace
  // checker.
  EXPECT_THAT(hits2, IsEmpty());

  std::vector<DocHitInfo> hits3;
  lite_index->FetchHits(
      bar_term_id, 0b1,
      /*only_from_prefix_sections=*/false,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
      /*namespace_checker=*/nullptr, &hits3);
  EXPECT_THAT(hits3, SizeIs(2));
  // Check fetching hits with SectionIdMask.
  EXPECT_THAT(hits3[0].document_id(), Eq(1));
  EXPECT_THAT(hits3[1].hit_section_ids_mask(), Eq(0b1));
  EXPECT_THAT(hits3[1].document_id(), Eq(0));
  EXPECT_THAT(hits3[1].hit_section_ids_mask(), Eq(0b1));

  // Check the total size and unsorted size of the hit buffer. FetchHits should
  // sort before performing search if the HitBuffer unsorted size exceeds the
  // sort threshold, regardless of the sort_at_indexing config (to avoid
  // sequential search on an extremely long unsorted tails).
  EXPECT_THAT(lite_index, Pointee(SizeIs(20)));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedSize(), Eq(0));
  EXPECT_THAT(lite_index->HasUnsortedHitsExceedingSortThreshold(), IsFalse());
}

TEST_F(LiteIndexTest, LiteIndexIterator) {
  // Set up LiteIndex and TermIdCodec
  std::string lite_index_file_name = index_dir_ + "/test_file.lite-idx.index";
  // At 64 bytes the unsorted tail can contain a max of 8 TermHitPairs.
  LiteIndex::Options options(lite_index_file_name,
                             /*hit_buffer_want_merge_bytes=*/1024 * 1024,
                             /*hit_buffer_sort_at_indexing=*/true,
                             /*hit_buffer_sort_threshold_bytes=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LiteIndex> lite_index,
                             LiteIndex::Create(options, &icing_filesystem_));
  ICING_ASSERT_OK_AND_ASSIGN(
      term_id_codec_,
      TermIdCodec::Create(
          IcingDynamicTrie::max_value_index(IcingDynamicTrie::Options()),
          IcingDynamicTrie::max_value_index(options.lexicon_options)));

  const std::string term = "foo";
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index->InsertTerm(term, TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  Hit doc0_hit0(/*section_id=*/0, /*document_id=*/0, /*term_frequency=*/3,
                /*is_in_prefix_section=*/false);
  Hit doc0_hit1(/*section_id=*/1, /*document_id=*/0, /*term_frequency=*/5,
                /*is_in_prefix_section=*/false);
  SectionIdMask doc0_section_id_mask = 0b11;
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map0 = {{0, 3}, {1, 5}};
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc0_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc0_hit1));

  Hit doc1_hit1(/*section_id=*/1, /*document_id=*/1, /*term_frequency=*/7,
                /*is_in_prefix_section=*/false);
  Hit doc1_hit2(/*section_id=*/2, /*document_id=*/1, /*term_frequency=*/11,
                /*is_in_prefix_section=*/false);
  SectionIdMask doc1_section_id_mask = 0b110;
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map1 = {{1, 7}, {2, 11}};
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc1_hit1));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc1_hit2));

  std::unique_ptr<DocHitInfoIteratorTermLiteExact> iter =
      std::make_unique<DocHitInfoIteratorTermLiteExact>(
          term_id_codec_.get(), lite_index.get(), term, /*term_start_index=*/0,
          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
          /*need_hit_term_frequency=*/true);

  ASSERT_THAT(iter->Advance(), IsOk());
  EXPECT_THAT(iter->doc_hit_info().document_id(), Eq(1));
  EXPECT_THAT(iter->doc_hit_info().hit_section_ids_mask(),
              Eq(doc1_section_id_mask));

  std::vector<TermMatchInfo> matched_terms_stats;
  iter->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       term, expected_section_ids_tf_map1)));

  ASSERT_THAT(iter->Advance(), IsOk());
  EXPECT_THAT(iter->doc_hit_info().document_id(), Eq(0));
  EXPECT_THAT(iter->doc_hit_info().hit_section_ids_mask(),
              Eq(doc0_section_id_mask));
  matched_terms_stats.clear();
  iter->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       term, expected_section_ids_tf_map0)));
}

TEST_F(LiteIndexTest, LiteIndexIterator_sortAtIndexingDisabled) {
  // Set up LiteIndex and TermIdCodec
  std::string lite_index_file_name = index_dir_ + "/test_file.lite-idx.index";
  // At 64 bytes the unsorted tail can contain a max of 8 TermHitPairs.
  LiteIndex::Options options(lite_index_file_name,
                             /*hit_buffer_want_merge_bytes=*/1024 * 1024,
                             /*hit_buffer_sort_at_indexing=*/false,
                             /*hit_buffer_sort_threshold_bytes=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LiteIndex> lite_index,
                             LiteIndex::Create(options, &icing_filesystem_));
  ICING_ASSERT_OK_AND_ASSIGN(
      term_id_codec_,
      TermIdCodec::Create(
          IcingDynamicTrie::max_value_index(IcingDynamicTrie::Options()),
          IcingDynamicTrie::max_value_index(options.lexicon_options)));

  const std::string term = "foo";
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index->InsertTerm(term, TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  Hit doc0_hit0(/*section_id=*/0, /*document_id=*/0, /*term_frequency=*/3,
                /*is_in_prefix_section=*/false);
  Hit doc0_hit1(/*section_id=*/1, /*document_id=*/0, /*term_frequency=*/5,
                /*is_in_prefix_section=*/false);
  SectionIdMask doc0_section_id_mask = 0b11;
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map0 = {{0, 3}, {1, 5}};
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc0_hit0));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc0_hit1));

  Hit doc1_hit1(/*section_id=*/1, /*document_id=*/1, /*term_frequency=*/7,
                /*is_in_prefix_section=*/false);
  Hit doc1_hit2(/*section_id=*/2, /*document_id=*/1, /*term_frequency=*/11,
                /*is_in_prefix_section=*/false);
  SectionIdMask doc1_section_id_mask = 0b110;
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map1 = {{1, 7}, {2, 11}};
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc1_hit1));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, doc1_hit2));

  std::unique_ptr<DocHitInfoIteratorTermLiteExact> iter =
      std::make_unique<DocHitInfoIteratorTermLiteExact>(
          term_id_codec_.get(), lite_index.get(), term, /*term_start_index=*/0,
          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
          /*need_hit_term_frequency=*/true);

  ASSERT_THAT(iter->Advance(), IsOk());
  EXPECT_THAT(iter->doc_hit_info().document_id(), Eq(1));
  EXPECT_THAT(iter->doc_hit_info().hit_section_ids_mask(),
              Eq(doc1_section_id_mask));

  std::vector<TermMatchInfo> matched_terms_stats;
  iter->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       term, expected_section_ids_tf_map1)));

  ASSERT_THAT(iter->Advance(), IsOk());
  EXPECT_THAT(iter->doc_hit_info().document_id(), Eq(0));
  EXPECT_THAT(iter->doc_hit_info().hit_section_ids_mask(),
              Eq(doc0_section_id_mask));
  matched_terms_stats.clear();
  iter->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       term, expected_section_ids_tf_map0)));
}

TEST_F(LiteIndexTest, LiteIndexHitBufferSize) {
  // Set up LiteIndex and TermIdCodec
  std::string lite_index_file_name = index_dir_ + "/test_file.lite-idx.index";
  // At 64 bytes the unsorted tail can contain a max of 8 TermHitPairs.
  LiteIndex::Options options(lite_index_file_name,
                             /*hit_buffer_want_merge_bytes=*/1024 * 1024,
                             /*hit_buffer_sort_at_indexing=*/true,
                             /*hit_buffer_sort_threshold_bytes=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LiteIndex> lite_index,
                             LiteIndex::Create(options, &icing_filesystem_));
  ICING_ASSERT_OK_AND_ASSIGN(
      term_id_codec_,
      TermIdCodec::Create(
          IcingDynamicTrie::max_value_index(IcingDynamicTrie::Options()),
          IcingDynamicTrie::max_value_index(options.lexicon_options)));

  const std::string term = "foo";
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index->InsertTerm(term, TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  Hit hit0(/*section_id=*/0, /*document_id=*/0, /*term_frequency=*/3,
           /*is_in_prefix_section=*/false);
  Hit hit1(/*section_id=*/1, /*document_id=*/0, /*term_frequency=*/5,
           /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, hit0));
  ICING_ASSERT_OK(lite_index->AddHit(foo_term_id, hit1));

  // Check the total size and byte size of the hit buffer.
  EXPECT_THAT(lite_index, Pointee(SizeIs(2)));
  EXPECT_THAT(lite_index->GetHitBufferByteSize(),
              Eq(2 * sizeof(TermIdHitPair::Value)));
  // Check the unsorted size and byte size of the hit buffer.
  EXPECT_THAT(lite_index->GetHitBufferUnsortedSize(), Eq(2));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedByteSize(),
              Eq(2 * sizeof(TermIdHitPair::Value)));

  // Sort the hit buffer and check again.
  lite_index->SortHits();
  EXPECT_THAT(lite_index, Pointee(SizeIs(2)));
  EXPECT_THAT(lite_index->GetHitBufferByteSize(),
              Eq(2 * sizeof(TermIdHitPair::Value)));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedSize(), Eq(0));
  EXPECT_THAT(lite_index->GetHitBufferUnsortedByteSize(), Eq(0));
}

}  // namespace
}  // namespace lib
}  // namespace icing
