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

#include "icing/index/iterator/doc-hit-info-iterator-all-document-id.h"

#include <string>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::IsNull;
using ::testing::Not;

TEST(DocHitInfoIteratorAllDocumentIdTest, Initialize) {
  {
    DocHitInfoIteratorAllDocumentId all_it(100);

    // We'll always start with an invalid document_id, need to Advance before we
    // get anything out of this.
    EXPECT_THAT(all_it.doc_hit_info(),
                EqualsDocHitInfo(kInvalidDocumentId, std::vector<SectionId>{}));
  }

  {
    // Can initialize with negative values, but won't ever be able to Advance to
    // a proper document_id
    DocHitInfoIteratorAllDocumentId all_it(-5);
    EXPECT_THAT(all_it.Advance(), Not(IsOk()));
  }
}

TEST(DocHitInfoIteratorAllDocumentIdTest, GetCallStats) {
  DocHitInfoIteratorAllDocumentId all_it(100);
  EXPECT_THAT(
      all_it.GetCallStats(),
      EqualsDocHitInfoIteratorCallStats(
          /*num_leaf_advance_calls_lite_index=*/0,
          /*num_leaf_advance_calls_main_index=*/0,
          /*num_leaf_advance_calls_integer_index=*/0,
          /*num_leaf_advance_calls_no_index=*/0, /*num_blocks_inspected=*/0));

  for (int i = 1; i <= 5; ++i) {
    EXPECT_THAT(all_it.Advance(), IsOk());
    EXPECT_THAT(
        all_it.GetCallStats(),
        EqualsDocHitInfoIteratorCallStats(
            /*num_leaf_advance_calls_lite_index=*/0,
            /*num_leaf_advance_calls_main_index=*/0,
            /*num_leaf_advance_calls_integer_index=*/0,
            /*num_leaf_advance_calls_no_index=*/i, /*num_blocks_inspected=*/0));
  }
}

TEST(DocHitInfoIteratorAllDocumentIdTest, Advance) {
  {
    // Can't advance beyond an invalid DocumentId
    EXPECT_THAT(DocHitInfoIteratorAllDocumentId(-1).Advance(), Not(IsOk()));
  }

  {
    // Test one advance
    DocHitInfoIteratorAllDocumentId all_it(5);
    EXPECT_THAT(all_it.Advance(), IsOk());
    EXPECT_THAT(all_it.doc_hit_info(),
                EqualsDocHitInfo(5, std::vector<SectionId>{}));
  }

  {
    std::vector<DocumentId> expected_document_ids;
    expected_document_ids.reserve(125);
    for (int i = 124; i >= 0; --i) {
      expected_document_ids.push_back(i);
    }

    // Many advances
    DocHitInfoIteratorAllDocumentId all_it(124);
    EXPECT_THAT(GetDocumentIds(&all_it),
                ElementsAreArray(expected_document_ids));
  }
}

TEST(DocHitInfoIteratorAllDocumentIdTest, TrimAllDocumentIdIterator) {
  DocHitInfoIteratorAllDocumentId all_it(100);
  ICING_ASSERT_OK_AND_ASSIGN(DocHitInfoIterator::TrimmedNode trimmed_node,
                             std::move(all_it).TrimRightMostNode());
  // The whole iterator is trimmed
  EXPECT_THAT(trimmed_node.term_, testing::IsEmpty());
  EXPECT_THAT(trimmed_node.term_start_index_, 0);
  EXPECT_THAT(trimmed_node.iterator_, IsNull());
}

}  // namespace
}  // namespace lib
}  // namespace icing
