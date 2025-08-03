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

#include "icing/scoring/ranker.h"

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
using ::testing::Test;

ScoredDocumentHit CreateScoredDocumentHit(DocumentId document_id,
                                          double score) {
  return ScoredDocumentHit(document_id, kSectionIdMaskAll, score);
}

TEST(RankerTest, ShouldHandleEmpty) {
  std::vector<ScoredDocumentHit> scored_document_hits = {};

  const ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);
  BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);

  EXPECT_THAT(PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/
                                    0, scored_document_hit_comparator),
              IsEmpty());

  EXPECT_THAT(PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/
                                    3, scored_document_hit_comparator),
              IsEmpty());
}

TEST(RankerTest, ShouldCorrectlySortResultsDesc) {
  ScoredDocumentHit scored_document_hit1 =
      CreateScoredDocumentHit(/*document_id=*/1, /*score=*/1);
  ScoredDocumentHit scored_document_hit2 =
      CreateScoredDocumentHit(/*document_id=*/2, /*score=*/2);
  ScoredDocumentHit scored_document_hit3 =
      CreateScoredDocumentHit(/*document_id=*/3, /*score=*/3);
  ScoredDocumentHit scored_document_hit4 =
      CreateScoredDocumentHit(/*document_id=*/4, /*score=*/4);
  ScoredDocumentHit scored_document_hit5 =
      CreateScoredDocumentHit(/*document_id=*/5, /*score=*/5);

  std::vector<ScoredDocumentHit> scored_document_hits = {
      scored_document_hit2, scored_document_hit1, scored_document_hit5,
      scored_document_hit4, scored_document_hit3};

  const ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);
  BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);

  EXPECT_THAT(PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/
                                    5, scored_document_hit_comparator),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit5),
                          EqualsScoredDocumentHit(scored_document_hit4),
                          EqualsScoredDocumentHit(scored_document_hit3),
                          EqualsScoredDocumentHit(scored_document_hit2),
                          EqualsScoredDocumentHit(scored_document_hit1)));
}

TEST(RankerTest, ShouldCorrectlySortResultsAsc) {
  ScoredDocumentHit scored_document_hit1 =
      CreateScoredDocumentHit(/*document_id=*/1, /*score=*/1);
  ScoredDocumentHit scored_document_hit2 =
      CreateScoredDocumentHit(/*document_id=*/2, /*score=*/2);
  ScoredDocumentHit scored_document_hit3 =
      CreateScoredDocumentHit(/*document_id=*/3, /*score=*/3);
  ScoredDocumentHit scored_document_hit4 =
      CreateScoredDocumentHit(/*document_id=*/4, /*score=*/4);
  ScoredDocumentHit scored_document_hit5 =
      CreateScoredDocumentHit(/*document_id=*/5, /*score=*/5);

  std::vector<ScoredDocumentHit> scored_document_hits = {
      scored_document_hit2, scored_document_hit1, scored_document_hit5,
      scored_document_hit4, scored_document_hit3};

  const ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/false);
  BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);

  EXPECT_THAT(PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/
                                    5, scored_document_hit_comparator),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit1),
                          EqualsScoredDocumentHit(scored_document_hit2),
                          EqualsScoredDocumentHit(scored_document_hit3),
                          EqualsScoredDocumentHit(scored_document_hit4),
                          EqualsScoredDocumentHit(scored_document_hit5)));
}

TEST(RankerTest, ShouldHandleSmallerNumResult) {
  ScoredDocumentHit scored_document_hit1 =
      CreateScoredDocumentHit(/*document_id=*/1, /*score=*/1);
  ScoredDocumentHit scored_document_hit2 =
      CreateScoredDocumentHit(/*document_id=*/2, /*score=*/2);
  ScoredDocumentHit scored_document_hit3 =
      CreateScoredDocumentHit(/*document_id=*/3, /*score=*/3);
  ScoredDocumentHit scored_document_hit4 =
      CreateScoredDocumentHit(/*document_id=*/4, /*score=*/4);
  ScoredDocumentHit scored_document_hit5 =
      CreateScoredDocumentHit(/*document_id=*/5, /*score=*/5);

  std::vector<ScoredDocumentHit> scored_document_hits = {
      scored_document_hit2, scored_document_hit1, scored_document_hit5,
      scored_document_hit4, scored_document_hit3};

  const ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);
  BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);

  // num_results = 3, smaller than the size 5
  EXPECT_THAT(PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/
                                    3, scored_document_hit_comparator),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit5),
                          EqualsScoredDocumentHit(scored_document_hit4),
                          EqualsScoredDocumentHit(scored_document_hit3)));
}

