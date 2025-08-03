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

#include "icing/index/index.h"

#include <unistd.h>

#include <algorithm>
#include <cstdint>
#include <limits>
#include <memory>
#include <random>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/lite/term-id-hit-pair.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/legacy/index/icing-mock-filesystem.h"
#include "icing/proto/debug.pb.h"
#include "icing/proto/logging.pb.h"
#include "icing/proto/storage.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/testing/always-true-suggestion-result-checker-impl.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/random-string.h"
#include "icing/testing/tmp-directory.h"
#include "icing/util/crc32.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ContainerEq;
using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::Ge;
using ::testing::Gt;
using ::testing::IsEmpty;
using ::testing::IsFalse;
using ::testing::IsTrue;
using ::testing::Ne;
using ::testing::NiceMock;
using ::testing::Not;
using ::testing::Return;
using ::testing::SizeIs;
using ::testing::StrEq;
using ::testing::StrNe;
using ::testing::Test;
using ::testing::UnorderedElementsAre;

int GetBlockSize() { return getpagesize(); }

class IndexTest : public Test {
 protected:
  void SetUp() override {
    index_dir_ = GetTestTempDir() + "/index_test/";
    Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                           /*lite_index_sort_at_indexing=*/true,
                           /*lite_index_sort_size=*/1024 * 8);
    ICING_ASSERT_OK_AND_ASSIGN(
        index_, Index::Create(options, &filesystem_, &icing_filesystem_));
  }

  void TearDown() override {
    index_.reset();
    icing_filesystem_.DeleteDirectoryRecursively(index_dir_.c_str());
  }

  std::vector<DocHitInfo> GetHits(
      std::unique_ptr<DocHitInfoIterator> iterator) {
    std::vector<DocHitInfo> infos;
    while (iterator->Advance().ok()) {
      infos.push_back(iterator->doc_hit_info());
    }
    return infos;
  }

  libtextclassifier3::StatusOr<std::vector<DocHitInfo>> GetHits(
      std::string term, int term_start_index, int unnormalized_term_length,
      TermMatchType::Code match_type) {
    ICING_ASSIGN_OR_RETURN(
        std::unique_ptr<DocHitInfoIterator> itr,
        index_->GetIterator(term, term_start_index, unnormalized_term_length,
                            kSectionIdMaskAll, match_type));
    return GetHits(std::move(itr));
  }

  Filesystem filesystem_;
  IcingFilesystem icing_filesystem_;
  std::string index_dir_;
  std::unique_ptr<Index> index_;
};

constexpr DocumentId kDocumentId0 = 0;
constexpr DocumentId kDocumentId1 = 1;
constexpr DocumentId kDocumentId2 = 2;
constexpr DocumentId kDocumentId3 = 3;
constexpr DocumentId kDocumentId4 = 4;
constexpr DocumentId kDocumentId5 = 5;
constexpr DocumentId kDocumentId6 = 6;
constexpr DocumentId kDocumentId7 = 7;
constexpr DocumentId kDocumentId8 = 8;
constexpr SectionId kSectionId2 = 2;
constexpr SectionId kSectionId3 = 3;

MATCHER_P2(EqualsDocHitInfo, document_id, sections, "") {
  const DocHitInfo& actual = arg;
  SectionIdMask section_mask = kSectionIdMaskNone;
  for (SectionId section : sections) {
    section_mask |= UINT64_C(1) << section;
  }
  *result_listener << "actual is {document_id=" << actual.document_id()
                   << ", section_mask=" << actual.hit_section_ids_mask()
                   << "}, but expected was {document_id=" << document_id
                   << ", section_mask=" << section_mask << "}.";
  return actual.document_id() == document_id &&
         actual.hit_section_ids_mask() == section_mask;
}

MATCHER_P2(EqualsTermMetadata, content, hit_count, "") {
  const TermMetadata& actual = arg;
  *result_listener << "actual is {content=" << actual.content
                   << ", score=" << actual.score
                   << "}, but expected was {content=" << content
                   << ", score=" << hit_count << "}.";
  return actual.content == content && actual.score == hit_count;
}

TEST_F(IndexTest, CreationWithNullPointerShouldFail) {
  Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/1024 * 8);
  EXPECT_THAT(
      Index::Create(options, &filesystem_, /*icing_filesystem=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(
      Index::Create(options, /*filesystem=*/nullptr, &icing_filesystem_),
      StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_F(IndexTest, EmptyIndex) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("foo", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST_F(IndexTest, EmptyIndexAfterMerge) {
  // Merging an empty index should succeed, but have no effects.
  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("foo", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST_F(IndexTest, CreationWithLiteIndexSortAtIndexingEnabledShouldSort) {
  // Make the index with lite_index_sort_at_indexing=false and a very small sort
  // threshold.
  Index::Options options(index_dir_, /*index_merge_size=*/1024,
                         /*lite_index_sort_at_indexing=*/false,
                         /*lite_index_sort_size=*/16);
  ICING_ASSERT_OK_AND_ASSIGN(
      index_, Index::Create(options, &filesystem_, &icing_filesystem_));

  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  ASSERT_THAT(edit.BufferTerm("bar"), IsOk());
  ASSERT_THAT(edit.BufferTerm("baz"), IsOk());
  ASSERT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // Persist and recreate the index with lite_index_sort_at_indexing=true
  ASSERT_THAT(index_->PersistToDisk(), IsOk());
  options = Index::Options(index_dir_, /*index_merge_size=*/1024,
                           /*lite_index_sort_at_indexing=*/true,
                           /*lite_index_sort_size=*/16);
  ICING_ASSERT_OK_AND_ASSIGN(
      index_, Index::Create(options, &filesystem_, &icing_filesystem_));

  // Check that the index is sorted after recreating with
  // lite_index_sort_at_indexing, with the unsorted HitBuffer exceeding the sort
  // threshold.
  EXPECT_THAT(index_->LiteIndexNeedSort(), IsFalse());
}

TEST_F(IndexTest, AdvancePastEnd) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("bar", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(itr->doc_hit_info(),
              EqualsDocHitInfo(kInvalidDocumentId, std::vector<SectionId>()));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("foo", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->Advance(), IsOk());
  EXPECT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(itr->doc_hit_info(),
              EqualsDocHitInfo(kInvalidDocumentId, std::vector<SectionId>()));
}

TEST_F(IndexTest, AdvancePastEndAfterMerge) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("bar", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(itr->doc_hit_info(),
              EqualsDocHitInfo(kInvalidDocumentId, std::vector<SectionId>()));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("foo", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->Advance(), IsOk());
  EXPECT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(itr->doc_hit_info(),
              EqualsDocHitInfo(kInvalidDocumentId, std::vector<SectionId>()));
}

TEST_F(IndexTest, IteratorGetCallStats_mainIndexOnly) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // Merge the index.
  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));

  // Before Advance().
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/0,
          /*num_leaf_advance_calls_main_index=*/0,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/0));

  // 1st Advance().
  ICING_ASSERT_OK(itr->Advance());
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/0,
          /*num_leaf_advance_calls_main_index=*/1,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/1));

  // 2nd Advance().
  ICING_ASSERT_OK(itr->Advance());
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/0,
          /*num_leaf_advance_calls_main_index=*/2,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/1));

  // 3rd Advance().
  ASSERT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/0,
          /*num_leaf_advance_calls_main_index=*/2,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/1));
}

TEST_F(IndexTest, IteratorGetCallStats_liteIndexOnly) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));

  // Before Advance().
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/0,
          /*num_leaf_advance_calls_main_index=*/0,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/0));

  // 1st Advance().
  ICING_ASSERT_OK(itr->Advance());
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/1,
          /*num_leaf_advance_calls_main_index=*/0,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/0));

  // 2nd Advance().
  ICING_ASSERT_OK(itr->Advance());
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/2,
          /*num_leaf_advance_calls_main_index=*/0,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/0));

  // 3rd Advance().
  ASSERT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/2,
          /*num_leaf_advance_calls_main_index=*/0,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/0));
}

