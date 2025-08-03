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

#include "icing/scoring/priority-queue-scored-document-hits-ranker.h"

#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::SizeIs;

class Converter {
 public:
  JoinedScoredDocumentHit operator()(ScoredDocumentHit hit) const {
    return converter_(std::move(hit));
  }

 private:
  ScoredDocumentHit::Converter converter_;
} converter;

std::vector<JoinedScoredDocumentHit> PopAll(
    PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>& ranker) {
  std::vector<JoinedScoredDocumentHit> hits;
  while (!ranker.empty()) {
    hits.push_back(ranker.PopNext());
  }
  return hits;
}

TEST(PriorityQueueScoredDocumentHitsRankerTest, ShouldGetCorrectSizeAndEmpty) {
  ScoredDocumentHit scored_hit_0(/*document_id=*/0, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_1(/*document_id=*/1, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_2(/*document_id=*/2, kSectionIdMaskNone,
                                 /*score=*/1);

  PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit> ranker(
      {scored_hit_1, scored_hit_0, scored_hit_2},
      /*is_descending=*/true);
  EXPECT_THAT(ranker.size(), Eq(3));
  EXPECT_FALSE(ranker.empty());

  ranker.PopNext();
  EXPECT_THAT(ranker.size(), Eq(2));
  EXPECT_FALSE(ranker.empty());

  ranker.PopNext();
  EXPECT_THAT(ranker.size(), Eq(1));
  EXPECT_FALSE(ranker.empty());

  ranker.PopNext();
  EXPECT_THAT(ranker.size(), Eq(0));
  EXPECT_TRUE(ranker.empty());
}

TEST(PriorityQueueScoredDocumentHitsRankerTest, ShouldRankInDescendingOrder) {
  ScoredDocumentHit scored_hit_0(/*document_id=*/0, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_1(/*document_id=*/1, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_2(/*document_id=*/2, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_3(/*document_id=*/3, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_4(/*document_id=*/4, kSectionIdMaskNone,
                                 /*score=*/1);

  PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit> ranker(
      {scored_hit_1, scored_hit_0, scored_hit_2, scored_hit_4, scored_hit_3},
      /*is_descending=*/true);

  EXPECT_THAT(ranker, SizeIs(5));
  std::vector<JoinedScoredDocumentHit> scored_document_hits = PopAll(ranker);
  EXPECT_THAT(
      scored_document_hits,
      ElementsAre(EqualsJoinedScoredDocumentHit(converter(scored_hit_4)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_3)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_2)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_1)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_0))));
}

TEST(PriorityQueueScoredDocumentHitsRankerTest, ShouldRankInAscendingOrder) {
  ScoredDocumentHit scored_hit_0(/*document_id=*/0, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_1(/*document_id=*/1, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_2(/*document_id=*/2, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_3(/*document_id=*/3, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_4(/*document_id=*/4, kSectionIdMaskNone,
                                 /*score=*/1);

  PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit> ranker(
      {scored_hit_1, scored_hit_0, scored_hit_2, scored_hit_4, scored_hit_3},
      /*is_descending=*/false);

  EXPECT_THAT(ranker, SizeIs(5));
  std::vector<JoinedScoredDocumentHit> scored_document_hits = PopAll(ranker);
  EXPECT_THAT(
      scored_document_hits,
      ElementsAre(EqualsJoinedScoredDocumentHit(converter(scored_hit_0)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_1)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_2)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_3)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_4))));
}

TEST(PriorityQueueScoredDocumentHitsRankerTest,
     ShouldRankDuplicateScoredDocumentHits) {
  ScoredDocumentHit scored_hit_0(/*document_id=*/0, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_1(/*document_id=*/1, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_2(/*document_id=*/2, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_3(/*document_id=*/3, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_4(/*document_id=*/4, kSectionIdMaskNone,
                                 /*score=*/1);

  PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit> ranker(
      {scored_hit_2, scored_hit_4, scored_hit_1, scored_hit_0, scored_hit_2,
       scored_hit_2, scored_hit_4, scored_hit_3},
      /*is_descending=*/true);

  EXPECT_THAT(ranker, SizeIs(8));
  std::vector<JoinedScoredDocumentHit> scored_document_hits = PopAll(ranker);
  EXPECT_THAT(
      scored_document_hits,
      ElementsAre(EqualsJoinedScoredDocumentHit(converter(scored_hit_4)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_4)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_3)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_2)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_2)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_2)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_1)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_0))));
}