TEST(RankerTest, ShouldHandleGreaterNumResult) {
  ScoredDocumentHit scored_document_hit1 =
      CreateScoredDocumentHit(/*document_id=*/1, /*score=*/1);
  ScoredDocumentHit scored_document_hit2 =
      CreateScoredDocumentHit(/*document_id=*/2, /*score=*/2);
  ScoredDocumentHit scored_document_hit3 =
      CreateScoredDocumentHit(/*document_id=*/3, /*score=*/3);
  ScoredDocumentHit scored_document_hit4 =
      CreateScoredDocumentHit(/*document_id=*/4, /*score=*/4);
  ScoredDocumentHit scored_document_hit5 =
      CreateScoredDocumentHit(/*document_id=*/5, /*score=*/5);

  std::vector<ScoredDocumentHit> scored_document_hits = {
      scored_document_hit2, scored_document_hit1, scored_document_hit5,
      scored_document_hit4, scored_document_hit3};

  const ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);
  BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);

  // num_results = 10, greater than the size 5
  EXPECT_THAT(PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/
                                    10, scored_document_hit_comparator),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit5),
                          EqualsScoredDocumentHit(scored_document_hit4),
                          EqualsScoredDocumentHit(scored_document_hit3),
                          EqualsScoredDocumentHit(scored_document_hit2),
                          EqualsScoredDocumentHit(scored_document_hit1)));
}

TEST(RankerTest, ShouldRespectDocumentIdDescWhenScoresAreEqual) {
  ScoredDocumentHit scored_document_hit1(
      /*document_id=*/1, /*hit_section_id_mask=*/0, /*score=*/100);
  ScoredDocumentHit scored_document_hit2(
      /*document_id=*/2, /*hit_section_id_mask=*/0, /*score=*/100);
  ScoredDocumentHit scored_document_hit3(
      /*document_id=*/3, /*hit_section_id_mask=*/0, /*score=*/100);

  std::vector<ScoredDocumentHit> scored_document_hits = {
      scored_document_hit3, scored_document_hit1, scored_document_hit2};

  const ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);
  BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);

  EXPECT_THAT(PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/
                                    3, scored_document_hit_comparator),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit3),
                          EqualsScoredDocumentHit(scored_document_hit2),
                          EqualsScoredDocumentHit(scored_document_hit1)));
}

TEST(RankerTest, ShouldRespectDocumentIdAscWhenScoresAreEqual) {
  ScoredDocumentHit scored_document_hit1(
      /*document_id=*/1, /*hit_section_id_mask=*/0, /*score=*/100);
  ScoredDocumentHit scored_document_hit2(
      /*document_id=*/2, /*hit_section_id_mask=*/0, /*score=*/100);
  ScoredDocumentHit scored_document_hit3(
      /*document_id=*/3, /*hit_section_id_mask=*/0, /*score=*/100);

  std::vector<ScoredDocumentHit> scored_document_hits = {
      scored_document_hit3, scored_document_hit1, scored_document_hit2};

  const ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/false);
  BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);

  EXPECT_THAT(PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/
                                    3, scored_document_hit_comparator),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit1),
                          EqualsScoredDocumentHit(scored_document_hit2),
                          EqualsScoredDocumentHit(scored_document_hit3)));
}

TEST(RankerTest, ShouldPopResultsFromHeapMultipleTimes) {
  ScoredDocumentHit scored_document_hit1 =
      CreateScoredDocumentHit(/*document_id=*/1, /*score=*/1);
  ScoredDocumentHit scored_document_hit2 =
      CreateScoredDocumentHit(/*document_id=*/2, /*score=*/2);
  ScoredDocumentHit scored_document_hit3 =
      CreateScoredDocumentHit(/*document_id=*/3, /*score=*/3);
  ScoredDocumentHit scored_document_hit4 =
      CreateScoredDocumentHit(/*document_id=*/4, /*score=*/4);
  ScoredDocumentHit scored_document_hit5 =
      CreateScoredDocumentHit(/*document_id=*/5, /*score=*/5);

  std::vector<ScoredDocumentHit> scored_document_hits = {
      scored_document_hit2, scored_document_hit1, scored_document_hit5,
      scored_document_hit4, scored_document_hit3};

  const ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);
  BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);

  EXPECT_THAT(scored_document_hits.size(), Eq(5));

  // Pops 2 results
  EXPECT_THAT(PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/2,
                                    scored_document_hit_comparator),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit5),
                          EqualsScoredDocumentHit(scored_document_hit4)));
  EXPECT_THAT(scored_document_hits.size(), Eq(3));

  // Pops 2 results
  EXPECT_THAT(PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/2,
                                    scored_document_hit_comparator),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit3),
                          EqualsScoredDocumentHit(scored_document_hit2)));
  EXPECT_THAT(scored_document_hits.size(), Eq(1));

  // Pops last 1 result
  EXPECT_THAT(PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/2,
                                    scored_document_hit_comparator),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit1)));
  EXPECT_THAT(scored_document_hits.size(), Eq(0));
}

}  // namespace

}  // namespace lib
}  // namespace icing