TEST_F(IndexTest, IteratorGetCallStats) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // Merge the index. 2 hits for "foo" will be merged into the main index.
  ICING_ASSERT_OK(index_->Merge());

  // Insert 2 more hits for "foo". It will be in the lite index.
  edit = index_->Edit(kDocumentId2, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId3, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));

  // Before Advance().
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/0,
          /*num_leaf_advance_calls_main_index=*/0,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/0));

  // 1st Advance(). DocHitInfoIteratorOr will advance both left and right
  // iterator (i.e. lite and main index iterator) once, compare document ids,
  // and return the hit with larger document id. In this case, hit from lite
  // index will be chosen and returned.
  ICING_ASSERT_OK(itr->Advance());
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/1,
          /*num_leaf_advance_calls_main_index=*/1,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/1));

  // 2nd Advance(). Since lite index iterator has larger document id in the
  // previous round, we advance lite index iterator in this round. We still
  // choose and return hit from lite index.
  ICING_ASSERT_OK(itr->Advance());
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/2,
          /*num_leaf_advance_calls_main_index=*/1,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/1));

  // 3rd Advance(). Since lite index iterator has larger document id in the
  // previous round, we advance lite index iterator in this round. However,
  // there is no hit from lite index anymore, so we choose and return hit from
  // main index.
  ICING_ASSERT_OK(itr->Advance());
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/2,
          /*num_leaf_advance_calls_main_index=*/1,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/1));

  // 4th Advance(). Advance main index.
  ICING_ASSERT_OK(itr->Advance());
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/2,
          /*num_leaf_advance_calls_main_index=*/2,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/1));

  // 5th Advance(). Reach the end.
  ASSERT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(
      itr->GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/2,
          /*num_leaf_advance_calls_main_index=*/2,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/1));
}

TEST_F(IndexTest, SingleHitSingleTermIndex) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, SingleHitSingleTermIndexAfterMerge) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, SingleHitSingleTermIndexAfterOptimize) {
  Index::Editor edit = index_->Edit(
      kDocumentId2, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  index_->set_last_added_document_id(kDocumentId2);

  ICING_ASSERT_OK(index_->Optimize(/*document_id_old_to_new=*/{0, 1, 2},
                                   /*new_last_added_document_id=*/2));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
          kDocumentId2, std::vector<SectionId>{kSectionId2}))));
  EXPECT_EQ(index_->last_added_document_id(), kDocumentId2);

  // Mapping to a different docid will translate the hit
  ICING_ASSERT_OK(index_->Optimize(
      /*document_id_old_to_new=*/{0, kInvalidDocumentId, kDocumentId1},
      /*new_last_added_document_id=*/1));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
          kDocumentId1, std::vector<SectionId>{kSectionId2}))));
  EXPECT_EQ(index_->last_added_document_id(), kDocumentId1);

  // Mapping to kInvalidDocumentId will remove the hit.
  ICING_ASSERT_OK(
      index_->Optimize(/*document_id_old_to_new=*/{0, kInvalidDocumentId},
                       /*new_last_added_document_id=*/0));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(IsEmpty()));
  EXPECT_EQ(index_->last_added_document_id(), kDocumentId0);
}

TEST_F(IndexTest, SingleHitSingleTermIndexAfterMergeAndOptimize) {
  Index::Editor edit = index_->Edit(
      kDocumentId2, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  index_->set_last_added_document_id(kDocumentId2);

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK(index_->Optimize(/*document_id_old_to_new=*/{0, 1, 2},
                                   /*new_last_added_document_id=*/2));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
          kDocumentId2, std::vector<SectionId>{kSectionId2}))));
  EXPECT_EQ(index_->last_added_document_id(), kDocumentId2);

  // Mapping to a different docid will translate the hit
  ICING_ASSERT_OK(index_->Optimize(
      /*document_id_old_to_new=*/{0, kInvalidDocumentId, kDocumentId1},
      /*new_last_added_document_id=*/1));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
          kDocumentId1, std::vector<SectionId>{kSectionId2}))));
  EXPECT_EQ(index_->last_added_document_id(), kDocumentId1);

  // Mapping to kInvalidDocumentId will remove the hit.
  ICING_ASSERT_OK(
      index_->Optimize(/*document_id_old_to_new=*/{0, kInvalidDocumentId},
                       /*new_last_added_document_id=*/0));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(IsEmpty()));
  EXPECT_EQ(index_->last_added_document_id(), 0);
}

TEST_F(IndexTest, SingleHitMultiTermIndex) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, SingleHitMultiTermIndexAfterMerge) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, MultiHitMultiTermIndexAfterOptimize) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId2, kSectionId3, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  index_->set_last_added_document_id(kDocumentId2);

  ICING_ASSERT_OK(index_->Optimize(/*document_id_old_to_new=*/{0, 1, 2},
                                   /*new_last_added_document_id=*/2));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(ElementsAre(
          EqualsDocHitInfo(kDocumentId2, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0,
                           std::vector<SectionId>{kSectionId2}))));
  EXPECT_THAT(
      GetHits("bar", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
          kDocumentId1, std::vector<SectionId>{kSectionId2}))));
  EXPECT_EQ(index_->last_added_document_id(), kDocumentId2);

  // Delete document id 1, and document id 2 is translated to 1.
  ICING_ASSERT_OK(
      index_->Optimize(/*document_id_old_to_new=*/{0, kInvalidDocumentId, 1},
                       /*new_last_added_document_id=*/1));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(ElementsAre(
          EqualsDocHitInfo(kDocumentId1, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0,
                           std::vector<SectionId>{kSectionId2}))));
  EXPECT_THAT(
      GetHits("bar", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(IsEmpty()));
  EXPECT_EQ(index_->last_added_document_id(), kDocumentId1);

  // Delete all the rest documents.
  ICING_ASSERT_OK(index_->Optimize(
      /*document_id_old_to_new=*/{kInvalidDocumentId, kInvalidDocumentId},
      /*new_last_added_document_id=*/kInvalidDocumentId));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(
      GetHits("bar", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(IsEmpty()));
  EXPECT_EQ(index_->last_added_document_id(), kInvalidDocumentId);
}

TEST_F(IndexTest, MultiHitMultiTermIndexAfterMergeAndOptimize) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId2, kSectionId3, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  index_->set_last_added_document_id(kDocumentId2);

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK(index_->Optimize(/*document_id_old_to_new=*/{0, 1, 2},
                                   /*new_last_added_document_id=*/2));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(ElementsAre(
          EqualsDocHitInfo(kDocumentId2, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0,
                           std::vector<SectionId>{kSectionId2}))));
  EXPECT_THAT(
      GetHits("bar", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
          kDocumentId1, std::vector<SectionId>{kSectionId2}))));
  EXPECT_EQ(index_->last_added_document_id(), kDocumentId2);

  // Delete document id 1, and document id 2 is translated to 1.
  ICING_ASSERT_OK(
      index_->Optimize(/*document_id_old_to_new=*/{0, kInvalidDocumentId, 1},
                       /*new_last_added_document_id=*/1));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(ElementsAre(
          EqualsDocHitInfo(kDocumentId1, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0,
                           std::vector<SectionId>{kSectionId2}))));
  EXPECT_THAT(
      GetHits("bar", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(IsEmpty()));
  EXPECT_EQ(index_->last_added_document_id(), kDocumentId1);

  // Delete all the rest documents.
  ICING_ASSERT_OK(index_->Optimize(
      /*document_id_old_to_new=*/{kInvalidDocumentId, kInvalidDocumentId},
      /*new_last_added_document_id=*/kInvalidDocumentId));
  EXPECT_THAT(
      GetHits("foo", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(
      GetHits("bar", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::EXACT_ONLY),
      IsOkAndHolds(IsEmpty()));
  EXPECT_EQ(index_->last_added_document_id(), kInvalidDocumentId);
}

TEST_F(IndexTest, NoHitMultiTermIndex) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("baz", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST_F(IndexTest, NoHitMultiTermIndexAfterMerge) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("baz", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST_F(IndexTest, MultiHitMultiTermIndex) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId2, kSectionId3, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(
          EqualsDocHitInfo(kDocumentId2, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, MultiHitMultiTermIndexAfterMerge) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId2, kSectionId3, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(
          EqualsDocHitInfo(kDocumentId2, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, MultiHitSectionRestrict) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  SectionIdMask desired_section = 1U << kSectionId2;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, desired_section,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, MultiHitSectionRestrictAfterMerge) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  SectionIdMask desired_section = 1U << kSectionId2;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, desired_section,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, SingleHitDedupeIndex) {
  ICING_ASSERT_OK_AND_ASSIGN(int64_t size, index_->GetElementsSize());
  EXPECT_THAT(size, Eq(0));
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  ICING_ASSERT_OK_AND_ASSIGN(size, index_->GetElementsSize());
  EXPECT_THAT(size, Gt(0));
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  ICING_ASSERT_OK_AND_ASSIGN(int64_t new_size, index_->GetElementsSize());
  EXPECT_THAT(new_size, Eq(size));
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, PrefixHit) {
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, PrefixHitAfterMerge) {
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, MultiPrefixHit) {
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(
          EqualsDocHitInfo(kDocumentId1, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, MultiPrefixHitAfterMerge) {
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(
          EqualsDocHitInfo(kDocumentId1, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, NoExactHitInPrefixQuery) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId1, std::vector<SectionId>{kSectionId3})));
}

TEST_F(IndexTest, NoExactHitInPrefixQueryAfterMerge) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId1, std::vector<SectionId>{kSectionId3})));
}