TEST(PriorityQueueScoredDocumentHitsRankerTest,
     ShouldRankEmptyScoredDocumentHits) {
  PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit> ranker(
      /*scored_document_hits=*/{},
      /*is_descending=*/true);
  EXPECT_THAT(ranker, IsEmpty());
}

TEST(PriorityQueueScoredDocumentHitsRankerTest, ShouldTruncateToNewSize) {
  ScoredDocumentHit scored_hit_0(/*document_id=*/0, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_1(/*document_id=*/1, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_2(/*document_id=*/2, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_3(/*document_id=*/3, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_4(/*document_id=*/4, kSectionIdMaskNone,
                                 /*score=*/1);

  PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit> ranker(
      {scored_hit_1, scored_hit_0, scored_hit_2, scored_hit_4, scored_hit_3},
      /*is_descending=*/true);
  ASSERT_THAT(ranker, SizeIs(5));

  ranker.TruncateHitsTo(/*new_size=*/3);
  EXPECT_THAT(ranker, SizeIs(3));
  std::vector<JoinedScoredDocumentHit> scored_document_hits = PopAll(ranker);
  EXPECT_THAT(
      scored_document_hits,
      ElementsAre(EqualsJoinedScoredDocumentHit(converter(scored_hit_4)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_3)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_2))));
}

TEST(PriorityQueueScoredDocumentHitsRankerTest, ShouldTruncateToZero) {
  ScoredDocumentHit scored_hit_0(/*document_id=*/0, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_1(/*document_id=*/1, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_2(/*document_id=*/2, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_3(/*document_id=*/3, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_4(/*document_id=*/4, kSectionIdMaskNone,
                                 /*score=*/1);

  PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit> ranker(
      {scored_hit_1, scored_hit_0, scored_hit_2, scored_hit_4, scored_hit_3},
      /*is_descending=*/true);
  ASSERT_THAT(ranker, SizeIs(5));

  ranker.TruncateHitsTo(/*new_size=*/0);
  EXPECT_THAT(ranker, IsEmpty());
}

TEST(PriorityQueueScoredDocumentHitsRankerTest, ShouldNotTruncateToNegative) {
  ScoredDocumentHit scored_hit_0(/*document_id=*/0, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_1(/*document_id=*/1, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_2(/*document_id=*/2, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_3(/*document_id=*/3, kSectionIdMaskNone,
                                 /*score=*/1);
  ScoredDocumentHit scored_hit_4(/*document_id=*/4, kSectionIdMaskNone,
                                 /*score=*/1);

  PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit> ranker(
      {scored_hit_1, scored_hit_0, scored_hit_2, scored_hit_4, scored_hit_3},
      /*is_descending=*/true);
  ASSERT_THAT(ranker, SizeIs(Eq(5)));

  ranker.TruncateHitsTo(/*new_size=*/-1);
  EXPECT_THAT(ranker, SizeIs(Eq(5)));
  // Contents are not affected.
  std::vector<JoinedScoredDocumentHit> scored_document_hits = PopAll(ranker);
  EXPECT_THAT(
      scored_document_hits,
      ElementsAre(EqualsJoinedScoredDocumentHit(converter(scored_hit_4)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_3)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_2)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_1)),
                  EqualsJoinedScoredDocumentHit(converter(scored_hit_0))));
}

}  // namespace

}  // namespace lib
}  // namespace icing
