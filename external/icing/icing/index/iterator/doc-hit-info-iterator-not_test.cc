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

#include "icing/index/iterator/doc-hit-info-iterator-not.h"

#include <string>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;

TEST(DocHitInfoIteratorNotTest, InvalidDocumentIdLimit) {
  std::vector<DocHitInfo> exclude_doc_hit_infos = {DocHitInfo(5),
                                                   DocHitInfo(4)};
  std::unique_ptr<DocHitInfoIterator> to_be_excluded_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(exclude_doc_hit_infos);

  DocHitInfoIteratorNot not_iterator(std::move(to_be_excluded_iterator),
                                     /*document_id_limit=*/-1);
  EXPECT_THAT(not_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST(DocHitInfoIteratorNotTest, NotFirstFewDocumentIdsOk) {
  std::vector<DocHitInfo> exclude_doc_hit_infos = {DocHitInfo(5),
                                                   DocHitInfo(4)};
  std::unique_ptr<DocHitInfoIterator> to_be_excluded_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(exclude_doc_hit_infos);

  DocHitInfoIteratorNot not_iterator(std::move(to_be_excluded_iterator),
                                     /*document_id_limit=*/5);
  EXPECT_THAT(GetDocumentIds(&not_iterator), ElementsAre(3, 2, 1, 0));
}

TEST(DocHitInfoIteratorNotTest, NotLastFewDocumentIdsOk) {
  std::vector<DocHitInfo> exclude_doc_hit_infos = {DocHitInfo(1),
                                                   DocHitInfo(0)};
  std::unique_ptr<DocHitInfoIterator> to_be_excluded_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(exclude_doc_hit_infos);

  DocHitInfoIteratorNot not_iterator(std::move(to_be_excluded_iterator),
                                     /*document_id_limit=*/5);
  EXPECT_THAT(GetDocumentIds(&not_iterator), ElementsAre(5, 4, 3, 2));
}

TEST(DocHitInfoIteratorNotTest, IntermittentDocumentIdOverlapOk) {
  std::vector<DocHitInfo> exclude_doc_hit_infos = {
      DocHitInfo(8), DocHitInfo(6), DocHitInfo(4), DocHitInfo(2)};
  std::unique_ptr<DocHitInfoIterator> to_be_excluded_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(exclude_doc_hit_infos);

  DocHitInfoIteratorNot not_iterator(std::move(to_be_excluded_iterator),
                                     /*document_id_limit=*/10);
  EXPECT_THAT(GetDocumentIds(&not_iterator), ElementsAre(10, 9, 7, 5, 3, 1, 0));
}

TEST(DocHitInfoIteratorNotTest, NoDocumentIdOverlapOk) {
  std::vector<DocHitInfo> exclude_doc_hit_infos = {};
  std::unique_ptr<DocHitInfoIterator> to_be_excluded_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(exclude_doc_hit_infos);

  DocHitInfoIteratorNot not_iterator(std::move(to_be_excluded_iterator),
                                     /*document_id_limit=*/5);
  EXPECT_THAT(GetDocumentIds(&not_iterator), ElementsAre(5, 4, 3, 2, 1, 0));
}

TEST(DocHitInfoIteratorNotTest, AllDocumentIdOverlapOk) {
  std::vector<DocHitInfo> exclude_doc_hit_infos = {
      DocHitInfo(5), DocHitInfo(4), DocHitInfo(3),
      DocHitInfo(2), DocHitInfo(1), DocHitInfo(0)};
  std::unique_ptr<DocHitInfoIterator> to_be_excluded_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(exclude_doc_hit_infos);

  DocHitInfoIteratorNot not_iterator(std::move(to_be_excluded_iterator),
                                     /*document_id_limit=*/5);
  EXPECT_THAT(GetDocumentIds(&not_iterator), IsEmpty());
}

TEST(DocHitInfoIteratorNotTest, GetCallStats) {
  DocHitInfoIterator::CallStats to_be_excluded_iterator_call_stats(
      /*num_leaf_advance_calls_lite_index_in=*/2,
      /*num_leaf_advance_calls_main_index_in=*/5,
      /*num_leaf_advance_calls_integer_index_in=*/3,
      /*num_leaf_advance_calls_no_index_in=*/1,
      /*num_blocks_inspected_in=*/4);  // arbitrary value
  auto to_be_excluded_iterator = std::make_unique<DocHitInfoIteratorDummy>();
  to_be_excluded_iterator->SetCallStats(to_be_excluded_iterator_call_stats);

  int all_document_id_limit = 5;
  // Since we iterate from [limit, 0] inclusive, add 1 for the 0th advance call
  int all_leaf_advance_calls = all_document_id_limit + 1;
  DocHitInfoIteratorNot not_iterator(std::move(to_be_excluded_iterator),
                                     /*document_id_limit=*/5);

  while (not_iterator.Advance().ok()) {
    // Advance through the whole not iterator
  }

  // The AllDocumentId iterator doesn't count lite/main/integer index or blocks
  // as being inspected since it's just decrementing 1 from the
  // document_id_limit.
  EXPECT_THAT(
      not_iterator.GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          to_be_excluded_iterator_call_stats.num_leaf_advance_calls_lite_index,
          to_be_excluded_iterator_call_stats.num_leaf_advance_calls_main_index,
          to_be_excluded_iterator_call_stats
              .num_leaf_advance_calls_integer_index,
          to_be_excluded_iterator_call_stats.num_leaf_advance_calls_no_index +
              all_leaf_advance_calls,
          to_be_excluded_iterator_call_stats.num_blocks_inspected));
}

TEST(DocHitInfoIteratorNotTest, SectionIdsAlwaysNone) {
  SectionIdMask section_id_mask5 = 1U << 5;  // arbitrary non-zero value
  SectionIdMask section_id_mask4 = 1U << 4;  // arbitrary non-zero value
  std::vector<DocHitInfo> exclude_doc_hit_infos = {
      DocHitInfo(5, section_id_mask5), DocHitInfo(4, section_id_mask4)};
  std::unique_ptr<DocHitInfoIterator> to_be_excluded_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(exclude_doc_hit_infos);

  DocHitInfoIteratorNot not_iterator(std::move(to_be_excluded_iterator),
                                     /*document_id_limit=*/5);
  EXPECT_THAT(GetDocHitInfos(&not_iterator),
              ElementsAre(DocHitInfo(3, kSectionIdMaskNone),
                          DocHitInfo(2, kSectionIdMaskNone),
                          DocHitInfo(1, kSectionIdMaskNone),
                          DocHitInfo(0, kSectionIdMaskNone)));
}

TEST(DocHitInfoIteratorNotTest, TrimNotIterator) {
  std::vector<DocHitInfo> exclude_doc_hit_infos = {DocHitInfo(0)};
  std::unique_ptr<DocHitInfoIterator> to_be_excluded_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(exclude_doc_hit_infos);

  DocHitInfoIteratorNot not_iterator(std::move(to_be_excluded_iterator),
                                     /*document_id_limit=*/5);
  EXPECT_THAT(std::move(not_iterator).TrimRightMostNode(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

}  // namespace

}  // namespace lib
}  // namespace icing