TEST_F(IndexTest, PrefixHitDedupe) {
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  ASSERT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, PrefixHitDedupeAfterMerge) {
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  ASSERT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, PrefixToString) {
  SectionIdMask id_mask = (1U << kSectionId2) | (1U << kSectionId3);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, id_mask,
                          TermMatchType::PREFIX));
  EXPECT_THAT(itr->ToString(), Eq("(0000000000000000000000000000000000000000000"
                                  "000000000000000001100:foo* OR "
                                  "00000000000000000000000000000000000000000000"
                                  "00000000000000001100:foo*)"));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("foo", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  EXPECT_THAT(itr->ToString(), Eq("(1111111111111111111111111111111111111111111"
                                  "111111111111111111111:foo* OR "
                                  "11111111111111111111111111111111111111111111"
                                  "11111111111111111111:foo*)"));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("foo", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskNone, TermMatchType::PREFIX));
  EXPECT_THAT(itr->ToString(), Eq("(0000000000000000000000000000000000000000000"
                                  "000000000000000000000:foo* OR "
                                  "00000000000000000000000000000000000000000000"
                                  "00000000000000000000:foo*)"));
}

TEST_F(IndexTest, ExactToString) {
  SectionIdMask id_mask = (1U << kSectionId2) | (1U << kSectionId3);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, id_mask,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->ToString(), Eq("(0000000000000000000000000000000000000000000"
                                  "000000000000000001100:foo OR "
                                  "00000000000000000000000000000000000000000000"
                                  "00000000000000001100:foo)"));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("foo", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->ToString(), Eq("(1111111111111111111111111111111111111111111"
                                  "111111111111111111111:foo OR "
                                  "11111111111111111111111111111111111111111111"
                                  "11111111111111111111:foo)"));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("foo", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskNone, TermMatchType::EXACT_ONLY));
  EXPECT_THAT(itr->ToString(), Eq("(0000000000000000000000000000000000000000000"
                                  "000000000000000000000:foo OR "
                                  "00000000000000000000000000000000000000000000"
                                  "00000000000000000000:foo)"));
}

TEST_F(IndexTest, NonAsciiTerms) {
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("こんにちは"), IsOk());
  ASSERT_THAT(edit.BufferTerm("あなた"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("こんに", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("あなた", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, NonAsciiTermsAfterMerge) {
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("こんにちは"), IsOk());
  ASSERT_THAT(edit.BufferTerm("あなた"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("こんに", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("あなた", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, FullIndex) {
  // Make a smaller index so that it's easier to fill up.
  Index::Options options(index_dir_, /*index_merge_size=*/1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(
      index_, Index::Create(options, &filesystem_, &icing_filesystem_));

  std::default_random_engine random;
  std::vector<std::string> query_terms;
  std::string prefix = "prefix";
  for (int i = 0; i < 2600; ++i) {
    constexpr int kTokenSize = 5;
    query_terms.push_back(prefix +
                          RandomString(kAlNumAlphabet, kTokenSize, &random));
  }

  DocumentId document_id = 0;
  libtextclassifier3::Status status = libtextclassifier3::Status::OK;
  std::uniform_int_distribution<size_t> uniform(0u, query_terms.size() - 1);
  while (status.ok()) {
    for (int i = 0; i < 100; ++i) {
      Index::Editor edit =
          index_->Edit(document_id, kSectionId2, TermMatchType::PREFIX,
                       /*namespace_id=*/0);
      size_t idx = uniform(random);
      status = edit.BufferTerm(query_terms.at(idx).c_str());
      if (!status.ok()) {
        break;
      }
      status = edit.IndexAllBufferedTerms();
      if (!status.ok()) {
        break;
      }
    }
    ++document_id;
  }

  // Adding more hits should fail.
  Index::Editor edit =
      index_->Edit(document_id + 1, kSectionId2, TermMatchType::PREFIX,
                   /*namespace_id=*/0);
  std::string term = prefix + "foo";
  EXPECT_THAT(edit.BufferTerm(term.c_str()), IsOk());
  term = prefix + "bar";
  EXPECT_THAT(edit.BufferTerm(term.c_str()), IsOk());
  term = prefix + "baz";
  EXPECT_THAT(edit.BufferTerm(term.c_str()), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));

  for (int i = 0; i < query_terms.size(); i += 25) {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<DocHitInfoIterator> itr,
        index_->GetIterator(query_terms.at(i).c_str(), /*term_start_index=*/0,
                            /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                            TermMatchType::PREFIX));
    // Each query term should contain at least one hit - there may have been
    // other hits for this term that were added.
    EXPECT_THAT(itr->Advance(), IsOk());
  }
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> last_itr,
      index_->GetIterator(prefix.c_str(), /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(last_itr->Advance(), IsOk());
  EXPECT_THAT(last_itr->doc_hit_info().document_id(), Eq(document_id - 1));
}

TEST_F(IndexTest, FullIndexMerge) {
  // Make a smaller index so that it's easier to fill up.
  Index::Options options(index_dir_, /*index_merge_size=*/1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(
      index_, Index::Create(options, &filesystem_, &icing_filesystem_));

  std::default_random_engine random;
  std::vector<std::string> query_terms;
  std::string prefix = "prefix";
  for (int i = 0; i < 2600; ++i) {
    constexpr int kTokenSize = 5;
    query_terms.push_back(prefix +
                          RandomString(kAlNumAlphabet, kTokenSize, &random));
  }

  DocumentId document_id = 0;
  libtextclassifier3::Status status = libtextclassifier3::Status::OK;
  std::uniform_int_distribution<size_t> uniform(0u, query_terms.size() - 1);
  while (status.ok()) {
    for (int i = 0; i < 100; ++i) {
      Index::Editor edit =
          index_->Edit(document_id, kSectionId2, TermMatchType::PREFIX,
                       /*namespace_id=*/0);
      size_t idx = uniform(random);
      status = edit.BufferTerm(query_terms.at(idx).c_str());
      if (!status.ok()) {
        break;
      }
      status = edit.IndexAllBufferedTerms();
      if (!status.ok()) {
        break;
      }
    }
    ++document_id;
  }
  EXPECT_THAT(status,
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));

  // Adding more hits should fail.
  Index::Editor edit =
      index_->Edit(document_id + 1, kSectionId2, TermMatchType::PREFIX,
                   /*namespace_id=*/0);
  std::string term = prefix + "foo";
  EXPECT_THAT(edit.BufferTerm(term.c_str()), IsOk());
  term = prefix + "bar";
  EXPECT_THAT(edit.BufferTerm(term.c_str()), IsOk());
  term = prefix + "baz";
  EXPECT_THAT(edit.BufferTerm(term.c_str()), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> last_itr,
      index_->GetIterator(prefix.c_str(), /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(last_itr->Advance(), IsOk());
  EXPECT_THAT(last_itr->doc_hit_info().document_id(), Eq(document_id - 1));

  // After merging with the main index. Adding more hits should succeed now.
  ICING_ASSERT_OK(index_->Merge());
  edit = index_->Edit(document_id + 1, kSectionId2, TermMatchType::PREFIX, 0);
  prefix + "foo";
  EXPECT_THAT(edit.BufferTerm(term.c_str()), IsOk());
  term = prefix + "bar";
  EXPECT_THAT(edit.BufferTerm(term.c_str()), IsOk());
  term = prefix + "baz";
  EXPECT_THAT(edit.BufferTerm(term.c_str()), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator(prefix + "bar", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  // We know that "bar" should have at least one hit because we just added it!
  EXPECT_THAT(itr->Advance(), IsOk());
  EXPECT_THAT(itr->doc_hit_info().document_id(), Eq(document_id + 1));
  ICING_ASSERT_OK_AND_ASSIGN(
      last_itr, index_->GetIterator(prefix.c_str(), /*term_start_index=*/0,
                                    /*unnormalized_term_length=*/0,
                                    kSectionIdMaskAll, TermMatchType::PREFIX));
  EXPECT_THAT(last_itr->Advance(), IsOk());
  EXPECT_THAT(last_itr->doc_hit_info().document_id(), Eq(document_id + 1));
}

TEST_F(IndexTest, OptimizeShouldWorkForEmptyIndex) {
  // Optimize an empty index should succeed, but have no effects.
  ICING_ASSERT_OK(
      index_->Optimize(std::vector<DocumentId>(),
                       /*new_last_added_document_id=*/kInvalidDocumentId));
  EXPECT_EQ(index_->last_added_document_id(), kInvalidDocumentId);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("", kSectionIdMaskAll, /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("", kSectionIdMaskAll, /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());
}

TEST_F(IndexTest, IndexShouldWorkAtSectionLimit) {
  std::string prefix = "prefix";
  std::default_random_engine random;
  std::vector<std::string> query_terms;
  // Add 2048 hits to main index, and 2048 hits to lite index.
  for (int i = 0; i < 4096; ++i) {
    if (i == 1024) {
      ICING_ASSERT_OK(index_->Merge());
    }
    // Generate a unique term for document i.
    query_terms.push_back(prefix + RandomString("abcdefg", 5, &random) +
                          std::to_string(i));
    TermMatchType::Code term_match_type = TermMatchType::PREFIX;
    SectionId section_id = i % 64;
    if (section_id == 2) {
      // Make section 2 an exact section.
      term_match_type = TermMatchType::EXACT_ONLY;
    }
    Index::Editor edit = index_->Edit(/*document_id=*/i, section_id,
                                      term_match_type, /*namespace_id=*/0);
    ICING_ASSERT_OK(edit.BufferTerm(query_terms.at(i).c_str()));
    ICING_ASSERT_OK(edit.IndexAllBufferedTerms());
  }

  std::vector<DocHitInfo> exp_prefix_hits;
  for (int i = 0; i < 4096; ++i) {
    if (i % 64 == 2) {
      // Section 2 is an exact section, so we should not see any hits in
      // prefix search.
      continue;
    }
    exp_prefix_hits.push_back(DocHitInfo(i));
    exp_prefix_hits.back().UpdateSection(/*section_id=*/i % 64);
  }
  std::reverse(exp_prefix_hits.begin(), exp_prefix_hits.end());

  // Check prefix search.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<DocHitInfo> hits,
      GetHits(prefix, /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::PREFIX));
  EXPECT_THAT(hits, ContainerEq(exp_prefix_hits));

  // Check exact search.
  for (int i = 0; i < 4096; ++i) {
    ICING_ASSERT_OK_AND_ASSIGN(
        hits,
        GetHits(query_terms[i], /*term_start_index=*/0,
                /*unnormalized_term_length=*/0, TermMatchType::EXACT_ONLY));
    EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfo(
                          i, std::vector<SectionId>{(SectionId)(i % 64)})));
  }
}

// Skip this test on Android because of timeout.
#if !defined(__ANDROID__)
TEST_F(IndexTest, IndexShouldWorkAtDocumentLimit) {
  std::string prefix = "pre";
  std::default_random_engine random;
  const int max_lite_index_size = 1024 * 1024 / 8;
  int lite_index_size = 0;
  for (int i = 0; i <= kMaxDocumentId; ++i) {
    if (i % max_lite_index_size == 0 && i != 0) {
      ICING_ASSERT_OK(index_->Merge());
      lite_index_size = 0;
    }
    std::string term;
    TermMatchType::Code term_match_type = TermMatchType::PREFIX;
    SectionId section_id = i % 64;
    if (section_id == 2) {
      // Make section 2 an exact section.
      term_match_type = TermMatchType::EXACT_ONLY;
      term = std::to_string(i);
    } else {
      term = prefix + RandomString("abcd", 5, &random);
    }
    Index::Editor edit = index_->Edit(/*document_id=*/i, section_id,
                                      term_match_type, /*namespace_id=*/0);
    ICING_ASSERT_OK(edit.BufferTerm(term.c_str()));
    ICING_ASSERT_OK(edit.IndexAllBufferedTerms());
    ++lite_index_size;
    index_->set_last_added_document_id(i);
  }
  // Ensure that the lite index still contains some data to better test both
  // indexes.
  ASSERT_THAT(lite_index_size, Eq(max_lite_index_size - 1));
  EXPECT_EQ(index_->last_added_document_id(), kMaxDocumentId);

  std::vector<DocHitInfo> exp_prefix_hits;
  for (int i = 0; i <= kMaxDocumentId; ++i) {
    if (i % 64 == 2) {
      // Section 2 is an exact section, so we should not see any hits in
      // prefix search.
      continue;
    }
    exp_prefix_hits.push_back(DocHitInfo(i));
    exp_prefix_hits.back().UpdateSection(/*section_id=*/i % 64);
  }
  std::reverse(exp_prefix_hits.begin(), exp_prefix_hits.end());

  // Check prefix search.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<DocHitInfo> hits,
      GetHits(prefix, /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::PREFIX));
  EXPECT_THAT(hits, ContainerEq(exp_prefix_hits));

  // Check exact search.
  for (int i = 0; i <= kMaxDocumentId; ++i) {
    if (i % 64 == 2) {
      // Only section 2 is an exact section
      ICING_ASSERT_OK_AND_ASSIGN(
          hits,
          GetHits(std::to_string(i), /*term_start_index=*/0,
                  /*unnormalized_term_length=*/0, TermMatchType::EXACT_ONLY));
      EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfo(
                            i, std::vector<SectionId>{(SectionId)(2)})));
    }
  }
}
#endif  // if !defined(__ANDROID__)

TEST_F(IndexTest, IndexOptimize) {
  std::string prefix = "prefix";
  std::default_random_engine random;
  std::vector<std::string> query_terms;
  // Add 1024 hits to main index, and 1024 hits to lite index.
  for (int i = 0; i < 2048; ++i) {
    if (i == 1024) {
      ICING_ASSERT_OK(index_->Merge());
    }
    // Generate a unique term for document i.
    query_terms.push_back(prefix + RandomString("abcdefg", 5, &random) +
                          std::to_string(i));
    TermMatchType::Code term_match_type = TermMatchType::PREFIX;
    SectionId section_id = i % 64;
    if (section_id == 2) {
      // Make section 2 an exact section.
      term_match_type = TermMatchType::EXACT_ONLY;
    }
    Index::Editor edit = index_->Edit(/*document_id=*/i, section_id,
                                      term_match_type, /*namespace_id=*/0);
    ICING_ASSERT_OK(edit.BufferTerm(query_terms.at(i).c_str()));
    ICING_ASSERT_OK(edit.IndexAllBufferedTerms());
    index_->set_last_added_document_id(i);
  }

  // Delete one document for every three documents.
  DocumentId document_id = 0;
  DocumentId new_last_added_document_id = kInvalidDocumentId;
  std::vector<DocumentId> document_id_old_to_new;
  for (int i = 0; i < 2048; ++i) {
    if (i % 3 == 0) {
      document_id_old_to_new.push_back(kInvalidDocumentId);
    } else {
      new_last_added_document_id = document_id++;
      document_id_old_to_new.push_back(new_last_added_document_id);
    }
  }

  std::vector<DocHitInfo> exp_prefix_hits;
  for (int i = 0; i < 2048; ++i) {
    if (document_id_old_to_new[i] == kInvalidDocumentId) {
      continue;
    }
    if (i % 64 == 2) {
      // Section 2 is an exact section, so we should not see any hits in
      // prefix search.
      continue;
    }
    exp_prefix_hits.push_back(DocHitInfo(document_id_old_to_new[i]));
    exp_prefix_hits.back().UpdateSection(/*section_id=*/i % 64);
  }
  std::reverse(exp_prefix_hits.begin(), exp_prefix_hits.end());

  // Check that optimize is correct
  ICING_ASSERT_OK(
      index_->Optimize(document_id_old_to_new, new_last_added_document_id));
  EXPECT_EQ(index_->last_added_document_id(), new_last_added_document_id);
  // Check prefix search.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<DocHitInfo> hits,
      GetHits(prefix, /*term_start_index=*/0, /*unnormalized_term_length=*/0,
              TermMatchType::PREFIX));
  EXPECT_THAT(hits, ContainerEq(exp_prefix_hits));
  // Check exact search.
  for (int i = 0; i < 2048; ++i) {
    ICING_ASSERT_OK_AND_ASSIGN(
        hits,
        GetHits(query_terms[i], /*term_start_index=*/0,
                /*unnormalized_term_length=*/0, TermMatchType::EXACT_ONLY));
    if (document_id_old_to_new[i] == kInvalidDocumentId) {
      EXPECT_THAT(hits, IsEmpty());
    } else {
      EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfo(
                            document_id_old_to_new[i],
                            std::vector<SectionId>{(SectionId)(i % 64)})));
    }
  }

  // Check that optimize does not block merge.
  ICING_ASSERT_OK(index_->Merge());
  EXPECT_EQ(index_->last_added_document_id(), new_last_added_document_id);
  // Check prefix search.
  ICING_ASSERT_OK_AND_ASSIGN(
      hits, GetHits(prefix, /*term_start_index=*/0,
                    /*unnormalized_term_length=*/0, TermMatchType::PREFIX));
  EXPECT_THAT(hits, ContainerEq(exp_prefix_hits));
  // Check exact search.
  for (int i = 0; i < 2048; ++i) {
    ICING_ASSERT_OK_AND_ASSIGN(
        hits,
        GetHits(query_terms[i], /*term_start_index=*/0,
                /*unnormalized_term_length=*/0, TermMatchType::EXACT_ONLY));
    if (document_id_old_to_new[i] == kInvalidDocumentId) {
      EXPECT_THAT(hits, IsEmpty());
    } else {
      EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfo(
                            document_id_old_to_new[i],
                            std::vector<SectionId>{(SectionId)(i % 64)})));
    }
  }
}

TEST_F(IndexTest, IndexCreateIOFailure) {
  // Create the index with mock filesystem. By default, Mock will return false,
  // so the first attempted file operation will fail.
  NiceMock<IcingMockFilesystem> mock_icing_filesystem;
  ON_CALL(mock_icing_filesystem, CreateDirectoryRecursively)
      .WillByDefault(Return(false));
  Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/1024 * 8);
  EXPECT_THAT(Index::Create(options, &filesystem_, &mock_icing_filesystem),
              StatusIs(libtextclassifier3::StatusCode::INTERNAL));
}

TEST_F(IndexTest, IndexCreateCorruptionFailure) {
  // Add some content to the index
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  ASSERT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // Close the index.
  index_.reset();

  // Corrrupt the index file.
  std::string hit_buffer_filename = index_dir_ + "/idx/lite.hb";
  ScopedFd sfd(icing_filesystem_.OpenForWrite(hit_buffer_filename.c_str()));
  ASSERT_THAT(sfd.is_valid(), IsTrue());

  constexpr std::string_view kCorruptBytes = "ffffffffffffffffffffff";
  // The first page of the hit_buffer is taken up by the header. Overwrite the
  // first page of content.
  int hit_buffer_start_offset = GetBlockSize();
  ASSERT_THAT(
      icing_filesystem_.PWrite(sfd.get(), hit_buffer_start_offset,
                               kCorruptBytes.data(), kCorruptBytes.length()),
      IsTrue());

  // Recreate the index.
  Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/1024 * 8);
  EXPECT_THAT(Index::Create(options, &filesystem_, &icing_filesystem_),
              StatusIs(libtextclassifier3::StatusCode::DATA_LOSS));
}

TEST_F(IndexTest, IndexPersistence) {
  // Add some content to the index
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  ASSERT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  EXPECT_THAT(index_->PersistToDisk(), IsOk());

  // Close the index.
  index_.reset();

  // Recreate the index.
  Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/1024 * 8);
  ICING_ASSERT_OK_AND_ASSIGN(
      index_, Index::Create(options, &filesystem_, &icing_filesystem_));

  // Check that the hits are present.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("f", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, IndexPersistenceAfterMerge) {
  // Add some content to the index
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  ASSERT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  ICING_ASSERT_OK(index_->Merge());
  EXPECT_THAT(index_->PersistToDisk(), IsOk());

  // Close the index.
  index_.reset();

  // Recreate the index.
  Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/1024 * 8);
  ICING_ASSERT_OK_AND_ASSIGN(
      index_, Index::Create(options, &filesystem_, &icing_filesystem_));

  // Check that the hits are present.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("f", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, InvalidHitBufferSize) {
  Index::Options options(
      index_dir_, /*index_merge_size=*/std::numeric_limits<uint32_t>::max(),
      /*lite_index_sort_at_indexing=*/true, /*lite_index_sort_size=*/1024 * 8);
  EXPECT_THAT(Index::Create(options, &filesystem_, &icing_filesystem_),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(IndexTest, FindTermByPrefixShouldReturnEmpty) {
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  AlwaysTrueSuggestionResultCheckerImpl impl;
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"foo", /*num_to_return=*/0, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"foo", /*num_to_return=*/-1, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(IsEmpty()));

  ICING_ASSERT_OK(index_->Merge());

  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"foo", /*num_to_return=*/0, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"foo", /*num_to_return=*/-1, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(IsEmpty()));
}

TEST_F(IndexTest, FindTermByPrefixShouldReturnCorrectResult) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  AlwaysTrueSuggestionResultCheckerImpl impl;
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("bar"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // "b" should only match "bar" but not "foo".
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"b", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(UnorderedElementsAre(EqualsTermMetadata("bar", 1))));

  ICING_ASSERT_OK(index_->Merge());

  // "b" should only match "bar" but not "foo".
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"b", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(UnorderedElementsAre(EqualsTermMetadata("bar", 1))));
}

TEST_F(IndexTest, FindTermByPrefixShouldRespectNumToReturn) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  AlwaysTrueSuggestionResultCheckerImpl impl;
  EXPECT_THAT(edit.BufferTerm("fo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // We have 3 results but only 2 should be returned.
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/2, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(SizeIs(2)));

  ICING_ASSERT_OK(index_->Merge());

  // We have 3 results but only 2 should be returned.
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/2, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(SizeIs(2)));
}

TEST_F(IndexTest, FindTermByPrefixShouldReturnTermsInAllNamespaces) {
  Index::Editor edit1 =
      index_->Edit(kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  AlwaysTrueSuggestionResultCheckerImpl impl;
  EXPECT_THAT(edit1.BufferTerm("fo"), IsOk());
  EXPECT_THAT(edit1.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit2 =
      index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/1);
  EXPECT_THAT(edit2.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit2.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit3 =
      index_->Edit(kDocumentId2, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/2);
  EXPECT_THAT(edit3.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit3.IndexAllBufferedTerms(), IsOk());

  // Should return "fo", "foo" and "fool" across all namespaces.
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(UnorderedElementsAre(EqualsTermMetadata("fo", 1),
                                        EqualsTermMetadata("foo", 1),
                                        EqualsTermMetadata("fool", 1))));

  ICING_ASSERT_OK(index_->Merge());

  // Should return "fo", "foo" and "fool" across all namespaces.
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(UnorderedElementsAre(EqualsTermMetadata("fo", 1),
                                        EqualsTermMetadata("foo", 1),
                                        EqualsTermMetadata("fool", 1))));
}

TEST_F(IndexTest, FindTermByPrefixShouldReturnCorrectHitCount) {
  Index::Editor edit1 =
      index_->Edit(kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  AlwaysTrueSuggestionResultCheckerImpl impl;
  EXPECT_THAT(edit1.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit1.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit1.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit2 =
      index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  EXPECT_THAT(edit2.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit2.IndexAllBufferedTerms(), IsOk());

  // 'foo' has 1 hit, 'fool' has 2 hits.
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("fool", 2),
                               EqualsTermMetadata("foo", 1))));

  ICING_ASSERT_OK(index_->Merge());

  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("fool", 2),
                               EqualsTermMetadata("foo", 1))));
}

TEST_F(IndexTest, FindTermByPrefixMultipleHitBatch) {
  AlwaysTrueSuggestionResultCheckerImpl impl;
  // Create multiple hit batches.
  for (int i = 0; i < 4000; i++) {
    Index::Editor edit = index_->Edit(i, kSectionId2, TermMatchType::EXACT_ONLY,
                                      /*namespace_id=*/0);
    EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
    EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  }

  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("fool", 4000))));

  ICING_ASSERT_OK(index_->Merge());

  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("fool", 4000))));
}

TEST_F(IndexTest, FindTermByPrefixShouldReturnInOrder) {
  // Push 6 term-six, 5 term-five, 4 term-four, 3 term-three, 2 term-two and one
  // term-one into lite index.
  Index::Editor edit1 =
      index_->Edit(kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  AlwaysTrueSuggestionResultCheckerImpl impl;
  EXPECT_THAT(edit1.BufferTerm("term-one"), IsOk());
  EXPECT_THAT(edit1.BufferTerm("term-two"), IsOk());
  EXPECT_THAT(edit1.BufferTerm("term-three"), IsOk());
  EXPECT_THAT(edit1.BufferTerm("term-four"), IsOk());
  EXPECT_THAT(edit1.BufferTerm("term-five"), IsOk());
  EXPECT_THAT(edit1.BufferTerm("term-six"), IsOk());
  EXPECT_THAT(edit1.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit2 =
      index_->Edit(kDocumentId2, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  EXPECT_THAT(edit2.BufferTerm("term-two"), IsOk());
  EXPECT_THAT(edit2.BufferTerm("term-three"), IsOk());
  EXPECT_THAT(edit2.BufferTerm("term-four"), IsOk());
  EXPECT_THAT(edit2.BufferTerm("term-five"), IsOk());
  EXPECT_THAT(edit2.BufferTerm("term-six"), IsOk());
  EXPECT_THAT(edit2.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit3 =
      index_->Edit(kDocumentId3, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  EXPECT_THAT(edit3.BufferTerm("term-three"), IsOk());
  EXPECT_THAT(edit3.BufferTerm("term-four"), IsOk());
  EXPECT_THAT(edit3.BufferTerm("term-five"), IsOk());
  EXPECT_THAT(edit3.BufferTerm("term-six"), IsOk());
  EXPECT_THAT(edit3.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit4 =
      index_->Edit(kDocumentId4, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  EXPECT_THAT(edit4.BufferTerm("term-four"), IsOk());
  EXPECT_THAT(edit4.BufferTerm("term-five"), IsOk());
  EXPECT_THAT(edit4.BufferTerm("term-six"), IsOk());
  EXPECT_THAT(edit4.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit5 =
      index_->Edit(kDocumentId5, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  EXPECT_THAT(edit5.BufferTerm("term-five"), IsOk());
  EXPECT_THAT(edit5.BufferTerm("term-six"), IsOk());
  EXPECT_THAT(edit5.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit6 =
      index_->Edit(kDocumentId6, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  EXPECT_THAT(edit6.BufferTerm("term-six"), IsOk());
  EXPECT_THAT(edit6.IndexAllBufferedTerms(), IsOk());

  // verify the order in lite index is correct.
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"t", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("term-six", 6),
                               EqualsTermMetadata("term-five", 5),
                               EqualsTermMetadata("term-four", 4),
                               EqualsTermMetadata("term-three", 3),
                               EqualsTermMetadata("term-two", 2),
                               EqualsTermMetadata("term-one", 1))));

  ICING_ASSERT_OK(index_->Merge());

  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"t", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("term-six", 6),
                               EqualsTermMetadata("term-five", 5),
                               EqualsTermMetadata("term-four", 4),
                               EqualsTermMetadata("term-three", 3),
                               EqualsTermMetadata("term-two", 2),
                               EqualsTermMetadata("term-one", 1))));

  // keep push terms to the lite index. We will add 2 document to term-five,
  // term-three and term-one. The output order should be 5-6-3-4-1-2.
  Index::Editor edit7 =
      index_->Edit(kDocumentId7, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  EXPECT_THAT(edit7.BufferTerm("term-one"), IsOk());
  EXPECT_THAT(edit7.BufferTerm("term-three"), IsOk());
  EXPECT_THAT(edit7.BufferTerm("term-five"), IsOk());
  EXPECT_THAT(edit7.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit8 =
      index_->Edit(kDocumentId8, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  EXPECT_THAT(edit8.BufferTerm("term-one"), IsOk());
  EXPECT_THAT(edit8.BufferTerm("term-three"), IsOk());
  EXPECT_THAT(edit8.BufferTerm("term-five"), IsOk());
  EXPECT_THAT(edit8.IndexAllBufferedTerms(), IsOk());

  // verify the combination of lite index and main index is in correct order.
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"t", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(ElementsAre(
          EqualsTermMetadata("term-five", 7), EqualsTermMetadata("term-six", 6),
          EqualsTermMetadata("term-three", 5),
          EqualsTermMetadata("term-four", 4), EqualsTermMetadata("term-one", 3),
          EqualsTermMetadata("term-two", 2))));

  // Get the first three terms.
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"t", /*num_to_return=*/3, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("term-five", 7),
                               EqualsTermMetadata("term-six", 6),
                               EqualsTermMetadata("term-three", 5))));
}

TEST_F(IndexTest, FindTermByPrefix_InTermMatchTypePrefix_ShouldReturnInOrder) {
  Index::Editor edit1 =
      index_->Edit(kDocumentId0, kSectionId2, TermMatchType::PREFIX,
                   /*namespace_id=*/0);
  AlwaysTrueSuggestionResultCheckerImpl impl;
  EXPECT_THAT(edit1.BufferTerm("fo"), IsOk());
  EXPECT_THAT(edit1.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit2 =
      index_->Edit(kDocumentId2, kSectionId2, TermMatchType::PREFIX,
                   /*namespace_id=*/0);
  EXPECT_THAT(edit2.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit2.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit3 =
      index_->Edit(kDocumentId3, kSectionId2, TermMatchType::PREFIX,
                   /*namespace_id=*/0);
  EXPECT_THAT(edit3.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit3.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());
  // verify the order in pls is correct
  // "fo"    { {doc0, exact_hit}, {doc1, prefix_hit}, {doc2, prefix_hit} }
  // "foo"   { {doc1, exact_hit}, {doc2, prefix_hit} }
  // "fool"  { {doc2, exact_hit} }
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f",
          /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("fo", 3),
                               EqualsTermMetadata("foo", 2),
                               EqualsTermMetadata("fool", 1))));
  // Find by exact only, all terms should be equally.
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::EXACT_ONLY,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(UnorderedElementsAre(EqualsTermMetadata("fo", 1),
                                        EqualsTermMetadata("foo", 1),
                                        EqualsTermMetadata("fool", 1))));
}

TEST_F(IndexTest, FindTermByPrefixShouldReturnHitCountForMain) {
  Index::Editor edit =
      index_->Edit(kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  AlwaysTrueSuggestionResultCheckerImpl impl;
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId2, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId3, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId4, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId5, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId6, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId7, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // 'foo' has 1 hit, 'fool' has 8 hits.
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("fool", 8),
                               EqualsTermMetadata("foo", 1))));

  ICING_ASSERT_OK(index_->Merge());

  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(UnorderedElementsAre(EqualsTermMetadata("foo", 1),
                                        EqualsTermMetadata("fool", 8))));
}

TEST_F(IndexTest, FindTermByPrefixShouldReturnCombinedHitCount) {
  Index::Editor edit =
      index_->Edit(kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  AlwaysTrueSuggestionResultCheckerImpl impl;
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("fool", 2),
                               EqualsTermMetadata("foo", 1))));
}

TEST_F(IndexTest, FindTermRankComparison) {
  Index::Editor edit =
      index_->Edit(kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  AlwaysTrueSuggestionResultCheckerImpl impl;
  EXPECT_THAT(edit.BufferTerm("fo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("fo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  Index::Editor edit2 =
      index_->Edit(kDocumentId2, kSectionId2, TermMatchType::PREFIX,
                   /*namespace_id=*/0);
  EXPECT_THAT(edit2.BufferTerm("fo"), IsOk());
  EXPECT_THAT(edit2.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit2.IndexAllBufferedTerms(), IsOk());

  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::EXACT_ONLY,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::TERM_FREQUENCY,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("fo", 3),
                               EqualsTermMetadata("foo", 2),
                               EqualsTermMetadata("fool", 1))));
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::EXACT_ONLY,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(UnorderedElementsAre(EqualsTermMetadata("fo", 2),
                                        EqualsTermMetadata("foo", 2),
                                        EqualsTermMetadata("fool", 1))));
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::EXACT_ONLY,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::NONE, &impl),
      IsOkAndHolds(UnorderedElementsAre(EqualsTermMetadata("fo", 1),
                                        EqualsTermMetadata("foo", 1),
                                        EqualsTermMetadata("fool", 1))));

  ICING_ASSERT_OK(index_->Merge());

  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::EXACT_ONLY,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::TERM_FREQUENCY,
          &impl),
      IsOkAndHolds(ElementsAre(EqualsTermMetadata("fo", 3),
                               EqualsTermMetadata("foo", 2),
                               EqualsTermMetadata("fool", 1))));
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::EXACT_ONLY,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(UnorderedElementsAre(EqualsTermMetadata("fo", 2),
                                        EqualsTermMetadata("foo", 2),
                                        EqualsTermMetadata("fool", 1))));
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::EXACT_ONLY,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::NONE, &impl),
      IsOkAndHolds(UnorderedElementsAre(EqualsTermMetadata("fo", 1),
                                        EqualsTermMetadata("foo", 1),
                                        EqualsTermMetadata("fool", 1))));
}

TEST_F(IndexTest, FindTermByPrefixShouldReturnTermsFromBothIndices) {
  Index::Editor edit =
      index_->Edit(kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY,
                   /*namespace_id=*/0);
  AlwaysTrueSuggestionResultCheckerImpl impl;

  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK(index_->Merge());

  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // 'foo' has 1 hit in the main index, 'fool' has 1 hit in the lite index.
  EXPECT_THAT(
      index_->FindTermsByPrefix(
          /*prefix=*/"f", /*num_to_return=*/10, TermMatchType::PREFIX,
          SuggestionScoringSpecProto::SuggestionRankingStrategy::DOCUMENT_COUNT,
          &impl),
      IsOkAndHolds(UnorderedElementsAre(EqualsTermMetadata("foo", 1),
                                        EqualsTermMetadata("fool", 1))));
}

TEST_F(IndexTest, GetElementsSize) {
  // Check empty index.
  ICING_ASSERT_OK_AND_ASSIGN(int64_t size, index_->GetElementsSize());
  EXPECT_THAT(size, Eq(0));

  // Add an element.
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  ICING_ASSERT_OK_AND_ASSIGN(size, index_->GetElementsSize());
  EXPECT_THAT(size, Gt(0));

  ASSERT_THAT(index_->Merge(), IsOk());
  ICING_ASSERT_OK_AND_ASSIGN(size, index_->GetElementsSize());
  EXPECT_THAT(size, Gt(0));
}

TEST_F(IndexTest, ExactResultsFromLiteAndMain) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foot"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  ICING_ASSERT_OK(index_->Merge());

  edit = index_->Edit(kDocumentId2, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("footer"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId2, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(
          EqualsDocHitInfo(kDocumentId2, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, PrefixResultsFromLiteAndMain) {
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foot"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  ICING_ASSERT_OK(index_->Merge());

  edit = index_->Edit(kDocumentId2, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("footer"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId2, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  EXPECT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("foo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(
          EqualsDocHitInfo(kDocumentId2, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId1, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, GetDebugInfo) {
  // Add two documents to the lite index, merge them into the main index and
  // then add another doc to the lite index.
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  ASSERT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  index_->set_last_added_document_id(kDocumentId1);
  ASSERT_THAT(edit.BufferTerm("foot"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  ICING_ASSERT_OK(index_->Merge());

  edit = index_->Edit(kDocumentId2, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  index_->set_last_added_document_id(kDocumentId2);
  ASSERT_THAT(edit.BufferTerm("footer"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId2, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  IndexDebugInfoProto out0 = index_->GetDebugInfo(DebugInfoVerbosity::BASIC);
  ICING_LOG(DBG) << "main_index_info:\n" << out0.main_index_info();
  ICING_LOG(DBG) << "lite_index_info:\n" << out0.lite_index_info();
  EXPECT_THAT(out0.main_index_info(), Not(IsEmpty()));
  EXPECT_THAT(out0.lite_index_info(), Not(IsEmpty()));

  IndexDebugInfoProto out1 = index_->GetDebugInfo(DebugInfoVerbosity::DETAILED);
  ICING_LOG(DBG) << "main_index_info:\n" << out1.main_index_info();
  ICING_LOG(DBG) << "lite_index_info:\n" << out1.lite_index_info();
  EXPECT_THAT(out1.main_index_info(),
              SizeIs(Gt(out0.main_index_info().size())));
  EXPECT_THAT(out1.lite_index_info(),
              SizeIs(Gt(out0.lite_index_info().size())));

  // Add one more doc to the lite index. Debug strings should change.
  edit = index_->Edit(kDocumentId3, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  index_->set_last_added_document_id(kDocumentId3);
  ASSERT_THAT(edit.BufferTerm("far"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  IndexDebugInfoProto out2 = index_->GetDebugInfo(DebugInfoVerbosity::BASIC);
  ICING_LOG(DBG) << "main_index_info:\n" << out2.main_index_info();
  ICING_LOG(DBG) << "lite_index_info:\n" << out2.lite_index_info();
  EXPECT_THAT(out2.main_index_info(), Not(IsEmpty()));
  EXPECT_THAT(out2.lite_index_info(), Not(IsEmpty()));
  EXPECT_THAT(out2.main_index_info(), StrEq(out0.main_index_info()));
  EXPECT_THAT(out2.lite_index_info(), StrNe(out0.lite_index_info()));

  // Merge into the man index. Debug strings should change again.
  ICING_ASSERT_OK(index_->Merge());

  IndexDebugInfoProto out3 = index_->GetDebugInfo(DebugInfoVerbosity::BASIC);
  EXPECT_TRUE(out3.has_index_storage_info());
  ICING_LOG(DBG) << "main_index_info:\n" << out3.main_index_info();
  ICING_LOG(DBG) << "lite_index_info:\n" << out3.lite_index_info();
  EXPECT_THAT(out3.main_index_info(), Not(IsEmpty()));
  EXPECT_THAT(out3.lite_index_info(), Not(IsEmpty()));
  EXPECT_THAT(out3.main_index_info(), StrNe(out2.main_index_info()));
  EXPECT_THAT(out3.lite_index_info(), StrNe(out2.lite_index_info()));
}

TEST_F(IndexTest, BackfillingMultipleTermsSucceeds) {
  // Add two documents to the lite index, merge them into the main index and
  // then add another doc to the lite index.
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId0, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foot"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // After this merge the index should have posting lists for
  // "fool" {(doc0,sec3)},
  // "foot" {(doc1,sec3)},
  // "foo"  {(doc1,sec3),(doc0,sec3),(doc0,sec2)}
  ICING_ASSERT_OK(index_->Merge());

  // Add one more doc to the lite index.
  edit = index_->Edit(kDocumentId2, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("far"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // After this merge the index should add a posting list for "far" and a
  // backfill branch point for "f". In addition to the posting lists described
  // above, which are unaffected, the new posting lists should be
  // "far" {(doc2,sec2)},
  // "f"   {(doc1,sec3),(doc0,sec3)}
  // Multiple pre-existing hits should be added to the new backfill branch
  // point.
  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("f", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(
          EqualsDocHitInfo(kDocumentId1, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0, std::vector<SectionId>{kSectionId3})));
}

TEST_F(IndexTest, BackfillingNewTermsSucceeds) {
  // Add two documents to the lite index, merge them into the main index and
  // then add another doc to the lite index.
  Index::Editor edit = index_->Edit(
      kDocumentId0, kSectionId2, TermMatchType::EXACT_ONLY, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  ASSERT_THAT(edit.BufferTerm("fool"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foot"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  // After this merge the index should have posting lists for
  // "fool" {(doc0,sec2)},
  // "foot" {(doc1,sec3)},
  // "foo"  {(doc1,sec3),(doc0,sec2)}
  ICING_ASSERT_OK(index_->Merge());

  edit = index_->Edit(kDocumentId2, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("footer"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId2, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  // Add one more doc to the lite index. Debug strings should change.
  edit = index_->Edit(kDocumentId3, kSectionId2, TermMatchType::EXACT_ONLY,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("far"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // After this merge the index should add posting lists for "far" and "footer"
  // and a backfill branch point for "f". The new posting lists should be
  // "fool"    {(doc0,sec2)},
  // "foot"    {(doc1,sec3)},
  // "foo"     {(doc2,sec3),(doc1,sec3),(doc0,sec2)}
  // "footer"  {(doc2,sec2)},
  // "far"     {(doc3,sec2)},
  // "f"       {(doc2,sec3),(doc1,sec3)}
  // Multiple pre-existing hits should be added to the new backfill branch
  // point.
  ICING_ASSERT_OK(index_->Merge());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("f", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(
          EqualsDocHitInfo(kDocumentId2, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId1, std::vector<SectionId>{kSectionId3})));
}

TEST_F(IndexTest, TruncateToInvalidDocumentIdHasNoEffect) {
  ICING_EXPECT_OK(index_->TruncateTo(kInvalidDocumentId));
  EXPECT_THAT(index_->GetElementsSize(), IsOkAndHolds(0));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("f", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());

  // Add one document to the lite index
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  // Clipping to invalid should have no effect.
  ICING_EXPECT_OK(index_->TruncateTo(kInvalidDocumentId));
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("f", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));

  // Clipping to invalid should still have no effect even if hits are in main.
  ICING_ASSERT_OK(index_->Merge());
  ICING_EXPECT_OK(index_->TruncateTo(kInvalidDocumentId));
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("f", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));

  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foot"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // Clipping to invalid should still have no effect even if both indices have
  // hits.
  ICING_EXPECT_OK(index_->TruncateTo(kInvalidDocumentId));
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("f", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(
          EqualsDocHitInfo(kDocumentId1, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, TruncateToLastAddedDocumentIdHasNoEffect) {
  ICING_EXPECT_OK(index_->TruncateTo(index_->last_added_document_id()));
  EXPECT_THAT(index_->GetElementsSize(), IsOkAndHolds(0));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("f", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());

  // Add one document to the lite index
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  index_->set_last_added_document_id(kDocumentId0);
  ICING_EXPECT_OK(index_->TruncateTo(index_->last_added_document_id()));
  // Clipping to invalid should have no effect.
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("f", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));

  // Clipping to invalid should still have no effect even if hits are in main.
  ICING_ASSERT_OK(index_->Merge());
  ICING_EXPECT_OK(index_->TruncateTo(index_->last_added_document_id()));
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("f", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));

  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foot"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  index_->set_last_added_document_id(kDocumentId1);

  // Clipping to invalid should still have no effect even if both indices have
  // hits.
  ICING_EXPECT_OK(index_->TruncateTo(index_->last_added_document_id()));
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("f", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(
          EqualsDocHitInfo(kDocumentId1, std::vector<SectionId>{kSectionId3}),
          EqualsDocHitInfo(kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, TruncateToThrowsOutLiteIndex) {
  // Add one document to the lite index and merge it into main.
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  index_->set_last_added_document_id(kDocumentId0);

  ICING_ASSERT_OK(index_->Merge());

  // Add another document to the lite index.
  edit = index_->Edit(kDocumentId1, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foot"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  index_->set_last_added_document_id(kDocumentId1);

  EXPECT_THAT(index_->TruncateTo(kDocumentId0), IsOk());

  // Clipping to document 0 should toss out the lite index, but keep the main.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("f", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kSectionId2})));
}

TEST_F(IndexTest, TruncateToThrowsOutBothIndices) {
  // Add two documents to the lite index and merge them into main.
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  index_->set_last_added_document_id(kDocumentId0);
  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foul"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  index_->set_last_added_document_id(kDocumentId1);

  ICING_ASSERT_OK(index_->Merge());

  // Add another document to the lite index.
  edit = index_->Edit(kDocumentId2, kSectionId3, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foot"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  index_->set_last_added_document_id(kDocumentId2);

  EXPECT_THAT(index_->TruncateTo(kDocumentId0), IsOk());

  // Clipping to document 0 should toss out both indices.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("f", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());
}

TEST_F(IndexTest, IndexStorageInfoProto) {
  // Add two documents to the lite index and merge them into main.
  {
    Index::Editor edit = index_->Edit(
        kDocumentId0, kSectionId2, TermMatchType::PREFIX, /*namespace_id=*/0);
    ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
    EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
    edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::PREFIX,
                        /*namespace_id=*/0);
    ASSERT_THAT(edit.BufferTerm("foul"), IsOk());
    EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

    ICING_ASSERT_OK(index_->Merge());
  }

  IndexStorageInfoProto storage_info = index_->GetStorageInfo();
  EXPECT_THAT(storage_info.index_size(), Ge(0));
  EXPECT_THAT(storage_info.lite_index_lexicon_size(), Ge(0));
  EXPECT_THAT(storage_info.lite_index_hit_buffer_size(), Ge(0));
  EXPECT_THAT(storage_info.main_index_lexicon_size(), Ge(0));
  EXPECT_THAT(storage_info.main_index_storage_size(), Ge(0));
  EXPECT_THAT(storage_info.main_index_block_size(), Ge(0));
  // There should be 1 block for the header and 1 block for two posting lists.
  EXPECT_THAT(storage_info.num_blocks(), Eq(2));
  EXPECT_THAT(storage_info.min_free_fraction(), Ge(0));
}

TEST_F(IndexTest, PublishQueryStats) {
  // Add two documents to the lite index without merging.
  Index::Editor edit = index_->Edit(kDocumentId0, kSectionId2,
                                    TermMatchType::PREFIX, /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foo"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());
  edit = index_->Edit(kDocumentId1, kSectionId2, TermMatchType::PREFIX,
                      /*namespace_id=*/0);
  ASSERT_THAT(edit.BufferTerm("foul"), IsOk());
  EXPECT_THAT(edit.IndexAllBufferedTerms(), IsOk());

  // Verify query stats.
  QueryStatsProto query_stats1;
  index_->PublishQueryStats(&query_stats1);
  EXPECT_THAT(query_stats1.lite_index_hit_buffer_byte_size(),
              Eq(2 * sizeof(TermIdHitPair::Value)));
  EXPECT_THAT(query_stats1.lite_index_hit_buffer_unsorted_byte_size(),
              Ge(2 * sizeof(TermIdHitPair::Value)));

  // Sort lite index.
  index_->SortLiteIndex();
  QueryStatsProto query_stats2;
  index_->PublishQueryStats(&query_stats2);
  EXPECT_THAT(query_stats2.lite_index_hit_buffer_byte_size(),
              Eq(2 * sizeof(TermIdHitPair::Value)));
  EXPECT_THAT(query_stats2.lite_index_hit_buffer_unsorted_byte_size(), Eq(0));

  // Merge lite index to main index.
  ICING_ASSERT_OK(index_->Merge());
  QueryStatsProto query_stats3;
  index_->PublishQueryStats(&query_stats3);
  EXPECT_THAT(query_stats3.lite_index_hit_buffer_byte_size(), Eq(0));
  EXPECT_THAT(query_stats3.lite_index_hit_buffer_unsorted_byte_size(), Eq(0));
}

}  // namespace

}  // namespace lib
}  // namespace icing
